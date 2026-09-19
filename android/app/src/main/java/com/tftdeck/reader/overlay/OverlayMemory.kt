package com.tftdeck.reader.overlay

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * 오버레이 펼침 화면에서 '보던 자리' — 고른 덱, 덱마다 고른 레벨, 덱 목록 스크롤 위치.
 *
 * 펼침 화면은 접으면 컴포지션에서 통째로 빠지고(접힌 칩만 남는다), 창은 자동 표시·닫기로 붙었다 떨어지며,
 * 서비스는 시스템이 되살리기도 한다. 화면 쪽 remember·rememberSaveable 로는 그중 어느 것도 넘기지 못하므로
 * 이 값들은 서비스가 들고([OverlayService]) 화면에 흘려 보낸다. 바뀔 때마다 저장해 서비스가 새로 만들어져도 이어진다.
 *
 * 검색 조건(칩)은 여기 두지 않는다 — 창을 닫으면 조건도 비우는 것이 기존 동작이다.
 */
internal class OverlayMemory(private val prefs: SharedPreferences) {

    private val _selectedDeckId = MutableStateFlow(prefs.getString(KEY_SELECTED, null))

    /** 펼침 화면에서 보고 있는 덱. null 이면 목록. */
    val selectedDeckId: StateFlow<String?> = _selectedDeckId.asStateFlow()

    private val _deckLevels = MutableStateFlow(decodeDeckLevels(prefs.getString(KEY_LEVELS, null)))

    /** 덱 id → 레벨 칩에서 고른 레벨. 고른 적 없는 덱은 없다(기본 레벨 규칙을 쓴다). */
    val deckLevels: StateFlow<Map<String, Int>> = _deckLevels.asStateFlow()

    private val _listAnchor = MutableStateFlow(
        prefs.getString(KEY_LIST_KEY, null)?.let { OverlayListAnchor(it, prefs.getInt(KEY_LIST_OFFSET, 0)) }
    )

    /** 덱 목록 맨 위에 보이던 덱과 그 덱이 위로 밀린 정도. null 이면 맨 위부터. */
    val listAnchor: StateFlow<OverlayListAnchor?> = _listAnchor.asStateFlow()

    fun selectDeck(id: String?) {
        if (_selectedDeckId.value == id) return
        _selectedDeckId.value = id
        prefs.edit().apply { if (id == null) remove(KEY_SELECTED) else putString(KEY_SELECTED, id) }.apply()
    }

    fun setLevel(deckId: String, level: Int) {
        val next = rememberDeckLevel(_deckLevels.value, deckId, level)
        if (next == _deckLevels.value) return
        _deckLevels.value = next
        prefs.edit().putString(KEY_LEVELS, encodeDeckLevels(next)).apply()
    }

    fun setListAnchor(anchor: OverlayListAnchor?) {
        if (_listAnchor.value == anchor) return
        _listAnchor.value = anchor
        prefs.edit().apply {
            if (anchor == null) {
                remove(KEY_LIST_KEY)
                remove(KEY_LIST_OFFSET)
            } else {
                putString(KEY_LIST_KEY, anchor.key)
                putInt(KEY_LIST_OFFSET, anchor.offset)
            }
        }.apply()
    }

    private companion object {
        const val KEY_SELECTED = "selected_deck"
        const val KEY_LEVELS = "deck_levels"
        const val KEY_LIST_KEY = "list_anchor_key"
        const val KEY_LIST_OFFSET = "list_anchor_offset"
    }
}

/**
 * 덱 목록의 스크롤 자리. 목록 칸 번호가 아니라 맨 위에 보이던 덱 id([key])로 기억한다 —
 * 구간·고정·숨김·데이터 갱신으로 목록 순서가 바뀌어도 같은 덱에서 다시 시작하도록.
 */
data class OverlayListAnchor(val key: String, val offset: Int)

/** LazyListState 를 만들 때 쓸 첫 칸 번호와 밀림. */
internal data class OverlayListStart(val index: Int, val offset: Int)

/** 기억해 둔 자리를 지금 목록([keys] = 덱 id 순서)에서 찾는다. 그 덱이 목록에 없으면 맨 위부터. */
internal fun overlayListStart(keys: List<String>, anchor: OverlayListAnchor?): OverlayListStart {
    if (anchor == null) return OverlayListStart(0, 0)
    val index = keys.indexOf(anchor.key)
    return if (index < 0) OverlayListStart(0, 0) else OverlayListStart(index, anchor.offset.coerceAtLeast(0))
}

/** 기억해 둔 레벨이 이 덱의 레벨 칩에 아직 있으면 그것, 아니면(패치로 빌드업이 바뀌는 등) 기본 레벨. */
internal fun resolveOverlayLevel(saved: Int?, levels: List<Int>, fallback: Int?): Int? =
    saved?.takeIf { it in levels } ?: fallback

/** 덱별 레벨을 기억한다. 가장 오래전에 고른 덱부터 버려 [cap] 개까지만 남긴다. */
internal fun rememberDeckLevel(
    levels: Map<String, Int>,
    deckId: String,
    level: Int,
    cap: Int = MAX_REMEMBERED_LEVELS,
): Map<String, Int> {
    if (levels[deckId] == level) return levels
    val next = LinkedHashMap(levels)
    next.remove(deckId)
    next[deckId] = level
    while (next.size > cap) next.remove(next.keys.first())
    return next
}

internal fun encodeDeckLevels(levels: Map<String, Int>): String =
    Json.encodeToString(DeckLevelsSerializer, levels)

/** 저장값이 없거나 깨졌으면 빈 값 — 레벨은 잃어도 되는 편의라 오류로 올리지 않는다. */
internal fun decodeDeckLevels(raw: String?): Map<String, Int> =
    raw?.let { runCatching { Json.decodeFromString(DeckLevelsSerializer, it) }.getOrNull() } ?: emptyMap()

/** 레벨을 기억해 두는 덱 수. 한 패치에 오가는 덱보다 넉넉하다. */
internal const val MAX_REMEMBERED_LEVELS = 60

private val DeckLevelsSerializer = MapSerializer(String.serializer(), Int.serializer())
