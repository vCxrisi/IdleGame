package de.vcxrisi.sternengarten.ui.hud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.vcxrisi.sternengarten.game.engine.Balance
import de.vcxrisi.sternengarten.game.engine.BoardAnalysis
import de.vcxrisi.sternengarten.game.engine.StarBreakdown
import de.vcxrisi.sternengarten.game.model.Achievement
import de.vcxrisi.sternengarten.game.model.GalaxyKind
import de.vcxrisi.sternengarten.game.model.GameState
import de.vcxrisi.sternengarten.game.model.Hex
import de.vcxrisi.sternengarten.game.model.LifePhase
import de.vcxrisi.sternengarten.game.model.StarFate
import de.vcxrisi.sternengarten.game.model.StarType
import de.vcxrisi.sternengarten.ui.GameController
import de.vcxrisi.sternengarten.ui.Sheet
import de.vcxrisi.sternengarten.ui.Toast
import de.vcxrisi.sternengarten.ui.render.StarVisual
import de.vcxrisi.sternengarten.ui.render.visualOf
import de.vcxrisi.sternengarten.ui.theme.Palette
import de.vcxrisi.sternengarten.ui.theme.Type
import de.vcxrisi.sternengarten.ui.theme.formatDecimal
import de.vcxrisi.sternengarten.ui.theme.formatDuration
import de.vcxrisi.sternengarten.ui.theme.formatNumber
import de.vcxrisi.sternengarten.ui.theme.formatPercent
import de.vcxrisi.sternengarten.ui.theme.galaxyColor
import de.vcxrisi.sternengarten.ui.theme.starColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Aktuelle Produktion pro Sekunde, wie sie beim Spieler ankommt. */
fun displayedRate(state: GameState, analysis: BoardAnalysis): Double =
    analysis.totalRate * state.law.onlineMult * (if (state.boostRemaining > 0) Balance.COMET_BOOST_MULT else 1.0)

/**
 * Kopfzeile: links der Staub der aktiven Galaxie mit dem Galaxie-Dock darunter, rechts Galaxie und Währungen.
 * Ohne [dock] (neue Spieler mit nur einer Galaxie) entfällt auch der Galaxie-Chip.
 */
@Composable
fun TopBar(
    state: GameState,
    analysis: BoardAnalysis,
    bridgeInflow: Double,
    onCrystals: () -> Unit,
    onGalaxies: () -> Unit,
    modifier: Modifier = Modifier,
    dock: (@Composable () -> Unit)? = null,
) {
    val kindColor = galaxyColor(state.activeGalaxy)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        // Die Chip-Spalte wird zuerst gemessen; das Dock scrollt, wenn der Platz knapp wird.
        Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GlassPanel(padding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), tint = kindColor.copy(alpha = 0.4f)) {
                Column {
                    Txt(state.activeGalaxy.dustName.uppercase(), Type.Small, color = kindColor.copy(alpha = 0.8f))
                    Txt(formatNumber(state.stardust), Type.Huge, color = Palette.Text, maxLines = 1)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Txt("+${formatNumber(displayedRate(state, analysis))}/s", Type.Label, color = kindColor, maxLines = 1)
                        if (state.boostRemaining > 0) {
                            Spacer(Modifier.width(8.dp))
                            Txt("×5 · ${state.boostRemaining.toInt()} s", Type.Small, color = Palette.Boost, maxLines = 1)
                        }
                    }
                    if (bridgeInflow > 0.0) {
                        Txt("+${formatNumber(bridgeInflow)}/s über Brücke", Type.Small, color = Palette.Accent, maxLines = 1)
                    }
                }
            }
            if (dock != null) dock()
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (dock != null) {
                GlassPanel(
                    Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onGalaxies),
                    shape = RoundedCornerShape(14.dp),
                    padding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    tint = kindColor.copy(alpha = 0.5f),
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Txt("${state.galaxyName} ▾", Type.Small, color = Palette.Text, maxLines = 1)
                        Txt(state.law.displayName, Type.Small, color = Color.hsv(state.law.hue, 0.45f, 1f), maxLines = 1)
                    }
                }
            }
            CurrencyChip(formatNumber(state.crystals.toDouble()), "Kristalle", Palette.Crystal, crystal = true, onClick = onCrystals)
            if (state.elements > 0 || state.supernovaCount > 0 || StarType.BLUE_GIANT in state.unlocked) {
                CurrencyChip(formatNumber(state.elements), "Elemente", Palette.Elements)
            }
            if (state.darkMatter > 0 || state.stats.bigBangs > 0 || state.parked.isNotEmpty()) {
                CurrencyChip(formatNumber(state.darkMatter), "Dunkle Mat.", Palette.DarkMatter)
            }
        }
    }
}

@Composable
fun BuildBar(controller: GameController, time: Float, modifier: Modifier = Modifier) {
    val state = controller.state
    Row(
        modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Exklusive Sternarten anderer Galaxien bleiben verborgen; sortiert nach Preis, damit sie sich einreihen.
        val types = StarType.entries
            .filter { it.exclusiveTo == null || it.exclusiveTo == state.activeGalaxy }
            .sortedBy { it.baseCost }
        val kindColor = galaxyColor(state.activeGalaxy)
        for (type in types) {
            val unlocked = type in state.unlocked
            val exclusive = type.exclusiveTo != null
            val cost = Balance.starCost(state, type)
            val selected = controller.selectedType == type
            val affordable = state.stardust >= cost
            val shape = RoundedCornerShape(16.dp)
            val glow = starColors(type).glow
            Box(
                Modifier
                    .width(86.dp)
                    .clip(shape)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                (if (selected) glow.copy(alpha = 0.28f) else Palette.Glass),
                                Palette.Glass.copy(alpha = 0.6f),
                            ),
                        ),
                    )
                    .border(
                        if (selected) 1.5.dp else 1.dp,
                        when {
                            selected -> glow.copy(alpha = 0.9f)
                            exclusive -> kindColor.copy(alpha = 0.5f)
                            else -> Palette.GlassBorder
                        },
                        shape,
                    )
                    .clickable(
                        enabled = unlocked,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        controller.selectedType = type
                        controller.selectedHex = null
                        controller.selectedField = null
                    }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Keine Alpha-Ebene um den Stern legen: additives Leuchten würde sonst als Rechteck sichtbar.
                    if (unlocked) StarIcon(StarVisual(type), time, size = 40.dp) else MysteryIcon(time, size = 40.dp)
                    Txt(
                        if (unlocked) type.displayName else "???", Type.Small,
                        color = if (unlocked) Palette.Text else Palette.TextFaint, align = TextAlign.Center, maxLines = 1,
                    )
                    Txt(
                        if (unlocked) formatNumber(cost) else "ab ${formatNumber(Balance.unlockThreshold(state, type))}",
                        Type.Label,
                        color = when {
                            !unlocked -> Palette.TextFaint
                            affordable -> kindColor
                            else -> Palette.Danger
                        },
                        maxLines = 1,
                    )
                }
                if (exclusive) {
                    Txt("★", Type.Small, color = kindColor, modifier = Modifier.align(Alignment.TopEnd).padding(end = 4.dp))
                }
            }
        }
    }
}

/** Anzahl abholbarer Belohnungen: Missionen, Galaxie-Ziele und Login-Bonus. */
fun claimableCount(controller: GameController): Int {
    val state = controller.state
    val missions = state.missions.count { !it.claimed && controller.progression.missionProgress(state, it) >= it.target }
    val goals = state.galaxyGoals.count { !it.claimed && controller.progression.goalProgress(state, controller.analysis, it) >= it.target }
    return missions + goals + (if (state.pendingLoginReward != null) 1 else 0)
}

@Composable
fun ActionBar(controller: GameController, modifier: Modifier = Modifier) {
    val state = controller.state
    val gain = Balance.darkMatterGain(state)
    val claimable = claimableCount(controller)
    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        GlowButton("Forschung", { controller.sheet = Sheet.RESEARCH }, Modifier.weight(1.2f), color = Palette.Elements, compact = true)
        Box(Modifier.weight(1f)) {
            GlowButton(
                "Ziele", { controller.sheet = Sheet.GOALS }, Modifier.fillMaxWidth(),
                color = Palette.Accent, compact = true, subtitle = "${state.achievements.size}/${Achievement.entries.size}",
            )
            if (claimable > 0) Badge(claimable, Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 2.dp))
        }
        GlowButton(
            "Shop", { controller.sheet = Sheet.SHOP }, Modifier.weight(1f),
            color = Palette.Crystal, compact = true, subtitle = when (state.capsules) {
                0 -> null
                1 -> "1 Kapsel"
                else -> "${state.capsules} Kapseln"
            },
        )
        GlowButton(
            "Urknall", { controller.sheet = Sheet.BIG_BANG }, Modifier.weight(1f),
            color = Palette.DarkMatter, compact = true, subtitle = if (gain >= 1) "+${formatNumber(gain)}" else null,
        )
    }
}

@Composable
fun StarInfoPanel(controller: GameController, hex: Hex, time: Float, modifier: Modifier = Modifier) {
    val state = controller.state
    val star = state.stars[hex] ?: return
    val breakdown = controller.analysis.breakdown[hex]
    val phase = Balance.phaseOf(state, star)
    val colors = starColors(star.type)
    val kindColor = galaxyColor(state.activeGalaxy)

    GlassPanel(modifier.widthIn(max = 520.dp).fillMaxWidth(), strong = true, tint = colors.glow.copy(alpha = 0.6f)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StarIcon(visualOf(state, star), time, size = 52.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Txt(star.type.displayName, Type.Title)
                    Txt(
                        if (star.type == StarType.BLACK_HOLE || star.type == StarType.NEBULA_NURSERY) phase.displayName
                        else "${phase.displayName} · Stufe ${star.level}",
                        Type.Small,
                    )
                }
                Txt("✕", Type.Title, color = Palette.TextDim, modifier = Modifier.clickable { controller.selectedHex = null }.padding(8.dp))
            }

            if (star.type == StarType.BLACK_HOLE) {
                val inflow = controller.analysis.blackHoleInflow[hex] ?: 0.0
                Txt("Sog: ${formatNumber(inflow * state.law.onlineMult)}/s · gespeichert: ${formatNumber(star.stored)}", Type.Label, color = Palette.Boost)
                val mult = Balance.blackHoleReleaseMultiplier(state)
                Txt("Freisetzen bringt ×${formatDecimal(mult, 1)}: ${formatNumber(star.stored * mult)}", Type.Body)
            } else if (breakdown != null) {
                Txt("+${formatNumber(breakdown.rate * state.law.onlineMult)} ${state.activeGalaxy.dustName}/s", Type.Label, color = kindColor)
                BonusChips(breakdown)
            }

            val lifespan = Balance.effectiveLifespan(state, star.type)
            val sheltered = controller.isSheltered(hex)
            if (lifespan != null && !star.whiteDwarf && sheltered) {
                Txt("Von einer Nebelwiege behütet – altert nicht.", Type.Small, color = starColors(StarType.NEBULA_NURSERY).glow)
            } else if (lifespan != null && !star.whiteDwarf) {
                val remaining = (lifespan - star.age) / Balance.agingMultiplier(state)
                val label = when (star.type.fate) {
                    StarFate.SUPERNOVA -> "Supernova in ${formatDuration(remaining)}"
                    StarFate.WHITE_DWARF -> "Weißer Zwerg in ${formatDuration(remaining)}"
                    StarFate.ETERNAL -> ""
                }
                Txt(label, Type.Small, color = if (phase == LifePhase.GIANT) Palette.Boost else Palette.TextDim)
                GlowBar((star.age / lifespan).toFloat(), if (phase == LifePhase.GIANT) Palette.Boost else colors.glow, Modifier.fillMaxWidth().height(6.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (star.type == StarType.NEBULA_NURSERY) {
                    Txt("Nebelwiegen lassen sich nicht verbessern.", Type.Small, modifier = Modifier.weight(1f))
                } else if (star.type == StarType.BLACK_HOLE) {
                    GlowButton(
                        "Freisetzen", { controller.releaseBlackHole(hex) }, Modifier.weight(1f),
                        color = Palette.Boost, enabled = star.stored > 0,
                    )
                } else {
                    val cost = Balance.levelUpCost(state, star)
                    GlowButton(
                        "Verbessern", { controller.levelUp(hex) }, Modifier.weight(1f),
                        color = kindColor, enabled = state.stardust >= cost, subtitle = formatNumber(cost),
                    )
                }
                GlowButton("Entfernen", { controller.remove(hex) }, color = Palette.Danger, subtitle = "50 % zurück")
            }

            if (controller.canLevel(star.type)) {
                // Mehrere Stufen auf einmal: jeder Knopf zeigt die Gesamtkosten.
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (count in BULK_LEVELS) {
                        val cost = Balance.levelUpCost(state, star, count)
                        GlowButton(
                            "+$count", { controller.levelUp(hex, count) }, Modifier.weight(1f),
                            color = kindColor, enabled = state.stardust >= cost, subtitle = formatNumber(cost), compact = true,
                        )
                    }
                    val affordable = Balance.maxAffordableLevels(state, star)
                    GlowButton(
                        "Max", { controller.levelUpMax(hex) }, Modifier.weight(1f),
                        color = Palette.Boost, enabled = affordable > 0, subtitle = if (affordable > 0) "+$affordable" else "–", compact = true,
                    )
                }
            }
        }
    }
}

/** Stufen-Pakete für das Mehrfach-Verbessern im Stern-Panel. */
private val BULK_LEVELS = listOf(10, 50, 100)

@Composable
private fun BonusChips(b: StarBreakdown) {
    val chips = buildList {
        if (b.phase != 1.0) add("Lebensphase ×${formatDecimal(b.phase, 1)}" to Palette.TextDim)
        if (b.levelBonus > 0) add("Neutronendruck +${b.levelBonus} Stufen" to Color(0xFF9FE7FF))
        if (b.aura > 0) add("Nachbarn ${formatPercent(b.aura)}" to Palette.Stardust)
        if (b.symmetry > 0) add("Symmetrie ${formatPercent(b.symmetry)}" to starColors(StarType.FROST_CRYSTAL).glow)
        if (b.variety > 0) add("Vielfalt ${formatPercent(b.variety)}" to starColors(StarType.AURORA_STAR).glow)
        if (b.edge > 0) add("Leere ${formatPercent(b.edge)}" to starColors(StarType.SHADOW_STAR).glow)
        if (b.group > 1.0) add("Glutnest ×${formatDecimal(b.group, 2)}" to starColors(StarType.EMBER_STAR).secondary)
        if (b.crowding < 1.0) add("Enge −${formatDecimal((1 - b.crowding) * 100, 0)} %" to Palette.Danger)
        if (b.pair > 1.0) add("Paar ×2" to Color(0xFFC08BFF))
        if (b.enrichment > 0) add("Gedüngt ${formatPercent(b.enrichment)}" to ENRICHED)
        if (b.constellation > 0) add("Sternbilder ${formatPercent(b.constellation)}" to Palette.Accent)
        if (b.drained > 0) add("Schwarzes Loch −50 %" to Palette.DarkMatter)
    }
    if (chips.isEmpty()) {
        Txt("Tipp: Nachbarn, Sternbilder und gedüngte Felder verstärken jeden Stern.", Type.Small)
        return
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((label, color) in chips) Chip(label, color)
    }
}

/** Farbe gedüngter Felder. */
private val ENRICHED = Color(0xFFFFC85A)

@Composable
private fun Chip(label: String, color: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) { Txt(label, Type.Small, color = color, maxLines = 1) }
}

/** Länger als so viele Sekunden Wartezeit wäre keine hilfreiche Schätzung mehr. */
private const val MAX_WAIT_SECONDS = 30 * 86_400.0

/** Angebot für ein Grenzfeld – erscheint an der Stelle des Stern-Panels. */
@Composable
fun FieldBuyPanel(controller: GameController, hex: Hex, time: Float, modifier: Modifier = Modifier) {
    val state = controller.state
    val kind = state.activeGalaxy
    val color = galaxyColor(kind)
    val dust = kind.dustName
    val cost = Balance.fieldCost(state)
    val affordable = state.stardust >= cost
    val enrichment = state.enrichment[hex] ?: 0.0

    GlassPanel(modifier.widthIn(max = 520.dp).fillMaxWidth(), strong = true, tint = color.copy(alpha = 0.6f)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FieldIcon(color, time, affordable, Modifier.size(44.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Txt("Neues Feld", Type.Title)
                    Txt("Feld ${state.ownedFields.size + 1} in ${state.galaxyName}", Type.Small)
                }
                Txt("✕", Type.Title, color = Palette.TextDim, modifier = Modifier.clickable { controller.selectedField = null }.padding(8.dp))
            }
            Txt("Kaufe dieses Feld frei, um hier Sterne zu pflanzen. Jedes weitere Feld wird teurer.", Type.Body)
            if (enrichment > 0.0) Chip("Gedüngt ${formatPercent(enrichment)}", ENRICHED)
            GlowButton(
                "Freikaufen", { controller.buyField(hex) }, Modifier.fillMaxWidth(),
                color = color, enabled = affordable, subtitle = "${formatNumber(cost)} $dust",
            )
            if (!affordable) {
                // Wartezeit nur, wenn sie sich sinnvoll schätzen lässt – Brückenstaub zählt mit.
                val missing = cost - state.stardust
                val rate = displayedRate(state, controller.analysis) + (controller.bridgeInflow[kind] ?: 0.0)
                val wait = if (rate > 0.0) missing / rate else Double.POSITIVE_INFINITY
                Txt(
                    "Noch ${formatNumber(missing)} $dust" + if (wait <= MAX_WAIT_SECONDS) " – etwa ${formatDuration(wait)}" else "",
                    Type.Small, color = Palette.Danger,
                )
            }
            Txt(
                "Danach: ${formatNumber(Balance.fieldCostAt(state, state.fieldsBought + 1))} · Gekaufte Felder bleiben bis zum Urknall dieser Galaxie.",
                Type.Small, color = Palette.TextFaint,
            )
        }
    }
}

/** Gestricheltes Sechseck mit Plus – das Symbol eines Feldes, das sich freikaufen lässt. */
@Composable
private fun FieldIcon(color: Color, time: Float, affordable: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val r = size.minDimension * 0.42f
        val pulse = if (affordable) 0.75f + 0.25f * sin(time * 2.4f) else 0.6f
        val hexagon = Path().apply {
            for (i in 0 until 6) {
                val a = (60f * i - 30f) * PI.toFloat() / 180f
                val p = Offset(center.x + r * cos(a), center.y + r * sin(a))
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
            close()
        }
        drawPath(hexagon, color.copy(alpha = 0.10f * pulse))
        drawPath(hexagon, color.copy(alpha = 0.8f * pulse), style = Stroke(width = 1.5f * density, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * density, 3f * density))))
        val arm = r * 0.38f
        drawLine(color.copy(alpha = pulse), Offset(center.x - arm, center.y), Offset(center.x + arm, center.y), strokeWidth = 2f * density)
        drawLine(color.copy(alpha = pulse), Offset(center.x, center.y - arm), Offset(center.x, center.y + arm), strokeWidth = 2f * density)
    }
}

@Composable
fun ToastStack(toasts: List<Toast>, clock: Float, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (toast in toasts) {
            val age = clock - toast.born
            val fadeIn = (age / GameController.TOAST_FADE).coerceIn(0f, 1f)
            val fadeOut = ((GameController.TOAST_SECONDS - age) / GameController.TOAST_FADE).coerceIn(0f, 1f)
            GlassPanel(
                Modifier.widthIn(max = 420.dp).alpha(minOf(fadeIn, fadeOut)),
                strong = true,
                tint = toast.color.copy(alpha = 0.7f),
                padding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Txt(toast.title, Type.Label, color = toast.color, align = TextAlign.Center)
                    Txt(toast.detail, Type.Small, align = TextAlign.Center, maxLines = 2)
                }
            }
        }
    }
}

@Composable
fun FirstStepsHint(state: GameState, modifier: Modifier = Modifier) {
    // Die ersten Hinweise nur für ganz neue Spieler – nicht für jede frische Galaxie.
    val newcomer = state.stats.bigBangs == 0L && state.parked.isEmpty() && state.activeGalaxy == GalaxyKind.SPIRAL
    val text = when {
        newcomer && state.stars.isEmpty() ->
            "Tippe auf ein leuchtendes Feld, um deinen ersten Roten Zwerg zu pflanzen."
        newcomer && state.stars.size in 1..2 ->
            "Sterne produzieren Sternenstaub. Drei in einer Reihe bilden ein Sternbild!"
        state.activeGalaxy != GalaxyKind.SPIRAL && state.stars.isEmpty() && state.runStardust == 0.0 ->
            "Willkommen in der ${state.activeGalaxy.displayName}! Hier sammelst du ${state.activeGalaxy.dustName} – er gilt nur in dieser Galaxie."
        state.stats.fieldsBought == 0L && state.stars.size >= 3 && state.stardust >= Balance.fieldCost(state) &&
            state.ownedFields.size < Balance.maxFieldCount(state.law) ->
            "Gestrichelte Felder am Rand kannst du freikaufen – tippe eins an."
        else -> return
    }
    GlassPanel(modifier.widthIn(max = 360.dp), padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
        Txt(text, Type.Body, color = Palette.Text, align = TextAlign.Center)
    }
}

/** Dunkle Abdeckung hinter Dialogen; Tippen schließt. */
@Composable
fun Scrim(onDismiss: (() -> Unit)?, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(Color(0xB0020008))
            .let {
                if (onDismiss != null) {
                    it.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
                } else it
            },
    )
}

@Composable
fun SectionTitle(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        Box(Modifier.size(width = 18.dp, height = 2.dp).background(color))
        Spacer(Modifier.width(8.dp))
        Txt(text.uppercase(), Type.Small, color = color)
    }
}
