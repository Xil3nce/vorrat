# Redesign – Notizen für die Umsetzung (Stand 11.11.2026)

Mockup: https://claude.ai/artifact/ByUXbUjhygpGZi5N81k42b
Quelldateien des Mockups (HTML, je Screen eine Datei):
`C:\Users\Xil3n\AppData\Local\Temp\claude\680ba8e2ba25ca0f\7b22a7da-415e-4bc4-9928-afe951c5ecf3\scratchpad\vorrat-design\project\*.dc.html`
(Main = Bestand, Suche, Scan, Einkauf, Einstellungen, Details, Rezepte, Essensplan, Verlauf, Orte)

## Auftrag
1. Design aus dem Mockup in die App übernehmen (alle Screens, Compose).
2. Einkaufsliste: pro Artikel Packungsgröße (Menge + Einheit) speichern und pflegen – analog zum Bestand-Formular
   (dort gibt es Packungsgröße/Einheit schon). Anzeige nur rechts als Plakette am Artikel.

## Look
- Nur dunkel (kein Hell-Modus). Feste Palette statt Dynamic Color → CLAUDE.md-Regel anpassen:
  Farben zentral im Theme (eigenes ColorScheme + Extra-Farben), keine Hex-Werte in Screens.
- Hintergrund: Verlauf #0B1B2E → #0D2536 → #0A2E33 (170°), oben rechts radialer Schimmer #14445A.
- Text #F2F6F8, sekundär #A9B8C3, tertiär #C9D5DD.
- Akzent Grün #8BE38A (Primary, Text darauf #0B1B2E). Status: bald/geöffnet Gelb #F5B94C, abgelaufen Rot #FF8A8A,
  Info/Einkauf Blau #7FC4FF.
- Glas-Karten: Weiß 6 % Fläche, 1 dp Rand Weiß 12 %, Radius 24 dp (Listenkarten 20 dp, Zeilen 16–18 dp).
- Pillen (Filter): 34–36 dp hoch, Radius rund, aktiv = weiß gefüllt mit dunkler Schrift.
- Ringe (Fortschritt/Übersicht), Ablaufbalken unter jedem Bestandseintrag (Farbe nach Status).
- Kleine weiße Kreise mit Anzahl („4×“) am Zeilenende.
- Produkt-Kacheln: Bild aus ImageStore, sonst Kürzel auf Pastellfläche, Radius 16 dp.
- Schrift: Systemschrift (Mockup nutzt Inter).
- Scrollbars dezent (in Compose ohnehin kaum sichtbar).

## Navigation
- Untere Leiste schwebend (Abstand 12 dp seitlich, 14 dp unten), Radius 36 dp, dunkel halbtransparent.
- Einträge wie bisher: Bestand, Suche, [Scannen als runder Mittelbutton, grün umrandet, ragt nach oben], Einkauf, Einstellungen.

## Screen-Entscheidungen (vom Nutzer bestätigt/gewünscht)
- Bestand: Begrüßung + Übersichtskarte mit Ring (Artikel gesamt, bald ab, abgelaufen, auf Einkaufsliste),
  Wochenleiste Essensplan, Filter-Pillen, Glas-Karten mit Ort, Anzahl, Ablaufbalken. Kopf-Buttons: Rezepte, Verlauf.
- Scannen: Modus-Buttons (Barcode, MHD, NFC-Chip, Eingeben) IMMER fix unten. Kamera-Fenster fix oben.
  Artikelkarte füllt den Platz dazwischen (gleich groß, auch bei nur 1 Ort). Darin nur die Ortsliste scrollbar.
  Jeder Ort eigene Zeile: Punkt (Status), Ort, Ablauf/Geöffnet-Info, kleiner weißer Kreis mit Anzahl am Zeilenende.
  KEIN großer Kreis „4× im Bestand“. Unter dem Namen: „Kategorie · Packung · 13× an 4 Orten“ (bzw. „4× im Bestand“).
  Aktions-Buttons („Zum Bestand hinzufügen“, „Verbrauchen“) fest am unteren Kartenrand.
- Einkaufsliste: Fortschrittsring (erledigt/gesamt), Eingabefeld „Artikel hinzufügen“ OHNE Mengenzeile darunter.
  Bereiche „Nachkaufen (unter Mindestbestand)“ (gelbe Plakette „fehlt X“) und „Auf der Liste“.
  Menge/Packung NUR rechts als Plakette (z. B. „750 g“, „2× 500 g“, „10 Stück“), nicht nochmal im Untertext.
  Untertext = Abteilung bzw. Hinweis (z. B. „Freitext – noch kein Produkt“).
- Suche, Einstellungen, Details, Rezepte, Essensplan, Verlauf, Orte: siehe Mockup.

## Technik-Hinweise
- Einkaufsliste Packungsgröße: Room-Migration (VERSION +1, MIGRATIONS, Schema-JSON), RemoteMapper, SyncApplier,
  firestore.rules, Backup-schemaVersion prüfen; UI zum Pflegen (Bearbeiten-Dialog/Formular wie beim Bestand).
- Strings nur in strings.xml (Plurals). Tests/Lint grün halten.
- Danach: versionCode/versionName erhöhen, pushen, scripts/publish-release.ps1 ausführen.
