package de.vcxrisi.sternengarten.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import de.vcxrisi.sternengarten.game.engine.BoardAnalysis
import de.vcxrisi.sternengarten.game.engine.GameEvent
import de.vcxrisi.sternengarten.game.model.GameState
import de.vcxrisi.sternengarten.game.model.StarType
import de.vcxrisi.sternengarten.ui.GameController
import de.vcxrisi.sternengarten.ui.Sheet
import de.vcxrisi.sternengarten.ui.fx.ParticleSystem
import de.vcxrisi.sternengarten.ui.hud.ActionBar
import de.vcxrisi.sternengarten.ui.hud.BigBangSheet
import de.vcxrisi.sternengarten.ui.hud.BuildBar
import de.vcxrisi.sternengarten.ui.hud.CatalogSheet
import de.vcxrisi.sternengarten.ui.hud.FirstStepsHint
import de.vcxrisi.sternengarten.ui.hud.GalaxySelectScreen
import de.vcxrisi.sternengarten.ui.hud.OfflineDialog
import de.vcxrisi.sternengarten.ui.hud.ResearchSheet
import de.vcxrisi.sternengarten.ui.hud.StarInfoPanel
import de.vcxrisi.sternengarten.ui.hud.ToastStack
import de.vcxrisi.sternengarten.ui.hud.TopBar
import de.vcxrisi.sternengarten.ui.render.Camera
import de.vcxrisi.sternengarten.ui.render.GardenView
import de.vcxrisi.sternengarten.ui.render.HexLayout
import de.vcxrisi.sternengarten.ui.render.Starfield
import de.vcxrisi.sternengarten.ui.render.cometPosition
import de.vcxrisi.sternengarten.ui.render.moteColor
import de.vcxrisi.sternengarten.ui.theme.Palette
import de.vcxrisi.sternengarten.ui.theme.starColors
import kotlin.random.Random

/** Merkt sich, ab welcher Animationszeit der aktuelle Komet sichtbar ist – für flüssige Bewegung. */
private class CometClock {
    var id = -1L
    var start = 0f
    var lastScreen = Offset.Zero

    fun progress(state: GameState, clock: Float): Float {
        val comet = state.comet ?: return 0f
        if (comet.id != id) {
            id = comet.id
            start = clock - comet.elapsed.toFloat()
        }
        return (clock - start) / comet.duration.toFloat()
    }
}

@Composable
fun GameScreen(controller: GameController) {
    val density = LocalDensity.current
    val layout = remember(density) { HexLayout(with(density) { 36.dp.toPx() }) }
    val camera = remember { Camera() }
    val starfield = remember { Starfield() }
    val particles = remember { ParticleSystem() }
    val cometClock = remember { CometClock() }
    val haptics = LocalHapticFeedback.current
    var viewport by remember { mutableStateOf(Size.Zero) }

    // Spielschleife: ein Schritt pro Bildschirm-Frame.
    LaunchedEffect(controller) {
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.1f)
                last = now
                controller.frame(dt)
                particles.update(dt)
                camera.shake = (camera.shake - dt * 28f).coerceAtLeast(0f)
                emitAmbient(controller.state, controller.analysis, layout, particles, dt)
            }
        }
    }

    // Ereignisse in Effekte übersetzen.
    DisposableEffect(controller, layout) {
        controller.onEvent = { event ->
            val unit = layout.size
            when (event) {
                is GameEvent.Planted -> {
                    val c = layout.center(event.hex)
                    val color = starColors(event.type).glow
                    particles.burst(c, 28, unit * 3f, unit * 0.07f, color, 0.9f)
                    particles.shockwave(c, unit * 1.3f, color, 0.6f)
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                is GameEvent.LeveledUp -> {
                    val c = layout.center(event.hex)
                    particles.burst(c, 18, unit * 2.2f, unit * 0.06f, Palette.Stardust, 0.8f)
                    particles.shockwave(c, unit * 0.9f, Palette.Stardust, 0.45f)
                }
                is GameEvent.Removed -> particles.burst(layout.center(event.hex), 20, unit * 1.6f, unit * 0.06f, Color(0xFF9A93B8), 0.8f)
                is GameEvent.Supernova -> {
                    val c = layout.center(event.hex)
                    particles.burst(c, 110, unit * 7f, unit * 0.06f, Color(0xFFCFE4FF), 1.6f)
                    particles.burst(c, 60, unit * 4.5f, unit * 0.055f, Color(0xFFFF9A3D), 1.4f)
                    particles.burst(c, 30, unit * 2.5f, unit * 0.05f, Palette.Elements, 2.0f)
                    particles.shockwave(c, unit * 4.2f, Color(0xFF9EC9FF), 1.3f)
                    particles.shockwave(c, unit * 2.4f, Palette.Elements, 0.9f)
                    camera.shake = 16f
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                is GameEvent.BecameWhiteDwarf -> particles.shockwave(layout.center(event.hex), unit * 1.6f, Color(0xFFBFD8FF), 0.9f)
                is GameEvent.BlackHoleReleased -> {
                    val c = layout.center(event.hex)
                    particles.burst(c, 140, unit * 6f, unit * 0.055f, Color(0xFFFFD27A), 1.5f)
                    particles.shockwave(c, unit * 3.5f, Color(0xFFFF9A3D), 1.1f)
                    camera.shake = 10f
                }
                is GameEvent.Discovered -> {
                    val members = controller.analysis.constellations.firstOrNull { it.kind == event.kind }?.members.orEmpty()
                    val color = Color.hsv(event.kind.hue, 0.55f, 1f)
                    for (m in members) {
                        particles.burst(layout.center(m), 16, unit * 2f, unit * 0.06f, color, 1.2f)
                        particles.shockwave(layout.center(m), unit * 1.1f, color, 0.8f)
                    }
                }
                is GameEvent.CometStardust, is GameEvent.CometBoost -> {
                    if (viewport != Size.Zero) {
                        val world = camera.toWorld(cometClock.lastScreen, viewport)
                        particles.burst(world, 90, unit * 5f / camera.zoom, unit * 0.08f / camera.zoom, Color(0xFF9EE8FF), 1.3f)
                        particles.shockwave(world, unit * 2.5f / camera.zoom, Color(0xFF6FD8FF), 0.9f)
                    }
                }
                is GameEvent.Unlocked -> Unit
            }
        }
        onDispose { controller.onEvent = {} }
    }

    val state = controller.state
    val clock = controller.clock
    val cometProgress = cometClock.progress(state, clock)
    state.comet?.let {
        val p = cometPosition(it, cometProgress)
        cometClock.lastScreen = Offset(p.x * viewport.width, p.y * viewport.height)
    }

    Box(Modifier.fillMaxSize().onSizeChanged { viewport = it.toSize() }) {
        GardenView(
            state = state,
            analysis = controller.analysis,
            selectedHex = controller.selectedHex,
            camera = camera,
            layout = layout,
            starfield = starfield,
            particles = particles,
            clock = clock,
            cometProgress = cometProgress,
            onTapCell = controller::tapCell,
            onTapComet = controller::catchComet,
        )

        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TopBar(state, controller.analysis, Modifier.padding(12.dp))
            ToastStack(controller.toasts, clock, Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            Spacer(Modifier.weight(1f))
            FirstStepsHint(state, Modifier.padding(12.dp))
            controller.selectedHex?.let { StarInfoPanel(controller, it, clock, Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) }
            BuildBar(controller, clock)
            Spacer(Modifier.height(8.dp))
            ActionBar(controller)
            Spacer(Modifier.height(10.dp))
        }

        when (controller.sheet) {
            Sheet.RESEARCH -> ResearchSheet(controller)
            Sheet.CATALOG -> CatalogSheet(controller)
            Sheet.BIG_BANG -> BigBangSheet(controller)
            Sheet.NONE -> Unit
        }

        controller.offlineReport?.let { OfflineDialog(it, controller::dismissOfflineReport) }

        if (state.lawChoices.isNotEmpty()) GalaxySelectScreen(controller, clock)
    }
}

/** Sanfter, ständiger Staubfluss aus allen Sternen und in Schwarze Löcher. */
private fun emitAmbient(state: GameState, analysis: BoardAnalysis, layout: HexLayout, particles: ParticleSystem, dt: Float) {
    val unit = layout.size
    for ((hex, star) in state.stars) {
        if (star.type == StarType.BLACK_HOLE) continue
        if (Random.nextFloat() < dt * 1.2f) {
            particles.mote(layout.center(hex), unit * 0.25f, moteColor(state, star), unit * 0.04f)
        }
    }
    for (hex in analysis.blackHoleInflow.keys) {
        if (Random.nextFloat() < dt * 10f) {
            particles.infall(layout.center(hex), unit * 1.5f, Color(0xFFFFB86B), unit * 0.05f)
        }
    }
}
