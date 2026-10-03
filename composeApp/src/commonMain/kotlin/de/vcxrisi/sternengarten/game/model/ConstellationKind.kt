package de.vcxrisi.sternengarten.game.model

/**
 * Sternbilder entstehen durch die Anordnung der Sterne. Aktive Sternbilder stärken ihre Mitglieder,
 * einmal entdeckte geben dauerhaft +10 % auf alles – auch nach dem Urknall.
 */
enum class ConstellationKind(
    val displayName: String,
    val hint: String,
    /** Bonus für jeden beteiligten Stern (0,15 = +15 %). */
    val memberBonus: Double,
    /** Linienfarbe als Farbton in Grad. */
    val hue: Float,
) {
    TRIO("Dreigestirn", "Drei Sterne in einer geraden Linie.", 0.15, 200f),
    TRIANGULUM("Triangulum", "Drei Sterne, die sich alle gegenseitig berühren.", 0.20, 160f),
    RED_THREAD("Roter Faden", "Vier Rote Zwerge in einer geraden Linie.", 0.50, 8f),
    RAINBOW("Regenbogen", "Vier verschiedene Sternarten in einer geraden Linie.", 1.00, 300f),
    CROWN("Krone", "Ein Stern, vollständig von sechs Sternen umringt.", 0.30, 45f),
    SUN_CROWN("Sonnenkrone", "Eine Krone mit einem Gelben Stern im Herzen.", 0.60, 52f),
    EVENT_HORIZON("Ereignishorizont", "Ein Schwarzes Loch, vollständig von Sternen umringt.", 1.00, 280f),
}
