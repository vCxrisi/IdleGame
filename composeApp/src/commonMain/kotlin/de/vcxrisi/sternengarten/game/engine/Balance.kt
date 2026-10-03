package de.vcxrisi.sternengarten.game.engine

import de.vcxrisi.sternengarten.game.model.GameState
import de.vcxrisi.sternengarten.game.model.LifePhase
import de.vcxrisi.sternengarten.game.model.Star
import de.vcxrisi.sternengarten.game.model.StarType
import de.vcxrisi.sternengarten.game.model.Upgrade
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sqrt

/** Alle Zahlen, an denen das Spielgefühl hängt, an einem Ort. */
object Balance {
    const val BASE_RADIUS = 2
    const val MIN_RADIUS = 1
    const val MAX_RADIUS = 6

    const val PROTO_SECONDS = 5.0
    const val GIANT_FRACTION = 0.8
    const val GIANT_MULT = 1.5
    const val WHITE_DWARF_MULT = 0.5

    const val YELLOW_AURA = 0.25
    const val PULSAR_AURA = 0.40
    const val PULSAR_RANGE = 3
    const val BLUE_GIANT_CROWDING = 0.15
    const val BINARY_PAIR_MULT = 2.0

    const val BLACK_HOLE_SHARE = 0.5
    const val BLACK_HOLE_RELEASE_MULT = 3.0
    /** Ein Schwarzes Loch fasst höchstens so viele Sekunden seines Zuflusses. */
    const val BLACK_HOLE_CAPACITY_SECONDS = 3600.0

    const val ENRICHMENT_PER_SUPERNOVA = 0.25
    const val ENRICHMENT_CAP = 5.0

    const val DISCOVERY_BONUS = 0.10
    const val DARK_MATTER_BONUS = 0.02

    const val COMET_BOOST_MULT = 5.0
    const val COMET_BOOST_SECONDS = 60.0

    const val BASE_OFFLINE_SECONDS = 2 * 3600.0
    const val OFFLINE_SECONDS_PER_DEEP_SLEEP = 2 * 3600.0

    const val REFUND_SHARE = 0.5

    fun gardenRadius(state: GameState): Int =
        (BASE_RADIUS + state.level(Upgrade.NEBULA_EXPANSION) + state.level(Upgrade.PRIMORDIAL_NEBULA) + state.law.radiusDelta)
            .coerceIn(MIN_RADIUS, MAX_RADIUS)

    fun starCost(state: GameState, type: StarType): Double {
        val owned = state.stars.values.count { it.type == type }
        return type.baseCost * type.costGrowth.pow(owned) * state.law.costMult
    }

    fun levelUpCost(state: GameState, star: Star): Double =
        star.type.baseCost * 0.6 * 1.14.pow(star.level - 1) * state.law.costMult

    /** Stufe 1 → ×1, jede Stufe linear mehr, alle 10 Stufen eine Verdopplung. */
    fun levelMultiplier(level: Int): Double = level * 2.0.pow(level / 10)

    fun upgradeCost(state: GameState, upgrade: Upgrade): Double =
        upgrade.baseCost * upgrade.costGrowth.pow(state.level(upgrade))

    fun effectiveLifespan(state: GameState, type: StarType): Double? =
        type.lifespan?.let { it * (1.0 + 0.25 * state.level(Upgrade.LONGEVITY)) }

    fun phaseOf(state: GameState, star: Star): LifePhase {
        if (star.whiteDwarf) return LifePhase.WHITE_DWARF
        if (star.age < PROTO_SECONDS) return LifePhase.PROTO
        val lifespan = effectiveLifespan(state, star.type) ?: return LifePhase.MAIN
        return if (star.age >= lifespan * GIANT_FRACTION) LifePhase.GIANT else LifePhase.MAIN
    }

    fun phaseMultiplier(state: GameState, star: Star): Double = when (phaseOf(state, star)) {
        LifePhase.PROTO -> 0.2 + 0.8 * (star.age / PROTO_SECONDS).coerceIn(0.0, 1.0)
        LifePhase.MAIN -> 1.0
        LifePhase.GIANT -> GIANT_MULT
        LifePhase.WHITE_DWARF -> WHITE_DWARF_MULT
    }

    fun supernovaElements(state: GameState, star: Star): Double =
        (1.0 + (star.level - 1) * 0.25) * state.law.supernovaMult

    fun supernovaEnrichment(state: GameState): Double =
        ENRICHMENT_PER_SUPERNOVA * (1.0 + 0.5 * state.level(Upgrade.ASH_FERTILIZER)) * state.law.supernovaMult

    fun globalMultiplier(state: GameState): Double =
        (1.0 + 0.25 * state.level(Upgrade.STELLAR_WIND)) *
            1.5.pow(state.level(Upgrade.FUSION)) *
            2.0.pow(state.level(Upgrade.DARK_ENERGY)) *
            (1.0 + DISCOVERY_BONUS * state.discovered.size) *
            (1.0 + DARK_MATTER_BONUS * state.darkMatter)

    fun darkMatterGain(state: GameState): Double =
        floor(sqrt(state.runStardust / 1_000_000.0) * state.law.darkMatterMult)

    fun maxOfflineSeconds(state: GameState): Double =
        BASE_OFFLINE_SECONDS + OFFLINE_SECONDS_PER_DEEP_SLEEP * state.level(Upgrade.DEEP_SLEEP)

    fun startingStardust(state: GameState): Double =
        15.0 + if (state.level(Upgrade.STARDUST_MEMORY) > 0) 100.0 * 10.0.pow(state.level(Upgrade.STARDUST_MEMORY)) else 0.0

    fun cometInterval(state: GameState, roll: Double): Double =
        (45.0 + 60.0 * roll) / (1.0 + 0.3 * state.level(Upgrade.COMET_LURE))
}
