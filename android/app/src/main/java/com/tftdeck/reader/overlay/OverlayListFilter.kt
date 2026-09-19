package com.tftdeck.reader.overlay

import com.tftdeck.reader.data.Deck
import com.tftdeck.reader.data.DeckKeys
import com.tftdeck.reader.data.DeckSearch

/**
 * 오버레이 덱 목록의 등급 조회 조건. 앱 목록과 같은 값(DeckPrefs.grades)을 쓰므로 어느 쪽에서 바꿔도 같다.
 * 등급은 그 구간 등급(gradeFor(bucket))으로 보고, 고정한 덱은 등급이 꺼져 있어도 남긴다 — 앱 목록과 같은 규칙.
 */
internal fun overlayGradeFiltered(
    decks: List<Deck>,
    bucket: String,
    pinned: Set<String>,
    grades: Set<String>,
): List<Deck> = decks.filter { it.id in pinned || DeckSearch.gradePasses(it.gradeFor(bucket), grades) }

/** 등급 조회 조건에서 꺼 둔 등급(S → D 순). */
internal fun offGrades(grades: Set<String>): List<String> = DeckKeys.GRADE_FILTER_ALL.filter { it !in grades }

/**
 * 머리줄의 덱 수. 검색 조건이 있으면 '좁혀진 수/검색 전 수'. 검색 전 수는 등급 조건까지 적용한 수다 —
 * 등급 조건은 검색 줄 옆 등급 칸이 늘 보여 주므로, 숫자는 지금 목록과 그 목록을 검색으로 좁힌 정도만 말한다.
 */
internal fun overlayCountText(shown: Int, beforeSearch: Int, hasTokens: Boolean): String =
    if (hasTokens) "덱 $shown/$beforeSearch" else "덱 $shown"

/**
 * 목록이 비었을 때 안내. 무엇이 막았는지 말하고 푸는 곳을 알려 준다.
 * [hiddenByGrade] 는 꺼 둔 등급 때문에만 빠진 덱 수(등급 조건을 모두 켜면 보일 덱 수)다.
 */
internal fun overlayEmptyListMessage(hasTokens: Boolean, off: List<String>, hiddenByGrade: Int): String = when {
    hiddenByGrade > 0 && off.isNotEmpty() ->
        "${off.joinToString("·")} 등급이 꺼져 있어 덱 ${hiddenByGrade}개가 가려졌습니다. 검색 줄 옆 등급 칸을 눌러 켜 보세요."
    hasTokens ->
        "이 구간에는 조건을 모두 만족하는 덱이 없습니다. 칩의 ×로 조건을 빼거나 '모두 지우기', 또는 위의 구간을 바꿔 보세요."
    else ->
        "숨기지 않은 덱이 없습니다. 앱의 '숨긴 덱 보기'에서 복구할 수 있습니다."
}
