package com.example.ui.components

import com.example.ui.theme.parseHexColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bathtub
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.IconButton
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import com.example.ui.theme.Dimens
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ActivityLog
import com.example.data.model.ActivityTypes
import com.example.ui.viewmodel.NursingSide
import com.example.data.model.BabyProfile
import com.example.data.model.CaregiverProfile
import com.example.data.model.ExactAge
import com.example.engine.BabyNeedPrediction
import com.example.engine.IntelligentNeedEngine
import com.example.engine.QuickActionPrefs
import com.example.engine.TodaySummary
import com.example.engine.UrgencyLevel
import com.example.ui.dialogs.SetFavoriteActionDialog
import com.example.ui.theme.CustomActionColor
import com.example.ui.theme.DiaperColor
import com.example.ui.theme.FavoriteActionColor
import com.example.ui.theme.FeedingColor
import com.example.ui.theme.HealthColor
import com.example.ui.theme.MedicineColor
import com.example.ui.theme.MilestoneColor
import com.example.ui.theme.PumpingColor
import com.example.ui.theme.SleepColor
import com.example.ui.theme.TummyTimeColor
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay

private const val FAVORITE_SLOT_TYPE = "FAVORITE_SLOT"

@Composable
fun TopBabyHeader(
    profile: BabyProfile?,
    activeCaregiver: CaregiverProfile?,
    syncStatus: String,
    onSwitchCaregiverClick: () -> Unit,
    onProfileClick: () -> Unit,
    /** Placeholder status for future background care-kernel processes. */
    backgroundKernelTitle: String = "No Process running"
) {
    var showExactAgeDialog by remember { mutableStateOf(false) }

    if (showExactAgeDialog && profile != null) {
        ExactAgeLiveDialog(
            profile = profile,
            onDismiss = { showExactAgeDialog = false }
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Baby Profile & Age
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable { onProfileClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChildCare,
                            contentDescription = "Baby Profile",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = profile?.name ?: "Your Baby",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .clickable { onProfileClick() }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                onClick = { if (profile != null) showExactAgeDialog = true },
                                enabled = profile != null,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("exact_age_chip")
                            ) {
                                Text(
                                    text = profile?.getFormattedAge() ?: "Newborn",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = backgroundKernelTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Active Caregiver Pill
                    Surface(
                        onClick = onSwitchCaregiverClick,
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.testTag("caregiver_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(parseHexColor(activeCaregiver?.avatarColorHex))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = activeCaregiver?.name ?: "Sarah (Mom)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Real-Time Sync Indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 2.dp)
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulse"
                )

                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4CAF50))
                        .alpha(alpha)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = syncStatus,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ExactAgeLiveDialog(
    profile: BabyProfile,
    onDismiss: () -> Unit
) {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val age = remember(nowMillis, profile.birthDateMillis) {
        profile.getExactAge(nowMillis)
    }
    val bornOn = remember(profile.birthDateMillis, profile.birthTimeFormatted) {
        val date = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            .format(Date(profile.birthDateMillis))
        "$date · ${profile.birthTimeFormatted}"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "${profile.name}'s exact age",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Born $bornOn",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = age.breakdownLabel(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = age.liveClockLabel(),
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("exact_age_live_clock")
                )
                Spacer(modifier = Modifier.height(16.dp))
                ExactAgeUnitRow(age)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (age.totalDays == 1L) "1 day total" else "${age.totalDays} days total",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun ExactAgeUnitRow(age: ExactAge) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ExactAgeUnit(value = age.years, label = "yr")
        ExactAgeUnit(value = age.months, label = "mo")
        ExactAgeUnit(value = age.days, label = "day")
        ExactAgeUnit(value = age.hours, label = "hr")
        ExactAgeUnit(value = age.minutes, label = "min")
        ExactAgeUnit(value = age.seconds, label = "sec")
    }
}

@Composable
private fun ExactAgeUnit(value: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun IntelligentNeedCard(
    prediction: BabyNeedPrediction,
    onActionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val cardColor = when (prediction.urgencyLevel) {
        UrgencyLevel.HIGH_URGENT -> Color(0xFFFFEBEE)
        UrgencyLevel.MEDIUM_RECOMMENDED -> Color(0xFFFFF3E0)
        UrgencyLevel.LOW_ALL_GOOD -> Color(0xFFE8F5E9)
    }

    val accentColor = when (prediction.urgencyLevel) {
        UrgencyLevel.HIGH_URGENT -> Color(0xFFD32F2F)
        UrgencyLevel.MEDIUM_RECOMMENDED -> Color(0xFFE65100)
        UrgencyLevel.LOW_ALL_GOOD -> Color(0xFF2E7D32)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ai_prediction_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI Prediction",
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "INTELLIGENT AI NEED ENGINE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        letterSpacing = 0.5.sp
                    )
                }

                Surface(
                    color = accentColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "${prediction.confidencePercentage}% Confidence",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = prediction.primaryNeedTitle,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF1F1A18)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = prediction.reasoning,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF423733)
            )

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = { onActionClick(prediction.suggestedActivityType) },
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("prediction_action_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Log Action: ${prediction.recommendedAction}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun LiveActiveTimerCard(
    ongoingActivity: ActivityLog,
    currentTimeMillis: Long,
    onStopClick: () -> Unit,
    activeNursingSide: NursingSide = NursingSide.LEFT,
    nursingSideStartedAtMillis: Long = 0L,
    onNursingSideChange: (NursingSide) -> Unit = {}
) {
    val elapsedMillis = currentTimeMillis - ongoingActivity.startTimeMillis
    val totalSeconds = (elapsedMillis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hours = minutes / 60
    val displayMin = minutes % 60

    val timeFormatted = if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, displayMin, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }

    val isNursing = ongoingActivity.activityType == ActivityTypes.BREASTFEEDING
    val sideStarted = nursingSideStartedAtMillis.takeIf { it > 0 } ?: ongoingActivity.startTimeMillis
    val liveSideSec = ((currentTimeMillis - sideStarted) / 1000).coerceAtLeast(0)
    val displayLeftSec = ongoingActivity.leftBreastDurationSec +
        if (isNursing && activeNursingSide == NursingSide.LEFT) liveSideSec else 0
    val displayRightSec = ongoingActivity.rightBreastDurationSec +
        if (isNursing && activeNursingSide == NursingSide.RIGHT) liveSideSec else 0

    val (title, icon, color) = when (ongoingActivity.activityType) {
        ActivityTypes.SLEEP -> Triple("Baby is Sleeping 💤", Icons.Default.NightlightRound, SleepColor)
        ActivityTypes.BREASTFEEDING -> Triple("Breastfeeding Session 🍼", Icons.Default.ChildCare, FeedingColor)
        ActivityTypes.BOTTLE -> Triple("Bottle Feeding Session 🍼", Icons.Default.ChildCare, FeedingColor)
        ActivityTypes.TUMMY_TIME -> Triple("Tummy Time Exercise 👶", Icons.Default.FitnessCenter, TummyTimeColor)
        ActivityTypes.PUMPING -> Triple("Breast Pumping Session 💧", Icons.Default.WaterDrop, PumpingColor)
        else -> Triple("Active ${ongoingActivity.activityType}", Icons.Default.ChildCare, MaterialTheme.colorScheme.primary)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("active_timer_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        border = androidx.compose.foundation.BorderStroke(2.dp, color)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(color),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF201A18)
                    )
                    Text(
                        text = "Caregiver: ${ongoingActivity.caregiverName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF53433C)
                    )
                    Text(
                        text = timeFormatted,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 32.sp
                        ),
                        color = color
                    )
                }
            }

            if (isNursing) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Active side",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF53433C)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = activeNursingSide == NursingSide.LEFT,
                        onClick = { onNursingSideChange(NursingSide.LEFT) },
                        label = {
                            Text("Left · ${formatSideMinutes(displayLeftSec)}")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("nursing_side_left")
                    )
                    FilterChip(
                        selected = activeNursingSide == NursingSide.RIGHT,
                        onClick = { onNursingSideChange(NursingSide.RIGHT) },
                        label = {
                            Text("Right · ${formatSideMinutes(displayRightSec)}")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("nursing_side_right")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onStopClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimens.LiveStopButtonHeight)
                    .testTag("stop_timer_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop",
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Finish & save", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

private fun formatSideMinutes(totalSec: Long): String {
    val mins = totalSec / 60
    val secs = totalSec % 60
    return if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickActionGrid(
    onActionSelected: (String) -> Unit
) {
    var showMoreActions by remember { mutableStateOf(false) }
    var showFavoritePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var favoriteType by remember {
        mutableStateOf(QuickActionPrefs.getFavoriteType(context))
    }
    var favoriteLabel by remember {
        mutableStateOf(
            QuickActionPrefs.getFavoriteLabel(context)
                ?: favoriteType?.let { QuickActionPrefs.defaultLabelForType(it) }
        )
    }

    if (showFavoritePicker) {
        SetFavoriteActionDialog(
            currentType = favoriteType,
            onDismiss = { showFavoritePicker = false },
            onConfirm = { type, label ->
                QuickActionPrefs.setFavorite(context, type, label)
                favoriteType = type
                favoriteLabel = label
                showFavoritePicker = false
            },
            onClear = {
                QuickActionPrefs.clearFavorite(context)
                favoriteType = null
                favoriteLabel = null
                showFavoritePicker = false
            }
        )
    }

    val primaryActions = listOf(
        QuickActionItem("Bottle", ActivityTypes.BOTTLE, Icons.Default.WaterDrop, FeedingColor),
        QuickActionItem("Nurse", ActivityTypes.BREASTFEEDING, Icons.Default.ChildCare, FeedingColor),
        QuickActionItem("Sleep", ActivityTypes.SLEEP, Icons.Default.NightlightRound, SleepColor),
        QuickActionItem("Diaper", ActivityTypes.DIAPER, Icons.Default.CleaningServices, DiaperColor),
        QuickActionItem("Custom", ActivityTypes.CUSTOM, Icons.Default.EditNote, CustomActionColor),
        QuickActionItem(
            label = favoriteLabel ?: "Favorite",
            type = FAVORITE_SLOT_TYPE,
            icon = Icons.Default.Star,
            color = FavoriteActionColor
        )
    )

    val moreActions = listOf(
        QuickActionItem("Pumping", ActivityTypes.PUMPING, Icons.Default.WaterDrop, PumpingColor),
        QuickActionItem("Medicine", ActivityTypes.MEDICINE, Icons.Default.MedicalServices, MedicineColor),
        QuickActionItem("Temp", ActivityTypes.TEMPERATURE, Icons.Default.Thermostat, HealthColor),
        QuickActionItem("Growth", ActivityTypes.GROWTH, Icons.Default.Scale, HealthColor),
        QuickActionItem("Bath", ActivityTypes.BATH, Icons.Default.Bathtub, DiaperColor),
        QuickActionItem("Tummy Time", ActivityTypes.TUMMY_TIME, Icons.Default.FitnessCenter, TummyTimeColor),
        QuickActionItem("Milestone", ActivityTypes.MILESTONE, Icons.Default.LocalHospital, MilestoneColor)
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "QUICK ACTIONS",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )

        // 3 × 2 matrix of big actions
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            primaryActions.chunked(3).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowItems.forEach { action ->
                        PrimaryQuickActionButton(
                            item = action,
                            onClick = {
                                when (action.type) {
                                    ActivityTypes.CUSTOM -> onActionSelected(ActivityTypes.CUSTOM)
                                    FAVORITE_SLOT_TYPE -> {
                                        val type = favoriteType
                                        if (type.isNullOrBlank()) {
                                            showFavoritePicker = true
                                        } else {
                                            onActionSelected(type)
                                        }
                                    }
                                    else -> onActionSelected(action.type)
                                }
                            },
                            onLongClick = if (action.type == FAVORITE_SLOT_TYPE) {
                                { showFavoritePicker = true }
                            } else {
                                null
                            },
                            modifier = Modifier.weight(1f),
                            cornerBadge = when (action.type) {
                                ActivityTypes.CUSTOM -> "Misc"
                                FAVORITE_SLOT_TYPE -> if (favoriteType == null) "Add" else "Edit"
                                else -> null
                            }
                        )
                    }
                    // Keep row balanced if a chunk is somehow short
                    repeat(3 - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        TextButton(
            onClick = { showMoreActions = !showMoreActions },
            modifier = Modifier
                .padding(top = 4.dp)
                .testTag("more_quick_actions_btn")
        ) {
            Icon(
                imageVector = if (showMoreActions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (showMoreActions) "Hide more actions" else "More actions",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
        }

        AnimatedVisibility(visible = showMoreActions) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                maxItemsInEachRow = 4
            ) {
                moreActions.forEach { action ->
                    QuickActionButton(
                        item = action,
                        onClick = { onActionSelected(action.type) }
                    )
                }
            }
        }
    }
}

data class QuickActionItem(
    val label: String,
    val type: String,
    val icon: ImageVector,
    val color: Color
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PrimaryQuickActionButton(
    item: QuickActionItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    cornerBadge: String? = null
) {
    val shape = RoundedCornerShape(18.dp)
    val interactionModifier = if (onLongClick != null) {
        Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        )
    } else {
        Modifier.clickable(onClick = onClick)
    }

    Surface(
        shape = shape,
        color = item.color.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, item.color.copy(alpha = 0.35f)),
        modifier = modifier
            .height(Dimens.PrimaryActionHeight)
            .clip(shape)
            .then(interactionModifier)
            .testTag("quick_action_${item.type.lowercase()}")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(item.color),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = item.label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (cornerBadge != null) {
                Surface(
                    shape = RoundedCornerShape(7.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        item.color.copy(alpha = 0.55f)
                    ),
                    tonalElevation = 1.dp,
                    shadowElevation = 1.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 5.dp, bottom = 5.dp)
                ) {
                    Text(
                        text = cornerBadge,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = item.color,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun QuickActionButton(
    item: QuickActionItem,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = item.color.copy(alpha = 0.10f),
        border = androidx.compose.foundation.BorderStroke(1.dp, item.color.copy(alpha = 0.3f)),
        modifier = Modifier
            .heightIn(min = Dimens.MinTouchTarget)
            .size(width = 80.dp, height = 72.dp)
            .testTag("quick_action_${item.type.lowercase()}")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(item.color),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TodaySummaryBar(
    allLogs: List<ActivityLog> = emptyList(),
    summary: TodaySummary = TodaySummary()
) {
    val totalPages = 3650
    val todayPageIndex = totalPages - 1
    val pagerState = rememberPagerState(
        initialPage = todayPageIndex,
        pageCount = { totalPages }
    )
    val scope = rememberCoroutineScope()

    val currentDayOffset = pagerState.currentPage - todayPageIndex

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp, horizontal = 12.dp)) {
            // Header Row with Date, Navigation controls, and Quick Jump button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))

                    val headerText = remember(allLogs, currentDayOffset, summary) {
                        if (currentDayOffset == 0 && summary.dateLabel.isNotEmpty()) {
                            summary.dateLabel
                        } else {
                            IntelligentNeedEngine.computeDaySummary(allLogs, currentDayOffset).dateLabel
                        }
                    }

                    Text(
                        text = headerText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (currentDayOffset < 0) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .clickable {
                                    scope.launch {
                                        pagerState.animateScrollToPage(todayPageIndex)
                                    }
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "Today ↩",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    IconButton(
                        onClick = {
                            if (pagerState.currentPage > 0) {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            }
                        },
                        enabled = pagerState.currentPage > 0,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Previous Day",
                            tint = if (pagerState.currentPage > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            if (pagerState.currentPage < todayPageIndex) {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            }
                        },
                        enabled = pagerState.currentPage < todayPageIndex,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Next Day",
                            tint = if (pagerState.currentPage < todayPageIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth()
            ) { page ->
                val dayOffset = page - todayPageIndex
                val daySummary = remember(allLogs, dayOffset, summary) {
                    if (dayOffset == 0) {
                        if (allLogs.isNotEmpty()) IntelligentNeedEngine.computeDaySummary(allLogs, 0) else summary
                    } else {
                        IntelligentNeedEngine.computeDaySummary(allLogs, dayOffset)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SummaryPill(
                        title = "Feedings",
                        value = "${daySummary.feedCount} times",
                        subtext = "${daySummary.totalFeedVolumeMl} ml",
                        color = FeedingColor
                    )
                    SummaryPill(
                        title = "Sleep",
                        value = IntelligentNeedEngine.formatMinutes(daySummary.totalSleepMinutes),
                        subtext = "${daySummary.napCount} naps",
                        color = SleepColor
                    )
                    SummaryPill(
                        title = "Diapers",
                        value = "${daySummary.wetDiaperCount + daySummary.dirtyDiaperCount} total",
                        subtext = "${daySummary.wetDiaperCount}W / ${daySummary.dirtyDiaperCount}D",
                        color = DiaperColor
                    )
                    SummaryPill(
                        title = "Milk Pumped",
                        value = "${daySummary.totalPumpedVolumeMl} ml",
                        subtext = "In Stash",
                        color = PumpingColor
                    )
                }
            }
        }
    }
}

@Composable
fun SummaryPill(
    title: String,
    value: String,
    subtext: String,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.08f),
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.25f)),
        modifier = Modifier.padding(horizontal = 2.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = subtext,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun BabyStatusBoard(
    logs: List<ActivityLog>,
    ongoing: ActivityLog?,
    currentTimeMillis: Long,
    modifier: Modifier = Modifier
) {
    val lastFeed = logs.firstOrNull {
        it.activityType == ActivityTypes.BOTTLE || it.activityType == ActivityTypes.BREASTFEEDING
    }
    val lastDiaper = logs.firstOrNull { it.activityType == ActivityTypes.DIAPER }
    val lastSleepEnded = logs.firstOrNull {
        it.activityType == ActivityTypes.SLEEP && it.endTimeMillis != null
    }

    val feedTimeText = lastFeed?.let { formatAgo(currentTimeMillis - it.startTimeMillis) } ?: "No feeds"
    val feedSubtext = when {
        lastFeed == null -> "Not logged yet"
        lastFeed.activityType == ActivityTypes.BOTTLE -> {
            val vol = if (lastFeed.volumeMl > 0) "${lastFeed.volumeMl} ml" else null
            val milk = lastFeed.milkType?.ifBlank { null }
            listOfNotNull(milk ?: "Bottle", vol).joinToString(" · ")
        }
        lastFeed.activityType == ActivityTypes.BREASTFEEDING -> {
            val mins = lastFeed.durationSeconds / 60
            if (mins > 0) "Nurse · ${mins}m" else "Breastfeeding"
        }
        else -> "Logged"
    }

    val diaperTimeText = lastDiaper?.let { formatAgo(currentTimeMillis - it.startTimeMillis) } ?: "No diapers"
    val diaperSubtext = lastDiaper?.diaperStatus?.ifBlank { null } ?: if (lastDiaper != null) "Logged" else "Not logged yet"

    val (sleepValue, sleepSubtext) = when {
        ongoing?.activityType == ActivityTypes.SLEEP -> {
            val mins = (currentTimeMillis - ongoing.startTimeMillis) / 60_000L
            "Sleeping" to "In progress · ${IntelligentNeedEngine.formatMinutes(mins)}"
        }
        lastSleepEnded?.endTimeMillis != null -> {
            val endMillis = lastSleepEnded.endTimeMillis!!
            val agoText = formatAgo(currentTimeMillis - endMillis)
            val awakeMins = (currentTimeMillis - endMillis) / 60_000L
            agoText to "Awake · ${IntelligentNeedEngine.formatMinutes(awakeMins)}"
        }
        else -> "No sleep" to "Not logged yet"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("baby_status_board")
    ) {
        Text(
            text = "BABY STATUS BOARD",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.6.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))

        SegmentedStripStatusBoard(
            feedValue = feedTimeText, feedSub = feedSubtext,
            diaperValue = diaperTimeText, diaperSub = diaperSubtext,
            sleepValue = sleepValue, sleepSub = sleepSubtext
        )
    }
}

@Composable
private fun SegmentedStripStatusBoard(
    feedValue: String, feedSub: String,
    diaperValue: String, diaperSub: String,
    sleepValue: String, sleepSub: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusSegment(
                title = "LAST FEED", value = feedValue, subtext = feedSub,
                icon = Icons.Default.WaterDrop, color = FeedingColor, modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(48.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            )
            StatusSegment(
                title = "LAST DIAPER", value = diaperValue, subtext = diaperSub,
                icon = Icons.Default.CleaningServices, color = DiaperColor, modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(48.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            )
            StatusSegment(
                title = "LAST SLEEP", value = sleepValue, subtext = sleepSub,
                icon = Icons.Default.NightlightRound, color = SleepColor, modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatusSegment(
    title: String, value: String, subtext: String,
    icon: ImageVector, color: Color, modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(horizontal = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
            Spacer(modifier = Modifier.width(3.dp))
            Text(text = title, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = color, letterSpacing = 0.4.sp)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(modifier = Modifier.height(1.dp))
        Text(text = subtext, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun formatAgo(diffMillis: Long): String {
    val mins = (diffMillis / 60_000L).toInt().coerceAtLeast(0)
    return when {
        mins < 1 -> "Just now"
        mins < 60 -> "${mins}m ago"
        else -> {
            val h = mins / 60
            val m = mins % 60
            if (m == 0) "${h}h ago" else "${h}h ${m}m ago"
        }
    }
}

@Composable
fun SmartSleepGapPromptCard(
    prompt: com.example.engine.SmartSleepGapPrompt,
    babyName: String,
    onQuickLogNap: (durationMinutes: Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = com.example.ui.theme.SleepColor.copy(alpha = 0.12f)
        ),
        border = BorderStroke(1.5.dp, com.example.ui.theme.SleepColor.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "💤",
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Did $babyName take an unlogged nap?",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                TextButton(onClick = onDismiss) {
                    Text("Dismiss", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }

            Text(
                text = "No sleep logged for ${prompt.minutesSinceLastActivity / 60}h ${prompt.minutesSinceLastActivity % 60}m. Quick-log a nap ending just now:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                prompt.suggestedDurationsMinutes.forEach { duration ->
                    val label = if (duration < 60) "${duration}m" else if (duration == 60) "1h" else "${duration / 60.0}h"
                    com.example.ui.dialogs.SquareChoiceChip(
                        selected = false,
                        onClick = { onQuickLogNap(duration) },
                        label = label,
                        accentColor = com.example.ui.theme.SleepColor,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

