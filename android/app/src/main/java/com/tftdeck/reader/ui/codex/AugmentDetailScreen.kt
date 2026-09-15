package com.tftdeck.reader.ui.codex

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tftdeck.reader.data.AugmentCnStatsMeta
import com.tftdeck.reader.data.AugmentDeckStat
import com.tftdeck.reader.data.AugmentEditorTierMeta
import com.tftdeck.reader.data.AugmentRow
import com.tftdeck.reader.data.StatsState

/** 증강을 고른 시점(첫째·둘째·셋째 증강). lol.qq 1_/2_/3_avg_rank 순서. */
private val STAGE_LABELS = listOf("2-1", "3-2", "4-2")

/**
 * 증강 상세: 설명 → 에디터 티어(출처) → 중국 통계 요약 → 덱별 성적(고른 시점별 평균은 칩으로 접힘) →
 * 추천 덱(작가/가이드/통계) → 라운드별 확률표(값이 있을 때만).
 */
@Composable
fun AugmentDetailScreen(
    id: String,
    viewModel: CodexViewModel,
    onOpenDeck: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val assetBase by viewModel.assetBase.collectAsState()
    val deckLinks by viewModel.deckLinks.collectAsState()
    var stageIndex by rememberSaveable(id) { mutableIntStateOf(-1) }

    val ready = when (val current = state) {
        is StatsState.Loading -> {
            CodexLoading()
            return
        }
        is StatsState.Missing -> {
            CodexMissing()
            return
        }
        is StatsState.Ready -> current
    }
    val augment = ready.augmentsById[id]
    if (augment == null) {
        CodexNotFound("증강")
        return
    }

    val meta = ready.augments.meta
    val rounds = remember(ready) { CodexQuery.roundRows(ready.augments) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = DetailPadding,
        verticalArrangement = Arrangement.spacedBy(DetailGap),
    ) {
        item(key = "header") { AugmentHeader(augment, assetBase) }
        item(key = "editor") { EditorTierSection(augment, meta.editorTier) }
        item(key = "summary") { SummarySection(augment, meta.cnStats) }
        if (augment.deckStats.isNotEmpty()) {
            item(key = "deckStats") {
                DeckStatsSection(
                    augment = augment,
                    cn = meta.cnStats,
                    stageIndex = stageIndex,
                    onStage = { stageIndex = it },
                    deckLinks = deckLinks,
                    onOpenDeck = onOpenDeck,
                )
            }
        }
        item(key = "recommended") { RecommendedDecksSection(augment, deckLinks, onOpenDeck) }
        if (rounds.isNotEmpty()) {
            item(key = "rounds") { RoundsSection(rounds) }
        }
    }
}

@Composable
private fun AugmentHeader(augment: AugmentRow, assetBase: String) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CodexIcon(
                codexIconUrl(assetBase, augment.icon),
                augment.name,
                56.dp,
                corner = 10.dp,
                borderColor = rarityColor(augment.rarity),
                borderWidth = 2.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(augment.name, style = MaterialTheme.typography.titleLarge, color = scheme.onSurface)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    augment.rarity?.takeIf { it.isNotBlank() }?.let { rarity ->
                        LabelChip(rarityLabel(rarity), rarityColor(rarity))
                        Spacer(Modifier.width(6.dp))
                    }
                    if (augment.tags.isNotEmpty()) {
                        Text(
                            augment.tags.joinToString(" · ", transform = ::tagLabel),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
                augment.nameEn?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            }
        }
        val desc = cleanDesc(augment.desc)
        if (desc.isNotBlank()) {
            Text(desc, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
        }
    }
}

@Composable
private fun EditorTierSection(augment: AugmentRow, meta: AugmentEditorTierMeta) {
    val scheme = MaterialTheme.colorScheme
    val tier = augment.editorTier?.takeIf { it.isNotBlank() }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionTitle("에디터 티어")
        if (tier == null) {
            Text("편집자 티어가 매겨지지 않은 증강입니다.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GradeBadge(tier, large = true)
                Spacer(Modifier.width(10.dp))
                Text(editorSourceText(meta), style = MaterialTheme.typography.bodySmall, color = scheme.onSurface)
            }
        }
        Text("통계가 아니라 편집자 한 사람의 평가입니다.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
    }
}

/** "중국 골드~에메랄드 · 9/14 기준". */
internal fun cnStatsLabel(cn: AugmentCnStatsMeta): String =
    listOf(
        cn.label,
        cn.detailDate.takeIf { it.isNotBlank() }?.let { "${formatShortDate(it)} 기준" }.orEmpty(),
    ).filter { it.isNotBlank() }.joinToString(" · ")

@Composable
private fun SummarySection(augment: AugmentRow, cn: AugmentCnStatsMeta) {
    val scheme = MaterialTheme.colorScheme
    val summary = augment.summary
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("중국 통계 요약", trailing = cnStatsLabel(cn))
        if (summary.n <= 0) {
            Text("이 증강의 중국 덱별 통계가 없습니다.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryBlock("평균 등수", formatAvg(summary.avg), Modifier.weight(1f))
                SummaryBlock("게임 수", formatCount(summary.n), Modifier.weight(1f))
                SummaryBlock("덱", "${summary.decks}개", Modifier.weight(1f))
            }
            Text(
                "덱마다 상위 5개 증강만 집계되어 실제보다 좋게 나올 수 있습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SummaryBlock(label: String, value: String, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(scheme.surfaceVariant)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
    }
}

@Composable
private fun DeckStatsSection(
    augment: AugmentRow,
    cn: AugmentCnStatsMeta,
    stageIndex: Int,
    onStage: (Int) -> Unit,
    deckLinks: Map<String, DeckLink>,
    onOpenDeck: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val rows = remember(augment.deckStats) {
        augment.deckStats.sortedWith(compareBy<AugmentDeckStat>({ it.avg ?: Double.MAX_VALUE }, { -it.n }))
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionTitle("덱별 성적", trailing = cnStatsLabel(cn))
        Text(
            "고른 시점을 누르면 그때 골랐을 때의 평균이 나옵니다. 흐린 값은 표본이 적습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterLabel("고른 시점")
            STAGE_LABELS.forEachIndexed { index, label ->
                FilterChip(
                    selected = stageIndex == index,
                    onClick = { onStage(if (stageIndex == index) -1 else index) },
                    label = { Text(label) },
                )
            }
        }
        rows.forEach { stat ->
            val link = deckLinks[stat.deck]
            val name = link?.name ?: stat.deckName.ifBlank { stat.deck }
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .then(if (link != null) Modifier.clickable { onOpenDeck(stat.deck) } else Modifier)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(stat.rank?.let { "덱 안 ${it}위" }, "${formatCount(stat.n)} 판").joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                if (stageIndex in STAGE_LABELS.indices) {
                    val value = stat.stage.getOrNull(stageIndex)?.takeIf { it.isFinite() }
                    val lowSample = stat.stageLowSample.getOrNull(stageIndex) == true
                    Column(
                        Modifier
                            .width(64.dp)
                            .alpha(if (value == null || lowSample) DIM_ALPHA else 1f),
                        horizontalAlignment = Alignment.End,
                    ) {
                        Text(STAGE_LABELS[stageIndex], style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        Text(rankText(value), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
                    }
                }
                Column(Modifier.width(64.dp), horizontalAlignment = Alignment.End) {
                    Text("전체", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    Text(rankText(stat.avg), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun RecommendedDecksSection(
    augment: AugmentRow,
    deckLinks: Map<String, DeckLink>,
    onOpenDeck: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val editorialIds = augment.recommendedBy.editorial.distinct()
    val editorial = editorialIds.mapNotNull { deckLinks[it] }
    val missingEditorial = editorialIds.count { it !in deckLinks }
    val guide = augment.recommendedBy.guide.mapNotNull { ref -> deckLinks[ref.deck]?.let { it to ref } }
    val statTop = augment.deckStats.filter { it.avg != null }.sortedBy { it.avg }.take(3)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionTitle("추천 덱")
        if (editorialIds.isEmpty() && guide.isEmpty() && statTop.isEmpty()) {
            Text("이 증강을 추천하는 덱 정보가 없습니다.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        if (editorialIds.isNotEmpty()) {
            SubLabel("작가 추천")
            editorial.forEach { DeckLinkRow(it, onOpenDeck) }
            if (missingEditorial > 0) {
                Text(
                    "지금 덱 목록에 없는 추천 덱 ${missingEditorial}개",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        if (guide.isNotEmpty()) {
            SubLabel("가이드 S")
            guide.forEach { (link, ref) -> DeckLinkRow(link, onOpenDeck, caption = ref.source.takeIf { it.isNotBlank() }) }
        }
        if (statTop.isNotEmpty()) {
            SubLabel("통계 상위")
            statTop.forEach { stat ->
                val link = deckLinks[stat.deck]
                DeckLinkRow(
                    link = link ?: DeckLink(stat.deck, stat.deckName.ifBlank { stat.deck }, ""),
                    onOpenDeck = if (link != null) onOpenDeck else null,
                    caption = "${rankText(stat.avg)} · ${formatCount(stat.n)} 판",
                )
            }
        }
    }
}

@Composable
private fun SubLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** 라운드별 등장 확률(게임 상수). 수집기가 값을 채운 경우에만 호출된다. */
@Composable
private fun RoundsSection(rows: List<Pair<String, Map<String, Double?>>>) {
    val scheme = MaterialTheme.colorScheme
    val rarities = (RARITY_KEYS + rows.flatMap { it.second.keys })
        .distinct()
        .filter { key -> rows.any { it.second.containsKey(key) } }
    Column {
        SectionTitle("라운드별 등장 확률")
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Text("라운드", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, modifier = Modifier.width(64.dp))
            rarities.forEach { rarity ->
                Text(
                    rarityLabel(rarity),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        rows.forEach { (round, odds) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(round, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface, modifier = Modifier.width(64.dp))
                rarities.forEach { rarity ->
                    Text(
                        formatPct(odds[rarity], 0),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurface,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
