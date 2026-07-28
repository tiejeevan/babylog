package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.engine.DspSoundTriggerAccess
import com.example.engine.VoiceCommand
import com.example.engine.VoiceCommandExecutor
import com.example.engine.VoiceCommandMatcher
import com.example.engine.VoiceCommandPrefs
import com.example.service.VoiceCommandForegroundService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VoiceCommandsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val powerManager = remember {
        context.getSystemService(PowerManager::class.java)
    }
    val timeFormat = remember { SimpleDateFormat("h:mm:ss a", Locale.getDefault()) }

    var enabled by remember { mutableStateOf(VoiceCommandPrefs.isEnabled(context)) }
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var speechAvailable by remember {
        mutableStateOf(SpeechRecognizer.isRecognitionAvailable(context))
    }
    var ignoringBatteryOpt by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && powerManager != null) {
                powerManager.isIgnoringBatteryOptimizations(context.packageName)
            } else {
                true
            }
        )
    }
    var lastHeard by remember { mutableStateOf(VoiceCommandPrefs.getLastHeard(context)) }
    var lastHeardAt by remember { mutableStateOf(VoiceCommandPrefs.getLastHeardAt(context)) }
    var testPhrase by remember { mutableStateOf("") }
    var testResult by remember { mutableStateOf<String?>(null) }
    var selectedCommand by remember { mutableStateOf(VoiceCommand.CODE_YELLOW) }
    var customDraft by remember { mutableStateOf("") }
    var customPhrases by remember {
        mutableStateOf(VoiceCommandPrefs.getCustomPhrases(context, selectedCommand))
    }
    var dspStatus by remember { mutableStateOf(DspSoundTriggerAccess.probe(context)) }

    fun refreshState() {
        enabled = VoiceCommandPrefs.isEnabled(context)
        hasMicPermission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        speechAvailable = SpeechRecognizer.isRecognitionAvailable(context)
        ignoringBatteryOpt =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && powerManager != null) {
                powerManager.isIgnoringBatteryOptimizations(context.packageName)
            } else {
                true
            }
        lastHeard = VoiceCommandPrefs.getLastHeard(context)
        lastHeardAt = VoiceCommandPrefs.getLastHeardAt(context)
        customPhrases = VoiceCommandPrefs.getCustomPhrases(context, selectedCommand)
        dspStatus = DspSoundTriggerAccess.probe(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasMicPermission = grants[Manifest.permission.RECORD_AUDIO] == true
        if (hasMicPermission && speechAvailable) {
            VoiceCommandPrefs.setEnabled(context, true)
            VoiceCommandForegroundService.start(context)
            enabled = true
        } else {
            VoiceCommandPrefs.setEnabled(context, false)
            enabled = false
        }
    }

    fun requestEnable() {
        if (!speechAvailable) return
        val needed = buildList {
            if (!hasMicPermission) add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val notifGranted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!notifGranted) add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        } else {
            VoiceCommandPrefs.setEnabled(context, true)
            VoiceCommandForegroundService.start(context)
            enabled = true
        }
    }

    fun disableListening() {
        VoiceCommandPrefs.setEnabled(context, false)
        VoiceCommandForegroundService.stop(context)
        enabled = false
    }

    fun runMatcherTest(input: String) {
        val extras = VoiceCommandPrefs.allExtraPhrases(context)
        val hits = VoiceCommandMatcher.matchAll(
            transcript = input,
            cooldownMs = 0L,
            extraPhrases = extras
        )
        testResult = if (hits.isEmpty()) {
            "No match for “${VoiceCommandMatcher.normalize(input)}”"
        } else {
            "Would log: " + hits.joinToString(", ") { it.displayLabel }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Voice Commands") },
                actions = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("voice_commands_dismiss")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .testTag("voice_commands_screen")
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Hands-free voice logging",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Listen while the phone is locked or the app is closed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { turnOn ->
                            if (turnOn) requestEnable() else disableListening()
                        },
                        enabled = speechAvailable,
                        modifier = Modifier.testTag("voice_commands_toggle")
                    )
                }
            }

            if (!speechAvailable) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Speech recognition is not available on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "DSP Sound Trigger",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("voice_dsp_status_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (dspStatus.connectionPath) {
                        DspSoundTriggerAccess.ConnectionPath.DSP_ACTIVE ->
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
                        DspSoundTriggerAccess.ConnectionPath.DSP_READY ->
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        else ->
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    }
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = dspStatus.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    dspStatus.detailLines.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    if (dspStatus.connectionPath != DspSoundTriggerAccess.ConnectionPath.DSP_ACTIVE) {
                        OutlinedButton(
                            onClick = { DspSoundTriggerAccess.openVoiceInteractionSettings(context) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                                .testTag("voice_open_assistant_settings")
                        ) {
                            Text("Open assistant settings")
                        }
                    }
                }
            }

            if (enabled && !hasMicPermission) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Microphone permission is required. Grant it in system settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .testTag("voice_open_app_settings")
                ) {
                    Text("Open app settings")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Test phrases",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("voice_phrase_tester"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Type what you said (or paste what the phone heard) to see if it matches a command.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (lastHeard.isNotBlank()) {
                        Text(
                            text = "Last heard: “$lastHeard”" +
                                if (lastHeardAt > 0) {
                                    " · ${timeFormat.format(Date(lastHeardAt))}"
                                } else {
                                    ""
                                },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                        TextButton(
                            onClick = {
                                testPhrase = lastHeard
                                runMatcherTest(lastHeard)
                            },
                            modifier = Modifier.testTag("voice_use_last_heard")
                        ) {
                            Text("Use last heard")
                        }
                    }
                    OutlinedTextField(
                        value = testPhrase,
                        onValueChange = {
                            testPhrase = it
                            testResult = null
                        },
                        label = { Text("Phrase to test") },
                        placeholder = { Text("e.g. wet diaper") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .testTag("voice_test_phrase_field"),
                        singleLine = true
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { runMatcherTest(testPhrase) },
                            enabled = testPhrase.isNotBlank(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("voice_test_match_btn")
                        ) {
                            Text("Test match")
                        }
                        OutlinedButton(
                            onClick = {
                                val extras = VoiceCommandPrefs.allExtraPhrases(context)
                                val hits = VoiceCommandMatcher.matchAll(
                                    transcript = testPhrase,
                                    cooldownMs = 0L,
                                    extraPhrases = extras
                                )
                                if (hits.isEmpty()) {
                                    Toast.makeText(context, "No match — nothing logged", Toast.LENGTH_SHORT).show()
                                    return@OutlinedButton
                                }
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        hits.forEach { VoiceCommandExecutor.execute(context, it) }
                                    }
                                    Toast.makeText(
                                        context,
                                        "Logged: " + hits.joinToString { it.displayLabel },
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    runMatcherTest(testPhrase)
                                }
                            },
                            enabled = testPhrase.isNotBlank(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("voice_test_log_btn")
                        ) {
                            Text("Log it now")
                        }
                    }
                    testResult?.let { result ->
                        Text(
                            text = result,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (result.startsWith("No match")) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier
                                .padding(top = 10.dp)
                                .testTag("voice_test_result")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Your custom phrases",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("voice_custom_phrases"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "When you find a phrase the phone always understands, save it here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        modifier = Modifier.padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VoiceCommand.entries.forEach { cmd ->
                            FilterChip(
                                selected = selectedCommand == cmd,
                                onClick = {
                                    selectedCommand = cmd
                                    customPhrases = VoiceCommandPrefs.getCustomPhrases(context, cmd)
                                    customDraft = ""
                                },
                                label = { Text(cmd.displayLabel) },
                                modifier = Modifier.testTag("voice_cmd_chip_${cmd.id}")
                            )
                        }
                    }
                    OutlinedTextField(
                        value = customDraft,
                        onValueChange = { customDraft = it },
                        label = { Text("Add phrase for ${selectedCommand.displayLabel}") },
                        placeholder = { Text("exact words that work for you") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .testTag("voice_custom_phrase_field"),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            if (VoiceCommandPrefs.addCustomPhrase(context, selectedCommand, customDraft)) {
                                customPhrases = VoiceCommandPrefs.getCustomPhrases(context, selectedCommand)
                                Toast.makeText(context, "Saved “${VoiceCommandMatcher.normalize(customDraft)}”", Toast.LENGTH_SHORT).show()
                                customDraft = ""
                            } else {
                                Toast.makeText(context, "Enter a new non-empty phrase", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = customDraft.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .testTag("voice_save_custom_phrase")
                    ) {
                        Text("Save phrase")
                    }
                    if (customPhrases.isEmpty()) {
                        Text(
                            text = "No custom phrases yet for ${selectedCommand.displayLabel}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    } else {
                        customPhrases.forEach { phrase ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "“$phrase”",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                TextButton(
                                    onClick = {
                                        VoiceCommandPrefs.removeCustomPhrase(context, selectedCommand, phrase)
                                        customPhrases = VoiceCommandPrefs.getCustomPhrases(context, selectedCommand)
                                    }
                                ) {
                                    Text("Remove")
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Built-in commands",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            PhraseCard(
                title = "Dirty diaper",
                action = "Logs diaper — Dirty",
                aliases = listOf("code brown", "poopy diaper", "brown diaper")
            )
            PhraseCard(
                title = "Wet diaper",
                action = "Logs diaper — Wet",
                aliases = listOf("code yellow", "pee diaper", "yellow diaper")
            )
            PhraseCard(
                title = "Feed the baby",
                action = "Logs bottle (edit volume later)",
                aliases = listOf("feeding baby", "bottle feed", "log bottle")
            )
            PhraseCard(
                title = "Nurse baby",
                action = "Starts breastfeeding timer",
                aliases = listOf("nursing baby", "start nursing")
            )

            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "How to find a 100% phrase",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "1) Turn listening on and say a candidate phrase.\n" +
                            "2) Check “Last heard” above — that’s what the phone understood.\n" +
                            "3) Tap Test match. If it matches, Save phrase for that command.\n" +
                            "4) Prefer short everyday words (wet diaper) over slang when STT struggles.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !ignoringBatteryOpt) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Battery optimization may stop listening in the background.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .testTag("voice_disable_battery_opt_btn")
                ) {
                    Text("Disable battery optimization")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PhraseCard(
    title: String,
    action: String,
    aliases: List<String>
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "“$title”",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = action,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (aliases.isNotEmpty()) {
                Text(
                    text = "Also: " + aliases.joinToString(", ") { "“$it”" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}
