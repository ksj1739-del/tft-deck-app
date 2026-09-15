package com.tftdeck.reader.ui.codex

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tftdeck.reader.data.StatsState
import com.tftdeck.reader.ui.components.EmptyState

/** 아이템 탭: '통계' 표와 '조합표' 격자. */
@Composable
fun ItemTab(viewModel: CodexViewModel, onOpenItem: (String) -> Unit) {
    val subTab by viewModel.itemSubTab.collectAsState()
    Column(Modifier.fillMaxSize()) {
        CodexSubTabs(listOf("통계", "조합표"), subTab, viewModel::setItemSubTab)
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (subTab == 1) ItemGridTab(viewModel, onOpenItem) else ItemStatsList(viewModel, onOpenItem)
        }
    }
}

@Composable
private fun ItemGridTab(viewModel: CodexViewModel, onOpenItem: (String) -> Unit) {
    val state by viewModel.state.collectAsState()
    val scope by viewModel.scope.collectAsState()
    val assetBase by viewModel.assetBase.collectAsState()
    val ready = state as? StatsState.Ready ?: return
    ItemGrid(ready, scope, assetBase, onOpenItem)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ItemStatsList(viewModel: CodexViewModel, onOpenItem: (String) -> Unit) {
    val table by viewModel.itemTable.collectAsState()
    val filter by viewModel.itemFilter.collectAsState()
    val state by viewModel.state.collectAsState()
    val assetBase by viewModel.assetBase.collectAsState()
    val ready = state as? StatsState.Ready ?: return
    val current = table

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "filters", contentType = "filters") { ItemFilters(filter, ready, assetBase, viewModel) }
        stickyHeader(key = "header", contentType = "header") { CodexTableHeader(firstLabel = "아이템") }
        when {
            current == null -> item(key = "loading") { ListLoading() }
            current.rows.isEmpty() -> item(key = "empty") {
                EmptyState(title = "조건에 맞는 아이템이 없습니다", detail = "종류·부품 필터를 줄이거나 검색어를 바꿔 보세요.")
            }
            else -> items(current.rows, key = { it.item.id }, contentType = { "item" }) { row ->
                ItemListCard(row, current.scope, ready, assetBase) { onOpenItem(row.item.id) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemFilters(
    filter: ItemFilter,
    ready: StatsState.Ready,
    assetBase: String,
    viewModel: CodexViewModel,
) {
    val scheme = MaterialTheme.colorScheme
    val components = remember(ready) { CodexQuery.componentIds(ready.items) }
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
            FilterChip(
                selected = filter.kind == null,
                onClick = { viewModel.setItemKind(null) },
                label = { Text("전체") },
            )
            // 이번 파일에 실제로 있는 종류만 칩으로 둔다. 행이 없는 칩은 누르면 늘 빈 목록이다.
            val kinds = remember(ready) { ITEM_KIND_FILTERS.filter { kind -> ready.items.items.any { it.kind == kind } } }
            kinds.forEach { kind ->
                FilterChip(
                    selected = filter.kind == kind,
                    onClick = { viewModel.setItemKind(if (filter.kind == kind) null else kind) },
                    label = { Text(kindLabel(kind)) },
                )
            }
        }

        if (components.isNotEmpty()) {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                components.forEach { componentId ->
                    val component = ready.itemsById[componentId]
                    ComponentToggle(
                        url = codexIconUrl(assetBase, component?.icon),
                        name = component?.name ?: componentId,
                        checked = componentId in filter.components,
                        onToggle = { viewModel.toggleItemComponent(componentId) },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (filter.components.isEmpty()) {
                        "부품을 1~2개 고르면 그 부품이 들어간 아이템만 보입니다"
                    } else {
                        filter.components.joinToString(" + ") { ready.itemsById[it]?.name ?: it } + " 포함"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (filter.components.isNotEmpty()) {
                    TextButton(onClick = viewModel::clearItemComponents) { Text("부품 해제") }
                }
            }
        }

        CodexSearchField(filter.query, viewModel::setItemQuery, "아이템 이름·초성", Modifier.fillMaxWidth())
    }
}

/** 부품 칩. 아이콘만으로 알아보므로 이름은 읽기 도구에만 준다. 누르는 영역은 48dp를 지킨다. */
@Composable
private fun ComponentToggle(url: String?, name: String, checked: Boolean, onToggle: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .size(48.dp)
            .clip(shape)
            .background(if (checked) scheme.primaryContainer else scheme.surfaceVariant)
            .border(if (checked) 2.dp else 1.dp, if (checked) scheme.primary else scheme.outlineVariant, shape)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics { contentDescription = name }
            .padding(5.dp),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(5.dp)),
        )
    }
}

/** 두 줄 카드: 1줄 아이콘·이름·등급·평균·TOP4·게임 수, 2줄 많이 드는 챔피언 5명. */
@Composable
private fun ItemListCard(
    row: ItemListRow,
    scope: String,
    ready: StatsState.Ready,
    assetBase: String,
    onClick: () -> Unit,
) {
    val itemRow = row.item
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .alpha(if (row.dimmed) DIM_ALPHA else 1f)
            .padding(horizontal = CodexHPad, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CodexIcon(codexIconUrl(assetBase, itemRow.icon), itemRow.name, 32.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    itemRow.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(kindLabel(itemRow.kind), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            StandardStatCells(row.stat)
        }
        if (row.wearers.isNotEmpty()) {
            WearerStrip(
                wearers = row.wearers,
                fallbackScope = row.wearerScope?.takeIf { it != scope },
                ready = ready,
                assetBase = assetBase,
                modifier = Modifier.padding(start = 40.dp),
            )
        }
    }
}
