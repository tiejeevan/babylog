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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.data.model.BabyProfile
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun OnboardingSetupDialog(
    initialProfile: BabyProfile?,
    onDismiss: () -> Unit,
    onCompleteSetup: (updatedProfile: BabyProfile, initialWeightKg: Double, initialHeightCm: Double) -> Unit
) {
    val context = LocalContext.current
    var currentStep by remember { mutableIntStateOf(0) }

    // Step 0: Permission States with dynamic System Verification
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
        hasNotificationPermission = isGranted
        if (isGranted) {
            Toast.makeText(context, "Notification permission granted! 🔔", Toast.LENGTH_SHORT).show()
        }
    }

    // Step 1: Caregiver Identity State
    var caregiverRole by remember { mutableStateOf(initialProfile?.primaryCaregiverRole ?: "Mom") }
    var caregiverName by remember { mutableStateOf(initialProfile?.primaryCaregiverName ?: "Mom") }

    // Step 2: Baby Details State
    var babyNameText by remember { mutableStateOf(if (initialProfile?.name == "Your Baby" || initialProfile?.name == "Emma") "" else (initialProfile?.name ?: "")) }
    
    val initialCalendar = remember {
        Calendar.getInstance().apply {
            timeInMillis = initialProfile?.birthDateMillis ?: (System.currentTimeMillis() - (60L * 24 * 3600 * 1000))
        }
    }
    var birthDateCalendar by remember { mutableStateOf(initialCalendar) }
    var birthTimeText by remember { mutableStateOf(initialProfile?.birthTimeFormatted ?: "08:30 AM") }

    var weightText by remember { mutableStateOf((initialProfile?.initialWeightKg ?: 3.5).toString()) }
    var heightText by remember { mutableStateOf((initialProfile?.initialHeightCm ?: 50.0).toString()) }
    var selectedGender by remember { mutableStateOf(initialProfile?.gender ?: "Girl") }
    
    var feedIntervalHoursText by remember { mutableStateOf(((initialProfile?.targetFeedingIntervalMinutes ?: 180) / 60.0).toString()) }
    var napIntervalHoursText by remember { mutableStateOf(((initialProfile?.targetNapIntervalMinutes ?: 150) / 60.0).toString()) }

    val dateFormatter = remember { SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()) }

    Dialog(
        onDismissRequest = {
            if (initialProfile?.isInitialSetupDone == true) onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header & Progress Indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "BabyCare Live Setup 🍼",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )

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

                // Progress Bar
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

                // Content View for Steps
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    AnimatedContent(
                        targetState = currentStep,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "StepTransition"
                    ) { step ->
                        when (step) {
                            0 -> StepPermissionsContent(
                                context = context,
                                hasNotificationPermission = hasNotificationPermission,
                                onRequestNotification = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        hasNotificationPermission = true
                                    }
                                },
                                hasBackgroundRunAllowed = hasBackgroundRunAllowed,
                                onToggleBackgroundRun = {
                                    hasBackgroundRunAllowed = true
                                    try {
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.fromParts("package", context.packageName, null)
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Opened App Info for background settings", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                hasSleepLockConfigured = hasSleepLockConfigured,
                                onConfigureSleepLock = {
                                    hasSleepLockConfigured = true
                                    Toast.makeText(context, "Wake lock permission enabled for alarm wakeup ⏰", Toast.LENGTH_SHORT).show()
                                }
                            )

                            1 -> StepCaregiverIdentityContent(
                                caregiverRole = caregiverRole,
                                onRoleSelected = { role ->
                                    caregiverRole = role
                                    if (caregiverName.isBlank() || caregiverName == "Mom" || caregiverName == "Dad") {
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

                // Bottom Navigation Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStep > 0) {
                        OutlinedButton(
                            onClick = { currentStep-- },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(50.dp)
                                .testTag("setup_back_btn")
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Back", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Button(
                        onClick = {
                            if (currentStep < 2) {
                                currentStep++
                            } else {
                                // Final Save
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
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .height(50.dp)
                            .testTag("setup_next_btn")
                    ) {
                        Text(
                            text = if (currentStep == 2) "Complete Setup & Launch 🚀" else "Next Step ➔",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepPermissionsContent(
    context: Context,
    hasNotificationPermission: Boolean,
    onRequestNotification: () -> Unit,
    hasBackgroundRunAllowed: Boolean,
    onToggleBackgroundRun: () -> Unit,
    hasSleepLockConfigured: Boolean,
    onConfigureSleepLock: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "System Access & Permissions 🔐",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
        Text(
            text = "BabyCare requires deep-level access to ensure urgent feeding, nap, and medication alarms trigger reliably even when your device is asleep.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Card 1: Notification Permission
        PermissionCard(
            title = "1. Notification Permissions 🔔",
            description = "Sends real-time alerts when feeding windows open or sleep timers elapse.",
            isGranted = hasNotificationPermission,
            actionText = if (hasNotificationPermission) "Allowed 🟢" else "Enable Notifications",
            onAction = onRequestNotification,
            testTag = "perm_notification_btn"
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Card 2: Background Run Permission
        PermissionCard(
            title = "2. Background Activity Permission ⚡",
            description = "Allows BabyCare background alarms to run continuously without battery optimization shutdowns.",
            isGranted = hasBackgroundRunAllowed,
            actionText = if (hasBackgroundRunAllowed) "Configured 🟢" else "Configure Background Access",
            onAction = onToggleBackgroundRun,
            testTag = "perm_background_btn"
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Card 3: Sleep Lock / Wake Lock Permission
        PermissionCard(
            title = "3. Sleep Lock & Wake Alarm Access ⏰",
            description = "Ensures device wake locks activate loud alerts during sleep hours.",
            isGranted = hasSleepLockConfigured,
            actionText = "Active 🟢",
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

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onAction,
                enabled = !isGranted,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isGranted) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag(testTag)
            ) {
                Text(
                    text = actionText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
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
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Who Are You? 👤",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
        Text(
            text = "Select your primary role to personalize co-parenting logs, voice reports, and caregiver badges.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        val roles = listOf(
            Triple("Mom", "👩", "Primary Mother Profile"),
            Triple("Dad", "👨", "Primary Father Profile"),
            Triple("Grandparent", "👵", "Grandmother or Grandfather"),
            Triple("Nanny", "🍼", "Nanny or Babysitter"),
            Triple("Other", "👤", "Family relative or Guardian")
        )

        roles.forEach { (role, emoji, subtitle) ->
            val isSelected = caregiverRole == role
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onRoleSelected(role) }
                    .testTag("role_chip_$role"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ),
                border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = emoji,
                        fontSize = 28.sp
                    )
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
            label = { Text("Your Display Name (e.g. Sarah, David)") },
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Baby Details & Birth Stats 👶",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
        Text(
            text = "Enter your baby's birth details. You can easily edit or change these anytime in settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Baby Name
        OutlinedTextField(
            value = babyNameText,
            onValueChange = onBabyNameChanged,
            label = { Text("Baby's Name (e.g. Liam, Maya, Noah)") },
            supportingText = { Text("*(Can be changed anytime later in settings)") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("baby_name_input"),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Gender Selection
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
                    label = { Text(gender) },
                    modifier = Modifier.testTag("gender_chip_$gender")
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Date & Time of Birth Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Date Picker Button
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        val year = birthDateCalendar.get(Calendar.YEAR)
                        val month = birthDateCalendar.get(Calendar.MONTH)
                        val day = birthDateCalendar.get(Calendar.DAY_OF_MONTH)
                        DatePickerDialog(context, { _, y, m, d ->
                            val newCal = Calendar.getInstance().apply {
                                set(y, m, d)
                            }
                            onBirthDateSelected(newCal)
                        }, year, month, day).show()
                    }
                    .testTag("pick_birth_date_btn"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Date of Birth 📅",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = dateFormatter.format(birthDateCalendar.time),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            // Time Picker Button
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        val hour = birthDateCalendar.get(Calendar.HOUR_OF_DAY)
                        val minute = birthDateCalendar.get(Calendar.MINUTE)
                        TimePickerDialog(context, { _, h, m ->
                            val amPm = if (h >= 12) "PM" else "AM"
                            val hour12 = if (h % 12 == 0) 12 else h % 12
                            val formattedTime = String.format(Locale.getDefault(), "%02d:%02d %s", hour12, m, amPm)
                            onBirthTimeSelected(formattedTime)
                        }, hour, minute, false).show()
                    }
                    .testTag("pick_birth_time_btn"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Time of Birth ⏰",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = birthTimeText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Initial Weight & Height
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
                label = { Text("Height/Length (cm)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("baby_height_input"),
                shape = RoundedCornerShape(12.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Feeding & Nap Routine Targets
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = feedIntervalHoursText,
                onValueChange = onFeedIntervalChanged,
                label = { Text("Feed Goal (hrs)") },
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
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("wake_window_input"),
                shape = RoundedCornerShape(12.dp)
            )
        }
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
    val wakeLockGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.WAKE_LOCK
    ) == PackageManager.PERMISSION_GRANTED
    return canSchedule && wakeLockGranted
}
