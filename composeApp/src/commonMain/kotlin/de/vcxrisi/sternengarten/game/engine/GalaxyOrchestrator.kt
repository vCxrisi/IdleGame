package de.vcxrisi.sternengarten.game.engine

import de.vcxrisi.sternengarten.game.model.GalaxyKind
import de.vcxrisi.sternengarten.game.model.GalaxyLaw
import de.vcxrisi.sternengarten.game.model.GalaxyRun
import de.vcxrisi.sternengarten.game.model.GalaxyUnlock
import de.vcxrisi.sternengarten.game.model.GameState
import de.vcxrisi.sternengarten.game.model.StarBridge
import de.vcxrisi.sternengarten.game.model.activeRun
import de.vcxrisi.sternengarten.game.model.hasCollapsed
import de.vcxrisi.sternengarten.game.model.hasRun
import de.vcxrisi.sternengarten.game.model.projected
import de.vcxrisi.sternengarten.game.model.runKinds
import de.vcxrisi.sternengarten.game.model.runOf
import de.vcxrisi.sternengarten.game.model.switchedTo
import de.vcxrisi.sternengarten.game.model.withRun
import kotlin.math.min

/** Ergebnis eines Hintergrund-Schritts; alle Werte je Sekunde. */
data class BackgroundResult(
    val state: GameState,
    val events: List<GameEvent>,
    /** Eigene Produktion je Galaxie: mit Naturgesetz, ohne Kometenrausch und ohne Brückenstaub. */
    val rates: Map<GalaxyKind, Double>,
    /** Brückenstaub je Zielgalaxie. */
    val inflow: Map<GalaxyKind, Double>,
    /** Abholbare Galaxie-Ziele je geparkter Galaxie. Die aktive zählt die Oberfläche mit ihrer eigenen Analyse. */
    val claimable: Map<GalaxyKind, Int>,
)

/** Ob sich die nächste Galaxie erschließen lässt – und wenn nicht, warum. */
enum class UnlockStatus {
    READY,
    /** Es entsteht schon eine Galaxie. */
    RUNNING,
    /** Die vorige Galaxie ist frisch erschlossen und wartet auf ihre Naturgesetze. */
    NEEDS_LAW_CHOICE,
    /** Die vorige Galaxie hatte noch keinen Urknall. */
    NEEDS_COLLAPSE,
    NEEDS_DARK_MATTER,
    ALL_UNLOCKED,
}

/** Warum sich eine Sternenbrücke nicht bauen lässt; [NONE], wenn doch. */
enum class BridgeBlock { NONE, SAME_GALAXY, MISSING_GALAXY, TARGET_TAKEN, UNDER_CONSTRUCTION, DARK_MATTER }

/**
 * Alles, was mehrere Galaxien betrifft: geparkte Galaxien weiterlaufen lassen, Wechsel, Erschließung und
 * Sternenbrücken. Die Engine selbst kennt nur die aktive Galaxie; geparkte werden dafür in den flachen Zustand
 * projiziert, getickt und wieder zurückgelegt – immer in der Reihenfolge der Galaxiearten.
 * Reine Funktionen: Die Wanduhr ([nowMs]) kommt von außen.
 */
class GalaxyOrchestrator internal constructor(private val engine: GameEngine) {

    // ------------------------------------------------------------ Hintergrund

    /**
     * Lässt alle geparkten Galaxien [dt] Sekunden laufen (ohne Kometenrausch, Spielzeit, Kometen und Ereignisse),
     * lässt die Sternenbrücken fließen und prüft die Erfolge einmal auf dem ganzen Zustand.
     * [activeIncome]: eigenes Einkommen der aktiven Galaxie in derselben Zeit (aus [TickResult.ownIncome]).
     */
    fun tickBackground(state: GameState, dt: Double, activeIncome: Double): BackgroundResult {
        val activeKind = state.activeGalaxy
        val incomes = LinkedHashMap<GalaxyKind, Double>()
        incomes[activeKind] = activeIncome
        val events = ArrayList<GameEvent>()
        var next = state
        if (state.parked.isNotEmpty() && dt > 0.0) {
            // Projiziert wird aus einem Zustand ohne `parked` – so enthält `parked` nie die gerade getickte Galaxie.
            val savedActive = state.activeRun()
            var meta = state.copy(parked = emptyMap())
            val parked = LinkedHashMap<GalaxyKind, GalaxyRun>()
            for ((kind, run) in state.parked.entries.sortedBy { it.key.ordinal }) {
                if (run.lawChoices.isNotEmpty()) {
                    parked[kind] = run
                    incomes[kind] = 0.0
                    continue
                }
                val result = engine.tick(meta.withRun(kind, run), dt, background = true)
                parked[kind] = result.state.activeRun()
                meta = result.state
                incomes[kind] = result.ownIncome
                result.events.mapTo(events) {
                    if (it is GameEvent.Supernova || it is GameEvent.BecameWhiteDwarf) GameEvent.InBackground(kind, it) else it
                }
            }
            next = meta.withRun(activeKind, savedActive).copy(parked = parked)
        }
        val (bridged, inflow) = applyBridges(next, incomes)
        next = engine.progression.checkAchievements(bridged, events).sanitized()
        val perSecond = if (dt > 0.0) 1.0 / dt else 0.0
        return BackgroundResult(
            state = next,
            events = events,
            rates = incomes.mapValues { it.value * perSecond },
            inflow = inflow.mapValues { it.value * perSecond },
            claimable = next.parked.keys.associateWith { claimableGoals(next, it) },
        )
    }

    /**
     * Holt die Offline-Zeit aller Galaxien nach. Alle teilen sich [Balance.OFFLINE_STEP_BUDGET] Schritte:
     * Jede geparkte Galaxie bekommt [Balance.PARKED_OFFLINE_STEPS], die aktive den Rest.
     * Brücken fließen mit dem Offline-Ertrag ihrer Quelle; eine erst unterwegs fertige Brücke trägt noch nichts bei.
     */
    fun applyOfflineAll(state: GameState, seconds: Double): Pair<GameState, OfflineReport> {
        if (state.parked.isEmpty()) return engine.applyOffline(state, seconds)
        val activeKind = state.activeGalaxy
        val (activeAfter, activeReport) = engine.applyOffline(
            state.copy(parked = emptyMap()), seconds, maxSteps = Balance.activeOfflineSteps(state.parked.size),
        )
        val reports = LinkedHashMap<GalaxyKind, OfflineReport>()
        reports[activeKind] = activeReport
        val savedActive = activeAfter.activeRun()
        var meta = activeAfter
        val parked = LinkedHashMap<GalaxyKind, GalaxyRun>()
        for ((kind, run) in state.parked.entries.sortedBy { it.key.ordinal }) {
            if (run.lawChoices.isNotEmpty()) {
                parked[kind] = run
                continue
            }
            val (after, report) = engine.applyOffline(
                meta.withRun(kind, run), seconds, background = true, maxSteps = Balance.PARKED_OFFLINE_STEPS,
            )
            parked[kind] = after.activeRun()
            meta = after
            reports[kind] = report
        }
        val restored = meta.withRun(activeKind, savedActive).copy(parked = parked)
        val (bridged, inflow) = applyBridges(restored, reports.mapValues { it.value.stardust })
        val next = engine.progression.checkAchievements(bridged, ArrayList()).sanitized()
        val lines = next.runKinds().map { kind ->
            val report = reports[kind]
            GalaxyOfflineLine(
                kind = kind,
                name = next.runOf(kind)?.galaxyName.orEmpty(),
                stardust = report?.stardust ?: 0.0,
                elements = report?.elements ?: 0.0,
                supernovas = report?.supernovas ?: 0,
                bridged = inflow[kind] ?: 0.0,
                paused = isFrozen(next, kind),
            )
        }
        return next to activeReport.copy(galaxies = lines)
    }

    /**
     * Wechselt zur geparkten Galaxie [kind]; `null`, wenn es sie nicht gibt oder sie schon aktiv ist.
     * Ein laufendes Ereignis endet dabei, ohne dass gleich das nächste ausgewürfelt wird.
     */
    fun switchTo(state: GameState, kind: GalaxyKind): GameState? {
        if (kind == state.activeGalaxy || kind !in state.parked) return null
        return engine.withoutEvent(state).switchedTo(kind)
    }

    /** Dunkle Materie, die ein Urknall der Galaxie [kind] jetzt brächte; 0, wenn es sie (noch) nicht gibt. */
    fun previewGain(state: GameState, kind: GalaxyKind): Double =
        state.projected(kind)?.let { Balance.darkMatterGain(it) } ?: 0.0

    /** Eigene Produktion je Galaxie pro Sekunde, ohne zu ticken – etwa direkt nach einem Wechsel. */
    fun ownRates(state: GameState): Map<GalaxyKind, Double> = state.runKinds().associateWith { kind ->
        val view = state.projected(kind)
        if (view == null || view.lawChoices.isNotEmpty()) 0.0 else BoardAnalyzer.analyze(view).totalRate * view.law.onlineMult
    }

    /** Erfüllte, aber noch nicht abgeholte Ziele der Galaxie [kind]. */
    fun claimableGoals(state: GameState, kind: GalaxyKind): Int {
        val view = state.projected(kind) ?: return 0
        val open = view.galaxyGoals.filter { !it.claimed }
        if (open.isEmpty()) return 0
        val analysis = BoardAnalyzer.analyze(view)
        return open.count { engine.progression.goalProgress(view, analysis, it) >= it.target }
    }

    // ------------------------------------------------------------ Erschließen

    /** Die nächste Galaxieart ohne Galaxie – erschlossen wird der Reihe nach. */
    fun nextUnlockable(state: GameState): GalaxyKind? = GalaxyKind.entries.firstOrNull { !state.hasRun(it) }

    fun unlockStatus(state: GameState): UnlockStatus {
        if (state.unlock != null) return UnlockStatus.RUNNING
        val kind = nextUnlockable(state) ?: return UnlockStatus.ALL_UNLOCKED
        val previous = GalaxyKind.entries.getOrNull(kind.ordinal - 1)
        if (previous != null && !state.hasCollapsed(previous)) {
            val choosing = state.runOf(previous)?.lawChoices?.isNotEmpty() == true
            return if (choosing) UnlockStatus.NEEDS_LAW_CHOICE else UnlockStatus.NEEDS_COLLAPSE
        }
        if (state.darkMatter < kind.unlockDarkMatter) return UnlockStatus.NEEDS_DARK_MATTER
        return UnlockStatus.READY
    }

    /** Zahlt die Dunkle Materie und startet die Erschließung der nächsten Galaxie. */
    fun startUnlock(state: GameState, nowMs: Long): Pair<GameState, GameEvent.GalaxyUnlockStarted>? {
        if (unlockStatus(state) != UnlockStatus.READY) return null
        val kind = nextUnlockable(state) ?: return null
        val readyAt = nowMs + Balance.unlockMillis(state, kind)
        return state.copy(
            darkMatter = state.darkMatter - kind.unlockDarkMatter,
            unlock = GalaxyUnlock(kind, nowMs, readyAt),
        ) to GameEvent.GalaxyUnlockStarted(kind, readyAt)
    }

    /** Stellt die laufende Erschließung für Kristalle sofort fertig; liefert auch den Preis. Danach [completeTimers]. */
    fun skipUnlock(state: GameState, nowMs: Long): Pair<GameState, Int>? {
        val unlock = state.unlock ?: return null
        val cost = skipCost(unlock.startedAtMs, unlock.readyAtMs, nowMs) ?: return null
        if (state.crystals < cost) return null
        return state.copy(crystals = state.crystals - cost, unlock = unlock.copy(readyAtMs = nowMs)) to cost
    }

    // ------------------------------------------------------------ Sternenbrücken

    fun bridgeBlock(state: GameState, from: GalaxyKind, to: GalaxyKind): BridgeBlock = when {
        from == to -> BridgeBlock.SAME_GALAXY
        !state.hasRun(from) || !state.hasRun(to) -> BridgeBlock.MISSING_GALAXY
        state.bridges.any { it.to == to } -> BridgeBlock.TARGET_TAKEN
        state.bridges.any { !it.built } -> BridgeBlock.UNDER_CONSTRUCTION
        state.darkMatter < Balance.bridgeCost(state) -> BridgeBlock.DARK_MATTER
        else -> BridgeBlock.NONE
    }

    /** Zahlt die Dunkle Materie und beginnt den Bau einer Brücke von [from] nach [to]. */
    fun buildBridge(state: GameState, from: GalaxyKind, to: GalaxyKind, nowMs: Long): Pair<GameState, GameEvent.BridgeStarted>? {
        if (bridgeBlock(state, from, to) != BridgeBlock.NONE) return null
        val bridge = StarBridge(from, to, nowMs, nowMs + Balance.bridgeMillis(state))
        return state.copy(
            darkMatter = state.darkMatter - Balance.bridgeCost(state),
            bridges = state.bridges + bridge,
        ) to GameEvent.BridgeStarted(bridge)
    }

    /** Stellt eine Brücke im Bau für Kristalle sofort fertig; liefert auch den Preis. Danach [completeTimers]. */
    fun skipBridge(state: GameState, from: GalaxyKind, to: GalaxyKind, nowMs: Long): Pair<GameState, Int>? {
        val bridge = state.bridges.firstOrNull { it.from == from && it.to == to && !it.built } ?: return null
        val cost = skipCost(bridge.startedAtMs, bridge.readyAtMs, nowMs) ?: return null
        if (state.crystals < cost) return null
        return state.copy(
            crystals = state.crystals - cost,
            bridges = state.bridges.map { if (it === bridge) it.copy(readyAtMs = nowMs) else it },
        ) to cost
    }

    /** Reißt eine Brücke sofort und kostenlos ab, auch im Bau – ohne Erstattung. */
    fun removeBridge(state: GameState, from: GalaxyKind, to: GalaxyKind): GameState {
        val bridges = state.bridges.filterNot { it.from == from && it.to == to }
        return if (bridges.size == state.bridges.size) state else state.copy(bridges = bridges)
    }

    /**
     * Lässt Staub über die fertigen Brücken fließen. [incomes]: eigenes Einkommen je Galaxie im selben Zeitraum –
     * Brückenstaub zählt dort nie mit, deshalb schaukeln sich Kreisläufe nicht auf. Gutgeschrieben wird nur der
     * ausgebbare Staub des Ziels (und [GameState.totalStardust] in Sternenstaub-Wert), nie `runStardust`:
     * Brückenstaub hilft beim Aufbau, zählt aber nicht für den Urknall. Galaxien, die auf ihre Naturgesetze
     * warten, bekommen nichts. Liefert auch den Zufluss je Zielgalaxie.
     */
    fun applyBridges(state: GameState, incomes: Map<GalaxyKind, Double>): Pair<GameState, Map<GalaxyKind, Double>> {
        if (state.bridges.none { it.built }) return state to emptyMap()
        val inflow = LinkedHashMap<GalaxyKind, Double>()
        for (bridge in state.bridges) {
            if (!bridge.built || !state.hasRun(bridge.to) || isFrozen(state, bridge.to)) continue
            val amount = Balance.bridgeFlow(state, bridge, incomes)
            if (amount > 0.0) inflow[bridge.to] = ((inflow[bridge.to] ?: 0.0) + amount).capped()
        }
        if (inflow.isEmpty()) return state to inflow
        var next = state
        var total = state.totalStardust
        for ((kind, amount) in inflow) {
            total += amount / kind.costScale
            next = if (kind == next.activeGalaxy) {
                next.copy(stardust = (next.stardust + amount).capped())
            } else {
                val run = next.parked.getValue(kind)
                next.copy(parked = next.parked + (kind to run.copy(stardust = (run.stardust + amount).capped())))
            }
        }
        return next.copy(totalStardust = total.capped()) to inflow
    }

    // ------------------------------------------------------------ Timer

    /**
     * Stellt fertige Erschließungen und Brücken fertig. Eine neue Galaxie beginnt eingefroren (Zyklus 0) mit drei
     * Naturgesetzen zur Wahl. Wurde die Uhr zurückgestellt, beginnt ein Timer neu, statt länger als seine Dauer
     * zu laufen. Ohne Änderung kommt derselbe Zustand zurück.
     */
    fun completeTimers(state: GameState, nowMs: Long): Pair<GameState, List<GameEvent>> {
        val events = ArrayList<GameEvent>()
        var next = state
        val unlock = state.unlock?.let { clamped(it, nowMs) }
        if (unlock != null && unlock.readyAtMs <= nowMs) {
            next = finishUnlock(next, unlock.kind)
            events += GameEvent.GalaxyUnlocked(unlock.kind)
        } else if (unlock != state.unlock) {
            next = next.copy(unlock = unlock)
        }
        if (state.bridges.any { !it.built }) {
            var built = 0
            val bridges = state.bridges.map { bridge ->
                if (bridge.built) return@map bridge
                val timer = clamped(bridge, nowMs)
                if (timer.readyAtMs > nowMs) return@map timer
                built++
                timer.copy(built = true).also { events += GameEvent.BridgeCompleted(it) }
            }
            if (bridges != state.bridges) {
                next = next.copy(bridges = bridges, stats = next.stats.copy(bridgesBuilt = next.stats.bridgesBuilt + built))
            }
        }
        if (events.isEmpty()) return next to events
        return engine.progression.checkAchievements(next, events) to events
    }

    /**
     * Beschleunigt laufende Timer nachträglich: Die Restzeit der Erschließung und aller Brücken im Bau wird
     * mit [factor] multipliziert. Für den Galaxie-Pionier (×0,5, auch beim Wiederherstellen) und jede Stufe
     * Raumfaltung (×0,85), die sonst erst beim nächsten Timer wirkten.
     */
    fun rescaleTimers(state: GameState, nowMs: Long, factor: Double): GameState {
        fun rescaled(readyAt: Long): Long = if (readyAt <= nowMs) readyAt else nowMs + ((readyAt - nowMs) * factor).toLong()
        if (state.unlock == null && state.bridges.all { it.built }) return state
        return state.copy(
            unlock = state.unlock?.let { it.copy(readyAtMs = rescaled(it.readyAtMs)) },
            bridges = state.bridges.map { if (it.built) it else it.copy(readyAtMs = rescaled(it.readyAtMs)) },
        )
    }

    // ------------------------------------------------------------ Intern

    private fun isFrozen(state: GameState, kind: GalaxyKind): Boolean =
        if (kind == state.activeGalaxy) state.lawChoices.isNotEmpty() else state.parked[kind]?.lawChoices?.isNotEmpty() == true

    private fun finishUnlock(state: GameState, kind: GalaxyKind): GameState {
        if (state.hasRun(kind)) return state.copy(unlock = null)
        val run = GalaxyFactory.freshRun(state, kind, GalaxyLaw.NORMAL, 0, engine.galaxyName(), engine.rollLawChoices(null))
        return state.copy(unlock = null, parked = state.parked + (kind to run))
    }

    /** Kristalle für den Rest eines Timers – nie mehr als seine ganze Dauer; `null`, wenn er schon abgelaufen ist. */
    private fun skipCost(startedAtMs: Long, readyAtMs: Long, nowMs: Long): Int? {
        val remaining = min(readyAtMs - nowMs, readyAtMs - startedAtMs)
        return if (remaining <= 0L) null else Balance.timerSkipCost(remaining)
    }

    /** Uhr zurückgestellt: Ein Timer läuft nie länger als seine Dauer, sondern beginnt jetzt neu. */
    private fun clamped(unlock: GalaxyUnlock, nowMs: Long): GalaxyUnlock {
        val duration = unlock.readyAtMs - unlock.startedAtMs
        return if (unlock.readyAtMs - nowMs <= duration) unlock else unlock.copy(startedAtMs = nowMs, readyAtMs = nowMs + duration)
    }

    private fun clamped(bridge: StarBridge, nowMs: Long): StarBridge {
        val duration = bridge.readyAtMs - bridge.startedAtMs
        return if (bridge.readyAtMs - nowMs <= duration) bridge else bridge.copy(startedAtMs = nowMs, readyAtMs = nowMs + duration)
    }
}
