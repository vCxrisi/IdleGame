package de.vcxrisi.sternengarten.ui.fx

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Leichtgewichtiges Partikelsystem in Weltkoordinaten. Arbeitet mit festen Arrays,
 * damit pro Frame nichts alloziert wird.
 */
class ParticleSystem(private val capacity: Int = 900) {
    private val x = FloatArray(capacity)
    private val y = FloatArray(capacity)
    private val vx = FloatArray(capacity)
    private val vy = FloatArray(capacity)
    private val life = FloatArray(capacity)
    private val maxLife = FloatArray(capacity)
    private val radius = FloatArray(capacity)
    private val drag = FloatArray(capacity)
    private val gravityX = FloatArray(capacity)
    private val gravityY = FloatArray(capacity)
    private val color = arrayOfNulls<Color>(capacity)
    private var count = 0

    private val rings = ArrayList<Ring>()
    private val random = Random.Default

    private class Ring(val center: Offset, val maxRadius: Float, val color: Color, val duration: Float, var age: Float = 0f)

    val isEmpty: Boolean get() = count == 0 && rings.isEmpty()

    /** Entfernt alle Partikel und Ringe – etwa beim Wechsel in eine andere Galaxie. */
    fun clear() {
        count = 0
        rings.clear()
    }

    /** Optionale Umfärbung jedes neuen Partikels (Funken-Stil aus dem Shop). */
    var palette: ((Color) -> Color)? = null

    fun emit(
        at: Offset,
        velocity: Offset,
        lifetime: Float,
        size: Float,
        tint: Color,
        friction: Float = 0f,
        pull: Offset = Offset.Zero,
    ) {
        if (count >= capacity) return
        val i = count++
        x[i] = at.x; y[i] = at.y
        vx[i] = velocity.x; vy[i] = velocity.y
        life[i] = lifetime; maxLife[i] = lifetime
        radius[i] = size
        drag[i] = friction
        gravityX[i] = pull.x; gravityY[i] = pull.y
        color[i] = palette?.invoke(tint) ?: tint
    }

    /** Explosion in alle Richtungen. */
    fun burst(at: Offset, amount: Int, speed: Float, size: Float, tint: Color, lifetime: Float = 1.2f) {
        repeat(amount) {
            val a = random.nextFloat() * 2f * PI.toFloat()
            val s = speed * (0.3f + random.nextFloat() * 0.7f)
            emit(
                at = at,
                velocity = Offset(cos(a) * s, sin(a) * s),
                lifetime = lifetime * (0.6f + random.nextFloat() * 0.6f),
                size = size * (0.5f + random.nextFloat()),
                tint = tint,
                friction = 1.6f,
            )
        }
    }

    /** Sternenstaub, der sanft aus einem Stern aufsteigt. */
    fun mote(at: Offset, spread: Float, tint: Color, size: Float) {
        val a = random.nextFloat() * 2f * PI.toFloat()
        emit(
            at = Offset(at.x + cos(a) * spread, at.y + sin(a) * spread),
            velocity = Offset((random.nextFloat() - 0.5f) * size * 4f, -size * (5f + random.nextFloat() * 6f)),
            lifetime = 1.6f + random.nextFloat() * 1.2f,
            size = size * (0.6f + random.nextFloat() * 0.6f),
            tint = tint,
            friction = 0.4f,
        )
    }

    /** Partikel, die spiralförmig in ein Ziel stürzen (Schwarzes Loch). */
    fun infall(target: Offset, distance: Float, tint: Color, size: Float) {
        val a = random.nextFloat() * 2f * PI.toFloat()
        val start = Offset(target.x + cos(a) * distance, target.y + sin(a) * distance)
        val tangent = Offset(-sin(a), cos(a)) * distance * 1.2f
        val toward = (target - start) * 0.9f
        emit(start, tangent + toward, 1.1f, size, tint, friction = 1.2f, pull = (target - start) * 2.2f)
    }

    fun shockwave(at: Offset, maxRadius: Float, tint: Color, duration: Float = 1.1f) {
        rings += Ring(at, maxRadius, tint, duration)
    }

    fun update(dt: Float) {
        var i = 0
        while (i < count) {
            life[i] -= dt
            if (life[i] <= 0f) {
                // Mit dem letzten Partikel tauschen, damit das Array dicht bleibt.
                val last = --count
                x[i] = x[last]; y[i] = y[last]; vx[i] = vx[last]; vy[i] = vy[last]
                life[i] = life[last]; maxLife[i] = maxLife[last]; radius[i] = radius[last]
                drag[i] = drag[last]; gravityX[i] = gravityX[last]; gravityY[i] = gravityY[last]
                color[i] = color[last]
                continue
            }
            val damping = 1f - drag[i] * dt
            vx[i] = (vx[i] + gravityX[i] * dt) * damping
            vy[i] = (vy[i] + gravityY[i] * dt) * damping
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt
            i++
        }
        val iterator = rings.iterator()
        while (iterator.hasNext()) {
            val ring = iterator.next()
            ring.age += dt
            if (ring.age >= ring.duration) iterator.remove()
        }
    }

    fun draw(scope: DrawScope) = with(scope) {
        for (i in 0 until count) {
            val t = life[i] / maxLife[i]
            val c = color[i] ?: continue
            val center = Offset(x[i], y[i])
            val r = radius[i] * (0.4f + 0.6f * t)
            // Drei Ringe mit abnehmender Deckkraft ergeben einen weichen Funken ohne teure Verläufe.
            drawCircle(c.copy(alpha = 0.07f * t), r * 2.6f, center, blendMode = BlendMode.Plus)
            drawCircle(c.copy(alpha = 0.16f * t), r * 1.6f, center, blendMode = BlendMode.Plus)
            drawCircle(c.copy(alpha = 0.9f * t), r * 0.7f, center, blendMode = BlendMode.Plus)
        }
        for (ring in rings) {
            val p = ring.age / ring.duration
            val eased = 1f - (1f - p) * (1f - p) * (1f - p)
            val r = ring.maxRadius * eased
            val alpha = (1f - p)
            drawCircle(
                brush = Brush.radialGradient(
                    0.6f to Color.Transparent,
                    0.92f to ring.color.copy(alpha = 0.55f * alpha),
                    1f to Color.Transparent,
                    center = ring.center, radius = r.coerceAtLeast(1f),
                ),
                radius = r.coerceAtLeast(1f), center = ring.center, blendMode = BlendMode.Plus,
            )
            drawCircle(ring.color.copy(alpha = 0.8f * alpha), r, ring.center, style = Stroke(width = 2f + 6f * (1f - p)), blendMode = BlendMode.Plus)
        }
    }
}
