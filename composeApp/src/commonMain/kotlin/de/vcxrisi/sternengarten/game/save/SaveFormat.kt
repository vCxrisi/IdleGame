package de.vcxrisi.sternengarten.game.save

import de.vcxrisi.sternengarten.game.model.GameState
import kotlinx.serialization.json.Json

/** Sicherung eines unlesbaren Spielstands: der Rohtext [raw] unter dem noch unbenutzten Schlüssel [key]. */
data class SaveBackup(val key: String, val raw: String)

/** Was beim Laden Besonderes passiert ist – dafür erscheint ein Hinweis. */
enum class LoadNotice {
    /** Der aktuelle Stand war unlesbar: Er ist gesichert, geladen wurde der Stand von vor dem Update (oder nichts). */
    UNREADABLE,

    /** Eine früher unlesbare Sicherung ist wieder lesbar und wurde geladen; der Notbehelf-Stand ist gesichert. */
    RESTORED,
}

/**
 * Ergebnis des Ladens. [state] ist `null`, wenn ein neues Spiel beginnt.
 * [backup]: Rohtext, den der Aufrufer vor allem anderen unter einem neuen Schlüssel sichern muss.
 * [restoredBackupKey]: Sicherung, aus der [state] stammt; sie darf erst nach dem nächsten erfolgreichen
 * Speichern gelöscht werden.
 */
data class LoadResult(
    val state: GameState?,
    val backup: SaveBackup? = null,
    val restoredBackupKey: String? = null,
    val notice: LoadNotice? = null,
) {
    /** Ob der aktuelle Spielstand nicht gelesen werden konnte. */
    val unreadable: Boolean get() = notice == LoadNotice.UNREADABLE
}

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
     * Wählt den Stand, der geladen wird. Die Gerätezeit entscheidet dabei nie – sie lässt sich verstellen:
     * - [v3] lesbar: v3. Der v1-Stand wird nie gelöscht; hat ein älterer Build dort weitergespielt, bleibt das liegen.
     * - v3 ist ein Notbehelf ([GameState.fallbackFromBackup]) und dessen Sicherung ist jetzt lesbar ([readBackup]):
     *   Die Sicherung ist der eigentliche Spielstand und wird geladen. Der Notbehelf kommt seinerseits unter einen
     *   neuen Schlüssel, damit auch dazwischen Gespieltes nicht verloren geht.
     * - v3 fehlt: [v1], also der Stand vor dem Update (er wird danach migriert).
     * - v3 vorhanden, aber unlesbar (etwa eine Enum-Konstante aus einem neueren Build): Der Rohtext kommt unter
     *   einen neuen Schlüssel, geladen wird v1 (oder nichts) – markiert als Notbehelf für genau diese Sicherung.
     *
     * Neue Schlüssel sind noch unbenutzt ([keyTaken]), damit nie eine Sicherung überschrieben wird.
     * Rein: Sichern und Entfernen erledigt der Aufrufer anhand des [LoadResult].
     */
    fun choose(
        v3: String?,
        v1: String?,
        nowMs: Long,
        keyTaken: (String) -> Boolean = { false },
        readBackup: (String) -> String? = { null },
    ): LoadResult {
        fun freeKey(): String {
            var stamp = nowMs
            while (keyTaken(unreadableKey(stamp))) stamp++
            return unreadableKey(stamp)
        }
        if (v3 == null) return LoadResult(decodeOrNull(v1))
        val current = decodeOrNull(v3)
        if (current == null) {
            val key = freeKey()
            return LoadResult(
                state = decodeOrNull(v1)?.copy(fallbackFromBackup = key),
                backup = SaveBackup(key, v3),
                notice = LoadNotice.UNREADABLE,
            )
        }
        val origin = current.fallbackFromBackup ?: return LoadResult(current)
        val rescued = decodeOrNull(readBackup(origin)) ?: return LoadResult(current)
        return LoadResult(
            state = rescued.copy(fallbackFromBackup = null),
            backup = SaveBackup(freeKey(), v3),
            restoredBackupKey = origin,
            notice = LoadNotice.RESTORED,
        )
    }
}
