# -*- coding: utf-8 -*-
"""
CommunityDragon 한국어·영어 사전.

한글 이름의 1순위다(Riot 공식 ko_KR). 세 원본(lol.qq, metatft, CDragon)이 같은 DA_* apiName을
쓰므로 정확 일치로 붙인다. 옛 세트 접두사(TFT11_Augment_…)로만 남은 증강은 접두사를 뗀 키로
한 번 더 찾는다(DA_Reinfourcement ↔ TFT11_Augment_Reinfourcement).
"""

import re

from . import clean_text, fmt_number, stat_words

KO_URL = "https://raw.communitydragon.org/latest/cdragon/tft/ko_kr.json"
EN_URL = "https://raw.communitydragon.org/latest/cdragon/tft/en_us.json"
ASSET_BASE = "https://raw.communitydragon.org/latest/game/"

# CDragon effects.style → 앱 계약의 등급 색(1 브론즈, 2 실버, 3 골드, 4 프리즘, 5 고유).
# CDragon 값은 1/3/5/6(+고유 4)이라 그대로 쓰면 lolchess·lol.qq 칩과 뜻이 어긋난다.
# 세트 18 전 특성 90단계를 lol.qq race/job.js color_list와 대조해 89단계 일치를 확인했다.
STYLE_TO_TIER = {1: 1, 2: 1, 3: 2, 4: 5, 5: 3, 6: 4}
UNIQUE_TIER = 5

_PREFIX = re.compile(r"^(TFT\d*_(Item_|Augment_)?|DA_(\d+_)?)", re.I)
_PLACEHOLDER = re.compile(r"@([^@\s]+?)@")
_ICON_RUN = re.compile(r"(?:%i:[A-Za-z]+%)+")
_ICON_NAME = re.compile(r"%i:([A-Za-z]+)%")
_SCALED = re.compile(r"^(.*?)\*(\d+(?:\.\d+)?)$")
_ROW = re.compile(r"<row>(.*?)</row>", re.S | re.I)
_EXPAND_ROW = re.compile(r"<expandRow>(.*?)</expandRow>", re.S | re.I)
_SECTION_START = re.compile(r"<row>|<expandRow>", re.I)


def asset_path(path):
    """.tex 경로를 png 상대 경로로. 접두사(assetBase)는 앱이 붙인다."""
    if not path or path == "None":
        return None
    return re.sub(r"\.(tex|dds)$", ".png", str(path).lower())


def normalized_key(api_name):
    return _PREFIX.sub("", api_name or "").lower()


def fnv1a32(name):
    """
    CDragon은 이름을 모르는 bin 필드를 '{aa3fad66}'처럼 해시로 적는다. 해시는 소문자 이름의
    FNV-1a 32비트다(세트 18에서 TeamwideRatio·HunterAD 등 10개로 확인). 설명문의 @TeamwideRatio@를
    이 해시로 찾으면 라이브 수치를 그대로 쓸 수 있다.
    """
    value = 0x811C9DC5
    for byte in str(name).lower().encode("utf-8"):
        value ^= byte
        value = (value * 0x01000193) & 0xFFFFFFFF
    return "{%08x}" % value


def _variable_value(value):
    """숫자면 그대로, 성급별 배열이면 1~3성을 '100/300/480'로."""
    if isinstance(value, bool):
        return None
    if isinstance(value, (int, float)):
        return value
    if isinstance(value, list) and len(value) >= 4:
        stars = [v for v in value[1:4] if isinstance(v, (int, float))]
        if len(stars) == 3:
            return stars
    return None


def render(text, variables=None, min_units=None, icon_words=False):
    """
    CDragon 설명문의 @Var@ / @Var*100@ 을 variables 값으로 바꾼다.
    %i:scaleAP% 같은 아이콘 토큰은 icon_words면 능력치 이름으로, 아니면 지운다(스킬 설명의
    '(%i:scaleAP%)'는 주문력 비례 표시일 뿐이라 지운다). 풀지 못한 자리표시자는 지우고 개수를 센다
    (자리표시자가 그대로 보이는 것보다 수치가 빠진 문장이 읽힌다).
    돌려주는 값: (정리된 문자열, 풀지 못한 자리표시자 수)
    """
    if not text:
        return "", 0

    lookup = {}
    if isinstance(variables, dict):
        for key, value in variables.items():
            lookup[str(key).lower()] = value
    elif isinstance(variables, list):
        for entry in variables:
            if isinstance(entry, dict) and entry.get("name"):
                lookup[str(entry["name"]).lower()] = entry.get("value")
    missing = [0]

    def substitute(match):
        token, scale = match.group(1), 1.0
        scaled = _SCALED.match(token)
        if scaled:
            token, scale = scaled.group(1), float(scaled.group(2))
        if token.lower() == "minunits" and min_units is not None:
            return str(min_units)
        value = _variable_value(lookup.get(token.lower()))
        if value is None:
            value = _variable_value(lookup.get(fnv1a32(token)))
        if value is None:
            missing[0] += 1
            return ""
        if isinstance(value, list):
            return "/".join(fmt_number(v * scale) for v in value)
        return fmt_number(value * scale)

    def icons(match):
        words = stat_words(_ICON_NAME.findall(match.group(0))) if icon_words else ""
        return " " + words if words else ""

    out = _ICON_RUN.sub(icons, str(text))
    out = _PLACEHOLDER.sub(substitute, out)
    return clean_text(out), missing[0]


def trait_sections(desc):
    """
    특성 설명을 (소개, 단계별 행 목록, 모든 단계 공통 행)으로 나눈다.
    CDragon은 '소개<br><row>(@MinUnits@) …</row>…' 모양이고 행 수가 effects 수와 같다.
    """
    if not desc:
        return "", [], None
    intro = _SECTION_START.split(desc, maxsplit=1)[0]
    rows = _ROW.findall(desc)
    expand = _EXPAND_ROW.findall(desc)
    return intro, rows, (expand[0] if expand else None)


def _pick_set(blob, set_number):
    sets = blob.get("sets") or {}
    if str(set_number) in sets:
        return sets[str(set_number)]
    numeric = sorted((k for k in sets if k.replace(".", "").isdigit()), key=float)
    return sets[numeric[-1]] if numeric else {}


def _pick_set_data(blob, set_number):
    """setData에는 세트별 아이템·증강 apiName 목록이 있다(같은 번호가 여럿이면 뮤테이터로 고른다)."""
    entries = [s for s in (blob.get("setData") or []) if str(s.get("number")) == str(set_number)]
    for entry in entries:
        if entry.get("mutator") == "TFTSet%s" % set_number:
            return entry
    return entries[0] if entries else {}


class Dictionary:
    """CDragon ko/en 사전을 apiName으로 찾는다."""

    def __init__(self, ko, en, set_number):
        self.set_number = str(set_number)
        ko_set = _pick_set(ko, set_number)
        en_set = _pick_set(en or {}, set_number)
        self.set_data = _pick_set_data(ko, set_number)

        self.champions = {c["apiName"]: c for c in ko_set.get("champions") or [] if c.get("apiName")}
        self.champions_en = {c["apiName"]: c for c in en_set.get("champions") or [] if c.get("apiName")}
        self.traits = {t["apiName"]: t for t in ko_set.get("traits") or [] if t.get("apiName")}
        self.traits_en = {t["apiName"]: t for t in en_set.get("traits") or [] if t.get("apiName")}
        # 챔피언의 traits는 apiName이 아니라 한국어 이름 목록이다.
        self.trait_by_name = {t["name"]: api for api, t in self.traits.items() if t.get("name")}

        self.items = {}
        for entry in ko.get("items") or []:
            if entry.get("apiName"):
                self.items.setdefault(entry["apiName"], entry)
        self.items_en = {}
        for entry in (en or {}).get("items") or []:
            if entry.get("apiName"):
                self.items_en.setdefault(entry["apiName"], entry)

        # 접두사를 뗀 키 → 후보들. 같은 키가 여러 세트에 있으면 최신 세트 번호를 고른다.
        self._normalized = {}
        for api, entry in self.items.items():
            if not entry.get("name"):
                continue
            key = (normalized_key(api), bool(entry.get("isAugment")))
            self._normalized.setdefault(key, []).append(api)

    # -- 챔피언 --------------------------------------------------------------
    def playable_champions(self):
        """
        상점에 나오는 챔피언만. CDragon 세트 목록에는 정글 몬스터·무기고 열쇠(TFT_*)와
        훈련 봇까지 cost 1 이상으로 섞여 있다. 실제 챔피언은 DA_* 이고 특성이 있다.
        """
        out = []
        for api, champ in self.champions.items():
            try:
                cost = int(champ.get("cost") or 0)
            except (TypeError, ValueError):
                continue
            if api.startswith("DA_") and 1 <= cost <= 7 and champ.get("traits"):
                out.append(api)
        return out

    def champion_en_name(self, api):
        return (self.champions_en.get(api) or {}).get("name")

    # -- 특성 ----------------------------------------------------------------
    def trait_en_name(self, api):
        return (self.traits_en.get(api) or {}).get("name")

    # -- 아이템·증강 -----------------------------------------------------------
    def item(self, api):
        return self.items.get(api)

    def item_en_name(self, api):
        entry = self.items_en.get(api)
        if entry and entry.get("name"):
            return entry["name"]
        match = self.normalized_item(api, augment=None, table=self.items_en)
        return (match or {}).get("name")

    def normalized_item(self, api, augment, table=None):
        """정확 일치가 없을 때만 쓰는 보조 조회. 증강/아이템을 섞지 않는다(ForceOfNature는 둘 다 있다)."""
        key = normalized_key(api)
        candidates = []
        flags = (True, False) if augment is None else (bool(augment),)
        for flag in flags:
            candidates.extend(self._normalized.get((key, flag)) or [])
        if not candidates:
            return None
        best = max(candidates, key=_set_rank)
        if table is not None:
            return table.get(best)
        return self.items.get(best)

    def set_augment_ids(self):
        return [a for a in self.set_data.get("augments") or [] if str(a).startswith("DA_")]


def _set_rank(api):
    """DA_* 를 가장 우선하고, 그다음 TFT 세트 번호가 큰 것."""
    if api.startswith("DA_"):
        return 1000
    found = re.match(r"^TFT(\d+)", api)
    return int(found.group(1)) if found else 0
