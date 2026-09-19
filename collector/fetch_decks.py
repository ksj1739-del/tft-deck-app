#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
lol.qq.com/tft 덱 데이터를 한국어로 모아 decks.json(v2)을 만든다.

원천 넷을 합친다(설계 §4):
  E  편집 덱 lineup_detail_total                 — 단계별 보드·운영 텍스트·증강
  W  胜率阵容 목록 5구간 + 상세                    — 그룹/조합, 중국 한정 덱의 등급 근거가 되는 표본
  R  数据检索器 lineup_rank(플래+ / 마스터+, 3일)  — 정밀 참고치
  M  metatft comps_stats(앱 구간 5 + 비교 스코프 3) · comps_data · comp_details — 조합 덱의 수치·등급,
     글로벌/KR 비교, 레벨별 빌드업

덱 목록(2026-09-19) = metatft 조합 덱(kind=meta, 구간별 metatft 등급; 가장 비슷한 lol.qq 그룹이 있으면 그 보드·편집 덱·
상세를 합친다) + 중국 한정 덱(어느 조합 덱에도 합쳐지지 않은 lol.qq 그룹·편집 독립 덱. 등급은 구간마다 lol.qq 보정 평균
순으로 대상의 앞 절반 S · 나머지 A — china_half_grades).

앱은 이 결과물만 받는다. 시즌이 바뀌거나 원본 스키마가 흔들려도 여기만 고치면 되고
앱은 재배포하지 않아도 된다. v1 필드와 index 5축·catalog 4배열은 이름·의미를 유지해
v1 앱이 v2 파일을 읽어도 죽지 않게 한다.

의존성 없음 (표준 라이브러리만 사용).

  python collector/fetch_decks.py [--snapshot] [--detail-limit N] [--comp-limit N]
"""

import argparse
import gzip
import hashlib
import json
import os
import re
import shutil
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone

import deck_merge as merge
import metatft_comps as mt
import qq_proxy
import qq_static

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
# metatft 사이트가 유닛 초상을 그리는 주소(apiName 소문자). lol.qq 도감에 없는 소환물의 마지막 대체 경로.
METATFT_CHAMPION_ICON = "https://cdn.metatft.com/file/metatft/champions/%s.png"

UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

# metatft 클러스터와 같은 덱으로 볼 최소 자카드 유사도.
# 실측 분포가 0.31~0.45(미등재) / 0.55~1.00(등재)로 뚜렷하게 갈려 0.5가 안전하다.
SIMILARITY_THRESHOLD = 0.5

# lol.qq의 특성 color 5는 챔피언 고유 특성이라 덱 이름/시너지 표시에서 제외한다.
UNIQUE_TRAIT_STYLE = 5

SCHEMA_VERSION = 2

# 胜率阵容 상세 호출 예산(하루). 레이트리밋은 관찰되지 않았지만 비공개 API 라 설계 상한을 지킨다.
DETAIL_CALL_LIMIT = 100
# comp_details 를 받을 클러스터 수 상한. 빌드업(§13.2)은 매칭된 덱의 80% 이상이 가져야 해서
# 클러스터 전체(현재 54)를 덮는 값으로 둔다. 상세 한 번에 약 300KB.
COMP_DETAIL_LIMIT = 60
# KR 배지를 달 최소 표본.
KR_MIN_SAMPLE = 300
MAX_FILE_BYTES = 2 * 1024 * 1024

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "data")
SNAPSHOT = os.path.join(ROOT, "android", "app", "src", "main", "assets", "decks.json")
OVERRIDES = os.path.join(ROOT, "collector", "overrides_ko.json")


def log(text):
    print(text)


def warn(text):
    print("[경고] %s" % text, file=sys.stderr)


def to_int(value, default=None):
    try:
        return int(float(str(value).strip()))
    except (TypeError, ValueError):
        return default


# ----------------------------------------------------------------------------
# 네트워크
# ----------------------------------------------------------------------------

class SourceError(Exception):
    """원본 하나를 끝내 가져오지 못했을 때(HTTP 200 빈 응답 포함)."""


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
    try:
        return json.loads(fetch(url, **kw), strict=False)
    except ValueError as exc:
        raise SourceError("%s -> JSON 아님: %s" % (url, exc))


def fetch_text(url, tries=3):
    # qq_static 에 넘기는 얇은 래퍼. 전역 fetch 를 호출 시점에 찾는다.
    return fetch(url, tries=tries)


# ----------------------------------------------------------------------------
# 사전 (CommunityDragon + lol.qq 정적 도감 + 수기 오버라이드)
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
    if path.startswith("http"):
        return path
    return re.sub(r"\.(tex|dds)$", ".png", path.lower())


class Dictionary:
    """챔피언/특성/아이템 ID를 한국어 이름과 아이콘으로 푼다."""

    def __init__(self, ko, en, set_number, overrides=None):
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

        # CommunityDragon 에 없는 소환물·변신체 이름. 게임 표기로 확인한 것만 수기로 둔다.
        self.overrides = (overrides or {}).get("units") or {}
        self.qq = None       # lol.qq 정적 도감(pet 이름·아이콘)
        self.aliases = {}    # 같은 캐릭터의 다른 형태 -> CommunityDragon 에 있는 id
        # metatft unit_lookup: DA id -> TFT18_* apiName, apiName -> 그 이름을 쓰는 DA id 들(pet 아이콘 대체 경로)
        self.metatft_api = {}
        self.metatft_members = {}

        # 실제로 등장한 것만 catalog에 싣는다. 해석 시점에 기록해 두면 보드·빌드업을
        # id 참조로 줄여도 거기 쓰인 유닛이 catalog에서 빠지지 않는다.
        self.seen = {"champions": {}, "traits": {}, "items": {}, "augments": {}, "pets": {}}

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

    def link_qq(self, static):
        self.qq = static

    def set_aliases(self, unit_lookup):
        """
        metatft unit_lookup 은 형태별 DA id 를 같은 TFT18_* apiName 으로 묶는다
        (예 DA_NidaleeCougar18_AD, DA_Nidalee18_AP -> TFT18_Nidalee).
        CommunityDragon 에 없는 형태를 있는 형태로 이어 이름·아이콘·코스트를 빌린다.
        """
        members = {}
        for da, row in (unit_lookup or {}).items():
            api = (row or {}).get("apiName")
            if api:
                members.setdefault(api, []).append(da)
        self.metatft_api = {da: api for api, ids in members.items() for da in ids}
        self.metatft_members = members
        for ids in members.values():
            known = sorted(i for i in ids if i in self.champions)
            if not known:
                continue
            for da in ids:
                if da not in self.champions:
                    self.aliases[da] = known[0]

    # -- champion / pet --------------------------------------------------------
    def _champion_record(self, api_name):
        ko = self.champions.get(api_name)
        en = self.champions_en.get(api_name)
        return {
            "id": api_name,
            "name": (ko or {}).get("name") or api_name,
            "nameEn": (en or {}).get("name"),
            "cost": (ko or {}).get("cost"),
            "icon": asset_path((ko or {}).get("tileIcon")),
            "translated": ko is not None,
        }

    def champion(self, api_name):
        return self._remember("champions", self._champion_record(api_name))

    def unit(self, api_name):
        """보드·조합·빌드업의 유닛 하나. 챔피언이면 champions, 소환물이면 pets 로 기록한다."""
        if api_name in self.champions:
            return self.champion(api_name)
        alias = self.aliases.get(api_name)
        if alias in self.champions:
            record = dict(self._champion_record(alias), id=api_name)
            return self._remember("champions", record)
        return self.pet(api_name)

    def pet(self, api_name):
        """
        pet·소환물. 이름은 수기 오버라이드(한글) → lol.qq displayName(중국어 원문) 순.
        아이콘은 오버라이드 → lol.qq originalImage → lol.qq 도감 초상(champions/{chessId}.png)
        → metatft 초상 순. 나무정령 소환물 3종은 originalImage 가 비어 있어 도감 초상만 있다.
        metatft 형태 id(DA_Elderwood18_*)는 lol.qq 도감에 없으므로 오버라이드 sameAs 로
        같은 소환물의 lol.qq id 를 빌린다.
        """
        over = self.overrides.get(api_name) or {}
        qq = self.qq.chess_record(api_name) if self.qq else None
        alias = str(over.get("sameAs") or "").strip()
        if qq is None and alias and self.qq:
            qq = self.qq.chess_record(alias)
        icon = (asset_path(over.get("icon")) or (qq or {}).get("image") or (qq or {}).get("avatar")
                or self._metatft_icon(api_name))
        record = {
            "id": api_name,
            "name": over.get("name") or (qq or {}).get("name_cn") or api_name,
            "nameEn": None,
            "cost": None,
            "icon": icon or None,
            "translated": bool(over.get("name")),
            "kind": "pet",
        }
        return self._remember("pets", record)

    def _metatft_icon(self, api_name):
        """
        metatft 가 그 유닛에 쓰는 초상(사이트가 apiName 소문자로 조립한다).
        한 apiName 을 여러 형태가 함께 쓰면(생명꽃·돌껍질 나무 → TreeSummon) 그림이 한 장뿐이라 쓰지 않는다.
        """
        api = self.metatft_api.get(api_name)
        if not api or len(self.metatft_members.get(api) or []) != 1:
            return None
        return METATFT_CHAMPION_ICON % api.lower()

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

    def remember_tokens(self, text):
        """
        metatft name_string('DA_18_Elderwood, DA_18_Ezreal, DA_Draven18')에 든 특성·유닛을 catalog 에 올린다.
        앱은 우리 목록에 대응 덱이 없는 상대를 이 이름으로 보여 주는데, catalog 에 없는 id 는 원문이 그대로 보인다.
        사전에 없는 토큰은 건너뛴다(소환물로 잘못 등록하지 않도록).
        """
        for token in str(text or "").split(","):
            token = token.strip()
            if token in self.traits:
                self.trait(token)
            elif token in self.champions or token in self.aliases:
                self.unit(token)

    def trait_count(self, api_name, step):
        """metatft 특성 단계 순번(1부터) -> 그 단계의 최소 인원."""
        effects = (self.traits.get(api_name) or {}).get("effects") or []
        if step is None or not 1 <= step <= len(effects):
            return None
        return effects[step - 1].get("minUnits")

    # CommunityDragon effects[].style -> 앱 시너지 색(1 브론즈, 2 실버, 3 골드, 4 프리즘 — lol.qq color 와 같은 체계).
    # 편집 덱 보드의 lol.qq color 1/2/3 이 CDragon 1/3/5 와 짝지어진다(2026-09-16, 135칸). CDragon 4 는 고유 특성(효과 하나, 1명)이다.
    CDRAGON_STYLE_COLOR = {1: 1, 3: 2, 5: 3, 6: 4}

    def trait_style(self, api_name, count):
        """
        인원수로 켜진 단계의 색(1 브론즈 … 4 프리즘). 안 켜졌거나 고유 특성이면 0.
        예전에는 CDragon style 을 그대로 돌려줘 실버(3)가 앱에서 골드로 보였고, 골드(5)·프리즘(6)은
        고유 특성(lol.qq color 5)으로 오인돼 胜率阵容 덱 시너지에서 빠졌다.
        """
        effects = (self.traits.get(api_name) or {}).get("effects") or []
        if len(effects) == 1 and (effects[0].get("minUnits") or 0) <= 1:
            return 0
        style = 0
        for effect in effects:
            low = effect.get("minUnits") or 0
            high = effect.get("maxUnits") or 10 ** 6
            if low <= count <= high:
                style = self.CDRAGON_STYLE_COLOR.get(effect.get("style") or 0, 0)
        return style

    # -- item / augment ------------------------------------------------------
    def item(self, api_name, augment=False):
        """augment=True 면 사전에 없는 id 도 증강으로 분류한다(증강 목록에서 온 id)."""
        key = item_key(api_name)
        ko = self.items.get(key)
        en = self.items_en.get(key)
        record = {
            "id": api_name,
            "name": (ko or {}).get("name") or api_name,
            "nameEn": (en or {}).get("name"),
            "icon": asset_path((ko or {}).get("icon")),
            "components": (ko or {}).get("composition") or [],
            "isAugment": bool((ko or {}).get("isAugment")) or (ko is None and augment),
            "translated": ko is not None,
        }
        return self._remember("augments" if record["isAugment"] else "items", record)


# ----------------------------------------------------------------------------
# lol.qq 편집 덱 파싱
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


def pet_id(entry, dic):
    """
    hero_id 가 빈 pet 칸. chess_id 는 지금 DA id('DA_18_IronbarkTree')지만 숫자 chessId 일 수도
    있어 lol.qq 도감으로 한 번 더 푼다. 도감에 DA id 가 없으면 'QQ_' + 숫자.
    """
    raw = str(entry.get("chess_id") or "").strip()
    if not raw:
        return ""
    record = dic.qq.chess_record(raw) if dic.qq else None
    if record:
        return record["da"]
    return raw if raw.startswith("DA_") else "QQ_" + raw


def build_unit(entry, dic, level3=frozenset()):
    """보드 한 칸을 앱이 쓸 형태로. pet 칸은 kind='pet' 으로 포함한다(예전에는 빠졌다)."""
    hero = str(entry.get("hero_id") or "").strip()
    if hero:
        record = dic.unit(hero)
    else:
        pid = pet_id(entry, dic)
        if not pid:
            return None
        record = dic.pet(pid)
    is_pet = record.get("kind") == "pet"

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
    # 레벨별 보드의 numStar 는 전부 1이라 3성은 level_3_heros 로 다시 매긴다.
    if record["id"] in level3 or hero in level3:
        star = 3

    unit = {
        "id": record["id"],
        "name": record["name"],
        "nameEn": record.get("nameEn"),
        "cost": record.get("cost"),
        "icon": record.get("icon"),
        "star": star,
        "carry": bool(entry.get("is_carry_hero")) and not is_pet,
        "row": position[0] if position else None,
        "col": position[1] if position else None,
        "items": [slim(dic.item(i)) for i in main_ids],
        "itemsBackup": [slim(dic.item(i)) for i in backup_ids],
    }
    if is_pet:
        unit["kind"] = "pet"
    return unit


def build_board(entries, dic, level3=frozenset()):
    units = [build_unit(e, dic, level3) for e in (entries or []) if isinstance(e, dict)]
    return [u for u in units if u]


def pick_carry(units):
    """is_carry_hero가 있으면 그것, 없으면 아이템을 가장 많이 든 고코스트 유닛. pet 은 제외."""
    candidates = [u for u in units if u.get("kind") != "pet"]
    flagged = [u for u in candidates if u["carry"]]
    if flagged:
        return flagged[0]
    with_items = [u for u in candidates if u["items"]]
    if not with_items:
        return candidates[0] if candidates else None
    return max(with_items, key=lambda u: (len(u["items"]), u["cost"] or 0, u["star"]))


def assign_carry_ranks(units, carry_id, ordered_ids=None):
    """
    carryRank 1~3. ordered_ids 가 있으면(胜率阵容의 메인C·보조C·2보조C) 그 순서,
    없으면(편집 덱) 캐리 다음에 아이템 수 → 코스트 순.
    """
    ranked, taken = [], set()

    def take(unit):
        if unit is not None and id(unit) not in taken:
            ranked.append(unit)
            taken.add(id(unit))

    if ordered_ids:
        for uid in ordered_ids:
            take(next((u for u in units if u["id"] == uid and id(u) not in taken), None))
    else:
        take(next((u for u in units if u["id"] == carry_id and u.get("kind") != "pet"), None))
        rest = [u for u in units if id(u) not in taken and u.get("kind") != "pet" and u["items"]]
        rest.sort(key=lambda u: (-len(u["items"]), -(u["cost"] or 0), -u["star"]))
        for unit in rest:
            take(unit)
    for unit in units:
        unit.pop("carryRank", None)
    for rank, unit in enumerate(ranked[:3], start=1):
        unit["carryRank"] = rank


def placement(unit, carry_id):
    """단계 보드 한 칸. 이름/아이콘은 catalog 에서 찾으므로 참조만 남긴다."""
    row = {"id": unit["id"], "star": unit["star"]}
    if unit["row"] is not None:
        row["row"], row["col"] = unit["row"], unit["col"]
    if carry_id and unit["id"] == carry_id and unit.get("kind") != "pet":
        row["carry"] = True
    if unit["items"]:
        row["items"] = [i["id"] for i in unit["items"]]
    if unit.get("kind") == "pet":
        row["kind"] = "pet"
    return row


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
    featured = [t["id"] for t in out if t["_featured"]]
    for row in out:
        del row["_featured"]
    return out, featured


def main_first(traits, main_ids):
    """주특성을 앞으로(안정 정렬). 그룹 덱 이름이 주특성으로 시작하게 한다."""
    return sorted(traits, key=lambda t: t["id"] not in main_ids)


def korean_deck_name(carry, traits, fallback):
    """
    중국어 원문은 '【별명】3특성2특성…' 형태라 별명이 번역되지 않는다.
    특성은 100% 번역되므로 '캐리 · 3 특성 2 특성'으로 새로 만든다.
    """
    composition = " ".join("%d %s" % (t["count"], t["name"]) for t in traits[:4])
    if carry and composition:
        return "%s · %s" % (carry["name"], composition)
    return composition or (carry or {}).get("name") or fallback


TIER_ORDER = {"SS": 0, "S": 1, "A": 2, "B": 3, "C": 4, "D": 5}

EDITORIAL_STAGES = (
    ("early", "초반", "y21_early_heros", "needLevel_early", "early_round"),
    ("mid", "중반", "y21_metaphase_heros", "needLevel_middle", "metaphase_round"),
    ("final", "최종", "hero_location", "needLevel", None),
)
# 예전에 레벨 보드로 쓰던 필드. 실제로는 세 단계 보드의 복사본이다(26/26 확인).
LEGACY_BOARDS = (("hero_location_l6", "y21_early_heros"),
                 ("hero_location_l8", "y21_metaphase_heros"),
                 ("hero_location_l9", "hero_location"))


def parse_cst(text):
    try:
        return datetime.strptime(str(text).strip(), "%Y-%m-%d %H:%M:%S").replace(tzinfo=qq_proxy.CST)
    except (TypeError, ValueError):
        return None


def build_editorial(raw, dic, patch_start):
    """lineup_list 한 항목 -> 편집 덱 작업 객체(그룹에 붙거나 독립 덱이 된다)."""
    # detail은 JSON 문자열 안에 든 JSON이고 제어문자가 섞여 있다.
    detail = json.loads(raw.get("detail") or "{}", strict=False)
    level3 = frozenset(split_ids(detail.get("level_3_heros")))

    final_units = build_board(detail.get("hero_location"), dic, level3)
    traits, featured = build_traits(detail.get("contact"), dic, detail.get("line_name") or "")
    carry = pick_carry(final_units)
    carry_id = (carry or {}).get("id")
    assign_carry_ranks(final_units, carry_id)

    stages = []
    for key, label, field, level_field, round_field in EDITORIAL_STAGES:
        units = final_units if key == "final" else build_board(detail.get(field), dic, level3)
        stages.append({
            "key": key,
            "label": label,
            "level": to_int(detail.get(level_field)),
            "round": (str(detail.get(round_field) or "").strip() or None) if round_field else None,
            "units": [placement(u, carry_id) for u in units],
        })

    def signature(entries):
        return [(e.get("hero_id"), e.get("chess_id"), e.get("location")) for e in entries or []]

    legacy_mismatch = any(detail.get(old) and signature(detail.get(old)) != signature(detail.get(new))
                          for old, new in LEGACY_BOARDS)

    hexbuff = detail.get("hexbuff") or {}
    if not isinstance(hexbuff, dict):
        hexbuff = {}
    author = raw.get("lineupauthor_data") or {}
    tier = (raw.get("quality") or "").upper()
    updated = str(raw.get("update_time") or raw.get("rel_time") or "").strip()

    # 작성자가 쓴 중국어 자유 서술. ID 사전으로 번역되지 않아 원문 그대로 둔다.
    notes = {
        "items": (detail.get("equipment_info") or "").strip(),
        "augments": (detail.get("hex_info") or "").strip(),
    }
    early = (detail.get("early_info") or "").strip()
    level_up = (detail.get("d_time") or "").strip()
    if early:
        notes["early"] = early
    if level_up:
        notes["levelUp"] = level_up

    edited = parse_cst(updated)
    stale = bool(edited and patch_start and edited < patch_start)

    try:
        final_level = int(detail.get("needLevel") or 0) or None
    except (TypeError, ValueError):
        final_level = None

    return {
        "id": str(raw.get("id")),
        "quality": tier,
        "nameCn": detail.get("line_name") or "",
        "patch": raw.get("simulator_edition") or "",
        "finalLevel": final_level,
        "units": final_units,
        "traits": traits,
        "featured": featured,
        "carry": carry,
        "carryId": carry_id,
        "stages": stages,
        "itemOrder": [slim(dic.item(i)) for i in split_ids(detail.get("equipment_order"))],
        "augments": {
            "recommended": [slim(dic.item(i, augment=True)) for i in split_ids(hexbuff.get("recomm"))],
            "alternatives": [slim(dic.item(i, augment=True)) for i in split_ids(hexbuff.get("replace"))],
        },
        "notesCn": notes,
        "author": (author.get("name") or "").strip(),
        "updatedAt": updated,
        "relTime": raw.get("rel_time") or raw.get("update_time") or "",
        "stale": stale,
        "legacyMismatch": legacy_mismatch,
        "matchIds": [u["id"] for u in final_units if u.get("kind") != "pet"],
    }


def editorial_out(editorial, team_code):
    out = {
        "id": editorial["id"],
        # 한 그룹에 편집 덱이 여럿이면 앱이 작가를 바꿔 보여 준다. 그때 원문 섹션의 덱 이름도 그 작가 것이어야 한다.
        "nameCn": editorial["nameCn"],
        "author": editorial["author"],
        "updatedAt": editorial["updatedAt"],
        "stale": editorial["stale"],
        "quality": editorial["quality"],
        "needLevel": editorial["finalLevel"],
        "stages": editorial["stages"],
        "itemOrder": editorial["itemOrder"],
        "augments": editorial["augments"],
        "notesCn": editorial["notesCn"],
        "teamCode": team_code,
    }
    if editorial.get("similarity") is not None:
        out["similarity"] = editorial["similarity"]
    return out


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
# 胜率阵容 그룹 -> 덱
# ----------------------------------------------------------------------------

def unique(seq):
    seen, out = set(), []
    for value in seq:
        if value and value not in seen:
            seen.add(value)
            out.append(value)
    return out


def carrier_items(row, static):
    """조합 행의 캐리 3명(메인C·첫 보조C·2보조C)과 각자의 아이템 id."""
    out = {}

    def put(chess, equips):
        da = static.chess_da(chess)
        items = [str(i).strip() for i in equips or [] if str(i).strip()]
        if da and items and da not in out:
            out[da] = items

    put(row.get("main_c_chess"), row.get("main_c_chess_equip"))
    assists = list(row.get("assist_chess") or [])
    if assists:
        put(assists[0], row.get("assist_chess_equip"))
    put(row.get("second_assist_chess"), row.get("second_assist_chess_equip"))
    return out


def carry_order(row, static):
    ids = [static.chess_da(row.get("main_c_chess"))]
    ids += [static.chess_da(c) for c in row.get("assist_chess") or []]
    ids.append(static.chess_da(row.get("second_assist_chess")))
    return unique(ids)


def w_units(row, das, dic, static):
    """胜率阵容 조합의 보드. 좌표가 없다(앱은 실측 최빈 칸을 쓴다)."""
    equips = carrier_items(row, static)
    main = static.chess_da(row.get("main_c_chess"))
    units = []
    for da in unique(das):
        record = dic.unit(da)
        unit = {
            "id": record["id"],
            "name": record["name"],
            "nameEn": record.get("nameEn"),
            "cost": record.get("cost"),
            "icon": record.get("icon"),
            "star": 1,
            "carry": da == main,
            "row": None,
            "col": None,
            "items": [slim(dic.item(i)) for i in equips.get(da) or []],
            "itemsBackup": [],
        }
        if record.get("kind") == "pet":
            unit["kind"] = "pet"
        units.append(unit)
    units.sort(key=lambda u: (u["cost"] if u["cost"] is not None else 0, u["name"]))
    assign_carry_ranks(units, main, carry_order(row, static))
    return units


def w_traits(row, dic, static, main_ids):
    """주특성+부특성(id, 인원) -> 켜진 시너지. 주특성 먼저, 그다음 인원 많은 순."""
    out, seen = [], set()
    for trait in list(row.get("main_trait_list") or []) + list(row.get("sub_trait_list") or []):
        da = static.trait_da(trait.get("trait_id"))
        count = to_int(trait.get("chess_num"))
        if not da or not count or da in seen:
            continue
        seen.add(da)
        style = dic.trait_style(da, count)
        if not style or style >= UNIQUE_TRAIT_STYLE:
            continue
        entry = slim(dic.trait(da))
        entry["count"] = count
        entry["style"] = style
        out.append(entry)
    out.sort(key=lambda t: (t["id"] not in main_ids, -t["count"], -t["style"], t["name"]))
    return out


def variant_out(variant, representative_id, dic, static):
    bucket = next(b for b in merge.BUCKET_PRIORITY if variant["occ"].get(b))
    occ = merge.best_occurrence(variant, bucket)
    row = occ["row"]
    equips = carrier_items(row, static)
    records = sorted((dic.unit(da) for da in unique(occ["das"])),
                     key=lambda r: (r.get("cost") if r.get("cost") is not None else 0, r["name"]))
    units = []
    for record in records:
        entry = {"id": record["id"]}
        items = equips.get(record["id"])
        if items:
            for item in items:
                dic.item(item)
            entry["items"] = items
        units.append(entry)
    out = {
        "id": variant["id"],
        "representative": variant["id"] == representative_id,
        "units": units,
        "carryId": static.chess_da(row.get("main_c_chess")),
        "assistIds": carry_order(row, static)[1:],
        "stats": merge.variant_stats(variant),
    }
    if variant["editorialIds"]:
        out["editorialId"] = variant["editorialIds"][0]
    if variant["precise"]:
        out["precise"] = variant["precise"]
    return out


class Context:
    def __init__(self, **kw):
        self.__dict__.update(kw)

    def sort_unit_ids(self, ids):
        """빌드업 유닛: catalog 에 등록하면서 코스트 오름차순 → 이름순."""
        records = [self.dic.unit(i) for i in unique(ids)]
        records.sort(key=lambda r: (r.get("cost") if r.get("cost") is not None else 0, r["name"]))
        return [r["id"] for r in records]


def build_group_deck(group, ctx):
    dic, static = ctx.dic, ctx.static
    rep, rep_bucket = merge.representative(group)
    occ = merge.best_occurrence(rep, rep_bucket)
    row = occ["row"]
    main_ids = set(group["traitIds"])

    stats = group["stats"]
    for bucket, block in stats.items():
        precise = merge.group_precise(group, bucket)
        if precise:
            block["precise"] = precise

    w_board = w_units(row, occ["das"], dic, static)
    editorials = group["editorials"]
    editorial = editorials[0] if editorials else None
    if editorial:
        # 카드 보드는 좌표·단계가 있는 편집 덱 보드를 우선한다.
        units, carry = editorial["units"], editorial["carry"]
        traits = main_first(editorial["traits"], main_ids)
    else:
        units = w_board
        carry = next((u for u in units if u.get("carryRank") == 1), None)
        traits = w_traits(row, dic, static, main_ids)
    main_traits = [t for t in traits if t["id"] in main_ids] or traits[:1]

    tier, order = merge.tier_fields(stats, editorial["quality"] if editorial else None)
    team = deck_code(units, ctx.codes, ctx.set_number)

    deck = {
        "id": group["id"],
        "kind": "group",
        "key": group["key"],
        "name": korean_deck_name(carry, traits, group["id"]),
        "nameCn": editorial["nameCn"] if editorial else "",
        "tier": tier,
        "tierOrder": order,
        "patch": ctx.patch,
        "finalLevel": (editorial or {}).get("finalLevel") or occ.get("num"),
        "carryId": (carry or {}).get("id"),
        "mainTraits": main_traits,
        "traits": traits,
        "units": units,
        "stats": stats,
        "variants": [variant_out(v, rep["id"], dic, static)
                     for v in sorted(group["variants"].values(),
                                     key=lambda v: (v["id"] != rep["id"],
                                                    -merge.use_num(v["occ"].get(merge.DEFAULT_BUCKET)),
                                                    -sum(merge.use_num(o) for o in v["occ"].values())))],
        "itemOrder": editorial["itemOrder"] if editorial else [],
        "augments": editorial["augments"] if editorial else {
            # 편집 덱이 없으면 胜率阵容이 이 조합에 붙인 추천 증강 5개를 쓴다.
            "recommended": [slim(dic.item(i, augment=True)) for i in unique(row.get("rune_id_group") or [])],
            "alternatives": [],
        },
        "notesCn": editorial["notesCn"] if editorial else {"items": "", "augments": ""},
        "author": editorial["author"] if editorial else "",
        "updatedAt": ctx.list_date or "",
        "teamCode": team,
    }
    if editorial:
        deck["editorialTier"] = editorial["quality"]
        deck["editorial"] = editorial_out(editorial, deck_code(editorial["units"], ctx.codes, ctx.set_number))
        if len(editorials) > 1:
            deck["moreEditorials"] = [editorial_out(e, deck_code(e["units"], ctx.codes, ctx.set_number))
                                      for e in editorials[1:]]
    work = {"group": group, "rep": rep, "repBucket": rep_bucket, "row": row,
            "wBoard": w_board, "matchUnits": rep["units"], "editorial": editorial,
            "moreEditorials": editorials[1:]}
    return deck, work


def build_editorial_deck(editorial, ctx):
    team = deck_code(editorial["units"], ctx.codes, ctx.set_number)
    main_traits = [t for t in editorial["traits"] if t["id"] in editorial["featured"]] or editorial["traits"][:1]
    deck = {
        "id": editorial["id"],
        "kind": "editorial",
        "name": korean_deck_name(editorial["carry"], editorial["traits"], editorial["id"]),
        "nameCn": editorial["nameCn"],
        "tier": editorial["quality"],
        "tierOrder": TIER_ORDER.get(editorial["quality"], 9),
        "editorialTier": editorial["quality"],
        "patch": editorial["patch"],
        "finalLevel": editorial["finalLevel"],
        "carryId": editorial["carryId"],
        "mainTraits": main_traits,
        "traits": editorial["traits"],
        "units": editorial["units"],
        "stats": {},
        "variants": [],
        "editorial": editorial_out(editorial, team),
        "itemOrder": editorial["itemOrder"],
        "augments": editorial["augments"],
        "notesCn": editorial["notesCn"],
        "author": editorial["author"],
        "updatedAt": editorial["relTime"],
        "teamCode": team,
    }
    work = {"group": None, "matchUnits": None, "editorial": editorial}
    return deck, work


# ----------------------------------------------------------------------------
# metatft 조합 덱(kind=meta): 구간별 metatft 등급이 있는 클러스터 + 거기 합쳐진 lol.qq 그룹(2026-09-19 계약)
# ----------------------------------------------------------------------------

# 대표 유닛이 보드보다 많을 때 남길 수: 가장 흔한 최종 레벨(8~10 사이로 자른다).
GLOBAL_BOARD_MIN = 8
# lol.qq 그룹·편집 독립 덱을 가장 비슷한 조합 덱에 합치는 유사도. 이 이상이면 합치고, SIMILARITY_THRESHOLD(0.5) 이상이면
# 덱 캐리가 그 클러스터 이름(name[] 의 unit 항목)에 있을 때만 합친다. 빌드 표본 상위 캐리는 보지 않는다
# (423009 나무정령 이즈리얼 드레이븐에 장로 드래곤 덱이 붙던 원인).
MERGE_STRONG = 0.7
# 합친 덱의 대표가 아닌 변형(otherVariants) 최대 수.
MERGED_VARIANT_LIMIT = 12


def champion_id(dic, unit_id):
    """
    metatft 유닛 id -> 사전에 있는 챔피언 id. DA id 는 lol.qq 와 같은 체계라 그대로 쓰고(형태 id 는 별칭으로 풀린다),
    'TFT18_Akali' 같은 apiName 은 같은 캐릭터의 DA id 로 옮긴다. 소환물·모르는 id 는 None.
    """
    if unit_id in dic.champions or unit_id in dic.aliases:
        return unit_id
    return next((member for member in dic.metatft_members.get(unit_id) or [] if member in dic.champions), None)


def canonical_unit(dic, space, unit_id):
    """캐리 비교용 id: 형태·럭스 변형을 한 챔피언으로 접는다(매칭과 같은 UnitSpace). 챔피언이 아니면 None."""
    if not unit_id:
        return None
    return space.canonical(champion_id(dic, unit_id) or unit_id)


def meta_deck_ids(clusters, bucket_data):
    """
    조합 덱 id = 'm-' + sha1(name_string 의 id 들을 정렬해 쉼표로 이은 문자열)[:10]. 클러스터 번호는 metatft 가 다시 묶을
    때마다 바뀌어 고정·숨김이 풀리므로 이름 조각으로 만든다(번호는 deck.metaCluster). 같은 이름의 클러스터가 둘 이상이면
    전체 구간 표본이 가장 큰 것만 그대로 두고 나머지는 뒤에 '-{클러스터}' 를 붙인다.
    """
    def base(cluster):
        tokens = sorted(mt.split_ids(cluster["name"])) or [cluster["id"]]
        return "m-" + hashlib.sha1(",".join(tokens).encode("utf-8")).hexdigest()[:10]

    def sample(cluster):
        row = ((bucket_data.get("all") or {}).get("clusters") or {}).get(cluster["id"]) or {}
        return sum(row.get("places") or [])

    out, taken = {}, set()
    for cluster in sorted(clusters, key=lambda c: (-sample(c), to_int(c["id"], 0))):
        did = base(cluster)
        if did in taken:
            did = "%s-%s" % (did, cluster["id"])
        taken.add(did)
        out[cluster["id"]] = did
    return out


def merge_decision(deck, cluster, score, listed, dic, space):
    """
    lol.qq 덱 하나를 가장 비슷한 클러스터(지금 매칭 코드의 값)에 합칠지 -> (합침, 사유).
    합침: 'strong'(유사도 0.7 이상) · 'carry'(0.5 이상 0.7 미만이고 덱 캐리가 클러스터 이름의 유닛).
    중국 한정: 'unmatched'(최고 유사도 0.5 미만) · 'weak'(0.5 이상 0.7 미만인데 캐리가 이름에 없다)
              · 'unlisted'(합칠 만한데 그 클러스터가 조합 덱 목록에 없다).
    """
    if cluster is None or score < SIMILARITY_THRESHOLD:
        return False, "unmatched"
    if score >= MERGE_STRONG:
        reason = "strong"
    else:
        carry = canonical_unit(dic, space, deck.get("carryId"))
        named = {canonical_unit(dic, space, name) for name, kind in cluster["nameParts"] if kind in ("unit", "")}
        if not carry or carry not in named:
            return False, "weak"
        reason = "carry"
    if cluster["id"] not in listed:
        return False, "unlisted"
    return True, reason


def merged_variants(rep_group, groups, dic, static, linked):
    """
    합친 덱의 변형: 대표 그룹의 대표 변형이 대표이고, 나머지는 합쳐진 그룹들의 변형을 같은 id 면 표본 큰 쪽만 남겨
    기본 구간 n 내림차순(같으면 전 구간 n)으로 최대 MERGED_VARIANT_LIMIT 개. 잇지 않은 편집 덱을 가리키는 editorialId 는 뗀다.
    """
    rep, _ = merge.representative(rep_group)

    def size(variant):
        return (merge.use_num(variant["occ"].get(merge.DEFAULT_BUCKET)),
                sum(merge.use_num(o) for o in variant["occ"].values()))

    best = {}
    for group in groups:
        for variant in group["variants"].values():
            if variant["id"] == rep["id"]:
                continue
            current = best.get(variant["id"])
            if current is None or size(variant) > size(current):
                best[variant["id"]] = variant
    others = sorted(best.values(), key=lambda v: (-size(v)[0], -size(v)[1], v["id"]))[:MERGED_VARIANT_LIMIT]
    out = [variant_out(v, rep["id"], dic, static) for v in [rep] + others]
    for row in out:
        if row.get("editorialId") and row["editorialId"] not in linked:
            row.pop("editorialId")
    return out


def build_merged_deck(members, scores, works, ctx):
    """
    한 클러스터에 합쳐진 lol.qq 덱들 -> 조합 덱의 lol.qq 쪽 내용(보드·단계·배치·증강·아이템 착용자·핵심 유닛·cn 빌드업·
    덱 코드·긴 이름은 대표 것). 대표 = 합쳐진 그룹 중 기본 구간 표본이 가장 큰 것(같으면 유사도가 높은 것), 그룹 없이
    편집 독립 덱만 합쳐졌으면 그 덱. 편집 덱은 대표에 있는 것만 잇는다 — 다른 그룹 것을 빌려 오면 카드 보드(대표 보드)와
    편집 최종 보드가 어긋난다. id·kind·등급·수치는 부르는 쪽이 metatft 값으로 채운다.
    반환 (덱, 작업 객체, 잇지 못한 편집 덱 id 들).
    """
    def default_n(deck):
        return ((deck.get("stats") or {}).get(merge.DEFAULT_BUCKET) or {}).get("n") or 0

    def total_n(deck):
        return sum((s or {}).get("n") or 0 for s in (deck.get("stats") or {}).values())

    groups = [d for d in members if d["kind"] == "group"]
    rep = max(groups or members, key=lambda d: (default_n(d), scores[d["id"]], total_n(d), d["id"]))
    rest = sorted((d for d in members if d is not rep), key=lambda d: (-scores[d["id"]], -default_n(d), d["id"]))
    rep_work = works[rep["id"]]

    deck = dict(rep)
    linked = {e["id"] for e in [deck.get("editorial")] + list(deck.get("moreEditorials") or []) if e}
    unlinked = []
    for member in rest:
        member_work = works[member["id"]]
        for editorial in [member_work.get("editorial")] + list(member_work.get("moreEditorials") or []):
            if editorial and editorial["id"] not in linked:
                unlinked.append(editorial["id"])
    if rep_work.get("group"):
        member_groups = [works[d["id"]]["group"] for d in [rep] + rest if works[d["id"]].get("group")]
        deck["variants"] = merged_variants(rep_work["group"], member_groups, ctx.dic, ctx.static, linked)
    if rep.get("stats"):
        # 대표 그룹의 lol.qq 구간 수치(등급 포함) 그대로. 참고용이고 덱 등급·정렬에는 쓰지 않는다.
        deck["cnStats"] = rep["stats"]
    deck["mergedGroups"] = [d["id"] for d in [rep] + rest]
    work = dict(rep_work, members=[(d, works[d["id"]]) for d in rest])
    return deck, work, unlinked


def build_meta_deck(cluster, info, details, ctx, patch_global, updated):
    """
    합쳐진 lol.qq 덱이 없는 조합 덱(예전 전용 덱 빌더 그대로). 보드는 대표 유닛(units_string)이고 좌표가 없다.
    아이템은 comps_data builds 의 유닛별 1순위 3아이템, 캐리 순위는 빌드 표본(아이템 3개를 든 보드 수) 순이되
    metatft 가 덱 이름에 쓴 유닛(name_string)을 앞에 둔다 — 2026-09-16 대상 20개 중 16개는 표본 1위와 같고,
    나머지 넷은 탱커가 표본 1위라 '세트 · 개화' 처럼 metatft 이름(개화 아리)과 어긋났다.
    성급·핵심 유닛·최종 레벨은 comp_details(unit_stats·final_levels)에서 온다. 사전으로 풀리는 유닛이 없으면 (None, None).
    id·등급·수치는 부르는 쪽이 채운다.
    """
    dic = ctx.dic
    did = "m-%s" % cluster["id"]
    usage = mt.unit_usage(details)
    final_level = mt.common_final_level(details)

    builds = [(champion_id(dic, unit), items, sample) for unit, items, sample in mt.unit_builds(info.get("builds"))]
    build_items = {}
    for uid, items, _ in builds:
        if uid and uid not in build_items:
            build_items[uid] = items
    named = [champion_id(dic, token) for token in mt.split_ids(cluster["name"])]
    carry_order = unique([uid for uid in named if uid in build_items] + [uid for uid, _, _ in builds])

    ids = unique(champion_id(dic, uid) for uid in cluster["unitIds"])
    cap = max(GLOBAL_BOARD_MIN, min(mt.GLOBAL_BOARD_MAX, final_level or mt.GLOBAL_BOARD_MAX))
    if len(ids) > cap:
        # 대표 유닛이 보드보다 많으면 아이템 빌드가 있는 유닛, 그다음 채용 보드가 많은 유닛을 남긴다.
        ids.sort(key=lambda uid: (uid not in build_items, -((usage.get(uid) or {}).get("count") or 0)))
        ids = ids[:cap]
    if not ids:
        return None, None

    order = [uid for uid in carry_order if uid in ids]
    carry_id = order[0] if order else None
    units = []
    for uid in ids:
        record = dic.unit(uid)
        units.append({
            "id": record["id"],
            "name": record["name"],
            "nameEn": record.get("nameEn"),
            "cost": record.get("cost"),
            "icon": record.get("icon"),
            "star": (usage.get(uid) or {}).get("star"),
            "carry": uid == carry_id,
            "row": None,
            "col": None,
            "items": [slim(dic.item(i)) for i in build_items.get(uid) or []],
            "itemsBackup": [],
        })
    units.sort(key=lambda u: (u["cost"] if u["cost"] is not None else 0, u["name"]))
    assign_carry_ranks(units, carry_id, order)
    carry = next((u for u in units if u.get("carryRank") == 1), None)

    # 특성은 클러스터 traits_string(단계 순번) -> 인원수. 胜率阵容 덱처럼 켜진 시너지만, 주특성(name_string) 먼저.
    main_ids = {token for token in mt.split_ids(cluster["name"]) if token in dic.traits}
    traits = []
    for row in mt.cluster_traits(cluster["traitsString"], dic.trait_count):
        style = dic.trait_style(row["id"], row["count"])
        if not style or style >= UNIQUE_TRAIT_STYLE:
            continue
        entry = slim(dic.trait(row["id"]))
        entry["count"] = row["count"]
        entry["style"] = style
        traits.append(entry)
    traits.sort(key=lambda t: (t["id"] not in main_ids, -t["count"], -t["style"], t["name"]))
    main_traits = [t for t in traits if t["id"] in main_ids] or traits[:1]

    key_units = []
    for unit, row in sorted(usage.items(), key=lambda pair: -pair[1]["count"]):
        uid = champion_id(dic, unit)
        if not uid or any(k["id"] == uid for k in key_units):
            continue
        dic.unit(uid)
        key_units.append(dict(row["key"], id=uid))
        if len(key_units) >= merge.KEY_UNIT_LIMIT:
            break

    deck = {
        "id": did,
        "kind": "meta",
        "key": "metatft:%s" % cluster["id"],
        "name": korean_deck_name(carry, traits, did),
        "nameCn": "",
        "tier": "",
        "tierOrder": 9,
        "patch": patch_global or ctx.patch,
        "finalLevel": final_level,
        "carryId": (carry or {}).get("id"),
        "mainTraits": main_traits,
        "traits": traits,
        "units": units,
        "stats": {},
        "variants": [],
        "itemOrder": [],
        "augments": {"recommended": [], "alternatives": []},
        "notesCn": {"items": "", "augments": ""},
        "author": "",
        "updatedAt": updated or "",
        "teamCode": deck_code(units, ctx.codes, ctx.set_number),
        "metatft": {"similarity": 1.0, "matchedComp": cluster["name"] or None, "onlyInChina": False, "compared": True},
    }
    if key_units:
        deck["keyUnits"] = key_units
    work = {"group": None, "matchUnits": cluster["units"], "editorial": None}
    return deck, work


# ----------------------------------------------------------------------------
# 중국 한정 덱 등급(halfSA, 2026-09-19 사용자 요구: "절반은 S 절반은 A, 홀수면 A 가 1개 더")
# ----------------------------------------------------------------------------
# lol.qq 胜率阵容 목록은 평균 4등 이내 조합만 올라와 중국 한정 덱은 모두 상위권이다. 그래서 백분위 5칸 대신
# S/A 두 칸으로만 나눈다. 순서는 lol.qq 그룹 전체 모집단의 경험적 베이즈 보정 평균(adjAvg) 그대로다.

CHINA_GRADE_METHOD = "halfSA"


def china_half_grades(decks, grade_cuts):
    """
    중국 한정 덱(kind group·editorial)의 구간 등급을 halfSA 로 다시 매긴다. 구간 b 마다
      대상 E_b = stats[b] 가 있고 n ≥ 그 구간 lol.qq minSample(buckets[b].gradeCuts)인 덱
      E_b 를 adjAvg 오름차순(같으면 n 큰 쪽, 그다음 id)으로 세워 앞 floor(k/2) 개 S, 나머지 ceil(k/2) 개 A
      대상 밖은 grade null.
    그룹 덱의 stats 는 그룹 집계(group["stats"])와 같은 객체라 덱 쪽만 복사해 고친다 — 그룹 쪽 백분위 등급은
    상세 호출 계획(exposure_plan)과 수집 로그의 lol.qq 전체 분포에 그대로 쓴다.
    tier/tierOrder 는 기본 구간 등급(없으면 편집 등급, 둘 다 없으면 빈 문자열 — tier_fields)을 다시 따른다.
    반환 {구간: buckets[b].chinaGrade}.
    """
    for deck in decks:
        deck["stats"] = dict((bucket, dict(stat)) for bucket, stat in (deck.get("stats") or {}).items())
    out = {}
    for bucket, _, _ in merge.BUCKETS:
        min_sample = grade_cuts[bucket]["minSample"]
        eligible = []
        for deck in decks:
            stat = deck["stats"].get(bucket)
            if not stat:
                continue
            if (stat.get("n") or 0) >= min_sample and stat.get("adjAvg") is not None:
                eligible.append(deck)
            else:
                stat["grade"] = None
        eligible.sort(key=lambda d: (d["stats"][bucket]["adjAvg"], -d["stats"][bucket]["n"], d["id"]))
        s_count = len(eligible) // 2
        for rank, deck in enumerate(eligible):
            deck["stats"][bucket]["grade"] = "S" if rank < s_count else "A"
        out[bucket] = {"method": CHINA_GRADE_METHOD, "eligible": len(eligible), "S": s_count,
                       "A": len(eligible) - s_count, "minSample": min_sample}
    for deck in decks:
        deck["tier"], deck["tierOrder"] = merge.tier_fields(deck["stats"], deck.get("editorialTier"))
    return out


# ----------------------------------------------------------------------------
# 별칭(alias)·한 줄 설명(summary): 목록에서 얼굴 없이 글만 읽어도 어떤 덱인지. 규칙 기반이라 매일 같은 값이 나온다.
# ----------------------------------------------------------------------------

LEVELLING_FAST = re.compile(r"^Fast\s*(\d+)$", re.I)


def champion_name(dic, unit_id):
    """사전의 챔피언 이름(형태 id 는 같은 캐릭터로). catalog 에 올리지 않는다(이름만 쓴다)."""
    uid = unit_id if unit_id in dic.champions else dic.aliases.get(unit_id)
    return (dic.champions.get(uid) or {}).get("name") if uid else None


def meta_alias(cluster, dic):
    """조합 이름 조각(name[])을 순서대로 한국어로: 특성은 특성 이름, 유닛은 챔피언 이름. 사전에 없는 id 는 건너뛴다."""
    names = []
    for token, kind in cluster.get("nameParts") or []:
        if kind == "trait" or (not kind and token in dic.traits):
            name = (dic.traits.get(token) or {}).get("name")
        else:
            name = champion_name(dic, token)
        if name and name not in names:
            names.append(name)
    return " ".join(names)


def china_alias(deck, dic):
    """중국 한정 덱: '{대표 시너지} {캐리}'. 대표 시너지는 주특성 중 개수가 가장 큰 것(같으면 앞의 것)."""
    top = None
    for trait in deck.get("mainTraits") or deck.get("traits") or []:
        if top is None or (trait.get("count") or 0) > (top.get("count") or 0):
            top = trait
    carry_id = deck.get("carryId")
    carry = champion_name(dic, carry_id) or next(
        (u.get("name") for u in deck.get("units") or [] if u.get("id") == carry_id), None)
    return " ".join(part for part in ((top or {}).get("name"), carry) if part)


def ranked_carries(deck, dic):
    """carryRank 순 캐리 이름(중복 없이). 순위가 없으면 carryId 하나."""
    ranked = sorted((u for u in deck.get("units") or [] if u.get("carryRank") and u.get("kind") != "pet"),
                    key=lambda u: u["carryRank"])
    names = []
    for unit in ranked:
        name = unit.get("name") or champion_name(dic, unit.get("id"))
        if name and name not in names:
            names.append(name)
    if not names and deck.get("carryId"):
        name = champion_name(dic, deck["carryId"])
        if name:
            names.append(name)
    return names


def operation_text(deck):
    """summary 의 운영: metatft 레벨링(예 '빠른 8레벨', '6레벨 리롤'), 없으면 '{최종 레벨}레벨 완성'."""
    levelling = str((deck.get("global") or {}).get("levelling") or "").strip()
    if levelling:
        return levelling
    level = deck.get("finalLevel")
    return "%d레벨 완성" % level if level else ""


def operation_short(deck):
    """별칭이 겹칠 때 붙이는 짧은 운영: 'Fast 9' → '9레벨', 'lvl 6' → '6레벨 리롤', Reroll → '리롤', 없으면 '{최종 레벨}레벨'."""
    raw = str((deck.get("global") or {}).get("levellingRaw") or "").strip()
    fast = LEVELLING_FAST.match(raw)
    if fast:
        return "%s레벨" % fast.group(1)
    if raw:
        return mt.levelling_ko(raw) or raw
    level = deck.get("finalLevel")
    return "%d레벨" % level if level else ""


def deck_summary(deck, dic):
    """'{운영} · {캐리1}·{캐리2} 캐리'. 캐리는 carryRank 순 상위 2명(1명뿐이면 1명)."""
    parts = []
    operation = operation_text(deck)
    if operation:
        parts.append(operation)
    carries = ranked_carries(deck, dic)[:2]
    if carries:
        parts.append("%s 캐리" % "·".join(carries))
    return " · ".join(parts) or deck.get("name") or deck["id"]


def assign_aliases(decks, clusters_by_id, dic):
    """
    목록 순서대로 alias·summary 를 매긴다. 조합 덱은 조합 이름 조각을 번역하고(비면 중국 한정 규칙), 중국 한정 덱은
    '{대표 시너지} {캐리}'. 같은 별칭이 앞 덱에 있으면 ' · {운영 짧게}', 그래도 겹치면 둘째 캐리(별칭에 아직 없는 다음
    캐리) 이름을 붙이고, 그래도 겹치면(드물다) 번호를 붙인다.
    """
    taken = set()
    for deck in decks:
        cluster = clusters_by_id.get(str(deck.get("metaCluster"))) if deck.get("kind") == "meta" else None
        base = (meta_alias(cluster, dic) if cluster else "") or china_alias(deck, dic) or deck.get("name") or deck["id"]
        operation = operation_short(deck)
        candidates = [base]
        if operation:
            candidates.append("%s · %s" % (base, operation))
        extra = next((name for name in ranked_carries(deck, dic)[1:] if name not in base), None)
        if extra:
            candidates.append("%s %s · %s" % (base, extra, operation) if operation else "%s %s" % (base, extra))
        alias = next((c for c in candidates if c not in taken), None)
        number = 2
        while alias is None:
            numbered = "%s %d" % (candidates[-1], number)
            alias = numbered if numbered not in taken else None
            number += 1
        taken.add(alias)
        deck["alias"] = alias
        deck["summary"] = deck_summary(deck, dic)


def dedupe_names(decks):
    """
    긴 이름('캐리 · 시너지 구성')이 겹치지 않게 한다. lol.qq 이름(중국 한정 덱, 합친 덱의 대표 이름)을 목록 순서대로 먼저
    지키고, 대표 유닛으로 만든 조합 덱 이름이 겹치면 시너지를 하나씩 더 적는다. 그래도 겹치면 별칭을 괄호로 붙인다.
    반환: 바꾼 (id, 옛 이름, 새 이름) 목록.
    """
    def generated(deck):
        return deck.get("kind") == "meta" and not deck.get("mergedGroups")

    taken, changed = set(), []
    for deck in sorted(decks, key=generated):
        name = deck["name"]
        if name in taken:
            carry = next((u for u in deck.get("units") or [] if u.get("id") == deck.get("carryId")), None)
            traits = deck.get("traits") or []
            candidates = []
            if carry:
                # korean_deck_name 은 시너지 4개까지만 적으므로 5개부터 직접 잇는다.
                candidates = ["%s · %s" % (carry["name"], " ".join("%d %s" % (t["count"], t["name"]) for t in traits[:size]))
                              for size in range(5, len(traits) + 1)]
            candidates.append("%s (%s)" % (name, deck.get("alias") or deck["id"]))
            new = next((c for c in candidates if c not in taken), None) or "%s (%s)" % (name, deck["id"])
            changed.append((deck["id"], name, new))
            deck["name"] = name = new
        taken.add(name)
    return changed


# ----------------------------------------------------------------------------
# 胜率阵容 상세
# ----------------------------------------------------------------------------

DETAIL_ALIASES = ("tft_lineup_all_detail", "tft_lineup_position", "tft_lineup_key_chess")


def fetch_details(plan, proxy, budget):
    """
    노출 그룹의 상세를 예산 안에서 받는다. 한 그룹씩 세 개를 다 받기보다 all_detail(증강·
    레벨 분포·착용자·빌드업)을 먼저 모든 그룹에, 남는 예산으로 position → key_chess 를
    받는 편이 더 많은 덱에 핵심 정보를 싣는다.
    반환: ({gid: {alias: data}}, {bucket: dtstatdate}, 실패 수)
    """
    tier_parts = {key: part for key, _, part in merge.BUCKETS}
    results, dates, failures = {}, {}, 0
    start = proxy.calls
    for alias in DETAIL_ALIASES:
        for group, bucket in plan:
            if proxy.calls - start >= budget:
                break
            got = results.setdefault(group["id"], {})
            if alias != DETAIL_ALIASES[0] and DETAIL_ALIASES[0] not in got:
                continue  # 요약이 비었던 조합은 파라미터가 맞지 않는 것이라 나머지도 건너뛴다
            variant = max((v for v in group["variants"].values() if v["occ"].get(bucket)),
                          key=lambda v: merge.use_num(v["occ"][bucket]))
            row = merge.best_occurrence(variant, bucket)["row"]
            try:
                data = proxy.lineup_detail(alias, qq_proxy.detail_params(row, tier_parts[bucket]))
            except SourceError as exc:
                failures += 1
                warn("상세 %s %s(%s): %s" % (alias, group["id"], bucket, exc))
                continue
            got[alias] = data
            got["bucket"] = bucket
            if data.get("dtstatdate"):
                dates.setdefault(bucket, str(data["dtstatdate"]))
    return results, dates, failures


def apply_details(deck, work, got, ctx):
    detail = got.get(DETAIL_ALIASES[0])
    if not detail:
        return
    dic, static = ctx.dic, ctx.static
    bucket = got["bucket"]
    deck["detailBucket"] = bucket

    def item_ref(item_id):
        return slim(dic.item(item_id))

    def augment_ref(item_id):
        return slim(dic.item(item_id, augment=True))

    def unit_name(da):
        return dic.unit(da)["name"]

    deck["augmentStats"] = merge.augment_stats(detail, augment_ref)
    deck["levelDist"] = merge.level_dist(detail)
    deck["itemWearers"] = merge.item_wearers(detail, static, item_ref, unit_name)

    chess_rows = (got.get(DETAIL_ALIASES[2]) or {}).get("key_champion_data") or detail.get("key_champion_data")
    deck["keyUnits"] = merge.key_units(chess_rows, static)
    for unit in deck["keyUnits"]:
        dic.unit(unit["id"])

    # 좌표 없는 胜率阵容 보드는 별도 알 수 없어 1로 뒀다. 핵심 유닛의 최빈 성급으로 채운다.
    if not work.get("editorial"):
        stars = {k["id"]: k for k in deck["keyUnits"]}
        for unit in deck["units"]:
            info = stars.get(unit["id"])
            if info:
                shares = [(info.get("star%d" % s) or 0, s) for s in (1, 2, 3)]
                unit["star"] = max(shares)[1]

    cn = merge.buildup_cn(detail, static, bucket, ctx.sort_unit_ids)
    if cn:
        work["buildupCn"] = cn

    position = got.get(DETAIL_ALIASES[1])
    if position:
        cells = merge.positions(position, static)
        if cells:
            deck["positions"] = cells


# ----------------------------------------------------------------------------
# metatft 대조
# ----------------------------------------------------------------------------

def metatft_clusters(cluster_blob, space):
    """클러스터마다 (id, 정규화 유닛 집합, 표시 이름)."""
    details = (cluster_blob.get("cluster_info") or {}).get("cluster_details") or {}
    comps = []
    for cluster in details.get("clusters") or []:
        raw = [u.strip() for u in (cluster.get("units_string") or "").split(",") if u.strip()]
        units = space.normalize(raw)
        if units:
            name = (cluster.get("name_string") or "").strip()
            # 조합 이름 조각(name[]: 특성·유닛 순서와 종류). 없으면 name_string 을 종류 없이 쓴다.
            parts = [(str(p.get("name") or "").strip(), str(p.get("type") or "").strip())
                     for p in cluster.get("name") or [] if isinstance(p, dict) and p.get("name")]
            comps.append({"id": str(cluster.get("Cluster") or cluster.get("cluster") or ""),
                          "units": units, "name": name,
                          "nameParts": parts or [(token, "") for token in mt.split_ids(name)],
                          # 대표 유닛·특성 원문. 합쳐진 lol.qq 덱이 없는 조합 덱의 보드를 만들 때 쓴다.
                          "unitIds": raw, "traitsString": (cluster.get("traits_string") or "").strip()})
    return comps


def unit_lookup(cluster_blob):
    details = (cluster_blob.get("cluster_info") or {}).get("cluster_details") or {}
    return details.get("unit_lookup") or {}


def best_cluster(units, clusters):
    """
    덱의 정체성은 챔피언 ID 집합이다.
    이름(중국어 vs 영어)이 아니라 구성으로 비교해야 의미가 있다.
    """
    best, score = None, 0.0
    for cluster in clusters:
        value = merge.jaccard(units, cluster["units"])
        if value > score:
            best, score = cluster, value
    return best, score


# ----------------------------------------------------------------------------
# 검색 역인덱스
# ----------------------------------------------------------------------------

def build_index(decks, works):
    """
    앱은 계산하지 않고 조회만 한다. 아이템->덱 검색이 이 앱의 핵심 기능이라
    수집 단계에서 미리 만들어 둔다. 이름 키 5축(v1)과 DA id 키(byId)를 함께 만든다.
    인덱스 대상은 카드 보드(편집 덱이 붙었으면 그 보드) + 胜率阵容 대표 조합이다. pet 은 뺀다.
    """
    items, components, champions, traits, augments = {}, {}, {}, {}, {}
    by_id = {"champion": {}, "item": {}, "trait": {}, "augment": {}}
    item_seen = set()

    def add(bucket, key, value):
        if key:
            bucket.setdefault(key, []).append(value)

    def add_unit(did, unit):
        if unit.get("kind") == "pet":
            return
        add(champions, unit["name"], did)
        add(by_id["champion"], unit["id"], did)
        for role, field in (("main", "items"), ("backup", "itemsBackup")):
            for item in unit.get(field) or []:
                key = (did, unit["name"], item["name"], role)
                if key in item_seen:
                    continue
                item_seen.add(key)
                add(items, item["name"], {"deck": did, "unit": unit["name"], "role": role})
                add(by_id["item"], item["id"], did)

    for deck in decks:
        did = deck["id"]
        for unit in deck["units"]:
            add_unit(did, unit)
        work = works.get(did) or {}
        if work.get("editorial") and work.get("wBoard"):
            for unit in work["wBoard"]:
                add_unit(did, unit)
        for item in deck["itemOrder"]:
            add(components, item["name"], did)
        for trait in deck["traits"]:
            add(traits, trait["name"], did)
            add(by_id["trait"], trait["id"], did)
        for augment in deck["augments"]["recommended"]:
            add(augments, augment["name"], did)
            add(by_id["augment"], augment["id"], did)
        # 같은 그룹에 붙은 다른 작가의 편집 덱(moreEditorials)도 이 덱 id 로 찾게 한다.
        # 앱 상세에서 작가를 바꿔 그 보드·증강을 볼 수 있다.
        for extra in work.get("moreEditorials") or []:
            for unit in extra["units"]:
                add_unit(did, unit)
            for item in extra["itemOrder"]:
                add(components, item["name"], did)
            for trait in extra["traits"]:
                add(traits, trait["name"], did)
                add(by_id["trait"], trait["id"], did)
            for augment in extra["augments"]["recommended"]:
                add(augments, augment["name"], did)
                add(by_id["augment"], augment["id"], did)
        # 조합 덱에 합쳐진 다른 lol.qq 그룹(대표가 아닌 것)의 보드·시너지·증강도 이 조합 덱으로 찾게 한다.
        # 그 그룹들은 목록에서 따로 보이지 않고 이 덱의 변형으로 들어온다.
        for member, member_work in work.get("members") or []:
            for unit in member["units"]:
                add_unit(did, unit)
            for unit in member_work.get("wBoard") or []:
                add_unit(did, unit)
            for item in member["itemOrder"]:
                add(components, item["name"], did)
            for trait in member["traits"]:
                add(traits, trait["name"], did)
                add(by_id["trait"], trait["id"], did)
            for augment in member["augments"]["recommended"]:
                add(augments, augment["name"], did)
                add(by_id["augment"], augment["id"], did)

    # 챔피언/특성은 덱 중복 제거
    champions = {k: sorted(set(v)) for k, v in champions.items()}
    traits = {k: sorted(set(v)) for k, v in traits.items()}
    components = {k: sorted(set(v)) for k, v in components.items()}
    augments = {k: sorted(set(v)) for k, v in augments.items()}
    by_id = {axis: {k: sorted(set(v)) for k, v in rows.items()} for axis, rows in by_id.items()}

    return {
        "item": items,
        "component": components,
        "champion": champions,
        "trait": traits,
        "augment": augments,
        "byId": by_id,
    }


def build_catalog(dic):
    """
    검색 자동완성과 참조 해석에 쓰는 목록.
    단계 보드·조합·빌드업을 ID 참조로 줄였기 때문에 앱은 여기서 이름과 아이콘을 찾는다.
    """
    def finish(bucket):
        rows = []
        for record in sorted(bucket.values(), key=lambda r: (r["name"], r["id"])):
            row = {"id": record["id"], "name": record["name"]}
            if record.get("nameEn") and record["nameEn"] != record["name"]:
                row["nameEn"] = record["nameEn"]
            if record.get("icon"):
                row["icon"] = record["icon"]
            if record.get("cost") is not None:
                row["cost"] = record["cost"]
            if record.get("components"):
                row["components"] = record["components"]
            if record.get("kind"):
                row["kind"] = record["kind"]
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


def read_overrides():
    try:
        with open(OVERRIDES, encoding="utf-8") as fp:
            return json.load(fp)
    except OSError:
        return {}
    except ValueError as exc:
        warn("overrides_ko.json 을 읽지 못했다(수기 이름 없이 진행): %s" % exc)
        return {}


def write_output(payload, snapshot=False):
    os.makedirs(OUT_DIR, exist_ok=True)

    body = json.dumps(payload, ensure_ascii=False, separators=(",", ":"), sort_keys=True)
    digest = hashlib.sha256(body.encode("utf-8")).hexdigest()[:16]
    payload["version"]["contentHash"] = digest

    # 줄바꿈을 LF 로 고정한다(Windows 에서 돌려도 CI 결과와 같은 바이트).
    path = os.path.join(OUT_DIR, "decks.json")
    with open(path, "w", encoding="utf-8", newline="\n") as fp:
        json.dump(payload, fp, ensure_ascii=False, separators=(",", ":"), sort_keys=True)

    # 앱은 이 작은 파일만 먼저 받아 갱신 여부를 판단한다.
    with open(os.path.join(OUT_DIR, "version.json"), "w", encoding="utf-8", newline="\n") as fp:
        json.dump(payload["version"], fp, ensure_ascii=False, indent=2, sort_keys=True)

    if snapshot:
        # 앱에 동봉하는 첫 실행용 스냅샷. 네트워크 없이도 목록이 비지 않게 한다.
        shutil.copyfile(path, SNAPSHOT)
    return digest


def yyyymmdd_to_iso(value):
    text = str(value or "").strip()
    if len(text) == 8 and text.isdigit():
        return "%s-%s-%s" % (text[:4], text[4:6], text[6:])
    return text or None


def parse_args(argv):
    parser = argparse.ArgumentParser(description="lol.qq + metatft 덱 데이터를 decks.json(v2)으로 만든다")
    parser.add_argument("--snapshot", action="store_true",
                        help="android/app/src/main/assets/decks.json 에도 복사한다")
    parser.add_argument("--detail-limit", type=int, default=DETAIL_CALL_LIMIT,
                        help="胜率阵容 상세 호출 상한 (기본 %d)" % DETAIL_CALL_LIMIT)
    parser.add_argument("--comp-limit", type=int, default=COMP_DETAIL_LIMIT,
                        help="metatft comp_details 를 받을 클러스터 수 상한 (기본 %d)" % COMP_DETAIL_LIMIT)
    return parser.parse_args(argv)


def main(argv=None):
    args = parse_args(sys.argv[1:] if argv is None else argv)
    started = datetime.now(timezone.utc)
    status = {}
    diag = {"empty": []}

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
        warn("CurrentSet 확인 실패, 이전 값 %s 사용: %s" % (set_id, exc))

    set_number = re.sub(r"\D", "", set_id) or "18"
    log("시즌: %s (세트 %s)" % (set_id, set_number))

    # --- 편집 덱 원본 --------------------------------------------------------
    raw_decks = []
    try:
        lineup = fetch_json(LINEUP_TMPL.format(set_id=set_id))
        raw_decks = lineup.get("lineup_list") or []
        status["lolqq"] = "ok" if raw_decks else "missing"
        if not raw_decks:
            diag["empty"].append("lineup_detail_total")
            warn("편집 덱 목록이 비었다. 스키마가 바뀌었을 수 있다.")
    except SourceError as exc:
        status["lolqq"] = "missing"
        warn("lol.qq 편집 덱을 가져오지 못했다: %s" % exc)
    log("편집 덱 원본: %d개" % len(raw_decks))

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
        warn("영문 사전 생략(검색 별칭만 줄어든다): %s" % exc)

    dic = Dictionary(ko, en, set_number, read_overrides())

    # --- 덱 코드 (없어도 진행) ----------------------------------------------
    try:
        codes = team_planner_codes(set_number)
        status["teamPlanner"] = "ok"
        log("팀 플래너 코드: %d개 챔피언" % len(codes))
    except SourceError as exc:
        codes = {}
        status["teamPlanner"] = "missing"
        warn("덱 코드 생성 생략: %s" % exc)

    # --- lol.qq 빌드 버전 ----------------------------------------------------
    proxy = qq_proxy.QQProxy(UA, error_cls=SourceError)
    versions, qq_build, patch, patch_start = [], None, None, None
    try:
        versions = proxy.recent_versions()
        qq_build = qq_proxy.latest_build(versions)
        patch = ".".join(qq_build.split(".")[:2]) if qq_build else None
    except SourceError as exc:
        warn("lol.qq 빌드 목록 생략: %s" % exc)
    if not patch:
        patch = next((str(r.get("simulator_edition")) for r in raw_decks if r.get("simulator_edition")), None)
    if patch:
        patch_start = qq_proxy.patch_start(versions, patch)
    log("lol.qq 빌드 %s · 패치 %s · 시작 %s" % (qq_build, patch, patch_start))
    patch_start_dt = datetime.fromisoformat(patch_start) if patch_start else None

    # --- lol.qq 정적 도감 ----------------------------------------------------
    static = None
    try:
        static = qq_static.load(fetch_text, set_id, patch=patch, log=log)
        dic.link_qq(static)
        status["lolqqStatic"] = "ok"
    except (SourceError, ValueError, KeyError, TypeError) as exc:
        status["lolqqStatic"] = "missing"
        warn("lol.qq 정적 도감을 받지 못해 胜率阵容을 쓸 수 없다: %s" % exc)

    # --- 胜率阵容 목록 5구간 --------------------------------------------------
    lists, list_dates, list_counts = {}, {}, {}
    if static:
        for bucket, _, part in merge.BUCKETS:
            try:
                data = proxy.group_list(part)
            except SourceError as exc:
                diag["empty"].append("tft_lineup_group_list:%s" % bucket)
                warn("胜率阵容 %s 목록 실패: %s" % (bucket, exc))
                continue
            lists[bucket] = data
            list_dates[bucket] = str(data.get("dtstatdate") or "")
            groups_src = data.get("main_traits_data") or []
            list_counts[bucket] = {"groups": len(groups_src),
                                   "variants": sum(len((g.get("info") or {}).get("list") or []) for g in groups_src)}
        status["lolqqWinrate"] = "ok" if len(lists) == len(merge.BUCKETS) else "missing"
    else:
        status["lolqqWinrate"] = "missing"

    # --- 数据检索器 ----------------------------------------------------------
    stime, etime = qq_proxy.datasearch_window()
    overviews, rank_rows = {}, {}
    for scope, tier in qq_proxy.DATASEARCH_TIERS:
        try:
            overviews[scope] = proxy.match_overview(tier, stime, etime)
            rank_rows[scope] = proxy.lineup_rank(tier, stime, etime)
        except SourceError as exc:
            diag["empty"].append("datasearch:%s" % scope)
            warn("数据检索器 %s 실패: %s" % (scope, exc))
    status["lolqqDatasearch"] = "ok" if len(rank_rows) == len(qq_proxy.DATASEARCH_TIERS) else "missing"

    # --- metatft (없어도 진행) ---------------------------------------------
    clusters, cluster_blob, meta_version = [], {}, {}
    try:
        cluster_blob = fetch_json(META_CLUSTER, timeout=90)
        meta_version = fetch_json(META_VERSION, timeout=30)
        dic.set_aliases(unit_lookup(cluster_blob))
        status["metatft"] = "ok"
    except SourceError as exc:
        status["metatft"] = "missing"
        warn("metatft 대조 생략. 중국 한정 배지는 표시하지 않는다: %s" % exc)

    scope_data, comps_info, bucket_data = {}, {}, {}
    if status["metatft"] == "ok":
        for key, _, rank, server in mt.SCOPES:
            try:
                scope_data[key] = mt.fetch_scope(fetch_json, rank, server, SourceError)
            except SourceError as exc:
                diag["empty"].append("comps_stats:%s" % key)
                warn("metatft %s 통계 실패: %s" % (key, exc))
        # 조합 덱의 구간별 수치·등급: 앱 구간마다 전 지역 comps_stats 를 한 번씩.
        bucket_data, failed_buckets = mt.fetch_buckets(fetch_json, SourceError, warn)
        for bucket in failed_buckets:
            diag["empty"].append("comps_stats:%s" % bucket)
        try:
            comps_info = mt.fetch_comps_data(fetch_json, SourceError)
        except SourceError as exc:
            diag["empty"].append("comps_data")
            warn("metatft comps_data 실패: %s" % exc)
    status["metatftStats"] = "ok" if len(scope_data) == len(mt.SCOPES) and comps_info else "missing"
    status["metatftBuckets"] = "ok" if len(bucket_data) == len(mt.BUCKET_RANKS) else "missing"
    patch_global = None
    try:
        patch_global = mt.fetch_patch(fetch_json)
    except SourceError as exc:
        warn("metatft 패치 이름 생략: %s" % exc)

    space = merge.UnitSpace(dic.champions.keys(), dic.aliases)
    if status["metatft"] == "ok":
        clusters = metatft_clusters(cluster_blob, space)
        log("metatft 클러스터: %d개" % len(clusters))

    ctx = Context(dic=dic, static=static, codes=codes, set_number=set_number, patch=patch or "",
                  list_date=yyyymmdd_to_iso(list_dates.get(merge.DEFAULT_BUCKET)))

    # --- 편집 덱 변환 -------------------------------------------------------
    editorials, failed = [], 0
    for raw in raw_decks:
        try:
            editorial = build_editorial(raw, dic, patch_start_dt)
        except (ValueError, KeyError, TypeError) as exc:
            failed += 1
            warn("덱 %s 파싱 실패: %s" % (raw.get("id"), exc))
            continue
        editorial["normUnits"] = space.normalize(editorial["matchIds"])
        editorials.append(editorial)
    legacy = sum(1 for e in editorials if e["legacyMismatch"])
    if legacy:
        warn("hero_location_l6/l8/l9 가 단계 보드와 다른 편집 덱 %d개(쓰지 않는 필드라 경고만)" % legacy)

    # --- 그룹 병합 ---------------------------------------------------------
    join = {}
    groups = merge.build_groups(lists, static, space, join) if static else {}
    # 등급 기준은 구간마다 그 구간 그룹 분포로 잡는다: 모든 그룹 집계 → 구간 기준 → 등급.
    aggregates = {gid: merge.group_aggregates(group) for gid, group in groups.items()}
    grade_cuts = merge.grade_cuts(aggregates)
    for gid, group in groups.items():
        group["stats"] = merge.group_stats(aggregates[gid], grade_cuts)

    precise, dropped = [], {}
    for scope, rows in rank_rows.items():
        kept, lost = merge.precise_rows(rows, scope, space)
        precise.extend(kept)
        dropped[scope] = lost
    precise_matched = merge.attach_precise(groups, precise)

    standalone = merge.attach_editorials(groups, editorials)
    attached = len(editorials) - len(standalone)

    # --- lol.qq 덱(그룹 · 편집 독립) ---------------------------------------------
    # lol.qq 등급·수치는 지금처럼 그룹 전체 분포로 매긴다. 조합 덱에 합쳐지는 그룹도 이 모집단에 든다.
    qq_decks, works = [], {}
    for group in groups.values():
        if not group["variants"]:
            continue
        deck, work = build_group_deck(group, ctx)
        qq_decks.append(deck)
        works[deck["id"]] = work
    for editorial in standalone:
        deck, work = build_editorial_deck(editorial, ctx)
        qq_decks.append(deck)
        works[deck["id"]] = work

    if not qq_decks:
        print("[치명] 만들어진 덱이 없다.", file=sys.stderr)
        return 1

    # --- metatft 조합 덱 목록: 어느 한 구간에서라도 metatft 등급이 있는 클러스터 ------------------
    # 수치는 구간별 comps_stats, 등급은 사이트 고정 컷(반올림 전 평균 등수, '<'), 추세는 comps_data trends(구간 공통).
    meta_stats = {}
    for cluster in clusters:
        change = mt.trend_change((comps_info.get(cluster["id"]) or {}).get("trends"))
        stats = mt.cluster_bucket_stats(cluster["id"], bucket_data, change, merge.trend_for)
        if any(stat.get("grade") for stat in stats.values()):
            meta_stats[cluster["id"]] = stats
    # 구간 수치를 하나도 못 받았으면 조합 덱을 만들 수 없고, '중국 한정'이라 단정하지도 않는다.
    compared = bool(clusters) and bool(bucket_data)

    # --- lol.qq 덱을 가장 비슷한 조합 덱에 합치기 --------------------------------------------
    scores, best_of, reasons, members = {}, {}, {}, {}
    for deck in qq_decks:
        work = works[deck["id"]]
        units = work["matchUnits"] if work.get("matchUnits") is not None else work["editorial"]["normUnits"]
        cluster, score = best_cluster(units, clusters) if units else (None, 0.0)
        merged, reason = merge_decision(deck, cluster, score, meta_stats, dic, space)
        scores[deck["id"]], best_of[deck["id"]], reasons[deck["id"]] = score, cluster, reason
        if merged:
            members.setdefault(cluster["id"], []).append(deck)

    # --- metatft comp_details(최종 레벨·상대 덱·빌드업·성급·핵심 유닛): 목록의 클러스터 전부 -------
    listed = [c for c in clusters if c["id"] in meta_stats]
    cluster_set = meta_version.get("cluster_id") or next(
        (s.get("clusterSet") for s in list(bucket_data.values()) + list(scope_data.values()) if s.get("clusterSet")),
        None)
    wanted = [c["id"] for c in listed]
    if len(wanted) > args.comp_limit:
        warn("comp_details 상한 %d 에 걸려 %d클러스터는 상세 없이 싣는다" % (args.comp_limit, len(wanted) - args.comp_limit))
    comp_results = {}
    if cluster_set:
        for cid in wanted[:max(0, args.comp_limit)]:
            try:
                comp_results[cid] = mt.fetch_comp_details(fetch_json, cid, cluster_set, SourceError)
            except SourceError as exc:
                warn("metatft comp_details %s 실패: %s" % (cid, exc))

    # --- 덱 목록 = metatft 조합 덱 + 중국 한정 덱 --------------------------------------------
    meta_ids = meta_deck_ids(listed, bucket_data)
    scope_updated = (scope_data.get(mt.GLOBAL_SCOPE) or {}).get("updatedAt")
    decks, deck_for_cluster, unlinked_editorials = [], {}, []
    for cluster in listed:
        cid = cluster["id"]
        if members.get(cid):
            deck, work, unlinked = build_merged_deck(members[cid], scores, works, ctx)
            unlinked_editorials.extend(unlinked)
        else:
            deck, work = build_meta_deck(cluster, comps_info.get(cid) or {}, comp_results.get(cid), ctx, patch_global,
                                         scope_updated)
            if deck is None:
                warn("metatft 조합 덱 %s: 사전으로 풀리는 대표 유닛이 없어 뺀다" % cid)
                continue
        stats = meta_stats[cid]
        # tier: 기본 구간 metatft 등급, 없으면 가장 좋은 구간 등급.
        tier = (stats.get(merge.DEFAULT_BUCKET) or {}).get("grade") or min(
            (stat["grade"] for stat in stats.values() if stat.get("grade")), key=lambda g: merge.GRADE_ORDER[g])
        deck.update({
            "id": meta_ids[cid],
            "kind": "meta",
            "key": "metatft:%s" % cid,
            "metaCluster": to_int(cid),
            "stats": stats,
            "tier": tier,
            "tierOrder": merge.GRADE_ORDER[tier],
            "metatft": {"similarity": 1.0, "matchedComp": cluster["name"] or None, "onlyInChina": False,
                        "compared": True},
        })
        decks.append(deck)
        works[deck["id"]] = work
        deck_for_cluster[cid] = deck["id"]
    meta_decks = list(decks)
    merged_decks = [d for d in meta_decks if d.get("mergedGroups")]
    merged_ids = {member for d in merged_decks for member in d["mergedGroups"]}

    # 중국 한정 덱: 합쳐지지 않은 lol.qq 그룹·편집 독립 덱 전부. id·등급·보드는 그대로 두고 metatft 정보는 떼어 낸다.
    china_log = []
    for deck in qq_decks:
        if deck["id"] in merged_ids:
            continue
        cluster = best_of[deck["id"]]
        deck["metatft"] = {"similarity": round(scores[deck["id"]], 3), "matchedComp": None,
                           "onlyInChina": compared, "compared": compared}
        decks.append(deck)
        china_log.append({"id": deck["id"], "similarity": round(scores[deck["id"]], 3), "reason": reasons[deck["id"]],
                          "cluster": to_int(cluster["id"]) if cluster else None})
    # 중국 한정 덱 등급은 halfSA(구간마다 대상의 앞 절반 S · 나머지 A). lol.qq 백분위 기준(gradeCuts)은 adjAvg 근거로 남는다.
    china_grades = china_half_grades([d for d in decks if d["kind"] != "meta"], grade_cuts)

    # --- 상세(증강·배치): 새 목록 기준 — 중국 한정 그룹 + 합친 덱의 대표 그룹 -----------------------
    owner = {}
    for deck in decks:
        group = works[deck["id"]].get("group")
        if group:
            owner[group["id"]] = deck
    plan = merge.exposure_plan([works[d["id"]]["group"] for d in decks if works[d["id"]].get("group")])
    detail_start = proxy.calls
    got, detail_dates, detail_failures = fetch_details(plan, proxy, max(0, args.detail_limit))
    detail_calls = proxy.calls - detail_start
    for group, _ in plan:
        if group["id"] in got:
            deck = owner[group["id"]]
            apply_details(deck, works[deck["id"]], got[group["id"]], ctx)
    with_detail = [d for d in decks if d.get("detailBucket")]
    status["lolqqDetail"] = "ok" if plan and len(with_detail) >= len(plan) / 2.0 else "missing"

    # 실측 배치의 축 해석 검사(설계 §4.6). 편집 덱 최종 좌표와 실측 최빈 칸의 일치율로 y 방향을
    # 고른다. 2026-09-15 실측: row = y 로 두면 1/164, row = 5 - y 로 뒤집으면 117/164 —
    # lol.qq 는 y 를 편집 보드와 반대쪽 줄부터 센다. 어느 해석도 40% 에 못 미치면 싣지 않는다.
    pairs = [(works[d["id"]]["editorial"]["units"], d["positions"])
             for d in decks if d.get("positions") and works[d["id"]].get("editorial")]
    agreement = merge.position_agreement(pairs)
    position_mapping = merge.choose_position_mapping(agreement)
    if position_mapping == "rowFlipped":
        for deck in decks:
            for cells in (deck.get("positions") or {}).values():
                for cell in cells:
                    cell["row"] = 5 - cell["row"]
    elif position_mapping is None:
        warn("실측 배치 방향 검사 실패(그대로 %d/%d, 행 뒤집으면 %d/%d) — positions 를 싣지 않는다"
             % (agreement["asIs"][0], agreement["asIs"][1],
                agreement["rowFlipped"][0], agreement["rowFlipped"][1]))
        for deck in decks:
            deck.pop("positions", None)
    matches, compared_cells = agreement[position_mapping or "asIs"]
    positions_kept = position_mapping is not None

    # 정렬: 등급(tierOrder) → 같은 등급이면 조합 덱 먼저(두 출처의 평균 등수는 척도가 달라 섞지 않는다)
    # → 기본 구간 보정 평균 → 이름.
    def list_order(deck):
        adj = ((deck.get("stats") or {}).get(merge.DEFAULT_BUCKET) or {}).get("adjAvg")
        return deck["tierOrder"], deck["kind"] != "meta", adj if adj is not None else 9.0, deck["name"]

    decks.sort(key=list_order)

    # --- 글로벌 비교 · 상대 덱 · 빌드업 · 출처 ---------------------------------------------
    by_cluster = {c["id"]: c for c in clusters}
    names = {c["id"]: c["name"] for c in clusters if c.get("name")}
    buildups = {cid: mt.buildup_global(res, cid, ctx.sort_unit_ids, dic.trait_count)
                for cid, res in comp_results.items()}
    for deck in decks:
        work = works[deck["id"]]
        cid = str(deck["metaCluster"]) if deck["kind"] == "meta" else None
        global_block = None
        if cid:
            info = comps_info.get(cid) or {}
            global_block = {
                "cluster": to_int(cid),
                "similarity": 1.0,
                "name": by_cluster[cid]["name"],
                "levelling": mt.levelling_ko(info.get("levelling")),
                "levellingRaw": info.get("levelling"),
                "difficulty": mt.difficulty_ko(info.get("difficulty")),
                "difficultyRaw": info.get("difficulty"),
                "stats": {},
            }
            for key, _, _, _ in mt.SCOPES:
                row = (scope_data.get(key) or {}).get("clusters", {}).get(cid)
                summary = mt.summarize(row["places"], row["count"]) if row else None
                if summary:
                    global_block["stats"][key] = summary
            details = comp_results.get(cid)
            if details:
                global_block["finalLevels"] = mt.final_levels(details)
                # 상대 덱은 목록의 조합 덱으로만 옮겨 간다(목록에 없는 클러스터는 뺀다).
                global_block["counters"] = mt.counters(details, cid, deck_for_cluster, names)
                for counter in global_block["counters"]:
                    dic.remember_tokens(counter.get("name"))
            deck["global"] = global_block
        else:
            deck.pop("global", None)

        buildup = {}
        if cid and buildups.get(cid):
            buildup["global"] = buildups[cid]
        if work.get("buildupCn"):
            buildup["cn"] = work["buildupCn"]
        if buildup:
            deck["buildup"] = buildup
        else:
            deck.pop("buildup", None)

        editorial = work.get("editorial")
        kr = ((global_block or {}).get("stats") or {}).get("kr_plat") or {}
        deck["sources"] = {
            "editorial": bool(editorial),
            "editorialStale": bool(editorial and editorial["stale"]),
            "cnStats": bool(deck.get("cnStats") if deck["kind"] == "meta" else deck.get("stats")),
            "global": bool(global_block),
            "kr": (kr.get("n") or 0) >= KR_MIN_SAMPLE,
            "onlyInChina": deck["metatft"]["onlyInChina"],
        }

    # 목록에서 얼굴 없이 글만 읽어도 알아보게: 별칭·한 줄 설명(규칙 기반이라 매일 같은 값).
    assign_aliases(decks, by_cluster, dic)
    for did, old, new in dedupe_names(decks):
        log("덱 이름 겹침: %s '%s' → '%s'" % (did, old, new))

    # --- 인덱스 · 카탈로그 · 버전 ------------------------------------------
    index = build_index(decks, works)
    catalog = build_catalog(dic)
    missing = untranslated_ids(dic)
    only_china = [d for d in decks if d["metatft"]["onlyInChina"]]
    coded = sum(1 for d in decks if d["teamCode"])
    linked_editorials = sum((1 if d.get("editorial") else 0) + len(d.get("moreEditorials") or []) for d in decks)

    buckets = {}
    for key, label, part in merge.BUCKETS:
        if key not in lists and key not in bucket_data:
            continue
        meta = {
            "label": label,
            "qqTierPart": part,
            "listDate": list_dates.get(key) or None,
            "detailDate": detail_dates.get(key),
            "groups": sum(1 for g in groups.values() if key in g["stats"]),
            "variants": sum(1 for g in groups.values() for v in g["variants"].values() if v["occ"].get(key)),
            # lol.qq 기준(표본 문턱·보정 강도·백분위 컷). 모든 lol.qq 수치의 adjAvg 와 조합 덱 cnStats 등급(참고용)이
            # 이 기준이다. 중국 한정 덱 등급은 chinaGrade(대상 절반 S · 나머지 A)다.
            "gradeCuts": grade_cuts[key],
            "chinaGrade": china_grades[key],
        }
        if key in bucket_data:
            # metatft 조합 덱 등급 기준(사이트 고정 컷)과 그 구간 요청(rank)·전체 보드·filter_adjustment 기록.
            meta["metaGradeCuts"] = mt.meta_grade_cuts(key, bucket_data[key])
        if key == merge.DEFAULT_BUCKET:
            meta["default"] = True
        buckets[key] = meta

    scopes = {}
    for key, label, _, _ in mt.SCOPES:
        data = scope_data.get(key)
        if data:
            scopes[key] = {"label": label, "source": "metatft", "days": mt.DAYS,
                           "boards": data["boards"], "updatedAt": data["updatedAt"]}
    cn_labels = {"cn_plat": "중국 플래티넘+", "cn_master": "중국 마스터+"}
    for key, tier in qq_proxy.DATASEARCH_TIERS:
        overview = overviews.get(key)
        if overview:
            scopes[key] = {"label": cn_labels[key], "source": "lolqq", "days": 3, "tier": tier,
                           "games": to_int(overview.get("total_games")), "statDate": etime, "build": ""}

    champion_total = join.get("champions", 0)
    trait_total = join.get("traits", 0)
    all_detail_ok = sum(1 for g in got.values() if g.get(DETAIL_ALIASES[0]))
    diag.update({
        "editorialRows": len(raw_decks),
        "editorialParsed": len(editorials),
        "editorialAttached": attached,
        # 조합 덱에 합쳐진 그룹 중 대표가 아닌 그룹의 편집 덱. 빌려 오지 않아 앱에서 닿지 않는다.
        "editorialUnlinked": sorted(unlinked_editorials),
        "legacyBoardMismatch": legacy,
        "winrate": list_counts,
        "groups": sum(1 for d in decks if d["kind"] == "group"),
        "metaComps": len(meta_decks),
        "metaMerged": len(merged_decks),
        "globalOnly": 0,
        "lineupRankRows": {scope: len(rows) for scope, rows in rank_rows.items()},
        "lineupRankDropped": dropped,
        "preciseMatched": precise_matched,
        "clusters": len(clusters),
        "scopeMeanAvg": {key: mt.scope_mean(data) for key, data in scope_data.items()},
        "metatftMatched": len(meta_decks),
        # 모든 lol.qq 그룹·편집 독립 덱이 정확히 한 덱(조합 덱 mergedGroups 또는 중국 한정 덱)에 있는지 검증이 본다.
        "merge": {
            "lolqqDecks": sorted(d["id"] for d in qq_decks),
            "merged": {d["id"]: d["mergedGroups"] for d in merged_decks},
            "chinaOnly": china_log,
        },
        "metaBuckets": {key: {"ranks": data.get("ranks"), "boards": data.get("boards"),
                              "filterAdjustment": data.get("filterAdjustment"),
                              "graded": sum(1 for stats in meta_stats.values() if (stats.get(key) or {}).get("grade"))}
                        for key, data in bucket_data.items()},
        "compDetails": {"clusters": len(wanted), "fetched": len(comp_results), "limit": args.comp_limit,
                        "overrideApplied": sum(1 for r in comp_results.values()
                                               if (r.get("_filterAdjustment") or {}).get("override_applied"))},
        "detail": {
            "exposed": len(plan), "calls": detail_calls, "limit": args.detail_limit, "failures": detail_failures,
            "allDetail": all_detail_ok,
            "position": sum(1 for g in got.values() if g.get(DETAIL_ALIASES[1])),
            "keyChess": sum(1 for g in got.values() if g.get(DETAIL_ALIASES[2])),
            "withAugments": sum(1 for d in decks if d.get("augmentStats")),
        },
        "join": {
            "champions": round(1 - join.get("championsMissing", 0) / float(champion_total), 4) if champion_total else None,
            "traits": round(1 - join.get("traitsMissing", 0) / float(trait_total), 4) if trait_total else None,
        },
        "positions": {"asIs": agreement["asIs"], "rowFlipped": agreement["rowFlipped"],
                      "mapping": position_mapping, "kept": positions_kept},
    })

    payload = {
        "version": {
            "schemaVersion": SCHEMA_VERSION,
            "generatedAt": started.strftime("%Y-%m-%dT%H:%M:%SZ"),
            "set": set_id,
            "setNumber": int(set_number),
            "patch": patch or (decks[0].get("patch") or ""),
            "patchGlobal": patch_global,
            "qqBuild": qq_build,
            "qqPatchStart": patch_start,
            "statDate": yyyymmdd_to_iso(list_dates.get(merge.DEFAULT_BUCKET)),
            "deckCount": len(decks),
            # 앱에서 닿는 편집 덱 수(덱의 editorial + moreEditorials). 수집한 수는 collector.editorialParsed.
            "editorialCount": linked_editorials,
            "onlyInChinaCount": len(only_china),
            # 예전 metatft 전용 덱(kind=global) 수. 조합 덱(kind=meta)으로 바뀌어 0 이다.
            "globalOnlyCount": 0,
            # metatft 조합 덱(kind=meta) 수. 그중 lol.qq 그룹이 합쳐진 덱 수는 collector.metaMerged.
            "metaCompCount": len(meta_decks),
            "teamCodeCount": coded,
            "metatftSet": meta_version.get("tft_set"),
            "metatftClusterId": meta_version.get("cluster_id"),
            # 아이콘 경로는 상대 경로로 저장한다. 앱이 여기에 이어 붙인다.
            "assetBase": CDRAGON_ASSET,
            "sources": status,
            "untranslatedIds": missing,
            "contentHash": "",
        },
        "buckets": buckets,
        "scopes": scopes,
        # 앱 호환 자리(구간별 기준을 모르는 앱이 읽는다): 기본 구간 lol.qq 기준. 구간별 기준은 buckets[b].gradeCuts,
        # 조합 덱 기준은 buckets[b].metaGradeCuts.
        "gradeCuts": dict(grade_cuts[merge.DEFAULT_BUCKET]),
        "decks": decks,
        "index": index,
        "catalog": catalog,
        "collector": diag,
    }

    digest = write_output(payload, snapshot=args.snapshot)

    # --- 요약 ---------------------------------------------------------------
    log("-" * 64)
    for key, meta in buckets.items():
        src = list_counts.get(key) or {}
        log("구간 %-7s 원본 그룹 %3d · 조합 %3d → 통합 그룹 %3d · 변형 %3d · 목록 %s · 상세 %s"
            % (key, src.get("groups", 0), src.get("variants", 0), meta["groups"], meta["variants"],
               meta["listDate"], meta["detailDate"]))
        cuts = meta["gradeCuts"]
        grades = [g["stats"][key]["grade"] for g in groups.values() if key in g["stats"]]
        log("        lol.qq 전체 그룹 %s S≤%.2f A≤%.2f B≤%.2f C≤%.2f · 표본≥%d · K %d · 수축 %.3f → 등급 %d/%d %s"
            % (cuts["method"], cuts["S"], cuts["A"], cuts["B"], cuts["C"], cuts["minSample"], cuts["shrinkK"],
               cuts["shrinkTo"],
               sum(1 for g in grades if g), len(grades), " ".join("%s%d" % (x, grades.count(x)) for x in "SABCD")))
        china = meta["chinaGrade"]
        log("        중국 한정 %s: 대상 %d(표본≥%d) → S%d A%d"
            % (china["method"], china["eligible"], china["minSample"], china["S"], china["A"]))
        meta_cuts = meta.get("metaGradeCuts")
        if meta_cuts:
            meta_grades = [s[key]["grade"] for s in meta_stats.values() if key in s]
            log("        metatft rank=%s · 보드 %s · filter_adjustment %s · 표본≥%d · 로비당≥%.2f → 등급 %d/%d %s"
                % (meta_cuts["ranks"] or "(전체)", meta_cuts["boards"], meta_cuts["filterAdjustment"],
                   meta_cuts["minSample"], meta_cuts["minPlayrate"], sum(1 for g in meta_grades if g),
                   len(meta_grades), " ".join("%s%d" % (x, meta_grades.count(x)) for x in "SABCD")))
    log("덱 %d개 = metatft 조합 %d(lol.qq 합침 %d) + 중국 한정 %d(그룹 %d · 편집 독립 %d)"
        % (len(decks), len(meta_decks), len(merged_decks), len(decks) - len(meta_decks), diag["groups"],
           sum(1 for d in decks if d["kind"] == "editorial")))
    log("편집 덱 %d개: 그룹 첨부 %d · 앱 연결 %d · 잇지 않음 %s · 파싱 실패 %d"
        % (len(editorials), attached, linked_editorials, unlinked_editorials or "-", failed))
    log("중국 한정: %s" % ", ".join("%s(%s %.3f)" % (row["id"], row["reason"], row["similarity"]) for row in china_log))
    log("数据检索器 행 %s · 범위 검사로 버림 %s · 변형에 붙은 행 %d"
        % (diag["lineupRankRows"], dropped, precise_matched))
    log("metatft 클러스터 %d · 조합 덱 %d · comp_details %d/%d · 스코프 평균 %s"
        % (len(clusters), len(meta_decks), len(comp_results), len(wanted), diag["scopeMeanAvg"]))
    log("상세: 노출 %d그룹 · 호출 %d회(상한 %d, 실패 %d) · all_detail %d · position %d · key_chess %d · 증강 보유 %d"
        % (len(plan), detail_calls, args.detail_limit, detail_failures, all_detail_ok,
           diag["detail"]["position"], diag["detail"]["keyChess"], diag["detail"]["withAugments"]))
    log("실측 배치 방향: %s 일치 %d/%d → %s" % (position_mapping or "-", matches, compared_cells,
                                              "싣는다" if positions_kept else "뺀다"))
    log("DA 조인율: 챔피언 %s · 특성 %s" % (diag["join"]["champions"], diag["join"]["traits"]))
    log("빌드업: global %d덱 · cn %d덱" % (sum(1 for d in decks if (d.get("buildup") or {}).get("global")),
                                        sum(1 for d in decks if (d.get("buildup") or {}).get("cn"))))
    log("중국 한정 %d개%s · 덱 코드 %d개" % (len(only_china), "" if compared else " (대조 생략)", coded))
    if missing:
        log("미번역 ID %d개: %s" % (len(missing), ", ".join(missing[:10])))
    size = os.path.getsize(os.path.join(OUT_DIR, "decks.json"))
    log("decks.json %.1f KB · hash %s%s" % (size / 1024.0, digest, " · 스냅샷 복사" if args.snapshot else ""))
    if size > MAX_FILE_BYTES:
        warn("decks.json 이 %.1f KB 로 상한 %d KB 를 넘는다" % (size / 1024.0, MAX_FILE_BYTES // 1024))
    log("lol.qq 호출 %d회 · 소요 %.0f초" % (proxy.calls, (datetime.now(timezone.utc) - started).total_seconds()))
    return 0


if __name__ == "__main__":
    sys.exit(main())
