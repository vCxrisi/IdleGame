package de.vcxrisi.sternengarten.store

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import de.vcxrisi.sternengarten.game.model.StoreProduct

/**
 * Google-Play-Anbindung (Play Billing Library 8). Alle Produkte sind "In-App-Produkte":
 * Kristalle werden nach der Gutschrift verbraucht, Starterpaket und Sternenwanderer bestätigt.
 *
 * Hinweis: Die Gutschrift erfolgt auf dem Gerät. Für ein großes Spiel sollte zusätzlich ein Server
 * die Kaufbelege über die Google Play Developer API prüfen.
 */
class PlayStoreGateway(private val activity: Activity) : StoreGateway, PurchasesUpdatedListener {

    override val name: String = "Google Play"

    private val main = Handler(Looper.getMainLooper())
    private var listener: StoreListener? = null
    private val details = HashMap<String, ProductDetails>()
    private val unfinished = HashMap<String, Purchase>()

    private val client: BillingClient = BillingClient.newBuilder(activity)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    override fun start(listener: StoreListener) {
        this.listener = listener
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingResponseCode.OK) {
                    queryProducts()
                    queryOwnedPurchases()
                } else {
                    Log.w(TAG, "Billing nicht verfügbar: ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                // Die Bibliothek verbindet sich dank enableAutoServiceReconnection() selbst neu.
            }
        })
    }

    private fun queryProducts() {
        val products = StoreProduct.entries.map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it.productId)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()
        client.queryProductDetailsAsync(params) { result, productDetailsResult ->
            if (result.responseCode != BillingResponseCode.OK) {
                Log.w(TAG, "Produkte konnten nicht geladen werden: ${result.debugMessage}")
                return@queryProductDetailsAsync
            }
            val loaded = productDetailsResult.productDetailsList
            val offers = loaded.map { product ->
                StoreOffer(
                    productId = product.productId,
                    title = product.name,
                    price = product.oneTimePurchaseOfferDetails?.formattedPrice ?: "",
                )
            }
            main.post {
                loaded.forEach { details[it.productId] = it }
                listener?.onProductsLoaded(offers)
            }
        }
    }

    override fun purchase(productId: String) {
        val product = details[productId]
        if (product == null) {
            listener?.onPurchaseFailed(productId, "Der Store ist gerade nicht erreichbar.")
            return
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product).build()),
            )
            .build()
        val result = client.launchBillingFlow(activity, params)
        if (result.responseCode != BillingResponseCode.OK) {
            listener?.onPurchaseFailed(productId, result.debugMessage)
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingResponseCode.OK -> purchases.orEmpty().forEach { handle(it) }
            BillingResponseCode.USER_CANCELED -> Unit
            BillingResponseCode.ITEM_ALREADY_OWNED -> queryOwnedPurchases()
            else -> main.post { listener?.onPurchaseFailed("", result.debugMessage) }
        }
    }

    private fun handle(purchase: Purchase) {
        val productId = purchase.products.firstOrNull() ?: return
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> main.post {
                unfinished[purchase.purchaseToken] = purchase
                // Bereits bestätigte dauerhafte Käufe sind Wiederherstellungen.
                listener?.onPurchaseSucceeded(productId, purchase.purchaseToken, restored = purchase.isAcknowledged)
            }
            Purchase.PurchaseState.PENDING -> main.post { listener?.onPurchasePending(productId) }
            else -> Unit
        }
    }

    override fun finish(transactionId: String, consumable: Boolean) {
        val purchase = unfinished.remove(transactionId) ?: return
        if (consumable) {
            val params = ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
            client.consumeAsync(params) { result, _ ->
                if (result.responseCode != BillingResponseCode.OK) Log.w(TAG, "Verbrauchen fehlgeschlagen: ${result.debugMessage}")
            }
        } else if (!purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
            client.acknowledgePurchase(params) { result ->
                if (result.responseCode != BillingResponseCode.OK) Log.w(TAG, "Bestätigen fehlgeschlagen: ${result.debugMessage}")
            }
        }
    }

    /** Liefert dauerhafte Käufe und noch nicht verbrauchte Kristall-Käufe erneut aus. */
    private fun queryOwnedPurchases() {
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingResponseCode.OK) purchases.forEach { handle(it) }
        }
    }

    override fun restorePurchases() = queryOwnedPurchases()

    override fun stop() {
        listener = null
        client.endConnection()
    }

    private companion object {
        const val TAG = "Sternengarten.Store"
    }
}
