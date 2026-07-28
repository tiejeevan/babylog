package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.AIInsightsScreen
import com.example.ui.screens.BluetoothCareScreen
import com.example.ui.screens.CareChatScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.HealthGrowthScreen
import com.example.ui.screens.MoreDestination
import com.example.ui.screens.MoreHubScreen
import com.example.ui.screens.SleepSoundScreen
import com.example.ui.screens.TimelineScreen
import com.example.ui.theme.BabyCareTheme
import com.example.ui.viewmodel.BabyCareViewModel
import com.example.navigation.rememberExitBackHandler

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.notification.BabyNotificationManager

import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import androidx.compose.runtime.LaunchedEffect
import com.example.service.SleepSoundForegroundService

class MainActivity : ComponentActivity() {

    private val viewModel: BabyCareViewModel by viewModels()
    private val quickActionState = mutableStateOf<String?>(null)
    private val openSleepSoundState = mutableStateOf(false)
    private val openPeerChatState = mutableStateOf(false)
    private val openVoiceCommandsState = mutableStateOf(false)

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            // Permission result handled
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        BabyNotificationManager.createNotificationChannels(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        quickActionState.value = intent?.getStringExtra("quick_action")
        openSleepSoundState.value =
            intent?.getBooleanExtra(SleepSoundForegroundService.EXTRA_OPEN_SLEEP_SOUND, false) == true
        openPeerChatState.value =
            intent?.getBooleanExtra(BabyNotificationManager.EXTRA_OPEN_PEER_CHAT, false) == true
        openVoiceCommandsState.value =
            intent?.getBooleanExtra(BabyNotificationManager.EXTRA_OPEN_VOICE_COMMANDS, false) == true
        registerDynamicShortcuts()

        setContent {
            val openSleepSound by openSleepSoundState
            val openPeerChat by openPeerChatState
            val openVoiceCommands by openVoiceCommandsState
            val quickAction by quickActionState
            BabyCareTheme {
                MainScreen(
                    viewModel = viewModel,
                    quickAction = quickAction,
                    onQuickActionHandled = { quickActionState.value = null },
                    openSleepSound = openSleepSound,
                    onOpenSleepSoundHandled = { openSleepSoundState.value = false },
                    openPeerChat = openPeerChat,
                    onOpenPeerChatHandled = { openPeerChatState.value = false },
                    openVoiceCommands = openVoiceCommands,
                    onOpenVoiceCommandsHandled = { openVoiceCommandsState.value = false },
                    onQuitApp = { finish() },
                    onMoveToBackground = { moveTaskToBack(true) }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        quickActionState.value = intent.getStringExtra("quick_action")
        if (intent.getBooleanExtra(SleepSoundForegroundService.EXTRA_OPEN_SLEEP_SOUND, false)) {
            openSleepSoundState.value = true
        }
        if (intent.getBooleanExtra(BabyNotificationManager.EXTRA_OPEN_PEER_CHAT, false)) {
            openPeerChatState.value = true
        }
        if (intent.getBooleanExtra(BabyNotificationManager.EXTRA_OPEN_VOICE_COMMANDS, false)) {
            openVoiceCommandsState.value = true
        }
    }

    private fun registerDynamicShortcuts() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            val shortcutManager = getSystemService(ShortcutManager::class.java) ?: return
            fun quickIntent(action: String) = Intent(this, MainActivity::class.java).apply {
                this.action = Intent.ACTION_VIEW
                putExtra("quick_action", action)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

            val feedShortcut = ShortcutInfo.Builder(this, "dynamic_feed")
                .setShortLabel(getString(R.string.shortcut_feed_short))
                .setLongLabel(getString(R.string.shortcut_feed_long))
                .setIcon(Icon.createWithResource(this, R.drawable.ic_shortcut_feed))
                .setIntent(quickIntent("LOG_FEED"))
                .build()

            val nurseShortcut = ShortcutInfo.Builder(this, "dynamic_nurse")
                .setShortLabel(getString(R.string.shortcut_nurse_short))
                .setLongLabel(getString(R.string.shortcut_nurse_long))
                .setIcon(Icon.createWithResource(this, R.drawable.ic_shortcut_nurse))
                .setIntent(quickIntent("LOG_NURSE"))
                .build()

            val sleepShortcut = ShortcutInfo.Builder(this, "dynamic_sleep")
                .setShortLabel(getString(R.string.shortcut_sleep_short))
                .setLongLabel(getString(R.string.shortcut_sleep_long))
                .setIcon(Icon.createWithResource(this, R.drawable.ic_shortcut_sleep))
                .setIntent(quickIntent("LOG_SLEEP"))
                .build()

            val diaperShortcut = ShortcutInfo.Builder(this, "dynamic_diaper")
                .setShortLabel(getString(R.string.shortcut_diaper_short))
                .setLongLabel(getString(R.string.shortcut_diaper_long))
                .setIcon(Icon.createWithResource(this, R.drawable.ic_shortcut_diaper))
                .setIntent(quickIntent("LOG_DIAPER"))
                .build()

            try {
                shortcutManager.dynamicShortcuts =
                    listOf(feedShortcut, nurseShortcut, sleepShortcut, diaperShortcut)
            } catch (_: Exception) {
            }
        }
    }
}

enum class NavDestination(
    val title: String,
    val icon: ImageVector,
    val testTag: String
) {
    DASHBOARD("Dashboard", Icons.Default.ChildCare, "nav_dashboard"),
    TIMELINE("Timeline", Icons.Default.FormatListBulleted, "nav_timeline"),
    HEALTH("Health", Icons.Default.HealthAndSafety, "nav_health"),
    AI_INSIGHTS("Insights", Icons.Default.AutoAwesome, "nav_ai_insights"),
    MORE("More", Icons.Default.MoreHoriz, "nav_more")
}

@Composable
fun MainScreen(
    viewModel: BabyCareViewModel,
    quickAction: String? = null,
    onQuickActionHandled: () -> Unit = {},
    openSleepSound: Boolean = false,
    onOpenSleepSoundHandled: () -> Unit = {},
    openPeerChat: Boolean = false,
    onOpenPeerChatHandled: () -> Unit = {},
    openVoiceCommands: Boolean = false,
    onOpenVoiceCommandsHandled: () -> Unit = {},
    onQuitApp: () -> Unit = {},
    onMoveToBackground: () -> Unit = {}
) {
    var currentDestination by remember { mutableStateOf(NavDestination.DASHBOARD) }
    var isShowingSleepSoundScreen by remember { mutableStateOf(false) }
    var isShowingBluetoothScreen by remember { mutableStateOf(false) }
    var isShowingCareChatScreen by remember { mutableStateOf(false) }
    var moreDestination by remember { mutableStateOf(MoreDestination.HUB) }

    val exitBack = rememberExitBackHandler(
        onQuitApp = onQuitApp,
        onMoveToBackground = onMoveToBackground
    )

    LaunchedEffect(openSleepSound) {
        if (openSleepSound) {
            isShowingBluetoothScreen = false
            isShowingCareChatScreen = false
            isShowingSleepSoundScreen = true
            onOpenSleepSoundHandled()
        }
    }

    LaunchedEffect(openPeerChat) {
        if (openPeerChat) {
            isShowingSleepSoundScreen = false
            isShowingBluetoothScreen = false
            isShowingCareChatScreen = true
            onOpenPeerChatHandled()
        }
    }

    LaunchedEffect(openVoiceCommands) {
        if (openVoiceCommands) {
            isShowingSleepSoundScreen = false
            isShowingBluetoothScreen = false
            isShowingCareChatScreen = false
            moreDestination = MoreDestination.VOICE_COMMANDS
            currentDestination = NavDestination.MORE
            onOpenVoiceCommandsHandled()
        }
    }

    BackHandler {
        when {
            exitBack.showExitDialog -> exitBack.dismissExitDialog()
            isShowingCareChatScreen -> isShowingCareChatScreen = false
            isShowingBluetoothScreen -> isShowingBluetoothScreen = false
            isShowingSleepSoundScreen -> isShowingSleepSoundScreen = false
            currentDestination == NavDestination.MORE && moreDestination != MoreDestination.HUB ->
                moreDestination = MoreDestination.HUB
            currentDestination == NavDestination.MORE -> {
                moreDestination = MoreDestination.HUB
                currentDestination = NavDestination.DASHBOARD
            }
            currentDestination != NavDestination.DASHBOARD ->
                currentDestination = NavDestination.DASHBOARD
            else -> exitBack.handleRootBack()
        }
    }

    if (isShowingCareChatScreen) {
        CareChatScreen(
            onNavigateBack = { isShowingCareChatScreen = false }
        )
    } else if (isShowingBluetoothScreen) {
        BluetoothCareScreen(
            viewModel = viewModel,
            onNavigateBack = { isShowingBluetoothScreen = false },
            onNavigateToChat = {
                isShowingBluetoothScreen = false
                isShowingCareChatScreen = true
            }
        )
    } else if (isShowingSleepSoundScreen) {
        SleepSoundScreen(
            viewModel = viewModel,
            onNavigateBack = { isShowingSleepSoundScreen = false }
        )
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                NavigationBar(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .heightIn(min = 64.dp)
                        .testTag("bottom_navigation_bar"),
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    NavDestination.entries.forEach { destination ->
                        val isSelected = currentDestination == destination
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                moreDestination = MoreDestination.HUB
                                currentDestination = destination
                            },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = destination.title,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = destination.title,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1
                                )
                            },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag(destination.testTag)
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentDestination) {
                    NavDestination.DASHBOARD -> DashboardScreen(
                        viewModel = viewModel,
                        onNavigateToTimeline = { currentDestination = NavDestination.TIMELINE },
                        onNavigateToFamily = {
                            moreDestination = MoreDestination.FAMILY
                            currentDestination = NavDestination.MORE
                        },
                        onNavigateToInsights = { currentDestination = NavDestination.AI_INSIGHTS },
                        onNavigateToSleepSound = { isShowingSleepSoundScreen = true },
                        onNavigateToBluetooth = { isShowingBluetoothScreen = true },
                        onNavigateToCareChat = { isShowingCareChatScreen = true },
                        quickAction = quickAction,
                        onQuickActionHandled = onQuickActionHandled
                    )
                    NavDestination.TIMELINE -> TimelineScreen(viewModel = viewModel)
                    NavDestination.HEALTH -> HealthGrowthScreen(viewModel = viewModel)
                    NavDestination.AI_INSIGHTS -> AIInsightsScreen(viewModel = viewModel)
                    NavDestination.MORE -> MoreHubScreen(
                        viewModel = viewModel,
                        destination = moreDestination,
                        onDestinationChange = { moreDestination = it },
                        onNavigateToBluetooth = { isShowingBluetoothScreen = true },
                        onNavigateToCareChat = { isShowingCareChatScreen = true },
                        onNavigateToSleepSound = { isShowingSleepSoundScreen = true },
                        onDismiss = {
                            moreDestination = MoreDestination.HUB
                            currentDestination = NavDestination.DASHBOARD
                        }
                    )
                }
            }
        }
    }
}
