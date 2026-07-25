package com.example.ui.screens

import android.app.Activity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.IconButton
import com.example.data.model.ActivityTypes
import com.example.engine.BabySoundSynthesizer
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
    val activeSound by BabySoundSynthesizer.currentSound.collectAsStateWithLifecycle()
    val volume by BabySoundSynthesizer.volume.collectAsStateWithLifecycle()
    val remainingTimerMillis by BabySoundSynthesizer.remainingTimerMillis.collectAsStateWithLifecycle()

    var selectedLightColor by remember { mutableStateOf(NightLightPresets[0]) }
    var lightBrightness by remember { mutableFloatStateOf(0.75f) }
    var isBreathingEnabled by remember { mutableStateOf(true) }
    var keepScreenAwake by remember { mutableStateOf(true) }
    var selectedTimerMinutes by remember { mutableIntStateOf(0) }

    // Keep screen awake side-effect when enabled
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

    // Breathing pulse animation for night light
    val infiniteTransition = rememberInfiniteTransition(label = "NightLightBreathing")
    val breathingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AlphaPulse"
    )

    val currentGlowAlpha = if (isBreathingEnabled) breathingAlpha * lightBrightness else lightBrightness

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF100C16))
            .padding(16.dp)
            .testTag("sleep_sound_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("btn_back_from_sleep_sound")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFFFFD8E4)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            text = "SLEEP & NIGHT LIGHT 🌙",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD8E4)
                        )
                        Text(
                            text = "Crib-side white noise machine and ambient glow",
                            fontSize = 11.sp,
                            color = Color(0xFFCBBBC3)
                        )
                    }
                }

                FilterChip(
                    selected = keepScreenAwake,
                    onClick = { keepScreenAwake = !keepScreenAwake },
                    label = {
                        Text(
                            text = if (keepScreenAwake) "Awake On" else "Dim Off",
                            fontSize = 11.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.NightlightRound,
                            contentDescription = "Awake",
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF4A2838),
                        selectedLabelColor = Color(0xFFFFACCE)
                    )
                )
            }
        }

        // Night Light Glowing Surface Banner
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .testTag("night_light_surface"),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    selectedLightColor.glowColor.copy(alpha = currentGlowAlpha),
                                    selectedLightColor.color.copy(alpha = currentGlowAlpha * 0.8f),
                                    Color(0xFF1B1425)
                                )
                            )
                        )
                        .padding(20.dp),
                    contentAlignment = Alignment.BottomStart
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "💡 Night Light Glow: ${selectedLightColor.name}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Pulse",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Switch(
                                    checked = isBreathingEnabled,
                                    onCheckedChange = { isBreathingEnabled = it },
                                    modifier = Modifier.size(28.dp),
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = selectedLightColor.color
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Brightness Level: ${(lightBrightness * 100).toInt()}%",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.9f)
                        )

                        Slider(
                            value = lightBrightness,
                            onValueChange = { lightBrightness = it },
                            valueRange = 0.1f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = selectedLightColor.glowColor,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            )
                        )
                    }
                }
            }
        }

        // Night Light Color Presets Row
        item {
            Column {
                Text(
                    text = "NIGHT LIGHT COLOR PALETTE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFA28EA0),
                    letterSpacing = 0.8.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(NightLightPresets) { preset ->
                        val isSelected = selectedLightColor == preset
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(preset.color)
                                .border(
                                    width = if (isSelected) 3.dp else 0.dp,
                                    color = if (isSelected) Color.White else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedLightColor = preset }
                                .testTag("color_preset_${preset.name}"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.LightMode,
                                    contentDescription = "Selected",
                                    tint = Color.Black.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // White Noise Sound Machine Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E182A)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Sound",
                                tint = Color(0xFFFFB2C9)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SOUND MACHINE SYNTH",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        if (isPlaying) {
                            Button(
                                onClick = { BabySoundSynthesizer.stopSound() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Pause,
                                    contentDescription = "Stop",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Pause", fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Volume slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Volume: ${(volume * 100).toInt()}%",
                            fontSize = 12.sp,
                            color = Color(0xFFD0BCFF),
                            modifier = Modifier.width(90.dp)
                        )
                        Slider(
                            value = volume,
                            onValueChange = { BabySoundSynthesizer.setVolume(it) },
                            valueRange = 0.0f..1.0f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFD0BCFF),
                                activeTrackColor = Color(0xFFD0BCFF)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Sound Selection List
                    SoundType.entries.forEach { sound ->
                        val isCurrentSound = activeSound == sound && isPlaying
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    if (isCurrentSound) {
                                        BabySoundSynthesizer.stopSound()
                                    } else {
                                        BabySoundSynthesizer.playSound(sound)
                                    }
                                }
                                .testTag("sound_card_${sound.name}"),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCurrentSound) Color(0xFF38233C) else Color(0xFF272036)
                            ),
                            border = if (isCurrentSound) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF83A8)) else null,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(sound.icon, fontSize = 22.sp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = sound.title,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = sound.description,
                                            fontSize = 11.sp,
                                            color = Color(0xFFCBBBC3)
                                        )
                                    }
                                }

                                Icon(
                                    imageVector = if (isCurrentSound) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Toggle",
                                    tint = if (isCurrentSound) Color(0xFFFF83A8) else Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Sleep Auto-Off Timer Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E182A)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = "Timer",
                                tint = Color(0xFF81D4FA)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "AUTO-OFF SLEEP TIMER",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        if (remainingTimerMillis > 0) {
                            val totalSec = remainingTimerMillis / 1000
                            val mins = totalSec / 60
                            val secs = totalSec % 60
                            val countdownText = String.format(Locale.US, "%02d:%02d", mins, secs)
                            Text(
                                text = "⏳ $countdownText",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF81D4FA)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val timerOptions = listOf(0, 15, 30, 45, 60)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        timerOptions.forEach { mins ->
                            val isSelected = selectedTimerMinutes == mins
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedTimerMinutes = mins
                                    BabySoundSynthesizer.startSleepTimer(mins)
                                    Toast.makeText(
                                        context,
                                        if (mins == 0) "Timer disabled (Continuous)" else "Auto-off in $mins minutes",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                label = {
                                    Text(
                                        text = if (mins == 0) "Off" else "${mins}m",
                                        fontSize = 12.sp
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF0288D1),
                                    selectedLabelColor = Color.White,
                                    containerColor = Color(0xFF2B223B),
                                    labelColor = Color.White
                                )
                            )
                        }
                    }
                }
            }
        }

        // Quick Log Sleep Action
        item {
            Button(
                onClick = {
                    viewModel.startLiveActivity(ActivityTypes.SLEEP)
                    Toast.makeText(context, "Started Live Sleep Session 😴", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("btn_log_sleep_from_sound_machine"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7E57C2))
            ) {
                Icon(
                    imageVector = Icons.Default.Bedtime,
                    contentDescription = "Sleep",
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Start Live Sleep Session in App 😴",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
