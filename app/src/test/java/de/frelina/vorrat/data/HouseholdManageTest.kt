package de.frelina.vorrat.data

import de.frelina.vorrat.data.db.AuthorActivity
import de.frelina.vorrat.data.db.HistoryAction
import de.frelina.vorrat.data.db.HistoryEventEntity
import de.frelina.vorrat.data.household.HouseholdRepository
import de.frelina.vorrat.data.household.HouseholdResult
import de.frelina.vorrat.data.sync.RemoteMember
import de.frelina.vorrat.domain.HouseholdMembers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Haushalt verwalten: verlassen, Mitglied entfernen, Code erneuern, Erkennen "wurde entfernt". */
@RunWith(RobolectricTestRunner::class)
class HouseholdManageTest : DbTestBase() {

    private val repo get() = HouseholdRepository(remote, householdStore, db, writer)
    private val me = "uid-test"
    private val other = "uid-other"
    private val oldCode = "ABCDE23456"

    @Before
    fun household() {
        remote.uid = me
        remote.members[HOUSEHOLD_ID] = mutableSetOf(me, other)
        remote.invites[oldCode] = HOUSEHOLD_ID
        remote.codes[HOUSEHOLD_ID] = oldCode
    }

    @Test fun `Verlassen entfernt die Mitgliedschaft, behaelt lokale Daten und merkt sich den Code`() = runBlocking {
        entry(product(), location())
        assertEquals(HouseholdResult.Success, repo.leave())
        assertNull(householdStore.current())
        assertEquals(setOf(other), remote.members[HOUSEHOLD_ID])
        assertEquals(1, db.stockEntryDao().getAllItems().size)
        assertEquals(oldCode, householdStore.lastCode.first())
        assertFalse(householdStore.removedNotice.first())
        // Der Haushalt besteht weiter: mit dem Code kann man wieder beitreten.
        assertEquals(HouseholdResult.Success, repo.join(oldCode))
        assertEquals(HOUSEHOLD_ID, householdStore.current()!!.id)
    }

    @Test fun `Verlassen scheitert ohne Server - Zuordnung bleibt`() = runBlocking {
        remote.failHouseholdWrites = true
        assertEquals(HouseholdResult.Failed, repo.leave())
        assertEquals(HOUSEHOLD_ID, householdStore.current()!!.id)
    }

    @Test fun `Code erneuern macht den alten Code ungueltig, Mitglieder bleiben`() = runBlocking {
        assertEquals(HouseholdResult.Success, repo.renewCode())
        val newCode = householdStore.current()!!.code
        assertNotEquals(oldCode, newCode)
        assertEquals(10, newCode.length)
        assertNull(remote.invites[oldCode])
        assertEquals(HOUSEHOLD_ID, remote.invites[newCode])
        assertEquals(newCode, remote.codes[HOUSEHOLD_ID])
        assertEquals(setOf(me, other), remote.members[HOUSEHOLD_ID])
    }

    @Test fun `Code erneuern scheitert - alter Code bleibt gespeichert`() = runBlocking {
        remote.failHouseholdWrites = true
        assertEquals(HouseholdResult.Failed, repo.renewCode())
        assertEquals(oldCode, householdStore.current()!!.code)
        assertEquals(HOUSEHOLD_ID, remote.invites[oldCode])
    }

    @Test fun `Mitglied entfernen erneuert den Code - das entfernte Handy kommt nicht wieder rein`() = runBlocking {
        assertEquals(HouseholdResult.Success, repo.removeMember(other))
        assertEquals(setOf(me), remote.members[HOUSEHOLD_ID])
        assertNotEquals(oldCode, householdStore.current()!!.code)
        assertNull(remote.invites[oldCode])
        // Wiederbeitritt mit dem alten Code (wie ihn das entfernte Handy versuchen würde) wird abgelehnt.
        val rejoin = runCatching { remote.joinHousehold(HOUSEHOLD_ID, other, oldCode) }
        assertTrue(rejoin.isFailure)
    }

    @Test fun `Sich selbst entfernen ist Verlassen - der Code bleibt`() = runBlocking {
        assertEquals(HouseholdResult.Success, repo.removeMember(me))
        assertNull(householdStore.current())
        assertEquals(HOUSEHOLD_ID, remote.invites[oldCode])
    }

    @Test fun `Entfernt worden - Zuordnung wird nur bei eindeutiger Antwort des Servers geloest`() = runBlocking {
        entry(product(), location())
        // Noch Mitglied.
        assertFalse(repo.dropIfRemoved())
        // Offline / unbekannt: nichts tun.
        remote.members[HOUSEHOLD_ID]!!.remove(me)
        remote.memberAnswerUnknown = true
        assertFalse(repo.dropIfRemoved())
        assertEquals(HOUSEHOLD_ID, householdStore.current()!!.id)
        // Andere Anmeldung (Wiederanmeldung läuft noch): nichts tun.
        remote.memberAnswerUnknown = false
        remote.uid = "uid-neu"
        assertFalse(repo.dropIfRemoved())
        // Eindeutig kein Mitglied mehr.
        remote.uid = me
        assertTrue(repo.dropIfRemoved())
        assertNull(householdStore.current())
        assertTrue(householdStore.removedNotice.first())
        assertEquals(1, db.stockEntryDao().getAllItems().size)
        // Neuer Haushalt: Hinweis verschwindet.
        assertEquals(HouseholdResult.Success, repo.create())
        assertFalse(householdStore.removedNotice.first())
    }

    @Test fun `Vom anderen Handy erneuerter Code wird uebernommen`() = runBlocking {
        repo.adoptCode(HOUSEHOLD_ID, "zzzzz-22222")
        assertEquals("ZZZZZ22222", householdStore.current()!!.code)
        repo.adoptCode("anderer-haushalt", "YYYYY33333")
        repo.adoptCode(HOUSEHOLD_ID, "kaputt")
        assertEquals("ZZZZZ22222", householdStore.current()!!.code)
    }

    @Test fun `Mitgliederliste - dieses Handy zuerst, Namen und Aktivitaet aus dem Verlauf`() = runBlocking {
        fun event(uid: String, name: String?, at: Long) = HistoryEventEntity(
            createdAt = at, action = HistoryAction.ADDED, authorUid = uid, authorName = name, productId = null,
            productName = "Milch", quantity = 1, quantityTypeSymbol = "Stck.", entryId = null,
        )
        db.historyDao().upsert(event(other, "Melina", 5_000))
        db.historyDao().upsert(event(other, "Meli", 9_000))
        db.historyDao().upsert(event(me, null, 7_000))
        val members = repo.members.first()!!
        assertEquals(listOf(me, other), members.map { it.uid })
        assertEquals(listOf(true, false), members.map { it.isMe })
        assertEquals(listOf(null, "Meli"), members.map { it.name })
        assertEquals(listOf(7_000L, 9_000L), members.map { it.lastActive })
        assertEquals(2, repo.memberCount.first())
    }

    @Test fun `Mitgliederliste - eigener Name aus den Einstellungen, Unbekannte ohne Verlauf`() {
        val members = HouseholdMembers.build(
            members = listOf(RemoteMember("geist", 100), RemoteMember("ich", 300), RemoteMember("b", null), RemoteMember("a", 200)),
            myUid = "ich",
            activity = listOf(AuthorActivity("a", " ", 50), AuthorActivity("a", "Anna", 40)),
            myName = " Franz ",
        )
        assertEquals(listOf("ich", "geist", "a", "b"), members.map { it.uid })
        assertEquals(listOf("Franz", null, "Anna", null), members.map { it.name })
        assertNull(members[1].lastActive)
    }
}
