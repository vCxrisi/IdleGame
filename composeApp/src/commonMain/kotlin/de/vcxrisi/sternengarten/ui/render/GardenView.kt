package de.vcxrisi.sternengarten.ui.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import de.vcxrisi.sternengarten.game.engine.Balance
import de.vcxrisi.sternengarten.game.engine.BoardAnalysis
import de.vcxrisi.sternengarten.game.engine.ConstellationInstance
import de.vcxrisi.sternengarten.game.model.Comet
import de.vcxrisi.sternengarten.game.model.ConstellationKind
import de.vcxrisi.sternengarten.game.model.GameState
import de.vcxrisi.sternengarten.game.model.Hex
import de.vcxrisi.sternengarten.game.model.LifePhase
import de.vcxrisi.sternengarten.game.model.Star
import de.vcxrisi.sternengarten.game.model.StarType
import de.vcxrisi.sternengarten.ui.fx.ParticleSystem
import de.vcxrisi.sternengarten.ui.theme.starColors
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Kamera über dem Garten: Verschiebung in Pixeln und Zoomfaktor. */
@Stable
class Camera {
    var pan by mutableStateOf(Offset.Zero)
    var zoom by mutableFloatStateOf(1f)
    var shake by mutableFloatStateOf(0f)
    var fitted = false

    fun toWorld(screen: Offset, viewport: Size): Offset =
        (screen - viewport.center() - pan) / zoom

    fun toScreen(world: Offset, viewport: Size): Offset =
        world * zoom + viewport.center() + pan

    fun zoomAround(focus: Offset, viewport: Size, factor: Float) {
        val newZoom = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val worldUnder = toWorld(focus, viewport)
        pan = focus - viewport.center() - worldUnder * newZoom
        zoom = newZoom
    }

    fun fit(viewport: Size, layout: HexLayout, radius: Int) {
        val gardenWidth = layout.size * sqrt(3f) * (radius * 2f + 1f)
        val gardenHeight = layout.size * (radius * 3f + 2f)
        zoom = min(viewport.width * 0.94f / gardenWidth, viewport.height * 0.55f / gardenHeight).coerceIn(MIN_ZOOM, MAX_ZOOM)
        pan = Offset(0f, -viewport.height * 0.02f)
        fitted = true
    }

    companion object {
        const val MIN_ZOOM = 0.35f
        const val MAX_ZOOM = 2.8f
    }
}

private fun Size.center() = Offset(width / 2f, height / 2f)

/** Wo der Komet gerade auf dem Bildschirm ist (normiert), oder null. */
fun cometPosition(comet: Comet, progress: Float): Offset {
    val p = progress.coerceIn(0f, 1f)
    return Offset(
        comet.startX + (comet.endX - comet.startX) * p,
        comet.startY + (comet.endY - comet.startY) * p,
    )
}

@Composable
fun GardenView(
    state: GameState,
    analysis: BoardAnalysis,
    selectedHex: Hex?,
    camera: Camera,
    layout: HexLayout,
    starfield: Starfield,
    particles: ParticleSystem,
    clock: Float,
    cometProgress: Float,
    onTapCell: (Hex) -> Unit,
    onTapComet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val currentState by rememberUpdatedState(state)
    val currentCometProgress by rememberUpdatedState(cometProgress)
    val tapCell by rememberUpdatedState(onTapCell)
    val tapComet by rememberUpdatedState(onTapComet)
    val radius = Balance.gardenRadius(state)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { centroid, panChange, zoomChange, _ ->
                    val viewport = Size(size.width.toFloat(), size.height.toFloat())
                    camera.pan += panChange
                    if (zoomChange != 1f) camera.zoomAround(centroid, viewport, zoomChange)
                }
            }
            .pointerInput(Unit) {
                // Mausrad-Zoom für Desktop.
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) {
                            val change = event.changes.first()
                            val viewport = Size(size.width.toFloat(), size.height.toFloat())
                            camera.zoomAround(change.position, viewport, exp(-change.scrollDelta.y * 0.12f))
                            change.consume()
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { position ->
                    val viewport = Size(size.width.toFloat(), size.height.toFloat())
                    val comet = currentState.comet
                    if (comet != null) {
                        val p = cometPosition(comet, currentCometProgress)
                        val screen = Offset(p.x * viewport.width, p.y * viewport.height)
                        if ((screen - position).getDistance() < 60f * density) {
                            tapComet()
                            return@detectTapGestures
                        }
                    }
                    tapCell(layout.hexAt(camera.toWorld(position, viewport)))
                }
            },
    ) {
        if (!camera.fitted) camera.fit(size, layout, radius)
        val shakeOffset = if (camera.shake > 0.01f) {
            Offset(sin(clock * 71f) * camera.shake, sin(clock * 53f + 1f) * camera.shake)
        } else Offset.Zero

        drawNebula(clock, state.law.hue, camera.pan)
        starfield.draw(this, clock, camera.pan, density)

        withTransform({
            translate(size.width / 2f + camera.pan.x + shakeOffset.x, size.height / 2f + camera.pan.y + shakeOffset.y)
            scale(camera.zoom, camera.zoom, pivot = Offset.Zero)
        }) {
            drawCells(state, layout, radius, selectedHex, clock)
            drawPulsarRays(state, layout, clock)
            drawBlackHoleStreams(state, layout, analysis, clock)
            drawConstellations(analysis.constellations, layout, clock)
            for ((hex, star) in state.stars) {
                val center = layout.center(hex)
                drawStar(center, layout.size, visualOf(state, star), clock, seedOf(hex))
                if (star.level > 1) {
                    val label = textMeasurer.measure(
                        "${star.level}",
                        TextStyle(color = Color.White.copy(alpha = 0.85f), fontSize = 9.sp, fontWeight = FontWeight.Bold),
                    )
                    drawText(label, topLeft = Offset(center.x - label.size.width / 2f, center.y + layout.size * 0.5f))
                }
            }
            selectedHex?.let { drawSelection(layout.center(it), layout, clock) }
            particles.draw(this)
        }

        state.comet?.let { drawComet(it, cometProgress, clock) }
    }
}

/** Stabiler Pseudozufall pro Feld im Bereich 0..1. */
fun seedOf(hex: Hex): Float {
    val hash = (hex.q * 73856093) xor (hex.r * 19349663)
    return ((hash % 1000) + 1000) % 1000 / 1000f
}

fun visualOf(state: GameState, star: Star): StarVisual {
    val phase = Balance.phaseOf(state, star)
    val lifespan = Balance.effectiveLifespan(state, star.type)
    return StarVisual(
        type = star.type,
        phase = phase,
        birth = (star.age / Balance.PROTO_SECONDS).toFloat().coerceIn(0f, 1f),
        ageFraction = if (lifespan != null && !star.whiteDwarf) (star.age / lifespan).toFloat().coerceIn(0f, 1f) else 0f,
        level = star.level,
        fill = if (star.type == StarType.BLACK_HOLE) (star.stored / (star.stored + 5_000.0)).toFloat() else 0f,
    )
}

private fun DrawScope.drawCells(state: GameState, layout: HexLayout, radius: Int, selected: Hex?, clock: Float) {
    val path = layout.hexPath()
    val hue = state.law.hue
    val edge = Color.hsv((hue + 20f) % 360f, 0.35f, 1f)
    for (hex in Hex.area(radius)) {
        val c = layout.center(hex)
        val enrichment = state.enrichment[hex] ?: 0.0
        val occupied = hex in state.stars
        translate(c.x, c.y) {
            val baseAlpha = if (occupied) 0.10f else 0.06f
            drawPath(path, Color.hsv(hue, 0.45f, 0.75f).copy(alpha = baseAlpha))
            if (enrichment > 0.0) {
                val glow = (0.10f + 0.07f * enrichment.toFloat()).coerceAtMost(0.4f) * (0.85f + 0.15f * sin(clock * 1.5f + seedOf(hex) * 6f))
                drawPath(
                    path,
                    Brush.radialGradient(listOf(Color(0xFFFFC85A).copy(alpha = glow), Color(0xFFFF7A3D).copy(alpha = glow * 0.3f)), Offset.Zero, layout.size),
                    blendMode = BlendMode.Plus,
                )
            }
            drawPath(path, edge.copy(alpha = if (occupied) 0.30f else 0.22f), style = Stroke(width = 1.5f))
        }
    }
    // Der nächste Ring als Versprechen auf Wachstum.
    if (radius < Balance.MAX_RADIUS) {
        val dashed = Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
        for (hex in Hex.ring(radius + 1)) {
            val c = layout.center(hex)
            translate(c.x, c.y) { drawPath(path, edge.copy(alpha = 0.06f), style = dashed) }
        }
    }
}

private fun DrawScope.drawSelection(center: Offset, layout: HexLayout, clock: Float) {
    val path = layout.hexPath(0.98f)
    val pulse = 0.6f + 0.4f * sin(clock * 5f)
    translate(center.x, center.y) {
        drawPath(path, Color(0xFF9EE8FF).copy(alpha = 0.9f * pulse), style = Stroke(width = 2.5f))
        drawPath(path, Color(0xFF9EE8FF).copy(alpha = 0.12f * pulse), blendMode = BlendMode.Plus)
    }
}

private fun DrawScope.drawPulsarRays(state: GameState, layout: HexLayout, clock: Float) {
    val color = Color(0xFF3DFFE0)
    for ((hex, star) in state.stars) {
        if (star.type != StarType.PULSAR) continue
        val from = layout.center(hex)
        for ((i, dir) in Hex.DIRECTIONS.withIndex()) {
            val to = layout.center(hex + dir * Balance.PULSAR_RANGE)
            drawLine(
                Brush.linearGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent), from, to),
                from, to, strokeWidth = 3f, blendMode = BlendMode.Plus,
            )
            val t = (clock * 0.9f + i / 6f) % 1f
            val dot = from + (to - from) * t
            drawGlow(dot, layout.size * 0.25f, color, 0.8f * (1f - t))
        }
    }
}

private fun DrawScope.drawBlackHoleStreams(state: GameState, layout: HexLayout, analysis: BoardAnalysis, clock: Float) {
    for ((hex, _) in analysis.blackHoleInflow) {
        val target = layout.center(hex)
        for (n in hex.neighbors()) {
            val star = state.stars[n] ?: continue
            if (star.type == StarType.BLACK_HOLE) continue
            val from = layout.center(n)
            val t = (clock * 0.7f + seedOf(n)) % 1f
            val p = from + (target - from) * t
            drawLine(
                Brush.linearGradient(listOf(Color(0xFFFF9A3D).copy(alpha = 0.25f), Color(0xFF7A3DFF).copy(alpha = 0.05f)), from, target),
                from, target, strokeWidth = 2f, blendMode = BlendMode.Plus,
            )
            drawGlow(p, layout.size * 0.18f, Color(0xFFFFB86B), 0.9f * (1f - t))
        }
    }
}

/** Zeichnet jede Sternbild-Kante genau einmal, in der Farbe des wertvollsten Sternbilds. */
private fun DrawScope.drawConstellations(instances: List<ConstellationInstance>, layout: HexLayout, clock: Float) {
    if (instances.isEmpty()) return
    val segments = HashMap<Pair<Hex, Hex>, ConstellationKind>()
    fun add(a: Hex, b: Hex, kind: ConstellationKind) {
        val key = if (a.q < b.q || (a.q == b.q && a.r < b.r)) a to b else b to a
        val existing = segments[key]
        if (existing == null || kind.memberBonus > existing.memberBonus) segments[key] = kind
    }
    for (c in instances) {
        val m = c.members
        when (c.kind) {
            ConstellationKind.TRIO, ConstellationKind.RED_THREAD, ConstellationKind.RAINBOW ->
                for (i in 0 until m.size - 1) add(m[i], m[i + 1], c.kind)
            ConstellationKind.TRIANGULUM -> {
                add(m[0], m[1], c.kind); add(m[1], m[2], c.kind); add(m[2], m[0], c.kind)
            }
            ConstellationKind.CROWN, ConstellationKind.SUN_CROWN, ConstellationKind.EVENT_HORIZON -> {
                val ring = m.drop(1)
                for (i in ring.indices) add(ring[i], ring[(i + 1) % ring.size], c.kind)
                if (c.kind != ConstellationKind.CROWN) for (r in ring) add(m[0], r, c.kind)
            }
        }
    }
    for ((key, kind) in segments) {
        val a = layout.center(key.first)
        val b = layout.center(key.second)
        // Etwas Abstand zu den Sternen lassen.
        val dir = (b - a) / (b - a).getDistance()
        val inset = layout.size * 0.42f
        val start = a + dir * inset
        val end = b - dir * inset
        val color = Color.hsv(kind.hue, 0.55f, 1f)
        val wave = 0.55f + 0.25f * sin(clock * 2f + seedOf(key.first) * 6f)
        drawLine(color.copy(alpha = 0.18f * wave), start, end, strokeWidth = 7f, blendMode = BlendMode.Plus)
        drawLine(color.copy(alpha = 0.75f * wave), start, end, strokeWidth = 1.6f, blendMode = BlendMode.Plus)
        val t = (clock * 0.45f + seedOf(key.second)) % 1f
        drawGlow(start + (end - start) * t, layout.size * 0.14f, color, 0.9f)
    }
}

private fun DrawScope.drawComet(comet: Comet, progress: Float, clock: Float) {
    val p = cometPosition(comet, progress)
    val head = Offset(p.x * size.width, p.y * size.height)
    val dir = Offset(comet.endX - comet.startX, comet.endY - comet.startY).let {
        val v = Offset(it.x * size.width, it.y * size.height)
        v / v.getDistance()
    }
    val tailLength = size.minDimension * 0.28f
    val tail = head - dir * tailLength
    val core = Color(0xFFDDF6FF)
    val ion = Color(0xFF6FD8FF)
    drawLine(
        Brush.linearGradient(listOf(ion.copy(alpha = 0.0f), ion.copy(alpha = 0.55f)), tail, head),
        tail, head, strokeWidth = 14f * density, blendMode = BlendMode.Plus,
    )
    drawLine(
        Brush.linearGradient(listOf(Color.Transparent, core.copy(alpha = 0.9f)), head - dir * tailLength * 0.6f, head),
        head - dir * tailLength * 0.6f, head, strokeWidth = 3f * density, blendMode = BlendMode.Plus,
    )
    val pulse = 1f + 0.15f * sin(clock * 9f)
    drawGlow(head, 46f * density * pulse, ion, 0.7f)
    drawGlow(head, 18f * density, Color.White, 1f)
    // Hinweisring: "tipp mich"
    drawCircle(core.copy(alpha = 0.35f + 0.25f * sin(clock * 6f)), 30f * density * pulse, head, style = Stroke(width = 1.5f * density))
}

/** Farbe, in der ein Stern Staubpartikel abgibt. */
fun moteColor(state: GameState, star: Star): Color = when {
    star.whiteDwarf -> Color(0xFFDDE8FF)
    Balance.phaseOf(state, star) == LifePhase.GIANT -> Color(0xFFFFB27A)
    else -> starColors(star.type).glow
}
