package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddBox
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FlightTakeoff
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Hardware
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowDown
import androidx.compose.material.icons.rounded.LocationCity
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.SwitchCamera
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BlockType
import com.example.data.model.GameMode
import com.example.engine.MiniVoxelBlockIcon
import com.example.engine.gl.CameraMode
import com.example.engine.gl.VoxelPlayerController
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.viewmodel.GameWorldUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.hypot

/**
 * Shared live geometry & visual state for the Bedrock Virtual Joystick.
 * Updated at 60fps directly from the bulletproof full-screen multi-touch router without restarting pointerInput.
 */
class BedrockJoystickState {
    var centerInParent by mutableStateOf(Offset.Zero)
    var outerRadiusPx by mutableFloatStateOf(160f)
    var maxKnobTravelPx by mutableFloatStateOf(105f)
    var knobOffset by mutableStateOf(Offset.Zero)
    var isPressed by mutableStateOf(false)
    var isAutoSprinting by mutableStateOf(false)
}

/**
 * Bulletproof Full-Screen Multi-Touch Container:
 * - Left Virtual Joystick handles its OWN pointer capture directly in local coordinates (Android
 *   guarantees continuous MOVE/UP delivery for that pointerId even far outside the joystick bounds,
 *   eliminating all root-offset or orientation-change glitches!).
 * - This container handles independent Camera Look dragging for any unconsumed finger on the viewport,
 *   so simultaneous Walking + Camera Look + Jump + Break/Place works at 60 FPS with zero stalling!
 */
@Composable
fun BedrockMultiTouchContainer(
    controller: VoxelPlayerController,
    joystickState: BedrockJoystickState,
    joystickEnabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val currentController by rememberUpdatedState(controller)
    val currentJoystickEnabled by rememberUpdatedState(joystickEnabled)

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                var cameraPointerId: PointerId? = null
                var lastCameraPos = Offset.Zero

                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val activeCam = cameraPointerId
                        if (activeCam != null) {
                            val camChange = event.changes.firstOrNull { it.id == activeCam }
                            if (camChange == null || !camChange.pressed) {
                                cameraPointerId = null
                            } else {
                                val rawDx = camChange.position.x - lastCameraPos.x
                                val rawDy = camChange.position.y - lastCameraPos.y
                                lastCameraPos = camChange.position
                                if (rawDx != 0f || rawDy != 0f) {
                                    // Density-normalized smooth camera look with jump clamping (consistent on all phones/tablets!)
                                    val safeDensity = density.coerceAtLeast(1f)
                                    val dpDx = (rawDx / safeDensity).coerceIn(-64f, 64f)
                                    val dpDy = (rawDy / safeDensity).coerceIn(-64f, 64f)
                                    val yawSens = 0.0088f
                                    val pitchSens = 0.0068f
                                    currentController.rotateLook(
                                        deltaYawRad = dpDx * yawSens,
                                        deltaPitchRad = -dpDy * pitchSens
                                    )
                                }
                                camChange.consume()
                            }
                        } else {
                            val topSafePx = 56.dp.toPx()
                            val bottomSafePx = size.height - 72.dp.toPx()
                            val joystickExcludeX = 216.dp.toPx()
                            val joystickExcludeTopY = size.height - 252.dp.toPx()
                            val fresh = event.changes.firstOrNull { ch ->
                                val inJoystickCorner = currentJoystickEnabled &&
                                    ch.position.x < joystickExcludeX &&
                                    ch.position.y > joystickExcludeTopY
                                !ch.isConsumed &&
                                    !inJoystickCorner &&
                                    ch.pressed &&
                                    !ch.previousPressed &&
                                    ch.position.y > topSafePx &&
                                    ch.position.y < bottomSafePx
                            }
                            if (fresh != null) {
                                cameraPointerId = fresh.id
                                lastCameraPos = fresh.position
                                fresh.consume()
                            }
                        }
                    }
                }
            }
            .testTag("bedrock_multitouch_container"),
        content = content
    )
}

/**
 * Self-Contained Zero-Lag Virtual Joystick ("Joystick & Aim Crosshair" mode):
 * - Captures its own finger directly in LOCAL widget coordinates (immune to screen rotation / offset bugs!).
 * - Smooth 360° movement: Forward (+Y), Backward (-Y), Left (-X), Right (+X), plus Auto-Sprint >85% forward.
 */
@Composable
fun BedrockVirtualJoystickVisual(
    state: BedrockJoystickState,
    controller: VoxelPlayerController,
    modifier: Modifier = Modifier,
    padSize: Dp = 182.dp
) {
    val currentController by rememberUpdatedState(controller)

    Box(
        modifier = modifier
            .size(padSize)
            .pointerInput(Unit) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val visualOuterR = minOf(size.width, size.height) * 0.39f
                val maxTravel = (visualOuterR * 0.72f).coerceAtLeast(1f)

                fun applyLocalPos(pos: Offset) {
                    val dx = pos.x - center.x
                    val dy = pos.y - center.y
                    val dist = hypot(dx, dy)
                    val clamped = dist.coerceAtMost(maxTravel)
                    val nx = if (dist > 0.001f) (dx / dist) * (clamped / maxTravel) else 0f
                    val ny = if (dist > 0.001f) (dy / dist) * (clamped / maxTravel) else 0f

                    state.knobOffset = Offset(nx * maxTravel, ny * maxTravel)
                    state.isPressed = true

                    val forward = -ny // Up on screen is +1 forward, Down is -1 backward
                    currentController.moveInputX = nx
                    currentController.moveInputY = forward
                    val autoSprint = forward > 0.82f && !currentController.sneaking
                    currentController.autoSprinting = autoSprint
                    state.isAutoSprinting = autoSprint
                }

                fun resetStick() {
                    state.knobOffset = Offset.Zero
                    state.isPressed = false
                    state.isAutoSprinting = false
                    currentController.moveInputX = 0f
                    currentController.moveInputY = 0f
                    currentController.autoSprinting = false
                }

                awaitPointerEventScope {
                    var activePointerId: PointerId? = null
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val active = activePointerId
                            if (active != null) {
                                val change = event.changes.firstOrNull { it.id == active }
                                if (change == null || !change.pressed) {
                                    change?.consume()
                                    activePointerId = null
                                    resetStick()
                                } else {
                                    applyLocalPos(change.position)
                                    change.consume()
                                }
                            } else {
                                val fresh = event.changes.firstOrNull { !it.isConsumed && it.pressed && !it.previousPressed }
                                if (fresh != null) {
                                    activePointerId = fresh.id
                                    fresh.consume()
                                    applyLocalPos(fresh.position)
                                }
                            }
                        }
                    } finally {
                        resetStick()
                    }
                }
            }
            .testTag("virtual_joystick")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerR = size.minDimension * 0.39f
            val knobR = outerR * 0.42f
            val baseAlpha = if (state.isPressed) 0.54f else 0.36f

            // Classic semi-transparent dark grey circular outer ring
            drawCircle(
                color = Color(0xFF1A1D21).copy(alpha = baseAlpha),
                radius = outerR,
                center = center
            )
            val ringColor = when {
                state.isAutoSprinting -> Color(0xDD53FF9E)
                state.isPressed -> Color(0xCCFFFFFF)
                else -> Color(0x88E2E6EC)
            }
            drawCircle(
                color = ringColor,
                radius = outerR,
                center = center,
                style = Stroke(width = if (state.isAutoSprinting) 2.5.dp.toPx() else 1.8.dp.toPx())
            )

            // Subtle directional chevron arrows inside the joystick base (Up, Down, Left, Right)
            val arrowColor = Color(0x78FFFFFF)
            val arrowDist = outerR * 0.72f
            val arrowHalf = 5.dp.toPx()
            val arrowDepth = 5.dp.toPx()
            val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)

            // Up arrow (brightens during auto-sprint!)
            val upPath = Path().apply {
                moveTo(center.x - arrowHalf, center.y - arrowDist + arrowDepth)
                lineTo(center.x, center.y - arrowDist)
                lineTo(center.x + arrowHalf, center.y - arrowDist + arrowDepth)
            }
            drawPath(
                upPath,
                color = if (state.isAutoSprinting) Color(0xFF6AFFB0) else arrowColor,
                style = stroke
            )

            // Down arrow
            val downPath = Path().apply {
                moveTo(center.x - arrowHalf, center.y + arrowDist - arrowDepth)
                lineTo(center.x, center.y + arrowDist)
                lineTo(center.x + arrowHalf, center.y + arrowDist - arrowDepth)
            }
            drawPath(downPath, color = arrowColor, style = stroke)

            // Left arrow
            val leftPath = Path().apply {
                moveTo(center.x - arrowDist + arrowDepth, center.y - arrowHalf)
                lineTo(center.x - arrowDist, center.y)
                lineTo(center.x - arrowDist + arrowDepth, center.y + arrowHalf)
            }
            drawPath(leftPath, color = arrowColor, style = stroke)

            // Right arrow
            val rightPath = Path().apply {
                moveTo(center.x + arrowDist - arrowDepth, center.y - arrowHalf)
                lineTo(center.x + arrowDist, center.y)
                lineTo(center.x + arrowDist - arrowDepth, center.y + arrowHalf)
            }
            drawPath(rightPath, color = arrowColor, style = stroke)

            // Crisp clamped inner thumb knob
            val thumbCenter = center + state.knobOffset
            drawCircle(
                color = Color(0xFF0E1012).copy(alpha = 0.55f),
                radius = knobR + 2.dp.toPx(),
                center = thumbCenter
            )
            drawCircle(
                color = if (state.isPressed) Color(0xDDE8EDF2) else Color(0xAAB8C0C8),
                radius = knobR,
                center = thumbCenter
            )
            drawCircle(
                color = Color(0xEEFFFFFF),
                radius = knobR,
                center = thumbCenter,
                style = Stroke(width = 1.8.dp.toPx())
            )
        }
    }
}

/**
 * Play Store Minecraft Bedrock Top-Center Compact Control Bar:
 * Small semi-transparent Chat, Pause, and Camera-View toggle buttons.
 */
@Composable
fun BedrockTopCenterBar(
    cameraMode: CameraMode,
    goldCoins: Int,
    adminUnlocked: Boolean,
    ridingBike: Boolean,
    flyingPlane: Boolean = false,
    onOpenBikes: () -> Unit,
    onOpenAdmin: () -> Unit,
    onOpenRealEstate: () -> Unit,
    onOpenAiFriends: () -> Unit,
    onOpenShop: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenPause: () -> Unit,
    onToggleCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BedrockTopSquareButton(
            icon = if (flyingPlane) Icons.Rounded.FlightTakeoff else Icons.Rounded.TwoWheeler,
            contentDescription = "Superbikes & 3D Airplanes Hangar",
            active = ridingBike || flyingPlane,
            badgeText = if (flyingPlane) "JET" else "RIDE",
            onClick = onOpenBikes,
            tag = "top_bikes_button"
        )
        BedrockTopSquareButton(
            icon = Icons.Rounded.LocationCity,
            contentDescription = "Real Estate Township & Properties",
            active = true,
            badgeText = "CITY",
            onClick = onOpenRealEstate,
            tag = "top_real_estate_button"
        )
        BedrockTopSquareButton(
            icon = Icons.Rounded.AdminPanelSettings,
            contentDescription = "Admin Studio Panel",
            active = adminUnlocked,
            onClick = onOpenAdmin,
            tag = "top_admin_button"
        )
        BedrockTopSquareButton(
            icon = Icons.Rounded.RecordVoiceOver,
            contentDescription = "AI Friends Hindi Voice",
            active = true,
            onClick = onOpenAiFriends,
            tag = "top_ai_friends_button"
        )
        BedrockTopSquareButton(
            icon = Icons.Rounded.Storefront,
            contentDescription = "Royal Bazaar Shop ($goldCoins Coins)",
            onClick = onOpenShop,
            tag = "top_shop_button"
        )
        BedrockTopSquareButton(
            icon = Icons.Rounded.ChatBubbleOutline,
            contentDescription = "Chat and World Commands",
            onClick = onOpenChat,
            tag = "top_chat_button"
        )
        BedrockTopSquareButton(
            icon = Icons.Rounded.Pause,
            contentDescription = "Pause Menu",
            onClick = onOpenPause,
            tag = "pause_menu_button"
        )
        BedrockTopSquareButton(
            icon = Icons.Rounded.SwitchCamera,
            contentDescription = "Switch Camera Angle (${cameraMode.displayName})",
            active = cameraMode != CameraMode.FIRST_PERSON,
            badgeText = cameraMode.shortLabel,
            onClick = onToggleCamera,
            tag = "hud_camera_button"
        )
    }
}

@Composable
private fun BedrockTopSquareButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tag: String,
    active: Boolean = false,
    badgeText: String? = null
) {
    var pressed by remember { mutableStateOf(false) }
    val currentOnClick by rememberUpdatedState(onClick)
    val bgAlpha = when {
        pressed -> 0.85f
        active -> 0.72f
        else -> 0.48f
    }

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF1A1D21).copy(alpha = bgAlpha))
            .border(
                width = 1.5.dp,
                color = if (active || pressed) Color(0xEEFFFFFF) else Color(0x77FFFFFF),
                shape = RoundedCornerShape(4.dp)
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    pressed = true
                    val up = waitForUpOrCancellation()
                    pressed = false
                    if (up != null) currentOnClick()
                }
            }
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        if (badgeText != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = if (active) Color(0xFF7CFFB2) else Color.White,
                    modifier = Modifier.size(19.dp)
                )
                Text(
                    text = badgeText,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (active) Color(0xFFFFE57F) else Color(0xDDFFFFFF)
                )
            }
        } else {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (active) Color(0xFF7CFFB2) else Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * Crisp Official Minecraft Bedrock Center `+` Crosshair with optional circular mining crack progress.
 */
@Composable
fun BedrockCrosshairReticle(
    targetBlock: BlockType?,
    miningProgress: Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(modifier = Modifier.size(34.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val arm = 8.5.dp.toPx()

            // Circular mining crack progress ring while holding Break
            if (miningProgress > 0.02f) {
                drawArc(
                    color = Color(0xCC000000),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = 3.5.dp.toPx())
                )
                drawArc(
                    color = Color(0xFFFFFFFF),
                    startAngle = -90f,
                    sweepAngle = 360f * miningProgress.coerceIn(0f, 1f),
                    useCenter = false,
                    style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Butt)
                )
            }

            // Crisp pixel-clean white `+` crosshair with subtle dark halo for legibility on bright sky/snow
            drawLine(Color(0x99000000), Offset(cx - arm - 1f, cy), Offset(cx + arm + 1f, cy), strokeWidth = 4.2f)
            drawLine(Color(0x99000000), Offset(cx, cy - arm - 1f), Offset(cx, cy + arm + 1f), strokeWidth = 4.2f)
            drawLine(Color.White, Offset(cx - arm, cy), Offset(cx + arm, cy), strokeWidth = 2.2f)
            drawLine(Color.White, Offset(cx, cy - arm), Offset(cx, cy + arm), strokeWidth = 2.2f)
        }
        if (targetBlock != null && miningProgress > 0.02f) {
            Text(
                text = targetBlock.displayName,
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 9.sp,
                color = Color(0xEEFFFFFF)
            )
        }
    }
}

/**
 * Official Play Store Minecraft Bedrock Bottom-Right Action Cluster:
 * - Break / Attack (Pickaxe): hold to continuously mine with crack progress; tap to attack/swing
 * - Place / Interact (Block): tap to place selected block immediately on pointerdown
 * - Sneak / Crouch: positioned next to Jump; toggles crouch (lowers camera + anti-fall edge guard)
 * - Jump (`^`): immediate trigger on pointerdown; hold for continuous jumping / swimming; double-tap to fly in Creative
 * - Plus compact Blaster Fire button above for hunting zombies!
 */
@Composable
fun BedrockActionButtonsCluster(
    uiState: GameWorldUiState,
    controller: VoxelPlayerController,
    onBreakProgressChanged: (Float) -> Unit,
    onBreakTrigger: () -> Unit,
    onPlaceTrigger: () -> Unit,
    onJumpHeldChanged: (Boolean) -> Unit,
    onToggleSneak: () -> Unit,
    onToggleFly: () -> Unit,
    onFireGun: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val currentBreakTrigger by rememberUpdatedState(onBreakTrigger)
    val currentBreakProgress by rememberUpdatedState(onBreakProgressChanged)
    val currentPlaceTrigger by rememberUpdatedState(onPlaceTrigger)
    val currentJumpHeld by rememberUpdatedState(onJumpHeldChanged)
    val currentToggleSneak by rememberUpdatedState(onToggleSneak)
    val currentToggleFly by rememberUpdatedState(onToggleFly)
    val currentFireGun by rememberUpdatedState(onFireGun)
    val currentController by rememberUpdatedState(controller)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Upper utility row: Creative Flight Up/Down + Fly toggle + Compact Blaster Fire
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val canFly = uiState.gameMode == GameMode.CREATIVE || uiState.jetBootsUnlocked
            if (canFly && uiState.flying) {
                BedrockTactileHoldButton(
                    icon = Icons.Rounded.ArrowDownward,
                    contentDescription = "Fly Down",
                    size = 48.dp,
                    onHoldChanged = { currentController.flyDownHeld = it },
                    tag = "btn_fly_down"
                )
                BedrockTactileHoldButton(
                    icon = Icons.Rounded.ArrowUpward,
                    contentDescription = "Fly Up",
                    size = 48.dp,
                    onHoldChanged = { currentController.flyUpHeld = it },
                    tag = "btn_fly_up"
                )
            }
            if (canFly) {
                BedrockTactileTapButton(
                    icon = Icons.Rounded.FlightTakeoff,
                    contentDescription = "Toggle Flight",
                    active = uiState.flying,
                    size = 48.dp,
                    onTapDown = { currentToggleFly() },
                    tag = "btn_toggle_fly"
                )
            }
            BedrockTactileTapButton(
                icon = Icons.Rounded.MyLocation,
                contentDescription = "Fire Blaster",
                tint = Color(0xFFFF758F),
                size = 48.dp,
                onTapDown = { currentFireGun() },
                tag = "btn_fire_gun"
            )
        }

        // Middle Bedrock Action Row: Break/Attack (Pickaxe) + Place/Interact (Block)
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // BREAK / ATTACK: immediate tap swing + continuous hold mining with crack progress!
            var breakPressed by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1A1D21).copy(alpha = if (breakPressed) 0.88f else 0.54f))
                    .border(
                        width = if (breakPressed) 2.2.dp else 1.6.dp,
                        color = if (breakPressed) Color.White else Color(0x99E5E9F0),
                        shape = CircleShape
                    )
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            breakPressed = true

                            // Tap triggers immediate break/attack, and holding smoothly charges continuous mining
                            val miningJob = scope.launch {
                                val steps = 6
                                for (step in 1..steps) {
                                    currentBreakProgress(step / steps.toFloat())
                                    delay(36L)
                                }
                                currentBreakTrigger()
                                currentBreakProgress(0f)
                                while (isActive) {
                                    for (step in 1..steps) {
                                        currentBreakProgress(step / steps.toFloat())
                                        delay(42L)
                                    }
                                    currentBreakTrigger()
                                }
                            }
                            val downTime = System.currentTimeMillis()
                            waitForUpOrCancellation()
                            val heldMs = System.currentTimeMillis() - downTime
                            miningJob.cancel()
                            currentBreakProgress(0f)
                            breakPressed = false
                            // Quick tap (<210ms) still attacks / breaks cleanly without lag!
                            if (heldMs < 210L) {
                                currentBreakTrigger()
                            }
                        }
                    }
                    .testTag("btn_break_block"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Hardware,
                    contentDescription = "Break or Attack",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            // PLACE / INTERACT: zero-lag placement on pointerdown
            BedrockTactileTapButton(
                icon = Icons.Rounded.AddBox,
                contentDescription = "Place or Interact",
                size = 58.dp,
                onTapDown = { currentPlaceTrigger() },
                tag = "btn_place_block"
            )
        }

        // Bottom Bedrock Row: Sneak/Crouch next to ergonomic Jump (`^`) button
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // SNEAK / CROUCH toggle next to Jump
            BedrockTactileTapButton(
                icon = Icons.Rounded.KeyboardDoubleArrowDown,
                contentDescription = "Sneak or Crouch",
                active = uiState.sneaking,
                size = 54.dp,
                onTapDown = { currentToggleSneak() },
                tag = "btn_sneak"
            )

            // JUMP (`^`) — triggers IMMEDIATELY on pointerdown, supports holding for continuous jumping/swimming!
            var jumpPressed by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1A1D21).copy(alpha = if (jumpPressed) 0.90f else 0.56f))
                    .border(
                        width = if (jumpPressed) 2.4.dp else 1.8.dp,
                        color = if (jumpPressed) Color.White else Color(0xAAE5E9F0),
                        shape = CircleShape
                    )
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            jumpPressed = true
                            currentJumpHeld(true)
                            waitForUpOrCancellation()
                            jumpPressed = false
                            currentJumpHeld(false)
                        }
                    }
                    .testTag("btn_jump"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (uiState.inWater) Icons.Rounded.Waves else Icons.Rounded.KeyboardArrowUp,
                    contentDescription = if (uiState.inWater) "Swim Up" else "Jump",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
    }
}

@Composable
private fun BedrockTactileTapButton(
    icon: ImageVector,
    contentDescription: String,
    onTapDown: () -> Unit,
    tag: String,
    size: Dp = 56.dp,
    active: Boolean = false,
    tint: Color = Color.White
) {
    var pressed by remember { mutableStateOf(false) }
    val currentTapDown by rememberUpdatedState(onTapDown)
    val bgAlpha = when {
        pressed -> 0.90f
        active -> 0.78f
        else -> 0.54f
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                if (active) Color(0xFF2B343E).copy(alpha = bgAlpha)
                else Color(0xFF1A1D21).copy(alpha = bgAlpha)
            )
            .border(
                width = if (pressed || active) 2.2.dp else 1.6.dp,
                color = if (pressed || active) Color.White else Color(0x99E5E9F0),
                shape = CircleShape
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    pressed = true
                    currentTapDown()
                    waitForUpOrCancellation()
                    pressed = false
                }
            }
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) Color(0xFFFFDF73) else tint,
            modifier = Modifier.size(size * 0.46f)
        )
    }
}

@Composable
fun BedrockTactileHoldButton(
    icon: ImageVector,
    contentDescription: String,
    onHoldChanged: (Boolean) -> Unit,
    tag: String,
    size: Dp = 48.dp
) {
    var pressed by remember { mutableStateOf(false) }
    val currentHoldChanged by rememberUpdatedState(onHoldChanged)

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFF1A1D21).copy(alpha = if (pressed) 0.90f else 0.54f))
            .border(
                width = if (pressed) 2.2.dp else 1.6.dp,
                color = if (pressed) Color.White else Color(0x99E5E9F0),
                shape = CircleShape
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    pressed = true
                    currentHoldChanged(true)
                    waitForUpOrCancellation()
                    pressed = false
                    currentHoldChanged(false)
                }
            }
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * Authentic Minecraft Bedrock Bottom-Center HUD:
 * - Top-Left above hotbar: 10 Pixel-art Hearts (Health)
 * - Top-Right above hotbar: 10 Pixel-art Hunger Drumsticks (+ Oxygen bubbles when underwater)
 * - Green XP Bar + Level Number
 * - Classic 9-Slot Pixel-Bordered Hotbar with bright White Active Slot Highlight + `...` Inventory slot on the right end.
 */
@Composable
fun BedrockHotbarAndVitals(
    uiState: GameWorldUiState,
    onSelectSlot: (Int) -> Unit,
    onOpenInventory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCreative = uiState.gameMode == GameMode.CREATIVE
    val showOxygen = uiState.isUnderwater || uiState.oxygen < 10

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        // Hearts (top-left) & Hunger/Oxygen (top-right) directly above the hotbar
        if (!isCreative || showOxygen) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                // Pixel-Art Hearts (10)
                if (!isCreative) {
                    Canvas(modifier = Modifier.size(width = 114.dp, height = 12.dp)) {
                        val slotW = size.width / 10f
                        for (i in 0 until 10) {
                            val filled = i < uiState.playerHealth
                            val x = i * slotW + 1f
                            val px = (slotW - 2f) / 7f
                            val y0 = (size.height - px * 6f) / 2f
                            // Crisp 7x6 pixel heart bitmap
                            val borderC = Color(0xEE14090C)
                            val fillC = if (filled) Color(0xFFE5253A) else Color(0x882A2A2D)
                            val shineC = Color(0xFFFF8C9A)
                            // Pixel outline silhouette
                            drawRect(borderC, Offset(x + px, y0), Size(px * 2f, px))
                            drawRect(borderC, Offset(x + px * 4f, y0), Size(px * 2f, px))
                            drawRect(borderC, Offset(x, y0 + px), Size(px * 7f, px * 2f))
                            drawRect(borderC, Offset(x + px, y0 + px * 3f), Size(px * 5f, px))
                            drawRect(borderC, Offset(x + px * 2f, y0 + px * 4f), Size(px * 3f, px))
                            drawRect(borderC, Offset(x + px * 3f, y0 + px * 5f), Size(px, px))
                            // Inner heart fill
                            drawRect(fillC, Offset(x + px, y0 + px), Size(px * 2f, px * 2f))
                            drawRect(fillC, Offset(x + px * 4f, y0 + px), Size(px * 2f, px * 2f))
                            drawRect(fillC, Offset(x + px * 2f, y0 + px * 2f), Size(px * 3f, px * 2f))
                            drawRect(fillC, Offset(x + px * 3f, y0 + px * 4f), Size(px, px))
                            if (filled) {
                                drawRect(shineC, Offset(x + px, y0 + px), Size(px, px))
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.width(114.dp))
                }

                // Pixel-Art Oxygen Bubbles (when underwater) + Pixel-Art Hunger Drumsticks (10)
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (showOxygen) {
                        Canvas(modifier = Modifier.size(width = 114.dp, height = 11.dp)) {
                            val slotW = size.width / 10f
                            for (i in 0 until 10) {
                                val filled = i < uiState.oxygen
                                val cx = size.width - (i * slotW + slotW * 0.5f)
                                val cy = size.height * 0.5f
                                val r = slotW * 0.36f
                                drawCircle(
                                    color = if (filled) Color(0xFF3CD4FF) else Color(0x441B303A),
                                    radius = r,
                                    center = Offset(cx, cy)
                                )
                                drawCircle(
                                    color = if (filled) Color(0xFFDFFAFF) else Color(0x66406575),
                                    radius = r,
                                    center = Offset(cx, cy),
                                    style = Stroke(width = 1.3f)
                                )
                                if (filled) {
                                    drawCircle(
                                        color = Color.White,
                                        radius = r * 0.32f,
                                        center = Offset(cx - r * 0.28f, cy - r * 0.28f)
                                    )
                                }
                            }
                        }
                    }

                    if (!isCreative) {
                        Canvas(modifier = Modifier.size(width = 114.dp, height = 12.dp)) {
                            val slotW = size.width / 10f
                            for (i in 0 until 10) {
                                val filled = i < uiState.hunger
                                val x = size.width - ((i + 1) * slotW) + 1f
                                val px = (slotW - 2f) / 7f
                                val y0 = (size.height - px * 6f) / 2f
                                val borderC = Color(0xEE1A1008)
                                val meatC = if (filled) Color(0xFFB86B2B) else Color(0x882A2A2D)
                                val roastC = if (filled) Color(0xFFE0954B) else Color(0x663A3A3D)
                                val boneC = if (filled) Color(0xFFF3E7D3) else Color(0x66555558)
                                // Pixel-art drumstick outline + meat + white bone handle
                                drawRect(borderC, Offset(x, y0), Size(px * 5f, px * 4f))
                                drawRect(meatC, Offset(x + px * 0.6f, y0 + px * 0.6f), Size(px * 3.8f, px * 2.8f))
                                drawRect(roastC, Offset(x + px, y0 + px), Size(px * 1.6f, px * 1.4f))
                                drawRect(borderC, Offset(x + px * 4f, y0 + px * 3f), Size(px * 3f, px * 3f))
                                drawRect(boneC, Offset(x + px * 4.5f, y0 + px * 3.5f), Size(px * 2f, px * 2f))
                            }
                        }
                    }
                }
            }
        }

        // Authentic thin green XP Bar with level number
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .height(11.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
            ) {
                drawRect(color = Color(0xCC111417))
                drawRect(
                    color = Color(0xFF72FF47),
                    size = Size(size.width * uiState.xpProgress.coerceIn(0f, 1f), size.height)
                )
                drawRect(
                    color = Color(0x88000000),
                    style = Stroke(width = 1.2f)
                )
            }
            Text(
                text = "${uiState.xpLevel}",
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF8CFF5E)
            )
        }

        // Classic 9-Slot Minecraft Bedrock Pixel-Bordered Hotbar + `...` Inventory slot on the right end
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val totalSlots = (uiState.hotbar.size.coerceAtLeast(1) + 1)
            val slotSize = ((maxWidth - 28.dp) / totalSlots).coerceIn(32.dp, 46.dp)
            val iconSize = (slotSize * 0.56f).coerceIn(18.dp, 26.dp)
            val currentOnSelect by rememberUpdatedState(onSelectSlot)
            val currentOnInventory by rememberUpdatedState(onOpenInventory)

            Box(
                modifier = Modifier
                    .background(Color(0xCC2A2D32), RoundedCornerShape(3.dp))
                    .border(2.dp, Color(0xEE141618), RoundedCornerShape(3.dp))
                    .padding(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    uiState.hotbar.forEachIndexed { index, block ->
                        val selected = index == uiState.selectedHotbarIndex && !uiState.gunEquipped
                        val count = uiState.countOf(block)
                        val isUnlimited = isCreative ||
                            block == BlockType.WATER || block == BlockType.WATERFALL

                        Box(
                            modifier = Modifier
                                .size(slotSize)
                                .background(
                                    if (selected) Color(0xDD44484F) else Color(0xCC1C1F23),
                                    RoundedCornerShape(2.dp)
                                )
                                .border(
                                    width = if (selected) 2.5.dp else 1.2.dp,
                                    color = if (selected) Color.White else Color(0xFF555A62),
                                    shape = RoundedCornerShape(2.dp)
                                )
                                .pointerInput(index) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        down.consume()
                                        currentOnSelect(index)
                                        waitForUpOrCancellation()
                                    }
                                }
                                .testTag("hotbar_slot_$index")
                        ) {
                            MiniVoxelBlockIcon(
                                block = block,
                                size = iconSize,
                                modifier = Modifier.align(Alignment.Center)
                            )
                            Text(
                                text = if (isUnlimited) "∞" else "$count",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = if (slotSize < 38.dp) 7.sp else 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isUnlimited || count > 0) Color.White else Color(0xFFFF7B7B),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 2.dp, bottom = 1.dp)
                            )
                        }
                    }

                    // Authentic Minecraft Bedrock `...` Inventory Slot on the right end of the hotbar
                    Box(
                        modifier = Modifier
                            .size(slotSize)
                            .background(Color(0xCC24282D), RoundedCornerShape(2.dp))
                            .border(1.4.dp, Color(0xFF6A7078), RoundedCornerShape(2.dp))
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    down.consume()
                                    waitForUpOrCancellation()
                                    currentOnInventory()
                                }
                            }
                            .testTag("hotbar_inventory_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreHoriz,
                            contentDescription = "Open Inventory",
                            tint = Color.White,
                            modifier = Modifier.size(iconSize)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Official Bedrock-Style Chat & Quick Commands Sheet opened from the Top-Center Chat button.
 * Gives fast access to slash commands (/day, /night, /rain, /pets) + World Map, Crafting Bench, & Character Spawner.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BedrockChatSheet(
    uiState: GameWorldUiState,
    onSendCommand: (String) -> Unit,
    onOpenSpawner: () -> Unit,
    onOpenCrafting: () -> Unit,
    onOpenMap: () -> Unit,
    onConnectAnimals: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var inputText by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("World Chat & Quick Commands", style = MaterialTheme.typography.headlineMedium)

            // Quick Action Row (Spawner, Crafting, World Map, Connect Pets)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onOpenSpawner()
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("chat_open_spawner")
                    ) {
                        Icon(Icons.Rounded.Groups, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Characters & Mobs")
                    }
                }
                item {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onOpenCrafting()
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("chat_open_crafting")
                    ) {
                        Icon(Icons.Rounded.Construction, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Crafting Bench")
                    }
                }
                item {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onOpenMap()
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("chat_open_map")
                    ) {
                        Icon(Icons.Rounded.Explore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("World Map")
                    }
                }
                item {
                    OutlinedButton(
                        onClick = {
                            onConnectAnimals()
                            onDismiss()
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("chat_connect_pets")
                    ) {
                        Icon(Icons.Rounded.Pets, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Connect Pets (${uiState.connectedAnimals})")
                    }
                }
            }

            // One-tap Slash Commands
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(listOf("/day", "/night", "/sunrise", "/rain", "/creative", "/survival", "/zombies")) { cmd ->
                    FilterChip(
                        selected = false,
                        onClick = { onSendCommand(cmd) },
                        label = { Text(cmd, fontFamily = JetBrainsMonoFontFamily, fontSize = 11.sp) }
                    )
                }
            }

            // Chat log
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp)
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(10.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    items(uiState.chatMessages) { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Input bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Type message or /day, /night, /rain...") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_command_input")
                )
                Button(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            onSendCommand(inputText)
                            inputText = ""
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("chat_send_button"),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send")
                }
            }
        }
    }
}
