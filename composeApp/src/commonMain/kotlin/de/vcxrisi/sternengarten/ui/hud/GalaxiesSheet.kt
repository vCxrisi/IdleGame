package de.vcxrisi.sternengarten.ui.hud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.vcxrisi.sternengarten.game.engine.Balance
import de.vcxrisi.sternengarten.game.engine.BridgeBlock
import de.vcxrisi.sternengarten.game.engine.UnlockStatus
import de.vcxrisi.sternengarten.game.model.GalaxyKind
import de.vcxrisi.sternengarten.game.model.Hex
import de.vcxrisi.sternengarten.game.model.Star
import de.vcxrisi.sternengarten.game.model.StarBridge
import de.vcxrisi.sternengarten.game.model.projected
import de.vcxrisi.sternengarten.game.model.runKinds
import de.vcxrisi.sternengarten.game.model.runOf
import de.vcxrisi.sternengarten.ui.GalaxiesTab
import de.vcxrisi.sternengarten.ui.GameController
import de.vcxrisi.sternengarten.ui.Sheet
import de.vcxrisi.sternengarten.ui.render.drawNebula
import de.vcxrisi.sternengarten.ui.theme.Palette
import de.vcxrisi.sternengarten.ui.theme.Type
import de.vcxrisi.sternengarten.ui.theme.formatDecimal
import de.vcxrisi.sternengarten.ui.theme.formatDuration
import de.vcxrisi.sternengarten.ui.theme.formatNumber
import de.vcxrisi.sternengarten.ui.theme.galaxyColor
import de.vcxrisi.sternengarten.ui.theme.starColors
import kotlin.math.min
import kotlin.math.sqrt

/** Übersicht aller Galaxien: Karten zum Wechseln, die Erschließung der nächsten und die Sternenbrücken. */
@Composable
fun BoxScope.GalaxiesSheet(controller: GameController, time: Float) {
    val state = controller.state
    BottomSheet(
        "Galaxien", Palette.DarkMatter, { controller.sheet = Sheet.NONE },
        header = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt("Dunkle Materie", Type.Small)
                    Spacer(Modifier.width(6.dp))
                    Txt(formatNumber(state.darkMatter), Type.Label, color = Palette.DarkMatter)
                    Spacer(Modifier.weight(1f))
                    Txt("${state.runKinds().size}/${GalaxyKind.entries.size} Galaxien", Type.Small)
                }
                TabRow(GalaxiesTab.entries, controller.galaxiesTab, { it.title }, Palette.DarkMatter, { controller.galaxiesTab = it })
            }
        },
    ) {
        when (controller.galaxiesTab) {
            GalaxiesTab.OVERVIEW -> OverviewTab(controller, time)
            GalaxiesTab.BRIDGES -> BridgesTab(controller, time)
        }
    }
}

// ------------------------------------------------------------ Übersicht

@Composable
private fun OverviewTab(controller: GameController, time: Float) {
    val state = controller.state
    Txt("Alle erschlossenen Galaxien wachsen gleichzeitig. Jede hat ihren eigenen Staub – Dunkle Materie teilen sie sich.", Type.Body)
    // Was sich gerade tun lässt, steht oben – sonst die Karte der nächsten Galaxie als Ausblick am Ende.
    val unlockFirst = state.unlock != null || controller.unlockStatus() == UnlockStatus.READY
    if (unlockFirst) UnlockCard(controller, time)
    for (kind in state.runKinds()) GalaxyCard(controller, kind, time)
    if (!unlockFirst) UnlockCard(controller, time)
}

@Composable
private fun GalaxyCard(controller: GameController, kind: GalaxyKind, time: Float) {
    val state = controller.state
    val view = state.projected(kind) ?: return
    val active = kind == state.activeGalaxy
    val color = galaxyColor(kind)
    val paused = view.lawChoices.isNotEmpty()
    val gain = controller.previewGain(kind)
    val rate = if (active) controller.analysis.totalRate * state.law.onlineMult else controller.galaxyRates[kind] ?: 0.0
    val inflow = controller.bridgeInflow[kind] ?: 0.0
    val claimable = if (active) {
        view.galaxyGoals.count { !it.claimed && controller.progression.goalProgress(view, controller.analysis, it) >= it.target }
    } else {
        controller.claimableByGalaxy[kind] ?: 0
    }
    val shape = RoundedCornerShape(20.dp)
    GlassPanel(
        Modifier.fillMaxWidth().then(if (active) Modifier.border(2.dp, color, shape) else Modifier),
        shape = shape,
        tint = color.copy(alpha = 0.7f),
        padding = PaddingValues(0.dp),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(58.dp)) {
                Canvas(Modifier.matchParentSize()) { drawNebula(time, kind.hue, Offset.Zero) }
                Row(
                    Modifier.fillMaxWidth().align(Alignment.CenterStart).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GalaxyOrb(kind, time, 32.dp, ring = if (active) 1f else 0.45f, glow = active)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Txt(view.galaxyName, Type.Label, color = Palette.Text, maxLines = 1)
                        Txt(
                            kind.displayName + " · " + (if (view.galaxyNumber == 0) "Naturgesetze offen" else view.law.displayName) +
                                if (active) " · aktiv" else "",
                            Type.Small, color = if (active) color else Palette.TextDim, maxLines = 1,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    GalaxyGlyph(view.ownedFields, view.stars, color, Modifier.size(46.dp))
                }
            }
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniStat(kind.dustName.uppercase(), formatNumber(view.stardust), color, Modifier.weight(1f))
                    MiniStat("PRO SEKUNDE", if (paused) "–" else "+${formatNumber(rate)}/s", Palette.Text, Modifier.weight(1f))
                    MiniStat(
                        "URKNALL",
                        if (gain >= 1) "+${formatNumber(gain)} DM" else "ab ${formatNumber(Balance.runStardustForDarkMatter(view, 1.0))}",
                        if (gain >= 1) Palette.DarkMatter else Palette.TextFaint,
                        Modifier.weight(1f),
                    )
                }
                if (paused) Txt("pausiert – Naturgesetze wählen", Type.Label, color = Palette.Boost)
                Txt(
                    "${view.ownedFields.size} Felder · ${view.stars.size} Sterne · Zyklus ${view.galaxyNumber} · " +
                        "Ziele ${view.galaxyGoals.count { it.claimed }}/${view.galaxyGoals.size}" + bridgeNote(state.bridges, kind),
                    Type.Small, maxLines = 2,
                )
                if (inflow > 0.0) Txt("+${formatNumber(inflow)}/s ${kind.dustName} über Brücke", Type.Small, color = Palette.Accent)
                if (claimable > 0) Txt(if (claimable == 1) "1 Ziel bereit zum Abholen" else "$claimable Ziele bereit zum Abholen", Type.Label, color = Palette.Success)
                when {
                    paused -> GlowButton("Gesetze wählen", { controller.switchGalaxy(kind) }, Modifier.fillMaxWidth(), color = Palette.Boost)
                    active -> GlowButton(
                        "Urknall", { controller.sheet = Sheet.BIG_BANG }, Modifier.fillMaxWidth(),
                        color = Palette.DarkMatter, enabled = gain >= 1,
                        subtitle = if (gain >= 1) "+${formatNumber(gain)} Dunkle Materie" else null,
                    )
                    else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlowButton("Wechseln", { controller.switchGalaxy(kind) }, Modifier.weight(1f), color = color)
                        GlowButton(
                            "Urknall",
                            {
                                controller.switchGalaxy(kind)
                                controller.sheet = Sheet.BIG_BANG
                            },
                            Modifier.weight(1f),
                            color = Palette.DarkMatter, enabled = gain >= 1,
                            subtitle = if (gain >= 1) "+${formatNumber(gain)}" else null,
                        )
                    }
                }
            }
        }
    }
}

/** Ausgehende Brücke einer Galaxie für die Infozeile der Karte. */
private fun bridgeNote(bridges: List<StarBridge>, kind: GalaxyKind): String {
    val bridge = bridges.firstOrNull { it.from == kind } ?: return ""
    return if (bridge.built) " · Brücke → ${bridge.to.shortName}" else " · Brücke → ${bridge.to.shortName} im Bau"
}

@Composable
private fun MiniStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Txt(label, Type.Small.copy(fontSize = 9.sp), color = Palette.TextFaint, maxLines = 1)
        Txt(value, Type.Label, color = color, maxLines = 1)
    }
}

/** Mini-Karte einer Galaxie: Felder als blasse Punkte, Sterne in ihrer Farbe. */
@Composable
private fun GalaxyGlyph(fields: Set<Hex>, stars: Map<Hex, Star>, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val sqrt3 = sqrt(3f)
        val radius = (fields.maxOfOrNull { it.length() } ?: 0).coerceAtLeast(2)
        val unit = size.minDimension / (2f * (radius + 0.6f) * sqrt3)
        fun p(h: Hex) = Offset(center.x + unit * sqrt3 * (h.q + h.r / 2f), center.y + unit * 1.5f * h.r)
        for (h in fields) drawCircle(color.copy(alpha = 0.16f), unit * 0.8f, p(h))
        for ((h, star) in stars) {
            drawCircle(starColors(star.type).glow.copy(alpha = 0.9f), unit * 0.7f, p(h))
            drawCircle(Color.White, unit * 0.28f, p(h))
        }
    }
}

/** „×2,5“ oder „×10“ – ganze Faktoren ohne Komma. */
private fun formatFactor(value: Double): String =
    "×" + if (value == kotlin.math.floor(value)) formatDecimal(value, 0) else formatDecimal(value, 1)

@Composable
private fun UnlockCard(controller: GameController, time: Float) {
    val state = controller.state
    val unlock = state.unlock
    val kind = unlock?.kind ?: controller.nextUnlockable()
    if (kind == null) {
        Txt("Alle fünf Galaxien sind erschlossen.", Type.Body, color = Palette.TextFaint)
        return
    }
    val color = galaxyColor(kind)
    val status = controller.unlockStatus()
    val previous = GalaxyKind.entries.getOrNull(kind.ordinal - 1)
    val span = unlock?.let { (it.readyAtMs - it.startedAtMs).coerceAtLeast(1L) } ?: 1L
    val progress = unlock?.let { ((controller.nowMs - it.startedAtMs).toFloat() / span).coerceIn(0f, 1f) }
    GlassPanel(Modifier.fillMaxWidth(), tint = color.copy(alpha = 0.7f), padding = PaddingValues(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GalaxyOrb(kind, time, 40.dp, progress = progress, dim = true, locked = unlock == null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Txt("Neue Galaxie: ${kind.displayName}", Type.Label, color = color)
                    Txt("Sammelt ${kind.dustName} · Urknall-Ertrag ${formatFactor(kind.darkMatterMult)}", Type.Small)
                }
            }
            Txt(kind.description, Type.Small)
            if (kind.exclusiveStar != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MysteryIcon(time, size = 28.dp)
                    Spacer(Modifier.width(6.dp))
                    Txt("Eine eigene Sternart wartet – sie wächst nur dort.", Type.Small, color = Palette.TextDim)
                }
            }
            if (unlock != null) {
                val remainingMs = (unlock.readyAtMs - controller.nowMs).coerceAtLeast(0L)
                val cost = Balance.timerSkipCost(min(remainingMs, span))
                Txt("Entsteht … noch ${formatDuration(remainingMs / 1000.0)}", Type.Label, color = color)
                GlowBar(progress ?: 0f, color, Modifier.fillMaxWidth().height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt("Die Erschließung läuft auch weiter, während die App geschlossen ist.", Type.Small, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                    ConfirmCrystalButton("Sofort fertig", cost, state.crystals >= cost, controller.clock, controller::skipGalaxyUnlock)
                }
            } else {
                Txt(
                    "Kosten: ${formatNumber(kind.unlockDarkMatter)} Dunkle Materie · Dauer: ${formatDuration(Balance.unlockMillis(state, kind) / 1000.0)}",
                    Type.Body,
                )
                GlowButton(
                    "Erschließen", controller::startGalaxyUnlock, Modifier.fillMaxWidth(),
                    color = Palette.DarkMatter, enabled = status == UnlockStatus.READY,
                    subtitle = when (status) {
                        UnlockStatus.READY -> "${formatNumber(kind.unlockDarkMatter)} DM · ${formatDuration(Balance.unlockMillis(state, kind) / 1000.0)}"
                        UnlockStatus.NEEDS_DARK_MATTER -> "Dir fehlen ${formatNumber(kind.unlockDarkMatter - state.darkMatter)} DM"
                        else -> null
                    },
                )
                val blocked = when (status) {
                    UnlockStatus.NEEDS_COLLAPSE -> "Voraussetzung: ein Urknall in der ${previous?.displayName.orEmpty()}."
                    UnlockStatus.NEEDS_LAW_CHOICE -> "Wähle zuerst die Naturgesetze der ${previous?.displayName.orEmpty()}."
                    else -> null
                }
                if (blocked != null) Txt(blocked, Type.Small, color = Palette.Boost)
                Txt("Ausgegebene Dunkle Materie zählt nicht mehr für den Produktionsbonus.", Type.Small, color = Palette.TextFaint)
            }
        }
    }
}

// ------------------------------------------------------------ Sternenbrücken

@Composable
private fun BridgesTab(controller: GameController, time: Float) {
    val state = controller.state
    val kinds = state.runKinds()
    Txt(
        "Sternenbrücken verbinden zwei Galaxien: ein Teil der Produktion der Quelle fließt als Staub der Zielgalaxie hinüber. " +
            "Brückenstaub zählt nicht für den Urknall.",
        Type.Body,
    )
    if (kinds.size < 2) {
        Txt("Erschließe eine zweite Galaxie, um Brücken zu bauen.", Type.Label, color = Palette.TextFaint)
        return
    }
    Txt(
        "Jede Brücke trägt ${formatDecimal(Balance.bridgeShare(state) * 100, 0)} % der Quelle, " +
            "höchstens ${formatDecimal(Balance.bridgeCap(state) * 100, 0)} % der eigenen Produktion des Ziels.",
        Type.Small, color = Palette.TextFaint,
    )
    for (bridge in state.bridges) BridgeRow(controller, bridge, time)

    SectionTitle("Neue Brücke", Palette.Accent)
    var from by remember { mutableStateOf(state.activeGalaxy) }
    var to by remember {
        mutableStateOf(kinds.firstOrNull { it != state.activeGalaxy && state.bridges.none { b -> b.to == it } } ?: kinds.first { it != state.activeGalaxy })
    }
    // Eine Galaxie könnte inzwischen fehlen (etwa nach einem Neustart der Übersicht) – dann neu wählen.
    if (from !in kinds) from = kinds.first()
    if (to !in kinds) to = kinds.first { it != from }
    Txt("Von", Type.Small)
    TabRow(kinds, from, { it.shortName }, Palette.Accent, { from = it })
    Txt("Nach", Type.Small)
    TabRow(kinds.filter { it != from }, to, { it.shortName }, Palette.Accent, { to = it })
    val block = controller.bridgeBlock(from, to)
    GlowButton(
        "Brücke bauen", { controller.buildBridge(from, to) }, Modifier.fillMaxWidth(),
        color = Palette.Accent, enabled = block == BridgeBlock.NONE,
        subtitle = "${formatNumber(Balance.bridgeCost(state))} Dunkle Materie · ${formatDuration(Balance.bridgeMillis(state) / 1000.0)}",
    )
    val message = when (block) {
        BridgeBlock.NONE -> null
        BridgeBlock.SAME_GALAXY -> "Wähle zwei verschiedene Galaxien."
        BridgeBlock.TARGET_TAKEN -> "${to.displayName} hat schon eine Brücke."
        BridgeBlock.UNDER_CONSTRUCTION -> "Es wird schon eine Brücke gebaut."
        BridgeBlock.DARK_MATTER -> "Zu wenig Dunkle Materie."
        BridgeBlock.MISSING_GALAXY -> "Erschließe eine zweite Galaxie, um Brücken zu bauen."
    }
    if (message != null) Txt(message, Type.Small, color = Palette.Danger)
}

@Composable
private fun BridgeRow(controller: GameController, bridge: StarBridge, time: Float) {
    val state = controller.state
    val paused = state.runOf(bridge.to)?.lawChoices?.isNotEmpty() == true
    GlassPanel(Modifier.fillMaxWidth(), tint = galaxyColor(bridge.to).copy(alpha = 0.5f), padding = PaddingValues(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GalaxyOrb(bridge.from, time, 24.dp)
            Txt("→", Type.Label, color = Palette.TextDim, modifier = Modifier.padding(horizontal = 2.dp))
            GalaxyOrb(bridge.to, time, 24.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Txt("${bridge.from.shortName} → ${bridge.to.shortName}", Type.Label, maxLines = 1)
                when {
                    !bridge.built -> {
                        val span = (bridge.readyAtMs - bridge.startedAtMs).coerceAtLeast(1L)
                        val remainingMs = (bridge.readyAtMs - controller.nowMs).coerceAtLeast(0L)
                        Txt("Im Bau · noch ${formatDuration(remainingMs / 1000.0)}", Type.Small, color = Palette.Accent, maxLines = 1)
                        GlowBar(((controller.nowMs - bridge.startedAtMs).toFloat() / span), Palette.Accent, Modifier.fillMaxWidth().height(4.dp))
                    }
                    paused -> Txt("pausiert – Naturgesetze wählen", Type.Small, color = Palette.Boost, maxLines = 1)
                    else -> Txt(
                        "Aktiv · +${formatNumber(controller.bridgeInflow[bridge.to] ?: 0.0)}/s ${bridge.to.dustName}",
                        Type.Small, color = galaxyColor(bridge.to), maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!bridge.built) {
                    val remainingMs = (bridge.readyAtMs - controller.nowMs).coerceAtLeast(0L)
                    val cost = Balance.timerSkipCost(min(remainingMs, bridge.readyAtMs - bridge.startedAtMs))
                    ConfirmCrystalButton("Sofort", cost, state.crystals >= cost, controller.clock, { controller.skipBridge(bridge.from, bridge.to) })
                }
                ConfirmButton("Abreißen", Palette.Danger, controller.clock, { controller.removeBridge(bridge.from, bridge.to) })
            }
        }
    }
}
