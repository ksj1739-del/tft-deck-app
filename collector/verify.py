#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
decks.json이 앱에 내보내도 되는 상태인지 검사한다.

원본이 조용히 바뀌면 수집기는 성공하는데 내용만 비어 있을 수 있다.
그런 결과가 배포되지 않도록 CI에서 이 검사를 통과해야 커밋한다.
"""

import io
import json
import os
import sys

# Windows 콘솔(cp949)에서 한글/기호 출력 시 죽지 않도록.
for _stream in (sys.stdout, sys.stderr):
    try:
        _stream.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, ValueError):
        pass


ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DECKS = os.path.join(ROOT, "data", "decks.json")

# 정상 범위. 벗어나면 원본이 바뀐 신호로 본다.
MIN_DECKS = 10
MIN_TRANSLATION_RATE = 0.95
MIN_ITEM_KEYS = 20
TEAM_CODE_LENGTH = 32 + len("TFTSet") + 2   # 접두사+슬롯 32자 + "TFTSetNN"


def main():
    if not os.path.exists(DECKS):
        print("[실패] data/decks.json이 없다", file=sys.stderr)
        return 1

    with io.open(DECKS, encoding="utf-8") as fp:
        data = json.load(fp)

    problems = []
    warnings = []

    version = data.get("version") or {}
    decks = data.get("decks") or []
    index = data.get("index") or {}
    catalog = data.get("catalog") or {}

    # --- 덱 --------------------------------------------------------------
    if len(decks) < MIN_DECKS:
        problems.append("덱이 %d개뿐이다 (최소 %d)" % (len(decks), MIN_DECKS))

    for deck in decks:
        label = deck.get("name") or deck.get("id")
        if not deck.get("units"):
            problems.append("덱 '%s'에 유닛이 없다" % label)
        if not deck.get("traits"):
            warnings.append("덱 '%s'에 시너지가 없다" % label)
        if not deck.get("tier"):
            warnings.append("덱 '%s'에 티어가 없다" % label)

        code = (deck.get("teamCode") or {}).get("code")
        if code:
            if not code.startswith("02"):
                problems.append("덱 '%s' 코드 접두사가 02가 아니다: %s" % (label, code[:4]))
            if not code.endswith("TFTSet%s" % version.get("setNumber")):
                problems.append("덱 '%s' 코드 세트 접미사가 틀렸다: %s" % (label, code[-10:]))
            if len(code) != TEAM_CODE_LENGTH:
                problems.append("덱 '%s' 코드 길이가 %d (기대 %d)"
                                % (label, len(code), TEAM_CODE_LENGTH))

    # --- 번역률 ----------------------------------------------------------
    champions = catalog.get("champions") or []
    if champions:
        korean = sum(1 for c in champions if c["name"] != c["id"])
        rate = korean / float(len(champions))
        if rate < MIN_TRANSLATION_RATE:
            problems.append("챔피언 한글화율 %.0f%% (최소 %.0f%%)"
                            % (rate * 100, MIN_TRANSLATION_RATE * 100))
    else:
        problems.append("catalog.champions가 비었다")

    # --- 검색 인덱스 ------------------------------------------------------
    # 아이템 역검색이 이 앱의 핵심 기능이라 비면 배포하지 않는다.
    if len(index.get("item") or {}) < MIN_ITEM_KEYS:
        problems.append("아이템 인덱스가 %d개뿐이다 (최소 %d)"
                        % (len(index.get("item") or {}), MIN_ITEM_KEYS))
    for axis in ("champion", "trait", "component", "augment"):
        if not index.get(axis):
            problems.append("%s 인덱스가 비었다" % axis)

    # 인덱스가 가리키는 덱이 실제로 있는지
    known = {d["id"] for d in decks}
    for entry in (index.get("item") or {}).values():
        for hit in entry:
            if hit["deck"] not in known:
                problems.append("아이템 인덱스가 없는 덱 %s를 가리킨다" % hit["deck"])
                break

    # --- 원본 상태 --------------------------------------------------------
    sources = version.get("sources") or {}
    for name, state in sources.items():
        if state != "ok":
            warnings.append("원본 %s 상태: %s" % (name, state))
    if version.get("untranslatedIds"):
        warnings.append("미번역 ID %d개: %s"
                        % (len(version["untranslatedIds"]),
                           ", ".join(version["untranslatedIds"][:5])))

    # --- 결과 -------------------------------------------------------------
    for text in warnings:
        print("[경고] %s" % text)
    for text in problems:
        print("[실패] %s" % text, file=sys.stderr)

    if problems:
        print("\n검증 실패: %d건" % len(problems), file=sys.stderr)
        return 1

    print("검증 통과 — 덱 %d개, 패치 %s, 아이템 인덱스 %d개, 덱 코드 %d개"
          % (len(decks), version.get("patch"),
             len(index.get("item") or {}), version.get("teamCodeCount", 0)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
