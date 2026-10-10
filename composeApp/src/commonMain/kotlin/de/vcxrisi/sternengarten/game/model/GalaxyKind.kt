package de.vcxrisi.sternengarten.game.model

/**
 * Galaxiearten. Jede Art sammelt ihren eigenen Staub und hat ihre eigenen Preise ([costScale]),
 * dafür bringt ihr Urknall mehr Dunkle Materie. Freigeschaltet wird der Reihe nach.
 * Die Namen sind Teil des Spielstands: nur anhängen, nie umbenennen oder löschen.
 */
enum class GalaxyKind(
    val displayName: String,
    /** Name des Staubs, den diese Galaxie sammelt. */
    val dustName: String,
    val shortName: String,
    val description: String,
    /** Grundfarbton des Nebels in Grad. */
    val hue: Float,
    /** K: Alle Staubpreise dieser Galaxie (Sterne, Stufen, Felder, Staub-Forschung, Startstaub) ×K. Die Produktion bleibt unskaliert. */
    val costScale: Double,
    /** Faktor auf die Dunkle Materie des Urknalls. */
    val darkMatterMult: Double,
    /** Faktor auf die Dunkle Materie der Galaxie-Ziele. */
    val goalDarkMatterMult: Double,
    /** Startfelder ohne Urnebel und Pionier. */
    val coreFields: Int,
    /** Kosten der Erschließung in Dunkler Materie. */
    val unlockDarkMatter: Double,
    /** Dauer der Erschließung in Stunden. */
    val unlockHours: Double,
    val agingMult: Double = 1.0,
    val supernovaMult: Double = 1.0,
    val fieldCostMult: Double = 1.0,
    val offlineMult: Double = 1.0,
) {
    SPIRAL(
        "Spiralgalaxie", "Sternenstaub", "Spirale",
        "Deine Heimat. Hier gelten die vertrauten Preise.",
        hue = 268f, costScale = 1.0, darkMatterMult = 1.0, goalDarkMatterMult = 1.0,
        coreFields = 19, unlockDarkMatter = 0.0, unlockHours = 0.0,
    ),
    FROST(
        "Frostgalaxie", "Eisstaub", "Frost",
        "Kalt und klar: Alles kostet das Dreifache, Sterne altern 30 % langsamer. Der Urknall bringt ×2,5 Dunkle Materie.",
        hue = 196f, costScale = 3.0, darkMatterMult = 2.5, goalDarkMatterMult = 1.5,
        coreFields = 19, unlockDarkMatter = 100.0, unlockHours = 1.0, agingMult = 0.7,
    ),
    EMBER(
        "Glutgalaxie", "Glutstaub", "Glut",
        "Glühend heiß: Alles kostet das Zehnfache, Supernovas sind 50 % ergiebiger. Der Urknall bringt ×5 Dunkle Materie.",
        hue = 14f, costScale = 10.0, darkMatterMult = 5.0, goalDarkMatterMult = 2.0,
        coreFields = 13, unlockDarkMatter = 2_500.0, unlockHours = 4.0, supernovaMult = 1.5,
    ),
    AURORA(
        "Polarlichtgalaxie", "Lichtstaub", "Polarlicht",
        "Schimmernde Weiten: Alles kostet das Dreißigfache, neue Felder 30 % weniger. Der Urknall bringt ×10 Dunkle Materie.",
        hue = 140f, costScale = 30.0, darkMatterMult = 10.0, goalDarkMatterMult = 3.0,
        coreFields = 13, unlockDarkMatter = 100_000.0, unlockHours = 12.0, fieldCostMult = 0.7,
    ),
    SHADOW(
        "Schattengalaxie", "Schattenstaub", "Schatten",
        "Die Leere ruht: Alles kostet das Hundertfache, offline +50 %. Der Urknall bringt ×20 Dunkle Materie.",
        hue = 245f, costScale = 100.0, darkMatterMult = 20.0, goalDarkMatterMult = 4.0,
        coreFields = 7, unlockDarkMatter = 5_000_000.0, unlockHours = 24.0, offlineMult = 1.5,
    ),
    ;

    /**
     * Die Sternart, die nur in dieser Galaxie wächst. Bewusst kein Konstruktor-Parameter:
     * StarType verweist schon auf GalaxyKind, ein Verweis zurück ergäbe einen Zyklus bei der Initialisierung.
     */
    val exclusiveStar: StarType? get() = StarType.entries.firstOrNull { it.exclusiveTo == this }
}
