package com.example.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import com.example.service.SleepSoundForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

enum class SoundType(val title: String, val icon: String, val description: String) {
    PINK_NOISE("Shush & Pink Noise", "💨", "Soft continuous fan frequency for infant soothing"),
    HEARTBEAT("Womb Heartbeat", "💓", "60 BPM gentle pulse mimicking mother's womb"),
    OCEAN_WAVES("Ocean Waves", "🌊", "Rhythmic ebb and flow ambient sound"),
    RAIN_SHUSH("Gentle Rain", "🌧️", "Calming rain drops and steady white noise"),
    LULLABY_MELODY("Warm Lullaby", "🎵", "Soft harmonic sine melody loop")
}

object BabySoundSynthesizer {

    private const val SAMPLE_RATE = 44100
    /** Soft peak so 100% volume stays comfortable at crib distance. */
    private const val PEAK_SCALE = 0.72

    private var appContext: Context? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var audioTrack: AudioTrack? = null
    private var playJob: Job? = null
    private var timerJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var baseVolume = 0.7f
    private var ducked = false

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _currentSound = MutableStateFlow(SoundType.PINK_NOISE)
    val currentSound: StateFlow<SoundType> = _currentSound.asStateFlow()

    private val _volume = MutableStateFlow(0.7f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _remainingTimerMillis = MutableStateFlow(0L)
    val remainingTimerMillis: StateFlow<Long> = _remainingTimerMillis.asStateFlow()

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                pauseSound()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                pauseSound()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                ducked = true
                applyEffectiveVolume()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                ducked = false
                applyEffectiveVolume()
                if (_isPaused.value) {
                    resumeSound()
                }
            }
        }
    }

    fun initialize(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        audioManager = appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    fun playSound(soundType: SoundType, volumeLevel: Float = baseVolume) {
        val ctx = appContext ?: return
        stopPlayback(cancelTimer = false, dismissService = false)

        baseVolume = volumeLevel.coerceIn(0f, 1f)
        _volume.value = baseVolume
        _currentSound.value = soundType
        _isPaused.value = false
        ducked = false

        if (!requestAudioFocus()) return

        _isPlaying.value = true
        SleepSoundForegroundService.start(ctx, soundType)
        startSynthesis(soundType)
    }

    fun pauseSound() {
        if (!_isPlaying.value && !_isPaused.value) return
        playJob?.cancel()
        playJob = null
        releaseTrack()
        _isPlaying.value = false
        _isPaused.value = true
        appContext?.let { SleepSoundForegroundService.update(it) }
    }

    fun resumeSound() {
        val ctx = appContext ?: return
        if (!_isPaused.value && _isPlaying.value) return
        if (!requestAudioFocus()) return
        _isPaused.value = false
        _isPlaying.value = true
        ducked = false
        applyEffectiveVolume()
        SleepSoundForegroundService.start(ctx, _currentSound.value)
        startSynthesis(_currentSound.value)
    }

    fun setVolume(vol: Float) {
        baseVolume = vol.coerceIn(0f, 1f)
        applyEffectiveVolume()
        appContext?.let { SleepSoundPrefs.setVolume(it, baseVolume) }
    }

    /** Stops playback and cancels the auto-off timer. */
    fun stopSound() {
        stopPlayback(cancelTimer = true, dismissService = true)
    }

    /**
     * @param cancelTimer if false, keeps an active sleep timer when switching sounds
     * @param dismissService if true, tears down the media notification
     */
    fun stopPlayback(cancelTimer: Boolean = true, dismissService: Boolean = true) {
        playJob?.cancel()
        playJob = null
        releaseTrack()
        abandonAudioFocus()
        ducked = false
        _isPlaying.value = false
        _isPaused.value = false

        if (cancelTimer) {
            timerJob?.cancel()
            timerJob = null
            _remainingTimerMillis.value = 0L
            _volume.value = baseVolume
        }

        if (dismissService) {
            appContext?.let { SleepSoundForegroundService.dismiss(it) }
        }
    }

    fun startSleepTimer(minutes: Int) {
        timerJob?.cancel()
        timerJob = null

        if (minutes <= 0) {
            _remainingTimerMillis.value = 0L
            _volume.value = baseVolume
            appContext?.let { SleepSoundPrefs.setTimerMinutes(it, 0) }
            return
        }

        appContext?.let { SleepSoundPrefs.setTimerMinutes(it, minutes) }

        val totalMillis = minutes * 60 * 1000L
        _remainingTimerMillis.value = totalMillis

        timerJob = scope.launch {
            var left = totalMillis
            var fadeFrom = baseVolume
            while (left > 0 && isActive) {
                delay(1000)
                left -= 1000
                _remainingTimerMillis.value = left.coerceAtLeast(0L)

                if (left > 30_000) {
                    fadeFrom = baseVolume
                    if (!ducked) {
                        _volume.value = baseVolume
                    }
                } else if (left in 1..30_000) {
                    // Linear fade over last 30s from volume at fade start → near silence
                    val fadeRatio = left.toFloat() / 30_000f
                    _volume.value = (fadeFrom * fadeRatio).coerceIn(0.02f, 1f)
                }
            }
            if (isActive) {
                stopSound()
            }
        }
    }

    private fun applyEffectiveVolume() {
        val effective = when {
            ducked -> baseVolume * 0.25f
            else -> baseVolume
        }
        // During fade-out, don't override the faded value unless ducking
        val inFade = _remainingTimerMillis.value in 1..30_000
        if (inFade && !ducked) return
        if (inFade && ducked) {
            _volume.value = (baseVolume * (_remainingTimerMillis.value / 30_000f) * 0.25f)
                .coerceIn(0.02f, 1f)
        } else {
            _volume.value = effective.coerceIn(0f, 1f)
        }
    }

    private fun requestAudioFocus(): Boolean {
        val am = audioManager ?: return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(focusChangeListener)
                .setAcceptsDelayedFocusGain(false)
                .build()
            focusRequest = request
            am.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { am.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(focusChangeListener)
        }
    }

    private fun releaseTrack() {
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {
        }
        audioTrack = null
    }

    private fun startSynthesis(soundType: SoundType) {
        playJob = scope.launch {
            val minBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBufferSize * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack = track
            track.play()

            val buffer = ShortArray(minBufferSize)
            var sampleIndex = 0L

            // Pink noise filter state (Paul Kellet)
            var b0 = 0.0; var b1 = 0.0; var b2 = 0.0; var b3 = 0.0
            var b4 = 0.0; var b5 = 0.0; var b6 = 0.0

            val notes = doubleArrayOf(261.63, 293.66, 329.63, 392.00, 440.00, 523.25)
            val lullabyDur = SAMPLE_RATE / 2

            try {
                while (isActive) {
                    val currentVol = _volume.value
                    for (i in buffer.indices) {
                        val sample: Double = when (soundType) {
                            SoundType.PINK_NOISE -> {
                                val white = Random.nextDouble(-1.0, 1.0)
                                b0 = 0.99886 * b0 + white * 0.0555179
                                b1 = 0.99332 * b1 + white * 0.0750759
                                b2 = 0.96900 * b2 + white * 0.1538520
                                b3 = 0.86650 * b3 + white * 0.3104856
                                b4 = 0.55000 * b4 + white * 0.5329522
                                b5 = -0.7616 * b5 - white * 0.0168980
                                val pink = b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362
                                b6 = white * 0.115926
                                (pink / 5.5).coerceIn(-1.0, 1.0)
                            }
                            SoundType.HEARTBEAT -> {
                                val cycleIndex = sampleIndex % SAMPLE_RATE
                                val sec = cycleIndex.toDouble() / SAMPLE_RATE
                                when {
                                    sec in 0.00..0.12 ->
                                        sin(2 * PI * 65.0 * sec) * sin(PI * sec / 0.12)
                                    sec in 0.20..0.30 ->
                                        sin(2 * PI * 55.0 * (sec - 0.20)) * 0.7 *
                                            sin(PI * (sec - 0.20) / 0.10)
                                    else -> 0.0
                                }
                            }
                            SoundType.OCEAN_WAVES -> {
                                // Smoother dual-LFO swell (~8s primary, soft secondary)
                                val primary = (sin(2 * PI * sampleIndex / (SAMPLE_RATE * 8.0)) + 1.0) / 2.0
                                val secondary = (sin(2 * PI * sampleIndex / (SAMPLE_RATE * 3.2)) + 1.0) / 2.0
                                val waveMod = 0.75 * primary + 0.25 * secondary
                                val noise = Random.nextDouble(-0.65, 0.65)
                                noise * (0.15 + 0.7 * waveMod)
                            }
                            SoundType.RAIN_SHUSH -> {
                                val noise = Random.nextDouble(-0.55, 0.55)
                                // Sparser, softer drops
                                val drop = if (Random.nextInt(2000) < 2) {
                                    Random.nextDouble(0.25, 0.55)
                                } else {
                                    0.0
                                }
                                noise * 0.75 + drop * 0.25
                            }
                            SoundType.LULLABY_MELODY -> {
                                val noteIndex = ((sampleIndex / lullabyDur) % notes.size).toInt()
                                val freq = notes[noteIndex]
                                val phase = (sampleIndex % lullabyDur).toDouble() / SAMPLE_RATE
                                val env = sin(PI * (sampleIndex % lullabyDur) / lullabyDur)
                                sin(2 * PI * freq * phase) * env * 0.4
                            }
                        }

                        buffer[i] = (sample * currentVol * PEAK_SCALE * 32767.0)
                            .toInt()
                            .coerceIn(-32768, 32767)
                            .toShort()
                        sampleIndex++
                    }
                    track.write(buffer, 0, buffer.size)
                }
            } catch (_: Exception) {
            } finally {
                try {
                    track.stop()
                    track.release()
                } catch (_: Exception) {
                }
                if (audioTrack === track) {
                    audioTrack = null
                }
            }
        }
    }
}
