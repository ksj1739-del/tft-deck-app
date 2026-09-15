package com.tftdeck.reader.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 덱 목록 보기 설정(구간·정렬·고정·숨김). 기기에만 저장한다.
 *
 * 앱 화면(ViewModel)과 오버레이 서비스가 같은 프로세스에서 이 싱글턴을 함께 본다.
 * 그래서 앱에서 구간을 바꾸면 오버레이 목록도 곧바로 같은 구간으로 바뀌고, 그 반대도 같다.
 */
class DeckPrefs private constructor(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _bucket = MutableStateFlow(prefs.getString(KEY_BUCKET, null) ?: DeckKeys.DEFAULT_BUCKET)
    val bucket: StateFlow<String> = _bucket.asStateFlow()

    private val _sort = MutableStateFlow(
        DeckSort(DeckSortMode.fromKey(prefs.getString(KEY_SORT, null)), prefs.getBoolean(KEY_SORT_REVERSED, false)),
    )
    val sort: StateFlow<DeckSort> = _sort.asStateFlow()

    // getStringSet 이 돌려준 집합은 저장소 내부 객체라 고치면 안 된다. 복사해서 들고 있는다.
    private val _pinned = MutableStateFlow(prefs.getStringSet(KEY_PINNED, null)?.toSet().orEmpty())
    val pinned: StateFlow<Set<String>> = _pinned.asStateFlow()

    private val _hidden = MutableStateFlow(prefs.getStringSet(KEY_HIDDEN, null)?.toSet().orEmpty())
    val hidden: StateFlow<Set<String>> = _hidden.asStateFlow()

    private val _showHidden = MutableStateFlow(prefs.getBoolean(KEY_SHOW_HIDDEN, false))
    val showHidden: StateFlow<Boolean> = _showHidden.asStateFlow()

    fun setBucket(key: String) {
        _bucket.value = key
        prefs.edit().putString(KEY_BUCKET, key).apply()
    }

    // 정렬과 방향은 한 값으로 바꾼다. 따로 바꾸면 목록이 '새 정렬 + 옛 방향'으로 한 번 더 그려진다.
    fun setSort(sort: DeckSort) {
        _sort.value = sort
        prefs.edit().putString(KEY_SORT, sort.mode.key).putBoolean(KEY_SORT_REVERSED, sort.reversed).apply()
    }

    fun togglePinned(id: String) {
        _pinned.update { it.toggle(id) }
        // 저장할 때도 새 집합을 넘겨야 한다. 같은 객체를 다시 넣으면 변경으로 인식되지 않는다.
        prefs.edit().putStringSet(KEY_PINNED, HashSet(_pinned.value)).apply()
    }

    fun toggleHidden(id: String) {
        _hidden.update { it.toggle(id) }
        prefs.edit().putStringSet(KEY_HIDDEN, HashSet(_hidden.value)).apply()
    }

    fun setShowHidden(show: Boolean) {
        _showHidden.value = show
        prefs.edit().putBoolean(KEY_SHOW_HIDDEN, show).apply()
    }

    private fun Set<String>.toggle(value: String): Set<String> =
        if (contains(value)) this - value else this + value

    companion object {
        private const val PREFS = "deck_prefs"
        private const val KEY_BUCKET = "bucket"
        private const val KEY_SORT = "sort_mode"
        private const val KEY_SORT_REVERSED = "sort_reversed"
        private const val KEY_PINNED = "pinned"
        private const val KEY_HIDDEN = "hidden"
        private const val KEY_SHOW_HIDDEN = "show_hidden"

        @Volatile
        private var instance: DeckPrefs? = null

        fun get(context: Context): DeckPrefs =
            instance ?: synchronized(this) {
                instance ?: DeckPrefs(context.applicationContext).also { instance = it }
            }
    }
}
