package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.GuestLocalDataSource
import com.example.data.model.BikeType
import com.example.data.model.BiomeType
import com.example.data.model.BlockType
import com.example.data.model.GameMode
import com.example.engine.gl.VoxelChunkWorld
import com.example.engine.gl.VoxelPlayerController
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BikeAndWorldFixTest {

    @Test
    fun guestLocalDataSource_createsAndImmediatelyReadsAllEightBiomes() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sourceA = GuestLocalDataSource.getInstance(context)
        val sourceB = GuestLocalDataSource.getInstance(context)
        assertEquals(sourceA, sourceB)

        BiomeType.entries.forEachIndexed { idx, biome ->
            val created = sourceA.createWorld(
                name = "Test World $idx",
                biome = biome,
                gameMode = GameMode.SURVIVAL,
                seed = 7000L + idx
            ).getOrThrow()

            val fetched = sourceB.getWorld(created.id).getOrThrow()
            assertNotNull("World for biome ${biome.name} must be immediately retrievable", fetched)
            assertEquals(biome, fetched!!.resolvedBiome())
            assertEquals("Test World $idx", fetched.name)
        }
    }

    @Test
    fun chunkWorld_allBiomesSpawnSafelyAndPreserveCherryLeavesDelta() {
        for (biome in BiomeType.entries) {
            val world = VoxelChunkWorld(biome = biome, seed = 1337L)
            val spawnY = world.findSpawnY(8, 8)
            assertTrue("Spawn Y for $biome should be above bedrock", spawnY >= 2f)

            // Verify CHERRY_LEAVES (id = 31) delta is NOT clamped to 30
            world.setBlock(8, 28, 8, BlockType.CHERRY_LEAVES, recordUndo = false)
            val deltas = world.exportDeltas()
            val reloaded = VoxelChunkWorld(biome = biome, seed = 1337L, initialDeltas = deltas)
            assertEquals(BlockType.CHERRY_LEAVES, reloaded.getBlock(8, 28, 8))
        }
    }

    @Test
    fun bikePhysics_mountsAcceleratesActivatesNitroAndDismounts() {
        val world = VoxelChunkWorld(biome = BiomeType.EMERALD_VALLEY, seed = 2025L)
        val spawnY = world.findSpawnY(8, 8)
        val player = VoxelPlayerController(8.5f, spawnY, 8.5f)

        assertEquals(6, BikeType.entries.size)
        player.mountBike(BikeType.NINJA_ZX10R)
        assertEquals(BikeType.NINJA_ZX10R, player.activeBike)

        player.moveInputY = 1.0f
        player.moveInputX = 0.25f
        var sawEnginePulse = false
        repeat(45) {
            val ev = player.stepPhysics(world, dtRaw = 0.03f, creativeMode = false)
            if (ev.bikeEnginePulse) sawEnginePulse = true
        }

        assertTrue("Bike should accelerate to riding speed", player.bikeSpeedKmh > 15)
        assertTrue("Bike wheels should spin forward", player.bikeWheelSpin > 0.5f)
        assertTrue("Bike should emit engine audio pulses", sawEnginePulse)

        assertTrue(player.triggerBikeNitro())
        val nitroEv = player.stepPhysics(world, dtRaw = 0.03f, creativeMode = false)
        assertTrue("Nitro boost should be active", nitroEv.bikeNitroActive)

        player.dismountBike()
        assertNull(player.activeBike)
        assertEquals(0, player.bikeSpeedKmh)
    }

    @Test
    fun waterSitPhysics_floatsStablyOnWaterSurfaceAndStandsUpCleanly() {
        val world = VoxelChunkWorld(biome = BiomeType.EMERALD_VALLEY, seed = 4040L)
        // Build a calm 3x3 water pool right at (12..14, y=24, 12..14)
        for (x in 11..15) {
            for (z in 11..15) {
                world.setBlock(x, 23, z, BlockType.STONE, recordUndo = false)
                world.setBlock(x, 24, z, BlockType.WATER, recordUndo = false)
                world.setBlock(x, 25, z, BlockType.AIR, recordUndo = false)
                world.setBlock(x, 26, z, BlockType.AIR, recordUndo = false)
            }
        }

        val player = VoxelPlayerController(13.5f, 25.0f, 13.5f)
        assertTrue("Water should be detected for sitting", player.isWaterAvailableForSit(world))

        val sitting = player.toggleWaterSit(world)
        assertTrue("Player should now be sitting on water", sitting)
        assertTrue(player.sittingOnWater)

        var sawRipple = false
        repeat(35) {
            val ev = player.stepPhysics(world, dtRaw = 0.03f, creativeMode = false)
            if (ev.waterSitRipple) sawRipple = true
        }

        assertTrue("Player should still be floating on water surface", player.sittingOnWater)
        assertTrue("Player should not sink below water block", player.y > 24.4f && player.y < 25.3f)
        assertTrue("Sitting on water should produce water ripple sound events", sawRipple)

        val stillSitting = player.toggleWaterSit(world)
        assertFalse("Toggling again should stand up from water", stillSitting)
        assertFalse(player.sittingOnWater)
    }
}
