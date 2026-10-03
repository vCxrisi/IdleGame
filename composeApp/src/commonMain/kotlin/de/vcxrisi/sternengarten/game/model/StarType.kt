package de.vcxrisi.sternengarten.game.model

/** Was am Ende eines Sternenlebens passiert. */
enum class StarFate { ETERNAL, WHITE_DWARF, SUPERNOVA }

enum class StarType(
    val displayName: String,
    val description: String,
    val baseCost: Double,
    val costGrowth: Double,
    val baseOutput: Double,
    /** Lebensdauer in Sekunden, `null` für ewige Sterne. */
    val lifespan: Double?,
    val fate: StarFate,
) {
    RED_DWARF(
        "Roter Zwerg",
        "Genügsam und ewig. Das Fundament jedes Gartens.",
        baseCost = 10.0, costGrowth = 1.15, baseOutput = 1.0,
        lifespan = null, fate = StarFate.ETERNAL,
    ),
    YELLOW_STAR(
        "Gelber Stern",
        "Wärmt seine Nachbarn: +25 % Produktion für jeden angrenzenden Stern. Wird am Ende zum Weißen Zwerg.",
        baseCost = 150.0, costGrowth = 1.18, baseOutput = 6.0,
        lifespan = 600.0, fate = StarFate.WHITE_DWARF,
    ),
    BLUE_GIANT(
        "Blauer Riese",
        "Gewaltig, aber braucht Raum: −15 % je belegtem Nachbarfeld. Explodiert als Supernova, bringt Elemente und düngt die Felder ringsum.",
        baseCost = 2_000.0, costGrowth = 1.22, baseOutput = 80.0,
        lifespan = 240.0, fate = StarFate.SUPERNOVA,
    ),
    BINARY(
        "Doppelstern",
        "Leistet doppelt, wenn ein zweiter Doppelstern direkt daneben kreist.",
        baseCost = 30_000.0, costGrowth = 1.25, baseOutput = 450.0,
        lifespan = 900.0, fate = StarFate.WHITE_DWARF,
    ),
    PULSAR(
        "Pulsar",
        "Strahlt entlang seiner drei Achsen: +40 % für jeden Stern bis zu drei Felder entfernt im Strahl.",
        baseCost = 500_000.0, costGrowth = 1.3, baseOutput = 2_500.0,
        lifespan = null, fate = StarFate.ETERNAL,
    ),
    BLACK_HOLE(
        "Schwarzes Loch",
        "Verschlingt die Hälfte der Produktion seiner Nachbarn. Antippen setzt das Dreifache frei.",
        baseCost = 8_000_000.0, costGrowth = 1.5, baseOutput = 0.0,
        lifespan = null, fate = StarFate.ETERNAL,
    );

    /** Ab diesem Sternenstaub-Stand wird die Sternart freigeschaltet. */
    val unlockAt: Double get() = baseCost * 0.4
}
