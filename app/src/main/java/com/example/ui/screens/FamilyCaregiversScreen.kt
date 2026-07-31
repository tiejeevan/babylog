@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.backup.FullBackupManager
import com.example.data.model.BabyProfile
import com.example.data.model.CaregiverProfile
import com.example.engine.BluetoothCareEngine
import com.example.engine.CareSyncPrefs
import com.example.ui.dialogs.AddCaregiverDialog
import com.example.ui.dialogs.EnterPinDialog
import com.example.ui.viewmodel.BabyCareViewModel
import com.example.ui.viewmodel.BackupUiState
import com.example.ui.theme.parseHexColor
import android.widget.Toast
import java.util.Calendar
import java.util.Locale
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Edit
import com.example.ui.dialogs.OnboardingSetupDialog

@Composable
fun FamilyCaregiversScreen(
    viewModel: BabyCareViewModel,
    onNavigateToBluetooth: () -> Unit = {}
) {
    val caregivers by viewModel.caregivers.collectAsStateWithLifecycle()
    val activeCaregiver by viewModel.activeCaregiver.collectAsStateWithLifecycle()
    val profile by viewModel.babyProfile.collectAsStateWithLifecycle()
    val muteOffDuty by viewModel.muteNonUrgentWhenOffDuty.collectAsStateWithLifecycle()
    val vibrateOnReceive by viewModel.vibrateOnReceive.collectAsStateWithLifecycle()

    val context = LocalContext.current

    var showAddCaregiverDialog by remember { mutableStateOf(false) }
    var showSetupProfileDialog by remember { mutableStateOf(false) }
    var selectedCaregiverToSwitch by remember { mutableStateOf<CaregiverProfile?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("family_caregivers_screen")
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "FAMILY & CAREGIVER COORDINATION",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = "Co-Parents, Grandparents & Nannies",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }

        // Active Caregiver Selection
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "WHO IS CARING FOR BABY RIGHT NOW?",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Select caregiver identity with 4-digit PIN verification",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    caregivers.forEach { caregiver ->
                        val isSelected = caregiver.id == (activeCaregiver?.id ?: 1L)
                        Surface(
                            onClick = {
                                if (!isSelected) {
                                    selectedCaregiverToSwitch = caregiver
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .testTag("caregiver_option_${caregiver.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        if (!isSelected) {
                                            selectedCaregiverToSwitch = caregiver
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(parseHexColor(caregiver.avatarColorHex))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = caregiver.name,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "${caregiver.relationship} • Role: ${caregiver.role} • PIN Protection Active",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSelected) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "Active Now",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { showAddCaregiverDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_caregiver_btn")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Connect New Caregiver / Family Member", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }



        // Widget Diagnostic Card
        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showSetupProfileDialog) {
        OnboardingSetupDialog(
            initialProfile = profile,
            onDismiss = { showSetupProfileDialog = false },
            onCompleteSetup = { updatedProfile, initWeight, initHeight ->
                viewModel.completeOnboardingSetup(updatedProfile, initWeight, initHeight)
                showSetupProfileDialog = false
                Toast.makeText(context, "Baby profile setup saved! 🎉", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showAddCaregiverDialog) {
        AddCaregiverDialog(
            onDismiss = { showAddCaregiverDialog = false },
            onConfirm = { name, rel, role, pin ->
                viewModel.addCaregiver(name, rel, role, pin)
                showAddCaregiverDialog = false
                Toast.makeText(context, "Caregiver $name added with PIN!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    selectedCaregiverToSwitch?.let { caregiver ->
        EnterPinDialog(
            caregiverName = caregiver.name,
            onDismiss = { selectedCaregiverToSwitch = null },
            onConfirm = { inputPin ->
                viewModel.verifyAndSwitchCaregiver(caregiver.id, inputPin) { success ->
                    if (success) {
                        Toast.makeText(context, "Switched caregiver to ${caregiver.name}", Toast.LENGTH_SHORT).show()
                        selectedCaregiverToSwitch = null
                    } else {
                        Toast.makeText(context, "Incorrect PIN for ${caregiver.name}. Default PINs: Mom=1234, Dad=5678, Grandma=0000", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }
}

/** Clock time today, or tomorrow if that clock time has already passed. */
internal fun medicinePilotMillisForTodayOrTomorrow(hour: Int, minute: Int): Long {
    val now = System.currentTimeMillis()
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    if (cal.timeInMillis <= now) {
        cal.add(Calendar.DAY_OF_YEAR, 1)
    }
    return cal.timeInMillis
}
