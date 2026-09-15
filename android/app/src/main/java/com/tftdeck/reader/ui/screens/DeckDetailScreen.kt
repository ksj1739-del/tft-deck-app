package com.tftdeck.reader.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tftdeck.reader.data.AugmentStat
import com.tftdeck.reader.data.BuildupOrigin
import com.tftdeck.reader.data.BuildupPick
import com.tftdeck.reader.data.BuildupPlanner
import com.tftdeck.reader.data.Catalog
import com.tftdeck.reader.data.CatalogIndex
import com.tftdeck.reader.data.CellStat
import com.tftdeck.reader.data.Deck
import com.tftdeck.reader.data.DeckFeed
import com.tftdeck.reader.data.DeckKeys
import com.tftdeck.reader.data.DeckWearer
import com.tftdeck.reader.data.Editorial
import com.tftdeck.reader.data.FeedState
import com.tftdeck.reader.data.ItemRef
import com.tftdeck.reader.data.KeyUnit
import com.tftdeck.reader.data.ScopeStat
import com.tftdeck.reader.data.Stage
import com.tftdeck.reader.data.Variant
import com.tftdeck.reader.data.withEditorial
import com.tftdeck.reader.data.Unit as DeckUnit
import com.tftdeck.reader.ui.AppViewModel
import com.tftdeck.reader.ui.bucketLabel
import com.tftdeck.reader.ui.components.BoardSlot
import com.tftdeck.reader.ui.components.BucketChips
import com.tftdeck.reader.ui.components.EmptyState
import com.tftdeck.reader.ui.components.FaceRow
import com.tftdeck.reader.ui.components.HexBoard
import com.tftdeck.reader.ui.components.ItemIcons
import com.tftdeck.reader.ui.components.LowSampleNote
import com.tftdeck.reader.ui.components.OutlineBadge
import com.tftdeck.reader.ui.components.SampleLabel
import com.tftdeck.reader.ui.components.SourceBadges
import com.tftdeck.reader.ui.components.StatsRow
import com.tftdeck.reader.ui.components.TierBadge
import com.tftdeck.reader.ui.components.TraitChip
import com.tftdeck.reader.ui.components.TrendGlyph
import com.tftdeck.reader.ui.components.UnitGrid
import com.tftdeck.reader.ui.components.UnitPortrait
import com.tftdeck.reader.ui.components.faceFor
import com.tftdeck.reader.ui.copyToClipboard
import com.tftdeck.reader.ui.formatAvg
import com.tftdeck.reader.ui.formatCount
import com.tftdeck.reader.ui.formatPct
import com.tftdeck.reader.ui.formatShortDate
import com.tftdeck.reader.ui.iconUrl
import com.tftdeck.reader.ui.placeColor
import com.tftdeck.reader.ui.scopeLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 덱 상세. 세로 스크롤 하나에 순서대로(§6.3, 배치는 §13.3):
 *  머리말 → 덱 코드/오버레이 → 빌드업 → 최종 배치 → 핵심 유닛 → 레벨 도달 → 아이템 배분
 *  → 조합 재료 → 증강 → 변형 → 불리한 상대 → 글로벌 비교 → 작성자 원문.
 *
 * [initialVariant] 는 목록의 변형 행에서 들어올 때 붙는다. 그 변형을 보드에 미리 띄우고 변형 섹션으로 내려간다.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
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
    val bucket by viewModel.bucket.collectAsState()
    val state by viewModel.feedState.collectAsState()
    val feed = (state as? FeedState.Ready)?.feed
    val showingThisDeck = overlayRunning && pinned == deckId
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme

    if (baseDeck == null) {
        EmptyState("덱을 찾을 수 없습니다", "목록에서 다시 선택해 주세요.")
        return
    }

    val catalog = remember(feed) { viewModel.catalog() } ?: EMPTY_CATALOG

    // 한 그룹에 작가가 다른 편집 덱이 여럿 붙기도 한다(moreEditorials). 작가를 고르면 그 작가의
    // 보드·빌드업 작가 행·증강·조합 재료·덱 코드·원문으로 화면 전체가 바뀐다. 통계는 그룹 것 그대로다.
    val editorials = baseDeck.editorials
    var authorIndex by rememberSaveable(deckId) { mutableStateOf(0) }
    val deck = remember(baseDeck, catalog, authorIndex) {
        editorials.getOrNull(authorIndex)?.takeIf { authorIndex > 0 }?.let { baseDeck.withEditorial(it, catalog) }
            ?: baseDeck
    }
    val scope = rememberCoroutineScope()
    val boardRequester = remember { BringIntoViewRequester() }
    val variantsRequester = remember { BringIntoViewRequester() }

    var previewVariantId by rememberSaveable(deckId) {
        mutableStateOf(initialVariant?.takeIf { id -> deck.otherVariants.any { it.id == id } })
    }
    var measured by rememberSaveable(deckId) { mutableStateOf(false) }
    var showCn by rememberSaveable(deckId) { mutableStateOf(false) }
    val previewVariant = deck.otherVariants.firstOrNull { it.id == previewVariantId }

    LaunchedEffect(deckId, initialVariant) {
        if (initialVariant != null && deck.otherVariants.any { it.id == initialVariant }) {
            // 첫 배치가 끝나야 섹션 위치를 안다.
            delay(200)
            variantsRequester.bringIntoView()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {

        // --- 1. 머리말 ---------------------------------------------------------
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TierBadge(deck.gradeFor(bucket), editorial = deck.showsEditorialGrade(bucket), global = deck.isGlobalOnly)
                // 편집 등급으로 대신 보여 줄 때도 이 구간 통계가 모자라다는 사실은 알린다.
                if (deck.isLowSample(bucket) && deck.showsEditorialGrade(bucket)) {
                    Spacer(Modifier.width(5.dp))
                    LowSampleNote()
                }
                Spacer(Modifier.width(7.dp))
                deck.finalLevel?.let {
                    Text(
                        "${it}레벨 완성",
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                TrendGlyph(deck.statsFor(bucket)?.trend)
            }
            Text(deck.name, style = MaterialTheme.typography.titleLarge, color = scheme.onSurface)
            SourceBadges(deck, feed?.version?.metatftCompared ?: true)

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                deck.traits.forEach { TraitChip(it, assetBase) }
            }

            if (feed != null && feed.buckets.isNotEmpty()) {
                StatsRow(deck.displayStats(bucket), Modifier.padding(top = 4.dp))
                BucketChips(feed.buckets, bucket, viewModel::setBucket)
                SampleLabel(deck, bucket, feed.buckets)
            }
            comparisonLine(deck, bucket, feed)?.let { line ->
                Text(line, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            }
            preciseLine(deck, bucket)?.let { line ->
                Text(line, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
        }

        // --- 작가 고르기(같은 그룹에 편집 덱이 둘 이상일 때) ----------------------
        if (editorials.size >= 2) {
            AuthorChips(editorials, authorIndex) { authorIndex = it }
        }

        // --- 2. 덱 코드 / 오버레이 --------------------------------------------
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                deck.teamCode?.let { code ->
                    Button(
                        onClick = { copyToClipboard(context, "TFT 덱 코드", code.code) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("덱 코드 복사")
                    }
                }
                OutlinedButton(
                    onClick = {
                        viewModel.pinDeck(deck.id)
                        onStartOverlay(deck.id)
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (showingThisDeck) scheme.primary else scheme.onSurfaceVariant,
                    ),
                ) {
                    Icon(Icons.Default.PictureInPictureAlt, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    // 고정해 둔 덱인지가 아니라 실제로 떠 있는지로 라벨을 정해야 헷갈리지 않는다.
                    Text(if (showingThisDeck) "띄우는 중" else "게임 위에 띄우기")
                }
            }

            deck.teamCode?.takeIf { it.isPartial }?.let { code ->
                val omitted = code.omitted.orEmpty()
                Text(
                    buildString {
                        append("덱 코드에는 상점에서 뽑는 유닛만 담깁니다.")
                        if (omitted.isNotEmpty()) append(" 제외: ${omitted.joinToString(", ")}")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }

        // --- 빌드업(§13.3) -----------------------------------------------------
        BuildupSection(deck, catalog, assetBase, feed)

        // --- 3. 최종 배치 -------------------------------------------------------
        BoardSection(
            deck = deck,
            catalog = catalog,
            assetBase = assetBase,
            detailLabel = detailBucketLabel(deck, feed),
            previewVariant = previewVariant,
            measured = measured,
            onMeasuredChange = { measured = it },
            onClearPreview = { previewVariantId = null },
            modifier = Modifier.bringIntoViewRequester(boardRequester),
        )

        // --- 4. 핵심 유닛 -------------------------------------------------------
        KeyUnitsSection(deck, catalog, assetBase)

        // --- 5. 레벨 도달 -------------------------------------------------------
        LevelReachSection(deck, detailBucketLabel(deck, feed))

        // --- 6. 아이템 배분 -----------------------------------------------------
        ItemsSection(deck, assetBase)

        // --- 7. 조합 재료 -------------------------------------------------------
        ComponentOrderSection(deck, catalog, assetBase)

        // --- 8. 증강체 ---------------------------------------------------------
        AugmentsSection(deck, feed, assetBase)

        // --- 9. 변형 -----------------------------------------------------------
        VariantsSection(
            deck = deck,
            bucket = bucket,
            catalog = catalog,
            assetBase = assetBase,
            previewId = previewVariantId,
            onPreview = { id ->
                previewVariantId = id
                // 미리보기는 위쪽 보드에 그려지므로 보드로 올라가야 바뀐 것이 보인다.
                scope.launch { boardRequester.bringIntoView() }
            },
            modifier = Modifier.bringIntoViewRequester(variantsRequester),
        )

        // --- 10. 불리한 상대 ----------------------------------------------------
        CountersSection(deck, catalog, viewModel, onOpenDeck)

        // --- 11. 글로벌 비교 ----------------------------------------------------
        GlobalCompareSection(deck, feed)

        // --- 12. 작성자 원문 ----------------------------------------------------
        if (!deck.notesCn.isEmpty || deck.nameCn.isNotBlank()) {
            Section("작성자 원문 (중국어)") {
                Text(
                    if (showCn) "접기" else "펼치기",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.primary,
                    modifier = Modifier.clickable { showCn = !showCn },
                )
                AnimatedVisibility(showCn) {
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        if (deck.nameCn.isNotBlank()) {
                            Text(deck.nameCn, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        }
                        listOf(deck.notesCn.items, deck.notesCn.augments)
                            .filter { it.isNotBlank() }
                            .forEach { note ->
                                Text(note, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                            }
                        val author = deck.editorial?.author?.takeIf { it.isNotBlank() } ?: deck.author
                        if (author.isNotBlank()) {
                            Text(
                                "작성 $author",
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.size(24.dp))
    }
}

@Composable
private fun Section(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        content()
    }
}

private val EMPTY_CATALOG = CatalogIndex(Catalog())
private val STAGE_LABELS = listOf("2-1", "3-2", "4-2")

/** "중국 골드~에메랄드 3.61 · 글로벌 플래+ 4.22 · KR 플래+ 4.20". 비교할 값이 둘 이상일 때만. */
private fun comparisonLine(deck: Deck, bucket: String, feed: DeckFeed?): String? {
    val parts = mutableListOf<String>()
    deck.statsFor(bucket)?.avg?.let { avg ->
        val label = feed?.buckets?.get(bucket)?.label?.takeIf { it.isNotBlank() } ?: bucketLabel(bucket)
        parts += "중국 $label ${formatAvg(avg)}"
    }
    listOf(DeckKeys.SCOPE_GLOBAL_PLAT, DeckKeys.SCOPE_KR_PLAT).forEach { key ->
        deck.global?.stats?.get(key)?.avg?.let { avg -> parts += "${scopeLabel(key, short = true)} ${formatAvg(avg)}" }
    }
    return parts.takeIf { it.size >= 2 }?.joinToString(" · ")
}

/**
 * 数据检索器(카운트 기반, 3일) 참고치: "lol.qq 데이터 검색 · 중국 플래+ 4.14등 · TOP4 54.7% · n=3,139".
 * 胜率阵容 수치와 정의가 다른 모집단이라 등급에는 쓰지 않고 머리말에 작게 곁들인다(§4.4).
 */
private fun preciseLine(deck: Deck, bucket: String): String? {
    val precise = deck.statsFor(bucket)?.precise ?: return null
    if (precise.n <= 0 || precise.avg == null) return null
    return listOfNotNull(
        "lol.qq 데이터 검색",
        "${scopeLabel(precise.scope, short = true)} ${formatAvg(precise.avg)}등",
        precise.top4?.let { "TOP4 ${formatPct(it)}" },
        "n=${formatCount(precise.n)}",
    ).joinToString(" · ")
}

/** 같은 그룹에 붙은 편집 덱의 작가 칩. 고르면 화면이 그 작가의 보드·증강·덱 코드로 바뀐다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AuthorChips(editorials: List<Editorial>, selected: Int, onSelect: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "편집 덱 ${editorials.size}개 · 작가를 고르면 보드·증강·덱 코드가 바뀝니다",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            editorials.forEachIndexed { index, editorial ->
                FilterChip(
                    selected = index == selected,
                    onClick = { onSelect(index) },
                    label = {
                        Text(
                            listOfNotNull(
                                editorial.author.takeIf { it.isNotBlank() } ?: "작가 ${index + 1}",
                                editorial.quality?.takeIf { it.isNotBlank() },
                            ).joinToString(" · "),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 빌드업
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BuildupSection(deck: Deck, catalog: CatalogIndex, assetBase: String, feed: DeckFeed?) {
    if (!BuildupPlanner.hasData(deck)) return
    val scheme = MaterialTheme.colorScheme
    val levels = remember(deck) { BuildupPlanner.levels(deck) }
    // 작가를 바꾸면 작가 단계 레벨이 달라지므로 그 작가 기준 기본 레벨로 다시 고른다.
    var selected by rememberSaveable(deck.id, deck.editorial?.id) {
        mutableStateOf(BuildupPlanner.defaultLevel(deck, levels))
    }
    var showNotes by rememberSaveable(deck.id) { mutableStateOf(false) }

    Section("빌드업") {
        // 1) 레벨별 도달 라운드와 주 리롤 레벨
        val timings = BuildupPlanner.timings(deck)
        val rollLevel = BuildupPlanner.rollLevel(deck)
        if (timings.isNotEmpty() || rollLevel != null) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (timings.isNotEmpty()) {
                    Text(
                        timings.joinToString(" · ") { (level, round) -> "${level}렙 $round" },
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurface,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
                rollLevel?.let { level ->
                    val rolls = BuildupPlanner.rollsAtRollLevel(deck)
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(scheme.secondaryContainer)
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                    ) {
                        Text(
                            "주 리롤 ${level}렙" + (rolls?.let { " (판당 ${it.roundToInt()}회)" } ?: ""),
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSecondaryContainer,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            deck.buildup?.global?.scope?.let { key ->
                val days = feed?.scopes?.get(key)?.days?.takeIf { it > 0 }
                Text(
                    scopeLabel(key, short = true) + (days?.let { " ${it}일" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }

        // 2) 레벨 칩. 가로 스크롤 없이 한 줄에 둔다(칩이 많으면 폭을 나눠 쓴다).
        if (levels.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                levels.forEach { level ->
                    LevelChip(
                        level = level,
                        selected = level == selected,
                        dim = BuildupPlanner.isLowSample(deck, level),
                        onClick = { selected = level },
                        modifier = if (levels.size <= 6) Modifier.width(48.dp) else Modifier.weight(1f),
                    )
                }
            }
        }

        // 3) 선택 레벨의 구성: 글로벌 → 중국 → 작가
        selected?.let { level ->
            val global = BuildupPlanner.globalPicks(deck, level)
            val cn = BuildupPlanner.cnPicks(deck, level)
            val author = BuildupPlanner.authorPicks(deck, level)
            if (global.isEmpty() && cn.isEmpty() && author.isEmpty()) {
                Text("이 레벨의 구성 자료가 없습니다.", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (global + cn + author).forEachIndexed { index, pick ->
                    val first = index == 0 || (global + cn + author)[index - 1].origin != pick.origin
                    BuildupRow(
                        label = if (first) pick.origin.label else "",
                        pick = pick,
                        deck = deck,
                        catalog = catalog,
                        assetBase = assetBase,
                    )
                }
            }
        }

        // 4) 작성자 운영 메모(중국어 원문, 접힘)
        val notes = deck.buildupNotes
        if (notes.hasBuildupNotes) {
            Text(
                if (showNotes) "작성자 운영 메모 접기" else "작성자 운영 메모 (중국어 원문)",
                style = MaterialTheme.typography.labelMedium,
                color = scheme.primary,
                modifier = Modifier.clickable { showNotes = !showNotes },
            )
            AnimatedVisibility(showNotes) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    notes.early?.takeIf { it.isNotBlank() }?.let {
                        Text("초반 · $it", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                    notes.levelUp?.takeIf { it.isNotBlank() }?.let {
                        Text("레벨업 · $it", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun LevelChip(level: Int, selected: Boolean, dim: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .height(34.dp)
            .alpha(if (dim && !selected) 0.5f else 1f)
            .clip(shape)
            .background(if (selected) scheme.primary else scheme.surfaceVariant)
            .border(1.dp, if (selected) scheme.primary else scheme.outlineVariant, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "${level}렙",
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** 빌드업 한 줄: 출처 · 얼굴 줄(새로 들어온 유닛에 '+') · 오른쪽 수치. */
@Composable
private fun BuildupRow(label: String, pick: BuildupPick, deck: Deck, catalog: CatalogIndex, assetBase: String) {
    val scheme = MaterialTheme.colorScheme
    val newUnits = remember(deck, pick) { BuildupPlanner.newUnits(deck, pick) }
    val option = pick.option
    val lowSample = option != null && option.n < BuildupPlanner.LOW_SAMPLE_N
    val detail: List<String> = when (pick.origin) {
        BuildupOrigin.GLOBAL -> listOf("${formatAvg(option?.avg)}등", "${formatCount(option?.n)}판")
        BuildupOrigin.CN -> listOfNotNull(
            "${formatAvg(option?.avg)}등",
            "${formatCount(option?.n)}판",
            option?.top4?.let { "TOP4 ${formatPct(it, 0)}" },
        )
        BuildupOrigin.AUTHOR -> listOf(stageCaption(pick.stage))
    }

    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (lowSample) 0.5f else 1f),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.primary,
            modifier = Modifier
                .width(36.dp)
                .padding(top = 9.dp),
        )
        FaceRow(
            faces = pick.units.map { id ->
                faceFor(id, catalog.unit(id), pet = catalog.isPet(id), newMark = id in newUnits)
            },
            assetBase = assetBase,
            size = BUILDUP_FACE,
            modifier = Modifier
                .weight(1f)
                .padding(top = 4.dp),
            // 빌드업 단계에서는 성급 정보가 없다. 별을 그리면 최종 보드와 헷갈린다.
            showStars = false,
        )
        Spacer(Modifier.width(6.dp))
        Column(Modifier.width(64.dp), horizontalAlignment = Alignment.End) {
            detail.forEach { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
            }
        }
    }
}

/** "2-3 초반" / "4-3 중반" / "최종". */
private fun stageCaption(stage: Stage?): String {
    if (stage == null) return ""
    val label = stage.label.takeIf { it.isNotBlank() } ?: when (stage.key) {
        "early" -> "초반"
        "mid" -> "중반"
        else -> "최종"
    }
    return listOfNotNull(stage.round?.takeIf { it.isNotBlank() }, label).joinToString(" ")
}

private val BUILDUP_FACE = 32.dp

// ---------------------------------------------------------------------------
// 최종 배치
// ---------------------------------------------------------------------------

@Composable
private fun BoardSection(
    deck: Deck,
    catalog: CatalogIndex,
    assetBase: String,
    detailLabel: String?,
    previewVariant: Variant?,
    measured: Boolean,
    onMeasuredChange: (Boolean) -> Unit,
    onClearPreview: () -> Unit,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val units = remember(deck, previewVariant, catalog) { previewVariant?.toUnits(catalog) ?: deck.units }
    val layout = remember(units, measured) { layoutBoard(units, deck.positions, measured) }

    Section(if (previewVariant != null) "변형 배치 미리보기" else "최종 배치", modifier) {
        if (previewVariant != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "좌표가 없는 변형이라 실측 최빈 칸에 놓았습니다.",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClearPreview) { Text("대표 보드로") }
            }
        }
        if (deck.positions.isNotEmpty() && previewVariant == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("실측 배치", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "유닛마다 가장 많이 놓인 칸과 그 비율" + (detailLabel?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Switch(checked = measured, onCheckedChange = onMeasuredChange)
            }
        }

        if (layout.slots.isNotEmpty()) {
            HexBoard(slots = layout.slots, assetBase = assetBase)
            if (layout.slots.any { it.diverges }) {
                Text(
                    "점이 찍힌 유닛은 작가 배치와 실측 최빈 칸이 다릅니다.",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            if (layout.unplaced.isNotEmpty()) {
                Text("자리 정보 없음", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                UnitGrid(layout.unplaced, assetBase, compact = true)
            }
        } else {
            // 좌표도 실측 칸도 없으면 보드 대신 얼굴·아이템으로 보여 준다. 빈 보드는 데이터가 없는 것처럼 보인다.
            UnitGrid(units, assetBase)
        }
    }
}

private data class BoardLayout(val slots: List<BoardSlot>, val unplaced: List<DeckUnit>)

/**
 * 보드 칸 배치.
 *  - 작가 좌표가 있고 실측을 끄면 작가 좌표 그대로.
 *  - 실측을 켜거나 좌표가 아예 없으면(통계만 있는 그룹·변형) 유닛마다 실측 최빈 칸으로 옮긴다.
 *    두 유닛의 최빈 칸이 겹치면 사용률이 높은 유닛이 먼저 차지하고, 다른 유닛은 다음 후보 칸으로 간다.
 *  - 실측 칸을 못 받은 유닛은 작가 좌표가 비어 있으면 거기에, 그것도 없으면 보드 밖으로 뺀다.
 */
private fun layoutBoard(units: List<DeckUnit>, positions: Map<String, List<CellStat>>, measured: Boolean): BoardLayout {
    val hasCoords = units.any { it.row != null && it.col != null }
    val useMeasured = (measured || !hasCoords) && positions.isNotEmpty()

    if (!useMeasured) {
        val placed = units.filter { it.row != null && it.col != null }
        return BoardLayout(
            slots = placed.map { slotOf(it, it.row!!, it.col!!) },
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
            heatLabel = formatPct(cell.use, 0),
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

/** 변형은 id 참조뿐이라 catalog 로 이름·아이콘·아이템을 풀어 보드 유닛으로 만든다. */
private fun Variant.toUnits(catalog: CatalogIndex): List<DeckUnit> = units.map { unit ->
    val entry = catalog.unit(unit.id)
    DeckUnit(
        id = unit.id,
        name = entry?.name ?: unit.id,
        nameEn = entry?.nameEn,
        cost = entry?.cost,
        icon = entry?.icon,
        // 변형은 성급을 모른다. 1로 두면 3성 표시가 나오지 않는다.
        star = unit.star ?: 1,
        carry = unit.id == carryId,
        kind = if (catalog.isPet(unit.id)) DeckKeys.KIND_PET else null,
        items = unit.items.map { itemId ->
            catalog.items[itemId]?.let { ItemRef(it.id, it.name, it.icon) } ?: ItemRef(itemId, itemId, null)
        },
    )
}

// ---------------------------------------------------------------------------
// 핵심 유닛 · 레벨 도달 · 아이템
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeyUnitsSection(deck: Deck, catalog: CatalogIndex, assetBase: String) {
    if (deck.keyUnits.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    Section("핵심 유닛") {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            deck.keyUnits.forEach { keyUnit ->
                val entry = catalog.unit(keyUnit.id)
                val name = entry?.name ?: keyUnit.id
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(scheme.surfaceVariant)
                        .padding(start = 3.dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
                ) {
                    UnitPortrait(entry?.icon, name, entry?.cost, assetBase, 22.dp, showStar = false)
                    Spacer(Modifier.width(6.dp))
                    Text(keyUnitText(name, keyUnit), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** "장로 드래곤 2성 67% · 아이템 2.9개". 3성 비율이 30% 이상이면 3성을 앞에 둔다. */
private fun keyUnitText(name: String, keyUnit: KeyUnit): String = buildString {
    append(name)
    val star3 = keyUnit.star3 ?: 0.0
    if (star3 >= 0.30) append(" 3성 ${formatPct(star3, 0)}")
    keyUnit.star2?.let { append(" 2성 ${formatPct(it, 0)}") }
    keyUnit.items?.let { append(" · 아이템 ${String.format(Locale.US, "%.1f", it)}개") }
}

@Composable
private fun LevelReachSection(deck: Deck, detailLabel: String?) {
    val globalLevels = deck.global?.finalLevels.orEmpty()
    val shares = globalLevels.ifEmpty { deck.levelDist }
    if (shares.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    var showHelp by remember { mutableStateOf(false) }

    Section("최종 레벨 도달") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                shares.sortedBy { it.level }.joinToString(" · ") { "${it.level}렙 ${formatPct(it.share, 0)}" },
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (globalLevels.isNotEmpty()) {
                IconButton(onClick = { showHelp = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.AutoMirrored.Outlined.HelpOutline,
                        contentDescription = "이 수치 설명",
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        Text(
            if (globalLevels.isNotEmpty()) {
                "metatft 집계"
            } else {
                listOfNotNull(detailLabel ?: "중국", "胜率阵容 상세 집계").joinToString(" ")
            },
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text("확인") } },
            title = { Text("기간·티어 미표기 집계") },
            text = {
                Text("metatft가 이 구성의 최종 레벨 비율을 기간과 티어를 밝히지 않고 집계한 값입니다. 위의 중국 수치와 표본이 다릅니다.")
            },
        )
    }
}

@Composable
private fun ItemsSection(deck: Deck, assetBase: String) {
    val carries = deck.carries
    val others = deck.units.filter { unit ->
        carries.none { it === unit } && (unit.items.isNotEmpty() || unit.itemsBackup.isNotEmpty())
    }
    val carryHolders = carries.filter { it.items.isNotEmpty() || it.itemsBackup.isNotEmpty() }
    if (carryHolders.isEmpty() && others.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    var showOthers by rememberSaveable(deck.id) { mutableStateOf(false) }

    Section("아이템 배분") {
        carryHolders.forEach { ItemHolderRow(it, assetBase, large = true) }
        if (others.isNotEmpty()) {
            Text(
                if (showOthers) "나머지 접기" else "나머지 ${others.size}명 보기",
                style = MaterialTheme.typography.labelMedium,
                color = scheme.primary,
                modifier = Modifier.clickable { showOthers = !showOthers },
            )
            AnimatedVisibility(showOthers) {
                Column {
                    others.forEach { ItemHolderRow(it, assetBase, large = false) }
                }
            }
        }
    }
}

@Composable
private fun ItemHolderRow(unit: DeckUnit, assetBase: String, large: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val face = if (large) 36.dp else 30.dp
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UnitPortrait(unit.icon, unit.name, unit.cost, assetBase, face, star = unit.star, carry = unit.carry, pet = unit.isPet)
            Spacer(Modifier.width(8.dp))
            Text(
                unit.name + if (unit.star >= 3) " ★3" else "",
                style = if (large) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                color = if (unit.carry) scheme.primary else scheme.onSurface,
                fontWeight = if (unit.carry) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
            )
            ItemIcons(unit.items, assetBase, size = if (large) 26 else 22)
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
                Text("대체 ", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                ItemIcons(unit.itemsBackup, assetBase, size = 16)
                Spacer(Modifier.width(5.dp))
                Text(
                    unit.itemsBackup.joinToString(" · ") { it.name },
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 조합 재료 · 증강
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ComponentOrderSection(deck: Deck, catalog: CatalogIndex, assetBase: String) {
    val order = deck.componentOrder
    if (order.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    Section("조합 재료 우선순위") {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            order.forEachIndexed { index, item ->
                val wearers = remember(deck, item.id) { wearersForComponent(deck, catalog, item.id) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(scheme.surfaceVariant)
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                ) {
                    Text("${index + 1}", style = MaterialTheme.typography.labelSmall, color = scheme.primary)
                    Spacer(Modifier.width(5.dp))
                    AsyncImage(
                        model = iconUrl(assetBase, item.icon),
                        contentDescription = item.name,
                        modifier = Modifier.size(19.dp).clip(RoundedCornerShape(3.dp)),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(item.name, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                    // 이 재료로 만드는 완성템을 실제로 많이 드는 유닛. 누구에게 줄지 바로 보인다.
                    wearers.forEach { wearer ->
                        val entry = catalog.unit(wearer.id)
                        Spacer(Modifier.width(4.dp))
                        UnitPortrait(
                            entry?.icon,
                            entry?.name ?: wearer.name,
                            entry?.cost,
                            assetBase,
                            18.dp,
                            showStar = false,
                        )
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AugmentsSection(deck: Deck, feed: DeckFeed?, assetBase: String) {
    val author = deck.authorAugments
    val stats = deck.augmentStats
    val hasAuthor = author.recommended.isNotEmpty() || author.alternatives.isNotEmpty()
    if (!hasAuthor && stats.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    var stageIndex by rememberSaveable(deck.id) { mutableStateOf<Int?>(null) }

    Section("증강체") {
        if (hasAuthor) {
            Text("작가 추천", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            if (author.recommended.isNotEmpty()) {
                AugmentRow("추천", author.recommended, assetBase, scheme.primary)
            }
            if (author.alternatives.isNotEmpty()) {
                AugmentRow("차선", author.alternatives, assetBase, scheme.onSurfaceVariant)
            }
        }

        if (stats.isNotEmpty()) {
            if (hasAuthor) Spacer(Modifier.size(4.dp))
            Text("통계 상위", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            augmentNotice(feed, deck)?.let { notice ->
                Text(notice, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
            // 단계별 평균은 기본으로 접어 두고, 단계를 고르면 그 단계 값만 보여 준다.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                STAGE_LABELS.forEachIndexed { index, label ->
                    FilterChip(
                        selected = stageIndex == index,
                        onClick = { stageIndex = if (stageIndex == index) null else index },
                        label = { Text(label) },
                    )
                }
            }
            stats.sortedBy { it.rank }.forEach { augment ->
                AugmentStatRow(augment, stageIndex, assetBase)
            }
        }
    }
}

/**
 * "중국 골드~에메랄드 9/14 기준 · 덱별 상위 5개만".
 * 증강 성적은 덱마다 상세를 받은 구간(detailBucket)의 값이다. 대부분 기본 구간이지만 다이아+ 등에서 받은 덱도 있다.
 */
private fun augmentNotice(feed: DeckFeed?, deck: Deck): String? {
    val label = detailBucketLabel(deck, feed) ?: return "중국 서버 집계 · 덱별 상위 5개만"
    return "$label 기준 · 덱별 상위 5개만"
}

/**
 * 상세(증강 성적·실측 배치·레벨 분포)를 받은 구간 이름과 기준일: "중국 다이아+ 9/14".
 * 수집기가 적은 detailBucket 을 쓰고, 없으면(옛 파일) 기본 구간으로 본다. 구간 정보가 없는 v1 피드는 null.
 */
private fun detailBucketLabel(deck: Deck, feed: DeckFeed?): String? {
    val buckets = feed?.buckets?.takeIf { it.isNotEmpty() } ?: return null
    val key = deck.detailBucket?.takeIf { it in buckets } ?: feed.defaultBucket
    val meta = buckets[key]
    val label = meta?.label?.takeIf { it.isNotBlank() } ?: bucketLabel(key)
    val date = formatShortDate(meta?.detailDate).takeIf { it.isNotBlank() }
    return listOfNotNull("중국 $label", date).joinToString(" ")
}

@Composable
private fun AugmentStatRow(augment: AugmentStat, stageIndex: Int?, assetBase: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            // 표본이 적은 증강은 순위가 흔들린다. 흐리게 해서 과신하지 않게 한다.
            .alpha(if (augment.n < DeckKeys.MIN_SAMPLE) 0.5f else 1f)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = iconUrl(assetBase, augment.icon),
            contentDescription = null,
            modifier = Modifier.size(26.dp).clip(RoundedCornerShape(5.dp)),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            augment.name,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "${formatAvg(augment.avg)}등 · n=${formatCount(augment.n)}",
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
            )
            if (stageIndex != null) {
                val value = augment.stage.getOrNull(stageIndex)
                val low = augment.stageLowSample.getOrNull(stageIndex) == true
                Text(
                    "${STAGE_LABELS[stageIndex]} 선택 ${formatAvg(value)}등",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.primary,
                    modifier = Modifier.alpha(if (low || value == null) 0.45f else 1f),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AugmentRow(
    label: String,
    augments: List<ItemRef>,
    assetBase: String,
    color: Color,
) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.Top) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier
                .width(30.dp)
                .padding(top = 7.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            augments.forEach { augment ->
                // 증강은 이름보다 아이콘으로 기억하는 경우가 많아 아이콘을 앞에 둔다.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(scheme.surfaceVariant)
                        .padding(start = 3.dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
                ) {
                    AsyncImage(
                        model = iconUrl(assetBase, augment.icon),
                        contentDescription = null,
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(5.dp)),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        augment.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 변형 · 상대 · 글로벌 비교
// ---------------------------------------------------------------------------

@Composable
private fun VariantsSection(
    deck: Deck,
    bucket: String,
    catalog: CatalogIndex,
    assetBase: String,
    previewId: String?,
    onPreview: (String) -> Unit,
    modifier: Modifier,
) {
    val variants = deck.otherVariants
    if (variants.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    Section("변형 ${variants.size}개", modifier) {
        Text("누르면 위 보드에서 그 구성을 미리 봅니다.", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        variants.forEach { variant ->
            val selected = variant.id == previewId
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) scheme.primaryContainer else scheme.surfaceVariant.copy(alpha = 0.6f))
                    .clickable { onPreview(variant.id) }
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    FaceRow(
                        faces = variant.units.map { unit ->
                            faceFor(unit.id, catalog.unit(unit.id), star = unit.star ?: 1, carry = unit.id == variant.carryId, pet = catalog.isPet(unit.id))
                        },
                        assetBase = assetBase,
                        size = 26.dp,
                        modifier = Modifier.weight(1f),
                        // 변형 조합 원본에는 성급이 없다. 별을 그리지 않는다.
                        showStars = false,
                    )
                    if (variant.editorialId != null) {
                        Spacer(Modifier.width(4.dp))
                        OutlineBadge("편집", scheme.primary)
                    }
                }
                StatsRow(variant.stats[bucket], compact = true)
            }
        }
    }
}

@Composable
private fun CountersSection(deck: Deck, catalog: CatalogIndex, viewModel: AppViewModel, onOpenDeck: (String) -> Unit) {
    val counters = deck.global?.counters.orEmpty()
    if (counters.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    Section("불리한 상대") {
        counters.forEach { counter ->
            val target = counter.deck?.let { viewModel.deck(it) }
            // 우리 목록에 대응 덱이 있으면 그 이름, 없으면 metatft 클러스터 이름(유닛 id 를 한글로 풀어서).
            val name = target?.name
                ?: counter.name?.takeIf { it.isNotBlank() }?.let { raw ->
                    raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                        .joinToString(", ") { id -> catalog.unit(id)?.name ?: catalog.traits[id]?.name ?: id }
                }
                ?: counter.cluster?.let { "metatft 구성 $it" }
                ?: "알 수 없는 덱"
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .then(if (target != null) Modifier.clickable { onOpenDeck(target.id) } else Modifier)
                    .padding(vertical = 6.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (target != null) scheme.onSurface else scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                counter.placeChange?.let { change ->
                    Text(
                        String.format(Locale.US, "%+.2f등", change),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (change > 0) scheme.error else scheme.primary,
                    )
                }
            }
        }
        Text(
            "상대가 이 구성일 때 평균 등수가 얼마나 밀리는지(metatft)",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GlobalCompareSection(deck: Deck, feed: DeckFeed?) {
    val scheme = MaterialTheme.colorScheme
    val global = deck.global
    val scopes = DeckKeys.SCOPE_ORDER.filter { global?.stats?.get(it) != null }

    if (global != null && scopes.isNotEmpty()) {
        Section("글로벌 비교") {
            scopes.forEach { key -> ScopeBar(key, global.stats.getValue(key)) }
            val days = feed?.scopes?.get(DeckKeys.SCOPE_GLOBAL_PLAT)?.days?.takeIf { it > 0 }
            Text(
                "metatft 유사 구성(유사도 ${formatPct(global.similarity, 0)})" + (days?.let { " · 최근 ${it}일" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }
        return
    }

    if (deck.metatft.compared) {
        Section("metatft 대조") {
            Text(
                if (deck.isOnlyInChina) {
                    "metatft에 대응 덱 없음(최고 유사도 ${"%.0f".format(deck.metatft.similarity * 100)}%). 중국 서버에서만 쓰이는 구성일 수 있습니다."
                } else {
                    "metatft에도 같은 구성이 있습니다 (유사도 ${"%.0f".format(deck.metatft.similarity * 100)}%)."
                },
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

/** 스코프 하나: 이름 · 표본/평균/TOP4, 그 아래 1~8등 비율 막대(칸 폭이 비율). */
@Composable
private fun ScopeBar(key: String, stat: ScopeStat) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(scopeLabel(key), style = MaterialTheme.typography.labelMedium, color = scheme.onSurface, modifier = Modifier.weight(1f))
            Text(
                "n=${formatCount(stat.n)} · ${formatAvg(stat.avg)}등 · TOP4 ${formatPct(stat.top4)}",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }
        val places = stat.places.take(8)
        if (places.sum() > 0) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp)),
            ) {
                places.forEachIndexed { index, count ->
                    if (count > 0) {
                        Box(
                            Modifier
                                .weight(count.toFloat())
                                .fillMaxHeight()
                                .background(placeColor(index + 1).copy(alpha = if (index % 2 == 0) 0.95f else 0.75f))
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Text("1등", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text("8등", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}

