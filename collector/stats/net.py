# -*- coding: utf-8 -*-
"""
도감 수집기의 네트워크 계층.

- 브라우저 UA, gzip 해제, 재시도(429·5xx·연결 오류만; 4xx는 다시 보내도 같으므로 즉시 실패)
- 같은 호스트 연속 호출 사이 최소 0.4초. api-hc.metatft.com은 CDN 캐시가 없어
  요청마다 오리진이 집계하므로 부담을 줄인다(설계 §3.1).
- lol.qq 통계 프록시 proxy(): result != 0 이거나 data가 비면 실패로 본다.
  세 원본 모두 잘못된 요청에 HTTP 200 빈 응답을 주므로 "200이면 성공"으로 보면
  조용히 빈 도감이 배포된다.
- 호스트별 호출 수를 세어 실행 요약에 출력한다(하루 호출량 감시용).
"""

import gzip
import hashlib
import json
import os
import time
import urllib.error
import urllib.parse
import urllib.request

UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

PROXY_URL = "https://mlol.qt.qq.com/go/exploit/proxy"
METATFT_REFERER = "https://www.metatft.com/"

MIN_INTERVAL = 0.4


class SourceError(Exception):
    """원본 하나를 끝내 가져오지 못했거나, 200이지만 내용이 비었을 때."""


class HttpError(SourceError):
    """HTTP 오류 코드. 정적 파일 경로 폴백(404)을 구분하려고 코드를 들고 있다."""

    def __init__(self, url, code):
        SourceError.__init__(self, "%s -> HTTP %s" % (url, code))
        self.code = code


class Client:
    """
    호출 하나하나를 기록하는 얇은 HTTP 클라이언트.

    cache_dir는 개발용이다. 같은 URL·본문의 응답을 파일로 재사용해 계산 로직을 고치는 동안
    원본 서버에 수백 번씩 다시 요청하지 않게 한다. 운영(워크플로)에서는 쓰지 않는다.
    """

    def __init__(self, min_interval=MIN_INTERVAL, cache_dir=None):
        self.min_interval = min_interval
        self.cache_dir = cache_dir
        self._last = {}
        self.calls = {}
        self.failures = {}
        self.cache_hits = 0
        if cache_dir:
            os.makedirs(cache_dir, exist_ok=True)

    # -- 내부 ----------------------------------------------------------------
    def _wait(self, host):
        last = self._last.get(host)
        if last is None:
            return
        gap = time.time() - last
        if gap < self.min_interval:
            time.sleep(self.min_interval - gap)

    def _cache_path(self, url, body):
        if not self.cache_dir:
            return None
        text = url + "\n" + (json.dumps(body, sort_keys=True) if body is not None else "")
        return os.path.join(self.cache_dir, hashlib.sha1(text.encode("utf-8")).hexdigest() + ".bin")

    def _fail(self, host):
        self.failures[host] = self.failures.get(host, 0) + 1

    # -- 공개 ----------------------------------------------------------------
    def request(self, url, body=None, timeout=60, tries=3):
        """응답 본문 bytes. body가 있으면 JSON POST."""
        cache_path = self._cache_path(url, body)
        if cache_path and os.path.exists(cache_path):
            self.cache_hits += 1
            with open(cache_path, "rb") as fp:
                return fp.read()

        host = urllib.parse.urlsplit(url).netloc
        headers = {
            "User-Agent": UA,
            "Accept": "application/json, text/javascript, */*",
            "Accept-Encoding": "gzip",
        }
        if "metatft.com" in host:
            headers["Referer"] = METATFT_REFERER
        data = None
        if body is not None:
            data = json.dumps(body, ensure_ascii=False).encode("utf-8")
            headers["Content-Type"] = "application/json"

        last = None
        for attempt in range(tries):
            self._wait(host)
            self.calls[host] = self.calls.get(host, 0) + 1
            wait = 2 * (attempt + 1)
            try:
                req = urllib.request.Request(url, data=data, headers=headers)
                with urllib.request.urlopen(req, timeout=timeout) as resp:
                    raw = resp.read()
                    if resp.headers.get("Content-Encoding") == "gzip":
                        raw = gzip.decompress(raw)
                self._last[host] = time.time()
                if cache_path:
                    with open(cache_path, "wb") as fp:
                        fp.write(raw)
                return raw
            except urllib.error.HTTPError as exc:
                self._last[host] = time.time()
                last = exc
                # 없는 키·경로(4xx)는 다시 보내도 같다. 레이트리밋과 서버 오류만 기다렸다 재시도.
                if exc.code != 429 and exc.code < 500:
                    self._fail(host)
                    raise HttpError(url, exc.code)
                if exc.code == 429:
                    wait = 10 * (attempt + 1)
            except (urllib.error.URLError, OSError, ValueError, EOFError) as exc:
                # 타임아웃·연결 끊김·깨진 gzip
                self._last[host] = time.time()
                last = exc
            if attempt < tries - 1:
                time.sleep(wait)

        self._fail(host)
        if isinstance(last, urllib.error.HTTPError):
            raise HttpError(url, last.code)
        raise SourceError("%s -> %s" % (url, last))

    def get_json(self, url, timeout=60, tries=3):
        raw = self.request(url, timeout=timeout, tries=tries)
        try:
            # lol.qq 정적 파일에는 제어문자가 섞일 수 있어 strict=False.
            return json.loads(raw.decode("utf-8", errors="replace"), strict=False)
        except ValueError as exc:
            raise SourceError("%s -> JSON이 아니다: %s" % (url, exc))

    def proxy(self, alias, params, version_id="v1", timeout=60):
        """lol.qq 통계 프록시. 쿠키·서명·Referer 없이 동작한다(설계 §3.2)."""
        body = {"req_alias": alias, "version_id": version_id, "req_params": params}
        raw = self.request(PROXY_URL, body=body, timeout=timeout)
        try:
            blob = json.loads(raw.decode("utf-8", errors="replace"), strict=False)
        except ValueError as exc:
            raise SourceError("%s -> JSON이 아니다: %s" % (alias, exc))
        if str(blob.get("result")) != "0":
            raise SourceError("%s -> result=%s %s" % (
                alias, blob.get("result"), blob.get("msg") or blob.get("err_msg") or ""))
        data = blob.get("data")
        if not data:
            raise SourceError("%s -> 빈 응답(200)" % alias)
        return data

    def total_calls(self):
        return sum(self.calls.values())
