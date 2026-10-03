package de.vcxrisi.sternengarten.game.engine

import de.vcxrisi.sternengarten.game.model.Comet
import de.vcxrisi.sternengarten.game.model.Currency
import de.vcxrisi.sternengarten.game.model.GalaxyLaw
import de.vcxrisi.sternengarten.game.model.GameState
import de.vcxrisi.sternengarten.game.model.Hex
import de.vcxrisi.sternengarten.game.model.Star
import de.vcxrisi.sternengarten.game.model.StarFate
import de.vcxrisi.sternengarten.game.model.StarType
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
        var stardust = state.stardust + income
        var elements = state.elements
        var supernovas = state.supernovaCount
        val enrichment = state.enrichment.toMutableMap()
        val stars = state.stars.toMutableMap()

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
            val ageStep = dt * law.agingMult
            if (lifespan == null || star.whiteDwarf) {
                // Ewige Sterne und Weiße Zwerge altern nur durch die Geburtsphase.
                if (next.age < Balance.PROTO_SECONDS) next = next.copy(age = next.age + ageStep)
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

        // Kometen erscheinen nur, wenn jemand zusieht.
        var comet = state.comet
        var cometCooldown = state.cometCooldown
        var nextCometId = state.nextCometId
        if (!offline) {
            if (comet != null) {
                comet = comet.copy(elapsed = comet.elapsed + dt).takeIf { it.elapsed < it.duration }
            } else {
                cometCooldown -= dt
                if (cometCooldown <= 0.0) {
                    comet = spawnComet(nextCometId++)
                    cometCooldown = Balance.cometInterval(state, random.nextDouble())
                }
            }
        }

        var unlocked = state.unlocked
        val runStardust = state.runStardust + income
        for (type in StarType.entries) {
            if (type !in unlocked && max(stardust, runStardust) >= type.unlockAt) {
                unlocked = unlocked + type
                events += GameEvent.Unlocked(type)
            }
        }

        val next = state.copy(
            stardust = stardust,
            elements = elements,
            stars = stars,
            enrichment = enrichment,
            runStardust = runStardust,
            totalStardust = state.totalStardust + income,
            unlocked = unlocked,
            supernovaCount = supernovas,
            comet = comet,
            cometCooldown = cometCooldown,
            nextCometId = nextCometId,
            boostRemaining = max(0.0, state.boostRemaining - dt),
            playTime = state.playTime + dt,
        )
        return TickResult(discover(next, events), events)
    }

    /** Neu gebildete Sternbilder werden dauerhaft in den Katalog aufgenommen. */
    private fun discover(state: GameState, events: MutableList<GameEvent>): GameState {
        val active = BoardAnalyzer.findConstellations(state).mapTo(mutableSetOf()) { it.kind }
        val fresh = active - state.discovered
        if (fresh.isEmpty()) return state
        fresh.sortedBy { it.ordinal }.forEach { events += GameEvent.Discovered(it) }
        return state.copy(discovered = state.discovered + fresh)
    }

    private fun spawnComet(id: Long): Comet {
        val fromLeft = random.nextBoolean()
        val startY = 0.12f + random.nextFloat() * 0.35f
        val endY = startY + 0.15f + random.nextFloat() * 0.35f
        return Comet(
            id = id,
            startX = if (fromLeft) -0.1f else 1.1f,
            startY = startY,
            endX = if (fromLeft) 1.1f else -0.1f,
            endY = endY,
            duration = 7.0 + random.nextDouble() * 3.0,
        )
    }

    /** Simuliert die Zeit, in der das Spiel geschlossen war. */
    fun applyOffline(state: GameState, seconds: Double): Pair<GameState, OfflineReport> {
        val capped = min(seconds, Balance.maxOfflineSeconds(state)).coerceAtLeast(0.0)
        if (capped < 1.0 || state.lawChoices.isNotEmpty()) {
            return state to OfflineReport(seconds, capped, 0.0, 0.0, 0)
        }
        // Grobe Schritte reichen: Produktion ist zwischen zwei Lebensereignissen konstant.
        val steps = ceil(capped / 5.0).toInt().coerceIn(1, 720)
        val step = capped / steps
        var current = state
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
        )
    }

    fun levelUp(state: GameState, hex: Hex): GameState? {
        val star = state.stars[hex] ?: return null
        if (star.type == StarType.BLACK_HOLE) return null
        val cost = Balance.levelUpCost(state, star)
        if (state.stardust < cost) return null
        return state.copy(
            stardust = state.stardust - cost,
            stars = state.stars + (hex to star.copy(level = star.level + 1)),
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
        val amount = star.stored * Balance.BLACK_HOLE_RELEASE_MULT
        val next = state.copy(
            stardust = state.stardust + amount,
            runStardust = state.runStardust + amount,
            totalStardust = state.totalStardust + amount,
            stars = state.stars + (hex to star.copy(stored = 0.0)),
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

    /** Fängt den aktuellen Kometen. Belohnung: Sternenregen oder Kometenrausch. */
    fun catchComet(state: GameState): Pair<GameState, GameEvent>? {
        if (state.comet == null) return null
        val lure = state.level(Upgrade.COMET_LURE)
        val caught = state.copy(comet = null)
        return if (random.nextBoolean()) {
            val rate = BoardAnalyzer.analyze(state).totalRate * state.law.onlineMult
            val amount = max(25.0, rate * 120.0 * (1.0 + 0.5 * lure))
            caught.copy(
                stardust = caught.stardust + amount,
                runStardust = caught.runStardust + amount,
                totalStardust = caught.totalStardust + amount,
            ) to GameEvent.CometStardust(amount)
        } else {
            val seconds = Balance.COMET_BOOST_SECONDS + 15.0 * lure
            caught.copy(boostRemaining = caught.boostRemaining + seconds) to GameEvent.CometBoost(seconds)
        }
    }

    // ---------------------------------------------------------------- Urknall

    fun canBigBang(state: GameState): Boolean = Balance.darkMatterGain(state) >= 1.0

    /**
     * Setzt die Galaxie zurück. Erhalten bleiben Dunkle Materie, permanente Upgrades,
     * freigeschaltete Sternarten und der Sternbild-Katalog. Danach wählt der Spieler ein neues Naturgesetz.
     */
    fun bigBang(state: GameState): GameState? {
        if (!canBigBang(state)) return null
        val permanent = state.upgrades.filterKeys { it.permanent }
        val choices = GalaxyLaw.entries.filter { it != GalaxyLaw.NORMAL && it != state.law }.shuffled(random).take(3)
        val reset = GameState(
            darkMatter = state.darkMatter + Balance.darkMatterGain(state),
            upgrades = permanent,
            galaxyNumber = state.galaxyNumber,
            galaxyName = state.galaxyName,
            law = state.law,
            totalStardust = state.totalStardust,
            unlocked = state.unlocked,
            discovered = state.discovered,
            supernovaCount = state.supernovaCount,
            nextCometId = state.nextCometId,
            playTime = state.playTime,
            lastSavedEpochMs = state.lastSavedEpochMs,
            lawChoices = choices,
        )
        return reset.copy(stardust = Balance.startingStardust(reset))
    }

    fun chooseGalaxy(state: GameState, law: GalaxyLaw): GameState? {
        if (law !in state.lawChoices) return null
        return state.copy(
            law = law,
            galaxyNumber = state.galaxyNumber + 1,
            galaxyName = galaxyName(),
            lawChoices = emptyList(),
        )
    }

    private fun galaxyName(): String {
        val prefixes = listOf("NGC", "Messier", "IC", "Kepler", "Lyra", "Andros", "Vela", "Cygni")
        return "${prefixes[random.nextInt(prefixes.size)]} ${random.nextInt(100, 9999)}"
    }
}
