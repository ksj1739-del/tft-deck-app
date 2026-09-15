package com.tftdeck.reader.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 수집기가 만드는 decks.json 구조를 그대로 받는다.
 *
 * 원본 스키마가 흔들려도 앱이 죽지 않도록 모든 필드에 기본값을 둔다.
 * v2(통합 덱)에서 생긴 필드도 전부 비어 있는 기본값이라, 옛 v1 캐시나 동봉 스냅샷을 읽어도
 * 파싱이 깨지지 않는다. (Json 파서는 [FeedJson] 한 곳에서 설정한다.)
 */
@Serializable
data class DeckFeed(
    val version: FeedVersion = FeedVersion(),
    /** 티어 구간(all/master/diamond/goldem/low)별 날짜·규모. v1 파일에는 없다. */
    val buckets: Map<String, BucketMeta> = emptyMap(),
    /** metatft 비교 스코프(glob_plat/kr_plat/kr_master)의 표본 규모. */
    val scopes: Map<String, ScopeMeta> = emptyMap(),
    val gradeCuts: GradeCuts = GradeCuts(),
    val decks: List<Deck> = emptyList(),
    val index: SearchIndex = SearchIndex(),
    val catalog: Catalog = Catalog(),
) {
    /** 수집기가 기본으로 표시한 구간. 표시가 없으면 골드~에메랄드. */
    val defaultBucket: String
        get() = buckets.entries.firstOrNull { it.value.default }?.key ?: DeckKeys.DEFAULT_BUCKET
}

/** 저장소와 단위 테스트가 같은 규칙으로 읽도록 파서 설정을 한 곳에 둔다. */
object FeedJson {
    val json = Json {
        ignoreUnknownKeys = true   // 수집기가 필드를 더해도 앱이 깨지지 않도록
        isLenient = true
        coerceInputValues = true   // 기본값이 있는 필드에 null 이 와도 기본값으로 받는다
    }

    fun decodeFeed(text: String): DeckFeed = json.decodeFromString(DeckFeed.serializer(), text)

    fun decodeVersion(text: String): FeedVersion = json.decodeFromString(FeedVersion.serializer(), text)
}

/**
 * 두 피드 버전 중 어느 쪽이 옛 데이터인지. 저장소가 기기 캐시·동봉 스냅샷·원격 중 무엇을 쓸지 고를 때 쓴다.
 *
 * schemaVersion 이 낮으면 옛 것으로 본다 — 원격이 옛 수집기(v1) 결과로 돌아가도 v2 화면을 지키기 위해서다.
 * 같으면 generatedAt(수집기가 UTC ISO-8601 로 쓴다)을 비교하고, 한쪽이라도 없거나 읽을 수 없으면 옛 것이라
 * 단정하지 않는다(모르는 값 때문에 새 데이터를 버리지 않도록).
 */
object FeedFreshness {

    /** [candidate] 가 [reference] 보다 옛 데이터인가. */
    fun isOlder(candidate: FeedVersion, reference: FeedVersion): Boolean =
        if (candidate.schemaVersion != reference.schemaVersion) {
            candidate.schemaVersion < reference.schemaVersion
        } else {
            isEarlier(candidate.generatedAt, reference.generatedAt)
        }

    /** [a] 가 [b] 보다 이른 시각인가. 둘 중 하나라도 읽을 수 없으면 false. */
    fun isEarlier(a: String, b: String): Boolean {
        val first = epochMillis(a) ?: return false
        val second = epochMillis(b) ?: return false
        return first < second
    }

    private fun epochMillis(text: String): Long? =
        text.takeIf { it.isNotBlank() }?.let { runCatching { java.time.Instant.parse(it.trim()).toEpochMilli() }.getOrNull() }
}

/** 수집기와 약속한 고정 문자열. 여러 화면이 같은 값을 쓰므로 한 곳에 모은다. */
object DeckKeys {
    const val KIND_GROUP = "group"
    const val KIND_EDITORIAL = "editorial"
    const val KIND_PET = "pet"
    const val STAGE_FINAL = "final"

    const val DEFAULT_BUCKET = "goldem"

    /** 구간 칩과 오버레이 순환 순서. 넓은 구간에서 좁은 구간으로 간다. */
    val BUCKET_ORDER = listOf("all", "master", "diamond", "goldem", "low")

    const val SCOPE_GLOBAL_PLAT = "glob_plat"
    const val SCOPE_KR_PLAT = "kr_plat"
    const val SCOPE_KR_MASTER = "kr_master"
    val SCOPE_ORDER = listOf(SCOPE_GLOBAL_PLAT, SCOPE_KR_PLAT, SCOPE_KR_MASTER)

    /** 통계 등급·KR 배지를 믿을 수 있는 최소 표본(수집기 gradeCuts.minSample 과 같은 값). */
    const val MIN_SAMPLE = 300
}

@Serializable
data class FeedVersion(
    /** v1 파일에는 없다. 없으면 1로 본다. 낮은 버전도 그대로 읽는다. */
    val schemaVersion: Int = 1,
    val generatedAt: String = "",
    val set: String = "",
    val setNumber: Int = 0,
    val patch: String = "",
    /** metatft 기준 글로벌 패치(예: 18.2). 중국 패치 번호와 체계가 다르다. */
    val patchGlobal: String = "",
    val qqBuild: String = "",
    val qqPatchStart: String = "",
    /** 胜率阵容 목록 기준일(yyyy-MM-dd). */
    val statDate: String = "",
    val deckCount: Int = 0,
    val editorialCount: Int = 0,
    val onlyInChinaCount: Int = 0,
    val teamCodeCount: Int = 0,
    val metatftSet: String? = null,
    val metatftClusterId: Int? = null,
    val assetBase: String = "https://raw.communitydragon.org/latest/game/",
    val sources: Map<String, String> = emptyMap(),
    val untranslatedIds: List<String> = emptyList(),
    val contentHash: String = "",
) {
    /** 한 곳이라도 ok가 아니면 데이터가 완전하지 않다는 뜻. */
    val hasDegradedSource: Boolean get() = sources.values.any { it != "ok" }

    /** metatft 대조를 못 했으면 '중국 한정' 배지를 신뢰할 수 없다. */
    val metatftCompared: Boolean get() = sources["metatft"] == "ok"
}

@Serializable
data class BucketMeta(
    val label: String = "",
    val qqTierPart: String = "",
    /** 목록 기준일(yyyyMMdd). 당일 집계. */
    val listDate: String = "",
    /** 상세(증강·배치) 기준일(yyyyMMdd). 목록보다 하루 늦다. */
    val detailDate: String = "",
    val groups: Int = 0,
    val variants: Int = 0,
    val default: Boolean = false,
)

/**
 * 비교 스코프의 표본 규모. metatft 스코프(glob_plat/kr_plat/kr_master)는 boards·updatedAt 을,
 * 중국 스코프(cn_plat/cn_master, 数据检索器)는 games·statDate·tier 를 채운다.
 */
@Serializable
data class ScopeMeta(
    val label: String = "",
    val source: String = "",
    val days: Int = 0,
    val boards: Long = 0,
    val updatedAt: String = "",
    /** 중국 스코프: 집계 판 수. */
    val games: Long = 0,
    /** 중국 스코프: 기준일. */
    val statDate: String = "",
    /** 중국 스코프: 数据检索器 티어 조건("4+", "7+"). */
    val tier: String = "",
)

/** 등급 컷. 앱은 계산하지 않고 표시용으로만 들고 있는다. */
@Serializable
data class GradeCuts(
    @SerialName("S") val s: Double = 3.90,
    @SerialName("A") val a: Double = 4.15,
    @SerialName("B") val b: Double = 4.40,
    @SerialName("C") val c: Double = 4.70,
    val minSample: Int = DeckKeys.MIN_SAMPLE,
    val shrinkK: Int = 200,
    /** 보정 평균이 끌려가는 값. 그룹이 충분한 구간은 그 구간 그룹 평균(경험적 베이즈), 적은 구간은 4.5. */
    val shrinkTo: Double = 4.5,
)

// ---------------------------------------------------------------------------
// 덱
// ---------------------------------------------------------------------------

@Serializable
data class Deck(
    val id: String = "",
    /** group(胜率阵容 그룹) 또는 editorial(편집 덱만 있는 독립 덱). v1 파일은 전부 편집 덱이다. */
    val kind: String = DeckKeys.KIND_EDITORIAL,
    val key: String = "",
    val name: String = "",
    val nameCn: String = "",
    val tier: String = "",
    val tierOrder: Int = 9,
    /** 편집 덱의 SS~C 등급. 통계 등급(S~D)과 다른 체계라 따로 둔다. */
    val editorialTier: String? = null,
    /** metatft 전용 덱(kind=global)의 글로벌 등급. lol.qq 통계가 없는 덱의 배지·정렬에만 쓴다. */
    val globalGrade: String? = null,
    val patch: String = "",
    val finalLevel: Int? = null,
    val carryId: String? = null,
    val mainTraits: List<TraitRef> = emptyList(),
    val traits: List<TraitRef> = emptyList(),
    /** 대표 보드. 편집 덱이 붙은 그룹은 좌표가 있고, 통계만 있는 그룹은 row/col 이 비어 있다. */
    val units: List<Unit> = emptyList(),
    val sources: DeckSources = DeckSources(),
    /** 구간 키 -> 그 구간의 수치. 앱은 조회만 한다. */
    val stats: Map<String, DeckStats> = emptyMap(),
    val global: GlobalStats? = null,
    val keyUnits: List<KeyUnit> = emptyList(),
    /** 유닛 id -> 실측 배치 상위 칸. */
    val positions: Map<String, List<CellStat>> = emptyMap(),
    val levelDist: List<LevelShare> = emptyList(),
    val augmentStats: List<AugmentStat> = emptyList(),
    val itemWearers: List<DeckItemWearers> = emptyList(),
    val variants: List<Variant> = emptyList(),
    val editorial: Editorial? = null,
    /**
     * 같은 그룹에 붙은 두 번째 이후 편집 덱(작가가 다르다). 대표(editorial)는 최근 작성분이다.
     * 상세 화면이 작가를 바꿔 보여 준다([withEditorial]). 편집 덱이 하나뿐이면 비어 있다.
     */
    val moreEditorials: List<Editorial> = emptyList(),
    /**
     * 증강 성적·실측 배치·레벨 분포(상세)를 받은 구간. 대부분 기본 구간이지만 그 구간에 없는 덱은
     * 다이아+ 등에서 받는다. 없으면(상세를 못 받은 덱·옛 파일) 기본 구간으로 본다.
     */
    val detailBucket: String? = null,
    /** 레벨별 빌드업(§13). 원천이 하나도 없으면 null 이고 화면은 섹션을 숨긴다. */
    val buildup: Buildup? = null,
    val itemOrder: List<ItemRef> = emptyList(),
    val augments: Augments = Augments(),
    val notesCn: Notes = Notes(),
    val author: String = "",
    val updatedAt: String = "",
    val teamCode: TeamCode? = null,
    val metatft: MetaComparison = MetaComparison(),
) {
    val carry: Unit? get() = units.firstOrNull { it.carry } ?: units.firstOrNull { it.id == carryId }

    /** 아이템을 드는 유닛만. */
    val carriers: List<Unit> get() = units.filter { it.items.isNotEmpty() }

    /**
     * 캐리 3명. 수집기가 준 carryRank(메인C/보조C/2보조C)를 따르고,
     * 순위가 없는 옛 데이터는 캐리 표시 → 아이템 많은 순으로 고른다.
     */
    val carries: List<Unit>
        get() {
            val ranked = units.filter { (it.carryRank ?: 0) in 1..3 }.sortedBy { it.carryRank }
            if (ranked.isNotEmpty()) return ranked
            return units
                .filter { it.kind != DeckKeys.KIND_PET && (it.carry || it.items.isNotEmpty()) }
                .sortedWith(compareByDescending<Unit> { it.carry }.thenByDescending { it.items.size })
                .take(3)
        }

    fun statsFor(bucket: String): DeckStats? = stats[bucket]

    /**
     * 카드·상세의 네 수치에 쓸 값. lol.qq 덱은 그 구간 통계이고,
     * metatft 전용 덱은 lol.qq 구간이 없으므로 [globalDisplayScope] 값을 쓴다.
     */
    fun displayStats(bucket: String): DeckStats? {
        if (!isGlobalOnly) return statsFor(bucket)
        val scope = globalDisplayScope ?: return null
        val stat = global?.stats?.get(scope) ?: return null
        return DeckStats(n = stat.n, avg = stat.avg, top4 = stat.top4, win = stat.win, grade = globalGrade)
    }

    /**
     * metatft 전용 덱의 네 수치 출처. 글로벌 등급을 글로벌 플래+ 평균 등수로 매기므로 그 값을 먼저 쓴다(없으면 KR 플래+).
     * KR 값을 앞세우면 'KR 4.94등인데 글로벌 A' 처럼 수치와 등급이 어긋나 보인다.
     */
    val globalDisplayScope: String?
        get() = global?.stats?.let { stats ->
            listOf(DeckKeys.SCOPE_GLOBAL_PLAT, DeckKeys.SCOPE_KR_PLAT).firstOrNull { it in stats }
        }

    /**
     * 이 구간에 통계는 있지만 표본이 작아 등급이 없는 덱. 카드·상세가 흐리게 하고 '표본 부족'을 붙인다.
     * 그 구간에 기록이 아예 없는 덱은 [appearsIn] 으로 목록에서 빠지므로 여기에 해당하지 않는다.
     */
    fun isLowSample(bucket: String): Boolean = statsFor(bucket)?.let { it.grade == null } ?: false

    /** metatft 에만 있는 덱(lol.qq 통계·편집 덱이 없다). 목록에서 등급 대신 '글로벌' 표시를 단다. */
    val isGlobalOnly: Boolean get() = kind == "global"

    /**
     * 이 구간 목록에 나올 덱인지. 등급이 있는 덱만 보인다 — 그 구간에 기록이 없거나 표본이 작아
     * 등급이 없는 통계 덱은 뺀다(다이아+·마스터+에서 목록 대부분이 표본 부족으로 보이던 문제).
     * 통계가 없는 편집 독립 덱(편집 등급)과 metatft 전용 덱('글로벌' 표시)은 어느 구간에서나 보인다.
     */
    fun listedIn(bucket: String): Boolean = isGlobalOnly || stats.isEmpty() || statsFor(bucket)?.grade != null

    /** 이 덱의 편집 덱 전부: 대표 먼저, 그다음 같은 그룹의 다른 작가. */
    val editorials: List<Editorial> get() = listOfNotNull(editorial) + moreEditorials

    /** 그 구간의 통계 등급. 없으면(표본 부족·편집 덱) 편집 등급으로 대신한다. */
    fun gradeFor(bucket: String): String? =
        statsFor(bucket)?.grade ?: editorialGrade ?: globalGrade?.takeIf { isGlobalOnly }

    /** 배지에 통계 등급이 아니라 편집 등급을 보여 주는 중인지. 모양을 달리해야 두 체계가 섞여 보이지 않는다. */
    fun showsEditorialGrade(bucket: String): Boolean =
        statsFor(bucket)?.grade == null && editorialGrade != null

    /** 편집 등급. v1 파일은 editorialTier 가 없고 tier 자체가 편집 등급이다. */
    val editorialGrade: String?
        get() = editorialTier?.takeIf { it.isNotBlank() }
            ?: tier.takeIf { it.isNotBlank() && stats.isEmpty() }

    /** 편집 덱이 붙어 있는지(그룹에 첨부 또는 편집 독립 덱). */
    val hasEditorial: Boolean
        get() = editorial != null || sources.editorial || (kind == DeckKeys.KIND_EDITORIAL && editorialTier != null)

    /** '편집 덱만' 필터 기준. v1 파일은 모든 덱이 편집 덱이다. */
    val isEditorialDeck: Boolean get() = hasEditorial || kind == DeckKeys.KIND_EDITORIAL

    val isOnlyInChina: Boolean get() = sources.onlyInChina || metatft.onlyInChina

    /** 편집 덱이 현재 중국 패치 이전에 작성됐다. */
    val isEditorialStale: Boolean get() = sources.editorialStale || editorial?.stale == true

    /** KR 표본이 믿을 만큼 있다. */
    val hasKrSample: Boolean
        get() = sources.kr || (global?.stats?.get(DeckKeys.SCOPE_KR_PLAT)?.n ?: 0) >= DeckKeys.MIN_SAMPLE

    /** 대표 보드를 단계 배치 모양으로. pet 표시는 kind 로 이어진다. */
    val finalPlacements: List<Placement>
        get() = units.map { u ->
            Placement(u.id, u.star, u.row, u.col, u.carry, u.items.map { it.id }, u.kind)
        }

    /** 편집 덱 단계(초반/중반/최종). 편집 덱이 없으면 대표 보드 하나짜리 목록. */
    val stages: List<Stage>
        get() = editorial?.stages?.takeIf { it.isNotEmpty() }
            ?: listOf(Stage(key = DeckKeys.STAGE_FINAL, label = "최종", level = finalLevel, units = finalPlacements))

    val representativeVariant: Variant? get() = variants.firstOrNull { it.representative }

    /** 대표가 아닌 변형. 상세의 변형 섹션에 나온다. */
    val otherVariants: List<Variant> get() = variants.filterNot { it.representative }

    /** 작가 추천 증강. 편집 덱이 붙었으면 그쪽을, 아니면 v1 필드를 쓴다. */
    val authorAugments: Augments
        get() = editorial?.augments?.takeIf { it.recommended.isNotEmpty() || it.alternatives.isNotEmpty() }
            ?: augments

    /** 조합 재료 우선순위. 편집 덱이 붙었으면 그쪽을 쓴다. */
    val componentOrder: List<ItemRef>
        get() = editorial?.itemOrder?.takeIf { it.isNotEmpty() } ?: itemOrder

    /** 작성자 운영 메모(초반·레벨업). 편집 덱 쪽이 비어 있으면 최상위 필드를 본다. */
    val buildupNotes: Notes
        get() = editorial?.notesCn?.takeIf { it.hasBuildupNotes } ?: notesCn
}

@Serializable
data class Unit(
    val id: String = "",
    val name: String = "",
    val nameEn: String? = null,
    val cost: Int? = null,
    val icon: String? = null,
    val star: Int = 1,
    val carry: Boolean = false,
    /** 1=메인C, 2=보조C, 3=2보조C. 없으면 순위 정보가 없는 옛 데이터. */
    val carryRank: Int? = null,
    /** "pet"이면 소환물. 덱 코드·검색에서 빠지고 칸 모양이 다르다. */
    val kind: String? = null,
    val row: Int? = null,
    val col: Int? = null,
    val items: List<ItemRef> = emptyList(),
    val itemsBackup: List<ItemRef> = emptyList(),
) {
    val isPet: Boolean get() = kind == DeckKeys.KIND_PET
}

/** 단계별 배치. 표시 정보는 catalog에서 id로 찾는다. */
@Serializable
data class Placement(
    val id: String = "",
    val star: Int = 1,
    val row: Int? = null,
    val col: Int? = null,
    val carry: Boolean = false,
    val items: List<String> = emptyList(),
    val kind: String? = null,
) {
    val isPet: Boolean get() = kind == DeckKeys.KIND_PET
}

@Serializable
data class ItemRef(
    val id: String = "",
    val name: String = "",
    val icon: String? = null,
)

@Serializable
data class TraitRef(
    val id: String = "",
    val name: String = "",
    val icon: String? = null,
    val count: Int = 0,
    /** 시너지 등급. 1=브론즈, 2=실버, 3=골드, 4=프리즘. */
    val style: Int = 0,
)

@Serializable
data class Augments(
    val recommended: List<ItemRef> = emptyList(),
    val alternatives: List<ItemRef> = emptyList(),
)

/** 작성자가 쓴 중국어 자유 서술. ID 사전으로 번역되지 않아 원문 그대로 둔다. */
@Serializable
data class Notes(
    val items: String = "",
    val augments: String = "",
    /** 초반 운영 팁(early_info). */
    val early: String? = null,
    /** 레벨업·리롤 시점(d_time). */
    val levelUp: String? = null,
) {
    val isEmpty: Boolean get() = items.isBlank() && augments.isBlank()

    val hasBuildupNotes: Boolean get() = !early.isNullOrBlank() || !levelUp.isNullOrBlank()
}

@Serializable
data class TeamCode(
    val code: String = "",
    val units: Int = 0,
    /** 소환수처럼 상점에 없어 코드로 표현할 수 없는 유닛. */
    val omitted: List<String>? = null,
    /** 10칸을 넘겨 잘린 유닛 수. */
    val truncated: Int? = null,
) {
    val isPartial: Boolean get() = !omitted.isNullOrEmpty() || (truncated ?: 0) > 0
}

@Serializable
data class MetaComparison(
    val similarity: Double = 0.0,
    val matchedComp: String? = null,
    val onlyInChina: Boolean = false,
    val compared: Boolean = false,
)

// ---------------------------------------------------------------------------
// 통합 덱(v2) 수치
// ---------------------------------------------------------------------------

@Serializable
data class DeckSources(
    val editorial: Boolean = false,
    val editorialStale: Boolean = false,
    val cnStats: Boolean = false,
    val global: Boolean = false,
    val kr: Boolean = false,
    val onlyInChina: Boolean = false,
)

/** 한 구간의 수치. 등급·보정 평균·추세는 수집기가 계산해 넣는다. */
@Serializable
data class DeckStats(
    val n: Int = 0,
    val avg: Double? = null,
    val adjAvg: Double? = null,
    val top4: Double? = null,
    val win: Double? = null,
    val pick: Double? = null,
    val avgDiff: Double? = null,
    val pickDiff: Double? = null,
    /** S~D. 표본이 모자라면 null. */
    val grade: String? = null,
    /** up / down / flat. */
    val trend: String = "flat",
    val precise: PreciseStats? = null,
)

/** 数据检索器(카운트 기반) 참고치. 등급 계산에는 쓰지 않는다. */
@Serializable
data class PreciseStats(
    val scope: String = "",
    val n: Int = 0,
    val avg: Double? = null,
    val top4: Double? = null,
    val win: Double? = null,
    val finalLevel: Int? = null,
)

@Serializable
data class GlobalStats(
    val cluster: Long? = null,
    val similarity: Double = 0.0,
    val name: String = "",
    val levelling: String? = null,
    val levellingRaw: String? = null,
    val difficulty: String? = null,
    val difficultyRaw: Double? = null,
    val stats: Map<String, ScopeStat> = emptyMap(),
    val finalLevels: List<LevelShare> = emptyList(),
    val counters: List<CounterDeck> = emptyList(),
)

@Serializable
data class ScopeStat(
    val n: Int = 0,
    val avg: Double? = null,
    val top4: Double? = null,
    val win: Double? = null,
    /** 1등~8등 보드 수. */
    val places: List<Int> = emptyList(),
)

@Serializable
data class LevelShare(
    val level: Int = 0,
    val share: Double = 0.0,
    val avg: Double? = null,
)

/** 불리한 상대. 우리 목록에 대응 덱이 있으면 deck 에 id 가 온다. */
@Serializable
data class CounterDeck(
    val cluster: Long? = null,
    val deck: String? = null,
    /** 대응 덱이 없을 때 보여 줄 metatft 클러스터 이름. */
    val name: String? = null,
    val placeChange: Double? = null,
)

@Serializable
data class KeyUnit(
    val id: String = "",
    val star1: Double? = null,
    val star2: Double? = null,
    val star3: Double? = null,
    /** 평균 아이템 수. */
    val items: Double? = null,
    val avg: Double? = null,
)

@Serializable
data class CellStat(
    val row: Int = 0,
    val col: Int = 0,
    val use: Double = 0.0,
    val win: Double? = null,
)

@Serializable
data class AugmentStat(
    val id: String = "",
    val name: String = "",
    val icon: String? = null,
    val rank: Int = 0,
    val n: Int = 0,
    val avg: Double? = null,
    /** 2-1 / 3-2 / 4-2 에서 골랐을 때 평균 등수. 원본이 비면 null. */
    val stage: List<Double?> = emptyList(),
    val stageLowSample: List<Boolean> = emptyList(),
)

@Serializable
data class DeckItemWearers(
    val item: ItemRef = ItemRef(),
    val wearers: List<DeckWearer> = emptyList(),
)

@Serializable
data class DeckWearer(
    val id: String = "",
    val name: String = "",
    val n: Int = 0,
    val avg: Double? = null,
)

@Serializable
data class Variant(
    val id: String = "",
    val representative: Boolean = false,
    val editorialId: String? = null,
    val units: List<VariantUnit> = emptyList(),
    val carryId: String? = null,
    val assistIds: List<String> = emptyList(),
    val stats: Map<String, DeckStats> = emptyMap(),
)

@Serializable
data class VariantUnit(
    val id: String = "",
    /**
     * 성급. 胜率阵容 조합 원본에는 유닛별 성급이 없어 수집기가 싣지 않는다(null = 모름).
     * 화면은 변형에 별을 그리지 않는다.
     */
    val star: Int? = null,
    val items: List<String> = emptyList(),
)

@Serializable
data class Editorial(
    val id: String = "",
    /** 작성자가 붙인 중국어 덱 이름. 작가를 바꿔 볼 때 원문 섹션에 쓴다. 옛 파일에는 없다. */
    val nameCn: String = "",
    val author: String = "",
    val updatedAt: String = "",
    val stale: Boolean = false,
    val quality: String? = null,
    val needLevel: Int? = null,
    val stages: List<Stage> = emptyList(),
    val itemOrder: List<ItemRef> = emptyList(),
    val augments: Augments = Augments(),
    val notesCn: Notes = Notes(),
    val teamCode: TeamCode? = null,
)

/** 편집 덱의 한 단계(early/mid/final). 레벨·라운드는 작성자가 적은 값이다. */
@Serializable
data class Stage(
    val key: String = "",
    val label: String = "",
    val level: Int? = null,
    val round: String? = null,
    val units: List<Placement> = emptyList(),
)

// ---------------------------------------------------------------------------
// 빌드업(§13.2) — 필드 이름은 계약 JSON 을 글자 그대로 따른다
// ---------------------------------------------------------------------------

@Serializable
data class Buildup(
    val global: BuildupSource? = null,
    val cn: BuildupSource? = null,
)

@Serializable
data class BuildupSource(
    /** 글로벌: metatft 스코프 키. */
    val scope: String? = null,
    /** 중국: 구간 키. */
    val bucket: String? = null,
    val cluster: Long? = null,
    /** 판당 리롤이 가장 많은 레벨. */
    val rollLevel: Int? = null,
    val levels: List<BuildupLevel> = emptyList(),
)

@Serializable
data class BuildupLevel(
    val level: Int = 0,
    /** 그 레벨에 가장 흔히 도달하는 라운드("4-2"). */
    val reachRound: String? = null,
    val reachShare: Double? = null,
    val rollsPerGame: Double? = null,
    val options: List<BuildupOption> = emptyList(),
)

@Serializable
data class BuildupOption(
    /** 코스트 오름차순 → 이름순으로 정렬된 유닛 id. */
    val units: List<String> = emptyList(),
    val n: Int = 0,
    val avg: Double? = null,
    val top4: Double? = null,
    val win: Double? = null,
    val carryId: String? = null,
    val traits: List<TraitRef> = emptyList(),
)

/** 빌드업 행의 출처. 화면은 이 순서로 보여 주고, 1순위도 이 순서로 고른다. */
enum class BuildupOrigin(val label: String) {
    GLOBAL("글로벌"),
    CN("중국"),
    AUTHOR("작가"),
}

/** 한 레벨에서 보여 줄 구성 한 줄. 통계 출처면 option, 작가 출처면 stage 가 채워진다. */
data class BuildupPick(
    val origin: BuildupOrigin,
    val level: Int,
    val units: List<String>,
    val option: BuildupOption? = null,
    val stage: Stage? = null,
)

/**
 * 빌드업 섹션과 오버레이 레벨 칩이 함께 쓰는 규칙.
 * Compose 밖의 순수 함수라 단위 테스트로 검사한다.
 */
object BuildupPlanner {

    /** 이 표본보다 작은 옵션만 있는 레벨은 흐리게 보여 준다. */
    const val LOW_SAMPLE_N = DeckKeys.MIN_SAMPLE

    /** 한 출처에서 레벨마다 보여 줄 최대 행 수. */
    const val MAX_ROWS = 3

    /** 레벨 칩 목록: 통계 옵션이 하나라도 있는 레벨과 편집 단계 레벨의 합집합, 오름차순. */
    fun levels(deck: Deck): List<Int> {
        val out = sortedSetOf<Int>()
        deck.buildup?.global?.levels.orEmpty().filter { it.options.isNotEmpty() }.forEach { out += it.level }
        deck.buildup?.cn?.levels.orEmpty().filter { it.options.isNotEmpty() }.forEach { out += it.level }
        deck.editorial?.stages.orEmpty().filter { it.units.isNotEmpty() }.mapNotNull { it.level }.forEach { out += it }
        return out.filter { it in 1..11 }
    }

    /** 기본 선택: 편집 덱 최종 레벨 → 주 리롤 레벨 → 가장 큰 레벨. 칩에 없는 레벨은 건너뛴다. */
    fun defaultLevel(deck: Deck, levels: List<Int> = levels(deck)): Int? {
        if (levels.isEmpty()) return null
        editorialFinalLevel(deck)?.takeIf { it in levels }?.let { return it }
        rollLevel(deck)?.takeIf { it in levels }?.let { return it }
        return levels.last()
    }

    fun editorialFinalLevel(deck: Deck): Int? {
        val editorial = deck.editorial ?: return null
        return editorial.stages.lastOrNull { it.key == DeckKeys.STAGE_FINAL }?.level
            ?: editorial.needLevel
            ?: deck.finalLevel
    }

    /** 주 리롤 레벨. 글로벌 값을 먼저 본다. */
    fun rollLevel(deck: Deck): Int? = deck.buildup?.global?.rollLevel ?: deck.buildup?.cn?.rollLevel

    /** 주 리롤 레벨의 판당 리롤 수. */
    fun rollsAtRollLevel(deck: Deck): Double? {
        val level = rollLevel(deck) ?: return null
        return (deck.buildup?.global?.levels.orEmpty() + deck.buildup?.cn?.levels.orEmpty())
            .firstOrNull { it.level == level && it.rollsPerGame != null }?.rollsPerGame
    }

    fun globalPicks(deck: Deck, level: Int): List<BuildupPick> =
        statPicks(deck.buildup?.global, BuildupOrigin.GLOBAL, level)

    fun cnPicks(deck: Deck, level: Int): List<BuildupPick> =
        statPicks(deck.buildup?.cn, BuildupOrigin.CN, level)

    /** 선택 레벨과 레벨이 같은 편집 단계. 중반과 최종이 같은 레벨이면 둘 다 나온다. */
    fun authorPicks(deck: Deck, level: Int): List<BuildupPick> =
        deck.editorial?.stages.orEmpty()
            .filter { it.level == level && it.units.isNotEmpty() }
            .map { stage -> BuildupPick(BuildupOrigin.AUTHOR, level, stage.units.map { it.id }, stage = stage) }

    /** 선택 레벨의 모든 행: 글로벌 → 중국 → 작가. */
    fun picks(deck: Deck, level: Int): List<BuildupPick> =
        globalPicks(deck, level) + cnPicks(deck, level) + authorPicks(deck, level)

    /**
     * 오버레이 얼굴 줄에 쓸 1순위 구성(글로벌 → 중국 → 작가).
     * 작가 단계가 여럿이면 뒤 단계(최종에 가까운 쪽)가 그 레벨의 완성형이라 그것을 고른다.
     */
    fun topPick(deck: Deck, level: Int): BuildupPick? =
        globalPicks(deck, level).firstOrNull()
            ?: cnPicks(deck, level).firstOrNull()
            ?: authorPicks(deck, level).lastOrNull()

    /**
     * 같은 출처의 바로 아래 단계 1순위와 비교해 새로 들어온 유닛.
     * 비교할 아래 단계가 없으면 빈 집합(첫 단계에 전부 '+'를 찍으면 의미가 없다).
     */
    fun newUnits(deck: Deck, pick: BuildupPick): Set<String> {
        val previous: List<String> = when (pick.origin) {
            BuildupOrigin.GLOBAL -> previousTop(deck.buildup?.global, pick.level)
            BuildupOrigin.CN -> previousTop(deck.buildup?.cn, pick.level)
            BuildupOrigin.AUTHOR -> {
                val stages = deck.editorial?.stages.orEmpty()
                val index = stages.indexOf(pick.stage)
                if (index > 0) stages[index - 1].units.map { it.id } else null
            }
        } ?: return emptySet()
        return pick.units.toSet() - previous.toSet()
    }

    /** 타이밍 줄: 도달 라운드가 있는 레벨만 (레벨, "4-2"). */
    fun timings(deck: Deck): List<Pair<Int, String>> =
        deck.buildup?.global?.levels.orEmpty()
            .mapNotNull { l -> l.reachRound?.takeIf { it.isNotBlank() }?.let { l.level to it } }
            .sortedBy { it.first }

    /** 오버레이 칩 아래 작은 글자: "8렙 4-2 · 주 리롤 9렙". */
    fun caption(deck: Deck, level: Int): String {
        val round = deck.buildup?.global?.levels.orEmpty()
            .firstOrNull { it.level == level }?.reachRound?.takeIf { it.isNotBlank() }
            ?: deck.editorial?.stages.orEmpty()
                .firstOrNull { it.level == level && !it.round.isNullOrBlank() }?.round
        val head = if (round != null) "${level}렙 $round" else "${level}렙"
        val roll = rollLevel(deck) ?: return head
        return "$head · 주 리롤 ${roll}렙"
    }

    /** 그 레벨의 통계 옵션이 전부 표본 부족인지. 작가 행이 있는 레벨은 흐리게 하지 않는다. */
    fun isLowSample(deck: Deck, level: Int): Boolean {
        val options = listOfNotNull(
            deck.buildup?.global?.levels?.firstOrNull { it.level == level },
            deck.buildup?.cn?.levels?.firstOrNull { it.level == level },
        ).flatMap { it.options }
        return options.isNotEmpty() && options.all { it.n < LOW_SAMPLE_N } && authorPicks(deck, level).isEmpty()
    }

    /** 섹션을 그릴 데이터가 있는지. */
    fun hasData(deck: Deck): Boolean =
        levels(deck).isNotEmpty() || timings(deck).isNotEmpty() || deck.buildupNotes.hasBuildupNotes

    private fun statPicks(source: BuildupSource?, origin: BuildupOrigin, level: Int): List<BuildupPick> =
        source?.levels.orEmpty()
            .firstOrNull { it.level == level }
            ?.options.orEmpty()
            .filter { it.units.isNotEmpty() }
            .take(MAX_ROWS)
            .map { BuildupPick(origin, level, it.units, option = it) }

    private fun previousTop(source: BuildupSource?, level: Int): List<String>? =
        source?.levels.orEmpty()
            .filter { it.level < level && it.options.isNotEmpty() }
            .maxByOrNull { it.level }
            ?.options?.firstOrNull()?.units
}

// ---------------------------------------------------------------------------
// 검색
// ---------------------------------------------------------------------------

@Serializable
data class SearchIndex(
    /** 아이템 이름 -> 그 아이템을 쓰는 덱/챔피언. 이 앱의 핵심 기능. */
    val item: Map<String, List<ItemUsage>> = emptyMap(),
    /** 기본 아이템(조합 재료) 이름 -> 덱 id. */
    val component: Map<String, List<String>> = emptyMap(),
    val champion: Map<String, List<String>> = emptyMap(),
    val trait: Map<String, List<String>> = emptyMap(),
    val augment: Map<String, List<String>> = emptyMap(),
    /** DA id -> 덱 id. 이름이 바뀌어도 도감에서 덱으로 정확히 이어진다. v1에는 없다. */
    val byId: IdIndex = IdIndex(),
)

@Serializable
data class IdIndex(
    val champion: Map<String, List<String>> = emptyMap(),
    val item: Map<String, List<String>> = emptyMap(),
    val trait: Map<String, List<String>> = emptyMap(),
    val augment: Map<String, List<String>> = emptyMap(),
)

@Serializable
data class ItemUsage(
    val deck: String = "",
    val unit: String = "",
    /** "main" = 핵심, "backup" = 대체. */
    val role: String = "main",
) {
    val isCore: Boolean get() = role == "main"
}

@Serializable
data class Catalog(
    val champions: List<CatalogEntry> = emptyList(),
    val traits: List<CatalogEntry> = emptyList(),
    val items: List<CatalogEntry> = emptyList(),
    val augments: List<CatalogEntry> = emptyList(),
    /** 상점에 없는 소환물. 보드·빌드업 칸 이름과 아이콘을 여기서 찾는다. */
    val pets: List<CatalogEntry> = emptyList(),
)

@Serializable
data class CatalogEntry(
    val id: String = "",
    val name: String = "",
    val nameEn: String? = null,
    val icon: String? = null,
    val cost: Int? = null,
    /** 완성 아이템의 조합 재료 id. */
    val components: List<String> = emptyList(),
    /** "pet" 등. 챔피언은 비어 있다. */
    val kind: String? = null,
    /** 증강 설명. 계약에는 없지만 들어오면 검색에 쓴다. */
    val desc: String? = null,
)

/** id로 catalog 를 찾는 지도. 피드마다 한 번 만들어 선형 탐색을 없앤다. */
class CatalogIndex(catalog: Catalog) {
    val champions: Map<String, CatalogEntry> = catalog.champions.associateBy { it.id }
    val items: Map<String, CatalogEntry> = catalog.items.associateBy { it.id }
    val traits: Map<String, CatalogEntry> = catalog.traits.associateBy { it.id }
    val augments: Map<String, CatalogEntry> = catalog.augments.associateBy { it.id }
    val pets: Map<String, CatalogEntry> = catalog.pets.associateBy { it.id }

    /** 보드·빌드업 칸의 유닛. 챔피언에 없으면 소환물에서 찾는다. */
    fun unit(id: String): CatalogEntry? = champions[id] ?: pets[id]

    fun isPet(id: String): Boolean = id !in champions && id in pets
}

/**
 * 같은 그룹의 다른 작가 편집 덱으로 바꿔 본 덱. 통계·글로벌 비교·변형·빌드업 통계는 그룹 것 그대로 두고,
 * 작가에 딸린 것(최종 보드·편집 등급·덱 코드·조합 재료·추천 증강·원문·작가)만 바꾼다.
 * 편집 덱의 단계 보드는 id 참조라 catalog 로 이름·아이콘·아이템을 푼다. 대표 편집 덱이면 그대로 돌려준다.
 */
fun Deck.withEditorial(chosen: Editorial, catalog: CatalogIndex): Deck {
    if (chosen.id == editorial?.id) return this
    val final = chosen.stages.lastOrNull { it.key == DeckKeys.STAGE_FINAL } ?: chosen.stages.lastOrNull()
    val board = final?.units.orEmpty().map { placement ->
        val entry = catalog.unit(placement.id)
        Unit(
            id = placement.id,
            name = entry?.name ?: placement.id,
            nameEn = entry?.nameEn,
            cost = entry?.cost,
            icon = entry?.icon,
            star = placement.star,
            carry = placement.carry,
            kind = placement.kind ?: if (catalog.isPet(placement.id)) DeckKeys.KIND_PET else null,
            row = placement.row,
            col = placement.col,
            items = placement.items.map { id ->
                catalog.items[id]?.let { ItemRef(it.id, it.name, it.icon) } ?: ItemRef(id, id, null)
            },
        )
    }
    return copy(
        nameCn = chosen.nameCn.ifBlank { nameCn },
        editorialTier = chosen.quality?.takeIf { it.isNotBlank() } ?: editorialTier,
        finalLevel = chosen.needLevel ?: finalLevel,
        carryId = board.firstOrNull { it.carry }?.id ?: carryId,
        units = board.ifEmpty { units },
        editorial = chosen,
        // '이전 패치 작성' 배지는 고른 작가의 작성 시점을 따른다(대표 작가가 옛 패치여도 이 작가는 새로 썼을 수 있다).
        sources = sources.copy(editorialStale = chosen.stale),
        itemOrder = chosen.itemOrder,
        augments = chosen.augments,
        notesCn = chosen.notesCn,
        author = chosen.author,
        updatedAt = chosen.updatedAt,
        teamCode = chosen.teamCode ?: teamCode,
    )
}

/**
 * 증강 설명을 도감 통계(stats/augments.json)에서 채운 피드. 검색 엔진이 설명문으로도 증강을 찾게 한다(§6.5).
 * decks.json catalog 에는 설명이 없어서 앱이 합친다. 이미 설명이 있는 항목은 그대로 둔다.
 */
fun DeckFeed.withAugmentDescriptions(descriptions: Map<String, String>): DeckFeed {
    if (descriptions.isEmpty()) return this
    var changed = false
    val augments = catalog.augments.map { entry ->
        val desc = descriptions[entry.id]?.let(::plainDescription)
        if (entry.desc.isNullOrBlank() && !desc.isNullOrBlank()) {
            changed = true
            entry.copy(desc = desc)
        } else {
            entry
        }
    }
    return if (changed) copy(catalog = catalog.copy(augments = augments)) else this
}

/** 설명의 서식 태그(<br> 등)를 공백으로 바꾼다. 태그 이름이 검색 단어로 섞이지 않게. */
private fun plainDescription(text: String): String = text.replace(MARKUP, " ").trim()

private val MARKUP = Regex("<[^>]*>")

// ---------------------------------------------------------------------------
// 검색 결과
// ---------------------------------------------------------------------------

enum class SearchAxis(val label: String) {
    CHAMPION("챔피언"),
    ITEM("아이템"),
    COMPONENT("조합 재료"),
    TRAIT("시너지"),
    AUGMENT("증강체"),
}

data class Suggestion(
    val axis: SearchAxis,
    val name: String,
    val icon: String?,
    val cost: Int?,
    val deckCount: Int,
    /** catalog id. 도감 상세로 넘어갈 때 쓴다. 이름만 있고 catalog 에 없으면 null. */
    val id: String? = null,
)

/** 아이템 역검색 결과 한 줄: 어느 덱의 어느 챔피언이 그 아이템을 드는가. */
data class ItemHit(
    val deck: Deck,
    @SerialName("unit") val unitName: String,
    val isCore: Boolean,
)
