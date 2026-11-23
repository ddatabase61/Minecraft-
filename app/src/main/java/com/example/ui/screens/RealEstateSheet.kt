package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.HomeWork
import androidx.compose.material.icons.rounded.LocationCity
import androidx.compose.material.icons.rounded.MonetizationOn
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Villa
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.viewmodel.GameWorldUiState
import com.example.ui.viewmodel.WorldLandmark

data class RealEstateProperty(
    val id: String,
    val title: String,
    val subtitleHindi: String,
    val category: String,
    val priceCoins: Int,
    val rentPerCycle: Int,
    val landmark: WorldLandmark,
    val accentHex: Long
)

val REAL_ESTATE_CATALOG = listOf(
    RealEstateProperty(
        id = "re_plot_sector_a",
        title = "Sector-A Corner Plot",
        subtitleHindi = "पक्की बाउंड्री, रोड कनेक्शन और स्ट्रीट लाइट के साथ रेडी प्लॉट",
        category = "RESIDENTIAL PLOT",
        priceCoins = 120,
        rentPerCycle = 20,
        landmark = WorldLandmark.RESIDENTIAL_PLOTS,
        accentHex = 0xFF66BB6A
    ),
    RealEstateProperty(
        id = "re_emerald_farmhouse",
        title = "Emerald Lakefront Farmhouse",
        subtitleHindi = "बरामदा, बगीचा, ईंट-लकड़ी का शानदार 2-मंज़िला फार्महाउस",
        category = "FARMHOUSE",
        priceCoins = 220,
        rentPerCycle = 35,
        landmark = WorldLandmark.FARMHOUSE,
        accentHex = 0xFF26A69A
    ),
    RealEstateProperty(
        id = "re_royal_villa",
        title = "Royal Green Luxury Villa",
        subtitleHindi = "स्विमिंग पूल, ग्लास बालकनी, रूफटॉप टेरेस और लग्ज़री बंगला",
        category = "LUXURY VILLA",
        priceCoins = 380,
        rentPerCycle = 65,
        landmark = WorldLandmark.LUXURY_VILLA,
        accentHex = 0xFFFFCA28
    ),
    RealEstateProperty(
        id = "re_plaza_mall",
        title = "BlockForge Commercial Plaza Mall",
        subtitleHindi = "शोरूम ग्लास फ्रंट, गोल्डन गेट और कॉमर्शियल शॉपिंग मॉल",
        category = "COMMERCIAL MALL",
        priceCoins = 550,
        rentPerCycle = 95,
        landmark = WorldLandmark.SHOPPING_MALL,
        accentHex = 0xFF42A5F5
    ),
    RealEstateProperty(
        id = "re_skyview_tower",
        title = "Skyview Heights 4-Story Tower",
        subtitleHindi = "4 मंज़िला हाई-राइज़ अपार्टमेंट टावर, बालकनी और रूफटॉप हेलीपैड",
        category = "HIGH-RISE FLATS",
        priceCoins = 750,
        rentPerCycle = 140,
        landmark = WorldLandmark.APARTMENT_TOWER,
        accentHex = 0xFFAB47BC
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RealEstateMarketSheet(
    uiState: GameWorldUiState,
    onBuyProperty: (RealEstateProperty) -> Unit,
    onVisitProperty: (WorldLandmark) -> Unit,
    onCollectRent: () -> Unit,
    onConstructHere: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableIntStateOf(0) }

    val ownedCount = REAL_ESTATE_CATALOG.count { it.id in uiState.ownedRealEstateIds }
    val totalRentRate = REAL_ESTATE_CATALOG
        .filter { it.id in uiState.ownedRealEstateIds }
        .sumOf { it.rentPerCycle }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LocationCity,
                        contentDescription = null,
                        tint = Color(0xFFFFCA28),
                        modifier = Modifier.size(30.dp)
                    )
                    Column {
                        Text("Real Estate Township", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            text = "🪙 ${uiState.goldCoins} Coins • $ownedCount/${REAL_ESTATE_CATALOG.size} Owned",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Live Rental Income Collector Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF13261E),
                border = BorderStroke(1.2.dp, Color(0xFF53FF9E).copy(alpha = 0.65f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Pending Rental Income: 🪙 ${uiState.pendingRentCoins}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFE57F)
                        )
                        Text(
                            text = if (totalRentRate > 0) {
                                "Active rent rate: +$totalRentRate Coins/cycle"
                            } else {
                                "Buy properties to earn automatic real-estate rent!"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFD6ECE1)
                        )
                    }
                    Button(
                        onClick = onCollectRent,
                        enabled = uiState.pendingRentCoins > 0,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("collect_rent_button")
                    ) {
                        Icon(Icons.Rounded.MonetizationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Collect")
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilterChip(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    label = { Text("Buy & Visit Properties") },
                    leadingIcon = { Icon(Icons.Rounded.HomeWork, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    label = { Text("Build New Here") },
                    leadingIcon = { Icon(Icons.Rounded.Construction, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.weight(1f)
                )
            }

            if (selectedTab == 0) {
                // Quick Visit Grand Boulevard button
                OutlinedButton(
                    onClick = {
                        onVisitProperty(WorldLandmark.REAL_ESTATE_BOULEVARD)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("visit_boulevard_button")
                ) {
                    Icon(Icons.Rounded.Explore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Teleport to Real Estate Grand Boulevard (Main City Road)")
                }

                REAL_ESTATE_CATALOG.forEach { prop ->
                    val owned = prop.id in uiState.ownedRealEstateIds
                    val accent = Color(prop.accentHex)
                    val icon: ImageVector = when (prop.id) {
                        "re_royal_villa" -> Icons.Rounded.Villa
                        "re_skyview_tower" -> Icons.Rounded.Apartment
                        "re_plaza_mall" -> Icons.Rounded.Storefront
                        "re_emerald_farmhouse" -> Icons.Rounded.AccountBalance
                        else -> Icons.Rounded.HomeWork
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(
                            width = if (owned) 1.5.dp else 1.dp,
                            color = if (owned) Color(0xFF53FF9E) else accent.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = accent.copy(alpha = 0.18f),
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = accent,
                                        modifier = Modifier.padding(9.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = prop.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = prop.subtitleHindi,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${prop.category} • Rent: +${prop.rentPerCycle} 🪙/cycle",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = accent
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        onVisitProperty(prop.landmark)
                                        onDismiss()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("visit_${prop.id}")
                                ) {
                                    Icon(Icons.Rounded.Explore, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Visit")
                                }

                                Button(
                                    onClick = { onBuyProperty(prop) },
                                    enabled = !owned && uiState.goldCoins >= prop.priceCoins,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = if (owned) {
                                        ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF1F4E38),
                                            contentColor = Color(0xFF8CFFBE)
                                        )
                                    } else {
                                        ButtonDefaults.buttonColors()
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .testTag("buy_${prop.id}")
                                ) {
                                    if (owned) {
                                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Owned")
                                    } else {
                                        Text("Buy • 🪙 ${prop.priceCoins}")
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "Construct Realistic Real Estate Right Where You Stand:",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val builders = listOf(
                    Triple("LUXURY_VILLA", "Build Luxury Villa + Swimming Pool", "2-story modern bungalow with glass balcony & pool"),
                    Triple("APARTMENT_TOWER", "Build 4-Story Apartment Tower", "High-rise luxury flats with glass windows & helipad"),
                    Triple("SHOPPING_MALL", "Build Commercial Shopping Mall", "Wide glass showroom plaza with golden archway"),
                    Triple("RESIDENTIAL_PLOT", "Build Walled Residential Plot", "Leveled green plot with boundary wall & gate lamps"),
                    Triple("BOULEVARD_ROAD", "Build Street-Lamp Boulevard Road", "Paved asphalt road with curbs, lane lines & lamps")
                )

                builders.forEach { (type, title, desc) ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Button(
                                onClick = {
                                    onConstructHere(type, title)
                                    onDismiss()
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("build_re_${type.lowercase()}")
                            ) {
                                Text("Build")
                            }
                        }
                    }
                }
            }
        }
    }
}
