package com.example.notification

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BabyCareTheme

/**
 * Brief lock-screen flash confirming a voice command was logged.
 * Turns the screen on so the caregiver gets immediate feedback.
 */
class VoiceCommandConfirmActivity : ComponentActivity() {

    private val dismissHandler = Handler(Looper.getMainLooper())
    private val autoDismiss = Runnable { finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        turnScreenOn()

        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Logged" }
        val message = intent.getStringExtra(EXTRA_MESSAGE).orEmpty()

        setContent {
            BabyCareTheme {
                VoiceCommandConfirmScreen(
                    title = title,
                    message = message,
                    onDismiss = { finish() }
                )
            }
        }

        dismissHandler.postDelayed(autoDismiss, AUTO_DISMISS_MS)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        dismissHandler.removeCallbacks(autoDismiss)
        dismissHandler.postDelayed(autoDismiss, AUTO_DISMISS_MS)
        recreate()
    }

    override fun onDestroy() {
        dismissHandler.removeCallbacks(autoDismiss)
        super.onDestroy()
    }

    private fun turnScreenOn() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            // Keep keyguard — just wake and show confirmation over lock screen.
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    companion object {
        const val EXTRA_TITLE = "voice_confirm_title"
        const val EXTRA_MESSAGE = "voice_confirm_message"
        private const val AUTO_DISMISS_MS = 2_800L

        fun intent(context: Context, title: String, message: String): Intent =
            Intent(context, VoiceCommandConfirmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_MESSAGE, message)
            }
    }
}

@Composable
private fun VoiceCommandConfirmScreen(
    title: String,
    message: String,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        if (message.isNotBlank()) {
            Text(
                text = message,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.padding(top = 28.dp)
        ) {
            Text("OK")
        }
    }
}
