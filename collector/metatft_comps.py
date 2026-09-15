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
    # permit_filter_adjustment=false: 표본이 적을 때 서버가 랭크 필터를 몰래 넓히지 못하게.
    url = (COMPS_BASE + "comps_stats?queue=1100&patch=current&days=%d&rank=%s"
           "&permit_filter_adjustment=false" % (DAYS, rank))
    if server:
        url += "&server=" + server
    return url


def fetch_scope(fetch_json, rank, server, error_cls):
    """
    comps_stats 한 스코프. 첫 행 {cluster:'', places:[총 보드]}, 나머지는
    places 9원소(앞 8 = 1~8등 보드 수, 9번째 = count).
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
    }


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
    against 는 클러스터 id 라 우리 덱 id 로도 옮겨 적는다(매칭된 경우만).
    names(클러스터 id -> metatft name_string)가 있으면 이름도 싣는다. 우리 목록에 대응 덱이 없는
    상대(절반 가까이)는 앱이 이 이름을 한글로 풀어 보여 주고, 없으면 클러스터 숫자만 남는다.
    """
    names = names or {}
    rows = []
    for row in results.get("counters") or []:
        against = str(row.get("against") or "")
        change = _float(row.get("place_change"))
        if not against or against == str(own_cluster) or change is None or change <= 0:
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
