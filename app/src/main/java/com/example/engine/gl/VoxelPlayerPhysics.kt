package com.example.engine.gl

import com.example.data.model.BikeType
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
    val inWater: Boolean = false,
    val isUnderwater: Boolean = false,
    val inLava: Boolean = false,
    val enteredWater: Boolean = false,
    val enteredLava: Boolean = false,
    val sittingOnWater: Boolean = false,
    val waterSitRipple: Boolean = false,
    val ridingBike: BikeType? = null,
    val bikeEnginePulse: Boolean = false,
    val bikeSpeedRatio: Float = 0f,
    val bikeNitroActive: Boolean = false
)

/**
 * Butter-Smooth First/Third-Person Voxel Player Controller (Minecraft Bedrock Edition Feel):
 * - Smooth horizontal velocity acceleration & deceleration (zero choppy start/stop jerks)
 * - Coyote-time ground forgiveness + Diagonal & Axis Auto-Step (never stalls on 1-block hills, slopes, or corners!)
 * - Anti-sinking ground safety guard so feet ALWAYS stay crisply above solid ground
 * - Swimming, Underwater Oxygen, Lava buoyancy, Creative/JetBoots Flight, Sneak edge-guard & exact DDA Raycast
 */
class VoxelPlayerController(
    startX: Float = 8.5f,
    startY: Float = 20.0f,
    startZ: Float = 8.5f
) {
    @Volatile var x: Float = startX
    @Volatile var y: Float = startY
    @Volatile var z: Float = startZ

    @Volatile var yaw: Float = 0.0f
    @Volatile var pitch: Float = -0.04f

    @Volatile var moveInputX: Float = 0f
    @Volatile var moveInputY: Float = 0f
    @Volatile var jumpRequested: Boolean = false
    @Volatile var jumpHeld: Boolean = false
    @Volatile var flyUpHeld: Boolean = false
    @Volatile var flyDownHeld: Boolean = false
    @Volatile var flying: Boolean = false
    @Volatile var sprinting: Boolean = false
    @Volatile var autoSprinting: Boolean = false
    @Volatile var sneaking: Boolean = false
    @Volatile var speedBoostMultiplier: Float = 1.0f
    @Volatile var jetBootsUnlocked: Boolean = false

    @Volatile var onGround: Boolean = false
        private set
    @Volatile var inWater: Boolean = false
        private set
    @Volatile var isUnderwater: Boolean = false
        private set
    @Volatile var inLava: Boolean = false
        private set
    @Volatile var oxygen: Float = 10f
        private set
    @Volatile var walkBobPhase: Float = 0f
        private set
    @Volatile var moveSpeedFraction: Float = 0f
        private set

    // --- Sit & Float on Water State ---
    @Volatile var sittingOnWater: Boolean = false
        private set
    @Volatile var waterSitBobPhase: Float = 0f
        private set

    // --- 3D Rideable Bike Fleet State ---
    @Volatile var activeBike: BikeType? = null
        private set
    @Volatile var bikeSpeedBlocks: Float = 0f
        private set
    @Volatile var bikeSpeedKmh: Int = 0
        private set
    @Volatile var bikeWheelSpin: Float = 0f
        private set
    @Volatile var bikeLeanRad: Float = 0f
        private set
    @Volatile var bikeNitroTimer: Float = 0f
        private set

    private var vx: Float = 0f
    private var vy: Float = 0f
    private var vz: Float = 0f
    private var coyoteTimer: Float = 0f
    private var stepDistanceAccum: Float = 0f
    private var waterRippleTimer: Float = 0f
    private var bikeAudioTimer: Float = 0f
    private var wasInWater: Boolean = false
    private var wasInLava: Boolean = false

    val isEffectivelySprinting: Boolean
        get() = (sprinting || autoSprinting) && !sneaking

    val eyeY: Float
        get() {
            val baseHeight = when {
                activeBike != null -> BIKE_EYE_HEIGHT
                sittingOnWater -> WATER_SIT_EYE_HEIGHT + sin(waterSitBobPhase * 2.2f) * 0.045f
                sneaking -> CROUCH_EYE_HEIGHT
                else -> EYE_HEIGHT
            }
            val bob = if (activeBike == null && !sittingOnWater && !flying && (onGround || inWater) && moveSpeedFraction > 0.1f) {
                sin(walkBobPhase * 2f) * (if (isEffectivelySprinting) 0.055f else 0.036f) * moveSpeedFraction
            } else 0f
            return y + baseHeight + bob
        }

    fun mountBike(bike: BikeType) {
        sittingOnWater = false
        flying = false
        sneaking = false
        activeBike = bike
        bikeSpeedBlocks = bikeSpeedBlocks.coerceAtLeast(0f)
        bikeNitroTimer = 0f
    }

    fun dismountBike() {
        activeBike = null
        bikeSpeedBlocks = 0f
        bikeSpeedKmh = 0
        bikeLeanRad = 0f
        bikeNitroTimer = 0f
    }

    fun triggerBikeNitro(): Boolean {
        if (activeBike == null) return false
        bikeNitroTimer = 2.6f
        return true
    }

    /**
     * Toggles sitting/floating peacefully right on top of the water surface!
     * Works while already in water OR when standing near a river/lake/ocean shoreline.
     */
    fun toggleWaterSit(world: VoxelChunkWorld): Boolean {
        if (sittingOnWater) {
            sittingOnWater = false
            vy = 3.6f
            return false
        }
        val px = floor(x).toInt()
        val py = floor(y).toInt()
        val pz = floor(z).toInt()

        // Search exact current column first, then adjacent shoreline columns within 2 blocks
        var targetWaterX = x
        var targetWaterZ = z
        var waterTop = world.waterSurfaceYAt(px, pz, py)
        if (waterTop == null) {
            outer@ for (r in 1..2) {
                for (dx in -r..r) {
                    for (dz in -r..r) {
                        val candidate = world.waterSurfaceYAt(px + dx, pz + dz, py)
                        if (candidate != null) {
                            waterTop = candidate
                            targetWaterX = (px + dx) + 0.5f
                            targetWaterZ = (pz + dz) + 0.5f
                            break@outer
                        }
                    }
                }
            }
        }
        val foundSurface = waterTop ?: return false
        dismountBike()
        flying = false
        sneaking = false
        x = targetWaterX
        z = targetWaterZ
        y = foundSurface - 0.12f
        vx = 0f
        vy = 0f
        vz = 0f
        sittingOnWater = true
        waterRippleTimer = 0f
        return true
    }

    fun isWaterAvailableForSit(world: VoxelChunkWorld): Boolean {
        if (sittingOnWater || inWater) return true
        val px = floor(x).toInt()
        val py = floor(y).toInt()
        val pz = floor(z).toInt()
        for (dx in -2..2) {
            for (dz in -2..2) {
                if (world.waterSurfaceYAt(px + dx, pz + dz, py) != null) return true
            }
        }
        return false
    }

    fun rotateLook(deltaYawRad: Float, deltaPitchRad: Float) {
        yaw = (yaw + deltaYawRad) % (2f * Math.PI.toFloat())
        pitch = (pitch + deltaPitchRad).coerceIn(-MAX_PITCH_RAD, MAX_PITCH_RAD)
    }

    fun resetCameraLevel() {
        pitch = -0.03f
    }

    fun collides(world: VoxelChunkWorld, px: Float, py: Float, pz: Float): Boolean =
        collidesAt(world, px, py, pz)

    fun forwardVector(): Triple<Float, Float, Float> {
        val cosPitch = cos(pitch)
        val fx = sin(yaw) * cosPitch
        val fy = sin(pitch)
        val fz = -cos(yaw) * cosPitch
        return Triple(fx, fy, fz)
    }

    fun stepPhysics(world: VoxelChunkWorld, dtRaw: Float, creativeMode: Boolean): PlayerStepEvents {
        val dt = dtRaw.coerceIn(0.001f, 0.045f)

        // 0. Guaranteed Anti-Sinking Ground Safety Guard (never stuck half-way in dirt/stone!)
        if (!flying && collidesAt(world, x, y, z)) {
            var rescued = false
            for (step in 1..24) {
                val liftY = y + step * 0.12f
                if (!collidesAt(world, x, liftY, z)) {
                    y = liftY
                    vy = vy.coerceAtLeast(0f)
                    onGround = true
                    coyoteTimer = COYOTE_DURATION
                    rescued = true
                    break
                }
            }
            if (!rescued) {
                y = world.findSpawnY(floor(x).toInt(), floor(z).toInt())
                vy = 0f
                onGround = true
                coyoteTimer = COYOTE_DURATION
            }
        }

        // Check fluid immersion at feet, torso, and eye level
        val feetBlock = world.getBlock(floor(x).toInt(), floor(y + 0.15f).toInt(), floor(z).toInt())
        val midBlock = world.getBlock(floor(x).toInt(), floor(y + 0.9f).toInt(), floor(z).toInt())
        val eyeBlock = world.getBlock(floor(x).toInt(), floor(y + EYE_HEIGHT - 0.1f).toInt(), floor(z).toInt())

        val inWaterNow = isWaterBlock(feetBlock) || isWaterBlock(midBlock)
        val underwaterNow = isWaterBlock(eyeBlock)
        val inLavaNow = feetBlock == BlockType.LAVA || midBlock == BlockType.LAVA

        val enteredWater = inWaterNow && !wasInWater
        val enteredLava = inLavaNow && !wasInLava
        wasInWater = inWaterNow
        wasInLava = inLavaNow
        inWater = inWaterNow
        isUnderwater = underwaterNow
        inLava = inLavaNow

        if (underwaterNow && !creativeMode) {
            oxygen = (oxygen - dt * 0.72f).coerceAtLeast(0f)
        } else {
            oxygen = (oxygen + dt * 3.2f).coerceAtMost(10f)
        }

        val forwardX = sin(yaw)
        val forwardZ = -cos(yaw)
        val rightX = cos(yaw)
        val rightZ = sin(yaw)

        val rawRight = moveInputX.coerceIn(-1f, 1f)
        val rawFwd = moveInputY.coerceIn(-1f, 1f)
        val mag = hypot(rawRight, rawFwd)
        // Tiny deadzone (0.04f) with smooth ramp for responsive movement
        val effectiveMag = if (mag < 0.04f) 0f else ((mag - 0.04f) / 0.96f).coerceIn(0f, 1f)
        val normRight = if (mag > 0.001f) (rawRight / mag) * effectiveMag else 0f
        val normFwd = if (mag > 0.001f) (rawFwd / mag) * effectiveMag else 0f

        val bike = activeBike
        var bikeEnginePulse = false
        var waterSitRipple = false

        // --- MODE A: SITTING / FLOATING ON WATER ---
        if (sittingOnWater) {
            if (jumpRequested || jumpHeld || inLavaNow) {
                sittingOnWater = false
                vy = 4.2f
                jumpRequested = false
            } else {
                waterSitBobPhase += dt
                val waterSurface = world.waterSurfaceYAt(floor(x).toInt(), floor(z).toInt(), floor(y).toInt())
                if (waterSurface != null) {
                    val targetY = waterSurface - 0.12f + sin(waterSitBobPhase * 2.2f) * 0.035f
                    y += (targetY - y) * (dt * 7.5f).coerceIn(0f, 1f)
                    vy = 0f
                    onGround = false
                    inWater = true
                    isUnderwater = false
                    oxygen = 10f
                } else {
                    // Drifted onto dry shore: smoothly stand back up!
                    sittingOnWater = false
                }
                waterRippleTimer += dt
                if (waterRippleTimer >= 0.72f) {
                    waterRippleTimer = 0f
                    waterSitRipple = true
                }
            }
        }

        // --- MODE B: 3D MOTORCYCLE RIDING PHYSICS ---
        if (bike != null) {
            flying = false
            sneaking = false
            if (bikeNitroTimer > 0f) {
                bikeNitroTimer = (bikeNitroTimer - dt).coerceAtLeast(0f)
            }
            val nitroActive = bikeNitroTimer > 0f

            // Responsive motorcycle steering & visual banking lean!
            val steerStrength = if (abs(bikeSpeedBlocks) > 0.35f) 1.0f else 0.55f
            if (abs(rawRight) > 0.06f) {
                val reverseFlip = if (bikeSpeedBlocks < -0.2f) -1f else 1f
                yaw = (yaw + rawRight * bike.handlingRate * steerStrength * reverseFlip * dt) % (2f * Math.PI.toFloat())
            }
            val targetLean = (-rawRight * 0.36f * (abs(bikeSpeedBlocks) / bike.topSpeedBlocksPerSec).coerceIn(0.15f, 1f))
            bikeLeanRad += (targetLean - bikeLeanRad) * (dt * 8.5f).coerceIn(0f, 1f)

            val bikeFwdX = sin(yaw)
            val bikeFwdZ = -cos(yaw)
            val maxSpd = bike.topSpeedBlocksPerSec * (if (nitroActive) 1.38f else 1f) * (if (inWaterNow) 0.55f else 1f)
            val desiredSpd = when {
                nitroActive && rawFwd >= -0.1f -> maxSpd
                rawFwd > 0.05f -> rawFwd * maxSpd
                rawFwd < -0.05f -> rawFwd * 4.4f
                else -> 0f
            }
            val rate = if (abs(desiredSpd) > abs(bikeSpeedBlocks)) bike.acceleration else 8.5f
            bikeSpeedBlocks += (desiredSpd - bikeSpeedBlocks) * (rate * dt).coerceIn(0f, 1f)
            if (abs(rawFwd) < 0.04f && !nitroActive && abs(bikeSpeedBlocks) < 0.06f) {
                bikeSpeedBlocks = 0f
            }
            vx = bikeFwdX * bikeSpeedBlocks
            vz = bikeFwdZ * bikeSpeedBlocks
            bikeWheelSpin += bikeSpeedBlocks * dt * 3.8f
            val speedRatio = (abs(bikeSpeedBlocks) / bike.topSpeedBlocksPerSec).coerceIn(0f, 1.4f)
            bikeSpeedKmh = (speedRatio * bike.maxDisplayKmh).toInt().coerceAtLeast(0)

            bikeAudioTimer += dt
            val pulseInterval = if (nitroActive) 0.14f else (0.28f - speedRatio * 0.11f).coerceIn(0.15f, 0.30f)
            if (bikeAudioTimer >= pulseInterval && (speedRatio > 0.05f || nitroActive)) {
                bikeAudioTimer = 0f
                bikeEnginePulse = true
            }
        } else {
            autoSprinting = !sneaking && !flying && !sittingOnWater && rawFwd > 0.85f
            val sprintActive = isEffectivelySprinting && normFwd > 0.25f && !inWaterNow && !inLavaNow
            val targetMaxSpeed = when {
                sittingOnWater -> WATER_PADDLE_SPEED
                flying -> FLY_SPEED * (if (sprintActive) 1.35f else 1f)
                inLavaNow -> LAVA_SPEED
                inWaterNow -> SWIM_SPEED * (if (sprintActive) 1.15f else 1f)
                sneaking -> SNEAK_SPEED
                sprintActive -> SPRINT_SPEED
                else -> WALK_SPEED
            } * speedBoostMultiplier

            val desiredVx = (forwardX * normFwd + rightX * normRight) * targetMaxSpeed
            val desiredVz = (forwardZ * normFwd + rightZ * normRight) * targetMaxSpeed

            // Butter-smooth velocity interpolation (crisp start, fluid glide, zero abrupt stopping!)
            val accelRate = when {
                sittingOnWater -> 6.5f
                flying -> 14f
                inWaterNow -> 10f
                onGround || coyoteTimer > 0f -> if (effectiveMag > 0.05f) 20f else 16f
                else -> 9f
            }
            val blend = (accelRate * dt).coerceIn(0f, 1f)
            vx += (desiredVx - vx) * blend
            vz += (desiredVz - vz) * blend
            if (effectiveMag == 0f && abs(vx) < 0.03f && abs(vz) < 0.03f) {
                vx = 0f
                vz = 0f
            }
        }

        val dx = vx * dt
        val dz = vz * dt

        val oldX = x
        val oldZ = z

        // Update coyote time for seamless auto-stepping across uneven blocks
        if (onGround) {
            coyoteTimer = COYOTE_DURATION
        } else {
            coyoteTimer = (coyoteTimer - dt).coerceAtLeast(0f)
        }

        // Smooth Horizontal Movement with Diagonal + Per-Axis 1-Block Auto-Step!
        val canStepUp = !flying && (onGround || coyoteTimer > 0f || inWaterNow) && !sneaking
        val stepY = floor(y + 0.06f) + 1.015f

        val prevX = x
        val prevZ = z

        // Try combined diagonal move first so diagonal walking never snags on voxel corners
        if (abs(dx) > 0.00001f || abs(dz) > 0.00001f) {
            val tryCombinedX = x + dx
            val tryCombinedZ = z + dz
            val combinedClear = !collidesAt(world, tryCombinedX, y, tryCombinedZ)
            val combinedSafeEdge = !sneaking || !onGround || hasSupportBelow(world, tryCombinedX, y, tryCombinedZ)

            if (combinedClear && combinedSafeEdge) {
                x = tryCombinedX
                z = tryCombinedZ
            } else if (canStepUp && (stepY - y) in 0.05f..1.25f && !collidesAt(world, tryCombinedX, stepY, tryCombinedZ)) {
                // Smooth diagonal 1-block climb!
                x = tryCombinedX
                z = tryCombinedZ
                y = stepY
                vy = vy.coerceAtLeast(0f)
                onGround = true
                coyoteTimer = COYOTE_DURATION
            } else {
                // Fallback: independent X and Z sliding + per-axis 1-block auto-step
                if (abs(dx) > 0.00001f) {
                    val nextX = x + dx
                    if (!collidesAt(world, nextX, y, z)) {
                        if (!sneaking || !onGround || hasSupportBelow(world, nextX, y, z)) {
                            x = nextX
                        } else {
                            vx = 0f
                        }
                    } else if (canStepUp && (stepY - y) in 0.05f..1.25f && !collidesAt(world, nextX, stepY, z)) {
                        x = nextX
                        y = stepY
                        vy = vy.coerceAtLeast(0f)
                        onGround = true
                        coyoteTimer = COYOTE_DURATION
                    } else {
                        vx = 0f
                    }
                }

                if (abs(dz) > 0.00001f) {
                    val nextZ = z + dz
                    if (!collidesAt(world, x, y, nextZ)) {
                        if (!sneaking || !onGround || hasSupportBelow(world, x, y, nextZ)) {
                            z = nextZ
                        } else {
                            vz = 0f
                        }
                    } else if (canStepUp && (stepY - y) in 0.05f..1.25f && !collidesAt(world, x, stepY, nextZ)) {
                        z = nextZ
                        y = stepY
                        vy = vy.coerceAtLeast(0f)
                        onGround = true
                        coyoteTimer = COYOTE_DURATION
                    } else {
                        vz = 0f
                    }
                }
            }
        }

        // Prevent unintended stalling if barely moved on an axis while moving cleanly on the other
        if (abs(x - prevX) < 0.0001f && abs(dx) > 0.002f) vx *= 0.2f
        if (abs(z - prevZ) < 0.0001f && abs(dz) > 0.002f) vz *= 0.2f

        val actualDist = hypot(x - oldX, z - oldZ)
        moveSpeedFraction = (actualDist / (WALK_SPEED * dt)).coerceIn(0f, 1.45f)

        var footstep = false
        if (bike == null && !sittingOnWater && !flying && (onGround || inWaterNow) && actualDist > 0.001f) {
            walkBobPhase += actualDist * 2.15f
            stepDistanceAccum += actualDist
            val stepThreshold = if (inWaterNow) 1.35f else if (isEffectivelySprinting) 1.85f else 1.55f
            if (stepDistanceAccum >= stepThreshold) {
                stepDistanceAccum = 0f
                footstep = true
            }
        }

        // Vertical movement (Water Sit vs Flight vs Lava vs Water Swimming vs Ground Gravity)
        val allowFly = (creativeMode || jetBootsUnlocked) && bike == null && !sittingOnWater
        if (sittingOnWater) {
            // Vertical height is smoothly managed on the water surface above
            jumpRequested = false
        } else if (flying && allowFly) {
            var vert = 0f
            if (flyUpHeld || jumpHeld) vert += FLY_VERTICAL_SPEED
            if (flyDownHeld) vert -= FLY_VERTICAL_SPEED
            vy = vert
            val nextY = (y + vy * dt).coerceIn(1.5f, CHUNK_HEIGHT - 2.2f)
            if (!collidesAt(world, x, nextY, z)) {
                y = nextY
            } else {
                vy = 0f
            }
            onGround = false
            jumpRequested = false
        } else if (inWaterNow || inLavaNow) {
            flying = false
            if (jumpHeld || jumpRequested) {
                vy = if (inLavaNow) 3.4f else SWIM_UP_VELOCITY
                jumpRequested = false
            } else {
                vy = (vy - WATER_GRAVITY * dt).coerceAtLeast(if (inLavaNow) -1.2f else -2.2f)
            }
            val nextY = y + vy * dt
            if (!collidesAt(world, x, nextY, z)) {
                y = nextY
                onGround = false
            } else {
                if (vy < 0f) {
                    y = snapAboveFloor(world, x, nextY, z)
                    onGround = true
                    coyoteTimer = COYOTE_DURATION
                }
                vy = 0f
            }
        } else {
            flying = false
            if ((jumpRequested || jumpHeld) && (onGround || coyoteTimer > 0f)) {
                vy = if (bike != null) JUMP_VELOCITY * 0.92f else JUMP_VELOCITY
                onGround = false
                coyoteTimer = 0f
                jumpRequested = false
            } else if (!onGround) {
                jumpRequested = false
            }

            vy = (vy - GRAVITY * dt).coerceAtLeast(-26f)
            val nextY = y + vy * dt
            if (!collidesAt(world, x, nextY, z)) {
                y = nextY
                onGround = false
            } else {
                if (vy < 0f) {
                    y = snapAboveFloor(world, x, nextY, z)
                    onGround = true
                    coyoteTimer = COYOTE_DURATION
                }
                vy = 0f
            }
        }

        if (y < 1.2f) {
            y = world.findSpawnY(floor(x).toInt(), floor(z).toInt())
            vy = 0f
            onGround = true
            coyoteTimer = COYOTE_DURATION
        }

        return PlayerStepEvents(
            footstep = footstep,
            inWater = inWaterNow || sittingOnWater,
            isUnderwater = underwaterNow && !sittingOnWater,
            inLava = inLavaNow,
            enteredWater = enteredWater,
            enteredLava = enteredLava,
            sittingOnWater = sittingOnWater,
            waterSitRipple = waterSitRipple,
            ridingBike = bike,
            bikeEnginePulse = bikeEnginePulse,
            bikeSpeedRatio = if (bike != null) (abs(bikeSpeedBlocks) / bike.topSpeedBlocksPerSec).coerceIn(0f, 1.4f) else 0f,
            bikeNitroActive = bikeNitroTimer > 0f
        )
    }

    private fun isWaterBlock(block: BlockType): Boolean =
        block == BlockType.WATER || block == BlockType.WATERFALL

    private fun hasSupportBelow(world: VoxelChunkWorld, px: Float, py: Float, pz: Float): Boolean {
        val checkY = floor(py - 0.15f).toInt()
        val offsets = floatArrayOf(-HALF_WIDTH * 0.72f, HALF_WIDTH * 0.72f)
        for (ox in offsets) {
            for (oz in offsets) {
                if (world.isSolid(floor(px + ox).toInt(), checkY, floor(pz + oz).toInt())) {
                    return true
                }
            }
        }
        return false
    }

    private fun snapAboveFloor(world: VoxelChunkWorld, px: Float, targetY: Float, pz: Float): Float {
        var snapped = (floor(targetY + 0.001f) + 1.001f).coerceAtLeast(1.001f)
        var guard = 0
        while (collidesAt(world, px, snapped, pz) && guard < 4) {
            snapped += 1.0f
            guard++
        }
        return snapped
    }

    fun overlapsBlock(bx: Int, by: Int, bz: Int): Boolean {
        val minX = x - HALF_WIDTH
        val maxX = x + HALF_WIDTH
        val minY = y
        val maxY = y + PLAYER_HEIGHT
        val minZ = z - HALF_WIDTH
        val maxZ = z + HALF_WIDTH

        return maxX > bx && minX < bx + 1f &&
            maxY > by && minY < by + 1f &&
            maxZ > bz && minZ < bz + 1f
    }

    private fun collidesAt(world: VoxelChunkWorld, px: Float, py: Float, pz: Float): Boolean {
        val minX = floor(px - HALF_WIDTH).toInt()
        val maxX = floor(px + HALF_WIDTH).toInt()
        val minY = floor(py + 0.001f).toInt()
        val maxY = floor(py + PLAYER_HEIGHT - 0.02f).toInt()
        val minZ = floor(pz - HALF_WIDTH).toInt()
        val maxZ = floor(pz + HALF_WIDTH).toInt()

        for (bx in minX..maxX) {
            for (by in minY..maxY) {
                for (bz in minZ..maxZ) {
                    if (world.isSolid(bx, by, bz)) return true
                }
            }
        }
        return false
    }

    fun raycastTarget(world: VoxelChunkWorld, maxDistance: Float = 6.5f): VoxelRaycastHit? {
        val (dx, dy, dz) = forwardVector()
        val ox = x
        val oy = eyeY
        val oz = z

        var bx = floor(ox).toInt()
        var by = floor(oy).toInt()
        var bz = floor(oz).toInt()

        val stepX = if (dx >= 0f) 1 else -1
        val stepY = if (dy >= 0f) 1 else -1
        val stepZ = if (dz >= 0f) 1 else -1

        val tDeltaX = if (abs(dx) < 1e-6f) Float.MAX_VALUE else abs(1f / dx)
        val tDeltaY = if (abs(dy) < 1e-6f) Float.MAX_VALUE else abs(1f / dy)
        val tDeltaZ = if (abs(dz) < 1e-6f) Float.MAX_VALUE else abs(1f / dz)

        var tMaxX = if (abs(dx) < 1e-6f) Float.MAX_VALUE else {
            val boundary = if (dx > 0f) bx + 1f else bx.toFloat()
            (boundary - ox) / dx
        }
        var tMaxY = if (abs(dy) < 1e-6f) Float.MAX_VALUE else {
            val boundary = if (dy > 0f) by + 1f else by.toFloat()
            (boundary - oy) / dy
        }
        var tMaxZ = if (abs(dz) < 1e-6f) Float.MAX_VALUE else {
            val boundary = if (dz > 0f) bz + 1f else bz.toFloat()
            (boundary - oz) / dz
        }

        var nx = 0
        var ny = 0
        var nz = 0
        var dist = 0f

        repeat(48) {
            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    bx += stepX
                    dist = tMaxX
                    tMaxX += tDeltaX
                    nx = -stepX; ny = 0; nz = 0
                } else {
                    bz += stepZ
                    dist = tMaxZ
                    tMaxZ += tDeltaZ
                    nx = 0; ny = 0; nz = -stepZ
                }
            } else {
                if (tMaxY < tMaxZ) {
                    by += stepY
                    dist = tMaxY
                    tMaxY += tDeltaY
                    nx = 0; ny = -stepY; nz = 0
                } else {
                    bz += stepZ
                    dist = tMaxZ
                    tMaxZ += tDeltaZ
                    nx = 0; ny = 0; nz = -stepZ
                }
            }

            if (dist > maxDistance) return null
            if (by in 0 until CHUNK_HEIGHT) {
                val block = world.getBlock(bx, by, bz)
                if (block != BlockType.AIR && block != BlockType.WATER && block != BlockType.WATERFALL && block != BlockType.LAVA) {
                    return VoxelRaycastHit(bx, by, bz, nx, ny, nz, block)
                }
            }
        }
        return null
    }

    companion object {
        const val HALF_WIDTH = 0.25f
        const val PLAYER_HEIGHT = 1.72f
        const val EYE_HEIGHT = 1.56f
        const val CROUCH_EYE_HEIGHT = 1.24f
        const val WATER_SIT_EYE_HEIGHT = 1.04f
        const val BIKE_EYE_HEIGHT = 1.78f
        const val WALK_SPEED = 5.4f
        const val SPRINT_SPEED = 7.9f
        const val SNEAK_SPEED = 2.6f
        const val SWIM_SPEED = 3.8f
        const val WATER_PADDLE_SPEED = 2.4f
        const val LAVA_SPEED = 2.0f
        const val FLY_SPEED = 9.4f
        const val FLY_VERTICAL_SPEED = 6.2f
        const val GRAVITY = 22.0f
        const val WATER_GRAVITY = 4.6f
        const val JUMP_VELOCITY = 8.4f
        const val SWIM_UP_VELOCITY = 4.5f
        const val COYOTE_DURATION = 0.16f
        const val MAX_PITCH_RAD = 1.553343f // ~89 degrees
    }
}
