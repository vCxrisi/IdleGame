package de.vcxrisi.sternengarten.game.save

import de.vcxrisi.sternengarten.game.engine.Balance
import de.vcxrisi.sternengarten.game.engine.GalaxyFactory
import de.vcxrisi.sternengarten.game.engine.hasUnsafeValues
import de.vcxrisi.sternengarten.game.engine.sanitized
import de.vcxrisi.sternengarten.game.model.GameState
import de.vcxrisi.sternengarten.game.model.Hex
import de.vcxrisi.sternengarten.game.model.Upgrade
import de.vcxrisi.sternengarten.game.model.hasRun
import de.vcxrisi.sternengarten.game.model.withRun
import kotlin.math.min
import kotlin.math.pow

/** Was beim Reparieren eines entgleisten Spielstands geändert wurde – für den Hinweis-Dialog. */
data class RepairReport(
    val darkMatterBefore: Double,
    val darkMatterAfter: Double,
    val darkEnergyBefore: Int,
    val darkEnergyAfter: Int,
    val galaxyRestarted: Boolean,
)

/**
 * Bringt gespeicherte Spielstände auf den aktuellen Stand – Version für Version, damit jede Stufe nur die
 * Stände sieht, für die sie geschrieben wurde.
 *
 * Version 2: Dunkle Materie wächst mit der Kubikwurzel statt der Quadratwurzel, Dunkle Energie kostet ×10 je Stufe.
 * Stände, die unter dem alten Balancing über alle Grenzen gewachsen sind (bis hin zu ∞/NaN), werden normalisiert:
 * Dunkle Materie wird umgerechnet und gedeckelt, Dunkle Energie gekürzt und die laufende Galaxie neu begonnen.
 * Kristalle, Artefakte, Erfolge, Sternbilder, Kosmetik und Käufe bleiben unangetastet.
 *
 * Version 3: Felder statt Ringe. Der bisherige Garten gehört dem Spieler als Felder, die Nebelausdehnung entfällt.
 * Repariert wird nur auf dem Weg zu Version 2 – ein gesunder v2-Stand mit viel Dunkler Materie gilt nie als entgleist.
 */
object SaveMigration {
    const val CURRENT_BALANCE_VERSION = 3

    /** Ab hier gilt ein alter Spielstand als entgleist. */
    const val DERAILED_DARK_MATTER = 1e6
    const val DERAILED_DARK_ENERGY = 20
    const val DERAILED_STAR_LEVEL = 1000

    /** Obergrenzen nach dem Normalisieren. */
    const val NORMALIZED_DARK_MATTER_CAP = 1000.0
    const val NORMALIZED_DARK_ENERGY_CAP = 3

    /** Läuft bei jedem Laden, noch vor der Offline-Simulation. Einen [RepairReport] gibt es nur aus Version 2. */
    fun migrate(state: GameState): Pair<GameState, RepairReport?> {
        var migrated = state
        var report: RepairReport? = null
        if (migrated.balanceVersion < 2) {
            val (repaired, repair) = migrateToV2(migrated)
            migrated = repaired
            report = repair
        }
        if (migrated.balanceVersion < 3) migrated = migrateToV3(migrated)
        return normalizeGalaxies(migrated).sanitized() to report
    }

    private fun migrateToV2(state: GameState): Pair<GameState, RepairReport?> {
        val derailed = state.hasUnsafeValues() ||
            state.darkMatter > DERAILED_DARK_MATTER ||
            state.level(Upgrade.DARK_ENERGY) > DERAILED_DARK_ENERGY ||
            state.stars.values.any { it.level > DERAILED_STAR_LEVEL }
        val clean = state.sanitized()
        if (!derailed) return clean.copy(balanceVersion = 2) to null

        // Umrechnung der Dunklen Materie: aus √ wird ∛, also x → x^(2/3), dazu eine feste Obergrenze.
        val darkMatter = min(clean.darkMatter.pow(2.0 / 3.0), NORMALIZED_DARK_MATTER_CAP)
        val darkEnergyBefore = clean.level(Upgrade.DARK_ENERGY)
        val darkEnergy = min(darkEnergyBefore, NORMALIZED_DARK_ENERGY_CAP)
        val upgrades = clean.upgrades.filterKeys { it.permanent } + (Upgrade.DARK_ENERGY to darkEnergy)
        val normalized = clean.copy(darkMatter = darkMatter, upgrades = upgrades.filterValues { it > 0 }, balanceVersion = 2)

        // Die laufende Galaxie beginnt neu – wie ein Urknall ohne Belohnung. Name, Zyklus und Naturgesetz bleiben.
        val fresh = GalaxyFactory.freshRun(
            normalized, normalized.activeGalaxy, clean.law, clean.galaxyNumber, clean.galaxyName, clean.lawChoices,
        )
        val report = RepairReport(
            darkMatterBefore = clean.darkMatter,
            darkMatterAfter = darkMatter,
            darkEnergyBefore = darkEnergyBefore,
            darkEnergyAfter = darkEnergy,
            galaxyRestarted = true,
        )
        return normalized.withRun(normalized.activeGalaxy, fresh) to report
    }

    /**
     * Aus dem Ring-Garten werden Felder: Alles im bisherigen Radius (Nebelausdehnung, Urnebel, Naturgesetz) und jedes
     * Sternfeld gehört dem Spieler, dazu die neue Startfläche. Was darüber hinausgeht, zählt als gekauft und bestimmt
     * den Preis des nächsten Feldes. Die Nebelausdehnung selbst entfällt.
     */
    private fun migrateToV3(state: GameState): GameState {
        val oldRadius = (Balance.BASE_RADIUS + state.level(Upgrade.NEBULA_EXPANSION) +
            state.level(Upgrade.PRIMORDIAL_NEBULA) + state.law.radiusDelta)
            .coerceIn(Balance.MIN_RADIUS, Balance.MAX_RADIUS)
        val garden = state.copy(
            ownedFields = Hex.area(oldRadius).toSet() + state.stars.keys,
            upgrades = state.upgrades - Upgrade.NEBULA_EXPANSION,
            balanceVersion = 3,
        )
        return GalaxyFactory.extendStartingFields(garden)
    }

    /**
     * Hält die Galaxien in sich stimmig; läuft bei jedem Laden. Idempotent, und ohne Kopie, wenn schon alles stimmt:
     * - Die aktive Galaxie steht nicht zusätzlich in `parked`.
     * - Jedes Sternfeld gehört seiner Galaxie.
     * - Eine Erschließung entfällt, sobald es die Galaxie schon gibt.
     * - Brücken verbinden zwei verschiedene, bestehende Galaxien, höchstens eine je Ziel.
     */
    fun normalizeGalaxies(state: GameState): GameState {
        val normalized = state.copy(
            ownedFields = state.ownedFields + state.stars.keys,
            parked = state.parked
                .filterKeys { it != state.activeGalaxy }
                .mapValues { (_, run) -> run.copy(ownedFields = run.ownedFields + run.stars.keys) },
            unlock = state.unlock?.takeUnless { state.hasRun(it.kind) },
            bridges = state.bridges
                .filter { it.from != it.to && state.hasRun(it.from) && state.hasRun(it.to) }
                .distinctBy { it.to },
        )
        return if (normalized == state) state else normalized
    }
}
