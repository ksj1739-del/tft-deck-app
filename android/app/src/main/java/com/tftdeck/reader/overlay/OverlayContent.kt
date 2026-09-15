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
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.tftdeck.reader.ui.theme.FloaColors
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tftdeck.reader.data.BuildupPick
import com.tftdeck.reader.data.BuildupPlanner
import com.tftdeck.reader.data.CatalogIndex
import com.tftdeck.reader.data.Deck
import com.tftdeck.reader.data.DeckPrefs
import com.tftdeck.reader.data.DeckRepository
import com.tftdeck.reader.data.DeckSearch
import com.tftdeck.reader.data.DeckSortMode
import com.tftdeck.reader.data.FeedState
import com.tftdeck.reader.data.ProfileState
import com.tftdeck.reader.data.Unit as DeckUnit
import com.tftdeck.reader.ui.bucketLabel
import com.tftdeck.reader.ui.components.ThreeStarMark
import com.tftdeck.reader.ui.copyToClipboard
import com.tftdeck.reader.ui.formatAvg
import com.tftdeck.reader.ui.gradeColor
import com.tftdeck.reader.ui.iconUrl
import com.tftdeck.reader.ui.tierColor
import com.tftdeck.reader.ui.traitStyleColor
import kotlinx.coroutines.flow.StateFlow

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

    val context = LocalContext.current
    val prefs = remember { DeckPrefs.get(context) }
    val repository = remember { DeckRepository.get(context) }
    val savedBucket by prefs.bucket.collectAsState()
    val pinned by prefs.pinned.collectAsState()
    val hidden by prefs.hidden.collectAsState()
    val feedState by repository.state.collectAsState()
    val feed = (feedState as? FeedState.Ready)?.feed
    val buckets = feed?.buckets.orEmpty()
    // 저장해 둔 구간이 이번 피드에 없으면 피드의 기본 구간으로 본다.
    val bucket = if (feed == null || buckets.isEmpty() || savedBucket in buckets) savedBucket else feed.defaultBucket
    val catalog = remember(feed) { feed?.let { CatalogIndex(it.catalog) } }
    val metatftCompared = feed?.version?.metatftCompared ?: true

    val allDecks = data?.decks.orEmpty()
    if (allDecks.isEmpty()) return
    val assetBase = data?.assetBase.orEmpty()
    // 목록: 숨긴 덱과 그 구간에 기록이 없는 덱은 빼고(고정한 덱은 남긴다), 그 구간 등급순으로, 고정한 덱을 맨 위로.
    val decks = remember(allDecks, bucket, pinned, hidden) {
        DeckSearch.pinFirst(
            DeckSearch.sort(
                allDecks.filter { it.id !in hidden && (it.appearsIn(bucket) || it.id in pinned) },
                DeckSortMode.GRADE,
                bucket,
            ),
            pinned,
        )
    }
    // 고른 덱은 숨긴 덱이어도 찾는다(덱 상세의 '게임 위에 띄우기'로 바로 열 수 있다).
    val selected = allDecks.firstOrNull { it.id == selectedId }

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
                )
                Spacer(Modifier.width(6.dp))
                // 구간 이름. 누를 때마다 전체 → 마스터+ → 다이아+ → 골드~에메랄드 → 골드 이하로 넘어가고 앱에도 저장된다.
                if (buckets.isNotEmpty()) {
                    Text(
                        buckets[bucket]?.label?.takeIf { it.isNotBlank() } ?: bucketLabel(bucket),
                        color = OverlayAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(OverlayAccent.copy(alpha = 0.15f))
                            .clickable { prefs.setBucket(DeckSearch.nextBucket(bucket, buckets.keys)) }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
            } else {
                IconBtn(Icons.AutoMirrored.Filled.ArrowBack, "목록으로") { onSelectDeck(null) }
                // 헤더에는 버튼이 많아 이름을 두면 한두 글자만 남는다. 이름은 본문 첫 줄로 내렸다.
                Text(
                    text = selected.gradeFor(bucket) ?: "-",
                    color = gradeTint(selected, bucket),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                // 덱 코드 복사는 본문 버튼 대신 헤더의 작은 아이콘으로 두어 패널을 얇게 한다.
                selected.teamCode?.let { code ->
                    TintedIconBtn(Icons.Default.ContentCopy, "덱 코드 복사", OverlayAccent) {
                        copyToClipboard(context, "TFT 덱 코드", code.code)
                    }
                }
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
            DeckListView(decks, bucket, pinned, metatftCompared, assetBase, wide) { onSelectDeck(it.id) }
        } else {
            DeckSummaryView(selected, catalog, assetBase, wide)
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

/** 통계 등급은 등급색, 편집 등급으로 대신 보여 줄 때는 편집 등급색. 등급이 없으면 흐린 색. */
private fun gradeTint(deck: Deck, bucket: String): Color {
    val grade = deck.gradeFor(bucket) ?: return OverlayMuted
    return if (deck.showsEditorialGrade(bucket)) tierColor(grade) else gradeColor(grade)
}

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
                text = deck.gradeFor(bucket) ?: "-",
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeckListView(
    decks: List<Deck>,
    bucket: String,
    pinned: Set<String>,
    metatftCompared: Boolean,
    assetBase: String,
    wide: Boolean,
    onPick: (Deck) -> Unit,
) {
    if (decks.isEmpty()) {
        Text(
            "숨기지 않은 덱이 없습니다. 앱의 '숨긴 덱 보기'에서 복구할 수 있습니다.",
            color = OverlayMuted,
            fontSize = 10.sp,
            modifier = Modifier.padding(10.dp),
        )
        return
    }
    LazyColumn(
        // 게임 화면을 너무 가리지 않도록 높이를 제한하고 나머지는 스크롤한다.
        modifier = Modifier.heightIn(max = 330.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(decks, key = { it.id }) { deck ->
            val chinaOnly = metatftCompared && deck.isOnlyInChina
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(deck) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                // 목록은 넓게 볼 때도 얼굴만 둔다. 덱 이름·평균 등수는 게임을 가려서 뺐다.
                run {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TierLabel(deck, bucket)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            deck.units.forEach { unit -> Face(unit, assetBase, 26.dp) }
                        }
                        if (deck.id in pinned) {
                            Spacer(Modifier.width(3.dp))
                            PinMark()
                        }
                        if (chinaOnly) {
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
private fun TierLabel(deck: Deck, bucket: String) {
    Text(
        text = deck.gradeFor(bucket) ?: "-",
        color = gradeTint(deck, bucket),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = Modifier.width(22.dp),
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
) {
    // 레벨 칩: 빌드업(글로벌·중국·작가 단계)이 있는 레벨. 기본 선택 규칙은 덱 상세와 같다.
    val levels = remember(deck) { BuildupPlanner.levels(deck) }
    var level by remember(deck.id) { mutableStateOf(BuildupPlanner.defaultLevel(deck, levels)) }
    val pick = level?.let { BuildupPlanner.topPick(deck, it) }

    Column(
        modifier = Modifier
            .heightIn(max = 330.dp)
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
                onSelect = { level = it },
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
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        levels.forEach { lv ->
            val on = lv == selected
            val shape = RoundedCornerShape(10.dp)
            Box(
                Modifier
                    .alpha(if (dim(lv) && !on) 0.5f else 1f)
                    .clip(shape)
                    .background(if (on) OverlayAccent.copy(alpha = 0.28f) else OverlayHeader)
                    .border(1.dp, if (on) OverlayAccent else OverlayBorder, shape)
                    .clickable { onSelect(lv) }
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            ) {
                Text(
                    "${lv}렙",
                    color = if (on) OverlayAccent else OverlayText,
                    fontSize = 10.sp,
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
private val OverlayScrim = FloaColors.Surface.copy(alpha = 0.95f)
private val OverlayHeader = FloaColors.SurfaceElevated
private val OverlayBorder = FloaColors.Secondary.copy(alpha = 0.3f)
private val OverlayText = FloaColors.OnSurface
private val OverlayMuted = FloaColors.OnSurfaceVariant
private val OverlayAccent = FloaColors.Secondary
private val OverlayAmber = FloaColors.Gold

/** 오버레이용 코스트 테두리 색. 어두운 배경 위에서 읽히도록 앱 테마와 따로 둔다. */
private fun costTint(cost: Int?): Color = when (cost) {
    1 -> Color(0xFF6E7C78)
    2 -> Color(0xFF3E9E86)
    3 -> Color(0xFF4E82B4)
    4 -> Color(0xFF9A5FB0)
    5 -> Color(0xFFC69A3C)
    else -> Color(0xFF4A5450)
}
