# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Projekt

Sternengarten ist ein Idle-Spiel für Android und iOS (Desktop zum Testen), gebaut mit Kotlin Multiplatform und Compose Multiplatform.
- Es gibt ein einziges Gradle-Modul `:composeApp`. Fast der gesamte Code liegt in `commonMain`.
- UI-Texte, Code-Kommentare, Doku und Commit-Nachrichten sind auf Deutsch.

## Befehle

```sh
./gradlew :composeApp:desktopTest                       # alle Tests (commonTest, laufen auf der JVM)
./gradlew :composeApp:desktopTest --tests "de.vcxrisi.sternengarten.game.GameEngineTest"
./gradlew :composeApp:desktopTest --tests "de.vcxrisi.sternengarten.game.GameEngineTest.levelUpByTenAtOnce"
./gradlew :composeApp:run                               # Desktop-Fenster mit Test-Store (jeder Kauf gelingt sofort)
./gradlew :composeApp:assembleDebug                     # Android
./gradlew :composeApp:linkReleaseFrameworkIosArm64      # iOS-Framework wie in CI
```

- Für Xcode ruft die Build-Phase `embedAndSignAppleFrameworkForXcode` auf.
- Es sind kein Linter und kein Formatter eingerichtet (`kotlin.code.style=official`).
- Toolchain: Kotlin 2.4.20, Compose Multiplatform 1.12.1, AGP 8.13.0, Gradle-Wrapper 8.14.3, JDK 17.

**Cloud-Sitzungen ohne Google Maven:** Dort ist `dl.google.com` gesperrt, deshalb scheitert `./gradlew` schon beim Auflösen von AGP und androidx. Bewährt hat sich ein eigenes Gradle-Projekt außerhalb des Repos:
- Es hat nur das Ziel `jvm("desktop")` und kein Android-Plugin.
- Es verweist per `kotlin.sourceSets[...].kotlin.setSrcDirs(...)` auf `composeApp/src/commonMain`, `commonTest` und `desktopMain` dieses Repos.
- Es schließt die Gruppen `androidx.*` per `configurations.all { exclude(group = ...) }` aus.
- Es nutzt Kotlin- und CMP-Versionen von Maven Central (zuletzt Kotlin 2.1.21 und CMP 1.8.2).
- Screenshots entstehen headless mit `ImageComposeScene` direkt aus `GameScreen(controller)`.
- Daher in `commonMain` keine Sprach- oder API-Features verwenden, die diese älteren Versionen nicht kennen.
- Die Wanduhrzeit kommt ausschließlich aus `nowEpochMillis()` in `game/save/SaveRepository.kt`.

## Architektur

**`game/`: reine Spiellogik ohne UI.**
- `GameState` (`game/model`) ist eine unveränderliche, `@Serializable` Datenklasse mit dem *ganzen* Spiel: Garten, Währungen, Meta-Fortschritt und Store-Zustand.
- Die Engine-Klassen bekommen einen Zustand und geben einen neuen zurück, Aktionen auch `null` bei „nicht möglich“. Dazu kommen `GameEvent`-Listen für die UI.
  - `GameEngine`: `tick` (Altern, Supernovas, Schwarze Löcher, Kometen, Ereignisse), Aktionen, `applyOffline`, Urknall (`bigBang`/`chooseGalaxy`).
  - `BoardAnalyzer`: zustandslose Produktionsrechnung mit Nachbarschafts-Auren, Sternbildern und dem Sog Schwarzer Löcher. `analyze(state)` liefert eine `BoardAnalysis` mit `rates` je Feld und `totalRate`.
  - `ProgressionSystem`: Missionen, Erfolge, Login-Kalender, Galaxie-Ziele.
  - `ShopSystem`: Kristall-Angebote, Kapseln, Kosmetik, Gutschrift echter Käufe (`grantPurchase`).
  - `Balance`: **alle** Spielwerte und Formeln. Balancing wird nur hier geändert.
- Zufall wird als `Random` in den Konstruktor gereicht; Tests nutzen feste Seeds.

**`ui/GameController`: der einzige Zustandshalter für Compose.**
- Hält `state` und `analysis` und simuliert in festen Schritten von 0,1 s nach Wanduhr (`frame(dt)`).
- Lücken über 30 s gelten als Offline-Zeit (`applyOffline`, begrenzt durch `Balance.maxOfflineSeconds`).
- Speichert alle 5 s automatisch.
- Leitet `GameEvent`s an Toasts und an `onEvent` weiter; die Partikel-Effekte dafür stehen in `GameScreen`.
- Composables lesen nur `controller.state`/`controller.analysis` und rufen Controller-Methoden auf.
- Das Rendering liegt in `ui/render` (Canvas, `HexLayout` mit spitzen Hexagonen und axialem `Hex(q, r)`), außerdem gibt es `ui/fx` (Partikel) und `ui/hud` (Panels, Sheets, Dialoge).

**Store:**
- Die Schnittstelle ist `store/StoreGateway`. Implementierungen:
  - `PlayStoreGateway` (androidMain, Play Billing 8)
  - `iosApp/iosApp/AppStoreGateway.swift` (StoreKit 2). Sie implementiert die Kotlin-Schnittstelle und wird an `MainViewController(store)` übergeben.
  - `DebugStoreGateway` (Desktop)
- Ein Kauf wird über `processedTransactions` genau einmal gutgeschrieben. Erst wenn der Stand mit der Gutschrift gespeichert ist, wird die Transaktion beim Store abgeschlossen.
- Die Produkt-IDs in `StoreProduct` (`game/model/Store.kt`) müssen mit App Store Connect und Play Console übereinstimmen (Tabelle in README.md).

## Spielstände und Zahlen (wichtig)

- **Speicherformat:** Gespeichert wird das JSON von `GameState` unter dem Schlüssel `sternengarten.save.v1` (multiplatform-settings, `SaveRepository`, `ignoreUnknownKeys`, `allowStructuredMapKeys` für `Map<Hex, …>`). Neue Felder brauchen Default-Werte.
- **Enum-Konstanten im Zustand nie umbenennen oder löschen.** Das betrifft u. a. `StarType`, `Upgrade`, `GalaxyLaw`, `Artifact`. Eine unbekannte Konstante lässt das Dekodieren scheitern, das Spiel startet neu und der Autosave überschreibt den Spielstand. Veraltete Einträge bleiben stehen und werden nur ausgeblendet.
- **Formatänderungen und Balancing-Brüche:** `SaveMigration.CURRENT_BALANCE_VERSION` erhöhen und in `SaveMigration.migrate` umrechnen.
  - Die Migration läuft bei jedem Laden vor der Offline-Simulation.
  - Ein optionaler `RepairReport` erscheint als Dialog.
- **Überlauf:** Alle Werte sind auf `VALUE_CAP = 1e300` begrenzt (`game/engine/Numbers.kt`).
  - Neue Multiplikatoren und Belohnungen mit `capped()` begrenzen.
  - `sanitized()` läuft nach jedem Tick und Update.
  - Hintergrund: Ein echter TestFlight-Spielstand lief einmal auf ∞/NaN über und zeigte danach Sternenstaub 0.
- **Balancing-Schutz:** `BalanceSimulationTest` lässt einen gierigen Bot zehn Galaxien spielen und prüft, dass das Wachstum der Dunklen Materie abbremst. Nach jeder Balancing-Änderung ausführen.

## iOS und CI

- `codemagic.yaml` enthält den Workflow `ios-testflight`: Er baut, signiert über `fetch-signing-files --create` und lädt zu TestFlight hoch. Die Build-Nummer kommt aus `$BUILD_NUMBER`.
- `iosApp/Configuration/Config.xcconfig` muss ASCII-only bleiben, weil das xcodeproj-Gem bei Umlauten abstürzte. Dort steht auch `MARKETING_VERSION`.
- `docs/TESTFLIGHT.md` beschreibt die Einrichtung von Apple, App Store Connect und Codemagic. Sie ist auch als Auftrag für eine lokale Claude-Sitzung mit Chrome geschrieben. Den Inhalt der `.p8` und des privaten Zertifikatsschlüssels nie lesen oder ausgeben.
