package de.vcxrisi.sternengarten

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import de.vcxrisi.sternengarten.ui.GameController
import de.vcxrisi.sternengarten.ui.screens.GameScreen

@Composable
fun App() {
    val controller = remember { GameController() }
    DisposableEffect(controller) {
        onDispose { controller.save() }
    }
    GameScreen(controller)
}
