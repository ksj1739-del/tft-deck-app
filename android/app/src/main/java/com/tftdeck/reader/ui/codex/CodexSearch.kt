package com.tftdeck.reader.ui.codex

import com.tftdeck.reader.data.AugmentRow
import com.tftdeck.reader.data.AugmentsFile
import com.tftdeck.reader.data.ChampionRow
import com.tftdeck.reader.data.ChampionsFile
import com.tftdeck.reader.data.CodexRef
import com.tftdeck.reader.data.CodexStat
import com.tftdeck.reader.data.ItemRow
import com.tftdeck.reader.data.ItemWearerStat
import com.tftdeck.reader.data.ItemsFile
import com.tftdeck.reader.data.StatScope
import com.tftdeck.reader.data.StatsScopeMeta
import com.tftdeck.reader.data.TraitRow
import com.tftdeck.reader.data.TraitsFile

/**
 * 도감 이름 검색. 덱 검색(DeckSearch)과 같은 규칙을 따르지만 코드는 공유하지 않는다
 * (패키지 소유가 달라 그쪽 내부 함수를 쓸 수 없다).
 *
 *   "아리"          부분 일치
 *   "ㅁㅎㅇ"         초성
 *   "무대"          각 어절 첫 글자 줄임말
 *   "infinity"      영문명
 *   "ie"            영문 두문자
 */
object CodexSearch {

    private const val NO_MATCH = Int.MAX_VALUE
    private const val HANGUL_BASE = 0xAC00
    private const val HANGUL_END = 0xD7A3

    // 한글 음절 = 0xAC00 + (초성 × 21 + 중성) × 28 + 종성 → 초성 = (코드 - 0xAC00) / 588
    private const val SYLLABLES_PER_CHOSEONG = 588
    private val CHOSEONG = charArrayOf(
        'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ',
        'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ',
    )

    /** 한 이름에 대해 검색어와 맞춰 볼 문자열들. */
    fun keysFor(name: String, nameEn: String?): List<String> = buildList {
        add(name.lowercase())
        nameEn?.takeIf { it.isNotBlank() }?.lowercase()?.let(::add)
        initials(name)?.let(::add)
        abbreviation(name)?.let(::add)
        nameEn?.let { en ->
            val words = en.split(' ', '.', '\'', '-').filter { it.isNotBlank() }
            if (words.size > 1) add(words.joinToString("") { it.first().lowercase() })
        }
    }.distinct()

    /** "무한의 대검" -> "ㅁㅎㅇㄷㄱ"(공백 제거). 한글이 없으면 null. */
    fun initials(name: String): String? {
        val sb = StringBuilder()
        var sawHangul = false
        for (ch in name) {
            when {
                ch.code in HANGUL_BASE..HANGUL_END -> {
                    sawHangul = true
                    sb.append(CHOSEONG[(ch.code - HANGUL_BASE) / SYLLABLES_PER_CHOSEONG])
                }
                ch.isWhitespace() -> Unit
                else -> sb.append(ch.lowercaseChar())
            }
        }
        return if (sawHangul) sb.toString() else null
    }

    /** "무한의 대검" -> "무대". 어절이 하나뿐이면 null. */
    fun abbreviation(name: String): String? {
        val words = name.split(' ', '·').filter { it.isNotBlank() }
        if (words.size < 2) return null
        return words.joinToString("") { it.first().toString() }.lowercase()
    }

    /**
     * 맞춘 정도. 0 정확 · 1 앞부분 · 2 포함 · 3 설명에만 있음. 맞지 않으면 null.
     * 빈 검색어는 모든 항목이 0점이다(필터를 걸지 않은 것과 같다).
     */
    fun score(query: String, name: String, nameEn: String? = null, extra: String? = null): Int? {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return 0
        val best = keysFor(name, nameEn).minOf { rank(it, needle) }
        if (best != NO_MATCH) return best
        if (!extra.isNullOrBlank() && extra.lowercase().contains(needle)) return 3
        return null
    }

    fun matches(query: String, name: String, nameEn: String? = null, extra: String? = null): Boolean =
        score(query, name, nameEn, extra) != null

    private fun rank(key: String, needle: String): Int = when {
        key == needle -> 0
        key.startsWith(needle) -> 1
        key.contains(needle) -> 2
        else -> NO_MATCH
    }
}

// ---------------------------------------------------------------------------
// 목록 질의(필터·정렬). 화면과 떼어 두어 단위 테스트로 검증한다.
// ---------------------------------------------------------------------------

enum class ChampionSort(val key: String, val label: String) {
    GRADE("grade", "등급"),
    AVG("avg", "평균 등수"),
    PICK("pick", "픽률"),
    NAME("name", "이름");

    companion object {
        fun fromKey(key: String?): ChampionSort = entries.firstOrNull { it.key == key } ?: GRADE
    }
}

data class ChampionFilter(
    val costs: Set<Int> = emptySet(),
    val traitId: String? = null,
    val sort: ChampionSort = ChampionSort.GRADE,
    val query: String = "",
)

data class ChampionListRow(
    val champion: ChampionRow,
    val stat: CodexStat?,
    val lowSample: Boolean,
) {
    /** 값이 없거나 표본이 부족하면 흐리게 그린다. */
    val dimmed: Boolean get() = stat == null || lowSample
}

data class TraitFilter(
    /** null = 전체, 4 프리즘 … 1 브론즈. */
    val style: Int? = null,
    /** 비면 전체. "origin"(계열), "class"(직업). */
    val types: Set<String> = emptySet(),
    /** 켜면 단계마다 한 줄, 끄면 특성마다 표본이 가장 큰 단계 한 줄. */
    val byStage: Boolean = false,
)

data class TraitListRow(
    val trait: TraitRow,
    /** 활성 인원수. 단계 정보가 전혀 없는 특성이면 null. */
    val units: Int?,
    val style: Int,
    val stat: CodexStat?,
    val lowSample: Boolean,
) {
    val key: String get() = "${trait.id}#${units ?: 0}"
    val dimmed: Boolean get() = stat == null || lowSample
}

data class ItemFilter(
    /** null = 전체(재료 제외). */
    val kind: String? = null,
    /** 고른 부품 id. 최대 두 개, 고른 순서대로. */
    val components: List<String> = emptyList(),
    val query: String = "",
)

data class ItemListRow(
    val item: ItemRow,
    val stat: CodexStat?,
    val lowSample: Boolean,
    val wearers: List<ItemWearerStat>,
    /** 착용자를 가져온 스코프. 선택한 스코프에 없어 다른 스코프로 대신했을 수 있다. */
    val wearerScope: String?,
) {
    val dimmed: Boolean get() = stat == null || lowSample
}

data class AugmentFilter(
    val rarity: String? = null,
    val tag: String? = null,
    val query: String = "",
)

data class AugmentTierGroup(val tier: String, val augments: List<AugmentRow>)

/** 표 하나: 실제로 쓴 스코프, 고를 수 있는 스코프, 흐림 기준, 행. */
data class CodexTable<T>(
    val scope: String,
    val scopes: List<String>,
    val minSample: Int,
    val rows: List<T>,
)

object CodexQuery {

    const val MAX_COMPONENTS = 2
    const val WEARER_LIMIT = 5
    const val KIND_COMPONENT = "component"
    val TIER_ORDER = listOf("S", "A", "B", "C", "D")

    // -- 스코프 ---------------------------------------------------------------

    /** 파일에 실제로 있는 스코프를 화면 순서대로. 메타가 통째로 없으면 다섯 개 모두. */
    fun availableScopes(scopes: Map<String, StatsScopeMeta?>): List<String> {
        val present = scopes.filterValues { it != null }.keys
        if (present.isEmpty()) return StatScope.ORDER
        return StatScope.ORDER.filter { it in present } + present.filterNot { it in StatScope.ORDER }.sorted()
    }

    /**
     * 기억해 둔 스코프가 이 파일에 없으면 가장 가까운 것으로 대신한다.
     * 저장된 선택은 바꾸지 않는다(다른 탭에는 그 스코프가 있을 수 있다).
     */
    fun resolveScope(preferred: String, available: List<String>): String {
        if (available.isEmpty() || preferred in available) return preferred
        val sibling = when (preferred) {
            StatScope.KR_MASTER -> StatScope.KR_PLAT
            StatScope.CN_MASTER -> StatScope.CN_PLAT
            StatScope.KR_PLAT -> StatScope.KR_MASTER
            StatScope.CN_PLAT -> StatScope.CN_MASTER
            else -> null
        }
        return listOfNotNull(sibling, StatScope.DEFAULT, StatScope.GLOB_PLAT).firstOrNull { it in available }
            ?: available.first()
    }

    /** 스코프별 목록 필드에서 쓸 것을 고른다: 그 스코프 → 같은 지역의 넓은 스코프 → 글로벌 → 아무거나. */
    fun <T> scopedList(map: Map<String, List<T>?>, scope: String): Pair<String, List<T>>? {
        val order = listOfNotNull(scope, broader(scope), StatScope.GLOB_PLAT).distinct()
        for (key in order) {
            val list = map[key]
            if (!list.isNullOrEmpty()) return key to list
        }
        return map.entries.firstOrNull { !it.value.isNullOrEmpty() }?.let { it.key to it.value.orEmpty() }
    }

    private fun broader(scope: String): String? = when (scope) {
        StatScope.KR_MASTER -> StatScope.KR_PLAT
        StatScope.CN_MASTER -> StatScope.CN_PLAT
        else -> null
    }

    // -- 정렬 공통 ------------------------------------------------------------

    fun gradeRank(grade: String?): Int = when (grade?.trim()?.uppercase()) {
        "SS" -> 0
        "S" -> 1
        "A" -> 2
        "B" -> 3
        "C" -> 4
        "D" -> 5
        else -> 9
    }

    fun rarityRank(rarity: String?): Int = when (rarity?.lowercase()) {
        "prismatic" -> 0
        "gold" -> 1
        "silver" -> 2
        else -> 3
    }

    /**
     * 0 정상 표본, 1 표본 부족, 2 값 없음.
     * 어떤 통계 정렬이든 이 순서를 먼저 지켜, 표본 몇 판짜리 행이 맨 위를 차지하지 않게 한다.
     */
    private fun sampleTier(stat: CodexStat?, lowSample: Boolean, value: Double?): Int = when {
        stat == null || value == null || !value.isFinite() -> 2
        lowSample -> 1
        else -> 0
    }

    private fun lowSample(stat: CodexStat?, minSample: Int): Boolean = stat != null && stat.isLowSample(minSample)

    // -- 챔피언 ---------------------------------------------------------------

    fun championTable(file: ChampionsFile, preferredScope: String, filter: ChampionFilter): CodexTable<ChampionListRow> {
        val scopes = availableScopes(file.scopes)
        val scope = resolveScope(preferredScope, scopes)
        val minSample = file.minSample
        val rows = file.champions
            .distinctBy { it.id }
            .filter { filter.costs.isEmpty() || it.cost in filter.costs }
            .filter { champion -> filter.traitId == null || champion.traits.any { it.id == filter.traitId } }
            .filter { CodexSearch.matches(filter.query, it.name, it.nameEn) }
            .map { champion ->
                val stat = champion.stat(scope)
                ChampionListRow(champion, stat, lowSample(stat, minSample))
            }
        return CodexTable(scope, scopes, minSample, sortChampions(rows, filter.sort))
    }

    fun sortChampions(rows: List<ChampionListRow>, sort: ChampionSort): List<ChampionListRow> = when (sort) {
        ChampionSort.GRADE -> rows.sortedWith(
            compareBy<ChampionListRow>(
                { sampleTier(it.stat, it.lowSample, it.stat?.avg) },
                { gradeRank(it.stat?.grade) },
                { it.stat?.avg ?: Double.MAX_VALUE },
                { it.champion.name },
            )
        )
        ChampionSort.AVG -> rows.sortedWith(
            compareBy<ChampionListRow>(
                { sampleTier(it.stat, it.lowSample, it.stat?.avg) },
                { it.stat?.avg ?: Double.MAX_VALUE },
                { it.champion.name },
            )
        )
        ChampionSort.PICK -> rows.sortedWith(
            compareBy<ChampionListRow>(
                { sampleTier(it.stat, it.lowSample, it.stat?.pick) },
                { -(it.stat?.pick ?: 0.0) },
                { it.champion.name },
            )
        )
        ChampionSort.NAME -> rows.sortedBy { it.champion.name }
    }

    /** 챔피언 탭 특성 드롭다운: 챔피언들이 가진 특성을 이름순으로. */
    fun championTraitOptions(file: ChampionsFile): List<CodexRef> =
        file.champions.flatMap { it.traits }
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }
            .sortedBy { it.name }

    // -- 특성 -----------------------------------------------------------------

    fun traitTable(file: TraitsFile, preferredScope: String, filter: TraitFilter): CodexTable<TraitListRow> {
        val scopes = availableScopes(file.scopes)
        val scope = resolveScope(preferredScope, scopes)
        val minSample = file.minSample
        val rows = file.traits
            .distinctBy { it.id }
            .filter { filter.types.isEmpty() || it.type in filter.types }
            .flatMap { trait ->
                val units = trait.stageUnits
                val stages = units
                    .map { count ->
                        val stat = trait.stat(scope, count)
                        TraitListRow(trait, count, trait.styleFor(count), stat, lowSample(stat, minSample))
                    }
                    .filter { filter.style == null || it.style == filter.style }
                when {
                    stages.isEmpty() ->
                        if (filter.style == null && units.isEmpty()) listOf(TraitListRow(trait, null, 0, null, false))
                        else emptyList()
                    filter.byStage -> stages
                    else -> listOf(representativeStage(stages))
                }
            }
        return CodexTable(scope, scopes, minSample, sortTraits(rows))
    }

    /** 특성당 한 줄이면 표본이 가장 큰 단계를 보여 준다(같으면 낮은 단계). */
    private fun representativeStage(stages: List<TraitListRow>): TraitListRow =
        stages.maxWith(compareBy<TraitListRow> { it.stat?.n ?: -1 }.thenByDescending { it.units ?: 0 })

    private fun sortTraits(rows: List<TraitListRow>): List<TraitListRow> = rows.sortedWith(
        compareBy<TraitListRow>(
            { sampleTier(it.stat, it.lowSample, it.stat?.avg) },
            { gradeRank(it.stat?.grade) },
            { it.stat?.avg ?: Double.MAX_VALUE },
            { it.trait.name },
            { it.units ?: 0 },
        )
    )

    // -- 아이템 ---------------------------------------------------------------

    fun itemTable(file: ItemsFile, preferredScope: String, filter: ItemFilter): CodexTable<ItemListRow> {
        val scopes = availableScopes(file.scopes)
        val scope = resolveScope(preferredScope, scopes)
        val minSample = file.minSample
        val rows = file.items
            .distinctBy { it.id }
            // 재료는 조합표·부품 칩으로 보여 준다. 통계 표에 섞으면 완성템 비교가 흐려진다.
            .filter { it.kind != KIND_COMPONENT }
            .filter { filter.kind == null || it.kind == filter.kind }
            .filter { hasComponents(it, filter.components) }
            .filter { CodexSearch.matches(filter.query, it.name, it.nameEn) }
            .map { item ->
                val stat = item.stat(scope)
                val wearers = wearersFor(item, scope)
                ItemListRow(item, stat, lowSample(stat, minSample), wearers?.second.orEmpty(), wearers?.first)
            }
            .sortedWith(
                compareBy<ItemListRow>(
                    { sampleTier(it.stat, it.lowSample, it.stat?.avg) },
                    { gradeRank(it.stat?.grade) },
                    { it.stat?.avg ?: Double.MAX_VALUE },
                    { it.item.name },
                )
            )
        return CodexTable(scope, scopes, minSample, rows)
    }

    /** 고른 부품이 모두 들어간 아이템만. 같은 부품 두 개짜리 조합도 그 부품 하나로 찾힌다. */
    fun hasComponents(item: ItemRow, selected: List<String>): Boolean =
        selected.isEmpty() || selected.all { it in item.components }

    /** 부품 칩 토글. 세 번째를 고르면 가장 먼저 고른 것을 밀어낸다. */
    fun toggleComponent(current: List<String>, id: String): List<String> = when {
        id in current -> current - id
        current.size >= MAX_COMPONENTS -> current.drop(current.size - MAX_COMPONENTS + 1) + id
        else -> current + id
    }

    /** 조합표 머리칸. 파일에 순서가 없으면 재료 행으로 대신한다. */
    fun componentIds(file: ItemsFile): List<String> =
        file.components.ifEmpty { file.items.filter { it.kind == KIND_COMPONENT }.map { it.id } }.distinct()

    /** 이 부품으로 만드는 완성 아이템 id(조합표 역조회). */
    fun itemsUsing(file: ItemsFile, componentId: String): List<String> =
        file.recipes.entries
            .filter { (key, _) -> key.split('|').contains(componentId) }
            .map { it.value }
            .distinct()

    /** 착용자 상위 5. 그 스코프에 없으면 같은 지역의 넓은 스코프, 그다음 글로벌로 대신한다. */
    fun wearersFor(item: ItemRow, scope: String): Pair<String, List<ItemWearerStat>>? {
        val order = listOfNotNull(scope, broader(scope), StatScope.GLOB_PLAT).distinct()
        for (key in order) {
            val list = item.wearers(key)
            if (list.isNotEmpty()) return key to list.take(WEARER_LIMIT)
        }
        return null
    }

    // -- 증강 -----------------------------------------------------------------

    fun augmentRows(file: AugmentsFile, filter: AugmentFilter): List<AugmentRow> {
        val scored = file.augments
            .distinctBy { it.id }
            .filter { filter.rarity == null || it.rarity.equals(filter.rarity, ignoreCase = true) }
            .filter { filter.tag == null || filter.tag in it.tags }
            .mapNotNull { augment ->
                CodexSearch.score(filter.query, augment.name, augment.nameEn, augment.desc)?.let { augment to it }
            }
        return scored.sortedWith(
            compareBy<Pair<AugmentRow, Int>>(
                // 검색 중이면 이름에서 맞은 것이 설명에서만 맞은 것보다 먼저 온다.
                { it.second },
                { gradeRank(it.first.editorTier) },
                { rarityRank(it.first.rarity) },
                { it.first.name },
            )
        ).map { it.first }
    }

    /** 에디터 티어 격자: S부터 D까지 비어 있지 않은 그룹만. 그룹 안은 희귀도 → 이름순. */
    fun augmentTierGroups(file: AugmentsFile): List<AugmentTierGroup> {
        val unique = file.augments.distinctBy { it.id }
        return TIER_ORDER
            .map { tier ->
                AugmentTierGroup(
                    tier,
                    unique.filter { it.editorTier?.trim().equals(tier, ignoreCase = true) }
                        .sortedWith(compareBy({ rarityRank(it.rarity) }, { it.name })),
                )
            }
            .filter { it.augments.isNotEmpty() }
    }

    fun untieredCount(file: AugmentsFile): Int =
        file.augments.distinctBy { it.id }.count { augment ->
            TIER_ORDER.none { augment.editorTier?.trim().equals(it, ignoreCase = true) }
        }

    /** 라운드별 확률표 행. rounds가 비면 빈 목록이고, 화면은 표를 숨긴다. */
    fun roundRows(file: AugmentsFile): List<Pair<String, Map<String, Double?>>> =
        file.rounds.entries
            .mapNotNull { (round, odds) -> odds?.takeIf { it.isNotEmpty() }?.let { round to it } }
            .sortedBy { roundOrder(it.first) }

    private fun roundOrder(round: String): Int {
        val parts = round.split('-')
        return (parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 99) * 100 + (parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0)
    }
}
