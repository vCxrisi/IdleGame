package de.vcxrisi.sternengarten.game.save

import com.russhwolf.settings.Settings
import de.vcxrisi.sternengarten.game.model.GameState
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Speichert den Spielstand als JSON in den plattformeigenen Einstellungen (SharedPreferences / NSUserDefaults). */
class SaveRepository(private val settings: Settings = Settings()) {

    private val json = Json {
        ignoreUnknownKeys = true
        allowStructuredMapKeys = true
        allowSpecialFloatingPointValues = true
        encodeDefaults = true
    }

    fun load(): GameState? = settings.getStringOrNull(KEY)?.let { raw ->
        runCatching { json.decodeFromString(GameState.serializer(), raw) }.getOrNull()
    }

    fun save(state: GameState) {
        settings.putString(KEY, json.encodeToString(GameState.serializer(), state))
    }

    fun clear() = settings.remove(KEY)

    private companion object {
        const val KEY = "sternengarten.save.v1"
    }
}

@OptIn(ExperimentalTime::class)
fun nowEpochMillis(): Long = Clock.System.now().toEpochMilliseconds()
