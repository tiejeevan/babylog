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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.GrowthRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CustomGrowthChart(
    records: List<GrowthRecord>,
    metricType: String, // "Weight (kg)", "Height (cm)", "Head (cm)"
    lineColor: Color = MaterialTheme.colorScheme.primary
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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
                Column {
                    Text(
                        text = metricType.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.6.sp
                    )
                    Text(
                        text = "WHO Growth Percentile Standard",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    color = lineColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "50th–85th Percentile",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = lineColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (records.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No growth records yet. Add a checkup measure!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val values = records.map {
                    when (metricType) {
                        "Height (cm)" -> it.heightCm
                        "Head (cm)" -> it.headCircumferenceCm
                        else -> it.weightKg
                    }
                }

                val minVal = (values.minOrNull() ?: 0.0) * 0.8
                val maxVal = (values.maxOrNull() ?: 10.0) * 1.2
                val range = if (maxVal - minVal == 0.0) 1.0 else maxVal - minVal

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    val width = size.width
                    val height = size.height
                    val paddingX = 40f
                    val paddingY = 20f

                    val usableW = width - (paddingX * 2)
                    val usableH = height - (paddingY * 2)

                    // Draw WHO percentile background band (50th to 85th)
                    val bandPath = Path().apply {
                        moveTo(paddingX, paddingY + usableH * 0.2f)
                        lineTo(paddingX + usableW, paddingY + usableH * 0.1f)
                        lineTo(paddingX + usableW, paddingY + usableH * 0.7f)
                        lineTo(paddingX, paddingY + usableH * 0.8f)
                        close()
                    }
                    drawPath(
                        path = bandPath,
                        color = lineColor.copy(alpha = 0.08f)
                    )

                    // Draw grid lines
                    for (i in 0..3) {
                        val y = paddingY + (usableH * i / 3f)
                        drawLine(
                            color = Color.LightGray.copy(alpha = 0.4f),
                            start = Offset(paddingX, y),
                            end = Offset(width - paddingX, y),
                            strokeWidth = 1f
                        )
                    }

                    // Plot points and line
                    val points = values.mapIndexed { index, valDouble ->
                        val x = if (values.size == 1) width / 2f else paddingX + (usableW * index / (values.size - 1))
                        val y = paddingY + usableH - (((valDouble - minVal) / range) * usableH).toFloat()
                        Offset(x, y)
                    }

                    if (points.size > 1) {
                        val path = Path().apply {
                            moveTo(points[0].x, points[0].y)
                            for (i in 1 until points.size) {
                                lineTo(points[i].x, points[i].y)
                            }
                        }
                        drawPath(
                            path = path,
                            color = lineColor,
                            style = Stroke(width = 6f)
                        )
                    }

                    // Draw circles for data points
                    points.forEach { point ->
                        drawCircle(
                            color = lineColor,
                            radius = 10f,
                            center = point
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 5f,
                            center = point
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Date axis labels
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
                    records.forEach { record ->
                        Text(
                            text = dateFormat.format(Date(record.dateMillis)),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
