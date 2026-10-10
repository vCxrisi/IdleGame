package de.vcxrisi.sternengarten.game.engine

import de.vcxrisi.sternengarten.game.model.GalaxyRun
import de.vcxrisi.sternengarten.game.model.GameState
import de.vcxrisi.sternengarten.game.model.Hex
import de.vcxrisi.sternengarten.game.model.Star

/**
 * Obergrenze für alle Spielwerte. Weit genug unter Double.MAX_VALUE (~1,8e308), damit auch
 * Produkte aus mehreren begrenzten Werten nicht sofort unendlich werden.
 */
const val VALUE_CAP = 1e300

/** NaN wird zu 0, alles andere wird auf ±[VALUE_CAP] begrenzt. */
fun Double.capped(): Double = when {
    isNaN() -> 0.0
    this > VALUE_CAP -> VALUE_CAP
    this < -VALUE_CAP -> -VALUE_CAP
    else -> this
}

/**
 * Bringt jeden Double-Wert des Spielstands in den gültigen Bereich. So kann ein Überlauf
 * (∞ − ∞ = NaN) den Spielstand nie mehr unbrauchbar machen. Gilt auch für die geparkten Galaxien.
 */
fun GameState.sanitized(): GameState {
    val clean = copy(
        stardust = stardust.capped().coerceAtLeast(0.0),
        elements = elements.capped().coerceAtLeast(0.0),
        darkMatter = darkMatter.capped().coerceAtLeast(0.0),
        runStardust = runStardust.capped().coerceAtLeast(0.0),
        totalStardust = totalStardust.capped().coerceAtLeast(0.0),
        boostRemaining = boostRemaining.capped().coerceAtLeast(0.0),
        cometCooldown = cometCooldown.capped(),
        eventCooldown = eventCooldown.capped(),
        playTime = playTime.capped().coerceAtLeast(0.0),
        stars = cleanStars(stars),
        enrichment = cleanEnrichment(enrichment),
        // Saubere Galaxien werden nicht kopiert – das läuft nach jedem Tick.
        parked = if (parked.values.none { it.hasUnsafeValues() }) parked else parked.mapValues { it.value.sanitized() },
    )
    return clean
}

fun GalaxyRun.sanitized(): GalaxyRun = copy(
    stardust = stardust.capped().coerceAtLeast(0.0),
    elements = elements.capped().coerceAtLeast(0.0),
    runStardust = runStardust.capped().coerceAtLeast(0.0),
    boostRemaining = boostRemaining.capped().coerceAtLeast(0.0),
    cometCooldown = cometCooldown.capped(),
    eventCooldown = eventCooldown.capped(),
    stars = cleanStars(stars),
    enrichment = cleanEnrichment(enrichment),
)

/** Dieselbe Map, wenn schon alles stimmt – sonst eine bereinigte Kopie. */
private fun cleanStars(stars: Map<Hex, Star>): Map<Hex, Star> =
    if (stars.values.all { it.stored.isSafe() && it.age.isSafe() }) stars
    else stars.mapValues { (_, star) -> star.copy(stored = star.stored.capped().coerceAtLeast(0.0), age = star.age.capped().coerceAtLeast(0.0)) }

private fun cleanEnrichment(enrichment: Map<Hex, Double>): Map<Hex, Double> =
    if (enrichment.values.all { it.isSafe() }) enrichment
    else enrichment.mapValues { (_, value) -> value.capped().coerceAtLeast(0.0) }

/** Endlich und innerhalb der Obergrenze. */
fun Double.isSafe(): Boolean = !isNaN() && this in -VALUE_CAP..VALUE_CAP

/** Ob irgendein Wert des Spielstands ungültig (NaN/∞) oder übergroß ist – auch in den geparkten Galaxien. */
fun GameState.hasUnsafeValues(): Boolean =
    !stardust.isSafe() || !elements.isSafe() || !darkMatter.isSafe() || !runStardust.isSafe() ||
        !totalStardust.isSafe() || !boostRemaining.isSafe() ||
        stars.values.any { !it.stored.isSafe() || !it.age.isSafe() } ||
        enrichment.values.any { !it.isSafe() } ||
        parked.values.any { it.hasUnsafeValues() }

fun GalaxyRun.hasUnsafeValues(): Boolean =
    !stardust.isSafe() || !elements.isSafe() || !runStardust.isSafe() || !boostRemaining.isSafe() ||
        !cometCooldown.isSafe() || !eventCooldown.isSafe() ||
        stars.values.any { !it.stored.isSafe() || !it.age.isSafe() } ||
        enrichment.values.any { !it.isSafe() }
