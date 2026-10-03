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
    NEUTRON_STAR(
        "Neutronenstern",
        "Ultradicht: Jeder angrenzende Stern leistet, als wäre er 5 Stufen höher.",
        baseCost = 2_500_000.0, costGrowth = 1.35, baseOutput = 6_000.0,
        lifespan = null, fate = StarFate.ETERNAL,
    ),
    BLACK_HOLE(
        "Schwarzes Loch",
        "Verschlingt die Hälfte der Produktion seiner Nachbarn. Antippen setzt das Dreifache frei.",
        baseCost = 8_000_000.0, costGrowth = 1.5, baseOutput = 0.0,
        lifespan = null, fate = StarFate.ETERNAL,
    ),
    MAGNETAR(
        "Magnetar",
        "Feldlinien: +60 % für jeden Stern im Abstand von genau zwei Feldern. Direkte Nachbarn gehen leer aus.",
        baseCost = 60_000_000.0, costGrowth = 1.4, baseOutput = 90_000.0,
        lifespan = null, fate = StarFate.ETERNAL,
    ),
    NEBULA_NURSERY(
        "Nebelwiege",
        "Produziert selbst nichts. Angrenzende Sterne altern nicht mehr und erhalten +20 %.",
        baseCost = 250_000_000.0, costGrowth = 1.6, baseOutput = 0.0,
        lifespan = null, fate = StarFate.ETERNAL,
    ),
    QUASAR(
        "Quasar",
        "Leuchtet über die ganze Galaxie: +3 % Produktion für alle Sterne je Stern im Garten.",
        baseCost = 1_500_000_000.0, costGrowth = 1.8, baseOutput = 2_000_000.0,
        lifespan = null, fate = StarFate.ETERNAL,
    );

    /** Ab diesem Sternenstaub-Stand wird die Sternart freigeschaltet. */
    val unlockAt: Double get() = baseCost * 0.4
}
