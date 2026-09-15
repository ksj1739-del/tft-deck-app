package com.tftdeck.reader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tftdeck.reader.data.DeckFeed
import com.tftdeck.reader.data.DeckSortMode
import com.tftdeck.reader.data.FeedState
import com.tftdeck.reader.ui.AppViewModel
import com.tftdeck.reader.ui.components.BucketChips
import com.tftdeck.reader.ui.components.DeckCardV2
import com.tftdeck.reader.ui.components.EmptyState
import com.tftdeck.reader.ui.formatShortDate
import com.tftdeck.reader.ui.iconUrl
import com.tftdeck.reader.ui.relativeTime

@Composable
fun DeckListScreen(
    viewModel: AppViewModel,
    onOpenDeck: (String) -> Unit,
    onOpenVariant: (deckId: String, variantId: String) -> Unit = { deckId, _ -> onOpenDeck(deckId) },
) {
    val state by viewModel.feedState.collectAsState()
    val decks by viewModel.decks.collectAsState()
    val searchReady by viewModel.searchReady.collectAsState()
    val assetBase by viewModel.assetBase.collectAsState()
    val bucket by viewModel.bucket.collectAsState()
    val pinned by viewModel.pinnedSet.collectAsState()
    val hidden by viewModel.hiddenSet.collectAsState()

    when (state) {
        is FeedState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator()
        }

        is FeedState.Error -> EmptyState(
            title = "덱을 불러오지 못했습니다",
            detail = (state as FeedState.Error).message,
        )

        is FeedState.Ready -> {
            val ready = state as FeedState.Ready
            val feed = ready.feed
            Column(Modifier.fillMaxSize()) {
                FeedBanner(ready)

                if (decks.isEmpty() && !searchReady) {
                    // 아직 인덱스를 만드는 중이다. 필터 때문이라고 안내하면 오해를 부른다.
                    Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                    return@Column
                }

                // 구간·정렬·필터 줄은 목록과 함께 스크롤된다. 고정해 두면 폰에서 카드가 한 장 반밖에 안 보인다.
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    item(key = "controls") {
                        Column(
                            Modifier.padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            if (feed.buckets.isNotEmpty()) {
                                BucketChips(
                                    buckets = feed.buckets,
                                    selected = bucket,
                                    onSelect = viewModel::setBucket,
                                    horizontalPadding = 14.dp,
                                )
                                SortBar(viewModel)
                            }
                            FilterBar(viewModel, feed, assetBase)
                        }
                    }

                    if (decks.isEmpty()) {
                        item(key = "empty") {
                            EmptyState(
                                title = "조건에 맞는 덱이 없습니다",
                                detail = "필터를 줄이면 더 많은 덱이 보입니다.",
                            )
                        }
                    }

                    items(decks, key = { it.id }) { deck ->
                        DeckCardV2(
                            deck = deck,
                            bucket = bucket,
                            assetBase = assetBase,
                            onClick = { onOpenDeck(deck.id) },
                            modifier = Modifier.padding(horizontal = 14.dp),
                            buckets = feed.buckets,
                            metatftCompared = feed.version.metatftCompared,
                            pinned = deck.id in pinned,
                            hidden = deck.id in hidden,
                            onTogglePinned = { viewModel.togglePinned(deck.id) },
                            onToggleHidden = { viewModel.toggleHidden(deck.id) },
                            unitInfo = viewModel::unitEntry,
                            onOpenVariant = { variantId -> onOpenVariant(deck.id, variantId) },
                        )
                    }
                }
            }
        }
    }
}

/** 데이터가 언제 것인지, 온전한지 한 줄로 알려 준다. */
@Composable
private fun FeedBanner(state: FeedState.Ready) {
    val scheme = MaterialTheme.colorScheme
    val version = state.feed.version

    val message = when {
        state.fromBundle -> "앱에 포함된 초기 데이터입니다. 아직 갱신되지 않았습니다."
        !version.metatftCompared -> "metatft 대조를 하지 못해 '중국 한정' 표시가 빠져 있습니다."
        version.hasDegradedSource -> "일부 원본을 받지 못했습니다. 내용이 완전하지 않을 수 있습니다."
        else -> null
    }

    val summary = buildString {
        append("패치 ${version.patch}")
        if (version.patchGlobal.isNotBlank()) append("(글로벌 ${version.patchGlobal})")
        append(" · 덱 ${version.deckCount}")
        append(" · 중국 한정 ${version.onlyInChinaCount}")
        formatShortDate(version.statDate).takeIf { it.isNotBlank() }?.let { append(" · 기준일 $it") }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .background(if (message == null) scheme.surface else scheme.secondaryContainer)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message ?: summary,
            style = MaterialTheme.typography.labelMedium,
            color = if (message == null) scheme.onSurfaceVariant else scheme.onSecondaryContainer,
            modifier = Modifier.weight(1f),
        )
        if (message == null) {
            Spacer(Modifier.width(8.dp))
            Text(
                relativeTime(state.lastSyncedAt),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SortBar(viewModel: AppViewModel) {
    val sortMode by viewModel.sortMode.collectAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Sort,
            contentDescription = "정렬",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        DeckSortMode.entries.forEach { mode ->
            FilterChip(
                selected = mode == sortMode,
                onClick = { viewModel.setSortMode(mode) },
                label = { Text(mode.label) },
            )
        }
    }
}

@Composable
private fun FilterBar(viewModel: AppViewModel, feed: DeckFeed, assetBase: String) {
    val tiers by viewModel.availableTiers.collectAsState()
    val tierFilter by viewModel.tierFilter.collectAsState()
    val levels by viewModel.availableLevels.collectAsState()
    val levelFilter by viewModel.levelFilter.collectAsState()
    val onlyChina by viewModel.onlyChina.collectAsState()
    val editorialOnly by viewModel.editorialOnly.collectAsState()
    val mainTraits by viewModel.availableMainTraits.collectAsState()
    val mainTrait by viewModel.mainTraitFilter.collectAsState()
    val hidden by viewModel.hiddenSet.collectAsState()
    val showHidden by viewModel.showHidden.collectAsState()
    val hasFilter by viewModel.hasActiveFilter.collectAsState()

    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hasFilter) {
            FilterChip(
                selected = false,
                onClick = viewModel::clearFilters,
                label = { Text("초기화") },
                leadingIcon = {
                    Icon(Icons.Default.FilterAltOff, null, Modifier.size(15.dp))
                },
            )
        }

        FilterChip(
            selected = onlyChina,
            onClick = viewModel::toggleOnlyChina,
            label = { Text("중국 한정") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        )

        // 구간이 있는 v2 피드에서는 편집 덱 여부가 의미 있는 구분이다. v1은 전부 편집 덱이라 뺀다.
        if (feed.buckets.isNotEmpty()) {
            FilterChip(
                selected = editorialOnly,
                onClick = viewModel::toggleEditorialOnly,
                label = { Text("편집 덱만") },
            )
        } else {
            // v1 피드는 통계 등급이 없어 편집 등급 칩으로 거른다(예전 화면과 같은 동작).
            tiers.forEach { tier ->
                FilterChip(
                    selected = tier in tierFilter,
                    onClick = { viewModel.toggleTier(tier) },
                    label = { Text(tier) },
                )
            }
        }

        mainTraits.forEach { trait ->
            FilterChip(
                selected = trait.id == mainTrait,
                onClick = { viewModel.toggleMainTrait(trait.id) },
                label = { Text(trait.name) },
                leadingIcon = trait.icon?.let { icon ->
                    {
                        AsyncImage(
                            model = iconUrl(assetBase, icon),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                },
            )
        }

        if (levels.isNotEmpty()) Spacer(Modifier.width(2.dp))

        levels.forEach { level ->
            FilterChip(
                selected = level in levelFilter,
                onClick = { viewModel.toggleLevel(level) },
                label = { Text("${level}렙") },
            )
        }

        // 숨긴 덱이 있을 때만 복구 칩을 보여 준다.
        if (hidden.isNotEmpty() || showHidden) {
            FilterChip(
                selected = showHidden,
                onClick = viewModel::toggleShowHidden,
                label = { Text("숨긴 덱 보기 ${hidden.size}") },
                leadingIcon = {
                    Icon(Icons.Default.VisibilityOff, null, Modifier.size(15.dp))
                },
            )
        }
    }
}
