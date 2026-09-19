package com.tftdeck.reader.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import com.tftdeck.reader.ui.theme.FloaColors
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tftdeck.reader.data.BuildupPick
import com.tftdeck.reader.data.BuildupPlanner
import com.tftdeck.reader.data.CatalogIndex
import com.tftdeck.reader.data.Deck
import com.tftdeck.reader.data.DeckKeys
import com.tftdeck.reader.data.DeckPrefs
import com.tftdeck.reader.data.DeckRepository
import com.tftdeck.reader.data.DeckSearch
import com.tftdeck.reader.data.DeckSortMode
import com.tftdeck.reader.data.DeckToken
import com.tftdeck.reader.data.FeedState
import com.tftdeck.reader.data.ProfileState
import com.tftdeck.reader.data.TokenCandidate
import com.tftdeck.reader.data.Unit as DeckUnit
import com.tftdeck.reader.ui.bucketLabel
import com.tftdeck.reader.ui.components.ThreeStarMark
import com.tftdeck.reader.ui.components.TokenCandidateRow
import com.tftdeck.reader.ui.components.TokenSearchField
import com.tftdeck.reader.ui.copyToClipboard
import com.tftdeck.reader.ui.formatAvg
import com.tftdeck.reader.ui.gradeColor
import com.tftdeck.reader.ui.iconUrl
import com.tftdeck.reader.ui.tierColor
import com.tftdeck.reader.ui.traitStyleColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter

/**
 * 게임 위에 뜨는 창의 내용.
 *
 * 게임을 가리면 안 되므로 기본은 접힌 칩이고, 눌러야 펼쳐진다.
 * 펼치면 **전체 덱 목록**이 먼저 나온다 — 인게임에서 뭘 갈지 고르는 게 주 용도라
 * 덱 하나만 띄워 두는 것보다 목록이 기본이어야 한다.
 * 목록에서 덱을 고르면 그 덱 요약으로 들어가고, 뒤로 누르면 목록으로 돌아온다.
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
) {
    val data by dataFlow.collectAsState()
    val selectedId by selectedIdFlow.collectAsState()
    val deckLevels by levelsFlow.collectAsState()
    val expanded by expandedFlow.collectAsState()
    val profileState by profileFlow.collectAsState()
    val wide by wideFlow.collectAsState()
    val showProfile by showProfileFlow.collectAsState()
    val searching by searchingFlow.collectAsState()

    // 검색 조건. 접었다 펴도 남도록 펼침 분기 밖에서 기억한다(창을 닫으면 사라진다). 앱 목록의 조건과는 따로다.
    var tokens by remember { mutableStateOf(emptyList<DeckToken>()) }
    var query by remember { mutableStateOf("") }
    // 창이 떨어지면(닫기·감지 끄기) 검색 조건이 사라진다. 조건으로 좁힌 목록에서 기억한 자리는 다음에 붙을 조건 없는
    // 목록과 맞지 않으니 함께 지운다 — 다시 띄우면 맨 위부터.
    val tokensNow by rememberUpdatedState(tokens)
    val clearAnchor by rememberUpdatedState(onListAnchor)
    DisposableEffect(Unit) {
        onDispose { if (tokensNow.isNotEmpty()) clearAnchor(null) }
    }
    // 검색이 끝나면(후보 선택·바깥 누름·뒤로 가기·접기 등) 치다 만 글자는 버린다.
    LaunchedEffect(searching) { if (!searching) query = "" }

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
    val decks = remember(graded, tokens, search, bucket, pinned) {
        DeckSearch.pinFirst(
            DeckSearch.sort(search?.filterByTokens(graded, tokens) ?: graded, DeckSortMode.GRADE, bucket),
            pinned,
        )
    }
    // 목록이 비면 무엇이 막았는지 말해 준다 — 꺼 둔 등급 때문이면 켜면 보일 덱 수까지.
    val emptyMessage = if (decks.isNotEmpty()) "" else {
        val withoutGrades = search?.filterByTokens(listed, tokens) ?: listed
        overlayEmptyListMessage(
            hasTokens = tokens.isNotEmpty(),
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
    val candidates = remember(search, query, tokens, decks, searching) {
        if (!searching) emptyList() else search?.suggestTokens(query, tokens, within = decks, limit = OVERLAY_CANDIDATES).orEmpty()
    }
    // 조건이 바뀌면 좁혀진 목록을 맨 위부터 보여 준다. 목록이 새 조건으로 다시 그려지기 전에 기억해 둔 자리부터 지운다.
    val changeTokens: (List<DeckToken>) -> Unit = { next ->
        if (next != tokens) {
            onListAnchor(null)
            tokens = next
        }
    }
    val pickCandidate: (TokenCandidate) -> Unit = { candidate ->
        if (tokens.none { it.key == candidate.token.key }) changeTokens(tokens + candidate.token)
        query = ""
        // 고르면 검색을 끝낸다 — 키보드를 내리고 창을 다시 포커스를 받지 않게 해 게임 조작을 돌려준다.
        onSearchEnd()
    }
    // 고른 덱은 숨긴 덱이어도 찾는다(덱 상세의 '게임 위에 띄우기'로 바로 열 수 있다).
    val selected = allDecks.firstOrNull { it.id == selectedId }

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
            modifier = dragModifier,
            onExpand = onToggleExpand,
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

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top,
    ) {
    Column(
        modifier = Modifier
            .widthIn(max = deckMax)
            .heightIn(max = panelMaxHeight)
            .clip(RoundedCornerShape(14.dp))
            .background(OverlayScrim)
            .border(1.dp, OverlayBorder, RoundedCornerShape(14.dp)),
    ) {
        if (headerShown) {
            PanelHeader(
                selected = selected,
                bucket = bucket,
                // 검색 조건이 있으면 좁혀진 수 / 검색 전 수(등급 조건까지 적용한 목록). 등급 조건은 검색 줄 옆 칸이 보여 준다.
                countText = overlayCountText(decks.size, graded.size, hasTokens = tokens.isNotEmpty()),
                bucketName = buckets.takeIf { it.isNotEmpty() }
                    ?.let { buckets[bucket]?.label?.takeIf { label -> label.isNotBlank() } ?: bucketLabel(bucket) },
                profileConnected = profileState.profileOrNull != null,
                showProfile = showProfile,
                wide = wide,
                dragModifier = dragModifier,
                onBack = { onSelectDeck(null) },
                onCycleBucket = { prefs.setBucket(DeckSearch.nextBucket(bucket, buckets.keys)) },
                onCopyCode = selected?.teamCode?.let { code -> { copyToClipboard(context, "TFT 덱 코드", code.code) } },
                onToggleProfile = onToggleProfile,
                onToggleWide = onToggleWide,
                onCollapse = onToggleExpand,
                onOpenApp = onOpenApp,
                onClose = onClose,
            )
        }

        if (selected == null) {
            OverlaySearchBar(
                tokens = tokens,
                query = query,
                searching = searching,
                onQueryChange = { query = it },
                onRemoveToken = { token -> changeTokens(tokens.filterNot { it.key == token.key }) },
                onClearAll = {
                    changeTokens(emptyList())
                    query = ""
                },
                // IME 의 검색 키는 맨 위 후보를 고른다. 친 글자가 없으면 검색만 끝낸다.
                onSubmit = { candidates.firstOrNull()?.let(pickCandidate) ?: onSearchEnd() },
                onSearchStart = onSearchStart,
                grades = grades.takeIf { gradeFilterOn },
                onToggleGrade = toggleGrade,
            )
            // 목록·후보·요약은 머리줄·검색줄을 놓고 남는 높이만 쓴다(weight, fill = false — 짧으면 그만큼만).
            if (searching && query.isNotBlank()) {
                // 치는 동안에는 목록 대신 후보를 검색창 바로 아래에 둔다(키보드가 아래를 가려도 위쪽 몇 줄은 보이게).
                CandidateList(candidates, assetBase, pickCandidate, Modifier.weight(1f, fill = false))
            } else {
                DeckListView(
                    decks = decks,
                    // 조건(검색 칩·등급)이 바뀌면 목록을 새로 맨 위부터.
                    filterKey = tokens to grades,
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

        // 내 티어와 최근 등수. 덱 패널 오른쪽에 붙는다.
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
    }
}

/** 프로필 카드 폭. 덱 패널 폭을 계산할 때도 쓰인다. */
private val PROFILE_WIDTH = 124.dp

/**
 * 덱 요약 머리줄에 별칭을 둘 최소 폭 — 버튼을 다 놓고 남는 폭이 이보다 좁으면(세로 화면 + 티어 카드) 별칭이
 * 한두 글자만 남아 두지 않는다. 가로 화면(게임 중, 패널 300dp 이상)에서는 남는다.
 */
private val HEADER_ALIAS_MIN_WIDTH = 56.dp

/** 통계 등급은 등급색, 편집 등급으로 대신 보여 줄 때는 편집 등급색. 등급이 없으면 흐린 색. */
private fun gradeTint(deck: Deck, bucket: String): Color {
    if (deck.isGlobalOnly) return OverlayAccent
    val grade = deck.gradeFor(bucket) ?: return OverlayMuted
    return if (deck.showsEditorialGrade(bucket)) tierColor(grade) else gradeColor(grade)
}

/** 오버레이는 칸이 좁아 metatft 전용 덱을 등급 글자 대신 'G' 한 글자로 구분한다. */
private fun gradeText(deck: Deck, bucket: String): String =
    if (deck.isGlobalOnly) "G" else deck.gradeFor(bucket) ?: "-"

// ---------------------------------------------------------------------------
// 접힌 상태
// ---------------------------------------------------------------------------

@Composable
private fun CollapsedChip(
    deck: Deck?,
    bucket: String,
    deckCount: Int,
    assetBase: String,
    modifier: Modifier,
    onExpand: () -> Unit,
) {
    Row(
        modifier = modifier
            // 덱을 고르지 않은 칩('덱 87')은 글자만 있어 30dp 남짓이었다. 게임 중 한 번에 눌리도록 높이를 맞춘다.
            .heightIn(min = CHIP_MIN_HEIGHT)
            .clip(RoundedCornerShape(22.dp))
            .background(OverlayScrim)
            .border(1.dp, OverlayBorder, RoundedCornerShape(22.dp))
            .clickable(onClick = onExpand)
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        // 게임 연동이 켜져 있으면 TFT 감지 초록 점 / 판 종료 직후 '6등 −35 LP' 배지가 칩 맨 앞에 붙는다.
        // 아무 일도 없으면 아무것도 그리지 않아 칩 폭이 그대로다.
        GameStatusBadge(OverlayService.gameStatus)
        val carry = deck?.carry
        if (deck != null && carry != null) {
            val tint = gradeTint(deck, bucket)
            AsyncImage(
                model = iconUrl(assetBase, carry.icon),
                contentDescription = carry.name,
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, tint, CircleShape),
            )
            Text(
                text = gradeText(deck, bucket),
                color = tint,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        } else {
            // 고른 덱이 없으면 목록으로 들어간다는 뜻으로 개수만 보여 준다.
            Text(
                text = "덱 $deckCount",
                color = OverlayAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 전체 덱 목록 — 펼쳤을 때 기본 화면
// ---------------------------------------------------------------------------

/**
 * 목록 위의 작은 검색 줄. 고른 조건은 입력칸 안에 '니달리 ×' 칩으로 쌓인다.
 * 창이 포커스를 받기 전에는 안내 글자만 그리고, 누르면 서비스에 포커스를 요청한다([onSearchStart]).
 * 서비스가 검색을 켜 주고 창이 실제로 포커스를 받은 뒤에 입력칸이 포커스를 잡고 키보드를 띄운다 —
 * 그 전에 키보드를 부르면 포커스 없는 창이라 무시된다.
 *
 * 오른쪽 끝에 등급 조회 조건 칸(S A B C D, [GradeToggles])을 붙인다. 목록 높이를 먹는 새 줄을 만들지 않으려고
 * 검색 줄과 한 줄을 나눠 쓴다. [grades] 가 null 이면(구간이 없는 옛 데이터) 칸을 두지 않는다.
 */
@Composable
private fun OverlaySearchBar(
    tokens: List<DeckToken>,
    query: String,
    searching: Boolean,
    onQueryChange: (String) -> Unit,
    onRemoveToken: (DeckToken) -> Unit,
    onClearAll: () -> Unit,
    onSubmit: () -> Unit,
    onSearchStart: () -> Unit,
    grades: Set<String>?,
    onToggleGrade: (String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(searching, windowFocused) {
        if (searching && windowFocused && runCatching { focusRequester.requestFocus() }.isSuccess) {
            keyboard?.show()
        }
    }
    Row(
        modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        TokenSearchField(
            tokens = tokens,
            query = query,
            onQueryChange = onQueryChange,
            onRemoveToken = onRemoveToken,
            onClearAll = onClearAll,
            onSubmit = onSubmit,
            editing = searching,
            onStartEditing = onSearchStart,
            focusRequester = focusRequester,
            compact = true,
            // 등급 칸과 높이를 맞춘다(한 줄일 때). 칩이 여러 줄로 늘면 등급 칸은 첫 줄에 붙어 있다.
            modifier = Modifier
                .weight(1f)
                .heightIn(min = GRADE_TOGGLE_HEIGHT),
        )
        if (grades != null) {
            Spacer(Modifier.width(4.dp))
            GradeToggles(grades, onToggleGrade)
        }
    }
}

/**
 * 등급 조회 조건 칸 S A B C D. 앱 목록의 등급 칩과 같은 값이다(여기서 바꾸면 앱에도 남는다).
 * 켜진 칸은 그 등급 색으로 채우고 테두리를 두르며, 꺼진 칸은 비운 채 흐린 글자에 가로줄을 긋는다 —
 * 색을 구분하기 어려워도 채움·가로줄로 읽힌다. 마지막 하나는 끌 수 없다(DeckPrefs.toggleGrade).
 */
@Composable
private fun GradeToggles(grades: Set<String>, onToggle: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(GRADE_TOGGLE_GAP)) {
        DeckKeys.GRADE_FILTER_ALL.forEach { grade ->
            val on = grade in grades
            val color = gradeColor(grade)
            val shape = RoundedCornerShape(6.dp)
            Box(
                modifier = Modifier
                    .size(width = GRADE_TOGGLE_WIDTH, height = GRADE_TOGGLE_HEIGHT)
                    .clip(shape)
                    .then(if (on) Modifier.background(color.copy(alpha = 0.2f)) else Modifier)
                    .border(1.dp, if (on) color.copy(alpha = 0.85f) else OverlayBorder, shape)
                    .toggleable(value = on, role = Role.Checkbox, onValueChange = { onToggle(grade) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    grade,
                    color = if (on) color else OverlayMuted,
                    fontSize = 11.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    textDecoration = if (on) null else TextDecoration.LineThrough,
                )
            }
        }
    }
}

/** 치는 동안 검색창 아래에 뜨는 후보. 맨 끝은 친 글자 그대로의 '사용자 지정' 후보다. */
@Composable
private fun CandidateList(
    candidates: List<TokenCandidate>,
    assetBase: String,
    onPick: (TokenCandidate) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (candidates.isEmpty()) {
        Text(
            "더할 조건이 없습니다. 이미 고른 조건입니다.",
            color = OverlayMuted,
            fontSize = 10.sp,
            modifier = modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        )
        return
    }
    LazyColumn(
        modifier = modifier.heightIn(max = CANDIDATE_LIST_MAX),
        contentPadding = PaddingValues(vertical = 2.dp),
    ) {
        items(candidates, key = { it.token.key }) { candidate ->
            TokenCandidateRow(candidate, assetBase, onClick = { onPick(candidate) }, compact = true)
        }
    }
}

@Composable
private fun DeckListView(
    decks: List<Deck>,
    filterKey: Any,
    emptyMessage: String,
    bucket: String,
    pinned: Set<String>,
    metatftCompared: Boolean,
    assetBase: String,
    wide: Boolean,
    anchorFlow: StateFlow<OverlayListAnchor?>,
    onAnchor: (OverlayListAnchor?) -> Unit,
    modifier: Modifier = Modifier,
    onPick: (Deck) -> Unit,
) {
    if (decks.isEmpty()) {
        // 무엇이 막았는지(꺼 둔 등급·검색 조건)와 푸는 곳을 말한다([overlayEmptyListMessage]).
        Text(
            emptyMessage,
            color = OverlaySubtext,
            fontSize = 10.sp,
            modifier = modifier.padding(10.dp),
        )
        return
    }
    // 목록 자리는 서비스가 기억한다 — 접었다 펴도, 덱을 봤다 돌아와도, 창이 다시 붙거나 서비스가 다시 떠도 보던 덱에서 이어진다.
    // 조건(검색 칩·등급, [filterKey])이 바뀌면 새로 만든다: 바꾸는 쪽이 기억한 자리를 먼저 지우므로 새 목록을 맨 위부터 보여 준다.
    // 기억한 자리는 흘려 받은 값이 아니라 지금 값(.value)으로 읽는다 — 지운 직후 다시 그릴 때 옛 값을 보지 않게.
    val listState = remember(filterKey) {
        val start = overlayListStart(decks.map { it.id }, anchorFlow.value)
        LazyListState(start.index, start.offset)
    }
    val reportAnchor by rememberUpdatedState(onAnchor)
    // 스크롤이 멈출 때마다 맨 위에 보이는 덱을 기억한다(움직이는 동안 프레임마다 저장하지 않는다).
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { moving -> !moving }
            .collect {
                val key = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.key as? String ?: return@collect
                reportAnchor(OverlayListAnchor(key, listState.firstVisibleItemScrollOffset))
            }
    }
    LazyColumn(
        state = listState,
        // 게임 화면을 너무 가리지 않도록 높이를 제한하고 나머지는 스크롤한다. 가로 화면에서는 남는 높이만 쓴다(modifier).
        modifier = modifier.heightIn(max = DECK_LIST_MAX),
        contentPadding = PaddingValues(vertical = 3.dp),
    ) {
        items(decks, key = { it.id }) { deck ->
            DeckRow(
                deck = deck,
                bucket = bucket,
                pinned = deck.id in pinned,
                chinaOnly = metatftCompared && deck.isOnlyInChina,
                assetBase = assetBase,
                wide = wide,
                onClick = { onPick(deck) },
            )
        }
    }
}

/**
 * 목록 한 줄. 글만 읽어도 어떤 덱인지 알 수 있게 별칭(굵게)과 짧은 설명을 얼굴 위에 둔다.
 *  - [등급] 별칭 … [고정·중국] / 설명(두 줄까지, 넘치면 …) / 얼굴(좁게 22dp · 넓게 24dp)
 * 설명은 '빠른 8레벨 · 니달리·시비르 캐리' 같은 짧은 운영·캐리 요약이다. 한 줄 말줄임으로는 캐리 이름이 잘려
 * 두 줄까지 보이게 했고, 넓게 볼 때도 별칭 옆에 붙여 한 줄로 자르던 것을 별칭 아래로 내려 같은 두 줄을 쓴다.
 * 설명 글자는 10sp, 색은 바탕 대비 7:1 이상([OverlaySubtext])으로 게임 위에서도 읽힌다. 줄 간격을 좁혀 두 줄이어도 줄이 크게 두꺼워지지 않는다.
 * 평균 등수 같은 수치는 게임을 가려서 여전히 뺀다(앱의 덱 상세에서 본다).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeckRow(
    deck: Deck,
    bucket: String,
    pinned: Boolean,
    chinaOnly: Boolean,
    assetBase: String,
    wide: Boolean,
    onClick: () -> Unit,
) {
    val summary = deck.displaySummary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TierLabel(deck, bucket)
            Text(
                deck.displayAlias,
                color = OverlayText,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (pinned) {
                Spacer(Modifier.width(3.dp))
                PinMark()
            }
            if (chinaOnly) {
                Spacer(Modifier.width(4.dp))
                ChinaDot()
            }
        }
        if (summary.isNotBlank()) {
            Text(
                summary,
                color = OverlaySubtext,
                fontSize = 10.sp,
                lineHeight = 12.5.sp,
                maxLines = SUMMARY_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = TIER_COLUMN),
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(start = TIER_COLUMN, top = 1.dp),
        ) {
            deck.units.forEach { unit -> Face(unit, assetBase, if (wide) 24.dp else 22.dp) }
        }
    }
}

@Composable
private fun TierLabel(deck: Deck, bucket: String) {
    Text(
        text = gradeText(deck, bucket),
        color = gradeTint(deck, bucket),
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = Modifier.width(TIER_COLUMN),
    )
}

/** 목록 줄 앞의 등급 칸 폭. 설명·얼굴 줄도 이만큼 들여 별칭과 줄을 맞춘다. */
private val TIER_COLUMN = 22.dp

/** 목록 줄 설명의 최대 줄 수. 짧은 운영·캐리 요약이라 두 줄이면 넉넉하고, 더 길면 말줄임으로 줄 높이를 지킨다. */
private const val SUMMARY_MAX_LINES = 2

/**
 * 덱 목록 높이 상한(세로 화면 기준). 예전 330dp 에서 검색 줄 높이만큼 뺐다.
 * 가로 화면처럼 화면이 낮으면 패널 상한([PANEL_SCREEN_MARGIN])에 맞춰 이보다 줄어든다.
 */
private val DECK_LIST_MAX = 300.dp

/** 덱 요약 높이 상한(세로 화면 기준). 가로 화면에서는 남는 높이만 쓰고 그 안에서 스크롤한다. */
private val SUMMARY_MAX = 330.dp

/** 후보 목록 높이 상한. 검색창 바로 아래라 키보드가 올라와도 위쪽 몇 줄은 보인다. */
private val CANDIDATE_LIST_MAX = 200.dp

/** 오버레이 후보 수(사용자 지정 후보는 따로 하나 더 붙는다). */
private const val OVERLAY_CANDIDATES = 8

/**
 * 펼친 패널 높이 = 화면(창이 놓이는 영역) 높이 − 이 여유. 창 자리는 서비스가 화면 안으로 맞추므로(clampOverlayPosition)
 * 패널이 영역보다 작기만 하면 머리줄부터 바닥까지 다 보인다. 여유는 위아래 가장자리에 딱 붙지 않게 하는 몫이다.
 */
private val PANEL_SCREEN_MARGIN = 8.dp

/** 아주 낮은 화면에서도 머리줄과 한두 줄은 보이게 하는 패널 최소 높이. */
private val PANEL_MIN_HEIGHT = 160.dp

/** 높이 제약이 없을 때(창 측정에서는 생기지 않는다) 쓰는 영역 높이. 세로 폰 정도. */
private val UNBOUNDED_AREA_HEIGHT = 800.dp

/** 접힌 칩 최소 높이. 게임 중 엄지로 한 번에 눌리는 크기. */
private val CHIP_MIN_HEIGHT = 40.dp

/**
 * 머리줄 버튼의 누르는 칸. 아이콘(16dp)은 그대로 작게 두고 칸을 머리줄 높이만큼 키웠다(예전 26dp 원).
 * 폭은 버튼 일곱 개(덱 요약: 뒤로·앱 열기·티어 카드·넓게·코드 복사·접기·닫기)가 가장 좁은 패널(세로 360dp 폰 +
 * 티어 카드 ≈ 222dp)에도 들어가도록 28dp 로 둔다.
 */
private val HEADER_BUTTON_WIDTH = 28.dp
private val HEADER_BUTTON_HEIGHT = 34.dp

/** 닫기를 다른 버튼과 떼어 두는 틈. 자주 누르는 접기 바로 옆이라 손가락이 미끄러져도 닫기에 덜 닿게. */
private val CLOSE_GAP = 6.dp

/** 닫기를 한 번 누른 뒤 두 번째 누름을 기다리는 시간. 지나면 원래 X 로 돌아간다. */
private const val CLOSE_CONFIRM_MS = 3_000L

/** 레벨 칩 높이. 게임 중 판마다 누르는 칩이라 글자보다 칸을 크게 둔다(예전 약 20dp). */
private val LEVEL_CHIP_HEIGHT = 30.dp

/**
 * 등급 조회 조건 칸 하나(S~D). 검색 줄과 한 줄을 나눠 쓰므로 다섯 칸이 약 130dp 에 들어가게 폭을 24dp 로 두고,
 * 높이는 검색 줄과 같은 28dp. 칸 사이를 3dp 벌려 옆 등급이 같이 눌리지 않게 한다.
 */
private val GRADE_TOGGLE_WIDTH = 24.dp
private val GRADE_TOGGLE_HEIGHT = 28.dp
private val GRADE_TOGGLE_GAP = 3.dp

@Composable
private fun PinMark() {
    Icon(
        Icons.Default.PushPin,
        contentDescription = "고정한 덱",
        tint = OverlayAccent,
        modifier = Modifier
            .padding(end = 3.dp)
            .size(10.dp),
    )
}

/** metatft에 없는 덱. 좁은 창이라 배지 대신 점 하나로 줄였다. */
@Composable
private fun ChinaDot() {
    Box(
        Modifier
            .size(5.dp)
            .clip(CircleShape)
            .background(OverlayAmber)
    )
}

// ---------------------------------------------------------------------------
// 덱 하나
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeckSummaryView(
    deck: Deck,
    catalog: CatalogIndex?,
    assetBase: String,
    wide: Boolean,
    savedLevel: Int?,
    onSelectLevel: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 레벨 칩: 빌드업(글로벌·중국·작가 단계)이 있는 레벨. 기본 선택 규칙은 덱 상세와 같다.
    val levels = remember(deck) { BuildupPlanner.levels(deck) }
    val defaultLevel = remember(deck, levels) { BuildupPlanner.defaultLevel(deck, levels) }
    // 고른 레벨은 서비스가 덱마다 기억한다 — 접었다 펴도, 서비스가 다시 떠도 그 레벨이다. 고른 적 없거나
    // 그 레벨 칩이 사라졌으면(패치로 빌드업이 바뀜) 기본 레벨.
    val level = resolveOverlayLevel(savedLevel, levels, defaultLevel)
    val pick = level?.let { BuildupPlanner.topPick(deck, it) }

    Column(
        modifier = modifier
            .heightIn(max = SUMMARY_MAX)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 9.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 덱 이름과 평균 등수·픽률·승률은 게임을 가려서 오버레이에서는 뺐다(앱의 덱 상세에서 본다).
        if (wide) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                deck.traits.take(6).forEach { trait ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(traitStyleColor(trait.style).copy(alpha = 0.22f))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    ) {
                        Text(
                            "${trait.count} ${trait.name}",
                            color = traitStyleColor(trait.style),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }

        if (levels.isNotEmpty()) {
            LevelChips(
                levels = levels,
                selected = level,
                dim = { BuildupPlanner.isLowSample(deck, it) },
                onSelect = onSelectLevel,
            )
        }

        if (pick != null) {
            // 그 레벨의 1순위 구성(글로벌 → 중국 → 작가). 보드 그림은 게임을 가려서 그리지 않는다.
            BuildFaces(pick, deck, catalog, assetBase, wide)
            Text(BuildupPlanner.caption(deck, pick.level), color = OverlayMuted, fontSize = 10.sp)
        } else if (wide) {
            deck.units.forEach { unit -> UnitLine(unit, assetBase) }
        } else {
            // 얼굴만. 이름과 아이템은 게임 화면을 가려서 좁게 볼 때는 뺀다.
            // 프로필 카드가 붙으면 패널이 좁아지므로 고정 칸수 대신 폭에 맞춰 접는다.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                deck.units.forEach { unit -> Face(unit, assetBase, 32.dp) }
            }
        }

        if (wide && deck.componentOrder.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("재료", color = OverlayMuted, fontSize = 10.sp)
                Spacer(Modifier.width(5.dp))
                deck.componentOrder.take(5).forEach { item ->
                    AsyncImage(
                        model = iconUrl(assetBase, item.icon),
                        contentDescription = item.name,
                        modifier = Modifier
                            .padding(end = 3.dp)
                            .size(18.dp)
                            .clip(RoundedCornerShape(3.dp)),
                    )
                }
            }
        }

    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LevelChips(levels: List<Int>, selected: Int?, dim: (Int) -> Boolean, onSelect: (Int) -> Unit) {
    // 판마다 누르는 칩이라 칸을 키우고(높이 30dp) 칩 사이를 벌려 옆 레벨이 눌리지 않게 한다.
    // 가로 화면 패널(300dp)에서는 4~10렙 일곱 개가 한 줄에 들어간다.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        levels.forEach { lv ->
            val on = lv == selected
            val shape = RoundedCornerShape(10.dp)
            Box(
                Modifier
                    .heightIn(min = LEVEL_CHIP_HEIGHT)
                    .alpha(if (dim(lv) && !on) 0.5f else 1f)
                    .clip(shape)
                    .background(if (on) OverlayAccent.copy(alpha = 0.28f) else OverlayHeader)
                    .border(1.dp, if (on) OverlayAccent else OverlayBorder, shape)
                    .clickable { onSelect(lv) }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${lv}렙",
                    color = if (on) OverlayAccent else OverlayText,
                    fontSize = 11.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

/**
 * 빌드업 구성의 얼굴 줄. 빌드업에는 유닛 id 만 있어서, 같은 id 가 최종 보드에 있으면
 * 넓게 볼 때 그 유닛의 아이템을 빌려 와 누가 무엇을 들게 되는지 함께 보여 준다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BuildFaces(pick: BuildupPick, deck: Deck, catalog: CatalogIndex?, assetBase: String, wide: Boolean) {
    val finalById = remember(deck) { deck.units.associateBy { it.id } }
    val carryId = pick.option?.carryId ?: deck.carryId
    // 작가 단계에는 칸마다 성급·캐리 표시가 있다. 통계 옵션에는 없어서 그때는 캐리 id 만 본다.
    val placements = remember(pick) { pick.stage?.units.orEmpty().associateBy { it.id } }
    if (wide) {
        pick.units.forEach { id ->
            val entry = catalog?.unit(id)
            val unit = finalById[id]
            val name = entry?.name ?: unit?.name ?: id
            val carry = placements[id]?.carry ?: (id == carryId)
            Row(verticalAlignment = Alignment.CenterVertically) {
                BuildFace(
                    icon = entry?.icon ?: unit?.icon,
                    name = name,
                    cost = entry?.cost ?: unit?.cost,
                    assetBase = assetBase,
                    size = 26.dp,
                    carry = carry,
                    pet = catalog?.isPet(id) == true || unit?.isPet == true,
                    star = placements[id]?.star ?: 1,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = name,
                    color = if (carry) OverlayAccent else OverlayText,
                    fontSize = 11.sp,
                    fontWeight = if (carry) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                unit?.items?.takeIf { it.isNotEmpty() }?.let { items ->
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        items.forEach { item ->
                            AsyncImage(
                                model = iconUrl(assetBase, item.icon),
                                contentDescription = item.name,
                                modifier = Modifier
                                    .size(17.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                            )
                        }
                    }
                }
            }
        }
    } else {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            pick.units.forEach { id ->
                val entry = catalog?.unit(id)
                val unit = finalById[id]
                BuildFace(
                    icon = entry?.icon ?: unit?.icon,
                    name = entry?.name ?: unit?.name ?: id,
                    cost = entry?.cost ?: unit?.cost,
                    assetBase = assetBase,
                    size = 32.dp,
                    carry = placements[id]?.carry ?: (id == carryId),
                    pet = catalog?.isPet(id) == true || unit?.isPet == true,
                    star = placements[id]?.star ?: 1,
                )
            }
        }
    }
}

/** 빌드업 얼굴 하나. 성급을 아는 경우(작가 단계)에만 3성 별을 얹는다. 소환물에는 별이 없다. */
@Composable
private fun BuildFace(
    icon: String?,
    name: String,
    cost: Int?,
    assetBase: String,
    size: Dp,
    carry: Boolean,
    pet: Boolean,
    star: Int,
) {
    Box(contentAlignment = Alignment.TopCenter) {
        IdFace(icon, name, cost, assetBase, size, carry = carry, pet = pet)
        if (!pet && star >= 3) {
            ThreeStarMark(6.sp, Modifier.offset(y = (-2).dp))
        }
    }
}

/** 넓게 볼 때 최종 보드 유닛 한 줄: 얼굴 · 이름 · 아이템. */
@Composable
private fun UnitLine(unit: DeckUnit, assetBase: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Face(unit, assetBase, 26.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            text = unit.name + if (!unit.isPet && unit.star >= 3) " ★★★" else "",
            color = if (unit.carry) OverlayAccent else OverlayText,
            fontSize = 11.sp,
            fontWeight = if (unit.carry) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            unit.items.forEach { item ->
                AsyncImage(
                    model = iconUrl(assetBase, item.icon),
                    contentDescription = item.name,
                    modifier = Modifier
                        .size(17.dp)
                        .clip(RoundedCornerShape(3.dp)),
                )
            }
        }
    }
}

/**
 * 챔피언 얼굴 하나.
 * 캐리는 강조 테두리, 3성은 별로 구분한다 — 글자 없이 구분되는 정보만 남겼다. 소환물은 회색 테두리에 별이 없다.
 */
@Composable
private fun Face(unit: DeckUnit, assetBase: String, size: Dp) {
    Box(contentAlignment = Alignment.TopCenter) {
        IdFace(unit.icon, unit.name, unit.cost, assetBase, size, carry = unit.carry, pet = unit.isPet)
        if (!unit.isPet && unit.star >= 3) {
            ThreeStarMark(6.sp, Modifier.offset(y = (-2).dp))
        }
    }
}

@Composable
private fun IdFace(
    icon: String?,
    name: String,
    cost: Int?,
    assetBase: String,
    size: Dp,
    carry: Boolean = false,
    pet: Boolean = false,
) {
    // 이미지가 오기 전에도 칸이 비지 않게 코스트색을 먼저 깐다.
    val placeholder = remember(cost) { ColorPainter(costTint(cost).copy(alpha = 0.35f)) }
    val borderColor = when {
        pet -> OverlayMuted
        carry -> OverlayAccent
        else -> costTint(cost)
    }
    AsyncImage(
        model = iconUrl(assetBase, icon),
        contentDescription = name,
        placeholder = placeholder,
        error = placeholder,
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(4.dp))
            .border(if (carry && !pet) 1.8.dp else 1.dp, borderColor, RoundedCornerShape(4.dp)),
    )
}

// ---------------------------------------------------------------------------
// 머리줄
// ---------------------------------------------------------------------------

/**
 * 펼친 패널의 머리줄. 전체가 끌기 손잡이다(본문은 스크롤해야 해서 손잡이를 머리줄로 나눴다).
 *
 * 제목 칸(목록: 옮기기·덱 수·구간 / 덱: 뒤로·등급·별칭)은 버튼을 다 놓고 남는 폭만 쓴다 — 예전에는 제목이 먼저 폭을
 * 차지해 좁은 패널(세로 화면 + 티어 카드)에서 접기·앱 열기·닫기가 밀려 사라졌다.
 * 버튼 순서: 앱 열기 · 티어 카드 · 넓게 · 코드 복사 · 접기 | 닫기. 가장 자주 누르는 접기를 끝에 두고, 잘못 누르면 게임을
 * 떠나는 앱 열기는 접기에서 먼 쪽에 둔다. 닫기는 틈을 두고 맨 끝에 두며 두 번 눌러야 닫힌다([CloseButton]).
 */
@Composable
private fun PanelHeader(
    selected: Deck?,
    bucket: String,
    countText: String,
    bucketName: String?,
    profileConnected: Boolean,
    showProfile: Boolean,
    wide: Boolean,
    dragModifier: Modifier,
    onBack: () -> Unit,
    onCycleBucket: () -> Unit,
    onCopyCode: (() -> Unit)?,
    onToggleProfile: () -> Unit,
    onToggleWide: () -> Unit,
    onCollapse: () -> Unit,
    onOpenApp: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = dragModifier
            .fillMaxWidth()
            .background(OverlayHeader)
            .padding(start = 4.dp, end = 3.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            if (selected == null) {
                Icon(
                    Icons.Default.DragIndicator,
                    contentDescription = "옮기기",
                    tint = OverlayMuted,
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(countText, color = OverlayText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                // 구간 이름. 누를 때마다 전체 → 마스터+ → 다이아+ → 골드~에메랄드 → 골드 이하로 넘어가고 앱에도 저장된다.
                if (bucketName != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        bucketName,
                        color = OverlayAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clip(RoundedCornerShape(6.dp))
                            .background(OverlayAccent.copy(alpha = 0.15f))
                            .clickable(onClick = onCycleBucket)
                            .padding(horizontal = 7.dp, vertical = 5.dp),
                    )
                }
            } else {
                HeaderBtn(Icons.AutoMirrored.Filled.ArrowBack, "목록으로", onClick = onBack)
                Text(
                    text = gradeText(selected, bucket),
                    color = gradeTint(selected, bucket),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                // 별칭은 남는 폭이 넉넉할 때만 둔다 — 한두 글자만 남은 별칭은 읽히지 않고 버튼만 좁힌다.
                BoxWithConstraints(Modifier.weight(1f)) {
                    if (maxWidth >= HEADER_ALIAS_MIN_WIDTH) {
                        Text(
                            text = selected.displayAlias,
                            color = OverlayText,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 5.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(4.dp))
        HeaderBtn(Icons.AutoMirrored.Filled.OpenInNew, "앱 열기", onClick = onOpenApp)
        // 티어 카드만 따로 켜고 끈다. 전적을 연결하지 않았으면 끌 카드가 없으니 숨긴다.
        if (profileConnected) {
            HeaderBtn(
                Icons.Default.EmojiEvents,
                if (showProfile) "티어 카드 숨기기" else "티어 카드 보기",
                tint = if (showProfile) OverlayAccent else OverlayMuted,
                onClick = onToggleProfile,
            )
        }
        HeaderBtn(
            if (wide) Icons.Default.CloseFullscreen else Icons.Default.OpenInFull,
            if (wide) "좁게 보기" else "넓게 보기",
            onClick = onToggleWide,
        )
        // 덱 코드 복사는 본문 버튼 대신 머리줄의 작은 아이콘으로 두어 패널을 얇게 한다.
        onCopyCode?.let { HeaderBtn(Icons.Default.ContentCopy, "덱 코드 복사", tint = OverlayAccent, onClick = it) }
        HeaderBtn(Icons.Default.UnfoldLess, "접기", onClick = onCollapse)
        Spacer(Modifier.width(CLOSE_GAP))
        CloseButton(onClose)
    }
}

/** 머리줄 버튼. 아이콘은 작게(16dp) 두고 누르는 칸은 머리줄 높이만큼 키웠다([HEADER_BUTTON_WIDTH]×[HEADER_BUTTON_HEIGHT]). */
@Composable
private fun HeaderBtn(icon: ImageVector, label: String, tint: Color = OverlayMuted, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = HEADER_BUTTON_WIDTH, height = HEADER_BUTTON_HEIGHT)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(16.dp))
    }
}

/**
 * 닫기. 게임 중 잘못 눌러 닫히면 되살리기 번거롭다 — 수동으로 띄운 창은 서비스가 끝나 앱으로 가야 하고, 감지 중이면
 * 이번 판 동안 자동 표시가 막힌다(알림의 '다시 띄우기'로만 되살린다). 그래서 처음 누르면 빨간 '닫기'로 바뀌고
 * [CLOSE_CONFIRM_MS] 안에 한 번 더 눌러야 닫힌다. 그냥 두면 X 로 돌아간다.
 */
@Composable
private fun CloseButton(onClose: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(CLOSE_CONFIRM_MS)
            armed = false
        }
    }
    if (!armed) {
        HeaderBtn(Icons.Default.Close, "닫기", onClick = { armed = true })
        return
    }
    Box(
        modifier = Modifier
            .height(HEADER_BUTTON_HEIGHT)
            .clip(RoundedCornerShape(8.dp))
            .background(OverlayDanger)
            .clickable(onClickLabel = "오버레이 닫기", onClick = onClose)
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("닫기", color = OverlayOnDanger, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

// 오버레이 전용 색. 게임 위에 떠 있어야 해서 앱 테마와 무관하게 어두운 반투명으로 고정한다.
private val OverlayScrim = FloaColors.Surface.copy(alpha = 0.95f)
private val OverlayHeader = FloaColors.SurfaceElevated
private val OverlayBorder = FloaColors.Secondary.copy(alpha = 0.3f)
private val OverlayText = FloaColors.OnSurface
private val OverlayMuted = FloaColors.OnSurfaceVariant
private val OverlayAccent = FloaColors.Secondary
private val OverlayAmber = FloaColors.Gold

/**
 * 목록 줄 설명 글자. OnSurfaceVariant(바탕 대비 6.8, 흰 게임 화면이 비치면 6.0)는 7:1 에 못 미쳐 본문색을 78% 로 낮춰 쓴다:
 * 바탕 대비 9.3, 흰 화면이 5% 비쳐도 8.3. 별칭(본문색·굵게·12sp)과는 크기·굵기·밝기로 구분된다.
 */
private val OverlaySubtext = FloaColors.OnSurface.copy(alpha = 0.78f)

/** 한 번 더 누르면 닫히는 상태의 닫기 버튼. 어두운 글자와 대비 약 7:1. */
private val OverlayDanger = FloaColors.Negative
private val OverlayOnDanger = FloaColors.Background

/** 오버레이용 코스트 테두리 색. 어두운 배경 위에서 읽히도록 앱 테마와 따로 둔다. */
private fun costTint(cost: Int?): Color = when (cost) {
    1 -> Color(0xFF6E7C78)
    2 -> Color(0xFF3E9E86)
    3 -> Color(0xFF4E82B4)
    4 -> Color(0xFF9A5FB0)
    5 -> Color(0xFFC69A3C)
    else -> Color(0xFF4A5450)
}
