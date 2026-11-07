# Vorrat

Android-App zur Übersicht über die eigenen Lebensmittelvorräte. Hauptfall: im Laden ein Produkt
scannen und sofort sehen, ob und wo es zuhause noch vorhanden ist – oder ob ein Alternativprodukt
derselben Kategorie da ist. Alle Daten liegen lokal auf dem Gerät; Internet wird nur für
Barcode-Abfragen bei Open Food Facts genutzt.

> **Platzhalter, die du anpassen musst**
> - **Package-Name / applicationId:** `de.example.vorrat` (in `app/build.gradle.kts` → `namespace`,
>   `applicationId`, sowie Ordnerstruktur `app/src/*/java/de/example/vorrat`).
> - **Kontaktadresse im OFF-User-Agent:** `OFF_CONTACT` in `app/build.gradle.kts`
>   (derzeit `kontakt@example.com`). Der User-Agent lautet `Vorrat/<versionName> (<OFF_CONTACT>)`.

## Funktionen

| Bereich | Inhalt |
|---|---|
| **Bestand** (Start) | Karten mit Bild, Name, Marke, Ort/Ebene/Fach, Anzahl + Menge-Art, Ablaufdatum. Sortierung nach Ablaufdatum (Standard, ohne Datum am Ende) oder Ort; Filter „läuft bald ab“, „abgelaufen“, Ort. Farbliche Hervorhebung über Material-3-Rollen (`tertiaryContainer` = bald, `errorContainer` = abgelaufen), Banner oben. Details: +/−, bearbeiten, löschen, Produktdaten bearbeiten. |
| **Suche** | Teilstring-Suche (Groß-/Kleinschreibung egal, auch Umlaute) über Produktname, Marke, Barcode, Ortsname, Notiz. |
| **Scannen** | CameraX + ML Kit (EAN-13, EAN-8, UPC-A, UPC-E), automatische Erkennung, großer „Neu scannen“-Button unten, Taschenlampe, Barcode-Eingabe per Tastatur. Ergebnis: (1) im Bestand, (2) Alternativprodukte in eigenem, deutlich markiertem Abschnitt, (3) nichts vorrätig. „Zum Bestand hinzufügen“ inkl. Abfrage „Anzahl erhöhen oder neuer Eintrag“. „Produkt manuell anlegen“ ohne Scan. |
| **Orte** | Anlegen, umbenennen, löschen (Löschschutz). Beim Erfassen Auswahl aus der Liste + direktes Neuanlegen. |
| **Einstellungen** | Packungseinheiten & Menge-Arten (mit Löschschutz), Erinnerungen (an/aus, Uhrzeit, Vorlauf), Backup (Export/Import), Über (Version, OFF-Attribution). |

## Architektur

- Kotlin, Jetpack Compose, Material 3 (Dynamic Color, Dark Mode folgt System), Single-Activity,
  Navigation Compose (typsichere `@Serializable`-Routen)
- MVVM: Compose-Screens → `@HiltViewModel` → Repositories → Room-DAOs / OFF-Quelle
- Hilt (DI), Kotlin Coroutines/Flow, Room (Schema-Export nach `app/schemas`), DataStore (Einstellungen),
  WorkManager (Erinnerungen), Coil 3 (Bilder aus dem internen Speicher), Retrofit + OkHttp +
  kotlinx.serialization (OFF-API)

```
app/src/main/java/de/example/vorrat/
├── data/
│   ├── db/        Entities, DAOs, VorratDatabase (Startwerte, Migrationen), Converters
│   ├── repo/      CatalogRepository, ProductRepository, StockRepository, ScanRepository
│   ├── off/       ProductLookupSource (Interface), OpenFoodFactsSource, OffApi, RateLimiter
│   ├── backup/    BackupManager (ZIP-Export/-Import), BackupModels (JSON)
│   ├── image/     ImageStore (Bilder in filesDir/images)
│   └── settings/  SettingsRepository (DataStore)
├── domain/        reine Logik: ExpiryLogic, AlternativeMatcher, QuantityParser,
│                  CategoryResolver, StockQueries (Sortierung/Filter/Suche), Validation
├── reminder/      ReminderWorker, ReminderScheduler, ReminderNotifications, RescheduleReceiver
├── di/            Hilt-Module
└── ui/            theme, navigation, components, stock, search, scan, product, locations, settings
```

### Datenmodell (Room, Version 1)

| Tabelle | Felder | Hinweise |
|---|---|---|
| `locations` | id, name | `name` eindeutig (NOCASE) |
| `units` | id, symbol (Kürzel), name | Start: g, kg, ml, l; `symbol` eindeutig |
| `quantity_types` | id, symbol, name | Start: Stck. (Stück), Pck. (Packungen) |
| `products` | id, barcode?, name, brand?, category?, packageSize? (Dezimal als Text), unitId?, imagePath?, source (OPEN_FOOD_FACTS/MANUAL), createdAt, updatedAt | `barcode` UNIQUE (mehrere NULL erlaubt), FK `unitId` → units (RESTRICT) |
| `stock_entries` | id, productId, locationId, level?, shelf?, expiryDate?, quantity (>0), quantityTypeId, note? | FK product (CASCADE), location & quantity_type (RESTRICT); Indizes auf allen FKs + expiryDate |
| `lookup_cache` | barcode, found, name, brand, category, packageSize, unitSymbol, imagePath, fetchedAt | Cache der OFF-Abfragen |

- Löschschutz: Repository prüft Verwendungen und liefert eine verständliche Meldung;
  der Fremdschlüssel (RESTRICT) ist zusätzliche Absicherung.
- Anzahl auf 0 → Bestandseintrag wird gelöscht (bei der letzten Einheit mit Bestätigungsdialog,
  danach „Rückgängig“-Snackbar). Produkt und Bild bleiben, damit spätere Scans es erkennen.
- Bilder liegen in `filesDir/images`; in der DB steht nur der Dateiname. OFF-Bilder werden beim
  ersten Abruf heruntergeladen. Verwaiste Bilder werden beim App-Start entfernt.

## Build & Run

Voraussetzungen: JDK 17+ (getestet mit Temurin 21), Android SDK mit Plattform **android-37**
(`sdk.dir` in `local.properties`).

```bash
./gradlew assembleDebug          # Debug-APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # Unit- und Robolectric-Tests (JVM, kein Gerät nötig)
./gradlew lintDebug              # Lint
./gradlew assembleRelease        # Release mit R8 (unsigniert – Signatur selbst konfigurieren)
./gradlew installDebug           # auf verbundenes Gerät/Emulator installieren
```

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
  „Bestehende Daten ersetzen?“ → Ersetzen in **einer Room-Transaktion**. Bei Fehlern rollt die
  Transaktion zurück; Bilder werden erst nach erfolgreichem Commit übernommen.
- **`android:allowBackup="false"`** + `dataExtractionRules` schließen Cloud-Backup und
  Gerätetransfer bewusst aus: Die Daten sollen lokal bleiben, gesichert wird nur manuell.
  Wer Android-Cloud-Backup möchte, kann das im Manifest ändern.

## Tests

`./gradlew testDebugUnitTest` – 52 Tests, alle grün (Stand dieses Commits):
- `ExpiryLogicTest` – bald/abgelaufen/Grenzen, Filter, Sortierung, Sammelbenachrichtigung, Startzeit
- `AlternativeMatcherTest` – Kategorie-Matching inkl. Negativfälle
- `QuantityParserTest` – Mengen-Parsing, OFF-JSON-Mapping (echte Beispielantwort), Kategorie-Auswahl
- `ValidationAndSearchTest` – Eingabevalidierung, Textsuche, Rate-Limiter
- `DaoAndRepositoryTest` (Robolectric, In-Memory-Room) – Startwerte, Joins, Eindeutigkeit,
  Löschschutz, **Löschen bei Anzahl 0** inkl. Rückgängig, Kategorien
- `ScanRepositoryTest` (Robolectric) – offline/online-Auflösung, Alternativen, Cache
- `BackupRoundTripTest` (Robolectric) – Export→Import identisch inkl. Bild, ungültige Daten,
  neuere Schema-Version, Fehler mitten in der Transaktion → Daten unverändert

## Offene Punkte

- Nicht auf einem Gerät/Emulator ausgeführt – nur kompiliert, Lint ohne Befunde, Unit-/Robolectric-Tests.
  Kamera, ML Kit, Benachrichtigungen, SAF-Dialoge und die Compose-Oberfläche sind ungeprüft.
- Keine Instrumentierungs-/UI-Tests (Compose-Tests) und kein Room-`MigrationTest` (es gibt erst Schema 1;
  ab Version 2 Migration in `VorratDatabase.MIGRATIONS` ergänzen und testen).
- Release-Signatur ist nicht konfiguriert; Release-Build (R8) nur gebaut, nicht gestartet.
- Kategorie-Reihenfolge in `categories_tags` ist in der OFF-Doku nicht ausdrücklich garantiert
  (siehe oben). Eine robustere Variante wäre der Abgleich mit der OFF-Taxonomie.
- OFF-Kategorien werden als Taxonomie-ID (z. B. `en:hazelnut-spreads`) gespeichert und im Detail
  lesbar formatiert, aber nicht übersetzt.
- Automatisches Backup ist bewusst nicht enthalten.
- App-Icon ist ein einfacher Platzhalter.
