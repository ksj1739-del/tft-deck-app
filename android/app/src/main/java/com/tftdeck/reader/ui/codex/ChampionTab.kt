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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tftdeck.reader.data.CodexRef
import com.tftdeck.reader.ui.components.EmptyState
import com.tftdeck.reader.ui.costColor

/**
 * 챔피언 탭: 코스트 칩, 특성 드롭다운, 이름·초성 검색, 정렬 칩, 표.
 * 필터는 목록과 함께 스크롤되어 올라가고 열 이름 줄만 위에 붙는다(첫 화면에 행이 더 보이게).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChampionTab(viewModel: CodexViewModel, onOpenChampion: (String) -> Unit) {
    val table by viewModel.championTable.collectAsState()
    val filter by viewModel.championFilter.collectAsState()
    val traitOptions by viewModel.championTraitOptions.collectAsState()
    val assetBase by viewModel.assetBase.collectAsState()
    val current = table

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "filters", contentType = "filters") {
            ChampionFilters(filter, traitOptions, viewModel)
        }
        stickyHeader(key = "header", contentType = "header") {
            CodexTableHeader(firstLabel = "챔피언")
        }
        when {
            current == null -> item(key = "loading") { ListLoading() }
            current.rows.isEmpty() -> item(key = "empty") {
                EmptyState(
                    title = "조건에 맞는 챔피언이 없습니다",
                    detail = "코스트·특성 필터를 줄이거나 검색어를 바꿔 보세요.",
                )
            }
            else -> items(current.rows, key = { it.champion.id }, contentType = { "champion" }) { row ->
                ChampionListItem(row, assetBase) { onOpenChampion(row.champion.id) }
            }
        }
    }
}

@Composable
private fun ChampionFilters(filter: ChampionFilter, traitOptions: List<CodexRef>, viewModel: CodexViewModel) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = CodexHPad, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterLabel("코스트")
            (1..5).forEach { cost ->
                FilterChip(
                    selected = cost in filter.costs,
                    onClick = { viewModel.toggleChampionCost(cost) },
                    label = { Text("$cost") },
                    leadingIcon = { ColorDot(costColor(cost)) },
                )
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TraitDropdown(traitOptions, filter.traitId, viewModel::setChampionTrait, Modifier.weight(1f))
            CodexSearchField(
                value = filter.query,
                onValueChange = viewModel::setChampionQuery,
                placeholder = "이름·초성",
                modifier = Modifier.weight(1f),
                showIcon = false,
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterLabel("정렬")
            ChampionSort.entries.forEach { sort ->
                FilterChip(
                    selected = filter.sort == sort,
                    onClick = { viewModel.setChampionSort(sort) },
                    label = { Text(sort.label) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TraitDropdown(
    options: List<CodexRef>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = options.firstOrNull { it.id == selectedId }?.name ?: ALL_TRAITS

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(ALL_TRAITS) },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.name) },
                    onClick = {
                        onSelect(option.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

private const val ALL_TRAITS = "전체 특성"

@Composable
private fun ChampionListItem(row: ChampionListRow, assetBase: String, onClick: () -> Unit) {
    val champion = row.champion
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
        CodexIcon(codexIconUrl(assetBase, champion.icon), champion.name, 32.dp, borderColor = costColor(champion.cost))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                champion.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val traits = champion.traits.joinToString(" · ") { it.name }
            if (traits.isNotBlank()) {
                Text(
                    traits,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        StandardStatCells(row.stat)
    }
}
