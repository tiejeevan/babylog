package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Bathtub
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ActivityLog
import com.example.data.model.ActivityTypes
import com.example.engine.IntelligentNeedEngine
import com.example.ui.theme.CustomActionColor
import com.example.ui.theme.DiaperColor
import com.example.ui.theme.FeedingColor
import com.example.ui.theme.HealthColor
import com.example.ui.theme.MedicineColor
import com.example.ui.theme.MilestoneColor
import com.example.ui.theme.PumpingColor
import com.example.ui.theme.SleepColor
import com.example.ui.theme.TummyTimeColor
import com.example.ui.viewmodel.BabyCareViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ActivityLogCard(
    log: ActivityLog,
    onDeleteClick: (() -> Unit)? = null,
    onEditClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val (title, icon, color) = when (log.activityType) {
        ActivityTypes.BREASTFEEDING -> Triple("Breastfeeding", Icons.Default.ChildCare, FeedingColor)
        ActivityTypes.BOTTLE -> Triple("Bottle Feeding (${log.volumeMl}ml)", Icons.Default.WaterDrop, FeedingColor)
        ActivityTypes.SLEEP -> Triple("Sleep / Nap", Icons.Default.NightlightRound, SleepColor)
        ActivityTypes.DIAPER -> Triple("Diaper (${log.diaperStatus ?: "Wet"})", Icons.Default.CleaningServices, DiaperColor)
        ActivityTypes.PUMPING -> Triple("Pumping (${log.volumeMl}ml)", Icons.Default.WaterDrop, PumpingColor)
        ActivityTypes.MEDICINE -> Triple("Medicine (${log.medicineName ?: ""})", Icons.Default.MedicalServices, MedicineColor)
        ActivityTypes.TEMPERATURE -> Triple("Temperature (${log.temperatureCelsius}°C)", Icons.Default.Thermostat, HealthColor)
        ActivityTypes.GROWTH -> Triple("Growth Check", Icons.Default.Scale, HealthColor)
        ActivityTypes.BATH -> Triple("Bath Time", Icons.Default.Bathtub, DiaperColor)
        ActivityTypes.TUMMY_TIME -> Triple("Tummy Time", Icons.Default.FitnessCenter, TummyTimeColor)
        ActivityTypes.MILESTONE -> Triple("Milestone Reached", Icons.Default.LocalHospital, MilestoneColor)
        ActivityTypes.CUSTOM -> Triple(
            log.notes.substringBefore(" — ").ifBlank { "Custom" },
            Icons.Default.EditNote,
            CustomActionColor
        )
        else -> Triple(log.activityType, Icons.Default.ChildCare, MaterialTheme.colorScheme.primary)
    }

    val formattedTime = formatLogTimestamp(log.startTimeMillis)

    val detailText = buildString {
        if (log.activityType == ActivityTypes.BREASTFEEDING &&
            (log.leftBreastDurationSec > 0 || log.rightBreastDurationSec > 0)
        ) {
            val leftMin = log.leftBreastDurationSec / 60
            val rightMin = log.rightBreastDurationSec / 60
            append("L ${leftMin}m · R ${rightMin}m  •  ")
        }
        if (log.durationSeconds > 0) {
            append("Duration: ${IntelligentNeedEngine.formatMinutes(log.durationSeconds / 60)}  •  ")
        }
        if (log.volumeMl > 0 && log.activityType != ActivityTypes.BOTTLE) {
            append("Volume: ${log.volumeMl} ml  •  ")
        }
        if (!log.milkType.isNullOrEmpty() && log.activityType == ActivityTypes.BOTTLE) {
            append("${log.milkType}  •  ")
        }
        append("By ${log.caregiverName}")
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("activity_log_card_${log.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = formattedTime,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = color
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = detailText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (log.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "“${log.notes}”",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (onEditClick != null) {
                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (onDeleteClick != null) {
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun formatLogTimestamp(millis: Long): String {
    val todayStart = BabyCareViewModel.startOfDayMillis(System.currentTimeMillis())
    val logDayStart = BabyCareViewModel.startOfDayMillis(millis)
    return if (logDayStart == todayStart) {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(millis))
    } else {
        SimpleDateFormat("MMM d · h:mm a", Locale.getDefault()).format(Date(millis))
    }
}
