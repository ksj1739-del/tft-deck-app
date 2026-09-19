package com.tftdeck.reader.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tftdeck.reader.data.Deck
import kotlinx.coroutines.delay

/**
 * 펼친 패널의 머리줄. 전체가 끌기 손잡이다(본문은 스크롤해야 해서 손잡이를 머리줄로 나눴다).
 *
 * 제목 칸(목록: 옮기기·덱 수·구간 / 덱: 뒤로·등급·별칭)은 버튼을 다 놓고 남는 폭만 쓴다 — 예전에는 제목이 먼저 폭을
 * 차지해 좁은 패널(세로 화면 + 티어 카드)에서 접기·앱 열기·닫기가 밀려 사라졌다.
 * 버튼 순서: 앱 열기 · 티어 카드 · 넓게 · 코드 복사 · 접기 | 닫기. 가장 자주 누르는 접기를 끝에 두고, 잘못 누르면 게임을
 * 떠나는 앱 열기는 접기에서 먼 쪽에 둔다. 닫기는 틈을 두고 맨 끝에 두며 두 번 눌러야 닫힌다([CloseButton]).
 */
@Composable
internal fun PanelHeader(
    selected: Deck?,
    bucket: String,
    countText: String,
    bucketName: String?,
    profileConnected: Boolean,
    showProfile: Boolean,
    wide: Boolean,
    dragModifier: Modifier,
    onBack: () -> Unit,
    onCycleBucket: () -> Unit,
    onCopyCode: (() -> Unit)?,
    onToggleProfile: () -> Unit,
    onToggleWide: () -> Unit,
    onCollapse: () -> Unit,
    onOpenApp: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = dragModifier
            .fillMaxWidth()
            .background(OverlayHeader)
            .padding(start = 4.dp, end = 3.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            if (selected == null) {
                Icon(
                    Icons.Default.DragIndicator,
                    contentDescription = "옮기기",
                    tint = OverlayMuted,
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(countText, color = OverlayText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                // 구간 이름. 누를 때마다 전체 → 마스터+ → 다이아+ → 골드~에메랄드 → 골드 이하로 넘어가고 앱에도 저장된다.
                if (bucketName != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        bucketName,
                        color = OverlayAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clip(RoundedCornerShape(6.dp))
                            .background(OverlayAccent.copy(alpha = 0.15f))
                            .clickable(onClick = onCycleBucket)
                            .padding(horizontal = 7.dp, vertical = 5.dp),
                    )
                }
            } else {
                HeaderBtn(Icons.AutoMirrored.Filled.ArrowBack, "목록으로", onClick = onBack)
                Text(
                    text = gradeText(selected, bucket),
                    color = gradeTint(selected, bucket),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                // 별칭은 남는 폭이 넉넉할 때만 둔다 — 한두 글자만 남은 별칭은 읽히지 않고 버튼만 좁힌다.
                BoxWithConstraints(Modifier.weight(1f)) {
                    if (maxWidth >= HEADER_ALIAS_MIN_WIDTH) {
                        Text(
                            text = selected.displayAlias,
                            color = OverlayText,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 5.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(4.dp))
        HeaderBtn(Icons.AutoMirrored.Filled.OpenInNew, "앱 열기", onClick = onOpenApp)
        // 티어 카드만 따로 켜고 끈다. 전적을 연결하지 않았으면 끌 카드가 없으니 숨긴다.
        if (profileConnected) {
            HeaderBtn(
                Icons.Default.EmojiEvents,
                if (showProfile) "티어 카드 숨기기" else "티어 카드 보기",
                tint = if (showProfile) OverlayAccent else OverlayMuted,
                onClick = onToggleProfile,
            )
        }
        HeaderBtn(
            if (wide) Icons.Default.CloseFullscreen else Icons.Default.OpenInFull,
            if (wide) "좁게 보기" else "넓게 보기",
            onClick = onToggleWide,
        )
        // 덱 코드 복사는 본문 버튼 대신 머리줄의 작은 아이콘으로 두어 패널을 얇게 한다.
        onCopyCode?.let { HeaderBtn(Icons.Default.ContentCopy, "덱 코드 복사", tint = OverlayAccent, onClick = it) }
        HeaderBtn(Icons.Default.UnfoldLess, "접기", onClick = onCollapse)
        Spacer(Modifier.width(CLOSE_GAP))
        CloseButton(onClose)
    }
}

/** 머리줄 버튼. 아이콘은 작게(16dp) 두고 누르는 칸은 머리줄 높이만큼 키웠다([HEADER_BUTTON_WIDTH]×[HEADER_BUTTON_HEIGHT]). */
@Composable
private fun HeaderBtn(icon: ImageVector, label: String, tint: Color = OverlayMuted, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = HEADER_BUTTON_WIDTH, height = HEADER_BUTTON_HEIGHT)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(16.dp))
    }
}

/**
 * 닫기. 게임 중 잘못 눌러 닫히면 되살리기 번거롭다 — 수동으로 띄운 창은 서비스가 끝나 앱으로 가야 하고, 감지 중이면
 * 이번 판 동안 자동 표시가 막힌다(알림의 '다시 띄우기'로만 되살린다). 그래서 처음 누르면 빨간 '닫기'로 바뀌고
 * [CLOSE_CONFIRM_MS] 안에 한 번 더 눌러야 닫힌다. 그냥 두면 X 로 돌아간다.
 */
@Composable
private fun CloseButton(onClose: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(CLOSE_CONFIRM_MS)
            armed = false
        }
    }
    if (!armed) {
        HeaderBtn(Icons.Default.Close, "닫기", onClick = { armed = true })
        return
    }
    Box(
        modifier = Modifier
            .height(HEADER_BUTTON_HEIGHT)
            .clip(RoundedCornerShape(8.dp))
            .background(OverlayDanger)
            .clickable(onClickLabel = "오버레이 닫기", onClick = onClose)
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("닫기", color = OverlayOnDanger, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/**
 * 덱 요약 머리줄에 별칭을 둘 최소 폭 — 버튼을 다 놓고 남는 폭이 이보다 좁으면(세로 화면 + 티어 카드) 별칭이
 * 한두 글자만 남아 두지 않는다. 가로 화면(게임 중, 패널 300dp 이상)에서는 남는다.
 */
private val HEADER_ALIAS_MIN_WIDTH = 56.dp

/**
 * 머리줄 버튼의 누르는 칸. 아이콘(16dp)은 그대로 작게 두고 칸을 머리줄 높이만큼 키웠다(예전 26dp 원).
 * 폭은 버튼 일곱 개(덱 요약: 뒤로·앱 열기·티어 카드·넓게·코드 복사·접기·닫기)가 가장 좁은 패널(세로 360dp 폰 +
 * 티어 카드 ≈ 222dp)에도 들어가도록 28dp 로 둔다.
 */
private val HEADER_BUTTON_WIDTH = 28.dp
private val HEADER_BUTTON_HEIGHT = 34.dp

/** 닫기를 다른 버튼과 떼어 두는 틈. 자주 누르는 접기 바로 옆이라 손가락이 미끄러져도 닫기에 덜 닿게. */
private val CLOSE_GAP = 6.dp

/** 닫기를 한 번 누른 뒤 두 번째 누름을 기다리는 시간. 지나면 원래 X 로 돌아간다. */
private const val CLOSE_CONFIRM_MS = 3_000L
