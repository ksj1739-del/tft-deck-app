package com.tftdeck.reader.ui.codex

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tftdeck.reader.data.SearchAxis
import com.tftdeck.reader.data.StatsState
import com.tftdeck.reader.data.TraitChampionRef
import com.tftdeck.reader.data.TraitRow
import com.tftdeck.reader.ui.traitStyleColor

/**
 * 특성 상세: 헤더 → 단계 효과표 → 챔피언(코스트순) → 단계별 성적(스코프 칩) → 함께 쓰는 특성 → 덱.
 */
@Composable
fun TraitDetailScreen(
    id: String,
    viewModel: CodexViewModel,
    onOpenDeck: (String) -> Unit,
    onOpenChampion: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val scope by viewModel.scope.collectAsState()
    val assetBase by viewModel.assetBase.collectAsState()
    val deckLinks by viewModel.deckLinks.collectAsState()

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
    val trait = ready.traitsById[id]
    if (trait == null) {
        CodexNotFound("특성")
        return
    }

    val file = ready.traits
    val scopes = CodexQuery.availableScopes(file.scopes)
    val selected = CodexQuery.resolveScope(scope, scopes)
    val decks = remember(deckLinks, trait.id, trait.decks) {
        viewModel.decksFor(trait.decks, SearchAxis.TRAIT, trait.name)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = DetailPadding,
        verticalArrangement = Arrangement.spacedBy(DetailGap),
    ) {
        item(key = "header") { TraitHeader(trait, assetBase) }
        if (trait.breakpoints.isNotEmpty()) {
            item(key = "breakpoints") { BreakpointSection(trait) }
        }
        if (trait.champions.isNotEmpty()) {
            item(key = "champions") { TraitChampionsSection(trait, ready, assetBase, onOpenChampion) }
        }
        if (trait.stageUnits.isNotEmpty()) {
            item(key = "stages") {
                StageStatsSection(trait, file.minSample, scopes, selected, assetBase, viewModel::setScope)
            }
        }
        if (trait.combos.isNotEmpty()) {
            item(key = "combos") { CombosSection(trait) }
        }
        item(key = "decks") {
            DeckLinkSection("이 특성 덱", decks, "이 특성이 들어간 덱이 없습니다.", onOpenDeck)
        }
    }
}

@Composable
private fun TraitHeader(trait: TraitRow, assetBase: String) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TraitGlyph(codexIconUrl(assetBase, trait.icon), trait.topStyle, 56.dp, contentDescription = trait.name)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(trait.name, style = MaterialTheme.typography.titleLarge, color = scheme.onSurface)
                val sub = listOf(traitTypeLabel(trait.type), trait.nameEn.orEmpty()).filter { it.isNotBlank() }.joinToString(" · ")
                if (sub.isNotBlank()) {
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            }
        }
        val desc = cleanDesc(trait.desc)
        if (desc.isNotBlank()) {
            Text(desc, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
        }
    }
}

@Composable
private fun BreakpointSection(trait: TraitRow) {
    val scheme = MaterialTheme.colorScheme
    Column {
        SectionTitle("단계 효과")
        trait.breakpoints.sortedBy { it.units }.forEach { breakpoint ->
            val styled = breakpoint.style in 1..4
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    Modifier
                        .width(44.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (styled) traitStyleColor(breakpoint.style) else scheme.surfaceVariant)
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${breakpoint.units}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (styled) BadgeInk else scheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    val desc = cleanDesc(breakpoint.desc)
                    Text(
                        desc.ifBlank { "${breakpoint.units}명 활성" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurface,
                    )
                    val styleName = styleLabel(breakpoint.style)
                    if (styleName.isNotBlank()) {
                        Text(styleName, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TraitChampionsSection(
    trait: TraitRow,
    ready: StatsState.Ready,
    assetBase: String,
    onOpenChampion: (String) -> Unit,
) {
    val sorted = trait.champions.sortedWith(compareBy<TraitChampionRef>({ it.cost ?: 99 }, { it.name }))
    Column {
        SectionTitle("챔피언 ${sorted.size}명", trailing = "코스트순")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            sorted.forEach { ref ->
                val champion = ready.championsById[ref.id]
                ChampionTile(
                    url = codexIconUrl(assetBase, champion?.icon),
                    name = ref.name.ifBlank { champion?.name ?: ref.id },
                    cost = ref.cost ?: champion?.cost,
                    onClick = { onOpenChampion(ref.id) },
                )
            }
        }
    }
}

@Composable
private fun StageStatsSection(
    trait: TraitRow,
    minSample: Int,
    scopes: List<String>,
    selected: String,
    assetBase: String,
    onSelectScope: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column {
        SectionTitle("단계별 성적", trailing = scopeLabel(selected))
        ScopeChips(scopes, selected, onSelectScope, horizontalPadding = 0.dp)
        Spacer(Modifier.height(6.dp))
        CodexTableHeader(firstLabel = "단계", horizontalPadding = 0.dp)
        trait.stageUnits.forEach { units ->
            val stat = trait.stat(selected, units)
            val dim = stat == null || stat.isLowSample(minSample)
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .alpha(if (dim) DIM_ALPHA else 1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TraitGlyph(codexIconUrl(assetBase, trait.icon), trait.styleFor(units), 24.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "$units ${trait.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                StandardStatCells(stat)
            }
        }
    }
}

@Composable
private fun CombosSection(trait: TraitRow) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("함께 쓰는 특성", trailing = "중국 · TOP4 추세")
        trait.combos.forEach { combo ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(scheme.surfaceVariant)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val selfUnits = combo.units.takeIf { it > 0 }?.let { "$it " }.orEmpty()
                    val partnerUnits = combo.partner.units.takeIf { it > 0 }?.let { "$it " }.orEmpty()
                    Text(
                        "$selfUnits${trait.name} + $partnerUnits${combo.partner.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface,
                    )
                    Text(
                        "TOP4 ${formatPct(combo.top4, 0)} · 승률 ${formatPct(combo.win, 0)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                    trendText(combo.trend)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(10.dp))
                TrendBars(combo.trend)
            }
        }
    }
}

/** "추세 58% → 61%"(처음과 마지막 값). 점이 둘 미만이면 null. */
internal fun trendText(trend: List<Double?>): String? {
    val points = trend.mapNotNull { value -> value?.takeIf { it.isFinite() } }
    if (points.size < 2) return null
    return "추세 ${formatPct(points.first(), 0)} → ${formatPct(points.last(), 0)}"
}
