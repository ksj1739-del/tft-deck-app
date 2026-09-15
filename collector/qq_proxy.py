#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
lol.qq 통계 프록시(POST mlol.qt.qq.com/go/exploit/proxy) 호출.

胜率阵容(목록·상세)과 数据检索器가 전부 이 한 주소로 온다. 쿠키·서명·Referer 가
필요 없지만 잘못된 요청도 HTTP 200 으로 돌아오기 때문에, 실패는 result 코드와
빈 data 로만 드러난다. 여기서 둘 다 예외로 바꿔 조용한 실패를 막는다.

표준 라이브러리만 사용한다.
"""

import gzip
import json
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone

PROXY_URL = "https://mlol.qt.qq.com/go/exploit/proxy"

# lol.qq 날짜 파라미터와 dtstatdate 는 전부 중국 표준시 기준이다.
CST = timezone(timedelta(hours=8))

QUEUE_RANKED = "1100"
WINRATE_TIME_TYPE = "d_grouping_v3"
WINRATE_LIST_VERSION = "dgroup_v3"

# 数据检索器 티어. ""(전체)는 행마다 모순이 나서 쓰지 않는다(벤치마킹에서 확인).
DATASEARCH_TIERS = (("cn_plat", "4+"), ("cn_master", "7+"))


class QQProxy:
    """호출 수를 세고 호출 사이에 쉰다. 상세 호출 예산을 지키려면 calls 가 필요하다."""

    def __init__(self, ua, error_cls=RuntimeError, pause=0.3, tries=2, timeout=60):
        self.ua = ua
        self.error_cls = error_cls
        self.pause = pause
        self.tries = tries
        self.timeout = timeout
        self.calls = 0

    # -- 저수준 -------------------------------------------------------------
    def _post(self, body):
        """본문을 보내고 파싱한 JSON 을 돌려준다. 네트워크 오류만 재시도한다."""
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        last = None
        for attempt in range(self.tries):
            try:
                req = urllib.request.Request(PROXY_URL, data=data, headers={
                    "User-Agent": self.ua,
                    "Content-Type": "application/json",
                    "Accept": "application/json, */*",
                    "Accept-Encoding": "gzip",
                })
                with urllib.request.urlopen(req, timeout=self.timeout) as resp:
                    raw = resp.read()
                    if resp.headers.get("Content-Encoding") == "gzip":
                        raw = gzip.decompress(raw)
                return json.loads(raw.decode("utf-8", errors="replace"), strict=False)
            except (urllib.error.URLError, OSError, ValueError) as exc:
                last = exc
                if attempt < self.tries - 1:
                    time.sleep(2 * (attempt + 1))
        raise self.error_cls("proxy %s -> %s" % (body.get("req_alias"), last))

    def call(self, alias, params, version_id="v1", extra=None):
        """
        extra 기본값은 목록·상세 요청이 쓰는 {"is_return_source": 0}.
        数据检索器와 버전 목록은 사이트도 이 키를 보내지 않으므로 extra={} 로 부른다.
        """
        body = {"req_alias": alias, "version_id": version_id, "req_params": params}
        body.update({"is_return_source": 0} if extra is None else extra)
        try:
            blob = self._post(body)
        finally:
            self.calls += 1
            time.sleep(self.pause)

        if not isinstance(blob, dict) or str(blob.get("result")) != "0":
            raise self.error_cls("proxy %s result=%s msg=%s" % (
                alias, (blob or {}).get("result"), (blob or {}).get("msg") or (blob or {}).get("err_msg")))
        data = blob.get("data")
        if not data:
            raise self.error_cls("proxy %s: HTTP 200 이지만 data 가 비었다" % alias)
        return data

    # -- 버전 --------------------------------------------------------------
    def recent_versions(self):
        """최근 빌드 목록. [{version_id, start_time 'YYYYMMDDHH', end_time}]"""
        data = self.call("tft_recent_versions", {"env": "0"}, extra={})
        rows = (data or {}).get("list") or []
        if not rows:
            raise self.error_cls("tft_recent_versions: list 가 비었다")
        return rows

    # -- 胜率阵容 ----------------------------------------------------------
    def group_list(self, tier_part):
        data = self.call("tft_lineup_group_list", {
            "queue_id": QUEUE_RANKED,
            "tier_part": tier_part,
            "time_type": WINRATE_TIME_TYPE,
        }, version_id=WINRATE_LIST_VERSION)
        if not data.get("main_traits_data"):
            raise self.error_cls("tft_lineup_group_list tier_part=%s: 그룹이 비었다" % tier_part)
        return data

    def lineup_detail(self, alias, params):
        """tft_lineup_all_detail / tft_lineup_position / tft_lineup_key_chess."""
        return self.call(alias, params)

    # -- 数据检索器 --------------------------------------------------------
    def match_overview(self, tier, stime, etime):
        data = self.call("tft_match_overview", datasearch_base(tier, stime, etime), extra={})
        if not isinstance(data, list) or not data or not data[0].get("total_games"):
            raise self.error_cls("tft_match_overview tier=%s: 빈 응답" % tier)
        return data[0]

    def lineup_rank(self, tier, stime, etime, limit=100, min_sample=10):
        params = datasearch_base(tier, stime, etime)
        params.update({"minSampleSize": str(min_sample), "limit": str(limit)})
        data = self.call("tft_lineup_rank", params, extra={})
        if not isinstance(data, list) or not data:
            raise self.error_cls("tft_lineup_rank tier=%s: 빈 응답" % tier)
        return data


# ----------------------------------------------------------------------------
# 파라미터 조립
# ----------------------------------------------------------------------------

def datasearch_window(now=None):
    """
    수집기는 05:00 KST(04:00 CST)에 돈다. 그 시각의 '오늘'은 거의 비어 있으므로
    완결된 전날을 끝으로 3일(최대 허용 기간)을 잡는다.
    """
    now = (now or datetime.now(timezone.utc)).astimezone(CST)
    end = (now - timedelta(days=1)).date()
    start = end - timedelta(days=2)
    return start.isoformat(), end.isoformat()


def datasearch_base(tier, stime, etime):
    # version 은 빈 문자열이어야 전 빌드 합산이 된다('16.18' 같은 짧은 값은 빈 응답).
    return {
        "queueId": '["%s"]' % QUEUE_RANKED,
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
    }


def trait_param(trait_list):
    """[{trait_id, chess_num}] -> 'id,개수;id,개수'. 원본 순서를 지킨다."""
    parts = []
    for trait in trait_list or []:
        tid, num = trait.get("trait_id"), trait.get("chess_num")
        if tid in (None, "") or num in (None, ""):
            continue
        parts.append("%s,%s" % (tid, num))
    return ";".join(parts)


def detail_params(variant, tier_part):
    """
    胜率阵容 상세 요청. 목록의 한 조합(variant)을 그대로 옮긴다.
    minor_traits_id 가 비면 사이트처럼 '-1,-1' 을 보낸다(이때 augment_data 가 빌 수 있다).
    """
    lineup = sorted(str(c) for c in variant.get("lineup") or [])
    return {
        "champion_content_id": ",".join(lineup),
        "main_traits_id": trait_param(variant.get("main_trait_list")),
        "mc_champion_id": str(variant.get("main_c_chess") or ""),
        "minor_traits_id": trait_param(variant.get("sub_trait_list")) or "-1,-1",
        "queue_id": QUEUE_RANKED,
        "tier_part": tier_part,
        "time_type": WINRATE_TIME_TYPE,
    }


def patch_start(versions, patch):
    """
    패치(예 '16.18')의 첫 빌드 시작 시각. '이전 패치 작성' 배지의 기준이다.
    반환: ISO 문자열(+08:00) 또는 None.
    """
    starts = []
    for row in versions or []:
        vid = str(row.get("version_id") or "")
        if patch and not vid.startswith(patch + "."):
            continue
        try:
            starts.append(datetime.strptime(str(row.get("start_time")), "%Y%m%d%H").replace(tzinfo=CST))
        except ValueError:
            continue
    if not starts:
        return None
    return min(starts).isoformat()


def latest_build(versions):
    """end_time 이 가장 늦은(같으면 start_time 이 늦은) 빌드 id. 예 '16.18.817.4437'."""
    rows = [r for r in versions or [] if r.get("version_id")]
    if not rows:
        return None
    rows.sort(key=lambda r: (str(r.get("end_time") or ""), str(r.get("start_time") or "")))
    return str(rows[-1]["version_id"])
