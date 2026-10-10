package de.vcxrisi.sternengarten.game.engine

import de.vcxrisi.sternengarten.game.model.Achievement
import de.vcxrisi.sternengarten.game.model.Artifact
import de.vcxrisi.sternengarten.game.model.ConstellationKind
import de.vcxrisi.sternengarten.game.model.CosmicEvent
import de.vcxrisi.sternengarten.game.model.GalaxyKind
import de.vcxrisi.sternengarten.game.model.GameState
import de.vcxrisi.sternengarten.game.model.Hex
import de.vcxrisi.sternengarten.game.model.StarBridge
import de.vcxrisi.sternengarten.game.model.StarType
import de.vcxrisi.sternengarten.game.model.StoreProduct

/** Ereignisse, auf die die Oberfläche mit Effekten und Meldungen reagiert. */
sealed interface GameEvent {
    data class Planted(val hex: Hex, val type: StarType) : GameEvent
    data class LeveledUp(val hex: Hex, val level: Int, val gained: Int = 1) : GameEvent
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

    /** Ereignis einer geparkten Galaxie – die Oberfläche zeigt dafür weder Meldung noch Effekt. */
    data class InBackground(val galaxy: GalaxyKind, val event: GameEvent) : GameEvent
    data class FieldBought(val hex: Hex, val cost: Double) : GameEvent
    data class GalaxyUnlockStarted(val kind: GalaxyKind, val readyAtMs: Long) : GameEvent
    data class GalaxyUnlocked(val kind: GalaxyKind) : GameEvent
    data class BridgeStarted(val bridge: StarBridge) : GameEvent
    data class BridgeCompleted(val bridge: StarBridge) : GameEvent
    /** Ein Timer wurde für [crystals] Kristalle sofort abgeschlossen. */
    data class TimerSkipped(val crystals: Int) : GameEvent
}

data class TickResult(
    val state: GameState,
    val events: List<GameEvent>,
    /** Staub aus der eigenen Produktion dieses Ticks, ohne Kometenrausch – Grundlage für Sternenbrücken. */
    val ownIncome: Double = 0.0,
)

/** Offline-Ertrag einer Galaxie. [bridged]: Staub, der über Sternenbrücken hereinkam. */
data class GalaxyOfflineLine(
    val kind: GalaxyKind,
    val name: String,
    val stardust: Double,
    val elements: Double,
    val supernovas: Int,
    val bridged: Double,
    /** Wartet auf die Wahl ihrer Naturgesetze und stand still. */
    val paused: Boolean = false,
)

/** Was während der Abwesenheit geschah. [stardust], [elements] und [supernovas] gelten für die aktive Galaxie. */
data class OfflineReport(
    val seconds: Double,
    val cappedSeconds: Double,
    val stardust: Double,
    val elements: Double,
    val supernovas: Int,
    /** Eine Zeile je Galaxie, sobald es mehr als eine gibt. */
    val galaxies: List<GalaxyOfflineLine> = emptyList(),
    val unlocked: List<GalaxyKind> = emptyList(),
    val bridgesCompleted: List<StarBridge> = emptyList(),
) {
    /** Übernimmt die Erschließungen und Brücken, die [GalaxyOrchestrator.completeTimers] fertiggestellt hat. */
    fun withTimers(events: List<GameEvent>): OfflineReport = copy(
        unlocked = unlocked + events.filterIsInstance<GameEvent.GalaxyUnlocked>().map { it.kind },
        bridgesCompleted = bridgesCompleted + events.filterIsInstance<GameEvent.BridgeCompleted>().map { it.bridge },
    )
}
