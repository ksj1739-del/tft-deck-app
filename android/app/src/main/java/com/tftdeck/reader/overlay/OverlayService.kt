package com.tftdeck.reader.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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

    // null이면 전체 덱 목록을 보여준다. 인게임에서 뭘 갈지 고르는 게 기본 용도라
    // 덱 하나를 고정해 두는 것보다 목록이 기본이다.
    private val selectedDeckId = MutableStateFlow<String?>(null)

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
        // 지정 없이 띄웠으면 목록에서 시작한다.
        intent.getStringExtra(EXTRA_DECK_ID)?.let {
            repository.pinnedDeckId = it
            selectedDeckId.value = it
        }
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
     * 드래그([moveBy])는 x·y 만 바꿔 여기서 합성한 플래그를 그대로 쓴다.
     */
    private fun applyWindow() {
        val root = overlayRoot ?: return
        val params = layoutParams ?: return
        root.visibility = if (windowVisible) View.VISIBLE else View.GONE
        params.flags = overlayWindowFlags(visible = windowVisible, searching = searching.value)
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
        applyWindow()
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
        if (apply) applyWindow()
    }

    // -- 창 --------------------------------------------------------------

    private fun attachOverlay() {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        windowVisible = true
        searching.value = false
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
            // 기본 위치는 좌상단. 이후 드래그한 자리를 기억한다.
            x = prefs.getInt(KEY_X, 0)
            y = prefs.getInt(KEY_Y, DEFAULT_TOP_MARGIN)
            // 검색 중 키보드가 떠도 창을 밀거나 줄이지 않는다.
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
                    selectedIdFlow = selectedDeckId,
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
                        selectedDeckId.value = id
                        if (id != null) repository.pinnedDeckId = id
                    },
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
     * 창을 닫는다. 감지 중이면 서비스는 남겨 두고, 이번 TFT 전면 구간에는 다시 띄우지 않는다.
     * 감지 중이 아니면 기존처럼 서비스를 끝낸다.
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

    /** 드래그. 자리만 바꾸고 플래그는 [applyWindow] 가 합성해 둔 값을 그대로 쓴다(검색 중이어도 풀리지 않는다). */
    private fun moveBy(dx: Float, dy: Float) {
        val params = layoutParams ?: return
        val root = overlayRoot ?: return
        params.x = (params.x + dx).toInt().coerceAtLeast(0)
        params.y = (params.y + dy).toInt().coerceAtLeast(0)
        runCatching { windowManager.updateViewLayout(root, params) }
    }

    private fun persistPosition() {
        val params = layoutParams ?: return
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_X, params.x)
            .putInt(KEY_Y, params.y)
            .apply()
    }

    /** 보고 있던 덱을 앱에서 그대로 이어서 연다. 목록이었으면 그냥 앱만 연다. */
    private fun openApp() {
        // 앱이 앞에 오면 창이 숨지만, 그 전에 포커스를 먼저 돌려준다.
        endSearch()
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .apply { selectedDeckId.value?.let { putExtra(EXTRA_OPEN_DECK, it) } }
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

        val deckText = selectedDeckId.value
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

        const val ACTION_STOP = "com.tftdeck.reader.STOP_OVERLAY"
        const val ACTION_WATCH = "com.tftdeck.reader.WATCH_TFT"
        const val ACTION_STOP_WATCH = "com.tftdeck.reader.STOP_WATCH_TFT"
        const val ACTION_DISABLE_WATCH = "com.tftdeck.reader.DISABLE_WATCH_TFT"
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
