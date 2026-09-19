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
 *
 * X·'감추기'로 닫은 기록([dismissal])도 여기 둔다. 메모리에만 두면 프로세스가 되살아날 때 닫은 창이 저절로 다시 떴다(S4).
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
        setLevelsAndSave(rememberDeckLevel(_deckLevels.value, deckId, level))
    }

    /**
     * 덱 id 가 바뀐 데이터가 들어오면(DeckIdMigration — lol.qq 그룹이 metatft 조합 덱에 합쳐지는 등) 보던 덱·레벨·목록 자리도
     * 새 id 로 옮긴다. 고정·숨김·오버레이 덱(pinnedDeckId)을 옮기는 것과 같은 때에 부른다. [current] 는 지금 데이터의 덱 id 전부 —
     * 옮길 곳도 없이 사라진 덱을 보고 있었으면 목록으로 돌린다(앱 열기가 없는 덱을 열지 않게).
     */
    fun migrateIds(mapping: Map<String, String>, current: Set<String>) {
        _selectedDeckId.value?.let { id ->
            val moved = mapping[id] ?: id
            selectDeck(moved.takeIf { it in current })
        }
        setLevelsAndSave(migrateDeckLevels(_deckLevels.value, mapping))
        _listAnchor.value?.let { anchor -> mapping[anchor.key]?.let { setListAnchor(anchor.copy(key = it)) } }
    }

    private fun setLevelsAndSave(next: Map<String, Int>) {
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

    /**
     * 창을 닫은 기록. 이 판 동안([OverlayDismissal.since] 가 같은 TFT 전면 구간) 자동 표시를 막는 데 쓴다([autoAttachBlocked]).
     * 저장값에서 되찾으므로 서비스·프로세스가 다시 떠도 닫은 창이 저절로 돌아오지 않는다.
     */
    var dismissal: OverlayDismissal? =
        if (prefs.contains(KEY_DISMISSED_SINCE) && prefs.contains(KEY_DISMISSED_AT)) {
            OverlayDismissal(since = prefs.getLong(KEY_DISMISSED_SINCE, 0L), at = prefs.getLong(KEY_DISMISSED_AT, 0L))
        } else {
            null
        }
        private set

    /** 창을 닫았다. [since] 는 그때의 TFT 전면 구간 시작 시각, [at] 은 닫은 시각. */
    fun dismiss(since: Long, at: Long) {
        dismissal = OverlayDismissal(since, at)
        prefs.edit().putLong(KEY_DISMISSED_SINCE, since).putLong(KEY_DISMISSED_AT, at).apply()
    }

    /** 사용자가 다시 띄웠다('지금 보이기'·직접 켜기). 닫은 기록을 지운다. */
    fun clearDismissal() {
        if (dismissal == null) return
        dismissal = null
        prefs.edit().remove(KEY_DISMISSED_SINCE).remove(KEY_DISMISSED_AT).apply()
    }

    /**
     * 판 종료(결과 배지)를 감지했다. 닫은 뒤에 끝난 판이면 닫은 판이 끝난 것이므로 기록을 지운다 — 다음 판부터 다시 자동으로
     * 뜬다. 지웠으면 true(부른 쪽이 가시성을 다시 판단한다).
     */
    fun onGameResult(at: Long): Boolean {
        if (!dismissalClearedByResult(dismissal, at)) return false
        clearDismissal()
        return true
    }

    private companion object {
        const val KEY_SELECTED = "selected_deck"
        const val KEY_LEVELS = "deck_levels"
        const val KEY_LIST_KEY = "list_anchor_key"
        const val KEY_LIST_OFFSET = "list_anchor_offset"
        const val KEY_DISMISSED_SINCE = "dismissed_since"
        const val KEY_DISMISSED_AT = "dismissed_at"
    }
}

/** X·'감추기'로 창을 닫은 기록. [since] 는 그때의 TFT 전면 구간 시작 시각(GameState.Foreground.since), [at] 은 닫은 시각. */
internal data class OverlayDismissal(val since: Long, val at: Long)

/**
 * 자동 표시를 막는지(S1). 같은 TFT 전면 구간([foregroundSince])이고 닫은 지 [DISMISS_BLOCK_MS] 가 지나지 않았으면 막는다.
 * 판 사이에 TFT 를 떠나지 않으면 전면 구간이 이어져(GameDetector) 예전에는 TFT 를 나갈 때까지 계속 막혔다. 판 종료 신호는
 * 최대 15분 늦으므로 시한(40분)과 함께 쓴다 — 판이 끝났다는 신호가 오면 [OverlayMemory.onGameResult] 가 기록을 지운다.
 */
internal fun autoAttachBlocked(dismissal: OverlayDismissal?, foregroundSince: Long?, now: Long): Boolean =
    dismissal != null && foregroundSince != null && dismissal.since == foregroundSince &&
        now - dismissal.at < DISMISS_BLOCK_MS

/** 닫은 뒤에 판이 끝났는지. 닫기 전에 이미 떠 있던 결과 배지로는 풀지 않는다. */
internal fun dismissalClearedByResult(dismissal: OverlayDismissal?, resultAt: Long): Boolean =
    dismissal != null && resultAt >= dismissal.at

/** 닫은 창을 자동으로 다시 띄우지 않는 시간. 한 판(보통 30~40분)을 덮는다. */
internal const val DISMISS_BLOCK_MS = 40 * 60 * 1000L

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

/**
 * 덱 id 가 바뀌면 기억한 레벨도 새 id 로 옮긴다. 여러 옛 id 가 같은 새 id 로 모이면(그룹이 합쳐짐) 가장 최근에 고른
 * 레벨을 둔다 — 저장 순서가 오래된 것부터라 뒤에 오는 값이 이긴다. 오래된 것부터 버리는 순서도 그대로 이어진다.
 */
internal fun migrateDeckLevels(levels: Map<String, Int>, mapping: Map<String, String>): Map<String, Int> {
    if (mapping.isEmpty() || levels.keys.none { it in mapping }) return levels
    val out = LinkedHashMap<String, Int>()
    for ((id, level) in levels) {
        val target = mapping[id] ?: id
        out.remove(target)
        out[target] = level
    }
    return out
}

internal fun encodeDeckLevels(levels: Map<String, Int>): String =
    Json.encodeToString(DeckLevelsSerializer, levels)

/** 저장값이 없거나 깨졌으면 빈 값 — 레벨은 잃어도 되는 편의라 오류로 올리지 않는다. */
internal fun decodeDeckLevels(raw: String?): Map<String, Int> =
    raw?.let { runCatching { Json.decodeFromString(DeckLevelsSerializer, it) }.getOrNull() } ?: emptyMap()

/** 레벨을 기억해 두는 덱 수. 한 패치에 오가는 덱보다 넉넉하다. */
internal const val MAX_REMEMBERED_LEVELS = 60

private val DeckLevelsSerializer = MapSerializer(String.serializer(), Int.serializer())
