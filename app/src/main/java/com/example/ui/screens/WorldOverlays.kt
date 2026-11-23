package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BlockType
import com.example.engine.gl.EntityCategory
import com.example.engine.gl.EntityKind
import com.example.engine.gl.MinimapBlip
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.viewmodel.GameWorldUiState
import com.example.ui.viewmodel.WorldLandmark
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Live corner World Minimap radar showing terrain biomes, oceans, waterfalls, gardens,
 * player orientation arrow, friendly characters/animals, and AI-tracking zombies.
 */
@Composable
fun WorldMinimapWidget(
    colors: IntArray,
    blips: List<MinimapBlip>,
    playerYawRad: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 84.dp
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xCC091310),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)),
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("world_minimap_widget")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            MinimapCanvas(
                colors = colors,
                blips = blips,
                playerYawRad = playerYawRad,
                modifier = Modifier.fillMaxSize()
            )
            Text(
                text = "N",
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFD166),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 2.dp)
            )
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color(0xAA08110E),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 2.dp)
            ) {
                Text(
                    text = "MAP",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 7.sp,
                    color = Color(0xFFE0F5EC),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
    }
}

@Composable
private fun MinimapCanvas(
    colors: IntArray,
    blips: List<MinimapBlip>,
    playerYawRad: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val gridDim = if (colors.isNotEmpty()) sqrt(colors.size.toDouble()).roundToInt() else 0
        if (gridDim > 0) {
            val cellW = size.width / gridDim
            val cellH = size.height / gridDim
            for (gz in 0 until gridDim) {
                for (gx in 0 until gridDim) {
                    val colInt = colors[gz * gridDim + gx]
                    drawRect(
                        color = Color(colInt),
                        topLeft = Offset(gx * cellW, gz * cellH),
                        size = Size(cellW + 0.6f, cellH + 0.6f)
                    )
                }
            }
        } else {
            drawRect(color = Color(0xFF173026))
        }

        val cx = size.width / 2f
        val cy = size.height / 2f
        val scale = size.minDimension / 44f

        // Entity radar blips
        blips.forEach { blip ->
            val bx = (cx + blip.dx * scale).coerceIn(4f, size.width - 4f)
            val by = (cy + blip.dz * scale).coerceIn(4f, size.height - 4f)
            val dotColor = when {
                blip.kind.isHostile -> Color(0xFFFF3B5C)
                blip.kind.category == EntityCategory.HEROES -> Color(0xFFFFD166)
                blip.kind.isFlying -> Color(0xFF8BE9FD)
                else -> Color(0xFF53FF9E)
            }
            val dotRadius = if (blip.kind.isHostile) 3.4.dp.toPx() else 2.5.dp.toPx()
            drawCircle(color = Color(0xAA000000), radius = dotRadius + 1.2f, center = Offset(bx, by))
            drawCircle(color = dotColor, radius = dotRadius, center = Offset(bx, by))
        }

        // Player directional arrow at center
        val deg = Math.toDegrees(playerYawRad.toDouble()).toFloat()
        rotate(degrees = deg, pivot = Offset(cx, cy)) {
            val arrow = Path().apply {
                moveTo(cx, cy - 6.dp.toPx())
                lineTo(cx + 4.5.dp.toPx(), cy + 5.dp.toPx())
                lineTo(cx, cy + 2.dp.toPx())
                lineTo(cx - 4.5.dp.toPx(), cy + 5.dp.toPx())
                close()
            }
            drawPath(arrow, color = Color.White)
            drawPath(arrow, color = Color(0xFF0A1410), style = Stroke(width = 1.4f))
        }
    }
}

/**
 * First-person 3D-styled Voxel Blaster overlay with smooth recoil kick and muzzle flash.
 */
@Composable
fun FirstPersonBlasterOverlay(
    recoilTick: Int,
    modifier: Modifier = Modifier
) {
    val recoil = remember { Animatable(0f) }

    LaunchedEffect(recoilTick) {
        if (recoilTick > 0) {
            recoil.snapTo(1f)
            recoil.animateTo(0f, animationSpec = tween(durationMillis = 140))
        }
    }

    val kick = recoil.value
    Canvas(modifier = modifier.size(width = 126.dp, height = 112.dp)) {
        val w = size.width
        val h = size.height
        val shiftX = kick * 10.dp.toPx()
        val shiftY = kick * 12.dp.toPx()

        // High-pro fiery muzzle flash starburst + shockwave ring when firing
        if (kick > 0.25f) {
            val flashCenter = Offset(w * 0.22f + shiftX, h * 0.20f + shiftY)
            drawCircle(
                color = Color(0xFFFF6D00).copy(alpha = (kick * 0.65f).coerceIn(0f, 1f)),
                radius = (34.dp.toPx() * kick),
                center = flashCenter
            )
            drawCircle(
                color = Color(0xFFFFEA70).copy(alpha = (kick * 0.90f).coerceIn(0f, 1f)),
                radius = (24.dp.toPx() * kick),
                center = flashCenter
            )
            drawCircle(
                color = Color(0xFFFFFFFF).copy(alpha = (kick * 0.95f).coerceIn(0f, 1f)),
                radius = (11.dp.toPx() * kick),
                center = flashCenter
            )
            drawCircle(
                color = Color(0xFF35E8FF).copy(alpha = (kick * 0.85f).coerceIn(0f, 1f)),
                radius = (28.dp.toPx() * (1.25f - kick * 0.4f)),
                center = flashCenter,
                style = Stroke(width = 2.6.dp.toPx())
            )
        }

        // Voxel Blaster Barrel (slanted perspective toward crosshair)
        rotate(degrees = -24f + kick * 10f, pivot = Offset(w * 0.72f, h * 0.82f)) {
            val bx = w * 0.26f + shiftX
            val by = h * 0.28f + shiftY
            // Barrel outer obsidian chassis
            drawRoundRect(
                color = Color(0xFF1D2926),
                topLeft = Offset(bx, by),
                size = Size(w * 0.62f, h * 0.26f),
                cornerRadius = CornerRadius(6.dp.toPx())
            )
            // Glowing emerald/cyan plasma rail
            drawRoundRect(
                color = Color(0xFF35E8FF),
                topLeft = Offset(bx + 6.dp.toPx(), by + 5.dp.toPx()),
                size = Size(w * 0.48f, h * 0.08f),
                cornerRadius = CornerRadius(3.dp.toPx())
            )
            // Golden barrel tip
            drawRoundRect(
                color = Color(0xFFFFC107),
                topLeft = Offset(bx - 4.dp.toPx(), by + 2.dp.toPx()),
                size = Size(10.dp.toPx(), h * 0.22f),
                cornerRadius = CornerRadius(3.dp.toPx())
            )
            // Grip handle
            drawRoundRect(
                color = Color(0xFF2B3D37),
                topLeft = Offset(bx + w * 0.38f, by + h * 0.22f),
                size = Size(w * 0.22f, h * 0.42f),
                cornerRadius = CornerRadius(5.dp.toPx())
            )
        }
    }
}

/**
 * Authentic Minecraft PE First-Person Hand holding the active 3D Voxel Block (or Water Bucket)
 * with smooth idle sway and crisp chop/place swing arc animation.
 */
@Composable
fun FirstPersonHandAndBlockOverlay(
    selectedBlock: BlockType,
    swingTick: Int,
    inWater: Boolean,
    modifier: Modifier = Modifier
) {
    val swingAnim = remember { Animatable(0f) }
    LaunchedEffect(swingTick) {
        if (swingTick > 0) {
            swingAnim.snapTo(1f)
            swingAnim.animateTo(0f, animationSpec = tween(durationMillis = 190))
        }
    }

    val idleTransition = rememberInfiniteTransition(label = "hand_idle")
    val idlePhase by idleTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (inWater) 1600 else 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "hand_bob"
    )

    val swing = swingAnim.value
    val swingArc = sin(swing * PI).toFloat()

    Canvas(modifier = modifier.size(width = 112.dp, height = 104.dp)) {
        val w = size.width
        val h = size.height
        val bobY = sin(idlePhase) * (if (inWater) 5.dp.toPx() else 2.5.dp.toPx())
        val swingDx = -swingArc * 26.dp.toPx()
        val swingDy = swingArc * 14.dp.toPx()
        val rotDeg = -14f - swingArc * 36f

        rotate(degrees = rotDeg, pivot = Offset(w * 0.80f, h * 0.90f)) {
            // Character forearm & chocolate-brown polo sleeve with grey cuff (matched to user photo)
            val armX = w * 0.50f + swingDx
            val armY = h * 0.46f + bobY + swingDy
            drawRoundRect(
                color = Color(0xFF613D35),
                topLeft = Offset(armX + 6.dp.toPx(), armY + 14.dp.toPx()),
                size = Size(w * 0.34f, h * 0.46f),
                cornerRadius = CornerRadius(6.dp.toPx())
            )
            // Cool grey polo sleeve cuff
            drawRoundRect(
                color = Color(0xFF9EA3AD),
                topLeft = Offset(armX + 4.dp.toPx(), armY + 12.dp.toPx()),
                size = Size(w * 0.33f, 6.dp.toPx()),
                cornerRadius = CornerRadius(3.dp.toPx())
            )
            // Warm medium-brown skin forearm
            drawRoundRect(
                color = Color(0xFFB88463),
                topLeft = Offset(armX, armY),
                size = Size(w * 0.30f, h * 0.28f),
                cornerRadius = CornerRadius(5.dp.toPx())
            )

            // Isometric 3D held voxel block right in the hand
            val cx = w * 0.38f + swingDx
            val cy = h * 0.36f + bobY + swingDy
            val r = 22.dp.toPx()
            val topC = selectedBlock.topColor
            val leftC = selectedBlock.leftColor
            val rightC = selectedBlock.rightColor

            // Top diamond face
            val topPath = Path().apply {
                moveTo(cx, cy - r * 0.92f)
                lineTo(cx + r, cy - r * 0.42f)
                lineTo(cx, cy + r * 0.08f)
                lineTo(cx - r, cy - r * 0.42f)
                close()
            }
            // Left face
            val leftPath = Path().apply {
                moveTo(cx - r, cy - r * 0.42f)
                lineTo(cx, cy + r * 0.08f)
                lineTo(cx, cy + r * 0.96f)
                lineTo(cx - r, cy + r * 0.46f)
                close()
            }
            // Right face
            val rightPath = Path().apply {
                moveTo(cx + r, cy - r * 0.42f)
                lineTo(cx, cy + r * 0.08f)
                lineTo(cx, cy + r * 0.96f)
                lineTo(cx + r, cy + r * 0.46f)
                close()
            }
            drawPath(topPath, color = topC)
            drawPath(leftPath, color = leftC)
            drawPath(rightPath, color = rightC)

            // Subtle crisp pixel outline
            val edgeStroke = Stroke(width = 1.3.dp.toPx())
            val outlineColor = Color(0x8808120F)
            drawPath(topPath, color = outlineColor, style = edgeStroke)
            drawPath(leftPath, color = outlineColor, style = edgeStroke)
            drawPath(rightPath, color = outlineColor, style = edgeStroke)

            // Extra shimmering water ripple highlight if holding Water / Waterfall
            if (selectedBlock == BlockType.WATER || selectedBlock == BlockType.WATERFALL) {
                drawCircle(
                    color = Color(0xAA9EF0FF),
                    radius = 5.dp.toPx(),
                    center = Offset(cx - 4.dp.toPx(), cy - r * 0.44f)
                )
            }
        }
    }
}

/**
 * Full-screen animated underwater caustics & rising air bubbles overlay when the player dives beneath water.
 */
@Composable
fun UnderwaterAnimationOverlay(
    isUnderwater: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isUnderwater) return

    val infiniteTransition = rememberInfiniteTransition(label = "underwater_fx")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "underwater_phase"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Deep blue-teal shimmering underwater tint
        val shimmer = 0.05f * sin(phase * 2.0 * PI).toFloat()
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF0A4B78).copy(alpha = 0.34f + shimmer),
                    Color(0xFF062C4D).copy(alpha = 0.46f - shimmer),
                    Color(0xFF031930).copy(alpha = 0.56f)
                )
            )
        )

        // Subtle undulating caustic light bands near the top
        for (i in 0 until 4) {
            val bandY = h * (0.08f + i * 0.14f) + sin((phase * 2.0 * PI) + i * 1.4).toFloat() * 14.dp.toPx()
            drawRect(
                color = Color(0xFF66E6FF).copy(alpha = 0.065f),
                topLeft = Offset(0f, bandY),
                size = Size(w, 18.dp.toPx())
            )
        }

        // Rising animated air bubbles with glossy specular shine
        val bubbleSeeds = listOf(0.08f, 0.18f, 0.29f, 0.44f, 0.58f, 0.71f, 0.83f, 0.92f)
        bubbleSeeds.forEachIndexed { idx, xFrac ->
            val progress = (phase + idx * 0.17f) % 1f
            val by = h * (1.05f - progress * 1.15f)
            val wobble = sin((progress * 6.0 * PI) + idx).toFloat() * 10.dp.toPx()
            val bx = w * xFrac + wobble
            val radius = (4.5f + (idx % 4) * 2.2f).dp.toPx()

            drawCircle(
                color = Color(0x389CF4FF),
                radius = radius,
                center = Offset(bx, by)
            )
            drawCircle(
                color = Color(0xAAD9FAFF),
                radius = radius,
                center = Offset(bx, by),
                style = Stroke(width = 1.2.dp.toPx())
            )
            drawCircle(
                color = Color(0xEEFFFFFF),
                radius = radius * 0.28f,
                center = Offset(bx - radius * 0.32f, by - radius * 0.32f)
            )
        }
    }
}

/**
 * Classic Minecraft PE Status Bars above the Hotbar:
 * Hearts (Health), Hunger Drumsticks, Underwater Oxygen Bubbles, and Emerald XP Level Bar.
 */
@Composable
fun McpeSurvivalStatusBars(
    health: Int,
    hunger: Int,
    oxygen: Int,
    showOxygen: Boolean,
    xpLevel: Int,
    xpProgress: Float,
    isCreative: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        if (!isCreative || showOxygen) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                // Left: 10 Health Hearts (Survival)
                if (!isCreative) {
                    Canvas(modifier = Modifier.size(width = 108.dp, height = 11.dp)) {
                        val step = size.width / 10f
                        for (i in 0 until 10) {
                            val filled = i < health
                            val cx = i * step + step * 0.5f
                            val cy = size.height * 0.54f
                            val r = step * 0.38f
                            val heartColor = if (filled) Color(0xFFFF3B5C) else Color(0x66222826)
                            val heartPath = Path().apply {
                                moveTo(cx, cy + r)
                                lineTo(cx - r, cy - r * 0.1f)
                                lineTo(cx - r * 0.55f, cy - r * 0.85f)
                                lineTo(cx, cy - r * 0.25f)
                                lineTo(cx + r * 0.55f, cy - r * 0.85f)
                                lineTo(cx + r, cy - r * 0.1f)
                                close()
                            }
                            drawPath(heartPath, color = Color(0xCC070C0A), style = Stroke(width = 2.2f))
                            drawPath(heartPath, color = heartColor)
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.width(108.dp))
                }

                // Right: Oxygen Bubbles (when underwater) + Hunger Drumsticks (Survival)
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (showOxygen) {
                        Canvas(modifier = Modifier.size(width = 108.dp, height = 11.dp)) {
                            val step = size.width / 10f
                            for (i in 0 until 10) {
                                val filled = i < oxygen
                                val cx = size.width - (i * step + step * 0.5f)
                                val cy = size.height * 0.5f
                                val r = step * 0.36f
                                drawCircle(
                                    color = if (filled) Color(0xFF42DCFF) else Color(0x4419323D),
                                    radius = r,
                                    center = Offset(cx, cy)
                                )
                                drawCircle(
                                    color = if (filled) Color(0xFFD6F9FF) else Color(0x66426675),
                                    radius = r,
                                    center = Offset(cx, cy),
                                    style = Stroke(width = 1.4f)
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
                        Canvas(modifier = Modifier.size(width = 108.dp, height = 10.dp)) {
                            val step = size.width / 10f
                            for (i in 0 until 10) {
                                val filled = i < hunger
                                val cx = size.width - (i * step + step * 0.5f)
                                val cy = size.height * 0.5f
                                val r = step * 0.35f
                                drawCircle(
                                    color = Color(0xCC070C0A),
                                    radius = r + 1.1f,
                                    center = Offset(cx, cy)
                                )
                                drawCircle(
                                    color = if (filled) Color(0xFFE08B3E) else Color(0x66222826),
                                    radius = r,
                                    center = Offset(cx, cy)
                                )
                                if (filled) {
                                    drawCircle(
                                        color = Color(0xFFFFD6A5),
                                        radius = r * 0.35f,
                                        center = Offset(cx - r * 0.25f, cy - r * 0.25f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Classic green MCPE Experience Progress Bar + Centered Level Number
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
            ) {
                drawRoundRect(
                    color = Color(0xBB07100C),
                    cornerRadius = CornerRadius(3.dp.toPx())
                )
                drawRoundRect(
                    color = Color(0xFF4BFF7A),
                    size = Size(size.width * xpProgress.coerceIn(0f, 1f), size.height),
                    cornerRadius = CornerRadius(3.dp.toPx())
                )
                drawRoundRect(
                    color = Color(0x66FFFFFF),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                    style = Stroke(width = 1f)
                )
            }
            Text(
                text = "$xpLevel",
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF8CFF66)
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullWorldMapSheet(
    uiState: GameWorldUiState,
    onTeleport: (WorldLandmark) -> Unit,
    onOpenRealEstate: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val realEstateLandmarks = remember { WorldLandmark.entries.filter { it.isRealEstate } }
    val natureLandmarks = remember { WorldLandmark.entries.filter { !it.isRealEstate } }

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
                .padding(bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Map, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text("Mega World & Real Estate Map", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            text = "Pos X:${uiState.playerX} Y:${uiState.playerY} Z:${uiState.playerZ} • 93×93 Live Radar • ${uiState.totalEntities} Entities",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Button(
                    onClick = {
                        onDismiss()
                        onOpenRealEstate()
                    },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("map_open_real_estate_button")
                ) {
                    Text("Real Estate")
                }
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                MinimapCanvas(
                    colors = uiState.minimapColors,
                    blips = uiState.minimapBlips,
                    playerYawRad = uiState.playerYaw,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Legend row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LegendDot(Color(0xFF1D88D6), "Water/Pool")
                LegendDot(Color(0xFFD8B872), "Real Estate")
                LegendDot(Color(0xFFFFD166), "Heroes")
                LegendDot(Color(0xFF53FF9E), "Animals")
                LegendDot(Color(0xFFFF3B5C), "Zombies")
            }

            Text(
                text = "Real Estate City District (रियल एस्टेट टाउनशिप)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFCA28)
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(realEstateLandmarks) { landmark ->
                    Button(
                        onClick = {
                            onTeleport(landmark)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF28200D),
                            contentColor = Color(0xFFFFE57F)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFFFCA28)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("landmark_${landmark.name.lowercase()}")
                    ) {
                        Icon(Icons.Rounded.Explore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(landmark.label)
                    }
                }
            }

            Text("World Nature & Heritage Landmarks", style = MaterialTheme.typography.titleSmall)

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(natureLandmarks) { landmark ->
                    Button(
                        onClick = {
                            onTeleport(landmark)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("landmark_${landmark.name.lowercase()}")
                    ) {
                        Icon(Icons.Rounded.Explore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(landmark.label)
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Surface(shape = CircleShape, color = color, modifier = Modifier.size(9.dp)) {}
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterSpawnerSheet(
    uiState: GameWorldUiState,
    onSpawnEntity: (EntityKind) -> Unit,
    onSpawnZombieWave: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedCategory by remember { mutableStateOf(EntityCategory.HEROES) }

    val itemsInCategory = remember(selectedCategory) {
        EntityKind.entries.filter { it.category == selectedCategory }
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
                .padding(bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Rounded.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f)) {
                    Text("Characters, Animals & Zombies", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        text = "Tap any character to spawn right in front of you with AI & real sound.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(EntityCategory.entries) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat.label) },
                        modifier = Modifier.testTag("spawner_cat_${cat.name.lowercase()}")
                    )
                }
            }

            if (selectedCategory == EntityCategory.ZOMBIES) {
                Button(
                    onClick = {
                        onSpawnZombieWave()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD93848),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("spawn_zombie_wave_button")
                ) {
                    Icon(Icons.Rounded.Warning, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Spawn Zombie Wave (×4 AI Trackers)")
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = 250.dp)
            ) {
                items(itemsInCategory, key = { it.name }) { kind ->
                    val badgeColor = Color(kind.badgeColorHex)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.2.dp, badgeColor.copy(alpha = 0.65f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                onSpawnEntity(kind)
                                onDismiss()
                            }
                            .testTag("spawn_${kind.name.lowercase()}")
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = badgeColor.copy(alpha = 0.22f),
                                border = BorderStroke(1.5.dp, badgeColor),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = when (kind.category) {
                                            EntityCategory.HEROES -> Icons.Rounded.Groups
                                            EntityCategory.SOCIETY -> Icons.Rounded.Groups
                                            EntityCategory.ANIMALS -> Icons.Rounded.Pets
                                            EntityCategory.ZOMBIES -> Icons.Rounded.Warning
                                        },
                                        contentDescription = kind.displayName,
                                        tint = badgeColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Text(
                                text = kind.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = when {
                                    kind.isHostile -> "AI Tracker"
                                    kind.isFlying -> "Flying Bird"
                                    kind.category == EntityCategory.SOCIETY -> "Autonomous AI"
                                    kind.isCompanion -> "AI Companion"
                                    else -> "Roaming AI"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = badgeColor
                            )
                        }
                    }
                }
            }
        }
    }
}
