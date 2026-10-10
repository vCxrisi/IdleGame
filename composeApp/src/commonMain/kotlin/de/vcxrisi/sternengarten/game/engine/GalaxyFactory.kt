package de.vcxrisi.sternengarten.game.engine

import de.vcxrisi.sternengarten.game.model.GalaxyKind
import de.vcxrisi.sternengarten.game.model.GalaxyLaw
import de.vcxrisi.sternengarten.game.model.GalaxyRun
import de.vcxrisi.sternengarten.game.model.GameState
import de.vcxrisi.sternengarten.game.model.Hex

/** Neue Galaxien und ihre Startfläche. Die einzige Stelle, die festlegt, was ein Neuanfang zurücksetzt. */
object GalaxyFactory {

    /**
     * Eine Galaxie wie neu: Startstaub und Startfläche ihrer Art, sonst die Default-Werte von [GalaxyRun].
     * Genutzt vom Urknall, von der Erschließung und von der Reparatur alter Spielstände – ein neues
     * Galaxie-Feld wird damit überall zurückgesetzt. [meta] liefert die permanente Forschung und die Käufe.
     */
    fun freshRun(
        meta: GameState,
        kind: GalaxyKind,
        law: GalaxyLaw,
        number: Int,
        name: String,
        lawChoices: List<GalaxyLaw>,
    ): GalaxyRun = GalaxyRun(
        stardust = Balance.startingStardust(meta, kind),
        law = law,
        galaxyName = name,
        galaxyNumber = number,
        lawChoices = lawChoices,
        ownedFields = Balance.startFields(meta, kind, law),
    )

    /**
     * Urnebel und Galaxie-Pionier: Jede Galaxie bekommt ihre (größere) Startfläche sofort dazu. Felder, die schon
     * gekauft waren und jetzt zur Startfläche gehören, zählen nicht mehr als gekauft – das nächste Feld wird billiger.
     */
    fun extendStartingFields(state: GameState): GameState {
        val (fields, bought) = extended(state, state.activeGalaxy, state.law, state.ownedFields)
        val parked = state.parked.mapValues { (kind, run) ->
            val (runFields, runBought) = extended(state, kind, run.law, run.ownedFields)
            run.copy(ownedFields = runFields, fieldsBought = runBought)
        }
        return state.copy(ownedFields = fields, fieldsBought = bought, parked = parked)
    }

    private fun extended(meta: GameState, kind: GalaxyKind, law: GalaxyLaw, owned: Set<Hex>): Pair<Set<Hex>, Int> {
        val fields = owned + Balance.startFields(meta, kind, law)
        return fields to (fields.size - Balance.startFieldCount(meta, kind, law)).coerceAtLeast(0)
    }
}
