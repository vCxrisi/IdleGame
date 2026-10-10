package de.vcxrisi.sternengarten.game.model

import kotlinx.serialization.Serializable

/** Startfläche einer neuen Galaxie ohne Urnebel. Liegt im Modell, weil das Modell `engine.Balance` nicht kennt. */
val STARTING_FIELDS: Set<Hex> = Hex.area(2).toSet()

/**
 * Eine Galaxie, die gerade nicht gespielt wird. Die Felder entsprechen den Galaxie-Feldern von [GameState]
 * mit denselben Default-Werten, nur die Forschung heißt hier [runUpgrades] (ohne die permanente).
 * Nur [activeRun] und [withRun] zählen die Felder auf – ein neues Galaxie-Feld gehört in beide.
 */
@Serializable
data class GalaxyRun(
    val stardust: Double = 15.0,
    val elements: Double = 0.0,
    val stars: Map<Hex, Star> = emptyMap(),
    val enrichment: Map<Hex, Double> = emptyMap(),
    /** Forschung dieser Galaxie, ohne die permanente. */
    val runUpgrades: Map<Upgrade, Int> = emptyMap(),
    val law: GalaxyLaw = GalaxyLaw.NORMAL,
    val galaxyName: String = "Milchstraße",
    val galaxyNumber: Int = 1,
    val runStardust: Double = 0.0,
    val comet: Comet? = null,
    val cometCooldown: Double = 40.0,
    val boostRemaining: Double = 0.0,
    val lawChoices: List<GalaxyLaw> = emptyList(),
    val event: ActiveEvent? = null,
    val eventCooldown: Double = 150.0,
    val galaxyGoals: List<GalaxyGoal> = emptyList(),
    val runSupernovas: Int = 0,
    val ownedFields: Set<Hex> = STARTING_FIELDS,
    val fieldsBought: Int = 0,
)

/** Eine Galaxie entsteht: Sie ist ab [readyAtMs] (Wanduhr) spielbar. */
@Serializable
data class GalaxyUnlock(val kind: GalaxyKind, val startedAtMs: Long, val readyAtMs: Long)

/** Sternenbrücke: Ein Teil der Produktion von [from] fließt als Staub nach [to]. Im Bau, bis [built]. */
@Serializable
data class StarBridge(
    val from: GalaxyKind,
    val to: GalaxyKind,
    val startedAtMs: Long,
    val readyAtMs: Long,
    val built: Boolean = false,
)

// ---- Projektion: Der flache GameState beschreibt immer die aktive Galaxie, die übrigen liegen in `parked`.

/** Die aktive Galaxie als [GalaxyRun]. */
fun GameState.activeRun(): GalaxyRun = GalaxyRun(
    stardust = stardust,
    elements = elements,
    stars = stars,
    enrichment = enrichment,
    runUpgrades = upgrades.filterKeys { !it.permanent },
    law = law,
    galaxyName = galaxyName,
    galaxyNumber = galaxyNumber,
    runStardust = runStardust,
    comet = comet,
    cometCooldown = cometCooldown,
    boostRemaining = boostRemaining,
    lawChoices = lawChoices,
    event = event,
    eventCooldown = eventCooldown,
    galaxyGoals = galaxyGoals,
    runSupernovas = runSupernovas,
    ownedFields = ownedFields,
    fieldsBought = fieldsBought,
)

/** Setzt [run] als aktive Galaxie der Art [kind] ein. Meta-Fortschritt und `parked` bleiben unverändert. */
fun GameState.withRun(kind: GalaxyKind, run: GalaxyRun): GameState = copy(
    activeGalaxy = kind,
    stardust = run.stardust,
    elements = run.elements,
    stars = run.stars,
    enrichment = run.enrichment,
    upgrades = upgrades.filterKeys { it.permanent } + run.runUpgrades,
    law = run.law,
    galaxyName = run.galaxyName,
    galaxyNumber = run.galaxyNumber,
    runStardust = run.runStardust,
    comet = run.comet,
    cometCooldown = run.cometCooldown,
    boostRemaining = run.boostRemaining,
    lawChoices = run.lawChoices,
    event = run.event,
    eventCooldown = run.eventCooldown,
    galaxyGoals = run.galaxyGoals,
    runSupernovas = run.runSupernovas,
    ownedFields = run.ownedFields,
    fieldsBought = run.fieldsBought,
)

/** Die aktive Galaxie zum Parken: Kometen und Ereignisse gibt es nur, wenn jemand zusieht. */
fun GameState.park(): GalaxyRun = activeRun().copy(comet = null, event = null)

/** Wechselt zur geparkten Galaxie [kind] und parkt die bisher aktive; `null`, wenn es dort keine Galaxie gibt. */
fun GameState.switchedTo(kind: GalaxyKind): GameState? {
    val run = parked[kind] ?: return null
    val from = activeGalaxy
    val old = park()
    return withRun(kind, run).copy(parked = parked - kind + (from to old))
}

fun GameState.hasRun(kind: GalaxyKind): Boolean = kind == activeGalaxy || kind in parked

/** Alle Galaxiearten, in denen schon eine Galaxie läuft, in fester Reihenfolge. */
fun GameState.runKinds(): List<GalaxyKind> = GalaxyKind.entries.filter { hasRun(it) }

/** Die Galaxie der Art [kind], aktiv oder geparkt; `null`, wenn es sie noch nicht gibt. */
fun GameState.runOf(kind: GalaxyKind): GalaxyRun? = if (kind == activeGalaxy) activeRun() else parked[kind]

/** Zyklus (`galaxyNumber`) der Galaxie der Art [kind]; 0 heißt: frisch erschlossen, noch ohne Naturgesetz. */
fun GameState.generationOf(kind: GalaxyKind): Int? =
    if (kind == activeGalaxy) galaxyNumber else parked[kind]?.galaxyNumber

/**
 * Ob die Galaxie der Art [kind] schon einmal einen Urknall hatte. Der Urknall erhöht `galaxyNumber` erst mit der
 * Wahl der Naturgesetze, deshalb zählt eine offene Gesetzeswahl ab Zyklus 1 schon mit.
 */
fun GameState.hasCollapsed(kind: GalaxyKind): Boolean {
    val generation = generationOf(kind) ?: return false
    val choosing = if (kind == activeGalaxy) lawChoices.isNotEmpty() else parked[kind]?.lawChoices?.isNotEmpty() == true
    return generation >= 2 || (generation >= 1 && choosing)
}

/** Lesesicht auf eine Galaxie; für geparkte ohne `parked`, damit die Regel „parked enthält nie die aktive“ gilt. */
fun GameState.projected(kind: GalaxyKind): GameState? =
    if (kind == activeGalaxy) this else parked[kind]?.let { copy(parked = emptyMap()).withRun(kind, it) }
