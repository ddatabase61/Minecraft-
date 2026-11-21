package com.example.engine.gl

import com.example.data.model.BlockType
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

data class VoxelRaycastHit(
    val x: Int,
    val y: Int,
    val z: Int,
    val nx: Int,
    val ny: Int,
    val nz: Int,
    val block: BlockType
)

data class PlayerStepEvents(
    val footstep: Boolean = false,
    val enteredWater: Boolean = false,
    val inWater: Boolean = false,
    val isUnderwater: Boolean = false
)

/**
 * Authentic Minecraft PE first-person & third-person player physics:
 * - Walk, Sprint, Sneak/Crouch (with anti-fall edge guard!)
 * - Water buoyancy, swimming, underwater diving & oxygen meter
 * - Creative flying, jumping, AABB collision, and 3D DDA voxel raycasting
 * - Walk bobbing & animation stride phase
 */
class VoxelPlayerController(
    startX: Float,
    startY: Float,
    startZ: Float
) {
    @Volatile var x: Float = startX
    @Volatile var y: Float = startY
    @Volatile var z: Float = startZ
    @Volatile var yaw: Float = 0.6f
    @Volatile var pitch: Float = -0.18f

    private var vy: Float = 0f
    @Volatile var onGround: Boolean = false
    @Volatile var flying: Boolean = false
    @Volatile var sprinting: Boolean = false
    @Volatile var autoSprinting: Boolean = false
    @Volatile var sneaking: Boolean = false
    @Volatile var inWater: Boolean = false
    @Volatile var isUnderwater: Boolean = false
    @Volatile var oxygen: Float = 10f // 0..10 bubbles
    @Volatile var walkBobPhase: Float = 0f
    @Volatile var moveSpeedFraction: Float = 0f

    val isEffectivelySprinting: Boolean
        get() = (sprinting || autoSprinting) && !sneaking

    // Live multi-touch input state written from Compose controls (thread-safe volatile)
    @Volatile var moveInputX: Float = 0f // -1 left .. +1 right
    @Volatile var moveInputY: Float = 0f // -1 back .. +1 forward
    @Volatile var jumpRequested: Boolean = false
    @Volatile var jumpHeld: Boolean = false
    @Volatile var flyUpHeld: Boolean = false
    @Volatile var flyDownHeld: Boolean = false

    private var stepAccumulator: Float = 0f
    private var wasInWater: Boolean = false
    private var currentEyeOffset: Float = EYE_HEIGHT

    val eyeY: Float
        get() {
            val bob = if (onGround && !flying && moveSpeedFraction > 0.1f) {
                sin(walkBobPhase * 2f) * 0.055f * moveSpeedFraction
            } else {
                0f
            }
            return y + currentEyeOffset + bob
        }

    fun rotateLook(deltaYawRad: Float, deltaPitchRad: Float) {
        yaw += deltaYawRad
        // Clamp pitch strictly between -89° and +89° (-1.553343 rad .. +1.553343 rad)
        pitch = (pitch + deltaPitchRad).coerceIn(-MAX_PITCH_RAD, MAX_PITCH_RAD)
    }

    fun forwardVector(): Triple<Float, Float, Float> {
        val cosP = cos(pitch)
        val fx = sin(yaw) * cosP
        val fy = sin(pitch)
        val fz = -cos(yaw) * cosP
        return Triple(fx, fy, fz)
    }

    fun stepPhysics(world: VoxelChunkWorld, dtRaw: Float, creativeMode: Boolean): PlayerStepEvents {
        val dt = dtRaw.coerceIn(0.001f, 0.05f)
        if (!creativeMode) flying = false

        // Smooth camera height transition for Sneak / Crouch
        val targetEye = if (sneaking) EYE_HEIGHT_SNEAK else EYE_HEIGHT
        currentEyeOffset += (targetEye - currentEyeOffset) * (dt * 14f).coerceIn(0f, 1f)

        val feetBlock = world.getBlock(floor(x).toInt(), floor(y + 0.25f).toInt(), floor(z).toInt())
        val eyeBlock = world.getBlock(floor(x).toInt(), floor(y + currentEyeOffset).toInt(), floor(z).toInt())
        inWater = (feetBlock == BlockType.WATER || feetBlock == BlockType.WATERFALL ||
            eyeBlock == BlockType.WATER || eyeBlock == BlockType.WATERFALL)
        isUnderwater = (eyeBlock == BlockType.WATER || eyeBlock == BlockType.WATERFALL)

        val enteredWater = inWater && !wasInWater
        wasInWater = inWater

        // Oxygen bubble regeneration / depletion while underwater
        if (isUnderwater && !creativeMode) {
            oxygen = (oxygen - dt * 0.55f).coerceAtLeast(0f)
        } else {
            oxygen = (oxygen + dt * 3.0f).coerceAtMost(10f)
        }

        // Auto-sprint activates automatically when joystick pushed >85% forward (unless sneaking)
        if (moveInputY > 0.85f && !sneaking) {
            autoSprinting = true
        } else if (moveInputY < 0.78f || sneaking) {
            autoSprinting = false
        }

        val effectiveSprint = isEffectivelySprinting
        val baseSpeed = when {
            flying -> if (effectiveSprint) FLY_SPEED * SPRINT_MULTIPLIER else FLY_SPEED
            inWater -> if (effectiveSprint) SWIM_SPEED * 1.25f else SWIM_SPEED
            sneaking -> SNEAK_SPEED
            effectiveSprint -> WALK_SPEED * SPRINT_MULTIPLIER
            else -> WALK_SPEED
        }

        val forwardX = sin(yaw)
        val forwardZ = -cos(yaw)
        val rightX = cos(yaw)
        val rightZ = sin(yaw)

        val inputMag = hypot(moveInputX, moveInputY).coerceAtMost(1f)
        moveSpeedFraction = inputMag

        val wishX = (forwardX * moveInputY + rightX * moveInputX) * baseSpeed
        val wishZ = (forwardZ * moveInputY + rightZ * moveInputX) * baseSpeed

        if (flying) {
            vy = when {
                flyUpHeld || jumpHeld -> FLY_VERTICAL_SPEED
                flyDownHeld -> -FLY_VERTICAL_SPEED
                else -> 0f
            }
        } else if (inWater) {
            // Smooth Minecraft swimming & buoyancy physics!
            vy = when {
                jumpHeld || jumpRequested -> SWIM_UP_SPEED
                sneaking -> -SWIM_UP_SPEED * 0.85f
                else -> (vy - WATER_GRAVITY * dt).coerceIn(-2.6f, 2.6f) * 0.90f
            }
            jumpRequested = false
        } else {
            if ((jumpRequested || jumpHeld) && onGround) {
                vy = JUMP_VELOCITY
                onGround = false
            }
            jumpRequested = false
            vy = (vy - GRAVITY * dt).coerceAtLeast(-28f)
        }

        val prevX = x
        val prevZ = z

        // Resolve X axis (with Sneak edge-guard so player cannot fall off cliffs while sneaking!)
        val nextX = x + wishX * dt
        if (!collides(world, nextX, y, z)) {
            if (!sneaking || !onGround || hasSupportBelow(world, nextX, y, z)) {
                x = nextX
            }
        }

        // Resolve Z axis
        val nextZ = z + wishZ * dt
        if (!collides(world, x, y, nextZ)) {
            if (!sneaking || !onGround || hasSupportBelow(world, x, y, nextZ)) {
                z = nextZ
            }
        }

        // Resolve Y axis
        val nextY = y + vy * dt
        if (!collides(world, x, nextY, z)) {
            y = nextY
            onGround = false
        } else {
            if (vy < 0f) {
                y = floor(y).coerceAtLeast(1f)
                onGround = true
            }
            vy = 0f
        }

        // Safety floor rescue
        if (y < 1.0f) {
            y = world.findSpawnY(floor(x).toInt(), floor(z).toInt())
            vy = 0f
        }

        // Walk bobbing & footstep cadence
        val movedDist = hypot(x - prevX, z - prevZ)
        var footstepTriggered = false
        if ((onGround || inWater) && !flying && movedDist > 0.002f) {
            walkBobPhase += movedDist * 3.1f
            stepAccumulator += movedDist
            val strideLen = if (sprinting) 1.85f else 1.55f
            if (stepAccumulator >= strideLen) {
                stepAccumulator = 0f
                footstepTriggered = true
            }
        }

        return PlayerStepEvents(
            footstep = footstepTriggered,
            enteredWater = enteredWater,
            inWater = inWater,
            isUnderwater = isUnderwater
        )
    }

    private fun hasSupportBelow(world: VoxelChunkWorld, px: Float, py: Float, pz: Float): Boolean {
        val checkY = floor(py - 0.15f).toInt()
        return world.isSolid(floor(px).toInt(), checkY, floor(pz).toInt())
    }

    fun overlapsBlock(bx: Int, by: Int, bz: Int): Boolean {
        val pMinX = x - HALF_WIDTH
        val pMaxX = x + HALF_WIDTH
        val pMinY = y
        val pMaxY = y + PLAYER_HEIGHT
        val pMinZ = z - HALF_WIDTH
        val pMaxZ = z + HALF_WIDTH

        return pMaxX > bx && pMinX < bx + 1f &&
            pMaxY > by && pMinY < by + 1f &&
            pMaxZ > bz && pMinZ < bz + 1f
    }

    private fun collides(world: VoxelChunkWorld, px: Float, py: Float, pz: Float): Boolean {
        val minX = floor(px - HALF_WIDTH).toInt()
        val maxX = floor(px + HALF_WIDTH).toInt()
        val minY = floor(py + 0.01f).toInt()
        val maxY = floor(py + PLAYER_HEIGHT).toInt()
        val minZ = floor(pz - HALF_WIDTH).toInt()
        val maxZ = floor(pz + HALF_WIDTH).toInt()

        for (by in minY..maxY) {
            for (bz in minZ..maxZ) {
                for (bx in minX..maxX) {
                    if (world.isSolid(bx, by, bz)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    fun raycastTarget(world: VoxelChunkWorld, maxDistance: Float = 6.5f): VoxelRaycastHit? {
        val ox = x
        val oy = eyeY
        val oz = z
        val (dx, dy, dz) = forwardVector()

        var vx = floor(ox).toInt()
        var vyCell = floor(oy).toInt()
        var vz = floor(oz).toInt()

        val stepX = if (dx >= 0f) 1 else -1
        val stepY = if (dy >= 0f) 1 else -1
        val stepZ = if (dz >= 0f) 1 else -1

        val tDeltaX = if (abs(dx) < 1e-6f) Float.POSITIVE_INFINITY else abs(1f / dx)
        val tDeltaY = if (abs(dy) < 1e-6f) Float.POSITIVE_INFINITY else abs(1f / dy)
        val tDeltaZ = if (abs(dz) < 1e-6f) Float.POSITIVE_INFINITY else abs(1f / dz)

        var tMaxX = if (dx >= 0f) (floor(ox) + 1f - ox) * tDeltaX else (ox - floor(ox)) * tDeltaX
        var tMaxY = if (dy >= 0f) (floor(oy) + 1f - oy) * tDeltaY else (oy - floor(oy)) * tDeltaY
        var tMaxZ = if (dz >= 0f) (floor(oz) + 1f - oz) * tDeltaZ else (oz - floor(oz)) * tDeltaZ

        var nx = 0
        var ny = 0
        var nz = 0
        var dist = 0f

        for (step in 0 until 52) {
            if (dist > maxDistance) break
            val b = world.getBlock(vx, vyCell, vz)
            if (b != BlockType.AIR && b != BlockType.WATER && b != BlockType.WATERFALL && b != BlockType.LAVA) {
                return VoxelRaycastHit(vx, vyCell, vz, nx, ny, nz, b)
            }

            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                dist = tMaxX
                tMaxX += tDeltaX
                vx += stepX
                nx = -stepX; ny = 0; nz = 0
            } else if (tMaxY < tMaxZ) {
                dist = tMaxY
                tMaxY += tDeltaY
                vyCell += stepY
                nx = 0; ny = -stepY; nz = 0
            } else {
                dist = tMaxZ
                tMaxZ += tDeltaZ
                vz += stepZ
                nx = 0; ny = 0; nz = -stepZ
            }
        }
        return null
    }

    companion object {
        private const val HALF_WIDTH = 0.28f
        private const val PLAYER_HEIGHT = 1.72f
        private const val EYE_HEIGHT = 1.54f
        private const val EYE_HEIGHT_SNEAK = 1.24f
        private const val WALK_SPEED = 4.9f
        private const val SPRINT_MULTIPLIER = 1.35f
        private const val SNEAK_SPEED = 2.3f
        private const val SWIM_SPEED = 3.4f
        private const val SWIM_UP_SPEED = 3.9f
        private const val WATER_GRAVITY = 4.6f
        private const val FLY_SPEED = 8.8f
        private const val FLY_VERTICAL_SPEED = 5.8f
        private const val JUMP_VELOCITY = 8.4f
        private const val GRAVITY = 24.0f
        const val MAX_PITCH_RAD = 1.553343f // 89 degrees
    }
}
