package com.tftdeck.reader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tftdeck.reader.data.Deck
import com.tftdeck.reader.data.DeckKeys
import com.tftdeck.reader.data.FeedState
import com.tftdeck.reader.data.SearchAxis
import com.tftdeck.reader.ui.AppViewModel
import com.tftdeck.reader.ui.ListBanner
import com.tftdeck.reader.ui.components.DeckCardV2
import com.tftdeck.reader.ui.components.SectionTitle

/**
 * 덱 탭. 목록 위는 배너(문제가 있을 때만) + 두 줄 조작부([DeckListControls]), 그 아래 카드.
 * 셋 다 목록과 함께 스크롤된다 — 위에 고정해 두면 폰에서 카드가 한 장 반밖에 안 보인다.
 *
 * 검색 탭을 없애고(결정 1) 그 기능을 여기로 옮겼다: 검색 조건이 아이템 하나면 목록을 '핵심/대체'로 나누고 그 아이템을 드는
 * 챔피언을 카드에서 크게 보여 준다. 조건 밖(등급·구간)에 맞는 덱이 더 있으면 결과 위에 '조건 밖 N개 더'. 후보의 도감 단추는
 * [onOpenCodex] 가 있을 때만 붙는다(MainActivity 가 도감 라우트로 연결한다).
 * [onOpenVariant] 는 카드에서 변형 펼치기를 뺀 뒤 쓰지 않지만 호출부와 맞추려고 남겨 둔다.
 */
@Composable
fun DeckListScreen(
    viewModel: AppViewModel,
    onOpenDeck: (String) -> Unit,
    @Suppress("UNUSED_PARAMETER")
    onOpenVariant: (deckId: String, variantId: String) -> Unit = { deckId, _ -> onOpenDeck(deckId) },
    onOpenCodex: ((SearchAxis, String) -> Unit)? = null,
) {
    val state by viewModel.feedState.collectAsState()
    val list by viewModel.listState.collectAsState()
    val searchReady by viewModel.searchReady.collectAsState()
    val assetBase by viewModel.assetBase.collectAsState()
    val bucket by viewModel.bucket.collectAsState()
    val pinned by viewModel.pinnedSet.collectAsState()
    val hidden by viewModel.hiddenSet.collectAsState()
    val tokens by viewModel.listTokens.collectAsState()
    val grades by viewModel.gradeFilter.collectAsState()
    val hiddenByGrade by viewModel.hiddenByGradeCount.collectAsState()
    val banner by viewModel.bannerState.collectAsState()

    when (val current = state) {
        is FeedState.Loading -> SkeletonList()

        is FeedState.Error -> ErrorState(current.message, onRetry = viewModel::refresh)

        is FeedState.Ready -> {
            val feed = current.feed
            val card: @Composable (Deck, String?) -> Unit = { deck, highlight ->
                DeckCardV2(
                    deck = deck,
                    bucket = bucket,
                    assetBase = assetBase,
                    onClick = { onOpenDeck(deck.id) },
                    modifier = Modifier.padding(horizontal = SCREEN_GUTTER),
                    buckets = feed.buckets,
                    metatftCompared = feed.version.metatftCompared,
                    pinned = deck.id in pinned,
                    hidden = deck.id in hidden,
                    onTogglePinned = { viewModel.togglePinned(deck.id) },
                    onToggleHidden = { viewModel.toggleHidden(deck.id) },
                    highlightUnit = highlight,
                )
            }
            val off = DeckKeys.GRADE_FILTER_ALL.filter { it !in grades }

            LazyColumn(
                contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                banner?.let { shownBanner ->
                    item(key = "banner") { ListBannerRow(shownBanner, onClick = viewModel::refresh) }
                }
                item(key = "controls") {
                    DeckListControls(
                        viewModel = viewModel,
                        feed = feed,
                        assetBase = assetBase,
                        shown = list.decks.size,
                        total = list.total,
                        onOpenCodex = onOpenCodex,
                    )
                }

                if (!searchReady && list.decks.isEmpty()) {
                    // 아직 인덱스를 만드는 중이다. 필터 때문이라고 안내하면 오해를 부르니 카드 자리만 그린다(L16).
                    items(SKELETON_CARDS) { DeckCardSkeleton() }
                    return@LazyColumn
                }

                if (tokens.isNotEmpty() && list.outside > 0) {
                    item(key = "outside") {
                        val (message, action) = outsideLinkText(list.outside, list.showingOutside)
                        ActionLine(message, action, onClick = viewModel::toggleShowOutside)
                    }
                }

                if (list.decks.isEmpty()) {
                    item(key = "empty") {
                        EmptyList(
                            detail = emptyListDetail(tokens.isNotEmpty(), off, hiddenByGrade, list.outside),
                            onReset = viewModel::clearFilters,
                        )
                    }
                }

                val split = list.itemSplit
                if (split != null) {
                    // 아이템 역검색(검색 탭의 핵심 기능): 그 아이템을 핵심으로 쓰는 덱과 대체로만 쓰는 덱을 나눠 보인다.
                    if (split.core.isNotEmpty()) {
                        item(key = "core-title") { SplitTitle("${split.item} 핵심으로 쓰는 덱 ${split.core.size}") }
                        items(split.core, key = { "core-${it.deck.id}" }) { card(it.deck, it.unitName) }
                    }
                    if (split.backup.isNotEmpty()) {
                        item(key = "backup-title") { SplitTitle("${split.item} 대체로 쓰는 덱 ${split.backup.size}") }
                        items(split.backup, key = { "backup-${it.deck.id}" }) { card(it.deck, it.unitName) }
                    }
                } else {
                    items(list.decks, key = { it.id }) { deck -> card(deck, null) }
                }

                // 기본 상태(C·D 꺼짐)도 걸러진 상태라는 것을 목록 끝에서 알린다(N11). 누르면 꺼 둔 등급을 켠다.
                if (tokens.isEmpty() && hiddenByGrade > 0 && off.isNotEmpty()) {
                    item(key = "hidden-by-grade") {
                        ActionLine(hiddenByGradeText(off, hiddenByGrade), "보기", onClick = viewModel::showAllGrades)
                    }
                }
            }
        }
    }
}

@Composable
private fun SplitTitle(text: String) {
    SectionTitle(text, Modifier.padding(horizontal = SCREEN_GUTTER))
}

/** 목록 맨 위 배너. 문제가 있을 때만 뜨고, 받는 중이 아니면 줄 전체가 새로고침 단추다(N3). */
@Composable
private fun ListBannerRow(banner: ListBanner, onClick: () -> Unit) {
    val (message, action) = listBannerText(banner)
    val scheme = MaterialTheme.colorScheme
    val syncing = banner is ListBanner.Syncing
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = SCREEN_GUTTER)
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surfaceContainer)
            .clickable(enabled = !syncing, onClickLabel = action, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (syncing) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Filled.Refresh, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        action?.let {
            Spacer(Modifier.width(8.dp))
            Text(it, style = MaterialTheme.typography.labelLarge, color = scheme.primary, maxLines = 1)
        }
    }
}

/** 안내 한 줄 + 오른쪽 누름 글자(파랑). 줄 전체가 누름 영역(44dp). */
@Composable
private fun ActionLine(message: String, action: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = SCREEN_GUTTER)
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClickLabel = action, onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1)
    }
}

/** 빈 목록(L16·N20): 무엇이 막았는지 + [조건 초기화]. */
@Composable
private fun EmptyList(detail: String, onReset: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = SCREEN_GUTTER, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("조건에 맞는 덱이 없습니다", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onReset) { Text("조건 초기화") }
    }
}

/** 데이터를 못 읽었을 때. 받아 오면 풀릴 수 있어 [다시 시도]를 단다(N20). */
@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = SCREEN_GUTTER, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("덱을 불러오지 못했습니다", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onRetry) { Text("다시 시도") }
    }
}

/** 첫 로딩: 스피너 대신 카드 모양 자리 3장(L16·V16). */
@Composable
private fun SkeletonList() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = 8.dp)
            .semantics { contentDescription = "덱을 불러오는 중" },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(SKELETON_CARDS) { DeckCardSkeleton() }
    }
}

/** 카드와 같은 면·모서리·줄 배치의 빈 자리. 움직이지 않는다(게임 위 앱이라 반짝임을 쓰지 않는다). */
@Composable
private fun DeckCardSkeleton() {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = SCREEN_GUTTER)
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surfaceContainerHighest)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SkeletonBlock(20.dp, 20.dp)
            Spacer(Modifier.width(8.dp))
            SkeletonBlock(140.dp, 16.dp)
        }
        Spacer(Modifier.height(8.dp))
        SkeletonBlock(200.dp, 12.dp)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SkeletonBlock(40.dp, 40.dp)
            SkeletonBlock(40.dp, 40.dp)
            repeat(5) { SkeletonBlock(22.dp, 22.dp) }
        }
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(32.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(scheme.surfaceBright)
        )
    }
}

@Composable
private fun SkeletonBlock(width: Dp, height: Dp) {
    Box(
        Modifier
            .size(width, height)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceBright)
    )
}

private const val SKELETON_CARDS = 3
