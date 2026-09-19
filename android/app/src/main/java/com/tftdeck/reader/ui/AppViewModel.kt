package com.tftdeck.reader.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tftdeck.reader.data.CatalogEntry
import com.tftdeck.reader.data.CatalogIndex
import com.tftdeck.reader.data.Deck
import com.tftdeck.reader.data.DeckFeed
import com.tftdeck.reader.data.DeckKeys
import com.tftdeck.reader.data.DeckPrefs
import com.tftdeck.reader.data.DeckRepository
import com.tftdeck.reader.data.DeckSearch
import com.tftdeck.reader.data.DeckSort
import com.tftdeck.reader.data.DeckSortMode
import com.tftdeck.reader.data.DeckToken
import com.tftdeck.reader.data.FeedState
import com.tftdeck.reader.data.IconPack
import com.tftdeck.reader.data.IconSyncResult
import com.tftdeck.reader.data.ItemHit
import com.tftdeck.reader.data.ProfileRepository
import com.tftdeck.reader.data.ProfileState
import com.tftdeck.reader.data.SearchAxis
import com.tftdeck.reader.data.StatsRepository
import com.tftdeck.reader.data.StatsState
import com.tftdeck.reader.data.StatsSyncResult
import com.tftdeck.reader.data.withAugmentDescriptions
import com.tftdeck.reader.data.SyncResult
import com.tftdeck.reader.data.Suggestion
import com.tftdeck.reader.data.TokenCandidate
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
        // 저장해 둔 조회 조건 중 새 데이터에 없는 시너지·레벨은 지운다. 칩이 보이지 않는 조건 때문에
        // 목록만 비는 일을 막는다(검색 칩은 화면에 보이므로 그대로 둔다).
        viewModelScope.launch {
            repository.state.collect { state ->
                val feed = (state as? FeedState.Ready)?.feed ?: return@collect
                prefs.mainTrait.value?.let { id ->
                    if (feed.decks.none { deck -> deck.mainTraits.any { it.id == id } }) prefs.setMainTrait(null)
                }
                val levels = feed.decks.mapNotNullTo(HashSet()) { it.finalLevel }
                val kept = prefs.levels.value.filterTo(HashSet()) { it in levels }
                if (kept != prefs.levels.value) prefs.setLevels(kept)
            }
        }
    }

    val feedState: StateFlow<FeedState> = repository.state

    /**
     * 검색 엔진은 피드나 도감 증강 설명이 바뀔 때만 새로 만든다.
     * 인덱스를 만드는 동안 UI가 멈추지 않도록 기본 디스패처에서 돌린다.
     *
     * 증강 설명은 decks.json 에 없고 도감 통계 파일(stats/augments.json)에 있다. 합쳐야 증강 설명문 검색(§6.5)이
     * 실제로 동작한다. 도감 파일이 아직 없으면 이름으로만 찾는다.
     */
    private val engine: StateFlow<DeckSearch?> =
        combine(repository.state, StatsRepository.get(app).state) { deckState, statsState ->
            val feed = (deckState as? FeedState.Ready)?.feed ?: return@combine null
            val descriptions = (statsState as? StatsState.Ready)?.augments?.augments.orEmpty()
                .filter { it.desc.isNotBlank() }
                .associate { it.id to it.desc }
            DeckSearch(feed.withAugmentDescriptions(descriptions))
        }
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

    val sort: StateFlow<DeckSort> = prefs.sort

    /** 다른 정렬 칩은 그 정렬의 기본 방향으로, 고른 칩을 다시 누르면 방향을 뒤집는다. */
    fun tapSort(mode: DeckSortMode) {
        prefs.setSort(prefs.sort.value.tapped(mode))
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

    // 조회 조건은 DeckPrefs 에 저장해 앱을 껐다 켜도 지난 조건으로 돌아온다(구간·정렬·등급과 같이).
    val tierFilter: StateFlow<Set<String>> = prefs.tiers
    val levelFilter: StateFlow<Set<Int>> = prefs.levels
    val onlyChina: StateFlow<Boolean> = prefs.onlyChina
    val editorialOnly: StateFlow<Boolean> = prefs.editorialOnly
    val mainTraitFilter: StateFlow<String?> = prefs.mainTrait

    // 목록 검색 줄의 조건(칩)도 저장한다. 오버레이의 조건과는 따로 둔다(오버레이는 창 안에서 기억한다).
    // 치는 중인 글자는 저장하지 않는다. [decks] 가 이 값을 읽으므로 그보다 먼저 선언한다.
    val listTokens: StateFlow<List<DeckToken>> = prefs.listTokens

    private val _listQuery = MutableStateFlow("")
    val listQuery: StateFlow<String> = _listQuery.asStateFlow()

    private data class FilterSpec(
        val tiers: Set<String>,
        val levels: Set<Int>,
        val onlyChina: Boolean,
        val editorialOnly: Boolean,
        val mainTrait: String?,
    )

    private data class ListPrefs(
        val bucket: String,
        val sort: DeckSort,
        val pinned: Set<String>,
        val hidden: Set<String>,
        val showHidden: Boolean,
    )

    private val filterSpec = combine(prefs.tiers, prefs.levels, prefs.onlyChina, prefs.editorialOnly, prefs.mainTrait) { t, l, c, e, m ->
        FilterSpec(t, l, c, e, m)
    }

    private val listPrefs = combine(bucket, prefs.sort, prefs.pinned, prefs.hidden, prefs.showHidden) { b, s, p, h, sh ->
        ListPrefs(b, s, p, h, sh)
    }

    /** 덱 등급 조회 조건(S~D 여러 개). 오버레이 목록도 같은 값을 쓴다. */
    val gradeFilter: StateFlow<Set<String>> = prefs.grades

    fun toggleGrade(grade: String) {
        prefs.toggleGrade(grade)
    }

    /**
     * 필터 → 검색 줄 조건(AND) → 정렬 → 고정한 덱을 맨 위로. 고정한 덱도 검색 조건에는 맞아야 남지만,
     * 등급 조회 조건은 건너뛴다(직접 고른 덱이 기본 조건 때문에 사라지지 않게).
     */
    val decks: StateFlow<List<Deck>> =
        combine(engine, filterSpec, listPrefs, prefs.listTokens, prefs.grades) { search, filter, list, tokens, grades ->
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
                alwaysShow = list.pinned,
                grades = grades,
            )
            val narrowed = search.filterByTokens(filtered, tokens)
            DeckSearch.pinFirst(DeckSearch.sort(narrowed, list.sort.mode, list.bucket, list.sort.reversed), list.pinned)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 검색 줄 후보. 후보마다 지금 목록에 그 조건을 더했을 때 남는 덱 수를 센다. */
    val listCandidates: StateFlow<List<TokenCandidate>> =
        combine(engine, _listQuery, prefs.listTokens, decks) { search, text, tokens, current ->
            search?.suggestTokens(text, tokens, within = current, limit = LIST_CANDIDATES).orEmpty()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setListQuery(text: String) {
        _listQuery.value = text
    }

    /** 후보를 고르면 조건으로 쌓고 치던 글자를 비운다. 같은 조건은 두 번 쌓지 않는다. */
    fun addListToken(token: DeckToken) {
        val tokens = prefs.listTokens.value
        if (tokens.none { it.key == token.key }) prefs.setListTokens(tokens + token)
        _listQuery.value = ""
    }

    fun removeListToken(token: DeckToken) {
        prefs.setListTokens(prefs.listTokens.value.filterNot { it.key == token.key })
    }

    fun clearListTokens() {
        prefs.setListTokens(emptyList())
        _listQuery.value = ""
    }

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
        prefs.setTiers(prefs.tiers.value.toggle(tier))
    }

    fun toggleLevel(level: Int) {
        prefs.setLevels(prefs.levels.value.toggle(level))
    }

    fun toggleOnlyChina() {
        prefs.setOnlyChina(!prefs.onlyChina.value)
    }

    fun toggleEditorialOnly() {
        prefs.setEditorialOnly(!prefs.editorialOnly.value)
    }

    /** 주 특성은 하나만 고른다. 같은 칩을 다시 누르면 해제. */
    fun toggleMainTrait(id: String) {
        prefs.setMainTrait(if (prefs.mainTrait.value == id) null else id)
    }

    /** 조회 조건 초기화: 필터·등급(기본 S·A·B)·검색 칩·숨긴 덱 보기를 처음 상태로. 구간·정렬은 그대로다. */
    fun clearFilters() {
        prefs.resetQuery()
        _listQuery.value = ""
    }

    val hasActiveFilter: StateFlow<Boolean> =
        combine(filterSpec, prefs.showHidden, prefs.grades, prefs.listTokens) { f, showHidden, grades, tokens ->
            f.tiers.isNotEmpty() || f.levels.isNotEmpty() || f.onlyChina || f.editorialOnly ||
                f.mainTrait != null || showHidden || grades != DeckKeys.GRADE_FILTER_DEFAULT || tokens.isNotEmpty()
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

    // -- 첫 실행 안내 -------------------------------------------------------

    // 설치 뒤 첫 실행에 전적 연결·오버레이 권한을 한 번 묻는다. 한 번 닫으면 다시 묻지 않는다.
    private val _firstRunPending = MutableStateFlow(!prefs.firstRunDone)
    val firstRunPending: StateFlow<Boolean> = _firstRunPending.asStateFlow()

    fun finishFirstRun() {
        prefs.firstRunDone = true
        _firstRunPending.value = false
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

        /** 목록 검색 줄 후보 수(사용자 지정 후보는 따로 하나 더 붙는다). */
        const val LIST_CANDIDATES = 8
    }
}

data class SearchResults(
    val decks: List<Deck> = emptyList(),
    val itemHits: List<ItemHit> = emptyList(),
) {
    val isEmpty: Boolean get() = decks.isEmpty() && itemHits.isEmpty()
}
