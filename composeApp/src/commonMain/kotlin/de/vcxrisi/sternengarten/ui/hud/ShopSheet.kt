package de.vcxrisi.sternengarten.ui.hud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.vcxrisi.sternengarten.game.model.Artifact
import de.vcxrisi.sternengarten.game.model.CrystalOffer
import de.vcxrisi.sternengarten.game.model.NebulaTheme
import de.vcxrisi.sternengarten.game.model.Rarity
import de.vcxrisi.sternengarten.game.model.SparkStyle
import de.vcxrisi.sternengarten.game.model.StoreProduct
import de.vcxrisi.sternengarten.ui.GameController
import de.vcxrisi.sternengarten.ui.Sheet
import de.vcxrisi.sternengarten.ui.ShopTab
import de.vcxrisi.sternengarten.ui.render.drawGlow
import de.vcxrisi.sternengarten.ui.render.drawNebula
import de.vcxrisi.sternengarten.ui.theme.Palette
import de.vcxrisi.sternengarten.ui.theme.Type
import de.vcxrisi.sternengarten.ui.theme.formatDecimal
import de.vcxrisi.sternengarten.ui.theme.formatNumber
import de.vcxrisi.sternengarten.ui.theme.rarityColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun BoxScope.ShopSheet(controller: GameController, time: Float) {
    BottomSheet(
        "Shop", Palette.Crystal, { controller.sheet = Sheet.NONE },
        header = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CrystalIcon(Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Txt("${formatNumber(controller.state.crystals.toDouble())} Sternenkristalle", Type.Label, color = Palette.Crystal)
                    Spacer(Modifier.weight(1f))
                    Txt("${controller.state.capsules} Kapseln", Type.Small)
                }
                TabRow(
                    ShopTab.entries, controller.shopTab, { it.title }, Palette.Crystal, { controller.shopTab = it },
                    badge = { if (it == ShopTab.ARTIFACTS) controller.state.capsules else 0 },
                )
            }
        },
    ) {
        when (controller.shopTab) {
            ShopTab.CRYSTALS -> CrystalsTab(controller)
            ShopTab.ARTIFACTS -> ArtifactsTab(controller, time)
            ShopTab.COSMETICS -> CosmeticsTab(controller, time)
        }
    }
}

// ------------------------------------------------------------ Kristalle & Echtgeld

@Composable
private fun CrystalsTab(controller: GameController) {
    val state = controller.state

    if (!state.owns(StoreProduct.STARTER_PACK)) {
        FeaturedOffer(controller, StoreProduct.STARTER_PACK, Palette.Stardust, "EINMALIG")
    }
    if (!state.owns(StoreProduct.WANDERER_PASS)) {
        FeaturedOffer(controller, StoreProduct.WANDERER_PASS, Palette.DarkMatter, "DAUERHAFT")
    } else {
        Txt("Du bist Sternenwanderer: ×2 Produktion, +4 h Offline-Zeit, Kometen werden automatisch gefangen.", Type.Small, color = Palette.DarkMatter)
    }

    SectionTitle("Sternenkristalle", Palette.Crystal)
    val packs = listOf(StoreProduct.CRYSTALS_S, StoreProduct.CRYSTALS_M, StoreProduct.CRYSTALS_L, StoreProduct.CRYSTALS_XL)
    for (row in packs.chunked(2)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((i, product) in row.withIndex()) {
                val index = packs.indexOf(product)
                GlassPanel(Modifier.weight(1f), tint = Palette.Crystal.copy(alpha = 0.5f), padding = PaddingValues(10.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        CrystalPile(index + 1, Modifier.size(width = 64.dp, height = 40.dp))
                        Txt(formatNumber(product.crystals.toDouble()), Type.Title, color = Palette.Crystal)
                        Txt(product.displayName, Type.Small, align = TextAlign.Center, maxLines = 1)
                        Spacer(Modifier.height(6.dp))
                        PriceButton(controller, product, Modifier.fillMaxWidth())
                    }
                }
                if (i == 0 && row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }

    SectionTitle("Für Kristalle", Palette.Stardust)
    for (offer in listOf(CrystalOffer.WARP_1H, CrystalOffer.WARP_8H, CrystalOffer.BOOST)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Txt(offer.displayName, Type.Label)
                Txt(offer.description, Type.Small)
            }
            CrystalButton(offer.price, state.crystals >= offer.price) { controller.buyOffer(offer) }
        }
    }

    Spacer(Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Txt(
            "Käufe laufen über ${controller.storeName}. Kristalle verdienst du auch mit Missionen, Erfolgen und Galaxie-Zielen.",
            Type.Small, modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        GlowButton("Wiederherstellen", controller::restorePurchases, color = Palette.TextDim, compact = true)
    }
}

@Composable
private fun FeaturedOffer(controller: GameController, product: StoreProduct, color: Color, tag: String) {
    GlassPanel(Modifier.fillMaxWidth(), strong = true, tint = color.copy(alpha = 0.8f), padding = PaddingValues(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt(product.displayName, Type.Title, color = color, modifier = Modifier.weight(1f))
                Box(Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.2f)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Txt(tag, Type.Small, color = color)
                }
            }
            Txt(product.description, Type.Body)
            PriceButton(controller, product, Modifier.fillMaxWidth(), color = color)
        }
    }
}

/** Knopf mit dem echten Store-Preis; deaktiviert, solange der Store lädt oder ein Kauf läuft. */
@Composable
private fun PriceButton(controller: GameController, product: StoreProduct, modifier: Modifier = Modifier, color: Color = Palette.Crystal) {
    val offer = controller.storeOffers[product.productId]
    val busy = controller.purchaseInFlight == product.productId
    GlowButton(
        text = when {
            busy -> "…"
            offer != null && offer.price.isNotBlank() -> offer.price
            else -> product.fallbackPrice
        },
        onClick = { controller.purchase(product) },
        modifier = modifier,
        color = color,
        enabled = offer != null && controller.purchaseInFlight == null,
        compact = true,
        subtitle = if (offer == null) "Store lädt …" else null,
    )
}

@Composable
private fun CrystalButton(price: Int, affordable: Boolean, onClick: () -> Unit) {
    GlowButton("◆ $price", onClick, color = Palette.Crystal, enabled = affordable, compact = true)
}

/** Kristallhaufen: je größer das Paket, desto mehr Kristalle. */
@Composable
private fun CrystalPile(count: Int, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.height * 0.55f
        val positions = listOf(0f, -0.9f, 0.9f, -1.7f, 1.7f).take(count + 1)
        drawGlow(center, size.height * 0.8f, Palette.Crystal, 0.35f)
        for ((i, dx) in positions.withIndex()) {
            val scale = if (i == 0) 1f else 0.75f
            drawGem(Offset(center.x + dx * w * 0.6f, center.y + (if (i == 0) 0f else w * 0.15f)), w * scale, Palette.Crystal)
        }
    }
}

fun DrawScope.drawGem(center: Offset, width: Float, color: Color) {
    val h = width * 1.3f
    val path = Path().apply {
        moveTo(center.x, center.y - h / 2f)
        lineTo(center.x + width / 2f, center.y - h * 0.12f)
        lineTo(center.x, center.y + h / 2f)
        lineTo(center.x - width / 2f, center.y - h * 0.12f)
        close()
    }
    drawPath(path, Brush.verticalGradient(listOf(Color.White, color, color.copy(alpha = 0.6f)), startY = center.y - h / 2f, endY = center.y + h / 2f))
    drawPath(path, Color.White.copy(alpha = 0.6f), style = Stroke(width = 1f))
}

// ------------------------------------------------------------ Artefakte

@Composable
private fun ArtifactsTab(controller: GameController, time: Float) {
    val state = controller.state
    GlassPanel(Modifier.fillMaxWidth(), strong = true, tint = Palette.Stardust.copy(alpha = 0.6f), padding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CapsuleIcon(Modifier.size(56.dp), time)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Txt("Artefakt-Kapseln: ${state.capsules}", Type.Label)
                GlowButton("Kapsel öffnen", controller::openCapsule, Modifier.fillMaxWidth(), color = Palette.Stardust, enabled = state.capsules > 0)
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (offer in listOf(CrystalOffer.CAPSULE, CrystalOffer.CAPSULE_BUNDLE)) {
            Column(Modifier.weight(1f)) {
                Txt(offer.displayName, Type.Label)
                Txt(offer.description, Type.Small)
                Spacer(Modifier.height(4.dp))
                CrystalButton(offer.price, state.crystals >= offer.price) { controller.buyOffer(offer) }
            }
        }
    }
    // Wahrscheinlichkeiten offen anzeigen.
    Txt(
        "Wahrscheinlichkeiten: " + Rarity.entries.joinToString(" · ") {
            "${it.displayName} ${formatDecimal(controller.shop.dropChance(it) * 100, 0)} %"
        } + ". Doppelte Funde erhöhen die Stufe (max. ${Artifact.MAX_LEVEL}).",
        Type.Small,
    )

    SectionTitle("Sammlung · ${state.artifacts.count { it.value > 0 }}/${Artifact.entries.size}", Palette.Stardust)
    for (artifact in Artifact.entries) {
        val level = state.artifactLevel(artifact)
        val color = rarityColor(artifact.rarity)
        Row(verticalAlignment = Alignment.CenterVertically) {
            ArtifactGlyph(artifact, if (level > 0) color else Palette.TextFaint, Modifier.size(44.dp), time)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Txt(if (level > 0) artifact.displayName else "???", Type.Label, color = if (level > 0) Palette.Text else Palette.TextFaint)
                Txt("${artifact.rarity.displayName} · ${artifact.effect}", Type.Small, color = color.copy(alpha = 0.85f))
            }
            Txt(if (level > 0) "St. $level" else "—", Type.Label, color = if (level > 0) color else Palette.TextFaint)
        }
    }
}

/** Leuchtende Kapsel mit Ring. */
@Composable
fun CapsuleIcon(modifier: Modifier = Modifier, time: Float = 0f) {
    Canvas(modifier) {
        val r = size.minDimension * 0.32f
        drawGlow(center, r * 2.2f, Palette.Stardust, 0.45f + 0.15f * sin(time * 2f))
        drawOval(
            Brush.verticalGradient(listOf(Color.White, Palette.Stardust, Color(0xFFFF9A3D)), startY = center.y - r, endY = center.y + r),
            topLeft = Offset(center.x - r * 0.75f, center.y - r),
            size = androidx.compose.ui.geometry.Size(r * 1.5f, r * 2f),
        )
        drawOval(
            Color.White.copy(alpha = 0.8f),
            topLeft = Offset(center.x - r * 1.1f, center.y - r * 0.18f),
            size = androidx.compose.ui.geometry.Size(r * 2.2f, r * 0.36f),
            style = Stroke(width = r * 0.08f),
        )
    }
}

/** Jedes Artefakt bekommt ein eigenes kleines Symbol aus Grundformen. */
@Composable
fun ArtifactGlyph(artifact: Artifact, color: Color, modifier: Modifier, time: Float) {
    Canvas(modifier) {
        val r = size.minDimension * 0.34f
        drawGlow(center, r * 1.9f, color, 0.35f)
        val stroke = Stroke(width = r * 0.12f)
        when (artifact) {
            Artifact.SEXTANT -> {
                drawArc(color, 200f, 140f, false, topLeft = Offset(center.x - r, center.y - r), size = androidx.compose.ui.geometry.Size(r * 2, r * 2), style = stroke)
                drawLine(color, center, Offset(center.x - r * 0.7f, center.y - r * 0.7f), strokeWidth = r * 0.12f)
                drawLine(color, center, Offset(center.x + r * 0.7f, center.y - r * 0.7f), strokeWidth = r * 0.12f)
            }
            Artifact.CHRONOMETER -> {
                drawCircle(color, r, center, style = stroke)
                val a = time * 0.8f
                drawLine(color, center, Offset(center.x + cos(a) * r * 0.75f, center.y + sin(a) * r * 0.75f), strokeWidth = r * 0.12f)
                drawLine(color, center, Offset(center.x, center.y - r * 0.5f), strokeWidth = r * 0.12f)
            }
            Artifact.COMET_HARP -> {
                for (k in 0 until 4) {
                    val x = center.x - r * 0.6f + k * r * 0.4f
                    drawLine(color, Offset(x, center.y - r * (0.8f - k * 0.15f)), Offset(x, center.y + r * 0.8f), strokeWidth = r * 0.1f)
                }
                drawArc(color, 180f, 180f, false, topLeft = Offset(center.x - r * 0.8f, center.y - r), size = androidx.compose.ui.geometry.Size(r * 1.6f, r * 0.8f), style = stroke)
            }
            Artifact.EMBER -> drawGlow(center, r, Color(0xFFFF7A3D), 1f)
            Artifact.PHOENIX_FEATHER -> {
                val path = Path().apply {
                    moveTo(center.x - r * 0.6f, center.y + r)
                    cubicTo(center.x - r * 0.4f, center.y + r * 0.2f, center.x - r * 0.1f, center.y - r * 0.5f, center.x + r * 0.7f, center.y - r)
                    cubicTo(center.x + r * 0.4f, center.y - r * 0.3f, center.x + r * 0.1f, center.y + r * 0.4f, center.x - r * 0.6f, center.y + r)
                }
                drawPath(path, color)
            }
            Artifact.GRAVITON_LENS -> {
                drawCircle(color, r, center, style = stroke)
                drawCircle(color.copy(alpha = 0.5f), r * 0.55f, center, style = stroke)
            }
            Artifact.STAR_CHART -> {
                val pts = listOf(Offset(-0.7f, 0.5f), Offset(-0.2f, -0.4f), Offset(0.3f, 0.2f), Offset(0.75f, -0.6f)).map { center + it * r }
                for (i in 0 until pts.size - 1) drawLine(color, pts[i], pts[i + 1], strokeWidth = r * 0.08f)
                pts.forEach { drawCircle(Color.White, r * 0.12f, it) }
            }
            Artifact.HORIZON_SHARD -> {
                drawGem(center, r * 1.2f, color)
                drawCircle(Color.Black, r * 0.25f, center)
            }
            Artifact.DARK_COMPASS -> {
                drawCircle(color, r, center, style = stroke)
                val a = time * 0.5f
                val tip = Offset(center.x + cos(a) * r * 0.8f, center.y + sin(a) * r * 0.8f)
                drawLine(color, Offset(2 * center.x - tip.x, 2 * center.y - tip.y), tip, strokeWidth = r * 0.14f)
                drawCircle(Color.White, r * 0.15f, tip)
            }
            Artifact.PRIMORDIAL_CRYSTAL -> {
                drawGem(center, r * 1.3f, color)
                for (k in 0 until 6) {
                    val a = k * PI.toFloat() / 3f + time * 0.4f
                    drawCircle(Color.White.copy(alpha = 0.7f), r * 0.07f, Offset(center.x + cos(a) * r * 1.2f, center.y + sin(a) * r * 1.2f))
                }
            }
        }
    }
}

// ------------------------------------------------------------ Kosmetik

@Composable
private fun CosmeticsTab(controller: GameController, time: Float) {
    val state = controller.state
    SectionTitle("Nebel-Themen", Palette.Accent)
    for (row in NebulaTheme.entries.chunked(2)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (theme in row) {
                val owned = theme in state.ownedThemes
                val active = state.activeTheme == theme
                val hue = theme.hue ?: state.law.hue
                CosmeticCard(
                    title = theme.displayName,
                    status = when {
                        active -> "Aktiv"
                        owned -> "Auswählen"
                        else -> "◆ ${theme.price}"
                    },
                    highlight = active,
                    color = Color.hsv(hue, 0.5f, 1f),
                    note = if (theme.exclusive && !owned) "Auch im Starterpaket" else null,
                    modifier = Modifier.weight(1f),
                    onClick = { controller.selectTheme(theme) },
                ) {
                    Canvas(Modifier.fillMaxWidth().height(54.dp)) { drawNebula(time, hue, Offset.Zero) }
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }

    SectionTitle("Funken", Palette.Stardust)
    for (row in SparkStyle.entries.chunked(2)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (spark in row) {
                val owned = spark in state.ownedSparks
                val active = state.activeSpark == spark
                CosmeticCard(
                    title = spark.displayName,
                    status = when {
                        active -> "Aktiv"
                        owned -> "Auswählen"
                        else -> "◆ ${spark.price}"
                    },
                    highlight = active,
                    color = sparkPreviewColor(spark, 0),
                    note = null,
                    modifier = Modifier.weight(1f),
                    onClick = { controller.selectSpark(spark) },
                ) {
                    Canvas(Modifier.fillMaxWidth().height(40.dp)) {
                        for (k in 0 until 9) {
                            val t = (time * 0.4f + k / 9f) % 1f
                            val p = Offset(size.width * (0.1f + 0.8f * k / 8f), size.height * (0.9f - 0.8f * t))
                            drawGlow(p, size.height * 0.18f, sparkPreviewColor(spark, k), 0.9f * (1f - t))
                        }
                    }
                }
            }
        }
    }
}

fun sparkPreviewColor(spark: SparkStyle, index: Int): Color = when (spark) {
    SparkStyle.CLASSIC -> listOf(Color(0xFFFF4F3A), Color(0xFFFFC53D), Color(0xFF4C8DFF))[index % 3]
    SparkStyle.GOLDEN -> Color(0xFFFFD27A)
    SparkStyle.FROST -> Color(0xFFBFEAFF)
    SparkStyle.RAINBOW -> Color.hsv((index * 40f) % 360f, 0.7f, 1f)
}

@Composable
private fun CosmeticCard(
    title: String,
    status: String,
    highlight: Boolean,
    color: Color,
    note: String?,
    modifier: Modifier,
    onClick: () -> Unit,
    preview: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(Palette.Glass)
            .border(if (highlight) 2.dp else 1.dp, if (highlight) color else Palette.GlassBorder, shape)
            .clickable(onClick = onClick),
    ) {
        preview()
        Column(Modifier.padding(10.dp)) {
            Txt(title, Type.Label, maxLines = 1)
            Txt(status, Type.Small, color = if (highlight) color else Palette.Crystal, maxLines = 1)
            if (note != null) Txt(note, Type.Small, color = Palette.TextFaint, maxLines = 1)
        }
    }
}
