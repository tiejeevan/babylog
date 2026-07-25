package com.example.ui.dialogs

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.data.model.BabyProfile
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ActivityTypes
import com.example.ui.theme.DiaperColor
import com.example.ui.theme.FeedingColor
import com.example.ui.theme.HealthColor
import com.example.ui.theme.MedicineColor
import com.example.ui.theme.PumpingColor
import com.example.ui.theme.SleepColor

@Composable
fun LogBottleDialog(
    onDismiss: () -> Unit,
    onConfirm: (volumeMl: Int, milkType: String, notes: String) -> Unit
) {
    var volumeText by remember { mutableStateOf("120") }
    var selectedMilkType by remember { mutableStateOf("Breast Milk") }
    var notesText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Log Bottle Feeding 🍼",
                fontWeight = FontWeight.Bold,
                color = FeedingColor
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = "Milk Type:", style = MaterialTheme.typography.bodyMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Breast Milk", "Formula", "Water").forEach { type ->
                        FilterChip(
                            selected = (selectedMilkType == type),
                            onClick = { selectedMilkType = type },
                            label = { Text(type) },
                            modifier = Modifier.testTag("chip_milk_$type")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = volumeText,
                    onValueChange = { volumeText = it },
                    label = { Text("Volume (ml)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bottle_volume_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val vol = volumeText.toIntOrNull() ?: 120
                    onConfirm(vol, selectedMilkType, notesText)
                },
                colors = ButtonDefaults.buttonColors(containerColor = FeedingColor),
                modifier = Modifier.testTag("confirm_bottle_log")
            ) {
                Text("Log Feeding", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun LogDiaperDialog(
    onDismiss: () -> Unit,
    onConfirm: (status: String, notes: String) -> Unit
) {
    var selectedStatus by remember { mutableStateOf("Wet") }
    var notesText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Log Diaper Change 👶",
                fontWeight = FontWeight.Bold,
                color = DiaperColor
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = "Diaper Status:", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Wet", "Dirty", "Both", "Dry").forEach { status ->
                        FilterChip(
                            selected = (selectedStatus == status),
                            onClick = { selectedStatus = status },
                            label = { Text(status) },
                            modifier = Modifier.testTag("chip_diaper_$status")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Notes (e.g. stool color / rash cream applied)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedStatus, notesText) },
                colors = ButtonDefaults.buttonColors(containerColor = DiaperColor),
                modifier = Modifier.testTag("confirm_diaper_log")
            ) {
                Text("Log Diaper", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun LogMedicineDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, dosage: String, notes: String) -> Unit
) {
    var nameText by remember { mutableStateOf("Infant Tylenol") }
    var dosageText by remember { mutableStateOf("1.25 ml") }
    var notesText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Log Medicine 💊",
                fontWeight = FontWeight.Bold,
                color = MedicineColor
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    label = { Text("Medicine Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = dosageText,
                    onValueChange = { dosageText = it },
                    label = { Text("Dosage") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(nameText, dosageText, notesText) },
                colors = ButtonDefaults.buttonColors(containerColor = MedicineColor)
            ) {
                Text("Save Medicine Log", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun LogTemperatureDialog(
    onDismiss: () -> Unit,
    onConfirm: (tempCelsius: Double, notes: String) -> Unit
) {
    var tempText by remember { mutableStateOf("36.8") }
    var notesText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Log Temperature 🌡️",
                fontWeight = FontWeight.Bold,
                color = HealthColor
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = tempText,
                    onValueChange = { tempText = it },
                    label = { Text("Temperature (°C)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Notes (Temporal, Axillary, Rectal)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val temp = tempText.toDoubleOrNull() ?: 36.8
                    onConfirm(temp, notesText)
                },
                colors = ButtonDefaults.buttonColors(containerColor = HealthColor)
            ) {
                Text("Log Temp", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AddGrowthDialog(
    onDismiss: () -> Unit,
    onConfirm: (weightKg: Double, heightCm: Double, headCm: Double, notes: String) -> Unit
) {
    var weightText by remember { mutableStateOf("5.8") }
    var heightText by remember { mutableStateOf("60.0") }
    var headText by remember { mutableStateOf("39.5") }
    var notesText by remember { mutableStateOf("Pediatrician checkup") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Record Growth Measure 📈",
                fontWeight = FontWeight.Bold,
                color = HealthColor
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it },
                    label = { Text("Weight (kg)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = heightText,
                    onValueChange = { heightText = it },
                    label = { Text("Height (cm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = headText,
                    onValueChange = { headText = it },
                    label = { Text("Head Circumference (cm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val w = weightText.toDoubleOrNull() ?: 5.8
                    val h = heightText.toDoubleOrNull() ?: 60.0
                    val head = headText.toDoubleOrNull() ?: 39.5
                    onConfirm(w, h, head, notesText)
                },
                colors = ButtonDefaults.buttonColors(containerColor = HealthColor)
            ) {
                Text("Save Measurement", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AddCaregiverDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, relationship: String, role: String, pin: String) -> Unit
) {
    var nameText by remember { mutableStateOf("") }
    var relationshipText by remember { mutableStateOf("Father") }
    var selectedRole by remember { mutableStateOf("Caregiver") }
    var pinText by remember { mutableStateOf("1234") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Connect Family Caregiver 👨‍👩‍👧",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    label = { Text("Caregiver Name (e.g. Alex, Sarah, Grandma)") },
                    modifier = Modifier.fillMaxWidth().testTag("add_caregiver_name_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = relationshipText,
                    onValueChange = { relationshipText = it },
                    label = { Text("Relationship (Mom, Dad, Babysitter, Pediatrician)") },
                    modifier = Modifier.fillMaxWidth().testTag("add_caregiver_rel_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = pinText,
                    onValueChange = { if (it.length <= 4) pinText = it },
                    label = { Text("4-Digit Security PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth().testTag("add_caregiver_pin_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("Permission Role:", style = MaterialTheme.typography.bodyMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Admin", "Caregiver", "Viewer").forEach { role ->
                        FilterChip(
                            selected = (selectedRole == role),
                            onClick = { selectedRole = role },
                            label = { Text(role) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (nameText.isNotBlank()) {
                        val pin = if (pinText.length == 4) pinText else "1234"
                        onConfirm(nameText, relationshipText, selectedRole, pin)
                    }
                },
                modifier = Modifier.testTag("confirm_add_caregiver_btn")
            ) {
                Text("Add Caregiver", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EnterPinDialog(
    caregiverName: String,
    onDismiss: () -> Unit,
    onConfirm: (pin: String) -> Unit
) {
    var pinText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Enter Security PIN 🔒",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Specify identity: Verify 4-digit PIN for $caregiverName to activate real-time caregiver logging",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = pinText,
                    onValueChange = {
                        if (it.length <= 4) {
                            pinText = it
                            errorMessage = null
                        }
                    },
                    label = { Text("4-Digit PIN") },
                    isError = errorMessage != null,
                    supportingText = {
                        if (errorMessage != null) {
                            Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth().testTag("pin_entry_input"),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pinText.length == 4) {
                        onConfirm(pinText)
                    } else {
                        errorMessage = "Please enter 4 digits"
                    }
                },
                modifier = Modifier.testTag("submit_pin_btn")
            ) {
                Text("Verify & Switch", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SetupBabyProfileDialog(
    initialProfile: BabyProfile?,
    onDismiss: () -> Unit,
    onSaveProfile: (profile: BabyProfile) -> Unit
) {
    var nameText by remember { mutableStateOf(if (initialProfile?.name == "Your Baby" || initialProfile?.name == "Emma") "" else (initialProfile?.name ?: "")) }
    var selectedGender by remember { mutableStateOf(initialProfile?.gender ?: "Boy") }
    var feedIntervalHoursText by remember { mutableStateOf(((initialProfile?.targetFeedingIntervalMinutes ?: 180) / 60.0).toString()) }
    var napIntervalHoursText by remember { mutableStateOf(((initialProfile?.targetNapIntervalMinutes ?: 150) / 60.0).toString()) }
    var caregiverNameText by remember { mutableStateOf(initialProfile?.primaryCaregiverName ?: "Mom") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = if (initialProfile?.isInitialSetupDone == true) "Edit Baby Profile 👶" else "Welcome! Set Up Your Baby's Profile 👶",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                Text(
                    text = "Personalize your baby's name, routine intervals, and system alerts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    label = { Text("Baby's Name (e.g. Liam, Maya, Noah)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("setup_baby_name_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Gender",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Boy", "Girl", "Other").forEach { gender ->
                        FilterChip(
                            selected = selectedGender == gender,
                            onClick = { selectedGender = gender },
                            label = { Text(gender) },
                            modifier = Modifier.testTag("gender_chip_$gender")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = feedIntervalHoursText,
                    onValueChange = { feedIntervalHoursText = it },
                    label = { Text("Target Feeding Interval (Hours, e.g. 3.0)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("setup_feed_interval_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = napIntervalHoursText,
                    onValueChange = { napIntervalHoursText = it },
                    label = { Text("Target Wake Window / Nap Interval (Hours, e.g. 2.5)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("setup_nap_interval_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = caregiverNameText,
                    onValueChange = { caregiverNameText = it },
                    label = { Text("Primary Parent / Logger Name (e.g. Mom, Dad)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("setup_caregiver_name_input"),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalName = nameText.ifBlank { "Your Baby" }
                    val feedHours = feedIntervalHoursText.toDoubleOrNull() ?: 3.0
                    val napHours = napIntervalHoursText.toDoubleOrNull() ?: 2.5
                    val feedMin = (feedHours * 60).toInt().coerceIn(30, 480)
                    val napMin = (napHours * 60).toInt().coerceIn(30, 480)

                    val newProfile = (initialProfile ?: BabyProfile()).copy(
                        name = finalName,
                        gender = selectedGender,
                        targetFeedingIntervalMinutes = feedMin,
                        targetNapIntervalMinutes = napMin,
                        primaryCaregiverName = caregiverNameText.ifBlank { "Mom" },
                        isInitialSetupDone = true
                    )
                    onSaveProfile(newProfile)
                },
                modifier = Modifier.testTag("save_profile_setup_btn")
            ) {
                Text("Save Profile & Launch", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            if (initialProfile?.isInitialSetupDone == true) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
