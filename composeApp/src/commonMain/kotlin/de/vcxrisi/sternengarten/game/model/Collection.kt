package de.vcxrisi.sternengarten.game.model

enum class Rarity(val displayName: String, val weight: Int, val hue: Float) {
    COMMON("Gewöhnlich", 60, 210f),
    RARE("Selten", 28, 190f),
    EPIC("Episch", 10, 285f),
    LEGENDARY("Legendär", 2, 42f),
}

/** Sammelbare Artefakte mit dauerhaften Boni. Doppelte Funde erhöhen die Stufe (max. 10). */
enum class Artifact(val displayName: String, val effect: String, val rarity: Rarity, val perLevel: Double) {
    SEXTANT("Sextant des Ptolemäus", "+8 % Produktion je Stufe", Rarity.COMMON, 0.08),
    CHRONOMETER("Chronometer der Tiefe", "+30 min Offline-Zeit je Stufe", Rarity.COMMON, 1800.0),
    COMET_HARP("Kometenharfe", "+20 % Kometen- und Meteorbelohnung je Stufe", Rarity.COMMON, 0.20),
    EMBER("Ewige Glut", "Sterne leben 8 % länger je Stufe", Rarity.RARE, 0.08),
    PHOENIX_FEATHER("Phönixfeder", "+15 % Elemente aus Supernovas je Stufe", Rarity.RARE, 0.15),
    GRAVITON_LENS("Gravitonlinse", "+6 % Nachbarschaftsboni je Stufe", Rarity.RARE, 0.06),
    STAR_CHART("Uralte Sternenkarte", "+10 % Sternbild-Boni je Stufe", Rarity.EPIC, 0.10),
    HORIZON_SHARD("Horizontsplitter", "Schwarze Löcher geben +0,3× mehr frei je Stufe", Rarity.EPIC, 0.30),
    DARK_COMPASS("Dunkler Kompass", "+10 % Dunkle Materie beim Urknall je Stufe", Rarity.LEGENDARY, 0.10),
    PRIMORDIAL_CRYSTAL("Urkristall", "×1,25 Produktion je Stufe", Rarity.LEGENDARY, 1.25),
    ;

    companion object {
        const val MAX_LEVEL = 10
    }
}

/** Farbthema des Nebels. `hue == null` folgt dem Naturgesetz der Galaxie. [exclusive]: auch im Starterpaket enthalten. */
enum class NebulaTheme(val displayName: String, val hue: Float?, val price: Int, val exclusive: Boolean = false) {
    GALAXY("Galaxie-Farben", null, 0),
    AURORA("Polarlicht", 150f, 150),
    EMBER("Glutnebel", 14f, 150),
    GLACIER("Gletscherblau", 196f, 150),
    ROSE("Rosenquarz", 332f, 200),
    ABYSS("Tiefsee", 238f, 200),
    ROYAL_GOLD("Königsgold", 44f, 500, exclusive = true),
}

/** Farbe der Funken und Partikel. */
enum class SparkStyle(val displayName: String, val price: Int) {
    CLASSIC("Sternfarben", 0),
    GOLDEN("Goldregen", 120),
    FROST("Frostfunken", 120),
    RAINBOW("Regenbogen", 250),
}
