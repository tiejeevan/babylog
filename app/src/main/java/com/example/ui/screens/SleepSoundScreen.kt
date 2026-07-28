package com.example.ui.screens

import android.app.Activity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ActivityTypes
import com.example.engine.BabySoundSynthesizer
import com.example.engine.SleepSoundPrefs
import com.example.engine.SoundType
import com.example.ui.viewmodel.BabyCareViewModel
import java.util.Locale

data class NightLightColor(
    val name: String,
    val color: Color,
    val glowColor: Color
)

val NightLightPresets = listOf(
    NightLightColor("Amber Soft", Color(0xFFFFB74D), Color(0xFFFFE0B2)),
    NightLightColor("Sunset Firefly", Color(0xFFFF8A65), Color(0xFFFFCCBC)),
    NightLightColor("Lavender Calm", Color(0xFFCE93D8), Color(0xFFF3E5F5)),
    NightLightColor("Moonlit Ocean", Color(0xFF81D4FA), Color(0xFFE0F7FA)),
    NightLightColor("Sage Mint", Color(0xFFA5D6A7), Color(0xFFE8F5E9)),
    NightLightColor("Warm Cloud", Color(0xFFFFF59D), Color(0xFFFFFDE7))
)

@Composable
fun SleepSoundScreen(
    viewModel: BabyCareViewModel,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val isPlaying by BabySoundSynthesizer.isPlaying.collectAsStateWithLifecycle()
    val isPaused by BabySoundSynthesizer.isPaused.collectAsStateWithLifecycle()
    val activeSound by BabySoundSynthesizer.currentSound.collectAsStateWithLifecycle()
    val volume by BabySoundSynthesizer.volume.collectAsStateWithLifecycle()
    val remainingTimerMillis by BabySoundSynthesizer.remainingTimerMillis.collectAsStateWithLifecycle()

    var selectedLightColor by remember {
        val idx = SleepSoundPrefs.getColorIndex(context).coerceIn(0, NightLightPresets.lastIndex)
        mutableStateOf(NightLightPresets[idx])
    }
    var lightBrightness by remember {
        mutableFloatStateOf(SleepSoundPrefs.getBrightness(context))
    }
    var isBreathingEnabled by remember {
        mutableStateOf(SleepSoundPrefs.isPulseEnabled(context))
    }
    var keepScreenAwake by remember {
        mutableStateOf(SleepSoundPrefs.isKeepAwake(context))
    }
    var selectedTimerMinutes by remember {
        mutableIntStateOf(SleepSoundPrefs.getTimerMinutes(context))
    }
    var showControls by remember { mutableStateOf(true) }
    var selectedSound by remember {
        mutableStateOf(SleepSoundPrefs.getSoundType(context))
    }

    LaunchedEffect(Unit) {
        val savedVol = SleepSoundPrefs.getVolume(context)
        BabySoundSynthesizer.setVolume(savedVol)
        if (!isPlaying && !isPaused) {
            selectedSound = SleepSoundPrefs.getSoundType(context)
        } else {
            selectedSound = activeSound
        }
    }

    LaunchedEffect(activeSound) {
        if (isPlaying || isPaused) {
            selectedSound = activeSound
        }
    }

    // Keep screen awake
    DisposableEffect(keepScreenAwake) {
        val activity = context as? Activity
        if (keepScreenAwake) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Soft breathing for glow / brightness (slow, calm — not a strobe)
    val infiniteTransition = rememberInfiniteTransition(label = "NightLightBreathing")
    val breathingFactor by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BrightnessPulse"
    )

    // Real window brightness; restore system brightness when leaving
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val window = activity?.window
        val previous = window?.attributes?.screenBrightness
            ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        onDispose {
            if (window != null) {
                val attrs = window.attributes
                attrs.screenBrightness = previous
                window.attributes = attrs
            }
        }
    }

    LaunchedEffect(lightBrightness, isBreathingEnabled, breathingFactor) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        val pulse = if (isBreathingEnabled) breathingFactor else 1f
        val attrs = window.attributes
        attrs.screenBrightness = (lightBrightness * pulse).coerceIn(0.01f, 1f)
        window.attributes = attrs
    }

    val glowAlpha = if (isBreathingEnabled) {
        0.55f + 0.35f * breathingFactor
    } else {
        0.85f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("sleep_sound_screen")
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("night_light_surface")
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            selectedLightColor.glowColor.copy(
                                alpha = glowAlpha * lightBrightness.coerceIn(0.2f, 1f)
                            ),
                            selectedLightColor.color.copy(
                                alpha = glowAlpha * 0.75f * lightBrightness.coerceIn(0.15f, 1f)
                            ),
                            Color(0xFF0A0810)
                        )
                    )
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    showControls = !showControls
                }
        ) {
            // Minimal status when controls hidden
            if (!showControls) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isPlaying) "${activeSound.icon} ${activeSound.title}"
                        else if (isPaused) "Paused · tap for controls"
                        else "Tap for controls",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    if (remainingTimerMillis > 0) {
                        val totalSec = remainingTimerMillis / 1000
                        Text(
                            text = String.format(
                                Locale.US,
                                "%02d:%02d left",
                                totalSec / 60,
                                totalSec % 60
                            ),
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f))
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* consume — don't toggle when interacting with panel */ }
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sleep Sound",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Tap glow to hide controls",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.65f)
                        )
                    }

                    FilterChip(
                        selected = keepScreenAwake,
                        onClick = {
                            keepScreenAwake = !keepScreenAwake
                            SleepSoundPrefs.setKeepAwake(context, keepScreenAwake)
                        },
                        label = {
                            Text(
                                text = if (keepScreenAwake) "Keep awake" else "Allow sleep",
                                fontSize = 11.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF4A2838),
                            selectedLabelColor = Color(0xFFFFACCE),
                            containerColor = Color(0xFF2A2235),
                            labelColor = Color.White
                        )
                    )

                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("btn_back_from_sleep_sound")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Big play / pause
                val soundActive = isPlaying || isPaused
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = selectedSound.icon,
                        fontSize = 42.sp
                    )
                    Text(
                        text = selectedSound.title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    if (remainingTimerMillis > 0) {
                        val totalSec = remainingTimerMillis / 1000
                        Text(
                            text = String.format(
                                Locale.US,
                                "⏱ %02d:%02d",
                                totalSec / 60,
                                totalSec % 60
                            ),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFB3E5FC),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            when {
                                isPlaying -> BabySoundSynthesizer.pauseSound()
                                isPaused -> BabySoundSynthesizer.resumeSound()
                                else -> {
                                    BabySoundSynthesizer.playSound(selectedSound)
                                    SleepSoundPrefs.setSoundType(context, selectedSound)
                                    if (selectedTimerMinutes > 0) {
                                        BabySoundSynthesizer.startSleepTimer(selectedTimerMinutes)
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .size(72.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.92f),
                            contentColor = Color(0xFF1A1220)
                        )
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    if (soundActive) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Stop",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clickable { BabySoundSynthesizer.stopSound() }
                                .padding(8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Volume
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${(volume * 100).toInt()}%",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.width(40.dp)
                    )
                    Slider(
                        value = volume,
                        onValueChange = { BabySoundSynthesizer.setVolume(it) },
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White.copy(alpha = 0.85f),
                            inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Sound selection
                Text(
                    text = "SOUNDS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.55f),
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                SoundType.entries.forEach { sound ->
                    val isCurrent = activeSound == sound && (isPlaying || isPaused)
                    val isSelected = selectedSound == sound
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                when {
                                    isCurrent -> Color.White.copy(alpha = 0.22f)
                                    isSelected -> Color.White.copy(alpha = 0.12f)
                                    else -> Color.White.copy(alpha = 0.06f)
                                }
                            )
                            .clickable {
                                selectedSound = sound
                                SleepSoundPrefs.setSoundType(context, sound)
                                if (isPlaying || isPaused) {
                                    BabySoundSynthesizer.playSound(sound)
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("sound_card_${sound.name}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(sound.icon, fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = sound.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = sound.description,
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                        if (isCurrent && isPlaying) {
                            Icon(
                                imageVector = Icons.Default.Pause,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Timer
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = Color(0xFFB3E5FC),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "AUTO-OFF",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.55f),
                        letterSpacing = 0.8.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                val timerOptions = listOf(0, 15, 30, 45, 60, 90, 120)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(timerOptions) { mins ->
                        val isSelected = selectedTimerMinutes == mins
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedTimerMinutes = mins
                                BabySoundSynthesizer.startSleepTimer(mins)
                                SleepSoundPrefs.setTimerMinutes(context, mins)
                                Toast.makeText(
                                    context,
                                    if (mins == 0) "Timer off (plays until stopped)"
                                    else "Auto-off in $mins minutes",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            label = {
                                Text(
                                    text = when (mins) {
                                        0 -> "Off"
                                        90 -> "1.5h"
                                        120 -> "2h"
                                        else -> "${mins}m"
                                    },
                                    fontSize = 12.sp
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF0277BD),
                                selectedLabelColor = Color.White,
                                containerColor = Color.White.copy(alpha = 0.1f),
                                labelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Night light brightness + pulse
                Text(
                    text = "NIGHT LIGHT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.55f),
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LightMode,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${(lightBrightness * 100).toInt()}%",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.width(40.dp)
                    )
                    Slider(
                        value = lightBrightness,
                        onValueChange = {
                            lightBrightness = it
                            SleepSoundPrefs.setBrightness(context, it)
                        },
                        valueRange = 0.01f..1f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = selectedLightColor.glowColor,
                            activeTrackColor = selectedLightColor.color,
                            inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                        )
                    )
                    Text(
                        text = "Pulse",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                    Switch(
                        checked = isBreathingEnabled,
                        onCheckedChange = {
                            isBreathingEnabled = it
                            SleepSoundPrefs.setPulseEnabled(context, it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = selectedLightColor.color
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(NightLightPresets) { preset ->
                        val isSelected = selectedLightColor == preset
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(preset.color)
                                .border(
                                    width = if (isSelected) 3.dp else 0.dp,
                                    color = if (isSelected) Color.White else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable {
                                    selectedLightColor = preset
                                    SleepSoundPrefs.setColorIndex(
                                        context,
                                        NightLightPresets.indexOf(preset)
                                    )
                                }
                                .testTag("color_preset_${preset.name}"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.LightMode,
                                    contentDescription = "Selected",
                                    tint = Color.Black.copy(alpha = 0.55f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        viewModel.startLiveActivity(ActivityTypes.SLEEP)
                        Toast.makeText(context, "Started Live Sleep Session", Toast.LENGTH_SHORT)
                            .show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_log_sleep_from_sound_machine"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.18f),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Bedtime,
                        contentDescription = null,
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Start Live Sleep Session",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
