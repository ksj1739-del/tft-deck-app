# -*- coding: utf-8 -*-
"""
도감(챔피언·특성·아이템·증강) 통계 수집 패키지.

덱 수집기(fetch_decks.py)와 코드를 공유하지 않는다. 두 파이프라인이 따로 실패하고
따로 고쳐질 수 있어야 하기 때문이다 — 덱 수집이 깨져도 도감은 갱신되고, 그 반대도 같다.
표준 라이브러리만 쓴다(워크플로에 설치 단계를 늘리지 않기 위해).

모듈 구성
- net.py      네트워크(UA, gzip, 재시도, 호스트별 간격, lol.qq proxy)
- metatft.py  metatft 통계·사전·티어 호출과 CurveTable 설명문 해석
- lolqq.py    lol.qq 数据检索器·특성 추세·정적 사전(chess/race/job/hex/equip)
- cdragon.py  CommunityDragon 한국어·영어 사전
- build.py    등급·평균·픽률 계산과 JSON 네 개 조립(앱은 조회만 한다)

이 파일에는 두 원본 설명문 해석기가 함께 쓰는 문자열 정리 함수만 둔다.
"""

import json
import os
import re

PACKAGE_DIR = os.path.dirname(os.path.abspath(__file__))
CONSTANTS_PATH = os.path.join(PACKAGE_DIR, "constants.json")


def load_constants():
    """등급 컷·표본 문턱처럼 분포를 보고 조정할 값은 코드가 아니라 이 파일 한 곳에 둔다."""
    with open(CONSTANTS_PATH, encoding="utf-8") as fp:
        return json.load(fp)


def fmt_number(value, precision=None):
    """
    설명문에 넣을 수치 문자열. 원본 값이 float32라 0.35*100이 34.99999로 나오므로
    소수 둘째 자리에서 반올림하고 정수면 소수점을 떼어 사람이 쓰는 모양으로 만든다.
    """
    try:
        number = float(value)
    except (TypeError, ValueError):
        return ""
    if precision not in (None, ""):
        try:
            number = round(number, int(precision))
        except (TypeError, ValueError):
            pass
    number = round(number, 2)
    if abs(number - round(number)) < 1e-9:
        return str(int(round(number)))
    return ("%.2f" % number).rstrip("0").rstrip(".")


# 설명문의 능력치 아이콘(%i:scaleAD%, <img id="Icon.AD"/>)을 글자로. 특성 효과는 '20% (아이콘)'처럼
# 수치 뒤 아이콘으로만 무엇이 오르는지 알려 주므로 지우면 '25% 또는'처럼 뜻이 끊긴다.
STAT_WORDS = {
    "ad": "공격력", "ap": "주문력", "as": "공격 속도", "health": "체력", "armor": "방어력",
    "mr": "마법 저항력", "dr": "내구력", "dura": "내구력", "manaregen": "마나 회복", "mana": "마나",
    "critchance": "치명타 확률", "crit": "치명타 확률", "omnivamp": "모든 피해 흡혈", "sv": "모든 피해 흡혈",
    "da": "피해 증폭", "damageamp": "피해 증폭",
}


def stat_words(tokens):
    """아이콘 id('Icon.AD', 'scaleAP', 'icon.Armor')들을 '방어력·마법 저항력'처럼. 모르는 아이콘은 뺀다."""
    words = []
    for token in tokens or []:
        key = re.sub(r"^(icon\.|scale)", "", str(token).strip(), flags=re.I).lower()
        word = STAT_WORDS.get(key)
        if word and word not in words:
            words.append(word)
    return "·".join(words)


_BR = re.compile(r"<br\s*/?>", re.I)
_TAG = re.compile(r"</?[A-Za-z][^<>]*>|</>")


def clean_text(text):
    """태그를 지우고(안의 글자는 남긴다) 줄바꿈·공백을 정리한다."""
    if not text:
        return ""
    out = str(text).replace("\r\n", "\n").replace("\r", "\n")
    out = _BR.sub("\n", out)
    out = _TAG.sub("", out)
    # 지운 아이콘·수치 자리표시자가 남긴 빈 괄호와 겹친 퍼센트 기호
    out = re.sub(r"\(\s*\)", "", out)
    out = out.replace("%%", "%")
    out = re.sub(r"[ \t ]+", " ", out)
    # 지운 아이콘 뒤에 남은 ' ,' ' .'
    out = re.sub(r" +([,.])", r"\1", out)
    out = re.sub(r" *\n *", "\n", out)
    out = re.sub(r"\n{3,}", "\n\n", out)
    return out.strip()
