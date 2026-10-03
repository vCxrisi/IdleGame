package de.vcxrisi.sternengarten.ui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.vcxrisi.sternengarten.game.engine.Balance
import de.vcxrisi.sternengarten.game.engine.BoardAnalysis
import de.vcxrisi.sternengarten.game.engine.StarBreakdown
import de.vcxrisi.sternengarten.game.model.ConstellationKind
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
import de.vcxrisi.sternengarten.ui.theme.starColors

/** Aktuelle Produktion pro Sekunde, wie sie beim Spieler ankommt. */
fun displayedRate(state: GameState, analysis: BoardAnalysis): Double =
    analysis.totalRate * state.law.onlineMult * (if (state.boostRemaining > 0) Balance.COMET_BOOST_MULT else 1.0)

@Composable
fun TopBar(state: GameState, analysis: BoardAnalysis, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        GlassPanel(padding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), tint = Palette.Stardust.copy(alpha = 0.4f)) {
            Column {
                Txt("STERNENSTAUB", Type.Small, color = Palette.Stardust.copy(alpha = 0.8f))
                Txt(formatNumber(state.stardust), Type.Huge, color = Palette.Text, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt("+${formatNumber(displayedRate(state, analysis))}/s", Type.Label, color = Palette.Stardust, maxLines = 1)
                    if (state.boostRemaining > 0) {
                        Spacer(Modifier.width(8.dp))
                        Txt("×5 · ${state.boostRemaining.toInt()} s", Type.Small, color = Palette.Boost, maxLines = 1)
                    }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GlassPanel(
                shape = RoundedCornerShape(14.dp),
                padding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                tint = Color.hsv(state.law.hue, 0.5f, 1f).copy(alpha = 0.5f),
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Txt("Galaxie ${state.galaxyNumber} · ${state.galaxyName}", Type.Small, color = Palette.Text, maxLines = 1)
                    Txt(state.law.displayName, Type.Small, color = Color.hsv(state.law.hue, 0.45f, 1f), maxLines = 1)
                }
            }
            if (state.elements > 0 || state.supernovaCount > 0 || StarType.BLUE_GIANT in state.unlocked) {
                CurrencyChip(formatNumber(state.elements), "Elemente", Palette.Elements)
            }
            if (state.darkMatter > 0 || state.galaxyNumber > 1) {
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
        for (type in StarType.entries) {
            val unlocked = type in state.unlocked
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
                    .border(if (selected) 1.5.dp else 1.dp, if (selected) glow.copy(alpha = 0.9f) else Palette.GlassBorder, shape)
                    .clickable(
                        enabled = unlocked,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        controller.selectedType = type
                        controller.selectedHex = null
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
                        if (unlocked) formatNumber(cost) else "ab ${formatNumber(type.unlockAt)}",
                        Type.Label,
                        color = when {
                            !unlocked -> Palette.TextFaint
                            affordable -> Palette.Stardust
                            else -> Palette.Danger
                        },
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
fun ActionBar(controller: GameController, modifier: Modifier = Modifier) {
    val state = controller.state
    val gain = Balance.darkMatterGain(state)
    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GlowButton("Forschung", { controller.sheet = Sheet.RESEARCH }, Modifier.weight(1f), color = Palette.Elements, compact = true)
        GlowButton(
            "Sternbilder", { controller.sheet = Sheet.CATALOG }, Modifier.weight(1f),
            color = Palette.Accent, compact = true, subtitle = "${state.discovered.size}/${ConstellationKind.entries.size}",
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

    GlassPanel(modifier.widthIn(max = 520.dp).fillMaxWidth(), strong = true, tint = colors.glow.copy(alpha = 0.6f)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StarIcon(visualOf(state, star), time, size = 52.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Txt(star.type.displayName, Type.Title)
                    Txt(
                        if (star.type == StarType.BLACK_HOLE) "Stufe —" else "${phase.displayName} · Stufe ${star.level}",
                        Type.Small,
                    )
                }
                Txt("✕", Type.Title, color = Palette.TextDim, modifier = Modifier.clickable { controller.selectedHex = null }.padding(8.dp))
            }

            if (star.type == StarType.BLACK_HOLE) {
                val inflow = controller.analysis.blackHoleInflow[hex] ?: 0.0
                Txt("Sog: ${formatNumber(inflow * state.law.onlineMult)}/s · gespeichert: ${formatNumber(star.stored)}", Type.Label, color = Palette.Boost)
                Txt("Freisetzen bringt das Dreifache: ${formatNumber(star.stored * Balance.BLACK_HOLE_RELEASE_MULT)}", Type.Body)
            } else if (breakdown != null) {
                Txt("+${formatNumber(breakdown.rate * state.law.onlineMult)} Sternenstaub/s", Type.Label, color = Palette.Stardust)
                BonusChips(breakdown)
            }

            val lifespan = Balance.effectiveLifespan(state, star.type)
            if (lifespan != null && !star.whiteDwarf) {
                val remaining = (lifespan - star.age) / state.law.agingMult
                val label = when (star.type.fate) {
                    StarFate.SUPERNOVA -> "Supernova in ${formatDuration(remaining)}"
                    StarFate.WHITE_DWARF -> "Weißer Zwerg in ${formatDuration(remaining)}"
                    StarFate.ETERNAL -> ""
                }
                Txt(label, Type.Small, color = if (phase == LifePhase.GIANT) Palette.Boost else Palette.TextDim)
                GlowBar((star.age / lifespan).toFloat(), if (phase == LifePhase.GIANT) Palette.Boost else colors.glow, Modifier.fillMaxWidth().height(6.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (star.type == StarType.BLACK_HOLE) {
                    GlowButton(
                        "Freisetzen", { controller.releaseBlackHole(hex) }, Modifier.weight(1f),
                        color = Palette.Boost, enabled = star.stored > 0,
                    )
                } else {
                    val cost = Balance.levelUpCost(state, star)
                    GlowButton(
                        "Verbessern", { controller.levelUp(hex) }, Modifier.weight(1f),
                        color = Palette.Stardust, enabled = state.stardust >= cost, subtitle = formatNumber(cost),
                    )
                }
                GlowButton("Entfernen", { controller.remove(hex) }, color = Palette.Danger, subtitle = "50 % zurück")
            }
        }
    }
}

@Composable
private fun BonusChips(b: StarBreakdown) {
    val chips = buildList {
        if (b.phase != 1.0) add("Lebensphase ×${formatDecimal(b.phase, 1)}" to Palette.TextDim)
        if (b.aura > 0) add("Nachbarn ${formatPercent(b.aura)}" to Palette.Stardust)
        if (b.crowding < 1.0) add("Enge −${formatDecimal((1 - b.crowding) * 100, 0)} %" to Palette.Danger)
        if (b.pair > 1.0) add("Paar ×2" to Color(0xFFC08BFF))
        if (b.enrichment > 0) add("Gedüngt ${formatPercent(b.enrichment)}" to Color(0xFFFFC85A))
        if (b.constellation > 0) add("Sternbilder ${formatPercent(b.constellation)}" to Palette.Accent)
        if (b.drained > 0) add("Schwarzes Loch −50 %" to Palette.DarkMatter)
    }
    if (chips.isEmpty()) {
        Txt("Tipp: Nachbarn, Sternbilder und gedüngte Felder verstärken jeden Stern.", Type.Small)
        return
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((label, color) in chips) {
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f))
                    .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) { Txt(label, Type.Small, color = color, maxLines = 1) }
        }
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
    val text = when {
        state.stars.isEmpty() && state.galaxyNumber == 1 ->
            "Tippe auf ein leuchtendes Feld, um deinen ersten Roten Zwerg zu pflanzen."
        state.stars.size in 1..2 && state.galaxyNumber == 1 ->
            "Sterne produzieren Sternenstaub. Drei in einer Reihe bilden ein Sternbild!"
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
