package com.tftdeck.reader.ingame

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Log
import com.tftdeck.reader.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 모바일 TFT 패키지. Play 스토어 id로 확인했다. */
const val TFT_PACKAGE = "com.riotgames.league.teamfighttactics"

/** TFT 앱이 화면 앞에 있는지. */
sealed interface GameState {
    /** 아직 TFT 이벤트를 한 번도 보지 못했다(권한 없음·잠금·감지 시작 직후). */
    data object Unknown : GameState

    /** [since]에 TFT 액티비티가 앞에 왔다. [className]은 로비/매치 구분 조사용. */
    data class Foreground(val since: Long, val className: String?) : GameState

    data class Background(val since: Long) : GameState
}

/**
 * TFT가 켜졌는지 알아낸다.
 *
 * 다른 앱의 화면 전환은 UsageStatsManager 기록으로만 알 수 있다(접근성 서비스·화면 캡처는 쓰지 않는다).
 * 시스템이 이미 남긴 이벤트의 최근 10초만 3초마다 읽으므로 배터리 부담이 작다.
 * 기기가 잠겨 있으면 queryEvents가 null을 주는데, 그때는 상태를 바꾸지 않는다.
 */
class GameDetector(context: Context) {

    private val appContext = context.applicationContext
    private val usage = appContext.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    private val _state = MutableStateFlow<GameState>(GameState.Unknown)
    val state: StateFlow<GameState> = _state.asStateFlow()

    /**
     * 지금 재개(resumed)된 TFT 액티비티들. 마지막 이벤트 하나만 보면 안 된다 —
     * 액티비티를 바꿀 때 순서가 'A 일시정지 → B 재개 → A 정지'라서 마지막 이벤트가
     * 정지여도 B는 여전히 앞에 있다.
     */
    private val resumed = mutableSetOf<String>()

    private var job: Job? = null

    fun hasPermission(): Boolean = hasUsageStatsPermission(appContext)

    fun settingsIntent(): Intent = usageAccessSettingsIntent()

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.Default) {
            // 서비스가 게임 도중 다시 시작된 경우를 놓치지 않도록 처음 한 번은 넓게 본다.
            poll(LOOKBACK_MS)
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                poll(WINDOW_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        synchronized(resumed) { resumed.clear() }
        _state.value = GameState.Unknown
    }

    private fun poll(windowMs: Long) {
        val manager = usage ?: return
        if (!hasPermission()) return
        val now = System.currentTimeMillis()
        // 잠금 상태면 null이다. 모르는 동안은 직전 상태를 유지한다.
        val events = runCatching { manager.queryEvents(now - windowMs, now) }.getOrNull() ?: return

        var sawTft = false
        var lastResumeAt = 0L
        var lastResumeClass: String? = null
        var lastLeaveAt = 0L
        val event = UsageEvents.Event()

        synchronized(resumed) {
            // 이벤트는 시간순으로 온다. 겹치는 구간을 다시 읽어도 활동별 마지막 이벤트가
            // 결과를 정하므로 같은 상태가 나온다.
            while (events.hasNextEvent()) {
                if (!events.getNextEvent(event)) break
                if (event.packageName != TFT_PACKAGE) continue
                val key = event.className.orEmpty()
                when (event.eventType) {
                    EVENT_RESUMED -> {
                        resumed += key
                        sawTft = true
                        lastResumeAt = event.timeStamp
                        lastResumeClass = event.className
                    }
                    EVENT_PAUSED, EVENT_STOPPED, EVENT_DESTROYED -> {
                        resumed -= key
                        sawTft = true
                        lastLeaveAt = event.timeStamp
                    }
                }
            }
            if (!sawTft) return

            val current = _state.value
            val next: GameState = if (resumed.isNotEmpty()) {
                if (current is GameState.Foreground) {
                    // 같은 앱 안에서 화면만 바뀐 것. 진입 시각은 처음 들어온 때로 둔다.
                    current.copy(className = lastResumeClass ?: current.className)
                } else {
                    GameState.Foreground(since = lastResumeAt.takeIf { it > 0 } ?: now, className = lastResumeClass)
                }
            } else {
                if (current is GameState.Background) current else GameState.Background(since = lastLeaveAt.takeIf { it > 0 } ?: now)
            }
            if (next != current) {
                // 로비와 매치가 다른 액티비티인지 실기기에서 확인하려고 디버그 빌드에만 남긴다.
                if (BuildConfig.DEBUG) Log.d(TAG, "state=$next resumed=$resumed")
                _state.value = next
            }
        }
    }

    companion object {
        private const val TAG = "GameDetector"
        private const val POLL_INTERVAL_MS = 3_000L
        private const val WINDOW_MS = 10_000L
        private const val LOOKBACK_MS = 2 * 60 * 60 * 1000L

        // UsageEvents.Event 상수. API 29에서 ACTIVITY_* 이름이 생겼고 값은 옛
        // MOVE_TO_FOREGROUND(1)/MOVE_TO_BACKGROUND(2)와 같다. minSdk 26이라 숫자로 둔다.
        private const val EVENT_RESUMED = 1
        private const val EVENT_PAUSED = 2
        private const val EVENT_STOPPED = 23
        private const val EVENT_DESTROYED = 24
    }
}

/**
 * '사용 기록 접근'이 허용됐는지.
 * 기본값(MODE_DEFAULT)이면 권한 부여 여부로 판단한다 — adb로 권한만 준 기기가 여기에 해당한다.
 */
fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    }
    return if (mode == AppOpsManager.MODE_DEFAULT) {
        context.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
    } else {
        mode == AppOpsManager.MODE_ALLOWED
    }
}

/** 시스템 설정의 사용 기록 접근 화면. 사용자가 직접 켜야 하는 권한이다. */
fun usageAccessSettingsIntent(): Intent =
    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
