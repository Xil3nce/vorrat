# CLAUDE.md – Vorrat

## Techstack
Kotlin 2.4 · AGP 9.5 (eingebautes Kotlin, kein `kotlin-android`-Plugin) · Gradle 9.9 · Compose (BOM) + Material 3 ·
Navigation Compose (typsichere `@Serializable`-Routen) · Hilt (KSP) · Room 2.8 (Schema in `app/schemas`) ·
DataStore · WorkManager (HiltWorker) · CameraX + ML Kit Barcode · Retrofit/OkHttp + kotlinx.serialization · Coil 3 ·
Firebase (BoM, Auth anonym + Firestore, Plugin `com.google.gms.google-services`).
minSdk 34 (Melinas Handy: Android 14), compile/targetSdk 37. Versionen nur in `gradle/libs.versions.toml`.

## Befehle
```bash
./gradlew assembleDebug        # bauen
./gradlew testDebugUnitTest    # alle Tests (JVM + Robolectric, sdk=34 in src/test/resources)
./gradlew lintDebug            # Lint (soll "No issues found" bleiben)
```
SDK-Pfad steht in `local.properties`, Firebase-Konfiguration in `app/google-services.json` (beide nicht eingecheckt).
`firestore.rules` wird in der Firebase-Konsole eingefügt (Projekt `frelina-vorrat-app`).

## Architektur (Sync)
- Gemeinsamer Haushalt über Firestore; **Room ist Lesemodell** (UI, Suche, ReminderWorker lesen nur Room).
- Schreiben: Repository → Room sofort, dann `RemoteWriter` → `RemoteStore` (Firestore puffert offline).
  Ohne Haushalt (vor dem Onboarding) schreibt `RemoteWriter` nichts.
- Lesen aus Firestore: `SyncManager` (Snapshot-Listener je Sammlung) → `SyncApplier` (Upsert/Delete in einer
  Transaktion, stellt Datensätze mit fehlenden Eltern zurück).
- Firebase nur über `RemoteStore` ansprechen (Tests nutzen `FakeRemoteStore`); Firestore-Feldnamen nur in
  `RemoteMapper`. Neue Sammlung → `RemoteCollection`, `RemoteMapper`, `SyncApplier` und `firestore.rules`.
- IDs sind String-UUIDs (`newId()`); keine UNIQUE-Indizes in Room (Eindeutigkeit im Repository/Formular).
- Anzahl ± nur über `StockRepository.changeQuantity` (FieldValue.increment; 0 → Dokument löschen).
- Pro Gerät: OFF-Cache, Erinnerungs-Einstellungen, Haushaltszuordnung (`HouseholdStore`).

## Konventionen
- UI-Texte ausschließlich Deutsch in `res/values/strings.xml` (Plurals für Zahlen + Wörter); keine Hardcodes.
- Farben nur über Material-3-Farbrollen (Dynamic Color), keine Hex-Werte in Compose.
- Fachlogik als reine Funktionen in `domain/` (testbar ohne Android); Repositories in `data/repo/`.
- Room-Änderung: `VorratDatabase.VERSION` erhöhen, Migration in `MIGRATIONS`, neues Schema-JSON committen,
  Backup-`schemaVersion`-Prüfung beachten.
- Externe Produktdaten nur über `ProductLookupSource` (derzeit nur Open Food Facts; User-Agent + Rate-Limit beachten).
- Bilder nur über `ImageStore` (Dateiname in DB, Datei in `filesDir/images`); Sync als `images/{dateiname}`
  (Base64-JPEG ≤ 1024 px, Ziel ≤ 200 KB) über `RemoteWriter`.
- Nie einchecken: `app/google-services.json`, Keystores, `keystore.properties`.
- Kleine, sprechende Commits auf Deutsch.
- OFF-Kontaktadresse (User-Agent): `OFF_CONTACT` in `app/build.gradle.kts`.
- Selbst-Update: `UpdateManager` fragt `releases/latest` von `UPDATE_REPO` ab (Tag `vX.Y.Z`, APK-Asset).
  Release nur mit `scripts/publish-release.ps1`; vorher `versionCode` +1 und `versionName` erhöhen.
