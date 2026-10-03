package de.vcxrisi.sternengarten.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import de.vcxrisi.sternengarten.game.model.Currency
import de.vcxrisi.sternengarten.game.model.StarType

object Palette {
    val SpaceTop = Color(0xFF03010B)
    val SpaceMid = Color(0xFF0B0624)
    val SpaceBottom = Color(0xFF040112)

    val Text = Color(0xFFF3EEFF)
    val TextDim = Color(0xFFB9B0D6)
    val TextFaint = Color(0xFF7E76A0)

    val Glass = Color(0xB3140C2E)
    val GlassStrong = Color(0xE6120A28)
    val GlassBorder = Color(0x40C9B8FF)

    val Stardust = Color(0xFFFFE08A)
    val Elements = Color(0xFF7CF0D2)
    val DarkMatter = Color(0xFFC59BFF)
    val Danger = Color(0xFFFF7A8A)
    val Accent = Color(0xFF8FD3FF)
    val Boost = Color(0xFFFFA94D)

    fun currency(currency: Currency) = when (currency) {
        Currency.STARDUST -> Stardust
        Currency.ELEMENTS -> Elements
        Currency.DARK_MATTER -> DarkMatter
    }
}

/** Farben eines Sterntyps: heller Kern und farbiges Leuchten. */
data class StarColors(val core: Color, val glow: Color, val secondary: Color = glow)

fun starColors(type: StarType): StarColors = when (type) {
    StarType.RED_DWARF -> StarColors(Color(0xFFFFD9C2), Color(0xFFFF4F3A))
    StarType.YELLOW_STAR -> StarColors(Color(0xFFFFFBEA), Color(0xFFFFC53D))
    StarType.BLUE_GIANT -> StarColors(Color(0xFFF2F8FF), Color(0xFF4C8DFF))
    StarType.BINARY -> StarColors(Color(0xFFFFF0DC), Color(0xFFC08BFF), Color(0xFF8ED6FF))
    StarType.PULSAR -> StarColors(Color(0xFFEFFFFF), Color(0xFF3DFFE0))
    StarType.BLACK_HOLE -> StarColors(Color(0xFF000000), Color(0xFFFF9A3D), Color(0xFFFF3DAA))
}

val WhiteDwarfColors = StarColors(Color(0xFFFFFFFF), Color(0xFFBFD8FF))

object Type {
    val Huge = TextStyle(color = Palette.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
    val Title = TextStyle(color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    val Label = TextStyle(color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    val Body = TextStyle(color = Palette.TextDim, fontSize = 13.sp, lineHeight = 18.sp)
    val Small = TextStyle(color = Palette.TextDim, fontSize = 11.sp, fontWeight = FontWeight.Medium)
}
