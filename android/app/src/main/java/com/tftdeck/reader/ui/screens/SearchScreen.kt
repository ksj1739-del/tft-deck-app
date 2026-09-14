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
import com.tftdeck.reader.data.SearchAxis
import com.tftdeck.reader.data.Suggestion
import com.tftdeck.reader.ui.AppViewModel
import com.tftdeck.reader.ui.components.DeckCard
import com.tftdeck.reader.ui.components.EmptyState
import com.tftdeck.reader.ui.components.ScreenPadding
import com.tftdeck.reader.ui.costColor
import com.tftdeck.reader.ui.iconUrl

/**
 * 통합 검색.
 *
 * 아이템을 고르면 그 아이템이 들어가는 덱이 나온다 — 두 원본 사이트 어디에도 없는 기능이라
 * 결과를 '핵심'과 '대체'로 나눠 보여 준다.
 */
@Composable
fun SearchScreen(
    viewModel: AppViewModel,
    onOpenDeck: (String) -> Unit,
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
                SuggestionList(suggestions, assetBase, viewModel::select)

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
) {
    LazyColumn(contentPadding = ScreenPadding) {
        items(suggestions, key = { "${it.axis}:${it.name}" }) { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPick(item) }
                    .padding(horizontal = 4.dp, vertical = 9.dp),
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
    val pick = selected ?: return

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
                    DeckCard(
                        deck = hit.deck,
                        assetBase = assetBase,
                        onClick = { onOpenDeck(hit.deck.id) },
                        highlightUnit = hit.unitName,
                    )
                }
            }
            if (backup.isNotEmpty()) {
                item { SectionLabel("대체 아이템으로 쓰는 덱 ${backup.size}") }
                items(backup, key = { "backup-${it.deck.id}-${it.unitName}" }) { hit ->
                    DeckCard(
                        deck = hit.deck,
                        assetBase = assetBase,
                        onClick = { onOpenDeck(hit.deck.id) },
                        highlightUnit = hit.unitName,
                    )
                }
            }
        } else {
            item { SectionLabel("${pick.name}이(가) 들어가는 덱 ${results.decks.size}") }
            items(results.decks, key = { it.id }) { deck ->
                DeckCard(deck = deck, assetBase = assetBase, onClick = { onOpenDeck(deck.id) })
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
            "아리" to "그 챔피언을 쓰는 덱",
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
