package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.HindiNpcVoiceAgent
import com.example.ai.NpcChatMessage
import com.example.data.AdminCustom3DModel
import com.example.data.AdminStudioSnapshot
import com.example.data.AdminStudioStore
import com.example.data.CustomModelRole
import com.example.data.WorldDataSource
import com.example.data.model.BikeType
import com.example.data.model.BiomeType
import com.example.data.model.BlockType
import com.example.data.model.CraftingRecipe
import com.example.data.model.GameMode
import com.example.data.model.ShopProduct
import com.example.data.model.ShopRewardType
import com.example.data.model.VoxelWorldDoc
import com.example.data.model.defaultHotbar
import com.example.data.model.defaultStarterInventory
import com.example.engine.audio.VoxelSoundEngine
import com.example.engine.gl.AiGoal
import com.example.engine.gl.CHUNK_HEIGHT
import com.example.engine.gl.CameraMode
import com.example.engine.gl.EntityKind
import com.example.engine.gl.HudTelemetry
import com.example.engine.gl.MinimapBlip
import com.example.engine.gl.TalkableNpcSnapshot
import com.example.engine.gl.VoxelChunkWorld
import com.example.engine.gl.VoxelEntityManager
import com.example.engine.gl.VoxelGLRenderer
import com.example.engine.gl.VoxelPlayerController
import com.example.ui.screens.REAL_ESTATE_CATALOG
import com.example.ui.screens.RealEstateProperty
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
    LOW("Wide City (4 Chunks)", 4),
    HIGH("Mega Map (5 Chunks)", 5),
    ULTRA("Ultra Horizon (6 Chunks)", 6)
}

enum class ControlStyle(val label: String) {
    MCPE_DPAD("MCPE D-Pad"),
    JOYSTICK("Joystick")
}

enum class WorldLandmark(
    val label: String,
    val dx: Int,
    val dz: Int,
    val isRealEstate: Boolean = false,
    val facingYaw: Float = 0f
) {
    REAL_ESTATE_BOULEVARD("City Boulevard (रियल एस्टेट रोड)", 26, 28, isRealEstate = true, facingYaw = 1.57f),
    LUXURY_VILLA("Luxury Villa + Pool (बंगला)", 39, 32, isRealEstate = true, facingYaw = 3.14f),
    APARTMENT_TOWER("4-Story Skyview Tower (फ्लैट्स)", 10, 34, isRealEstate = true, facingYaw = 3.14f),
    SHOPPING_MALL("Commercial Plaza Mall (मॉल)", -18, 34, isRealEstate = true, facingYaw = 3.14f),
    FARMHOUSE("Emerald Farmhouse (फार्महाउस)", -42, 33, isRealEstate = true, facingYaw = 3.14f),
    RESIDENTIAL_PLOTS("Sector-A Plots (रेडी प्लॉट्स)", 44, 23, isRealEstate = true, facingYaw = 0f),
    SPAWN("Village Square", 8, 8),
    MANDIR("Sacred Mandir (मंदिर)", 18, 8),
    PALACE("Royal Palace (राजमहल)", -8, 18),
    FLOWER_VALLEY("Flower Garden (फूलों की घाटी)", 24, -18),
    PYRAMID("Desert Pyramid (पिरामिड)", 38, -16),
    VOLCANO("Jwalamukhi Volcano (ज्वालामुखी)", -34, -14),
    WATERFALL("Mountain Waterfall (झरना)", -26, 22),
    SKY_PEAKS("High Mountain Peak (पहाड़)", -46, 36),
    OCEAN("Ocean Shore (समुद्र तट)", 42, 34)
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
    val cameraMode: CameraMode = CameraMode.FIRST_PERSON,
    val thirdPersonCamera: Boolean = false,
    val rainEnabled: Boolean = false,
    val controlStyle: ControlStyle = ControlStyle.JOYSTICK,
    val chatMessages: List<String> = listOf(
        "BlockForge में स्वागत है! रास्ता काटकर पानी/लावा कहीं भी बहाएं, मंदिर और ज्वालामुखी देखें।",
        "Tip: AI Dost बटन से राजा, पुजारी, मज़दूर व श्री कृष्ण से हिंदी आवाज़ में बात करें!"
    ),
    val recoilTick: Int = 0,
    val handSwingTick: Int = 0,
    val graphicsQuality: GraphicsQuality = GraphicsQuality.LOW,
    val showTouchControls: Boolean = true,
    val showTopHud: Boolean = true,
    val showVoiceHud: Boolean = true,
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
    val playerY: Int = 18,
    val playerZ: Int = 8,
    val playerYaw: Float = 0f,
    val playerHealth: Int = 10,
    val hunger: Int = 10,
    val oxygen: Int = 10,
    val inWater: Boolean = false,
    val isUnderwater: Boolean = false,
    val inLava: Boolean = false,
    val sittingOnWater: Boolean = false,
    val waterAvailableForSit: Boolean = false,
    val activeBike: BikeType? = null,
    val bikeSpeedKmh: Int = 0,
    val bikeNitroActive: Boolean = false,
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
    val jetBootsUnlocked: Boolean = false,
    val goldCoins: Int = 500,
    val ownedProductIds: Set<String> = emptySet(),
    val ownedRealEstateIds: Set<String> = emptySet(),
    val pendingRentCoins: Int = 0,
    val talkableNpcs: List<TalkableNpcSnapshot> = emptyList(),
    val activeNpc: TalkableNpcSnapshot? = null,
    val npcChatHistory: List<NpcChatMessage> = emptyList(),
    val npcAiThinking: Boolean = false,
    val npcAiError: String? = null,
    val isVoiceListening: Boolean = false,
    val isVoiceSpeaking: Boolean = false,
    val voiceStatusText: String = "Hindi Voice Ready",
    val nearbyEntity: TalkableNpcSnapshot? = null,
    val nearbyEntities: List<TalkableNpcSnapshot> = emptyList(),
    val selectedNearbyEntityId: Int? = null,
    val helloActiveEntityId: Int? = null,
    val liveSubtitleText: String? = null,
    val playerDamageFlashTick: Int = 0,
    val adminSnapshot: AdminStudioSnapshot = AdminStudioSnapshot(),
    val syncStatus: CloudSyncStatus = CloudSyncStatus.SYNCED,
    val statusToast: String? = null
) {
    val activeNearbyEntity: TalkableNpcSnapshot?
        get() = nearbyEntities.firstOrNull { it.id == selectedNearbyEntityId }
            ?: nearbyEntities.firstOrNull()
            ?: nearbyEntity

    val isHelloConversationActive: Boolean
        get() = activeNearbyEntity != null && helloActiveEntityId == activeNearbyEntity?.id

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
    private val worldId: String,
    private val appContext: Context? = null
) : ViewModel() {

    private val _state = MutableStateFlow(
        GameWorldUiState(worldId = worldId, isCloudBacked = repository.isCloudBacked)
    )
    val state: StateFlow<GameWorldUiState> = _state.asStateFlow()
    private var lastRentAccrualMs = System.currentTimeMillis()

    val soundEngine = VoxelSoundEngine()
    private val voiceAgent: HindiNpcVoiceAgent? = appContext?.let { HindiNpcVoiceAgent(it) }
    private val adminStore: AdminStudioStore? = appContext?.let { AdminStudioStore.getInstance(it) }

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
    private var lastKnownKills = 0
    private var lastKnownDamageTick = 0
    private val entityGreetingCooldownMs = HashMap<Int, Long>()
    private val npcHistories = HashMap<Int, MutableList<NpcChatMessage>>()

    init {
        observeVoiceAgent()
        observeAdminStudio()
        loadWorld()
    }

    private fun observeAdminStudio() {
        val store = adminStore ?: return
        viewModelScope.launch {
            store.snapshot.collect { snap ->
                _state.update { it.copy(adminSnapshot = snap) }
                applyEquippedCustomModelToPlayer(snap)
            }
        }
    }

    private fun applyEquippedCustomModelToPlayer(snap: AdminStudioSnapshot) {
        val entities = entityManager ?: return
        val store = adminStore ?: return
        val equippedId = snap.equippedPublicModelId
        if (equippedId == null) {
            entities.playerCustomModel = null
            entities.playerCustomMesh = null
            return
        }
        val model = snap.customModels.firstOrNull { it.id == equippedId }
        entities.playerCustomModel = model
        entities.playerCustomMesh = model?.uploadedObjFileName?.let { store.loadParsedMesh(it) }
    }

    private fun observeVoiceAgent() {
        val agent = voiceAgent ?: return
        viewModelScope.launch {
            agent.isListening.collect { listening ->
                _state.update { it.copy(isVoiceListening = listening) }
            }
        }
        viewModelScope.launch {
            agent.isSpeaking.collect { speaking ->
                _state.update { it.copy(isVoiceSpeaking = speaking) }
            }
        }
        viewModelScope.launch {
            agent.voiceStatus.collect { status ->
                _state.update { it.copy(voiceStatusText = status) }
            }
        }
    }

    override fun onCleared() {
        voiceAgent?.release()
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
        val spawnY = if (doc.playerY > 2.0 && doc.playerY < CHUNK_HEIGHT - 2) {
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
        if ((rawInv[BlockType.WATER.name] ?: 0L) < 16L) rawInv[BlockType.WATER.name] = 32L
        if ((rawInv[BlockType.LAVA.name] ?: 0L) < 8L) rawInv[BlockType.LAVA.name] = 16L
        if ((rawInv[BlockType.FLOWER_RED.name] ?: 0L) < 8L) rawInv[BlockType.FLOWER_RED.name] = 16L
        if ((rawInv[BlockType.FLOWER_PINK.name] ?: 0L) < 8L) rawInv[BlockType.FLOWER_PINK.name] = 16L
        if ((rawInv[BlockType.FLOWER_PURPLE.name] ?: 0L) < 8L) rawInv[BlockType.FLOWER_PURPLE.name] = 16L
        if ((rawInv[BlockType.FLOWER_SUNFLOWER.name] ?: 0L) < 8L) rawInv[BlockType.FLOWER_SUNFLOWER.name] = 16L
        if ((rawInv[BlockType.CHERRY_LEAVES.name] ?: 0L) < 12L) rawInv[BlockType.CHERRY_LEAVES.name] = 24L
        val invMap = rawInv.entries.associate { (k, v) ->
            BlockType.safeFromName(k) to v.toInt().coerceAtLeast(0)
        }
        val hotbarList = (doc.hotbar.ifEmpty { defaultHotbar() })
            .take(9)
            .map { BlockType.safeFromName(it) }
            .toMutableList()
        if (hotbarList.size >= 9) {
            if (!hotbarList.contains(BlockType.WATER)) hotbarList[7] = BlockType.WATER
            if (!hotbarList.contains(BlockType.LAVA)) hotbarList[8] = BlockType.LAVA
        }

        val npcs = entities.getTalkableSnapshots(controller)
        val currentAdminSnap = adminStore?.snapshot?.value ?: AdminStudioSnapshot()
        applyEquippedCustomModelToPlayer(currentAdminSnap)

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
            goldCoins = 300,
            talkableNpcs = npcs,
            activeNpc = npcs.firstOrNull(),
            adminSnapshot = currentAdminSnap,
            syncStatus = CloudSyncStatus.SYNCED
        )
    }

    private fun onTelemetryUpdate(t: HudTelemetry) {
        val extraKills = (t.zombiesDefeated - lastKnownKills).coerceAtLeast(0)
        if (extraKills > 0) {
            lastKnownKills = t.zombiesDefeated
        }
        val earnedCoinBonus = extraKills * 25
        val newDamageFlash = t.damageTick > lastKnownDamageTick
        if (newDamageFlash) {
            lastKnownDamageTick = t.damageTick
        }

        // Player-controlled Conversation Mode:
        // Characters NEVER speak uninvited! When you walk near them they STOP and face you silently.
        // ONLY when you tap "हेलो (Hello)" or speak "हेलो" into Mic do they reply aloud!
        val nearbyList = t.nearbyEntities
        val currentState = _state.value
        val keptSelection = nearbyList.firstOrNull { it.id == currentState.selectedNearbyEntityId }
        val resolvedTarget = keptSelection ?: nearbyList.firstOrNull()
        val keptHelloId = if (nearbyList.any { it.id == currentState.helloActiveEntityId }) {
            currentState.helloActiveEntityId
        } else {
            null
        }
        entityManager?.preferredTalkTargetId = resolvedTarget?.id

        val nowMs = System.currentTimeMillis()
        var accruedRent = 0
        if (!currentState.paused && currentState.ownedRealEstateIds.isNotEmpty() && nowMs - lastRentAccrualMs >= 16_000L) {
            lastRentAccrualMs = nowMs
            accruedRent = REAL_ESTATE_CATALOG
                .filter { it.id in currentState.ownedRealEstateIds }
                .sumOf { it.rentPerCycle }
        }

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
                inLava = t.inLava,
                sittingOnWater = t.sittingOnWater,
                waterAvailableForSit = t.waterAvailableForSit,
                activeBike = t.activeBike,
                bikeSpeedKmh = t.bikeSpeedKmh,
                bikeNitroActive = t.bikeNitroActive,
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
                minimapColors = t.minimapColors,
                nearbyEntity = resolvedTarget,
                nearbyEntities = nearbyList,
                selectedNearbyEntityId = resolvedTarget?.id,
                helloActiveEntityId = keptHelloId,
                playerDamageFlashTick = if (newDamageFlash) current.playerDamageFlashTick + 1 else current.playerDamageFlashTick,
                goldCoins = current.goldCoins + earnedCoinBonus,
                pendingRentCoins = current.pendingRentCoins + accruedRent,
                statusToast = t.worldEventToast ?: current.statusToast
            )
        }
    }

    fun toggleTopHud() {
        val nextShow = !_state.value.showTopHud
        if (!nextShow) {
            voiceAgent?.stopListening()
        }
        _state.update {
            it.copy(
                showTopHud = nextShow,
                liveSubtitleText = if (!nextShow) null else it.liveSubtitleText
            )
        }
    }

    fun toggleVoiceHud() {
        val nextShow = !_state.value.showVoiceHud
        if (!nextShow) {
            voiceAgent?.stopListening()
            voiceAgent?.stopSpeaking()
        }
        _state.update {
            it.copy(
                showVoiceHud = nextShow,
                liveSubtitleText = if (!nextShow) null else it.liveSubtitleText,
                statusToast = if (nextShow) "वॉइस कन्वर्सेशन दिख रहा है" else "वॉइस कन्वर्सेशन छुपा दिया गया (Fullscreen Clean)"
            )
        }
    }

    fun toggleWaterSit() {
        val world = chunkWorld ?: return
        val controller = playerController ?: return
        val wasSitting = controller.sittingOnWater
        val nowSitting = controller.toggleWaterSit(world)
        if (nowSitting) {
            soundEngine.playWaterSitSplash()
            entityManager?.spawnWaterSitRipples(controller.x, controller.y, controller.z)
            if (_state.value.cameraMode == CameraMode.FIRST_PERSON) {
                glRenderer?.cameraMode = CameraMode.THIRD_PERSON_BACK
                controller.resetCameraLevel()
            }
            _state.update {
                val nextCam = if (it.cameraMode == CameraMode.FIRST_PERSON) CameraMode.THIRD_PERSON_BACK else it.cameraMode
                it.copy(
                    sittingOnWater = true,
                    activeBike = null,
                    bikeSpeedKmh = 0,
                    bikeNitroActive = false,
                    cameraMode = nextCam,
                    thirdPersonCamera = nextCam != CameraMode.FIRST_PERSON,
                    statusToast = "🌊 आप पानी पर आराम से बैठ गए हैं! (जॉयस्टिक से तैरें या उठें)"
                )
            }
        } else if (wasSitting) {
            soundEngine.playWaterSplash()
            _state.update {
                it.copy(
                    sittingOnWater = false,
                    statusToast = "आप पानी से खड़े हो गए!"
                )
            }
        } else {
            _state.update {
                it.copy(statusToast = "पानी के किनारे या पानी में उतरकर बैठें!")
            }
        }
    }

    fun mountBike(bike: BikeType) {
        val controller = playerController ?: return
        controller.mountBike(bike)
        soundEngine.playBikeStart(bike.soundStyle)
        if (_state.value.cameraMode == CameraMode.FIRST_PERSON) {
            glRenderer?.cameraMode = CameraMode.THIRD_PERSON_BACK
            controller.resetCameraLevel()
        }
        _state.update {
            val nextCam = if (it.cameraMode == CameraMode.FIRST_PERSON) CameraMode.THIRD_PERSON_BACK else it.cameraMode
            it.copy(
                activeBike = bike,
                sittingOnWater = false,
                flying = false,
                sneaking = false,
                cameraMode = nextCam,
                thirdPersonCamera = nextCam != CameraMode.FIRST_PERSON,
                statusToast = "🏍️ ${bike.displayName} स्टार्ट! जॉयस्टिक से राइड करें"
            )
        }
    }

    fun dismountBike() {
        val controller = playerController ?: return
        val prevBike = controller.activeBike ?: return
        controller.dismountBike()
        _state.update {
            it.copy(
                activeBike = null,
                bikeSpeedKmh = 0,
                bikeNitroActive = false,
                statusToast = "${prevBike.displayName} पार्क कर दी गई!"
            )
        }
    }

    fun honkBikeHorn() {
        val bike = playerController?.activeBike ?: return
        soundEngine.playBikeHorn(bike.soundStyle)
        val c = playerController
        if (c != null) {
            entityManager?.spawnBikeExhaustParticles(
                c.x,
                c.y,
                c.z,
                nitro = false,
                isElectric = bike == BikeType.CYBER_TRON
            )
        }
    }

    fun triggerBikeNitro() {
        val controller = playerController ?: return
        val bike = controller.activeBike ?: return
        if (controller.triggerBikeNitro()) {
            soundEngine.playBikeNitro()
            entityManager?.spawnBikeExhaustParticles(
                controller.x,
                controller.y,
                controller.z,
                nitro = true,
                isElectric = bike == BikeType.CYBER_TRON
            )
            _state.update {
                it.copy(
                    bikeNitroActive = true,
                    statusToast = "⚡ ${bike.displayName} NITRO TURBO BOOST!"
                )
            }
        }
    }

    fun cycleNearbyEntity() {
        val list = _state.value.nearbyEntities
        if (list.size <= 1) return
        val curIdx = list.indexOfFirst { it.id == _state.value.selectedNearbyEntityId }.coerceAtLeast(0)
        val next = list[(curIdx + 1) % list.size]
        entityManager?.preferredTalkTargetId = next.id
        _state.update {
            it.copy(
                selectedNearbyEntityId = next.id,
                nearbyEntity = next,
                helloActiveEntityId = if (it.helloActiveEntityId == next.id) next.id else null
            )
        }
    }

    /**
     * Player explicitly says "Hello" to the chosen nearby character who stopped for them!
     * Only NOW does that character speak aloud in Hindi with lip-sync and unlock voice commands.
     */
    fun sayHelloToNearbyEntity(targetOverride: TalkableNpcSnapshot? = null) {
        val target = targetOverride ?: _state.value.activeNearbyEntity ?: return
        entityManager?.preferredTalkTargetId = target.id
        entityManager?.triggerEntityTalking(target.id, durationSec = 4.8f)
        if (target.isAnimal) {
            soundEngine.playAnimalCall(target.kind.soundStyle)
        } else {
            soundEngine.playHumanGreetingChime()
        }
        val greeting = voiceAgent?.buildProximityBehaviorGreeting(target)
            ?: "हेलो दोस्त! मैं ${target.displayName} हूँ। बताइए क्या बात करनी है या क्या काम करना है?"
        voiceAgent?.speakHindi(greeting, target.kind)
        val list = npcHistories.getOrPut(target.id) { mutableListOf() }
        list.add(NpcChatMessage("आप", "हेलो!", isPlayer = true))
        list.add(NpcChatMessage(target.displayName, greeting, isPlayer = false))
        _state.update {
            it.copy(
                selectedNearbyEntityId = target.id,
                nearbyEntity = target,
                helloActiveEntityId = target.id,
                activeNpc = target,
                npcChatHistory = list.toList(),
                liveSubtitleText = "${target.displayName}: $greeting"
            )
        }
    }

    fun endNearbyConversation() {
        voiceAgent?.stopSpeaking()
        voiceAgent?.stopListening()
        _state.update {
            it.copy(
                helloActiveEntityId = null,
                liveSubtitleText = null
            )
        }
    }

    fun clearLiveSubtitle() {
        _state.update { it.copy(liveSubtitleText = null) }
    }

    // --- AI HINDI VOICE FRIEND SYSTEM ---

    fun refreshTalkableNpcs() {
        val controller = playerController ?: return
        val entities = entityManager ?: return
        val snapshots = entities.getTalkableSnapshots(controller)
        val activeId = _state.value.activeNpc?.id
        val chosen = snapshots.firstOrNull { it.id == activeId } ?: snapshots.firstOrNull()
        val history = chosen?.let { npcHistories[it.id]?.toList() } ?: emptyList()
        _state.update {
            it.copy(
                talkableNpcs = snapshots,
                activeNpc = chosen,
                npcChatHistory = history,
                npcAiError = null
            )
        }
    }

    fun selectActiveNpc(npc: TalkableNpcSnapshot) {
        voiceAgent?.stopSpeaking()
        val history = npcHistories[npc.id]?.toList() ?: emptyList()
        _state.update {
            it.copy(
                activeNpc = npc,
                npcChatHistory = history,
                npcAiError = null
            )
        }
    }

    fun befriendActiveNpc() {
        val npc = _state.value.activeNpc ?: return
        val entities = entityManager ?: return
        val updated = entities.boostFriendship(npc.id, delta = 25) ?: return
        soundEngine.playHeartChime()
        val greeting = "नमस्ते मित्र! अब हम पक्के दोस्त बन गए हैं। आप मुझसे कुछ भी कह सकते हैं!"
        voiceAgent?.speakHindi(greeting, updated.kind)
        val list = npcHistories.getOrPut(updated.id) { mutableListOf() }
        list.add(NpcChatMessage(updated.displayName, greeting, isPlayer = false))
        refreshTalkableNpcs()
        _state.update {
            it.copy(
                npcChatHistory = list.toList(),
                statusToast = "${updated.displayName} अब आपके पक्के दोस्त हैं! (दोस्ती: ${updated.friendshipLevel}%)"
            )
        }
    }

    fun commandNpcWorldAction(goal: AiGoal, targetOverride: TalkableNpcSnapshot? = null) {
        val npc = targetOverride ?: _state.value.activeNpc ?: _state.value.nearbyEntity ?: return
        val world = chunkWorld ?: return
        val controller = playerController ?: return
        val entities = entityManager ?: return
        val resultMsg = entities.applyNpcDecision(npc.id, goal, world, controller)
        entities.triggerEntityTalking(npc.id, durationSec = 4.6f)
        when (goal) {
            AiGoal.DO_PUJA, AiGoal.BUILD_MANDIR -> soundEngine.playTempleBell()
            AiGoal.BRING_WATER -> soundEngine.playWaterSplash()
            AiGoal.PLANT_TREES -> soundEngine.playHeartChime()
            else -> soundEngine.playBlockPlace()
        }
        if (resultMsg != null) {
            voiceAgent?.speakHindi(resultMsg, npc.kind)
            val list = npcHistories.getOrPut(npc.id) { mutableListOf() }
            list.add(NpcChatMessage(npc.displayName, resultMsg, isPlayer = false, decidedActionHindi = goal.labelHindi))
            _state.update {
                it.copy(
                    npcChatHistory = list.toList(),
                    liveSubtitleText = "${npc.displayName}: $resultMsg",
                    statusToast = resultMsg
                )
            }
        }
        refreshTalkableNpcs()
    }

    fun sendHindiMessageToNpc(rawText: String, targetOverride: TalkableNpcSnapshot? = null) {
        val text = rawText.trim()
        if (text.isBlank()) return
        val npc = targetOverride ?: _state.value.activeNpc ?: _state.value.nearbyEntity ?: return
        val agent = voiceAgent ?: run {
            _state.update { it.copy(npcAiError = "AI Voice Agent तैयार नहीं है।") }
            return
        }

        val list = npcHistories.getOrPut(npc.id) { mutableListOf() }
        list.add(NpcChatMessage("आप", text, isPlayer = true))
        _state.update {
            it.copy(
                activeNpc = npc,
                npcChatHistory = list.toList(),
                npcAiThinking = true,
                npcAiError = null
            )
        }

        viewModelScope.launch {
            val result = agent.chatWithNpc(
                kind = npc.kind,
                currentGoal = npc.goalLabelHindi,
                friendshipLevel = npc.friendshipLevel,
                history = list,
                playerMessage = text
            )
            result.fold(
                onSuccess = { aiReply ->
                    // Execute any autonomous world action decided by the AI character!
                    val world = chunkWorld
                    val controller = playerController
                    val entities = entityManager
                    var actionLabel: String? = null
                    if (aiReply.decidedGoal != null && world != null && controller != null && entities != null) {
                        entities.applyNpcDecision(npc.id, aiReply.decidedGoal, world, controller)
                        actionLabel = aiReply.decidedGoal.labelHindi
                        when (aiReply.decidedGoal) {
                            AiGoal.DO_PUJA, AiGoal.BUILD_MANDIR -> soundEngine.playTempleBell()
                            AiGoal.BRING_WATER -> soundEngine.playWaterSplash()
                            AiGoal.PLANT_TREES -> soundEngine.playHeartChime()
                            else -> soundEngine.playBlockPlace()
                        }
                    }
                    entities?.boostFriendship(npc.id, delta = aiReply.friendshipGain)
                    entities?.triggerEntityTalking(npc.id, durationSec = 5.0f)
                    if (aiReply.giftCoins > 0) {
                        soundEngine.playCoinChime()
                    }

                    list.add(
                        NpcChatMessage(
                            senderName = npc.displayName,
                            textHindi = aiReply.replyHindi,
                            isPlayer = false,
                            decidedActionHindi = actionLabel
                        )
                    )
                    // Speak aloud in real human-sounding Hindi voice with 3D lip-sync!
                    agent.speakHindi(aiReply.replyHindi, npc.kind)
                    _state.update { current ->
                        current.copy(
                            npcAiThinking = false,
                            npcChatHistory = list.toList(),
                            liveSubtitleText = "${npc.displayName}: ${aiReply.replyHindi}",
                            goldCoins = current.goldCoins + aiReply.giftCoins,
                            statusToast = if (aiReply.giftCoins > 0) {
                                "${npc.displayName} ने आपको +${aiReply.giftCoins} सोने के सिक्के दिए!"
                            } else actionLabel
                        )
                    }
                    refreshTalkableNpcs()
                },
                onFailure = { err ->
                    _state.update {
                        it.copy(
                            npcAiThinking = false,
                            npcAiError = err.localizedMessage ?: "AI से बात करने में समस्या आई।"
                        )
                    }
                }
            )
        }
    }

    fun startHindiVoiceMic(targetOverride: TalkableNpcSnapshot? = null) {
        val agent = voiceAgent ?: return
        val chosenTarget = targetOverride ?: _state.value.activeNearbyEntity ?: _state.value.activeNpc
        _state.update { it.copy(npcAiError = null) }
        agent.startHindiVoiceListening(
            onResult = { spokenHindi ->
                val lower = spokenHindi.lowercase()
                val isHelloPhrase = lower.contains("hello") || lower.contains("हेलो") ||
                    lower.contains("हैलो") || lower.contains("नमस्ते") || lower.contains("नमस्कार") || lower.contains("hi")
                if (chosenTarget != null && isHelloPhrase && _state.value.helloActiveEntityId != chosenTarget.id) {
                    sayHelloToNearbyEntity(targetOverride = chosenTarget)
                } else {
                    if (chosenTarget != null) {
                        _state.update { it.copy(helloActiveEntityId = chosenTarget.id) }
                    }
                    sendHindiMessageToNpc(spokenHindi, targetOverride = chosenTarget)
                }
            },
            onError = { errMsg ->
                _state.update { it.copy(npcAiError = errMsg, statusToast = errMsg) }
            }
        )
    }

    fun stopHindiVoiceMic() {
        voiceAgent?.stopListening()
    }

    fun stopHindiVoicePlayback() {
        voiceAgent?.stopSpeaking()
    }

    // --- ADMIN LIVE STUDIO, REAL-TIME MAP EDITOR & 3D MODEL SHOP ---

    fun adminClaimCredentials(email: String, password: String): Result<Unit> {
        val store = adminStore ?: return Result.failure(IllegalStateException("Admin Store unavailable"))
        val res = store.claimAdminCredentials(email, password)
        if (res.isSuccess) {
            soundEngine.playAdminLiveChime()
            _state.update { it.copy(statusToast = "Admin Account सेट व अनलॉक हो गया!") }
        }
        return res
    }

    fun adminLogin(email: String, password: String): Result<Unit> {
        val store = adminStore ?: return Result.failure(IllegalStateException("Admin Store unavailable"))
        val res = store.loginAdmin(email, password)
        if (res.isSuccess) {
            soundEngine.playAdminLiveChime()
            _state.update { it.copy(statusToast = "Admin Panel Unlocked!") }
        }
        return res
    }

    fun adminUpdatePassword(currentPass: String, newPass: String): Result<Unit> {
        val store = adminStore ?: return Result.failure(IllegalStateException("Admin Store unavailable"))
        val res = store.updateAdminPassword(currentPass, newPass)
        if (res.isSuccess) {
            soundEngine.playAdminLiveChime()
            _state.update { it.copy(statusToast = "Admin Password सफलतापूर्वक बदल गया!") }
        }
        return res
    }

    fun adminLockSession() {
        adminStore?.lockAdminSession()
        _state.update { it.copy(statusToast = "Admin Panel Locked") }
    }

    fun adminImportObjUri(uri: Uri): Result<Pair<String, Int>> {
        val store = adminStore ?: return Result.failure(IllegalStateException("Admin Store unavailable"))
        return store.importObjFileFromUri(uri)
    }

    fun adminSaveCustomModel(model: AdminCustom3DModel, spawnImmediately: Boolean = false) {
        val store = adminStore ?: return
        store.saveCustomModel(model)
        soundEngine.playAdminLiveChime()
        if (spawnImmediately) {
            adminSpawnCustomModel(model)
        } else {
            val suffix = if (model.role == CustomModelRole.SHOP_CHARACTER && model.listedInShop) {
                "Shop में Live लिस्ट हो गया!"
            } else {
                "सेव हो गया!"
            }
            _state.update { it.copy(statusToast = "3D मॉडल '${model.name}' $suffix") }
        }
    }

    fun adminDeleteCustomModel(modelId: String) {
        adminStore?.deleteCustomModel(modelId)
        _state.update { it.copy(statusToast = "3D मॉडल हटा दिया गया।") }
    }

    fun adminSpawnCustomModel(model: AdminCustom3DModel) {
        val world = chunkWorld ?: return
        val controller = playerController ?: return
        val entities = entityManager ?: return
        val parsed = adminStore?.loadParsedMesh(model.uploadedObjFileName)
        val spawned = entities.spawnCustomModelInFront(world, controller, model, parsed, distance = 4.6f)
        if (model.role == CustomModelRole.ENEMY_DUSHMAN) {
            soundEngine.playZombieGroan()
            _state.update { it.copy(statusToast = "दुश्मन 3D बॉस '${model.name}' Live Spawn हुआ! (HP: ${model.maxHealth})") }
        } else {
            soundEngine.playAdminLiveChime()
            entities.triggerEntityTalking(spawned.id, durationSec = 4.2f)
            voiceAgent?.speakHindi(model.hindiGreeting, spawned.kind, customPitch = model.voicePitch)
            _state.update {
                it.copy(
                    liveSubtitleText = "${model.name}: ${model.hindiGreeting}",
                    statusToast = "3D कैरेक्टर '${model.name}' Live Spawn हुआ!"
                )
            }
        }
        refreshTalkableNpcs()
    }

    fun purchaseAndEquipCustom3DModel(model: AdminCustom3DModel, spawnAsCompanion: Boolean = false) {
        val current = _state.value
        val ownKey = "custom_3d_${model.id}"
        val alreadyOwned = current.ownedProductIds.contains(ownKey) || current.gameMode == GameMode.CREATIVE
        if (!alreadyOwned && current.goldCoins < model.priceCoins) {
            _state.update {
                it.copy(statusToast = "पर्याप्त सिक्के नहीं! चाहिए: ${model.priceCoins}, आपके पास: ${current.goldCoins}")
            }
            return
        }

        val remaining = if (alreadyOwned) current.goldCoins else (current.goldCoins - model.priceCoins).coerceAtLeast(0)
        val nextOwned = current.ownedProductIds + ownKey
        soundEngine.playCoinChime()

        if (spawnAsCompanion) {
            adminSpawnCustomModel(model)
            _state.update {
                it.copy(
                    goldCoins = remaining,
                    ownedProductIds = nextOwned,
                    statusToast = "3D साथी '${model.name}' आपके सामने आ गया!"
                )
            }
        } else {
            adminStore?.equipPublicCharacterModel(model.id)
            glRenderer?.cameraMode = CameraMode.THIRD_PERSON_BACK
            _state.update {
                it.copy(
                    goldCoins = remaining,
                    ownedProductIds = nextOwned,
                    cameraMode = CameraMode.THIRD_PERSON_BACK,
                    thirdPersonCamera = true,
                    statusToast = "3D मॉडल '${model.name}' आपका Avatar बन गया! (3rd-Person View ON)"
                )
            }
        }
    }

    fun unequipCustom3DAvatar() {
        adminStore?.equipPublicCharacterModel(null)
        _state.update { it.copy(statusToast = "Default Steve Avatar वापस लग गया।") }
    }

    private fun frontTargetCoords(distance: Float = 6.5f): Pair<Int, Int>? {
        val c = playerController ?: return null
        val (fx, _, fz) = c.forwardVector()
        val bx = floor(c.x + fx * distance).toInt()
        val bz = floor(c.z + fz * distance).toInt()
        return bx to bz
    }

    fun adminPlantTree() {
        val world = chunkWorld ?: return
        val (bx, bz) = frontTargetCoords(5.5f) ?: return
        world.plantTreeAt(bx, bz, tall = true)
        soundEngine.playHeartChime()
        _state.update { it.copy(syncStatus = CloudSyncStatus.UNSAVED, statusToast = "Admin Live: विशाल हरा पेड़ लगा दिया गया!") }
    }

    fun adminPlantForest() {
        val world = chunkWorld ?: return
        val (bx, bz) = frontTargetCoords(8.0f) ?: return
        world.plantForestAround(bx, bz, count = 14)
        soundEngine.playHeartChime()
        _state.update { it.copy(syncStatus = CloudSyncStatus.UNSAVED, statusToast = "Admin Live: 14 पेड़ों का घना जंगल तैयार!") }
    }

    fun adminCreateWaterLake(waterfall: Boolean) {
        val world = chunkWorld ?: return
        val (bx, bz) = frontTargetCoords(7.0f) ?: return
        world.createWaterSpringOrRiver(bx, bz, radius = 4, waterfall = waterfall)
        soundEngine.playWaterSplash()
        entityManager?.spawnWaterSplashParticles(bx + 0.5f, world.findSpawnY(bx, bz), bz + 0.5f)
        _state.update {
            it.copy(
                syncStatus = CloudSyncStatus.UNSAVED,
                statusToast = if (waterfall) "Admin Live: बहता झरना (Waterfall) + पानी साउंड Live!" else "Admin Live: असली बहती नदी/झील तैयार!"
            )
        }
    }

    fun adminBuildStructure(type: String, labelHindi: String) {
        val world = chunkWorld ?: return
        val (bx, bz) = frontTargetCoords(9.0f) ?: return
        world.buildAdminLiveStructure(bx, bz, type)
        soundEngine.playAdminLiveChime()
        _state.update {
            it.copy(
                syncStatus = CloudSyncStatus.UNSAVED,
                statusToast = "Admin Live: $labelHindi रियल-टाइम में बन गया!"
            )
        }
    }

    fun adminPublishLiveMap(title: String, announcement: String) {
        val store = adminStore ?: return
        val current = _state.value
        val published = store.publishLiveMap(
            title = title.ifBlank { current.worldName },
            biome = current.biome,
            announcementBanner = announcement
        )
        soundEngine.playAdminLiveChime()
        saveWorld()
        _state.update {
            it.copy(
                statusToast = "LIVE PUBLISHED: '${published.title}' (Edit #${published.liveEditsCount})!"
            )
        }
    }

    // --- ROYAL BAZAAR SHOP SYSTEM ---

    fun purchaseShopProduct(product: ShopProduct) {
        val current = _state.value
        if (product.oneTimeUnlock && current.ownedProductIds.contains(product.id)) {
            _state.update { it.copy(statusToast = "${product.title} पहले से अनलॉक है!") }
            return
        }
        if (current.goldCoins < product.priceCoins && current.gameMode != GameMode.CREATIVE) {
            _state.update { it.copy(statusToast = "पर्याप्त सिक्के नहीं! (चाहिए: ${product.priceCoins}, आपके पास: ${current.goldCoins})") }
            return
        }

        val world = chunkWorld
        val controller = playerController
        val entities = entityManager
        val updatedInv = current.inventory.toMutableMap()
        var nextPickaxe = current.pickaxeTier
        var nextJetBoots = current.jetBootsUnlocked

        when (product.rewardType) {
            ShopRewardType.ROYAL_CROWN -> {
                entities?.playerWearsRoyalCrown = true
            }
            ShopRewardType.DIAMOND_PICKAXE -> {
                nextPickaxe = 3
            }
            ShopRewardType.SUPER_BLASTER -> {
                entities?.blasterDamage = 60
            }
            ShopRewardType.JET_FLY_BOOTS -> {
                controller?.jetBootsUnlocked = true
                nextJetBoots = true
            }
            ShopRewardType.SPEED_SANDALS -> {
                controller?.speedBoostMultiplier = 1.35f
            }
            ShopRewardType.INSTANT_MANDIR_KIT -> {
                if (world != null && controller != null) {
                    val (fx, _, fz) = controller.forwardVector()
                    val bx = floor(controller.x + fx * 6f).toInt()
                    val bz = floor(controller.z + fz * 6f).toInt()
                    world.buildMiniStructure(bx, bz, isMandir = true)
                    soundEngine.playTempleBell()
                }
            }
            ShopRewardType.INSTANT_HOUSE_KIT -> {
                if (world != null && controller != null) {
                    val (fx, _, fz) = controller.forwardVector()
                    val bx = floor(controller.x + fx * 6f).toInt()
                    val bz = floor(controller.z + fz * 6f).toInt()
                    world.buildMiniStructure(bx, bz, isMandir = false)
                }
            }
            ShopRewardType.LAVA_WATER_PACK -> {
                updatedInv[BlockType.LAVA] = ((updatedInv[BlockType.LAVA] ?: 0) + 32).coerceAtMost(999)
                updatedInv[BlockType.WATER] = ((updatedInv[BlockType.WATER] ?: 0) + 64).coerceAtMost(999)
                updatedInv[BlockType.OBSIDIAN] = ((updatedInv[BlockType.OBSIDIAN] ?: 0) + 24).coerceAtMost(999)
            }
            ShopRewardType.DIAMOND_GOLD_VAULT -> {
                updatedInv[BlockType.GOLD_ORE] = ((updatedInv[BlockType.GOLD_ORE] ?: 0) + 24).coerceAtMost(999)
                updatedInv[BlockType.DIAMOND_ORE] = ((updatedInv[BlockType.DIAMOND_ORE] ?: 0) + 16).coerceAtMost(999)
                updatedInv[BlockType.BRICK] = ((updatedInv[BlockType.BRICK] ?: 0) + 32).coerceAtMost(999)
                updatedInv[BlockType.GLASS] = ((updatedInv[BlockType.GLASS] ?: 0) + 24).coerceAtMost(999)
            }
            ShopRewardType.ROYAL_ELEPHANT -> {
                if (world != null && controller != null && entities != null) {
                    val e = entities.spawnEntityInFront(world, controller, EntityKind.ELEPHANT, 5f)
                    e.connected = true
                }
            }
            ShopRewardType.ROYAL_HORSE -> {
                if (world != null && controller != null && entities != null) {
                    val e = entities.spawnEntityInFront(world, controller, EntityKind.HORSE, 4.5f)
                    e.connected = true
                }
            }
            ShopRewardType.ROYAL_GUARD_RAJA -> {
                if (world != null && controller != null && entities != null) {
                    entities.spawnEntityInFront(world, controller, EntityKind.RAJA, 4.5f)
                    entities.spawnEntityInFront(world, controller, EntityKind.PUJARI, 5.5f)
                    entities.spawnEntityInFront(world, controller, EntityKind.MAJDUR, 6.2f)
                }
            }
        }

        soundEngine.playCoinChime()
        val remainingCoins = if (current.gameMode == GameMode.CREATIVE) {
            current.goldCoins
        } else {
            (current.goldCoins - product.priceCoins).coerceAtLeast(0)
        }
        val nextOwned = if (product.oneTimeUnlock) current.ownedProductIds + product.id else current.ownedProductIds

        _state.update {
            it.copy(
                goldCoins = remainingCoins,
                ownedProductIds = nextOwned,
                inventory = updatedInv,
                pickaxeTier = nextPickaxe,
                jetBootsUnlocked = nextJetBoots,
                syncStatus = CloudSyncStatus.UNSAVED,
                statusToast = "खरीद सफल: ${product.title}!"
            )
        }
        refreshTalkableNpcs()
    }

    fun setPaused(isPaused: Boolean) {
        glRenderer?.paused = isPaused
        if (isPaused) {
            playerController?.let { c ->
                c.moveInputX = 0f
                c.moveInputY = 0f
                c.jumpHeld = false
                c.jumpRequested = false
                c.flyUpHeld = false
                c.flyDownHeld = false
                c.autoSprinting = false
            }
        }
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
        val nextMode = _state.value.cameraMode.next()
        glRenderer?.cameraMode = nextMode
        playerController?.resetCameraLevel()
        _state.update {
            it.copy(
                cameraMode = nextMode,
                thirdPersonCamera = nextMode != CameraMode.FIRST_PERSON,
                statusToast = "Camera: ${nextMode.displayName}"
            )
        }
    }

    fun resetCameraAngle() {
        playerController?.resetCameraLevel()
        _state.update { it.copy(statusToast = "Camera Angle Leveled!") }
    }

    fun buyRealEstateProperty(property: RealEstateProperty) {
        val current = _state.value
        if (property.id in current.ownedRealEstateIds) {
            _state.update { it.copy(statusToast = "${property.title} already owned!") }
            return
        }
        if (current.goldCoins < property.priceCoins) {
            _state.update { it.copy(statusToast = "Need ${property.priceCoins} Coins to buy ${property.title}") }
            return
        }
        soundEngine.playCoinChime()
        lastRentAccrualMs = System.currentTimeMillis()
        _state.update {
            it.copy(
                goldCoins = it.goldCoins - property.priceCoins,
                ownedRealEstateIds = it.ownedRealEstateIds + property.id,
                pendingRentCoins = it.pendingRentCoins + property.rentPerCycle,
                statusToast = "🎉 खरीदा गया: ${property.title}! (+${property.rentPerCycle} 🪙 किराया चालू)"
            )
        }
    }

    fun collectRealEstateRent() {
        val rent = _state.value.pendingRentCoins
        if (rent <= 0) return
        soundEngine.playCoinChime()
        _state.update {
            it.copy(
                goldCoins = it.goldCoins + rent,
                pendingRentCoins = 0,
                statusToast = "Collected +$rent Gold Coins Real Estate Rent!"
            )
        }
    }

    fun constructRealEstateHere(structureType: String, label: String) {
        val world = chunkWorld ?: return
        val controller = playerController ?: return
        val (fx, _, fz) = controller.forwardVector()
        val dist = if (structureType == "BOULEVARD_ROAD") 6f else 12f
        val tx = floor(controller.x + fx * dist).toInt()
        val tz = floor(controller.z + fz * dist).toInt()
        world.buildRealEstateStructure(tx, tz, structureType)
        soundEngine.playBlockPlace()
        _state.update {
            it.copy(
                syncStatus = CloudSyncStatus.UNSAVED,
                statusToast = "🏗️ $label built right ahead!"
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
            lower == "/coins" -> {
                soundEngine.playCoinChime()
                _state.update { it.copy(goldCoins = it.goldCoins + 250) }
                reply = "+250 Gold Coins added to Royal Purse!"
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
        val canFly = _state.value.gameMode == GameMode.CREATIVE || _state.value.jetBootsUnlocked
        if (held && !controller.jumpHeld) {
            val now = System.currentTimeMillis()
            if (canFly && (now - lastJumpTapMs) < 320L) {
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
        refreshTalkableNpcs()
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
        controller.yaw = landmark.facingYaw
        controller.resetCameraLevel()
        if (landmark == WorldLandmark.MANDIR) {
            soundEngine.playTempleBell()
        } else {
            soundEngine.playAnimalCall("HERO")
        }
        _state.update {
            it.copy(
                playerX = landmark.dx,
                playerY = floor(destY).toInt(),
                playerZ = landmark.dz,
                playerYaw = landmark.facingYaw,
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
        if (current.gameMode != GameMode.CREATIVE && !current.jetBootsUnlocked) {
            _state.update { it.copy(statusToast = "Switch to Creative Mode or buy Sky Jet Boots in Shop!") }
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
        if (!isCreative && !current.jetBootsUnlocked) {
            playerController?.flying = false
        }
        _state.update {
            it.copy(
                gameMode = nextMode,
                flying = if (isCreative || it.jetBootsUnlocked) it.flying else false,
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
            _state.update { it.copy(statusToast = "Craft or buy $toolName to mine ${target.displayName}") }
            return false
        }

        world.setBlock(hit.x, hit.y, hit.z, BlockType.AIR, recordUndo = true)
        entityManager?.spawnBlockBreakParticles(
            hit.x, hit.y, hit.z,
            target.topColor.red, target.topColor.green, target.topColor.blue
        )
        soundEngine.playBlockBreak()
        unsyncedMinedDelta += 1L

        val coinReward = when (target) {
            BlockType.DIAMOND_ORE -> 25
            BlockType.GOLD_ORE -> 15
            BlockType.IRON_ORE, BlockType.COAL_ORE -> 6
            else -> 2
        }

        val updatedInv = current.inventory.toMutableMap()
        if (current.gameMode == GameMode.SURVIVAL) {
            val drop = target.survivalDrop()
            updatedInv[drop] = ((updatedInv[drop] ?: 0) + 1).coerceAtMost(999)
        }
        _state.update {
            it.copy(
                inventory = updatedInv,
                blocksMined = it.blocksMined + 1L,
                goldCoins = it.goldCoins + coinReward,
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
        if (blockToPlace != BlockType.WATER && blockToPlace != BlockType.WATERFALL && blockToPlace != BlockType.LAVA && !blockToPlace.crossPlant) {
            if (controller.overlapsBlock(px, py, pz)) {
                _state.update { it.copy(statusToast = "Step back to place block here") }
                return false
            }
        }

        val updatedInv = current.inventory.toMutableMap()
        val isUnlimitedWater = (blockToPlace == BlockType.WATER || blockToPlace == BlockType.WATERFALL)
        if (current.gameMode == GameMode.SURVIVAL && !isUnlimitedWater) {
            val count = updatedInv[blockToPlace] ?: 0
            if (count <= 0) {
                _state.update { it.copy(statusToast = "Out of ${blockToPlace.displayName}! Mine, Craft or buy in Shop.") }
                return false
            }
            updatedInv[blockToPlace] = count - 1
        }

        world.setBlock(px, py, pz, blockToPlace, recordUndo = true)
        glRenderer?.triggerArmSwing()
        when {
            isUnlimitedWater -> {
                soundEngine.playWaterSplash()
                entityManager?.spawnWaterSplashParticles(px + 0.5f, py + 0.5f, pz + 0.5f)
            }
            blockToPlace == BlockType.LAVA -> {
                soundEngine.playLavaBubble()
                entityManager?.spawnLavaEmberParticles(px + 0.5f, py + 0.5f, pz + 0.5f)
            }
            else -> soundEngine.playBlockPlace()
        }
        unsyncedPlacedDelta += 1L

        _state.update {
            it.copy(
                gunEquipped = false,
                handSwingTick = it.handSwingTick + 1,
                inventory = updatedInv,
                blocksPlaced = it.blocksPlaced + 1L,
                goldCoins = it.goldCoins + 1,
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
                goldCoins = it.goldCoins + 10,
                syncStatus = CloudSyncStatus.UNSAVED,
                statusToast = "Crafted ${recipe.title}! (+10 Coins)"
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
