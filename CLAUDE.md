# CLAUDE.md – Vorrat

## Techstack
Kotlin 2.4 · AGP 9.5 (eingebautes Kotlin, kein `kotlin-android`-Plugin) · Gradle 9.9 · Compose (BOM) + Material 3 ·
Navigation Compose (typsichere `@Serializable`-Routen) · Hilt (KSP) · Room 2.8 (Schema in `app/schemas`) ·
DataStore · WorkManager (HiltWorker) · CameraX + ML Kit Barcode · Retrofit/OkHttp + kotlinx.serialization · Coil 3.
minSdk 35, compile/targetSdk 37. Versionen nur in `gradle/libs.versions.toml`.

## Befehle
```bash
./gradlew assembleDebug        # bauen
./gradlew testDebugUnitTest    # alle Tests (JVM + Robolectric, sdk=35 in src/test/resources)
./gradlew lintDebug            # Lint (soll "No issues found" bleiben)
```
SDK-Pfad steht in `local.properties` (nicht eingecheckt).

## Konventionen
- UI-Texte ausschließlich Deutsch in `res/values/strings.xml` (Plurals für Zahlen + Wörter); keine Hardcodes.
- Farben nur über Material-3-Farbrollen (Dynamic Color), keine Hex-Werte in Compose.
- Fachlogik als reine Funktionen in `domain/` (testbar ohne Android); Repositories in `data/repo/`.
- Room-Änderung: `VorratDatabase.VERSION` erhöhen, Migration in `MIGRATIONS`, neues Schema-JSON committen,
  Backup-`schemaVersion`-Prüfung beachten.
- Externe Produktdaten nur über `ProductLookupSource` (derzeit nur Open Food Facts; User-Agent + Rate-Limit beachten).
- Bilder nur über `ImageStore` (Dateiname in DB, Datei in `filesDir/images`).
- Kleine, sprechende Commits auf Deutsch.
- Platzhalter: Package `de.example.vorrat`, `OFF_CONTACT` in `app/build.gradle.kts`.
