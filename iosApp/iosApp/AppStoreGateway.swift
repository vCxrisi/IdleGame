import Foundation
import StoreKit
import ComposeApp

/// App-Store-Anbindung über StoreKit 2. Implementiert die Kotlin-Schnittstelle `StoreGateway`.
///
/// Nur verifizierte Transaktionen werden gutgeschrieben. Abgeschlossen (`finish()`) wird eine Transaktion
/// erst, wenn das Spiel sie gespeichert hat – so geht nach einem Absturz kein Kauf verloren.
final class AppStoreGateway: NSObject, StoreGateway {
    let name: String = "App Store"

    private var listener: StoreListener?
    private var products: [String: Product] = [:]
    private var unfinished: [String: Transaction] = [:]
    private var updatesTask: Task<Void, Never>?

    func start(listener: StoreListener) {
        self.listener = listener
        // Käufe, die außerhalb der App abgeschlossen werden (z. B. "Ask to Buy"), kommen hier an.
        updatesTask = Task { [weak self] in
            for await result in Transaction.updates {
                await self?.handle(result, restored: false)
            }
        }
        Task { @MainActor in
            await loadProducts()
            // Noch nicht abgeschlossene Käufe (z. B. nach einem Absturz) zuerst voll gutschreiben …
            for await result in Transaction.unfinished {
                handle(result, restored: false)
            }
            // … danach dauerhafte Käufe wiederherstellen.
            for await result in Transaction.currentEntitlements {
                handle(result, restored: true)
            }
        }
    }

    @MainActor
    private func loadProducts() async {
        do {
            let loaded = try await Product.products(for: StoreCatalog.shared.productIds)
            products = Dictionary(uniqueKeysWithValues: loaded.map { ($0.id, $0) })
            let offers = loaded.map { StoreOffer(productId: $0.id, title: $0.displayName, price: $0.displayPrice) }
            listener?.onProductsLoaded(offers: offers)
        } catch {
            print("Sternengarten.Store: Produkte konnten nicht geladen werden: \(error)")
        }
    }

    func purchase(productId: String) {
        Task { @MainActor in
            guard let product = products[productId] else {
                listener?.onPurchaseFailed(productId: productId, message: "Der Store ist gerade nicht erreichbar.")
                return
            }
            do {
                switch try await product.purchase() {
                case .success(let verification):
                    handle(verification, restored: false)
                case .pending:
                    listener?.onPurchasePending(productId: productId)
                case .userCancelled:
                    break
                @unknown default:
                    break
                }
            } catch {
                listener?.onPurchaseFailed(productId: productId, message: error.localizedDescription)
            }
        }
    }

    @MainActor
    private func handle(_ result: VerificationResult<Transaction>, restored: Bool) {
        guard case .verified(let transaction) = result, transaction.revocationDate == nil else { return }
        let id = String(transaction.id)
        unfinished[id] = transaction
        listener?.onPurchaseSucceeded(productId: transaction.productID, transactionId: id, restored: restored)
    }

    func finish(transactionId: String, consumable: Bool) {
        Task { @MainActor in
            guard let transaction = unfinished.removeValue(forKey: transactionId) else { return }
            await transaction.finish()
        }
    }

    func restorePurchases() {
        Task { @MainActor in
            try? await AppStore.sync()
            for await result in Transaction.currentEntitlements {
                handle(result, restored: true)
            }
        }
    }

    func stop() {
        updatesTask?.cancel()
        listener = nil
    }
}
