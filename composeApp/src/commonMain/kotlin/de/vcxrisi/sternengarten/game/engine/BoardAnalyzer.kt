package de.vcxrisi.sternengarten.game.engine

import de.vcxrisi.sternengarten.game.model.ConstellationKind
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
                    if (target in stars) pulsarAura[target] = (pulsarAura[target] ?: 0.0) + Balance.PULSAR_AURA * law.auraMult
                }
            }
        }

        val global = Balance.globalMultiplier(state)
        val raw = HashMap<Hex, StarBreakdown>()
        for ((hex, star) in stars) {
            val neighbors = hex.neighbors()
            var aura = 0.0
            var occupied = 0
            var hasBinaryNeighbor = false
            for (n in neighbors) {
                val other = stars[n] ?: continue
                occupied++
                if (other.type == StarType.YELLOW_STAR) {
                    val strength = if (other.whiteDwarf) 0.5 else 1.0
                    aura += Balance.YELLOW_AURA * strength * law.auraMult * law.yellowAuraMult
                }
                if (other.type == StarType.BINARY) hasBinaryNeighbor = true
            }
            aura += pulsarAura[hex] ?: 0.0

            val crowding = if (star.type == StarType.BLUE_GIANT && law.blueGiantPenalty) {
                (1.0 - Balance.BLUE_GIANT_CROWDING * occupied).coerceAtLeast(0.1)
            } else 1.0
            val pair = if (star.type == StarType.BINARY && hasBinaryNeighbor) Balance.BINARY_PAIR_MULT else 1.0
            val enrichment = state.enrichment[hex] ?: 0.0
            val constellation = (kindsByHex[hex] ?: emptySet()).sumOf { it.memberBonus } * law.constellationMult
            val base = star.type.baseOutput * Balance.levelMultiplier(star.level)
            val phase = Balance.phaseMultiplier(state, star)

            val rate = base * phase * (1.0 + aura) * crowding * pair * (1.0 + enrichment) * (1.0 + constellation) * global
            raw[hex] = StarBreakdown(base, phase, aura, crowding, pair, enrichment, constellation, 0.0, rate)
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
        // Sternbilder um ein Schwarzes Loch verstärken seinen Sog.
        for ((h, amount) in inflow.entries.toList()) {
            val bonus = raw[h]?.constellation ?: 0.0
            inflow[h] = amount * (1.0 + bonus)
        }

        return BoardAnalysis(
            breakdown = result,
            blackHoleInflow = inflow,
            totalRate = result.values.sumOf { it.rate },
            constellations = constellations,
            activeKinds = constellations.mapTo(mutableSetOf()) { it.kind },
            globalMultiplier = global,
        )
    }

    fun findConstellations(state: GameState): List<ConstellationInstance> {
        val stars = state.stars
        val found = ArrayList<ConstellationInstance>()
        val seenTriangles = HashSet<Set<Hex>>()

        for ((hex, star) in stars) {
            // Linien: jedes Fenster wird genau einmal vom Startfeld aus gezählt.
            for (dir in Hex.AXES) {
                val line = (0 until 4).map { hex + dir * it }
                val types = line.map { stars[it]?.type }
                if (types[0] != null && types[1] != null && types[2] != null) {
                    found += ConstellationInstance(ConstellationKind.TRIO, line.take(3))
                }
                if (types.all { it != null }) {
                    if (types.all { it == StarType.RED_DWARF }) {
                        found += ConstellationInstance(ConstellationKind.RED_THREAD, line)
                    }
                    if (types.toSet().size == 4) {
                        found += ConstellationInstance(ConstellationKind.RAINBOW, line)
                    }
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
            }
        }
        return found
    }
}
