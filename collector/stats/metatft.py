# -*- coding: utf-8 -*-
"""
metatft 호출과 사전 설명문 해석.

통계(tft-stat-api)는 rank/days/server/patch 필터가 실제로 적용되는 엔드포인트만 쓴다(설계 §3.1).
항상 permit_filter_adjustment=false — 표본이 작을 때 서버가 조용히 필터를 넓히면
"KR 마스터+"라는 라벨이 거짓이 된다.
잘못된 키(TFT18_Aphelios 등)는 HTTP 200 빈 응답이라 비었는지 반드시 검사한다.
"""

import re
import urllib.parse

from . import clean_text, fmt_number, stat_words
from .net import SourceError

BASE = "https://api-hc.metatft.com/"
SPECTATE_TOP = "https://api.metatft.com/tft-spectate/top_players?region=kr"
LOOKUP_TMPL = "https://data.metatft.com/lookups/{set_name}_latest_ko_kr.json"

PLAT = "CHALLENGER,DIAMOND,EMERALD,GRANDMASTER,MASTER,PLATINUM"
MASTER = "CHALLENGER,GRANDMASTER,MASTER"
DAYS = 3

# (키, 라벨, 랭크 집합, 서버)
SCOPES = (
    ("glob_plat", "글로벌 플래티넘+", PLAT, None),
    ("kr_plat", "KR 플래티넘+", PLAT, "KR"),
    ("kr_master", "KR 마스터+", MASTER, "KR"),
)
DETAIL_SCOPE = "glob_plat"


def filter_query(scope):
    for key, _label, rank, server in SCOPES:
        if key == scope:
            query = ("queue=1100&patch=current&days=%d&permit_filter_adjustment=false&rank=%s"
                     % (DAYS, rank))
            if server:
                query += "&server=" + server
            return query
    raise KeyError(scope)


def _quote(value):
    return urllib.parse.quote(value, safe="")


def _check_adjustment(blob, what):
    adjustment = blob.get("filter_adjustment") or {}
    if adjustment.get("override_applied"):
        raise SourceError("%s: 서버가 필터를 넓혔다(override_applied)" % what)


# ----------------------------------------------------------------------------
# 호출
# ----------------------------------------------------------------------------

def fetch_patch(client):
    blob = client.get_json(BASE + "tft-stat-api/patch")
    if not blob.get("patch"):
        raise SourceError("tft-stat-api/patch: patch가 없다")
    return blob


def fetch_table(client, endpoint, scope, key):
    """
    units / traits / items_matches 한 스코프. TFT18_* 같은 비 DA 키는 저표본 중복 행이라 뺀다.
    돌려주는 값: {"rows": [...], "games": int, "updated": ms}
    """
    blob = client.get_json(BASE + "tft-stat-api/%s?%s" % (endpoint, filter_query(scope)))
    _check_adjustment(blob, "%s %s" % (endpoint, scope))
    games = ((blob.get("games") or [{}])[0] or {}).get("count")
    rows = []
    for row in blob.get("results") or []:
        name = row.get(key) or ""
        places = row.get("places")
        if not name.startswith("DA_") or not isinstance(places, list) or len(places) != 8:
            continue
        rows.append({"id": name, "places": [int(p or 0) for p in places]})
    if not rows or not games:
        raise SourceError("%s %s: 빈 응답" % (endpoint, scope))
    return {"rows": rows, "games": int(games), "updated": blob.get("updated")}


def fetch_unit_items(client, unit):
    url = (BASE + "tft-stat-api/unit_detail_items?%s&unit=%s&artifact_count=0"
           % (filter_query(DETAIL_SCOPE), _quote(unit)))
    blob = client.get_json(url)
    if not blob.get("items") and not blob.get("builds"):
        raise SourceError("unit_detail_items %s: 빈 응답" % unit)
    return blob


def fetch_item_detail(client, item):
    url = BASE + "tft-stat-api/item_detail?%s&itemName=%s" % (filter_query(DETAIL_SCOPE), _quote(item))
    blob = client.get_json(url)
    if not blob.get("units"):
        raise SourceError("item_detail %s: 빈 응답" % item)
    return blob


def fetch_item_stages(client, item):
    url = BASE + "tft-stat-api/item_stage_detail?%s&item=%s" % (filter_query(DETAIL_SCOPE), _quote(item))
    blob = client.get_json(url)
    if not blob.get("stage"):
        raise SourceError("item_stage_detail %s: 빈 응답" % item)
    return blob


def fetch_augment_tiers(client):
    """에디터 1인 티어(통계 아님). 작성자·갱신 시각을 함께 싣는다."""
    blob = client.get_json(BASE + "tft-stat-api/augments_tiers")
    content = blob.get("content") or {}
    inner = content.get("content") or {}
    tiers, order = {}, []
    for tier in inner.get("tierList") or []:
        label = str(tier.get("label") or "").strip()
        if not label:
            continue
        order.append(label)
        for entry in tier.get("content") or []:
            if entry.get("id"):
                tiers.setdefault(entry["id"], label)
    if not tiers:
        raise SourceError("augments_tiers: tierList가 비었다")
    tags = inner.get("tags") or blob.get("tags") or {}
    return {
        "tiers": tiers,
        "order": order,
        "tags": tags if isinstance(tags, dict) else {},
        "author": (content.get("author") or {}).get("gameName"),
        "updatedAt": content.get("updated_at"),
    }


def fetch_comp_augment_tiers(client):
    blob = client.get_json(BASE + "tft-comps-api/comp_augment_tiers")
    results = blob.get("results")
    if not isinstance(results, dict) or not results:
        raise SourceError("comp_augment_tiers: results가 비었다")
    return results


def fetch_clusters(client):
    """클러스터 id → 유닛 집합. id는 재생성마다 바뀌므로 저장하지 않고 매번 받는다."""
    blob = client.get_json(BASE + "tft-comps-api/latest_cluster_info", timeout=90)
    details = ((blob.get("cluster_info") or {}).get("cluster_details")) or {}
    out = {}
    for cluster in details.get("clusters") or []:
        try:
            cid = int(cluster.get("Cluster"))
        except (TypeError, ValueError):
            continue
        units = {u.strip() for u in (cluster.get("units_string") or "").split(",") if u.strip()}
        if units:
            out[cid] = units
    if not out:
        raise SourceError("latest_cluster_info: 클러스터가 없다")
    return out


def fetch_lookup(client, set_name):
    blob = client.get_json(LOOKUP_TMPL.format(set_name=set_name), timeout=120)
    if not blob.get("units") and not blob.get("augments"):
        raise SourceError("%s 한국어 사전이 비었다" % set_name)
    return blob


def fetch_top_players(client):
    blob = client.get_json(SPECTATE_TOP)
    if not isinstance(blob, dict) or "data" not in blob:
        raise SourceError("top_players: 형식이 다르다")
    return blob


# ----------------------------------------------------------------------------
# 사전 설명문(<TFTCurveTable/>, <TFTAttribute/>) 해석
# ----------------------------------------------------------------------------

_CURVE = re.compile(r"<TFTCurveTable\b([^>]*?)/?>", re.I)
_ATTRIBUTE = re.compile(r"<TFTAttribute\b([^>]*?)/?>", re.I)
_ARGS = re.compile(r'([A-Za-z]+)\s*=\s*"([^"]*)"')
_IMG_RUN = re.compile(r"(?:<img\b[^>]*>\s*)+", re.I)
_IMG_ID = re.compile(r'id\s*=\s*"([^"]+)"', re.I)
_BRACE = re.compile(r"\{[A-Za-z][A-Za-z0-9_.]*\}")


def _curve_at(points, level):
    """[[단계, 값], …]에서 level 이하 마지막 값(계단식). 목록 앞보다 작으면 첫 값."""
    chosen, first = None, None
    for pair in points or []:
        if not isinstance(pair, (list, tuple)) or len(pair) < 2:
            continue
        step, value = pair[0], pair[1]
        if first is None:
            first = value
        try:
            if float(step) <= level:
                chosen = value
        except (TypeError, ValueError):
            continue
    return chosen if chosen is not None else first


def _format(value, kind, precision):
    kind = (kind or "").lower()
    if kind in ("percent", "p"):
        return fmt_number(value * 100, precision) + "%"
    if kind == "percentminusone":
        return fmt_number((value - 1) * 100, precision) + "%"
    if kind == "invertedpercent":
        return fmt_number((1 - value) * 100, precision) + "%"
    return fmt_number(value, precision)


def _join(values, kind, precision):
    texts = [_format(v, kind, precision) for v in values]
    if len(set(texts)) == 1:
        return texts[0]
    return "/".join(texts)


def render(text, curve_values=None, attribute_values=None, levels=(1,), icon_words=False):
    """
    metatft 사전 설명문의 자리표시자를 실제 수치로 바꾼다.
    - column 속성이 있으면 그 단계 값(특성 단계별 효과)
    - 없으면 levels의 값들(챔피언은 1~3성 → '455/685/3500', 같으면 하나)
    - format: percent / percentMinusOne / invertedPercent
    - icon_words: 수치의 icon 속성과 <img id="Icon.AD"/>를 능력치 이름으로(특성 효과용).
      아이템 설명은 '공격력 <img/> +10%'처럼 이름이 이미 있어 끄고 쓴다.
    돌려주는 값: (정리된 문자열, 풀지 못한 자리표시자 수)
    """
    if not text:
        return "", 0
    curves = {str(k).lower(): v for k, v in (curve_values or {}).items()}
    attributes = {str(k).lower(): v for k, v in (attribute_values or {}).items()}
    missing = [0]

    def numbers(values):
        return all(isinstance(v, (int, float)) and not isinstance(v, bool) for v in values)

    def curve(match):
        args = {k.lower(): v for k, v in _ARGS.findall(match.group(1))}
        points = curves.get((args.get("row") or "").lower())
        if not points:
            missing[0] += 1
            return ""
        try:
            wanted = [int(args["column"])] if args.get("column") else list(levels)
        except ValueError:
            wanted = list(levels)
        values = [_curve_at(points, level) for level in wanted]
        if not values or not numbers(values):
            missing[0] += 1
            return ""
        value = _join(values, args.get("format"), args.get("precision"))
        words = stat_words(args["icon"].split(",")) if icon_words and args.get("icon") else ""
        return value + " " + words if words else value

    def attribute(match):
        args = {k.lower(): v for k, v in _ARGS.findall(match.group(1))}
        series = attributes.get((args.get("attributeid") or "").lower())
        if not isinstance(series, list) or not series:
            missing[0] += 1
            return ""
        values = [series[min(level, len(series)) - 1] for level in levels]
        if not numbers(values):
            missing[0] += 1
            return ""
        return _join(values, args.get("format"), args.get("precision"))

    def images(match):
        if not icon_words:
            return ""
        words = stat_words(_IMG_ID.findall(match.group(0)))
        return " %s " % words if words else " "

    out = _CURVE.sub(curve, str(text))
    out = _ATTRIBUTE.sub(attribute, out)
    out = _IMG_RUN.sub(images, out)
    out = _BRACE.sub("", out)
    return clean_text(out), missing[0]
