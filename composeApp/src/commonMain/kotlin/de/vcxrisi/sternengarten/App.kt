package de.vcxrisi.sternengarten

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import de.vcxrisi.sternengarten.store.StoreGateway
import de.vcxrisi.sternengarten.ui.GameController
import de.vcxrisi.sternengarten.ui.screens.GameScreen

@Composable
fun App(store: StoreGateway) {
    val controller = remember(store) { GameController(store = store) }
    DisposableEffect(controller) {
        controller.startStore()
        onDispose {
            controller.save()
            controller.stopStore()
        }
    }
    GameScreen(controller)
}
