@file:UseSerializers(FlexIntSerializer::class, FlexLongSerializer::class)

package com.tftdeck.reader.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.roundToLong

/*
 * 도감 통계 파일(data/stats 아래 다섯 JSON) 구조. 계약은 설계 문서 §5.3~§5.7.
 *
 * 원칙은 덱 피드와 같다: 모든 필드에 기본값을 두고 모르는 필드는 무시한다.
 * 파일 하나가 통째로 안 읽히면 도감 탭 하나가 비어 버리므로, 파이썬 수집기가 흔히 내는
 * 흔들림(정수 자리에 20.0, 숫자가 문자열, NaN, 스코프 값 null)까지 받아 준다.
 *
 * 같은 패키지의 Models.kt(덱 피드)에도 스코프·등급 컷 같은 타입이 생기므로,
 * 이름이 겹쳐 컴파일이 깨지지 않도록 도감 쪽 타입에는 소유를 드러내는 접두사를 붙인다.
 * JSON 키 이름은 계약 그대로다.
 */

// ---------------------------------------------------------------------------
// 공통
// ---------------------------------------------------------------------------

/** 통계 스코프 키. 수집기와 글자 그대로 맞춰야 한다(§5 표). */
object StatScope {
    const val GLOB_PLAT = "glob_plat"
    const val KR_PLAT = "kr_plat"
    const val KR_MASTER = "kr_master"
    const val CN_PLAT = "cn_plat"
    const val CN_MASTER = "cn_master"

    /** 화면에 늘어놓는 순서. 기본값은 한국 사용자에게 가장 가까운 KR 플래+. */
    val ORDER = listOf(GLOB_PLAT, KR_PLAT, KR_MASTER, CN_PLAT, CN_MASTER)
    const val DEFAULT = KR_PLAT

    /** lol.qq 스코프는 패치 표기(16.18)와 수치 정의(top4가 횟수)가 metatft와 다르다. */
    fun isChina(scope: String): Boolean = scope.startsWith("cn_")
}

/** stats/version.json 과, 각 파일 안의 version 객체가 같은 모양을 쓴다. */
@Serializable
data class StatsVersion(
    val schemaVersion: Int = 0,
    val generatedAt: String = "",
    val set: String = "",
    val setNumber: Int = 0,
    val patchGlobal: String = "",
    val patch: String = "",
    val qqBuild: String = "",
    val statDate: String = "",
    /** 파일 이름("champions") -> 내용 해시. 앱은 해시가 바뀐 파일만 내려받는다. */
    val files: Map<String, String> = emptyMap(),
    val sources: Map<String, String> = emptyMap(),
    val contentHash: String = "",
) {
    val hasDegradedSource: Boolean get() = sources.values.any { it != "ok" }
}

@Serializable
data class StatsScopeMeta(
    val label: String = "",
    val source: String = "",
    val days: Int = 0,
    val games: Long = 0,
    val boards: Long = 0,
    val updatedAt: String = "",
    val statDate: String = "",
    val build: String = "",
    val meanAvg: Double? = null,
    val unitsPerBoard: Double? = null,
) {
    /** 표본 크기. 도감 파일은 games, 덱 피드 계약은 boards를 쓴다. */
    val sampleSize: Long get() = if (games > 0) games else boards
}

/** 등급 컷. 앱은 등급을 계산하지 않고 minSample(흐림·정렬 기준)만 쓴다. */
@Serializable
data class StatsGradeCuts(
    @SerialName("S") val s: Double? = null,
    @SerialName("A") val a: Double? = null,
    @SerialName("B") val b: Double? = null,
    @SerialName("C") val c: Double? = null,
    val minSample: Int? = null,
)

/** 한 스코프의 성적 한 칸(계약의 ScopeStat). */
@Serializable
data class CodexStat(
    val n: Int = 0,
    /** metatft 스코프만 있다. 1등부터 8등까지 보드 수. */
    val places: List<Int> = emptyList(),
    val avg: Double? = null,
    /** metatft 스코프는 TOP4 비율(0~1), lol.qq 스코프는 TOP4 횟수다. 화면에는 [top4Share]를 쓴다. */
    val top4: Double? = null,
    val win: Double? = null,
    val pick: Double? = null,
    /** 평균 등수 차이. 음수가 좋다(부호를 뒤집지 않고 그대로 싣는다). */
    val delta: Double? = null,
    val grade: String? = null,
    /** lol.qq 스코프의 1등 횟수. */
    val top1: Long? = null,
    /** lol.qq 스코프의 TOP4 비율. */
    val top4Rate: Double? = null,
) {
    /** 스코프 출처와 무관하게 TOP4 비율. */
    val top4Share: Double?
        get() {
            top4Rate?.takeIf { it.isFinite() }?.let { return it }
            val raw = top4?.takeIf { it.isFinite() } ?: return null
            // 1을 넘으면 비율이 아니라 횟수다(lol.qq). 표본으로 나눠 비율로 바꾼다.
            return if (raw <= 1.0) raw else if (n > 0) raw / n else null
        }

    fun isLowSample(minSample: Int): Boolean = n < minSample
}

/** 지난 패치 마지막 값(수집기가 패치가 바뀐 날 동결한다). */
@Serializable
data class CodexPrevStat(
    val avg: Double? = null,
    val pick: Double? = null,
)

/** id와 이름만 있는 참조. */
@Serializable
data class CodexRef(
    val id: String = "",
    val name: String = "",
)

// ---------------------------------------------------------------------------
// 챔피언 (§5.4)
// ---------------------------------------------------------------------------

@Serializable
data class ChampionsFile(
    val version: StatsVersion = StatsVersion(),
    val scopes: Map<String, StatsScopeMeta?> = emptyMap(),
    val gradeCuts: StatsGradeCuts = StatsGradeCuts(),
    val prevPatch: ChampionPrevPatch? = null,
    val champions: List<ChampionRow> = emptyList(),
) {
    val minSample: Int get() = gradeCuts.minSample ?: DEFAULT_MIN_SAMPLE

    companion object {
        /** 수집기 계약의 챔피언·아이템 minSample. 파일에 없을 때만 쓴다. */
        const val DEFAULT_MIN_SAMPLE = 1000
    }
}

@Serializable
data class ChampionPrevPatch(
    val patchGlobal: String = "",
    val frozenAt: String = "",
)

@Serializable
data class ChampionRow(
    val id: String = "",
    val name: String = "",
    val nameEn: String? = null,
    val cost: Int? = null,
    val icon: String? = null,
    val traits: List<CodexRef> = emptyList(),
    val role: String? = null,
    val ability: ChampionAbility? = null,
    val base: ChampionBase? = null,
    /** buff / nerf / new, 변화 없으면 null. */
    val changed: String? = null,
    /** 통계 행이 없는 챔피언은 빈 객체다. */
    val stats: Map<String, CodexStat?> = emptyMap(),
    val starStats: Map<String, List<ChampionStarStat>?> = emptyMap(),
    val items: ChampionItems = ChampionItems(),
    val cnItems: List<ChampionItemStat> = emptyList(),
    val prev: Map<String, CodexPrevStat?> = emptyMap(),
    /** decks.json 덱 id. 도감에서 덱으로 넘어갈 때 쓴다. */
    val decks: List<String> = emptyList(),
) {
    fun stat(scope: String): CodexStat? = stats[scope]

    fun grade(scope: String): String? = stats[scope]?.grade
}

@Serializable
data class ChampionAbility(
    val name: String = "",
    val desc: String = "",
    /** [시작 마나, 최대 마나]. CommunityDragon 원본이 실수라 20.0으로 올 수 있어 Double로 받는다. */
    val mana: List<Double> = emptyList(),
)

/** 기본 능력치. 체력·공격력은 1/2/3성 값 목록이다. */
@Serializable
data class ChampionBase(
    val health: List<Double> = emptyList(),
    val attackDamage: List<Double> = emptyList(),
    val armor: Double? = null,
    val magicResist: Double? = null,
    val attackSpeed: Double? = null,
    val range: Double? = null,
)

@Serializable
data class ChampionStarStat(
    val star: Int = 0,
    val n: Int = 0,
    val avg: Double? = null,
)

@Serializable
data class ChampionItems(
    /** 이 추천이 어느 스코프 기준인지(보통 glob_plat). */
    val scope: String = "",
    val top: List<ChampionItemStat> = emptyList(),
    val builds: List<ChampionBuildStat> = emptyList(),
)

@Serializable
data class ChampionItemStat(
    val id: String = "",
    val name: String = "",
    val icon: String? = null,
    val n: Int = 0,
    val avg: Double? = null,
    /** 이 아이템을 들었을 때 평균 - 챔피언 전체 평균. 음수가 좋다. */
    val delta: Double? = null,
)

@Serializable
data class ChampionBuildStat(
    val items: List<String> = emptyList(),
    val n: Int = 0,
    val avg: Double? = null,
)

// ---------------------------------------------------------------------------
// 특성 (§5.5)
// ---------------------------------------------------------------------------

@Serializable
data class TraitsFile(
    val version: StatsVersion = StatsVersion(),
    val scopes: Map<String, StatsScopeMeta?> = emptyMap(),
    val gradeCuts: StatsGradeCuts = StatsGradeCuts(),
    val traits: List<TraitRow> = emptyList(),
) {
    val minSample: Int get() = gradeCuts.minSample ?: DEFAULT_MIN_SAMPLE

    companion object {
        /** 수집기 계약의 특성 단계 minSample. */
        const val DEFAULT_MIN_SAMPLE = 500
    }
}

@Serializable
data class TraitRow(
    val id: String = "",
    val name: String = "",
    val nameEn: String? = null,
    /** origin(계열) / class(직업). */
    val type: String? = null,
    val icon: String? = null,
    val desc: String = "",
    val breakpoints: List<TraitBreakpoint> = emptyList(),
    val champions: List<TraitChampionRef> = emptyList(),
    val stats: Map<String, TraitScopeStats?> = emptyMap(),
    val combos: List<TraitCombo> = emptyList(),
    val decks: List<String> = emptyList(),
) {
    /** 활성 인원수 [units]일 때의 성적. metatft 단계 순번은 수집기가 이미 인원수로 바꿔 둔다. */
    fun stat(scope: String, units: Int): CodexStat? = stats[scope]?.byUnits?.get(units.toString())

    /** 인원수에 해당하는 등급(1 브론즈 … 4 프리즘). 모르면 0. */
    fun styleFor(units: Int): Int = breakpoints.firstOrNull { it.units == units }?.style ?: 0

    /** 표시할 단계 인원수. 단계 표가 비었으면 통계에 있는 인원수로 대신한다. */
    val stageUnits: List<Int>
        get() = breakpoints.map { it.units }.distinct().sorted().ifEmpty {
            stats.values.filterNotNull().flatMap { it.byUnits.keys }.mapNotNull { it.toIntOrNull() }.distinct().sorted()
        }

    val topStyle: Int get() = breakpoints.maxOfOrNull { it.style } ?: 0
}

@Serializable
data class TraitBreakpoint(
    val units: Int = 0,
    /** 1 브론즈, 2 실버, 3 골드, 4 프리즘. */
    val style: Int = 0,
    val desc: String = "",
)

@Serializable
data class TraitChampionRef(
    val id: String = "",
    val name: String = "",
    val cost: Int? = null,
)

@Serializable
data class TraitScopeStats(
    /** 활성 인원수 문자열("3") -> 성적. */
    val byUnits: Map<String, CodexStat?> = emptyMap(),
)

@Serializable
data class TraitCombo(
    @SerialName("with") val partner: TraitComboPartner = TraitComboPartner(),
    val units: Int = 0,
    val top4: Double? = null,
    val win: Double? = null,
    /** TOP4 비율 추세. 오래된 것부터 최신이 마지막. 빠진 날은 null. */
    val trend: List<Double?> = emptyList(),
)

@Serializable
data class TraitComboPartner(
    val id: String = "",
    val name: String = "",
    val units: Int = 0,
)

// ---------------------------------------------------------------------------
// 아이템 (§5.6)
// ---------------------------------------------------------------------------

@Serializable
data class ItemsFile(
    val version: StatsVersion = StatsVersion(),
    val scopes: Map<String, StatsScopeMeta?> = emptyMap(),
    val gradeCuts: StatsGradeCuts = StatsGradeCuts(),
    val items: List<ItemRow> = emptyList(),
    /** 조합표 머리칸 순서(기본 아이템 10종). */
    val components: List<String> = emptyList(),
    /** "부품A|부품B"(id 정렬) -> 완성 아이템 id. */
    val recipes: Map<String, String> = emptyMap(),
) {
    val minSample: Int get() = gradeCuts.minSample ?: ChampionsFile.DEFAULT_MIN_SAMPLE

    /** 두 부품으로 만드는 아이템. 순서와 무관하게 찾는다. */
    fun recipeFor(first: String, second: String): String? =
        recipes[recipeKey(first, second)] ?: recipes["$first|$second"] ?: recipes["$second|$first"]

    companion object {
        /** 수집기와 같은 규칙: 두 id를 정렬해 |로 잇는다. */
        fun recipeKey(first: String, second: String): String =
            if (first <= second) "$first|$second" else "$second|$first"
    }
}

@Serializable
data class ItemRow(
    val id: String = "",
    val name: String = "",
    val nameEn: String? = null,
    /** component / completed / emblem / artifact / radiant / support / other. */
    val kind: String = "other",
    val icon: String? = null,
    val desc: String = "",
    val components: List<String> = emptyList(),
    val stats: Map<String, CodexStat?> = emptyMap(),
    val wearers: Map<String, List<ItemWearerStat>?> = emptyMap(),
    val stages: List<ItemStageStat> = emptyList(),
    val prev: Map<String, CodexPrevStat?> = emptyMap(),
    val decks: List<String> = emptyList(),
) {
    fun stat(scope: String): CodexStat? = stats[scope]

    /** 그 스코프의 착용 챔피언 상위. 없으면 빈 목록(대체 스코프는 CodexQuery가 고른다). */
    fun wearers(scope: String): List<ItemWearerStat> = wearers[scope].orEmpty()
}

@Serializable
data class ItemWearerStat(
    val id: String = "",
    val name: String = "",
    val n: Int = 0,
    val avg: Double? = null,
    val delta: Double? = null,
)

@Serializable
data class ItemStageStat(
    val stage: Int = 0,
    val n: Int = 0,
    val win: Double? = null,
)

// ---------------------------------------------------------------------------
// 증강 (§5.7)
// ---------------------------------------------------------------------------

@Serializable
data class AugmentsFile(
    val version: StatsVersion = StatsVersion(),
    val meta: AugmentsMeta = AugmentsMeta(),
    val augments: List<AugmentRow> = emptyList(),
    /** 라운드("2-1") -> 희귀도 -> 확률. 값이 검증되지 않아 비어 있을 수 있고, 그러면 표를 숨긴다. */
    val rounds: Map<String, Map<String, Double?>?> = emptyMap(),
)

@Serializable
data class AugmentsMeta(
    val editorTier: AugmentEditorTierMeta = AugmentEditorTierMeta(),
    val cnStats: AugmentCnStatsMeta = AugmentCnStatsMeta(),
)

@Serializable
data class AugmentEditorTierMeta(
    val source: String = "",
    val author: String = "",
    val updatedAt: String = "",
    val counts: Map<String, Int> = emptyMap(),
)

@Serializable
data class AugmentCnStatsMeta(
    val source: String = "",
    val bucket: String = "",
    val label: String = "",
    val detailDate: String = "",
    val note: String = "",
)

@Serializable
data class AugmentRow(
    val id: String = "",
    val name: String = "",
    val nameEn: String? = null,
    /** silver / gold / prismatic. */
    val rarity: String? = null,
    val tags: List<String> = emptyList(),
    val icon: String? = null,
    val desc: String = "",
    /** 편집자 한 사람의 의견(S~D). 통계가 아니다. */
    val editorTier: String? = null,
    val deckStats: List<AugmentDeckStat> = emptyList(),
    val summary: AugmentSummary = AugmentSummary(),
    val recommendedBy: AugmentRecommendation = AugmentRecommendation(),
    val decks: List<String> = emptyList(),
)

@Serializable
data class AugmentDeckStat(
    val deck: String = "",
    val deckName: String = "",
    val n: Int = 0,
    val avg: Double? = null,
    /** 그 덱 안에서 이 증강의 순위(상위 5개만 집계된다). */
    val rank: Int? = null,
    /** 첫째·둘째·셋째 증강으로 골랐을 때 평균. 원본 "0"은 null. */
    val stage: List<Double?> = emptyList(),
    /** 원본 값에 소수부가 없으면 표본이 작다는 뜻이라 흐리게 보여 준다. null 원소가 와도 파일을 버리지 않는다. */
    val stageLowSample: List<Boolean?> = emptyList(),
)

@Serializable
data class AugmentSummary(
    val n: Int = 0,
    val avg: Double? = null,
    val decks: Int = 0,
)

@Serializable
data class AugmentRecommendation(
    val editorial: List<String> = emptyList(),
    val guide: List<AugmentGuideRef> = emptyList(),
)

@Serializable
data class AugmentGuideRef(
    val deck: String = "",
    val tier: String? = null,
    val source: String = "",
)

// ---------------------------------------------------------------------------
// 파서
// ---------------------------------------------------------------------------

/**
 * 도감 파일 파서. 저장소와 단위 테스트가 같은 설정을 쓴다.
 *
 * 쓸 수 없는 파일(파싱 실패, 행이 하나도 없음)은 null을 돌려준다.
 * 호출하는 쪽은 null이면 멀쩡한 캐시를 덮어쓰지 않는다.
 */
object StatsParser {

    val json: Json = Json {
        ignoreUnknownKeys = true      // 수집기가 필드를 더해도 앱이 깨지지 않도록
        isLenient = true
        coerceInputValues = true      // null이 기본값 있는 필드에 오면 기본값으로
        allowSpecialFloatingPointValues = true  // 파이썬 json.dumps가 내는 NaN을 받아 준다
    }

    fun parseVersion(text: String): StatsVersion? =
        decode<StatsVersion>(text)?.takeIf { it.files.isNotEmpty() }

    fun parseChampions(text: String): ChampionsFile? =
        decode<ChampionsFile>(text)?.takeIf { it.champions.isNotEmpty() }

    fun parseTraits(text: String): TraitsFile? =
        decode<TraitsFile>(text)?.takeIf { it.traits.isNotEmpty() }

    fun parseItems(text: String): ItemsFile? =
        decode<ItemsFile>(text)?.takeIf { it.items.isNotEmpty() }

    fun parseAugments(text: String): AugmentsFile? =
        decode<AugmentsFile>(text)?.takeIf { it.augments.isNotEmpty() }

    private inline fun <reified T> decode(text: String): T? =
        if (text.isBlank()) null else runCatching { json.decodeFromString<T>(text) }.getOrNull()
}

// ---------------------------------------------------------------------------
// 관대한 정수
// ---------------------------------------------------------------------------

/**
 * 정수 자리에 1526455.0, "1526455", NaN이 와도 읽는다.
 *
 * kotlinx 기본 Int 파서는 "20.0"에서 예외를 던지고, 그러면 파일 전체가 버려진다.
 * 이 파일의 모든 Int/Long 필드에 @file:UseSerializers로 적용된다.
 */
object FlexIntSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.tftdeck.reader.data.FlexInt", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): Int {
        if (decoder !is JsonDecoder) return decoder.decodeInt()
        val value = flexLong(decoder, "정수") ?: return 0
        return value.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
    }

    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
}

object FlexLongSerializer : KSerializer<Long> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.tftdeck.reader.data.FlexLong", PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): Long {
        if (decoder !is JsonDecoder) return decoder.decodeLong()
        return flexLong(decoder, "정수") ?: 0L
    }

    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeLong(value)
}

/** 숫자로 읽을 수 없는 값(객체·배열·빈 문자열)만 예외. NaN·null은 0으로 본다. */
private fun flexLong(decoder: JsonDecoder, what: String): Long? {
    val element = decoder.decodeJsonElement()
    if (element is JsonNull) return null
    val primitive = element as? JsonPrimitive
        ?: throw SerializationException("$what 자리에 숫자가 아닌 값: $element")
    val content = primitive.content.trim()
    content.toLongOrNull()?.let { return it }
    val number = content.toDoubleOrNull()
        ?: throw SerializationException("$what 자리에 숫자가 아닌 값: $content")
    return if (number.isFinite()) number.roundToLong() else null
}
