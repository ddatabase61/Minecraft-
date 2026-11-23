package com.example.data.model

import androidx.compose.ui.graphics.Color

enum class BikeSoundStyle {
    CRUISER_THUMP,
    SUPERBIKE_SCREAM,
    STREET_GROWL,
    DIRT_BRAP,
    ELECTRIC_HUM
}

/**
 * 6 Distinct Rideable 3D Motorcycles for BlockForge Mega World & Real Estate City Roads.
 */
enum class BikeType(
    val displayName: String,
    val subtitleHindi: String,
    val topSpeedBlocksPerSec: Float,
    val maxDisplayKmh: Int,
    val acceleration: Float,
    val handlingRate: Float,
    val hillClimbBoost: Float,
    val primaryColorHex: Long,
    val accentColorHex: Long,
    val rimColorHex: Long,
    val headlightGlowHex: Long,
    val soundStyle: BikeSoundStyle,
    val hasNeonWheels: Boolean = false
) {
    ROYAL_BULLET(
        displayName = "Royal Bullet 350",
        subtitleHindi = "क्लासिक दमदार थंप आवाज़ • क्रूज़र बाइक",
        topSpeedBlocksPerSec = 11.5f,
        maxDisplayKmh = 125,
        acceleration = 9.5f,
        handlingRate = 2.6f,
        hillClimbBoost = 1.0f,
        primaryColorHex = 0xFF1C2026,
        accentColorHex = 0xFFD9B24C,
        rimColorHex = 0xFFD8DEE9,
        headlightGlowHex = 0xFFFFF3B0,
        soundStyle = BikeSoundStyle.CRUISER_THUMP
    ),
    NINJA_ZX10R(
        displayName = "Ninja ZX-10R",
        subtitleHindi = "नियॉन ग्रीन सुपरबाइक • तेज़ रफ़्तार रेसर",
        topSpeedBlocksPerSec = 16.5f,
        maxDisplayKmh = 299,
        acceleration = 14.5f,
        handlingRate = 3.1f,
        hillClimbBoost = 1.0f,
        primaryColorHex = 0xFF22E55E,
        accentColorHex = 0xFF14181E,
        rimColorHex = 0xFF53FF9E,
        headlightGlowHex = 0xFFE3FAFF,
        soundStyle = BikeSoundStyle.SUPERBIKE_SCREAM
    ),
    KTM_DUKE(
        displayName = "KTM Duke 390",
        subtitleHindi = "ऑरेंज स्ट्रीट स्टंट बाइक • शार्प टर्निंग",
        topSpeedBlocksPerSec = 14.2f,
        maxDisplayKmh = 185,
        acceleration = 13.8f,
        handlingRate = 3.5f,
        hillClimbBoost = 1.1f,
        primaryColorHex = 0xFFFF6D00,
        accentColorHex = 0xFFF5F7FA,
        rimColorHex = 0xFFFF8F33,
        headlightGlowHex = 0xFFFFFFFF,
        soundStyle = BikeSoundStyle.STREET_GROWL
    ),
    DUCATI_PANIGALE(
        displayName = "Ducati Panigale V4",
        subtitleHindi = "लाल इटालियन रेसिंग सुपरबाइक • टर्बो पावर",
        topSpeedBlocksPerSec = 17.2f,
        maxDisplayKmh = 315,
        acceleration = 15.2f,
        handlingRate = 3.2f,
        hillClimbBoost = 1.0f,
        primaryColorHex = 0xFFE5192E,
        accentColorHex = 0xFF1A1C20,
        rimColorHex = 0xFFFFD54F,
        headlightGlowHex = 0xFFFFFFFF,
        soundStyle = BikeSoundStyle.SUPERBIKE_SCREAM
    ),
    DIRT_MOTOCROSS(
        displayName = "Desert Dirt 450",
        subtitleHindi = "पहाड़ व ऑफ-रोड मोटोक्रॉस • हर रास्ते पर चले",
        topSpeedBlocksPerSec = 13.0f,
        maxDisplayKmh = 155,
        acceleration = 12.8f,
        handlingRate = 3.6f,
        hillClimbBoost = 1.35f,
        primaryColorHex = 0xFFFFC107,
        accentColorHex = 0xFF21262E,
        rimColorHex = 0xFFE0E5EC,
        headlightGlowHex = 0xFFFFF59D,
        soundStyle = BikeSoundStyle.DIRT_BRAP
    ),
    CYBER_TRON(
        displayName = "Cyber Tron Lightcycle",
        subtitleHindi = "फ्यूचरिस्टिक नियॉन इलेक्ट्रिक बाइक • साइलेंट टर्बो",
        topSpeedBlocksPerSec = 18.0f,
        maxDisplayKmh = 340,
        acceleration = 16.5f,
        handlingRate = 3.4f,
        hillClimbBoost = 1.15f,
        primaryColorHex = 0xFF0E1726,
        accentColorHex = 0xFF00E5FF,
        rimColorHex = 0xFF00F0FF,
        headlightGlowHex = 0xFF7DF9FF,
        soundStyle = BikeSoundStyle.ELECTRIC_HUM,
        hasNeonWheels = true
    );

    val primaryColor: Color
        get() = Color(primaryColorHex)

    val accentColor: Color
        get() = Color(accentColorHex)

    val rimColor: Color
        get() = Color(rimColorHex)
}
