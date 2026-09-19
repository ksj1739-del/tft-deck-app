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
import re
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

# 이 원본이 빠지면 목록 자체가 달라지므로 배포하지 않는다. 2026-09-19 부터 목록의 주는 metatft 조합 덱이라
# metatft 클러스터·구간 통계도 필수다(빠지면 조합 덱 없이 lol.qq 덱만 남는다).
REQUIRED_SOURCES = ("lolqq", "lolqqWinrate", "lolqqStatic", "namesKo", "metatft", "metatftBuckets")

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
GRADE_CUT_KEYS = ("S", "A", "B", "C", "minSample", "shrinkK", "shrinkTo")
# adjAvg 를 실린 shrinkK·shrinkTo 로 다시 계산했을 때 허용 차이(avg 가 소수 둘째 자리로 반올림돼 실린다).
ADJ_AVG_TOLERANCE = 0.02
# 둘 다 등급이 있는데 원평균이 이만큼 이상 좋은 그룹의 등급이 더 낮으면 역전으로 센다.
INVERSION_AVG_GAP = 0.3
GRADE_METHODS = ("percentile", "absolute")
# 통계가 있는 그룹 중 등급이 붙은 비율이 이보다 낮으면 경고한다(앱에 표본 부족 카드가 많아진다).
MIN_GRADED_SHARE = 0.70
# 기본 구간이 아닌 구간은 등급 붙은 그룹이 이만큼은 돼야 S 비율 초과를 실패로 본다.
MIN_S_GATE_GROUPS = 10

# metatft 조합 덱(kind=meta) 등급: 사이트 고정 컷('<'). 실린 avg(소수 넷째 자리)로 다시 매길 때 컷과의 차이가
# 이보다 작으면 반올림 몫으로 보고 봐준다.
META_GRADE_CUTS = (("S", 4.25), ("A", 4.5), ("B", 4.75), ("C", 5.0))
META_CUT_TOLERANCE = 0.0001
META_LOBBY_SIZE = 8
META_ID = re.compile(r"^m-[0-9a-f]{10}(-\d+)?$")
# 중국 한정 덱 등급(buckets[b].chinaGrade): 구간마다 대상(n ≥ lol.qq minSample)을 adjAvg 순으로 세워 앞 절반 S · 나머지 A.
CHINA_GRADE_METHOD = "halfSA"
# 편집 덱 등급 순서(deck_merge.EDITORIAL_TIER_ORDER). 통계 등급이 없는 중국 한정 덱의 tierOrder 다.
EDITORIAL_TIER_ORDER = {"SS": 0, "S": 1, "A": 2, "B": 3, "C": 4, "D": 5}
# 다른 플레이어를 알아볼 수 있는 식별자. 출력 어디에도 이 키가 있으면 안 된다(공개 저장소).
PLAYER_KEYS = {"puuid", "riotid", "riot_id", "gamename", "game_name", "tagline", "tag_line", "summonername",
               "summoner_name", "summonerid", "summoner_id", "procomps", "pro_comps"}


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


def meta_grade_ok(stat, cuts):
    """
    조합 덱 구간 등급을 실린 기준(metaGradeCuts)으로 다시 매겨 맞는지. 표본 문턱·사이트 최소 픽률(로비당 인원) 미만이면
    등급이 없어야 하고, 아니면 avg 에 '<' 컷. 실린 avg 가 컷과 META_CUT_TOLERANCE 안이면 이웃한 두 등급 모두 받는다.
    반환 (맞음, 기대 등급).
    """
    n, avg, boards = stat.get("n") or 0, stat.get("avg"), cuts.get("boards") or 0
    if avg is None or n < cuts["minSample"] or not boards or n * META_LOBBY_SIZE / float(boards) < cuts["minPlayrate"]:
        return stat.get("grade") is None, None
    expected = next((g for g, _ in META_GRADE_CUTS if avg < cuts[g]), "D")
    if stat.get("grade") == expected:
        return True, expected
    near = [g for g, _ in META_GRADE_CUTS if abs(avg - cuts[g]) < META_CUT_TOLERANCE]
    if near:
        order = "SABCD"
        allowed = {near[0], order[order.index(near[0]) + 1]}
        return stat.get("grade") in allowed, expected
    return False, expected


def player_keys(node, path=""):
    """출력 JSON 에서 다른 플레이어 식별자 키(PLAYER_KEYS)가 있는 경로들."""
    found = []
    if isinstance(node, dict):
        for key, value in node.items():
            child = "%s.%s" % (path, key) if path else str(key)
            if str(key).lower() in PLAYER_KEYS:
                found.append(child)
            found.extend(player_keys(value, child))
    elif isinstance(node, list):
        for value in node:
            found.extend(player_keys(value, path + "[]"))
    return found


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
    # 목록에서 글만 읽고 덱을 고르므로 긴 이름·별칭이 겹치거나 비면 안 된다.
    for field in ("name", "alias"):
        values = [d.get(field) for d in decks]
        empty = [d.get("id") for d in decks if not str(d.get(field) or "").strip()]
        if empty:
            problems.append("%s 가 빈 덱 %d개: %s" % (field, len(empty), empty[:5]))
        dup = sorted({v for v in values if v and values.count(v) > 1})
        if dup:
            problems.append("%s 가 겹치는 덱: %s" % (field, dup[:5]))
    no_summary = [d.get("id") for d in decks if not str(d.get("summary") or "").strip()]
    if no_summary:
        problems.append("summary 가 빈 덱 %d개: %s" % (len(no_summary), no_summary[:5]))

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

    # 앱과 같은 판정(sources.onlyInChina 또는 metatft.onlyInChina)으로 센다.
    only_china = sum(1 for d in decks
                     if (d.get("sources") or {}).get("onlyInChina") or (d.get("metatft") or {}).get("onlyInChina"))
    if only_china != version.get("onlyInChinaCount"):
        problems.append("onlyInChinaCount %s 와 실제 중국 한정 덱 %d개가 다르다"
                        % (version.get("onlyInChinaCount"), only_china))

    # 출력에 다른 플레이어 식별자(puuid·riotId·gameName·tagLine 등)나 proComps 가 있으면 안 된다(공개 저장소).
    leaked = player_keys(data)
    if leaked:
        problems.append("다른 플레이어 식별자 키가 출력에 있다: %s" % sorted(set(leaked))[:5])

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
    # editorialCount 는 앱이 닿는 편집 덱 수(덱의 editorial + moreEditorials)다. 수집한 편집 덱은 전부 닿거나,
    # 조합 덱에 합쳐진 대표 아닌 그룹의 것이라 잇지 않은 것(collector.editorialUnlinked)이어야 한다.
    reachable = sum((1 if d.get("editorial") else 0) + len(d.get("moreEditorials") or []) for d in decks)
    if reachable != editorial_count:
        problems.append("editorialCount %d 와 앱이 닿는 편집 덱 %d개(editorial + moreEditorials)가 다르다"
                        % (editorial_count, reachable))
    unlinked = diag.get("editorialUnlinked") or []
    parsed = diag.get("editorialParsed")
    if parsed is not None and reachable + len(unlinked) != parsed:
        problems.append("수집한 편집 덱 %s개 = 앱 연결 %d + 잇지 않음 %d 가 맞지 않는다" % (parsed, reachable, len(unlinked)))
    if unlinked:
        warnings.append("조합 덱의 대표가 아닌 그룹에 붙어 잇지 않은 편집 덱 %d개: %s" % (len(unlinked), ", ".join(unlinked)))
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

    # --- metatft 조합 덱(kind=meta) -------------------------------------------------
    # 목록의 주. 구간별 수치·등급은 metatft comps_stats 이고, 등급은 사이트 고정 컷(반올림 전 평균 등수 '<')이다.
    # 실린 기준(buckets[b].metaGradeCuts)으로 다시 매겨 맞춰 보고, 분포는 출력만 한다(고정 컷이라 구간마다 모양이 다르다).
    meta_decks = [d for d in decks if d.get("kind") == "meta"]
    china_decks = [d for d in decks if d.get("kind") != "meta"]
    if any(d.get("kind") == "global" for d in decks) or (version.get("globalOnlyCount") or 0) != 0:
        problems.append("예전 metatft 전용 덱(kind=global)이 남았거나 globalOnlyCount 가 0 이 아니다")
    if data.get("globalGradeCuts") is not None:
        problems.append("globalGradeCuts 는 더 싣지 않는다")
    if not meta_decks:
        problems.append("metatft 조합 덱(kind=meta)이 하나도 없다")
    if version.get("metaCompCount") != len(meta_decks):
        problems.append("metaCompCount %s 와 실제 조합 덱 %d개가 다르다" % (version.get("metaCompCount"), len(meta_decks)))

    meta_cuts = {}
    for key in BUCKET_ORDER:
        cuts = (buckets.get(key) or {}).get("metaGradeCuts")
        if cuts is None:
            problems.append("구간 %s 에 metaGradeCuts 가 없다" % key)
            continue
        broken = (cuts.get("method") != "absolute"
                  or any(cuts.get(g) != cut for g, cut in META_GRADE_CUTS)
                  or not isinstance(cuts.get("minSample"), int) or not isinstance(cuts.get("minPlayrate"), (int, float))
                  or not isinstance(cuts.get("boards"), int) or cuts["boards"] <= 0
                  or not isinstance(cuts.get("ranks"), str))
        if broken:
            problems.append("구간 %s metaGradeCuts 가 깨졌다: %s" % (key, cuts))
            continue
        # permit_filter_adjustment=false 로 받았으니 서버가 필터를 넓힌 흔적이 있으면 안 된다. 응답에 블록이 없으면 None 으로 기록된다.
        if "filterAdjustment" not in cuts:
            problems.append("구간 %s 요청의 filter_adjustment 기록이 없다" % key)
        else:
            adjustment = cuts["filterAdjustment"]
            if adjustment and (not isinstance(adjustment, dict) or adjustment.get("override_applied")
                               or adjustment.get("adjusted") or adjustment.get("applied")):
                problems.append("구간 %s metatft 요청이 넓혀졌다(filter_adjustment %s)" % (key, adjustment))
        meta_cuts[key] = cuts

    for deck in meta_decks:
        label = deck.get("id")
        if not META_ID.match(str(label or "")):
            problems.append("조합 덱 id 형식이 다르다: %s" % label)
        cluster = deck.get("metaCluster")
        if not isinstance(cluster, int) or (deck.get("global") or {}).get("cluster") != cluster:
            problems.append("조합 덱 %s metaCluster %s 가 정수가 아니거나 global.cluster 와 다르다" % (label, cluster))
        block = deck.get("metatft") or {}
        if block.get("similarity") != 1.0 or block.get("onlyInChina") or not block.get("compared") or not block.get("matchedComp"):
            problems.append("조합 덱 %s metatft 블록이 계약과 다르다: %s" % (label, block))
        stats = deck.get("stats") or {}
        graded = {key: s.get("grade") for key, s in stats.items() if s.get("grade")}
        if not graded:
            problems.append("조합 덱 %s 는 어느 구간에도 등급이 없다" % label)
        for key, stat in stats.items():
            cuts = meta_cuts.get(key)
            if not cuts:
                continue
            ok, want = meta_grade_ok(stat, cuts)
            if not ok:
                problems.append("조합 덱 %s 구간 %s 등급 %s 가 metaGradeCuts 로 다시 매긴 %s 와 다르다(n %s · avg %s)"
                                % (label, key, stat.get("grade"), want, stat.get("n"), stat.get("avg")))
            if stat.get("adjAvg") != stat.get("avg"):
                problems.append("조합 덱 %s 구간 %s adjAvg 가 avg 와 다르다" % (label, key))
            if stat.get("pick") is None or abs(stat["pick"] - (stat.get("n") or 0) / float(cuts["boards"])) > 0.00006:
                problems.append("조합 덱 %s 구간 %s pick %s 가 n/전체 보드와 다르다" % (label, key, stat.get("pick")))
        if graded:
            want_tier = graded.get("goldem") or min(graded.values(), key="SABCD".index)
            if deck.get("tier") != want_tier or deck.get("tierOrder") != "SABCD".index(want_tier):
                problems.append("조합 덱 %s tier %s/%s 가 기본 구간(없으면 최고) 등급 %s 와 다르다"
                                % (label, deck.get("tier"), deck.get("tierOrder"), want_tier))
        units = deck.get("units") or []
        unknown = sorted({str(u.get("id")) for u in units if u.get("id") not in unit_ids})
        if not units or unknown:
            problems.append("조합 덱 %s 보드가 비었거나 catalog 에 없는 유닛: %s" % (label, unknown))
        if not (deck.get("teamCode") or {}).get("code"):
            problems.append("조합 덱 %s 에 덱 코드가 없다" % label)
    for key in BUCKET_ORDER:
        grades = [((d.get("stats") or {}).get(key) or {}).get("grade") for d in meta_decks]
        print("metatft 등급(%s): %s · 등급 %d/%d · rank %s · 보드 %s"
              % (key, {g: grades.count(g) for g in "SABCD"}, sum(1 for g in grades if g), len(grades),
                 (meta_cuts.get(key) or {}).get("ranks"), (meta_cuts.get(key) or {}).get("boards")))

    # --- 중국 한정 덱 · lol.qq 그룹 배정 ---------------------------------------------
    # 조합 덱에 합쳐지지 않은 lol.qq 그룹·편집 독립 덱. 다른 조합의 metatft 수치를 보여 주지 않도록 global 을 떼어 낸다.
    compared = (version.get("sources") or {}).get("metatft") == "ok" and (version.get("sources") or {}).get("metatftBuckets") == "ok"
    for deck in china_decks:
        label = deck.get("id")
        if deck.get("global") or (deck.get("buildup") or {}).get("global"):
            problems.append("중국 한정 덱 %s 에 metatft global·buildup.global 이 남았다" % label)
        block = deck.get("metatft") or {}
        if block.get("matchedComp"):
            problems.append("중국 한정 덱 %s 에 matchedComp 가 남았다: %s" % (label, block.get("matchedComp")))
        if compared and not (block.get("onlyInChina") and (deck.get("sources") or {}).get("onlyInChina")):
            problems.append("중국 한정 덱 %s 에 중국 한정 표시(onlyInChina)가 없다" % label)
        # tier 는 기본 구간 등급(halfSA 라 S/A), 없으면 편집 등급, 둘 다 없으면 빈 문자열(deck_merge.tier_fields).
        grade = ((deck.get("stats") or {}).get("goldem") or {}).get("grade")
        editorial_tier = deck.get("editorialTier") or ""
        want = (grade, "SABCD".index(grade)) if grade else (
            editorial_tier, EDITORIAL_TIER_ORDER.get(editorial_tier, 9) if editorial_tier else 9)
        if (deck.get("tier"), deck.get("tierOrder")) != want:
            problems.append("중국 한정 덱 %s tier %s/%s 가 기본 구간 등급(없으면 편집 등급) %s/%s 와 다르다"
                            % (label, deck.get("tier"), deck.get("tierOrder"), want[0], want[1]))
    # 모든 lol.qq 그룹·편집 독립 덱이 정확히 한 덱(조합 덱의 mergedGroups 또는 중국 한정 덱 자신)에 있어야 한다.
    universe = (diag.get("merge") or {}).get("lolqqDecks")
    if not universe:
        problems.append("collector.merge.lolqqDecks(수집한 lol.qq 덱 목록)가 없다")
    else:
        seen = [member for d in meta_decks for member in d.get("mergedGroups") or []] + [d.get("id") for d in china_decks]
        missing_ids = sorted(set(universe) - set(seen))
        extra_ids = sorted(set(seen) - set(universe))
        twice = sorted({x for x in seen if seen.count(x) > 1})
        if missing_ids or extra_ids or twice:
            problems.append("lol.qq 덱 배정이 어긋난다: 빠짐 %s · 모르는 id %s · 두 번 %s" % (missing_ids[:5], extra_ids[:5], twice[:5]))
        merged_count = sum(len(d.get("mergedGroups") or []) for d in meta_decks)
        print("lol.qq 덱 %d개 = 조합 덱에 합침 %d(조합 덱 %d개) + 중국 한정 %d"
              % (len(universe), merged_count, sum(1 for d in meta_decks if d.get("mergedGroups")), len(china_decks)))
    # 상대 덱은 목록의 조합 덱만 가리킨다(목록에 없는 클러스터는 수집기가 뺀다).
    known_ids = set(ids)
    stray = [(d.get("id"), c.get("cluster")) for d in decks for c in ((d.get("global") or {}).get("counters") or [])
             if c.get("deck") not in known_ids]
    if stray:
        problems.append("목록에 없는 덱을 가리키는 불리한 상대 %d건: %s" % (len(stray), stray[:3]))

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
    # 조합 덱의 lol.qq 수치는 cnStats(대표 그룹 것)에 있다. 조합 덱 stats(metatft)는 등수 분포에서 계산해 늘 맞는다.
    impossible = 0
    for deck in decks:
        lolqq = deck.get("cnStats") if deck.get("kind") == "meta" else deck.get("stats")
        blocks = [(s, WINRATE_TOLERANCE) for s in (lolqq or {}).values()]
        blocks += [(s["precise"], PRECISE_TOLERANCE) for s in (lolqq or {}).values() if s.get("precise")]
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

    # --- lol.qq 등급 --------------------------------------------------------------
    # lol.qq 기준(buckets[b].gradeCuts: 표본 문턱·보정 강도·백분위 컷)은 구간마다 lol.qq 그룹 전체 분포로 잡는다.
    # 그 기준의 adjAvg 는 중국 한정 그룹 덱 stats 와 조합 덱 cnStats(대표 그룹 것)에 있고, 백분위 등급이 실리는 곳은
    # cnStats(참고용)뿐이다. 중국 한정 덱 등급은 halfSA(buckets[b].chinaGrade)라 따로 다시 매겨 본다.
    # 분포 검사(S 비율·역전·등급 비율)는 예전처럼 lol.qq 쪽 전체(중국 한정 + cnStats)의 백분위 등급으로 본다 —
    # 중국 한정 덱의 백분위 등급은 실린 기준으로 다시 매긴 값이다(분포 검사는 lol.qq 쪽에만).
    group_decks = [d for d in decks if d.get("kind") == "group"]

    def lolqq_rows(key):
        """[(구간 수치, 실린 등급이 백분위인가)] — 중국 한정 그룹 덱 stats(halfSA) + 조합 덱 cnStats(백분위)."""
        rows = [((d.get("stats") or {}).get(key), False) for d in group_decks]
        rows += [((d.get("cnStats") or {}).get(key), True) for d in meta_decks]
        return [(s, percentile_graded) for s, percentile_graded in rows if s]

    if not any(s.get("grade") for s, _ in lolqq_rows("goldem")):
        problems.append("lol.qq 골드~에메랄드 등급이 하나도 없다")
    if not any(((d.get("stats") or {}).get("goldem") or {}).get("grade") for d in meta_decks):
        problems.append("metatft 골드~에메랄드 등급이 하나도 없다")
    for key in BUCKET_ORDER:
        if key not in buckets:
            continue
        meta = buckets[key]
        cuts = meta.get("gradeCuts") or {}
        if cuts.get("method") not in GRADE_METHODS or any(
                not isinstance(cuts.get(k), (int, float)) for k in GRADE_CUT_KEYS):
            problems.append("구간 %s 등급 기준(gradeCuts)이 없거나 깨졌다: %s" % (key, cuts))
            continue
        rows = lolqq_rows(key)
        stats = [s for s, _ in rows]
        grades = [expected_grade(s, cuts) for s in stats]
        with_grade = [g for g in grades if g]
        mismatched = sum(1 for s, percentile_graded in rows if percentile_graded and s.get("grade") != expected_grade(s, cuts))
        if mismatched:
            problems.append("구간 %s 조합 덱 cnStats 등급 %d건이 실린 기준(gradeCuts)으로 다시 매긴 등급과 다르다"
                            % (key, mismatched))

        # 중국 한정 덱 halfSA: 대상 = stats[b] 가 있고 n ≥ lol.qq minSample. adjAvg 오름차순(같으면 n 큰 쪽, 그다음 id)
        # 앞 floor(k/2) 개 S, 나머지 A, 대상 밖 null. 편집 독립 덱은 lol.qq 수치가 없어 늘 대상 밖이다.
        china = meta.get("chinaGrade") or {}
        eligible = sorted((d for d in china_decks
                           if ((d.get("stats") or {}).get(key) or {}).get("adjAvg") is not None
                           and (d["stats"][key].get("n") or 0) >= cuts["minSample"]),
                          key=lambda d: (d["stats"][key]["adjAvg"], -d["stats"][key]["n"], d.get("id") or ""))
        k = len(eligible)
        want_grade = dict((d.get("id"), "S" if rank < k // 2 else "A") for rank, d in enumerate(eligible))
        if (china.get("method"), china.get("minSample"), china.get("eligible"), china.get("S"), china.get("A")) != (
                CHINA_GRADE_METHOD, cuts["minSample"], k, k // 2, k - k // 2):
            problems.append("구간 %s 중국 한정 등급 기록(chinaGrade) %s 가 규칙(halfSA · 표본≥%s · 대상 %d · S %d · A %d)과 다르다"
                            % (key, china, cuts["minSample"], k, k // 2, k - k // 2))
        wrong = [d.get("id") for d in china_decks if (d.get("stats") or {}).get(key)
                 and d["stats"][key].get("grade") != want_grade.get(d.get("id"))]
        if wrong:
            problems.append("구간 %s 중국 한정 덱 등급 %d건이 halfSA(adjAvg 순 앞 절반 S · 나머지 A · 대상 밖 null)와 다르다: %s"
                            % (key, len(wrong), wrong[:5]))
        print("중국 한정 등급(%s): 대상 %d(표본≥%s) → S %d · A %d"
              % (meta.get("label") or key, k, cuts["minSample"], k // 2, k - k // 2))
        # adjAvg 는 실린 shrinkK·shrinkTo 로 다시 계산한 값과 맞아야 한다(avg 반올림 몫만큼 허용).
        off = [s for s in stats if s.get("avg") is not None and s.get("adjAvg") is not None and abs(
            s["adjAvg"] - (s["n"] * s["avg"] + cuts["shrinkK"] * cuts["shrinkTo"]) / float(s["n"] + cuts["shrinkK"]))
            > ADJ_AVG_TOLERANCE]
        if off:
            problems.append("구간 %s adjAvg %d건이 실린 shrinkK %s · shrinkTo %s 로 다시 계산한 값과 %.2f 넘게 다르다"
                            % (key, len(off), cuts["shrinkK"], cuts["shrinkTo"], ADJ_AVG_TOLERANCE))
        # 원평균이 0.3등 이상 좋은데 (백분위) 등급이 더 낮은 쌍(보정이 판수 적은 좋은 덱을 뒤집는 신호).
        ranked = [(s["avg"], g) for s, g in zip(stats, grades) if g and s.get("avg") is not None]
        inverted = sum(1 for x_avg, x_grade in ranked for y_avg, y_grade in ranked
                       if x_avg <= y_avg - INVERSION_AVG_GAP and "SABCD".index(x_grade) > "SABCD".index(y_grade))
        if inverted:
            text = "구간 %s 원평균이 %.1f등 이상 좋은데 등급이 더 낮은 쌍 %d개" % (key, INVERSION_AVG_GAP, inverted)
            if key == "goldem":
                problems.append(text)
            else:
                warnings.append(text)
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
        print("lol.qq 백분위 등급 분포(%s): %s · 등급 %d/%d%s · %s S≤%s A≤%s B≤%s C≤%s · 표본≥%s · K %s"
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

    print("검증 통과 — 덱 %d개(metatft 조합 %d · 그중 lol.qq 합침 %d · 중국 한정 %d), 패치 %s, 아이템 인덱스 %d개, "
          "덱 코드 %d개, %.0f KB"
          % (len(decks), len(meta_decks), sum(1 for d in meta_decks if d.get("mergedGroups")), len(china_decks),
             version.get("patch"), len(index.get("item") or {}), version.get("teamCodeCount", 0), size / 1024.0))
    return 0


if __name__ == "__main__":
    sys.exit(main())
