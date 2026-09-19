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

    // 덱 등급 조회 조건(여러 개 고름). 저장값이 없으면 S·A·B 만 켠다.
    private val _grades = MutableStateFlow(
        prefs.getStringSet(KEY_GRADES, null)?.toSet()?.takeIf { it.isNotEmpty() } ?: DeckKeys.GRADE_FILTER_DEFAULT,
    )
    val grades: StateFlow<Set<String>> = _grades.asStateFlow()

    // 덱 목록 조회 조건. 앱을 껐다 켜도 지난 조건으로 돌아온다. 오버레이의 검색 조건과는 따로다.
    private val _onlyChina = MutableStateFlow(prefs.getBoolean(KEY_ONLY_CHINA, false))
    val onlyChina: StateFlow<Boolean> = _onlyChina.asStateFlow()

    private val _editorialOnly = MutableStateFlow(prefs.getBoolean(KEY_EDITORIAL_ONLY, false))
    val editorialOnly: StateFlow<Boolean> = _editorialOnly.asStateFlow()

    private val _mainTrait = MutableStateFlow(prefs.getString(KEY_MAIN_TRAIT, null))
    val mainTrait: StateFlow<String?> = _mainTrait.asStateFlow()

    private val _levels = MutableStateFlow(
        prefs.getStringSet(KEY_LEVELS, null).orEmpty().mapNotNullTo(HashSet()) { it.toIntOrNull() }.toSet(),
    )
    val levels: StateFlow<Set<Int>> = _levels.asStateFlow()

    // v1 피드의 편집 등급 칩.
    private val _tiers = MutableStateFlow(prefs.getStringSet(KEY_TIERS, null)?.toSet().orEmpty())
    val tiers: StateFlow<Set<String>> = _tiers.asStateFlow()

    // 목록 검색 줄의 조건 칩(치는 중인 글자는 저장하지 않는다).
    private val _listTokens = MutableStateFlow(DeckToken.decode(prefs.getString(KEY_LIST_TOKENS, null)))
    val listTokens: StateFlow<List<DeckToken>> = _listTokens.asStateFlow()

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

    /** 덱 id 가 바뀐 데이터가 들어오면 고정·숨김을 새 id 로 옮긴다. [mapping] 은 옛 id → 새 id([DeckIdMigration]). */
    fun migrateIds(mapping: Map<String, String>) {
        if (mapping.isEmpty()) return
        val pinned = _pinned.value.mapTo(HashSet()) { mapping[it] ?: it }
        if (pinned != _pinned.value) {
            _pinned.value = pinned
            prefs.edit().putStringSet(KEY_PINNED, HashSet(pinned)).apply()
        }
        val hidden = _hidden.value.mapTo(HashSet()) { mapping[it] ?: it }
        if (hidden != _hidden.value) {
            _hidden.value = hidden
            prefs.edit().putStringSet(KEY_HIDDEN, HashSet(hidden)).apply()
        }
    }

    /** 등급 칩을 켜고 끈다. 마지막 하나는 끄지 않는다(목록이 통째로 비지 않게). */
    fun toggleGrade(grade: String) {
        val next = _grades.value.toggle(grade)
        if (next.isNotEmpty()) setGrades(next)
    }

    fun setGrades(grades: Set<String>) {
        _grades.value = grades
        prefs.edit().putStringSet(KEY_GRADES, HashSet(grades)).apply()
    }

    fun setOnlyChina(value: Boolean) {
        _onlyChina.value = value
        prefs.edit().putBoolean(KEY_ONLY_CHINA, value).apply()
    }

    fun setEditorialOnly(value: Boolean) {
        _editorialOnly.value = value
        prefs.edit().putBoolean(KEY_EDITORIAL_ONLY, value).apply()
    }

    fun setMainTrait(id: String?) {
        _mainTrait.value = id
        prefs.edit().apply { if (id == null) remove(KEY_MAIN_TRAIT) else putString(KEY_MAIN_TRAIT, id) }.apply()
    }

    fun setLevels(levels: Set<Int>) {
        _levels.value = levels
        prefs.edit().putStringSet(KEY_LEVELS, levels.mapTo(HashSet()) { it.toString() }).apply()
    }

    fun setTiers(tiers: Set<String>) {
        _tiers.value = tiers
        prefs.edit().putStringSet(KEY_TIERS, HashSet(tiers)).apply()
    }

    fun setListTokens(tokens: List<DeckToken>) {
        _listTokens.value = tokens
        prefs.edit().putString(KEY_LIST_TOKENS, DeckToken.encode(tokens)).apply()
    }

    /** 조회 조건을 처음 상태로 돌린다. 등급은 기본값(S·A·B)으로. 구간·정렬·고정·숨김 목록은 그대로 둔다. */
    fun resetQuery() {
        setOnlyChina(false)
        setEditorialOnly(false)
        setMainTrait(null)
        setLevels(emptySet())
        setTiers(emptySet())
        setListTokens(emptyList())
        setGrades(DeckKeys.GRADE_FILTER_DEFAULT)
        setShowHidden(false)
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
        private const val KEY_GRADES = "grade_filter"
        private const val KEY_ONLY_CHINA = "filter_only_china"
        private const val KEY_EDITORIAL_ONLY = "filter_editorial_only"
        private const val KEY_MAIN_TRAIT = "filter_main_trait"
        private const val KEY_LEVELS = "filter_levels"
        private const val KEY_TIERS = "filter_tiers"
        private const val KEY_LIST_TOKENS = "filter_list_tokens"

        @Volatile
        private var instance: DeckPrefs? = null

        fun get(context: Context): DeckPrefs =
            instance ?: synchronized(this) {
                instance ?: DeckPrefs(context.applicationContext).also { instance = it }
            }
    }
}
