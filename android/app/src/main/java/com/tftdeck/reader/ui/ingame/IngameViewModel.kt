package com.tftdeck.reader.ui.ingame

import android.app.Application
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tftdeck.reader.ingame.GameStatus
import com.tftdeck.reader.ingame.IngamePrefs
import com.tftdeck.reader.ingame.LastLobby
import com.tftdeck.reader.ingame.LobbyRepository
import com.tftdeck.reader.ingame.hasUsageStatsPermission
import com.tftdeck.reader.ingame.usageAccessSettingsIntent
import com.tftdeck.reader.overlay.OverlayService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 내 정보 탭의 게임 연동 부분.
 *
 * 스위치 값은 IngamePrefs에 두고, 감지는 오버레이 서비스(ACTION_WATCH)가 돌린다.
 * 이 뷰모델은 둘을 이어 주기만 한다 — 화면이 닫혀도 감지는 서비스에서 계속된다.
 */
class IngameViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = IngamePrefs.get(app)
    private val lobbies = LobbyRepository.get(app)

    val detectEnabled: StateFlow<Boolean> = prefs.detectEnabled
    val autoOverlay: StateFlow<Boolean> = prefs.autoOverlay
    val resultNotify: StateFlow<Boolean> = prefs.resultNotify
    val showOutsideTft: StateFlow<Boolean> = prefs.showOutsideTft

    /** 지난 게임 로비. 파일 캐시를 먼저 올리고, 판이 끝나면 서비스가 새로 채운다. */
    val lastLobby: StateFlow<LastLobby?> = lobbies.state

    val gameStatus: StateFlow<GameStatus> = OverlayService.gameStatus

    /** 감지가 실제로 돌고 있는지(스위치와 별개로). */
    val detecting: StateFlow<Boolean> = OverlayService.detecting

    private val _usagePermission = MutableStateFlow(hasUsageStatsPermission(app))
    val usagePermission: StateFlow<Boolean> = _usagePermission.asStateFlow()

    /** 권한 화면에 다녀오는 동안 켜 달라고 한 요청. 돌아왔을 때 권한이 있으면 마저 켠다. */
    private var pendingEnable = false

    init {
        viewModelScope.launch { lobbies.load() }
        ensureWatchRunning()
    }

    fun hasUsagePermission(): Boolean = hasUsageStatsPermission(getApplication())

    fun openUsageSettings(context: Context) {
        runCatching { context.startActivity(usageAccessSettingsIntent()) }
            .onFailure {
                // 일부 제조사 기기에는 사용 기록 접근 화면이 따로 없다. 설정 첫 화면이라도 연다.
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
    }

    /**
     * 'TFT 실행 감지'. 켤 때 권한이 없으면 저장하지 않고 권한 화면으로 보낸다
     * (권한 없이 켜진 것처럼 보이면 아무 일도 안 일어나는 이유를 알 수 없다).
     */
    fun setDetectEnabled(context: Context, enabled: Boolean) {
        if (enabled) {
            if (!hasUsagePermission()) {
                pendingEnable = true
                openUsageSettings(context)
                return
            }
            pendingEnable = false
            prefs.setDetectEnabled(true)
            OverlayService.startWatch(getApplication())
        } else {
            pendingEnable = false
            prefs.setDetectEnabled(false)
            OverlayService.stopWatch(getApplication())
        }
    }

    fun setAutoOverlay(enabled: Boolean) = prefs.setAutoOverlay(enabled)

    fun setResultNotify(enabled: Boolean) = prefs.setResultNotify(enabled)

    fun setShowOutsideTft(enabled: Boolean) = prefs.setShowOutsideTft(enabled)

    /** 화면이 다시 보일 때(권한 화면에서 돌아올 때 포함) 권한을 다시 확인한다. */
    fun onScreenResumed() {
        val granted = hasUsagePermission()
        _usagePermission.value = granted
        if (pendingEnable && granted) {
            pendingEnable = false
            prefs.setDetectEnabled(true)
            OverlayService.startWatch(getApplication())
        }
        ensureWatchRunning()
    }

    /** 스위치는 켜져 있는데 기기 재시작 등으로 서비스가 없으면 다시 켠다. 앱 화면이 보일 때만 부른다. */
    private fun ensureWatchRunning() {
        if (prefs.detectEnabled.value && hasUsagePermission() && !OverlayService.detecting.value) {
            OverlayService.startWatch(getApplication())
        }
    }
}
