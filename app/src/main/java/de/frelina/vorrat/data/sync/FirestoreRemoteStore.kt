package de.frelina.vorrat.data.sync

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore-Struktur (siehe firestore.rules im Repo):
 * - `households/{hid}` {ownerUid, createdAt}
 * - `households/{hid}/members/{uid}` {code, joinedAt}
 * - `invites/{code}` {householdId, createdBy}
 * - `households/{hid}/{locations|units|quantityTypes|products|entries|images}/{id}`
 */
@Singleton
class FirestoreRemoteStore @Inject constructor() : RemoteStore {

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    private fun household(hid: String) = firestore.collection(HOUSEHOLDS).document(hid)

    private fun collection(hid: String, c: RemoteCollection) = household(hid).collection(c.path)

    override fun currentUid(): String? = auth.currentUser?.uid

    override suspend fun signIn(): String {
        auth.currentUser?.let { return it.uid }
        val user = auth.signInAnonymously().await().user ?: error("Anmeldung ohne Nutzer")
        return user.uid
    }

    override suspend fun createHousehold(uid: String, code: String): String {
        val ref = firestore.collection(HOUSEHOLDS).document()
        firestore.batch()
            .set(ref, mapOf("ownerUid" to uid, "createdAt" to FieldValue.serverTimestamp()))
            .set(ref.collection(MEMBERS).document(uid), mapOf("code" to code, "joinedAt" to FieldValue.serverTimestamp()))
            .set(firestore.collection(INVITES).document(code), mapOf("householdId" to ref.id, "createdBy" to uid))
            .commit()
            .await()
        return ref.id
    }

    override suspend fun findHouseholdByCode(code: String): String? {
        val doc = firestore.collection(INVITES).document(code).get(Source.SERVER).await()
        return if (doc.exists()) doc.getString("householdId") else null
    }

    override suspend fun joinHousehold(householdId: String, uid: String, code: String) {
        household(householdId).collection(MEMBERS).document(uid)
            .set(mapOf("code" to code, "joinedAt" to FieldValue.serverTimestamp()))
            .await()
    }

    override fun observeMemberCount(householdId: String): Flow<Int> = callbackFlow {
        val registration = household(householdId).collection(MEMBERS).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Mitglieder nicht lesbar", error)
                return@addSnapshotListener
            }
            if (snapshot != null) trySend(snapshot.size())
        }
        awaitClose { registration.remove() }
    }

    override fun set(householdId: String, collection: RemoteCollection, id: String, data: Map<String, Any?>) {
        collection(householdId, collection).document(id).set(data).addOnFailureListener(::logFailure)
    }

    override fun delete(householdId: String, collection: RemoteCollection, id: String) {
        collection(householdId, collection).document(id).delete().addOnFailureListener(::logFailure)
    }

    override fun increment(householdId: String, collection: RemoteCollection, id: String, field: String, delta: Long) {
        // update() statt set(merge): existiert das Dokument nicht mehr (anderes Handy hat gelöscht),
        // schlägt die Änderung fehl, statt ein unvollständiges Dokument anzulegen.
        collection(householdId, collection).document(id).update(field, FieldValue.increment(delta))
            .addOnFailureListener(::logFailure)
    }

    override fun commit(householdId: String, ops: List<RemoteOp>) {
        require(ops.size <= RemoteStore.MAX_BATCH_OPS) { "zu viele Operationen: ${ops.size}" }
        val batch = firestore.batch()
        ops.forEach { op ->
            val ref = collection(householdId, op.collection).document(op.id)
            when (op) {
                is RemoteOp.Set -> batch.set(ref, op.data)
                is RemoteOp.Delete -> batch.delete(ref)
            }
        }
        batch.commit().addOnFailureListener(::logFailure)
    }

    override suspend fun listIds(householdId: String, collection: RemoteCollection): Set<String> =
        collection(householdId, collection).get().await().documents.mapTo(mutableSetOf()) { it.id }

    override suspend fun getDocument(householdId: String, collection: RemoteCollection, id: String): Map<String, Any?>? {
        val doc = collection(householdId, collection).document(id).get().await()
        return if (doc.exists()) doc.data else null
    }

    override fun listen(
        householdId: String,
        collection: RemoteCollection,
        onSnapshot: (RemoteSnapshot) -> Unit,
    ): RemoteRegistration {
        val registration = collection(householdId, collection).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Listener ${collection.path} beendet", error)
                return@addSnapshotListener
            }
            if (snapshot == null) return@addSnapshotListener
            val changes = snapshot.documentChanges.map { change ->
                val doc = change.document
                when (change.type) {
                    DocumentChange.Type.REMOVED -> RemoteChange.Removed(doc.id)
                    DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> RemoteChange.Upsert(doc.id, doc.data)
                }
            }
            onSnapshot(
                RemoteSnapshot(
                    changes = changes,
                    fromCache = snapshot.metadata.isFromCache,
                    allIds = snapshot.documents.mapTo(mutableSetOf()) { it.id },
                ),
            )
        }
        return RemoteRegistration { registration.remove() }
    }

    private fun logFailure(e: Exception) {
        Log.w(TAG, "Firestore-Schreibvorgang fehlgeschlagen", e)
    }

    private companion object {
        const val TAG = "FirestoreRemoteStore"
        const val HOUSEHOLDS = "households"
        const val MEMBERS = "members"
        const val INVITES = "invites"
    }
}
