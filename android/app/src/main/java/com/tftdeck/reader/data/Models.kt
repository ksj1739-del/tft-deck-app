package com.tftdeck.reader.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 수집기가 만드는 decks.json 구조를 그대로 받는다.
 *
 * 원본 스키마가 흔들려도 앱이 죽지 않도록 모든 필드에 기본값을 둔다.
 * (Json 파서는 ignoreUnknownKeys = true 로 설정한다.)
 */
@Serializable
data class DeckFeed(
    val version: FeedVersion = FeedVersion(),
    val decks: List<Deck> = emptyList(),
    val index: SearchIndex = SearchIndex(),
    val catalog: Catalog = Catalog(),
)

@Serializable
data class FeedVersion(
    val generatedAt: String = "",
    val set: String = "",
    val setNumber: Int = 0,
    val patch: String = "",
    val deckCount: Int = 0,
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

// ---------------------------------------------------------------------------
// 덱
// ---------------------------------------------------------------------------

@Serializable
data class Deck(
    val id: String = "",
    val name: String = "",
    val nameCn: String = "",
    val tier: String = "",
    val tierOrder: Int = 9,
    val patch: String = "",
    val finalLevel: Int? = null,
    val carryId: String? = null,
    val traits: List<TraitRef> = emptyList(),
    val units: List<Unit> = emptyList(),
    /** 레벨("6"/"8"/"9") -> 그 레벨의 배치. 이름/아이콘은 catalog에서 찾는다. */
    val boards: Map<String, List<Placement>> = emptyMap(),
    val early: List<Placement> = emptyList(),
    val mid: List<Placement> = emptyList(),
    val itemOrder: List<ItemRef> = emptyList(),
    val augments: Augments = Augments(),
    val notesCn: Notes = Notes(),
    val author: String = "",
    val updatedAt: String = "",
    val teamCode: TeamCode? = null,
    val metatft: MetaComparison = MetaComparison(),
) {
    val carry: Unit? get() = units.firstOrNull { it.carry } ?: units.firstOrNull { it.id == carryId }

    /** 아이템을 드는 유닛만. 덱 카드에 요약으로 보여준다. */
    val carriers: List<Unit> get() = units.filter { it.items.isNotEmpty() }

    /** 선택 가능한 레벨 탭. 배치 데이터가 있는 것만. */
    val boardLevels: List<String> get() = boards.keys.sortedBy { it.toIntOrNull() ?: 0 }
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
    val row: Int? = null,
    val col: Int? = null,
    val items: List<ItemRef> = emptyList(),
    val itemsBackup: List<ItemRef> = emptyList(),
)

/** 레벨별 배치. 표시 정보는 catalog에서 id로 찾는다. */
@Serializable
data class Placement(
    val id: String = "",
    val star: Int = 1,
    val row: Int? = null,
    val col: Int? = null,
    val carry: Boolean = false,
    val items: List<String> = emptyList(),
)

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
) {
    val isEmpty: Boolean get() = items.isBlank() && augments.isBlank()
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
)

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
)

/** 아이템 역검색 결과 한 줄: 어느 덱의 어느 챔피언이 그 아이템을 드는가. */
data class ItemHit(
    val deck: Deck,
    @SerialName("unit") val unitName: String,
    val isCore: Boolean,
)
