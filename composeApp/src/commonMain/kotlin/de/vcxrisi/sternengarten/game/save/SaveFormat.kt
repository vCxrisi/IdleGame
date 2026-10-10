package de.vcxrisi.sternengarten.game.save

import de.vcxrisi.sternengarten.game.model.GameState
import kotlinx.serialization.json.Json

/** Sicherung eines unlesbaren Spielstands: der Rohtext [raw] unter dem noch unbenutzten Schlüssel [key]. */
data class SaveBackup(val key: String, val raw: String)

/**
 * Ergebnis des Ladens. [state] ist `null`, wenn ein neues Spiel beginnt. Mit [backup] war der aktuelle Stand
 * unlesbar: Er wird gesichert, geladen wird der ältere v1-Stand, und der Spieler bekommt einen Hinweis.
 */
data class LoadResult(val state: GameState?, val backup: SaveBackup? = null) {
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
     * Wählt aus den gespeicherten Rohtexten [v3] und [v1] den Stand, der geladen wird:
     * - v3 lesbar: v3.
     * - v3 fehlt: v1, also der Stand vor dem Update (er wird danach migriert).
     * - v3 vorhanden, aber unlesbar: Der Rohtext kommt unter einen neuen Schlüssel, der noch nicht vergeben ist
     *   ([keyTaken]), damit der Autosave ihn nicht für immer überschreibt. Geladen wird v1 oder nichts.
     *
     * Rein: Sichern muss der Aufrufer, mit dem [LoadResult.backup] des Ergebnisses.
     */
    fun choose(v3: String?, v1: String?, nowMs: Long, keyTaken: (String) -> Boolean = { false }): LoadResult {
        if (v3 == null) return LoadResult(decodeOrNull(v1))
        decodeOrNull(v3)?.let { return LoadResult(it) }
        var stamp = nowMs
        while (keyTaken(unreadableKey(stamp))) stamp++
        return LoadResult(decodeOrNull(v1), SaveBackup(unreadableKey(stamp), v3))
    }
}
