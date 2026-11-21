package com.example.engine.gl

import com.example.data.model.BiomeType
import com.example.data.model.BlockType
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.floor

const val CHUNK_SIZE = 16
const val CHUNK_HEIGHT = 32
const val SEA_LEVEL = 9

data class ChunkCoord(val cx: Int, val cz: Int)

data class ChunkMeshData(
    val opaqueBuffer: FloatBuffer?,
    val opaqueVertexCount: Int,
    val translucentBuffer: FloatBuffer?,
    val translucentVertexCount: Int,
    val waterBuffer: FloatBuffer? = null,
    val waterVertexCount: Int = 0
)

data class VoxelEditRecord(
    val x: Int,
    val y: Int,
    val z: Int,
    val previous: BlockType,
    val updated: BlockType
)

/**
 * 16x16x32 chunk-based 3D voxel world with lightweight 2D Perlin-style noise terrain,
 * procedural trees, face-culled mesh generation, and delta-only persistence.
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
        // Apply any saved deltas inside this chunk
        val minX = coord.cx * CHUNK_SIZE
        val maxX = minX + CHUNK_SIZE - 1
        val minZ = coord.cz * CHUNK_SIZE
        val maxZ = minZ + CHUNK_SIZE - 1

        blockDeltas.forEach { (key, blockId) ->
            val p = parseKey(key) ?: return@forEach
            if (p.first in minX..maxX && p.third in minZ..maxZ && p.second in 0 until CHUNK_HEIGHT) {
                val lx = p.first - minX
                val lz = p.third - minZ
                data[localIndex(lx, p.second, lz)] = blockId.toInt().coerceIn(0, 30).toByte()
            }
        }

        chunks[coord] = data
        dirtyChunks.add(coord)
        // Neighbors may need face culling refresh on boundary
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

    fun isNearWater(wx: Int, wz: Int): Boolean {
        for (dz in -4..4 step 2) {
            for (dx in -4..4 step 2) {
                val b = getBlock(wx + dx, SEA_LEVEL, wz + dz)
                val bHigh = getBlock(wx + dx, SEA_LEVEL + 3, wz + dz)
                if (b == BlockType.WATER || b == BlockType.WATERFALL || bHigh == BlockType.WATERFALL) {
                    return true
                }
            }
        }
        return false
    }

    fun findSpawnY(wx: Int, wz: Int): Float {
        for (y in CHUNK_HEIGHT - 2 downTo 1) {
            val b = getBlock(wx, y, wz)
            if (b != BlockType.AIR && b != BlockType.WATER && b != BlockType.WATERFALL && b != BlockType.LAVA && !b.crossPlant) {
                return (y + 1.05f)
            }
        }
        return (SEA_LEVEL + 3f)
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

            // Mark neighboring chunks dirty if edit touches a chunk border
            if (lx == 0) markDirtyIfLoadedLocked(ChunkCoord(coord.cx - 1, coord.cz))
            if (lx == CHUNK_SIZE - 1) markDirtyIfLoadedLocked(ChunkCoord(coord.cx + 1, coord.cz))
            if (lz == 0) markDirtyIfLoadedLocked(ChunkCoord(coord.cx, coord.cz - 1))
            if (lz == CHUNK_SIZE - 1) markDirtyIfLoadedLocked(ChunkCoord(coord.cx, coord.cz + 1))

            if (recordUndo) {
                if (undoStack.size >= 60) undoStack.removeFirst()
                undoStack.addLast(VoxelEditRecord(wx, wy, wz, prev, newBlock))
            }

            // Minecraft Infinite Water Source ("Water Unlimited") rules:
            // 1. Placing WATER spreads into adjacent air pockets & forms an infinite reservoir
            // 2. Mining a block next to 2+ water blocks automatically refills it with infinite water!
            if (newBlock == BlockType.WATER || newBlock == BlockType.WATERFALL) {
                spreadWaterLocked(wx, wy, wz)
            } else if (newBlock == BlockType.AIR) {
                checkInfiniteWaterRefillLocked(wx, wy, wz)
            }
            return prev
        }
    }

    private fun isWaterType(b: BlockType): Boolean = b == BlockType.WATER || b == BlockType.WATERFALL

    private fun checkInfiniteWaterRefillLocked(wx: Int, wy: Int, wz: Int) {
        if (wy !in 1 until CHUNK_HEIGHT) return
        val adjWater = listOf(
            getBlockRawLocked(wx + 1, wy, wz),
            getBlockRawLocked(wx - 1, wy, wz),
            getBlockRawLocked(wx, wy, wz + 1),
            getBlockRawLocked(wx, wy, wz - 1)
        ).count { isWaterType(it) }
        val aboveWater = isWaterType(getBlockRawLocked(wx, wy + 1, wz))
        if (adjWater >= 2 || aboveWater) {
            setBlockRawLocked(wx, wy, wz, if (aboveWater) BlockType.WATERFALL else BlockType.WATER)
        }
    }

    private fun spreadWaterLocked(wx: Int, wy: Int, wz: Int) {
        // Flow downward into a cascading waterfall if air is directly below
        var curY = wy - 1
        var drops = 0
        while (curY >= 1 && drops < 8 && getBlockRawLocked(wx, curY, wz) == BlockType.AIR) {
            setBlockRawLocked(wx, curY, wz, BlockType.WATERFALL)
            curY--
            drops++
        }
        // Check horizontal neighbors for 2x2 infinite water source creation
        val dirs = arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
        for ((dx, dz) in dirs) {
            val nx = wx + dx
            val nz = wz + dz
            if (getBlockRawLocked(nx, wy, nz) == BlockType.AIR) {
                val neighborWaterCount = dirs.count { (ddx, ddz) ->
                    isWaterType(getBlockRawLocked(nx + ddx, wy, nz + ddz))
                }
                if (neighborWaterCount >= 2) {
                    setBlockRawLocked(nx, wy, nz, BlockType.WATER)
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

    fun canUndo(): Boolean = synchronized(lock) { undoStack.isNotEmpty() }

    fun undo(): VoxelEditRecord? {
        val last = synchronized(lock) { undoStack.removeLastOrNull() } ?: return null
        setBlock(last.x, last.y, last.z, last.previous, recordUndo = false)
        return last
    }

    fun exportDeltas(): Map<String, Long> = synchronized(lock) { HashMap(blockDeltas) }

    /**
     * Ensures chunks within [renderDistance] around player chunk exist, rebuilds up to [maxRebuildsPerFrame]
     * dirty meshes with face culling, and returns visible meshes for rendering.
     */
    fun prepareVisibleMeshes(
        playerX: Float,
        playerZ: Float,
        renderDistance: Int,
        maxRebuildsPerFrame: Int = 3
    ): List<ChunkMeshData> {
        val centerCx = Math.floorDiv(floor(playerX).toInt(), CHUNK_SIZE)
        val centerCz = Math.floorDiv(floor(playerZ).toInt(), CHUNK_SIZE)
        val needed = ArrayList<ChunkCoord>((renderDistance * 2 + 1) * (renderDistance * 2 + 1))

        synchronized(lock) {
            for (dx in -renderDistance..renderDistance) {
                for (dz in -renderDistance..renderDistance) {
                    val c = ChunkCoord(centerCx + dx, centerCz + dz)
                    ensureChunkLocked(c)
                    needed.add(c)
                }
            }

            // Sort closer chunks first so chunks around the player build immediately
            needed.sortBy { (it.cx - centerCx) * (it.cx - centerCx) + (it.cz - centerCz) * (it.cz - centerCz) }

            var rebuilt = 0
            for (c in needed) {
                if (dirtyChunks.contains(c) || !chunkMeshes.containsKey(c)) {
                    chunkMeshes[c] = buildChunkMeshLocked(c)
                    dirtyChunks.remove(c)
                    rebuilt++
                    if (rebuilt >= maxRebuildsPerFrame) break
                }
            }

            // Prune far-away cached meshes to keep mobile RAM tight
            val pruneDist = renderDistance + 2
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
        if (block.translucent) {
            // Avoid internal faces between identical translucent blocks (e.g. water-to-water)
            return neighbor != block && neighbor.translucent
        }
        return neighbor.translucent
    }

    /**
     * Face-culled chunk mesh builder: emits ONLY faces exposed to air/translucent blocks.
     * Vertex layout: [x, y, z, u, v, shade] (6 floats per vertex, 6 vertices per quad).
     */
    private fun buildChunkMeshLocked(coord: ChunkCoord): ChunkMeshData {
        val data = chunks[coord] ?: return ChunkMeshData(null, 0, null, 0, null, 0)
        val opaqueFloats = FloatList(8192)
        val transFloats = FloatList(1024)
        val waterFloats = FloatList(1536)

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
                        block.translucent -> transFloats
                        else -> opaqueFloats
                    }

                    val x0 = wx.toFloat()
                    val y0 = y.toFloat()
                    val z0 = wz.toFloat()
                    val x1 = x0 + 1f
                    val y1 = if (block == BlockType.WATER) y0 + 0.88f else y0 + 1f
                    val z1 = z0 + 1f

                    // Authentic Minecraft 3D X-crossed quad for wild grass & flowers
                    if (block.crossPlant) {
                        val uv = VoxelTextureAtlas.uvFor(block, 0)
                        val pad = 0.12f
                        emitQuad(out, x0 + pad, y0, z0 + pad, x1 - pad, y0, z1 - pad, x1 - pad, y1, z1 - pad, x0 + pad, y1, z0 + pad, uv, 0.96f)
                        emitQuad(out, x1 - pad, y0, z0 + pad, x0 + pad, y0, z1 - pad, x0 + pad, y1, z1 - pad, x1 - pad, y1, z0 + pad, uv, 0.90f)
                        continue
                    }

                    // +Y Top face (faceDir = 0, brightest sunlight)
                    if (shouldRenderFace(block, getBlockRawLocked(wx, y + 1, wz))) {
                        val uv = VoxelTextureAtlas.uvFor(block, 0)
                        emitQuad(out, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, uv, 1.0f)
                    }
                    // -Y Bottom face (faceDir = 1, darkest)
                    if (y > 0 && shouldRenderFace(block, getBlockRawLocked(wx, y - 1, wz))) {
                        val uv = VoxelTextureAtlas.uvFor(block, 1)
                        emitQuad(out, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, uv, 0.55f)
                    }
                    // +X East face (faceDir = 2)
                    if (shouldRenderFace(block, getBlockRawLocked(wx + 1, y, wz))) {
                        val uv = VoxelTextureAtlas.uvFor(block, 2)
                        emitQuad(out, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, uv, 0.82f)
                    }
                    // -X West face (faceDir = 3)
                    if (shouldRenderFace(block, getBlockRawLocked(wx - 1, y, wz))) {
                        val uv = VoxelTextureAtlas.uvFor(block, 3)
                        emitQuad(out, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, uv, 0.78f)
                    }
                    // +Z South face (faceDir = 4)
                    if (shouldRenderFace(block, getBlockRawLocked(wx, y, wz + 1))) {
                        val uv = VoxelTextureAtlas.uvFor(block, 4)
                        emitQuad(out, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, uv, 0.70f)
                    }
                    // -Z North face (faceDir = 5)
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
            waterVertexCount = waterFloats.size / 6
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
        // Triangle 1: A -> B -> C
        out.addVertex(ax, ay, az, uv.u0, uv.v1, shade)
        out.addVertex(bx, by, bz, uv.u1, uv.v1, shade)
        out.addVertex(cx, cy, cz, uv.u1, uv.v0, shade)
        // Triangle 2: A -> C -> D
        out.addVertex(ax, ay, az, uv.u0, uv.v1, shade)
        out.addVertex(cx, cy, cz, uv.u1, uv.v0, shade)
        out.addVertex(dx, dy, dz, uv.u0, uv.v0, shade)
    }

    // --- PROCEDURAL 2D PERLIN / VALUE NOISE TERRAIN & TREES ---

    private fun generateChunkTerrain(cx: Int, cz: Int): ByteArray {
        val data = ByteArray(CHUNK_SIZE * CHUNK_HEIGHT * CHUNK_SIZE)
        val baseX = cx * CHUNK_SIZE
        val baseZ = cz * CHUNK_SIZE

        val heights = IntArray(CHUNK_SIZE * CHUNK_SIZE)

        for (lz in 0 until CHUNK_SIZE) {
            val wz = baseZ + lz
            for (lx in 0 until CHUNK_SIZE) {
                val wx = baseX + lx

                val n1 = valueNoise2D(wx * 0.045, wz * 0.045, seed)
                val n2 = valueNoise2D(wx * 0.11, wz * 0.11, seed + 1337L) * 0.45
                val n3 = valueNoise2D(wx * 0.24, wz * 0.24, seed + 7919L) * 0.18
                val combined = (n1 + n2 + n3) / 1.63 // roughly 0..1

                // Broad ocean basins ("समुद्र") + winding rivers ("पानी")
                val oceanMask = valueNoise2D(wx * 0.016, wz * 0.016, seed + 22111L)
                val riverBand = abs(valueNoise2D(wx * 0.028, wz * 0.028, seed + 9091L) - 0.5)
                val isOcean = oceanMask < 0.27 && (abs(wx - 8) > 14 || abs(wz - 8) > 14)
                val isRiver = riverBand < 0.042 && (abs(wx - 8) > 10 || abs(wz - 8) > 10)

                val baseHeight = when (biome) {
                    BiomeType.EMERALD_VALLEY -> (7.0 + combined * 12.0).toInt().coerceIn(5, 23)
                    BiomeType.DESERT_DUNES -> (8.0 + combined * 9.0).toInt().coerceIn(6, 20)
                    BiomeType.FROST_PEAKS -> (8.0 + combined * 15.0).toInt().coerceIn(6, 25)
                    BiomeType.VOLCANIC_BADLANDS -> (7.0 + combined * 13.0).toInt().coerceIn(5, 23)
                }
                val height = when {
                    isOcean -> (SEA_LEVEL - 4 + (combined * 2.5).toInt()).coerceIn(3, SEA_LEVEL - 2)
                    isRiver -> (SEA_LEVEL - 2).coerceAtLeast(4)
                    else -> baseHeight
                }
                heights[lz * CHUNK_SIZE + lx] = height

                // Y=0 Bedrock
                data[localIndex(lx, 0, lz)] = BlockType.BEDROCK.id.toByte()

                for (y in 1..height) {
                    val block = when {
                        y == height -> surfaceBlock(height)
                        y >= height - 2 -> subSurfaceBlock()
                        else -> undergroundOreOrStone(wx, y, wz)
                    }
                    data[localIndex(lx, y, lz)] = block.id.toByte()
                }

                // Ocean, River & Lake Water fill below SEA_LEVEL
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

        // Cascading Mountain Waterfalls ("झरना") spilling down cliffs into rivers/valleys
        if (biome == BiomeType.EMERALD_VALLEY || biome == BiomeType.FROST_PEAKS) {
            val cliffX = 6
            val cliffZ = 6
            val cliffH = heights[cliffZ * CHUNK_SIZE + cliffX]
            val dropH = heights[cliffZ * CHUNK_SIZE + (cliffX + 1)]
            val waterfallRoll = hash2D(cx, cz, seed + 6121L)
            if (cliffH >= SEA_LEVEL + 4 && dropH <= cliffH - 2 && waterfallRoll > 0.42) {
                for (wy in (dropH + 1)..cliffH) {
                    data[localIndex(cliffX + 1, wy, cliffZ)] = BlockType.WATERFALL.id.toByte()
                }
                // Splash pool at waterfall base
                if (dropH >= 2) {
                    data[localIndex(cliffX + 1, dropH, cliffZ)] = BlockType.WATER.id.toByte()
                    if (cliffX + 2 < CHUNK_SIZE) {
                        data[localIndex(cliffX + 2, dropH, cliffZ)] = BlockType.WATER.id.toByte()
                    }
                }
            }
        }

        // Lush Jungle Trees, Wild Grass ("घास फूस"), & Colorful Flower Gardens ("फूल बगीचा")
        if (biome != BiomeType.VOLCANIC_BADLANDS) {
            for (lz in 2 until CHUNK_SIZE - 2) {
                for (lx in 2 until CHUNK_SIZE - 2) {
                    val wx = baseX + lx
                    val wz = baseZ + lz
                    val h = heights[lz * CHUNK_SIZE + lx]
                    if (h <= SEA_LEVEL || h >= CHUNK_HEIGHT - 7) continue
                    val surface = BlockType.fromId(data[localIndex(lx, h, lz)].toInt() and 0xFF)
                    val roll = hash2D(wx, wz, seed + 4049L)

                    val treeThreshold = when (biome) {
                        BiomeType.EMERALD_VALLEY -> 0.952 // richer jungle & forests
                        BiomeType.FROST_PEAKS -> 0.972
                        BiomeType.DESERT_DUNES -> 0.990
                        BiomeType.VOLCANIC_BADLANDS -> 1.0
                    }
                    if (roll > treeThreshold && h > SEA_LEVEL + 1) {
                        placeTreeInChunk(data, lx, h, lz, tall = (roll > 0.980))
                    } else if (surface == BlockType.GRASS) {
                        val floraRoll = hash2D(wx, wz, seed + 8821L)
                        val gardenZone = valueNoise2D(wx * 0.09, wz * 0.09, seed + 3131L) > 0.56
                        when {
                            gardenZone && floraRoll > 0.84 -> setIfAir(data, lx, h + 1, lz, BlockType.FLOWER_RED)
                            gardenZone && floraRoll > 0.72 -> setIfAir(data, lx, h + 1, lz, BlockType.FLOWER_GOLD)
                            gardenZone && floraRoll > 0.64 -> setIfAir(data, lx, h + 1, lz, BlockType.FLOWER_BLUE)
                            floraRoll > 0.78 -> setIfAir(data, lx, h + 1, lz, BlockType.TALL_GRASS)
                        }
                    }
                }
            }
        }

        return data
    }

    private fun surfaceBlock(height: Int): BlockType = when (biome) {
        BiomeType.EMERALD_VALLEY -> if (height <= SEA_LEVEL) BlockType.SAND else BlockType.GRASS
        BiomeType.DESERT_DUNES -> if (height >= 14) BlockType.SANDSTONE else BlockType.SAND
        BiomeType.FROST_PEAKS -> if (height <= SEA_LEVEL) BlockType.STONE else BlockType.SNOW
        BiomeType.VOLCANIC_BADLANDS -> if (height >= 14) BlockType.OBSIDIAN else BlockType.COBBLESTONE
    }

    private fun subSurfaceBlock(): BlockType = when (biome) {
        BiomeType.EMERALD_VALLEY -> BlockType.DIRT
        BiomeType.DESERT_DUNES -> BlockType.SANDSTONE
        BiomeType.FROST_PEAKS -> BlockType.DIRT
        BiomeType.VOLCANIC_BADLANDS -> BlockType.STONE
    }

    private fun undergroundOreOrStone(wx: Int, y: Int, wz: Int): BlockType {
        val h = hash3D(wx, y, wz, seed + 991L)
        return when {
            y <= 3 && h > 0.95 -> BlockType.DIAMOND_ORE
            y <= 5 && h > 0.93 -> BlockType.GOLD_ORE
            y <= 8 && h > 0.89 -> BlockType.IRON_ORE
            h > 0.87 -> BlockType.COAL_ORE
            else -> BlockType.STONE
        }
    }

    private fun placeTreeInChunk(data: ByteArray, lx: Int, groundY: Int, lz: Int, tall: Boolean = false) {
        val trunkH = if (tall) 5 else 4
        for (dy in 1..trunkH) {
            if (groundY + dy < CHUNK_HEIGHT - 2) {
                data[localIndex(lx, groundY + dy, lz)] = BlockType.WOOD_LOG.id.toByte()
            }
        }
        val canopyY = groundY + trunkH
        for (dx in -2..2) {
            for (dz in -2..2) {
                val dist = abs(dx) + abs(dz)
                if (dist <= 3) {
                    setIfAir(data, lx + dx, canopyY, lz + dz, BlockType.LEAVES)
                }
                if (dist <= 2) {
                    setIfAir(data, lx + dx, canopyY + 1, lz + dz, BlockType.LEAVES)
                }
                if (dist <= 1) {
                    setIfAir(data, lx + dx, canopyY + 2, lz + dz, BlockType.LEAVES)
                }
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
}
