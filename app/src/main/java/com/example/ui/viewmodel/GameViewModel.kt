package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.WorldDataSource
import com.example.data.model.BiomeType
import com.example.data.model.BlockType
import com.example.data.model.CraftingRecipe
import com.example.data.model.GameMode
import com.example.data.model.VoxelWorldDoc
import com.example.data.model.defaultHotbar
import com.example.data.model.defaultStarterInventory
import com.example.engine.audio.VoxelSoundEngine
import com.example.engine.gl.CHUNK_HEIGHT
import com.example.engine.gl.EntityKind
import com.example.engine.gl.HudTelemetry
import com.example.engine.gl.MinimapBlip
import com.example.engine.gl.VoxelChunkWorld
import com.example.engine.gl.VoxelEntityManager
import com.example.engine.gl.VoxelGLRenderer
import com.example.engine.gl.VoxelPlayerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.floor

enum class CloudSyncStatus {
    SYNCED,
    UNSAVED,
    SAVING,
    ERROR
}

enum class GraphicsQuality(val label: String, val renderDistance: Int) {
    LOW("Low (Fast)", 2),
    HIGH("High (Far)", 3)
}

enum class ControlStyle(val label: String) {
    MCPE_DPAD("MCPE D-Pad"),
    JOYSTICK("Joystick")
}

enum class WorldLandmark(val label: String, val dx: Int, val dz: Int) {
    SPAWN("Village Meadow", 8, 8),
    GARDEN("Flower Garden", 24, -14),
    WATERFALL("Mountain Waterfall", -26, 22),
    OCEAN("Ocean Shore", 42, 34)
}

data class GameWorldUiState(
    val loading: Boolean = true,
    val errorMessage: String? = null,
    val isCloudBacked: Boolean = true,
    val worldId: String = "",
    val worldName: String = "",
    val biome: BiomeType = BiomeType.EMERALD_VALLEY,
    val gameMode: GameMode = GameMode.SURVIVAL,
    val seed: Long = 1024L,
    val timeOfDay: Float = 0.22f,
    val autoTimeCycle: Boolean = true,
    val soundEnabled: Boolean = true,
    val gunEquipped: Boolean = false,
    val thirdPersonCamera: Boolean = false,
    val rainEnabled: Boolean = false,
    val controlStyle: ControlStyle = ControlStyle.JOYSTICK,
    val chatMessages: List<String> = listOf(
        "Welcome to BlockForge Bedrock! Push Joystick >85% forward to Auto-Sprint.",
        "Tip: Hold Break to mine, tap Place to build, or type /day /night /rain /pets"
    ),
    val recoilTick: Int = 0,
    val handSwingTick: Int = 0,
    val graphicsQuality: GraphicsQuality = GraphicsQuality.LOW,
    val showTouchControls: Boolean = true,
    val paused: Boolean = false,
    val pickaxeTier: Int = 1,
    val hotbar: List<BlockType> = defaultHotbar().map { BlockType.safeFromName(it) },
    val selectedHotbarIndex: Int = 0,
    val inventory: Map<BlockType, Int> = emptyMap(),
    val blocksMined: Long = 0L,
    val blocksPlaced: Long = 0L,
    val canUndo: Boolean = false,
    val fps: Int = 60,
    val playerX: Int = 8,
    val playerY: Int = 14,
    val playerZ: Int = 8,
    val playerYaw: Float = 0f,
    val playerHealth: Int = 10,
    val hunger: Int = 10,
    val oxygen: Int = 10,
    val inWater: Boolean = false,
    val isUnderwater: Boolean = false,
    val sprinting: Boolean = false,
    val sneaking: Boolean = false,
    val zombiesDefeated: Int = 0,
    val activeZombies: Int = 0,
    val totalEntities: Int = 0,
    val connectedAnimals: Int = 0,
    val minimapBlips: List<MinimapBlip> = emptyList(),
    val minimapColors: IntArray = IntArray(0),
    val targetBlock: BlockType? = null,
    val flying: Boolean = false,
    val syncStatus: CloudSyncStatus = CloudSyncStatus.SYNCED,
    val statusToast: String? = null
) {
    val selectedBlock: BlockType
        get() = hotbar.getOrElse(selectedHotbarIndex) { BlockType.GRASS }

    val xpLevel: Int
        get() = 1 + ((blocksMined + blocksPlaced + zombiesDefeated * 5L) / 15L).toInt()

    val xpProgress: Float
        get() = (((blocksMined + blocksPlaced + zombiesDefeated * 5L) % 15L) / 15f)

    fun countOf(block: BlockType): Int = inventory[block] ?: 0
}

class GameViewModel(
    private val repository: WorldDataSource,
    private val worldId: String
) : ViewModel() {

    private val _state = MutableStateFlow(
        GameWorldUiState(worldId = worldId, isCloudBacked = repository.isCloudBacked)
    )
    val state: StateFlow<GameWorldUiState> = _state.asStateFlow()

    val soundEngine = VoxelSoundEngine()

    var chunkWorld: VoxelChunkWorld? = null
        private set
    var playerController: VoxelPlayerController? = null
        private set
    var entityManager: VoxelEntityManager? = null
        private set
    var glRenderer: VoxelGLRenderer? = null
        private set

    private var unsyncedMinedDelta = 0L
    private var unsyncedPlacedDelta = 0L
    private var unsyncedCraftedDelta = 0L
    private var lastJumpTapMs = 0L

    init {
        loadWorld()
    }

    override fun onCleared() {
        soundEngine.release()
        super.onCleared()
    }

    fun loadWorld() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, errorMessage = null) }
            val result = repository.getWorld(worldId)
            result.fold(
                onSuccess = { doc ->
                    if (doc == null) {
                        _state.update { it.copy(loading = false, errorMessage = "World not found.") }
                    } else {
                        initializeWorld(doc)
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            loading = false,
                            errorMessage = error.localizedMessage ?: "Failed to load world."
                        )
                    }
                }
            )
        }
    }

    private fun initializeWorld(doc: VoxelWorldDoc) {
        val biome = doc.resolvedBiome()
        val mode = doc.resolvedGameMode()

        val world = VoxelChunkWorld(
            biome = biome,
            seed = doc.seed,
            initialDeltas = doc.blockDeltas
        )
        val spawnX = doc.playerX.toFloat()
        val spawnZ = doc.playerZ.toFloat()
        val safeY = world.findSpawnY(floor(spawnX).toInt(), floor(spawnZ).toInt())
        val spawnY = if (doc.playerY > 2.0 && doc.playerY < CHUNK_HEIGHT - 1) {
            maxOf(doc.playerY.toFloat(), safeY)
        } else {
            safeY
        }

        val controller = VoxelPlayerController(spawnX, spawnY, spawnZ).apply {
            flying = (mode == GameMode.CREATIVE)
        }
        val entities = VoxelEntityManager(doc.seed).apply {
            populateInitialEcosystem(world, spawnX, spawnZ)
        }
        val renderer = VoxelGLRenderer(
            world = world,
            player = controller,
            entityManager = entities,
            soundEngine = soundEngine,
            onTelemetry = { telemetry -> onTelemetryUpdate(telemetry) }
        ).apply {
            creativeMode = (mode == GameMode.CREATIVE)
            timeOfDay = doc.timeOfDay.toFloat().coerceIn(0f, 1f)
            autoTimeCycle = true
            renderDistance = GraphicsQuality.LOW.renderDistance
            paused = false
        }

        chunkWorld = world
        playerController = controller
        entityManager = entities
        glRenderer = renderer
        soundEngine.startAmbience()

        val rawInv = doc.inventory.ifEmpty { defaultStarterInventory(mode) }.toMutableMap()
        // Ensure unlimited Water Bucket & Flowers are ready to build with right away!
        if ((rawInv[BlockType.WATER.name] ?: 0L) < 16L) rawInv[BlockType.WATER.name] = 32L
        if ((rawInv[BlockType.FLOWER_RED.name] ?: 0L) < 8L) rawInv[BlockType.FLOWER_RED.name] = 16L
        val invMap = rawInv.entries.associate { (k, v) ->
            BlockType.safeFromName(k) to v.toInt().coerceAtLeast(0)
        }
        val hotbarList = (doc.hotbar.ifEmpty { defaultHotbar() })
            .take(9)
            .map { BlockType.safeFromName(it) }
            .toMutableList()
        if (hotbarList.size >= 9 && !hotbarList.contains(BlockType.WATER)) {
            hotbarList[8] = BlockType.WATER
        }

        _state.value = GameWorldUiState(
            loading = false,
            isCloudBacked = repository.isCloudBacked,
            worldId = doc.id.ifBlank { worldId },
            worldName = doc.name,
            biome = biome,
            gameMode = mode,
            seed = doc.seed,
            timeOfDay = doc.timeOfDay.toFloat().coerceIn(0f, 1f),
            autoTimeCycle = true,
            soundEnabled = soundEngine.soundEnabled,
            gunEquipped = false,
            controlStyle = ControlStyle.JOYSTICK,
            graphicsQuality = GraphicsQuality.LOW,
            showTouchControls = true,
            paused = false,
            pickaxeTier = doc.pickaxeTier.toInt().coerceIn(1, 3),
            hotbar = hotbarList,
            selectedHotbarIndex = 0,
            inventory = invMap,
            blocksMined = doc.blocksMined,
            blocksPlaced = doc.blocksPlaced,
            canUndo = false,
            playerX = floor(spawnX).toInt(),
            playerY = floor(spawnY).toInt(),
            playerZ = floor(spawnZ).toInt(),
            flying = controller.flying,
            syncStatus = CloudSyncStatus.SYNCED
        )
    }

    private fun onTelemetryUpdate(t: HudTelemetry) {
        _state.update { current ->
            current.copy(
                fps = t.fps,
                playerX = t.x,
                playerY = t.y,
                playerZ = t.z,
                playerYaw = t.yaw,
                targetBlock = t.targetBlock,
                flying = t.flying,
                inWater = t.inWater,
                isUnderwater = t.isUnderwater,
                oxygen = t.oxygen,
                sprinting = t.sprinting,
                sneaking = t.sneaking,
                timeOfDay = t.timeOfDay,
                playerHealth = t.playerHealth,
                zombiesDefeated = t.zombiesDefeated,
                activeZombies = t.activeZombies,
                totalEntities = t.totalEntities,
                connectedAnimals = t.connectedAnimals,
                minimapBlips = t.minimapBlips,
                minimapColors = t.minimapColors
            )
        }
    }

    fun setPaused(isPaused: Boolean) {
        glRenderer?.paused = isPaused
        _state.update { it.copy(paused = isPaused) }
    }

    fun fireGun() {
        val controller = playerController ?: return
        val entities = entityManager ?: return
        entities.fireGun(controller)
        glRenderer?.triggerArmSwing()
        soundEngine.playGunshot()
        _state.update {
            it.copy(
                gunEquipped = true,
                recoilTick = it.recoilTick + 1,
                handSwingTick = it.handSwingTick + 1
            )
        }
    }

    fun connectNearbyAnimals() {
        val controller = playerController ?: return
        val entities = entityManager ?: return
        val count = entities.connectNearbyAnimals(controller, radius = 18f)
        soundEngine.playHeartChime()
        val total = entities.connectedAnimalCount()
        _state.update {
            it.copy(
                connectedAnimals = total,
                statusToast = if (count > 0) {
                    "Connected $count new friends! ($total following you)"
                } else {
                    "$total animals & heroes connected & following you!"
                }
            )
        }
    }

    fun toggleThirdPersonCamera() {
        val next = !_state.value.thirdPersonCamera
        glRenderer?.thirdPersonCamera = next
        _state.update {
            it.copy(
                thirdPersonCamera = next,
                statusToast = if (next) "3rd-Person View" else "1st-Person View"
            )
        }
    }

    fun toggleRain() {
        val next = !_state.value.rainEnabled
        glRenderer?.rainEnabled = next
        _state.update {
            it.copy(
                rainEnabled = next,
                statusToast = if (next) "Rain Shower Started" else "Clear Sunny Sky"
            )
        }
    }

    fun setControlStyle(style: ControlStyle) {
        _state.update {
            it.copy(
                controlStyle = style,
                statusToast = "Controls: ${style.label}"
            )
        }
    }

    fun sendChatCommand(rawInput: String) {
        val text = rawInput.trim()
        if (text.isEmpty()) return
        val lower = text.lowercase()
        var reply: String? = null
        when {
            lower == "/day" || lower == "/time set day" -> {
                setTimeOfDayManual(0.25f)
                reply = "Set world time to Daylight"
            }
            lower == "/night" || lower == "/time set night" -> {
                setTimeOfDayManual(0.78f)
                reply = "Set world time to Starry Night"
            }
            lower == "/sunrise" -> {
                setTimeOfDayManual(0.05f)
                reply = "Set world time to Sunrise"
            }
            lower == "/rain" || lower == "/weather rain" -> {
                toggleRain()
                reply = if (_state.value.rainEnabled) "Rain shower enabled" else "Sky cleared"
            }
            lower == "/creative" || lower == "/gamemode c" -> {
                if (_state.value.gameMode != GameMode.CREATIVE) toggleGameMode()
                reply = "Game mode set to Creative"
            }
            lower == "/survival" || lower == "/gamemode s" -> {
                if (_state.value.gameMode != GameMode.SURVIVAL) toggleGameMode()
                reply = "Game mode set to Survival"
            }
            lower == "/pets" || lower == "/connect" -> {
                connectNearbyAnimals()
                reply = "Connected nearby animals & heroes!"
            }
            lower == "/zombies" -> {
                spawnZombieWave()
                reply = "Spawned 4 AI-Tracking Zombies!"
            }
        }
        _state.update { current ->
            val updated = (current.chatMessages + buildList {
                add("You: $text")
                if (reply != null) add("World: $reply")
            }).takeLast(14)
            current.copy(chatMessages = updated)
        }
    }

    fun toggleSprint() {
        val controller = playerController ?: return
        controller.sprinting = !controller.sprinting
        if (controller.sprinting) controller.sneaking = false
        _state.update {
            it.copy(
                sprinting = controller.sprinting,
                sneaking = controller.sneaking
            )
        }
    }

    fun toggleSneak() {
        val controller = playerController ?: return
        controller.sneaking = !controller.sneaking
        if (controller.sneaking) controller.sprinting = false
        _state.update {
            it.copy(
                sneaking = controller.sneaking,
                sprinting = controller.sprinting
            )
        }
    }

    fun setJumpHeld(held: Boolean) {
        val controller = playerController ?: return
        if (held && !controller.jumpHeld) {
            val now = System.currentTimeMillis()
            if (_state.value.gameMode == GameMode.CREATIVE && (now - lastJumpTapMs) < 320L) {
                toggleFlyMode()
                lastJumpTapMs = 0L
            } else {
                lastJumpTapMs = now
                controller.jumpRequested = true
                if (!controller.inWater) soundEngine.playJump()
            }
        }
        controller.jumpHeld = held
    }

    fun spawnCharacter(kind: EntityKind) {
        val world = chunkWorld ?: return
        val controller = playerController ?: return
        val entities = entityManager ?: return
        entities.spawnEntityInFront(world, controller, kind)
        soundEngine.playAnimalCall(kind.soundStyle)
        _state.update { it.copy(statusToast = "Spawned ${kind.displayName}!") }
    }

    fun spawnZombieWave() {
        val world = chunkWorld ?: return
        val controller = playerController ?: return
        val entities = entityManager ?: return
        entities.spawnZombieWave(world, controller, count = 4)
        soundEngine.playZombieGroan()
        _state.update { it.copy(statusToast = "4 AI-Tracking Zombies Incoming!") }
    }

    fun teleportToLandmark(landmark: WorldLandmark) {
        val world = chunkWorld ?: return
        val controller = playerController ?: return
        val destY = world.findSpawnY(landmark.dx, landmark.dz)
        controller.x = landmark.dx + 0.5f
        controller.y = destY
        controller.z = landmark.dz + 0.5f
        soundEngine.playAnimalCall("HERO")
        _state.update {
            it.copy(
                playerX = landmark.dx,
                playerY = floor(destY).toInt(),
                playerZ = landmark.dz,
                statusToast = "Explored ${landmark.label}!"
            )
        }
    }

    fun toggleSound() {
        val next = !soundEngine.soundEnabled
        soundEngine.soundEnabled = next
        _state.update {
            it.copy(
                soundEnabled = next,
                statusToast = if (next) "Real Sound & Nature SFX ON" else "Sound Muted"
            )
        }
    }

    fun setTimeOfDayManual(t: Float) {
        val clamped = t.coerceIn(0f, 1f)
        glRenderer?.timeOfDay = clamped
        glRenderer?.autoTimeCycle = false
        _state.update {
            it.copy(
                timeOfDay = clamped,
                autoTimeCycle = false,
                syncStatus = CloudSyncStatus.UNSAVED
            )
        }
    }

    fun selectHotbarSlot(index: Int) {
        _state.update {
            it.copy(
                gunEquipped = false,
                selectedHotbarIndex = index.coerceIn(0, (it.hotbar.size - 1).coerceAtLeast(0))
            )
        }
    }

    fun equipBlockToHotbar(block: BlockType) {
        _state.update { current ->
            val existingIdx = current.hotbar.indexOf(block)
            if (existingIdx >= 0) {
                current.copy(gunEquipped = false, selectedHotbarIndex = existingIdx)
            } else {
                val updated = current.hotbar.toMutableList()
                val slot = current.selectedHotbarIndex.coerceIn(0, (updated.size - 1).coerceAtLeast(0))
                if (updated.isNotEmpty()) updated[slot] = block else updated.add(block)
                current.copy(
                    gunEquipped = false,
                    hotbar = updated,
                    selectedHotbarIndex = slot,
                    syncStatus = CloudSyncStatus.UNSAVED
                )
            }
        }
    }

    fun toggleFlyMode() {
        val current = _state.value
        val controller = playerController ?: return
        if (current.gameMode != GameMode.CREATIVE) {
            _state.update { it.copy(statusToast = "Switch to Creative Mode to fly!") }
            return
        }
        controller.flying = !controller.flying
        _state.update {
            it.copy(
                flying = controller.flying,
                statusToast = if (controller.flying) "Flight Enabled" else "Flight Disabled"
            )
        }
    }

    fun toggleGameMode() {
        val current = _state.value
        val nextMode = if (current.gameMode == GameMode.SURVIVAL) GameMode.CREATIVE else GameMode.SURVIVAL
        val isCreative = (nextMode == GameMode.CREATIVE)
        glRenderer?.creativeMode = isCreative
        if (!isCreative) {
            playerController?.flying = false
        }
        _state.update {
            it.copy(
                gameMode = nextMode,
                flying = if (isCreative) it.flying else false,
                syncStatus = CloudSyncStatus.UNSAVED,
                statusToast = "Switched to ${nextMode.displayName} Mode"
            )
        }
    }

    fun setGraphicsQuality(quality: GraphicsQuality) {
        glRenderer?.renderDistance = quality.renderDistance
        _state.update {
            it.copy(
                graphicsQuality = quality,
                statusToast = "Graphics: ${quality.label}"
            )
        }
    }

    fun toggleTouchControls() {
        _state.update { it.copy(showTouchControls = !it.showTouchControls) }
    }

    fun toggleAutoTimeCycle() {
        val next = !_state.value.autoTimeCycle
        glRenderer?.autoTimeCycle = next
        _state.update { it.copy(autoTimeCycle = next) }
    }

    fun cycleTimePreset() {
        val currentT = glRenderer?.timeOfDay ?: _state.value.timeOfDay
        val nextT = when {
            currentT < 0.16f -> 0.25f
            currentT < 0.42f -> 0.50f
            currentT < 0.70f -> 0.78f
            else -> 0.04f
        }
        setTimeOfDayManual(nextT)
    }

    fun breakTargetedBlock(): Boolean {
        val world = chunkWorld ?: return false
        val hit = glRenderer?.currentHit
        glRenderer?.triggerArmSwing()
        _state.update { it.copy(gunEquipped = false, handSwingTick = it.handSwingTick + 1) }

        if (hit == null) {
            soundEngine.playSwing()
            return false
        }

        val current = _state.value
        val target = hit.block
        if (target == BlockType.AIR) return false
        if (target.unbreakable) {
            _state.update { it.copy(statusToast = "Bedrock is indestructible!") }
            return false
        }
        if (current.gameMode == GameMode.SURVIVAL && target.minPickaxeTier > current.pickaxeTier) {
            val toolName = if (target.minPickaxeTier == 2) "Iron Pickaxe (T2)" else "Diamond Pickaxe (T3)"
            _state.update { it.copy(statusToast = "Craft $toolName to mine ${target.displayName}") }
            return false
        }

        world.setBlock(hit.x, hit.y, hit.z, BlockType.AIR, recordUndo = true)
        entityManager?.spawnBlockBreakParticles(
            hit.x, hit.y, hit.z,
            target.topColor.red, target.topColor.green, target.topColor.blue
        )
        soundEngine.playBlockBreak()
        unsyncedMinedDelta += 1L

        val updatedInv = current.inventory.toMutableMap()
        if (current.gameMode == GameMode.SURVIVAL) {
            val drop = target.survivalDrop()
            updatedInv[drop] = ((updatedInv[drop] ?: 0) + 1).coerceAtMost(999)
        }
        _state.update {
            it.copy(
                inventory = updatedInv,
                blocksMined = it.blocksMined + 1L,
                canUndo = world.canUndo(),
                syncStatus = CloudSyncStatus.UNSAVED
            )
        }
        return true
    }

    fun placeBlockAtTarget(): Boolean {
        val world = chunkWorld ?: return false
        val controller = playerController ?: return false
        val hit = glRenderer?.currentHit ?: return false
        val current = _state.value

        val px = hit.x + hit.nx
        val py = hit.y + hit.ny
        val pz = hit.z + hit.nz
        if (py !in 1 until CHUNK_HEIGHT) {
            _state.update { it.copy(statusToast = "Build height limit reached!") }
            return false
        }

        val blockToPlace = current.selectedBlock
        if (blockToPlace != BlockType.WATER && blockToPlace != BlockType.WATERFALL && !blockToPlace.crossPlant) {
            if (controller.overlapsBlock(px, py, pz)) {
                _state.update { it.copy(statusToast = "Step back to place block here") }
                return false
            }
        }

        val updatedInv = current.inventory.toMutableMap()
        // Water Bucket is unlimited ("Water Unlimited"), other blocks consume inventory in Survival
        val isUnlimitedWater = (blockToPlace == BlockType.WATER || blockToPlace == BlockType.WATERFALL)
        if (current.gameMode == GameMode.SURVIVAL && !isUnlimitedWater) {
            val count = updatedInv[blockToPlace] ?: 0
            if (count <= 0) {
                _state.update { it.copy(statusToast = "Out of ${blockToPlace.displayName}! Mine or Craft more.") }
                return false
            }
            updatedInv[blockToPlace] = count - 1
        }

        world.setBlock(px, py, pz, blockToPlace, recordUndo = true)
        glRenderer?.triggerArmSwing()
        if (isUnlimitedWater) {
            soundEngine.playWaterSplash()
            entityManager?.spawnWaterSplashParticles(px + 0.5f, py + 0.5f, pz + 0.5f)
        } else {
            soundEngine.playBlockPlace()
        }
        unsyncedPlacedDelta += 1L

        _state.update {
            it.copy(
                gunEquipped = false,
                handSwingTick = it.handSwingTick + 1,
                inventory = updatedInv,
                blocksPlaced = it.blocksPlaced + 1L,
                canUndo = world.canUndo(),
                syncStatus = CloudSyncStatus.UNSAVED
            )
        }
        return true
    }

    fun undoLastEdit() {
        val world = chunkWorld ?: return
        val undone = world.undo() ?: return
        _state.update { current ->
            val updatedInv = current.inventory.toMutableMap()
            if (current.gameMode == GameMode.SURVIVAL && undone.updated != BlockType.AIR) {
                updatedInv[undone.updated] = (updatedInv[undone.updated] ?: 0) + 1
            }
            current.copy(
                inventory = updatedInv,
                canUndo = world.canUndo(),
                syncStatus = CloudSyncStatus.UNSAVED,
                statusToast = "Undid last edit"
            )
        }
    }

    fun canCraft(recipe: CraftingRecipe): Boolean {
        val current = _state.value
        if (recipe.unlocksPickaxeTier != null && current.pickaxeTier >= recipe.unlocksPickaxeTier) {
            return false
        }
        if (current.gameMode == GameMode.CREATIVE) return true
        return recipe.ingredients.all { (block, needed) -> (current.inventory[block] ?: 0) >= needed }
    }

    fun craftRecipe(recipe: CraftingRecipe) {
        val current = _state.value
        if (!canCraft(recipe)) {
            _state.update { it.copy(statusToast = "Missing ingredients for ${recipe.title}") }
            return
        }
        val updatedInv = current.inventory.toMutableMap()
        if (current.gameMode == GameMode.SURVIVAL) {
            recipe.ingredients.forEach { (block, needed) ->
                updatedInv[block] = ((updatedInv[block] ?: 0) - needed).coerceAtLeast(0)
            }
        }
        val nextTier = if (recipe.unlocksPickaxeTier != null) {
            maxOf(current.pickaxeTier, recipe.unlocksPickaxeTier)
        } else {
            current.pickaxeTier
        }
        if (recipe.outputBlock != null) {
            updatedInv[recipe.outputBlock] =
                ((updatedInv[recipe.outputBlock] ?: 0) + recipe.outputCount).coerceAtMost(999)
        }
        unsyncedCraftedDelta += 1L
        soundEngine.playAnimalCall("HERO")
        _state.update {
            it.copy(
                inventory = updatedInv,
                pickaxeTier = nextTier,
                syncStatus = CloudSyncStatus.UNSAVED,
                statusToast = "Crafted ${recipe.title}!"
            )
        }
    }

    fun clearToast() {
        _state.update { it.copy(statusToast = null) }
    }

    fun saveWorld(onComplete: (() -> Unit)? = null) {
        val world = chunkWorld ?: run {
            onComplete?.invoke()
            return
        }
        val controller = playerController
        val current = _state.value
        if (current.loading || current.worldId.isBlank()) {
            onComplete?.invoke()
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(syncStatus = CloudSyncStatus.SAVING) }
            val invLongMap = current.inventory
                .filterValues { it > 0 }
                .entries
                .associate { it.key.name to it.value.toLong() }
            val hotbarNames = current.hotbar.map { it.name }

            val result = repository.saveWorldState(
                worldId = current.worldId,
                gameMode = current.gameMode,
                timeOfDay = (glRenderer?.timeOfDay ?: current.timeOfDay).toDouble(),
                pickaxeTier = current.pickaxeTier.toLong(),
                blocksMined = current.blocksMined,
                blocksPlaced = current.blocksPlaced,
                playerX = (controller?.x ?: current.playerX.toFloat()).toDouble(),
                playerY = (controller?.y ?: current.playerY.toFloat()).toDouble(),
                playerZ = (controller?.z ?: current.playerZ.toFloat()).toDouble(),
                blockDeltas = world.exportDeltas(),
                inventory = invLongMap,
                hotbar = hotbarNames
            )

            val minedDelta = unsyncedMinedDelta
            val placedDelta = unsyncedPlacedDelta
            val craftedDelta = unsyncedCraftedDelta

            if (result.isSuccess) {
                unsyncedMinedDelta = 0L
                unsyncedPlacedDelta = 0L
                unsyncedCraftedDelta = 0L
                repository.incrementProfileStats(minedDelta, placedDelta, craftedDelta)
                val dest = if (repository.isCloudBacked) "Saved to cloud" else "Saved locally (Guest)"
                _state.update {
                    it.copy(syncStatus = CloudSyncStatus.SYNCED, statusToast = dest)
                }
            } else {
                _state.update {
                    it.copy(
                        syncStatus = CloudSyncStatus.ERROR,
                        statusToast = result.exceptionOrNull()?.localizedMessage ?: "Save failed"
                    )
                }
            }
            onComplete?.invoke()
        }
    }
}
