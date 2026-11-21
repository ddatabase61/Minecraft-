package com.example.engine.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Procedural real-time PCM Sound Synthesizer for BlockForge.
 * Synthesizes footsteps, water splashes, underwater bubbles, tool swings,
 * gunshots, zombie groans/hits, block mining/placing, animal/bird calls ("चूं-चैं"),
 * and ambient ocean/waterfall soundscapes with zero external audio files.
 */
class VoxelSoundEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var ambienceJob: Job? = null

    @Volatile var soundEnabled: Boolean = true
    @Volatile var isNearWater: Boolean = false
    @Volatile var isUnderwater: Boolean = false
    @Volatile var isNightTime: Boolean = false

    fun startAmbience() {
        if (ambienceJob?.isActive == true) return
        ambienceJob = scope.launch {
            val rng = Random(System.currentTimeMillis())
            while (isActive) {
                delay(rng.nextLong(2000L, 4200L))
                if (!soundEnabled) continue
                if (isUnderwater) {
                    playUnderwaterBubble()
                } else {
                    if (!isNightTime) {
                        playBirdChirp(highPitch = rng.nextBoolean())
                    } else {
                        playNightCricket()
                    }
                    if (isNearWater && soundEnabled) {
                        delay(380L)
                        playWaterRushPulse()
                    }
                }
            }
        }
    }

    fun stopAmbience() {
        ambienceJob?.cancel()
        ambienceJob = null
    }

    fun release() {
        stopAmbience()
        scope.cancel()
    }

    fun playFootstep(inWater: Boolean) {
        if (!soundEnabled) return
        scope.launch {
            val samples = ShortArray((SAMPLE_RATE * 0.065f).toInt())
            val rng = Random(System.nanoTime())
            var lp = 0f
            for (i in samples.indices) {
                val t = i.toFloat() / SAMPLE_RATE
                val env = exp(-t * 48f)
                val white = rng.nextFloat() * 2f - 1f
                lp = if (inWater) lp * 0.65f + white * 0.35f else lp * 0.82f + white * 0.18f
                val thump = sin(2.0 * PI * (if (inWater) 280.0 else 110.0) * t).toFloat() * 0.3f
                val s = ((lp * 0.55f + thump) * env * 0.42f).coerceIn(-1f, 1f)
                samples[i] = (s * Short.MAX_VALUE).toInt().toShort()
            }
            playPcm(samples)
        }
    }

    fun playWaterSplash() {
        if (!soundEnabled) return
        scope.launch {
            val samples = ShortArray((SAMPLE_RATE * 0.26f).toInt())
            val rng = Random(System.nanoTime())
            var lp = 0f
            for (i in samples.indices) {
                val t = i.toFloat() / SAMPLE_RATE
                val env = exp(-t * 11f)
                val white = rng.nextFloat() * 2f - 1f
                lp = lp * 0.70f + white * 0.30f
                val bubble = sin(2.0 * PI * (340.0 + t * 900.0) * t).toFloat() * 0.18f
                val s = ((lp * 0.72f + bubble) * env * 0.60f).coerceIn(-1f, 1f)
                samples[i] = (s * Short.MAX_VALUE).toInt().toShort()
            }
            playPcm(samples)
        }
    }

    fun playUnderwaterBubble() {
        if (!soundEnabled) return
        scope.launch {
            val samples = ShortArray((SAMPLE_RATE * 0.14f).toInt())
            var phase = 0.0
            for (i in samples.indices) {
                val p = i.toFloat() / samples.size
                val env = sin(PI * p).toFloat().coerceAtLeast(0f)
                val freq = 260f + p * p * 780f
                phase += 2.0 * PI * freq / SAMPLE_RATE
                val s = (sin(phase).toFloat() * env * 0.32f).coerceIn(-1f, 1f)
                samples[i] = (s * Short.MAX_VALUE).toInt().toShort()
            }
            playPcm(samples)
        }
    }

    fun playSwing() {
        if (!soundEnabled) return
        scope.launch {
            val samples = ShortArray((SAMPLE_RATE * 0.07f).toInt())
            val rng = Random(System.nanoTime())
            var lp = 0f
            for (i in samples.indices) {
                val t = i.toFloat() / SAMPLE_RATE
                val env = sin(PI * (t / 0.07f)).toFloat().coerceAtLeast(0f)
                lp = lp * 0.75f + (rng.nextFloat() * 2f - 1f) * 0.25f
                samples[i] = ((lp * env * 0.35f).coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
            }
            playPcm(samples)
        }
    }

    fun playHeartChime() {
        if (!soundEnabled) return
        playToneBurst(587f, 880f, 0.20f, 0.36f)
    }

    fun playGunshot() {
        if (!soundEnabled) return
        scope.launch {
            val samples = ShortArray((SAMPLE_RATE * 0.16f).toInt())
            val rng = Random(System.nanoTime())
            for (i in samples.indices) {
                val t = i.toFloat() / SAMPLE_RATE
                val noiseEnv = exp(-t * 28f)
                val punchEnv = exp(-t * 42f)
                val noise = (rng.nextFloat() * 2f - 1f) * noiseEnv * 0.75f
                val sub = sin(2.0 * PI * (155.0 - t * 650.0).coerceAtLeast(45.0) * t).toFloat() * punchEnv * 0.65f
                val crack = sin(2.0 * PI * 980.0 * t).toFloat() * exp(-t * 95f) * 0.35f
                val sample = ((noise + sub + crack) * 0.85f).coerceIn(-1f, 1f)
                samples[i] = (sample * Short.MAX_VALUE).toInt().toShort()
            }
            playPcm(samples)
        }
    }

    fun playZombieHit(defeated: Boolean) {
        if (!soundEnabled) return
        scope.launch {
            val dur = if (defeated) 0.28f else 0.14f
            val samples = ShortArray((SAMPLE_RATE * dur).toInt())
            val rng = Random(System.nanoTime())
            for (i in samples.indices) {
                val t = i.toFloat() / SAMPLE_RATE
                val env = exp(-t * if (defeated) 9f else 18f)
                val freq = if (defeated) (220f - t * 420f).coerceAtLeast(65f) else 190f
                val growl = sin(2.0 * PI * freq * t).toFloat() * 0.6f +
                    sin(2.0 * PI * (freq * 1.49f) * t).toFloat() * 0.25f
                val grit = (rng.nextFloat() * 2f - 1f) * 0.2f
                samples[i] = (((growl + grit) * env * 0.7f).coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
            }
            playPcm(samples)
        }
    }

    fun playZombieGroan() {
        if (!soundEnabled) return
        scope.launch {
            val samples = ShortArray((SAMPLE_RATE * 0.34f).toInt())
            for (i in samples.indices) {
                val t = i.toFloat() / SAMPLE_RATE
                val env = sin(PI * (t / 0.34f)).toFloat().coerceAtLeast(0f)
                val freq = 108f - t * 45f + sin(2.0 * PI * 6.0 * t).toFloat() * 6f
                val wave = sin(2.0 * PI * freq * t).toFloat() * 0.55f +
                    sin(2.0 * PI * (freq * 2.02f) * t).toFloat() * 0.22f
                samples[i] = ((wave * env * 0.5f).coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
            }
            playPcm(samples)
        }
    }

    fun playBlockBreak() {
        if (!soundEnabled) return
        scope.launch {
            val samples = ShortArray((SAMPLE_RATE * 0.12f).toInt())
            val rng = Random(System.nanoTime())
            for (i in samples.indices) {
                val t = i.toFloat() / SAMPLE_RATE
                val env = exp(-t * 32f)
                val crunch = (rng.nextFloat() * 2f - 1f) * 0.62f
                val thud = sin(2.0 * PI * (185f - t * 400f).coerceAtLeast(70f) * t).toFloat() * 0.45f
                samples[i] = (((crunch + thud) * env * 0.68f).coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
            }
            playPcm(samples)
        }
    }

    fun playBlockPlace() {
        if (!soundEnabled) return
        scope.launch {
            val samples = ShortArray((SAMPLE_RATE * 0.085f).toInt())
            for (i in samples.indices) {
                val t = i.toFloat() / SAMPLE_RATE
                val env = exp(-t * 46f)
                val pop = sin(2.0 * PI * (260f + t * 920f) * t).toFloat()
                samples[i] = ((pop * env * 0.56f).coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
            }
            playPcm(samples)
        }
    }

    fun playJump() {
        if (!soundEnabled) return
        scope.launch {
            val samples = ShortArray((SAMPLE_RATE * 0.09f).toInt())
            for (i in samples.indices) {
                val t = i.toFloat() / SAMPLE_RATE
                val env = exp(-t * 32f)
                val sweep = sin(2.0 * PI * (180f + t * 1400f) * t).toFloat()
                samples[i] = ((sweep * env * 0.34f).coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
            }
            playPcm(samples)
        }
    }

    fun playBirdChirp(highPitch: Boolean = true) {
        if (!soundEnabled) return
        scope.launch {
            val samples = ShortArray((SAMPLE_RATE * 0.24f).toInt())
            val baseFreq = if (highPitch) 2400f else 1750f
            for (i in samples.indices) {
                val t = i.toFloat() / SAMPLE_RATE
                val syllable1 = if (t < 0.095f) sin(PI * t / 0.095f).toFloat() else 0f
                val syllable2 = if (t in 0.12f..0.23f) sin(PI * (t - 0.12f) / 0.11f).toFloat() else 0f
                val env = (syllable1 + syllable2).coerceIn(0f, 1f)
                val fm = baseFreq + sin(2.0 * PI * 28.0 * t).toFloat() * 340f +
                    (if (t > 0.12f) 280f else -120f * t * 10f)
                val tone = sin(2.0 * PI * fm * t).toFloat()
                samples[i] = ((tone * env * 0.24f).coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
            }
            playPcm(samples)
        }
    }

    fun playWaterRushPulse() {
        if (!soundEnabled) return
        scope.launch {
            val samples = ShortArray((SAMPLE_RATE * 0.45f).toInt())
            val rng = Random(System.nanoTime())
            var lowPass = 0f
            for (i in samples.indices) {
                val t = i.toFloat() / SAMPLE_RATE
                val env = sin(PI * t / 0.45f).toFloat().coerceAtLeast(0f)
                val white = rng.nextFloat() * 2f - 1f
                lowPass = lowPass * 0.86f + white * 0.14f
                samples[i] = ((lowPass * env * 0.28f).coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
            }
            playPcm(samples)
        }
    }

    fun playAnimalCall(style: String) {
        if (!soundEnabled) return
        when (style) {
            "BIRD" -> playBirdChirp(true)
            "DOG" -> playToneBurst(320f, 520f, 0.11f, 0.42f)
            "CAT" -> playToneBurst(740f, 560f, 0.22f, 0.28f)
            "ELEPHANT" -> playToneBurst(410f, 760f, 0.30f, 0.45f)
            "CATTLE" -> playToneBurst(145f, 122f, 0.34f, 0.38f)
            "HERO" -> playToneBurst(523f, 784f, 0.22f, 0.32f)
            "ZOMBIE" -> playZombieGroan()
            else -> playBlockPlace()
        }
    }

    private fun playNightCricket() {
        val samples = ShortArray((SAMPLE_RATE * 0.18f).toInt())
        for (i in samples.indices) {
            val t = i.toFloat() / SAMPLE_RATE
            val pulse = if (((t * 45f).toInt() % 2) == 0) 1f else 0.1f
            val env = sin(PI * t / 0.18f).toFloat().coerceAtLeast(0f) * pulse
            val tone = sin(2.0 * PI * 3600.0 * t).toFloat()
            samples[i] = ((tone * env * 0.10f).coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
        }
        playPcm(samples)
    }

    private fun playToneBurst(startFreq: Float, endFreq: Float, durationSec: Float, gain: Float) {
        scope.launch {
            val samples = ShortArray((SAMPLE_RATE * durationSec).toInt())
            var phase = 0.0
            for (i in samples.indices) {
                val p = i.toFloat() / samples.size
                val env = sin(PI * p).toFloat().coerceAtLeast(0f)
                val freq = startFreq + (endFreq - startFreq) * p
                phase += 2.0 * PI * freq / SAMPLE_RATE
                val wave = (sin(phase) * 0.7 + sin(phase * 2.0) * 0.25).toFloat()
                samples[i] = ((wave * env * gain).coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
            }
            playPcm(samples)
        }
    }

    private fun playPcm(samples: ShortArray) {
        try {
            val minBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(samples.size * 2)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(samples, 0, samples.size)
            track.play()
            val waitMs = ((samples.size.toLong() * 1000L) / SAMPLE_RATE) + 35L
            Thread.sleep(waitMs)
            track.stop()
            track.release()
        } catch (_: Throwable) {
            // Headless JVM / unit test safe fallback
        }
    }

    companion object {
        private const val SAMPLE_RATE = 22050
    }
}
