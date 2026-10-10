package de.vcxrisi.sternengarten.game

import de.vcxrisi.sternengarten.game.engine.Balance
import de.vcxrisi.sternengarten.game.engine.BackgroundResult
import de.vcxrisi.sternengarten.game.engine.BridgeBlock
import de.vcxrisi.sternengarten.game.engine.GalaxyFactory
import de.vcxrisi.sternengarten.game.engine.GameEngine
import de.vcxrisi.sternengarten.game.engine.UnlockStatus
import de.vcxrisi.sternengarten.game.engine.hasUnsafeValues
import de.vcxrisi.sternengarten.game.engine.isSafe
import de.vcxrisi.sternengarten.game.model.GalaxyKind
import de.vcxrisi.sternengarten.game.model.GalaxyLaw
import de.vcxrisi.sternengarten.game.model.GameState
import de.vcxrisi.sternengarten.game.model.StarBridge
import de.vcxrisi.sternengarten.game.model.StarType
import de.vcxrisi.sternengarten.game.model.Upgrade
import de.vcxrisi.sternengarten.game.model.generationOf
import de.vcxrisi.sternengarten.game.model.hasRun
import de.vcxrisi.sternengarten.game.model.runKinds
import de.vcxrisi.sternengarten.game.model.runOf
import de.vcxrisi.sternengarten.game.model.withRun
import de.vcxrisi.sternengarten.ui.theme.formatDecimal
import de.vcxrisi.sternengarten.ui.theme.formatNumber
import kotlin.math.log10
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Mehrere Galaxien gleichzeitig. Ein gieriger Bot spielt alle Galaxien parallel – die Spirale im Vordergrund,
 * die übrigen über [de.vcxrisi.sternengarten.game.engine.GalaxyOrchestrator.tickBackground] – und lässt jede Galaxie
 * nach einer Stunde kollabieren. Derselbe Bot spielt im selben Test auch nur die Spiralgalaxie: Mehrere Galaxien
 * dürfen die Dunkle Materie nur um einen festen Faktor vermehren, nie das Wachstum selbst aufschaukeln.
 *
 * Aufgezeichneter DM-Gewinn je Runde (Seed 11, Schritte von 4 s, ein Zug je Galaxie und Minute):
 * - nur die Spirale: 19,1K · 756K · 12,1M · 22,1M · 89,8M · 133M · 340M · 480M · 728M · 919M (Summe 2,72Mrd)
 * - alle fünf ab Sekunde 0 mit Brückenring: 30,0K · 5,73M · 92,0M · 374M · 1,06Mrd · 1,26Mrd · 2,33Mrd · 3,35Mrd ·
 *   4,39Mrd · 2,69Mrd (Summe 15,6Mrd, das 5,7-Fache; Dunkle Materie am Ende 4,44Mrd)
 * - mit Freischaltung nach Zeitplan (Wartezeiten übersprungen): Frost, Glut, Polarlicht und Schatten entstehen
 *   nach Runde 1, 2, 3 und 4.
 */
class MultiGalaxySimulationTest {

    /** Ein Spieldurchlauf: Zustand, Wanduhr und der DM-Gewinn jeder Galaxie je Runde (ab 0). */
    private class Simulation(start: GameState) {
        val engine = GameEngine(Random(11))
        val galaxies = engine.galaxies
        var state = start
        var nowMs = 0L
        var round = 0
        val gains = LinkedHashMap<GalaxyKind, LinkedHashMap<Int, Double>>()

        fun gainsOf(kind: GalaxyKind): List<Double> = gains[kind]?.values?.toList().orEmpty()

        /** DM-Gewinn aller Galaxien in Runde [round]. */
        fun earned(round: Int): Double = gains.values.sumOf { it[round] ?: 0.0 }

        fun total(): Double = gains.values.sumOf { it.values.sum() }
    }

    /** Sternarten, die der Bot pflanzt – teuerste zuerst; Sonderfälle (Riesen, Löcher, Wiegen) lässt er aus. */
    private val plantable = listOf(
        StarType.QUASAR, StarType.MAGNETAR, StarType.NEUTRON_STAR, StarType.PULSAR,
        StarType.BINARY, StarType.YELLOW_STAR, StarType.RED_DWARF,
    )

    /** Permanente Forschung, die der Bot nach jeder Runde kauft – wie in [BalanceSimulationTest]. */
    private val permanentUpgrades = listOf(
        Upgrade.DARK_ENERGY, Upgrade.STARDUST_MEMORY, Upgrade.DEEP_SLEEP, Upgrade.PRIMORDIAL_NEBULA, Upgrade.COMET_LURE,
    )

    /** Ein Zug in der aktiven Galaxie: Gesetz wählen, Forschung, Felder, Sterne (die besondere Art zuerst), Stufen. */
    private fun act(engine: GameEngine, start: GameState): GameState {
        var state = start
        if (state.lawChoices.isNotEmpty()) state = engine.chooseGalaxy(state, state.lawChoices.first()) ?: return state
        for (upgrade in Upgrade.entries.filter { !it.permanent && !it.retired }) {
            repeat(20) { engine.buyUpgrade(state, upgrade)?.let { state = it } ?: return@repeat }
        }
        for (hex in Balance.FIELD_ORDER) {
            if (state.ownedFields.count { it !in state.stars } >= 2) break
            if (hex in state.ownedFields) continue
            state = engine.buyField(state, hex)?.first ?: break
        }
        val types = listOfNotNull(state.activeGalaxy.exclusiveStar) + plantable
        for (hex in Balance.FIELD_ORDER) {
            if (hex !in state.ownedFields || hex in state.stars) continue
            val type = types.firstOrNull { engine.canPlant(state, hex, it) } ?: break
            state = engine.plant(state, hex, type) ?: state
        }
        repeat(100) {
            val weakest = state.stars.entries.filter { engine.canLevel(it.value.type) }.minByOrNull { it.value.level } ?: return state
            state = engine.levelUp(state, weakest.key, 25) ?: return state
        }
        return state
    }

    /** Jede Galaxie kommt reihum dran; danach ist wieder die Spirale aktiv. */
    private fun actEverywhere(sim: Simulation) {
        for (kind in sim.state.runKinds()) {
            sim.state = sim.galaxies.switchTo(sim.state, kind) ?: sim.state
            sim.state = act(sim.engine, sim.state)
        }
        sim.state = sim.galaxies.switchTo(sim.state, GalaxyKind.SPIRAL) ?: sim.state
    }

    /** Eine Stunde: die aktive Galaxie im Vordergrund, die geparkten im Hintergrund, die Timer nach Wanduhr. */
    private fun playRound(sim: Simulation, check: (GameState, BackgroundResult) -> Unit = { _, _ -> }) {
        var t = 0.0
        while (t < ROUND_SECONDS) {
            if (t % ACT_SECONDS == 0.0) actEverywhere(sim)
            val result = sim.engine.tick(sim.state, STEP)
            val background = sim.galaxies.tickBackground(result.state, STEP, result.ownIncome)
            check(result.state, background)
            sim.nowMs += (STEP * 1000).toLong()
            sim.state = sim.galaxies.completeTimers(background.state, sim.nowMs).first
            t += STEP
        }
    }

    /** Urknall in jeder Galaxie, die es kann; der DM-Gewinn wird je Galaxieart notiert. */
    private fun collapseEverywhere(sim: Simulation) {
        for (kind in sim.state.runKinds()) {
            sim.state = sim.galaxies.switchTo(sim.state, kind) ?: sim.state
            if (sim.state.lawChoices.isNotEmpty()) continue
            val gain = Balance.darkMatterGain(sim.state)
            sim.gains.getOrPut(kind) { LinkedHashMap() }[sim.round] = gain
            val collapsed = sim.engine.bigBang(sim.state) ?: continue
            sim.state = sim.engine.chooseGalaxy(collapsed, collapsed.lawChoices.first()) ?: collapsed
        }
        sim.state = sim.galaxies.switchTo(sim.state, GalaxyKind.SPIRAL) ?: sim.state
        sim.round++
    }

    private fun buyPermanentUpgrades(sim: Simulation) {
        for (upgrade in permanentUpgrades) {
            repeat(50) { sim.engine.buyUpgrade(sim.state, upgrade)?.let { sim.state = it } ?: return@repeat }
        }
    }

    private fun assertSafe(state: GameState, label: String) {
        assertTrue(!state.hasUnsafeValues() && state.darkMatter.isSafe(), "$label: Werte nicht endlich")
    }

    private fun printTable(title: String, sims: List<Pair<String, Simulation>>) {
        println(title)
        for (round in 0 until sims.maxOf { it.second.round }) {
            val cells = sims.joinToString(" | ") { (name, sim) ->
                val perKind = sim.gains.entries.joinToString(" ") { (kind, gains) ->
                    "${kind.shortName}=${gains[round]?.let { formatNumber(it) } ?: "–"}"
                }
                "$name: $perKind"
            }
            println("Runde ${round + 1}: $cells")
        }
    }

    /** Faktoren von Runde zu Runde. */
    private fun ratios(gains: List<Double>): List<Double> = gains.zipWithNext { a, b -> if (a >= 1.0) b / a else 0.0 }

    @Test
    fun fiveParallelGalaxiesStayBounded() {
        val rounds = 10

        // Ein Spieler mit nur der Spiralgalaxie, gleicher Bot und gleicher Takt.
        val single = Simulation(GameState())
        repeat(rounds) { round ->
            playRound(single)
            collapseEverywhere(single)
            buyPermanentUpgrades(single)
            assertSafe(single.state, "Einzel, Runde ${round + 1}")
        }

        // Schlimmster Fall: alle fünf Galaxien ab der ersten Sekunde, volle Brückenbaukunst, ein Ring aus Brücken.
        val meta = GameState(upgrades = mapOf(Upgrade.BRIDGE_CRAFT to 4))
        fun fresh(kind: GalaxyKind) = GalaxyFactory.freshRun(meta, kind, GalaxyLaw.NORMAL, 1, kind.displayName, emptyList())
        val kinds = GalaxyKind.entries
        val multi = Simulation(
            meta.withRun(GalaxyKind.SPIRAL, fresh(GalaxyKind.SPIRAL)).copy(
                parked = kinds.filter { it != GalaxyKind.SPIRAL }.associateWith { fresh(it) },
                bridges = kinds.map { StarBridge(it, kinds[(it.ordinal + 1) % kinds.size], 0, 0, built = true) },
            ),
        )
        var flowChecks = 0
        repeat(rounds) { round ->
            playRound(multi) { before, background ->
                // Keine Brücke trägt mehr als ihre Obergrenze am eigenen Einkommen des Ziels.
                for ((kind, inflow) in background.inflow) {
                    val law = before.runOf(kind)!!.law
                    val own = background.rates[kind] ?: 0.0
                    assertTrue(inflow <= Balance.bridgeCap(before) * law.bridgeMult * own * (1 + 1e-9) + 1e-9, "Brücke nach $kind: $inflow > $own")
                    flowChecks++
                }
            }
            collapseEverywhere(multi)
            buyPermanentUpgrades(multi)
            assertSafe(multi.state, "Runde ${round + 1}")
        }
        printTable("DM-Gewinn je Runde (Seed 11)", listOf("Einzel" to single, "Parallel" to multi))
        println("Parallel je Runde: " + (0 until rounds).joinToString(" · ") { formatNumber(multi.earned(it)) })
        println(
            "Summe: Einzel=${formatNumber(single.total())}, Parallel=${formatNumber(multi.total())}, " +
                "Faktor=${formatDecimal(multi.total() / single.total(), 1)}, DM=${formatNumber(multi.state.darkMatter)}",
        )

        assertTrue(flowChecks > 0, "Die Brücken flossen nie")
        assertEquals(kinds.toSet(), multi.gains.keys)
        // Der größte Sprung der einzelnen Galaxie (der erste Urknall macht aus 0 Dunkler Materie viel) ist die Messlatte.
        val maxSingleRatio = ratios(single.gainsOf(GalaxyKind.SPIRAL)).max()
        for (kind in kinds) {
            val gains = multi.gainsOf(kind)
            assertEquals(rounds, gains.size, "$kind kollabierte nicht in jeder Runde: $gains")
            assertTrue(gains.last() >= 1.0, "$kind bringt in der letzten Runde nichts: $gains")
            // Ab Runde 2: Runde 1 ist der Anlauf, in dem teure Galaxien noch kaum etwas bringen.
            val fromRound2 = gains.drop(1)
            val steps = fromRound2.filter { it >= 1.0 }.map { log10(it) }.zipWithNext { a, b -> b - a }
            assertTrue(steps.all { it < 2.0 }, "$kind: Größenordnung des DM-Gewinns springt zu stark: $steps")
            assertTrue(steps.takeLast(3).all { it < 1.0 }, "$kind: Wachstum bremst nicht ab: $steps")
            val kindRatios = ratios(fromRound2)
            assertTrue(
                kindRatios.all { it <= maxSingleRatio + 1.0 },
                "$kind wächst schneller als eine einzelne Galaxie: $kindRatios, Einzel höchstens $maxSingleRatio",
            )
        }
        assertTrue(multi.earned(rounds - 1) / multi.earned(rounds - 2) < 5.0, "Letzte Runde springt: ${multi.earned(rounds - 1)}")
        assertTrue(multi.total() <= 60.0 * single.total(), "Fünf Galaxien bringen mehr als das 60-Fache von einer")
        assertTrue(multi.state.darkMatter < 1e12, "Dunkle Materie läuft davon: ${multi.state.darkMatter}")
    }

    @Test
    fun galaxiesUnlockInOrderWithTimers() {
        val sim = Simulation(GameState(crystals = 5_000))
        val startedInRound = LinkedHashMap<GalaxyKind, Int>()
        var crystalsForSkips = 0
        var skipCosts = 0

        fun skip(result: Pair<GameState, Int>?) {
            val (next, cost) = result ?: return
            assertEquals(sim.state.crystals - cost, next.crystals)
            crystalsForSkips += sim.state.crystals - next.crystals
            skipCosts += cost
            sim.state = sim.galaxies.completeTimers(next, sim.nowMs).first
        }

        for (round in 0 until 14) {
            playRound(sim)
            collapseEverywhere(sim)
            // Erst erschließen, dann Dunkle Energie kaufen; Wartezeiten mit Kristallen überspringen.
            val previous = sim.galaxies.nextUnlockable(sim.state)?.let { GalaxyKind.entries.getOrNull(it.ordinal - 1) }
            sim.galaxies.startUnlock(sim.state, sim.nowMs)?.let { (next, event) ->
                assertTrue(previous != null && (sim.state.generationOf(previous) ?: 0) >= 2, "${event.kind} vor dem Urknall in $previous")
                startedInRound[event.kind] = round
                sim.state = next
            }
            skip(sim.galaxies.skipUnlock(sim.state, sim.nowMs))
            // Brücken im Ring bauen, sobald eine bezahlbar ist und keine andere im Bau.
            val bridge = GalaxyKind.entries.map { it to GalaxyKind.entries[(it.ordinal + 1) % GalaxyKind.entries.size] }
                .firstOrNull { (from, to) -> sim.galaxies.bridgeBlock(sim.state, from, to) == BridgeBlock.NONE }
            if (bridge != null) {
                sim.state = sim.galaxies.buildBridge(sim.state, bridge.first, bridge.second, sim.nowMs)!!.first
                skip(sim.galaxies.skipBridge(sim.state, bridge.first, bridge.second, sim.nowMs))
            }
            buyPermanentUpgrades(sim)
            assertSafe(sim.state, "Runde ${round + 1}")
            val allCollapsed = GalaxyKind.entries.all { sim.gainsOf(it).isNotEmpty() }
            if (sim.galaxies.unlockStatus(sim.state) == UnlockStatus.ALL_UNLOCKED && allCollapsed) break
        }
        println("Erschlossen in Runde: ${startedInRound.mapValues { it.value + 1 }}, Kristalle für Wartezeiten: $crystalsForSkips")
        printTable("Freischaltung (Seed 11)", listOf("Bot" to sim))

        assertTrue(GalaxyKind.entries.all { sim.state.hasRun(it) }, "nicht alle erschlossen: ${sim.state.runKinds()}")
        assertEquals(GalaxyKind.entries.drop(1), startedInRound.keys.toList(), "Reihenfolge")
        assertTrue(startedInRound.values.zipWithNext().all { (a, b) -> a < b }, "nie zwei Erschließungen in einer Runde: $startedInRound")
        assertTrue(GalaxyKind.entries.all { sim.gainsOf(it).isNotEmpty() }, "jede Galaxie hatte einen Urknall: ${sim.gains}")
        assertTrue(skipCosts > 0)
        assertEquals(skipCosts, crystalsForSkips)
        assertTrue(sim.state.darkMatter < 1e12, "Dunkle Materie läuft davon: ${sim.state.darkMatter}")
    }

    private companion object {
        const val STEP = 4.0
        const val ACT_SECONDS = 60.0
        const val ROUND_SECONDS = 3600.0
    }
}
