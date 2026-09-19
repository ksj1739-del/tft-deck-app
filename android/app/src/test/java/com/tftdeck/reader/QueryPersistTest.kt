package com.tftdeck.reader

import com.tftdeck.reader.data.DeckToken
import com.tftdeck.reader.data.SearchAxis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 조회 조건 저장: 목록 검색 칩을 한 줄 글자로 바꿨다가 앱을 다시 켤 때 되살린다. */
class QueryPersistTest {
    @Test
    fun `검색 칩을 순서 그대로 저장했다가 되살린다`() {
        val tokens = listOf(
            DeckToken(SearchAxis.CHAMPION, "니달리", "TFT18_Nidalee"),
            DeckToken(SearchAxis.TRAIT, "사냥꾼", null),
            DeckToken.custom("장로 드래곤"),
        )
        assertEquals(tokens, DeckToken.decode(DeckToken.encode(tokens)))
        assertTrue(DeckToken.decode(null).isEmpty())
        assertTrue(DeckToken.decode("").isEmpty())
    }

    @Test
    fun `읽을 수 없는 조각은 버리고 같은 조건은 하나만 남긴다`() {
        val good = DeckToken(SearchAxis.ITEM, "무한의 대검", "TFT_Item_InfinityEdge")
        val broken = "UNKNOWN_AXIS" + Char(31) + Char(31) + "x"
        val encoded = DeckToken.encode(listOf(good, good)) + Char(30) + broken + Char(30) + "garbage"
        assertEquals(listOf(good), DeckToken.decode(encoded))
    }
}
