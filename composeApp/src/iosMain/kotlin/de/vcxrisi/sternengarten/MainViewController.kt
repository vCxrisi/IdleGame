package de.vcxrisi.sternengarten

import androidx.compose.ui.window.ComposeUIViewController
import de.vcxrisi.sternengarten.store.StoreGateway

/** Einstieg für SwiftUI. Der StoreKit-2-Store wird in Swift implementiert und hereingereicht. */
fun MainViewController(store: StoreGateway) = ComposeUIViewController { App(store) }
