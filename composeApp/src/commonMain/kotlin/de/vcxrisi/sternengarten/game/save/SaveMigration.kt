package de.vcxrisi.sternengarten.game.save

import de.vcxrisi.sternengarten.game.engine.Balance
import de.vcxrisi.sternengarten.game.engine.hasUnsafeValues
import de.vcxrisi.sternengarten.game.engine.sanitized
import de.vcxrisi.sternengarten.game.model.GameState
import de.vcxrisi.sternengarten.game.model.Upgrade
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
 * Bringt gespeicherte Spielstände auf den aktuellen Balancing-Stand.
 *
 * Version 2: Dunkle Materie wächst mit der Kubikwurzel statt der Quadratwurzel, Dunkle Energie kostet ×10 je Stufe.
 * Stände, die unter dem alten Balancing über alle Grenzen gewachsen sind (bis hin zu ∞/NaN), werden normalisiert:
 * Dunkle Materie wird umgerechnet und gedeckelt, Dunkle Energie gekürzt und die laufende Galaxie neu begonnen.
 * Kristalle, Artefakte, Erfolge, Sternbilder, Kosmetik und Käufe bleiben unangetastet.
 */
object SaveMigration {
    const val CURRENT_BALANCE_VERSION = 2

    /** Ab hier gilt ein alter Spielstand als entgleist. */
    const val DERAILED_DARK_MATTER = 1e6
    const val DERAILED_DARK_ENERGY = 20
    const val DERAILED_STAR_LEVEL = 1000

    /** Obergrenzen nach dem Normalisieren. */
    const val NORMALIZED_DARK_MATTER_CAP = 1000.0
    const val NORMALIZED_DARK_ENERGY_CAP = 3

    fun migrate(state: GameState): Pair<GameState, RepairReport?> {
        if (state.balanceVersion >= CURRENT_BALANCE_VERSION) return state.sanitized() to null

        val derailed = state.hasUnsafeValues() ||
            state.darkMatter > DERAILED_DARK_MATTER ||
            state.level(Upgrade.DARK_ENERGY) > DERAILED_DARK_ENERGY ||
            state.stars.values.any { it.level > DERAILED_STAR_LEVEL }
        val clean = state.sanitized()
        if (!derailed) return clean.copy(balanceVersion = CURRENT_BALANCE_VERSION) to null

        // Umrechnung der Dunklen Materie: aus √ wird ∛, also x → x^(2/3), dazu eine feste Obergrenze.
        val darkMatter = min(clean.darkMatter.pow(2.0 / 3.0), NORMALIZED_DARK_MATTER_CAP)
        val darkEnergyBefore = clean.level(Upgrade.DARK_ENERGY)
        val darkEnergy = min(darkEnergyBefore, NORMALIZED_DARK_ENERGY_CAP)
        val upgrades = clean.upgrades.filterKeys { it.permanent } + (Upgrade.DARK_ENERGY to darkEnergy)

        // Die laufende Galaxie beginnt neu – wie ein Urknall ohne Belohnung.
        val fresh = GameState()
        val restarted = clean.copy(
            darkMatter = darkMatter,
            upgrades = upgrades.filterValues { it > 0 },
            elements = fresh.elements,
            stars = fresh.stars,
            enrichment = fresh.enrichment,
            runStardust = 0.0,
            runSupernovas = 0,
            comet = null,
            cometCooldown = fresh.cometCooldown,
            boostRemaining = 0.0,
            event = null,
            eventCooldown = fresh.eventCooldown,
            galaxyGoals = emptyList(),
            balanceVersion = CURRENT_BALANCE_VERSION,
        )
        val repaired = restarted.copy(stardust = Balance.startingStardust(restarted))
        val report = RepairReport(
            darkMatterBefore = clean.darkMatter,
            darkMatterAfter = darkMatter,
            darkEnergyBefore = darkEnergyBefore,
            darkEnergyAfter = darkEnergy,
            galaxyRestarted = true,
        )
        return repaired to report
    }
}
