package com.example.engine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    private val sampleRate = 44100
    private var audioTrack: AudioTrack? = null
    private var playJob: Job? = null
    private var timerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentSound = MutableStateFlow(SoundType.PINK_NOISE)
    val currentSound: StateFlow<SoundType> = _currentSound.asStateFlow()

    private val _volume = MutableStateFlow(0.7f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _remainingTimerMillis = MutableStateFlow(0L)
    val remainingTimerMillis: StateFlow<Long> = _remainingTimerMillis.asStateFlow()

    fun playSound(soundType: SoundType, volumeLevel: Float = _volume.value) {
        stopSound()
        _currentSound.value = soundType
        _volume.value = volumeLevel
        _isPlaying.value = true

        playJob = scope.launch {
            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
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
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack = track
            track.play()

            val buffer = ShortArray(minBufferSize)
            var sampleIndex = 0L

            // Pink noise filter state
            var b0 = 0.0; var b1 = 0.0; var b2 = 0.0; var b3 = 0.0; var b4 = 0.0; var b5 = 0.0; var b6 = 0.0

            // Lullaby notes frequencies (C major pentatonic lullaby melody)
            val notes = doubleArrayOf(261.63, 293.66, 329.63, 392.00, 440.00, 523.25)
            val lullabyDur = sampleRate / 2 // 0.5s per note

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
                                (pink / 5.0).coerceIn(-1.0, 1.0)
                            }
                            SoundType.HEARTBEAT -> {
                                val cycleIndex = sampleIndex % (sampleRate) // 1 second cycle = 60 BPM
                                val sec = cycleIndex.toDouble() / sampleRate
                                val thump = when {
                                    sec in 0.00..0.12 -> sin(2 * PI * 65.0 * sec) * sin(PI * sec / 0.12)
                                    sec in 0.20..0.30 -> sin(2 * PI * 55.0 * (sec - 0.20)) * 0.7 * sin(PI * (sec - 0.20) / 0.10)
                                    else -> 0.0
                                }
                                thump
                            }
                            SoundType.OCEAN_WAVES -> {
                                val waveMod = (sin(2 * PI * sampleIndex / (sampleRate * 6.0)) + 1.0) / 2.0 // 6 sec wave cycle
                                val noise = Random.nextDouble(-0.8, 0.8)
                                noise * (0.2 + 0.8 * waveMod)
                            }
                            SoundType.RAIN_SHUSH -> {
                                val noise = Random.nextDouble(-0.7, 0.7)
                                val drop = if (Random.nextInt(1000) < 3) Random.nextDouble(0.5, 1.0) else 0.0
                                (noise * 0.7 + drop * 0.3)
                            }
                            SoundType.LULLABY_MELODY -> {
                                val noteIndex = ((sampleIndex / lullabyDur) % notes.size).toInt()
                                val freq = notes[noteIndex]
                                val phase = (sampleIndex % lullabyDur).toDouble() / sampleRate
                                val env = sin(PI * (sampleIndex % lullabyDur) / lullabyDur)
                                (sin(2 * PI * freq * phase) * env * 0.5)
                            }
                        }

                        buffer[i] = (sample * currentVol * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
                        sampleIndex++
                    }
                    track.write(buffer, 0, buffer.size)
                }
            } catch (_: Exception) {
            } finally {
                try {
                    track.stop()
                    track.release()
                } catch (_: Exception) {}
            }
        }
    }

    fun setVolume(vol: Float) {
        _volume.value = vol.coerceIn(0.0f, 1.0f)
    }

    fun stopSound() {
        playJob?.cancel()
        playJob = null
        timerJob?.cancel()
        timerJob = null
        _isPlaying.value = false
        _remainingTimerMillis.value = 0L
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
    }

    fun startSleepTimer(minutes: Int) {
        timerJob?.cancel()
        if (minutes <= 0) {
            _remainingTimerMillis.value = 0L
            return
        }

        val totalMillis = minutes * 60 * 1000L
        _remainingTimerMillis.value = totalMillis

        timerJob = scope.launch {
            var left = totalMillis
            while (left > 0 && isActive) {
                delay(1000)
                left -= 1000
                _remainingTimerMillis.value = left

                // Gradual fade out in last 30 seconds
                if (left in 1..30000) {
                    val fadeRatio = left.toFloat() / 30000f
                    _volume.value = (_volume.value * fadeRatio).coerceIn(0.05f, 1.0f)
                }
            }
            if (isActive) {
                stopSound()
            }
        }
    }
}
