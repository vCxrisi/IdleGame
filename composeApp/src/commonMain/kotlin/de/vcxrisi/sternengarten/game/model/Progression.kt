package de.vcxrisi.sternengarten.game.model

import kotlinx.serialization.Serializable

/** Lebenslange Zähler – überleben den Urknall. */
@Serializable
data class Stats(
    val starsPlanted: Long = 0,
    val levelUps: Long = 0,
    val highestLevel: Int = 1,
    val cometsCaught: Long = 0,
    val blackHoleReleases: Long = 0,
    val eventsSeen: Long = 0,
    val missionsCompleted: Long = 0,
    val bigBangs: Long = 0,
    val capsulesOpened: Long = 0,
    val fieldsBought: Long = 0,
    /** Je fertig gebauter Sternenbrücke eins; Abreißen zählt nicht zurück. Nur für die Chronik. */
    val bridgesBuilt: Long = 0,
)

/** Messgrößen, auf denen Missionen und Erfolge aufbauen. */
enum class Metric(val label: String) {
    STARS_PLANTED("Sterne gepflanzt"),
    LEVEL_UPS("Sterne verbessert"),
    HIGHEST_LEVEL("Höchste Sternstufe"),
    SUPERNOVAS("Supernovas"),
    COMETS("Kometen gefangen"),
    BLACK_HOLE_RELEASES("Schwarze Löcher geöffnet"),
    /** Staub aller Galaxien, umgerechnet in Sternenstaub (Staub ÷ K). */
    TOTAL_STARDUST("Staub verdient (Sternenstaub-Wert)"),
    BIG_BANGS("Urknalle"),
    EVENTS("Kosmische Ereignisse erlebt"),
    MISSIONS("Missionen erfüllt"),
    CONSTELLATIONS("Sternbilder entdeckt"),
    STAR_TYPES("Sternarten entdeckt"),
    ARTIFACTS("Artefakte gesammelt"),
    CAPSULES("Kapseln geöffnet"),
    FIELDS_BOUGHT("Felder freigekauft"),
    GALAXIES("Galaxien erschlossen"),
    /** Gleichzeitig stehende Brücken, damit Bauen und Abreißen nichts bringt. */
    BRIDGES("Sternenbrücken gebaut"),
    KINDS_COLLAPSED("Galaxiearten zum Urknall gebracht"),
}

/** Erfolge werden automatisch vergeben. Jeder bringt Kristalle und dauerhaft +2 % Produktion. */
enum class Achievement(val title: String, val metric: Metric, val threshold: Double, val crystals: Int) {
    FIRST_STAR("Erster Funke", Metric.STARS_PLANTED, 1.0, 5),
    GARDENER("Gärtner der Sterne", Metric.STARS_PLANTED, 100.0, 20),
    STAR_FARMER("Sternenbauer", Metric.STARS_PLANTED, 1_000.0, 50),
    POLISHER("Feinschliff", Metric.LEVEL_UPS, 50.0, 15),
    ARTISAN("Sternenschmied", Metric.LEVEL_UPS, 1_000.0, 50),
    LEVEL_25("Gleißend", Metric.HIGHEST_LEVEL, 25.0, 20),
    LEVEL_100("Überirdisch", Metric.HIGHEST_LEVEL, 100.0, 60),
    FIRST_SUPERNOVA("Erste Supernova", Metric.SUPERNOVAS, 1.0, 10),
    SUPERNOVA_50("Feuerwerk", Metric.SUPERNOVAS, 50.0, 30),
    SUPERNOVA_500("Sternenschmiede", Metric.SUPERNOVAS, 500.0, 80),
    COMETS_10("Kometenjäger", Metric.COMETS, 10.0, 15),
    COMETS_100("Schweifsammler", Metric.COMETS, 100.0, 50),
    BLACK_HOLES_10("Ereignishorizont-Tänzer", Metric.BLACK_HOLE_RELEASES, 10.0, 25),
    STARDUST_1M("Millionenstaub", Metric.TOTAL_STARDUST, 1e6, 10),
    STARDUST_1B("Milliardenglanz", Metric.TOTAL_STARDUST, 1e9, 30),
    STARDUST_1T("Billionenleuchten", Metric.TOTAL_STARDUST, 1e12, 60),
    STARDUST_1Q("Unermesslich", Metric.TOTAL_STARDUST, 1e15, 100),
    CONSTELLATIONS_5("Sterndeuter", Metric.CONSTELLATIONS, 5.0, 25),
    CONSTELLATIONS_ALL("Himmelskartograf", Metric.CONSTELLATIONS, ConstellationKind.entries.size.toDouble(), 100),
    STAR_TYPES_ALL("Vollständiger Kosmos", Metric.STAR_TYPES, StarType.entries.size.toDouble(), 80),
    BIG_BANG_1("Neuanfang", Metric.BIG_BANGS, 1.0, 30),
    BIG_BANG_5("Zyklenwanderer", Metric.BIG_BANGS, 5.0, 60),
    BIG_BANG_20("Herr der Zeitalter", Metric.BIG_BANGS, 20.0, 150),
    EVENTS_10("Sturmerprobt", Metric.EVENTS, 10.0, 20),
    MISSIONS_25("Pflichtbewusst", Metric.MISSIONS, 25.0, 40),
    CAPSULES_10("Schatzsucher", Metric.CAPSULES, 10.0, 25),
    ARTIFACTS_ALL("Kurator des Kosmos", Metric.ARTIFACTS, Artifact.entries.size.toDouble(), 150),
    FIRST_FIELD("Landnahme", Metric.FIELDS_BOUGHT, 1.0, 5),
    FIELDS_100("Nebelpächter", Metric.FIELDS_BOUGHT, 100.0, 25),
    FIELDS_1000("Weltenvermesser", Metric.FIELDS_BOUGHT, 1_000.0, 60),
    SECOND_GALAXY("Zweite Heimat", Metric.GALAXIES, 2.0, 30),
    GALAXIES_3("Sternenreich", Metric.GALAXIES, 3.0, 50),
    ALL_GALAXIES("Herr der fünf Galaxien", Metric.GALAXIES, GalaxyKind.entries.size.toDouble(), 150),
    FIRST_BRIDGE("Brückenschlag", Metric.BRIDGES, 1.0, 25),
    BRIDGES_5("Netz der Welten", Metric.BRIDGES, 5.0, 60),
    ALL_KINDS_COLLAPSED("Kosmischer Kreislauf", Metric.KINDS_COLLAPSED, GalaxyKind.entries.size.toDouble(), 120),
}

/** Tägliche Mission: Fortschritt = aktueller Messwert − Ausgangswert bei Vergabe. */
@Serializable
data class Mission(
    val metric: Metric,
    val target: Double,
    val baseline: Double,
    val crystals: Int,
    val claimed: Boolean = false,
)

/** Kosmische Ereignisse verändern für kurze Zeit die Regeln. */
enum class CosmicEvent(val displayName: String, val description: String, val duration: Double, val hue: Float) {
    SOLAR_STORM("Sonnensturm", "Gelbe Sterne und Blaue Riesen leisten ×3 – aber alle Sterne altern doppelt so schnell.", 45.0, 28f),
    GRAVITY_WAVE("Gravitationswelle", "Alle Nachbarschaftsboni wirken doppelt.", 40.0, 200f),
    DARK_TIDE("Dunkle Flut", "Schwarze Löcher saugen dreimal so viel auf.", 60.0, 275f),
    STAR_RAIN("Sternenregen", "Neue Sterne kosten nur die Hälfte.", 40.0, 50f),
    METEOR_SHOWER("Meteorschauer", "Meteore jagen über den Himmel – tippe sie an!", 35.0, 15f),
}

@Serializable
data class ActiveEvent(val kind: CosmicEvent, val remaining: Double)

enum class GoalKind(val label: String) {
    RUN_STARDUST("Verdiene Sternenstaub in dieser Galaxie"),
    STARS_AT_ONCE("Lass gleichzeitig Sterne leuchten"),
    RUN_SUPERNOVAS("Löse Supernovas aus"),
    STAR_LEVEL("Bring einen Stern auf Stufe"),
    ACTIVE_CONSTELLATIONS("Halte verschiedene Sternbilder gleichzeitig aktiv"),
    PRODUCTION_RATE("Erreiche Sternenstaub pro Sekunde"),
    FIELDS_OWNED("Erweitere den Garten auf Felder"),
    EXCLUSIVE_STARS("Lass die besondere Sternart dieser Galaxie leuchten"),
    WHITE_DWARFS("Halte Weiße Zwerge gleichzeitig"),
}

/** Ziel einer einzelnen Galaxie – drei pro Galaxie, alle drei geben zusätzlich eine Artefakt-Kapsel. */
@Serializable
data class GalaxyGoal(
    val kind: GoalKind,
    val target: Double,
    val darkMatter: Double,
    val crystals: Int,
    val claimed: Boolean = false,
)

/** Belohnungen des 7-Tage-Login-Kalenders. */
enum class LoginReward(val label: String, val crystals: Int = 0, val capsules: Int = 0, val warpSeconds: Double = 0.0) {
    DAY_1("10 Kristalle", crystals = 10),
    DAY_2("30 min Zeitsprung", warpSeconds = 1800.0),
    DAY_3("20 Kristalle", crystals = 20),
    DAY_4("Artefakt-Kapsel", capsules = 1),
    DAY_5("30 Kristalle", crystals = 30),
    DAY_6("2 h Zeitsprung", warpSeconds = 7200.0),
    DAY_7("60 Kristalle + Kapsel", crystals = 60, capsules = 1),
}
