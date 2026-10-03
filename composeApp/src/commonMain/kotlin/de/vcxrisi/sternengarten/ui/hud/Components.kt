package de.vcxrisi.sternengarten.ui.hud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.vcxrisi.sternengarten.ui.render.StarVisual
import de.vcxrisi.sternengarten.ui.render.drawStar
import de.vcxrisi.sternengarten.ui.theme.Palette
import de.vcxrisi.sternengarten.ui.theme.Type

/** Halbtransparentes "Glas"-Panel mit feinem Rand. */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    strong: Boolean = false,
    tint: Color = Palette.GlassBorder,
    padding: PaddingValues = PaddingValues(12.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    if (strong) listOf(Palette.GlassStrong, Palette.GlassStrong.copy(alpha = 0.95f))
                    else listOf(Palette.Glass, Palette.Glass.copy(alpha = 0.55f)),
                ),
            )
            .border(1.dp, Brush.verticalGradient(listOf(tint, tint.copy(alpha = 0.05f))), shape)
            .padding(padding),
        content = content,
    )
}

@Composable
fun Txt(
    text: String,
    style: TextStyle = Type.Body,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    align: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style
            .let { if (color != Color.Unspecified) it.copy(color = color) else it }
            .let { if (align != null) it.copy(textAlign = align) else it },
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Leuchtender Knopf; ausgegraut, wenn [enabled] falsch ist. */
@Composable
fun GlowButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Palette.Accent,
    enabled: Boolean = true,
    subtitle: String? = null,
    compact: Boolean = false,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.32f), color.copy(alpha = 0.12f))))
            .border(1.dp, color.copy(alpha = if (enabled) 0.7f else 0.3f), shape)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = if (compact) 6.dp else 14.dp, vertical = if (compact) 6.dp else 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Txt(text, if (compact) Type.Label.copy(fontSize = 14.sp) else Type.Label, color = Palette.Text, maxLines = 1)
            if (subtitle != null) Txt(subtitle, Type.Small, color = color, maxLines = 1)
        }
    }
}

/** Kleines, animiertes Sternsymbol für Bauleiste und Listen. */
@Composable
fun StarIcon(visual: StarVisual, time: Float, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    // Bewusst ohne Clipping: der weiche Leuchthof darf über den Rand hinausstrahlen.
    Canvas(modifier.size(size)) {
        drawStar(center, this.size.minDimension * 0.75f, visual, time)
    }
}

/** Noch unbekannte Sternart: ein schwach schimmernder Umriss. */
@Composable
fun MysteryIcon(time: Float, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension * 0.22f
        val shimmer = 0.5f + 0.5f * kotlin.math.sin(time * 1.3f)
        drawCircle(Palette.TextFaint.copy(alpha = 0.10f + 0.08f * shimmer), r * 1.8f, center)
        drawCircle(Palette.TextFaint.copy(alpha = 0.6f), r, center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4f, 4f))))
    }
}

/** Fortschrittsbalken mit Leuchtkante. */
@Composable
fun GlowBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val r = CornerRadius(size.height / 2f)
        drawRoundRect(Color.White.copy(alpha = 0.08f), cornerRadius = r)
        val w = size.width * fraction.coerceIn(0f, 1f)
        if (w > 0f) {
            drawRoundRect(
                Brush.horizontalGradient(listOf(color.copy(alpha = 0.5f), color)),
                size = Size(w, size.height),
                cornerRadius = r,
            )
        }
    }
}

/** Kleine Kennzahl mit Farbpunkt: "● 12,5K Elemente". */
@Composable
fun CurrencyChip(
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    crystal: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val clickable = if (onClick != null) {
        Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick)
    } else Modifier
    GlassPanel(modifier.then(clickable), shape = RoundedCornerShape(50), padding = PaddingValues(horizontal = 10.dp, vertical = 5.dp), tint = color.copy(alpha = 0.45f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (crystal) {
                CrystalIcon(Modifier.size(12.dp))
            } else {
                Canvas(Modifier.size(8.dp)) {
                    drawCircle(color.copy(alpha = 0.35f), size.minDimension)
                    drawCircle(color, size.minDimension / 2f)
                }
            }
            Spacer(Modifier.size(6.dp))
            Txt(value, Type.Label, color = color, maxLines = 1)
            Spacer(Modifier.size(4.dp))
            Txt(label, Type.Small, maxLines = 1)
        }
    }
}

/** Facettierter Kristall – Symbol der Premium-Währung. */
@Composable
fun CrystalIcon(modifier: Modifier = Modifier, color: Color = Palette.Crystal) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val outline = Path().apply {
            moveTo(w * 0.5f, 0f); lineTo(w, h * 0.38f); lineTo(w * 0.5f, h); lineTo(0f, h * 0.38f); close()
        }
        drawPath(outline, Brush.verticalGradient(listOf(Color.White, color, color.copy(alpha = 0.7f))))
        drawLine(Color.White.copy(alpha = 0.7f), Offset(0f, h * 0.38f), Offset(w, h * 0.38f), strokeWidth = 1f)
        drawLine(Color.White.copy(alpha = 0.5f), Offset(w * 0.5f, 0f), Offset(w * 0.5f, h), strokeWidth = 1f)
    }
}

/** Reiter für Panels mit mehreren Bereichen. */
@Composable
fun <T> TabRow(tabs: List<T>, selected: T, label: (T) -> String, color: Color, onSelect: (T) -> Unit, badge: (T) -> Int = { 0 }) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.05f)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        for (tab in tabs) {
            val active = tab == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (active) color.copy(alpha = 0.25f) else Color.Transparent)
                    .clickable { onSelect(tab) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt(label(tab), Type.Small, color = if (active) Palette.Text else Palette.TextDim, maxLines = 1)
                    val count = badge(tab)
                    if (count > 0) {
                        Spacer(Modifier.width(4.dp))
                        Badge(count)
                    }
                }
            }
        }
    }
}

/** Kleiner roter Zähler für Abholbares. */
@Composable
fun Badge(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier.size(16.dp).clip(RoundedCornerShape(50)).background(Palette.Danger),
        contentAlignment = Alignment.Center,
    ) {
        Txt("$count", Type.Small.copy(fontSize = 9.sp), color = Color.White, maxLines = 1)
    }
}

/** Zeile mit Titel, Fortschrittsbalken und optionalem Knopf rechts. */
@Composable
fun ProgressRow(
    title: String,
    detail: String,
    fraction: Float,
    color: Color,
    action: (@Composable () -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Txt(title, Type.Label, maxLines = 2)
            GlowBar(fraction, color, Modifier.fillMaxWidth().height(5.dp))
            Txt(detail, Type.Small, maxLines = 1)
        }
        if (action != null) {
            Spacer(Modifier.width(10.dp))
            action()
        }
    }
}
