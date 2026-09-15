#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
data/stats/*.json이 앱에 내보내도 되는 상태인지 검사한다.

원본(metatft·lol.qq)은 잘못된 요청에도 HTTP 200 빈 응답을 준다. 수집기가 성공해도 내용이
비거나 모순일 수 있으므로 CI에서 이 검사를 통과해야 커밋한다(실패 시 워크플로가 data/stats를 되돌린다).

  python collector/verify_stats.py            # data/stats 검사
  python collector/verify_stats.py --dir X    # 다른 폴더(개발용)
"""

import argparse
import json
import os
import sys

# Windows 콘솔(cp949)에서 한글/기호 출력 시 죽지 않도록.
for _stream in (sys.stdout, sys.stderr):
    try:
        _stream.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, ValueError):
        pass

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
STATS_DIR = os.path.join(ROOT, "data", "stats")

FILES = ("version", "champions", "traits", "items", "augments")
MIN_ROWS = {
    "champions": 60,
    # 설계 초안은 30이었으나 세트 18 특성 36개 중 고유 특성 10개를 계약대로 빼면 26개다.
    # 빈 표·조인 붕괴를 잡는 게 목적이므로 실제 규모에 맞춰 20으로 둔다.
    "traits": 20,
    "items": 120,
    "augments": 200,
}
UNITS_PER_BOARD = (8.0, 8.8)      # 보드당 유닛 수(Σn/games). 벗어나면 행이 빠졌거나 중복됐다.
MIN_UNIT_VOLUME_JOIN = 0.95       # 챔피언 행 Σn / 원본 DA 행 Σn
MIN_CDRAGON_CHAMPIONS = 0.90
MIN_TRANSLATION_RATE = 0.95
MAX_BYTES = int(1.5 * 1024 * 1024)
METATFT_SCOPES = ("glob_plat", "kr_plat", "kr_master")
LOLQQ_SCOPES = ("cn_plat", "cn_master")
RANGE_TOLERANCE = 0.001
GRADES = {"S", "A", "B", "C", "D", None}


def range_ok(n, top1, top4, avg):
    if not n or top1 is None or top4 is None or avg is None:
        return False
    p1, p4 = top1 / float(n), top4 / float(n)
    low = p1 + 2 * (p4 - p1) + 5 * (1 - p4)
    high = p1 + 4 * (p4 - p1) + 8 * (1 - p4)
    # 저장된 avg는 소수 둘째 자리 반올림이라 0.005만큼 더 봐준다.
    return low - RANGE_TOLERANCE - 0.005 <= avg <= high + RANGE_TOLERANCE + 0.005


def check_metatft_stat(stat, where, problems):
    places = stat.get("places")
    if not isinstance(places, list) or len(places) != 8:
        problems.append("%s: places가 8칸이 아니다" % where)
        return
    if sum(places) != stat.get("n"):
        problems.append("%s: sum(places) %d != n %s" % (where, sum(places), stat.get("n")))
    if stat.get("grade") not in GRADES:
        problems.append("%s: 알 수 없는 등급 %r" % (where, stat.get("grade")))


def check_lolqq_stat(stat, where, violations):
    if not range_ok(stat.get("n"), stat.get("top1"), stat.get("top4"), stat.get("avg")):
        violations.append(where)


def main(argv=None):
    parser = argparse.ArgumentParser(description="도감 통계 검증")
    parser.add_argument("--dir", default=STATS_DIR)
    args = parser.parse_args(sys.argv[1:] if argv is None else argv)

    problems, warnings = [], []
    data = {}

    # --- 파일 존재·파싱·크기 ----------------------------------------------------
    for name in FILES:
        path = os.path.join(args.dir, name + ".json")
        if not os.path.exists(path):
            problems.append("%s.json이 없다" % name)
            continue
        size = os.path.getsize(path)
        if size > MAX_BYTES:
            problems.append("%s.json %.2f MB (최대 1.5 MB)" % (name, size / 1048576.0))
        try:
            with open(path, encoding="utf-8") as fp:
                data[name] = json.load(fp)
        except ValueError as exc:
            problems.append("%s.json 파싱 실패: %s" % (name, exc))

    if len(data) < len(FILES):
        return finish(problems, warnings, data)

    version = data["version"]
    champions = data["champions"].get("champions") or []
    traits = data["traits"].get("traits") or []
    items = data["items"].get("items") or []
    augments = data["augments"].get("augments") or []
    rows = {"champions": champions, "traits": traits, "items": items, "augments": augments}

    # --- 행 수 ------------------------------------------------------------------
    for name, minimum in MIN_ROWS.items():
        if len(rows[name]) < minimum:
            problems.append("%s가 %d개뿐이다 (최소 %d)" % (name, len(rows[name]), minimum))

    # --- 버전·해시 ----------------------------------------------------------------
    for name in rows:
        file_hash = (data[name].get("version") or {}).get("contentHash")
        if not file_hash or (version.get("files") or {}).get(name) != file_hash:
            problems.append("version.json files.%s 해시가 %s.json과 다르다" % (name, name))
    for name, state in (version.get("sources") or {}).items():
        if state != "ok":
            warnings.append("원본 %s 상태: %s" % (name, state))

    # --- metatft: sum(places) == n, 보드당 유닛 수, 조인 손실 ------------------------------
    scopes = data["champions"].get("scopes") or {}
    for scope in METATFT_SCOPES:
        meta = scopes.get(scope)
        stats = [c["stats"][scope] for c in champions if scope in (c.get("stats") or {})]
        if not meta or not stats:
            warnings.append("챔피언 %s 스코프가 비었다" % scope)
            continue
        for champ in champions:
            if scope in (champ.get("stats") or {}):
                check_metatft_stat(champ["stats"][scope], "챔피언 %s %s" % (champ["id"], scope), problems)
        total = sum(s["n"] for s in stats)
        per_board = total / float(meta["games"])
        if not UNITS_PER_BOARD[0] <= per_board <= UNITS_PER_BOARD[1]:
            problems.append("챔피언 %s Σn/games %.2f (정상 %.1f~%.1f)" % (scope, per_board, UNITS_PER_BOARD[0], UNITS_PER_BOARD[1]))
        if meta.get("unitsPerBoard"):
            joined = per_board / float(meta["unitsPerBoard"])
            if joined < MIN_UNIT_VOLUME_JOIN:
                problems.append("챔피언 %s DA 조인 손실: 원본 표본의 %.0f%%만 붙었다" % (scope, joined * 100))

    for trait in traits:
        for scope in METATFT_SCOPES:
            for units, stat in ((trait.get("stats") or {}).get(scope) or {}).get("byUnits", {}).items():
                check_metatft_stat(stat, "특성 %s %s %s" % (trait["id"], scope, units), problems)
    for item in items:
        for scope in METATFT_SCOPES:
            if scope in (item.get("stats") or {}):
                check_metatft_stat(item["stats"][scope], "아이템 %s %s" % (item["id"], scope), problems)

    # --- lol.qq: 범위 검사 위반 0 ---------------------------------------------------------
    violations = []
    for champ in champions:
        for scope in LOLQQ_SCOPES:
            if scope in (champ.get("stats") or {}):
                check_lolqq_stat(champ["stats"][scope], "챔피언 %s %s" % (champ["id"], scope), violations)
    for trait in traits:
        for scope in LOLQQ_SCOPES:
            for units, stat in ((trait.get("stats") or {}).get(scope) or {}).get("byUnits", {}).items():
                check_lolqq_stat(stat, "특성 %s %s %s" % (trait["id"], scope, units), violations)
    if violations:
        problems.append("lol.qq 범위 검사 위반 %d건: %s" % (len(violations), ", ".join(violations[:5])))

    # --- CDragon 조인·한글화 --------------------------------------------------------------
    if champions:
        joined = sum(1 for c in champions if c.get("icon") and c.get("name") != c.get("id"))
        if joined / float(len(champions)) < MIN_CDRAGON_CHAMPIONS:
            problems.append("CDragon 이름·아이콘이 붙은 챔피언 %.0f%% (최소 %.0f%%)"
                            % (joined * 100.0 / len(champions), MIN_CDRAGON_CHAMPIONS * 100))
    everything = champions + traits + items + augments
    translated = sum(1 for row in everything if row.get("name") and row.get("name") != row.get("id"))
    rate = translated / float(len(everything)) if everything else 0.0
    if rate < MIN_TRANSLATION_RATE:
        problems.append("한글화율 %.1f%% (최소 %.0f%%)" % (rate * 100, MIN_TRANSLATION_RATE * 100))
    untranslated = [row["id"] for row in everything if row.get("name") == row.get("id")]
    if untranslated:
        warnings.append("미번역 %d개: %s" % (len(untranslated), ", ".join(untranslated[:6])))

    # --- 등급 분포 ------------------------------------------------------------------------
    def grades(stat_rows):
        return {s.get("grade") for s in stat_rows if s.get("grade")}

    champ_grades = grades(c["stats"][s] for c in champions for s in METATFT_SCOPES if s in (c.get("stats") or {}))
    trait_grades = grades(stat for t in traits for s in METATFT_SCOPES
                          for stat in ((t.get("stats") or {}).get(s) or {}).get("byUnits", {}).values())
    item_grades = grades(i["stats"][s] for i in items for s in METATFT_SCOPES if s in (i.get("stats") or {}))
    for label, found in (("챔피언", champ_grades), ("특성", trait_grades), ("아이템", item_grades)):
        if not found:
            problems.append("%s 등급 분포가 비었다" % label)

    # --- 구조 참조 ------------------------------------------------------------------------
    item_ids = {i["id"] for i in items}
    for key, target in (data["items"].get("recipes") or {}).items():
        if target not in item_ids:
            problems.append("조합표 %s가 없는 아이템 %s를 가리킨다" % (key, target))
    for comp in data["items"].get("components") or []:
        if comp not in item_ids:
            problems.append("부품 %s가 아이템 목록에 없다" % comp)
    if not (data["augments"].get("meta") or {}).get("editorTier"):
        warnings.append("증강 에디터 티어가 비었다")

    summary = ("챔피언 %d · 특성 %d · 아이템 %d · 증강 %d · 한글화 %.1f%% · 등급 %s/%s/%s · 패치 %s"
               % (len(champions), len(traits), len(items), len(augments), rate * 100,
                  "".join(sorted(champ_grades)), "".join(sorted(trait_grades)), "".join(sorted(item_grades)),
                  version.get("patchGlobal")))
    return finish(problems, warnings, data, summary)


def finish(problems, warnings, data, summary=None):
    for text in warnings:
        print("[경고] %s" % text)
    for text in problems:
        print("[실패] %s" % text, file=sys.stderr)
    if problems:
        print("\n검증 실패: %d건" % len(problems), file=sys.stderr)
        return 1
    print("검증 통과 — %s" % summary)
    return 0


if __name__ == "__main__":
    sys.exit(main())
