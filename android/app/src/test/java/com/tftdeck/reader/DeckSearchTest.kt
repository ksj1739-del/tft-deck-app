package com.tftdeck.reader

import com.tftdeck.reader.data.BuildupOrigin
import com.tftdeck.reader.data.BuildupPlanner
import com.tftdeck.reader.data.CatalogIndex
import com.tftdeck.reader.data.Deck
import com.tftdeck.reader.data.DeckFeed
import com.tftdeck.reader.data.DeckSearch
import com.tftdeck.reader.data.DeckSort
import com.tftdeck.reader.data.DeckSortMode
import com.tftdeck.reader.data.FeedFreshness
import com.tftdeck.reader.data.FeedJson
import com.tftdeck.reader.data.FeedVersion
import com.tftdeck.reader.data.IdIndex
import com.tftdeck.reader.data.SearchAxis
import com.tftdeck.reader.data.StatsParser
import com.tftdeck.reader.data.withAugmentDescriptions
import com.tftdeck.reader.data.withEditorial
import com.tftdeck.reader.ui.components.formatPick
import com.tftdeck.reader.ui.components.sampleText
import com.tftdeck.reader.ui.formatAvg
import com.tftdeck.reader.ui.formatCount
import com.tftdeck.reader.ui.formatPct
import com.tftdeck.reader.ui.formatShortDate
import com.tftdeck.reader.ui.iconUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.math.roundToInt

/**
 * (a) 수집기가 실제로 만든 decks.json(앱 동봉 스냅샷)으로 검색을 검증한다.
 *
 * 모형 데이터가 아니라 배포되는 파일을 그대로 읽기 때문에, 수집기 스키마가 바뀌면
 * 여기서 먼저 깨진다. v1 파일이든 v2 파일이든 같은 모델로 통과해야 한다.
 */
class DeckSearchTest {

    private lateinit var feed: DeckFeed
    private lateinit var search: DeckSearch

    @Before
    fun load() {
        // 단위 테스트의 작업 디렉터리는 모듈 폴더(android/app)다.
        val file = File("src/main/assets/decks.json")
        assertTrue("decks.json이 assets에 없다: ${file.absolutePath}", file.exists())
        feed = FeedJson.decodeFeed(file.readText())
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
    fun `단계·변형 배치의 유닛은 catalog 챔피언이나 소환물에서 찾을 수 있다`() {
        // v1 파일은 편집 단계가 없어 stages 가 대표 보드 하나뿐이다. 그래도 같은 규칙으로 통과해야 한다.
        val known = (feed.catalog.champions + feed.catalog.pets).map { it.id }.toSet()
        val missing = feed.decks
            .flatMap { deck ->
                deck.stages.flatMap { stage -> stage.units.map { it.id } } +
                    deck.moreEditorials.flatMap { extra -> extra.stages.flatMap { stage -> stage.units.map { it.id } } } +
                    deck.variants.flatMap { variant -> variant.units.map { it.id } }
            }
            .filterNot { it in known }
            .distinct()
        assertTrue("catalog에 없는 유닛 참조: $missing", missing.isEmpty())
    }

    // -- v2 계약이 실제 파일에서 채워지는지 ------------------------------------------
    // 픽스처에만 있고 수집기가 만들지 않는 필드는 테스트가 모두 초록인데도 화면을 조용히 비게 만든다.

    @Test
    fun `픽스처가 쓰는 필드는 실제 수집 결과에도 있다`() {
        assumeTrue("v2 파일에서만 검사한다", feed.version.schemaVersion >= 2)
        val fixture = javaClass.classLoader?.getResource("decks_v2_sample.json")?.readText()
        assertNotNull("테스트 리소스 decks_v2_sample.json 이 없다", fixture)
        val real = jsonPaths(File("src/main/assets/decks.json").readText())
        val onlyInFixture = jsonPaths(fixture!!) - real - FIXTURE_ONLY_PATHS
        assertTrue(
            "픽스처에만 있는 필드(수집기가 만들지 않는다): $onlyInFixture\n" +
                "수집기가 싣게 고치거나, 그날 데이터에만 없는 조건부 필드면 FIXTURE_ONLY_PATHS 에 이유와 함께 넣는다.",
            onlyInFixture.isEmpty(),
        )
    }

    @Test
    fun `수집한 편집 덱은 모두 앱에서 닿고 작가를 바꿔 볼 수 있다`() {
        assumeTrue("v2 파일에서만 검사한다", feed.version.schemaVersion >= 2)
        val reachable = feed.decks.sumOf { (if (it.editorial != null) 1 else 0) + it.moreEditorials.size }
        assertEquals("editorial + moreEditorials 가 editorialCount 와 다르다", feed.version.editorialCount, reachable)

        val catalog = CatalogIndex(feed.catalog)
        feed.decks.filter { it.moreEditorials.isNotEmpty() }.forEach { deck ->
            deck.moreEditorials.forEach { extra ->
                val shown = deck.withEditorial(extra, catalog)
                assertEquals(extra.id, shown.editorial?.id)
                assertTrue("작가를 바꾼 보드가 비었다: ${deck.id}/${extra.id}", shown.units.isNotEmpty())
                assertTrue("유닛 이름이 풀리지 않았다: ${shown.units.map { it.name }}", shown.units.none { it.name == it.id })
                assertEquals(extra.teamCode?.code ?: deck.teamCode?.code, shown.teamCode?.code)
            }
        }
    }

    @Test
    fun `대응 덱이 없는 불리한 상대는 metatft 이름이 있고 한글로 풀린다`() {
        val catalog = CatalogIndex(feed.catalog)
        val orphans = feed.decks.flatMap { it.global?.counters.orEmpty() }.filter { it.deck == null }
        assertTrue("이름 없는 상대: ${orphans.filter { it.name.isNullOrBlank() }.take(3)}", orphans.all { !it.name.isNullOrBlank() })
        val tokens = orphans.flatMap { it.name.orEmpty().split(',') }.map { it.trim() }.filter { it.isNotEmpty() }
        val unresolved = tokens.filter { catalog.unit(it) == null && catalog.traits[it] == null }
        // 사전에 아직 없는 새 유닛은 원문으로 남을 수 있다. 대부분 풀리기만 하면 된다.
        assertTrue("catalog 로 풀리지 않는 이름 토큰이 많다: ${unresolved.distinct()}", unresolved.size * 10 <= tokens.size)
    }

    @Test
    fun `보드에 쓰인 소환물은 아이콘이 있다`() {
        val icons = feed.catalog.pets.associate { it.id to it.icon }
        val used = feed.decks.flatMap { deck ->
            deck.units.filter { it.isPet }.map { it.id } +
                deck.editorials.flatMap { editorial -> editorial.stages.flatMap { stage -> stage.units.filter { it.isPet }.map { it.id } } }
        }.distinct()
        val blank = used.filter { icons[it].isNullOrBlank() }
        assertTrue("아이콘 없는 소환물(무엇인지 알 수 없는 회색 칸이 된다): $blank", blank.isEmpty())
    }

    @Test
    fun `변형 수치에도 픽률이 있다`() {
        val stats = feed.decks.flatMap { deck -> deck.variants.flatMap { it.stats.values } }
        assertEquals("픽률 없는 변형 수치(변형 행의 픽률 칸이 늘 '-')", 0, stats.count { it.pick == null })
    }

    @Test
    fun `동봉 도감의 증강 설명으로도 증강을 찾는다`() {
        val file = File("src/main/assets/stats/augments.json")
        assumeTrue("도감 스냅샷이 없다: ${file.absolutePath}", file.exists())
        val augments = StatsParser.parseAugments(file.readText())
        assertNotNull("augments.json 파싱 실패", augments)
        val descriptions = augments!!.augments.filter { it.desc.isNotBlank() }.associate { it.id to it.desc }
        val merged = feed.withAugmentDescriptions(descriptions)
        assertTrue("덱 피드의 증강에 설명이 하나도 붙지 않았다", merged.catalog.augments.any { !it.desc.isNullOrBlank() })

        // 이름에는 없고 설명에만 있는 단어로 그 증강이 후보에 나와야 한다.
        val probe = merged.catalog.augments.asSequence()
            .filter { it.name in merged.index.augment }
            .mapNotNull { entry ->
                val keys = DeckSearch.keysFor(entry.name, entry.nameEn)
                DeckSearch.descWords(entry.desc).firstOrNull { word -> keys.none { it.contains(word) } }
                    ?.let { word -> entry.name to word }
            }
            .firstOrNull()
        assertNotNull("설명 단어로 검색해 볼 증강이 없다", probe)
        val (name, word) = probe!!
        assertTrue(
            "설명 단어 '$word'로 증강 '$name'을 못 찾았다",
            DeckSearch(merged).suggest(word, limit = 1000).any { it.axis == SearchAxis.AUGMENT && it.name == name },
        )
        assertTrue(
            "설명을 합치기 전에는 그 단어로 찾지 못해야 한다(이름으로 맞은 것이 아님을 확인)",
            search.suggest(word, limit = 1000).none { it.axis == SearchAxis.AUGMENT && it.name == name },
        )
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
        val champion = feed.catalog.champions.first { it.name.length >= 2 && it.name in feed.index.champion }
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
        val withEn = feed.catalog.champions.first { !it.nameEn.isNullOrBlank() && it.name in feed.index.champion }
        val results = search.suggest(withEn.nameEn!!)
        assertTrue("영문명 '${withEn.nameEn}'로 못 찾았다", results.any { it.name == withEn.name })
    }

    @Test
    fun `검색 후보에 catalog id가 붙는다`() {
        val suggestion = search.suggest("무한의 대검").first { it.name == "무한의 대검" }
        assertEquals(feed.catalog.items.first { it.name == "무한의 대검" }.id, suggestion.id)
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
        val onlyChina = search.filter(onlyChina = true)
        assertTrue("대조된 덱이 섞였다", onlyChina.all { it.isOnlyInChina })
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
        val byId = feed.index.byId
        val dangling = buildList {
            feed.index.item.values.flatten().forEach { if (it.deck !in ids) add(it.deck) }
            listOf(
                feed.index.champion, feed.index.trait, feed.index.component, feed.index.augment,
                byId.champion, byId.item, byId.trait, byId.augment,
            ).forEach { axis -> axis.values.flatten().forEach { if (it !in ids) add(it) } }
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

    private companion object {
        /** 그날 수집 결과에 없을 수 있는 조건부 필드. 픽스처에만 있어도 되는 이유를 함께 적는다. 지금은 없다. */
        val FIXTURE_ONLY_PATHS: Set<String> = emptySet()

        /** 키가 id·이름·구간 같은 값인 맵. 경로에서 '*' 로 접는다. */
        val DYNAMIC_MAPS = setOf(
            "version.sources", "buckets", "scopes",
            "decks[].stats", "decks[].variants[].stats", "decks[].variants[].precise", "decks[].global.stats",
            "decks[].positions",
            "index.item", "index.component", "index.champion", "index.trait", "index.augment",
            "index.byId.champion", "index.byId.item", "index.byId.trait", "index.byId.augment",
        )

        /** JSON 의 필드 경로 집합. 배열은 "[]", 동적 키 맵은 "*" 로 접는다. 수집기 진단(collector)은 뺀다. */
        fun jsonPaths(text: String): Set<String> {
            val out = mutableSetOf<String>()
            fun walk(node: JsonElement, path: String) {
                when (node) {
                    is JsonObject -> {
                        val dynamic = path in DYNAMIC_MAPS
                        node.forEach { (key, value) ->
                            if (path.isEmpty() && key == "collector") return@forEach
                            val child = (if (path.isEmpty()) "" else "$path.") + (if (dynamic) "*" else key)
                            out += child
                            walk(value, child)
                        }
                    }
                    is JsonArray -> node.forEach { walk(it, "${path}[]") }
                    else -> {}
                }
            }
            walk(Json.parseToJsonElement(text), "")
            return out
        }
    }
}

/**
 * (b) 설계 계약(§5.2·§13.2)을 확장한 v2 픽스처로 모델·필터·정렬·빌드업 규칙을 검증한다.
 *
 * 그룹 3개(장로 드래곤: 편집 덱·pet·단계·실측 배치·증강·변형 2개·빌드업 / 아펠리오스: 좌표 없는 대표 보드
 * / 요릭: 중국 한정·표본 부족)와 편집 독립 덱 1개로 이루어져 있다.
 */
class DeckFeedV2Test {

    private lateinit var feed: DeckFeed
    private lateinit var search: DeckSearch

    @Before
    fun load() {
        val resource = javaClass.classLoader?.getResource("decks_v2_sample.json")
        assertNotNull("테스트 리소스 decks_v2_sample.json 이 없다", resource)
        feed = FeedJson.decodeFeed(resource!!.readText())
        search = DeckSearch(feed)
    }

    private fun deck(id: String): Deck = feed.decks.first { it.id == id }

    private fun ids(decks: List<Deck>): List<String> = decks.map { it.id }

    // -- 파싱 ---------------------------------------------------------------

    @Test
    fun `v2 최상위 메타가 파싱된다`() {
        assertEquals(2, feed.version.schemaVersion)
        assertEquals("18.2", feed.version.patchGlobal)
        assertEquals("2026-09-15", feed.version.statDate)
        assertEquals(5, feed.buckets.size)
        assertEquals("goldem", feed.defaultBucket)
        assertEquals("20260914", feed.buckets.getValue("goldem").detailDate)
        // metatft 스코프 3개 + 중국 스코프(수집기가 数据检索器 판 수를 싣는다)
        assertEquals(4, feed.scopes.size)
        assertEquals(6_799_416L, feed.scopes.getValue("glob_plat").boards)
        assertEquals(4_017_601L, feed.scopes.getValue("cn_plat").games)
        assertEquals("4+", feed.scopes.getValue("cn_plat").tier)
        assertEquals(3.90, feed.gradeCuts.s, 1e-9)
        assertEquals(300, feed.gradeCuts.minSample)
        assertEquals(1, feed.catalog.pets.size)
        assertEquals("pet", feed.catalog.pets.single().kind)
    }

    @Test
    fun `statsFor와 gradeFor는 구간 등급을 주고 없으면 편집 등급으로 대신한다`() {
        val elder = deck(ELDER)
        assertEquals(17059, elder.statsFor("goldem")!!.n)
        assertEquals("S", elder.gradeFor("goldem"))
        assertFalse(elder.showsEditorialGrade("goldem"))

        // 마스터+ 는 표본 부족(grade null) → 편집 등급 SS 로 대신한다. trend 가 null 로 와도 기본값.
        assertNull(elder.statsFor("master")!!.grade)
        assertEquals("SS", elder.gradeFor("master"))
        assertTrue(elder.showsEditorialGrade("master"))
        assertEquals("flat", elder.statsFor("master")!!.trend)

        // 편집 등급도 없는 소표본 그룹은 null(화면에서 '표본 부족')
        assertNull(deck(YORICK).gradeFor("goldem"))

        // 편집 독립 덱은 통계가 없어 편집 등급
        assertNull(deck(EDITORIAL).statsFor("goldem"))
        assertEquals("S", deck(EDITORIAL).gradeFor("goldem"))

        // v1 덱은 editorialTier 가 없고 tier 자체가 편집 등급이다.
        val v1 = Deck(id = "14266", tier = "SS")
        assertEquals("SS", v1.gradeFor("goldem"))
        assertTrue(v1.isEditorialDeck)
        assertEquals(1, v1.stages.size)
    }

    @Test
    fun `단계 보드가 pet 표시와 함께 파싱된다`() {
        val elder = deck(ELDER)
        assertEquals(listOf("early", "mid", "final"), elder.stages.map { it.key })
        val early = elder.stages.first()
        assertEquals(5, early.level)
        assertEquals("2-3", early.round)
        assertTrue(early.units.first { it.id == PET }.isPet)
        assertFalse(early.units.first { it.id == "DA_18_Yorick" }.isPet)
        assertNull(elder.stages.last().round)
        assertTrue(elder.units.first { it.id == PET }.isPet)

        // 편집 덱이 없는 그룹은 대표 보드 하나짜리 단계. 좌표가 없다.
        val aphelios = deck(APHELIOS)
        assertEquals(1, aphelios.stages.size)
        assertEquals(aphelios.units.map { it.id }, aphelios.stages.single().units.map { it.id })
        assertNull(aphelios.units.first().row)

        // 캐리 3명은 carryRank 순
        assertEquals(listOf("DA_18_ElderDragon", "DA_Draven18", "DA_Taric18"), elder.carries.map { it.id })
    }

    @Test
    fun `그룹 부가 수치가 파싱된다`() {
        val elder = deck(ELDER)
        assertEquals("v-1a2b3c4d5e", elder.representativeVariant?.id)
        assertEquals(listOf("v-9f8e7d6c5b"), elder.otherVariants.map { it.id })
        assertEquals(0.21, elder.positions.getValue("DA_Draven18").first().use, 1e-9)
        assertEquals(listOf(3.23, 2.68, 4.33), elder.augmentStats.first().stage)
        assertNull(elder.augmentStats[1].stage[0])
        assertEquals(listOf(true, false, false), elder.augmentStats[1].stageLowSample)
        assertEquals("DA_Taric18", elder.itemWearers.first().wearers.first().id)
        assertEquals("g-1c2d3e4f5a", elder.global?.counters?.first()?.deck)
        assertEquals(423009L, elder.global?.cluster)
        assertEquals(8, elder.global!!.stats.getValue("glob_plat").places.size)
        assertEquals(0.668, elder.keyUnits.first().star2!!, 1e-9)
        assertEquals(9, elder.statsFor("goldem")!!.precise?.finalLevel)
        assertEquals("DA_18_Ashe, DA_18_Sivir", elder.global?.counters?.get(1)?.name)
        // 변형은 성급을 모르고(null), 픽률은 그룹과 같은 4수치 줄에 쓴다.
        assertNull(elder.otherVariants.single().units.first().star)
        assertEquals(0.0005, elder.otherVariants.single().stats.getValue("goldem").pick!!, 1e-9)
        // 증강 성적·실측 배치를 받은 구간
        assertEquals("goldem", elder.detailBucket)
        assertNull(deck(APHELIOS).detailBucket)

        // 출처 배지 판정
        assertTrue(elder.hasEditorial)
        assertTrue(elder.isEditorialStale)
        assertTrue(elder.hasKrSample)
        assertFalse(deck(APHELIOS).hasEditorial)
        assertTrue(deck(YORICK).isOnlyInChina)
        assertEquals(
            "n=17,059 · 골드~에메랄드 · 9/15 · KR 플래+ 4.20등 n=9,849",
            sampleText(elder, "goldem", feed.buckets),
        )
    }

    // -- 필터·정렬 -------------------------------------------------------------

    @Test
    fun `구간별 네 가지 정렬`() {
        val goldem = "goldem"
        // 목록 필터는 그 구간 등급이 있는 덱과 편집 독립 덱만 남긴다(요릭은 표본 250이라 등급이 없어 빠진다).
        assertEquals(listOf(ELDER, APHELIOS, EDITORIAL), ids(search.filter(bucket = goldem)))
        // 고정한 덱은 등급이 없어도 남긴다.
        assertEquals(ids(feed.decks), ids(search.filter(bucket = goldem, alwaysShow = setOf(YORICK))))
        // 정렬 규칙은 필터와 무관하게 모든 덱으로 검사한다.
        val all = feed.decks

        // 등급 S→D→없음, 같은 무리에서는 보정 평균(없으면 뒤)
        assertEquals(listOf(ELDER, APHELIOS, YORICK, EDITORIAL), ids(DeckSearch.sort(all, DeckSortMode.GRADE, goldem)))
        // 픽률 내림차순
        assertEquals(listOf(APHELIOS, ELDER, YORICK, EDITORIAL), ids(DeckSearch.sort(all, DeckSortMode.PICK, goldem)))
        // 상승: trend up 먼저, 그다음 평균 등수 변화가 작은 순
        assertEquals(listOf(APHELIOS, YORICK, ELDER, EDITORIAL), ids(DeckSearch.sort(all, DeckSortMode.RISING, goldem)))
        // 표본 내림차순
        assertEquals(listOf(ELDER, APHELIOS, YORICK, EDITORIAL), ids(DeckSearch.sort(all, DeckSortMode.SAMPLE, goldem)))

        // 마스터+: 등급이 보정 평균보다 먼저다(장로 드래곤은 보정 평균이 더 좋지만 표본 부족이라 뒤).
        assertEquals(listOf(APHELIOS, ELDER, EDITORIAL, YORICK), ids(DeckSearch.sort(all, DeckSortMode.GRADE, "master")))

        assertEquals(DeckSortMode.RISING, DeckSortMode.fromKey("rising"))
        assertEquals(DeckSortMode.GRADE, DeckSortMode.fromKey(null))
    }

    @Test
    fun `고른 정렬을 다시 누르면 기준 값이 있는 덱끼리만 순서가 뒤집힌다`() {
        val goldem = "goldem"
        val all = feed.decks
        // 등급 D→S: 등급 있는 덱만 거꾸로, 등급 없는 요릭과 편집 독립 덱은 그대로 뒤에 둔다.
        assertEquals(listOf(APHELIOS, ELDER, YORICK, EDITORIAL), ids(DeckSearch.sort(all, DeckSortMode.GRADE, goldem, reversed = true)))
        // 픽률 낮은순·하락·표본 적은순. 통계가 없는 편집 독립 덱은 어느 방향이든 끝이다.
        assertEquals(listOf(YORICK, ELDER, APHELIOS, EDITORIAL), ids(DeckSearch.sort(all, DeckSortMode.PICK, goldem, reversed = true)))
        assertEquals(listOf(ELDER, YORICK, APHELIOS, EDITORIAL), ids(DeckSearch.sort(all, DeckSortMode.RISING, goldem, reversed = true)))
        assertEquals(listOf(YORICK, APHELIOS, ELDER, EDITORIAL), ids(DeckSearch.sort(all, DeckSortMode.SAMPLE, goldem, reversed = true)))

        // 칩 누름: 고른 칩은 방향만 바꾸고, 다른 칩은 그 정렬의 기본 방향으로 시작한다.
        val start = DeckSort()
        val gradeUp = start.tapped(DeckSortMode.GRADE)
        assertEquals(DeckSort(DeckSortMode.GRADE, reversed = true), gradeUp)
        assertEquals("등급 D→S", gradeUp.label)
        assertEquals("등급 S→D", start.label)
        assertEquals(start, gradeUp.tapped(DeckSortMode.GRADE))
        assertEquals(DeckSort(DeckSortMode.PICK), gradeUp.tapped(DeckSortMode.PICK))
    }

    @Test
    fun `구간 등급·편집 덱·중국 한정·주 특성·레벨 필터`() {
        // 티어 필터는 그 구간의 등급(없으면 편집 등급)으로 본다.
        assertEquals(setOf(ELDER, EDITORIAL), ids(search.filter(tiers = setOf("S"), bucket = "goldem")).toSet())
        // 마스터+ 에서 장로 드래곤은 통계 등급이 없어(표본 부족) 편집 등급 SS 여도 목록에서 빠진다. 고정하면 남는다.
        assertTrue(search.filter(tiers = setOf("SS"), bucket = "master").isEmpty())
        assertEquals(setOf(ELDER), ids(search.filter(tiers = setOf("SS"), bucket = "master", alwaysShow = setOf(ELDER))).toSet())

        assertEquals(setOf(ELDER, EDITORIAL), ids(search.filter(editorialOnly = true)).toSet())
        assertEquals(listOf(YORICK), ids(search.filter(onlyChina = true)))
        assertEquals(feed.version.onlyInChinaCount, search.filter(onlyChina = true).size)
        assertEquals(listOf(APHELIOS), ids(search.filter(mainTrait = "DA_18_Vanguard")))
        assertEquals(setOf(YORICK, EDITORIAL), ids(search.filter(levels = setOf(9))).toSet())
    }

    @Test
    fun `고정 덱은 맨 위로, 숨긴 덱은 보기를 켤 때만`() {
        val sorted = DeckSearch.sort(feed.decks, DeckSortMode.GRADE, "goldem")
        assertEquals(listOf(YORICK, ELDER, APHELIOS, EDITORIAL), ids(DeckSearch.pinFirst(sorted, setOf(YORICK))))
        assertEquals(listOf(YORICK, EDITORIAL, ELDER, APHELIOS), ids(DeckSearch.pinFirst(sorted, setOf(EDITORIAL, YORICK))))
        assertEquals(ids(sorted), ids(DeckSearch.pinFirst(sorted, emptySet())))

        val hiddenOff = search.filter(hidden = setOf(APHELIOS))
        assertFalse(hiddenOff.any { it.id == APHELIOS })
        assertEquals(feed.decks.size - 1, hiddenOff.size)
        val hiddenOn = search.filter(hidden = setOf(APHELIOS), showHidden = true)
        assertTrue(hiddenOn.any { it.id == APHELIOS })
    }

    @Test
    fun `오버레이 구간 순환`() {
        val keys = feed.buckets.keys
        assertEquals("master", DeckSearch.nextBucket("all", keys))
        assertEquals("low", DeckSearch.nextBucket("goldem", keys))
        assertEquals("all", DeckSearch.nextBucket("low", keys))
        assertEquals("all", DeckSearch.nextBucket("unknown", keys))
        assertEquals("goldem", DeckSearch.nextBucket("goldem", emptyList()))
    }

    // -- 검색 ---------------------------------------------------------------

    @Test
    fun `byId 로 덱을 찾고, 없는 축은 이름 인덱스로 떨어진다`() {
        assertEquals(setOf(ELDER, APHELIOS), ids(search.decksForId(SearchAxis.TRAIT, "DA_18_Vanguard")).toSet())
        assertEquals(setOf(ELDER, EDITORIAL), ids(search.decksForId(SearchAxis.CHAMPION, "DA_18_ElderDragon")).toSet())
        assertEquals(listOf(ELDER), ids(search.decksForId(SearchAxis.AUGMENT, "DA_Ascension")))
        // 조합 재료 축은 byId 가 없어 catalog 이름("B.F. 대검")으로 찾는다.
        assertEquals(listOf(ELDER), ids(search.decksForId(SearchAxis.COMPONENT, "DA_Component_BFSword")))
        assertTrue(search.decksForId(SearchAxis.ITEM, "DA_NoSuchItem").isEmpty())

        // byId 가 없는 옛 피드도 이름 인덱스로 찾는다.
        val legacy = DeckSearch(feed.copy(index = feed.index.copy(byId = IdIndex())))
        assertEquals(setOf(ELDER, EDITORIAL), ids(legacy.decksForId(SearchAxis.CHAMPION, "DA_18_ElderDragon")).toSet())
    }

    @Test
    fun `검색 후보에 id가 붙고 증강 설명으로도 찾되 이름으로 맞은 후보가 먼저다`() {
        val elder = search.suggest("장로").first { it.axis == SearchAxis.CHAMPION }
        assertEquals("DA_18_ElderDragon", elder.id)

        // decks.json 에는 증강 설명이 없다(수집기가 싣지 않는다). 도감 파일의 설명을 합친 피드여야 설명으로 찾는다.
        assertTrue(search.suggest("대검").none { it.axis == SearchAxis.AUGMENT })
        val merged = feed.withAugmentDescriptions(
            mapOf("DA_Swordsmith" to "라운드마다 조합 재료 B.F. 대검을<br>하나 얻습니다.", "DA_NoSuchAugment" to "없는 증강"),
        )
        assertTrue("설명이 없으면 같은 피드", feed.withAugmentDescriptions(emptyMap()) === feed)
        val swordsmith = merged.catalog.augments.first { it.id == "DA_Swordsmith" }.desc
        assertFalse("서식 태그가 검색 단어로 섞였다", "br" in DeckSearch.descWords(swordsmith))
        val results = DeckSearch(merged).suggest("대검")
        val augment = results.indexOfFirst { it.axis == SearchAxis.AUGMENT && it.name == "검 제작자" }
        assertTrue("설명문 검색이 동작하지 않는다: $results", augment >= 0)
        val item = results.indexOfFirst { it.name == "무한의 대검" }
        assertTrue("이름으로 맞은 후보가 설명으로 맞은 후보보다 앞서야 한다", item in 0 until augment)
        assertEquals(listOf("라운드마다", "조합", "재료", "대검을", "하나", "얻습니다"), DeckSearch.descWords("라운드마다 조합 재료 B.F. 대검을 하나 얻습니다."))
    }

    @Test
    fun `catalog 지도는 챔피언에 없으면 소환물에서 찾는다`() {
        val catalog = CatalogIndex(feed.catalog)
        assertEquals("돌껍질 나무", catalog.unit(PET)?.name)
        assertTrue(catalog.isPet(PET))
        assertFalse(catalog.isPet("DA_18_ElderDragon"))
        assertEquals(5, catalog.champions["DA_18_ElderDragon"]?.cost)
        assertNull(catalog.unit("DA_Unknown"))
    }

    // -- 편집 덱 여럿 · 표본 부족 · 데이터 신선도 ----------------------------------------

    @Test
    fun `같은 그룹의 다른 작가 편집 덱으로 바꿔 본다`() {
        val elder = deck(ELDER)
        assertEquals(listOf("14266", "14330"), elder.editorials.map { it.id })
        val catalog = CatalogIndex(feed.catalog)
        val other = elder.withEditorial(elder.moreEditorials.single(), catalog)

        assertEquals("14330", other.editorial?.id)
        assertEquals("作者B", other.author)
        assertEquals("S", other.editorialTier)
        assertEquals(9, other.finalLevel)
        assertEquals("【作者B】4森林巨龙", other.nameCn)
        assertEquals("02000000000000000000000000000003TFTSet18", other.teamCode?.code)
        assertEquals(listOf("DA_Swordsmith"), other.authorAugments.recommended.map { it.id })
        assertEquals(listOf("DA_Component_BFSword"), other.componentOrder.map { it.id })
        assertEquals("前期卖血", other.buildupNotes.early)
        assertEquals(listOf("final"), BuildupPlanner.authorPicks(other, 9).map { it.stage!!.key })
        // 대표 작가(14266)는 이전 패치 작성이지만 이 작가는 새로 썼다. 배지는 고른 작가를 따른다.
        assertTrue(elder.isEditorialStale)
        assertFalse(other.isEditorialStale)

        // 최종 단계 보드(id 참조)를 catalog 로 풀어 보드·아이템 섹션이 쓰는 유닛으로 만든다.
        assertEquals(listOf("DA_18_ElderDragon", "DA_18_Kennen", "DA_18_Yorick", PET), other.units.map { it.id })
        val carry = other.units.first { it.carry }
        assertEquals("장로 드래곤", carry.name)
        assertEquals(5, carry.cost)
        assertEquals(listOf("무한의 대검"), carry.items.map { it.name })
        assertEquals(3, other.units.first { it.id == "DA_18_Yorick" }.star)
        assertTrue(other.units.first { it.id == PET }.isPet)
        assertEquals("DA_18_ElderDragon", other.carries.first().id)

        // 통계·변형·빌드업 통계는 그룹 것 그대로
        assertEquals(elder.stats, other.stats)
        assertEquals(elder.variants, other.variants)
        assertEquals(elder.buildup, other.buildup)
        // 대표 편집 덱을 고르면 같은 덱, 편집 덱이 하나뿐이거나 없는 덱
        assertTrue(elder.withEditorial(elder.editorial!!, catalog) === elder)
        assertEquals(1, deck(EDITORIAL).editorials.size)
        assertTrue(deck(APHELIOS).editorials.isEmpty())
    }

    @Test
    fun `표본 부족은 그 구간에 통계가 있지만 등급이 없는 경우다`() {
        assertFalse(deck(ELDER).isLowSample("goldem"))
        assertTrue("등급 null", deck(ELDER).isLowSample("master"))
        assertTrue("표본 250", deck(YORICK).isLowSample("goldem"))
        // 그 구간에 기록이 없거나 표본이 작아 등급이 없으면 목록에서 뺀다(등급 있는 덱만 보인다).
        assertFalse("그 구간에 없음", deck(APHELIOS).isLowSample("low"))
        assertFalse(deck(APHELIOS).listedIn("low"))
        assertFalse("등급 없는 구간", deck(ELDER).listedIn("master"))
        assertTrue(deck(ELDER).listedIn("goldem"))
        assertFalse("표본 250", deck(YORICK).listedIn("goldem"))
        assertFalse("통계가 없는 편집 독립 덱은 편집 등급이 기준", deck(EDITORIAL).isLowSample("goldem"))
        assertTrue("편집 독립 덱은 어느 구간에나 나온다", deck(EDITORIAL).listedIn("low"))
    }

    @Test
    fun `metatft 전용 덱은 어느 구간에나 보이고 글로벌 등급과 글로벌 플래+ 수치로 lol_qq 덱 뒤에 선다`() {
        val global = Deck(
            id = "m-423017",
            kind = "global",
            name = "글로벌 전용",
            globalGrade = "A",
            global = com.tftdeck.reader.data.GlobalStats(
                cluster = 423017,
                stats = mapOf(
                    "kr_plat" to com.tftdeck.reader.data.ScopeStat(n = 5000, avg = 4.1, top4 = 0.55, win = 0.13),
                    "glob_plat" to com.tftdeck.reader.data.ScopeStat(n = 90000, avg = 4.2, top4 = 0.53, win = 0.12),
                ),
            ),
        )
        assertTrue(global.isGlobalOnly)
        assertTrue(global.listedIn("master"))
        assertFalse(global.isLowSample("goldem"))
        assertEquals("A", global.gradeFor("goldem"))
        // 네 수치는 글로벌 등급을 매긴 글로벌 플래+ 값이고, KR 플래+ 는 표본 줄에 참고로 붙는다.
        assertEquals(90000, global.displayStats("goldem")?.n)
        assertEquals(4.2, global.displayStats("goldem")?.avg ?: 0.0, 1e-9)
        assertEquals("n=90,000 · 글로벌 플래+ · KR 플래+ 4.10등 n=5,000", sampleText(global, "goldem", feed.buckets))
        // 글로벌 플래+ 가 없으면 KR 플래+ 가 네 수치가 되고, 같은 값을 표본 줄에 되풀이하지 않는다.
        val krOnly = global.copy(global = global.global?.copy(stats = mapOf("kr_plat" to global.global!!.stats.getValue("kr_plat"))))
        assertEquals(5000, krOnly.displayStats("goldem")?.n)
        assertEquals("n=5,000 · KR 플래+", sampleText(krOnly, "goldem", feed.buckets))
        assertEquals(listOf(ELDER, "m-423017"), ids(DeckSearch.sort(listOf(global, deck(ELDER)), DeckSortMode.GRADE, "goldem")))

        // D→S 로 뒤집어도 metatft 전용 덱은 lol.qq 덱 뒤에서 자기들끼리만 뒤집힌다.
        val globalC = global.copy(id = "m-423018", globalGrade = "C")
        assertEquals(
            listOf(APHELIOS, ELDER, YORICK, "m-423018", "m-423017"),
            ids(
                DeckSearch.sort(
                    listOf(global, deck(YORICK), globalC, deck(ELDER), deck(APHELIOS)),
                    DeckSortMode.GRADE,
                    "goldem",
                    reversed = true,
                ),
            ),
        )
    }

    @Test
    fun `캐시·동봉본·원격 중 더 새 피드를 고른다`() {
        val v1 = FeedVersion(schemaVersion = 1, generatedAt = "2026-09-14T14:30:44Z")
        val v2 = FeedVersion(schemaVersion = 2, generatedAt = "2026-09-15T16:34:40Z")
        val v2Later = v2.copy(generatedAt = "2026-09-16T20:05:12Z")

        // 1.0 이 받아 둔 v1 캐시는 1.1 동봉 v2 보다 옛 것이다.
        assertTrue(FeedFreshness.isOlder(v1, v2))
        assertFalse(FeedFreshness.isOlder(v2, v1))
        // 옛 수집기가 다시 돌아 생성 시각이 늦어도 스키마가 낮으면 받지 않는다.
        assertTrue(FeedFreshness.isOlder(v1.copy(generatedAt = "2026-09-20T00:00:00Z"), v2))
        // 같은 스키마는 생성 시각으로
        assertTrue(FeedFreshness.isOlder(v2, v2Later))
        assertFalse(FeedFreshness.isOlder(v2Later, v2))
        assertFalse(FeedFreshness.isOlder(v2, v2))
        // 모르는 시각은 옛 것이라 단정하지 않는다.
        assertFalse(FeedFreshness.isOlder(v2.copy(generatedAt = ""), v2Later))
        assertFalse(FeedFreshness.isOlder(v2.copy(generatedAt = "어제"), v2Later))
        assertTrue(FeedFreshness.isEarlier(feed.version.generatedAt, "2026-09-17T00:00:00Z"))
    }

    // -- 표시 도우미 ------------------------------------------------------------

    @Test
    fun `iconUrl은 절대 URL을 그대로 쓰고 상대 경로에만 접두사를 붙인다`() {
        val base = feed.version.assetBase
        val pet = feed.catalog.pets.single()
        assertEquals(pet.icon, iconUrl(base, pet.icon))
        assertEquals("https://raw.communitydragon.org/latest/game/assets/x.png", iconUrl(base, "assets/x.png"))
        assertEquals("https://raw.communitydragon.org/latest/game/assets/x.png", iconUrl(base, "/assets/x.png"))
        assertNull(iconUrl(base, ""))
        assertNull(iconUrl(base, null))
    }

    @Test
    fun `숫자와 날짜 표기`() {
        assertEquals("17,059", formatCount(17059))
        assertEquals("-", formatCount(null))
        assertEquals("3.61", formatAvg(3.61))
        assertEquals("4.00", formatAvg(4.0))
        assertEquals("-", formatAvg(null))
        assertEquals("68.8%", formatPct(0.688))
        assertEquals("-", formatPct(null))
        assertEquals("0.21%", formatPick(0.0021))
        assertEquals("1.2%", formatPick(0.012))
        assertEquals("9/15", formatShortDate("20260915"))
        assertEquals("9/15", formatShortDate("2026-09-15T13:14:40Z"))
        assertEquals("", formatShortDate(null))
    }

    // -- 빌드업(§13) -------------------------------------------------------------

    @Test
    fun `빌드업이 계약 이름 그대로 파싱된다`() {
        val buildup = deck(ELDER).buildup!!
        val global = buildup.global!!
        assertEquals("glob_plat", global.scope)
        assertEquals(423009L, global.cluster)
        assertEquals(9, global.rollLevel)
        assertEquals(listOf(4, 5, 6, 7, 8, 9, 10), global.levels.map { it.level })
        assertNull(global.levels.first().reachRound)
        assertEquals(0.98, global.levels.first { it.level == 5 }.reachShare!!, 1e-9)
        assertEquals(31.9, global.levels.first { it.level == 9 }.rollsPerGame!!, 1e-9)
        assertTrue(global.levels.first { it.level == 7 }.options.isEmpty())
        assertEquals("DA_18_Inferno", global.levels.first { it.level == 9 }.options.single().traits.single().id)
        assertEquals(1, global.levels.first { it.level == 9 }.options.single().traits.single().count)

        val cn = buildup.cn!!
        assertEquals("goldem", cn.bucket)
        assertNull(cn.rollLevel)
        assertEquals("DA_18_ElderDragon", cn.levels.first().options.single().carryId)
        assertEquals(0.755, cn.levels.first().options.single().top4!!, 1e-9)
        assertTrue(cn.levels.first { it.level == 9 }.options.isEmpty())

        assertEquals("前期连胜为主，血量低于50就卖血", deck(ELDER).buildupNotes.early)
        assertEquals("4-2上8，4-5搜卡", deck(ELDER).editorial!!.notesCn.levelUp)
        assertNull(deck(APHELIOS).buildupNotes.early)

        // 빌드업이 없는 덱과, 필드가 없거나 null 인 옛 캐시
        assertNull(deck(EDITORIAL).buildup)
        val legacy = FeedJson.decodeFeed("""{"decks":[{"id":"a","buildup":null,"notesCn":{"items":"x"}},{"id":"b","buildup":{"global":{"levels":[{"level":8}]}}}]}""")
        assertNull(legacy.decks[0].buildup)
        assertNull(legacy.decks[0].notesCn.levelUp)
        assertTrue(legacy.decks[1].buildup!!.global!!.levels.single().options.isEmpty())
    }

    @Test
    fun `레벨 칩 목록과 기본 선택 레벨`() {
        // 글로벌 옵션이 있는 레벨(4,5,6,8,9,10) ∪ 중국(8) ∪ 편집 단계(5,8). 옵션이 빈 7렙은 칩이 없다.
        assertEquals(listOf(4, 5, 6, 8, 9, 10), BuildupPlanner.levels(deck(ELDER)))
        // 편집 덱 최종 레벨(8)이 먼저
        assertEquals(8, BuildupPlanner.defaultLevel(deck(ELDER)))
        // 편집 최종 레벨이 칩에 없으면 주 리롤 레벨로 넘어간다
        assertEquals(9, BuildupPlanner.defaultLevel(deck(ELDER), levels = listOf(4, 9, 10)))

        // 편집 덱이 없으면 주 리롤 레벨
        assertEquals(listOf(6, 7, 8), BuildupPlanner.levels(deck(APHELIOS)))
        assertEquals(7, BuildupPlanner.defaultLevel(deck(APHELIOS)))

        // 주 리롤 레벨도 없으면 가장 큰 레벨
        assertEquals(listOf(8, 9), BuildupPlanner.levels(deck(YORICK)))
        assertEquals(9, BuildupPlanner.defaultLevel(deck(YORICK)))

        // 편집 독립 덱은 단계 레벨만
        assertEquals(listOf(9), BuildupPlanner.levels(deck(EDITORIAL)))
        assertEquals(9, BuildupPlanner.defaultLevel(deck(EDITORIAL)))

        // 자료가 전혀 없으면 섹션을 숨긴다
        assertNull(BuildupPlanner.defaultLevel(Deck(id = "empty")))
        assertFalse(BuildupPlanner.hasData(Deck(id = "empty")))
        assertTrue(BuildupPlanner.hasData(deck(ELDER)))
    }

    @Test
    fun `레벨 1순위 구성은 글로벌 → 중국 → 작가 순이다`() {
        val elder = deck(ELDER)
        val top8 = BuildupPlanner.topPick(elder, 8)!!
        assertEquals(BuildupOrigin.GLOBAL, top8.origin)
        assertEquals(20000, top8.option!!.n)
        // 8렙 행: 글로벌 2 + 중국 1 + 작가 2(중반·최종)
        assertEquals(
            listOf(BuildupOrigin.GLOBAL, BuildupOrigin.GLOBAL, BuildupOrigin.CN, BuildupOrigin.AUTHOR, BuildupOrigin.AUTHOR),
            BuildupPlanner.picks(elder, 8).map { it.origin },
        )
        // 옵션이 빈 레벨은 1순위가 없다
        assertNull(BuildupPlanner.topPick(elder, 7))
        // 중국만 있는 덱
        assertEquals(BuildupOrigin.CN, BuildupPlanner.topPick(deck(YORICK), 9)!!.origin)
        // 작가만 있는 덱은 그 레벨의 마지막 단계
        val author = BuildupPlanner.topPick(deck(EDITORIAL), 9)!!
        assertEquals(BuildupOrigin.AUTHOR, author.origin)
        assertEquals("final", author.stage!!.key)

        // 새로 들어온 유닛: 같은 출처 바로 아래 레벨의 1순위와 비교
        assertEquals(setOf("DA_18_Alistar"), BuildupPlanner.newUnits(elder, BuildupPlanner.globalPicks(elder, 5).first()))
        assertTrue(BuildupPlanner.newUnits(elder, BuildupPlanner.globalPicks(elder, 4).first()).isEmpty())
        val mid = BuildupPlanner.authorPicks(elder, 8).first()
        assertEquals("mid", mid.stage!!.key)
        assertTrue("DA_Draven18" in BuildupPlanner.newUnits(elder, mid))
        assertFalse("DA_18_Yorick" in BuildupPlanner.newUnits(elder, mid))

        // 오버레이 칩 아래 작은 글자
        assertEquals("8렙 4-2 · 주 리롤 9렙", BuildupPlanner.caption(elder, 8))
        assertEquals("4렙 · 주 리롤 9렙", BuildupPlanner.caption(elder, 4))
        assertEquals("9렙", BuildupPlanner.caption(deck(YORICK), 9))

        // 표본 부족 레벨은 흐리게
        assertTrue(BuildupPlanner.isLowSample(elder, 10))
        assertFalse(BuildupPlanner.isLowSample(elder, 9))
        assertTrue(BuildupPlanner.isLowSample(deck(YORICK), 8))

        // 타이밍 줄과 주 리롤
        assertEquals(
            listOf(5 to "2-5", 6 to "3-2", 7 to "3-5", 8 to "4-2", 9 to "4-6", 10 to "6-5"),
            BuildupPlanner.timings(elder),
        )
        assertEquals(32, BuildupPlanner.rollsAtRollLevel(elder)!!.roundToInt())
    }

    private companion object {
        const val ELDER = "g-7f3a9c1b2d"
        const val APHELIOS = "g-1c2d3e4f5a"
        const val YORICK = "g-2b3c4d5e6f"
        const val EDITORIAL = "14301"
        const val PET = "DA_18_IronbarkTree"
    }
}
