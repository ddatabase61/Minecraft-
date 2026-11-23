package com.example

import com.example.data.model.BiomeType
import com.example.data.model.BlockType
import com.example.engine.gl.VoxelChunkWorld
import com.example.engine.gl.VoxelPlayerController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoxelChunkWorldTest {

    @Test
    fun deterministicTerrainAndDeltasRoundTrip() {
        val worldA = VoxelChunkWorld(BiomeType.EMERALD_VALLEY, seed = 424242L)
        val spawnY = worldA.findSpawnY(8, 8).toInt()
        assertTrue("Spawn height should be above bedrock", spawnY in 3..44)

        // Place a diamond block above spawn and mine the guaranteed solid block right beneath spawn
        worldA.setBlock(8, spawnY + 1, 8, BlockType.DIAMOND_ORE, recordUndo = true)
        worldA.setBlock(8, spawnY - 1, 8, BlockType.AIR, recordUndo = true)

        val deltas = worldA.exportDeltas()
        assertEquals(BlockType.DIAMOND_ORE.id.toLong(), deltas["8,${spawnY + 1},8"])
        assertEquals(BlockType.AIR.id.toLong(), deltas["8,${spawnY - 1},8"])

        // Reconstruct a brand-new world from seed + deltas and verify exact match
        val worldB = VoxelChunkWorld(BiomeType.EMERALD_VALLEY, seed = 424242L, initialDeltas = deltas)
        assertEquals(BlockType.DIAMOND_ORE, worldB.getBlock(8, spawnY + 1, 8))
        assertEquals(BlockType.AIR, worldB.getBlock(8, spawnY - 1, 8))

        // Verify undo reverts the last edit
        assertNotNull(worldA.undo())
        assertTrue(worldA.getBlock(8, spawnY - 1, 8) != BlockType.AIR)
    }

    @Test
    fun chunkFaceCullingBuildsNonEmptyMeshes() {
        val world = VoxelChunkWorld(BiomeType.EMERALD_VALLEY, seed = 1337L)
        val meshes = world.prepareVisibleMeshes(playerX = 8f, playerZ = 8f, renderDistance = 1, maxRebuildsPerFrame = 9)
        assertTrue("Should generate chunk meshes around player", meshes.isNotEmpty())
        val totalOpaqueVerts = meshes.sumOf { it.opaqueVertexCount }
        assertTrue("Face-culled chunks should emit visible vertices", totalOpaqueVerts > 100)
    }

    @Test
    fun playerPhysicsLandsOnGroundAndRaycastHitsBlock() {
        val world = VoxelChunkWorld(BiomeType.DESERT_DUNES, seed = 98765L)
        val spawnY = world.findSpawnY(8, 8)
        val player = VoxelPlayerController(8.5f, spawnY + 1.5f, 8.5f)

        // Step gravity until player lands on terrain
        repeat(80) {
            player.stepPhysics(world, dtRaw = 0.02f, creativeMode = false)
        }
        assertTrue("Player should land on solid ground", player.onGround)

        // Look straight down and raycast the supporting block
        player.pitch = -1.45f
        val hit = player.raycastTarget(world, maxDistance = 5.0f)
        assertNotNull("Looking straight down should hit ground block", hit)
        assertEquals(1, hit?.ny)
    }

    @Test
    fun entityEcosystemAiTrackingAndGunShootingWork() {
        val world = VoxelChunkWorld(BiomeType.EMERALD_VALLEY, seed = 7777L)
        val spawnY = world.findSpawnY(8, 8)
        val player = VoxelPlayerController(8.5f, spawnY, 8.5f)
        val manager = com.example.engine.gl.VoxelEntityManager(7777L)
        manager.populateInitialEcosystem(world, player.x, player.z)

        val (totalEntities, activeZombies) = manager.activeCounts()
        assertTrue("Initial ecosystem should include heroes, society, animals, birds, and zombies", totalEntities >= 22)
        assertTrue("Initial ecosystem should include AI-tracking zombies", activeZombies >= 3)

        // Clear a flat shooting corridor directly in front of player and spawn an AI-tracking Zombie
        player.yaw = 0f
        player.pitch = 0f
        val baseY = player.y.toInt()
        for (dz in 1..5) {
            for (dy in 0..3) {
                world.setBlock(8, baseY + dy, 8 - dz, BlockType.AIR, recordUndo = false)
            }
            world.setBlock(8, baseY - 1, 8 - dz, BlockType.GRASS, recordUndo = false)
        }
        val targetZombie = manager.spawnEntityInFront(world, player, com.example.engine.gl.EntityKind.ZOMBIE, distance = 3.0f)
        assertEquals(com.example.engine.gl.EntityKind.ZOMBIE, targetZombie.kind)

        // Fire 3 shots straight ahead and step simulation so bullets connect
        repeat(3) {
            manager.fireGun(player)
            repeat(12) {
                manager.stepSimulation(world, player, dt = 0.02f, creativeMode = true, isNight = false)
            }
        }
        assertTrue("Firing gun straight at spawned zombie should defeat it", manager.zombiesDefeated >= 1)

        // Verify minimap terrain sampling and 3D entity render buffer
        val mapColors = world.sampleMinimapColors(8, 8, gridSize = 15, step = 2)
        assertEquals(15 * 15, mapColors.size)
        val (_, vertCount) = manager.buildRenderBuffer()
        assertTrue("Living 3D entities should generate non-empty OpenGL vertices", vertCount > 500)
    }

    @Test
    fun infiniteWaterUnderwaterSwimmingAndConnectorAnimalsWork() {
        val world = VoxelChunkWorld(BiomeType.EMERALD_VALLEY, seed = 2026L)
        val baseY = 28

        // Build a 3x3 stone basin at y=baseY-1 and air inside at y=baseY..baseY+2
        for (x in 2..6) {
            for (z in 2..6) {
                world.setBlock(x, baseY - 1, z, BlockType.STONE, recordUndo = false)
                for (dy in 0..3) {
                    world.setBlock(x, baseY + dy, z, BlockType.AIR, recordUndo = false)
                }
            }
        }

        // Place two opposite corners of a 2x2 pool -> Infinite Water Source automatically refills neighbors
        world.setBlock(3, baseY, 3, BlockType.WATER, recordUndo = false)
        world.setBlock(4, baseY, 4, BlockType.WATER, recordUndo = false)
        assertEquals(
            "Adjacent cell between two water blocks should auto-fill as Infinite Water Source",
            BlockType.WATER,
            world.getBlock(3, baseY, 4)
        )

        // Fill a 3-block deep water column and submerge the player to verify underwater & oxygen physics
        for (dy in 0..2) {
            world.setBlock(4, baseY + dy, 4, BlockType.WATER, recordUndo = false)
        }
        val player = VoxelPlayerController(4.5f, baseY.toFloat(), 4.5f)
        repeat(80) {
            player.stepPhysics(world, dtRaw = 0.05f, creativeMode = false)
        }
        assertTrue("Player inside deep water should detect inWater", player.inWater)
        assertTrue("Player submerged under water should detect isUnderwater", player.isUnderwater)
        assertTrue("Submerged player oxygen should deplete below 10", player.oxygen < 10)

        // Verify Connector Animals: spawn a Cow & Horse nearby, connect them, and confirm connected count
        val manager = com.example.engine.gl.VoxelEntityManager(2026L)
        manager.spawnEntityInFront(world, player, com.example.engine.gl.EntityKind.COW, distance = 3.0f)
        manager.spawnEntityInFront(world, player, com.example.engine.gl.EntityKind.HORSE, distance = 3.5f)
        val newlyConnected = manager.connectNearbyAnimals(player, radius = 12f)
        assertTrue("Connecting nearby animals should bond at least 2 animals", newlyConnected >= 2)
        assertTrue("Connected animal count should reflect bonded herd", manager.connectedAnimalCount() >= 2)
    }

    @Test
    fun bedrockAutoSprintPitchClampAndSneakWork() {
        val world = VoxelChunkWorld(BiomeType.EMERALD_VALLEY, seed = 9090L)
        val spawnY = world.findSpawnY(8, 8)
        val player = VoxelPlayerController(8.5f, spawnY, 8.5f)

        // Pitch clamp between -89° and +89°
        player.rotateLook(deltaYawRad = 0.5f, deltaPitchRad = 10.0f)
        assertEquals(VoxelPlayerController.MAX_PITCH_RAD, player.pitch, 0.0001f)
        player.rotateLook(deltaYawRad = 0f, deltaPitchRad = -20.0f)
        assertEquals(-VoxelPlayerController.MAX_PITCH_RAD, player.pitch, 0.0001f)

        // Pushing joystick >85% forward activates auto-sprint
        player.moveInputY = 0.92f
        player.stepPhysics(world, dtRaw = 0.02f, creativeMode = false)
        assertTrue("Joystick >85% forward should activate autoSprinting", player.autoSprinting)
        assertTrue("isEffectivelySprinting should be true during auto-sprint", player.isEffectivelySprinting)

        // Sneaking disables auto-sprint and smoothly lowers camera eyeY
        val standingEye = player.eyeY
        player.sneaking = true
        repeat(20) {
            player.stepPhysics(world, dtRaw = 0.02f, creativeMode = false)
        }
        assertTrue("Sneaking should disable autoSprinting", !player.autoSprinting)
        assertTrue("Sneaking should lower camera eye height", player.eyeY < standingEye - 0.15f)
    }

    @Test
    fun zeroDirtLegSinkingAndSmoothAutoStepWork() {
        val world = VoxelChunkWorld(BiomeType.EMERALD_VALLEY, seed = 5151L)
        val floorY = 26
        // Create a flat dirt runway with a 1-block dirt step ahead along -Z
        for (x in 4..12) {
            for (z in 2..12) {
                world.setBlock(x, floorY - 1, z, BlockType.DIRT, recordUndo = false)
                for (dy in 0..4) {
                    world.setBlock(x, floorY + dy, z, BlockType.AIR, recordUndo = false)
                }
            }
        }
        for (x in 4..12) {
            for (z in 2..5) {
                world.setBlock(x, floorY, z, BlockType.DIRT, recordUndo = false)
            }
        }

        // Drop player from slightly above dirt and confirm feet sit strictly above floorY (never sunk into dirt)
        val player = VoxelPlayerController(8.5f, floorY + 2.2f, 8.5f)
        repeat(60) {
            player.stepPhysics(world, dtRaw = 0.02f, creativeMode = false)
        }
        assertTrue("Player should land cleanly on top of dirt", player.onGround)
        assertTrue("Player feet must never sink below dirt top", player.y >= floorY.toFloat())
        assertTrue("Player AABB must not be stuck inside dirt", !player.collides(world, player.x, player.y, player.z))

        // Walk forward (-Z) up the 1-block dirt ledge using auto-step
        player.yaw = 0f
        player.moveInputY = 0.9f
        repeat(35) {
            player.stepPhysics(world, dtRaw = 0.02f, creativeMode = false)
        }
        assertTrue("Player should smoothly auto-step onto the +1 dirt ledge", player.y >= floorY + 0.95f)
        assertTrue("Player should move well forward past the ledge", player.z < 5.5f)

        // Reverse backward (+Z) and verify player moves freely back without getting stuck
        val zBeforeBack = player.z
        player.moveInputY = -0.9f
        repeat(45) {
            player.stepPhysics(world, dtRaw = 0.02f, creativeMode = false)
        }
        assertTrue("Player should move smoothly backward", player.z > zBeforeBack + 1.5f)
        assertTrue("Player remains collision-free after forward/backward movement", !player.collides(world, player.x, player.y, player.z))
    }

    @Test
    fun waterChannelFlowAndLavaQuenchToObsidianWork() {
        val world = VoxelChunkWorld(BiomeType.EMERALD_VALLEY, seed = 8282L)
        val y = 30
        // Build a stone trough with a dug channel to test real water flow
        for (x in 2..9) {
            for (z in 2..6) {
                world.setBlock(x, y - 1, z, BlockType.STONE, recordUndo = false)
                world.setBlock(x, y, z, if (z == 4 && x in 3..8) BlockType.AIR else BlockType.STONE, recordUndo = false)
            }
        }
        // Place water at the start of the dug channel (x=3, y, z=4)
        world.setBlock(3, y, 4, BlockType.WATER, recordUndo = false)
        repeat(6) {
            world.stepFluidPhysics(maxWaterUpdates = 24, maxLavaUpdates = 8)
        }
        assertEquals(
            "Water should dynamically flow into dug channel cells",
            BlockType.WATER,
            world.getBlock(5, y, 4)
        )

        // Place Lava at the end of the channel and run fluid physics so Water + Lava forms Obsidian/Cobblestone
        world.setBlock(7, y, 4, BlockType.LAVA, recordUndo = false)
        var obsidianFormed = 0
        repeat(8) {
            obsidianFormed += world.stepFluidPhysics(maxWaterUpdates = 24, maxLavaUpdates = 8).obsidianFormed
        }
        val boundaryBlock = world.getBlock(7, y, 4)
        assertTrue(
            "Water meeting Lava should quench into Obsidian or Cobblestone (was $boundaryBlock, obsidianFormed=$obsidianFormed)",
            obsidianFormed > 0 || boundaryBlock == BlockType.OBSIDIAN || boundaryBlock == BlockType.COBBLESTONE
        )
    }

    @Test
    fun autonomousSocietyAndFriendshipWork() {
        val world = VoxelChunkWorld(BiomeType.EMERALD_VALLEY, seed = 3344L)
        val spawnY = world.findSpawnY(8, 8)
        val player = VoxelPlayerController(8.5f, spawnY, 8.5f)
        val manager = com.example.engine.gl.VoxelEntityManager(3344L)
        manager.populateInitialEcosystem(world, player.x, player.z)

        val npcs = manager.getTalkableSnapshots(player)
        val kinds = npcs.map { it.kind }.toSet()
        assertTrue("Society should include Raja", com.example.engine.gl.EntityKind.RAJA in kinds)
        assertTrue("Society should include Pujari", com.example.engine.gl.EntityKind.PUJARI in kinds)
        assertTrue("Society should include Majdur", com.example.engine.gl.EntityKind.MAJDUR in kinds)
        assertTrue("Society should include Amir Seth", com.example.engine.gl.EntityKind.AMIR_SETH in kinds)
        assertTrue("Society should include Garib Kisan", com.example.engine.gl.EntityKind.GARIB_KISAN in kinds)

        val majdur = npcs.first { it.kind == com.example.engine.gl.EntityKind.MAJDUR }
        val updated = manager.boostFriendship(majdur.id, delta = 35)
        assertNotNull(updated)
        assertTrue(
            "Boosting friendship should increase friendship score",
            updated!!.friendshipLevel >= majdur.friendshipLevel + 35
        )
        val buildResult = manager.applyNpcDecision(majdur.id, com.example.engine.gl.AiGoal.BUILD_HOUSE, world, player)
        assertNotNull("Autonomous build decision should return Hindi status", buildResult)
    }

    @Test
    fun customObj3DParserNormalizesMeshAndRejectsInvalidInput() {
        val sampleObj = """
            # Mini 3D Pyramid Character
            v 0.0 0.0 0.0
            v 2.0 0.0 0.0
            v 2.0 0.0 2.0
            v 0.0 0.0 2.0
            v 1.0 4.0 1.0
            f 1 2 5
            f 2 3 5
            f 3 4 5
            f 4 1 5
            f 1 2 3 4
        """.trimIndent()

        val parsed = com.example.engine.gl.CustomModelParser.parseObjStream(sampleObj.byteInputStream()).getOrNull()
        assertNotNull("Valid .obj mesh should parse cleanly", parsed)
        assertEquals("Quad + 4 tris = 6 triangles", 6, parsed!!.triangleCount)
        assertEquals(6 * 3 * 4, parsed.localVertices.size)

        val invalid = com.example.engine.gl.CustomModelParser.parseObjStream("not an obj file".byteInputStream())
        assertTrue("Invalid/empty .obj should fail cleanly", invalid.isFailure)
    }

    @Test
    fun adminLiveMapTreesWaterAndStructuresWorkInRealTime() {
        val world = VoxelChunkWorld(BiomeType.EMERALD_VALLEY, seed = 6060L)
        val cx = 14
        val cz = 14

        // Plant a tall tree and verify wood trunk exists above surface
        val surfaceBefore = world.findSpawnY(cx, cz).toInt()
        world.plantTreeAt(cx, cz, tall = true)
        assertEquals(BlockType.WOOD_LOG, world.getBlock(cx, surfaceBefore, cz))

        // Create real flowing water lake and verify water blocks exist
        world.createWaterSpringOrRiver(cx + 10, cz, radius = 3, waterfall = false)
        var foundWater = false
        for (y in 2..40) {
            if (world.getBlock(cx + 10, y, cz) == BlockType.WATER) {
                foundWater = true
                break
            }
        }
        assertTrue("Admin createWaterSpringOrRiver should carve real water blocks", foundWater)

        // Build Admin Palace and verify gold/stone structure blocks placed
        world.buildAdminLiveStructure(cx - 12, cz + 12, "PALACE")
        assertTrue("Admin edits should be recorded in world deltas", world.exportDeltas().size > 20)
    }

    @Test
    fun custom3DModelsEnemySpawningProximityAndVoiceWorkActionsWork() {
        val world = VoxelChunkWorld(BiomeType.EMERALD_VALLEY, seed = 7070L)
        val spawnY = world.findSpawnY(8, 8)
        val player = VoxelPlayerController(8.5f, spawnY, 8.5f)
        val manager = com.example.engine.gl.VoxelEntityManager(7070L)

        val heroModel = com.example.data.AdminCustom3DModel(
            id = "hero_1",
            name = "Cyber Veer 3D",
            role = com.example.data.CustomModelRole.SHOP_CHARACTER,
            listedInShop = true,
            priceCoins = 150,
            maxHealth = 180,
            hindiGreeting = "नमस्ते मित्र, मैं साइबर वीर हूँ!"
        )
        val enemyModel = com.example.data.AdminCustom3DModel(
            id = "boss_1",
            name = "Rakshas Boss 3D",
            role = com.example.data.CustomModelRole.ENEMY_DUSHMAN,
            listedInShop = false,
            maxHealth = 260,
            attackDamage = 3
        )

        val spawnedHero = manager.spawnCustomModelInFront(world, player, heroModel, parsedMesh = null, distance = 3.0f)
        val spawnedEnemy = manager.spawnCustomModelInFront(world, player, enemyModel, parsedMesh = null, distance = 5.0f)
        assertEquals(com.example.engine.gl.EntityKind.CUSTOM_HERO, spawnedHero.kind)
        assertEquals(com.example.engine.gl.EntityKind.CUSTOM_ENEMY, spawnedEnemy.kind)
        assertTrue("Custom enemy must be hostile", spawnedEnemy.kind.isHostile)

        // Proximity detection should find the friendly custom hero (skipping hostile enemy)
        val nearby = manager.findNearestProximityEntity(player, maxRange = 7.0f)
        assertNotNull("Nearest proximity entity should be detected", nearby)
        assertEquals("Cyber Veer 3D", nearby?.displayName)

        // Execute live voice work commands: BRING_WATER & PLANT_TREES
        val waterMsg = manager.applyNpcDecision(spawnedHero.id, com.example.engine.gl.AiGoal.BRING_WATER, world, player)
        assertNotNull("BRING_WATER voice command should succeed", waterMsg)
        val treeMsg = manager.applyNpcDecision(spawnedHero.id, com.example.engine.gl.AiGoal.PLANT_TREES, world, player)
        assertNotNull("PLANT_TREES voice command should succeed", treeMsg)

        // Equip hero as player's 3rd-person custom avatar and verify OpenGL vertex buffer builds cleanly
        manager.playerCustomModel = heroModel
        val (_, vertCount) = manager.buildRenderBuffer(playerForThirdPerson = player, armSwingProgress = 0.4f)
        assertTrue("Custom 3D avatar & entities should build valid OpenGL triangles", vertCount > 200)
    }

    @Test
    fun diagonalSmoothAutoStepAndCoyoteTimeNeverStall() {
        val world = VoxelChunkWorld(BiomeType.FLOWER_PARADISE, seed = 9191L)
        val floorY = 26
        // Build a diagonal staircase of 1-block grass steps so diagonal walking must smoothly climb without stalling
        for (x in 2..14) {
            for (z in 2..14) {
                world.setBlock(x, floorY - 1, z, BlockType.GRASS, recordUndo = false)
                for (dy in 0..5) {
                    world.setBlock(x, floorY + dy, z, BlockType.AIR, recordUndo = false)
                }
            }
        }
        for (x in 2..8) {
            for (z in 2..8) {
                world.setBlock(x, floorY, z, BlockType.GRASS, recordUndo = false)
            }
        }

        val player = VoxelPlayerController(9.6f, floorY.toFloat(), 9.6f)
        player.yaw = -0.78f // Facing diagonally toward (-X, -Z)
        player.moveInputY = 0.92f
        val startX = player.x
        val startZ = player.z
        repeat(45) {
            player.stepPhysics(world, dtRaw = 0.02f, creativeMode = false)
        }
        assertTrue("Diagonal movement should climb the 1-block corner step smoothly", player.y >= floorY + 0.95f)
        assertTrue("Player should advance continuously along X and Z without stalling", player.x < startX - 1.5f && player.z < startZ - 1.5f)
    }

    @Test
    fun allEightBiomeMapsAndFlowersGenerateCleanly() {
        for (biome in BiomeType.entries) {
            val world = VoxelChunkWorld(biome, seed = 12345L + biome.ordinal)
            world.prewarmChunksAround(centerCx = 0, centerCz = 0, radius = 1)
            val spawnY = world.findSpawnY(8, 8)
            assertTrue("Biome ${biome.name} should have valid spawnY (got $spawnY)", spawnY in 3f..56f)
            val meshes = world.prepareVisibleMeshes(playerX = 8f, playerZ = 8f, renderDistance = 1, maxRebuildsPerFrame = 4)
            assertTrue("Biome ${biome.name} should produce visible terrain geometry", meshes.sumOf { it.opaqueVertexCount } > 50)
        }

        // Verify Flower Paradise spawns lush flowers/cherry trees across a wide patch
        val flowerWorld = VoxelChunkWorld(BiomeType.FLOWER_PARADISE, seed = 55555L)
        var flowerCount = 0
        for (x in -24..24) {
            for (z in -24..24) {
                for (y in 8..42) {
                    val b = flowerWorld.getBlock(x, y, z)
                    if (b == BlockType.FLOWER_RED || b == BlockType.FLOWER_GOLD ||
                        b == BlockType.FLOWER_BLUE || b == BlockType.FLOWER_PINK ||
                        b == BlockType.FLOWER_PURPLE || b == BlockType.FLOWER_SUNFLOWER ||
                        b == BlockType.CHERRY_LEAVES
                    ) {
                        flowerCount++
                    }
                }
            }
        }
        assertTrue("Mega Flower Valley should generate abundant flowers and cherry blossoms (found $flowerCount)", flowerCount >= 20)
    }

    @Test
    fun nearbyCharacterStopsAndWaitsForHello() {
        val world = VoxelChunkWorld(BiomeType.EMERALD_VALLEY, seed = 4545L)
        val floorY = 26
        for (x in 2..14) {
            for (z in 2..14) {
                world.setBlock(x, floorY - 1, z, BlockType.GRASS, recordUndo = false)
                for (dy in 0..4) {
                    world.setBlock(x, floorY + dy, z, BlockType.AIR, recordUndo = false)
                }
            }
        }
        val player = VoxelPlayerController(8.5f, floorY.toFloat(), 8.5f)
        val manager = com.example.engine.gl.VoxelEntityManager(4545L)
        val npc = manager.spawnEntityInFront(world, player, com.example.engine.gl.EntityKind.RAJA, distance = 3.2f)

        val xBefore = npc.x
        val zBefore = npc.z
        repeat(25) {
            manager.stepSimulation(world, player, dt = 0.02f, creativeMode = true, isNight = false)
        }
        val movedDist = kotlin.math.hypot((npc.x - xBefore).toDouble(), (npc.z - zBefore).toDouble())
        assertTrue("Character near player should stop walking and wait for Hello (moved $movedDist)", movedDist < 0.25)

        val nearbyList = manager.findNearbyInteractiveEntities(player, maxRange = 7.5f)
        assertTrue("Nearby interactive list should include stopped Raja", nearbyList.any { it.id == npc.id })
    }

    @Test
    fun realEstateTownshipAndThreeModeCameraWork() {
        val world = VoxelChunkWorld(BiomeType.EMERALD_VALLEY, seed = 2027L)
        val baseY = com.example.engine.gl.SEA_LEVEL + 3

        // 1. Verify Grand Boulevard paved road & gold lane line exist
        val roadBlock = world.getBlock(26, baseY - 1, 28)
        assertTrue("Grand Boulevard road should be paved cobblestone", roadBlock == BlockType.COBBLESTONE || roadBlock == BlockType.GOLD_ORE)

        // 2. Verify Royal Green Luxury Villa has private swimming pool water & glass windows
        val poolWater = world.getBlock(44, baseY - 1, 39)
        assertEquals("Luxury Villa swimming pool should contain real water", BlockType.WATER, poolWater)

        // 3. Verify 4-Story Skyview Apartment Tower rises high with rooftop gold helipad
        val towerHelipad = world.getBlock(10, baseY + 16, 44)
        assertEquals("4-Story Apartment Tower rooftop should have gold Helipad center", BlockType.GOLD_ORE, towerHelipad)

        // 4. Verify on-demand Real Estate construction builds a Luxury Villa cleanly
        world.buildRealEstateStructure(originX = -70, originZ = -70, structureType = "LUXURY_VILLA")
        assertTrue("On-demand Real Estate construction should record edits", world.exportDeltas().size > 100)

        // 5. Verify 3-mode Camera cycling & level horizon reset
        val player = VoxelPlayerController(8.5f, baseY.toFloat(), 8.5f)
        player.rotateLook(0.2f, -1.2f)
        player.resetCameraLevel()
        assertEquals(-0.03f, player.pitch, 0.001f)
        assertEquals(com.example.engine.gl.CameraMode.THIRD_PERSON_BACK, com.example.engine.gl.CameraMode.FIRST_PERSON.next())
        assertEquals(com.example.engine.gl.CameraMode.THIRD_PERSON_FRONT, com.example.engine.gl.CameraMode.THIRD_PERSON_BACK.next())
        assertEquals(com.example.engine.gl.CameraMode.FIRST_PERSON, com.example.engine.gl.CameraMode.THIRD_PERSON_FRONT.next())
    }
}



