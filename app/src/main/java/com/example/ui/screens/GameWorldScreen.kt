package com.example.ui.screens

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.opengl.GLSurfaceView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material.icons.rounded.WavingHand
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.firebase.findActivity
import com.example.engine.gl.AiGoal
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
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val joystickState = remember { BedrockJoystickState() }

    var showInventorySheet by remember { mutableStateOf(false) }
    var showCraftingSheet by remember { mutableStateOf(false) }
    var showPauseSheet by remember { mutableStateOf(false) }
    var showSpawnerSheet by remember { mutableStateOf(false) }
    var showFullMapSheet by remember { mutableStateOf(false) }
    var showChatSheet by remember { mutableStateOf(false) }
    var showAiFriendSheet by remember { mutableStateOf(false) }
    var showShopSheet by remember { mutableStateOf(false) }
    var showAdminSheet by remember { mutableStateOf(false) }
    var showRealEstateSheet by remember { mutableStateOf(false) }
    var showBikeSheet by remember { mutableStateOf(false) }
    var miningProgress by remember { mutableFloatStateOf(0f) }
    var micAskedOnLaunch by remember { mutableStateOf(false) }
    val damageFlashAlpha = remember { Animatable(0f) }

    val hudMicPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && uiState.nearbyEntity != null && micAskedOnLaunch) {
            // Mic ready for instant live Hindi voice conversation!
        }
    }

    // Automatically request Microphone permission right when gameplay starts so live voice talking works immediately!
    LaunchedEffect(uiState.loading) {
        if (!uiState.loading && !micAskedOnLaunch) {
            micAskedOnLaunch = true
            val hasMic = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasMic) {
                hudMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    // Player Damage Red Vignette Pulse Animation
    LaunchedEffect(uiState.playerDamageFlashTick) {
        if (uiState.playerDamageFlashTick > 0) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            damageFlashAlpha.snapTo(0.85f)
            damageFlashAlpha.animateTo(0f, animationSpec = tween(durationMillis = 360))
        }
    }

    // Auto-clear live speech subtitle after 5.5s
    LaunchedEffect(uiState.liveSubtitleText) {
        if (uiState.liveSubtitleText != null) {
            delay(5500L)
            viewModel.clearLiveSubtitle()
        }
    }

    // Mandatory Landscape Mode & Immersive Full-Screen while playing (restores on exit)
    DisposableEffect(activity) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
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

    val requestLandscapeNow: () -> Unit = {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    LaunchedEffect(uiState.statusToast) {
        if (uiState.statusToast != null) {
            delay(2500L)
            viewModel.clearToast()
        }
    }

    val anySheetOpen = showInventorySheet || showCraftingSheet || showPauseSheet ||
        showSpawnerSheet || showFullMapSheet || showChatSheet ||
        showAiFriendSheet || showShopSheet || showAdminSheet || showRealEstateSheet || showBikeSheet

    // Pause simulation whenever a modal sheet is open OR the phone is not yet rotated to Landscape!
    LaunchedEffect(anySheetOpen, isLandscape) {
        viewModel.setPaused(anySheetOpen || !isLandscape)
    }

    BackHandler(enabled = !uiState.loading) {
        when {
            showBikeSheet -> showBikeSheet = false
            showRealEstateSheet -> showRealEstateSheet = false
            showAdminSheet -> showAdminSheet = false
            showAiFriendSheet -> showAiFriendSheet = false
            showShopSheet -> showShopSheet = false
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
                        text = "Forging Mountains, Volcanoes, Mandir, Rivers & AI Society...",
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

                        // 2. Animated Underwater Caustics Overlay
                        UnderwaterAnimationOverlay(
                            isUnderwater = uiState.isUnderwater,
                            modifier = Modifier.fillMaxSize()
                        )

                        // 2b. Molten Lava Heat Glow Vignette when touching Volcano / Underground Lava
                        if (uiState.inLava) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.radialGradient(
                                            listOf(Color(0x33FF6D00), Color(0x88D50000))
                                        )
                                    )
                            )
                        }

                        // 2c. High-Impact Player Damage Red Flash Vignette (Enemies / Lava hit feedback!)
                        if (damageFlashAlpha.value > 0.02f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.radialGradient(
                                            listOf(
                                                Color.Transparent,
                                                Color(0xFFE5192E).copy(alpha = damageFlashAlpha.value * 0.72f)
                                            )
                                        )
                                    )
                            )
                        }

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

                        // 5. Top-Left Always-Accessible HUD Hide/Unhide Eye Button + Collapsible Pos/Avatar/Minimap
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = 10.dp, top = 8.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // 1-Tap Full-Screen View Toggle: Hide or Unhide ALL Top HUD Buttons
                            Surface(
                                shape = CircleShape,
                                color = if (uiState.showTopHud) Color(0x9914171A) else Color(0xCC112A1F),
                                border = BorderStroke(
                                    width = 1.2.dp,
                                    color = if (uiState.showTopHud) Color(0x66FFFFFF) else Color(0xFF53FF9E)
                                ),
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .clickable { viewModel.toggleTopHud() }
                                    .testTag("toggle_top_hud_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (uiState.showTopHud) {
                                            Icons.Rounded.VisibilityOff
                                        } else {
                                            Icons.Rounded.Visibility
                                        },
                                        contentDescription = if (uiState.showTopHud) "Hide Top HUD Buttons" else "Show Top HUD Buttons",
                                        tint = if (uiState.showTopHud) Color.White else Color(0xFF53FF9E),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            AnimatedVisibility(
                                visible = uiState.showTopHud,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier
                                            .background(Color(0x8814171A), RoundedCornerShape(6.dp))
                                            .border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                    ) {
                                        Image(
                                            painter = painterResource(id = R.drawable.player_custom_avatar),
                                            contentDescription = "Player Character Portrait",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clip(CircleShape)
                                                .border(1.dp, Color(0xFF53FF9E), CircleShape)
                                        )
                                        Text(
                                            text = "Pos: ${uiState.playerX}, ${uiState.playerY}, ${uiState.playerZ} • ${uiState.fps} FPS • 🪙 ${uiState.goldCoins}",
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
                                        size = 82.dp
                                    )
                                }
                            }
                        }

                        // 6. Top-Center Bar (Hideable for Clean Full-Screen 3D Gameplay!)
                        AnimatedVisibility(
                            visible = uiState.showTopHud,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 8.dp)
                        ) {
                            BedrockTopCenterBar(
                                cameraMode = uiState.cameraMode,
                                goldCoins = uiState.goldCoins,
                                adminUnlocked = uiState.adminSnapshot.isUnlockedInSession,
                                ridingBike = uiState.activeBike != null,
                                onOpenBikes = { showBikeSheet = true },
                                onOpenAdmin = { showAdminSheet = true },
                                onOpenRealEstate = { showRealEstateSheet = true },
                                onOpenAiFriends = {
                                    viewModel.refreshTalkableNpcs()
                                    showAiFriendSheet = true
                                },
                                onOpenShop = { showShopSheet = true },
                                onOpenChat = { showChatSheet = true },
                                onOpenPause = { showPauseSheet = true },
                                onToggleCamera = viewModel::toggleThirdPersonCamera
                            )
                        }

                        // 6b. Top-Right Player-Controlled "Stopped — Say Hello" Conversation Card
                        // Hidden cleanly whenever Fullscreen Eye (!showTopHud) OR Voice Hide (!showVoiceHud) is toggled!
                        val activeNearby = uiState.activeNearbyEntity
                        if (uiState.showTopHud && activeNearby != null && !uiState.showVoiceHud) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xCC111C17),
                                border = BorderStroke(1.dp, Color(0xAA53FF9E)),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 8.dp, end = 10.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { viewModel.toggleVoiceHud() }
                                    .testTag("hud_show_voice_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.RecordVoiceOver,
                                        contentDescription = "Show Voice Conversation",
                                        tint = Color(0xFF53FF9E),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Voice",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        } else if (uiState.showTopHud && uiState.showVoiceHud && activeNearby != null) {
                            val helloActive = uiState.isHelloConversationActive
                            val nearbyCount = uiState.nearbyEntities.size
                            val currentIdx = uiState.nearbyEntities.indexOfFirst { it.id == activeNearby.id }.coerceAtLeast(0) + 1

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xE0111C17),
                                border = BorderStroke(
                                    width = 1.5.dp,
                                    color = when {
                                        uiState.isVoiceListening -> Color(0xFFFF5252)
                                        helloActive -> Color(0xFF53FF9E)
                                        else -> Color(0xFFFFD54F)
                                    }
                                ),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 8.dp, end = 10.dp)
                                    .widthIn(max = 248.dp)
                                    .testTag("hud_proximity_voice_card")
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = activeNearby.displayName + if (helloActive && uiState.isVoiceSpeaking) " 🔊" else "",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFFFE57F),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = when {
                                                    uiState.isVoiceListening -> "सुन रहा हूँ... हेलो या काम बोलिए!"
                                                    helloActive -> activeNearby.goalLabelHindi
                                                    else -> "रुक गए हैं • बात करने के लिए हेलो कहें"
                                                },
                                                fontSize = 9.sp,
                                                color = Color(0xFFD6ECE1),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        if (nearbyCount > 1) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0x33FFFFFF),
                                                border = BorderStroke(1.dp, Color(0x66FFFFFF)),
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable { viewModel.cycleNearbyEntity() }
                                                    .testTag("hud_cycle_nearby_button")
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.SwapHoriz,
                                                        contentDescription = "Choose another nearby character",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                    Text(
                                                        text = "$currentIdx/$nearbyCount",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                }
                                            }
                                        }

                                        if (helloActive) {
                                            Box(
                                                modifier = Modifier
                                                    .size(26.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0x44FF5252))
                                                    .clickable { viewModel.endNearbyConversation() }
                                                    .testTag("hud_end_hello_button"),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Close,
                                                    contentDescription = "End Conversation",
                                                    tint = Color(0xFFFFB4B4),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }

                                        // 1-Tap Voice Hide Button (independent of Top HUD toggle)
                                        Box(
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clip(CircleShape)
                                                .background(Color(0x33FFFFFF))
                                                .clickable { viewModel.toggleVoiceHud() }
                                                .testTag("hud_hide_voice_button"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.VisibilityOff,
                                                contentDescription = "Hide Voice Conversation",
                                                tint = Color(0xFFD6ECE1),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }

                                    if (!helloActive) {
                                        // Waiting silently until player says Hello!
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0xFF22A06B),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable { viewModel.sayHelloToNearbyEntity(activeNearby) }
                                                    .testTag("hud_say_hello_button")
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.WavingHand,
                                                        contentDescription = "Say Hello",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                    Spacer(modifier = Modifier.size(5.dp))
                                                    Text(
                                                        text = "हेलो (Hello)",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                }
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(if (uiState.isVoiceListening) Color(0xFFE53935) else Color(0xFF1E88E5))
                                                    .clickable {
                                                        if (uiState.isVoiceListening) {
                                                            viewModel.stopHindiVoiceMic()
                                                        } else {
                                                            val hasMic = ContextCompat.checkSelfPermission(
                                                                context,
                                                                Manifest.permission.RECORD_AUDIO
                                                            ) == PackageManager.PERMISSION_GRANTED
                                                            if (hasMic) {
                                                                viewModel.startHindiVoiceMic(targetOverride = activeNearby)
                                                            } else {
                                                                hudMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                            }
                                                        }
                                                    }
                                                    .testTag("hud_live_mic_button"),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = if (uiState.isVoiceListening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                                                    contentDescription = "Say Hello via Microphone",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    } else {
                                        // Active conversation: Mic + Work Commands!
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(30.dp)
                                                    .clip(CircleShape)
                                                    .background(if (uiState.isVoiceListening) Color(0xFFE53935) else Color(0xFF22A06B))
                                                    .clickable {
                                                        if (uiState.isVoiceListening) {
                                                            viewModel.stopHindiVoiceMic()
                                                        } else {
                                                            val hasMic = ContextCompat.checkSelfPermission(
                                                                context,
                                                                Manifest.permission.RECORD_AUDIO
                                                            ) == PackageManager.PERMISSION_GRANTED
                                                            if (hasMic) {
                                                                viewModel.startHindiVoiceMic(targetOverride = activeNearby)
                                                            } else {
                                                                hudMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                            }
                                                        }
                                                    }
                                                    .testTag("hud_live_mic_button"),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = if (uiState.isVoiceListening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                                                    contentDescription = "Talk Live in Hindi",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            Row(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                QuickVoiceWorkChip("घर", Color(0xFFFFCA28)) {
                                                    viewModel.commandNpcWorldAction(AiGoal.BUILD_HOUSE, targetOverride = activeNearby)
                                                }
                                                QuickVoiceWorkChip("पानी लाओ", Color(0xFF29B6F6)) {
                                                    viewModel.commandNpcWorldAction(AiGoal.BRING_WATER, targetOverride = activeNearby)
                                                }
                                                QuickVoiceWorkChip("पेड़", Color(0xFF66BB6A)) {
                                                    viewModel.commandNpcWorldAction(AiGoal.PLANT_TREES, targetOverride = activeNearby)
                                                }
                                                QuickVoiceWorkChip("मंदिर", Color(0xFFFF8A65)) {
                                                    viewModel.commandNpcWorldAction(AiGoal.BUILD_MANDIR, targetOverride = activeNearby)
                                                }
                                                QuickVoiceWorkChip("साथ चलो", Color(0xFFBA68C8)) {
                                                    viewModel.commandNpcWorldAction(AiGoal.FOLLOW_FRIEND, targetOverride = activeNearby)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 7. Admin Live Announcement, Live Speech Subtitle & Status Toast Banner
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = if (uiState.showTopHud) 60.dp else 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val liveBanner = uiState.adminSnapshot.liveMap.announcementBanner
                            if (uiState.showTopHud && uiState.adminSnapshot.liveMap.published && liveBanner.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xCC2C1F08),
                                    border = BorderStroke(1.dp, Color(0xFFFFD54F))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Campaign,
                                            contentDescription = "Live Announcement",
                                            tint = Color(0xFFFFD54F),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "LIVE: $liveBanner",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFFF3C4),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            AnimatedVisibility(
                                visible = uiState.showTopHud && uiState.showVoiceHud && uiState.liveSubtitleText != null,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xDD0B1E17),
                                    border = BorderStroke(1.dp, Color(0xAA53FF9E)),
                                    modifier = Modifier.widthIn(max = 360.dp)
                                ) {
                                    Text(
                                        text = "🗣️ ${uiState.liveSubtitleText.orEmpty()}",
                                        fontSize = 11.sp,
                                        color = Color.White,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            AnimatedVisibility(
                                visible = uiState.statusToast != null,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xDD14181C),
                                    border = BorderStroke(1.dp, Color(0x77FFFFFF))
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
                        }

                        // 8. Left Side (Bottom-Left): Bulletproof Local-Coord Virtual Joystick (or D-Pad)
                        if (uiState.showTouchControls) {
                            if (uiState.controlStyle == ControlStyle.JOYSTICK) {
                                BedrockVirtualJoystickVisual(
                                    state = joystickState,
                                    controller = controller,
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(start = 16.dp, bottom = 70.dp)
                                )
                            } else {
                                McpeClassicDpad(
                                    controller = controller,
                                    sneaking = uiState.sneaking,
                                    onToggleSneak = viewModel::toggleSneak,
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(start = 16.dp, bottom = 74.dp)
                                )
                            }

                            // 9. Right Side (Bottom-Right): Bedrock Action Cluster
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
                                    .padding(end = 18.dp, bottom = 72.dp)
                            )
                        }

                        // 9b. Contextual Water-Sit Floating Pill OR Active 3D Bike Riding Dashboard (above Hotbar)
                        if (uiState.showTopHud && uiState.activeBike != null) {
                            val activeBike = uiState.activeBike!!
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xDC0D171F),
                                border = BorderStroke(
                                    width = 1.4.dp,
                                    color = if (uiState.bikeNitroActive) Color(0xFF00E5FF) else activeBike.accentColor
                                ),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 84.dp)
                                    .testTag("bike_riding_dashboard")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = "${uiState.bikeSpeedKmh} km/h",
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (uiState.bikeNitroActive) Color(0xFF00E5FF) else Color(0xFF7CFFB2)
                                        )
                                        Text(
                                            text = activeBike.displayName,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFFB5CDD9),
                                            maxLines = 1
                                        )
                                    }

                                    BikeHudActionChip(
                                        icon = Icons.Rounded.Campaign,
                                        label = "हॉर्न",
                                        tint = Color(0xFFFFD54F),
                                        tag = "bike_horn_button",
                                        onClick = { viewModel.honkBikeHorn() }
                                    )
                                    BikeHudActionChip(
                                        icon = Icons.Rounded.Bolt,
                                        label = if (uiState.bikeNitroActive) "TURBO!" else "नाइट्रो",
                                        tint = Color(0xFF00E5FF),
                                        active = uiState.bikeNitroActive,
                                        tag = "bike_nitro_button",
                                        onClick = { viewModel.triggerBikeNitro() }
                                    )
                                    BikeHudActionChip(
                                        icon = Icons.Rounded.TwoWheeler,
                                        label = "बदलें",
                                        tint = Color(0xFF7CFFB2),
                                        tag = "bike_switch_button",
                                        onClick = { showBikeSheet = true }
                                    )
                                    BikeHudActionChip(
                                        icon = Icons.Rounded.DirectionsWalk,
                                        label = "उतरें",
                                        tint = Color(0xFFFF8A80),
                                        tag = "bike_dismount_button",
                                        onClick = { viewModel.dismountBike() }
                                    )
                                }
                            }
                        } else if (uiState.showTopHud && (uiState.waterAvailableForSit || uiState.sittingOnWater)) {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = if (uiState.sittingOnWater) Color(0xDD0B3954) else Color(0xCC0D2330),
                                border = BorderStroke(
                                    width = 1.4.dp,
                                    color = if (uiState.sittingOnWater) Color(0xFF40E0D0) else Color(0xAA4FC3F7)
                                ),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 86.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable { viewModel.toggleWaterSit() }
                                    .testTag("hud_water_sit_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Waves,
                                        contentDescription = if (uiState.sittingOnWater) "Stand up from water" else "Sit on water",
                                        tint = Color(0xFF4FC3F7),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = if (uiState.sittingOnWater) "पानी से उठें" else "पानी पर बैठें",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
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

                    // Mandatory Landscape Gate Overlay: blocks gameplay until phone is rotated to Landscape!
                    if (!isLandscape) {
                        LandscapeRotationGateOverlay(
                            onRequestLandscape = requestLandscapeNow,
                            onExitToHub = onExitToHub,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    if (showAdminSheet) {
        AdminPanelSheet(
            snapshot = uiState.adminSnapshot,
            inGameWorld = true,
            defaultWorldTitle = uiState.worldName,
            onClaimAdmin = viewModel::adminClaimCredentials,
            onLoginAdmin = viewModel::adminLogin,
            onUpdatePassword = viewModel::adminUpdatePassword,
            onLockAdmin = viewModel::adminLockSession,
            onImportObjUri = viewModel::adminImportObjUri,
            onSaveCustomModel = viewModel::adminSaveCustomModel,
            onDeleteCustomModel = viewModel::adminDeleteCustomModel,
            onSpawnCustomModel = { model ->
                showAdminSheet = false
                viewModel.adminSpawnCustomModel(model)
            },
            onPlantTree = {
                showAdminSheet = false
                viewModel.adminPlantTree()
            },
            onPlantForest = {
                showAdminSheet = false
                viewModel.adminPlantForest()
            },
            onCreateWaterLake = { isWaterfall ->
                showAdminSheet = false
                viewModel.adminCreateWaterLake(isWaterfall)
            },
            onBuildStructure = { type, label ->
                showAdminSheet = false
                viewModel.adminBuildStructure(type, label)
            },
            onSetTimeDay = { viewModel.setTimeOfDayManual(0.25f) },
            onSetTimeNight = { viewModel.setTimeOfDayManual(0.78f) },
            onToggleRain = viewModel::toggleRain,
            onPublishLiveMap = viewModel::adminPublishLiveMap,
            onDismiss = { showAdminSheet = false }
        )
    }

    if (showAiFriendSheet) {
        HindiAiFriendSheet(
            uiState = uiState,
            onSelectNpc = viewModel::selectActiveNpc,
            onMakeFriend = viewModel::befriendActiveNpc,
            onCommandAction = { goal -> viewModel.commandNpcWorldAction(goal) },
            onSendHindiMessage = { msg -> viewModel.sendHindiMessageToNpc(msg) },
            onStartVoiceMic = { viewModel.startHindiVoiceMic() },
            onStopVoiceMic = viewModel::stopHindiVoiceMic,
            onStopSpeaking = viewModel::stopHindiVoicePlayback,
            onDismiss = { showAiFriendSheet = false }
        )
    }

    if (showShopSheet) {
        RoyalBazaarShopSheet(
            uiState = uiState,
            onPurchase = viewModel::purchaseShopProduct,
            onEquipCustom3DModel = { model, spawnCompanion ->
                viewModel.purchaseAndEquipCustom3DModel(model, spawnAsCompanion = spawnCompanion)
                if (spawnCompanion) showShopSheet = false
            },
            onResetDefaultAvatar = viewModel::unequipCustom3DAvatar,
            onDismiss = { showShopSheet = false }
        )
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
            onOpenRealEstate = { showRealEstateSheet = true },
            onDismiss = { showFullMapSheet = false }
        )
    }

    if (showRealEstateSheet) {
        RealEstateMarketSheet(
            uiState = uiState,
            onBuyProperty = viewModel::buyRealEstateProperty,
            onVisitProperty = viewModel::teleportToLandmark,
            onCollectRent = viewModel::collectRealEstateRent,
            onConstructHere = viewModel::constructRealEstateHere,
            onDismiss = { showRealEstateSheet = false }
        )
    }

    if (showBikeSheet) {
        BikeGarageSheet(
            activeBike = uiState.activeBike,
            onSelectBike = { bike -> viewModel.mountBike(bike) },
            onDismountBike = { viewModel.dismountBike() },
            onDismiss = { showBikeSheet = false }
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
            onRotateScreen = requestLandscapeNow,
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

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) break
                            val onCenterNow = applyTouch(change.position)
                            if (!onCenterNow) stayedOnCenter = false
                            change.consume()
                        }
                    } finally {
                        activeDirX = 0f
                        activeDirY = 0f
                        controller.moveInputX = 0f
                        controller.moveInputY = 0f
                    }
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

@Composable
private fun QuickVoiceWorkChip(
    label: String,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(7.dp),
        color = accent.copy(alpha = 0.24f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.8f)),
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun BikeHudActionChip(
    icon: ImageVector,
    label: String,
    tint: Color,
    tag: String,
    active: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = if (active) tint.copy(alpha = 0.36f) else Color(0x33FFFFFF),
        border = BorderStroke(1.dp, if (active) tint else tint.copy(alpha = 0.55f)),
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}
