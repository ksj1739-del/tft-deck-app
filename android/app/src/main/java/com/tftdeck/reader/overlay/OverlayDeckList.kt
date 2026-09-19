package com.tftdeck.reader.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tftdeck.reader.data.Deck
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter

@Composable
internal fun DeckListView(
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

/** 목록 줄 앞의 등급 칸 폭. 설명·얼굴 줄도 이만큼 들여 별칭과 줄을 맞춘다. */
private val TIER_COLUMN = 22.dp

/** 목록 줄 설명의 최대 줄 수. 짧은 운영·캐리 요약이라 두 줄이면 넉넉하고, 더 길면 말줄임으로 줄 높이를 지킨다. */
private const val SUMMARY_MAX_LINES = 2

/**
 * 덱 목록 높이 상한(세로 화면 기준). 예전 330dp 에서 검색 줄 높이만큼 뺐다.
 * 가로 화면처럼 화면이 낮으면 패널 상한([PANEL_SCREEN_MARGIN])에 맞춰 이보다 줄어든다.
 */
private val DECK_LIST_MAX = 300.dp
