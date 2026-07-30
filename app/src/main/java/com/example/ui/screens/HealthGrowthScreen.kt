@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.example.data.model.GrowthRecord
import com.example.ui.components.CustomGrowthChart
import com.example.ui.dialogs.AddGrowthDialog
import com.example.ui.theme.HealthColor
import com.example.ui.theme.MilestoneColor
import com.example.ui.theme.PumpingColor
import com.example.ui.viewmodel.BabyCareViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HealthGrowthScreen(viewModel: BabyCareViewModel) {
    val growthRecords by viewModel.growthRecords.collectAsStateWithLifecycle()
    val medicalRecords by viewModel.medicalRecords.collectAsStateWithLifecycle()
    val milkStash by viewModel.milkStash.collectAsStateWithLifecycle()
    val milestones by viewModel.milestones.collectAsStateWithLifecycle()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showAddGrowthDialog by remember { mutableStateOf(false) }
    var showAddMilkDialog by remember { mutableStateOf(false) }
    var showAddMedicalDialog by remember { mutableStateOf(false) }

    val tabs = listOf("Growth", "Milk Bank", "Medical", "Milestones")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("health_growth_screen")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "HEALTH & DEVELOPMENT OS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.8.sp
            )
            Text(
                text = "Growth, Milk Stash & Vaccines",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        }

        SecondaryTabRow(selectedTabIndex = selectedTabIndex) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(title, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("health_tab_$index")
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (selectedTabIndex) {
            0 -> GrowthTabContent(
                growthRecords = growthRecords,
                onAddGrowthClick = { showAddGrowthDialog = true }
            )
            1 -> MilkBankTabContent(
                milkStash = milkStash,
                onAddMilkClick = { showAddMilkDialog = true }
            )
            2 -> MedicalTabContent(
                medicalRecords = medicalRecords,
                onAddMedicalClick = { showAddMedicalDialog = true },
                onToggleMedical = { viewModel.toggleMedicalRecord(it) }
            )
            3 -> MilestonesTabContent(
                milestones = milestones,
                onToggleMilestone = { viewModel.toggleMilestone(it) }
            )
        }
    }

    if (showAddGrowthDialog) {
        AddGrowthDialog(
            onDismiss = { showAddGrowthDialog = false },
            onConfirm = { w, h, head, notes, _ ->
                viewModel.addGrowthRecord(w, h, head, notes)
                showAddGrowthDialog = false
            }
        )
    }

    if (showAddMilkDialog) {
        var volText by remember { mutableStateOf("150") }
        var location by remember { mutableStateOf("Freezer") }

        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAddMilkDialog = false },
            title = { Text("Log Expressed Milk Stash 💧", fontWeight = FontWeight.Bold, color = PumpingColor) },
            text = {
                Column {
                    OutlinedTextField(
                        value = volText,
                        onValueChange = { volText = it },
                        label = { Text("Volume (ml)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Fridge", "Freezer").forEach { loc ->
                            androidx.compose.material3.FilterChip(
                                selected = (location == loc),
                                onClick = { location = loc },
                                label = { Text(loc) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val vol = volText.toIntOrNull() ?: 150
                        viewModel.addMilkStash(vol, location, "Express pumping")
                        showAddMilkDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PumpingColor)
                ) {
                    Text("Add to Milk Stash")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddMilkDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showAddMedicalDialog) {
        var titleText by remember { mutableStateOf("4-Month Vaccination") }
        var typeText by remember { mutableStateOf("Vaccine") }
        var detailsText by remember { mutableStateOf("Pediatrician clinic visit") }

        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAddMedicalDialog = false },
            title = { Text("Add Medical Record / Vaccine 🏥", fontWeight = FontWeight.Bold, color = HealthColor) },
            text = {
                Column {
                    OutlinedTextField(
                        value = titleText,
                        onValueChange = { titleText = it },
                        label = { Text("Appointment / Vaccine Title") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = detailsText,
                        onValueChange = { detailsText = it },
                        label = { Text("Details / Clinic Address") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addMedicalRecord(typeText, titleText, detailsText)
                        showAddMedicalDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = HealthColor)
                ) {
                    Text("Save Record")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddMedicalDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun GrowthTabContent(
    growthRecords: List<GrowthRecord>,
    onAddGrowthClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Button(
                onClick = onAddGrowthClick,
                colors = ButtonDefaults.buttonColors(containerColor = HealthColor),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_growth_btn")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Log Growth Measurement", fontWeight = FontWeight.Bold)
            }
        }

        item {
            CustomGrowthChart(
                records = growthRecords,
                metricType = "Weight (kg)",
                lineColor = HealthColor
            )
        }

        item {
            CustomGrowthChart(
                records = growthRecords,
                metricType = "Height (cm)",
                lineColor = PumpingColor
            )
        }

        item {
            CustomGrowthChart(
                records = growthRecords,
                metricType = "Head (cm)",
                lineColor = MilestoneColor
            )
        }
    }
}

@Composable
fun MilkBankTabContent(
    milkStash: List<com.example.data.model.MilkStashItem>,
    onAddMilkClick: () -> Unit
) {
    val totalVolume = milkStash.filter { !it.isUsed }.sumOf { it.volumeMl }
    val freezerVolume = milkStash.filter { !it.isUsed && it.location == "Freezer" }.sumOf { it.volumeMl }
    val fridgeVolume = milkStash.filter { !it.isUsed && it.location == "Fridge" }.sumOf { it.volumeMl }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = PumpingColor.copy(alpha = 0.12f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "BREAST MILK STASH INVENTORY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = PumpingColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$totalVolume ml Total Stash",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("❄️ Freezer: $freezerVolume ml", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("🧊 Fridge: $fridgeVolume ml", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }
        }

        item {
            Button(
                onClick = onAddMilkClick,
                colors = ButtonDefaults.buttonColors(containerColor = PumpingColor),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_milk_btn")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Log Expressed Milk Bag", fontWeight = FontWeight.Bold)
            }
        }

        items(milkStash) { item ->
            val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.WaterDrop, contentDescription = null, tint = PumpingColor)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("${item.volumeMl} ml (${item.location})", fontWeight = FontWeight.Bold)
                            Text("Pumped: ${dateFormat.format(Date(item.pumpedDateMillis))}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Surface(
                        color = Color(0xFFE8F5E9),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Fresh & Safe", fontSize = 11.sp, color = Color(0xFF2E7D32), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun MedicalTabContent(
    medicalRecords: List<com.example.data.model.MedicalRecord>,
    onAddMedicalClick: () -> Unit,
    onToggleMedical: (com.example.data.model.MedicalRecord) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Button(
                onClick = onAddMedicalClick,
                colors = ButtonDefaults.buttonColors(containerColor = HealthColor),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Schedule Medical Visit / Vaccine", fontWeight = FontWeight.Bold)
            }
        }

        items(medicalRecords) { record ->
            val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (record.isCompleted) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = record.isCompleted,
                        onCheckedChange = { onToggleMedical(record) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = record.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "${record.recordType}  •  ${dateFormat.format(Date(record.dateMillis))}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (record.details.isNotBlank()) {
                            Text(text = record.details, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MilestonesTabContent(
    milestones: List<com.example.data.model.MilestoneRecord>,
    onToggleMilestone: (com.example.data.model.MilestoneRecord) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "DEVELOPMENTAL MILESTONES CHECKLIST",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(milestones) { milestone ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = milestone.isAchieved,
                        onCheckedChange = { onToggleMilestone(milestone) },
                        modifier = Modifier.testTag("milestone_checkbox_${milestone.id}")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = MilestoneColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = milestone.category,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MilestoneColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = milestone.title,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = milestone.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
