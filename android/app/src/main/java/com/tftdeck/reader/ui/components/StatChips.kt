package com.tftdeck.reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tftdeck.reader.data.BucketMeta
import com.tftdeck.reader.data.Deck
import com.tftdeck.reader.data.DeckKeys
import com.tftdeck.reader.data.DeckStats
import com.tftdeck.reader.data.GlobalStats
import com.tftdeck.reader.ui.bucketLabel
import com.tftdeck.reader.ui.formatAvg
import com.tftdeck.reader.ui.formatCount
import com.tftdeck.reader.ui.formatPct
import com.tftdeck.reader.ui.formatShortDate
import com.tftdeck.reader.ui.scopeLabel
import com.tftdeck.reader.ui.trendColor
import com.tftdeck.reader.ui.trendGlyph

/**
 * 고정 4수치: 평균 등수 / 픽률 / 승률 / TOP4.
 * 모든 카드·상세·오버레이에서 같은 자리·같은 순서라 눈이 매번 같은 곳을 본다. 값이 없으면 '-'.
 *
 * 오버레이는 앱 테마 밖에서 그려지므로 색을 넘겨받는다. [compact] 는 오버레이용 작은 글자.
 */
@Composable
fun StatsRow(
    stats: DeckStats?,
    modifier: Modifier = Modifier,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    compact: Boolean = false,
) {
    val cells = listOf(
        "평균 등수" to formatAvg(stats?.avg),
        "픽률" to formatPick(stats?.pick),
        "승률" to formatPct(stats?.win),
        "TOP4" to formatPct(stats?.top4),
    )
    Row(modifier.fillMaxWidth()) {
        cells.forEach { (label, value) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    value,
                    color = valueColor,
                    fontSize = if (compact) 11.sp else 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    label,
                    color = labelColor,
                    fontSize = if (compact) 9.sp else 10.5.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

/** 픽률은 1%도 안 되는 값이 대부분이라 그때는 소수 둘째 자리까지 보여 준다. */
fun formatPick(pick: Double?): String =
    if (pick != null && pick < 0.01) formatPct(pick, digits = 2) else formatPct(pick)

/**
 * 표본 라벨: "n=17,059 · 골드~에메랄드 · 9/15 · KR 플래+ 4.20등 n=9,849".
 * 표본 크기를 항상 같이 보여 줘야 소표본 수치를 과신하지 않는다.
 */
fun sampleText(deck: Deck, bucket: String, buckets: Map<String, BucketMeta>): String {
    val parts = mutableListOf<String>()
    deck.statsFor(bucket)?.let { stats ->
        val meta = buckets[bucket]
        parts += "n=${formatCount(stats.n)}"
        parts += meta?.label?.takeIf { it.isNotBlank() } ?: bucketLabel(bucket)
        formatShortDate(meta?.listDate).takeIf { it.isNotBlank() }?.let { parts += it }
    }
    // metatft 전용 덱은 네 수치가 글로벌 플래+ 값이다(등급도 이 값으로 매겼다). lol.qq 덱의 'n · 구간' 자리에 출처를 적는다.
    val displayScope = if (deck.isGlobalOnly) deck.globalDisplayScope else null
    displayScope?.let { scope ->
        deck.global?.stats?.get(scope)?.let { stat ->
            parts += "n=${formatCount(stat.n)}"
            parts += scopeLabel(scope, short = true)
        }
    }
    deck.global?.let { global ->
        // 곁들이는 참고 수치(KR 플래+ 먼저). 네 수치와 같은 출처면 되풀이하지 않는다.
        val scope = listOf(DeckKeys.SCOPE_KR_PLAT, DeckKeys.SCOPE_GLOBAL_PLAT)
            .firstOrNull { it in global.stats && it != displayScope }
        if (scope != null) {
            val stat = global.stats.getValue(scope)
            parts += "${scopeLabel(scope, short = true)} ${formatAvg(stat.avg)}등 n=${formatCount(stat.n)}"
        }
    }
    return parts.joinToString(" · ")
}

@Composable
fun SampleLabel(
    deck: Deck,
    bucket: String,
    buckets: Map<String, BucketMeta>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val text = sampleText(deck, bucket, buckets)
    if (text.isBlank()) return
    Text(text, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 2, modifier = modifier)
}

/** 운영 방식·난이도(metatft 기준). 글로벌 매칭이 없으면 비어 있다. */
fun opsTexts(global: GlobalStats?): List<String> = listOfNotNull(
    global?.levelling?.takeIf { it.isNotBlank() },
    global?.difficulty?.takeIf { it.isNotBlank() },
)

@Composable
fun OpsChip(text: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .clip(RoundedCornerShape(5.dp))
            .background(scheme.surfaceVariant)
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, maxLines = 1)
    }
}

/** 출처 배지 종류. 표시 순서도 이 순서다. */
enum class SourceBadge { EDITORIAL, STALE, CHINA_ONLY, KR }

/**
 * 덱에 붙일 출처 배지.
 * metatft 대조를 못 한 날은 '중국 한정'을 믿을 수 없으므로 붙이지 않는다.
 */
fun sourceBadges(deck: Deck, metatftCompared: Boolean): List<SourceBadge> = buildList {
    if (deck.hasEditorial) add(SourceBadge.EDITORIAL)
    if (deck.hasEditorial && deck.isEditorialStale) add(SourceBadge.STALE)
    if (metatftCompared && deck.isOnlyInChina) add(SourceBadge.CHINA_ONLY)
    if (deck.hasKrSample) add(SourceBadge.KR)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SourceBadges(deck: Deck, metatftCompared: Boolean, modifier: Modifier = Modifier) {
    val badges = sourceBadges(deck, metatftCompared)
    if (badges.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        badges.forEach { badge ->
            when (badge) {
                SourceBadge.EDITORIAL -> OutlineBadge("편집", scheme.primary)
                SourceBadge.STALE -> OutlineBadge("이전 패치", scheme.onSurfaceVariant)
                SourceBadge.CHINA_ONLY -> OnlyInChinaBadge()
                SourceBadge.KR -> OutlineBadge("KR", KrCyan)
            }
        }
    }
}

/** 테두리만 있는 작은 배지. 통계 등급 배지(채움)와 헷갈리지 않게 비워 둔다. */
@Composable
fun OutlineBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier
            .clip(shape)
            .border(1.dp, color.copy(alpha = 0.7f), shape)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(text, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun TrendGlyph(trend: String?, modifier: Modifier = Modifier) {
    val glyph = trendGlyph(trend)
    if (glyph.isEmpty()) return
    Text(glyph, color = trendColor(trend), style = MaterialTheme.typography.labelMedium, modifier = modifier)
}

/**
 * 구간 선택 칩(단일 선택). 다섯 구간이 JSON에 미리 들어 있어 바꿔도 네트워크를 쓰지 않는다.
 * 피드에 구간이 없으면(v1) 아무것도 그리지 않는다.
 */
@Composable
fun BucketChips(
    buckets: Map<String, BucketMeta>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 0.dp,
) {
    val keys = DeckKeys.BUCKET_ORDER.filter { it in buckets }
    if (keys.isEmpty()) return
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        keys.forEach { key ->
            FilterChip(
                selected = key == selected,
                onClick = { onSelect(key) },
                label = { Text(buckets[key]?.label?.takeIf { it.isNotBlank() } ?: bucketLabel(key)) },
            )
        }
    }
}

// 포인트 색(파랑)과 겹치지 않게 KR 출처는 청록으로 구분한다.
private val KrCyan = Color(0xFF22D3EE)
