package com.tftdeck.reader.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tftdeck.reader.data.PlayerProfile

/**
 * 내 티어와 최근 등수. 덱 패널 오른쪽에 붙는다.
 *
 * 한 판 하는 동안 흘깃 보는 용도라 숫자만 크게 두고 설명은 최소로 한다.
 */
@Composable
fun OverlayProfileCard(
    profile: PlayerProfile,
    stale: Boolean,
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
                    Text(profile.lp, color = ProfileMuted, fontSize = 9.5.sp)
                }
            }
        }

        // 최근 등수
        if (profile.recentPlacements.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                // 최근 경기가 앞에 오므로 뒤집어서 왼쪽이 과거, 오른쪽이 최근이 되게 한다.
                profile.recentPlacements.reversed().forEach { place ->
                    PlacementChip(place)
                }
            }
        }

        // 평균 등수 — 이 카드에서 제일 자주 보는 숫자
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = profile.averageText,
                color = placementColor(profile.averagePlacement.toInt().coerceIn(1, 8)),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(5.dp))
            Column(Modifier.padding(bottom = 2.dp)) {
                Text("평균 등수", color = ProfileMuted, fontSize = 8.5.sp)
                Text(
                    "${profile.games}판 · 톱4 ${profile.top4Text}",
                    color = ProfileMuted,
                    fontSize = 8.5.sp,
                )
            }
        }

        if (stale) {
            Text("갱신 실패 · 이전 기록", color = ProfileWarn, fontSize = 8.sp)
        }
    }
}

@Composable
private fun PlacementChip(place: Int) {
    val color = placementColor(place)
    Box(
        modifier = Modifier
            .size(width = 15.dp, height = 17.dp)
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

/** 1등은 금색, 톱4는 강조색, 그 아래는 차분하게. 색만 봐도 판이 어땠는지 읽힌다. */
private fun placementColor(place: Int): Color = when (place) {
    1 -> Color(0xFFE0B348)
    2, 3, 4 -> Color(0xFF4FC2A3)
    5, 6 -> Color(0xFF9AA8A4)
    else -> Color(0xFFE0736F)
}

private val ProfileScrim = Color(0xF20E1413)
private val ProfileBorder = Color(0x4D4FC2A3)
private val ProfileText = Color(0xFFE6EDEA)
private val ProfileMuted = Color(0xFF8FA29C)
private val ProfileAccent = Color(0xFF4FC2A3)
private val ProfileWarn = Color(0xFFD9A441)
