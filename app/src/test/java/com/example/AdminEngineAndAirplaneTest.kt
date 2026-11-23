package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.AdminStudioStore
import com.example.data.model.AdminEngineAsset
import com.example.data.model.AircraftType
import com.example.data.model.BiomeType
import com.example.data.model.BlockType
import com.example.engine.gl.VoxelChunkWorld
import com.example.engine.gl.VoxelEntityManager
import com.example.engine.gl.VoxelPlayerController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdminEngineAndAirplaneTest {

    @Test
    fun singleUserAdminClaim_locksSecondUser_persistsSession_andEnforcesLockout() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val store = AdminStudioStore.getInstance(app)

        // Generate strong random password on-device (never hardcoded)
        val p1 = store.generateStrongAdminPassword()
        val p2 = store.generateStrongAdminPassword()
        assertTrue("Generated password must be at least 12 chars", p1.length >= 12)
        assertNotEquals("Random passwords must differ", p1, p2)

        // First owner claims admin
        val claimRes = store.claimAdminCredentials("owner@blockforge.studio", p1)
        assertTrue("First owner claim should succeed", claimRes.isSuccess)
        assertTrue("Session should be unlocked after claim", store.snapshot.value.isUnlockedInSession)
        assertTrue("Single-user ownership lock must be active", store.snapshot.value.singleUserLocked)

        // Second user attempting to claim must be rejected
        val secondClaim = store.claimAdminCredentials("attacker@example.com", "AnotherPass99!")
        assertTrue("Second admin claim must be rejected", secondClaim.isFailure)

        // Explicit lock clears persistent session token
        store.lockAdminSession()
        assertFalse("Session should be locked after lockAdminSession()", store.snapshot.value.isUnlockedInSession)

        // 5 wrong login attempts trigger brute-force lockout
        repeat(5) {
            val bad = store.loginAdmin("owner@blockforge.studio", "WrongPass123!")
            assertTrue("Wrong password must fail", bad.isFailure)
        }
        assertTrue("Lockout timer should activate after 5 bad attempts", store.snapshot.value.lockoutRemainingSec > 0)

        // Publish Rank #1 Live Map metadata persists cleanly
        val published = store.publishLiveMap(
            title = "Mega Bharat Kingdom #1",
            biome = BiomeType.EMERALD_VALLEY,
            announcementBanner = "New Airport & Palace Live!",
            publishedWorldId = "world_rank1_test",
            mapDescription = "Full engine map",
            placedAssetsCount = 12
        )
        assertTrue(published.published)
        assertEquals("world_rank1_test", published.publishedWorldId)
        assertEquals("Mega Bharat Kingdom #1", published.title)
        assertTrue(published.placedAssetsCount >= 12)
    }

    @Test
    fun flyableAirplanePhysics_takeoffClimbBoostAndLandCleanly() {
        val world = VoxelChunkWorld(BiomeType.EMERALD_VALLEY, seed = 4242L)
        val spawnY = world.findSpawnY(8, 8)
        val controller = VoxelPlayerController(8.5f, spawnY, 8.5f)

        controller.mountPlane(AircraftType.RAFALE_FIGHTER)
        assertEquals(AircraftType.RAFALE_FIGHTER, controller.activePlane)
        assertTrue("Plane starts in flying mode", controller.flying)
        assertTrue("Plane should lift off above ground", controller.y > spawnY)

        // Fly forward + climb + afterburner boost
        val startY = controller.y
        controller.moveInputY = 1.0f
        controller.planeClimbHeld = true
        assertTrue(controller.triggerPlaneBoost())
        repeat(24) {
            controller.stepPhysics(world, 0.04f, creativeMode = true)
        }
        assertTrue("Airplane should climb smoothly", controller.y > startY + 2f)
        assertTrue("Airplane speed should exceed 300 km/h", controller.planeSpeedKmh > 300)

        // Land / dismount cleanly
        controller.dismountPlane(world)
        assertNull(controller.activePlane)
        assertEquals(0, controller.planeSpeedKmh)
    }

    @Test
    fun adminGameEngineAssets_digPitsPlantNatureBuildRoadsMandapAndRunway() {
        val world = VoxelChunkWorld(BiomeType.EMERALD_VALLEY, seed = 7777L)
        val groundY = world.findSpawnY(20, 20).toInt() - 1

        // 1. Dig pit (गड्ढा)
        world.applyAdminEngineAsset(20, groundY, 20, AdminEngineAsset.DIG_SMALL_PIT, brushRadius = 2)
        assertEquals(BlockType.AIR, world.getBlock(20, groundY, 20))

        // 2. Build Stone Road (सड़क)
        world.applyAdminEngineAsset(30, groundY, 20, AdminEngineAsset.STONE_ROAD, brushRadius = 2)
        val roadY = world.findSpawnY(30, 20).toInt() - 1
        val roadBlock = world.getBlock(30, roadY, 20)
        assertTrue(
            "Road surface should be STONE or SANDSTONE, got $roadBlock",
            roadBlock == BlockType.STONE || roadBlock == BlockType.SANDSTONE
        )

        // 3. Plant Bush & Cherry Tree & Flower Garden
        world.applyAdminEngineAsset(40, groundY, 20, AdminEngineAsset.GREEN_BUSH, brushRadius = 2)
        world.applyAdminEngineAsset(48, groundY, 20, AdminEngineAsset.CHERRY_TREE, brushRadius = 2)
        world.applyAdminEngineAsset(56, groundY, 20, AdminEngineAsset.FLOWER_GARDEN, brushRadius = 2)

        // 4. Build Wedding Mandap & Airport Runway
        val beforeCount = world.exportDeltas().size
        world.applyAdminEngineAsset(64, groundY, 20, AdminEngineAsset.WEDDING_MANDAP, brushRadius = 2)
        val afterMandap = world.exportDeltas().size
        assertTrue("Wedding Mandap should add blocks to world", afterMandap > beforeCount + 10)

        world.applyAdminEngineAsset(80, groundY, 20, AdminEngineAsset.AIRPORT_RUNWAY, brushRadius = 2)
        val afterRunway = world.exportDeltas().size
        assertTrue("Airport Runway should add runway blocks to world", afterRunway > afterMandap + 20)
    }

    @Test
    fun livingHumanCivilization_weddingsAndInternationalWarWork() {
        val world = VoxelChunkWorld(BiomeType.EMERALD_VALLEY, seed = 9001L)
        val spawnY = world.findSpawnY(8, 8)
        val controller = VoxelPlayerController(8.5f, spawnY, 8.5f)
        val entities = VoxelEntityManager(seed = 9001L)
        entities.populateInitialEcosystem(world, controller.x, controller.z)

        // Trigger autonomous/admin wedding ceremony
        val prevWeddings = entities.weddingsCount
        val weddingMsg = entities.triggerWeddingCeremony(world, 18f, 18f)
        assertNotNull(weddingMsg)
        assertEquals(prevWeddings + 1, entities.weddingsCount)

        // Spawn nation squads & toggle Desh-Videsh War
        val beforeCount = entities.getTalkableSnapshots(controller).size
        entities.spawnNationSquad(world, controller, isVidesh = false)
        entities.spawnNationSquad(world, controller, isVidesh = true)
        val afterCount = entities.getTalkableSnapshots(controller).size
        assertTrue("Nation squads should spawn new human soldiers", afterCount > beforeCount)

        assertTrue("War toggle should activate war", entities.toggleInternationalWar())
        assertTrue(entities.warModeEnabled)
        assertFalse("Second toggle should restore peace", entities.toggleInternationalWar())
    }
}
