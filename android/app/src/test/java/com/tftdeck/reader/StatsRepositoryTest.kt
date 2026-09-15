package com.tftdeck.reader

import com.tftdeck.reader.data.AugmentRow
import com.tftdeck.reader.data.AugmentsFile
import com.tftdeck.reader.data.ChampionRow
import com.tftdeck.reader.data.ChampionsFile
import com.tftdeck.reader.data.CodexStat
import com.tftdeck.reader.data.ItemRow
import com.tftdeck.reader.data.ItemsFile
import com.tftdeck.reader.data.StatScope
import com.tftdeck.reader.data.StatsParser
import com.tftdeck.reader.data.StatsRepository
import com.tftdeck.reader.data.StatsVersion
import com.tftdeck.reader.data.TraitRow
import com.tftdeck.reader.data.TraitsFile
import com.tftdeck.reader.ui.codex.AugmentFilter
import com.tftdeck.reader.ui.codex.ChampionFilter
import com.tftdeck.reader.ui.codex.ChampionSort
import com.tftdeck.reader.ui.codex.CodexQuery
import com.tftdeck.reader.ui.codex.CodexSearch
import com.tftdeck.reader.ui.codex.ItemFilter
import com.tftdeck.reader.ui.codex.TraitFilter
import com.tftdeck.reader.ui.codex.cleanDesc
import com.tftdeck.reader.ui.codex.codexIconUrl
import com.tftdeck.reader.ui.codex.formatAvg
import com.tftdeck.reader.ui.codex.formatCountShort
import com.tftdeck.reader.ui.codex.formatDelta
import com.tftdeck.reader.ui.codex.formatNumber
import com.tftdeck.reader.ui.codex.formatPct
import com.tftdeck.reader.ui.codex.formatShortDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * 도감 통계 계약(설계 §5.3~§5.7)을 넓힌 픽스처로 파서·조회·검색·정렬을 검증한다.
 *
 * 픽스처는 src/test/resources/stats 에 있다. 수집기(WP-2)의 실제 스냅샷이
 * assets/stats 에 들어오면 마지막 테스트가 그 파일도 같은 파서로 읽어 본다.
 */
class StatsRepositoryTest {

    private lateinit var version: StatsVersion
    private lateinit var champions: ChampionsFile
    private lateinit var traits: TraitsFile
    private lateinit var items: ItemsFile
    private lateinit var augments: AugmentsFile

    @Before
    fun load() {
        version = StatsParser.parseVersion(fixture("version")) ?: error("version.json 파싱 실패")
        champions = StatsParser.parseChampions(fixture("champions")) ?: error("champions.json 파싱 실패")
        traits = StatsParser.parseTraits(fixture("traits")) ?: error("traits.json 파싱 실패")
        items = StatsParser.parseItems(fixture("items")) ?: error("items.json 파싱 실패")
        augments = StatsParser.parseAugments(fixture("augments")) ?: error("augments.json 파싱 실패")
    }

    // -- 파싱 ---------------------------------------------------------------

    @Test
    fun `픽스처 다섯 파일이 계약대로 파싱된다`() {
        assertEquals(StatsRepository.DATA_FILES.toSet(), version.files.keys)
        assertEquals("18.2", version.patchGlobal)
        assertEquals("16.18", version.patch)

        assertEquals(4, champions.champions.size)
        assertEquals(StatScope.ORDER, CodexQuery.availableScopes(champions.scopes))
        assertEquals(1000, champions.minSample)
        assertEquals("18.1d", champions.prevPatch?.patchGlobal)
        assertEquals(967_672L, champions.scopes[StatScope.KR_PLAT]?.sampleSize)

        assertEquals(3, traits.traits.size)
        assertEquals(500, traits.minSample)

        assertEquals(5, items.items.size)
        assertEquals(10, items.components.size)
        assertEquals(3, items.recipes.size)

        assertEquals(4, augments.augments.size)
        assertEquals("META Spencer", augments.meta.editorTier.author)
        assertEquals(24, augments.meta.editorTier.counts["S"])
    }

    @Test
    fun `숫자 흔들림과 null이 와도 파일 전체가 버려지지 않는다`() {
        // 파이썬 수집기가 흔히 내는 모양: 정수 자리의 20.0, 문자열 숫자, NaN, 모르는 필드
        val stat = StatsParser.json.decodeFromString(
            CodexStat.serializer(),
            """{"n": 620.0, "places": [80.0, "81", 79], "top1": "8696", "avg": NaN, "grade": null, "extra": {"x": 1}}""",
        )
        assertEquals(620, stat.n)
        assertEquals(listOf(80, 81, 79), stat.places)
        assertEquals(8696L, stat.top1)
        assertTrue(stat.avg!!.isNaN())
        assertEquals("-", formatAvg(stat.avg))

        val file = StatsParser.parseChampions(
            """{"champions": [{"id": "DA_X", "name": "엑스", "cost": 3.0,
                "stats": {"kr_plat": null, "glob_plat": {"n": null, "avg": 4.1}},
                "ability": {"mana": [20.0, 100.0]}}]}""",
        )
        assertNotNull("흔들린 값 때문에 파일이 버려졌다", file)
        val row = file!!.champions.single()
        assertEquals(3, row.cost)
        assertNull(row.stat(StatScope.KR_PLAT))
        assertEquals(0, row.stat(StatScope.GLOB_PLAT)?.n)
        assertEquals(listOf(20.0, 100.0), row.ability?.mana)
    }

    @Test
    fun `빈 파일이나 행이 없는 파일은 쓸 수 없는 데이터로 거부한다`() {
        assertNull(StatsParser.parseChampions(""))
        assertNull(StatsParser.parseChampions("{}"))
        assertNull(StatsParser.parseChampions("""{"champions": []}"""))
        assertNull(StatsParser.parseItems("<html>404</html>"))
        assertNull("파일 목록이 없는 버전은 쓸 수 없다", StatsParser.parseVersion("""{"schemaVersion": 1}"""))
    }

    // -- 조회 ---------------------------------------------------------------

    @Test
    fun `스코프별 성적과 등급을 조회한다`() {
        val ahri = champion("DA_18_Ahri")
        assertEquals(4.46, ahri.stat(StatScope.KR_PLAT)!!.avg!!, 1e-9)
        assertEquals("B", ahri.grade(StatScope.KR_PLAT))
        assertEquals("A", ahri.grade(StatScope.CN_PLAT))
        assertNull("표본 부족 스코프는 등급이 없다", ahri.grade(StatScope.KR_MASTER))
        assertNull("통계 행이 없는 챔피언", champion("DA_18_Yorick").stat(StatScope.KR_PLAT))
        assertNull("그 스코프 행이 없는 챔피언", champion("DA_18_ElderDragon").stat(StatScope.CN_PLAT))
        assertEquals(listOf(20.0, 100.0), ahri.ability?.mana)
        assertEquals(listOf(700.0, 1260.0, 2268.0), ahri.base?.health)
    }

    @Test
    fun `중국 스코프의 TOP4는 횟수 대신 비율로 읽는다`() {
        val ahri = champion("DA_18_Ahri")
        assertEquals(898_053.0, ahri.stat(StatScope.CN_PLAT)!!.top4!!, 1e-9)
        assertEquals(0.588, ahri.stat(StatScope.CN_PLAT)!!.top4Share!!, 1e-9)
        assertEquals(0.508, ahri.stat(StatScope.KR_PLAT)!!.top4Share!!, 1e-9)
        // top4Rate가 빠져도 횟수를 표본으로 나눠 비율을 만든다.
        assertEquals(0.55, CodexStat(n = 1000, top4 = 550.0).top4Share!!, 1e-9)
    }

    @Test
    fun `특성 성적은 활성 인원수로 찾는다`() {
        val fae = trait("DA_18_Fae")
        assertEquals(4.10, fae.stat(StatScope.GLOB_PLAT, 5)!!.avg!!, 1e-9)
        assertNull(fae.stat(StatScope.GLOB_PLAT, 10))
        assertEquals(3, fae.styleFor(7))
        assertEquals(listOf(3, 5, 7, 10), fae.stageUnits)
        assertEquals(4, fae.topStyle)

        val combo = fae.combos.single()
        assertEquals("DA_18_Adaptor", combo.partner.id)
        assertEquals(listOf(0.58, 0.60, null, 0.60, 0.61), combo.trend)
    }

    @Test
    fun `아이템 착용자는 스코프에 없으면 넓은 스코프와 글로벌로 대신한다`() {
        val infinityEdge = item("DA_InfinityEdge")
        assertEquals(2, infinityEdge.wearers(StatScope.GLOB_PLAT).size)
        assertTrue(infinityEdge.wearers(StatScope.KR_PLAT).isEmpty())
        assertEquals(StatScope.GLOB_PLAT, CodexQuery.wearersFor(infinityEdge, StatScope.KR_PLAT)?.first)
        assertEquals(StatScope.CN_PLAT, CodexQuery.wearersFor(infinityEdge, StatScope.CN_MASTER)?.first)
        assertNull(CodexQuery.wearersFor(item("DA_18_EmblemFae"), StatScope.KR_PLAT))
        assertEquals(listOf(2, 3, 4), infinityEdge.stages.map { it.stage })
    }

    // -- 검색 ---------------------------------------------------------------

    @Test
    fun `초성으로 찾는다`() {
        assertEquals("ㅁㅎㅇㄷㄱ", CodexSearch.initials("무한의 대검"))
        assertNull(CodexSearch.initials("Ahri"))
        assertTrue(CodexSearch.matches("ㅁㅎㅇ", "무한의 대검"))
        assertEquals(listOf("DA_18_Tristana"), championIds(ChampionFilter(query = "ㅌㄹㅅ")))
    }

    @Test
    fun `줄임말로 찾는다`() {
        assertEquals("무대", CodexSearch.abbreviation("무한의 대검"))
        assertNull("어절이 하나면 줄임말이 없다", CodexSearch.abbreviation("아리"))
        assertEquals(listOf("DA_18_ElderDragon"), championIds(ChampionFilter(query = "장드")))
    }

    @Test
    fun `영문명과 영문 두문자로 찾는다`() {
        assertEquals(listOf("DA_18_ElderDragon"), championIds(ChampionFilter(query = "elder")))
        assertEquals(listOf("DA_18_ElderDragon"), championIds(ChampionFilter(query = "ED")))
        assertEquals(listOf("DA_InfinityEdge"), itemIds(ItemFilter(query = "infinity")))
    }

    @Test
    fun `이름 일부로 찾고 빈 검색어는 거르지 않는다`() {
        assertEquals(listOf("DA_18_Ahri"), championIds(ChampionFilter(query = "아")))
        assertEquals(4, championIds(ChampionFilter(query = "  ")).size)
        assertTrue(championIds(ChampionFilter(query = "존재하지않는이름zzz")).isEmpty())
    }

    @Test
    fun `증강은 설명으로도 찾고 이름에서 맞은 것이 먼저 온다`() {
        assertEquals(listOf("DA_18_FaeCrest"), CodexQuery.augmentRows(augments, AugmentFilter(query = "상징")).map { it.id })
        assertEquals(listOf("DA_SilverSpoon"), CodexQuery.augmentRows(augments, AugmentFilter(query = "ㅇㅅㅈ")).map { it.id })

        // 이름순이면 "가벼운 주머니"가 먼저지만, 이름에서 맞은 쪽이 설명에서만 맞은 쪽보다 앞선다.
        val file = AugmentsFile(
            augments = listOf(
                AugmentRow(id = "desc-only", name = "가벼운 주머니", desc = "경험치를 얻습니다.", editorTier = "S"),
                AugmentRow(id = "by-name", name = "폭발하는 경험치", desc = "", editorTier = "S"),
            ),
        )
        assertEquals(listOf("by-name", "desc-only"), CodexQuery.augmentRows(file, AugmentFilter(query = "경험치")).map { it.id })
    }

    @Test
    fun `증강 희귀도와 태그 필터가 동작한다`() {
        assertEquals(
            setOf("DA_Ascension", "DA_18_FaeCrest"),
            CodexQuery.augmentRows(augments, AugmentFilter(rarity = "gold")).map { it.id }.toSet(),
        )
        assertEquals(
            setOf("DA_SilverSpoon", "DA_LateGameScaling"),
            CodexQuery.augmentRows(augments, AugmentFilter(tag = "econ")).map { it.id }.toSet(),
        )
    }

    // -- 정렬 ---------------------------------------------------------------

    @Test
    fun `기본 정렬은 등급순이고 표본 부족 행과 값이 없는 행은 뒤로 간다`() {
        val table = CodexQuery.championTable(champions, StatScope.KR_PLAT, ChampionFilter())
        assertEquals(StatScope.KR_PLAT, table.scope)
        // 장로 드래곤은 KR 플래+ 평균 3.96으로 가장 좋지만 표본 800 < 1000이라 뒤로 간다.
        assertEquals(
            listOf("DA_18_Tristana", "DA_18_Ahri", "DA_18_ElderDragon", "DA_18_Yorick"),
            table.rows.map { it.champion.id },
        )
        val elder = table.rows.first { it.champion.id == "DA_18_ElderDragon" }
        assertTrue(elder.lowSample)
        assertTrue(elder.dimmed)
        val yorick = table.rows.last()
        assertNull(yorick.stat)
        assertFalse(yorick.lowSample)
        assertTrue(yorick.dimmed)
    }

    @Test
    fun `평균 정렬에서도 표본 부족 행은 평균이 좋아도 뒤로 간다`() {
        assertEquals(
            listOf("DA_18_Tristana", "DA_18_Ahri", "DA_18_ElderDragon", "DA_18_Yorick"),
            championIds(ChampionFilter(sort = ChampionSort.AVG)),
        )
    }

    @Test
    fun `글로벌 등급 정렬과 이름 정렬`() {
        assertEquals(
            listOf("DA_18_ElderDragon", "DA_18_Tristana", "DA_18_Ahri", "DA_18_Yorick"),
            championIds(ChampionFilter(), StatScope.GLOB_PLAT),
        )
        assertEquals(
            listOf("DA_18_Ahri", "DA_18_Yorick", "DA_18_ElderDragon", "DA_18_Tristana"),
            championIds(ChampionFilter(sort = ChampionSort.NAME), StatScope.GLOB_PLAT),
        )
        assertEquals(ChampionSort.PICK, ChampionSort.fromKey("pick"))
        assertEquals(ChampionSort.GRADE, ChampionSort.fromKey("모르는 값"))
    }

    @Test
    fun `코스트와 특성 필터가 동작한다`() {
        assertEquals(listOf("DA_18_Ahri"), championIds(ChampionFilter(costs = setOf(4))))
        assertEquals(setOf("DA_18_Tristana", "DA_18_Yorick"), championIds(ChampionFilter(traitId = "DA_18_Fae")).toSet())
        assertEquals(listOf("DA_18_Tristana"), championIds(ChampionFilter(costs = setOf(1, 5), traitId = "DA_18_Fae")))
        assertEquals(
            listOf("개화", "기원자", "요정", "적응가", "협곡야수"),
            CodexQuery.championTraitOptions(champions).map { it.name },
        )
    }

    @Test
    fun `특성은 기본으로 특성당 표본이 가장 큰 단계 한 줄을 보여 준다`() {
        val table = CodexQuery.traitTable(traits, StatScope.KR_PLAT, TraitFilter())
        assertEquals(listOf("DA_18_Adaptor#2", "DA_18_Fae#3", "DA_Riftbeast18#3"), table.rows.map { it.key })
    }

    @Test
    fun `단계별 보기와 등급 칩과 유형 토글`() {
        val staged = CodexQuery.traitTable(traits, StatScope.GLOB_PLAT, TraitFilter(byStage = true)).rows
        assertEquals(9, staged.size)
        assertTrue("표본 300 < 500", staged.first { it.key == "DA_18_Fae#7" }.lowSample)

        val gold = CodexQuery.traitTable(traits, StatScope.GLOB_PLAT, TraitFilter(style = 3)).rows.map { it.key }
        assertEquals(setOf("DA_18_Fae#7", "DA_18_Adaptor#3", "DA_Riftbeast18#5"), gold.toSet())

        val classes = CodexQuery.traitTable(traits, StatScope.GLOB_PLAT, TraitFilter(types = setOf("class"))).rows
        assertEquals(listOf("DA_18_Adaptor"), classes.map { it.trait.id })
    }

    // -- 아이템 ---------------------------------------------------------------

    @Test
    fun `아이템 표는 재료를 빼고 부품 필터는 그 부품이 들어간 아이템만 남긴다`() {
        // KR 플래+: 요정 상징 A, 무한의 대검 B, 죽음의 검은 표본 640 < 1000이라 뒤로.
        assertEquals(listOf("DA_18_EmblemFae", "DA_InfinityEdge", "DA_Deathblade"), itemIds(ItemFilter()))
        assertEquals(
            setOf("DA_InfinityEdge", "DA_Deathblade"),
            itemIds(ItemFilter(components = listOf("DA_Component_BFSword"))).toSet(),
        )
        assertEquals(
            listOf("DA_InfinityEdge"),
            itemIds(ItemFilter(components = listOf("DA_Component_BFSword", "DA_Component_SparringGloves"))),
        )
        assertEquals(listOf("DA_18_EmblemFae"), itemIds(ItemFilter(kind = "emblem")))
    }

    @Test
    fun `부품 칩은 두 개까지 고르고 세 번째는 가장 먼저 고른 것을 밀어낸다`() {
        var picked = emptyList<String>()
        picked = CodexQuery.toggleComponent(picked, "A")
        picked = CodexQuery.toggleComponent(picked, "B")
        assertEquals(listOf("A", "B"), picked)
        picked = CodexQuery.toggleComponent(picked, "C")
        assertEquals(listOf("B", "C"), picked)
        picked = CodexQuery.toggleComponent(picked, "B")
        assertEquals(listOf("C"), picked)
    }

    @Test
    fun `조합표 recipes를 부품 순서와 무관하게 역조회한다`() {
        assertEquals("DA_InfinityEdge", items.recipeFor("DA_Component_SparringGloves", "DA_Component_BFSword"))
        assertEquals("DA_InfinityEdge", items.recipeFor("DA_Component_BFSword", "DA_Component_SparringGloves"))
        assertEquals("DA_Deathblade", items.recipeFor("DA_Component_BFSword", "DA_Component_BFSword"))
        assertNull(items.recipeFor("DA_Component_FryingPan", "DA_Component_ChainVest"))
        assertEquals(
            "DA_Component_BFSword|DA_Component_SparringGloves",
            ItemsFile.recipeKey("DA_Component_SparringGloves", "DA_Component_BFSword"),
        )
        assertEquals(setOf("DA_InfinityEdge", "DA_Deathblade"), CodexQuery.itemsUsing(items, "DA_Component_BFSword").toSet())
        assertEquals(items.components, CodexQuery.componentIds(items))
    }

    @Test
    fun `스코프가 파일에 없으면 가까운 스코프로 대신한다`() {
        val available = CodexQuery.availableScopes(items.scopes)
        assertFalse(StatScope.CN_MASTER in available)
        assertEquals(StatScope.CN_PLAT, CodexQuery.resolveScope(StatScope.CN_MASTER, available))
        assertEquals(StatScope.CN_PLAT, CodexQuery.itemTable(items, StatScope.CN_MASTER, ItemFilter()).scope)
        assertEquals(StatScope.KR_PLAT, CodexQuery.resolveScope(StatScope.KR_PLAT, available))
        assertEquals(StatScope.GLOB_PLAT, CodexQuery.resolveScope(StatScope.KR_MASTER, listOf(StatScope.GLOB_PLAT)))
    }

    // -- 증강 ---------------------------------------------------------------

    @Test
    fun `증강 티어는 S부터 비어 있지 않은 그룹만 만든다`() {
        val groups = CodexQuery.augmentTierGroups(augments)
        assertEquals(listOf("S", "A", "B"), groups.map { it.tier })
        assertEquals(listOf("DA_LateGameScaling"), groups.first().augments.map { it.id })
        assertEquals("티어 없는 요정의 문장", 1, CodexQuery.untieredCount(augments))
    }

    @Test
    fun `라운드 확률표가 비어 있으면 행이 없어 표를 숨긴다`() {
        assertTrue(augments.rounds.isEmpty())
        assertTrue(CodexQuery.roundRows(augments).isEmpty())

        val filled = augments.copy(
            rounds = mapOf(
                "4-2" to mapOf("gold" to 0.5),
                "2-1" to mapOf("silver" to 0.6, "gold" to 0.35, "prismatic" to 0.05),
                "3-2" to null,
            ),
        )
        assertEquals(listOf("2-1", "4-2"), CodexQuery.roundRows(filled).map { it.first })
    }

    @Test
    fun `증강 덱별 단계 평균의 빈 값과 표본 부족 표시를 읽는다`() {
        val ascension = augments.augments.first { it.id == "DA_Ascension" }
        val second = ascension.deckStats[1]
        assertEquals(listOf(null, 3.24, 3.23), second.stage)
        assertEquals(listOf(true, false, false), second.stageLowSample)
        assertEquals(listOf("g-7f3a9c1b2d"), ascension.recommendedBy.editorial)
        assertEquals("S", ascension.recommendedBy.guide.single().tier)
        assertEquals("중국 골드~에메랄드", augments.meta.cnStats.label)
    }

    // -- 동기화 판단 ----------------------------------------------------------

    @Test
    fun `해시가 바뀐 파일만 내려받는다`() {
        val loaded = version.files
        assertTrue(StatsRepository.filesToDownload(version, loaded, force = false).isEmpty())

        val changed = version.copy(files = version.files + ("items" to "ffff"))
        assertEquals(listOf("items"), StatsRepository.filesToDownload(changed, loaded, force = false))

        // 올라온 파일의 해시를 모르면(처음이거나 버전 기록이 없으면) 받는다.
        assertEquals(StatsRepository.DATA_FILES, StatsRepository.filesToDownload(version, emptyMap(), force = false))

        // 원격 목록에서 빠진 파일은 건드리지 않는다(수집기가 그 파일을 못 만든 날).
        val partial = version.copy(files = mapOf("champions" to "new"))
        assertEquals(listOf("champions"), StatsRepository.filesToDownload(partial, loaded, force = false))

        assertEquals(StatsRepository.DATA_FILES, StatsRepository.filesToDownload(version, loaded, force = true))
    }

    // -- 표기 ---------------------------------------------------------------

    @Test
    fun `도감 숫자와 아이콘 표기`() {
        assertEquals("4.37", formatAvg(4.37))
        assertEquals("-", formatAvg(null))
        assertEquals("52.5%", formatPct(0.525))
        assertEquals("-", formatPct(Double.NaN))
        assertEquals("9,849", formatCountShort(9849))
        assertEquals("13.4만", formatCountShort(134_007))
        assertEquals("96.7만", formatCountShort(967_672L))
        assertEquals("152만", formatCountShort(1_526_455))
        assertEquals("+0.07", formatDelta(0.07))
        assertEquals("-0.19", formatDelta(-0.19))
        assertEquals("9/14", formatShortDate("20260914"))
        assertEquals("9/15", formatShortDate("2026-09-15"))
        assertEquals("30", formatNumber(30.0))
        assertEquals("67.5", formatNumber(67.5))

        val base = "https://raw.communitydragon.org/latest/game/"
        assertEquals("${base}assets/a.png", codexIconUrl(base, "assets/a.png"))
        assertEquals("https://game.gtimg.cn/x.png", codexIconUrl(base, "https://game.gtimg.cn/x.png"))
        assertNull(codexIconUrl(base, " "))

        assertEquals("대상에게 폭탄을 붙입니다\n3초 뒤 터집니다", cleanDesc(champion("DA_18_Tristana").ability!!.desc))
    }

    // -- 동기화 규칙 ------------------------------------------------------------

    @Test
    fun `받은 파일의 해시가 version 과 다르면 받지 않는다`() {
        assertTrue(StatsRepository.hashMatches("3a9f", "3a9f"))
        assertFalse("새 version.json 과 옛 본체가 섞여 왔다", StatsRepository.hashMatches("3a9f", "0c44"))
        assertTrue("파일에 해시가 없는 옛 형식은 확인할 수 없어 받아들인다", StatsRepository.hashMatches("3a9f", ""))
    }

    @Test
    fun `동봉 스냅샷이 캐시보다 새로우면 캐시를 버린다`() {
        val cached = StatsVersion(schemaVersion = 1, generatedAt = "2026-09-15T20:07:40Z", files = mapOf("champions" to "a"))
        val bundled = cached.copy(generatedAt = "2026-09-16T20:07:40Z")
        assertTrue(StatsRepository.isStale(cached, bundled))
        assertFalse(StatsRepository.isStale(bundled, cached))
        assertFalse(StatsRepository.isStale(cached, cached))
        assertTrue("스키마가 낮으면 시각과 무관하게 옛 것", StatsRepository.isStale(cached.copy(schemaVersion = 0, generatedAt = "2026-09-20T00:00:00Z"), bundled))
        assertFalse("시각을 모르면 버리지 않는다", StatsRepository.isStale(cached.copy(generatedAt = ""), bundled))
    }

    @Test
    fun `일부 파일만 받으면 메타는 그대로 두고 해시만 바꾼다`() {
        val shown = StatsVersion(
            patchGlobal = "18.1", statDate = "2026-09-14", generatedAt = "2026-09-14T20:00:00Z",
            files = mapOf("champions" to "old-c", "traits" to "old-t"),
        )
        val remote = StatsVersion(
            patchGlobal = "18.2", statDate = "2026-09-15", generatedAt = "2026-09-15T20:00:00Z",
            files = mapOf("champions" to "new-c", "traits" to "new-t"),
        )
        val hashes = mapOf("champions" to "new-c", "traits" to "old-t", "junk" to "x")

        val partial = StatsRepository.recordAfterSync(remote, shown, hashes, allReceived = false)
        assertEquals("18.1", partial.patchGlobal)
        assertEquals("2026-09-14", partial.statDate)
        assertEquals(mapOf("champions" to "new-c", "traits" to "old-t"), partial.files)

        val complete = StatsRepository.recordAfterSync(remote, shown, hashes, allReceived = true)
        assertEquals("18.2", complete.patchGlobal)
        assertEquals("2026-09-15", complete.statDate)
        assertEquals("비교할 메타가 없으면 원격 메타", "18.2", StatsRepository.recordAfterSync(remote, null, hashes, false).patchGlobal)
    }

    // -- 실제 스냅샷 ------------------------------------------------------------

    @Test
    fun `배포 스냅샷이 있으면 같은 파서로 읽히고 목록 질의가 돈다`() {
        // 기본은 앱 동봉 스냅샷(assets/stats, 단위 테스트의 작업 디렉터리는 android/app).
        // 수집기 산출물로 돌려 보려면 STATS_SNAPSHOT_DIR 에 data/stats 경로를 주고 --rerun 으로 실행한다.
        val dir = System.getenv("STATS_SNAPSHOT_DIR")?.takeIf { it.isNotBlank() }?.let(::File)
            ?: File("src/main/assets/stats")
        assumeTrue("도감 스냅샷이 아직 없다(수집기 통합 전): ${dir.absolutePath}", File(dir, "version.json").exists())

        val snapshotVersion = StatsParser.parseVersion(File(dir, "version.json").readText())
        assertNotNull("version.json 파싱 실패", snapshotVersion)
        fun read(name: String): String? = File(dir, "$name.json").takeIf { it.exists() }?.readText()
        val summary = mutableListOf("패치 ${snapshotVersion!!.patchGlobal}/${snapshotVersion.patch}")

        read("champions")?.let { text ->
            val file = StatsParser.parseChampions(text)
            assertNotNull("champions.json 파싱 실패", file)
            assertTrue("champions 해시가 version.json 과 다르다", StatsRepository.hashMatches(snapshotVersion.files["champions"].orEmpty(), file!!.version.contentHash))
            val scopes = CodexQuery.availableScopes(file!!.scopes)
            val withStats = scopes.associateWith { scope ->
                ChampionSort.entries.forEach { CodexQuery.championTable(file, scope, ChampionFilter(sort = it)) }
                CodexQuery.championTable(file, scope, ChampionFilter()).rows.count { it.stat != null }
            }
            assertTrue("어느 스코프에도 챔피언 성적이 없다: $withStats", withStats.values.any { it > 0 })
            summary += "챔피언 ${file.champions.size}(스코프별 성적 $withStats)"
        }
        read("traits")?.let { text ->
            val file = StatsParser.parseTraits(text)
            assertNotNull("traits.json 파싱 실패", file)
            assertTrue("traits 해시가 version.json 과 다르다", StatsRepository.hashMatches(snapshotVersion.files["traits"].orEmpty(), file!!.version.contentHash))
            val staged = CodexQuery.availableScopes(file!!.scopes).sumOf { scope ->
                CodexQuery.traitTable(file, scope, TraitFilter())
                CodexQuery.traitTable(file, scope, TraitFilter(byStage = true)).rows.count { it.stat != null }
            }
            summary += "특성 ${file.traits.size}(단계 성적 행 $staged)"
        }
        read("items")?.let { text ->
            val file = StatsParser.parseItems(text)
            assertNotNull("items.json 파싱 실패", file)
            assertTrue("items 해시가 version.json 과 다르다", StatsRepository.hashMatches(snapshotVersion.files["items"].orEmpty(), file!!.version.contentHash))
            CodexQuery.availableScopes(file!!.scopes).forEach { CodexQuery.itemTable(file, it, ItemFilter()) }
            val components = CodexQuery.componentIds(file)
            val gridCells = components.sumOf { a -> components.count { b -> file.recipeFor(a, b) != null } }
            summary += "아이템 ${file.items.size}(부품 ${components.size}, 조합표 칸 $gridCells)"
        }
        read("augments")?.let { text ->
            val file = StatsParser.parseAugments(text)
            assertNotNull("augments.json 파싱 실패", file)
            assertTrue("augments 해시가 version.json 과 다르다", StatsRepository.hashMatches(snapshotVersion.files["augments"].orEmpty(), file!!.version.contentHash))
            CodexQuery.augmentRows(file!!, AugmentFilter())
            val tiers = CodexQuery.augmentTierGroups(file).associate { it.tier to it.augments.size }
            summary += "증강 ${file.augments.size}(티어 $tiers, 덱별 성적 ${file.augments.count { it.deckStats.isNotEmpty() }}, 라운드 ${CodexQuery.roundRows(file).size})"
        }
        println("도감 스냅샷 ${dir.path}: ${summary.joinToString(" · ")}")
    }

    // -- 도우미 ---------------------------------------------------------------

    private fun fixture(name: String): String =
        javaClass.classLoader?.getResourceAsStream("stats/$name.json")?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: File("src/test/resources/stats/$name.json").readText(Charsets.UTF_8)

    private fun champion(id: String): ChampionRow = champions.champions.first { it.id == id }

    private fun trait(id: String): TraitRow = traits.traits.first { it.id == id }

    private fun item(id: String): ItemRow = items.items.first { it.id == id }

    private fun championIds(filter: ChampionFilter, scope: String = StatScope.KR_PLAT): List<String> =
        CodexQuery.championTable(champions, scope, filter).rows.map { it.champion.id }

    private fun itemIds(filter: ItemFilter, scope: String = StatScope.KR_PLAT): List<String> =
        CodexQuery.itemTable(items, scope, filter).rows.map { it.item.id }
}
