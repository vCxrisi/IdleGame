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
    /** Meteore (während eines Meteorschauers) sind kleiner, schneller und bringen immer Sternenstaub. */
    val meteor: Boolean = false,
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

    // ---- Kosmische Ereignisse und Galaxie-Ziele (pro Galaxie)
    val event: ActiveEvent? = null,
    val eventCooldown: Double = 150.0,
    val galaxyGoals: List<GalaxyGoal> = emptyList(),
    val runSupernovas: Int = 0,

    // ---- Dauerhafter Fortschritt
    /** Premium-Währung: im Spiel verdient oder im Store gekauft. */
    val crystals: Int = 0,
    val stats: Stats = Stats(),
    val achievements: Set<Achievement> = emptySet(),
    val missions: List<Mission> = emptyList(),
    val missionDay: Long = -1,
    val loginStreak: Int = 0,
    val lastLoginDay: Long = -1,
    /** Noch abzuholende Login-Belohnung des heutigen Tages. */
    val pendingLoginReward: LoginReward? = null,
    val artifacts: Map<Artifact, Int> = emptyMap(),
    val capsules: Int = 0,
    val ownedThemes: Set<NebulaTheme> = setOf(NebulaTheme.GALAXY),
    val activeTheme: NebulaTheme = NebulaTheme.GALAXY,
    val ownedSparks: Set<SparkStyle> = setOf(SparkStyle.CLASSIC),
    val activeSpark: SparkStyle = SparkStyle.CLASSIC,

    // ---- Store
    /** Gekaufte, dauerhafte Store-Produkte (Product-IDs). */
    val entitlements: Set<String> = emptySet(),
    /** Bereits gutgeschriebene Transaktionen – verhindert doppelte Gutschrift. */
    val processedTransactions: Set<String> = emptySet(),
) {
    fun level(upgrade: Upgrade): Int = upgrades[upgrade] ?: 0

    fun artifactLevel(artifact: Artifact): Int = artifacts[artifact] ?: 0

    fun owns(product: StoreProduct): Boolean = product.productId in entitlements

    val eventKind: CosmicEvent? get() = event?.kind

    /** Farbton des Nebels: gewähltes Thema oder das Naturgesetz der Galaxie. */
    val nebulaHue: Float get() = activeTheme.hue ?: law.hue

    fun amount(currency: Currency): Double = when (currency) {
        Currency.STARDUST -> stardust
        Currency.ELEMENTS -> elements
        Currency.DARK_MATTER -> darkMatter
    }
}
