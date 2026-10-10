package de.vcxrisi.sternengarten.game.save

import de.vcxrisi.sternengarten.game.model.GameState
import kotlinx.serialization.json.Json

/** Sicherung eines unlesbaren Spielstands: der Rohtext [raw] unter dem noch unbenutzten Schlüssel [key]. */
data class SaveBackup(val key: String, val raw: String)

/**
 * Ergebnis des Ladens. [state] ist `null`, wenn ein neues Spiel beginnt. Mit [backup] war der aktuelle Stand
 * unlesbar: Er wird gesichert, geladen wird der ältere v1-Stand, und der Spieler bekommt einen Hinweis.
 */
data class LoadResult(
    val state: GameState?,
    val backup: SaveBackup? = null,
    /** Schlüssel einer früheren Sicherung, aus der [state] stammt; sie wird nach dem Laden entfernt. */
    val restoredBackupKey: String? = null,
) {
    /** Ob der aktuelle Spielstand nicht gelesen werden konnte – dann erscheint der Hinweis-Dialog. */
    val unreadable: Boolean get() = backup != null
}

/**
 * Speicherformat: das JSON von [GameState] in den plattformeigenen Einstellungen.
 *
 * Seit den parallelen Galaxien steht der Stand unter [KEY]. Der frühere Schlüssel [LEGACY_KEY] wird nur noch
 * gelesen, nie mehr geschrieben: Ein älterer Build kennt die neuen Enum-Konstanten nicht, könnte den neuen Stand
 * also nicht dekodieren und würde ihn per Autosave überschreiben. So liest er stattdessen seinen alten Stand.
 */
object SaveFormat {
    const val KEY = "sternengarten.save.v3"
    const val LEGACY_KEY = "sternengarten.save.v1"

    /** Anfang der Schlüssel, unter denen unlesbare v3-Stände gesichert werden; dahinter steht die Wanduhrzeit in ms. */
    const val UNREADABLE_KEY_PREFIX = "sternengarten.save.v3.unreadable."

    val json = Json {
        ignoreUnknownKeys = true
        allowStructuredMapKeys = true
        allowSpecialFloatingPointValues = true
        encodeDefaults = true
    }

    fun encode(state: GameState): String = json.encodeToString(GameState.serializer(), state)

    /** `null`, wenn nichts gespeichert ist oder sich der Text nicht dekodieren lässt (z. B. unbekannte Enum-Konstante). */
    fun decodeOrNull(raw: String?): GameState? =
        raw?.let { runCatching { json.decodeFromString(GameState.serializer(), it) }.getOrNull() }

    fun unreadableKey(epochMs: Long): String = UNREADABLE_KEY_PREFIX + epochMs

    /**
     * Wählt aus den gespeicherten Rohtexten den Stand, der geladen wird – den zuletzt gespeicherten
     * (`lastSavedEpochMs`), bei Gleichstand v3 vor einer Sicherung vor v1:
     * - [v3]: der aktuelle Stand. Fehlt er, kommt der Stand von vor dem Update ([v1]) und wird danach migriert.
     * - [v1]: Hat ein älterer Build nach dem Update weitergespielt, ist v1 neuer als v3 und gewinnt.
     * - [backups]: frühere Sicherungen unlesbarer Stände (Schlüssel → Rohtext). Ist eine inzwischen lesbar
     *   und neuer als alles andere, wird sie wiederhergestellt ([LoadResult.restoredBackupKey]).
     * - v3 vorhanden, aber unlesbar (etwa eine Enum-Konstante aus einem neueren Build): Der Rohtext kommt unter
     *   einen neuen Schlüssel, der noch nicht vergeben ist ([keyTaken]), damit der Autosave ihn nicht für immer
     *   überschreibt; der Spieler bekommt einen Hinweis.
     *
     * Rein: Sichern und Entfernen muss der Aufrufer, mit [LoadResult.backup] und [LoadResult.restoredBackupKey].
     */
    fun choose(
        v3: String?,
        v1: String?,
        nowMs: Long,
        keyTaken: (String) -> Boolean = { false },
        backups: Map<String, String> = emptyMap(),
    ): LoadResult {
        val current = decodeOrNull(v3)
        var backup: SaveBackup? = null
        if (v3 != null && current == null) {
            var stamp = nowMs
            while (keyTaken(unreadableKey(stamp))) stamp++
            backup = SaveBackup(unreadableKey(stamp), v3)
        }
        // Reihenfolge = Vorrang bei Gleichstand.
        val candidates = ArrayList<Pair<String?, GameState>>()
        current?.let { candidates += null to it }
        for ((key, raw) in backups.entries.sortedBy { it.key }) decodeOrNull(raw)?.let { candidates += key to it }
        decodeOrNull(v1)?.let { candidates += LEGACY_KEY to it }
        var best: Pair<String?, GameState>? = null
        for (candidate in candidates) {
            if (best == null || candidate.second.lastSavedEpochMs > best.second.lastSavedEpochMs) best = candidate
        }
        val restored = best?.first?.takeIf { it != LEGACY_KEY }
        return LoadResult(best?.second, backup, restored)
    }
}
