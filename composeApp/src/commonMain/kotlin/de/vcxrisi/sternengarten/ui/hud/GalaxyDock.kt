package de.vcxrisi.sternengarten.ui.hud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.vcxrisi.sternengarten.game.engine.UnlockStatus
import de.vcxrisi.sternengarten.game.model.GalaxyKind
import de.vcxrisi.sternengarten.game.model.GameState
import de.vcxrisi.sternengarten.game.model.runKinds
import de.vcxrisi.sternengarten.game.model.runOf
import de.vcxrisi.sternengarten.ui.GameController
import de.vcxrisi.sternengarten.ui.Sheet
import de.vcxrisi.sternengarten.ui.theme.Palette
import de.vcxrisi.sternengarten.ui.theme.Type
import de.vcxrisi.sternengarten.ui.theme.formatDurationShort
import de.vcxrisi.sternengarten.ui.theme.galaxyColor
import kotlin.math.sin

/** Zustand einer Galaxie im Dock. */
enum class DockStatus { ACTIVE, RUNNING, CHOOSING_LAW, UNLOCKING, LOCKED }

/** Ein Platz im Galaxie-Dock. [badge]: abholbare Ziele; [ready]: die gesperrte Galaxie lässt sich erschließen. */
data class DockSlot(
    val kind: GalaxyKind,
    val status: DockStatus,
    val badge: Int = 0,
    val ready: Boolean = false,
    val startedAtMs: Long = 0,
    val readyAtMs: Long = 0,
)

/** Das Dock erscheint, sobald es mehr als eine Galaxie geben kann – vorher würde es neue Spieler nur ablenken. */
fun dockVisible(state: GameState): Boolean =
    state.parked.isNotEmpty() || state.unlock != null || state.stats.bigBangs > 0 || state.darkMatter > 0.0

/** Alle Galaxien, dazu die entstehende oder – als Ausblick – die nächste gesperrte. */
fun dockSlots(controller: GameController): List<DockSlot> {
    val state = controller.state
    val slots = state.runKinds().map { kind ->
        val run = state.runOf(kind)
        DockSlot(
            kind = kind,
            status = when {
                kind == state.activeGalaxy -> DockStatus.ACTIVE
                run?.lawChoices?.isNotEmpty() == true -> DockStatus.CHOOSING_LAW
                else -> DockStatus.RUNNING
            },
            badge = if (kind == state.activeGalaxy) 0 else controller.claimableByGalaxy[kind] ?: 0,
        )
    }
    val unlock = state.unlock
    val next = controller.nextUnlockable()
    val extra = when {
        unlock != null -> DockSlot(unlock.kind, DockStatus.UNLOCKING, startedAtMs = unlock.startedAtMs, readyAtMs = unlock.readyAtMs)
        next != null -> DockSlot(next, DockStatus.LOCKED, ready = controller.unlockStatus() == UnlockStatus.READY)
        else -> null
    }
    return if (extra != null) slots + extra else slots
}

/** Kompakte Orb-Leiste unter dem Staub-Panel: antippen wechselt die Galaxie oder öffnet die Übersicht. */
@Composable
fun GalaxyDock(controller: GameController, time: Float, modifier: Modifier = Modifier) {
    val slots = dockSlots(controller)
    val openOverview = { controller.sheet = Sheet.GALAXIES }
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (slot in slots) {
            val color = galaxyColor(slot.kind)
            val onTap = when (slot.status) {
                DockStatus.RUNNING, DockStatus.CHOOSING_LAW -> { -> controller.switchGalaxy(slot.kind) }
                else -> openOverview
            }
            Column(
                Modifier
                    .widthIn(min = 38.dp)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    when (slot.status) {
                        DockStatus.ACTIVE -> GalaxyOrb(slot.kind, time, 34.dp, ring = 1f, glow = true)
                        DockStatus.RUNNING -> GalaxyOrb(slot.kind, time, 30.dp, ring = 0.45f)
                        DockStatus.CHOOSING_LAW -> GalaxyOrb(slot.kind, time, 30.dp, ring = 0.4f + 0.4f * sin(time * 4f))
                        DockStatus.UNLOCKING -> {
                            val span = (slot.readyAtMs - slot.startedAtMs).coerceAtLeast(1L)
                            val progress = ((controller.nowMs - slot.startedAtMs).toFloat() / span).coerceIn(0f, 1f)
                            GalaxyOrb(slot.kind, time, 30.dp, progress = progress, dim = true)
                        }
                        DockStatus.LOCKED -> GalaxyOrb(
                            slot.kind, time, 30.dp,
                            ring = if (slot.ready) 0.4f + 0.3f * sin(time * 3f) else 0f, dim = true, locked = true,
                        )
                    }
                    if (slot.badge > 0) Badge(slot.badge, Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-2).dp))
                }
                val (caption, captionColor) = when (slot.status) {
                    DockStatus.ACTIVE -> slot.kind.shortName to color
                    DockStatus.RUNNING -> slot.kind.shortName to Palette.TextDim
                    DockStatus.CHOOSING_LAW -> "Wählen" to Palette.Boost
                    DockStatus.UNLOCKING -> formatDurationShort((slot.readyAtMs - controller.nowMs) / 1000.0) to color
                    DockStatus.LOCKED -> if (slot.ready) "Neu!" to Palette.Success else "Neu" to Palette.TextFaint
                }
                Txt(caption, Type.Small.copy(fontSize = 9.sp), color = captionColor, maxLines = 1)
            }
        }
    }
}

/**
 * Symbol einer Galaxieart: Leuchten in ihrer Farbe, zwei kreisende Spiralarme und ein heller Kern.
 * [ring]: Deckkraft des Rings (0 = keiner); [progress]: Fortschrittsbogen einer Erschließung;
 * [dim]: noch nicht spielbar; [locked]: mit „+“ als Versprechen. Zeichnet nur Kreise und Bögen.
 */
@Composable
fun GalaxyOrb(
    kind: GalaxyKind,
    time: Float,
    size: Dp,
    modifier: Modifier = Modifier,
    ring: Float = 0f,
    glow: Boolean = false,
    progress: Float? = null,
    dim: Boolean = false,
    locked: Boolean = false,
) {
    val color = galaxyColor(kind)
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val alpha = if (dim) 0.4f else 1f
        if (glow) drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent), center, r * 1.4f), r * 1.4f, center)
        drawCircle(
            Brush.radialGradient(
                0f to color.copy(alpha = 0.85f * alpha),
                0.45f to color.copy(alpha = 0.35f * alpha),
                1f to Color.Transparent,
                center = center, radius = r * 0.92f,
            ),
            r * 0.92f, center,
        )
        // Zwei Spiralarme, jede Art dreht mit eigener Phase.
        val arm = r * 0.55f
        val armStroke = Stroke(width = 1.5f * density)
        val spin = time * 25f + kind.ordinal * 47f
        for (k in 0 until 2) {
            drawArc(
                Color.White.copy(alpha = 0.55f * alpha), spin + k * 180f, 110f, false,
                topLeft = Offset(center.x - arm, center.y - arm), size = Size(arm * 2f, arm * 2f), style = armStroke,
            )
        }
        drawCircle(Color.White.copy(alpha = 0.95f * alpha), r * 0.18f, center)
        val ringRadius = r - 1.5f * density
        if (progress != null) {
            drawCircle(
                Palette.TextFaint.copy(alpha = 0.6f), ringRadius, center,
                style = Stroke(width = 1.5f * density, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f * density, 3f * density))),
            )
            drawArc(
                color, -90f, 360f * progress.coerceIn(0f, 1f), false,
                topLeft = Offset(center.x - ringRadius, center.y - ringRadius), size = Size(ringRadius * 2f, ringRadius * 2f),
                style = Stroke(width = 2.5f * density),
            )
        } else if (ring > 0f) {
            drawCircle(color.copy(alpha = ring.coerceIn(0f, 1f)), ringRadius, center, style = Stroke(width = (if (glow) 2f else 1f) * density))
        }
        if (locked) {
            val plus = r * 0.32f
            val c = Palette.Text.copy(alpha = 0.8f)
            drawLine(c, Offset(center.x - plus, center.y), Offset(center.x + plus, center.y), strokeWidth = 1.5f * density)
            drawLine(c, Offset(center.x, center.y - plus), Offset(center.x, center.y + plus), strokeWidth = 1.5f * density)
        }
    }
}
