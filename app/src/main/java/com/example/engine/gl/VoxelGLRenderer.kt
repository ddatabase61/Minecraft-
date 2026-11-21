package com.example.engine.gl

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
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
    val y: Int = 12,
    val z: Int = 8,
    val yaw: Float = 0f,
    val targetBlock: BlockType? = null,
    val flying: Boolean = false,
    val inWater: Boolean = false,
    val isUnderwater: Boolean = false,
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
    val minimapColors: IntArray = IntArray(0)
)

/**
 * OpenGL ES 2.0 3D Voxel Renderer featuring:
 * - Animated Water & Waterfall Shaders (surface waves, flowing foam, underwater caustic tint)
 * - 1st-Person & 3rd-Person Camera Perspective (with animated 3D Player Avatar)
 * - Dynamic Rain Weather streaks
 * - Articulated Animals, Flying Birds, Heroes, AI-Tracking Zombies, Gun Projectiles & Debris Particles
 * - Dynamic Day/Night Sky, Sun Halo, Moon, Fog & Exact DDA Target Wireframe
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
    @Volatile var renderDistance: Int = 2
    @Volatile var autoTimeCycle: Boolean = true
    @Volatile var timeOfDay: Float = 0.22f
    @Volatile var thirdPersonCamera: Boolean = false
    @Volatile var rainEnabled: Boolean = false
    @Volatile var armSwingProgress: Float = 0f
    @Volatile var currentHit: VoxelRaycastHit? = null
        private set

    private var voxelProgram = 0
    private var waterProgram = 0
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
    private var currentFov = 70f
    private var elapsedTime = 0f
    private var bubbleTimer = 0f
    private var lastFrameNs = 0L
    private var fpsCounter = 0
    private var fpsTimerMs = 0L
    private var displayedFps = 60
    private var lastTelemetryMs = 0L
    private var lastMinimapMs = 0L
    private var cachedMinimapColors = IntArray(0)

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
        Matrix.perspectiveM(projMatrix, 0, currentFov, viewportAspect, 0.1f, 165f)
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
            if (autoTimeCycle) {
                timeOfDay = (timeOfDay + dt * 0.0038f) % 1.0f
            }
            val stepEvents = player.stepPhysics(world, dt, creativeMode)
            if (stepEvents.enteredWater) {
                soundEngine.playWaterSplash()
                entityManager.spawnWaterSplashParticles(player.x, player.y, player.z)
            } else if (stepEvents.footstep) {
                soundEngine.playFootstep(stepEvents.inWater)
                if (stepEvents.inWater) {
                    entityManager.spawnWaterSplashParticles(player.x, player.y + 0.1f, player.z)
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
                soundEngine.playZombieGroan()
            }
        }

        val sky = computeDayNightPalette(timeOfDay, player.isUnderwater, rainEnabled)
        GLES20.glClearColor(sky.skyR, sky.skyG, sky.skyB, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val (fx, fy, fz) = player.forwardVector()
        val baseEyeX = player.x
        val baseEyeY = player.eyeY
        val baseEyeZ = player.z

        // 1st-Person or 3rd-Person Chase Camera
        var camX = baseEyeX
        var camY = baseEyeY
        var camZ = baseEyeZ
        var lookX = baseEyeX + fx
        var lookY = baseEyeY + fy
        var lookZ = baseEyeZ + fz

        if (thirdPersonCamera) {
            val backDist = 4.0f
            var safeDist = 0.5f
            for (step in 1..8) {
                val d = (step / 8f) * backDist
                val tx = baseEyeX - fx * d
                val ty = baseEyeY + 0.7f - fy * d * 0.6f
                val tz = baseEyeZ - fz * d
                if (world.isSolid(floor(tx).toInt(), floor(ty).toInt(), floor(tz).toInt())) break
                safeDist = d
            }
            camX = baseEyeX - fx * safeDist
            camY = baseEyeY + 0.7f - fy * safeDist * 0.6f
            camZ = baseEyeZ - fz * safeDist
            lookX = baseEyeX + fx * 2.5f
            lookY = baseEyeY + fy * 1.5f
            lookZ = baseEyeZ + fz * 2.5f
        }

        // Smooth Bedrock-style camera FOV widening while sprinting
        val targetFov = if (player.isEffectivelySprinting && player.moveSpeedFraction > 0.25f) 79f else 70f
        if (kotlin.math.abs(currentFov - targetFov) > 0.04f) {
            currentFov += (targetFov - currentFov) * (dt * 9.5f).coerceIn(0f, 1f)
            Matrix.perspectiveM(projMatrix, 0, currentFov, viewportAspect, 0.1f, 165f)
        }

        Matrix.setLookAtM(viewMatrix, 0, camX, camY, camZ, lookX, lookY, lookZ, 0f, 1f, 0f)
        Matrix.multiplyMM(vpMatrix, 0, projMatrix, 0, viewMatrix, 0)

        // Pass 0: Sun Halo & Moon (hidden when deeply underwater)
        if (!player.isUnderwater) {
            drawCelestialBodies(camX, camY, camZ, timeOfDay)
        }

        val meshes = world.prepareVisibleMeshes(
            playerX = player.x,
            playerZ = player.z,
            renderDistance = renderDistance,
            maxRebuildsPerFrame = 2
        )

        val fogEnd = if (player.isUnderwater) 18f else (renderDistance * CHUNK_SIZE + 8).toFloat()
        val fogStart = if (player.isUnderwater) 2.5f else (fogEnd * 0.52f).coerceAtLeast(14f)

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

        // Pass 2: Flowers, Tall Grass, Glass & Ice (non-water translucent / alpha-cutout)
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

        // Pass 3: 3D Articulated Entities, 3rd-Person Avatar, Gun Bullets & Debris/Bubble Particles
        drawLivingEntities(camX, camY, camZ, sky, fogStart, fogEnd)

        // Pass 4: Animated Ocean, Rivers & Cascading Waterfalls Shader!
        drawAnimatedWater(meshes, camX, camY, camZ, sky, fogStart, fogEnd)

        // Pass 5: Dynamic 3D Rain Weather (when enabled)
        if (rainEnabled && !player.isUnderwater) {
            drawRainStreaks(player.x, player.y, player.z)
        }

        // Pass 6: Exact Raycast Target Block Wireframe
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
        if (nowMs - lastTelemetryMs >= 120L) {
            lastTelemetryMs = nowMs
            val px = floor(player.x).toInt()
            val py = floor(player.y).toInt()
            val pz = floor(player.z).toInt()
            soundEngine.isNightTime = isNight
            soundEngine.isUnderwater = player.isUnderwater

            if (nowMs - lastMinimapMs >= 750L || cachedMinimapColors.isEmpty()) {
                lastMinimapMs = nowMs
                cachedMinimapColors = world.sampleMinimapColors(px, pz, gridSize = 21, step = 2)
                soundEngine.isNearWater = world.isNearWater(px, pz)
            }

            val (totalEnt, activeZom) = entityManager.activeCounts()
            onTelemetry(
                HudTelemetry(
                    fps = displayedFps,
                    x = px,
                    y = py,
                    z = pz,
                    yaw = player.yaw,
                    targetBlock = hit?.block,
                    flying = player.flying,
                    inWater = player.inWater,
                    isUnderwater = player.isUnderwater,
                    oxygen = player.oxygen.toInt().coerceIn(0, 10),
                    sprinting = player.isEffectivelySprinting,
                    sneaking = player.sneaking,
                    timeOfDay = timeOfDay,
                    playerHealth = entityManager.playerHealth,
                    zombiesDefeated = entityManager.zombiesDefeated,
                    activeZombies = activeZom,
                    totalEntities = totalEnt,
                    connectedAnimals = entityManager.connectedAnimalCount(),
                    minimapBlips = entityManager.snapshotMinimapBlips(player.x, player.z, maxRange = 22f),
                    minimapColors = cachedMinimapColors
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
        val dist = 85f
        val sunX = eyeX + cos(angle) * dist
        val sunY = eyeY + sin(angle) * dist
        val sunZ = eyeZ - 18f

        val moonX = eyeX - cos(angle) * dist
        val moonY = eyeY - sin(angle) * dist
        val moonZ = eyeZ - 18f

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

        // Animated Water & Waterfall Vertex + Fragment Shaders (surface waves + shimmering caustics!)
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
                float wave = sin(pos.x * 2.1 + uTime * 3.1) * cos(pos.z * 1.9 + uTime * 2.6) * 0.06;
                pos.y += wave;
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
                float shimmer = 0.90 + 0.14 * sin(vWorldPos.x * 3.5 + vWorldPos.y * 5.0 - uTime * 5.2 + vWorldPos.z * 3.2);
                float foam = smoothstep(0.92, 1.04, shimmer) * 0.25;
                vec3 lit = (tex.rgb * shimmer + vec3(foam)) * vShade * uDaylight;
                float fogFactor = clamp((vDist - uFogStart) / max(1.0, (uFogEnd - uFogStart)), 0.0, 1.0);
                gl_FragColor = vec4(mix(lit, uFogColor, fogFactor), 0.80);
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
