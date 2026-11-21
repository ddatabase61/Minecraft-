package com.example.data.model

import androidx.annotation.Keep
import androidx.compose.ui.graphics.Color
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class BlockCategory(val label: String) {
    TERRAIN("Terrain"),
    BUILDING("Building"),
    DECOR("Gardens & Flora"),
    ORES("Ores & Gems"),
    LIGHT_FLUID("Light & Fluids")
}

enum class BlockType(
    val id: Int,
    val displayName: String,
    val category: BlockCategory,
    val topColor: Color,
    val leftColor: Color,
    val rightColor: Color,
    val accentColor: Color,
    val minPickaxeTier: Int = 0,
    val unbreakable: Boolean = false,
    val emitsLight: Boolean = false,
    val translucent: Boolean = false,
    val crossPlant: Boolean = false,
    val description: String = "",
    val biomeSource: String = "All Biomes"
) {
    AIR(
        id = 0,
        displayName = "Air",
        category = BlockCategory.TERRAIN,
        topColor = Color.Transparent,
        leftColor = Color.Transparent,
        rightColor = Color.Transparent,
        accentColor = Color.Transparent,
        translucent = true,
        description = "Empty space."
    ),
    GRASS(
        id = 1,
        displayName = "Grass Block",
        category = BlockCategory.TERRAIN,
        topColor = Color(0xFF59B843),
        leftColor = Color(0xFF7D5636),
        rightColor = Color(0xFF634328),
        accentColor = Color(0xFF439930),
        description = "Lush turf over fertile soil. Forms rolling valley hills.",
        biomeSource = "Emerald Valley"
    ),
    DIRT(
        id = 2,
        displayName = "Rich Dirt",
        category = BlockCategory.TERRAIN,
        topColor = Color(0xFF8C623D),
        leftColor = Color(0xFF755030),
        rightColor = Color(0xFF5D3E24),
        accentColor = Color(0xFF6B4729),
        description = "Easy-to-shape earth for landscaping and gardens.",
        biomeSource = "Emerald Valley, Frost Peaks"
    ),
    STONE(
        id = 3,
        displayName = "Smooth Stone",
        category = BlockCategory.TERRAIN,
        topColor = Color(0xFF8C9496),
        leftColor = Color(0xFF727A7D),
        rightColor = Color(0xFF5B6266),
        accentColor = Color(0xFFA2AAAC),
        minPickaxeTier = 1,
        description = "Solid bedrock-adjacent stone. Mines into Cobblestone.",
        biomeSource = "Underground (All Biomes)"
    ),
    COBBLESTONE(
        id = 4,
        displayName = "Cobblestone",
        category = BlockCategory.BUILDING,
        topColor = Color(0xFF7F868A),
        leftColor = Color(0xFF666D70),
        rightColor = Color(0xFF4F5559),
        accentColor = Color(0xFF9AA2A6),
        minPickaxeTier = 1,
        description = "Rugged fractured masonry ideal for castles and foundations.",
        biomeSource = "Mined from Stone"
    ),
    WOOD_LOG(
        id = 5,
        displayName = "Oak Timber",
        category = BlockCategory.BUILDING,
        topColor = Color(0xFFA88556),
        leftColor = Color(0xFF6D4B2B),
        rightColor = Color(0xFF54381E),
        accentColor = Color(0xFF8C663F),
        description = "Sturdy tree trunk. Craft into four Oak Planks.",
        biomeSource = "Emerald Valley, Frost Peaks"
    ),
    PLANKS(
        id = 6,
        displayName = "Oak Planks",
        category = BlockCategory.BUILDING,
        topColor = Color(0xFFC99D62),
        leftColor = Color(0xFFB0844D),
        rightColor = Color(0xFF916A3B),
        accentColor = Color(0xFF78552C),
        description = "Warm milled timber boards for cabins, floors, and bridges.",
        biomeSource = "Crafted from Oak Timber"
    ),
    LEAVES(
        id = 7,
        displayName = "Canopy Leaves",
        category = BlockCategory.TERRAIN,
        topColor = Color(0xFF3E9C43),
        leftColor = Color(0xFF2D7D32),
        rightColor = Color(0xFF206125),
        accentColor = Color(0xFF5BC25F),
        description = "Vibrant voxel foliage for trees, hedges, and sky gardens.",
        biomeSource = "Emerald Valley, Frost Peaks"
    ),
    SAND(
        id = 8,
        displayName = "Golden Sand",
        category = BlockCategory.TERRAIN,
        topColor = Color(0xFFE7D393),
        leftColor = Color(0xFFD1BC7B),
        rightColor = Color(0xFFB5A062),
        accentColor = Color(0xFFF2E2AD),
        description = "Sun-baked grains. Smelt with Coal to forge clear Glass.",
        biomeSource = "Desert Dunes, Riverbanks"
    ),
    SANDSTONE(
        id = 9,
        displayName = "Carved Sandstone",
        category = BlockCategory.BUILDING,
        topColor = Color(0xFFDCC484),
        leftColor = Color(0xFFC2A869),
        rightColor = Color(0xFFA68D51),
        accentColor = Color(0xFFEBDA9F),
        minPickaxeTier = 1,
        description = "Chiseled desert stone used in ancient oasis shrines.",
        biomeSource = "Desert Dunes / Crafted"
    ),
    WATER(
        id = 10,
        displayName = "Crystal Water",
        category = BlockCategory.LIGHT_FLUID,
        topColor = Color(0xCC2CA3E8),
        leftColor = Color(0xCC1E80BD),
        rightColor = Color(0xCC146396),
        accentColor = Color(0xFF7AD8FF),
        translucent = true,
        description = "Shimmering clear water for rivers, moats, and waterfalls.",
        biomeSource = "Emerald Valley, Oasis"
    ),
    LAVA(
        id = 11,
        displayName = "Molten Lava",
        category = BlockCategory.LIGHT_FLUID,
        topColor = Color(0xFFFF6A1A),
        leftColor = Color(0xFFE0450B),
        rightColor = Color(0xFFB52D04),
        accentColor = Color(0xFFFFD15C),
        emitsLight = true,
        description = "Glowing volcanic magma that radiates fiery warmth at night.",
        biomeSource = "Volcanic Badlands"
    ),
    SNOW(
        id = 12,
        displayName = "Alpine Snow",
        category = BlockCategory.TERRAIN,
        topColor = Color(0xFFF3FAFC),
        leftColor = Color(0xFFD5E6EE),
        rightColor = Color(0xFFB7CEDB),
        accentColor = Color(0xFFFFFFFF),
        description = "Fresh powder blanketing high mountain ridges.",
        biomeSource = "Frost Peaks"
    ),
    ICE(
        id = 13,
        displayName = "Glacial Ice",
        category = BlockCategory.LIGHT_FLUID,
        topColor = Color(0xCC8EE3FA),
        leftColor = Color(0xCC6BC7E3),
        rightColor = Color(0xCC50ACC9),
        accentColor = Color(0xFFD6F7FF),
        translucent = true,
        description = "Translucent frozen crystal ideal for alpine palaces.",
        biomeSource = "Frost Peaks / Crafted"
    ),
    BRICK(
        id = 14,
        displayName = "Terracotta Brick",
        category = BlockCategory.BUILDING,
        topColor = Color(0xFFB85644),
        leftColor = Color(0xFF9C4233),
        rightColor = Color(0xFF7D3124),
        accentColor = Color(0xFFD4C5B6),
        minPickaxeTier = 1,
        description = "Kiln-fired masonry bricks for watchtowers and hearths.",
        biomeSource = "Crafted / Badlands Ruins"
    ),
    GLASS(
        id = 15,
        displayName = "Crystal Glass",
        category = BlockCategory.BUILDING,
        topColor = Color(0x99C7F2FF),
        leftColor = Color(0x8899D9ED),
        rightColor = Color(0x887DC2D9),
        accentColor = Color(0xFFFFFFFF),
        translucent = true,
        description = "Clear architectural glass panels for sunlit windows.",
        biomeSource = "Crafted from Sand + Coal"
    ),
    TORCH(
        id = 16,
        displayName = "Forge Torch",
        category = BlockCategory.LIGHT_FLUID,
        topColor = Color(0xFFFFCF54),
        leftColor = Color(0xFF8C5B2B),
        rightColor = Color(0xFF6B431D),
        accentColor = Color(0xFFFF8C24),
        emitsLight = true,
        translucent = true,
        description = "Casts warm flickering light across nearby blocks at night.",
        biomeSource = "Crafted from Planks + Coal"
    ),
    COAL_ORE(
        id = 17,
        displayName = "Coal Ore",
        category = BlockCategory.ORES,
        topColor = Color(0xFF7E8587),
        leftColor = Color(0xFF646B6E),
        rightColor = Color(0xFF4F5559),
        accentColor = Color(0xFF1D2224),
        minPickaxeTier = 1,
        description = "Essential dark fuel for crafting Torches, Glass, and Bricks.",
        biomeSource = "Underground Veins"
    ),
    IRON_ORE(
        id = 18,
        displayName = "Iron Ore",
        category = BlockCategory.ORES,
        topColor = Color(0xFF858C8F),
        leftColor = Color(0xFF6D7478),
        rightColor = Color(0xFF565C61),
        accentColor = Color(0xFFE0B494),
        minPickaxeTier = 1,
        description = "Raw metallic ore needed to forge the Tier-2 Iron Pickaxe.",
        biomeSource = "Mid-Depth Caverns"
    ),
    GOLD_ORE(
        id = 19,
        displayName = "Gold Ore",
        category = BlockCategory.ORES,
        topColor = Color(0xFF82898C),
        leftColor = Color(0xFF686F73),
        rightColor = Color(0xFF53595E),
        accentColor = Color(0xFFFFD53D),
        minPickaxeTier = 2,
        description = "Lustrous golden seams. Requires Iron Pickaxe in Survival.",
        biomeSource = "Deep Caverns & Desert"
    ),
    DIAMOND_ORE(
        id = 20,
        displayName = "Diamond Ore",
        category = BlockCategory.ORES,
        topColor = Color(0xFF7B848A),
        leftColor = Color(0xFF626A70),
        rightColor = Color(0xFF4C5359),
        accentColor = Color(0xFF35E8FF),
        minPickaxeTier = 2,
        emitsLight = true,
        description = "Radiant cyan crystal ore used to craft the Diamond Pickaxe.",
        biomeSource = "Deep Bedrock Layers"
    ),
    OBSIDIAN(
        id = 21,
        displayName = "Obsidian",
        category = BlockCategory.BUILDING,
        topColor = Color(0xFF2D2245),
        leftColor = Color(0xFF1F1633),
        rightColor = Color(0xFF150E24),
        accentColor = Color(0xFF7C4DFF),
        minPickaxeTier = 3,
        description = "Ultra-dense volcanic glass. Requires Diamond Pickaxe in Survival.",
        biomeSource = "Volcanic Badlands / Crafted"
    ),
    BEDROCK(
        id = 22,
        displayName = "Bedrock",
        category = BlockCategory.TERRAIN,
        topColor = Color(0xFF3A3E42),
        leftColor = Color(0xFF2B2E31),
        rightColor = Color(0xFF1E2022),
        accentColor = Color(0xFF545A61),
        unbreakable = true,
        description = "Indestructible foundation anchoring the bottom of every world.",
        biomeSource = "Layer Y=0"
    ),
    TALL_GRASS(
        id = 23,
        displayName = "Wild Grass",
        category = BlockCategory.TERRAIN,
        topColor = Color(0xFF53C947),
        leftColor = Color(0xFF3EA834),
        rightColor = Color(0xFF2E8526),
        accentColor = Color(0xFF7BE86E),
        translucent = true,
        crossPlant = true,
        description = "Lush swaying prairie blades found across jungles and valleys.",
        biomeSource = "Emerald Valley, Gardens"
    ),
    FLOWER_RED(
        id = 24,
        displayName = "Crimson Rose",
        category = BlockCategory.DECOR,
        topColor = Color(0xFFFF3B5C),
        leftColor = Color(0xFFD91E40),
        rightColor = Color(0xFF3DA635),
        accentColor = Color(0xFFFF8095),
        translucent = true,
        crossPlant = true,
        description = "Fragrant red garden blossom blooming in sunlit meadows.",
        biomeSource = "Flower Gardens, Emerald Valley"
    ),
    FLOWER_GOLD(
        id = 25,
        displayName = "Golden Marigold",
        category = BlockCategory.DECOR,
        topColor = Color(0xFFFFC107),
        leftColor = Color(0xFFFF9800),
        rightColor = Color(0xFF3DA635),
        accentColor = Color(0xFFFFEB3B),
        translucent = true,
        crossPlant = true,
        description = "Bright golden marigold petals cherished across village gardens.",
        biomeSource = "Flower Gardens, Emerald Valley"
    ),
    FLOWER_BLUE(
        id = 26,
        displayName = "Sky Lotus",
        category = BlockCategory.DECOR,
        topColor = Color(0xFF38CFFF),
        leftColor = Color(0xFF1995DB),
        rightColor = Color(0xFF2F9C42),
        accentColor = Color(0xFFA6EDFF),
        translucent = true,
        crossPlant = true,
        description = "Radiant blue orchid growing beside rivers and waterfalls.",
        biomeSource = "Waterfalls, Riverbanks"
    ),
    WATERFALL(
        id = 27,
        displayName = "Waterfall Stream",
        category = BlockCategory.TERRAIN,
        topColor = Color(0xFF7DE2FF),
        leftColor = Color(0xFF39B5F5),
        rightColor = Color(0xFF2392DC),
        accentColor = Color(0xFFE3FAFF),
        translucent = true,
        description = "Cascading aerated mountain stream with white mist foam.",
        biomeSource = "Mountain Cliffs & Rivers"
    );

    fun survivalDrop(): BlockType = when (this) {
        GRASS -> DIRT
        STONE -> COBBLESTONE
        else -> this
    }

    companion object {
        private val byId = entries.associateBy { it.id }
        val placeableBlocks: List<BlockType> = entries.filter { it != AIR && it != BEDROCK }
        val codexBlocks: List<BlockType> = entries.filter { it != AIR }

        fun fromId(id: Int): BlockType = byId[id] ?: AIR

        fun safeFromName(raw: String): BlockType =
            entries.firstOrNull { it.name == raw } ?: PLANKS
    }
}

enum class BiomeType(
    val displayName: String,
    val tagline: String,
    val accentColor: Color,
    val badgeColor: Color
) {
    EMERALD_VALLEY(
        displayName = "Emerald Valley",
        tagline = "Lush green hills, oak groves & crystal rivers",
        accentColor = Color(0xFF34E57A),
        badgeColor = Color(0xFF134027)
    ),
    DESERT_DUNES(
        displayName = "Desert Dunes",
        tagline = "Golden sand mesas, palm oasis & buried gold",
        accentColor = Color(0xFFFFC857),
        badgeColor = Color(0xFF47330E)
    ),
    FROST_PEAKS(
        displayName = "Frost Peaks",
        tagline = "Snow-capped alpine ridges, glacial lakes & diamond veins",
        accentColor = Color(0xFF4CE3FF),
        badgeColor = Color(0xFF0E3947)
    ),
    VOLCANIC_BADLANDS(
        displayName = "Volcanic Badlands",
        tagline = "Obsidian spires, magma rivers & ancient brick ruins",
        accentColor = Color(0xFFFF6E40),
        badgeColor = Color(0xFF4A1B0E)
    );

    companion object {
        fun safeFromName(raw: String): BiomeType =
            entries.firstOrNull { it.name == raw } ?: EMERALD_VALLEY
    }
}

enum class GameMode(val displayName: String, val tagline: String) {
    SURVIVAL("Survival", "Mine resources, craft tools & expand your world"),
    CREATIVE("Creative", "Unlimited blocks, instant mining & pure building");

    companion object {
        fun safeFromName(raw: String): GameMode =
            entries.firstOrNull { it.name == raw } ?: SURVIVAL
    }
}

enum class ToolMode {
    MINE,
    BUILD
}

data class CraftingRecipe(
    val id: String,
    val title: String,
    val description: String,
    val ingredients: Map<BlockType, Int>,
    val outputBlock: BlockType? = null,
    val outputCount: Int = 1,
    val unlocksPickaxeTier: Int? = null
)

val CRAFTING_RECIPES: List<CraftingRecipe> = listOf(
    CraftingRecipe(
        id = "planks_from_wood",
        title = "Oak Planks ×4",
        description = "Mill raw Oak Timber into four building planks.",
        ingredients = mapOf(BlockType.WOOD_LOG to 1),
        outputBlock = BlockType.PLANKS,
        outputCount = 4
    ),
    CraftingRecipe(
        id = "torch_craft",
        title = "Forge Torch ×4",
        description = "Illuminate caverns and castles after sunset.",
        ingredients = mapOf(BlockType.PLANKS to 1, BlockType.COAL_ORE to 1),
        outputBlock = BlockType.TORCH,
        outputCount = 4
    ),
    CraftingRecipe(
        id = "glass_smelt",
        title = "Crystal Glass ×4",
        description = "Smelt Golden Sand with Coal for sleek windows.",
        ingredients = mapOf(BlockType.SAND to 2, BlockType.COAL_ORE to 1),
        outputBlock = BlockType.GLASS,
        outputCount = 4
    ),
    CraftingRecipe(
        id = "brick_kiln",
        title = "Terracotta Brick ×4",
        description = "Kiln-fire Cobblestone and Dirt into red masonry.",
        ingredients = mapOf(BlockType.COBBLESTONE to 2, BlockType.DIRT to 1),
        outputBlock = BlockType.BRICK,
        outputCount = 4
    ),
    CraftingRecipe(
        id = "sandstone_carve",
        title = "Carved Sandstone ×2",
        description = "Compress loose Sand into architectural blocks.",
        ingredients = mapOf(BlockType.SAND to 2),
        outputBlock = BlockType.SANDSTONE,
        outputCount = 2
    ),
    CraftingRecipe(
        id = "smooth_stone_forge",
        title = "Smooth Stone ×2",
        description = "Refine rough Cobblestone using Coal heat.",
        ingredients = mapOf(BlockType.COBBLESTONE to 2, BlockType.COAL_ORE to 1),
        outputBlock = BlockType.STONE,
        outputCount = 2
    ),
    CraftingRecipe(
        id = "glacial_ice",
        title = "Glacial Ice ×2",
        description = "Pack fresh Snow into translucent ice blocks.",
        ingredients = mapOf(BlockType.SNOW to 2),
        outputBlock = BlockType.ICE,
        outputCount = 2
    ),
    CraftingRecipe(
        id = "obsidian_cast",
        title = "Obsidian ×2",
        description = "Quench Molten Lava with Crystal Water.",
        ingredients = mapOf(BlockType.LAVA to 1, BlockType.WATER to 1),
        outputBlock = BlockType.OBSIDIAN,
        outputCount = 2
    ),
    CraftingRecipe(
        id = "iron_pickaxe",
        title = "Iron Pickaxe (Tier 2)",
        description = "Unlocks mining of Gold Ore and Diamond Ore in Survival.",
        ingredients = mapOf(BlockType.IRON_ORE to 2, BlockType.WOOD_LOG to 1),
        unlocksPickaxeTier = 2
    ),
    CraftingRecipe(
        id = "diamond_pickaxe",
        title = "Diamond Pickaxe (Tier 3)",
        description = "Master tool capable of mining dense Obsidian.",
        ingredients = mapOf(BlockType.DIAMOND_ORE to 2, BlockType.WOOD_LOG to 1),
        unlocksPickaxeTier = 3
    )
)

fun defaultStarterInventory(mode: GameMode): Map<String, Long> =
    if (mode == GameMode.CREATIVE) {
        BlockType.placeableBlocks.associate { it.name to 99L }
    } else {
        mapOf(
            BlockType.PLANKS.name to 16L,
            BlockType.COBBLESTONE.name to 12L,
            BlockType.TORCH.name to 8L,
            BlockType.WOOD_LOG.name to 6L,
            BlockType.GLASS.name to 6L,
            BlockType.BRICK.name to 8L,
            BlockType.COAL_ORE.name to 4L
        )
    }

fun defaultHotbar(): List<String> = listOf(
    BlockType.GRASS.name,
    BlockType.DIRT.name,
    BlockType.STONE.name,
    BlockType.COBBLESTONE.name,
    BlockType.WOOD_LOG.name,
    BlockType.PLANKS.name,
    BlockType.SAND.name,
    BlockType.BRICK.name,
    BlockType.GLASS.name
)

@Keep
@IgnoreExtraProperties
data class BuilderProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Builder",
    val totalBlocksMined: Long = 0L,
    val totalBlocksPlaced: Long = 0L,
    val totalItemsCrafted: Long = 0L,
    val builderLevel: Long = 1L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class VoxelWorldDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "New World",
    val biome: String = BiomeType.EMERALD_VALLEY.name,
    val gameMode: String = GameMode.SURVIVAL.name,
    val seed: Long = 1024L,
    val timeOfDay: Double = 0.25,
    val pickaxeTier: Long = 1L,
    val blocksMined: Long = 0L,
    val blocksPlaced: Long = 0L,
    val playerX: Double = 8.5,
    val playerY: Double = 16.0,
    val playerZ: Double = 8.5,
    val blockDeltas: Map<String, Long> = emptyMap(),
    val inventory: Map<String, Long> = emptyMap(),
    val hotbar: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedBiome(): BiomeType = BiomeType.safeFromName(biome)
    fun resolvedGameMode(): GameMode = GameMode.safeFromName(gameMode)
}
