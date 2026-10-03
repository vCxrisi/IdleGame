package de.vcxrisi.sternengarten.ui.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import de.vcxrisi.sternengarten.game.model.LifePhase
import de.vcxrisi.sternengarten.game.model.StarType
import de.vcxrisi.sternengarten.ui.theme.StarColors
import de.vcxrisi.sternengarten.ui.theme.WhiteDwarfColors
import de.vcxrisi.sternengarten.ui.theme.starColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Alles, was zum Zeichnen eines Sterns nötig ist – unabhängig vom Spielzustand. */
data class StarVisual(
    val type: StarType,
    val phase: LifePhase = LifePhase.MAIN,
    /** 0..1 innerhalb der Geburtsphase, sonst 1. */
    val birth: Float = 1f,
    /** 0..1 Anteil der Lebenszeit, für das Anschwellen vor der Supernova. */
    val ageFraction: Float = 0f,
    val level: Int = 1,
    /** Nur Schwarzes Loch: Füllstand 0..1. */
    val fill: Float = 0f,
)

private const val TAU = (2 * PI).toFloat()

/**
 * Zeichnet einen Stern mit Leuchthof, Korona, Kern und Strahlen.
 * [unit] ist die Feldgröße, [seed] sorgt für individuelle Phasen.
 */
fun DrawScope.drawStar(center: Offset, unit: Float, visual: StarVisual, time: Float, seed: Float = 0f) {
    when {
        visual.type == StarType.BLACK_HOLE -> drawBlackHole(center, unit, visual, time, seed)
        visual.type == StarType.NEBULA_NURSERY -> drawNursery(center, unit, visual, time, seed)
        visual.type == StarType.QUASAR -> drawQuasar(center, unit, visual, time, seed)
        visual.type == StarType.BINARY && visual.phase != LifePhase.WHITE_DWARF -> drawBinary(center, unit, visual, time, seed)
        else -> drawSingleStar(center, unit, visual, time, seed)
    }
}

private fun colorsFor(visual: StarVisual): StarColors {
    if (visual.phase == LifePhase.WHITE_DWARF) return WhiteDwarfColors
    val base = starColors(visual.type)
    if (visual.phase != LifePhase.GIANT) return base
    // Riesen werden röter und unruhiger.
    val redden = if (visual.type == StarType.BLUE_GIANT) 0.15f else 0.5f
    return base.copy(glow = lerp(base.glow, Color(0xFFFF6A3D), redden))
}

private fun sizeFor(visual: StarVisual): Float {
    val typeSize = when (visual.type) {
        StarType.RED_DWARF -> 0.26f
        StarType.YELLOW_STAR -> 0.32f
        StarType.BLUE_GIANT -> 0.40f
        StarType.BINARY -> 0.24f
        StarType.PULSAR -> 0.22f
        StarType.BLACK_HOLE -> 0.34f
        StarType.NEUTRON_STAR -> 0.15f
        StarType.MAGNETAR -> 0.24f
        StarType.NEBULA_NURSERY -> 0.30f
        StarType.QUASAR -> 0.26f
    }
    val levelBoost = 1f + min(0.25f, (visual.level - 1) * 0.01f)
    val phase = when (visual.phase) {
        LifePhase.PROTO -> 0.35f + 0.65f * visual.birth
        LifePhase.MAIN -> 1f
        LifePhase.GIANT -> 1.25f + 0.25f * visual.ageFraction
        LifePhase.WHITE_DWARF -> 0.55f
    }
    return typeSize * levelBoost * phase
}

private fun DrawScope.drawSingleStar(center: Offset, unit: Float, visual: StarVisual, time: Float, seed: Float) {
    val colors = colorsFor(visual)
    val pulseSpeed = if (visual.phase == LifePhase.GIANT) 4.5f else 1.6f
    val pulse = 1f + 0.06f * sin(time * pulseSpeed + seed * 7f)
    val flicker = if (visual.phase == LifePhase.PROTO) 0.7f + 0.3f * sin(time * 13f + seed * 3f) else 1f
    val r = unit * sizeFor(visual) * pulse

    // Weicher Leuchthof
    drawGlow(center, r * 3.4f, colors.glow, 0.42f * flicker)
    // Korona
    drawGlow(center, r * 1.7f, colors.glow, 0.75f * flicker)

    // Protosterne: kreisende Staubscheibe
    if (visual.phase == LifePhase.PROTO) {
        rotate(time * 90f + seed * 40f, center) {
            scale(1f, 0.45f, center) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        listOf(Color.Transparent, colors.glow.copy(alpha = 0.6f), Color.Transparent, colors.glow.copy(alpha = 0.4f), Color.Transparent),
                        center,
                    ),
                    radius = r * 2.4f, center = center, style = Stroke(width = r * 0.5f), blendMode = BlendMode.Plus,
                )
            }
        }
    }

    // Strahlen (Beugungsspikes)
    if (visual.phase != LifePhase.PROTO) {
        val spikeLength = r * if (visual.type == StarType.BLUE_GIANT) 4.2f else 3.2f
        val angle = time * 6f + seed * 90f
        for (k in 0 until 2) {
            rotate(angle + k * 90f, center) {
                val brush = Brush.linearGradient(
                    0f to Color.Transparent,
                    0.5f to colors.core.copy(alpha = 0.7f),
                    1f to Color.Transparent,
                    start = Offset(center.x - spikeLength, center.y),
                    end = Offset(center.x + spikeLength, center.y),
                )
                drawLine(brush, Offset(center.x - spikeLength, center.y), Offset(center.x + spikeLength, center.y), strokeWidth = r * 0.12f, blendMode = BlendMode.Plus)
            }
        }
    }

    // Pulsar: rotierende Leuchtkegel
    if (visual.type == StarType.PULSAR) drawPulsarBeams(center, r, colors.glow, time, seed)
    if (visual.type == StarType.NEUTRON_STAR) drawNeutronShell(center, r, colors.glow, time, seed)
    if (visual.type == StarType.MAGNETAR) drawFieldLines(center, r, colors, time, seed)

    // Heißer Kern
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color.White,
            0.45f to colors.core,
            1f to colors.glow.copy(alpha = 0.0f),
            center = center, radius = r,
        ),
        radius = r, center = center,
    )

    // Riesen kurz vor dem Ende flackern unruhig
    if (visual.phase == LifePhase.GIANT && visual.ageFraction > 0.9f) {
        val warn = 0.5f + 0.5f * sin(time * 18f)
        drawCircle(colors.glow.copy(alpha = 0.25f * warn), r * 2.2f, center, style = Stroke(width = r * 0.15f), blendMode = BlendMode.Plus)
    }
}

private fun DrawScope.drawPulsarBeams(center: Offset, r: Float, color: Color, time: Float, seed: Float) {
    val angle = time * 2.6f + seed
    val length = r * 7f
    val spread = 0.13f
    for (k in 0 until 2) {
        val a = angle + k * PI.toFloat()
        val tip1 = Offset(center.x + cos(a - spread) * length, center.y + sin(a - spread) * length)
        val tip2 = Offset(center.x + cos(a + spread) * length, center.y + sin(a + spread) * length)
        val path = Path().apply {
            moveTo(center.x, center.y)
            lineTo(tip1.x, tip1.y)
            lineTo(tip2.x, tip2.y)
            close()
        }
        drawPath(
            path,
            brush = Brush.radialGradient(listOf(color.copy(alpha = 0.55f), Color.Transparent), center, length),
            blendMode = BlendMode.Plus,
        )
    }
}

/** Neutronenstern: winziger, gleißender Kern mit schnell pulsierender Hülle. */
private fun DrawScope.drawNeutronShell(center: Offset, r: Float, color: Color, time: Float, seed: Float) {
    val beat = (time * 3.2f + seed) % 1f
    drawCircle(color.copy(alpha = 0.7f * (1f - beat)), r * (1.4f + 2.2f * beat), center, style = Stroke(width = r * 0.18f), blendMode = BlendMode.Plus)
    drawGlow(center, r * 2.2f, Color.White, 0.9f)
}

/** Magnetar: geschwungene Feldlinien, die langsam rotieren. */
private fun DrawScope.drawFieldLines(center: Offset, r: Float, colors: StarColors, time: Float, seed: Float) {
    rotate(time * 12f + seed * 60f, center) {
        for (k in 0 until 3) {
            val w = r * (3.2f + k * 1.1f)
            val h = r * (1.3f + k * 0.35f)
            val color = if (k % 2 == 0) colors.glow else colors.secondary
            for (side in listOf(-1f, 1f)) {
                drawOval(
                    color = color.copy(alpha = 0.45f - k * 0.1f),
                    topLeft = Offset(center.x + (if (side < 0) -w else 0f), center.y - h / 2f),
                    size = Size(w, h),
                    style = Stroke(width = r * 0.09f),
                    blendMode = BlendMode.Plus,
                )
            }
        }
    }
}

/** Nebelwiege: wogende Gaswolke mit kleinen, glitzernden Protosternen. */
private fun DrawScope.drawNursery(center: Offset, unit: Float, visual: StarVisual, time: Float, seed: Float) {
    val colors = starColors(StarType.NEBULA_NURSERY)
    val r = unit * sizeFor(visual)
    for (k in 0 until 5) {
        val a = time * 0.4f + k * 1.26f + seed * 6f
        val c = Offset(center.x + cos(a) * r * 0.55f, center.y + sin(a * 1.3f) * r * 0.45f)
        drawGlow(c, r * (1.3f + 0.25f * sin(time + k)), if (k % 2 == 0) colors.glow else colors.secondary, 0.45f)
    }
    for (k in 0 until 4) {
        val a = -time * 0.9f + k * 1.57f + seed
        val c = Offset(center.x + cos(a) * r * 0.75f, center.y + sin(a) * r * 0.5f)
        val twinkle = 0.5f + 0.5f * sin(time * 5f + k * 2f)
        drawGlow(c, r * 0.35f, Color.White, 0.9f * twinkle)
    }
}

/** Quasar: gleißende Scheibe mit zwei relativistischen Jets. */
private fun DrawScope.drawQuasar(center: Offset, unit: Float, visual: StarVisual, time: Float, seed: Float) {
    val colors = starColors(StarType.QUASAR)
    val r = unit * sizeFor(visual) * (1f + 0.05f * sin(time * 2f + seed))
    drawGlow(center, r * 4.5f, colors.glow, 0.45f)
    val jet = r * 6.5f
    for (dir in listOf(-1f, 1f)) {
        val end = Offset(center.x, center.y + dir * jet)
        drawLine(
            Brush.linearGradient(listOf(colors.secondary.copy(alpha = 0.9f), Color.Transparent), center, end),
            center, end, strokeWidth = r * 0.35f, blendMode = BlendMode.Plus,
        )
        val knot = (time * 0.8f + seed + (if (dir > 0) 0.5f else 0f)) % 1f
        drawGlow(Offset(center.x, center.y + dir * jet * knot), r * 0.6f, colors.secondary, 0.8f * (1f - knot))
    }
    scale(1f, 0.32f, center) {
        rotate(time * 90f, center) {
            drawCircle(
                brush = Brush.sweepGradient(listOf(colors.glow, Color.White, colors.secondary, colors.glow), center),
                radius = r * 1.6f, center = center, style = Stroke(width = r * 0.7f), blendMode = BlendMode.Plus,
            )
        }
    }
    drawCircle(
        brush = Brush.radialGradient(0f to Color.White, 0.6f to colors.core, 1f to colors.glow.copy(alpha = 0f), center = center, radius = r),
        radius = r, center = center,
    )
}

private fun DrawScope.drawBinary(center: Offset, unit: Float, visual: StarVisual, time: Float, seed: Float) {
    val colors = colorsFor(visual)
    val r = unit * sizeFor(visual)
    val orbit = unit * 0.2f
    val angle = time * 1.4f + seed * 5f
    drawGlow(center, r * 4.2f, colors.glow, 0.35f)
    // Gemeinsame Hülle als feiner Ring
    scale(1f, 0.55f, center) {
        drawCircle(colors.glow.copy(alpha = 0.25f), orbit, center, style = Stroke(width = r * 0.08f), blendMode = BlendMode.Plus)
    }
    val tints = listOf(colors.glow, colors.secondary)
    for (k in 0 until 2) {
        val a = angle + k * PI.toFloat()
        val c = Offset(center.x + cos(a) * orbit, center.y + sin(a) * orbit * 0.55f)
        val tint = tints[k]
        drawGlow(c, r * 1.8f, tint, 0.7f)
        drawCircle(
            brush = Brush.radialGradient(0f to Color.White, 0.5f to colors.core, 1f to tint.copy(alpha = 0f), center = c, radius = r * 0.8f),
            radius = r * 0.8f, center = c,
        )
    }
}

private fun DrawScope.drawBlackHole(center: Offset, unit: Float, visual: StarVisual, time: Float, seed: Float) {
    val colors = starColors(StarType.BLACK_HOLE)
    val r = unit * sizeFor(visual)
    val hunger = 0.6f + 0.4f * visual.fill
    drawGlow(center, r * 3.2f, Color(0xFF7A3DFF), 0.35f * hunger)

    // Akkretionsscheibe (flach, rotierend)
    rotate(-12f, center) {
        scale(1f, 0.36f, center) {
            rotate(time * 70f + seed * 30f, center) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        listOf(
                            colors.glow.copy(alpha = 0.95f),
                            colors.secondary.copy(alpha = 0.6f),
                            Color.Transparent,
                            colors.glow.copy(alpha = 0.8f),
                            Color(0xFFFFF1C2).copy(alpha = 0.9f),
                            colors.glow.copy(alpha = 0.95f),
                        ),
                        center,
                    ),
                    radius = r * 1.9f, center = center, style = Stroke(width = r * 0.9f), blendMode = BlendMode.Plus,
                )
            }
        }
    }
    // Ereignishorizont
    drawCircle(Color.Black, r * 0.95f, center)
    drawCircle(
        brush = Brush.radialGradient(
            0.75f to Color.Transparent, 0.92f to Color(0xFFFFE3B0).copy(alpha = 0.85f), 1f to Color.Transparent,
            center = center, radius = r * 1.12f,
        ),
        radius = r * 1.12f, center = center, blendMode = BlendMode.Plus,
    )
    // Füllstand als Bogen
    if (visual.fill > 0.01f) {
        drawArc(
            color = Color(0xFFFFD27A).copy(alpha = 0.9f),
            startAngle = -90f, sweepAngle = 360f * visual.fill, useCenter = false,
            topLeft = Offset(center.x - r * 1.45f, center.y - r * 1.45f),
            size = Size(r * 2.9f, r * 2.9f),
            style = Stroke(width = r * 0.12f),
            blendMode = BlendMode.Plus,
        )
    }
}

fun DrawScope.drawGlow(center: Offset, radius: Float, color: Color, alpha: Float) {
    if (radius <= 0f || alpha <= 0f) return
    val a = alpha.coerceAtMost(1f)
    drawCircle(
        brush = Brush.radialGradient(
            0f to color.copy(alpha = a),
            0.35f to color.copy(alpha = a * 0.45f),
            1f to Color.Transparent,
            center = center, radius = radius,
        ),
        radius = radius, center = center, blendMode = BlendMode.Plus,
    )
}

/** Hilfsfunktion für [TAU]-basierte Winkel in Effekten. */
internal fun angleOf(index: Int, count: Int): Float = TAU * index / count
