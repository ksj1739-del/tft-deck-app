package com.tftdeck.reader.ui.ingame

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tftdeck.reader.ingame.describe

/**
 * 게임 연동 설정: 스위치 하나가 사용 기록 권한 흐름까지 맡고(N6), 켜져 있을 때만 하위 항목이 보인다(N7).
 * 권한은 시스템 설정에서만 켤 수 있어서, 돌아왔을 때(ON_RESUME) 다시 확인한다.
 *
 * [riotIdConnected] 가 false 면 판 종료 판정·결과 알림·지난 게임 로비가 채워지지 않는다
 * (모두 metatft 전적 조회에 기대므로). 감지를 켜 둔 사용자가 이유를 알 수 있게 한 줄로 알린다.
 */
@Composable
fun GameLinkSection(
    viewModel: IngameViewModel,
    riotIdConnected: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme

    val granted by viewModel.usagePermission.collectAsState()
    val overlayGranted by viewModel.overlayPermission.collectAsState()
    val detectEnabled by viewModel.detectEnabled.collectAsState()
    val autoOverlay by viewModel.autoOverlay.collectAsState()
    val resultNotify by viewModel.resultNotify.collectAsState()
    val showOutsideTft by viewModel.showOutsideTft.collectAsState()
    val status by viewModel.gameStatus.collectAsState()
    val notificationPrompt by viewModel.notificationPrompt.collectAsState()
    val notificationsOn by viewModel.notificationsEnabled.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onScreenResumed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Android 13+ 는 알림을 띄우려면 알림 권한이 필요하다. 거부해도 감지 자체는 동작한다.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshNotifications() }
    fun askNotificationPermission() {
        if (needsNotificationPermission(context)) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // 사용 기록 권한 화면에 다녀와 감지가 켜졌으면, 스위치를 바로 켤 때처럼 알림 권한을 묻는다.
    LaunchedEffect(notificationPrompt) {
        if (notificationPrompt) {
            viewModel.consumeNotificationPrompt()
            askNotificationPermission()
        }
    }

    val active = detectEnabled && granted

    Column(modifier = modifier) {
        SettingSwitchRow(
            title = "게임 중 자동으로 띄우기",
            subtitle = if (granted) {
                "TFT 가 앞에 오면 오버레이를 띄웁니다 · 어떤 앱이 앞에 있는지만 봅니다"
            } else {
                "켜면 '사용 기록 접근' 화면이 열립니다 · 목록에서 FloaTFT → 허용 → 뒤로"
            },
            checked = active,
            onCheckedChange = { on ->
                viewModel.setDetectEnabled(context, on)
                // 권한이 있어 바로 켜졌을 때. 권한 화면을 거치면 돌아온 뒤 notificationPrompt 로 묻는다.
                if (on && granted) askNotificationPermission()
            },
        )

        // 감지가 꺼져 있으면 하위 항목은 뜻이 없다. 흐린 채 켜진 스위치로 두지 않고 숨긴다(N7).
        if (active) {
            Text(
                "오버레이를 언제 보일까요",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
            val mode = showMode(autoOverlay, showOutsideTft)
            Column(Modifier.selectableGroup()) {
                ChoiceRow(
                    title = "TFT 가 앞에 있을 때만 (추천)",
                    subtitle = "홈 화면이나 다른 앱에서는 숨깁니다",
                    selected = mode == ShowMode.OnlyInTft,
                    enabled = overlayGranted,
                    onSelect = { viewModel.setShowMode(ShowMode.OnlyInTft) },
                )
                ChoiceRow(
                    title = "항상",
                    subtitle = "TFT 밖에서도 숨기지 않습니다",
                    selected = mode == ShowMode.Always,
                    enabled = overlayGranted,
                    onSelect = { viewModel.setShowMode(ShowMode.Always) },
                )
            }
            if (!overlayGranted) {
                Text(
                    "'다른 앱 위에 표시' 를 먼저 허용해 주세요 · 위 '게임 위에 띄우기'",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(4.dp))
            SettingSwitchRow(
                title = "게임이 끝나면 결과 알림",
                subtitle = "등수와 LP 변화를 알려 줍니다 · 최대 15분 늦을 수 있습니다",
                checked = resultNotify,
                onCheckedChange = { on ->
                    viewModel.setResultNotify(on)
                    if (on) askNotificationPermission()
                },
            )
            if (!notificationsOn) {
                NotificationsOffRow(onOpenSettings = { openNotificationSettings(context) })
            }

            Spacer(Modifier.height(4.dp))
            Text(
                "현재 상태:${status.describe(detecting = true)}",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            if (!riotIdConnected) {
                Text(
                    "판 결과와 지난 게임 로비는 아래 '내 전적' 을 연결해야 나옵니다",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** 알림이 꺼져 있을 때 무엇을 잃는지와 고칠 곳(N8). */
@Composable
private fun NotificationsOffRow(onOpenSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "알림이 꺼져 있어 닫은 오버레이를 알림에서 되살릴 수 없습니다",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onOpenSettings) { Text("설정") }
    }
}

/**
 * 설정 행 한 모양(V25): 제목 14sp · 설명 12sp, 글자와 스위치 사이 16dp, 최소 높이 56dp. 행 전체가 눌린다.
 * 쓸 수 없는 행은 제목·설명·스위치를 함께 흐린다(alpha 0.38) — 제목만 흐리면 설명이 더 밝아 위계가 뒤집힌다.
 */
@Composable
internal fun SettingSwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant) }
        }
        Spacer(Modifier.width(16.dp))
        // 누름은 행이 받는다. 흐림은 행 alpha 하나로만 나타낸다(스위치 자체의 비활성 색을 겹치지 않는다).
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** 라디오 한 줄. [SettingSwitchRow] 와 같은 글자·흐림 규칙. */
@Composable
private fun ChoiceRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant) }
        }
    }
}

/** Android 13+ 에서 알림 권한을 아직 받지 않았는지. 그 아래 버전은 런타임 권한이 없다. */
internal fun needsNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

/** 이 앱의 알림 설정 화면. 없으면 앱 정보 화면. */
internal fun openNotificationSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.recoverCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private const val DISABLED_ALPHA = 0.38f
