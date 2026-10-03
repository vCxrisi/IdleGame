package de.vcxrisi.sternengarten.game.model

import kotlinx.serialization.Serializable

/** Ein Komet fliegt in normierten Bildschirmkoordinaten (0..1) von [start] nach [end]. */
@Serializable
data class Comet(
    val id: Long,
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val duration: Double,
    val elapsed: Double = 0.0,
)

@Serializable
data class GameState(
    val stardust: Double = 15.0,
    val elements: Double = 0.0,
    val darkMatter: Double = 0.0,
    val stars: Map<Hex, Star> = emptyMap(),
    /** Durch Supernovas gedüngte Felder: Produktionsbonus (0,25 = +25 %). */
    val enrichment: Map<Hex, Double> = emptyMap(),
    val upgrades: Map<Upgrade, Int> = emptyMap(),
    val law: GalaxyLaw = GalaxyLaw.NORMAL,
    val galaxyName: String = "Milchstraße",
    val galaxyNumber: Int = 1,
    /** In dieser Galaxie verdienter Sternenstaub – Grundlage für den Urknall. */
    val runStardust: Double = 0.0,
    val totalStardust: Double = 0.0,
    val unlocked: Set<StarType> = setOf(StarType.RED_DWARF),
    val discovered: Set<ConstellationKind> = emptySet(),
    val supernovaCount: Int = 0,
    val comet: Comet? = null,
    val cometCooldown: Double = 40.0,
    val nextCometId: Long = 1,
    /** Restzeit des Kometenrauschs (×5 Produktion). */
    val boostRemaining: Double = 0.0,
    val playTime: Double = 0.0,
    val lastSavedEpochMs: Long = 0,
    /** Nach dem Urknall: zur Wahl stehende Naturgesetze der nächsten Galaxie. */
    val lawChoices: List<GalaxyLaw> = emptyList(),
) {
    fun level(upgrade: Upgrade): Int = upgrades[upgrade] ?: 0

    fun amount(currency: Currency): Double = when (currency) {
        Currency.STARDUST -> stardust
        Currency.ELEMENTS -> elements
        Currency.DARK_MATTER -> darkMatter
    }
}
