package com.tftdeck.reader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tftdeck.reader.data.Deck
import com.tftdeck.reader.data.FeedState
import com.tftdeck.reader.data.SearchAxis
import com.tftdeck.reader.data.Suggestion
import com.tftdeck.reader.ui.AppViewModel
import com.tftdeck.reader.ui.components.DeckCardV2
import com.tftdeck.reader.ui.components.EmptyState
import com.tftdeck.reader.ui.components.ScreenPadding
import com.tftdeck.reader.ui.costColor
import com.tftdeck.reader.ui.iconUrl

/** 도감 상세가 있는 검색 축. 조합 재료는 아이템 도감의 일부라 따로 버튼을 달지 않는다. */
private val CODEX_AXES = setOf(SearchAxis.CHAMPION, SearchAxis.TRAIT, SearchAxis.ITEM, SearchAxis.AUGMENT)

/**
 * 통합 검색.
 *
 * 아이템을 고르면 그 아이템이 들어가는 덱이 나온다 — 두 원본 사이트 어디에도 없는 기능이라
 * 결과를 '핵심'과 '대체'로 나눠 보여 준다. 후보 오른쪽의 '도감' 버튼은 그 항목의 통계 화면으로 간다.
 */
@Deprecated("결정 1: 검색 탭을 없애고 기능(핵심/대체 묶음·도감 버튼·예시·조건 밖 N개)을 덱 목록 검색 줄로 옮겼다(A2). 탭 제거는 A4, 파일 삭제는 T")
@Composable
fun SearchScreen(
    viewModel: AppViewModel,
    onOpenDeck: (String) -> Unit,
    onOpenCodex: (SearchAxis, String) -> Unit = { _, _ -> },
) {
    val query by viewModel.query.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val results by viewModel.results.collectAsState()
    val assetBase by viewModel.assetBase.collectAsState()

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            placeholder = { Text("챔피언 · 아이템 · 시너지 · 증강체") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = viewModel::clearSearch) {
                        Icon(Icons.Default.Close, contentDescription = "지우기")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
        )

        when {
            // 아직 아무것도 고르지 않았고 입력 중이면 후보를 보여 준다.
            selected == null && suggestions.isNotEmpty() ->
                SuggestionList(suggestions, assetBase, viewModel::select, onOpenCodex)

            selected == null && query.isNotBlank() -> EmptyState(
                title = "검색 결과가 없습니다",
                detail = "이름 일부, 초성(ㅁㅎㅇ), 줄임말(무대), 영문명으로 찾을 수 있습니다.",
            )

            selected == null -> SearchHints()

            results.isEmpty -> EmptyState(
                title = "해당하는 덱이 없습니다",
                detail = "다른 항목으로 검색해 보세요.",
            )

            else -> ResultList(viewModel, onOpenDeck, assetBase)
        }
    }
}

@Composable
private fun SuggestionList(
    suggestions: List<Suggestion>,
    assetBase: String,
    onPick: (Suggestion) -> Unit,
    onOpenCodex: (SearchAxis, String) -> Unit,
) {
    LazyColumn(contentPadding = ScreenPadding) {
        items(suggestions, key = { "${it.axis}:${it.name}" }) { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPick(item) }
                    .padding(start = 4.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = iconUrl(assetBase, item.icon),
                        contentDescription = null,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(5.dp)),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.bodyMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            item.axis.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        item.cost?.let { cost ->
                            Spacer(Modifier.width(5.dp))
                            Text(
                                "${cost}코스트",
                                style = MaterialTheme.typography.labelSmall,
                                color = costColor(cost),
                            )
                        }
                    }
                }
                Text(
                    "덱 ${item.deckCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val codexId = item.id?.takeIf { item.axis in CODEX_AXES }
                if (codexId != null) {
                    IconButton(onClick = { onOpenCodex(item.axis, codexId) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = "${item.name} 도감",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                } else {
                    // 버튼이 없는 줄도 오른쪽 끝이 같은 자리에서 끝나게 한다.
                    Spacer(Modifier.width(48.dp))
                }
            }
        }
    }
}

@Composable
private fun ResultList(
    viewModel: AppViewModel,
    onOpenDeck: (String) -> Unit,
    assetBase: String,
) {
    val selected by viewModel.selected.collectAsState()
    val results by viewModel.results.collectAsState()
    val state by viewModel.feedState.collectAsState()
    val bucket by viewModel.bucket.collectAsState()
    val pinned by viewModel.pinnedSet.collectAsState()
    val hidden by viewModel.hiddenSet.collectAsState()
    val feed = (state as? FeedState.Ready)?.feed
    val pick = selected ?: return

    val card: @Composable (Deck, String?) -> Unit = { deck, highlightUnit ->
        DeckCardV2(
            deck = deck,
            bucket = bucket,
            assetBase = assetBase,
            onClick = { onOpenDeck(deck.id) },
            buckets = feed?.buckets.orEmpty(),
            metatftCompared = feed?.version?.metatftCompared ?: true,
            pinned = deck.id in pinned,
            hidden = deck.id in hidden,
            onTogglePinned = { viewModel.togglePinned(deck.id) },
            onToggleHidden = { viewModel.toggleHidden(deck.id) },
            highlightUnit = highlightUnit,
            unitInfo = viewModel::unitEntry,
        )
    }

    LazyColumn(
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        if (pick.axis == SearchAxis.ITEM) {
            val core = results.itemHits.filter { it.isCore }
            val backup = results.itemHits.filterNot { it.isCore }

            if (core.isNotEmpty()) {
                item { SectionLabel("핵심 아이템으로 쓰는 덱 ${core.size}") }
                items(core, key = { "core-${it.deck.id}-${it.unitName}" }) { hit ->
                    card(hit.deck, hit.unitName)
                }
            }
            if (backup.isNotEmpty()) {
                item { SectionLabel("대체 아이템으로 쓰는 덱 ${backup.size}") }
                items(backup, key = { "backup-${it.deck.id}-${it.unitName}" }) { hit ->
                    card(hit.deck, hit.unitName)
                }
            }
        } else {
            item { SectionLabel("${pick.name}이(가) 들어가는 덱 ${results.decks.size}") }
            items(results.decks, key = { it.id }) { deck ->
                card(deck, null)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
    )
}

@Composable
private fun SearchHints() {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("이렇게 찾을 수 있습니다", style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
        listOf(
            "무한의 대검" to "그 아이템이 들어가는 덱 전부 — 핵심/대체로 구분",
            "곡궁" to "기본 아이템으로 갈 수 있는 덱 (지금 뭘 먹었는지로 검색)",
            "아리" to "그 챔피언을 쓰는 덱. 오른쪽 책 버튼은 도감으로 갑니다",
            "ㅁㅎㅇ" to "초성으로도 찾습니다",
            "무대" to "줄임말도 받습니다",
        ).forEach { (example, detail) ->
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(scheme.surfaceVariant)
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                ) {
                    Text(example, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
