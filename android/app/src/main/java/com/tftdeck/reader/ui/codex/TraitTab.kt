package com.tftdeck.reader.ui.codex

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tftdeck.reader.ui.components.EmptyState
import com.tftdeck.reader.ui.traitStyleColor

/**
 * 특성 탭: 등급 칩(전체/프리즘/골드/실버/브론즈), 계열/직업 토글, 단계별 보기 토글, 표.
 * 단계별 보기를 끄면 특성마다 표본이 가장 큰 단계 한 줄만 보여 준다.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TraitTab(viewModel: CodexViewModel, onOpenTrait: (String) -> Unit) {
    val table by viewModel.traitTable.collectAsState()
    val filter by viewModel.traitFilter.collectAsState()
    val assetBase by viewModel.assetBase.collectAsState()
    val current = table

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "filters", contentType = "filters") { TraitFilters(filter, viewModel) }
        stickyHeader(key = "header", contentType = "header") { CodexTableHeader(firstLabel = "특성") }
        when {
            current == null -> item(key = "loading") { ListLoading() }
            current.rows.isEmpty() -> item(key = "empty") {
                EmptyState(title = "조건에 맞는 특성이 없습니다", detail = "등급·유형 필터를 줄여 보세요.")
            }
            else -> items(current.rows, key = { it.key }, contentType = { "trait" }) { row ->
                TraitListItem(row, assetBase) { onOpenTrait(row.trait.id) }
            }
        }
    }
}

private val STYLE_FILTERS = listOf(4, 3, 2, 1)

@Composable
private fun TraitFilters(filter: TraitFilter, viewModel: CodexViewModel) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = CodexHPad, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = filter.style == null,
                onClick = { viewModel.setTraitStyle(null) },
                label = { Text("전체") },
            )
            STYLE_FILTERS.forEach { style ->
                FilterChip(
                    selected = filter.style == style,
                    onClick = { viewModel.setTraitStyle(if (filter.style == style) null else style) },
                    label = { Text(styleLabel(style)) },
                    leadingIcon = { ColorDot(traitStyleColor(style)) },
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = TYPE_ORIGIN in filter.types,
                onClick = { viewModel.toggleTraitType(TYPE_ORIGIN) },
                label = { Text("계열") },
            )
            FilterChip(
                selected = TYPE_CLASS in filter.types,
                onClick = { viewModel.toggleTraitType(TYPE_CLASS) },
                label = { Text("직업") },
            )
            Spacer(Modifier.width(6.dp))
            FilterChip(
                selected = filter.byStage,
                onClick = { viewModel.setTraitByStage(!filter.byStage) },
                label = { Text("단계별 보기") },
            )
        }
    }
}

private const val TYPE_ORIGIN = "origin"
private const val TYPE_CLASS = "class"

@Composable
private fun TraitListItem(row: TraitListRow, assetBase: String, onClick: () -> Unit) {
    val trait = row.trait
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .clickable(onClick = onClick)
            .alpha(if (row.dimmed) DIM_ALPHA else 1f)
            .padding(horizontal = CodexHPad, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TraitGlyph(codexIconUrl(assetBase, trait.icon), row.style, 32.dp, contentDescription = trait.name)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            // lolchess와 같은 표기: 활성 인원수 + 이름("5 사냥꾼").
            Text(
                row.units?.let { "$it ${trait.name}" } ?: trait.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = listOf(styleLabel(row.style), traitTypeLabel(trait.type)).filter { it.isNotBlank() }.joinToString(" · ")
            if (sub.isNotBlank()) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1)
            }
        }
        StandardStatCells(row.stat)
    }
}
