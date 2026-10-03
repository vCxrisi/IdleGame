package de.vcxrisi.sternengarten

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import de.vcxrisi.sternengarten.store.DebugStoreGateway

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Sternengarten",
        state = rememberWindowState(width = 430.dp, height = 900.dp),
    ) {
        // Auf dem Desktop gibt es keinen echten Store: Käufe gelingen sofort und kostenlos.
        App(remember { DebugStoreGateway() })
    }
}
