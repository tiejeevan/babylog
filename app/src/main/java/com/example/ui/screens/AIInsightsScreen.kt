package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.engine.PatternRangeDays
import com.example.engine.PatternReport
import com.example.ui.components.IntelligentNeedCard
import com.example.ui.components.PatternInsightCard
import com.example.ui.components.PatternLineChart
import com.example.ui.components.PatternStatChip
import com.example.ui.components.PatternTrendBarChart
import com.example.ui.components.TypicalDayHourChart
import com.example.ui.viewmodel.BabyCareViewModel
import com.example.ui.viewmodel.ChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun AIInsightsScreen(viewModel: BabyCareViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("ai_insights_screen")
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "INSIGHTS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.8.sp
                )
            }
            Text(
                text = "Patterns & Care Assistant",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        }

        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Patterns") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Ask AI") }
            )
        }

        when (selectedTab) {
            0 -> PatternsTab(viewModel = viewModel)
            else -> AskAiTab(viewModel = viewModel)
        }
    }
}

@Composable
private fun PatternsTab(viewModel: BabyCareViewModel) {
    val report by viewModel.patternReport.collectAsStateWithLifecycle()
    val range by viewModel.patternRangeDays.collectAsStateWithLifecycle()
    val dayLabelFormat = remember { SimpleDateFormat("M/d", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PatternRangeDays.entries.forEach { option ->
                    FilterChip(
                        selected = range == option,
                        onClick = { viewModel.setPatternRangeDays(option) },
                        label = { Text("${option.days}d") }
                    )
                }
            }
        }

        if (!report.hasEnoughData) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Building your pattern picture",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Logged on ${report.distinctActiveDays} of ${report.rangeDays} days. " +
                                "Keep tracking feeds, sleep, and diapers for about 3 days to unlock habit insights.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            item {
                PatternOverviewStats(report = report)
            }

            items(report.insights) { insight ->
                PatternInsightCard(insight = insight)
            }
        }

        item {
            val feedCounts = report.dailyFeeds.map { it.feedCount.toFloat() }
            val labels = report.dailyFeeds.map { dayLabelFormat.format(Date(it.dayStartMillis)) }
            PatternTrendBarChart(
                values = feedCounts,
                labels = labels,
                barColor = MaterialTheme.colorScheme.primary,
                title = "Feeding",
                subtitle = "Feeds per day"
            )
        }

        item {
            val volumes = report.dailyFeeds.map { it.volumeMl.toFloat() }
            val labels = report.dailyFeeds.map { dayLabelFormat.format(Date(it.dayStartMillis)) }
            PatternLineChart(
                values = volumes,
                labels = labels,
                lineColor = MaterialTheme.colorScheme.primary,
                title = "Feed volume",
                subtitle = "ml per day"
            )
        }

        item {
            val sleepMins = report.dailySleep.map { it.sleepMinutes.toFloat() }
            val labels = report.dailySleep.map { dayLabelFormat.format(Date(it.dayStartMillis)) }
            PatternTrendBarChart(
                values = sleepMins,
                labels = labels,
                barColor = MaterialTheme.colorScheme.tertiary,
                title = "Sleep",
                subtitle = "Minutes per day"
            )
        }

        item {
            val diaperTotals = report.dailyDiapers.map { it.totalCount.toFloat() }
            val labels = report.dailyDiapers.map { dayLabelFormat.format(Date(it.dayStartMillis)) }
            PatternTrendBarChart(
                values = diaperTotals,
                labels = labels,
                barColor = MaterialTheme.colorScheme.secondary,
                title = "Diapers",
                subtitle = "Changes per day"
            )
        }

        item {
            TypicalDayHourChart(bins = report.hourBins)
        }

        if (report.breastBalance.totalSeconds > 0) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "BREAST SIDE BALANCE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.6.sp
                        )
                        Text(
                            text = "L ${report.breastBalance.leftPercent}%  ·  R ${report.breastBalance.rightPercent}%",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun PatternOverviewStats(report: PatternReport) {
    val avgFeeds = if (report.dailyFeeds.isNotEmpty()) {
        report.dailyFeeds.map { it.feedCount }.average()
    } else 0.0
    val avgSleep = if (report.dailySleep.isNotEmpty()) {
        report.dailySleep.map { it.sleepMinutes }.average()
    } else 0.0
    val feedInterval = report.feedInterval?.averageMinutes?.roundToInt()?.let { "${it}m" } ?: "--"
    val nightPct = "${report.nightSleepPercent}%"

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PatternStatChip(
                label = "Avg feeds",
                value = "%.1f".format(avgFeeds),
                modifier = Modifier.weight(1f)
            )
            PatternStatChip(
                label = "Avg sleep",
                value = "${avgSleep.roundToInt()}m",
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PatternStatChip(
                label = "Feed gap",
                value = feedInterval,
                modifier = Modifier.weight(1f)
            )
            PatternStatChip(
                label = "Night sleep",
                value = nightPct,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AskAiTab(viewModel: BabyCareViewModel) {
    val prediction by viewModel.needPrediction.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isThinking by viewModel.isAiThinking.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            item {
                IntelligentNeedCard(
                    prediction = prediction,
                    onActionClick = { }
                )
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "ASK AI CAREGIVER ASSISTANT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.6.sp
                )
            }

            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val promptSuggestions = listOf(
                        "What patterns do you see in our logs?",
                        "What is my baby's wake window?",
                        "How to soothe a gassy baby?",
                        "Safe sleep guidelines"
                    )
                    items(promptSuggestions) { prompt ->
                        FilterChip(
                            selected = false,
                            onClick = { viewModel.askAiAssistant(prompt) },
                            label = { Text(prompt, fontSize = 12.sp) }
                        )
                    }
                }
            }

            items(chatMessages) { message ->
                ChatMessageBubble(message = message)
            }

            if (isThinking) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Gemini AI is analyzing baby routines...",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Surface(
            tonalElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Ask AI caregiver assistant...") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_chat_input"),
                    shape = RoundedCornerShape(20.dp),
                    maxLines = 3
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            val msg = inputText
                            inputText = ""
                            viewModel.askAiAssistant(msg)
                        }
                    },
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .testTag("ai_send_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ChatMessageBubble(message: ChatMessage) {
    val alignment = if (message.isUser) Alignment.End else Alignment.Start
    val bgColor = if (message.isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (message.isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        horizontalAlignment = alignment,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            color = bgColor,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                if (!message.isUser) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
