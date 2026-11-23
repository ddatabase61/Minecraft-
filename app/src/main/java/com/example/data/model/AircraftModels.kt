package com.example.data.model

import androidx.compose.ui.graphics.Color

enum class AircraftSoundStyle {
    JET_TURBINE,
    FIGHTER_AFTERBURNER,
    PROPELLER_CLASSIC,
    VTOL_HYBRID
}

/**
 * 4 Distinct 3D Flyable Airplanes for exploring the mega world, cities, mountains, and sky battles.
 */
enum class AircraftType(
    val displayName: String,
    val subtitleHindi: String,
    val cruiseSpeedBlocksPerSec: Float,
    val maxDisplayKmh: Int,
    val climbRate: Float,
    val turnRate: Float,
    val fuselageColorHex: Long,
    val wingColorHex: Long,
    val accentColorHex: Long,
    val canopyColorHex: Long,
    val soundStyle: AircraftSoundStyle,
    val hasPropeller: Boolean = false,
    val hasWeapons: Boolean = false
) {
    JUMBO_747(
        displayName = "Air India Jumbo 747",
        subtitleHindi = "विशाल यात्री हवाई जहाज़ • स्मूथ आसमान की सैर",
        cruiseSpeedBlocksPerSec = 19.5f,
        maxDisplayKmh = 880,
        climbRate = 9.5f,
        turnRate = 1.65f,
        fuselageColorHex = 0xFFF2F5F8,
        wingColorHex = 0xFFD2DBE4,
        accentColorHex = 0xFFFF6F00,
        canopyColorHex = 0xFF1E3A5F,
        soundStyle = AircraftSoundStyle.JET_TURBINE
    ),
    RAFALE_FIGHTER(
        displayName = "Rafale Fighter Jet",
        subtitleHindi = "सुपरसोनिक लड़ाकू विमान • आफ्टरबर्नर व मिसाइल",
        cruiseSpeedBlocksPerSec = 25.5f,
        maxDisplayKmh = 1920,
        climbRate = 14.0f,
        turnRate = 2.65f,
        fuselageColorHex = 0xFF455A64,
        wingColorHex = 0xFF37474F,
        accentColorHex = 0xFF00E5FF,
        canopyColorHex = 0xFFFFD54F,
        soundStyle = AircraftSoundStyle.FIGHTER_AFTERBURNER,
        hasWeapons = true
    ),
    CLASSIC_BIPLANE(
        displayName = "Village Prop Plane",
        subtitleHindi = "क्लासिक पंखे वाला जहाज़ • गाँव व पहाड़ों की सैर",
        cruiseSpeedBlocksPerSec = 15.0f,
        maxDisplayKmh = 310,
        climbRate = 8.5f,
        turnRate = 2.35f,
        fuselageColorHex = 0xFFE53935,
        wingColorHex = 0xFFFFCA28,
        accentColorHex = 0xFF263238,
        canopyColorHex = 0xFF81D4FA,
        soundStyle = AircraftSoundStyle.PROPELLER_CLASSIC,
        hasPropeller = true
    ),
    ROYAL_SKY_VTOL(
        displayName = "Royal Sky Jet VTOL",
        subtitleHindi = "रॉयल गोल्डन प्राइवेट जेट • वर्टिकल टेकऑफ़",
        cruiseSpeedBlocksPerSec = 22.0f,
        maxDisplayKmh = 1240,
        climbRate = 13.0f,
        turnRate = 2.45f,
        fuselageColorHex = 0xFF142A24,
        wingColorHex = 0xFF1E4438,
        accentColorHex = 0xFFFFD54F,
        canopyColorHex = 0xFF40E0D0,
        soundStyle = AircraftSoundStyle.VTOL_HYBRID,
        hasWeapons = true
    );

    val fuselageColor: Color get() = Color(fuselageColorHex)
    val wingColor: Color get() = Color(wingColorHex)
    val accentColor: Color get() = Color(accentColorHex)
}

enum class EngineAssetCategory(val labelHindi: String) {
    TERRAIN("गड्ढा-गुड्डी व जमीन"),
    NATURE("पेड़, फूल, जंगल, झाड़ी"),
    WATER_LAVA("पानी व लावा"),
    BUILDINGS("रोड, घर, शहर, रनवे"),
    SOCIETY("इंसान, शादी व युद्ध")
}

/**
 * Full Asset Store for the Full-Screen Real-Time Admin Game Engine Studio.
 * Every asset can be placed directly at the admin's exact 3D crosshair position!
 */
enum class AdminEngineAsset(
    val titleHindi: String,
    val shortBadge: String,
    val category: EngineAssetCategory,
    val accentHex: Long
) {
    // Terrain & Digging (गड्ढा-गुड्डी)
    DIG_SMALL_PIT("छोटा गड्ढा (Pit)", "PIT", EngineAssetCategory.TERRAIN, 0xFF8D6E63),
    DIG_DEEP_CRATER("बड़ा गड्ढा / खाई", "CRATER", EngineAssetCategory.TERRAIN, 0xFF6D4C41),
    FLATTEN_GROUND("ज़मीन समतल करें", "FLAT", EngineAssetCategory.TERRAIN, 0xFF66BB6A),
    RAISE_HILL("पहाड़ी टिब्बा", "HILL", EngineAssetCategory.TERRAIN, 0xFF78909C),
    STONE_ROAD("पक्की सड़क (Road)", "ROAD", EngineAssetCategory.TERRAIN, 0xFF546E7A),

    // Nature, Grass, Bushes, Trees, Flowers, Jungle (पेड़, पौधा, फूल, जंगल, झाड़ी, घास-फूस)
    GRASS_PATCH("घास-फूस मैदान", "GRASS", EngineAssetCategory.NATURE, 0xFF4CAF50),
    GREEN_BUSH("हरी झाड़ी (Bush)", "BUSH", EngineAssetCategory.NATURE, 0xFF2E7D32),
    OAK_TREE("विशाल हरा पेड़", "TREE", EngineAssetCategory.NATURE, 0xFF43A047),
    CHERRY_TREE("चेरी फूल पेड़", "CHERRY", EngineAssetCategory.NATURE, 0xFFF48FB1),
    DENSE_JUNGLE("घना जंगल (Jungle)", "JUNGLE", EngineAssetCategory.NATURE, 0xFF1B5E20),
    FLOWER_GARDEN("रंगीन फूल बगीचा", "FLOWER", EngineAssetCategory.NATURE, 0xFFFF4081),
    SUNFLOWER_FIELD("सूरजमुखी खेत", "SUNFLR", EngineAssetCategory.NATURE, 0xFFFFD54F),

    // Water & Lava
    WATER_POND("पानी का तालाब", "POND", EngineAssetCategory.WATER_LAVA, 0xFF29B6F6),
    WATERFALL_SPRING("बहता झरना", "FALLS", EngineAssetCategory.WATER_LAVA, 0xFF00E5FF),
    RIVER_CHANNEL("बहती नदी", "RIVER", EngineAssetCategory.WATER_LAVA, 0xFF0288D1),
    VOLCANO_LAVA("ज्वालामुखी लावा", "LAVA", EngineAssetCategory.WATER_LAVA, 0xFFFF5722),

    // Buildings, Roads, Airport Runway
    HOUSE_HOME("पक्का घर (House)", "HOME", EngineAssetCategory.BUILDINGS, 0xFFFFA726),
    WEDDING_MANDAP("शादी विवाह मंडप", "VIVAH", EngineAssetCategory.BUILDINGS, 0xFFE91E63),
    SACRED_MANDIR("भव्य मंदिर", "MANDIR", EngineAssetCategory.BUILDINGS, 0xFFFF7043),
    ROYAL_PALACE("राजमहल", "PALACE", EngineAssetCategory.BUILDINGS, 0xFFFFD54F),
    LUXURY_VILLA("लग्ज़री विला + पूल", "VILLA", EngineAssetCategory.BUILDINGS, 0xFF26A69A),
    CITY_BOULEVARD("शहर हाईवे रोड", "HWY", EngineAssetCategory.BUILDINGS, 0xFF90A4AE),
    AIRPORT_RUNWAY("हवाई पट्टी (Runway)", "RUNWAY", EngineAssetCategory.BUILDINGS, 0xFF4FC3F7),

    // Living Human Society, Weddings & International War
    SPAWN_VILLAGER_FAMILY("परिवार व नागरिक", "HUMAN", EngineAssetCategory.SOCIETY, 0xFF81C784),
    TRIGGER_WEDDING("शादी-विवाह कराएँ 💍", "SHAADI", EngineAssetCategory.SOCIETY, 0xFFFF80AB),
    SPAWN_BHARAT_SOLDIERS("देश रक्षक सेना ⚔️", "DESH", EngineAssetCategory.SOCIETY, 0xFFFF9800),
    SPAWN_VIDESH_WARRIORS("विदेश योद्धा दल ⚔️", "VIDESH", EngineAssetCategory.SOCIETY, 0xFF42A5F5),
    TOGGLE_WORLD_WAR("देश-विदेश युद्ध / शांति", "WAR", EngineAssetCategory.SOCIETY, 0xFFEF5350);

    val accentColor: Color get() = Color(accentHex)
}
