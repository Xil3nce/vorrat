# Plan: Gemeinsamer Haushalt (Sync über Firebase)

Stand: 2026-11-07 – Entscheidungen des Nutzers, noch nicht umgesetzt.

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
