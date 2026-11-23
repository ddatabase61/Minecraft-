package com.example.engine.gl

import com.example.data.AdminCustom3DModel
import com.example.data.CustomModelRole
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
    SOCIETY("Living Society (राजा, पुजारी, मज़दूर)"),
    HEROES("Mythic & Heroes"),
    ANIMALS("Animals & Birds"),
    ZOMBIES("Zombies")
}

enum class AiGoal(val labelHindi: String) {
    WANDER("गाँव में घूम रहे हैं"),
    BUILD_HOUSE("नया घर बना रहे हैं"),
    BUILD_MANDIR("नया मंदिर बना रहे हैं"),
    BRING_WATER("यहाँ पानी की नदी ला रहे हैं"),
    PLANT_TREES("पेड़ और बगीचा लगा रहे हैं"),
    DO_PUJA("मंदिर में पूजा कर रहे हैं"),
    ROYAL_PATROL("राजमहल की सैर कर रहे हैं"),
    TRADE_WORK("व्यापार और काम कर रहे हैं"),
    FARM_WORK("मेहनत और खेती कर रहे हैं"),
    FOLLOW_FRIEND("आपके दोस्त बनकर साथ चल रहे हैं"),
    REST_SEAT("बैठकर आराम कर रहे हैं"),
    CHAT_NPC("दूसरे दोस्त से गपशप कर रहे हैं"),
    WATER_RELAX("पानी के किनारे टहल रहे हैं")
}

enum class EntityKind(
    val displayName: String,
    val badgeColorHex: Long,
    val category: EntityCategory,
    val isHostile: Boolean = false,
    val isFlying: Boolean = false,
    val isCompanion: Boolean = false,
    val isTalkableAi: Boolean = false,
    val roleSummaryHindi: String = "",
    val soundStyle: String = "CATTLE",
    val maxHp: Int = 60,
    val speed: Float = 1.9f,
    val radius: Float = 0.45f,
    val height: Float = 1.65f
) {
    // --- Autonomous Living Society (Independent Thinkers & Builders!) ---
    RAJA(
        displayName = "Raja (महाराजा)",
        badgeColorHex = 0xFFFFD54F,
        category = EntityCategory.SOCIETY,
        isTalkableAi = true,
        roleSummaryHindi = "राजसी जीवन जीने वाले दयालु राजा जो प्रजा की रक्षा करते हैं",
        soundStyle = "HERO",
        maxHp = 150,
        speed = 2.15f,
        height = 1.90f
    ),
    PUJARI(
        displayName = "Pujari (मंदिर पुजारी)",
        badgeColorHex = 0xFFFF7043,
        category = EntityCategory.SOCIETY,
        isTalkableAi = true,
        roleSummaryHindi = "मंदिर में नित्य पूजा करने वाले और नए मंदिर बनवाने वाले पुजारी",
        soundStyle = "PUJA",
        maxHp = 100,
        speed = 2.0f,
        height = 1.82f
    ),
    MAJDUR(
        displayName = "Majdur (घर बनाने वाला)",
        badgeColorHex = 0xFFE68A3E,
        category = EntityCategory.SOCIETY,
        isTalkableAi = true,
        roleSummaryHindi = "मेहनती कारीगर जो गाँव में नए पक्के घर और इमारतें बनाता है",
        soundStyle = "HERO",
        maxHp = 110,
        speed = 2.25f,
        height = 1.80f
    ),
    AMIR_SETH(
        displayName = "Amir Seth (अमीर सेठ)",
        badgeColorHex = 0xFFAB47BC,
        category = EntityCategory.SOCIETY,
        isTalkableAi = true,
        roleSummaryHindi = "धनवान व्यापारी जो बाज़ार और सोने के सिक्कों का व्यापार करता है",
        soundStyle = "HERO",
        maxHp = 110,
        speed = 2.0f,
        height = 1.82f
    ),
    GARIB_KISAN(
        displayName = "Garib Kisan (गरीब किसान)",
        badgeColorHex = 0xFF8D6E63,
        category = EntityCategory.SOCIETY,
        isTalkableAi = true,
        roleSummaryHindi = "ईमानदार और मेहनती किसान जो खेतों में काम करता है और मदद की कदर करता है",
        soundStyle = "HERO",
        maxHp = 85,
        speed = 1.95f,
        height = 1.78f
    ),

    // --- Heroes, Mythic & Cartoon Characters ---
    KRISHNA("Shri Krishna", 0xFF29B6F6, EntityCategory.HEROES, isCompanion = true, isTalkableAi = true, roleSummaryHindi = "मधुर बांसुरी बजाने वाले श्री कृष्ण जो प्रेम और ज्ञान सिखाते हैं", soundStyle = "HERO", maxHp = 140, speed = 2.6f, height = 1.85f),
    RAM("Shri Ram", 0xFF34D399, EntityCategory.HEROES, isCompanion = true, isTalkableAi = true, roleSummaryHindi = "मर्यादा पुरुषोत्तम श्री राम जो धर्म और सत्य का मार्गदर्शन करते हैं", soundStyle = "HERO", maxHp = 140, speed = 2.6f, height = 1.85f),
    HANUMAN("Shri Hanuman", 0xFFFF7043, EntityCategory.HEROES, isCompanion = true, isTalkableAi = true, roleSummaryHindi = "महाबली श्री हनुमान जो हर संकट से बचाते हैं", soundStyle = "HERO", maxHp = 160, speed = 3.0f, height = 1.9f),
    BHEEM("Bheem", 0xFFFF9800, EntityCategory.HEROES, isCompanion = true, isTalkableAi = true, roleSummaryHindi = "शक्तिशाली और मिलनसार भीम जो हमेशा दोस्तों की मदद करता है", soundStyle = "HERO", maxHp = 120, speed = 2.7f, height = 1.75f),
    RAJU("Raju", 0xFFFFCA28, EntityCategory.HEROES, isCompanion = true, isTalkableAi = true, roleSummaryHindi = "चंचल और समझदार दोस्त राजू", soundStyle = "HERO", maxHp = 90, speed = 2.8f, height = 1.45f),
    VILLAGER("Villager (ग्रामवासी)", 0xFF795548, EntityCategory.SOCIETY, isTalkableAi = true, roleSummaryHindi = "गाँव का मिलनसार निवासी", soundStyle = "HERO", maxHp = 80, speed = 2.0f, height = 1.8f),

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

    // --- Hostile AI-Tracking Zombies & Admin Custom 3D Models ---
    ZOMBIE("Zombie", 0xFF4CAF50, EntityCategory.ZOMBIES, isHostile = true, soundStyle = "ZOMBIE", maxHp = 60, speed = 2.45f, radius = 0.45f, height = 1.85f),
    CUSTOM_HERO("Custom 3D Hero", 0xFF29B6F6, EntityCategory.HEROES, isCompanion = true, isTalkableAi = true, roleSummaryHindi = "एडमिन द्वारा बनाया गया विशेष 3D किरदार जो आपके साथ काम और बात कर सकता है", soundStyle = "HERO", maxHp = 150, speed = 2.8f, radius = 0.48f, height = 1.9f),
    CUSTOM_ENEMY("Custom 3D Dushman", 0xFFFF3D00, EntityCategory.ZOMBIES, isHostile = true, soundStyle = "ZOMBIE", maxHp = 140, speed = 2.9f, radius = 0.55f, height = 2.0f)
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
    var maxHpOverride: Int = kind.maxHp,
    var animPhase: Float = 0f,
    var aiTimer: Float = 0f,
    var targetYaw: Float = 0f,
    var hitFlash: Float = 0f,
    var attackCooldown: Float = 0f,
    var connected: Boolean = false,
    var goal: AiGoal = AiGoal.WANDER,
    var goalTimer: Float = 4f,
    var friendshipLevel: Int = 25,
    var isFriend: Boolean = false,
    var targetX: Float = x,
    var targetZ: Float = z,
    var buildCooldown: Float = 10f,
    var talkingTimer: Float = 0f,
    var energy: Float = 85f,
    var socialNeed: Float = 40f,
    var isResting: Boolean = false,
    var socialPartnerId: Int? = null,
    val customName: String? = null,
    val customModelId: String? = null,
    val customMesh: Parsed3DModelMesh? = null,
    val customPrimaryHex: Long? = null,
    val customAccentHex: Long? = null,
    val customDamage: Int = 1,
    val customSpeed: Float? = null,
    val customScale: Float = 1.0f,
    val customGreeting: String? = null
) {
    val effectiveName: String
        get() = customName?.takeIf { it.isNotBlank() } ?: kind.displayName
}

data class TalkableNpcSnapshot(
    val id: Int,
    val kind: EntityKind,
    val displayName: String,
    val goalLabelHindi: String,
    val friendshipLevel: Int,
    val isFriend: Boolean,
    val distanceBlocks: Int,
    val isAnimal: Boolean = false,
    val customGreeting: String? = null
)

data class BulletProjectile(
    var x: Float,
    var y: Float,
    var z: Float,
    val vx: Float,
    val vy: Float,
    val vz: Float,
    val damage: Int = 25,
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
    var life: Float,
    val floatsUp: Boolean = false
)

data class MinimapBlip(
    val dx: Float,
    val dz: Float,
    val kind: EntityKind
)

data class CombatEvents(
    val zombieHits: Int = 0,
    val zombieKills: Int = 0,
    val playerDamaged: Boolean = false,
    val pujaBellTriggered: Boolean = false,
    val humanWorkSoundTriggered: Boolean = false,
    val aiActivityToast: String? = null
)

/**
 * Manages all living 3D entities including:
 * - Autonomous Thinking Really-Human Society (Raja, Pujari, Majdur, Amir Seth, Garib Kisan, Villagers)
 *   with independent human brains (energy, socializing with each other, resting, relaxing by water,
 *   building 3D houses/Mandirs, Puja, trading & farming!)
 * - Heroes (Shri Krishna, Shri Ram, Shri Hanuman, Bheem, Raju), Animals, Birds & AI-Tracking Zombies.
 * - Full 3D Rideable Motorcycles + Seated Water-Float Raft + Exhaust/Ripple/Puja Particles.
 */
class VoxelEntityManager(seed: Long) {
    private val rng = Random(seed + 5511L)
    private val entities = ArrayList<VoxelEntity>(72)
    private val bullets = ArrayList<BulletProjectile>(32)
    private val particles = ArrayList<VoxelParticle>(240)
    private val lock = Any()
    private var nextId = 1

    @Volatile var zombiesDefeated: Int = 0
        private set
    @Volatile var playerHealth: Int = 10
        private set
    @Volatile var blasterDamage: Int = 25
    @Volatile var playerWearsRoyalCrown: Boolean = false
    @Volatile var playerCustomModel: AdminCustom3DModel? = null
    @Volatile var playerCustomMesh: Parsed3DModelMesh? = null
    @Volatile var preferredTalkTargetId: Int? = null
    private var healthRegenTimer = 0f
    private var zombieAutoSpawnTimer = 0f
    private var pujaBellCooldown = 0f
    private var humanWorkSoundCooldown = 0f
    private var globalAnimClock = 0f

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

            val roster = listOf(
                // Autonomous Living Society positioned around Palace, Mandir & Village Square
                EntityKind.RAJA to (-8f - spawnX to 16f - spawnZ),
                EntityKind.PUJARI to (18f - spawnX to 7f - spawnZ),
                EntityKind.MAJDUR to (6f to 8f),
                EntityKind.MAJDUR to (-5f to -4f),
                EntityKind.AMIR_SETH to (-3f to 10f),
                EntityKind.GARIB_KISAN to (10f to -3f),
                EntityKind.VILLAGER to (15f - spawnX to 6f - spawnZ),
                // Iconic Mythic & Cartoon Friends
                EntityKind.KRISHNA to (4f to -5f),
                EntityKind.RAM to (-4f to -6f),
                EntityKind.HANUMAN to (6f to -3f),
                EntityKind.BHEEM to (-5f to 4f),
                EntityKind.RAJU to (-3f to 5f),
                // Animals
                EntityKind.COW to (9f to -4f),
                EntityKind.BUFFALO to (11f to 2f),
                EntityKind.BULL to (-9f to -3f),
                EntityKind.HORSE to (-8f to 7f),
                EntityKind.ELEPHANT to (13f to -9f),
                EntityKind.DOG to (2.5f to 3f),
                EntityKind.CAT to (-2.5f to 3f),
                // Flying Birds
                EntityKind.BIRD_SPARROW to (4f to -8f),
                EntityKind.BIRD_SPARROW to (-7f to -5f),
                EntityKind.BIRD_MYNA to (8f to 6f),
                EntityKind.BIRD_MYNA to (-5f to 9f),
                // Roaming AI-tracking Zombies further out
                EntityKind.ZOMBIE to (20f to -18f),
                EntityKind.ZOMBIE to (-20f to -18f),
                EntityKind.ZOMBIE to (18f to 20f)
            )

            roster.forEach { (kind, offset) ->
                val ex = spawnX + offset.first
                val ez = spawnZ + offset.second
                val groundY = world.findSpawnY(floor(ex).toInt(), floor(ez).toInt())
                val ey = if (kind.isFlying) groundY + 5.5f + rng.nextFloat() * 3.5f else groundY
                val initialGoal = defaultGoalFor(kind)
                entities.add(
                    VoxelEntity(
                        id = nextId++,
                        kind = kind,
                        x = ex,
                        y = ey,
                        z = ez,
                        yaw = rng.nextFloat() * 6.28f,
                        targetYaw = rng.nextFloat() * 6.28f,
                        goal = initialGoal,
                        goalTimer = 3f + rng.nextFloat() * 6f,
                        friendshipLevel = if (kind.isCompanion) 55 else 25,
                        isFriend = kind.isCompanion,
                        targetX = ex,
                        targetZ = ez,
                        buildCooldown = 14f + rng.nextFloat() * 14f
                    )
                )
            }
        }
    }

    private fun defaultGoalFor(kind: EntityKind): AiGoal = when (kind) {
        EntityKind.RAJA -> AiGoal.ROYAL_PATROL
        EntityKind.PUJARI -> AiGoal.DO_PUJA
        EntityKind.MAJDUR -> AiGoal.BUILD_HOUSE
        EntityKind.AMIR_SETH -> AiGoal.TRADE_WORK
        EntityKind.GARIB_KISAN -> AiGoal.FARM_WORK
        else -> AiGoal.WANDER
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
            connected = kind.isCompanion,
            goal = defaultGoalFor(kind),
            friendshipLevel = if (kind.isCompanion) 55 else 30,
            isFriend = kind.isCompanion,
            targetX = sx,
            targetZ = sz
        )
        synchronized(lock) {
            if (entities.size >= MAX_ENTITIES) {
                val oldestIdx = entities.indexOfFirst { it.kind == kind && !it.connected && !it.isFriend }
                    .takeIf { it >= 0 } ?: 0
                entities.removeAt(oldestIdx)
            }
            entities.add(entity)
            spawnBurstLocked(sx, sy + 1.0f, sz, 0.35f, 1.0f, 0.65f, 8)
        }
        return entity
    }

    /**
     * Spawns an Admin-created or uploaded 3D Character (Shop Hero) or Hostile Enemy (Dushman)
     * directly into the live 3D voxel world with its real parsed .obj mesh & custom combat/voice stats!
     */
    fun spawnCustomModelInFront(
        world: VoxelChunkWorld,
        player: VoxelPlayerController,
        model: AdminCustom3DModel,
        parsedMesh: Parsed3DModelMesh?,
        distance: Float = 4.5f
    ): VoxelEntity {
        val (fx, _, fz) = player.forwardVector()
        val len = hypot(fx, fz).coerceAtLeast(0.01f)
        val sx = player.x + (fx / len) * distance
        val sz = player.z + (fz / len) * distance
        val sy = world.findSpawnY(floor(sx).toInt(), floor(sz).toInt())
        val isEnemy = model.role == CustomModelRole.ENEMY_DUSHMAN
        val kind = if (isEnemy) EntityKind.CUSTOM_ENEMY else EntityKind.CUSTOM_HERO
        val entity = VoxelEntity(
            id = nextId++,
            kind = kind,
            x = sx,
            y = sy,
            z = sz,
            yaw = player.yaw + 3.14159f,
            hp = model.maxHealth.coerceAtLeast(30),
            maxHpOverride = model.maxHealth.coerceAtLeast(30),
            connected = !isEnemy,
            isFriend = !isEnemy,
            friendshipLevel = if (isEnemy) 0 else 65,
            goal = if (isEnemy) AiGoal.WANDER else AiGoal.FOLLOW_FRIEND,
            customName = model.name,
            customModelId = model.id,
            customMesh = parsedMesh,
            customPrimaryHex = model.primaryColorHex,
            customAccentHex = model.accentColorHex,
            customDamage = model.attackDamage.coerceIn(1, 5),
            customSpeed = model.moveSpeed.coerceIn(1.2f, 5.2f),
            customScale = model.scale.coerceIn(0.65f, 1.8f),
            customGreeting = model.hindiGreeting
        )
        synchronized(lock) {
            if (entities.size >= MAX_ENTITIES) {
                entities.removeFirstOrNull()
            }
            entities.add(entity)
            if (isEnemy) {
                spawnBurstLocked(sx, sy + 1.2f, sz, 1.0f, 0.28f, 0.14f, 16)
            } else {
                spawnBurstLocked(sx, sy + 1.2f, sz, 0.25f, 0.92f, 1.0f, 14)
            }
        }
        return entity
    }

    fun triggerEntityTalking(entityId: Int, durationSec: Float = 4.2f) {
        synchronized(lock) {
            entities.firstOrNull { it.id == entityId }?.let { e ->
                e.talkingTimer = durationSec
            }
        }
    }

    /**
     * Returns ALL nearby interactive Characters & Animals within range (sorted closest-first)
     * so the player can explicitly CHOOSE who to talk to — nobody auto-talks uninvited!
     */
    fun findNearbyInteractiveEntities(player: VoxelPlayerController, maxRange: Float = 7.5f): List<TalkableNpcSnapshot> {
        synchronized(lock) {
            val matches = ArrayList<Pair<Float, TalkableNpcSnapshot>>()
            for (e in entities) {
                if (e.kind.isHostile) continue
                val d = hypot(e.x - player.x, e.z - player.z)
                if (d <= maxRange) {
                    val isAnimal = e.kind.category == EntityCategory.ANIMALS
                    val behaviorLabel = if (isAnimal) {
                        when (e.kind) {
                            EntityKind.COW, EntityKind.BUFFALO, EntityKind.BULL -> "शांत खड़े हैं • हेलो कहें"
                            EntityKind.DOG -> "पूंछ हिलाकर रुका है • हेलो कहें"
                            EntityKind.CAT -> "पास आकर रुक गई है • हेलो कहें"
                            EntityKind.HORSE -> "रुक कर इंतज़ार कर रहा है"
                            EntityKind.ELEPHANT -> "शाही अंदाज़ में रुका है"
                            else -> "पास मंडरा रही है"
                        }
                    } else {
                        e.goal.labelHindi
                    }
                    matches.add(
                        d to TalkableNpcSnapshot(
                            id = e.id,
                            kind = e.kind,
                            displayName = e.effectiveName,
                            goalLabelHindi = behaviorLabel,
                            friendshipLevel = e.friendshipLevel,
                            isFriend = e.isFriend || e.connected,
                            distanceBlocks = d.toInt(),
                            isAnimal = isAnimal,
                            customGreeting = e.customGreeting
                        )
                    )
                }
            }
            return matches.sortedBy { it.first }.map { it.second }
        }
    }

    fun findNearestProximityEntity(player: VoxelPlayerController, maxRange: Float = 7.5f): TalkableNpcSnapshot? {
        return findNearbyInteractiveEntities(player, maxRange).firstOrNull()
    }

    fun getTalkableSnapshots(player: VoxelPlayerController): List<TalkableNpcSnapshot> {
        synchronized(lock) {
            return entities
                .filter { !it.kind.isHostile && (it.kind.isTalkableAi || it.kind.category == EntityCategory.ANIMALS) }
                .map { e ->
                    val d = hypot(e.x - player.x, e.z - player.z).toInt()
                    val isAnimal = e.kind.category == EntityCategory.ANIMALS
                    TalkableNpcSnapshot(
                        id = e.id,
                        kind = e.kind,
                        displayName = e.effectiveName,
                        goalLabelHindi = if (isAnimal) "पशु मित्र (Animal Behavior)" else e.goal.labelHindi,
                        friendshipLevel = e.friendshipLevel,
                        isFriend = e.isFriend || e.connected,
                        distanceBlocks = d,
                        isAnimal = isAnimal,
                        customGreeting = e.customGreeting
                    )
                }
                .sortedWith(compareBy<TalkableNpcSnapshot> { it.isAnimal }.thenBy { it.distanceBlocks })
        }
    }

    fun boostFriendship(entityId: Int, delta: Int = 20): TalkableNpcSnapshot? {
        synchronized(lock) {
            val e = entities.firstOrNull { it.id == entityId } ?: return null
            e.friendshipLevel = (e.friendshipLevel + delta).coerceIn(0, 100)
            if (e.friendshipLevel >= 50) {
                e.isFriend = true
                e.connected = true
            }
            e.talkingTimer = 2.8f
            spawnBurstLocked(e.x, e.y + e.kind.height + 0.3f, e.z, 1.0f, 0.35f, 0.58f, 10)
            return TalkableNpcSnapshot(
                id = e.id,
                kind = e.kind,
                displayName = e.effectiveName,
                goalLabelHindi = e.goal.labelHindi,
                friendshipLevel = e.friendshipLevel,
                isFriend = e.isFriend,
                distanceBlocks = 0,
                isAnimal = e.kind.category == EntityCategory.ANIMALS,
                customGreeting = e.customGreeting
            )
        }
    }

    /**
     * Executes an autonomous or voice-commanded world action for ANY character
     * (Build House, Build Mandir, Bring Flowing Water Here, Plant Trees, Go to Puja, Follow Friend)!
     */
    fun applyNpcDecision(
        entityId: Int,
        goal: AiGoal,
        world: VoxelChunkWorld,
        player: VoxelPlayerController
    ): String? {
        synchronized(lock) {
            val e = entities.firstOrNull { it.id == entityId } ?: return null
            e.goal = goal
            e.goalTimer = 20f
            e.talkingTimer = 4.2f
            val (fx, _, fz) = player.forwardVector()
            val fLen = hypot(fx, fz).coerceAtLeast(0.01f)
            val frontX = floor(player.x + (fx / fLen) * 5.5f).toInt()
            val frontZ = floor(player.z + (fz / fLen) * 5.5f).toInt()

            when (goal) {
                AiGoal.FOLLOW_FRIEND -> {
                    e.isFriend = true
                    e.connected = true
                    e.friendshipLevel = (e.friendshipLevel + 15).coerceAtMost(100)
                    spawnBurstLocked(e.x, e.y + 1.8f, e.z, 1.0f, 0.35f, 0.55f, 10)
                    return "${e.effectiveName} अब आपके पक्के दोस्त बनकर साथ चल रहे हैं!"
                }
                AiGoal.BUILD_HOUSE -> {
                    world.buildMiniStructure(frontX, frontZ, isMandir = false)
                    e.targetX = frontX.toFloat()
                    e.targetZ = frontZ.toFloat()
                    spawnBurstLocked(frontX.toFloat(), world.findSpawnY(frontX, frontZ) + 2f, frontZ.toFloat(), 1.0f, 0.85f, 0.3f, 18)
                    return "${e.effectiveName} ने आपके सामने नया पक्का घर बना दिया!"
                }
                AiGoal.BUILD_MANDIR -> {
                    world.buildMiniStructure(frontX, frontZ, isMandir = true)
                    e.targetX = frontX.toFloat()
                    e.targetZ = frontZ.toFloat()
                    spawnPujaSparklesLocked(frontX.toFloat(), world.findSpawnY(frontX, frontZ) + 2f, frontZ.toFloat())
                    return "${e.effectiveName} ने आपके सामने भव्य पावन मंदिर बना दिया!"
                }
                AiGoal.BRING_WATER -> {
                    world.bringWaterChannelTo(floor(e.x).toInt(), floor(e.z).toInt(), frontX, frontZ)
                    world.createWaterSpringOrRiver(frontX, frontZ, radius = 3, waterfall = false)
                    spawnBurstLocked(frontX.toFloat(), world.findSpawnY(frontX, frontZ) + 1.2f, frontZ.toFloat(), 0.25f, 0.78f, 1.0f, 20)
                    return "${e.effectiveName} ने यहाँ बहते हुए असली पानी की धारा और तालाब बना दिया!"
                }
                AiGoal.PLANT_TREES -> {
                    world.plantForestAround(frontX, frontZ, count = 8)
                    spawnBurstLocked(frontX.toFloat(), world.findSpawnY(frontX, frontZ) + 2.4f, frontZ.toFloat(), 0.22f, 0.88f, 0.35f, 18)
                    return "${e.effectiveName} ने यहाँ हरे-भरे पेड़ और बगीचा लगा दिया!"
                }
                AiGoal.DO_PUJA -> {
                    e.targetX = world.mandirCenterX.toFloat()
                    e.targetZ = world.mandirCenterZ + 3.2f
                    spawnPujaSparklesLocked(e.x, e.y + 1.4f, e.z)
                    return "${e.effectiveName} मंदिर में पूजा करने जा रहे हैं!"
                }
                AiGoal.ROYAL_PATROL -> {
                    e.targetX = world.palaceCenterX.toFloat()
                    e.targetZ = world.palaceCenterZ + 3.2f
                    return "${e.effectiveName} राजमहल की ओर जा रहे हैं!"
                }
                else -> {
                    e.targetX = player.x + (rng.nextFloat() - 0.5f) * 14f
                    e.targetZ = player.z + (rng.nextFloat() - 0.5f) * 14f
                    return null
                }
            }
        }
    }

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
                    if (e.kind.isTalkableAi) {
                        e.isFriend = true
                        e.friendshipLevel = (e.friendshipLevel + 20).coerceAtMost(100)
                    }
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
                if (particles.size < 220) {
                    particles.add(
                        VoxelParticle(
                            x = x + (rng.nextFloat() - 0.5f) * 0.7f,
                            y = y + 0.25f,
                            z = z + (rng.nextFloat() - 0.5f) * 0.7f,
                            vx = (rng.nextFloat() - 0.5f) * 3.8f,
                            vy = 2.8f + rng.nextFloat() * 3.4f,
                            vz = (rng.nextFloat() - 0.5f) * 3.8f,
                            r = 0.72f, g = 0.94f, b = 1.0f,
                            size = 0.10f + rng.nextFloat() * 0.06f,
                            life = 0.45f + rng.nextFloat() * 0.25f
                        )
                    )
                }
            }
        }
    }

    fun spawnLavaEmberParticles(x: Float, y: Float, z: Float) {
        synchronized(lock) {
            repeat(10) {
                if (particles.size < 220) {
                    particles.add(
                        VoxelParticle(
                            x = x + (rng.nextFloat() - 0.5f) * 1.1f,
                            y = y + 0.2f,
                            z = z + (rng.nextFloat() - 0.5f) * 1.1f,
                            vx = (rng.nextFloat() - 0.5f) * 2.4f,
                            vy = 2.6f + rng.nextFloat() * 3.6f,
                            vz = (rng.nextFloat() - 0.5f) * 2.4f,
                            r = 1.0f, g = 0.52f + rng.nextFloat() * 0.35f, b = 0.10f,
                            size = 0.10f + rng.nextFloat() * 0.05f,
                            life = 0.55f + rng.nextFloat() * 0.45f,
                            floatsUp = true
                        )
                    )
                }
            }
        }
    }

    fun spawnSteamBurst(x: Float, y: Float, z: Float) {
        synchronized(lock) {
            repeat(12) {
                if (particles.size < 220) {
                    particles.add(
                        VoxelParticle(
                            x = x + (rng.nextFloat() - 0.5f) * 0.8f,
                            y = y,
                            z = z + (rng.nextFloat() - 0.5f) * 0.8f,
                            vx = (rng.nextFloat() - 0.5f) * 1.2f,
                            vy = 2.0f + rng.nextFloat() * 2.0f,
                            vz = (rng.nextFloat() - 0.5f) * 1.2f,
                            r = 0.92f, g = 0.94f, b = 0.98f,
                            size = 0.14f,
                            life = 0.75f,
                            floatsUp = true
                        )
                    )
                }
            }
        }
    }

    private fun spawnPujaSparklesLocked(x: Float, y: Float, z: Float) {
        repeat(8) {
            if (particles.size < 220) {
                particles.add(
                    VoxelParticle(
                        x = x + (rng.nextFloat() - 0.5f) * 1.2f,
                        y = y,
                        z = z + (rng.nextFloat() - 0.5f) * 1.2f,
                        vx = (rng.nextFloat() - 0.5f) * 0.8f,
                        vy = 1.6f + rng.nextFloat() * 1.5f,
                        vz = (rng.nextFloat() - 0.5f) * 0.8f,
                        r = 1.0f, g = 0.86f, b = 0.22f,
                        size = 0.11f,
                        life = 0.85f,
                        floatsUp = true
                    )
                )
            }
        }
    }

    fun spawnUnderwaterBubble(x: Float, y: Float, z: Float) {
        synchronized(lock) {
            if (particles.size < 220) {
                particles.add(
                    VoxelParticle(
                        x = x + (rng.nextFloat() - 0.5f) * 1.4f,
                        y = y - 0.4f,
                        z = z + (rng.nextFloat() - 0.5f) * 1.4f,
                        vx = (rng.nextFloat() - 0.5f) * 0.4f,
                        vy = 2.4f + rng.nextFloat() * 1.6f,
                        vz = (rng.nextFloat() - 0.5f) * 0.4f,
                        r = 0.78f, g = 0.96f, b = 1.0f,
                        size = 0.09f,
                        life = 0.85f,
                        floatsUp = true
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
                    vz = fz * BULLET_SPEED,
                    damage = blasterDamage
                )
            )
            // Bright 3D Muzzle Flash + fiery recoil sparks!
            repeat(10) { idx ->
                val isCore = idx < 3
                particles.add(
                    VoxelParticle(
                        x = startX + fx * 0.18f,
                        y = startY + fy * 0.18f,
                        z = startZ + fz * 0.18f,
                        vx = fx * (if (isCore) 7f else 11f) + (rng.nextFloat() - 0.5f) * 3.4f,
                        vy = fy * 6f + (rng.nextFloat() - 0.35f) * 3.0f,
                        vz = fz * (if (isCore) 7f else 11f) + (rng.nextFloat() - 0.5f) * 3.4f,
                        r = 1.0f,
                        g = if (isCore) 0.96f else 0.58f,
                        b = if (isCore) 0.45f else 0.10f,
                        size = if (isCore) 0.16f else 0.085f,
                        life = if (isCore) 0.12f else 0.24f,
                        floatsUp = !isCore
                    )
                )
            }
        }
    }

    fun spawnWaterSitRipples(x: Float, y: Float, z: Float) {
        synchronized(lock) {
            repeat(8) { i ->
                if (particles.size < 240) {
                    val ang = (i / 8f) * 6.283f + rng.nextFloat() * 0.25f
                    particles.add(
                        VoxelParticle(
                            x = x + cos(ang) * 0.52f,
                            y = y,
                            z = z + sin(ang) * 0.52f,
                            vx = cos(ang) * 1.45f,
                            vy = 0.08f,
                            vz = sin(ang) * 1.45f,
                            r = 0.78f, g = 0.96f, b = 1.0f,
                            size = 0.09f,
                            life = 0.55f,
                            floatsUp = true
                        )
                    )
                }
            }
        }
    }

    fun spawnBikeExhaustParticles(x: Float, y: Float, z: Float, nitro: Boolean, isElectric: Boolean) {
        synchronized(lock) {
            val count = if (nitro) 6 else 3
            repeat(count) {
                if (particles.size < 240) {
                    val r = when {
                        isElectric -> 0.08f
                        nitro -> 1.0f
                        else -> 0.76f
                    }
                    val g = when {
                        isElectric -> 0.92f
                        nitro -> 0.52f
                        else -> 0.78f
                    }
                    val b = when {
                        isElectric -> 1.0f
                        nitro -> 0.14f
                        else -> 0.82f
                    }
                    particles.add(
                        VoxelParticle(
                            x = x + (rng.nextFloat() - 0.5f) * 0.25f,
                            y = y + rng.nextFloat() * 0.12f,
                            z = z + (rng.nextFloat() - 0.5f) * 0.25f,
                            vx = (rng.nextFloat() - 0.5f) * 1.2f,
                            vy = 0.6f + rng.nextFloat() * 0.9f,
                            vz = (rng.nextFloat() - 0.5f) * 1.2f,
                            r = r, g = g, b = b,
                            size = if (nitro) 0.13f else 0.09f,
                            life = if (nitro) 0.32f else 0.42f,
                            floatsUp = true
                        )
                    )
                }
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
        var pujaBellTriggered = false
        var humanWorkSoundTriggered = false
        var aiActivityToast: String? = null

        synchronized(lock) {
            globalAnimClock += dt
            pujaBellCooldown = (pujaBellCooldown - dt).coerceAtLeast(0f)
            humanWorkSoundCooldown = (humanWorkSoundCooldown - dt).coerceAtLeast(0f)

            if (playerHealth < 10) {
                healthRegenTimer += dt
                if (healthRegenTimer >= 4.0f) {
                    healthRegenTimer = 0f
                    playerHealth = (playerHealth + 1).coerceAtMost(10)
                }
            }

            // Volcano summit glowing embers!
            if (hypot(player.x - world.volcanoCenterX, player.z - world.volcanoCenterZ) < 42f && rng.nextFloat() < dt * 4.5f) {
                particles.add(
                    VoxelParticle(
                        x = world.volcanoCenterX + (rng.nextFloat() - 0.5f) * 6f,
                        y = 33.5f,
                        z = world.volcanoCenterZ + (rng.nextFloat() - 0.5f) * 6f,
                        vx = (rng.nextFloat() - 0.5f) * 2.2f,
                        vy = 3.2f + rng.nextFloat() * 3.5f,
                        vz = (rng.nextFloat() - 0.5f) * 2.2f,
                        r = 1.0f, g = 0.45f + rng.nextFloat() * 0.4f, b = 0.08f,
                        size = 0.16f, life = 1.6f, floatsUp = true
                    )
                )
            }

            zombieAutoSpawnTimer += dt
            val zombieCount = entities.count { it.kind == EntityKind.ZOMBIE }
            val maxAutoZombies = if (isNight) 5 else 2
            if (zombieAutoSpawnTimer >= 12f && zombieCount < maxAutoZombies && entities.size < MAX_ENTITIES) {
                zombieAutoSpawnTimer = 0f
                val ang = rng.nextFloat() * 6.283f
                val zx = player.x + cos(ang) * 20f
                val zz = player.z + sin(ang) * 20f
                val zy = world.findSpawnY(floor(zx).toInt(), floor(zz).toInt())
                entities.add(VoxelEntity(nextId++, EntityKind.ZOMBIE, zx, zy, zz, yaw = ang))
            }

            // 1. Update bullets
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

                    if (world.isSolid(floor(b.x).toInt(), floor(b.y).toInt(), floor(b.z).toInt())) {
                        spawnBurstLocked(b.x, b.y, b.z, 1.0f, 0.85f, 0.35f, 6)
                        consumed = true
                        break
                    }

                    val hitZombie = entities.firstOrNull { e ->
                        e.kind.isHostile &&
                            hypot(e.x - b.x, e.z - b.z) <= (e.kind.radius + 0.42f) &&
                            b.y in (e.y - 0.1f)..(e.y + e.kind.height + 0.25f)
                    }
                    if (hitZombie != null) {
                        hitZombie.hp -= b.damage
                        hitZombie.hitFlash = 0.28f
                        val kLen = hypot(b.vx, b.vz).coerceAtLeast(0.01f)
                        hitZombie.x += (b.vx / kLen) * 0.55f
                        hitZombie.z += (b.vz / kLen) * 0.55f
                        hitZombie.vy = 3.6f
                        zombieHits++
                        // Vivid crimson + golden damage spark burst on hit!
                        spawnBurstLocked(b.x, b.y, b.z, 1.0f, 0.22f, 0.18f, 10)
                        spawnBurstLocked(b.x, b.y + 0.2f, b.z, 1.0f, 0.84f, 0.22f, 5)
                        if (hitZombie.hp <= 0) {
                            spawnBurstLocked(hitZombie.x, hitZombie.y + 1f, hitZombie.z, 1.0f, 0.45f, 0.12f, 22)
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

            // Determine the closest interactive character within 5.4 blocks so they politely STOP and face the player waiting for "Hello"!
            var closestInteractiveId = -1
            var closestInteractiveDist = 5.4f
            for (e in entities) {
                if (e.kind.isHostile || e.kind.isFlying) continue
                val d = hypot(player.x - e.x, player.z - e.z)
                if (d < closestInteractiveDist) {
                    closestInteractiveDist = d
                    closestInteractiveId = e.id
                }
            }
            val chosenFocusId = preferredTalkTargetId

            // 2. Autonomous AI Decision Loop for Society, Heroes, Animals, Birds, Zombies & Custom 3D Models
            val iter = entities.iterator()
            while (iter.hasNext()) {
                val e = iter.next()
                e.hitFlash = (e.hitFlash - dt).coerceAtLeast(0f)
                e.attackCooldown = (e.attackCooldown - dt).coerceAtLeast(0f)
                e.buildCooldown = (e.buildCooldown - dt).coerceAtLeast(0f)
                e.talkingTimer = (e.talkingTimer - dt).coerceAtLeast(0f)

                val dxP = player.x - e.x
                val dzP = player.z - e.z
                val distP = hypot(dxP, dzP)

                // Check if player walked up to this character to talk — STOP & politely face player waiting for "Hello"!
                val isStoppedWaitingForHello = !e.kind.isHostile &&
                    !e.kind.isFlying &&
                    distP < 5.6f &&
                    (e.id == chosenFocusId || e.id == closestInteractiveId || e.talkingTimer > 0f)

                if (isStoppedWaitingForHello) {
                    e.targetYaw = atan2(dxP, -dzP)
                    e.yaw = lerpAngle(e.yaw, e.targetYaw, dt * 7.5f)
                }

                if (distP > 56f && e.kind.category != EntityCategory.SOCIETY && e.customModelId == null) {
                    val ang = rng.nextFloat() * 6.283f
                    e.x = player.x + cos(ang) * 22f
                    e.z = player.z + sin(ang) * 22f
                    val gy = world.findSpawnY(floor(e.x).toInt(), floor(e.z).toInt())
                    e.y = if (e.kind.isFlying) gy + 6f else gy
                    continue
                }

                if (e.kind.isFlying) {
                    e.aiTimer -= dt
                    if (e.aiTimer <= 0f) {
                        e.aiTimer = 1.6f + rng.nextFloat() * 2.4f
                        e.targetYaw = if (distP > 24f) atan2(dxP, -dzP) else e.yaw + (rng.nextFloat() - 0.5f) * 2.2f
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
                val entityBaseSpeed = e.customSpeed ?: e.kind.speed

                when {
                    isStoppedWaitingForHello -> {
                        // Stand still attentively facing the player until they say Hello or walk away!
                        moveSpeed = 0f
                    }

                    e.kind.isHostile -> {
                        if (distP < 30f) {
                            e.targetYaw = atan2(dxP, -dzP)
                            e.yaw = lerpAngle(e.yaw, e.targetYaw, dt * 6.5f)
                            if (distP > 1.35f) {
                                moveSpeed = entityBaseSpeed
                            } else if (!creativeMode && e.attackCooldown <= 0f) {
                                e.attackCooldown = 1.25f
                                playerHealth = (playerHealth - e.customDamage).coerceAtLeast(1)
                                playerDamaged = true
                                spawnBurstLocked(player.x, player.y + 1.1f, player.z, 1.0f, 0.15f, 0.15f, 8)
                            }
                        } else {
                            e.aiTimer -= dt
                            if (e.aiTimer <= 0f) {
                                e.aiTimer = 2.5f + rng.nextFloat() * 2f
                                e.targetYaw = rng.nextFloat() * 6.283f
                            }
                            e.yaw = lerpAngle(e.yaw, e.targetYaw, dt * 2.5f)
                            moveSpeed = entityBaseSpeed * 0.45f
                        }
                    }

                    e.kind.category == EntityCategory.SOCIETY && !e.connected -> {
                        // AUTONOMOUS HUMAN BRAIN ("Apna Dimag"):
                        // Independently tracks Energy, Social Need, Time of Day, and Landmarks!
                        e.goalTimer -= dt
                        e.socialNeed = (e.socialNeed + dt * 2.2f).coerceAtMost(100f)
                        if (e.goal == AiGoal.REST_SEAT) {
                            e.isResting = true
                            e.energy = (e.energy + dt * 9.5f).coerceAtMost(100f)
                        } else {
                            e.isResting = false
                            e.energy = (e.energy - dt * 1.35f).coerceAtLeast(5f)
                        }

                        if (e.goalTimer <= 0f) {
                            chooseAutonomousSocietyGoalLocked(e, world, isNight)
                        }

                        // Autonomous Human-to-Human Socializing (NPCs chat & wave to each other!)
                        if (e.goal == AiGoal.CHAT_NPC) {
                            val partner = entities.firstOrNull { it.id == e.socialPartnerId }
                            if (partner != null) {
                                e.targetX = partner.x
                                e.targetZ = partner.z
                            }
                        }

                        val tdx = e.targetX - e.x
                        val tdz = e.targetZ - e.z
                        val tDist = hypot(tdx, tdz)

                        if (e.goal == AiGoal.REST_SEAT) {
                            // Sitting comfortably & recharging energy
                            moveSpeed = 0f
                            e.animPhase += dt * 1.2f
                        } else if (tDist > (if (e.goal == AiGoal.CHAT_NPC) 2.2f else 1.8f)) {
                            e.targetYaw = atan2(tdx, -tdz)
                            e.yaw = lerpAngle(e.yaw, e.targetYaw, dt * 4.2f)
                            moveSpeed = e.kind.speed * 0.85f
                        } else {
                            // Reached autonomous destination — perform intelligent human action!
                            e.animPhase += dt * 3.8f
                            when (e.goal) {
                                AiGoal.CHAT_NPC -> {
                                    e.socialNeed = (e.socialNeed - dt * 16f).coerceAtLeast(0f)
                                    val partner = entities.firstOrNull { it.id == e.socialPartnerId }
                                    if (partner != null) {
                                        e.targetYaw = atan2(partner.x - e.x, -(partner.z - e.z))
                                        e.yaw = lerpAngle(e.yaw, e.targetYaw, dt * 6f)
                                        if (e.talkingTimer <= 0f) e.talkingTimer = 2.2f
                                        if (partner.talkingTimer <= 0f) partner.talkingTimer = 2.2f
                                        if (rng.nextFloat() < dt * 1.5f) {
                                            spawnBurstLocked(e.x, e.y + 2.0f, e.z, 0.38f, 1.0f, 0.68f, 2)
                                        }
                                    }
                                }
                                AiGoal.WATER_RELAX -> {
                                    e.energy = (e.energy + dt * 5f).coerceAtMost(100f)
                                    if (rng.nextFloat() < dt * 1.4f) {
                                        spawnBurstLocked(e.x, e.y + 0.35f, e.z, 0.55f, 0.90f, 1.0f, 2)
                                    }
                                }
                                AiGoal.DO_PUJA -> {
                                    if (rng.nextFloat() < dt * 2.0f) {
                                        spawnPujaSparklesLocked(e.x, e.y + 1.5f, e.z)
                                    }
                                    if (pujaBellCooldown <= 0f && distP < 24f) {
                                        pujaBellCooldown = 14f
                                        pujaBellTriggered = true
                                    }
                                }
                                AiGoal.BUILD_HOUSE -> {
                                    if (humanWorkSoundCooldown <= 0f && distP < 24f) {
                                        humanWorkSoundCooldown = 3.2f
                                        humanWorkSoundTriggered = true
                                    }
                                    if (e.buildCooldown <= 0f && distP < 34f) {
                                        e.buildCooldown = 55f
                                        val bx = floor(e.x + 3f).toInt()
                                        val bz = floor(e.z + 3f).toInt()
                                        if (hypot((bx - player.x).toDouble(), (bz - player.z).toDouble()) > 4.5) {
                                            world.buildMiniStructure(bx, bz, isMandir = false)
                                            spawnBurstLocked(bx.toFloat(), e.y + 2f, bz.toFloat(), 1.0f, 0.85f, 0.35f, 12)
                                            aiActivityToast = "${e.kind.displayName} ने खुद नया घर बनाया!"
                                        }
                                        e.goal = AiGoal.REST_SEAT
                                        e.goalTimer = 7f
                                    }
                                }
                                AiGoal.BUILD_MANDIR -> {
                                    if (humanWorkSoundCooldown <= 0f && distP < 24f) {
                                        humanWorkSoundCooldown = 3.2f
                                        humanWorkSoundTriggered = true
                                    }
                                    if (e.buildCooldown <= 0f && distP < 34f) {
                                        e.buildCooldown = 75f
                                        val bx = floor(e.x + 4f).toInt()
                                        val bz = floor(e.z - 4f).toInt()
                                        if (hypot((bx - player.x).toDouble(), (bz - player.z).toDouble()) > 5.0) {
                                            world.buildMiniStructure(bx, bz, isMandir = true)
                                            spawnPujaSparklesLocked(bx.toFloat(), e.y + 2f, bz.toFloat())
                                            pujaBellTriggered = true
                                            aiActivityToast = "${e.kind.displayName} ने खुद नया मंदिर बनाया!"
                                        }
                                        e.goal = AiGoal.DO_PUJA
                                    }
                                }
                                AiGoal.FARM_WORK, AiGoal.TRADE_WORK -> {
                                    if (humanWorkSoundCooldown <= 0f && distP < 18f && rng.nextFloat() < dt * 0.6f) {
                                        humanWorkSoundCooldown = 4.5f
                                        humanWorkSoundTriggered = true
                                    }
                                }
                                else -> Unit
                            }
                        }
                    }

                    e.kind.isCompanion || e.connected -> {
                        val nearZombie = if (e.kind.isCompanion) {
                            entities.firstOrNull { z ->
                                z.kind.isHostile && hypot(z.x - e.x, z.z - e.z) < 9.5f
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
                                nearZombie.hp -= 22
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

                // Ground locomotion + smart 1-block climb
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
                        e.vy = 6.6f
                        e.x = nx
                        e.z = nz
                    } else {
                        e.targetYaw += 1.9f
                    }
                    e.animPhase += dt * (moveSpeed * 3.2f)
                }

                // Gravity & exact top-of-block landing (entities also never sink!)
                e.vy = (e.vy - 20f * dt).coerceAtLeast(-22f)
                val nextY = e.y + e.vy * dt
                val gx = floor(e.x).toInt()
                val gz = floor(e.z).toInt()
                val belowSolid = world.isSolid(gx, floor(nextY).toInt(), gz)
                if (!belowSolid && nextY > 1.0f) {
                    e.y = nextY
                } else {
                    if (e.vy < 0f) {
                        e.y = (floor(nextY) + 1.005f).coerceAtLeast(1.005f)
                    }
                    e.vy = 0f
                }
            }

            entities.removeAll { it.kind.isHostile && it.hp <= 0 }

            // 3. Update Particles
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
                if (!p.floatsUp) {
                    p.vy -= 11f * dt
                }
            }
        }

        return CombatEvents(
            zombieHits = zombieHits,
            zombieKills = zombieKills,
            playerDamaged = playerDamaged,
            pujaBellTriggered = pujaBellTriggered,
            humanWorkSoundTriggered = humanWorkSoundTriggered,
            aiActivityToast = aiActivityToast
        )
    }

    private fun chooseAutonomousSocietyGoalLocked(e: VoxelEntity, world: VoxelChunkWorld, isNight: Boolean) {
        e.goalTimer = 8f + rng.nextFloat() * 9f
        e.socialPartnerId = null

        // 1. Human Brain Need Check: Tired -> Rest & sit down!
        if (e.energy < 28f || (isNight && rng.nextFloat() < 0.45f)) {
            e.goal = AiGoal.REST_SEAT
            e.goalTimer = 6.5f + rng.nextFloat() * 4.5f
            return
        }

        // 2. Human Brain Need Check: Socialize -> Walk over to chat with another human NPC!
        if (e.socialNeed > 68f && rng.nextFloat() < 0.55f) {
            val friend = entities.firstOrNull { other ->
                other.id != e.id &&
                    !other.kind.isHostile &&
                    other.kind.isTalkableAi &&
                    hypot(other.x - e.x, other.z - e.z) < 22f
            }
            if (friend != null) {
                e.goal = AiGoal.CHAT_NPC
                e.socialPartnerId = friend.id
                e.targetX = friend.x
                e.targetZ = friend.z
                e.goalTimer = 9f
                return
            }
        }

        // 3. Relax by Waterfront / River occasionally
        val roll = rng.nextFloat()
        if (roll < 0.14f) {
            e.goal = AiGoal.WATER_RELAX
            e.targetX = 28f + (rng.nextFloat() - 0.5f) * 14f
            e.targetZ = 22f + (rng.nextFloat() - 0.5f) * 14f
            return
        }

        when (e.kind) {
            EntityKind.RAJA -> {
                if (roll < 0.58f) {
                    e.goal = AiGoal.ROYAL_PATROL
                    e.targetX = world.palaceCenterX + (rng.nextFloat() - 0.5f) * 14f
                    e.targetZ = world.palaceCenterZ + 3f + (rng.nextFloat() - 0.5f) * 10f
                } else {
                    e.goal = AiGoal.DO_PUJA
                    e.targetX = world.mandirCenterX + (rng.nextFloat() - 0.5f) * 4f
                    e.targetZ = world.mandirCenterZ + 4f
                }
            }
            EntityKind.PUJARI -> {
                if (roll < 0.72f) {
                    e.goal = AiGoal.DO_PUJA
                    e.targetX = world.mandirCenterX + (rng.nextFloat() - 0.5f) * 3f
                    e.targetZ = world.mandirCenterZ + 3.5f
                } else {
                    e.goal = AiGoal.BUILD_MANDIR
                    e.targetX = world.mandirCenterX + (rng.nextFloat() - 0.5f) * 18f
                    e.targetZ = world.mandirCenterZ + (rng.nextFloat() - 0.5f) * 18f
                }
            }
            EntityKind.MAJDUR -> {
                if (roll < 0.60f) {
                    e.goal = AiGoal.BUILD_HOUSE
                    e.targetX = 8f + (rng.nextFloat() - 0.5f) * 28f
                    e.targetZ = 8f + (rng.nextFloat() - 0.5f) * 28f
                } else {
                    e.goal = AiGoal.TRADE_WORK
                    e.targetX = world.palaceCenterX + (rng.nextFloat() - 0.5f) * 12f
                    e.targetZ = world.palaceCenterZ + (rng.nextFloat() - 0.5f) * 12f
                }
            }
            EntityKind.AMIR_SETH -> {
                if (roll < 0.65f) {
                    e.goal = AiGoal.TRADE_WORK
                    e.targetX = (world.palaceCenterX + world.mandirCenterX) * 0.5f + (rng.nextFloat() - 0.5f) * 16f
                    e.targetZ = (world.palaceCenterZ + world.mandirCenterZ) * 0.5f + (rng.nextFloat() - 0.5f) * 16f
                } else {
                    e.goal = AiGoal.DO_PUJA
                    e.targetX = world.mandirCenterX + 1f
                    e.targetZ = world.mandirCenterZ + 4f
                }
            }
            EntityKind.GARIB_KISAN -> {
                if (roll < 0.65f) {
                    e.goal = AiGoal.FARM_WORK
                    e.targetX = 8f + (rng.nextFloat() - 0.5f) * 24f
                    e.targetZ = 8f + (rng.nextFloat() - 0.5f) * 24f
                } else {
                    e.goal = AiGoal.DO_PUJA
                    e.targetX = world.mandirCenterX - 1f
                    e.targetZ = world.mandirCenterZ + 4f
                }
            }
            else -> {
                e.goal = if (roll < 0.35f) AiGoal.DO_PUJA else AiGoal.WANDER
                e.targetX = if (e.goal == AiGoal.DO_PUJA) world.mandirCenterX.toFloat() else 8f + (rng.nextFloat() - 0.5f) * 24f
                e.targetZ = if (e.goal == AiGoal.DO_PUJA) world.mandirCenterZ + 4f else 8f + (rng.nextFloat() - 0.5f) * 24f
            }
        }
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
        val bike = p.activeBike
        val sittingWater = p.sittingOnWater
        val stride = if (bike != null || sittingWater) 0f else sin(p.walkBobPhase * 2f) * 0.22f * p.moveSpeedFraction
        val swimBob = when {
            sittingWater -> sin(p.waterSitBobPhase * 2.2f) * 0.045f
            p.inWater -> sin(p.walkBobPhase * 1.5f) * 0.08f
            else -> 0f
        }
        val crouchDrop = when {
            bike != null -> 0.16f
            sittingWater -> -0.30f
            p.sneaking -> -0.18f
            else -> 0f
        }
        val swingKick = sin(armSwingProgress * 3.14159f) * 0.45f
        val c = cos(p.yaw)
        val s = sin(p.yaw)
        val leanOffset = if (bike != null) sin(p.bikeLeanRad) * 0.28f else 0f

        fun bikePart(lx: Float, ly: Float, lz: Float, w: Float, h: Float, d: Float, r: Float, g: Float, b: Float) {
            val leanedX = lx + leanOffset * (ly / 1.4f).coerceIn(0f, 1.3f)
            val wx = p.x + leanedX * c + lz * s
            val wz = p.z + leanedX * s - lz * c
            emitBox(wx, p.y + ly + swimBob, wz, w, h, d, p.yaw, r, g, b)
        }

        // --- 1. Render Full 3D Motorcycle Mesh when Riding! ---
        if (bike != null) {
            val (pr, pg, pb) = hexToRgb(bike.primaryColorHex)
            val (ar, ag, ab) = hexToRgb(bike.accentColorHex)
            val (rr, rg, rb) = hexToRgb(bike.rimColorHex)
            val (hr, hg, hb) = hexToRgb(bike.headlightGlowHex)
            val wheelPulse = 0.04f * sin(p.bikeWheelSpin * 2f)

            // Rear & Front Tires (chunky dark rubber + glowing/chrome rims + spinning spoke accents!)
            bikePart(0f, 0.34f, -0.78f, 0.24f, 0.66f, 0.66f, 0.09f, 0.09f, 0.11f)
            bikePart(0f, 0.34f, -0.78f, 0.26f, 0.38f + wheelPulse, 0.38f - wheelPulse, rr, rg, rb)
            bikePart(0f, 0.34f, 0.84f, 0.22f, 0.66f, 0.66f, 0.09f, 0.09f, 0.11f)
            bikePart(0f, 0.34f, 0.84f, 0.24f, 0.38f - wheelPulse, 0.38f + wheelPulse, rr, rg, rb)
            if (bike.hasNeonWheels) {
                // Tron glowing cyan neon wheel arcs & underglow strip
                bikePart(0f, 0.34f, -0.78f, 0.28f, 0.50f, 0.12f, 0.05f, 0.96f, 1.0f)
                bikePart(0f, 0.34f, 0.84f, 0.26f, 0.50f, 0.12f, 0.05f, 0.96f, 1.0f)
                bikePart(0f, 0.24f, 0.02f, 0.34f, 0.08f, 1.40f, 0.06f, 0.92f, 1.0f)
            }

            // Engine Block + Lower Frame Chassis + Twin Chrome Exhaust Pipes
            bikePart(0f, 0.42f, 0.02f, 0.36f, 0.42f, 0.96f, 0.20f, 0.22f, 0.26f)
            bikePart(-0.22f, 0.32f, -0.58f, 0.09f, 0.10f, 0.90f, 0.72f, 0.76f, 0.82f)
            bikePart(0.22f, 0.32f, -0.58f, 0.09f, 0.10f, 0.90f, 0.72f, 0.76f, 0.82f)

            // Sculpted Fuel Tank, Side Fairings & Tail Cowl in Bike Primary + Accent Colors
            bikePart(0f, 0.76f, 0.26f, 0.42f, 0.32f, 0.68f, pr, pg, pb)
            bikePart(0f, 0.70f, -0.40f, 0.36f, 0.18f, 0.66f, ar, ag, ab) // Leather rider seat
            bikePart(0f, 0.80f, -0.78f, 0.30f, 0.16f, 0.40f, pr, pg, pb) // Sport tail section
            bikePart(0f, 0.82f, -1.00f, 0.22f, 0.10f, 0.06f, 1.0f, 0.12f, 0.16f) // Bright Red Taillight

            // Front Forks, Nose Cowl, Windscreen, Handlebars & Glowing Headlight
            bikePart(-0.14f, 0.74f, 0.74f, 0.07f, 0.78f, 0.09f, 0.78f, 0.82f, 0.88f)
            bikePart(0.14f, 0.74f, 0.74f, 0.07f, 0.78f, 0.09f, 0.78f, 0.82f, 0.88f)
            bikePart(0f, 0.92f, 0.72f, 0.38f, 0.34f, 0.34f, pr, pg, pb) // Front nose fairing
            bikePart(0f, 1.14f, 0.62f, 0.30f, 0.20f, 0.12f, 0.12f, 0.20f, 0.28f) // Tinted windscreen
            bikePart(0f, 0.94f, 0.91f, 0.24f, 0.16f, 0.08f, hr, hg, hb) // Headlight glow
            bikePart(0f, 1.06f, 0.56f, 0.78f, 0.07f, 0.08f, 0.14f, 0.16f, 0.18f) // Wide handlebars
            bikePart(-0.36f, 1.06f, 0.56f, 0.12f, 0.09f, 0.09f, ar, ag, ab)
            bikePart(0.36f, 1.06f, 0.56f, 0.12f, 0.09f, 0.09f, ar, ag, ab)
        }

        // --- 2. Render Floating Wooden Water-Lounger Raft when Sitting on Water! ---
        if (sittingWater) {
            bikePart(0f, 0.18f, 0f, 1.18f, 0.14f, 1.42f, 0.72f, 0.48f, 0.24f)
            bikePart(0f, 0.27f, -0.58f, 1.10f, 0.16f, 0.22f, 0.96f, 0.82f, 0.34f) // Golden cushion backrest
            bikePart(-0.56f, 0.24f, 0f, 0.12f, 0.14f, 1.44f, 0.52f, 0.32f, 0.14f)
            bikePart(0.56f, 0.24f, 0f, 0.12f, 0.14f, 1.44f, 0.52f, 0.32f, 0.14f)
        }

        val customModel = playerCustomModel
        val customMesh = playerCustomMesh
        if (customModel != null && customMesh != null && bike == null) {
            val (pr, pg, pb) = hexToRgb(customModel.primaryColorHex)
            emitParsedObjMesh(
                mesh = customMesh,
                cx = p.x,
                cy = p.y + swimBob + crouchDrop + kotlin.math.abs(stride) * 0.14f,
                cz = p.z,
                yaw = p.yaw,
                scale = customModel.scale,
                r = pr, g = pg, b = pb
            )
            return
        }

        val skinR = 0.74f; val skinG = 0.54f; val skinB = 0.40f
        val (poloR, poloG, poloB) = if (customModel != null) hexToRgb(customModel.primaryColorHex) else Triple(0.38f, 0.24f, 0.21f)
        val (stripeR, stripeG, stripeB) = if (customModel != null) hexToRgb(customModel.accentColorHex) else Triple(0.24f, 0.14f, 0.12f)
        val collarR = 0.62f; val collarG = 0.64f; val collarB = 0.68f
        val shortsR = 0.16f; val shortsG = 0.16f; val shortsB = 0.19f
        val hairR = 0.08f; val hairG = 0.08f; val hairB = 0.10f

        fun part(lx: Float, ly: Float, lz: Float, w: Float, h: Float, d: Float, r: Float, g: Float, b: Float) {
            val leanedX = lx + leanOffset * ((ly + crouchDrop) / 1.5f).coerceIn(0f, 1.4f)
            val wx = p.x + leanedX * c + lz * s
            val wz = p.z + leanedX * s - lz * c
            emitBox(wx, p.y + ly + swimBob + crouchDrop, wz, w, h, d, p.yaw, r, g, b)
        }

        // Legs (Straddling bike, sitting relaxed on water raft, or walking smoothly)
        when {
            bike != null -> {
                part(-0.24f, 0.52f, -0.14f, 0.19f, 0.36f, 0.28f, shortsR, shortsG, shortsB)
                part(0.24f, 0.52f, -0.14f, 0.19f, 0.36f, 0.28f, shortsR, shortsG, shortsB)
                part(-0.26f, 0.24f, 0.04f, 0.16f, 0.30f, 0.18f, skinR, skinG, skinB)
                part(0.26f, 0.24f, 0.04f, 0.16f, 0.30f, 0.18f, skinR, skinG, skinB)
                part(-0.26f, 0.11f, 0.08f, 0.18f, 0.10f, 0.24f, 0.14f, 0.14f, 0.16f)
                part(0.26f, 0.11f, 0.08f, 0.18f, 0.10f, 0.24f, 0.14f, 0.14f, 0.16f)
            }
            sittingWater -> {
                part(-0.15f, 0.64f, 0.26f, 0.21f, 0.20f, 0.44f, shortsR, shortsG, shortsB)
                part(0.15f, 0.64f, 0.26f, 0.21f, 0.20f, 0.44f, shortsR, shortsG, shortsB)
                part(-0.15f, 0.56f, 0.58f, 0.17f, 0.18f, 0.32f, skinR, skinG, skinB)
                part(0.15f, 0.56f, 0.58f, 0.17f, 0.18f, 0.32f, skinR, skinG, skinB)
            }
            else -> {
                part(-0.13f, 0.07f, stride, 0.20f, 0.14f, 0.24f, 0.15f, 0.15f, 0.18f) // Shoes
                part(0.13f, 0.07f, -stride, 0.20f, 0.14f, 0.24f, 0.15f, 0.15f, 0.18f)
                part(-0.13f, 0.24f, stride, 0.18f, 0.28f, 0.18f, skinR, skinG, skinB)
                part(0.13f, 0.24f, -stride, 0.18f, 0.28f, 0.18f, skinR, skinG, skinB)
                part(-0.13f, 0.54f, stride * 0.7f, 0.23f, 0.38f, 0.23f, shortsR, shortsG, shortsB)
                part(0.13f, 0.54f, -stride * 0.7f, 0.23f, 0.38f, 0.23f, shortsR, shortsG, shortsB)
            }
        }

        val torsoZ = if (bike != null) -0.22f else 0f
        // Human Torso with Neck, Collar & Striped Polo
        part(0f, 1.04f, torsoZ, 0.48f, 0.64f, 0.27f, poloR, poloG, poloB)
        part(0f, 0.92f, torsoZ, 0.49f, 0.07f, 0.28f, stripeR, stripeG, stripeB)
        part(0f, 1.12f, torsoZ, 0.49f, 0.07f, 0.28f, stripeR, stripeG, stripeB)
        part(0f, 1.35f, torsoZ, 0.42f, 0.09f, 0.29f, collarR, collarG, collarB)
        part(0f, 1.41f, torsoZ, 0.18f, 0.12f, 0.18f, skinR, skinG, skinB) // Realistic human neck
        part(0f, 1.38f, torsoZ + 0.13f, 0.14f, 0.04f, 0.04f, 0.90f, 0.24f, 0.16f)

        // Arms (Gripping Bike Handlebars vs Relaxed on Water vs Walking/Swinging!)
        if (bike != null) {
            part(-0.33f, 1.18f, 0.04f, 0.18f, 0.20f, 0.38f, poloR, poloG, poloB)
            part(-0.35f, 1.04f, 0.36f, 0.15f, 0.15f, 0.42f, skinR, skinG, skinB)
            part(0.33f, 1.18f, 0.04f, 0.18f, 0.20f, 0.38f, poloR, poloG, poloB)
            part(0.35f, 1.04f, 0.36f, 0.15f, 0.15f, 0.42f, skinR, skinG, skinB)
        } else {
            part(-0.33f, 1.22f, -stride * 0.5f, 0.19f, 0.24f, 0.19f, poloR, poloG, poloB)
            part(-0.33f, 1.09f, -stride * 0.7f, 0.195f, 0.05f, 0.195f, collarR, collarG, collarB)
            part(-0.33f, 0.89f, -stride, 0.16f, 0.36f, 0.16f, skinR, skinG, skinB)

            part(0.33f, 1.22f + swingKick * 0.2f, stride * 0.5f + swingKick * 0.4f, 0.19f, 0.24f, 0.19f, poloR, poloG, poloB)
            part(0.33f, 1.09f + swingKick * 0.28f, stride * 0.7f + swingKick * 0.6f, 0.195f, 0.05f, 0.195f, collarR, collarG, collarB)
            part(0.33f, 0.89f + swingKick * 0.38f, stride + swingKick, 0.16f, 0.36f, 0.16f, skinR, skinG, skinB)
            if (!sittingWater) {
                part(0.36f, 0.86f + swingKick * 0.4f, 0.34f + swingKick, 0.09f, 0.38f, 0.09f, 0.22f, 0.90f, 1.0f)
            }
        }

        // Realistic Human Head (Sclera + Pupils + Eyebrows + Nose + Smile + Side-Parted Hair)
        val headZ = torsoZ + if (bike != null) 0.08f else 0f
        part(0f, 1.62f, headZ, 0.42f, 0.44f, 0.42f, skinR, skinG, skinB)
        part(-0.10f, 1.65f, headZ + 0.21f, 0.09f, 0.06f, 0.03f, 0.96f, 0.97f, 0.99f)
        part(0.10f, 1.65f, headZ + 0.21f, 0.09f, 0.06f, 0.03f, 0.96f, 0.97f, 0.99f)
        part(-0.10f, 1.65f, headZ + 0.225f, 0.045f, 0.045f, 0.02f, 0.08f, 0.08f, 0.10f)
        part(0.10f, 1.65f, headZ + 0.225f, 0.045f, 0.045f, 0.02f, 0.08f, 0.08f, 0.10f)
        part(-0.10f, 1.71f, headZ + 0.215f, 0.10f, 0.025f, 0.03f, hairR, hairG, hairB)
        part(0.10f, 1.71f, headZ + 0.215f, 0.10f, 0.025f, 0.03f, hairR, hairG, hairB)
        part(0f, 1.59f, headZ + 0.225f, 0.05f, 0.07f, 0.04f, skinR * 0.92f, skinG * 0.90f, skinB * 0.88f) // Nose
        part(0f, 1.50f, headZ + 0.215f, 0.13f, 0.035f, 0.03f, 0.54f, 0.24f, 0.22f)
        part(0f, 1.85f, headZ - 0.01f, 0.45f, 0.13f, 0.45f, hairR, hairG, hairB)
        part(0f, 1.69f, headZ - 0.21f, 0.45f, 0.28f, 0.06f, hairR, hairG, hairB)
        part(0.06f, 1.80f, headZ + 0.21f, 0.28f, 0.09f, 0.06f, hairR, hairG, hairB)
        if (playerWearsRoyalCrown || customModel != null) {
            part(0f, 1.96f, headZ, 0.46f, 0.18f, 0.46f, if (customModel != null) stripeR else 1.0f, if (customModel != null) stripeG else 0.84f, if (customModel != null) stripeB else 0.18f)
            part(0f, 2.09f, headZ, 0.18f, 0.10f, 0.18f, 0.95f, 0.18f, 0.35f)
        }
    }

    private fun emitEntityModel(e: VoxelEntity) {
        val flash = e.hitFlash > 0f
        val swing = sin(e.animPhase) * 0.16f
        val c = cos(e.yaw)
        val s = sin(e.yaw)

        fun part(lx: Float, ly: Float, lz: Float, w: Float, h: Float, d: Float, r: Float, g: Float, b: Float) {
            val wx = e.x + lx * c + lz * s
            val wz = e.z + lx * s - lz * c
            val rr = if (flash) 1.0f else r
            val gg = if (flash) 0.22f else g
            val bb = if (flash) 0.22f else b
            emitBox(wx, e.y + ly, wz, w, h, d, e.yaw, rr, gg, bb)
        }

        // Floating 3D Health Bar above hostile enemies when damaged or for Custom Admin Bosses
        if (e.kind.isHostile && (e.hp < e.maxHpOverride || e.kind == EntityKind.CUSTOM_ENEMY)) {
            val ratio = (e.hp.toFloat() / e.maxHpOverride.coerceAtLeast(1)).coerceIn(0.05f, 1f)
            val barY = e.kind.height * e.customScale + 0.35f
            part(0f, barY, 0f, 0.86f, 0.10f, 0.08f, 0.12f, 0.12f, 0.15f)
            part(-(1f - ratio) * 0.40f, barY, 0.02f, 0.80f * ratio, 0.08f, 0.09f, 1.0f, 0.22f, 0.22f)
        }

        // Real Uploaded Wavefront .OBJ 3D Mesh rendering!
        val uploadedMesh = e.customMesh
        if (uploadedMesh != null) {
            val (pr, pg, pb) = hexToRgb(e.customPrimaryHex ?: e.kind.badgeColorHex)
            val rr = if (flash) 1.0f else pr
            val gg = if (flash) 0.22f else pg
            val bb = if (flash) 0.22f else pb
            val hop = kotlin.math.abs(sin(e.animPhase)) * 0.08f
            emitParsedObjMesh(uploadedMesh, e.x, e.y + hop, e.z, e.yaw, e.customScale, rr, gg, bb)
            // Glowing speech wave ring above head when talking!
            if (e.talkingTimer > 0f) {
                val mouthPulse = 0.12f + kotlin.math.abs(sin(globalAnimClock * 20f)) * 0.14f
                part(0f, 1.92f * e.customScale, 0.22f, 0.24f, mouthPulse, 0.12f, 0.32f, 1.0f, 0.65f)
            }
            return
        }

        when (e.kind) {
            EntityKind.BIRD_SPARROW, EntityKind.BIRD_MYNA -> {
                val isMyna = e.kind == EntityKind.BIRD_MYNA
                val bodyR = if (isMyna) 0.22f else 0.62f
                val bodyG = if (isMyna) 0.18f else 0.44f
                val bodyB = if (isMyna) 0.16f else 0.29f
                val wingLift = sin(e.animPhase) * 0.14f
                part(0f, 0.15f, 0f, 0.24f, 0.18f, 0.34f, bodyR, bodyG, bodyB)
                part(0f, 0.24f, 0.20f, 0.18f, 0.16f, 0.18f, if (isMyna) 0.12f else 0.55f, if (isMyna) 0.12f else 0.36f, if (isMyna) 0.14f else 0.22f)
                part(0f, 0.22f, 0.32f, 0.07f, 0.06f, 0.10f, 1.0f, 0.80f, 0.15f)
                part(-0.26f, 0.18f + wingLift, 0f, 0.30f, 0.05f, 0.22f, if (isMyna) 0.92f else 0.48f, if (isMyna) 0.92f else 0.32f, if (isMyna) 0.92f else 0.20f)
                part(0.26f, 0.18f + wingLift, 0f, 0.30f, 0.05f, 0.22f, if (isMyna) 0.92f else 0.48f, if (isMyna) 0.92f else 0.32f, if (isMyna) 0.92f else 0.20f)
            }

            EntityKind.ELEPHANT -> {
                val gR = 0.50f; val gG = 0.56f; val gB = 0.60f
                part(-0.42f, 0.45f, 0.50f + swing, 0.34f, 0.90f, 0.34f, gR * 0.85f, gG * 0.85f, gB * 0.85f)
                part(0.42f, 0.45f, 0.50f - swing, 0.34f, 0.90f, 0.34f, gR * 0.85f, gG * 0.85f, gB * 0.85f)
                part(-0.42f, 0.45f, -0.50f - swing, 0.34f, 0.90f, 0.34f, gR * 0.85f, gG * 0.85f, gB * 0.85f)
                part(0.42f, 0.45f, -0.50f + swing, 0.34f, 0.90f, 0.34f, gR * 0.85f, gG * 0.85f, gB * 0.85f)
                part(0f, 1.32f, 0f, 1.25f, 0.95f, 1.55f, gR, gG, gB)
                part(0f, 1.82f, 0f, 1.28f, 0.12f, 0.85f, 0.92f, 0.22f, 0.28f)
                part(0f, 1.58f, 0.98f, 0.90f, 0.86f, 0.78f, gR * 1.05f, gG * 1.05f, gB * 1.05f)
                part(-0.62f, 1.58f, 0.88f, 0.36f, 0.72f, 0.14f, gR * 0.92f, gG * 0.92f, gB * 0.92f)
                part(0.62f, 1.58f, 0.88f, 0.36f, 0.72f, 0.14f, gR * 0.92f, gG * 0.92f, gB * 0.92f)
                part(0f, 1.02f, 1.42f, 0.26f, 0.92f, 0.26f, gR * 0.95f, gG * 0.95f, gB * 0.95f)
                part(-0.26f, 1.24f, 1.44f, 0.10f, 0.12f, 0.48f, 0.98f, 0.96f, 0.88f)
                part(0.26f, 1.24f, 1.44f, 0.10f, 0.12f, 0.48f, 0.98f, 0.96f, 0.88f)
            }

            EntityKind.COW, EntityKind.BUFFALO, EntityKind.BULL, EntityKind.HORSE -> {
                val (bR, bG, bB) = when (e.kind) {
                    EntityKind.COW -> Triple(0.94f, 0.92f, 0.88f)
                    EntityKind.BUFFALO -> Triple(0.16f, 0.18f, 0.21f)
                    EntityKind.BULL -> Triple(0.48f, 0.28f, 0.16f)
                    else -> Triple(0.62f, 0.38f, 0.20f)
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
                    part(0.18f, bodyY + 0.12f, 0.10f, 0.36f, 0.32f, 0.40f, 0.24f, 0.18f, 0.15f)
                }
                if (isHorse) {
                    part(0f, bodyY + 0.44f, 0.52f, 0.28f, 0.52f, 0.32f, bR, bG, bB)
                    part(0f, bodyY + 0.68f, 0.72f, 0.32f, 0.30f, 0.52f, bR * 0.92f, bG * 0.92f, bB * 0.92f)
                    part(0f, bodyY + 0.50f, 0.36f, 0.12f, 0.48f, 0.14f, 0.16f, 0.10f, 0.08f)
                } else {
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
                part(0f, 0.56f * sScale, -0.40f * sScale, 0.10f * sScale, 0.28f * sScale, 0.10f * sScale, r, g, b)
                if (isDog) part(0f, 0.48f, 0.24f, 0.40f, 0.08f, 0.10f, 0.95f, 0.18f, 0.22f)
            }

            EntityKind.ZOMBIE -> {
                part(-0.14f, 0.36f, swing, 0.22f, 0.72f, 0.22f, 0.20f, 0.28f, 0.55f)
                part(0.14f, 0.36f, -swing, 0.22f, 0.72f, 0.22f, 0.20f, 0.28f, 0.55f)
                part(0f, 1.04f, 0f, 0.52f, 0.66f, 0.28f, 0.14f, 0.54f, 0.58f)
                part(0f, 1.62f, 0f, 0.46f, 0.46f, 0.46f, 0.32f, 0.76f, 0.28f)
                part(-0.11f, 1.66f, 0.24f, 0.09f, 0.07f, 0.04f, 0.95f, 0.15f, 0.15f)
                part(0.11f, 1.66f, 0.24f, 0.09f, 0.07f, 0.04f, 0.95f, 0.15f, 0.15f)
                part(-0.36f, 1.18f, 0.32f, 0.18f, 0.18f, 0.66f, 0.32f, 0.76f, 0.28f)
                part(0.36f, 1.18f, 0.32f, 0.18f, 0.18f, 0.66f, 0.32f, 0.76f, 0.28f)
            }

            else -> {
                // --- REALLY HUMAN 3D CHARACTER RENDERER ---
                // Realistic human proportions: shoes, trousers, belted kurta/shirt, neck, articulated sleeves & forearms,
                // expressive sclera+pupil eyes, eyebrows, nose, hair/beard, animated waving/working arms & seated resting pose!
                val scale = if (e.kind == EntityKind.RAJU) 0.84f else e.customScale
                val breath = sin(globalAnimClock * 2.6f + e.id * 0.9f) * 0.02f * scale
                val sitDrop = if (e.isResting) -0.34f * scale else 0f
                val waveLift = if (e.talkingTimer > 0f) (0.28f + sin(globalAnimClock * 11f) * 0.14f) * scale else 0f
                val workSwing = if (e.goal == AiGoal.BUILD_HOUSE || e.goal == AiGoal.BUILD_MANDIR || e.goal == AiGoal.FARM_WORK) {
                    sin(globalAnimClock * 9f + e.id) * 0.24f * scale
                } else 0f

                val (skinR, skinG, skinB) = when (e.kind) {
                    EntityKind.KRISHNA -> Triple(0.32f, 0.66f, 0.92f)
                    EntityKind.RAM -> Triple(0.42f, 0.72f, 0.84f)
                    EntityKind.HANUMAN -> Triple(0.96f, 0.50f, 0.22f)
                    EntityKind.CUSTOM_ENEMY -> hexToRgb(e.customAccentHex ?: 0xFFFF3D00)
                    EntityKind.MAJDUR, EntityKind.GARIB_KISAN -> Triple(0.76f, 0.55f, 0.40f)
                    EntityKind.RAJA, EntityKind.AMIR_SETH -> Triple(0.88f, 0.69f, 0.54f)
                    else -> Triple(0.84f, 0.64f, 0.49f)
                }
                val (tunicR, tunicG, tunicB) = when {
                    e.customPrimaryHex != null -> hexToRgb(e.customPrimaryHex)
                    e.kind == EntityKind.RAJA -> Triple(0.84f, 0.12f, 0.22f)
                    e.kind == EntityKind.PUJARI -> Triple(1.0f, 0.48f, 0.10f)
                    e.kind == EntityKind.MAJDUR -> Triple(0.82f, 0.56f, 0.32f)
                    e.kind == EntityKind.AMIR_SETH -> Triple(0.56f, 0.18f, 0.78f)
                    e.kind == EntityKind.GARIB_KISAN -> Triple(0.68f, 0.62f, 0.52f)
                    e.kind == EntityKind.KRISHNA -> Triple(1.0f, 0.84f, 0.18f)
                    e.kind == EntityKind.RAM -> Triple(0.96f, 0.62f, 0.14f)
                    e.kind == EntityKind.HANUMAN -> Triple(1.0f, 0.38f, 0.08f)
                    e.kind == EntityKind.BHEEM -> Triple(1.0f, 0.56f, 0.08f)
                    e.kind == EntityKind.RAJU -> Triple(1.0f, 0.82f, 0.20f)
                    else -> Triple(0.28f, 0.52f, 0.78f)
                }
                val (legR, legG, legB) = when {
                    e.customAccentHex != null -> hexToRgb(e.customAccentHex)
                    e.kind == EntityKind.RAJA -> Triple(1.0f, 0.84f, 0.20f)
                    e.kind == EntityKind.PUJARI || e.kind == EntityKind.GARIB_KISAN -> Triple(0.95f, 0.93f, 0.87f)
                    e.kind == EntityKind.KRISHNA -> Triple(1.0f, 0.82f, 0.14f)
                    e.kind == EntityKind.BHEEM -> Triple(0.98f, 0.52f, 0.06f)
                    else -> Triple(0.18f, 0.22f, 0.30f)
                }

                // Shoes + Legs (Seated bent pose if resting, else realistic walking stride!)
                if (e.isResting) {
                    part(-0.12f * scale, 0.24f * scale, 0.26f * scale, 0.18f * scale, 0.20f * scale, 0.44f * scale, legR, legG, legB)
                    part(0.12f * scale, 0.24f * scale, 0.26f * scale, 0.18f * scale, 0.20f * scale, 0.44f * scale, legR, legG, legB)
                    part(-0.12f * scale, 0.14f * scale, 0.48f * scale, 0.17f * scale, 0.14f * scale, 0.18f * scale, 0.16f, 0.12f, 0.10f)
                    part(0.12f * scale, 0.14f * scale, 0.48f * scale, 0.17f * scale, 0.14f * scale, 0.18f * scale, 0.16f, 0.12f, 0.10f)
                } else {
                    part(-0.12f * scale, 0.07f * scale, swing, 0.19f * scale, 0.14f * scale, 0.24f * scale, 0.18f, 0.13f, 0.10f)
                    part(0.12f * scale, 0.07f * scale, -swing, 0.19f * scale, 0.14f * scale, 0.24f * scale, 0.18f, 0.13f, 0.10f)
                    part(-0.12f * scale, 0.41f * scale, swing * 0.85f, 0.18f * scale, 0.58f * scale, 0.19f * scale, legR, legG, legB)
                    part(0.12f * scale, 0.41f * scale, -swing * 0.85f, 0.18f * scale, 0.58f * scale, 0.19f * scale, legR, legG, legB)
                }

                // Human Torso with Belt/Waistband, Collar & Realistic Neck
                part(0f, 1.03f * scale + sitDrop + breath, 0f, 0.46f * scale, 0.62f * scale, 0.26f * scale, tunicR, tunicG, tunicB)
                part(0f, 0.74f * scale + sitDrop, 0f, 0.47f * scale, 0.08f * scale, 0.27f * scale, 0.22f, 0.16f, 0.12f) // Belt/sash
                part(0f, 1.38f * scale + sitDrop + breath, 0f, 0.16f * scale, 0.12f * scale, 0.16f * scale, skinR, skinG, skinB) // Neck

                // Articulated Upper Sleeves + Skin Forearms & Hands (with greeting wave or work hammer animation!)
                part(-0.31f * scale, 1.18f * scale + sitDrop + breath, -swing * 0.6f, 0.16f * scale, 0.28f * scale, 0.17f * scale, tunicR, tunicG, tunicB)
                part(-0.31f * scale, 0.88f * scale + sitDrop + breath, -swing, 0.14f * scale, 0.34f * scale, 0.15f * scale, skinR, skinG, skinB)

                val rightArmY = 1.18f * scale + sitDrop + breath + waveLift * 0.45f
                val rightForeY = 0.88f * scale + sitDrop + breath + waveLift + kotlin.math.abs(workSwing) * 0.35f
                val rightArmZ = swing * 0.6f + (if (waveLift > 0f) 0.16f * scale else workSwing)
                part(0.31f * scale, rightArmY, rightArmZ * 0.6f, 0.16f * scale, 0.28f * scale, 0.17f * scale, tunicR, tunicG, tunicB)
                part(0.31f * scale, rightForeY, rightArmZ, 0.14f * scale, 0.34f * scale, 0.15f * scale, skinR, skinG, skinB)

                // Realistic Human Head (White Sclera + Pupils + Eyebrows + Nose + Real-Time Lip-Sync Mouth + Ears/Hair!)
                val headY = 1.60f * scale + sitDrop + breath
                part(0f, headY, 0f, 0.40f * scale, 0.43f * scale, 0.40f * scale, skinR, skinG, skinB)
                part(-0.21f * scale, headY, 0f, 0.04f * scale, 0.10f * scale, 0.08f * scale, skinR * 0.94f, skinG * 0.92f, skinB * 0.90f) // Ears
                part(0.21f * scale, headY, 0f, 0.04f * scale, 0.10f * scale, 0.08f * scale, skinR * 0.94f, skinG * 0.92f, skinB * 0.90f)
                // Eyes (Sclera + Dark Pupils + Eyebrows)
                part(-0.09f * scale, headY + 0.04f * scale, 0.20f * scale, 0.085f * scale, 0.055f * scale, 0.025f, 0.96f, 0.97f, 0.99f)
                part(0.09f * scale, headY + 0.04f * scale, 0.20f * scale, 0.085f * scale, 0.055f * scale, 0.025f, 0.96f, 0.97f, 0.99f)
                part(-0.09f * scale, headY + 0.04f * scale, 0.215f * scale, 0.042f * scale, 0.042f * scale, 0.02f, 0.09f, 0.08f, 0.10f)
                part(0.09f * scale, headY + 0.04f * scale, 0.215f * scale, 0.042f * scale, 0.042f * scale, 0.02f, 0.09f, 0.08f, 0.10f)
                part(-0.09f * scale, headY + 0.095f * scale, 0.205f * scale, 0.095f * scale, 0.022f * scale, 0.025f, 0.12f, 0.10f, 0.10f)
                part(0.09f * scale, headY + 0.095f * scale, 0.205f * scale, 0.095f * scale, 0.022f * scale, 0.025f, 0.12f, 0.10f, 0.10f)
                // Human Nose Bridge
                part(0f, headY - 0.015f * scale, 0.215f * scale, 0.045f * scale, 0.075f * scale, 0.04f, skinR * 0.90f, skinG * 0.88f, skinB * 0.85f)
                // Real-Time Animated Speaking Mouth
                val mouthOpen = if (e.talkingTimer > 0f) {
                    (0.038f + kotlin.math.abs(sin(globalAnimClock * 20f)) * 0.095f) * scale
                } else {
                    0.032f * scale
                }
                part(
                    0f, headY - 0.105f * scale, 0.205f * scale,
                    0.13f * scale, mouthOpen, 0.03f,
                    if (e.talkingTimer > 0f) 0.56f else 0.42f, 0.16f, 0.16f
                )
                // Full back-of-head human hair cap
                part(0f, headY + 0.05f * scale, -0.19f * scale, 0.42f * scale, 0.30f * scale, 0.06f * scale, 0.10f, 0.09f, 0.10f)

                when (e.kind) {
                    EntityKind.CUSTOM_ENEMY -> {
                        part(-0.18f * scale, headY + 0.30f * scale, 0.06f, 0.10f * scale, 0.28f * scale, 0.10f * scale, 1.0f, 0.28f, 0.12f)
                        part(0.18f * scale, headY + 0.30f * scale, 0.06f, 0.10f * scale, 0.28f * scale, 0.10f * scale, 1.0f, 0.28f, 0.12f)
                        part(0.38f * scale, 1.06f * scale + sitDrop, 0.34f * scale, 0.11f * scale, 0.82f * scale, 0.11f * scale, 1.0f, 0.42f, 0.08f)
                    }
                    EntityKind.CUSTOM_HERO -> {
                        part(0f, headY + 0.26f * scale, 0f, 0.46f * scale, 0.20f * scale, 0.46f * scale, legR, legG, legB)
                        part(-0.38f * scale, 1.30f * scale + sitDrop, 0f, 0.20f * scale, 0.16f * scale, 0.30f * scale, legR, legG, legB)
                        part(0.38f * scale, 1.30f * scale + sitDrop, 0f, 0.20f * scale, 0.16f * scale, 0.30f * scale, legR, legG, legB)
                    }
                    EntityKind.RAJA -> {
                        // Royal Mustache + Golden Mukut Crown + Crimson Cape + Scepter
                        part(0f, headY - 0.06f * scale, 0.21f * scale, 0.18f * scale, 0.035f * scale, 0.03f, 0.12f, 0.10f, 0.10f)
                        part(0f, headY + 0.28f, 0f, 0.44f, 0.24f, 0.44f, 1.0f, 0.84f, 0.16f)
                        part(0f, headY + 0.46f, 0f, 0.18f, 0.14f, 0.18f, 0.95f, 0.16f, 0.32f)
                        part(0f, 0.96f + sitDrop, -0.20f, 0.48f, 0.88f, 0.07f, 0.72f, 0.08f, 0.16f)
                        part(0.36f, 1.05f + sitDrop, 0.24f, 0.07f, 0.74f, 0.07f, 1.0f, 0.86f, 0.22f)
                    }
                    EntityKind.PUJARI -> {
                        // Tilak + Wise White Beard + Brass Aarti Diya Plate
                        part(0f, headY + 0.12f, 0.21f, 0.06f, 0.12f, 0.03f, 1.0f, 0.35f, 0.10f)
                        part(0f, headY - 0.15f, 0.18f, 0.26f, 0.14f, 0.08f, 0.92f, 0.92f, 0.94f)
                        part(0.34f, 1.02f + sitDrop, 0.28f, 0.20f, 0.09f, 0.20f, 0.88f, 0.65f, 0.20f)
                        part(0.34f, 1.13f + sitDrop, 0.28f, 0.09f, 0.13f, 0.09f, 1.0f, 0.92f, 0.25f)
                    }
                    EntityKind.MAJDUR -> {
                        // Pagdi Turban + Brick/Hammer tool in hand
                        part(0f, headY + 0.26f, 0f, 0.46f, 0.18f, 0.46f, 0.96f, 0.88f, 0.72f)
                        part(0.35f, rightForeY - 0.04f, rightArmZ + 0.20f, 0.22f, 0.16f, 0.22f, 0.74f, 0.32f, 0.24f)
                    }
                    EntityKind.AMIR_SETH -> {
                        // Royal Pagdi + Gold Necklace + Neat Mustache
                        part(0f, headY - 0.06f, 0.21f, 0.16f, 0.03f, 0.03f, 0.14f, 0.12f, 0.12f)
                        part(0f, headY + 0.26f, 0f, 0.46f, 0.20f, 0.46f, 1.0f, 0.82f, 0.18f)
                        part(0f, 1.24f + sitDrop, 0.14f, 0.26f, 0.09f, 0.05f, 1.0f, 0.88f, 0.22f)
                    }
                    EntityKind.GARIB_KISAN -> {
                        part(0f, headY + 0.24f, 0f, 0.45f, 0.14f, 0.45f, 0.92f, 0.90f, 0.84f)
                        part(0.36f, 1.02f + sitDrop, 0.18f + workSwing, 0.065f, 0.90f, 0.065f, 0.54f, 0.36f, 0.18f)
                    }
                    EntityKind.KRISHNA -> {
                        part(0f, headY + 0.26f, 0f, 0.44f, 0.16f, 0.44f, 1.0f, 0.84f, 0.18f)
                        part(0.10f, headY + 0.50f, 0.04f, 0.14f, 0.34f, 0.08f, 0.10f, 0.88f, 0.55f)
                        part(0.10f, headY + 0.54f, 0.09f, 0.08f, 0.10f, 0.04f, 0.12f, 0.45f, 1.0f)
                        part(0.22f, headY - 0.14f, 0.26f, 0.56f, 0.055f, 0.055f, 0.96f, 0.82f, 0.42f)
                    }
                    EntityKind.RAM -> {
                        part(0f, headY + 0.30f, 0f, 0.42f, 0.26f, 0.42f, 1.0f, 0.84f, 0.16f)
                        part(0f, headY + 0.48f, 0f, 0.20f, 0.15f, 0.20f, 1.0f, 0.92f, 0.35f)
                        part(0.40f, 1.15f + sitDrop, 0.18f, 0.08f, 0.90f, 0.16f, 0.98f, 0.78f, 0.20f)
                    }
                    EntityKind.HANUMAN -> {
                        part(0f, headY + 0.28f, 0f, 0.42f, 0.24f, 0.42f, 1.0f, 0.84f, 0.16f)
                        part(0.40f, 1.18f + sitDrop, 0.24f, 0.09f, 0.74f, 0.09f, 1.0f, 0.82f, 0.18f)
                        part(0.40f, 1.60f + sitDrop, 0.24f, 0.26f, 0.26f, 0.26f, 1.0f, 0.88f, 0.22f)
                        part(0f, 0.96f + sitDrop, -0.30f, 0.09f, 0.70f, 0.09f, 0.96f, 0.44f, 0.14f)
                    }
                    EntityKind.BHEEM -> {
                        part(0f, headY + 0.24f, 0f, 0.43f, 0.14f, 0.43f, 0.12f, 0.11f, 0.12f)
                        part(0f, headY + 0.08f, 0.21f, 0.06f, 0.12f, 0.03f, 1.0f, 0.24f, 0.16f)
                    }
                    EntityKind.RAJU -> {
                        part(0f, headY + 0.24f * scale, 0f, 0.16f, 0.16f, 0.16f, 0.12f, 0.12f, 0.14f)
                    }
                    else -> {
                        part(0f, headY + 0.24f, 0f, 0.43f, 0.13f, 0.43f, 0.14f, 0.11f, 0.10f)
                    }
                }
            }
        }
    }

    private fun hexToRgb(hex: Long): Triple<Float, Float, Float> {
        val r = ((hex shr 16) and 0xFFL) / 255f
        val g = ((hex shr 8) and 0xFFL) / 255f
        val b = (hex and 0xFFL) / 255f
        return Triple(r, g, b)
    }

    private fun emitParsedObjMesh(
        mesh: Parsed3DModelMesh,
        cx: Float,
        cy: Float,
        cz: Float,
        yaw: Float,
        scale: Float,
        r: Float,
        g: Float,
        b: Float
    ) {
        val verts = mesh.localVertices
        val totalVerts = mesh.triangleCount * 3
        if (vertFloatCount + totalVerts * 7 > vertArray.size) return
        val cosY = cos(yaw)
        val sinY = sin(yaw)
        var idx = 0
        for (v in 0 until totalVerts) {
            val lx = verts[idx++] * scale
            val ly = verts[idx++] * scale
            val lz = verts[idx++] * scale
            val shade = verts[idx++]
            val wx = cx + lx * cosY + lz * sinY
            val wy = cy + ly
            val wz = cz + lx * sinY - lz * cosY
            addV(wx, wy, wz, r, g, b, shade)
        }
    }

    private fun spawnBurstLocked(x: Float, y: Float, z: Float, r: Float, g: Float, b: Float, count: Int) {
        repeat(count) {
            if (particles.size < 220) {
                particles.add(
                    VoxelParticle(
                        x = x, y = y, z = z,
                        vx = (rng.nextFloat() - 0.5f) * 6f,
                        vy = 2f + rng.nextFloat() * 4.5f,
                        vz = (rng.nextFloat() - 0.5f) * 6f,
                        r = r, g = g, b = b,
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

        emitQuad(x01, y1, z01, x11, y1, z11, x10, y1, z10, x00, y1, z00, r, g, b, 1.00f)
        emitQuad(x00, y0, z00, x10, y0, z10, x11, y0, z11, x01, y0, z01, r, g, b, 0.55f)
        emitQuad(x01, y0, z01, x11, y0, z11, x11, y1, z11, x01, y1, z01, r, g, b, 0.88f)
        emitQuad(x10, y0, z10, x00, y0, z00, x00, y1, z00, x10, y1, z10, r, g, b, 0.72f)
        emitQuad(x11, y0, z11, x10, y0, z10, x10, y1, z10, x11, y1, z11, r, g, b, 0.80f)
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
        private const val MAX_ENTITIES = 72
        private const val BULLET_SPEED = 32f
        private const val MAX_FLOATS = 220_000
    }
}
