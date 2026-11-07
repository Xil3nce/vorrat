# Plan: Gemeinsamer Haushalt (Sync über Firebase)

Stand: 2026-11-07 – umgesetzt (siehe README, Abschnitt „Sync“). Abweichungen vom Entwurf:
Room-UNIQUE-Indizes entfallen (Room spiegelt Firestore); Ersteller-Batch nutzt `getAfter`/`existsAfter`
in den Regeln; Bilder werden vor dem Produkt geschrieben und bei Bedarf per `get` nachgeladen.

## Entscheidungen
- Sync über **Firebase** (Firestore mit Offline-Persistenz + Firebase Auth).
- Paket-ID / applicationId / namespace: **`de.frelina.vorrat`** (ersetzt Platzhalter `de.example.vorrat`) – umgesetzt.
- Beitritt per **Einladungscode**, Anmeldung anonym (kein Login/Passwort). Neuinstallation → Code erneut eingeben.
- Beide Handys haben Android 15+ → minSdk 35 bleibt.
- Installation per Sideload (kein Play Store), signierte Release-APK mit eigenem Keystore
  (`keystore.properties` + Keystore nicht einchecken).

## Architektur (geplant)
- Room bleibt lokales Lesemodell (UI, Suche, ReminderWorker unverändert lesend).
- Schreiben geht an Firestore; Snapshot-Listener spiegeln in Room (Firestore-Latenzkompensation → offline sofort sichtbar).
- IDs werden String-UUIDs → Room-Migration v1→v2 (Schema-JSON committen, Backup-schemaVersion beachten).
- Firestore: `households/{hid}` {members[], createdAt}, Subcollections `locations`, `units`, `quantityTypes`,
  `products`, `entries`, `images`; `invites/{code}` {householdId}. Regeln: Zugriff nur für members;
  Invite nur per get (kein list); Beitritt prüft Code in den Regeln.
- Anzahl +/− per `FieldValue.increment` (keine verlorenen Updates bei gleichzeitiger Änderung); 0 → Doc löschen, Undo legt neu an.
- Konflikte: last-write-wins pro Feld.
- Bilder: komprimiertes JPEG (≤ ~150 KB) als eigenes Firestore-Dokument statt Cloud Storage
  (Storage braucht für neue Projekte evtl. Blaze-Tarif – vor Umsetzung prüfen).
- Bestehende lokale Daten: Ersteller lädt sie in den neuen Haushalt hoch; beim Beitreten werden lokale Daten
  ersetzt (mit Warnung).
- Firebase hinter eigener Abstraktion → Tests mit Fake; About-/Datenschutztext, README, CLAUDE.md anpassen.
- Offen: Google-`datatransport`-Job (vermutlich ML Kit) prüfen – betrifft Datenschutztext.
- Versionen (Firebase BoM, google-services-Plugin) vor Einbau aktuell verifizieren.

## Schritte für den Nutzer in der Firebase-Konsole
1. console.firebase.google.com → Projekt anlegen (Google Analytics kann aus bleiben).
2. Android-App hinzufügen, Paketname `de.frelina.vorrat` (SHA-1 für anonyme Anmeldung nicht nötig).
3. `google-services.json` herunterladen → `app/google-services.json` (wird nicht eingecheckt).
4. Build → Authentication → Anmeldemethode **Anonym** aktivieren.
5. Build → Firestore Database → Datenbank erstellen, Standort Europa (z. B. `eur3` oder `europe-west3`),
   **Produktionsmodus** (Regeln liefere ich mit).

## Detailentwurf (für die Umsetzung)

### Abhängigkeiten (verifiziert 2026-11-07 auf dl.google.com / Maven Central)
- Firebase BoM `35.1.0` → `firebase-auth` 25.0.0, `firebase-firestore` 27.1.0 (keine -ktx-Module mehr).
- Plugin `com.google.gms.google-services` `4.5.0`.
- `org.jetbrains.kotlinx:kotlinx-coroutines-play-services` `1.11.0` (für `Task.await()`).

### Haushalt / Beitritt
- Erststart ohne Haushalt → Onboarding-Screen: „Haushalt anlegen" oder „Haushalt beitreten" (Code eingeben).
- Anonyme Anmeldung (`signInAnonymously`) automatisch.
- Einladungscode: 10 Zeichen aus Alphabet ohne 0/O/1/I/L, Anzeige `XXXXX-XXXXX`; Eingabe tolerant
  (Groß/Klein, Bindestrich/Leerzeichen egal).
- Einstellungen → Abschnitt „Haushalt": Code anzeigen + teilen (Share-Intent), Mitgliederzahl.
- Firestore-Struktur:
  - `households/{hid}` {ownerUid, createdAt}
  - `households/{hid}/members/{uid}` {code, joinedAt}
  - `invites/{code}` {householdId, createdBy}
  - `households/{hid}/{locations|units|quantityTypes|products|entries|images}/{id}`
- Regeln (`firestore.rules` im Repo, Nutzer fügt sie in der Konsole ein):
  - `invites/{code}`: get für angemeldete Nutzer, kein list; create nur, wenn Ersteller Mitglied des Haushalts.
  - `households/{hid}`: create, wenn `ownerUid == auth.uid`; read nur für Mitglieder.
  - `members/{uid}`: create, wenn `uid == auth.uid` und (Owner des Haushalts **oder**
    `get(invites/$(code)).data.householdId == hid`); read für Mitglieder.
  - Alle Daten-Subcollections: read/write nur, wenn `exists(members/$(auth.uid))`.
- Ersteller: alle lokalen Room-Daten per Batch (≤ 500 Ops) hochladen.
  Beitretender: Warnung, dann lokale Daten leeren; Listener füllt Room.

### Sync-Mechanik
- Room bleibt Lesemodell; IDs werden String-UUIDs (Migration v1→v2 mit Test; Schema 2.json committen).
- Schreiben: Repository schreibt sofort in Room **und** an Firestore (Firestore puffert offline).
- `SyncManager` (Singleton, startet bei bekanntem Haushalt): Snapshot-Listener je Subcollection →
  `docChanges` als Upsert/Delete in Room (Transaktion). Echo eigener Schreibvorgänge ist idempotent.
- Anzahl ±: `FieldValue.increment(delta)` in Firestore, lokal `quantity += delta`; 0 → Doc löschen; Undo legt neu an.
- Bilder: lokal weiter via `ImageStore`; Sync als `images/{fileName}` {data: Base64-JPEG, max. Kante 1024 px,
  Ziel ≤ 200 KB}; fehlt eine Datei lokal, wird sie beim Eintreffen des Produkts nachgeladen.
- Lokal bleiben: OFF-Cache, Erinnerungs-Einstellungen (pro Gerät).
- ReminderWorker: vor dem Auswerten einmal Daten vom Server holen (mit Timeout), dann Room auswerten.
- Backup-Import ersetzt die Haushaltsdaten in Firestore (Warnung: betrifft beide Handys); Export unverändert.
- Firebase hinter Interface (`RemoteStore`), damit Unit-Tests mit Fake laufen.
- About-/Datenschutztext: Daten liegen in Firebase (Google Cloud, Region eur3).
