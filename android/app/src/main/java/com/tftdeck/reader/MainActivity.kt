package com.tftdeck.reader

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tftdeck.reader.data.SearchAxis
import com.tftdeck.reader.ingame.IngamePrefs
import com.tftdeck.reader.ingame.TFT_PACKAGE
import com.tftdeck.reader.ingame.hasUsageStatsPermission
import com.tftdeck.reader.overlay.OverlayService
import com.tftdeck.reader.overlay.OverlayState
import com.tftdeck.reader.ui.AppViewModel
import com.tftdeck.reader.ui.LaunchStep
import com.tftdeck.reader.ui.LaunchTarget
import com.tftdeck.reader.ui.OverlayLaunch
import com.tftdeck.reader.ui.SetupViewModel
import com.tftdeck.reader.ui.codex.AugmentDetailScreen
import com.tftdeck.reader.ui.codex.ChampionDetailScreen
import com.tftdeck.reader.ui.codex.CodexScreen
import com.tftdeck.reader.ui.codex.CodexViewModel
import com.tftdeck.reader.ui.codex.ItemDetailScreen
import com.tftdeck.reader.ui.codex.TraitDetailScreen
import com.tftdeck.reader.ui.components.FirstRunDialog
import com.tftdeck.reader.ui.firstRunNeeded
import com.tftdeck.reader.ui.ingame.needsNotificationPermission
import com.tftdeck.reader.ui.nextLaunchStep
import com.tftdeck.reader.ui.screens.DeckDetailScreen
import com.tftdeck.reader.ui.screens.DeckListScreen
import com.tftdeck.reader.ui.screens.SettingsScreen
import com.tftdeck.reader.ui.theme.TftDeckTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // 오버레이에서 '앱에서 열기'로 들어온 덱. 첫 화면 대신 그 덱 상세로 간다.
    private val pendingDeck = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 화면 회전·다크 모드 전환으로 다시 만들어질 때는 같은 인텐트가 남아 있어도 읽지 않는다.
        // 복원된 내비게이션 스택 맨 위가 이미 그 덱이라, 다시 열면 같은 상세가 두 번 쌓인다.
        if (savedInstanceState == null) {
            pendingDeck.value = intent?.getStringExtra(OverlayService.EXTRA_OPEN_DECK)
        }
        setContent {
            TftDeckTheme {
                // 오버레이에서 연 상세의 뒤로 가기: 앱의 이전 화면이 아니라 게임으로 돌아간다(N15a).
                AppRoot(pendingDeck, onLeaveApp = { moveTaskToBack(true) })
            }
        }
    }

    // 앱 화면이 보이는 동안 오버레이는 숨는다. 게임으로 돌아가면 다시 나타난다.
    override fun onStart() {
        super.onStart()
        OverlayState.appVisible.value = true
        // 게임 연동을 켜 두었는데 프로세스 종료 등으로 감지가 멈췄으면 다시 켠다.
        // 재부팅·앱 업데이트 뒤에는 BootReceiver 가 앱을 열지 않아도 다시 켠다(S3).
        val ingame = IngamePrefs.get(this)
        if (ingame.detectEnabled.value && hasUsageStatsPermission(this) && !OverlayService.detecting.value) {
            OverlayService.startWatch(this)
        }
    }

    override fun onStop() {
        OverlayState.appVisible.value = false
        super.onStop()
    }

    // launchMode=singleTask 라 이미 떠 있으면 onCreate 가 아니라 여기로 온다.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(OverlayService.EXTRA_OPEN_DECK)?.let { pendingDeck.value = it }
    }
}

/** 하단 탭. 검색 탭은 덱 목록의 검색 줄로 합쳤다(사용자 결정 1). */
private sealed class Tab(val route: String, val label: String) {
    data object Decks : Tab("decks", "덱")
    data object Codex : Tab("codex", "도감")
    data object Settings : Tab("settings", "내 정보")
}

private val TABS = listOf(Tab.Decks, Tab.Codex, Tab.Settings)

/** 덱 상세. variant 는 목록의 변형 행에서 들어올 때만 붙는다(오버레이의 "deck/{id}"도 그대로 맞는다). */
private const val DETAIL_ROUTE = "deck/{deckId}?variant={variant}"
private const val CODEX_CHAMPION_ROUTE = "codex/champion/{id}"
private const val CODEX_TRAIT_ROUTE = "codex/trait/{id}"
private const val CODEX_ITEM_ROUTE = "codex/item/{id}"
private const val CODEX_AUGMENT_ROUTE = "codex/augment/{id}"

/** 하단 바를 숨기고 뒤로 버튼을 다는 상세 화면: 덱 상세와 도감 상세(codex/종류/id). */
private fun isDetailRoute(route: String?): Boolean =
    route != null && (route.startsWith("deck/") || isCodexDetailRoute(route))

private fun isCodexDetailRoute(route: String?): Boolean =
    route != null && route.startsWith("codex/") && route.count { it == '/' } >= 2

/**
 * 검색 후보의 '도감' 버튼이 여는 라우트. 조합 재료 축은 도감 버튼을 달지 않는다.
 * 검색 탭을 없앤 뒤로는 부르는 곳이 없다. 목록 검색 줄(DeckListScreen)이 도감 바로가기를 받게 되면 이 함수로 잇는다.
 */
@Suppress("unused")
private fun codexRouteFor(axis: SearchAxis, id: String): String? {
    val encoded = Uri.encode(id)
    return when (axis) {
        SearchAxis.CHAMPION -> "codex/champion/$encoded"
        SearchAxis.TRAIT -> "codex/trait/$encoded"
        SearchAxis.ITEM -> "codex/item/$encoded"
        SearchAxis.AUGMENT -> "codex/augment/$encoded"
        SearchAxis.COMPONENT -> null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(pendingDeck: MutableState<String?>, onLeaveApp: () -> Unit) {
    val viewModel: AppViewModel = viewModel()
    // 도감 목록·상세 라우트가 함께 쓴다. 액티비티 범위라 탭을 오가도 필터·검색어가 유지된다.
    val codexViewModel: CodexViewModel = viewModel()
    // '게임 위에 띄우기' 시트와 첫 동기화 알림. 액티비티 범위.
    val setup: SetupViewModel = viewModel()
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val snackbar = remember { SnackbarHostState() }
    val syncMessage by viewModel.syncMessage.collectAsState()
    val syncNotice by setup.syncNotice.collectAsState()

    // 실제로 떠 있는지는 서비스가 알린다. 화면 변수로 들고 있으면 앱을 새로 열 때 꺼짐으로 초기화된다.
    val overlayRunning by OverlayState.running.collectAsState()

    // 설치 뒤 첫 실행에만: 전적 연결·오버레이 권한 중 빠진 것이 있으면 묻는다. 둘 다 돼 있으면 조용히 넘어간다.
    // 필요 여부는 처음 한 번만 본다 — 안내 도중 하나를 마쳐도 창이 사라지지 않고 확인 표시로 바뀐다.
    val firstRunPending by viewModel.firstRunPending.collectAsState()
    if (firstRunPending) {
        val needsSetup = remember {
            firstRunNeeded(viewModel.savedRiotId.value, OverlayService.canDrawOverlays(context))
        }
        if (needsSetup) {
            FirstRunDialog(viewModel, onClose = { remindLater ->
                viewModel.finishFirstRun()
                if (remindLater) scope.launch { snackbar.showSnackbar("내 정보 탭에서 언제든 설정할 수 있습니다") }
            })
        } else {
            LaunchedEffect(Unit) { viewModel.finishFirstRun() }
        }
    }

    // 오버레이에서 연 덱 상세(뒤로 가면 게임으로 돌아갈 항목). 그 상세에서 다른 화면으로 가면 평소 뒤로 가기다.
    var overlayEntryId by rememberSaveable { mutableStateOf<String?>(null) }

    // 오버레이에서 넘어온 덱이 있으면 상세로 보낸다. 한 번 처리하면 비운다.
    LaunchedEffect(pendingDeck.value) {
        pendingDeck.value?.let { id ->
            navController.navigate("deck/$id")
            overlayEntryId = navController.currentBackStackEntry?.id
            pendingDeck.value = null
        }
    }
    val fromOverlay = overlayEntryId != null && backStack?.id == overlayEntryId
    fun backToGame() {
        overlayEntryId = null
        onLeaveApp()
    }

    LaunchedEffect(syncMessage) {
        syncMessage?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeSyncMessage()
        }
    }

    // 설치 직후 앱에 담긴 데이터를 쓰다가 첫 새로고침이 끝나면 알린다(N3). 직접 누른 새로고침은 위 syncMessage 가 알린다.
    LaunchedEffect(syncNotice) {
        syncNotice?.let {
            if (!viewModel.syncing.value) snackbar.showSnackbar(it)
            setup.consumeSyncNotice()
        }
    }

    // -- 게임 위에 띄우기 -------------------------------------------------------
    // 덱 탭 앱바의 오버레이 버튼, 덱 상세의 버튼, 내 정보의 오버레이 스위치가 모두 같은 시트를 연다(N4, N5).
    // 권한이 없으면 설정으로 보냈다가 돌아와서(ON_RESUME) 권한이 생겼으면 이어서 띄운다. 알림 권한은 시트 안에서 묻는다.

    val overlaySheet by setup.overlaySheet.collectAsState()
    var overlayGranted by remember { mutableStateOf(OverlayService.canDrawOverlays(context)) }
    var notificationAsked by remember { mutableStateOf(false) }
    var afterNotification by remember { mutableStateOf<OverlayLaunch?>(null) }

    fun launchOverlay(launch: OverlayLaunch) {
        setup.closeOverlaySheet()
        OverlayService.start(context, launch.deckId)
        when (launch.target) {
            LaunchTarget.Tft -> {
                val tft = context.packageManager.getLaunchIntentForPackage(TFT_PACKAGE)
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (tft == null || runCatching { context.startActivity(tft) }.isFailure) onLeaveApp()
            }
            LaunchTarget.Home -> onLeaveApp()
        }
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // 거부해도 오버레이는 뜬다. 닫은 창을 알림에서 되살릴 수 없을 뿐이다.
        afterNotification?.let { launch ->
            afterNotification = null
            launchOverlay(launch)
        }
    }

    fun requestLaunch(launch: OverlayLaunch) {
        overlayGranted = OverlayService.canDrawOverlays(context)
        when (nextLaunchStep(overlayGranted, !needsNotificationPermission(context), notificationAsked)) {
            LaunchStep.OverlayPermission -> {
                setup.awaitOverlayPermission(launch)
                context.startActivity(OverlayService.permissionIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            LaunchStep.NotificationPermission -> {
                notificationAsked = true
                afterNotification = launch
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            LaunchStep.Start -> launchOverlay(launch)
        }
    }

    val onResumed by rememberUpdatedState(newValue = {
        overlayGranted = OverlayService.canDrawOverlays(context)
        setup.resumeLaunch(overlayGranted)?.let { requestLaunch(it) }
    })
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onResumed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    overlaySheet?.let { sheet ->
        // 시트를 새로 열 때마다 알림 권한을 한 번 물을 수 있다.
        LaunchedEffect(sheet) { notificationAsked = false }
        val ingame = IngamePrefs.get(context)
        val detect by ingame.detectEnabled.collectAsState()
        val auto by ingame.autoOverlay.collectAsState()
        val outside by ingame.showOutsideTft.collectAsState()
        val tftInstalled = remember(sheet) { context.packageManager.getLaunchIntentForPackage(TFT_PACKAGE) != null }
        OverlayLaunchSheet(
            deckName = sheet.deckId?.let { id -> viewModel.deck(id)?.displayAlias ?: id },
            running = overlayRunning,
            overlayGranted = overlayGranted,
            notificationsGranted = !needsNotificationPermission(context),
            tftInstalled = tftInstalled,
            onlyInTft = detect && auto && !outside && hasUsageStatsPermission(context),
            onLaunch = { target -> requestLaunch(OverlayLaunch(sheet.deckId, target)) },
            onStop = {
                setup.closeOverlaySheet()
                OverlayService.stop(context)
            },
            onDismiss = setup::closeOverlaySheet,
        )
    }

    val openDeck: (String) -> Unit = { id -> navController.navigate("deck/$id") }
    val isDetail = isDetailRoute(route)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            route?.startsWith("deck/") == true -> "덱 상세"
                            isCodexDetailRoute(route) -> "도감"
                            route == Tab.Codex.route -> "도감"
                            route == Tab.Settings.route -> "내 정보"
                            // 덱 탭도 탭 이름을 쓴다(L10). 앱 이름은 런처·알림에만 둔다.
                            else -> Tab.Decks.label
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    if (isDetail) {
                        IconButton(onClick = { if (fromOverlay) backToGame() else navController.popBackStack() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = if (fromOverlay) "게임으로 돌아가기" else "뒤로",
                            )
                        }
                    }
                },
                actions = {
                    if (route == Tab.Decks.route) {
                        OverlayButton(running = overlayRunning, onClick = { setup.openOverlaySheet(null) })
                    }
                },
            )
        },
        bottomBar = {
            if (!isDetail) {
                NavigationBar {
                    TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(Tab.Decks.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tabIcon(tab), contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            NavHost(navController = navController, startDestination = Tab.Decks.route) {

                composable(Tab.Decks.route) {
                    DeckListScreen(
                        viewModel = viewModel,
                        onOpenDeck = openDeck,
                        onOpenVariant = { deckId, variantId ->
                            navController.navigate("deck/$deckId?variant=${Uri.encode(variantId)}")
                        },
                    )
                }

                // -- 도감 ------------------------------------------------------------
                // 목록과 상세 네 종류가 codexViewModel 하나를 함께 쓴다. 상세에서 돌아와도 필터·검색어가 남도록.

                composable(Tab.Codex.route) {
                    CodexScreen(
                        viewModel = codexViewModel,
                        onOpenChampion = { navController.navigate("codex/champion/${Uri.encode(it)}") },
                        onOpenTrait = { navController.navigate("codex/trait/${Uri.encode(it)}") },
                        onOpenItem = { navController.navigate("codex/item/${Uri.encode(it)}") },
                        onOpenAugment = { navController.navigate("codex/augment/${Uri.encode(it)}") },
                    )
                }

                composable(
                    route = CODEX_CHAMPION_ROUTE,
                    arguments = listOf(navArgument("id") { type = NavType.StringType }),
                ) { entry ->
                    ChampionDetailScreen(
                        id = entry.arguments?.getString("id").orEmpty(),
                        viewModel = codexViewModel,
                        onOpenDeck = openDeck,
                        onOpenItem = { navController.navigate("codex/item/${Uri.encode(it)}") },
                    )
                }

                composable(
                    route = CODEX_TRAIT_ROUTE,
                    arguments = listOf(navArgument("id") { type = NavType.StringType }),
                ) { entry ->
                    TraitDetailScreen(
                        id = entry.arguments?.getString("id").orEmpty(),
                        viewModel = codexViewModel,
                        onOpenDeck = openDeck,
                        onOpenChampion = { navController.navigate("codex/champion/${Uri.encode(it)}") },
                    )
                }

                composable(
                    route = CODEX_ITEM_ROUTE,
                    arguments = listOf(navArgument("id") { type = NavType.StringType }),
                ) { entry ->
                    ItemDetailScreen(
                        id = entry.arguments?.getString("id").orEmpty(),
                        viewModel = codexViewModel,
                        onOpenDeck = openDeck,
                        onOpenChampion = { navController.navigate("codex/champion/${Uri.encode(it)}") },
                    )
                }

                composable(
                    route = CODEX_AUGMENT_ROUTE,
                    arguments = listOf(navArgument("id") { type = NavType.StringType }),
                ) { entry ->
                    AugmentDetailScreen(
                        id = entry.arguments?.getString("id").orEmpty(),
                        viewModel = codexViewModel,
                        onOpenDeck = openDeck,
                    )
                }

                composable(Tab.Settings.route) {
                    SettingsScreen(
                        viewModel = viewModel,
                        overlayRunning = overlayRunning,
                        onToggleOverlay = { on ->
                            // 켜면 전체 덱 목록으로 시작한다(어디로 갈지는 시트에서 고른다).
                            // 덱 하나를 바로 열려면 덱 상세의 버튼을 쓴다.
                            if (on) setup.openOverlaySheet(null) else OverlayService.stop(context)
                        },
                    )
                }

                composable(
                    route = DETAIL_ROUTE,
                    arguments = listOf(
                        navArgument("deckId") { type = NavType.StringType },
                        navArgument("variant") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                    ),
                ) { entry ->
                    DeckDetailScreen(
                        deckId = entry.arguments?.getString("deckId").orEmpty(),
                        viewModel = viewModel,
                        overlayRunning = overlayRunning,
                        onStartOverlay = { id -> setup.openOverlaySheet(id) },
                        initialVariant = entry.arguments?.getString("variant"),
                        // 상세에서 다른 덱(불리한 상대 등)으로 갈 때는 지금 상세를 바꿔 끼운다. 쌓이면 목록까지 뒤로를 여러 번 눌러야 한다(N15b).
                        onOpenDeck = { id ->
                            navController.navigate("deck/$id") { popUpTo(DETAIL_ROUTE) { inclusive = true } }
                        },
                    )
                    // NavHost 의 뒤로 처리보다 나중에 등록돼 먼저 불린다. 오버레이에서 연 그 상세에서만 켠다.
                    BackHandler(enabled = overlayEntryId != null && overlayEntryId == entry.id) { backToGame() }
                }
            }
        }
    }
}

/** 덱 탭 앱바의 오버레이 버튼. 켜져 있으면 파랑 아이콘 + 점(색만으로 알리지 않는다). */
@Composable
private fun OverlayButton(running: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    IconButton(onClick = onClick) {
        BadgedBox(badge = { if (running) Badge(containerColor = scheme.primary) }) {
            Icon(
                Icons.Filled.PictureInPictureAlt,
                contentDescription = if (running) "오버레이 켜짐 · 게임 위에 띄우기" else "게임 위에 띄우기",
                tint = if (running) scheme.primary else scheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * '게임 위에 띄우기' 시트. 앱이 앞에 있는 동안 오버레이는 숨으므로, 켠 뒤 어디로 갈지까지 한 번에 고르게 한다(N4).
 * TFT 가 없으면 TFT 버튼은 숨긴다. 알림 권한은 이 시트에서 버튼을 누를 때 이유 한 줄과 함께 묻는다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverlayLaunchSheet(
    deckName: String?,
    running: Boolean,
    overlayGranted: Boolean,
    notificationsGranted: Boolean,
    tftInstalled: Boolean,
    onlyInTft: Boolean,
    onLaunch: (LaunchTarget) -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("게임 위에 띄우기", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
            Text(
                if (deckName != null) "$deckName 요약으로 시작합니다" else "덱 목록으로 시작합니다",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            if (!overlayGranted) {
                Text(
                    "먼저 '다른 앱 위에 표시' 를 허용해야 합니다 · 목록에서 FloaTFT → 허용 → 뒤로",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            if (!notificationsGranted) {
                Text(
                    "알림을 허용하면 닫은 오버레이를 알림에서 다시 띄울 수 있습니다",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            if (tftInstalled) {
                Button(
                    onClick = { onLaunch(LaunchTarget.Tft) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) { Text("TFT 열고 띄우기") }
            }
            OutlinedButton(
                onClick = { onLaunch(LaunchTarget.Home) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) { Text("홈 화면에 띄우기") }
            if (onlyInTft) {
                Text(
                    "지금은 TFT 가 앞에 있을 때만 보이게 설정돼 있습니다 · 내 정보 > 게임 연동",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            if (running) {
                TextButton(
                    onClick = onStop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) { Text("오버레이 끄기") }
            }
        }
    }
}

private fun tabIcon(tab: Tab) = when (tab) {
    Tab.Decks -> Icons.AutoMirrored.Filled.ViewList
    Tab.Codex -> Icons.AutoMirrored.Filled.MenuBook
    Tab.Settings -> Icons.Default.Person
}
