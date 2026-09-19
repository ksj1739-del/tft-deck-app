package com.tftdeck.reader.ui.components

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tftdeck.reader.data.ProfileRepository
import com.tftdeck.reader.overlay.OverlayService
import com.tftdeck.reader.ui.AppViewModel

/**
 * 설치 뒤 첫 실행 안내. 전적 연결(라이엇 ID)과 오버레이 권한 중 빠진 것을 한 화면에서 묻는다.
 * 한 번 닫으면(완료·나중에·뒤로) 다시 뜨지 않는다. 둘 다 내 정보 탭과 덱 상세에서 언제든 다시 할 수 있다.
 */
@Composable
fun FirstRunDialog(viewModel: AppViewModel, onDone: () -> Unit) {
    val context = LocalContext.current
    val savedId by viewModel.savedRiotId.collectAsState()
    val busy by viewModel.profileBusy.collectAsState()
    var input by remember { mutableStateOf("") }
    var overlayGranted by remember { mutableStateOf(OverlayService.canDrawOverlays(context)) }

    // 권한 설정 화면에서 돌아오면 다시 확인해 '허용됨'으로 바꾼다.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) overlayGranted = OverlayService.canDrawOverlays(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val connected = savedId.contains("#")
    AlertDialog(
        onDismissRequest = onDone,
        // 바깥을 잘못 눌러 안내를 놓치지 않게 한다. 뒤로 가기와 버튼으로만 닫는다.
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text("시작하기 전에") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                SetupStep(
                    title = "내 전적 연결",
                    body = "라이엇 ID를 넣으면 오버레이와 내 정보 탭에 내 티어·최근 순위가 나옵니다.",
                    done = connected,
                    doneText = "연결됨 · $savedId",
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("이름#태그") },
                        supportingText = { Text("게임 안 프로필의 '이름#태그' · 지역 KR(내 정보에서 바꿀 수 있음)") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                    )
                    FilledTonalButton(
                        onClick = { viewModel.saveProfile(input, ProfileRepository.DEFAULT_REGION) },
                        enabled = !busy && input.contains("#"),
                    ) { Text(if (busy) "연결하는 중…" else "연결") }
                }
                SetupStep(
                    title = "오버레이 권한",
                    body = "게임 위에 덱 목록·레벨별 빌드를 띄우려면 '다른 앱 위에 표시'를 허용해야 합니다.",
                    done = overlayGranted,
                    doneText = "허용됨",
                ) {
                    FilledTonalButton(onClick = {
                        context.startActivity(OverlayService.permissionIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }) { Text("허용하러 가기") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDone) { Text(if (connected && overlayGranted) "완료" else "나중에") }
        },
    )
}

/** 안내 한 단계. 이미 돼 있으면 입력 대신 확인 표시만 보인다. */
@Composable
private fun SetupStep(title: String, body: String, done: Boolean, doneText: String, action: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
        Text(body, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        if (done) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(18.dp))
                Text(doneText, style = MaterialTheme.typography.labelLarge, color = scheme.primary)
            }
        } else {
            action()
        }
    }
}
