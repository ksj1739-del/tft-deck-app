package com.tftdeck.reader

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.ViewList
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tftdeck.reader.overlay.OverlayService
import com.tftdeck.reader.ui.AppViewModel
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
        pendingDeck.value = intent?.getStringExtra(OverlayService.EXTRA_OPEN_DECK)
        setContent {
            TftDeckTheme {
                AppRoot(pendingDeck)
            }
        }
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
    data object Search : Tab("search", "검색")
    data object Settings : Tab("settings", "설정")
}

private val TABS = listOf(Tab.Decks, Tab.Search, Tab.Settings)
private const val DETAIL_ROUTE = "deck/{deckId}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(pendingDeck: MutableState<String?>) {
    val viewModel: AppViewModel = viewModel()
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val context = LocalContext.current

    val snackbar = remember { SnackbarHostState() }
    val syncMessage by viewModel.syncMessage.collectAsState()

    // 오버레이는 시스템 서비스라 상태를 직접 들고 있어야 한다.
    var overlayRunning by remember { mutableStateOf(false) }

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
        overlayRunning = true
    }

    val isDetail = route == DETAIL_ROUTE

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            isDetail -> "덱 상세"
                            route == Tab.Search.route -> "검색"
                            route == Tab.Settings.route -> "설정"
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
                    DeckListScreen(viewModel) { id -> navController.navigate("deck/$id") }
                }

                composable(Tab.Search.route) {
                    SearchScreen(viewModel) { id -> navController.navigate("deck/$id") }
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
                                overlayRunning = false
                            }
                        },
                    )
                }

                composable(
                    route = DETAIL_ROUTE,
                    arguments = listOf(navArgument("deckId") { type = NavType.StringType }),
                ) { entry ->
                    DeckDetailScreen(
                        deckId = entry.arguments?.getString("deckId").orEmpty(),
                        viewModel = viewModel,
                        overlayRunning = overlayRunning,
                        onStartOverlay = { id -> startOverlay(id) },
                    )
                }
            }
        }
    }
}

private fun tabIcon(tab: Tab) = when (tab) {
    Tab.Decks -> Icons.AutoMirrored.Filled.ViewList
    Tab.Search -> Icons.Default.Search
    Tab.Settings -> Icons.Default.Settings
}

private fun stringResourceSafe(context: Context, id: Int): String = context.getString(id)
