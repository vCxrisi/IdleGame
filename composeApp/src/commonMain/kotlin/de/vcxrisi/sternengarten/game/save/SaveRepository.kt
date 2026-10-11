package de.vcxrisi.sternengarten.game.save

import com.russhwolf.settings.Settings
import de.vcxrisi.sternengarten.game.model.GameState
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Speichert den Spielstand als JSON in den plattformeigenen Einstellungen (SharedPreferences / NSUserDefaults).
 * Schlüssel und Format stehen in [SaveFormat].
 */
class SaveRepository(private val settings: Settings = Settings()) {

    /** Erst nach dem nächsten erfolgreichen Speichern zu löschen: die gerade wiederhergestellte Sicherung. */
    private var restoredBackupKey: String? = null

    /** Lädt den Stand; Sicherungen schreibt es sofort (siehe [SaveFormat.choose]). */
    fun load(): LoadResult {
        val result = SaveFormat.choose(
            v3 = settings.getStringOrNull(SaveFormat.KEY),
            v1 = settings.getStringOrNull(SaveFormat.LEGACY_KEY),
            nowMs = nowEpochMillis(),
            keyTaken = settings::hasKey,
            readBackup = settings::getStringOrNull,
        )
        result.backup?.let { settings.putString(it.key, it.raw) }
        restoredBackupKey = result.restoredBackupKey
        return result
    }

    /** Schreibt nur den v3-Schlüssel; der v1-Stand bleibt für ältere Builds unverändert liegen. */
    fun save(state: GameState) {
        settings.putString(SaveFormat.KEY, SaveFormat.encode(state))
        // Die wiederhergestellte Sicherung steckt jetzt im v3-Stand.
        restoredBackupKey?.let { settings.remove(it) }
        restoredBackupKey = null
    }

    /** Löscht den Spielstand samt v1-Stand. Sicherungen unlesbarer Stände bleiben erhalten. */
    fun clear() {
        settings.remove(SaveFormat.KEY)
        settings.remove(SaveFormat.LEGACY_KEY)
    }
}

@OptIn(ExperimentalTime::class)
fun nowEpochMillis(): Long = Clock.System.now().toEpochMilliseconds()
