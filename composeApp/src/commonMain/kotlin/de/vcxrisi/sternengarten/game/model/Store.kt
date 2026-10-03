package de.vcxrisi.sternengarten.game.model

/**
 * Echtgeld-Produkte. Die IDs müssen in der Google Play Console und in App Store Connect
 * genau so angelegt werden. Preise kommen live aus dem Store, [fallbackPrice] dient nur zur Anzeige,
 * solange der Store noch lädt.
 */
enum class StoreProduct(
    val productId: String,
    val displayName: String,
    val description: String,
    val consumable: Boolean,
    val fallbackPrice: String,
    val crystals: Int = 0,
    val capsules: Int = 0,
) {
    CRYSTALS_S(
        "de.vcxrisi.sternengarten.crystals_100", "Handvoll Kristalle", "100 Sternenkristalle",
        consumable = true, fallbackPrice = "0,99 €", crystals = 100,
    ),
    CRYSTALS_M(
        "de.vcxrisi.sternengarten.crystals_550", "Kristallbeutel", "550 Sternenkristalle (+10 % Bonus)",
        consumable = true, fallbackPrice = "4,99 €", crystals = 550,
    ),
    CRYSTALS_L(
        "de.vcxrisi.sternengarten.crystals_1200", "Kristallkiste", "1.200 Sternenkristalle (+20 % Bonus)",
        consumable = true, fallbackPrice = "9,99 €", crystals = 1_200,
    ),
    CRYSTALS_XL(
        "de.vcxrisi.sternengarten.crystals_3500", "Kristallhort", "3.500 Sternenkristalle (+40 % Bonus)",
        consumable = true, fallbackPrice = "24,99 €", crystals = 3_500,
    ),
    STARTER_PACK(
        "de.vcxrisi.sternengarten.starter_pack", "Starterpaket",
        "300 Kristalle, Nebel „Königsgold“ und 3 Artefakt-Kapseln – einmalig",
        consumable = false, fallbackPrice = "2,99 €", crystals = 300, capsules = 3,
    ),
    WANDERER_PASS(
        "de.vcxrisi.sternengarten.wanderer_pass", "Sternenwanderer",
        "Für immer: ×2 Produktion, +4 h Offline-Zeit und Kometen werden automatisch gefangen",
        consumable = false, fallbackPrice = "7,99 €",
    ),
    ;

    companion object {
        fun byId(productId: String): StoreProduct? = entries.firstOrNull { it.productId == productId }
    }
}

/** Was es für Sternenkristalle im Spiel zu kaufen gibt. */
enum class CrystalOffer(val displayName: String, val description: String, val price: Int) {
    CAPSULE("Artefakt-Kapsel", "Enthält ein zufälliges Artefakt", 100),
    CAPSULE_BUNDLE("5 Artefakt-Kapseln", "Spare 50 Kristalle", 450),
    WARP_1H("Zeitsprung 1 h", "Erhalte sofort eine Stunde Produktion", 40),
    WARP_8H("Zeitsprung 8 h", "Erhalte sofort acht Stunden Produktion", 250),
    BOOST("Kometenrausch", "×5 Produktion für 5 Minuten", 60),
}
