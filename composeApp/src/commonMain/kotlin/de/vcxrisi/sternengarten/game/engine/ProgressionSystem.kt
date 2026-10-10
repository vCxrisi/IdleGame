package de.vcxrisi.sternengarten.game.engine

import de.vcxrisi.sternengarten.game.model.Achievement
import de.vcxrisi.sternengarten.game.model.GalaxyGoal
import de.vcxrisi.sternengarten.game.model.GameState
import de.vcxrisi.sternengarten.game.model.GoalKind
import de.vcxrisi.sternengarten.game.model.LoginReward
import de.vcxrisi.sternengarten.game.model.Metric
import de.vcxrisi.sternengarten.game.model.Mission
import de.vcxrisi.sternengarten.game.model.StarType
import de.vcxrisi.sternengarten.game.model.hasCollapsed
import de.vcxrisi.sternengarten.game.model.projected
import de.vcxrisi.sternengarten.game.model.runKinds
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/** Missionen, Erfolge, Login-Kalender und Galaxie-Ziele. */
class ProgressionSystem(private val random: Random) {

    fun metric(state: GameState, metric: Metric): Double = when (metric) {
        Metric.STARS_PLANTED -> state.stats.starsPlanted.toDouble()
        Metric.LEVEL_UPS -> state.stats.levelUps.toDouble()
        Metric.HIGHEST_LEVEL -> state.stats.highestLevel.toDouble()
        Metric.SUPERNOVAS -> state.supernovaCount.toDouble()
        Metric.COMETS -> state.stats.cometsCaught.toDouble()
        Metric.BLACK_HOLE_RELEASES -> state.stats.blackHoleReleases.toDouble()
        Metric.TOTAL_STARDUST -> state.totalStardust
        Metric.BIG_BANGS -> state.stats.bigBangs.toDouble()
        Metric.EVENTS -> state.stats.eventsSeen.toDouble()
        Metric.MISSIONS -> state.stats.missionsCompleted.toDouble()
        Metric.CONSTELLATIONS -> state.discovered.size.toDouble()
        Metric.STAR_TYPES -> state.unlocked.size.toDouble()
        Metric.ARTIFACTS -> state.artifacts.count { it.value > 0 }.toDouble()
        Metric.CAPSULES -> state.stats.capsulesOpened.toDouble()
        Metric.FIELDS_BOUGHT -> state.stats.fieldsBought.toDouble()
        Metric.GALAXIES -> (1 + state.parked.size).toDouble()
        Metric.BRIDGES -> state.bridges.count { it.built }.toDouble()
        Metric.KINDS_COLLAPSED -> state.runKinds().count { state.hasCollapsed(it) }.toDouble()
    }

    // ------------------------------------------------------------ Erfolge

    fun checkAchievements(state: GameState, events: MutableList<GameEvent>): GameState {
        var next = state
        for (achievement in Achievement.entries) {
            if (achievement in next.achievements) continue
            if (metric(next, achievement.metric) < achievement.threshold) continue
            next = next.copy(
                achievements = next.achievements + achievement,
                crystals = next.crystals + achievement.crystals,
            )
            events += GameEvent.AchievementUnlocked(achievement)
        }
        return next
    }

    // ------------------------------------------------------------ Tageswechsel

    /** Login-Serie, tägliche Missionen und fehlende Galaxie-Ziele für den Tag [today] (Tage seit 1970). */
    fun refreshDaily(state: GameState, today: Long): GameState {
        var next = state
        if (next.lastLoginDay != today) {
            val streak = if (next.lastLoginDay == today - 1) next.loginStreak + 1 else 1
            next = next.copy(
                loginStreak = streak,
                lastLoginDay = today,
                pendingLoginReward = LoginReward.entries[(streak - 1) % LoginReward.entries.size],
            )
        }
        if (next.missionDay != today) {
            next = next.copy(missions = rollMissions(next, today), missionDay = today)
        }
        if (next.galaxyGoals.isEmpty() && next.lawChoices.isEmpty()) {
            next = next.copy(galaxyGoals = rollGalaxyGoals(next))
        }
        return next
    }

    /** Liefert die eingelöste Belohnung und den Sternenstaub aus einem Zeitsprung. */
    fun claimLoginReward(state: GameState): Triple<GameState, LoginReward, Double>? {
        val reward = state.pendingLoginReward ?: return null
        val warp = (BoardAnalyzer.analyze(state).totalRate * state.law.onlineMult * reward.warpSeconds).capped()
        val next = state.copy(
            pendingLoginReward = null,
            crystals = state.crystals + reward.crystals,
            capsules = state.capsules + reward.capsules,
            stardust = state.stardust + warp,
            runStardust = state.runStardust + warp,
            totalStardust = state.totalStardust + Balance.normalizedDust(state, warp),
        )
        return Triple(next, reward, warp)
    }

    // ------------------------------------------------------------ Missionen

    fun rollMissions(state: GameState, day: Long): List<Mission> {
        val dayRandom = Random(day * 7919 + 17)
        val candidates = buildList {
            add(Metric.STARS_PLANTED)
            add(Metric.LEVEL_UPS)
            add(Metric.COMETS)
            add(Metric.TOTAL_STARDUST)
            add(Metric.EVENTS)
            add(Metric.FIELDS_BOUGHT)
            if (StarType.BLUE_GIANT in state.unlocked) add(Metric.SUPERNOVAS)
            if (StarType.BLACK_HOLE in state.unlocked) add(Metric.BLACK_HOLE_RELEASES)
            // Laufen mehrere Galaxien, ist ein Urknall am Tag gut zu schaffen.
            if (state.parked.isNotEmpty()) add(Metric.BIG_BANGS)
        }.shuffled(dayRandom).take(3)
        return candidates.map { metric ->
            val (target, crystals) = when (metric) {
                Metric.STARS_PLANTED -> dayRandom.nextInt(5, 13).toDouble() to 10
                Metric.LEVEL_UPS -> dayRandom.nextInt(15, 41).toDouble() to 10
                Metric.COMETS -> dayRandom.nextInt(2, 5).toDouble() to 15
                Metric.SUPERNOVAS -> dayRandom.nextInt(2, 6).toDouble() to 15
                Metric.BLACK_HOLE_RELEASES -> dayRandom.nextInt(1, 4).toDouble() to 15
                Metric.EVENTS -> 1.0 to 10
                Metric.FIELDS_BOUGHT -> dayRandom.nextInt(4, 11).toDouble() to 10
                Metric.BIG_BANGS -> 1.0 to 20
                // Eine Viertelstunde Produktion aller Galaxien, in Sternenstaub-Wert wie die Messgröße selbst.
                Metric.TOTAL_STARDUST -> roundNice(max(500.0, normalizedRate(state) * 900.0)) to 10
                else -> 1.0 to 10
            }
            Mission(metric, target, metric(state, metric), crystals)
        }
    }

    /** Eigene Produktion aller Galaxien pro Sekunde, umgerechnet in Sternenstaub (Staub ÷ K). */
    private fun normalizedRate(state: GameState): Double =
        state.runKinds().mapNotNull { state.projected(it) }.sumOf { view ->
            BoardAnalyzer.analyze(view).totalRate * view.law.onlineMult / view.activeGalaxy.costScale
        }

    fun missionProgress(state: GameState, mission: Mission): Double =
        (metric(state, mission.metric) - mission.baseline).coerceAtLeast(0.0)

    fun claimMission(state: GameState, index: Int): GameState? {
        val mission = state.missions.getOrNull(index) ?: return null
        if (mission.claimed || missionProgress(state, mission) < mission.target) return null
        return state.copy(
            missions = state.missions.mapIndexed { i, m -> if (i == index) m.copy(claimed = true) else m },
            crystals = state.crystals + mission.crystals,
            stats = state.stats.copy(missionsCompleted = state.stats.missionsCompleted + 1),
        )
    }

    // ------------------------------------------------------------ Galaxie-Ziele

    /** Drei Ziele für den aktuellen Zyklus der aktiven Galaxie; Staubziele wachsen mit K, die Belohnung mit der Galaxieart. */
    fun rollGalaxyGoals(state: GameState): List<GalaxyGoal> {
        val g = state.galaxyNumber
        val kind = state.activeGalaxy
        val k = kind.costScale
        val maxFields = Balance.maxFieldCount(state.law).toDouble()
        val darkMatter = ceil((g + 1) / 2.0 * kind.goalDarkMatterMult)
        // Die besondere Sternart erst als Ziel, wenn sie schon freigeschaltet ist – sonst bleibt es unerreichbar.
        val exclusiveReady = kind.exclusiveStar?.let { it in state.unlocked } == true
        return GoalKind.entries.filter { it != GoalKind.EXCLUSIVE_STARS || exclusiveReady }.shuffled(random).take(3).map { goal ->
            val target = when (goal) {
                GoalKind.RUN_STARDUST -> 2e6 * 5.0.pow(g - 1) * k
                GoalKind.STARS_AT_ONCE -> minOf(12.0 + 4 * (g - 1), 60.0, maxFields)
                GoalKind.RUN_SUPERNOVAS -> 3.0 + 2 * g
                GoalKind.STAR_LEVEL -> 15.0 + 5 * g
                GoalKind.ACTIVE_CONSTELLATIONS -> min(3.0 + g / 2, 8.0)
                GoalKind.PRODUCTION_RATE -> 500.0 * 8.0.pow(g - 1) * k
                GoalKind.FIELDS_OWNED ->
                    min(Balance.startFieldCount(state, kind, state.law) + 6.0 + 3 * g, maxFields)
                GoalKind.EXCLUSIVE_STARS -> min(1.0 + g / 3, 4.0)
                GoalKind.WHITE_DWARFS -> min(2.0 + g, 10.0)
            }
            GalaxyGoal(goal, target, darkMatter, crystals = 15)
        }
    }

    fun goalProgress(state: GameState, analysis: BoardAnalysis, goal: GalaxyGoal): Double = when (goal.kind) {
        GoalKind.RUN_STARDUST -> state.runStardust
        GoalKind.STARS_AT_ONCE -> state.stars.size.toDouble()
        GoalKind.RUN_SUPERNOVAS -> state.runSupernovas.toDouble()
        GoalKind.STAR_LEVEL -> (state.stars.values.maxOfOrNull { it.level } ?: 0).toDouble()
        GoalKind.ACTIVE_CONSTELLATIONS -> analysis.activeKinds.size.toDouble()
        GoalKind.PRODUCTION_RATE -> analysis.totalRate * state.law.onlineMult
        GoalKind.FIELDS_OWNED -> state.ownedFields.size.toDouble()
        GoalKind.EXCLUSIVE_STARS -> state.stars.values.count { it.type.exclusiveTo == state.activeGalaxy }.toDouble()
        GoalKind.WHITE_DWARFS -> state.stars.values.count { it.whiteDwarf }.toDouble()
    }

    /** Gibt den neuen Zustand zurück und ob damit alle Ziele der Galaxie erfüllt sind. */
    fun claimGoal(state: GameState, index: Int): Pair<GameState, Boolean>? {
        val goal = state.galaxyGoals.getOrNull(index) ?: return null
        if (goal.claimed || goalProgress(state, BoardAnalyzer.analyze(state), goal) < goal.target) return null
        val goals = state.galaxyGoals.mapIndexed { i, g -> if (i == index) g.copy(claimed = true) else g }
        val allDone = goals.all { it.claimed }
        return state.copy(
            galaxyGoals = goals,
            darkMatter = state.darkMatter + goal.darkMatter,
            crystals = state.crystals + goal.crystals,
            capsules = state.capsules + if (allDone) 1 else 0,
        ) to allDone
    }

    /** Rundet auf zwei signifikante Stellen – "1.234.567" wird zu "1.200.000". */
    private fun roundNice(value: Double): Double {
        if (value <= 0) return 0.0
        val magnitude = 10.0.pow(floor(log10(value)) - 1)
        return ceil(value / magnitude) * magnitude
    }
}
