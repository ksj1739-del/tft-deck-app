package com.tftdeck.reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
 * 앱 덱 목록과 오버레이가 같은 모양을 쓴다. 오버레이는 앱 테마 밖에서 그려지므로 색·글자 크기를 전부 직접 준다
 * (다크 전용 [FloaColors] 토큰).
 *
 * 오버레이 창은 평소 포커스를 받지 않아 키보드를 띄울 수 없다. 그래서 [editing] 이 false 인 동안에는 입력칸 대신
 * 안내 글자를 그리고, 누르면 [onStartEditing] 으로 창 포커스를 먼저 요청한다. 앱은 늘 [editing] = true 다.
 * [compact] 는 오버레이용 작은 크기.
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
    val fontSize = if (compact) 11.sp else 14.sp
    val hint = when {
        tokens.isNotEmpty() -> "조건 더하기"
        // 오버레이(compact)는 검색 줄 옆에 등급 칸이 붙어 좁다. 띄어쓰기를 빼서 한 줄에 더 들게 한다.
        compact -> "유닛·시너지·아이템·증강"
        else -> "유닛 · 시너지 · 아이템 · 증강"
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
            .padding(horizontal = if (compact) 6.dp else 10.dp, vertical = if (compact) 3.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            tint = FloaColors.OnSurfaceVariant,
            modifier = Modifier.size(if (compact) 14.dp else 18.dp),
        )
        Spacer(Modifier.width(if (compact) 5.dp else 8.dp))
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            tokens.forEach { token -> TokenChip(token, compact, onRemove = { onRemoveToken(token) }) }
            if (editing) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = FloaColors.OnSurface, fontSize = fontSize),
                    cursorBrush = SolidColor(FloaColors.Secondary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                    modifier = Modifier
                        .widthIn(min = if (compact) 64.dp else 96.dp)
                        .focusRequester(focusRequester)
                        .onFocusChanged { focused = it.isFocused },
                    decorationBox = { inner ->
                        // 칩과 같은 높이로 두어 한 줄에 섞여도 글자 높이가 맞는다.
                        Box(Modifier.height(lineHeight), contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text(hint, color = FloaColors.OnSurfaceVariant, fontSize = fontSize, maxLines = 1)
                            }
                            inner()
                        }
                    },
                )
            } else {
                // 안내 글자도 직접 누르는 자리로 둔다. 칩이 여러 줄로 접히면 바로 위 칩의 ×(작아서 Compose 가 누르는 범위를
                // 48dp 로 넓힌다)가 '조건 더하기' 누름을 가로채 조건이 빠지곤 했다 — 직접 맞은 쪽이 넓힌 범위보다 먼저다.
                Box(
                    Modifier
                        .height(lineHeight)
                        .widthIn(min = if (compact) 64.dp else 96.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            onStartEditing()
                        },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(hint, color = FloaColors.OnSurfaceVariant, fontSize = fontSize, maxLines = 1)
                }
            }
        }
        if (tokens.isNotEmpty()) {
            Spacer(Modifier.width(4.dp))
            if (compact) {
                // 오버레이는 폭이 좁아(등급 칸이 옆에 붙는다) 글자 대신 원 안 × 아이콘으로 둔다.
                // 칩의 ×(그 조건만 빼기)와 모양이 달라 헷갈리지 않는다.
                Box(
                    Modifier
                        .size(CHIP_HEIGHT_COMPACT + 4.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onClearAll),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Cancel,
                        contentDescription = "조건 모두 지우기",
                        tint = FloaColors.Secondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            } else {
                Text(
                    "모두 지우기",
                    color = FloaColors.Secondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onClearAll)
                        .padding(horizontal = 4.dp, vertical = 3.dp),
                )
            }
        }
    }
}

/** 고른 조건 하나: '니달리 ×'. ×를 누르면 그 조건만 뺀다. 사용자 지정은 따옴표로 감싸 보인다. */
@Composable
fun TokenChip(token: DeckToken, compact: Boolean, onRemove: () -> Unit) {
    val height = if (compact) CHIP_HEIGHT_COMPACT else CHIP_HEIGHT
    Row(
        Modifier
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(FloaColors.PrimaryContainer)
            .padding(start = if (compact) 7.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            token.chipLabel,
            color = FloaColors.OnPrimaryContainer,
            fontSize = if (compact) 10.5.sp else 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = if (compact) 110.dp else 180.dp),
        )
        Box(
            Modifier
                .size(height)
                .clip(CircleShape)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "${token.name} 조건 빼기",
                tint = FloaColors.OnPrimaryContainer,
                modifier = Modifier.size(if (compact) 12.dp else 16.dp),
            )
        }
    }
}

/**
 * 후보 한 줄: 아이콘 · 이름 · 축 이름(유닛/시너지/아이템/증강/사용자 지정) · 덱 수.
 * 덱 수는 지금 목록에 이 조건을 더했을 때 남는 수라, 0이면 흐리게 한다(골라도 목록이 빈다).
 */
@Composable
fun TokenCandidateRow(
    candidate: TokenCandidate,
    assetBase: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val token = candidate.token
    val iconSize = if (compact) 20.dp else 28.dp
    val nameSize = if (compact) 11.5.sp else 14.sp
    val subSize = if (compact) 9.5.sp else 12.sp
    Row(
        modifier
            .fillMaxWidth()
            .alpha(if (candidate.deckCount == 0) 0.5f else 1f)
            .clickable(onClick = onClick)
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
        Spacer(Modifier.width(if (compact) 7.dp else 10.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = FloaColors.OnSurface, fontWeight = FontWeight.Medium)) {
                    append(token.chipLabel)
                }
                withStyle(SpanStyle(color = FloaColors.OnSurfaceVariant, fontSize = subSize)) {
                    append(" · ")
                    append(token.axisLabel)
                }
            },
            fontSize = nameSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(6.dp))
        Text("덱 ${candidate.deckCount}", color = FloaColors.OnSurfaceVariant, fontSize = subSize, maxLines = 1)
    }
}

private val CHIP_HEIGHT = 28.dp
private val CHIP_HEIGHT_COMPACT = 20.dp
