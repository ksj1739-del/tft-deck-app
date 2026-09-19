package com.tftdeck.reader

import com.tftdeck.reader.data.Deck
import com.tftdeck.reader.data.DeckKeys
import com.tftdeck.reader.data.DeckStats
import com.tftdeck.reader.overlay.offGrades
import com.tftdeck.reader.overlay.overlayCountText
import com.tftdeck.reader.overlay.overlayEmptyListMessage
import com.tftdeck.reader.overlay.overlayGradeFiltered
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 오버레이 덱 목록의 등급 조회 조건 — 앱 목록과 같은 값·같은 규칙(구간 등급, 고정한 덱은 건너뜀). */
class OverlayListFilterTest {

    private fun deck(id: String, grade: String?, bucket: String = "goldem") = Deck(
        id = id,
        kind = DeckKeys.KIND_META,
        name = id,
        stats = mapOf(bucket to DeckStats(n = 5000, avg = 4.3, adjAvg = 4.3, grade = grade)),
    )

    private val decks = listOf(deck("s", "S"), deck("a", "A"), deck("b", "B"), deck("c", "C"), deck("d", "D"))

    @Test
    fun `기본 조건(S·A·B)은 C·D 를 빼고 고정한 덱은 남긴다`() {
        val ids = overlayGradeFiltered(decks, "goldem", pinned = emptySet(), grades = DeckKeys.GRADE_FILTER_DEFAULT).map { it.id }
        assertEquals(listOf("s", "a", "b"), ids)
        val withPin = overlayGradeFiltered(decks, "goldem", pinned = setOf("d"), grades = DeckKeys.GRADE_FILTER_DEFAULT).map { it.id }
        assertEquals(listOf("s", "a", "b", "d"), withPin)
    }

    @Test
    fun `등급은 그 구간 등급으로 본다`() {
        // 다른 구간에만 S 인 덱은 이 구간(goldem)에서 등급이 없어 빠진다.
        val other = deck("x", "S", bucket = "master")
        assertEquals(emptyList<Deck>(), overlayGradeFiltered(listOf(other), "goldem", emptySet(), setOf("S")))
        assertEquals(listOf("x"), overlayGradeFiltered(listOf(other), "master", emptySet(), setOf("S")).map { it.id })
    }

    @Test
    fun `다섯 등급을 모두 켜면 거르지 않는다`() {
        val all = DeckKeys.GRADE_FILTER_ALL.toSet()
        assertEquals(decks + deck("n", null), overlayGradeFiltered(decks + deck("n", null), "goldem", emptySet(), all))
    }

    @Test
    fun `꺼 둔 등급은 S 에서 D 순으로`() {
        assertEquals(listOf("C", "D"), offGrades(DeckKeys.GRADE_FILTER_DEFAULT))
        assertEquals(listOf("S", "B", "D"), offGrades(setOf("C", "A")))
        assertEquals(emptyList<String>(), offGrades(DeckKeys.GRADE_FILTER_ALL.toSet()))
    }

    @Test
    fun `머리줄 덱 수는 검색 조건이 있을 때만 좁혀진 수와 검색 전 수를 함께`() {
        assertEquals("덱 40", overlayCountText(shown = 40, beforeSearch = 40, hasTokens = false))
        assertEquals("덱 5/40", overlayCountText(shown = 5, beforeSearch = 40, hasTokens = true))
    }

    @Test
    fun `꺼 둔 등급 때문에 비었으면 그 등급과 가려진 수를 말한다`() {
        val message = overlayEmptyListMessage(hasTokens = true, off = listOf("C", "D"), hiddenByGrade = 3)
        assertEquals("조건에 맞는 덱이 없습니다 · 꺼진 등급 C·D 3", message)
        val perGrade = overlayEmptyListMessage(true, listOf("C", "D"), 3, hiddenPerGrade = mapOf("D" to 1, "C" to 2))
        assertEquals("조건에 맞는 덱이 없습니다 · 꺼진 등급 C 2 · D 1", perGrade)
    }

    @Test
    fun `등급을 켜도 없으면 검색 조건 안내, 조건도 없으면 숨긴 덱 안내`() {
        assertTrue(overlayEmptyListMessage(hasTokens = true, off = listOf("C", "D"), hiddenByGrade = 0).contains("지우기 버튼"))
        assertTrue(overlayEmptyListMessage(hasTokens = false, off = emptyList(), hiddenByGrade = 0).contains("숨긴 덱"))
    }
}
