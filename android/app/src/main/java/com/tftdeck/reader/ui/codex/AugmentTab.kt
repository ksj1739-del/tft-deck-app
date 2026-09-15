package com.tftdeck.reader.ui.codex

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.tftdeck.reader.data.AugmentEditorTierMeta
import com.tftdeck.reader.data.AugmentRow
import com.tftdeck.reader.data.AugmentsMeta
import com.tftdeck.reader.data.StatsState
import com.tftdeck.reader.ui.components.EmptyState

/**
 * 증강 탭: '티어'(편집자 S~D 격자)와 '목록'(희귀도·태그·검색 + 2줄 카드).
 * 증강 자체의 평균 등수 순위는 어느 원천에도 없어서 만들지 않는다. 그 한계를 맨 위에 고지한다.
 */
@Composable
fun AugmentTab(viewModel: CodexViewModel, onOpenAugment: (String) -> Unit) {
    val state by viewModel.state.collectAsState()
    val subTab by viewModel.augmentSubTab.collectAsState()
    val ready = state as? StatsState.Ready ?: return

    Column(Modifier.fillMaxSize()) {
        NoticeLine(augmentNotice(ready.augments.meta))
        CodexSubTabs(listOf("티어", "목록"), subTab, viewModel::setAugmentSubTab)
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (subTab == 1) {
                AugmentList(viewModel, onOpenAugment)
            } else {
                AugmentTierView(viewModel, ready, onOpenAugment)
            }
        }
    }
}

/** '증강 성적은 중국 서버 골드~에메랄드 하루치(덱별 상위 5개)만, 티어는 편집자 의견'. */
internal fun augmentNotice(meta: AugmentsMeta): String {
    // 라벨이 "중국 골드~에메랄드"라 "중국 서버 중국 …"으로 겹치지 않게 앞의 "중국"을 뗀다.
    val bucket = meta.cnStats.label.trim().removePrefix("중국").trim().ifBlank { "골드~에메랄드" }
    return "증강 성적은 중국 서버 $bucket 하루치(덱별 상위 5개)만, 티어는 편집자 의견"
}

/** "metatft META Spencer · 9월 15일 갱신". */
internal fun editorSourceText(meta: AugmentEditorTierMeta): String {
    val who = listOf(meta.source, meta.author).filter { it.isNotBlank() }.joinToString(" ")
    val whenText = meta.updatedAt.takeIf { it.isNotBlank() }?.let { "${formatIsoDay(it)} 갱신" }
    return listOfNotNull(who.takeIf { it.isNotBlank() }, whenText).joinToString(" · ").ifBlank { "편집자 티어" }
}

/** "평균 3.21 · n=2,280 · 덱 4". */
internal fun augmentSummaryText(augment: AugmentRow): String {
    val summary = augment.summary
    return if (summary.n <= 0) {
        "중국 통계 없음"
    } else {
        "평균 ${formatAvg(summary.avg)} · n=${formatCount(summary.n)} · 덱 ${summary.decks}"
    }
}

@Composable
private fun AugmentTierView(viewModel: CodexViewModel, ready: StatsState.Ready, onOpenAugment: (String) -> Unit) {
    val groups by viewModel.augmentTierGroups.collectAsState()
    val assetBase by viewModel.assetBase.collectAsState()
    val current = groups
    val untiered = remember(ready) { CodexQuery.untieredCount(ready.augments) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = CodexHPad, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(key = "source") {
            Text(
                "출처: ${editorSourceText(ready.augments.meta.editorTier)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            current == null -> item(key = "loading") { ListLoading() }
            current.isEmpty() -> item(key = "empty") {
                EmptyState(title = "에디터 티어가 없습니다", detail = "목록 탭에서 모든 증강을 볼 수 있습니다.")
            }
            else -> items(current, key = { "tier-${it.tier}" }) { group ->
                TierGroup(group, assetBase, onOpenAugment)
            }
        }
        if (untiered > 0) {
            item(key = "untiered") {
                Text(
                    "티어가 매겨지지 않은 증강 ${untiered}개는 목록 탭에서 볼 수 있습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TierGroup(group: AugmentTierGroup, assetBase: String, onOpenAugment: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GradeBadge(group.tier, large = true)
            Spacer(Modifier.width(8.dp))
            Text(
                "${group.augments.size}개",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = if (maxWidth < 330.dp) 3 else 4
            val gap = 8.dp
            // 칸 폭 합이 줄 폭을 반올림으로 넘으면 FlowRow가 줄을 바꿔 버리므로 여유를 조금 뺀다.
            val cellWidth = (maxWidth - gap * (columns - 1)) / columns - 1.dp
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                maxItemsInEachRow = columns,
            ) {
                group.augments.forEach { augment ->
                    AugmentTile(augment, assetBase, Modifier.width(cellWidth)) { onOpenAugment(augment.id) }
                }
            }
        }
    }
}

@Composable
private fun AugmentTile(augment: AugmentRow, assetBase: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CodexIcon(
            codexIconUrl(assetBase, augment.icon),
            augment.name,
            36.dp,
            corner = 8.dp,
            borderColor = rarityColor(augment.rarity),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            augment.name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AugmentList(viewModel: CodexViewModel, onOpenAugment: (String) -> Unit) {
    val rows by viewModel.augmentRows.collectAsState()
    val filter by viewModel.augmentFilter.collectAsState()
    val assetBase by viewModel.assetBase.collectAsState()
    val current = rows

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "filters", contentType = "filters") { AugmentFilters(filter, viewModel) }
        when {
            current == null -> item(key = "loading") { ListLoading() }
            current.isEmpty() -> item(key = "empty") {
                EmptyState(title = "조건에 맞는 증강이 없습니다", detail = "희귀도·태그 필터를 줄이거나 검색어를 바꿔 보세요.")
            }
            else -> items(current, key = { it.id }, contentType = { "augment" }) { augment ->
                AugmentCard(augment, assetBase) { onOpenAugment(augment.id) }
            }
        }
    }
}

@Composable
private fun AugmentFilters(filter: AugmentFilter, viewModel: CodexViewModel) {
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
            FilterLabel("희귀도")
            RARITY_KEYS.forEach { rarity ->
                FilterChip(
                    selected = filter.rarity == rarity,
                    onClick = { viewModel.setAugmentRarity(if (filter.rarity == rarity) null else rarity) },
                    label = { Text(rarityLabel(rarity)) },
                    leadingIcon = { ColorDot(rarityColor(rarity)) },
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
            FilterLabel("태그")
            TAG_KEYS.forEach { tag ->
                FilterChip(
                    selected = filter.tag == tag,
                    onClick = { viewModel.setAugmentTag(if (filter.tag == tag) null else tag) },
                    label = { Text(tagLabel(tag)) },
                )
            }
        }
        CodexSearchField(
            value = filter.query,
            onValueChange = viewModel::setAugmentQuery,
            placeholder = "이름·설명 검색",
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        )
    }
}

/** 2줄 카드: 1줄 아이콘·이름·희귀도·태그, 2줄 에디터 티어·중국 통계 요약. */
@Composable
private fun AugmentCard(augment: AugmentRow, assetBase: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = CodexHPad, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CodexIcon(
            codexIconUrl(assetBase, augment.icon),
            augment.name,
            36.dp,
            corner = 8.dp,
            borderColor = rarityColor(augment.rarity),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // 한 줄에 이름·희귀도·태그를 넣고, 폭이 모자라면 뒤(태그)부터 말줄임한다.
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = scheme.onSurface)) {
                        append(augment.name)
                    }
                    val rarity = rarityLabel(augment.rarity)
                    if (rarity.isNotBlank()) {
                        append("  ")
                        withStyle(SpanStyle(color = rarityColor(augment.rarity))) { append("● ") }
                        withStyle(SpanStyle(color = scheme.onSurfaceVariant)) { append(rarity) }
                    }
                    if (augment.tags.isNotEmpty()) {
                        withStyle(SpanStyle(color = scheme.onSurfaceVariant)) {
                            append(" · " + augment.tags.joinToString(" · ", transform = ::tagLabel))
                        }
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("에디터", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                GradeBadge(augment.editorTier)
                Spacer(Modifier.width(8.dp))
                Text(
                    augmentSummaryText(augment),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
