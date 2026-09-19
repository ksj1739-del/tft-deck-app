package com.tftdeck.reader.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.tftdeck.reader.data.CatalogIndex
import com.tftdeck.reader.data.DeckKeys
import com.tftdeck.reader.data.DeckPrefs
import com.tftdeck.reader.data.DeckRepository
import com.tftdeck.reader.data.DeckSearch
import com.tftdeck.reader.data.DeckSortMode
import com.tftdeck.reader.data.FeedState
import com.tftdeck.reader.data.ProfileState
import com.tftdeck.reader.ui.bucketShortLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 게임 위에 뜨는 창의 내용.
 *
 * 게임을 가리면 안 되므로 기본은 접힌 칩이고, 눌러야 펼쳐진다.
 * 펼치면 **전체 덱 목록**이 먼저 나온다 — 인게임에서 뭘 갈지 고르는 게 주 용도라
 * 덱 하나만 띄워 두는 것보다 목록이 기본이어야 한다.
 * 목록에서 덱을 고르면 그 덱 요약으로 들어가고, 뒤로 누르면 목록으로 돌아온다.
 *
 * 펼친 창의 모양은 칩이 놓인 사분면([quadrantFlow])을 따른다(WP-O1 — F1·F6): 머리줄은 칩과 같은 변(위·아래),
 * '접기'는 칩 쪽 끝, 티어 카드는 칩 반대쪽. 서비스는 '접기' 가운데가 칩 가운데에 오도록 창을 놓고 칩 쪽 모서리를 창 gravity 로
 * 잡아, 요약↔목록↔검색으로 크기가 바뀌어도 '접기'가 제자리에 있다. 드문 기능은 머리줄 ⋯ 메뉴(OverlayMenu)에 있다 —
 * 패널 안 Box 로 그리고, 열린 동안([menuOpenFlow]) 본문은 덮개가 받아 메뉴만 닫는다.
 *
 * 서비스(OverlayService)가 넘겨주는 데이터는 덱 목록과 아이콘 접두사뿐이다. 구간·고정·숨김은
 * 앱과 같은 [DeckPrefs] 싱글턴에서, 구간 이름과 catalog 는 [DeckRepository] 에서 직접 읽는다 —
 * 같은 프로세스라 앱에서 구간을 바꾸면 여기도 곧바로 바뀐다.
 *
 * 목록 위 검색 줄은 유닛·시너지·아이템·증강·사용자 지정 글자를 칩으로 쌓아 목록을 좁힌다(AND). 조건은 앱 목록과 따로
 * 이 창 안에서만 기억한다. 창은 평소 포커스를 받지 않으므로 검색창을 누르면 [onSearchStart] 로 서비스에 포커스를
 * 요청하고, 서비스가 [searchingFlow] 를 켜 준 뒤에야 입력칸이 생긴다. 끝낼 때는 [onSearchEnd].
 *
 * 보던 자리(고른 덱 [selectedIdFlow], 덱별 레벨 [levelsFlow], 목록 스크롤 [listAnchorFlow])는 서비스가 들고 있다.
 * 접으면 펼침 화면이 통째로 컴포지션에서 빠지므로 여기서 remember 하면 펼칠 때마다 처음으로 돌아갔다.
 */
@Composable
fun OverlayContent(
    dataFlow: StateFlow<OverlayData?>,
    profileFlow: StateFlow<ProfileState>,
    selectedIdFlow: StateFlow<String?>,
    levelsFlow: StateFlow<Map<String, Int>>,
    listAnchorFlow: StateFlow<OverlayListAnchor?>,
    expandedFlow: StateFlow<Boolean>,
    wideFlow: StateFlow<Boolean>,
    showProfileFlow: StateFlow<Boolean>,
    searchingFlow: StateFlow<Boolean>,
    onToggleExpand: () -> Unit,
    onToggleWide: () -> Unit,
    onToggleProfile: () -> Unit,
    onRefreshProfile: () -> Unit,
    onSelectDeck: (String?) -> Unit,
    onSelectLevel: (deckId: String, level: Int) -> Unit,
    onListAnchor: (OverlayListAnchor?) -> Unit,
    onSearchStart: () -> Unit,
    onSearchEnd: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onOpenApp: () -> Unit,
    onClose: () -> Unit,
    quadrantFlow: StateFlow<Quadrant> = TopLeftQuadrant,
    menuOpenFlow: StateFlow<Boolean> = MenuClosed,
    onMenuOpenChange: (Boolean) -> Unit = {},
    onExpandToList: () -> Unit = onToggleExpand,
    onChipSize: (IntSize) -> Unit = {},
    onListStatus: (count: Int, bucket: String) -> Unit = { _, _ -> },
) {
    val data by dataFlow.collectAsState()
    val selectedId by selectedIdFlow.collectAsState()
    val deckLevels by levelsFlow.collectAsState()
    val expanded by expandedFlow.collectAsState()
    val profileState by profileFlow.collectAsState()
    val wide by wideFlow.collectAsState()
    val showProfile by showProfileFlow.collectAsState()
    val searching by searchingFlow.collectAsState()
    val quadrant by quadrantFlow.collectAsState()
    val menuOpen by menuOpenFlow.collectAsState()

    // 검색 조건(고른 조건·치는 글자). 접었다 펴도 남도록 펼침 분기 밖에서 기억한다(OverlaySearchBar.kt).
    val searchState = rememberOverlaySearchState(searching, onSearchEnd, onListAnchor)

    val context = LocalContext.current
    val prefs = remember { DeckPrefs.get(context) }
    val repository = remember { DeckRepository.get(context) }
    val savedBucket by prefs.bucket.collectAsState()
    val pinned by prefs.pinned.collectAsState()
    val hidden by prefs.hidden.collectAsState()
    // 덱 등급 조회 조건(S~D 여러 개). 앱 목록과 같은 값이라 어느 쪽에서 바꿔도 함께 바뀐다.
    val grades by prefs.grades.collectAsState()
    val feedState by repository.state.collectAsState()
    val feed = (feedState as? FeedState.Ready)?.feed
    val buckets = feed?.buckets.orEmpty()
    // 저장해 둔 구간이 이번 피드에 없으면 피드의 기본 구간으로 본다.
    val bucket = if (feed == null || buckets.isEmpty() || savedBucket in buckets) savedBucket else feed.defaultBucket
    val catalog = remember(feed) { feed?.let { CatalogIndex(it.catalog) } }
    val metatftCompared = feed?.version?.metatftCompared ?: true
    // 검색 줄의 후보·조건 매칭. 앱과 같은 규칙(초성·줄임말·영문, 이름 인덱스)을 쓴다.
    val search = remember(feed) { feed?.let { DeckSearch(it) } }

    val allDecks = data?.decks.orEmpty()
    if (allDecks.isEmpty()) return
    val assetBase = data?.assetBase.orEmpty()
    // 목록: 숨긴 덱과 그 구간 등급이 없는 덱은 빼고(고정한 덱은 남긴다) → 켜 둔 등급의 덱만(고정한 덱은 건너뛴다) →
    // 검색 조건을 모두 만족하는 덱만 → 그 구간 등급순으로, 고정한 덱을 맨 위로.
    // 등급 조건은 앱처럼 구간이 있는 데이터(v2)에서만 쓴다.
    val gradeFilterOn = buckets.isNotEmpty()
    val listed = remember(allDecks, bucket, pinned, hidden) {
        allDecks.filter { it.id !in hidden && (it.listedIn(bucket) || it.id in pinned) }
    }
    val graded = remember(listed, bucket, pinned, grades, gradeFilterOn) {
        if (gradeFilterOn) overlayGradeFiltered(listed, bucket, pinned, grades) else listed
    }
    val decks = remember(graded, searchState.tokens, search, bucket, pinned) {
        DeckSearch.pinFirst(
            DeckSearch.sort(search?.filterByTokens(graded, searchState.tokens) ?: graded, DeckSortMode.GRADE, bucket),
            pinned,
        )
    }
    // 목록이 비면 무엇이 막았는지 말해 준다 — 꺼 둔 등급 때문이면 켜면 보일 덱 수까지.
    val emptyMessage = if (decks.isNotEmpty()) "" else {
        val withoutGrades = search?.filterByTokens(listed, searchState.tokens) ?: listed
        overlayEmptyListMessage(
            hasTokens = searchState.tokens.isNotEmpty(),
            off = if (gradeFilterOn) offGrades(grades) else emptyList(),
            hiddenByGrade = withoutGrades.size - decks.size,
        )
    }
    // 등급 칸을 누르면 앱과 함께 쓰는 조건이 바뀐다. 바뀐 목록은 맨 위부터(기억한 자리를 먼저 지운다).
    // 마지막 하나는 DeckPrefs 가 끄지 않으므로 그때는 자리도 그대로 둔다.
    val toggleGrade: (String) -> Unit = { grade ->
        val next = if (grade in grades) grades - grade else grades + grade
        if (next.isNotEmpty()) onListAnchor(null)
        prefs.toggleGrade(grade)
    }
    // 후보는 지금 좁혀진 목록 기준으로 센다(골랐을 때 남는 덱 수).
    val candidates = searchState.rememberCandidates(search, listed, decks, searching)
    // 고른 덱은 숨긴 덱이어도 찾는다(덱 상세의 '게임 위에 띄우기'로 바로 열 수 있다).
    val selected = allDecks.firstOrNull { it.id == selectedId }

    // 알림 문구('덱 12 · 골드~에메 · 오버레이 표시 중')가 칩과 같은 수·구간을 말하도록 서비스에 알린다.
    val reportStatus by rememberUpdatedState(onListStatus)
    LaunchedEffect(decks.size, bucket) { reportStatus(decks.size, bucket) }

    val dragModifier = Modifier.pointerInput(Unit) {
        detectDragGestures(
            onDragEnd = onDragEnd,
            // 끌다가 취소돼도 창은 이미 옮겨졌으니 그 자리를 기억한다.
            onDragCancel = onDragEnd,
            onDrag = { change, offset ->
                change.consume()
                onDrag(offset.x, offset.y)
            },
        )
    }

    if (!expanded) {
        CollapsedChip(
            deck = selected,
            bucket = bucket,
            deckCount = decks.size,
            assetBase = assetBase,
            // 칩 크기는 서비스가 펼친 창을 칩 가운데에 맞출 때 쓴다(펼친 동안에는 창이 패널이라 칩을 잴 수 없다).
            modifier = dragModifier.onSizeChanged(onChipSize),
            onExpand = onToggleExpand,
            onExpandToList = onExpandToList,
        )
        return
    }

    // 창은 WRAP_CONTENT 라 그냥 두면 덱 패널이 폭을 다 먹고 프로필 카드가 찌그러진다.
    // 화면 폭에서 카드 자리를 먼저 떼고 남는 만큼만 덱 패널에 준다.
    BoxWithConstraints {
        // 티어 카드를 꺼 두면 그 자리를 덱 패널이 쓴다.
        val hasProfile = showProfile && profileState.profileOrNull != null
        val profileWidth = PROFILE_WIDTH
        val deckMax = if (hasProfile) {
            (maxWidth - profileWidth - 14.dp).coerceIn(200.dp, if (wide) 380.dp else 300.dp)
        } else {
            if (wide) 380.dp else 300.dp
        }
        // 패널 높이는 화면 높이에 맞춘다. 가로 화면(높이 약 360~410dp)에서는 목록·요약이 고정 상한(300dp 안팎)을 다 쓰면
        // 머리줄·검색줄과 합쳐 화면을 넘겨 아래가 잘렸다. 이제 넘치는 만큼 목록·요약이 줄고 그 안에서 스크롤한다.
        val availableHeight = if (constraints.hasBoundedHeight) maxHeight else UNBOUNDED_AREA_HEIGHT
        val panelMaxHeight = (availableHeight - PANEL_SCREEN_MARGIN).coerceAtLeast(PANEL_MIN_HEIGHT)
        // 가로 화면에서 검색하는 동안에는 머리줄을 접는다 — 키보드가 화면의 60% 남짓을 덮어 후보 자리가 모자라다.
        val headerShown = !(searching && hideHeaderWhileSearching(availableHeight.value))
        // 칩이 아래 절반이면 머리줄을 패널 아래에(패널은 위로 자란다), 오른쪽 절반이면 머리줄 좌우를 뒤집는다.
        val headerOnTop = quadrant.isTop
        val mirrored = !quadrant.isLeft
        // 메뉴는 머리줄의 ⋯ 로만 열리므로 머리줄이 숨은 동안(가로 검색)에는 그리지 않는다.
        val showMenu = menuOpen && headerShown
        val closeMenu = { onMenuOpenChange(false) }
        val headerGap = HEADER_HEIGHT_DP.dp

        val header: @Composable () -> Unit = {
            PanelHeader(
                selected = selected,
                bucket = bucket,
                // 검색 조건이 있으면 좁혀진 수 / 검색 전 수(등급 조건까지 적용한 목록). 등급 조건은 검색 줄 옆 칸이 보여 준다.
                countText = overlayCountText(decks.size, graded.size, hasTokens = searchState.tokens.isNotEmpty()),
                bucketName = if (buckets.isNotEmpty()) bucketShortLabel(bucket) else null,
                metatftCompared = metatftCompared,
                mirrored = mirrored,
                menuOpen = showMenu,
                dragModifier = dragModifier,
                onCollapse = onToggleExpand,
                onBack = { onSelectDeck(null) },
                onToggleMenu = { onMenuOpenChange(!menuOpen) },
                onTitleTap = if (showMenu) closeMenu else null,
            )
        }

        // 본문. 검색 줄은 머리줄이 어느 변에 있든 패널 위쪽에 둔다 — 가로 검색에서 창이 키보드를 피해 화면 위까지 올라가도
        // 검색 줄과 첫 후보들이 보이게(아래에 두면 키보드 밑으로 들어간다).
        val body: @Composable ColumnScope.() -> Unit = {
            if (selected == null) {
                OverlaySearchBar(
                    tokens = searchState.tokens,
                    query = searchState.query,
                    searching = searching,
                    onQueryChange = { searchState.query = it },
                    onRemoveToken = searchState::removeToken,
                    onClearAll = searchState::clearAll,
                    onSubmit = { searchState.onSubmit(candidates) },
                    onSearchStart = onSearchStart,
                    grades = grades.takeIf { gradeFilterOn },
                    onToggleGrade = toggleGrade,
                )
                // 목록·후보·요약은 머리줄·검색줄을 놓고 남는 높이만 쓴다(weight, fill = false — 짧으면 그만큼만).
                if (searching && searchState.query.isNotBlank()) {
                    // 치는 동안에는 목록 대신 후보를 검색창 바로 아래에 둔다(키보드가 아래를 가려도 위쪽 몇 줄은 보이게).
                    CandidateList(candidates, assetBase, searchState::pickCandidate, Modifier.weight(1f, fill = false))
                } else {
                    DeckListView(
                        decks = decks,
                        // 조건(검색 칩·등급)이 바뀌면 목록을 새로 맨 위부터.
                        filterKey = searchState.tokens to grades,
                        emptyMessage = emptyMessage,
                        bucket = bucket,
                        pinned = pinned,
                        metatftCompared = metatftCompared,
                        assetBase = assetBase,
                        wide = wide,
                        anchorFlow = listAnchorFlow,
                        onAnchor = onListAnchor,
                        modifier = Modifier.weight(1f, fill = false),
                    ) { onSelectDeck(it.id) }
                }
            } else {
                DeckSummaryView(
                    deck = selected,
                    catalog = catalog,
                    assetBase = assetBase,
                    wide = wide,
                    savedLevel = deckLevels[selected.id],
                    onSelectLevel = { onSelectLevel(selected.id, it) },
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }

        // 내 티어와 최근 등수. 덱 패널의 칩 반대쪽에 붙는다 — '접기'가 창의 칩 쪽 모서리에 있어야 칩 자리와 겹친다.
        val profileCard: @Composable () -> Unit = {
            if (showProfile) {
                profileState.profileOrNull?.let { profile ->
                    OverlayProfileCard(
                        profile = profile,
                        stale = profileState is ProfileState.Failed,
                        refreshing = profileState is ProfileState.Loading,
                        onRefresh = onRefreshProfile,
                        modifier = Modifier.width(profileWidth),
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = if (headerOnTop) Alignment.Top else Alignment.Bottom,
        ) {
            if (mirrored) profileCard()
            // 패널과 ⋯ 메뉴. 메뉴가 패널보다 길면 이 상자가 늘어나도 패널은 머리줄 쪽 변에 붙어 있다.
            Box(contentAlignment = cornerAlignment(headerOnTop, alignEnd = mirrored)) {
                Column(
                    modifier = Modifier
                        .widthIn(max = deckMax)
                        .heightIn(max = panelMaxHeight)
                        .clip(RoundedCornerShape(PANEL_CORNER))
                        .background(OverlayScrim)
                        .border(1.dp, OverlayBorder, RoundedCornerShape(PANEL_CORNER)),
                ) {
                    if (headerShown && headerOnTop) header()
                    body()
                    if (headerShown && !headerOnTop) header()
                }
                if (showMenu) {
                    // 메뉴 밖(검색 줄·목록·요약) 누름은 메뉴만 닫는다. 머리줄은 덮지 않는다 — 접기·←·⋯ 는 한 번에 눌린다.
                    OverlayMenuScrim(
                        onDismiss = closeMenu,
                        modifier = Modifier
                            .matchParentSize()
                            .padding(top = if (headerOnTop) headerGap else 0.dp, bottom = if (headerOnTop) 0.dp else headerGap),
                    )
                    OverlayMenu(
                        wide = wide,
                        onToggleWide = onToggleWide,
                        profileConnected = profileState.profileOrNull != null,
                        showProfile = showProfile,
                        onToggleProfile = onToggleProfile,
                        teamCode = selected?.teamCode?.code?.takeIf { it.isNotBlank() },
                        bucket = bucket,
                        buckets = DeckKeys.BUCKET_ORDER.filter { it in buckets },
                        onSelectBucket = { key ->
                            prefs.setBucket(key)
                            closeMenu()
                        },
                        onOpenApp = {
                            closeMenu()
                            onOpenApp()
                        },
                        onClose = {
                            closeMenu()
                            onClose()
                        },
                        maxHeight = (panelMaxHeight - headerGap).coerceAtLeast(MENU_MIN_HEIGHT),
                        // ⋯ 바로 옆(머리줄이 위면 아래로, 아래면 위로)에 붙인다. ⋯ 는 칩 반대쪽 끝이다.
                        modifier = Modifier
                            .align(cornerAlignment(headerOnTop, alignEnd = !mirrored))
                            .padding(top = if (headerOnTop) headerGap else 0.dp, bottom = if (headerOnTop) 0.dp else headerGap),
                    )
                }
            }
            if (!mirrored) profileCard()
        }
    }
}

/** 패널 상자 안의 모서리 정렬: 머리줄 쪽 변(위·아래) × 왼쪽·오른쪽. */
private fun cornerAlignment(top: Boolean, alignEnd: Boolean): Alignment = when {
    top && !alignEnd -> Alignment.TopStart
    top -> Alignment.TopEnd
    !alignEnd -> Alignment.BottomStart
    else -> Alignment.BottomEnd
}

/** [OverlayContent] 의 칩 사분면·메뉴 기본값(서비스가 늘 넘기므로 미리보기 등에서만 쓰인다). */
private val TopLeftQuadrant: StateFlow<Quadrant> = MutableStateFlow(Quadrant.TopLeft)
private val MenuClosed: StateFlow<Boolean> = MutableStateFlow(false)

/** 프로필 카드 폭. 덱 패널 폭을 계산할 때도 쓰인다. */
private val PROFILE_WIDTH = 124.dp

/** 아주 낮은 화면에서도 머리줄과 한두 줄은 보이게 하는 패널 최소 높이. */
private val PANEL_MIN_HEIGHT = 160.dp

/** 높이 제약이 없을 때(창 측정에서는 생기지 않는다) 쓰는 영역 높이. 세로 폰 정도. */
private val UNBOUNDED_AREA_HEIGHT = 800.dp

/** 패널 모서리(MASTER 규칙 4: 카드 12dp). */
private val PANEL_CORNER = 12.dp

/** 메뉴가 스크롤로라도 두세 줄은 보이게 하는 최소 높이. */
private val MENU_MIN_HEIGHT = 120.dp
