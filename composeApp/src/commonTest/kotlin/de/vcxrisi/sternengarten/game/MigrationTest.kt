package de.vcxrisi.sternengarten.game

import de.vcxrisi.sternengarten.game.engine.Balance
import de.vcxrisi.sternengarten.game.engine.isSafe
import de.vcxrisi.sternengarten.game.model.Achievement
import de.vcxrisi.sternengarten.game.model.Artifact
import de.vcxrisi.sternengarten.game.model.ConstellationKind
import de.vcxrisi.sternengarten.game.model.GalaxyLaw
import de.vcxrisi.sternengarten.game.model.GameState
import de.vcxrisi.sternengarten.game.model.Hex
import de.vcxrisi.sternengarten.game.model.NebulaTheme
import de.vcxrisi.sternengarten.game.model.Star
import de.vcxrisi.sternengarten.game.model.StarType
import de.vcxrisi.sternengarten.game.model.StoreProduct
import de.vcxrisi.sternengarten.game.model.Upgrade
import de.vcxrisi.sternengarten.game.save.SaveMigration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MigrationTest {

    /** Nachbau des Spielstands vom TestFlight-Screenshot (Galaxie 6, Große Leere). */
    private fun brokenTestFlightSave() = GameState(
        stardust = Double.NaN,
        darkMatter = 1.4e137,
        elements = Double.POSITIVE_INFINITY,
        runStardust = Double.POSITIVE_INFINITY,
        totalStardust = Double.POSITIVE_INFINITY,
        galaxyNumber = 6,
        galaxyName = "Kepler 1426",
        law = GalaxyLaw.GREAT_VOID,
        upgrades = mapOf(
            Upgrade.DARK_ENERGY to 287,
            Upgrade.DEEP_SLEEP to 2,
            Upgrade.STELLAR_WIND to 40,
            Upgrade.NEBULA_EXPANSION to 3,
        ),
        stars = mapOf(
            Hex.ORIGIN to Star(StarType.BLACK_HOLE, age = 10.0, stored = Double.POSITIVE_INFINITY),
            Hex(1, 0) to Star(StarType.RED_DWARF, level = 5001, age = 10.0),
        ),
        crystals = 640,
        capsules = 2,
        artifacts = mapOf(Artifact.SEXTANT to 3),
        achievements = setOf(Achievement.FIRST_STAR, Achievement.BIG_BANG_5),
        discovered = setOf(ConstellationKind.TRIO, ConstellationKind.CROWN),
        unlocked = StarType.entries.toSet(),
        ownedThemes = setOf(NebulaTheme.GALAXY, NebulaTheme.AURORA),
        entitlements = setOf(StoreProduct.STARTER_PACK.productId),
        processedTransactions = setOf("tx-1"),
    )

    @Test
    fun brokenSaveIsRepairedAndNormalized() {
        val before = brokenTestFlightSave()
        val (after, report) = SaveMigration.migrate(before)
        assertNotNull(report)

        // Normalisiert
        assertEquals(SaveMigration.NORMALIZED_DARK_MATTER_CAP, after.darkMatter)
        assertEquals(SaveMigration.NORMALIZED_DARK_ENERGY_CAP, after.level(Upgrade.DARK_ENERGY))
        assertEquals(287, report.darkEnergyBefore)
        assertTrue(report.galaxyRestarted)
        assertEquals(SaveMigration.CURRENT_BALANCE_VERSION, after.balanceVersion)

        // Galaxie neu begonnen, permanente Upgrades bleiben, Galaxie-Upgrades nicht
        assertTrue(after.stars.isEmpty())
        assertEquals(Balance.startingStardust(after), after.stardust)
        assertEquals(0.0, after.elements)
        assertEquals(0.0, after.runStardust)
        assertEquals(2, after.level(Upgrade.DEEP_SLEEP))
        assertEquals(0, after.level(Upgrade.STELLAR_WIND))
        assertEquals(6, after.galaxyNumber)
        assertEquals(GalaxyLaw.GREAT_VOID, after.law)

        // Meta-Fortschritt bleibt
        assertEquals(640, after.crystals)
        assertEquals(2, after.capsules)
        assertEquals(before.artifacts, after.artifacts)
        assertEquals(before.achievements, after.achievements)
        assertEquals(before.discovered, after.discovered)
        assertEquals(before.unlocked, after.unlocked)
        assertEquals(before.ownedThemes, after.ownedThemes)
        assertEquals(before.entitlements, after.entitlements)
        assertEquals(before.processedTransactions, after.processedTransactions)

        // Keine ungültigen Werte mehr
        listOf(after.stardust, after.darkMatter, after.elements, after.runStardust, after.totalStardust)
            .forEach { assertTrue(it.isSafe(), "$it") }
    }

    @Test
    fun migrationIsIdempotent() {
        val (once, _) = SaveMigration.migrate(brokenTestFlightSave())
        val (twice, report) = SaveMigration.migrate(once)
        assertNull(report)
        assertEquals(once, twice)
    }

    @Test
    fun healthyOldSaveOnlyGetsTheNewVersion() {
        val healthy = GameState(
            stardust = 12_345.0,
            darkMatter = 42.0,
            upgrades = mapOf(Upgrade.DARK_ENERGY to 2, Upgrade.STELLAR_WIND to 3),
            stars = mapOf(Hex.ORIGIN to Star(StarType.YELLOW_STAR, level = 30, age = 50.0)),
            crystals = 10,
        )
        val (after, report) = SaveMigration.migrate(healthy)
        assertNull(report)
        assertEquals(healthy.copy(balanceVersion = SaveMigration.CURRENT_BALANCE_VERSION), after)
    }

    @Test
    fun smallerDarkMatterIsConvertedWithoutCap() {
        // Nur Dunkle Energie entgleist: Dunkle Materie 1000 wird zu 1000^(2/3) = 100.
        val (after, report) = SaveMigration.migrate(GameState(darkMatter = 1000.0, upgrades = mapOf(Upgrade.DARK_ENERGY to 25)))
        assertNotNull(report)
        assertTrue(kotlin.math.abs(after.darkMatter - 100.0) < 1e-6, "${after.darkMatter}")
        assertEquals(3, after.level(Upgrade.DARK_ENERGY))
    }
}
