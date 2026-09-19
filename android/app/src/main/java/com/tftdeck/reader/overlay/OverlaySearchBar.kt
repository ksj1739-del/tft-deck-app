package com.tftdeck.reader.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tftdeck.reader.data.Deck
import com.tftdeck.reader.data.DeckKeys
import com.tftdeck.reader.data.DeckSearch
import com.tftdeck.reader.data.DeckToken
import com.tftdeck.reader.data.OverlayCandidate
import com.tftdeck.reader.ui.components.SearchBodyTextSize
import com.tftdeck.reader.ui.components.SearchLabelTextSize
import com.tftdeck.reader.ui.components.TokenCandidateRow
import com.tftdeck.reader.ui.components.TokenSearchField
import com.tftdeck.reader.ui.gradeColor
import com.tftdeck.reader.ui.theme.FloaColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 검색 줄 상태를 만든다. 고른 조건·치는 글자는 접었다 펴도 남도록 펼침 분기 밖에서 기억한다(창을 닫으면 사라진다).
 * 앱 목록의 조건과는 따로다. 루트에서 덱이 없어 일찍 돌아가는 곳보다 앞에서 부른다 — 뒤에서 부르면 덱이 잠깐 비는
 * 동안 조건이 사라진다. 후보는 목록이 이 조건으로 좁혀진 뒤에 [OverlaySearchState.rememberCandidates] 로 센다.
 */
@Composable
internal fun rememberOverlaySearchState(
    searching: Boolean,
    onSearchEnd: () -> Unit,
    onListAnchor: (OverlayListAnchor?) -> Unit,
): OverlaySearchState {
    val currentOnSearchEnd by rememberUpdatedState(onSearchEnd)
    val currentOnListAnchor by rememberUpdatedState(onListAnchor)
    val scope = rememberCoroutineScope()
    val state = remember {
        OverlaySearchState(
            onSearchEnd = { currentOnSearchEnd() },
            onListAnchor = { currentOnListAnchor(it) },
            scope = scope,
        )
    }
    // 창이 떨어지면(닫기·감지 끄기) 검색 조건이 사라진다. 조건으로 좁힌 목록에서 기억한 자리는 다음에 붙을 조건 없는
    // 목록과 맞지 않으니 함께 지운다 — 다시 띄우면 맨 위부터.
    val tokensNow by rememberUpdatedState(state.tokens)
    DisposableEffect(Unit) {
        onDispose { if (tokensNow.isNotEmpty()) currentOnListAnchor(null) }
    }
    // 검색이 끝나면('완료'·키보드 내림·바깥 누름·뒤로 가기·접기 등) 치다 만 글자와 안내는 버린다.
    LaunchedEffect(searching) { if (!searching) state.onSearchClosed() }
    return state
}

/**
 * 오버레이 검색 줄 상태: 고른 조건([tokens])과 치는 글자([query]), 그리고 조건을 바꾸는 동작(후보 고르기·IME 검색 키·
 * 칩 빼기·지우기). 기억한 목록 자리 지우기([onListAnchor])와 검색 끝내기([onSearchEnd])는 서비스에 맡긴다.
 *
 * 후보를 골라도 검색은 이어진다(키보드를 둔 채 조건을 이어 더한다). 끝내는 것은 '완료'·키보드 내림·바깥 누름·뒤로 가기다.
 */
internal class OverlaySearchState(
    private val onSearchEnd: () -> Unit,
    private val onListAnchor: (OverlayListAnchor?) -> Unit,
    private val scope: CoroutineScope? = null,
) {
    /** 고른 조건. 바꿀 때는 [changeTokens] 를 거친다(기억한 목록 자리를 함께 지운다). */
    var tokens by mutableStateOf(emptyList<DeckToken>())
        private set

    private var typed by mutableStateOf("")

    /** 치는 글자. 바뀌면 검색 키 안내([notice])는 거둔다 — 방금 친 글자에 대한 말이 아니게 된다. */
    var query: String
        get() = typed
        set(value) {
            if (value == typed) return
            typed = value
            clearNotice()
        }

    /** 검색 줄 아래에 2초 보일 안내. 검색 키를 눌렀는데 고를 후보가 없을 때 '맞는 덱 없음'. */
    var notice by mutableStateOf<String?>(null)
        private set

    private var noticeJob: Job? = null

    /** 직전에 보인 색인 후보(사용자 지정 제외). 한글을 조합하는 사이 후보가 사용자 지정 하나로 줄 때 이어 보인다. */
    private var lastIndexed: List<OverlayCandidate> = emptyList()

    /**
     * 후보. 검색 중이 아니거나 친 글자가 없으면 없다. 후보마다 두 수를 센다([DeckSearch.suggestTokensWithHidden]):
     * 등급 조건 전 수(deckCount)와 그중 꺼 둔 등급에 가려진 수(hiddenByGrade). [listed] 는 등급 조건을 걸기 전 목록,
     * [decksShown] 은 지금 목록(등급·검색 조건 적용)이다. 새 후보가 사용자 지정 하나뿐이면 직전 후보를 이어 보인다
     * ([keepCandidatesWhileComposing]).
     */
    @Composable
    fun rememberCandidates(
        search: DeckSearch?,
        listed: List<Deck>,
        decksShown: List<Deck>,
        searching: Boolean,
    ): List<OverlayCandidate> = remember(search, query, tokens, listed, decksShown, searching) {
        candidatesFor(search, listed, decksShown, searching)
    }

    internal fun candidatesFor(
        search: DeckSearch?,
        listed: List<Deck>,
        decksShown: List<Deck>,
        searching: Boolean,
    ): List<OverlayCandidate> {
        if (!searching || search == null || query.isBlank()) {
            lastIndexed = emptyList()
            return emptyList()
        }
        val fresh = search.suggestTokensWithHidden(query, listed = decksShown, all = listed, selected = tokens, limit = OVERLAY_CANDIDATES)
        val shown = keepCandidatesWhileComposing(lastIndexed, fresh, tokens) { search.recount(it, decksShown, listed, tokens) }
        if (!isOnlyCustom(fresh)) lastIndexed = fresh.filterNot { it.token.isCustom }
        return shown
    }

    /** 조건이 바뀌면 좁혀진 목록을 맨 위부터 보여 준다. 목록이 새 조건으로 다시 그려지기 전에 기억해 둔 자리부터 지운다. */
    fun changeTokens(next: List<DeckToken>) {
        if (next != tokens) {
            onListAnchor(null)
            tokens = next
        }
    }

    /** 후보를 고른다. 키보드는 둔다 — 조건을 이어서 더할 수 있다(끝내기는 '완료'). */
    fun pickCandidate(candidate: OverlayCandidate) {
        if (tokens.none { it.key == candidate.token.key }) changeTokens(tokens + candidate.token)
        query = ""
        lastIndexed = emptyList()
    }

    /**
     * IME 검색 키·하드웨어 Enter. 덱이 있는 첫 색인 후보([submitCandidate])를 고른다 — 덱 0 후보와 사용자 지정은 건너뛴다.
     * 고를 후보가 없으면 아무것도 고르지 않고 '맞는 덱 없음'을 2초 보인다. 친 글자가 없으면 검색을 끝낸다('완료'와 같다).
     */
    fun onSubmit(candidates: List<OverlayCandidate>) {
        if (query.isBlank()) {
            onSearchEnd()
            return
        }
        val pick = submitCandidate(candidates)
        if (pick != null) pickCandidate(pick) else showNotice(NO_MATCH_NOTICE)
    }

    fun removeToken(token: DeckToken) {
        changeTokens(tokens.filterNot { it.key == token.key })
    }

    /**
     * 검색 줄 끝 지우기 버튼. 두 단계다: 친 글자가 있으면 글자만 지우고, 없으면 고른 조건을 모두 뺀다
     * (오타를 지우려다 쌓은 조건까지 날리지 않게). 이름은 루트가 부르는 그대로 둔다.
     */
    fun clearAll() {
        if (query.isNotEmpty()) query = "" else changeTokens(emptyList())
    }

    /** 검색이 끝났다. 치다 만 글자·안내·직전 후보를 버린다. */
    internal fun onSearchClosed() {
        query = ""
        clearNotice()
        lastIndexed = emptyList()
    }

    private fun showNotice(text: String) {
        noticeJob?.cancel()
        notice = text
        noticeJob = scope?.launch {
            delay(NOTICE_MILLIS)
            notice = null
        }
    }

    private fun clearNotice() {
        noticeJob?.cancel()
        noticeJob = null
        notice = null
    }
}

/** 검색 키로 고를 후보: 덱이 있는(등급 조건 전 수 > 0) 첫 색인 후보. 사용자 지정은 고르지 않는다. */
internal fun submitCandidate(candidates: List<OverlayCandidate>): OverlayCandidate? =
    candidates.firstOrNull { !it.token.isCustom && it.deckCount > 0 }

/** 후보가 사용자 지정 하나뿐인지(색인에서 맞는 이름이 없다). */
internal fun isOnlyCustom(candidates: List<OverlayCandidate>): Boolean =
    candidates.size == 1 && candidates.single().token.isCustom

/**
 * 한글 조합 중 후보 유지. 받침이 붙었다 떨어지는 사이('드' → '들' → '드레') 새 후보가 사용자 지정 하나로 줄면 직전 색인
 * 후보를 그대로 두고 사용자 지정만 맨 아래에 새로 단다 — 누르려던 후보가 손가락 밑에서 사라지지 않고 창 높이도 출렁이지
 * 않는다. 이어 보이는 후보는 [recount] 로 지금 목록 기준 수를 다시 세고, 이미 고른 조건은 뺀다.
 */
internal fun keepCandidatesWhileComposing(
    previous: List<OverlayCandidate>,
    fresh: List<OverlayCandidate>,
    picked: List<DeckToken>,
    recount: (OverlayCandidate) -> OverlayCandidate = { it },
): List<OverlayCandidate> {
    if (!isOnlyCustom(fresh)) return fresh
    val pickedKeys = picked.mapTo(HashSet()) { it.key }
    val kept = previous.filter { !it.token.isCustom && it.token.key !in pickedKeys }
    if (kept.isEmpty()) return fresh
    return kept.map(recount) + fresh
}

/**
 * 후보 목록 높이 상한. 머리줄을 숨기는 낮은 화면(가로)은 키보드 위에 보이는 칸이 약 85dp 라 28dp 줄 3개(84dp)로 두고
 * 나머지는 스크롤한다. 세로는 200dp.
 */
internal fun candidateListMaxHeight(areaHeightDp: Float): Dp =
    if (hideHeaderWhileSearching(areaHeightDp)) CANDIDATE_LIST_MAX_SHORT else CANDIDATE_LIST_MAX

/**
 * 목록 위의 작은 검색 줄. 고른 조건은 입력칸 안에 '니달리 ×' 칩으로 쌓인다.
 * 창이 포커스를 받기 전에는 안내 글자만 그리고, 누르면 서비스에 포커스를 요청한다([onSearchStart]).
 * 서비스가 검색을 켜 주고 창이 실제로 포커스를 받은 뒤에 입력칸이 포커스를 잡고 키보드를 띄운다 —
 * 그 전에 키보드를 부르면 포커스 없는 창이라 무시된다.
 *
 * 오른쪽 끝:
 *  - 검색 중: '완료'(44×32dp). 가로에서 머리줄이 숨어도 늘 보이는 끝내기 자리다. [onSearchEnd] 를 넘기지 않으면
 *    창 뿌리([OverlayRootView.requestEndSearch])로 끝낸다. 치는 동안에는 등급 칸을 숨겨 입력칸에 폭을 준다.
 *  - 그 밖: 등급 조회 조건 칸(S A B C D, [GradeToggles])과 그 위 '앱과 같은 조건' 라벨. [grades] 가 null 이면
 *    (구간이 없는 옛 데이터) 칸을 두지 않는다.
 * 아래 한 줄: 검색 중에는 [notice](검색 키로 고를 후보가 없을 때), 그 밖에는 마지막 등급 칸을 끄려 할 때의 안내. 2초.
 */
@Composable
internal fun OverlaySearchBar(
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
    onSearchEnd: (() -> Unit)? = null,
    notice: String? = null,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(searching, windowFocused) {
        if (searching && windowFocused && runCatching { focusRequester.requestFocus() }.isSuccess) {
            keyboard?.show()
        }
    }
    val view = LocalView.current
    val endSearch: () -> Unit = onSearchEnd ?: { view.overlayRoot()?.requestEndSearch() }
    // 마지막 등급 칸을 끄려 할 때의 안내. 누를 때마다 새 값을 두어 2초를 다시 센다.
    var gradeNotice by remember { mutableStateOf<Any?>(null) }
    LaunchedEffect(gradeNotice) {
        if (gradeNotice != null) {
            delay(NOTICE_MILLIS)
            gradeNotice = null
        }
    }

    Column(Modifier.padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 2.dp)) {
        Row(verticalAlignment = if (searching) Alignment.CenterVertically else Alignment.Bottom) {
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
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = GRADE_TOGGLE_SIZE),
            )
            if (searching) {
                Spacer(Modifier.width(FIELD_END_GAP))
                DoneButton(onClick = endSearch)
            } else if (grades != null) {
                // 입력칸 끝 지우기 버튼(입력칸 안쪽 여백 4dp)과 S 칸 사이가 12dp 가 되게 띄운다.
                Spacer(Modifier.width(FIELD_END_GAP))
                Column(horizontalAlignment = Alignment.End) {
                    Text("앱과 같은 조건", style = SearchLabelStyle, color = OverlayMuted, maxLines = 1)
                    Spacer(Modifier.height(4.dp))
                    GradeToggles(grades, onToggleGrade, onLastGrade = { gradeNotice = Any() })
                }
            }
        }
        val line = when {
            searching -> notice?.let { it to TextAlign.Start }
            grades != null && gradeNotice != null -> LAST_GRADE_NOTICE to TextAlign.End
            else -> null
        }
        if (line != null) {
            Text(
                line.first,
                style = SearchNoticeStyle,
                color = OverlayText,
                textAlign = line.second,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            )
        }
    }
}

/** 검색 끝내기. 게임 중 엄지로 누르기 쉽게 44×32dp(검색 줄 예외 크기). */
@Composable
private fun DoneButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = DONE_WIDTH, height = DONE_HEIGHT)
            .clip(RoundedCornerShape(8.dp))
            .background(FloaColors.Primary)
            .focusProperties { canFocus = false }
            .clickable(role = Role.Button, onClickLabel = "검색 끝내기", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("완료", style = SearchButtonStyle, color = FloaColors.OnPrimary, maxLines = 1)
    }
}

/**
 * 등급 조회 조건 칸 S A B C D. 앱 목록의 등급 칩과 같은 값이다(여기서 바꾸면 앱에도 남는다 — 위 라벨이 말한다).
 * 28dp 칸, 간격 6dp, 테두리 없음. 켜진 칸은 등급색 35% 채움에 흰 글자, 꺼진 칸은 채움 없이 흐린 글자(40%) —
 * 채움 유무로 읽히므로 회색 D 도 켜짐이 보인다. 마지막 하나를 끄려 하면 무시하지 않고 [onLastGrade] 로 안내한다
 * (DeckPrefs.toggleGrade 는 그대로 끄지 않는다). 키보드 포커스는 받지 않는다(하드웨어 Enter 가 칸을 누르지 않게).
 */
@Composable
private fun GradeToggles(grades: Set<String>, onToggle: (String) -> Unit, onLastGrade: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(GRADE_TOGGLE_GAP)) {
        DeckKeys.GRADE_FILTER_ALL.forEach { grade ->
            val on = grade in grades
            Box(
                modifier = Modifier
                    .size(GRADE_TOGGLE_SIZE)
                    .clip(RoundedCornerShape(6.dp))
                    .then(if (on) Modifier.background(gradeColor(grade).copy(alpha = GRADE_ON_FILL_ALPHA)) else Modifier)
                    .focusProperties { canFocus = false }
                    .toggleable(
                        value = on,
                        role = Role.Checkbox,
                        onValueChange = { if (turnsOffLastGrade(grades, grade)) onLastGrade() else onToggle(grade) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    grade,
                    style = SearchGradeStyle,
                    color = OverlayText,
                    modifier = if (on) Modifier else Modifier.alpha(GRADE_OFF_ALPHA),
                )
            }
        }
    }
}

/**
 * 치는 동안 검색창 아래에 뜨는 후보. 맨 끝은 친 글자 그대로의 '사용자 지정' 후보다.
 * 높이 상한은 가로(머리줄을 숨기는 낮은 화면)에서 28dp 줄 3개, 세로 200dp([candidateListMaxHeight]).
 * 글자가 바뀌어 후보가 바뀌면 맨 위부터 보인다 — 첫 세 후보는 스크롤 없이 닿는다.
 */
@Composable
internal fun CandidateList(
    candidates: List<OverlayCandidate>,
    assetBase: String,
    onPick: (OverlayCandidate) -> Unit,
    modifier: Modifier = Modifier,
    maxHeight: Dp = candidateListMaxHeight(LocalConfiguration.current.screenHeightDp.toFloat()),
) {
    if (candidates.isEmpty()) {
        Text(
            "더할 조건이 없습니다 · 이미 고른 조건입니다",
            style = SearchNoticeStyle,
            color = OverlayMuted,
            modifier = modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        )
        return
    }
    val listState = rememberLazyListState()
    LaunchedEffect(candidates) {
        if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) listState.scrollToItem(0)
    }
    LazyColumn(state = listState, modifier = modifier.heightIn(max = maxHeight)) {
        items(candidates) { candidate ->
            TokenCandidateRow(
                candidate.toTokenCandidate(),
                assetBase,
                onClick = { onPick(candidate) },
                compact = true,
                hiddenByGrade = candidate.hiddenByGrade,
            )
        }
    }
}

/** 후보 목록 높이 상한(세로). 검색창 바로 아래라 키보드가 올라와도 위쪽 몇 줄은 보인다. */
private val CANDIDATE_LIST_MAX = 200.dp

/** 후보 목록 높이 상한(가로, 머리줄을 숨기는 낮은 화면): 28dp 후보 줄 3개. */
private val CANDIDATE_LIST_MAX_SHORT = 84.dp

/** 오버레이 후보 수(사용자 지정 후보는 따로 하나 더 붙는다). */
private const val OVERLAY_CANDIDATES = 8

/** 검색 줄 아래 안내를 보이는 시간. */
private const val NOTICE_MILLIS = 2_000L

private const val NO_MATCH_NOTICE = "맞는 덱 없음"
private const val LAST_GRADE_NOTICE = "등급 하나는 켜 두어야 합니다"

/** 등급 조회 조건 칸 하나(S~D): 28×28dp, 칸 사이 6dp(MASTER 규칙 9 의 검색 줄 예외). 입력칸 최소 높이도 같다. */
private val GRADE_TOGGLE_SIZE = 28.dp
private val GRADE_TOGGLE_GAP = 6.dp
private const val GRADE_ON_FILL_ALPHA = 0.35f
private const val GRADE_OFF_ALPHA = 0.4f

/** 입력칸과 오른쪽 버튼('완료'·등급 칸) 사이. 입력칸 안쪽 여백 4dp 와 합쳐 지우기 버튼에서 12dp 떨어진다. */
private val FIELD_END_GAP = 8.dp

private val DONE_WIDTH = 44.dp
private val DONE_HEIGHT = 32.dp

// 글자 크기는 TokenSearch.kt 의 검색 줄 크기(11·12sp)를 같이 쓴다.
private val SearchLabelStyle = TextStyle(fontSize = SearchLabelTextSize, fontWeight = FontWeight.Medium)
private val SearchGradeStyle = TextStyle(fontSize = SearchLabelTextSize, fontWeight = FontWeight.Bold)
private val SearchNoticeStyle = TextStyle(fontSize = SearchBodyTextSize)
private val SearchButtonStyle = TextStyle(fontSize = SearchBodyTextSize, fontWeight = FontWeight.Medium)
