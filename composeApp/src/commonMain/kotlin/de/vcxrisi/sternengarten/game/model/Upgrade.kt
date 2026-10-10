package de.vcxrisi.sternengarten.game.model

enum class Currency(val displayName: String) {
    STARDUST("Sternenstaub"),
    ELEMENTS("Elemente"),
    DARK_MATTER("Dunkle Materie"),
}

enum class Upgrade(
    val displayName: String,
    val description: String,
    val currency: Currency,
    val baseCost: Double,
    val costGrowth: Double,
    val maxLevel: Int? = null,
    /** Permanente Upgrades überleben den Urknall. */
    val permanent: Boolean = false,
    /** Ausgemustert: bleibt nur wegen alter Spielstände stehen, wird nicht angezeigt und nicht verkauft. */
    val retired: Boolean = false,
) {
    NEBULA_EXPANSION(
        "Nebel ausdehnen", "Ersetzt durch Felder, die du einzeln freikaufst.",
        Currency.STARDUST, 400.0, 40.0, maxLevel = 3, retired = true,
    ),
    STELLAR_WIND(
        "Sternenwind", "+25 % Produktion aller Sterne.",
        Currency.STARDUST, 1_000.0, 6.0,
    ),
    FUSION(
        "Kernfusion", "×1,5 Produktion aller Sterne.",
        Currency.ELEMENTS, 3.0, 2.6,
    ),
    LONGEVITY(
        "Stellare Langlebigkeit", "Sterne leben 25 % länger.",
        Currency.ELEMENTS, 2.0, 2.2, maxLevel = 8,
    ),
    ASH_FERTILIZER(
        "Sternenasche", "Supernovas düngen ihre Nachbarfelder 50 % stärker.",
        Currency.ELEMENTS, 4.0, 2.4,
    ),
    DARK_ENERGY(
        "Dunkle Energie", "×2 Produktion – in jeder Galaxie.",
        Currency.DARK_MATTER, 1.0, 10.0, permanent = true,
    ),
    STARDUST_MEMORY(
        "Sternengedächtnis", "Jede neue Galaxie startet mit mehr Staub.",
        Currency.DARK_MATTER, 1.0, 2.5, maxLevel = 5, permanent = true,
    ),
    DEEP_SLEEP(
        "Tiefschlaf", "+2 h maximale Offline-Zeit.",
        Currency.DARK_MATTER, 2.0, 2.0, maxLevel = 5, permanent = true,
    ),
    PRIMORDIAL_NEBULA(
        "Urnebel", "Jede Galaxie startet mit mehr Feldern (+18, dann +24).",
        Currency.DARK_MATTER, 5.0, 4.0, maxLevel = 2, permanent = true,
    ),
    COMET_LURE(
        "Kometenköder", "Kometen erscheinen 30 % öfter und bringen mehr.",
        Currency.DARK_MATTER, 3.0, 2.5, maxLevel = 3, permanent = true,
    ),
    BRIDGE_CRAFT(
        "Brückenbaukunst", "Sternenbrücken tragen 5 Prozentpunkte mehr, ihre Obergrenze steigt um 25 Prozentpunkte.",
        Currency.DARK_MATTER, 25.0, 5.0, maxLevel = 4, permanent = true,
    ),
    SPACE_FOLD(
        "Raumfaltung", "Erschließen und Brückenbau dauern 15 % kürzer.",
        Currency.DARK_MATTER, 500.0, 10.0, maxLevel = 3, permanent = true,
    ),
    NEBULA_SURVEY(
        "Nebelvermessung", "Neue Felder kosten 15 % weniger – in jeder Galaxie.",
        Currency.DARK_MATTER, 10.0, 3.0, maxLevel = 5, permanent = true,
    ),
}
