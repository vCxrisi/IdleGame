package de.vcxrisi.sternengarten.ui.hud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.vcxrisi.sternengarten.game.model.Achievement
import de.vcxrisi.sternengarten.game.model.ConstellationKind
import de.vcxrisi.sternengarten.game.model.GalaxyKind
import de.vcxrisi.sternengarten.game.model.GoalKind
import de.vcxrisi.sternengarten.game.model.Hex
import de.vcxrisi.sternengarten.game.model.LoginReward
import de.vcxrisi.sternengarten.game.model.Metric
import de.vcxrisi.sternengarten.game.save.nowEpochMillis
import de.vcxrisi.sternengarten.ui.GameController
import de.vcxrisi.sternengarten.ui.GoalsTab
import de.vcxrisi.sternengarten.ui.Sheet
import de.vcxrisi.sternengarten.ui.render.drawGlow
import de.vcxrisi.sternengarten.ui.theme.Palette
import de.vcxrisi.sternengarten.ui.theme.Type
import de.vcxrisi.sternengarten.ui.theme.formatDuration
import de.vcxrisi.sternengarten.ui.theme.formatNumber
import de.vcxrisi.sternengarten.ui.theme.formatPercent
import kotlin.math.sqrt

@Composable
fun BoxScope.GoalsSheet(controller: GameController) {
    val state = controller.state
    val p = controller.progression
    BottomSheet(
        "Ziele", Palette.Accent, { controller.sheet = Sheet.NONE },
        header = {
            TabRow(
                GoalsTab.entries, controller.goalsTab, { it.title }, Palette.Accent, { controller.goalsTab = it },
                badge = { tab ->
                    when (tab) {
                        GoalsTab.MISSIONS -> state.missions.count { !it.claimed && p.missionProgress(state, it) >= it.target } +
                            (if (state.pendingLoginReward != null) 1 else 0)
                        GoalsTab.GALAXY -> state.galaxyGoals.count { !it.claimed && p.goalProgress(state, controller.analysis, it) >= it.target }
                        else -> 0
                    }
                },
            )
        },
    ) {
        when (controller.goalsTab) {
            GoalsTab.MISSIONS -> MissionsTab(controller)
            GoalsTab.GALAXY -> GalaxyGoalsTab(controller)
            GoalsTab.ACHIEVEMENTS -> AchievementsTab(controller)
            GoalsTab.CONSTELLATIONS -> ConstellationsTab(controller)
        }
    }
}

// ------------------------------------------------------------ Missionen & Login

@Composable
private fun MissionsTab(controller: GameController) {
    val state = controller.state
    SectionTitle("Tägliche Belohnung · Serie ${state.loginStreak} Tage", Palette.Crystal)
    LoginCalendar(state.loginStreak, state.pendingLoginReward)
    if (state.pendingLoginReward != null) {
        GlowButton("Belohnung abholen", controller::claimLoginReward, Modifier.fillMaxWidth(), color = Palette.Crystal, subtitle = state.pendingLoginReward.label)
    }

    val untilReset = (GameController.DAY_MS - nowEpochMillis() % GameController.DAY_MS) / 1000.0
    SectionTitle("Missionen · neu in ${formatDuration(untilReset)}", Palette.Accent)
    if (state.missions.isEmpty()) Txt("Neue Missionen erscheinen bald.", Type.Body)
    state.missions.forEachIndexed { index, mission ->
        val progress = controller.progression.missionProgress(state, mission)
        val done = progress >= mission.target
        ProgressRow(
            title = "${mission.metric.label}: ${formatTarget(mission.metric, mission.target)}",
            detail = if (mission.claimed) "Erledigt · +${mission.crystals} Kristalle erhalten"
            else "${formatTarget(mission.metric, progress.coerceAtMost(mission.target))} / ${formatTarget(mission.metric, mission.target)} · +${mission.crystals} Kristalle",
            fraction = if (mission.claimed) 1f else (progress / mission.target).toFloat(),
            color = if (mission.claimed) Palette.Success else Palette.Accent,
        ) {
            when {
                mission.claimed -> Txt("✓", Type.Title, color = Palette.Success)
                else -> GlowButton("Abholen", { controller.claimMission(index) }, color = Palette.Crystal, enabled = done, compact = true)
            }
        }
    }
}

private fun formatTarget(metric: Metric, value: Double): String =
    if (metric == Metric.TOTAL_STARDUST) formatNumber(value) else value.toLong().toString()

/** Sieben-Tage-Kalender der Login-Belohnungen. */
@Composable
fun LoginCalendar(streak: Int, pending: LoginReward?) {
    val currentIndex = ((streak - 1).coerceAtLeast(0)) % LoginReward.entries.size
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        LoginReward.entries.forEachIndexed { i, reward ->
            val claimed = i < currentIndex || (i == currentIndex && pending == null && streak > 0)
            val today = i == currentIndex
            val color = when {
                today && pending != null -> Palette.Crystal
                claimed -> Palette.Success
                else -> Palette.TextFaint
            }
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = if (today) 0.22f else 0.08f))
                    .border(1.dp, color.copy(alpha = if (today) 0.9f else 0.3f), RoundedCornerShape(10.dp))
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Txt("Tag ${i + 1}", Type.Small, color = color, maxLines = 1)
                when {
                    reward.capsules > 0 -> CapsuleIcon(Modifier.size(16.dp))
                    reward.crystals > 0 -> CrystalIcon(Modifier.size(14.dp))
                    else -> Txt("⧗", Type.Label, color = Palette.Stardust)
                }
                Txt(
                    when {
                        claimed -> "✓"
                        reward.crystals > 0 -> "${reward.crystals}"
                        reward.warpSeconds > 0 -> "${(reward.warpSeconds / 60).toInt()}m"
                        else -> "1"
                    },
                    Type.Small, color = Palette.Text, maxLines = 1,
                )
            }
        }
    }
}

// ------------------------------------------------------------ Galaxie-Ziele

@Composable
private fun GalaxyGoalsTab(controller: GameController) {
    val state = controller.state
    Txt(
        "Ziele für ${state.galaxyName}. Jedes bringt Dunkle Materie und Kristalle – alle drei zusammen zusätzlich eine Artefakt-Kapsel.",
        Type.Body,
    )
    state.galaxyGoals.forEachIndexed { index, goal ->
        val progress = controller.progression.goalProgress(state, controller.analysis, goal)
        val done = progress >= goal.target
        val value = { v: Double ->
            when (goal.kind) {
                GoalKind.RUN_STARDUST, GoalKind.PRODUCTION_RATE -> formatNumber(v)
                else -> v.toLong().toString()
            }
        }
        ProgressRow(
            title = "${goal.kind.label.replace("Sternenstaub", state.activeGalaxy.dustName)}: ${value(goal.target)}" +
                if (goal.kind == GoalKind.PRODUCTION_RATE) "/s" else "",
            detail = if (goal.claimed) "Erreicht · +${formatNumber(goal.darkMatter)} Dunkle Materie erhalten"
            else "${value(progress.coerceAtMost(goal.target))} / ${value(goal.target)} · +${formatNumber(goal.darkMatter)} DM · +${goal.crystals} Kristalle",
            fraction = if (goal.claimed) 1f else (progress / goal.target).toFloat(),
            color = if (goal.claimed) Palette.Success else Palette.DarkMatter,
        ) {
            if (goal.claimed) Txt("✓", Type.Title, color = Palette.Success)
            else GlowButton("Abholen", { controller.claimGoal(index) }, color = Palette.DarkMatter, enabled = done, compact = true)
        }
    }
    if (state.galaxyGoals.isNotEmpty() && state.galaxyGoals.all { it.claimed }) {
        Txt("Alle Ziele dieser Galaxie erfüllt! Zeit für den nächsten Urknall?", Type.Label, color = Palette.Success)
    }
    // Ziele geparkter Galaxien lassen sich nur dort abholen – wenigstens Bescheid geben.
    val elsewhere = controller.claimableByGalaxy.filterKeys { it != state.activeGalaxy }.filterValues { it > 0 }
    if (elsewhere.isNotEmpty()) {
        Txt(
            "Bereit in anderen Galaxien: " + elsewhere.entries.joinToString(" · ") { "${it.key.shortName} ${it.value}" } +
                ". Wechsle dorthin, um sie abzuholen.",
            Type.Small, color = Palette.Success,
        )
    }
}

// ------------------------------------------------------------ Erfolge

@Composable
private fun AchievementsTab(controller: GameController) {
    val state = controller.state
    Txt(
        "${state.achievements.size} von ${Achievement.entries.size} Erfolgen · jeder gibt dauerhaft +2 % Produktion " +
            "(aktuell ${formatPercent(0.02 * state.achievements.size)}).",
        Type.Body,
    )
    // Erreichte nach unten, offene nach Fortschritt sortiert.
    val sorted = Achievement.entries.sortedWith(
        compareBy<Achievement> { it in state.achievements }
            .thenByDescending { controller.progression.metric(state, it.metric) / it.threshold },
    )
    for (achievement in sorted) {
        val reached = achievement in state.achievements
        val value = controller.progression.metric(state, achievement.metric)
        val fmt = { v: Double -> if (achievement.metric == Metric.TOTAL_STARDUST) formatNumber(v) else v.toLong().toString() }
        ProgressRow(
            title = achievement.title,
            // Erreichte Erfolge zeigen ihre Schwelle – eine später gesunkene Messgröße (etwa Brücken) wäre verwirrend.
            detail = if (reached) "${achievement.metric.label}: ${fmt(achievement.threshold)} · +${achievement.crystals} Kristalle"
            else "${achievement.metric.label}: ${fmt(value.coerceAtMost(achievement.threshold))} / ${fmt(achievement.threshold)} · +${achievement.crystals} Kristalle",
            fraction = if (reached) 1f else (value / achievement.threshold).toFloat(),
            color = if (reached) Palette.Success else Palette.Crystal,
        ) {
            if (reached) Txt("✓", Type.Title, color = Palette.Success)
        }
    }
    SectionTitle("Chronik", Palette.TextDim)
    Txt(
        "Galaxien: ${1 + state.parked.size}/${GalaxyKind.entries.size} · Urknalle: ${state.stats.bigBangs} · " +
            "Felder gekauft: ${state.stats.fieldsBought} · Supernovas: ${state.supernovaCount} · Kometen: ${state.stats.cometsCaught} · " +
            "Staub insgesamt: ${formatNumber(state.totalStardust)} · Spielzeit: ${formatDuration(state.playTime)}",
        Type.Small,
    )
}

// ------------------------------------------------------------ Sternbilder

@Composable
private fun ConstellationsTab(controller: GameController) {
    val state = controller.state
    val active = controller.analysis.activeKinds
    Txt(
        "Jedes entdeckte Sternbild gibt dauerhaft +10 % auf alles – auch nach dem Urknall. " +
            "Aktive Sternbilder stärken zusätzlich jeden beteiligten Stern.",
        Type.Body,
    )
    for (kind in ConstellationKind.entries) {
        val found = kind in state.discovered
        val color = Color.hsv(kind.hue, 0.55f, 1f)
        Row(verticalAlignment = Alignment.CenterVertically) {
            ConstellationGlyph(kind, if (found) color else Palette.TextFaint, Modifier.size(54.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt(if (found) kind.displayName else "Unentdeckt", Type.Label, color = if (found) Palette.Text else Palette.TextFaint)
                    if (kind in active) {
                        Spacer(Modifier.width(8.dp))
                        Txt("AKTIV", Type.Small, color = color)
                    }
                }
                Txt(kind.hint, Type.Small)
                Txt(
                    "${formatPercent(kind.memberBonus * state.law.constellationMult)} je beteiligtem Stern",
                    Type.Small, color = color.copy(alpha = if (found) 0.9f else 0.4f),
                )
            }
        }
    }
}

/** Kleine Skizze der Sternbild-Form. */
@Composable
private fun ConstellationGlyph(kind: ConstellationKind, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val unit = size.minDimension / 5.6f
        val sqrt3 = sqrt(3f)
        fun p(h: Hex) = Offset(center.x + unit * sqrt3 * (h.q + h.r / 2f), center.y + unit * 1.5f * h.r)
        val line = { n: Int -> (0 until n).map { Hex(it, 0) } }
        val points: List<Hex> = when (kind) {
            ConstellationKind.TRIO -> line(3)
            ConstellationKind.TRIANGULUM -> listOf(Hex(0, 0), Hex(1, -1), Hex(0, -1), Hex(0, 0))
            ConstellationKind.RED_THREAD, ConstellationKind.RAINBOW -> line(4)
            ConstellationKind.LADDER -> line(5)
            ConstellationKind.KILONOVA -> line(2)
            ConstellationKind.CROWN, ConstellationKind.SUN_CROWN, ConstellationKind.EVENT_HORIZON,
            ConstellationKind.QUASAR_THRONE,
            -> Hex.ORIGIN.neighbors() + Hex.ORIGIN.neighbors().first()
        }
        // Linien mittig ausrichten.
        val raw = points.map { p(it) }
        val shift = if (points.all { it.r == 0 }) Offset(-(raw.maxOf { it.x } + raw.minOf { it.x }) / 2f + center.x, 0f) else Offset.Zero
        val pts = raw.map { it + shift }
        for (i in 0 until pts.size - 1) drawLine(color.copy(alpha = 0.6f), pts[i], pts[i + 1], strokeWidth = 2f)
        for (pt in pts.distinct()) drawGlow(pt, unit * 0.8f, color, 0.9f)
        val core = when (kind) {
            ConstellationKind.CROWN -> color
            ConstellationKind.SUN_CROWN -> Color(0xFFFFC53D)
            ConstellationKind.EVENT_HORIZON -> Color(0xFFFF9A3D)
            ConstellationKind.QUASAR_THRONE -> Color(0xFFFFE27A)
            else -> null
        }
        if (core != null) drawGlow(center, unit, core, 1f)
    }
}
