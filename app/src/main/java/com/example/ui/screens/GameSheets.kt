package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.BlockCategory
import com.example.data.model.BlockType
import com.example.data.model.CRAFTING_RECIPES
import com.example.data.model.CraftingRecipe
import com.example.data.model.GameMode
import com.example.engine.MiniVoxelBlockIcon
import com.example.ui.viewmodel.GameWorldUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockInventorySheet(
    uiState: GameWorldUiState,
    onSelectBlock: (BlockType) -> Unit,
    onOpenCrafting: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var category by remember { mutableStateOf<BlockCategory?>(null) }

    val blocks = remember(category) {
        if (category == null) BlockType.placeableBlocks
        else BlockType.placeableBlocks.filter { it.category == category }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (uiState.gameMode == GameMode.CREATIVE) "Creative Inventory" else "Survival Inventory",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Tap any block to equip onto your hotbar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onOpenCrafting()
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("inventory_open_crafting_button")
                ) {
                    Icon(Icons.Rounded.Construction, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Craft")
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = category == null,
                        onClick = { category = null },
                        label = { Text("All") }
                    )
                }
                items(BlockCategory.entries) { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = { Text(cat.label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 92.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = 300.dp)
            ) {
                items(blocks, key = { it.id }) { block ->
                    val count = uiState.countOf(block)
                    val available = uiState.gameMode == GameMode.CREATIVE || count > 0
                    val isEquipped = uiState.selectedBlock == block

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isEquipped) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                        border = BorderStroke(
                            width = if (isEquipped) 1.5.dp else 1.dp,
                            color = if (isEquipped) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelectBlock(block) }
                            .testTag("inventory_block_${block.name.lowercase()}")
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            MiniVoxelBlockIcon(block = block, size = 38.dp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = block.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (uiState.gameMode == GameMode.CREATIVE) "∞" else "×$count",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (available) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CraftingBenchSheet(
    uiState: GameWorldUiState,
    canCraft: (CraftingRecipe) -> Boolean,
    onCraft: (CraftingRecipe) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Construction,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Column {
                    Text("Crafting Bench", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        text = "Pickaxe Tier ${uiState.pickaxeTier} • ${uiState.gameMode.displayName} Mode",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = 320.dp)
            ) {
                items(CRAFTING_RECIPES, key = { it.id }) { recipe ->
                    val alreadyOwnedTool = recipe.unlocksPickaxeTier != null &&
                        uiState.pickaxeTier >= recipe.unlocksPickaxeTier
                    val craftable = canCraft(recipe)

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(
                            1.dp,
                            if (craftable) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                            else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (recipe.outputBlock != null) {
                                    MiniVoxelBlockIcon(block = recipe.outputBlock, size = 38.dp)
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Rounded.Construction,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(recipe.title, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        recipe.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                if (alreadyOwnedTool) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = "Unlocked",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Button(
                                        onClick = { onCraft(recipe) },
                                        enabled = craftable,
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        modifier = Modifier.testTag("craft_recipe_${recipe.id}")
                                    ) {
                                        Text("Craft")
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                recipe.ingredients.forEach { (block, needed) ->
                                    val owned = uiState.countOf(block)
                                    val ok = uiState.gameMode == GameMode.CREATIVE || owned >= needed
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (ok) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                        } else {
                                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            MiniVoxelBlockIcon(block = block, size = 16.dp)
                                            val qtyText = if (uiState.gameMode == GameMode.CREATIVE) {
                                                "$needed"
                                            } else {
                                                "$owned/$needed"
                                            }
                                            Text(
                                                text = "${block.displayName} $qtyText",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (ok) MaterialTheme.colorScheme.onPrimaryContainer
                                                else MaterialTheme.colorScheme.onErrorContainer
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorldSettingsSheet(
    uiState: GameWorldUiState,
    onResume: () -> Unit,
    onToggleMode: () -> Unit,
    onSelectGraphicsQuality: (com.example.ui.viewmodel.GraphicsQuality) -> Unit,
    onSelectControlStyle: (com.example.ui.viewmodel.ControlStyle) -> Unit,
    onToggleThirdPerson: () -> Unit,
    onToggleRain: () -> Unit,
    onConnectAnimals: () -> Unit,
    onToggleTouchControls: () -> Unit,
    onToggleAutoCycle: () -> Unit,
    onSetTimeOfDay: (Float) -> Unit,
    onToggleSound: () -> Unit,
    onRotateScreen: () -> Unit,
    onSaveNow: () -> Unit,
    onSaveAndExit: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Creator & Pause Menu", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            text = "${uiState.worldName} • ${uiState.biome.displayName} • Seed ${uiState.seed}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (uiState.isCloudBacked) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        }
                    ) {
                        Text(
                            text = if (uiState.isCloudBacked) "CLOUD SYNC" else "GUEST LOCAL",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (uiState.isCloudBacked) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onResume,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("pause_resume_button")
                    ) {
                        Text("Resume Game")
                    }
                    OutlinedButton(
                        onClick = onRotateScreen,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .height(50.dp)
                            .testTag("pause_rotate_button")
                    ) {
                        Text("Rotate")
                    }
                }
            }

            // Controller Style (MCPE D-Pad vs Virtual Joystick)
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Touch Controller Style", style = MaterialTheme.typography.titleSmall)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            com.example.ui.viewmodel.ControlStyle.entries.forEach { style ->
                                FilterChip(
                                    selected = uiState.controlStyle == style,
                                    onClick = { onSelectControlStyle(style) },
                                    label = { Text(style.label) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("control_style_${style.name.lowercase()}")
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                onConnectAnimals()
                                onDismiss()
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("pause_connect_animals_button")
                        ) {
                            Text("Connect Nearby Animals & Heroes (${uiState.connectedAnimals} Following)")
                        }
                    }
                }
            }

            // Creator Sky Control: Exact Sun Rise / Day / Sunset / Night Slider
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Creator Sun & Night Control", style = MaterialTheme.typography.titleSmall)
                            val phaseLabel = when {
                                uiState.timeOfDay < 0.14f -> "Sunrise"
                                uiState.timeOfDay < 0.42f -> "Daylight"
                                uiState.timeOfDay < 0.56f -> "Sunset"
                                else -> "Starry Night"
                            }
                            Text(
                                text = phaseLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        androidx.compose.material3.Slider(
                            value = uiState.timeOfDay.coerceIn(0f, 1f),
                            onValueChange = onSetTimeOfDay,
                            modifier = Modifier.testTag("creator_time_slider")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "Sunrise" to 0.05f,
                                "Noon" to 0.25f,
                                "Sunset" to 0.49f,
                                "Night" to 0.78f
                            ).forEach { (label, preset) ->
                                FilterChip(
                                    selected = kotlin.math.abs(uiState.timeOfDay - preset) < 0.08f,
                                    onClick = { onSetTimeOfDay(preset) },
                                    label = { Text(label, maxLines = 1) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Auto Day/Night Cycle", style = MaterialTheme.typography.bodySmall)
                            Switch(
                                checked = uiState.autoTimeCycle,
                                onCheckedChange = { onToggleAutoCycle() },
                                modifier = Modifier.testTag("pause_day_night_switch")
                            )
                        }
                    }
                }
            }

            // Graphics Quality (Render Distance) selector
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Graphics Quality (Smooth FPS)", style = MaterialTheme.typography.titleSmall)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            com.example.ui.viewmodel.GraphicsQuality.entries.forEach { quality ->
                                val selected = uiState.graphicsQuality == quality
                                FilterChip(
                                    selected = selected,
                                    onClick = { onSelectGraphicsQuality(quality) },
                                    label = { Text(quality.label) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("graphics_quality_${quality.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            }

            // Mode, Camera, Weather, Sound & Controls Toggles
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Creative Mode", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Flight + unlimited blocks & instant mining",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = uiState.gameMode == GameMode.CREATIVE,
                                onCheckedChange = { onToggleMode() },
                                modifier = Modifier.testTag("pause_creative_switch")
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("3rd-Person Character View", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "See your animated 3D hero walk, swim & swing",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = uiState.thirdPersonCamera,
                                onCheckedChange = { onToggleThirdPerson() },
                                modifier = Modifier.testTag("pause_third_person_switch")
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Rain Weather Animation", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "3D falling raindrops across your voxel world",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = uiState.rainEnabled,
                                onCheckedChange = { onToggleRain() },
                                modifier = Modifier.testTag("pause_rain_switch")
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Real Sound & Nature SFX", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Footsteps, water splashes, bubbles, birds & gunshots",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = uiState.soundEnabled,
                                onCheckedChange = { onToggleSound() },
                                modifier = Modifier.testTag("pause_sound_switch")
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("On-Screen Touch Controls", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "MCPE D-Pad / Joystick, jump, mine & place buttons",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = uiState.showTouchControls,
                                onCheckedChange = { onToggleTouchControls() },
                                modifier = Modifier.testTag("pause_touch_controls_switch")
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onSaveNow,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("sheet_save_cloud_button")
                    ) {
                        Icon(Icons.Rounded.CloudUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (uiState.isCloudBacked) "Save Cloud" else "Save Local")
                    }
                    OutlinedButton(
                        onClick = onSaveAndExit,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("sheet_save_exit_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ExitToApp, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Load World")
                    }
                }
            }
        }
    }
}
