package de.vcxrisi.sternengarten.game.engine

import de.vcxrisi.sternengarten.game.model.ActiveEvent
import de.vcxrisi.sternengarten.game.model.Comet
import de.vcxrisi.sternengarten.game.model.ConstellationKind
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
import de.vcxrisi.sternengarten.game.model.withRun
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
    /** Geparkte Galaxien, Erschließung und Sternenbrücken. */
    val galaxies by lazy { GalaxyOrchestrator(this) }

    // ---------------------------------------------------------------- Zeit

    /**
     * Lässt die aktive Galaxie [dt] Sekunden laufen. [offline]: während das Spiel geschlossen war.
     * [background]: eine geparkte Galaxie bei offener App – ohne Kometenrausch, Spielzeit, Kometen, Ereignisse
     * und Erfolgsprüfung; die laufen einmal pro Schritt auf dem ganzen Zustand.
     */
    fun tick(state: GameState, dt: Double, offline: Boolean = false, background: Boolean = false): TickResult {
        if (dt <= 0.0 || state.lawChoices.isNotEmpty()) return TickResult(state, emptyList())
        val events = ArrayList<GameEvent>()
        val analysis = BoardAnalyzer.analyze(state)
        val law = state.law

        val lawMult = if (offline) law.offlineMult * state.activeGalaxy.offlineMult else law.onlineMult
        // Der Kometenrausch wirkt nur in der Galaxie, die man gerade ansieht.
        val boosted = state.boostRemaining > 0.0 && !offline && !background
        val timeMult = lawMult * (if (boosted) Balance.COMET_BOOST_MULT else 1.0)

        val income = (analysis.totalRate * timeMult * dt).capped()
        // Eigenes Einkommen ohne Kometenrausch: Grundlage für Sternenbrücken.
        val ownIncome = (analysis.totalRate * lawMult * dt).capped()
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
                    next = next.copy(stored = min(capacity.coerceAtLeast(star.stored), star.stored + inflowRate * dt).capped())
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
                    // Glutsterne bringen mehr Elemente, je größer ihr Nest ist.
                    val nest = min((analysis.emberGroups[hex] ?: 1) - 1, Balance.EMBER_GROUP_CAP)
                    val gain = Balance.supernovaElements(state, star) * (1.0 + Balance.EMBER_SUPERNOVA_PER_MEMBER * nest)
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
            totalStardust = state.totalStardust + Balance.normalizedDust(state, income),
            supernovaCount = state.supernovaCount + supernovas,
            runSupernovas = state.runSupernovas + supernovas,
            boostRemaining = if (background) state.boostRemaining else max(0.0, state.boostRemaining - dt),
            playTime = if (background) state.playTime else state.playTime + dt,
        )

        // Kometen und Ereignisse gibt es nur, wenn jemand zusieht – und nie in der Großen Stille.
        if (!offline && !background && !law.quiet) {
            next = updateCosmicEvent(next, dt, events)
            next = updateComet(next, dt, events)
        }

        next = unlockStarTypes(next, events)
        next = discover(next, analysis.activeKinds, events)
        if (!background) next = progression.checkAchievements(next, events)
        return TickResult(next.sanitized(), events, ownIncome)
    }

    /**
     * Beendet ein laufendes Ereignis vorzeitig (Offline-Zeit, Parken der Galaxie). Das nächste kommt dann nicht
     * sofort – sonst ließe sich durch Hin- und Herwechseln ein Sternenregen erzwingen.
     */
    internal fun withoutEvent(state: GameState): GameState =
        if (state.event == null) state else state.copy(event = null, eventCooldown = cooldownAfterEvent(state.eventCooldown))

    /** Wartezeit nach einem vorzeitig beendeten Ereignis: mindestens ein ganzer Abstand bis zum nächsten. */
    internal fun cooldownAfterEvent(cooldown: Double): Double = max(cooldown, Balance.eventInterval(random.nextDouble()))

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

    /** Exklusive Sternarten werden nur in ihrer eigenen Galaxie freigeschaltet. */
    private fun unlockStarTypes(state: GameState, events: MutableList<GameEvent>): GameState {
        var unlocked = state.unlocked
        for (type in StarType.entries) {
            if (type in unlocked) continue
            if (type.exclusiveTo != null && type.exclusiveTo != state.activeGalaxy) continue
            if (max(state.stardust, state.runStardust) >= Balance.unlockThreshold(state, type)) {
                unlocked = unlocked + type
                events += GameEvent.Unlocked(type)
            }
        }
        return if (unlocked === state.unlocked) state else state.copy(unlocked = unlocked)
    }

    /** Neu gebildete Sternbilder werden dauerhaft in den Katalog aufgenommen; [active] kommt aus der Analyse des Ticks. */
    private fun discover(state: GameState, active: Set<ConstellationKind>, events: MutableList<GameEvent>): GameState {
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

    /**
     * Simuliert die Zeit, in der das Spiel geschlossen war, in höchstens [maxSteps] Schritten.
     * [background]: eine geparkte Galaxie (siehe [tick]); alle Galaxien zusammen holt [GalaxyOrchestrator.applyOfflineAll] nach.
     */
    fun applyOffline(
        state: GameState,
        seconds: Double,
        background: Boolean = false,
        maxSteps: Int = Balance.OFFLINE_STEP_BUDGET,
    ): Pair<GameState, OfflineReport> {
        val capped = min(seconds, Balance.maxOfflineSeconds(state)).coerceAtLeast(0.0)
        // Ein laufendes Ereignis endet, während niemand zusieht.
        val start = withoutEvent(state).copy(comet = null)
        if (capped < 1.0 || state.lawChoices.isNotEmpty()) {
            return start to OfflineReport(seconds, capped, 0.0, 0.0, 0)
        }
        // Grobe Schritte reichen: Produktion ist zwischen zwei Lebensereignissen konstant.
        val steps = ceil(capped / Balance.OFFLINE_STEP_SECONDS).toInt().coerceIn(1, maxSteps.coerceAtLeast(1))
        val step = capped / steps
        var current = start
        var supernovas = 0
        repeat(steps) {
            val result = tick(current, step, offline = true, background = background)
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
        val amount = (BoardAnalyzer.analyze(state).totalRate * state.law.onlineMult * seconds).capped()
        return state.copy(
            stardust = state.stardust + amount,
            runStardust = state.runStardust + amount,
            totalStardust = state.totalStardust + Balance.normalizedDust(state, amount),
        ) to amount
    }

    // ---------------------------------------------------------------- Aktionen

    fun canPlant(state: GameState, hex: Hex, type: StarType): Boolean =
        type in state.unlocked &&
            (type.exclusiveTo == null || type.exclusiveTo == state.activeGalaxy) &&
            hex in state.ownedFields &&
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

    /** Verbessert einen Stern um [count] Stufen auf einmal – nur, wenn alle bezahlbar sind. */
    fun levelUp(state: GameState, hex: Hex, count: Int = 1): GameState? {
        if (count < 1) return null
        val star = state.stars[hex] ?: return null
        if (!canLevel(star.type)) return null
        val cost = Balance.levelUpCost(state, star, count)
        if (state.stardust < cost) return null
        val level = star.level + count
        return state.copy(
            stardust = state.stardust - cost,
            stars = state.stars + (hex to star.copy(level = level)),
            stats = state.stats.copy(levelUps = state.stats.levelUps + count, highestLevel = max(state.stats.highestLevel, level)),
        )
    }

    /** Verbessert einen Stern um so viele Stufen wie bezahlbar; liefert auch die Anzahl. */
    fun levelUpMax(state: GameState, hex: Hex): Pair<GameState, Int>? {
        val star = state.stars[hex] ?: return null
        if (!canLevel(star.type)) return null
        val count = Balance.maxAffordableLevels(state, star)
        if (count < 1) return null
        val next = levelUp(state, hex, count) ?: return null
        return next to count
    }

    fun canLevel(type: StarType): Boolean = type != StarType.BLACK_HOLE && type != StarType.NEBULA_NURSERY

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
        val amount = (star.stored * Balance.blackHoleReleaseMultiplier(state)).capped()
        val next = state.copy(
            stardust = state.stardust + amount,
            runStardust = state.runStardust + amount,
            totalStardust = state.totalStardust + Balance.normalizedDust(state, amount),
            stars = state.stars + (hex to star.copy(stored = 0.0)),
            stats = state.stats.copy(blackHoleReleases = state.stats.blackHoleReleases + 1),
        )
        return next to amount
    }

    fun canBuy(state: GameState, upgrade: Upgrade): Boolean {
        if (upgrade.retired) return false
        val max = upgrade.maxLevel
        if (max != null && state.level(upgrade) >= max) return false
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
        val next = paid.copy(upgrades = paid.upgrades + (upgrade to paid.level(upgrade) + 1))
        // Der Urnebel vergrößert die Startfläche sofort – in jeder Galaxie.
        return if (upgrade == Upgrade.PRIMORDIAL_NEBULA) GalaxyFactory.extendStartingFields(next) else next
    }

    /** Ein Feld lässt sich kaufen, wenn es an den Garten grenzt, nah genug am Zentrum liegt und bezahlbar ist. */
    fun canBuyField(state: GameState, hex: Hex): Boolean =
        state.lawChoices.isEmpty() &&
            hex !in state.ownedFields &&
            hex.length() <= Balance.maxFieldRadius(state.law) &&
            hex.neighbors().any { it in state.ownedFields } &&
            state.stardust >= Balance.fieldCost(state)

    /** Kauft ein Feld frei; liefert auch den gezahlten Preis. */
    fun buyField(state: GameState, hex: Hex): Pair<GameState, Double>? {
        if (!canBuyField(state, hex)) return null
        val cost = Balance.fieldCost(state)
        return state.copy(
            stardust = state.stardust - cost,
            ownedFields = state.ownedFields + hex,
            fieldsBought = state.fieldsBought + 1,
            stats = state.stats.copy(fieldsBought = state.stats.fieldsBought + 1),
        ) to cost
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
            val amount = max(Balance.cometMinReward(state), rate * seconds * rewardMult).capped()
            return caught.copy(
                stardust = caught.stardust + amount,
                runStardust = caught.runStardust + amount,
                totalStardust = caught.totalStardust + Balance.normalizedDust(state, amount),
            ) to GameEvent.CometStardust(amount, comet.meteor)
        }
        val seconds = Balance.COMET_BOOST_SECONDS * rewardMult
        return caught.copy(boostRemaining = caught.boostRemaining + seconds) to GameEvent.CometBoost(seconds)
    }

    // ---------------------------------------------------------------- Urknall

    /** Der Faktor der Galaxieart zählt hier nicht – sonst ließen sich teure Galaxien für Kleinstbeträge kollabieren. */
    fun canBigBang(state: GameState): Boolean = Balance.darkMatterBase(state) >= 1.0 && state.lawChoices.isEmpty()

    /**
     * Setzt die aktive Galaxie zurück (siehe [GalaxyFactory.freshRun]). Alles, was nicht zur einzelnen Galaxie gehört
     * (Dunkle Materie, permanente Upgrades, Sternarten, Sternbilder, Kristalle, Artefakte, Erfolge, Käufe,
     * die anderen Galaxien …), bleibt erhalten. Danach wählt der Spieler ein neues Naturgesetz.
     */
    fun bigBang(state: GameState): GameState? {
        if (!canBigBang(state)) return null
        var fresh = GalaxyFactory.freshRun(
            state, state.activeGalaxy, state.law, state.galaxyNumber, state.galaxyName, rollLawChoices(state.law),
        )
        // Ein laufendes Ereignis endet; das nächste kommt nicht sofort, sonst ließe es sich neu auswürfeln.
        if (state.event != null) fresh = fresh.copy(eventCooldown = cooldownAfterEvent(fresh.eventCooldown))
        return state.withRun(state.activeGalaxy, fresh).copy(
            darkMatter = state.darkMatter + Balance.darkMatterGain(state),
            stats = state.stats.copy(bigBangs = state.stats.bigBangs + 1),
        )
    }

    fun chooseGalaxy(state: GameState, law: GalaxyLaw): GameState? {
        if (law !in state.lawChoices) return null
        val chosen = state.copy(
            law = law,
            galaxyNumber = state.galaxyNumber + 1,
            // Eine frisch erschlossene Galaxie (Zyklus 0) behält ihren Namen.
            galaxyName = if (state.galaxyNumber == 0) state.galaxyName else galaxyName(),
            lawChoices = emptyList(),
            // Das Naturgesetz bestimmt die Startfläche mit (Hohe Gravitation).
            ownedFields = Balance.startFields(state, state.activeGalaxy, law),
            fieldsBought = 0,
        )
        return chosen.copy(galaxyGoals = progression.rollGalaxyGoals(chosen))
    }

    /** Drei Naturgesetze zur Wahl, nie die vertrauten und nie [exclude]. */
    internal fun rollLawChoices(exclude: GalaxyLaw?): List<GalaxyLaw> =
        GalaxyLaw.entries.filter { it != GalaxyLaw.NORMAL && it != exclude }.shuffled(random).take(3)

    internal fun galaxyName(): String {
        val prefixes = listOf("NGC", "Messier", "IC", "Kepler", "Lyra", "Andros", "Vela", "Cygni")
        return "${prefixes[random.nextInt(prefixes.size)]} ${random.nextInt(100, 9999)}"
    }
}
