package com.tftdeck.reader.ui.codex

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tftdeck.reader.data.ItemRow
import com.tftdeck.reader.data.StatsState
import com.tftdeck.reader.ui.components.EmptyState

// 360dp 폰에서 11칸이 각각 30dp를 넘도록 바깥 여백과 칸 사이를 줄였다: (360 - 12 - 10) / 11 ≈ 30.7dp.
private val GridPadding = 6.dp
private val GridGap = 1.dp

/**
 * 부품 조합표(11×11). 첫 행·열은 기본 아이템, 칸은 두 부품으로 만드는 완성 아이템이다.
 *
 * 칸 크기는 가로 스크롤 없이 폭을 11로 나눠 정한다(weight + aspectRatio).
 * 머리칸을 누르면 그 행·열이 강조되고, 칸을 누르면 하단 시트에 효과·통계·착용자가 뜬다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemGrid(
    ready: StatsState.Ready,
    scope: String,
    assetBase: String,
    onOpenItem: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val file = ready.items
    val components = remember(ready) { CodexQuery.componentIds(file) }
    if (components.isEmpty() || file.recipes.isEmpty()) {
        EmptyState(title = "조합표 데이터가 없습니다", detail = "도감 데이터를 갱신하면 채워집니다.")
        return
    }

    var highlightRow by remember { mutableStateOf<String?>(null) }
    var highlightColumn by remember { mutableStateOf<String?>(null) }
    var sheetItemId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = GridPadding, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(GridGap),
    ) {
        Text(
            "머리칸을 누르면 그 줄이 강조되고, 칸을 누르면 아이템 정보가 열립니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
        )

        // 첫 행: 모서리 빈칸 + 열 머리칸
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GridGap)) {
            Spacer(
                Modifier
                    .weight(1f)
                    .aspectRatio(1f)
            )
            components.forEach { columnId ->
                HeaderCell(
                    item = ready.itemsById[columnId],
                    id = columnId,
                    assetBase = assetBase,
                    selected = highlightColumn == columnId,
                    modifier = Modifier.weight(1f),
                ) {
                    highlightColumn = if (highlightColumn == columnId) null else columnId
                }
            }
        }

        components.forEach { rowId ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GridGap)) {
                HeaderCell(
                    item = ready.itemsById[rowId],
                    id = rowId,
                    assetBase = assetBase,
                    selected = highlightRow == rowId,
                    modifier = Modifier.weight(1f),
                ) {
                    highlightRow = if (highlightRow == rowId) null else rowId
                }
                components.forEach { columnId ->
                    val itemId = file.recipeFor(rowId, columnId)
                    val focused = (highlightRow == null && highlightColumn == null) ||
                        highlightRow == rowId || highlightColumn == columnId
                    RecipeCell(
                        item = itemId?.let { ready.itemsById[it] },
                        itemId = itemId,
                        assetBase = assetBase,
                        dimmed = !focused,
                        modifier = Modifier.weight(1f),
                    ) {
                        sheetItemId = itemId
                    }
                }
            }
        }
    }

    sheetItemId?.let { itemId ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { sheetItemId = null }, sheetState = sheetState) {
            ItemSheetContent(
                itemId = itemId,
                ready = ready,
                scope = scope,
                assetBase = assetBase,
                onDetail = {
                    sheetItemId = null
                    onOpenItem(itemId)
                },
            )
        }
    }
}

@Composable
private fun HeaderCell(
    item: ItemRow?,
    id: String,
    assetBase: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(5.dp)
    Box(
        modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(if (selected) scheme.primaryContainer else scheme.surfaceVariant)
            .then(if (selected) Modifier.border(2.dp, scheme.primary, shape) else Modifier)
            .clickable(onClickLabel = "줄 강조", onClick = onClick)
            .semantics { contentDescription = item?.name ?: id }
            .padding(2.dp),
    ) {
        AsyncImage(
            model = codexIconUrl(assetBase, item?.icon),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(4.dp)),
        )
    }
}

@Composable
private fun RecipeCell(
    item: ItemRow?,
    itemId: String?,
    assetBase: String,
    dimmed: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.surface)
            .alpha(if (dimmed) 0.25f else 1f)
            .then(
                if (itemId != null) {
                    Modifier
                        .clickable(onClickLabel = "아이템 정보", onClick = onClick)
                        .semantics { contentDescription = item?.name ?: itemId }
                } else {
                    Modifier
                }
            ),
    ) {
        if (itemId != null) {
            AsyncImage(
                model = codexIconUrl(assetBase, item?.icon),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** 조합표 칸의 하단 시트: 효과, 선택 스코프 성적, 착용자, 상세 버튼. */
@Composable
private fun ItemSheetContent(
    itemId: String,
    ready: StatsState.Ready,
    scope: String,
    assetBase: String,
    onDetail: () -> Unit,
) {
    val itemRow = ready.itemsById[itemId]
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CodexIcon(codexIconUrl(assetBase, itemRow?.icon), itemRow?.name ?: itemId, 48.dp, corner = 8.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(itemRow?.name ?: itemId, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
                if (itemRow != null) {
                    val recipe = itemRow.components.joinToString(" + ") { ready.itemsById[it]?.name ?: it }
                    Text(
                        listOf(kindLabel(itemRow.kind), recipe).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (itemRow == null) {
            Text(
                "이 조합의 아이템 정보가 도감 데이터에 없습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        } else {
            val desc = cleanDesc(itemRow.desc)
            if (desc.isNotBlank()) {
                Text(desc, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
            }
            val selected = CodexQuery.resolveScope(scope, CodexQuery.availableScopes(ready.items.scopes))
            val stat = itemRow.stat(selected)
            Row(verticalAlignment = Alignment.CenterVertically) {
                GradeBadge(stat?.grade)
                Spacer(Modifier.width(8.dp))
                Text(
                    "${scopeLabel(selected)} · 평균 ${formatAvg(stat?.avg)} · TOP4 ${formatPct(stat?.top4Share)} · ${formatCountShort(stat?.n)} 판",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurface,
                )
            }
            CodexQuery.wearersFor(itemRow, selected)?.let { (wearerScope, wearers) ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("많이 드는 챔피언", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    WearerStrip(wearers, wearerScope.takeIf { it != selected }, ready, assetBase)
                }
            }
            Button(onClick = onDetail, modifier = Modifier.fillMaxWidth()) { Text("상세 보기") }
        }
    }
}
