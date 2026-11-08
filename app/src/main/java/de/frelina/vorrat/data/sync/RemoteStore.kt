package de.frelina.vorrat.data.sync

import kotlinx.coroutines.flow.Flow

/** Subcollections unter `households/{hid}`. */
enum class RemoteCollection(val path: String) {
    UNITS("units"),
    QUANTITY_TYPES("quantityTypes"),
    LOCATIONS("locations"),
    PRODUCTS("products"),
    ENTRIES("entries"),
    IMAGES("images"),
    SHOPPING("shopping"),
    ;

    companion object {
        /** Datensammlungen, die in Room gespiegelt werden – in Abhängigkeitsreihenfolge (Eltern zuerst). */
        val DATA = listOf(UNITS, QUANTITY_TYPES, LOCATIONS, PRODUCTS, ENTRIES, SHOPPING)

        /** Für den Bestand nötige Sammlungen (Erinnerungen warten nur auf diese). */
        val STOCK = listOf(UNITS, QUANTITY_TYPES, LOCATIONS, PRODUCTS, ENTRIES)
    }
}

/** Eine Dokumentänderung aus einem Snapshot-Listener. */
sealed interface RemoteChange {
    val id: String

    data class Upsert(override val id: String, val data: Map<String, Any?>) : RemoteChange

    data class Removed(override val id: String) : RemoteChange
}

/**
 * Ein Snapshot-Ereignis.
 * @param changes Änderungen relativ zum vorherigen Snapshot desselben Listeners (beim ersten: alle Dokumente).
 * @param fromCache true, solange der Stand nur aus dem lokalen Firestore-Cache stammt.
 * @param allIds alle Dokument-IDs im Snapshot (für den Abgleich beim ersten Server-Stand).
 */
data class RemoteSnapshot(
    val changes: List<RemoteChange>,
    val fromCache: Boolean,
    val allIds: Set<String>,
)

/** Schreiboperation für Sammel-Schreibvorgänge (Batches). */
sealed interface RemoteOp {
    val collection: RemoteCollection
    val id: String

    data class Set(override val collection: RemoteCollection, override val id: String, val data: Map<String, Any?>) :
        RemoteOp

    data class Delete(override val collection: RemoteCollection, override val id: String) : RemoteOp
}

fun interface RemoteRegistration {
    fun remove()
}

/**
 * Abstraktion über Firebase (Auth + Firestore), damit Repositories und Sync-Logik ohne Firebase testbar sind.
 * Einzelne Schreibvorgänge warten nicht auf den Server: Firestore übernimmt sie sofort in den lokalen
 * Cache und überträgt sie, sobald eine Verbindung besteht (auch nach einem Neustart der App).
 */
interface RemoteStore {
    /** UID des angemeldeten (anonymen) Nutzers oder null. */
    fun currentUid(): String?

    /** Meldet anonym an (falls nötig) und liefert die UID. Braucht beim ersten Mal eine Verbindung. */
    suspend fun signIn(): String

    /**
     * Legt Haushalt, Mitgliedschaft des Erstellers und Einladung in EINEM Batch an und wartet auf den Server.
     * @return neue Haushalts-ID
     */
    suspend fun createHousehold(uid: String, code: String): String

    /** Haushalts-ID zur Einladung [code] (direkt vom Server) oder null, wenn es den Code nicht gibt. */
    suspend fun findHouseholdByCode(code: String): String?

    /** Trägt [uid] als Mitglied ein (Regeln prüfen den Code) und wartet auf den Server. */
    suspend fun joinHousehold(householdId: String, uid: String, code: String)

    fun observeMemberCount(householdId: String): Flow<Int>

    fun set(householdId: String, collection: RemoteCollection, id: String, data: Map<String, Any?>)

    fun delete(householdId: String, collection: RemoteCollection, id: String)

    /** Atomares Hoch-/Herunterzählen eines Zahlenfelds (FieldValue.increment). */
    fun increment(householdId: String, collection: RemoteCollection, id: String, field: String, delta: Long)

    /** Ein Batch (≤ [MAX_BATCH_OPS] Operationen); wird lokal sofort übernommen, Server-Übertragung im Hintergrund. */
    fun commit(householdId: String, ops: List<RemoteOp>)

    /** IDs aller Dokumente einer Sammlung (Server, offline aus dem Cache). */
    suspend fun listIds(householdId: String, collection: RemoteCollection): Set<String>

    /** Einzelnes Dokument (Server, offline aus dem Cache) oder null. */
    suspend fun getDocument(householdId: String, collection: RemoteCollection, id: String): Map<String, Any?>?

    fun listen(householdId: String, collection: RemoteCollection, onSnapshot: (RemoteSnapshot) -> Unit): RemoteRegistration

    companion object {
        /** Firestore: höchstens 500 Schreiboperationen pro Batch. */
        const val MAX_BATCH_OPS = 500

        /** Puffer unter der Firestore-Grenze für die Größe einer Anfrage (10 MiB). */
        const val MAX_BATCH_BYTES = 8 * 1024 * 1024
    }
}
