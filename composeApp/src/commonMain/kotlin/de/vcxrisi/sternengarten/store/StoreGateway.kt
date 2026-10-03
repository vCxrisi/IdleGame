package de.vcxrisi.sternengarten.store

import de.vcxrisi.sternengarten.game.model.StoreProduct

/** Ein Produkt, wie der Store es anbietet – mit lokalisiertem Preis. */
data class StoreOffer(val productId: String, val title: String, val price: String)

/** Rückmeldungen des Stores an das Spiel. Wird immer auf dem Haupt-Thread aufgerufen. */
interface StoreListener {
    fun onProductsLoaded(offers: List<StoreOffer>)

    /**
     * Ein Kauf ist bezahlt und verifiziert. Das Spiel schreibt ihn gut, speichert und ruft danach
     * [StoreGateway.finish] auf. [restored] markiert bereits früher abgeschlossene dauerhafte Käufe.
     */
    fun onPurchaseSucceeded(productId: String, transactionId: String, restored: Boolean)

    fun onPurchasePending(productId: String)

    fun onPurchaseFailed(productId: String, message: String)
}

/**
 * Plattformneutrale Schnittstelle zum App-Store. Android nutzt Google Play Billing,
 * iOS StoreKit 2 (in Swift implementiert, siehe `iosApp/iosApp/AppStoreGateway.swift`).
 */
interface StoreGateway {
    /** Anzeigename des Stores, z. B. "Google Play". */
    val name: String

    fun start(listener: StoreListener)

    fun purchase(productId: String)

    /** Schließt eine Transaktion nach der Gutschrift ab (verbrauchen bzw. bestätigen). */
    fun finish(transactionId: String, consumable: Boolean)

    fun restorePurchases()

    fun stop()
}

/** Hilfen für die Swift-Seite, die Kotlin-Enums nur umständlich lesen kann. */
object StoreCatalog {
    val productIds: List<String> = StoreProduct.entries.map { it.productId }

    fun isConsumable(productId: String): Boolean = StoreProduct.byId(productId)?.consumable ?: true
}

/**
 * Test-Store für Desktop und Entwicklung: Käufe gelingen sofort und kosten nichts.
 * Wird in den mobilen Apps nicht verwendet.
 */
class DebugStoreGateway : StoreGateway {
    override val name: String = "Test-Store"
    private var listener: StoreListener? = null
    private var counter = 0

    override fun start(listener: StoreListener) {
        this.listener = listener
        listener.onProductsLoaded(StoreProduct.entries.map { StoreOffer(it.productId, it.displayName, it.fallbackPrice) })
    }

    override fun purchase(productId: String) {
        listener?.onPurchaseSucceeded(productId, "debug-${counter++}-$productId", restored = false)
    }

    override fun finish(transactionId: String, consumable: Boolean) = Unit

    override fun restorePurchases() = Unit

    override fun stop() {
        listener = null
    }
}
