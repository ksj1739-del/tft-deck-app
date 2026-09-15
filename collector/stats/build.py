# -*- coding: utf-8 -*-
"""
원본 응답을 앱 계약(설계 §5.3~§5.7)의 JSON 네 개로 조립한다.

앱은 계산하지 않고 조회만 한다. 평균·TOP4·승률·픽률·Δ·등급·라벨은 전부 여기서 넣는다.
- 숫자는 JSON 숫자. 비율은 소수 3자리, 평균 등수·Δ는 소수 2자리, 카운트는 정수.
- 원본 하나가 실패하면 같은 패치의 직전 결과를 그 부분만 이어 쓰고(stale),
  직전 결과도 없으면 비워 둔다(missing). 앱이 하루아침에 빈 도감을 받지 않게 하기 위해서다.
- 이 모듈은 네트워크를 모른다. fetch_stats.py가 채운 Inputs만 읽는다.
"""

import hashlib
import json
import math
import os
import re
from collections import defaultdict
from datetime import datetime, timezone

from . import cdragon, lolqq, metatft

SCHEMA_VERSION = 1
FILE_NAMES = ("champions", "traits", "items", "augments")
# 증강 도감의 중국 통계 요약이 기준으로 삼는 덱 구간. meta.cnStats 라벨과 같은 구간 행만 합산한다.
CN_STATS_BUCKET = "goldem"

KIND_ORDER = {"completed": 0, "emblem": 1, "artifact": 2, "radiant": 3, "support": 4,
              "component": 5, "other": 6}
TYPE_ORDER = {"origin": 0, "class": 1}
RARITY_BY_HEX_TYPE = {"1": "silver", "2": "gold", "3": "prismatic"}

EMBLEM = re.compile(r"^DA_\d+_Emblem")
ARTIFACT = re.compile(r"^DA_(Item_)?Artifact_")
UNIT_COUNT_PREFIX = re.compile(r"^\(\d+\)\s*")
COPY_SUFFIX = re.compile(r"-\d+$")


# ----------------------------------------------------------------------------
# 숫자
# ----------------------------------------------------------------------------

def _round(value, digits):
    if value is None:
        return None
    out = round(float(value), digits)
    return 0.0 if out == 0 else out


def r2(value):
    return _round(value, 2)


def r3(value):
    return _round(value, 3)


def as_int(value):
    try:
        return int(round(float(value)))
    except (TypeError, ValueError):
        return None


def half_up(value):
    return int(math.floor(float(value) + 0.5 + 1e-9))


def iso_now(now):
    return now.strftime("%Y-%m-%dT%H:%M:%SZ")


def iso_from_ms(ms):
    try:
        return datetime.fromtimestamp(int(ms) / 1000.0, tz=timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    except (TypeError, ValueError, OverflowError, OSError):
        return None


def iso_trim(text):
    """'2026-09-15T11:11:58.602Z' → '2026-09-15T11:11:58Z'."""
    if not text:
        return None
    try:
        parsed = datetime.fromisoformat(str(text).replace("Z", "+00:00"))
    except ValueError:
        return str(text)
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return parsed.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def clean_places(places):
    if not isinstance(places, list) or len(places) < 8:
        return None
    return [as_int(p) or 0 for p in places[:8]]


def summarize(places):
    """(n, 평균 등수, TOP4 비율, 1등 비율). places[i]는 i+1등 횟수."""
    n = sum(places)
    if n <= 0:
        return None
    avg = sum((i + 1) * p for i, p in enumerate(places)) / float(n)
    return n, avg, sum(places[:4]) / float(n), places[0] / float(n)


def grade_linear(value, cuts):
    if value is None:
        return None
    for label in ("S", "A", "B", "C"):
        if value > cuts[label]:
            return label
    return "D"


# ----------------------------------------------------------------------------
# 입력 그릇
# ----------------------------------------------------------------------------

class DecksLink:
    """
    data/decks.json(WP-1 산출물)을 읽기만 한다. 덱 연결 필드(decks, deckStats, recommendedBy)는
    schemaVersion 2 이상일 때만 채운다 — v1에는 byId·augmentStats·global이 없다.
    세트 이름 같은 version 정보는 v1에도 있어 그대로 쓴다.
    """

    def __init__(self):
        self.ok = False
        self.version = {}
        self.by_id = {"champion": {}, "item": {}, "trait": {}, "augment": {}}
        self.augment_stats = defaultdict(list)
        self.editorial = defaultdict(set)
        self.cluster_decks = defaultdict(set)
        self.deck_units = {}
        self.detail_date = None

    @classmethod
    def load(cls, path):
        link = cls()
        if not path or not os.path.exists(path):
            return link
        try:
            with open(path, encoding="utf-8") as fp:
                blob = json.load(fp)
        except (OSError, ValueError):
            return link
        link.version = blob.get("version") or {}
        if (as_int(link.version.get("schemaVersion")) or 1) < 2:
            return link

        link.ok = True
        by_id = (blob.get("index") or {}).get("byId") or {}
        for axis in link.by_id:
            table = by_id.get(axis) or {}
            link.by_id[axis] = {str(k): sorted({str(x) for x in v if x})
                                for k, v in table.items() if isinstance(v, list)}
        link.detail_date = ((blob.get("buckets") or {}).get(CN_STATS_BUCKET) or {}).get("detailDate")

        for deck in blob.get("decks") or []:
            if not isinstance(deck, dict) or not deck.get("id"):
                continue
            did = str(deck["id"])
            name = deck.get("name") or did
            # 증강 성적은 덱마다 상세를 받은 구간(detailBucket)의 값이다. 대부분 골드~에메랄드지만
            # 그 구간에 없는 덱은 다이아+ 등에서 받는다. 행마다 구간을 적어 라벨이 섞이지 않게 한다.
            bucket = str(deck.get("detailBucket") or CN_STATS_BUCKET)
            for stat in deck.get("augmentStats") or []:
                n = stat.get("n") if isinstance(stat, dict) else None
                if not stat.get("id") or not isinstance(n, (int, float)):
                    continue
                link.augment_stats[str(stat["id"])].append({
                    "deck": did,
                    "deckName": name,
                    "bucket": bucket,
                    "n": int(n),
                    "avg": stat.get("avg"),
                    "rank": stat.get("rank"),
                    "stage": stat.get("stage") or [],
                    "stageLowSample": stat.get("stageLowSample") or [],
                })
            # 한 그룹에 편집 덱이 여럿 붙으면 두 번째부터는 moreEditorials 에 있다. 그 작가 추천도 센다.
            editorials = [deck.get("editorial")] + list(deck.get("moreEditorials") or [])
            for editorial in editorials:
                if not isinstance(editorial, dict):
                    continue
                recommended = (editorial.get("augments") or {}).get("recommended") or []
                for entry in recommended:
                    aid = entry.get("id") if isinstance(entry, dict) else entry
                    if aid:
                        link.editorial[str(aid)].add(did)
            cluster = as_int((deck.get("global") or {}).get("cluster"))
            if cluster is not None:
                link.cluster_decks[cluster].add(did)
            units = {u.get("id") for u in deck.get("units") or []
                     if isinstance(u, dict) and u.get("id") and u.get("kind") != "pet"}
            if units:
                link.deck_units[did] = units
        return link


def load_overrides(path):
    """
    collector/overrides_ko.json(WP-1이 관리하는 수동 한글 이름). 형식이 확정되기 전이라
    {id: 이름}, {id: {"name": 이름}}, {분류: {id: …}} 모두 받아 id → 이름으로 편다.
    """
    flat = {}
    if not path or not os.path.exists(path):
        return flat
    try:
        with open(path, encoding="utf-8") as fp:
            blob = json.load(fp)
    except (OSError, ValueError):
        return flat

    def looks_like_id(key):
        return bool(re.match(r"^(DA_|TFT|QQ_)", key))

    def walk(node):
        if not isinstance(node, dict):
            return
        for key, value in node.items():
            key = str(key)
            if isinstance(value, str):
                if looks_like_id(key) and value.strip():
                    flat.setdefault(key, value.strip())
            elif isinstance(value, dict):
                name = value.get("name") or value.get("ko")
                if looks_like_id(key) and isinstance(name, str) and name.strip():
                    flat.setdefault(key, name.strip())
                else:
                    walk(value)

    walk(blob)
    return flat


class Inputs:
    """fetch_stats.py가 원본을 받아 채우는 그릇."""

    def __init__(self, constants, now):
        self.constants = constants
        self.now = now
        self.set_id = "s18"
        self.set_number = 18
        self.metatft_set = "TFTSet18"
        self.dic = None
        self.lookup = None
        self.patch = None
        self.mt_tables = {}
        self.unit_items = {}
        self.item_details = {}
        self.item_stages = {}
        self.detail_failed = {"unit_items": set(), "item_detail": set(), "item_stages": set()}
        self.augment_tiers = None
        self.comp_guides = None
        self.clusters = None
        self.qq_scopes = {}
        self.qq_equip = None
        self.qq_trend = None
        self.qq_static = {}
        self.qq_build = None
        self.qq_patch = None
        self.window = (None, None)
        self.decks = DecksLink()
        self.overrides = {}
        self.previous_blobs = {}
        self.snapshot = {}
        self.carried = set()
        self.report = {}

    def warn(self, text):
        print(text, flush=True)

    @property
    def patch_global(self):
        if self.patch:
            return "%s%s" % (self.patch.get("patch") or "", self.patch.get("b_patch_version") or "")
        return ((self.previous_blobs.get("champions") or {}).get("version") or {}).get("patchGlobal")

    def table(self, scope, kind):
        return (self.mt_tables.get(scope) or {}).get(kind)


class Previous:
    """같은 패치의 직전 결과. 원본 하나가 실패했을 때 그 부분만 이어 쓴다(패치가 다르면 쓰지 않는다)."""

    def __init__(self, blobs, patch_global):
        self.blobs = blobs or {}
        patch = ((self.blobs.get("champions") or {}).get("version") or {}).get("patchGlobal")
        self.valid = bool(patch) and patch == patch_global
        self.rows = {}
        for name in FILE_NAMES:
            rows = (self.blobs.get(name) or {}).get(name) or []
            self.rows[name] = {r.get("id"): r for r in rows if isinstance(r, dict) and r.get("id")}

    def row(self, name, api):
        return self.rows.get(name, {}).get(api) if self.valid else None

    def scopes(self, name):
        return ((self.blobs.get(name) or {}).get("scopes") or {}) if self.valid else {}

    def blob(self, name):
        return (self.blobs.get(name) or {}) if self.valid else {}


class Names:
    """
    이름: CDragon ko 정확 일치 → metatft ko 사전 → CDragon 접두사 정규화 → overrides_ko.json → id 원문.
    id 원문으로 떨어진 것은 untranslated에 모은다(신규 챔피언·증강 조기 발견용).
    """

    def __init__(self, inputs):
        self.dic = inputs.dic
        lookup = inputs.lookup or {}
        by_api = {u.get("apiName"): u for u in lookup.get("units") or [] if u.get("apiName")}
        self.units = {}
        for unit in by_api.values():
            for asset in unit.get("assetNames") or []:
                self.units.setdefault(asset, unit)
        for da, api in (lookup.get("unitAssetNames") or {}).items():
            if da not in self.units and api in by_api:
                self.units[da] = by_api[api]
        self.traits = {t["apiName"]: t for t in lookup.get("traits") or [] if t.get("apiName")}
        self.items = {i["apiName"]: i for i in lookup.get("items") or [] if i.get("apiName")}
        self.augments = {a["apiName"]: a for a in lookup.get("augments") or [] if a.get("apiName")}
        self.roles = lookup.get("roles") or {}
        self.overrides = inputs.overrides or {}
        self.untranslated = set()

    def _fallback(self, api):
        if api in self.overrides:
            return self.overrides[api]
        self.untranslated.add(api)
        return api

    def champion(self, api):
        ko = self.dic.champions.get(api)
        if ko and ko.get("name"):
            return ko["name"]
        unit = self.units.get(api)
        if unit and unit.get("name"):
            return unit["name"]
        return self._fallback(api)

    def champion_en(self, api):
        return self.dic.champion_en_name(api) or (self.units.get(api) or {}).get("en_name")

    def trait(self, api):
        ko = self.dic.traits.get(api)
        if ko and ko.get("name"):
            return ko["name"]
        lk = self.traits.get(api)
        if lk and lk.get("name"):
            return lk["name"]
        return self._fallback(api)

    def trait_en(self, api):
        return self.dic.trait_en_name(api) or (self.traits.get(api) or {}).get("en_name")

    def item(self, api, augment=False):
        ko = self.dic.item(api)
        if ko and ko.get("name"):
            return ko["name"]
        lk = (self.augments if augment else self.items).get(api)
        if lk and lk.get("name"):
            return lk["name"]
        norm = self.dic.normalized_item(api, augment)
        if norm and norm.get("name"):
            return norm["name"]
        return self._fallback(api)

    def item_en(self, api, augment=False):
        lk = (self.augments if augment else self.items).get(api) or {}
        return self.dic.item_en_name(api) or lk.get("en_name")


# ----------------------------------------------------------------------------
# 통계 한 칸
# ----------------------------------------------------------------------------

def mt_stat(places, games, cuts, with_delta=True):
    """metatft 스코프: places[8]에서 평균·TOP4·승률, pick = n/games. 등급은 4.5 − avg(metatft 번들 공식)."""
    n, avg, top4, win = summarize(places)
    stat = {
        "n": n,
        "places": list(places),
        "avg": r2(avg),
        "top4": r3(top4),
        "win": r3(win),
        "pick": r3(n / float(games)) if games else None,
    }
    if with_delta:
        stat["delta"] = r2(avg - 4.5)
    stat["grade"] = grade_linear(4.5 - avg, cuts) if n >= cuts["minSample"] else None
    return stat


def qq_stat(row, mean_avg, cuts):
    """
    lol.qq 스코프. delta는 그 스코프 unit_s 가중 평균 대비(중국 플래+ 모집단은 4.5가 중앙이 아니라
    4.2~4.3이다). 등급은 −delta에 같은 컷을 쓴다.
    """
    n, avg = row["n"], row["avg"]
    delta = avg - mean_avg if mean_avg is not None else None
    return {
        "n": n,
        "top1": row["top1"],
        "top4": row["top4"],
        "avg": r2(avg),
        "top4Rate": r3(row["top4"] / float(n)),
        "win": r3(row["top1"] / float(n)),
        "pick": r3(n / float(row["total"])) if row.get("total") else None,
        "delta": r2(delta),
        "grade": grade_linear(-delta, cuts) if delta is not None and n >= cuts["minSample"] else None,
    }


def item_group(kind):
    if kind == "emblem":
        return "emblem"
    if kind in ("artifact", "radiant", "support"):
        return "flat"
    return "normal"


def item_stat(places, games, kind, constants):
    """아이템 등급 = (4.5 − avg + p) × pick^i (metatft 번들 공식, 종류별 p·i)."""
    cuts = constants["itemGradeCuts"]
    params = constants["itemGradeParams"][item_group(kind)]
    stat = mt_stat(places, games, cuts, with_delta=False)
    n, avg, _top4, _win = summarize(places)
    pick = n / float(games) if games else 0.0
    score = (4.5 - avg + params["p"]) * (pick ** params["i"])
    stat["grade"] = grade_linear(score, cuts) if n >= cuts["minSample"] else None
    return stat


# ----------------------------------------------------------------------------
# lol.qq 전처리
# ----------------------------------------------------------------------------

def _qq_rows(rows, id_key, n_key, tolerance, with_units):
    kept, checked, bad = {}, 0, []
    for row in rows or []:
        raw = str(row.get(id_key) or "")
        units = None
        if with_units:
            raw, _, count = raw.partition(",")
            units = lolqq.to_int(count)
            if units is None:
                continue
        elif "," in raw:
            continue      # ',N' 접미사 행은 성급/변형 분할 행이라 챔피언 합계와 겹친다
        if not raw.startswith("DA_"):
            continue
        n = lolqq.to_int(row.get(n_key))
        top1 = lolqq.to_int(row.get("top1_cnt"))
        top4 = lolqq.to_int(row.get("top4_cnt"))
        avg = lolqq.to_float(row.get("avg_rank"))
        total = lolqq.to_int(row.get("total"))
        if not n:
            continue
        checked += 1
        if not lolqq.range_ok(n, top1, top4, avg, tolerance):
            bad.append(row.get(id_key))
            continue
        entry = {"n": n, "top1": top1, "top4": top4, "avg": avg, "total": total}
        if with_units:
            kept.setdefault(raw, {})[units] = entry
        else:
            kept[raw] = entry
    return kept, checked, bad


def prepare_qq(inputs):
    """
    스코프별 행을 범위 검사로 거르고 meanAvg를 계산한다.
    돌려주는 값: {scope: None(받지 못함) | {"heroes", "traits", "mean", "games"}}
    위반이 10%를 넘은 표는 None 대신 빈 값으로 둬서 직전 결과로 이어 쓰지도 않는다
    (원본이 모순이면 어제 값도 믿을 근거가 없다).
    """
    constants = inputs.constants["lolqq"]
    share, tolerance = constants["maxRangeViolationShare"], constants["rangeTolerance"]
    out = {}
    for key, _label, _tier in lolqq.SCOPES:
        data = inputs.qq_scopes.get(key)
        if not data:
            out[key] = None
            continue
        heroes, hero_checked, hero_bad = _qq_rows(data["hero"], "unit_id", "unit_s", tolerance, False)
        traits, trait_checked, trait_bad = _qq_rows(data["trait"], "trait_id", "trait_s", tolerance, True)
        report = {"heroes": len(heroes), "heroViolations": len(hero_bad),
                  "traits": sum(len(v) for v in traits.values()), "traitViolations": len(trait_bad)}
        for label, bad in (("챔피언", hero_bad), ("특성", trait_bad)):
            if bad:
                inputs.warn("[경고] lol.qq %s %s 범위 검사 위반 %d행 제외: %s"
                            % (key, label, len(bad), ", ".join(str(b) for b in bad[:6])))
        if hero_checked and len(hero_bad) > share * hero_checked:
            inputs.warn("[경고] lol.qq %s 챔피언 위반이 %.0f%%를 넘어 스코프를 뺀다" % (key, share * 100))
            heroes, traits = {}, {}
            report["excluded"] = True
        if trait_checked and len(trait_bad) > share * trait_checked:
            inputs.warn("[경고] lol.qq %s 특성 위반이 %.0f%%를 넘어 특성 표를 뺀다" % (key, share * 100))
            traits = {}
            report["traitsExcluded"] = True
        weight = sum(h["n"] for h in heroes.values())
        mean = sum(h["n"] * h["avg"] for h in heroes.values()) / float(weight) if weight else None
        games = lolqq.to_int((data.get("overview") or {}).get("total_games"))
        if not games and heroes:
            games = next(iter(heroes.values())).get("total")
        out[key] = {"heroes": heroes, "traits": traits, "mean": mean, "games": games,
                    "excluded": bool(report.get("excluded"))}
        report["mean"] = r3(mean)
        report["games"] = games
        inputs.report.setdefault("qq", {})[key] = report
    return out


def prepare_equip(inputs):
    """
    tft_equip_rank 두 표. 챔피언#아이템 행렬은 인기도(build_s)만 일관돼 착용 상위와 챔피언별
    아이템에만 쓰고, 평균 등수는 범위 검사를 통과한 행만 참고로 싣는다.
    """
    if not inputs.qq_equip:
        return None
    detail = inputs.constants["detail"]
    tolerance = inputs.constants["lolqq"]["rangeTolerance"]
    hero_rows = inputs.qq_equip.get("hero") or []
    builds = [lolqq.to_int(r.get("build_s")) for r in hero_rows]
    builds = [b for b in builds if b is not None]
    if builds and min(builds) > detail["equipTruncationFloor"]:
        inputs.warn("[경고] lol.qq 챔피언#아이템 표가 잘렸을 수 있다(최소 build_s %d > %d)"
                    % (min(builds), detail["equipTruncationFloor"]))

    by_champion, by_item = defaultdict(list), defaultdict(list)
    for row in hero_rows:
        key = str(row.get("item_id") or "")
        if key.count("#") != 1 or "|" in key:
            continue
        champion, item = key.split("#")
        if not champion.startswith("DA_") or not item.startswith("DA_"):
            continue
        n = lolqq.to_int(row.get("build_s"))
        if not n or n < detail["minCnBuildSample"]:
            continue
        avg = lolqq.to_float(row.get("avg_rank"))
        if not lolqq.range_ok(n, lolqq.to_int(row.get("top1_cnt")), lolqq.to_int(row.get("top4_cnt")), avg, tolerance):
            avg = None
        by_champion[champion].append((n, item, avg))
        by_item[item].append((n, champion, avg))

    table, violations = {}, 0
    for row in inputs.qq_equip.get("item") or []:
        item = str(row.get("item_id") or "")
        n = lolqq.to_int(row.get("build_s"))
        if not item.startswith("DA_") or not n:
            continue
        total = lolqq.to_int(row.get("total"))
        avg = lolqq.to_float(row.get("avg_rank"))
        if not lolqq.range_ok(n, lolqq.to_int(row.get("top1_cnt")), lolqq.to_int(row.get("top4_cnt")), avg, tolerance):
            avg = None
            violations += 1
        table[item] = {
            "n": n,
            "pick": r3(n / float(total)) if total else r3(lolqq.to_float(row.get("build_rate"))),
            "avg": r2(avg),
            "grade": None,
        }
    totals = [lolqq.to_int(r.get("total")) for r in inputs.qq_equip.get("item") or []]
    games = max([t for t in totals if t] or [0]) or None
    inputs.report["equip"] = {"pairs": sum(len(v) for v in by_champion.values()), "items": len(table),
                              "itemAvgNull": violations}
    return {"champions": by_champion, "items": by_item, "table": table, "games": games}


# ----------------------------------------------------------------------------
# 설명문
# ----------------------------------------------------------------------------

def _best_text(candidates):
    """(풀지 못한 수, 우선순위, 글) 중 비어 있지 않고 가장 온전한 것. 같으면 우선순위가 앞선 것."""
    usable = [c for c in candidates if c[2]]
    if not usable:
        return ""
    return min(usable, key=lambda c: (c[0], c[1]))[2]


def champion_ability(api, dic, names):
    """스킬 설명은 metatft 사전의 수치 계산본을 우선한다(CDragon 세트 18은 variables가 비어 수치가 빠진다)."""
    ko = dic.champions.get(api) or {}
    ability = ko.get("ability") or {}
    unit = names.units.get(api) or {}
    lk_ability = unit.get("ability") or {}
    desc, _missing = metatft.render(lk_ability.get("desc"), unit.get("curveValues"),
                                    lk_ability.get("attributeValues"), levels=(1, 2, 3))
    if not desc:
        desc, _missing = cdragon.render(ability.get("desc"), ability.get("variables"))
    stats = ko.get("stats") or {}
    initial, maximum = as_int(stats.get("initialMana")), as_int(stats.get("mana"))
    return {
        "name": ability.get("name") or lk_ability.get("name"),
        "desc": desc,
        "mana": [initial, maximum] if initial is not None and maximum is not None else [],
    }


def champion_base(ko, chess_info):
    """1~3성 체력·공격력. 성급 배율은 lol.qq chess.js(lifeMag 1.8, attackMag 1.5)가 기준."""
    stats = ko.get("stats") or {}
    life = (chess_info or {}).get("lifeMag") or 1.8
    attack = (chess_info or {}).get("attackMag") or 1.5
    hp = lolqq.to_float(stats.get("hp"))
    ad = lolqq.to_float(stats.get("damage"))
    speed = lolqq.to_float(stats.get("attackSpeed"))
    return {
        # 게임 표기는 사사오입이다(55 × 1.5 = 82.5 → 83). 파이썬 round는 짝수 반올림이라 쓰지 않는다.
        "health": [half_up(hp * life ** k) for k in range(3)] if hp else [],
        "attackDamage": [half_up(ad * attack ** k) for k in range(3)] if ad else [],
        "armor": as_int(stats.get("armor")),
        "magicResist": as_int(stats.get("magicResist")),
        "attackSpeed": r2(speed) if speed is not None else None,
        "range": as_int(stats.get("range")),
    }


def breakpoint_units(api, dic, names, trait_ids):
    """
    단계별 인원수. CDragon minUnits가 빈 특성(세트 18 일월식)이 있어 metatft 사전 → lol.qq 색 목록
    순으로 채우고, 끝내 없으면 1로 둔다(통계 키가 null이 되면 앱 모델이 깨진다).
    """
    effects = (dic.traits.get(api) or {}).get("effects") or []
    lk_effects = (names.traits.get(api) or {}).get("effects") or []
    qq_counts = next((sorted(colors) for da, _kind, colors in trait_ids.values() if da == api and colors), [])
    out = []
    for index, effect in enumerate(effects):
        units = as_int(effect.get("minUnits"))
        if units is None and index < len(lk_effects):
            units = as_int(lk_effects[index].get("minUnits"))
        if units is None and index < len(qq_counts):
            units = qq_counts[index]
        out.append(1 if units is None else units)
    return out


def trait_texts(api, dic, names, levels):
    """
    특성 소개와 단계별 효과. CDragon(라이브)과 metatft 사전 중 수치가 더 온전히 풀리는 쪽을 쓰고,
    같으면 CDragon. metatft 곡선은 PBE 기준이라 단계 값이 빠진 경우가 있다(세트 18 날렵이 5단계 0%).
    """
    trait = dic.traits.get(api) or {}
    effects = trait.get("effects") or []
    intro_raw, rows_raw, expand_raw = cdragon.trait_sections(trait.get("desc"))
    lk = names.traits.get(api) or {}
    curves = lk.get("curveValues") or {}

    first_vars = (effects[0].get("variables") if effects else None) or {}
    cd_text, cd_missing = cdragon.render(intro_raw, first_vars, icon_words=True)
    mt_text, mt_missing = metatft.render(lk.get("desc"), curves, icon_words=True)
    desc = _best_text([(cd_missing + _dangling(cd_text), 0, cd_text),
                       (mt_missing + _dangling(mt_text), 1, mt_text)])

    lk_list = lk.get("effects") or []
    lk_effects = {e.get("minUnits"): e for e in lk_list}
    breakpoints = []
    for index, effect in enumerate(effects):
        units = levels[index] if index < len(levels) else 1
        raw = rows_raw[index] if index < len(rows_raw) else expand_raw
        cd_text, cd_missing = cdragon.render(raw, effect.get("variables"), min_units=units, icon_words=True)
        # 단계 수가 같으면 순서로 짝짓는다(경쟁자처럼 같은 인원수 단계가 둘인 특성이 있다).
        if len(lk_list) == len(effects):
            mt_effect = lk_list[index]
        else:
            mt_effect = lk_effects.get(units) or {}
        mt_text, mt_missing = metatft.render(mt_effect.get("desc"), curves, levels=(index + 1,), icon_words=True)
        text = _best_text([(cd_missing + _dangling(cd_text), 0, cd_text),
                           (mt_missing + _dangling(mt_text), 1, mt_text)])
        breakpoints.append({
            "units": units,
            "style": cdragon.STYLE_TO_TIER.get(effect.get("style"), effect.get("style")),
            "desc": UNIT_COUNT_PREFIX.sub("", text),
        })
    _repair_displaced_rows(breakpoints)
    return desc, breakpoints


_DANGLING = re.compile(r"(또는|및|,)\s*$")
_DISPLACED = re.compile(r"^(\S{1,6}?)\((\d+)\)\s*")


def _dangling(text):
    """'25% 또는'처럼 아이콘이 원문에서 빠져 문장이 끊긴 글은 덜 온전한 것으로 친다."""
    return 1 if text and _DANGLING.search(text) else 0


def _repair_displaced_rows(breakpoints):
    """
    CDragon 설명의 행 경계가 한 단어 밀린 경우(날렵이: '(3) 기수가 … 및 …' 다음 행이 '획득(5) …')를
    되돌린다. 앞 행 끝에 조각을 붙이고 이 행 앞의 '조각(N)'을 지운다.
    """
    for index in range(1, len(breakpoints)):
        text = breakpoints[index]["desc"] or ""
        match = _DISPLACED.match(text)
        if not match or as_int(match.group(2)) != breakpoints[index]["units"]:
            continue
        fragment = match.group(1)
        previous = breakpoints[index - 1]["desc"]
        if previous and not previous.endswith(fragment):
            breakpoints[index - 1]["desc"] = previous + " " + fragment
        breakpoints[index]["desc"] = text[match.end():]


def item_desc(api, dic, names, augment):
    ko = dic.item(api) or (dic.normalized_item(api, True) if augment else None) or {}
    lk = (names.augments if augment else names.items).get(api) or {}
    cd_text, cd_missing = cdragon.render(ko.get("desc"), ko.get("effects"))
    mt_text, mt_missing = metatft.render(lk.get("desc"), lk.get("curveValues"), lk.get("attributeValues"))
    return _best_text([(cd_missing, 0, cd_text), (mt_missing, 1, mt_text)])


# ----------------------------------------------------------------------------
# 공통 조각
# ----------------------------------------------------------------------------

def unique_traits(inputs, names):
    """
    고유 특성(챔피언 한 명 전용). metatft 사전의 type=unique가 기준이고, 사전이 없으면 lol.qq 색 목록이
    전부 5(고유)인지, 그것도 없으면 CDragon 단계가 하나이고 style이 고유(4)인지로 본다
    (세트 18에서 세 기준 모두 같은 10개. '단계 하나'만 보면 일월식처럼 인원수 없는 특성이 잘못 빠진다).
    """
    trait_ids = lolqq.trait_id_map(inputs.qq_static.get("race"), inputs.qq_static.get("job"))
    qq_colors = {info[0]: info[2] for info in trait_ids.values()}
    out = set()
    for api, trait in inputs.dic.traits.items():
        lk_type = (names.traits.get(api) or {}).get("type")
        if lk_type:
            if lk_type == "unique":
                out.add(api)
            continue
        colors = qq_colors.get(api) or {}
        if colors:
            if all(color == cdragon.UNIQUE_TIER for color in colors.values()):
                out.add(api)
            continue
        effects = trait.get("effects") or []
        if len(effects) == 1 and cdragon.STYLE_TO_TIER.get(effects[0].get("style")) == cdragon.UNIQUE_TIER:
            out.add(api)
    return out


def scope_meta(inputs, kind, file_name, qq, previous):
    out = {}
    for key, label, _rank, _server in metatft.SCOPES:
        table = inputs.table(key, kind)
        if table:
            meta = {"label": label, "source": "metatft", "days": metatft.DAYS,
                    "games": table["games"], "updatedAt": iso_from_ms(table.get("updated"))}
            if kind == "units":
                meta["unitsPerBoard"] = r2(sum(sum(r["places"]) for r in table["rows"]) / float(table["games"]))
            out[key] = meta
        elif previous.scopes(file_name).get(key):
            out[key] = previous.scopes(file_name)[key]
    for key, label, _tier in lolqq.SCOPES:
        if key not in qq:
            continue      # 이 파일에는 그 스코프 원본이 없다(아이템은 중국 플래+ 표만 있다)
        scope = qq[key]
        if scope is None:
            if previous.scopes(file_name).get(key):
                out[key] = previous.scopes(file_name)[key]
            continue
        if scope.get("excluded"):
            continue      # 범위 검사로 통째로 뺀 스코프는 표본 줄도 싣지 않는다
        out[key] = {"label": label, "source": "lolqq", "days": inputs.constants["lolqq"]["days"],
                    "games": scope["games"], "statDate": inputs.window[1], "build": "",
                    "meanAvg": r3(scope["mean"])}
    return out


def version_block(inputs):
    return {
        "schemaVersion": SCHEMA_VERSION,
        "generatedAt": iso_now(inputs.now),
        "patchGlobal": inputs.patch_global,
        "patch": inputs.qq_patch,
        "statDate": inputs.window[1],
        "contentHash": "",
    }


def snapshot_info(snapshot, patch_global):
    if snapshot.get("patchGlobal") and snapshot.get("patchGlobal") != patch_global:
        return {"patchGlobal": snapshot["patchGlobal"], "frozenAt": snapshot.get("frozenAt")}
    return None


def _carry(inputs, source, prev_row, key, container, field="stats"):
    """직전 결과(같은 패치)의 한 칸을 이어 쓴다. 이어 쓴 원본은 상태가 stale이 된다."""
    value = ((prev_row or {}).get(field) or {}).get(key)
    if value:
        container[key] = value
        inputs.carried.add(source)


def detail_targets(inputs):
    """
    상세 호출 대상과 순서(표본이 큰 것부터 — 호출 수를 줄여야 할 때 앞에서 자르면 된다).
    챔피언: 글로벌 플래+ 통계 행이 있는 챔피언. 아이템 착용자: 완성템(부품 2개) ∪ 상징.
    완성 스테이지: 완성템.
    """
    dic = inputs.dic
    components = set(inputs.constants["components"])
    units = inputs.table(metatft.DETAIL_SCOPE, "units")
    unit_index = units["index"] if units else {}
    champions = [api for api in dic.playable_champions() if api in unit_index]
    champions.sort(key=lambda api: (-sum(unit_index[api]["places"]), api))

    items = inputs.table(metatft.DETAIL_SCOPE, "items")
    item_index = items["index"] if items else {}
    pool = set(item_index) if item_index else set(dic.set_data.get("items") or [])
    completed, emblems = set(), set()
    for api in pool:
        if not api.startswith("DA_") or api in components:
            continue
        composition = (dic.item(api) or {}).get("composition") or []
        if len(composition) == 2 and all(c in components for c in composition):
            completed.add(api)
        if EMBLEM.match(api):
            emblems.add(api)

    def volume(api):
        return (-sum(item_index[api]["places"]) if api in item_index else 0, api)

    return champions, sorted(completed | emblems, key=volume), sorted(completed, key=volume)


# ----------------------------------------------------------------------------
# champions.json
# ----------------------------------------------------------------------------

def unit_items_summary(blob, champion_avg, names, constants):
    detail = constants["detail"]
    components = set(constants["components"])
    merged = {}
    for entry in blob.get("items") or []:
        # 'DA_GuinsoosRageblade-2'의 -N은 같은 아이템을 N개 든 행으로 보인다(미확인). 합산이 안전하다.
        name = COPY_SUFFIX.sub("", str(entry.get("itemName") or ""))
        places = clean_places(entry.get("places"))
        if not name.startswith("DA_") or name in components or not places:
            continue
        acc = merged.setdefault(name, [0] * 8)
        for i, value in enumerate(places):
            acc[i] += value
    ranked = []
    for name, places in merged.items():
        summary = summarize(places)
        if summary:
            ranked.append((summary[0], name, summary[1]))
    ranked.sort(key=lambda t: (-t[0], t[1]))
    top = []
    for n, name, avg in ranked[:detail["topItems"]]:
        ko = names.dic.item(name) or {}
        top.append({
            "id": name,
            "name": names.item(name),
            "icon": cdragon.asset_path(ko.get("icon")),
            "n": n,
            "avg": r2(avg),
            "delta": r2(avg - champion_avg) if champion_avg is not None else None,
        })

    builds = []
    for entry in blob.get("builds") or []:
        ids = [x for x in str(entry.get("buildNames") or "").split("|") if x]
        places = clean_places(entry.get("places"))
        summary = summarize(places) if places else None
        if len(ids) != 3 or not summary or summary[0] < detail["minBuildSample"]:
            continue
        builds.append({"items": ids, "n": summary[0], "avg": r2(summary[1])})
    builds.sort(key=lambda b: (-b["n"], b["items"]))
    return {"scope": metatft.DETAIL_SCOPE, "top": top, "builds": builds[:detail["topBuilds"]]}


def build_champions(inputs, names, previous, qq, equip):
    dic, constants = inputs.dic, inputs.constants
    cuts = constants["championGradeCuts"]
    components = set(constants["components"])
    top_n = constants["detail"]["topItems"]
    chess = lolqq.chess_map(inputs.qq_static.get("chess"))
    snapshot = inputs.snapshot
    snap_ok = snapshot_info(snapshot, inputs.patch_global) is not None

    rows = []
    for api in dic.playable_champions():
        ko = dic.champions[api]
        prev_row = previous.row("champions", api)

        stats, glob_avg = {}, None
        for key, _label, _rank, _server in metatft.SCOPES:
            table = inputs.table(key, "units")
            if table is None:
                _carry(inputs, "metatftStats", prev_row, key, stats)
                continue
            row = table["index"].get(api)
            if row:
                stats[key] = mt_stat(row["places"], table["games"], cuts)
                if key == metatft.DETAIL_SCOPE:
                    glob_avg = summarize(row["places"])[1]
        for key, _label, _tier in lolqq.SCOPES:
            scope = qq.get(key)
            if scope is None:
                _carry(inputs, "lolqqDatasearch", prev_row, key, stats)
            elif api in scope["heroes"]:
                stats[key] = qq_stat(scope["heroes"][api], scope["mean"], cuts)

        blob = inputs.unit_items.get(api)
        if blob:
            items = unit_items_summary(blob, glob_avg, names, constants)
        elif api in inputs.detail_failed["unit_items"] and (prev_row or {}).get("items"):
            items = prev_row["items"]
            inputs.carried.add("metatftDetail")
        else:
            items = {"scope": metatft.DETAIL_SCOPE, "top": [], "builds": []}

        cn_items = []
        if equip is not None:
            for n, item, avg in sorted(equip["champions"].get(api, []), key=lambda t: (-t[0], t[1])):
                if item in components:
                    continue
                cn_items.append({"id": item, "name": names.item(item), "n": n, "avg": r2(avg)})
                if len(cn_items) >= top_n:
                    break
        elif (prev_row or {}).get("cnItems"):
            cn_items = prev_row["cnItems"]
            inputs.carried.add("lolqqDatasearch")

        # 고유 특성도 싣는다(traits.json에는 없지만 드레이븐처럼 고유 특성뿐인 챔피언이 빈 칸이 되지 않게).
        traits = []
        for trait_name in ko.get("traits") or []:
            tid = dic.trait_by_name.get(trait_name)
            if tid:
                traits.append({"id": tid, "name": names.trait(tid)})

        role_key = (names.units.get(api) or {}).get("role")
        rows.append({
            "id": api,
            "name": names.champion(api),
            "nameEn": names.champion_en(api),
            "cost": as_int(ko.get("cost")),
            "icon": cdragon.asset_path(ko.get("tileIcon") or ko.get("squareIcon")),
            "traits": traits,
            "role": names.roles.get(role_key) if role_key else None,
            "ability": champion_ability(api, dic, names),
            "base": champion_base(ko, chess.get(api)),
            "changed": (chess.get(api) or {}).get("changed"),
            "stats": stats,
            "items": items,
            "cnItems": cn_items,
            "prev": (snapshot.get("champions") or {}).get(api, {}) if snap_ok else {},
            "decks": inputs.decks.by_id["champion"].get(api, []),
        })
    rows.sort(key=lambda r: (r["cost"] or 0, r["name"], r["id"]))

    return {
        "version": version_block(inputs),
        "scopes": scope_meta(inputs, "units", "champions", qq, previous),
        "gradeCuts": cuts,
        "prevPatch": snapshot_info(snapshot, inputs.patch_global),
        "champions": rows,
    }


# ----------------------------------------------------------------------------
# traits.json
# ----------------------------------------------------------------------------

def trait_combos(trend, trait_ids, names):
    """
    lol.qq 주특성 2개 조합(tft_trait_strength_trend). 1_이 최신, 5_가 가장 오래된 주기라
    trend는 5_→1_ 순으로 뒤집어 최신이 마지막에 오게 한다. 1개·3개 조합 행은 계약상 쓰지 않는다.
    """
    combos = defaultdict(list)
    for row in (trend or {}).get("main_buff_data") or []:
        pair = row.get("trait_list") or []
        if len(pair) != 2:
            continue
        mapped = []
        for entry in pair:
            info = trait_ids.get(str(entry.get("trait_id")))
            cycle = lolqq.to_int(entry.get("cycle"))
            if not info or cycle is None:
                break
            mapped.append((info[0], cycle))
        if len(mapped) != 2 or mapped[0][0] == mapped[1][0]:
            continue
        top4 = lolqq.to_float(row.get("1_top_4_rate"))
        win = lolqq.to_float(row.get("1_top_1_rate"))
        history = [r3(lolqq.to_float(row.get("%d_top_4_rate" % k))) for k in (5, 4, 3, 2, 1)]
        for mine, other in ((mapped[0], mapped[1]), (mapped[1], mapped[0])):
            combos[mine[0]].append({
                "with": {"id": other[0], "name": names.trait(other[0]), "units": other[1]},
                "units": mine[1],
                "top4": r3(top4),
                "win": r3(win),
                "trend": history,
            })
    for rows in combos.values():
        rows.sort(key=lambda c: (-(c["top4"] or 0), c["with"]["id"]))
    return combos


def build_traits(inputs, names, previous, qq):
    dic, constants = inputs.dic, inputs.constants
    cuts = constants["traitGradeCuts"]
    unique = unique_traits(inputs, names)
    trait_ids = lolqq.trait_id_map(inputs.qq_static.get("race"), inputs.qq_static.get("job"))
    kind_by_da = {info[0]: info[1] for info in trait_ids.values()}

    # metatft 'DA_X_N'의 N은 인원수가 아니라 단계 순번 → CDragon effects[N-1].minUnits
    levels = {api: breakpoint_units(api, dic, names, trait_ids) for api in dic.traits}
    mt_stats = defaultdict(lambda: defaultdict(dict))
    skipped = []
    for key, _label, _rank, _server in metatft.SCOPES:
        table = inputs.table(key, "traits")
        for row in (table or {}).get("rows") or []:
            base, _, step = row["id"].rpartition("_")
            step = lolqq.to_int(step)
            units = levels.get(base) or []
            if not step or step > len(units):
                skipped.append(row["id"])
                continue
            mt_stats[base][key][str(units[step - 1])] = mt_stat(row["places"], table["games"], cuts)
    if skipped:
        inputs.warn("[경고] metatft 특성 행 %d개를 CDragon 단계에 맞추지 못했다: %s"
                    % (len(skipped), ", ".join(sorted(set(skipped))[:6])))

    combos = trait_combos(inputs.qq_trend, trait_ids, names) if inputs.qq_trend else None

    members = defaultdict(list)
    for api in dic.playable_champions():
        for trait_name in dic.champions[api].get("traits") or []:
            tid = dic.trait_by_name.get(trait_name)
            if tid:
                members[tid].append(api)

    rows = []
    for api, trait in dic.traits.items():
        if api in unique:
            continue
        prev_row = previous.row("traits", api)
        stats = {}
        for key, _label, _rank, _server in metatft.SCOPES:
            if inputs.table(key, "traits") is None:
                _carry(inputs, "metatftStats", prev_row, key, stats)
            elif mt_stats[api].get(key):
                stats[key] = {"byUnits": dict(mt_stats[api][key])}
        for key, _label, _tier in lolqq.SCOPES:
            scope = qq.get(key)
            if scope is None:
                _carry(inputs, "lolqqDatasearch", prev_row, key, stats)
                continue
            by_units = {str(units): qq_stat(entry, scope["mean"], cuts)
                        for units, entry in sorted((scope["traits"].get(api) or {}).items())}
            if by_units:
                stats[key] = {"byUnits": by_units}

        if combos is not None:
            trait_combo_rows = combos.get(api, [])
        else:
            trait_combo_rows = (prev_row or {}).get("combos") or []

        desc, breakpoints = trait_texts(api, dic, names, levels[api])
        lk_type = (names.traits.get(api) or {}).get("type")
        champions = sorted(members.get(api, []), key=lambda c: (as_int(dic.champions[c].get("cost")) or 0, names.champion(c)))
        rows.append({
            "id": api,
            "name": names.trait(api),
            "nameEn": names.trait_en(api),
            "type": lk_type if lk_type in TYPE_ORDER else kind_by_da.get(api),
            "icon": cdragon.asset_path(trait.get("icon")),
            "desc": desc,
            "breakpoints": breakpoints,
            "champions": [{"id": c, "name": names.champion(c), "cost": as_int(dic.champions[c].get("cost"))}
                          for c in champions],
            "stats": stats,
            "combos": trait_combo_rows,
            "decks": inputs.decks.by_id["trait"].get(api, []),
        })
    rows.sort(key=lambda r: (TYPE_ORDER.get(r["type"], 9), r["name"], r["id"]))

    return {
        "version": version_block(inputs),
        "scopes": scope_meta(inputs, "traits", "traits", qq, previous),
        "gradeCuts": cuts,
        "traits": rows,
    }


# ----------------------------------------------------------------------------
# items.json
# ----------------------------------------------------------------------------

def item_kind(api, dic, names, equip_types, components):
    """
    종류 판정(설계 §5.6 순서). 이름 규칙이 1순위이고 metatft 사전 태그와 lol.qq equip.js type으로 보강한다
    (equip.js: 1 부품, 2 완성, 3 찬란, 5 획득형 상징, 6·7 유물).
    """
    if api in components:
        return "component"
    tags = set((names.items.get(api) or {}).get("tags") or [])
    equip_type = (equip_types.get(api) or {}).get("type")
    if EMBLEM.match(api) or equip_type == "5":
        return "emblem"
    if ARTIFACT.match(api) or equip_type in ("6", "7"):
        return "artifact"
    name = (dic.item(api) or {}).get("name") or ""
    if "radiant" in api.lower() or "Radiant" in tags or equip_type == "3" or name.startswith("찬란한"):
        return "radiant"
    if "support" in api.lower():
        return "support"
    composition = (dic.item(api) or {}).get("composition") or []
    if len(composition) == 2:
        return "completed"
    return "other"


def item_wearers_glob(blob, names, top_n):
    """item_detail.units 착용 상위. delta = 그 유닛이 이 아이템을 들었을 때 평균 − 그 유닛 전체 평균."""
    overall = {}
    for row in blob.get("units_overall") or []:
        places = clean_places(row.get("places"))
        summary = summarize(places) if places else None
        if summary:
            overall[row.get("unit")] = summary[1]
    ranked = []
    for row in blob.get("units") or []:
        unit = str(row.get("unit") or "")
        places = clean_places(row.get("places"))
        summary = summarize(places) if places else None
        if not unit.startswith("DA_") or not summary:
            continue
        ranked.append((summary[0], unit, summary[1]))
    ranked.sort(key=lambda t: (-t[0], t[1]))
    return [{"id": unit, "name": names.champion(unit), "n": n, "avg": r2(avg),
             "delta": r2(avg - overall[unit]) if unit in overall else None}
            for n, unit, avg in ranked[:top_n]]


def item_stage_rows(blob):
    out = []
    for row in blob.get("stage") or []:
        stage = lolqq.to_int(row.get("stage"))
        count = lolqq.to_int(row.get("count"))
        win = lolqq.to_int(row.get("win"))
        if stage is None or not count:
            continue
        out.append({"stage": stage, "n": count, "win": r3(win / float(count)) if win is not None else None})
    return sorted(out, key=lambda r: r["stage"])


def build_items(inputs, names, previous, equip):
    dic, constants = inputs.dic, inputs.constants
    components = list(constants["components"])
    component_set = set(components)
    top_n = constants["detail"]["topWearers"]
    equip_types = lolqq.equip_map(inputs.qq_static.get("equip"))
    snapshot = inputs.snapshot
    snap_ok = snapshot_info(snapshot, inputs.patch_global) is not None

    ids = set(component_set)
    for key, _label, _rank, _server in metatft.SCOPES:
        table = inputs.table(key, "items")
        if table:
            ids.update(table["index"])
    if equip is not None:
        ids.update(equip["table"])
    for api in dic.set_data.get("items") or []:
        composition = (dic.item(api) or {}).get("composition") or []
        if api.startswith("DA_") and len(composition) == 2 and all(c in component_set for c in composition):
            ids.add(api)
    ids.update(previous.rows["items"] if previous.valid else [])

    rows = []
    for api in ids:
        ko = dic.item(api) or {}
        lk = names.items.get(api) or {}
        kind = item_kind(api, dic, names, equip_types, component_set)
        composition = ko.get("composition") or lk.get("composition") or []
        prev_row = previous.row("items", api)

        stats = {}
        for key, _label, _rank, _server in metatft.SCOPES:
            table = inputs.table(key, "items")
            if table is None:
                _carry(inputs, "metatftStats", prev_row, key, stats)
                continue
            row = table["index"].get(api)
            if row:
                stats[key] = item_stat(row["places"], table["games"], kind, constants)
        if equip is not None:
            if api in equip["table"]:
                stats[lolqq.EQUIP_SCOPE] = equip["table"][api]
        else:
            _carry(inputs, "lolqqDatasearch", prev_row, lolqq.EQUIP_SCOPE, stats)

        wearers = {}
        detail = inputs.item_details.get(api)
        if detail:
            wearers[metatft.DETAIL_SCOPE] = item_wearers_glob(detail, names, top_n)
        elif api in inputs.detail_failed["item_detail"]:
            _carry(inputs, "metatftDetail", prev_row, metatft.DETAIL_SCOPE, wearers, field="wearers")
        if equip is not None:
            ranked = sorted(equip["items"].get(api, []), key=lambda t: (-t[0], t[1]))[:top_n]
            if ranked:
                wearers[lolqq.EQUIP_SCOPE] = [{"id": c, "name": names.champion(c), "n": n, "avg": r2(avg)}
                                              for n, c, avg in ranked]
        else:
            _carry(inputs, "lolqqDatasearch", prev_row, lolqq.EQUIP_SCOPE, wearers, field="wearers")

        stages = []
        if api in inputs.item_stages:
            stages = item_stage_rows(inputs.item_stages[api])
        elif api in inputs.detail_failed["item_stages"] and (prev_row or {}).get("stages"):
            stages = prev_row["stages"]
            inputs.carried.add("metatftDetail")

        rows.append({
            "id": api,
            "name": names.item(api),
            "nameEn": names.item_en(api),
            "kind": kind,
            "icon": cdragon.asset_path(ko.get("icon")) or (equip_types.get(api) or {}).get("icon"),
            "desc": item_desc(api, dic, names, augment=False),
            "components": [c for c in composition if c],
            "stats": stats,
            "wearers": wearers,
            "stages": stages,
            "prev": (snapshot.get("items") or {}).get(api, {}) if snap_ok else {},
            "decks": inputs.decks.by_id["item"].get(api, []),
        })
    rows.sort(key=lambda r: (KIND_ORDER.get(r["kind"], 9), r["name"], r["id"]))

    # 11×11 조합표: 정렬한 부품 두 개 → 완성템. 같은 조합이 둘이면 완성템을 상징보다 앞에 둔다.
    recipes = {}
    for row in sorted(rows, key=lambda r: (r["kind"] != "completed", r["id"])):
        pair = row["components"]
        if row["kind"] == "component" or len(pair) != 2 or not all(c in component_set for c in pair):
            continue
        key = "|".join(sorted(pair))
        if key in recipes:
            inputs.warn("[경고] 조합 %s가 %s와 %s 두 곳에 있다(앞의 것을 쓴다)" % (key, recipes[key], row["id"]))
            continue
        recipes[key] = row["id"]

    # 중국 스코프의 아이템 표본은 플래+(4+) 아이템 표뿐이다. meanAvg는 챔피언 기준이라 싣지 않는다.
    equip_scope = {"games": equip["games"], "mean": None} if equip is not None else None
    return {
        "version": version_block(inputs),
        "scopes": scope_meta(inputs, "items", "items", {lolqq.EQUIP_SCOPE: equip_scope}, previous),
        "gradeCuts": constants["itemGradeCuts"],
        "prevPatch": snapshot_info(snapshot, inputs.patch_global),
        "items": rows,
        "components": components,
        "recipes": dict(sorted(recipes.items())),
    }


# ----------------------------------------------------------------------------
# augments.json
# ----------------------------------------------------------------------------

def augment_guides(inputs):
    """
    metatft comp_augment_tiers(가이드 기반 클러스터별 추천)를 뒤집어 '이 증강을 S로 추천하는 덱'.
    클러스터 → 덱은 decks.json global.cluster 일치가 1순위, 없으면 클러스터 유닛과 덱 유닛의
    자카드 유사도 최고값(≥ 0.5). 매칭 거리(distance)가 큰 가이드는 엉뚱한 덱일 수 있어 뺀다.
    """
    results = inputs.comp_guides or {}
    if not results or not inputs.decks.ok:
        return {}
    guide = inputs.constants["guide"]
    out = defaultdict(dict)
    for key, entry in results.items():
        cluster = as_int(key)
        distance = entry.get("distance") if isinstance(entry, dict) else None
        if cluster is None or not isinstance(distance, (int, float)) or distance > guide["maxDistance"]:
            continue
        decks = sorted(inputs.decks.cluster_decks.get(cluster) or [])
        if not decks and inputs.clusters and cluster in inputs.clusters:
            units = inputs.clusters[cluster]
            best, score = None, 0.0
            for did, mine in inputs.decks.deck_units.items():
                union = mine | units
                value = len(mine & units) / float(len(union)) if union else 0.0
                if value > score:
                    best, score = did, value
            if best and score >= guide["clusterJaccard"]:
                decks = [best]
        source = entry.get("source_title") or ""
        for augment in entry.get("augments") or []:
            if augment.get("tier") != guide["tier"] or not augment.get("id"):
                continue
            for did in decks:
                out[augment["id"]].setdefault(did, {"deck": did, "tier": guide["tier"], "source": source})
    return {aid: [by_deck[d] for d in sorted(by_deck)] for aid, by_deck in out.items()}


def build_augments(inputs, names, previous):
    dic, constants = inputs.dic, inputs.constants
    hexes = lolqq.hex_map(inputs.qq_static.get("hex"))
    ids = set(hexes) | {a for a in names.augments if a.startswith("DA_")}
    if not ids:
        # 두 목록을 모두 못 받은 날에도 도감이 비지 않게 CDragon 세트 목록과 직전 결과로 채운다.
        ids = set(dic.set_augment_ids()) | set(previous.rows["augments"] if previous.valid else [])

    tiers = inputs.augment_tiers
    guides = augment_guides(inputs)
    prev_meta = previous.blob("augments").get("meta") or {}

    rows = []
    for api in ids:
        ko = dic.item(api) or dic.normalized_item(api, True) or {}
        lk = names.augments.get(api) or {}
        prev_row = previous.row("augments", api)

        rarity = str(lk.get("rarity") or "").lower() or RARITY_BY_HEX_TYPE.get((hexes.get(api) or {}).get("type"))
        if tiers:
            raw_tags = tiers["tags"].get(api)
            tags = ([t.strip() for t in str(raw_tags).split(",") if t.strip()] if raw_tags
                    else [str(t) for t in lk.get("manual_tags") or []])
            editor = tiers["tiers"].get(api)
        else:
            tags = (prev_row or {}).get("tags") or [str(t) for t in lk.get("manual_tags") or []]
            editor = (prev_row or {}).get("editorTier")
            if prev_row:
                inputs.carried.add("augmentsTiers")

        deck_stats = sorted(inputs.decks.augment_stats.get(api, []), key=lambda s: (-s["n"], s["deck"]))
        # 요약은 meta.cnStats 라벨이 가리키는 구간 행만 합친다. 다른 구간(다이아+ 등) 행은 덱별 표에만
        # 구간 이름과 함께 나온다 — 섞어 합치면 '중국 골드~에메랄드' 라벨이 틀린 말이 된다.
        base_rows = [s for s in deck_stats if s.get("bucket", CN_STATS_BUCKET) == CN_STATS_BUCKET]
        summary = None
        if base_rows:
            total = sum(s["n"] for s in base_rows)
            weighted = [(s["n"], s["avg"]) for s in base_rows if isinstance(s["avg"], (int, float))]
            weight = sum(n for n, _ in weighted)
            summary = {"n": total,
                       "avg": r2(sum(n * a for n, a in weighted) / float(weight)) if weight else None,
                       "decks": len(base_rows)}
        editorial = sorted(inputs.decks.editorial.get(api) or [])
        guide = guides.get(api, [])
        decks = sorted(set(inputs.decks.by_id["augment"].get(api, []))
                       | {s["deck"] for s in deck_stats} | set(editorial) | {g["deck"] for g in guide})

        rows.append({
            "id": api,
            "name": names.item(api, augment=True),
            "nameEn": names.item_en(api, augment=True),
            "rarity": rarity,
            "tags": tags,
            "icon": cdragon.asset_path(ko.get("icon")) or (hexes.get(api) or {}).get("icon"),
            "desc": item_desc(api, dic, names, augment=True),
            "editorTier": editor,
            "deckStats": deck_stats,
            "summary": summary,
            "recommendedBy": {"editorial": editorial, "guide": guide},
            "decks": decks,
        })
    rows.sort(key=lambda r: (r["name"], r["id"]))

    if tiers:
        counts = {label: 0 for label in ("S", "A", "B", "C", "D")}
        for label in tiers["tiers"].values():
            counts[label] = counts.get(label, 0) + 1
        editor_meta = {"source": "metatft", "author": tiers.get("author"),
                       "updatedAt": iso_trim(tiers.get("updatedAt")), "counts": counts}
    else:
        editor_meta = prev_meta.get("editorTier")

    return {
        "version": version_block(inputs),
        "meta": {
            "editorTier": editor_meta,
            "cnStats": {"source": "lolqq", "bucket": CN_STATS_BUCKET, "label": "중국 골드~에메랄드",
                        "detailDate": inputs.decks.detail_date, "note": "덱별 상위 5개만 집계된다"},
        },
        "augments": rows,
        "rounds": constants.get("rounds") or {},
    }


# ----------------------------------------------------------------------------
# 이전 패치 동결
# ----------------------------------------------------------------------------

def _freeze_rows(rows):
    out = {}
    for row in rows or []:
        values = {}
        for scope, stat in (row.get("stats") or {}).items():
            if isinstance(stat, dict) and stat.get("avg") is not None:
                values[scope] = {"avg": stat.get("avg"), "pick": stat.get("pick")}
        if values and row.get("id"):
            out[row["id"]] = values
    return out


def next_snapshot(inputs, old_snapshot):
    """
    패치가 바뀐 첫 실행에서 직전 실행 결과(이전 패치의 마지막 값)를 동결한다.
    스냅샷의 patchGlobal(동결된 옛 패치)과 비교하면 새 패치 동안 매 실행마다 다시 동결되므로,
    직전 결과 파일의 patchGlobal과 현재 패치를 비교한다. 첫 실행은 빈 객체.
    """
    champions = inputs.previous_blobs.get("champions") or {}
    old_patch = (champions.get("version") or {}).get("patchGlobal")
    current = inputs.patch_global
    if current and old_patch and old_patch != current:
        frozen_at = iso_trim((inputs.patch or {}).get("start")) or iso_now(inputs.now)
        snapshot = {
            "patchGlobal": old_patch,
            "frozenAt": frozen_at,
            "champions": _freeze_rows(champions.get("champions")),
            "items": _freeze_rows((inputs.previous_blobs.get("items") or {}).get("items")),
        }
        return snapshot, True
    return (old_snapshot if isinstance(old_snapshot, dict) else {}), False


# ----------------------------------------------------------------------------
# 마무리
# ----------------------------------------------------------------------------

def dumps(payload):
    return json.dumps(payload, ensure_ascii=False, separators=(",", ":"), sort_keys=True)


def finalize(payload):
    """
    내용 해시. generatedAt은 매일 바뀌므로 빼고 계산한다 — 수치가 같으면 해시도 같아
    앱이 같은 파일을 다시 받지 않는다.
    """
    clone = json.loads(dumps(payload))
    clone["version"]["generatedAt"] = ""
    clone["version"]["contentHash"] = ""
    digest = hashlib.sha256(dumps(clone).encode("utf-8")).hexdigest()[:16]
    payload["version"]["contentHash"] = digest
    return digest


def stats_version(inputs, hashes, sources):
    payload = {
        "schemaVersion": SCHEMA_VERSION,
        "generatedAt": iso_now(inputs.now),
        "set": inputs.set_id,
        "setNumber": inputs.set_number,
        "patchGlobal": inputs.patch_global,
        "patch": inputs.qq_patch,
        "qqBuild": inputs.qq_build,
        "statDate": inputs.window[1],
        "files": dict(hashes),
        "sources": dict(sources),
        "contentHash": "",
    }
    body = {k: v for k, v in payload.items() if k not in ("generatedAt", "contentHash")}
    payload["contentHash"] = hashlib.sha256(dumps(body).encode("utf-8")).hexdigest()[:16]
    return payload


def build_all(inputs):
    """네 파일 payload와 새 스냅샷. 파일은 쓰지 않는다(전부 만든 뒤에 한꺼번에 쓰려고)."""
    names = Names(inputs)
    previous = Previous(inputs.previous_blobs, inputs.patch_global)
    qq = prepare_qq(inputs)
    equip = prepare_equip(inputs)
    payloads = {
        "champions": build_champions(inputs, names, previous, qq, equip),
        "traits": build_traits(inputs, names, previous, qq),
        "items": build_items(inputs, names, previous, equip),
        "augments": build_augments(inputs, names, previous),
    }
    return payloads, names
