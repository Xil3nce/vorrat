package de.frelina.vorrat.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class ReleaseNotesAndTrashLogicTest {

    private fun release(tag: String, body: String?, draft: Boolean = false, prerelease: Boolean = false) =
        GitHubRelease(tagName = tag, body = body, draft = draft, prerelease = prerelease)

    private val releases = listOf(
        release("v2.17.0", "Zukunft"),
        release("v2.16.1", "- Fix A\n- Fix B"),
        release("v2.16.0", "## Neu\n* **Papierkorb** für Einträge\r\n1. `Kachel`"),
        release("v2.16.0-rc", "Vorab", prerelease = true),
        release("v2.15.5", "Entwurf", draft = true),
        release("v2.15.0", "Haushalt verwalten"),
        release("v2.14.0", ""),
    )

    @Test fun `Zeigt alle Versionen seit der zuletzt gesehenen bis zur installierten, neueste zuerst`() {
        val notes = ReleaseNotes.between(releases, lastSeen = "2.15.0", current = "2.16.1")
        assertEquals(listOf("2.16.1", "2.16.0"), notes.map { it.version })
        assertEquals(listOf(NoteLine("Fix A", true), NoteLine("Fix B", true)), notes[0].items)
    }

    @Test fun `Ohne zuletzt gesehene Version nur die installierte`() {
        val notes = ReleaseNotes.between(releases, lastSeen = null, current = "2.15.0")
        assertEquals(listOf("2.15.0"), notes.map { it.version })
        assertEquals(listOf(NoteLine("Haushalt verwalten", false)), notes.single().items)
    }

    @Test fun `Entwuerfe, Vorabversionen und leere Notizen fallen weg`() {
        assertTrue(ReleaseNotes.between(releases, lastSeen = "2.13.0", current = "2.14.0").isEmpty())
        assertEquals(listOf("2.15.0"), ReleaseNotes.between(releases, "2.14.0", "2.15.9").map { it.version })
    }

    @Test fun `Markdown wird zu schlichten Zeilen`() {
        assertEquals(
            listOf(NoteLine("Neu", false), NoteLine("Papierkorb für Einträge", true), NoteLine("Kachel", true)),
            ReleaseNotes.parse("## Neu\n* **Papierkorb** für Einträge\r\n1. `Kachel`\n---\n"),
        )
    }

    @Test fun `Papierkorb behaelt 30 Tage`() {
        val day = TimeUnit.DAYS.toMillis(1)
        val deleted = 1_000_000L
        assertEquals(30, TrashLogic.daysLeft(deleted, deleted))
        assertEquals(30, TrashLogic.daysLeft(deleted, deleted + 1))
        assertEquals(1, TrashLogic.daysLeft(deleted, deleted + 29 * day + 1))
        assertEquals(0, TrashLogic.daysLeft(deleted, deleted + 30 * day))
        assertFalse(TrashLogic.isExpired(deleted, deleted + 30 * day - 1))
        assertTrue(TrashLogic.isExpired(deleted, deleted + 30 * day))
    }
}
