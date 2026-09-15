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
import android.view.WindowManager
import androidx.compose.runtime.CompositionContext
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
import com.tftdeck.reader.data.ProfileState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * 게임 화면 위에 덱을 띄워 두는 오버레이.
 *
 * 다른 앱 위에 그려야 해서 일반 Activity로는 안 되고, 시스템 창(TYPE_APPLICATION_OVERLAY)을
 * WindowManager에 직접 붙인다. 그 창이 살아 있는 동안 프로세스가 죽지 않도록
 * 포그라운드 서비스로 유지한다.
 *
 * 창은 포커스를 가져가지 않는다(FLAG_NOT_FOCUSABLE). 오버레이를 띄운 채로
 * 게임을 그대로 조작할 수 있어야 하기 때문이다.
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
    private var overlayView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private val expanded = MutableStateFlow(false)
    private val overlayData = MutableStateFlow<OverlayData?>(null)

    // null이면 전체 덱 목록을 보여준다. 인게임에서 뭘 갈지 고르는 게 기본 용도라
    // 덱 하나를 고정해 두는 것보다 목록이 기본이다.
    private val selectedDeckId = MutableStateFlow<String?>(null)

    // 좁게(얼굴만) / 넓게(이름·아이템·시너지까지). 마지막 선택을 기억한다.
    private val wide = MutableStateFlow(false)

    // 티어 카드만 따로 켜고 끈다. 마지막 선택을 기억한다.
    private val showProfile = MutableStateFlow(true)

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        repository = DeckRepository.get(this)
        profiles = ProfileRepository.get(this)
        wide.value = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_WIDE, false)
        showProfile.value = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SHOW_PROFILE, true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        // 권한이 없으면 창을 붙일 수 없다. 조용히 실패하지 않고 바로 종료한다.
        if (!canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        lifecycleRegistry.currentState = Lifecycle.State.STARTED

        // 덱을 지정해서 띄웠으면(덱 상세의 '게임 위에 띄우기') 그 덱을 바로 연다.
        // 지정 없이 띄웠으면 목록에서 시작한다.
        intent?.getStringExtra(EXTRA_DECK_ID)?.let {
            repository.pinnedDeckId = it
            selectedDeckId.value = it
        }
        observeDecks()
        observeProfile()
        if (overlayView == null) attachOverlay()
        return START_STICKY
    }

    // -- 덱 --------------------------------------------------------------

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
     * 저장된 요약을 먼저 올리고, 곧이어 새로 조회한다.
     */
    private fun observeProfile() {
        scope.launch {
            profiles.load()
            profiles.refresh()
        }
    }

    // -- 창 --------------------------------------------------------------

    private fun attachOverlay() {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // 포커스를 가져가지 않아야 오버레이를 띄운 채 게임을 조작할 수 있다.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // 기본 위치는 좌상단. 이후 드래그한 자리를 기억한다.
            x = prefs.getInt(KEY_X, 0)
            y = prefs.getInt(KEY_Y, DEFAULT_TOP_MARGIN)
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
                    onToggleExpand = {
                        val opening = !expanded.value
                        expanded.value = opening
                        // 펼칠 때 전적을 한 번 확인한다. 너무 잦으면 저장소가 알아서 건너뛴다.
                        if (opening) scope.launch { profiles.refresh() }
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
                        if (next) scope.launch { profiles.refresh() }
                    },
                    // 사용자가 직접 누른 새로고침은 최소 간격을 무시한다.
                    onRefreshProfile = { scope.launch { profiles.refresh(force = true) } },
                    onSelectDeck = { id ->
                        selectedDeckId.value = id
                        if (id != null) repository.pinnedDeckId = id
                    },
                    onDrag = { dx, dy -> moveBy(dx, dy) },
                    onDragEnd = { persistPosition() },
                    onOpenApp = { openApp() },
                    onClose = { stopSelf() },
                )
            }
        }
        overlayView = view
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        runCatching { windowManager.addView(view, params) }
            .onSuccess {
                OverlayState.running.value = true
                observeAppVisibility()
            }
            .onFailure {
                // 권한이 도중에 회수된 경우. 조용히 떠 있는 척하지 않고 끝낸다.
                overlayView = null
                stopSelf()
            }
    }

    /**
     * 앱 화면이 열려 있는 동안은 오버레이를 숨긴다. 같은 정보가 앱을 가리기 때문이다.
     * 숨길 때는 터치도 통과시켜 보이지 않는 창이 앱 조작을 막지 않게 한다.
     * 앱을 나가 게임으로 돌아가면 그대로 다시 나타난다.
     */
    private fun observeAppVisibility() {
        scope.launch {
            OverlayState.appVisible.collect { appVisible ->
                val view = overlayView ?: return@collect
                val params = layoutParams ?: return@collect
                view.visibility = if (appVisible) View.GONE else View.VISIBLE
                params.flags = if (appVisible) {
                    params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                } else {
                    params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                }
                runCatching { windowManager.updateViewLayout(view, params) }
            }
        }
    }

    private fun moveBy(dx: Float, dy: Float) {
        val params = layoutParams ?: return
        val view = overlayView ?: return
        params.x = (params.x + dx).toInt().coerceAtLeast(0)
        params.y = (params.y + dy).toInt().coerceAtLeast(0)
        runCatching { windowManager.updateViewLayout(view, params) }
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

        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_overlay)
            .setContentTitle(getString(R.string.overlay_running))
            .setContentText(
                selectedDeckId.value
                    ?.let { id -> overlayData.value?.decks?.firstOrNull { it.id == id }?.name }
                    ?: getString(R.string.overlay_deck_list)
            )
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, getString(R.string.overlay_stop), stop).build())
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        OverlayState.running.value = false
        overlayView?.let { view ->
            runCatching { windowManager.removeView(view) }
            view.disposeComposition()
        }
        overlayView = null
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "overlay"
        private const val NOTIFICATION_ID = 42
        private const val PREFS = "overlay_position"
        private const val KEY_X = "x"
        private const val KEY_Y = "y"
        private const val KEY_WIDE = "wide"
        private const val KEY_SHOW_PROFILE = "show_profile"
        private const val DEFAULT_TOP_MARGIN = 120

        const val ACTION_STOP = "com.tftdeck.reader.STOP_OVERLAY"
        const val EXTRA_DECK_ID = "deck_id"
        const val EXTRA_OPEN_DECK = "open_deck"

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
