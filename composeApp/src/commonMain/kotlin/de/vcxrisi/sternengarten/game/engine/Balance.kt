package de.vcxrisi.sternengarten.game.engine

import de.vcxrisi.sternengarten.game.model.Artifact
import de.vcxrisi.sternengarten.game.model.CosmicEvent
import de.vcxrisi.sternengarten.game.model.GameState
import de.vcxrisi.sternengarten.game.model.LifePhase
import de.vcxrisi.sternengarten.game.model.Star
import de.vcxrisi.sternengarten.game.model.StarType
import de.vcxrisi.sternengarten.game.model.StoreProduct
import de.vcxrisi.sternengarten.game.model.Upgrade
import kotlin.math.cbrt
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow

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
    const val NEUTRON_LEVEL_BONUS = 5
    const val MAGNETAR_AURA = 0.60
    const val NURSERY_AURA = 0.20
    const val QUASAR_PER_STAR = 0.03

    const val BLACK_HOLE_SHARE = 0.5
    const val BLACK_HOLE_RELEASE_MULT = 3.0
    /** Ein Schwarzes Loch fasst höchstens so viele Sekunden seines Zuflusses. */
    const val BLACK_HOLE_CAPACITY_SECONDS = 3600.0

    const val ENRICHMENT_PER_SUPERNOVA = 0.25
    const val ENRICHMENT_CAP = 5.0

    const val DISCOVERY_BONUS = 0.10
    const val DARK_MATTER_BONUS = 0.02
    const val ACHIEVEMENT_BONUS = 0.02

    const val COMET_BOOST_MULT = 5.0
    const val COMET_BOOST_SECONDS = 60.0
    const val METEOR_REWARD_SECONDS = 20.0
    const val METEOR_INTERVAL = 2.5

    const val BASE_OFFLINE_SECONDS = 2 * 3600.0
    const val OFFLINE_SECONDS_PER_DEEP_SLEEP = 2 * 3600.0
    const val PASS_OFFLINE_SECONDS = 4 * 3600.0
    const val PASS_PRODUCTION_MULT = 2.0

    const val REFUND_SHARE = 0.5

    const val SOLAR_STORM_MULT = 3.0
    const val DARK_TIDE_MULT = 3.0

    /** Kristalle, die ein Artefakt auf Höchststufe beim erneuten Fund zurückgibt. */
    const val MAXED_ARTIFACT_REFUND = 20

    fun gardenRadius(state: GameState): Int =
        (BASE_RADIUS + state.level(Upgrade.NEBULA_EXPANSION) + state.level(Upgrade.PRIMORDIAL_NEBULA) + state.law.radiusDelta)
            .coerceIn(MIN_RADIUS, MAX_RADIUS)

    fun costMultiplier(state: GameState): Double =
        state.law.costMult * (if (state.eventKind == CosmicEvent.STAR_RAIN) 0.5 else 1.0)

    fun starCost(state: GameState, type: StarType): Double {
        val owned = state.stars.values.count { it.type == type }
        return type.baseCost * type.costGrowth.pow(owned) * costMultiplier(state)
    }

    /** Jede Stufe eines Sterns kostet 14 % mehr als die vorige. */
    const val LEVEL_COST_GROWTH = 1.14

    /** Obergrenze für "Max", damit die Produktion je Stufe sicher im Double-Bereich bleibt. */
    const val MAX_LEVELS_PER_PURCHASE = 5000

    fun levelUpCost(state: GameState, star: Star): Double =
        star.type.baseCost * 0.6 * LEVEL_COST_GROWTH.pow(star.level - 1) * state.law.costMult

    /** Gesamtkosten für [count] Stufen am Stück (geometrische Summe). */
    fun levelUpCost(state: GameState, star: Star, count: Int): Double {
        if (count <= 0) return 0.0
        val growth = LEVEL_COST_GROWTH
        return levelUpCost(state, star) * (growth.pow(count) - 1.0) / (growth - 1.0)
    }

    /** Wie viele Stufen sich mit dem aktuellen Sternenstaub höchstens kaufen lassen (0, wenn keine). */
    fun maxAffordableLevels(state: GameState, star: Star): Int {
        val first = levelUpCost(state, star)
        if (first <= 0.0 || state.stardust < first) return 0
        val growth = LEVEL_COST_GROWTH
        val estimate = ln(1.0 + state.stardust * (growth - 1.0) / first) / ln(growth)
        var count = floor(estimate).toInt().coerceIn(0, MAX_LEVELS_PER_PURCHASE)
        // Rundungsfehler der Logarithmen ausgleichen.
        while (count > 0 && levelUpCost(state, star, count) > state.stardust) count--
        while (count < MAX_LEVELS_PER_PURCHASE && levelUpCost(state, star, count + 1) <= state.stardust) count++
        return count
    }

    /** Nach so vielen Stufen verdoppelt sich die Leistung eines Sterns zusätzlich. */
    const val LEVEL_DOUBLING_INTERVAL = 25

    /** Stufe 1 → ×1, jede Stufe linear mehr, alle [LEVEL_DOUBLING_INTERVAL] Stufen eine Verdopplung. */
    fun levelMultiplier(level: Int): Double = (level * 2.0.pow(level / LEVEL_DOUBLING_INTERVAL)).capped()

    fun upgradeCost(state: GameState, upgrade: Upgrade): Double =
        upgrade.baseCost * upgrade.costGrowth.pow(state.level(upgrade))

    fun effectiveLifespan(state: GameState, type: StarType): Double? =
        type.lifespan?.let {
            it * (1.0 + 0.25 * state.level(Upgrade.LONGEVITY)) *
                (1.0 + Artifact.EMBER.perLevel * state.artifactLevel(Artifact.EMBER))
        }

    fun agingMultiplier(state: GameState): Double =
        state.law.agingMult * (if (state.eventKind == CosmicEvent.SOLAR_STORM) 2.0 else 1.0)

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

    /** Faktor auf alle Nachbarschaftsboni (Gelbe Sterne, Pulsare, Magnetare, Nebelwiegen). */
    fun auraMultiplier(state: GameState): Double =
        state.law.auraMult *
            (1.0 + Artifact.GRAVITON_LENS.perLevel * state.artifactLevel(Artifact.GRAVITON_LENS)) *
            (if (state.eventKind == CosmicEvent.GRAVITY_WAVE) 2.0 else 1.0)

    fun constellationMultiplier(state: GameState): Double =
        state.law.constellationMult * (1.0 + Artifact.STAR_CHART.perLevel * state.artifactLevel(Artifact.STAR_CHART))

    fun supernovaElements(state: GameState, star: Star): Double =
        (1.0 + (star.level - 1) * 0.25) * state.law.supernovaMult *
            (1.0 + Artifact.PHOENIX_FEATHER.perLevel * state.artifactLevel(Artifact.PHOENIX_FEATHER))

    fun supernovaEnrichment(state: GameState): Double =
        ENRICHMENT_PER_SUPERNOVA * (1.0 + 0.5 * state.level(Upgrade.ASH_FERTILIZER)) * state.law.supernovaMult

    fun blackHoleReleaseMultiplier(state: GameState): Double =
        BLACK_HOLE_RELEASE_MULT + Artifact.HORIZON_SHARD.perLevel * state.artifactLevel(Artifact.HORIZON_SHARD)

    fun globalMultiplier(state: GameState): Double = (
        (1.0 + 0.25 * state.level(Upgrade.STELLAR_WIND)) *
            1.5.pow(state.level(Upgrade.FUSION)) *
            2.0.pow(state.level(Upgrade.DARK_ENERGY)) *
            (1.0 + DISCOVERY_BONUS * state.discovered.size) *
            (1.0 + DARK_MATTER_BONUS * state.darkMatter) *
            (1.0 + ACHIEVEMENT_BONUS * state.achievements.size) *
            (1.0 + Artifact.SEXTANT.perLevel * state.artifactLevel(Artifact.SEXTANT)) *
            Artifact.PRIMORDIAL_CRYSTAL.perLevel.pow(state.artifactLevel(Artifact.PRIMORDIAL_CRYSTAL)) *
            (if (state.owns(StoreProduct.WANDERER_PASS)) PASS_PRODUCTION_MULT else 1.0)
        ).capped()

    /** Sternenstaub dieser Galaxie, ab dem der Urknall 1 Dunkle Materie bringt. */
    const val DARK_MATTER_BASE = 1_000_000.0

    /**
     * Dunkle Materie wächst mit der Kubikwurzel des Sternenstaubs dieser Galaxie
     * (1M → 1, 1Mrd → 10, 1Bio → 100). Die Kubikwurzel hält das Wachstum über viele Galaxien stabil;
     * mit der Quadratwurzel schaukelten sich Dunkle Materie und Dunkle Energie gegenseitig auf.
     */
    fun darkMatterGain(state: GameState): Double =
        floor(
            cbrt(state.runStardust / DARK_MATTER_BASE) * state.law.darkMatterMult *
                (1.0 + Artifact.DARK_COMPASS.perLevel * state.artifactLevel(Artifact.DARK_COMPASS)),
        ).capped()

    /** Sternenstaub dieser Galaxie, ab dem der Urknall [gain] Dunkle Materie bringt. */
    fun runStardustForDarkMatter(state: GameState, gain: Double): Double {
        val factor = state.law.darkMatterMult * (1.0 + Artifact.DARK_COMPASS.perLevel * state.artifactLevel(Artifact.DARK_COMPASS))
        return ((gain / factor).pow(3) * DARK_MATTER_BASE).capped()
    }

    fun maxOfflineSeconds(state: GameState): Double =
        BASE_OFFLINE_SECONDS +
            OFFLINE_SECONDS_PER_DEEP_SLEEP * state.level(Upgrade.DEEP_SLEEP) +
            Artifact.CHRONOMETER.perLevel * state.artifactLevel(Artifact.CHRONOMETER) +
            (if (state.owns(StoreProduct.WANDERER_PASS)) PASS_OFFLINE_SECONDS else 0.0)

    fun startingStardust(state: GameState): Double =
        15.0 + if (state.level(Upgrade.STARDUST_MEMORY) > 0) 100.0 * 10.0.pow(state.level(Upgrade.STARDUST_MEMORY)) else 0.0

    fun cometInterval(state: GameState, roll: Double): Double =
        (45.0 + 60.0 * roll) / (1.0 + 0.3 * state.level(Upgrade.COMET_LURE))

    fun cometRewardMultiplier(state: GameState): Double =
        (1.0 + 0.5 * state.level(Upgrade.COMET_LURE)) *
            (1.0 + Artifact.COMET_HARP.perLevel * state.artifactLevel(Artifact.COMET_HARP))

    /** Abstand bis zum nächsten kosmischen Ereignis. */
    fun eventInterval(roll: Double): Double = 240.0 + 240.0 * roll
}
