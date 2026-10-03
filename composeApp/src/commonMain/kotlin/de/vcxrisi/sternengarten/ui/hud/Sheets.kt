package de.vcxrisi.sternengarten.ui.hud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.vcxrisi.sternengarten.game.engine.Balance
import de.vcxrisi.sternengarten.game.engine.OfflineReport
import de.vcxrisi.sternengarten.game.model.ConstellationKind
import de.vcxrisi.sternengarten.game.model.Currency
import de.vcxrisi.sternengarten.game.model.GalaxyLaw
import de.vcxrisi.sternengarten.game.model.Hex
import de.vcxrisi.sternengarten.game.model.Upgrade
import de.vcxrisi.sternengarten.ui.GameController
import de.vcxrisi.sternengarten.ui.Sheet
import de.vcxrisi.sternengarten.ui.render.drawGlow
import de.vcxrisi.sternengarten.ui.render.drawNebula
import de.vcxrisi.sternengarten.ui.theme.Palette
import de.vcxrisi.sternengarten.ui.theme.Type
import de.vcxrisi.sternengarten.ui.theme.formatDuration
import de.vcxrisi.sternengarten.ui.theme.formatNumber
import de.vcxrisi.sternengarten.ui.theme.formatPercent
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Rahmen für alle Panels, die von unten hereinkommen. */
@Composable
fun BoxScope.BottomSheet(title: String, color: Color, onClose: () -> Unit, content: @Composable () -> Unit) {
    Scrim(onClose, Modifier.fillMaxSize())
    GlassPanel(
        Modifier
            .align(Alignment.BottomCenter)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .heightIn(max = 620.dp)
            .padding(8.dp)
            // Klicks innerhalb des Panels sollen den Scrim nicht erreichen.
            .pointerInput(Unit) { detectTapGestures { } },
        strong = true,
        shape = RoundedCornerShape(26.dp),
        tint = color.copy(alpha = 0.7f),
        padding = PaddingValues(18.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt(title, Type.Title, color = color, modifier = Modifier.weight(1f))
                Txt("✕", Type.Title, color = Palette.TextDim, modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClose).padding(8.dp))
            }
            Spacer(Modifier.height(8.dp))
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                content()
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun BoxScope.ResearchSheet(controller: GameController) {
    val state = controller.state
    BottomSheet("Forschung", Palette.Elements, { controller.sheet = Sheet.NONE }) {
        for (currency in Currency.entries) {
            val upgrades = Upgrade.entries.filter { it.currency == currency }
            if (currency == Currency.DARK_MATTER && state.darkMatter <= 0 && state.galaxyNumber == 1) {
                SectionTitle("Dunkle Materie – permanent", Palette.DarkMatter)
                Txt("Löse deinen ersten Urknall aus, um permanente Forschung freizuschalten.", Type.Body)
                continue
            }
            SectionTitle(
                when (currency) {
                    Currency.STARDUST -> "Sternenstaub"
                    Currency.ELEMENTS -> "Elemente – aus Supernovas"
                    Currency.DARK_MATTER -> "Dunkle Materie – permanent"
                },
                Palette.currency(currency),
            )
            for (upgrade in upgrades) UpgradeRow(controller, upgrade)
        }
    }
}

@Composable
private fun UpgradeRow(controller: GameController, upgrade: Upgrade) {
    val state = controller.state
    val level = state.level(upgrade)
    val max = upgrade.maxLevel
    val maxed = (max != null && level >= max) ||
        (upgrade == Upgrade.NEBULA_EXPANSION && Balance.gardenRadius(state) >= Balance.MAX_RADIUS)
    val cost = Balance.upgradeCost(state, upgrade)
    val color = Palette.currency(upgrade.currency)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Txt(upgrade.displayName, Type.Label)
            Txt(upgrade.description, Type.Small)
            Txt(if (max != null) "Stufe $level / $max" else "Stufe $level", Type.Small, color = color.copy(alpha = 0.8f))
        }
        Spacer(Modifier.width(10.dp))
        GlowButton(
            if (maxed) "Max." else formatNumber(cost),
            { controller.buy(upgrade) },
            color = color,
            enabled = !maxed && controller.state.amount(upgrade.currency) >= cost,
            compact = true,
        )
    }
}

@Composable
fun BoxScope.CatalogSheet(controller: GameController) {
    val state = controller.state
    val active = controller.analysis.activeKinds
    BottomSheet("Sternbilder", Palette.Accent, { controller.sheet = Sheet.NONE }) {
        Txt(
            "Jedes entdeckte Sternbild gibt dauerhaft +10 % auf alles – auch nach dem Urknall. " +
                "Aktive Sternbilder stärken zusätzlich jeden beteiligten Stern.",
            Type.Body,
        )
        for (kind in ConstellationKind.entries) {
            val found = kind in state.discovered
            val color = Color.hsv(kind.hue, 0.55f, 1f)
            Row(verticalAlignment = Alignment.CenterVertically) {
                ConstellationGlyph(kind, if (found) color else Palette.TextFaint, Modifier.size(54.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Txt(if (found) kind.displayName else "Unentdeckt", Type.Label, color = if (found) Palette.Text else Palette.TextFaint)
                        if (kind in active) {
                            Spacer(Modifier.width(8.dp))
                            Txt("AKTIV", Type.Small, color = color)
                        }
                    }
                    Txt(kind.hint, Type.Small)
                    Txt("${formatPercent(kind.memberBonus * state.law.constellationMult)} je beteiligtem Stern", Type.Small, color = color.copy(alpha = if (found) 0.9f else 0.4f))
                }
            }
        }
        SectionTitle("Chronik", Palette.TextDim)
        Txt(
            "Galaxien: ${state.galaxyNumber} · Supernovas: ${state.supernovaCount} · " +
                "Sternenstaub insgesamt: ${formatNumber(state.totalStardust)} · Spielzeit: ${formatDuration(state.playTime)}",
            Type.Small,
        )
    }
}

/** Kleine Skizze der Sternbild-Form. */
@Composable
private fun ConstellationGlyph(kind: ConstellationKind, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val unit = size.minDimension / 5.2f
        val sqrt3 = sqrt(3f)
        fun p(h: Hex) = Offset(center.x + unit * sqrt3 * (h.q + h.r / 2f), center.y + unit * 1.5f * h.r)
        val points: List<Hex> = when (kind) {
            ConstellationKind.TRIO -> listOf(Hex(-1, 0), Hex(0, 0), Hex(1, 0))
            ConstellationKind.TRIANGULUM -> listOf(Hex(0, 0), Hex(1, -1), Hex(0, -1), Hex(0, 0))
            ConstellationKind.RED_THREAD, ConstellationKind.RAINBOW -> listOf(Hex(-1, 0), Hex(0, 0), Hex(1, 0), Hex(2, 0)).map { Hex(it.q, 0) }
            else -> Hex.ORIGIN.neighbors() + Hex.ORIGIN.neighbors().first()
        }
        val offset = if (kind == ConstellationKind.RED_THREAD || kind == ConstellationKind.RAINBOW) Offset(-unit * sqrt3 / 2f, 0f) else Offset.Zero
        val pts = points.map { p(it) + offset }
        for (i in 0 until pts.size - 1) drawLine(color.copy(alpha = 0.6f), pts[i], pts[i + 1], strokeWidth = 2f)
        for (pt in pts.distinct()) drawGlow(pt, unit * 0.8f, color, 0.9f)
        if (kind == ConstellationKind.CROWN || kind == ConstellationKind.SUN_CROWN || kind == ConstellationKind.EVENT_HORIZON) {
            val core = when (kind) {
                ConstellationKind.SUN_CROWN -> Color(0xFFFFC53D)
                ConstellationKind.EVENT_HORIZON -> Color(0xFFFF9A3D)
                else -> color
            }
            drawGlow(center, unit, core, 1f)
        }
    }
}

@Composable
fun BoxScope.BigBangSheet(controller: GameController) {
    val state = controller.state
    val gain = Balance.darkMatterGain(state)
    val nextAt = ((gain + 1) / state.law.darkMatterMult).pow(2) * 1_000_000.0
    BottomSheet("Urknall", Palette.DarkMatter, { controller.sheet = Sheet.NONE }) {
        Txt(
            "Lass diese Galaxie in sich zusammenstürzen und gebäre eine neue – mit anderen Naturgesetzen. " +
                "Sterne, Sternenstaub, Elemente und Forschung vergehen. " +
                "Dunkle Materie, permanente Forschung, freigeschaltete Sternarten und entdeckte Sternbilder bleiben.",
            Type.Body,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatBlock("Du erhältst", formatNumber(gain), Palette.DarkMatter, Modifier.weight(1f))
            StatBlock("Nächste bei", formatNumber(nextAt), Palette.TextDim, Modifier.weight(1f))
        }
        Txt(
            "Jede ungenutzte Dunkle Materie gibt +${(Balance.DARK_MATTER_BONUS * 100).toInt()} % Produktion. " +
                "In dieser Galaxie verdient: ${formatNumber(state.runStardust)} Sternenstaub.",
            Type.Small,
        )
        GlowButton(
            if (gain >= 1) "Urknall auslösen" else "Noch nicht genug Sternenstaub",
            { controller.bigBang() },
            Modifier.fillMaxWidth(),
            color = Palette.DarkMatter,
            enabled = gain >= 1,
            subtitle = if (gain >= 1) "+${formatNumber(gain)} Dunkle Materie" else "Benötigt 1M in dieser Galaxie",
        )
    }
}

@Composable
private fun StatBlock(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    GlassPanel(modifier, tint = color.copy(alpha = 0.5f), padding = PaddingValues(10.dp)) {
        Column {
            Txt(label.uppercase(), Type.Small)
            Txt(value, Type.Title, color = color)
        }
    }
}

@Composable
fun BoxScope.OfflineDialog(report: OfflineReport, onDismiss: () -> Unit) {
    Scrim(onDismiss, Modifier.fillMaxSize())
    GlassPanel(
        Modifier.align(Alignment.Center).widthIn(max = 380.dp).padding(24.dp),
        strong = true,
        tint = Palette.Stardust.copy(alpha = 0.6f),
        padding = PaddingValues(20.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Txt("Während du fort warst", Type.Title, align = TextAlign.Center)
            Txt(
                if (report.cappedSeconds < report.seconds) {
                    "${formatDuration(report.seconds)} (davon ${formatDuration(report.cappedSeconds)} gezählt)"
                } else formatDuration(report.seconds),
                Type.Small, align = TextAlign.Center,
            )
            Txt("+${formatNumber(report.stardust)}", Type.Huge, color = Palette.Stardust)
            Txt("Sternenstaub", Type.Small)
            if (report.elements > 0) Txt("+${formatNumber(report.elements)} Elemente aus ${report.supernovas} Supernovas", Type.Label, color = Palette.Elements)
            GlowButton("Weiter", onDismiss, Modifier.fillMaxWidth(), color = Palette.Stardust)
        }
    }
}

@Composable
fun GalaxySelectScreen(controller: GameController, time: Float, modifier: Modifier = Modifier) {
    val choices = controller.state.lawChoices
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) { drawNebula(time, 270f + 40f * sin(time * 0.1f), Offset.Zero) }
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Txt("URKNALL", Type.Small, color = Palette.DarkMatter)
            Txt("Wähle die Gesetze deiner neuen Galaxie", Type.Title, align = TextAlign.Center)
            Txt("Dunkle Materie: ${formatNumber(controller.state.darkMatter)}", Type.Label, color = Palette.DarkMatter)
            for (law in choices) LawCard(law, time) { controller.chooseGalaxy(law) }
        }
    }
}

@Composable
private fun LawCard(law: GalaxyLaw, time: Float, onChoose: () -> Unit) {
    val color = Color.hsv(law.hue, 0.5f, 1f)
    GlassPanel(
        Modifier.widthIn(max = 480.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onChoose),
        strong = true,
        tint = color.copy(alpha = 0.8f),
        padding = PaddingValues(0.dp),
    ) {
        Column {
            Canvas(Modifier.fillMaxWidth().height(70.dp)) { drawNebula(time, law.hue, Offset.Zero) }
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Txt(law.displayName, Type.Title, color = color)
                Txt(law.description, Type.Body)
                Txt("Diese Galaxie erschaffen →", Type.Label, color = color, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}
