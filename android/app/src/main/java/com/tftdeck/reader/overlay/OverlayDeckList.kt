package com.tftdeck.reader.overlay

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tftdeck.reader.data.Deck
import com.tftdeck.reader.data.DeckPrefs
import com.tftdeck.reader.data.DeckRepository
import com.tftdeck.reader.data.DeckSearch
import com.tftdeck.reader.data.FeedState
import com.tftdeck.reader.data.Unit as DeckUnit
import com.tftdeck.reader.ui.components.GradeBadge
import com.tftdeck.reader.ui.components.GradeBadgeStyle
import com.tftdeck.reader.ui.components.TextBadge
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter

/**
 * 오버레이 덱 목록.
 *
 * 가로 화면(게임 중)과 세로 화면(앱 위)은 줄 모양이 다르다([landscape], 기본은 지금 화면 방향).
 *  - 가로: `[등급 배지][별칭][캐리 얼굴 2개 20dp]` / `설명 두 줄까지` — 한 줄 44~59dp, 목록 높이 상한 200dp 라
 *    약 4줄 + 스크롤로 게임 화면을 덜 가린다(R5·F9, 사용자 결정 4). 얼굴 전체 줄은 넓게 보기에서만 보인다.
 *  - 세로: 같은 첫 줄·설명 아래에 얼굴 전체 줄(24dp). 목록 높이 상한 300dp.
 *
 * [grades] 는 앱과 함께 쓰는 등급 조회 조건이다. 루트가 넘기지 않으면 직접 읽는다([rememberOverlayQuery]).
 * 고정해서 남은 덱 중 그 조건으로는 빠졌을 덱은 등급 배지를 흐리게 한다(Q14).
 */
@Composable
internal fun DeckListView(
    decks: List<Deck>,
    filterKey: Any,
    emptyMessage: String,
    bucket: String,
    pinned: Set<String>,
    metatftCompared: Boolean,
    assetBase: String,
    wide: Boolean,
    anchorFlow: StateFlow<OverlayListAnchor?>,
    onAnchor: (OverlayListAnchor?) -> Unit,
    modifier: Modifier = Modifier,
    landscape: Boolean = isOverlayLandscape(),
    grades: Set<String>? = null,
    onPick: (Deck) -> Unit,
) {
    val query = rememberOverlayQuery()
    val gradeCondition = grades ?: query.grades
    if (decks.isEmpty()) {
        // 무엇이 막았는지(꺼 둔 등급·검색 조건)와 푸는 곳을 말한다([overlayEmptyListMessage]).
        Text(
            emptyMessage,
            color = OverlaySubtext,
            style = OverlayType.body,
            modifier = modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        )
        return
    }
    // 목록 자리는 서비스가 기억한다 — 접었다 펴도, 덱을 봤다 돌아와도, 창이 다시 붙거나 서비스가 다시 떠도 보던 덱에서 이어진다.
    // 조건(검색 칩·등급, [filterKey])이 바뀌면 새로 만든다: 바꾸는 쪽이 기억한 자리를 먼저 지우므로 새 목록을 맨 위부터 보여 준다.
    // 기억한 자리는 흘려 받은 값이 아니라 지금 값(.value)으로 읽는다 — 지운 직후 다시 그릴 때 옛 값을 보지 않게.
    val listState = remember(filterKey) {
        val start = overlayListStart(decks.map { it.id }, anchorFlow.value)
        LazyListState(start.index, start.offset)
    }
    val reportAnchor by rememberUpdatedState(onAnchor)
    // 스크롤이 멈출 때마다 맨 위에 보이는 덱을 기억한다(움직이는 동안 프레임마다 저장하지 않는다).
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { moving -> !moving }
            .collect {
                val key = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.key as? String ?: return@collect
                reportAnchor(OverlayListAnchor(key, listState.firstVisibleItemScrollOffset))
            }
    }
    LazyColumn(
        state = listState,
        // 게임 화면을 너무 가리지 않도록 높이를 제한하고 나머지는 스크롤한다. 화면이 더 낮으면 남는 높이만 쓴다(modifier).
        modifier = modifier.heightIn(max = overlayDeckListMax(landscape)),
    ) {
        items(decks, key = { it.id }) { deck ->
            val isPinned = deck.id in pinned
            DeckRow(
                deck = deck,
                bucket = bucket,
                pinned = isPinned,
                offGrade = overlayPinnedOffGrade(deck, bucket, isPinned, gradeCondition),
                style = overlayGradeStyle(deck, bucket, metatftCompared),
                assetBase = assetBase,
                wide = wide,
                landscape = landscape,
                onClick = { onPick(deck) },
            )
        }
    }
}

/**
 * 목록 한 줄. 글만 읽어도 어떤 덱인지 알 수 있게 별칭(12sp 굵게)과 설명(12sp, 두 줄까지)을 둔다.
 *  - 첫 줄: [등급 배지(+ '중국')] 별칭 … [핀] [캐리 얼굴 2개(가로, 좁게)]
 *  - 설명: '빠른 8레벨 · 달빛 3 · 캐리 아이템' — 운영·대표 특성·캐리 아이템(수집기가 44자 안으로 싣는다).
 *  - 얼굴 전체 줄: 세로 화면, 또는 가로 화면의 넓게 보기에서만(24dp).
 * 별칭의 ' · …' 운영 접미사는 떼고 보여 준다 — 운영은 바로 아래 설명 첫머리에 있다(R6).
 * 평균 등수 같은 수치는 게임을 가려서 여전히 뺀다(앱의 덱 상세에서 본다).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeckRow(
    deck: Deck,
    bucket: String,
    pinned: Boolean,
    offGrade: Boolean,
    style: GradeBadgeStyle,
    assetBase: String,
    wide: Boolean,
    landscape: Boolean,
    onClick: () -> Unit,
) {
    val summary = deck.displaySummary
    val alias = remember(deck) { overlayRowAlias(deck.displayAlias) }
    val boardRow = !landscape || wide
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = ROW_PADDING),
        verticalArrangement = Arrangement.spacedBy(ROW_GAP),
    ) {
        Row(Modifier.heightIn(min = FIRST_LINE), verticalAlignment = Alignment.CenterVertically) {
            OverlayGradeBadges(deck, bucket, style, dim = offGrade)
            Spacer(Modifier.width(8.dp))
            Text(
                alias,
                color = OverlayText,
                style = OverlayType.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (pinned) {
                Spacer(Modifier.width(4.dp))
                PinMark()
            }
            if (!boardRow) {
                val carries = remember(deck) { overlayRowCarries(deck) }
                if (carries.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        carries.forEach { unit -> Face(unit, assetBase, ROW_CARRY_FACE) }
                    }
                }
            }
        }
        if (summary.isNotBlank()) {
            Text(
                summary,
                color = OverlaySubtext,
                style = OverlayType.body,
                maxLines = SUMMARY_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (boardRow) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(top = 1.dp),
            ) {
                deck.units.forEach { unit -> Face(unit, assetBase, BOARD_FACE) }
            }
        }
    }
}

/**
 * 등급 배지와 (중국 한정 덱이면) 뒤에 '중국' 글자 배지. 목록 줄과 요약 첫 줄이 같이 쓴다.
 * 모양은 [overlayGradeStyle] — 채움(metatft) / 테두리(중국 한정) / 테두리 + '편'(편집 등급). 예전 5dp 호박색 점은 뜻을 알 수 없었다(R2).
 * [dim]: 고정해서 남았지만 꺼 둔 등급의 덱 — 배지만 흐리게(0.45) 한다(Q14).
 */
@Composable
internal fun OverlayGradeBadges(deck: Deck, bucket: String, style: GradeBadgeStyle, dim: Boolean = false) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GradeBadge(
            deck.gradeFor(bucket).orEmpty(),
            style,
            if (dim) Modifier.alpha(OFF_GRADE_ALPHA) else Modifier,
        )
        if (style == GradeBadgeStyle.Outlined) {
            TextBadge("중국", textStyle = OverlayType.label)
        }
    }
}

/** 고정한 덱 표시. 줄 오른쪽(캐리 얼굴 앞)에 둔다. */
@Composable
private fun PinMark() {
    Icon(
        Icons.Filled.PushPin,
        contentDescription = "고정한 덱",
        tint = OverlayAccent,
        modifier = Modifier.size(PIN_ICON),
    )
}

/** 지금 화면이 가로인지(게임 중). 오버레이 창도 화면 회전 때 설정 변경을 받아 다시 그린다. */
@Composable
@ReadOnlyComposable
internal fun isOverlayLandscape(): Boolean =
    LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

/** 앱과 함께 쓰는 조회 조건 중 목록·요약이 쓰는 것. [grades] 는 등급 조건을 쓰지 않는 피드(구간 없음)면 null. */
internal data class OverlayQuery(val bucket: String, val grades: Set<String>?, val metatftCompared: Boolean)

/**
 * 루트(OverlayContent)가 값을 넘기지 않을 때 목록·요약이 직접 읽는 조회 조건. 루트와 같은 규칙이다:
 * 저장한 구간이 이번 피드에 없으면 피드 기본 구간, 등급 조건은 구간이 있는 피드(v2)에서만.
 * 같은 프로세스의 싱글턴이라 앱에서 조건을 바꾸면 여기도 곧바로 바뀐다.
 */
@Composable
internal fun rememberOverlayQuery(): OverlayQuery {
    val context = LocalContext.current
    val prefs = remember { DeckPrefs.get(context) }
    val repository = remember { DeckRepository.get(context) }
    val savedBucket by prefs.bucket.collectAsState()
    val grades by prefs.grades.collectAsState()
    val feedState by repository.state.collectAsState()
    val feed = (feedState as? FeedState.Ready)?.feed
    val buckets = feed?.buckets.orEmpty()
    val bucket = if (feed == null || buckets.isEmpty() || savedBucket in buckets) savedBucket else feed.defaultBucket
    return OverlayQuery(
        bucket = bucket,
        grades = grades.takeIf { buckets.isNotEmpty() },
        metatftCompared = feed?.version?.metatftCompared ?: true,
    )
}

/**
 * 목록 줄·요약 첫 줄의 별칭. 수집기가 같은 별칭을 가르려고 붙인 운영 접미사(' · 7레벨 리롤')는 뗀다 — 운영은 바로 아래
 * 설명 첫머리에 나온다(R6). 캐리 사이 '·' 는 띄어 쓰지 않아 남고, 번호 접미사(' 2')는 캐리 뒤에 붙어 있어 남는다.
 */
internal fun overlayRowAlias(alias: String): String =
    alias.substringBefore(" · ").trim().ifEmpty { alias.trim() }

/**
 * 가로 목록 줄 오른쪽의 캐리 얼굴 2개: 캐리 순위(메인C → 보조C) 앞 둘. 순위가 없는 옛 데이터는 [Deck.carries] 의 대체 규칙
 * (캐리 표시 → 아이템 많은 순)을 따른다. 소환물은 뺀다. 별칭 챔피언은 수집기 검사로 늘 이 둘 안에 있다.
 */
internal fun overlayRowCarries(deck: Deck): List<DeckUnit> =
    deck.carries.filterNot { it.isPet }.take(ROW_CARRIES)

/**
 * 고정해서 목록에 남았지만 등급 조회 조건으로는 빠졌을 덱인지(Q14). 목록 필터([overlayGradeFiltered])와 같은 규칙
 * ([DeckSearch.gradePasses], 그 구간 등급)으로 본다. [grades] 가 null 이면 등급 조건을 쓰지 않는 피드라 아니다.
 */
internal fun overlayPinnedOffGrade(deck: Deck, bucket: String, pinned: Boolean, grades: Set<String>?): Boolean =
    pinned && grades != null && !DeckSearch.gradePasses(deck.gradeFor(bucket), grades)

/** 목록 높이 상한. 가로(게임 중)는 약 4줄 + 스크롤(R5), 세로(앱 위)는 예전 그대로. */
internal fun overlayDeckListMax(landscape: Boolean): Dp = if (landscape) DECK_LIST_MAX_LANDSCAPE else DECK_LIST_MAX

/** 목록 줄 설명의 최대 줄 수(MASTER 오버레이 추가 규칙: 덱 설명 12sp 최대 2줄). 넘치면 말줄임으로 줄 높이를 지킨다. */
private const val SUMMARY_MAX_LINES = 2

/** 가로 목록 줄의 캐리 얼굴 수(사용자 결정 4). */
private const val ROW_CARRIES = 2

/**
 * 덱 목록 높이 상한(세로 화면). 예전 330dp 에서 검색 줄 높이만큼 뺐다.
 * 화면이 낮으면 패널 상한([PANEL_SCREEN_MARGIN])에 맞춰 이보다 줄어든다.
 */
private val DECK_LIST_MAX = 300.dp

/**
 * 덱 목록 높이 상한(가로 화면). 예전에는 가로에서도 300dp 라 머리줄·검색 줄과 함께 화면 높이의 85% 를 덮었다(R5).
 * 한 줄 44~59dp 라 약 4줄이 보이고 나머지는 스크롤한다.
 */
private val DECK_LIST_MAX_LANDSCAPE = 200.dp

/** 줄 위아래 여백과 첫 줄·설명 사이 틈. 첫 줄 20dp + 설명 한 줄 15dp 면 44dp, 두 줄이면 59dp. */
private val ROW_PADDING = 4.dp
private val ROW_GAP = 1.dp

/** 첫 줄 높이 — 등급 배지(20dp)와 캐리 얼굴(20dp)이 이 줄에 선다. */
private val FIRST_LINE = 20.dp

/** 가로 목록 줄의 캐리 얼굴. */
private val ROW_CARRY_FACE = 20.dp

/** 얼굴 전체 줄(세로 화면·넓게 보기)의 얼굴. 예전 22dp(좁게)에서 넓게 보기와 같은 24dp 로 맞췄다. */
private val BOARD_FACE = 24.dp

/** 핀 아이콘. 아이콘 크기는 16·20·24 만 쓴다(MASTER 규칙 4). */
private val PIN_ICON = 16.dp

/** 고정 때문에 남은 꺼진 등급 덱의 배지 불투명도(Q14). */
private const val OFF_GRADE_ALPHA = 0.45f
