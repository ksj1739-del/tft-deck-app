package com.tftdeck.reader.ingame

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.tftdeck.reader.BuildConfig
import com.tftdeck.reader.MainActivity
import com.tftdeck.reader.R
import com.tftdeck.reader.data.PlayerProfile
import com.tftdeck.reader.data.ProfileRepository
import com.tftdeck.reader.data.RatingSnapshot
import com.tftdeck.reader.data.firstObservedAt
import com.tftdeck.reader.data.formatLpDelta
import com.tftdeck.reader.data.lpDeltaFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/** 오버레이 칩과 설정 화면이 보여 주는 게임 연동 상태. */
sealed interface GameStatus {
    data object Idle : GameStatus

    /** TFT가 화면 앞에 있다. */
    data object TftForeground : GameStatus

    /** 판이 끝났는지 LP 기록을 확인하는 중. */
    data object Watching : GameStatus

    /** 판 종료를 감지했다. "6등 −35 LP". [at]은 감지 시각. */
    data class Result(val text: String, val at: Long = System.currentTimeMillis()) : GameStatus
}

/** 설정 화면의 상태 한 줄. */
fun GameStatus.describe(detecting: Boolean): String = when (this) {
    GameStatus.Idle -> if (detecting) "대기 중 · TFT가 켜지면 알아챕니다" else "꺼져 있음"
    GameStatus.TftForeground -> "TFT 실행 중"
    GameStatus.Watching -> "판 종료 확인 중"
    is GameStatus.Result -> text
}

/** 판 종료 판정 결과. [observedAt]은 새 판 수가 처음 기록된 시각. */
data class GameEnd(
    val numGames: Int,
    val gamesPlayed: Int,
    val lpDelta: Int?,
    val observedAt: Long,
)

/**
 * TFT 감지 결과로 판 종료를 알아내고, 끝나면 결과 배지·알림·지난 게임 로비를 채운다.
 *
 * 진행 중 게임 데이터는 열려 있지 않아서, metatft가 약 15분 간격으로 남기는 LP 기록의
 * 판 수(num_games)가 늘어나는 것으로 판 종료를 안다. 그래서 감지는 최대 15분쯤 늦을 수 있다.
 * 모든 판단은 서비스의 메인 스코프에서 순서대로 돌고, 네트워크만 저장소가 IO로 넘긴다.
 */
class GameSession(
    context: Context,
    private val gameState: StateFlow<GameState>,
    private val profiles: ProfileRepository,
    private val lobbies: LobbyRepository,
    private val prefs: IngamePrefs,
) {
    private val appContext = context.applicationContext

    private var sessionJob: Job? = null
    private var scope: CoroutineScope? = null
    private var foregroundJob: Job? = null
    private var watchJob: Job? = null
    private var lobbyJob: Job? = null
    private var resultJob: Job? = null

    private var inForeground = false
    private var watching = false
    private var result: GameStatus.Result? = null

    /** 판 종료를 비교할 기준 LP 기록. 세션에서 처음 받은 값부터 시작해 판정할 때마다 옮긴다. */
    private var baseline: RatingSnapshot? = null

    fun start(parent: CoroutineScope) {
        if (sessionJob?.isActive == true) return
        val job = SupervisorJob(parent.coroutineContext[Job])
        val sessionScope = CoroutineScope(parent.coroutineContext + job)
        sessionJob = job
        scope = sessionScope
        sessionScope.launch { LiveSpectate.refreshFlagsIfStale(prefs) }
        sessionScope.launch { gameState.collect { onGameState(it) } }
        publish()
    }

    fun stop() {
        sessionJob?.cancel()
        sessionJob = null
        scope = null
        foregroundJob = null
        watchJob = null
        lobbyJob = null
        resultJob = null
        inForeground = false
        watching = false
        result = null
        baseline = null
        _liveGame.value = false
        _state.value = GameStatus.Idle
    }

    // -- 감지 상태 ----------------------------------------------------------

    private fun onGameState(state: GameState) {
        when (state) {
            is GameState.Foreground -> if (!inForeground) {
                inForeground = true
                enterTft(state.since)
            }
            is GameState.Background -> if (inForeground) {
                inForeground = false
                leaveTft()
            }
            GameState.Unknown -> if (inForeground) {
                inForeground = false
                foregroundJob?.cancel()
                _liveGame.value = false
                publish()
            }
        }
    }

    private fun enterTft(since: Long) {
        val sessionScope = scope ?: return
        foregroundJob?.cancel()
        foregroundJob = sessionScope.launch {
            // TFT가 앞에 있는 동안 5분마다 LP만 확인한다(17KB). 오버레이 티어 카드도 이걸로 갱신된다.
            launch {
                while (isActive) {
                    pollRating(force = false)
                    delay(FOREGROUND_POLL_MS)
                }
            }
            // 25분이면 보통 한 판이 끝날 무렵이다. 앞에 머물러 있어도 촘촘한 감시를 시작한다.
            launch {
                val wait = since + FOREGROUND_WATCH_AFTER_MS - System.currentTimeMillis()
                if (wait > 0) delay(wait)
                startWatch()
            }
            launch { watchLiveLobby() }
        }
        publish()
    }

    private fun leaveTft() {
        foregroundJob?.cancel()
        foregroundJob = null
        _liveGame.value = false
        // 판이 끝나 나갔을 가능성이 크다. 나간 뒤에도 LP 기록이 늦게 올라오니 한동안 지켜본다.
        startWatch()
        publish()
    }

    // -- 판 종료 감시 ---------------------------------------------------------

    private fun startWatch() {
        val sessionScope = scope ?: return
        if (watching) return
        watching = true
        watchJob = sessionScope.launch {
            try {
                val deadline = System.currentTimeMillis() + WATCH_DURATION_MS
                while (isActive && System.currentTimeMillis() < deadline) {
                    if (pollRating(force = true)) break
                    delay(WATCH_INTERVAL_MS)
                }
            } finally {
                watching = false
                publish()
            }
        }
        publish()
    }

    /** LP 기록을 받아 기준과 비교한다. 판 종료를 감지했으면 true. */
    private suspend fun pollRating(force: Boolean): Boolean {
        if (!profiles.isConfigured) return false
        val snapshot = profiles.fetchRatingChanges(force) ?: return false
        val base = baseline
        // 첫 기록이거나 세트가 바뀌어 판 수가 줄었으면 기준만 잡는다.
        if (base == null || snapshot.numGames < base.numGames) {
            baseline = snapshot
            return false
        }
        val end = detectGameEnd(base, snapshot)
        baseline = snapshot
        if (end == null) return false
        onGameEnded(end)
        return true
    }

    private fun onGameEnded(end: GameEnd) {
        val sessionScope = scope ?: return
        val before = profiles.state.value.profileOrNull
        val beforeMatchAt = before?.recentMatchTimes?.firstOrNull() ?: 0L
        if (BuildConfig.DEBUG) Log.d(TAG, "game ended: $end")

        sessionScope.launch {
            // 전적 카드 갱신과 등수 확인을 lookup 한 번으로 한다.
            profiles.refresh(force = true)
            val after = profiles.state.value.profileOrNull
            val newest = after?.recentMatchTimes?.firstOrNull() ?: 0L
            val placement = when {
                after == null -> null
                beforeMatchAt > 0 && newest > beforeMatchAt -> after.recentPlacements.firstOrNull()
                else -> after.placementNear(end.observedAt)
            }
            val text = resultText(placement, end.lpDelta)
            showResult(text)
            if (prefs.resultNotify.value) notifyResult(text)
            fetchLobby(after, beforeMatchAt, end, placementKnown = placement != null)
        }
    }

    /** 경기 JSON은 로비가 끝난 뒤에 생긴다. 3분 간격으로 최대 30분 기다린다. */
    private fun fetchLobby(after: PlayerProfile?, beforeMatchAt: Long, end: GameEnd, placementKnown: Boolean) {
        val sessionScope = scope ?: return
        lobbyJob?.cancel()
        lobbyJob = sessionScope.launch {
            if (!profiles.isConfigured) return@launch
            // 라이엇 ID 와 로비 저장소의 계정 세대를 함께 잡는다. 둘 다 메인 스레드에서 읽으므로 그 사이에
            // 연결 해제가 끼지 않는다. 기다리는 30분 동안 해제·계정 변경이 있으면 옛 계정 로비를 받지도
            // 저장하지도 않는다(저장소가 세대로 한 번 더 막는다).
            val riotId = profiles.riotId
            val token = lobbies.accountToken()
            fun sameAccount(): Boolean = profiles.isConfigured && sameRiotId(profiles.riotId, riotId)

            // 방금 받은 요약에 새 경기가 이미 있으면 lookup 없이 경기 JSON만 받는다.
            val ref = after?.let { profile ->
                val id = profile.latestMatchId
                val url = profile.latestMatchUrl
                val at = profile.recentMatchTimes.firstOrNull() ?: 0L
                if (id != null && url != null && at > beforeMatchAt) {
                    MatchRef(id, url, profile.recentPlacements.firstOrNull() ?: 0, at)
                } else {
                    null
                }
            }
            var lobby = ref?.let { lobbies.fetchMatch(it, riotId, beforeMatchAt, token) }
            if (lobby == null && ref != null && lobbies.state.value?.matchId == ref.matchId) {
                lobby = lobbies.state.value
            }

            val deadline = System.currentTimeMillis() + LOBBY_DURATION_MS
            while (lobby == null && isActive && System.currentTimeMillis() < deadline) {
                delay(LOBBY_INTERVAL_MS)
                if (!sameAccount()) return@launch
                lobby = lobbies.fetchLatest(riotId, profiles.region, newerThan = beforeMatchAt, token = token)
            }
            val arrived = lobby ?: return@launch
            if (!sameAccount()) return@launch

            // 등수를 모른 채 알렸으면 로비의 내 등수로 고쳐 다시 알린다(같은 알림을 바꾼다).
            val me = arrived.me
            if (!placementKnown && me != null) {
                val text = resultText(me.placement, end.lpDelta)
                if (result != null) showResult(text)
                if (prefs.resultNotify.value) notifyResult(text)
            }
            // 전적 카드가 이 경기를 아직 모르면 한 번 더 받아 맞춘다.
            val known = profiles.state.value.profileOrNull?.recentMatchTimes?.firstOrNull() ?: 0L
            if (known < arrived.endedAt) profiles.refresh(force = true)
        }
    }

    private fun showResult(text: String) {
        val sessionScope = scope ?: return
        result = GameStatus.Result(text)
        publish()
        resultJob?.cancel()
        resultJob = sessionScope.launch {
            delay(RESULT_SHOW_MS)
            result = null
            publish()
        }
    }

    private fun publish() {
        if (scope == null) {
            _state.value = GameStatus.Idle
            return
        }
        _state.value = result ?: when {
            watching -> GameStatus.Watching
            inForeground -> GameStatus.TftForeground
            else -> GameStatus.Idle
        }
    }

    /**
     * 판 결과 알림. 판 종료는 최대 15분 늦게 알아채므로 그때 이미 다음 판을 하고 있을 수 있다.
     * TFT 가 앞에 있으면 소리·팝업 없는 채널로 조용히 올린다(S12). 채널 중요도는 만든 뒤 바꿀 수 없어 채널을 둘로 나눴다.
     */
    private fun notifyResult(text: String) {
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val channelId = resultChannelId(tftInFront = inForeground)
        if (manager.getNotificationChannel(channelId) == null) {
            manager.createNotificationChannel(
                if (channelId == RESULT_QUIET_CHANNEL_ID) {
                    // 문구는 strings.xml 로 옮길 때까지 코드에 둔다(용어 정리 단계).
                    NotificationChannel(channelId, "게임 결과(게임 중)", NotificationManager.IMPORTANCE_LOW)
                        .apply { description = "TFT 를 하는 중에는 소리 없이 판 결과를 알려 줍니다" }
                } else {
                    NotificationChannel(
                        channelId,
                        appContext.getString(R.string.game_result_channel_name),
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ).apply { description = appContext.getString(R.string.game_result_channel_desc) }
                }
            )
        }
        val open = PendingIntent.getActivity(
            appContext, 3,
            Intent(appContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(appContext, channelId)
            .setSmallIcon(R.drawable.ic_overlay)
            .setContentTitle(appContext.getString(R.string.game_result_title))
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(RESULT_NOTIFICATION_ID, notification) }
    }

    // -- 진행 중 게임(원격 플래그가 켜졌을 때만) ----------------------------------

    private suspend fun watchLiveLobby() {
        val source = LiveSpectate.sourceFor(prefs)
        if (source === NoopSource) return
        try {
            while (currentCoroutineContext().isActive) {
                val puuid = profiles.metatftPuuid
                if (puuid != null) {
                    val region = profiles.metatftRegion ?: profiles.region.lowercase(Locale.ROOT)
                    val lobby = source.current(region, puuid)
                    // 정책상 게임 중에는 티어 외에 아무것도 그리지 않는다. 게임 중인지 점으로만 쓴다.
                    _liveGame.value = lobby?.includesMe == true
                    if (BuildConfig.DEBUG) Log.d(TAG, "live lobby participants=${lobby?.participantCount}")
                }
                delay(LIVE_POLL_MS)
            }
        } finally {
            _liveGame.value = false
        }
    }

    companion object {
        private const val TAG = "GameSession"

        const val RESULT_SHOW_MS = 60_000L
        private const val FOREGROUND_POLL_MS = 5 * 60 * 1000L
        private const val FOREGROUND_WATCH_AFTER_MS = 25 * 60 * 1000L
        private const val WATCH_INTERVAL_MS = 3 * 60 * 1000L
        private const val WATCH_DURATION_MS = 30 * 60 * 1000L
        private const val LOBBY_INTERVAL_MS = 3 * 60 * 1000L
        private const val LOBBY_DURATION_MS = 30 * 60 * 1000L
        private const val LIVE_POLL_MS = 60_000L

        const val RESULT_CHANNEL_ID = "game_result"

        /** TFT 가 앞에 있을 때 쓰는 결과 채널(IMPORTANCE_LOW: 소리·팝업 없음). */
        const val RESULT_QUIET_CHANNEL_ID = "game_result_quiet"
        private const val RESULT_NOTIFICATION_ID = 43

        /** 결과 알림 채널. 다음 판을 하는 중(TFT 가 앞)이면 조용한 채널. */
        internal fun resultChannelId(tftInFront: Boolean): String =
            if (tftInFront) RESULT_QUIET_CHANNEL_ID else RESULT_CHANNEL_ID

        private val _state = MutableStateFlow<GameStatus>(GameStatus.Idle)

        /** 프로세스 전체에서 하나. 서비스가 꺼져 있으면 Idle. */
        val state: StateFlow<GameStatus> = _state.asStateFlow()

        private val _liveGame = MutableStateFlow(false)

        /** 진행 중 게임 조회로 내가 게임 중임을 확인했는지. 원격 플래그가 꺼져 있으면 항상 false. */
        val liveGame: StateFlow<Boolean> = _liveGame.asStateFlow()
    }
}

/**
 * 기준 기록보다 판 수가 늘었으면 판 종료다.
 * LP 변화는 새 판 수의 첫 기록 − 한 판 전 기록. 그 기록이 없으면 한 판만 늘었을 때에 한해
 * 최신 점수 − 기준 점수를 쓴다(여러 판이 묶였으면 한 판 값으로 볼 수 없어 null).
 */
internal fun detectGameEnd(baseline: RatingSnapshot, latest: RatingSnapshot): GameEnd? {
    if (latest.numGames <= baseline.numGames) return null
    val played = latest.numGames - baseline.numGames
    val delta = lpDeltaFor(latest.changes, latest.numGames)
        ?: if (played == 1) latest.ratingNumeric - baseline.ratingNumeric else null
    val observedAt = firstObservedAt(latest.changes, latest.numGames) ?: latest.fetchedAt
    return GameEnd(numGames = latest.numGames, gamesPlayed = played, lpDelta = delta, observedAt = observedAt)
}

/** "6등 −35 LP". 모르는 값은 뺀다. 둘 다 모르면 "게임 종료". */
internal fun resultText(placement: Int?, lpDelta: Int?): String {
    val parts = listOfNotNull(
        placement?.takeIf { it in 1..8 }?.let { "${it}등" },
        lpDelta?.let { formatLpDelta(it) + " LP" },
    )
    return if (parts.isEmpty()) "게임 종료" else parts.joinToString(" ")
}
