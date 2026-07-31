package com.example.ui.dialogs

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.IntelligentNeedEngine
import com.example.engine.NapPlacementState
import com.example.engine.SmartSleepGapPrompt
import com.example.engine.TimelineAnchor
import com.example.ui.theme.SleepColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val OverlapRed = Color(0xFFEF5350)
private val HapticThrottleMs = 80L

@Composable
fun SmartNapAdjusterSheet(
    prompt: SmartSleepGapPrompt,
    babyName: String,
    onConfirm: (startMillis: Long, endMillis: Long) -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var placement by remember(prompt) {
        mutableStateOf(
            NapPlacementState(
                gapStartMillis = prompt.gapStartMillis,
                gapEndMillis = prompt.gapEndMillis,
                napStartMillis = prompt.defaultNapStartMillis,
                napEndMillis = prompt.defaultNapStartMillis +
                    prompt.defaultNapDurationMinutes * 60_000L,
                intermediateActivities = prompt.intermediateActivities
            ).clamp()
        )
    }
    var lastHapticAt by remember { mutableLongStateOf(0L) }
    var wasOverlapping by remember { mutableStateOf(false) }

    fun maybeHaptic(type: HapticFeedbackType) {
        val now = System.currentTimeMillis()
        if (now - lastHapticAt >= HapticThrottleMs) {
            haptic.performHapticFeedback(type)
            lastHapticAt = now
        }
    }

    fun applyPlacement(next: NapPlacementState, snapDuration: Boolean = false) {
        val clamped = next.clamp()
        val prevDuration = placement.durationMinutes
        placement = clamped
        if (snapDuration) {
            val preset = NapPlacementState.nearestPresetMinutes(clamped.durationMinutes)
            if (preset != null && preset != prevDuration) {
                maybeHaptic(HapticFeedbackType.TextHandleMove)
            }
        }
        if (clamped.hasOverlap && !wasOverlapping) {
            maybeHaptic(HapticFeedbackType.LongPress)
        }
        wasOverlapping = clamped.hasOverlap
    }

    ActionModalBottomSheet(
        onDismissRequest = onDismiss,
        title = "Adjust Nap Timeline",
        accentColor = SleepColor,
        icon = Icons.Default.NightlightRound,
        modifier = Modifier.testTag("smart_nap_adjuster_sheet")
    ) {
        Text(
            text = "Unlogged Gap: ${IntelligentNeedEngine.formatGapRange(prompt.gapStartMillis, prompt.gapEndMillis)}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Suggested: ~${prompt.defaultNapDurationMinutes}m based on ${babyName}'s routine",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        prompt.prevActivity?.let { prev ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "After: ${emojiFor(prev)} ${prev.title} at ${formatClock(prev.timeMillis)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        prompt.nextActivity?.let { next ->
            Text(
                text = "Before: ${emojiFor(next)} ${next.title} at ${formatClock(next.timeMillis)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        GapTimelineAdjuster(
            placement = placement,
            onPlacementChange = { next, fromResize ->
                applyPlacement(next, snapDuration = fromResize)
                val atBoundary =
                    next.napStartMillis <= prompt.gapStartMillis + 15 * 60_000L + 1_000L ||
                        next.napEndMillis >= prompt.gapEndMillis - 15 * 60_000L - 1_000L
                if (atBoundary) maybeHaptic(HapticFeedbackType.LongPress)
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Tap-to-snap boundary buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            prompt.prevActivity?.let { prev ->
                Surface(
                    onClick = {
                        applyPlacement(placement.snapAfter(prev))
                        maybeHaptic(HapticFeedbackType.TextHandleMove)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("snap_after_btn"),
                    shape = RoundedCornerShape(10.dp),
                    color = SleepColor.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SleepColor.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "Start after ${prev.title}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = SleepColor,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)
                    )
                }
            }
            prompt.nextActivity?.takeIf { it.activityType != "NOW" || prompt.prevActivity != null }?.let { next ->
                Surface(
                    onClick = {
                        applyPlacement(placement.snapBefore(next))
                        maybeHaptic(HapticFeedbackType.TextHandleMove)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("snap_before_btn"),
                    shape = RoundedCornerShape(10.dp),
                    color = SleepColor.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SleepColor.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "End before ${next.title}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = SleepColor,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Metrics
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MetricCell("Start", formatClock(placement.napStartMillis))
            MetricCell("End", formatClock(placement.napEndMillis))
            MetricCell("Nap", "${placement.durationMinutes}m")
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MetricCell(
                "Awake before",
                IntelligentNeedEngine.formatMinutes(placement.awakeBeforeMillis / 60_000L)
            )
            MetricCell(
                "Awake after",
                IntelligentNeedEngine.formatMinutes(placement.awakeAfterMillis / 60_000L)
            )
        }

        if (placement.hasOverlap) {
            Spacer(modifier = Modifier.height(8.dp))
            val conflict = placement.overlappingActivities.first()
            Text(
                text = "Nap overlaps ${conflict.title} at ${formatClock(conflict.timeMillis)}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = OverlapRed,
                modifier = Modifier.testTag("overlap_warning")
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Duration presets
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            NapPlacementState.PRESET_DURATIONS.forEach { minutes ->
                val selected = placement.durationMinutes == minutes
                val chipLabel = when {
                    minutes < 60 -> "${minutes}m"
                    minutes % 60 == 0 -> "${minutes / 60}h"
                    else -> String.format(Locale.US, "%.1fh", minutes / 60.0)
                }
                SquareChoiceChip(
                    selected = selected,
                    onClick = {
                        applyPlacement(placement.withCenteredDuration(minutes), snapDuration = true)
                        maybeHaptic(HapticFeedbackType.TextHandleMove)
                    },
                    label = chipLabel,
                    accentColor = SleepColor,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SquareActionButton(
            onClick = {
                onConfirm(placement.napStartMillis, placement.napEndMillis)
            },
            enabled = !placement.hasOverlap,
            containerColor = SleepColor,
            text = "Save Nap",
            modifier = Modifier.testTag("confirm_intelligent_nap_btn")
        )
    }
}

@Composable
private fun MetricCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun GapTimelineAdjuster(
    placement: NapPlacementState,
    onPlacementChange: (NapPlacementState, fromResize: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var trackWidthPx by remember { mutableStateOf(1f) }
    val gapSpan = (placement.gapEndMillis - placement.gapStartMillis).coerceAtLeast(1L)

    fun millisToX(millis: Long): Float {
        val ratio = (millis - placement.gapStartMillis).toFloat() / gapSpan
        return ratio.coerceIn(0f, 1f) * trackWidthPx
    }

    fun xToMillis(x: Float): Long {
        val ratio = (x / trackWidthPx).coerceIn(0f, 1f)
        return placement.gapStartMillis + (ratio * gapSpan).toLong()
    }

    val animatedStart by animateFloatAsState(
        targetValue = millisToX(placement.napStartMillis),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "napStart"
    )
    val animatedEnd by animateFloatAsState(
        targetValue = millisToX(placement.napEndMillis),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "napEnd"
    )

    val currentPlacement by rememberUpdatedState(placement)
    val currentTrackWidthPx by rememberUpdatedState(trackWidthPx)
    val currentOnPlacementChange by rememberUpdatedState(onPlacementChange)
    val currentAnimatedStart by rememberUpdatedState(animatedStart)
    val currentAnimatedEnd by rememberUpdatedState(animatedEnd)

    val blockColor = if (placement.hasOverlap) OverlapRed else SleepColor
    val overlappingIds = placement.overlappingActivities.map { it.timeMillis }.toSet()

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .onSizeChanged { trackWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .testTag("gap_timeline_track")
        ) {
            // Track baseline
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .height(6.dp)
                    .padding(horizontal = 4.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            )

            // Intermediate pins
            placement.intermediateActivities.forEach { anchor ->
                val x = millisToX(anchor.timeMillis)
                val isConflict = anchor.timeMillis in overlappingIds
                Box(
                    modifier = Modifier
                        .offset { IntOffset(x.roundToInt() - with(density) { 5.dp.roundToPx() }, 0) }
                        .align(Alignment.CenterStart)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isConflict) OverlapRed else MaterialTheme.colorScheme.outline)
                )
            }

            // Nap block
            val left = animatedStart
            val width = (animatedEnd - animatedStart).coerceAtLeast(with(density) { 24.dp.toPx() })
            Box(
                modifier = Modifier
                    .offset { IntOffset(left.roundToInt(), 0) }
                    .width(with(density) { width.toDp() })
                    .fillMaxHeight()
                    .padding(vertical = 10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(blockColor.copy(alpha = 0.35f))
                    .border(
                        width = 2.dp,
                        color = blockColor,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            if (currentTrackWidthPx > 0f) {
                                val deltaMillis = ((dragAmount / currentTrackWidthPx) * gapSpan).toLong()
                                currentOnPlacementChange(currentPlacement.panBy(deltaMillis), false)
                            }
                        }
                    }
                    .testTag("nap_drag_block"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "💤 ${placement.durationMinutes}m",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = blockColor,
                    maxLines = 1
                )
            }

            // Left resize handle
            Box(
                modifier = Modifier
                    .offset { IntOffset(animatedStart.roundToInt() - with(density) { 8.dp.roundToPx() }, 0) }
                    .align(Alignment.CenterStart)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(blockColor)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val newStart = xToMillis(currentAnimatedStart + dragAmount.x)
                            currentOnPlacementChange(currentPlacement.resizeStart(newStart), true)
                        }
                    }
                    .testTag("nap_resize_start")
            )

            // Right resize handle
            Box(
                modifier = Modifier
                    .offset { IntOffset(animatedEnd.roundToInt() - with(density) { 8.dp.roundToPx() }, 0) }
                    .align(Alignment.CenterStart)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(blockColor)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val newEnd = xToMillis(currentAnimatedEnd + dragAmount.x)
                            currentOnPlacementChange(currentPlacement.resizeEnd(newEnd), true)
                        }
                    }
                    .testTag("nap_resize_end")
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatClock(placement.gapStartMillis),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                text = formatClock(placement.gapEndMillis),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

private fun formatClock(millis: Long): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(millis))

private fun emojiFor(anchor: TimelineAnchor): String = when (anchor.activityType) {
    "BOTTLE", "BREASTFEEDING" -> "🍼"
    "DIAPER" -> "👶"
    "SLEEP" -> "💤"
    "NOW" -> "⏱"
    "MEDICINE" -> "💊"
    else -> "•"
}
