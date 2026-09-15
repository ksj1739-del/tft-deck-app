package com.tftdeck.reader.ui.codex

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tftdeck.reader.data.AugmentRow
import com.tftdeck.reader.data.CodexPrefs
import com.tftdeck.reader.data.CodexRef
import com.tftdeck.reader.data.DeckRepository
import com.tftdeck.reader.data.FeedState
import com.tftdeck.reader.data.SearchAxis
import com.tftdeck.reader.data.StatsRepository
import com.tftdeck.reader.data.StatsState
import com.tftdeck.reader.data.StatsSyncResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 도감에서 덱으로 넘어가는 링크 한 줄. */
data class DeckLink(val id: String, val name: String, val tier: String)

/**
 * 도감 네 탭과 상세 화면의 상태.
 *
 * 수치는 전부 수집기가 계산해 둔 것을 조회만 한다. 여기서 하는 일은 필터·정렬과
 * 도감 → 덱 연결(덱 피드의 이름 인덱스)뿐이다. 파생 목록은 기본 디스패처에서 만든다.
 */
class CodexViewModel(app: Application) : AndroidViewModel(app) {

    private val stats = StatsRepository.get(app)
    private val deckRepository = DeckRepository.get(app)
    private val prefs = CodexPrefs.get(app)

    init {
        // 앱 시작 때 TftApp이 load()를 부르게 되지만(통합 단계), 그 전에도 도감이 비지 않도록
        // 여기서 한 번 올린다. 이미 Ready면 저장소가 건너뛴다.
        viewModelScope.launch { stats.load() }
    }

    val state: StateFlow<StatsState> = stats.state

    /** 아이콘 접두사. 덱 피드 버전에 있고, 아직 없으면 CommunityDragon 기본 경로. */
    val assetBase: StateFlow<String> = deckRepository.state
        .map { state ->
            (state as? FeedState.Ready)?.feed?.version?.assetBase?.takeIf { it.isNotBlank() } ?: DEFAULT_ASSET_BASE
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_ASSET_BASE)

    // -- 공통 ---------------------------------------------------------------

    /** 사용자가 고른 스코프(기본 KR 플래+). 파일에 없으면 각 표가 가까운 스코프로 대신한다. */
    val scope: StateFlow<String> = prefs.scope

    fun setScope(value: String) = prefs.setScope(value)

    private val _tab = MutableStateFlow(prefs.tab.coerceIn(0, TAB_COUNT - 1))
    val tab: StateFlow<Int> = _tab.asStateFlow()

    fun setTab(index: Int) {
        val value = index.coerceIn(0, TAB_COUNT - 1)
        _tab.value = value
        prefs.tab = value
    }

    // -- 챔피언 ---------------------------------------------------------------

    private val _championFilter = MutableStateFlow(ChampionFilter(sort = ChampionSort.fromKey(prefs.championSort)))
    val championFilter: StateFlow<ChampionFilter> = _championFilter.asStateFlow()

    fun toggleChampionCost(cost: Int) = _championFilter.update { it.copy(costs = it.costs.toggle(cost)) }

    fun setChampionTrait(traitId: String?) = _championFilter.update { it.copy(traitId = traitId) }

    fun setChampionQuery(text: String) = _championFilter.update { it.copy(query = text) }

    fun setChampionSort(sort: ChampionSort) {
        _championFilter.update { it.copy(sort = sort) }
        prefs.championSort = sort.key
    }

    /** null이면 아직 계산 중(로딩 표시). */
    val championTable: StateFlow<CodexTable<ChampionListRow>?> =
        combine(stats.state, prefs.scope, _championFilter) { state, scope, filter ->
            (state as? StatsState.Ready)?.let { CodexQuery.championTable(it.champions, scope, filter) }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val championTraitOptions: StateFlow<List<CodexRef>> = stats.state
        .map { state -> (state as? StatsState.Ready)?.let { CodexQuery.championTraitOptions(it.champions) }.orEmpty() }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // -- 특성 -----------------------------------------------------------------

    private val _traitFilter = MutableStateFlow(TraitFilter())
    val traitFilter: StateFlow<TraitFilter> = _traitFilter.asStateFlow()

    fun setTraitStyle(style: Int?) = _traitFilter.update { it.copy(style = style) }

    fun toggleTraitType(type: String) = _traitFilter.update { it.copy(types = it.types.toggle(type)) }

    fun setTraitByStage(on: Boolean) = _traitFilter.update { it.copy(byStage = on) }

    val traitTable: StateFlow<CodexTable<TraitListRow>?> =
        combine(stats.state, prefs.scope, _traitFilter) { state, scope, filter ->
            (state as? StatsState.Ready)?.let { CodexQuery.traitTable(it.traits, scope, filter) }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // -- 아이템 ---------------------------------------------------------------

    private val _itemSubTab = MutableStateFlow(prefs.itemSubTab.coerceIn(0, 1))
    val itemSubTab: StateFlow<Int> = _itemSubTab.asStateFlow()

    fun setItemSubTab(index: Int) {
        val value = index.coerceIn(0, 1)
        _itemSubTab.value = value
        prefs.itemSubTab = value
    }

    private val _itemFilter = MutableStateFlow(ItemFilter())
    val itemFilter: StateFlow<ItemFilter> = _itemFilter.asStateFlow()

    fun setItemKind(kind: String?) = _itemFilter.update { it.copy(kind = kind) }

    fun toggleItemComponent(id: String) =
        _itemFilter.update { it.copy(components = CodexQuery.toggleComponent(it.components, id)) }

    fun clearItemComponents() = _itemFilter.update { it.copy(components = emptyList()) }

    fun setItemQuery(text: String) = _itemFilter.update { it.copy(query = text) }

    val itemTable: StateFlow<CodexTable<ItemListRow>?> =
        combine(stats.state, prefs.scope, _itemFilter) { state, scope, filter ->
            (state as? StatsState.Ready)?.let { CodexQuery.itemTable(it.items, scope, filter) }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // -- 증강 -----------------------------------------------------------------

    private val _augmentSubTab = MutableStateFlow(prefs.augmentSubTab.coerceIn(0, 1))
    val augmentSubTab: StateFlow<Int> = _augmentSubTab.asStateFlow()

    fun setAugmentSubTab(index: Int) {
        val value = index.coerceIn(0, 1)
        _augmentSubTab.value = value
        prefs.augmentSubTab = value
    }

    private val _augmentFilter = MutableStateFlow(AugmentFilter())
    val augmentFilter: StateFlow<AugmentFilter> = _augmentFilter.asStateFlow()

    fun setAugmentRarity(rarity: String?) = _augmentFilter.update { it.copy(rarity = rarity) }

    fun setAugmentTag(tag: String?) = _augmentFilter.update { it.copy(tag = tag) }

    fun setAugmentQuery(text: String) = _augmentFilter.update { it.copy(query = text) }

    val augmentRows: StateFlow<List<AugmentRow>?> =
        combine(stats.state, _augmentFilter) { state, filter ->
            (state as? StatsState.Ready)?.let { CodexQuery.augmentRows(it.augments, filter) }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val augmentTierGroups: StateFlow<List<AugmentTierGroup>?> = stats.state
        .map { state -> (state as? StatsState.Ready)?.let { CodexQuery.augmentTierGroups(it.augments) } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // -- 덱 연결 ---------------------------------------------------------------

    /** 지금 앱에 있는 덱 id -> 링크. 화면은 이것을 구독해 덱 피드가 바뀌면 다시 그린다. */
    val deckLinks: StateFlow<Map<String, DeckLink>> = deckRepository.state
        .map { state ->
            (state as? FeedState.Ready)?.feed?.decks.orEmpty().associate { it.id to DeckLink(it.id, it.name, it.tier) }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /**
     * 덱 피드의 이름 인덱스로 찾은 덱 id. 덱 피드 v1에도 있는 필드만 쓴다.
     * 아이템은 누가 드는지까지 담긴 목록이라 덱 id만 모은다.
     */
    fun deckIdsFor(axis: SearchAxis, name: String): List<String> {
        val index = (deckRepository.state.value as? FeedState.Ready)?.feed?.index ?: return emptyList()
        return when (axis) {
            SearchAxis.ITEM -> index.item[name].orEmpty().map { it.deck }.distinct()
            SearchAxis.COMPONENT -> index.component[name].orEmpty()
            SearchAxis.CHAMPION -> index.champion[name].orEmpty()
            SearchAxis.TRAIT -> index.trait[name].orEmpty()
            SearchAxis.AUGMENT -> index.augment[name].orEmpty()
        }
    }

    fun deckName(id: String): String? =
        deckLinks.value[id]?.name
            ?: (deckRepository.state.value as? FeedState.Ready)?.feed?.decks?.firstOrNull { it.id == id }?.name

    /**
     * 도감 항목이 들어가는 덱. 통계 파일이 적어 둔 덱 id(수집일 기준)와 지금 덱 피드의
     * 이름 인덱스를 합친다. 지금 앱에 없는 덱은 눌러도 열 수 없으므로 뺀다.
     */
    fun decksFor(explicitIds: List<String>, axis: SearchAxis, name: String): List<DeckLink> {
        val links = deckLinks.value.ifEmpty {
            (deckRepository.state.value as? FeedState.Ready)?.feed?.decks.orEmpty()
                .associate { it.id to DeckLink(it.id, it.name, it.tier) }
        }
        val ids = LinkedHashSet<String>()
        explicitIds.filterTo(ids) { it in links }
        deckIdsFor(axis, name).filterTo(ids) { it in links }
        return ids.mapNotNull { links[it] }
    }

    // -- 갱신 ---------------------------------------------------------------

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    /** 도감 데이터만 갱신한다. 덱 데이터 갱신은 설정 화면(AppViewModel)이 맡는다. */
    fun refresh() {
        if (_syncing.value) return
        viewModelScope.launch {
            _syncing.value = true
            _syncMessage.value = when (val outcome = stats.sync()) {
                is StatsSyncResult.Updated -> "도감 데이터를 갱신했습니다"
                StatsSyncResult.UpToDate -> "도감 데이터가 이미 최신입니다"
                is StatsSyncResult.Failed -> "도감 갱신 실패: ${outcome.reason}"
            }
            _syncing.value = false
        }
    }

    fun consumeSyncMessage() {
        _syncMessage.value = null
    }

    private fun <T> Set<T>.toggle(value: T): Set<T> = if (contains(value)) this - value else this + value

    companion object {
        const val TAB_COUNT = 4
    }
}
