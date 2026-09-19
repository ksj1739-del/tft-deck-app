package com.tftdeck.reader.overlay

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tftdeck.reader.R
import com.tftdeck.reader.ui.bucketLabel
import com.tftdeck.reader.ui.bucketShortLabel
import com.tftdeck.reader.ui.theme.FloaColors
import kotlinx.coroutines.delay

/**
 * 머리줄 ⋯ 메뉴(WP-O1 — F2·F3·F8·F14). 게임 중 드물게 쓰는 기능을 자주 누르는 자리(접기) 옆에서 떼어 여기 모았다.
 * 메뉴를 여는 것 자체가 확인 역할을 해서 '넓게 → 앱 열기'·'닫기 두 번' 같은 같은 자리 연달아 누름 사고가 없다.
 *
 * Popup 이 아니라 패널 안 Box 로 그린다(OverlayContent). 오버레이 창 위의 Popup 은 창 토큰 때문에 기기마다 다르게 동작하고,
 * 새 창이 생기면 칩 자리 기준 배치·터치 플래그 계산이 흔들린다.
 *
 * 항목은 44dp 높이, 12sp. 켜고 끄는 항목(넓게 보기·티어 카드)과 코드 복사는 눌러도 메뉴를 닫지 않는다 — 바뀐 상태·'복사됨 ✓'를
 * 그 자리에서 보여 준다. 폭이 바뀌어 메뉴가 옮겨 가도 다음 누름은 메뉴 밖 덮개([OverlayMenuScrim])가 받아 메뉴만 닫는다.
 * 구간은 순환 대신 라디오(F8b)로 고르고, 고르면 앱과 함께 쓰는 구간이 바뀌며 메뉴가 닫힌다.
 * 메뉴 항목은 포커스를 받지 않는다(Q7 — 메뉴가 열린 동안 창이 포커스를 받아도 하드웨어 Enter 로 눌리지 않게).
 */
@Composable
internal fun OverlayMenu(
    wide: Boolean,
    onToggleWide: () -> Unit,
    profileConnected: Boolean,
    showProfile: Boolean,
    onToggleProfile: () -> Unit,
    teamCode: String?,
    bucket: String,
    buckets: List<String>,
    onSelectBucket: (String) -> Unit,
    onOpenApp: () -> Unit,
    onClose: () -> Unit,
    maxHeight: Dp,
    modifier: Modifier = Modifier,
) {
    var bucketPage by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(MENU_CORNER)
    Column(
        modifier = modifier
            .width(MENU_WIDTH)
            .heightIn(max = maxHeight)
            .clip(shape)
            .background(OverlayHeader)
            .border(1.dp, OverlayBorder, shape)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
    ) {
        if (bucketPage) {
            MenuRow(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                label = stringResource(R.string.overlay_menu_bucket),
                onClick = { bucketPage = false },
            )
            buckets.forEach { key ->
                BucketRow(label = bucketLabel(key), selected = key == bucket, onClick = { onSelectBucket(key) })
            }
            return@Column
        }
        SwitchRow(
            icon = if (wide) Icons.Default.CloseFullscreen else Icons.Default.OpenInFull,
            label = stringResource(R.string.overlay_menu_wide),
            checked = wide,
            onToggle = onToggleWide,
        )
        // 전적을 연결하지 않았으면 끌 카드가 없다.
        if (profileConnected) {
            SwitchRow(
                icon = Icons.Default.EmojiEvents,
                label = stringResource(R.string.overlay_menu_profile),
                checked = showProfile,
                onToggle = onToggleProfile,
            )
        }
        // 덱 요약에서만(덱 코드가 있는 덱).
        if (teamCode != null) CopyCodeRow(teamCode)
        if (buckets.isNotEmpty()) {
            MenuRow(
                icon = Icons.Default.Tune,
                label = stringResource(R.string.overlay_menu_bucket),
                onClick = { bucketPage = true },
            ) {
                Text(bucketShortLabel(bucket), style = OverlayChromeType.item, color = OverlayMuted, maxLines = 1)
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = OverlayMuted,
                    modifier = Modifier.size(MENU_ICON),
                )
            }
        }
        MenuRow(
            icon = Icons.AutoMirrored.Filled.OpenInNew,
            label = stringResource(R.string.overlay_menu_open_app),
            onClick = onOpenApp,
        )
        // 닫기는 선을 긋고 맨 아래에 떼어 둔다.
        Box(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .height(1.dp)
                .background(FloaColors.OutlineVariant)
        )
        MenuRow(
            icon = Icons.Default.Close,
            label = stringResource(R.string.overlay_menu_close),
            onClick = onClose,
        )
    }
}

/**
 * 메뉴가 열린 동안 패널 본문(검색 줄·목록·요약)을 덮는 투명한 덮개. 누르거나 끌면 메뉴만 닫고 아래(목록 스크롤·검색 줄)로는
 * 보내지 않는다 — 메뉴 밖 첫 누름이 엉뚱한 덱을 열거나 키보드를 띄우지 않게.
 */
@Composable
internal fun OverlayMenuScrim(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val dismiss by rememberUpdatedState(onDismiss)
    Box(
        modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false).consume()
                do {
                    val event = awaitPointerEvent()
                    event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
                dismiss()
            }
        },
    )
}

/** 코드 복사. 누르면 1초 동안 항목 글자가 '복사됨 ✓'로 바뀐다(F14 — 시스템 클립보드 미리보기는 막을 수 없어 글자로만 알린다). */
@Composable
private fun CopyCodeRow(code: String) {
    val context = LocalContext.current
    val clipLabel = stringResource(R.string.overlay_clip_label)
    var copied by remember(code) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(COPIED_SHOW_MS)
            copied = false
        }
    }
    MenuRow(
        icon = Icons.Default.ContentCopy,
        label = stringResource(if (copied) R.string.overlay_menu_copied else R.string.overlay_menu_copy),
        emphasis = copied,
        onClick = {
            copyCode(context, clipLabel, code)
            copied = true
        },
    )
}

/** 클립보드에 넣기만 한다. 게임 위에 토스트를 띄우지 않는다(오버레이 규칙) — 알림은 항목 글자가 맡는다. */
private fun copyCode(context: Context, label: String, code: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    runCatching { clipboard.setPrimaryClip(ClipData.newPlainText(label, code)) }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    emphasis: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(MENU_ITEM_HEIGHT)
            .focusProperties { canFocus = false }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val color = if (emphasis) FloaColors.Primary else OverlayText
        Icon(icon, contentDescription = null, tint = if (emphasis) color else OverlayMuted, modifier = Modifier.size(MENU_ICON))
        Text(
            label,
            style = OverlayChromeType.item,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/** 켜고 끄는 항목. 오른쪽 작은 스위치로 상태를 보인다(켜짐 = primary). */
@Composable
private fun SwitchRow(icon: ImageVector, label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(MENU_ITEM_HEIGHT)
            .focusProperties { canFocus = false }
            .toggleable(value = checked, role = Role.Switch, onValueChange = { onToggle() })
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = OverlayMuted, modifier = Modifier.size(MENU_ICON))
        Text(
            label,
            style = OverlayChromeType.item,
            color = OverlayText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(width = 30.dp, height = 18.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (checked) FloaColors.Primary else FloaColors.SurfaceBright)
                .padding(2.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(if (checked) FloaColors.OnPrimary else FloaColors.OnSurfaceVariant)
            )
        }
    }
}

/** 구간 라디오 한 줄. 고른 줄은 선택 표시 한 모양(MASTER 규칙 6: secondaryContainer 채움 + 글자)과 라디오 점. */
@Composable
private fun BucketRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(MENU_ITEM_HEIGHT)
            .focusProperties { canFocus = false }
            .then(if (selected) Modifier.background(FloaColors.SecondaryContainer) else Modifier)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(MENU_ICON)
                .border(1.5.dp, if (selected) FloaColors.Primary else FloaColors.Outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(FloaColors.Primary)
                )
            }
        }
        Text(
            label,
            style = OverlayChromeType.item,
            color = if (selected) FloaColors.OnSecondaryContainer else OverlayText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 메뉴 폭. 가장 좁은 패널(세로 화면 + 티어 카드, 200dp) 안에 들어간다. */
private val MENU_WIDTH = 196.dp

/** 메뉴 항목 높이(누름 영역 최소 44dp). */
private val MENU_ITEM_HEIGHT = 44.dp
private val MENU_ICON = 16.dp
private val MENU_CORNER = 12.dp

/** '복사됨 ✓'을 보이는 시간. */
private const val COPIED_SHOW_MS = 1_000L
