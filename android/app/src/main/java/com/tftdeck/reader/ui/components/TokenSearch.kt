package com.tftdeck.reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tftdeck.reader.data.DeckToken
import com.tftdeck.reader.data.TokenCandidate
import com.tftdeck.reader.ui.costColor
import com.tftdeck.reader.ui.iconUrl
import com.tftdeck.reader.ui.theme.FloaColors

/**
 * 덱 목록 위의 검색 줄(metatft 식 다중 선택). 고른 조건이 입력칸 안에 칩('니달리 ×')으로 쌓이고 그 뒤에 글자를 친다.
 * 앱 덱 목록과 오버레이가 같은 모양을 쓴다. 색은 다크 전용 [FloaColors] 토큰을 직접 준다. 글자는 앱이면 테마 글자,
 * 오버레이([compact])는 앱 테마 밖이라 크기를 직접 준다.
 *
 * 오버레이 창은 평소 포커스를 받지 않아 키보드를 띄울 수 없다. 그래서 [editing] 이 false 인 동안에는 입력칸 대신
 * 안내 글자를 그리고, 누르면 [onStartEditing] 으로 창 포커스를 먼저 요청한다. 앱은 늘 [editing] = true 다.
 * [compact] 는 오버레이용 작은 크기이고, 오버레이에서만 쓰는 동작도 이것으로 가른다:
 *  - 편집 중에는 칩과 입력칸을 한 줄에 두고 가로로 민다(글자를 치거나 칩이 늘어도 줄이 늘어 창 높이가 튀지 않게).
 *    편집이 끝나면 칩이 여러 줄로 접힌다(앱은 늘 여러 줄).
 *  - 끝에 지우기 버튼([onClearAll]): 친 글자나 고른 조건이 있을 때 보인다. 무엇부터 지울지는 부르는 쪽이 정한다
 *    (오버레이는 친 글자 먼저, 그다음 조건 모두). 앱에는 두지 않는다 — 칩마다 빼기와 목록 위 '조건 초기화'가 있다.
 *  - 칩·지우기 버튼은 키보드 포커스를 받지 않는다(하드웨어 Enter 가 입력칸 밖 버튼을 누르지 않게).
 *
 * 키보드: 검색 키(IME), 한국어 힌트(hintLocales ko-KR), 자동 수정 끔. 하드웨어 Enter 는 누름을 삼키고 뗄 때 [onSubmit] 을
 * 한 번 부른다 — 뗌이 입력칸이 사라진 뒤의 다른 포커스로 새지 않게.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TokenSearchField(
    tokens: List<DeckToken>,
    query: String,
    onQueryChange: (String) -> Unit,
    onRemoveToken: (DeckToken) -> Unit,
    onClearAll: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    editing: Boolean = true,
    onStartEditing: () -> Unit = {},
    focusRequester: FocusRequester = remember { FocusRequester() },
    compact: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (compact) 8.dp else 12.dp)
    val lineHeight = if (compact) CHIP_HEIGHT_COMPACT else CHIP_HEIGHT
    val textStyle = (if (compact) CompactBodyStyle else MaterialTheme.typography.bodyMedium).copy(color = FloaColors.OnSurface)
    val hintStyle = textStyle.copy(color = FloaColors.OnSurfaceVariant)
    val hint = if (tokens.isNotEmpty()) "조건 더하기" else SEARCH_HINT
    val keyboardOptions = remember {
        KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false, hintLocales = LocaleList("ko-KR"))
    }

    val input: @Composable () -> Unit = {
        if (editing) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = textStyle,
                cursorBrush = SolidColor(FloaColors.Secondary),
                keyboardOptions = keyboardOptions,
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier
                    .widthIn(min = if (compact) 64.dp else 96.dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged { focused = it.isFocused }
                    .onPreviewKeyEvent { submitOnEnter(it, onSubmit) },
                decorationBox = { inner ->
                    // 칩과 같은 높이로 두어 한 줄에 섞여도 글자 높이가 맞는다.
                    Box(Modifier.height(lineHeight), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(hint, style = hintStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        inner()
                    }
                },
            )
        } else {
            // 안내 글자도 직접 누르는 자리로 둔다. 칩이 여러 줄로 접히면 바로 위 칩(작아서 Compose 가 누르는 범위를
            // 48dp 로 넓힌다)이 '조건 더하기' 누름을 가로채 조건이 빠지곤 했다 — 직접 맞은 쪽이 넓힌 범위보다 먼저다.
            Box(
                Modifier
                    .height(lineHeight)
                    .widthIn(min = if (compact) 64.dp else 96.dp)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        onStartEditing()
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(hint, style = hintStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(FloaColors.SurfaceVariant)
            .border(1.dp, if (editing && focused) FloaColors.Secondary else FloaColors.OutlineVariant, shape)
            // 칩과 입력칸 사이 빈 곳을 눌러도 입력이 시작되게 한다.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (editing) runCatching { focusRequester.requestFocus() } else onStartEditing()
            }
            .padding(horizontal = if (compact) 4.dp else 10.dp, vertical = if (compact) 2.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            tint = FloaColors.OnSurfaceVariant,
            modifier = Modifier.size(if (compact) 14.dp else 18.dp),
        )
        Spacer(Modifier.width(if (compact) 4.dp else 8.dp))
        if (compact && editing) {
            val scroll = rememberScrollState()
            // 칩이 늘거나 글자가 길어지면 끝(입력칸)이 보이게 민다.
            LaunchedEffect(scroll) { snapshotFlow { scroll.maxValue }.collect { scroll.scrollTo(it) } }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scroll),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tokens.forEach { token -> TokenChip(token, compact, onRemove = { onRemoveToken(token) }) }
                input()
            }
        } else {
            FlowRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                tokens.forEach { token -> TokenChip(token, compact, onRemove = { onRemoveToken(token) }) }
                input()
            }
        }
        if (compact && (tokens.isNotEmpty() || query.isNotEmpty())) {
            Spacer(Modifier.width(4.dp))
            // 원 안 ×(취소) 모양은 '입력 글자 지우기'로 읽혀 조건까지 날리곤 했다. 지우기(⌫) 모양으로 둔다.
            Box(
                Modifier
                    .size(CHIP_HEIGHT_COMPACT)
                    .clip(CircleShape)
                    .focusProperties { canFocus = false }
                    .clickable(role = Role.Button, onClick = onClearAll),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = if (query.isNotEmpty()) "입력 글자 지우기" else "조건 모두 지우기",
                    tint = FloaColors.Secondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/**
 * 고른 조건 하나: '니달리 ×'. 칩 어디를 눌러도 그 조건만 뺀다(×는 표시다) — 글자 부분을 누르면 입력이 시작돼
 * 키보드가 뜨던 것을 막는다. 사용자 지정은 따옴표로 감싸 보인다.
 */
@Composable
fun TokenChip(token: DeckToken, compact: Boolean, onRemove: () -> Unit) {
    val height = if (compact) CHIP_HEIGHT_COMPACT else CHIP_HEIGHT
    Row(
        Modifier
            .widthIn(max = if (compact) CHIP_MAX_WIDTH_COMPACT else CHIP_MAX_WIDTH)
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(FloaColors.PrimaryContainer)
            .then(if (compact) Modifier.focusProperties { canFocus = false } else Modifier)
            .clickable(onClickLabel = "${token.name} 조건 빼기", role = Role.Button, onClick = onRemove)
            .padding(start = if (compact) 8.dp else 10.dp, end = if (compact) 6.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            token.chipLabel,
            style = if (compact) CompactLabelStyle else MaterialTheme.typography.labelMedium,
            color = FloaColors.OnPrimaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(if (compact) 2.dp else 4.dp))
        Icon(
            Icons.Default.Close,
            contentDescription = null,
            tint = FloaColors.OnPrimaryContainer,
            modifier = Modifier.size(if (compact) 12.dp else 16.dp),
        )
    }
}

/**
 * 후보 한 줄: 아이콘 · 이름 · 축 이름(유닛/시너지/아이템/증강/사용자 지정) · 덱 수.
 * 덱 수는 지금 목록에 이 조건을 더했을 때 남는 수다. [hiddenByGrade] 는 꺼 둔 등급 때문에 지금 목록에서 빠진 수로,
 * 오버레이만 넘긴다: 남는 수가 0 이어도 꺼진 등급에 덱이 있으면 '덱 0 · 꺼진 등급 5'로 적고 흐리게 하지 않는다
 * ([candidateCountText], [candidateDimmed]). 흐린 줄은 골라도 목록이 비고 등급을 켜도 덱이 없는 후보다.
 */
@Composable
fun TokenCandidateRow(
    candidate: TokenCandidate,
    assetBase: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    hiddenByGrade: Int = 0,
) {
    val token = candidate.token
    val iconSize = if (compact) 20.dp else 28.dp
    val nameStyle = if (compact) CompactBodyStyle else MaterialTheme.typography.bodyMedium
    val subStyle = if (compact) CompactLabelStyle else MaterialTheme.typography.bodySmall
    Row(
        modifier
            .fillMaxWidth()
            .alpha(if (candidateDimmed(candidate.deckCount, hiddenByGrade)) 0.5f else 1f)
            .then(if (compact) Modifier.focusProperties { canFocus = false } else Modifier)
            .clickable(onClick = onClick)
            // 오버레이는 줄 높이를 28dp 로 맞춘다 — 가로 화면 후보 칸(84dp)에 세 줄이 스크롤 없이 들어간다.
            .then(if (compact) Modifier.heightIn(min = CANDIDATE_ROW_HEIGHT_COMPACT) else Modifier)
            .padding(horizontal = if (compact) 8.dp else 6.dp, vertical = if (compact) 4.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (token.isCustom) {
            Box(
                Modifier
                    .size(iconSize)
                    .clip(RoundedCornerShape(4.dp))
                    .background(FloaColors.SurfaceElevated),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = FloaColors.OnSurfaceVariant,
                    modifier = Modifier.size(if (compact) 13.dp else 17.dp),
                )
            }
        } else {
            val placeholder = remember(candidate.cost) { ColorPainter(costColor(candidate.cost).copy(alpha = 0.3f)) }
            AsyncImage(
                model = iconUrl(assetBase, candidate.icon),
                contentDescription = null,
                placeholder = placeholder,
                error = placeholder,
                modifier = Modifier
                    .size(iconSize)
                    .clip(RoundedCornerShape(4.dp)),
            )
        }
        Spacer(Modifier.width(if (compact) 8.dp else 10.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = FloaColors.OnSurface, fontWeight = FontWeight.Medium)) {
                    append(token.chipLabel)
                }
                withStyle(SpanStyle(color = FloaColors.OnSurfaceVariant, fontSize = subStyle.fontSize)) {
                    append(" · ")
                    append(token.axisLabel)
                }
            },
            style = nameStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            candidateCountText(candidate.deckCount, hiddenByGrade),
            style = subStyle,
            color = FloaColors.OnSurfaceVariant,
            maxLines = 1,
        )
    }
}

/**
 * 후보 줄의 덱 수. [shownCount] 는 지금 목록에 남는 수. 그 수가 0 인데 꺼 둔 등급에 덱이 있으면 함께 적어
 * '등급 때문에 0'과 '원래 덱이 없음'을 가른다.
 */
internal fun candidateCountText(shownCount: Int, hiddenByGrade: Int): String =
    if (shownCount == 0 && hiddenByGrade > 0) "덱 0 · 꺼진 등급 $hiddenByGrade" else "덱 $shownCount"

/** 흐리게 할 후보: 골라도 지금 목록이 비고, 꺼 둔 등급을 켜도 덱이 없다. */
internal fun candidateDimmed(shownCount: Int, hiddenByGrade: Int): Boolean = shownCount == 0 && hiddenByGrade == 0

/** 하드웨어 Enter(숫자판 Enter 포함). 누름은 삼키고 뗄 때 한 번 제출한다. */
private fun submitOnEnter(event: KeyEvent, onSubmit: () -> Unit): Boolean {
    if (event.key != Key.Enter && event.key != Key.NumPadEnter) return false
    if (event.type == KeyEventType.KeyUp) onSubmit()
    return true
}

/** 검색 줄 안내 글자(앱·오버레이 같은 말). 도감·용어 규칙과 같은 이름을 쓴다. */
private const val SEARCH_HINT = "챔피언·특성·아이템·증강"

/**
 * 오버레이(compact) 검색 줄 글자 크기. 오버레이는 앱 테마 밖이라 크기를 직접 준다(MASTER 규칙 1 의 11·12sp —
 * 11sp 는 세 낱말 이하 라벨). 오버레이 검색 줄(OverlaySearchBar)도 이 둘을 쓴다. 오버레이 글자 체계가 생기면 그리로 옮긴다.
 */
internal val SearchLabelTextSize = 11.sp
internal val SearchBodyTextSize = 12.sp

private val CompactBodyStyle = TextStyle(fontSize = SearchBodyTextSize)
private val CompactLabelStyle = TextStyle(fontSize = SearchLabelTextSize, fontWeight = FontWeight.Medium)

private val CHIP_HEIGHT = 28.dp

/** 오버레이 칩 높이. 칩 전체가 빼기 버튼이라 24dp 로 둔다. 입력칸 글자 줄과 지우기 버튼도 같은 높이. */
private val CHIP_HEIGHT_COMPACT = 24.dp

/** 칩 최대 폭(× 포함). 넘치는 이름은 줄임표로 줄인다. */
private val CHIP_MAX_WIDTH = 220.dp
private val CHIP_MAX_WIDTH_COMPACT = 140.dp

/** 오버레이 후보 줄 높이(아이콘 20dp + 위아래 4dp). */
private val CANDIDATE_ROW_HEIGHT_COMPACT = 28.dp
