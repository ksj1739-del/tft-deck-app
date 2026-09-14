#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
lol.qq.com/tft 덱 데이터를 한국어로 변환하고 metatft와 대조해 decks.json을 만든다.

앱은 이 스크립트의 결과물만 받는다. 시즌이 바뀌거나 원본 스키마가 흔들려도
여기만 고치면 되고 앱은 재배포하지 않아도 된다.

의존성 없음 (표준 라이브러리만 사용).
"""

import gzip
import hashlib
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone

# Windows 콘솔(cp949)에서 한글/기호 출력 시 죽지 않도록.
for _stream in (sys.stdout, sys.stderr):
    try:
        _stream.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, ValueError):
        pass


# ----------------------------------------------------------------------------
# 원본 엔드포인트
# ----------------------------------------------------------------------------

MODE_REGISTRY = "https://lol.qq.com/tft/js/tft-mode-registry.js"
LINEUP_TMPL = ("https://game.gtimg.cn/images/lol/act/tftzlkauto/json/"
               "lineupJson/{set_id}/6/lineup_detail_total.json")
CDRAGON_KO = "https://raw.communitydragon.org/latest/cdragon/tft/ko_kr.json"
CDRAGON_EN = "https://raw.communitydragon.org/latest/cdragon/tft/en_us.json"
CDRAGON_ASSET = "https://raw.communitydragon.org/latest/game/"
TEAMPLANNER = ("https://raw.communitydragon.org/latest/plugins/rcp-be-lol-game-data/"
               "global/default/v1/tftchampions-teamplanner.json")
META_CLUSTER = "https://api-hc.metatft.com/tft-comps-api/latest_cluster_info"
META_VERSION = "https://api-hc.metatft.com/tft-comps-api/latest_cluster_id"

UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

# metatft 클러스터와 같은 덱으로 볼 최소 자카드 유사도.
# 실측 분포가 0.31~0.45(미등재) / 0.55~1.00(등재)로 뚜렷하게 갈려 0.5가 안전하다.
SIMILARITY_THRESHOLD = 0.5

# lol.qq의 특성 color 5는 챔피언 고유 특성이라 덱 이름/시너지 표시에서 제외한다.
UNIQUE_TRAIT_STYLE = 5

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "data")


# ----------------------------------------------------------------------------
# 네트워크
# ----------------------------------------------------------------------------

class SourceError(Exception):
    """원본 하나를 끝내 가져오지 못했을 때."""


def fetch(url, tries=3, timeout=60):
    """재시도 + gzip 해제. 실패하면 SourceError."""
    last = None
    for attempt in range(tries):
        try:
            req = urllib.request.Request(url, headers={
                "User-Agent": UA,
                "Accept": "application/json, text/javascript, */*",
                "Accept-Encoding": "gzip",
                "Referer": "https://www.metatft.com/" if "metatft" in url else "https://lol.qq.com/tft/",
            })
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                raw = resp.read()
                if resp.headers.get("Content-Encoding") == "gzip":
                    raw = gzip.decompress(raw)
                return raw.decode("utf-8", errors="replace")
        except (urllib.error.URLError, OSError, ValueError) as exc:
            last = exc
            if attempt < tries - 1:
                time.sleep(2 * (attempt + 1))
    raise SourceError("%s -> %s" % (url, last))


def fetch_json(url, **kw):
    return json.loads(fetch(url, **kw), strict=False)


# ----------------------------------------------------------------------------
# 사전 (CommunityDragon)
# ----------------------------------------------------------------------------

ITEM_PREFIX = re.compile(r"^(TFT\d*_Item_|TFT_Item_|TFT\d*_Augment_|TFT\d*_|DA_)", re.I)


def item_key(api_name):
    """lol.qq의 DA_InfinityEdge와 사전의 TFT_Item_InfinityEdge를 같은 키로 접는다."""
    return ITEM_PREFIX.sub("", api_name or "").lower()


def asset_path(path):
    """
    CommunityDragon .tex 경로를 png 상대 경로로.
    공통 접두사는 version.assetBase에 한 번만 담는다 (덱 전체로 80 KB 넘게 절약).
    """
    if not path:
        return None
    return re.sub(r"\.(tex|dds)$", ".png", path.lower())


class Dictionary:
    """챔피언/특성/아이템 ID를 한국어 이름과 아이콘으로 푼다."""

    def __init__(self, ko, en, set_number):
        ko_set = self._pick_set(ko, set_number)
        en_set = self._pick_set(en, set_number)

        self.champions = {c["apiName"]: c for c in ko_set.get("champions", [])}
        self.traits = {t["apiName"]: t for t in ko_set.get("traits", [])}
        self.champions_en = {c["apiName"]: c for c in en_set.get("champions", [])}
        self.traits_en = {t["apiName"]: t for t in en_set.get("traits", [])}

        self.items, self.items_en = {}, {}
        for entry in ko.get("items", []):
            if entry.get("apiName") and entry.get("name"):
                self.items.setdefault(item_key(entry["apiName"]), entry)
        for entry in en.get("items", []):
            if entry.get("apiName") and entry.get("name"):
                self.items_en.setdefault(item_key(entry["apiName"]), entry)

        # 실제로 등장한 것만 catalog에 싣는다. 레벨별 배치를 참조로 줄여도
        # 거기 쓰인 챔피언이 catalog에서 빠지지 않도록 해석 시점에 기록한다.
        self.seen = {"champions": {}, "traits": {}, "items": {}, "augments": {}}

    def _remember(self, bucket, record):
        if record["id"]:
            self.seen[bucket].setdefault(record["id"], record)
        return record

    @staticmethod
    def _pick_set(blob, set_number):
        sets = blob.get("sets", {})
        if str(set_number) in sets:
            return sets[str(set_number)]
        # 세트 번호를 못 찾으면 가장 높은 번호로 대체한다.
        numeric = sorted((k for k in sets if k.replace(".", "").isdigit()), key=float)
        return sets[numeric[-1]] if numeric else {}

    # -- champion ------------------------------------------------------------
    def champion(self, api_name):
        ko = self.champions.get(api_name)
        en = self.champions_en.get(api_name)
        record = {
            "id": api_name,
            "name": (ko or {}).get("name") or api_name,
            "nameEn": (en or {}).get("name"),
            "cost": (ko or {}).get("cost"),
            "icon": asset_path((ko or {}).get("tileIcon")),
            "translated": ko is not None,
        }
        return self._remember("champions", record)

    # -- trait ---------------------------------------------------------------
    def trait(self, api_name):
        ko = self.traits.get(api_name)
        en = self.traits_en.get(api_name)
        record = {
            "id": api_name,
            "name": (ko or {}).get("name") or api_name,
            "nameEn": (en or {}).get("name"),
            "icon": asset_path((ko or {}).get("icon")),
            "translated": ko is not None,
        }
        return self._remember("traits", record)

    # -- item / augment ------------------------------------------------------
    def item(self, api_name):
        key = item_key(api_name)
        ko = self.items.get(key)
        en = self.items_en.get(key)
        record = {
            "id": api_name,
            "name": (ko or {}).get("name") or api_name,
            "nameEn": (en or {}).get("name"),
            "icon": asset_path((ko or {}).get("icon")),
            "components": (ko or {}).get("composition") or [],
            "isAugment": bool((ko or {}).get("isAugment")),
            "translated": ko is not None,
        }
        return self._remember("augments" if record["isAugment"] else "items", record)


# ----------------------------------------------------------------------------
# lol.qq 파싱
# ----------------------------------------------------------------------------

def current_set_id():
    """tft-mode-registry.js에서 CurrentSet을 읽는다. 경로를 하드코딩하지 않기 위해."""
    js = fetch(MODE_REGISTRY)
    found = re.search(r"CurrentSet\s*=\s*['\"](s\d+)['\"]", js)
    if not found:
        raise SourceError("tft-mode-registry.js에서 CurrentSet을 찾지 못했다")
    return found.group(1)


def split_ids(raw):
    """'A,B,C' 또는 None을 리스트로."""
    if not raw:
        return []
    return [part.strip() for part in str(raw).split(",") if part.strip()]


def parse_location(raw):
    """'3,5' -> (3, 5). 형식이 깨지면 None."""
    parts = split_ids(raw)
    if len(parts) != 2:
        return None
    try:
        return int(parts[0]), int(parts[1])
    except ValueError:
        return None


def slim(record):
    """덱 안에 박히는 사본. 조합식/영문명 등 부가 정보는 catalog에만 둔다."""
    out = {"id": record["id"], "name": record["name"]}
    if record.get("icon"):
        out["icon"] = record["icon"]
    return out


def build_unit(entry, dic):
    """hero_location 한 칸을 앱이 쓸 형태로."""
    champ = dic.champion(entry.get("hero_id", ""))
    replace = entry.get("equipment_replace") or {}
    if not isinstance(replace, dict):
        replace = {}

    main_ids = split_ids(entry.get("equipment_id"))
    backup_ids = [i for i in split_ids(replace.get("backup")) if i not in main_ids]
    position = parse_location(entry.get("location"))

    try:
        star = int(entry.get("numStar") or 1)
    except (TypeError, ValueError):
        star = 1

    return {
        "id": champ["id"],
        "name": champ["name"],
        "nameEn": champ["nameEn"],
        "cost": champ["cost"],
        "icon": champ["icon"],
        "star": star,
        "carry": bool(entry.get("is_carry_hero")),
        "row": position[0] if position else None,
        "col": position[1] if position else None,
        "items": [slim(dic.item(i)) for i in main_ids],
        "itemsBackup": [slim(dic.item(i)) for i in backup_ids],
    }


def build_board(entries, dic):
    return [build_unit(e, dic) for e in (entries or []) if e.get("hero_id")]


def compact_board(entries, dic):
    """
    레벨별 배치와 초/중반 운영은 유닛의 '어디에 몇 성으로' 만 다르다.
    이름/아이콘/코스트는 catalog에 이미 있으므로 참조만 남겨 용량을 줄인다.
    """
    out = []
    for unit in build_board(entries, dic):
        row = {"id": unit["id"], "star": unit["star"]}
        if unit["row"] is not None:
            row["row"], row["col"] = unit["row"], unit["col"]
        if unit["carry"]:
            row["carry"] = True
        if unit["items"]:
            row["items"] = [i["id"] for i in unit["items"]]
        out.append(row)
    return out


def build_traits(entries, dic, line_name=""):
    """
    활성 시너지만. 챔피언 고유 특성(style 5)은 뺀다.

    덱에 2단계 시너지가 여럿이면 무엇이 핵심인지 숫자만으로는 갈리지 않는다.
    lol.qq의 중국어 덱 이름(line_name)에 들어간 시너지가 작성자가 꼽은 핵심이므로
    그것을 앞으로 보낸다. 한국어 덱 이름도 이 순서로 만들어진다.
    """
    out = []
    for entry in entries or []:
        try:
            style = int(entry.get("color") or 0)
            count = int(entry.get("num") or 0)
        except (TypeError, ValueError):
            continue
        if style >= UNIQUE_TRAIT_STYLE:
            continue
        row = slim(dic.trait(entry.get("id", "")))
        row["count"] = count
        row["style"] = style
        # entry["name"]은 중국어 원문이다. 덱 이름에 등장하는지 여기서만 쓴다.
        row["_featured"] = bool(entry.get("name")) and str(entry["name"]) in (line_name or "")
        out.append(row)

    # 숫자가 큰 시너지가 먼저다 ("5 요들"처럼 사람들이 부르는 순서).
    # 같은 숫자끼리 갈리지 않을 때만 작성자가 꼽은 것을 앞에 둔다.
    out.sort(key=lambda t: (-t["count"], not t["_featured"], -t["style"], t["name"]))
    for row in out:
        del row["_featured"]
    return out


def korean_deck_name(carry, traits, fallback):
    """
    중국어 원문은 '【별명】3특성2특성…' 형태라 별명이 번역되지 않는다.
    특성은 100% 번역되므로 '캐리 · 3 특성 2 특성'으로 새로 만든다.
    """
    composition = " ".join("%d %s" % (t["count"], t["name"]) for t in traits[:4])
    if carry and composition:
        return "%s · %s" % (carry["name"], composition)
    return composition or (carry or {}).get("name") or fallback


def pick_carry(units):
    """is_carry_hero가 있으면 그것, 없으면 아이템을 가장 많이 든 고코스트 유닛."""
    flagged = [u for u in units if u["carry"]]
    if flagged:
        return flagged[0]
    with_items = [u for u in units if u["items"]]
    if not with_items:
        return units[0] if units else None
    return max(with_items, key=lambda u: (len(u["items"]), u["cost"] or 0, u["star"]))


TIER_ORDER = {"SS": 0, "S": 1, "A": 2, "B": 3, "C": 4, "D": 5}


def build_deck(raw, dic):
    """lineup_list 한 항목 -> 앱이 쓰는 덱 하나."""
    # detail은 JSON 문자열 안에 든 JSON이고 제어문자가 섞여 있다.
    detail = json.loads(raw.get("detail") or "{}", strict=False)

    units = build_board(detail.get("hero_location"), dic)
    traits = build_traits(detail.get("contact"), dic, detail.get("line_name") or "")
    carry = pick_carry(units)
    hexbuff = detail.get("hexbuff") or {}
    if not isinstance(hexbuff, dict):
        hexbuff = {}

    boards = {}
    for level, key in (("6", "hero_location_l6"), ("8", "hero_location_l8"), ("9", "hero_location_l9")):
        board = compact_board(detail.get(key), dic)
        if board:
            boards[level] = board

    try:
        final_level = int(detail.get("needLevel") or 0) or None
    except (TypeError, ValueError):
        final_level = None

    tier = (raw.get("quality") or "").upper()
    author = raw.get("lineupauthor_data") or {}

    return {
        "id": str(raw.get("id")),
        "name": korean_deck_name(carry, traits, str(raw.get("id"))),
        "nameCn": detail.get("line_name") or "",
        "tier": tier,
        "tierOrder": TIER_ORDER.get(tier, 9),
        "patch": raw.get("simulator_edition") or "",
        "finalLevel": final_level,
        "carryId": (carry or {}).get("id"),
        "traits": traits,
        "units": units,
        "boards": boards,
        "early": compact_board(detail.get("y21_early_heros"), dic),
        "mid": compact_board(detail.get("y21_metaphase_heros"), dic),
        "itemOrder": [slim(dic.item(i)) for i in split_ids(detail.get("equipment_order"))],
        "augments": {
            "recommended": [slim(dic.item(i)) for i in split_ids(hexbuff.get("recomm"))],
            "alternatives": [slim(dic.item(i)) for i in split_ids(hexbuff.get("replace"))],
        },
        # 작성자가 쓴 중국어 자유 서술. ID 사전으로 번역되지 않아 원문 그대로 둔다.
        "notesCn": {
            "items": (detail.get("equipment_info") or "").strip(),
            "augments": (detail.get("hex_info") or "").strip(),
        },
        "author": (author.get("name") or "").strip(),
        "updatedAt": raw.get("rel_time") or raw.get("update_time") or "",
    }


# ----------------------------------------------------------------------------
# 팀 플래너 덱 코드
# ----------------------------------------------------------------------------
#
# 인게임 팀 플래너에 붙여넣는 코드. 공개 문서(gist)는 세트 13 기준이라 현재와
# 다르다 — 실제 포맷은 tftguide.org 팀빌더 구현과 Riot 데이터로 대조해 확인했다.
#
#   "02" + 챔피언마다 team_planner_code를 소문자 hex 3자리 + "0"으로 32자 채움 + "TFTSet18"
#
#   예) DA_18_Akali_AD -> team_planner_code 1002 -> hex 3ea
#
# 슬롯은 10개(32 = 접두사 2 + 10 x 3)이고 빈 슬롯은 "000"이다.
# 팀 플래너에 없는 챔피언(소환수 등)은 코드에 넣을 수 없어 제외한다.

CODE_PREFIX = "02"
CODE_SLOTS = 10
CODE_WIDTH = 3
CODE_LENGTH = len(CODE_PREFIX) + CODE_SLOTS * CODE_WIDTH  # 32


def team_planner_codes(set_number):
    """character_id -> 3자리 hex 코드. 실패하면 빈 dict(덱 코드만 생략)."""
    blob = fetch_json(TEAMPLANNER, timeout=60)
    key = "TFTSet%s" % set_number
    if key not in blob:
        available = [k for k in blob if k.startswith("TFTSet")]
        raise SourceError("팀플래너 데이터에 %s가 없다 (있는 것: %s)" % (key, ", ".join(available)))

    codes = {}
    for champ in blob[key]:
        raw = champ.get("team_planner_code")
        cid = champ.get("character_id")
        if cid is None or raw is None:
            continue
        try:
            codes[cid] = format(int(raw), "0%dx" % CODE_WIDTH)
        except (TypeError, ValueError):
            continue
    return codes


def deck_code(units, codes, set_number):
    """
    덱의 유닛으로 팀 플래너 코드를 만든다.
    코드가 없는 유닛은 건너뛰고, 10칸이 넘으면 캐리와 고코스트를 우선 남긴다.
    """
    if not codes:
        return None

    usable = [u for u in units if u["id"] in codes]
    if not usable:
        return None

    # 팀 플래너에 보이는 순서 = 여기서 넣는 순서. 캐리를 맨 앞에 둔다.
    usable.sort(key=lambda u: (not u["carry"], -(u["cost"] or 0), u["name"]))
    dropped = max(0, len(usable) - CODE_SLOTS)
    usable = usable[:CODE_SLOTS]

    body = CODE_PREFIX + "".join(codes[u["id"]] for u in usable)
    return {
        "code": body.ljust(CODE_LENGTH, "0") + "TFTSet%s" % set_number,
        "units": len(usable),
        # 소환수처럼 상점에 없는 유닛은 코드로 표현할 수 없다. 앱에서 안내한다.
        "omitted": [u["name"] for u in units if u["id"] not in codes] or None,
        "truncated": dropped or None,
    }


# ----------------------------------------------------------------------------
# metatft 대조
# ----------------------------------------------------------------------------

def metatft_comps(cluster_blob):
    """클러스터에서 (유닛 ID 집합, 표시 이름) 목록을 뽑는다."""
    details = (cluster_blob.get("cluster_info") or {}).get("cluster_details") or {}
    comps = []
    for cluster in details.get("clusters") or []:
        units = {u.strip() for u in (cluster.get("units_string") or "").split(",") if u.strip()}
        if units:
            comps.append((units, (cluster.get("name_string") or "").strip()))
    return comps


def compare_with_metatft(decks, comps):
    """
    덱의 정체성은 최종 배치의 챔피언 ID 집합이다.
    이름(중국어 vs 영어)이 아니라 구성으로 비교해야 의미가 있다.
    """
    for deck in decks:
        mine = {u["id"] for u in deck["units"] if u["id"]}
        best_score, best_name = 0.0, None

        if mine:
            for units, name in comps:
                union = mine | units
                if not union:
                    continue
                score = len(mine & units) / len(union)
                if score > best_score:
                    best_score, best_name = score, name

        deck["metatft"] = {
            "similarity": round(best_score, 3),
            "matchedComp": best_name if best_score >= SIMILARITY_THRESHOLD else None,
            # 비교 데이터를 못 받았으면 '중국 한정'이라 단정하지 않는다.
            "onlyInChina": bool(comps) and best_score < SIMILARITY_THRESHOLD,
            "compared": bool(comps),
        }
    return decks


# ----------------------------------------------------------------------------
# 검색 역인덱스
# ----------------------------------------------------------------------------

def build_index(decks):
    """
    앱은 계산하지 않고 조회만 한다. 아이템->덱 검색이 이 앱의 핵심 기능이라
    수집 단계에서 미리 만들어 둔다.
    """
    items, components, champions, traits, augments = {}, {}, {}, {}, {}

    def add(bucket, key, value):
        if key:
            bucket.setdefault(key, []).append(value)

    for deck in decks:
        did = deck["id"]

        for unit in deck["units"]:
            add(champions, unit["name"], did)
            for item in unit["items"]:
                add(items, item["name"], {"deck": did, "unit": unit["name"], "role": "main"})
            for item in unit["itemsBackup"]:
                add(items, item["name"], {"deck": did, "unit": unit["name"], "role": "backup"})

        for item in deck["itemOrder"]:
            add(components, item["name"], did)
        for trait in deck["traits"]:
            add(traits, trait["name"], did)
        for augment in deck["augments"]["recommended"]:
            add(augments, augment["name"], did)

    # 챔피언/특성은 덱 중복 제거
    champions = {k: sorted(set(v)) for k, v in champions.items()}
    traits = {k: sorted(set(v)) for k, v in traits.items()}
    components = {k: sorted(set(v)) for k, v in components.items()}
    augments = {k: sorted(set(v)) for k, v in augments.items()}

    return {
        "item": items,
        "component": components,
        "champion": champions,
        "trait": traits,
        "augment": augments,
    }


def build_catalog(dic):
    """
    검색 자동완성과 배치 참조 해석에 쓰는 목록.
    레벨별 배치를 ID 참조로 줄였기 때문에 앱은 여기서 이름과 아이콘을 찾는다.
    """
    def finish(bucket):
        rows = []
        for record in sorted(bucket.values(), key=lambda r: r["name"]):
            row = {"id": record["id"], "name": record["name"]}
            if record.get("nameEn") and record["nameEn"] != record["name"]:
                row["nameEn"] = record["nameEn"]
            if record.get("icon"):
                row["icon"] = record["icon"]
            if record.get("cost") is not None:
                row["cost"] = record["cost"]
            if record.get("components"):
                row["components"] = record["components"]
            rows.append(row)
        return rows

    return {name: finish(bucket) for name, bucket in dic.seen.items()}


def untranslated_ids(dic):
    """한글로 안 풀린 ID. 신규 챔피언/아이템 추가를 조기에 발견하기 위한 지표."""
    missing = set()
    for bucket in dic.seen.values():
        for record in bucket.values():
            if not record.get("translated"):
                missing.add(record["id"])
    return sorted(missing)


# ----------------------------------------------------------------------------
# 실행
# ----------------------------------------------------------------------------

def read_previous():
    path = os.path.join(OUT_DIR, "decks.json")
    if not os.path.exists(path):
        return None
    try:
        with open(path, encoding="utf-8") as fp:
            return json.load(fp)
    except (OSError, ValueError):
        return None


def write_output(payload):
    os.makedirs(OUT_DIR, exist_ok=True)

    body = json.dumps(payload, ensure_ascii=False, separators=(",", ":"), sort_keys=True)
    digest = hashlib.sha256(body.encode("utf-8")).hexdigest()[:16]
    payload["version"]["contentHash"] = digest

    with open(os.path.join(OUT_DIR, "decks.json"), "w", encoding="utf-8") as fp:
        json.dump(payload, fp, ensure_ascii=False, separators=(",", ":"), sort_keys=True)

    # 앱은 이 작은 파일만 먼저 받아 갱신 여부를 판단한다.
    with open(os.path.join(OUT_DIR, "version.json"), "w", encoding="utf-8") as fp:
        json.dump(payload["version"], fp, ensure_ascii=False, indent=2, sort_keys=True)

    return digest


def main():
    started = datetime.now(timezone.utc)
    status = {}

    # --- 시즌 확인 ---------------------------------------------------------
    try:
        set_id = current_set_id()
        status["modeRegistry"] = "ok"
    except SourceError as exc:
        previous = read_previous()
        if not previous:
            print("[치명] 시즌을 확인할 수 없고 이전 결과도 없다: %s" % exc, file=sys.stderr)
            return 1
        set_id = previous["version"]["set"]
        status["modeRegistry"] = "stale"
        print("[경고] CurrentSet 확인 실패, 이전 값 %s 사용: %s" % (set_id, exc), file=sys.stderr)

    set_number = re.sub(r"\D", "", set_id) or "18"
    print("시즌: %s (세트 %s)" % (set_id, set_number))

    # --- 덱 원본 (없으면 진행 불가) ----------------------------------------
    try:
        lineup = fetch_json(LINEUP_TMPL.format(set_id=set_id))
        status["lolqq"] = "ok"
    except SourceError as exc:
        print("[치명] lol.qq 덱 데이터를 가져오지 못했다: %s" % exc, file=sys.stderr)
        print("       이전 decks.json을 그대로 둔다.", file=sys.stderr)
        return 1

    raw_decks = lineup.get("lineup_list") or []
    if not raw_decks:
        print("[치명] 덱 목록이 비어 있다. 스키마가 바뀌었을 수 있다.", file=sys.stderr)
        return 1
    print("덱 원본: %d개" % len(raw_decks))

    # --- 한국어 사전 (없으면 진행 불가) ------------------------------------
    try:
        ko = fetch_json(CDRAGON_KO, timeout=120)
        status["namesKo"] = "ok"
    except SourceError as exc:
        print("[치명] 한국어 사전을 가져오지 못했다: %s" % exc, file=sys.stderr)
        return 1

    try:
        en = fetch_json(CDRAGON_EN, timeout=120)
        status["namesEn"] = "ok"
    except SourceError as exc:
        en = {"sets": {}, "items": []}
        status["namesEn"] = "missing"
        print("[경고] 영문 사전 생략(검색 별칭만 줄어든다): %s" % exc, file=sys.stderr)

    dic = Dictionary(ko, en, set_number)

    # --- 덱 코드 (없어도 진행) ----------------------------------------------
    try:
        codes = team_planner_codes(set_number)
        status["teamPlanner"] = "ok"
        print("팀 플래너 코드: %d개 챔피언" % len(codes))
    except SourceError as exc:
        codes = {}
        status["teamPlanner"] = "missing"
        print("[경고] 덱 코드 생성 생략: %s" % exc, file=sys.stderr)

    # --- metatft (없어도 진행) ---------------------------------------------
    comps, meta_version = [], {}
    try:
        comps = metatft_comps(fetch_json(META_CLUSTER, timeout=90))
        meta_version = fetch_json(META_VERSION, timeout=30)
        status["metatft"] = "ok"
        print("metatft 클러스터: %d개" % len(comps))
    except SourceError as exc:
        status["metatft"] = "missing"
        print("[경고] metatft 대조 생략. 중국 한정 배지는 표시하지 않는다: %s" % exc, file=sys.stderr)

    # --- 변환 ---------------------------------------------------------------
    decks, failed = [], 0
    for raw in raw_decks:
        try:
            decks.append(build_deck(raw, dic))
        except (ValueError, KeyError, TypeError) as exc:
            failed += 1
            print("[경고] 덱 %s 파싱 실패: %s" % (raw.get("id"), exc), file=sys.stderr)

    if not decks:
        print("[치명] 변환에 성공한 덱이 없다.", file=sys.stderr)
        return 1

    for deck in decks:
        deck["teamCode"] = deck_code(deck["units"], codes, set_number)

    compare_with_metatft(decks, comps)
    decks.sort(key=lambda d: (d["tierOrder"], d["name"]))

    missing = untranslated_ids(dic)
    only_china = [d for d in decks if d["metatft"]["onlyInChina"]]
    coded = sum(1 for d in decks if d["teamCode"])

    payload = {
        "version": {
            "generatedAt": started.strftime("%Y-%m-%dT%H:%M:%SZ"),
            "set": set_id,
            "setNumber": int(set_number),
            "patch": decks[0]["patch"],
            "deckCount": len(decks),
            "onlyInChinaCount": len(only_china),
            "teamCodeCount": coded,
            "metatftSet": meta_version.get("tft_set"),
            "metatftClusterId": meta_version.get("cluster_id"),
            # 아이콘 경로는 상대 경로로 저장한다. 앱이 여기에 이어 붙인다.
            "assetBase": CDRAGON_ASSET,
            "sources": status,
            "untranslatedIds": missing,
            "contentHash": "",
        },
        "decks": decks,
        "index": build_index(decks),
        "catalog": build_catalog(dic),
    }

    digest = write_output(payload)

    print("-" * 58)
    print("덱 %d개 (파싱 실패 %d)" % (len(decks), failed))
    print("중국 한정 %d개%s" % (len(only_china), "" if comps else " (대조 생략)"))
    for deck in only_china:
        print("   · %-42s %s" % (deck["name"][:42], deck["tier"]))
    if missing:
        print("미번역 ID %d개: %s" % (len(missing), ", ".join(missing[:6])))
    size = os.path.getsize(os.path.join(OUT_DIR, "decks.json"))
    print("decks.json %.1f KB · hash %s" % (size / 1024.0, digest))
    return 0


if __name__ == "__main__":
    sys.exit(main())
