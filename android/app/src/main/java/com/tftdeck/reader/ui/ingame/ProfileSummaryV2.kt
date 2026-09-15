package com.tftdeck.reader.ui.ingame

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.tftdeck.reader.ui.theme.FloaColors
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tftdeck.reader.data.PlayerProfile
import com.tftdeck.reader.data.formatLpDelta
import com.tftdeck.reader.ui.relativeTime
import com.tftdeck.reader.ui.screens.placementTint
import java.util.Locale

/**
 * 내 정보 탭의 전적 카드. 오버레이 티어 카드와 같은 정보를 앱 화면 크기로, 글자는 12sp 이상으로 보여 준다.
 *
 * TOP4·1등 비율은 metatft가 주는 최근 경기(최대 40판)로만 계산할 수 있어서 '최근 N판'이라고 밝힌다.
 * 시즌 전체 판 수는 ranked.num_games라 따로 적는다.
 */
@Composable
fun ProfileSummaryV2(
    profile: PlayerProfile?,
    error: String?,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme

    if (profile == null) {
        Text(
            error ?: "아직 조회된 기록이 없습니다",
            style = MaterialTheme.typography.bodySmall,
            color = if (error != null) scheme.error else scheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 티어와 평균 등수
        Row(verticalAlignment = Alignment.CenterVertically) {
            profile.emblemUrl?.let { url ->
                AsyncImage(model = url, contentDescription = null, modifier = Modifier.size(44.dp))
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(profile.riotId, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Text(
                    listOf(profile.tier.ifBlank { "언랭크" }, profile.lp).filter { it.isNotBlank() }.joinToString(" "),
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                val rankLine = listOfNotNull(profile.serverRankText, profile.percentileText).joinToString(" · ")
                if (rankLine.isNotBlank()) {
                    Text(rankLine, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    profile.averageText,
                    style = MaterialTheme.typography.headlineSmall,
                    color = placementTint(profile.averagePlacement.toInt().coerceIn(1, 8)),
                    fontWeight = FontWeight.Bold,
                )
                Text("평균 등수", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }

        // 최근 8판(왼쪽이 가장 최근)과 그 판의 LP 변화
        if (profile.recentPlacements.isNotEmpty()) {
            val deltas = profile.recentPlacements.indices.map { profile.recentLpChanges.getOrNull(it) }
            val hasDelta = deltas.any { it != null }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                profile.recentPlacements.forEachIndexed { index, place ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .widthIn(max = 34.dp)
                                .fillMaxWidth()
                                .height(28.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(placementTint(place).copy(alpha = if (place <= 4) 0.24f else 0.13f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                place.toString(),
                                color = placementTint(place),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (place == 1) FontWeight.Bold else FontWeight.Medium,
                            )
                        }
                        if (hasDelta) {
                            val delta = deltas[index]
                            Text(
                                delta?.let { formatLpDelta(it) } ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = lpColor(delta, scheme.onSurfaceVariant),
                                maxLines = 1,
                            )
                        }
                    }
                }
                // 판이 8개보다 적으면 칩 크기가 커지지 않게 빈 칸으로 채운다.
                repeat((RECENT_SLOTS - profile.recentPlacements.size).coerceAtLeast(0)) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }

        // 판 수·비율
        Text(
            buildString {
                if (profile.seasonGames > 0) append("${profile.seasonGames}판 · ")
                append("최근 ${profile.games}판 TOP4 ${profile.top4Text} · 1등 ${profile.firstPlaces}회")
                if (profile.peak.isNotBlank()) append(" · 최고 ${profile.peak}")
            },
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )

        // 현재 패치 성적. 목록이 40판에서 잘렸으면 판 수는 '이상'이다.
        profile.currentPatch?.let { patch ->
            val saturated = profile.matchesTruncated && profile.currentPatchGames >= profile.games
            val count = "${profile.currentPatchGames}판" + if (saturated) " 이상" else ""
            Text(
                "현재 패치 $patch: $count · 평균 ${String.format(Locale.US, "%.2f", profile.currentPatchAvg)}등",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }

        // 갱신 시각과 버튼
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "갱신 ${relativeTime(profile.fetchedAt.takeIf { it > 0 })}",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRefresh, enabled = !refreshing) {
                if (refreshing) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text("갱신")
            }
        }

        error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.error)
        }
    }
}

/** 오른 판은 초록, 내린 판은 빨강. 밝은·어두운 테마 양쪽에서 읽히는 중간 톤으로 둔다. */
private fun lpColor(delta: Int?, neutral: Color): Color = when {
    delta == null || delta == 0 -> neutral
    delta > 0 -> FloaColors.Positive
    else -> FloaColors.Negative
}

private const val RECENT_SLOTS = 8
