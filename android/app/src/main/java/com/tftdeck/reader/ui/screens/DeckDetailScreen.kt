package com.tftdeck.reader.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tftdeck.reader.data.AugmentStat
import com.tftdeck.reader.data.Augments
import com.tftdeck.reader.data.BucketMeta
import com.tftdeck.reader.data.BuildupOrigin
import com.tftdeck.reader.data.BuildupPick
import com.tftdeck.reader.data.BuildupPlanner
import com.tftdeck.reader.data.Catalog
import com.tftdeck.reader.data.CatalogIndex
import com.tftdeck.reader.data.CellStat
import com.tftdeck.reader.data.CounterDeck
import com.tftdeck.reader.data.Deck
import com.tftdeck.reader.data.DeckKeys
import com.tftdeck.reader.data.DeckStats
import com.tftdeck.reader.data.DeckWearer
import com.tftdeck.reader.data.Editorial
import com.tftdeck.reader.data.FeedState
import com.tftdeck.reader.data.ItemRef
import com.tftdeck.reader.data.KeyUnit
import com.tftdeck.reader.data.LevelShare
import com.tftdeck.reader.data.Stage
import com.tftdeck.reader.data.TeamCode
import com.tftdeck.reader.data.Variant
import com.tftdeck.reader.data.withEditorial
import com.tftdeck.reader.data.Unit as DeckUnit
import com.tftdeck.reader.ui.AppViewModel
import com.tftdeck.reader.ui.bucketLabel
import com.tftdeck.reader.ui.components.BoardSlot
import com.tftdeck.reader.ui.components.EmptyState
import com.tftdeck.reader.ui.components.FaceRow
import com.tftdeck.reader.ui.components.FloaFilterChip
import com.tftdeck.reader.ui.components.GradeBadge
import com.tftdeck.reader.ui.components.GradeBadgeStyle
import com.tftdeck.reader.ui.components.HexBoard
import com.tftdeck.reader.ui.components.ItemIcons
import com.tftdeck.reader.ui.components.SectionTitle
import com.tftdeck.reader.ui.components.TextBadge
import com.tftdeck.reader.ui.components.TraitChip
import com.tftdeck.reader.ui.components.TrendGlyph
import com.tftdeck.reader.ui.components.UnitGrid
import com.tftdeck.reader.ui.components.UnitPortrait
import com.tftdeck.reader.ui.components.faceFor
import com.tftdeck.reader.ui.copyToClipboard
import com.tftdeck.reader.ui.formatAvg
import com.tftdeck.reader.ui.formatAvgRank
import com.tftdeck.reader.ui.formatDelta
import com.tftdeck.reader.ui.formatPct
import com.tftdeck.reader.ui.scopeLabel
import com.tftdeck.reader.ui.theme.FloaColors
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * 덱 상세(2026-09-19 UX 검토 WP-A3). 세로 스크롤 하나에 게임에서 쓰는 순서대로 둔다(D7):
 *  머리말 — 등급·추세·별칭 / 운영·마무리 레벨 한 줄 / 주 특성 / 네 수치 / 기준 구간 ▾ · ⓘ / [오버레이로 보기][코드 복사]
 *  → ① 캐리·아이템 → ② 레벨별 구성 → ③ 추천 증강 → ④ 배치 → ⑤ 더 보기(비슷한 구성·상대하기 어려운 덱, 접힘)
 *  → ⑥ 데이터 출처(시트, [DeckSourceSheet]).
 *
 * - 표본 수·출처 이름·지역별 비교·중국어 원문은 본문에 두지 않고 '데이터 출처' 시트에만 둔다(MASTER 규칙 2, D1·D2·D8·D15·D18).
 * - 구간은 이 화면 안에서만 바뀐다. 목록·오버레이가 함께 쓰는 저장된 구간(DeckPrefs)은 건드리지 않는다(D5·N12).
 * - 스크롤이 머리말을 지나면 화면 위에 [별칭][코드 복사] 띠를 고정한다(D6).
 * - 상대 덱에서 다른 상세로 갈 때는 [onOpenDeck] 을 부른다. 앞 상세를 대체하는 popUpTo 는 내비게이션을 가진 MainActivity 몫이다(N15b).
 *
 * [initialVariant] 는 목록의 변형 행에서 들어올 때 붙는다. '더 보기'의 비슷한 구성을 펼쳐 그 행을 열고 그 자리로 내려간다.
 */
@Composable
fun DeckDetailScreen(
    deckId: String,
    viewModel: AppViewModel,
    overlayRunning: Boolean,
    onStartOverlay: (String) -> Unit,
    initialVariant: String? = null,
    onOpenDeck: (String) -> Unit = {},
) {
    val baseDeck = viewModel.deck(deckId)
    val assetBase by viewModel.assetBase.collectAsState()
    val pinned by viewModel.pinnedDeckId.collectAsState()
    val state by viewModel.feedState.collectAsState()
    val feed = (state as? FeedState.Ready)?.feed
    val context = LocalContext.current

    if (baseDeck == null) {
        EmptyState("덱을 찾을 수 없습니다", "새로고침으로 목록에서 빠졌을 수 있습니다 · 목록에서 다시 고르세요")
        return
    }

    val catalog = remember(feed) { viewModel.catalog() } ?: EMPTY_CATALOG

    // 처음에는 목록의 구간을 따르고, 여기서 바꾼 구간은 이 화면에만 쓴다(저장하지 않는다).
    var bucket by rememberSaveable(deckId) { mutableStateOf(viewModel.bucket.value) }

    // 한 그룹에 작가가 다른 편집 덱이 여럿 붙기도 한다(moreEditorials). 작가를 고르면 그 작가의
    // 보드·빌드업 작가 행·증강·조합 재료·덱 코드·원문으로 화면 전체가 바뀐다. 통계는 그룹 것 그대로다.
    val editorials = baseDeck.editorials
    var authorIndex by rememberSaveable(deckId) { mutableStateOf(0) }
    val deck = remember(baseDeck, catalog, authorIndex) {
        editorials.getOrNull(authorIndex)?.takeIf { authorIndex > 0 }?.let { baseDeck.withEditorial(it, catalog) }
            ?: baseDeck
    }
    var showSources by rememberSaveable(deckId) { mutableStateOf(false) }

    val showingThisDeck = overlayRunning && pinned == deck.id
    val copyCode: (() -> Unit)? = deck.teamCode?.code?.takeIf { it.isNotBlank() }?.let { code ->
        { copyToClipboard(context, "TFT 덱 코드", code) }
    }

    // 머리말(버튼까지)이 화면 위로 다 올라가면 고정 띠를 띄운다. 위치는 스크롤 내용 기준이라 스크롤과 무관하다.
    val scrollState = rememberScrollState()
    var headerBottom by remember { mutableIntStateOf(Int.MAX_VALUE) }
    val showPinnedBar by remember { derivedStateOf { scrollState.value > headerBottom } }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        ) {
            DetailHeader(
                deck = deck,
                bucket = bucket,
                buckets = feed?.buckets.orEmpty(),
                assetBase = assetBase,
                editorials = editorials,
                authorIndex = authorIndex,
                showingThisDeck = showingThisDeck,
                onBucket = { bucket = it },
                onAuthor = { authorIndex = it },
                onSources = { showSources = true },
                onOverlay = {
                    viewModel.pinDeck(deck.id)
                    onStartOverlay(deck.id)
                },
                onCopy = copyCode,
                modifier = Modifier.onGloballyPositioned { coordinates ->
                    headerBottom = (coordinates.positionInParent().y + coordinates.size.height).roundToInt()
                },
            )

            CarriesSection(deck, catalog, assetBase)
            BuildupSection(deck, catalog, assetBase)
            AugmentsSection(deck, assetBase)
            BoardSection(deck, assetBase)
            MoreSection(
                deck = deck,
                bucket = bucket,
                catalog = catalog,
                assetBase = assetBase,
                resolveDeck = { id -> viewModel.deck(id) },
                onOpenDeck = onOpenDeck,
                initialVariant = initialVariant,
            )
            SourcesRow(onClick = { showSources = true })
        }

        AnimatedVisibility(
            visible = showPinnedBar,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(100)),
        ) {
            PinnedBar(deck.displayAlias, copyCode)
        }
    }

    if (showSources) {
        DeckSourceSheet(deck = deck, bucket = bucket, feed = feed, onDismiss = { showSources = false })
    }
}

private val EMPTY_CATALOG = CatalogIndex(Catalog())

// ---------------------------------------------------------------------------
// 머리말
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailHeader(
    deck: Deck,
    bucket: String,
    buckets: Map<String, BucketMeta>,
    assetBase: String,
    editorials: List<Editorial>,
    authorIndex: Int,
    showingThisDeck: Boolean,
    onBucket: (String) -> Unit,
    onAuthor: (Int) -> Unit,
    onSources: () -> Unit,
    onOverlay: () -> Unit,
    onCopy: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val stats = deck.displayStats(bucket)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TitleRow(deck, bucket, stats)
            // 레벨은 이 한 줄에서만 말한다(D3): 운영 · 마무리 레벨 분포. '레벨 완성'·긴 원래 이름은 두지 않는다(D1·D4).
            val levelLine = DeckDetailLogic.levelLine(
                levelling = deck.global?.levelling,
                finalLevel = deck.finalLevel,
                finalLevels = deck.global?.finalLevels.orEmpty(),
            )
            val stale = deck.hasEditorial && deck.isEditorialStale
            if (levelLine != null || stale) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    levelLine?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    if (stale) {
                        if (levelLine != null) Spacer(Modifier.width(8.dp))
                        TextBadge("이전 패치")
                    }
                }
            }
        }

        if (deck.traits.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                deck.traits.take(HEADER_TRAITS).forEach { TraitChip(it, assetBase) }
                val more = deck.traits.size - HEADER_TRAITS
                if (more > 0) MoreChip("+$more")
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (deck.stats.isNotEmpty() || deck.isGlobalOnly) {
                // 중국 한정 덱의 픽률은 중국 목록 안에서의 비율이라 metatft 픽률과 나란히 둘 수 없다(카드와 같은 규칙).
                DetailStatsRow(stats, hidePick = deck.isOnlyInChina && !deck.isMeta)
            }
            BasisRow(
                deck = deck,
                bucket = bucket,
                buckets = buckets,
                editorials = editorials,
                authorIndex = authorIndex,
                onBucket = onBucket,
                onAuthor = onAuthor,
                onSources = onSources,
            )
        }

        ActionButtons(showingThisDeck = showingThisDeck, onOverlay = onOverlay, onCopy = onCopy)

        // 본문에 남기는 출처 글은 행동을 바꾸는 경고 하나뿐이다(D8): 덱 코드에 빠지는 챔피언.
        deck.teamCode?.let { DeckDetailLogic.partialCodeNote(it) }?.let { note ->
            Text(note, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
    }
}

/** [등급 배지][중국][표본 적음][▲▼] 별칭. 배지 묶음은 별칭 첫 줄 높이에 맞춰 두 줄로 꺾여도 첫 줄 옆에 선다. */
@Composable
private fun TitleRow(deck: Deck, bucket: String, stats: DeckStats?) {
    val firstLine = with(LocalDensity.current) { MaterialTheme.typography.titleLarge.lineHeight.toDp() }
    val grade = deck.gradeFor(bucket)
    Row(verticalAlignment = Alignment.Top) {
        Row(
            Modifier.heightIn(min = firstLine),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (grade != null) GradeBadge(grade, gradeStyle(deck, bucket))
            if (deck.isOnlyInChina) TextBadge("중국")
            // 등급이 없거나(표본 부족) 이 구간 표본이 300판 미만이면 한 번만 알린다(D1: 표본은 경고로만).
            if (grade == null || DeckDetailLogic.thinSample(stats)) TextBadge("표본 적음")
            TrendGlyph(stats?.trend)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            deck.displayAlias,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 편집 등급으로 대신 보여 주면 편집 모양, 중국 한정 덱은 테두리형, 그 밖(metatft)은 채움(MASTER 규칙 5). */
private fun gradeStyle(deck: Deck, bucket: String): GradeBadgeStyle = when {
    deck.showsEditorialGrade(bucket) -> GradeBadgeStyle.Editorial
    deck.isOnlyInChina -> GradeBadgeStyle.Outlined
    else -> GradeBadgeStyle.Filled
}

/** 특성 칩 뒤 '+N'. 특성 칩과 같은 높이·모양. */
@Composable
private fun MoreChip(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * 머리말 네 수치: 평균 등수 · TOP4 · 픽률 · 승률. 값은 titleLarge(20sp). 카드는 앞 세 값만 두고(L4 결정 2)
 * 승률은 여기서 본다. 카드와 같은 자리에 같은 값이 오도록 승률을 끝에 붙인다.
 */
@Composable
private fun DetailStatsRow(stats: DeckStats?, hidePick: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val cells = listOf(
        "평균 등수" to formatAvg(stats?.avg),
        "TOP4" to formatPct(stats?.top4),
        "픽률" to if (hidePick) "–" else formatPct(stats?.pick),
        "승률" to formatPct(stats?.win),
    )
    Row(modifier.fillMaxWidth()) {
        cells.forEach { (label, value) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(value, style = MaterialTheme.typography.titleLarge, color = scheme.onSurface, maxLines = 1)
                Text(label, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/** '골드~에메랄드 기준 ▾ · 작가 ○○ ▾ … ⓘ'. 구간·작가는 이 화면에서만 바뀐다. ⓘ 는 데이터 출처 시트. */
@Composable
private fun BasisRow(
    deck: Deck,
    bucket: String,
    buckets: Map<String, BucketMeta>,
    editorials: List<Editorial>,
    authorIndex: Int,
    onBucket: (String) -> Unit,
    onAuthor: (Int) -> Unit,
    onSources: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val keys = DeckKeys.BUCKET_ORDER.filter { it in buckets }
        if (keys.isNotEmpty() && deck.stats.isNotEmpty()) {
            MenuButton(label = "${bucketLabel(bucket)} 기준") { close ->
                keys.forEach { key ->
                    val available = deck.statsFor(key) != null
                    DropdownMenuItem(
                        text = {
                            Text(
                                bucketLabel(key),
                                color = if (key == bucket) scheme.primary else scheme.onSurface,
                            )
                        },
                        trailingIcon = if (available) null else {
                            { Text("자료 없음", style = MaterialTheme.typography.labelSmall) }
                        },
                        enabled = available,
                        onClick = {
                            onBucket(key)
                            close()
                        },
                    )
                }
            }
        } else if (deck.isGlobalOnly) {
            deck.globalDisplayScope?.let { scope ->
                Text(
                    "${scopeLabel(scope, short = true)} 기준",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        if (editorials.size >= 2) {
            Spacer(Modifier.width(8.dp))
            val current = editorials.getOrNull(authorIndex) ?: editorials.first()
            MenuButton(
                label = "작가 ${authorName(current, authorIndex)}",
                modifier = Modifier.weight(1f, fill = false),
            ) { close ->
                editorials.forEachIndexed { index, editorial ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                listOfNotNull(
                                    authorName(editorial, index),
                                    editorial.quality?.takeIf { it.isNotBlank() }?.let { "등급 $it" },
                                ).joinToString(" · "),
                                color = if (index == authorIndex) scheme.primary else scheme.onSurface,
                            )
                        },
                        onClick = {
                            onAuthor(index)
                            close()
                        },
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onSources) {
            Icon(
                Icons.Filled.Info,
                contentDescription = "데이터 출처",
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun authorName(editorial: Editorial, index: Int): String =
    editorial.author.takeIf { it.isNotBlank() } ?: "${index + 1}"

/** 글자 + ▾ 모양의 드롭다운 단추. 글자는 누를 수 있음을 뜻하는 primary(MASTER 규칙 3). */
@Composable
private fun MenuButton(
    label: String,
    modifier: Modifier = Modifier,
    items: @Composable (close: () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        TextButton(onClick = { open = true }, contentPadding = PaddingValues(start = 0.dp, end = 4.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items { open = false }
        }
    }
}

/** [오버레이로 보기][코드 복사]: 같은 폭·높이 44dp·같은 글자색(V19). 덱 코드가 없으면 오버레이 단추가 전체 폭을 쓴다. */
@Composable
private fun ActionButtons(showingThisDeck: Boolean, onOverlay: () -> Unit, onCopy: (() -> Unit)?) {
    val colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
    val padding = PaddingValues(horizontal = 12.dp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = onOverlay,
            modifier = Modifier
                .weight(1f)
                .height(ACTION_HEIGHT),
            colors = colors,
            contentPadding = padding,
        ) {
            Icon(Icons.Filled.PictureInPictureAlt, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            // 고정해 둔 덱인지가 아니라 실제로 떠 있는지로 라벨을 정해야 헷갈리지 않는다.
            Text(if (showingThisDeck) "띄우는 중" else "오버레이로 보기", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onCopy != null) {
            OutlinedButton(
                onClick = onCopy,
                modifier = Modifier
                    .weight(1f)
                    .height(ACTION_HEIGHT),
                colors = colors,
                contentPadding = padding,
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("코드 복사", maxLines = 1)
            }
        }
    }
}

/** 머리말이 가려진 뒤 위에 고정하는 띠(D6): [별칭][코드 복사], 높이 40dp. 앱바와 같은 면이라 앱바가 늘어난 것처럼 보인다. */
@Composable
private fun PinnedBar(alias: String, onCopy: (() -> Unit)?) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .background(scheme.surface),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(PINNED_BAR_HEIGHT)
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                alias,
                style = MaterialTheme.typography.titleSmall,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (onCopy != null) {
                TextButton(onClick = onCopy, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("코드 복사", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        HorizontalDivider(color = scheme.outlineVariant)
    }
}

// ---------------------------------------------------------------------------
// ① 캐리·아이템
// ---------------------------------------------------------------------------

/**
 * 캐리별 아이템 3개 + 대체 아이템(예전 '아이템 배분'), 3성 목표(예전 '핵심 유닛' 칩에서 쓸모 있던 값 하나, D11),
 * 조합 재료 우선순위. 캐리가 아닌 아이템 착용자는 접어 둔다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CarriesSection(deck: Deck, catalog: CatalogIndex, assetBase: String) {
    val carries = deck.carries
    val holders = carries.filter { it.items.isNotEmpty() || it.itemsBackup.isNotEmpty() }
    val others = deck.units.filter { unit ->
        carries.none { it === unit } && (unit.items.isNotEmpty() || unit.itemsBackup.isNotEmpty())
    }
    val star3 = remember(deck) { DeckDetailLogic.star3Targets(deck.keyUnits) }
    // 캐리 행에 태그로 붙지 않은 3성 목표(리롤 덱의 아이템 없는 챔피언 등)는 따로 한 줄로 모은다.
    val holderIds = holders.map { it.id }.toSet()
    val targets = deck.units.filter { it.id !in holderIds && it.id in star3 && !it.isPet }.distinctBy { it.id }
    val order = deck.componentOrder
    if (holders.isEmpty() && others.isEmpty() && targets.isEmpty() && order.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    var showOthers by rememberSaveable(deck.id) { mutableStateOf(false) }

    SectionTitle("캐리·아이템")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        holders.forEach { ItemHolderRow(it, assetBase, large = true, star3 = star3[it.id]) }
        if (others.isNotEmpty()) {
            if (holders.isEmpty()) {
                others.forEach { ItemHolderRow(it, assetBase, large = false) }
            } else {
                ExpandRow(
                    label = if (showOthers) "나머지 접기" else "나머지 ${others.size}명 보기",
                    expanded = showOthers,
                    onToggle = { showOthers = !showOthers },
                )
                AnimatedVisibility(showOthers) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        others.forEach { ItemHolderRow(it, assetBase, large = false) }
                    }
                }
            }
        }
        if (targets.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SubLabel("3성 목표")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    targets.forEach { unit ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(scheme.surfaceVariant)
                                .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                        ) {
                            UnitPortrait(unit.icon, unit.name, unit.cost, assetBase, 24.dp, showStar = false, pet = unit.isPet)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${unit.name} ${formatPct(star3[unit.id])}",
                                style = MaterialTheme.typography.labelMedium,
                                color = scheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
        if (order.isNotEmpty()) ComponentOrder(deck, order, catalog, assetBase)
    }
}

@Composable
private fun ItemHolderRow(unit: DeckUnit, assetBase: String, large: Boolean, star3: Double? = null) {
    val scheme = MaterialTheme.colorScheme
    val face = if (large) 36.dp else 28.dp
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UnitPortrait(unit.icon, unit.name, unit.cost, assetBase, face, star = unit.star, carry = unit.carry, pet = unit.isPet)
            Spacer(Modifier.width(8.dp))
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 캐리 강조는 굵기로 한다. 파랑은 누를 수 있음·선택됨이다(V8).
                Text(
                    unit.name,
                    style = if (large) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface,
                    fontWeight = if (large) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                star3?.let { TextBadge("3성 목표 ${formatPct(it)}") }
            }
            Spacer(Modifier.width(8.dp))
            ItemIcons(unit.items, assetBase, size = if (large) 24 else 20)
        }
        if (unit.items.isNotEmpty()) {
            Text(
                unit.items.joinToString(" · ") { it.name },
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(start = face + 8.dp),
            )
        }
        if (unit.itemsBackup.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = face + 8.dp),
            ) {
                Text("대체", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                ItemIcons(unit.itemsBackup, assetBase, size = 16)
                Spacer(Modifier.width(8.dp))
                Text(
                    unit.itemsBackup.joinToString(" · ") { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 조합 재료 우선순위 칩 + 재료마다 그 완성템을 많이 드는 챔피언 1~2명. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ComponentOrder(deck: Deck, order: List<ItemRef>, catalog: CatalogIndex, assetBase: String) {
    val scheme = MaterialTheme.colorScheme
    val wearers = remember(deck, catalog) { order.associate { it.id to wearersForComponent(deck, catalog, it.id) } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SubLabel("조합 재료 우선순위")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            order.forEachIndexed { index, item ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(scheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text("${index + 1}", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    ItemIcons(listOf(item), assetBase, size = 20)
                    Spacer(Modifier.width(4.dp))
                    Text(item.name, style = MaterialTheme.typography.labelMedium, color = scheme.onSurface)
                    // 이 재료로 만드는 완성템을 실제로 많이 드는 챔피언. 누구에게 줄지 바로 보인다.
                    wearers[item.id].orEmpty().forEach { wearer ->
                        val entry = catalog.unit(wearer.id)
                        Spacer(Modifier.width(4.dp))
                        UnitPortrait(entry?.icon, entry?.name ?: wearer.name, entry?.cost, assetBase, 20.dp, showStar = false)
                    }
                }
            }
        }
    }
}

/**
 * 재료 칩 옆 추천 착용자 1~2명. 부품 → 완성템 → 착용자를 새로 추정하지 않고,
 * itemWearers 에 그 재료가 들어간 완성템이 있을 때만 그 착용자를 쓴다.
 * catalog 의 조합 재료 id 가 "TFT_Item_BFSword"·"DA_Component_BFSword"로 섞여 있어 끝 이름으로 맞춘다.
 */
private fun wearersForComponent(deck: Deck, catalog: CatalogIndex, componentId: String): List<DeckWearer> {
    val key = componentKey(componentId)
    return deck.itemWearers
        .filter { entry -> catalog.items[entry.item.id]?.components.orEmpty().any { componentKey(it) == key } }
        .flatMap { it.wearers }
        .distinctBy { it.id }
        .take(2)
}

private fun componentKey(id: String): String = id.substringAfterLast('_').lowercase()

// ---------------------------------------------------------------------------
// ② 레벨별 구성
// ---------------------------------------------------------------------------

/**
 * 레벨 칩(숫자만) + 그 레벨 캡션 '{L}레벨 {라운드} 도달 · 롤다운 {r}레벨' + 1순위 구성 한 줄(D9).
 * 등수·판수는 싣지 않고, 같은 출처의 아래 순위는 1순위 표본의 10% 이상만 '다른 구성'으로 접어 둔다.
 * 출처 글자(글로벌·중국·작가)는 그 레벨에 출처가 둘 이상일 때만 붙는다. 작성자 운영 메모는 출처 시트의 원문으로 옮겼다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BuildupSection(deck: Deck, catalog: CatalogIndex, assetBase: String) {
    val levels = remember(deck) { BuildupPlanner.levels(deck) }
    if (levels.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    // 작가를 바꾸면 작가 단계 레벨이 달라지므로 그 작가 기준 기본 레벨로 다시 고른다.
    var selected by rememberSaveable(deck.id, deck.editorial?.id) {
        mutableStateOf(BuildupPlanner.defaultLevel(deck, levels))
    }
    var showOthers by rememberSaveable(deck.id, selected) { mutableStateOf(false) }

    SectionTitle("레벨별 구성")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            levels.forEach { level ->
                FloaFilterChip(selected = level == selected, label = "$level", onClick = { selected = level })
            }
        }
        val level = selected?.takeIf { it in levels }
        if (level != null) {
            val caption = DeckDetailLogic.levelCaption(
                level = level,
                round = DeckDetailLogic.reachRound(deck, level),
                rollLevel = BuildupPlanner.rollLevel(deck),
            )
            if (caption.isNotEmpty()) {
                Text(caption, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
            }
            val rows = remember(deck, level) { DeckDetailLogic.buildupRows(BuildupPlanner.picks(deck, level)) }
            val top = rows.top
            if (top == null) {
                Text("이 레벨의 구성 자료가 없습니다", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            } else {
                BuildupRow(top, rows.showOrigin, deck, catalog, assetBase)
                if (rows.others.isNotEmpty()) {
                    ExpandRow(
                        label = if (showOthers) "다른 구성 접기" else "다른 구성 ${rows.others.size}개",
                        expanded = showOthers,
                        onToggle = { showOthers = !showOthers },
                    )
                    AnimatedVisibility(showOthers) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            rows.others.forEach { BuildupRow(it, rows.showOrigin, deck, catalog, assetBase) }
                        }
                    }
                }
            }
        }
    }
}

/** 구성 한 줄: 얼굴 줄(이전 레벨에 없던 챔피언은 Positive 점) + 필요할 때만 출처·단계 글자. */
@Composable
private fun BuildupRow(pick: BuildupPick, showOrigin: Boolean, deck: Deck, catalog: CatalogIndex, assetBase: String) {
    val newUnits = remember(deck, pick) { BuildupPlanner.newUnits(deck, pick) }
    val tag = DeckDetailLogic.buildupTag(pick, showOrigin)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        FaceRow(
            faces = pick.units.map { id ->
                faceFor(id, catalog.unit(id), pet = catalog.isPet(id), newMark = id in newUnits)
            },
            assetBase = assetBase,
            size = BUILDUP_FACE,
            modifier = Modifier.weight(1f),
            // 빌드업 단계에는 성급 정보가 없다. 별을 그리면 최종 보드와 헷갈린다.
            showStars = false,
        )
        if (tag.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Text(tag, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------------------
// ③ 추천 증강
// ---------------------------------------------------------------------------

/**
 * 추천 증강 한 목록(D12·V20). 편집 덱 작가가 고른 것만 '작가' 글자 배지를 달고, 통계 상위 증강과 id 로 합친다.
 * 편집 덱이 없을 때 수집기가 채워 둔 추천(중국 승률 조합의 추천 증강)은 작가 것이 아니므로 배지 없이 통계 행으로 둔다.
 * 등수는 표본 300판 이상일 때만 적고, 행을 흐리게 하지 않는다. 고른 시점(2-1·3-2·4-2)을 고르면 그 시점의 등수로 바뀐다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AugmentsSection(deck: Deck, assetBase: String) {
    val lines = remember(deck) { DeckDetailLogic.augmentLines(deck) }
    if (lines.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    var stage by rememberSaveable(deck.id) { mutableStateOf<Int?>(null) }
    val showStages = remember(lines) { DeckDetailLogic.hasStageValues(lines) }

    SectionTitle("추천 증강")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showStages) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "고른 시점",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp),
                )
                FloaFilterChip(selected = stage == null, label = "전체", onClick = { stage = null })
                DeckDetailLogic.STAGE_LABELS.forEachIndexed { index, label ->
                    FloaFilterChip(selected = stage == index, label = label, onClick = { stage = index })
                }
            }
        }
        lines.forEach { line -> AugmentRow(line, DeckDetailLogic.augmentValue(line.stat, stage), assetBase) }
    }
}

@Composable
private fun AugmentRow(line: DeckDetailLogic.AugmentLine, value: String?, assetBase: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 증강은 이름보다 아이콘으로 기억하는 경우가 많아 아이콘을 앞에 둔다.
        ItemIcons(listOf(ItemRef(line.id, line.name, line.icon)), assetBase, size = 24)
        Spacer(Modifier.width(8.dp))
        Text(
            line.name,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        line.tag?.let {
            Spacer(Modifier.width(8.dp))
            TextBadge(it)
        }
        value?.let {
            Spacer(Modifier.width(8.dp))
            Text(it, style = MaterialTheme.typography.labelLarge, color = scheme.onSurface, maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------------------
// ④ 배치
// ---------------------------------------------------------------------------

/**
 * 최종 배치 보드. 작가 좌표와 실측 칸이 둘 다 있을 때만 '작가 배치 | 많이 놓는 자리' 를 고른다(D10).
 * 좌표가 없으면 실측 칸만(전환 없음), 실측이 없으면 작가 좌표만. 칸의 비율은 10% 이상만 적는다 —
 * 겹쳐서 밀려난 챔피언이 2%·5% 칸에 서면 그 칸이 권장 자리로 읽혔다.
 */
@Composable
private fun BoardSection(deck: Deck, assetBase: String) {
    val scheme = MaterialTheme.colorScheme
    val hasCoords = deck.units.any { it.row != null && it.col != null }
    val hasPositions = deck.positions.isNotEmpty()
    val canChoose = hasCoords && hasPositions
    var measured by rememberSaveable(deck.id) { mutableStateOf(false) }
    val useMeasured = DeckDetailLogic.useMeasured(hasCoords, hasPositions, measured)
    val layout = remember(deck.units, deck.positions, useMeasured) {
        DeckDetailLogic.layoutBoard(deck.units, deck.positions, useMeasured)
    }

    SectionTitle("배치")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (canChoose) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FloaFilterChip(selected = !measured, label = "작가 배치", onClick = { measured = false })
                FloaFilterChip(selected = measured, label = "많이 놓는 자리", onClick = { measured = true })
            }
        }
        if (layout.slots.isNotEmpty()) {
            HexBoard(slots = layout.slots, assetBase = assetBase)
            if (useMeasured) {
                val note = listOfNotNull(
                    "많이 놓는 자리".takeIf { !canChoose },
                    "숫자는 그 칸에 놓은 비율",
                    "점은 작가 배치와 다른 칸".takeIf { layout.slots.any { it.diverges } },
                ).joinToString(" · ")
                Text(note, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            if (layout.unplaced.isNotEmpty()) {
                SubLabel("자리 정보 없음")
                UnitGrid(layout.unplaced, assetBase, compact = true)
            }
        } else {
            // 좌표도 실측 칸도 없으면 보드 대신 얼굴·아이템으로 보여 준다. 빈 보드는 데이터가 없는 것처럼 보인다.
            UnitGrid(deck.units, assetBase)
        }
    }
}

// ---------------------------------------------------------------------------
// ⑤ 더 보기: 비슷한 구성 · 상대하기 어려운 덱
// ---------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MoreSection(
    deck: Deck,
    bucket: String,
    catalog: CatalogIndex,
    assetBase: String,
    resolveDeck: (String) -> Deck?,
    onOpenDeck: (String) -> Unit,
    initialVariant: String?,
) {
    // 이 구간 표본이 큰 변형부터. 수치는 척도가 다른 중국 값이라 행에 싣지 않는다(D2·D13).
    val variants = remember(deck, bucket) { DeckDetailLogic.sortVariants(deck.otherVariants, bucket) }
    val counters = deck.global?.counters.orEmpty()
    if (variants.isEmpty() && counters.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    val entryVariant = initialVariant?.takeIf { id -> variants.any { it.id == id } }
    var showVariants by rememberSaveable(deck.id) { mutableStateOf(entryVariant != null) }
    var openVariant by rememberSaveable(deck.id) { mutableStateOf(entryVariant) }
    var showAllVariants by rememberSaveable(deck.id) {
        mutableStateOf(entryVariant != null && variants.indexOfFirst { it.id == entryVariant } >= VARIANTS_FIRST)
    }
    var showCounters by rememberSaveable(deck.id) { mutableStateOf(false) }
    val variantsRequester = remember { BringIntoViewRequester() }

    // 목록의 변형 행에서 들어왔을 때만 그 행으로 내려간다. 화면 안에서 누를 때는 제자리에서 펼친다(D13).
    LaunchedEffect(deck.id, entryVariant) {
        if (entryVariant != null) {
            delay(200)
            variantsRequester.bringIntoView()
        }
    }

    SectionTitle("더 보기")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (variants.isNotEmpty()) {
            Column(Modifier.bringIntoViewRequester(variantsRequester)) {
                ExpandRow(
                    label = "비슷한 구성 ${variants.size}개",
                    expanded = showVariants,
                    onToggle = { showVariants = !showVariants },
                )
                AnimatedVisibility(showVariants) {
                    Column {
                        val base = deck.units.map { it.id }
                        val shown = if (showAllVariants) variants else variants.take(VARIANTS_FIRST)
                        shown.forEach { variant ->
                            VariantRow(
                                variant = variant,
                                base = base,
                                deck = deck,
                                catalog = catalog,
                                assetBase = assetBase,
                                expanded = openVariant == variant.id,
                                onToggle = { openVariant = if (openVariant == variant.id) null else variant.id },
                            )
                        }
                        if (!showAllVariants && variants.size > VARIANTS_FIRST) {
                            ExpandRow(
                                label = "${variants.size - VARIANTS_FIRST}개 더 보기",
                                expanded = false,
                                onToggle = { showAllVariants = true },
                            )
                        }
                    }
                }
            }
        }
        if (counters.isNotEmpty()) {
            ExpandRow(
                label = "상대하기 어려운 덱 ${counters.size}개",
                expanded = showCounters,
                onToggle = { showCounters = !showCounters },
            )
            AnimatedVisibility(showCounters) {
                Column {
                    Text(
                        "이 덱을 만나면 평균 등수가 밀리는 정도",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                    counters.forEach { counter ->
                        CounterRow(counter, counter.deck?.let(resolveDeck), catalog, assetBase, onOpenDeck)
                    }
                }
            }
        }
    }
}

/** 비슷한 구성 한 줄: 보이는 보드 대비 바뀐 챔피언만 '−카직스 +럭스'. 누르면 그 자리에서 전체 얼굴 줄을 펼친다. */
@Composable
private fun VariantRow(
    variant: Variant,
    base: List<String>,
    deck: Deck,
    catalog: CatalogIndex,
    assetBase: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val text = remember(variant, base, deck, catalog) {
        val (minus, plus) = DeckDetailLogic.variantDiff(base, variant.units.map { it.id })
        fun name(id: String) = deck.units.firstOrNull { it.id == id }?.name ?: catalog.unit(id)?.name ?: id
        DeckDetailLogic.diffText(minus.map(::name), plus.map(::name))
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClickLabel = if (expanded) "접기" else "펼치기", onClick = onToggle)
            .heightIn(min = 44.dp)
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface, modifier = Modifier.weight(1f))
            if (variant.editorialId != null) {
                Spacer(Modifier.width(8.dp))
                TextBadge("작가")
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        if (expanded) {
            FaceRow(
                faces = variant.units.map { unit ->
                    faceFor(unit.id, catalog.unit(unit.id), carry = unit.id == variant.carryId, pet = catalog.isPet(unit.id))
                },
                assetBase = assetBase,
                size = VARIANT_FACE,
                // 변형 조합 원본에는 성급이 없다. 별을 그리지 않는다.
                showStars = false,
            )
        }
    }
}

/** 상대하기 어려운 덱 한 줄(D14): 캐리 얼굴 · 별칭 · '▲ +0.26등' · ›. 우리 목록에 없는 덱은 누를 수 없다. */
@Composable
private fun CounterRow(
    counter: CounterDeck,
    target: Deck?,
    catalog: CatalogIndex,
    assetBase: String,
    onOpenDeck: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val name = target?.displayAlias
        ?: DeckDetailLogic.counterName(counter.name) { id -> catalog.unit(id)?.name ?: catalog.traits[id]?.name }
        ?: "이름 없는 구성"
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(if (target != null) Modifier.clickable { onOpenDeck(target.id) } else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        target?.carries?.firstOrNull()?.let { carry ->
            UnitPortrait(carry.icon, carry.name, carry.cost, assetBase, 28.dp, showStar = false, pet = carry.isPet)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (target != null) scheme.onSurface else scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        counter.placeChange?.let { change ->
            Spacer(Modifier.width(8.dp))
            // 평균 등수가 커지면(밀리면) 나쁨. 색만으로 말하지 않게 ▲▼ 가 함께 간다(MASTER 규칙 3).
            Text(
                formatDelta(change) + "등",
                style = MaterialTheme.typography.labelLarge,
                color = when {
                    change > 0 -> FloaColors.Negative
                    change < 0 -> FloaColors.Positive
                    else -> scheme.onSurfaceVariant
                },
                maxLines = 1,
            )
        }
        if (target != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Spacer(Modifier.width(20.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// ⑥ 데이터 출처 줄 · 공용 조각
// ---------------------------------------------------------------------------

@Composable
private fun SourcesRow(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 24.dp)
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Info, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("데이터 출처", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface, modifier = Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** 접고 펴는 줄. 누를 수 있으니 primary 글자이고, 줄 전체가 48dp 누름 영역이다(D16). */
@Composable
private fun ExpandRow(label: String, expanded: Boolean, onToggle: () -> Unit) {
    val color = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClickLabel = if (expanded) "접기" else "펼치기", onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = color, modifier = Modifier.weight(1f))
        Icon(
            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** 섹션 안 작은 묶음 이름(제목보다 한 단 낮게). */
@Composable
private fun SubLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private const val HEADER_TRAITS = 4
private const val VARIANTS_FIRST = 5
private val ACTION_HEIGHT = 44.dp
private val PINNED_BAR_HEIGHT = 40.dp
private val BUILDUP_FACE = 32.dp
private val VARIANT_FACE = 26.dp

// ---------------------------------------------------------------------------
// 순수 규칙
// ---------------------------------------------------------------------------

/**
 * 상세 화면의 순수 규칙(Compose 밖). 단위 테스트 DeckDetailLogicTest 가 검사한다.
 * Models.kt 는 이번 묶음에서 고치지 않으므로(용어 정리 단계 소유) 화면 파일 안에 둔다.
 */
internal object DeckDetailLogic {

    /** 마무리 레벨 비율을 운영 줄에 적는 문턱과 개수. */
    const val FINISH_SHARE_MIN = 0.10
    const val FINISH_LEVELS_MAX = 2

    /** 보드 칸에 비율 글자를 적는 문턱(D10). */
    const val HEAT_LABEL_MIN = 0.10

    /** 같은 출처의 아래 순위 구성을 '다른 구성'에 남기는 문턱: 그 출처 1순위 표본의 10%(D9). */
    const val OTHER_PICK_SHARE = 0.10

    /** 3성 목표로 보여 줄 3성 비율(예전 핵심 유닛 칩과 같은 30%). */
    const val STAR3_TARGET = 0.30

    const val AUTHOR_TAG = "작가"
    const val AUTHOR_ALT_TAG = "작가 차선"

    /** 증강 고른 시점. AugmentStat.stage 의 순서와 같다. */
    val STAGE_LABELS = listOf("2-1", "3-2", "4-2")

    // -- 레벨 한 줄 ----------------------------------------------------------

    /** 운영: metatft 운영 방식(수집기가 운영 어휘로 옮긴 값), 없으면 '최종 N레벨'. */
    fun operation(levelling: String?, finalLevel: Int?): String? =
        levelling?.trim()?.takeIf { it.isNotEmpty() } ?: finalLevel?.let { "최종 ${it}레벨" }

    /** '마무리 8레벨 58.9% · 9레벨 36.5%': 10% 이상인 레벨을 많은 순으로 둘까지. 없으면 null. */
    fun finishLevels(levels: List<LevelShare>): String? {
        val top = levels
            .filter { it.level > 0 && it.share >= FINISH_SHARE_MIN }
            .sortedByDescending { it.share }
            .take(FINISH_LEVELS_MAX)
        if (top.isEmpty()) return null
        return "마무리 " + top.joinToString(" · ") { "${it.level}레벨 ${formatPct(it.share)}" }
    }

    /**
     * 머리말 둘째 줄. 조합 덱은 '빠른 8레벨 · 마무리 8레벨 58.9% · 9레벨 36.5%'(metatft finalLevels),
     * 중국 한정 덱은 '최종 9레벨'. 운영이 '최종 N레벨' 인데 마무리 분포가 있으면 분포만 둔다(같은 말 두 번).
     */
    fun levelLine(levelling: String?, finalLevel: Int?, finalLevels: List<LevelShare>): String? {
        val finish = finishLevels(finalLevels)
        val op = operation(levelling, finalLevel)?.takeUnless { finish != null && it.startsWith("최종") }
        return listOfNotNull(op, finish).joinToString(" · ").ifEmpty { null }
    }

    /** 레벨 칩 아래 캡션 '8레벨 4-2 도달 · 롤다운 9레벨'. 라운드·롤다운 레벨 중 모르는 것은 뺀다. */
    fun levelCaption(level: Int, round: String?, rollLevel: Int?): String = listOfNotNull(
        round?.trim()?.takeIf { it.isNotEmpty() }?.let { "${level}레벨 $it 도달" },
        rollLevel?.let { "롤다운 ${it}레벨" },
    ).joinToString(" · ")

    /** 그 레벨에 가장 흔히 도달하는 라운드(metatft), 없으면 편집 덱 단계에 적힌 라운드. */
    fun reachRound(deck: Deck, level: Int): String? =
        deck.buildup?.global?.levels.orEmpty()
            .firstOrNull { it.level == level }?.reachRound?.takeIf { it.isNotBlank() }
            ?: deck.editorial?.stages.orEmpty()
                .firstOrNull { it.level == level && !it.round.isNullOrBlank() }?.round

    // -- 레벨별 구성 -----------------------------------------------------------

    data class BuildupRows(val top: BuildupPick?, val others: List<BuildupPick>, val showOrigin: Boolean)

    /**
     * 한 레벨의 행: 첫 행(글로벌 → 중국 → 작가 순의 1순위)과 접어 둘 나머지.
     * 같은 출처 안에서 2·3순위는 그 출처 1순위 표본의 10% 이상일 때만 남긴다(1.3%·0.8% 같은 잡음 행을 뺀다).
     * 출처가 다른 행은 표본 척도가 달라 비교하지 않고 남긴다. 출처가 둘 이상이면 행마다 출처 글자를 단다.
     */
    fun buildupRows(picks: List<BuildupPick>): BuildupRows {
        val leadN = picks.groupBy { it.origin }.mapValues { (_, list) -> list.first().option?.n ?: 0 }
        val kept = picks.filterIndexed { index, pick ->
            val lead = picks.indexOfFirst { it.origin == pick.origin } == index
            val n = pick.option?.n
            val base = leadN[pick.origin] ?: 0
            lead || n == null || base <= 0 || n >= base * OTHER_PICK_SHARE
        }
        val top = kept.firstOrNull() ?: return BuildupRows(null, emptyList(), false)
        return BuildupRows(top, kept.drop(1), kept.map { it.origin }.distinct().size > 1)
    }

    /** 행 끝 글자: 출처('글로벌'·'중국'·'작가', 출처가 둘 이상일 때만) + 작가 행은 단계('2-3 초반'). */
    fun buildupTag(pick: BuildupPick, showOrigin: Boolean): String = listOf(
        if (showOrigin) pick.origin.label else "",
        if (pick.origin == BuildupOrigin.AUTHOR) stageCaption(pick.stage) else "",
    ).filter { it.isNotEmpty() }.joinToString(" · ")

    /** "2-3 초반" / "4-3 중반" / "최종". */
    fun stageCaption(stage: Stage?): String {
        if (stage == null) return ""
        val label = stage.label.takeIf { it.isNotBlank() } ?: when (stage.key) {
            "early" -> "초반"
            "mid" -> "중반"
            else -> "최종"
        }
        return listOfNotNull(stage.round?.takeIf { it.isNotBlank() }, label).joinToString(" ")
    }

    // -- 추천 증강 -------------------------------------------------------------

    /** 추천 증강 한 줄. [tag] 는 작가가 고른 것만('작가'·'작가 차선'), [stat] 은 통계가 있을 때. */
    data class AugmentLine(
        val id: String,
        val name: String,
        val icon: String?,
        val tag: String?,
        val stat: AugmentStat?,
    )

    /**
     * 작가가 고른 증강. 편집 덱이 붙었으면 그 편집 덱 것, 편집 덱만 있는 옛 독립 덱이면 최상위 필드.
     * Deck.authorAugments 는 편집 덱이 없을 때 수집기가 채운 중국 추천(작가 것이 아님)으로 떨어지므로 쓰지 않는다(D12).
     */
    fun authoredAugments(deck: Deck): Augments? {
        fun Augments.nonEmpty() = takeIf { it.recommended.isNotEmpty() || it.alternatives.isNotEmpty() }
        deck.editorial?.let { return it.augments.nonEmpty() }
        return deck.augments.nonEmpty()?.takeIf { deck.kind == DeckKeys.KIND_EDITORIAL }
    }

    fun augmentLines(deck: Deck): List<AugmentLine> {
        val authored = authoredAugments(deck)
        return mergeAugments(authored, if (authored == null) deck.augments else Augments(), deck.augmentStats)
    }

    /**
     * 한 목록으로 합친다: 작가 추천 → 작가 차선 → 통계 상위(순위 순) → 나머지 추천. 같은 증강은 id 로 한 번만 두고,
     * 작가 행에도 통계가 있으면 붙인다. [fallback] 은 작가 것이 아닌 추천이라 배지를 달지 않는다.
     */
    fun mergeAugments(authored: Augments?, fallback: Augments, stats: List<AugmentStat>): List<AugmentLine> {
        val statById = stats.filter { it.id.isNotBlank() }.associateBy { it.id }
        val lines = LinkedHashMap<String, AugmentLine>()
        fun key(id: String, name: String) = id.ifBlank { "name:$name" }
        fun addRef(ref: ItemRef, tag: String?) {
            val stat = ref.id.takeIf { it.isNotBlank() }?.let { statById[it] }
            lines.getOrPut(key(ref.id, ref.name)) { AugmentLine(ref.id, ref.name, ref.icon ?: stat?.icon, tag, stat) }
        }
        authored?.recommended?.forEach { addRef(it, AUTHOR_TAG) }
        authored?.alternatives?.forEach { addRef(it, AUTHOR_ALT_TAG) }
        stats.sortedBy { it.rank }.forEach { stat ->
            lines.getOrPut(key(stat.id, stat.name)) { AugmentLine(stat.id, stat.name, stat.icon, null, stat) }
        }
        (fallback.recommended + fallback.alternatives).forEach { addRef(it, null) }
        return lines.values.toList()
    }

    /** 행 오른쪽 등수 '3.12등'. 표본 300판 미만이거나 그 시점 값이 표본 부족이면 적지 않는다(null). */
    fun augmentValue(stat: AugmentStat?, stageIndex: Int?): String? {
        if (stat == null || stat.n < DeckKeys.MIN_SAMPLE) return null
        if (stageIndex == null) return stat.avg?.let { formatAvgRank(it) }
        if (stat.stageLowSample.getOrNull(stageIndex) == true) return null
        return stat.stage.getOrNull(stageIndex)?.let { formatAvgRank(it) }
    }

    /** 고른 시점 칩을 보일지: 어느 행이든 시점별 등수를 적을 수 있을 때만. */
    fun hasStageValues(lines: List<AugmentLine>): Boolean =
        lines.any { line -> STAGE_LABELS.indices.any { augmentValue(line.stat, it) != null } }

    // -- 배치 ----------------------------------------------------------------

    /** 칸 비율 글자. 10% 미만은 적지 않는다. */
    fun heatLabel(use: Double): String? = if (use >= HEAT_LABEL_MIN) formatPct(use) else null

    /** 실측 칸으로 놓을지: 실측이 없으면 아니오, 작가 좌표가 없으면 예, 둘 다 있으면 고른 대로. */
    fun useMeasured(hasCoords: Boolean, hasPositions: Boolean, measured: Boolean): Boolean = when {
        !hasPositions -> false
        !hasCoords -> true
        else -> measured
    }

    data class BoardLayout(val slots: List<BoardSlot>, val unplaced: List<DeckUnit>)

    /**
     * 보드 칸 배치.
     *  - 실측을 쓰지 않으면 작가 좌표 그대로.
     *  - 실측을 쓰면 유닛마다 실측 최빈 칸으로 옮긴다. 두 유닛의 최빈 칸이 겹치면 사용률이 높은 유닛이 먼저 차지하고,
     *    다른 유닛은 다음 후보 칸으로 간다. 칸 비율 글자는 [heatLabel] 규칙(10% 이상)만.
     *  - 실측 칸을 못 받은 유닛은 작가 좌표가 비어 있으면 거기에, 그것도 없으면 보드 밖으로 뺀다.
     */
    fun layoutBoard(units: List<DeckUnit>, positions: Map<String, List<CellStat>>, useMeasured: Boolean): BoardLayout {
        if (!useMeasured || positions.isEmpty()) {
            val placed = units.mapNotNull { unit ->
                val row = unit.row ?: return@mapNotNull null
                val col = unit.col ?: return@mapNotNull null
                slotOf(unit, row, col)
            }
            return BoardLayout(
                slots = placed,
                unplaced = if (placed.isEmpty()) emptyList() else units.filter { it.row == null || it.col == null },
            )
        }

        val maxUse = positions.values.mapNotNull { cells -> cells.maxOfOrNull { it.use } }.maxOrNull()?.takeIf { it > 0 } ?: 1.0
        val taken = mutableSetOf<Pair<Int, Int>>()
        val result = arrayOfNulls<BoardSlot>(units.size)

        val order = units.indices.sortedByDescending { i -> positions[units[i].id]?.maxOfOrNull { it.use } ?: -1.0 }
        for (i in order) {
            val unit = units[i]
            val cells = positions[unit.id].orEmpty()
                .filter { it.row in 1..4 && it.col in 1..7 }
                .sortedByDescending { it.use }
            val cell = cells.firstOrNull { (it.row to it.col) !in taken } ?: continue
            taken += cell.row to cell.col
            val top = cells.first()
            val diverges = unit.row != null && unit.col != null && (unit.row != top.row || unit.col != top.col)
            result[i] = slotOf(
                unit, cell.row, cell.col,
                heat = (cell.use / maxUse).toFloat(),
                heatLabel = heatLabel(cell.use),
                diverges = diverges,
            )
        }
        for (i in units.indices) {
            if (result[i] != null) continue
            val unit = units[i]
            val row = unit.row ?: continue
            val col = unit.col ?: continue
            if ((row to col) in taken) continue
            taken += row to col
            result[i] = slotOf(unit, row, col)
        }
        val slots = result.filterNotNull()
        return BoardLayout(slots, if (slots.isEmpty()) emptyList() else units.filterIndexed { i, _ -> result[i] == null })
    }

    private fun slotOf(
        unit: DeckUnit,
        row: Int,
        col: Int,
        heat: Float? = null,
        heatLabel: String? = null,
        diverges: Boolean = false,
    ) = BoardSlot(
        row = row,
        col = col,
        name = unit.name,
        icon = unit.icon,
        cost = unit.cost,
        star = unit.star,
        carry = unit.carry,
        kind = unit.kind,
        heat = heat,
        heatLabel = heatLabel,
        diverges = diverges,
    )

    // -- 비슷한 구성 · 상대 --------------------------------------------------------

    /** 이 구간 표본이 큰 변형부터(그 구간에 없는 변형은 뒤로, 같으면 원래 순서). */
    fun sortVariants(variants: List<Variant>, bucket: String): List<Variant> =
        variants.sortedByDescending { it.stats[bucket]?.n ?: -1 }

    /** 보이는 보드([base]) 대비 빠진 챔피언과 들어온 챔피언(원래 순서, 중복 없이). */
    fun variantDiff(base: List<String>, variant: List<String>): Pair<List<String>, List<String>> {
        val baseSet = base.toSet()
        val variantSet = variant.toSet()
        return base.filter { it !in variantSet }.distinct() to variant.filter { it !in baseSet }.distinct()
    }

    /** '−카직스 +럭스'(빼기는 U+2212). 바뀐 챔피언이 없으면 '같은 챔피언 구성'. */
    fun diffText(minus: List<String>, plus: List<String>): String {
        if (minus.isEmpty() && plus.isEmpty()) return "같은 챔피언 구성"
        return (minus.map { "−$it" } + plus.map { "+$it" }).joinToString(" ")
    }

    /** 우리 목록에 없는 상대 덱의 이름: metatft 이름 조각(특성·챔피언 id)을 한글로 풀어 잇는다. */
    fun counterName(raw: String?, resolve: (String) -> String?): String? {
        val ids = Regex("[^,\\s]+").findAll(raw.orEmpty()).map { it.value }.toList()
        if (ids.isEmpty()) return null
        return ids.joinToString(" ") { resolve(it) ?: it }
    }

    // -- 그 밖 -----------------------------------------------------------------

    /** 3성 목표: 3성 비율이 30% 이상인 챔피언 id → 비율. */
    fun star3Targets(keyUnits: List<KeyUnit>): Map<String, Double> =
        keyUnits.mapNotNull { unit -> unit.star3?.takeIf { it >= STAR3_TARGET }?.let { unit.id to it } }.toMap()

    /** 그 구간 표본이 300판 미만인지('표본 적음' 배지). 통계가 없는 덱은 아니다. */
    fun thinSample(stats: DeckStats?): Boolean = stats != null && stats.n < DeckKeys.MIN_SAMPLE

    /** 덱 코드가 보드를 다 담지 못할 때 단추 아래 한 줄. 다 담으면 null. */
    fun partialCodeNote(code: TeamCode): String? {
        if (!code.isPartial) return null
        val omitted = code.omitted.orEmpty().filter { it.isNotBlank() }.distinct()
        val truncated = code.truncated ?: 0
        return listOfNotNull(
            omitted.takeIf { it.isNotEmpty() }?.let { "덱 코드에는 상점 챔피언만 담깁니다 · 빠짐: ${it.joinToString(", ")}" },
            truncated.takeIf { it > 0 }?.let { "덱 코드는 10칸까지라 ${it}명이 빠집니다" },
        ).joinToString(" · ").ifEmpty { null }
    }
}
