package com.example.navigation

import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.R

const val EXIT_BACK_WINDOW_MS = 2_000L

/**
 * Pure double-back timing: first press within a fresh window shows a hint;
 * second press within [windowMs] opens the exit dialog.
 */
fun resolveRootBackPress(
    nowMillis: Long,
    lastBackPressMillis: Long,
    windowMs: Long = EXIT_BACK_WINDOW_MS
): RootBackResult {
    return if (lastBackPressMillis > 0L && nowMillis - lastBackPressMillis <= windowMs) {
        RootBackResult.ShowExitDialog
    } else {
        RootBackResult.ShowHint
    }
}

enum class RootBackResult {
    ShowHint,
    ShowExitDialog
}

/**
 * Double-back exit flow for the navigation root (Dashboard).
 * Returns [handleRootBack] for the global [androidx.activity.compose.BackHandler],
 * and [showExitDialog] so the caller can dismiss the dialog on back.
 */
@Composable
fun rememberExitBackHandler(
    onQuitApp: () -> Unit,
    onMoveToBackground: () -> Unit
): ExitBackState {
    val context = LocalContext.current
    var lastBackPressMillis by remember { mutableLongStateOf(0L) }
    var showExitDialog by remember { mutableStateOf(false) }
    val hintText = stringResource(R.string.back_press_exit_hint)

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text(stringResource(R.string.exit_app_title)) },
            text = { Text(stringResource(R.string.exit_app_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        lastBackPressMillis = 0L
                        onQuitApp()
                    }
                ) {
                    Text(stringResource(R.string.exit_app_quit))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showExitDialog = false }) {
                        Text(stringResource(R.string.exit_app_cancel))
                    }
                    TextButton(
                        onClick = {
                            showExitDialog = false
                            lastBackPressMillis = 0L
                            onMoveToBackground()
                        }
                    ) {
                        Text(stringResource(R.string.exit_app_background))
                    }
                }
            }
        )
    }

    val handleRootBack: () -> Unit = {
        when (resolveRootBackPress(System.currentTimeMillis(), lastBackPressMillis)) {
            RootBackResult.ShowHint -> {
                lastBackPressMillis = System.currentTimeMillis()
                Toast.makeText(context, hintText, Toast.LENGTH_SHORT).show()
            }
            RootBackResult.ShowExitDialog -> {
                lastBackPressMillis = 0L
                showExitDialog = true
            }
        }
    }

    return ExitBackState(
        showExitDialog = showExitDialog,
        dismissExitDialog = { showExitDialog = false },
        handleRootBack = handleRootBack
    )
}

data class ExitBackState(
    val showExitDialog: Boolean,
    val dismissExitDialog: () -> Unit,
    val handleRootBack: () -> Unit
)
