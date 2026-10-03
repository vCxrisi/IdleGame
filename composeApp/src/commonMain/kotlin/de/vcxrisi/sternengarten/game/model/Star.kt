package de.vcxrisi.sternengarten.game.model

import kotlinx.serialization.Serializable

@Serializable
data class Star(
    val type: StarType,
    val level: Int = 1,
    /** Alter in Sekunden (bereits mit dem Alterungsfaktor der Galaxie verrechnet). */
    val age: Double = 0.0,
    val whiteDwarf: Boolean = false,
    /** Nur für Schwarze Löcher: verschlungener Sternenstaub. */
    val stored: Double = 0.0,
)

enum class LifePhase(val displayName: String) {
    PROTO("Protostern"),
    MAIN("Hauptreihe"),
    GIANT("Riesenphase"),
    WHITE_DWARF("Weißer Zwerg"),
}
