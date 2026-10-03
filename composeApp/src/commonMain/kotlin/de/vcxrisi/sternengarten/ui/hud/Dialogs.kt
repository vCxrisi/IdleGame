package de.vcxrisi.sternengarten.ui.hud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.vcxrisi.sternengarten.game.model.ActiveEvent
import de.vcxrisi.sternengarten.game.model.LoginReward
import de.vcxrisi.sternengarten.ui.CapsuleReveal
import de.vcxrisi.sternengarten.ui.render.drawGlow
import de.vcxrisi.sternengarten.ui.theme.Palette
import de.vcxrisi.sternengarten.ui.theme.Type
import de.vcxrisi.sternengarten.ui.theme.rarityColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Begrüßung mit der Tagesbelohnung. */
@Composable
fun BoxScope.LoginRewardDialog(streak: Int, reward: LoginReward, onClaim: () -> Unit) {
    Scrim(null, Modifier.fillMaxSize())
    GlassPanel(
        Modifier.align(Alignment.Center).widthIn(max = 420.dp).padding(20.dp),
        strong = true,
        tint = Palette.Crystal.copy(alpha = 0.7f),
        padding = PaddingValues(20.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Txt("Willkommen zurück!", Type.Title, align = TextAlign.Center)
            Txt("Tag $streak deiner Serie – komm morgen wieder für mehr.", Type.Small, align = TextAlign.Center)
            LoginCalendar(streak, reward)
            Txt(reward.label, Type.Huge.copy(fontSize = Type.Title.fontSize), color = Palette.Crystal, align = TextAlign.Center)
            GlowButton("Abholen", onClaim, Modifier.fillMaxWidth(), color = Palette.Crystal)
        }
    }
}

/** Enthüllung eines Artefakts: Lichtstrahlen in der Farbe der Seltenheit. */
@Composable
fun BoxScope.CapsuleRevealOverlay(reveal: CapsuleReveal, clock: Float, onDismiss: () -> Unit, onOpenAnother: (() -> Unit)?) {
    val age = clock - reveal.born
    val appear = min(1f, age / 0.6f)
    val event = reveal.event
    val color = rarityColor(event.artifact.rarity)
    Scrim(onDismiss, Modifier.fillMaxSize())
    Column(
        Modifier.align(Alignment.Center).padding(24.dp).alpha(appear),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
            // Weiche Lichtstrahlen hinter dem Artefakt; sie dürfen über den Rahmen hinausragen.
            Canvas(Modifier.size(200.dp)) {
                val reach = size.minDimension * (0.6f + 0.6f * appear)
                val rays = if (event.artifact.rarity.ordinal >= 2) 16 else 10
                for (k in 0 until rays) {
                    val a = k * 2f * PI.toFloat() / rays + age * 0.25f
                    val end = Offset(center.x + cos(a) * reach, center.y + sin(a) * reach)
                    drawLine(
                        Brush.linearGradient(listOf(color.copy(alpha = 0.45f * appear), Color.Transparent), center, end),
                        center, end, strokeWidth = size.minDimension * 0.05f, blendMode = BlendMode.Plus,
                    )
                }
                drawGlow(center, reach * 0.55f, color, 0.55f * appear)
            }
            ArtifactGlyph(event.artifact, color, Modifier.size(110.dp).scale(0.6f + 0.4f * appear), clock)
        }
        Txt(event.artifact.rarity.displayName.uppercase(), Type.Small, color = color)
        Txt(event.artifact.displayName, Type.Title, align = TextAlign.Center)
        Txt(event.artifact.effect, Type.Body, align = TextAlign.Center)
        Txt(
            when {
                event.refund > 0 -> "Bereits auf Höchststufe: +${event.refund} Kristalle"
                event.level == 1 -> "Neu in deiner Sammlung!"
                else -> "Stufe ${event.level} erreicht"
            },
            Type.Label, color = if (event.refund > 0) Palette.Crystal else Palette.Success, align = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlowButton("Weiter", onDismiss, color = color)
            if (onOpenAnother != null) GlowButton("Nächste öffnen", onOpenAnother, color = Palette.Stardust)
        }
    }
}

/** Banner für laufende kosmische Ereignisse. */
@Composable
fun EventBanner(event: ActiveEvent, modifier: Modifier = Modifier) {
    val color = Color.hsv(event.kind.hue, 0.55f, 1f)
    GlassPanel(
        modifier.widthIn(max = 460.dp).fillMaxWidth(),
        strong = true,
        tint = color.copy(alpha = 0.8f),
        padding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt(event.kind.displayName.uppercase(), Type.Label, color = color, modifier = Modifier.weight(1f))
                Txt("${event.remaining.toInt()} s", Type.Label, color = Palette.Text)
            }
            Txt(event.kind.description, Type.Small, maxLines = 2)
            GlowBar((event.remaining / event.kind.duration).toFloat(), color, Modifier.fillMaxWidth().height(4.dp))
        }
    }
}
