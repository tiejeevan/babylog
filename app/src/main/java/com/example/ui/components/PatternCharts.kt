package com.example.ui.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.HourBin
import com.example.engine.PatternInsight
import com.example.engine.PatternReport
import kotlin.math.max

@Composable
fun PatternInsightCard(
    insight: PatternInsight,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Lightbulb,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = insight.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = insight.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun DashboardPatternHighlightCard(
    report: PatternReport,
    onOpenInsights: () -> Unit,
    modifier: Modifier = Modifier
) {
    val insight = report.highlightInsight
    Card(
        onClick = onOpenInsights,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Insights,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "PATTERNS & HABITS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.6.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (insight != null && report.hasEnoughData) {
                    Text(
                        text = insight.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = insight.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                } else {
                    val daysLeft = (3 - report.distinctActiveDays).coerceAtLeast(1)
                    Text(
                        text = "Keep logging for $daysLeft more ${if (daysLeft == 1) "day" else "days"}",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Patterns unlock after about 3 days of feeds, sleep, and diapers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = "View",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun PatternTrendBarChart(
    values: List<Float>,
    labels: List<String>,
    barColor: Color,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.6.sp
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(12.dp))
            val maxVal = max(values.maxOrNull() ?: 0f, 1f)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                val n = values.size.coerceAtLeast(1)
                val gap = size.width * 0.04f / n
                val barWidth = (size.width - gap * (n + 1)) / n
                values.forEachIndexed { index, value ->
                    val h = (value / maxVal) * size.height * 0.9f
                    val x = gap + index * (barWidth + gap)
                    val y = size.height - h
                    drawRoundRect(
                        color = barColor.copy(alpha = 0.85f),
                        topLeft = Offset(x, y),
                        size = Size(barWidth, h),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                }
            }
            if (labels.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val showEvery = when {
                        labels.size <= 7 -> 1
                        labels.size <= 14 -> 2
                        else -> 5
                    }
                    labels.forEachIndexed { index, label ->
                        if (index % showEvery == 0 || index == labels.lastIndex) {
                            Text(
                                text = label,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PatternLineChart(
    values: List<Float>,
    labels: List<String>,
    lineColor: Color,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.6.sp
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(12.dp))
            val maxVal = max(values.maxOrNull() ?: 0f, 1f)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                if (values.isEmpty()) return@Canvas
                val path = Path()
                val stepX = if (values.size == 1) 0f else size.width / (values.size - 1)
                values.forEachIndexed { index, value ->
                    val x = index * stepX
                    val y = size.height - (value / maxVal) * size.height * 0.9f - size.height * 0.05f
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    drawCircle(color = lineColor, radius = 5f, center = Offset(x, y))
                }
                drawPath(path = path, color = lineColor, style = Stroke(width = 4f))
            }
            if (labels.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(labels.first(), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(labels.last(), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun TypicalDayHourChart(
    bins: List<HourBin>,
    modifier: Modifier = Modifier
) {
    val maxVal = max(bins.maxOfOrNull { it.totalCount } ?: 0, 1)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "TYPICAL DAY",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.6.sp
            )
            Text(
                text = "Activity by hour of day",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(12.dp))
            val feedColor = MaterialTheme.colorScheme.primary
            val sleepColor = MaterialTheme.colorScheme.tertiary
            val diaperColor = MaterialTheme.colorScheme.secondary
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
            ) {
                val n = 24
                val gap = 2f
                val barWidth = (size.width - gap * (n + 1)) / n
                bins.forEach { bin ->
                    val total = bin.totalCount.toFloat()
                    if (total <= 0f) return@forEach
                    val h = (total / maxVal) * size.height * 0.9f
                    val x = gap + bin.hour * (barWidth + gap)
                    var y = size.height
                    // stacked: diaper, sleep, feed from bottom
                    val segments = listOf(
                        bin.diaperCount.toFloat() to diaperColor,
                        bin.sleepCount.toFloat() to sleepColor,
                        bin.feedCount.toFloat() to feedColor
                    )
                    segments.forEach { (count, color) ->
                        if (count <= 0f) return@forEach
                        val segH = (count / total) * h
                        y -= segH
                        drawRoundRect(
                            color = color.copy(alpha = 0.85f),
                            topLeft = Offset(x, y),
                            size = Size(barWidth, segH),
                            cornerRadius = CornerRadius(2f, 2f)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LegendDot("Feeds", MaterialTheme.colorScheme.primary)
                LegendDot("Sleep", MaterialTheme.colorScheme.tertiary)
                LegendDot("Diapers", MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun PatternStatChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = label.uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.4.sp
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}
