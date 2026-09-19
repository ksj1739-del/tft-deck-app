package com.tftdeck.reader.overlay

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tftdeck.reader.R
import com.tftdeck.reader.data.Deck
import com.tftdeck.reader.ui.iconUrl

/**
 * 접힌 칩. 누르면 마지막 상태(고른 덱이 있으면 요약, 없으면 목록)로, 길게 누르면 목록으로 펼친다([onExpandToList], F13 —
 * 새 판에 지난 판 덱 요약부터 열리지 않게). 펼치면 칩이 있던 자리에 '접기'가 온다(서비스의 칩 자리 기준 배치).
 * 크기는 그대로 둔다(얼굴 30dp + 좌우 9dp).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CollapsedChip(
    deck: Deck?,
    bucket: String,
    deckCount: Int,
    assetBase: String,
    modifier: Modifier,
    onExpand: () -> Unit,
    onExpandToList: (() -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    val expandLabel = stringResource(R.string.overlay_expand)
    val expandListLabel = stringResource(R.string.overlay_expand_list)
    Row(
        modifier = modifier
            // 덱을 고르지 않은 칩('덱 87')은 글자만 있어 30dp 남짓이었다. 게임 중 한 번에 눌리도록 높이를 맞춘다.
            .heightIn(min = CHIP_MIN_HEIGHT)
            .clip(RoundedCornerShape(22.dp))
            .background(OverlayScrim)
            .border(1.dp, OverlayBorder, RoundedCornerShape(22.dp))
            .combinedClickable(
                onClickLabel = expandLabel,
                onLongClickLabel = if (onExpandToList != null) expandListLabel else null,
                onLongClick = onExpandToList?.let { expandToList ->
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        expandToList()
                    }
                },
                onClick = onExpand,
            )
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
            Text(text = gradeText(deck, bucket), color = tint, style = OverlayChromeType.title)
        } else {
            // 고른 덱이 없으면 목록으로 들어간다는 뜻으로 개수만 보여 준다.
            Text(text = "덱 $deckCount", color = OverlayAccent, style = OverlayChromeType.title)
        }
    }
}

/** 접힌 칩 최소 높이. 게임 중 엄지로 한 번에 눌리는 크기. */
private val CHIP_MIN_HEIGHT = 40.dp
