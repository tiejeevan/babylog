package com.example.ui.dialogs

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.viewmodel.BabyCareViewModel

/**
 * Shared host for [SmartNapAdjusterSheet] so Dashboard and Timeline can open the same flow.
 */
@Composable
fun SmartNapAdjusterHost(
    viewModel: BabyCareViewModel,
    babyName: String
) {
    val showAdjuster by viewModel.showSmartNapAdjuster.collectAsStateWithLifecycle()
    val sleepGapPrompt by viewModel.sleepGapPrompt.collectAsStateWithLifecycle()
    val context = LocalContext.current

    if (showAdjuster && sleepGapPrompt != null) {
        val prompt = sleepGapPrompt!!
        SmartNapAdjusterSheet(
            prompt = prompt,
            babyName = babyName,
            onConfirm = { start, end ->
                viewModel.logIntelligentNap(
                    startTimeMillis = start,
                    endTimeMillis = end,
                    gapPrompt = prompt
                )
                viewModel.closeSmartNapAdjuster()
                viewModel.dismissSleepGapPrompt()
                Toast.makeText(context, "Logged intelligent nap 💤", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { viewModel.closeSmartNapAdjuster() }
        )
    }
}
