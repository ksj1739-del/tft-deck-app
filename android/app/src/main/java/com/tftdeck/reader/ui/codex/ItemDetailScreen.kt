package com.tftdeck.reader.ui.codex

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tftdeck.reader.data.ItemRow
import com.tftdeck.reader.data.ItemWearerStat
import com.tftdeck.reader.data.SearchAxis
import com.tftdeck.reader.data.StatScope
import com.tftdeck.reader.data.StatsState
import com.tftdeck.reader.ui.costColor

/**
 * 아이템 상세: 효과 → 조합(부품 두 개) → 범위별 성적 → 지난 패치 대비 → 착용자(글로벌/중국) →
 * 완성 시점별 승률 → 이 아이템 덱(기존 아이템 역검색 인덱스).
 */
@Composable
fun ItemDetailScreen(
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
    // LazyColumn 안에서 item() 호출과 헷갈리지 않도록 itemRow라고 부른다.
    val itemRow = ready.itemsById[id]
    if (itemRow == null) {
        CodexNotFound("아이템")
        return
    }

    val file = ready.items
    val scopes = CodexQuery.availableScopes(file.scopes)
    val selected = CodexQuery.resolveScope(scope, scopes)
    val decks = remember(deckLinks, itemRow.id, itemRow.decks) {
        viewModel.decksFor(itemRow.decks, SearchAxis.ITEM, itemRow.name)
    }
    val usedIn = remember(ready, itemRow.id) {
        if (itemRow.kind == CodexQuery.KIND_COMPONENT) CodexQuery.itemsUsing(file, itemRow.id) else emptyList()
    }
    val prevEntries = orderedEntries(itemRow.prev)
    val globalWearers = itemRow.wearers(StatScope.GLOB_PLAT)
    val chinaWearers = itemRow.wearers(StatScope.CN_PLAT)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = DetailPadding,
        verticalArrangement = Arrangement.spacedBy(DetailGap),
    ) {
        item(key = "header") { ItemHeader(itemRow, assetBase) }

        if (itemRow.components.isNotEmpty()) {
            item(key = "recipe") { RecipeSection(itemRow, ready, assetBase) }
        }
        if (usedIn.isNotEmpty()) {
            item(key = "usedIn") { UsedInSection(usedIn, ready, assetBase) }
        }

        if (itemRow.kind != CodexQuery.KIND_COMPONENT || itemRow.stats.values.any { it != null }) {
            item(key = "scopes") {
                Column {
                    SectionTitle("범위별 성적", trailing = "행을 누르면 기준이 바뀝니다")
                    ScopeTable(
                        scopes = scopes,
                        selected = selected,
                        minSample = file.minSample,
                        statFor = { itemRow.stat(it) },
                        onSelect = viewModel::setScope,
                    )
                    if (itemRow.stat(StatScope.CN_PLAT)?.avg == null && itemRow.stat(StatScope.CN_PLAT) != null) {
                        Text(
                            "중국 서버는 아이템 평균 등수가 원천마다 어긋나 픽률만 싣습니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }

        if (prevEntries.isNotEmpty()) {
            item(key = "prev") {
                Column {
                    SectionTitle("지난 패치 대비")
                    prevEntries.forEach { (key, prev) -> PrevChangeRow(key, prev, itemRow.stat(key)) }
                }
            }
        }

        if (globalWearers.isNotEmpty() || chinaWearers.isNotEmpty()) {
            item(key = "wearers") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle("많이 드는 챔피언")
                    WearerGroup("글로벌 플래+", globalWearers, ready, assetBase, onOpenChampion)
                    WearerGroup("중국 플래+", chinaWearers, ready, assetBase, onOpenChampion)
                }
            }
        }

        if (itemRow.stages.isNotEmpty()) {
            item(key = "stages") { StageWinSection(itemRow) }
        }

        item(key = "decks") {
            DeckLinkSection("이 아이템 덱", decks, "이 아이템을 쓰는 덱이 없습니다.", onOpenDeck)
        }
    }
}

@Composable
private fun ItemHeader(itemRow: ItemRow, assetBase: String) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CodexIcon(codexIconUrl(assetBase, itemRow.icon), itemRow.name, 56.dp, corner = 10.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(itemRow.name, style = MaterialTheme.typography.titleLarge, color = scheme.onSurface)
                val sub = listOf(kindLabel(itemRow.kind), itemRow.nameEn.orEmpty()).filter { it.isNotBlank() }.joinToString(" · ")
                if (sub.isNotBlank()) {
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            }
        }
        val desc = cleanDesc(itemRow.desc)
        if (desc.isNotBlank()) {
            Column {
                SectionTitle("효과")
                Text(desc, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
            }
        }
    }
}

@Composable
private fun RecipeSection(itemRow: ItemRow, ready: StatsState.Ready, assetBase: String) {
    val scheme = MaterialTheme.colorScheme
    Column {
        SectionTitle("조합")
        Row(verticalAlignment = Alignment.CenterVertically) {
            itemRow.components.forEachIndexed { index, componentId ->
                if (index > 0) {
                    Text(
                        "+",
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp),
                    )
                }
                val component = ready.itemsById[componentId]
                Column(Modifier.width(88.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CodexIcon(codexIconUrl(assetBase, component?.icon), component?.name ?: componentId, 44.dp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        component?.name ?: componentId,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurface,
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UsedInSection(itemIds: List<String>, ready: StatsState.Ready, assetBase: String) {
    val scheme = MaterialTheme.colorScheme
    Column {
        SectionTitle("이 재료로 만드는 아이템 ${itemIds.size}개")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemIds.forEach { made ->
                val madeRow = ready.itemsById[made]
                Column(Modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CodexIcon(codexIconUrl(assetBase, madeRow?.icon), madeRow?.name ?: made, 40.dp)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        madeRow?.name ?: made,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** 착용자 한 줄(상위 5명). 칸 폭을 5등분해 이름이 길어도 줄이 어긋나지 않게 한다. */
@Composable
private fun WearerGroup(
    label: String,
    wearers: List<ItemWearerStat>,
    ready: StatsState.Ready,
    assetBase: String,
    onOpenChampion: (String) -> Unit,
) {
    if (wearers.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    val shown = wearers.take(CodexQuery.WEARER_LIMIT)
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        ) {
            shown.forEach { wearer ->
                val champion = ready.championsById[wearer.id]
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onOpenChampion(wearer.id) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CodexIcon(codexIconUrl(assetBase, champion?.icon), wearer.name, 40.dp, borderColor = costColor(champion?.cost))
                    Text(
                        wearer.name.ifBlank { champion?.name ?: wearer.id },
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                    Text(rankText(wearer.avg), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1)
                }
            }
            repeat(CodexQuery.WEARER_LIMIT - shown.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun StageWinSection(itemRow: ItemRow) {
    val scheme = MaterialTheme.colorScheme
    Column {
        SectionTitle("완성 시점별 승률", trailing = "글로벌 플래+")
        itemRow.stages.sortedBy { it.stage }.forEach { stage ->
            val win = stage.win?.takeIf { it.isFinite() }
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 36.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "스테이지 ${stage.stage}",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurface,
                    modifier = Modifier.width(72.dp),
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(scheme.surfaceVariant)
                ) {
                    if (win != null) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(win.toFloat().coerceIn(0f, 1f))
                                .background(scheme.primary)
                        )
                    }
                }
                Text(
                    formatPct(win),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(56.dp),
                )
                Text(
                    "${formatCountShort(stage.n)} 판",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(64.dp),
                )
            }
        }
    }
}
