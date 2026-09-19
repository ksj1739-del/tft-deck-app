package com.tftdeck.reader

import com.tftdeck.reader.data.Catalog
import com.tftdeck.reader.data.CatalogEntry
import com.tftdeck.reader.data.Deck
import com.tftdeck.reader.data.DeckFeed
import com.tftdeck.reader.data.DeckSearch
import com.tftdeck.reader.data.DeckToken
import com.tftdeck.reader.data.GlobalStats
import com.tftdeck.reader.data.IdIndex
import com.tftdeck.reader.data.ItemRef
import com.tftdeck.reader.data.ItemUsage
import com.tftdeck.reader.data.SearchAxis
import com.tftdeck.reader.data.SearchIndex
import com.tftdeck.reader.data.TraitRef
import com.tftdeck.reader.data.Unit as DeckUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 덱 목록 검색 줄(다중 선택 조건)과 별칭·설명 대체 규칙.
 *
 * 동봉 스냅샷 대신 덱을 직접 만들어 규칙만 본다 — 스냅샷은 수집기를 돌릴 때마다 바뀌고,
 * 별칭·설명이 실린 뒤에도 대체 규칙은 그대로 검사할 수 있어야 한다.
 */
class DeckTokenFilterTest {

    private fun unit(id: String, name: String, rank: Int? = null, items: List<ItemRef> = emptyList()) =
        DeckUnit(id = id, name = name, carryRank = rank, items = items)

    private fun trait(name: String, count: Int, style: Int = 1) =
        TraitRef(id = "T_$name", name = name, count = count, style = style)

    // 니달리와 시비르가 둘 다 들어간 덱은 hunter 하나뿐이다.
    private val hunter = Deck(
        id = "hunter",
        name = "니달리 · 4 사냥꾼 3 협곡야수 2 선봉대",
        mainTraits = listOf(trait("사냥꾼", 4, 2), trait("협곡야수", 3)),
        units = listOf(
            unit("TFT_Nidalee", "니달리", 1),
            unit("TFT_Sivir", "시비르", 2),
            unit("TFT_Malphite", "말파이트", 3),
            unit("TFT_Taric", "타릭"),
        ),
        global = GlobalStats(levelling = "빠른 8레벨"),
        finalLevel = 8,
    )

    // 별칭·설명이 없고 운영 방식도 없다(대체 설명은 'N레벨 완성'으로 시작한다).
    private val elder = Deck(
        id = "elder",
        name = "장로 드래곤 · 7 협곡야수 2 선봉대",
        mainTraits = listOf(trait("협곡야수", 7, 3)),
        units = listOf(unit("TFT_Elder", "장로 드래곤", 1), unit("TFT_Nidalee", "니달리", 2), unit("TFT_Taric", "타릭")),
        finalLevel = 9,
    )

    // 수집기가 별칭·설명을 실은 덱.
    private val ashe = Deck(
        id = "ashe",
        name = "애쉬 · 4 원시 2 사냥꾼",
        alias = "원시 애쉬 리롤",
        summary = "6레벨 리롤 · 애쉬 3성",
        mainTraits = listOf(trait("원시", 4, 3)),
        units = listOf(unit("TFT_Ashe", "애쉬", 1), unit("TFT_Sivir", "시비르", 2)),
    )

    // 대소문자 매칭을 보려고 이름에 영문을 둔다.
    private val arcana = Deck(
        id = "arcana",
        name = "Ahri · 5 Arcana",
        units = listOf(unit("TFT_Ahri", "아리", 1)),
    )

    private val feed = DeckFeed(
        decks = listOf(hunter, elder, ashe, arcana),
        index = SearchIndex(
            champion = mapOf(
                "니달리" to listOf("hunter", "elder"),
                "시비르" to listOf("hunter", "ashe"),
                "장로 드래곤" to listOf("elder"),
                "애쉬" to listOf("ashe"),
                "타릭" to listOf("hunter", "elder"),
                "말파이트" to listOf("hunter"),
                "아리" to listOf("arcana"),
            ),
            trait = mapOf(
                "협곡야수" to listOf("hunter", "elder"),
                "사냥꾼" to listOf("hunter", "ashe"),
                "원시" to listOf("ashe"),
            ),
            item = mapOf(
                // 핵심(main)과 대체(backup) 모두 '그 아이템이 들어가는 덱'이다.
                "구인수의 격노검" to listOf(ItemUsage("hunter", "니달리", "main"), ItemUsage("ashe", "애쉬", "backup")),
            ),
            component = mapOf("곡궁" to listOf("hunter", "ashe")),
            augment = mapOf("사냥의 전율" to listOf("hunter")),
            byId = IdIndex(champion = mapOf("TFT_Elder" to listOf("elder"))),
        ),
        catalog = Catalog(
            champions = listOf(
                CatalogEntry(id = "TFT_Nidalee", name = "니달리", nameEn = "Nidalee", cost = 1),
                CatalogEntry(id = "TFT_Sivir", name = "시비르", nameEn = "Sivir", cost = 2),
                CatalogEntry(id = "TFT_Elder", name = "장로 드래곤", nameEn = "Elder Dragon", cost = 5),
                CatalogEntry(id = "TFT_Ashe", name = "애쉬", nameEn = "Ashe", cost = 3),
                CatalogEntry(id = "TFT_Taric", name = "타릭", nameEn = "Taric", cost = 1),
                CatalogEntry(id = "TFT_Malphite", name = "말파이트", nameEn = "Malphite", cost = 1),
                CatalogEntry(id = "TFT_Ahri", name = "아리", nameEn = "Ahri", cost = 4),
            ),
            traits = listOf(
                CatalogEntry(id = "T_협곡야수", name = "협곡야수"),
                CatalogEntry(id = "T_사냥꾼", name = "사냥꾼"),
                CatalogEntry(id = "T_원시", name = "원시"),
            ),
            items = listOf(
                CatalogEntry(id = "TFT_Item_GuinsoosRageblade", name = "구인수의 격노검", nameEn = "Guinsoo's Rageblade"),
                CatalogEntry(id = "TFT_Item_RecurveBow", name = "곡궁", nameEn = "Recurve Bow"),
            ),
            augments = listOf(CatalogEntry(id = "TFT_Augment_Thrill", name = "사냥의 전율")),
        ),
    )

    private val search = DeckSearch(feed)
    private val all = feed.decks

    private fun ids(decks: List<Deck>) = decks.map { it.id }

    private fun champ(name: String) = DeckToken(SearchAxis.CHAMPION, name, null)

    // -- 조건 AND ----------------------------------------------------------------

    @Test
    fun `조건이 없으면 목록을 순서 그대로 돌려준다`() {
        val reversed = all.reversed()
        assertEquals(ids(reversed), ids(search.filterByTokens(reversed, emptyList())))
    }

    @Test
    fun `유닛 두 개를 고르면 둘 다 가진 덱만 남는다`() {
        assertEquals(listOf("hunter", "elder"), ids(search.filterByTokens(all, listOf(champ("니달리")))))
        assertEquals(listOf("hunter"), ids(search.filterByTokens(all, listOf(champ("니달리"), champ("시비르")))))
        // 순서는 넘긴 목록 그대로다(정렬은 목록 화면이 따로 한다).
        assertEquals(
            listOf("ashe", "hunter"),
            ids(search.filterByTokens(listOf(ashe, elder, hunter), listOf(champ("시비르")))),
        )
    }

    @Test
    fun `다른 축과 사용자 지정도 AND 로 묶인다`() {
        val tokens = listOf(
            DeckToken(SearchAxis.TRAIT, "사냥꾼", "T_사냥꾼"),
            champ("시비르"),
            DeckToken.custom("리롤"),
        )
        assertEquals(listOf("ashe"), ids(search.filterByTokens(all, tokens)))
        // 어느 덱도 동시에 만족하지 않으면 빈 목록.
        assertTrue(search.filterByTokens(all, listOf(champ("애쉬"), champ("니달리"))).isEmpty())
    }

    // -- 축별 매칭 ----------------------------------------------------------------

    @Test
    fun `축별 조건은 기존 decksFor 와 같은 덱을 고른다`() {
        val cases = listOf(
            SearchAxis.CHAMPION to "타릭",
            SearchAxis.TRAIT to "협곡야수",
            SearchAxis.ITEM to "구인수의 격노검",
            SearchAxis.COMPONENT to "곡궁",
            SearchAxis.AUGMENT to "사냥의 전율",
        )
        cases.forEach { (axis, name) ->
            val expected = search.decksFor(axis, name).map { it.id }.toSet()
            assertTrue("$axis $name 에 맞는 덱이 없다", expected.isNotEmpty())
            assertEquals("$axis $name", expected, search.matchingIds(DeckToken(axis, name, null)))
        }
        // 아이템은 대체(backup)로 드는 덱도 들어간다.
        assertEquals(setOf("hunter", "ashe"), search.matchingIds(DeckToken(SearchAxis.ITEM, "구인수의 격노검", null)))
    }

    @Test
    fun `이름으로 못 찾으면 DA id 로 찾는다`() {
        // 피드가 바뀌어 이름이 달라져도 id 는 같다.
        assertEquals(setOf("elder"), search.matchingIds(DeckToken(SearchAxis.CHAMPION, "옛 이름", "TFT_Elder")))
        assertTrue(search.matchingIds(DeckToken(SearchAxis.CHAMPION, "없는 유닛", null)).isEmpty())
    }

    // -- 사용자 지정 ----------------------------------------------------------------

    @Test
    fun `사용자 지정은 별칭·이름·설명에 부분 일치한다`() {
        fun custom(text: String) = search.filterByTokens(all, listOf(DeckToken.custom(text))).map { it.id }.toSet()

        assertEquals(setOf("ashe"), custom("원시 애쉬")) // 수집기 별칭
        assertEquals(setOf("ashe"), custom("3성")) // 수집기 설명
        assertEquals(setOf("hunter", "elder"), custom("협곡야수")) // elder 는 대체 별칭, hunter 는 긴 이름에서
        assertEquals(setOf("hunter"), custom("빠른 8")) // 대체 설명('빠른 8레벨 · 니달리·시비르 캐리')
        assertEquals(setOf("elder"), custom("9레벨 완성")) // 운영 방식이 없을 때의 대체 설명
        assertTrue(custom("없는 말").isEmpty())
    }

    @Test
    fun `사용자 지정은 대소문자·띄어쓰기를 가리지 않고 초성도 받는다`() {
        fun matches(deck: Deck, text: String) = DeckSearch.matchesText(deck, text)

        assertTrue(matches(arcana, "ahri"))
        assertTrue(matches(arcana, "ARCANA"))
        assertTrue(matches(elder, "장로드래곤")) // '협곡야수 장로 드래곤'
        assertTrue(matches(elder, "ㅈㄹㄷㄹㄱ")) // 초성
        assertTrue(matches(hunter, "ㅅㄴㄲ")) // '사냥꾼 니달리'의 초성
        assertTrue(matches(hunter, "  니달리  ")) // 앞뒤 공백
        assertFalse(matches(arcana, "니달리"))
    }

    // -- 후보 ----------------------------------------------------------------------

    @Test
    fun `후보는 기존 자동완성을 따르고 사용자 지정 후보가 맨 끝이다`() {
        val candidates = search.suggestTokens("니달")
        assertEquals(DeckToken(SearchAxis.CHAMPION, "니달리", "TFT_Nidalee"), candidates.first().token)
        val last = candidates.last()
        assertTrue(last.token.isCustom)
        assertEquals("니달", last.token.name)
        assertEquals("사용자 지정", last.token.axisLabel)
        assertEquals("\"니달\"", last.token.chipLabel)

        // 초성·영문도 기존 자동완성과 같이 받는다.
        assertEquals("니달리", search.suggestTokens("ㄴㄷㄹ").first().token.name)
        assertEquals("시비르", search.suggestTokens("sivir").first().token.name)
        assertEquals("유닛", search.suggestTokens("sivir").first().token.axisLabel)
        assertTrue(search.suggestTokens("   ").isEmpty())
    }

    @Test
    fun `이미 고른 조건은 후보에서 뺀다`() {
        val picked = listOf(DeckToken(SearchAxis.CHAMPION, "니달리", "TFT_Nidalee"))
        val candidates = search.suggestTokens("니달", selected = picked)
        assertTrue(candidates.none { it.token.axis == SearchAxis.CHAMPION && it.token.name == "니달리" })
        assertTrue(candidates.last().token.isCustom)

        // 사용자 지정도 같은 글자(대소문자·앞뒤 공백 무시)를 이미 골랐으면 빠진다.
        val customPicked = listOf(DeckToken.custom(" 니달 "))
        assertTrue(search.suggestTokens("니달", selected = customPicked).none { it.token.isCustom })
        assertEquals(DeckToken.custom("Ahri").key, DeckToken.custom("  ahri ").key)
    }

    @Test
    fun `후보의 덱 수는 지금 목록에 더했을 때 남는 수다`() {
        fun sivirCount(within: List<Deck>?) =
            search.suggestTokens("시비", within = within).first { it.token.name == "시비르" }.deckCount

        assertEquals(2, sivirCount(null)) // 전체: hunter, ashe
        assertEquals(1, sivirCount(listOf(hunter, elder))) // 니달리로 좁힌 목록에서는 hunter 하나
        assertEquals(0, sivirCount(listOf(elder)))
    }

    // -- 별칭·설명 대체 규칙 --------------------------------------------------------

    @Test
    fun `별칭이 없으면 대표 시너지와 캐리로 만든다`() {
        assertEquals("사냥꾼 니달리", hunter.displayAlias) // 활성 수가 가장 많은 주 특성 + 첫 캐리
        assertEquals("협곡야수 장로 드래곤", elder.displayAlias)
        assertEquals("원시 애쉬 리롤", ashe.displayAlias) // 수집기 별칭이 있으면 그대로

        // 활성 수가 같으면 앞선 특성, 캐리는 carryRank 순서(목록 순서가 아니다).
        val tie = Deck(
            name = "드레이븐 · 3 처형자 3 나무정령",
            mainTraits = listOf(trait("처형자", 3, 2), trait("나무정령", 3, 1)),
            units = listOf(unit("a", "이즈리얼", 3), unit("b", "드레이븐", 1), unit("c", "마오카이", 2)),
        )
        assertEquals("처형자 드레이븐", tie.displayAlias)

        // 캐리를 모르면 이름의 ' · ' 앞부분을 쓴다. 주 특성이 없으면 전체 특성에서 고른다.
        val noCarry = Deck(name = "케일 · 3 햇빛 2 요정", traits = listOf(trait("요정", 2), trait("햇빛", 3)))
        assertEquals("햇빛 케일", noCarry.displayAlias)

        // 특성이 없으면 캐리만, 특성도 캐리도 없으면 이름 그대로.
        assertEquals("아리", arcana.displayAlias)
        assertEquals("무언가", Deck(name = "무언가").displayAlias)
    }

    @Test
    fun `설명이 없으면 운영과 캐리 두 명으로 만든다`() {
        assertEquals("빠른 8레벨 · 니달리·시비르 캐리", hunter.displaySummary)
        assertEquals("9레벨 완성 · 장로 드래곤·니달리 캐리", elder.displaySummary) // 운영 방식이 없으면 'N레벨 완성'
        assertEquals("6레벨 리롤 · 애쉬 3성", ashe.displaySummary) // 수집기 설명이 있으면 그대로

        // 캐리가 한 명이면 한 명, 운영 정보가 없으면 캐리만, 아무것도 없으면 빈 글자.
        val single = Deck(units = listOf(unit("x", "아리", 1)), global = GlobalStats(levelling = "7레벨 리롤"))
        assertEquals("7레벨 리롤 · 아리 캐리", single.displaySummary)
        assertEquals("아리 캐리", arcana.displaySummary)
        assertEquals("", Deck().displaySummary)
        assertEquals("8레벨 완성", Deck(finalLevel = 8).displaySummary)
    }
}
