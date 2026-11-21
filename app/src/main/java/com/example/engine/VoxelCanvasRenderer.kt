package com.example.engine

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.BlockType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class VoxelParticle(
    val id: Long,
    val worldX: Float,
    val worldY: Float,
    val worldZ: Float,
    val vx: Float,
    val vy: Float,
    val color: Color,
    val createdAtMs: Long
)

/**
 * Computes sky zenith/horizon colors and ambient daylight factor (0.28 at midnight .. 1.0 at noon)
 * for normalized timeOfDay in [0f, 1f]:
 * 0.00 = Sunrise, 0.25 = Noon, 0.50 = Golden Sunset, 0.75 = Starry Midnight.
 */
data class SkyAtmosphere(
    val topColor: Color,
    val midColor: Color,
    val bottomColor: Color,
    val ambientLight: Float,
    val starAlpha: Float,
    val sunAngleRad: Float
)

fun computeSkyAtmosphere(timeOfDay: Float): SkyAtmosphere {
    val t = ((timeOfDay % 1f) + 1f) % 1f
    val sunAngle = t * 2f * PI.toFloat()
    val daylightWave = sin(sunAngle) // +1 at 0.25 noon, -1 at 0.75 midnight

    val dawnTop = Color(0xFF1F2B56)
    val dawnMid = Color(0xFF8C4E78)
    val dawnBot = Color(0xFFFF9E66)

    val dayTop = Color(0xFF1E7BD6)
    val dayMid = Color(0xFF5AB4F7)
    val dayBot = Color(0xFFB6E8FF)

    val duskTop = Color(0xFF1C1F4A)
    val duskMid = Color(0xFFA8425E)
    val duskBot = Color(0xFFFF8A47)

    val nightTop = Color(0xFF050914)
    val nightMid = Color(0xFF0C1830)
    val nightBot = Color(0xFF182C4A)

    val (top, mid, bot) = when {
        t < 0.25f -> {
            val f = t / 0.25f
            Triple(lerp(dawnTop, dayTop, f), lerp(dawnMid, dayMid, f), lerp(dawnBot, dayBot, f))
        }

        t < 0.50f -> {
            val f = (t - 0.25f) / 0.25f
            Triple(lerp(dayTop, duskTop, f), lerp(dayMid, duskMid, f), lerp(dayBot, duskBot, f))
        }

        t < 0.75f -> {
            val f = (t - 0.50f) / 0.25f
            Triple(lerp(duskTop, nightTop, f), lerp(duskMid, nightMid, f), lerp(duskBot, nightBot, f))
        }

        else -> {
            val f = (t - 0.75f) / 0.25f
            Triple(lerp(nightTop, dawnTop, f), lerp(nightMid, dawnMid, f), lerp(nightBot, dawnBot, f))
        }
    }

    val ambient = (0.30f + ((daylightWave + 1f) * 0.5f) * 0.70f).coerceIn(0.28f, 1.0f)
    val starAlpha = ((-daylightWave - 0.1f) / 0.9f).coerceIn(0f, 1f)

    return SkyAtmosphere(
        topColor = top,
        midColor = mid,
        bottomColor = bot,
        ambientLight = ambient,
        starAlpha = starAlpha,
        sunAngleRad = sunAngle
    )
}

fun DrawScope.drawVoxelSkyAndEnvironment(
    atmosphere: SkyAtmosphere,
    animPhase: Float
) {
    drawRect(
        brush = Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to atmosphere.topColor,
                0.55f to atmosphere.midColor,
                1.0f to atmosphere.bottomColor
            )
        )
    )

    // Twinkling voxel stars at night
    if (atmosphere.starAlpha > 0.05f) {
        for (i in 0 until 36) {
            val sx = ((i * 73 + 19) % 100) / 100f * size.width
            val sy = ((i * 41 + 11) % 52) / 100f * size.height
            val twinkle = 0.55f + 0.45f * sin(animPhase * 2.5f + i * 1.7f)
            val starSize = if (i % 4 == 0) 4.5f else 2.8f
            drawRect(
                color = Color(0xFFE8F6FF).copy(alpha = atmosphere.starAlpha * twinkle),
                topLeft = Offset(sx, sy),
                size = Size(starSize, starSize)
            )
        }
    }

    // Cubic Sun & Moon arc across the upper sky
    val arcCenterX = size.width * 0.5f
    val arcCenterY = size.height * 0.58f
    val rx = size.width * 0.44f
    val ry = size.height * 0.46f

    val sunX = arcCenterX - cos(atmosphere.sunAngleRad) * rx
    val sunY = arcCenterY - sin(atmosphere.sunAngleRad) * ry
    if (sunY < size.height * 0.65f) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x99FFE082), Color.Transparent),
                center = Offset(sunX, sunY),
                radius = 78f
            ),
            radius = 78f,
            center = Offset(sunX, sunY)
        )
        drawRect(
            color = Color(0xFFFFF3B0),
            topLeft = Offset(sunX - 18f, sunY - 18f),
            size = Size(36f, 36f)
        )
        drawRect(
            color = Color(0xFFFFD54F),
            topLeft = Offset(sunX - 12f, sunY - 12f),
            size = Size(24f, 24f)
        )
    }

    val moonAngle = atmosphere.sunAngleRad + PI.toFloat()
    val moonX = arcCenterX - cos(moonAngle) * rx
    val moonY = arcCenterY - sin(moonAngle) * ry
    if (moonY < size.height * 0.65f) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x66B3E5FC), Color.Transparent),
                center = Offset(moonX, moonY),
                radius = 64f
            ),
            radius = 64f,
            center = Offset(moonX, moonY)
        )
        drawRect(
            color = Color(0xFFE3F2FD),
            topLeft = Offset(moonX - 15f, moonY - 15f),
            size = Size(30f, 30f)
        )
        drawRect(
            color = Color(0xFFB0BEC5),
            topLeft = Offset(moonX - 8f, moonY - 5f),
            size = Size(8f, 8f)
        )
    }

    // Drifting blocky voxel clouds
    val cloudColor = lerp(Color(0xFF283A54), Color.White, atmosphere.ambientLight).copy(alpha = 0.34f)
    for (c in 0 until 4) {
        val speed = 10f + c * 4f
        val cx = ((c * 240f + animPhase * speed) % (size.width + 220f)) - 110f
        val cy = size.height * (0.11f + c * 0.055f)
        val cw = 76f + (c % 2) * 34f
        drawRect(
            color = cloudColor,
            topLeft = Offset(cx, cy),
            size = Size(cw, 16f)
        )
        drawRect(
            color = cloudColor.copy(alpha = 0.22f),
            topLeft = Offset(cx + 14f, cy - 10f),
            size = Size(cw * 0.6f, 10f)
        )
    }
}

private fun applyLighting(base: Color, ambient: Float, torchLight: Float, y: Int): Color {
    val depthShade = (0.76f + (y / WORLD_HEIGHT.toFloat()) * 0.24f).coerceIn(0.75f, 1.0f)
    val combined = (ambient * depthShade + torchLight * (1f - ambient * 0.65f) * 0.85f).coerceIn(0.24f, 1.15f)
    val warmR = if (torchLight > 0.05f) torchLight * (1f - ambient) * 0.24f else 0f
    val warmG = if (torchLight > 0.05f) torchLight * (1f - ambient) * 0.14f else 0f
    return Color(
        red = (base.red * combined + warmR).coerceIn(0f, 1f),
        green = (base.green * combined + warmG).coerceIn(0f, 1f),
        blue = (base.blue * combined).coerceIn(0f, 1f),
        alpha = base.alpha
    )
}

fun DrawScope.drawIsometricVoxel(
    voxel: VisibleVoxel,
    tileHalfW: Float,
    tileHalfH: Float,
    blockHeight: Float,
    ambientLight: Float,
    animPhase: Float,
    isHighlighted: Boolean
) {
    val block = voxel.block
    val cx = voxel.screenX
    val cy = voxel.screenY

    if (block == BlockType.TORCH) {
        drawIsometricTorch(cx, cy, tileHalfW, tileHalfH, blockHeight, animPhase)
        if (isHighlighted) drawHighlightWireframe(cx, cy, tileHalfW, tileHalfH, blockHeight)
        return
    }

    val topCol = applyLighting(block.topColor, ambientLight, voxel.torchLight, voxel.pos.y)
    val leftCol = applyLighting(block.leftColor, ambientLight, voxel.torchLight, voxel.pos.y)
    val rightCol = applyLighting(block.rightColor, ambientLight, voxel.torchLight, voxel.pos.y)
    val accentCol = applyLighting(block.accentColor, ambientLight, voxel.torchLight, voxel.pos.y)

    // Water gentle bobbing offset
    val fluidDip = if (block == BlockType.WATER || block == BlockType.LAVA) {
        blockHeight * 0.14f + sin(animPhase * 2.4f + voxel.pos.x + voxel.pos.z) * (blockHeight * 0.03f)
    } else {
        0f
    }
    val topY = cy + fluidDip
    val bh = blockHeight - fluidDip

    // Top diamond path
    val topPath = Path().apply {
        moveTo(cx, topY - tileHalfH)
        lineTo(cx + tileHalfW, topY)
        lineTo(cx, topY + tileHalfH)
        lineTo(cx - tileHalfW, topY)
        close()
    }

    // Left parallelogram face
    val leftPath = Path().apply {
        moveTo(cx - tileHalfW, topY)
        lineTo(cx, topY + tileHalfH)
        lineTo(cx, topY + tileHalfH + bh)
        lineTo(cx - tileHalfW, topY + bh)
        close()
    }

    // Right parallelogram face
    val rightPath = Path().apply {
        moveTo(cx + tileHalfW, topY)
        lineTo(cx, topY + tileHalfH)
        lineTo(cx, topY + tileHalfH + bh)
        lineTo(cx + tileHalfW, topY + bh)
        close()
    }

    drawPath(topPath, topCol)
    drawPath(leftPath, leftCol)
    drawPath(rightPath, rightCol)

    // Procedural block texture details for rich Minecraft-style voxel craft
    when (block) {
        BlockType.GRASS -> {
            // Green turf overhang strip on left and right faces
            val turfH = bh * 0.28f
            val turfLeft = Path().apply {
                moveTo(cx - tileHalfW, topY)
                lineTo(cx, topY + tileHalfH)
                lineTo(cx, topY + tileHalfH + turfH)
                lineTo(cx - tileHalfW, topY + turfH)
                close()
            }
            val turfRight = Path().apply {
                moveTo(cx + tileHalfW, topY)
                lineTo(cx, topY + tileHalfH)
                lineTo(cx, topY + tileHalfH + turfH)
                lineTo(cx + tileHalfW, topY + turfH)
                close()
            }
            drawPath(turfLeft, accentCol)
            drawPath(turfRight, accentCol.copy(alpha = 0.85f))
        }

        BlockType.COAL_ORE, BlockType.IRON_ORE, BlockType.GOLD_ORE, BlockType.DIAMOND_ORE -> {
            val fleckSize = (tileHalfW * 0.16f).coerceAtLeast(2.2f)
            val oreCol = if (block == BlockType.DIAMOND_ORE || block == BlockType.GOLD_ORE) {
                block.accentColor
            } else {
                accentCol
            }
            drawRect(oreCol, Offset(cx - tileHalfW * 0.62f, topY + bh * 0.34f), Size(fleckSize, fleckSize))
            drawRect(oreCol, Offset(cx - tileHalfW * 0.28f, topY + bh * 0.62f), Size(fleckSize * 1.2f, fleckSize))
            drawRect(oreCol, Offset(cx + tileHalfW * 0.25f, topY + bh * 0.42f), Size(fleckSize * 1.1f, fleckSize))
            drawRect(oreCol, Offset(cx + tileHalfW * 0.52f, topY + bh * 0.68f), Size(fleckSize, fleckSize))
            if (block == BlockType.DIAMOND_ORE) {
                drawRect(
                    Color.White.copy(alpha = 0.7f),
                    Offset(cx - tileHalfW * 0.26f, topY + bh * 0.62f),
                    Size(fleckSize * 0.45f, fleckSize * 0.45f)
                )
            }
        }

        BlockType.PLANKS, BlockType.BRICK -> {
            // Horizontal mortar / plank seam lines
            val lineCol = accentCol.copy(alpha = 0.42f)
            for (row in 1..2) {
                val frac = row / 3f
                drawLine(
                    color = lineCol,
                    start = Offset(cx - tileHalfW, topY + bh * frac),
                    end = Offset(cx, topY + tileHalfH + bh * frac),
                    strokeWidth = 1.2f
                )
                drawLine(
                    color = lineCol,
                    start = Offset(cx, topY + tileHalfH + bh * frac),
                    end = Offset(cx + tileHalfW, topY + bh * frac),
                    strokeWidth = 1.2f
                )
            }
        }

        BlockType.WOOD_LOG -> {
            // Tree ring detail on top face
            val ringPath = Path().apply {
                val s = 0.55f
                moveTo(cx, topY - tileHalfH * s)
                lineTo(cx + tileHalfW * s, topY)
                lineTo(cx, topY + tileHalfH * s)
                lineTo(cx - tileHalfW * s, topY)
                close()
            }
            drawPath(ringPath, accentCol.copy(alpha = 0.55f), style = Stroke(width = 1.4f))
        }

        BlockType.GLASS, BlockType.ICE -> {
            // Diagonal crystalline specular glint
            drawLine(
                color = Color.White.copy(alpha = 0.65f),
                start = Offset(cx - tileHalfW * 0.45f, topY - tileHalfH * 0.1f),
                end = Offset(cx + tileHalfW * 0.1f, topY - tileHalfH * 0.48f),
                strokeWidth = 1.8f
            )
            drawPath(topPath, Color.White.copy(alpha = 0.45f), style = Stroke(width = 1f))
        }

        BlockType.WATER -> {
            val shimmer = (0.35f + 0.35f * sin(animPhase * 3f + voxel.pos.x * 1.3f)).coerceIn(0f, 0.8f)
            drawLine(
                color = Color.White.copy(alpha = shimmer),
                start = Offset(cx - tileHalfW * 0.35f, topY),
                end = Offset(cx + tileHalfW * 0.25f, topY - tileHalfH * 0.25f),
                strokeWidth = 1.6f
            )
        }

        BlockType.LAVA -> {
            val glow = 0.55f + 0.35f * sin(animPhase * 3.5f + voxel.pos.z)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x66FF9100).copy(alpha = glow * 0.5f), Color.Transparent),
                    center = Offset(cx, topY),
                    radius = tileHalfW * 1.4f
                ),
                radius = tileHalfW * 1.4f,
                center = Offset(cx, topY)
            )
        }

        BlockType.OBSIDIAN -> {
            val glintSize = (tileHalfW * 0.14f).coerceAtLeast(2f)
            drawRect(
                accentCol.copy(alpha = 0.65f),
                Offset(cx - tileHalfW * 0.45f, topY + bh * 0.45f),
                Size(glintSize, glintSize)
            )
        }

        else -> Unit
    }

    // Crisp subtle voxel bevel outlines
    val edgeColor = Color.Black.copy(alpha = if (block.translucent) 0.12f else 0.18f)
    drawPath(topPath, edgeColor, style = Stroke(width = 0.9f))
    drawPath(leftPath, edgeColor, style = Stroke(width = 0.9f))
    drawPath(rightPath, edgeColor, style = Stroke(width = 0.9f))

    if (isHighlighted) {
        drawHighlightWireframe(cx, topY, tileHalfW, tileHalfH, bh)
    }
}

private fun DrawScope.drawIsometricTorch(
    cx: Float,
    cy: Float,
    tileHalfW: Float,
    tileHalfH: Float,
    blockHeight: Float,
    animPhase: Float
) {
    val flicker = sin(animPhase * 9f + cx * 0.1f) * 2.2f
    val postW = (tileHalfW * 0.24f).coerceAtLeast(3.5f)
    val postH = blockHeight * 0.72f
    val baseY = cy + tileHalfH * 0.45f + blockHeight * 0.55f

    // Warm radiant glow halo
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0x99FFCA28),
                Color(0x44FF7043),
                Color.Transparent
            ),
            center = Offset(cx, baseY - postH),
            radius = tileHalfW * 2.1f + flicker
        ),
        radius = tileHalfW * 2.1f + flicker,
        center = Offset(cx, baseY - postH)
    )

    // Wooden torch stick
    drawRect(
        color = Color(0xFF7D5229),
        topLeft = Offset(cx - postW * 0.5f, baseY - postH),
        size = Size(postW, postH)
    )
    // Glowing flame tip
    drawRect(
        color = Color(0xFFFF8F00),
        topLeft = Offset(cx - postW * 0.65f, baseY - postH - postW * 0.85f),
        size = Size(postW * 1.3f, postW * 1.1f)
    )
    drawRect(
        color = Color(0xFFFFF176),
        topLeft = Offset(cx - postW * 0.35f, baseY - postH - postW * 0.65f - flicker * 0.3f),
        size = Size(postW * 0.7f, postW * 0.7f)
    )
}

private fun DrawScope.drawHighlightWireframe(
    cx: Float,
    topY: Float,
    tileHalfW: Float,
    tileHalfH: Float,
    bh: Float
) {
    val highlightCol = Color(0xFF35E8FF)
    val outline = Path().apply {
        moveTo(cx, topY - tileHalfH)
        lineTo(cx + tileHalfW, topY)
        lineTo(cx + tileHalfW, topY + bh)
        lineTo(cx, topY + tileHalfH + bh)
        lineTo(cx - tileHalfW, topY + bh)
        lineTo(cx - tileHalfW, topY)
        close()
    }
    drawPath(outline, highlightCol, style = Stroke(width = 2.6f))
    drawLine(highlightCol.copy(alpha = 0.8f), Offset(cx - tileHalfW, topY), Offset(cx, topY + tileHalfH), 1.8f)
    drawLine(highlightCol.copy(alpha = 0.8f), Offset(cx + tileHalfW, topY), Offset(cx, topY + tileHalfH), 1.8f)
    drawLine(highlightCol.copy(alpha = 0.8f), Offset(cx, topY + tileHalfH), Offset(cx, topY + tileHalfH + bh), 1.8f)
}

/**
 * Reusable crisp 3D isometric voxel icon rendered natively on Canvas for Hotbar, Inventory, Crafting & Codex.
 */
@Composable
fun MiniVoxelBlockIcon(
    block: BlockType,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val tileHalfW = w * 0.44f
        val tileHalfH = h * 0.22f
        val blockH = h * 0.44f
        val cx = w * 0.5f
        val cy = h * 0.28f

        drawIsometricVoxel(
            voxel = VisibleVoxel(
                pos = BlockPos(0, WORLD_HEIGHT - 1, 0),
                block = block,
                screenX = cx,
                screenY = cy,
                torchLight = if (block.emitsLight) 0.9f else 0f
            ),
            tileHalfW = tileHalfW,
            tileHalfH = tileHalfH,
            blockHeight = blockH,
            ambientLight = 1.0f,
            animPhase = 0f,
            isHighlighted = false
        )
    }
}
