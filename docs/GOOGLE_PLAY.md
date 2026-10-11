# Sternengarten zu Google Play bringen (Codemagic)

| Was | Wert |
|---|---|
| Paket-ID | `de.vcxrisi.sternengarten` |
| Name in der Play Console | **Sternengarten: Idle** |
| Codemagic-Workflows | `android-aab` („Android AAB“) und `android-google-play` („Android Google Play“) aus `codemagic.yaml` |
| Referenzname des Upload-Keystores in Codemagic | genau **`sternengarten_upload`** |
| Variablen-Gruppe in Codemagic | **`google-play`** mit dem Secret `GCLOUD_SERVICE_ACCOUNT_CREDENTIALS` |

Der Upload-Schlüssel kommt **nie** ins Repository. Gradle liest ihn aus den Variablen `CM_KEYSTORE_PATH`, `CM_KEYSTORE_PASSWORD`, `CM_KEY_ALIAS` und `CM_KEY_PASSWORD`, die Codemagic selbst setzt. Der `versionCode` kommt aus `VERSION_CODE`, lokal ist er 1.

## 1. Upload-Keystore erzeugen (einmalig, im Terminal)

Nicht im Repository ausführen:

```sh
keytool -genkeypair -v -keystore ~/sternengarten-upload.jks -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000
```

- Passwort und Alias (`upload`) sicher notieren, die `.jks` sichern.
- Geht der Schlüssel verloren, kann Google ihn zurücksetzen (Play App Signing), das dauert aber einige Tage.

## 2. Codemagic: Keystore hochladen

1. Team settings → **codemagic.yaml settings** → **Code signing identities** → **Android keystores**.
2. Die `.jks` hochladen, Keystore-Passwort, Alias `upload` und Key-Passwort eintragen.
3. Referenzname: **`sternengarten_upload`** → **Add keystore**.

## 3. Erstes Bundle bauen und von Hand hochladen

Google nimmt Uploads über die API erst an, wenn die App schon ein Bundle hat.

1. In Codemagic **Start new build** → Branch `claude/confident-tesla-4tzdfl` → Workflow **Android AAB**.
2. Das Artefakt `composeApp-release.aab` herunterladen.
3. Play Console → die App → **Testen → Interner Test** → Neuen Release erstellen → Bundle hochladen.
4. Play App Signing bestätigen (Google verwaltet den App-Signaturschlüssel, dein Schlüssel ist nur der Upload-Schlüssel).

## 4. Dienstkonto für automatische Uploads

1. Google Cloud Console → Projekt anlegen oder wählen → **IAM → Dienstkonten** → Dienstkonto anlegen → Schlüssel → **JSON** herunterladen.
2. Im selben Projekt die **Google Play Android Developer API** aktivieren.
3. Play Console → **Nutzer und Berechtigungen** → Nutzer einladen → E-Mail des Dienstkontos → für die App Sternengarten die Rechte „Releases verwalten“ (Testtracks) vergeben.
4. Codemagic → die App → **Environment variables**:
   - Name `GCLOUD_SERVICE_ACCOUNT_CREDENTIALS`, Wert: kompletter Inhalt der JSON-Datei
   - Group **`google-play`**, **Secret** anhaken → Add.

## 5. Ab jetzt

Workflow **Android Google Play** starten. Er nimmt den höchsten `versionCode` aus Google Play + 1, baut, signiert und lädt in den **internen Test** hoch (als Entwurf, solange die App nicht veröffentlicht ist; danach in `codemagic.yaml` `submit_as_draft: false` setzen).

## Wenn etwas schiefgeht

| Problem | Lösung |
|---|---|
| „Kein Upload-Schlüssel“ | Schritt 2, Referenzname muss exakt `sternengarten_upload` sein. |
| „Noch kein Build in Google Play gefunden“ | Schritt 3 zuerst erledigen, oder das Dienstkonto (Schritt 4) hat keine Rechte. |
| Play meldet „versionCode bereits verwendet“ | Workflow **Android Google Play** statt **Android AAB** nutzen; er liest den aktuellen Stand aus Play. |
| „SDK location not found“ | Log an die Cloud-Sitzung schicken. |
