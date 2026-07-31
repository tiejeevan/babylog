package com.example.ui.dialogs

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.data.model.BabyBirthDefaults
import com.example.data.model.BabyProfile
import com.example.engine.BluetoothCareEngine
import com.example.engine.CareSyncPrefs
import com.example.ui.theme.Dimens
import java.text.SimpleDateFormat
import java.util.*

private val stepTitles = listOf("Permissions", "Your profile", "Baby profile")

@Composable
fun OnboardingSetupDialog(
    initialProfile: BabyProfile?,
    onDismiss: () -> Unit,
    onCompleteSetup: (updatedProfile: BabyProfile, initialWeightKg: Double, initialHeightCm: Double) -> Unit
) {
    val context = LocalContext.current
    var currentStep by remember { mutableIntStateOf(0) }

    var hasNotificationPermission by remember { mutableStateOf(checkNotificationPermissionStatus(context)) }
    var hasBackgroundRunAllowed by remember { mutableStateOf(checkBackgroundRunPermissionStatus(context)) }
    var hasSleepLockConfigured by remember { mutableStateOf(checkSleepLockPermissionStatus(context)) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasNotificationPermission = checkNotificationPermissionStatus(context)
                hasBackgroundRunAllowed = checkBackgroundRunPermissionStatus(context)
                hasSleepLockConfigured = checkSleepLockPermissionStatus(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = checkNotificationPermissionStatus(context)
        if (isGranted) {
            Toast.makeText(context, "Notification permission granted", Toast.LENGTH_SHORT).show()
        } else {
            openNotificationSettings(context)
        }
    }

    var caregiverRole by remember {
        mutableStateOf(
            BluetoothCareEngine.getMyCaregiverRole(context).ifBlank { initialProfile?.primaryCaregiverRole ?: "Mom" }
        )
    }
    var caregiverName by remember {
        mutableStateOf(
            BluetoothCareEngine.getMyCaregiverName(context).ifBlank { initialProfile?.primaryCaregiverName ?: "Mom" }
        )
    }

    var babyNameText by remember {
        mutableStateOf(
            if (initialProfile?.name == "Your Baby" || initialProfile?.name == "Emma") ""
            else (initialProfile?.name ?: "")
        )
    }

    val initialCalendar = remember {
        if (initialProfile?.isInitialSetupDone == true) {
            Calendar.getInstance().apply { timeInMillis = initialProfile.birthDateMillis }
        } else {
            BabyBirthDefaults.birthCalendar()
        }
    }
    var birthDateCalendar by remember { mutableStateOf(initialCalendar) }
    var birthTimeText by remember {
        mutableStateOf(
            if (initialProfile?.isInitialSetupDone == true) {
                initialProfile.birthTimeFormatted
            } else {
                BabyBirthDefaults.BIRTH_TIME_FORMATTED
            }
        )
    }

    var weightText by remember { mutableStateOf((initialProfile?.initialWeightKg ?: 3.5).toString()) }
    var heightText by remember { mutableStateOf((initialProfile?.initialHeightCm ?: 50.0).toString()) }
    var selectedGender by remember { mutableStateOf(initialProfile?.gender ?: "Girl") }

    var feedIntervalHoursText by remember {
        mutableStateOf(((initialProfile?.targetFeedingIntervalMinutes ?: 180) / 60.0).toString())
    }
    var napIntervalHoursText by remember {
        mutableStateOf(((initialProfile?.targetNapIntervalMinutes ?: 150) / 60.0).toString())
    }

    val dateFormatter = remember { SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()) }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val safeInsets = WindowInsets.safeDrawing
    val insetTop = with(density) { safeInsets.getTop(this).toDp() }
    val insetBottom = with(density) { safeInsets.getBottom(this).toDp() }
    // Dialog often measures with unbounded height; pin Surface to the real viewport so weight(1f) works.
    val dialogMaxHeight = (configuration.screenHeightDp.dp - insetTop - insetBottom - 32.dp)
        .coerceAtLeast(320.dp)

    Dialog(
        onDismissRequest = {
            if (initialProfile?.isInitialSetupDone == true) onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .heightIn(max = dialogMaxHeight)
                .height(dialogMaxHeight),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "BabyCare Live Setup",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stepTitles.getOrElse(currentStep) { "" },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (initialProfile?.isInitialSetupDone == true) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close setup")
                        }
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "Step ${currentStep + 1} of 3",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { (currentStep + 1) / 3f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer
                )

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    AnimatedContent(
                        targetState = currentStep,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        modifier = Modifier.fillMaxSize(),
                        label = "StepTransition"
                    ) { step ->
                        when (step) {
                            0 -> StepPermissionsContent(
                                hasNotificationPermission = hasNotificationPermission,
                                onRequestNotification = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        if (hasNotificationPermission) return@StepPermissionsContent
                                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else if (!checkNotificationPermissionStatus(context)) {
                                        openNotificationSettings(context)
                                    }
                                },
                                hasBackgroundRunAllowed = hasBackgroundRunAllowed,
                                onToggleBackgroundRun = {
                                    openBatteryOptimizationSettings(context)
                                },
                                hasSleepLockConfigured = hasSleepLockConfigured,
                                onConfigureSleepLock = {
                                    openExactAlarmSettings(context)
                                }
                            )

                            1 -> StepCaregiverIdentityContent(
                                caregiverRole = caregiverRole,
                                onRoleSelected = { role ->
                                    caregiverRole = role
                                    if (caregiverName.isBlank() ||
                                        caregiverName == "Mom" ||
                                        caregiverName == "Dad" ||
                                        caregiverName == "Grandparent" ||
                                        caregiverName == "Nanny" ||
                                        caregiverName == "Other"
                                    ) {
                                        caregiverName = role
                                    }
                                },
                                caregiverName = caregiverName,
                                onNameChanged = { caregiverName = it }
                            )

                            2 -> StepBabyDetailsContent(
                                context = context,
                                babyNameText = babyNameText,
                                onBabyNameChanged = { babyNameText = it },
                                birthDateCalendar = birthDateCalendar,
                                onBirthDateSelected = { newCal -> birthDateCalendar = newCal },
                                birthTimeText = birthTimeText,
                                onBirthTimeSelected = { newTime -> birthTimeText = newTime },
                                weightText = weightText,
                                onWeightChanged = { weightText = it },
                                heightText = heightText,
                                onHeightChanged = { heightText = it },
                                selectedGender = selectedGender,
                                onGenderSelected = { selectedGender = it },
                                feedIntervalHoursText = feedIntervalHoursText,
                                onFeedIntervalChanged = { feedIntervalHoursText = it },
                                napIntervalHoursText = napIntervalHoursText,
                                onNapIntervalChanged = { napIntervalHoursText = it },
                                dateFormatter = dateFormatter
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStep > 0) {
                        OutlinedButton(
                            onClick = { currentStep-- },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(Dimens.SetupNavButtonHeight)
                                .testTag("setup_back_btn")
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Back", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }

                    Button(
                        onClick = {
                            BluetoothCareEngine.setMyCaregiverName(context, caregiverName.ifBlank { caregiverRole })
                            BluetoothCareEngine.setMyCaregiverRole(context, caregiverRole)

                            if (currentStep < 2) {
                                currentStep++
                            } else {
                                val finalBabyName = babyNameText.ifBlank { "Your Baby" }
                                val weightKg = weightText.toDoubleOrNull() ?: 3.5
                                val heightCm = heightText.toDoubleOrNull() ?: 50.0
                                val feedHours = feedIntervalHoursText.toDoubleOrNull() ?: 3.0
                                val napHours = napIntervalHoursText.toDoubleOrNull() ?: 2.5

                                val updatedProfile = (initialProfile ?: BabyProfile()).copy(
                                    name = finalBabyName,
                                    birthDateMillis = birthDateCalendar.timeInMillis,
                                    birthTimeFormatted = birthTimeText,
                                    initialWeightKg = weightKg,
                                    initialHeightCm = heightCm,
                                    gender = selectedGender,
                                    targetFeedingIntervalMinutes = (feedHours * 60).toInt().coerceIn(30, 480),
                                    targetNapIntervalMinutes = (napHours * 60).toInt().coerceIn(30, 480),
                                    primaryCaregiverName = caregiverName.ifBlank { caregiverRole },
                                    primaryCaregiverRole = caregiverRole,
                                    isInitialSetupDone = true
                                )

                                onCompleteSetup(updatedProfile, weightKg, heightCm)
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .weight(if (currentStep > 0) 1.4f else 1f)
                            .height(Dimens.SetupNavButtonHeight)
                            .testTag("setup_next_btn")
                    ) {
                        Text(
                            text = if (currentStep == 2) "Finish setup" else "Next",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepPermissionsContent(
    hasNotificationPermission: Boolean,
    onRequestNotification: () -> Unit,
    hasBackgroundRunAllowed: Boolean,
    onToggleBackgroundRun: () -> Unit,
    hasSleepLockConfigured: Boolean,
    onConfigureSleepLock: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 8.dp)
    ) {
        Text(
            text = "Permissions",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
        Text(
            text = "Turn on these settings so feeding, nap, and medicine alerts still work when the phone is asleep.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        PermissionCard(
            title = "1. Notifications",
            description = "Alerts when feeding windows open or sleep timers end.",
            isGranted = hasNotificationPermission,
            actionText = if (hasNotificationPermission) "Allowed" else "Enable notifications",
            settingsHint = "Opens: App notification settings",
            howToFind = "Settings → Apps → BabyCare Live → Notifications",
            onAction = onRequestNotification,
            testTag = "perm_notification_btn"
        )

        Spacer(modifier = Modifier.height(12.dp))

        PermissionCard(
            title = "2. Background activity",
            description = "Lets alarms keep running without battery optimization shutting them down.",
            isGranted = hasBackgroundRunAllowed,
            actionText = if (hasBackgroundRunAllowed) "Configured" else "Allow unrestricted battery",
            settingsHint = "Opens: App battery settings",
            howToFind = "Settings → Apps → BabyCare Live → Battery → Unrestricted",
            onAction = onToggleBackgroundRun,
            testTag = "perm_background_btn"
        )

        Spacer(modifier = Modifier.height(12.dp))

        PermissionCard(
            title = "3. Alarms & reminders",
            description = "Allows exact wake alarms so sleep and feed reminders fire on time.",
            isGranted = hasSleepLockConfigured,
            actionText = if (hasSleepLockConfigured) "Allowed" else "Allow alarms & reminders",
            settingsHint = "Opens: Alarms & reminders",
            howToFind = "Settings → Apps → BabyCare Live → Alarms & reminders",
            onAction = onConfigureSleepLock,
            testTag = "perm_sleeplock_btn"
        )
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    actionText: String,
    settingsHint: String,
    howToFind: String,
    onAction: () -> Unit,
    testTag: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (isGranted) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Granted",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!isGranted) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = settingsHint,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "If that screen does not open: $howToFind",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onAction,
                enabled = !isGranted,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isGranted) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimens.MinTouchTarget)
                    .testTag(testTag)
            ) {
                Text(
                    text = actionText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
private fun StepCaregiverIdentityContent(
    caregiverRole: String,
    onRoleSelected: (String) -> Unit,
    caregiverName: String,
    onNameChanged: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 8.dp)
    ) {
        Text(
            text = "Your profile",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
        Text(
            text = "Choose your role so logs and caregiver badges show the right person.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        val roles = listOf(
            Triple("Mom", "Mom", "Primary mother"),
            Triple("Dad", "Dad", "Primary father"),
            Triple("Grandparent", "GP", "Grandmother or grandfather"),
            Triple("Nanny", "Nanny", "Nanny or babysitter"),
            Triple("Other", "Other", "Family or guardian")
        )

        roles.forEach { (role, shortLabel, subtitle) ->
            val isSelected = caregiverRole == role
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onRoleSelected(role) }
                    .testTag("role_chip_$role"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
                border = if (isSelected) {
                    androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                } else null
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Dimens.SecondaryActionMinHeight)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = shortLabel.take(1),
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface,
                                fontSize = 18.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = role,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    RadioButton(
                        selected = isSelected,
                        onClick = { onRoleSelected(role) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = caregiverName,
            onValueChange = onNameChanged,
            label = { Text("Your display name") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("caregiver_display_name_input"),
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
private fun StepBabyDetailsContent(
    context: Context,
    babyNameText: String,
    onBabyNameChanged: (String) -> Unit,
    birthDateCalendar: Calendar,
    onBirthDateSelected: (Calendar) -> Unit,
    birthTimeText: String,
    onBirthTimeSelected: (String) -> Unit,
    weightText: String,
    onWeightChanged: (String) -> Unit,
    heightText: String,
    onHeightChanged: (String) -> Unit,
    selectedGender: String,
    onGenderSelected: (String) -> Unit,
    feedIntervalHoursText: String,
    onFeedIntervalChanged: (String) -> Unit,
    napIntervalHoursText: String,
    onNapIntervalChanged: (String) -> Unit,
    dateFormatter: SimpleDateFormat
) {
    var showMoreDetails by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 8.dp)
    ) {
        Text(
            text = "Baby profile",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
        Text(
            text = "A few basics to get started. You can change these anytime later.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = babyNameText,
            onValueChange = onBabyNameChanged,
            label = { Text("Baby's name") },
            supportingText = { Text("Can be changed later in Family settings") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("baby_name_input"),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Gender",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Boy", "Girl", "Other").forEach { gender ->
                FilterChip(
                    selected = selectedGender == gender,
                    onClick = { onGenderSelected(gender) },
                    label = { Text(gender, fontSize = 14.sp) },
                    modifier = Modifier
                        .heightIn(min = Dimens.MinTouchTarget)
                        .testTag("gender_chip_$gender")
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Date & time of birth",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                onClick = {
                    val year = birthDateCalendar.get(Calendar.YEAR)
                    val month = birthDateCalendar.get(Calendar.MONTH)
                    val day = birthDateCalendar.get(Calendar.DAY_OF_MONTH)
                    DatePickerDialog(context, { _, y, m, d ->
                        val newCal = Calendar.getInstance().apply {
                            timeInMillis = birthDateCalendar.timeInMillis
                            set(Calendar.YEAR, y)
                            set(Calendar.MONTH, m)
                            set(Calendar.DAY_OF_MONTH, d)
                        }
                        onBirthDateSelected(newCal)
                    }, year, month, day).show()
                },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Dimens.PrimaryActionHeight)
                    .testTag("pick_birth_date_btn")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Date",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = dateFormatter.format(birthDateCalendar.time),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }

            Surface(
                onClick = {
                    val parsed = parseBirthTime(birthTimeText)
                    TimePickerDialog(context, { _, h, m ->
                        val amPm = if (h >= 12) "PM" else "AM"
                        val hour12 = if (h % 12 == 0) 12 else h % 12
                        val formattedTime =
                            String.format(Locale.getDefault(), "%d:%02d %s", hour12, m, amPm)
                        onBirthTimeSelected(formattedTime)
                    }, parsed.first, parsed.second, false).show()
                },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Dimens.PrimaryActionHeight)
                    .testTag("pick_birth_time_btn")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Time",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = birthTimeText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Routine Targets (Always Visible)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Routine Targets & Goals ⏱️",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Target feeding intervals and wake window targets drive smart need predictions and alerts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = feedIntervalHoursText,
                        onValueChange = onFeedIntervalChanged,
                        label = { Text("Feed Goal (hrs)") },
                        supportingText = { Text("${((feedIntervalHoursText.toDoubleOrNull() ?: 3.0) * 60).toInt()} mins") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("feed_goal_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = napIntervalHoursText,
                        onValueChange = onNapIntervalChanged,
                        label = { Text("Wake Window (hrs)") },
                        supportingText = { Text("${((napIntervalHoursText.toDoubleOrNull() ?: 2.5) * 60).toInt()} mins") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("wake_window_input"),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Quick Presets:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("2.0", "2.5", "3.0", "3.5", "4.0").forEach { preset ->
                        FilterChip(
                            selected = feedIntervalHoursText == preset,
                            onClick = { onFeedIntervalChanged(preset) },
                            label = { Text("${preset}h", fontSize = 11.sp) },
                            modifier = Modifier.heightIn(min = 32.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(
            onClick = { showMoreDetails = !showMoreDetails },
            modifier = Modifier.testTag("more_baby_details_btn")
        ) {
            Icon(
                imageVector = if (showMoreDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (showMoreDetails) "Hide birth measurements" else "Birth weight & length (optional)",
                fontWeight = FontWeight.SemiBold
            )
        }

        AnimatedVisibility(visible = showMoreDetails) {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = weightText,
                        onValueChange = onWeightChanged,
                        label = { Text("Weight (kg)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("baby_weight_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = heightText,
                        onValueChange = onHeightChanged,
                        label = { Text("Length (cm)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("baby_height_input"),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }
    }
}

private fun parseBirthTime(birthTimeText: String): Pair<Int, Int> {
    return try {
        val parts = birthTimeText.trim().split(" ")
        val timePart = parts[0]
        val amPm = parts.getOrNull(1)?.uppercase(Locale.getDefault()) ?: "AM"
        val hm = timePart.split(":")
        var hour = hm[0].toInt()
        val minute = hm.getOrNull(1)?.toIntOrNull() ?: 0
        if (amPm == "PM" && hour < 12) hour += 12
        if (amPm == "AM" && hour == 12) hour = 0
        hour to minute
    } catch (_: Exception) {
        11 to 12
    }
}

private fun openAppInfoSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(
            context,
            "Open Settings → Apps → BabyCare Live",
            Toast.LENGTH_LONG
        ).show()
    }
}

private fun openNotificationSettings(context: Context) {
    try {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(
            context,
            "Settings → Apps → BabyCare Live → Notifications",
            Toast.LENGTH_LONG
        ).show()
        openAppInfoSettings(context)
    }
}

private fun openBatteryOptimizationSettings(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return
        }
    } catch (_: Exception) {
        // fall through
    }
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Toast.makeText(
                context,
                "Find BabyCare Live and set Battery to Unrestricted",
                Toast.LENGTH_LONG
            ).show()
            return
        }
    } catch (_: Exception) {
        // fall through
    }
    Toast.makeText(
        context,
        "Settings → Apps → BabyCare Live → Battery → Unrestricted",
        Toast.LENGTH_LONG
    ).show()
    openAppInfoSettings(context)
}

private fun openExactAlarmSettings(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return
        }
        Toast.makeText(context, "Exact alarms are already available on this Android version", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {
        Toast.makeText(
            context,
            "Settings → Apps → BabyCare Live → Alarms & reminders",
            Toast.LENGTH_LONG
        ).show()
        openAppInfoSettings(context)
    }
}

private fun checkNotificationPermissionStatus(context: Context): Boolean {
    val notifEnabled = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
    val sdkGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else true
    return notifEnabled && sdkGranted
}

private fun checkBackgroundRunPermissionStatus(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
    } else true
}

private fun checkSleepLockPermissionStatus(context: Context): Boolean {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager
    val canSchedule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        alarmManager?.canScheduleExactAlarms() ?: true
    } else true
    // WAKE_LOCK is a normal permission granted at install; exact-alarm is the user-facing gate.
    return canSchedule
}
