package com.example.ui.components

import com.example.ui.theme.parseHexColor
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ActivityLog
import com.example.data.model.ActivityTypes
import com.example.data.model.BabyProfile
import com.example.data.model.CaregiverProfile
import com.example.engine.BabyNeedPrediction
import com.example.engine.IntelligentNeedEngine
import com.example.engine.TodaySummary
import com.example.engine.UrgencyLevel
import com.example.ui.theme.DiaperColor
import com.example.ui.theme.FeedingColor
import com.example.ui.theme.HealthColor
import com.example.ui.theme.MedicineColor
import com.example.ui.theme.MilestoneColor
import com.example.ui.theme.PumpingColor
import com.example.ui.theme.SleepColor
import com.example.ui.theme.TummyTimeColor
import java.util.concurrent.TimeUnit

@Composable
fun TopBabyHeader(
    profile: BabyProfile?,
    activeCaregiver: CaregiverProfile?,
    syncStatus: String,
    onSwitchCaregiverClick: () -> Unit,
    onProfileClick: () -> Unit
) {
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
                    modifier = Modifier.clickable { onProfileClick() }
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
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
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = profile?.name ?: "Your Baby",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(12.dp)
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
                            text = "Live Care OS • 24/7 Monitoring",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

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
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
    onStopClick: () -> Unit
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
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
                Column {
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

            Button(
                onClick = onStopClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("stop_timer_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("FINISH", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickActionGrid(
    onActionSelected: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "ONE-HANDED QUICK ACTIONS",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )

        val actions = listOf(
            QuickActionItem("Bottle", ActivityTypes.BOTTLE, Icons.Default.WaterDrop, FeedingColor),
            QuickActionItem("Breastfeed", ActivityTypes.BREASTFEEDING, Icons.Default.ChildCare, FeedingColor),
            QuickActionItem("Diaper", ActivityTypes.DIAPER, Icons.Default.CleaningServices, DiaperColor),
            QuickActionItem("Sleep", ActivityTypes.SLEEP, Icons.Default.NightlightRound, SleepColor),
            QuickActionItem("Pumping", ActivityTypes.PUMPING, Icons.Default.WaterDrop, PumpingColor),
            QuickActionItem("Medicine", ActivityTypes.MEDICINE, Icons.Default.MedicalServices, MedicineColor),
            QuickActionItem("Temp", ActivityTypes.TEMPERATURE, Icons.Default.Thermostat, HealthColor),
            QuickActionItem("Growth", ActivityTypes.GROWTH, Icons.Default.Scale, HealthColor),
            QuickActionItem("Bath", ActivityTypes.BATH, Icons.Default.Bathtub, DiaperColor),
            QuickActionItem("Tummy Time", ActivityTypes.TUMMY_TIME, Icons.Default.FitnessCenter, TummyTimeColor),
            QuickActionItem("Milestone", ActivityTypes.MILESTONE, Icons.Default.LocalHospital, MilestoneColor)
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            maxItemsInEachRow = 4
        ) {
            actions.forEach { action ->
                QuickActionButton(
                    item = action,
                    onClick = { onActionSelected(action.type) }
                )
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
            .size(80.dp)
            .testTag("quick_action_${item.type.lowercase()}")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
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

@Composable
fun TodaySummaryBar(summary: TodaySummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "TODAY'S ROUTINE SUMMARY",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.6.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SummaryPill(
                    title = "Feedings",
                    value = "${summary.feedCount} times",
                    subtext = "${summary.totalFeedVolumeMl} ml",
                    color = FeedingColor
                )
                SummaryPill(
                    title = "Sleep",
                    value = IntelligentNeedEngine.formatMinutes(summary.totalSleepMinutes),
                    subtext = "${summary.napCount} naps",
                    color = SleepColor
                )
                SummaryPill(
                    title = "Diapers",
                    value = "${summary.wetDiaperCount + summary.dirtyDiaperCount} total",
                    subtext = "${summary.wetDiaperCount}W / ${summary.dirtyDiaperCount}D",
                    color = DiaperColor
                )
                SummaryPill(
                    title = "Milk Pumped",
                    value = "${summary.totalPumpedVolumeMl} ml",
                    subtext = "In Stash",
                    color = PumpingColor
                )
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Text(
            text = title,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = subtext,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
