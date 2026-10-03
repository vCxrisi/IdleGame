package de.vcxrisi.sternengarten.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.vcxrisi.sternengarten.game.engine.Balance
import de.vcxrisi.sternengarten.game.engine.BoardAnalysis
import de.vcxrisi.sternengarten.game.engine.BoardAnalyzer
import de.vcxrisi.sternengarten.game.engine.GameEngine
import de.vcxrisi.sternengarten.game.engine.GameEvent
import de.vcxrisi.sternengarten.game.engine.OfflineReport
import de.vcxrisi.sternengarten.game.model.GalaxyLaw
import de.vcxrisi.sternengarten.game.model.GameState
import de.vcxrisi.sternengarten.game.model.Hex
import de.vcxrisi.sternengarten.game.model.StarType
import de.vcxrisi.sternengarten.game.model.Upgrade
import de.vcxrisi.sternengarten.game.save.SaveRepository
import de.vcxrisi.sternengarten.game.save.nowEpochMillis
import de.vcxrisi.sternengarten.ui.theme.Palette
import de.vcxrisi.sternengarten.ui.theme.formatNumber
import androidx.compose.ui.graphics.Color

data class Toast(val id: Long, val title: String, val detail: String, val color: Color, val born: Float)

enum class Sheet { NONE, RESEARCH, CATALOG, BIG_BANG }

/**
 * Verbindet Engine, Speicherstand und Oberfläche. Der Zustand liegt in Compose-States,
 * damit die Oberfläche direkt darauf reagiert.
 */
class GameController(
    private val repository: SaveRepository = SaveRepository(),
    private val engine: GameEngine = GameEngine(),
) {
    var state by mutableStateOf(repository.load() ?: GameState(lastSavedEpochMs = nowEpochMillis()))
        private set
    var analysis: BoardAnalysis by mutableStateOf(BoardAnalyzer.analyze(state))
        private set
    var selectedType by mutableStateOf(StarType.RED_DWARF)
    var selectedHex by mutableStateOf<Hex?>(null)
    var sheet by mutableStateOf(Sheet.NONE)
    var offlineReport by mutableStateOf<OfflineReport?>(null)
    val toasts = mutableStateListOf<Toast>()

    /** Gemeinsame Animationsuhr in Sekunden – treibt alle Effekte an. */
    var clock by mutableFloatStateOf(0f)
        private set

    /** Wird von der Oberfläche gesetzt, um Effekte zu Ereignissen abzuspielen. */
    var onEvent: (GameEvent) -> Unit = {}

    private var accumulator = 0.0
    private var sinceSave = 0.0
    private var lastWallClock = nowEpochMillis()
    private var toastId = 0L

    init {
        catchUp(nowEpochMillis(), showReport = true)
    }

    // ------------------------------------------------------------ Spielschleife

    /**
     * Pro Frame aufgerufen. [dt] treibt nur die Animationen; die Simulation folgt der echten Uhr
     * in festen Schritten von 0,1 s, damit auch kurze Unterbrechungen nicht verloren gehen.
     */
    fun frame(dt: Float) {
        clock += dt
        val now = nowEpochMillis()
        val elapsedMs = (now - lastWallClock).coerceAtLeast(0)
        lastWallClock = now
        if (elapsedMs > BACKGROUND_GAP_MS) {
            // App war im Hintergrund oder das Gerät gesperrt: als Offline-Zeit nachholen.
            applyOffline(elapsedMs / 1000.0)
        } else {
            accumulator += elapsedMs / 1000.0
        }
        var changed = false
        while (accumulator >= STEP) {
            accumulator -= STEP
            val result = engine.tick(state, STEP)
            state = result.state
            result.events.forEach(::dispatch)
            changed = true
        }
        if (changed) {
            analysis = BoardAnalyzer.analyze(state)
            if (selectedHex?.let { it !in state.stars } == true) selectedHex = null
        }

        sinceSave += dt
        if (sinceSave >= AUTOSAVE_SECONDS) save()

        toasts.removeAll { clock - it.born > TOAST_SECONDS }
    }

    private fun catchUp(now: Long, showReport: Boolean) {
        val last = state.lastSavedEpochMs.takeIf { it > 0 } ?: now
        applyOffline((now - last) / 1000.0, showReport)
        lastWallClock = now
    }

    private fun applyOffline(seconds: Double, showReport: Boolean = true) {
        if (seconds >= MIN_OFFLINE_SECONDS) {
            val (next, report) = engine.applyOffline(state, seconds)
            state = next
            if (showReport && report.stardust > 0.0) offlineReport = report
        }
        state = state.copy(comet = null)
        analysis = BoardAnalyzer.analyze(state)
        accumulator = 0.0
        save()
    }

    fun save() {
        sinceSave = 0.0
        state = state.copy(lastSavedEpochMs = nowEpochMillis())
        repository.save(state)
    }

    // ------------------------------------------------------------ Eingaben

    fun tapCell(hex: Hex) {
        val star = state.stars[hex]
        when {
            star == null && hex.length() <= Balance.gardenRadius(state) -> plant(hex)
            star == null -> selectedHex = null
            star.type == StarType.BLACK_HOLE && star.stored > 0.0 && selectedHex == hex -> releaseBlackHole(hex)
            else -> selectedHex = if (selectedHex == hex) null else hex
        }
    }

    private fun plant(hex: Hex) {
        selectedHex = null
        val type = selectedType
        if (type !in state.unlocked) return
        val next = engine.plant(state, hex, type)
        if (next == null) {
            toast("Zu wenig Sternenstaub", "${type.displayName} kostet ${formatNumber(Balance.starCost(state, type))}", Palette.Danger)
            return
        }
        update(next)
        dispatch(GameEvent.Planted(hex, type))
    }

    fun levelUp(hex: Hex) {
        val next = engine.levelUp(state, hex) ?: return
        update(next)
        dispatch(GameEvent.LeveledUp(hex, next.stars.getValue(hex).level))
    }

    fun remove(hex: Hex) {
        val next = engine.remove(state, hex) ?: return
        selectedHex = null
        update(next)
        dispatch(GameEvent.Removed(hex))
    }

    fun releaseBlackHole(hex: Hex) {
        val (next, amount) = engine.releaseBlackHole(state, hex) ?: return
        update(next)
        dispatch(GameEvent.BlackHoleReleased(hex, amount))
    }

    fun buy(upgrade: Upgrade) {
        val next = engine.buyUpgrade(state, upgrade) ?: return
        update(next)
    }

    fun catchComet() {
        val (next, event) = engine.catchComet(state) ?: return
        update(next)
        dispatch(event)
    }

    fun bigBang() {
        val next = engine.bigBang(state) ?: return
        selectedHex = null
        sheet = Sheet.NONE
        update(next)
        save()
    }

    fun chooseGalaxy(law: GalaxyLaw) {
        val next = engine.chooseGalaxy(state, law) ?: return
        selectedType = StarType.RED_DWARF
        update(next)
        toast("Willkommen in ${next.galaxyName}", law.displayName, Palette.DarkMatter)
        save()
    }

    fun dismissOfflineReport() {
        offlineReport = null
    }

    private fun update(next: GameState) {
        state = next
        analysis = BoardAnalyzer.analyze(next)
    }

    // ------------------------------------------------------------ Meldungen

    private fun dispatch(event: GameEvent) {
        when (event) {
            is GameEvent.Supernova -> toast("Supernova!", "+${formatNumber(event.elements)} Elemente · Nachbarfelder gedüngt", Palette.Elements)
            is GameEvent.Discovered -> toast("Neues Sternbild: ${event.kind.displayName}", "Dauerhaft +10 % auf alles", Palette.Accent)
            is GameEvent.Unlocked -> toast("Neue Sternart: ${event.type.displayName}", event.type.description, Palette.Stardust)
            is GameEvent.BlackHoleReleased -> toast("Ereignishorizont geöffnet", "+${formatNumber(event.amount)} Sternenstaub", Palette.Boost)
            is GameEvent.CometStardust -> toast("Kometenregen", "+${formatNumber(event.amount)} Sternenstaub", Palette.Stardust)
            is GameEvent.CometBoost -> toast("Kometenrausch!", "×5 Produktion für ${event.seconds.toInt()} s", Palette.Boost)
            else -> Unit
        }
        onEvent(event)
    }

    private fun toast(title: String, detail: String, color: Color) {
        toasts.add(Toast(toastId++, title, detail, color, clock))
        while (toasts.size > 3) toasts.removeAt(0)
    }

    companion object {
        const val STEP = 0.1
        const val AUTOSAVE_SECONDS = 5.0
        const val TOAST_SECONDS = 3.5f
        const val TOAST_FADE = 0.4f
        const val MIN_OFFLINE_SECONDS = 30.0
        const val BACKGROUND_GAP_MS = 30_000L
    }
}
