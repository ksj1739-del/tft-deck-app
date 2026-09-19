package com.tftdeck.reader

import com.tftdeck.reader.data.Deck
import com.tftdeck.reader.data.DeckFeed
import com.tftdeck.reader.data.DeckKeys
import com.tftdeck.reader.data.DeckSearch
import com.tftdeck.reader.data.DeckStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 덱 등급 조회 조건(S~D 여러 개 고름, 처음엔 C·D 해제). */
class GradeFilterTest {
    private fun deck(id: String, grade: String?) = Deck(
        id = id,
        kind = DeckKeys.KIND_META,
        name = id,
        stats = mapOf("goldem" to DeckStats(n = 5000, avg = 4.3, adjAvg = 4.3, grade = grade)),
    )

    @Test
    fun `기본 조건은 S A B 만 남기고 편집 등급 SS 는 S 로 본다`() {
        val default = DeckKeys.GRADE_FILTER_DEFAULT
        assertEquals(setOf("S", "A", "B"), default)
        assertTrue(DeckSearch.gradePasses("S", default))
        assertTrue(DeckSearch.gradePasses("SS", default))
        assertTrue(DeckSearch.gradePasses("b", default))
        assertFalse(DeckSearch.gradePasses("C", default))
        assertFalse(DeckSearch.gradePasses("D", default))
        assertFalse(DeckSearch.gradePasses(null, default))
        // 다섯 등급을 모두 고르면 거르지 않는다(등급 없는 덱도 남는다).
        assertTrue(DeckSearch.gradePasses(null, DeckKeys.GRADE_FILTER_ALL.toSet()))
    }

    @Test
    fun `목록 필터가 등급 조건을 적용하고 고정한 덱은 등급이 꺼져 있어도 남긴다`() {
        val search = DeckSearch(DeckFeed(decks = listOf(deck("s", "S"), deck("a", "A"), deck("c", "C"), deck("d", "D"))))
        fun ids(grades: Set<String>?, pinned: Set<String> = emptySet()) =
            search.filter(bucket = "goldem", grades = grades, alwaysShow = pinned).map { it.id }

        assertEquals(listOf("s", "a"), ids(DeckKeys.GRADE_FILTER_DEFAULT))
        assertEquals(listOf("c", "d"), ids(setOf("C", "D")))
        assertEquals(listOf("s", "a", "d"), ids(DeckKeys.GRADE_FILTER_DEFAULT, pinned = setOf("d")))
        assertEquals(listOf("s", "a", "c", "d"), ids(null))
    }
}
