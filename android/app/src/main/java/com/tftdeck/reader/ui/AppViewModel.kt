package com.tftdeck.reader.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tftdeck.reader.data.CatalogEntry
import com.tftdeck.reader.data.CatalogIndex
import com.tftdeck.reader.data.Deck
import com.tftdeck.reader.data.DeckFeed
import com.tftdeck.reader.data.DeckPrefs
import com.tftdeck.reader.data.DeckRepository
import com.tftdeck.reader.data.DeckSearch
import com.tftdeck.reader.data.DeckSortMode
import com.tftdeck.reader.data.FeedState
import com.tftdeck.reader.data.IconPack
import com.tftdeck.reader.data.IconSyncResult
import com.tftdeck.reader.data.ItemHit
import com.tftdeck.reader.data.ProfileRepository
import com.tftdeck.reader.data.ProfileState
import com.tftdeck.reader.data.SearchAxis
import com.tftdeck.reader.data.StatsRepository
import com.tftdeck.reader.data.StatsSyncResult
import com.tftdeck.reader.data.SyncResult
import com.tftdeck.reader.data.Suggestion
import com.tftdeck.reader.data.TraitRef
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = DeckRepository.get(app)

    /** 구간·정렬·고정·숨김. 오버레이와 같은 싱글턴을 본다. */
    private val prefs = DeckPrefs.get(app)

    init {
        // 저장해 둔 전적 요약을 먼저 올린다. 설정 화면이 빈 채로 뜨지 않도록.
        viewModelScope.launch { ProfileRepository.get(app).load() }
    }

    val feedState: StateFlow<FeedState> = repository.state

    /**
     * 검색 엔진은 피드가 바뀔 때만 새로 만든다.
     * 인덱스를 만드는 동안 UI가 멈추지 않도록 기본 디스패처에서 돌린다.
     */
    private val engine: StateFlow<DeckSearch?> = repository.state
        .map { state -> (state as? FeedState.Ready)?.let { DeckSearch(it.feed) } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * 아직 인덱스를 만드는 중인지.
     * 이걸 구분하지 않으면 로딩 중에 덱이 0개인 것을 '필터 때문'이라고 잘못 안내하게 된다.
     */
    val searchReady: StateFlow<Boolean> = engine
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val assetBase: StateFlow<String> = repository.state
        .map { (it as? FeedState.Ready)?.feed?.version?.assetBase.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    /** 지금 화면에 올라온 피드. 없으면 null. */
    val currentFeed: DeckFeed? get() = (repository.state.value as? FeedState.Ready)?.feed

    // -- 구간·정렬(기기에 저장) ----------------------------------------------

    /**
     * 선택한 구간. 저장해 둔 구간이 이번 피드에 없으면 피드의 기본 구간으로 본다 —
     * 그대로 두면 모든 카드가 '-'로 비어 보인다.
     */
    val bucket: StateFlow<String> =
        combine(prefs.bucket, repository.state) { saved, state ->
            val feed = (state as? FeedState.Ready)?.feed
            if (feed == null || feed.buckets.isEmpty() || saved in feed.buckets) saved else feed.defaultBucket
        }.stateIn(viewModelScope, SharingStarted.Eagerly, prefs.bucket.value)

    fun setBucket(key: String) {
        prefs.setBucket(key)
    }

    val sortMode: StateFlow<DeckSortMode> = prefs.sortMode

    fun setSortMode(mode: DeckSortMode) {
        prefs.setSortMode(mode)
    }

    val pinnedSet: StateFlow<Set<String>> = prefs.pinned

    fun togglePinned(id: String) {
        prefs.togglePinned(id)
    }

    val hiddenSet: StateFlow<Set<String>> = prefs.hidden

    fun toggleHidden(id: String) {
        prefs.toggleHidden(id)
    }

    val showHidden: StateFlow<Boolean> = prefs.showHidden

    fun toggleShowHidden() {
        prefs.setShowHidden(!prefs.showHidden.value)
    }

    // -- 덱 목록 필터 -------------------------------------------------------

    private val _tierFilter = MutableStateFlow<Set<String>>(emptySet())
    val tierFilter: StateFlow<Set<String>> = _tierFilter.asStateFlow()

    private val _levelFilter = MutableStateFlow<Set<Int>>(emptySet())
    val levelFilter: StateFlow<Set<Int>> = _levelFilter.asStateFlow()

    private val _onlyChina = MutableStateFlow(false)
    val onlyChina: StateFlow<Boolean> = _onlyChina.asStateFlow()

    private val _editorialOnly = MutableStateFlow(false)
    val editorialOnly: StateFlow<Boolean> = _editorialOnly.asStateFlow()

    private val _mainTrait = MutableStateFlow<String?>(null)
    val mainTraitFilter: StateFlow<String?> = _mainTrait.asStateFlow()

    private data class FilterSpec(
        val tiers: Set<String>,
        val levels: Set<Int>,
        val onlyChina: Boolean,
        val editorialOnly: Boolean,
        val mainTrait: String?,
    )

    private data class ListPrefs(
        val bucket: String,
        val sort: DeckSortMode,
        val pinned: Set<String>,
        val hidden: Set<String>,
        val showHidden: Boolean,
    )

    private val filterSpec = combine(_tierFilter, _levelFilter, _onlyChina, _editorialOnly, _mainTrait) { t, l, c, e, m ->
        FilterSpec(t, l, c, e, m)
    }

    private val listPrefs = combine(bucket, prefs.sortMode, prefs.pinned, prefs.hidden, prefs.showHidden) { b, s, p, h, sh ->
        ListPrefs(b, s, p, h, sh)
    }

    /** 필터 → 정렬 → 고정한 덱을 맨 위로. */
    val decks: StateFlow<List<Deck>> =
        combine(engine, filterSpec, listPrefs) { search, filter, list ->
            if (search == null) return@combine emptyList()
            val filtered = search.filter(
                tiers = filter.tiers,
                levels = filter.levels,
                onlyChina = filter.onlyChina,
                editorialOnly = filter.editorialOnly,
                mainTrait = filter.mainTrait,
                hidden = list.hidden,
                showHidden = list.showHidden,
                bucket = list.bucket,
            )
            DeckSearch.pinFirst(DeckSearch.sort(filtered, list.sort, list.bucket), list.pinned)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 필터에 쓸 등급 목록. 선택 구간에서 실제로 나오는 것만 보여준다. */
    val availableTiers: StateFlow<List<String>> =
        combine(repository.state, bucket) { state, b ->
            (state as? FeedState.Ready)?.feed?.decks
                ?.mapNotNull { it.gradeFor(b) }?.distinct()
                ?.sortedBy { tier -> TIER_SORT.indexOf(tier).takeIf { it >= 0 } ?: 99 }
                .orEmpty()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val availableLevels: StateFlow<List<Int>> = repository.state
        .map { state ->
            (state as? FeedState.Ready)?.feed?.decks
                ?.mapNotNull { it.finalLevel }?.distinct()?.sorted().orEmpty()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 주 특성 필터 칩. 목록에 실제로 있는 주 특성만, 많이 쓰이는 순. 아이콘이 있는 항목을 대표로 쓴다. */
    val availableMainTraits: StateFlow<List<TraitRef>> = repository.state
        .map { state ->
            (state as? FeedState.Ready)?.feed?.decks.orEmpty()
                .flatMap { deck -> deck.mainTraits.distinctBy { it.id } }
                .groupBy { it.id }
                .values
                .sortedWith(compareByDescending<List<TraitRef>> { it.size }.thenBy { it.first().name })
                .map { group -> group.firstOrNull { !it.icon.isNullOrBlank() } ?: group.first() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleTier(tier: String) {
        _tierFilter.value = _tierFilter.value.toggle(tier)
    }

    fun toggleLevel(level: Int) {
        _levelFilter.value = _levelFilter.value.toggle(level)
    }

    fun toggleOnlyChina() {
        _onlyChina.value = !_onlyChina.value
    }

    fun toggleEditorialOnly() {
        _editorialOnly.value = !_editorialOnly.value
    }

    /** 주 특성은 하나만 고른다. 같은 칩을 다시 누르면 해제. */
    fun toggleMainTrait(id: String) {
        _mainTrait.value = if (_mainTrait.value == id) null else id
    }

    fun clearFilters() {
        _tierFilter.value = emptySet()
        _levelFilter.value = emptySet()
        _onlyChina.value = false
        _editorialOnly.value = false
        _mainTrait.value = null
        prefs.setShowHidden(false)
    }

    val hasActiveFilter: StateFlow<Boolean> =
        combine(filterSpec, prefs.showHidden) { f, showHidden ->
            f.tiers.isNotEmpty() || f.levels.isNotEmpty() || f.onlyChina || f.editorialOnly ||
                f.mainTrait != null || showHidden
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // -- 검색 ---------------------------------------------------------------

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _selected = MutableStateFlow<Suggestion?>(null)
    val selected: StateFlow<Suggestion?> = _selected.asStateFlow()

    val suggestions: StateFlow<List<Suggestion>> =
        combine(engine, _query) { search, text -> search?.suggest(text).orEmpty() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 선택한 항목이 들어가는 덱. 아이템이면 어느 챔피언이 드는지까지 나온다. */
    val results: StateFlow<SearchResults> =
        combine(engine, _selected) { search, pick ->
            if (search == null || pick == null) return@combine SearchResults()
            if (pick.axis == SearchAxis.ITEM) {
                SearchResults(itemHits = search.decksWithItem(pick.name))
            } else {
                SearchResults(decks = search.decksFor(pick.axis, pick.name))
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults())

    fun onQueryChange(text: String) {
        _query.value = text
        if (text.isBlank()) _selected.value = null
    }

    fun select(suggestion: Suggestion) {
        _selected.value = suggestion
        _query.value = suggestion.name
    }

    fun clearSearch() {
        _query.value = ""
        _selected.value = null
    }

    /** DA id 로 덱 찾기(도감 → 덱). byId 가 없는 옛 피드는 이름 인덱스로 떨어진다. */
    fun decksForId(axis: SearchAxis, id: String): List<Deck> =
        engine.value?.decksForId(axis, id).orEmpty()

    // -- 덱 상세 ------------------------------------------------------------

    fun deck(id: String): Deck? =
        (repository.state.value as? FeedState.Ready)?.feed?.decks?.firstOrNull { it.id == id }

    @Volatile
    private var catalogCache: Pair<DeckFeed, CatalogIndex>? = null

    /**
     * 지금 피드의 catalog 지도. 화면이 그리는 도중에 동기로 불러도 늦지 않도록
     * 흐름 대신 피드 객체 기준 캐시로 둔다(피드가 바뀌면 한 번만 새로 만든다).
     */
    fun catalog(): CatalogIndex? {
        val feed = currentFeed ?: return null
        catalogCache?.let { (cachedFeed, index) -> if (cachedFeed === feed) return index }
        return CatalogIndex(feed.catalog).also { catalogCache = feed to it }
    }

    /** 단계 배치·빌드업은 id 참조라 catalog에서 이름/아이콘을 찾아야 한다. */
    fun catalogChampion(id: String): CatalogEntry? = catalog()?.champions?.get(id)

    fun catalogItem(id: String): CatalogEntry? = catalog()?.items?.get(id)

    fun catalogTrait(id: String): CatalogEntry? = catalog()?.traits?.get(id)

    fun catalogAugment(id: String): CatalogEntry? = catalog()?.augments?.get(id)

    /** 상점에 없는 소환물. */
    fun petEntry(id: String): CatalogEntry? = catalog()?.pets?.get(id)

    /** 챔피언이면 챔피언, 아니면 소환물. */
    fun unitEntry(id: String): CatalogEntry? = catalog()?.unit(id)

    // -- 동기화 -------------------------------------------------------------

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    fun refresh() {
        if (_syncing.value) return
        viewModelScope.launch {
            _syncing.value = true
            val deckMessage = when (val outcome = repository.sync(force = true)) {
                is SyncResult.Updated -> "패치 ${outcome.patch} · 덱 ${outcome.deckCount}개로 갱신했습니다"
                SyncResult.UpToDate -> "이미 최신입니다"
                is SyncResult.Failed -> "갱신 실패: ${outcome.reason}"
            }
            // 같은 버튼으로 도감 통계와 아이콘 팩도 받는다(하루 한 번 도는 워커와 같은 범위).
            // 둘은 실패해도 기존 파일을 그대로 쓰므로, 새로 받은 것만 덧붙여 알린다.
            val extras = buildList {
                if (syncStatsQuietly()) add("도감")
                if (syncIconsQuietly()) add("아이콘")
            }
            _syncMessage.value =
                if (extras.isEmpty()) deckMessage else "$deckMessage · ${extras.joinToString("·")} 새로 받음"
            _syncing.value = false
        }
    }

    /** 도감 통계를 받는다. 새 파일을 받았으면 true. 실패하면 기존 데이터를 유지하므로 조용히 넘긴다. */
    private suspend fun syncStatsQuietly(): Boolean = try {
        val stats = StatsRepository.get(getApplication())
        stats.load()
        stats.sync() is StatsSyncResult.Updated
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    /** 아이콘 팩을 받는다. 새 팩으로 바꿨으면 true. */
    private suspend fun syncIconsQuietly(): Boolean = try {
        IconPack.get(getApplication()).sync() is IconSyncResult.Updated
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    fun consumeSyncMessage() {
        _syncMessage.value = null
    }

    // -- 내 전적 -------------------------------------------------------------

    private val profiles = ProfileRepository.get(app)

    val profileState: StateFlow<ProfileState> = profiles.state

    private val _savedRiotId = MutableStateFlow(profiles.riotId)
    val savedRiotId: StateFlow<String> = _savedRiotId.asStateFlow()

    private val _savedRegion = MutableStateFlow(profiles.region)
    val savedRegion: StateFlow<String> = _savedRegion.asStateFlow()

    private val _profileBusy = MutableStateFlow(false)
    val profileBusy: StateFlow<Boolean> = _profileBusy.asStateFlow()

    /** "이름#태그"와 지역을 저장하고 곧바로 한 번 조회한다. */
    fun saveProfile(riotId: String, region: String) {
        if (_profileBusy.value) return
        viewModelScope.launch {
            _profileBusy.value = true
            profiles.configure(riotId, region)
            _savedRiotId.value = profiles.riotId
            _savedRegion.value = profiles.region
            _profileBusy.value = false
        }
    }

    fun refreshProfile() {
        if (_profileBusy.value) return
        viewModelScope.launch {
            _profileBusy.value = true
            profiles.refresh(force = true)
            _profileBusy.value = false
        }
    }

    fun clearProfile() {
        profiles.clear()
        _savedRiotId.value = ""
        _savedRegion.value = ProfileRepository.DEFAULT_REGION
    }

    // -- 오버레이 -----------------------------------------------------------

    private val _pinned = MutableStateFlow(repository.pinnedDeckId)
    val pinnedDeckId: StateFlow<String?> = _pinned.asStateFlow()

    fun pinDeck(id: String?) {
        repository.pinnedDeckId = id
        _pinned.value = id
    }

    private fun <T> Set<T>.toggle(value: T): Set<T> =
        if (contains(value)) this - value else this + value

    private companion object {
        val TIER_SORT = listOf("SS", "S", "A", "B", "C", "D")
    }
}

data class SearchResults(
    val decks: List<Deck> = emptyList(),
    val itemHits: List<ItemHit> = emptyList(),
) {
    val isEmpty: Boolean get() = decks.isEmpty() && itemHits.isEmpty()
}
