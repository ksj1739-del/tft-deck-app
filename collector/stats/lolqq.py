# -*- coding: utf-8 -*-
"""
lol.qq(중국 서버) 数据检索器·특성 추세·정적 사전.

数据检索器 규칙(설계 §3.2, 전부 실측):
- tier를 반드시 준다("4+" 플래+, "7+" 마스터+). ""는 전 행이 모순이다.
- version은 ""(전 빌드 합산). "16.18" 같은 짧은 문자열은 200 빈 응답이다.
- 기간은 최대 3일, 날짜는 CST. 수집 시각(05:00 KST = 04:00 CST)의 당일은 집계 중이라
  etime = CST 전날, stime = etime − 2일로 완결된 사흘을 받는다.
- 분모는 unit_s/trait_s/build_s(그 항목이 있는 보드 수). 서버의 tier 등급과
  avg_rank_delta는 쓰지 않는다(수집기가 다시 계산).

정적 사전은 WP-1의 qq_static.py를 import하지 않고 같은 URL을 여기서 따로 읽는다
(두 파이프라인이 서로의 코드 변경에 끌려가지 않게).
"""

import re
from datetime import timedelta, timezone

from .net import HttpError, SourceError

CST = timezone(timedelta(hours=8))

# (키, 라벨, tier)
SCOPES = (
    ("cn_plat", "중국 플래티넘+", "4+"),
    ("cn_master", "중국 마스터+", "7+"),
)
EQUIP_SCOPE = "cn_plat"

VERSIONCONFIG_URL = "https://game.gtimg.cn/images/lol/tfth5lib/v1/versionconfig.json"
STATIC_ROOT = "https://game.gtimg.cn/images/lol/act/img/tft/js/"
STATIC_FILES = (
    ("chess", "urlChessData"),
    ("race", "urlRaceData"),
    ("job", "urlJobData"),
    ("hex", "urlBuffData"),
    ("equip", "urlEquipData"),
)
_FOLDER = re.compile(r"/js/(\d+\.\d+)-(\d{4}\.S\d+)/")

PRO_STATUS = {"增强": "buff", "削弱": "nerf", "最新": "new"}


def to_int(value):
    try:
        return int(float(value))
    except (TypeError, ValueError):
        return None


def to_float(value):
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def stat_window(now_utc, days=3):
    """(stime, etime) — CST 기준 전날까지 days일."""
    end = now_utc.astimezone(CST).date() - timedelta(days=1)
    start = end - timedelta(days=days - 1)
    return start.isoformat(), end.isoformat()


def base_params(tier, stime, etime):
    return {
        "queueId": '["1100"]',
        "version": "",
        "stime": stime,
        "etime": etime,
        "tier": tier,
        "level": "",
        "threeStarCount": "",
        "artifactCount": "",
        "emblemCount": "",
        "radiantCount": "",
        "filterOptions": "",
        "minSampleSize": "10",
    }


def range_ok(n, top1, top4, avg, tolerance=0.001):
    """
    평균 등수가 1등·톱4 비율로 가능한 구간 안인지.
    1등은 1, 2~4등은 최소 2·최대 4, 5~8등은 최소 5·최대 8이므로
    lo = p1 + 2(p4−p1) + 5(1−p4) ≤ avg ≤ p1 + 4(p4−p1) + 8(1−p4).
    이 검사가 lol.qq 不限 버킷의 모순 행을 100% 걸러냈다(설계 §4.6). avg_rank가 소수 셋째
    자리 반올림이라 경계에서 tolerance만큼 봐준다.
    """
    if not n or n <= 0 or top1 is None or top4 is None or avg is None:
        return False
    p1 = top1 / float(n)
    p4 = top4 / float(n)
    if p1 < 0 or p4 < p1 - 1e-9 or p4 > 1 + 1e-9:
        return False
    low = p1 + 2 * (p4 - p1) + 5 * (1 - p4)
    high = p1 + 4 * (p4 - p1) + 8 * (1 - p4)
    return low - tolerance <= avg <= high + tolerance


# ----------------------------------------------------------------------------
# 호출
# ----------------------------------------------------------------------------

def fetch_recent_versions(client):
    data = client.proxy("tft_recent_versions", {"env": "0"})
    rows = data.get("list") if isinstance(data, dict) else None
    if not rows:
        raise SourceError("tft_recent_versions: list가 비었다")
    return rows


def fetch_scope(client, tier, stime, etime):
    """한 스코프의 경기 수·챔피언·특성. 하나라도 비면 스코프 전체를 missing으로 본다."""
    base = base_params(tier, stime, etime)
    overview = client.proxy("tft_match_overview", dict(base))
    hero = client.proxy("tft_hero_rank", dict(base, limit="2000", unitType=""))
    trait = client.proxy("tft_trait_rank", dict(base, limit="2000"))
    if not isinstance(overview, list) or not isinstance(hero, list) or not isinstance(trait, list):
        raise SourceError("数据检索器 %s: 형식이 다르다" % tier)
    return {"overview": overview[0], "hero": hero, "trait": trait}


def fetch_equip(client, stime, etime):
    """
    챔피언#아이템 행렬(인기도만 신뢰)과 아이템 단독 표. 둘 다 플래+(4+)만 받는다.
    limit 8000은 현재 약 7,900행을 모두 덮는다(최소 build_s가 10보다 크면 잘린 것).
    """
    base = base_params("4+", stime, etime)
    by_hero = client.proxy("tft_equip_rank", dict(base, ShowHero="1", limit="8000"))
    by_item = client.proxy("tft_equip_rank", dict(base, ShowHero="", limit="2000"))
    return {"hero": by_hero, "item": by_item}


def fetch_trait_trend(client):
    data = client.proxy("tft_trait_strength_trend", {"tier_part": "255", "battletype": "1100"})
    if not isinstance(data, dict) or not data.get("main_buff_data"):
        raise SourceError("tft_trait_strength_trend: main_buff_data가 비었다")
    return data


def fetch_static(client, qq_patch, set_id, log):
    """
    chess/race/job/hex/equip.js. versionconfig.json이 한 패치 늦은 폴더(16.17-2026.S18)를
    가리키는 일이 있어 현재 패치 폴더(16.18-…)를 먼저 시도하고, 404면 versionconfig 경로로 간다.
    돌려주는 값: {이름: blob} (실패한 파일은 빠진다)
    """
    entry = None
    try:
        config = client.get_json(VERSIONCONFIG_URL)
        seasons = config if isinstance(config, list) else []
        entry = next((s for s in seasons if s.get("idSeason") == set_id), None) or (seasons[0] if seasons else None)
    except SourceError as exc:
        log("[경고] versionconfig.json 실패(현재 패치 폴더만 시도): %s" % exc)

    out = {}
    for name, key in STATIC_FILES:
        original = (entry or {}).get(key)
        candidates = []
        if original and qq_patch:
            candidates.append(_FOLDER.sub(lambda m: "/js/%s-%s/" % (qq_patch, m.group(2)), original, count=1))
        if original:
            candidates.append(original)
        if not candidates and qq_patch and set_id:
            season = re.sub(r"\D", "", set_id)
            candidates.append("%s%s-%s.S%s/%s.js" % (STATIC_ROOT, qq_patch, _season_year(), season, name))
        seen = set()
        for url in candidates:
            if url in seen:
                continue
            seen.add(url)
            try:
                blob = client.get_json(url)
                if not blob.get("data"):
                    raise SourceError("%s: data가 비었다" % url)
                out[name] = blob
                break
            except HttpError as exc:
                if exc.code != 404:
                    log("[경고] lol.qq %s.js 실패: %s" % (name, exc))
                    break
            except SourceError as exc:
                log("[경고] lol.qq %s.js 실패: %s" % (name, exc))
                break
        if name not in out:
            log("[경고] lol.qq %s.js를 받지 못했다" % name)
    return out


def _season_year():
    from datetime import datetime
    return datetime.now(CST).year


# ----------------------------------------------------------------------------
# 정적 사전 해석
# ----------------------------------------------------------------------------

def trait_id_map(race_blob, job_blob):
    """
    숫자 traitId → (DA id, 'origin'|'class', {인원수: 색}).
    特质(race)=계열, 职业(job)=직업. 색은 1 브론즈 … 4 프리즘, 5 고유.
    """
    out = {}
    for blob, id_key, kind, color_key in ((race_blob, "raceId", "origin", "race_color_list"),
                                         (job_blob, "jobId", "class", "job_color_list")):
        for row in (blob or {}).get("data") or []:
            da = row.get("characterid") or ""
            if not da.startswith("DA_"):
                continue
            colors = {}
            for part in str(row.get(color_key) or "").split(","):
                count, _, color = part.partition(":")
                if to_int(count) is not None and to_int(color) is not None:
                    colors[to_int(count)] = to_int(color)
            for key in (row.get(id_key), row.get("traitId"), row.get("TFTID")):
                if key:
                    out[str(key)] = (da, kind, colors)
    return out


def chess_map(chess_blob):
    """DA id → {변경 상태, 성급 배율}. 성급 배율은 체력 1.8·공격력 1.5가 기본이다."""
    out = {}
    for row in (chess_blob or {}).get("data") or []:
        da = row.get("hero_EN_name") or ""
        if not da.startswith("DA_"):
            continue
        out[da] = {
            "changed": PRO_STATUS.get(str(row.get("proStatus") or "").strip()),
            "lifeMag": to_float(row.get("lifeMag")),
            "attackMag": to_float(row.get("attackMag")),
        }
    return out


def hex_map(hex_blob):
    """DA 증강 id → {type 1|2|3, 아이콘 절대 URL}."""
    data = (hex_blob or {}).get("data") or {}
    rows = data.values() if isinstance(data, dict) else data
    out = {}
    for row in rows:
        da = str(row.get("augments") or "").strip()
        if not da.startswith("DA_"):
            continue
        out.setdefault(da, {"type": str(row.get("type") or ""), "icon": row.get("imgUrl")})
    return out


def equip_map(equip_blob):
    """DA 아이템 id → {type, 아이콘 절대 URL}. englishName에 여러 id가 콤마로 들어오기도 한다."""
    out = {}
    for row in (equip_blob or {}).get("data") or []:
        for da in str(row.get("englishName") or "").split(","):
            da = da.strip()
            if da.startswith("DA_"):
                out.setdefault(da, {"type": str(row.get("type") or ""), "icon": row.get("imagePath")})
    return out
