package de.vcxrisi.sternengarten.ui.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import de.vcxrisi.sternengarten.game.model.Hex
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private val SQRT3 = sqrt(3f)

/** Umrechnung zwischen Hex-Feldern und Weltkoordinaten (pointy-top). */
class HexLayout(val size: Float) {

    fun center(hex: Hex): Offset = Offset(
        x = size * SQRT3 * (hex.q + hex.r / 2f),
        y = size * 1.5f * hex.r,
    )

    fun hexAt(world: Offset): Hex {
        val q = (SQRT3 / 3f * world.x - world.y / 3f) / size
        val r = (2f / 3f * world.y) / size
        return round(q, r)
    }

    /** Sechseck-Umriss um den Ursprung; wird beim Zeichnen verschoben. */
    fun hexPath(scale: Float = 0.94f): Path = Path().apply {
        for (i in 0 until 6) {
            val angle = PI / 180.0 * (60 * i - 30)
            val x = (size * scale * cos(angle)).toFloat()
            val y = (size * scale * sin(angle)).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    private fun round(fq: Float, fr: Float): Hex {
        val fs = -fq - fr
        var q = fq.roundToInt()
        var r = fr.roundToInt()
        val s = fs.roundToInt()
        val dq = abs(q - fq)
        val dr = abs(r - fr)
        val ds = abs(s - fs)
        if (dq > dr && dq > ds) q = -r - s else if (dr > ds) r = -q - s
        return Hex(q, r)
    }
}
