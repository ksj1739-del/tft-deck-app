package com.tftdeck.reader

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tftdeck.reader.data.SearchAxis
import com.tftdeck.reader.ingame.IngamePrefs
import com.tftdeck.reader.ingame.hasUsageStatsPermission
import com.tftdeck.reader.overlay.OverlayService
import com.tftdeck.reader.ui.components.FirstRunDialog
import com.tftdeck.reader.overlay.OverlayState
import com.tftdeck.reader.ui.AppViewModel
import com.tftdeck.reader.ui.codex.AugmentDetailScreen
import com.tftdeck.reader.ui.codex.ChampionDetailScreen
import com.tftdeck.reader.ui.codex.CodexScreen
import com.tftdeck.reader.ui.codex.CodexViewModel
import com.tftdeck.reader.ui.codex.ItemDetailScreen
import com.tftdeck.reader.ui.codex.TraitDetailScreen
import com.tftdeck.reader.ui.screens.DeckDetailScreen
import com.tftdeck.reader.ui.screens.DeckListScreen
import com.tftdeck.reader.ui.screens.SearchScreen
import com.tftdeck.reader.ui.screens.SettingsScreen
import com.tftdeck.reader.ui.theme.TftDeckTheme

class MainActivity : ComponentActivity() {

    // 오버레이에서 '앱에서 보기'로 들어온 덱. 첫 화면 대신 그 덱 상세로 간다.
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
                AppRoot(pendingDeck)
            }
        }
    }

    // 앱 화면이 보이는 동안 오버레이는 숨는다. 게임으로 돌아가면 다시 나타난다.
    override fun onStart() {
        super.onStart()
        OverlayState.appVisible.value = true
        // 게임 연동을 켜 두었는데 기기 재시작·프로세스 종료로 감지가 멈췄으면 다시 켠다.
        // 포그라운드 서비스는 앱 화면이 보일 때만 시작할 수 있어 TftApp.onCreate 가 아니라 여기서 한다.
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

private sealed class Tab(val route: String, val label: String) {
    data object Decks : Tab("decks", "덱")
    data object Codex : Tab("codex", "도감")
    data object Search : Tab("search", "검색")
    data object Settings : Tab("settings", "내 정보")
}

private val TABS = listOf(Tab.Decks, Tab.Codex, Tab.Search, Tab.Settings)

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

/** 검색 후보의 '도감' 버튼이 여는 라우트. 조합 재료 축은 도감 버튼을 달지 않는다. */
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
private fun AppRoot(pendingDeck: MutableState<String?>) {
    val viewModel: AppViewModel = viewModel()
    // 도감 목록·상세 라우트가 함께 쓴다. 액티비티 범위라 탭을 오가도 필터·검색어가 유지된다.
    val codexViewModel: CodexViewModel = viewModel()
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val context = LocalContext.current

    val snackbar = remember { SnackbarHostState() }
    val syncMessage by viewModel.syncMessage.collectAsState()

    // 실제로 떠 있는지는 서비스가 알린다. 화면 변수로 들고 있으면 앱을 새로 열 때 꺼짐으로 초기화된다.
    val overlayRunning by OverlayState.running.collectAsState()

    // 설치 뒤 첫 실행에만: 전적 연결·오버레이 권한 중 빠진 것이 있으면 묻는다. 둘 다 돼 있으면 조용히 넘어간다.
    // 필요 여부는 처음 한 번만 본다 — 안내 도중 하나를 마쳐도 창이 사라지지 않고 확인 표시로 바뀐다.
    val firstRunPending by viewModel.firstRunPending.collectAsState()
    if (firstRunPending) {
        val needsSetup = remember {
            !viewModel.savedRiotId.value.contains("#") || !OverlayService.canDrawOverlays(context)
        }
        if (needsSetup) {
            FirstRunDialog(viewModel, onDone = viewModel::finishFirstRun)
        } else {
            LaunchedEffect(Unit) { viewModel.finishFirstRun() }
        }
    }

    // Android 13+ 는 포그라운드 서비스 알림을 띄우려면 알림 권한이 필요하다.
    val notificationPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 거부해도 오버레이 자체는 동작한다. */ }

    // 오버레이에서 넘어온 덱이 있으면 상세로 보낸다. 한 번 처리하면 비운다.
    LaunchedEffect(pendingDeck.value) {
        pendingDeck.value?.let { id ->
            navController.navigate("deck/$id")
            pendingDeck.value = null
        }
    }

    LaunchedEffect(syncMessage) {
        syncMessage?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeSyncMessage()
        }
    }

    fun startOverlay(deckId: String?) {
        if (!OverlayService.canDrawOverlays(context)) {
            context.startActivity(
                OverlayService.permissionIntent(context)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        OverlayService.start(context, deckId)
        // 앱이 열려 있는 동안은 숨어 있으므로, 눌렀는데 아무 일도 없는 것처럼 보이지 않게 알려 준다.
        // 게임 연동의 자동 표시를 켜 두었으면 TFT가 앞에 있을 때만 보이므로 안내도 그에 맞춘다.
        val ingame = IngamePrefs.get(context)
        val onlyInTft = ingame.detectEnabled.value && hasUsageStatsPermission(context) &&
            ingame.autoOverlay.value && !ingame.showOutsideTft.value
        val hint = if (onlyInTft) "TFT가 앞에 오면 게임 위에 나타납니다" else "앱을 나가면 게임 위에 나타납니다"
        android.widget.Toast.makeText(context, hint, android.widget.Toast.LENGTH_SHORT).show()
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
                            route == Tab.Search.route -> "검색"
                            route == Tab.Settings.route -> "내 정보"
                            else -> stringResourceSafe(context, R.string.app_name)
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    if (isDetail) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                        }
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

                composable(Tab.Search.route) {
                    SearchScreen(
                        viewModel = viewModel,
                        onOpenDeck = openDeck,
                        onOpenCodex = { axis, id -> codexRouteFor(axis, id)?.let { navController.navigate(it) } },
                    )
                }

                composable(Tab.Settings.route) {
                    SettingsScreen(
                        viewModel = viewModel,
                        overlayRunning = overlayRunning,
                        onToggleOverlay = { on ->
                            if (on) {
                                // 설정에서 켜면 전체 덱 목록으로 시작한다.
                                // 덱 하나를 바로 열려면 덱 상세의 '게임 위에 띄우기'를 쓴다.
                                startOverlay(null)
                            } else {
                                OverlayService.stop(context)
                            }
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
                        onStartOverlay = { id -> startOverlay(id) },
                        initialVariant = entry.arguments?.getString("variant"),
                        onOpenDeck = openDeck,
                    )
                }
            }
        }
    }
}

private fun tabIcon(tab: Tab) = when (tab) {
    Tab.Decks -> Icons.AutoMirrored.Filled.ViewList
    Tab.Codex -> Icons.AutoMirrored.Filled.MenuBook
    Tab.Search -> Icons.Default.Search
    Tab.Settings -> Icons.Default.Person
}

private fun stringResourceSafe(context: Context, id: Int): String = context.getString(id)
