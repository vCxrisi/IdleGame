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
    /** Wächst nur in dieser Galaxieart; `null` für Sterne, die es überall gibt. */
    val exclusiveTo: GalaxyKind? = null,
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
    ),

    // ---- Nur in ihrer Galaxieart. Die Preise gelten für K = 1 und werden dort wie alle Preise ×K genommen.
    FROST_CRYSTAL(
        "Eiskristall",
        "Liebt Symmetrie: +30 % für jede Drehlage um das Zentrum (60°, 120°, …), auf der ebenfalls ein Stern steht. Nur in der Frostgalaxie.",
        baseCost = 120_000.0, costGrowth = 1.30, baseOutput = 900.0,
        lifespan = null, fate = StarFate.ETERNAL, exclusiveTo = GalaxyKind.FROST,
    ),
    EMBER_STAR(
        "Glutstern",
        "Glüht im Nest: +35 % für jeden weiteren Glutstern seiner zusammenhängenden Gruppe. Explodiert als Supernova – je größer das Nest, desto mehr Elemente. Nur in der Glutgalaxie.",
        baseCost = 4_000_000.0, costGrowth = 1.35, baseOutput = 15_000.0,
        lifespan = 300.0, fate = StarFate.SUPERNOVA, exclusiveTo = GalaxyKind.EMBER,
    ),
    AURORA_STAR(
        "Polarlichtstern",
        "Bricht das Licht: +35 % für jede verschiedene Sternart unter seinen Nachbarn. Nur in der Polarlichtgalaxie.",
        baseCost = 30_000_000.0, costGrowth = 1.40, baseOutput = 80_000.0,
        lifespan = null, fate = StarFate.ETERNAL, exclusiveTo = GalaxyKind.AURORA,
    ),
    SHADOW_STAR(
        "Schattenstern",
        "Lebt am Rand der Leere: +60 % für jedes angrenzende Feld, das nicht zu deinem Garten gehört. Nur in der Schattengalaxie.",
        baseCost = 600_000_000.0, costGrowth = 1.60, baseOutput = 1_000_000.0,
        lifespan = null, fate = StarFate.ETERNAL, exclusiveTo = GalaxyKind.SHADOW,
    );

    /** Ab diesem Sternenstaub-Stand wird die Sternart freigeschaltet (in anderen Galaxiearten ×K, siehe Balance.unlockThreshold). */
    val unlockAt: Double get() = baseCost * 0.4
}
