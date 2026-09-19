package com.tftdeck.reader

import com.tftdeck.reader.data.BucketMeta
import com.tftdeck.reader.data.Deck
import com.tftdeck.reader.data.DeckIdMigration
import com.tftdeck.reader.data.DeckKeys
import com.tftdeck.reader.data.DeckSearch
import com.tftdeck.reader.data.DeckSortMode
import com.tftdeck.reader.data.DeckStats
import com.tftdeck.reader.data.FeedJson
import com.tftdeck.reader.data.GlobalStats
import com.tftdeck.reader.data.MetaComparison
import com.tftdeck.reader.data.ScopeStat
import com.tftdeck.reader.ui.components.sampleText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** metatft 조합 덱(kind meta)과 중국 한정 덱이 섞인 새 덱 목록의 등급·정렬·표시·id 이전 규칙. */
class MetaDeckTest {
    private val buckets = mapOf(
        "goldem" to BucketMeta(label = "골드~에메랄드", listDate = "20260918", default = true),
        "master" to BucketMeta(label = "마스터+", listDate = "20260918"),
    )

    private fun meta(id: String, grade: String?, avg: Double, editorialTier: String? = null) = Deck(
        id = id,
        kind = DeckKeys.KIND_META,
        name = id,
        editorialTier = editorialTier,
        stats = mapOf("goldem" to DeckStats(n = 12_345, avg = avg, adjAvg = avg, grade = grade)),
        cnStats = mapOf("goldem" to DeckStats(n = 900, avg = 2.85, adjAvg = 2.9, grade = "A")),
        mergedGroups = listOf("g-old1", "g-old2"),
        metaCluster = 423014,
        global = GlobalStats(cluster = 423014, stats = mapOf("kr_plat" to ScopeStat(n = 5000, avg = 4.4))),
        metatft = MetaComparison(similarity = 1.0, matchedComp = id, compared = true),
    )

    private fun china(id: String, grade: String?, adjAvg: Double) = Deck(
        id = id,
        kind = DeckKeys.KIND_GROUP,
        name = id,
        stats = mapOf("goldem" to DeckStats(n = 800, avg = adjAvg, adjAvg = adjAvg, grade = grade)),
        metatft = MetaComparison(similarity = 0.4, onlyInChina = true, compared = true),
    )

    @Test
    fun `metatft 조합 덱은 구간 metatft 등급을 쓰고 편집 등급으로 대신하지 않는다`() {
        val deck = meta("m-a", grade = "A", avg = 4.3, editorialTier = "SS")
        assertTrue(deck.isMeta)
        assertFalse(deck.isGlobalOnly)
        assertEquals("A", deck.gradeFor("goldem"))
        // 그 구간에 metatft 표본이 없으면 등급도 없고 목록에서 빠진다. 합쳐진 편집 덱 등급(SS)을 내세우지 않는다.
        assertNull(deck.gradeFor("master"))
        assertFalse(deck.showsEditorialGrade("master"))
        assertFalse(deck.listedIn("master"))
        assertTrue(deck.listedIn("goldem"))
        // 네 수치는 metatft 값, 중국 값은 합쳐진 lol.qq 덱의 참고 수치.
        assertEquals(4.3, deck.displayStats("goldem")?.avg ?: 0.0, 1e-9)
        assertEquals(2.85, deck.chinaStatsFor("goldem")?.avg ?: 0.0, 1e-9)
        assertEquals(3.0, china("g-c", "B", 3.0).chinaStatsFor("goldem")?.avg ?: 0.0, 1e-9)
    }

    @Test
    fun `같은 등급이면 metatft 조합 덱이 중국 한정 덱보다 먼저 온다`() {
        val decks = listOf(
            china("g-s", "S", 2.4),
            meta("m-s2", "S", 4.2),
            meta("m-s1", "S", 4.1),
            china("g-a", "A", 2.6),
            meta("m-a", "A", 4.4),
        )
        val sorted = DeckSearch.sort(decks, DeckSortMode.GRADE, "goldem").map { it.id }
        assertEquals(listOf("m-s1", "m-s2", "g-s", "m-a", "g-a"), sorted)
    }

    @Test
    fun `metatft 조합 덱의 표본 줄은 출처를 metatft 로 적고 lol_qq 기준일을 붙이지 않는다`() {
        assertEquals(
            "n=12,345 · 골드~에메랄드 · metatft · KR 플래+ 4.40등 n=5,000",
            sampleText(meta("m-a", "A", 4.3), "goldem", buckets),
        )
        assertEquals("n=800 · 골드~에메랄드 · 9/18", sampleText(china("g-c", "B", 3.0), "goldem", buckets))
    }

    @Test
    fun `옛 그룹 id 와 옛 metatft 전용 덱 id 를 새 조합 덱 id 로 옮긴다`() {
        val decks = listOf(meta("m-3f2a9c01bd", "A", 4.3), china("g-keep", "B", 3.0))
        val mapping = DeckIdMigration.mapping(decks)
        assertEquals("m-3f2a9c01bd", mapping["g-old1"])
        assertEquals("m-3f2a9c01bd", mapping["g-old2"])
        assertEquals("m-3f2a9c01bd", mapping["m-423014"])
        // 지금도 있는 id 는 옮기지 않는다.
        assertFalse("g-keep" in mapping)
        assertTrue(DeckIdMigration.mapping(listOf(china("g-x", "A", 2.5))).isEmpty())
        // 클러스터 번호가 global 블록에만 있어도 옛 id 를 찾는다.
        val onlyGlobal = meta("m-new", "A", 4.3).copy(metaCluster = null, mergedGroups = emptyList())
        assertEquals(mapOf("m-423014" to "m-new"), DeckIdMigration.mapping(listOf(onlyGlobal)))
    }

    @Test
    fun `새 필드가 있는 덱 JSON 을 읽는다`() {
        val text = """{"decks":[{"id":"m-1","kind":"meta","metaCluster":423001,"mergedGroups":["g-a"],
            "stats":{"goldem":{"n":1500,"avg":4.2,"grade":"S"}},"cnStats":{"goldem":{"n":300,"avg":2.7,"grade":"A"}}}],
            "version":{"metaCompCount":1}}"""
        val feed = FeedJson.decodeFeed(text)
        val deck = feed.decks.single()
        assertTrue(deck.isMeta)
        assertEquals(423001, deck.metaCluster)
        assertEquals(listOf("g-a"), deck.mergedGroups)
        assertEquals("S", deck.gradeFor("goldem"))
        assertEquals(2.7, deck.chinaStatsFor("goldem")?.avg ?: 0.0, 1e-9)
        assertEquals(1, feed.version.metaCompCount)
    }
}
