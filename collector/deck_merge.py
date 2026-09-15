#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
lol.qq 胜率阵容(W) · 数据检索器(R) · 편집 덱(E)을 하나의 덱 목록으로 합치는 계산.

네트워크를 쓰지 않는다. 설계 §4.3(병합 절차)·§4.4(등급 산식)를 여기 한 곳에 둬서
컷·표본 문턱을 바꿀 때 이 파일만 고치면 되게 했다.

그룹 정체성에 대해: 설계는 그룹 키를 '주특성(id.개수) + 메인C' 로 적었지만, 실제 목록에서는
lol.qq 한 그룹 안의 조합끼리 주특성 개수와 메인C 가 서로 다르다(골드~에메랄드 45그룹 → 122가지).
그대로 키를 만들면 한 덱이 잘게 쪼개지므로, lol.qq 그룹 id 에 인코딩된 주특성 id 들과
메인C 로 키를 만든다(개수는 변형 쪽에 남는다). 다섯 구간에서 같은 그룹이 같은 키가 된다.
"""

import base64
import binascii
import hashlib
import math
import statistics
from collections import OrderedDict

# (키, 라벨, lol.qq tier_part)
BUCKETS = (
    ("all", "전체", "255"),
    ("master", "마스터+", "0"),
    ("diamond", "다이아+", "1"),
    ("goldem", "골드~에메랄드", "2"),
    ("low", "골드 이하", "3"),
)
BUCKET_LABELS = {key: label for key, label, _ in BUCKETS}
DEFAULT_BUCKET = "goldem"
# 대표 변형과 상세 구간을 고르는 순서. 앱 기본 구간이 먼저다.
BUCKET_PRIORITY = ("goldem", "all", "diamond", "master", "low")
# 구간별 노출(상세 호출 대상) 상한. 앞 구간부터 채우고 그룹 중복은 뺀다.
EXPOSURE_QUOTA = (("goldem", 40), ("all", 25), ("diamond", 15), ("master", 10), ("low", 10))
# 数据检索器 스코프가 어느 구간의 참고치인지. 4+ 는 넓은 구간, 7+ 는 마스터+.
PRECISE_SCOPE_FOR_BUCKET = {"goldem": "cn_plat", "all": "cn_plat", "diamond": "cn_plat",
                            "low": "cn_plat", "master": "cn_master"}

# 등급: 구간마다 그 구간 그룹 분포로 기준을 잡는다(bucket_grade_cuts). 조정은 여기 상수 한 곳에서 한다.
# 구간마다 표본 규모가 수십 배 다르다(2026-09-15 수집분 그룹 n 중앙값: 전체 4613 · 골드~에메랄드 3100 ·
# 골드 이하 1065 · 다이아+ 250 · 마스터+ 104). 전 구간에 문턱 300 · 보정 K 200 을 쓰면 다이아+ 는 16/28,
# 마스터+ 는 9/9 그룹이 표본 부족이었고, 등급이 붙어도 K 가 표본만 해서 평균이 4.5 쪽으로 끌려가 D 로 눌렸다.
#   minSample = clamp(사사오입(n 중앙값 × 0.1), 30, 300)  — 미만이면 grade null(표본 부족)
#   shrinkK   = clamp(사사오입(n 중앙값 × 0.2), 20, 200)  — adjAvg = (n·avg + K·4.5) / (n + K)
#   컷        = minSample 이상 그룹 adjAvg 의 10/25/50/75% 분위수 → S/A/B/C, 그 밖 D
# minSample 이상 그룹이 5개 미만이면 분위수가 뜻이 없어 절대 컷 GRADE_CUTS 를 쓴다.
# 절대 컷: 胜率阵容은 평균 4.0 이하 조합만 노출해 그룹 평균이 2.4~3.8 에 몰린다. 설계 초기값
# (S 3.90 / A 4.15 / B 4.40 / C 4.70)으로는 전부 S 가 돼 2026-09-15 골드~에메랄드 분포
# (10/25/50/75/90% = 2.70/2.96/3.09/3.27/3.50)에 맞춰 다시 잡았다.
GRADE_CUTS = (("S", 2.70), ("A", 3.00), ("B", 3.25), ("C", 3.50))
GRADE_PERCENTILES = (("S", 0.10), ("A", 0.25), ("B", 0.50), ("C", 0.75))
PERCENTILE_MIN_GROUPS = 5
SHRINK_TO = 4.5
MIN_SAMPLE_RATIO, MIN_SAMPLE_FLOOR, MIN_SAMPLE = 0.1, 30, 300   # MIN_SAMPLE 은 문턱 상한
SHRINK_K_RATIO, SHRINK_K_FLOOR, SHRINK_K = 0.2, 20, 200         # SHRINK_K 는 보정 강도 상한
TREND_AVG_DELTA = 0.05
GRADE_ORDER = {"S": 0, "A": 1, "B": 2, "C": 3, "D": 4}
# 편집 덱 등급(v1 과 같은 순서). 통계 등급이 없는 덱의 정렬에만 쓴다.
EDITORIAL_TIER_ORDER = {"SS": 0, "S": 1, "A": 2, "B": 3, "C": 4, "D": 5}

PRECISE_MATCH = 0.7
EDITORIAL_MATCH = 0.5

# 상세 가공 상한
POSITION_TOP = 3
KEY_UNIT_LIMIT = 10
WEARER_TOP = 3
CN_BUILDUP_OPTIONS = 3
CN_BUILDUP_MIN_N = 30


def _int(value, default=None):
    try:
        return int(float(str(value).strip()))
    except (TypeError, ValueError):
        return default


def _float(value, default=None):
    try:
        return float(str(value).strip())
    except (TypeError, ValueError):
        return default


def _round(value, digits):
    return None if value is None else round(value, digits)


def short_hash(text):
    return hashlib.sha1(text.encode("utf-8")).hexdigest()[:10]


def jaccard(a, b):
    union = a | b
    return len(a & b) / float(len(union)) if union else 0.0


# ----------------------------------------------------------------------------
# 유닛 집합 정규화
# ----------------------------------------------------------------------------

class UnitSpace:
    """
    덱 매칭용 유닛 집합. 럭스 변형은 한 챔피언으로 접고, 소환물·pet·TFT18_* 는 뺀다.
    champions 는 CommunityDragon 세트 챔피언 apiName, aliases 는 같은 캐릭터의 다른
    형태(metatft unit_lookup 의 같은 TFT18_* apiName)를 CDragon 쪽 id 로 잇는 표.
    """

    LUX = "DA_Lux18_Base"

    def __init__(self, champion_ids, aliases=None):
        self.champions = set(champion_ids)
        self.aliases = dict(aliases or {})

    def canonical(self, unit_id):
        uid = str(unit_id or "").strip()
        if not uid or uid.startswith("TFT") or uid.startswith("QQ_"):
            return None
        if uid.startswith("DA_18_Lux_") or uid.startswith("DA_Lux18_"):
            return self.LUX
        if uid not in self.champions:
            uid = self.aliases.get(uid, uid)
        return uid if uid in self.champions else None

    def normalize(self, ids):
        return frozenset(c for c in (self.canonical(u) for u in ids or []) if c)


# ----------------------------------------------------------------------------
# W: 胜率阵容 그룹·변형
# ----------------------------------------------------------------------------

def decode_group(group):
    """
    그룹 id 'winlineup_' + base64('1100#10379;10383$100331;100337$8')
    -> (주특성 id 들, [메인C, 보조C], 인원). 해독이 안 되면 목록 필드로 대신한다.
    """
    gid = str(group.get("id") or "")
    try:
        payload = base64.b64decode(gid.split("_", 1)[1]).decode("utf-8")
        _, rest = payload.split("#", 1)
        traits, carries, num = rest.split("$")
        trait_ids = [t for t in traits.split(";") if t]
        carry_ids = [c for c in carries.split(";") if c]
        if trait_ids and carry_ids:
            return trait_ids, carry_ids, _int(num)
    except (IndexError, ValueError, binascii.Error, UnicodeDecodeError):
        pass
    info = group.get("info") or {}
    carries = [info.get("main_c_chess_id")] + list(info.get("main_assist_chess") or [])[1:]
    return [str(t) for t in group.get("main_trait_list") or []], [str(c) for c in carries if c], _int(group.get("num"))


def group_key(trait_das, carry_da):
    return "|".join(sorted(trait_das)) + "|" + carry_da


def group_id(key):
    return "g-" + short_hash(key)


def variant_id(units):
    return "v-" + short_hash(",".join(sorted(units)))


def build_groups(lists, static, space, join):
    """
    lists: {bucket: tft_lineup_group_list data}. join: 조인 통계를 쌓는 dict.
    반환: OrderedDict gid -> group(작업용 dict). 순서는 구간 우선순위대로 처음 본 순.
    """
    groups = OrderedDict()
    for bucket in BUCKET_PRIORITY:
        data = lists.get(bucket)
        if not data:
            continue
        for source in data.get("main_traits_data") or []:
            trait_ids, carry_ids, num = decode_group(source)
            trait_das = [static.trait_da(t) or "?" + t for t in trait_ids]
            carry_da = static.chess_da(carry_ids[0]) if carry_ids else None
            if not carry_da:
                join["groupsSkipped"] = join.get("groupsSkipped", 0) + 1
                continue
            key = group_key(trait_das, carry_da)
            gid = group_id(key)
            group = groups.get(gid)
            if group is None:
                group = groups[gid] = {
                    "id": gid, "key": key, "traitIds": trait_das, "carryId": carry_da,
                    "num": num, "sources": set(), "variants": OrderedDict(), "editorials": [],
                }
            group["sources"].add(str(source.get("id")))

            for row in (source.get("info") or {}).get("list") or []:
                lineup = [str(c) for c in row.get("lineup") or []]
                das = []
                for cid in lineup:
                    join["champions"] = join.get("champions", 0) + 1
                    da = static.chess_da(cid)
                    if da:
                        das.append(da)
                    else:
                        join["championsMissing"] = join.get("championsMissing", 0) + 1
                for trait in list(row.get("main_trait_list") or []) + list(row.get("sub_trait_list") or []):
                    if str(trait.get("trait_id")) in ("", "-1"):
                        continue
                    join["traits"] = join.get("traits", 0) + 1
                    if not static.trait_da(trait.get("trait_id")):
                        join["traitsMissing"] = join.get("traitsMissing", 0) + 1

                units = space.normalize(das)
                if not units:
                    continue
                vid = variant_id(units)
                variant = group["variants"].get(vid)
                if variant is None:
                    variant = group["variants"][vid] = {
                        "id": vid, "units": units, "occ": {}, "precise": {}, "editorialIds": [],
                    }
                variant["occ"].setdefault(bucket, []).append({"row": row, "das": das, "num": num})
    return groups


def use_num(occurrences):
    return sum(_int(o["row"].get("use_num"), 0) for o in occurrences or [])


def aggregate(rows):
    """use_num 가중 합산. rows: W 원본 조합 행들."""
    n = wavg = wtop4 = wwin = pick = 0.0
    diff_n = wdiff = 0.0
    pick_diff = None
    for row in rows:
        use = _int(row.get("use_num"), 0)
        avg = _float(row.get("avg_rank"))
        if use <= 0 or avg is None:
            continue
        n += use
        wavg += use * avg
        wtop4 += use * (_float(row.get("top_4_rate"), 0.0))
        wwin += use * (_float(row.get("top_1_rate"), 0.0))
        pick += _float(row.get("use_rate"), 0.0)
        diff = _float(row.get("avg_rank_diff"))
        if diff is not None:
            diff_n += use
            wdiff += use * diff
        pdiff = _float(row.get("use_rate_diff"))
        if pdiff is not None:
            pick_diff = (pick_diff or 0.0) + pdiff
    if n <= 0:
        return None
    return {
        "n": int(n), "avg": wavg / n, "top4": wtop4 / n, "win": wwin / n, "pick": pick,
        "avgDiff": wdiff / diff_n if diff_n else None, "pickDiff": pick_diff,
    }


def grade_for(adj_avg, cuts):
    """cuts: bucket_grade_cuts 가 만든 그 구간 기준. 컷 이하이면 그 등급, 넷 다 넘으면 D."""
    for grade, _ in GRADE_CUTS:
        if adj_avg <= cuts[grade]:
            return grade
    return "D"


def trend_for(avg_diff, pick_diff):
    if avg_diff is None or pick_diff is None:
        return "flat"
    if avg_diff < -TREND_AVG_DELTA and pick_diff > 0:
        return "up"
    if avg_diff > TREND_AVG_DELTA and pick_diff < 0:
        return "down"
    return "flat"


def adjusted_avg(agg, shrink_k):
    """표본 보정 평균. 표시되는 반올림 값이라 등급·분위수도 이 값으로 매겨 화면과 어긋나지 않게 한다."""
    n = agg["n"]
    return round((n * agg["avg"] + shrink_k * SHRINK_TO) / float(n + shrink_k), 2)


def percentile(values, q):
    """선형 보간 분위수(numpy 기본 방식). values 는 비어 있지 않아야 한다."""
    ordered = sorted(values)
    pos = (len(ordered) - 1) * q
    low = int(math.floor(pos))
    high = min(low + 1, len(ordered) - 1)
    return ordered[low] + (ordered[high] - ordered[low]) * (pos - low)


def percentile_cuts(values):
    """낮을수록 좋은 값들 -> {"S": p10, "A": p25, "B": p50, "C": p75}. 표시 자리수(2)로 반올림해 등급과 화면을 맞춘다."""
    return dict((grade, round(percentile(values, q), 2)) for grade, q in GRADE_PERCENTILES)


def _half_up(value):
    # 파이썬 round 는 .5 를 짝수 쪽으로 보낸다. 문턱은 사사오입으로 잡는다.
    return int(math.floor(value + 0.5))


def bucket_grade_cuts(aggs):
    """
    한 구간 그룹 집계들 -> 그 구간 등급 기준. decks.json buckets[b].gradeCuts 에 그대로 싣는다.
    표본 문턱·보정 강도는 n 중앙값에 비례(상·하한 안), 컷은 문턱을 넘은 그룹 adjAvg 의 분위수.
    """
    if aggs:
        med = statistics.median([agg["n"] for agg in aggs])
        min_sample = max(MIN_SAMPLE_FLOOR, min(MIN_SAMPLE, _half_up(med * MIN_SAMPLE_RATIO)))
        shrink_k = max(SHRINK_K_FLOOR, min(SHRINK_K, _half_up(med * SHRINK_K_RATIO)))
    else:
        min_sample, shrink_k = MIN_SAMPLE, SHRINK_K
    eligible = [adjusted_avg(agg, shrink_k) for agg in aggs if agg["n"] >= min_sample]
    if len(eligible) >= PERCENTILE_MIN_GROUPS:
        cuts, method = percentile_cuts(eligible), "percentile"
    else:
        cuts, method = dict(GRADE_CUTS), "absolute"
    cuts.update(minSample=min_sample, shrinkK=shrink_k, method=method)
    return cuts


def grade_cuts(aggregates):
    """{gid: group_aggregates 결과} -> {구간: 등급 기준}. 통계가 없는 구간도 기준(상한 문턱·절대 컷)은 만든다."""
    return {bucket: bucket_grade_cuts([aggs[bucket] for aggs in aggregates.values() if bucket in aggs])
            for bucket, _, _ in BUCKETS}


def finish_stats(agg, cuts):
    """집계 -> 카드 수치. cuts: 그 구간 등급 기준(bucket_grade_cuts)."""
    n = agg["n"]
    adj = adjusted_avg(agg, cuts["shrinkK"])
    return {
        "n": n,
        "avg": round(agg["avg"], 2),
        "adjAvg": adj,
        "top4": round(agg["top4"], 3),
        "win": round(agg["win"], 3),
        "pick": round(agg["pick"], 4),
        "avgDiff": _round(agg["avgDiff"], 2),
        "pickDiff": _round(agg["pickDiff"], 4),
        "grade": grade_for(adj, cuts) if n >= cuts["minSample"] else None,
        "trend": trend_for(agg["avgDiff"], agg["pickDiff"]),
    }


def group_aggregates(group):
    """등급 1단계: 구간별 use_num 가중 집계. 구간 기준은 모든 그룹을 집계한 뒤 grade_cuts 로 잡는다."""
    out = {}
    for bucket, _, _ in BUCKETS:
        rows = [o["row"] for v in group["variants"].values() for o in v["occ"].get(bucket) or []]
        agg = aggregate(rows)
        if agg:
            out[bucket] = agg
    return out


def group_stats(aggregates, cuts):
    """등급 2단계: 그룹의 구간 집계(group_aggregates) -> 카드 수치. cuts: grade_cuts 결과."""
    return {bucket: finish_stats(agg, cuts[bucket]) for bucket, agg in aggregates.items()}


def variant_stats(variant):
    out = {}
    for bucket, _, _ in BUCKETS:
        agg = aggregate([o["row"] for o in variant["occ"].get(bucket) or []])
        if agg:
            # 앱의 변형 행은 그룹과 같은 고정 4수치 줄을 쓴다. 픽률이 빠지면 그 칸만 늘 '-' 로 비어 보인다.
            out[bucket] = {"n": agg["n"], "avg": round(agg["avg"], 2),
                           "top4": round(agg["top4"], 3), "win": round(agg["win"], 3),
                           "pick": round(agg["pick"], 4)}
    return out


def representative(group):
    """기본 구간에서 표본이 가장 큰 변형. 그 구간에 없으면 우선순위 다음 구간."""
    for bucket in BUCKET_PRIORITY:
        present = [v for v in group["variants"].values() if v["occ"].get(bucket)]
        if present:
            return max(present, key=lambda v: use_num(v["occ"][bucket])), bucket
    return None, None


def best_occurrence(variant, bucket):
    occ = variant["occ"].get(bucket) or []
    return max(occ, key=lambda o: _int(o["row"].get("use_num"), 0)) if occ else None


def sort_key(stats):
    stats = stats or {}
    return (GRADE_ORDER.get(stats.get("grade"), 9), stats.get("adjAvg") if stats.get("adjAvg") is not None else 9.0)


def tier_fields(stats, editorial_tier):
    """tier = 기본 구간 등급, 없으면 편집 등급, 둘 다 없으면 빈 문자열(v1 앱은 문자열을 기대한다)."""
    grade = (stats.get(DEFAULT_BUCKET) or {}).get("grade")
    if grade:
        return grade, GRADE_ORDER[grade]
    if editorial_tier:
        return editorial_tier, EDITORIAL_TIER_ORDER.get(editorial_tier, 9)
    return "", 9


def exposure_plan(groups):
    """[(group, bucket)] — 구간별 등급순 상위 N, 그룹은 처음 뽑힌 구간 하나로만."""
    plan, seen = [], set()
    for bucket, quota in EXPOSURE_QUOTA:
        ranked = sorted((g for g in groups if bucket in g["stats"]),
                        key=lambda g: sort_key(g["stats"][bucket]))
        for group in ranked[:quota]:
            if group["id"] in seen:
                continue
            seen.add(group["id"])
            plan.append((group, bucket))
    return plan


# ----------------------------------------------------------------------------
# R: 数据检索器 조합 -> 변형 참고치
# ----------------------------------------------------------------------------

def feasible(avg, win, top4, tolerance=0.0):
    """
    평균 등수가 1등·TOP4 비율로 가능한 범위 안인가.
    가장 좋은 경우: 1등 외 TOP4 는 2등, 나머지는 5등. 가장 나쁜 경우: 4등과 8등.
    """
    low = 1 * win + 2 * (top4 - win) + 5 * (1 - top4)
    high = 1 * win + 4 * (top4 - win) + 8 * (1 - top4)
    return low - tolerance <= avg <= high + tolerance


def precise_rows(rows, scope, space):
    """tft_lineup_rank 행 -> [{units, stat}]. 범위 검사를 통과한 행만. (행들, 버린 수)"""
    out, dropped = [], 0
    for row in rows or []:
        n = _int(row.get("comp_s"), 0)
        avg = _float(row.get("avg_rank"))
        if n <= 0 or avg is None:
            dropped += 1
            continue
        win = _int(row.get("top1_cnt"), 0) / float(n)
        top4 = _int(row.get("top4_cnt"), 0) / float(n)
        if not feasible(avg, win, top4):
            dropped += 1
            continue
        units = space.normalize((row.get("lineup") or {}).get("chess_ids"))
        if not units:
            dropped += 1
            continue
        out.append({"units": units, "stat": {
            "scope": scope, "n": n, "avg": round(avg, 2), "top4": round(top4, 3), "win": round(win, 3),
            "finalLevel": _int(row.get("final_level")),
        }})
    return out, dropped


def attach_precise(groups, rows):
    """각 R 행을 자카드 0.7 이상인 가장 가까운 변형 하나에 붙인다. 붙은 행 수를 돌려준다."""
    variants = [v for g in groups.values() for v in g["variants"].values()]
    matched = 0
    for row in rows:
        best, score = None, 0.0
        for variant in variants:
            value = jaccard(row["units"], variant["units"])
            if value > score:
                best, score = variant, value
        if best is None or score < PRECISE_MATCH:
            continue
        matched += 1
        scope = row["stat"]["scope"]
        current = best["precise"].get(scope)
        if current is None or row["stat"]["n"] > current["n"]:
            best["precise"][scope] = row["stat"]
    return matched


def group_precise(group, bucket):
    """그 구간 참고치: 구간에 나온 변형 중 해당 스코프 표본이 가장 큰 R 행."""
    scope = PRECISE_SCOPE_FOR_BUCKET.get(bucket)
    candidates = [v["precise"][scope] for v in group["variants"].values()
                  if v["occ"].get(bucket) and scope in v["precise"]]
    return max(candidates, key=lambda s: s["n"]) if candidates else None


# ----------------------------------------------------------------------------
# E: 편집 덱 첨부
# ----------------------------------------------------------------------------

def attach_editorials(groups, editorials):
    """
    editorials: [{"id", "normUnits"(정규화 집합), "updatedAt", ...}].
    자카드 0.5 이상인 변형이 있으면 그 그룹에 붙이고, 없으면 독립 덱으로 돌려준다.
    """
    variants = [(g, v) for g in groups.values() for v in g["variants"].values()]
    standalone = []
    for editorial in editorials:
        best, score = None, 0.0
        for group, variant in variants:
            value = jaccard(editorial["normUnits"], variant["units"])
            if value > score:
                best, score = (group, variant), value
        editorial["similarity"] = round(score, 3)
        if best is None or score < EDITORIAL_MATCH:
            standalone.append(editorial)
            continue
        group, variant = best
        group["editorials"].append(editorial)
        variant["editorialIds"].append(editorial["id"])
    for group in groups.values():
        group["editorials"].sort(key=lambda e: e.get("updatedAt") or "", reverse=True)
    return standalone


# ----------------------------------------------------------------------------
# 상세 가공 (tft_lineup_all_detail / position / key_chess)
# ----------------------------------------------------------------------------

def _stage_value(raw):
    """단계별 평균: '0'·빈 값은 null. 소수 한 자리 이하('2', '3.5')는 몇 판뿐인 표본이다."""
    text = str(raw if raw is not None else "").strip()
    value = _float(text)
    if value is None or value == 0:
        return None, True
    fraction = text.split(".", 1)[1] if "." in text else ""
    return round(value, 2), len(fraction) <= 1


def augment_stats(detail, item_ref):
    out = []
    for entry in detail.get("augment_data") or []:
        rune = str(entry.get("rune_id") or "").strip()
        info = entry.get("info") or {}
        n, avg = _int(info.get("use_num"), 0), _float(info.get("avg_rank"))
        if not rune or n <= 0 or avg is None:
            continue
        stage, low = [], []
        for step in (1, 2, 3):
            value, is_low = _stage_value(info.get("%d_avg_rank" % step))
            stage.append(value)
            low.append(is_low)
        row = dict(item_ref(rune))
        row.update({"rank": _int(info.get("rune_id_rank")), "n": n, "avg": round(avg, 2),
                    "stage": stage, "stageLowSample": low})
        out.append(row)
    out.sort(key=lambda r: (r["rank"] if r["rank"] is not None else 99, -r["n"]))
    return out


def key_units(rows, static):
    out = []
    for row in rows or []:
        da = static.chess_da(row.get("chess_id"))
        info = row.get("info") or {}
        if not da:
            continue
        out.append((_int(info.get("chess_rank"), 99), {
            "id": da,
            "star1": _round(_float(info.get("1_star_percent")), 3),
            "star2": _round(_float(info.get("2_star_percent")), 3),
            "star3": _round(_float(info.get("3_star_percent")), 3),
            "items": _round(_float(info.get("equip_num")), 2),
            "avg": _round(_float(info.get("avg_rank")), 2),
        }))
    out.sort(key=lambda pair: pair[0])
    return [unit for _, unit in out[:KEY_UNIT_LIMIT]]


def positions(detail, static):
    """유닛별 사용률 상위 3칸. x -> col(1..7), y -> row(1..4). 범위를 벗어난 칸은 버린다."""
    out = {}
    for row in detail.get("content_pos_data") or []:
        da = static.chess_da(row.get("chess_id"))
        if not da:
            continue
        cells = []
        for cell in sorted(row.get("position_data") or [], key=lambda c: -(_float(c.get("use_rate"), 0))):
            pos = cell.get("position") or {}
            col, line = _int(pos.get("x")), _int(pos.get("y"))
            if col is None or line is None or not (1 <= col <= 7 and 1 <= line <= 4):
                continue
            cells.append({"row": line, "col": col, "use": _round(_float(cell.get("use_rate")), 3),
                          "win": _round(_float(cell.get("win_rate")), 3)})
            if len(cells) >= POSITION_TOP:
                break
        if cells:
            out[da] = cells
    return out


def level_dist(detail):
    out = []
    for row in detail.get("level_data") or []:
        level = _int(row.get("degree"))
        info = row.get("info") or {}
        if level is None:
            continue
        out.append({"level": level, "share": _round(_float(info.get("use_num_percent")), 3),
                    "avg": _round(_float(info.get("avg_rank")), 2)})
    out.sort(key=lambda r: r["level"])
    return out


def item_wearers(detail, static, item_ref, unit_name):
    out = []
    for block in detail.get("equip_data") or []:
        for entry in block.get("list") or []:
            equip = str(((entry.get("info") or {}).get("equip_id")) or "").strip()
            if not equip:
                continue
            wearers = []
            for row in sorted(entry.get("list") or [], key=lambda r: -(_int(r.get("use_num"), 0))):
                da = static.chess_da(row.get("chess_id"))
                if not da:
                    continue
                wearers.append({"id": da, "name": unit_name(da), "n": _int(row.get("use_num"), 0),
                                "avg": _round(_float(row.get("avg_rank")), 2)})
                if len(wearers) >= WEARER_TOP:
                    break
            if wearers:
                out.append({"item": item_ref(equip), "wearers": wearers})
    return out


def buildup_cn(detail, static, bucket, sort_units):
    """level_change_lineup_data -> buildup.cn (설계 §13.2). 레벨당 rank 순 상위 3, use_num ≥ 30."""
    levels = []
    for entry in detail.get("level_change_lineup_data") or []:
        level = _int(entry.get("level"))
        if level is None:
            continue
        options = []
        for row in sorted(entry.get("list") or [], key=lambda r: _int(r.get("rank"), 99)):
            n = _int(row.get("use_num"), 0)
            if n < CN_BUILDUP_MIN_N:
                continue
            units = [static.chess_da(c) for c in row.get("lineup") or []]
            if not units or any(u is None for u in units):
                continue
            options.append({
                "units": sort_units(units),
                "carryId": static.chess_da(row.get("main_c_chess")),
                "n": n,
                "avg": _round(_float(row.get("avg_rank")), 2),
                "top4": _round(_float(row.get("top_4_rate")), 3),
                "win": _round(_float(row.get("top_1_rate")), 3),
            })
            if len(options) >= CN_BUILDUP_OPTIONS:
                break
        levels.append({"level": level, "options": options})
    if not levels:
        return None
    levels.sort(key=lambda r: r["level"])
    return {"bucket": bucket, "levels": levels}


POSITION_MIN_AGREEMENT = 0.40
POSITION_MIN_COMPARED = 5


def choose_position_mapping(agreement):
    """
    실측 칸의 y 해석을 고른다. 기본 해석(row = y)이 40% 이상 맞으면 그대로,
    아니면 행을 뒤집은 해석(row = 5 - y)이 40% 이상일 때 그것, 둘 다 아니면 None(싣지 않음).
    """
    def ratio(pair):
        return pair[0] / float(pair[1]) if pair[1] >= POSITION_MIN_COMPARED else 0.0

    as_is, flipped = ratio(agreement["asIs"]), ratio(agreement["rowFlipped"])
    if as_is >= POSITION_MIN_AGREEMENT and as_is >= flipped:
        return "asIs"
    if flipped >= POSITION_MIN_AGREEMENT:
        return "rowFlipped"
    return None


def position_agreement(pairs):
    """
    편집 덱 최종 좌표와 실측 최빈 칸이 같은 비율. pairs: [(편집 유닛들, positions)].
    반환: {mapping: (일치, 비교)} — 기본 해석과 뒤집은 해석 두 가지를 같이 센다(진단용).
    """
    result = {"asIs": [0, 0], "rowFlipped": [0, 0]}
    for units, cells in pairs:
        for unit in units:
            top = (cells.get(unit.get("id")) or [None])[0]
            if top is None or unit.get("row") is None or unit.get("col") is None:
                continue
            result["asIs"][1] += 1
            result["rowFlipped"][1] += 1
            if (top["row"], top["col"]) == (unit["row"], unit["col"]):
                result["asIs"][0] += 1
            if (5 - top["row"], top["col"]) == (unit["row"], unit["col"]):
                result["rowFlipped"][0] += 1
    return result
