package com.example.ui.screens

import android.content.pm.ActivityInfo
import android.opengl.GLSurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.firebase.findActivity
import com.example.engine.gl.VoxelGLRenderer
import com.example.engine.gl.VoxelPlayerController
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.viewmodel.ControlStyle
import com.example.ui.viewmodel.GameViewModel
import kotlinx.coroutines.delay
import kotlin.math.hypot

@Composable
fun GameWorldScreen(
    viewModel: GameViewModel,
    onExitToHub: () -> Unit
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val joystickState = remember { BedrockJoystickState() }

    var isLandscapeLocked by remember { mutableStateOf(false) }
    var showInventorySheet by remember { mutableStateOf(false) }
    var showCraftingSheet by remember { mutableStateOf(false) }
    var showPauseSheet by remember { mutableStateOf(false) }
    var showSpawnerSheet by remember { mutableStateOf(false) }
    var showFullMapSheet by remember { mutableStateOf(false) }
    var showChatSheet by remember { mutableStateOf(false) }
    var miningProgress by remember { mutableFloatStateOf(0f) }

    // Immersive Full-Screen Mode (hides system bars while playing; restores on exit)
    DisposableEffect(activity) {
        val window = activity?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val toggleOrientation: () -> Unit = {
        val nextLandscape = !isLandscapeLocked
        isLandscapeLocked = nextLandscape
        activity?.requestedOrientation = if (nextLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    LaunchedEffect(uiState.statusToast) {
        if (uiState.statusToast != null) {
            delay(2100L)
            viewModel.clearToast()
        }
    }

    val anySheetOpen = showInventorySheet || showCraftingSheet || showPauseSheet ||
        showSpawnerSheet || showFullMapSheet || showChatSheet
    LaunchedEffect(anySheetOpen) {
        viewModel.setPaused(anySheetOpen)
    }

    BackHandler(enabled = !uiState.loading) {
        when {
            showChatSheet -> showChatSheet = false
            showInventorySheet -> showInventorySheet = false
            showCraftingSheet -> showCraftingSheet = false
            showSpawnerSheet -> showSpawnerSheet = false
            showFullMapSheet -> showFullMapSheet = false
            showPauseSheet -> showPauseSheet = false
            else -> showPauseSheet = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A110E))
    ) {
        when {
            uiState.loading -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "Forging 3D World, Oceans, Gardens & Characters...",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            uiState.errorMessage != null -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = uiState.errorMessage ?: "Unable to load world.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Button(onClick = onExitToHub) {
                        Text("Back to Worlds")
                    }
                }
            }

            else -> {
                val renderer = viewModel.glRenderer
                val controller = viewModel.playerController

                if (renderer != null && controller != null) {
                    val useBedrockJoystick = uiState.showTouchControls && uiState.controlStyle == ControlStyle.JOYSTICK

                    // Bulletproof Full-Screen 2-Pass Multi-Touch Container (Independent Joystick & Camera pointerIds)
                    BedrockMultiTouchContainer(
                        controller = controller,
                        joystickState = joystickState,
                        joystickEnabled = useBedrockJoystick,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // 1. Full-Screen OpenGL ES 2.0 3D Viewport
                        VoxelGLViewport(
                            renderer = renderer,
                            modifier = Modifier.fillMaxSize()
                        )

                        // 2. Animated Underwater Caustics & Rising Air Bubbles Overlay
                        UnderwaterAnimationOverlay(
                            isUnderwater = uiState.isUnderwater,
                            modifier = Modifier.fillMaxSize()
                        )

                        // 3. First-Person Viewmodel: Hand Holding Active 3D Block OR Blaster
                        if (!uiState.thirdPersonCamera) {
                            if (uiState.gunEquipped) {
                                FirstPersonBlasterOverlay(
                                    recoilTick = uiState.recoilTick,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(end = 94.dp, bottom = 78.dp)
                                )
                            } else {
                                FirstPersonHandAndBlockOverlay(
                                    selectedBlock = uiState.selectedBlock,
                                    swingTick = uiState.handSwingTick,
                                    inWater = uiState.inWater,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(end = 92.dp, bottom = 76.dp)
                                )
                            }
                        }

                        // 4. Center Crisp White `+` Crosshair & Mining Crack Ring
                        BedrockCrosshairReticle(
                            targetBlock = uiState.targetBlock,
                            miningProgress = miningProgress,
                            modifier = Modifier.align(Alignment.Center)
                        )

                        // 5. Top-Left Classic Bedrock Position Badge + Compact Minimap Preview
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = 10.dp, top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(Color(0x8814171A), RoundedCornerShape(3.dp))
                                    .border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(3.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Pos: ${uiState.playerX}, ${uiState.playerY}, ${uiState.playerZ} • ${uiState.fps} FPS",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xF0FFFFFF)
                                )
                            }
                            WorldMinimapWidget(
                                colors = uiState.minimapColors,
                                blips = uiState.minimapBlips,
                                playerYawRad = uiState.playerYaw,
                                onClick = { showFullMapSheet = true },
                                size = 72.dp
                            )
                        }

                        // 6. Official Play Store Minecraft Bedrock Top-Center Bar (Chat, Pause, Camera)
                        BedrockTopCenterBar(
                            thirdPersonCamera = uiState.thirdPersonCamera,
                            onOpenChat = { showChatSheet = true },
                            onOpenPause = { showPauseSheet = true },
                            onToggleCamera = viewModel::toggleThirdPersonCamera,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 8.dp)
                        )

                        // 7. Subtle Status Toast Banner (below Top-Center Bar)
                        AnimatedVisibility(
                            visible = uiState.statusToast != null,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 62.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xCC14181C),
                                border = BorderStroke(1.dp, Color(0x66FFFFFF))
                            ) {
                                Text(
                                    text = uiState.statusToast.orEmpty(),
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 11.sp,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                )
                            }
                        }

                        // 8. Left Side (Bottom-Left): Official Bedrock Virtual Joystick (or optional D-Pad)
                        if (uiState.showTouchControls) {
                            if (uiState.controlStyle == ControlStyle.JOYSTICK) {
                                BedrockVirtualJoystickVisual(
                                    state = joystickState,
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(start = 20.dp, bottom = 78.dp)
                                )
                            } else {
                                McpeClassicDpad(
                                    controller = controller,
                                    sneaking = uiState.sneaking,
                                    onToggleSneak = viewModel::toggleSneak,
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(start = 16.dp, bottom = 78.dp)
                                )
                            }

                            // 9. Right Side (Bottom-Right): Bedrock Action Cluster (Jump ^, Sneak, Break, Place)
                            BedrockActionButtonsCluster(
                                uiState = uiState,
                                controller = controller,
                                onBreakProgressChanged = { miningProgress = it },
                                onBreakTrigger = {
                                    if (viewModel.breakTargetedBlock()) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                },
                                onPlaceTrigger = {
                                    if (viewModel.placeBlockAtTarget()) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                },
                                onJumpHeldChanged = { held ->
                                    viewModel.setJumpHeld(held)
                                },
                                onToggleSneak = viewModel::toggleSneak,
                                onToggleFly = viewModel::toggleFlyMode,
                                onFireGun = {
                                    viewModel.fireGun()
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 18.dp, bottom = 76.dp)
                            )
                        }

                        // 10. Bottom Center: Pixel Hearts & Hunger + XP Bar + 9-Slot Pixel Hotbar + `...` Inventory
                        BedrockHotbarAndVitals(
                            uiState = uiState,
                            onSelectSlot = viewModel::selectHotbarSlot,
                            onOpenInventory = { showInventorySheet = true },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .widthIn(max = 490.dp)
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }

    if (showChatSheet) {
        BedrockChatSheet(
            uiState = uiState,
            onSendCommand = viewModel::sendChatCommand,
            onOpenSpawner = { showSpawnerSheet = true },
            onOpenCrafting = { showCraftingSheet = true },
            onOpenMap = { showFullMapSheet = true },
            onConnectAnimals = viewModel::connectNearbyAnimals,
            onDismiss = { showChatSheet = false }
        )
    }

    if (showInventorySheet) {
        BlockInventorySheet(
            uiState = uiState,
            onSelectBlock = { block ->
                viewModel.equipBlockToHotbar(block)
                showInventorySheet = false
            },
            onOpenCrafting = { showCraftingSheet = true },
            onDismiss = { showInventorySheet = false }
        )
    }

    if (showCraftingSheet) {
        CraftingBenchSheet(
            uiState = uiState,
            canCraft = viewModel::canCraft,
            onCraft = viewModel::craftRecipe,
            onDismiss = { showCraftingSheet = false }
        )
    }

    if (showSpawnerSheet) {
        CharacterSpawnerSheet(
            uiState = uiState,
            onSpawnEntity = viewModel::spawnCharacter,
            onSpawnZombieWave = viewModel::spawnZombieWave,
            onDismiss = { showSpawnerSheet = false }
        )
    }

    if (showFullMapSheet) {
        FullWorldMapSheet(
            uiState = uiState,
            onTeleport = viewModel::teleportToLandmark,
            onDismiss = { showFullMapSheet = false }
        )
    }

    if (showPauseSheet) {
        WorldSettingsSheet(
            uiState = uiState,
            onResume = { showPauseSheet = false },
            onToggleMode = viewModel::toggleGameMode,
            onSelectGraphicsQuality = viewModel::setGraphicsQuality,
            onSelectControlStyle = viewModel::setControlStyle,
            onToggleThirdPerson = viewModel::toggleThirdPersonCamera,
            onToggleRain = viewModel::toggleRain,
            onConnectAnimals = viewModel::connectNearbyAnimals,
            onToggleTouchControls = viewModel::toggleTouchControls,
            onToggleAutoCycle = viewModel::toggleAutoTimeCycle,
            onSetTimeOfDay = viewModel::setTimeOfDayManual,
            onToggleSound = viewModel::toggleSound,
            onRotateScreen = toggleOrientation,
            onSaveNow = { viewModel.saveWorld() },
            onSaveAndExit = {
                showPauseSheet = false
                viewModel.saveWorld(onComplete = onExitToHub)
            },
            onDismiss = { showPauseSheet = false }
        )
    }
}

@Composable
private fun VoxelGLViewport(
    renderer: VoxelGLRenderer,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var glSurfaceView by remember { mutableStateOf<GLSurfaceView?>(null) }

    DisposableEffect(lifecycleOwner, glSurfaceView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> glSurfaceView?.onResume()
                Lifecycle.Event.ON_PAUSE -> glSurfaceView?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            glSurfaceView?.onPause()
        }
    }

    AndroidView(
        factory = { ctx ->
            GLSurfaceView(ctx).apply {
                setEGLContextClientVersion(2)
                preserveEGLContextOnPause = true
                setRenderer(renderer)
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                glSurfaceView = this
            }
        },
        modifier = modifier
    )
}

/**
 * Optional classic MCPE 3×3 Directional D-Pad (selectable in Pause Menu alongside Bedrock Joystick).
 */
@Composable
private fun McpeClassicDpad(
    controller: VoxelPlayerController,
    sneaking: Boolean,
    onToggleSneak: () -> Unit,
    modifier: Modifier = Modifier,
    cellSize: Dp = 46.dp
) {
    var activeDirX by remember { mutableFloatStateOf(0f) }
    var activeDirY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .size(cellSize * 3)
            .pointerInput(Unit) {
                val thirdW = size.width / 3f
                val thirdH = size.height / 3f

                fun applyTouch(pos: Offset): Boolean {
                    val col = (pos.x / thirdW).toInt().coerceIn(0, 2)
                    val row = (pos.y / thirdH).toInt().coerceIn(0, 2)
                    if (col == 1 && row == 1) {
                        activeDirX = 0f
                        activeDirY = 0f
                        controller.moveInputX = 0f
                        controller.moveInputY = 0f
                        return true
                    }
                    val dx = when (col) { 0 -> -1f; 2 -> 1f; else -> 0f }
                    val dy = when (row) { 0 -> 1f; 2 -> -1f; else -> 0f }
                    val len = hypot(dx, dy).coerceAtLeast(1f)
                    activeDirX = dx / len
                    activeDirY = dy / len
                    controller.moveInputX = activeDirX
                    controller.moveInputY = activeDirY
                    return false
                }

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startedOnCenter = applyTouch(down.position)
                    var stayedOnCenter = startedOnCenter
                    down.consume()

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) break
                        val onCenterNow = applyTouch(change.position)
                        if (!onCenterNow) stayedOnCenter = false
                        change.consume()
                    }
                    activeDirX = 0f
                    activeDirY = 0f
                    controller.moveInputX = 0f
                    controller.moveInputY = 0f
                    if (startedOnCenter && stayedOnCenter) onToggleSneak()
                }
            }
            .testTag("mcpe_dpad")
    ) {
        DpadTile(Icons.Rounded.ArrowUpward, "Forward", activeDirY > 0.35f, Modifier.size(cellSize).align(Alignment.TopCenter).padding(2.dp))
        DpadTile(Icons.Rounded.ArrowDownward, "Back", activeDirY < -0.35f, Modifier.size(cellSize).align(Alignment.BottomCenter).padding(2.dp))
        DpadTile(Icons.AutoMirrored.Rounded.ArrowBack, "Left", activeDirX < -0.35f, Modifier.size(cellSize).align(Alignment.CenterStart).padding(2.dp))
        DpadTile(Icons.AutoMirrored.Rounded.ArrowForward, "Right", activeDirX > 0.35f, Modifier.size(cellSize).align(Alignment.CenterEnd).padding(2.dp))
        Box(
            modifier = Modifier
                .size(cellSize)
                .align(Alignment.Center)
                .padding(3.dp)
                .background(if (sneaking) Color(0xCC3C444D) else Color(0x881A1D21), RoundedCornerShape(4.dp))
                .border(1.4.dp, if (sneaking) Color.White else Color(0x77FFFFFF), RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (sneaking) "LOW" else "SNEAK",
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun DpadTile(icon: ImageVector, contentDescription: String, active: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color(0xFF1A1D21).copy(alpha = if (active) 0.88f else 0.50f), RoundedCornerShape(4.dp))
            .border(1.4.dp, if (active) Color.White else Color(0x88FFFFFF), RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}
