package de.vcxrisi.sternengarten.game

import de.vcxrisi.sternengarten.game.engine.Balance
import de.vcxrisi.sternengarten.game.engine.BoardAnalyzer
import de.vcxrisi.sternengarten.game.engine.GameEngine
import de.vcxrisi.sternengarten.game.engine.GameEvent
import de.vcxrisi.sternengarten.game.model.Achievement
import de.vcxrisi.sternengarten.game.model.ActiveEvent
import de.vcxrisi.sternengarten.game.model.Artifact
import de.vcxrisi.sternengarten.game.model.Comet
import de.vcxrisi.sternengarten.game.model.ConstellationKind
import de.vcxrisi.sternengarten.game.model.CosmicEvent
import de.vcxrisi.sternengarten.game.model.CrystalOffer
import de.vcxrisi.sternengarten.game.model.GameState
import de.vcxrisi.sternengarten.game.model.Hex
import de.vcxrisi.sternengarten.game.model.LoginReward
import de.vcxrisi.sternengarten.game.model.Metric
import de.vcxrisi.sternengarten.game.model.NebulaTheme
import de.vcxrisi.sternengarten.game.model.Rarity
import de.vcxrisi.sternengarten.game.model.Star
import de.vcxrisi.sternengarten.game.model.StarType
import de.vcxrisi.sternengarten.game.model.StoreProduct
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContentTest {

    private val engine = GameEngine(Random(7))

    private fun adult(type: StarType, level: Int = 1) = Star(type, level = level, age = Balance.PROTO_SECONDS)

    private fun board(vararg stars: Pair<Hex, Star>) = GameState(stars = stars.toMap())

    private fun rateOf(state: GameState, hex: Hex) = BoardAnalyzer.analyze(state).breakdown.getValue(hex).rate

    private fun assertClose(expected: Double, actual: Double) =
        assertTrue(abs(expected - actual) < 1e-6 * maxOf(1.0, abs(expected)), "expected $expected but was $actual")

    // ------------------------------------------------------------ Neue Sternarten

    @Test
    fun neutronStarRaisesNeighborLevels() {
        val alone = board(Hex.ORIGIN to adult(StarType.RED_DWARF))
        val dense = board(Hex.ORIGIN to adult(StarType.RED_DWARF), Hex(1, 0) to adult(StarType.NEUTRON_STAR))
        // Stufe 1 + 5 = 6 → Faktor 6 statt 1.
        assertClose(rateOf(alone, Hex.ORIGIN) * Balance.levelMultiplier(6), rateOf(dense, Hex.ORIGIN))
    }

    @Test
    fun magnetarBoostsOnlyRingTwo() {
        val state = board(
            Hex.ORIGIN to adult(StarType.MAGNETAR),
            Hex(1, 0) to adult(StarType.RED_DWARF),
            Hex(2, 0) to adult(StarType.RED_DWARF),
            Hex(0, -2) to adult(StarType.RED_DWARF),
        )
        val analysis = BoardAnalyzer.analyze(state)
        assertClose(0.0, analysis.breakdown.getValue(Hex(1, 0)).aura)
        assertClose(Balance.MAGNETAR_AURA, analysis.breakdown.getValue(Hex(2, 0)).aura)
        assertClose(Balance.MAGNETAR_AURA, analysis.breakdown.getValue(Hex(0, -2)).aura)
    }

    @Test
    fun nebulaNurseryStopsAging() {
        val lifespan = StarType.BLUE_GIANT.lifespan!!
        val state = board(
            Hex.ORIGIN to Star(StarType.BLUE_GIANT, age = lifespan - 0.05),
            Hex(1, 0) to adult(StarType.NEBULA_NURSERY),
        )
        val next = engine.tick(state, 10.0).state
        assertTrue(Hex.ORIGIN in next.stars, "geschützter Stern explodiert nicht")
        assertClose(lifespan - 0.05, next.stars.getValue(Hex.ORIGIN).age)
        assertClose(Balance.NURSERY_AURA, BoardAnalyzer.analyze(next).breakdown.getValue(Hex.ORIGIN).aura)
    }

    @Test
    fun quasarScalesWithGardenSize() {
        val stars = (0 until 4).associate { Hex(it, 2) to adult(StarType.RED_DWARF) } +
            (Hex(0, -2) to adult(StarType.QUASAR))
        val with = GameState(stars = stars)
        val without = GameState(stars = stars - Hex(0, -2))
        // 5 Sterne × 3 % = +15 % für jeden anderen Stern.
        assertClose(rateOf(without, Hex(0, 2)) * 1.15, rateOf(with, Hex(0, 2)))
    }

    @Test
    fun newConstellationsAreDetected() {
        val ladder = GameState(stars = (0 until 5).associate { Hex(it, 0) to adult(StarType.YELLOW_STAR) })
        assertTrue(BoardAnalyzer.findConstellations(ladder).any { it.kind == ConstellationKind.LADDER })

        val kilonova = board(Hex.ORIGIN to adult(StarType.NEUTRON_STAR), Hex(0, 1) to adult(StarType.NEUTRON_STAR))
        assertEquals(1, BoardAnalyzer.findConstellations(kilonova).count { it.kind == ConstellationKind.KILONOVA })

        val throne = GameState(
            stars = Hex.ORIGIN.neighbors().associateWith { adult(StarType.RED_DWARF) } + (Hex.ORIGIN to adult(StarType.QUASAR)),
        )
        assertTrue(BoardAnalyzer.findConstellations(throne).any { it.kind == ConstellationKind.QUASAR_THRONE })
    }

    // ------------------------------------------------------------ Ereignisse

    @Test
    fun solarStormTriplesYellowAndBlueStars() {
        val calm = board(Hex.ORIGIN to adult(StarType.YELLOW_STAR), Hex(3, 0) to adult(StarType.RED_DWARF))
        val storm = calm.copy(event = ActiveEvent(CosmicEvent.SOLAR_STORM, 10.0))
        assertClose(rateOf(calm, Hex.ORIGIN) * 3.0, rateOf(storm, Hex.ORIGIN))
        assertClose(rateOf(calm, Hex(3, 0)), rateOf(storm, Hex(3, 0)))
        assertClose(2.0, Balance.agingMultiplier(storm))
    }

    @Test
    fun starRainHalvesCosts() {
        val state = GameState(event = ActiveEvent(CosmicEvent.STAR_RAIN, 10.0))
        assertClose(StarType.RED_DWARF.baseCost * 0.5, Balance.starCost(state, StarType.RED_DWARF))
    }

    @Test
    fun eventsStartAndEnd() {
        val stars = (0 until 3).associate { Hex(it, 0) to adult(StarType.RED_DWARF) }
        var state = GameState(stars = stars, eventCooldown = 0.05)
        val started = engine.tick(state, 0.1)
        assertNotNull(started.state.event)
        assertTrue(started.events.any { it is GameEvent.EventStarted })
        assertEquals(1, started.state.stats.eventsSeen)

        state = started.state.copy(event = started.state.event!!.copy(remaining = 0.05))
        val ended = engine.tick(state, 0.1)
        assertNull(ended.state.event)
        assertTrue(ended.events.any { it is GameEvent.EventEnded })
        assertTrue(ended.state.eventCooldown > 0)
    }

    @Test
    fun eventsDoNotHappenOffline() {
        val stars = (0 until 3).associate { Hex(it, 0) to adult(StarType.RED_DWARF) }
        val state = GameState(stars = stars, eventCooldown = 0.05)
        assertNull(engine.tick(state, 1.0, offline = true).state.event)
    }

    @Test
    fun meteorsAlwaysGiveStardust() {
        val state = board(Hex.ORIGIN to adult(StarType.RED_DWARF)).copy(
            comet = Comet(1, 0f, 0f, 1f, 1f, 3.0, meteor = true),
        )
        repeat(10) {
            val (_, event) = assertNotNull(engine.catchComet(state))
            assertTrue(event is GameEvent.CometStardust && event.meteor)
        }
    }

    // ------------------------------------------------------------ Fortschritt

    @Test
    fun achievementsGrantCrystalsOnce() {
        val state = GameState(stardust = 100.0)
        val planted = assertNotNull(engine.plant(state, Hex.ORIGIN, StarType.RED_DWARF))
        val result = engine.tick(planted, 0.1)
        assertTrue(Achievement.FIRST_STAR in result.state.achievements)
        assertEquals(Achievement.FIRST_STAR.crystals, result.state.crystals)
        assertTrue(result.events.any { it is GameEvent.AchievementUnlocked })
        // Kein zweites Mal.
        assertEquals(result.state.crystals, engine.tick(result.state, 0.1).state.crystals)
        assertClose(1.0 + Balance.ACHIEVEMENT_BONUS, Balance.globalMultiplier(result.state))
    }

    @Test
    fun loginStreakAdvancesAndResets() {
        val p = engine.progression
        var state = p.refreshDaily(GameState(), today = 100)
        assertEquals(1, state.loginStreak)
        assertEquals(LoginReward.DAY_1, state.pendingLoginReward)

        val (claimed, reward, _) = assertNotNull(p.claimLoginReward(state))
        assertEquals(LoginReward.DAY_1, reward)
        assertEquals(10, claimed.crystals)
        assertNull(p.claimLoginReward(claimed), "nur einmal pro Tag")

        state = p.refreshDaily(claimed, today = 101)
        assertEquals(2, state.loginStreak)
        assertEquals(LoginReward.DAY_2, state.pendingLoginReward)

        state = p.refreshDaily(state, today = 105)
        assertEquals(1, state.loginStreak, "Serie reißt nach ausgelassenen Tagen")
    }

    @Test
    fun dailyMissionsTrackProgressFromBaseline() {
        val p = engine.progression
        val base = GameState(stats = GameState().stats.copy(starsPlanted = 40))
        val state = p.refreshDaily(base, today = 5)
        assertEquals(3, state.missions.size)
        assertEquals(state.missions, p.refreshDaily(state, today = 5).missions, "gleicher Tag, gleiche Missionen")

        val mission = de.vcxrisi.sternengarten.game.model.Mission(Metric.STARS_PLANTED, 5.0, baseline = 40.0, crystals = 10)
        val withMission = state.copy(missions = listOf(mission))
        assertNull(p.claimMission(withMission, 0))
        val done = withMission.copy(stats = withMission.stats.copy(starsPlanted = 45))
        assertClose(5.0, p.missionProgress(done, mission))
        val claimed = assertNotNull(p.claimMission(done, 0))
        assertEquals(done.crystals + 10, claimed.crystals)
        assertEquals(1, claimed.stats.missionsCompleted)
        assertNull(p.claimMission(claimed, 0))
    }

    @Test
    fun galaxyGoalsPayOutAndBonusCapsule() {
        val p = engine.progression
        val state = p.refreshDaily(GameState(), today = 1)
        assertEquals(3, state.galaxyGoals.size)
        // Alle Ziele künstlich erfüllen.
        val goals = state.galaxyGoals.map { it.copy(target = 0.0) }
        var current = state.copy(galaxyGoals = goals)
        var allDone = false
        for (i in goals.indices) {
            val (next, done) = assertNotNull(p.claimGoal(current, i))
            current = next
            allDone = done
        }
        assertTrue(allDone)
        assertEquals(1, current.capsules)
        assertClose(goals.sumOf { it.darkMatter }, current.darkMatter)
    }

    @Test
    fun newGalaxyGetsFreshGoals() {
        val state = GameState(runStardust = 4e6, galaxyGoals = listOf())
        val reset = assertNotNull(engine.bigBang(state))
        assertTrue(reset.galaxyGoals.isEmpty())
        val next = assertNotNull(engine.chooseGalaxy(reset, reset.lawChoices.first()))
        assertEquals(3, next.galaxyGoals.size)
        assertEquals(1, next.stats.bigBangs)
    }

    // ------------------------------------------------------------ Shop und Artefakte

    @Test
    fun capsulesLevelArtifactsAndRefundWhenMaxed() {
        val shop = engine.shop
        var state = GameState(capsules = 200)
        repeat(200) { state = assertNotNull(shop.openCapsule(state)).first }
        assertEquals(0, state.capsules)
        assertEquals(200, state.stats.capsulesOpened)
        assertTrue(state.artifacts.values.all { it in 1..Artifact.MAX_LEVEL })
        assertTrue(state.artifacts.containsKey(Artifact.SEXTANT), "häufige Artefakte tauchen auf")
        assertNull(shop.openCapsule(state))

        val maxed = GameState(capsules = 1, artifacts = Artifact.entries.associateWith { Artifact.MAX_LEVEL })
        val (after, event) = assertNotNull(shop.openCapsule(maxed))
        assertEquals(Balance.MAXED_ARTIFACT_REFUND, event.refund)
        assertEquals(Balance.MAXED_ARTIFACT_REFUND, after.crystals)
    }

    @Test
    fun dropChancesSumToOne() {
        assertClose(1.0, Rarity.entries.sumOf { engine.shop.dropChance(it) })
    }

    @Test
    fun artifactsAffectBalance() {
        val state = GameState(artifacts = mapOf(Artifact.SEXTANT to 2, Artifact.PRIMORDIAL_CRYSTAL to 1))
        assertClose(1.16 * 1.25, Balance.globalMultiplier(state))
        val chrono = GameState(artifacts = mapOf(Artifact.CHRONOMETER to 2))
        assertClose(Balance.BASE_OFFLINE_SECONDS + 3600.0, Balance.maxOfflineSeconds(chrono))
    }

    @Test
    fun crystalOffersCostCrystals() {
        val shop = engine.shop
        assertNull(shop.buyOffer(GameState(crystals = 10), CrystalOffer.CAPSULE))
        val (bought, _) = assertNotNull(shop.buyOffer(GameState(crystals = 120), CrystalOffer.CAPSULE))
        assertEquals(20, bought.crystals)
        assertEquals(1, bought.capsules)

        val producing = board(Hex.ORIGIN to adult(StarType.RED_DWARF)).copy(crystals = 40)
        val (warped, amount) = assertNotNull(shop.buyOffer(producing, CrystalOffer.WARP_1H))
        assertClose(BoardAnalyzer.analyze(producing).totalRate * 3600.0, amount)
        assertClose(producing.stardust + amount, warped.stardust)
    }

    @Test
    fun themesAreBoughtOnceThenSelected() {
        val shop = engine.shop
        val state = GameState(crystals = NebulaTheme.AURORA.price)
        val bought = assertNotNull(shop.buyTheme(state, NebulaTheme.AURORA))
        assertEquals(0, bought.crystals)
        assertEquals(NebulaTheme.AURORA, bought.activeTheme)
        val back = assertNotNull(shop.buyTheme(bought, NebulaTheme.GALAXY))
        assertEquals(NebulaTheme.GALAXY, back.activeTheme)
        val again = assertNotNull(shop.buyTheme(back, NebulaTheme.AURORA))
        assertEquals(0, again.crystals, "schon besessen – kostet nichts mehr")
    }

    @Test
    fun purchasesAreGrantedExactlyOnce() {
        val shop = engine.shop
        val state = GameState()
        val first = assertNotNull(shop.grantPurchase(state, StoreProduct.CRYSTALS_M, "tx-1"))
        assertEquals(550, first.crystals)
        assertNull(shop.grantPurchase(first, StoreProduct.CRYSTALS_M, "tx-1"), "gleiche Transaktion nicht doppelt")
        val second = assertNotNull(shop.grantPurchase(first, StoreProduct.CRYSTALS_M, "tx-2"))
        assertEquals(1100, second.crystals)
    }

    @Test
    fun starterPackGrantsContentButRestoreOnlyEntitlement() {
        val shop = engine.shop
        val bought = assertNotNull(shop.grantPurchase(GameState(), StoreProduct.STARTER_PACK, "tx-a"))
        assertTrue(bought.owns(StoreProduct.STARTER_PACK))
        assertEquals(300, bought.crystals)
        assertEquals(3, bought.capsules)
        assertTrue(NebulaTheme.ROYAL_GOLD in bought.ownedThemes)
        assertNull(shop.grantPurchase(bought, StoreProduct.STARTER_PACK, "tx-b"), "einmalig")

        val restored = assertNotNull(shop.grantPurchase(GameState(), StoreProduct.STARTER_PACK, "tx-a", restored = true))
        assertEquals(0, restored.crystals)
        assertTrue(NebulaTheme.ROYAL_GOLD in restored.ownedThemes)
    }

    @Test
    fun wandererPassDoublesProductionAndCatchesComets() {
        val shop = engine.shop
        val base = board(Hex.ORIGIN to adult(StarType.RED_DWARF))
        val pass = assertNotNull(shop.grantPurchase(base, StoreProduct.WANDERER_PASS, "tx-p"))
        assertClose(Balance.globalMultiplier(base) * 2.0, Balance.globalMultiplier(pass))
        assertClose(Balance.maxOfflineSeconds(base) + Balance.PASS_OFFLINE_SECONDS, Balance.maxOfflineSeconds(pass))

        val withComet = pass.copy(comet = Comet(9, 0f, 0f, 1f, 1f, 8.0, elapsed = 0.95), cometCooldown = 999.0)
        val result = engine.tick(withComet, 0.1)
        assertNull(result.state.comet)
        assertEquals(1, result.state.stats.cometsCaught)
        assertFalse(result.events.none { it is GameEvent.CometStardust || it is GameEvent.CometBoost })
    }
}
