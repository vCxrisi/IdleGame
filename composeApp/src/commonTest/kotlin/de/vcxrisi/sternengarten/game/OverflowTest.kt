package de.vcxrisi.sternengarten.game

import de.vcxrisi.sternengarten.game.engine.Balance
import de.vcxrisi.sternengarten.game.engine.BoardAnalyzer
import de.vcxrisi.sternengarten.game.engine.GameEngine
import de.vcxrisi.sternengarten.game.engine.VALUE_CAP
import de.vcxrisi.sternengarten.game.engine.capped
import de.vcxrisi.sternengarten.game.engine.hasUnsafeValues
import de.vcxrisi.sternengarten.game.engine.isSafe
import de.vcxrisi.sternengarten.game.engine.sanitized
import de.vcxrisi.sternengarten.game.model.Comet
import de.vcxrisi.sternengarten.game.model.GalaxyKind
import de.vcxrisi.sternengarten.game.model.GalaxyRun
import de.vcxrisi.sternengarten.game.model.GameState
import de.vcxrisi.sternengarten.game.model.Hex
import de.vcxrisi.sternengarten.game.model.Star
import de.vcxrisi.sternengarten.game.model.StarType
import de.vcxrisi.sternengarten.game.model.Upgrade
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Riesige Werte dürfen nie zu ∞ oder NaN werden – genau das hatte einen TestFlight-Spielstand zerstört. */
class OverflowTest {

    private val engine = GameEngine(Random(3))

    /** Nachbau des Problems: riesige Stufe, riesige Dunkle Materie, ein Schwarzes Loch daneben. */
    private fun extreme(): GameState = GameState(
        stardust = VALUE_CAP,
        darkMatter = 1.4e137,
        upgrades = mapOf(Upgrade.DARK_ENERGY to 287, Upgrade.FUSION to 400),
        stars = mapOf(
            Hex.ORIGIN to Star(StarType.BLACK_HOLE, age = 10.0, stored = 1e299),
            Hex(1, 0) to Star(StarType.QUASAR, level = 20_000, age = 10.0),
            Hex(-1, 0) to Star(StarType.RED_DWARF, level = 5001, age = 10.0),
            Hex(0, 1) to Star(StarType.NEUTRON_STAR, level = 5001, age = 10.0),
        ),
    )

    @Test
    fun cappedHandlesNanAndInfinity() {
        assertEquals(0.0, Double.NaN.capped())
        assertEquals(VALUE_CAP, Double.POSITIVE_INFINITY.capped())
        assertEquals(-VALUE_CAP, Double.NEGATIVE_INFINITY.capped())
        assertEquals(42.0, 42.0.capped())
    }

    @Test
    fun sanitizedRepairsEveryDouble() {
        val broken = GameState(
            stardust = Double.NaN,
            elements = Double.POSITIVE_INFINITY,
            darkMatter = Double.NaN,
            runStardust = Double.POSITIVE_INFINITY,
            totalStardust = Double.NaN,
            stars = mapOf(Hex.ORIGIN to Star(StarType.BLACK_HOLE, stored = Double.POSITIVE_INFINITY, age = Double.NaN)),
            enrichment = mapOf(Hex(1, 0) to Double.NaN),
            parked = mapOf(
                GalaxyKind.FROST to GalaxyRun(
                    stardust = Double.NaN,
                    elements = Double.NEGATIVE_INFINITY,
                    runStardust = Double.POSITIVE_INFINITY,
                    boostRemaining = Double.NaN,
                    cometCooldown = Double.POSITIVE_INFINITY,
                    eventCooldown = Double.NaN,
                    stars = mapOf(Hex.ORIGIN to Star(StarType.RED_DWARF, stored = Double.NaN, age = Double.POSITIVE_INFINITY)),
                    enrichment = mapOf(Hex(1, 0) to Double.POSITIVE_INFINITY),
                ),
            ),
        )
        assertTrue(broken.hasUnsafeValues())
        val clean = broken.sanitized()
        assertEquals(0.0, clean.stardust)
        assertEquals(VALUE_CAP, clean.elements)
        assertEquals(0.0, clean.darkMatter)
        assertEquals(VALUE_CAP, clean.runStardust)
        assertEquals(VALUE_CAP, clean.stars.getValue(Hex.ORIGIN).stored)
        assertEquals(0.0, clean.stars.getValue(Hex.ORIGIN).age)
        assertEquals(0.0, clean.enrichment.getValue(Hex(1, 0)))

        // Die geparkte Galaxie wird genauso bereinigt.
        val run = clean.parked.getValue(GalaxyKind.FROST)
        assertEquals(0.0, run.stardust)
        assertEquals(0.0, run.elements)
        assertEquals(VALUE_CAP, run.runStardust)
        assertEquals(0.0, run.boostRemaining)
        assertEquals(VALUE_CAP, run.cometCooldown)
        assertEquals(0.0, run.eventCooldown)
        assertEquals(0.0, run.stars.getValue(Hex.ORIGIN).stored)
        assertEquals(VALUE_CAP, run.stars.getValue(Hex.ORIGIN).age)
        assertEquals(VALUE_CAP, run.enrichment.getValue(Hex(1, 0)))
        assertFalse(clean.hasUnsafeValues())

        // Auch wenn nur eine geparkte Galaxie kaputt ist, wird das erkannt.
        val parkedOnly = GameState(parked = broken.parked)
        assertTrue(parkedOnly.hasUnsafeValues())
        assertFalse(parkedOnly.sanitized().hasUnsafeValues())
    }

    @Test
    fun analysisStaysFiniteWithExtremeValues() {
        val analysis = BoardAnalyzer.analyze(extreme())
        assertTrue(analysis.totalRate.isSafe(), "totalRate=${analysis.totalRate}")
        assertTrue(analysis.globalMultiplier.isSafe())
        analysis.breakdown.values.forEach { assertTrue(it.rate.isSafe() && it.rate >= 0.0, "rate=${it.rate}") }
        analysis.blackHoleInflow.values.forEach { assertTrue(it.isSafe(), "inflow=$it") }
        assertTrue(Balance.levelMultiplier(20_000).isSafe())
    }

    @Test
    fun tickingNeverProducesNan() {
        var state = extreme()
        repeat(50) { state = engine.tick(state, 1.0).state }
        assertTrue(state.stardust.isSafe() && state.stardust > 0.0, "stardust=${state.stardust}")
        assertTrue(state.runStardust.isSafe() && state.totalStardust.isSafe())
        state.stars.values.forEach { assertTrue(it.stored.isSafe()) }
        val (offline, report) = engine.applyOffline(state, 3600.0)
        assertTrue(offline.stardust.isSafe() && report.stardust.isSafe())
    }

    @Test
    fun infiniteCostsAreSimplyUnaffordable() {
        val state = extreme()
        assertTrue(Balance.levelUpCost(state, state.stars.getValue(Hex(1, 0)), 100) > VALUE_CAP)
        assertNull(engine.levelUp(state, Hex(1, 0), 100))
    }

    @Test
    fun rewardsAtMaximumStayFinite() {
        val state = extreme().copy(comet = Comet(1, 0f, 0f, 1f, 1f, 5.0, meteor = true))
        val (caught, _) = engine.catchComet(state)!!
        assertTrue(caught.stardust.isSafe() || caught.sanitized().stardust.isSafe())
        val (released, amount) = engine.releaseBlackHole(state, Hex.ORIGIN)!!
        assertTrue(amount.isSafe())
        assertTrue(released.sanitized().stardust.isSafe())
        val (warped, warp) = engine.timeWarp(state, 8 * 3600.0)
        assertTrue(warp.isSafe() && warped.sanitized().stardust.isSafe())
    }
}
