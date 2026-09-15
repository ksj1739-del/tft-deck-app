package com.tftdeck.reader.ui.ingame

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tftdeck.reader.R
import com.tftdeck.reader.ingame.describe

/**
 * 게임 연동 설정: 사용 기록 접근 권한 안내, 스위치, 현재 상태 한 줄.
 * 권한은 시스템 설정에서만 켤 수 있어서, 돌아왔을 때(ON_RESUME) 다시 확인한다.
 *
 * [riotIdConnected] 가 false 면 판 종료 판정·결과 배지·지난 게임 로비가 채워지지 않는다
 * (모두 metatft 전적 조회에 기대므로). 감지를 켜 둔 사용자가 이유를 알 수 있게 안내한다.
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

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onScreenResumed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Android 13+ 는 결과 알림을 띄우려면 알림 권한이 필요하다. 거부해도 감지 자체는 동작한다.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
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
        if (!granted) {
            Text(
                stringResource(R.string.usage_permission_rationale),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(8.dp))
            Button(onClick = { viewModel.openUsageSettings(context) }) {
                Text("사용 기록 접근 허용")
            }
            Spacer(Modifier.size(10.dp))
        }

        SwitchRow(
            title = "TFT 실행 감지",
            subtitle = "TFT가 켜지고 꺼지는 것을 알아냅니다",
            checked = active,
            enabled = true,
            onCheckedChange = { on ->
                viewModel.setDetectEnabled(context, on)
                if (on && granted && resultNotify) askNotificationPermission()
            },
        )
        SwitchRow(
            title = "TFT가 켜지면 오버레이 자동 표시",
            subtitle = if (overlayGranted) {
                "TFT 밖에서는 자동으로 숨깁니다"
            } else {
                "아래 '게임 위에 띄우기'에서 다른 앱 위에 표시 권한을 먼저 허용해 주세요"
            },
            checked = autoOverlay,
            enabled = active,
            onCheckedChange = viewModel::setAutoOverlay,
        )
        if (autoOverlay) {
            SwitchRow(
                title = "TFT 밖에서도 표시",
                subtitle = "홈 화면이나 다른 앱에서도 오버레이를 숨기지 않습니다",
                checked = showOutsideTft,
                enabled = active,
                indent = true,
                onCheckedChange = viewModel::setShowOutsideTft,
            )
        }
        SwitchRow(
            title = "게임이 끝나면 결과 알림",
            subtitle = "등수와 LP 변화를 알려 줍니다. LP 기록 주기 때문에 최대 15분쯤 늦을 수 있습니다",
            checked = resultNotify,
            enabled = active,
            onCheckedChange = { on ->
                viewModel.setResultNotify(on)
                if (on) askNotificationPermission()
            },
        )

        Spacer(Modifier.size(8.dp))
        Text(
            "현재 상태: ${status.describe(detecting = active)}",
            style = MaterialTheme.typography.bodySmall,
        )
        if (active && !riotIdConnected) {
            Spacer(Modifier.size(4.dp))
            Text(
                "판 결과와 지난 게임 로비는 위 '내 전적'에서 라이엇 ID를 연결해야 채워집니다. " +
                    "연결하지 않으면 TFT 실행만 감지합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.error,
            )
        }
        Spacer(Modifier.size(4.dp))
        Text(
            "게임 중에는 상대 정보를 보여 주지 않습니다. 지난 게임 로비는 판이 끝난 뒤에만 채워집니다.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    indent: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (indent) 16.dp else 0.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) scheme.onSurface else scheme.onSurface.copy(alpha = 0.38f),
            )
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
