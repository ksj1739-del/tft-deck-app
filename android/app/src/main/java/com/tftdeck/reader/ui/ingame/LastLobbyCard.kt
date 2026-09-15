package com.tftdeck.reader.ui.ingame

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tftdeck.reader.ingame.LastLobby
import com.tftdeck.reader.ingame.LobbyPlayer
import com.tftdeck.reader.ui.formatDate
import com.tftdeck.reader.ui.relativeTime
import com.tftdeck.reader.ui.screens.placementTint

/**
 * 지난 게임 로비 8명. 게임이 끝난 뒤의 정보만 보여 준다(Riot 정책: 게임 중 상대 정보 표시 금지).
 * 좁은 폰에서도 라이엇 ID가 잘리지 않도록 티어는 이름 아래 줄로 내렸다.
 */
@Composable
fun LastLobbyCard(lobby: LastLobby?, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme

    if (lobby == null || lobby.players.isEmpty()) {
        Text(
            "게임이 끝나면 자동으로 채워집니다(게임 연동 필요)",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "${formatDate(lobby.endedAt)}에 끝난 게임 · ${relativeTime(lobby.endedAt)}",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(4.dp))
        lobby.players.forEach { player -> LobbyRow(player) }
        Spacer(Modifier.size(4.dp))
        Text(
            "티어는 metatft가 경기 전에 기록한 값이라 이번 판 결과가 반영되지 않았습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LobbyRow(player: LobbyPlayer) {
    val scheme = MaterialTheme.colorScheme
    val tint = placementTint(player.placement)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (player.isMe) scheme.primary.copy(alpha = 0.12f) else Color.Transparent)
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(tint.copy(alpha = if (player.placement <= 4) 0.24f else 0.13f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                player.placement.toString(),
                color = tint,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (player.placement == 1) FontWeight.Bold else FontWeight.Medium,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                player.riotId,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (player.isMe) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                player.tierText.ifBlank { "티어 기록 없음" },
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // 색만으로 구분하지 않도록 내 행에는 글자도 붙인다.
        if (player.isMe) {
            Text(
                "나",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            "Lv ${player.level}",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
    }
}
