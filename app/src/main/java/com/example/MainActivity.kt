package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.People
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
import androidx.compose.material.icons.filled.NightlightRound
import com.example.ui.screens.AIInsightsScreen
import com.example.ui.screens.BluetoothCareScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.FamilyCaregiversScreen
import com.example.ui.screens.HealthGrowthScreen
import com.example.ui.screens.SleepSoundScreen
import com.example.ui.screens.TimelineScreen
import com.example.ui.theme.BabyCareTheme
import com.example.ui.viewmodel.BabyCareViewModel

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
import androidx.compose.runtime.mutableStateOf

class MainActivity : ComponentActivity() {

    private val viewModel: BabyCareViewModel by viewModels()
    private val quickActionState = mutableStateOf<String?>(null)

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
        registerDynamicShortcuts()

        setContent {
            BabyCareTheme {
                MainScreen(
                    viewModel = viewModel,
                    quickAction = quickActionState.value,
                    onQuickActionHandled = { quickActionState.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        quickActionState.value = intent.getStringExtra("quick_action")
    }

    private fun registerDynamicShortcuts() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            val shortcutManager = getSystemService(ShortcutManager::class.java) ?: return
            val feedIntent = Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra("quick_action", "LOG_FEED")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val sleepIntent = Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra("quick_action", "LOG_SLEEP")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val diaperIntent = Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra("quick_action", "LOG_DIAPER")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

            val feedShortcut = ShortcutInfo.Builder(this, "dynamic_feed")
                .setShortLabel(getString(R.string.shortcut_feed_short))
                .setLongLabel(getString(R.string.shortcut_feed_long))
                .setIcon(Icon.createWithResource(this, R.mipmap.ic_launcher))
                .setIntent(feedIntent)
                .build()

            val sleepShortcut = ShortcutInfo.Builder(this, "dynamic_sleep")
                .setShortLabel(getString(R.string.shortcut_sleep_short))
                .setLongLabel(getString(R.string.shortcut_sleep_long))
                .setIcon(Icon.createWithResource(this, R.mipmap.ic_launcher))
                .setIntent(sleepIntent)
                .build()

            val diaperShortcut = ShortcutInfo.Builder(this, "dynamic_diaper")
                .setShortLabel(getString(R.string.shortcut_diaper_short))
                .setLongLabel(getString(R.string.shortcut_diaper_long))
                .setIcon(Icon.createWithResource(this, R.mipmap.ic_launcher))
                .setIntent(diaperIntent)
                .build()

            try {
                shortcutManager.dynamicShortcuts = listOf(feedShortcut, sleepShortcut, diaperShortcut)
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
    AI_INSIGHTS("AI Care", Icons.Default.AutoAwesome, "nav_ai_insights"),
    FAMILY("Family", Icons.Default.People, "nav_family")
}

@Composable
fun MainScreen(
    viewModel: BabyCareViewModel,
    quickAction: String? = null,
    onQuickActionHandled: () -> Unit = {}
) {
    var currentDestination by remember { mutableStateOf(NavDestination.DASHBOARD) }
    var isShowingSleepSoundScreen by remember { mutableStateOf(false) }
    var isShowingBluetoothScreen by remember { mutableStateOf(false) }

    if (isShowingBluetoothScreen) {
        BluetoothCareScreen(
            viewModel = viewModel,
            onNavigateBack = { isShowingBluetoothScreen = false }
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
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .testTag("bottom_navigation_bar"),
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    NavDestination.entries.forEach { destination ->
                        val isSelected = currentDestination == destination
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { currentDestination = destination },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = destination.title
                                )
                            },
                            label = {
                                Text(
                                    text = destination.title,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            modifier = Modifier.testTag(destination.testTag)
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
                        onNavigateToFamily = { currentDestination = NavDestination.FAMILY },
                        onNavigateToSleepSound = { isShowingSleepSoundScreen = true },
                        onNavigateToBluetooth = { isShowingBluetoothScreen = true },
                        quickAction = quickAction,
                        onQuickActionHandled = onQuickActionHandled
                    )
                    NavDestination.TIMELINE -> TimelineScreen(viewModel = viewModel)
                    NavDestination.HEALTH -> HealthGrowthScreen(viewModel = viewModel)
                    NavDestination.AI_INSIGHTS -> AIInsightsScreen(viewModel = viewModel)
                    NavDestination.FAMILY -> FamilyCaregiversScreen(
                        viewModel = viewModel,
                        onNavigateToBluetooth = { isShowingBluetoothScreen = true }
                    )
                }
            }
        }
    }
}
