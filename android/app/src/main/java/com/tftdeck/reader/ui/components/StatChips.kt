package com.tftdeck.reader.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import com.tftdeck.reader.ui.theme.FloaColors
import com.tftdeck.reader.ui.trendColor
import com.tftdeck.reader.ui.trendGlyph

/**
 * 수치 줄: 평균 등수 / 픽률 / 승률 / TOP4 를 같은 폭 칸에 값(위)·라벨(아래)로.
 * 상세·카드가 같은 순서라 눈이 매번 같은 곳을 본다. 값이 없으면 '-'. 숫자 표기는 [formatAvg]·[formatPct] 만 쓴다(MASTER 규칙 7).
 *
 * 값은 [valueStyle](기본 titleMedium 16sp, 상세는 titleLarge 20sp)을 SemiBold 로, 라벨은 labelSmall 11sp 로 쓴다.
 * [compact] 는 좁은 행(상세의 변형 행)용 작은 값(labelMedium 12sp)이다 — 규칙 1 의 11sp 아래로는 내리지 않는다.
 * 덱 카드는 L4 결정(2026-09-19)대로 [showWin] = false 로 승률 칸을 빼고(평균 등수·픽률·TOP4), 중국 한정 덱은
 * [pickAvailable] = false 로 픽률을 '–' 로 둔다 — 중국 픽률은 metatft 픽률과 정의가 달라 나란히 비교할 수 없다(V11·L14).
 * 오버레이처럼 앱 테마 밖에서 그리면 색을 넘겨받는다.
 */
@Composable
fun StatsRow(
    stats: DeckStats?,
    modifier: Modifier = Modifier,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    compact: Boolean = false,
    valueStyle: TextStyle = MaterialTheme.typography.titleMedium,
    showWin: Boolean = true,
    pickAvailable: Boolean = true,
) {
    val cells = statCells(stats, showWin = showWin, pickAvailable = pickAvailable)
    val valueText = (if (compact) MaterialTheme.typography.labelMedium else valueStyle).copy(fontWeight = FontWeight.SemiBold)
    val labelText = MaterialTheme.typography.labelSmall
    Row(modifier.fillMaxWidth()) {
        cells.forEach { (label, value) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(value, color = valueColor, style = valueText, maxLines = 1)
                Text(label, color = labelColor, style = labelText, maxLines = 1)
            }
        }
    }
}

/**
 * [StatsRow] 의 칸(라벨 to 값). 순서는 평균 등수 · 픽률 · 승률 · TOP4 로 고정하고, [showWin] 이 false 면 승률만 뺀다.
 * [pickAvailable] 이 false 면 픽률 값을 '–'(U+2013)로 둔다(값이 없어서가 아니라 비교할 수 없어서 비운다는 표시).
 */
internal fun statCells(
    stats: DeckStats?,
    showWin: Boolean = true,
    pickAvailable: Boolean = true,
): List<Pair<String, String>> = buildList {
    add("평균 등수" to formatAvg(stats?.avg))
    add("픽률" to if (pickAvailable) formatPick(stats?.pick) else PICK_NOT_COMPARABLE)
    if (showWin) add("승률" to formatPct(stats?.win))
    add("TOP4" to formatPct(stats?.top4))
}

/** 중국 한정 덱의 픽률 자리. */
internal const val PICK_NOT_COMPARABLE = "–"

/**
 * 픽률. 다른 비율과 같이 소수 1자리이고 0.1% 미만은 "<0.1%"다(L14). 예전에는 1% 미만을 소수 둘째 자리로 써
 * '0.03%' 가 '아무도 안 하는 덱'으로 읽혔다.
 */
fun formatPick(pick: Double?): String = formatPct(pick)

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
        if (deck.isMeta) {
            // metatft 조합 덱의 수치는 metatft 전 지역 3일치다. lol.qq 목록 기준일을 붙이면 출처가 섞여 보인다.
            parts += "metatft"
        } else {
            formatShortDate(meta?.listDate).takeIf { it.isNotBlank() }?.let { parts += it }
        }
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

@Deprecated("표본·출처 줄은 카드에서 뺐고(A2) 상세도 출처 시트로 옮긴다(A3). 용어 정리 단계(T)에서 지운다")
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
@Deprecated("카드는 운영을 설명 줄 하나로 합쳤다(A2, cardDescription). 용어 정리 단계(T)에서 지운다")
fun opsTexts(global: GlobalStats?): List<String> = listOfNotNull(
    global?.levelling?.takeIf { it.isNotBlank() },
    global?.difficulty?.takeIf { it.isNotBlank() },
)

@Deprecated("카드의 운영 칩 줄은 설명 줄과 겹쳐 뺐다(A2·L2). 용어 정리 단계(T)에서 지운다")
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

@Deprecated("출처 배지 줄은 카드에서 뺐다(A2·L3·V1). 중국 한정은 GradeBadge(Outlined) + TextBadge(\"중국\"). 용어 정리 단계(T)에서 지운다")
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SourceBadges(deck: Deck, metatftCompared: Boolean, modifier: Modifier = Modifier) {
    val badges = sourceBadges(deck, metatftCompared)
    if (badges.isEmpty()) return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // 글자 배지 한 모양(MASTER 규칙 5). KR 청록(KrCyan)은 색 뜻 규칙 3 에 어긋나 더는 쓰지 않는다.
        badges.forEach { badge ->
            when (badge) {
                SourceBadge.EDITORIAL -> TextBadge("편집", emphasis = true)
                SourceBadge.STALE -> TextBadge("이전 패치")
                SourceBadge.CHINA_ONLY -> TextBadge("중국")
                SourceBadge.KR -> TextBadge("KR")
            }
        }
    }
}

/**
 * 예전 테두리 배지. 테두리형은 이제 중국 한정 등급 배지만 쓰므로(MASTER 규칙 5) 글자 배지 한 모양으로 그린다.
 * [color] 가 primary(파랑)면 강조 글자('편집'), 그 밖의 색(예전 KR 청록 포함)은 보조 글자가 된다.
 */
@Deprecated("TextBadge 를 쓴다", ReplaceWith("TextBadge(text, modifier = modifier)"))
@Composable
fun OutlineBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    TextBadge(text, emphasis = color == FloaColors.Primary, modifier = modifier)
}

@Composable
fun TrendGlyph(trend: String?, modifier: Modifier = Modifier) {
    val glyph = trendGlyph(trend)
    if (glyph.isEmpty()) return
    Text(glyph, color = trendColor(trend), style = MaterialTheme.typography.labelMedium, modifier = modifier)
}

/**
 * 구간 선택 칩(단일 선택). 다섯 구간이 JSON에 미리 들어 있어 바꿔도 네트워크를 쓰지 않는다.
 * 피드에 구간이 없으면(v1) 아무것도 그리지 않는다. 선택 표시는 [FloaFilterChip] 한 모양(MASTER 규칙 6).
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        keys.forEach { key ->
            FloaFilterChip(
                selected = key == selected,
                label = buckets[key]?.label?.takeIf { it.isNotBlank() } ?: bucketLabel(key),
                onClick = { onSelect(key) },
            )
        }
    }
}

// 예전 KR 출처 배지 색. 색은 뜻 하나(MASTER 규칙 3)라 쓰지 않는다 — OutlineBadge 가 글자 배지로 그려 이 색은 화면에 나오지 않는다.
@Deprecated("KR 배지는 TextBadge 로 그린다. Wave 1 이 끝나면 지운다")
private val KrCyan = Color(0xFF22D3EE)
