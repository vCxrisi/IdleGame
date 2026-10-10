package de.vcxrisi.sternengarten.ui.hud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.vcxrisi.sternengarten.game.engine.Balance
import de.vcxrisi.sternengarten.game.engine.GalaxyOfflineLine
import de.vcxrisi.sternengarten.game.engine.OfflineReport
import de.vcxrisi.sternengarten.game.model.Currency
import de.vcxrisi.sternengarten.game.model.GalaxyKind
import de.vcxrisi.sternengarten.game.model.GalaxyLaw
import de.vcxrisi.sternengarten.game.model.GameState
import de.vcxrisi.sternengarten.game.model.runKinds
import de.vcxrisi.sternengarten.game.model.runOf
import de.vcxrisi.sternengarten.game.model.Upgrade
import de.vcxrisi.sternengarten.ui.GameController
import de.vcxrisi.sternengarten.ui.Sheet
import de.vcxrisi.sternengarten.ui.render.drawNebula
import de.vcxrisi.sternengarten.ui.theme.Palette
import de.vcxrisi.sternengarten.ui.theme.Type
import de.vcxrisi.sternengarten.ui.theme.formatDecimal
import de.vcxrisi.sternengarten.ui.theme.formatDuration
import de.vcxrisi.sternengarten.ui.theme.formatNumber
import de.vcxrisi.sternengarten.ui.theme.galaxyColor
import kotlin.math.sin

/** Rahmen für alle Panels, die von unten hereinkommen. */
@Composable
fun BoxScope.BottomSheet(
    title: String,
    color: Color,
    onClose: () -> Unit,
    header: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
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
            if (header != null) {
                header()
                Spacer(Modifier.height(10.dp))
            }
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
            val upgrades = Upgrade.entries.filter { it.currency == currency && !it.retired }
            if (currency == Currency.DARK_MATTER && state.darkMatter <= 0 && state.stats.bigBangs == 0L && state.parked.isEmpty()) {
                SectionTitle("Dunkle Materie – für alle Galaxien", Palette.DarkMatter)
                Txt("Löse deinen ersten Urknall aus, um permanente Forschung freizuschalten.", Type.Body)
                continue
            }
            SectionTitle(
                when (currency) {
                    Currency.STARDUST -> "${state.activeGalaxy.dustName} – nur ${state.galaxyName}"
                    Currency.ELEMENTS -> "Elemente – aus Supernovas"
                    Currency.DARK_MATTER -> "Dunkle Materie – für alle Galaxien"
                },
                currencyColor(state, currency),
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
    val maxed = max != null && level >= max
    val cost = Balance.upgradeCost(state, upgrade)
    val color = currencyColor(state, upgrade.currency)
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

/** Farbe einer Währung; Staub trägt die Farbe der aktiven Galaxie. */
private fun currencyColor(state: GameState, currency: Currency): Color =
    if (currency == Currency.STARDUST) galaxyColor(state.activeGalaxy) else Palette.currency(currency)

@Composable
fun BoxScope.BigBangSheet(controller: GameController) {
    val state = controller.state
    val kind = state.activeGalaxy
    val dust = kind.dustName
    val gain = Balance.darkMatterGain(state)
    val nextAt = Balance.runStardustForDarkMatter(state, gain + 1)
    BottomSheet("Urknall · ${state.galaxyName}", Palette.DarkMatter, { controller.sheet = Sheet.NONE }) {
        Txt(
            "Lass ${state.galaxyName} in sich zusammenstürzen und gebäre sie neu – mit anderen Naturgesetzen. " +
                "Sterne, Felder, $dust, Elemente und Forschung dieser Galaxie vergehen." +
                (if (state.parked.isNotEmpty()) " Deine anderen Galaxien wachsen ungestört weiter." else "") +
                " Dunkle Materie, permanente Forschung, Sternarten und Sternbilder bleiben.",
            Type.Body,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatBlock("Du erhältst", formatNumber(gain), Palette.DarkMatter, Modifier.weight(1f))
            StatBlock("Nächste bei", formatNumber(nextAt), Palette.TextDim, Modifier.weight(1f))
        }
        if (kind.darkMatterMult != 1.0) {
            Txt(
                "Ertrag dieser Galaxie ×${formatDecimal(kind.darkMatterMult, if (kind.darkMatterMult % 1.0 == 0.0) 0 else 1)} – " +
                    "dafür kostet hier alles ×${formatDecimal(kind.costScale, 0)}.",
                Type.Small, color = galaxyColor(kind),
            )
        }
        Txt(
            "Jede ungenutzte Dunkle Materie gibt +${(Balance.DARK_MATTER_BONUS * 100).toInt()} % Produktion. " +
                "In dieser Galaxie verdient: ${formatNumber(state.runStardust)} $dust.",
            Type.Small,
        )
        GlowButton(
            if (gain >= 1) "Urknall auslösen" else "Noch nicht genug $dust",
            { controller.bigBang() },
            Modifier.fillMaxWidth(),
            color = Palette.DarkMatter,
            enabled = gain >= 1,
            subtitle = if (gain >= 1) "+${formatNumber(gain)} Dunkle Materie"
            else "Benötigt ${formatNumber(Balance.runStardustForDarkMatter(state, 1.0))} $dust in dieser Galaxie",
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
fun BoxScope.OfflineDialog(report: OfflineReport, active: GalaxyKind, time: Float, onDismiss: () -> Unit) {
    val several = report.galaxies.size > 1
    val activeColor = galaxyColor(active)
    Scrim(onDismiss, Modifier.fillMaxSize())
    GlassPanel(
        Modifier.align(Alignment.Center).widthIn(max = 400.dp).padding(24.dp),
        strong = true,
        tint = (if (several) Palette.DarkMatter else activeColor).copy(alpha = 0.6f),
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
            if (several) {
                // Eine Zeile je Galaxie – jede mit ihrem eigenen Staub.
                for (line in report.galaxies) OfflineGalaxyRow(line, time)
            } else if (report.stardust > 0.0) {
                Txt("+${formatNumber(report.stardust)}", Type.Huge, color = activeColor)
                Txt(active.dustName, Type.Small)
                if (report.elements > 0) Txt("+${formatNumber(report.elements)} Elemente aus ${report.supernovas} Supernovas", Type.Label, color = Palette.Elements)
            }
            for (kind in report.unlocked) {
                Txt("Neue Galaxie entstanden: ${kind.displayName}", Type.Label, color = Palette.Success, align = TextAlign.Center)
            }
            for (bridge in report.bridgesCompleted) {
                Txt("Sternenbrücke fertig: ${bridge.from.displayName} → ${bridge.to.displayName}", Type.Label, color = Palette.Accent, align = TextAlign.Center)
            }
            GlowButton("Weiter", onDismiss, Modifier.fillMaxWidth(), color = if (several) Palette.DarkMatter else activeColor)
        }
    }
}

@Composable
private fun OfflineGalaxyRow(line: GalaxyOfflineLine, time: Float) {
    val color = galaxyColor(line.kind)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        GalaxyOrb(line.kind, time, 26.dp, ring = 0.45f)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Txt(line.name, Type.Label, maxLines = 1)
            Txt(
                when {
                    line.paused -> "pausiert – Naturgesetze wählen"
                    line.elements > 0 -> "+${formatNumber(line.elements)} Elemente aus ${line.supernovas} Supernovas"
                    else -> line.kind.displayName
                },
                Type.Small, color = if (line.paused) Palette.Boost else Palette.TextDim, maxLines = 1,
            )
            if (line.bridged > 0.0) Txt("+${formatNumber(line.bridged)} über Sternenbrücken", Type.Small, color = Palette.Accent, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Txt(if (line.paused) "–" else "+${formatNumber(line.stardust + line.bridged)}", Type.Label, color = color, maxLines = 1)
            Txt(line.kind.dustName, Type.Small.copy(fontSize = 9.sp), color = Palette.TextFaint, maxLines = 1)
        }
    }
}

@Composable
fun GalaxySelectScreen(controller: GameController, time: Float, modifier: Modifier = Modifier) {
    val state = controller.state
    val choices = state.lawChoices
    val kind = state.activeGalaxy
    // Eine frisch erschlossene Galaxie muss nicht sofort gewählt werden, wenn es woanders etwas zu spielen gibt.
    val playable = state.runKinds().filter { it != kind && state.runOf(it)?.lawChoices?.isEmpty() == true }
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) { drawNebula(time, kind.hue + 40f * sin(time * 0.1f), Offset.Zero) }
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            if (state.galaxyNumber == 0) {
                Txt("NEUE GALAXIE · ${kind.displayName.uppercase()}", Type.Small, color = galaxyColor(kind), align = TextAlign.Center)
                Txt("Wähle die Naturgesetze von ${state.galaxyName}", Type.Title, align = TextAlign.Center)
            } else {
                Txt("URKNALL", Type.Small, color = Palette.DarkMatter)
                Txt("Wähle die Gesetze deiner neuen Galaxie", Type.Title, align = TextAlign.Center)
            }
            Txt(
                "Hier sammelst du ${kind.dustName}" + (kind.exclusiveStar?.let { " · exklusiv: ${it.displayName}" } ?: ""),
                Type.Label, color = galaxyColor(kind), align = TextAlign.Center,
            )
            Txt("Dunkle Materie: ${formatNumber(state.darkMatter)}", Type.Label, color = Palette.DarkMatter)
            for (law in choices) LawCard(law, time) { controller.chooseGalaxy(law) }
            if (playable.isNotEmpty()) {
                GlowButton(
                    "Später wählen",
                    { controller.switchGalaxy(controller.previousGalaxy?.takeIf { it in playable } ?: playable.first()) },
                    color = Palette.TextDim,
                )
            }
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
