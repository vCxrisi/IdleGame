package de.vcxrisi.sternengarten.game

import de.vcxrisi.sternengarten.game.engine.Balance
import de.vcxrisi.sternengarten.game.engine.isSafe
import de.vcxrisi.sternengarten.game.model.Achievement
import de.vcxrisi.sternengarten.game.model.Artifact
import de.vcxrisi.sternengarten.game.model.ConstellationKind
import de.vcxrisi.sternengarten.game.model.GalaxyKind
import de.vcxrisi.sternengarten.game.model.GalaxyLaw
import de.vcxrisi.sternengarten.game.model.GalaxyRun
import de.vcxrisi.sternengarten.game.model.GalaxyUnlock
import de.vcxrisi.sternengarten.game.model.GameState
import de.vcxrisi.sternengarten.game.model.Hex
import de.vcxrisi.sternengarten.game.model.NebulaTheme
import de.vcxrisi.sternengarten.game.model.Star
import de.vcxrisi.sternengarten.game.model.StarBridge
import de.vcxrisi.sternengarten.game.model.StarType
import de.vcxrisi.sternengarten.game.model.StoreProduct
import de.vcxrisi.sternengarten.game.model.Upgrade
import de.vcxrisi.sternengarten.game.save.LoadResult
import de.vcxrisi.sternengarten.game.save.SaveBackup
import de.vcxrisi.sternengarten.game.save.SaveFormat
import de.vcxrisi.sternengarten.game.save.SaveMigration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

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

        // Felder statt Ringe: nur die Startfläche, die Nebelausdehnung ist weg
        assertEquals(Hex.area(2).toSet(), after.ownedFields)
        assertEquals(0, after.fieldsBought)
        assertFalse(Upgrade.NEBULA_EXPANSION in after.upgrades)
        assertEquals(GalaxyKind.SPIRAL, after.activeGalaxy)
        assertTrue(after.parked.isEmpty())

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

    // ---- Version 3: Felder statt Ringe

    @Test
    fun v2SaveWithLargeRadiusKeepsFields() {
        // Nebelausdehnung 3 und Urnebel 2: früher Radius 6, also der ganze Garten.
        val v2 = GameState(
            balanceVersion = 2,
            darkMatter = 1000.0,
            galaxyNumber = 6,
            upgrades = mapOf(Upgrade.NEBULA_EXPANSION to 3, Upgrade.PRIMORDIAL_NEBULA to 2, Upgrade.STELLAR_WIND to 4),
            stars = mapOf(Hex(6, 0) to Star(StarType.RED_DWARF, age = 10.0)),
        )
        val (after, report) = SaveMigration.migrate(v2)
        assertNull(report)
        assertEquals(Hex.area(6).toSet(), after.ownedFields)
        // 127 Felder, davon 61 Startfelder mit Urnebel 2
        assertEquals(66, after.fieldsBought)
        assertFalse(Upgrade.NEBULA_EXPANSION in after.upgrades)
        assertEquals(2, after.level(Upgrade.PRIMORDIAL_NEBULA))
        assertEquals(4, after.level(Upgrade.STELLAR_WIND))
        assertEquals(v2.stars, after.stars)
        assertEquals(1000.0, after.darkMatter)
        assertEquals(SaveMigration.CURRENT_BALANCE_VERSION, after.balanceVersion)
        assertTrue(Balance.frontier(after).isEmpty(), "Weiter als Radius 6 gibt es keine Felder")
    }

    @Test
    fun highGravityV2SaveGetsNewStartArea() {
        // Hohe Gravitation mit Urnebel 1: früher Radius 2 (19 Felder), jetzt 19 + 18 − 12 = 25 Startfelder.
        val v2 = GameState(balanceVersion = 2, law = GalaxyLaw.HIGH_GRAVITY, upgrades = mapOf(Upgrade.PRIMORDIAL_NEBULA to 1))
        val (after, _) = SaveMigration.migrate(v2)
        assertEquals(Balance.FIELD_ORDER.take(25).toSet(), after.ownedFields)
        assertEquals(0, after.fieldsBought)

        // Ohne Urnebel bleibt es bei Radius 1. Ein Stern außerhalb (nur in kaputten Ständen denkbar) behält sein Feld.
        val small = GameState(balanceVersion = 2, law = GalaxyLaw.HIGH_GRAVITY, stars = mapOf(Hex(2, 0) to Star(StarType.RED_DWARF)))
        val (smallAfter, _) = SaveMigration.migrate(small)
        assertEquals(Hex.area(1).toSet() + Hex(2, 0), smallAfter.ownedFields)
        assertEquals(1, smallAfter.fieldsBought)
    }

    @Test
    fun v2SaveWithMillionsOfDarkMatterIsNotDerailed() {
        // Unter Version 1 wäre das entgleist; ein v2-Stand ist schon mit dem neuen Balancing gewachsen.
        val v2 = GameState(
            balanceVersion = 2,
            darkMatter = 5.7e6,
            upgrades = mapOf(Upgrade.DARK_ENERGY to 9),
            stars = mapOf(Hex.ORIGIN to Star(StarType.QUASAR, level = 1500, age = 50.0)),
            runStardust = 3e14,
            galaxyNumber = 14,
        )
        val (after, report) = SaveMigration.migrate(v2)
        assertNull(report)
        assertEquals(v2.copy(balanceVersion = SaveMigration.CURRENT_BALANCE_VERSION), after)
    }

    @Test
    fun literalV2JsonDecodesAndMigrates() {
        val raw = """{"stardust":12.0,"stars":[{"q":0,"r":0},{"type":"RED_DWARF"}],"upgrades":{"NEBULA_EXPANSION":1},"balanceVersion":2}"""
        val (after, report) = SaveMigration.migrate(assertNotNull(SaveFormat.decodeOrNull(raw)))
        assertNull(report)
        assertEquals(12.0, after.stardust)
        assertEquals(mapOf(Hex.ORIGIN to Star(StarType.RED_DWARF)), after.stars)
        assertEquals(Hex.area(3).toSet(), after.ownedFields)
        assertEquals(18, after.fieldsBought)
        assertTrue(after.upgrades.isEmpty())
        assertEquals(GalaxyKind.SPIRAL, after.activeGalaxy)
        assertTrue(after.parked.isEmpty() && after.unlock == null && after.bridges.isEmpty())
    }

    @Test
    fun oldJsonWithoutNewKeysDecodes() {
        val state = GameState(
            stardust = 99.0,
            stars = mapOf(Hex(1, 0) to Star(StarType.YELLOW_STAR, level = 3, age = 12.0)),
            law = GalaxyLaw.ENTROPY,
            balanceVersion = 2,
        )
        val full = SaveFormat.json.parseToJsonElement(SaveFormat.encode(state)).jsonObject
        val newKeys = setOf("ownedFields", "fieldsBought", "activeGalaxy", "unlock", "bridges", "parked")
        assertTrue(full.keys.containsAll(newKeys), "${full.keys}")
        assertEquals(state, SaveFormat.decodeOrNull(JsonObject(full - newKeys).toString()))
    }

    @Test
    fun saveFormatPrefersV3AndBacksUpUnreadable() {
        val current = GameState(stardust = 300.0, balanceVersion = 3)
        val legacy = GameState(stardust = 100.0, balanceVersion = 2)
        val v3 = SaveFormat.encode(current)
        val v1 = SaveFormat.encode(legacy)

        // Ein lesbarer v3-Stand gewinnt immer.
        assertEquals(LoadResult(current), SaveFormat.choose(v3, v1, NOW))
        assertEquals(LoadResult(current), SaveFormat.choose(v3, "kaputt", NOW))

        // Nur wenn v3 fehlt, kommt der Stand von vor dem Update.
        assertEquals(LoadResult(legacy), SaveFormat.choose(null, v1, NOW))
        assertEquals(LoadResult(null), SaveFormat.choose(null, null, NOW))
        assertEquals(LoadResult(null), SaveFormat.choose(null, "kaputt", NOW))

        // v3 unlesbar, etwa mit einer Enum-Konstante aus einem neueren Build: sichern, v1 laden, Hinweis zeigen.
        val unreadable = v3.replace("\"SPIRAL\"", "\"QUASAR_CLUSTER\"")
        assertNotEquals(v3, unreadable)
        val fallback = SaveFormat.choose(unreadable, v1, NOW)
        assertEquals(legacy, fallback.state)
        assertTrue(fallback.unreadable)
        assertEquals(SaveBackup(SaveFormat.unreadableKey(NOW), unreadable), fallback.backup)
        val nothing = SaveFormat.choose(unreadable, null, NOW)
        assertNull(nothing.state)
        assertTrue(nothing.unreadable)

        // Eine vorhandene Sicherung wird nie überschrieben.
        val taken = setOf(SaveFormat.unreadableKey(NOW), SaveFormat.unreadableKey(NOW + 1))
        assertEquals(SaveFormat.unreadableKey(NOW + 2), SaveFormat.choose(unreadable, v1, NOW, taken::contains).backup?.key)
    }

    @Test
    fun normalizeIsIdempotent() {
        val clean = SaveMigration.migrate(GameState()).first
        assertSame(clean, SaveMigration.normalizeGalaxies(clean))
        val unlocking = GameState(unlock = GalaxyUnlock(GalaxyKind.FROST, 0, 10))
        assertSame(unlocking, SaveMigration.normalizeGalaxies(unlocking))

        val messy = GameState(
            activeGalaxy = GalaxyKind.FROST,
            stars = mapOf(Hex(4, 0) to Star(StarType.RED_DWARF)),
            balanceVersion = SaveMigration.CURRENT_BALANCE_VERSION,
            unlock = GalaxyUnlock(GalaxyKind.SPIRAL, 0, 10),
            bridges = listOf(
                StarBridge(GalaxyKind.SPIRAL, GalaxyKind.FROST, 0, 10, built = true),
                StarBridge(GalaxyKind.FROST, GalaxyKind.FROST, 0, 10),
                StarBridge(GalaxyKind.FROST, GalaxyKind.EMBER, 0, 10),
                StarBridge(GalaxyKind.FROST, GalaxyKind.SPIRAL, 0, 10),
                StarBridge(GalaxyKind.SPIRAL, GalaxyKind.FROST, 5, 15),
            ),
            parked = mapOf(
                GalaxyKind.FROST to GalaxyRun(stardust = 1.0),
                GalaxyKind.SPIRAL to GalaxyRun(stars = mapOf(Hex(0, 5) to Star(StarType.YELLOW_STAR))),
            ),
        )
        val once = SaveMigration.normalizeGalaxies(messy)
        assertEquals(setOf(GalaxyKind.SPIRAL), once.parked.keys, "Die aktive Galaxie steht nicht zusätzlich in parked")
        assertTrue(Hex(4, 0) in once.ownedFields)
        assertTrue(Hex(0, 5) in once.parked.getValue(GalaxyKind.SPIRAL).ownedFields)
        assertNull(once.unlock, "Die Spiralgalaxie gibt es schon")
        assertEquals(listOf(messy.bridges[0], messy.bridges[3]), once.bridges)
        assertSame(once, SaveMigration.normalizeGalaxies(once))
        assertEquals(once, SaveMigration.migrate(messy).first, "Läuft bei jedem Laden")
    }

    private companion object {
        const val NOW = 1_760_000_000_000L
    }
}
