package de.vcxrisi.sternengarten.game.engine

import de.vcxrisi.sternengarten.game.model.Artifact
import de.vcxrisi.sternengarten.game.model.CosmicEvent
import de.vcxrisi.sternengarten.game.model.Currency
import de.vcxrisi.sternengarten.game.model.GalaxyKind
import de.vcxrisi.sternengarten.game.model.GalaxyLaw
import de.vcxrisi.sternengarten.game.model.GameState
import de.vcxrisi.sternengarten.game.model.Hex
import de.vcxrisi.sternengarten.game.model.LifePhase
import de.vcxrisi.sternengarten.game.model.Star
import de.vcxrisi.sternengarten.game.model.StarBridge
import de.vcxrisi.sternengarten.game.model.StarType
import de.vcxrisi.sternengarten.game.model.StoreProduct
import de.vcxrisi.sternengarten.game.model.Upgrade
import kotlin.math.cbrt
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Alle Zahlen, an denen das Spielgefühl hängt, an einem Ort. */
object Balance {
    /** Radien des früheren Ring-Gartens; die Spielstand-Migration rechnet damit noch alte Gärten um. */
    const val BASE_RADIUS = 2
    const val MIN_RADIUS = 1
    /** Weiter als so viele Felder vom Zentrum reicht kein Garten. */
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

    // ---------------------------------------------------------------- Galaxiearten

    /** K der aktiven Galaxie: Faktor auf alle Staubpreise. */
    fun costScale(state: GameState): Double = state.activeGalaxy.costScale

    /** Staub der aktiven Galaxie in Sternenstaub-Wert – so zählt [GameState.totalStardust] alle Galaxien gleich. */
    fun normalizedDust(state: GameState, amount: Double): Double = amount / costScale(state)

    fun costMultiplier(state: GameState): Double =
        state.law.costMult * (if (state.eventKind == CosmicEvent.STAR_RAIN) 0.5 else 1.0) * costScale(state)

    fun starCost(state: GameState, type: StarType): Double {
        val owned = state.stars.values.count { it.type == type }
        return type.baseCost * type.costGrowth.pow(owned) * costMultiplier(state)
    }

    /** Jede Stufe eines Sterns kostet 14 % mehr als die vorige. */
    const val LEVEL_COST_GROWTH = 1.14

    /** Obergrenze für "Max", damit die Produktion je Stufe sicher im Double-Bereich bleibt. */
    const val MAX_LEVELS_PER_PURCHASE = 5000

    fun levelUpCost(state: GameState, star: Star): Double =
        star.type.baseCost * 0.6 * LEVEL_COST_GROWTH.pow(star.level - 1) * state.law.costMult * costScale(state)

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

    /** Forschung für Staub kostet in jeder Galaxie ×K, Elemente und Dunkle Materie nicht. */
    fun upgradeCost(state: GameState, upgrade: Upgrade): Double =
        upgrade.baseCost * upgrade.costGrowth.pow(state.level(upgrade)) *
            (if (upgrade.currency == Currency.STARDUST) costScale(state) else 1.0)

    fun effectiveLifespan(state: GameState, type: StarType): Double? =
        type.lifespan?.let {
            it * (1.0 + 0.25 * state.level(Upgrade.LONGEVITY)) *
                (1.0 + Artifact.EMBER.perLevel * state.artifactLevel(Artifact.EMBER))
        }

    fun agingMultiplier(state: GameState): Double =
        state.law.agingMult * state.activeGalaxy.agingMult * (if (state.eventKind == CosmicEvent.SOLAR_STORM) 2.0 else 1.0)

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
        (1.0 + (star.level - 1) * 0.25) * state.law.supernovaMult * state.activeGalaxy.supernovaMult *
            (1.0 + Artifact.PHOENIX_FEATHER.perLevel * state.artifactLevel(Artifact.PHOENIX_FEATHER))

    fun supernovaEnrichment(state: GameState): Double =
        ENRICHMENT_PER_SUPERNOVA * (1.0 + 0.5 * state.level(Upgrade.ASH_FERTILIZER)) * state.law.supernovaMult *
            state.activeGalaxy.supernovaMult

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

    /** Sternenstaub dieser Galaxie, ab dem der Urknall 1 Dunkle Materie bringt (in anderen Galaxiearten ×K). */
    const val DARK_MATTER_BASE = 1_000_000.0

    /** Naturgesetz und Dunkler Kompass – ohne den Faktor der Galaxieart. */
    fun darkMatterFactor(state: GameState): Double =
        state.law.darkMatterMult * (1.0 + Artifact.DARK_COMPASS.perLevel * state.artifactLevel(Artifact.DARK_COMPASS))

    /**
     * Dunkle Materie ohne den Faktor der Galaxieart. Sie wächst mit der Kubikwurzel des Staubs dieser Galaxie
     * geteilt durch K (1M → 1, 1Mrd → 10, 1Bio → 100). Die Kubikwurzel hält das Wachstum über viele Galaxien stabil;
     * mit der Quadratwurzel schaukelten sich Dunkle Materie und Dunkle Energie gegenseitig auf.
     * Ab 1 ist ein Urknall möglich – der Faktor der Galaxieart zählt dafür nicht, sonst ließen sich teure
     * Galaxien ständig für ein paar Dunkle Materie kollabieren.
     */
    fun darkMatterBase(state: GameState): Double =
        cbrt(state.runStardust / (DARK_MATTER_BASE * costScale(state))) * darkMatterFactor(state)

    /** Was der Urknall dieser Galaxie bringt: Basiswert mal Faktor der Galaxieart, 0 unter der Schwelle. */
    fun darkMatterGain(state: GameState): Double {
        val base = darkMatterBase(state)
        return if (base < 1.0) 0.0 else floor(base * state.activeGalaxy.darkMatterMult).capped()
    }

    /** Staub dieser Galaxie, ab dem der Urknall [gain] Dunkle Materie bringt – nie unter der Urknall-Schwelle. */
    fun runStardustForDarkMatter(state: GameState, gain: Double): Double =
        ((max(gain / state.activeGalaxy.darkMatterMult, 1.0) / darkMatterFactor(state)).pow(3) *
            DARK_MATTER_BASE * costScale(state)).capped()

    fun maxOfflineSeconds(state: GameState): Double =
        BASE_OFFLINE_SECONDS +
            OFFLINE_SECONDS_PER_DEEP_SLEEP * state.level(Upgrade.DEEP_SLEEP) +
            Artifact.CHRONOMETER.perLevel * state.artifactLevel(Artifact.CHRONOMETER) +
            (if (state.owns(StoreProduct.WANDERER_PASS)) PASS_OFFLINE_SECONDS else 0.0)

    /** Startstaub einer neuen Galaxie der Art [kind] – wie alle Preise dort ×K. */
    fun startingStardust(state: GameState, kind: GalaxyKind = state.activeGalaxy): Double =
        (15.0 + if (state.level(Upgrade.STARDUST_MEMORY) > 0) 100.0 * 10.0.pow(state.level(Upgrade.STARDUST_MEMORY)) else 0.0) *
            kind.costScale

    /** Ab diesem Staub-Stand wird [type] in der aktiven Galaxie freigeschaltet. */
    fun unlockThreshold(state: GameState, type: StarType): Double = type.unlockAt * costScale(state)

    fun cometInterval(state: GameState, roll: Double): Double =
        (45.0 + 60.0 * roll) / (1.0 + 0.3 * state.level(Upgrade.COMET_LURE))

    fun cometRewardMultiplier(state: GameState): Double =
        (1.0 + 0.5 * state.level(Upgrade.COMET_LURE)) *
            (1.0 + Artifact.COMET_HARP.perLevel * state.artifactLevel(Artifact.COMET_HARP))

    /** Ein Komet bringt mindestens so viel Staub, auch in einem leeren Garten. */
    fun cometMinReward(state: GameState): Double = 25.0 * costScale(state)

    /** Abstand bis zum nächsten kosmischen Ereignis. */
    fun eventInterval(roll: Double): Double = 240.0 + 240.0 * roll

    /** Takt, in dem die geparkten Galaxien bei offener App weiterlaufen (Sekunden). */
    const val BACKGROUND_STEP = 1.0

    /** Offline genügen grobe Schritte: Die Produktion ist zwischen zwei Lebensereignissen konstant. */
    const val OFFLINE_STEP_SECONDS = 5.0
    /**
     * Schritte, die sich alle Galaxien beim Nachholen der Offline-Zeit teilen: Jede geparkte bekommt
     * [PARKED_OFFLINE_STEPS], die aktive den Rest, aber nie weniger als [MIN_ACTIVE_OFFLINE_STEPS].
     * Bei 21 h Offline-Zeit ist ein Schritt einer geparkten Galaxie 840 s lang – kurzlebige Sterne
     * (Blauer Riese, Glutstern) leben dort höchstens einen Schritt zu lang.
     */
    const val OFFLINE_STEP_BUDGET = 720
    const val PARKED_OFFLINE_STEPS = 90
    const val MIN_ACTIVE_OFFLINE_STEPS = 360

    /** Offline-Schritte der aktiven Galaxie, wenn [parked] Galaxien mitlaufen. */
    fun activeOfflineSteps(parked: Int): Int =
        (OFFLINE_STEP_BUDGET - PARKED_OFFLINE_STEPS * parked).coerceAtLeast(MIN_ACTIVE_OFFLINE_STEPS)

    // ---------------------------------------------------------------- Felder

    const val FIELD_BASE_COST = 25.0
    const val FIELD_COST_GROWTH = 1.15
    /** Zusätzliche Startfelder je Stufe Urnebel (0, 1, 2). */
    val PRIMORDIAL_FIELDS = listOf(0, 18, 42)
    const val PIONEER_FIELDS = 6
    /** Startfelder je Ring, den ein Naturgesetz den Garten kleiner macht. */
    const val RADIUS_DELTA_FIELDS = 12
    const val MIN_START_FIELDS = 7
    /** Preisfaktor je Stufe Nebelvermessung. */
    const val NEBULA_SURVEY_FACTOR = 0.85

    /** Reihenfolge der Startfelder: Ring für Ring, je sechs Felder als volle Drehung – der Start ist immer symmetrisch. */
    val FIELD_ORDER: List<Hex> = Hex.spiral(MAX_RADIUS)

    /** Bis zu diesem Abstand vom Zentrum lassen sich Felder kaufen (6, unter Hoher Gravitation 5). */
    fun maxFieldRadius(law: GalaxyLaw): Int = MAX_RADIUS + law.radiusDelta

    fun maxFieldCount(law: GalaxyLaw): Int {
        val r = maxFieldRadius(law)
        return 1 + 3 * r * (r + 1)
    }

    /** Wie viele Felder eine neue Galaxie der Art [kind] unter [law] geschenkt bekommt. */
    fun startFieldCount(meta: GameState, kind: GalaxyKind, law: GalaxyLaw): Int {
        val primordial = PRIMORDIAL_FIELDS[meta.level(Upgrade.PRIMORDIAL_NEBULA).coerceIn(0, PRIMORDIAL_FIELDS.lastIndex)]
        val pioneer = if (meta.owns(StoreProduct.GALAXY_PIONEER)) PIONEER_FIELDS else 0
        return (kind.coreFields + primordial + pioneer + RADIUS_DELTA_FIELDS * law.radiusDelta)
            .coerceIn(MIN_START_FIELDS, maxFieldCount(law))
    }

    fun startFields(meta: GameState, kind: GalaxyKind, law: GalaxyLaw): Set<Hex> =
        FIELD_ORDER.take(startFieldCount(meta, kind, law)).toSet()

    /** Preis des Feldes, nachdem in dieser Galaxie schon [bought] Felder gekauft wurden. */
    fun fieldCostAt(state: GameState, bought: Int): Double =
        (FIELD_BASE_COST * FIELD_COST_GROWTH.pow(bought) * costScale(state) * state.law.fieldCostMult *
            state.activeGalaxy.fieldCostMult * NEBULA_SURVEY_FACTOR.pow(state.level(Upgrade.NEBULA_SURVEY))).capped()

    /** Preis des nächsten Feldes – jedes kostet 15 % mehr als das vorige. */
    fun fieldCost(state: GameState): Double = fieldCostAt(state, state.fieldsBought)

    /** Felder, die an den Garten grenzen und noch gekauft werden können. */
    fun frontier(state: GameState): Set<Hex> {
        val maxRadius = maxFieldRadius(state.law)
        return buildSet {
            for (hex in state.ownedFields) {
                for (n in hex.neighbors()) if (n !in state.ownedFields && n.length() <= maxRadius) add(n)
            }
        }
    }

    // ---------------------------------------------------------------- Erschließen und Sternenbrücken

    const val HOUR_MS = 3_600_000.0
    const val PIONEER_TIMER_MULT = 0.5
    /** Zeitfaktor je Stufe Raumfaltung. */
    const val SPACE_FOLD_FACTOR = 0.85
    /** Sofort fertig: ein Kristall je angefangene drei Minuten, höchstens [MAX_TIMER_SKIP_CRYSTALS]. */
    const val TIMER_SKIP_MS_PER_CRYSTAL = 180_000.0
    const val MAX_TIMER_SKIP_CRYSTALS = 400

    /** Bauzeit der ersten bis fünften Brücke in Stunden. */
    val BRIDGE_HOURS = listOf(0.5, 2.0, 6.0, 12.0, 24.0)
    const val BRIDGE_BASE_COST = 50.0
    const val BRIDGE_COST_GROWTH = 10.0
    const val BRIDGE_BASE_SHARE = 0.10
    const val BRIDGE_SHARE_PER_CRAFT = 0.05
    const val BRIDGE_BASE_CAP = 0.50
    const val BRIDGE_CAP_PER_CRAFT = 0.25

    /** Faktor auf Erschließungs- und Bauzeiten: Galaxie-Pionier halbiert, jede Stufe Raumfaltung −15 %. */
    fun timerMultiplier(state: GameState): Double =
        (if (state.owns(StoreProduct.GALAXY_PIONEER)) PIONEER_TIMER_MULT else 1.0) *
            SPACE_FOLD_FACTOR.pow(state.level(Upgrade.SPACE_FOLD))

    fun unlockMillis(state: GameState, kind: GalaxyKind): Long =
        (kind.unlockHours * HOUR_MS * timerMultiplier(state)).toLong()


    /** Kristalle, um einen Timer mit [remainingMs] Restzeit sofort abzuschließen. */
    fun timerSkipCost(remainingMs: Long): Int =
        ceil(remainingMs / TIMER_SKIP_MS_PER_CRYSTAL).toInt().coerceIn(1, MAX_TIMER_SKIP_CRYSTALS)

    /** Dunkle Materie für die nächste Brücke: 50, 500, 5.000 … */
    fun bridgeCost(state: GameState): Double = (BRIDGE_BASE_COST * BRIDGE_COST_GROWTH.pow(state.bridges.size)).capped()

    fun bridgeMillis(state: GameState): Long =
        (BRIDGE_HOURS[min(state.bridges.size, BRIDGE_HOURS.lastIndex)] * HOUR_MS * timerMultiplier(state)).toLong()

    /** Anteil des eigenen Einkommens der Quelle, der über eine Brücke fließt. */
    fun bridgeShare(state: GameState): Double = BRIDGE_BASE_SHARE + BRIDGE_SHARE_PER_CRAFT * state.level(Upgrade.BRIDGE_CRAFT)

    /** Obergrenze einer Brücke, gemessen am eigenen Einkommen des Ziels. */
    fun bridgeCap(state: GameState): Double = BRIDGE_BASE_CAP + BRIDGE_CAP_PER_CRAFT * state.level(Upgrade.BRIDGE_CRAFT)

    /**
     * Staub, der über [bridge] ins Ziel fließt, bei den eigenen Einkommen [incomes] je Galaxie (ohne Brückenstaub,
     * damit sich Kreisläufe nicht aufschaukeln). Umgerechnet zum Kurs K(Ziel)/K(Quelle), gedeckelt am eigenen
     * Einkommen des Ziels und mal dem Brückenfaktor seines Naturgesetzes.
     */
    fun bridgeFlow(state: GameState, bridge: StarBridge, incomes: Map<GalaxyKind, Double>): Double {
        val targetLaw = (if (bridge.to == state.activeGalaxy) state.law else state.parked[bridge.to]?.law) ?: return 0.0
        val source = incomes[bridge.from] ?: 0.0
        val target = incomes[bridge.to] ?: 0.0
        val shared = bridgeShare(state) * source * bridge.to.costScale / bridge.from.costScale
        return (min(shared, bridgeCap(state) * target) * targetLaw.bridgeMult).capped()
    }

    // ---------------------------------------------------------------- Exklusive Sternarten

    /** Eiskristall: je Drehlage um das Zentrum, auf der ebenfalls ein Stern steht. */
    const val FROST_SYMMETRY_BONUS = 0.30
    /** Glutstern: je weiterem Glutstern im zusammenhängenden Nest … */
    const val EMBER_GROUP_BONUS = 0.35
    /** … aber höchstens für so viele weitere. */
    const val EMBER_GROUP_CAP = 11
    /** Mehr Elemente aus der Supernova eines Glutsterns je weiterem Nestmitglied. */
    const val EMBER_SUPERNOVA_PER_MEMBER = 0.25
    /** Polarlichtstern: je verschiedener Sternart unter den Nachbarn. */
    const val AURORA_VARIETY_BONUS = 0.35
    /** Schattenstern: je angrenzendem Feld, das nicht zum Garten gehört. */
    const val SHADOW_EDGE_BONUS = 0.60
}
