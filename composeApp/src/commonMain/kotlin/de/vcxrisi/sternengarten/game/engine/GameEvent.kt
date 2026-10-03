package de.vcxrisi.sternengarten.game.engine

import de.vcxrisi.sternengarten.game.model.Achievement
import de.vcxrisi.sternengarten.game.model.Artifact
import de.vcxrisi.sternengarten.game.model.ConstellationKind
import de.vcxrisi.sternengarten.game.model.CosmicEvent
import de.vcxrisi.sternengarten.game.model.GameState
import de.vcxrisi.sternengarten.game.model.Hex
import de.vcxrisi.sternengarten.game.model.StarType
import de.vcxrisi.sternengarten.game.model.StoreProduct

/** Ereignisse, auf die die Oberfläche mit Effekten und Meldungen reagiert. */
sealed interface GameEvent {
    data class Planted(val hex: Hex, val type: StarType) : GameEvent
    data class LeveledUp(val hex: Hex, val level: Int) : GameEvent
    data class Removed(val hex: Hex) : GameEvent
    data class Supernova(val hex: Hex, val elements: Double) : GameEvent
    data class BecameWhiteDwarf(val hex: Hex) : GameEvent
    data class Discovered(val kind: ConstellationKind) : GameEvent
    data class Unlocked(val type: StarType) : GameEvent
    data class BlackHoleReleased(val hex: Hex, val amount: Double) : GameEvent
    data class CometStardust(val amount: Double, val meteor: Boolean = false) : GameEvent
    data class CometBoost(val seconds: Double) : GameEvent
    data class EventStarted(val kind: CosmicEvent) : GameEvent
    data class EventEnded(val kind: CosmicEvent) : GameEvent
    data class AchievementUnlocked(val achievement: Achievement) : GameEvent
    data class CapsuleOpened(val artifact: Artifact, val level: Int, val refund: Int) : GameEvent
    data class PurchaseGranted(val product: StoreProduct, val restored: Boolean) : GameEvent
    data class TimeWarped(val amount: Double) : GameEvent
}

data class TickResult(
    val state: GameState,
    val events: List<GameEvent>,
)

data class OfflineReport(
    val seconds: Double,
    val cappedSeconds: Double,
    val stardust: Double,
    val elements: Double,
    val supernovas: Int,
)
