#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
lol.qq 정적 도감(chess/race/job/equip/hex.js)으로 숫자 id 를 DA_* id 로 바꾼다.

胜率阵容은 챔피언·특성을 숫자 id(chessId '100331', traitId '10379')로 준다.
세 소스(lol.qq·metatft·CommunityDragon)의 공통 키는 DA_* 이므로, 합치기 전에
여기서 한 번에 변환한다. 챔피언의 DA id 는 chess.js 의 hero_EN_name 이다
(TFTID 는 숫자라 쓸 수 없다, 2026-09-15 확인).

versionconfig.json 은 한 패치 늦은 경로(예 16.17-2026.S18)를 가리키는 일이 있어
최신 빌드 패치로 바꾼 경로를 먼저 시도하고, 없으면 원래 경로를 쓴다.
"""

import json
import re

VERSION_CONFIG = "https://game.gtimg.cn/images/lol/tfth5lib/v1/versionconfig.json"

FILE_KEYS = (
    ("chess", "urlChessData"),
    ("race", "urlRaceData"),
    ("job", "urlJobData"),
    ("equip", "urlEquipData"),
    ("hex", "urlBuffData"),
)

# 경로 속 '16.17-2026.S18' 부분.
PATCH_SEGMENT = re.compile(r"/(\d+\.\d+)-(\d{4}\.S\d+)/")


def _int(value, default=None):
    try:
        return int(str(value).strip())
    except (TypeError, ValueError):
        return default


def _parse(text):
    """정적 .js 파일은 현재 순수 JSON 이다. 혹시 변수 대입으로 바뀌어도 읽히게 한다."""
    try:
        return json.loads(text, strict=False)
    except ValueError:
        starts = [i for i in (text.find("{"), text.find("[")) if i >= 0]
        if not starts:
            raise
        return json.loads(text[min(starts):].rstrip().rstrip(";"), strict=False)


class QQStatic:
    def __init__(self, blobs, urls):
        self.urls = urls
        self.versions = {name: (blob or {}).get("version") for name, blob in blobs.items()}

        # 챔피언: 숫자 id 와 DA id 양쪽으로 찾는다. 편집 덱의 pet 칸은 chess_id 에
        # 숫자가 아니라 DA id('DA_18_IronbarkTree')를 넣기 때문이다.
        self.chess_by_id, self.chess_by_da = {}, {}
        for row in (blobs.get("chess") or {}).get("data") or []:
            cid = str(row.get("chessId") or "").strip()
            if not cid:
                continue
            da = (row.get("hero_EN_name") or "").strip()
            record = {
                "chessId": cid,
                "da": da or "QQ_" + cid,
                "name_cn": (row.get("displayName") or "").strip(),
                "price": _int(row.get("price")),
                "image": (row.get("originalImage") or "").strip(),
                "chess_type": row.get("chess_type"),
            }
            self.chess_by_id[cid] = record
            if da:
                self.chess_by_da.setdefault(da, record)

        # 특성: raceId / jobId -> characterid(DA). 두 파일의 id 는 겹치지 않는다.
        self.trait_by_id = {}
        for name, key in (("race", "raceId"), ("job", "jobId")):
            for row in (blobs.get(name) or {}).get("data") or []:
                tid = str(row.get(key) or row.get("traitId") or "").strip()
                da = (row.get("characterid") or "").strip()
                if tid and da:
                    self.trait_by_id[tid] = da

        self.augment_by_hexid = {}
        hexes = (blobs.get("hex") or {}).get("data") or {}
        for row in (hexes.values() if isinstance(hexes, dict) else hexes):
            hid = str(row.get("hexId") or "").strip()
            da = (row.get("augments") or "").strip()
            if hid and da:
                self.augment_by_hexid[hid] = da

        self.equip_by_id = {}
        for row in (blobs.get("equip") or {}).get("data") or []:
            eid = str(row.get("equipId") or "").strip()
            names = [n.strip() for n in str(row.get("englishName") or "").split(",") if n.strip()]
            if eid and names:
                self.equip_by_id[eid] = names[0]

    # -- 변환 ---------------------------------------------------------------
    def chess_da(self, chess_id):
        """숫자 chessId -> DA id. 도감에 없으면 None(호출한 쪽이 조인 실패로 센다)."""
        record = self.chess_by_id.get(str(chess_id))
        return record["da"] if record and not record["da"].startswith("QQ_") else None

    def chess_record(self, key):
        """숫자 id 든 DA id 든 chess.js 항목을 찾는다."""
        key = str(key or "").strip()
        return self.chess_by_id.get(key) or self.chess_by_da.get(key)

    def trait_da(self, trait_id):
        return self.trait_by_id.get(str(trait_id))

    def summary(self):
        return "chess %d · trait %d · equip %d · hex %d · version %s" % (
            len(self.chess_by_id), len(self.trait_by_id), len(self.equip_by_id),
            len(self.augment_by_hexid), self.versions.get("chess"))


def load(fetch_text, season_id, patch=None, log=print):
    """
    fetch_text(url, tries=N) -> str (실패 시 예외). versionconfig 에서 시즌 항목을 찾고
    파일 다섯 개를 받는다. patch 가 있으면 그 패치 경로를 먼저 시도한다.
    """
    config = _parse(fetch_text(VERSION_CONFIG))
    entries = config if isinstance(config, list) else [config]
    entry = next((e for e in entries if isinstance(e, dict) and e.get("idSeason") == season_id), None)
    if entry is None:
        raise ValueError("versionconfig.json 에 시즌 %s 항목이 없다" % season_id)

    blobs, urls = {}, {}
    for name, key in FILE_KEYS:
        original = entry.get(key)
        if not original:
            raise ValueError("versionconfig.json 에 %s 가 없다" % key)
        candidates = []
        if patch:
            patched = PATCH_SEGMENT.sub(lambda m: "/%s-%s/" % (patch, m.group(2)), original, count=1)
            if patched != original:
                candidates.append(patched)
        candidates.append(original)

        last = None
        for index, url in enumerate(candidates):
            # 최신 패치 경로는 없을 수도 있는 추측이라 한 번만 시도한다.
            tries = 1 if index < len(candidates) - 1 else 3
            try:
                blobs[name] = _parse(fetch_text(url, tries=tries))
                urls[name] = url
                break
            except Exception as exc:  # noqa: BLE001 — 다음 후보로 넘어가야 한다
                last = exc
        if name not in blobs:
            raise ValueError("%s 를 받지 못했다: %s" % (name, last))
        if urls[name] != candidates[0]:
            log("[경고] lol.qq %s: 최신 패치 경로가 없어 %s 사용" % (name, urls[name]))

    static = QQStatic(blobs, urls)
    log("lol.qq 정적 도감: %s" % static.summary())
    return static
