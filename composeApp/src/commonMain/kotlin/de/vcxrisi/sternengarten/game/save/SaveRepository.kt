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

    /** Lädt den zuletzt gespeicherten Stand; ein unlesbarer wird vorher gesichert (siehe [SaveFormat.choose]). */
    fun load(): LoadResult {
        val backups = settings.keys
            .filter { it.startsWith(SaveFormat.UNREADABLE_KEY_PREFIX) }
            .mapNotNull { key -> settings.getStringOrNull(key)?.let { key to it } }
            .toMap()
        val result = SaveFormat.choose(
            v3 = settings.getStringOrNull(SaveFormat.KEY),
            v1 = settings.getStringOrNull(SaveFormat.LEGACY_KEY),
            nowMs = nowEpochMillis(),
            keyTaken = settings::hasKey,
            backups = backups,
        )
        result.backup?.let { settings.putString(it.key, it.raw) }
        // Die wiederhergestellte Sicherung wird mit dem nächsten Speichern zum v3-Stand.
        result.restoredBackupKey?.let { settings.remove(it) }
        return result
    }

    /** Schreibt nur den v3-Schlüssel; der v1-Stand bleibt für ältere Builds unverändert liegen. */
    fun save(state: GameState) {
        settings.putString(SaveFormat.KEY, SaveFormat.encode(state))
    }

    /** Löscht den Spielstand samt v1-Stand. Sicherungen unlesbarer Stände bleiben erhalten. */
    fun clear() {
        settings.remove(SaveFormat.KEY)
        settings.remove(SaveFormat.LEGACY_KEY)
    }
}

@OptIn(ExperimentalTime::class)
fun nowEpochMillis(): Long = Clock.System.now().toEpochMilliseconds()
