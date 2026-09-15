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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tftdeck.reader.data.PlayerProfile
import com.tftdeck.reader.data.formatLpDelta

/**
 * 내 티어와 최근 등수. 덱 패널 오른쪽에 붙는다.
 *
 * 한 판 하는 동안 흘깃 보는 용도라 숫자만 크게 두고 설명은 최소로 한다.
 * 폭은 OverlayContent가 124dp로 정한다(덱 패널 폭 계산에도 쓰여서 여기서 바꾸지 않는다).
 * 게임 화면 위에서도 읽히도록 가장 작은 글자를 10sp로 둔다.
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
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
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
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                if (profile.lp.isNotBlank()) {
                    Text(profile.lp, color = ProfileMuted, fontSize = 10.5.sp, maxLines = 1)
                }
            }
        }

        // 서버 백분위. 티어만으로는 같은 다이아몬드 안에서 어디쯤인지 모른다.
        profile.percentileText?.let { percentile ->
            Text(
                text = listOf(profile.region, percentile).filter { it.isNotBlank() }.joinToString(" "),
                color = ProfileMuted,
                fontSize = 10.sp,
                maxLines = 1,
            )
        }

        // 최근 등수와 그 판의 LP 변화
        if (profile.recentPlacements.isNotEmpty()) {
            // 최근 경기가 앞에 온다 — 왼쪽이 가장 최근 판.
            // 카드 폭에 6칸까지 들어간다. 더 넣으면 오른쪽이 잘린다(설정 화면은 8판 전부).
            val shown = profile.recentPlacements.take(OVERLAY_RECENT_GAMES)
            val deltas = shown.indices.map { profile.recentLpChanges.getOrNull(it) }
            val hasDelta = deltas.any { it != null }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
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
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    "평균 등수",
                    color = ProfileMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
            Text(
                "${profile.games}판 · 톱4 ${profile.top4Text}",
                color = ProfileMuted,
                fontSize = 10.sp,
                maxLines = 1,
            )
        }

        if (stale) {
            Text("갱신 실패 · 이전 기록", color = ProfileWarn, fontSize = 10.sp)
        }
    }
}

@Composable
private fun PlacementChip(place: Int) {
    val color = placementColor(place)
    Box(
        modifier = Modifier
            .size(width = CHIP_WIDTH, height = 17.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(color.copy(alpha = if (place <= 4) 0.30f else 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = place.toString(),
            color = color,
            fontSize = 10.sp,
            fontWeight = if (place == 1) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

/**
 * 칩 아래 ±LP. "−35"는 10sp에서 칩(15dp)보다 살짝 넓어서, 칩 간격을 흔들지 않도록
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
        fontSize = 10.sp,
        letterSpacing = (-0.3).sp,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.wrapContentWidth(align = Alignment.CenterHorizontally, unbounded = true),
    )
}

/** 1등은 금색, 톱4는 강조색, 그 아래는 차분하게. 색만 봐도 판이 어땠는지 읽힌다. */
private fun placementColor(place: Int): Color = when (place) {
    1 -> FloaColors.Gold
    2, 3, 4 -> FloaColors.Positive
    5, 6 -> FloaColors.OnSurfaceVariant
    else -> FloaColors.Negative
}

private const val OVERLAY_RECENT_GAMES = 6
private val CHIP_WIDTH = 15.dp

private val ProfileScrim = FloaColors.Surface.copy(alpha = 0.95f)
private val ProfileBorder = FloaColors.Secondary.copy(alpha = 0.3f)
private val ProfileText = FloaColors.OnSurface
private val ProfileMuted = FloaColors.OnSurfaceVariant
private val ProfileAccent = FloaColors.Secondary
private val ProfileWarn = FloaColors.Gold
private val ProfileGain = FloaColors.Positive
private val ProfileLoss = FloaColors.Negative
