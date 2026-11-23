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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.FlightTakeoff
import androidx.compose.material.icons.rounded.Publish
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.example.data.model.AdminEngineAsset
import com.example.data.model.EngineAssetCategory
import com.example.engine.gl.VoxelPlayerController
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.viewmodel.GameWorldUiState

/**
 * Full-Screen Real-Time 3D Admin Game Engine Studio Overlay:
 * - Prominent Back button (`← Back`) at top-left so Admin can exit cleanly anytime.
 * - Live Crosshair Target Coords, Brush Radius (`1x`/`3x`/`5x`), Undo, Fly Up/Down, and `🚀 Publish #1`.
 * - Full Categorized Asset Store (गड्ढा-गुड्डी, पेड़-पौधा-फूल-जंगल-झाड़ी, पानी-लावा, रोड-घर-रनवे-शादी मंडप, इंसान-शादी-युद्ध).
 * - One-tap `PLACE HERE (रखें)` button to place any chosen asset directly at the exact 3D crosshair location!
 */
@Composable
fun AdminGameEngineOverlay(
    uiState: GameWorldUiState,
    controller: VoxelPlayerController,
    onExitEngine: () -> Unit,
    onSelectAsset: (AdminEngineAsset) -> Unit,
    onSelectBrushRadius: (Int) -> Unit,
    onPlaceAssetHere: () -> Unit,
    onUndo: () -> Unit,
    onToggleFly: () -> Unit,
    onPublishLiveMap: (title: String, announcement: String, description: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf(uiState.selectedEngineAsset.category) }
    var showPublishDialog by remember { mutableStateOf(false) }

    val filteredAssets = remember(selectedCategory) {
        AdminEngineAsset.entries.filter { it.category == selectedCategory }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. TOP ENGINE STUDIO BAR (Back Button + Coordinates + Brush + Undo + Fly + Publish #1)
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xDD0A1016), Color(0x880A1016), Color.Transparent)
                    )
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Prominent Full-Screen Back Button + Engine Identity Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xEE1D2832),
                    border = BorderStroke(1.4.dp, Color(0xFFFFD54F)),
                    modifier = Modifier
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onExitEngine)
                        .testTag("engine_back_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back from Admin Engine",
                            tint = Color(0xFFFFE57F),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Back",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xCC111B24),
                    border = BorderStroke(1.dp, Color(0x6600E5FF))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "ADMIN 3D ENGINE • ${uiState.enginePlacedCount} रखे",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF00E5FF)
                        )
                        val targetLabel = if (uiState.targetBlockX != null && uiState.targetBlockY != null && uiState.targetBlockZ != null) {
                            "Target: ${uiState.targetBlockX}, ${uiState.targetBlockY}, ${uiState.targetBlockZ}"
                        } else {
                            "Player: ${uiState.playerX}, ${uiState.playerY}, ${uiState.playerZ}"
                        }
                        Text(
                            text = targetLabel,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 9.sp,
                            color = Color(0xFFD6E6F2)
                        )
                    }
                }
            }

            // Right: Brush Size + Undo + Fly + Publish #1
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(1 to "1x", 2 to "3x", 4 to "5x").forEach { (radius, label) ->
                    val active = uiState.engineBrushRadius == radius
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (active) Color(0xFF00B8D4) else Color(0xBB16202A),
                        border = BorderStroke(1.dp, if (active) Color.White else Color(0x55FFFFFF)),
                        modifier = Modifier
                            .height(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelectBrushRadius(radius) }
                            .testTag("engine_brush_$label")
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (active) Color(0xFF061318) else Color.White
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (uiState.canUndo) Color(0xCC24303C) else Color(0x66161D24),
                    border = BorderStroke(1.dp, Color(0x66FFFFFF)),
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = uiState.canUndo, onClick = onUndo)
                        .testTag("engine_undo_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Undo,
                            contentDescription = "Undo Last Edit",
                            tint = if (uiState.canUndo) Color.White else Color(0x66FFFFFF),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (uiState.flying) Color(0xFF22A06B) else Color(0xCC16202A),
                    border = BorderStroke(1.dp, if (uiState.flying) Color(0xFF7CFFB2) else Color(0x66FFFFFF)),
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onToggleFly)
                        .testTag("engine_fly_toggle")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.FlightTakeoff,
                            contentDescription = "Toggle Editor Flight",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Button(
                    onClick = { showPublishDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFC107),
                        contentColor = Color(0xFF1A1200)
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .height(40.dp)
                        .testTag("engine_publish_rank1_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RocketLaunch,
                        contentDescription = "Publish Live Map #1",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Publish #1",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // 2. RIGHT-SIDE EDITOR ALTITUDE + BIG PLACE HERE BUTTON (above Asset Dock)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 14.dp, bottom = 34.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (uiState.flying) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BedrockTactileHoldButton(
                        icon = Icons.Rounded.ArrowUpward,
                        contentDescription = "Engine Fly Up",
                        size = 46.dp,
                        onHoldChanged = { controller.flyUpHeld = it },
                        tag = "engine_fly_up"
                    )
                    BedrockTactileHoldButton(
                        icon = Icons.Rounded.ArrowDownward,
                        contentDescription = "Engine Fly Down",
                        size = 46.dp,
                        onHoldChanged = { controller.flyDownHeld = it },
                        tag = "engine_fly_down"
                    )
                }
            }

            // Primary Tactile Placement Button
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = uiState.selectedEngineAsset.accentColor,
                border = BorderStroke(2.dp, Color.White),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onPlaceAssetHere)
                    .testTag("engine_place_asset_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AddLocationAlt,
                        contentDescription = "Place Selected Asset Here",
                        tint = Color(0xFF0A1116),
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = "PLACE HERE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF0A1116)
                        )
                        Text(
                            text = uiState.selectedEngineAsset.shortBadge,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xCC0A1116)
                        )
                    }
                }
            }
        }

        // 3. BOTTOM FULL ASSET STORE DOCK (Categories + Scrollable Asset Cards)
        Surface(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            color = Color(0xDE0B1218),
            border = BorderStroke(1.dp, Color(0x55FFFFFF)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = 580.dp)
                .fillMaxWidth()
                .padding(horizontal = 168.dp)
                .testTag("engine_asset_store_dock")
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // Category Tabs
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    items(EngineAssetCategory.entries, key = { it.name }) { category ->
                        val active = selectedCategory == category
                        Surface(
                            shape = CircleShape,
                            color = if (active) Color(0xFFFFD54F) else Color(0x33FFFFFF),
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    selectedCategory = category
                                    val first = AdminEngineAsset.entries.firstOrNull { it.category == category }
                                    if (first != null) onSelectAsset(first)
                                }
                                .testTag("engine_cat_${category.name.lowercase()}")
                        ) {
                            Text(
                                text = category.labelHindi,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (active) Color(0xFF1A1200) else Color(0xFFE0EEF8),
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Asset Store Cards for active category
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredAssets, key = { it.name }) { asset ->
                        val selected = uiState.selectedEngineAsset == asset
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) asset.accentColor.copy(alpha = 0.30f) else Color(0xFF141E28),
                            border = BorderStroke(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) asset.accentColor else Color(0x44FFFFFF)
                            ),
                            modifier = Modifier
                                .width(114.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onSelectAsset(asset) }
                                .testTag("engine_asset_${asset.name.lowercase()}")
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(asset.accentColor)
                                    )
                                    Text(
                                        text = asset.shortBadge,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = asset.accentColor
                                    )
                                }
                                Text(
                                    text = asset.titleHindi,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPublishDialog) {
        var title by remember(uiState.worldName) {
            mutableStateOf(uiState.adminSnapshot.liveMap.title.ifBlank { uiState.worldName })
        }
        var desc by remember {
            mutableStateOf(
                uiState.adminSnapshot.liveMap.mapDescription.ifBlank {
                    "एडमिन 3D गेम इंजन से बनाया गया पूरा मैप — सड़क, पेड़-जंगल, फूल, शादी मंडप व हवाई पट्टी!"
                }
            )
        }
        var banner by remember {
            mutableStateOf(
                uiState.adminSnapshot.liveMap.announcementBanner.ifBlank {
                    "नया #1 ऑफिशियल मैप लाइव हो गया है — अभी खेलें!"
                }
            )
        }

        AlertDialog(
            onDismissRequest = { showPublishDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Publish,
                        contentDescription = null,
                        tint = Color(0xFFFFC107)
                    )
                    Text("Publish Live Map (#1 Rank)")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "पब्लिश करते ही यह मैप गेम के मुख्य स्क्रीन पर #1 नंबर (Top Featured Official Map) पर दिखेगा!",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = title,
                        onValueChange = { if (it.length <= 45) title = it },
                        label = { Text("Map Title") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("engine_publish_title_input")
                    )
                    OutlinedTextField(
                        value = desc,
                        onValueChange = { if (it.length <= 120) desc = it },
                        label = { Text("Map Description") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = banner,
                        onValueChange = { if (it.length <= 100) banner = it },
                        label = { Text("Live Announcement Banner") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPublishDialog = false
                        onPublishLiveMap(title, banner, desc)
                    },
                    modifier = Modifier.testTag("engine_confirm_publish_button")
                ) {
                    Text("🚀 Publish at #1")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPublishDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
