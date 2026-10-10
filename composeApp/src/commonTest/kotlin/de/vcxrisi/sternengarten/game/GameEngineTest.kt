package de.vcxrisi.sternengarten.game

import de.vcxrisi.sternengarten.game.engine.Balance
import de.vcxrisi.sternengarten.game.engine.BoardAnalyzer
import de.vcxrisi.sternengarten.game.engine.GameEngine
import de.vcxrisi.sternengarten.game.engine.GameEvent
import de.vcxrisi.sternengarten.game.model.ConstellationKind
import de.vcxrisi.sternengarten.game.model.GalaxyKind
import de.vcxrisi.sternengarten.game.model.GalaxyLaw
import de.vcxrisi.sternengarten.game.model.GalaxyRun
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
        assertNull(engine.plant(planted, Hex(3, 0), StarType.RED_DWARF), "Grenzfeld, noch nicht freigekauft")
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
        // Fünf in einer Linie: der mittlere Stern liegt in drei Dreigestirnen und zwei Roten Fäden,
        // bekommt jeden Bonus aber nur einmal (plus die Himmelsleiter).
        val state = GameState(stars = (0 until 5).associate { Hex(it, 0) to adult(StarType.RED_DWARF) })
        val middle = BoardAnalyzer.analyze(state).breakdown.getValue(Hex(2, 0))
        assertClose(
            ConstellationKind.TRIO.memberBonus + ConstellationKind.RED_THREAD.memberBonus + ConstellationKind.LADDER.memberBonus,
            middle.constellation,
        )
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
        val frost = GalaxyRun(
            stardust = 4_200.0,
            stars = mapOf(Hex(1, 0) to adult(StarType.YELLOW_STAR)),
            runUpgrades = mapOf(Upgrade.STELLAR_WIND to 1),
            law = GalaxyLaw.ENTROPY,
            galaxyNumber = 3,
            runStardust = 9e6,
        )
        val state = GameState(
            runStardust = 8_000_000.0, // ∛8 = 2 Dunkle Materie
            darkMatter = 1.0,
            stars = mapOf(Hex.ORIGIN to adult(StarType.RED_DWARF)),
            upgrades = mapOf(Upgrade.STELLAR_WIND to 3, Upgrade.DARK_ENERGY to 1),
            discovered = setOf(ConstellationKind.TRIO),
            unlocked = setOf(StarType.RED_DWARF, StarType.YELLOW_STAR),
            ownedFields = Hex.area(3).toSet(),
            fieldsBought = 18,
            parked = mapOf(GalaxyKind.FROST to frost),
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
        // Gekaufte Felder fallen auf die Startfläche zurück, die anderen Galaxien bleiben unberührt.
        assertEquals(Hex.area(2).toSet(), reset.ownedFields)
        assertEquals(0, reset.fieldsBought)
        assertEquals(mapOf(GalaxyKind.FROST to frost), reset.parked)
        assertEquals(GalaxyKind.SPIRAL, reset.activeGalaxy)

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
    fun fieldsAreBoughtOneByOneAndGetPricier() {
        val rich = GameState(stardust = 1e6)
        assertClose(25.0, Balance.fieldCost(rich))
        val (first, cost) = assertNotNull(engine.buyField(rich, Hex(3, 0)))
        assertClose(25.0, cost)
        assertClose(1e6 - 25.0, first.stardust)
        assertTrue(Hex(3, 0) in first.ownedFields)
        assertEquals(1, first.fieldsBought)
        assertEquals(1, first.stats.fieldsBought)
        assertClose(28.75, Balance.fieldCost(first))
        assertNull(engine.buyField(first, Hex(3, 0)), "schon gekauft")
        assertNull(engine.buyField(first, Hex(5, 0)), "grenzt nicht an den Garten")
        assertNotNull(engine.plant(first, Hex(3, 0), StarType.RED_DWARF), "gekaufte Felder lassen sich bepflanzen")

        val wide = rich.copy(ownedFields = Hex.area(6).toSet())
        assertNull(engine.buyField(wide, Hex(7, 0)), "weiter als 6 Felder vom Zentrum")
        assertNull(engine.buyField(GameState(stardust = 24.0), Hex(3, 0)), "zu wenig Sternenstaub")

        // Der alte Ring-Kauf ist ausgemustert; die übrige Forschung bleibt bei Währung und Höchststufe.
        assertNull(engine.buyUpgrade(rich.copy(stardust = 1e12), Upgrade.NEBULA_EXPANSION))
        assertNull(engine.buyUpgrade(rich, Upgrade.FUSION), "kostet Elemente")
        var state = GameState(elements = 1e12)
        repeat(8) { state = assertNotNull(engine.buyUpgrade(state, Upgrade.LONGEVITY)) }
        assertNull(engine.buyUpgrade(state, Upgrade.LONGEVITY), "Maximalstufe erreicht")
    }

    @Test
    fun noFieldsAreBoughtWhileChoosingLaws() {
        val choosing = GameState(stardust = 1e6, lawChoices = listOf(GalaxyLaw.ENTROPY, GalaxyLaw.NURSERY, GalaxyLaw.REDSHIFT))
        assertFalse(engine.canBuyField(choosing, Hex(3, 0)))
        assertNull(engine.buyField(choosing, Hex(3, 0)))
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
    fun bulkLevelCostIsSumOfSingleSteps() {
        val star = adult(StarType.YELLOW_STAR, level = 7)
        val state = board(Hex.ORIGIN to star)
        var expected = 0.0
        for (i in 0 until 10) expected += Balance.levelUpCost(state, star.copy(level = star.level + i))
        assertClose(expected, Balance.levelUpCost(state, star, 10))
        assertClose(Balance.levelUpCost(state, star), Balance.levelUpCost(state, star, 1))
        assertEquals(0.0, Balance.levelUpCost(state, star, 0))
    }

    @Test
    fun levelUpByTenAtOnce() {
        val star = adult(StarType.RED_DWARF, level = 3)
        val cost = Balance.levelUpCost(board(Hex.ORIGIN to star), star, 10)
        val state = board(Hex.ORIGIN to star).copy(stardust = cost + 5.0)
        val next = assertNotNull(engine.levelUp(state, Hex.ORIGIN, 10))
        assertEquals(13, next.stars.getValue(Hex.ORIGIN).level)
        assertClose(5.0, next.stardust)
        assertEquals(10, next.stats.levelUps)
        assertEquals(13, next.stats.highestLevel)

        val poor = state.copy(stardust = cost * 0.99)
        assertNull(engine.levelUp(poor, Hex.ORIGIN, 10), "alle 10 Stufen müssen bezahlbar sein")
        assertNull(engine.levelUp(state, Hex.ORIGIN, 0))
    }

    @Test
    fun levelUpMaxBuysEverythingAffordable() {
        val star = adult(StarType.BLUE_GIANT, level = 1)
        val base = board(Hex.ORIGIN to star)
        for (stardust in listOf(1e3, 5e4, 1e9, 1e30)) {
            val state = base.copy(stardust = stardust)
            val n = Balance.maxAffordableLevels(state, star)
            if (n > 0) assertTrue(Balance.levelUpCost(state, star, n) <= stardust)
            assertTrue(Balance.levelUpCost(state, star, n + 1) > stardust, "n=$n bei $stardust")
            val result = engine.levelUpMax(state, Hex.ORIGIN)
            if (n == 0) {
                assertNull(result)
            } else {
                val (next, count) = assertNotNull(result)
                assertEquals(n, count)
                assertEquals(1 + n, next.stars.getValue(Hex.ORIGIN).level)
                // Es bleibt weniger übrig, als die nächste Stufe kosten würde.
                assertTrue(next.stardust < Balance.levelUpCost(next, next.stars.getValue(Hex.ORIGIN)))
                assertTrue(next.stardust >= 0.0)
            }
        }
        assertNull(engine.levelUpMax(base.copy(stardust = 0.0), Hex.ORIGIN))
    }

    @Test
    fun maxLevelsAreCappedForHugeBudgets() {
        val star = adult(StarType.RED_DWARF)
        val state = board(Hex.ORIGIN to star).copy(stardust = 1e300)
        assertEquals(Balance.MAX_LEVELS_PER_PURCHASE, Balance.maxAffordableLevels(state, star))
        val (next, _) = assertNotNull(engine.levelUpMax(state, Hex.ORIGIN))
        assertTrue(BoardAnalyzer.analyze(next).totalRate.isFinite())
    }

    @Test
    fun blackHolesAndNurseriesCannotBeBulkLeveled() {
        for (type in listOf(StarType.BLACK_HOLE, StarType.NEBULA_NURSERY)) {
            val state = board(Hex.ORIGIN to adult(type)).copy(stardust = 1e30)
            assertNull(engine.levelUp(state, Hex.ORIGIN, 10))
            assertNull(engine.levelUpMax(state, Hex.ORIGIN))
        }
    }

    @Test
    fun starTypesUnlockWithProgress() {
        val state = GameState(stardust = StarType.YELLOW_STAR.unlockAt)
        val result = engine.tick(state, 0.01)
        assertTrue(StarType.YELLOW_STAR in result.state.unlocked)
        assertTrue(result.events.any { it is GameEvent.Unlocked && it.type == StarType.YELLOW_STAR })
    }
}
