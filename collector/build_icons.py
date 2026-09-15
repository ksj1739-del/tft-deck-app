#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
피드가 참조하는 아이콘을 WebP 팩(data/icons/icons.zip + manifest.json)으로 미러링한다.

앱은 이 zip 을 파일 하나로 받아 기기에 풀어 두고 아이콘을 로컬 파일로 그린다.
CommunityDragon 에서 아이콘마다 받으면 도쿄 edge 왕복(요청당 120~160 ms), 1시간마다
돌아오는 재검증(304 도 전체 다운로드만큼 느리다), OkHttp 호스트당 5요청 제한이 겹쳐
첫 화면이 수 초씩 늦었다(2026-09-15 측정). 파일별 CDN 미러는 저트래픽 앱에서 대부분
콜드라 빠르지 않아서 zip 한 파일 방식을 쓴다.

의존성: Pillow(WebP 인코딩, pip install pillow). 나머지는 표준 라이브러리.

사용법:
  python collector/build_icons.py              # data/icons/ 갱신
  python collector/build_icons.py --snapshot   # + 앱 동봉본(android assets)도 갱신
"""

import argparse
import hashlib
import http.client
import io
import json
import os
import sys
import time
import urllib.error
import urllib.request
import zipfile
import zlib
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from glob import glob

# Windows 콘솔(cp949)에서 한글/기호 출력 시 죽지 않도록.
for _stream in (sys.stdout, sys.stderr):
    try:
        _stream.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, ValueError):
        pass

try:
    from PIL import Image
except ImportError:  # 워크플로에서 설치 단계를 빠뜨렸을 때 원인이 바로 보이도록 main 에서 알린다.
    Image = None


# ----------------------------------------------------------------------------
# 경로와 상수
# ----------------------------------------------------------------------------

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA_DIR = os.path.join(ROOT, "data")
DECKS_PATH = os.path.join(DATA_DIR, "decks.json")
STATS_GLOB = os.path.join(DATA_DIR, "stats", "*.json")
ICON_DIR = os.path.join(DATA_DIR, "icons")
# 변환본 작업 폴더. 다음 실행의 캐시이며 커밋하지 않는다(스스로 .gitignore 를 둔다).
WORK_DIR = os.path.join(ICON_DIR, "tmp")
MANIFEST_PATH = os.path.join(ICON_DIR, "manifest.json")
ZIP_NAME = "icons.zip"
ZIP_PATH = os.path.join(ICON_DIR, ZIP_NAME)
CACHE_PATH = os.path.join(ROOT, "collector", ".icon_cache.json")
ASSETS_DIR = os.path.join(ROOT, "android", "app", "src", "main", "assets")
SNAPSHOT_ZIP = os.path.join(ASSETS_DIR, "icons.zip")
SNAPSHOT_MANIFEST = os.path.join(ASSETS_DIR, "icons_manifest.json")

DEFAULT_ASSET_BASE = "https://raw.communitydragon.org/latest/game/"
UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

# 앱 IconPack 이 이 값으로 형식을 판단한다. manifest 구조나 pack_hash() 정의를 바꾸면 올려야 한다.
SCHEMA_VERSION = 1
ICON_KEYS = ("icon", "iconUrl")

# 종류별 한 변 픽셀. 앱 IconInterceptor.kind() 와 같은 규칙이어야
# 같은 URL 을 여러 dp 로 그려도 메모리 캐시 한 항목을 함께 쓴다.
PX_CHAMPION = 128
PX_TRAIT = 32
PX_DEFAULT = 64
# 챔피언 칸 크기로 줄일 경로 조각. 앱 IconInterceptor.kind() 의 목록과 같아야 한다.
CHAMPION_MARKERS = ("/characters/", "/original-image/", "/img/tft/champions/", "/file/metatft/champions/")

WEBP_QUALITY = 80
WEBP_METHOD = 6
# 인코딩 설정이 바뀌면 캐시된 변환본을 다시 쓰지 않도록 캐시 항목에 함께 기록한다.
ENCODER = "webp-q%d-m%d" % (WEBP_QUALITY, WEBP_METHOD)
# zip 항목 날짜를 고정해야 내용이 같을 때 바이트도 같다(git 변경 없음).
ZIP_DATE = (1980, 1, 1, 0, 0, 0)

TRIES = 3
TIMEOUT = 30
WORKERS = 8
MAX_SOURCE_BYTES = 8 * 1024 * 1024


# ----------------------------------------------------------------------------
# 규칙
# ----------------------------------------------------------------------------

def icon_px(path):
    """
    경로로 종류를 가른다. 앱 IconInterceptor.kind() 와 같은 규칙.
    /original-image/ 는 pet 소환물 초상(lol.qq chess.js originalImage, 128x128 확인)이라
    절대 URL 로 오지만 챔피언 칸에 같은 크기로 그려지므로 챔피언과 같게 둔다.
    """
    lowered = path.lower()
    # lol.qq champions/{chessId}.png(pet 초상 96px)와 metatft champions/*.png 도 챔피언 칸에 그려진다.
    if any(marker in lowered for marker in CHAMPION_MARKERS):
        return PX_CHAMPION
    if "/traiticons/" in lowered:
        return PX_TRAIT
    return PX_DEFAULT


def is_absolute(path):
    return path.startswith("http://") or path.startswith("https://")


def normalize_key(value):
    """
    manifest 키. 앱 iconUrl() 이 상대 경로 앞 슬래시를 떼고 assetBase 뒤에 붙이므로,
    같은 방식으로 떼어 두어야 앱이 요청 URL 에서 키를 되찾을 수 있다.
    """
    value = value.strip()
    return value if is_absolute(value) else value.lstrip("/")


def source_url(key, asset_base):
    if is_absolute(key):
        return key
    return asset_base.rstrip("/") + "/" + key


def file_name(key):
    """원본 경로 문자열의 sha1 앞 12자리. 내용이 바뀌어도 이름은 같으므로 갱신은 manifest.hash 로 판단한다."""
    return hashlib.sha1(key.encode("utf-8")).hexdigest()[:12] + ".webp"


def sha1_hex(data):
    return hashlib.sha1(data).hexdigest()


def pack_hash(blobs):
    """
    팩 전체 해시. 앱(IconPack)이 zip 을 풀면서 같은 방식으로 다시 계산해 받은 파일을 검증한다.

    정의: 파일 이름순으로 "이름 + 공백 + 내용 sha1(hex)" 줄을 만들고, 줄바꿈(LF) 하나로 이은
    UTF-8 문자열의 sha1(hex). 내용만 모으지 않고 이름을 넣는 이유는 경로만 바뀌고 내용이 같은
    아이콘도 갱신으로 잡기 위해서다. 정의를 바꾸면 SCHEMA_VERSION 을 올려야 한다.
    """
    lines = ["%s %s" % (name, sha1_hex(blobs[name])) for name in sorted(blobs)]
    return sha1_hex("\n".join(lines).encode("utf-8"))


# ----------------------------------------------------------------------------
# 입력
# ----------------------------------------------------------------------------

def load_json(path):
    if not os.path.isfile(path):
        return None
    try:
        with io.open(path, encoding="utf-8") as fp:
            return json.load(fp, strict=False)
    except (OSError, ValueError) as exc:
        print("[경고] %s 를 읽지 못했다: %s" % (rel(path), exc), file=sys.stderr)
        return None


def collect_icons(node, out):
    """키가 icon/iconUrl 인 문자열 값을 재귀로 모은다. 계약상 icon 은 어느 깊이에나 올 수 있다."""
    if isinstance(node, dict):
        for key, value in node.items():
            if key in ICON_KEYS and isinstance(value, str):
                if value.strip():
                    out.add(normalize_key(value))
            else:
                collect_icons(value, out)
    elif isinstance(node, list):
        for item in node:
            collect_icons(item, out)


def rel(path):
    return os.path.relpath(path, ROOT).replace(os.sep, "/")


# ----------------------------------------------------------------------------
# 캐시
# ----------------------------------------------------------------------------

def open_previous_zip():
    """
    직전 팩. CI 러너는 매번 새로 체크아웃해 tmp 폴더가 비어 있으므로,
    커밋된 zip 이 변환본 캐시 역할을 한다(원본이 그대로면 다시 인코딩하지 않는다).
    """
    if not os.path.isfile(ZIP_PATH):
        return None
    try:
        return zipfile.ZipFile(ZIP_PATH)
    except (OSError, zipfile.BadZipFile) as exc:
        print("[경고] 직전 %s 를 열지 못했다: %s" % (rel(ZIP_PATH), exc), file=sys.stderr)
        return None


def cached_webp(entry, previous_zip):
    """캐시 항목이 가리키는 변환본을 tmp 폴더나 직전 zip 에서 찾는다. sha1 이 맞을 때만 쓴다."""
    name = entry.get("file")
    expected = entry.get("sha1")
    if not name or not expected:
        return None
    path = os.path.join(WORK_DIR, name)
    if os.path.isfile(path):
        with open(path, "rb") as fp:
            data = fp.read()
        if sha1_hex(data) == expected:
            return data
    if previous_zip is not None:
        try:
            data = previous_zip.read(name)
        except (KeyError, OSError, zipfile.BadZipFile, zlib.error):
            return None
        if sha1_hex(data) == expected:
            return data
    return None


# ----------------------------------------------------------------------------
# 받기와 변환
# ----------------------------------------------------------------------------

def download(url, entry, conditional):
    """
    원본 하나를 받는다. 쓸 수 있는 변환본이 있으면 조건부 요청(If-None-Match / If-Modified-Since)을
    보내 304 면 본문 없이 끝낸다(ETag·Last-Modified 가 같으면 다시 받지 않는다).
    반환 (status, body, etag, lastModified). 끝내 실패하면 RuntimeError.
    """
    headers = {"User-Agent": UA, "Accept": "image/png,image/*;q=0.8,*/*;q=0.5"}
    if conditional:
        if entry.get("etag"):
            headers["If-None-Match"] = entry["etag"]
        if entry.get("lastModified"):
            headers["If-Modified-Since"] = entry["lastModified"]

    last = None
    for attempt in range(TRIES):
        try:
            req = urllib.request.Request(url, headers=headers)
            with urllib.request.urlopen(req, timeout=TIMEOUT) as resp:
                body = resp.read(MAX_SOURCE_BYTES + 1)
                if len(body) > MAX_SOURCE_BYTES:
                    raise RuntimeError("원본이 %d바이트를 넘는다" % MAX_SOURCE_BYTES)
                return resp.status, body, resp.headers.get("ETag"), resp.headers.get("Last-Modified")
        except urllib.error.HTTPError as exc:
            if exc.code == 304:
                return 304, None, exc.headers.get("ETag"), exc.headers.get("Last-Modified")
            last = "HTTP %d" % exc.code
            # 404 같은 영구 오류는 다시 물어도 같다.
            if exc.code < 500 and exc.code not in (408, 429):
                break
        except (urllib.error.URLError, http.client.HTTPException, OSError, ValueError) as exc:
            last = str(exc) or exc.__class__.__name__
        if attempt < TRIES - 1:
            time.sleep(1.5 * (attempt + 1))
    raise RuntimeError(last or "알 수 없는 오류")


def open_rgba(body):
    """
    원본을 RGBA 로 연다. 서명은 파일 바이트가 아니라 픽셀로 계산한다 —
    CDN 이 PNG 를 무손실 재압축(cf-polished)해 바이트만 달라져도 같은 원본으로 보기 위해서.
    """
    with Image.open(io.BytesIO(body)) as img:
        img.load()
        rgba = img.convert("RGBA")
    signature = sha1_hex(("%dx%d|" % rgba.size).encode("ascii") + rgba.tobytes())
    return rgba, signature


def encode_webp(rgba, px):
    """긴 변을 px 로 줄인다(LANCZOS). 원본이 더 작으면 키우지 않는다 — 필요하면 앱 디코더가 늘린다."""
    width, height = rgba.size
    longest = max(width, height)
    if longest > px:
        scale = float(px) / longest
        size = (max(1, int(round(width * scale))), max(1, int(round(height * scale))))
        rgba = rgba.resize(size, Image.Resampling.LANCZOS)
    buf = io.BytesIO()
    rgba.save(buf, "WEBP", quality=WEBP_QUALITY, method=WEBP_METHOD)
    return buf.getvalue()


def process_icon(task):
    """
    아이콘 하나를 처리한다. 작업 스레드에서 돌며 예외를 던지지 않고 결과 dict 를 돌려준다.
    status: reused(원본이 그대로라 기존 변환본 사용) / converted(새로 변환) /
            stale(받기 실패, 기존 변환본 유지) / failed(쓸 것이 없음)
    """
    key, url, px = task["key"], task["url"], task["px"]
    entry, cached = task["entry"] or {}, task["cached"]
    result = {"key": key, "url": url, "px": px, "status": "failed", "data": None, "entry": None, "reason": None}

    def keep_stale(reason):
        # 원본을 못 받아도 예전 변환본이 있으면 팩에서 빼지 않는다(일시 장애로 앱 아이콘이 원격으로 떨어지지 않게).
        if cached is not None:
            result.update(status="stale", data=cached, entry=dict(entry), reason=reason)
        else:
            result["reason"] = reason
        return result

    try:
        status, body, etag, last_modified = download(url, entry, conditional=cached is not None)
    except RuntimeError as exc:
        return keep_stale(str(exc))

    if status == 304:
        renewed = dict(entry)
        if etag:
            renewed["etag"] = etag
        if last_modified:
            renewed["lastModified"] = last_modified
        result.update(status="reused", data=cached, entry=renewed)
        return result

    try:
        rgba, signature = open_rgba(body)
    except Exception as exc:  # Pillow 가 못 여는 응답(HTML 오류 페이지 등). 원인 종류가 많아 넓게 받는다.
        return keep_stale("이미지로 열 수 없다: %s" % exc)

    fresh = {"file": file_name(key), "px": px, "enc": ENCODER, "srcSha1": signature,
             "etag": etag, "lastModified": last_modified}
    if cached is not None and entry.get("srcSha1") == signature:
        # 픽셀이 같으면 다시 인코딩하지 않는다. libwebp 버전(로컬 Windows 와 CI Linux)이 달라
        # 같은 원본에서 다른 바이트가 나와 팩이 괜히 바뀌는 일을 막는다.
        fresh["sha1"] = entry["sha1"]
        result.update(status="reused", data=cached, entry=fresh)
        return result

    try:
        data = encode_webp(rgba, px)
    except Exception as exc:  # 인코더 오류도 아이콘 하나의 실패로만 다룬다.
        return keep_stale("WebP 변환 실패: %s" % exc)
    fresh["sha1"] = sha1_hex(data)
    result.update(status="converted", data=data, entry=fresh)
    return result


# ----------------------------------------------------------------------------
# 출력
# ----------------------------------------------------------------------------

def build_zip(blobs):
    """이름순, 고정 날짜·권한·OS 표기로 만든다. 같은 내용이면 같은 바이트가 나온다. 항목은 파일명만."""
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        for name in sorted(blobs):
            info = zipfile.ZipInfo(name, date_time=ZIP_DATE)
            info.compress_type = zipfile.ZIP_DEFLATED
            # 기본값이 Windows 0 / 그 외 3 이라 그대로 두면 러너 OS 마다 바이트가 달라진다.
            info.create_system = 3
            info.external_attr = 0o644 << 16
            zf.writestr(info, blobs[name], compresslevel=9)
    return buf.getvalue()


def write_bytes_atomic(path, data):
    """임시 파일에 쓰고 교체한다. 중간에 끊겨도 기존 파일이 깨지지 않는다."""
    os.makedirs(os.path.dirname(path), exist_ok=True)
    part = path + ".part"
    with open(part, "wb") as fp:
        fp.write(data)
    os.replace(part, path)


def write_text_atomic(path, text):
    # Windows 에서도 LF 로 쓴다(저장소 파일과 같게).
    write_bytes_atomic(path, text.encode("utf-8"))


def read_bytes(path):
    if not os.path.isfile(path):
        return None
    with open(path, "rb") as fp:
        return fp.read()


def copy_if_different(src, dst):
    data = read_bytes(src)
    if data is None or read_bytes(dst) == data:
        return False
    write_bytes_atomic(dst, data)
    return True


def sync_work_dir(blobs):
    """변환본을 tmp 폴더에 둔다(다음 실행의 캐시). 더 이상 쓰지 않는 변환본은 지운다."""
    os.makedirs(WORK_DIR, exist_ok=True)
    ignore = os.path.join(WORK_DIR, ".gitignore")
    if not os.path.isfile(ignore):
        write_text_atomic(ignore, "# build_icons.py 작업 폴더. 팩은 icons.zip 으로만 커밋한다.\n*\n")
    for name, data in blobs.items():
        path = os.path.join(WORK_DIR, name)
        if read_bytes(path) != data:
            write_bytes_atomic(path, data)
    for existing in os.listdir(WORK_DIR):
        if existing.endswith(".webp") and existing not in blobs:
            os.remove(os.path.join(WORK_DIR, existing))


def kb(n):
    return "%.1f KB" % (n / 1024.0)


# ----------------------------------------------------------------------------
# 실행
# ----------------------------------------------------------------------------

def main(argv=None):
    parser = argparse.ArgumentParser(description="아이콘 WebP 팩(icons.zip + manifest.json)을 만든다.")
    parser.add_argument("--snapshot", action="store_true",
                        help="android/app/src/main/assets 의 icons.zip·icons_manifest.json 도 갱신한다")
    parser.add_argument("--workers", type=int, default=WORKERS, help="동시 다운로드 수(기본 %d)" % WORKERS)
    args = parser.parse_args(argv)
    started = time.time()

    if Image is None:
        print("[치명] Pillow 가 없다. pip install pillow 후 다시 실행한다.", file=sys.stderr)
        return 1

    decks = load_json(DECKS_PATH)
    if not isinstance(decks, dict):
        print("[치명] %s 가 없어 아이콘 목록을 만들 수 없다. 기존 팩을 그대로 둔다." % rel(DECKS_PATH), file=sys.stderr)
        return 1
    asset_base = ((decks.get("version") or {}).get("assetBase") or DEFAULT_ASSET_BASE)

    sources = [DECKS_PATH] + sorted(glob(STATS_GLOB))
    keys = set()
    for path in sources:
        payload = decks if path == DECKS_PATH else load_json(path)
        if payload is not None:
            collect_icons(payload, keys)
    keys = sorted(keys)
    print("원천: %s" % ", ".join(rel(p) for p in sources))
    print("assetBase: %s" % asset_base)
    print("고유 아이콘: %d개" % len(keys))
    if not keys:
        print("[치명] icon 값이 하나도 없다. 기존 팩을 그대로 둔다.", file=sys.stderr)
        return 1

    cache = load_json(CACHE_PATH)
    cache = cache if isinstance(cache, dict) else {}
    previous_zip = open_previous_zip()
    try:
        previous_names = set(previous_zip.namelist()) if previous_zip is not None else None
        tasks = []
        for key in keys:
            px = icon_px(key)
            entry = cache.get(key) if isinstance(cache.get(key), dict) else None
            # 크기·인코딩·파일 이름 규칙이 바뀐 항목은 캐시를 믿지 않는다.
            if entry and (entry.get("px") != px or entry.get("enc") != ENCODER
                          or entry.get("file") != file_name(key)):
                entry = None
            cached = cached_webp(entry, previous_zip) if entry else None
            tasks.append({"key": key, "url": source_url(key, asset_base), "px": px,
                          "entry": entry, "cached": cached})
    finally:
        # Windows 에서는 열린 파일을 교체할 수 없으므로 쓰기 전에 닫는다.
        if previous_zip is not None:
            previous_zip.close()

    with ThreadPoolExecutor(max_workers=max(1, args.workers)) as pool:
        results = list(pool.map(process_icon, tasks))

    files, blobs, new_cache = {}, {}, {}
    counts = {"reused": 0, "converted": 0, "stale": 0, "failed": 0}
    by_px = {}
    failures, stale = [], []
    for res in results:
        counts[res["status"]] += 1
        if res["data"] is None:
            failures.append((res["url"], res["reason"]))
            continue
        if res["status"] == "stale":
            stale.append((res["url"], res["reason"]))
        name = res["entry"]["file"]
        files[res["key"]] = name
        blobs[name] = res["data"]
        new_cache[res["key"]] = {k: v for k, v in sorted(res["entry"].items()) if v is not None}
        bucket = by_px.setdefault(res["px"], [0, 0])
        bucket[0] += 1
        bucket[1] += len(res["data"])

    print("처리: 재사용 %d · 새로 변환 %d · 받기 실패했지만 기존본 유지 %d · 실패 %d"
          % (counts["reused"], counts["converted"], counts["stale"], counts["failed"]))
    for px in sorted(by_px, reverse=True):
        print("  %dpx: %d개 %s" % (px, by_px[px][0], kb(by_px[px][1])))

    if not blobs:
        print("[치명] 쓸 수 있는 아이콘이 하나도 없다. 기존 팩을 그대로 둔다.", file=sys.stderr)
        return 1
    if counts["failed"] > len(keys) // 2:
        print("[치명] 절반 넘게 실패했다(%d/%d). 네트워크 문제로 보고 기존 팩을 그대로 둔다."
              % (counts["failed"], len(keys)), file=sys.stderr)
        for url, reason in failures[:20]:
            print("  - %s: %s" % (url, reason), file=sys.stderr)
        return 1

    sync_work_dir(blobs)

    digest = pack_hash(blobs)
    webp_bytes = sum(len(b) for b in blobs.values())
    previous = load_json(MANIFEST_PATH)
    unchanged = (isinstance(previous, dict)
                 and previous.get("schemaVersion") == SCHEMA_VERSION
                 and previous.get("hash") == digest
                 and previous.get("files") == files
                 and previous_names == set(blobs))

    if unchanged:
        zip_bytes = os.path.getsize(ZIP_PATH)
        print("팩: 변경 없음(hash %s) — %s, %s 를 다시 쓰지 않는다" % (digest[:12], rel(MANIFEST_PATH), rel(ZIP_PATH)))
    else:
        archive = build_zip(blobs)
        zip_bytes = len(archive)
        manifest = {
            "schemaVersion": SCHEMA_VERSION,
            "hash": digest,
            "generatedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
            "count": len(blobs),
            # 앱이 내려받는 zip 의 크기.
            "bytes": zip_bytes,
            "zip": ZIP_NAME,
            "files": files,
        }
        write_bytes_atomic(ZIP_PATH, archive)
        # zip 을 먼저 쓰고 manifest 를 나중에 쓴다. 중간에 끊기면 다음 실행에서 hash 가 달라 다시 쓴다.
        write_text_atomic(MANIFEST_PATH, json.dumps(manifest, ensure_ascii=False, indent=1, sort_keys=True) + "\n")
        print("팩: 갱신(hash %s) — %s, %s" % (digest[:12], rel(MANIFEST_PATH), rel(ZIP_PATH)))

    print("합계: %d개 · WebP %s · zip %s (%d바이트)" % (len(blobs), kb(webp_bytes), kb(zip_bytes), zip_bytes))

    cache_text = json.dumps(new_cache, ensure_ascii=False, indent=1, sort_keys=True) + "\n"
    if read_bytes(CACHE_PATH) != cache_text.encode("utf-8"):
        write_text_atomic(CACHE_PATH, cache_text)
        print("캐시: %s 갱신" % rel(CACHE_PATH))

    if args.snapshot:
        changed = [rel(dst) for src, dst in ((ZIP_PATH, SNAPSHOT_ZIP), (MANIFEST_PATH, SNAPSHOT_MANIFEST))
                   if copy_if_different(src, dst)]
        print("스냅샷: %s" % (", ".join(changed) if changed else "변경 없음"))

    if stale:
        print("[경고] 원본을 받지 못해 기존 변환본을 유지한 아이콘 %d개:" % len(stale))
        for url, reason in stale:
            print("  - %s: %s" % (url, reason))
    if failures:
        print("[경고] 팩에서 빠진 아이콘 %d개(앱은 원격으로 받는다):" % len(failures))
        for url, reason in failures:
            print("  - %s: %s" % (url, reason))
    print("소요 시간: %.1f초" % (time.time() - started))
    return 0


if __name__ == "__main__":
    sys.exit(main())
