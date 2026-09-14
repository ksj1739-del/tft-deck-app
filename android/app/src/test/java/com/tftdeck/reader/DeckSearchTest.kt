package com.tftdeck.reader

import com.tftdeck.reader.data.DeckFeed
import com.tftdeck.reader.data.DeckSearch
import com.tftdeck.reader.data.SearchAxis
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * 수집기가 실제로 만든 decks.json으로 검색을 검증한다.
 *
 * 모형 데이터가 아니라 배포되는 파일을 그대로 읽기 때문에, 수집기 스키마가 바뀌면
 * 여기서 먼저 깨진다.
 */
class DeckSearchTest {

    private lateinit var feed: DeckFeed
    private lateinit var search: DeckSearch

    @Before
    fun load() {
        // 단위 테스트의 작업 디렉터리는 모듈 폴더(android/app)다.
        val file = File("src/main/assets/decks.json")
        assertTrue("decks.json이 assets에 없다: ${file.absolutePath}", file.exists())
        feed = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
            .decodeFromString(file.readText())
        search = DeckSearch(feed)
    }

    // -- 파싱 ---------------------------------------------------------------

    @Test
    fun `수집기 결과가 그대로 파싱된다`() {
        assertTrue("덱이 비었다", feed.decks.isNotEmpty())
        assertTrue("패치가 없다", feed.version.patch.isNotBlank())
        assertTrue("catalog 챔피언이 비었다", feed.catalog.champions.isNotEmpty())

        val deck = feed.decks.first()
        assertTrue("유닛이 없다", deck.units.isNotEmpty())
        assertTrue("시너지가 없다", deck.traits.isNotEmpty())
    }

    @Test
    fun `모든 덱 이름이 한글로 만들어진다`() {
        val untranslated = feed.decks.filter { deck ->
            deck.name.isBlank() || deck.name.any { it.code in 0x4E00..0x9FFF }
        }
        assertTrue("중국어가 남은 덱 이름: ${untranslated.map { it.name }}", untranslated.isEmpty())
    }

    @Test
    fun `레벨별 배치의 챔피언은 catalog에서 찾을 수 있다`() {
        val known = feed.catalog.champions.map { it.id }.toSet()
        val missing = feed.decks
            .flatMap { it.boards.values.flatten() + it.early + it.mid }
            .map { it.id }
            .filterNot { it in known }
            .distinct()
        assertTrue("catalog에 없는 챔피언 참조: $missing", missing.isEmpty())
    }

    // -- 덱 코드 -------------------------------------------------------------

    @Test
    fun `덱 코드가 팀 플래너 형식을 지킨다`() {
        val codes = feed.decks.mapNotNull { it.teamCode }
        assertTrue("덱 코드가 하나도 없다", codes.isNotEmpty())

        val suffix = "TFTSet${feed.version.setNumber}"
        codes.forEach { code ->
            assertTrue("접두사가 02가 아니다: ${code.code}", code.code.startsWith("02"))
            assertTrue("세트 접미사가 없다: ${code.code}", code.code.endsWith(suffix))
            // 접두사 2 + 10슬롯 x 3자리 = 32자, 그 뒤에 세트 이름
            assertEquals("코드 길이가 다르다: ${code.code}", 32 + suffix.length, code.code.length)

            val body = code.code.removeSuffix(suffix).drop(2)
            assertTrue("본문이 16진수가 아니다: $body", body.all { it.isDigit() || it in 'a'..'f' })
        }
    }

    // -- 검색 ---------------------------------------------------------------

    @Test
    fun `아이템으로 덱을 역검색한다`() {
        // 어떤 아이템이든 인덱스에 있으면 덱이 나와야 한다.
        val (itemName, usages) = feed.index.item.entries.first { it.value.isNotEmpty() }
            .let { it.key to it.value }

        val hits = search.decksWithItem(itemName)
        assertEquals("인덱스 항목 수와 결과 수가 다르다", usages.size, hits.size)
        assertTrue("결과가 비었다", hits.isNotEmpty())

        // 핵심(main)이 대체(backup)보다 먼저 나와야 한다.
        val firstBackup = hits.indexOfFirst { !it.isCore }
        val lastCore = hits.indexOfLast { it.isCore }
        if (firstBackup >= 0 && lastCore >= 0) {
            assertTrue("핵심과 대체 정렬이 섞였다", lastCore < firstBackup)
        }
    }

    @Test
    fun `한글 이름 일부로 찾는다`() {
        val champion = feed.catalog.champions.first { it.name.length >= 2 }
        val results = search.suggest(champion.name.take(2))
        assertTrue(
            "'${champion.name.take(2)}'로 ${champion.name}을 못 찾았다",
            results.any { it.name == champion.name },
        )
    }

    @Test
    fun `초성으로 찾는다`() {
        // "무한의 대검" -> "ㅁㅎㅇㄷㄱ"
        val target = feed.catalog.items.firstOrNull { it.name == "무한의 대검" }
        assertNotNull("테스트 기준 아이템이 데이터에 없다", target)

        val initials = DeckSearch.initials("무한의 대검")
        assertEquals("ㅁㅎㅇㄷㄱ", initials)

        val results = search.suggest("ㅁㅎㅇ")
        assertTrue("초성 검색이 동작하지 않는다", results.any { it.name == "무한의 대검" })
    }

    @Test
    fun `줄임말로 찾는다`() {
        assertEquals("무대", DeckSearch.abbreviation("무한의 대검"))
        val results = search.suggest("무대")
        assertTrue("줄임말 검색이 동작하지 않는다", results.any { it.name == "무한의 대검" })
    }

    @Test
    fun `영문명으로 찾는다`() {
        val withEn = feed.catalog.champions.first { !it.nameEn.isNullOrBlank() }
        val results = search.suggest(withEn.nameEn!!)
        assertTrue("영문명 '${withEn.nameEn}'로 못 찾았다", results.any { it.name == withEn.name })
    }

    @Test
    fun `빈 검색어는 아무것도 반환하지 않는다`() {
        assertTrue(search.suggest("").isEmpty())
        assertTrue(search.suggest("   ").isEmpty())
    }

    @Test
    fun `없는 검색어는 결과가 없다`() {
        assertTrue(search.suggest("존재하지않는이름zzz").isEmpty())
    }

    // -- 필터 ---------------------------------------------------------------

    @Test
    fun `티어 필터가 동작한다`() {
        val tier = feed.decks.first().tier
        val filtered = search.filter(tiers = setOf(tier))
        assertTrue("필터 결과가 비었다", filtered.isNotEmpty())
        assertTrue("다른 티어가 섞였다", filtered.all { it.tier == tier })
    }

    @Test
    fun `중국 한정 필터는 metatft에 없는 덱만 남긴다`() {
        val onlyChina = search.filter(onlyInChinaOnly = true)
        assertTrue("대조된 덱이 섞였다", onlyChina.all { it.metatft.onlyInChina })
        assertEquals(
            "version의 집계와 실제 덱 수가 다르다",
            feed.version.onlyInChinaCount,
            onlyChina.size,
        )
    }

    @Test
    fun `필터가 없으면 모든 덱이 나온다`() {
        assertEquals(feed.decks.size, search.filter().size)
    }

    // -- 인덱스 무결성 --------------------------------------------------------

    @Test
    fun `검색 인덱스가 실제 덱을 가리킨다`() {
        val ids = feed.decks.map { it.id }.toSet()
        val dangling = buildList {
            feed.index.item.values.flatten().forEach { if (it.deck !in ids) add(it.deck) }
            listOf(feed.index.champion, feed.index.trait, feed.index.component, feed.index.augment)
                .forEach { axis -> axis.values.flatten().forEach { if (it !in ids) add(it) } }
        }.distinct()
        assertTrue("없는 덱을 가리키는 인덱스: $dangling", dangling.isEmpty())
    }

    @Test
    fun `모든 검색 축이 비어 있지 않다`() {
        SearchAxis.entries.forEach { axis ->
            val count = when (axis) {
                SearchAxis.CHAMPION -> feed.index.champion.size
                SearchAxis.ITEM -> feed.index.item.size
                SearchAxis.COMPONENT -> feed.index.component.size
                SearchAxis.TRAIT -> feed.index.trait.size
                SearchAxis.AUGMENT -> feed.index.augment.size
            }
            assertFalse("${axis.label} 인덱스가 비었다", count == 0)
        }
    }
}
