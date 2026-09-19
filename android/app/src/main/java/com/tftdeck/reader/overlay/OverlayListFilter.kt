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
 * 꺼 둔 등급 때문에 지금 목록에서 빠진 덱 수를 등급별로 센다(S → D 순, 0 인 등급은 뺀다).
 * [withoutGrades] 는 등급 조건만 걸지 않은 목록(검색 조건은 건 것), [shown] 은 지금 목록이다. 등급은 그 구간 등급이고
 * 편집 등급 SS 는 S 로 센다. 등급이 없는 덱은 어느 칸에도 넣지 않는다(합계 [overlayEmptyListMessage] 의 hiddenByGrade 에는 든다).
 */
internal fun hiddenByGradeCounts(withoutGrades: List<Deck>, shown: List<Deck>, bucket: String): Map<String, Int> {
    val shownIds = shown.mapTo(HashSet()) { it.id }
    val counts = withoutGrades.asSequence()
        .filter { it.id !in shownIds }
        .mapNotNull { DeckSearch.gradeLetter(it.gradeFor(bucket)) }
        .groupingBy { it }
        .eachCount()
    return DeckKeys.GRADE_FILTER_ALL.mapNotNull { grade -> counts[grade]?.let { grade to it } }.toMap()
}

/**
 * 켜 둔 등급이 이것 하나뿐인데 끄려는 누름인지. DeckPrefs.toggleGrade 는 마지막 하나를 끄지 않고 조용히 넘어가므로
 * 오버레이 등급 칸이 대신 안내한다('등급 하나는 켜 두어야 합니다').
 */
internal fun turnsOffLastGrade(grades: Set<String>, grade: String): Boolean = grades.size == 1 && grade in grades

/**
 * 머리줄의 덱 수. 검색 조건이 있으면 '좁혀진 수/검색 전 수'. 검색 전 수는 등급 조건까지 적용한 수다 —
 * 등급 조건은 검색 줄 옆 등급 칸이 늘 보여 주므로, 숫자는 지금 목록과 그 목록을 검색으로 좁힌 정도만 말한다.
 */
internal fun overlayCountText(shown: Int, beforeSearch: Int, hasTokens: Boolean): String =
    if (hasTokens) "덱 $shown/$beforeSearch" else "덱 $shown"

/**
 * 목록이 비었을 때 안내 한 줄(무엇이 · 왜 · 다음 행동, 마침표 없이).
 *  - 꺼 둔 등급 때문이면 등급별로 몇 개가 가려졌는지: '조건에 맞는 덱이 없습니다 · 꺼진 등급 S 3 · B 2'.
 *    등급별 수([hiddenPerGrade], [hiddenByGradeCounts])를 받지 못하면 꺼진 등급과 합계로 적는다('꺼진 등급 C·D 5').
 *  - 검색 조건 때문이면 칩(누르면 그 조건만 빠진다)과 검색 줄 끝 지우기 버튼(조건 모두 빼기)을 가리킨다.
 *  - 그 밖에는 숨긴 덱 안내.
 * [hiddenByGrade] 는 꺼 둔 등급 때문에만 빠진 덱 수(등급 조건을 모두 켜면 보일 덱 수)다.
 */
internal fun overlayEmptyListMessage(
    hasTokens: Boolean,
    off: List<String>,
    hiddenByGrade: Int,
    hiddenPerGrade: Map<String, Int> = emptyMap(),
): String = when {
    hiddenByGrade > 0 && off.isNotEmpty() -> "$NO_MATCHING_DECKS · 꺼진 등급 ${gradeBreakdown(off, hiddenByGrade, hiddenPerGrade)}"
    hasTokens -> "$NO_MATCHING_DECKS · 칩이나 지우기 버튼으로 조건 빼기"
    else -> "숨기지 않은 덱이 없습니다 · 앱의 '숨긴 덱 보기'에서 복구"
}

/** 'S 3 · B 2'. 등급별 수가 없으면 'C·D 5'(꺼진 등급 · 합계). */
private fun gradeBreakdown(off: List<String>, total: Int, perGrade: Map<String, Int>): String {
    val parts = DeckKeys.GRADE_FILTER_ALL.mapNotNull { grade ->
        perGrade[grade]?.takeIf { it > 0 }?.let { "$grade $it" }
    }
    return if (parts.isNotEmpty()) parts.joinToString(" · ") else "${off.joinToString("·")} $total"
}

private const val NO_MATCHING_DECKS = "조건에 맞는 덱이 없습니다"
