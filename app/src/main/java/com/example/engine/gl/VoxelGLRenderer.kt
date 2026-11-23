package com.example.engine.gl

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import com.example.data.model.AircraftType
import com.example.data.model.BikeType
import com.example.data.model.BlockType
import com.example.engine.audio.VoxelSoundEngine
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

data class HudTelemetry(
    val fps: Int = 60,
    val x: Int = 8,
    val y: Int = 18,
    val z: Int = 8,
    val yaw: Float = 0f,
    val targetBlock: BlockType? = null,
    val targetBlockX: Int? = null,
    val targetBlockY: Int? = null,
    val targetBlockZ: Int? = null,
    val flying: Boolean = false,
    val inWater: Boolean = false,
    val isUnderwater: Boolean = false,
    val inLava: Boolean = false,
    val sittingOnWater: Boolean = false,
    val waterAvailableForSit: Boolean = false,
    val activeBike: BikeType? = null,
    val bikeSpeedKmh: Int = 0,
    val bikeNitroActive: Boolean = false,
    val activePlane: AircraftType? = null,
    val planeSpeedKmh: Int = 0,
    val planeAltitude: Int = 0,
    val planeBoostActive: Boolean = false,
    val warActive: Boolean = false,
    val weddingsCount: Int = 0,
    val oxygen: Int = 10,
    val sprinting: Boolean = false,
    val sneaking: Boolean = false,
    val timeOfDay: Float = 0.25f,
    val playerHealth: Int = 10,
    val zombiesDefeated: Int = 0,
    val activeZombies: Int = 0,
    val totalEntities: Int = 0,
    val connectedAnimals: Int = 0,
    val minimapBlips: List<MinimapBlip> = emptyList(),
    val minimapColors: IntArray = IntArray(0),
    val worldEventToast: String? = null,
    val damageTick: Int = 0,
    val nearbyEntity: TalkableNpcSnapshot? = null,
    val nearbyEntities: List<TalkableNpcSnapshot> = emptyList()
)

enum class CameraMode(val shortLabel: String, val displayName: String) {
    FIRST_PERSON("1P", "1st-Person Eye View"),
    THIRD_PERSON_BACK("3P", "3rd-Person Over-Shoulder"),
    THIRD_PERSON_FRONT("FRONT", "Front Selfie Camera");

    fun next(): CameraMode = when (this) {
        FIRST_PERSON -> THIRD_PERSON_BACK
        THIRD_PERSON_BACK -> THIRD_PERSON_FRONT
        THIRD_PERSON_FRONT -> FIRST_PERSON
    }
}

/**
 * OpenGL ES 2.0 3D Voxel Renderer featuring:
 * - Pro 3-Mode Spring-Arm Camera (1st-Person, Smooth Over-Shoulder 3rd-Person, Front Selfie)
 * - Upgraded Animated Water & Waterfall Shaders + Glowing Molten Lava Shader
 * - Real-time Water & Lava channel flow physics + Real Estate City Rendering
 * - Autonomous Living Society, Heroes, Animals, Birds & Zombies
 * - Dynamic Day/Night Sky, Sun Halo, Moon, Rain Weather & Exact DDA Target Wireframe
 */
class VoxelGLRenderer(
    private val world: VoxelChunkWorld,
    val player: VoxelPlayerController,
    val entityManager: VoxelEntityManager,
    private val soundEngine: VoxelSoundEngine,
    private val onTelemetry: (HudTelemetry) -> Unit
) : GLSurfaceView.Renderer {

    @Volatile var paused: Boolean = false
    @Volatile var creativeMode: Boolean = false
    @Volatile var renderDistance: Int = 4
    @Volatile var autoTimeCycle: Boolean = true
    @Volatile var timeOfDay: Float = 0.22f
    @Volatile var cameraMode: CameraMode = CameraMode.FIRST_PERSON
    val thirdPersonCamera: Boolean
        get() = cameraMode != CameraMode.FIRST_PERSON
    @Volatile var rainEnabled: Boolean = false
    @Volatile var armSwingProgress: Float = 0f
    @Volatile var currentHit: VoxelRaycastHit? = null
        private set

    private var voxelProgram = 0
    private var waterProgram = 0
    private var lavaProgram = 0
    private var entityProgram = 0
    private var lineProgram = 0
    private var atlasTextureId = 0

    // Opaque & Flora Voxel shader handles
    private var aPos = 0; private var aTex = 0; private var aShade = 0
    private var uMvp = 0; private var uCamPos = 0; private var uDaylight = 0
    private var uFogColor = 0; private var uFogStart = 0; private var uFogEnd = 0; private var uTexture = 0

    // Animated Water & Waterfall shader handles
    private var wPos = 0; private var wTex = 0; private var wShade = 0
    private var wMvp = 0; private var wCamPos = 0; private var wDaylight = 0
    private var wFogColor = 0; private var wFogStart = 0; private var wFogEnd = 0
    private var wTexture = 0; private var wTime = 0

    // Animated Glowing Lava shader handles
    private var vPos = 0; private var vTex = 0; private var vShade = 0
    private var vMvp = 0; private var vCamPos = 0
    private var vFogColor = 0; private var vFogStart = 0; private var vFogEnd = 0
    private var vTexture = 0; private var vTime = 0

    // Colored 3D Entity/Particle shader handles
    private var ePos = 0; private var eColor = 0; private var eShade = 0
    private var eMvp = 0; private var eCamPos = 0; private var eDaylight = 0
    private var eFogColor = 0; private var eFogStart = 0; private var eFogEnd = 0

    // Line / Celestial / Rain shader handles
    private var lPos = 0; private var lMvp = 0; private var lColor = 0

    private val projMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val vpMatrix = FloatArray(16)
    private val celestialMvp = FloatArray(16)
    private val modelMatrix = FloatArray(16)

    private val wireframeBuffer: FloatBuffer = ByteBuffer.allocateDirect(24 * 3 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    private val rainBuffer: FloatBuffer = ByteBuffer.allocateDirect(160 * 2 * 3 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
    private val rainArray = FloatArray(160 * 6)

    private val celestialQuadBuffer: FloatBuffer = ByteBuffer.allocateDirect(6 * 3 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer().apply {
            val s = 1.0f
            put(floatArrayOf(-s, -s, 0f, s, -s, 0f, s, s, 0f, -s, -s, 0f, s, s, 0f, -s, s, 0f))
            position(0)
        }

    private var viewportAspect = 1.777f
    private var currentFov = 72f
    private var smoothCamDist = 4.2f
    private var elapsedTime = 0f
    private var bubbleTimer = 0f
    private var fluidStepTimer = 0f
    private var lastFrameNs = 0L
    private var fpsCounter = 0
    private var fpsTimerMs = 0L
    private var displayedFps = 60
    private var lastTelemetryMs = 0L
    private var lastMinimapMs = 0L
    private var cachedMinimapColors = IntArray(0)
    private var pendingWorldToast: String? = null
    private var damageShakeTimer = 0f
    private var damageCounter = 0
    private var lastPrewarmCx = Int.MIN_VALUE
    private var lastPrewarmCz = Int.MIN_VALUE
    private val prewarmExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "BlockForge-ChunkPrewarm").apply { isDaemon = true }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)
        GLES20.glFrontFace(GLES20.GL_CCW)

        voxelProgram = createProgram(VOXEL_VERTEX_SHADER, VOXEL_FRAGMENT_SHADER)
        aPos = GLES20.glGetAttribLocation(voxelProgram, "aPosition")
        aTex = GLES20.glGetAttribLocation(voxelProgram, "aTexCoord")
        aShade = GLES20.glGetAttribLocation(voxelProgram, "aShade")
        uMvp = GLES20.glGetUniformLocation(voxelProgram, "uMVPMatrix")
        uCamPos = GLES20.glGetUniformLocation(voxelProgram, "uCameraPos")
        uDaylight = GLES20.glGetUniformLocation(voxelProgram, "uDaylight")
        uFogColor = GLES20.glGetUniformLocation(voxelProgram, "uFogColor")
        uFogStart = GLES20.glGetUniformLocation(voxelProgram, "uFogStart")
        uFogEnd = GLES20.glGetUniformLocation(voxelProgram, "uFogEnd")
        uTexture = GLES20.glGetUniformLocation(voxelProgram, "uTexture")

        waterProgram = createProgram(WATER_VERTEX_SHADER, WATER_FRAGMENT_SHADER)
        wPos = GLES20.glGetAttribLocation(waterProgram, "aPosition")
        wTex = GLES20.glGetAttribLocation(waterProgram, "aTexCoord")
        wShade = GLES20.glGetAttribLocation(waterProgram, "aShade")
        wMvp = GLES20.glGetUniformLocation(waterProgram, "uMVPMatrix")
        wCamPos = GLES20.glGetUniformLocation(waterProgram, "uCameraPos")
        wDaylight = GLES20.glGetUniformLocation(waterProgram, "uDaylight")
        wFogColor = GLES20.glGetUniformLocation(waterProgram, "uFogColor")
        wFogStart = GLES20.glGetUniformLocation(waterProgram, "uFogStart")
        wFogEnd = GLES20.glGetUniformLocation(waterProgram, "uFogEnd")
        wTexture = GLES20.glGetUniformLocation(waterProgram, "uTexture")
        wTime = GLES20.glGetUniformLocation(waterProgram, "uTime")

        lavaProgram = createProgram(LAVA_VERTEX_SHADER, LAVA_FRAGMENT_SHADER)
        vPos = GLES20.glGetAttribLocation(lavaProgram, "aPosition")
        vTex = GLES20.glGetAttribLocation(lavaProgram, "aTexCoord")
        vShade = GLES20.glGetAttribLocation(lavaProgram, "aShade")
        vMvp = GLES20.glGetUniformLocation(lavaProgram, "uMVPMatrix")
        vCamPos = GLES20.glGetUniformLocation(lavaProgram, "uCameraPos")
        vFogColor = GLES20.glGetUniformLocation(lavaProgram, "uFogColor")
        vFogStart = GLES20.glGetUniformLocation(lavaProgram, "uFogStart")
        vFogEnd = GLES20.glGetUniformLocation(lavaProgram, "uFogEnd")
        vTexture = GLES20.glGetUniformLocation(lavaProgram, "uTexture")
        vTime = GLES20.glGetUniformLocation(lavaProgram, "uTime")

        entityProgram = createProgram(ENTITY_VERTEX_SHADER, ENTITY_FRAGMENT_SHADER)
        ePos = GLES20.glGetAttribLocation(entityProgram, "aPosition")
        eColor = GLES20.glGetAttribLocation(entityProgram, "aColor")
        eShade = GLES20.glGetAttribLocation(entityProgram, "aShade")
        eMvp = GLES20.glGetUniformLocation(entityProgram, "uMVPMatrix")
        eCamPos = GLES20.glGetUniformLocation(entityProgram, "uCameraPos")
        eDaylight = GLES20.glGetUniformLocation(entityProgram, "uDaylight")
        eFogColor = GLES20.glGetUniformLocation(entityProgram, "uFogColor")
        eFogStart = GLES20.glGetUniformLocation(entityProgram, "uFogStart")
        eFogEnd = GLES20.glGetUniformLocation(entityProgram, "uFogEnd")

        lineProgram = createProgram(LINE_VERTEX_SHADER, LINE_FRAGMENT_SHADER)
        lPos = GLES20.glGetAttribLocation(lineProgram, "aPosition")
        lMvp = GLES20.glGetUniformLocation(lineProgram, "uMVPMatrix")
        lColor = GLES20.glGetUniformLocation(lineProgram, "uColor")

        atlasTextureId = uploadProceduralAtlas()
        lastFrameNs = System.nanoTime()
        fpsTimerMs = System.currentTimeMillis()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width.coerceAtLeast(1), height.coerceAtLeast(1))
        viewportAspect = width.toFloat() / height.coerceAtLeast(1).toFloat()
        Matrix.perspectiveM(projMatrix, 0, currentFov, viewportAspect, 0.12f, 260f)
    }

    fun triggerArmSwing() {
        armSwingProgress = 1.0f
    }

    override fun onDrawFrame(gl: GL10?) {
        val nowNs = System.nanoTime()
        val dt = ((nowNs - lastFrameNs) / 1_000_000_000.0f).coerceIn(0.001f, 0.05f)
        lastFrameNs = nowNs

        val isNight = sin(timeOfDay * 2f * PI.toFloat()) < -0.05f

        if (!paused) {
            elapsedTime += dt
            if (armSwingProgress > 0f) {
                armSwingProgress = (armSwingProgress - dt * 4.5f).coerceAtLeast(0f)
            }
            if (damageShakeTimer > 0f) {
                damageShakeTimer = (damageShakeTimer - dt).coerceAtLeast(0f)
            }
            if (autoTimeCycle) {
                timeOfDay = (timeOfDay + dt * 0.0038f) % 1.0f
            }

            // Real-time Water & Lava Channel Physics (~5.5 Hz tick for smooth animated fluid spreading!)
            fluidStepTimer += dt
            if (fluidStepTimer >= 0.18f) {
                fluidStepTimer = 0f
                val fluidEvents = world.stepFluidPhysics()
                if (fluidEvents.obsidianFormed > 0) {
                    soundEngine.playLavaSizzle()
                    fluidEvents.sizzlePos?.let { (sx, sy, sz) ->
                        entityManager.spawnSteamBurst(sx, sy, sz)
                    }
                } else if (fluidEvents.waterFlowed > 2) {
                    soundEngine.playWaterRushPulse()
                } else if (fluidEvents.lavaFlowed > 1) {
                    soundEngine.playLavaBubble()
                }
            }

            val stepEvents = player.stepPhysics(world, dt, creativeMode)
            if (stepEvents.enteredWater) {
                soundEngine.playWaterSplash()
                entityManager.spawnWaterSplashParticles(player.x, player.y, player.z)
            } else if (stepEvents.enteredLava) {
                soundEngine.playLavaSizzle()
                entityManager.spawnLavaEmberParticles(player.x, player.y, player.z)
            } else if (stepEvents.footstep) {
                val underFoot = world.getBlock(floor(player.x).toInt(), floor(player.y - 0.12f).toInt(), floor(player.z).toInt())
                soundEngine.playSurfaceFootstep(underFoot, stepEvents.inWater)
                if (stepEvents.inWater) {
                    entityManager.spawnWaterSplashParticles(player.x, player.y + 0.1f, player.z)
                }
            }
            if (stepEvents.waterSitRipple) {
                soundEngine.playWaterSitRipple()
                entityManager.spawnWaterSitRipples(player.x, player.y + 0.14f, player.z)
            }
            val activePlaneNow = stepEvents.flyingPlane
            if (activePlaneNow != null) {
                if (stepEvents.planeEnginePulse) {
                    soundEngine.playPlaneEnginePulse(
                        style = activePlaneNow.soundStyle,
                        speedRatio = stepEvents.planeSpeedRatio,
                        boost = stepEvents.planeBoostActive
                    )
                    entityManager.spawnPlaneContrailParticles(
                        x = player.x - sin(player.yaw) * 2.1f,
                        y = player.y + 0.85f,
                        z = player.z + cos(player.yaw) * 2.1f,
                        boost = stepEvents.planeBoostActive
                    )
                }
            }
            val activeBikeNow = stepEvents.ridingBike
            if (activeBikeNow != null) {
                if (stepEvents.bikeEnginePulse) {
                    soundEngine.playBikeEnginePulse(
                        style = activeBikeNow.soundStyle,
                        speedRatio = stepEvents.bikeSpeedRatio,
                        nitro = stepEvents.bikeNitroActive
                    )
                    entityManager.spawnBikeExhaustParticles(
                        x = player.x - sin(player.yaw) * 0.95f,
                        y = player.y + 0.35f,
                        z = player.z + cos(player.yaw) * 0.95f,
                        nitro = stepEvents.bikeNitroActive,
                        isElectric = activeBikeNow.hasNeonWheels
                    )
                }
            }
            if (stepEvents.isUnderwater) {
                bubbleTimer += dt
                if (bubbleTimer >= 0.22f) {
                    bubbleTimer = 0f
                    entityManager.spawnUnderwaterBubble(player.x, player.eyeY, player.z)
                }
            }

            val events = entityManager.stepSimulation(world, player, dt, creativeMode, isNight)
            if (events.zombieKills > 0) {
                soundEngine.playZombieHit(defeated = true)
            } else if (events.zombieHits > 0) {
                soundEngine.playZombieHit(defeated = false)
            }
            if (events.playerDamaged) {
                soundEngine.playPlayerHurt()
                damageShakeTimer = 0.28f
                damageCounter++
            }
            if (events.pujaBellTriggered) {
                soundEngine.playTempleBell()
            }
            if (events.humanWorkSoundTriggered) {
                soundEngine.playHumanWorkHammer()
            }
            if (events.weddingTriggered) {
                soundEngine.playWeddingShehnai()
            }
            if (events.warClashTriggered) {
                soundEngine.playSwordClash()
            }
            if (events.aiActivityToast != null) {
                pendingWorldToast = events.aiActivityToast
            }
        }

        val sky = computeDayNightPalette(timeOfDay, player.isUnderwater, rainEnabled)
        GLES20.glClearColor(sky.skyR, sky.skyG, sky.skyB, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val (fx, fy, fz) = player.forwardVector()
        val baseEyeX = player.x
        val baseEyeY = player.eyeY
        val baseEyeZ = player.z

        var camX = baseEyeX
        var camY = baseEyeY
        var camZ = baseEyeZ
        var lookX = baseEyeX + fx
        var lookY = baseEyeY + fy
        var lookZ = baseEyeZ + fz

        if (damageShakeTimer > 0f) {
            val shakeAmt = (damageShakeTimer / 0.28f) * 0.16f
            val sx = sin(elapsedTime * 68f) * shakeAmt
            val sy = cos(elapsedTime * 54f) * shakeAmt * 0.7f
            camX += sx
            camY += sy
            lookX += sx * 0.5f
            lookY += sy * 0.5f
        }

        val mode = cameraMode
        if (mode != CameraMode.FIRST_PERSON) {
            val rightX = cos(player.yaw)
            val rightZ = sin(player.yaw)
            val isFront = mode == CameraMode.THIRD_PERSON_FRONT
            val inPlane = player.activePlane != null
            val onBike = player.activeBike != null
            val desiredDist = when {
                inPlane -> if (isFront) 7.5f else 8.8f
                isFront -> 3.9f
                onBike -> 5.45f
                player.sittingOnWater -> 4.6f
                else -> 4.35f
            }
            val pivotX = baseEyeX + if (isFront || onBike || inPlane) 0f else rightX * 0.42f
            val pivotY = baseEyeY + when {
                inPlane -> 1.15f
                onBike -> 0.42f
                else -> 0.14f
            }
            val pivotZ = baseEyeZ + if (isFront || onBike || inPlane) 0f else rightZ * 0.42f

            // Direction vector from pivot toward the orbiting camera
            val dirX = if (isFront) fx else -fx
            val dirY = if (isFront) -fy * 0.55f + 0.12f else -fy * 0.85f + (if (inPlane) 0.26f else if (onBike) 0.22f else 0.16f)
            val dirZ = if (isFront) fz else -fz
            val dirLen = kotlin.math.sqrt((dirX * dirX + dirY * dirY + dirZ * dirZ).toDouble()).toFloat().coerceAtLeast(0.001f)
            val ndx = dirX / dirLen
            val ndy = dirY / dirLen
            val ndz = dirZ / dirLen

            // 24-step smooth collision raycast with 0.30f wall margin so camera NEVER clips inside blocks!
            var hitSafeDist = desiredDist
            val steps = 24
            for (step in 1..steps) {
                val d = (step / steps.toFloat()) * desiredDist
                val tx = pivotX + ndx * d
                val ty = pivotY + ndy * d
                val tz = pivotZ + ndz * d
                if (world.isSolid(floor(tx).toInt(), floor(ty).toInt(), floor(tz).toInt())) {
                    hitSafeDist = (d - 0.32f).coerceAtLeast(0.65f)
                    break
                }
            }
            // Pull in instantaneously on wall hit; smoothly extend back out when clear (zero snappy jumping!)
            smoothCamDist = if (hitSafeDist < smoothCamDist) {
                hitSafeDist
            } else {
                smoothCamDist + (hitSafeDist - smoothCamDist) * (dt * 8.5f).coerceIn(0f, 1f)
            }

            camX = pivotX + ndx * smoothCamDist
            camY = (pivotY + ndy * smoothCamDist).coerceAtLeast(1.4f)
            camZ = pivotZ + ndz * smoothCamDist

            if (isFront) {
                // Selfie camera frames player's upper body & face smoothly
                lookX = baseEyeX
                lookY = baseEyeY - 0.15f
                lookZ = baseEyeZ
            } else {
                // Over-shoulder / Bike / Airplane Chase camera converges smoothly onto the player's true forward aim point
                val aimDist = if (inPlane) 26.0f else 18.0f
                lookX = baseEyeX + fx * aimDist
                lookY = baseEyeY + fy * aimDist
                lookZ = baseEyeZ + fz * aimDist
            }
        }

        val targetFov = when {
            player.planeBoostTimer > 0f -> 94f
            player.activePlane != null -> 85f
            player.bikeNitroTimer > 0f -> 88f
            player.activeBike != null && player.bikeSpeedKmh > 60 -> 82f
            player.isEffectivelySprinting && player.moveSpeedFraction > 0.25f -> 80f
            mode != CameraMode.FIRST_PERSON -> 74f
            else -> 72f
        }
        if (kotlin.math.abs(currentFov - targetFov) > 0.04f) {
            currentFov += (targetFov - currentFov) * (dt * 9.5f).coerceIn(0f, 1f)
            Matrix.perspectiveM(projMatrix, 0, currentFov, viewportAspect, 0.12f, 260f)
        }

        Matrix.setLookAtM(viewMatrix, 0, camX, camY, camZ, lookX, lookY, lookZ, 0f, 1f, 0f)
        Matrix.multiplyMM(vpMatrix, 0, projMatrix, 0, viewMatrix, 0)

        if (!player.isUnderwater) {
            drawCelestialBodies(camX, camY, camZ, timeOfDay)
        }

        // Trigger async background pre-warming whenever entering a new chunk so borders NEVER stutter!
        val curCx = Math.floorDiv(floor(player.x).toInt(), CHUNK_SIZE)
        val curCz = Math.floorDiv(floor(player.z).toInt(), CHUNK_SIZE)
        if (curCx != lastPrewarmCx || curCz != lastPrewarmCz) {
            lastPrewarmCx = curCx
            lastPrewarmCz = curCz
            val rd = renderDistance
            prewarmExecutor.execute {
                world.prewarmChunksAround(curCx, curCz, rd + 1)
            }
        }

        val rebuildBudget = if (player.moveSpeedFraction > 0.15f) 1 else 2
        val meshes = world.prepareVisibleMeshes(
            playerX = player.x,
            playerZ = player.z,
            renderDistance = renderDistance,
            maxRebuildsPerFrame = rebuildBudget
        )

        val fogEnd = if (player.isUnderwater) 18f else (renderDistance * CHUNK_SIZE + 14).toFloat()
        val fogStart = if (player.isUnderwater) 2.5f else (fogEnd * 0.56f).coerceAtLeast(18f)

        // Pass 1: Opaque Terrain Blocks
        GLES20.glUseProgram(voxelProgram)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, vpMatrix, 0)
        GLES20.glUniform3f(uCamPos, camX, camY, camZ)
        GLES20.glUniform3f(uDaylight, sky.lightR, sky.lightG, sky.lightB)
        GLES20.glUniform3f(uFogColor, sky.skyR, sky.skyG, sky.skyB)
        GLES20.glUniform1f(uFogStart, fogStart)
        GLES20.glUniform1f(uFogEnd, fogEnd)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, atlasTextureId)
        GLES20.glUniform1i(uTexture, 0)

        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glEnableVertexAttribArray(aTex)
        GLES20.glEnableVertexAttribArray(aShade)

        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glDepthMask(true)
        for (i in meshes.indices) {
            val m = meshes[i]
            val buf = m.opaqueBuffer ?: continue
            drawVoxelBuffer(buf, m.opaqueVertexCount, aPos, aTex, aShade)
        }

        // Pass 2: Flowers, Tall Grass, Glass & Ice
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        for (i in meshes.indices) {
            val m = meshes[i]
            val buf = m.translucentBuffer ?: continue
            drawVoxelBuffer(buf, m.translucentVertexCount, aPos, aTex, aShade)
        }
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aTex)
        GLES20.glDisableVertexAttribArray(aShade)

        // Pass 3: Animated Glowing Molten Lava Shader (Volcanoes & Underground Lava!)
        drawAnimatedLava(meshes, camX, camY, camZ, sky, fogStart, fogEnd)

        // Pass 4: 3D Articulated Entities, 3rd-Person Avatar, Gun Bullets & Particles
        drawLivingEntities(camX, camY, camZ, sky, fogStart, fogEnd)

        // Pass 5: Animated Crystal Water, Rivers, Underground Aquifers & Cascading Waterfalls Shader!
        drawAnimatedWater(meshes, camX, camY, camZ, sky, fogStart, fogEnd)

        // Pass 6: Dynamic 3D Rain Weather
        if (rainEnabled && !player.isUnderwater) {
            drawRainStreaks(player.x, player.y, player.z)
        }

        // Pass 7: Exact Raycast Target Block Wireframe
        val hit = player.raycastTarget(world, maxDistance = 6.5f)
        currentHit = hit
        if (hit != null) {
            drawTargetWireframe(hit.x, hit.y, hit.z)
        }

        // Telemetry (~8-10 Hz)
        fpsCounter++
        val nowMs = System.currentTimeMillis()
        if (nowMs - fpsTimerMs >= 1000L) {
            displayedFps = fpsCounter
            fpsCounter = 0
            fpsTimerMs = nowMs
        }
        if (nowMs - lastTelemetryMs >= 160L) {
            lastTelemetryMs = nowMs
            val px = floor(player.x).toInt()
            val py = floor(player.y).toInt()
            val pz = floor(player.z).toInt()
            soundEngine.isNightTime = isNight
            soundEngine.isUnderwater = player.isUnderwater
            soundEngine.isSittingOnWater = player.sittingOnWater
            soundEngine.isRidingBike = player.activeBike != null
            soundEngine.isFlyingPlane = player.activePlane != null

            if (nowMs - lastMinimapMs >= 1100L || cachedMinimapColors.isEmpty()) {
                lastMinimapMs = nowMs
                // Expanded 93x93 block live map radar covering Real Estate Township, Roads & Landmarks!
                cachedMinimapColors = world.sampleMinimapColors(px, pz, gridSize = 31, step = 3)
                soundEngine.isNearWater = world.isNearWater(px, py, pz)
                soundEngine.isNearLava = world.isNearLava(px, py, pz)
            }

            val toastToSend = pendingWorldToast
            pendingWorldToast = null
            val (totalEnt, activeZom) = entityManager.activeCounts()
            val nearbyList = entityManager.findNearbyInteractiveEntities(player, maxRange = 7.5f)
            onTelemetry(
                HudTelemetry(
                    fps = displayedFps,
                    x = px,
                    y = py,
                    z = pz,
                    yaw = player.yaw,
                    targetBlock = hit?.block,
                    targetBlockX = hit?.x,
                    targetBlockY = hit?.y,
                    targetBlockZ = hit?.z,
                    flying = player.flying,
                    inWater = player.inWater,
                    isUnderwater = player.isUnderwater,
                    inLava = player.inLava,
                    sittingOnWater = player.sittingOnWater,
                    waterAvailableForSit = player.isWaterAvailableForSit(world),
                    activeBike = player.activeBike,
                    bikeSpeedKmh = player.bikeSpeedKmh,
                    bikeNitroActive = player.bikeNitroTimer > 0f,
                    activePlane = player.activePlane,
                    planeSpeedKmh = player.planeSpeedKmh,
                    planeAltitude = player.planeAltitude,
                    planeBoostActive = player.planeBoostTimer > 0f,
                    warActive = entityManager.warModeEnabled,
                    weddingsCount = entityManager.weddingsCount,
                    oxygen = player.oxygen.toInt().coerceAtLeast(0).coerceAtMost(10),
                    sprinting = player.isEffectivelySprinting,
                    sneaking = player.sneaking,
                    timeOfDay = timeOfDay,
                    playerHealth = entityManager.playerHealth,
                    zombiesDefeated = entityManager.zombiesDefeated,
                    activeZombies = activeZom,
                    totalEntities = totalEnt,
                    connectedAnimals = entityManager.connectedAnimalCount(),
                    minimapBlips = entityManager.snapshotMinimapBlips(player.x, player.z, maxRange = 46f),
                    minimapColors = cachedMinimapColors,
                    worldEventToast = toastToSend,
                    damageTick = damageCounter,
                    nearbyEntity = nearbyList.firstOrNull(),
                    nearbyEntities = nearbyList
                )
            )
        }
    }

    private fun drawAnimatedWater(
        meshes: List<ChunkMeshData>,
        camX: Float, camY: Float, camZ: Float,
        sky: DayNightPalette, fogStart: Float, fogEnd: Float
    ) {
        GLES20.glUseProgram(waterProgram)
        GLES20.glUniformMatrix4fv(wMvp, 1, false, vpMatrix, 0)
        GLES20.glUniform3f(wCamPos, camX, camY, camZ)
        GLES20.glUniform3f(wDaylight, sky.lightR, sky.lightG, sky.lightB)
        GLES20.glUniform3f(wFogColor, sky.skyR, sky.skyG, sky.skyB)
        GLES20.glUniform1f(wFogStart, fogStart)
        GLES20.glUniform1f(wFogEnd, fogEnd)
        GLES20.glUniform1f(wTime, elapsedTime)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, atlasTextureId)
        GLES20.glUniform1i(wTexture, 0)

        GLES20.glEnableVertexAttribArray(wPos)
        GLES20.glEnableVertexAttribArray(wTex)
        GLES20.glEnableVertexAttribArray(wShade)

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDepthMask(false)
        GLES20.glDisable(GLES20.GL_CULL_FACE)

        for (i in meshes.indices) {
            val m = meshes[i]
            val buf = m.waterBuffer ?: continue
            drawVoxelBuffer(buf, m.waterVertexCount, wPos, wTex, wShade)
        }

        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glDisableVertexAttribArray(wPos)
        GLES20.glDisableVertexAttribArray(wTex)
        GLES20.glDisableVertexAttribArray(wShade)
    }

    private fun drawAnimatedLava(
        meshes: List<ChunkMeshData>,
        camX: Float, camY: Float, camZ: Float,
        sky: DayNightPalette, fogStart: Float, fogEnd: Float
    ) {
        GLES20.glUseProgram(lavaProgram)
        GLES20.glUniformMatrix4fv(vMvp, 1, false, vpMatrix, 0)
        GLES20.glUniform3f(vCamPos, camX, camY, camZ)
        GLES20.glUniform3f(vFogColor, sky.skyR, sky.skyG, sky.skyB)
        GLES20.glUniform1f(vFogStart, fogStart)
        GLES20.glUniform1f(vFogEnd, fogEnd)
        GLES20.glUniform1f(vTime, elapsedTime)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, atlasTextureId)
        GLES20.glUniform1i(vTexture, 0)

        GLES20.glEnableVertexAttribArray(vPos)
        GLES20.glEnableVertexAttribArray(vTex)
        GLES20.glEnableVertexAttribArray(vShade)

        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glDisable(GLES20.GL_CULL_FACE)

        for (i in meshes.indices) {
            val m = meshes[i]
            val buf = m.lavaBuffer ?: continue
            drawVoxelBuffer(buf, m.lavaVertexCount, vPos, vTex, vShade)
        }

        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glDisableVertexAttribArray(vPos)
        GLES20.glDisableVertexAttribArray(vTex)
        GLES20.glDisableVertexAttribArray(vShade)
    }

    private fun drawLivingEntities(
        camX: Float, camY: Float, camZ: Float,
        sky: DayNightPalette, fogStart: Float, fogEnd: Float
    ) {
        val (buf, vertCount) = entityManager.buildRenderBuffer(
            playerForThirdPerson = if (thirdPersonCamera) player else null,
            armSwingProgress = armSwingProgress
        )
        if (vertCount <= 0) return

        GLES20.glUseProgram(entityProgram)
        GLES20.glUniformMatrix4fv(eMvp, 1, false, vpMatrix, 0)
        GLES20.glUniform3f(eCamPos, camX, camY, camZ)
        GLES20.glUniform3f(eDaylight, sky.lightR, sky.lightG, sky.lightB)
        GLES20.glUniform3f(eFogColor, sky.skyR, sky.skyG, sky.skyB)
        GLES20.glUniform1f(eFogStart, fogStart)
        GLES20.glUniform1f(eFogEnd, fogEnd)

        val stride = 7 * 4
        GLES20.glEnableVertexAttribArray(ePos)
        GLES20.glEnableVertexAttribArray(eColor)
        GLES20.glEnableVertexAttribArray(eShade)

        buf.position(0)
        GLES20.glVertexAttribPointer(ePos, 3, GLES20.GL_FLOAT, false, stride, buf)
        buf.position(3)
        GLES20.glVertexAttribPointer(eColor, 3, GLES20.GL_FLOAT, false, stride, buf)
        buf.position(6)
        GLES20.glVertexAttribPointer(eShade, 1, GLES20.GL_FLOAT, false, stride, buf)

        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertCount)
        GLES20.glEnable(GLES20.GL_CULL_FACE)

        GLES20.glDisableVertexAttribArray(ePos)
        GLES20.glDisableVertexAttribArray(eColor)
        GLES20.glDisableVertexAttribArray(eShade)
    }

    private fun drawRainStreaks(px: Float, py: Float, pz: Float) {
        var idx = 0
        for (i in 0 until 160) {
            val hashA = ((i * 73856093) xor 19349663) and 0xFFFF
            val hashB = ((i * 83492791) xor 297121507) and 0xFFFF
            val rx = px + ((hashA % 240) / 10f - 12f)
            val rz = pz + ((hashB % 240) / 10f - 12f)
            val fall = ((elapsedTime * 15f + (i * 0.37f)) % 14f)
            val ry = py + 11f - fall
            rainArray[idx++] = rx; rainArray[idx++] = ry; rainArray[idx++] = rz
            rainArray[idx++] = rx; rainArray[idx++] = ry - 0.65f; rainArray[idx++] = rz
        }
        rainBuffer.position(0)
        rainBuffer.put(rainArray, 0, idx)
        rainBuffer.position(0)

        GLES20.glUseProgram(lineProgram)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUniformMatrix4fv(lMvp, 1, false, vpMatrix, 0)
        GLES20.glUniform4f(lColor, 0.70f, 0.88f, 1.0f, 0.55f)
        GLES20.glLineWidth(2.0f)
        GLES20.glEnableVertexAttribArray(lPos)
        GLES20.glVertexAttribPointer(lPos, 3, GLES20.GL_FLOAT, false, 0, rainBuffer)
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, 320)
        GLES20.glDisableVertexAttribArray(lPos)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun drawVoxelBuffer(buffer: FloatBuffer, vertexCount: Int, posLoc: Int, texLoc: Int, shadeLoc: Int) {
        val stride = 6 * 4
        buffer.position(0)
        GLES20.glVertexAttribPointer(posLoc, 3, GLES20.GL_FLOAT, false, stride, buffer)
        buffer.position(3)
        GLES20.glVertexAttribPointer(texLoc, 2, GLES20.GL_FLOAT, false, stride, buffer)
        buffer.position(5)
        GLES20.glVertexAttribPointer(shadeLoc, 1, GLES20.GL_FLOAT, false, stride, buffer)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)
    }

    private fun drawCelestialBodies(eyeX: Float, eyeY: Float, eyeZ: Float, t: Float) {
        val angle = t * 2f * PI.toFloat()
        val dist = 132f
        val sunX = eyeX + cos(angle) * dist
        val sunY = eyeY + sin(angle) * dist
        val sunZ = eyeZ - 24f

        val moonX = eyeX - cos(angle) * dist
        val moonY = eyeY - sin(angle) * dist
        val moonZ = eyeZ - 24f

        GLES20.glUseProgram(lineProgram)
        GLES20.glDepthMask(false)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glEnableVertexAttribArray(lPos)
        celestialQuadBuffer.position(0)
        GLES20.glVertexAttribPointer(lPos, 3, GLES20.GL_FLOAT, false, 0, celestialQuadBuffer)

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, sunX, sunY, sunZ)
        Matrix.scaleM(modelMatrix, 0, 9.2f, 9.2f, 9.2f)
        Matrix.multiplyMM(celestialMvp, 0, vpMatrix, 0, modelMatrix, 0)
        GLES20.glUniformMatrix4fv(lMvp, 1, false, celestialMvp, 0)
        GLES20.glUniform4f(lColor, 1.0f, 0.85f, 0.35f, 0.32f)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, sunX, sunY, sunZ)
        Matrix.scaleM(modelMatrix, 0, 5.8f, 5.8f, 5.8f)
        Matrix.multiplyMM(celestialMvp, 0, vpMatrix, 0, modelMatrix, 0)
        GLES20.glUniformMatrix4fv(lMvp, 1, false, celestialMvp, 0)
        GLES20.glUniform4f(lColor, 1.0f, 0.96f, 0.68f, 1.0f)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, moonX, moonY, moonZ)
        Matrix.scaleM(modelMatrix, 0, 4.4f, 4.4f, 4.4f)
        Matrix.multiplyMM(celestialMvp, 0, vpMatrix, 0, modelMatrix, 0)
        GLES20.glUniformMatrix4fv(lMvp, 1, false, celestialMvp, 0)
        GLES20.glUniform4f(lColor, 0.86f, 0.93f, 1.0f, 1.0f)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)

        GLES20.glDisableVertexAttribArray(lPos)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glDepthMask(true)
    }

    private fun drawTargetWireframe(bx: Int, by: Int, bz: Int) {
        val e = 0.008f
        val x0 = bx - e; val y0 = by - e; val z0 = bz - e
        val x1 = bx + 1f + e; val y1 = by + 1f + e; val z1 = bz + 1f + e

        val lines = floatArrayOf(
            x0, y0, z0, x1, y0, z0, x1, y0, z0, x1, y0, z1, x1, y0, z1, x0, y0, z1, x0, y0, z1, x0, y0, z0,
            x0, y1, z0, x1, y1, z0, x1, y1, z0, x1, y1, z1, x1, y1, z1, x0, y1, z1, x0, y1, z1, x0, y1, z0,
            x0, y0, z0, x0, y1, z0, x1, y0, z0, x1, y1, z0, x1, y0, z1, x1, y1, z1, x0, y0, z1, x0, y1, z1
        )
        wireframeBuffer.position(0)
        wireframeBuffer.put(lines)
        wireframeBuffer.position(0)

        GLES20.glUseProgram(lineProgram)
        GLES20.glUniformMatrix4fv(lMvp, 1, false, vpMatrix, 0)
        GLES20.glUniform4f(lColor, 0.21f, 0.91f, 1.0f, 0.95f)
        GLES20.glLineWidth(4.0f)
        GLES20.glEnableVertexAttribArray(lPos)
        GLES20.glVertexAttribPointer(lPos, 3, GLES20.GL_FLOAT, false, 0, wireframeBuffer)
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, 24)
        GLES20.glDisableVertexAttribArray(lPos)
    }

    private fun uploadProceduralAtlas(): Int {
        val texIds = IntArray(1)
        GLES20.glGenTextures(1, texIds, 0)
        val id = texIds[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        val bmp = VoxelTextureAtlas.buildAtlasBitmap()
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
        bmp.recycle()
        return id
    }

    private fun createProgram(vertexSrc: String, fragmentSrc: String): Int {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertexSrc)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)
        return program
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        return shader
    }

    private data class DayNightPalette(
        val skyR: Float, val skyG: Float, val skyB: Float,
        val lightR: Float, val lightG: Float, val lightB: Float
    )

    private fun computeDayNightPalette(tRaw: Float, underwater: Boolean, raining: Boolean): DayNightPalette {
        if (underwater) {
            return DayNightPalette(0.05f, 0.34f, 0.56f, 0.48f, 0.80f, 0.98f)
        }
        val t = ((tRaw % 1f) + 1f) % 1f
        val sunHeight = sin(t * 2f * PI.toFloat())
        val dayFactor = ((sunHeight + 0.25f) / 1.25f).coerceIn(0f, 1f)
        val sunsetBand = (1f - kotlin.math.abs(sunHeight) * 3.5f).coerceIn(0f, 1f)
        val rainDim = if (raining) 0.72f else 1.0f

        val skyR = ((0.04f + dayFactor * 0.44f + sunsetBand * 0.36f) * rainDim).coerceIn(0f, 1f)
        val skyG = ((0.07f + dayFactor * 0.67f + sunsetBand * 0.14f) * rainDim).coerceIn(0f, 1f)
        val skyB = ((0.16f + dayFactor * 0.80f - sunsetBand * 0.12f) * (if (raining) 0.85f else 1f)).coerceIn(0f, 1f)

        val lightR = ((0.36f + dayFactor * 0.64f + sunsetBand * 0.14f) * rainDim).coerceIn(0.32f, 1.05f)
        val lightG = ((0.38f + dayFactor * 0.62f) * rainDim).coerceIn(0.32f, 1.0f)
        val lightB = ((0.50f + dayFactor * 0.50f - sunsetBand * 0.12f) * (if (raining) 0.88f else 1f)).coerceIn(0.34f, 1.0f)

        return DayNightPalette(skyR, skyG, skyB, lightR, lightG, lightB)
    }

    companion object {
        private const val VOXEL_VERTEX_SHADER = """
            uniform mat4 uMVPMatrix;
            uniform vec3 uCameraPos;
            attribute vec3 aPosition;
            attribute vec2 aTexCoord;
            attribute float aShade;
            varying vec2 vTexCoord;
            varying float vShade;
            varying float vDist;
            void main() {
                gl_Position = uMVPMatrix * vec4(aPosition, 1.0);
                vTexCoord = aTexCoord;
                vShade = aShade;
                vDist = distance(aPosition, uCameraPos);
            }
        """

        private const val VOXEL_FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uTexture;
            uniform vec3 uDaylight;
            uniform vec3 uFogColor;
            uniform float uFogStart;
            uniform float uFogEnd;
            varying vec2 vTexCoord;
            varying float vShade;
            varying float vDist;
            void main() {
                vec4 tex = texture2D(uTexture, vTexCoord);
                if (tex.a < 0.12) discard;
                vec3 lit = tex.rgb * vShade * uDaylight;
                float fogFactor = clamp((vDist - uFogStart) / max(1.0, (uFogEnd - uFogStart)), 0.0, 1.0);
                gl_FragColor = vec4(mix(lit, uFogColor, fogFactor), tex.a);
            }
        """

        // Upgraded Layered Wave Animated Water & Waterfall Shader
        private const val WATER_VERTEX_SHADER = """
            uniform mat4 uMVPMatrix;
            uniform vec3 uCameraPos;
            uniform float uTime;
            attribute vec3 aPosition;
            attribute vec2 aTexCoord;
            attribute float aShade;
            varying vec2 vTexCoord;
            varying float vShade;
            varying float vDist;
            varying vec3 vWorldPos;
            void main() {
                vec3 pos = aPosition;
                float wave1 = sin(pos.x * 2.2 + uTime * 3.4) * cos(pos.z * 2.0 + uTime * 2.8) * 0.065;
                float wave2 = sin((pos.x - pos.z) * 3.1 - uTime * 4.2) * 0.035;
                pos.y += (wave1 + wave2);
                gl_Position = uMVPMatrix * vec4(pos, 1.0);
                vTexCoord = aTexCoord;
                vShade = aShade;
                vDist = distance(pos, uCameraPos);
                vWorldPos = pos;
            }
        """

        private const val WATER_FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uTexture;
            uniform vec3 uDaylight;
            uniform vec3 uFogColor;
            uniform float uFogStart;
            uniform float uFogEnd;
            uniform float uTime;
            varying vec2 vTexCoord;
            varying float vShade;
            varying float vDist;
            varying vec3 vWorldPos;
            void main() {
                vec4 tex = texture2D(uTexture, vTexCoord);
                float ripple1 = sin(vWorldPos.x * 4.0 + vWorldPos.y * 5.2 - uTime * 5.6 + vWorldPos.z * 3.4);
                float ripple2 = cos(vWorldPos.x * 3.2 - vWorldPos.z * 4.4 + uTime * 4.1);
                float shimmer = 0.90 + 0.14 * (ripple1 * 0.65 + ripple2 * 0.35);
                float foam = smoothstep(0.95, 1.04, shimmer) * 0.32;
                vec3 lit = (tex.rgb * shimmer + vec3(foam * 0.85, foam * 0.96, foam)) * vShade * uDaylight;
                float fogFactor = clamp((vDist - uFogStart) / max(1.0, (uFogEnd - uFogStart)), 0.0, 1.0);
                gl_FragColor = vec4(mix(lit, uFogColor, fogFactor), 0.82);
            }
        """

        // Dedicated Animated Volcanic Lava Shader (undulating molten ripples + self-illuminated magma veins!)
        private const val LAVA_VERTEX_SHADER = """
            uniform mat4 uMVPMatrix;
            uniform vec3 uCameraPos;
            uniform float uTime;
            attribute vec3 aPosition;
            attribute vec2 aTexCoord;
            attribute float aShade;
            varying vec2 vTexCoord;
            varying float vShade;
            varying float vDist;
            varying vec3 vWorldPos;
            void main() {
                vec3 pos = aPosition;
                float swell = sin(pos.x * 1.6 + uTime * 1.9) * cos(pos.z * 1.5 - uTime * 1.6) * 0.055;
                pos.y += swell;
                gl_Position = uMVPMatrix * vec4(pos, 1.0);
                vTexCoord = aTexCoord;
                vShade = aShade;
                vDist = distance(pos, uCameraPos);
                vWorldPos = pos;
            }
        """

        private const val LAVA_FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uTexture;
            uniform vec3 uFogColor;
            uniform float uFogStart;
            uniform float uFogEnd;
            uniform float uTime;
            varying vec2 vTexCoord;
            varying float vShade;
            varying float vDist;
            varying vec3 vWorldPos;
            void main() {
                vec4 tex = texture2D(uTexture, vTexCoord);
                float vein1 = sin(vWorldPos.x * 3.2 + uTime * 2.4 + sin(vWorldPos.z * 2.5 - uTime * 1.8));
                float vein2 = cos(vWorldPos.z * 3.6 - vWorldPos.y * 4.0 - uTime * 3.1);
                float heat = 0.5 + 0.5 * (vein1 * 0.6 + vein2 * 0.4);
                vec3 magmaGlow = mix(vec3(0.95, 0.22, 0.02), vec3(1.0, 0.92, 0.28), smoothstep(0.35, 0.92, heat));
                vec3 lit = mix(tex.rgb, magmaGlow, 0.58) * (0.85 + 0.25 * vShade);
                float fogFactor = clamp((vDist - uFogStart) / max(1.0, (uFogEnd - uFogStart)), 0.0, 1.0);
                gl_FragColor = vec4(mix(lit, uFogColor, fogFactor * 0.75), 1.0);
            }
        """

        private const val ENTITY_VERTEX_SHADER = """
            uniform mat4 uMVPMatrix;
            uniform vec3 uCameraPos;
            attribute vec3 aPosition;
            attribute vec3 aColor;
            attribute float aShade;
            varying vec3 vColor;
            varying float vShade;
            varying float vDist;
            void main() {
                gl_Position = uMVPMatrix * vec4(aPosition, 1.0);
                vColor = aColor;
                vShade = aShade;
                vDist = distance(aPosition, uCameraPos);
            }
        """

        private const val ENTITY_FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec3 uDaylight;
            uniform vec3 uFogColor;
            uniform float uFogStart;
            uniform float uFogEnd;
            varying vec3 vColor;
            varying float vShade;
            varying float vDist;
            void main() {
                vec3 lit = vColor * vShade * uDaylight;
                float fogFactor = clamp((vDist - uFogStart) / max(1.0, (uFogEnd - uFogStart)), 0.0, 1.0);
                gl_FragColor = vec4(mix(lit, uFogColor, fogFactor), 1.0);
            }
        """

        private const val LINE_VERTEX_SHADER = """
            uniform mat4 uMVPMatrix;
            attribute vec3 aPosition;
            void main() {
                gl_Position = uMVPMatrix * vec4(aPosition, 1.0);
            }
        """

        private const val LINE_FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec4 uColor;
            void main() {
                gl_FragColor = uColor;
            }
        """
    }
}
