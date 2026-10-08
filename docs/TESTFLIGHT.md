# Sternengarten zu TestFlight bringen (Codemagic)

Diese Anleitung ist so geschrieben, dass eine **Claude-Sitzung auf deinem Computer** sie Schritt für Schritt ausführen kann. Das kann Claude Desktop mit Chrome-Erweiterung sein oder `claude --chrome` im Terminal. Du kannst sie natürlich auch selbst abarbeiten.

| Was | Wert |
|---|---|
| Repository | `vCxrisi/IdleGame`, Branch `claude/confident-tesla-4tzdfl` |
| Bundle-ID | `de.vcxrisi.sternengarten` |
| Name in App Store Connect | **Sternengarten: Idle** (auf dem Gerät heißt die App „Sternengarten“) |
| SKU | `sternengarten-ios` |
| Codemagic-Workflow | `ios-testflight` („iOS TestFlight“) aus `codemagic.yaml` |
| Name des API-Schlüssels in Codemagic | genau **`Codemagic`** (großes C) |
| Variablen-Gruppe in Codemagic | **`code-signing`** mit dem Secret `CERTIFICATE_PRIVATE_KEY` |

## Regeln für die ausführende Claude-Sitzung

- **Arbeite in neuen Tabs.** Lies vorher den Tab-Kontext und rühre die vorhandenen Tabs des Nutzers nicht an.
- **Diese Schritte erledigt der Mensch selbst.** Halte jeweils an und bitte darum:
  - Anmeldung und Zwei-Faktor-Bestätigung bei Apple und Codemagic.
  - Herunterladen der `.p8`-Datei. Sie lässt sich **nur einmal** herunterladen.
  - Datei-Uploads über den Dateidialog des Betriebssystems.
  - Einfügen von Secrets: den Inhalt der `.p8` und des privaten Zertifikatschlüssels.
  - Zustimmung zu Verträgen und rechtliche Angaben.
- **Geheimnisse nie auslesen, anzeigen oder in den Chat schreiben.** Das betrifft den Inhalt von `.p8` und `ios_distribution_private_key`. Lege beide Dateien nie im Repository ab.
- **Löse nichts Kostenpflichtiges oder Endgültiges ohne Rückfrage aus.** Dazu zählen Zertifikate widerrufen, Apps löschen und Verträge annehmen.
- **Halte nach 2–3 Fehlversuchen an.** Beschreibe dann, was passiert ist, statt weiter zu probieren.

## 1. Apple Developer: Bundle-ID registrieren

1. Öffne https://developer.apple.com/account/resources/identifiers/list
2. Klicke auf „+“ → **App IDs** → Weiter → Typ **App** → Weiter.
3. Gib Folgendes ein:
   - Description: `Sternengarten`
   - Bundle ID: **Explicit**, `de.vcxrisi.sternengarten`
   - Capabilities: Standard lassen; In-App Purchase ist automatisch dabei.
4. Klicke auf **Continue** → **Register**.

Gibt es die ID schon, überspringe diesen Schritt.

## 2. App Store Connect: App anlegen

1. Öffne https://appstoreconnect.apple.com/apps und klicke auf „+“ → **Neue App**.
2. Gib Folgendes ein:
   - Plattform: **iOS**
   - Name: **Sternengarten: Idle**
   - Primäre Sprache: **Deutsch**
   - Bundle-ID: `de.vcxrisi.sternengarten`
   - SKU: `sternengarten-ios`
   - Zugriff: Voller Zugriff
3. Klicke auf **Erstellen**.

Ist der Name vergeben, frage den Nutzer nach einem Ausweichnamen. Der Name auf dem Gerät bleibt „Sternengarten“.

## 3. App Store Connect: API-Schlüssel für Codemagic

1. Öffne https://appstoreconnect.apple.com/access/integrations/api
   - Beim ersten Mal muss der Account Holder „Zugriff anfordern“ und zustimmen.
2. Gehe zum Reiter **Team-Schlüssel** und klicke auf „+“.
3. Name: `Codemagic`, Zugriff: **App Manager** → **Generieren**.
4. Notiere die **Issuer-ID** (über der Tabelle) und die **Schlüssel-ID**.
5. **Mensch:** Lade die `.p8`-Datei herunter (nur einmal möglich) und bewahre sie sicher auf.

## 4. Privater Schlüssel für das Distribution-Zertifikat (Terminal)

Diesen Befehl kann die lokale Claude-Sitzung im Terminal ausführen, zum Beispiel im Home-Verzeichnis, **nicht** im Repository:

```sh
ssh-keygen -t rsa -b 2048 -m PEM -f ~/ios_distribution_private_key -q -N ""
```

- Es entstehen `~/ios_distribution_private_key` und eine `.pub`-Datei. Die `.pub` wird nicht gebraucht.
- Codemagic legt damit beim ersten Build das Apple-Distribution-Zertifikat und das App-Store-Profil selbst an.

## 5. Codemagic: App hinzufügen

1. Öffne https://codemagic.io/apps und klicke auf **Add application**.
2. Wähle **GitHub** als Quelle.
   - Ist noch keine Verbindung da, Codemagic autorisieren und die GitHub-App für das Konto `vCxrisi` installieren. Wenn nur einzelne Repos freigegeben sind, `IdleGame` auswählen.
3. Wähle das Repository `vCxrisi/IdleGame`.
4. Projekttyp: **Other** bzw. die Konfiguration über `codemagic.yaml` → **Add application**.

## 6. Codemagic: API-Schlüssel hinterlegen

1. Öffne die Einstellungen von Team oder persönlichem Konto → **Integrations** → **Developer Portal** → **Connect** bzw. **Manage keys** → **Add key**.
   - Nimm **dasselbe Konto**, dem die App in Schritt 5 hinzugefügt wurde. Persönliches Konto und Teams haben getrennte Integrationen.
   - Ohne Team landet die App im persönlichen Konto.
2. Gib Folgendes ein:
   - App Store Connect API key name: **`Codemagic`** mit großem C. Er muss exakt so heißen, weil `codemagic.yaml` ihn so referenziert.
   - Issuer ID und Key ID aus Schritt 3.
   - **Mensch:** die `.p8`-Datei hochladen.
3. Klicke auf **Save**.

## 7. Codemagic: Secret für das Signieren

1. Öffne die App in Codemagic → Reiter **Environment variables**.
2. Lege die Variable an:
   - Variable name: `CERTIFICATE_PRIVATE_KEY`
   - Variable value: **Mensch** fügt den kompletten Inhalt von `~/ios_distribution_private_key` ein, inklusive der Zeilen `-----BEGIN RSA PRIVATE KEY-----` und `-----END RSA PRIVATE KEY-----`.
     - Auf dem Mac geht das mit `pbcopy < ~/ios_distribution_private_key`, danach einfügen.
   - Group: **`code-signing`** (neu anlegen)
   - **Secret** anhaken → **Add**.

## 8. Build starten

1. Klicke in der App auf **Start new build**.
2. Branch: `claude/confident-tesla-4tzdfl`, Workflow: **iOS TestFlight** → **Start new build**.
3. Rechne mit 25–40 Minuten für den ersten Lauf.
   - Spätere Läufe sind etwas schneller, weil die Gradle-Abhängigkeiten im Cache liegen.
   - Die Kotlin/Native-Werkzeuge werden jedes Mal neu geladen.
4. Danach zeigt App Store Connect → TestFlight den Build als „Wird verarbeitet“. Das dauert 5–30 Minuten.

## 9. Auf dem iPhone testen

1. Gehe in App Store Connect → die App → **TestFlight** → **Interne Tests** → „+“.
2. Lege die Gruppe „Intern“ an, aktiviere die automatische Verteilung und füge dich selbst hinzu.
3. Installiere die **TestFlight-App** auf dem iPhone und dann Sternengarten.

## Wenn etwas schiefgeht

Schicke den Log-Ausschnitt des fehlgeschlagenen Schritts oder das Artefakt `xcodebuild_logs` in die Cloud-Sitzung, die das Spiel entwickelt. Sie korrigiert das Repository, danach den Build neu starten.

| Problem | Lösung |
|---|---|
| Xcode-Version 26.4 nicht verfügbar | In `codemagic.yaml` eine angebotene 26.x-Version oder `latest` eintragen. |
| `fetch-signing-files` meldet „already have a current Distribution certificate“ | Apple erlaubt höchstens 3 Distribution-Zertifikate. Nach Rückfrage ein ungenutztes widerrufen. |
| 403 / fehlende Rechte beim Anlegen von Zertifikat oder Profil | Den API-Schlüssel mit der Rolle **Admin** neu erzeugen und in Codemagic ersetzen. |
| Publishing schlägt fehl: App nicht gefunden | Schritt 2 prüfen. Die IPA liegt als Artefakt vor; den Build danach wiederholen. |
| Build-Nummer schon vergeben | Passiert, wenn der Workflow umbenannt oder die App neu angelegt wurde. Die Cloud-Sitzung setzt dann einen Versatz. |
| „SDK location not found“ | Der Schritt „Android-SDK für Gradle bekannt machen“ fand kein SDK; Log an die Cloud-Sitzung schicken. |

## Ausweichweg ohne Terminal (Plan B)

Falls Schritt 4 nicht möglich ist, sind statt des automatischen Signierens vier Handgriffe nötig. Die Cloud-Sitzung passt dafür `codemagic.yaml` an: `ios_signing` statt `fetch-signing-files`.

1. In Codemagic: Team settings → codemagic.yaml settings → **Code signing identities** → iOS certificates.
   - **Generate certificate** mit Typ Apple Distribution und Schlüssel `Codemagic`.
   - Die `.p12` einmalig herunterladen und das Passwort notieren.
   - Danach im Reiter **Upload certificate** wieder hochladen und einen Referenznamen vergeben.
2. Im Apple-Developer-Portal: Profiles → „+“ → **App Store Connect**.
   - Die App-ID `de.vcxrisi.sternengarten` und dieses Zertifikat wählen → Generate.
3. In Codemagic: iOS provisioning profiles → **Fetch profiles** → das App-Store-Profil auswählen → Referenzname → **Download selected**.

## Später: Ingame-Käufe

Die sechs Produkt-IDs aus der README legst du unter App Store Connect → die App → **In-App-Käufe** an. Kristalle sind Verbrauchsartikel, Starterpaket und Sternenwanderer Nicht-Verbrauchsartikel. Außerdem muss der Vertrag „Kostenpflichtige Apps“ akzeptiert sein.

Ohne diese Produkte zeigt der Shop in TestFlight „Store lädt …“. Der Upload selbst funktioniert trotzdem.
