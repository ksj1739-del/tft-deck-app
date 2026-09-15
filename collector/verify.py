#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
decks.json(v2)이 앱에 내보내도 되는 상태인지 검사한다.

원본이 조용히 바뀌면 수집기는 성공하는데 내용만 비어 있을 수 있다(lol.qq·metatft 는
잘못된 요청에도 HTTP 200 을 준다). 그런 결과가 배포되지 않도록 CI에서 이 검사를
통과해야 커밋한다. 수집기가 남긴 진단(collector 블록)과 결과물 자체를 함께 본다.
"""

import io
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
DECKS = os.path.join(ROOT, "data", "decks.json")
VERSION = os.path.join(ROOT, "data", "version.json")

# 정상 범위. 벗어나면 원본이 바뀐 신호로 본다.
SCHEMA_VERSION = 2
MIN_DECKS = 10
MIN_TRANSLATION_RATE = 0.95
MIN_ITEM_KEYS = 20
TEAM_CODE_LENGTH = 32 + len("TFTSet") + 2   # 접두사+슬롯 32자 + "TFTSetNN"
MAX_FILE_BYTES = 2 * 1024 * 1024

# 소스별 최소 행수(설계 §4.6)
MIN_EDITORIAL = 20
MIN_GOLDEM_VARIANTS = 300
MIN_LINEUP_RANK = 50
MIN_CLUSTERS = 30
MIN_AUGMENT_SHARE = 0.5
MIN_CHAMPION_JOIN = 0.90

# 이 원본이 빠지면 목록 자체가 달라지므로 배포하지 않는다.
REQUIRED_SOURCES = ("lolqq", "lolqqWinrate", "lolqqStatic", "namesKo")

SCOPE_MEAN = 4.5
SCOPE_MEAN_TOLERANCE = 0.05
MAX_S_SHARE = 0.30
MIN_POSITION_AGREEMENT = 0.40
MIN_BUILDUP_SHARE = 0.80
MAX_BUILDUP_MISSING = 5
STAGE_KEYS = ("early", "mid", "final")
LEVEL_RANGE = (4, 11)
# 胜率阵容 평균 등수는 0.1 단위로 반올림돼 오므로 가능 범위 검사에 여유를 둔다.
WINRATE_TOLERANCE = 0.06
PRECISE_TOLERANCE = 0.01
# 胜率阵容 구간(앱 칩 순서)과 구간별 등급 기준(buckets[b].gradeCuts).
BUCKET_ORDER = ("all", "master", "diamond", "goldem", "low")
GRADE_CUT_KEYS = ("S", "A", "B", "C", "minSample", "shrinkK")
GRADE_METHODS = ("percentile", "absolute")
# 통계가 있는 그룹 중 등급이 붙은 비율이 이보다 낮으면 경고한다(앱에 표본 부족 카드가 많아진다).
MIN_GRADED_SHARE = 0.70
# 기본 구간이 아닌 구간은 등급 붙은 그룹이 이만큼은 돼야 S 비율 초과를 실패로 본다.
MIN_S_GATE_GROUPS = 10


def feasible(avg, win, top4, tolerance):
    """평균 등수가 1등·TOP4 비율로 가능한 범위 안인가(1등 외 TOP4 는 2~4등, 나머지는 5~8등)."""
    low = 1 * win + 2 * (top4 - win) + 5 * (1 - top4)
    high = 1 * win + 4 * (top4 - win) + 8 * (1 - top4)
    return low - tolerance <= avg <= high + tolerance


def expected_grade(stat, cuts):
    """실린 구간 기준으로 다시 매긴 등급. 표본 문턱 미만이면 None(deck_merge.finish_stats 와 같은 규칙)."""
    if (stat.get("n") or 0) < cuts["minSample"] or stat.get("adjAvg") is None:
        return None
    for grade in ("S", "A", "B", "C"):
        if stat["adjAvg"] <= cuts[grade]:
            return grade
    return "D"


def main():
    if not os.path.exists(DECKS):
        print("[실패] data/decks.json이 없다", file=sys.stderr)
        return 1

    size = os.path.getsize(DECKS)
    with io.open(DECKS, encoding="utf-8") as fp:
        data = json.load(fp)

    problems = []
    warnings = []

    version = data.get("version") or {}
    decks = data.get("decks") or []
    index = data.get("index") or {}
    catalog = data.get("catalog") or {}
    diag = data.get("collector") or {}
    buckets = data.get("buckets") or {}

    # --- 파일 · 스키마 ----------------------------------------------------
    if size > MAX_FILE_BYTES:
        problems.append("decks.json 이 %.0f KB (상한 %d KB)" % (size / 1024.0, MAX_FILE_BYTES // 1024))
    if version.get("schemaVersion") != SCHEMA_VERSION:
        problems.append("schemaVersion 이 %s (기대 %d)" % (version.get("schemaVersion"), SCHEMA_VERSION))
    if os.path.exists(VERSION):
        with io.open(VERSION, encoding="utf-8") as fp:
            if (json.load(fp) or {}).get("contentHash") != version.get("contentHash"):
                problems.append("version.json 과 decks.json 의 contentHash 가 다르다")
    else:
        problems.append("data/version.json이 없다")

    # --- 덱 --------------------------------------------------------------
    if len(decks) < MIN_DECKS:
        problems.append("덱이 %d개뿐이다 (최소 %d)" % (len(decks), MIN_DECKS))

    ids = [d.get("id") for d in decks]
    if len(set(ids)) != len(ids):
        problems.append("덱 id 가 중복된다")

    for deck in decks:
        label = deck.get("name") or deck.get("id")
        if not deck.get("units"):
            problems.append("덱 '%s'에 유닛이 없다" % label)
        if not deck.get("traits"):
            warnings.append("덱 '%s'에 시너지가 없다" % label)
        if not isinstance(deck.get("tier"), str):
            problems.append("덱 '%s'의 tier 가 문자열이 아니다(v1 앱 호환)" % label)

        code = (deck.get("teamCode") or {}).get("code")
        if code:
            if not code.startswith("02"):
                problems.append("덱 '%s' 코드 접두사가 02가 아니다: %s" % (label, code[:4]))
            if not code.endswith("TFTSet%s" % version.get("setNumber")):
                problems.append("덱 '%s' 코드 세트 접미사가 틀렸다: %s" % (label, code[-10:]))
            if len(code) != TEAM_CODE_LENGTH:
                problems.append("덱 '%s' 코드 길이가 %d (기대 %d)"
                                % (label, len(code), TEAM_CODE_LENGTH))

    only_china = sum(1 for d in decks if (d.get("metatft") or {}).get("onlyInChina"))
    if only_china != version.get("onlyInChinaCount"):
        problems.append("onlyInChinaCount %s 와 실제 중국 한정 덱 %d개가 다르다"
                        % (version.get("onlyInChinaCount"), only_china))

    # --- 원본 상태 · 빈 응답 ------------------------------------------------
    sources = version.get("sources") or {}
    for name in REQUIRED_SOURCES:
        if sources.get(name) != "ok":
            problems.append("필수 원본 %s 상태: %s" % (name, sources.get(name)))
    for name, state in sources.items():
        if name not in REQUIRED_SOURCES and state != "ok":
            warnings.append("원본 %s 상태: %s" % (name, state))
    for label in diag.get("empty") or []:
        problems.append("HTTP 200 빈 응답: %s" % label)

    # --- 소스별 최소 행수 ----------------------------------------------------
    editorial_count = version.get("editorialCount") or 0
    if editorial_count < MIN_EDITORIAL:
        problems.append("편집 덱이 %d개뿐이다 (최소 %d)" % (editorial_count, MIN_EDITORIAL))
    # 수집한 편집 덱은 전부 앱에서 닿아야 한다: 덱의 editorial 이거나 그룹의 moreEditorials.
    reachable = sum((1 if d.get("editorial") else 0) + len(d.get("moreEditorials") or []) for d in decks)
    if reachable != editorial_count:
        problems.append("편집 덱 %d개 중 앱이 닿는 것은 %d개(editorial + moreEditorials)" % (editorial_count, reachable))
    goldem_rows = ((diag.get("winrate") or {}).get("goldem") or {}).get("variants") or 0
    if goldem_rows < MIN_GOLDEM_VARIANTS:
        problems.append("胜率阵容 골드~에메랄드 조합이 %d개뿐이다 (최소 %d)" % (goldem_rows, MIN_GOLDEM_VARIANTS))
    if not (buckets.get("goldem") or {}).get("default"):
        problems.append("기본 구간 goldem 이 buckets 에 없다")
    rank_rows = diag.get("lineupRankRows") or {}
    for scope in ("cn_plat", "cn_master"):
        if (rank_rows.get(scope) or 0) < MIN_LINEUP_RANK:
            problems.append("数据检索器 %s 조합이 %s행뿐이다 (최소 %d)" % (scope, rank_rows.get(scope), MIN_LINEUP_RANK))
    if (diag.get("clusters") or 0) < MIN_CLUSTERS:
        problems.append("metatft 클러스터가 %s개뿐이다 (최소 %d)" % (diag.get("clusters"), MIN_CLUSTERS))
    detail = diag.get("detail") or {}
    exposed = detail.get("exposed") or 0
    if exposed:
        share = (detail.get("withAugments") or 0) / float(exposed)
        if share < MIN_AUGMENT_SHARE:
            problems.append("증강 통계가 있는 노출 덱이 %.0f%% (최소 %.0f%%)" % (share * 100, MIN_AUGMENT_SHARE * 100))
    else:
        problems.append("상세를 받은 노출 덱이 없다")

    # --- DA 조인율 ---------------------------------------------------------
    join = (diag.get("join") or {}).get("champions")
    if join is None or join < MIN_CHAMPION_JOIN:
        problems.append("胜率阵容 챔피언 DA 조인율 %s (최소 %.0f%%)" % (join, MIN_CHAMPION_JOIN * 100))

    champion_ids = {c["id"] for c in catalog.get("champions") or []}
    pet_ids = {p["id"] for p in catalog.get("pets") or []}
    unit_ids = champion_ids | pet_ids
    variant_units = [u["id"] for d in decks for v in d.get("variants") or [] for u in v.get("units") or []]
    if variant_units:
        known = sum(1 for uid in variant_units if uid in champion_ids)
        if known / float(len(variant_units)) < MIN_CHAMPION_JOIN:
            problems.append("변형 유닛 중 catalog 챔피언으로 풀린 비율 %.0f%%" % (100.0 * known / len(variant_units)))

    # --- metatft 비교 수치 ---------------------------------------------------
    for deck in decks:
        for scope, stat in ((deck.get("global") or {}).get("stats") or {}).items():
            places = stat.get("places") or []
            if len(places) != 8 or sum(places) != stat.get("n"):
                problems.append("덱 '%s' %s: sum(places) %s != n %s"
                                % (deck.get("id"), scope, sum(places), stat.get("n")))
    # 우리 목록에 대응 덱이 없는 상대는 metatft 이름으로만 알아볼 수 있다.
    nameless = [(deck.get("id"), counter.get("cluster")) for deck in decks
                for counter in ((deck.get("global") or {}).get("counters") or [])
                if not counter.get("deck") and not counter.get("name")]
    if nameless:
        problems.append("대응 덱도 이름도 없는 불리한 상대 %d건(앱에 클러스터 숫자만 보인다): %s"
                        % (len(nameless), nameless[:3]))
    for scope, mean in (diag.get("scopeMeanAvg") or {}).items():
        if mean is None or abs(mean - SCOPE_MEAN) > SCOPE_MEAN_TOLERANCE:
            problems.append("metatft %s 클러스터 가중 평균 등수 %s (4.5±0.05 밖)" % (scope, mean))
    # comps_stats 는 permit_filter_adjustment=false 라 보정이 없고, 보정 여부는 comp_details 에만 온다.
    # comp_details 는 고정 모집단(스코프 라벨 없음)이라 경고로만 알린다.
    comp = diag.get("compDetails") or {}
    if comp.get("overrideApplied"):
        warnings.append("metatft comp_details %d개에서 랭크 필터 보정(override_applied)이 켜졌다" % comp["overrideApplied"])
    if comp.get("clusters") and (comp.get("fetched") or 0) < comp["clusters"]:
        warnings.append("metatft comp_details 를 %s/%s 클러스터만 받았다" % (comp.get("fetched"), comp["clusters"]))

    # --- lol.qq 수치 가능 범위 -------------------------------------------------
    impossible = 0
    for deck in decks:
        blocks = [(s, WINRATE_TOLERANCE) for s in (deck.get("stats") or {}).values()]
        blocks += [(s["precise"], PRECISE_TOLERANCE) for s in (deck.get("stats") or {}).values() if s.get("precise")]
        for variant in deck.get("variants") or []:
            blocks += [(s, WINRATE_TOLERANCE) for s in (variant.get("stats") or {}).values()]
            blocks += [(s, PRECISE_TOLERANCE) for s in (variant.get("precise") or {}).values()]
        for stat, tolerance in blocks:
            if None in (stat.get("avg"), stat.get("win"), stat.get("top4")):
                continue
            if not feasible(stat["avg"], stat["win"], stat["top4"], tolerance):
                impossible += 1
    if impossible:
        problems.append("평균 등수가 1등·TOP4 비율로 불가능한 lol.qq 수치 %d건" % impossible)
    # 변형 행은 그룹과 같은 고정 4수치 줄을 쓴다. 픽률이 없으면 그 칸만 늘 비어 보인다.
    no_pick = sum(1 for d in decks for v in d.get("variants") or []
                  for s in (v.get("stats") or {}).values() if s.get("pick") is None)
    if no_pick:
        problems.append("픽률이 없는 변형 수치 %d건" % no_pick)

    # --- 등급 분포 ---------------------------------------------------------
    # 등급 기준은 구간마다 그 구간 분포로 잡아 buckets[b].gradeCuts 에 싣는다. 실린 기준으로 등급을 다시 매겨
    # 맞춰 보고, 구간마다 S 비율과 '통계가 있는 그룹 중 등급이 붙은 비율'을 본다.
    group_decks = [d for d in decks if d.get("kind") == "group"]
    if not any(((d.get("stats") or {}).get("goldem") or {}).get("grade") for d in group_decks):
        problems.append("골드~에메랄드 등급이 하나도 없다")
    for key in BUCKET_ORDER:
        if key not in buckets:
            continue
        meta = buckets[key]
        cuts = meta.get("gradeCuts") or {}
        if cuts.get("method") not in GRADE_METHODS or any(
                not isinstance(cuts.get(k), (int, float)) for k in GRADE_CUT_KEYS):
            problems.append("구간 %s 등급 기준(gradeCuts)이 없거나 깨졌다: %s" % (key, cuts))
            continue
        stats = [s for s in ((d.get("stats") or {}).get(key) for d in group_decks) if s]
        grades = [s.get("grade") for s in stats]
        with_grade = [g for g in grades if g]
        mismatched = sum(1 for s in stats if s.get("grade") != expected_grade(s, cuts))
        if mismatched:
            problems.append("구간 %s 등급 %d건이 실린 기준(gradeCuts)으로 다시 매긴 등급과 다르다" % (key, mismatched))
        if with_grade:
            s_share = with_grade.count("S") / float(len(with_grade))
            if s_share > MAX_S_SHARE:
                text = ("구간 %s S 등급 비율 %.0f%% (최대 %.0f%%, 등급 %d개)"
                        % (key, s_share * 100, MAX_S_SHARE * 100, len(with_grade)))
                # 등급이 붙은 그룹이 적은 구간은 adjAvg 동률 하나로도 30% 를 넘어 경고로만 둔다. 기본 구간은 늘 막는다.
                if key == "goldem" or len(with_grade) >= MIN_S_GATE_GROUPS:
                    problems.append(text)
                else:
                    warnings.append(text)
        coverage = len(with_grade) / float(len(grades)) if grades else None
        print("등급 분포(%s): %s · 등급 %d/%d%s · %s S≤%s A≤%s B≤%s C≤%s · 표본≥%s · K %s"
              % (meta.get("label") or key, {g: with_grade.count(g) for g in "SABCD"}, len(with_grade), len(grades),
                 " (%.0f%%)" % (coverage * 100) if coverage is not None else "",
                 cuts["method"], cuts["S"], cuts["A"], cuts["B"], cuts["C"], cuts["minSample"], cuts["shrinkK"]))
        if coverage is not None and coverage < MIN_GRADED_SHARE:
            warnings.append("구간 %s 통계가 있는 그룹 중 등급이 붙은 비율 %.0f%% (%d/%d, 기준 %.0f%%)"
                            % (key, coverage * 100, len(with_grade), len(grades), MIN_GRADED_SHARE * 100))

    # --- 편집 덱 단계 보드 ----------------------------------------------------
    pet_cells = 0
    used_pets = set()
    for deck in decks:
        editorials = ([deck["editorial"]] if deck.get("editorial") else []) + list(deck.get("moreEditorials") or [])
        for index_no, editorial in enumerate(editorials):
            label = "%s/%s" % (deck.get("id"), editorial.get("id"))
            stages = editorial.get("stages") or []
            if [s.get("key") for s in stages] != list(STAGE_KEYS):
                problems.append("편집 덱 %s 단계가 early/mid/final 이 아니다: %s" % (label, [s.get("key") for s in stages]))
                continue
            for stage in stages:
                level = stage.get("level")
                if not isinstance(level, int) or not LEVEL_RANGE[0] <= level <= LEVEL_RANGE[1]:
                    problems.append("편집 덱 %s %s 레벨이 %s" % (label, stage["key"], level))
                if not stage.get("units"):
                    problems.append("편집 덱 %s %s 보드가 비었다" % (label, stage["key"]))
                for unit in stage.get("units") or []:
                    row, col = unit.get("row"), unit.get("col")
                    if row is not None and not 1 <= row <= 4 or col is not None and not 1 <= col <= 7:
                        problems.append("편집 덱 %s %s 좌표가 판 밖이다: %s,%s" % (label, stage["key"], row, col))
                    if unit.get("id") not in unit_ids:
                        problems.append("편집 덱 %s %s 유닛 %s 가 catalog 에 없다" % (label, stage["key"], unit.get("id")))
                    if unit.get("kind") == "pet":
                        pet_cells += 1
                        used_pets.add(unit.get("id"))
                        if unit.get("id") not in pet_ids:
                            problems.append("pet %s 가 catalog.pets 에 없다" % unit.get("id"))
            # 카드 보드가 이 편집 덱이면 최종 단계와 칸 수가 같아야 한다(pet 칸 포함).
            final = stages[-1]
            if index_no == 0 and len(final.get("units") or []) != len(deck.get("units") or []):
                problems.append("편집 덱 %s 최종 보드 %d칸과 카드 보드 %d칸이 다르다"
                                % (label, len(final.get("units") or []), len(deck.get("units") or [])))
    if catalog.get("pets") and not pet_cells:
        warnings.append("단계 보드에 pet 칸이 하나도 없다(pet 누락 버그 재발 가능성)")

    # pet 아이콘. 보드에 쓰인 pet 이 아이콘 없이 나가면 앱은 무엇인지 알 수 없는 회색 칸을 그린다.
    for deck in decks:
        for unit in deck.get("units") or []:
            if unit.get("kind") == "pet":
                used_pets.add(unit.get("id"))
    pet_icons = {p.get("id"): p.get("icon") for p in catalog.get("pets") or []}
    blank_used = sorted(pid for pid in used_pets if pid and not pet_icons.get(pid))
    if blank_used:
        problems.append("보드에 쓰인 pet 의 아이콘이 없다(회색 칸으로 보인다): %s" % ", ".join(blank_used))
    blank_other = sorted(pid for pid, icon in pet_icons.items() if pid and not icon and pid not in used_pets)
    if blank_other:
        warnings.append("catalog.pets 아이콘 없음(빌드업 칸 등): %s" % ", ".join(blank_other))

    # --- 실측 배치 방향 ---------------------------------------------------------
    # 수집기의 방향 판단을 믿지 않고, 실린 positions 를 편집 덱 최종 좌표와 다시 맞춰 본다.
    matches = compared = 0
    for deck in decks:
        cells = deck.get("positions") or {}
        final = (((deck.get("editorial") or {}).get("stages") or [{}])[-1]).get("units") or []
        if not cells or not final:
            continue
        for unit in final:
            top = (cells.get(unit.get("id")) or [None])[0]
            if top is None or unit.get("row") is None or unit.get("col") is None:
                continue
            compared += 1
            if (top.get("row"), top.get("col")) == (unit["row"], unit["col"]):
                matches += 1
    has_positions = any(d.get("positions") for d in decks)
    if has_positions:
        if not compared or matches / float(compared) < MIN_POSITION_AGREEMENT:
            problems.append("positions 가 실렸는데 편집 좌표와 최빈 칸 일치율이 %d/%d" % (matches, compared))
        else:
            print("실측 배치 방향: 편집 좌표와 최빈 칸 일치 %d/%d (해석 %s)"
                  % (matches, compared, (diag.get("positions") or {}).get("mapping")))
    elif detail.get("position"):
        warnings.append("실측 배치 방향 검사를 통과하지 못해 positions 를 싣지 않았다 (%s)" % diag.get("positions"))

    # --- 빌드업 -------------------------------------------------------------
    matched = [d for d in decks if d.get("global")]
    if matched:
        with_levels = sum(1 for d in matched if ((d.get("buildup") or {}).get("global") or {}).get("levels"))
        if with_levels / float(len(matched)) < MIN_BUILDUP_SHARE:
            problems.append("metatft 매칭 덱 중 빌드업이 있는 덱 %d/%d (최소 %.0f%%)"
                            % (with_levels, len(matched), MIN_BUILDUP_SHARE * 100))
    buildup_ids = set()
    for deck in decks:
        buildup = deck.get("buildup") or {}
        for source in ("global", "cn"):
            for level in (buildup.get(source) or {}).get("levels") or []:
                for option in level.get("options") or []:
                    buildup_ids.update(option.get("units") or [])
                    if option.get("carryId"):
                        buildup_ids.add(option["carryId"])
    missing_buildup = sorted(buildup_ids - unit_ids)
    if len(missing_buildup) > MAX_BUILDUP_MISSING:
        problems.append("빌드업 유닛 %d개가 catalog 에 없다: %s" % (len(missing_buildup), ", ".join(missing_buildup[:8])))
    elif missing_buildup:
        warnings.append("빌드업 유닛 %d개가 catalog 에 없다: %s" % (len(missing_buildup), ", ".join(missing_buildup)))
    unlisted = [uid for uid in missing_buildup if uid not in (version.get("untranslatedIds") or [])]
    if unlisted:
        warnings.append("catalog 에 없는데 untranslatedIds 에도 없는 빌드업 유닛: %s" % ", ".join(unlisted))

    # --- 번역률 ----------------------------------------------------------
    champions = catalog.get("champions") or []
    if champions:
        korean = sum(1 for c in champions if c["name"] != c["id"])
        rate = korean / float(len(champions))
        if rate < MIN_TRANSLATION_RATE:
            problems.append("챔피언 한글화율 %.0f%% (최소 %.0f%%)"
                            % (rate * 100, MIN_TRANSLATION_RATE * 100))
    else:
        problems.append("catalog.champions가 비었다")

    # --- 검색 인덱스 ------------------------------------------------------
    # 아이템 역검색이 이 앱의 핵심 기능이라 비면 배포하지 않는다.
    if len(index.get("item") or {}) < MIN_ITEM_KEYS:
        problems.append("아이템 인덱스가 %d개뿐이다 (최소 %d)"
                        % (len(index.get("item") or {}), MIN_ITEM_KEYS))
    for axis in ("champion", "trait", "component", "augment"):
        if not index.get(axis):
            problems.append("%s 인덱스가 비었다" % axis)

    # 인덱스가 가리키는 덱이 실제로 있는지
    known = set(ids)
    dangling = set()
    for entry in (index.get("item") or {}).values():
        dangling.update(hit["deck"] for hit in entry if hit["deck"] not in known)
    for axis in ("component", "champion", "trait", "augment"):
        for entry in (index.get(axis) or {}).values():
            dangling.update(d for d in entry if d not in known)
    by_id = index.get("byId") or {}
    if not by_id.get("champion") or not by_id.get("item"):
        problems.append("byId 인덱스가 비었다")
    for axis, rows in by_id.items():
        for entry in rows.values():
            dangling.update(d for d in entry if d not in known)
    if dangling:
        problems.append("인덱스가 없는 덱을 가리킨다: %s" % ", ".join(sorted(dangling)[:5]))

    if version.get("untranslatedIds"):
        warnings.append("미번역 ID %d개: %s"
                        % (len(version["untranslatedIds"]),
                           ", ".join(version["untranslatedIds"][:8])))

    # --- 결과 -------------------------------------------------------------
    for text in warnings:
        print("[경고] %s" % text)
    for text in problems:
        print("[실패] %s" % text, file=sys.stderr)

    if problems:
        print("\n검증 실패: %d건" % len(problems), file=sys.stderr)
        return 1

    print("검증 통과 — 덱 %d개(그룹 %d · 편집 독립 %d), 패치 %s, 아이템 인덱스 %d개, 덱 코드 %d개, %.0f KB"
          % (len(decks), sum(1 for d in decks if d.get("kind") == "group"),
             sum(1 for d in decks if d.get("kind") == "editorial"), version.get("patch"),
             len(index.get("item") or {}), version.get("teamCodeCount", 0), size / 1024.0))
    return 0


if __name__ == "__main__":
    sys.exit(main())
