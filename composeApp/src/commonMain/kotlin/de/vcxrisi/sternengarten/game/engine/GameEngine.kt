package de.vcxrisi.sternengarten.game.engine

import de.vcxrisi.sternengarten.game.model.ActiveEvent
import de.vcxrisi.sternengarten.game.model.Comet
import de.vcxrisi.sternengarten.game.model.CosmicEvent
import de.vcxrisi.sternengarten.game.model.Currency
import de.vcxrisi.sternengarten.game.model.GalaxyLaw
import de.vcxrisi.sternengarten.game.model.GameState
import de.vcxrisi.sternengarten.game.model.Hex
import de.vcxrisi.sternengarten.game.model.Star
import de.vcxrisi.sternengarten.game.model.StarFate
import de.vcxrisi.sternengarten.game.model.StarType
import de.vcxrisi.sternengarten.game.model.StoreProduct
import de.vcxrisi.sternengarten.game.model.Upgrade
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Die gesamte Spiellogik als reine Funktionen auf [GameState].
 * Aktionen geben `null` zurück, wenn sie nicht möglich sind.
 */
class GameEngine(private val random: Random = Random.Default) {

    val progression = ProgressionSystem(random)
    val shop = ShopSystem(random)

    // ---------------------------------------------------------------- Zeit

    fun tick(state: GameState, dt: Double, offline: Boolean = false): TickResult {
        if (dt <= 0.0 || state.lawChoices.isNotEmpty()) return TickResult(state, emptyList())
        val events = ArrayList<GameEvent>()
        val analysis = BoardAnalyzer.analyze(state)
        val law = state.law

        val boostActive = state.boostRemaining > 0.0
        val timeMult = (if (offline) law.offlineMult else law.onlineMult) *
            (if (boostActive && !offline) Balance.COMET_BOOST_MULT else 1.0)

        val income = analysis.totalRate * timeMult * dt
        var elements = state.elements
        var supernovas = 0
        val enrichment = state.enrichment.toMutableMap()
        val stars = state.stars.toMutableMap()
        val agingMult = Balance.agingMultiplier(state)

        for ((hex, star) in state.stars) {
            var next = star

            if (star.type == StarType.BLACK_HOLE) {
                val inflowRate = (analysis.blackHoleInflow[hex] ?: 0.0) * timeMult
                if (inflowRate > 0.0) {
                    val capacity = inflowRate * Balance.BLACK_HOLE_CAPACITY_SECONDS
                    next = next.copy(stored = min(capacity.coerceAtLeast(star.stored), star.stored + inflowRate * dt))
                }
            }

            val lifespan = Balance.effectiveLifespan(state, star.type)
            val ageStep = dt * agingMult
            // Ewige Sterne, Weiße Zwerge und Sterne neben einer Nebelwiege altern nur durch die Geburtsphase.
            if (lifespan == null || star.whiteDwarf || isSheltered(state, hex)) {
                if (next.age < Balance.PROTO_SECONDS) next = next.copy(age = min(Balance.PROTO_SECONDS, next.age + ageStep))
                stars[hex] = next
                continue
            }

            val newAge = next.age + ageStep
            if (newAge < lifespan) {
                stars[hex] = next.copy(age = newAge)
                continue
            }

            when (star.type.fate) {
                StarFate.SUPERNOVA -> {
                    stars.remove(hex)
                    val gain = Balance.supernovaElements(state, star)
                    elements += gain
                    supernovas++
                    val fertilizer = Balance.supernovaEnrichment(state)
                    for (n in hex.neighbors()) {
                        enrichment[n] = min(Balance.ENRICHMENT_CAP, (enrichment[n] ?: 0.0) + fertilizer)
                    }
                    events += GameEvent.Supernova(hex, gain)
                }
                StarFate.WHITE_DWARF -> {
                    stars[hex] = next.copy(age = newAge, whiteDwarf = true)
                    events += GameEvent.BecameWhiteDwarf(hex)
                }
                StarFate.ETERNAL -> stars[hex] = next.copy(age = newAge)
            }
        }

        var next = state.copy(
            stardust = state.stardust + income,
            elements = elements,
            stars = stars,
            enrichment = enrichment,
            runStardust = state.runStardust + income,
            totalStardust = state.totalStardust + income,
            supernovaCount = state.supernovaCount + supernovas,
            runSupernovas = state.runSupernovas + supernovas,
            boostRemaining = max(0.0, state.boostRemaining - dt),
            playTime = state.playTime + dt,
        )

        // Kometen und Ereignisse gibt es nur, wenn jemand zusieht.
        if (!offline) {
            next = updateCosmicEvent(next, dt, events)
            next = updateComet(next, dt, events)
        }

        next = unlockStarTypes(next, events)
        next = discover(next, events)
        next = progression.checkAchievements(next, events)
        return TickResult(next, events)
    }

    /** Neben einer Nebelwiege bleibt die Zeit stehen. */
    fun isSheltered(state: GameState, hex: Hex): Boolean =
        hex.neighbors().any { state.stars[it]?.type == StarType.NEBULA_NURSERY }

    private fun updateCosmicEvent(state: GameState, dt: Double, events: MutableList<GameEvent>): GameState {
        val active = state.event
        if (active != null) {
            val remaining = active.remaining - dt
            if (remaining > 0.0) return state.copy(event = active.copy(remaining = remaining))
            events += GameEvent.EventEnded(active.kind)
            return state.copy(event = null, eventCooldown = Balance.eventInterval(random.nextDouble()))
        }
        val cooldown = state.eventCooldown - dt
        if (cooldown > 0.0 || state.stars.size < 3) return state.copy(eventCooldown = max(cooldown, 0.0))
        val kind = CosmicEvent.entries[random.nextInt(CosmicEvent.entries.size)]
        events += GameEvent.EventStarted(kind)
        return state.copy(
            event = ActiveEvent(kind, kind.duration),
            stats = state.stats.copy(eventsSeen = state.stats.eventsSeen + 1),
        )
    }

    private fun updateComet(state: GameState, dt: Double, events: MutableList<GameEvent>): GameState {
        var comet = state.comet
        var cooldown = state.cometCooldown
        var nextId = state.nextCometId
        val shower = state.eventKind == CosmicEvent.METEOR_SHOWER
        if (comet != null) {
            comet = comet.copy(elapsed = comet.elapsed + dt).takeIf { it.elapsed < it.duration }
        } else {
            cooldown -= dt
            if (shower) cooldown = min(cooldown, Balance.METEOR_INTERVAL)
            if (cooldown <= 0.0) {
                comet = spawnComet(nextId++, meteor = shower)
                cooldown = if (shower) Balance.METEOR_INTERVAL else Balance.cometInterval(state, random.nextDouble())
            }
        }
        var next = state.copy(comet = comet, cometCooldown = cooldown, nextCometId = nextId)
        // Sternenwanderer fangen Kometen nach einer Sekunde automatisch.
        if (comet != null && comet.elapsed >= 1.0 && state.owns(StoreProduct.WANDERER_PASS)) {
            catchComet(next)?.let { (caught, event) ->
                next = caught
                events += event
            }
        }
        return next
    }

    private fun unlockStarTypes(state: GameState, events: MutableList<GameEvent>): GameState {
        var unlocked = state.unlocked
        for (type in StarType.entries) {
            if (type !in unlocked && max(state.stardust, state.runStardust) >= type.unlockAt) {
                unlocked = unlocked + type
                events += GameEvent.Unlocked(type)
            }
        }
        return if (unlocked === state.unlocked) state else state.copy(unlocked = unlocked)
    }

    /** Neu gebildete Sternbilder werden dauerhaft in den Katalog aufgenommen. */
    private fun discover(state: GameState, events: MutableList<GameEvent>): GameState {
        val active = BoardAnalyzer.findConstellations(state).mapTo(mutableSetOf()) { it.kind }
        val fresh = active - state.discovered
        if (fresh.isEmpty()) return state
        fresh.sortedBy { it.ordinal }.forEach { events += GameEvent.Discovered(it) }
        return state.copy(discovered = state.discovered + fresh)
    }

    private fun spawnComet(id: Long, meteor: Boolean): Comet {
        val fromLeft = random.nextBoolean()
        val startY = (if (meteor) 0.05f else 0.12f) + random.nextFloat() * 0.35f
        val endY = startY + 0.15f + random.nextFloat() * (if (meteor) 0.5f else 0.35f)
        return Comet(
            id = id,
            startX = if (fromLeft) -0.1f else 1.1f,
            startY = startY,
            endX = if (fromLeft) 1.1f else -0.1f,
            endY = endY,
            duration = if (meteor) 2.8 + random.nextDouble() * 1.2 else 7.0 + random.nextDouble() * 3.0,
            meteor = meteor,
        )
    }

    /** Simuliert die Zeit, in der das Spiel geschlossen war. */
    fun applyOffline(state: GameState, seconds: Double): Pair<GameState, OfflineReport> {
        val capped = min(seconds, Balance.maxOfflineSeconds(state)).coerceAtLeast(0.0)
        // Ein laufendes Ereignis endet, während niemand zusieht.
        val start = state.copy(event = null, comet = null)
        if (capped < 1.0 || state.lawChoices.isNotEmpty()) {
            return start to OfflineReport(seconds, capped, 0.0, 0.0, 0)
        }
        // Grobe Schritte reichen: Produktion ist zwischen zwei Lebensereignissen konstant.
        val steps = ceil(capped / 5.0).toInt().coerceIn(1, 720)
        val step = capped / steps
        var current = start
        var supernovas = 0
        repeat(steps) {
            val result = tick(current, step, offline = true)
            supernovas += result.events.count { it is GameEvent.Supernova }
            current = result.state
        }
        val report = OfflineReport(
            seconds = seconds,
            cappedSeconds = capped,
            stardust = current.stardust - state.stardust,
            elements = current.elements - state.elements,
            supernovas = supernovas,
        )
        return current to report
    }

    /** Sofortige Produktion für [seconds] Sekunden – ohne dass Sterne altern. */
    fun timeWarp(state: GameState, seconds: Double): Pair<GameState, Double> {
        val amount = BoardAnalyzer.analyze(state).totalRate * state.law.onlineMult * seconds
        return state.copy(
            stardust = state.stardust + amount,
            runStardust = state.runStardust + amount,
            totalStardust = state.totalStardust + amount,
        ) to amount
    }

    // ---------------------------------------------------------------- Aktionen

    fun canPlant(state: GameState, hex: Hex, type: StarType): Boolean =
        type in state.unlocked &&
            hex.length() <= Balance.gardenRadius(state) &&
            hex !in state.stars &&
            state.stardust >= Balance.starCost(state, type)

    fun plant(state: GameState, hex: Hex, type: StarType): GameState? {
        if (!canPlant(state, hex, type)) return null
        val cost = Balance.starCost(state, type)
        return state.copy(
            stardust = state.stardust - cost,
            stars = state.stars + (hex to Star(type)),
            stats = state.stats.copy(starsPlanted = state.stats.starsPlanted + 1),
        )
    }

    fun levelUp(state: GameState, hex: Hex): GameState? {
        val star = state.stars[hex] ?: return null
        if (star.type == StarType.BLACK_HOLE || star.type == StarType.NEBULA_NURSERY) return null
        val cost = Balance.levelUpCost(state, star)
        if (state.stardust < cost) return null
        val level = star.level + 1
        return state.copy(
            stardust = state.stardust - cost,
            stars = state.stars + (hex to star.copy(level = level)),
            stats = state.stats.copy(levelUps = state.stats.levelUps + 1, highestLevel = max(state.stats.highestLevel, level)),
        )
    }

    /** Entfernt einen Stern und erstattet die Hälfte des aktuellen Preises seiner Art. */
    fun remove(state: GameState, hex: Hex): GameState? {
        val star = state.stars[hex] ?: return null
        val without = state.copy(stars = state.stars - hex)
        val refund = Balance.starCost(without, star.type) * Balance.REFUND_SHARE
        return without.copy(stardust = without.stardust + refund + star.stored)
    }

    fun releaseBlackHole(state: GameState, hex: Hex): Pair<GameState, Double>? {
        val star = state.stars[hex] ?: return null
        if (star.type != StarType.BLACK_HOLE || star.stored <= 0.0) return null
        val amount = star.stored * Balance.blackHoleReleaseMultiplier(state)
        val next = state.copy(
            stardust = state.stardust + amount,
            runStardust = state.runStardust + amount,
            totalStardust = state.totalStardust + amount,
            stars = state.stars + (hex to star.copy(stored = 0.0)),
            stats = state.stats.copy(blackHoleReleases = state.stats.blackHoleReleases + 1),
        )
        return next to amount
    }

    fun canBuy(state: GameState, upgrade: Upgrade): Boolean {
        val max = upgrade.maxLevel
        if (max != null && state.level(upgrade) >= max) return false
        if (upgrade == Upgrade.NEBULA_EXPANSION &&
            Balance.gardenRadius(state) >= Balance.MAX_RADIUS
        ) return false
        return state.amount(upgrade.currency) >= Balance.upgradeCost(state, upgrade)
    }

    fun buyUpgrade(state: GameState, upgrade: Upgrade): GameState? {
        if (!canBuy(state, upgrade)) return null
        val cost = Balance.upgradeCost(state, upgrade)
        val paid = when (upgrade.currency) {
            Currency.STARDUST -> state.copy(stardust = state.stardust - cost)
            Currency.ELEMENTS -> state.copy(elements = state.elements - cost)
            Currency.DARK_MATTER -> state.copy(darkMatter = state.darkMatter - cost)
        }
        return paid.copy(upgrades = paid.upgrades + (upgrade to paid.level(upgrade) + 1))
    }

    /** Fängt den aktuellen Kometen. Belohnung: Sternenregen oder Kometenrausch; Meteore bringen immer Staub. */
    fun catchComet(state: GameState): Pair<GameState, GameEvent>? {
        val comet = state.comet ?: return null
        val rewardMult = Balance.cometRewardMultiplier(state)
        val caught = state.copy(
            comet = null,
            stats = state.stats.copy(cometsCaught = state.stats.cometsCaught + 1),
        )
        val rate = BoardAnalyzer.analyze(state).totalRate * state.law.onlineMult
        if (comet.meteor || random.nextBoolean()) {
            val seconds = if (comet.meteor) Balance.METEOR_REWARD_SECONDS else 120.0
            val amount = max(25.0, rate * seconds * rewardMult)
            return caught.copy(
                stardust = caught.stardust + amount,
                runStardust = caught.runStardust + amount,
                totalStardust = caught.totalStardust + amount,
            ) to GameEvent.CometStardust(amount, comet.meteor)
        }
        val seconds = Balance.COMET_BOOST_SECONDS * rewardMult
        return caught.copy(boostRemaining = caught.boostRemaining + seconds) to GameEvent.CometBoost(seconds)
    }

    // ---------------------------------------------------------------- Urknall

    fun canBigBang(state: GameState): Boolean = Balance.darkMatterGain(state) >= 1.0

    /**
     * Setzt die Galaxie zurück. Alles, was nicht zur einzelnen Galaxie gehört (Dunkle Materie, permanente
     * Upgrades, Sternarten, Sternbilder, Kristalle, Artefakte, Erfolge, Käufe …), bleibt erhalten.
     * Danach wählt der Spieler ein neues Naturgesetz.
     */
    fun bigBang(state: GameState): GameState? {
        if (!canBigBang(state)) return null
        val fresh = GameState()
        val choices = GalaxyLaw.entries.filter { it != GalaxyLaw.NORMAL && it != state.law }.shuffled(random).take(3)
        val reset = state.copy(
            darkMatter = state.darkMatter + Balance.darkMatterGain(state),
            elements = fresh.elements,
            stars = fresh.stars,
            enrichment = fresh.enrichment,
            upgrades = state.upgrades.filterKeys { it.permanent },
            runStardust = 0.0,
            runSupernovas = 0,
            comet = null,
            cometCooldown = fresh.cometCooldown,
            boostRemaining = 0.0,
            event = null,
            eventCooldown = fresh.eventCooldown,
            galaxyGoals = emptyList(),
            lawChoices = choices,
            stats = state.stats.copy(bigBangs = state.stats.bigBangs + 1),
        )
        return reset.copy(stardust = Balance.startingStardust(reset))
    }

    fun chooseGalaxy(state: GameState, law: GalaxyLaw): GameState? {
        if (law !in state.lawChoices) return null
        val chosen = state.copy(
            law = law,
            galaxyNumber = state.galaxyNumber + 1,
            galaxyName = galaxyName(),
            lawChoices = emptyList(),
        )
        return chosen.copy(galaxyGoals = progression.rollGalaxyGoals(chosen))
    }

    private fun galaxyName(): String {
        val prefixes = listOf("NGC", "Messier", "IC", "Kepler", "Lyra", "Andros", "Vela", "Cygni")
        return "${prefixes[random.nextInt(prefixes.size)]} ${random.nextInt(100, 9999)}"
    }
}
