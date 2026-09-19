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
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
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

/**
 * 게임 화면 위에 덱을 띄워 두는 오버레이.
 *
 * 다른 앱 위에 그려야 해서 일반 Activity로는 안 되고, 시스템 창(TYPE_APPLICATION_OVERLAY)을
 * WindowManager에 직접 붙인다. 그 창이 살아 있는 동안 프로세스가 죽지 않도록
 * 포그라운드 서비스로 유지한다.
 *
 * 창은 포커스를 가져가지 않는다(FLAG_NOT_FOCUSABLE). 오버레이를 띄운 채로
 * 게임을 그대로 조작할 수 있어야 하기 때문이다. 덱 목록 검색창을 누른 동안에만 그 플래그를 빼서 키보드를 띄우고,
 * 검색이 끝나면 [endSearch] 에서 반드시 되돌린다. 플래그는 [overlayWindowFlags] 한 곳에서 합성한다.
 *
 * 게임 연동을 켜면 창 없이 TFT 감지만 돌리다가(ACTION_WATCH) TFT가 앞에 오면 창을 붙인다.
 * 감지·판 종료 확인은 이 서비스가 살아 있는 동안에만 돈다 — 별도 백그라운드 작업을 두지 않는다.
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

    private val expanded = MutableStateFlow(false)
    private val overlayData = MutableStateFlow<OverlayData?>(null)

    /**
     * 펼침 화면의 보던 자리: 고른 덱(null 이면 전체 덱 목록 — 인게임에서 뭘 갈지 고르는 게 기본 용도라 목록이 기본이다),
     * 덱별 레벨, 목록 스크롤. 접기·창 떼기·서비스 재시작을 넘겨야 해서 화면이 아니라 서비스가 들고 흘려 보낸다.
     */
    private lateinit var memory: OverlayMemory

    // -- 창 자리 --------------------------------------------------------------

    /**
     * 사용자가 끌어다 놓은 자리(방향별로 저장). 펼침·넓게 보기로 커진 창이 화면 안으로 밀려났다가도
     * 접으면 이 자리로 돌아온다 — 모서리에 둔 칩이 펼칠 때마다 떠내려가지 않게.
     */
    private var anchor = OverlayPosition(0, DEFAULT_TOP_MARGIN)

    /** 지금 가로 화면인지. 자리를 방향별로 기억하는 데 쓴다. */
    private var landscape = false

    /** 검색 중 키보드를 피해 창을 올리기 전의 y. 검색이 끝나면 되돌린다. 그사이 끌어 옮기면 버린다. */
    private var preSearchY: Int? = null

    // 좁게(얼굴만) / 넓게(이름·아이템·시너지까지). 마지막 선택을 기억한다.
    private val wide = MutableStateFlow(false)

    // 티어 카드만 따로 켜고 끈다. 마지막 선택을 기억한다.
    private val showProfile = MutableStateFlow(true)

    // -- 게임 연동 ---------------------------------------------------------------

    private var detector: GameDetector? = null
    private var session: GameSession? = null
    private val detectionJobs = mutableListOf<Job>()

    /** 감지기 상태를 서비스 안에서 한 번 더 들고 있다. 감지를 껐다 켜도 구독자가 바뀌지 않게. */
    private val gameState = MutableStateFlow<GameState>(GameState.Unknown)

    /** 사용자가 직접 켠 창인지. 자동 표시로 붙은 창과 구분해야 감지를 끌 때 무엇을 닫을지 안다. */
    private var userOverlay = false

    /** 사용자가 X로 닫은 TFT 전면 구간. 같은 구간 안에서는 다시 자동으로 띄우지 않는다. */
    private var dismissedForegroundSince: Long? = null

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
        showProfile.value = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SHOW_PROFILE, true)
        // 보던 덱·레벨·목록 자리는 저장값에서 되찾는다 — 시스템이 서비스를 되살려도(START_STICKY) 이어서 본다.
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
                stopWatchMode()
                keepAliveResult()
            }
            ACTION_DISABLE_WATCH -> {
                // 알림의 '감지 끄기'. 설정 스위치도 함께 꺼야 다음에 앱을 열었을 때 다시 켜지지 않는다.
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
        dismissedForegroundSince = null

        // 덱을 지정해서 띄웠으면(덱 상세의 '게임 위에 띄우기') 그 덱을 바로 연다.
        // 지정 없이 띄웠으면(설정의 '오버레이 표시') 목록에서 시작한다. 서비스가 새로 만들어지며 저장값에서
        // 되찾은 덱이 있어도 목록이다 — 되찾은 덱은 시스템 재시작·자동 표시처럼 사용자가 새로 고르지 않은 경우에 쓴다.
        val deckId = intent.getStringExtra(EXTRA_DECK_ID)
        deckId?.let { repository.pinnedDeckId = it }
        memory.selectDeck(deckId)
        startObservers()
        if (overlayView == null) attachOverlay()

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
     * 알림의 '다시 띄우기'. 감지 중에 X 로 닫은 창을 게임을 떠나지 않고 되살린다 — 닫은 TFT 구간 동안에는 자동 표시가
     * 막혀 있어(dismissedForegroundSince) 알림 말고는 되살릴 길이 없다. 사용자가 켠 창으로 바꾸지는 않는다
     * (감지를 끄면 자동으로 붙은 창처럼 함께 닫힌다).
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
        dismissedForegroundSince = null
        startObservers()
        if (overlayView == null) attachOverlay()
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
            if (overlayView == null) attachOverlay()
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
        // 창 없이 감지만 할 때는 알림이 유일한 표시라 상태가 바뀌면 문구를 고친다.
        detectionJobs += scope.launch { GameSession.state.collect { updateNotification() } }
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
        dismissedForegroundSince = null
        _detecting.value = false
    }

    /**
     * 창을 보일지 정한다.
     * - 앱 화면이 열려 있으면 숨긴다. 같은 정보가 앱을 가리기 때문이다.
     * - 자동 표시(감지 + 'TFT가 켜지면 오버레이 자동 표시')면 TFT가 앞에 있을 때만 보인다
     *   ('TFT 밖에서도 표시'를 켜면 계속 보인다). TFT가 새로 앞에 오면 창이 없더라도 붙인다.
     * 숨길 때는 터치도 통과시켜 보이지 않는 창이 다른 앱 조작을 막지 않게 한다.
     * 가시성이 바뀌면 검색을 끝낸다 — 숨은 창이 포커스를 쥐고 있으면 안 된다.
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
        lastVisibility = input
        val inTft = input.game as? GameState.Foreground

        if (input.autoMode && inTft != null && overlayView == null &&
            dismissedForegroundSince != inTft.since && canDrawOverlays(this)
        ) {
            startObservers()
            attachOverlay() // 붙은 뒤 lastVisibility로 다시 여기를 거친다.
            updateNotification()
            return
        }

        if (overlayRoot == null || layoutParams == null) return
        val visible = !input.appVisible && (!input.autoMode || inTft != null || input.showOutsideTft)
        if (visible != windowVisible) endSearch(apply = false)
        windowVisible = visible
        applyWindow()
    }

    /**
     * 보임·검색 상태를 창에 반영한다. 플래그는 [overlayWindowFlags] 한 곳에서 합성하고,
     * 드래그([moveBy])는 x·y 만 바꿔 여기서 합성한 플래그를 그대로 쓴다. 자리도 화면 안으로 한 번 맞춘다
     * (숨어 있는 사이 회전했을 수 있다).
     */
    private fun applyWindow() {
        val root = overlayRoot ?: return
        val params = layoutParams ?: return
        root.visibility = if (windowVisible) View.VISIBLE else View.GONE
        params.flags = overlayWindowFlags(visible = windowVisible, searching = searching.value)
        clampInto(params, root, OverlayPosition(params.x, params.y))
        runCatching { windowManager.updateViewLayout(root, params) }
    }

    // -- 오버레이 검색(키보드) ---------------------------------------------------

    /**
     * 덱 목록 검색창을 눌렀을 때. 창의 FLAG_NOT_FOCUSABLE 을 빼서 포커스를 받게 한다 — 그래야 키보드가 뜬다.
     * 숨은 창에서는 켜지 않는다. 입력칸은 창이 실제로 포커스를 받은 뒤에 포커스를 잡는다(OverlayContent).
     */
    private fun startSearch() {
        if (overlayRoot == null || !windowVisible || searching.value) return
        searching.value = true
        // 키보드가 창을 가리면 창을 올린다([liftForIme]). 검색이 끝나면 이 자리로 돌아온다.
        preSearchY = layoutParams?.y
        applyWindow()
    }

    /**
     * 검색 중 키보드가 창을 가리면 창 아래가 키보드 위에 오도록 올린다(화면 위보다 위로는 안 간다, [liftAboveIme]).
     * 창은 SOFT_INPUT_ADJUST_NOTHING 이라 시스템이 밀어 주지 않는다 — 가로 화면은 키보드가 화면의 60% 남짓을 덮어
     * 기본 자리에서도 후보가 전부 가려졌고, 아래로 옮겨 둔 창은 검색창까지 가려졌다.
     *
     * 키보드 위치는 이 창의 인셋이 아니라 화면 기준(WindowMetrics)으로 잰다. 이 창의 인셋은 '창 아래가 키보드에 가린 높이'인데,
     * 창을 올린 직후에도 옛 틀 기준 값을 한 번 더 보내 와서(에뮬레이터 로그: 올린 뒤에도 820px) 그대로 빼면 필요보다 높이
     * 화면 맨 위까지 올라갔다. 키보드 인셋 변화와 창 배치는 부르는 신호로만 쓴다. API 30 미만은 올리지 않는다.
     */
    private fun liftForIme() {
        if (!searching.value || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val root = overlayRoot ?: return
        val params = layoutParams ?: return
        val metrics = windowManager.currentWindowMetrics
        val insets = metrics.windowInsets
        if (!insets.isVisible(WindowInsets.Type.ime())) return
        val imeTop = metrics.bounds.bottom - insets.getInsets(WindowInsets.Type.ime()).bottom
        val y = liftAboveIme(params.y, root.height, overlayArea().top, imeTop)
        if (y == params.y) return
        params.y = y
        runCatching { windowManager.updateViewLayout(root, params) }
    }

    /**
     * 검색을 끝내고 창을 다시 포커스를 받지 않는 상태로 되돌린다. 키보드를 먼저 내린다.
     * 부르는 곳: 후보 선택·IME 검색 키(OverlayContent) · 창 밖 누름·뒤로 가기·포커스 잃음(OverlayRootView) ·
     * 덱 고름(목록 닫힘) · 접기 · 앱 열기 · 닫기 · 가시성 변화 · 창 떼기 · 서비스 종료.
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
        // 키보드를 피해 올렸던 창을 검색 전 자리로 되돌린다(그사이 끌어 옮겼으면 그 자리 그대로). 창 반영은 applyWindow 가 한다.
        val restoreY = preSearchY
        preSearchY = null
        layoutParams?.let { params -> restoreY?.let { params.y = it } }
        if (apply) applyWindow()
    }

    // -- 창 --------------------------------------------------------------

    private fun attachOverlay() {
        windowVisible = true
        searching.value = false
        preSearchY = null
        // 이 방향에서 놓아 둔 자리. 창 크기는 붙은 뒤 첫 배치에서 알게 되므로 그때 한 번 더 화면 안으로 맞춘다.
        landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        anchor = loadAnchor()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // 포커스를 가져가지 않아야 오버레이를 띄운 채 게임을 조작할 수 있다(FLAG_NOT_FOCUSABLE).
            // 검색하는 동안에만 풀린다 — 플래그는 overlayWindowFlags 한 곳에서 합성한다.
            overlayWindowFlags(visible = true, searching = false),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // 기본 위치는 좌상단. 이후 드래그한 자리를 기억한다(방향별). 칩 크기는 첫 배치에서 알게 되어 그때 한 번 더 맞춘다.
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
                    onToggleExpand = {
                        val opening = !expanded.value
                        // 접으면 검색창이 사라지므로 검색도 끝낸다.
                        if (!opening) endSearch()
                        expanded.value = opening
                        // 펼칠 때 전적을 한 번 확인한다. 3분 안이면 저장소가 직전 결과를 돌려준다.
                        if (opening) scope.launch { refreshProfileLight(force = false) }
                        // 접으면 창이 칩 크기로 줄어든 뒤 놓아 둔 자리로 돌아간다(onWindowLayout).
                    },
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
                )
            }
        }
        // 창의 뿌리. Compose 의 창 단위 재구성기가 뿌리 뷰에서 수명 주기를 찾으므로 소유자를 여기에도 단다.
        val root = OverlayRootView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            isSearching = { searching.value }
            onEndSearch = { endSearch() }
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
                if (!_detecting.value) stopSelf()
            }
    }

    /**
     * 창을 닫는다. 감지 중이면 서비스는 남겨 두고, 이번 TFT 전면 구간에는 자동으로 다시 띄우지 않는다 —
     * 잘못 눌렀으면 알림의 '다시 띄우기'([ACTION_SHOW])로 되살린다. 감지 중이 아니면 기존처럼 서비스를 끝낸다.
     */
    private fun closeOverlay() {
        // 창을 떼기 전에 키보드를 내리고 포커스를 돌려준다(서비스 종료는 비동기라 그사이에도 포커스를 쥐지 않게).
        endSearch()
        setUserOverlay(false)
        if (_detecting.value) {
            (gameState.value as? GameState.Foreground)?.let { dismissedForegroundSince = it.since }
            detachOverlay()
            updateNotification()
        } else {
            stopSelf()
        }
    }

    private fun detachOverlay() {
        endSearch(apply = false)
        overlayRoot?.let { root -> runCatching { windowManager.removeView(root) } }
        overlayView?.disposeComposition()
        overlayView = null
        overlayRoot = null
        layoutParams = null
        expanded.value = false
        OverlayState.running.value = false
    }

    /**
     * 드래그. 자리만 바꾸고 플래그는 [applyWindow] 가 합성해 둔 값을 그대로 쓴다(검색 중이어도 풀리지 않는다).
     * 화면 밖으로는 끌려 나가지 않는다 — 예전에는 오른쪽·아래로 상한이 없어 놓치면 되찾을 수 없었다.
     */
    private fun moveBy(dx: Float, dy: Float) {
        val params = layoutParams ?: return
        // 직접 옮겼으면 검색이 끝나도 키보드를 피하기 전 자리로 되돌리지 않는다.
        preSearchY = null
        placeWindow(OverlayPosition((params.x + dx).toInt(), (params.y + dy).toInt()))
        // 끄는 동안에도 놓아 둔 자리가 손을 따라가야 그사이 다시 배치돼도(내용 변경) 옛 자리로 튀지 않는다. 저장은 끝날 때 한 번.
        anchor = OverlayPosition(params.x, params.y)
    }

    /** 드래그를 마친 자리를 이 방향의 자리로 기억한다. 끄는 동안 이미 화면 안으로 맞춘 값이다. */
    private fun persistPosition() {
        val params = layoutParams ?: return
        saveAnchor(OverlayPosition(params.x, params.y))
    }

    /**
     * 창을 다시 배치할 때마다(펼침·접힘·넓게 보기·목록↔덱·회전) 자리를 화면 안으로 맞춘다.
     * 접힌 칩은 놓아 둔 자리([anchor])를 기준으로 한다 — 펼친 창이 화면 안으로 밀렸다가 접히면 제자리로 돌아오고,
     * 칩 폭이 잠깐 늘어(판 결과 배지) 밀렸다가 줄면 다시 돌아온다. 펼친 창은 지금 자리에서 넘치는 만큼만 민다
     * (줄어도 되돌아가지 않아 머리줄 버튼이 손가락 아래에서 흔들리지 않는다).
     */
    private fun keepOnScreen() {
        val params = layoutParams ?: return
        if (searching.value) {
            // 넘치는 것만 막고, 치는 동안 후보가 늘어 창이 커졌으면 다시 키보드 위로 올린다.
            placeWindow(OverlayPosition(params.x, params.y))
            liftForIme()
            return
        }
        if (expanded.value) {
            placeWindow(OverlayPosition(params.x, params.y))
            return
        }
        placeWindow(anchor)
        // 붙거나 회전한 뒤 첫 배치에서 칩 크기를 알게 된다. 저장해 둔 자리가 이 화면에서 칩을 다 담지 못하면(해상도가
        // 바뀌었거나 다른 기기에서 복원) 맞춘 자리를 새로 저장한다 — 저장값도 늘 화면 안이다. 첫 배치에서만 한다:
        // 나중에 칩이 잠깐 넓어져(판 결과 배지) 밀린 자리까지 저장하면 칩이 조금씩 떠내려간다.
        val root = overlayRoot ?: return
        if (anchorUnchecked && root.width > 0 && root.height > 0) {
            anchorUnchecked = false
            val placed = OverlayPosition(params.x, params.y)
            if (placed != anchor) saveAnchor(placed)
        }
    }

    /** 붙은 뒤·회전 뒤 첫 배치에서 저장해 둔 자리를 칩 크기로 확인해야 하는지([keepOnScreen]). */
    private var anchorUnchecked = false

    /** [target] 을 지금 창 크기로 화면 안에 맞춰 옮긴다. 바뀐 게 없으면 창을 건드리지 않는다. */
    private fun placeWindow(target: OverlayPosition) {
        val root = overlayRoot ?: return
        val params = layoutParams ?: return
        val beforeX = params.x
        val beforeY = params.y
        clampInto(params, root, target)
        if (params.x != beforeX || params.y != beforeY) {
            runCatching { windowManager.updateViewLayout(root, params) }
        }
    }

    /** [target] 을 지금 창 크기와 화면 영역으로 맞춰 [params] 에 넣는다. 창에 반영은 부른 쪽이 한다. */
    private fun clampInto(params: WindowManager.LayoutParams, root: View, target: OverlayPosition) {
        val area = overlayArea()
        val placed = clampOverlayPosition(target, root.width, root.height, area.width(), area.height())
        params.x = placed.x
        params.y = placed.y
    }

    private var areaCache: Rect? = null
    private var areaCachedAt = 0L

    /**
     * 창 x·y 의 기준 영역(화면 좌표, px). 시스템은 이 창(TOP|START)을 화면에서 지금 보이는 시스템 바와 컷아웃을 뺀 영역의
     * 왼쪽 위를 (0,0) 으로 놓는다 — FLAG_LAYOUT_NO_LIMITS 는 그 밖으로 나가도 잘라 내지 않을 뿐 기준은 같다.
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
    private fun loadAnchor(): OverlayPosition {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val (keyX, keyY) = overlayPositionKeys(landscape)
        // 가로 자리는 새로 생긴 값이다. 아직 없으면 예전(방향 구분 없던) 자리에서 시작한다.
        val legacyX = prefs.getInt(KEY_X, 0)
        val legacyY = prefs.getInt(KEY_Y, DEFAULT_TOP_MARGIN)
        anchorUnchecked = true
        return OverlayPosition(prefs.getInt(keyX, legacyX), prefs.getInt(keyY, legacyY))
    }

    /** 창 크기를 모르는 첫 배치 전에도 칩 앞머리([MIN_VISIBLE_DP])는 화면 안에 들게 한 자리. */
    private fun initialPlacement(target: OverlayPosition): OverlayPosition {
        val minVisible = (MIN_VISIBLE_DP * resources.displayMetrics.density).toInt()
        val area = overlayArea()
        return clampOverlayPosition(target, minVisible, minVisible, area.width(), area.height())
    }

    private fun saveAnchor(position: OverlayPosition) {
        anchor = position
        val (keyX, keyY) = overlayPositionKeys(landscape)
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(keyX, position.x)
            .putInt(keyY, position.y)
            .apply()
    }

    /**
     * 회전. 창은 가로·세로마다 놓아 둔 자리로 간다(게임은 가로, 홈 화면은 세로). 새 방향에서 다시 잰 크기는
     * 곧이어 [keepOnScreen] 이 한 번 더 맞춘다. 창이 없어도(감지만 하는 중) 방향은 기억해 둔다.
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
        preSearchY = null
        anchor = loadAnchor()
        placeWindow(anchor)
    }

    /** 보고 있던 덱을 앱에서 그대로 이어서 연다. 목록이었으면 그냥 앱만 연다. */
    private fun openApp() {
        // 앱이 앞에 오면 창이 숨지만, 그 전에 포커스를 먼저 돌려준다.
        endSearch()
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .apply { memory.selectedDeckId.value?.let { putExtra(EXTRA_OPEN_DECK, it) } }
        )
    }

    // -- 알림 -------------------------------------------------------------

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
        val watchOnly = !attached && _detecting.value

        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        // 창이 떠 있으면 '중지'는 창만 닫는다. 창 없이 감지만 하는 중이면 감지를 끈다.
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, OverlayService::class.java).setAction(if (watchOnly) ACTION_DISABLE_WATCH else ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val deckText = memory.selectedDeckId.value
            ?.let { id -> overlayData.value?.decks?.firstOrNull { it.id == id }?.displayAlias }
            ?: getString(R.string.overlay_deck_list)
        val title = if (watchOnly) getString(R.string.overlay_watching) else getString(R.string.overlay_running)
        val text = when {
            watchOnly -> GameSession.state.value.describe(detecting = true)
            _detecting.value -> "$deckText · ${getString(R.string.overlay_watching)}"
            else -> deckText
        }

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_overlay)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .apply {
                // 감지만 하는 중(창을 X 로 닫았거나 아직 붙지 않음)이면 게임을 떠나지 않고 창을 되살릴 길이 이것뿐이다.
                if (watchOnly) {
                    val show = PendingIntent.getService(
                        this@OverlayService, 2,
                        Intent(this@OverlayService, OverlayService::class.java).setAction(ACTION_SHOW),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                    addAction(Notification.Action.Builder(null, getString(R.string.overlay_show_again), show).build())
                }
            }
            .addAction(
                Notification.Action.Builder(
                    null,
                    getString(if (watchOnly) R.string.overlay_watch_stop else R.string.overlay_stop),
                    stop,
                ).build()
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification() {
        if (!foregroundStarted) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        runCatching { manager.notify(NOTIFICATION_ID, buildNotification()) }
    }

    override fun onDestroy() {
        stopDetection()
        endSearch(apply = false)
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

        /** 저장된 자리를 되찾을 때 창 크기를 모르는 동안 화면 안에 남겨 둘 칩 앞머리(dp). */
        private const val MIN_VISIBLE_DP = 48

        /** 화면 영역을 다시 묻지 않고 쓰는 시간. 끄는 동안 이벤트마다 시스템에 묻지 않게. */
        private const val AREA_CACHE_MS = 300L

        const val ACTION_STOP = "com.tftdeck.reader.STOP_OVERLAY"
        const val ACTION_WATCH = "com.tftdeck.reader.WATCH_TFT"
        const val ACTION_STOP_WATCH = "com.tftdeck.reader.STOP_WATCH_TFT"
        const val ACTION_DISABLE_WATCH = "com.tftdeck.reader.DISABLE_WATCH_TFT"

        /** 알림의 '다시 띄우기'. 감지 중에 X 로 닫은 창을 되살린다. */
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
