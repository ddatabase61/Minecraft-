package com.example.engine.gl

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

enum class EntityCategory(val label: String) {
    HEROES("Heroes & Characters"),
    ANIMALS("Animals & Birds"),
    ZOMBIES("Zombies")
}

enum class EntityKind(
    val displayName: String,
    val badgeColorHex: Long,
    val category: EntityCategory,
    val isHostile: Boolean = false,
    val isFlying: Boolean = false,
    val isCompanion: Boolean = false,
    val soundStyle: String = "CATTLE",
    val maxHp: Int = 60,
    val speed: Float = 1.9f,
    val radius: Float = 0.45f,
    val height: Float = 1.65f
) {
    // --- Heroes, Mythic & Cartoon Characters ---
    KRISHNA("Shri Krishna", 0xFF29B6F6, EntityCategory.HEROES, isCompanion = true, soundStyle = "HERO", maxHp = 120, speed = 2.6f, height = 1.85f),
    RAM("Shri Ram", 0xFF34D399, EntityCategory.HEROES, isCompanion = true, soundStyle = "HERO", maxHp = 120, speed = 2.6f, height = 1.85f),
    HANUMAN("Shri Hanuman", 0xFFFF7043, EntityCategory.HEROES, isCompanion = true, soundStyle = "HERO", maxHp = 150, speed = 3.0f, height = 1.9f),
    BHEEM("Bheem", 0xFFFF9800, EntityCategory.HEROES, isCompanion = true, soundStyle = "HERO", maxHp = 120, speed = 2.7f, height = 1.75f),
    RAJU("Raju", 0xFFFFCA28, EntityCategory.HEROES, isCompanion = true, soundStyle = "HERO", maxHp = 90, speed = 2.8f, height = 1.45f),
    VILLAGER("Man / Villager", 0xFF8D6E63, EntityCategory.HEROES, isCompanion = false, soundStyle = "HERO", maxHp = 80, speed = 2.0f, height = 1.8f),

    // --- Animals & Birds ---
    COW("Cow (गौ)", 0xFFF5F5F5, EntityCategory.ANIMALS, soundStyle = "CATTLE", maxHp = 60, speed = 1.5f, radius = 0.55f, height = 1.35f),
    BUFFALO("Buffalo (भैंस)", 0xFF37474F, EntityCategory.ANIMALS, soundStyle = "CATTLE", maxHp = 80, speed = 1.4f, radius = 0.62f, height = 1.45f),
    BULL("Bull (बैल)", 0xFF795548, EntityCategory.ANIMALS, soundStyle = "CATTLE", maxHp = 85, speed = 1.9f, radius = 0.60f, height = 1.45f),
    HORSE("Horse (घोड़ा)", 0xFFA16B47, EntityCategory.ANIMALS, soundStyle = "CATTLE", maxHp = 80, speed = 3.1f, radius = 0.58f, height = 1.75f),
    ELEPHANT("Elephant (हाथी)", 0xFF78909C, EntityCategory.ANIMALS, soundStyle = "ELEPHANT", maxHp = 180, speed = 1.3f, radius = 0.95f, height = 2.5f),
    DOG("Dog (कुत्ता)", 0xFFD4A373, EntityCategory.ANIMALS, isCompanion = true, soundStyle = "DOG", maxHp = 70, speed = 3.4f, radius = 0.36f, height = 0.85f),
    CAT("Cat (बिल्ली)", 0xFFFFB74D, EntityCategory.ANIMALS, isCompanion = true, soundStyle = "CAT", maxHp = 50, speed = 2.7f, radius = 0.30f, height = 0.65f),
    BIRD_SPARROW("Sparrow (चिड़िया)", 0xFFB5835A, EntityCategory.ANIMALS, isFlying = true, soundStyle = "BIRD", maxHp = 20, speed = 4.6f, radius = 0.25f, height = 0.4f),
    BIRD_MYNA("Myna (मैना)", 0xFF424242, EntityCategory.ANIMALS, isFlying = true, soundStyle = "BIRD", maxHp = 24, speed = 5.0f, radius = 0.26f, height = 0.42f),

    // --- Hostile AI-Tracking Zombies ---
    ZOMBIE("Zombie", 0xFF4CAF50, EntityCategory.ZOMBIES, isHostile = true, soundStyle = "ZOMBIE", maxHp = 60, speed = 2.45f, radius = 0.45f, height = 1.85f)
}

data class VoxelEntity(
    val id: Int,
    val kind: EntityKind,
    var x: Float,
    var y: Float,
    var z: Float,
    var vy: Float = 0f,
    var yaw: Float = 0f,
    var hp: Int = kind.maxHp,
    var animPhase: Float = 0f,
    var aiTimer: Float = 0f,
    var targetYaw: Float = 0f,
    var hitFlash: Float = 0f,
    var attackCooldown: Float = 0f,
    var connected: Boolean = false
)

data class BulletProjectile(
    var x: Float,
    var y: Float,
    var z: Float,
    val vx: Float,
    val vy: Float,
    val vz: Float,
    var life: Float = 1.25f
)

data class VoxelParticle(
    var x: Float,
    var y: Float,
    var z: Float,
    var vx: Float,
    var vy: Float,
    var vz: Float,
    val r: Float,
    val g: Float,
    val b: Float,
    var size: Float,
    var life: Float
)

data class MinimapBlip(
    val dx: Float,
    val dz: Float,
    val kind: EntityKind
)

data class CombatEvents(
    val zombieHits: Int = 0,
    val zombieKills: Int = 0,
    val playerDamaged: Boolean = false
)

/**
 * Manages all living 3D entities (Animals, Birds, Heroes/Mythic Characters, AI-tracking Zombies),
 * gun projectiles, impact/waterfall particles, and builds a single zero-GC vertex buffer per frame.
 */
class VoxelEntityManager(seed: Long) {
    private val rng = Random(seed + 5511L)
    private val entities = ArrayList<VoxelEntity>(64)
    private val bullets = ArrayList<BulletProjectile>(32)
    private val particles = ArrayList<VoxelParticle>(128)
    private val lock = Any()
    private var nextId = 1

    @Volatile var zombiesDefeated: Int = 0
        private set
    @Volatile var playerHealth: Int = 10
        private set
    private var healthRegenTimer = 0f
    private var zombieAutoSpawnTimer = 0f

    // Pre-allocated scratch vertex array & native FloatBuffer for smooth 60 FPS rendering
    // Vertex format: [x, y, z, r, g, b, shade] (7 floats per vertex)
    private var vertArray = FloatArray(MAX_FLOATS)
    private var vertFloatCount = 0
    private val nativeBuffer: FloatBuffer = ByteBuffer.allocateDirect(MAX_FLOATS * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    fun populateInitialEcosystem(world: VoxelChunkWorld, spawnX: Float, spawnZ: Float) {
        synchronized(lock) {
            entities.clear()
            bullets.clear()
            particles.clear()
            playerHealth = 10

            // Spawn iconic Heroes & Cartoon friends near the sunlit meadow
            val roster = listOf(
                EntityKind.KRISHNA to (4f to -5f),
                EntityKind.RAM to (-4f to -6f),
                EntityKind.HANUMAN to (6f to -3f),
                EntityKind.BHEEM to (-5f to 4f),
                EntityKind.RAJU to (-3f to 5f),
                EntityKind.VILLAGER to (5f to 5f),
                // Animals
                EntityKind.COW to (9f to -4f),
                EntityKind.BUFFALO to (11f to 2f),
                EntityKind.BULL to (-9f to -3f),
                EntityKind.HORSE to (-8f to 7f),
                EntityKind.ELEPHANT to (13f to -9f),
                EntityKind.DOG to (2.5f to 3f),
                EntityKind.CAT to (-2.5f to 3f),
                // Flying Birds ("चिड़िया मैना")
                EntityKind.BIRD_SPARROW to (4f to -8f),
                EntityKind.BIRD_SPARROW to (-7f to -5f),
                EntityKind.BIRD_MYNA to (8f to 6f),
                EntityKind.BIRD_MYNA to (-5f to 9f),
                // Roaming AI-tracking Zombies further out so player can hunt them with the gun
                EntityKind.ZOMBIE to (16f to -14f),
                EntityKind.ZOMBIE to (-16f to -15f),
                EntityKind.ZOMBIE to (14f to 16f)
            )

            roster.forEach { (kind, offset) ->
                val ex = spawnX + offset.first
                val ez = spawnZ + offset.second
                val groundY = world.findSpawnY(floor(ex).toInt(), floor(ez).toInt())
                val ey = if (kind.isFlying) groundY + 5.5f + rng.nextFloat() * 3.5f else groundY
                entities.add(
                    VoxelEntity(
                        id = nextId++,
                        kind = kind,
                        x = ex,
                        y = ey,
                        z = ez,
                        yaw = rng.nextFloat() * 6.28f,
                        targetYaw = rng.nextFloat() * 6.28f
                    )
                )
            }
        }
    }

    fun spawnEntityInFront(
        world: VoxelChunkWorld,
        player: VoxelPlayerController,
        kind: EntityKind,
        distance: Float = 4.5f
    ): VoxelEntity {
        val (fx, _, fz) = player.forwardVector()
        val len = hypot(fx, fz).coerceAtLeast(0.01f)
        val sx = player.x + (fx / len) * distance
        val sz = player.z + (fz / len) * distance
        val groundY = world.findSpawnY(floor(sx).toInt(), floor(sz).toInt())
        val sy = if (kind.isFlying) groundY + 4.5f else groundY
        val entity = VoxelEntity(
            id = nextId++,
            kind = kind,
            x = sx,
            y = sy,
            z = sz,
            yaw = player.yaw + 3.14f,
            targetYaw = player.yaw + 3.14f,
            connected = kind.isCompanion
        )
        synchronized(lock) {
            if (entities.size >= MAX_ENTITIES) {
                val oldestIdx = entities.indexOfFirst { it.kind == kind && !it.connected }.takeIf { it >= 0 } ?: 0
                entities.removeAt(oldestIdx)
            }
            entities.add(entity)
            spawnBurstLocked(sx, sy + 1.0f, sz, 0.35f, 1.0f, 0.65f, 8)
        }
        return entity
    }

    /**
     * Connects / Bonds nearby animals & characters ("Connector Animals") so they follow the player
     * as a loyal herd/caravan, emitting pink/gold heart sparkle particles!
     */
    fun connectNearbyAnimals(player: VoxelPlayerController, radius: Float = 16f): Int {
        var newlyConnected = 0
        synchronized(lock) {
            for (e in entities) {
                if (e.kind.isHostile) continue
                val dist = hypot(e.x - player.x, e.z - player.z)
                if (dist <= radius) {
                    if (!e.connected) {
                        e.connected = true
                        newlyConnected++
                    }
                    // Heart / bonding particle burst above every connected friend
                    spawnBurstLocked(e.x, e.y + e.kind.height + 0.3f, e.z, 1.0f, 0.32f, 0.56f, 6)
                }
            }
        }
        return newlyConnected
    }

    fun connectedAnimalCount(): Int = synchronized(lock) { entities.count { it.connected } }

    fun spawnBlockBreakParticles(bx: Int, by: Int, bz: Int, r: Float, g: Float, b: Float) {
        synchronized(lock) {
            spawnBurstLocked(bx + 0.5f, by + 0.5f, bz + 0.5f, r, g, b, 12)
        }
    }

    fun spawnWaterSplashParticles(x: Float, y: Float, z: Float) {
        synchronized(lock) {
            repeat(12) {
                if (particles.size < 200) {
                    particles.add(
                        VoxelParticle(
                            x = x + (rng.nextFloat() - 0.5f) * 0.7f,
                            y = y + 0.25f,
                            z = z + (rng.nextFloat() - 0.5f) * 0.7f,
                            vx = (rng.nextFloat() - 0.5f) * 3.8f,
                            vy = 2.8f + rng.nextFloat() * 3.4f,
                            vz = (rng.nextFloat() - 0.5f) * 3.8f,
                            r = 0.72f,
                            g = 0.94f,
                            b = 1.0f,
                            size = 0.10f + rng.nextFloat() * 0.06f,
                            life = 0.45f + rng.nextFloat() * 0.25f
                        )
                    )
                }
            }
        }
    }

    fun spawnUnderwaterBubble(x: Float, y: Float, z: Float) {
        synchronized(lock) {
            if (particles.size < 200) {
                particles.add(
                    VoxelParticle(
                        x = x + (rng.nextFloat() - 0.5f) * 1.4f,
                        y = y - 0.4f,
                        z = z + (rng.nextFloat() - 0.5f) * 1.4f,
                        vx = (rng.nextFloat() - 0.5f) * 0.4f,
                        vy = 2.4f + rng.nextFloat() * 1.6f,
                        vz = (rng.nextFloat() - 0.5f) * 0.4f,
                        r = 0.78f,
                        g = 0.96f,
                        b = 1.0f,
                        size = 0.09f,
                        life = 0.85f
                    )
                )
            }
        }
    }

    fun spawnZombieWave(world: VoxelChunkWorld, player: VoxelPlayerController, count: Int = 4) {
        synchronized(lock) {
            repeat(count) { i ->
                val angle = (i.toFloat() / count) * 6.283f + rng.nextFloat() * 0.5f
                val dist = 12f + rng.nextFloat() * 6f
                val zx = player.x + cos(angle) * dist
                val zz = player.z + sin(angle) * dist
                val zy = world.findSpawnY(floor(zx).toInt(), floor(zz).toInt())
                if (entities.size < MAX_ENTITIES) {
                    entities.add(
                        VoxelEntity(
                            id = nextId++,
                            kind = EntityKind.ZOMBIE,
                            x = zx,
                            y = zy,
                            z = zz,
                            yaw = angle + 3.14f
                        )
                    )
                }
            }
        }
    }

    fun fireGun(player: VoxelPlayerController) {
        val (rawFx, rawFy, rawFz) = player.forwardVector()
        val startX = player.x + rawFx * 0.65f
        val startY = player.eyeY - 0.12f + rawFy * 0.65f
        val startZ = player.z + rawFz * 0.65f

        var fx = rawFx
        var fy = rawFy
        var fz = rawFz

        synchronized(lock) {
            // Subtle mobile aim-assist: lock smoothly onto any hostile zombie near the crosshair
            var bestDot = 0.965f
            var bestDir: Triple<Float, Float, Float>? = null
            for (e in entities) {
                if (!e.kind.isHostile) continue
                val tx = e.x - startX
                val ty = (e.y + e.kind.height * 0.55f) - startY
                val tz = e.z - startZ
                val dist = kotlin.math.sqrt(tx * tx + ty * ty + tz * tz)
                if (dist in 0.5f..26f) {
                    val nx = tx / dist
                    val ny = ty / dist
                    val nz = tz / dist
                    val dot = nx * rawFx + ny * rawFy + nz * rawFz
                    if (dot > bestDot) {
                        bestDot = dot
                        bestDir = Triple(nx, ny, nz)
                    }
                }
            }
            if (bestDir != null) {
                fx = bestDir.first
                fy = bestDir.second
                fz = bestDir.third
            }

            bullets.add(
                BulletProjectile(
                    x = startX,
                    y = startY,
                    z = startZ,
                    vx = fx * BULLET_SPEED,
                    vy = fy * BULLET_SPEED,
                    vz = fz * BULLET_SPEED
                )
            )
            // Muzzle spark particles
            repeat(4) {
                particles.add(
                    VoxelParticle(
                        x = startX,
                        y = startY,
                        z = startZ,
                        vx = fx * 5f + (rng.nextFloat() - 0.5f) * 2.5f,
                        vy = fy * 5f + (rng.nextFloat() - 0.5f) * 2.5f,
                        vz = fz * 5f + (rng.nextFloat() - 0.5f) * 2.5f,
                        r = 1.0f,
                        g = 0.88f,
                        b = 0.25f,
                        size = 0.09f,
                        life = 0.18f
                    )
                )
            }
        }
    }

    fun stepSimulation(
        world: VoxelChunkWorld,
        player: VoxelPlayerController,
        dt: Float,
        creativeMode: Boolean,
        isNight: Boolean
    ): CombatEvents {
        var zombieHits = 0
        var zombieKills = 0
        var playerDamaged = false

        synchronized(lock) {
            // Gentle health regen
            if (playerHealth < 10) {
                healthRegenTimer += dt
                if (healthRegenTimer >= 4.0f) {
                    healthRegenTimer = 0f
                    playerHealth = (playerHealth + 1).coerceAtMost(10)
                }
            }

            // Periodically replenish a roaming zombie at night or if count < 2
            zombieAutoSpawnTimer += dt
            val zombieCount = entities.count { it.kind == EntityKind.ZOMBIE }
            val maxAutoZombies = if (isNight) 5 else 2
            if (zombieAutoSpawnTimer >= 12f && zombieCount < maxAutoZombies && entities.size < MAX_ENTITIES) {
                zombieAutoSpawnTimer = 0f
                val ang = rng.nextFloat() * 6.283f
                val zx = player.x + cos(ang) * 18f
                val zz = player.z + sin(ang) * 18f
                val zy = world.findSpawnY(floor(zx).toInt(), floor(zz).toInt())
                entities.add(VoxelEntity(nextId++, EntityKind.ZOMBIE, zx, zy, zz, yaw = ang))
            }

            // 1. Update bullets & check collisions with Zombies & Terrain
            val bulletIter = bullets.iterator()
            while (bulletIter.hasNext()) {
                val b = bulletIter.next()
                b.life -= dt
                if (b.life <= 0f) {
                    bulletIter.remove()
                    continue
                }
                val steps = 2
                var consumed = false
                for (s in 0 until steps) {
                    val subDt = dt / steps
                    b.x += b.vx * subDt
                    b.y += b.vy * subDt
                    b.z += b.vz * subDt

                    // Terrain impact
                    if (world.isSolid(floor(b.x).toInt(), floor(b.y).toInt(), floor(b.z).toInt())) {
                        spawnBurstLocked(b.x, b.y, b.z, 1.0f, 0.85f, 0.35f, 6)
                        consumed = true
                        break
                    }

                    // Zombie hit detection (with mobile-friendly hit radius)
                    val hitZombie = entities.firstOrNull { e ->
                        e.kind.isHostile &&
                            hypot(e.x - b.x, e.z - b.z) <= (e.kind.radius + 0.42f) &&
                            b.y in (e.y - 0.1f)..(e.y + e.kind.height + 0.25f)
                    }
                    if (hitZombie != null) {
                        hitZombie.hp -= 25
                        hitZombie.hitFlash = 0.22f
                        // Knockback away from bullet
                        val kLen = hypot(b.vx, b.vz).coerceAtLeast(0.01f)
                        hitZombie.x += (b.vx / kLen) * 0.45f
                        hitZombie.z += (b.vz / kLen) * 0.45f
                        hitZombie.vy = 3.2f
                        zombieHits++
                        spawnBurstLocked(b.x, b.y, b.z, 0.35f, 1.0f, 0.42f, 10)
                        if (hitZombie.hp <= 0) {
                            spawnBurstLocked(hitZombie.x, hitZombie.y + 1f, hitZombie.z, 0.25f, 0.95f, 0.35f, 16)
                            entities.remove(hitZombie)
                            zombiesDefeated++
                            zombieKills++
                        }
                        consumed = true
                        break
                    }
                }
                if (consumed) bulletIter.remove()
            }

            // 2. Update Entities with AI Tracking, Companion Guarding, Herd Wandering & Bird Flight
            val iter = entities.iterator()
            while (iter.hasNext()) {
                val e = iter.next()
                e.hitFlash = (e.hitFlash - dt).coerceAtLeast(0f)
                e.attackCooldown = (e.attackCooldown - dt).coerceAtLeast(0f)

                val dxP = player.x - e.x
                val dzP = player.z - e.z
                val distP = hypot(dxP, dzP)

                // Keep roaming wildlife within 48 blocks of player so the world stays lively wherever you explore
                if (distP > 48f) {
                    val ang = rng.nextFloat() * 6.283f
                    e.x = player.x + cos(ang) * 20f
                    e.z = player.z + sin(ang) * 20f
                    val gy = world.findSpawnY(floor(e.x).toInt(), floor(e.z).toInt())
                    e.y = if (e.kind.isFlying) gy + 6f else gy
                    continue
                }

                if (e.kind.isFlying) {
                    // Flocking Bird Flight AI ("चिड़िया मैना")
                    e.aiTimer -= dt
                    if (e.aiTimer <= 0f) {
                        e.aiTimer = 1.6f + rng.nextFloat() * 2.4f
                        e.targetYaw = if (distP > 22f) {
                            atan2(dxP, -dzP)
                        } else {
                            e.yaw + (rng.nextFloat() - 0.5f) * 2.2f
                        }
                    }
                    e.yaw = lerpAngle(e.yaw, e.targetYaw, dt * 2.4f)
                    e.x += sin(e.yaw) * e.kind.speed * dt
                    e.z -= cos(e.yaw) * e.kind.speed * dt
                    val desiredAlt = world.findSpawnY(floor(e.x).toInt(), floor(e.z).toInt()) + 5.2f + sin(e.animPhase * 0.4f) * 1.4f
                    e.y += (desiredAlt - e.y) * dt * 1.8f
                    e.animPhase += dt * 13f
                    continue
                }

                var moveSpeed = 0f

                when {
                    e.kind.isHostile -> {
                        // AI-TRACKING ZOMBIE: actively locks onto player and chases around/over blocks!
                        if (distP < 28f) {
                            e.targetYaw = atan2(dxP, -dzP)
                            e.yaw = lerpAngle(e.yaw, e.targetYaw, dt * 6.5f)
                            if (distP > 1.25f) {
                                moveSpeed = e.kind.speed
                            } else if (!creativeMode && e.attackCooldown <= 0f) {
                                e.attackCooldown = 1.35f
                                playerHealth = (playerHealth - 1).coerceAtLeast(1)
                                playerDamaged = true
                            }
                        } else {
                            e.aiTimer -= dt
                            if (e.aiTimer <= 0f) {
                                e.aiTimer = 2.5f + rng.nextFloat() * 2f
                                e.targetYaw = rng.nextFloat() * 6.283f
                            }
                            e.yaw = lerpAngle(e.yaw, e.targetYaw, dt * 2.5f)
                            moveSpeed = e.kind.speed * 0.45f
                        }
                    }

                    e.kind.isCompanion || e.connected -> {
                        // COMPANION, HERO & CONNECTED ANIMAL CARAVAN AI: Follows player & protects!
                        val nearZombie = if (e.kind.isCompanion) {
                            entities.firstOrNull { z ->
                                z.kind.isHostile && hypot(z.x - e.x, z.z - e.z) < 9.0f
                            }
                        } else null
                        if (nearZombie != null) {
                            val zdx = nearZombie.x - e.x
                            val zdz = nearZombie.z - e.z
                            val zd = hypot(zdx, zdz)
                            e.targetYaw = atan2(zdx, -zdz)
                            e.yaw = lerpAngle(e.yaw, e.targetYaw, dt * 7f)
                            if (zd > 1.3f) {
                                moveSpeed = e.kind.speed
                            } else if (e.attackCooldown <= 0f) {
                                e.attackCooldown = 1.1f
                                nearZombie.hp -= 20
                                nearZombie.hitFlash = 0.2f
                                spawnBurstLocked(nearZombie.x, nearZombie.y + 1f, nearZombie.z, 1.0f, 0.85f, 0.25f, 6)
                                if (nearZombie.hp <= 0) {
                                    zombiesDefeated++
                                    zombieKills++
                                }
                            }
                        } else if (distP > (if (e.connected) 4.2f else 7.5f)) {
                            e.targetYaw = atan2(dxP, -dzP)
                            e.yaw = lerpAngle(e.yaw, e.targetYaw, dt * 5.2f)
                            moveSpeed = e.kind.speed * (if (e.connected && distP > 8f) 1.35f else 1.0f)
                        } else {
                            e.aiTimer -= dt
                            if (e.aiTimer <= 0f) {
                                e.aiTimer = 2.0f + rng.nextFloat() * 3.0f
                                e.targetYaw = rng.nextFloat() * 6.283f
                            }
                            e.yaw = lerpAngle(e.yaw, e.targetYaw, dt * 2f)
                            if (e.aiTimer > 1.3f) moveSpeed = e.kind.speed * 0.42f
                        }
                    }

                    else -> {
                        // NATURAL ANIMAL & VILLAGER WANDER AI
                        e.aiTimer -= dt
                        if (e.aiTimer <= 0f) {
                            e.aiTimer = 1.8f + rng.nextFloat() * 3.5f
                            e.targetYaw = rng.nextFloat() * 6.283f
                        }
                        e.yaw = lerpAngle(e.yaw, e.targetYaw, dt * 2.2f)
                        if (e.aiTimer > 1.0f) {
                            moveSpeed = e.kind.speed * 0.65f
                        }
                    }
                }

                // Step ground locomotion + obstacle jump-climbing
                if (moveSpeed > 0.01f) {
                    val nx = e.x + sin(e.yaw) * moveSpeed * dt
                    val nz = e.z - cos(e.yaw) * moveSpeed * dt
                    val footY = floor(e.y + 0.08f).toInt()
                    val blockedLow = world.isSolid(floor(nx).toInt(), footY, floor(nz).toInt())
                    val blockedHigh = world.isSolid(floor(nx).toInt(), footY + 1, floor(nz).toInt())

                    if (!blockedLow) {
                        e.x = nx
                        e.z = nz
                    } else if (!blockedHigh && e.vy <= 0.05f) {
                        // Smart AI obstacle hop onto 1-block ledge!
                        e.vy = 6.6f
                        e.x = nx
                        e.z = nz
                    } else {
                        e.targetYaw += 1.9f
                    }
                    e.animPhase += dt * (moveSpeed * 3.2f)
                }

                // Gravity & ground landing
                e.vy = (e.vy - 20f * dt).coerceAtLeast(-22f)
                val nextY = e.y + e.vy * dt
                val gx = floor(e.x).toInt()
                val gz = floor(e.z).toInt()
                val belowSolid = world.isSolid(gx, floor(nextY).toInt(), gz)
                if (!belowSolid && nextY > 1.0f) {
                    e.y = nextY
                } else {
                    if (e.vy < 0f) {
                        e.y = (floor(nextY) + 1f).coerceAtLeast(1f)
                    }
                    e.vy = 0f
                }
            }

            // Clean up any zombies defeated by Heroes/Dogs
            entities.removeAll { it.kind.isHostile && it.hp <= 0 }

            // 3. Update Particles (bubbles float up, sparks & block debris fall with gravity)
            val pIter = particles.iterator()
            while (pIter.hasNext()) {
                val p = pIter.next()
                p.life -= dt
                if (p.life <= 0f) {
                    pIter.remove()
                    continue
                }
                p.x += p.vx * dt
                p.y += p.vy * dt
                p.z += p.vz * dt
                val isBubble = (p.b > 0.92f && p.r > 0.70f && p.vy > 0f && p.size < 0.095f)
                if (!isBubble) {
                    p.vy -= 11f * dt
                }
            }
        }

        return CombatEvents(zombieHits, zombieKills, playerDamaged)
    }

    fun snapshotMinimapBlips(playerX: Float, playerZ: Float, maxRange: Float = 26f): List<MinimapBlip> {
        synchronized(lock) {
            return entities.mapNotNull { e ->
                val dx = e.x - playerX
                val dz = e.z - playerZ
                if (hypot(dx, dz) <= maxRange) MinimapBlip(dx, dz, e.kind) else null
            }
        }
    }

    fun activeCounts(): Pair<Int, Int> = synchronized(lock) {
        val zombies = entities.count { it.kind.isHostile }
        entities.size to zombies
    }

    /**
     * Builds the consolidated 3D entity + optional 3rd-person player avatar + projectile + particle FloatBuffer.
     */
    fun buildRenderBuffer(
        playerForThirdPerson: VoxelPlayerController? = null,
        armSwingProgress: Float = 0f
    ): Pair<FloatBuffer, Int> {
        vertFloatCount = 0
        synchronized(lock) {
            if (playerForThirdPerson != null) {
                emitPlayerAvatar(playerForThirdPerson, armSwingProgress)
            }
            for (i in entities.indices) {
                emitEntityModel(entities[i])
            }
            for (i in bullets.indices) {
                val b = bullets[i]
                emitBox(b.x, b.y, b.z, 0.14f, 0.14f, 0.14f, 0f, 1.0f, 0.92f, 0.25f)
            }
            for (i in particles.indices) {
                val p = particles[i]
                emitBox(p.x, p.y, p.z, p.size, p.size, p.size, 0f, p.r, p.g, p.b)
            }
        }
        nativeBuffer.position(0)
        nativeBuffer.put(vertArray, 0, vertFloatCount)
        nativeBuffer.position(0)
        return nativeBuffer to (vertFloatCount / 7)
    }

    private fun emitPlayerAvatar(p: VoxelPlayerController, armSwingProgress: Float) {
        val stride = sin(p.walkBobPhase * 2f) * 0.22f * p.moveSpeedFraction
        val swimBob = if (p.inWater) sin(p.walkBobPhase * 1.5f) * 0.08f else 0f
        val crouchDrop = if (p.sneaking) -0.18f else 0f
        val swingKick = sin(armSwingProgress * 3.14159f) * 0.45f
        val c = cos(p.yaw)
        val s = sin(p.yaw)

        fun part(lx: Float, ly: Float, lz: Float, w: Float, h: Float, d: Float, r: Float, g: Float, b: Float) {
            val wx = p.x + lx * c + lz * s
            val wz = p.z + lx * s - lz * c
            emitBox(wx, p.y + ly + swimBob + crouchDrop, wz, w, h, d, p.yaw, r, g, b)
        }

        // Classic Minecraft Explorer Avatar (Emerald Tunic, Blue Denim, Animated Limbs & Pickaxe)
        part(-0.13f, 0.36f, stride, 0.21f, 0.72f, 0.21f, 0.18f, 0.30f, 0.62f)
        part(0.13f, 0.36f, -stride, 0.21f, 0.72f, 0.21f, 0.18f, 0.30f, 0.62f)
        part(0f, 1.04f, 0f, 0.50f, 0.66f, 0.28f, 0.12f, 0.76f, 0.52f) // emerald builder tunic
        part(-0.34f, 1.04f, -stride, 0.18f, 0.62f, 0.18f, 0.92f, 0.74f, 0.58f)
        part(0.34f, 1.04f + swingKick * 0.35f, stride + swingKick, 0.18f, 0.62f, 0.18f, 0.92f, 0.74f, 0.58f)
        // Held diamond tool in right hand
        part(0.36f, 0.96f + swingKick * 0.4f, 0.36f + swingKick, 0.09f, 0.42f, 0.09f, 0.22f, 0.90f, 1.0f)
        // Head + Hair
        part(0f, 1.60f, 0f, 0.46f, 0.46f, 0.46f, 0.92f, 0.74f, 0.58f)
        part(0f, 1.84f, 0f, 0.48f, 0.14f, 0.48f, 0.22f, 0.15f, 0.10f)
    }

    private fun emitEntityModel(e: VoxelEntity) {
        val flash = e.hitFlash > 0f
        val swing = sin(e.animPhase) * 0.16f
        val c = cos(e.yaw)
        val s = sin(e.yaw)

        fun part(
            lx: Float, ly: Float, lz: Float,
            w: Float, h: Float, d: Float,
            r: Float, g: Float, b: Float
        ) {
            val wx = e.x + lx * c + lz * s
            val wz = e.z + lx * s - lz * c
            val rr = if (flash) 1.0f else r
            val gg = if (flash) 0.25f else g
            val bb = if (flash) 0.25f else b
            emitBox(wx, e.y + ly, wz, w, h, d, e.yaw, rr, gg, bb)
        }

        when (e.kind) {
            EntityKind.BIRD_SPARROW, EntityKind.BIRD_MYNA -> {
                val isMyna = e.kind == EntityKind.BIRD_MYNA
                val bodyR = if (isMyna) 0.22f else 0.62f
                val bodyG = if (isMyna) 0.18f else 0.44f
                val bodyB = if (isMyna) 0.16f else 0.29f
                val wingLift = sin(e.animPhase) * 0.14f
                part(0f, 0.15f, 0f, 0.24f, 0.18f, 0.34f, bodyR, bodyG, bodyB) // body
                part(0f, 0.24f, 0.20f, 0.18f, 0.16f, 0.18f, if (isMyna) 0.12f else 0.55f, if (isMyna) 0.12f else 0.36f, if (isMyna) 0.14f else 0.22f) // head
                part(0f, 0.22f, 0.32f, 0.07f, 0.06f, 0.10f, 1.0f, 0.80f, 0.15f) // yellow beak
                part(-0.26f, 0.18f + wingLift, 0f, 0.30f, 0.05f, 0.22f, if (isMyna) 0.92f else 0.48f, if (isMyna) 0.92f else 0.32f, if (isMyna) 0.92f else 0.20f) // left wing
                part(0.26f, 0.18f + wingLift, 0f, 0.30f, 0.05f, 0.22f, if (isMyna) 0.92f else 0.48f, if (isMyna) 0.92f else 0.32f, if (isMyna) 0.92f else 0.20f) // right wing
            }

            EntityKind.ELEPHANT -> {
                val gR = 0.50f; val gG = 0.56f; val gB = 0.60f
                // 4 sturdy legs
                part(-0.42f, 0.45f, 0.50f + swing, 0.34f, 0.90f, 0.34f, gR * 0.85f, gG * 0.85f, gB * 0.85f)
                part(0.42f, 0.45f, 0.50f - swing, 0.34f, 0.90f, 0.34f, gR * 0.85f, gG * 0.85f, gB * 0.85f)
                part(-0.42f, 0.45f, -0.50f - swing, 0.34f, 0.90f, 0.34f, gR * 0.85f, gG * 0.85f, gB * 0.85f)
                part(0.42f, 0.45f, -0.50f + swing, 0.34f, 0.90f, 0.34f, gR * 0.85f, gG * 0.85f, gB * 0.85f)
                // Massive body & ceremonial blanket
                part(0f, 1.32f, 0f, 1.25f, 0.95f, 1.55f, gR, gG, gB)
                part(0f, 1.82f, 0f, 1.28f, 0.12f, 0.85f, 0.92f, 0.22f, 0.28f) // red & gold royal saddle cloth
                // Head, Big Ears, Trunk & White Tusks
                part(0f, 1.58f, 0.98f, 0.90f, 0.86f, 0.78f, gR * 1.05f, gG * 1.05f, gB * 1.05f)
                part(-0.62f, 1.58f, 0.88f, 0.36f, 0.72f, 0.14f, gR * 0.92f, gG * 0.92f, gB * 0.92f) // left ear
                part(0.62f, 1.58f, 0.88f, 0.36f, 0.72f, 0.14f, gR * 0.92f, gG * 0.92f, gB * 0.92f) // right ear
                part(0f, 1.02f, 1.42f, 0.26f, 0.92f, 0.26f, gR * 0.95f, gG * 0.95f, gB * 0.95f) // trunk
                part(-0.26f, 1.24f, 1.44f, 0.10f, 0.12f, 0.48f, 0.98f, 0.96f, 0.88f) // left tusk
                part(0.26f, 1.24f, 1.44f, 0.10f, 0.12f, 0.48f, 0.98f, 0.96f, 0.88f) // right tusk
            }

            EntityKind.COW, EntityKind.BUFFALO, EntityKind.BULL, EntityKind.HORSE -> {
                val (bR, bG, bB) = when (e.kind) {
                    EntityKind.COW -> Triple(0.94f, 0.92f, 0.88f)
                    EntityKind.BUFFALO -> Triple(0.16f, 0.18f, 0.21f)
                    EntityKind.BULL -> Triple(0.48f, 0.28f, 0.16f)
                    else -> Triple(0.62f, 0.38f, 0.20f) // horse
                }
                val isHorse = e.kind == EntityKind.HORSE
                val legH = if (isHorse) 0.78f else 0.56f
                part(-0.24f, legH / 2f, 0.36f + swing, 0.18f, legH, 0.18f, bR * 0.8f, bG * 0.8f, bB * 0.8f)
                part(0.24f, legH / 2f, 0.36f - swing, 0.18f, legH, 0.18f, bR * 0.8f, bG * 0.8f, bB * 0.8f)
                part(-0.24f, legH / 2f, -0.36f - swing, 0.18f, legH, 0.18f, bR * 0.8f, bG * 0.8f, bB * 0.8f)
                part(0.24f, legH / 2f, -0.36f + swing, 0.18f, legH, 0.18f, bR * 0.8f, bG * 0.8f, bB * 0.8f)

                val bodyY = legH + 0.32f
                part(0f, bodyY, 0f, 0.68f, 0.62f, if (isHorse) 1.20f else 1.05f, bR, bG, bB)
                if (e.kind == EntityKind.COW) {
                    part(0.18f, bodyY + 0.12f, 0.10f, 0.36f, 0.32f, 0.40f, 0.24f, 0.18f, 0.15f) // cow patch
                }
                if (isHorse) {
                    // Long neck, mane & head
                    part(0f, bodyY + 0.44f, 0.52f, 0.28f, 0.52f, 0.32f, bR, bG, bB)
                    part(0f, bodyY + 0.68f, 0.72f, 0.32f, 0.30f, 0.52f, bR * 0.92f, bG * 0.92f, bB * 0.92f)
                    part(0f, bodyY + 0.50f, 0.36f, 0.12f, 0.48f, 0.14f, 0.16f, 0.10f, 0.08f) // dark mane
                } else {
                    // Head + Horns for Cow/Buffalo/Bull
                    part(0f, bodyY + 0.12f, 0.66f, 0.46f, 0.44f, 0.44f, bR * 0.95f, bG * 0.95f, bB * 0.95f)
                    val hornCol = if (e.kind == EntityKind.BUFFALO) 0.28f else 0.88f
                    part(-0.28f, bodyY + 0.36f, 0.66f, 0.10f, 0.22f, 0.10f, hornCol, hornCol, hornCol * 0.9f)
                    part(0.28f, bodyY + 0.36f, 0.66f, 0.10f, 0.22f, 0.10f, hornCol, hornCol, hornCol * 0.9f)
                }
            }

            EntityKind.DOG, EntityKind.CAT -> {
                val isDog = e.kind == EntityKind.DOG
                val r = if (isDog) 0.82f else 0.98f
                val g = if (isDog) 0.64f else 0.72f
                val b = if (isDog) 0.44f else 0.30f
                val sScale = if (isDog) 1.0f else 0.76f
                part(-0.14f * sScale, 0.16f * sScale, 0.20f * sScale + swing * 0.6f, 0.11f * sScale, 0.32f * sScale, 0.11f * sScale, r * 0.85f, g * 0.85f, b * 0.85f)
                part(0.14f * sScale, 0.16f * sScale, 0.20f * sScale - swing * 0.6f, 0.11f * sScale, 0.32f * sScale, 0.11f * sScale, r * 0.85f, g * 0.85f, b * 0.85f)
                part(-0.14f * sScale, 0.16f * sScale, -0.20f * sScale - swing * 0.6f, 0.11f * sScale, 0.32f * sScale, 0.11f * sScale, r * 0.85f, g * 0.85f, b * 0.85f)
                part(0.14f * sScale, 0.16f * sScale, -0.20f * sScale + swing * 0.6f, 0.11f * sScale, 0.32f * sScale, 0.11f * sScale, r * 0.85f, g * 0.85f, b * 0.85f)
                part(0f, 0.40f * sScale, 0f, 0.38f * sScale, 0.30f * sScale, 0.62f * sScale, r, g, b)
                part(0f, 0.56f * sScale, 0.38f * sScale, 0.32f * sScale, 0.30f * sScale, 0.32f * sScale, r * 1.04f, g * 1.04f, b * 1.04f)
                part(0f, 0.56f * sScale, -0.40f * sScale, 0.10f * sScale, 0.28f * sScale, 0.10f * sScale, r, g, b) // wagging tail
                if (isDog) {
                    part(0f, 0.48f, 0.24f, 0.40f, 0.08f, 0.10f, 0.95f, 0.18f, 0.22f) // red collar
                }
            }

            EntityKind.ZOMBIE -> {
                // Iconic green Zombie with outstretched tracking arms
                part(-0.14f, 0.36f, swing, 0.22f, 0.72f, 0.22f, 0.20f, 0.28f, 0.55f)
                part(0.14f, 0.36f, -swing, 0.22f, 0.72f, 0.22f, 0.20f, 0.28f, 0.55f)
                part(0f, 1.04f, 0f, 0.52f, 0.66f, 0.28f, 0.14f, 0.54f, 0.58f) // torn teal tunic
                part(0f, 1.62f, 0f, 0.46f, 0.46f, 0.46f, 0.32f, 0.76f, 0.28f) // green head
                part(-0.11f, 1.66f, 0.24f, 0.09f, 0.07f, 0.04f, 0.95f, 0.15f, 0.15f) // glowing red eye L
                part(0.11f, 1.66f, 0.24f, 0.09f, 0.07f, 0.04f, 0.95f, 0.15f, 0.15f) // glowing red eye R
                // Outstretched tracking arms!
                part(-0.36f, 1.18f, 0.32f, 0.18f, 0.18f, 0.66f, 0.32f, 0.76f, 0.28f)
                part(0.36f, 1.18f, 0.32f, 0.18f, 0.18f, 0.66f, 0.32f, 0.76f, 0.28f)
            }

            // --- Humanoid Heroes, Devotional & Cartoon Characters ---
            else -> {
                val scale = if (e.kind == EntityKind.RAJU) 0.82f else 1.0f
                val (skinR, skinG, skinB) = when (e.kind) {
                    EntityKind.KRISHNA -> Triple(0.24f, 0.68f, 0.96f) // divine sky-blue aura
                    EntityKind.RAM -> Triple(0.36f, 0.72f, 0.86f)
                    EntityKind.HANUMAN -> Triple(0.98f, 0.46f, 0.16f) // vermilion saffron radiance
                    else -> Triple(0.92f, 0.72f, 0.56f)
                }
                val (tunicR, tunicG, tunicB) = when (e.kind) {
                    EntityKind.KRISHNA -> Triple(1.0f, 0.84f, 0.18f) // golden pitambara
                    EntityKind.RAM -> Triple(0.96f, 0.62f, 0.14f) // saffron/gold royal robe
                    EntityKind.HANUMAN -> Triple(1.0f, 0.38f, 0.08f) // saffron dhoti
                    EntityKind.BHEEM -> Triple(1.0f, 0.56f, 0.08f) // bright orange dhoti
                    EntityKind.RAJU -> Triple(1.0f, 0.82f, 0.20f) // bright yellow outfit
                    else -> Triple(0.30f, 0.55f, 0.82f) // villager shirt
                }
                val (legR, legG, legB) = when (e.kind) {
                    EntityKind.KRISHNA -> Triple(1.0f, 0.82f, 0.14f)
                    EntityKind.BHEEM -> Triple(0.98f, 0.52f, 0.06f)
                    else -> Triple(tunicR * 0.85f, tunicG * 0.85f, tunicB * 0.85f)
                }

                // Legs
                part(-0.13f * scale, 0.35f * scale, swing, 0.20f * scale, 0.70f * scale, 0.20f * scale, legR, legG, legB)
                part(0.13f * scale, 0.35f * scale, -swing, 0.20f * scale, 0.70f * scale, 0.20f * scale, legR, legG, legB)
                // Torso
                part(0f, 1.02f * scale, 0f, 0.50f * scale, 0.64f * scale, 0.28f * scale, tunicR, tunicG, tunicB)
                // Arms
                part(-0.34f * scale, 1.02f * scale, -swing, 0.17f * scale, 0.58f * scale, 0.18f * scale, skinR, skinG, skinB)
                part(0.34f * scale, 1.02f * scale, swing, 0.17f * scale, 0.58f * scale, 0.18f * scale, skinR, skinG, skinB)
                // Head
                part(0f, 1.58f * scale, 0f, 0.44f * scale, 0.44f * scale, 0.44f * scale, skinR, skinG, skinB)

                // Signature Character Accessories
                when (e.kind) {
                    EntityKind.KRISHNA -> {
                        // Golden crown + vibrant green/cyan Peacock Feather (Mor Pankh) + Bansuri Flute
                        part(0f, 1.88f, 0f, 0.46f, 0.18f, 0.46f, 1.0f, 0.84f, 0.18f)
                        part(0.10f, 2.12f, 0.04f, 0.14f, 0.34f, 0.08f, 0.10f, 0.88f, 0.55f) // mor pankh
                        part(0.10f, 2.16f, 0.09f, 0.08f, 0.10f, 0.04f, 0.12f, 0.45f, 1.0f) // peacock eye
                        part(0.24f, 1.44f, 0.28f, 0.58f, 0.06f, 0.06f, 0.96f, 0.82f, 0.42f) // flute
                    }
                    EntityKind.RAM -> {
                        // Royal Golden Mukut Crown + Golden Bow (Dhanush) on back
                        part(0f, 1.92f, 0f, 0.44f, 0.28f, 0.44f, 1.0f, 0.84f, 0.16f)
                        part(0f, 2.12f, 0f, 0.22f, 0.16f, 0.22f, 1.0f, 0.92f, 0.35f)
                        part(0.42f, 1.15f, 0.18f, 0.08f, 0.92f, 0.18f, 0.98f, 0.78f, 0.20f) // bow
                    }
                    EntityKind.HANUMAN -> {
                        // Golden Crown + Golden Gada (Mace) in right hand + curved tail
                        part(0f, 1.90f, 0f, 0.44f, 0.26f, 0.44f, 1.0f, 0.84f, 0.16f)
                        part(0.42f, 1.18f, 0.26f, 0.10f, 0.75f, 0.10f, 1.0f, 0.82f, 0.18f) // gada handle
                        part(0.42f, 1.62f, 0.26f, 0.28f, 0.28f, 0.28f, 1.0f, 0.88f, 0.22f) // gada sphere
                        part(0f, 0.96f, -0.32f, 0.10f, 0.72f, 0.10f, 0.96f, 0.44f, 0.14f) // tail
                    }
                    EntityKind.BHEEM -> {
                        // Iconic dark spiky hair + gold armband & tilak
                        part(0f, 1.84f, 0f, 0.46f, 0.16f, 0.46f, 0.14f, 0.12f, 0.14f)
                        part(0f, 1.64f, 0.23f, 0.07f, 0.14f, 0.04f, 1.0f, 0.24f, 0.16f) // tilak
                    }
                    EntityKind.RAJU -> {
                        // Cute top-knot hair tuft
                        part(0f, 1.86f * scale, 0f, 0.16f, 0.18f, 0.16f, 0.12f, 0.12f, 0.14f)
                    }
                    else -> {
                        part(0f, 1.82f, 0f, 0.46f, 0.14f, 0.46f, 0.22f, 0.16f, 0.12f)
                    }
                }
            }
        }
    }

    private fun spawnBurstLocked(x: Float, y: Float, z: Float, r: Float, g: Float, b: Float, count: Int) {
        repeat(count) {
            if (particles.size < 160) {
                particles.add(
                    VoxelParticle(
                        x = x,
                        y = y,
                        z = z,
                        vx = (rng.nextFloat() - 0.5f) * 6f,
                        vy = 2f + rng.nextFloat() * 4.5f,
                        vz = (rng.nextFloat() - 0.5f) * 6f,
                        r = r,
                        g = g,
                        b = b,
                        size = 0.11f + rng.nextFloat() * 0.08f,
                        life = 0.35f + rng.nextFloat() * 0.35f
                    )
                )
            }
        }
    }

    private fun lerpAngle(current: Float, target: Float, factor: Float): Float {
        var diff = (target - current) % 6.2831855f
        if (diff > 3.1415927f) diff -= 6.2831855f
        if (diff < -3.1415927f) diff += 6.2831855f
        return current + diff * factor.coerceIn(0f, 1f)
    }

    /**
     * Emits a Y-rotated 3D colored box (36 vertices * 7 floats) into [vertArray].
     */
    private fun emitBox(
        cx: Float, cy: Float, cz: Float,
        w: Float, h: Float, d: Float,
        yaw: Float,
        r: Float, g: Float, b: Float
    ) {
        if (vertFloatCount + 36 * 7 > vertArray.size) return
        val hw = w * 0.5f
        val hh = h * 0.5f
        val hd = d * 0.5f
        val cosY = cos(yaw)
        val sinY = sin(yaw)

        fun tx(lx: Float, lz: Float) = cx + lx * cosY + lz * sinY
        fun tz(lx: Float, lz: Float) = cz + lx * sinY - lz * cosY

        val y0 = cy - hh
        val y1 = cy + hh

        val x00 = tx(-hw, -hd); val z00 = tz(-hw, -hd)
        val x10 = tx(hw, -hd);  val z10 = tz(hw, -hd)
        val x11 = tx(hw, hd);   val z11 = tz(hw, hd)
        val x01 = tx(-hw, hd);  val z01 = tz(-hw, hd)

        // Top (+Y)
        emitQuad(x01, y1, z01, x11, y1, z11, x10, y1, z10, x00, y1, z00, r, g, b, 1.00f)
        // Bottom (-Y)
        emitQuad(x00, y0, z00, x10, y0, z10, x11, y0, z11, x01, y0, z01, r, g, b, 0.55f)
        // Front (+Z local)
        emitQuad(x01, y0, z01, x11, y0, z11, x11, y1, z11, x01, y1, z01, r, g, b, 0.88f)
        // Back (-Z local)
        emitQuad(x10, y0, z10, x00, y0, z00, x00, y1, z00, x10, y1, z10, r, g, b, 0.72f)
        // Right (+X local)
        emitQuad(x11, y0, z11, x10, y0, z10, x10, y1, z10, x11, y1, z11, r, g, b, 0.80f)
        // Left (-X local)
        emitQuad(x00, y0, z00, x01, y0, z01, x01, y1, z01, x00, y1, z00, r, g, b, 0.76f)
    }

    private fun emitQuad(
        ax: Float, ay: Float, az: Float,
        bx: Float, by: Float, bz: Float,
        cx: Float, cy: Float, cz: Float,
        dx: Float, dy: Float, dz: Float,
        r: Float, g: Float, b: Float,
        shade: Float
    ) {
        addV(ax, ay, az, r, g, b, shade)
        addV(bx, by, bz, r, g, b, shade)
        addV(cx, cy, cz, r, g, b, shade)
        addV(ax, ay, az, r, g, b, shade)
        addV(cx, cy, cz, r, g, b, shade)
        addV(dx, dy, dz, r, g, b, shade)
    }

    private fun addV(x: Float, y: Float, z: Float, r: Float, g: Float, b: Float, shade: Float) {
        vertArray[vertFloatCount++] = x
        vertArray[vertFloatCount++] = y
        vertArray[vertFloatCount++] = z
        vertArray[vertFloatCount++] = r
        vertArray[vertFloatCount++] = g
        vertArray[vertFloatCount++] = b
        vertArray[vertFloatCount++] = shade
    }

    companion object {
        private const val MAX_ENTITIES = 64
        private const val BULLET_SPEED = 32f
        private const val MAX_FLOATS = 180_000 // ~25,700 colored vertices
    }
}
