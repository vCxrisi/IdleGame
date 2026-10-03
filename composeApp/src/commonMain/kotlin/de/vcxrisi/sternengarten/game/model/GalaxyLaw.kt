package de.vcxrisi.sternengarten.game.model

/** Naturgesetz einer Galaxie – jede neue Galaxie nach dem Urknall spielt sich anders. */
enum class GalaxyLaw(
    val displayName: String,
    val description: String,
    /** Grundfarbton des Nebels in Grad. */
    val hue: Float,
    val auraMult: Double = 1.0,
    val yellowAuraMult: Double = 1.0,
    val radiusDelta: Int = 0,
    val onlineMult: Double = 1.0,
    val offlineMult: Double = 1.0,
    val agingMult: Double = 1.0,
    val supernovaMult: Double = 1.0,
    val blueGiantPenalty: Boolean = true,
    val costMult: Double = 1.0,
    val constellationMult: Double = 1.0,
    val darkMatterMult: Double = 1.0,
) {
    NORMAL(
        "Vertraute Gesetze",
        "Die Physik, wie du sie kennst. Ein guter Ort, um zu lernen.",
        hue = 268f,
    ),
    HIGH_GRAVITY(
        "Hohe Gravitation",
        "Alle Nachbarschaftsboni wirken doppelt – doch der Nebel ist einen Ring kleiner.",
        hue = 18f, auraMult = 2.0, radiusDelta = -1,
    ),
    TIME_DILATION(
        "Zeitdehnung",
        "Während du fort bist, vergeht die Zeit dreimal so ergiebig. Online nur halb so schnell.",
        hue = 192f, onlineMult = 0.5, offlineMult = 3.0,
    ),
    ENTROPY(
        "Entropie",
        "Sterne altern doppelt so schnell, aber Supernovas sind dreimal so ergiebig.",
        hue = 350f, agingMult = 2.0, supernovaMult = 3.0,
    ),
    GREAT_VOID(
        "Große Leere",
        "Blaue Riesen kennen keine Enge mehr. Gelbe Sterne wärmen nur noch halb.",
        hue = 225f, blueGiantPenalty = false, yellowAuraMult = 0.5,
    ),
    NURSERY(
        "Sternenwiege",
        "Sterne kosten 40 % weniger, leben aber nur halb so lange.",
        hue = 320f, costMult = 0.6, agingMult = 2.0,
    ),
    CELESTIAL_HARP(
        "Himmelsharfe",
        "Sternbilder klingen doppelt so stark.",
        hue = 290f, constellationMult = 2.0,
    ),
    DARK_FLOW(
        "Dunkler Fluss",
        "Alle Sterne leisten 20 % weniger – der nächste Urknall bringt 50 % mehr Dunkle Materie.",
        hue = 150f, onlineMult = 0.8, offlineMult = 0.8, darkMatterMult = 1.5,
    ),
}
