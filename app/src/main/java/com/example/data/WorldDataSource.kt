package com.example.data

import android.content.Context
import com.example.data.model.BiomeType
import com.example.data.model.BuilderProfile
import com.example.data.model.GameMode
import com.example.data.model.VoxelWorldDoc
import com.example.data.model.defaultHotbar
import com.example.data.model.defaultStarterInventory
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.max

/**
 * Unified data source for both Google Cloud Sync (Firestore) and Offline Guest Mode (SharedPreferences).
 */
interface WorldDataSource {
    val isCloudBacked: Boolean
    fun observeProfile(userId: String): Flow<BuilderProfile?>
    fun observeWorlds(userId: String): Flow<List<VoxelWorldDoc>>
    suspend fun ensureProfileExists(defaultDisplayName: String): Result<Unit>
    suspend fun updateDisplayName(newName: String): Result<Unit>
    suspend fun incrementProfileStats(minedDelta: Long, placedDelta: Long, craftedDelta: Long): Result<Unit>
    suspend fun createWorld(name: String, biome: BiomeType, gameMode: GameMode, seed: Long): Result<VoxelWorldDoc>
    suspend fun getWorld(worldId: String): Result<VoxelWorldDoc?>
    suspend fun saveWorldState(
        worldId: String,
        gameMode: GameMode,
        timeOfDay: Double,
        pickaxeTier: Long,
        blocksMined: Long,
        blocksPlaced: Long,
        playerX: Double,
        playerY: Double,
        playerZ: Double,
        blockDeltas: Map<String, Long>,
        inventory: Map<String, Long>,
        hotbar: List<String>
    ): Result<Unit>
    suspend fun renameWorld(worldId: String, newName: String): Result<Unit>
    suspend fun deleteWorld(worldId: String): Result<Unit>
}

/**
 * Purely device-local Guest storage so users can explore, build, and save worlds without signing in.
 * Never calls Firestore or Firebase Auth.
 */
class GuestLocalDataSource(context: Context) : WorldDataSource {
    override val isCloudBacked: Boolean = false

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val profileFlow = MutableStateFlow<BuilderProfile?>(loadProfile())
    private val worldsFlow = MutableStateFlow<List<VoxelWorldDoc>>(loadWorlds())

    init {
        if (worldsFlow.value.isEmpty()) {
            val starter = VoxelWorldDoc(
                id = "guest_starter_valley",
                userId = GUEST_UID,
                name = "Emerald Valley (Guest)",
                biome = BiomeType.EMERALD_VALLEY.name,
                gameMode = GameMode.CREATIVE.name,
                seed = 424242L,
                timeOfDay = 0.22,
                pickaxeTier = 1L,
                blocksMined = 0L,
                blocksPlaced = 0L,
                playerX = 8.5,
                playerY = 18.0,
                playerZ = 8.5,
                blockDeltas = emptyMap(),
                inventory = defaultStarterInventory(GameMode.CREATIVE),
                hotbar = defaultHotbar(),
                createdAt = Timestamp.now(),
                updatedAt = Timestamp.now()
            )
            persistWorlds(listOf(starter))
        }
    }

    override fun observeProfile(userId: String): Flow<BuilderProfile?> = profileFlow.asStateFlow()

    override fun observeWorlds(userId: String): Flow<List<VoxelWorldDoc>> = worldsFlow.asStateFlow()

    override suspend fun ensureProfileExists(defaultDisplayName: String): Result<Unit> {
        if (profileFlow.value == null) {
            val profile = BuilderProfile(
                id = GUEST_UID,
                userId = GUEST_UID,
                displayName = defaultDisplayName.ifBlank { "Guest Builder" },
                builderLevel = 1L,
                createdAt = Timestamp.now(),
                updatedAt = Timestamp.now()
            )
            persistProfile(profile)
        }
        return Result.success(Unit)
    }

    override suspend fun updateDisplayName(newName: String): Result<Unit> {
        val clean = newName.trim().take(60)
        if (clean.isEmpty()) return Result.failure(IllegalArgumentException("Name cannot be blank"))
        val current = profileFlow.value ?: BuilderProfile(id = GUEST_UID, userId = GUEST_UID)
        persistProfile(current.copy(displayName = clean, updatedAt = Timestamp.now()))
        return Result.success(Unit)
    }

    override suspend fun incrementProfileStats(
        minedDelta: Long,
        placedDelta: Long,
        craftedDelta: Long
    ): Result<Unit> {
        val current = profileFlow.value ?: BuilderProfile(id = GUEST_UID, userId = GUEST_UID, displayName = "Guest Builder")
        val mined = (current.totalBlocksMined + max(0L, minedDelta)).coerceAtMost(10_000_000L)
        val placed = (current.totalBlocksPlaced + max(0L, placedDelta)).coerceAtMost(10_000_000L)
        val crafted = (current.totalItemsCrafted + max(0L, craftedDelta)).coerceAtMost(10_000_000L)
        val xp = mined * 2L + placed * 3L + crafted * 10L
        val level = (1L + xp / 60L).coerceIn(1L, 999L)
        persistProfile(
            current.copy(
                totalBlocksMined = mined,
                totalBlocksPlaced = placed,
                totalItemsCrafted = crafted,
                builderLevel = level,
                updatedAt = Timestamp.now()
            )
        )
        return Result.success(Unit)
    }

    override suspend fun createWorld(
        name: String,
        biome: BiomeType,
        gameMode: GameMode,
        seed: Long
    ): Result<VoxelWorldDoc> {
        val cleanName = name.trim().take(50).ifBlank { biome.displayName }
        val doc = VoxelWorldDoc(
            id = "guest_${UUID.randomUUID().toString().replace("-", "").take(12)}",
            userId = GUEST_UID,
            name = cleanName,
            biome = biome.name,
            gameMode = gameMode.name,
            seed = seed.coerceIn(0L, 999_999_999L),
            timeOfDay = 0.25,
            pickaxeTier = 1L,
            blocksMined = 0L,
            blocksPlaced = 0L,
            playerX = 8.5,
            playerY = 18.0,
            playerZ = 8.5,
            blockDeltas = emptyMap(),
            inventory = defaultStarterInventory(gameMode),
            hotbar = defaultHotbar(),
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )
        persistWorlds(listOf(doc) + worldsFlow.value)
        return Result.success(doc)
    }

    override suspend fun getWorld(worldId: String): Result<VoxelWorldDoc?> {
        return Result.success(worldsFlow.value.firstOrNull { it.id == worldId })
    }

    override suspend fun saveWorldState(
        worldId: String,
        gameMode: GameMode,
        timeOfDay: Double,
        pickaxeTier: Long,
        blocksMined: Long,
        blocksPlaced: Long,
        playerX: Double,
        playerY: Double,
        playerZ: Double,
        blockDeltas: Map<String, Long>,
        inventory: Map<String, Long>,
        hotbar: List<String>
    ): Result<Unit> {
        val updated = worldsFlow.value.map { w ->
            if (w.id == worldId) {
                w.copy(
                    gameMode = gameMode.name,
                    timeOfDay = timeOfDay.coerceIn(0.0, 1.0),
                    pickaxeTier = pickaxeTier.coerceIn(1L, 3L),
                    blocksMined = blocksMined.coerceAtLeast(0L),
                    blocksPlaced = blocksPlaced.coerceAtLeast(0L),
                    playerX = playerX.coerceIn(-10_000.0, 10_000.0),
                    playerY = playerY.coerceIn(-10_000.0, 10_000.0),
                    playerZ = playerZ.coerceIn(-10_000.0, 10_000.0),
                    blockDeltas = blockDeltas,
                    inventory = inventory,
                    hotbar = hotbar.take(9),
                    updatedAt = Timestamp.now()
                )
            } else {
                w
            }
        }
        persistWorlds(updated)
        return Result.success(Unit)
    }

    override suspend fun renameWorld(worldId: String, newName: String): Result<Unit> {
        val clean = newName.trim().take(50)
        if (clean.isEmpty()) return Result.failure(IllegalArgumentException("Name cannot be blank"))
        val updated = worldsFlow.value.map { w ->
            if (w.id == worldId) w.copy(name = clean, updatedAt = Timestamp.now()) else w
        }
        persistWorlds(updated)
        return Result.success(Unit)
    }

    override suspend fun deleteWorld(worldId: String): Result<Unit> {
        persistWorlds(worldsFlow.value.filterNot { it.id == worldId })
        return Result.success(Unit)
    }

    private fun persistProfile(profile: BuilderProfile) {
        profileFlow.value = profile
        val json = JSONObject().apply {
            put("displayName", profile.displayName)
            put("totalBlocksMined", profile.totalBlocksMined)
            put("totalBlocksPlaced", profile.totalBlocksPlaced)
            put("totalItemsCrafted", profile.totalItemsCrafted)
            put("builderLevel", profile.builderLevel)
        }
        prefs.edit().putString(KEY_PROFILE, json.toString()).apply()
    }

    private fun loadProfile(): BuilderProfile {
        val raw = prefs.getString(KEY_PROFILE, null)
        if (raw.isNullOrBlank()) {
            return BuilderProfile(
                id = GUEST_UID,
                userId = GUEST_UID,
                displayName = "Guest Builder",
                builderLevel = 1L
            )
        }
        return try {
            val j = JSONObject(raw)
            BuilderProfile(
                id = GUEST_UID,
                userId = GUEST_UID,
                displayName = j.optString("displayName", "Guest Builder"),
                totalBlocksMined = j.optLong("totalBlocksMined", 0L),
                totalBlocksPlaced = j.optLong("totalBlocksPlaced", 0L),
                totalItemsCrafted = j.optLong("totalItemsCrafted", 0L),
                builderLevel = j.optLong("builderLevel", 1L)
            )
        } catch (_: Exception) {
            BuilderProfile(id = GUEST_UID, userId = GUEST_UID, displayName = "Guest Builder")
        }
    }

    private fun persistWorlds(worlds: List<VoxelWorldDoc>) {
        worldsFlow.value = worlds
        val arr = JSONArray()
        worlds.forEach { w ->
            val obj = JSONObject().apply {
                put("id", w.id)
                put("name", w.name)
                put("biome", w.biome)
                put("gameMode", w.gameMode)
                put("seed", w.seed)
                put("timeOfDay", w.timeOfDay)
                put("pickaxeTier", w.pickaxeTier)
                put("blocksMined", w.blocksMined)
                put("blocksPlaced", w.blocksPlaced)
                put("playerX", w.playerX)
                put("playerY", w.playerY)
                put("playerZ", w.playerZ)
                put("blockDeltas", JSONObject().apply { w.blockDeltas.forEach { (k, v) -> put(k, v) } })
                put("inventory", JSONObject().apply { w.inventory.forEach { (k, v) -> put(k, v) } })
                put("hotbar", JSONArray(w.hotbar))
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_WORLDS, arr.toString()).apply()
    }

    private fun loadWorlds(): List<VoxelWorldDoc> {
        val raw = prefs.getString(KEY_WORLDS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                val deltasObj = o.optJSONObject("blockDeltas")
                val deltas = mutableMapOf<String, Long>()
                deltasObj?.keys()?.forEach { k -> deltas[k] = deltasObj.optLong(k, 0L) }

                val invObj = o.optJSONObject("inventory")
                val inv = mutableMapOf<String, Long>()
                invObj?.keys()?.forEach { k -> inv[k] = invObj.optLong(k, 0L) }

                val hbArr = o.optJSONArray("hotbar")
                val hb = mutableListOf<String>()
                if (hbArr != null) {
                    for (j in 0 until hbArr.length()) hb.add(hbArr.optString(j))
                }

                VoxelWorldDoc(
                    id = o.optString("id"),
                    userId = GUEST_UID,
                    name = o.optString("name", "Guest World"),
                    biome = o.optString("biome", BiomeType.EMERALD_VALLEY.name),
                    gameMode = o.optString("gameMode", GameMode.CREATIVE.name),
                    seed = o.optLong("seed", 424242L),
                    timeOfDay = o.optDouble("timeOfDay", 0.25),
                    pickaxeTier = o.optLong("pickaxeTier", 1L),
                    blocksMined = o.optLong("blocksMined", 0L),
                    blocksPlaced = o.optLong("blocksPlaced", 0L),
                    playerX = o.optDouble("playerX", 8.5),
                    playerY = o.optDouble("playerY", 18.0),
                    playerZ = o.optDouble("playerZ", 8.5),
                    blockDeltas = deltas,
                    inventory = inv,
                    hotbar = hb.ifEmpty { defaultHotbar() }
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        const val GUEST_UID = "guest_local_user"
        private const val PREFS_NAME = "blockforge_guest_store"
        private const val KEY_PROFILE = "guest_profile"
        private const val KEY_WORLDS = "guest_worlds"
    }
}
