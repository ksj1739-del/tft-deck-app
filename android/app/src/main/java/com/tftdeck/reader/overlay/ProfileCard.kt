package com.tftdeck.reader.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.tftdeck.reader.ui.theme.FloaColors
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tftdeck.reader.data.PlayerProfile
import com.tftdeck.reader.data.formatLpDelta
import java.util.Locale

/**
 * 내 티어와 최근 등수. 덱 패널 오른쪽에 붙는다.
 *
 * 한 판 하는 동안 흘깃 보는 용도라 숫자만 크게 두고 설명은 최소로 한다.
 * 폭은 OverlayContent가 124dp로 정한다(덱 패널 폭 계산에도 쓰여서 여기서 바꾸지 않는다).
 * 게임 화면 위에서도 읽히도록 글자는 오버레이 글자(OverlayType)만 쓰고 가장 작은 글자를 11sp 로 둔다. 바탕은 불투명.
 */
@Composable
fun OverlayProfileCard(
    profile: PlayerProfile,
    stale: Boolean,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ProfileScrim)
            .border(1.dp, ProfileBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 9.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // 소환사
        Row(verticalAlignment = Alignment.CenterVertically) {
            profile.iconUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .border(1.dp, ProfileBorder, CircleShape),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = profile.riotId.substringBefore('#'),
                color = ProfileText,
                style = OverlayType.label.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // 판이 끝난 직후 바로 확인할 수 있게 작게 둔다.
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .clickable(enabled = !refreshing, onClick = onRefresh),
                contentAlignment = Alignment.Center,
            ) {
                if (refreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(11.dp),
                        strokeWidth = 1.5.dp,
                        color = ProfileMuted,
                    )
                } else {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "전적 새로고침",
                        tint = ProfileMuted,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }

        // 티어
        Row(verticalAlignment = Alignment.CenterVertically) {
            profile.emblemUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                )
                Spacer(Modifier.width(5.dp))
            }
            Column {
                Text(
                    text = profile.tier.ifBlank { "언랭크" },
                    color = ProfileAccent,
                    style = OverlayType.title,
                    maxLines = 1,
                )
                if (profile.lp.isNotBlank()) {
                    Text(overlayLpText(profile.lp), color = ProfileMuted, style = OverlayType.label, maxLines = 1)
                }
            }
        }

        // 서버 백분위. 티어만으로는 같은 다이아몬드 안에서 어디쯤인지 모른다.
        profile.percentileText?.let { percentile ->
            Text(
                text = listOf(profile.region, percentile).filter { it.isNotBlank() }.joinToString(" "),
                color = ProfileMuted,
                style = OverlayType.label,
                maxLines = 1,
            )
        }

        // 최근 등수와 그 판의 LP 변화
        if (profile.recentPlacements.isNotEmpty()) {
            // 최근 경기가 앞에 온다 — 왼쪽이 가장 최근 판.
            // 카드 폭(안쪽 106dp)에 18dp 칩 5칸(간격 3dp, 102dp)이 들어간다. 설정 화면은 8판 전부.
            val shown = profile.recentPlacements.take(OVERLAY_RECENT_GAMES)
            val deltas = shown.indices.map { profile.recentLpChanges.getOrNull(it) }
            val hasDelta = deltas.any { it != null }
            Row(horizontalArrangement = Arrangement.spacedBy(CHIP_GAP)) {
                shown.forEachIndexed { index, place ->
                    Column(
                        modifier = Modifier.width(CHIP_WIDTH),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        PlacementChip(place)
                        if (hasDelta) LpDeltaText(deltas[index])
                    }
                }
            }
        }

        // 평균 등수 — 이 카드에서 제일 자주 보는 숫자
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = profile.averageText,
                    color = placementColor(profile.averagePlacement.toInt().coerceIn(1, 8)),
                    style = OverlayType.display,
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    "평균 등수",
                    color = ProfileMuted,
                    style = OverlayType.label,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
            Text(
                "${profile.games}판 · TOP4 ${profile.top4Text}",
                color = ProfileMuted,
                style = OverlayType.label,
                maxLines = 1,
            )
        }

        if (stale) {
            Text("갱신 실패 · 이전 기록", color = ProfileWarn, style = OverlayType.label)
        }
    }
}

/**
 * 티어 카드의 LP 글자: 천 단위 쉼표와 띄어쓰기('1,234 LP'). 마스터 이상은 LP 가 네 자리가 된다.
 * 숫자를 찾지 못하면 받은 글자 그대로 둔다.
 */
internal fun overlayLpText(raw: String): String {
    val number = LP_NUMBER.find(raw)?.value?.replace(",", "")?.toLongOrNull() ?: return raw.trim()
    return String.format(Locale.US, "%,d LP", number)
}

private val LP_NUMBER = Regex("""\d[\d,]*""")

@Composable
private fun PlacementChip(place: Int) {
    val color = placementColor(place)
    Box(
        modifier = Modifier
            .size(CHIP_WIDTH)
            .clip(RoundedCornerShape(3.dp))
            .background(color.copy(alpha = if (place <= 4) 0.30f else 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = place.toString(),
            color = color,
            style = if (place == 1) OverlayType.badge else OverlayType.label,
            maxLines = 1,
        )
    }
}

/**
 * 칩 아래 ±LP. "−35"는 11sp 에서 칩(18dp)과 거의 같은 폭이라, 칩 간격을 흔들지 않도록
 * 칸 폭은 그대로 두고 글자만 양옆으로 넘치게 그린다. 짝을 못 찾은 판은 비워 둔다.
 */
@Composable
private fun LpDeltaText(delta: Int?) {
    Text(
        text = delta?.let { formatLpDelta(it) } ?: "",
        color = when {
            delta == null || delta == 0 -> ProfileMuted
            delta > 0 -> ProfileGain
            else -> ProfileLoss
        },
        style = OverlayType.label,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.wrapContentWidth(align = Alignment.CenterHorizontally, unbounded = true),
    )
}

/** 1등은 금색, TOP4 는 초록, 그 아래는 차분하게. 색만 봐도 판이 어땠는지 읽힌다(숫자도 함께 쓴다). */
private fun placementColor(place: Int): Color = when (place) {
    1 -> FloaColors.Gold
    2, 3, 4 -> FloaColors.Positive
    5, 6 -> FloaColors.OnSurfaceVariant
    else -> FloaColors.Negative
}

/** 오버레이 카드에 보이는 최근 판 수. 18dp 칩 5칸이 카드 안쪽 폭에 맞는다. */
private const val OVERLAY_RECENT_GAMES = 5

/** 최근 등수 칩 한 칸(가로·세로 18dp)과 칩 사이 간격. */
private val CHIP_WIDTH = 18.dp
private val CHIP_GAP = 3.dp

/** 카드 바탕. 덱 패널과 같이 불투명 100%(R11·V28). */
private val ProfileScrim = FloaColors.Surface
private val ProfileBorder = FloaColors.Secondary.copy(alpha = 0.3f)
private val ProfileText = FloaColors.OnSurface
private val ProfileMuted = FloaColors.OnSurfaceVariant
private val ProfileAccent = FloaColors.Secondary
private val ProfileWarn = FloaColors.Gold
private val ProfileGain = FloaColors.Positive
private val ProfileLoss = FloaColors.Negative
