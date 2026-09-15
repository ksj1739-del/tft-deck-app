#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
도감(챔피언·특성·아이템·증강) 통계를 모아 앱이 조회만 하면 되는 JSON으로 만든다.

  python collector/fetch_stats.py              # data/stats/*.json, data/flags.json 갱신
  python collector/fetch_stats.py --snapshot   # + 앱 동봉 스냅샷(android/app/src/main/assets/stats/)

출력(설계 §5.3~§5.7)
  data/stats/version.json     앱이 먼저 받는 작은 파일(files.<이름> 해시로 바뀐 파일만 내려받는다)
  data/stats/champions.json   metatft 3스코프 + lol.qq 2스코프, 추천 아이템·빌드, 스킬·기본 능력치
  data/stats/traits.json      단계별 통계, 효과, 소속 챔피언, lol.qq 2특성 조합 추세
  data/stats/items.json       종류·통계·착용 상위·완성 스테이지, 조합표
  data/stats/augments.json    에디터 티어·태그·희귀도, decks.json 덱별 성적 역인덱스
  data/stats/prev_snapshot.json  이전 패치 마지막 값(패치가 바뀐 첫 실행에 동결)
  data/flags.json             원격 플래그(liveSpectateAvailable)

덱 수집기(fetch_decks.py)와 코드를 공유하지 않고, data/decks.json은 읽기만 한다.
의존성 없음(표준 라이브러리만).
"""

import argparse
import json
import os
import re
import shutil
import sys
import time
from datetime import datetime, timezone

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
if HERE not in sys.path:
    sys.path.insert(0, HERE)

from stats import build, cdragon, load_constants, lolqq, metatft  # noqa: E402
from stats.net import Client, SourceError  # noqa: E402

# Windows 콘솔(cp949)에서 한글/기호 출력 시 죽지 않도록.
for _stream in (sys.stdout, sys.stderr):
    try:
        _stream.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, ValueError):
        pass

DATA_DIR = os.path.join(ROOT, "data")
ASSETS_DIR = os.path.join(ROOT, "android", "app", "src", "main", "assets", "stats")
OVERRIDES = os.path.join(HERE, "overrides_ko.json")
SNAPSHOT_FILES = ("version",) + build.FILE_NAMES

SOURCE_KEYS = ("metatftStats", "metatftDetail", "lolqqDatasearch", "namesKo",
               "metatftLookup", "augmentsTiers", "decksJson", "lolqqStatic")


def parse_args(argv):
    parser = argparse.ArgumentParser(description="도감 통계 수집")
    parser.add_argument("--snapshot", action="store_true",
                        help="결과 다섯 파일을 android/app/src/main/assets/stats/에도 복사한다")
    parser.add_argument("--out", default=DATA_DIR, help="출력 루트(기본 data/)")
    parser.add_argument("--decks", default=os.path.join(DATA_DIR, "decks.json"),
                        help="읽기만 하는 덱 피드(기본 data/decks.json)")
    parser.add_argument("--detail-limit", type=int, default=-1,
                        help="상세 호출(유닛 아이템·아이템 착용자·완성 스테이지) 종류별 최대 개수. "
                             "원본이 호출량을 문제 삼으면 줄인다(기본 전부)")
    parser.add_argument("--cache", default=None,
                        help="개발용: 응답을 이 폴더에 저장하고 재사용한다(운영에서는 쓰지 않는다)")
    return parser.parse_args(argv)


def read_json(path):
    if not os.path.exists(path):
        return None
    try:
        with open(path, encoding="utf-8") as fp:
            return json.load(fp)
    except (OSError, ValueError):
        return None


def write_text(path, text):
    """임시 파일에 쓴 뒤 교체한다. 도중에 죽어도 반쯤 쓴 JSON이 남지 않게."""
    os.makedirs(os.path.dirname(path), exist_ok=True)
    temp = path + ".tmp"
    with open(temp, "w", encoding="utf-8", newline="\n") as fp:
        fp.write(text)
    os.replace(temp, path)


def status(failed, carried):
    if not failed:
        return "ok"
    return "stale" if carried else "missing"


def limited(values, limit):
    return values if limit is None or limit < 0 else values[:limit]


def main(argv=None):
    args = parse_args(sys.argv[1:] if argv is None else argv)
    started = time.time()
    now = datetime.now(timezone.utc).replace(microsecond=0)
    constants = load_constants()
    client = Client(cache_dir=args.cache)
    inputs = build.Inputs(constants, now)
    warn = inputs.warn
    stats_dir = os.path.join(args.out, "stats")
    failed = {key: False for key in SOURCE_KEYS}

    # --- 덱 피드(읽기만) · 직전 결과 ------------------------------------------
    inputs.decks = build.DecksLink.load(args.decks)
    deck_version = inputs.decks.version
    inputs.set_id = deck_version.get("set") or "s18"
    inputs.set_number = build.as_int(deck_version.get("setNumber")) or int(re.sub(r"\D", "", inputs.set_id) or 18)
    inputs.metatft_set = deck_version.get("metatftSet") or "TFTSet%d" % inputs.set_number
    failed["decksJson"] = not inputs.decks.ok
    if not inputs.decks.ok:
        warn("[경고] decks.json이 없거나 schemaVersion < 2 — 덱 연결 필드를 비운다")

    for name in build.FILE_NAMES:
        blob = read_json(os.path.join(stats_dir, name + ".json"))
        if blob:
            inputs.previous_blobs[name] = blob
    old_snapshot = read_json(os.path.join(stats_dir, "prev_snapshot.json")) or {}
    inputs.overrides = build.load_overrides(OVERRIDES)
    print("시즌: %s (세트 %d, metatft %s)%s" % (
        inputs.set_id, inputs.set_number, inputs.metatft_set,
        " · 수동 한글 이름 %d개" % len(inputs.overrides) if inputs.overrides else ""))

    # --- CommunityDragon(없으면 진행 불가) --------------------------------------
    try:
        ko = client.get_json(cdragon.KO_URL, timeout=180)
    except SourceError as exc:
        print("[치명] 한국어 사전을 가져오지 못했다. 이전 도감 파일을 그대로 둔다: %s" % exc, file=sys.stderr)
        return 1
    try:
        en = client.get_json(cdragon.EN_URL, timeout=180)
    except SourceError as exc:
        en = {}
        warn("[경고] 영문 사전 생략(nameEn만 빈다): %s" % exc)
    inputs.dic = cdragon.Dictionary(ko, en, inputs.set_number)
    del ko, en
    if not inputs.dic.playable_champions():
        print("[치명] CDragon 세트 %d 챔피언이 없다. 스키마가 바뀌었을 수 있다." % inputs.set_number, file=sys.stderr)
        return 1

    # --- metatft 패치·사전 ------------------------------------------------------
    try:
        inputs.patch = metatft.fetch_patch(client)
    except SourceError as exc:
        warn("[경고] metatft 패치 확인 실패(직전 결과의 패치로 본다): %s" % exc)
    try:
        inputs.lookup = metatft.fetch_lookup(client, inputs.metatft_set)
    except SourceError as exc:
        failed["metatftLookup"] = True
        warn("[경고] metatft 한국어 사전 생략(스킬 수치·희귀도가 빈다): %s" % exc)

    # --- metatft 스코프 표 -----------------------------------------------------
    for key, _label, _rank, _server in metatft.SCOPES:
        for kind, endpoint, field in (("units", "units", "unit"), ("traits", "traits", "trait"),
                                      ("items", "items_matches", "itemName")):
            try:
                table = metatft.fetch_table(client, endpoint, key, field)
            except SourceError as exc:
                failed["metatftStats"] = True
                warn("[경고] metatft %s %s 실패: %s" % (endpoint, key, exc))
                continue
            table["index"] = {row["id"]: row for row in table["rows"]}
            inputs.mt_tables.setdefault(key, {})[kind] = table

    # --- lol.qq ---------------------------------------------------------------
    inputs.window = lolqq.stat_window(now, constants["lolqq"]["days"])
    try:
        versions = lolqq.fetch_recent_versions(client)
        inputs.qq_build = versions[0].get("version_id")
    except SourceError as exc:
        inputs.qq_build = deck_version.get("qqBuild")
        warn("[경고] lol.qq 빌드 확인 실패(덱 피드 값 %s): %s" % (inputs.qq_build, exc))
    if inputs.qq_build:
        inputs.qq_patch = ".".join(str(inputs.qq_build).split(".")[:2])
    else:
        inputs.qq_patch = deck_version.get("patch")

    inputs.qq_static = lolqq.fetch_static(client, inputs.qq_patch, inputs.set_id, warn)
    failed["lolqqStatic"] = len(inputs.qq_static) < len(lolqq.STATIC_FILES)
    if not inputs.qq_patch and inputs.qq_static.get("chess"):
        inputs.qq_patch = inputs.qq_static["chess"].get("version")

    stime, etime = inputs.window
    for key, _label, tier in lolqq.SCOPES:
        try:
            inputs.qq_scopes[key] = lolqq.fetch_scope(client, tier, stime, etime)
        except SourceError as exc:
            failed["lolqqDatasearch"] = True
            warn("[경고] lol.qq 数据检索器 %s 실패: %s" % (key, exc))
    try:
        inputs.qq_equip = lolqq.fetch_equip(client, stime, etime)
    except SourceError as exc:
        failed["lolqqDatasearch"] = True
        warn("[경고] lol.qq 아이템 표 실패: %s" % exc)
    try:
        inputs.qq_trend = lolqq.fetch_trait_trend(client)
    except SourceError as exc:
        warn("[경고] lol.qq 특성 조합 추세 생략(combos는 직전 값): %s" % exc)

    # --- metatft 상세 ------------------------------------------------------------
    champions, detail_items, stage_items = build.detail_targets(inputs)
    for api in limited(champions, args.detail_limit):
        try:
            inputs.unit_items[api] = metatft.fetch_unit_items(client, api)
        except SourceError as exc:
            inputs.detail_failed["unit_items"].add(api)
            warn("[경고] %s" % exc)
    for api in limited(detail_items, args.detail_limit):
        try:
            inputs.item_details[api] = metatft.fetch_item_detail(client, api)
        except SourceError as exc:
            inputs.detail_failed["item_detail"].add(api)
            warn("[경고] %s" % exc)
    for api in limited(stage_items, args.detail_limit):
        try:
            inputs.item_stages[api] = metatft.fetch_item_stages(client, api)
        except SourceError as exc:
            inputs.detail_failed["item_stages"].add(api)
            warn("[경고] %s" % exc)
    failed["metatftDetail"] = any(inputs.detail_failed.values())

    # --- 증강 티어·가이드 ----------------------------------------------------------
    try:
        inputs.augment_tiers = metatft.fetch_augment_tiers(client)
    except SourceError as exc:
        failed["augmentsTiers"] = True
        warn("[경고] 증강 에디터 티어 실패: %s" % exc)
    try:
        inputs.comp_guides = metatft.fetch_comp_augment_tiers(client)
        inputs.clusters = metatft.fetch_clusters(client)
    except SourceError as exc:
        warn("[경고] 증강 가이드 추천 생략: %s" % exc)

    # --- 원격 플래그 -----------------------------------------------------------
    flags_path = os.path.join(args.out, "flags.json")
    flags = read_json(flags_path) or {}
    try:
        top = metatft.fetch_top_players(client)
        flags["liveSpectateAvailable"] = (build.as_int(top.get("length")) or len(top.get("data") or [])) > 0
    except SourceError as exc:
        # 확인하지 못하면 켜지 않는다. 앱은 이 값이 true일 때만 진행 중 로비를 조회한다.
        flags["liveSpectateAvailable"] = False
        warn("[경고] 관전 목록 확인 실패(false로 둔다): %s" % exc)
    flags["checkedAt"] = build.iso_now(now)

    # --- 조립 -------------------------------------------------------------------
    inputs.snapshot, frozen = build.next_snapshot(inputs, old_snapshot)
    payloads, names = build.build_all(inputs)

    sources = {
        "metatftStats": status(failed["metatftStats"], "metatftStats" in inputs.carried),
        "metatftDetail": status(failed["metatftDetail"], "metatftDetail" in inputs.carried),
        "lolqqDatasearch": status(failed["lolqqDatasearch"], "lolqqDatasearch" in inputs.carried),
        "namesKo": "ok",
        "metatftLookup": status(failed["metatftLookup"], False),
        "augmentsTiers": status(failed["augmentsTiers"], "augmentsTiers" in inputs.carried),
        "decksJson": "ok" if inputs.decks.ok else "missing",
        "lolqqStatic": status(failed["lolqqStatic"], False),
    }
    hashes = {name: build.finalize(payload) for name, payload in payloads.items()}
    version = build.stats_version(inputs, hashes, sources)

    # --- 쓰기(전부 만든 뒤 한꺼번에) -------------------------------------------------
    for name, payload in payloads.items():
        write_text(os.path.join(stats_dir, name + ".json"), build.dumps(payload))
    write_text(os.path.join(stats_dir, "version.json"),
               json.dumps(version, ensure_ascii=False, indent=2, sort_keys=True) + "\n")
    write_text(os.path.join(stats_dir, "prev_snapshot.json"), build.dumps(inputs.snapshot))
    write_text(flags_path, json.dumps(flags, ensure_ascii=False, indent=2, sort_keys=True) + "\n")
    if args.snapshot:
        os.makedirs(ASSETS_DIR, exist_ok=True)
        for name in SNAPSHOT_FILES:
            shutil.copyfile(os.path.join(stats_dir, name + ".json"), os.path.join(ASSETS_DIR, name + ".json"))

    report(inputs, payloads, names, version, sources, frozen, client, started, stats_dir, args)
    return 0


def report(inputs, payloads, names, version, sources, frozen, client, started, stats_dir, args):
    line = "-" * 64
    print(line)
    print("글로벌 패치 %s · 중국 빌드 %s · lol.qq 기간 %s ~ %s"
          % (version["patchGlobal"], version["qqBuild"], inputs.window[0], inputs.window[1]))
    for key, label, _rank, _server in metatft.SCOPES:
        parts = []
        for kind in ("units", "traits", "items"):
            table = inputs.table(key, kind)
            parts.append("%s %s" % (kind, "%d행/%s판" % (len(table["rows"]), format(table["games"], ","))
                                    if table else "실패"))
        print("metatft %-9s %s" % (key, " · ".join(parts)))
    for key, label, _tier in lolqq.SCOPES:
        info = (inputs.report.get("qq") or {}).get(key)
        if not info:
            print("lol.qq  %-9s 실패" % key)
            continue
        print("lol.qq  %-9s 챔피언 %d행(위반 %d) · 특성 %d행(위반 %d) · %s판 · meanAvg %s%s"
              % (key, info["heroes"], info["heroViolations"], info["traits"], info["traitViolations"],
                 format(info["games"] or 0, ","), info["mean"], " · 제외" if info.get("excluded") else ""))
    equip = inputs.report.get("equip")
    if equip:
        print("lol.qq  아이템    챔피언#아이템 %d쌍(build_s≥300) · 아이템 %d행(평균 null %d)"
              % (equip["pairs"], equip["items"], equip["itemAvgNull"]))
    print("상세    unit_detail_items %d(실패 %d) · item_detail %d(실패 %d) · item_stage_detail %d(실패 %d)"
          % (len(inputs.unit_items), len(inputs.detail_failed["unit_items"]),
             len(inputs.item_details), len(inputs.detail_failed["item_detail"]),
             len(inputs.item_stages), len(inputs.detail_failed["item_stages"])))

    champions = payloads["champions"]["champions"]
    traits = payloads["traits"]["traits"]
    items = payloads["items"]["items"]
    augments = payloads["augments"]["augments"]
    kinds = {}
    for item in items:
        kinds[item["kind"]] = kinds.get(item["kind"], 0) + 1
    print("행 수   챔피언 %d · 특성 %d · 아이템 %d(%s) · 증강 %d · 조합표 %d"
          % (len(champions), len(traits), len(items),
             ", ".join("%s %d" % (k, v) for k, v in sorted(kinds.items(), key=lambda kv: -kv[1])),
             len(augments), len(payloads["items"]["recipes"])))
    if names.untranslated:
        print("미번역  %d개: %s" % (len(names.untranslated), ", ".join(sorted(names.untranslated)[:8])))
    if frozen:
        print("이전 패치 %s 값을 동결했다(frozenAt %s)" % (inputs.snapshot.get("patchGlobal"), inputs.snapshot.get("frozenAt")))
    print("원본    %s" % " · ".join("%s=%s" % kv for kv in sources.items()))

    elapsed = time.time() - started
    hosts = ", ".join("%s %d" % kv for kv in sorted(client.calls.items(), key=lambda kv: -kv[1]))
    print("호출    %d회 (%s)%s" % (client.total_calls(), hosts,
                               " · 캐시 %d" % client.cache_hits if client.cache_hits else ""))
    print("소요    %d분 %02d초" % (elapsed // 60, elapsed % 60))
    sizes = []
    for name in SNAPSHOT_FILES:
        path = os.path.join(stats_dir, name + ".json")
        sizes.append("%s %.1f KB" % (name, os.path.getsize(path) / 1024.0))
    print("파일    %s%s" % (" · ".join(sizes), " (앱 스냅샷 복사)" if args.snapshot else ""))


if __name__ == "__main__":
    sys.exit(main())
