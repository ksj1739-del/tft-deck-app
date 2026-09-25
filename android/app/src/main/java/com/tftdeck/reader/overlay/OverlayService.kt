package com.tftdeck.reader.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.tftdeck.reader.MainActivity
import com.tftdeck.reader.R
import com.tftdeck.reader.data.DeckIdMigration
import com.tftdeck.reader.data.DeckRepository
import com.tftdeck.reader.data.FeedState
import com.tftdeck.reader.data.ProfileRepository
import com.tftdeck.reader.ingame.GameDetector
import com.tftdeck.reader.ingame.GameSession
import com.tftdeck.reader.ingame.GameState
import com.tftdeck.reader.ingame.GameStatus
import com.tftdeck.reader.ingame.IngamePrefs
import com.tftdeck.reader.ingame.LobbyRepository
import com.tftdeck.reader.ingame.describe
import com.tftdeck.reader.ingame.hasUsageStatsPermission
import com.tftdeck.reader.ui.bucketShortLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 게임 화면 위에 덱을 띄워 두는 오버레이.
 *
 * 다른 앱 위에 그려야 해서 일반 Activity로는 안 되고, 시스템 창(TYPE_APPLICATION_OVERLAY)을
 * WindowManager에 직접 붙인다. 그 창이 살아 있는 동안 프로세스가 죽지 않도록
 * 포그라운드 서비스로 유지한다.
 *
 * 창은 포커스를 가져가지 않는다(FLAG_NOT_FOCUSABLE). 오버레이를 띄운 채로
 * 게임을 그대로 조작할 수 있어야 하기 때문이다. 덱 목록 검색창을 누른 동안과 ⋯ 메뉴가 열린 동안에만 그 플래그를 빼고,
 * 끝나면 [endSearch]·[closeMenu] 에서 반드시 되돌린다. 플래그는 [overlayWindowFlags] 한 곳에서 합성한다.
 *
 * 창 자리는 접힌 칩 자리([anchor])가 기준이다. 펼친 창은 '접기' 버튼이 칩 자리에 오도록 놓고 칩 쪽 모서리를 창 gravity 로
 * 잡는다([placeWindow], OverlayPlacement). 그래서 칩을 누른 자리에서 곧바로 접히고, 창 크기가 바뀌어도 '접기'가 제자리다.
 *
 * 게임 연동을 켜면 창 없이 TFT 감지만 돌리다가(ACTION_WATCH) TFT가 앞에 오면 창을 붙인다.
 * 감지·판 종료 확인은 이 서비스가 살아 있는 동안에만 돈다 — 별도 백그라운드 작업을 두지 않는다.
 * 알림은 창 상태 세 가지(보임 / 감지 중 닫힘·숨김 / 직접 띄움)를 그대로 말한다([overlayNotice]).
 */
class OverlayService : android.app.Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private lateinit var repository: DeckRepository
    private lateinit var profiles: ProfileRepository
    private lateinit var ingamePrefs: IngamePrefs
    private var overlayView: ComposeView? = null

    /** 창에 붙는 뿌리 뷰(ComposeView 를 감싼다). addView·updateViewLayout·removeView 는 이 뷰로 한다. */
    private var overlayRoot: OverlayRootView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    /** 가시성 판단([applyVisibility])의 결과. 창 플래그를 합성할 때 검색 상태와 함께 쓴다. */
    private var windowVisible = true

    /**
     * 덱 목록 검색창이 키보드를 쓰는 중인지. 켜져 있는 동안만 창이 포커스를 받는다(FLAG_NOT_FOCUSABLE 해제).
     * 켜는 곳은 [startSearch], 끄는 곳은 [endSearch] 한 곳이다.
     */
    private val searching = MutableStateFlow(false)

    /** 머리줄 ⋯ 메뉴가 열려 있는지. 열린 동안 창이 포커스를 받아 뒤로 가기·창 밖 누름으로 닫힌다. */
    private val menuOpen = MutableStateFlow(false)

    private val expanded = MutableStateFlow(false)
    private val overlayData = MutableStateFlow<OverlayData?>(null)

    /**
     * 펼침 화면의 보던 자리: 고른 덱(null 이면 전체 덱 목록 — 인게임에서 뭘 갈지 고르는 게 기본 용도라 목록이 기본이다),
     * 덱별 레벨, 목록 스크롤, 닫은 기록. 접기·창 떼기·서비스 재시작을 넘겨야 해서 화면이 아니라 서비스가 들고 흘려 보낸다.
     */
    private lateinit var memory: OverlayMemory

    // -- 창 자리 --------------------------------------------------------------

    /**
     * 칩 자리: 접힌 칩의 왼쪽 위(창이 놓이는 영역 기준 px, 방향별로 저장). 펼친 창도 이 자리를 기준으로 놓는다.
     * 끌면 끈 만큼 더한다([dragAnchor]) — 펼친 창을 끌어도 창 좌표로 덮어쓰지 않는다(F6).
     */
    private var anchor = IntOffset(0, DEFAULT_TOP_MARGIN)

    /** 접힌 칩의 크기(px). 펼친 창의 '접기'를 칩 가운데에 맞출 때 쓴다(펼친 동안에는 창이 패널이라 잴 수 없다). */
    private var chipSize: IntSize? = null

    /** 펼친 창의 모양을 정하는 칩 사분면. 펼칠 때·회전할 때 칩 자리로 다시 정한다(펼친 채 끌어도 바꾸지 않는다). */
    private val quadrant = MutableStateFlow(Quadrant.TopLeft)

    /** 지금 창에 건 gravity 의 모서리. 창 x·y 는 이 모서리에서 잰다([gravityOffset]). 붙을 때는 왼쪽 위다. */
    private var windowCorner = Quadrant.TopLeft

    /** 끌기에서 정수 px 로 옮기고 남은 소수 부분. 천천히 끌어도 움직임을 잃지 않게. */
    private var dragRemainderX = 0f
    private var dragRemainderY = 0f

    /** 지금 가로 화면인지. 자리를 방향별로 기억하는 데 쓴다. */
    private var landscape = false

    // 좁게(얼굴만) / 넓게(이름·아이템·시너지까지). 마지막 선택을 기억한다.
    private val wide = MutableStateFlow(false)

    // 티어 카드만 따로 켜고 끈다. 마지막 선택을 기억한다. 기본은 꺼짐(R9·F10 — 판 중에는 바뀌지 않는 정보가 게임을 가린다).
    private val showProfile = MutableStateFlow(false)

    /** 알림 문구에 쓰는 목록 수·구간. 칩과 같은 값을 OverlayContent 가 알려 준다. */
    private var listCount: Int? = null
    private var listBucket: String? = null

    // -- 게임 연동 ---------------------------------------------------------------

    private var detector: GameDetector? = null
    private var session: GameSession? = null
    private val detectionJobs = mutableListOf<Job>()

    /** 감지기 상태를 서비스 안에서 한 번 더 들고 있다. 감지를 껐다 켜도 구독자가 바뀌지 않게. */
    private val gameState = MutableStateFlow<GameState>(GameState.Unknown)

    /** 사용자가 직접 켠 창인지. 자동 표시로 붙은 창과 구분해야 감지를 끌 때 무엇을 닫을지 안다. */
    private var userOverlay = false

    /** 자동 표시로 붙은 창인지(S8). 자동 표시가 꺼지면 이 창은 떼고 사용자가 띄운 창만 남긴다. */
    private var autoAttached = false

    /**
     * 알림의 '지금 보이기'로 잠깐 보이게 한 상태. 자동 표시의 'TFT 가 앞에 있을 때만' 규칙을 넘는다. TFT 가 앞에 오거나 떠나면,
     * 앱 화면이 열리면, 창이 떨어지면 풀린다([forceVisibleExpires]).
     */
    private var forceVisible = false

    /** 마지막으로 본 TFT 전면 구간 시작 시각. 새 구간(새 판)이면 요약이 아니라 접힌 칩에서 시작한다(F13). */
    private var lastForegroundSince: Long? = null

    /** 닫은 창의 자동 표시 차단(40분)이 풀릴 때 다시 판단한다 — 그사이 가시성 입력이 그대로면 아무도 다시 부르지 않는다. */
    private val mainHandler = Handler(Looper.getMainLooper())
    private val dismissRecheck = Runnable { lastVisibility?.let { applyVisibility(it) } }

    private var foregroundStarted = false
    private var observersStarted = false
    private var lastVisibility: VisibilityInput? = null

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        repository = DeckRepository.get(this)
        profiles = ProfileRepository.get(this)
        ingamePrefs = IngamePrefs.get(this)
        wide.value = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_WIDE, false)
        // 티어 카드는 기본 꺼짐(R9·F10). 직접 켠 사용자는 저장값(true)이 있어 그대로 켜져 있다.
        showProfile.value = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SHOW_PROFILE, false)
        // 보던 덱·레벨·목록 자리·닫은 기록은 저장값에서 되찾는다 — 시스템이 서비스를 되살려도(START_STICKY) 이어서 본다.
        memory = OverlayMemory(getSharedPreferences(PREFS, Context.MODE_PRIVATE))
        landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        observeVisibility()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 시스템이 START_STICKY로 되살린 경우. 무엇이 켜져 있었는지 저장값으로 되찾는다.
        if (intent == null) return restoreAfterRestart()

        return when (intent.action) {
            ACTION_STOP -> {
                closeOverlay()
                keepAliveResult()
            }
            ACTION_STOP_WATCH -> {
                // 알림의 '감지 잠시 끄기'(S5). 서비스만 멈추고 설정은 그대로 둔다 — 앱을 다시 열거나 재부팅하면 다시 감지한다.
                stopWatchMode()
                keepAliveResult()
            }
            ACTION_DISABLE_WATCH -> {
                // 예전 알림의 '감지 끄기'(이미 떠 있던 알림에서 올 수 있다). 설정 스위치까지 끈다.
                ingamePrefs.setDetectEnabled(false)
                stopWatchMode()
                keepAliveResult()
            }
            ACTION_WATCH -> startWatchMode()
            ACTION_SHOW -> showAgain()
            else -> startUserOverlay(intent)
        }
    }

    private fun keepAliveResult(): Int =
        if (overlayView != null || _detecting.value) START_STICKY else START_NOT_STICKY

    /** 설정의 '오버레이 표시'나 덱 상세의 '게임 위에 띄우기'로 켠 경우. */
    private fun startUserOverlay(intent: Intent): Int {
        // startForegroundService로 왔으므로 다른 판단보다 먼저 포그라운드로 올린다.
        if (!enterForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }

        // 권한이 없으면 창을 붙일 수 없다. 조용히 실패하지 않고 바로 종료한다(감지만 돌던 중이면 감지는 유지).
        if (!canDrawOverlays(this)) {
            if (!_detecting.value) stopSelf()
            return keepAliveResult()
        }

        setUserOverlay(true)
        // 자동으로 붙어 있던 창이라도 이제 사용자가 띄운 창이다.
        autoAttached = false
        memory.clearDismissal()

        // 덱을 지정해서 띄웠으면(덱 상세의 '게임 위에 띄우기') 그 덱을 바로 연다.
        // 지정 없이 띄웠으면(설정의 '오버레이 표시') 목록에서 시작한다. 서비스가 새로 만들어지며 저장값에서
        // 되찾은 덱이 있어도 목록이다 — 되찾은 덱은 시스템 재시작·자동 표시처럼 사용자가 새로 고르지 않은 경우에 쓴다.
        val deckId = intent.getStringExtra(EXTRA_DECK_ID)
        deckId?.let { repository.pinnedDeckId = it }
        memory.selectDeck(deckId)
        startObservers()
        if (overlayView == null) attachOverlay(auto = false)

        // 게임 연동을 켜 두었으면 직접 켠 오버레이에서도 감지를 함께 돌린다.
        if (ingamePrefs.detectEnabled.value && hasUsageStatsPermission(this)) startDetection()
        updateNotification()
        return START_STICKY
    }

    /** 게임 연동을 켰을 때. 창은 붙이지 않고 감지만 돌린다. */
    private fun startWatchMode(): Int {
        if (!enterForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!ingamePrefs.detectEnabled.value || !hasUsageStatsPermission(this)) {
            if (overlayView == null) stopSelf()
            return keepAliveResult()
        }
        startObservers()
        startDetection()
        updateNotification()
        return START_STICKY
    }

    /**
     * 알림의 '지금 보이기'(본문 누름도 같다). 닫은 창·숨은 창을 게임을 떠나지 않고 곧바로 보이게 한다 — 닫은 판 동안의
     * 자동 표시 차단을 무시하고([forceVisible]), 'TFT 가 앞에 있을 때만' 규칙도 잠깐 넘는다. 사용자가 켠 창으로 바꾸지는 않는다
     * (감지를 끄면 함께 닫힌다).
     */
    private fun showAgain(): Int {
        // 알림은 포그라운드 서비스가 살아 있는 동안에만 있으니 보통은 이미 올라가 있다. 혹시 새로 만들어진 서비스면 먼저 올린다.
        if (!foregroundStarted && !enterForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!canDrawOverlays(this)) {
            if (!_detecting.value && overlayView == null) stopSelf()
            return keepAliveResult()
        }
        memory.clearDismissal()
        forceVisible = true
        startObservers()
        if (overlayView == null) attachOverlay(auto = false) else lastVisibility?.let { applyVisibility(it) }
        updateNotification()
        return keepAliveResult()
    }

    private fun stopWatchMode() {
        stopDetection()
        if (userOverlay && overlayView != null) {
            updateNotification()
        } else {
            // 자동으로 붙은 창이면 감지를 끌 때 같이 닫는다. 사용자가 켠 창은 그대로 둔다.
            stopSelf()
        }
    }

    private fun restoreAfterRestart(): Int {
        val wantOverlay = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_USER_OVERLAY, false) &&
            canDrawOverlays(this)
        val wantWatch = ingamePrefs.detectEnabled.value && hasUsageStatsPermission(this)
        if (!wantOverlay && !wantWatch) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!enterForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        startObservers()
        if (wantOverlay) {
            userOverlay = true
            if (overlayView == null) attachOverlay(auto = false)
        }
        if (wantWatch) startDetection()
        updateNotification()
        return START_STICKY
    }

    /**
     * 포그라운드로 올린다. 백그라운드 재시작 등으로 시스템이 거절하면 false —
     * 그때 억지로 버티면 프로세스가 강제 종료되므로 호출한 쪽이 서비스를 끝낸다.
     */
    private fun enterForeground(): Boolean {
        val ok = runCatching { startForeground(NOTIFICATION_ID, buildNotification()) }.isSuccess
        if (ok) {
            foregroundStarted = true
            if (lifecycleRegistry.currentState == Lifecycle.State.CREATED) {
                lifecycleRegistry.currentState = Lifecycle.State.STARTED
            }
        }
        return ok
    }

    private fun setUserOverlay(value: Boolean) {
        userOverlay = value
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_USER_OVERLAY, value).apply()
    }

    // -- 덱 --------------------------------------------------------------

    private fun startObservers() {
        if (observersStarted) return
        observersStarted = true
        observeDecks()
        observeProfile()
    }

    private fun observeDecks() {
        scope.launch {
            repository.state.collect { state ->
                if (state !is FeedState.Ready) return@collect
                // 덱 id 가 바뀐 데이터면(그룹이 조합 덱에 합쳐지는 등) 보던 덱·레벨·목록 자리도 새 id 로 옮긴다.
                // 저장소가 고정·숨김·오버레이 덱을 옮기는 것과 같은 대응표다.
                memory.migrateIds(
                    mapping = DeckIdMigration.mapping(state.feed.decks),
                    current = state.feed.decks.mapTo(HashSet()) { it.id },
                )
                overlayData.value = OverlayData(
                    decks = state.feed.decks,
                    assetBase = state.feed.version.assetBase,
                )
                // 알림이 보던 덱 이름을 말하므로 데이터가 바뀌면(재시작 직후 첫 로드·덱이 목록으로 튐) 다시 그린다(S7).
                updateNotification()
            }
        }
    }

    /**
     * 내 전적은 사람마다 다르고 한 판 끝날 때마다 바뀌므로 앱이 직접 가져온다.
     * 저장된 요약을 먼저 올리고, 곧이어 LP 기록만 가볍게 확인한다.
     */
    private fun observeProfile() {
        scope.launch {
            profiles.load()
            refreshProfileLight(force = false)
        }
    }

    /**
     * 오버레이의 전적 갱신은 rating_changes(17KB)로 한다. 판 수가 늘었을 때만
     * 최근 등수를 위해 lookup(63KB)을 한 번 더 부른다.
     */
    private suspend fun refreshProfileLight(force: Boolean) {
        val before = profiles.state.value.profileOrNull
        val snapshot = profiles.fetchRatingChanges(force)
        when {
            snapshot == null -> if (force || before == null) profiles.refresh(force = force)
            before == null || snapshot.numGames > before.seasonGames -> profiles.refresh(force = true)
        }
    }

    // -- 게임 연동 ---------------------------------------------------------------

    private fun startDetection() {
        if (_detecting.value) return
        val newDetector = GameDetector(this)
        val newSession = GameSession(this, newDetector.state, profiles, LobbyRepository.get(this), ingamePrefs)
        detector = newDetector
        session = newSession
        newDetector.start(scope)
        newSession.start(scope)
        detectionJobs += scope.launch { newDetector.state.collect { gameState.value = it } }
        detectionJobs += scope.launch {
            GameSession.state.collect { status ->
                // 닫은 뒤에 판이 끝났으면(결과 배지) 차단을 풀고 다시 판단한다 — 다음 판부터 다시 자동으로 뜬다(S1).
                if (status is GameStatus.Result && memory.onGameResult(status.at)) {
                    lastVisibility?.let { applyVisibility(it) }
                }
                // 창 없이 감지만 할 때는 알림이 유일한 표시라 상태가 바뀌면 문구를 고친다.
                updateNotification()
            }
        }
        _detecting.value = true
        // 판 종료 판정은 저장된 계정 정보를 쓴다. 창이 없어도 먼저 올려 둔다.
        startObservers()
    }

    private fun stopDetection() {
        session?.stop()
        detector?.stop()
        session = null
        detector = null
        detectionJobs.forEach { it.cancel() }
        detectionJobs.clear()
        gameState.value = GameState.Unknown
        // 닫은 기록(memory.dismissal)은 지우지 않는다 — 같은 판에 감지를 다시 켜도 닫은 창이 저절로 돌아오지 않게.
        forceVisible = false
        lastForegroundSince = null
        mainHandler.removeCallbacks(dismissRecheck)
        _detecting.value = false
    }

    /**
     * 창을 보일지 정한다([overlayVisible]).
     * - 앱 화면이 열려 있으면 숨긴다. 같은 정보가 앱을 가리기 때문이다.
     * - 자동 표시(감지 + 'TFT가 켜지면 오버레이 자동 표시')면 TFT가 앞에 있을 때만 보인다
     *   ('TFT 밖에서도 표시'를 켜면 계속 보인다). TFT가 새로 앞에 오면 창이 없더라도 붙인다 — 이 판에 닫은 창은 빼고(S1).
     * 숨길 때는 터치도 통과시켜 보이지 않는 창이 다른 앱 조작을 막지 않게 하고, 펼친 창은 접는다(F11).
     * 가시성이 바뀌면 검색·메뉴를 끝낸다 — 숨은 창이 포커스를 쥐고 있으면 안 된다.
     */
    private fun observeVisibility() {
        scope.launch {
            combine(
                OverlayState.appVisible,
                gameState,
                _detecting,
                ingamePrefs.autoOverlay,
                ingamePrefs.showOutsideTft,
            ) { appVisible, game, detecting, auto, outside ->
                VisibilityInput(appVisible, game, autoMode = detecting && auto, showOutsideTft = outside)
            }.collect { applyVisibility(it) }
        }
    }

    private fun applyVisibility(input: VisibilityInput) {
        val previous = lastVisibility
        lastVisibility = input
        val inTft = input.game as? GameState.Foreground

        // '지금 보이기'는 잠깐만 — TFT 가 앞에 오거나 떠나면, 앱 화면이 열리면 평소 규칙으로 돌아간다.
        if (previous != null && forceVisibleExpires(
                wasInTft = previous.game is GameState.Foreground,
                inTft = inTft != null,
                wasAppVisible = previous.appVisible,
                appVisible = input.appVisible,
            )
        ) {
            forceVisible = false
        }

        // 새 TFT 전면 구간(새 판)이면 요약이 아니라 접힌 칩에서 시작한다(F13). 고른 덱은 그대로 둔다.
        if (inTft != null && inTft.since != lastForegroundSince) {
            lastForegroundSince = inTft.since
            if (expanded.value) setExpanded(false)
        }

        // 자동 표시를 끄면(감지를 멈추면) 자동으로 붙은 창은 뗀다(S8) — 숨어 있던 자동 창이 홈·다른 앱 위에 나타나지 않게.
        if (overlayView != null && detachAutoWindow(input.autoMode, autoAttached, userOverlay)) {
            detachOverlay()
            updateNotification()
            return
        }

        if (input.autoMode && inTft != null && overlayView == null && canDrawOverlays(this)) {
            val now = System.currentTimeMillis()
            val dismissal = memory.dismissal
            if (!autoAttachBlocked(dismissal, inTft.since, now)) {
                startObservers()
                attachOverlay(auto = true) // 붙은 뒤 lastVisibility로 다시 여기를 거친다.
                updateNotification()
                return
            }
            // 이 판에 닫은 창. 차단이 풀리는 때(40분) 다시 판단한다.
            if (dismissal != null) scheduleDismissRecheck(dismissal, now)
        }

        if (overlayRoot == null || layoutParams == null) {
            updateNotification()
            return
        }
        val visible = overlayVisible(
            appVisible = input.appVisible,
            autoMode = input.autoMode,
            inTft = inTft != null,
            showOutsideTft = input.showOutsideTft,
            forceVisible = forceVisible,
        )
        if (visible != windowVisible) {
            endSearch(apply = false)
            closeMenu(apply = false)
            // 숨을 때 접는다(F11) — 돌아오면 칩부터. 덱·레벨·목록 자리는 서비스가 기억하므로 한 번 눌러 그대로 돌아온다.
            if (!visible) expanded.value = false
        }
        windowVisible = visible
        applyWindow()
        updateNotification()
    }

    private fun scheduleDismissRecheck(dismissal: OverlayDismissal, now: Long) {
        mainHandler.removeCallbacks(dismissRecheck)
        val wait = (dismissal.at + DISMISS_BLOCK_MS - now).coerceAtLeast(0L) + DISMISS_RECHECK_SLACK_MS
        mainHandler.postDelayed(dismissRecheck, wait)
    }

    /**
     * 보임·검색·메뉴 상태를 창에 반영한다. 플래그는 [overlayWindowFlags] 한 곳에서 합성하고, 자리도 지금 상태로 다시 맞춘다
     * (숨어 있는 사이 회전했을 수 있다, [computePlacement]).
     */
    private fun applyWindow() {
        val root = overlayRoot ?: return
        val params = layoutParams ?: return
        root.visibility = if (windowVisible) View.VISIBLE else View.GONE
        params.flags = overlayWindowFlags(visible = windowVisible, searching = searching.value, menuOpen = menuOpen.value)
        computePlacement(params, root)
        runCatching { windowManager.updateViewLayout(root, params) }
    }

    // -- 펼치기·메뉴 -------------------------------------------------------------

    /**
     * 펼치고 접는다. 펼칠 때는 칩 자리로 사분면을 정하고 곧바로 그 모서리 기준으로 놓는다 — 패널이 처음 그려질 때부터
     * '접기'가 칩 자리에 있다. 접을 때는 검색·메뉴를 끝내고 칩 자리로 돌려놓는다.
     */
    private fun setExpanded(open: Boolean) {
        if (open == expanded.value) return
        if (open) {
            quadrant.value = quadrantOf(anchor, chip(), areaSize())
            expanded.value = true
            placeWindow()
            // 펼칠 때 전적을 한 번 확인한다. 3분 안이면 저장소가 직전 결과를 돌려준다.
            scope.launch { refreshProfileLight(force = false) }
        } else {
            endSearch(apply = false)
            closeMenu(apply = false)
            expanded.value = false
            applyWindow()
        }
    }

    /** ⋯ 메뉴를 연다. 창이 포커스를 받아 뒤로 가기·창 밖 누름으로 닫힌다(검색 중이면 검색을 먼저 끝낸다). */
    private fun openMenu() {
        if (!expanded.value || !windowVisible || menuOpen.value || overlayRoot == null) return
        endSearch(apply = false)
        menuOpen.value = true
        applyWindow()
    }

    private fun closeMenu(apply: Boolean = true) {
        if (!menuOpen.value) return
        menuOpen.value = false
        if (apply) applyWindow()
    }

    /** 창이 포커스를 받는 상태(검색·메뉴)를 모두 끝낸다. 뒤로 가기·창 밖 누름·포커스 잃음(OverlayRootView)이 부른다. */
    private fun endFocusModes() {
        val menuWasOpen = menuOpen.value
        closeMenu(apply = false)
        if (searching.value) endSearch() else if (menuWasOpen) applyWindow()
    }

    // -- 오버레이 검색(키보드) ---------------------------------------------------

    /**
     * 덱 목록 검색창을 눌렀을 때. 창의 FLAG_NOT_FOCUSABLE 을 빼서 포커스를 받게 한다 — 그래야 키보드가 뜬다.
     * 숨은 창에서는 켜지 않는다. 입력칸은 창이 실제로 포커스를 받은 뒤에 포커스를 잡는다(OverlayContent).
     * 검색하는 동안에는 창을 왼쪽 위 gravity 로 바꿔 둔다 — 머리줄이 숨거나 후보가 늘어도 검색 줄이 제자리에 있고,
     * 키보드를 피해 올리는 계산([liftForIme])이 창 위 기준이다. 지금 보이는 자리는 그대로다.
     */
    private fun startSearch() {
        if (overlayRoot == null || !windowVisible || searching.value) return
        closeMenu(apply = false)
        pinToTopLeft()
        searching.value = true
        applyWindow()
        // API 30 미만은 키보드 인셋 신호가 오지 않아 여기서 올린다(S14).
        liftForIme()
    }

    /** 창 gravity 를 왼쪽 위로 바꾼다. 지금 창 크기로 왼쪽 위 자리를 되짚어 보이는 자리는 그대로다. */
    private fun pinToTopLeft() {
        val root = overlayRoot ?: return
        val params = layoutParams ?: return
        if (windowCorner == Quadrant.TopLeft) return
        val topLeft = gravityOffset(IntOffset(params.x, params.y), IntSize(root.width, root.height), areaSize(), windowCorner)
        windowCorner = Quadrant.TopLeft
        params.gravity = windowCorner.gravity
        params.x = topLeft.x
        params.y = topLeft.y
    }

    /**
     * 검색 중 키보드가 창을 가리면 창 아래가 키보드 위에 오도록 올린다(화면 위보다 위로는 안 간다, [liftAboveIme]).
     * 창은 SOFT_INPUT_ADJUST_NOTHING 이라 시스템이 밀어 주지 않는다 — 가로 화면은 키보드가 화면의 60% 남짓을 덮어
     * 기본 자리에서도 후보가 전부 가려졌고, 아래로 옮겨 둔 창은 검색창까지 가려졌다.
     *
     * 키보드 위치는 이 창의 인셋이 아니라 화면 기준(WindowMetrics)으로 잰다. 이 창의 인셋은 '창 아래가 키보드에 가린 높이'인데,
     * 창을 올린 직후에도 옛 틀 기준 값을 한 번 더 보내 와서(에뮬레이터 로그: 올린 뒤에도 820px) 그대로 빼면 필요보다 높이
     * 화면 맨 위까지 올라갔다. 키보드 인셋 변화와 창 배치는 부르는 신호로만 쓴다.
     * API 30 미만은 키보드 위치를 알 수 없어 가로 화면에서 맨 위로 올린다([searchFallbackY], S14).
     */
    private fun liftForIme() {
        if (!searching.value) return
        val root = overlayRoot ?: return
        val params = layoutParams ?: return
        val y = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val insets = metrics.windowInsets
            if (!insets.isVisible(WindowInsets.Type.ime())) return
            val imeTop = metrics.bounds.bottom - insets.getInsets(WindowInsets.Type.ime()).bottom
            liftAboveIme(params.y, root.height, overlayArea().top, imeTop)
        } else {
            searchFallbackY(params.y, landscape)
        }
        if (y == params.y) return
        params.y = y
        runCatching { windowManager.updateViewLayout(root, params) }
    }

    /**
     * 검색을 끝내고 창을 다시 포커스를 받지 않는 상태로 되돌린다. 키보드를 먼저 내린다.
     * 부르는 곳: 후보 선택·IME 검색 키(OverlayContent) · 창 밖 누름·뒤로 가기·포커스 잃음(OverlayRootView) ·
     * 덱 고름(목록 닫힘) · 접기 · 메뉴 열기 · 앱 열기 · 닫기 · 가시성 변화 · 회전 · 창 떼기 · 서비스 종료.
     * 키보드를 피해 올렸던 창은 칩 자리 기준으로 돌아간다([computePlacement]).
     * [apply] 가 false 면 부른 쪽이 곧바로 창을 반영하거나(가시성 변화) 창이 곧 사라진다.
     */
    private fun endSearch(apply: Boolean = true) {
        if (!searching.value) return
        searching.value = false
        overlayRoot?.let { root ->
            runCatching {
                getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(root.windowToken, 0)
            }
        }
        if (apply) applyWindow()
    }

    // -- 창 --------------------------------------------------------------

    /** [auto] 는 자동 표시가 붙인 창인지(S8). */
    private fun attachOverlay(auto: Boolean) {
        windowVisible = true
        searching.value = false
        menuOpen.value = false
        autoAttached = auto
        // 이 방향에서 놓아 둔 자리. 창 크기는 붙은 뒤 첫 배치에서 알게 되므로 그때 한 번 더 화면 안으로 맞춘다.
        landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        anchor = loadAnchor()
        // 패널이 첫 프레임부터 이 화면 크기로 스스로를 재도록 영역을 먼저 알려 둔다([areaState]).
        areaSize()
        windowCorner = Quadrant.TopLeft
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // 포커스를 가져가지 않아야 오버레이를 띄운 채 게임을 조작할 수 있다(FLAG_NOT_FOCUSABLE).
            // 검색·메뉴 동안에만 풀린다 — 플래그는 overlayWindowFlags 한 곳에서 합성한다.
            overlayWindowFlags(visible = true, searching = false),
            PixelFormat.TRANSLUCENT,
        ).apply {
            // 붙을 때는 칩을 왼쪽 위 gravity 로 둔다(칩 크기를 아직 몰라도 칩 자리가 맞는다). 펼칠 때 칩 쪽 모서리로 바꾼다.
            gravity = windowCorner.gravity
            val start = initialPlacement(anchor)
            x = start.x
            y = start.y
            // 검색 중 키보드가 떠도 시스템이 창을 밀거나 줄이지 않는다. 가리면 서비스가 직접 올린다(liftForIme).
            softInputMode = OVERLAY_SOFT_INPUT_MODE
        }
        layoutParams = params

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setContent {
                OverlayContent(
                    dataFlow = overlayData,
                    profileFlow = profiles.state,
                    selectedIdFlow = memory.selectedDeckId,
                    levelsFlow = memory.deckLevels,
                    listAnchorFlow = memory.listAnchor,
                    expandedFlow = expanded,
                    wideFlow = wide,
                    showProfileFlow = showProfile,
                    searchingFlow = searching,
                    onToggleExpand = { setExpanded(!expanded.value) },
                    onToggleWide = {
                        val next = !wide.value
                        wide.value = next
                        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                            .putBoolean(KEY_WIDE, next).apply()
                    },
                    onToggleProfile = {
                        val next = !showProfile.value
                        showProfile.value = next
                        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                            .putBoolean(KEY_SHOW_PROFILE, next).apply()
                        // 다시 켤 때는 그사이 판이 끝났을 수 있으니 한 번 확인한다.
                        if (next) scope.launch { refreshProfileLight(force = false) }
                    },
                    // 사용자가 직접 누른 새로고침은 최소 간격을 무시한다.
                    onRefreshProfile = { scope.launch { refreshProfileLight(force = true) } },
                    onSelectDeck = { id ->
                        // 덱을 고르면 목록(검색창)이 닫힌다.
                        endSearch()
                        closeMenu()
                        memory.selectDeck(id)
                        if (id != null) repository.pinnedDeckId = id
                        // 알림 문구가 보고 있는 덱 이름이다.
                        updateNotification()
                    },
                    onSelectLevel = { deckId, level -> memory.setLevel(deckId, level) },
                    onListAnchor = { memory.setListAnchor(it) },
                    onSearchStart = { startSearch() },
                    onSearchEnd = { endSearch() },
                    onDrag = { dx, dy -> moveBy(dx, dy) },
                    onDragEnd = { persistPosition() },
                    onOpenApp = { openApp() },
                    onClose = { closeOverlay() },
                    quadrantFlow = quadrant,
                    menuOpenFlow = menuOpen,
                    onMenuOpenChange = { open -> if (open) openMenu() else closeMenu() },
                    // 칩을 길게 누르면 목록으로 펼친다(F13 — 새 판에 지난 판 덱 요약부터 열리지 않게).
                    onExpandToList = {
                        memory.selectDeck(null)
                        updateNotification()
                        setExpanded(true)
                    },
                    onChipSize = { size -> if (size.width > 0 && size.height > 0) chipSize = size },
                    areaFlow = areaState,
                    onListStatus = { count, bucket ->
                        if (count != listCount || bucket != listBucket) {
                            listCount = count
                            listBucket = bucket
                            updateNotification()
                        }
                    },
                )
            }
        }
        // 창의 뿌리. Compose 의 창 단위 재구성기가 뿌리 뷰에서 수명 주기를 찾으므로 소유자를 여기에도 단다.
        val root = OverlayRootView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            // 창이 포커스를 받는 동안(검색 또는 ⋯ 메뉴)의 끝낼 신호(뒤로 가기·창 밖 누름·포커스 잃음)를 함께 받는다.
            isSearching = { searching.value || menuOpen.value }
            onEndSearch = { endFocusModes() }
            onWindowLayout = { keepOnScreen() }
            onImeInsetsChanged = { liftForIme() }
            addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        overlayView = view
        overlayRoot = root
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        runCatching { windowManager.addView(root, params) }
            .onSuccess {
                OverlayState.running.value = true
                lastVisibility?.let { applyVisibility(it) }
            }
            .onFailure {
                // 권한이 도중에 회수된 경우. 조용히 떠 있는 척하지 않는다(감지 중이면 감지는 계속).
                overlayView = null
                overlayRoot = null
                layoutParams = null
                autoAttached = false
                if (!_detecting.value) stopSelf()
            }
    }

    /**
     * 창을 닫는다(머리줄 ⋯ 메뉴의 '오버레이 닫기', 알림의 '감추기'·'닫기'). 감지 중이면 서비스는 남겨 두고 이번 판 동안(같은 TFT
     * 전면 구간, 40분 안, 판 종료 전 — [autoAttachBlocked]) 자동으로 다시 띄우지 않는다. 닫은 기록은 저장해 재시작에도 이어진다.
     * 잘못 닫았으면 알림의 '지금 보이기'([ACTION_SHOW])로 곧바로 되살린다. 감지 중이 아니면 기존처럼 서비스를 끝낸다.
     */
    private fun closeOverlay() {
        // 창을 떼기 전에 키보드를 내리고 포커스를 돌려준다(서비스 종료는 비동기라 그사이에도 포커스를 쥐지 않게).
        endSearch()
        closeMenu()
        setUserOverlay(false)
        if (_detecting.value) {
            (gameState.value as? GameState.Foreground)?.let { memory.dismiss(it.since, System.currentTimeMillis()) }
            detachOverlay()
            updateNotification()
        } else {
            stopSelf()
        }
    }

    private fun detachOverlay() {
        endSearch(apply = false)
        closeMenu(apply = false)
        overlayRoot?.let { root -> runCatching { windowManager.removeView(root) } }
        overlayView?.disposeComposition()
        overlayView = null
        overlayRoot = null
        layoutParams = null
        expanded.value = false
        autoAttached = false
        forceVisible = false
        OverlayState.running.value = false
    }

    /**
     * 드래그. 칩 자리를 끈 만큼 옮기고([dragAnchor] — 펼친 창을 끌어도 덮어쓰지 않고 더한다, F6) 창을 그 자리 기준으로 다시 놓는다.
     * 플래그는 [applyWindow] 가 합성해 둔 값을 그대로 쓴다(검색 중이어도 풀리지 않는다). 화면 밖으로는 끌려 나가지 않는다.
     */
    private fun moveBy(dx: Float, dy: Float) {
        val params = layoutParams ?: return
        dragRemainderX += dx
        dragRemainderY += dy
        val stepX = dragRemainderX.toInt()
        val stepY = dragRemainderY.toInt()
        if (stepX == 0 && stepY == 0) return
        dragRemainderX -= stepX
        dragRemainderY -= stepY
        anchor = dragAnchor(anchor, stepX, stepY, chip(), areaSize())
        if (searching.value) {
            // 키보드를 피해 올린 창은 손가락을 그대로 따라간다(검색 중 gravity 는 왼쪽 위). 끝나면 옮긴 칩 자리 기준으로 돌아간다.
            params.x += stepX
            params.y += stepY
        }
        placeWindow()
    }

    /** 드래그를 마친 칩 자리를 이 방향의 자리로 기억한다. 끄는 동안 이미 화면 안으로 맞춘 값이다. */
    private fun persistPosition() {
        dragRemainderX = 0f
        dragRemainderY = 0f
        saveAnchor(anchor)
    }

    /**
     * 창을 다시 배치할 때마다(펼침·접힘·넓게 보기·목록↔덱·검색·회전) 자리를 지금 상태로 다시 맞춘다([placeWindow]).
     * 펼친 창은 칩 쪽 모서리가 gravity 라 크기만 바뀌었으면 보통 고칠 것이 없다(자라는 쪽이 화면을 넘을 때만 민다).
     * 붙거나 회전한 뒤 첫 배치에서 칩 크기를 알게 된다. 저장해 둔 자리가 이 화면에서 칩을 다 담지 못하면(해상도가
     * 바뀌었거나 다른 기기에서 복원) 맞춘 자리를 새로 저장한다 — 저장값도 늘 화면 안이다. 첫 배치에서만 한다:
     * 나중에 칩이 잠깐 넓어져(판 결과 배지) 밀린 자리까지 저장하면 칩이 조금씩 떠내려간다.
     */
    private fun keepOnScreen() {
        val root = overlayRoot ?: return
        placeWindow()
        if (searching.value) {
            // 치는 동안 후보가 늘어 창이 커졌으면 다시 키보드 위로 올린다.
            liftForIme()
            return
        }
        if (!expanded.value && anchorUnchecked && root.width > 0 && root.height > 0) {
            anchorUnchecked = false
            val placed = dragAnchor(anchor, 0, 0, IntSize(root.width, root.height), areaSize())
            if (placed != anchor) saveAnchor(placed)
        }
    }

    /** 붙은 뒤·회전 뒤 첫 배치에서 저장해 둔 자리를 칩 크기로 확인해야 하는지([keepOnScreen]). */
    private var anchorUnchecked = false

    /** 지금 상태로 창 자리를 다시 계산해 바뀐 것이 있을 때만 창에 반영한다. */
    private fun placeWindow() {
        val root = overlayRoot ?: return
        val params = layoutParams ?: return
        val beforeGravity = params.gravity
        val beforeX = params.x
        val beforeY = params.y
        computePlacement(params, root)
        if (params.gravity != beforeGravity || params.x != beforeX || params.y != beforeY) {
            runCatching { windowManager.updateViewLayout(root, params) }
        }
    }

    /**
     * 지금 상태로 창 gravity·x·y 를 정해 [params] 에 넣는다(창 반영은 부른 쪽).
     * - 검색 중: 왼쪽 위 gravity 로 지금 자리를 화면 안으로만 맞춘다(키보드를 피해 올리는 건 [liftForIme]).
     * - 펼침: 칩 사분면 모서리를 gravity 로, '접기' 가운데가 칩 가운데에 오게([expandedPlacement]). 칩 쪽 모서리 기준이라
     *   밀려나지 않은 창의 x·y 는 창 크기와 상관없다 — 아직 칩이 그려져 있는 첫 프레임에 불러도 패널이 제자리에 뜬다.
     * - 접힘: 칩 자리에 칩을 둔다. gravity 는 지난번 펼친 모서리 그대로 두고 칩 크기로 계산한다 — 접는 순간 아직 패널인 창이
     *   한 프레임 그려져도 칩 자리 근처에 있고, 칩이 그려지면 곧바로 제자리다.
     */
    private fun computePlacement(params: WindowManager.LayoutParams, root: View) {
        val area = areaSize()
        val rootSize = IntSize(root.width, root.height)
        val corner: Quadrant
        val size: IntSize
        val topLeft: IntOffset
        when {
            searching.value -> {
                corner = Quadrant.TopLeft
                size = rootSize
                val current = gravityOffset(IntOffset(params.x, params.y), rootSize, area, windowCorner)
                topLeft = clampOverlayPosition(OverlayPosition(current.x, current.y), size.width, size.height, area.width, area.height)
                    .let { IntOffset(it.x, it.y) }
            }
            expanded.value -> {
                corner = quadrant.value
                size = rootSize
                topLeft = expandedPlacement(
                    anchor = anchor,
                    chipSize = chip(),
                    panelSize = rootSize,
                    area = IntRect(0, 0, area.width, area.height),
                    corner = corner,
                    collapseCenter = collapseCenter(),
                    farMargin = dpToPx(PANEL_FAR_MARGIN_DP),
                )
            }
            else -> {
                corner = windowCorner
                size = chip(rootSize)
                topLeft = dragAnchor(anchor, 0, 0, size, area)
            }
        }
        val offset = gravityOffset(topLeft, size, area, corner)
        windowCorner = corner
        params.gravity = corner.gravity
        params.x = offset.x
        params.y = offset.y
    }

    /** 칩 크기(px). 아직 잰 적 없으면 [fallback](접힌 창 크기) 또는 칩 앞머리 크기. */
    private fun chip(fallback: IntSize? = null): IntSize =
        chipSize ?: fallback?.takeIf { it.width > 0 && it.height > 0 } ?: dpToPx(MIN_VISIBLE_DP).let { IntSize(it, it) }

    /** 패널의 칩 쪽 모서리에서 '접기' 버튼 가운데까지(px). 머리줄(OverlayHeader)의 버튼 크기와 같은 값이다. */
    private fun collapseCenter(): IntOffset =
        IntOffset(dpToPx(HEADER_BUTTON_WIDTH_DP) / 2, dpToPx(HEADER_HEIGHT_DP) / 2)

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).roundToInt()

    private fun areaSize(): IntSize {
        val area = overlayArea()
        return IntSize(area.width(), area.height()).also { areaState.value = it }
    }

    /**
     * 지금 화면 영역(px). 패널이 스스로 크기를 정할 때 쓴다([OverlayContent]) — 창에 들어오는 제약을 쓰면 '지금 창 크기'가
     * 다시 기준이 돼 끄는 동안 창이 커졌다 작아지며 깜빡인다. 같은 값이면 흘려보내므로(StateFlow) 재구성은 화면이
     * 실제로 달라질 때만 일어난다.
     */
    private val areaState = MutableStateFlow(IntSize.Zero)

    private var areaCache: Rect? = null
    private var areaCachedAt = 0L

    /**
     * 창 x·y 의 기준 영역(화면 좌표, px). 시스템은 이 창을 화면에서 지금 보이는 시스템 바와 컷아웃을 뺀 영역 안에서 gravity 로
     * 놓는다 — 왼쪽 위 gravity 면 그 왼쪽 위가 (0,0), 오른쪽 아래 gravity 면 x·y 는 그 오른쪽·아래 변에서 잰다. FLAG_LAYOUT_NO_LIMITS 는
     * 그 밖으로 나가도 잘라 내지 않을 뿐 기준은 같다.
     * 에뮬레이터(1080x2400)의 dumpsys window 로 확인: 세로 parent=[0,136][1080,2337], 가로 parent=[136,74][2400,1017]
     * (상태 표시줄 136/74, 제스처 막대 63, 가로의 왼쪽 컷아웃 136). 그래서 시스템 바를 빼고 맞춘다 — 창이 막대 밑으로
     * 들어가 버튼이 가려지지 않는다. 게임이 시스템 바를 숨기면 보이는 바가 없어 영역도 그만큼 넓어진다.
     * 끄는 동안 이벤트마다 부르므로 잠깐(0.3초) 기억해 둔다.
     */
    private fun overlayArea(): Rect {
        val now = android.os.SystemClock.uptimeMillis()
        areaCache?.takeIf { now - areaCachedAt < AREA_CACHE_MS }?.let { return it }
        val area = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val insets = metrics.windowInsets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            Rect(metrics.bounds).apply { inset(insets.left, insets.top, insets.right, insets.bottom) }
        } else {
            // API 29 이하: 앱 영역(내비게이션 막대를 뺀 크기)에서 상태 표시줄 높이를 뺀다.
            @Suppress("DEPRECATION")
            val size = Point().also { windowManager.defaultDisplay.getSize(it) }
            val statusBarId = resources.getIdentifier("status_bar_height", "dimen", "android")
            val statusBar = if (statusBarId != 0) resources.getDimensionPixelSize(statusBarId) else 0
            Rect(0, statusBar, size.x, size.y)
        }
        areaCache = area
        areaCachedAt = now
        return area
    }

    /**
     * 이 방향에서 놓아 둔 자리. 창 크기를 아직 모르므로 여기서는 저장값을 그대로 돌려주고, 칩 크기로 화면 안에 맞춰
     * 필요하면 다시 저장하는 것은 첫 배치([keepOnScreen])가 한다.
     */
    private fun loadAnchor(): IntOffset {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val (keyX, keyY) = overlayPositionKeys(landscape)
        // 가로 자리는 새로 생긴 값이다. 아직 없으면 예전(방향 구분 없던) 자리에서 시작한다.
        val legacyX = prefs.getInt(KEY_X, 0)
        val legacyY = prefs.getInt(KEY_Y, DEFAULT_TOP_MARGIN)
        anchorUnchecked = true
        return IntOffset(prefs.getInt(keyX, legacyX), prefs.getInt(keyY, legacyY))
    }

    /** 창 크기를 모르는 첫 배치 전에도 칩 앞머리([MIN_VISIBLE_DP])는 화면 안에 들게 한 자리(왼쪽 위 gravity). */
    private fun initialPlacement(target: IntOffset): OverlayPosition {
        val minVisible = dpToPx(MIN_VISIBLE_DP)
        val area = overlayArea()
        return clampOverlayPosition(OverlayPosition(target.x, target.y), minVisible, minVisible, area.width(), area.height())
    }

    private fun saveAnchor(position: IntOffset) {
        anchor = position
        val (keyX, keyY) = overlayPositionKeys(landscape)
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(keyX, position.x)
            .putInt(keyY, position.y)
            .apply()
    }

    /**
     * 회전. 창은 가로·세로마다 놓아 둔 칩 자리로 간다(게임은 가로, 홈 화면은 세로). 방향이 바뀌면 검색·메뉴를 끝낸다(Q16 —
     * 키보드를 피해 올린 자리와 숨긴 머리줄이 옛 방향 기준이다). 펼쳐 있으면 새 칩 자리로 사분면을 다시 정하고 그 기준으로 놓는다.
     * 새 방향에서 다시 잰 크기는 곧이어 [keepOnScreen] 이 한 번 더 맞춘다. 창이 없어도(감지만 하는 중) 방향은 기억해 둔다.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        areaCache = null
        val land = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (land == landscape) {
            // 방향은 같고 화면 크기만 바뀐 경우(접는 폰 등). 지금 자리를 다시 맞춘다.
            keepOnScreen()
            return
        }
        landscape = land
        endSearch(apply = false)
        closeMenu(apply = false)
        anchor = loadAnchor()
        if (expanded.value) quadrant.value = quadrantOf(anchor, chip(), areaSize())
        applyWindow()
    }

    /** 보고 있던 덱을 앱에서 그대로 이어서 연다. 목록이었으면 그냥 앱만 연다. */
    private fun openApp() {
        // 앱이 앞에 오면 창이 숨지만, 그 전에 포커스를 먼저 돌려준다.
        endSearch()
        closeMenu()
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .apply { memory.selectedDeckId.value?.let { putExtra(EXTRA_OPEN_DECK, it) } }
        )
    }

    // -- 알림 -------------------------------------------------------------

    /**
     * 창 상태 세 가지를 그대로 말하는 알림(S7·S2·S5, [overlayNotice]):
     * (1) 감지 중 창 보임 '덱 12 · 골드~에메 · 오버레이 표시 중' [감추기][앱 열기][감지 잠시 끄기]
     * (2) 감지 중 창 닫힘·숨김 'TFT 감지 중 · 오버레이 닫힘' [지금 보이기][앱 열기][감지 잠시 끄기] — 본문을 눌러도 지금 보이기
     * (3) 감지 없이 직접 띄움 '오버레이 켜짐' [닫기][앱 열기].
     * '감지 잠시 끄기'는 서비스만 멈춘다(설정은 그대로). 부르는 곳: 가시성·데이터·덱·목록 수 변화, 닫기, 게임 연동 상태 변화.
     */
    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.overlay_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = getString(R.string.overlay_channel_desc)
                    setShowBadge(false)
                }
            )
        }

        val attached = overlayView != null
        val notice = overlayNotice(detecting = _detecting.value, attached = attached, visible = attached && windowVisible)
        val openApp = PendingIntent.getActivity(
            this, REQUEST_OPEN_APP,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val showNow = servicePendingIntent(REQUEST_SHOW, ACTION_SHOW)
        val title = when (notice) {
            OverlayNotice.Shown -> overlayShownLine(
                deckPart = shownDeckPart(),
                bucketPart = listBucket?.let(::bucketShortLabel),
                state = getString(R.string.overlay_state_shown),
            )
            OverlayNotice.Closed -> getString(R.string.overlay_state_closed)
            OverlayNotice.Manual -> getString(R.string.overlay_state_on)
        }
        // 감지 중에 창이 닫혀 있으면 게임 연동 상태('판 종료 확인 중'·'6등 −35 LP')를 한 줄 덧붙인다.
        val detail = if (notice == OverlayNotice.Closed) GameSession.state.value.describe(detecting = true) else null

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_overlay)
            .setContentTitle(title)
            .apply { detail?.let { setContentText(it) } }
            // 창이 없을 때는 본문 한 번 누름이 곧 '지금 보이기'다 — 게임을 떠나지 않고 되살린다(S2).
            .setContentIntent(if (notice.bodyShowsOverlay) showNow else openApp)
            .apply {
                notice.actions.forEach { action ->
                    val label: Int
                    val intent: PendingIntent
                    when (action) {
                        OverlayNoticeAction.Hide -> {
                            label = R.string.overlay_action_hide
                            intent = servicePendingIntent(REQUEST_STOP, ACTION_STOP)
                        }
                        OverlayNoticeAction.Close -> {
                            label = R.string.overlay_action_close
                            intent = servicePendingIntent(REQUEST_STOP, ACTION_STOP)
                        }
                        OverlayNoticeAction.ShowNow -> {
                            label = R.string.overlay_action_show_now
                            intent = showNow
                        }
                        OverlayNoticeAction.OpenApp -> {
                            label = R.string.overlay_action_open_app
                            intent = openApp
                        }
                        OverlayNoticeAction.PauseWatch -> {
                            label = R.string.overlay_action_pause_watch
                            intent = servicePendingIntent(REQUEST_STOP_WATCH, ACTION_STOP_WATCH)
                        }
                    }
                    addAction(Notification.Action.Builder(null, getString(label), intent).build())
                }
            }
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun servicePendingIntent(requestCode: Int, action: String): PendingIntent = PendingIntent.getService(
        this, requestCode,
        Intent(this, OverlayService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** 알림에서 창 내용을 가리키는 조각: 고른 덱 별칭, 없으면 칩과 같은 '덱 12'. */
    private fun shownDeckPart(): String? =
        memory.selectedDeckId.value?.let { id -> overlayData.value?.decks?.firstOrNull { it.id == id }?.displayAlias }
            ?: listCount?.let { getString(R.string.overlay_deck_count, it) }

    private fun updateNotification() {
        if (!foregroundStarted) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        runCatching { manager.notify(NOTIFICATION_ID, buildNotification()) }
    }

    override fun onDestroy() {
        stopDetection()
        mainHandler.removeCallbacks(dismissRecheck)
        endSearch(apply = false)
        closeMenu(apply = false)
        OverlayState.running.value = false
        overlayRoot?.let { root -> runCatching { windowManager.removeView(root) } }
        overlayView?.disposeComposition()
        overlayView = null
        overlayRoot = null
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private data class VisibilityInput(
        val appVisible: Boolean,
        val game: GameState,
        val autoMode: Boolean,
        val showOutsideTft: Boolean,
    )

    companion object {
        private const val CHANNEL_ID = "overlay"
        private const val NOTIFICATION_ID = 42
        private const val PREFS = "overlay_position"
        private const val KEY_X = "x"
        private const val KEY_Y = "y"
        private const val KEY_WIDE = "wide"
        private const val KEY_SHOW_PROFILE = "show_profile"
        private const val KEY_USER_OVERLAY = "user_overlay"
        private const val DEFAULT_TOP_MARGIN = 120

        /** 알림 PendingIntent 요청 코드. 서로 다른 동작이 같은 PendingIntent 로 합쳐지지 않게 나눈다(3 은 판 결과 알림이 쓴다). */
        private const val REQUEST_OPEN_APP = 0
        private const val REQUEST_STOP = 1
        private const val REQUEST_SHOW = 2
        private const val REQUEST_STOP_WATCH = 4

        /** 저장된 자리를 되찾을 때 창 크기를 모르는 동안 화면 안에 남겨 둘 칩 앞머리(dp). */
        private const val MIN_VISIBLE_DP = 48

        /** 화면 영역을 다시 묻지 않고 쓰는 시간. 끄는 동안 이벤트마다 시스템에 묻지 않게. */
        private const val AREA_CACHE_MS = 300L

        /** 닫은 창의 차단이 풀린 뒤 다시 판단할 때 더 기다리는 여유. */
        private const val DISMISS_RECHECK_SLACK_MS = 1_000L

        const val ACTION_STOP = "com.tftdeck.reader.STOP_OVERLAY"
        const val ACTION_WATCH = "com.tftdeck.reader.WATCH_TFT"
        const val ACTION_STOP_WATCH = "com.tftdeck.reader.STOP_WATCH_TFT"
        const val ACTION_DISABLE_WATCH = "com.tftdeck.reader.DISABLE_WATCH_TFT"

        /** 알림의 '지금 보이기'. 감지 중에 닫거나 숨은 창을 곧바로 보이게 한다. */
        const val ACTION_SHOW = "com.tftdeck.reader.SHOW_OVERLAY"
        const val EXTRA_DECK_ID = "deck_id"
        const val EXTRA_OPEN_DECK = "open_deck"

        private val _detecting = MutableStateFlow(false)

        /** TFT 감지가 실제로 돌고 있는지. 설정 화면이 서비스를 다시 켜야 하는지 판단할 때 쓴다. */
        val detecting: StateFlow<Boolean> = _detecting.asStateFlow()

        /** 게임 연동 상태. OverlayContent의 GameStatusBadge와 설정 화면이 읽는다. */
        val gameStatus: StateFlow<GameStatus> get() = GameSession.state

        /** '다른 앱 위에 표시' 권한이 있는지. 없으면 창을 붙일 수 없다. */
        fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

        fun start(context: Context, deckId: String? = null) {
            val intent = Intent(context, OverlayService::class.java).apply {
                deckId?.let { putExtra(EXTRA_DECK_ID, it) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, OverlayService::class.java).setAction(ACTION_STOP)
            )
        }

        /** 게임 연동 시작. 창은 붙이지 않고 TFT 감지만 돌린다. 앱 화면이 보일 때 불러야 한다. */
        fun startWatch(context: Context) {
            val intent = Intent(context, OverlayService::class.java).setAction(ACTION_WATCH)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        /** 게임 연동 중지. 사용자가 켠 오버레이 창은 남긴다. */
        fun stopWatch(context: Context) {
            runCatching {
                context.startService(Intent(context, OverlayService::class.java).setAction(ACTION_STOP_WATCH))
            }
        }

        /** 권한 설정 화면으로 보내는 인텐트. */
        fun permissionIntent(context: Context): Intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:${context.packageName}"),
        )
    }
}

/** 오버레이가 그릴 덱 전체와, 아이콘 URL을 만들 때 쓸 접두사. */
data class OverlayData(
    val decks: List<com.tftdeck.reader.data.Deck>,
    val assetBase: String,
)

// -- 가시성·알림 규칙(순수 함수) ------------------------------------------------------

/**
 * 창을 보일지. 앱 화면이 보이면 숨긴다(같은 정보가 앱을 가린다). 자동 표시면 TFT 가 앞에 있을 때만 보이고('TFT 밖에서도 표시'를
 * 켜면 계속), 알림의 '지금 보이기'([forceVisible])는 그 규칙을 잠깐 넘는다.
 */
internal fun overlayVisible(
    appVisible: Boolean,
    autoMode: Boolean,
    inTft: Boolean,
    showOutsideTft: Boolean,
    forceVisible: Boolean,
): Boolean = !appVisible && (!autoMode || inTft || showOutsideTft || forceVisible)

/** '지금 보이기'가 풀리는지: TFT 가 앞에 오거나 떠났거나, 앱 화면이 새로 열렸다. */
internal fun forceVisibleExpires(wasInTft: Boolean, inTft: Boolean, wasAppVisible: Boolean, appVisible: Boolean): Boolean =
    wasInTft != inTft || (appVisible && !wasAppVisible)

/** 자동 표시가 꺼졌을 때(또는 감지를 멈췄을 때) 떼야 하는 창인지(S8): 자동으로 붙었고 사용자가 띄운 창이 아니다. */
internal fun detachAutoWindow(autoMode: Boolean, autoAttached: Boolean, userOverlay: Boolean): Boolean =
    !autoMode && autoAttached && !userOverlay

/** 오버레이 알림이 말하는 창 상태(S7). */
internal enum class OverlayNotice {
    /** 감지 중이고 창이 보인다. */
    Shown,

    /** 감지 중인데 창이 닫혔거나 숨었다. 본문을 누르면 창을 되살린다. */
    Closed,

    /** 감지 없이 직접 띄운 창. */
    Manual;

    /** 알림 동작 버튼(왼쪽부터). */
    val actions: List<OverlayNoticeAction>
        get() = when (this) {
            Shown -> listOf(OverlayNoticeAction.Hide, OverlayNoticeAction.OpenApp, OverlayNoticeAction.PauseWatch)
            Closed -> listOf(OverlayNoticeAction.ShowNow, OverlayNoticeAction.OpenApp, OverlayNoticeAction.PauseWatch)
            Manual -> listOf(OverlayNoticeAction.Close, OverlayNoticeAction.OpenApp)
        }

    /** 본문을 누르면 창을 되살리는지(아니면 앱을 연다). */
    val bodyShowsOverlay: Boolean get() = this == Closed
}

internal enum class OverlayNoticeAction { Hide, ShowNow, OpenApp, Close, PauseWatch }

internal fun overlayNotice(detecting: Boolean, attached: Boolean, visible: Boolean): OverlayNotice = when {
    !detecting -> OverlayNotice.Manual
    attached && visible -> OverlayNotice.Shown
    else -> OverlayNotice.Closed
}

/** '덱 12 · 골드~에메 · 오버레이 표시 중'. 모르는 조각은 뺀다. */
internal fun overlayShownLine(deckPart: String?, bucketPart: String?, state: String): String =
    listOfNotNull(deckPart?.takeIf { it.isNotBlank() }, bucketPart?.takeIf { it.isNotBlank() }, state).joinToString(" · ")
