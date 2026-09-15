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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tftdeck.reader.data.Deck
import com.tftdeck.reader.data.ProfileState
import com.tftdeck.reader.data.Unit as DeckUnit
import com.tftdeck.reader.ui.copyToClipboard
import com.tftdeck.reader.ui.iconUrl
import com.tftdeck.reader.ui.tierColor
import com.tftdeck.reader.ui.traitStyleColor
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.material.icons.filled.EmojiEvents
import com.tftdeck.reader.ui.components.ThreeStarMark
import androidx.compose.ui.text.style.TextOverflow
/**
 * 게임 위에 뜨는 창의 내용.
 *
 * 게임을 가리면 안 되므로 기본은 접힌 칩이고, 눌러야 펼쳐진다.
 * 펼치면 **전체 덱 목록**이 먼저 나온다 — 인게임에서 뭘 갈지 고르는 게 주 용도라
 * 덱 하나만 띄워 두는 것보다 목록이 기본이어야 한다.
 * 목록에서 덱을 고르면 그 덱 요약으로 들어가고, 뒤로 누르면 목록으로 돌아온다.
 */
@Composable
fun OverlayContent(
    dataFlow: StateFlow<OverlayData?>,
    profileFlow: StateFlow<ProfileState>,
    selectedIdFlow: StateFlow<String?>,
    expandedFlow: StateFlow<Boolean>,
    wideFlow: StateFlow<Boolean>,
    showProfileFlow: StateFlow<Boolean>,
    onToggleExpand: () -> Unit,
    onToggleWide: () -> Unit,
    onToggleProfile: () -> Unit,
    onRefreshProfile: () -> Unit,
    onSelectDeck: (String?) -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onOpenApp: () -> Unit,
    onClose: () -> Unit,
) {
    val data by dataFlow.collectAsState()
    val selectedId by selectedIdFlow.collectAsState()
    val expanded by expandedFlow.collectAsState()
    val profileState by profileFlow.collectAsState()
    val wide by wideFlow.collectAsState()
    val showProfile by showProfileFlow.collectAsState()

    val decks = data?.decks.orEmpty()
    if (decks.isEmpty()) return
    val assetBase = data?.assetBase.orEmpty()
    val selected = decks.firstOrNull { it.id == selectedId }

    val dragModifier = Modifier.pointerInput(Unit) {
        detectDragGestures(
            onDragEnd = onDragEnd,
            onDrag = { change, offset ->
                change.consume()
                onDrag(offset.x, offset.y)
            },
        )
    }

    if (!expanded) {
        CollapsedChip(
            deck = selected,
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

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top,
    ) {
    Column(
        modifier = Modifier
            .widthIn(max = deckMax)
            .clip(RoundedCornerShape(14.dp))
            .background(OverlayScrim)
            .border(1.dp, OverlayBorder, RoundedCornerShape(14.dp)),
    ) {
        // 헤더 전체가 드래그 손잡이다. 본문을 스크롤할 수 있도록 분리한다.
        Row(
            modifier = dragModifier
                .fillMaxWidth()
                .background(OverlayHeader)
                .padding(start = 6.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected == null) {
                Icon(
                    Icons.Default.DragIndicator,
                    contentDescription = "옮기기",
                    tint = OverlayMuted,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "덱 ${decks.size}",
                    color = OverlayText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
            } else {
                IconBtn(Icons.AutoMirrored.Filled.ArrowBack, "목록으로") { onSelectDeck(null) }
                // 헤더에는 버튼이 많아 이름을 두면 한두 글자만 남는다. 이름은 본문 첫 줄로 내렸다.
                Text(
                    text = selected.tier,
                    color = tierColor(selected.tier),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
            }
            // 티어 카드만 따로 켜고 끈다. 전적을 연결하지 않았으면 끌 카드가 없으니 숨긴다.
            if (profileState.profileOrNull != null) {
                TintedIconBtn(
                    Icons.Default.EmojiEvents,
                    if (showProfile) "티어 카드 숨기기" else "티어 카드 보기",
                    if (showProfile) OverlayAccent else OverlayMuted,
                    onToggleProfile,
                )
            }
            IconBtn(
                if (wide) Icons.Default.CloseFullscreen else Icons.Default.OpenInFull,
                if (wide) "좁게 보기" else "넓게 보기",
                onToggleWide,
            )
            IconBtn(Icons.Default.UnfoldLess, "접기", onToggleExpand)
            IconBtn(Icons.AutoMirrored.Filled.OpenInNew, "앱 열기", onOpenApp)
            IconBtn(Icons.Default.Close, "닫기", onClose)
        }

        if (selected == null) {
            DeckListView(decks, assetBase, wide) { onSelectDeck(it.id) }
        } else {
            DeckSummaryView(selected, assetBase, wide)
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

// ---------------------------------------------------------------------------
// 접힌 상태
// ---------------------------------------------------------------------------

@Composable
private fun CollapsedChip(
    deck: Deck?,
    deckCount: Int,
    assetBase: String,
    modifier: Modifier,
    onExpand: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(OverlayScrim)
            .border(1.dp, OverlayBorder, RoundedCornerShape(22.dp))
            .clickable(onClick = onExpand)
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        val carry = deck?.carry
        if (carry != null) {
            AsyncImage(
                model = iconUrl(assetBase, carry.icon),
                contentDescription = carry.name,
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, tierColor(deck.tier), CircleShape),
            )
            Text(
                text = deck.tier,
                color = tierColor(deck.tier),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeckListView(
    decks: List<Deck>,
    assetBase: String,
    wide: Boolean,
    onPick: (Deck) -> Unit,
) {
    LazyColumn(
        // 게임 화면을 너무 가리지 않도록 높이를 제한하고 나머지는 스크롤한다.
        modifier = Modifier.heightIn(max = 330.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(decks, key = { it.id }) { deck ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(deck) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                // 좁게 볼 때는 얼굴만으로도 어떤 덱인지 안다. 넓게 볼 때만 이름을 붙인다.
                if (wide) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TierLabel(deck.tier)
                        Text(
                            text = deck.name,
                            color = OverlayText,
                            fontSize = 10.sp,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        if (deck.metatft.onlyInChina) ChinaDot()
                    }
                    FlowRow(
                        modifier = Modifier.padding(start = 22.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        deck.units.forEach { unit -> Face(unit, assetBase, 26.dp) }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TierLabel(deck.tier)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            deck.units.forEach { unit -> Face(unit, assetBase, 26.dp) }
                        }
                        if (deck.metatft.onlyInChina) {
                            Spacer(Modifier.width(4.dp))
                            ChinaDot()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TierLabel(tier: String) {
    Text(
        text = tier,
        color = tierColor(tier),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.width(22.dp),
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
private fun DeckSummaryView(deck: Deck, assetBase: String, wide: Boolean) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .heightIn(max = 330.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 9.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = deck.name,
            color = OverlayText,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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

            deck.units.forEach { unit ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Face(unit, assetBase, 26.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = unit.name + if (unit.star >= 3) " ★★★" else "",
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

            if (deck.itemOrder.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("재료", color = OverlayMuted, fontSize = 10.sp)
                    Spacer(Modifier.width(5.dp))
                    deck.itemOrder.take(5).forEach { item ->
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

        deck.teamCode?.let { code ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(OverlayAccent.copy(alpha = 0.18f))
                    .clickable { copyToClipboard(context, "TFT 덱 코드", code.code) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = null,
                    tint = OverlayAccent,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "덱 코드 복사",
                    color = OverlayAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/**
 * 챔피언 얼굴 하나.
 * 캐리는 강조 테두리, 3성은 별로 구분한다 — 글자 없이 구분되는 정보만 남겼다.
 */
@Composable
private fun Face(unit: DeckUnit, assetBase: String, size: Dp) {
    Box(contentAlignment = Alignment.TopCenter) {
        AsyncImage(
            model = iconUrl(assetBase, unit.icon),
            contentDescription = unit.name,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(4.dp))
                .border(
                    if (unit.carry) 1.8.dp else 1.dp,
                    if (unit.carry) OverlayAccent else costTint(unit.cost),
                    RoundedCornerShape(4.dp),
                ),
        )
        if (unit.star >= 3) {
            ThreeStarMark(6.sp, Modifier.offset(y = (-2).dp))
        }
    }
}

@Composable
private fun IconBtn(icon: ImageVector, label: String, onClick: () -> Unit) =
    TintedIconBtn(icon, label, OverlayMuted, onClick)

/** 켜짐/꺼짐을 색으로 보여 줘야 하는 버튼용. */
@Composable
private fun TintedIconBtn(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(15.dp))
    }
}

// 오버레이 전용 색. 게임 위에 떠 있어야 해서 앱 테마와 무관하게 어두운 반투명으로 고정한다.
private val OverlayScrim = Color(0xF20E1413)
private val OverlayHeader = Color(0xFF18211F)
private val OverlayBorder = Color(0x4D4FC2A3)
private val OverlayText = Color(0xFFE6EDEA)
private val OverlayMuted = Color(0xFF8FA29C)
private val OverlayAccent = Color(0xFF4FC2A3)
private val OverlayAmber = Color(0xFFD9A441)
private val OverlayStar = Color(0xFFE0B348)

/** 오버레이용 코스트 테두리 색. 어두운 배경 위에서 읽히도록 앱 테마와 따로 둔다. */
private fun costTint(cost: Int?): Color = when (cost) {
    1 -> Color(0xFF6E7C78)
    2 -> Color(0xFF3E9E86)
    3 -> Color(0xFF4E82B4)
    4 -> Color(0xFF9A5FB0)
    5 -> Color(0xFFC69A3C)
    else -> Color(0xFF4A5450)
}
