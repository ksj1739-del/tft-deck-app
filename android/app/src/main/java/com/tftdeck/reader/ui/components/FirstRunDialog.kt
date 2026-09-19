package com.tftdeck.reader.ui.components

import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tftdeck.reader.overlay.OverlayService
import com.tftdeck.reader.ui.AppViewModel
import com.tftdeck.reader.ui.FirstRunProgress
import com.tftdeck.reader.ui.ProfileLink
import com.tftdeck.reader.ui.ingame.IngameViewModel
import com.tftdeck.reader.ui.ingame.needsNotificationPermission
import com.tftdeck.reader.ui.profileLink
import com.tftdeck.reader.ui.regionLabel
import com.tftdeck.reader.ui.regionOptions

/**
 * 설치 뒤 첫 실행 안내. 세 단계를 한 화면에서 묻는다: ① 내 전적 연결 ② 게임 위에 띄우기 권한
 * ③ 게임 중 자동으로 띄우기(선택, 사용 기록 권한).
 *
 * 전적은 실제 조회 결과로 '연결됨' 을 띄운다(N1) — 틀린 ID 면 입력 칸 아래 빨간 한 줄이 나오고 창은 닫히지 않는다.
 * 권한 화면에서 돌아오면(ON_RESUME) 다시 확인해 완료 표시로 바꾸고, 셋째 단계는 돌아오면 저절로 켠다(N2).
 * 한 번 닫으면(완료·나중에·뒤로) 다시 뜨지 않는다. [onClose] 의 인자는 빠진 단계가 남아 '내 정보에서 언제든' 을 알려야 하는지.
 */
@Composable
fun FirstRunDialog(viewModel: AppViewModel, onClose: (remindLater: Boolean) -> Unit) {
    val context = LocalContext.current
    // 액티비티 범위 인스턴스(NavHost 밖). 내 정보 탭의 인스턴스와 같은 설정값(IngamePrefs)을 본다.
    val ingame: IngameViewModel = viewModel()

    val savedId by viewModel.savedRiotId.collectAsState()
    val savedRegion by viewModel.savedRegion.collectAsState()
    val profileState by viewModel.profileState.collectAsState()
    val busy by viewModel.profileBusy.collectAsState()
    val overlayGranted by ingame.overlayPermission.collectAsState()
    val usageGranted by ingame.usagePermission.collectAsState()
    val detectEnabled by ingame.detectEnabled.collectAsState()
    val notificationsOn by ingame.notificationsEnabled.collectAsState()
    val notificationPrompt by ingame.notificationPrompt.collectAsState()

    // 권한 설정 화면에서 돌아오면 다시 확인한다. 사용 기록 권한을 받아 왔으면 뷰모델이 감지를 마저 켠다.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) ingame.onScreenResumed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 알림 권한은 자동으로 띄우기를 켠 직후에 이유와 함께 한 번 묻는다(따로 단계를 두지 않는다, N8).
    var notificationAsked by rememberSaveable { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ingame.refreshNotifications() }
    fun askNotificationOnce() {
        if (!notificationAsked && needsNotificationPermission(context)) {
            notificationAsked = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(notificationPrompt) {
        if (notificationPrompt) {
            ingame.consumeNotificationPrompt()
            askNotificationOnce()
        }
    }

    val link = profileLink(profileState, savedId)
    val autoOn = detectEnabled && usageGranted
    val progress = FirstRunProgress(link is ProfileLink.Linked, overlayGranted, autoOn)

    AlertDialog(
        onDismissRequest = { onClose(!progress.allDone) },
        // 바깥을 잘못 눌러 안내를 놓치지 않게 한다. 뒤로 가기(= 나중에)와 버튼으로만 닫는다.
        properties = DialogProperties(dismissOnClickOutside = false),
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text("시작하기 전에", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                SetupStep(
                    number = 1,
                    title = "내 전적 연결",
                    body = "라이엇 ID 를 넣으면 오버레이와 내 정보 탭에 내 티어가 나옵니다",
                    done = link is ProfileLink.Linked,
                ) {
                    if (link is ProfileLink.Linked) {
                        Text(link.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    } else {
                        RiotIdForm(
                            initialId = savedId,
                            initialRegion = savedRegion,
                            busy = busy,
                            link = link,
                            onConnect = viewModel::saveProfile,
                        )
                    }
                }

                SetupStep(
                    number = 2,
                    title = "게임 위에 띄우기 권한",
                    body = "게임 위에 덱을 띄우려면 '다른 앱 위에 표시' 를 허용해야 합니다",
                    done = overlayGranted,
                ) {
                    if (overlayGranted) {
                        Text("허용됨", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    } else {
                        OutlinedButton(onClick = {
                            context.startActivity(OverlayService.permissionIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }) { Text("권한 허용하기") }
                        PermissionHint()
                    }
                }

                SetupStep(
                    number = 3,
                    title = "게임 중 자동으로 띄우기",
                    body = "TFT 가 앞에 오면 오버레이가 뜨고 나가면 숨습니다 · 어떤 앱이 앞에 있는지만 봅니다",
                    done = autoOn,
                    optional = true,
                    trailing = {
                        Switch(
                            checked = autoOn,
                            onCheckedChange = { on ->
                                // 권한이 없으면 사용 기록 접근 화면으로 보내고, 돌아와서 권한이 있으면 뷰모델이 켠다.
                                ingame.setDetectEnabled(context, on)
                                if (on && usageGranted) askNotificationOnce()
                            },
                            modifier = Modifier.semantics { contentDescription = "게임 중 자동으로 띄우기" },
                        )
                    },
                ) {
                    if (!usageGranted) {
                        Text(
                            "켜면 '사용 기록 접근' 화면이 열립니다",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        PermissionHint()
                    }
                    if (!notificationsOn) {
                        Text(
                            "켜면 알림 권한도 묻습니다 · 닫은 오버레이를 다시 띄울 때 씁니다",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onClose(false) }, enabled = progress.requiredDone) { Text("완료") }
        },
        dismissButton = {
            TextButton(onClick = { onClose(!progress.allDone) }) { Text("나중에") }
        },
    )
}

/** 시스템 권한 화면에서 할 일 한 줄. 안드로이드 11부터는 앱 항목이 아니라 전체 목록이 열린다. */
@Composable
private fun PermissionHint() {
    Text(
        "목록에서 FloaTFT → 허용 → 뒤로",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 안내 한 단계. 머리줄은 [번호 또는 완료 표시][제목][선택 배지][trailing]. */
@Composable
private fun SetupStep(
    number: Int,
    title: String,
    body: String,
    done: Boolean,
    optional: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (done) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "완료",
                    tint = scheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Box(
                    Modifier
                        .size(20.dp)
                        .border(1.5.dp, scheme.outline, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(number.toString(), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(8.dp))
            // 제목과 '선택' 배지는 붙여 두고, 남는 폭은 이 묶음이 가져가 trailing(스위치)을 오른쪽 끝으로 민다.
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (optional) {
                    Spacer(Modifier.width(8.dp))
                    TextBadge("선택")
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
        }
        Text(body, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        content()
    }
}

/**
 * 라이엇 ID 입력 한 벌: [이름#태그] / [지역 ▾] [연결] / 실패 한 줄. 첫 실행 안내와 내 정보 탭이 같이 쓴다.
 * 조회 결과(ProfileLink)가 실패면 입력 칸 아래 빨간 한 줄, 조회 중이면 '확인하는 중…'. 입력한 글자는 실패해도 그대로 둔다.
 * [extraActions] 는 오른쪽 아래 글자 버튼 줄(연결 해제·취소).
 */
@Composable
internal fun RiotIdForm(
    initialId: String,
    initialRegion: String,
    busy: Boolean,
    link: ProfileLink,
    onConnect: (riotId: String, region: String) -> Unit,
    modifier: Modifier = Modifier,
    extraActions: (@Composable RowScope.() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    var input by rememberSaveable(initialId) { mutableStateOf(initialId) }
    var region by rememberSaveable(initialRegion) { mutableStateOf(initialRegion) }
    val checking = busy || link is ProfileLink.Checking
    val canConnect = !busy && input.contains('#')
    val failure = (link as? ProfileLink.Failed)?.message?.takeIf { !checking }
    val supporting = if (checking) "확인하는 중…" else failure

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("이름#태그") },
            singleLine = true,
            isError = failure != null,
            supportingText = if (supporting != null) {
                { Text(supporting) }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
            shape = MaterialTheme.shapes.small,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            RegionPicker(region = region, onPick = { region = it }, enabled = !busy)
            Spacer(Modifier.weight(1f))
            if (busy) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = scheme.primary)
                Spacer(Modifier.width(12.dp))
            }
            OutlinedButton(onClick = { onConnect(input.trim(), region) }, enabled = canConnect) { Text("연결") }
        }
        if (extraActions != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, content = extraActions)
        }
    }
}

/** 지역 고르기. 'KR · 한국 ▾' 를 누르면 목록이 펼쳐진다(첫 실행의 KR 고정을 없앴다, N1). */
@Composable
internal fun RegionPicker(region: String, onPick: (String) -> Unit, enabled: Boolean = true) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { open = true },
            enabled = enabled,
            contentPadding = PaddingValues(start = 12.dp, end = 8.dp),
            modifier = Modifier.semantics { contentDescription = "지역 ${regionLabel(region)}, 바꾸기" },
        ) {
            Text("$region · ${regionLabel(region)}", maxLines = 1)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            regionOptions().forEach { code ->
                DropdownMenuItem(
                    text = { Text("$code · ${regionLabel(code)}") },
                    onClick = {
                        onPick(code)
                        open = false
                    },
                )
            }
        }
    }
}
