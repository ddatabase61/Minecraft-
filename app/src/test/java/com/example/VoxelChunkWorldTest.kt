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
        assertTrue("Spawn height should be above bedrock", spawnY in 3..28)

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
        assertTrue("Initial ecosystem should include heroes, animals, birds, and zombies", totalEntities >= 18)
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
        val baseY = 18

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
}

