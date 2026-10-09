package de.vcxrisi.sternengarten.game

import de.vcxrisi.sternengarten.game.engine.Balance
import de.vcxrisi.sternengarten.game.engine.GameEngine
import de.vcxrisi.sternengarten.game.engine.isSafe
import de.vcxrisi.sternengarten.game.model.GameState
import de.vcxrisi.sternengarten.game.model.Hex
import de.vcxrisi.sternengarten.game.model.StarType
import de.vcxrisi.sternengarten.game.model.Upgrade
import de.vcxrisi.sternengarten.ui.theme.formatNumber
import kotlin.math.log10
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Ein einfacher, gieriger Bot spielt zehn Galaxien je eine Stunde. Unter dem alten Balancing wuchs die
 * Dunkle Materie doppelt-exponentiell (ein echter Spielstand erreichte in Galaxie 6 ~10^137 und lief über).
 * Der Test stellt sicher, dass das Wachstum jetzt abbremst und alle Werte endlich bleiben.
 */
class BalanceSimulationTest {

    private val engine = GameEngine(Random(11))

    /** Sternarten, die der Bot pflanzt – teuerste zuerst; Sonderfälle (Riesen, Löcher, Wiegen) lässt er aus. */
    private val plantable = listOf(
        StarType.QUASAR, StarType.MAGNETAR, StarType.NEUTRON_STAR, StarType.PULSAR,
        StarType.BINARY, StarType.YELLOW_STAR, StarType.RED_DWARF,
    )

    private fun act(start: GameState): GameState {
        var state = start
        // Forschung kaufen, solange es geht.
        for (upgrade in Upgrade.entries.filter { !it.permanent }) {
            repeat(20) { engine.buyUpgrade(state, upgrade)?.let { state = it } ?: return@repeat }
        }
        // Leere Felder bepflanzen.
        for (hex in Hex.area(Balance.gardenRadius(state))) {
            if (hex in state.stars) continue
            val type = plantable.firstOrNull { engine.canPlant(state, hex, it) } ?: break
            state = engine.plant(state, hex, type) ?: state
        }
        // Sterne gleichmäßig verbessern: immer den schwächsten zuerst, in 10er-Schritten.
        repeat(200) {
            val weakest = state.stars.entries.filter { engine.canLevel(it.value.type) }.minByOrNull { it.value.level } ?: return@repeat
            state = engine.levelUp(state, weakest.key, 10) ?: return state
        }
        return state
    }

    @Test
    fun darkMatterGrowsSteadilyNotExplosively() {
        var state = GameState()
        val gains = ArrayList<Double>()
        val galaxySeconds = 3600.0
        val step = 2.0

        repeat(10) { galaxy ->
            var t = 0.0
            while (t < galaxySeconds) {
                if ((t % 20.0) == 0.0) state = act(state)
                state = engine.tick(state, step).state
                t += step
            }
            assertTrue(state.stardust.isSafe() && state.runStardust.isSafe(), "Galaxie ${galaxy + 1}: ${state.stardust}")

            val gain = Balance.darkMatterGain(state)
            gains += gain
            println(
                "Galaxie ${galaxy + 1}: Staub dieser Galaxie=${formatNumber(state.runStardust)}, " +
                    "DM-Gewinn=${formatNumber(gain)}, DM gesamt=${formatNumber(state.darkMatter + gain)}, " +
                    "Dunkle Energie=${state.level(Upgrade.DARK_ENERGY)}, höchste Stufe=${state.stars.values.maxOfOrNull { it.level }}",
            )
            state = engine.bigBang(state) ?: return@repeat
            for (upgrade in Upgrade.entries.filter { it.permanent }) {
                repeat(50) { engine.buyUpgrade(state, upgrade)?.let { state = it } ?: return@repeat }
            }
            state = engine.chooseGalaxy(state, state.lawChoices.first()) ?: state
        }

        assertTrue(gains.size >= 10, "Bot kam nicht durch alle Galaxien: $gains")
        // Altes Balancing (Stand des kaputten TestFlight-Spielstands): DM-Gewinn sprang um ~10^4 pro Galaxie und
        // die Sprünge wurden größer; nach 10 Galaxien lag die Dunkle Materie bei ~10^38.
        // Gefordert: kein Sprung über 10^2 je Galaxie, und gegen Ende werden die Sprünge klein (< 10^1).
        val orders = gains.filter { it >= 1.0 }.map { log10(it) }
        val steps = orders.zipWithNext { a, b -> b - a }
        assertTrue(steps.all { it < 2.0 }, "Größenordnung des DM-Gewinns springt zu stark: $steps")
        assertTrue(steps.takeLast(3).all { it < 1.0 }, "Wachstum bremst nicht ab: $steps")
        assertTrue(state.darkMatter < 1e12, "Dunkle Materie läuft davon: ${state.darkMatter}")
    }
}
