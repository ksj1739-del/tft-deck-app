package com.tftdeck.reader.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.tftdeck.reader.ui.theme.FloaColors
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tftdeck.reader.ingame.GameSession
import com.tftdeck.reader.ingame.GameStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/**
 * 접힌 칩 옆에 붙는 게임 연동 표시.
 * TFT가 앞에 있거나 판 종료를 확인하는 중이면 초록 점, 판 종료를 감지하면 60초 동안 "6등 −35 LP".
 * 아무 일도 없으면 자리를 차지하지 않는다 — 게임 화면을 가리는 면적을 늘리지 않기 위해서다.
 *
 * 통합 단계에서 OverlayContent.CollapsedChip 안에 `GameStatusBadge(OverlayService.gameStatus)`로 들어간다.
 */
@Composable
fun GameStatusBadge(statusFlow: StateFlow<GameStatus>, modifier: Modifier = Modifier) {
    val status by statusFlow.collectAsState()
    val live by GameSession.liveGame.collectAsState()

    when (val current = status) {
        GameStatus.Idle -> Unit
        GameStatus.TftForeground, GameStatus.Watching -> StatusDot(live, modifier)
        is GameStatus.Result -> ResultPill(current, modifier)
    }
}

@Composable
private fun StatusDot(live: Boolean, modifier: Modifier) {
    // 진행 중 게임이 확인되면(원격 플래그가 켜졌을 때만) 점에 테두리를 둘러 구분한다.
    Box(
        modifier
            .size(if (live) 8.dp else 6.dp)
            .clip(CircleShape)
            .background(BadgeGreen)
            .then(if (live) Modifier.border(1.dp, BadgeRing, CircleShape) else Modifier)
    )
}

@Composable
private fun ResultPill(result: GameStatus.Result, modifier: Modifier) {
    // 세션도 60초 뒤 Idle로 되돌리지만, 서비스가 멈춰 값이 남는 경우에도 배지가 오래 남지 않게 여기서도 잰다.
    var visible by remember(result) {
        mutableStateOf(System.currentTimeMillis() - result.at < GameSession.RESULT_SHOW_MS)
    }
    LaunchedEffect(result) {
        val left = GameSession.RESULT_SHOW_MS - (System.currentTimeMillis() - result.at)
        if (left > 0) delay(left)
        visible = false
    }
    if (!visible) return

    Text(
        text = result.text,
        color = BadgeText,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(BadgeBackground)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

private val BadgeGreen = FloaColors.Positive
private val BadgeRing = FloaColors.OnSurface
private val BadgeText = FloaColors.OnSurface
private val BadgeBackground = FloaColors.Primary.copy(alpha = 0.4f)
