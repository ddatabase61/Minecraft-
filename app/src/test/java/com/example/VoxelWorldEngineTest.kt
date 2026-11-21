package com.example

import com.example.data.model.BiomeType
import com.example.data.model.BlockType
import com.example.engine.BlockFace
import com.example.engine.BlockPos
import com.example.engine.RaycastHit
import com.example.engine.VoxelWorldEngine
import com.example.engine.WORLD_HEIGHT
import com.example.engine.WORLD_SIZE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoxelWorldEngineTest {

    @Test
    fun deterministicGeneration_sameSeedProducesIdenticalTerrain() {
        val a = VoxelWorldEngine(BiomeType.EMERALD_VALLEY, seed = 424242L)
        val b = VoxelWorldEngine(BiomeType.EMERALD_VALLEY, seed = 424242L)

        for (x in 0 until WORLD_SIZE) {
            for (z in 0 until WORLD_SIZE) {
                assertEquals(BlockType.BEDROCK, a.getBlock(x, 0, z))
                for (y in 0 until WORLD_HEIGHT) {
                    assertEquals(a.getBlock(x, y, z), b.getBlock(x, y, z))
                }
            }
        }
    }

    @Test
    fun setBlock_tracksDeltasAndRestoresOnUndo() {
        val engine = VoxelWorldEngine(BiomeType.DESERT_DUNES, seed = 999L)
        val pos = BlockPos(5, 8, 5)
        assertEquals(BlockType.AIR, engine.getBlock(pos))

        engine.setBlock(pos, BlockType.BRICK)
        assertEquals(BlockType.BRICK, engine.getBlock(pos))
        assertEquals(BlockType.BRICK.id.toLong(), engine.exportDeltas()[pos.key()])
        assertTrue(engine.canUndo())

        engine.undo()
        assertEquals(BlockType.AIR, engine.getBlock(pos))
        assertFalse(engine.exportDeltas().containsKey(pos.key()))
    }

    @Test
    fun placementTarget_topFaceAlwaysPlacesAboveAcrossRotations() {
        val engine = VoxelWorldEngine(BiomeType.FROST_PEAKS, seed = 123L)
        val hit = RaycastHit(BlockPos(6, 4, 6), BlockType.SNOW, BlockFace.TOP)
        for (rot in 0..3) {
            assertEquals(BlockPos(6, 5, 6), engine.placementTarget(hit, rot))
        }
    }
}
