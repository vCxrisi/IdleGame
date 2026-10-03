package de.vcxrisi.sternengarten.ui.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import de.vcxrisi.sternengarten.ui.theme.Palette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/** Vorberechnetes Sternenfeld in drei Parallax-Ebenen. */
class Starfield(seed: Int = 7) {
    private class Layer(val parallax: Float, val count: Int, val maxRadius: Float, random: Random) {
        val x = FloatArray(count) { random.nextFloat() }
        val y = FloatArray(count) { random.nextFloat() }
        val radius = FloatArray(count) { 0.4f + random.nextFloat() * maxRadius }
        val phase = FloatArray(count) { random.nextFloat() * 6.28f }
        val speed = FloatArray(count) { 0.6f + random.nextFloat() * 2.2f }
        val tint = IntArray(count) { random.nextInt(4) }
    }

    private val random = Random(seed)
    private val layers = listOf(
        Layer(0.03f, 140, 0.9f, random),
        Layer(0.08f, 70, 1.4f, random),
        Layer(0.16f, 28, 2.0f, random),
    )
    private val tints = listOf(Color.White, Color(0xFFCFE0FF), Color(0xFFFFE6C9), Color(0xFFE6D2FF))

    fun draw(scope: DrawScope, time: Float, pan: Offset, density: Float) = with(scope) {
        val w = size.width
        val h = size.height
        for (layer in layers) {
            for (i in 0 until layer.count) {
                var px = (layer.x[i] * w + pan.x * layer.parallax) % w
                var py = (layer.y[i] * h + pan.y * layer.parallax) % h
                if (px < 0) px += w
                if (py < 0) py += h
                val twinkle = 0.55f + 0.45f * sin(time * layer.speed[i] + layer.phase[i])
                val r = layer.radius[i] * density
                val color = tints[layer.tint[i]]
                drawCircle(color.copy(alpha = 0.85f * twinkle), r, Offset(px, py))
                if (layer.maxRadius > 1.5f && twinkle > 0.8f) {
                    drawGlow(Offset(px, py), r * 5f, color, 0.22f * twinkle)
                }
            }
        }
    }
}

/** Tiefer Raum mit langsam treibenden Nebelschwaden im Farbton der Galaxie. */
fun DrawScope.drawNebula(time: Float, hue: Float, pan: Offset) {
    drawRect(Brush.verticalGradient(listOf(Palette.SpaceTop, Palette.SpaceMid, Palette.SpaceBottom)))

    val w = size.width
    val h = size.height
    val big = max(w, h)
    val blobs = listOf(
        Blob(0.22f, 0.28f, 0.62f, 0f, 0.20f, 0.07f),
        Blob(0.78f, 0.22f, 0.55f, 38f, 0.16f, 0.05f),
        Blob(0.68f, 0.72f, 0.70f, -46f, 0.18f, 0.04f),
        Blob(0.18f, 0.82f, 0.50f, 150f, 0.12f, 0.06f),
        Blob(0.50f, 0.50f, 0.85f, 18f, 0.10f, 0.03f),
    )
    for ((i, b) in blobs.withIndex()) {
        val drift = time * b.speed + i * 1.7f
        val cx = (b.x + 0.06f * sin(drift)) * w + pan.x * 0.12f
        val cy = (b.y + 0.05f * cos(drift * 0.8f)) * h + pan.y * 0.12f
        val breathe = 1f + 0.08f * sin(drift * 1.3f)
        val radius = b.radius * big * breathe
        val color = Color.hsv(((hue + b.hueShift + 10f * sin(time * 0.05f + i)) % 360f + 360f) % 360f, 0.75f, 0.9f)
        drawCircle(
            brush = Brush.radialGradient(
                0f to color.copy(alpha = b.alpha * 1.25f),
                0.45f to color.copy(alpha = b.alpha * 0.45f),
                1f to Color.Transparent,
                center = Offset(cx, cy),
                radius = radius,
            ),
            radius = radius,
            center = Offset(cx, cy),
            blendMode = BlendMode.Plus,
        )
    }

    // Vignette: Ränder ins Dunkel ziehen, damit der Garten im Zentrum leuchtet.
    drawRect(
        Brush.radialGradient(
            0.45f to Color.Transparent,
            1f to Palette.SpaceTop.copy(alpha = 0.75f),
            center = Offset(w / 2f, h / 2f),
            radius = big * 0.75f,
        ),
    )

    // Feine, kreisende Staubbänder für Tiefe.
    for (i in 0 until 3) {
        val angle = time * 0.02f * (i + 1) + i * 2f * PI.toFloat() / 3f
        val c = Offset(w * 0.5f + cos(angle) * w * 0.3f, h * 0.5f + sin(angle) * h * 0.25f)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color.hsv((hue + 200f) % 360f, 0.4f, 1f).copy(alpha = 0.05f), Color.Transparent),
                center = c, radius = big * 0.35f,
            ),
            radius = big * 0.35f, center = c, blendMode = BlendMode.Plus,
        )
    }
}

private data class Blob(val x: Float, val y: Float, val radius: Float, val hueShift: Float, val alpha: Float, val speed: Float)
