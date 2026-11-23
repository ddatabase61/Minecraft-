package com.example.data.model

import androidx.compose.ui.graphics.Color

enum class ShopCategory(val label: String) {
    GEAR("Royal Gear & Tools"),
    BUILDINGS("Instant Structures"),
    FLUIDS("Lava & Water Packs"),
    COMPANIONS("Mounts & Guards")
}

enum class ShopRewardType {
    ROYAL_CROWN,
    DIAMOND_PICKAXE,
    SUPER_BLASTER,
    JET_FLY_BOOTS,
    SPEED_SANDALS,
    INSTANT_HOUSE_KIT,
    INSTANT_MANDIR_KIT,
    LAVA_WATER_PACK,
    DIAMOND_GOLD_VAULT,
    ROYAL_ELEPHANT,
    ROYAL_HORSE,
    ROYAL_GUARD_RAJA
}

data class ShopProduct(
    val id: String,
    val title: String,
    val subtitleHindi: String,
    val priceCoins: Int,
    val category: ShopCategory,
    val rewardType: ShopRewardType,
    val accentColor: Color,
    val oneTimeUnlock: Boolean = false
)

val ROYAL_BAZAAR_PRODUCTS: List<ShopProduct> = listOf(
    ShopProduct(
        id = "royal_crown",
        title = "Maharaja Golden Crown",
        subtitleHindi = "शाही सोने का मुकुट (3rd-Person में चमकता मुकुट + राजा सम्मान)",
        priceCoins = 120,
        category = ShopCategory.GEAR,
        rewardType = ShopRewardType.ROYAL_CROWN,
        accentColor = Color(0xFFFFD54F),
        oneTimeUnlock = true
    ),
    ShopProduct(
        id = "diamond_pickaxe",
        title = "Diamond Pickaxe (Tier 3)",
        subtitleHindi = "ओब्सीडियन और हीरे की खान तुरंत खोदने वाली शक्तिशाली कुदाल",
        priceCoins = 100,
        category = ShopCategory.GEAR,
        rewardType = ShopRewardType.DIAMOND_PICKAXE,
        accentColor = Color(0xFF35E8FF),
        oneTimeUnlock = true
    ),
    ShopProduct(
        id = "super_blaster",
        title = "Super Blaster Mk-II",
        subtitleHindi = "ज़ॉम्बी को 1 ही गोली में हराने वाली सुपर गन अपग्रेड (60 DMG)",
        priceCoins = 150,
        category = ShopCategory.GEAR,
        rewardType = ShopRewardType.SUPER_BLASTER,
        accentColor = Color(0xFFFF5C8A),
        oneTimeUnlock = true
    ),
    ShopProduct(
        id = "jet_fly_boots",
        title = "Sky Jet Fly Boots",
        subtitleHindi = "सर्वाइवल मोड में भी आसमान में उड़ने की शक्ति अनलॉक करें",
        priceCoins = 180,
        category = ShopCategory.GEAR,
        rewardType = ShopRewardType.JET_FLY_BOOTS,
        accentColor = Color(0xFF53FF9E),
        oneTimeUnlock = true
    ),
    ShopProduct(
        id = "speed_sandals",
        title = "Pawan Speed Boots",
        subtitleHindi = "35% तेज़ दौड़ने और बड़े पहाड़ जल्दी चढ़ने के जादुई जूते",
        priceCoins = 90,
        category = ShopCategory.GEAR,
        rewardType = ShopRewardType.SPEED_SANDALS,
        accentColor = Color(0xFF64B5F6),
        oneTimeUnlock = true
    ),
    ShopProduct(
        id = "instant_mandir",
        title = "Instant Golden Mandir Kit",
        subtitleHindi = "अपने सामने तुरंत भव्य सोने के कलश वाला मंदिर बनाएं",
        priceCoins = 140,
        category = ShopCategory.BUILDINGS,
        rewardType = ShopRewardType.INSTANT_MANDIR_KIT,
        accentColor = Color(0xFFFF9800),
        oneTimeUnlock = false
    ),
    ShopProduct(
        id = "instant_house",
        title = "Instant Brick Haveli Kit",
        subtitleHindi = "अपने सामने तुरंत पक्का ईंट-कांच का सुंदर घर बनाएं",
        priceCoins = 110,
        category = ShopCategory.BUILDINGS,
        rewardType = ShopRewardType.INSTANT_HOUSE_KIT,
        accentColor = Color(0xFFE57373),
        oneTimeUnlock = false
    ),
    ShopProduct(
        id = "lava_water_pack",
        title = "Volcano Lava & Water Pack",
        subtitleHindi = "32 ज्वालामुखी लावा + 64 पानी + 24 ओब्सीडियन ब्लॉक",
        priceCoins = 80,
        category = ShopCategory.FLUIDS,
        rewardType = ShopRewardType.LAVA_WATER_PACK,
        accentColor = Color(0xFFFF6D00),
        oneTimeUnlock = false
    ),
    ShopProduct(
        id = "gem_vault",
        title = "Royal Gold & Diamond Vault",
        subtitleHindi = "24 सोना + 16 हीरा + 32 लाल ईंट + 24 क्रिस्टल ग्लास",
        priceCoins = 130,
        category = ShopCategory.FLUIDS,
        rewardType = ShopRewardType.DIAMOND_GOLD_VAULT,
        accentColor = Color(0xFF40C4FF),
        oneTimeUnlock = false
    ),
    ShopProduct(
        id = "royal_elephant",
        title = "Shahi Elephant (शाही हाथी)",
        subtitleHindi = "शाही सवारी के लिए अपने पास विशाल हाथी बुलाएं",
        priceCoins = 95,
        category = ShopCategory.COMPANIONS,
        rewardType = ShopRewardType.ROYAL_ELEPHANT,
        accentColor = Color(0xFF90A4AE),
        oneTimeUnlock = false
    ),
    ShopProduct(
        id = "royal_horse",
        title = "Fast Royal Horse (तेज़ घोड़ा)",
        subtitleHindi = "वफादार तेज़ शाही घोड़ा अपने साथ जोड़ें",
        priceCoins = 70,
        category = ShopCategory.COMPANIONS,
        rewardType = ShopRewardType.ROYAL_HORSE,
        accentColor = Color(0xFFBC8A5F),
        oneTimeUnlock = false
    ),
    ShopProduct(
        id = "royal_society_pack",
        title = "Raja + Pujari + Majdur Team",
        subtitleHindi = "राजा, पुजारी और घर बनाने वाले मज़दूर को तुरंत अपने पास बुलाएं",
        priceCoins = 110,
        category = ShopCategory.COMPANIONS,
        rewardType = ShopRewardType.ROYAL_GUARD_RAJA,
        accentColor = Color(0xFFCE93D8),
        oneTimeUnlock = false
    )
)
