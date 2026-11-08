# Vorrat

Android-App zur Übersicht über die eigenen Lebensmittelvorräte. Hauptfall: im Laden ein Produkt
scannen und sofort sehen, ob und wo es zuhause noch vorhanden ist – oder ob ein Alternativprodukt
derselben Kategorie da ist. Zwei Personen verwalten **einen gemeinsamen Haushalt** auf zwei Handys:
Die Daten werden über Firebase (anonyme Anmeldung + Cloud Firestore, Region eur3) synchronisiert;
die App funktioniert auch offline. Open Food Facts erhält nur Barcodes.

> **Konfiguration**
> - Package / applicationId: `de.frelina.vorrat`.
> - Kontaktadresse im OFF-User-Agent: `OFF_CONTACT` in `app/build.gradle.kts` (`xil3nce@gmail.com`).
>   Der User-Agent lautet `Vorrat/<versionName> (<OFF_CONTACT>)`.

## Funktionen

| Bereich | Inhalt |
|---|---|
| **Bestand** (Start) | Karten mit Bild, Name, Marke, Ort/Ebene/Fach, Anzahl + Menge-Art, Ablaufdatum. Sortierung nach Ablaufdatum (Standard, ohne Datum am Ende) oder Ort; Filter „läuft bald ab“, „abgelaufen“, Ort, „Ohne Ort“ (Hinweis-Banner mit Anzahl, solange Einträge keinem Ort zugeordnet sind). Geöffnete Packungen zählen mit „verbrauchen bis“ (Öffnungsdatum + Haltbarkeit nach dem Öffnen, höchstens MHD). Farbliche Hervorhebung über Material-3-Rollen (`tertiaryContainer` = bald, `errorContainer` = abgelaufen), Banner oben. Wischen nach links löscht (mit Rückgängig), nach rechts öffnet „Eintrag duplizieren“ (vorbelegtes Formular, z. B. für anderen Ort/Fach; die Kopie entsteht erst beim Speichern). Auslösen ab 40 % der Kartenbreite oder per schnellem Wischen. Details: +/−, bearbeiten, löschen, Produktdaten bearbeiten, „Geöffnet am …“ (bei mehreren Packungen wird eine abgetrennt; Haltbarkeit wird pro Produkt gemerkt, Vorschlag aus dem OFF-Lagerhinweis), auf die Einkaufsliste; Info-Button neben dem Namen zeigt die Nährwerte je 100 g/ml aus Open Food Facts (falls vorhanden, im OFF-Cache gespeichert, für ältere Produkte einmal nachgeladen). |
| **Suche** | Teilstring-Suche (Groß-/Kleinschreibung egal, auch Umlaute) über Produktname, Marke, Barcode, Ortsname, Notiz. |
| **Scannen** | CameraX + ML Kit (EAN-13, EAN-8, UPC-A, UPC-E), automatische Erkennung, großer „Neu scannen“-Button unten, Taschenlampe, Barcode-Eingabe per Tastatur. Ergebnis: (1) im Bestand, (2) Alternativprodukte in eigenem, deutlich markiertem Abschnitt, (3) nichts vorrätig. „Zum Bestand hinzufügen“ inkl. Abfrage „Anzahl erhöhen oder neuer Eintrag“. „Produkt manuell anlegen“ ohne Scan. Modus „Entnehmen“: Scan öffnet direkt den Entnehmen-Dialog (früheste Ablauf zuerst, −/+ und Schnellwahl 1/2/3/Alle, Rückgängig). Hinweis, wenn das Produkt auf der Einkaufsliste steht. Ort beim Erfassen optional (später zuordnen). Ablaufdatum per Kamera (ML Kit Texterkennung) oder Eingabe nur mit Ziffern (Punkte automatisch, Jahr auch zweistellig). |
| **Eingefrorenes** | Selbst Eingefrorenes ohne Barcode (z. B. „Bolognese, 2 Portionen“) über den Schneeflocken-Button im Bestand: Name (Vorschläge aus bisherigen Produkten ohne Barcode, gleichnamige werden demselben Produkt zugeordnet), Einfrierdatum (Standard heute), Ort/Ebene/Fach, Menge-Art „Port.“ (wird bei Bedarf angelegt). Jeder Eintrag kann im Formular als „Eingefroren“ markiert werden. Filter „Eingefroren“. **NFC-Chip** (NDEF, z. B. NTAG213): im Formular oder in den Details beschreiben (URI `vorrat://entry/{id}` + App-Verweis; die Chip-Kennung wird am Eintrag gespeichert, ein Chip gehört immer nur zu einem Eintrag). Lesen über das NFC-Symbol im Bestand oder einfach Antippen bei entsperrtem Handy → Details. Freier Chip (Portion aufgebraucht) → „Neue Portion einfrieren“ mit diesem Chip. |
| **Verlauf** | Wer hat wann was hinzugefügt, entnommen, gelöscht oder geöffnet (über den Haushalt synchronisiert, Name pro Handy in den Einstellungen, Aufbewahrung 90 Tage). Nimmt jemand das Letzte eines Produkts, bekommt die andere Person automatisch einen Hinweis („Milch ist alle“; abschaltbar, sonst „Bescheid geben“ im Verlauf). Eigene Nachrichten per „Nachricht“ mit Vorschlägen. Hinweise kommen ohne Server-Push: sofort, solange die App läuft, sonst über einen WorkManager-Abruf etwa alle 15 Minuten. Erreichbar über das Verlauf-Symbol im Bestand, die Einstellungen und durch Tippen auf den Hinweis. |
| **Einkauf** | Gemeinsame Einkaufsliste (über den Haushalt synchronisiert): „Nachkaufen“ automatisch für Produkte unter Mindestbestand (im Produktformular), dazu eigene Einträge (Freitext oder Produkt, Anzahl ±, abhaken mit Rückgängig). „Gekauft – zum Bestand“ öffnet das Erfassen (ohne Ort möglich) und entfernt den Eintrag. |
| **Rezepte** („Was koche ich heute?“) | Rezeptvorschläge aus bald ablaufenden Produkten: Jedes Produkt gehört zu einer Zutatengruppe (ca. 80 Gruppen, automatisch aus Name/OFF-Kategorie erkannt, im Produktformular unter „Zutat für Rezepte“ änderbar). Etwa 140 eingebaute Alltagsrezepte (`assets/recipes.json`) plus eigene Rezepte (Haushalt-synchronisiert, im Backup). Bewertung: Zutaten, die bald ablaufen, zählen mehr (abgelaufen/≤ 1 Tag ×3, ≤ 3 Tage ×2, ≤ 7 Tage ×1,5), fehlende Pflichtzutaten −1,5; Rezepte mit mehr als 2 fehlenden Zutaten werden ausgeblendet; Salz, Öl, Wasser und Gewürze gelten als vorhanden. Filter (Alle/Eigene/Vegetarisch), Suche, Chips der ablaufenden Zutaten, „Fehlendes auf die Einkaufsliste“, „Gekocht“ entnimmt die Zutaten aus dem Bestand. Einstieg: Symbol im Bestand, Banner bei bald ablaufenden Produkten, Einstellungen. |
| **Orte** (in Einstellungen) | Anlegen, umbenennen, löschen (Löschschutz). Beim Erfassen Auswahl aus der Liste + direktes Neuanlegen. |
| **Haushalt** | Erststart: „Haushalt anlegen“ (vorhandene lokale Daten werden hochgeladen) oder „Haushalt beitreten“ per Einladungscode (lokale Daten werden nach Warnung ersetzt). Einstellungen → Haushalt: Code `XXXXX-XXXXX` anzeigen/teilen, Mitgliederzahl. |
| **Einstellungen** | Haushalt, Packungseinheiten & Menge-Arten (mit Löschschutz), Erinnerungen (an/aus, Uhrzeit, Vorlauf – pro Gerät), Backup (Export/Import), Über (Version, OFF-Attribution, Datenschutz). |

## Architektur

- Kotlin, Jetpack Compose, Material 3 (Dynamic Color, Dark Mode folgt System), Single-Activity,
  Navigation Compose (typsichere `@Serializable`-Routen)
- MVVM: Compose-Screens → `@HiltViewModel` → Repositories → Room-DAOs / OFF-Quelle / `RemoteWriter`
- Firebase Auth (anonym) + Cloud Firestore hinter der Schnittstelle `RemoteStore`
  (Implementierung `FirestoreRemoteStore`, in Tests `FakeRemoteStore`)
- Hilt (DI), Kotlin Coroutines/Flow, Room (Schema-Export nach `app/schemas`), DataStore (Einstellungen),
  WorkManager (Erinnerungen), Coil 3 (Bilder aus dem internen Speicher), Retrofit + OkHttp +
  kotlinx.serialization (OFF-API)

```
app/src/main/java/de/frelina/vorrat/
├── data/
│   ├── db/        Entities, DAOs, VorratDatabase (Startwerte, Migrationen), Converters
│   ├── repo/      CatalogRepository, ProductRepository, StockRepository, ScanRepository
│   ├── off/       ProductLookupSource (Interface), OpenFoodFactsSource, OffApi, RateLimiter
│   ├── backup/    BackupManager (ZIP-Export/-Import), BackupModels (JSON)
│   ├── image/     ImageStore (Bilder in filesDir/images, JPEG-Verkleinerung für den Sync)
│   ├── household/ HouseholdStore (Haushalt pro Gerät, DataStore), HouseholdRepository (anlegen/beitreten)
│   ├── sync/      RemoteStore (Interface), FirestoreRemoteStore, RemoteMapper, RemoteWriter,
│   │              SyncApplier (Firestore → Room), SyncManager (Snapshot-Listener)
│   └── settings/  SettingsRepository (DataStore)
├── domain/        reine Logik: ExpiryLogic, AlternativeMatcher, QuantityParser,
│                  CategoryResolver, StockQueries (Sortierung/Filter/Suche), Validation, InviteCode
├── reminder/      ReminderWorker, ReminderScheduler, ReminderNotifications, RescheduleReceiver
├── di/            Hilt-Module
└── ui/            theme, navigation, components, household (Onboarding), stock, search, scan, product,
                   locations, settings
```

### Sync (gemeinsamer Haushalt)

- **Room bleibt das Lesemodell** für UI, Suche und Erinnerungen. Repositories schreiben sofort in Room
  **und** über `RemoteWriter` an Firestore (Firestore puffert offline, auch über App-Neustarts).
- **`SyncManager`** (Singleton, Start in `VorratApp`) hängt bei bekanntem Haushalt je Sammlung einen
  Snapshot-Listener an; `SyncApplier` übernimmt `docChanges` als Upsert/Delete in einer Room-Transaktion.
  Das Echo eigener Schreibvorgänge ist idempotent. Da die Sammlungen unabhängig eintreffen, werden
  Datensätze ohne Elterndatensatz (z. B. Eintrag vor seinem Produkt) zurückgestellt, bis die
  Fremdschlüssel erfüllt sind. Beim ersten Server-Stand je Sammlung werden lokale Datensätze ohne
  Gegenstück entfernt (z. B. gelöscht, während die App geschlossen war).
- **Anzahl ±** per `FieldValue.increment` (gleichzeitige Änderungen addieren sich); bei 0 wird das
  Dokument gelöscht, „Rückgängig“ legt es neu an. Konflikte sonst: last-write-wins pro Dokument.
- **Bilder** bleiben lokal im `ImageStore`; jedes Produktbild liegt zusätzlich als `images/{dateiname}`
  (Base64-JPEG, längste Kante ≤ 1024 px, Ziel ≤ 200 KB; Firestore-Limit 1 MiB pro Dokument).
  Das Bild wird vor dem Produkt geschrieben; fehlt die Datei auf dem anderen Handy, wird sie beim
  Eintreffen des Produkts nachgeladen. Kein Cloud Storage (bräuchte ggf. den Blaze-Tarif).
- **Pro Gerät** (nicht synchronisiert): Open-Food-Facts-Cache, Erinnerungs-Einstellungen, Haushaltszuordnung.
- **ReminderWorker** wartet vor dem Auswerten bis zu 20 s auf einen Server-Stand (offline: lokaler Stand).
- **Haushalt anlegen**: anonyme Anmeldung, ein Batch `households/{hid}` + `members/{uid}` + `invites/{code}`,
  danach alle lokalen Daten in Batches (≤ 500 Operationen, ≤ 8 MiB) hochladen.
  **Beitreten**: Code prüfen (`invites/{code}`), sich als Mitglied eintragen (Regeln prüfen den Code),
  lokale Daten leeren – der Listener füllt Room. Geht die anonyme Anmeldung verloren, trägt sich die App
  mit dem gespeicherten Code automatisch erneut ein; nach einer Neuinstallation muss der Code neu
  eingegeben werden.
- **Einladungscode**: 10 Zeichen aus `23456789ABCDEFGHJKMNPQRSTUVWXYZ` (ohne 0/O/1/I/L), Anzeige
  `XXXXX-XXXXX`, Eingabe tolerant (Groß/Klein, Bindestrich/Leerzeichen egal).

Firestore-Struktur und Regeln (`firestore.rules` im Repo):

| Pfad | Inhalt | Zugriff |
|---|---|---|
| `invites/{code}` | householdId, createdBy | `get` für Angemeldete, kein `list`; anlegen nur durch Mitglied des Haushalts |
| `households/{hid}` | ownerUid, createdAt | lesen nur Mitglieder; anlegen nur mit eigener `ownerUid` |
| `households/{hid}/members/{uid}` | code, joinedAt | lesen Mitglieder; anlegen nur für sich selbst als Besitzer oder mit Code, der auf `hid` zeigt |
| `households/{hid}/{locations,units,quantityTypes,products,entries,images,shopping,recipes,history}/{id}` | Datensätze (Felder wie Room, Datum/Dezimal als Text) | lesen/schreiben nur Mitglieder |

Alles andere ist verboten (insbesondere jeder Zugriff von Nicht-Mitgliedern).

### Datenmodell (Room, Version 2)

IDs sind seit Schema 2 Text-UUIDs (Migration 1 → 2 wandelt bestehende Zahlen-IDs in Text um, Fremdschlüssel
bleiben erhalten). UNIQUE-Indizes auf Name/Kürzel/Barcode gibt es nicht mehr: Room spiegelt Firestore, und
zwei Handys können offline denselben Namen anlegen. Die Eindeutigkeit prüfen Repositories bzw. Formular.

| Tabelle | Felder | Hinweise |
|---|---|---|
| `locations` | id, name | Name eindeutig (NOCASE, geprüft im Repository) |
| `units` | id, symbol (Kürzel), name | Start: g, kg, ml, l |
| `quantity_types` | id, symbol, name | Start: Stck. (Stück), Pck. (Packungen) |
| `products` | id, barcode?, name, brand?, category?, packageSize? (Dezimal als Text), unitId?, imagePath?, source (OPEN_FOOD_FACTS/MANUAL), createdAt, updatedAt, minStock?, openedDays? | Barcode eindeutig (geprüft im Formular), FK `unitId` → units (RESTRICT) |
| `stock_entries` | id, productId, locationId? (null = noch kein Ort), level?, shelf?, expiryDate?, quantity (>0), quantityTypeId, note?, openedAt?, openedDays?, frozenAt?, nfcTagId? | FK product (CASCADE), location & quantity_type (RESTRICT); Indizes auf allen FKs + expiryDate |
| `lookup_cache` | barcode, found, name, brand, category, packageSize, unitSymbol, imagePath, fetchedAt, nutrition?, nutritionLoaded, openedDays? | Cache der OFF-Abfragen (pro Gerät) |
| `shopping_items` | id, productId? (ohne FK), name, quantity, note?, createdAt | manuelle Einkaufsliste; „Nachkaufen“ wird aus minStock berechnet |
| `recipes` | id, title, minutes?, servings?, ingredients (JSON), steps (Text, eine Zeile = ein Schritt), vegetarian, createdAt, updatedAt | eigene Rezepte (seit Schema 7), Sammlung `recipes` im Haushalt, im Backup; `products.ingredientGroup?` = manuell gewählte Zutatengruppe |
| `history_events` | id, createdAt, action (ADDED/REMOVED/DELETED/OPENED/MESSAGE), authorUid?, authorName?, productId?, productName, quantity?, quantityTypeSymbol?, entryId?, outOfStock, message?, notify | Verlauf im Haushalt (seit Schema 6), ohne FK; nicht im Backup |

- Löschschutz: Repository prüft Verwendungen und liefert eine verständliche Meldung;
  der Fremdschlüssel (RESTRICT) ist zusätzliche Absicherung.
- Anzahl auf 0 → Bestandseintrag wird gelöscht (bei der letzten Einheit mit Bestätigungsdialog,
  danach „Rückgängig“-Snackbar). Produkt und Bild bleiben, damit spätere Scans es erkennen.
- Bilder liegen in `filesDir/images`; in der DB steht nur der Dateiname. OFF-Bilder werden beim
  ersten Abruf heruntergeladen. Verwaiste Bilder werden beim App-Start entfernt.

## Einrichtung (Firebase)

1. [console.firebase.google.com](https://console.firebase.google.com) → Projekt anlegen
   (hier: `frelina-vorrat-app`; Google Analytics kann aus bleiben).
2. Android-App hinzufügen, Paketname `de.frelina.vorrat` (SHA-1 ist für die anonyme Anmeldung nicht nötig).
3. `google-services.json` herunterladen und nach **`app/google-services.json`** legen.
   Die Datei ist in `.gitignore` und wird **nicht eingecheckt**; ohne sie schlägt der Build fehl
   (Task `processDebugGoogleServices`).
4. Build → Authentication → Anmeldemethode **Anonym** aktivieren.
5. Build → Firestore Database → Datenbank erstellen, Standort `eur3` (Europa), **Produktionsmodus**.
6. Firestore Database → **Regeln**: Inhalt von [`firestore.rules`](firestore.rules) einfügen und
   **Veröffentlichen**. Ohne diese Regeln verweigert der Produktionsmodus jeden Zugriff.

## Build & Run

Voraussetzungen: JDK 17+ (getestet mit Temurin 21), Android SDK mit Plattform **android-37**
(`sdk.dir` in `local.properties`), `app/google-services.json` (siehe oben).

```bash
./gradlew assembleDebug          # Debug-APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # Unit- und Robolectric-Tests (JVM, kein Gerät nötig)
./gradlew lintDebug              # Lint
./gradlew assembleRelease        # Release mit R8 (unsigniert – Signatur selbst konfigurieren)
./gradlew installDebug           # auf verbundenes Gerät/Emulator installieren
```

### Installation per Sideload (ohne Play Store)
1. APK bauen (Debug: `app/build/outputs/apk/debug/app-debug.apk`; Release erst nach eingerichteter Signatur).
2. APK aufs Handy kopieren (USB, Cloud, Messenger) oder per `adb install -r <apk>` installieren.
3. Beim ersten Öffnen der APK „Installation aus dieser Quelle zulassen“ erlauben.
4. Beide Handys müssen mit **demselben Signaturschlüssel** signierte APKs nutzen, sonst sind Updates
   über die bestehende Installation nicht möglich (Deinstallation löscht die Haushaltszuordnung –
   danach den Einladungscode erneut eingeben).
5. Erstes Handy: „Haushalt anlegen“; zweites Handy: „Haushalt beitreten“ mit dem Code aus
   Einstellungen → Haushalt.

### Updates über GitHub-Releases
Ab Version 1.1.0 aktualisiert sich die App selbst; die Handys müssen nicht mehr angeschlossen werden.
1. `versionCode` (+1) und `versionName` in `app/build.gradle.kts` erhöhen, committen, pushen.
2. `powershell -ExecutionPolicy Bypass -File scripts\publish-release.ps1 -Notes "Was ist neu"` – baut die
   signierte APK und legt das Release `v<versionName>` mit der APK in `UPDATE_REPO` an (öffentliches Repo,
   `gh` angemeldet).
3. Die App sieht beim Start (höchstens einmal pro Stunde) oder über Einstellungen → „Nach Updates suchen“ das
   neue Release und bietet „Installieren“ an. Beim ersten Mal fragt Android nach „Unbekannte Apps installieren“
   für Vorrat und lässt die Installation bestätigen.

Sicherheit: Android installiert das Update nur, wenn Paketname und Signatur zur installierten App passen; eine
fremde APK im Release würde abgelehnt. Entwürfe und Vorabversionen (Pre-Release) werden ignoriert – damit
lässt sich ein Release vorbereiten, ohne dass die Handys es sofort sehen. Die Firebase-Konfiguration steckt
ohnehin in jeder APK und ist kein Geheimnis; geschützt werden die Daten durch `firestore.rules`.

## Entscheidungen (mit Quellen)

### Versionen (Stand 2026-11-07)
Alle Versionen im `gradle/libs.versions.toml` wurden direkt aus den `maven-metadata.xml` von
Google Maven bzw. Maven Central als neueste stabile Version ermittelt.
- AGP 9.5.1 → laut [AGP-Release-Notes](https://developer.android.com/build/releases/gradle-plugin):
  max. API 37, Gradle ≥ 9.6.0, JDK 17. Verwendet: Gradle 9.9.0 (aktuelle Version laut
  `services.gradle.org/versions/current`), compileSdk/targetSdk 37 (stabiler Kanal im SDK-Repository).
- AGP 9 hat Kotlin eingebaut – daher kein `org.jetbrains.kotlin.android`-Plugin; nur Compose- und
  Serialization-Compiler-Plugin (Kotlin 2.4.21).
- **Room 2.8.5** statt Room 3.x: Room 3 erscheint unter der neuen Gruppe `androidx.room3` mit
  geändertem API (Treiber-basiert). 2.8.x ist die gepflegte stabile Linie der klassischen Artefakte.
- Material-Icons: `material-icons-extended` 1.7.8 ist im Compose-BOM 2026.09.00 enthalten (eingefroren).
- Firebase BoM **35.1.0** (→ `firebase-auth` 25.0.0, `firebase-firestore` 27.1.0; keine `-ktx`-Module mehr),
  Plugin `com.google.gms.google-services` **4.5.0** (funktioniert mit AGP 9.5.1),
  `kotlinx-coroutines-play-services` 1.11.0 (`Task.await()`), alles gegen `dl.google.com` bzw.
  Maven Central geprüft.

### Open Food Facts
Quellen: [API-Einführung](https://openfoodfacts.github.io/openfoodfacts-server/api/),
[OpenAPI-Spezifikation](https://github.com/openfoodfacts/openfoodfacts-server/tree/main/docs/api/ref)
(`api.yaml`, `schemas/product_base.yaml`, `schemas/product_base_tags.yaml`,
`schemas/product_tags.yaml`, `schemas/product_images.yaml`, `schemas/tags/taxonomy_tag_entry.yaml`),
[Nutzungsbedingungen](https://world.openfoodfacts.org/terms-of-use).

- Endpoint `GET https://world.openfoodfacts.org/api/v2/product/{code}?fields=…`, angefragte Felder:
  `code, product_name, product_name_de, brands, quantity, product_quantity, product_quantity_unit,
  image_front_url, image_url, categories_tags`. `status` 1 = gefunden, 0 = nicht gefunden.
- **User-Agent** `AppName/Version (Kontakt)` ist laut Doku Pflicht → OkHttp-Interceptor.
- **Rate-Limit** laut Doku: 15 Produkt-Leseanfragen/Minute/IP. Clientseitig erzwungen
  (`RateLimiter`, gleitendes Fenster); bei Überschreitung verständliche Meldung statt Anfrage.
  Jedes Ergebnis wird in `lookup_cache` gespeichert („gefunden“ dauerhaft, „nicht gefunden“ 24 h).
  Lokal bekannte Barcodes werden ganz ohne Netz aufgelöst.
- `product_quantity` ist in der Doku als String beschrieben, kommt real aber auch als Zahl – wird
  tolerant gelesen.
- `brands` ist laut Doku eine kommagetrennte Liste (eigentlich nur „für Debugging“); das empfohlene
  `brands_hierarchy` wurde in Testabfragen nicht geliefert. Verwendet wird daher die erste Marke aus `brands`.
- **Packungsgröße/Einheit** (`QuantityParser`): (1) `quantity` ist genau „Zahl + g/kg/ml/cl/l“ →
  übernehmen (cl → ml); (2) sonst `product_quantity` + `product_quantity_unit` (g/ml, laut Doku
  normalisierte Gesamtmenge, z. B. „3 x 150 g“ → 450 g); (3) sonst leer lassen, Hinweis im Formular.
- **Attribution (ODbL):** Einstellungen → Über: Datenbank unter ODbL 1.0, Inhalte unter DbCL 1.0,
  Bilder unter CC BY-SA 3.0 (laut Nutzungsbedingungen), mit Links.

### Alternativprodukte – „spezifischste Kategorie“
Laut OFF-Doku enthält `categories_tags` die Kategorien inkl. automatisch ergänzter Oberkategorien;
Einträge haben die Form `<sprache>:<name>`, Taxonomie-Einträge sind kanonische IDs wie
`en:hazelnut-spreads`; nicht zuordenbare Einträge bleiben in der Originalsprache (z. B. `de:Other`).
Festlegung (`CategoryResolver`):
1. Die Liste wird in der gelieferten Reihenfolge (allgemein → spezifisch) betrachtet.
2. **Spezifischste Kategorie = letzter Eintrag mit Präfix `en:`** (kanonische Taxonomie-ID).
3. Gibt es keinen `en:`-Eintrag, wird der letzte Eintrag verwendet.

Beispiel Nutella: `… en:hazelnut-spreads, en:chocolate-spreads, en:cocoa-and-hazelnuts-spreads, de:Other`
→ `en:cocoa-and-hazelnuts-spreads`.

Matching (`AlternativeMatcher`): exakt gleiche Kategorie (ohne Groß-/Kleinschreibung, getrimmt),
keine Oberkategorien, keine Namenssuche; fehlt die Kategorie auf einer Seite → keine Alternative.
Alternativen nur nach einem Scan und nur, wenn das gescannte Produkt nicht im Bestand ist.
Manuell angelegte Produkte haben ein freies Kategorie-Feld mit Autovervollständigung aus vorhandenen
Kategorien – eine Übereinstimmung mit OFF-Produkten entsteht nur, wenn dieselbe Kategorie gewählt wird.

### Erinnerungen
- Periodische WorkManager-Arbeit (24 h), erste Ausführung zur eingestellten Uhrzeit (Standard 09:00).
  **WorkManager garantiert keine exakte Uhrzeit** – Doze/Akku-Optimierung können die Ausführung
  verzögern, periodische Arbeit kann driften. Bewusst keine exakten Alarme (`SCHEDULE_EXACT_ALARM`),
  da eine Sammelbenachrichtigung keine Minutengenauigkeit braucht. Hinweis steht auch in der App.
- Höchstens eine Benachrichtigung pro Tag; keine, wenn nichts ansteht. Inhalt: abgelaufen, heute,
  innerhalb der Vorlaufzeit (Standard 3 Tage).
- Kanal „Ablauf-Erinnerungen“, Laufzeitberechtigung `POST_NOTIFICATIONS` mit Erklärdialog; ohne
  Berechtigung Hinweis + Link zu den Systemeinstellungen. Tippen öffnet Bestand mit Filter „läuft bald ab“.
- Neustart/Update: WorkManager plant persistierte Arbeit selbst neu; zusätzlich ruft
  `RescheduleReceiver` (BOOT_COMPLETED, MY_PACKAGE_REPLACED) und der App-Start `ensureScheduled()`
  (Policy KEEP) auf.

### Backup
- Export: ZIP mit `backup.json` (formatVersion, schemaVersion, alle Tabellen außer Cache) und
  `images/<datei>`; Speichern über den SAF-Dialog (`CreateDocument`) – keine Speicherberechtigungen.
- Import: `OpenDocument` → ZIP lesen → vollständig validieren (Format-/Schema-Version, Pflichtfelder,
  Referenzen, Eindeutigkeit, Anzahl ≥ 1, Datumsformat; Schutz vor „Zip Slip“) → Rückfrage
  „Bestehende Daten ersetzen?“ (Hinweis: betrifft beide Handys) → Ersetzen in **einer Room-Transaktion**.
  Bei Fehlern rollt die Transaktion zurück; Bilder werden erst nach erfolgreichem Commit übernommen.
  Danach werden die Haushaltsdaten in Firestore ersetzt (nicht mehr vorhandene Dokumente gelöscht,
  alle übrigen geschrieben) – das andere Handy übernimmt den Stand über seinen Listener.
- Backups aus Schema 1 (Zahlen-IDs) werden weiterhin gelesen; die IDs werden wie bei der Migration
  als Text übernommen. Export unverändert (`formatVersion` 1, `schemaVersion` = Room-Version, jetzt 2).
- **`android:allowBackup="false"`** + `dataExtractionRules` schließen Android-Cloud-Backup und
  Gerätetransfer bewusst aus: Der Bestand liegt im gemeinsamen Haushalt (Firestore), zusätzlich
  wird manuell gesichert.

## Tests

`./gradlew testDebugUnitTest` – 76 Tests, alle grün (Stand dieses Commits):
- `ExpiryLogicTest` – bald/abgelaufen/Grenzen, Filter, Sortierung, Sammelbenachrichtigung, Startzeit
- `AlternativeMatcherTest` – Kategorie-Matching inkl. Negativfälle
- `QuantityParserTest` – Mengen-Parsing, OFF-JSON-Mapping (echte Beispielantwort), Kategorie-Auswahl
- `ValidationAndSearchTest` – Eingabevalidierung, Textsuche, Rate-Limiter
- `DaoAndRepositoryTest` (Robolectric, In-Memory-Room) – Startwerte, Joins, Eindeutigkeit (Repository),
  Löschschutz, **Löschen bei Anzahl 0** inkl. Rückgängig, Kategorien
- `InviteCodeTest` – Alphabet, Erzeugung, Anzeige, tolerante Eingabe, ungültige Codes
- `SyncApplierTest` (Robolectric) – docChanges → Room, idempotentes Echo, Zurückstellen bei fehlenden
  Eltern, verzögertes Löschen verwendeter Orte, Abgleich beim ersten Server-Stand, fehlerhafte Dokumente
- `RemoteWriteTest` (Robolectric, `FakeRemoteStore`) – Anzahl per Increment, gleichzeitige Änderungen,
  0 → Dokument löschen, Rückgängig legt neu an, Stammdaten, ohne Haushalt kein Schreiben,
  Haushalt anlegen (Upload) / beitreten (Ersetzen, falscher Code), Batch-Größen
- `MigrationTest` (Robolectric) – DB exakt nach `schemas/…/1.json` anlegen, Room migriert auf 2 und
  prüft gegen das erwartete Schema; Werte, Joins und Fremdschlüssel bleiben erhalten
- `ScanRepositoryTest` (Robolectric) – offline/online-Auflösung, Alternativen, Cache
- `BackupRoundTripTest` (Robolectric) – Export→Import identisch inkl. Bild, ungültige Daten,
  neuere Schema-Version, Fehler mitten in der Transaktion → Daten unverändert, Schema-1-Backup
  (Zahlen-IDs) lesen und Firestore ersetzen

Die Firestore-Regeln wurden zusätzlich mit dem Firestore-Emulator geprüft (firebase-tools 15.34.0,
`@firebase/rules-unit-testing` 6.0.0, Projekt `demo-vorrat`, ohne Login): Anlegen im Batch, Beitritt mit
gültigem/falschem Code, kein Auflisten der Einladungen, kein Zugriff für Nicht-Mitglieder.

## Offene Punkte

- Sync mit echtem Firebase-Projekt auf zwei Geräten noch nicht getestet (nur Fake + Regel-Emulator).
- Keine Instrumentierungs-/UI-Tests (Compose-Tests).
- Haushalt verlassen / Mitglied entfernen / Code erneuern gibt es noch nicht. Wer den Code kennt, kann
  beitreten (31^10 Möglichkeiten, Einladungen nicht auflistbar).
- Snapshot-Listener bleiben aktiv, solange der App-Prozess lebt (auch nach einem Erinnerungs-Lauf im
  Hintergrund); Android beendet den Prozess später selbst.
- Wird eine Anzahl auf einem Handy auf 0 gesetzt, während das andere sie gleichzeitig erhöht, gewinnt
  das Löschen (Increment auf ein gelöschtes Dokument schlägt fehl).
- Google-`datatransport`-Job (vermutlich ML Kit/Firebase) für den Datenschutztext noch nicht geprüft.
- Release-Signatur ist nicht konfiguriert; Release-Build (R8) nur gebaut, nicht gestartet.
- Kategorie-Reihenfolge in `categories_tags` ist in der OFF-Doku nicht ausdrücklich garantiert
  (siehe oben). Eine robustere Variante wäre der Abgleich mit der OFF-Taxonomie.
- OFF-Kategorien werden als Taxonomie-ID (z. B. `en:hazelnut-spreads`) gespeichert und im Detail
  lesbar formatiert, aber nicht übersetzt.
- Automatisches Backup ist bewusst nicht enthalten.
- App-Icon ist ein einfacher Platzhalter.
