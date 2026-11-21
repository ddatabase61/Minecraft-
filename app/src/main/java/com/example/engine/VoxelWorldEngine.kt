package com.example.engine

import com.example.data.model.BiomeType
import com.example.data.model.BlockType
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

const val WORLD_SIZE = 18
const val WORLD_HEIGHT = 12

enum class BlockFace {
    TOP,
    LEFT,
    RIGHT
}

data class BlockPos(val x: Int, val y: Int, val z: Int) {
    fun key(): String = "${x}_${y}_${z}"

    fun isInBounds(): Boolean =
        x in 0 until WORLD_SIZE && y in 0 until WORLD_HEIGHT && z in 0 until WORLD_SIZE
}

data class BlockEdit(
    val pos: BlockPos,
    val previousBlock: BlockType,
    val newBlock: BlockType
)

data class RaycastHit(
    val pos: BlockPos,
    val block: BlockType,
    val face: BlockFace
)

data class VisibleVoxel(
    val pos: BlockPos,
    val block: BlockType,
    val screenX: Float,
    val screenY: Float,
    val torchLight: Float // 0f..1f dynamic glow from nearby torches/lava
)

/**
 * Deterministic procedural voxel terrain generator + delta overlay + isometric projection & hit-tester.
 */
class VoxelWorldEngine(
    val biome: BiomeType,
    val seed: Long,
    initialDeltas: Map<String, Long> = emptyMap()
) {
    private val baseBlocks = IntArray(WORLD_SIZE * WORLD_HEIGHT * WORLD_SIZE)
    private val currentBlocks = IntArray(WORLD_SIZE * WORLD_HEIGHT * WORLD_SIZE)
    private val deltas = mutableMapOf<String, Long>()
    private val undoStack = ArrayDeque<BlockEdit>()

    init {
        generateBaseTerrain()
        baseBlocks.copyInto(currentBlocks)
        applyDeltas(initialDeltas)
    }

    private fun indexOf(x: Int, y: Int, z: Int): Int =
        (y * WORLD_SIZE + z) * WORLD_SIZE + x

    fun getBlock(x: Int, y: Int, z: Int): BlockType {
        if (x !in 0 until WORLD_SIZE || y !in 0 until WORLD_HEIGHT || z !in 0 until WORLD_SIZE) {
            return BlockType.AIR
        }
        return BlockType.fromId(currentBlocks[indexOf(x, y, z)])
    }

    fun getBlock(pos: BlockPos): BlockType = getBlock(pos.x, pos.y, pos.z)

    fun setBlock(pos: BlockPos, newType: BlockType, recordUndo: Boolean = true): BlockType {
        if (!pos.isInBounds()) return BlockType.AIR
        val idx = indexOf(pos.x, pos.y, pos.z)
        val prev = BlockType.fromId(currentBlocks[idx])
        if (prev == newType) return prev

        currentBlocks[idx] = newType.id
        val key = pos.key()
        if (newType.id == baseBlocks[idx]) {
            deltas.remove(key)
        } else {
            deltas[key] = newType.id.toLong()
        }
        if (recordUndo) {
            if (undoStack.size >= 80) undoStack.removeFirst()
            undoStack.addLast(BlockEdit(pos, prev, newType))
        }
        return prev
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()

    fun undo(): BlockEdit? {
        val last = undoStack.removeLastOrNull() ?: return null
        setBlock(last.pos, last.previousBlock, recordUndo = false)
        return last
    }

    fun exportDeltas(): Map<String, Long> = deltas.toMap()

    private fun applyDeltas(initialDeltas: Map<String, Long>) {
        deltas.clear()
        initialDeltas.forEach { (key, blockIdLong) ->
            val parts = key.split("_")
            if (parts.size == 3) {
                val x = parts[0].toIntOrNull()
                val y = parts[1].toIntOrNull()
                val z = parts[2].toIntOrNull()
                if (x != null && y != null && z != null) {
                    val pos = BlockPos(x, y, z)
                    if (pos.isInBounds()) {
                        val blockId = blockIdLong.toInt().coerceIn(0, BlockType.entries.last().id)
                        val idx = indexOf(x, y, z)
                        currentBlocks[idx] = blockId
                        if (blockId != baseBlocks[idx]) {
                            deltas[key] = blockId.toLong()
                        }
                    }
                }
            }
        }
    }

    private fun setBase(x: Int, y: Int, z: Int, type: BlockType) {
        if (x in 0 until WORLD_SIZE && y in 0 until WORLD_HEIGHT && z in 0 until WORLD_SIZE) {
            baseBlocks[indexOf(x, y, z)] = type.id
        }
    }

    private fun getBase(x: Int, y: Int, z: Int): BlockType {
        if (x !in 0 until WORLD_SIZE || y !in 0 until WORLD_HEIGHT || z !in 0 until WORLD_SIZE) {
            return BlockType.AIR
        }
        return BlockType.fromId(baseBlocks[indexOf(x, y, z)])
    }

    private fun generateBaseTerrain() {
        val rng = Random(seed)
        val phaseX = rng.nextDouble(0.0, 6.28)
        val phaseZ = rng.nextDouble(0.0, 6.28)
        val riverOffset = rng.nextDouble(-2.0, 2.0)

        for (x in 0 until WORLD_SIZE) {
            for (z in 0 until WORLD_SIZE) {
                // Layer 0 is always Bedrock
                setBase(x, 0, z, BlockType.BEDROCK)

                val nx = x / 4.2 + phaseX
                val nz = z / 4.2 + phaseZ
                val wave = sin(nx) * cos(nz) + 0.45 * sin(nx * 1.9 + nz * 1.3)
                val distCenter = abs(x - WORLD_SIZE / 2.0) + abs(z - WORLD_SIZE / 2.0)

                val surfaceY = when (biome) {
                    BiomeType.EMERALD_VALLEY -> (4.6 + wave * 1.65).toInt().coerceIn(3, 7)
                    BiomeType.DESERT_DUNES -> (4.2 + wave * 1.45).toInt().coerceIn(3, 7)
                    BiomeType.FROST_PEAKS -> (5.2 + wave * 2.1 + (if (distCenter < 6) 1 else 0)).toInt().coerceIn(3, 8)
                    BiomeType.VOLCANIC_BADLANDS -> (4.4 + wave * 1.8).toInt().coerceIn(3, 7)
                }

                // River / Oasis / Magma channel
                val channelCenter = (WORLD_SIZE / 2.0) + sin(z * 0.35 + phaseX) * 2.4 + riverOffset
                val inChannel = abs(x - channelCenter) < 1.35

                val effectiveSurface = if (inChannel) 3 else surfaceY

                for (y in 1..effectiveSurface) {
                    val block = when {
                        y == effectiveSurface -> surfaceBlockFor(biome, y, inChannel)
                        y >= effectiveSurface - 1 -> subSurfaceBlockFor(biome)
                        else -> generateUndergroundBlock(y, rng)
                    }
                    setBase(x, y, z, block)
                }

                // Fill channel with water / ice / lava at y = 4
                if (inChannel) {
                    val fluid = when (biome) {
                        BiomeType.EMERALD_VALLEY -> BlockType.WATER
                        BiomeType.DESERT_DUNES -> BlockType.WATER
                        BiomeType.FROST_PEAKS -> BlockType.ICE
                        BiomeType.VOLCANIC_BADLANDS -> BlockType.LAVA
                    }
                    setBase(x, 4, z, fluid)
                }
            }
        }

        // Add biome signature structures and trees
        when (biome) {
            BiomeType.EMERALD_VALLEY -> {
                placeOakTree(3, 3)
                placeOakTree(14, 4)
                placeOakTree(4, 14)
                placeOakTree(13, 13)
                // Cozy starter stone & timber outpost platform with glowing torches
                buildStarterCamp(8, 8, BlockType.COBBLESTONE, BlockType.PLANKS)
            }

            BiomeType.DESERT_DUNES -> {
                placePalmTree(4, 5)
                placePalmTree(13, 12)
                buildDesertShrine(11, 5)
            }

            BiomeType.FROST_PEAKS -> {
                placePineTree(3, 4)
                placePineTree(14, 5)
                placePineTree(5, 13)
                buildStarterCamp(10, 10, BlockType.STONE, BlockType.WOOD_LOG)
            }

            BiomeType.VOLCANIC_BADLANDS -> {
                buildVolcanicSpire(4, 4)
                buildVolcanicSpire(14, 13)
                buildStarterCamp(11, 6, BlockType.BRICK, BlockType.OBSIDIAN)
            }
        }
    }

    private fun surfaceBlockFor(biome: BiomeType, y: Int, inChannel: Boolean): BlockType {
        if (inChannel) {
            return when (biome) {
                BiomeType.VOLCANIC_BADLANDS -> BlockType.OBSIDIAN
                else -> BlockType.SAND
            }
        }
        return when (biome) {
            BiomeType.EMERALD_VALLEY -> BlockType.GRASS
            BiomeType.DESERT_DUNES -> if (y >= 6) BlockType.SANDSTONE else BlockType.SAND
            BiomeType.FROST_PEAKS -> BlockType.SNOW
            BiomeType.VOLCANIC_BADLANDS -> if (y >= 6) BlockType.OBSIDIAN else BlockType.STONE
        }
    }

    private fun subSurfaceBlockFor(biome: BiomeType): BlockType = when (biome) {
        BiomeType.EMERALD_VALLEY -> BlockType.DIRT
        BiomeType.DESERT_DUNES -> BlockType.SANDSTONE
        BiomeType.FROST_PEAKS -> BlockType.DIRT
        BiomeType.VOLCANIC_BADLANDS -> BlockType.COBBLESTONE
    }

    private fun generateUndergroundBlock(y: Int, rng: Random): BlockType {
        val roll = rng.nextInt(100)
        return when {
            y == 1 && roll < 14 -> BlockType.DIAMOND_ORE
            y <= 2 && roll < 26 -> BlockType.GOLD_ORE
            y <= 3 && roll < 40 -> BlockType.IRON_ORE
            roll < 52 -> BlockType.COAL_ORE
            else -> BlockType.STONE
        }
    }

    private fun findSurfaceY(x: Int, z: Int): Int {
        for (y in WORLD_HEIGHT - 1 downTo 1) {
            val b = getBase(x, y, z)
            if (b != BlockType.AIR && b != BlockType.WATER && b != BlockType.LAVA) return y
        }
        return 4
    }

    private fun placeOakTree(cx: Int, cz: Int) {
        val baseY = findSurfaceY(cx, cz)
        val ground = getBase(cx, baseY, cz)
        if (ground == BlockType.WATER || ground == BlockType.LAVA || baseY + 5 >= WORLD_HEIGHT) return
        for (dy in 1..3) {
            setBase(cx, baseY + dy, cz, BlockType.WOOD_LOG)
        }
        for (dx in -1..1) {
            for (dz in -1..1) {
                setBase(cx + dx, baseY + 3, cz + dz, BlockType.LEAVES)
                if (abs(dx) + abs(dz) <= 1) {
                    setBase(cx + dx, baseY + 4, cz + dz, BlockType.LEAVES)
                }
            }
        }
        setBase(cx, baseY + 3, cz, BlockType.WOOD_LOG)
        setBase(cx, baseY + 5, cz, BlockType.LEAVES)
    }

    private fun placePineTree(cx: Int, cz: Int) {
        val baseY = findSurfaceY(cx, cz)
        if (baseY + 5 >= WORLD_HEIGHT) return
        for (dy in 1..3) {
            setBase(cx, baseY + dy, cz, BlockType.WOOD_LOG)
        }
        for (dx in -1..1) {
            for (dz in -1..1) {
                if (dx != 0 || dz != 0) setBase(cx + dx, baseY + 2, cz + dz, BlockType.LEAVES)
                if (abs(dx) + abs(dz) == 1) setBase(cx + dx, baseY + 3, cz + dz, BlockType.LEAVES)
            }
        }
        setBase(cx, baseY + 4, cz, BlockType.LEAVES)
        setBase(cx, baseY + 5, cz, BlockType.SNOW)
    }

    private fun placePalmTree(cx: Int, cz: Int) {
        val baseY = findSurfaceY(cx, cz)
        if (baseY + 4 >= WORLD_HEIGHT) return
        for (dy in 1..3) setBase(cx, baseY + dy, cz, BlockType.WOOD_LOG)
        setBase(cx, baseY + 4, cz, BlockType.LEAVES)
        setBase(cx + 1, baseY + 3, cz, BlockType.LEAVES)
        setBase(cx - 1, baseY + 3, cz, BlockType.LEAVES)
        setBase(cx, baseY + 3, cz + 1, BlockType.LEAVES)
        setBase(cx, baseY + 3, cz - 1, BlockType.LEAVES)
    }

    private fun buildStarterCamp(cx: Int, cz: Int, baseMat: BlockType, trimMat: BlockType) {
        val y = findSurfaceY(cx, cz).coerceIn(4, 6)
        for (dx in -1..1) {
            for (dz in -1..1) {
                setBase(cx + dx, y, cz + dz, baseMat)
                for (clearY in y + 1 until WORLD_HEIGHT) {
                    setBase(cx + dx, clearY, cz + dz, BlockType.AIR)
                }
            }
        }
        setBase(cx - 1, y + 1, cz - 1, trimMat)
        setBase(cx + 1, y + 1, cz - 1, trimMat)
        setBase(cx - 1, y + 2, cz - 1, BlockType.TORCH)
        setBase(cx + 1, y + 1, cz + 1, BlockType.TORCH)
    }

    private fun buildDesertShrine(cx: Int, cz: Int) {
        val y = findSurfaceY(cx, cz).coerceIn(4, 6)
        for (dx in -1..1) {
            for (dz in -1..1) {
                setBase(cx + dx, y, cz + dz, BlockType.SANDSTONE)
                setBase(cx + dx, y + 1, cz + dz, BlockType.AIR)
            }
        }
        setBase(cx, y + 1, cz, BlockType.GOLD_ORE)
        setBase(cx, y + 2, cz, BlockType.TORCH)
        setBase(cx - 1, y + 1, cz - 1, BlockType.SANDSTONE)
        setBase(cx + 1, y + 1, cz - 1, BlockType.SANDSTONE)
    }

    private fun buildVolcanicSpire(cx: Int, cz: Int) {
        val y = findSurfaceY(cx, cz).coerceIn(4, 6)
        for (dy in 1..3) {
            setBase(cx, y + dy, cz, BlockType.OBSIDIAN)
        }
        setBase(cx, y + 4, cz, BlockType.LAVA)
    }

    /**
     * Rotates world coordinates into camera-space (rx, rz) where higher (rx + rz) is closer to the viewer.
     */
    private fun toCameraCoords(x: Int, z: Int, rotation: Int): Pair<Int, Int> {
        val maxIdx = WORLD_SIZE - 1
        return when (rotation and 3) {
            0 -> x to z
            1 -> z to (maxIdx - x)
            2 -> (maxIdx - x) to (maxIdx - z)
            else -> (maxIdx - z) to x
        }
    }

    /**
     * Returns the world-space neighbor offset for placing a block onto the tapped face.
     */
    fun placementTarget(hit: RaycastHit, rotation: Int): BlockPos {
        if (hit.face == BlockFace.TOP) {
            return BlockPos(hit.pos.x, hit.pos.y + 1, hit.pos.z)
        }
        // In camera space:
        // Left face points toward +rz (closer-left), Right face points toward +rx (closer-right)
        val (dRx, dRz) = when (hit.face) {
            BlockFace.LEFT -> 0 to 1
            BlockFace.RIGHT -> 1 to 0
            BlockFace.TOP -> 0 to 0
        }
        val (dx, dz) = when (rotation and 3) {
            0 -> dRx to dRz
            1 -> -dRz to dRx
            2 -> -dRx to -dRz
            else -> dRz to -dRx
        }
        return BlockPos(hit.pos.x + dx, hit.pos.y, hit.pos.z + dz)
    }

    /**
     * Builds the painter-ordered list of visible blocks (with interior occlusion culling and torch light).
     */
    fun buildVisibleVoxels(
        rotation: Int,
        maxVisibleY: Int,
        tileHalfW: Float,
        tileHalfH: Float,
        blockHeight: Float,
        originX: Float,
        originY: Float
    ): List<VisibleVoxel> {
        val clampedMaxY = maxVisibleY.coerceIn(0, WORLD_HEIGHT - 1)

        // Collect dynamic light emitters (torches, lava, diamond ore)
        val lightSources = ArrayList<BlockPos>(16)
        for (y in 0..clampedMaxY) {
            for (z in 0 until WORLD_SIZE) {
                for (x in 0 until WORLD_SIZE) {
                    val b = getBlock(x, y, z)
                    if (b.emitsLight) lightSources.add(BlockPos(x, y, z))
                }
            }
        }

        val visible = ArrayList<VisibleVoxel>(900)
        // Strict painter's order in camera space: rx ascending, rz ascending, y ascending
        for (rx in 0 until WORLD_SIZE) {
            for (rz in 0 until WORLD_SIZE) {
                val (wx, wz) = fromCameraCoords(rx, rz, rotation)
                for (y in 0..clampedMaxY) {
                    val block = getBlock(wx, y, wz)
                    if (block == BlockType.AIR) continue

                    // Cull completely occluded interior opaque blocks
                    if (!block.translucent && isOccluded(rx, y, rz, clampedMaxY, rotation)) {
                        continue
                    }

                    val screenX = originX + (rx - rz) * tileHalfW
                    val screenY = originY + (rx + rz) * tileHalfH - y * blockHeight

                    // Compute warm light intensity from nearest light source
                    var light = if (block.emitsLight) 1f else 0f
                    if (light < 1f && lightSources.isNotEmpty()) {
                        for (i in lightSources.indices) {
                            val ls = lightSources[i]
                            val dist = abs(wx - ls.x) + abs(y - ls.y) + abs(wz - ls.z)
                            if (dist <= 4) {
                                val contrib = (1f - dist / 4.8f).coerceIn(0f, 1f)
                                if (contrib > light) light = contrib
                            }
                        }
                    }

                    visible.add(
                        VisibleVoxel(
                            pos = BlockPos(wx, y, wz),
                            block = block,
                            screenX = screenX,
                            screenY = screenY,
                            torchLight = light
                        )
                    )
                }
            }
        }
        return visible
    }

    private fun fromCameraCoords(rx: Int, rz: Int, rotation: Int): Pair<Int, Int> {
        val maxIdx = WORLD_SIZE - 1
        return when (rotation and 3) {
            0 -> rx to rz
            1 -> (maxIdx - rz) to rx
            2 -> (maxIdx - rx) to (maxIdx - rz)
            else -> rz to (maxIdx - rx)
        }
    }

    private fun isOccluded(
        rx: Int,
        y: Int,
        rz: Int,
        maxVisibleY: Int,
        rotation: Int
    ): Boolean {
        if (y >= maxVisibleY || rx == WORLD_SIZE - 1 || rz == WORLD_SIZE - 1) return false
        val (wx, wz) = fromCameraCoords(rx, rz, rotation)
        val topBlock = getBlock(wx, y + 1, wz)
        if (topBlock == BlockType.AIR || topBlock.translucent) return false

        val (lx, lz) = fromCameraCoords(rx, rz + 1, rotation)
        val leftFront = getBlock(lx, y, lz)
        if (leftFront == BlockType.AIR || leftFront.translucent) return false

        val (rx2, rz2) = fromCameraCoords(rx + 1, rz, rotation)
        val rightFront = getBlock(rx2, y, rz2)
        if (rightFront == BlockType.AIR || rightFront.translucent) return false

        return true
    }

    /**
     * Exact reverse-painter hit-testing against each visible block's Top, Left, and Right faces.
     */
    fun hitTest(
        tapX: Float,
        tapY: Float,
        visibleVoxels: List<VisibleVoxel>,
        tileHalfW: Float,
        tileHalfH: Float,
        blockHeight: Float
    ): RaycastHit? {
        for (i in visibleVoxels.lastIndex downTo 0) {
            val v = visibleVoxels[i]
            val cx = v.screenX
            val cy = v.screenY

            // Fast bounding-box rejection
            if (tapX < cx - tileHalfW || tapX > cx + tileHalfW) continue
            if (tapY < cy - tileHalfH || tapY > cy + tileHalfH + blockHeight) continue

            // Special forgiving hit box for Torch
            if (v.block == BlockType.TORCH) {
                return RaycastHit(v.pos, v.block, BlockFace.TOP)
            }

            // 1. Check TOP diamond:
            // (cx, cy - tileHalfH), (cx + tileHalfW, cy), (cx, cy + tileHalfH), (cx - tileHalfW, cy)
            val dxNorm = abs(tapX - cx) / tileHalfW
            val dyNorm = abs(tapY - cy) / tileHalfH
            if (dxNorm + dyNorm <= 1.0f) {
                return RaycastHit(v.pos, v.block, BlockFace.TOP)
            }

            // 2. Check LEFT parallelogram face:
            // x in [cx - tileHalfW, cx], top edge y = cy + (tapX - (cx - tileHalfW)) * (tileHalfH / tileHalfW)
            if (tapX in (cx - tileHalfW)..cx) {
                val t = (tapX - (cx - tileHalfW)) / tileHalfW
                val topEdgeY = cy + t * tileHalfH
                if (tapY in topEdgeY..(topEdgeY + blockHeight)) {
                    return RaycastHit(v.pos, v.block, BlockFace.LEFT)
                }
            }

            // 3. Check RIGHT parallelogram face:
            // x in [cx, cx + tileHalfW], top edge y = cy + (1 - (tapX - cx) / tileHalfW) * tileHalfH
            if (tapX in cx..(cx + tileHalfW)) {
                val t = (tapX - cx) / tileHalfW
                val topEdgeY = cy + (1f - t) * tileHalfH
                if (tapY in topEdgeY..(topEdgeY + blockHeight)) {
                    return RaycastHit(v.pos, v.block, BlockFace.RIGHT)
                }
            }
        }
        return null
    }
}
