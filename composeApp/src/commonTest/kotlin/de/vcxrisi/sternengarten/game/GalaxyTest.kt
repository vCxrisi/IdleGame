package de.vcxrisi.sternengarten.game

import de.vcxrisi.sternengarten.game.engine.Balance
import de.vcxrisi.sternengarten.game.engine.BoardAnalyzer
import de.vcxrisi.sternengarten.game.engine.BridgeBlock
import de.vcxrisi.sternengarten.game.engine.GalaxyFactory
import de.vcxrisi.sternengarten.game.engine.GameEngine
import de.vcxrisi.sternengarten.game.engine.GameEvent
import de.vcxrisi.sternengarten.game.engine.UnlockStatus
import de.vcxrisi.sternengarten.game.engine.hasUnsafeValues
import de.vcxrisi.sternengarten.game.engine.sanitized
import de.vcxrisi.sternengarten.game.model.Achievement
import de.vcxrisi.sternengarten.game.model.ActiveEvent
import de.vcxrisi.sternengarten.game.model.Comet
import de.vcxrisi.sternengarten.game.model.ConstellationKind
import de.vcxrisi.sternengarten.game.model.CosmicEvent
import de.vcxrisi.sternengarten.game.model.CrystalOffer
import de.vcxrisi.sternengarten.game.model.GalaxyGoal
import de.vcxrisi.sternengarten.game.model.GalaxyKind
import de.vcxrisi.sternengarten.game.model.GalaxyLaw
import de.vcxrisi.sternengarten.game.model.GalaxyRun
import de.vcxrisi.sternengarten.game.model.GalaxyUnlock
import de.vcxrisi.sternengarten.game.model.GameState
import de.vcxrisi.sternengarten.game.model.GoalKind
import de.vcxrisi.sternengarten.game.model.Hex
import de.vcxrisi.sternengarten.game.model.LoginReward
import de.vcxrisi.sternengarten.game.model.Metric
import de.vcxrisi.sternengarten.game.model.NebulaTheme
import de.vcxrisi.sternengarten.game.model.Star
import de.vcxrisi.sternengarten.game.model.StarBridge
import de.vcxrisi.sternengarten.game.model.StarType
import de.vcxrisi.sternengarten.game.model.Stats
import de.vcxrisi.sternengarten.game.model.StoreProduct
import de.vcxrisi.sternengarten.game.model.Upgrade
import de.vcxrisi.sternengarten.game.model.activeRun
import de.vcxrisi.sternengarten.game.model.generationOf
import de.vcxrisi.sternengarten.game.model.hasCollapsed
import de.vcxrisi.sternengarten.game.model.projected
import de.vcxrisi.sternengarten.game.model.runKinds
import de.vcxrisi.sternengarten.game.model.switchedTo
import de.vcxrisi.sternengarten.game.model.withRun
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** Mehrere Galaxien: Projektion, Galaxiearten, Felder und die exklusiven Sternarten. */
@OptIn(ExperimentalSerializationApi::class)
class GalaxyTest {

    private val engine = GameEngine(Random(5))
    private val galaxies = engine.galaxies

    private val json = Json {
        encodeDefaults = true
        allowStructuredMapKeys = true
    }

    private fun adult(type: StarType, level: Int = 1) = Star(type, level = level, age = Balance.PROTO_SECONDS)

    private fun assertClose(expected: Double, actual: Double, message: String = "") =
        assertTrue(abs(expected - actual) < 1e-6 * maxOf(1.0, abs(expected)), "$message expected $expected but was $actual")

    private fun breakdown(state: GameState, hex: Hex) = BoardAnalyzer.analyze(state).breakdown.getValue(hex)

    // ------------------------------------------------------------ Projektion

    private fun names(descriptor: SerialDescriptor): Set<String> =
        (0 until descriptor.elementsCount).map { descriptor.getElementName(it) }.toSet()

    private fun jsonOf(state: GameState): JsonObject =
        json.parseToJsonElement(json.encodeToString(GameState.serializer(), state)).jsonObject

    private fun jsonOf(run: GalaxyRun): JsonObject =
        json.parseToJsonElement(json.encodeToString(GalaxyRun.serializer(), run)).jsonObject

    /** Eine Galaxie, in der kein einziges Feld seinen Default-Wert hat. */
    private fun richRun() = GalaxyRun(
        stardust = 1_234.0,
        elements = 56.0,
        stars = mapOf(Hex(1, 0) to adult(StarType.YELLOW_STAR, level = 7)),
        enrichment = mapOf(Hex(0, 1) to 0.5),
        runUpgrades = mapOf(Upgrade.STELLAR_WIND to 2, Upgrade.FUSION to 1),
        law = GalaxyLaw.ENTROPY,
        galaxyName = "Vela 4711",
        galaxyNumber = 4,
        runStardust = 9.9e9,
        comet = Comet(7, 0f, 0.2f, 1f, 0.6f, 8.0, elapsed = 1.0),
        cometCooldown = 12.0,
        boostRemaining = 30.0,
        lawChoices = listOf(GalaxyLaw.NURSERY, GalaxyLaw.REDSHIFT, GalaxyLaw.SILENCE),
        event = ActiveEvent(CosmicEvent.SOLAR_STORM, 20.0),
        eventCooldown = 99.0,
        galaxyGoals = listOf(GalaxyGoal(GoalKind.FIELDS_OWNED, 30.0, 2.0, 15)),
        runSupernovas = 3,
        ownedFields = Hex.area(3).toSet(),
        fieldsBought = 18,
    )

    /** Ein Spielstand mit Meta-Fortschritt, einer geparkten Galaxie, Erschließung und Brücke. */
    private fun richState() = GameState(
        stardust = 4_321.0,
        stars = mapOf(Hex.ORIGIN to adult(StarType.RED_DWARF)),
        upgrades = mapOf(Upgrade.DARK_ENERGY to 3, Upgrade.PRIMORDIAL_NEBULA to 1, Upgrade.STELLAR_WIND to 5),
        law = GalaxyLaw.CELESTIAL_HARP,
        galaxyNumber = 3,
        runStardust = 2e7,
        ownedFields = Hex.area(3).toSet(),
        darkMatter = 500.0,
        totalStardust = 1e12,
        unlocked = setOf(StarType.RED_DWARF, StarType.YELLOW_STAR),
        discovered = setOf(ConstellationKind.TRIO),
        supernovaCount = 12,
        nextCometId = 9,
        playTime = 4_000.0,
        crystals = 77,
        stats = Stats(bigBangs = 2, fieldsBought = 40),
        achievements = setOf(Achievement.FIRST_STAR),
        capsules = 1,
        activeTheme = NebulaTheme.GALAXY,
        entitlements = setOf(StoreProduct.WANDERER_PASS.productId),
        balanceVersion = 2,
        unlock = GalaxyUnlock(GalaxyKind.EMBER, 1_000, 2_000),
        bridges = listOf(StarBridge(GalaxyKind.SPIRAL, GalaxyKind.FROST, 0, 10, built = true)),
        parked = mapOf(GalaxyKind.FROST to GalaxyRun(stardust = 300.0, galaxyNumber = 2, law = GalaxyLaw.NURSERY)),
    )

    @Test
    fun everyGameStateFieldIsClassified() {
        val stateFields = names(GameState.serializer().descriptor)
        val runFields = names(GalaxyRun.serializer().descriptor).map { if (it == "runUpgrades") "upgrades" else it }.toSet()
        assertTrue((runFields intersect META_FIELDS).isEmpty(), "Doppelt eingetragen: ${runFields intersect META_FIELDS}")
        assertEquals(stateFields, runFields + META_FIELDS, "Neues Feld in GameState? In GalaxyRun oder META_FIELDS eintragen.")
    }

    @Test
    fun richRunHasNoDefaults() {
        val rich = jsonOf(richRun())
        val default = jsonOf(GalaxyRun())
        assertEquals(default.keys, rich.keys)
        for (key in rich.keys) assertNotEquals(default[key], rich[key], "richRun() lässt $key auf dem Default-Wert")
    }

    @Test
    fun projectionRoundTrips() {
        val state = richState()
        val run = richRun()
        val withEmber = state.withRun(GalaxyKind.EMBER, run)
        assertEquals(run, withEmber.activeRun())
        assertEquals(GalaxyKind.EMBER, withEmber.activeGalaxy)
        assertEquals(state, state.withRun(state.activeGalaxy, state.activeRun()))

        // Meta-Fortschritt bleibt beim Einsetzen einer Galaxie unverändert.
        val before = jsonOf(state)
        val after = jsonOf(withEmber)
        for (key in META_FIELDS - "activeGalaxy") assertEquals(before[key], after[key], key)
        // Permanente Forschung gehört allen Galaxien, die übrige nur der einen.
        assertEquals(mapOf(Upgrade.DARK_ENERGY to 3, Upgrade.PRIMORDIAL_NEBULA to 1) + run.runUpgrades, withEmber.upgrades)
    }

    @Test
    fun defaultStateProjectsToDefaultRun() {
        assertEquals(GalaxyRun(), GameState().activeRun())
        assertEquals(GameState(), GameState().withRun(GalaxyKind.SPIRAL, GalaxyRun()))
        assertEquals(listOf(GalaxyKind.SPIRAL), GameState().runKinds())
    }

    @Test
    fun switchingKeepsBothGalaxies() {
        val state = richState().copy(
            comet = Comet(1, 0f, 0f, 1f, 1f, 5.0),
            event = ActiveEvent(CosmicEvent.STAR_RAIN, 10.0),
        )
        val frost = assertNotNull(state.switchedTo(GalaxyKind.FROST))
        assertEquals(GalaxyKind.FROST, frost.activeGalaxy)
        assertEquals(300.0, frost.stardust)
        assertEquals(GalaxyLaw.NURSERY, frost.law)
        assertEquals(setOf(GalaxyKind.SPIRAL), frost.parked.keys)
        assertEquals(mapOf(Upgrade.DARK_ENERGY to 3, Upgrade.PRIMORDIAL_NEBULA to 1), frost.upgrades, "Sternenwind gehört der Spirale")
        assertEquals(state.darkMatter, frost.darkMatter)
        assertNull(state.switchedTo(GalaxyKind.EMBER), "noch nicht erschlossen")

        // Zurück: alles wie vorher, nur Komet und Ereignis sind beim Parken vergangen.
        val back = assertNotNull(frost.switchedTo(GalaxyKind.SPIRAL))
        assertEquals(state.copy(comet = null, event = null), back)
        assertEquals(state, assertNotNull(state.projected(GalaxyKind.SPIRAL)))
        val projected = assertNotNull(state.projected(GalaxyKind.FROST))
        assertTrue(projected.parked.isEmpty(), "die Lesesicht enthält keine geparkten Galaxien")
        assertEquals(300.0, projected.stardust)
    }

    @Test
    fun nebulaFollowsLawThenKind() {
        assertEquals(GalaxyLaw.NORMAL.hue, GameState().nebulaHue)
        assertEquals(GalaxyKind.FROST.hue, GameState(activeGalaxy = GalaxyKind.FROST).nebulaHue)
        assertEquals(GalaxyLaw.ENTROPY.hue, GameState(activeGalaxy = GalaxyKind.FROST, law = GalaxyLaw.ENTROPY).nebulaHue)
        assertEquals(NebulaTheme.ROSE.hue, GameState(activeGalaxy = GalaxyKind.FROST, activeTheme = NebulaTheme.ROSE).nebulaHue)
    }

    @Test
    fun sanitizedRepairsParkedRuns() {
        val clean = richState()
        assertTrue(clean.sanitized().parked === clean.parked, "saubere Galaxien werden nicht kopiert")
        val broken = clean.copy(
            parked = mapOf(
                GalaxyKind.FROST to GalaxyRun(
                    stardust = Double.NaN,
                    runStardust = Double.POSITIVE_INFINITY,
                    eventCooldown = Double.NaN,
                    stars = mapOf(Hex.ORIGIN to Star(StarType.BLACK_HOLE, stored = Double.POSITIVE_INFINITY)),
                ),
            ),
        )
        assertTrue(broken.hasUnsafeValues())
        val repaired = broken.sanitized()
        assertFalse(repaired.hasUnsafeValues())
        val run = repaired.parked.getValue(GalaxyKind.FROST)
        assertEquals(0.0, run.stardust)
        assertEquals(1e300, run.runStardust)
    }

    // ------------------------------------------------------------ Galaxiearten

    @Test
    fun pricesScaleWithKindButProductionDoesNot() {
        val spiral = GameState(stars = mapOf(Hex.ORIGIN to adult(StarType.RED_DWARF, level = 3)))
        val frost = spiral.copy(activeGalaxy = GalaxyKind.FROST)
        val star = spiral.stars.getValue(Hex.ORIGIN)
        assertClose(3 * Balance.starCost(spiral, StarType.YELLOW_STAR), Balance.starCost(frost, StarType.YELLOW_STAR))
        assertClose(3 * Balance.levelUpCost(spiral, star), Balance.levelUpCost(frost, star))
        assertClose(3 * Balance.upgradeCost(spiral, Upgrade.STELLAR_WIND), Balance.upgradeCost(frost, Upgrade.STELLAR_WIND))
        assertClose(Balance.upgradeCost(spiral, Upgrade.FUSION), Balance.upgradeCost(frost, Upgrade.FUSION), "Elemente")
        assertClose(45.0, Balance.startingStardust(frost))
        assertClose(1_500.0, Balance.startingStardust(spiral, GalaxyKind.SHADOW))
        assertClose(BoardAnalyzer.analyze(spiral).totalRate, BoardAnalyzer.analyze(frost).totalRate)
        assertClose(StarType.YELLOW_STAR.unlockAt * 3, Balance.unlockThreshold(frost, StarType.YELLOW_STAR))
        assertClose(75.0, Balance.cometMinReward(frost))
    }

    @Test
    fun totalStardustIsCountedInStardustValue() {
        val frost = GameState(activeGalaxy = GalaxyKind.FROST, stars = mapOf(Hex.ORIGIN to adult(StarType.RED_DWARF)))
        val result = engine.tick(frost, 10.0)
        val income = result.state.stardust - frost.stardust
        assertTrue(income > 0.0)
        assertClose(income, result.state.runStardust)
        assertClose(income / 3.0, result.state.totalStardust)
        val (warped, amount) = engine.timeWarp(frost, 60.0)
        assertClose(amount / 3.0, warped.totalStardust)
    }

    @Test
    fun kindTwistsApply() {
        val frost = GameState(activeGalaxy = GalaxyKind.FROST)
        assertClose(0.7, Balance.agingMultiplier(frost))
        val ember = GameState(activeGalaxy = GalaxyKind.EMBER)
        val giant = Star(StarType.BLUE_GIANT)
        assertClose(1.5 * Balance.supernovaElements(GameState(), giant), Balance.supernovaElements(ember, giant))
        assertClose(1.5 * Balance.supernovaEnrichment(GameState()), Balance.supernovaEnrichment(ember))

        // Schatten: offline +50 %, online nicht.
        val shadow = GameState(activeGalaxy = GalaxyKind.SHADOW, stars = mapOf(Hex.ORIGIN to adult(StarType.RED_DWARF)))
        val rate = BoardAnalyzer.analyze(shadow).totalRate
        val (_, report) = engine.applyOffline(shadow, 600.0)
        assertClose(rate * 600.0 * 1.5, report.stardust)
        assertClose(rate * 10.0, engine.tick(shadow, 10.0).ownIncome)
    }

    @Test
    fun darkMatterGainNormalizesByKind() {
        assertEquals(2.0, Balance.darkMatterGain(GameState(runStardust = 8e6)), "Spirale wie bisher")
        val frost = GameState(activeGalaxy = GalaxyKind.FROST, runStardust = 3e6)
        assertClose(1.0, Balance.darkMatterBase(frost))
        assertEquals(2.0, Balance.darkMatterGain(frost), "⌊1 · 2,5⌋")
        assertEquals(20.0, Balance.darkMatterGain(GameState(activeGalaxy = GalaxyKind.SHADOW, runStardust = 1e8)))
        assertEquals(5.0, Balance.darkMatterGain(frost.copy(runStardust = 24e6)), "⌊∛8 · 2,5⌋")

        // Schwellen: nie unter dem Basiswert 1, darüber passend zum Faktor der Art.
        assertClose(3e6, Balance.runStardustForDarkMatter(frost, 1.0))
        assertClose(24e6, Balance.runStardustForDarkMatter(frost, 5.0))
        assertClose(8e6, Balance.runStardustForDarkMatter(GameState(), 2.0))
        assertTrue(Balance.darkMatterGain(frost.copy(runStardust = Balance.runStardustForDarkMatter(frost, 5.0) * 1.0001)) >= 5.0)
    }

    @Test
    fun canBigBangIgnoresKindMultiplier() {
        // ∛(2,9M / 3M) < 1 – mit ×2,5 wären es zwei Dunkle Materie, ein Urknall ist trotzdem nicht möglich.
        val frost = GameState(activeGalaxy = GalaxyKind.FROST, runStardust = 2.9e6)
        assertFalse(engine.canBigBang(frost))
        assertNull(engine.bigBang(frost))
        assertEquals(0.0, Balance.darkMatterGain(frost))
        assertTrue(engine.canBigBang(frost.copy(runStardust = 3e6)))
        assertFalse(engine.canBigBang(GameState(runStardust = 8e6, lawChoices = listOf(GalaxyLaw.ENTROPY))))
    }

    @Test
    fun collapseResetsOnlyThatGalaxy() {
        val spiral = GalaxyRun(
            stardust = 9e9,
            stars = mapOf(Hex.ORIGIN to adult(StarType.QUASAR)),
            runUpgrades = mapOf(Upgrade.STELLAR_WIND to 4),
            galaxyNumber = 5,
            runStardust = 1e12,
            ownedFields = Hex.area(4).toSet(),
            fieldsBought = 42,
        )
        val state = GameState(
            activeGalaxy = GalaxyKind.FROST,
            law = GalaxyLaw.ENTROPY,
            galaxyNumber = 2,
            galaxyName = "Kepler 1426",
            runStardust = 24e6,
            stardust = 5e6,
            elements = 40.0,
            stars = mapOf(Hex.ORIGIN to adult(StarType.FROST_CRYSTAL), Hex(3, 0) to adult(StarType.RED_DWARF)),
            upgrades = mapOf(Upgrade.STELLAR_WIND to 2, Upgrade.DARK_ENERGY to 1),
            ownedFields = Hex.area(3).toSet(),
            fieldsBought = 18,
            darkMatter = 10.0,
            parked = mapOf(GalaxyKind.SPIRAL to spiral),
        )
        val reset = assertNotNull(engine.bigBang(state))
        assertClose(15.0, reset.darkMatter, "∛8 · 2,5 = 5 Dunkle Materie")
        assertEquals(mapOf(GalaxyKind.SPIRAL to spiral), reset.parked)
        assertEquals(GalaxyKind.FROST, reset.activeGalaxy)
        assertTrue(reset.stars.isEmpty())
        assertEquals(0.0, reset.elements)
        assertEquals(0.0, reset.runStardust)
        assertClose(45.0, reset.stardust)
        assertEquals(mapOf(Upgrade.DARK_ENERGY to 1), reset.upgrades)
        assertEquals(Balance.FIELD_ORDER.take(19).toSet(), reset.ownedFields)
        assertEquals(0, reset.fieldsBought)
        assertEquals("Kepler 1426", reset.galaxyName)
        assertEquals(3, reset.lawChoices.size)
        assertFalse(GalaxyLaw.ENTROPY in reset.lawChoices)
        assertEquals(1, reset.stats.bigBangs)
    }

    @Test
    fun bigBangDoesNotRerollARunningEvent() {
        val state = GameState(runStardust = 8e6, event = ActiveEvent(CosmicEvent.STAR_RAIN, 30.0), eventCooldown = 0.0)
        val reset = assertNotNull(engine.bigBang(state))
        assertNull(reset.event)
        assertTrue(reset.eventCooldown >= Balance.eventInterval(0.0), "${reset.eventCooldown}")
        assertEquals(GameState().eventCooldown, assertNotNull(engine.bigBang(GameState(runStardust = 8e6))).eventCooldown)
    }

    @Test
    fun hasCollapsedCountsAnOpenLawChoice() {
        val choices = listOf(GalaxyLaw.ENTROPY, GalaxyLaw.NURSERY, GalaxyLaw.SILENCE)
        assertFalse(GameState().hasCollapsed(GalaxyKind.SPIRAL))
        assertTrue(GameState(lawChoices = choices).hasCollapsed(GalaxyKind.SPIRAL), "Urknall erfolgt, Gesetz noch offen")
        assertTrue(GameState(galaxyNumber = 2).hasCollapsed(GalaxyKind.SPIRAL))
        assertFalse(GameState().hasCollapsed(GalaxyKind.FROST), "nicht erschlossen")

        val fresh = GameState(parked = mapOf(GalaxyKind.FROST to GalaxyRun(galaxyNumber = 0, lawChoices = choices)))
        assertEquals(0, fresh.generationOf(GalaxyKind.FROST))
        assertFalse(fresh.hasCollapsed(GalaxyKind.FROST), "frisch erschlossen, noch ohne Urknall")
        val chosen = GameState(parked = mapOf(GalaxyKind.FROST to GalaxyRun(galaxyNumber = 1, lawChoices = choices)))
        assertTrue(chosen.hasCollapsed(GalaxyKind.FROST))
    }

    // ------------------------------------------------------------ Felder

    @Test
    fun fieldOrderIsSymmetricSpiral() {
        val order = Balance.FIELD_ORDER
        assertEquals(127, order.size)
        assertEquals(Hex.area(6).toSet(), order.toSet())
        assertEquals(order.map { it.length() }.sorted(), order.map { it.length() }, "Ring für Ring")
        // Jeder Sechserblock ist eine volle Drehung um das Zentrum.
        for (block in order.drop(1).chunked(6)) {
            for (i in block.indices) assertEquals(block[(i + 1) % 6], block[i].rotated60())
        }
        assertEquals(Hex.area(2).toSet(), order.take(19).toSet())
    }

    @Test
    fun startFieldCounts() {
        fun count(meta: GameState, kind: GalaxyKind, law: GalaxyLaw = GalaxyLaw.NORMAL) = Balance.startFieldCount(meta, kind, law)
        val pn1 = GameState(upgrades = mapOf(Upgrade.PRIMORDIAL_NEBULA to 1))
        val pn2 = GameState(upgrades = mapOf(Upgrade.PRIMORDIAL_NEBULA to 2))
        val pioneer = GameState(entitlements = setOf(StoreProduct.GALAXY_PIONEER.productId))
        assertEquals(19, count(GameState(), GalaxyKind.SPIRAL))
        assertEquals(37, count(pn1, GalaxyKind.SPIRAL))
        assertEquals(61, count(pn2, GalaxyKind.SPIRAL))
        assertEquals(25, count(pioneer, GalaxyKind.SPIRAL))
        assertEquals(19, count(GameState(), GalaxyKind.FROST))
        assertEquals(13, count(GameState(), GalaxyKind.EMBER))
        assertEquals(55, count(pn2, GalaxyKind.AURORA))
        assertEquals(7, count(GameState(), GalaxyKind.SHADOW))
        assertEquals(7, count(GameState(), GalaxyKind.SPIRAL, GalaxyLaw.HIGH_GRAVITY))
        assertEquals(7, count(GameState(), GalaxyKind.SHADOW, GalaxyLaw.HIGH_GRAVITY), "nie unter sieben")
        assertEquals(49, count(pn2, GalaxyKind.SPIRAL, GalaxyLaw.HIGH_GRAVITY))
        assertEquals(91, Balance.maxFieldCount(GalaxyLaw.HIGH_GRAVITY))
        assertEquals(127, Balance.maxFieldCount(GalaxyLaw.NORMAL))

        // Die Startflächen der Spirale entsprechen den alten Ringen.
        assertEquals(Hex.area(3).toSet(), Balance.startFields(pn1, GalaxyKind.SPIRAL, GalaxyLaw.NORMAL))
        assertEquals(Hex.area(4).toSet(), Balance.startFields(pn2, GalaxyKind.SPIRAL, GalaxyLaw.NORMAL))
        assertEquals(Hex.area(1).toSet(), Balance.startFields(GameState(), GalaxyKind.SPIRAL, GalaxyLaw.HIGH_GRAVITY))
    }

    @Test
    fun fieldCostScalesWithKindLawAndSurvey() {
        assertClose(25.0, Balance.fieldCost(GameState()))
        assertClose(25.0 * 1.15.pow(10), Balance.fieldCost(GameState(fieldsBought = 10)))
        assertClose(75.0, Balance.fieldCost(GameState(activeGalaxy = GalaxyKind.FROST)))
        assertClose(25.0 * 30 * 0.7, Balance.fieldCost(GameState(activeGalaxy = GalaxyKind.AURORA)), "Polarlicht −30 %")
        assertClose(25.0 * 0.25, Balance.fieldCost(GameState(law = GalaxyLaw.COSMIC_EXPANSION)))
        assertClose(50.0, Balance.fieldCost(GameState(law = GalaxyLaw.REDSHIFT)))
        assertClose(25.0 * 0.85 * 0.85, Balance.fieldCost(GameState(upgrades = mapOf(Upgrade.NEBULA_SURVEY to 2))))
        // Sternenregen und Sternenwiege machen Sterne billiger, Felder nicht.
        assertClose(25.0, Balance.fieldCost(GameState(law = GalaxyLaw.NURSERY, event = ActiveEvent(CosmicEvent.STAR_RAIN, 9.0))))
    }

    @Test
    fun frontierStaysWithinReach() {
        assertEquals(Hex.ring(3).toSet(), Balance.frontier(GameState()))
        val gravity = GameState(law = GalaxyLaw.HIGH_GRAVITY, ownedFields = Hex.area(5).toSet())
        assertTrue(Balance.frontier(gravity).isEmpty(), "Hohe Gravitation: höchstens fünf Ringe")
        assertFalse(engine.canBuyField(gravity.copy(stardust = 1e30), Hex(6, 0)))
        val bump = GameState(ownedFields = Hex.area(2).toSet() + Hex(3, 0))
        assertTrue(Hex(4, 0) in Balance.frontier(bump))
    }

    @Test
    fun fieldsResetOnCollapseAndFollowTheNewLaw() {
        var state = GameState(stardust = 1e6, runStardust = 8e6)
        repeat(5) { i -> state = assertNotNull(engine.buyField(state, Hex.ring(3)[i])).first }
        assertEquals(24, state.ownedFields.size)
        assertEquals(5, state.fieldsBought)
        val reset = assertNotNull(engine.bigBang(state))
        assertEquals(Hex.area(2).toSet(), reset.ownedFields)
        assertEquals(0, reset.fieldsBought)
        assertEquals(5L, reset.stats.fieldsBought, "die Statistik bleibt")

        val gravity = assertNotNull(engine.chooseGalaxy(reset.copy(lawChoices = listOf(GalaxyLaw.HIGH_GRAVITY)), GalaxyLaw.HIGH_GRAVITY))
        assertEquals(Hex.area(1).toSet(), gravity.ownedFields)
        assertEquals(2, gravity.galaxyNumber)
    }

    @Test
    fun freshlyUnlockedGalaxyKeepsItsName() {
        val fresh = GalaxyFactory.freshRun(GameState(), GalaxyKind.EMBER, GalaxyLaw.NORMAL, 0, "Andros 812", listOf(GalaxyLaw.ENTROPY))
        val state = GameState().withRun(GalaxyKind.EMBER, fresh)
        assertClose(150.0, state.stardust)
        assertEquals(13, state.ownedFields.size)
        val chosen = assertNotNull(engine.chooseGalaxy(state, GalaxyLaw.ENTROPY))
        assertEquals("Andros 812", chosen.galaxyName)
        assertEquals(1, chosen.galaxyNumber)
        assertEquals(3, chosen.galaxyGoals.size)
    }

    @Test
    fun primordialNebulaExtendsEveryRun() {
        val state = GameState(
            darkMatter = 5.0,
            ownedFields = Hex.area(3).toSet() + Hex(4, 0),
            fieldsBought = 19,
            parked = mapOf(
                GalaxyKind.FROST to GalaxyRun(ownedFields = Hex.area(2).toSet() + Hex(3, 0), fieldsBought = 1),
                GalaxyKind.EMBER to GalaxyRun(ownedFields = Balance.FIELD_ORDER.take(13).toSet()),
            ),
        )
        val next = assertNotNull(engine.buyUpgrade(state, Upgrade.PRIMORDIAL_NEBULA))
        assertEquals(Hex.area(3).toSet() + Hex(4, 0), next.ownedFields)
        assertEquals(1, next.fieldsBought, "nur Hex(4, 0) liegt außerhalb der neuen Startfläche")
        assertClose(25.0 * 1.15, Balance.fieldCost(next))
        val frost = next.parked.getValue(GalaxyKind.FROST)
        assertEquals(Hex.area(3).toSet(), frost.ownedFields)
        assertEquals(0, frost.fieldsBought)
        assertEquals(Balance.FIELD_ORDER.take(31).toSet(), next.parked.getValue(GalaxyKind.EMBER).ownedFields)
    }

    @Test
    fun galaxyPioneerExtendsEveryRunEvenWhenRestored() {
        val state = GameState(parked = mapOf(GalaxyKind.SHADOW to GalaxyRun(ownedFields = Balance.FIELD_ORDER.take(7).toSet())))
        val restored = assertNotNull(engine.shop.grantPurchase(state, StoreProduct.GALAXY_PIONEER, "tx-p", restored = true))
        assertEquals(Balance.FIELD_ORDER.take(25).toSet(), restored.ownedFields)
        assertEquals(Balance.FIELD_ORDER.take(13).toSet(), restored.parked.getValue(GalaxyKind.SHADOW).ownedFields)
        assertEquals(0, restored.crystals)
        val bought = assertNotNull(engine.shop.grantPurchase(state, StoreProduct.GALAXY_PIONEER, "tx-q"))
        assertEquals(250, bought.crystals)
        assertEquals(2, bought.capsules)
        assertEquals(25, bought.ownedFields.size)
    }

    // ------------------------------------------------------------ Exklusive Sternarten

    @Test
    fun exclusiveStarsOnlyInTheirHomeGalaxy() {
        val rich = GameState(stardust = 1e12, unlocked = StarType.entries.toSet())
        assertFalse(engine.canPlant(rich, Hex.ORIGIN, StarType.FROST_CRYSTAL))
        assertTrue(engine.canPlant(rich.copy(activeGalaxy = GalaxyKind.FROST), Hex.ORIGIN, StarType.FROST_CRYSTAL))
        assertFalse(engine.canPlant(rich.copy(activeGalaxy = GalaxyKind.FROST), Hex.ORIGIN, StarType.SHADOW_STAR))
        assertEquals(StarType.EMBER_STAR, GalaxyKind.EMBER.exclusiveStar)
        assertNull(GalaxyKind.SPIRAL.exclusiveStar)

        // Freigeschaltet wird nur zu Hause, und dort ab Grundpreis · 0,4 · K.
        val spiral = engine.tick(GameState(stardust = 1e12), 0.01).state
        assertFalse(StarType.FROST_CRYSTAL in spiral.unlocked)
        val poorFrost = engine.tick(GameState(activeGalaxy = GalaxyKind.FROST, stardust = 100_000.0), 0.01).state
        assertFalse(StarType.FROST_CRYSTAL in poorFrost.unlocked, "48K reichen in der Spirale, nicht im Frost")
        val frost = engine.tick(GameState(activeGalaxy = GalaxyKind.FROST, stardust = 144_000.0), 0.01)
        assertTrue(StarType.FROST_CRYSTAL in frost.state.unlocked)
        assertTrue(frost.events.any { it is GameEvent.Unlocked && it.type == StarType.FROST_CRYSTAL })
    }

    @Test
    fun frostCrystalCountsRotations() {
        val crystal = Hex(2, 0)
        val rotations = generateSequence(crystal) { it.rotated60() }.drop(1).take(5).toList()
        assertEquals(listOf(Hex(2, -2), Hex(0, -2), Hex(-2, 0), Hex(-2, 2), Hex(0, 2)), rotations)
        val alone = GameState(stars = mapOf(crystal to adult(StarType.FROST_CRYSTAL)))
        val full = alone.copy(stars = alone.stars + rotations.associateWith { adult(StarType.RED_DWARF) })
        val two = alone.copy(stars = alone.stars + rotations.take(2).associateWith { adult(StarType.RED_DWARF) })
        assertClose(1.5, breakdown(full, crystal).symmetry)
        assertClose(0.6, breakdown(two, crystal).symmetry)
        assertClose(0.0, breakdown(full, crystal).aura, "zählt nicht als Nachbarschaftsbonus")
        assertClose(breakdown(alone, crystal).rate * 2.5, breakdown(full, crystal).rate)
        val center = GameState(stars = mapOf(Hex.ORIGIN to adult(StarType.FROST_CRYSTAL), Hex(1, 0) to adult(StarType.RED_DWARF)))
        assertClose(0.0, breakdown(center, Hex.ORIGIN).symmetry, "im Zentrum ohne Bonus")
    }

    @Test
    fun emberGroupScalesWithConnectedNest() {
        val nest = listOf(Hex(0, 0), Hex(1, 0), Hex(2, 0))
        val lonely = Hex(0, 3)
        val state = GameState(
            activeGalaxy = GalaxyKind.EMBER,
            stars = nest.associateWith { adult(StarType.EMBER_STAR) } + (lonely to adult(StarType.EMBER_STAR)),
        )
        val analysis = BoardAnalyzer.analyze(state)
        for (h in nest) {
            assertEquals(3, analysis.emberGroups[h])
            assertClose(1.70, analysis.breakdown.getValue(h).group)
        }
        assertEquals(1, analysis.emberGroups[lonely])
        assertClose(1.0, analysis.breakdown.getValue(lonely).group)

        // Ein großes Nest bringt mehr Elemente: Glut ×1,5 und Nest ×1,5 (zwei weitere Mitglieder).
        val lifespan = StarType.EMBER_STAR.lifespan!!
        val dying = state.copy(stars = state.stars + (Hex.ORIGIN to Star(StarType.EMBER_STAR, age = lifespan - 0.05)))
        val result = engine.tick(dying, 0.1)
        assertFalse(Hex.ORIGIN in result.state.stars)
        assertClose(1.5 * 1.5, result.state.elements)
        assertTrue(result.events.any { it is GameEvent.Supernova })
    }

    @Test
    fun auroraCountsDistinctNeighborTypes() {
        val state = GameState(
            stars = mapOf(
                Hex.ORIGIN to adult(StarType.AURORA_STAR),
                Hex(1, 0) to adult(StarType.RED_DWARF),
                Hex(-1, 0) to adult(StarType.RED_DWARF),
                Hex(0, 1) to adult(StarType.YELLOW_STAR),
                Hex(0, -1) to adult(StarType.BINARY),
            ),
        )
        assertClose(3 * 0.35, breakdown(state, Hex.ORIGIN).variety)
        assertClose(0.25, breakdown(state, Hex.ORIGIN).aura, "der Gelbe Stern wärmt wie gewohnt")
        val gravity = state.copy(law = GalaxyLaw.HIGH_GRAVITY)
        assertClose(3 * 0.35 * 2, breakdown(gravity, Hex.ORIGIN).variety)
    }

    @Test
    fun shadowStarLovesTheEdge() {
        val edge = Hex(2, 0)
        val state = GameState(
            ownedFields = Hex.area(1).toSet() + edge,
            stars = mapOf(edge to adult(StarType.SHADOW_STAR)),
        )
        assertClose(3.0, breakdown(state, edge).edge, "fünf Nachbarn liegen außerhalb des Gartens")
        val inside = GameState(stars = mapOf(Hex.ORIGIN to adult(StarType.SHADOW_STAR)))
        assertClose(0.0, breakdown(inside, Hex.ORIGIN).edge)
    }

    @Test
    fun redshiftQuadruplesRedDwarfs() {
        val normal = GameState(stars = mapOf(Hex.ORIGIN to adult(StarType.RED_DWARF), Hex(3, 0) to adult(StarType.YELLOW_STAR)))
        val redshift = normal.copy(law = GalaxyLaw.REDSHIFT)
        assertClose(4 * breakdown(normal, Hex.ORIGIN).rate, breakdown(redshift, Hex.ORIGIN).rate)
        assertClose(breakdown(normal, Hex(3, 0)).rate, breakdown(redshift, Hex(3, 0)).rate)
    }

    // ------------------------------------------------------------ Tick-Modi

    @Test
    fun backgroundTickSkipsBoostPlayTimeSkyAndAchievements() {
        val stars = (0 until 3).associate { Hex(it, 0) to adult(StarType.RED_DWARF) }
        val state = GameState(
            stars = stars,
            boostRemaining = 10.0,
            cometCooldown = 0.5,
            eventCooldown = 0.05,
            stats = Stats(starsPlanted = 3),
        )
        val rate = BoardAnalyzer.analyze(state).totalRate

        val background = engine.tick(state, 1.0, background = true)
        assertClose(rate, background.state.stardust - state.stardust, "ohne Kometenrausch")
        assertClose(rate, background.ownIncome)
        assertEquals(10.0, background.state.boostRemaining)
        assertEquals(0.0, background.state.playTime)
        assertNull(background.state.comet)
        assertNull(background.state.event)
        assertTrue(background.state.achievements.isEmpty(), "Erfolge prüft der Orchestrator auf dem ganzen Zustand")
        assertTrue(ConstellationKind.TRIO in background.state.discovered, "Entdeckungen zählen trotzdem")

        val online = engine.tick(state, 1.0)
        assertClose(rate * Balance.COMET_BOOST_MULT, online.state.stardust - state.stardust)
        assertClose(rate, online.ownIncome, "eigenes Einkommen ohne Kometenrausch")
        assertEquals(9.0, online.state.boostRemaining)
        assertEquals(1.0, online.state.playTime)
        assertNotNull(online.state.comet)
        assertNotNull(online.state.event)
        assertTrue(Achievement.FIRST_STAR in online.state.achievements)

        assertEquals(0.0, engine.tick(state.copy(lawChoices = listOf(GalaxyLaw.ENTROPY)), 1.0).ownIncome, "eingefroren")
    }

    @Test
    fun silenceHasNoCometsOrEvents() {
        val stars = (0 until 3).associate { Hex(it, 0) to adult(StarType.RED_DWARF) }
        val state = GameState(stars = stars, law = GalaxyLaw.SILENCE, cometCooldown = 0.5, eventCooldown = 0.05)
        val next = engine.tick(state, 1.0).state
        assertNull(next.comet)
        assertNull(next.event)
        assertClose(BoardAnalyzer.analyze(state).totalRate * 1.25, next.stardust - state.stardust)
    }

    // ------------------------------------------------------------ Ziele und Erfolge

    @Test
    fun exclusiveGoalOnlyOnceTheStarIsUnlocked() {
        val p = engine.progression
        val locked = GameState(activeGalaxy = GalaxyKind.FROST)
        val unlocked = locked.copy(unlocked = locked.unlocked + StarType.FROST_CRYSTAL)
        val rolls = 60
        assertTrue((1..rolls).none { p.rollGalaxyGoals(locked).any { it.kind == GoalKind.EXCLUSIVE_STARS } })
        assertTrue((1..rolls).none { p.rollGalaxyGoals(GameState(unlocked = StarType.entries.toSet())).any { it.kind == GoalKind.EXCLUSIVE_STARS } })
        assertTrue((1..rolls).any { p.rollGalaxyGoals(unlocked).any { it.kind == GoalKind.EXCLUSIVE_STARS } })
    }

    @Test
    fun goalsScaleWithKind() {
        val p = engine.progression
        val frost = GameState(activeGalaxy = GalaxyKind.FROST, unlocked = setOf(StarType.RED_DWARF, StarType.FROST_CRYSTAL))
        val targets = HashMap<GoalKind, GalaxyGoal>()
        repeat(80) { p.rollGalaxyGoals(frost).forEach { targets[it.kind] = it } }
        assertEquals(GoalKind.entries.toSet(), targets.keys)
        assertClose(6e6, targets.getValue(GoalKind.RUN_STARDUST).target)
        assertClose(1_500.0, targets.getValue(GoalKind.PRODUCTION_RATE).target)
        assertClose(19.0 + 6 + 3, targets.getValue(GoalKind.FIELDS_OWNED).target)
        assertClose(1.0, targets.getValue(GoalKind.EXCLUSIVE_STARS).target)
        assertClose(3.0, targets.getValue(GoalKind.WHITE_DWARFS).target)
        assertClose(2.0, targets.getValue(GoalKind.RUN_STARDUST).darkMatter, "⌈1 · 1,5⌉")

        val state = frost.copy(
            stars = mapOf(
                Hex.ORIGIN to adult(StarType.FROST_CRYSTAL),
                Hex(1, 0) to adult(StarType.YELLOW_STAR).copy(whiteDwarf = true),
            ),
        )
        val analysis = BoardAnalyzer.analyze(state)
        assertEquals(19.0, p.goalProgress(state, analysis, GalaxyGoal(GoalKind.FIELDS_OWNED, 1.0, 1.0, 1)))
        assertEquals(1.0, p.goalProgress(state, analysis, GalaxyGoal(GoalKind.EXCLUSIVE_STARS, 1.0, 1.0, 1)))
        assertEquals(1.0, p.goalProgress(state, analysis, GalaxyGoal(GoalKind.WHITE_DWARFS, 1.0, 1.0, 1)))
    }

    @Test
    fun galaxyMetricsCountRunsBridgesAndCollapses() {
        val p = engine.progression
        val state = GameState(
            galaxyNumber = 3,
            stats = Stats(fieldsBought = 12),
            parked = mapOf(
                GalaxyKind.FROST to GalaxyRun(galaxyNumber = 1, lawChoices = listOf(GalaxyLaw.ENTROPY)),
                GalaxyKind.EMBER to GalaxyRun(galaxyNumber = 0, lawChoices = listOf(GalaxyLaw.ENTROPY)),
            ),
            bridges = listOf(
                StarBridge(GalaxyKind.SPIRAL, GalaxyKind.FROST, 0, 1, built = true),
                StarBridge(GalaxyKind.FROST, GalaxyKind.SPIRAL, 0, 9_999),
            ),
        )
        assertEquals(12.0, p.metric(state, Metric.FIELDS_BOUGHT))
        assertEquals(3.0, p.metric(state, Metric.GALAXIES))
        assertEquals(1.0, p.metric(state, Metric.BRIDGES), "nur fertige Brücken")
        assertEquals(0.0, p.metric(state.copy(bridges = emptyList(), stats = state.stats.copy(bridgesBuilt = 7)), Metric.BRIDGES), "abgerissen zählt nicht")
        assertEquals(2.0, p.metric(state, Metric.KINDS_COLLAPSED), "Spirale und Frost, nicht die frische Glut")

        val events = ArrayList<GameEvent>()
        val awarded = p.checkAchievements(state, events)
        assertTrue(Achievement.FIRST_FIELD in awarded.achievements)
        assertTrue(Achievement.SECOND_GALAXY in awarded.achievements)
        assertTrue(Achievement.GALAXIES_3 in awarded.achievements)
        assertTrue(Achievement.FIRST_BRIDGE in awarded.achievements)
        assertFalse(Achievement.ALL_GALAXIES in awarded.achievements)
        assertEquals(36, Achievement.entries.size)
        assertEquals(14.0, Achievement.STAR_TYPES_ALL.threshold)
    }

    // ------------------------------------------------------------ Zeiten und Brücken (Formeln)

    @Test
    fun timersAndBridgeFormulas() {
        assertEquals(3_600_000L, Balance.unlockMillis(GameState(), GalaxyKind.FROST))
        val faster = GameState(
            entitlements = setOf(StoreProduct.GALAXY_PIONEER.productId),
            upgrades = mapOf(Upgrade.SPACE_FOLD to 1),
        )
        assertEquals((24 * 3_600_000.0 * 0.5 * 0.85).toLong(), Balance.unlockMillis(faster, GalaxyKind.SHADOW))
        assertEquals(listOf(20, 80, 240, 400), listOf(1, 4, 12, 24).map { Balance.timerSkipCost(it * 3_600_000L) })
        assertEquals(10, Balance.timerSkipCost(1_800_000L))
        assertEquals(1, Balance.timerSkipCost(1L))
        assertEquals(400, Balance.timerSkipCost(1_000L * 3_600_000L))

        val oneBridge = GameState(bridges = listOf(StarBridge(GalaxyKind.SPIRAL, GalaxyKind.FROST, 0, 1, built = true)))
        assertClose(50.0, Balance.bridgeCost(GameState()))
        assertClose(500.0, Balance.bridgeCost(oneBridge))
        assertEquals(1_800_000L, Balance.bridgeMillis(GameState()))
        assertEquals(7_200_000L, Balance.bridgeMillis(oneBridge))
    }

    @Test
    fun bridgeFlowUsesExchangeRateAndCap() {
        val bridge = StarBridge(GalaxyKind.SPIRAL, GalaxyKind.FROST, 0, 1, built = true)
        val state = GameState(parked = mapOf(GalaxyKind.FROST to GalaxyRun()))
        // 10 % von 100/s zum Kurs 3 sind 30/s, gedeckelt bei 50 % von 50/s.
        assertClose(25.0, Balance.bridgeFlow(state, bridge, mapOf(GalaxyKind.SPIRAL to 100.0, GalaxyKind.FROST to 50.0)))
        assertClose(30.0, Balance.bridgeFlow(state, bridge, mapOf(GalaxyKind.SPIRAL to 100.0, GalaxyKind.FROST to 1_000.0)))
        val crafted = state.copy(upgrades = mapOf(Upgrade.BRIDGE_CRAFT to 4))
        assertClose(90.0, Balance.bridgeFlow(crafted, bridge, mapOf(GalaxyKind.SPIRAL to 100.0, GalaxyKind.FROST to 1_000.0)))
        val tidal = state.copy(parked = mapOf(GalaxyKind.FROST to GalaxyRun(law = GalaxyLaw.TIDAL_BOND)))
        assertClose(50.0, Balance.bridgeFlow(tidal, bridge, mapOf(GalaxyKind.SPIRAL to 100.0, GalaxyKind.FROST to 50.0)))
        val silent = state.copy(parked = mapOf(GalaxyKind.FROST to GalaxyRun(law = GalaxyLaw.SILENCE)))
        assertEquals(0.0, Balance.bridgeFlow(silent, bridge, mapOf(GalaxyKind.SPIRAL to 100.0, GalaxyKind.FROST to 50.0)))
        assertEquals(0.0, Balance.bridgeFlow(GameState(), bridge, mapOf(GalaxyKind.SPIRAL to 100.0)), "Ziel noch nicht erschlossen")
    }

    // ------------------------------------------------------------ Wechsel und Hintergrund

    @Test
    fun switchingDoesNotRerollEvents() {
        val stars = (0 until 3).associate { Hex(it, 0) to adult(StarType.RED_DWARF) }
        val state = GameState(
            stars = stars,
            event = ActiveEvent(CosmicEvent.STAR_RAIN, 30.0),
            eventCooldown = 0.05,
            parked = mapOf(GalaxyKind.FROST to GalaxyRun()),
        )
        assertNull(galaxies.switchTo(state, GalaxyKind.SPIRAL), "schon aktiv")
        assertNull(galaxies.switchTo(state, GalaxyKind.EMBER), "noch nicht erschlossen")
        val away = assertNotNull(galaxies.switchTo(state, GalaxyKind.FROST))
        val back = assertNotNull(galaxies.switchTo(away, GalaxyKind.SPIRAL))
        assertNull(back.event)
        assertTrue(back.eventCooldown >= Balance.eventInterval(0.0), "${back.eventCooldown}")
        val next = engine.tick(back, 1.0)
        assertNull(next.state.event, "Hin- und Herwechseln würfelt kein neues Ereignis aus")
        assertEquals(state.stats.eventsSeen, next.state.stats.eventsSeen)

        // Ohne laufendes Ereignis bleibt die Wartezeit, wie sie ist.
        val calm = state.copy(event = null, eventCooldown = 77.0)
        assertEquals(77.0, assertNotNull(galaxies.switchTo(calm, GalaxyKind.FROST)).parked.getValue(GalaxyKind.SPIRAL).eventCooldown)

        // Dasselbe gilt für die Offline-Lücke.
        val (offline, _) = engine.applyOffline(state, 60.0)
        assertNull(offline.event)
        assertTrue(offline.eventCooldown >= Balance.eventInterval(0.0), "${offline.eventCooldown}")
    }

    @Test
    fun backgroundGalaxyProducesOwnDustAndLeavesActiveAlone() {
        val frost = GalaxyRun(
            stardust = 100.0,
            stars = mapOf(Hex.ORIGIN to adult(StarType.RED_DWARF, level = 5), Hex(1, 0) to adult(StarType.YELLOW_STAR)),
            law = GalaxyLaw.TIME_DILATION,
        )
        val comet = Comet(3, 0f, 0.2f, 1f, 0.5f, 8.0)
        val state = GameState(
            stars = mapOf(Hex.ORIGIN to adult(StarType.RED_DWARF)),
            comet = comet,
            boostRemaining = 30.0,
            playTime = 50.0,
            totalStardust = 1_000.0,
            parked = mapOf(GalaxyKind.FROST to frost),
        )
        // Zeitdehnung: online nur halb so schnell.
        val income = BoardAnalyzer.analyze(assertNotNull(state.projected(GalaxyKind.FROST))).totalRate * 0.5 * 2.0
        val result = galaxies.tickBackground(state, 2.0, activeIncome = 7.0)
        val after = result.state.parked.getValue(GalaxyKind.FROST)
        assertClose(100.0 + income, after.stardust)
        assertClose(income, after.runStardust)
        assertClose(1_000.0 + income / 3.0, result.state.totalStardust, "in Sternenstaub-Wert")
        assertClose(income / 2.0, result.rates.getValue(GalaxyKind.FROST))
        assertClose(3.5, result.rates.getValue(GalaxyKind.SPIRAL))
        assertTrue(result.inflow.isEmpty())

        // Die aktive Galaxie bleibt unberührt – mit Komet, Kometenrausch und Spielzeit.
        assertEquals(GalaxyKind.SPIRAL, result.state.activeGalaxy)
        assertEquals(state.activeRun(), result.state.activeRun())
        assertEquals(comet, result.state.comet)
        assertEquals(50.0, result.state.playTime)

        // Erfolge prüft der Hintergrund-Schritt einmal auf dem ganzen Zustand – und nur einmal.
        assertTrue(Achievement.SECOND_GALAXY in result.state.achievements)
        assertEquals(1, result.events.count { it == GameEvent.AchievementUnlocked(Achievement.SECOND_GALAXY) })
        assertTrue(galaxies.tickBackground(result.state, 2.0, 0.0).events.none { it is GameEvent.AchievementUnlocked })
    }

    @Test
    fun backgroundEventsAreTaggedAndDiscoveriesFoldIntoMeta() {
        val giant = Star(StarType.BLUE_GIANT, age = StarType.BLUE_GIANT.lifespan!! - 0.5)
        val yellow = Star(StarType.YELLOW_STAR, age = StarType.YELLOW_STAR.lifespan!! - 0.5)
        val frost = GalaxyRun(
            stars = mapOf(Hex(-3, 0) to giant, Hex(0, -3) to yellow) +
                (0 until 3).associate { Hex(it, 0) to adult(StarType.RED_DWARF) },
            ownedFields = Hex.area(3).toSet(),
        )
        val state = GameState(parked = mapOf(GalaxyKind.FROST to frost))
        val result = galaxies.tickBackground(state, 1.0, 0.0)
        val tagged = result.events.filterIsInstance<GameEvent.InBackground>()
        assertTrue(tagged.all { it.galaxy == GalaxyKind.FROST })
        assertTrue(tagged.any { it.event is GameEvent.Supernova })
        assertTrue(tagged.any { it.event == GameEvent.BecameWhiteDwarf(Hex(0, -3)) })
        assertTrue(result.events.none { it is GameEvent.Supernova || it is GameEvent.BecameWhiteDwarf }, "nie ungetarnt")

        // Elemente gehören der Frostgalaxie, Zähler und Entdeckungen allen.
        val after = result.state.parked.getValue(GalaxyKind.FROST)
        assertTrue(after.elements > 0.0)
        assertEquals(0.0, result.state.elements)
        assertEquals(1, after.runSupernovas)
        assertEquals(1, result.state.supernovaCount)
        assertTrue(ConstellationKind.TRIO in result.state.discovered)
        assertTrue(GameEvent.Discovered(ConstellationKind.TRIO) in result.events, "Entdeckungen melden sich wie gewohnt")
    }

    @Test
    fun frozenParkedRunStaysFrozen() {
        val frozen = GalaxyRun(
            galaxyNumber = 0,
            lawChoices = listOf(GalaxyLaw.ENTROPY, GalaxyLaw.NURSERY, GalaxyLaw.SILENCE),
            stars = mapOf(Hex.ORIGIN to adult(StarType.RED_DWARF)),
        )
        val state = GameState(parked = mapOf(GalaxyKind.EMBER to frozen))
        val result = galaxies.tickBackground(state, 1.0, 0.0)
        assertSame(frozen, result.state.parked.getValue(GalaxyKind.EMBER))
        assertEquals(0.0, result.rates.getValue(GalaxyKind.EMBER))
        assertEquals(0.0, assertNotNull(galaxies.ownRates(state)[GalaxyKind.EMBER]))
        val (offline, report) = galaxies.applyOfflineAll(state, 3_600.0)
        assertSame(frozen, offline.parked.getValue(GalaxyKind.EMBER))
        assertTrue(report.galaxies.single { it.kind == GalaxyKind.EMBER }.paused)
    }

    @Test
    fun parkedGoalsShowWhenClaimable() {
        val frost = GalaxyRun(
            stars = mapOf(Hex.ORIGIN to adult(StarType.RED_DWARF)),
            galaxyGoals = listOf(
                GalaxyGoal(GoalKind.STARS_AT_ONCE, 1.0, 2.0, 15),
                GalaxyGoal(GoalKind.STAR_LEVEL, 99.0, 2.0, 15),
                GalaxyGoal(GoalKind.FIELDS_OWNED, 1.0, 2.0, 15, claimed = true),
            ),
        )
        val state = GameState(parked = mapOf(GalaxyKind.FROST to frost, GalaxyKind.EMBER to GalaxyRun()))
        val result = galaxies.tickBackground(state, 1.0, 0.0)
        assertEquals(mapOf(GalaxyKind.FROST to 1, GalaxyKind.EMBER to 0), result.claimable)
    }

    @Test
    fun previewGainAndRatesWithoutRun() {
        assertEquals(0.0, galaxies.previewGain(GameState(), GalaxyKind.FROST), "nicht erschlossen")
        assertEquals(2.0, galaxies.previewGain(GameState(runStardust = 8e6), GalaxyKind.SPIRAL))
        val frost = GameState(parked = mapOf(GalaxyKind.FROST to GalaxyRun(runStardust = 3e6)))
        assertEquals(2.0, galaxies.previewGain(frost, GalaxyKind.FROST), "⌊1 · 2,5⌋")
        val board = GameState(stars = mapOf(Hex.ORIGIN to adult(StarType.RED_DWARF)), law = GalaxyLaw.TIME_DILATION)
        assertEquals(mapOf(GalaxyKind.SPIRAL to BoardAnalyzer.analyze(board).totalRate * 0.5), galaxies.ownRates(board))
    }

    // ------------------------------------------------------------ Erschließen

    @Test
    fun unlockNeedsDarkMatterTimeAndPredecessorCollapse() {
        val t0 = 1_000_000_000L
        val hour = 3_600_000L
        assertEquals(GalaxyKind.FROST, galaxies.nextUnlockable(GameState()))
        assertEquals(UnlockStatus.NEEDS_COLLAPSE, galaxies.unlockStatus(GameState(darkMatter = 1e9)))
        val poor = GameState(galaxyNumber = 2, darkMatter = 99.0, crystals = 30)
        assertEquals(UnlockStatus.NEEDS_DARK_MATTER, galaxies.unlockStatus(poor))
        assertNull(galaxies.startUnlock(poor, t0))

        val ready = poor.copy(darkMatter = 150.0)
        assertEquals(UnlockStatus.READY, galaxies.unlockStatus(ready))
        val (started, event) = assertNotNull(galaxies.startUnlock(ready, t0))
        assertEquals(GameEvent.GalaxyUnlockStarted(GalaxyKind.FROST, t0 + hour), event)
        assertClose(50.0, started.darkMatter)
        assertEquals(GalaxyUnlock(GalaxyKind.FROST, t0, t0 + hour), started.unlock)
        assertEquals(UnlockStatus.RUNNING, galaxies.unlockStatus(started))
        assertNull(galaxies.startUnlock(started, t0), "immer nur eine gleichzeitig")

        // Nach 59 Minuten noch nicht, nach 60 fertig.
        val (waiting, none) = galaxies.completeTimers(started, t0 + 59 * 60_000L)
        assertSame(started, waiting)
        assertTrue(none.isEmpty())
        val (done, events) = galaxies.completeTimers(started, t0 + hour)
        assertTrue(GameEvent.GalaxyUnlocked(GalaxyKind.FROST) in events)
        assertNull(done.unlock)
        val frost = done.parked.getValue(GalaxyKind.FROST)
        assertEquals(0, frost.galaxyNumber)
        assertEquals(3, frost.lawChoices.size)
        assertFalse(GalaxyLaw.NORMAL in frost.lawChoices)
        assertClose(45.0, frost.stardust)
        assertEquals(19, frost.ownedFields.size)
        assertTrue(Achievement.SECOND_GALAXY in done.achievements)
        assertEquals(GalaxyKind.EMBER, galaxies.nextUnlockable(done))

        // Sofort fertig: ein Kristall je angefangene drei Minuten.
        val (skipped, cost) = assertNotNull(galaxies.skipUnlock(started, t0))
        assertEquals(20, cost)
        assertEquals(10, skipped.crystals)
        assertTrue(GalaxyKind.FROST in galaxies.completeTimers(skipped, t0).first.parked)
        assertEquals(10, assertNotNull(galaxies.skipUnlock(started, t0 + hour / 2)).second)
        assertNull(galaxies.skipUnlock(started.copy(crystals = 19), t0), "zu wenig Kristalle")
        assertNull(galaxies.skipUnlock(started, t0 + hour), "schon abgelaufen")

        // Uhr zurückgestellt: Der Timer beginnt neu, statt länger als seine Dauer zu laufen.
        val (rewound, nothing) = galaxies.completeTimers(started, t0 - 5 * hour)
        assertTrue(nothing.isEmpty())
        assertEquals(GalaxyUnlock(GalaxyKind.FROST, t0 - 5 * hour, t0 - 4 * hour), rewound.unlock)
        assertEquals(20, assertNotNull(galaxies.skipUnlock(started, t0 - 5 * hour)).second, "nie teurer als die ganze Dauer")
    }

    @Test
    fun unlockedGalaxyStartsFrozenAndCountsAsCollapsedWhileChoosing() {
        val choices = listOf(GalaxyLaw.ENTROPY, GalaxyLaw.NURSERY, GalaxyLaw.SILENCE)
        val fresh = GalaxyFactory.freshRun(GameState(), GalaxyKind.FROST, GalaxyLaw.NORMAL, 0, "Kepler 22", choices)
        var state = GameState(galaxyNumber = 2, darkMatter = 1e4, parked = mapOf(GalaxyKind.FROST to fresh))
        assertEquals(UnlockStatus.NEEDS_LAW_CHOICE, galaxies.unlockStatus(state), "erst die Naturgesetze der Frostgalaxie")

        state = assertNotNull(engine.chooseGalaxy(assertNotNull(galaxies.switchTo(state, GalaxyKind.FROST)), GalaxyLaw.ENTROPY))
        assertEquals("Kepler 22", state.galaxyName)
        assertEquals(1, state.galaxyNumber)
        assertEquals(UnlockStatus.NEEDS_COLLAPSE, galaxies.unlockStatus(state))

        // Urknall in der Frostgalaxie, das neue Gesetz ist noch offen („Später wählen“): Glut lässt sich erschließen.
        state = assertNotNull(engine.bigBang(state.copy(runStardust = 3e6)))
        assertTrue(state.lawChoices.isNotEmpty())
        assertEquals(UnlockStatus.READY, galaxies.unlockStatus(state))
        assertEquals(GalaxyKind.EMBER, assertNotNull(galaxies.startUnlock(state, 0L)).second.kind)
        assertEquals(2.0, engine.progression.metric(state, Metric.KINDS_COLLAPSED))

        val all = GameState(parked = GalaxyKind.entries.drop(1).associateWith { GalaxyRun() })
        assertEquals(UnlockStatus.ALL_UNLOCKED, galaxies.unlockStatus(all))
        assertNull(galaxies.nextUnlockable(all))
    }

    @Test
    fun pioneerHalvesRunningUnlock() {
        val hour = 3_600_000L
        val now = 4 * hour
        val state = GameState(
            unlock = GalaxyUnlock(GalaxyKind.SHADOW, 0, 24 * hour),
            bridges = listOf(
                StarBridge(GalaxyKind.SPIRAL, GalaxyKind.FROST, 0, 6 * hour),
                StarBridge(GalaxyKind.FROST, GalaxyKind.SPIRAL, 0, 1, built = true),
            ),
            parked = mapOf(GalaxyKind.FROST to GalaxyRun()),
        )
        val pioneer = assertNotNull(engine.shop.grantPurchase(state, StoreProduct.GALAXY_PIONEER, "tx-p", restored = true))
        val faster = galaxies.rescaleTimers(pioneer, now, Balance.PIONEER_TIMER_MULT)
        assertEquals(now + 10 * hour, assertNotNull(faster.unlock).readyAtMs, "20 h Rest werden zu 10 h")
        assertEquals(now + hour, faster.bridges[0].readyAtMs)
        assertEquals(state.bridges[1], faster.bridges[1], "fertige Brücken bleiben")
        assertNull(galaxies.completeTimers(faster, now + 10 * hour - 1).first.parked[GalaxyKind.SHADOW])
        assertNotNull(galaxies.completeTimers(faster, now + 10 * hour).first.parked[GalaxyKind.SHADOW])
        // Neue Timer laufen ohnehin kürzer.
        assertEquals(12 * hour, Balance.unlockMillis(pioneer, GalaxyKind.SHADOW))

        // Raumfaltung: −15 % je Stufe, auch für laufende Timer.
        val folded = galaxies.rescaleTimers(state, now, Balance.SPACE_FOLD_FACTOR)
        assertEquals(now + (20 * hour * 0.85).toLong(), assertNotNull(folded.unlock).readyAtMs)
        val idle = GameState()
        assertSame(idle, galaxies.rescaleTimers(idle, now, 0.5))
    }

    // ------------------------------------------------------------ Sternenbrücken

    @Test
    fun bridgeIncomeDoesNotCountTowardDarkMatter() {
        val frost = GalaxyRun(stardust = 10.0, runStardust = 3e6, stars = mapOf(Hex.ORIGIN to adult(StarType.RED_DWARF)))
        val state = GameState(
            stars = (0 until 3).associate { Hex(it, 0) to adult(StarType.RED_DWARF, level = 60) },
            totalStardust = 1_000.0,
            achievements = Achievement.entries.toSet(),
            parked = mapOf(GalaxyKind.FROST to frost),
            bridges = listOf(StarBridge(GalaxyKind.SPIRAL, GalaxyKind.FROST, 0, 0, built = true)),
        )
        val gainBefore = Balance.darkMatterGain(assertNotNull(state.projected(GalaxyKind.FROST)))

        // 10 % von 100 zum Kurs 3 sind 30, gedeckelt bei 50 % von 50.
        val (next, inflow) = galaxies.applyBridges(state, mapOf(GalaxyKind.SPIRAL to 100.0, GalaxyKind.FROST to 50.0))
        assertClose(25.0, inflow.getValue(GalaxyKind.FROST))
        val after = next.parked.getValue(GalaxyKind.FROST)
        assertClose(35.0, after.stardust)
        assertEquals(3e6, after.runStardust, "Brückenstaub zählt nicht für den Urknall")
        assertEquals(gainBefore, Balance.darkMatterGain(assertNotNull(next.projected(GalaxyKind.FROST))))
        assertClose(1_000.0 + 25.0 / 3.0, next.totalStardust)
        assertEquals(state.stardust, next.stardust, "die Quelle gibt nichts ab")

        // Über den Hintergrund-Schritt: runStardust wächst nur um das eigene Einkommen.
        val own = BoardAnalyzer.analyze(assertNotNull(state.projected(GalaxyKind.FROST))).totalRate
        val result = galaxies.tickBackground(state, 1.0, activeIncome = 1e9)
        val ticked = result.state.parked.getValue(GalaxyKind.FROST)
        assertClose(Balance.bridgeCap(state) * own, result.inflow.getValue(GalaxyKind.FROST))
        assertClose(3e6 + own, ticked.runStardust)
        assertClose(10.0 + own + result.inflow.getValue(GalaxyKind.FROST), ticked.stardust)

        // In die aktive Galaxie: ebenso nur der ausgebbare Staub.
        val reverse = state.copy(bridges = listOf(StarBridge(GalaxyKind.FROST, GalaxyKind.SPIRAL, 0, 0, built = true)))
        val (credited, flow) = galaxies.applyBridges(reverse, mapOf(GalaxyKind.SPIRAL to 100.0, GalaxyKind.FROST to 50.0))
        assertClose(0.1 * 50.0 / 3.0, flow.getValue(GalaxyKind.SPIRAL))
        assertClose(state.stardust + 0.1 * 50.0 / 3.0, credited.stardust)
        assertEquals(state.runStardust, credited.runStardust)
    }

    @Test
    fun twoWayBridgesDoNotCompound() {
        val stars = mapOf(Hex.ORIGIN to adult(StarType.RED_DWARF, level = 10))
        var state = GameState(
            stars = stars,
            achievements = Achievement.entries.toSet(),
            parked = mapOf(GalaxyKind.FROST to GalaxyRun(stars = stars)),
            bridges = listOf(
                StarBridge(GalaxyKind.SPIRAL, GalaxyKind.FROST, 0, 0, built = true),
                StarBridge(GalaxyKind.FROST, GalaxyKind.SPIRAL, 0, 0, built = true),
            ),
            upgrades = mapOf(Upgrade.BRIDGE_CRAFT to 4),
        )
        val rate = BoardAnalyzer.analyze(state).totalRate
        val inflows = ArrayList<Map<GalaxyKind, Double>>()
        repeat(200) {
            val active = engine.tick(state, 1.0)
            val background = galaxies.tickBackground(active.state, 1.0, active.ownIncome)
            assertClose(rate, background.rates.getValue(GalaxyKind.SPIRAL), "eigenes Einkommen ohne Brückenstaub")
            inflows += background.inflow
            state = background.state
        }
        // Spirale → Frost: 30 % · Kurs 3 = 0,9 · Rate; Frost → Spirale: 30 % ÷ 3 = 0,1 · Rate. Und so bleibt es.
        assertClose(0.9 * rate, inflows.first().getValue(GalaxyKind.FROST))
        assertClose(0.1 * rate, inflows.first().getValue(GalaxyKind.SPIRAL))
        for (kind in listOf(GalaxyKind.SPIRAL, GalaxyKind.FROST)) {
            assertClose(inflows.first().getValue(kind), inflows.last().getValue(kind), "$kind")
        }
    }

    @Test
    fun frozenTargetReceivesNothing() {
        val choosing = GalaxyRun(galaxyNumber = 0, lawChoices = listOf(GalaxyLaw.ENTROPY))
        val state = GameState(
            parked = mapOf(GalaxyKind.FROST to choosing),
            bridges = listOf(StarBridge(GalaxyKind.SPIRAL, GalaxyKind.FROST, 0, 0, built = true)),
        )
        val incomes = mapOf(GalaxyKind.SPIRAL to 100.0, GalaxyKind.FROST to 100.0)
        val (next, inflow) = galaxies.applyBridges(state, incomes)
        assertTrue(inflow.isEmpty())
        assertSame(state, next)

        // Auch eine aktive Galaxie, die gerade ihr Gesetz wählt, bekommt nichts.
        val activeChoosing = GameState(
            lawChoices = listOf(GalaxyLaw.ENTROPY),
            parked = mapOf(GalaxyKind.FROST to GalaxyRun()),
            bridges = listOf(StarBridge(GalaxyKind.FROST, GalaxyKind.SPIRAL, 0, 0, built = true)),
        )
        assertTrue(galaxies.applyBridges(activeChoosing, incomes).second.isEmpty())
        // Und die Große Stille lässt keine Brücke herein.
        val silent = state.copy(parked = mapOf(GalaxyKind.FROST to GalaxyRun(law = GalaxyLaw.SILENCE)))
        assertTrue(galaxies.applyBridges(silent, incomes).second.isEmpty())
    }

    @Test
    fun bridgeRulesLimitIncomingAndConstruction() {
        val state = GameState(
            darkMatter = 520.0,
            crystals = 100,
            parked = mapOf(GalaxyKind.FROST to GalaxyRun(), GalaxyKind.EMBER to GalaxyRun()),
        )
        assertEquals(BridgeBlock.SAME_GALAXY, galaxies.bridgeBlock(state, GalaxyKind.FROST, GalaxyKind.FROST))
        assertEquals(BridgeBlock.MISSING_GALAXY, galaxies.bridgeBlock(state, GalaxyKind.SPIRAL, GalaxyKind.AURORA))
        assertEquals(BridgeBlock.NONE, galaxies.bridgeBlock(state, GalaxyKind.SPIRAL, GalaxyKind.FROST))
        assertEquals(BridgeBlock.DARK_MATTER, galaxies.bridgeBlock(state.copy(darkMatter = 49.0), GalaxyKind.SPIRAL, GalaxyKind.FROST))

        val (building, started) = assertNotNull(galaxies.buildBridge(state, GalaxyKind.SPIRAL, GalaxyKind.FROST, 0L))
        assertEquals(StarBridge(GalaxyKind.SPIRAL, GalaxyKind.FROST, 0, 1_800_000), started.bridge)
        assertClose(470.0, building.darkMatter)
        assertEquals(BridgeBlock.UNDER_CONSTRUCTION, galaxies.bridgeBlock(building, GalaxyKind.FROST, GalaxyKind.SPIRAL))
        assertEquals(BridgeBlock.TARGET_TAKEN, galaxies.bridgeBlock(building, GalaxyKind.EMBER, GalaxyKind.FROST))
        assertTrue(galaxies.applyBridges(building, mapOf(GalaxyKind.SPIRAL to 100.0, GalaxyKind.FROST to 100.0)).second.isEmpty(), "im Bau fließt nichts")

        // Sofort fertig: 30 Minuten kosten 10 Kristalle.
        val (skipped, cost) = assertNotNull(galaxies.skipBridge(building, GalaxyKind.SPIRAL, GalaxyKind.FROST, 0L))
        assertEquals(10, cost)
        assertEquals(90, skipped.crystals)
        val (built, events) = galaxies.completeTimers(skipped, 0L)
        val bridge = built.bridges.single()
        assertTrue(bridge.built)
        assertTrue(GameEvent.BridgeCompleted(bridge) in events)
        assertEquals(1L, built.stats.bridgesBuilt)
        assertTrue(Achievement.FIRST_BRIDGE in built.achievements)
        assertTrue(galaxies.completeTimers(building, 1_800_000).first.bridges.single().built, "oder einfach warten")

        // Die zweite Brücke kostet 500 und braucht zwei Stunden.
        assertEquals(BridgeBlock.DARK_MATTER, galaxies.bridgeBlock(built, GalaxyKind.FROST, GalaxyKind.SPIRAL))
        val second = assertNotNull(galaxies.buildBridge(built.copy(darkMatter = 500.0), GalaxyKind.FROST, GalaxyKind.SPIRAL, 0L)).first
        assertEquals(0.0, second.darkMatter)
        assertEquals(7_200_000L, second.bridges.last().readyAtMs)

        // Abreißen: sofort, kostenlos, ohne Erstattung. Die Chronik zählt weiter, der Messwert für Erfolge nicht.
        val torn = galaxies.removeBridge(built, GalaxyKind.SPIRAL, GalaxyKind.FROST)
        assertTrue(torn.bridges.isEmpty())
        assertEquals(built.darkMatter, torn.darkMatter)
        assertEquals(1L, torn.stats.bridgesBuilt)
        assertEquals(0.0, engine.progression.metric(torn, Metric.BRIDGES))
        assertClose(50.0, Balance.bridgeCost(torn))
        assertSame(torn, galaxies.removeBridge(torn, GalaxyKind.SPIRAL, GalaxyKind.FROST))
    }

    // ------------------------------------------------------------ Offline

    @Test
    fun offlineReportListsEveryGalaxyWithLimitedSteps() {
        assertEquals(720, Balance.activeOfflineSteps(0))
        assertEquals(630, Balance.activeOfflineSteps(1))
        assertEquals(360, Balance.activeOfflineSteps(4))
        assertEquals(720, Balance.activeOfflineSteps(4) + 4 * Balance.PARKED_OFFLINE_STEPS)

        val frost = GalaxyRun(
            stardust = 0.0,
            stars = mapOf(Hex.ORIGIN to adult(StarType.RED_DWARF, level = 10)),
            law = GalaxyLaw.TIME_DILATION,
            galaxyName = "Kepler 22",
        )
        val ember = GalaxyRun(galaxyNumber = 0, lawChoices = listOf(GalaxyLaw.ENTROPY), galaxyName = "Andros 812")
        val state = GameState(
            stars = mapOf(Hex.ORIGIN to adult(StarType.RED_DWARF, level = 20)),
            playTime = 100.0,
            achievements = Achievement.entries.toSet(),
            parked = mapOf(GalaxyKind.FROST to frost, GalaxyKind.EMBER to ember),
            bridges = listOf(StarBridge(GalaxyKind.SPIRAL, GalaxyKind.FROST, 0, 0, built = true)),
        )
        val spiralRate = BoardAnalyzer.analyze(state).totalRate
        val frostRate = BoardAnalyzer.analyze(assertNotNull(state.projected(GalaxyKind.FROST))).totalRate
        val (after, report) = galaxies.applyOfflineAll(state, 3_600.0)

        assertClose(spiralRate * 3_600.0, report.stardust, "oben steht die aktive Galaxie")
        assertEquals(listOf(GalaxyKind.SPIRAL, GalaxyKind.FROST, GalaxyKind.EMBER), report.galaxies.map { it.kind })
        val frostLine = report.galaxies[1]
        assertEquals("Kepler 22", frostLine.name)
        assertClose(frostRate * 3_600.0 * 3.0, frostLine.stardust, "Zeitdehnung: offline ×3")
        assertClose(min(0.1 * spiralRate * 3_600.0 * 3.0, 0.5 * frostLine.stardust), frostLine.bridged)
        val frostAfter = after.parked.getValue(GalaxyKind.FROST)
        assertClose(frostLine.stardust + frostLine.bridged, frostAfter.stardust)
        assertClose(frostLine.stardust, frostAfter.runStardust)
        assertTrue(report.galaxies[2].paused)
        assertEquals("Andros 812", report.galaxies[2].name)
        assertEquals(ember, after.parked.getValue(GalaxyKind.EMBER))
        assertClose(100.0 + 3_600.0, after.playTime, "die Spielzeit zählt nur einmal")

        // Mit nur einer Galaxie bleibt alles wie bisher.
        val (_, single) = galaxies.applyOfflineAll(GameState(stars = state.stars), 3_600.0)
        assertTrue(single.galaxies.isEmpty())

        // Fertige Timer kommen nachträglich in den Bericht.
        val done = StarBridge(GalaxyKind.FROST, GalaxyKind.EMBER, 0, 1, built = true)
        val withTimers = report.withTimers(listOf(GameEvent.GalaxyUnlocked(GalaxyKind.AURORA), GameEvent.BridgeCompleted(done)))
        assertEquals(listOf(GalaxyKind.AURORA), withTimers.unlocked)
        assertEquals(listOf(done), withTimers.bridgesCompleted)
    }

    // ------------------------------------------------------------ Shop und Missionen

    @Test
    fun warpBoostAndLoginActOnTheActiveGalaxy() {
        val spiral = GalaxyRun(stardust = 5.0, stars = mapOf(Hex.ORIGIN to adult(StarType.RED_DWARF, level = 10)))
        val state = GameState(
            activeGalaxy = GalaxyKind.FROST,
            stardust = 0.0,
            stars = mapOf(Hex.ORIGIN to adult(StarType.RED_DWARF, level = 20)),
            crystals = 1_000,
            parked = mapOf(GalaxyKind.SPIRAL to spiral),
        )
        val rate = BoardAnalyzer.analyze(state).totalRate
        val (warped, amount) = assertNotNull(engine.shop.buyOffer(state, CrystalOffer.WARP_1H))
        assertClose(rate * 3_600.0, amount)
        assertClose(amount, warped.stardust)
        assertClose(amount, warped.runStardust)
        assertClose(amount / 3.0, warped.totalStardust, "in Sternenstaub-Wert")
        assertEquals(spiral, warped.parked.getValue(GalaxyKind.SPIRAL))

        val (boosted, _) = assertNotNull(engine.shop.buyOffer(state, CrystalOffer.BOOST))
        val home = assertNotNull(galaxies.switchTo(boosted, GalaxyKind.SPIRAL))
        assertEquals(0.0, home.boostRemaining)
        assertEquals(300.0, home.parked.getValue(GalaxyKind.FROST).boostRemaining, "der Kometenrausch gehört der Frostgalaxie")
        assertEquals(300.0, galaxies.tickBackground(home, 10.0, 0.0).state.parked.getValue(GalaxyKind.FROST).boostRemaining, "und ruht, solange sie geparkt ist")

        val (claimed, _, warp) = assertNotNull(engine.progression.claimLoginReward(state.copy(pendingLoginReward = LoginReward.DAY_2)))
        assertTrue(warp > 0.0)
        assertClose(warp / 3.0, claimed.totalStardust)
    }

    @Test
    fun missionsCoverEveryGalaxy() {
        val p = engine.progression
        val stars = mapOf(Hex.ORIGIN to adult(StarType.RED_DWARF, level = 40))
        val single = GameState(stars = stars)
        val multi = single.copy(parked = mapOf(GalaxyKind.FROST to GalaxyRun(stars = stars)))
        val rate = BoardAnalyzer.analyze(single).totalRate
        val days = 0L until 120L
        val singleMissions = days.flatMap { p.rollMissions(single, it) }
        val multiMissions = days.flatMap { p.rollMissions(multi, it) }

        assertTrue(singleMissions.none { it.metric == Metric.BIG_BANGS }, "ein Urknall am Tag erst mit mehreren Galaxien")
        val bigBang = multiMissions.first { it.metric == Metric.BIG_BANGS }
        assertEquals(1.0, bigBang.target)
        assertEquals(20, bigBang.crystals)
        val fields = singleMissions.filter { it.metric == Metric.FIELDS_BOUGHT }
        assertTrue(fields.isNotEmpty() && fields.all { it.target in 4.0..10.0 }, "$fields")

        // Staub-Missionen: eine Viertelstunde Produktion aller Galaxien in Sternenstaub-Wert, auf zwei Stellen gerundet.
        val singleDust = singleMissions.first { it.metric == Metric.TOTAL_STARDUST }.target
        val multiDust = multiMissions.first { it.metric == Metric.TOTAL_STARDUST }.target
        assertTrue(singleDust >= rate * 900.0 && singleDust <= rate * 900.0 * 1.1, "$singleDust")
        val expected = (rate + rate / 3.0) * 900.0
        assertTrue(multiDust >= expected && multiDust <= expected * 1.1, "$multiDust statt $expected")
    }

    private companion object {
        /** Felder von GameState, die allen Galaxien gemeinsam sind. Alle übrigen stehen in GalaxyRun. */
        val META_FIELDS = setOf(
            "darkMatter", "totalStardust", "unlocked", "discovered", "supernovaCount", "nextCometId", "playTime",
            "lastSavedEpochMs", "crystals", "stats", "achievements", "missions", "missionDay", "loginStreak",
            "lastLoginDay", "pendingLoginReward", "artifacts", "capsules", "ownedThemes", "activeTheme", "ownedSparks",
            "activeSpark", "entitlements", "processedTransactions", "balanceVersion", "activeGalaxy", "unlock", "bridges",
            "parked",
        )
    }
}
