package de.vcxrisi.sternengarten.game.engine

import de.vcxrisi.sternengarten.game.model.ConstellationKind
import de.vcxrisi.sternengarten.game.model.CosmicEvent
import de.vcxrisi.sternengarten.game.model.GameState
import de.vcxrisi.sternengarten.game.model.Hex
import de.vcxrisi.sternengarten.game.model.StarType

data class ConstellationInstance(val kind: ConstellationKind, val members: List<Hex>)

/** Aufschlüsselung der Produktion eines Sterns – für das Info-Panel. */
data class StarBreakdown(
    val base: Double,
    val phase: Double,
    val aura: Double,
    val crowding: Double,
    val pair: Double,
    val enrichment: Double,
    val constellation: Double,
    /** Anteil, der an benachbarte Schwarze Löcher abfließt (0..1). */
    val drained: Double,
    /** Sternenstaub/s, der tatsächlich beim Spieler ankommt (ohne Online/Offline-Faktor). */
    val rate: Double,
    /** Zusätzliche Stufen durch angrenzende Neutronensterne. */
    val levelBonus: Int = 0,
)

data class BoardAnalysis(
    val breakdown: Map<Hex, StarBreakdown>,
    val blackHoleInflow: Map<Hex, Double>,
    val totalRate: Double,
    val constellations: List<ConstellationInstance>,
    val activeKinds: Set<ConstellationKind>,
    val globalMultiplier: Double,
)

object BoardAnalyzer {

    fun analyze(state: GameState): BoardAnalysis {
        val stars = state.stars
        val law = state.law
        val auraMult = Balance.auraMultiplier(state)
        val constellationMult = Balance.constellationMultiplier(state)
        val event = state.eventKind
        val constellations = findConstellations(state)
        val kindsByHex = HashMap<Hex, MutableSet<ConstellationKind>>()
        for (c in constellations) for (h in c.members) kindsByHex.getOrPut(h) { mutableSetOf() }.add(c.kind)

        // Pulsar-Strahlen vorab sammeln.
        val pulsarAura = HashMap<Hex, Double>()
        for ((hex, star) in stars) {
            if (star.type != StarType.PULSAR) continue
            for (dir in Hex.DIRECTIONS) {
                for (k in 1..Balance.PULSAR_RANGE) {
                    val target = hex + dir * k
                    if (target in stars) pulsarAura[target] = (pulsarAura[target] ?: 0.0) + Balance.PULSAR_AURA * auraMult
                }
            }
        }
        // Magnetare wirken auf den Ring im Abstand zwei.
        for ((hex, star) in stars) {
            if (star.type != StarType.MAGNETAR) continue
            for (target in ringAt(hex, 2)) {
                if (target in stars) pulsarAura[target] = (pulsarAura[target] ?: 0.0) + Balance.MAGNETAR_AURA * auraMult
            }
        }
        // Quasare heben die ganze Galaxie – je mehr Sterne, desto stärker.
        val quasars = stars.values.count { it.type == StarType.QUASAR }
        val quasarMult = 1.0 + Balance.QUASAR_PER_STAR * stars.size * quasars

        val global = Balance.globalMultiplier(state)
        val raw = HashMap<Hex, StarBreakdown>()
        for ((hex, star) in stars) {
            val neighbors = hex.neighbors()
            var aura = 0.0
            var occupied = 0
            var hasBinaryNeighbor = false
            var levelBonus = 0
            for (n in neighbors) {
                val other = stars[n] ?: continue
                occupied++
                when (other.type) {
                    StarType.YELLOW_STAR -> {
                        val strength = if (other.whiteDwarf) 0.5 else 1.0
                        aura += Balance.YELLOW_AURA * strength * auraMult * law.yellowAuraMult
                    }
                    StarType.BINARY -> hasBinaryNeighbor = true
                    StarType.NEUTRON_STAR -> levelBonus += Balance.NEUTRON_LEVEL_BONUS
                    StarType.NEBULA_NURSERY -> aura += Balance.NURSERY_AURA * auraMult
                    else -> Unit
                }
            }
            aura += pulsarAura[hex] ?: 0.0

            val crowding = if (star.type == StarType.BLUE_GIANT && law.blueGiantPenalty) {
                (1.0 - Balance.BLUE_GIANT_CROWDING * occupied).coerceAtLeast(0.1)
            } else 1.0
            val pair = if (star.type == StarType.BINARY && hasBinaryNeighbor) Balance.BINARY_PAIR_MULT else 1.0
            val enrichment = state.enrichment[hex] ?: 0.0
            val constellation = (kindsByHex[hex] ?: emptySet()).sumOf { it.memberBonus } * constellationMult
            val base = star.type.baseOutput * Balance.levelMultiplier(star.level + levelBonus)
            val phase = Balance.phaseMultiplier(state, star)
            val storm = if (event == CosmicEvent.SOLAR_STORM &&
                (star.type == StarType.YELLOW_STAR || star.type == StarType.BLUE_GIANT)
            ) Balance.SOLAR_STORM_MULT else 1.0

            // Sofort begrenzen: Ein unendlicher Wert würde beim Abzug durch Schwarze Löcher zu NaN (∞ − ∞).
            val rate = (
                base * phase * (1.0 + aura) * crowding * pair * (1.0 + enrichment) * (1.0 + constellation) *
                    storm * quasarMult * global
                ).capped()
            raw[hex] = StarBreakdown(base, phase, aura, crowding, pair, enrichment, constellation, 0.0, rate, levelBonus)
        }

        // Schwarze Löcher zweigen die Hälfte der Nachbarproduktion ab.
        val inflow = HashMap<Hex, Double>()
        val result = HashMap<Hex, StarBreakdown>(raw.size)
        for ((hex, b) in raw) {
            val star = stars.getValue(hex)
            if (star.type == StarType.BLACK_HOLE) {
                result[hex] = b
                continue
            }
            val holes = hex.neighbors().filter { stars[it]?.type == StarType.BLACK_HOLE }
            if (holes.isEmpty()) {
                result[hex] = b
                continue
            }
            val drainedAmount = b.rate * Balance.BLACK_HOLE_SHARE
            for (h in holes) inflow[h] = (inflow[h] ?: 0.0) + drainedAmount / holes.size
            result[hex] = b.copy(drained = Balance.BLACK_HOLE_SHARE, rate = b.rate - drainedAmount)
        }
        // Sternbilder um ein Schwarzes Loch verstärken seinen Sog, die Dunkle Flut verdreifacht ihn.
        val tide = if (event == CosmicEvent.DARK_TIDE) Balance.DARK_TIDE_MULT else 1.0
        for ((h, amount) in inflow.entries.toList()) {
            val bonus = raw[h]?.constellation ?: 0.0
            inflow[h] = (amount * (1.0 + bonus) * tide).capped()
        }

        return BoardAnalysis(
            breakdown = result,
            blackHoleInflow = inflow,
            totalRate = result.values.sumOf { it.rate }.capped(),
            constellations = constellations,
            activeKinds = constellations.mapTo(mutableSetOf()) { it.kind },
            globalMultiplier = (global * quasarMult).capped(),
        )
    }

    /** Alle Felder mit genau dem Abstand [radius] um [center]. */
    fun ringAt(center: Hex, radius: Int): List<Hex> = Hex.ring(radius).map { center + it }

    fun findConstellations(state: GameState): List<ConstellationInstance> {
        val stars = state.stars
        val found = ArrayList<ConstellationInstance>()
        val seenTriangles = HashSet<Set<Hex>>()

        for ((hex, star) in stars) {
            // Linien: jedes Fenster wird genau einmal vom Startfeld aus gezählt.
            for (dir in Hex.AXES) {
                val line = (0 until 5).map { hex + dir * it }
                val types = line.map { stars[it]?.type }
                if (types[0] != null && types[1] != null && types[2] != null) {
                    found += ConstellationInstance(ConstellationKind.TRIO, line.take(3))
                }
                val four = types.take(4)
                if (four.all { it != null }) {
                    if (four.all { it == StarType.RED_DWARF }) {
                        found += ConstellationInstance(ConstellationKind.RED_THREAD, line.take(4))
                    }
                    if (four.toSet().size == 4) {
                        found += ConstellationInstance(ConstellationKind.RAINBOW, line.take(4))
                    }
                }
                if (types.all { it != null }) {
                    found += ConstellationInstance(ConstellationKind.LADDER, line)
                }
                if (star.type == StarType.NEUTRON_STAR && types[1] == StarType.NEUTRON_STAR) {
                    found += ConstellationInstance(ConstellationKind.KILONOVA, line.take(2))
                }
            }

            // Dreiecke aus drei sich berührenden Sternen.
            for (i in 0 until 6) {
                val a = hex + Hex.DIRECTIONS[i]
                val b = hex + Hex.DIRECTIONS[(i + 1) % 6]
                if (a in stars && b in stars) {
                    val key = setOf(hex, a, b)
                    if (seenTriangles.add(key)) {
                        found += ConstellationInstance(ConstellationKind.TRIANGULUM, listOf(hex, a, b))
                    }
                }
            }

            // Kronen: vollständig umringte Sterne.
            val ring = hex.neighbors()
            if (ring.all { it in stars }) {
                val members = listOf(hex) + ring
                found += ConstellationInstance(ConstellationKind.CROWN, members)
                if (star.type == StarType.YELLOW_STAR) found += ConstellationInstance(ConstellationKind.SUN_CROWN, members)
                if (star.type == StarType.BLACK_HOLE) found += ConstellationInstance(ConstellationKind.EVENT_HORIZON, members)
                if (star.type == StarType.QUASAR) found += ConstellationInstance(ConstellationKind.QUASAR_THRONE, members)
            }
        }
        return found
    }
}
