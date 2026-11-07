package de.frelina.vorrat.data

import de.frelina.vorrat.data.sync.RemoteCollection
import de.frelina.vorrat.data.sync.RemoteOp
import de.frelina.vorrat.data.sync.RemoteRegistration
import de.frelina.vorrat.data.sync.RemoteSnapshot
import de.frelina.vorrat.data.sync.RemoteStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** In-Memory-Ersatz für Firestore: speichert Dokumente und protokolliert Schreibaufrufe. */
class FakeRemoteStore : RemoteStore {
    sealed interface Call {
        data class Set(val collection: RemoteCollection, val id: String, val data: Map<String, Any?>) : Call
        data class Delete(val collection: RemoteCollection, val id: String) : Call
        data class Increment(val collection: RemoteCollection, val id: String, val field: String, val delta: Long) : Call
        data class Commit(val ops: List<RemoteOp>) : Call
    }

    val calls = mutableListOf<Call>()
    val docs = mutableMapOf<RemoteCollection, MutableMap<String, Map<String, Any?>>>()
    val invites = mutableMapOf<String, String>()
    val members = mutableMapOf<String, MutableSet<String>>()
    var uid: String? = null
    var households = 0

    fun doc(c: RemoteCollection, id: String): Map<String, Any?>? = docs[c]?.get(id)

    private fun coll(c: RemoteCollection) = docs.getOrPut(c) { mutableMapOf() }

    override fun currentUid(): String? = uid

    override suspend fun signIn(): String = uid ?: "uid-test".also { uid = it }

    override suspend fun createHousehold(uid: String, code: String): String {
        val hid = "hh-${++households}"
        invites[code] = hid
        members.getOrPut(hid) { mutableSetOf() } += uid
        return hid
    }

    override suspend fun findHouseholdByCode(code: String): String? = invites[code]

    override suspend fun joinHousehold(householdId: String, uid: String, code: String) {
        check(invites[code] == householdId) { "PERMISSION_DENIED" }
        members.getOrPut(householdId) { mutableSetOf() } += uid
    }

    override fun observeMemberCount(householdId: String): Flow<Int> = flowOf(members[householdId]?.size ?: 0)

    override fun set(householdId: String, collection: RemoteCollection, id: String, data: Map<String, Any?>) {
        calls += Call.Set(collection, id, data)
        coll(collection)[id] = data
    }

    override fun delete(householdId: String, collection: RemoteCollection, id: String) {
        calls += Call.Delete(collection, id)
        coll(collection).remove(id)
    }

    override fun increment(householdId: String, collection: RemoteCollection, id: String, field: String, delta: Long) {
        calls += Call.Increment(collection, id, field, delta)
        val current = coll(collection)[id] ?: return // wie update(): fehlt das Dokument, passiert nichts
        coll(collection)[id] = current + (field to ((current[field] as? Number)?.toLong() ?: 0L) + delta)
    }

    override fun commit(householdId: String, ops: List<RemoteOp>) {
        check(ops.size <= RemoteStore.MAX_BATCH_OPS)
        calls += Call.Commit(ops)
        ops.forEach { op ->
            when (op) {
                is RemoteOp.Set -> coll(op.collection)[op.id] = op.data
                is RemoteOp.Delete -> coll(op.collection).remove(op.id)
            }
        }
    }

    override suspend fun listIds(householdId: String, collection: RemoteCollection): Set<String> =
        coll(collection).keys.toSet()

    override suspend fun getDocument(householdId: String, collection: RemoteCollection, id: String) =
        coll(collection)[id]

    override fun listen(
        householdId: String,
        collection: RemoteCollection,
        onSnapshot: (RemoteSnapshot) -> Unit,
    ): RemoteRegistration = RemoteRegistration { }
}
