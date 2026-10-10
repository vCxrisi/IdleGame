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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import de.vcxrisi.sternengarten.game.engine.Balance
import de.vcxrisi.sternengarten.game.engine.BoardAnalysis
import de.vcxrisi.sternengarten.game.engine.ConstellationInstance
import de.vcxrisi.sternengarten.game.model.ActiveEvent
import de.vcxrisi.sternengarten.game.model.Comet
import de.vcxrisi.sternengarten.game.model.ConstellationKind
import de.vcxrisi.sternengarten.game.model.CosmicEvent
import de.vcxrisi.sternengarten.game.model.GameState
import de.vcxrisi.sternengarten.game.model.Hex
import de.vcxrisi.sternengarten.game.model.LifePhase
import de.vcxrisi.sternengarten.game.model.Star
import de.vcxrisi.sternengarten.game.model.StarType
import de.vcxrisi.sternengarten.ui.fx.ParticleSystem
import de.vcxrisi.sternengarten.ui.theme.Palette
import de.vcxrisi.sternengarten.ui.theme.formatNumber
import de.vcxrisi.sternengarten.ui.theme.galaxyColor
import de.vcxrisi.sternengarten.ui.theme.starColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Gemerkte Kameraposition, etwa je Galaxie. */
data class CameraPose(val pan: Offset, val zoom: Float, val autoFit: Boolean)

/** Kamera über dem Garten: Verschiebung in Pixeln und Zoomfaktor. */
@Stable
class Camera {
    var pan by mutableStateOf(Offset.Zero)
    var zoom by mutableFloatStateOf(1f)
    var shake by mutableFloatStateOf(0f)
    var fitted = false

    /** Solange der Spieler nicht selbst verschiebt oder zoomt, folgt die Kamera dem wachsenden Garten. */
    var autoFit = true

    /** Ob die nächste Einpassung weich hinübergleitet statt zu springen. */
    private var animateFit = false
    private var targetPan: Offset? = null
    private var targetZoom = 1f

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

    /** Beim nächsten Zeichnen neu einpassen; [animate] gleitet hinüber. */
    fun requestFit(animate: Boolean) {
        fitted = false
        animateFit = animate
    }

    /** Der Spieler übernimmt: keine Kamerafahrt und keine automatische Einpassung mehr. */
    fun takeControl() {
        autoFit = false
        targetPan = null
    }

    fun pose(): CameraPose = CameraPose(pan, zoom, autoFit)

    fun moveTo(pose: CameraPose) {
        pan = pose.pan
        zoom = pose.zoom
        autoFit = pose.autoFit
        targetPan = null
        fitted = true
    }

    /** Zoomt so, dass alle [hexes] ins Bild passen, und rückt ihre Mitte ins Zentrum. */
    fun fitTo(viewport: Size, layout: HexLayout, hexes: Collection<Hex>) {
        if (hexes.isEmpty()) return
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (hex in hexes) {
            val c = layout.center(hex)
            minX = min(minX, c.x)
            maxX = max(maxX, c.x)
            minY = min(minY, c.y)
            maxY = max(maxY, c.y)
        }
        val gardenWidth = maxX - minX + layout.size * sqrt(3f)
        val gardenHeight = maxY - minY + layout.size * 2f
        val newZoom = min(viewport.width * 0.94f / gardenWidth, viewport.height * 0.55f / gardenHeight).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val mid = Offset((minX + maxX) / 2f, (minY + maxY) / 2f)
        val newPan = -mid * newZoom + Offset(0f, -viewport.height * 0.02f)
        if (animateFit) {
            targetPan = newPan
            targetZoom = newZoom
        } else {
            pan = newPan
            zoom = newZoom
            targetPan = null
        }
        animateFit = false
        fitted = true
    }

    /** Führt eine laufende Kamerafahrt pro Frame ein Stück weiter. */
    fun step(dt: Float) {
        val target = targetPan ?: return
        val k = 1f - exp(-9f * dt)
        pan += (target - pan) * k
        zoom += (targetZoom - zoom) * k
        if ((target - pan).getDistance() < 0.5f && abs(targetZoom - zoom) < 0.001f) {
            pan = target
            zoom = targetZoom
            targetPan = null
        }
    }

    companion object {
        const val MIN_ZOOM = 0.35f
        const val MAX_ZOOM = 2.8f

        /** Darunter wären die Preise an den Grenzfeldern nicht mehr lesbar. */
        const val PRICE_LABEL_ZOOM = 0.7f
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
    /** Felder, die sich freikaufen lassen, mit dem Preis des nächsten. */
    frontier: Set<Hex>,
    selectedField: Hex?,
    fieldPrice: Double,
    fieldAffordable: Boolean,
    camera: Camera,
    layout: HexLayout,
    starfield: Starfield,
    particles: ParticleSystem,
    clock: Float,
    cometProgress: Float,
    /** Sekunden seit dem letzten Galaxiewechsel – für den Warp-Blitz. */
    switchAge: Float,
    onTapCell: (Hex) -> Unit,
    onTapComet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val currentState by rememberUpdatedState(state)
    val currentCometProgress by rememberUpdatedState(cometProgress)
    val tapCell by rememberUpdatedState(onTapCell)
    val tapComet by rememberUpdatedState(onTapComet)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { centroid, panChange, zoomChange, _ ->
                    camera.takeControl()
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
                            camera.takeControl()
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
        if (!camera.fitted) camera.fitTo(size, layout, state.ownedFields + frontier)
        val kindColor = galaxyColor(state.activeGalaxy)
        val shakeOffset = if (camera.shake > 0.01f) {
            Offset(sin(clock * 71f) * camera.shake, sin(clock * 53f + 1f) * camera.shake)
        } else Offset.Zero

        drawNebula(clock, state.nebulaHue, camera.pan)
        starfield.draw(this, clock, camera.pan, density)

        withTransform({
            translate(size.width / 2f + camera.pan.x + shakeOffset.x, size.height / 2f + camera.pan.y + shakeOffset.y)
            scale(camera.zoom, camera.zoom, pivot = Offset.Zero)
        }) {
            drawCells(state, layout, clock)
            drawFrontier(state, frontier, layout, kindColor, fieldAffordable, priceLabel(textMeasurer, fieldPrice, kindColor, fieldAffordable, camera.zoom), clock)
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
            selectedHex?.let { drawSelection(layout.center(it), layout, clock, SELECTION_COLOR) }
            selectedField?.let { drawSelection(layout.center(it), layout, clock, kindColor) }
            particles.draw(this)
        }

        state.event?.let { drawEventOverlay(it, clock, camera.toScreen(Offset.Zero, size), layout.size * camera.zoom) }
        state.comet?.let { drawComet(it, cometProgress, clock) }
        if (switchAge in 0f..WARP_SECONDS) drawWarp(switchAge / WARP_SECONDS, camera.toScreen(Offset.Zero, size), kindColor)
    }
}

/** Dauer des Warp-Blitzes nach einem Galaxiewechsel. */
private const val WARP_SECONDS = 0.6f

private val SELECTION_COLOR = Color(0xFF9EE8FF)

/** Preis-Etikett der Grenzfelder; einmal pro Frame gemessen und nur, wenn es groß genug zu lesen ist. */
private fun priceLabel(measurer: TextMeasurer, price: Double, color: Color, affordable: Boolean, zoom: Float): TextLayoutResult? {
    if (zoom < Camera.PRICE_LABEL_ZOOM) return null
    return measurer.measure(
        formatNumber(price),
        TextStyle(color = (if (affordable) color else Palette.Danger).copy(alpha = 0.85f), fontSize = 10.sp, fontWeight = FontWeight.Bold),
    )
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

private fun DrawScope.drawCells(state: GameState, layout: HexLayout, clock: Float) {
    val path = layout.hexPath()
    // Jede Galaxieart behält ihren eigenen Farbton, auch unter fremden Naturgesetzen.
    val hue = state.activeGalaxy.hue
    val edge = Color.hsv((hue + 20f) % 360f, 0.35f, 1f)
    for (hex in state.ownedFields) {
        val c = layout.center(hex)
        val enrichment = state.enrichment[hex] ?: 0.0
        val occupied = hex in state.stars
        translate(c.x, c.y) {
            val baseAlpha = if (occupied) 0.10f else 0.06f
            drawPath(path, Color.hsv(hue, 0.45f, 0.75f).copy(alpha = baseAlpha))
            if (enrichment > 0.0) drawEnrichment(path, layout, enrichment, clock, hex, 1f)
            drawPath(path, edge.copy(alpha = if (occupied) 0.30f else 0.22f), style = Stroke(width = 1.5f))
        }
    }
}

/** Goldenes Leuchten gedüngter Felder; [strength] dämpft es auf noch nicht gekauften Feldern. */
private fun DrawScope.drawEnrichment(path: Path, layout: HexLayout, enrichment: Double, clock: Float, hex: Hex, strength: Float) {
    val glow = (0.10f + 0.07f * enrichment.toFloat()).coerceAtMost(0.4f) * (0.85f + 0.15f * sin(clock * 1.5f + seedOf(hex) * 6f)) * strength
    drawPath(
        path,
        Brush.radialGradient(listOf(Color(0xFFFFC85A).copy(alpha = glow), Color(0xFFFF7A3D).copy(alpha = glow * 0.3f)), Offset.Zero, layout.size),
        blendMode = BlendMode.Plus,
    )
}

/**
 * Felder, die sich freikaufen lassen: gestrichelt, mit „+“ und dem Preis des nächsten Feldes. Ist es bezahlbar,
 * pulsieren sie hell in der Farbe der Galaxie, sonst bleiben sie gedämpft.
 */
private fun DrawScope.drawFrontier(
    state: GameState,
    frontier: Set<Hex>,
    layout: HexLayout,
    color: Color,
    affordable: Boolean,
    price: TextLayoutResult?,
    clock: Float,
) {
    if (frontier.isEmpty()) return
    val path = layout.hexPath()
    val dashed = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
    val pulse = if (affordable) 0.75f + 0.25f * sin(clock * 2.4f) else 1f
    val arm = layout.size * 0.16f
    // Mit Preis rückt das Plus etwas nach oben, damit beides ins Feld passt.
    val plusY = if (price != null) -layout.size * 0.12f else 0f
    val plusColor = if (affordable) color.copy(alpha = 0.8f * pulse) else Palette.TextFaint.copy(alpha = 0.35f)
    for (hex in frontier) {
        val c = layout.center(hex)
        translate(c.x, c.y) {
            drawPath(path, color.copy(alpha = if (affordable) 0.035f else 0.015f))
            val enrichment = state.enrichment[hex] ?: 0.0
            if (enrichment > 0.0) drawEnrichment(path, layout, enrichment, clock, hex, 0.4f)
            drawPath(path, color.copy(alpha = if (affordable) 0.38f * pulse else 0.14f), style = dashed)
            drawLine(plusColor, Offset(-arm, plusY), Offset(arm, plusY), strokeWidth = 2f)
            drawLine(plusColor, Offset(0f, plusY - arm), Offset(0f, plusY + arm), strokeWidth = 2f)
            if (price != null) {
                drawText(price, topLeft = Offset(-price.size.width / 2f, layout.size * 0.32f - price.size.height / 2f))
            }
        }
    }
}

private fun DrawScope.drawSelection(center: Offset, layout: HexLayout, clock: Float, color: Color) {
    val path = layout.hexPath(0.98f)
    val pulse = 0.6f + 0.4f * sin(clock * 5f)
    translate(center.x, center.y) {
        drawPath(path, color.copy(alpha = 0.9f * pulse), style = Stroke(width = 2.5f))
        drawPath(path, color.copy(alpha = 0.12f * pulse), blendMode = BlendMode.Plus)
    }
}

/** Warp nach einem Galaxiewechsel: Lichtstreifen vom Garten nach außen und ein kurzer Blitz. [p] läuft von 0 bis 1. */
private fun DrawScope.drawWarp(p: Float, center: Offset, color: Color) {
    val fade = (1f - p) * (1f - p)
    drawRect(color.copy(alpha = 0.22f * fade), blendMode = BlendMode.Plus)
    val reach = size.maxDimension
    for (k in 0 until 14) {
        val a = k * 2f * PI.toFloat() / 14f + seedOf(Hex(k, 3)) * 0.4f
        val dir = Offset(cos(a), sin(a))
        val from = center + dir * reach * (0.05f + 0.6f * p)
        val to = center + dir * reach * (0.25f + 0.9f * p)
        drawLine(
            Brush.linearGradient(listOf(Color.Transparent, color.copy(alpha = 0.5f * (1f - p))), from, to),
            from, to, strokeWidth = 3f * density, blendMode = BlendMode.Plus,
        )
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
            ConstellationKind.TRIO, ConstellationKind.RED_THREAD, ConstellationKind.RAINBOW,
            ConstellationKind.LADDER, ConstellationKind.KILONOVA ->
                for (i in 0 until m.size - 1) add(m[i], m[i + 1], c.kind)
            ConstellationKind.TRIANGULUM -> {
                add(m[0], m[1], c.kind); add(m[1], m[2], c.kind); add(m[2], m[0], c.kind)
            }
            ConstellationKind.CROWN, ConstellationKind.SUN_CROWN, ConstellationKind.EVENT_HORIZON,
            ConstellationKind.QUASAR_THRONE -> {
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
    val scale = if (comet.meteor) 0.55f else 1f
    val tailLength = size.minDimension * 0.28f * scale
    val tail = head - dir * tailLength
    val core = if (comet.meteor) Color(0xFFFFE6C2) else Color(0xFFDDF6FF)
    val ion = if (comet.meteor) Color(0xFFFF8A4D) else Color(0xFF6FD8FF)
    drawLine(
        Brush.linearGradient(listOf(ion.copy(alpha = 0.0f), ion.copy(alpha = 0.55f)), tail, head),
        tail, head, strokeWidth = 14f * density * scale, blendMode = BlendMode.Plus,
    )
    drawLine(
        Brush.linearGradient(listOf(Color.Transparent, core.copy(alpha = 0.9f)), head - dir * tailLength * 0.6f, head),
        head - dir * tailLength * 0.6f, head, strokeWidth = 3f * density, blendMode = BlendMode.Plus,
    )
    val pulse = 1f + 0.15f * sin(clock * 9f)
    drawGlow(head, 46f * density * pulse * scale, ion, 0.7f)
    drawGlow(head, 18f * density * scale, Color.White, 1f)
    // Hinweisring: "tipp mich"
    drawCircle(core.copy(alpha = 0.35f + 0.25f * sin(clock * 6f)), 30f * density * pulse * scale.coerceAtLeast(0.8f), head, style = Stroke(width = 1.5f * density))
}

/** Bildschirmeffekte für laufende kosmische Ereignisse. */
private fun DrawScope.drawEventOverlay(event: ActiveEvent, clock: Float, gardenCenter: Offset, cell: Float) {
    val fadeIn = ((event.kind.duration - event.remaining) / 1.5).toFloat().coerceIn(0f, 1f)
    val fadeOut = (event.remaining / 1.5).toFloat().coerceIn(0f, 1f)
    val strength = min(fadeIn, fadeOut)
    if (strength <= 0f) return
    val color = Color.hsv(event.kind.hue, 0.7f, 1f)
    when (event.kind) {
        CosmicEvent.SOLAR_STORM -> {
            val flare = Offset(size.width * 0.15f, size.height * 0.1f)
            drawGlow(flare, size.maxDimension * (0.55f + 0.05f * sin(clock * 3f)), color, 0.35f * strength)
            for (k in 0 until 7) {
                val a = 0.2f + k * 0.18f + 0.05f * sin(clock * 2f + k)
                val end = Offset(flare.x + cos(a) * size.maxDimension, flare.y + sin(a) * size.maxDimension)
                drawLine(
                    Brush.linearGradient(listOf(color.copy(alpha = 0.25f * strength), Color.Transparent), flare, end),
                    flare, end, strokeWidth = 30f, blendMode = BlendMode.Plus,
                )
            }
        }
        CosmicEvent.GRAVITY_WAVE -> {
            for (k in 0 until 3) {
                val t = (clock * 0.5f + k / 3f) % 1f
                drawCircle(
                    color.copy(alpha = 0.35f * (1f - t) * strength),
                    radius = cell * (1f + t * 9f), center = gardenCenter,
                    style = Stroke(width = cell * 0.18f * (1f - t)), blendMode = BlendMode.Plus,
                )
            }
        }
        CosmicEvent.DARK_TIDE -> drawRect(
            Brush.radialGradient(
                0.35f to Color.Transparent,
                1f to Color(0xFF3A0F6E).copy(alpha = (0.55f + 0.15f * sin(clock * 2f)) * strength),
                center = Offset(size.width / 2f, size.height / 2f), radius = size.maxDimension * 0.7f,
            ),
        )
        CosmicEvent.STAR_RAIN -> {
            for (k in 0 until 28) {
                val seed = k * 0.618f
                val x = ((seed * 997f) % 1f) * size.width
                val y = ((clock * (0.15f + (k % 5) * 0.03f) + seed) % 1f) * size.height
                drawGlow(Offset(x, y), 10f * density, color, 0.8f * strength)
                drawLine(color.copy(alpha = 0.4f * strength), Offset(x, y - 26f * density), Offset(x, y), strokeWidth = 2f * density, blendMode = BlendMode.Plus)
            }
        }
        CosmicEvent.METEOR_SHOWER -> drawRect(
            Brush.verticalGradient(listOf(color.copy(alpha = 0.18f * strength), Color.Transparent), endY = size.height * 0.5f),
        )
    }
}

/** Farbe, in der ein Stern Staubpartikel abgibt. */
fun moteColor(state: GameState, star: Star): Color = when {
    star.whiteDwarf -> Color(0xFFDDE8FF)
    Balance.phaseOf(state, star) == LifePhase.GIANT -> Color(0xFFFFB27A)
    else -> starColors(star.type).glow
}
