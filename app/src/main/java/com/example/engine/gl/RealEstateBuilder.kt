package com.example.engine.gl

import com.example.data.model.BlockType
import kotlin.math.abs

/**
 * Procedural Real Estate & Modern Urban Architecture Generator for BlockForge.
 * Stamps realistic, walkable multi-story properties, paved roads with street lamps,
 * private swimming pools, balconies, and walled plots directly into world chunks
 * and supports instant on-demand construction anywhere in the world!
 */
internal object RealEstateArchitect {

    // Real Estate Township Master Coordinates (South-East & South-West Grand Boulevard District)
    const val BOULEVARD_Z_CENTER = 28
    const val AVENUE_X_CENTER = 26

    const val VILLA_X = 40
    const val VILLA_Z = 42

    const val TOWER_X = 10
    const val TOWER_Z = 44

    const val MALL_X = -18
    const val MALL_Z = 44

    const val FARMHOUSE_X = -42
    const val FARMHOUSE_Z = 42

    const val PLOT_A_X = 44
    const val PLOT_A_Z = 14

    const val PLOT_B_X = 58
    const val PLOT_B_Z = 42

    fun isInsideRealEstateZone(wx: Int, wz: Int): Boolean {
        // Keep boulevard roads, villas, towers, malls, farmhouses & plots free of random trees/weeds
        if (wz in (BOULEVARD_Z_CENTER - 4)..(BOULEVARD_Z_CENTER + 4) && wx in -56..70) return true
        if (wx in (AVENUE_X_CENTER - 4)..(AVENUE_X_CENTER + 4) && wz in -14..60) return true
        if (abs(wx - VILLA_X) <= 10 && abs(wz - VILLA_Z) <= 9) return true
        if (abs(wx - TOWER_X) <= 9 && abs(wz - TOWER_Z) <= 9) return true
        if (abs(wx - MALL_X) <= 11 && abs(wz - MALL_Z) <= 9) return true
        if (abs(wx - FARMHOUSE_X) <= 9 && abs(wz - FARMHOUSE_Z) <= 9) return true
        if (abs(wx - PLOT_A_X) <= 8 && abs(wz - PLOT_A_Z) <= 8) return true
        if (abs(wx - PLOT_B_X) <= 8 && abs(wz - PLOT_B_Z) <= 8) return true
        return false
    }

    /**
     * Stamps the full Real Estate City District (Roads, Street Lamps, Villa + Pool,
     * 4-Story Apartment Tower, Shopping Mall, Farmhouse & Ready Plots) into any overlapping chunk.
     */
    fun stampTownshipInChunk(
        data: ByteArray,
        baseX: Int,
        baseZ: Int,
        heights: IntArray
    ) {
        val baseY = SEA_LEVEL + 3
        val put = { wx: Int, y: Int, wz: Int, block: BlockType ->
            val lx = wx - baseX
            val lz = wz - baseZ
            if (lx in 0 until CHUNK_SIZE && lz in 0 until CHUNK_SIZE && y in 1 until CHUNK_HEIGHT - 1) {
                data[(y * CHUNK_SIZE + lz) * CHUNK_SIZE + lx] = block.id.toByte()
                if (block != BlockType.AIR && y > heights[lz * CHUNK_SIZE + lx]) {
                    heights[lz * CHUNK_SIZE + lx] = y
                }
            }
        }
        val prepColumn = { wx: Int, wz: Int, floorY: Int, clearTopY: Int ->
            val lx = wx - baseX
            val lz = wz - baseZ
            if (lx in 0 until CHUNK_SIZE && lz in 0 until CHUNK_SIZE) {
                val supportStart = (floorY - 6).coerceAtLeast(1)
                for (y in supportStart until floorY) {
                    val idx = (y * CHUNK_SIZE + lz) * CHUNK_SIZE + lx
                    if (data[idx].toInt() == 0 || data[idx].toInt() == BlockType.WATER.id) {
                        data[idx] = BlockType.STONE.id.toByte()
                    }
                }
                for (y in floorY..clearTopY.coerceAtMost(CHUNK_HEIGHT - 2)) {
                    data[(y * CHUNK_SIZE + lz) * CHUNK_SIZE + lx] = BlockType.AIR.id.toByte()
                }
                heights[lz * CHUNK_SIZE + lx] = floorY - 1
            }
        }

        // 1. Grand Boulevard Roads & Street Lamps
        stampBoulevardInChunk(baseX, baseZ, baseY, prepColumn, put)

        // 2. Royal Green 2-Story Luxury Villa + Private Blue Swimming Pool
        if (overlaps(baseX, baseZ, VILLA_X, VILLA_Z, 9, 8)) {
            stampLuxuryVilla(VILLA_X, VILLA_Z, baseY, prepColumn, put)
        }

        // 3. Skyview Heights 4-Story Luxury Apartment Tower + Rooftop Helipad
        if (overlaps(baseX, baseZ, TOWER_X, TOWER_Z, 8, 8)) {
            stampApartmentTower(TOWER_X, TOWER_Z, baseY, prepColumn, put)
        }

        // 4. BlockForge Commercial Shopping Mall & Real Estate Plaza
        if (overlaps(baseX, baseZ, MALL_X, MALL_Z, 10, 8)) {
            stampShoppingMall(MALL_X, MALL_Z, baseY, prepColumn, put)
        }

        // 5. Emerald Lakefront Farmhouse
        if (overlaps(baseX, baseZ, FARMHOUSE_X, FARMHOUSE_Z, 8, 8)) {
            stampFarmhouse(FARMHOUSE_X, FARMHOUSE_Z, baseY, prepColumn, put)
        }

        // 6. Ready-to-Build Walled Real Estate Plots (Sector A & Sector B)
        if (overlaps(baseX, baseZ, PLOT_A_X, PLOT_A_Z, 7, 7)) {
            stampResidentialPlot(PLOT_A_X, PLOT_A_Z, baseY, prepColumn, put)
        }
        if (overlaps(baseX, baseZ, PLOT_B_X, PLOT_B_Z, 7, 7)) {
            stampResidentialPlot(PLOT_B_X, PLOT_B_Z, baseY, prepColumn, put)
        }
    }

    /**
     * On-demand player construction of realistic real-estate buildings anywhere in the world!
     */
    fun buildRealEstateLive(
        originX: Int,
        originZ: Int,
        baseY: Int,
        structureType: String,
        setWorldBlock: (Int, Int, Int, BlockType) -> Unit
    ) {
        val prep = { wx: Int, wz: Int, floorY: Int, clearTopY: Int ->
            for (y in (floorY - 3).coerceAtLeast(1) until floorY) {
                setWorldBlock(wx, y, wz, BlockType.STONE)
            }
            for (y in floorY..clearTopY.coerceAtMost(CHUNK_HEIGHT - 2)) {
                setWorldBlock(wx, y, wz, BlockType.AIR)
            }
        }
        when (structureType) {
            "LUXURY_VILLA" -> stampLuxuryVilla(originX, originZ, baseY, prep, setWorldBlock)
            "APARTMENT_TOWER" -> stampApartmentTower(originX, originZ, baseY, prep, setWorldBlock)
            "SHOPPING_MALL" -> stampShoppingMall(originX, originZ, baseY, prep, setWorldBlock)
            "RESIDENTIAL_PLOT" -> stampResidentialPlot(originX, originZ, baseY, prep, setWorldBlock)
            "BOULEVARD_ROAD" -> {
                for (dx in -14..14) {
                    for (dz in -3..3) {
                        val wx = originX + dx
                        val wz = originZ + dz
                        prep(wx, wz, baseY, baseY + 7)
                        val roadBlock = when {
                            abs(dz) == 3 -> BlockType.SANDSTONE
                            dz == 0 && (dx and 3) < 2 -> BlockType.GOLD_ORE
                            else -> BlockType.COBBLESTONE
                        }
                        setWorldBlock(wx, baseY - 1, wz, roadBlock)
                        if (abs(dz) == 3 && Math.floorMod(dx, 8) == 0) {
                            for (ly in 0..3) setWorldBlock(wx, baseY + ly, wz, BlockType.STONE)
                            setWorldBlock(wx, baseY + 4, wz, BlockType.TORCH)
                        }
                    }
                }
            }
            else -> stampLuxuryVilla(originX, originZ, baseY, prep, setWorldBlock)
        }
    }

    private fun overlaps(baseX: Int, baseZ: Int, cx: Int, cz: Int, rx: Int, rz: Int): Boolean {
        return cx + rx >= baseX && cx - rx < baseX + CHUNK_SIZE &&
            cz + rz >= baseZ && cz - rz < baseZ + CHUNK_SIZE
    }

    private fun stampBoulevardInChunk(
        baseX: Int,
        baseZ: Int,
        baseY: Int,
        prep: (Int, Int, Int, Int) -> Unit,
        put: (Int, Int, Int, BlockType) -> Unit
    ) {
        val maxX = baseX + CHUNK_SIZE - 1
        val maxZ = baseZ + CHUNK_SIZE - 1

        // Main East-West City Highway (z = 25..31, x = -52..66)
        if (BOULEVARD_Z_CENTER + 3 >= baseZ && BOULEVARD_Z_CENTER - 3 <= maxZ && maxX >= -52 && baseX <= 66) {
            for (wx in maxOf(baseX, -52)..minOf(maxX, 66)) {
                for (dz in -3..3) {
                    val wz = BOULEVARD_Z_CENTER + dz
                    if (wz !in baseZ..maxZ) continue
                    prep(wx, wz, baseY, baseY + 7)
                    val surface = when {
                        abs(dz) == 3 -> BlockType.SANDSTONE
                        dz == 0 && Math.floorMod(wx, 4) < 2 -> BlockType.GOLD_ORE
                        else -> BlockType.COBBLESTONE
                    }
                    put(wx, baseY - 1, wz, surface)
                    // Street Lamps every 10 blocks along the curb
                    if (abs(dz) == 3 && Math.floorMod(wx, 10) == 0 && abs(wx - AVENUE_X_CENTER) > 4) {
                        for (ly in 0..3) put(wx, baseY + ly, wz, BlockType.STONE)
                        put(wx, baseY + 4, wz, BlockType.TORCH)
                    }
                }
            }
        }

        // Cross North-South Avenue (x = 23..29, z = -10..54)
        if (AVENUE_X_CENTER + 3 >= baseX && AVENUE_X_CENTER - 3 <= maxX && maxZ >= -10 && baseZ <= 54) {
            for (wz in maxOf(baseZ, -10)..minOf(maxZ, 54)) {
                for (dx in -3..3) {
                    val wx = AVENUE_X_CENTER + dx
                    if (wx !in baseX..maxX) continue
                    val inIntersection = abs(wz - BOULEVARD_Z_CENTER) <= 3
                    prep(wx, wz, baseY, baseY + 7)
                    val surface = when {
                        inIntersection -> BlockType.COBBLESTONE
                        abs(dx) == 3 -> BlockType.SANDSTONE
                        dx == 0 && Math.floorMod(wz, 4) < 2 -> BlockType.GOLD_ORE
                        else -> BlockType.COBBLESTONE
                    }
                    put(wx, baseY - 1, wz, surface)
                    if (!inIntersection && abs(dx) == 3 && Math.floorMod(wz, 10) == 0) {
                        for (ly in 0..3) put(wx, baseY + ly, wz, BlockType.STONE)
                        put(wx, baseY + 4, wz, BlockType.TORCH)
                    }
                }
            }
        }
    }

    /**
     * Royal Green 2-Story Modern Luxury Villa:
     * Walled compound, driveway gate, private tiled swimming pool, ground living hall,
     * walkable interior wooden staircase, 2nd-floor suite with glass balcony & rooftop terrace!
     */
    private fun stampLuxuryVilla(
        cx: Int,
        cz: Int,
        baseY: Int,
        prep: (Int, Int, Int, Int) -> Unit,
        put: (Int, Int, Int, BlockType) -> Unit
    ) {
        // Compound grounds & boundary wall (17 x 15 estate)
        for (dx in -8..8) {
            for (dz in -7..7) {
                val wx = cx + dx
                val wz = cz + dz
                prep(wx, wz, baseY, baseY + 13)
                val isBorder = abs(dx) == 8 || abs(dz) == 7
                val isFrontGate = dz == -7 && dx in -2..0
                val isDriveway = dx in -2..0 && dz in -7..-3
                put(wx, baseY - 1, wz, if (isDriveway) BlockType.SANDSTONE else BlockType.GRASS)

                if (isBorder && !isFrontGate) {
                    put(wx, baseY, wz, BlockType.SANDSTONE)
                    put(wx, baseY + 1, wz, if ((dx + dz) and 1 == 0) BlockType.BRICK else BlockType.SANDSTONE)
                }
                // Grand Gate Pillars with glowing entrance lamps
                if (dz == -7 && (dx == -3 || dx == 1)) {
                    for (ly in 0..3) put(wx, baseY + ly, wz, BlockType.SANDSTONE)
                    put(wx, baseY + 4, wz, BlockType.TORCH)
                }
            }
        }

        // Private Tiled Blue Swimming Pool in front-right courtyard (dx 2..6, dz -5..0)
        for (dx in 2..6) {
            for (dz in -5..0) {
                val wx = cx + dx
                val wz = cz + dz
                val isRim = dx == 2 || dx == 6 || dz == -5 || dz == 0
                if (isRim) {
                    put(wx, baseY - 1, wz, BlockType.SANDSTONE)
                } else {
                    put(wx, baseY - 3, wz, BlockType.SANDSTONE)
                    put(wx, baseY - 2, wz, BlockType.WATER)
                    put(wx, baseY - 1, wz, BlockType.WATER)
                }
            }
        }

        // Front Flower Beds beside driveway
        put(cx - 5, baseY, cz - 5, BlockType.FLOWER_PINK)
        put(cx - 4, baseY, cz - 5, BlockType.FLOWER_SUNFLOWER)
        put(cx - 6, baseY, cz - 4, BlockType.FLOWER_RED)
        put(cx + 4, baseY, cz + 3, BlockType.FLOWER_PURPLE)
        put(cx + 5, baseY, cz + 4, BlockType.FLOWER_GOLD)

        // 2-Story Modern Villa Structure (dx -6..1, dz -2..5)
        for (dx in -6..1) {
            for (dz in -2..5) {
                val wx = cx + dx
                val wz = cz + dz
                val isOuter = dx == -6 || dx == 1 || dz == -2 || dz == 5
                val isCorner = (dx == -6 || dx == 1) && (dz == -2 || dz == 5)
                val isFrontDoor = dz == -2 && dx in -3..-2
                val isPoolDoor = dx == 1 && dz in 0..1

                // Ground floor polished plank flooring
                put(wx, baseY - 1, wz, BlockType.PLANKS)

                // Ground Floor Walls (dy 0..3)
                if (isOuter) {
                    for (dy in 0..3) {
                        if ((isFrontDoor || isPoolDoor) && dy in 0..2) continue
                        val block = when {
                            isCorner -> BlockType.WOOD_LOG
                            dy in 1..2 && ((dz == -2 && dx in -5..-4) || (dx == 1 && dz in 2..4) || (dz == 5 && dx in -4..0)) -> BlockType.GLASS
                            dy == 0 -> BlockType.BRICK
                            else -> BlockType.SANDSTONE
                        }
                        put(wx, baseY + dy, wz, block)
                    }
                }

                // 2nd Floor Slab (dy = 4), leave stairwell opening at dx == -5, dz in 1..4
                val isStairOpening = dx == -5 && dz in 1..4
                if (!isStairOpening) {
                    put(wx, baseY + 4, wz, BlockType.PLANKS)
                }

                // 2nd Floor Walls (dy 5..8)
                if (isOuter) {
                    val isBalconyDoor = dz == -2 && dx in -3..-2
                    for (dy in 5..8) {
                        if (isBalconyDoor && dy in 5..7) continue
                        val block = when {
                            isCorner -> BlockType.SANDSTONE
                            dy in 6..7 && (dz == -2 || dx == 1 || dz == 5) -> BlockType.GLASS
                            else -> BlockType.SANDSTONE
                        }
                        put(wx, baseY + dy, wz, block)
                    }
                }

                // Modern Flat Rooftop Terrace (dy = 9) + Parapet Border (dy = 10)
                put(wx, baseY + 9, wz, BlockType.SANDSTONE)
                if (isOuter) {
                    put(wx, baseY + 10, wz, if (isCorner) BlockType.GOLD_ORE else BlockType.GLASS)
                }
            }
        }

        // Cantilevered Front 2nd-Floor Glass Balcony (dz = -3, dx = -6..1)
        for (dx in -6..1) {
            put(cx + dx, baseY + 4, cz - 3, BlockType.SANDSTONE)
            put(cx + dx, baseY + 5, cz - 3, BlockType.GLASS)
        }
        // Walkable Interior Staircase from Ground Floor up to 2nd Floor
        for (step in 0..3) {
            put(cx - 5, baseY + step, cz + 1 + step, BlockType.PLANKS)
        }
        // Warm Interior & Rooftop Lighting
        put(cx - 2, baseY + 2, cz + 1, BlockType.TORCH)
        put(cx - 2, baseY + 7, cz + 1, BlockType.TORCH)
        put(cx - 6, baseY + 11, cz - 2, BlockType.TORCH)
        put(cx + 1, baseY + 11, cz - 2, BlockType.TORCH)
    }

    /**
     * Skyview Heights 4-Story Luxury Apartment Tower:
     * Grand glass lobby, 4 habitable floors with wrap-around panoramic windows & balconies,
     * stairwell, brick/sandstone corner pilasters, and rooftop Helipad ("H")!
     */
    private fun stampApartmentTower(
        cx: Int,
        cz: Int,
        baseY: Int,
        prep: (Int, Int, Int, Int) -> Unit,
        put: (Int, Int, Int, BlockType) -> Unit
    ) {
        val r = 6
        val floorH = 4
        val floors = 4
        val roofY = baseY + floors * floorH // +16

        for (dx in -r..r) {
            for (dz in -r..r) {
                val wx = cx + dx
                val wz = cz + dz
                prep(wx, wz, baseY, roofY + 5)
                val isOuter = abs(dx) == r || abs(dz) == r
                val isCorner = abs(dx) == r && abs(dz) == r

                // Paved lobby plaza floor
                put(wx, baseY - 1, wz, BlockType.STONE)

                for (f in 0 until floors) {
                    val fy = baseY + f * floorH
                    // Floor slab (except ground which uses baseY-1)
                    if (f > 0) {
                        val isStairWell = dx == -4 && dz in -2..1
                        if (!isStairWell) {
                            put(wx, fy, wz, BlockType.PLANKS)
                        }
                    }

                    // Perimeter walls & wide modern apartment windows
                    if (isOuter) {
                        val isMainEntrance = f == 0 && dz == -r && dx in -1..1
                        for (dy in 0 until floorH) {
                            val wy = fy + dy
                            if (isMainEntrance && dy in 0..2) continue
                            val block = when {
                                isCorner -> BlockType.BRICK
                                dy == 0 -> BlockType.SANDSTONE
                                dy in 1..2 && ((dx % 3 != 0 && abs(dz) == r) || (dz % 3 != 0 && abs(dx) == r)) -> BlockType.GLASS
                                else -> BlockType.SANDSTONE
                            }
                            put(wx, wy, wz, block)
                        }
                    }
                }

                // Rooftop Terrace & Gold "H" Helipad!
                put(wx, roofY, wz, BlockType.COBBLESTONE)
                if (isOuter) {
                    put(wx, roofY + 1, wz, if (isCorner) BlockType.GOLD_ORE else BlockType.GLASS)
                    if (isCorner) put(wx, roofY + 2, wz, BlockType.TORCH)
                }
                val isHelipadH = (abs(dx) == 2 && dz in -2..2) || (dz == 0 && dx in -2..2)
                if (isHelipadH) {
                    put(wx, roofY, wz, BlockType.GOLD_ORE)
                }
            }
        }

        // Front Exterior Balconies on Floors 2, 3 & 4 (dz = -r - 1)
        for (f in 1 until floors) {
            val by = baseY + f * floorH
            for (dx in -3..3) {
                put(cx + dx, by, cz - r - 1, BlockType.SANDSTONE)
                put(cx + dx, by + 1, cz - r - 1, BlockType.GLASS)
            }
        }
        // Lobby Columns & Entrance Lighting
        put(cx - 2, baseY + 2, cz - r - 1, BlockType.TORCH)
        put(cx + 2, baseY + 2, cz - r - 1, BlockType.TORCH)
        put(cx, baseY + 2, cz, BlockType.TORCH)
    }

    /**
     * BlockForge Commercial Shopping Mall & Real Estate Office:
     * Wide 2-story showroom with golden grand entrance arch, panoramic glass storefront,
     * mezzanine gallery, and glowing commercial sign parapet!
     */
    private fun stampShoppingMall(
        cx: Int,
        cz: Int,
        baseY: Int,
        prep: (Int, Int, Int, Int) -> Unit,
        put: (Int, Int, Int, BlockType) -> Unit
    ) {
        val rx = 8
        val rz = 6
        for (dx in -rx..rx) {
            for (dz in -rz..rz) {
                val wx = cx + dx
                val wz = cz + dz
                prep(wx, wz, baseY, baseY + 12)
                val isOuter = abs(dx) == rx || abs(dz) == rz
                val isCorner = abs(dx) == rx && abs(dz) == rz
                val isGrandDoor = dz == -rz && dx in -2..2

                // Marble/sandstone mall floor
                put(wx, baseY - 1, wz, if ((dx + dz) and 1 == 0) BlockType.SANDSTONE else BlockType.STONE)

                if (isOuter) {
                    for (dy in 0..7) {
                        if (isGrandDoor && dy in 0..3) continue
                        val block = when {
                            isGrandDoor && dy == 4 -> BlockType.GOLD_ORE
                            isCorner -> BlockType.GOLD_ORE
                            dz == -rz && dy in 1..6 && abs(dx) in 3..7 -> BlockType.GLASS
                            dy in 1..2 && abs(dx) == rx && abs(dz) <= 4 -> BlockType.GLASS
                            dy == 4 || dy == 7 -> BlockType.SANDSTONE
                            else -> BlockType.BRICK
                        }
                        put(wx, baseY + dy, wz, block)
                    }
                } else {
                    // 2nd-floor perimeter shopping mezzanine around open central atrium!
                    val isMezzanine = abs(dx) >= 5 || dz >= 3
                    if (isMezzanine) {
                        put(wx, baseY + 4, wz, BlockType.PLANKS)
                    }
                }

                // Mall Roof & Illuminated Skylight Center
                val isSkylight = abs(dx) <= 3 && abs(dz) <= 2
                put(wx, baseY + 8, wz, if (isSkylight) BlockType.GLASS else BlockType.STONE)
                if (dz == -rz) {
                    put(wx, baseY + 9, wz, if (abs(dx) <= 4) BlockType.GOLD_ORE else BlockType.SANDSTONE)
                    if (abs(dx) == 4 || dx == 0) {
                        put(wx, baseY + 10, wz, BlockType.TORCH)
                    }
                }
            }
        }
        // Interior shop counter & atrium lights
        for (dx in -2..2) {
            put(cx + dx, baseY, cz + 2, BlockType.PLANKS)
        }
        put(cx, baseY + 1, cz + 2, BlockType.TORCH)
        put(cx - 4, baseY + 2, cz - 3, BlockType.TORCH)
        put(cx + 4, baseY + 2, cz - 3, BlockType.TORCH)
    }

    /**
     * Emerald Lakefront Farmhouse:
     * Brick & wood-log cottage with front columned porch, pitched plank roof & garden.
     */
    private fun stampFarmhouse(
        cx: Int,
        cz: Int,
        baseY: Int,
        prep: (Int, Int, Int, Int) -> Unit,
        put: (Int, Int, Int, BlockType) -> Unit
    ) {
        for (dx in -6..6) {
            for (dz in -6..6) {
                val wx = cx + dx
                val wz = cz + dz
                prep(wx, wz, baseY, baseY + 11)
                put(wx, baseY - 1, wz, if (abs(dx) <= 4 && dz in -4..4) BlockType.PLANKS else BlockType.GRASS)

                // Front porch veranda columns (dz == -4)
                if (dz == -4 && (dx == -4 || dx == -2 || dx == 2 || dx == 4)) {
                    for (dy in 0..3) put(wx, baseY + dy, wz, BlockType.WOOD_LOG)
                }

                // House body (dx -4..4, dz -2..4)
                if (dx in -4..4 && dz in -2..4) {
                    val isOuter = abs(dx) == 4 || dz == -2 || dz == 4
                    val isDoor = dz == -2 && dx in -1..0
                    if (isOuter) {
                        for (dy in 0..3) {
                            if (isDoor && dy in 0..2) continue
                            val b = when {
                                abs(dx) == 4 && (dz == -2 || dz == 4) -> BlockType.WOOD_LOG
                                dy in 1..2 && (abs(dx) == 2 || dz == 1) -> BlockType.GLASS
                                else -> BlockType.BRICK
                            }
                            put(wx, baseY + dy, wz, b)
                        }
                    }
                }

                // Stepped Pitched Wooden Roof covering porch + house (dx -5..5, dz -4..5)
                if (dx in -5..5 && dz in -4..5) {
                    val roofStep = (5 - abs(dx)).coerceIn(0, 4)
                    put(wx, baseY + 4 + roofStep, wz, BlockType.PLANKS)
                }
            }
        }
        put(cx - 2, baseY + 2, cz - 4, BlockType.TORCH)
        put(cx + 2, baseY + 2, cz - 4, BlockType.TORCH)
        put(cx - 5, baseY, cz - 5, BlockType.FLOWER_SUNFLOWER)
        put(cx + 5, baseY, cz - 5, BlockType.FLOWER_PINK)
    }

    /**
     * Ready-to-Build Walled Residential Real Estate Plot:
     * Flat leveled grass turf, brick perimeter wall, golden gate pillars & street lamps.
     */
    private fun stampResidentialPlot(
        cx: Int,
        cz: Int,
        baseY: Int,
        prep: (Int, Int, Int, Int) -> Unit,
        put: (Int, Int, Int, BlockType) -> Unit
    ) {
        val r = 6
        for (dx in -r..r) {
            for (dz in -r..r) {
                val wx = cx + dx
                val wz = cz + dz
                prep(wx, wz, baseY, baseY + 6)
                val isBorder = abs(dx) == r || abs(dz) == r
                val isCorner = abs(dx) == r && abs(dz) == r
                val isGate = dz == r && dx in -1..1
                put(wx, baseY - 1, wz, if (isGate) BlockType.SANDSTONE else BlockType.GRASS)

                if (isCorner) {
                    for (dy in 0..2) put(wx, baseY + dy, wz, BlockType.GOLD_ORE)
                    put(wx, baseY + 3, wz, BlockType.TORCH)
                } else if (isBorder && !isGate) {
                    put(wx, baseY, wz, BlockType.BRICK)
                    put(wx, baseY + 1, wz, BlockType.SANDSTONE)
                }
            }
        }
        // Corner landscaping flowers inside the plot
        put(cx - 4, baseY, cz - 4, BlockType.FLOWER_RED)
        put(cx + 4, baseY, cz - 4, BlockType.FLOWER_PINK)
        put(cx - 4, baseY, cz + 4, BlockType.FLOWER_SUNFLOWER)
        put(cx + 4, baseY, cz + 4, BlockType.FLOWER_PURPLE)
    }
}
