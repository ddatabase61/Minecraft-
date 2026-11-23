package com.example.engine.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.data.model.AircraftSoundStyle
import com.example.data.model.BikeSoundStyle
import com.example.data.model.BlockType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.Semaphore
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
    private val audioSlots = Semaphore(6)
    private var ambienceJob: Job? = null

    @Volatile var soundEnabled: Boolean = true
    @Volatile var isNearWater: Boolean = false
    @Volatile var isNearLava: Boolean = false
    @Volatile var isUnderwater: Boolean = false
    @Volatile var isNightTime: Boolean = false
    @Volatile var isSittingOnWater: Boolean = false
    @Volatile var isRidingBike: Boolean = false
    @Volatile var isFlyingPlane: Boolean = false

    fun startAmbience() {
        if (ambienceJob?.isActive == true) return
        ambienceJob = scope.launch {
            val rng = Random(System.currentTimeMillis())
            while (isActive) {
                delay(rng.nextLong(1700L, 3400L))
                if (!soundEnabled) continue
                if (isUnderwater) {
                    playUnderwaterBubble()
                } else if (isSittingOnWater) {
                    playWaterSitRipple()
                    if (!isNightTime && rng.nextBoolean()) {
                        delay(380L)
                        playBirdChirp(highPitch = true)
                    }
                } else {
                    if (!isNightTime && !isRidingBike && !isFlyingPlane) {
                        playBirdChirp(highPitch = rng.nextBoolean())
                    } else if (isNightTime && !isRidingBike && !isFlyingPlane) {
                        playNightCricket()
                    }
                    if (isNearWater && soundEnabled) {
                        delay(320L)
                        playWaterRushPulse()
                    }
                    if (isNearLava && soundEnabled) {
                        delay(340L)
                        playLavaBubble()
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
            val samples = ShortArray((SAMPLE_RATE * 0.18f).toInt())
            val rng = Random(System.nanoTime())
            for (i in samples.indices) {
                val t = i.toFloat() / SAMPLE_RATE
                val noiseEnv = exp(-t * 25f)
                val punchEnv = exp(-t * 38f)
                val noise = (rng.nextFloat() * 2f - 1f) * noiseEnv * 0.78f
                val sub = sin(2.0 * PI * (168.0 - t * 700.0).coerceAtLeast(42.0) * t).toFloat() * punchEnv * 0.72f
                val crack = sin(2.0 * PI * 1120.0 * t).toFloat() * exp(-t * 90f) * 0.40f
                val shellPing = if (t > 0.09f) sin(2.0 * PI * 3200.0 * t).toFloat() * exp(-(t - 0.09f) * 45f) * 0.16f else 0f
                val sample = ((noise + sub + crack + shellPing) * 0.88f).coerceIn(-1f, 1f)
                samples[i] = (sample * Short.MAX_VALUE).toInt().toShort()
            }
            playPcm(samples)
        }
    }

    fun playPlayerHurt() {
        if (!soundEnabled) return
        scope.launch {
            val samples = ShortArray((SAMPLE_RATE * 0.22f).toInt())
            val rng = Random(System.nanoTime())
            for (i in samples.indices) {
                val t = i.toFloat() / SAMPLE_RATE
                val env = exp(-t * 14f)
                val impact = sin(2.0 * PI * (135.0 - t * 340.0).coerceAtLeast(52.0) * t).toFloat() * 0.72f
                val crunch = (rng.nextFloat() * 2f - 1f) * exp(-t * 36f) * 0.45f
                val s = ((impact + crunch) * env * 0.85f).coerceIn(-1f, 1f)
                samples[i] = (s * Short.MAX_VALUE).toInt().toShort()
            }
            playPcm(samples)
        }
    }

    fun playAdminLiveChime() {
        if (!soundEnabled) return
        scope.launch {
            val samples = ShortArray((SAMPLE_RATE * 0.38f).toInt())
            for (i in samples.indices) {
                val t = i.toFloat() / SAMPLE_RATE
                val env = exp(-t * 6.5f)
                val f1 = sin(2.0 * PI * 523.25 * t).toFloat() * 0.40f
                val f2 = sin(2.0 * PI * 659.25 * t).toFloat() * 0.35f
                val f3 = sin(2.0 * PI * 783.99 * t).toFloat() * 0.30f
                val f4 = if (t > 0.10f) sin(2.0 * PI * 1046.5 * t).toFloat() * 0.35f else 0f
                samples[i] = (((f1 + f2 + f3 + f4) * env * 0.55f).coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
            }
            playPcm(samples)
        }
    }

    fun playZombieHit(defeated: Boolean) {
        if (!soundEnabled) return
        scope.launch {
            val dur = if (defeated) 0.32f else 0.15f
            val samples = ShortArray((SAMPLE_RATE * dur).toInt())
            val rng = Random(System.nanoTime())
            for (i in samples.indices) {
                val t = i.toFloat() / SAMPLE_RATE
                val env = exp(-t * if (defeated) 8.5f else 18f)
                val freq = if (defeated) (240f - t * 460f).coerceAtLeast(55f) else 195f
                val growl = sin(2.0 * PI * freq * t).toFloat() * 0.62f +
                    sin(2.0 * PI * (freq * 1.49f) * t).toFloat() * 0.25f
                val boom = if (defeated) sin(2.0 * PI * 68.0 * t).toFloat() * exp(-t * 14f) * 0.50f else 0f
                val grit = (rng.nextFloat() * 2f - 1f) * 0.24f
                samples[i] = (((growl + boom + grit) * env * 0.78f).coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
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

    fun playLavaBubble() {
        if (!soundEnabled) return
        scope.launch {
            val samples = ShortArray((SAMPLE_RATE * 0.28f).toInt())
            val rng = Random(System.nanoTime())
            var lp = 0f
            var phase = 0.0
            for (i in samples.indices) {
                val p = i.toFloat() / samples.size
                val env = sin(PI * p).toFloat().coerceAtLeast(0f)
                val freq = 75f + p * p * 165f
                phase += 2.0 * PI * freq / SAMPLE_RATE
                val pop = sin(phase).toFloat() * 0.55f
                val white = rng.nextFloat() * 2f - 1f
                lp = lp * 0.90f + white * 0.10f
                val s = ((pop + lp * 0.45f) * env * 0.42f).coerceIn(-1f, 1f)
                samples[i] = (s * Short.MAX_VALUE).toInt().toShort()
            }
            playPcm(samples)
        }
    }

    fun playLavaSizzle() {
        if (!soundEnabled) return
        scope.launch {
            val samples = ShortArray((SAMPLE_RATE * 0.36f).toInt())
            val rng = Random(System.nanoTime())
            var hpPrev = 0f
            for (i in samples.indices) {
                val t = i.toFloat() / SAMPLE_RATE
                val env = exp(-t * 7.5f)
                val white = rng.nextFloat() * 2f - 1f
                val hiss = white - hpPrev * 0.65f
                hpPrev = white
                samples[i] = ((hiss * env * 0.52f).coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
            }
            playPcm(samples)
        }
    }

    fun playTempleBell() {
        if (!soundEnabled) return
        scope.launch {
            val samples = ShortArray((SAMPLE_RATE * 0.55f).toInt())
            for (i in samples.indices) {
                val t = i.toFloat() / SAMPLE_RATE
                val env = exp(-t * 5.2f)
                val f1 = sin(2.0 * PI * 660.0 * t).toFloat() * 0.55f
                val f2 = sin(2.0 * PI * 1320.0 * t).toFloat() * 0.25f
                val f3 = sin(2.0 * PI * 1995.0 * t).toFloat() * 0.14f
                samples[i] = (((f1 + f2 + f3) * env * 0.48f).coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
            }
            playPcm(samples)
        }
    }

    fun playCoinChime() {
        if (!soundEnabled) return
        scope.launch {
            val samples = ShortArray((SAMPLE_RATE * 0.26f).toInt())
            for (i in samples.indices) {
                val t = i.toFloat() / SAMPLE_RATE
                val freq = if (t < 0.10f) 987.7f else 1318.5f
                val env = exp(-t * 11f)
                val wave = sin(2.0 * PI * freq * t).toFloat() * 0.7f + sin(2.0 * PI * freq * 2.0 * t).toFloat() * 0.2f
                samples[i] = ((wave * env * 0.48f).coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
            }
            playPcm(samples)
        }
    }

    fun playWaterSitSplash() {
        if (!soundEnabled) return
        scope.launch {
            val samples = ShortArray((SAMPLE_RATE * 0.34f).toInt())
            val rng = Random(System.nanoTime())
            var lp = 0f
            var phase = 0.0
            for (i in samples.indices) {
                val t = i.toFloat() / SAMPLE_RATE
                val env = exp(-t * 7.5f)
                val white = rng.nextFloat() * 2f - 1f
                lp = lp * 0.74f + white * 0.26f
                val freq = 260f + sin(2.0 * PI * 9.0 * t).toFloat() * 85f + t * 420f
                phase += 2.0 * PI * freq / SAMPLE_RATE
                val plop = sin(phase).toFloat() * 0.36f
                val s = ((lp * 0.62f + plop) * env * 0.64f).coerceIn(-1f, 1f)
                samples[i] = (s * Short.MAX_VALUE).toInt().toShort()
            }
            playPcm(samples)
        }
    }

    fun playWaterSitRipple() {
        if (!soundEnabled) return
        scope.launch {
            val samples = ShortArray((SAMPLE_RATE * 0.30f).toInt())
            val rng = Random(System.nanoTime())
            var lp = 0f
            for (i in samples.indices) {
                val p = i.toFloat() / samples.size
                val env = sin(PI * p).toFloat().coerceAtLeast(0f)
                val white = rng.nextFloat() * 2f - 1f
                lp = lp * 0.88f + white * 0.12f
                val lap = sin(2.0 * PI * (240.0 + p * 180.0) * (i.toFloat() / SAMPLE_RATE)).toFloat() * 0.16f
                samples[i] = (((lp * 0.45f + lap) * env * 0.32f).coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
            }
            playPcm(samples)
        }
    }

    fun playBikeStart(style: BikeSoundStyle) {
        if (!soundEnabled) return
        scope.launch {
            val samples = ShortArray((SAMPLE_RATE * 0.44f).toInt())
            val rng = Random(System.nanoTime())
            var phase = 0.0
            for (i in samples.indices) {
                val t = i.toFloat() / SAMPLE_RATE
                val p = i.toFloat() / samples.size
                val env = if (p < 0.12f) p / 0.12f else exp(-(p - 0.12f) * 2.6f)
                val baseFreq = when (style) {
                    BikeSoundStyle.CRUISER_THUMP -> 48f + p * 62f
                    BikeSoundStyle.SUPERBIKE_SCREAM -> 130f + p * 260f
                    BikeSoundStyle.STREET_GROWL -> 88f + p * 155f
                    BikeSoundStyle.DIRT_BRAP -> 72f + p * 120f
                    BikeSoundStyle.ELECTRIC_HUM -> 220f + p * 520f
                }
                phase += 2.0 * PI * baseFreq / SAMPLE_RATE
                val wave = if (style == BikeSoundStyle.ELECTRIC_HUM) {
                    sin(phase).toFloat() * 0.6f + sin(phase * 2.0).toFloat() * 0.3f
                } else {
                    val saw = ((phase / PI) % 2.0 - 1.0).toFloat()
                    sin(phase).toFloat() * 0.55f + saw * 0.32f + (rng.nextFloat() * 2f - 1f) * 0.12f
                }
                samples[i] = ((wave * env * 0.58f).coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
            }
            playPcm(samples)
        }
    }

    fun playBikeEnginePulse(style: BikeSoundStyle, speedRatio: Float, nitro: Boolean) {
        if (!soundEnabled) return
        scope.launch {
            val clamped = speedRatio.coerceIn(0f, 1.35f)
            val samples = ShortArray((SAMPLE_RATE * 0.16f).toInt())
            var phase = 0.0
            val nitroBoost = if (nitro) 1.38f else 1f
            for (i in samples.indices) {
                val p = i.toFloat() / samples.size
                val env = sin(PI * p).toFloat().coerceAtLeast(0f)
                val freq = when (style) {
                    BikeSoundStyle.CRUISER_THUMP -> (42f + clamped * 58f) * nitroBoost
                    BikeSoundStyle.SUPERBIKE_SCREAM -> (115f + clamped * 270f) * nitroBoost
                    BikeSoundStyle.STREET_GROWL -> (76f + clamped * 165f) * nitroBoost
                    BikeSoundStyle.DIRT_BRAP -> (64f + clamped * 135f) * nitroBoost
                    BikeSoundStyle.ELECTRIC_HUM -> (210f + clamped * 540f) * nitroBoost
                }
                phase += 2.0 * PI * freq / SAMPLE_RATE
                val wave = when (style) {
                    BikeSoundStyle.CRUISER_THUMP -> {
                        val thump = sin(phase).toFloat() * 0.72f + sin(phase * 0.5).toFloat() * 0.35f
                        thump * (0.65f + 0.35f * sin(2.0 * PI * 14.0 * (i.toFloat() / SAMPLE_RATE)).toFloat())
                    }
                    BikeSoundStyle.ELECTRIC_HUM -> sin(phase).toFloat() * 0.55f + sin(phase * 1.5).toFloat() * 0.25f
                    else -> sin(phase).toFloat() * 0.52f + sin(phase * 2.0).toFloat() * 0.28f + sin(phase * 3.0).toFloat() * 0.14f
                }
                val gain = (0.20f + clamped * 0.22f + if (nitro) 0.12f else 0f).coerceAtMost(0.54f)
                samples[i] = ((wave * env * gain).coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
            }
            playPcm(samples)
        }
    }

    fun playBikeHorn(style: BikeSoundStyle) {
        if (!soundEnabled) return
        scope.launch {
            val samples = ShortArray((SAMPLE_RATE * 0.28f).toInt())
            val (f1, f2) = when (style) {
                BikeSoundStyle.CRUISER_THUMP -> 370.0 to 466.0
                BikeSoundStyle.ELECTRIC_HUM -> 620.0 to 830.0
                else -> 440.0 to 554.0
            }
            for (i in samples.indices) {
                val t = i.toFloat() / SAMPLE_RATE
                val p = i.toFloat() / samples.size
                val env = if (p < 0.08f) p / 0.08f else if (p > 0.85f) (1f - p) / 0.15f else 1f
                val s = (sin(2.0 * PI * f1 * t) * 0.45 + sin(2.0 * PI * f2 * t) * 0.45 + sin(2.0 * PI * f1 * 2.0 * t) * 0.15).toFloat()
                samples[i] = ((s * env * 0.52f).coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
            }
            playPcm(samples)
        }
    }

    fun playBikeNitro() {
        if (!soundEnabled) return
        scope.launch {
            val samples = ShortArray((SAMPLE_RATE * 0.36f).toInt())
            val rng = Random(System.nanoTime())
            var lp = 0f
            var phase = 0.0
            for (i in samples.indices) {
                val p = i.toFloat() / samples.size
                val env = sin(PI * p).toFloat().coerceAtLeast(0f)
                val white = rng.nextFloat() * 2f - 1f
                lp = lp * 0.62f + white * 0.38f
                phase += 2.0 * PI * (180f + p * 860f) / SAMPLE_RATE
                val whoosh = lp * 0.55f + sin(phase).toFloat() * 0.45f
                samples[i] = ((whoosh * env * 0.62f).coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
            }
            playPcm(samples)
        }
    }

    fun playHumanWorkHammer() {
        if (!soundEnabled) return
        scope.launch {
            val samples = ShortArray((SAMPLE_RATE * 0.14f).toInt())
            for (i in samples.indices) {
                val t = i.toFloat() / SAMPLE_RATE
                val env = exp(-t * 28f)
                val clang = sin(2.0 * PI * 740.0 * t).toFloat() * 0.45f +
                    sin(2.0 * PI * 1490.0 * t).toFloat() * 0.25f +
                    sin(2.0 * PI * 210.0 * t).toFloat() * 0.35f
                samples[i] = ((clang * env * 0.36f).coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
            }
            playPcm(samples)
        }
    }

    fun playHumanGreetingChime() {
        if (!soundEnabled) return
        playToneBurst(523f, 784f, 0.16f, 0.30f)
    }

    /**
     * Surface-specific footsteps & material sounds so EVERY block in the world has its own real sound!
     */
    fun playSurfaceFootstep(surface: BlockType, inWater: Boolean) {
        if (!soundEnabled) return
        if (inWater) {
            playFootstep(inWater = true)
            return
        }
        when (surface) {
            BlockType.GRASS, BlockType.LEAVES, BlockType.CHERRY_LEAVES, BlockType.TALL_GRASS,
            BlockType.FLOWER_RED, BlockType.FLOWER_GOLD, BlockType.FLOWER_BLUE,
            BlockType.FLOWER_PINK, BlockType.FLOWER_PURPLE, BlockType.FLOWER_SUNFLOWER -> playGrassRustle()

            BlockType.STONE, BlockType.COBBLESTONE, BlockType.BRICK,
            BlockType.SANDSTONE, BlockType.OBSIDIAN, BlockType.BEDROCK,
            BlockType.COAL_ORE, BlockType.IRON_ORE, BlockType.GOLD_ORE, BlockType.DIAMOND_ORE -> playStoneRoadStep()

            BlockType.WOOD_LOG, BlockType.PLANKS -> playWoodStep()
            else -> playFootstep(inWater = false)
        }
    }

    fun playGrassRustle() {
        if (!soundEnabled) return
        scope.launch {
            val samples = ShortArray((SAMPLE_RATE * 0.09f).toInt())
            val rng = Random(System.nanoTime())
            var prev = 0f
            for (i in samples.indices) {
                val t = i.toFloat() / SAMPLE_RATE
                val env = sin(PI * (t / 0.09f)).toFloat().coerceAtLeast(0f)
                val white = rng.nextFloat() * 2f - 1f
                // High-pass rustling swish like walking through grass & bushes
                val hp = white - prev * 0.65f
                prev = white
                samples[i] = ((hp * env * 0.28f).coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
            }
            playPcm(samples)
        }
    }

    fun playStoneRoadStep() {
        if (!soundEnabled) return
        scope.launch {
            val samples = ShortArray((SAMPLE_RATE * 0.055f).toInt())
            val rng = Random(System.nanoTime())
            for (i in samples.indices) {
                val t = i.toFloat() / SAMPLE_RATE
                val env = exp(-t * 68f)
                val clack = sin(2.0 * PI * 420.0 * t).toFloat() * 0.45f +
                    sin(2.0 * PI * 980.0 * t).toFloat() * 0.28f +
                    (rng.nextFloat() * 2f - 1f) * 0.26f
                samples[i] = ((clack * env * 0.42f).coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
            }
            playPcm(samples)
        }
    }

    fun playWoodStep() {
        if (!soundEnabled) return
        scope.launch {
            val samples = ShortArray((SAMPLE_RATE * 0.07f).toInt())
            for (i in samples.indices) {
                val t = i.toFloat() / SAMPLE_RATE
                val env = exp(-t * 44f)
                val knock = sin(2.0 * PI * 240.0 * t).toFloat() * 0.58f +
                    sin(2.0 * PI * 510.0 * t).toFloat() * 0.24f
                samples[i] = ((knock * env * 0.44f).coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
            }
            playPcm(samples)
        }
    }

    fun playDigEarth() {
        if (!soundEnabled) return
        scope.launch {
            val samples = ShortArray((SAMPLE_RATE * 0.16f).toInt())
            val rng = Random(System.nanoTime())
            var lp = 0f
            for (i in samples.indices) {
                val t = i.toFloat() / SAMPLE_RATE
                val env = exp(-t * 16f)
                lp = lp * 0.65f + (rng.nextFloat() * 2f - 1f) * 0.35f
                val scoop = sin(2.0 * PI * (120.0 - t * 240.0).coerceAtLeast(45.0) * t).toFloat() * 0.55f
                samples[i] = (((lp * 0.65f + scoop) * env * 0.58f).coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
            }
            playPcm(samples)
        }
    }

    // --- 3D Airplane Jet, Propeller, Afterburner & Sky Missile Sounds ---

    fun playPlaneStart(style: AircraftSoundStyle) {
        if (!soundEnabled) return
        scope.launch {
            val samples = ShortArray((SAMPLE_RATE * 0.55f).toInt())
            val rng = Random(System.nanoTime())
            var lp = 0f
            var phase = 0.0
            for (i in samples.indices) {
                val p = i.toFloat() / samples.size
                val env = sin(PI * p).toFloat().coerceAtLeast(0f)
                val freq = when (style) {
                    AircraftSoundStyle.PROPELLER_CLASSIC -> 48f + p * 82f
                    AircraftSoundStyle.FIGHTER_AFTERBURNER -> 140f + p * 420f
                    AircraftSoundStyle.VTOL_HYBRID -> 110f + p * 290f
                    AircraftSoundStyle.JET_TURBINE -> 95f + p * 240f
                }
                phase += 2.0 * PI * freq / SAMPLE_RATE
                lp = lp * 0.72f + (rng.nextFloat() * 2f - 1f) * 0.28f
                val whine = sin(phase).toFloat() * 0.42f + sin(phase * 2.5).toFloat() * 0.22f
                val jetAir = lp * (if (style == AircraftSoundStyle.PROPELLER_CLASSIC) 0.25f else 0.52f)
                samples[i] = (((whine + jetAir) * env * 0.62f).coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
            }
            playPcm(samples)
        }
    }

    fun playPlaneEnginePulse(style: AircraftSoundStyle, speedRatio: Float, boost: Boolean) {
        if (!soundEnabled) return
        scope.launch {
            val samples = ShortArray((SAMPLE_RATE * 0.22f).toInt())
            val rng = Random(System.nanoTime())
            var lp = 0f
            var phase = 0.0
            val sr = speedRatio.coerceIn(0f, 1.3f)
            val baseF = when (style) {
                AircraftSoundStyle.PROPELLER_CLASSIC -> 42f + sr * 44f
                AircraftSoundStyle.FIGHTER_AFTERBURNER -> 150f + sr * 220f + if (boost) 130f else 0f
                AircraftSoundStyle.VTOL_HYBRID -> 115f + sr * 150f
                AircraftSoundStyle.JET_TURBINE -> 90f + sr * 130f
            }
            for (i in samples.indices) {
                val p = i.toFloat() / samples.size
                val env = sin(PI * p).toFloat().coerceAtLeast(0f)
                phase += 2.0 * PI * baseF / SAMPLE_RATE
                lp = lp * 0.76f + (rng.nextFloat() * 2f - 1f) * 0.24f
                val tone = if (style == AircraftSoundStyle.PROPELLER_CLASSIC) {
                    val chop = if (sin(phase) > 0) 0.52f else -0.35f
                    chop + lp * 0.24f
                } else {
                    sin(phase).toFloat() * 0.30f + sin(phase * 2.0).toFloat() * 0.16f + lp * (if (boost) 0.75f else 0.46f)
                }
                val gain = if (boost) 0.56f else 0.34f
                samples[i] = ((tone * env * gain).coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
            }
            playPcm(samples)
        }
    }

    fun playPlaneAfterburner() {
        if (!soundEnabled) return
        scope.launch {
            val samples = ShortArray((SAMPLE_RATE * 0.48f).toInt())
            val rng = Random(System.nanoTime())
            var lp = 0f
            for (i in samples.indices) {
                val t = i.toFloat() / SAMPLE_RATE
                val env = exp(-t * 3.8f)
                lp = lp * 0.62f + (rng.nextFloat() * 2f - 1f) * 0.38f
                val boom = sin(2.0 * PI * (115.0 + t * 520.0) * t).toFloat() * 0.38f
                samples[i] = (((lp * 0.75f + boom) * env * 0.78f).coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
            }
            playPcm(samples)
        }
    }

    // --- Indian Wedding Shehnai + Dhol Celebration Sound ---

    fun playWeddingShehnai() {
        if (!soundEnabled) return
        scope.launch {
            val notes = floatArrayOf(523.25f, 587.33f, 659.25f, 783.99f, 659.25f, 783.99f, 1046.5f)
            val totalSamples = (SAMPLE_RATE * 0.84f).toInt()
            val samples = ShortArray(totalSamples)
            val noteLen = totalSamples / notes.size
            var phase = 0.0
            for (i in samples.indices) {
                val noteIdx = (i / noteLen).coerceIn(0, notes.lastIndex)
                val localP = (i % noteLen).toFloat() / noteLen
                val env = sin(PI * localP).toFloat().coerceAtLeast(0f)
                val vibrato = sin(2.0 * PI * 6.5 * i / SAMPLE_RATE).toFloat() * 8f
                val freq = notes[noteIdx] + vibrato
                phase += 2.0 * PI * freq / SAMPLE_RATE
                // Nasal reed timbre (Shehnai harmonics) + soft Dhol beat on alternating notes
                val reed = sin(phase).toFloat() * 0.45f +
                    sin(phase * 2.0).toFloat() * 0.30f +
                    sin(phase * 3.0).toFloat() * 0.18f
                val dhol = if (localP < 0.25f) sin(2.0 * PI * 115.0 * localP).toFloat() * exp(-localP * 14f) * 0.35f else 0f
                samples[i] = (((reed * env + dhol) * 0.52f).coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
            }
            playPcm(samples)
        }
    }

    // --- International War Horn & Sword Clash Sounds ---

    fun playWarHorn() {
        if (!soundEnabled) return
        scope.launch {
            val samples = ShortArray((SAMPLE_RATE * 0.72f).toInt())
            var phase = 0.0
            for (i in samples.indices) {
                val p = i.toFloat() / samples.size
                val env = sin(PI * p).toFloat().coerceAtLeast(0f)
                val freq = 148f + sin(PI * p).toFloat() * 24f
                phase += 2.0 * PI * freq / SAMPLE_RATE
                val brass = sin(phase).toFloat() * 0.55f +
                    sin(phase * 2.0).toFloat() * 0.32f +
                    sin(phase * 3.0).toFloat() * 0.18f
                samples[i] = ((brass * env * 0.66f).coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
            }
            playPcm(samples)
        }
    }

    fun playSwordClash() {
        if (!soundEnabled) return
        scope.launch {
            val samples = ShortArray((SAMPLE_RATE * 0.19f).toInt())
            val rng = Random(System.nanoTime())
            for (i in samples.indices) {
                val t = i.toFloat() / SAMPLE_RATE
                val env = exp(-t * 20f)
                val metal = sin(2.0 * PI * 1950.0 * t).toFloat() * 0.38f +
                    sin(2.0 * PI * 2840.0 * t).toFloat() * 0.30f +
                    sin(2.0 * PI * 3710.0 * t).toFloat() * 0.20f +
                    (rng.nextFloat() * 2f - 1f) * exp(-t * 55f) * 0.35f
                samples[i] = ((metal * env * 0.45f).coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
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
            "PUJA" -> playTempleBell()
            "HERO" -> playToneBurst(523f, 784f, 0.22f, 0.32f)
            "ZOMBIE" -> playZombieGroan()
            else -> playBlockPlace()
        }
    }

    private suspend fun playNightCricket() {
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

    private suspend fun playPcm(samples: ShortArray) {
        if (!audioSlots.tryAcquire()) return
        var track: AudioTrack? = null
        try {
            val rawMinBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (rawMinBuf <= 0) return
            val minBuf = rawMinBuf.coerceAtLeast(samples.size * 2)

            track = AudioTrack.Builder()
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

            if (track.state != AudioTrack.STATE_INITIALIZED) return
            track.write(samples, 0, samples.size)
            track.play()
            val waitMs = ((samples.size.toLong() * 1000L) / SAMPLE_RATE) + 35L
            delay(waitMs)
        } catch (_: Throwable) {
            // Headless JVM / unit test safe fallback
        } finally {
            runCatching { track?.stop() }
            runCatching { track?.release() }
            audioSlots.release()
        }
    }

    companion object {
        private const val SAMPLE_RATE = 22050
    }
}
