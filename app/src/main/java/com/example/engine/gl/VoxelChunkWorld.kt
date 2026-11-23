package com.example.engine.gl

import com.example.data.model.BiomeType
import com.example.data.model.BlockType
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

const val CHUNK_SIZE = 16
const val CHUNK_HEIGHT = 48
const val SEA_LEVEL = 14

data class ChunkCoord(val cx: Int, val cz: Int)

data class ChunkMeshData(
    val opaqueBuffer: FloatBuffer?,
    val opaqueVertexCount: Int,
    val translucentBuffer: FloatBuffer?,
    val translucentVertexCount: Int,
    val waterBuffer: FloatBuffer? = null,
    val waterVertexCount: Int = 0,
    val lavaBuffer: FloatBuffer? = null,
    val lavaVertexCount: Int = 0
)

data class VoxelEditRecord(
    val x: Int,
    val y: Int,
    val z: Int,
    val previous: BlockType,
    val updated: BlockType
)

data class FluidStepEvents(
    val waterFlowed: Int = 0,
    val lavaFlowed: Int = 0,
    val obsidianFormed: Int = 0,
    val sizzlePos: Triple<Float, Float, Float>? = null
)

private data class ActiveFluidCell(
    val x: Int,
    val y: Int,
    val z: Int,
    val isLava: Boolean,
    val spreadLeft: Int
)

/**
 * Vast 16x16x48 chunked 3D voxel world featuring:
 * - Towering mountain ranges, wide crystal rivers, oceans, waterfalls & lush flower gardens
 * - Underground water aquifers/rivers (Y=5..8) AND deep molten lava chambers (Y=1..4)
 * - Erupting Volcanoes ("Jwalamukhi") with glowing lava craters & magma streams
 * - Pre-built Sacred Village Mandir & Royal Palace Pavilion in the starting valley
 * - Real Water & Lava Channel Flow Physics: carve trenches anywhere and watch water or lava flow!
 * - Water + Lava contact quenching into Obsidian / Cobblestone with sizzle!
 */
class VoxelChunkWorld(
    val biome: BiomeType,
    val seed: Long,
    initialDeltas: Map<String, Long> = emptyMap()
) {
    private val chunks = HashMap<ChunkCoord, ByteArray>()
    private val dirtyChunks = HashSet<ChunkCoord>()
    private val chunkMeshes = HashMap<ChunkCoord, ChunkMeshData>()
    private val blockDeltas = HashMap<String, Long>()
    private val undoStack = ArrayDeque<VoxelEditRecord>()

    // Dynamic fluid physics queues for real channel water & lava flow
    private val waterFlowQueue = ArrayDeque<ActiveFluidCell>()
    private val lavaFlowQueue = ArrayDeque<ActiveFluidCell>()
    private var fluidTickCounter = 0

    // Landmark coordinates (accessible to AI villagers, Pujari, Raja & World Map)
    val mandirCenterX = 18
    val mandirCenterZ = 4
    val palaceCenterX = -8
    val palaceCenterZ = 14
    val volcanoCenterX = -34
    val volcanoCenterZ = -30
    val pyramidCenterX = 38
    val pyramidCenterZ = -24

    private val lock = Any()

    init {
        initialDeltas.forEach { (key, id) ->
            parseKey(key)?.let { (x, y, z) ->
                if (y in 0 until CHUNK_HEIGHT) {
                    blockDeltas[coordKey(x, y, z)] = id
                }
            }
        }
    }

    private fun coordKey(x: Int, y: Int, z: Int): String = "$x,$y,$z"

    private fun parseKey(key: String): Triple<Int, Int, Int>? {
        val sep = if (',' in key) ',' else '_'
        val parts = key.split(sep)
        if (parts.size != 3) return null
        val x = parts[0].toIntOrNull() ?: return null
        val y = parts[1].toIntOrNull() ?: return null
        val z = parts[2].toIntOrNull() ?: return null
        return Triple(x, y, z)
    }

    private fun chunkCoordOf(wx: Int, wz: Int): ChunkCoord =
        ChunkCoord(Math.floorDiv(wx, CHUNK_SIZE), Math.floorDiv(wz, CHUNK_SIZE))

    private fun localIndex(lx: Int, y: Int, lz: Int): Int =
        (y * CHUNK_SIZE + lz) * CHUNK_SIZE + lx

    private fun ensureChunkLocked(coord: ChunkCoord): ByteArray {
        chunks[coord]?.let { return it }
        val data = generateChunkTerrain(coord.cx, coord.cz)
        val minX = coord.cx * CHUNK_SIZE
        val maxX = minX + CHUNK_SIZE - 1
        val minZ = coord.cz * CHUNK_SIZE
        val maxZ = minZ + CHUNK_SIZE - 1

        blockDeltas.forEach { (key, blockId) ->
            val p = parseKey(key) ?: return@forEach
            if (p.first in minX..maxX && p.third in minZ..maxZ && p.second in 0 until CHUNK_HEIGHT) {
                val lx = p.first - minX
                val lz = p.third - minZ
                data[localIndex(lx, p.second, lz)] = blockId.toInt().coerceIn(0, 31).toByte()
            }
        }

        chunks[coord] = data
        dirtyChunks.add(coord)
        markDirtyIfLoadedLocked(ChunkCoord(coord.cx + 1, coord.cz))
        markDirtyIfLoadedLocked(ChunkCoord(coord.cx - 1, coord.cz))
        markDirtyIfLoadedLocked(ChunkCoord(coord.cx, coord.cz + 1))
        markDirtyIfLoadedLocked(ChunkCoord(coord.cx, coord.cz - 1))
        return data
    }

    private fun markDirtyIfLoadedLocked(coord: ChunkCoord) {
        if (chunks.containsKey(coord)) {
            dirtyChunks.add(coord)
        }
    }

    fun getBlock(wx: Int, wy: Int, wz: Int): BlockType {
        if (wy !in 0 until CHUNK_HEIGHT) return BlockType.AIR
        synchronized(lock) {
            val coord = chunkCoordOf(wx, wz)
            val data = ensureChunkLocked(coord)
            val lx = Math.floorMod(wx, CHUNK_SIZE)
            val lz = Math.floorMod(wz, CHUNK_SIZE)
            return BlockType.fromId(data[localIndex(lx, wy, lz)].toInt() and 0xFF)
        }
    }

    fun isSolid(wx: Int, wy: Int, wz: Int): Boolean {
        val b = getBlock(wx, wy, wz)
        return b != BlockType.AIR &&
            b != BlockType.WATER &&
            b != BlockType.WATERFALL &&
            b != BlockType.LAVA &&
            b != BlockType.TORCH &&
            !b.crossPlant
    }

    fun isNearWater(wx: Int, wy: Int, wz: Int): Boolean {
        for (dz in -5..5 step 2) {
            for (dx in -5..5 step 2) {
                val b0 = getBlock(wx + dx, wy.coerceIn(1, CHUNK_HEIGHT - 2), wz + dz)
                val bSea = getBlock(wx + dx, SEA_LEVEL, wz + dz)
                val bBelow = getBlock(wx + dx, (wy - 2).coerceAtLeast(1), wz + dz)
                if (isWaterType(b0) || isWaterType(bSea) || isWaterType(bBelow)) {
                    return true
                }
            }
        }
        return false
    }

    fun isNearLava(wx: Int, wy: Int, wz: Int): Boolean {
        for (dz in -6..6 step 3) {
            for (dx in -6..6 step 3) {
                for (dy in -3..3 step 3) {
                    val cy = (wy + dy).coerceIn(1, CHUNK_HEIGHT - 2)
                    if (getBlock(wx + dx, cy, wz + dz) == BlockType.LAVA) {
                        return true
                    }
                }
            }
        }
        return false
    }

    fun findSpawnY(wx: Int, wz: Int): Float {
        for (y in CHUNK_HEIGHT - 2 downTo 1) {
            val b = getBlock(wx, y, wz)
            if (b != BlockType.AIR && b != BlockType.WATER && b != BlockType.WATERFALL && b != BlockType.LAVA && !b.crossPlant && b != BlockType.TORCH) {
                return (y + 1.005f)
            }
        }
        return (SEA_LEVEL + 3.005f)
    }

    /**
     * Returns the exact top Y coordinate of the water surface at (wx, wz) if water exists near [nearY],
     * or null if no water column is found there. Used for smooth sitting/floating right on top of water.
     */
    fun waterSurfaceYAt(wx: Int, wz: Int, nearY: Int = SEA_LEVEL): Float? {
        val topCheck = (nearY + 4).coerceAtMost(CHUNK_HEIGHT - 2)
        val botCheck = (nearY - 4).coerceAtLeast(1)
        for (y in topCheck downTo botCheck) {
            val b = getBlock(wx, y, wz)
            if (isWaterType(b)) {
                return y + 0.88f
            }
        }
        return null
    }

    /**
     * Samples top-down RGB colors around (centerX, centerZ) for the live World Minimap.
     */
    fun sampleMinimapColors(centerX: Int, centerZ: Int, gridSize: Int = 25, step: Int = 2): IntArray {
        val colors = IntArray(gridSize * gridSize)
        val half = gridSize / 2
        for (gz in 0 until gridSize) {
            val wz = centerZ + (gz - half) * step
            for (gx in 0 until gridSize) {
                val wx = centerX + (gx - half) * step
                var topBlock = BlockType.WATER
                for (y in CHUNK_HEIGHT - 2 downTo 1) {
                    val b = getBlock(wx, y, wz)
                    if (b != BlockType.AIR) {
                        topBlock = b
                        break
                    }
                }
                colors[gz * gridSize + gx] = when (topBlock) {
                    BlockType.WATER -> 0xFF1D88D6.toInt()
                    BlockType.WATERFALL -> 0xFF7BE6FF.toInt()
                    BlockType.GRASS, BlockType.TALL_GRASS -> 0xFF45AD39.toInt()
                    BlockType.LEAVES -> 0xFF256E29.toInt()
                    BlockType.FLOWER_RED -> 0xFFFF3D60.toInt()
                    BlockType.FLOWER_GOLD -> 0xFFFFC928.toInt()
                    BlockType.FLOWER_BLUE -> 0xFF42D4FF.toInt()
                    BlockType.SAND, BlockType.SANDSTONE -> 0xFFE3CE8A.toInt()
                    BlockType.SNOW, BlockType.ICE -> 0xFFEBF7FC.toInt()
                    BlockType.LAVA -> 0xFFFF5E1A.toInt()
                    BlockType.OBSIDIAN -> 0xFF332054.toInt()
                    BlockType.GOLD_ORE -> 0xFFFFD53D.toInt()
                    BlockType.BRICK -> 0xFFB85644.toInt()
                    else -> 0xFF838C91.toInt()
                }
            }
        }
        return colors
    }

    fun setBlock(wx: Int, wy: Int, wz: Int, newBlock: BlockType, recordUndo: Boolean = true): BlockType {
        if (wy !in 0 until CHUNK_HEIGHT) return BlockType.AIR
        synchronized(lock) {
            val coord = chunkCoordOf(wx, wz)
            val data = ensureChunkLocked(coord)
            val lx = Math.floorMod(wx, CHUNK_SIZE)
            val lz = Math.floorMod(wz, CHUNK_SIZE)
            val idx = localIndex(lx, wy, lz)
            val prev = BlockType.fromId(data[idx].toInt() and 0xFF)
            if (prev == newBlock) return prev

            data[idx] = newBlock.id.toByte()
            blockDeltas[coordKey(wx, wy, wz)] = newBlock.id.toLong()
            dirtyChunks.add(coord)

            if (lx == 0) markDirtyIfLoadedLocked(ChunkCoord(coord.cx - 1, coord.cz))
            if (lx == CHUNK_SIZE - 1) markDirtyIfLoadedLocked(ChunkCoord(coord.cx + 1, coord.cz))
            if (lz == 0) markDirtyIfLoadedLocked(ChunkCoord(coord.cx, coord.cz - 1))
            if (lz == CHUNK_SIZE - 1) markDirtyIfLoadedLocked(ChunkCoord(coord.cx, coord.cz + 1))

            if (recordUndo) {
                if (undoStack.size >= 60) undoStack.removeFirst()
                undoStack.addLast(VoxelEditRecord(wx, wy, wz, prev, newBlock))
            }

            // Trigger real water & lava channel physics!
            when {
                isWaterType(newBlock) -> {
                    enqueueWaterLocked(wx, wy, wz, spread = 7)
                    checkInfiniteWater2x2Locked(wx, wy, wz)
                }
                newBlock == BlockType.LAVA -> {
                    enqueueLavaLocked(wx, wy, wz, spread = 4)
                }
                newBlock == BlockType.AIR -> {
                    // Carving a trench next to water or lava makes it rush into the newly dug path!
                    checkInfiniteWaterRefillLocked(wx, wy, wz)
                    wakeAdjacentFluidsLocked(wx, wy, wz)
                }
            }
            return prev
        }
    }

    /**
     * Advances real-time Water & Lava flow physics so digging channels guides water/lava
     * dynamically across the map, forming waterfalls, lava streams, and Obsidian when they collide!
     */
    fun stepFluidPhysics(maxWaterUpdates: Int = 20, maxLavaUpdates: Int = 8): FluidStepEvents {
        var waterFlowed = 0
        var lavaFlowed = 0
        var obsidianFormed = 0
        var sizzlePos: Triple<Float, Float, Float>? = null

        synchronized(lock) {
            fluidTickCounter++

            // 1. Water channel & waterfall physics (fast, responsive flow)
            var waterOps = 0
            while (waterFlowQueue.isNotEmpty() && waterOps < maxWaterUpdates) {
                val cell = waterFlowQueue.removeFirst()
                waterOps++
                val current = getBlockRawLocked(cell.x, cell.y, cell.z)
                if (!isWaterType(current)) continue

                // Check downward first (gravity waterfall)
                val belowY = cell.y - 1
                if (belowY >= 1) {
                    val below = getBlockRawLocked(cell.x, belowY, cell.z)
                    if (below == BlockType.LAVA) {
                        setBlockRawLocked(cell.x, belowY, cell.z, BlockType.OBSIDIAN)
                        obsidianFormed++
                        sizzlePos = Triple(cell.x + 0.5f, belowY + 0.8f, cell.z + 0.5f)
                        continue
                    } else if (isFluidReplaceable(below)) {
                        setBlockRawLocked(cell.x, belowY, cell.z, BlockType.WATERFALL)
                        enqueueWaterLocked(cell.x, belowY, cell.z, spread = 7)
                        waterFlowed++
                        continue
                    }
                }

                // Spread horizontally along dug channels!
                if (cell.spreadLeft > 0) {
                    val dirs = CARDINAL_DIRS
                    for ((dx, dz) in dirs) {
                        val nx = cell.x + dx
                        val nz = cell.z + dz
                        val neighbor = getBlockRawLocked(nx, cell.y, nz)
                        if (neighbor == BlockType.LAVA) {
                            setBlockRawLocked(nx, cell.y, nz, BlockType.COBBLESTONE)
                            obsidianFormed++
                            sizzlePos = Triple(nx + 0.5f, cell.y + 0.8f, nz + 0.5f)
                        } else if (isFluidReplaceable(neighbor)) {
                            setBlockRawLocked(nx, cell.y, nz, BlockType.WATER)
                            enqueueWaterLocked(nx, cell.y, nz, spread = cell.spreadLeft - 1)
                            waterFlowed++
                        }
                    }
                }
            }

            // 2. Molten Lava physics (thicker, slower volcanic flow every 2nd fluid tick)
            if (fluidTickCounter % 2 == 0) {
                var lavaOps = 0
                while (lavaFlowQueue.isNotEmpty() && lavaOps < maxLavaUpdates) {
                    val cell = lavaFlowQueue.removeFirst()
                    lavaOps++
                    val current = getBlockRawLocked(cell.x, cell.y, cell.z)
                    if (current != BlockType.LAVA) continue

                    val belowY = cell.y - 1
                    if (belowY >= 1) {
                        val below = getBlockRawLocked(cell.x, belowY, cell.z)
                        if (isWaterType(below)) {
                            setBlockRawLocked(cell.x, belowY, cell.z, BlockType.OBSIDIAN)
                            obsidianFormed++
                            sizzlePos = Triple(cell.x + 0.5f, belowY + 0.8f, cell.z + 0.5f)
                            continue
                        } else if (isFluidReplaceable(below)) {
                            setBlockRawLocked(cell.x, belowY, cell.z, BlockType.LAVA)
                            enqueueLavaLocked(cell.x, belowY, cell.z, spread = 4)
                            lavaFlowed++
                            continue
                        }
                    }

                    if (cell.spreadLeft > 0) {
                        for ((dx, dz) in CARDINAL_DIRS) {
                            val nx = cell.x + dx
                            val nz = cell.z + dz
                            val neighbor = getBlockRawLocked(nx, cell.y, nz)
                            if (isWaterType(neighbor)) {
                                setBlockRawLocked(nx, cell.y, nz, BlockType.COBBLESTONE)
                                obsidianFormed++
                                sizzlePos = Triple(nx + 0.5f, cell.y + 0.8f, nz + 0.5f)
                            } else if (isFluidReplaceable(neighbor)) {
                                setBlockRawLocked(nx, cell.y, nz, BlockType.LAVA)
                                enqueueLavaLocked(nx, cell.y, nz, spread = cell.spreadLeft - 1)
                                lavaFlowed++
                            }
                        }
                    }
                }
            }
        }

        return FluidStepEvents(waterFlowed, lavaFlowed, obsidianFormed, sizzlePos)
    }

    private fun isWaterType(b: BlockType): Boolean = b == BlockType.WATER || b == BlockType.WATERFALL

    private fun isFluidReplaceable(b: BlockType): Boolean =
        b == BlockType.AIR || b.crossPlant || b == BlockType.TORCH

    private fun enqueueWaterLocked(x: Int, y: Int, z: Int, spread: Int) {
        if (waterFlowQueue.size < 450 && y in 1 until CHUNK_HEIGHT) {
            waterFlowQueue.addLast(ActiveFluidCell(x, y, z, isLava = false, spreadLeft = spread))
        }
    }

    private fun enqueueLavaLocked(x: Int, y: Int, z: Int, spread: Int) {
        if (lavaFlowQueue.size < 250 && y in 1 until CHUNK_HEIGHT) {
            lavaFlowQueue.addLast(ActiveFluidCell(x, y, z, isLava = true, spreadLeft = spread))
        }
    }

    private fun wakeAdjacentFluidsLocked(wx: Int, wy: Int, wz: Int) {
        val neighbors = arrayOf(
            Triple(wx, wy + 1, wz),
            Triple(wx + 1, wy, wz),
            Triple(wx - 1, wy, wz),
            Triple(wx, wy, wz + 1),
            Triple(wx, wy, wz - 1)
        )
        for ((nx, ny, nz) in neighbors) {
            if (ny !in 1 until CHUNK_HEIGHT) continue
            val b = getBlockRawLocked(nx, ny, nz)
            if (isWaterType(b)) {
                enqueueWaterLocked(nx, ny, nz, spread = 7)
            } else if (b == BlockType.LAVA) {
                enqueueLavaLocked(nx, ny, nz, spread = 4)
            }
        }
    }

    private fun checkInfiniteWaterRefillLocked(wx: Int, wy: Int, wz: Int) {
        if (wy !in 1 until CHUNK_HEIGHT) return
        val adjWater = CARDINAL_DIRS.count { (dx, dz) -> isWaterType(getBlockRawLocked(wx + dx, wy, wz + dz)) }
        val aboveWater = isWaterType(getBlockRawLocked(wx, wy + 1, wz))
        if (adjWater >= 2 || aboveWater) {
            setBlockRawLocked(wx, wy, wz, if (aboveWater) BlockType.WATERFALL else BlockType.WATER)
            enqueueWaterLocked(wx, wy, wz, spread = 7)
        }
    }

    private fun checkInfiniteWater2x2Locked(wx: Int, wy: Int, wz: Int) {
        for ((dx, dz) in CARDINAL_DIRS) {
            val nx = wx + dx
            val nz = wz + dz
            if (getBlockRawLocked(nx, wy, nz) == BlockType.AIR) {
                val neighborWaterCount = CARDINAL_DIRS.count { (ddx, ddz) ->
                    isWaterType(getBlockRawLocked(nx + ddx, wy, nz + ddz))
                }
                if (neighborWaterCount >= 2) {
                    setBlockRawLocked(nx, wy, nz, BlockType.WATER)
                    enqueueWaterLocked(nx, wy, nz, spread = 6)
                }
            }
        }
    }

    private fun setBlockRawLocked(wx: Int, wy: Int, wz: Int, block: BlockType) {
        if (wy !in 0 until CHUNK_HEIGHT) return
        val coord = chunkCoordOf(wx, wz)
        val data = ensureChunkLocked(coord)
        val lx = Math.floorMod(wx, CHUNK_SIZE)
        val lz = Math.floorMod(wz, CHUNK_SIZE)
        data[localIndex(lx, wy, lz)] = block.id.toByte()
        blockDeltas[coordKey(wx, wy, wz)] = block.id.toLong()
        dirtyChunks.add(coord)
        if (lx == 0) markDirtyIfLoadedLocked(ChunkCoord(coord.cx - 1, coord.cz))
        if (lx == CHUNK_SIZE - 1) markDirtyIfLoadedLocked(ChunkCoord(coord.cx + 1, coord.cz))
        if (lz == 0) markDirtyIfLoadedLocked(ChunkCoord(coord.cx, coord.cz - 1))
        if (lz == CHUNK_SIZE - 1) markDirtyIfLoadedLocked(ChunkCoord(coord.cx, coord.cz + 1))
    }

    /**
     * Allows autonomous AI characters (Majdur / Pujari) or instant Blueprint Kits to construct
     * real 3D structures directly into the voxel world!
     */
    fun buildMiniStructure(originX: Int, originZ: Int, isMandir: Boolean) {
        val baseY = floor(findSpawnY(originX, originZ)).toInt().coerceIn(SEA_LEVEL, CHUNK_HEIGHT - 12)
        val wallBlock = if (isMandir) BlockType.SANDSTONE else BlockType.BRICK
        val trimBlock = if (isMandir) BlockType.GOLD_ORE else BlockType.PLANKS
        val radius = 2

        for (dx in -radius..radius) {
            for (dz in -radius..radius) {
                // Solid foundation floor
                setBlock(originX + dx, baseY - 1, originZ + dz, BlockType.COBBLESTONE, recordUndo = false)
                // Clear interior
                for (dy in 0..4) {
                    setBlock(originX + dx, baseY + dy, originZ + dz, BlockType.AIR, recordUndo = false)
                }
                val isEdge = abs(dx) == radius || abs(dz) == radius
                val isDoor = dx == 0 && dz == radius
                if (isEdge && !isDoor) {
                    for (dy in 0..2) {
                        val b = if (dy == 1 && (dx == 0 || dz == 0)) BlockType.GLASS else wallBlock
                        setBlock(originX + dx, baseY + dy, originZ + dz, b, recordUndo = false)
                    }
                }
                // Roof layer
                setBlock(originX + dx, baseY + 3, originZ + dz, trimBlock, recordUndo = false)
            }
        }
        if (isMandir) {
            // Stepped Golden Mandir Shikhara spire + glowing courtyard torches
            for (dx in -1..1) {
                for (dz in -1..1) {
                    setBlock(originX + dx, baseY + 4, originZ + dz, BlockType.SANDSTONE, recordUndo = false)
                }
            }
            setBlock(originX, baseY + 5, originZ, BlockType.GOLD_ORE, recordUndo = false)
            setBlock(originX, baseY + 6, originZ, BlockType.TORCH, recordUndo = false)
            setBlock(originX - 1, baseY, originZ + radius + 1, BlockType.FLOWER_GOLD, recordUndo = false)
            setBlock(originX + 1, baseY, originZ + radius + 1, BlockType.FLOWER_RED, recordUndo = false)
        } else {
            setBlock(originX, baseY + 1, originZ, BlockType.TORCH, recordUndo = false)
        }
    }

    /**
     * Plants a full 3D voxel tree (wood trunk + layered leafy canopy) live in real time.
     */
    fun plantTreeAt(originX: Int, originZ: Int, tall: Boolean = false) {
        val baseY = floor(findSpawnY(originX, originZ)).toInt().coerceIn(SEA_LEVEL, CHUNK_HEIGHT - 10)
        val trunkHeight = if (tall) 6 else 4
        setBlock(originX, baseY - 1, originZ, BlockType.DIRT, recordUndo = false)
        for (dy in 0 until trunkHeight) {
            setBlock(originX, baseY + dy, originZ, BlockType.WOOD_LOG, recordUndo = false)
        }
        val canopyBase = baseY + trunkHeight - 1
        for (dy in 0..2) {
            val r = if (dy == 2) 1 else 2
            for (dx in -r..r) {
                for (dz in -r..r) {
                    if (abs(dx) == r && abs(dz) == r && dy != 0) continue
                    val tx = originX + dx
                    val ty = canopyBase + dy
                    val tz = originZ + dz
                    if (ty < CHUNK_HEIGHT - 1 && getBlock(tx, ty, tz) == BlockType.AIR) {
                        setBlock(tx, ty, tz, BlockType.LEAVES, recordUndo = false)
                    }
                }
            }
        }
    }

    /**
     * Plants a lush grove of 3D trees around a center point (used by Admin Map Creator & AI workers).
     */
    fun plantForestAround(centerX: Int, centerZ: Int, count: Int = 14) {
        val rng = Random(System.nanoTime())
        repeat(count) { i ->
            val angle = (i.toFloat() / count) * 6.283f + rng.nextFloat() * 0.45f
            val dist = 5f + rng.nextFloat() * 14f
            val tx = centerX + floor(cos(angle) * dist).toInt()
            val tz = centerZ + floor(sin(angle) * dist).toInt()
            plantTreeAt(tx, tz, tall = (i % 3 == 0))
        }
    }

    /**
     * Carves a real flowing water lake, river, or cascading mountain waterfall with live fluid physics!
     */
    fun createWaterSpringOrRiver(centerX: Int, centerZ: Int, radius: Int = 3, waterfall: Boolean = false) {
        val baseY = floor(findSpawnY(centerX, centerZ)).toInt().coerceIn(SEA_LEVEL, CHUNK_HEIGHT - 12)
        if (waterfall) {
            // Build a rocky cliff spring pouring real animated flowing water down into a basin!
            val cliffTop = (baseY + 7).coerceAtMost(CHUNK_HEIGHT - 3)
            for (dx in -2..2) {
                for (dz in -3..-1) {
                    for (y in baseY..cliffTop) {
                        setBlock(centerX + dx, y, centerZ + dz, BlockType.STONE, recordUndo = false)
                    }
                }
            }
            // Carve splash basin at bottom
            for (dx in -3..3) {
                for (dz in 0..4) {
                    setBlock(centerX + dx, baseY - 2, centerZ + dz, BlockType.SAND, recordUndo = false)
                    setBlock(centerX + dx, baseY - 1, centerZ + dz, BlockType.WATER, recordUndo = false)
                    for (dy in 0..2) {
                        setBlock(centerX + dx, baseY + dy, centerZ + dz, BlockType.AIR, recordUndo = false)
                    }
                }
            }
            // Pour live waterfall from top spring
            for (y in baseY..cliffTop) {
                setBlock(centerX, y, centerZ - 1, BlockType.WATERFALL, recordUndo = false)
                setBlock(centerX, y, centerZ, BlockType.WATER, recordUndo = false)
            }
            setBlock(centerX, cliffTop, centerZ - 2, BlockType.WATER, recordUndo = false)
        } else {
            for (dx in -radius..radius) {
                for (dz in -radius..radius) {
                    if (dx * dx + dz * dz <= radius * radius + 1) {
                        setBlock(centerX + dx, baseY - 2, centerZ + dz, BlockType.SAND, recordUndo = false)
                        setBlock(centerX + dx, baseY - 1, centerZ + dz, BlockType.WATER, recordUndo = false)
                        setBlock(centerX + dx, baseY, centerZ + dz, BlockType.AIR, recordUndo = false)
                        setBlock(centerX + dx, baseY + 1, centerZ + dz, BlockType.AIR, recordUndo = false)
                    }
                }
            }
        }
    }

    /**
     * Digs a live flowing water channel straight to the target point ("यहाँ पानी ले आओ")!
     */
    fun bringWaterChannelTo(fromX: Int, fromZ: Int, toX: Int, toZ: Int) {
        val steps = maxOf(abs(toX - fromX), abs(toZ - fromZ), 8).coerceAtMost(24)
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val wx = floor(fromX + (toX - fromX) * t).toInt()
            val wz = floor(fromZ + (toZ - fromZ) * t).toInt()
            val surfaceY = floor(findSpawnY(wx, wz)).toInt().coerceIn(SEA_LEVEL, CHUNK_HEIGHT - 5)
            for (ox in -1..1) {
                setBlock(wx + ox, surfaceY - 2, wz, BlockType.SAND, recordUndo = false)
                setBlock(wx + ox, surfaceY - 1, wz, BlockType.WATER, recordUndo = false)
                setBlock(wx + ox, surfaceY, wz, BlockType.AIR, recordUndo = false)
            }
        }
    }

    /**
     * Admin Real-Time Live Map Builder: constructs instant major structures live in the 3D world!
     */
    fun buildAdminLiveStructure(originX: Int, originZ: Int, structureType: String) {
        val baseY = floor(findSpawnY(originX, originZ)).toInt().coerceIn(SEA_LEVEL, CHUNK_HEIGHT - 14)
        when (structureType) {
            "PALACE" -> {
                val r = 4
                for (dx in -r..r) {
                    for (dz in -r..r) {
                        setBlock(originX + dx, baseY - 1, originZ + dz, BlockType.SANDSTONE, recordUndo = false)
                        for (dy in 0..6) setBlock(originX + dx, baseY + dy, originZ + dz, BlockType.AIR, recordUndo = false)
                        val isCorner = abs(dx) == r && abs(dz) == r
                        val isWall = abs(dx) == r || abs(dz) == r
                        val isGate = abs(dx) <= 1 && dz == r
                        if (isCorner) {
                            for (dy in 0..5) setBlock(originX + dx, baseY + dy, originZ + dz, BlockType.GOLD_ORE, recordUndo = false)
                            setBlock(originX + dx, baseY + 6, originZ + dz, BlockType.TORCH, recordUndo = false)
                        } else if (isWall && !isGate) {
                            for (dy in 0..3) {
                                val b = if (dy == 1 && (dx % 2 == 0 || dz % 2 == 0)) BlockType.GLASS else BlockType.SANDSTONE
                                setBlock(originX + dx, baseY + dy, originZ + dz, b, recordUndo = false)
                            }
                        }
                        setBlock(originX + dx, baseY + 4, originZ + dz, BlockType.GOLD_ORE, recordUndo = false)
                    }
                }
            }
            "FORTRESS" -> {
                val r = 5
                for (dx in -r..r) {
                    for (dz in -r..r) {
                        setBlock(originX + dx, baseY - 1, originZ + dz, BlockType.OBSIDIAN, recordUndo = false)
                        val isEdge = abs(dx) == r || abs(dz) == r
                        val isGate = abs(dx) <= 1 && dz == r
                        if (isEdge && !isGate) {
                            for (dy in 0..4) {
                                val crenellation = dy < 4 || ((dx + dz) and 1) == 0
                                if (crenellation) {
                                    setBlock(originX + dx, baseY + dy, originZ + dz, BlockType.COBBLESTONE, recordUndo = false)
                                }
                            }
                            if (abs(dx) == r && abs(dz) == r) {
                                setBlock(originX + dx, baseY + 5, originZ + dz, BlockType.TORCH, recordUndo = false)
                            }
                        }
                    }
                }
            }
            "VOLCANO" -> {
                for (dx in -5..5) {
                    for (dz in -5..5) {
                        val dist = hypot(dx.toFloat(), dz.toFloat())
                        if (dist <= 5.2f) {
                            val coneH = ((5.5f - dist) * 1.9f).toInt().coerceAtLeast(1)
                            val topY = (baseY + coneH).coerceAtMost(CHUNK_HEIGHT - 3)
                            for (y in baseY..topY) {
                                val isCrater = dist < 1.8f && y >= topY - 2
                                val b = if (isCrater) BlockType.LAVA else if (y == topY && dist < 2.6f) BlockType.OBSIDIAN else BlockType.STONE
                                setBlock(originX + dx, y, originZ + dz, b, recordUndo = false)
                            }
                        }
                    }
                }
                setBlock(originX, (baseY + 9).coerceAtMost(CHUNK_HEIGHT - 2), originZ, BlockType.LAVA, recordUndo = false)
            }
            "GOLD_MINE" -> {
                for (dx in -2..2) {
                    for (dz in -2..2) {
                        setBlock(originX + dx, baseY - 1, originZ + dz, BlockType.GOLD_ORE, recordUndo = false)
                        if (abs(dx) == 2 || abs(dz) == 2) {
                            setBlock(originX + dx, baseY, originZ + dz, BlockType.DIAMOND_ORE, recordUndo = false)
                        }
                    }
                }
                setBlock(originX, baseY, originZ, BlockType.TORCH, recordUndo = false)
            }
            "VILLAGE" -> {
                buildMiniStructure(originX - 5, originZ, isMandir = false)
                buildMiniStructure(originX + 5, originZ, isMandir = false)
                buildMiniStructure(originX, originZ - 6, isMandir = true)
            }
            else -> buildMiniStructure(originX, originZ, isMandir = false)
        }
    }

    /**
     * Constructs realistic multi-story Real Estate architecture (Luxury Villa + Pool,
     * 4-Story Apartment Tower, Shopping Mall, Walled Plot, or Street-Lamp Boulevard Road)
     * directly in the live world!
     */
    fun buildRealEstateStructure(originX: Int, originZ: Int, structureType: String) {
        val baseY = floor(findSpawnY(originX, originZ)).toInt().coerceIn(SEA_LEVEL + 1, CHUNK_HEIGHT - 22)
        RealEstateArchitect.buildRealEstateLive(
            originX = originX,
            originZ = originZ,
            baseY = baseY,
            structureType = structureType
        ) { wx, wy, wz, block ->
            setBlock(wx, wy, wz, block, recordUndo = false)
        }
    }

    fun canUndo(): Boolean = synchronized(lock) { undoStack.isNotEmpty() }

    fun undo(): VoxelEditRecord? {
        val last = synchronized(lock) { undoStack.removeLastOrNull() } ?: return null
        setBlock(last.x, last.y, last.z, last.previous, recordUndo = false)
        return last
    }

    fun exportDeltas(): Map<String, Long> = synchronized(lock) { HashMap(blockDeltas) }

    /**
     * Generates surrounding chunk terrain data asynchronously on a background thread
     * so crossing chunk borders NEVER causes frame stutters on the OpenGL render thread!
     */
    fun prewarmChunksAround(centerCx: Int, centerCz: Int, radius: Int) {
        val coords = ArrayList<ChunkCoord>((radius * 2 + 1) * (radius * 2 + 1))
        for (dx in -radius..radius) {
            for (dz in -radius..radius) {
                coords.add(ChunkCoord(centerCx + dx, centerCz + dz))
            }
        }
        coords.sortBy { (it.cx - centerCx) * (it.cx - centerCx) + (it.cz - centerCz) * (it.cz - centerCz) }
        for (c in coords) {
            val alreadyLoaded = synchronized(lock) { chunks.containsKey(c) }
            if (!alreadyLoaded) {
                val generated = generateChunkTerrain(c.cx, c.cz)
                synchronized(lock) {
                    if (!chunks.containsKey(c)) {
                        applyDeltasToChunkLocked(c, generated)
                        chunks[c] = generated
                        dirtyChunks.add(c)
                    }
                }
            }
        }
    }

    private fun applyDeltasToChunkLocked(coord: ChunkCoord, data: ByteArray) {
        val baseX = coord.cx * CHUNK_SIZE
        val baseZ = coord.cz * CHUNK_SIZE
        for ((key, blockIdLong) in blockDeltas) {
            val (wx, wy, wz) = parseKey(key) ?: continue
            if (wx in baseX until (baseX + CHUNK_SIZE) &&
                wz in baseZ until (baseZ + CHUNK_SIZE) &&
                wy in 0 until CHUNK_HEIGHT
            ) {
                val lx = wx - baseX
                val lz = wz - baseZ
                data[localIndex(lx, wy, lz)] = blockIdLong.toInt().toByte()
            }
        }
    }

    fun prepareVisibleMeshes(
        playerX: Float,
        playerZ: Float,
        renderDistance: Int,
        maxRebuildsPerFrame: Int = 1
    ): List<ChunkMeshData> {
        val centerCx = Math.floorDiv(floor(playerX).toInt(), CHUNK_SIZE)
        val centerCz = Math.floorDiv(floor(playerZ).toInt(), CHUNK_SIZE)
        val needed = ArrayList<ChunkCoord>((renderDistance * 2 + 1) * (renderDistance * 2 + 1))

        for (dx in -renderDistance..renderDistance) {
            for (dz in -renderDistance..renderDistance) {
                needed.add(ChunkCoord(centerCx + dx, centerCz + dz))
            }
        }
        needed.sortBy { (it.cx - centerCx) * (it.cx - centerCx) + (it.cz - centerCz) * (it.cz - centerCz) }

        synchronized(lock) {
            // Budget new terrain generation to at most 1 chunk per frame on GL thread (always ensure player's own chunk first!)
            var generatedThisFrame = 0
            for (c in needed) {
                if (!chunks.containsKey(c)) {
                    val isImmediate = abs(c.cx - centerCx) <= 1 && abs(c.cz - centerCz) <= 1
                    if (isImmediate || generatedThisFrame < 1) {
                        ensureChunkLocked(c)
                        generatedThisFrame++
                    }
                }
            }

            var rebuilt = 0
            for (c in needed) {
                if (!chunks.containsKey(c)) continue
                if (dirtyChunks.contains(c) || !chunkMeshes.containsKey(c)) {
                    chunkMeshes[c] = buildChunkMeshLocked(c)
                    dirtyChunks.remove(c)
                    rebuilt++
                    if (rebuilt >= maxRebuildsPerFrame) break
                }
            }

            val pruneDist = renderDistance + 3
            val iter = chunkMeshes.entries.iterator()
            while (iter.hasNext()) {
                val entry = iter.next()
                if (abs(entry.key.cx - centerCx) > pruneDist || abs(entry.key.cz - centerCz) > pruneDist) {
                    iter.remove()
                }
            }

            return needed.mapNotNull { chunkMeshes[it] }
        }
    }

    private fun getBlockRawLocked(wx: Int, wy: Int, wz: Int): BlockType {
        if (wy !in 0 until CHUNK_HEIGHT) return BlockType.AIR
        val c = chunkCoordOf(wx, wz)
        val data = chunks[c] ?: return BlockType.AIR
        val lx = Math.floorMod(wx, CHUNK_SIZE)
        val lz = Math.floorMod(wz, CHUNK_SIZE)
        return BlockType.fromId(data[localIndex(lx, wy, lz)].toInt() and 0xFF)
    }

    private fun shouldRenderFace(block: BlockType, neighbor: BlockType): Boolean {
        if (neighbor == BlockType.AIR) return true
        if (block == BlockType.LAVA) return neighbor != BlockType.LAVA && (neighbor.translucent || neighbor == BlockType.AIR)
        if (block.translucent) {
            return neighbor != block && neighbor.translucent
        }
        return neighbor.translucent || neighbor == BlockType.LAVA
    }

    private fun buildChunkMeshLocked(coord: ChunkCoord): ChunkMeshData {
        val data = chunks[coord] ?: return ChunkMeshData(null, 0, null, 0, null, 0, null, 0)
        val opaqueFloats = FloatList(8192)
        val transFloats = FloatList(1024)
        val waterFloats = FloatList(1536)
        val lavaFloats = FloatList(1024)

        val baseX = coord.cx * CHUNK_SIZE
        val baseZ = coord.cz * CHUNK_SIZE

        for (y in 0 until CHUNK_HEIGHT) {
            for (lz in 0 until CHUNK_SIZE) {
                val wz = baseZ + lz
                for (lx in 0 until CHUNK_SIZE) {
                    val id = data[localIndex(lx, y, lz)].toInt() and 0xFF
                    if (id == 0) continue
                    val block = BlockType.fromId(id)
                    val wx = baseX + lx
                    val out = when {
                        block == BlockType.WATER || block == BlockType.WATERFALL -> waterFloats
                        block == BlockType.LAVA -> lavaFloats
                        block.translucent -> transFloats
                        else -> opaqueFloats
                    }

                    val x0 = wx.toFloat()
                    val y0 = y.toFloat()
                    val z0 = wz.toFloat()
                    val x1 = x0 + 1f
                    val y1 = when (block) {
                        BlockType.WATER -> y0 + 0.88f
                        BlockType.LAVA -> y0 + 0.90f
                        else -> y0 + 1f
                    }
                    val z1 = z0 + 1f

                    if (block.crossPlant) {
                        val uv = VoxelTextureAtlas.uvFor(block, 0)
                        val pad = 0.12f
                        emitQuad(out, x0 + pad, y0, z0 + pad, x1 - pad, y0, z1 - pad, x1 - pad, y1, z1 - pad, x0 + pad, y1, z0 + pad, uv, 0.96f)
                        emitQuad(out, x1 - pad, y0, z0 + pad, x0 + pad, y0, z1 - pad, x0 + pad, y1, z1 - pad, x1 - pad, y1, z0 + pad, uv, 0.90f)
                        continue
                    }

                    // +Y Top face
                    if (shouldRenderFace(block, getBlockRawLocked(wx, y + 1, wz))) {
                        val uv = VoxelTextureAtlas.uvFor(block, 0)
                        emitQuad(out, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, uv, 1.0f)
                    }
                    // -Y Bottom face
                    if (y > 0 && shouldRenderFace(block, getBlockRawLocked(wx, y - 1, wz))) {
                        val uv = VoxelTextureAtlas.uvFor(block, 1)
                        emitQuad(out, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, uv, 0.55f)
                    }
                    // +X East face
                    if (shouldRenderFace(block, getBlockRawLocked(wx + 1, y, wz))) {
                        val uv = VoxelTextureAtlas.uvFor(block, 2)
                        emitQuad(out, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, uv, 0.82f)
                    }
                    // -X West face
                    if (shouldRenderFace(block, getBlockRawLocked(wx - 1, y, wz))) {
                        val uv = VoxelTextureAtlas.uvFor(block, 3)
                        emitQuad(out, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, uv, 0.78f)
                    }
                    // +Z South face
                    if (shouldRenderFace(block, getBlockRawLocked(wx, y, wz + 1))) {
                        val uv = VoxelTextureAtlas.uvFor(block, 4)
                        emitQuad(out, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, uv, 0.70f)
                    }
                    // -Z North face
                    if (shouldRenderFace(block, getBlockRawLocked(wx, y, wz - 1))) {
                        val uv = VoxelTextureAtlas.uvFor(block, 5)
                        emitQuad(out, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, uv, 0.68f)
                    }
                }
            }
        }

        return ChunkMeshData(
            opaqueBuffer = opaqueFloats.toDirectBuffer(),
            opaqueVertexCount = opaqueFloats.size / 6,
            translucentBuffer = transFloats.toDirectBuffer(),
            translucentVertexCount = transFloats.size / 6,
            waterBuffer = waterFloats.toDirectBuffer(),
            waterVertexCount = waterFloats.size / 6,
            lavaBuffer = lavaFloats.toDirectBuffer(),
            lavaVertexCount = lavaFloats.size / 6
        )
    }

    private fun emitQuad(
        out: FloatList,
        ax: Float, ay: Float, az: Float,
        bx: Float, by: Float, bz: Float,
        cx: Float, cy: Float, cz: Float,
        dx: Float, dy: Float, dz: Float,
        uv: VoxelTextureAtlas.TileUV,
        shade: Float
    ) {
        out.addVertex(ax, ay, az, uv.u0, uv.v1, shade)
        out.addVertex(bx, by, bz, uv.u1, uv.v1, shade)
        out.addVertex(cx, cy, cz, uv.u1, uv.v0, shade)
        out.addVertex(ax, ay, az, uv.u0, uv.v1, shade)
        out.addVertex(cx, cy, cz, uv.u1, uv.v0, shade)
        out.addVertex(dx, dy, dz, uv.u0, uv.v0, shade)
    }

    // --- GRAND MOUNTAIN TERRAIN, VOLCANOES, UNDERGROUND WATER & LAVA, MANDIR & PALACE ---

    private fun generateChunkTerrain(cx: Int, cz: Int): ByteArray {
        val data = ByteArray(CHUNK_SIZE * CHUNK_HEIGHT * CHUNK_SIZE)
        val baseX = cx * CHUNK_SIZE
        val baseZ = cz * CHUNK_SIZE

        val heights = IntArray(CHUNK_SIZE * CHUNK_SIZE)

        for (lz in 0 until CHUNK_SIZE) {
            val wz = baseZ + lz
            for (lx in 0 until CHUNK_SIZE) {
                val wx = baseX + lx

                val n1 = valueNoise2D(wx * 0.032, wz * 0.032, seed)
                val n2 = valueNoise2D(wx * 0.085, wz * 0.085, seed + 1337L) * 0.45
                val n3 = valueNoise2D(wx * 0.20, wz * 0.20, seed + 7919L) * 0.16
                val combined = (n1 + n2 + n3) / 1.61

                // Mountain ridge amplification ("बड़े पहाड़")
                val mountainMask = valueNoise2D(wx * 0.015, wz * 0.015, seed + 55101L)
                val mountainBoost = if (mountainMask > 0.58) ((mountainMask - 0.58) * 38.0).toInt() else 0

                // Broad ocean basins ("समुद्र") + winding rivers ("पानी")
                val oceanMask = valueNoise2D(wx * 0.014, wz * 0.014, seed + 22111L)
                val riverBand = abs(valueNoise2D(wx * 0.026, wz * 0.026, seed + 9091L) - 0.5)
                val nearVillage = abs(wx - 8) <= 18 && abs(wz - 8) <= 18
                val isOcean = oceanMask < 0.25 && !nearVillage
                val isRiver = riverBand < 0.040 && (abs(wx - 8) > 10 || abs(wz - 8) > 10)

                // Erupting Volcano ("ज्वालामुखी") near (-34, -30) and repeating every ~140 blocks
                val distMainVolcano = hypot((wx - volcanoCenterX).toDouble(), (wz - volcanoCenterZ).toDouble())
                val isVolcanoCone = distMainVolcano < 17.5

                val baseHeight = when (biome) {
                    BiomeType.EMERALD_VALLEY -> (12.0 + combined * 13.0 + mountainBoost).toInt().coerceIn(9, 39)
                    BiomeType.FLOWER_PARADISE -> (13.0 + combined * 9.5 + mountainBoost * 0.6).toInt().coerceIn(11, 35)
                    BiomeType.DESERT_DUNES -> (13.0 + combined * 11.0 + mountainBoost * 0.7).toInt().coerceIn(10, 34)
                    BiomeType.FROST_PEAKS -> (14.0 + combined * 17.0 + mountainBoost).toInt().coerceIn(10, 41)
                    BiomeType.VOLCANIC_BADLANDS -> (12.0 + combined * 15.0 + mountainBoost).toInt().coerceIn(9, 39)
                    BiomeType.JUNGLE_RIVER -> (13.0 + combined * 12.0 + mountainBoost * 0.85).toInt().coerceIn(9, 38)
                    BiomeType.OCEAN_ISLANDS -> (8.0 + combined * 11.0 + mountainBoost * 0.5).toInt().coerceIn(6, 30)
                    BiomeType.SKY_KINGDOM -> (18.0 + combined * 12.0 + mountainBoost * 0.8).toInt().coerceIn(12, 41)
                }

                // Smooth gentle meadow plateau right around spawn, Mandir & Palace
                val villageFlatDist = hypot((wx - 6).toDouble(), (wz - 8).toDouble())
                val isOceanBiomeWater = biome == BiomeType.OCEAN_ISLANDS && oceanMask < 0.52 && !nearVillage
                var height = when {
                    isVolcanoCone && biome != BiomeType.SKY_KINGDOM -> {
                        val coneH = (36.0 - distMainVolcano * 1.25).toInt()
                        if (distMainVolcano < 4.2) 30 else maxOf(baseHeight, coneH).coerceIn(14, 39)
                    }
                    villageFlatDist < 18.0 -> (SEA_LEVEL + 2 + (combined * 2.2).toInt()).coerceIn(SEA_LEVEL + 1, SEA_LEVEL + 4)
                    isOcean || isOceanBiomeWater -> (SEA_LEVEL - 5 + (combined * 2.5).toInt()).coerceIn(6, SEA_LEVEL - 2)
                    isRiver -> (SEA_LEVEL - 2).coerceAtLeast(8)
                    else -> baseHeight
                }
                height = height.coerceIn(5, CHUNK_HEIGHT - 6)
                heights[lz * CHUNK_SIZE + lx] = height

                // Y=0 Bedrock
                data[localIndex(lx, 0, lz)] = BlockType.BEDROCK.id.toByte()

                // Underground Water Aquifer & Underground Lava Cavern masks!
                val caveNoise = valueNoise2D(wx * 0.065, wz * 0.065, seed + 44019L)
                val lavaCaveNoise = valueNoise2D(wx * 0.075, wz * 0.075, seed + 88177L)
                val hasUndergroundWater = caveNoise > 0.64 && height >= SEA_LEVEL + 1
                val hasUndergroundLava = lavaCaveNoise > 0.68 || distMainVolcano < 10.0

                for (y in 1..height) {
                    val block = when {
                        // Deep underground molten lava lakes & magma channels (Y=1..3)
                        hasUndergroundLava && y in 1..2 -> BlockType.LAVA
                        hasUndergroundLava && y in 3..4 && height > 10 -> BlockType.AIR
                        // Underground water aquifers / subterranean rivers (Y=5..7 water, Y=8..9 cavern air)
                        hasUndergroundWater && y in 5..7 && height > 13 -> BlockType.WATER
                        hasUndergroundWater && y in 8..9 && height > 14 -> BlockType.AIR
                        // Volcano slopes made of Obsidian & Stone
                        isVolcanoCone && biome != BiomeType.SKY_KINGDOM && y >= height - 2 -> if (distMainVolcano < 8.5) BlockType.OBSIDIAN else BlockType.COBBLESTONE
                        y == height -> surfaceBlock(height)
                        y >= height - 2 -> subSurfaceBlock()
                        else -> undergroundOreOrStone(wx, y, wz)
                    }
                    data[localIndex(lx, y, lz)] = block.id.toByte()
                }

                // Sky Kingdom Floating Cloud Platforms overhead!
                if (biome == BiomeType.SKY_KINGDOM) {
                    val skyMask = valueNoise2D(wx * 0.045, wz * 0.045, seed + 77321L)
                    if (skyMask > 0.62) {
                        val skyY = (29 + ((skyMask - 0.62) * 20).toInt()).coerceIn(28, 37)
                        data[localIndex(lx, skyY - 1, lz)] = BlockType.STONE.id.toByte()
                        data[localIndex(lx, skyY, lz)] = BlockType.GRASS.id.toByte()
                        if (skyY + 1 < CHUNK_HEIGHT - 2) {
                            val skyFlower = hash2D(wx, wz, seed + 1997L)
                            if (skyFlower > 0.80) {
                                data[localIndex(lx, skyY + 1, lz)] = BlockType.FLOWER_PURPLE.id.toByte()
                            } else if (skyFlower > 0.66) {
                                data[localIndex(lx, skyY + 1, lz)] = BlockType.FLOWER_PINK.id.toByte()
                            }
                        }
                    }
                }

                // Volcano Crater Molten Lava Lake & Cascading Lava Stream down the mountain!
                if (isVolcanoCone && biome != BiomeType.SKY_KINGDOM) {
                    if (distMainVolcano < 4.2) {
                        for (y in 26..33) {
                            data[localIndex(lx, y, lz)] = BlockType.LAVA.id.toByte()
                        }
                    } else if (abs(wx - volcanoCenterX) <= 1 && wz > volcanoCenterZ && distMainVolcano < 16.5) {
                        // Flowing fiery lava river streaming down south slope of volcano!
                        val streamY = (height + 1).coerceAtMost(CHUNK_HEIGHT - 2)
                        data[localIndex(lx, streamY, lz)] = BlockType.LAVA.id.toByte()
                    }
                }

                // Surface Ocean, River & Lake Water fill below SEA_LEVEL
                if (height < SEA_LEVEL) {
                    val fluid = when (biome) {
                        BiomeType.FROST_PEAKS -> BlockType.ICE
                        BiomeType.VOLCANIC_BADLANDS -> BlockType.LAVA
                        else -> BlockType.WATER
                    }
                    for (y in (height + 1)..SEA_LEVEL) {
                        data[localIndex(lx, y, lz)] = fluid.id.toByte()
                    }
                }
            }
        }

        // Pre-built Village Landmarks + Real Estate Township District (Roads, Villa, Tower, Mall, Plots)
        stampLandmarkIfInChunk(data, baseX, baseZ, heights, mandirCenterX, mandirCenterZ, isMandir = true)
        stampLandmarkIfInChunk(data, baseX, baseZ, heights, palaceCenterX, palaceCenterZ, isMandir = false)
        stampPyramidIfInChunk(data, baseX, baseZ, heights, pyramidCenterX, pyramidCenterZ)
        RealEstateArchitect.stampTownshipInChunk(data, baseX, baseZ, heights)

        // Cascading Mountain Waterfalls ("झरना") spilling down cliffs
        if (biome != BiomeType.VOLCANIC_BADLANDS && biome != BiomeType.DESERT_DUNES) {
            val cliffX = 6
            val cliffZ = 6
            val cliffH = heights[cliffZ * CHUNK_SIZE + cliffX]
            val dropH = heights[cliffZ * CHUNK_SIZE + (cliffX + 1)]
            val waterfallRoll = hash2D(cx, cz, seed + 6121L)
            if (cliffH >= SEA_LEVEL + 4 && dropH <= cliffH - 2 && waterfallRoll > 0.34 &&
                !RealEstateArchitect.isInsideRealEstateZone(baseX + cliffX, baseZ + cliffZ)
            ) {
                for (wy in (dropH + 1)..cliffH) {
                    data[localIndex(cliffX + 1, wy, cliffZ)] = BlockType.WATERFALL.id.toByte()
                }
                if (dropH >= 2) {
                    data[localIndex(cliffX + 1, dropH, cliffZ)] = BlockType.WATER.id.toByte()
                    if (cliffX + 2 < CHUNK_SIZE) {
                        data[localIndex(cliffX + 2, dropH, cliffZ)] = BlockType.WATER.id.toByte()
                    }
                }
            }
        }

        // Lush Trees, Cherry Blossoms, Wild Grass & 6 Vibrant Flower Gardens!
        if (biome != BiomeType.VOLCANIC_BADLANDS) {
            for (lz in 2 until CHUNK_SIZE - 2) {
                for (lx in 2 until CHUNK_SIZE - 2) {
                    val wx = baseX + lx
                    val wz = baseZ + lz
                    // Keep landmark & Real Estate Township compounds clear of random trees
                    if (abs(wx - mandirCenterX) <= 5 && abs(wz - mandirCenterZ) <= 5) continue
                    if (abs(wx - palaceCenterX) <= 5 && abs(wz - palaceCenterZ) <= 5) continue
                    if (abs(wx - pyramidCenterX) <= 7 && abs(wz - pyramidCenterZ) <= 7) continue
                    if (RealEstateArchitect.isInsideRealEstateZone(wx, wz)) continue
                    if (hypot((wx - volcanoCenterX).toDouble(), (wz - volcanoCenterZ).toDouble()) < 18.0) continue

                    val h = heights[lz * CHUNK_SIZE + lx]
                    if (h <= SEA_LEVEL || h >= CHUNK_HEIGHT - 9) continue
                    val surface = BlockType.fromId(data[localIndex(lx, h, lz)].toInt() and 0xFF)
                    val roll = hash2D(wx, wz, seed + 4049L)

                    val treeThreshold = when (biome) {
                        BiomeType.EMERALD_VALLEY -> 0.955
                        BiomeType.FLOWER_PARADISE -> 0.962
                        BiomeType.JUNGLE_RIVER -> 0.930
                        BiomeType.OCEAN_ISLANDS -> 0.965
                        BiomeType.SKY_KINGDOM -> 0.962
                        BiomeType.FROST_PEAKS -> 0.974
                        BiomeType.DESERT_DUNES -> 0.990
                        BiomeType.VOLCANIC_BADLANDS -> 1.0
                    }
                    if (roll > treeThreshold && h > SEA_LEVEL + 1 && (surface == BlockType.GRASS || surface == BlockType.SAND)) {
                        val isCherry = biome == BiomeType.FLOWER_PARADISE ||
                            biome == BiomeType.SKY_KINGDOM ||
                            (biome == BiomeType.EMERALD_VALLEY && hash2D(wx, wz, seed + 7171L) > 0.68)
                        val isTall = biome == BiomeType.JUNGLE_RIVER || roll > 0.978
                        placeTreeInChunk(data, lx, h, lz, tall = isTall, cherryBlossom = isCherry)
                    } else if (surface == BlockType.GRASS) {
                        val floraRoll = hash2D(wx, wz, seed + 8821L)
                        val gardenNoise = valueNoise2D(wx * 0.085, wz * 0.085, seed + 3131L)
                        val gardenZone = biome == BiomeType.FLOWER_PARADISE || gardenNoise > 0.44
                        val denseFlowerBoost = if (biome == BiomeType.FLOWER_PARADISE) 0.16 else 0.0
                        when {
                            gardenZone && floraRoll > (0.91 - denseFlowerBoost * 0.5) -> setIfAir(data, lx, h + 1, lz, BlockType.FLOWER_SUNFLOWER)
                            gardenZone && floraRoll > (0.83 - denseFlowerBoost * 0.6) -> setIfAir(data, lx, h + 1, lz, BlockType.FLOWER_PINK)
                            gardenZone && floraRoll > (0.75 - denseFlowerBoost * 0.7) -> setIfAir(data, lx, h + 1, lz, BlockType.FLOWER_PURPLE)
                            gardenZone && floraRoll > (0.68 - denseFlowerBoost * 0.8) -> setIfAir(data, lx, h + 1, lz, BlockType.FLOWER_RED)
                            gardenZone && floraRoll > (0.61 - denseFlowerBoost * 0.9) -> setIfAir(data, lx, h + 1, lz, BlockType.FLOWER_GOLD)
                            gardenZone && floraRoll > (0.55 - denseFlowerBoost) -> setIfAir(data, lx, h + 1, lz, BlockType.FLOWER_BLUE)
                            floraRoll > 0.74 -> setIfAir(data, lx, h + 1, lz, BlockType.TALL_GRASS)
                        }
                    }
                }
            }
        }

        return data
    }

    private fun stampLandmarkIfInChunk(
        data: ByteArray,
        baseX: Int,
        baseZ: Int,
        heights: IntArray,
        centerX: Int,
        centerZ: Int,
        isMandir: Boolean
    ) {
        val r = 3
        if (centerX + r < baseX || centerX - r >= baseX + CHUNK_SIZE) return
        if (centerZ + r < baseZ || centerZ - r >= baseZ + CHUNK_SIZE) return

        val baseY = SEA_LEVEL + 3
        val wallBlock = if (isMandir) BlockType.SANDSTONE else BlockType.BRICK
        val roofBlock = if (isMandir) BlockType.GOLD_ORE else BlockType.PLANKS

        for (dx in -r..r) {
            val wx = centerX + dx
            val lx = wx - baseX
            if (lx !in 0 until CHUNK_SIZE) continue
            for (dz in -r..r) {
                val wz = centerZ + dz
                val lz = wz - baseZ
                if (lz !in 0 until CHUNK_SIZE) continue

                // Stone platform foundation
                for (y in (baseY - 2)..(baseY - 1)) {
                    data[localIndex(lx, y, lz)] = BlockType.COBBLESTONE.id.toByte()
                }
                for (y in baseY..(baseY + 8).coerceAtMost(CHUNK_HEIGHT - 2)) {
                    data[localIndex(lx, y, lz)] = BlockType.AIR.id.toByte()
                }
                heights[lz * CHUNK_SIZE + lx] = baseY - 1

                val isOuter = abs(dx) == r || abs(dz) == r
                val isInner = abs(dx) <= 2 && abs(dz) <= 2
                val isDoor = dx == 0 && dz == r

                if (isOuter && !isDoor) {
                    for (dy in 0..3) {
                        val b = when {
                            dy == 1 && (abs(dx) == 1 || abs(dz) == 1) -> BlockType.GLASS
                            abs(dx) == r && abs(dz) == r -> if (isMandir) BlockType.GOLD_ORE else BlockType.WOOD_LOG
                            else -> wallBlock
                        }
                        data[localIndex(lx, baseY + dy, lz)] = b.id.toByte()
                    }
                }
                // Roof & Stepped Mandir Shikhara
                if (abs(dx) <= r && abs(dz) <= r) {
                    data[localIndex(lx, baseY + 4, lz)] = roofBlock.id.toByte()
                }
                if (isMandir && isInner) {
                    data[localIndex(lx, baseY + 5, lz)] = BlockType.SANDSTONE.id.toByte()
                }
                if (isMandir && abs(dx) <= 1 && abs(dz) <= 1) {
                    data[localIndex(lx, baseY + 6, lz)] = BlockType.GOLD_ORE.id.toByte()
                }
                if (isMandir && dx == 0 && dz == 0) {
                    data[localIndex(lx, baseY + 7, lz)] = BlockType.GOLD_ORE.id.toByte()
                    data[localIndex(lx, baseY + 8, lz)] = BlockType.TORCH.id.toByte()
                    // Sacred altar inside Mandir
                    data[localIndex(lx, baseY, lz)] = BlockType.GOLD_ORE.id.toByte()
                    data[localIndex(lx, baseY + 1, lz)] = BlockType.TORCH.id.toByte()
                }
                if (!isMandir && dx == 0 && dz == 0) {
                    data[localIndex(lx, baseY, lz)] = BlockType.GOLD_ORE.id.toByte()
                    data[localIndex(lx, baseY + 1, lz)] = BlockType.TORCH.id.toByte()
                }
            }
        }
    }

    private fun stampPyramidIfInChunk(
        data: ByteArray,
        baseX: Int,
        baseZ: Int,
        heights: IntArray,
        centerX: Int,
        centerZ: Int
    ) {
        val r = 6
        if (centerX + r < baseX || centerX - r >= baseX + CHUNK_SIZE) return
        if (centerZ + r < baseZ || centerZ - r >= baseZ + CHUNK_SIZE) return
        val baseY = SEA_LEVEL + 2
        for (dx in -r..r) {
            val lx = centerX + dx - baseX
            if (lx !in 0 until CHUNK_SIZE) continue
            for (dz in -r..r) {
                val lz = centerZ + dz - baseZ
                if (lz !in 0 until CHUNK_SIZE) continue
                val ring = maxOf(abs(dx), abs(dz))
                val pyrH = (r - ring).coerceAtLeast(0)
                for (y in (baseY - 2)..(baseY + pyrH).coerceAtMost(CHUNK_HEIGHT - 2)) {
                    val b = when {
                        y == baseY + r -> BlockType.GOLD_ORE
                        y == baseY && abs(dx) <= 1 && abs(dz) <= 1 -> BlockType.DIAMOND_ORE
                        else -> BlockType.SANDSTONE
                    }
                    data[localIndex(lx, y, lz)] = b.id.toByte()
                }
                heights[lz * CHUNK_SIZE + lx] = baseY + pyrH
            }
        }
    }

    private fun surfaceBlock(height: Int): BlockType = when (biome) {
        BiomeType.EMERALD_VALLEY, BiomeType.FLOWER_PARADISE, BiomeType.JUNGLE_RIVER, BiomeType.SKY_KINGDOM -> when {
            height >= 31 -> BlockType.SNOW
            height >= 26 -> BlockType.STONE
            height <= SEA_LEVEL -> BlockType.SAND
            else -> BlockType.GRASS
        }
        BiomeType.OCEAN_ISLANDS -> if (height <= SEA_LEVEL + 2) BlockType.SAND else BlockType.GRASS
        BiomeType.DESERT_DUNES -> if (height >= 20) BlockType.SANDSTONE else BlockType.SAND
        BiomeType.FROST_PEAKS -> if (height <= SEA_LEVEL) BlockType.STONE else BlockType.SNOW
        BiomeType.VOLCANIC_BADLANDS -> if (height >= 22) BlockType.OBSIDIAN else BlockType.COBBLESTONE
    }

    private fun subSurfaceBlock(): BlockType = when (biome) {
        BiomeType.DESERT_DUNES -> BlockType.SANDSTONE
        BiomeType.VOLCANIC_BADLANDS -> BlockType.STONE
        else -> BlockType.DIRT
    }

    private fun undergroundOreOrStone(wx: Int, y: Int, wz: Int): BlockType {
        val h = hash3D(wx, y, wz, seed + 991L)
        return when {
            y <= 6 && h > 0.94 -> BlockType.DIAMOND_ORE
            y <= 10 && h > 0.92 -> BlockType.GOLD_ORE
            y <= 15 && h > 0.89 -> BlockType.IRON_ORE
            h > 0.87 -> BlockType.COAL_ORE
            else -> BlockType.STONE
        }
    }

    private fun placeTreeInChunk(
        data: ByteArray,
        lx: Int,
        groundY: Int,
        lz: Int,
        tall: Boolean = false,
        cherryBlossom: Boolean = false
    ) {
        val trunkH = if (tall) 6 else 4
        val leafBlock = if (cherryBlossom) BlockType.CHERRY_LEAVES else BlockType.LEAVES
        for (dy in 1..trunkH) {
            if (groundY + dy < CHUNK_HEIGHT - 2) {
                data[localIndex(lx, groundY + dy, lz)] = BlockType.WOOD_LOG.id.toByte()
            }
        }
        val canopyY = groundY + trunkH
        for (dx in -2..2) {
            for (dz in -2..2) {
                val dist = abs(dx) + abs(dz)
                if (dist <= 3) setIfAir(data, lx + dx, canopyY, lz + dz, leafBlock)
                if (dist <= 2) setIfAir(data, lx + dx, canopyY + 1, lz + dz, leafBlock)
                if (dist <= 1) setIfAir(data, lx + dx, canopyY + 2, lz + dz, leafBlock)
            }
        }
    }

    private fun setIfAir(data: ByteArray, lx: Int, y: Int, lz: Int, block: BlockType) {
        if (lx in 0 until CHUNK_SIZE && lz in 0 until CHUNK_SIZE && y in 1 until CHUNK_HEIGHT) {
            val idx = localIndex(lx, y, lz)
            if (data[idx].toInt() == 0) {
                data[idx] = block.id.toByte()
            }
        }
    }

    private fun hash2D(x: Int, z: Int, s: Long): Double {
        var n = x * 374761393L + z * 668265263L + s * 1442695040888963407L
        n = (n xor (n ushr 13)) * 1274126177L
        n = n xor (n ushr 16)
        return ((n and 0xFFFFFFL).toDouble() / 0x1000000L.toDouble())
    }

    private fun hash3D(x: Int, y: Int, z: Int, s: Long): Double {
        var n = x * 374761393L + y * 1103515245L + z * 668265263L + s * 982451653L
        n = (n xor (n ushr 13)) * 1274126177L
        n = n xor (n ushr 16)
        return ((n and 0xFFFFFFL).toDouble() / 0x1000000L.toDouble())
    }

    private fun fade(t: Double): Double = t * t * t * (t * (t * 6.0 - 15.0) + 10.0)
    private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t

    private fun valueNoise2D(x: Double, z: Double, s: Long): Double {
        val xi = floor(x).toInt()
        val zi = floor(z).toInt()
        val xf = fade(x - xi)
        val zf = fade(z - zi)

        val v00 = hash2D(xi, zi, s)
        val v10 = hash2D(xi + 1, zi, s)
        val v01 = hash2D(xi, zi + 1, s)
        val v11 = hash2D(xi + 1, zi + 1, s)

        val ix0 = lerp(v00, v10, xf)
        val ix1 = lerp(v01, v11, xf)
        return lerp(ix0, ix1, zf)
    }

    private class FloatList(initialCapacity: Int) {
        private var arr = FloatArray(initialCapacity)
        var size: Int = 0
            private set

        fun addVertex(x: Float, y: Float, z: Float, u: Float, v: Float, shade: Float) {
            if (size + 6 > arr.size) {
                arr = arr.copyOf(arr.size * 2)
            }
            arr[size++] = x
            arr[size++] = y
            arr[size++] = z
            arr[size++] = u
            arr[size++] = v
            arr[size++] = shade
        }

        fun toDirectBuffer(): FloatBuffer? {
            if (size == 0) return null
            val bb = ByteBuffer.allocateDirect(size * 4).order(ByteOrder.nativeOrder())
            val fb = bb.asFloatBuffer()
            fb.put(arr, 0, size)
            fb.position(0)
            return fb
        }
    }

    companion object {
        private val CARDINAL_DIRS = arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
    }
}
