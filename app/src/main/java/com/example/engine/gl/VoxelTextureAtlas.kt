package com.example.engine.gl

import android.graphics.Bitmap
import android.graphics.Color
import com.example.data.model.BlockType
import kotlin.math.abs
import kotlin.random.Random

/**
 * Generates crisp 16x16 procedural pixel-art block textures into a 256x256 OpenGL texture atlas.
 * Zero external image assets required.
 */
object VoxelTextureAtlas {
    const val TILE_SIZE = 16
    const val GRID_TILES = 16
    const val ATLAS_SIZE = TILE_SIZE * GRID_TILES // 256x256

    // Tile indices in the 16x16 atlas
    private const val TILE_GRASS_TOP = 0
    private const val TILE_GRASS_SIDE = 1
    private const val TILE_DIRT = 2
    private const val TILE_STONE = 3
    private const val TILE_COBBLESTONE = 4
    private const val TILE_WOOD_SIDE = 5
    private const val TILE_WOOD_TOP = 6
    private const val TILE_PLANKS = 7
    private const val TILE_LEAVES = 8
    private const val TILE_SAND = 9
    private const val TILE_SANDSTONE = 10
    private const val TILE_WATER = 11
    private const val TILE_BRICK = 12
    private const val TILE_GLASS = 13
    private const val TILE_SNOW = 14
    private const val TILE_ICE = 15
    private const val TILE_LAVA = 16
    private const val TILE_COAL_ORE = 17
    private const val TILE_IRON_ORE = 18
    private const val TILE_GOLD_ORE = 19
    private const val TILE_DIAMOND_ORE = 20
    private const val TILE_OBSIDIAN = 21
    private const val TILE_TORCH = 22
    private const val TILE_BEDROCK = 23
    private const val TILE_TALL_GRASS = 24
    private const val TILE_FLOWER_RED = 25
    private const val TILE_FLOWER_GOLD = 26
    private const val TILE_FLOWER_BLUE = 27
    private const val TILE_WATERFALL = 28
    private const val TILE_FLOWER_PINK = 29
    private const val TILE_FLOWER_PURPLE = 30
    private const val TILE_FLOWER_SUNFLOWER = 31
    private const val TILE_CHERRY_LEAVES = 32

    data class TileUV(val u0: Float, val v0: Float, val u1: Float, val v1: Float)

    fun uvFor(block: BlockType, faceDir: Int): TileUV {
        // faceDir: 0 = TOP (+Y), 1 = BOTTOM (-Y), 2..5 = SIDES
        val tileIndex = when (block) {
            BlockType.GRASS -> when (faceDir) {
                0 -> TILE_GRASS_TOP
                1 -> TILE_DIRT
                else -> TILE_GRASS_SIDE
            }
            BlockType.DIRT -> TILE_DIRT
            BlockType.STONE -> TILE_STONE
            BlockType.COBBLESTONE -> TILE_COBBLESTONE
            BlockType.WOOD_LOG -> if (faceDir == 0 || faceDir == 1) TILE_WOOD_TOP else TILE_WOOD_SIDE
            BlockType.PLANKS -> TILE_PLANKS
            BlockType.LEAVES -> TILE_LEAVES
            BlockType.SAND -> TILE_SAND
            BlockType.SANDSTONE -> TILE_SANDSTONE
            BlockType.WATER -> TILE_WATER
            BlockType.LAVA -> TILE_LAVA
            BlockType.SNOW -> TILE_SNOW
            BlockType.ICE -> TILE_ICE
            BlockType.BRICK -> TILE_BRICK
            BlockType.GLASS -> TILE_GLASS
            BlockType.TORCH -> TILE_TORCH
            BlockType.COAL_ORE -> TILE_COAL_ORE
            BlockType.IRON_ORE -> TILE_IRON_ORE
            BlockType.GOLD_ORE -> TILE_GOLD_ORE
            BlockType.DIAMOND_ORE -> TILE_DIAMOND_ORE
            BlockType.OBSIDIAN -> TILE_OBSIDIAN
            BlockType.BEDROCK -> TILE_BEDROCK
            BlockType.TALL_GRASS -> TILE_TALL_GRASS
            BlockType.FLOWER_RED -> TILE_FLOWER_RED
            BlockType.FLOWER_GOLD -> TILE_FLOWER_GOLD
            BlockType.FLOWER_BLUE -> TILE_FLOWER_BLUE
            BlockType.WATERFALL -> TILE_WATERFALL
            BlockType.FLOWER_PINK -> TILE_FLOWER_PINK
            BlockType.FLOWER_PURPLE -> TILE_FLOWER_PURPLE
            BlockType.FLOWER_SUNFLOWER -> TILE_FLOWER_SUNFLOWER
            BlockType.CHERRY_LEAVES -> TILE_CHERRY_LEAVES
            BlockType.AIR -> TILE_DIRT
        }
        val col = tileIndex % GRID_TILES
        val row = tileIndex / GRID_TILES
        val pad = 0.0015f // tiny inset prevents UV seam bleeding
        val step = 1f / GRID_TILES
        return TileUV(
            u0 = col * step + pad,
            v0 = row * step + pad,
            u1 = (col + 1) * step - pad,
            v1 = (row + 1) * step - pad
        )
    }

    fun buildAtlasBitmap(): Bitmap {
        val bmp = Bitmap.createBitmap(ATLAS_SIZE, ATLAS_SIZE, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.TRANSPARENT)

        drawNoiseTile(bmp, TILE_GRASS_TOP, 0xFF56B840.toInt(), 0xFF429C2E.toInt(), 0xFF6DD453.toInt(), 101)
        drawGrassSideTile(bmp, TILE_GRASS_SIDE)
        drawNoiseTile(bmp, TILE_DIRT, 0xFF8A5E3B.toInt(), 0xFF704A2C.toInt(), 0xFF9C6D47.toInt(), 102)
        drawStoneTile(bmp, TILE_STONE, 103)
        drawCobblestoneTile(bmp, TILE_COBBLESTONE)
        drawWoodSideTile(bmp, TILE_WOOD_SIDE)
        drawWoodTopTile(bmp, TILE_WOOD_TOP)
        drawPlanksTile(bmp, TILE_PLANKS)
        drawLeavesTile(bmp, TILE_LEAVES)
        drawNoiseTile(bmp, TILE_SAND, 0xFFE6D292.toInt(), 0xFFD4BE7B.toInt(), 0xFFF2E2AD.toInt(), 109)
        drawSandstoneTile(bmp, TILE_SANDSTONE)
        drawWaterTile(bmp, TILE_WATER)
        drawBrickTile(bmp, TILE_BRICK)
        drawGlassTile(bmp, TILE_GLASS)
        drawNoiseTile(bmp, TILE_SNOW, 0xFFF2FAFC.toInt(), 0xFFDAEBF2.toInt(), 0xFFFFFFFF.toInt(), 114)
        drawIceTile(bmp, TILE_ICE)
        drawLavaTile(bmp, TILE_LAVA)
        drawOreTile(bmp, TILE_COAL_ORE, 0xFF1A1E21.toInt(), 0xFF2B3136.toInt(), 117)
        drawOreTile(bmp, TILE_IRON_ORE, 0xFFDDA885.toInt(), 0xFFF2C8A9.toInt(), 118)
        drawOreTile(bmp, TILE_GOLD_ORE, 0xFFFFC928.toInt(), 0xFFFFEA70.toInt(), 119)
        drawOreTile(bmp, TILE_DIAMOND_ORE, 0xFF2CE8FF.toInt(), 0xFFA8F7FF.toInt(), 120)
        drawNoiseTile(bmp, TILE_OBSIDIAN, 0xFF231938.toInt(), 0xFF140D24.toInt(), 0xFF563494.toInt(), 121)
        drawTorchTile(bmp, TILE_TORCH)
        drawNoiseTile(bmp, TILE_BEDROCK, 0xFF33363B.toInt(), 0xFF181A1D.toInt(), 0xFF545961.toInt(), 123)
        drawTallGrassTile(bmp, TILE_TALL_GRASS)
        drawFlowerTile(bmp, TILE_FLOWER_RED, 0xFFFF2B54.toInt(), 0xFFFF889F.toInt(), 0xFFB81233.toInt())
        drawFlowerTile(bmp, TILE_FLOWER_GOLD, 0xFFFFB703.toInt(), 0xFFFFEA70.toInt(), 0xFFE67E00.toInt())
        drawFlowerTile(bmp, TILE_FLOWER_BLUE, 0xFF2CC8FF.toInt(), 0xFFB3F0FF.toInt(), 0xFF1280C4.toInt())
        drawWaterfallTile(bmp, TILE_WATERFALL)
        drawFlowerTile(bmp, TILE_FLOWER_PINK, 0xFFFF5CA8.toInt(), 0xFFFFB8DA.toInt(), 0xFFD42479.toInt())
        drawFlowerTile(bmp, TILE_FLOWER_PURPLE, 0xFFB36BFF.toInt(), 0xFFE3C2FF.toInt(), 0xFF7B2ED6.toInt())
        drawSunflowerTile(bmp, TILE_FLOWER_SUNFLOWER)
        drawCherryLeavesTile(bmp, TILE_CHERRY_LEAVES)

        return bmp
    }

    private fun setTilePixel(bmp: Bitmap, tileIndex: Int, x: Int, y: Int, color: Int) {
        val baseX = (tileIndex % GRID_TILES) * TILE_SIZE
        val baseY = (tileIndex / GRID_TILES) * TILE_SIZE
        bmp.setPixel(baseX + x, baseY + y, color)
    }

    private fun drawNoiseTile(bmp: Bitmap, tile: Int, mid: Int, dark: Int, light: Int, seed: Int) {
        val rng = Random(seed)
        for (y in 0 until 16) {
            for (x in 0 until 16) {
                val r = rng.nextInt(10)
                val col = when {
                    r < 3 -> dark
                    r < 7 -> mid
                    else -> light
                }
                setTilePixel(bmp, tile, x, y, col)
            }
        }
    }

    private fun drawGrassSideTile(bmp: Bitmap, tile: Int) {
        val rng = Random(202)
        for (x in 0 until 16) {
            val overhang = 3 + rng.nextInt(3)
            for (y in 0 until 16) {
                val col = if (y < overhang) {
                    if (rng.nextBoolean()) 0xFF56B840.toInt() else 0xFF429C2E.toInt()
                } else if (y == overhang && rng.nextInt(3) == 0) {
                    0xFF377D26.toInt()
                } else {
                    when (rng.nextInt(3)) {
                        0 -> 0xFF704A2C.toInt()
                        1 -> 0xFF8A5E3B.toInt()
                        else -> 0xFF9C6D47.toInt()
                    }
                }
                setTilePixel(bmp, tile, x, y, col)
            }
        }
    }

    private fun drawStoneTile(bmp: Bitmap, tile: Int, seed: Int) {
        val rng = Random(seed)
        for (y in 0 until 16) {
            val band = if (y % 4 == 0) -1 else 0
            for (x in 0 until 16) {
                val r = rng.nextInt(10) + band
                val col = when {
                    r < 2 -> 0xFF6B7378.toInt()
                    r < 7 -> 0xFF858E94.toInt()
                    else -> 0xFF9CA5AB.toInt()
                }
                setTilePixel(bmp, tile, x, y, col)
            }
        }
    }

    private fun drawCobblestoneTile(bmp: Bitmap, tile: Int) {
        val rng = Random(303)
        for (y in 0 until 16) {
            for (x in 0 until 16) {
                val mortar = (y % 4 == 0) || ((x + (y / 4) * 3) % 5 == 0)
                val col = if (mortar) {
                    0xFF4B5156.toInt()
                } else {
                    when (rng.nextInt(3)) {
                        0 -> 0xFF70777C.toInt()
                        1 -> 0xFF878F95.toInt()
                        else -> 0xFF9BA3A9.toInt()
                    }
                }
                setTilePixel(bmp, tile, x, y, col)
            }
        }
    }

    private fun drawWoodSideTile(bmp: Bitmap, tile: Int) {
        val rng = Random(404)
        for (x in 0 until 16) {
            val ridge = x % 3 == 0
            for (y in 0 until 16) {
                val col = when {
                    ridge && rng.nextInt(4) != 0 -> 0xFF52371F.toInt()
                    rng.nextBoolean() -> 0xFF6E4B2B.toInt()
                    else -> 0xFF7F5834.toInt()
                }
                setTilePixel(bmp, tile, x, y, col)
            }
        }
    }

    private fun drawWoodTopTile(bmp: Bitmap, tile: Int) {
        for (y in 0 until 16) {
            for (x in 0 until 16) {
                val border = x == 0 || y == 0 || x == 15 || y == 15
                val ring = maxOf(abs(x - 7.5f), abs(y - 7.5f)).toInt()
                val col = when {
                    border -> 0xFF573B21.toInt()
                    ring % 2 == 0 -> 0xFFC59F6B.toInt()
                    else -> 0xFFA6804E.toInt()
                }
                setTilePixel(bmp, tile, x, y, col)
            }
        }
    }

    private fun drawPlanksTile(bmp: Bitmap, tile: Int) {
        val rng = Random(505)
        for (y in 0 until 16) {
            for (x in 0 until 16) {
                val seamH = y % 4 == 0
                val seamV = (y / 4 % 2 == 0 && x == 8) || (y / 4 % 2 == 1 && x == 3)
                val col = when {
                    seamH || seamV -> 0xFF855D32.toInt()
                    rng.nextInt(4) == 0 -> 0xFFB88D54.toInt()
                    else -> 0xFFCCA166.toInt()
                }
                setTilePixel(bmp, tile, x, y, col)
            }
        }
    }

    private fun drawLeavesTile(bmp: Bitmap, tile: Int) {
        val rng = Random(606)
        for (y in 0 until 16) {
            for (x in 0 until 16) {
                val r = rng.nextInt(10)
                val col = when {
                    r < 2 -> 0xFF1D5922.toInt()
                    r < 5 -> 0xFF2D7D32.toInt()
                    r < 8 -> 0xFF3E9E44.toInt()
                    else -> 0xFF5EC462.toInt()
                }
                setTilePixel(bmp, tile, x, y, col)
            }
        }
    }

    private fun drawSandstoneTile(bmp: Bitmap, tile: Int) {
        val rng = Random(707)
        for (y in 0 until 16) {
            for (x in 0 until 16) {
                val col = when {
                    y < 3 -> 0xFFEBDA9F.toInt()
                    y == 3 || y == 11 -> 0xFFB3985B.toInt()
                    rng.nextBoolean() -> 0xFFD9C182.toInt()
                    else -> 0xFFCBB274.toInt()
                }
                setTilePixel(bmp, tile, x, y, col)
            }
        }
    }

    private fun drawWaterTile(bmp: Bitmap, tile: Int) {
        for (y in 0 until 16) {
            for (x in 0 until 16) {
                val wave = ((x + y * 2) % 7 == 0)
                val col = if (wave) 0xCC6BD4FF.toInt() else 0xBB2392DC.toInt()
                setTilePixel(bmp, tile, x, y, col)
            }
        }
    }

    private fun drawBrickTile(bmp: Bitmap, tile: Int) {
        val rng = Random(808)
        for (y in 0 until 16) {
            val row = y / 4
            val offset = if (row % 2 == 0) 0 else 4
            for (x in 0 until 16) {
                val mortar = (y % 4 == 0) || ((x + offset) % 8 == 0)
                val col = if (mortar) {
                    0xFFD1C6BA.toInt()
                } else {
                    when (rng.nextInt(3)) {
                        0 -> 0xFF9C4132.toInt()
                        1 -> 0xFFB55140.toInt()
                        else -> 0xFFC45F4C.toInt()
                    }
                }
                setTilePixel(bmp, tile, x, y, col)
            }
        }
    }

    private fun drawGlassTile(bmp: Bitmap, tile: Int) {
        for (y in 0 until 16) {
            for (x in 0 until 16) {
                val border = x == 0 || y == 0 || x == 15 || y == 15
                val glint = (x + y == 6) || (x + y == 7 && x in 2..5) || (x + y == 22 && x in 10..13)
                val col = when {
                    border -> 0xDD8FD9EE.toInt()
                    glint -> 0xEEFFFFFF.toInt()
                    else -> 0x44B5ECFC.toInt()
                }
                setTilePixel(bmp, tile, x, y, col)
            }
        }
    }

    private fun drawIceTile(bmp: Bitmap, tile: Int) {
        for (y in 0 until 16) {
            for (x in 0 until 16) {
                val streak = (x - y == 3 || x - y == -5)
                val col = if (streak) 0xDDD4F7FF.toInt() else 0xBB7CD9F5.toInt()
                setTilePixel(bmp, tile, x, y, col)
            }
        }
    }

    private fun drawLavaTile(bmp: Bitmap, tile: Int) {
        val rng = Random(909)
        for (y in 0 until 16) {
            for (x in 0 until 16) {
                val r = rng.nextInt(10)
                val col = when {
                    r < 2 -> 0xFFFFEA61.toInt()
                    r < 6 -> 0xFFFF6D14.toInt()
                    else -> 0xFFD93506.toInt()
                }
                setTilePixel(bmp, tile, x, y, col)
            }
        }
    }

    private fun drawOreTile(bmp: Bitmap, tile: Int, oreDark: Int, oreBright: Int, seed: Int) {
        drawStoneTile(bmp, tile, seed)
        val rng = Random(seed + 77)
        repeat(10) {
            val ox = 1 + rng.nextInt(13)
            val oy = 1 + rng.nextInt(13)
            setTilePixel(bmp, tile, ox, oy, oreDark)
            setTilePixel(bmp, tile, ox + 1, oy, oreBright)
            if (rng.nextBoolean()) setTilePixel(bmp, tile, ox, oy + 1, oreBright)
        }
    }

    private fun drawTorchTile(bmp: Bitmap, tile: Int) {
        for (y in 0 until 16) {
            for (x in 0 until 16) {
                val col = when {
                    y in 1..4 && x in 6..9 -> if (y in 2..3 && x in 7..8) 0xFFFFF59D.toInt() else 0xFFFF9100.toInt()
                    y in 5..15 && x in 6..9 -> if (x == 6) 0xFF5D3B1A.toInt() else 0xFF8C5B2B.toInt()
                    else -> 0xFFFFD54F.toInt() // warm glowing lantern tile
                }
                setTilePixel(bmp, tile, x, y, col)
            }
        }
    }

    private fun drawTallGrassTile(bmp: Bitmap, tile: Int) {
        val blades = listOf(2 to 6, 4 to 4, 6 to 2, 8 to 3, 10 to 5, 12 to 3, 14 to 7)
        for ((bx, topY) in blades) {
            for (y in topY until 16) {
                val lean = if (y < 9) (if (bx < 8) -1 else 1) else 0
                val px = (bx + lean).coerceIn(0, 15)
                val col = when {
                    y <= topY + 2 -> 0xFF7BE86E.toInt()
                    y < 11 -> 0xFF4CC942.toInt()
                    else -> 0xFF2D8A29.toInt()
                }
                setTilePixel(bmp, tile, px, y, col)
                if (y > 9 && px + 1 < 16) {
                    setTilePixel(bmp, tile, px + 1, y, 0xFF39A333.toInt())
                }
            }
        }
    }

    private fun drawFlowerTile(bmp: Bitmap, tile: Int, petalMain: Int, petalLight: Int, petalDark: Int) {
        // Green stem and leaves
        for (y in 7 until 16) {
            setTilePixel(bmp, tile, 7, y, 0xFF2E8C2E.toInt())
            setTilePixel(bmp, tile, 8, y, 0xFF3EA838.toInt())
        }
        setTilePixel(bmp, tile, 5, 12, 0xFF3EA838.toInt())
        setTilePixel(bmp, tile, 6, 11, 0xFF4EC246.toInt())
        setTilePixel(bmp, tile, 9, 13, 0xFF3EA838.toInt())
        setTilePixel(bmp, tile, 10, 12, 0xFF4EC246.toInt())

        // Blossom head (y = 2..7, x = 4..11)
        for (y in 2..7) {
            for (x in 5..10) {
                val dx = abs(x - 7.5f)
                val dy = abs(y - 4.5f)
                if (dx + dy <= 3.6f) {
                    val col = when {
                        dx <= 0.8f && dy <= 0.8f -> 0xFFFFF59D.toInt() // pollen core
                        y <= 3 -> petalLight
                        y >= 6 -> petalDark
                        else -> petalMain
                    }
                    setTilePixel(bmp, tile, x, y, col)
                }
            }
        }
    }

    private fun drawWaterfallTile(bmp: Bitmap, tile: Int) {
        for (y in 0 until 16) {
            for (x in 0 until 16) {
                val foamStreak = ((x * 3 + y) % 5 == 0) || (x in listOf(3, 8, 13) && y % 3 != 0)
                val col = when {
                    foamStreak -> 0xDDE6FAFF.toInt()
                    x % 2 == 0 -> 0xCC3CB8F6.toInt()
                    else -> 0xCC2193DF.toInt()
                }
                setTilePixel(bmp, tile, x, y, col)
            }
        }
    }

    private fun drawSunflowerTile(bmp: Bitmap, tile: Int) {
        // Tall sturdy stem + side leaves
        for (y in 6 until 16) {
            setTilePixel(bmp, tile, 7, y, 0xFF2E8C2E.toInt())
            setTilePixel(bmp, tile, 8, y, 0xFF42B33C.toInt())
        }
        setTilePixel(bmp, tile, 5, 11, 0xFF42B33C.toInt())
        setTilePixel(bmp, tile, 6, 10, 0xFF5CE055.toInt())
        setTilePixel(bmp, tile, 9, 12, 0xFF42B33C.toInt())
        setTilePixel(bmp, tile, 10, 11, 0xFF5CE055.toInt())

        // Wide golden sunflower bloom with warm dark brown seed center
        for (y in 1..8) {
            for (x in 4..11) {
                val dx = abs(x - 7.5f)
                val dy = abs(y - 4.5f)
                val dist = dx + dy
                if (dist <= 4.3f) {
                    val col = when {
                        dx <= 1.2f && dy <= 1.2f -> if ((x + y) % 2 == 0) 0xFF593315.toInt() else 0xFF7A4820.toInt()
                        y <= 3 -> 0xFFFFF176.toInt()
                        y >= 7 -> 0xFFFFA000.toInt()
                        else -> 0xFFFFD600.toInt()
                    }
                    setTilePixel(bmp, tile, x, y, col)
                }
            }
        }
    }

    private fun drawCherryLeavesTile(bmp: Bitmap, tile: Int) {
        val rng = Random(676)
        for (y in 0 until 16) {
            for (x in 0 until 16) {
                val r = rng.nextInt(10)
                val col = when {
                    r < 2 -> 0xFFD96499.toInt()
                    r < 5 -> 0xFFF48FB1.toInt()
                    r < 8 -> 0xFFFFB2CE.toInt()
                    else -> 0xFFFFE0EC.toInt()
                }
                setTilePixel(bmp, tile, x, y, col)
            }
        }
    }
}
