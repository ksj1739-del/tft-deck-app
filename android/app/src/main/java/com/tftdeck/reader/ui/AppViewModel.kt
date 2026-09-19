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
import com.tftdeck.reader.data.FeedVersion
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

    /**
     * 정렬 메뉴에 보일 정렬: 등급·픽률·상승. '표본'은 유지보수자용이라 메뉴에서 뺀다(L8).
     * 저장해 둔 정렬이 표본이면 그대로 쓴다(메뉴에만 안 보인다). 정렬 규칙 자체는 [DeckSearch.sort] 그대로다.
     */
    val sortModesVisible: List<DeckSortMode> = visibleSortModes()

    /** 다른 정렬은 그 정렬의 기본 방향으로, 고른 정렬을 다시 누르면 방향을 뒤집는다. */
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

    // 검색 조건이 있을 때 등급·구간 조건 밖의 덱도 잠시 함께 보는지(결정 1 '조건 밖 N개 더'). 저장하지 않고,
    // 검색 조건이 바뀌거나 조건을 초기화하면 끈다 — 잠시 풀어 보는 것이지 조회 조건이 아니다.
    private val _showOutside = MutableStateFlow(false)
    val showOutside: StateFlow<Boolean> = _showOutside.asStateFlow()

    fun toggleShowOutside() {
        _showOutside.value = !_showOutside.value
    }

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

    private data class QuerySpec(
        val tokens: List<DeckToken>,
        val grades: Set<String>,
        val showOutside: Boolean,
    )

    private val filterSpec = combine(prefs.tiers, prefs.levels, prefs.onlyChina, prefs.editorialOnly, prefs.mainTrait) { t, l, c, e, m ->
        FilterSpec(t, l, c, e, m)
    }

    private val listPrefs = combine(bucket, prefs.sort, prefs.pinned, prefs.hidden, prefs.showHidden) { b, s, p, h, sh ->
        ListPrefs(b, s, p, h, sh)
    }

    private val querySpec = combine(prefs.listTokens, prefs.grades, _showOutside) { tokens, grades, outside ->
        QuerySpec(tokens, grades, outside)
    }

    /** 덱 등급 조회 조건(S~D 여러 개). 오버레이 목록도 같은 값을 쓴다. */
    val gradeFilter: StateFlow<Set<String>> = prefs.grades

    fun toggleGrade(grade: String) {
        prefs.toggleGrade(grade)
    }

    /** 목록 끝 'C·D 등급 덱 N개 숨김 · 보기': 꺼 둔 등급을 모두 켠다(저장된다. '조건 초기화'가 S·A·B 로 되돌린다). */
    fun showAllGrades() {
        prefs.setGrades(DeckKeys.GRADE_FILTER_ALL.toSet())
    }

    /**
     * 목록 한 벌: 필터 → 검색 줄 조건(AND) → 정렬 → 고정한 덱을 맨 위로, 그리고 조건 때문에 빠진 수와 아이템 묶음.
     * 고정한 덱도 검색 조건에는 맞아야 남지만, 등급 조회 조건은 건너뛴다(직접 고른 덱이 기본 조건 때문에 사라지지 않게).
     */
    val listState: StateFlow<DeckListResult> =
        combine(engine, filterSpec, listPrefs, querySpec) { search, filter, list, query ->
            if (search == null) return@combine DeckListResult()
            buildDeckList(
                search,
                DeckQuery(
                    bucket = list.bucket,
                    sort = list.sort,
                    tiers = filter.tiers,
                    levels = filter.levels,
                    onlyChina = filter.onlyChina,
                    editorialOnly = filter.editorialOnly,
                    mainTrait = filter.mainTrait,
                    pinned = list.pinned,
                    hidden = list.hidden,
                    showHidden = list.showHidden,
                    tokens = query.tokens,
                    grades = query.grades,
                    showOutside = query.showOutside,
                ),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DeckListResult())

    /** 지금 목록에 보이는 덱. [listState] 의 decks 와 같다. */
    val decks: StateFlow<List<Deck>> = listState
        .map { it.decks }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 등급 조회 조건 때문에만 빠진 덱 수(목록 끝 안내 N11). 다른 조건·검색 조건에는 맞는 덱만 센다. */
    val hiddenByGradeCount: StateFlow<Int> = listState
        .map { it.hiddenByGrade }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** 검색 줄 후보. 후보마다 지금 목록에 그 조건을 더했을 때 남는 덱 수를 센다. */
    val listCandidates: StateFlow<List<TokenCandidate>> =
        combine(engine, _listQuery, prefs.listTokens, decks) { search, text, tokens, current ->
            search?.suggestTokens(text, tokens, within = current, limit = LIST_CANDIDATES).orEmpty()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 아이템 후보의 핵심·대체 덱 수(검색 탭에서 옮긴 기능, 결정 1). 키는 [DeckToken.key].
     * 지금 목록 안에서 센다 — 후보 줄의 '덱 N' 과 같은 기준이라 핵심 + 대체 = N 이다.
     */
    val listCandidateRoles: StateFlow<Map<String, ItemRoleCount>> =
        combine(engine, listCandidates, decks) { search, candidates, current ->
            if (search == null) return@combine emptyMap()
            candidates.filter { it.token.axis == SearchAxis.ITEM }
                .associate { it.token.key to itemRoleCount(search.decksWithItem(it.token.name), current) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** 검색 줄을 눌렀는데 아직 아무것도 없을 때 보여 줄 예시 조건(아이템·조합 재료·챔피언 하나씩). 이번 데이터에서 고른다. */
    val searchExamples: StateFlow<List<DeckToken>> = repository.state
        .map { state -> (state as? FeedState.Ready)?.feed?.let(::exampleTokens).orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setListQuery(text: String) {
        _listQuery.value = text
    }

    /** 후보를 고르면 조건으로 쌓고 치던 글자를 비운다. 같은 조건은 두 번 쌓지 않는다. */
    fun addListToken(token: DeckToken) {
        val tokens = prefs.listTokens.value
        if (tokens.none { it.key == token.key }) prefs.setListTokens(tokens + token)
        _listQuery.value = ""
        _showOutside.value = false
    }

    fun removeListToken(token: DeckToken) {
        prefs.setListTokens(prefs.listTokens.value.filterNot { it.key == token.key })
        _showOutside.value = false
    }

    fun clearListTokens() {
        prefs.setListTokens(emptyList())
        _listQuery.value = ""
        _showOutside.value = false
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
        _showOutside.value = false
    }

    /** 조회 조건이 처음 상태와 다른지. '조건 초기화' 를 켜고 그 옆에 점을 찍는다. */
    val hasActiveFilter: StateFlow<Boolean> =
        combine(filterSpec, prefs.showHidden, prefs.grades, prefs.listTokens) { f, showHidden, grades, tokens ->
            queryIsModified(f.tiers, f.levels, f.onlyChina, f.editorialOnly, f.mainTrait, showHidden, grades, tokens)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** 필터 시트 안에서 켜 둔 조건 수(필터 버튼의 숫자 배지). 등급·검색 칩은 늘 보이는 자리에 있어 세지 않는다. */
    val activeSheetFilterCount: StateFlow<Int> =
        combine(filterSpec, prefs.showHidden) { f, showHidden ->
            sheetFilterCount(f.tiers, f.levels, f.onlyChina, f.editorialOnly, f.mainTrait, showHidden)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    // -- 검색 탭(결정 1 로 없앤다) -----------------------------------------------
    // 아래 query/selected/suggestions/results 는 @Deprecated SearchScreen 만 쓴다. 탭이 빠지면 T 가 함께 지운다.

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

    /** 새로고침 버튼(덱 + 도감 + 아이콘)이 도는 중인지. 목록 배너는 덱 동기화만 보는 [bannerState] 를 쓴다. */
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    /**
     * 목록 맨 위 배너(L9·V29·N3). 문제가 있을 때만 뜬다: 받는 중 → 마지막 동기화 실패 → 한 번도 확인 못 한 동봉 데이터 →
     * 36시간 넘게 확인 못 함. 정상이면 null(배너 없음). 하루 한 번 워커가 돌려도 [DeckRepository.syncing] 으로 같이 보인다.
     */
    val bannerState: StateFlow<ListBanner?> =
        combine(repository.state, repository.syncing, repository.lastError) { state, syncing, error ->
            val ready = state as? FeedState.Ready ?: return@combine null
            listBannerFor(
                fromBundle = ready.fromBundle,
                syncing = syncing,
                lastError = error,
                lastSyncedAt = ready.lastSyncedAt,
                version = ready.feed.version,
                now = System.currentTimeMillis(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun refresh() {
        if (_syncing.value) return
        viewModelScope.launch {
            _syncing.value = true
            val deckMessage = when (val outcome = repository.sync(force = true)) {
                is SyncResult.Updated -> {
                    // 사용자에게 보이는 패치는 글로벌 표기(18.2)다. 중국 패치 번호(16.18)는 체계가 다르다(규칙 7).
                    val patch = currentFeed?.version?.patchGlobal?.takeIf { it.isNotBlank() } ?: outcome.patch
                    "덱 데이터를 새로고침했습니다 (패치 $patch)"
                }
                SyncResult.UpToDate -> "이미 최신 데이터입니다"
                is SyncResult.Failed -> "새로고침하지 못했습니다 · ${outcome.reason}"
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

// ---------------------------------------------------------------------------
// 덱 목록 규칙(순수 함수 — GradeFilterTest·QueryPersistTest·DeckListLogicTest 가 검사한다)
// ---------------------------------------------------------------------------

/** 목록 화면 한 번 그리기에 필요한 값. */
data class DeckListResult(
    /** 필터·검색 조건·정렬·고정을 적용한 목록. */
    val decks: List<Deck> = emptyList(),
    /** 등급 조회 조건 때문에만 빠진 덱 수(다른 조건·검색 조건에는 맞는다). */
    val hiddenByGrade: Int = 0,
    /** 검색 조건에는 맞지만 등급·구간 조건 밖이라 빠진(또는 잠시 함께 보여 주는) 덱 수. 검색 조건이 없으면 0. */
    val outside: Int = 0,
    /** 뒤 숫자('12/77' 의 77): 이 구간에 나오는 덱 수. 조건·숨김과 무관하고 고정한 덱을 포함한다. */
    val total: Int = 0,
    /** 검색 조건에 아이템이 하나면 그 아이템을 핵심·대체로 쓰는 덱으로 나눈 목록(결정 1). */
    val itemSplit: ItemSplit? = null,
    /** 등급·구간 조건 밖 덱을 잠시 함께 보여 주는 중인지. */
    val showingOutside: Boolean = false,
)

/** 아이템 하나로 좁힌 목록을 핵심·대체로 나눈 것. 순서는 목록(정렬) 그대로다. */
data class ItemSplit(
    val item: String,
    val core: List<ItemSplitDeck>,
    val backup: List<ItemSplitDeck>,
)

/** [unitName] 은 그 덱에서 아이템을 드는 챔피언(카드가 크게 보여 준다). */
data class ItemSplitDeck(val deck: Deck, val unitName: String?)

/** 아이템 후보 한 줄의 핵심·대체 덱 수. */
data class ItemRoleCount(val core: Int, val backup: Int)

/** 목록 맨 위 배너 상태. */
sealed interface ListBanner {
    data object Syncing : ListBanner

    /** [reason] 은 [com.tftdeck.reader.data.SyncFailure] 사유. */
    data class Failed(val reason: String) : ListBanner

    /** 앱에 담긴 데이터를 아직 한 번도 확인하지 못했다. [patch] 는 글로벌 패치 표기(18.2). */
    data class Bundled(val patch: String) : ListBanner

    /** 마지막 확인이 36시간보다 오래됐다. [hours] 는 데이터가 만들어진 뒤 지난 시간. */
    data class Stale(val hours: Long) : ListBanner
}

/** 목록 조회 조건 한 벌(저장된 조건 + 화면 상태). [buildDeckList] 의 입력. */
internal data class DeckQuery(
    val bucket: String,
    val sort: DeckSort = DeckSort(),
    val tiers: Set<String> = emptySet(),
    val levels: Set<Int> = emptySet(),
    val onlyChina: Boolean = false,
    val editorialOnly: Boolean = false,
    val mainTrait: String? = null,
    val pinned: Set<String> = emptySet(),
    val hidden: Set<String> = emptySet(),
    val showHidden: Boolean = false,
    val tokens: List<DeckToken> = emptyList(),
    val grades: Set<String> = DeckKeys.GRADE_FILTER_DEFAULT,
    val showOutside: Boolean = false,
)

/**
 * 목록을 만든다. 같은 필터를 세 번 돌린다: 모든 조건(보이는 목록), 등급 조건만 뺀 것(등급 때문에 숨은 수),
 * 등급·구간 조건을 뺀 것(검색 조건이 있을 때 '조건 밖 N개'). 앞의 것이 뒤의 것에 포함되므로 빼면 그 조건 때문에 빠진 수다.
 * 조건 밖 덱을 함께 보는 중이면 마지막 목록을 정렬해 보여 준다(등급 정렬에서는 조건 밖 덱이 자연히 뒤로 간다).
 */
internal fun buildDeckList(search: DeckSearch, q: DeckQuery): DeckListResult {
    fun filtered(bucket: String?, grades: Set<String>?): List<Deck> = search.filterByTokens(
        search.filter(
            tiers = q.tiers,
            levels = q.levels,
            onlyChina = q.onlyChina,
            editorialOnly = q.editorialOnly,
            mainTrait = q.mainTrait,
            hidden = q.hidden,
            showHidden = q.showHidden,
            bucket = bucket,
            alwaysShow = q.pinned,
            grades = grades,
        ),
        q.tokens,
    )

    val strict = filtered(q.bucket, q.grades)
    val gradeFree = filtered(q.bucket, null)
    val searching = q.tokens.isNotEmpty()
    val relaxed = if (searching) filtered(null, null) else gradeFree
    val outside = if (searching) (relaxed.size - strict.size).coerceAtLeast(0) else 0
    val showingOutside = searching && q.showOutside && outside > 0
    val chosen = if (showingOutside) relaxed else strict
    val sorted = DeckSearch.pinFirst(DeckSearch.sort(chosen, q.sort.mode, q.bucket, q.sort.reversed), q.pinned)
    val total = if (showingOutside) {
        search.filter(showHidden = true).size
    } else {
        search.filter(bucket = q.bucket, alwaysShow = q.pinned, showHidden = true).size
    }
    return DeckListResult(
        decks = sorted,
        hiddenByGrade = (gradeFree.size - strict.size).coerceAtLeast(0),
        outside = outside,
        total = total,
        itemSplit = itemSplitFor(search, sorted, q.tokens),
        showingOutside = showingOutside,
    )
}

/** 검색 조건 가운데 아이템이 꼭 하나면 그 아이템으로 목록을 나눈다. 둘 이상이면 어느 쪽으로 나눌지 몰라 나누지 않는다. */
internal fun itemSplitFor(search: DeckSearch, decks: List<Deck>, tokens: List<DeckToken>): ItemSplit? {
    val item = tokens.singleOrNull { it.axis == SearchAxis.ITEM } ?: return null
    return splitByItem(item.name, decks, search.decksWithItem(item.name))
}

/**
 * [decks] 순서를 지킨 채 [item] 을 핵심(main)으로 쓰는 덱과 대체(backup)로만 쓰는 덱으로 나눈다(검색 탭의 핵심/대체 묶음).
 * 한 덱에서 여러 챔피언이 들면 핵심 쪽 챔피언을 고른다. 쓰임을 모르는 덱이 하나라도 있으면(이름이 바뀌어 id 로만 찾은 경우) null.
 */
internal fun splitByItem(item: String, decks: List<Deck>, hits: List<ItemHit>): ItemSplit? {
    if (decks.isEmpty()) return null
    val byDeck = hits.groupBy { it.deck.id }
    val core = mutableListOf<ItemSplitDeck>()
    val backup = mutableListOf<ItemSplitDeck>()
    for (deck in decks) {
        val deckHits = byDeck[deck.id] ?: return null
        val coreHit = deckHits.firstOrNull { it.isCore }
        val unit = (coreHit ?: deckHits.first()).unitName.takeIf { it.isNotBlank() }
        if (coreHit != null) core += ItemSplitDeck(deck, unit) else backup += ItemSplitDeck(deck, unit)
    }
    return ItemSplit(item, core, backup)
}

/** [within] 목록 안에서 아이템을 핵심으로 쓰는 덱 수와 대체로만 쓰는 덱 수. */
internal fun itemRoleCount(hits: List<ItemHit>, within: List<Deck>): ItemRoleCount {
    val ids = within.mapTo(HashSet()) { it.id }
    val byDeck = hits.filter { it.deck.id in ids }.groupBy { it.deck.id }
    val core = byDeck.values.count { deckHits -> deckHits.any { it.isCore } }
    return ItemRoleCount(core = core, backup = byDeck.size - core)
}

/** 정렬 메뉴에 보일 정렬. 표본순은 유지보수자용이라 뺀다(L8). */
internal fun visibleSortModes(): List<DeckSortMode> = DeckSortMode.entries.filter { it != DeckSortMode.SAMPLE }

/** 필터 시트 안의 켜진 조건 수: 편집 등급(v1)·마무리 레벨·중국 한정만·편집 덱만·주 특성·숨긴 덱 보기. */
internal fun sheetFilterCount(
    tiers: Set<String>,
    levels: Set<Int>,
    onlyChina: Boolean,
    editorialOnly: Boolean,
    mainTrait: String?,
    showHidden: Boolean,
): Int = listOf(tiers.isNotEmpty(), levels.isNotEmpty(), onlyChina, editorialOnly, mainTrait != null, showHidden).count { it }

/** 조회 조건이 처음 상태(등급 S·A·B, 필터·검색 칩·숨긴 덱 보기 없음)와 다른지. 구간·정렬은 조회 조건이 아니다. */
internal fun queryIsModified(
    tiers: Set<String>,
    levels: Set<Int>,
    onlyChina: Boolean,
    editorialOnly: Boolean,
    mainTrait: String?,
    showHidden: Boolean,
    grades: Set<String>,
    tokens: List<DeckToken>,
): Boolean = sheetFilterCount(tiers, levels, onlyChina, editorialOnly, mainTrait, showHidden) > 0 ||
    grades != DeckKeys.GRADE_FILTER_DEFAULT || tokens.isNotEmpty()

/** 배너가 '오래된 데이터'로 보는 기준. 하루 한 번 도는 워커가 한 번 놓쳐도(24h) 뜨지 않게 36시간. */
internal const val STALE_AFTER_MS = 36L * 60 * 60 * 1000

/**
 * 배너 상태. 우선순위: 받는 중 → 마지막 동기화 실패 → 한 번도 확인 못 한 동봉 데이터 → 36시간 넘게 확인 못 함 → 없음.
 * 동봉 데이터라도 이번 실행에서 원격과 같다고 확인했으면(lastSyncedAt 있음) '앱에 담긴 데이터' 배너를 띄우지 않는다.
 */
internal fun listBannerFor(
    fromBundle: Boolean,
    syncing: Boolean,
    lastError: String?,
    lastSyncedAt: Long?,
    version: FeedVersion,
    now: Long,
): ListBanner? = when {
    syncing -> ListBanner.Syncing
    lastError != null -> ListBanner.Failed(lastError)
    fromBundle && lastSyncedAt == null -> ListBanner.Bundled(version.patchGlobal.ifBlank { version.patch })
    lastSyncedAt != null && now - lastSyncedAt > STALE_AFTER_MS ->
        ListBanner.Stale(dataAgeHours(version.generatedAt, lastSyncedAt, now))
    else -> null
}

/** 데이터 나이(시간). 수집기가 적은 생성 시각을 쓰고, 읽을 수 없으면 마지막 확인 시각으로 잰다. */
private fun dataAgeHours(generatedAt: String, lastSyncedAt: Long, now: Long): Long {
    val made = generatedAt.takeIf { it.isNotBlank() }
        ?.let { runCatching { java.time.Instant.parse(it.trim()).toEpochMilli() }.getOrNull() }
        ?.takeIf { it <= now }
    return (now - (made ?: lastSyncedAt)) / HOUR_MS
}

private const val HOUR_MS = 60L * 60 * 1000

/**
 * 검색 줄 예시(결정 1, 검색 탭의 안내를 옮긴 것): 아이템 · 조합 재료 · 챔피언 하나씩. 이번 데이터에서 고르므로 시즌이 바뀌어도
 * 없는 이름을 보여 주지 않는다. 아이템은 1순위 캐리가 가장 많이 드는 것(모든 덱이 드는 탱커 아이템보다 역검색을 잘 보여 준다),
 * 조합 재료는 가장 많은 덱이 쓰는 것, 챔피언은 1순위 캐리로 가장 자주 나오는 것. 같은 수면 이름순.
 */
internal fun exampleTokens(feed: DeckFeed): List<DeckToken> {
    fun top(counts: Map<String, Int>): String? =
        counts.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).firstOrNull()?.key

    val mainCarries = feed.decks.mapNotNull { it.carries.firstOrNull() }
    val item = top(
        mainCarries.flatMap { unit -> unit.items.map { it.name }.distinct() }
            .filter { it.isNotBlank() && it in feed.index.item }
            .groupingBy { it }.eachCount()
    )
    val component = top(feed.index.component.mapValues { (_, decks) -> decks.distinct().size }.filterValues { it > 0 })
    val champion = top(
        mainCarries.map { it.name }
            .filter { it.isNotBlank() && it in feed.index.champion }
            .groupingBy { it }.eachCount()
    )
    fun idOf(list: List<CatalogEntry>, name: String): String? = list.firstOrNull { it.name == name }?.id
    return listOfNotNull(
        item?.let { DeckToken(SearchAxis.ITEM, it, idOf(feed.catalog.items, it)) },
        component?.let { DeckToken(SearchAxis.COMPONENT, it, idOf(feed.catalog.items, it)) },
        champion?.let { DeckToken(SearchAxis.CHAMPION, it, idOf(feed.catalog.champions, it)) },
    )
}
