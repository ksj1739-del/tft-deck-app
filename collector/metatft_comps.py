#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
metatft 덱(클러스터) 통계: comps_stats(스코프별 등수 분포), comps_data(운영 방식·난이도),
comp_details(최종 레벨·상대 덱·레벨별 빌드업).

필터(rank/days/server)가 실제로 적용되는 곳은 comps_stats 뿐이다. comps_data 와
comp_details 는 고정 모집단이므로 스코프 라벨을 붙이지 않는다(설계 §3.1).
네트워크 함수는 fetch_json(url, timeout=N) 을 주입받아 기존 재시도·gzip 처리를 재사용한다.
"""

import re
from datetime import datetime, timezone

COMPS_BASE = "https://api-hc.metatft.com/tft-comps-api/"
STAT_PATCH = "https://api-hc.metatft.com/tft-stat-api/patch"

RANK_PLAT = "CHALLENGER,DIAMOND,EMERALD,GRANDMASTER,MASTER,PLATINUM"
RANK_MASTER = "CHALLENGER,GRANDMASTER,MASTER"
DAYS = 3

# (키, 라벨, rank 집합, server)
SCOPES = (
    ("glob_plat", "글로벌 플래티넘+", RANK_PLAT, None),
    ("kr_plat", "KR 플래티넘+", RANK_PLAT, "KR"),
    ("kr_master", "KR 마스터+", RANK_MASTER, "KR"),
)

# 앱 구간(lol.qq 胜率阵容 구간과 같은 키)마다 comps_stats 를 전 지역으로 한 번씩 받는다. rank 는 사이트처럼 알파벳순.
# low: lol.qq tier_part 3 의 라벨은 '黄金以下'지만 실제로는 골드를 빼고 센다 — 2026-09-19 목록 5구간의
# use_num/use_rate 역산 분모가 전체 2.88M ≈ 다이아+ 0.23M + 골드~에메랄드 1.94M + tier 3 0.70M 로 겹침 없이 맞았다.
# 그래서 goldem 과 겹치지 않는 아이언~실버로 둔다.
RANK_ALL = "BRONZE,CHALLENGER,DIAMOND,EMERALD,GOLD,GRANDMASTER,IRON,MASTER,PLATINUM,SILVER"
BUCKET_RANKS = (
    ("all", RANK_ALL),
    ("master", RANK_MASTER),
    ("diamond", "CHALLENGER,DIAMOND,GRANDMASTER,MASTER"),
    ("goldem", "EMERALD,GOLD,PLATINUM"),
    ("low", "BRONZE,IRON,SILVER"),
)
# metatft 사이트의 조합 등급(main 번들 CompRow): 반올림 전 평균 등수 p 에 p<4.25 S, <4.5 A, <4.75 B, <5.0 C, 그 밖 D.
# 부등호가 '<' 라 lol.qq 등급(deck_merge.grade_for, '≤')과 다르다.
META_GRADE_CUTS = (("S", 4.25), ("A", 4.5), ("B", 4.75), ("C", 5.0))
# 구간별 등급 표본 문턱(고정). 마스터+ 도 1000 으로 등급 받는 조합이 41개(2026-09-19)라 500 으로 낮추지 않았다.
META_MIN_SAMPLE = {"all": 1000, "master": 1000, "diamond": 1000, "goldem": 1000, "low": 1000}
# 사이트 조합 목록의 기본 최소 픽률(min_playrate): 로비당 인원(pick × 8)이 이보다 작으면 목록에서 숨긴다.
META_MIN_PLAYRATE = 0.01
LOBBY_SIZE = 8

LEVELLING_KO = {
    "Fast 8": "빠른 8레벨",
    "Fast 9": "빠른 9레벨",
    "Standard": "표준",
    "Reroll": "리롤",
    "Slow Roll": "리롤",
}
# 'lvl 6' 은 metatft 화면에서 6레벨 리롤 덱을 뜻한다.
LEVELLING_REROLL = re.compile(r"^lvl\s*(\d+)$", re.I)

# metatft difficulty 는 0 근처의 실수다. 화면 라벨 경계는 공개되지 않아 추정값을 상수로 둔다.
DIFFICULTY_EASY = -0.1
DIFFICULTY_HARD = 0.1

# 빌드업(설계 §13.2)
BUILDUP_LEVELS = range(4, 11)
BUILDUP_OPTIONS = 3
BUILDUP_MIN_N = 100
FINAL_LEVEL_MIN_SHARE = 0.01
COUNTER_LIMIT = 3


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


def _iso_ms(value):
    ms = _int(value)
    if not ms:
        return None
    return datetime.fromtimestamp(ms / 1000.0, tz=timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


# ----------------------------------------------------------------------------
# 호출
# ----------------------------------------------------------------------------

def comps_stats_url(rank, server=None):
    # permit_filter_adjustment=false: 표본이 적을 때 서버가 랭크 필터를 몰래 넓히지 못하게. rank 가 비면 전 랭크.
    url = COMPS_BASE + "comps_stats?queue=1100&patch=current&days=%d" % DAYS
    if rank:
        url += "&rank=" + rank
    url += "&permit_filter_adjustment=false"
    if server:
        url += "&server=" + server
    return url


def fetch_scope(fetch_json, rank, server, error_cls):
    """
    comps_stats 한 스코프. 첫 행 {cluster:'', places:[총 보드]}, 나머지는
    places 9원소(앞 8 = 1~8등 보드 수, 9번째 = count).
    filter_adjustment 는 서버가 표본이 적다고 필터를 넓혔을 때 오는 블록이다. 없으면 None 으로 남긴다(검증이 본다).
    """
    blob = fetch_json(comps_stats_url(rank, server), timeout=90)
    boards, clusters = None, {}
    for row in blob.get("results") or []:
        cid = str(row.get("cluster") or "").strip()
        places = row.get("places") or []
        if not cid:
            boards = _int(places[0]) if places else None
            continue
        if len(places) < 9:
            continue
        values = [_int(p, 0) for p in places[:8]]
        count = _int(row.get("count"), _int(places[8], 0))
        clusters[cid] = {"places": values, "count": count}
    if not clusters:
        raise error_cls("comps_stats(%s%s): HTTP 200 이지만 클러스터 행이 없다"
                        % (rank, "," + server if server else ""))
    return {
        "boards": boards,
        "updatedAt": _iso_ms(blob.get("updated")),
        "clusterSet": blob.get("cluster_id"),
        "clusters": clusters,
        "ranks": rank or "",
        "filterAdjustment": blob.get("filter_adjustment"),
    }


def fetch_buckets(fetch_json, error_cls, warn=None):
    """
    앱 구간 5개(BUCKET_RANKS)의 comps_stats. 전 지역(server 없음). 반환 ({구간: fetch_scope 결과}, 실패한 구간들).
    '전체'는 10개 랭크 목록이 거부되면 rank 를 빼고 한 번 더 받는다(그때 ranks 는 빈 문자열).
    """
    out, failed = {}, []
    for bucket, rank in BUCKET_RANKS:
        try:
            out[bucket] = fetch_scope(fetch_json, rank, None, error_cls)
        except error_cls as exc:
            if bucket == "all":
                try:
                    out[bucket] = fetch_scope(fetch_json, None, None, error_cls)
                    if warn:
                        warn("metatft 전체 구간: 10개 랭크 목록이 거부돼 rank 없이 받았다(%s)" % exc)
                    continue
                except error_cls as retry:
                    exc = retry
            failed.append(bucket)
            if warn:
                warn("metatft %s 구간 통계 실패: %s" % (bucket, exc))
    return out, failed


def meta_grade(avg):
    """사이트 조합 등급. avg 는 반올림 전 평균 등수."""
    for grade, cut in META_GRADE_CUTS:
        if avg < cut:
            return grade
    return "D"


def trend_change(trends):
    """
    사이트의 '평균 등수 변화'(comps_data trends): 마지막 날(표본이 앞날의 1/4 미만인 반쪽 날이면 그 앞날)과
    3일 전 날의 평균 등수·픽률 차이. 픽률은 보드 비율 그대로다(사이트는 로비당 인원으로 보여 주려고 ×8 한다).
    부호는 lol.qq avg_rank_diff·use_rate_diff 와 같다(나중 − 먼저, 평균 등수가 음수면 좋아진 것).
    rank 필터와 무관한 comps_data 값이라 구간마다 같다. 날이 셋 미만이면 (None, None).
    """
    rows = [t for t in trends or [] if isinstance(t, dict) and _float(t.get("avg")) is not None]
    count = len(rows)
    if count <= 2:
        return None, None
    last = rows[-1]
    before = rows[-2]
    if ((_float(before.get("count"), 0) > _float(last.get("count"), 0) * 4)
            and before.get("patch") == last.get("patch")
            and before.get("b_patch_version") == last.get("b_patch_version")):
        last = before
    base = rows[count - min(count, 4)]
    avg_diff = _float(last.get("avg")) - _float(base.get("avg"))
    pick_last, pick_base = _float(last.get("pick")), _float(base.get("pick"))
    pick_diff = pick_last - pick_base if pick_last is not None and pick_base is not None else None
    return avg_diff, pick_diff


def bucket_stat(row, boards, change, min_sample, trend_for):
    """
    comps_stats 한 행 -> 구간 수치(lol.qq stats 와 같은 모양). avg 는 소수 넷째 자리로 싣고 등급은 반올림 전 값으로 매긴다.
    표본이 min_sample 미만이거나 사이트 기본 최소 픽률(로비당 0.01명) 미만이면 grade 는 None(수치는 남긴다).
    change: trend_change 결과(avgDiff, pickDiff). trend_for: lol.qq 와 같은 추세 규칙.
    """
    places = row["places"]
    count = sum(places)
    if count <= 0:
        return None
    avg = sum((i + 1) * p for i, p in enumerate(places)) / float(count)
    pick = count / float(boards) if boards else None
    graded = count >= min_sample and pick is not None and pick * LOBBY_SIZE >= META_MIN_PLAYRATE
    avg_diff, pick_diff = change or (None, None)
    return {
        "n": count,
        "avg": round(avg, 4),
        "adjAvg": round(avg, 4),
        "top4": round(sum(places[:4]) / float(count), 3),
        "win": round(places[0] / float(count), 3),
        "pick": round(pick, 4) if pick is not None else None,
        "avgDiff": round(avg_diff, 2) if avg_diff is not None else None,
        "pickDiff": round(pick_diff, 4) if pick_diff is not None else None,
        "grade": meta_grade(avg) if graded else None,
        "trend": trend_for(avg_diff, pick_diff),
    }


def cluster_bucket_stats(cid, buckets, change, trend_for):
    """클러스터 하나의 구간별 수치 {구간: bucket_stat}. 그 구간에 행이 없으면 뺀다."""
    out = {}
    for bucket, _ in BUCKET_RANKS:
        data = buckets.get(bucket)
        row = ((data or {}).get("clusters") or {}).get(str(cid))
        if not row:
            continue
        stat = bucket_stat(row, data.get("boards"), change, META_MIN_SAMPLE[bucket], trend_for)
        if stat:
            out[bucket] = stat
    return out


def meta_grade_cuts(bucket, data):
    """buckets[b].metaGradeCuts: 고정 컷·표본 문턱·최소 픽률과 그 구간 요청(rank)·전체 보드·filter_adjustment 기록."""
    cuts = dict(META_GRADE_CUTS)
    cuts.update({
        "method": "absolute",
        "minSample": META_MIN_SAMPLE[bucket],
        "minPlayrate": META_MIN_PLAYRATE,
        "ranks": (data or {}).get("ranks", ""),
        "boards": (data or {}).get("boards"),
        "filterAdjustment": (data or {}).get("filterAdjustment"),
        "updatedAt": (data or {}).get("updatedAt"),
    })
    return cuts


def fetch_comps_data(fetch_json, error_cls):
    """파라미터 없이 1회. 클러스터별 운영 방식(levelling)·난이도·전체 표본."""
    blob = fetch_json(COMPS_BASE + "comps_data", timeout=90)
    details = ((blob.get("results") or {}).get("data") or {}).get("cluster_details") or {}
    if not details:
        raise error_cls("comps_data: cluster_details 가 비었다")
    out = {}
    for cid, row in details.items():
        out[str(cid)] = {
            "levelling": row.get("levelling"),
            "difficulty": _float(row.get("difficulty")),
            "overall": row.get("overall") or {},
            # 유닛별 1순위 3아이템 빌드(comp builds). metatft 전용 덱의 보드 아이템·캐리 순위에 쓴다.
            "builds": row.get("builds") or [],
            # 일별 표본·평균 등수·픽률. 사이트는 여기서 '평균 등수 변화'를 계산한다(trend_change).
            "trends": row.get("trends") or [],
        }
    return out


def comp_details_url(cluster, cluster_set):
    # 확인된 형식(2026-09-15). comp_details 는 필터를 무시하지만 사이트와 같은 값을 보낸다.
    return (COMPS_BASE + "comp_details?comp=%s&cluster_id=%s&queue=1100&patch=current&days=%d"
            "&rank=%s&permit_filter_adjustment=true" % (cluster, cluster_set, DAYS, RANK_PLAT))


def fetch_comp_details(fetch_json, cluster, cluster_set, error_cls):
    blob = fetch_json(comp_details_url(cluster, cluster_set), timeout=120)
    results = blob.get("results") or {}
    if not results or str(results.get("cluster")) != str(cluster):
        raise error_cls("comp_details %s: 빈 응답이거나 다른 클러스터" % cluster)
    results = dict(results)
    results["_filterAdjustment"] = blob.get("filter_adjustment") or {}
    return results


def fetch_patch(fetch_json):
    """글로벌 패치 이름(예 '18.2'). 라벨용이라 실패해도 None."""
    blob = fetch_json(STAT_PATCH, timeout=30)
    patch = str(blob.get("patch") or "").strip()
    return patch or None


# ----------------------------------------------------------------------------
# 가공
# ----------------------------------------------------------------------------

def summarize(places, count):
    """places[8] -> 평균 등수·TOP4·승률. n 은 원본 count(9번째 원소)."""
    total = sum(places)
    if total <= 0:
        return None
    avg = sum((i + 1) * p for i, p in enumerate(places)) / float(total)
    return {
        "n": count,
        "avg": round(avg, 2),
        "top4": round(sum(places[:4]) / float(total), 3),
        "win": round(places[0] / float(total), 3),
        "places": list(places),
    }


def scope_mean(scope):
    """스코프 전체 클러스터의 보드 가중 평균 등수. 정상이면 4.5 근처(검증 게이트용)."""
    boards = weighted = 0
    for row in (scope or {}).get("clusters", {}).values():
        places = row["places"]
        boards += sum(places)
        weighted += sum((i + 1) * p for i, p in enumerate(places))
    return round(weighted / float(boards), 4) if boards else None


def levelling_ko(raw):
    text = str(raw or "").strip()
    if not text:
        return None
    found = LEVELLING_REROLL.match(text)
    if found:
        return "%s레벨 리롤" % found.group(1)
    return LEVELLING_KO.get(text, text)


def difficulty_ko(value):
    if value is None:
        return None
    if value < DIFFICULTY_EASY:
        return "쉬움"
    if value > DIFFICULTY_HARD:
        return "어려움"
    return "보통"


def final_levels(results):
    rows = []
    for row in results.get("final_levels") or []:
        level, count = _int(row.get("level")), _int(row.get("count"), 0)
        if level is not None and count > 0:
            rows.append((level, count))
    total = float(sum(c for _, c in rows))
    if not total:
        return []
    out = [{"level": level, "share": round(count / total, 3)}
           for level, count in sorted(rows) if count / total >= FINAL_LEVEL_MIN_SHARE]
    return out


def counters(results, own_cluster, deck_for_cluster, names=None):
    """
    place_change 가 큰(만나면 등수가 더 밀리는) 상대 상위 3. 자기 자신 행은 뺀다.
    against 는 클러스터 id 라 우리 덱 id(metatft 조합 덱)로 옮겨 적는다. 우리 목록에 없는 클러스터는 뺀다
    (2026-09-19 계약: 목록의 덱으로만 옮겨 가게). names(클러스터 id -> metatft name_string)는 참고로 싣는다.
    """
    names = names or {}
    rows = []
    for row in results.get("counters") or []:
        against = str(row.get("against") or "")
        change = _float(row.get("place_change"))
        if not against or against == str(own_cluster) or change is None or change <= 0:
            continue
        if against not in deck_for_cluster:
            continue
        rows.append((change, against))
    rows.sort(reverse=True)
    out = []
    for change, against in rows[:COUNTER_LIMIT]:
        entry = {"cluster": _int(against), "deck": deck_for_cluster.get(against), "placeChange": round(change, 2)}
        if names.get(against):
            entry["name"] = names[against]
        out.append(entry)
    return out


def _unit_list(text):
    return [u.strip() for u in str(text or "").split("&") if u.strip()]


def _traits(text, trait_count):
    """'DA_18_Adaptor_1&…' 의 _N 은 인원수가 아니라 단계 순번 -> (id, 인원수)."""
    out = []
    for token in _unit_list(text):
        base, _, step = token.rpartition("_")
        step = _int(step)
        if not base or step is None:
            continue
        count = trait_count(base, step)
        if count:
            out.append({"id": base, "count": count})
    out.sort(key=lambda t: (-t["count"], t["id"]))
    return out


def _rolls_per_game(entry):
    entry = entry or {}
    rerolls, matches = _float(entry.get("rerolls")), _float(entry.get("matches"))
    if rerolls is None or rerolls <= 0 or not matches:
        return None
    return round(rerolls / matches, 1)


def buildup_global(results, cluster, sort_units, trait_count):
    """
    comp_details 한 응답 -> buildup.global (설계 §13.2).
    sort_units(ids) 는 catalog 에 등록하면서 코스트→이름순으로 정렬해 돌려준다.
    trait_count(id, step) 는 단계 순번을 인원수로 바꾼다(없으면 None).
    """
    early = results.get("early_options") or {}
    finals = results.get("options") or {}

    reach, top_count = {}, 0
    for row in results.get("levels") or []:
        level = _int(row.get("level"))
        if level is None:
            continue
        count = _int(row.get("count"), 0)
        top_count = max(top_count, count)
        stage, rnd = str(row.get("stage") or "").strip(), str(row.get("round") or "").strip()
        reach[level] = {"round": "%s-%s" % (stage, rnd) if stage and rnd else None, "count": count}

    rerolls = results.get("rerolls") or {}
    levels = []
    for level in BUILDUP_LEVELS:
        candidates = []
        if level <= 7:
            for row in sorted(early.get(str(level)) or [], key=lambda r: -(_float(r.get("count"), 0))):
                candidates.append((_unit_list(row.get("unit_list")), row.get("count"), row.get("avg"), None))
        if level >= 7:
            for row in sorted(finals.get(str(level)) or [], key=lambda r: -(_float(r.get("score"), 0))):
                candidates.append((_unit_list(row.get("units_list")), row.get("count"), row.get("avg"),
                                   row.get("traits_list")))

        picked, seen = [], set()
        for units, n, avg, traits in candidates:
            n = _int(n, 0)
            if not units or n < BUILDUP_MIN_N:
                continue
            key = frozenset(units)
            if key in seen:
                continue
            seen.add(key)
            option = {"units": sort_units(units), "n": n}
            avg = _float(avg)
            option["avg"] = round(avg, 2) if avg is not None else None
            if traits:
                parsed = _traits(traits, trait_count)
                if parsed:
                    option["traits"] = parsed
            picked.append(option)
            if len(picked) >= BUILDUP_OPTIONS:
                break

        info = reach.get(level)
        rolls = _rolls_per_game(rerolls.get(str(level)))
        reach_round = info["round"] if info else None
        if not picked and reach_round is None and rolls is None:
            continue
        levels.append({
            "level": level,
            "reachRound": reach_round,
            "reachShare": round(info["count"] / float(top_count), 2) if info and top_count else None,
            "rollsPerGame": rolls,
            "options": picked,
        })

    if not levels:
        return None
    rolling = [row for row in levels if row["rollsPerGame"] is not None]
    roll_level = max(rolling, key=lambda row: row["rollsPerGame"])["level"] if rolling else None
    return {"scope": "glob_plat", "cluster": _int(cluster), "rollLevel": roll_level, "levels": levels}


# ----------------------------------------------------------------------------
# metatft 조합 덱(kind=meta) 보드: 합쳐진 lol.qq 그룹이 없는 클러스터는 대표 유닛·빌드로 보드를 만든다
# ----------------------------------------------------------------------------

GLOBAL_SCOPE = "glob_plat"
# 대표 유닛(units_string)은 클러스터에 따라 8~21명이라 보드로 보일 만큼만 남긴다.
GLOBAL_BOARD_MAX = 10


def split_ids(text):
    """cluster_info 의 'DA_18_Varus, DA_KogMaw18_AD' -> id 목록(순서 유지)."""
    return [part.strip() for part in str(text or "").split(",") if part.strip()]


def cluster_traits(text, trait_count):
    """traits_string('DA_18_Lunar_2, DA_Primal18_1') -> [{"id", "count"}]. _N 은 단계 순번이다(_traits 와 같다)."""
    return _traits("&".join(split_ids(text)), trait_count)


def unit_builds(builds):
    """
    comps_data builds -> [(유닛, 1순위 아이템 id 들, 표본)] 표본 큰 순.
    유닛마다 목록에서 처음 나온 빌드가 1순위다(score 순으로 온다). 표본은 그 유닛이 아이템 3개를 든
    보드 수(unit_numitems_count, 없으면 그 빌드의 count).
    """
    best = {}
    for row in builds or []:
        unit = str(row.get("unit") or "").strip()
        items = [str(i).strip() for i in row.get("buildName") or [] if str(i).strip()]
        if not unit or not items or unit in best:
            continue
        best[unit] = (items[:3], _int(row.get("unit_numitems_count"), 0) or _int(row.get("count"), 0))
    ordered = sorted(best.items(), key=lambda pair: -pair[1][1])
    return [(unit, items, sample) for unit, (items, sample) in ordered]


def unit_usage(results):
    """
    comp_details unit_stats -> {유닛: {"count": 채용 보드 수, "star": 최빈 성급, "key": keyUnits 행}}.
    keyUnits 행은 lol.qq 핵심 유닛과 같은 모양(1~3성 비율·평균 아이템 수·평균 등수)이다.
    """
    out = {}
    for row in (results or {}).get("unit_stats") or []:
        unit = str(row.get("unit") or "").strip()
        if not unit:
            continue
        tiers = {}
        for tier in row.get("tiers") or []:
            level = _int(tier.get("tier"))
            if level is not None:
                tiers[level] = _float(tier.get("pcnt"), 0.0)
        shares = [(_int(n.get("num_items"), 0), _float(n.get("pcnt"), 0.0)) for n in row.get("num_items") or []]
        weight = sum(share for _, share in shares)
        avg = _float(row.get("avg"))
        out[unit] = {
            "count": _int(row.get("count"), 0),
            "star": max(tiers, key=lambda level: tiers[level]) if tiers else None,
            "key": {
                "id": unit,
                "star1": round(tiers.get(1, 0.0), 3) if tiers else None,
                "star2": round(tiers.get(2, 0.0), 3) if tiers else None,
                "star3": round(tiers.get(3, 0.0), 3) if tiers else None,
                "items": round(sum(k * share for k, share in shares) / weight, 2) if weight else None,
                "avg": round(avg, 2) if avg is not None else None,
            },
        }
    return out


def common_final_level(results):
    """comp_details final_levels 에서 가장 많이 끝난 레벨. 없으면 None."""
    rows = [(_int(row.get("count"), 0), _int(row.get("level"))) for row in (results or {}).get("final_levels") or []]
    rows = [(count, level) for count, level in rows if level is not None and count > 0]
    return max(rows)[1] if rows else None
