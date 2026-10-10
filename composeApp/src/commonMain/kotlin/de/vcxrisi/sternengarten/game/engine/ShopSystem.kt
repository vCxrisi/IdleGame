package de.vcxrisi.sternengarten.game.engine

import de.vcxrisi.sternengarten.game.model.Artifact
import de.vcxrisi.sternengarten.game.model.CrystalOffer
import de.vcxrisi.sternengarten.game.model.GameState
import de.vcxrisi.sternengarten.game.model.NebulaTheme
import de.vcxrisi.sternengarten.game.model.Rarity
import de.vcxrisi.sternengarten.game.model.SparkStyle
import de.vcxrisi.sternengarten.game.model.StoreProduct
import kotlin.random.Random

/** Kristall-Shop, Artefakt-Kapseln, Kosmetik und die Gutschrift von Store-Käufen. */
class ShopSystem(private val random: Random) {

    // ------------------------------------------------------------ Kristall-Angebote

    /** Gibt den neuen Zustand und den Sternenstaub aus einem Zeitsprung zurück. */
    fun buyOffer(state: GameState, offer: CrystalOffer): Pair<GameState, Double>? {
        if (state.crystals < offer.price) return null
        val paid = state.copy(crystals = state.crystals - offer.price)
        return when (offer) {
            CrystalOffer.CAPSULE -> paid.copy(capsules = paid.capsules + 1) to 0.0
            CrystalOffer.CAPSULE_BUNDLE -> paid.copy(capsules = paid.capsules + 5) to 0.0
            CrystalOffer.WARP_1H -> warp(paid, 3600.0)
            CrystalOffer.WARP_8H -> warp(paid, 8 * 3600.0)
            CrystalOffer.BOOST -> paid.copy(boostRemaining = paid.boostRemaining + 300.0) to 0.0
        }
    }

    private fun warp(state: GameState, seconds: Double): Pair<GameState, Double> {
        val amount = (BoardAnalyzer.analyze(state).totalRate * state.law.onlineMult * seconds).capped()
        return state.copy(
            stardust = state.stardust + amount,
            runStardust = state.runStardust + amount,
            totalStardust = state.totalStardust + Balance.normalizedDust(state, amount),
        ) to amount
    }

    // ------------------------------------------------------------ Kapseln

    /** Wahrscheinlichkeit je Seltenheit – wird im Shop offen angezeigt. */
    fun dropChance(rarity: Rarity): Double = rarity.weight.toDouble() / Rarity.entries.sumOf { it.weight }

    fun openCapsule(state: GameState): Pair<GameState, GameEvent.CapsuleOpened>? {
        if (state.capsules <= 0) return null
        val rarity = rollRarity()
        val pool = Artifact.entries.filter { it.rarity == rarity }
        val artifact = pool[random.nextInt(pool.size)]
        val level = state.artifactLevel(artifact)
        val opened = state.copy(
            capsules = state.capsules - 1,
            stats = state.stats.copy(capsulesOpened = state.stats.capsulesOpened + 1),
        )
        return if (level >= Artifact.MAX_LEVEL) {
            opened.copy(crystals = opened.crystals + Balance.MAXED_ARTIFACT_REFUND) to
                GameEvent.CapsuleOpened(artifact, level, Balance.MAXED_ARTIFACT_REFUND)
        } else {
            opened.copy(artifacts = opened.artifacts + (artifact to level + 1)) to
                GameEvent.CapsuleOpened(artifact, level + 1, 0)
        }
    }

    private fun rollRarity(): Rarity {
        var roll = random.nextInt(Rarity.entries.sumOf { it.weight })
        for (rarity in Rarity.entries) {
            roll -= rarity.weight
            if (roll < 0) return rarity
        }
        return Rarity.COMMON
    }

    // ------------------------------------------------------------ Kosmetik

    fun buyTheme(state: GameState, theme: NebulaTheme): GameState? {
        if (theme in state.ownedThemes) return state.copy(activeTheme = theme)
        if (state.crystals < theme.price) return null
        return state.copy(
            crystals = state.crystals - theme.price,
            ownedThemes = state.ownedThemes + theme,
            activeTheme = theme,
        )
    }

    fun buySpark(state: GameState, spark: SparkStyle): GameState? {
        if (spark in state.ownedSparks) return state.copy(activeSpark = spark)
        if (state.crystals < spark.price) return null
        return state.copy(
            crystals = state.crystals - spark.price,
            ownedSparks = state.ownedSparks + spark,
            activeSpark = spark,
        )
    }

    // ------------------------------------------------------------ Store-Käufe

    /**
     * Schreibt einen Store-Kauf gut. Jede Transaktion zählt nur einmal. Wiederhergestellte dauerhafte Käufe
     * ([restored]) schalten nur die Berechtigung frei, ohne Kristalle erneut auszuzahlen.
     * Gibt `null` zurück, wenn nichts gutzuschreiben war.
     */
    fun grantPurchase(
        state: GameState,
        product: StoreProduct,
        transactionId: String,
        restored: Boolean = false,
    ): GameState? {
        if (transactionId in state.processedTransactions) return null
        val recorded = state.copy(processedTransactions = state.processedTransactions + transactionId)
        if (product.consumable) {
            return recorded.copy(crystals = recorded.crystals + product.crystals)
        }
        if (state.owns(product)) return null
        var next = recorded.copy(entitlements = recorded.entitlements + product.productId)
        if (!restored) {
            next = next.copy(crystals = next.crystals + product.crystals, capsules = next.capsules + product.capsules)
        }
        if (product == StoreProduct.STARTER_PACK) {
            next = next.copy(ownedThemes = next.ownedThemes + NebulaTheme.ROYAL_GOLD)
        }
        // Die zusätzlichen Startfelder gibt es sofort in jeder Galaxie – auch beim Wiederherstellen.
        if (product == StoreProduct.GALAXY_PIONEER) next = GalaxyFactory.extendStartingFields(next)
        return next
    }
}
