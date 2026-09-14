package com.tftdeck.reader.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tftdeck.reader.data.Deck
import com.tftdeck.reader.data.DeckRepository
import com.tftdeck.reader.data.DeckSearch
import com.tftdeck.reader.data.FeedState
import com.tftdeck.reader.data.ItemHit
import com.tftdeck.reader.data.ProfileRepository
import com.tftdeck.reader.data.ProfileState
import com.tftdeck.reader.data.SearchAxis
import com.tftdeck.reader.data.SyncResult
import com.tftdeck.reader.data.Suggestion
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

    // -- 덱 목록 필터 -------------------------------------------------------

    private val _tierFilter = MutableStateFlow<Set<String>>(emptySet())
    val tierFilter: StateFlow<Set<String>> = _tierFilter.asStateFlow()

    private val _levelFilter = MutableStateFlow<Set<Int>>(emptySet())
    val levelFilter: StateFlow<Set<Int>> = _levelFilter.asStateFlow()

    private val _onlyChina = MutableStateFlow(false)
    val onlyChina: StateFlow<Boolean> = _onlyChina.asStateFlow()

    val decks: StateFlow<List<Deck>> =
        combine(engine, _tierFilter, _levelFilter, _onlyChina) { search, tiers, levels, china ->
            search?.filter(tiers, levels, china).orEmpty()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 필터에 쓸 티어 목록. 실제 데이터에 있는 것만 보여준다. */
    val availableTiers: StateFlow<List<String>> = repository.state
        .map { state ->
            (state as? FeedState.Ready)?.feed?.decks
                ?.map { it.tier }?.distinct()
                ?.sortedBy { tier -> TIER_SORT.indexOf(tier).takeIf { it >= 0 } ?: 99 }
                .orEmpty()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val availableLevels: StateFlow<List<Int>> = repository.state
        .map { state ->
            (state as? FeedState.Ready)?.feed?.decks
                ?.mapNotNull { it.finalLevel }?.distinct()?.sorted().orEmpty()
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

    fun clearFilters() {
        _tierFilter.value = emptySet()
        _levelFilter.value = emptySet()
        _onlyChina.value = false
    }

    val hasActiveFilter: StateFlow<Boolean> =
        combine(_tierFilter, _levelFilter, _onlyChina) { tiers, levels, china ->
            tiers.isNotEmpty() || levels.isNotEmpty() || china
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

    // -- 덱 상세 ------------------------------------------------------------

    fun deck(id: String): Deck? =
        (repository.state.value as? FeedState.Ready)?.feed?.decks?.firstOrNull { it.id == id }

    /** 레벨별 배치는 id 참조라 catalog에서 이름/아이콘을 찾아야 한다. */
    fun catalogChampion(id: String) =
        (repository.state.value as? FeedState.Ready)?.feed?.catalog?.champions?.firstOrNull { it.id == id }

    fun catalogItem(id: String) =
        (repository.state.value as? FeedState.Ready)?.feed?.catalog?.items?.firstOrNull { it.id == id }

    // -- 동기화 -------------------------------------------------------------

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    fun refresh() {
        if (_syncing.value) return
        viewModelScope.launch {
            _syncing.value = true
            _syncMessage.value = when (val outcome = repository.sync(force = true)) {
                is SyncResult.Updated -> "패치 ${outcome.patch} · 덱 ${outcome.deckCount}개로 갱신했습니다"
                SyncResult.UpToDate -> "이미 최신입니다"
                is SyncResult.Failed -> "갱신 실패: ${outcome.reason}"
            }
            _syncing.value = false
        }
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
