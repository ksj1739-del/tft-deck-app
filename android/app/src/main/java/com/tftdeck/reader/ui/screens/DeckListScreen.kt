package com.tftdeck.reader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.FilterAltOff
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
import com.tftdeck.reader.data.FeedState
import com.tftdeck.reader.ui.AppViewModel
import com.tftdeck.reader.ui.components.DeckCard
import com.tftdeck.reader.ui.components.EmptyState
import com.tftdeck.reader.ui.components.ScreenPadding
import com.tftdeck.reader.ui.relativeTime

@Composable
fun DeckListScreen(
    viewModel: AppViewModel,
    onOpenDeck: (String) -> Unit,
) {
    val state by viewModel.feedState.collectAsState()
    val decks by viewModel.decks.collectAsState()
    val searchReady by viewModel.searchReady.collectAsState()
    val assetBase by viewModel.assetBase.collectAsState()

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
            Column(Modifier.fillMaxSize()) {
                FeedBanner(ready)
                FilterBar(viewModel)

                if (decks.isEmpty() && !searchReady) {
                    // 아직 인덱스를 만드는 중이다. 필터 때문이라고 안내하면 오해를 부른다.
                    Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                } else if (decks.isEmpty()) {
                    EmptyState(
                        title = "조건에 맞는 덱이 없습니다",
                        detail = "필터를 줄이면 더 많은 덱이 보입니다.",
                    )
                } else {
                    LazyColumn(
                        contentPadding = ScreenPadding,
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        items(decks, key = { it.id }) { deck ->
                            DeckCard(
                                deck = deck,
                                assetBase = assetBase,
                                onClick = { onOpenDeck(deck.id) },
                            )
                        }
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

    Row(
        Modifier
            .fillMaxWidth()
            .background(if (message == null) scheme.surface else scheme.secondaryContainer)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message ?: "패치 ${version.patch} · 덱 ${version.deckCount}개 · 중국 한정 ${version.onlyInChinaCount}개",
            style = MaterialTheme.typography.labelMedium,
            color = if (message == null) scheme.onSurfaceVariant else scheme.onSecondaryContainer,
            modifier = Modifier.weight(1f),
        )
        if (message == null) {
            Text(
                relativeTime(state.lastSyncedAt),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FilterBar(viewModel: AppViewModel) {
    val tiers by viewModel.availableTiers.collectAsState()
    val levels by viewModel.availableLevels.collectAsState()
    val tierFilter by viewModel.tierFilter.collectAsState()
    val levelFilter by viewModel.levelFilter.collectAsState()
    val onlyChina by viewModel.onlyChina.collectAsState()
    val hasFilter by viewModel.hasActiveFilter.collectAsState()

    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 6.dp),
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

        tiers.forEach { tier ->
            FilterChip(
                selected = tier in tierFilter,
                onClick = { viewModel.toggleTier(tier) },
                label = { Text(tier) },
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
    }
}
