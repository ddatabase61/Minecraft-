package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.FlightTakeoff
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AircraftType
import com.example.data.model.BikeType
import com.example.ui.theme.JetBrainsMonoFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BikeGarageSheet(
    activeBike: BikeType?,
    activePlane: AircraftType? = null,
    onSelectBike: (BikeType) -> Unit,
    onDismountBike: () -> Unit,
    onSelectPlane: (AircraftType) -> Unit = {},
    onDismountPlane: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember(activePlane) { mutableIntStateOf(if (activePlane != null) 1 else 0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0E151B),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
                .padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    if (selectedTab == 1) {
                                        listOf(Color(0xFF00B8D4), Color(0xFFFFD54F))
                                    } else {
                                        listOf(Color(0xFF00E5FF), Color(0xFF22E55E))
                                    }
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (selectedTab == 1) Icons.Rounded.AirplanemodeActive else Icons.Rounded.TwoWheeler,
                            contentDescription = "Vehicle Hangar",
                            tint = Color(0xFF081217),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = if (selectedTab == 1) "Airfield Hangar (4 हवाई जहाज़)" else "Superbike Garage (6 बाइक्स)",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (selectedTab == 1) {
                                "कोई भी 3D हवाई जहाज़ उड़ाएँ — जेट व प्रोपेलर साउंड के साथ!"
                            } else {
                                "कोई भी 3D बाइक चुनें और रियल साउंड के साथ चलाएँ!"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFA7C4D4)
                        )
                    }
                }

                if (activeBike != null || activePlane != null) {
                    OutlinedButton(
                        onClick = {
                            if (activePlane != null) onDismountPlane() else onDismountBike()
                            onDismiss()
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.2.dp, Color(0xFFFF6E6E)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("garage_dismount_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DirectionsWalk,
                            contentDescription = "Exit Vehicle",
                            tint = Color(0xFFFF8A80),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (activePlane != null) "लैंड करें" else "उतरें",
                            color = Color(0xFFFFB4AB),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Two-Tab Switcher: 6 Superbikes vs 4 Flyable 3D Airplanes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilterChip(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    leadingIcon = { Icon(Icons.Rounded.TwoWheeler, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    label = { Text("🏍️ 6 Superbikes", fontWeight = FontWeight.Bold) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("hangar_tab_bikes")
                )
                FilterChip(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    leadingIcon = { Icon(Icons.Rounded.AirplanemodeActive, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    label = { Text("✈️ 4 हवाई जहाज़", fontWeight = FontWeight.Bold) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("hangar_tab_planes")
                )
            }

            if (selectedTab == 0) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(310.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(BikeType.entries, key = { it.name }) { bike ->
                        val isRiding = activeBike == bike
                        val highlightColor = if (bike == BikeType.ROYAL_BULLET) bike.accentColor else bike.primaryColor

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isRiding) Color(0xFF162A24) else Color(0xFF151E26),
                            border = BorderStroke(
                                width = if (isRiding) 2.dp else 1.dp,
                                color = if (isRiding) Color(0xFF53FF9E) else highlightColor.copy(alpha = 0.45f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    onSelectBike(bike)
                                    onDismiss()
                                }
                                .testTag("bike_card_${bike.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            Brush.linearGradient(
                                                listOf(bike.primaryColor, bike.accentColor.copy(alpha = 0.85f))
                                            )
                                        )
                                        .border(1.5.dp, bike.rimColor, RoundedCornerShape(14.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.TwoWheeler,
                                        contentDescription = bike.displayName,
                                        tint = Color.White,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = bike.displayName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (isRiding) {
                                            Surface(shape = CircleShape, color = Color(0xFF22A06B)) {
                                                Text(
                                                    text = "ACTIVE",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = bike.subtitleHindi,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFB5CDD9),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Speed,
                                                contentDescription = null,
                                                tint = highlightColor,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Text(
                                                text = "${bike.maxDisplayKmh} km/h",
                                                fontFamily = JetBrainsMonoFontFamily,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFEAF6FF)
                                            )
                                        }
                                        LinearProgressIndicator(
                                            progress = { (bike.topSpeedBlocksPerSec / 18.5f).coerceIn(0.25f, 1f) },
                                            color = highlightColor,
                                            trackColor = Color(0x33FFFFFF),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(5.dp)
                                                .clip(CircleShape)
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        onSelectBike(bike)
                                        onDismiss()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isRiding) Color(0xFF22A06B) else highlightColor,
                                        contentColor = if (highlightColor == Color(0xFFFFC107) || highlightColor == Color(0xFF22E55E)) Color(0xFF0A140F) else Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    modifier = Modifier.testTag("ride_bike_btn_${bike.name.lowercase()}")
                                ) {
                                    Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = if (isRiding) "सवार" else "चलाएँ",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(310.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(AircraftType.entries, key = { it.name }) { plane ->
                        val isFlying = activePlane == plane
                        val highlightColor = plane.accentColor

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isFlying) Color(0xFF132732) else Color(0xFF151E26),
                            border = BorderStroke(
                                width = if (isFlying) 2.dp else 1.dp,
                                color = if (isFlying) Color(0xFF00E5FF) else highlightColor.copy(alpha = 0.55f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    onSelectPlane(plane)
                                    onDismiss()
                                }
                                .testTag("plane_card_${plane.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            Brush.linearGradient(
                                                listOf(plane.fuselageColor, highlightColor)
                                            )
                                        )
                                        .border(1.5.dp, highlightColor, RoundedCornerShape(14.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AirplanemodeActive,
                                        contentDescription = plane.displayName,
                                        tint = Color(0xFF0B141A),
                                        modifier = Modifier.size(30.dp)
                                    )
                                }

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = plane.displayName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (isFlying) {
                                            Surface(shape = CircleShape, color = Color(0xFF00ACC1)) {
                                                Text(
                                                    text = "FLYING",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = plane.subtitleHindi,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFB5CDD9),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = "${plane.maxDisplayKmh} km/h" + if (plane.hasWeapons) " • 🚀" else "",
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFEAF6FF)
                                        )
                                        LinearProgressIndicator(
                                            progress = { (plane.cruiseSpeedBlocksPerSec / 26f).coerceIn(0.3f, 1f) },
                                            color = highlightColor,
                                            trackColor = Color(0x33FFFFFF),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(5.dp)
                                                .clip(CircleShape)
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        onSelectPlane(plane)
                                        onDismiss()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isFlying) Color(0xFF00ACC1) else highlightColor,
                                        contentColor = Color(0xFF0A140F)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    modifier = Modifier.testTag("fly_plane_btn_${plane.name.lowercase()}")
                                ) {
                                    Icon(Icons.Rounded.FlightTakeoff, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = if (isFlying) "उड़ान" else "उड़ाएँ",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
