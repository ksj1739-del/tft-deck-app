package com.tftdeck.reader

import androidx.compose.ui.unit.dp
import com.tftdeck.reader.data.Buildup
import com.tftdeck.reader.data.BuildupLevel
import com.tftdeck.reader.data.BuildupOption
import com.tftdeck.reader.data.BuildupPlanner
import com.tftdeck.reader.data.BuildupSource
import com.tftdeck.reader.data.Deck
import com.tftdeck.reader.data.DeckKeys
import com.tftdeck.reader.data.DeckSources
import com.tftdeck.reader.data.DeckStats
import com.tftdeck.reader.data.Editorial
import com.tftdeck.reader.data.FeedJson
import com.tftdeck.reader.data.GlobalStats
import com.tftdeck.reader.data.ItemRef
import com.tftdeck.reader.data.Placement
import com.tftdeck.reader.data.Stage
import com.tftdeck.reader.data.TraitRef
import com.tftdeck.reader.data.Unit as DeckUnit
import com.tftdeck.reader.overlay.OverlayBuildFace
import com.tftdeck.reader.overlay.overlayBoardFaces
import com.tftdeck.reader.overlay.overlayBuildFaces
import com.tftdeck.reader.overlay.overlayDeckListMax
import com.tftdeck.reader.overlay.overlayGradeStyle
import com.tftdeck.reader.overlay.overlayLevelCaption
import com.tftdeck.reader.overlay.overlayLpText
import com.tftdeck.reader.overlay.overlayOperation
import com.tftdeck.reader.overlay.overlayPinnedOffGrade
import com.tftdeck.reader.overlay.overlayRowAlias
import com.tftdeck.reader.overlay.overlayRowCarries
import com.tftdeck.reader.overlay.overlaySummaryLine
import com.tftdeck.reader.ui.components.GradeBadgeStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 오버레이 목록 줄·덱 요약·티어 카드가 화면에 올리기 전에 고르는 값(WP-O4). Compose 밖 순수 함수만 본다. */
class OverlayListSummaryTest {

    private fun u(id: String, rank: Int? = null, carry: Boolean = false, kind: String? = null, items: Int = 0) = DeckUnit(
        id = id,
        name = id,
        carryRank = rank,
        carry = carry,
        kind = kind,
        items = List(items) { ItemRef(id = "$id-item$it") },
    )

    private fun graded(id: String, grade: String?, kind: String = DeckKeys.KIND_META) =
        Deck(id = id, kind = kind, stats = mapOf("goldem" to DeckStats(n = 5000, avg = 4.3, grade = grade)))

    @Test
    fun `목록 줄 별칭은 운영 접미사만 떼고 캐리 사이 가운뎃점과 번호는 남긴다`() {
        assertEquals("요정 트리스타나·시비르", overlayRowAlias("요정 트리스타나·시비르 · 7레벨 리롤"))
        assertEquals("달빛 아펠리오스·니달리", overlayRowAlias("달빛 아펠리오스·니달리"))
        assertEquals("처형자 자이라 2", overlayRowAlias("처형자 자이라 2"))
        assertEquals("검은 가시 베이가", overlayRowAlias("  검은 가시 베이가  "))
    }

    @Test
    fun `가로 목록 줄의 캐리 얼굴은 캐리 순위 앞 둘이고 소환물은 뺀다`() {
        val deck = Deck(units = listOf(u("c", rank = 3), u("a", rank = 1, carry = true), u("x"), u("b", rank = 2)))
        assertEquals(listOf("a", "b"), overlayRowCarries(deck).map { it.id })
        val single = Deck(units = listOf(u("k", rank = 1, carry = true), u("y")))
        assertEquals(listOf("k"), overlayRowCarries(single).map { it.id })
        val withPet = Deck(units = listOf(u("p", rank = 1, kind = DeckKeys.KIND_PET), u("a", rank = 2), u("b", rank = 3)))
        assertEquals(listOf("a", "b"), overlayRowCarries(withPet).map { it.id })
        // 순위가 없는 옛 데이터: 캐리 표시 → 아이템 많은 순(Deck.carries 대체 규칙)
        val legacy = Deck(units = listOf(u("m", items = 1), u("n", carry = true), u("o", items = 3)))
        assertEquals(listOf("n", "o"), overlayRowCarries(legacy).map { it.id })
        assertTrue(overlayRowCarries(Deck(units = listOf(u("z")))).isEmpty())
    }

    @Test
    fun `등급 배지 모양은 metatft 채움, 중국 한정 테두리, 편집 등급 편`() {
        assertEquals(GradeBadgeStyle.Filled, overlayGradeStyle(graded("m", "S"), "goldem", metatftCompared = true))
        val china = Deck(
            id = "g",
            kind = DeckKeys.KIND_GROUP,
            sources = DeckSources(onlyInChina = true),
            stats = mapOf("goldem" to DeckStats(n = 900, grade = "S")),
        )
        assertEquals(GradeBadgeStyle.Outlined, overlayGradeStyle(china, "goldem", metatftCompared = true))
        // metatft 비교가 없는 피드는 등급 체계가 하나뿐이라 채움으로 그린다.
        assertEquals(GradeBadgeStyle.Filled, overlayGradeStyle(china, "goldem", metatftCompared = false))
        val editorial = Deck(id = "e", kind = DeckKeys.KIND_EDITORIAL, editorialTier = "SS")
        assertEquals(GradeBadgeStyle.Editorial, overlayGradeStyle(editorial, "goldem", metatftCompared = true))
    }

    @Test
    fun `고정해서 남은 덱만 꺼진 등급이면 흐리게 한다`() {
        val c = graded("c", "C")
        val s = graded("s", "S")
        val grades = DeckKeys.GRADE_FILTER_DEFAULT
        assertTrue(overlayPinnedOffGrade(c, "goldem", pinned = true, grades = grades))
        assertFalse(overlayPinnedOffGrade(c, "goldem", pinned = false, grades = grades))
        assertFalse(overlayPinnedOffGrade(s, "goldem", pinned = true, grades = grades))
        // 등급 조건을 쓰지 않는 피드(구간 없음)에서는 흐리게 하지 않는다.
        assertFalse(overlayPinnedOffGrade(c, "goldem", pinned = true, grades = null))
        // 그 구간에 등급이 없는 고정 덱도 조건으로는 빠졌을 덱이다.
        assertTrue(overlayPinnedOffGrade(c, "master", pinned = true, grades = grades))
        // 다섯 등급을 다 켜면 아무것도 빠지지 않는다.
        assertFalse(overlayPinnedOffGrade(c, "master", pinned = true, grades = DeckKeys.GRADE_FILTER_ALL.toSet()))
    }

    @Test
    fun `운영은 설명 첫머리의 운영 어휘를 먼저, 없으면 metatft 운영, 그다음 최종 레벨`() {
        assertEquals("빠른 8레벨", overlayOperation(Deck(summary = "빠른 8레벨 · 달빛 3 · 무한의 대검")))
        assertEquals("최종 9레벨", overlayOperation(Deck(summary = "최종 9레벨 · 검은 가시 4", finalLevel = 8)))
        assertEquals("7레벨 리롤", overlayOperation(Deck(global = GlobalStats(levelling = "7레벨 리롤"), finalLevel = 9)))
        // 운영 어휘 밖의 말('N레벨 완성', '표준')은 쓰지 않는다.
        val old = Deck(summary = "9레벨 완성 · 달빛 3", global = GlobalStats(levelling = "표준"), finalLevel = 9)
        assertEquals("최종 9레벨", overlayOperation(old))
        assertNull(overlayOperation(Deck()))
    }

    @Test
    fun `요약 둘째 줄은 운영과 주 특성 둘`() {
        val deck = Deck(
            summary = "빠른 8레벨 · 기원자 3 · 쇼진의 창",
            mainTraits = listOf(
                TraitRef(name = "개화", count = 5),
                TraitRef(name = "기원자", count = 3),
                TraitRef(name = "협곡야수", count = 3),
            ),
        )
        assertEquals("빠른 8레벨 · 개화 5 · 기원자 3", overlaySummaryLine(deck))
        // 주 특성이 없으면 전체 특성에서 인원 많은 순.
        val noMain = Deck(
            finalLevel = 9,
            traits = listOf(
                TraitRef(name = "싸움꾼", count = 2),
                TraitRef(name = "악의 여단", count = 7),
                TraitRef(name = "선봉대", count = 4),
            ),
        )
        assertEquals("최종 9레벨 · 악의 여단 7 · 선봉대 4", overlaySummaryLine(noMain))
        assertEquals("달빛 3", overlaySummaryLine(Deck(mainTraits = listOf(TraitRef(name = "달빛", count = 3)))))
        assertEquals("", overlaySummaryLine(Deck()))
    }

    @Test
    fun `레벨 캡션은 도달 라운드와 롤다운 레벨이고 주 리롤 문구는 없다`() {
        val deck = Deck(
            buildup = Buildup(
                global = BuildupSource(
                    rollLevel = 8,
                    levels = listOf(
                        BuildupLevel(level = 6, reachRound = "3-2", options = listOf(BuildupOption(units = listOf("a")))),
                        BuildupLevel(level = 8, reachRound = "4-2", options = listOf(BuildupOption(units = listOf("a")))),
                        BuildupLevel(level = 9, options = listOf(BuildupOption(units = listOf("a")))),
                    ),
                ),
            ),
        )
        assertEquals("8렙 4-2 도달 · 롤다운 8렙", overlayLevelCaption(deck, 8))
        assertEquals("6렙 3-2 도달 · 롤다운 8렙", overlayLevelCaption(deck, 6))
        assertEquals("롤다운 8렙", overlayLevelCaption(deck, 9))
        listOf(6, 8, 9).mapNotNull { overlayLevelCaption(deck, it) }.forEach { assertFalse(it, it.contains("주 리롤")) }
        // 중국 한정 덱: metatft 도달 라운드가 없으면 그 레벨 작가 단계의 라운드. 아무것도 모르면 캡션을 두지 않는다.
        val china = Deck(
            editorial = Editorial(
                stages = listOf(
                    Stage(key = "early", level = 5, round = "2-3"),
                    Stage(key = DeckKeys.STAGE_FINAL, level = 8),
                ),
            ),
        )
        assertEquals("5렙 2-3 도달", overlayLevelCaption(china, 5))
        assertNull(overlayLevelCaption(china, 8))
    }

    @Test
    fun `빌드업 얼굴은 캐리를 맨 앞에 두고 아래 레벨에 없던 유닛에 새 표시를 한다`() {
        val deck = Deck(
            buildup = Buildup(
                global = BuildupSource(
                    levels = listOf(
                        BuildupLevel(level = 6, options = listOf(BuildupOption(units = listOf("a", "b", "c"), carryId = "c"))),
                        BuildupLevel(level = 8, options = listOf(BuildupOption(units = listOf("a", "b", "d", "e"), carryId = "d"))),
                    ),
                ),
            ),
        )
        val eight = BuildupPlanner.topPick(deck, 8)!!
        assertEquals(
            listOf(
                OverlayBuildFace("d", carry = true, isNew = true, star = 1),
                OverlayBuildFace("a", carry = false, isNew = false, star = 1),
                OverlayBuildFace("b", carry = false, isNew = false, star = 1),
                OverlayBuildFace("e", carry = false, isNew = true, star = 1),
            ),
            overlayBuildFaces(deck, eight),
        )
        // 첫 레벨은 비교할 아래 레벨이 없어 새 표시가 없다.
        val six = overlayBuildFaces(deck, BuildupPlanner.topPick(deck, 6)!!)
        assertEquals(listOf("c", "a", "b"), six.map { it.id })
        assertTrue(six.none { it.isNew })
    }

    @Test
    fun `작가 단계 얼굴은 칸의 캐리와 성급을 쓴다`() {
        val deck = Deck(
            editorial = Editorial(
                stages = listOf(
                    Stage(key = "mid", level = 5, units = listOf(Placement(id = "a"), Placement(id = "b"))),
                    Stage(
                        key = DeckKeys.STAGE_FINAL,
                        level = 8,
                        units = listOf(Placement(id = "a", star = 3), Placement(id = "b"), Placement(id = "c", carry = true, star = 2)),
                    ),
                ),
            ),
        )
        val faces = overlayBuildFaces(deck, BuildupPlanner.topPick(deck, 8)!!)
        assertEquals(listOf("c", "a", "b"), faces.map { it.id })
        assertEquals(3, faces.first { it.id == "a" }.star)
        assertEquals(listOf("c"), faces.filter { it.isNew }.map { it.id })
    }

    @Test
    fun `빌드업이 없는 덱의 요약 얼굴도 캐리가 맨 앞`() {
        val units = listOf(u("a"), u("b", carry = true), u("c"))
        assertEquals(listOf("b", "a", "c"), overlayBoardFaces(units).map { it.id })
    }

    @Test
    fun `티어 카드 LP 는 천 단위 쉼표와 띄어쓰기`() {
        assertEquals("36 LP", overlayLpText("36 LP"))
        assertEquals("1,234 LP", overlayLpText("1234 LP"))
        assertEquals("1,234 LP", overlayLpText("1234LP"))
        assertEquals("1,234 LP", overlayLpText("1,234 LP"))
        assertEquals("언랭크", overlayLpText(" 언랭크 "))
    }

    @Test
    fun `목록 높이 상한은 가로 200dp 세로 300dp`() {
        assertEquals(200.dp, overlayDeckListMax(landscape = true))
        assertEquals(300.dp, overlayDeckListMax(landscape = false))
    }

    @Test
    fun `동봉 데이터 - 목록 별칭에 운영 접미사가 없고 요약 둘째 줄은 운영 어휘로 시작한다`() {
        val file = File("src/main/assets/decks.json")
        assertTrue("decks.json이 assets에 없다: ${file.absolutePath}", file.exists())
        val feed = FeedJson.decodeFeed(file.readText())
        val bucket = feed.defaultBucket
        val compared = feed.version.metatftCompared
        val line = Regex("""(빠른 \d+레벨|\d+레벨 리롤|표준 운영|최종 \d+레벨)( · .+)?""")
        feed.decks.forEach { deck ->
            val alias = overlayRowAlias(deck.displayAlias)
            assertTrue(deck.id, alias.isNotBlank())
            assertFalse("${deck.id}: $alias", alias.contains(" · "))
            val summaryLine = overlaySummaryLine(deck)
            assertTrue("${deck.id}: $summaryLine", line.matches(summaryLine))
            assertTrue(deck.id, overlayRowCarries(deck).size in 1..2)
            // 중국 한정 덱(편집 등급이 아닌)은 테두리형, metatft 조합 덱은 채움형.
            val style = overlayGradeStyle(deck, bucket, compared)
            if (deck.isMeta) assertEquals(deck.id, GradeBadgeStyle.Filled, style)
            if (compared && deck.isOnlyInChina && !deck.showsEditorialGrade(bucket)) {
                assertEquals(deck.id, GradeBadgeStyle.Outlined, style)
            }
        }
        // 접미사를 떼서 별칭이 같아진 덱들은 바로 아래 설명(운영 첫머리)으로 갈린다(R6).
        feed.decks.groupBy { overlayRowAlias(it.displayAlias) }.values.filter { it.size > 1 }.forEach { same ->
            val summaries = same.map { it.displaySummary }
            assertEquals(same.map { it.id }.toString(), summaries.size, summaries.toSet().size)
            assertNotEquals(same.map { it.id }.toString(), 1, summaries.map { it.substringBefore(" · ") }.toSet().size)
        }
    }
}
