package de.vcxrisi.sternengarten.game

import de.vcxrisi.sternengarten.game.engine.Balance
import de.vcxrisi.sternengarten.game.engine.BoardAnalyzer
import de.vcxrisi.sternengarten.game.engine.GameEngine
import de.vcxrisi.sternengarten.game.engine.GameEvent
import de.vcxrisi.sternengarten.game.model.ConstellationKind
import de.vcxrisi.sternengarten.game.model.GalaxyLaw
import de.vcxrisi.sternengarten.game.model.GameState
import de.vcxrisi.sternengarten.game.model.Hex
import de.vcxrisi.sternengarten.game.model.Star
import de.vcxrisi.sternengarten.game.model.StarType
import de.vcxrisi.sternengarten.game.model.Upgrade
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameEngineTest {

    private val engine = GameEngine(Random(42))

    /** Erwachsene Sterne, damit die Geburtsphase die Rechnung nicht verfälscht. */
    private fun adult(type: StarType, level: Int = 1) = Star(type, level = level, age = Balance.PROTO_SECONDS)

    private fun board(vararg stars: Pair<Hex, Star>) = GameState(stars = stars.toMap())

    private fun rateOf(state: GameState, hex: Hex) = BoardAnalyzer.analyze(state).breakdown.getValue(hex).rate

    private fun assertClose(expected: Double, actual: Double) =
        assertTrue(abs(expected - actual) < 1e-6 * maxOf(1.0, abs(expected)), "expected $expected but was $actual")

    @Test
    fun hexAreaHasExpectedSize() {
        assertEquals(1, Hex.area(0).size)
        assertEquals(7, Hex.area(1).size)
        assertEquals(19, Hex.area(2).size)
        assertEquals(91, Hex.area(5).size)
        assertEquals(12, Hex.ring(2).size)
    }

    @Test
    fun plantingCostsStardustAndRespectsGarden() {
        val start = GameState(stardust = 100.0)
        val planted = assertNotNull(engine.plant(start, Hex.ORIGIN, StarType.RED_DWARF))
        assertClose(90.0, planted.stardust)
        assertNull(engine.plant(planted, Hex.ORIGIN, StarType.RED_DWARF), "Feld ist belegt")
        assertNull(engine.plant(planted, Hex(5, 0), StarType.RED_DWARF), "außerhalb des Gartens")
        assertNull(engine.plant(planted, Hex(1, 0), StarType.YELLOW_STAR), "noch gesperrt")
        // Zweiter Roter Zwerg wird teurer.
        val second = assertNotNull(engine.plant(planted, Hex(1, 0), StarType.RED_DWARF))
        assertClose(90.0 - 10.0 * 1.15, second.stardust)
    }

    @Test
    fun yellowStarWarmsNeighbors() {
        val alone = board(Hex.ORIGIN to adult(StarType.RED_DWARF))
        val warmed = board(Hex.ORIGIN to adult(StarType.RED_DWARF), Hex(1, 0) to adult(StarType.YELLOW_STAR))
        assertClose(rateOf(alone, Hex.ORIGIN) * 1.25, rateOf(warmed, Hex.ORIGIN))
    }

    @Test
    fun blueGiantSuffersFromCrowding() {
        val free = board(Hex.ORIGIN to adult(StarType.BLUE_GIANT))
        val crowded = board(
            Hex.ORIGIN to adult(StarType.BLUE_GIANT),
            // Weder Linie noch Dreieck, damit kein Sternbild mitspielt.
            Hex(1, 0) to adult(StarType.RED_DWARF),
            Hex(-1, 1) to adult(StarType.RED_DWARF),
        )
        assertTrue(BoardAnalyzer.findConstellations(crowded).isEmpty())
        assertClose(rateOf(free, Hex.ORIGIN) * 0.7, rateOf(crowded, Hex.ORIGIN))
        // In der Großen Leere gibt es keinen Abzug.
        val void = crowded.copy(law = GalaxyLaw.GREAT_VOID)
        assertClose(rateOf(free.copy(law = GalaxyLaw.GREAT_VOID), Hex.ORIGIN), rateOf(void, Hex.ORIGIN))
    }

    @Test
    fun binariesDoubleAsPair() {
        val single = board(Hex.ORIGIN to adult(StarType.BINARY))
        val pair = board(Hex.ORIGIN to adult(StarType.BINARY), Hex(0, 1) to adult(StarType.BINARY))
        assertClose(rateOf(single, Hex.ORIGIN) * 2.0, rateOf(pair, Hex.ORIGIN))
    }

    @Test
    fun pulsarBoostsAlongAxesOnly() {
        val state = board(
            Hex.ORIGIN to adult(StarType.PULSAR),
            Hex(3, 0) to adult(StarType.RED_DWARF), // im Strahl, 3 Felder entfernt
            Hex(2, 1) to adult(StarType.RED_DWARF), // nicht auf einer Achse
        )
        val analysis = BoardAnalyzer.analyze(state)
        assertClose(Balance.PULSAR_AURA, analysis.breakdown.getValue(Hex(3, 0)).aura)
        assertClose(0.0, analysis.breakdown.getValue(Hex(2, 1)).aura)
    }

    @Test
    fun blackHoleDrainsNeighborsAndReleasesTriple() {
        var state = board(Hex.ORIGIN to adult(StarType.BLACK_HOLE), Hex(1, 0) to adult(StarType.RED_DWARF))
        val analysis = BoardAnalyzer.analyze(state)
        val dwarf = analysis.breakdown.getValue(Hex(1, 0))
        assertClose(0.5, dwarf.drained)
        assertClose(dwarf.rate, analysis.blackHoleInflow.getValue(Hex.ORIGIN))

        state = engine.tick(state, 10.0).state
        val stored = state.stars.getValue(Hex.ORIGIN).stored
        assertClose(dwarf.rate * 10.0, stored)
        val (released, amount) = assertNotNull(engine.releaseBlackHole(state, Hex.ORIGIN))
        assertClose(stored * 3.0, amount)
        assertEquals(0.0, released.stars.getValue(Hex.ORIGIN).stored)
    }

    @Test
    fun constellationsAreDetected() {
        val line = board(
            Hex(0, 0) to adult(StarType.RED_DWARF),
            Hex(1, 0) to adult(StarType.RED_DWARF),
            Hex(2, 0) to adult(StarType.RED_DWARF),
            Hex(3, 0) to adult(StarType.RED_DWARF),
        )
        val kinds = BoardAnalyzer.findConstellations(line).map { it.kind }.toSet()
        assertEquals(setOf(ConstellationKind.TRIO, ConstellationKind.RED_THREAD), kinds)

        val triangle = board(
            Hex(0, 0) to adult(StarType.RED_DWARF),
            Hex(1, 0) to adult(StarType.RED_DWARF),
            Hex(1, -1) to adult(StarType.RED_DWARF),
        )
        val triangles = BoardAnalyzer.findConstellations(triangle).filter { it.kind == ConstellationKind.TRIANGULUM }
        assertEquals(1, triangles.size, "jedes Dreieck zählt nur einmal")

        val crown = GameState(
            stars = (Hex.ORIGIN.neighbors().associateWith { adult(StarType.RED_DWARF) }) +
                (Hex.ORIGIN to adult(StarType.YELLOW_STAR)),
        )
        val crownKinds = BoardAnalyzer.findConstellations(crown).map { it.kind }.toSet()
        assertTrue(ConstellationKind.CROWN in crownKinds)
        assertTrue(ConstellationKind.SUN_CROWN in crownKinds)

        val rainbow = board(
            Hex(0, 0) to adult(StarType.RED_DWARF),
            Hex(0, 1) to adult(StarType.YELLOW_STAR),
            Hex(0, 2) to adult(StarType.BLUE_GIANT),
            Hex(0, 3) to adult(StarType.BINARY),
        )
        assertTrue(BoardAnalyzer.findConstellations(rainbow).any { it.kind == ConstellationKind.RAINBOW })
    }

    @Test
    fun constellationBonusDoesNotStackForSameKind() {
        // Fünf in einer Linie: der mittlere Stern liegt in drei Dreigestirnen, bekommt den Bonus aber nur einmal.
        val state = GameState(stars = (0 until 5).associate { Hex(it, 0) to adult(StarType.RED_DWARF) })
        val middle = BoardAnalyzer.analyze(state).breakdown.getValue(Hex(2, 0))
        assertClose(ConstellationKind.TRIO.memberBonus + ConstellationKind.RED_THREAD.memberBonus, middle.constellation)
    }

    @Test
    fun discoveringConstellationRaisesGlobalBonus() {
        val state = board(
            Hex(0, 0) to adult(StarType.RED_DWARF),
            Hex(1, 0) to adult(StarType.RED_DWARF),
            Hex(2, 0) to adult(StarType.RED_DWARF),
        )
        val result = engine.tick(state, 0.1)
        assertTrue(ConstellationKind.TRIO in result.state.discovered)
        assertTrue(result.events.any { it is GameEvent.Discovered && it.kind == ConstellationKind.TRIO })
        assertClose(1.0 + Balance.DISCOVERY_BONUS * result.state.discovered.size, Balance.globalMultiplier(result.state))
    }

    @Test
    fun blueGiantExplodesAndFertilizesNeighbors() {
        val lifespan = StarType.BLUE_GIANT.lifespan!!
        val state = board(Hex.ORIGIN to Star(StarType.BLUE_GIANT, level = 5, age = lifespan - 0.05))
        val result = engine.tick(state, 0.1)
        assertFalse(Hex.ORIGIN in result.state.stars)
        assertEquals(1, result.state.supernovaCount)
        assertClose(2.0, result.state.elements) // 1 + (5 − 1) · 0,25
        for (n in Hex.ORIGIN.neighbors()) assertClose(Balance.ENRICHMENT_PER_SUPERNOVA, result.state.enrichment.getValue(n))
        assertTrue(result.events.any { it is GameEvent.Supernova })
    }

    @Test
    fun yellowStarBecomesWhiteDwarf() {
        val lifespan = StarType.YELLOW_STAR.lifespan!!
        val state = board(Hex.ORIGIN to Star(StarType.YELLOW_STAR, age = lifespan - 0.05))
        val result = engine.tick(state, 0.1)
        assertTrue(result.state.stars.getValue(Hex.ORIGIN).whiteDwarf)
    }

    @Test
    fun offlineProgressIsCapped() {
        val state = board(Hex.ORIGIN to adult(StarType.RED_DWARF))
        val rate = BoardAnalyzer.analyze(state).totalRate
        val (after, report) = engine.applyOffline(state, 100 * 3600.0)
        assertClose(Balance.BASE_OFFLINE_SECONDS, report.cappedSeconds)
        assertClose(rate * Balance.BASE_OFFLINE_SECONDS, report.stardust)
        assertClose(state.stardust + report.stardust, after.stardust)
    }

    @Test
    fun timeDilationTriplesOfflineGains() {
        val state = board(Hex.ORIGIN to adult(StarType.RED_DWARF)).copy(law = GalaxyLaw.TIME_DILATION)
        val (_, report) = engine.applyOffline(state, 600.0)
        assertClose(BoardAnalyzer.analyze(state).totalRate * 600.0 * 3.0, report.stardust)
    }

    @Test
    fun bigBangKeepsMetaProgressAndOffersLaws() {
        val state = GameState(
            runStardust = 4_000_000.0,
            darkMatter = 1.0,
            stars = mapOf(Hex.ORIGIN to adult(StarType.RED_DWARF)),
            upgrades = mapOf(Upgrade.STELLAR_WIND to 3, Upgrade.DARK_ENERGY to 1),
            discovered = setOf(ConstellationKind.TRIO),
            unlocked = setOf(StarType.RED_DWARF, StarType.YELLOW_STAR),
        )
        assertEquals(2.0, Balance.darkMatterGain(state))
        val reset = assertNotNull(engine.bigBang(state))
        assertClose(3.0, reset.darkMatter)
        assertTrue(reset.stars.isEmpty())
        assertEquals(mapOf(Upgrade.DARK_ENERGY to 1), reset.upgrades)
        assertEquals(state.discovered, reset.discovered)
        assertEquals(state.unlocked, reset.unlocked)
        assertEquals(3, reset.lawChoices.size)
        assertFalse(GalaxyLaw.NORMAL in reset.lawChoices)

        // Solange gewählt wird, steht die Zeit still.
        assertEquals(reset, engine.tick(reset, 10.0).state)

        val next = assertNotNull(engine.chooseGalaxy(reset, reset.lawChoices.first()))
        assertEquals(reset.lawChoices.first(), next.law)
        assertEquals(2, next.galaxyNumber)
        assertTrue(next.lawChoices.isEmpty())
    }

    @Test
    fun bigBangRequiresEnoughStardust() {
        assertNull(engine.bigBang(GameState(runStardust = 500_000.0)))
    }

    @Test
    fun upgradesRespectCurrencyAndMaxLevel() {
        val rich = GameState(stardust = 1e12)
        var state = rich
        repeat(3) { state = assertNotNull(engine.buyUpgrade(state, Upgrade.NEBULA_EXPANSION)) }
        assertEquals(5, Balance.gardenRadius(state))
        assertNull(engine.buyUpgrade(state, Upgrade.NEBULA_EXPANSION), "Maximalstufe erreicht")
        assertNull(engine.buyUpgrade(rich, Upgrade.FUSION), "kostet Elemente")
    }

    @Test
    fun cometEventuallyAppearsAndCanBeCaught() {
        var state = GameState(cometCooldown = 0.5)
        state = engine.tick(state, 1.0).state
        assertNotNull(state.comet)
        val (caught, event) = assertNotNull(engine.catchComet(state))
        assertNull(caught.comet)
        assertTrue(event is GameEvent.CometStardust || event is GameEvent.CometBoost)
    }

    @Test
    fun starTypesUnlockWithProgress() {
        val state = GameState(stardust = StarType.YELLOW_STAR.unlockAt)
        val result = engine.tick(state, 0.01)
        assertTrue(StarType.YELLOW_STAR in result.state.unlocked)
        assertTrue(result.events.any { it is GameEvent.Unlocked && it.type == StarType.YELLOW_STAR })
    }
}
