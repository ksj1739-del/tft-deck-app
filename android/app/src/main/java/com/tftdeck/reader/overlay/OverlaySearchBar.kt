package com.tftdeck.reader.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tftdeck.reader.data.Deck
import com.tftdeck.reader.data.DeckKeys
import com.tftdeck.reader.data.DeckSearch
import com.tftdeck.reader.data.DeckToken
import com.tftdeck.reader.data.TokenCandidate
import com.tftdeck.reader.ui.components.TokenCandidateRow
import com.tftdeck.reader.ui.components.TokenSearchField
import com.tftdeck.reader.ui.gradeColor

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
    val state = remember {
        OverlaySearchState(onSearchEnd = { currentOnSearchEnd() }, onListAnchor = { currentOnListAnchor(it) })
    }
    // 창이 떨어지면(닫기·감지 끄기) 검색 조건이 사라진다. 조건으로 좁힌 목록에서 기억한 자리는 다음에 붙을 조건 없는
    // 목록과 맞지 않으니 함께 지운다 — 다시 띄우면 맨 위부터.
    val tokensNow by rememberUpdatedState(state.tokens)
    DisposableEffect(Unit) {
        onDispose { if (tokensNow.isNotEmpty()) currentOnListAnchor(null) }
    }
    // 검색이 끝나면(후보 선택·바깥 누름·뒤로 가기·접기 등) 치다 만 글자는 버린다.
    LaunchedEffect(searching) { if (!searching) state.query = "" }
    return state
}

/**
 * 오버레이 검색 줄 상태: 고른 조건([tokens])과 치는 글자([query]), 그리고 조건을 바꾸는 동작(후보 고르기·IME 검색 키·
 * 칩 빼기·모두 지우기). 기억한 목록 자리 지우기([onListAnchor])와 검색 끝내기([onSearchEnd])는 서비스에 맡긴다.
 */
internal class OverlaySearchState(
    private val onSearchEnd: () -> Unit,
    private val onListAnchor: (OverlayListAnchor?) -> Unit,
) {
    /** 고른 조건. 바꿀 때는 [changeTokens] 를 거친다(기억한 목록 자리를 함께 지운다). */
    var tokens by mutableStateOf(emptyList<DeckToken>())
        private set

    /** 치는 글자. */
    var query by mutableStateOf("")

    /**
     * 후보. 지금 좁혀진 목록([decksShown]) 기준으로 센다(골랐을 때 남는 덱 수). 검색 중이 아니면 없다.
     * [listed] 는 등급 조건을 걸기 전 목록이다. 등급 때문에 빠진 덱을 따로 셀 때 쓰는 자리로, 지금은 쓰지 않는다.
     */
    @Composable
    fun rememberCandidates(
        search: DeckSearch?,
        listed: List<Deck>,
        decksShown: List<Deck>,
        searching: Boolean,
    ): List<TokenCandidate> = remember(search, query, tokens, decksShown, searching) {
        if (!searching) emptyList() else search?.suggestTokens(query, tokens, within = decksShown, limit = OVERLAY_CANDIDATES).orEmpty()
    }

    /** 조건이 바뀌면 좁혀진 목록을 맨 위부터 보여 준다. 목록이 새 조건으로 다시 그려지기 전에 기억해 둔 자리부터 지운다. */
    fun changeTokens(next: List<DeckToken>) {
        if (next != tokens) {
            onListAnchor(null)
            tokens = next
        }
    }

    fun pickCandidate(candidate: TokenCandidate) {
        if (tokens.none { it.key == candidate.token.key }) changeTokens(tokens + candidate.token)
        query = ""
        // 고르면 검색을 끝낸다 — 키보드를 내리고 창을 다시 포커스를 받지 않게 해 게임 조작을 돌려준다.
        onSearchEnd()
    }

    /** IME 의 검색 키는 맨 위 후보를 고른다. 친 글자가 없으면 검색만 끝낸다. */
    fun onSubmit(candidates: List<TokenCandidate>) {
        candidates.firstOrNull()?.let(::pickCandidate) ?: onSearchEnd()
    }

    fun removeToken(token: DeckToken) {
        changeTokens(tokens.filterNot { it.key == token.key })
    }

    fun clearAll() {
        changeTokens(emptyList())
        query = ""
    }
}

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
internal fun CandidateList(
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

/** 후보 목록 높이 상한. 검색창 바로 아래라 키보드가 올라와도 위쪽 몇 줄은 보인다. */
private val CANDIDATE_LIST_MAX = 200.dp

/** 오버레이 후보 수(사용자 지정 후보는 따로 하나 더 붙는다). */
private const val OVERLAY_CANDIDATES = 8

/**
 * 등급 조회 조건 칸 하나(S~D). 검색 줄과 한 줄을 나눠 쓰므로 다섯 칸이 약 130dp 에 들어가게 폭을 24dp 로 두고,
 * 높이는 검색 줄과 같은 28dp. 칸 사이를 3dp 벌려 옆 등급이 같이 눌리지 않게 한다.
 */
private val GRADE_TOGGLE_WIDTH = 24.dp
private val GRADE_TOGGLE_HEIGHT = 28.dp
private val GRADE_TOGGLE_GAP = 3.dp
