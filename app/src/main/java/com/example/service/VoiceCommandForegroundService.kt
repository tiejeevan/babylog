package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.engine.VoiceCommandExecutor
import com.example.engine.VoiceCommandMatcher
import com.example.engine.VoiceCommandPrefs
import com.example.notification.BabyNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Opt-in foreground service that continuously listens for care voice phrases
 * (code brown / yellow, feeding baby, nurse baby) while the phone may be locked.
 */
class VoiceCommandForegroundService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var isDestroyed = false
    private var isExecuting = false
    private var listenCycles = 0
    private var lastHeard: String = ""
    private var speechBeganThisSession = false
    /** Prefer cloud STT — on-device ambient often rejects slang like "code brown". */
    private var preferOnDevice: Boolean = false

    private val restartListeningRunnable = Runnable {
        if (!isDestroyed && VoiceCommandPrefs.isEnabled(this)) {
            startListeningCycle()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                VoiceCommandPrefs.setEnabled(this, false)
                tearDownAndStop()
                return START_NOT_STICKY
            }
            else -> {
                if (!hasMicPermission()) {
                    VoiceCommandPrefs.setEnabled(this, false)
                    stopSelf()
                    return START_NOT_STICKY
                }
                isDestroyed = false
                VoiceCommandPrefs.setEnabled(this, true)
                ensureChannel()
                try {
                    ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        buildListeningNotification(),
                        foregroundType()
                    )
                } catch (e: SecurityException) {
                    Log.w(TAG, "Cannot start microphone FGS yet; will retry when app is foreground", e)
                    stopSelf()
                    return START_NOT_STICKY
                }
                ensureRecognizer()
                startListeningCycle()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isDestroyed = true
        mainHandler.removeCallbacks(restartListeningRunnable)
        releaseRecognizer()
        serviceJob.cancel()
        super.onDestroy()
    }

    private fun tearDownAndStop() {
        isDestroyed = true
        mainHandler.removeCallbacks(restartListeningRunnable)
        releaseRecognizer()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun foregroundType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            0
        }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Hands-free Voice Logging",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps listening for care voice commands while enabled"
                setSound(null, null)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildListeningNotification(): Notification {
        val launch = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(BabyNotificationManager.EXTRA_OPEN_VOICE_COMMANDS, true)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPending = PendingIntent.getActivity(
            this,
            0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPending = PendingIntent.getService(
            this,
            1,
            Intent(this, VoiceCommandForegroundService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val body = if (lastHeard.isNotBlank()) {
            "Heard: $lastHeard"
        } else {
            "Say: Wet diaper, Dirty diaper, Feed the baby, Nurse baby"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Listening for care commands")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(R.drawable.ic_shortcut_diaper)
            .setContentIntent(contentPending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(0, "Stop listening", stopPending)
            .addAction(0, "Open app", contentPending)
            .build()
    }

    private fun refreshNotification() {
        if (isDestroyed) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, buildListeningNotification())
    }

    private fun ensureRecognizer() {
        if (speechRecognizer != null) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.w(TAG, "Speech recognition not available on this device")
            VoiceCommandPrefs.setEnabled(this, false)
            tearDownAndStop()
            return
        }
        speechRecognizer = createRecognizer().also { recognizer ->
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    speechBeganThisSession = false
                    Log.d(TAG, "ready for speech")
                }

                override fun onBeginningOfSpeech() {
                    speechBeganThisSession = true
                    Log.d(TAG, "beginning of speech")
                }

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    // Keep isListening true until results/error — end-of-speech is not idle yet
                    Log.d(TAG, "end of speech")
                }

                override fun onError(error: Int) {
                    val spoke = speechBeganThisSession
                    isListening = false
                    speechBeganThisSession = false
                    Log.d(TAG, "speech error=$error (${errorLabel(error)}) spoke=$spoke")
                    when (error) {
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                            Log.w(TAG, "Mic permission lost; stopping voice service")
                            VoiceCommandPrefs.setEnabled(this@VoiceCommandForegroundService, false)
                            tearDownAndStop()
                        }
                        SpeechRecognizer.ERROR_CLIENT,
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                            releaseRecognizer()
                            scheduleRestart(RESTART_DELAY_BUSY_MS)
                        }
                        SpeechRecognizer.ERROR_NETWORK,
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                            releaseRecognizer()
                            scheduleRestart(RESTART_DELAY_NETWORK_MS)
                        }
                        else -> {
                            if (spoke &&
                                (error == SpeechRecognizer.ERROR_NO_MATCH ||
                                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
                            ) {
                                lastHeard = "(didn't catch a command — try Wet diaper / Dirty diaper)"
                                VoiceCommandPrefs.setLastHeard(
                                    this@VoiceCommandForegroundService,
                                    lastHeard
                                )
                                refreshNotification()
                            }
                            scheduleRestart(RESTART_DELAY_QUICK_MS)
                        }
                    }
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    speechBeganThisSession = false
                    handleSpeechResults(results, isFinal = true)
                    scheduleRestart(RESTART_DELAY_QUICK_MS)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    handleSpeechResults(partialResults, isFinal = false)
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        Log.i(TAG, "recognizer created (onDevicePreferred=$preferOnDevice)")
    }

    private fun createRecognizer(): SpeechRecognizer {
        if (preferOnDevice &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
        ) {
            try {
                return SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            } catch (e: Exception) {
                Log.w(TAG, "on-device recognizer failed; using default", e)
            }
        }
        return SpeechRecognizer.createSpeechRecognizer(this)
    }

    private fun errorLabel(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "NETWORK_TIMEOUT"
        SpeechRecognizer.ERROR_NETWORK -> "NETWORK"
        SpeechRecognizer.ERROR_AUDIO -> "AUDIO"
        SpeechRecognizer.ERROR_SERVER -> "SERVER"
        SpeechRecognizer.ERROR_CLIENT -> "CLIENT"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "SPEECH_TIMEOUT"
        SpeechRecognizer.ERROR_NO_MATCH -> "NO_MATCH"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "BUSY"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "PERMISSIONS"
        else -> "OTHER($error)"
    }

    private fun handleSpeechResults(results: Bundle?, isFinal: Boolean) {
        if (isExecuting || isDestroyed) return
        val texts = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            .orEmpty()
            .filter { it.isNotBlank() }
        if (texts.isEmpty()) return

        val heard = texts.first()
        if (heard != lastHeard) {
            lastHeard = heard.take(80)
            Log.i(TAG, "heard(${if (isFinal) "final" else "partial"}): $texts")
            VoiceCommandPrefs.setLastHeard(this, lastHeard)
            refreshNotification()
        }

        val lastFired = VoiceCommandPrefs.lastFiredMap(this)
        val extras = VoiceCommandPrefs.allExtraPhrases(this)
        val now = System.currentTimeMillis()
        val commands = texts
            .flatMap { transcript ->
                VoiceCommandMatcher.matchAll(
                    transcript = transcript,
                    nowMillis = now,
                    lastFiredAt = lastFired,
                    extraPhrases = extras
                )
            }
            .distinct()
        if (commands.isEmpty()) {
            if (isFinal) {
                Log.i(TAG, "no command matched in: $texts")
            }
            return
        }

        Log.i(TAG, "matched commands=$commands from $texts")
        isExecuting = true
        isListening = false

        serviceScope.launch(Dispatchers.IO) {
            try {
                for (command in commands) {
                    VoiceCommandExecutor.execute(this@VoiceCommandForegroundService, command)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to execute voice commands $commands", e)
            } finally {
                mainHandler.post {
                    isExecuting = false
                    scheduleRestart(RESTART_AFTER_COMMAND_MS)
                }
            }
        }
    }

    private fun startListeningCycle() {
        if (isDestroyed || isExecuting) return
        if (isListening) {
            Log.d(TAG, "skip restart; already listening")
            return
        }
        if (!VoiceCommandPrefs.isEnabled(this) || !hasMicPermission()) {
            VoiceCommandPrefs.setEnabled(this, false)
            tearDownAndStop()
            return
        }

        ensureRecognizer()
        val recognizer = speechRecognizer ?: return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2_500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2_000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 300L)
            // Cloud STT handles unusual phrases better than ambient on-device
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
        }
        try {
            recognizer.startListening(intent)
            isListening = true
            listenCycles++
        } catch (e: Exception) {
            Log.w(TAG, "startListening failed", e)
            isListening = false
            releaseRecognizer()
            scheduleRestart(RESTART_DELAY_BUSY_MS)
        }
    }

    private fun scheduleRestart(delayMs: Long) {
        if (isDestroyed) return
        mainHandler.removeCallbacks(restartListeningRunnable)
        mainHandler.postDelayed(restartListeningRunnable, delayMs)
    }

    private fun releaseRecognizer() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }
        speechRecognizer = null
        isListening = false
    }

    companion object {
        private const val TAG = "VoiceCommandService"
        const val CHANNEL_ID = "channel_voice_listening"
        const val NOTIFICATION_ID = 4203
        const val ACTION_START = "com.example.ACTION_VOICE_COMMAND_START"
        const val ACTION_STOP = "com.example.ACTION_VOICE_COMMAND_STOP"

        /** Quick gap after silence timeout so speech isn't lost between cycles. */
        private const val RESTART_DELAY_QUICK_MS = 200L
        private const val RESTART_DELAY_BUSY_MS = 1_200L
        private const val RESTART_DELAY_NETWORK_MS = 1_500L
        /** Confirm overlay holds the screen ~2.8s — don't fight it for the mic. */
        private const val RESTART_AFTER_COMMAND_MS = 3_200L

        fun start(context: Context) {
            val intent = Intent(context, VoiceCommandForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, VoiceCommandForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
                try {
                    context.stopService(Intent(context, VoiceCommandForegroundService::class.java))
                } catch (_: Exception) {
                }
            }
        }
    }
}
