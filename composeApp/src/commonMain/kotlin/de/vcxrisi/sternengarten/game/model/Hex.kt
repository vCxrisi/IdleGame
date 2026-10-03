package de.vcxrisi.sternengarten.game.model

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.serialization.Serializable

/** Feld im Hex-Raster in axialen Koordinaten (pointy-top). */
@Serializable
data class Hex(val q: Int, val r: Int) {
    val s: Int get() = -q - r

    operator fun plus(other: Hex) = Hex(q + other.q, r + other.r)
    operator fun minus(other: Hex) = Hex(q - other.q, r - other.r)
    operator fun times(k: Int) = Hex(q * k, r * k)

    fun neighbors(): List<Hex> = DIRECTIONS.map { this + it }

    fun distanceTo(other: Hex): Int =
        (abs(q - other.q) + abs(r - other.r) + abs(s - other.s)) / 2

    fun length(): Int = distanceTo(ORIGIN)

    companion object {
        val ORIGIN = Hex(0, 0)

        /** Die sechs Nachbarrichtungen, im Uhrzeigersinn benachbart. */
        val DIRECTIONS = listOf(
            Hex(1, 0), Hex(1, -1), Hex(0, -1),
            Hex(-1, 0), Hex(-1, 1), Hex(0, 1),
        )

        /** Je eine Richtung pro Achse – zum Abschreiten von Linien ohne Doppelzählung. */
        val AXES = DIRECTIONS.take(3)

        /** Alle Felder bis zum gegebenen Radius um den Ursprung. */
        fun area(radius: Int): List<Hex> = buildList {
            for (q in -radius..radius) {
                for (r in max(-radius, -q - radius)..min(radius, -q + radius)) add(Hex(q, r))
            }
        }

        /** Nur der äußere Ring mit genau diesem Radius. */
        fun ring(radius: Int): List<Hex> = area(radius).filter { it.length() == radius }
    }
}
