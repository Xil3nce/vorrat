package de.frelina.vorrat.data

import de.frelina.vorrat.data.db.DepartmentAssignmentEntity
import de.frelina.vorrat.data.db.DepartmentEntity
import de.frelina.vorrat.data.db.MealPlanEntity
import de.frelina.vorrat.data.db.StapleEntity
import de.frelina.vorrat.data.repo.DepartmentNameResult
import de.frelina.vorrat.data.repo.DepartmentRepository
import de.frelina.vorrat.data.repo.MealPlanRepository
import de.frelina.vorrat.data.repo.StapleRepository
import de.frelina.vorrat.data.sync.RemoteChange
import de.frelina.vorrat.data.sync.RemoteCollection
import de.frelina.vorrat.data.sync.RemoteMapper
import de.frelina.vorrat.data.sync.SyncApplier
import de.frelina.vorrat.domain.DepartmentDefaults
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class DepartmentsAndMealPlanDataTest : DbTestBase() {

    private val departments get() = DepartmentRepository(db.departmentDao(), db.departmentAssignmentDao(), writer)
    private val plan get() = MealPlanRepository(db.mealPlanDao(), writer)
    private val staples get() = StapleRepository(db.stapleDao(), writer)
    private val applier get() = SyncApplier(db)

    private fun ids(c: RemoteCollection) = remote.docs[c].orEmpty().keys

    // ---------------------------------------------------------------- Standard-Abteilungen

    @Test fun `ensureDefaults legt 13 Abteilungen an und ist idempotent`() = runBlocking {
        assertTrue(departments.ensureDefaults())
        assertEquals(13, db.departmentDao().count())
        assertEquals(DepartmentDefaults.ids, ids(RemoteCollection.DEPARTMENTS))
        val before = db.departmentDao().getAll()

        // zweiter Aufruf: nichts passiert
        assertFalse(departments.ensureDefaults())
        assertEquals(before, db.departmentDao().getAll())

        // auch eine Umbenennung bleibt erhalten
        departments.rename(DepartmentDefaults.MEAT, "Metzger")
        assertFalse(departments.ensureDefaults())
        assertEquals("Metzger", db.departmentDao().getById(DepartmentDefaults.MEAT)!!.name)
    }

    @Test fun `ensureDefaults mit festen IDs erzeugt auf zwei Geraeten dieselben Dokumente`() = runBlocking {
        departments.ensureDefaults()
        val first = remote.docs.getValue(RemoteCollection.DEPARTMENTS).toMap()
        // "zweites Handy": leere lokale DB, schreibt in denselben Remote-Speicher
        db.departmentDao().deleteAll()
        departments.ensureDefaults()
        assertEquals(first, remote.docs.getValue(RemoteCollection.DEPARTMENTS))
        assertEquals(13, remote.docs.getValue(RemoteCollection.DEPARTMENTS).size)
    }

    @Test fun `Standard-Abteilungen kommen auch aus dem Sync ohne Dubletten`() = runBlocking {
        departments.ensureDefaults()
        val docs = remote.docs.getValue(RemoteCollection.DEPARTMENTS)
        db.departmentDao().deleteAll()
        applier.apply(RemoteCollection.DEPARTMENTS, docs.map { (id, data) -> RemoteChange.Upsert(id, data) }, docs.keys)
        assertEquals(DepartmentDefaults.entities(), db.departmentDao().getAll())
        assertFalse(departments.ensureDefaults()) // schon da → kein Überschreiben
    }

    // ---------------------------------------------------------------- Verwalten

    @Test fun `Abteilungen hinzufuegen, umbenennen, Namen pruefen`() = runBlocking {
        departments.ensureDefaults()
        assertEquals(DepartmentNameResult.OK, departments.add("Bio-Laden"))
        val added = db.departmentDao().getAll().last()
        assertEquals("Bio-Laden", added.name)
        assertEquals(13, added.position)
        assertEquals("Bio-Laden", remote.doc(RemoteCollection.DEPARTMENTS, added.id)!!["name"])

        assertEquals(DepartmentNameResult.DUPLICATE, departments.add("bio-laden"))
        assertEquals(DepartmentNameResult.DUPLICATE, departments.rename(added.id, "Käse"))
        assertEquals(DepartmentNameResult.INVALID, departments.add("   "))
        assertEquals(DepartmentNameResult.INVALID, departments.add("x".repeat(41)))
        assertEquals(DepartmentNameResult.OK, departments.rename(added.id, "Bio"))
        assertEquals("Bio", db.departmentDao().getById(added.id)!!.name)
        // Umbenennen auf den eigenen Namen (andere Schreibweise) ist erlaubt
        assertEquals(DepartmentNameResult.OK, departments.rename(added.id, "BIO"))
    }

    @Test fun `Reihenfolge speichern verteilt Positionen neu`() = runBlocking {
        departments.ensureDefaults()
        departments.saveOrder(listOf(DepartmentDefaults.DRINKS, DepartmentDefaults.PRODUCE))
        val order = db.departmentDao().getAll().map { it.id }
        assertEquals(DepartmentDefaults.DRINKS, order[0])
        assertEquals(DepartmentDefaults.PRODUCE, order[1])
        assertEquals(DepartmentDefaults.BAKERY, order[2]) // Rest in bisheriger Reihenfolge
        assertEquals((0 until 13).toList(), db.departmentDao().getAll().map { it.position })
        assertEquals(0L, remote.doc(RemoteCollection.DEPARTMENTS, DepartmentDefaults.DRINKS)!!["position"])
    }

    @Test fun `Loeschen entfernt Abteilung und Zuordnungen, Sonstiges bleibt`() = runBlocking {
        departments.ensureDefaults()
        departments.assign("product:p1", DepartmentDefaults.CHEESE)
        departments.assign("name:xyz", DepartmentDefaults.CHEESE)
        departments.assign("name:abc", DepartmentDefaults.DRINKS)

        assertTrue(departments.delete(DepartmentDefaults.CHEESE))
        assertNull(db.departmentDao().getById(DepartmentDefaults.CHEESE))
        assertNull(remote.doc(RemoteCollection.DEPARTMENTS, DepartmentDefaults.CHEESE))
        assertEquals(listOf("name:abc"), db.departmentAssignmentDao().getAll().map { it.key })
        assertEquals(setOf("name:abc"), ids(RemoteCollection.DEPARTMENT_ASSIGNMENTS))

        assertFalse(departments.delete(DepartmentDefaults.OTHER))
        assertNotNull(db.departmentDao().getById(DepartmentDefaults.OTHER))
        assertFalse(departments.delete("gibt-es-nicht"))
    }

    @Test fun `Auf Standard zuruecksetzen stellt Namen, Reihenfolge und geloeschte wieder her`() = runBlocking {
        departments.ensureDefaults()
        departments.rename(DepartmentDefaults.MEAT, "Metzger")
        departments.delete(DepartmentDefaults.CHEESE)
        departments.add("Bio-Laden")
        departments.saveOrder(listOf(DepartmentDefaults.DRINKS))

        departments.resetToDefaults()

        val all = db.departmentDao().getAll()
        assertEquals(DepartmentDefaults.entities() + DepartmentEntity(all.last().id, "Bio-Laden", 13), all)
        assertEquals("Fleisch, Wurst & Fisch", db.departmentDao().getById(DepartmentDefaults.MEAT)!!.name)
        assertEquals(14, ids(RemoteCollection.DEPARTMENTS).size)
    }

    // ---------------------------------------------------------------- Zuordnungen

    @Test fun `Zuordnung wird gespeichert, ueberschrieben und synchronisiert`() = runBlocking {
        departments.ensureDefaults()
        departments.assign("product:p1", DepartmentDefaults.FROZEN)
        departments.assign("product:p1", DepartmentDefaults.DRINKS)
        assertEquals(mapOf("product:p1" to DepartmentDefaults.DRINKS), departments.observeAssignments().first())
        val doc = remote.doc(RemoteCollection.DEPARTMENT_ASSIGNMENTS, "product:p1")!!
        assertEquals(DepartmentDefaults.DRINKS, doc["departmentId"])
        // ungültige Schlüssel werden nicht gespeichert
        departments.assign("irgendwas", DepartmentDefaults.DRINKS)
        departments.assign("name:a/b", DepartmentDefaults.DRINKS)
        assertEquals(1, db.departmentAssignmentDao().getAll().size)
    }

    // ---------------------------------------------------------------- RemoteMapper

    @Test fun `RemoteMapper Rundreise fuer alle neuen Sammlungen`() {
        val d = DepartmentEntity("dept_x", "Bio", 3)
        assertEquals(d, RemoteMapper.department(d.id, RemoteMapper.toMap(d)))
        val a = DepartmentAssignmentEntity("name:milch", "dept_dairy", 99)
        assertEquals(a, RemoteMapper.departmentAssignment(a.key, RemoteMapper.toMap(a)))
        val m = MealPlanEntity("m1", LocalDate.of(2026, 11, 12), "builtin:Suppe", 4, 5)
        assertEquals(m, RemoteMapper.mealPlan(m.id, RemoteMapper.toMap(m)))
        val s = StapleEntity("group:spices", "Salz, Pfeffer & Gewürze", 7)
        assertEquals(s, RemoteMapper.staple(s.key, RemoteMapper.toMap(s)))
    }

    @Test fun `RemoteMapper lehnt kaputte Dokumente ab`() {
        assertNull(RemoteMapper.department("d", mapOf("name" to "  ", "position" to 1L)))
        assertNull(RemoteMapper.department("d", mapOf("position" to 1L)))
        assertNull(RemoteMapper.departmentAssignment("kaputt", mapOf("departmentId" to "x")))
        assertNull(RemoteMapper.departmentAssignment("name:a/b", mapOf("departmentId" to "x")))
        assertNull(RemoteMapper.departmentAssignment("name:milch", mapOf("departmentId" to " ")))
        assertNull(RemoteMapper.mealPlan("m", mapOf("date" to "kein Datum", "recipeId" to "r", "persons" to 2L)))
        assertNull(RemoteMapper.mealPlan("m", mapOf("date" to "2026-11-12", "persons" to 2L)))
        assertNull(RemoteMapper.staple("a/b", mapOf("label" to "x")))
        assertNull(RemoteMapper.staple("salz", mapOf("label" to " ")))
        // unplausible Personenzahl → 1; zu langer Name wird gekürzt
        assertEquals(1, RemoteMapper.mealPlan("m", mapOf("date" to "2026-11-12", "recipeId" to "r", "persons" to 500L))!!.persons)
        assertEquals(40, RemoteMapper.department("d", mapOf("name" to "x".repeat(80), "position" to 0L))!!.name.length)
    }

    // ---------------------------------------------------------------- SyncApplier

    @Test fun `SyncApplier uebernimmt und loescht alle neuen Sammlungen`() = runBlocking {
        val d = DepartmentEntity("dept_x", "Bio", 3)
        val a = DepartmentAssignmentEntity("name:milch", "dept_x", 99)
        val m = MealPlanEntity("m1", LocalDate.of(2026, 11, 12), "builtin:Suppe", 4, 5)
        val s = StapleEntity("salz", "Salz", 7)
        applier.apply(RemoteCollection.DEPARTMENTS, listOf(RemoteChange.Upsert(d.id, RemoteMapper.toMap(d))))
        applier.apply(RemoteCollection.DEPARTMENT_ASSIGNMENTS, listOf(RemoteChange.Upsert(a.key, RemoteMapper.toMap(a))))
        applier.apply(RemoteCollection.MEAL_PLAN, listOf(RemoteChange.Upsert(m.id, RemoteMapper.toMap(m))))
        applier.apply(RemoteCollection.STAPLES, listOf(RemoteChange.Upsert(s.key, RemoteMapper.toMap(s))))
        assertEquals(listOf(d), db.departmentDao().getAll())
        assertEquals(listOf(a), db.departmentAssignmentDao().getAll())
        assertEquals(listOf(m), db.mealPlanDao().getBetween(m.date, m.date))
        assertEquals(listOf(s), db.stapleDao().getAll())

        // Änderung und Löschen vom anderen Handy
        applier.apply(RemoteCollection.DEPARTMENTS, listOf(RemoteChange.Upsert(d.id, RemoteMapper.toMap(d.copy(name = "Bio 2")))))
        assertEquals("Bio 2", db.departmentDao().getById(d.id)!!.name)
        applier.apply(RemoteCollection.DEPARTMENTS, listOf(RemoteChange.Removed(d.id)))
        applier.apply(RemoteCollection.DEPARTMENT_ASSIGNMENTS, listOf(RemoteChange.Removed(a.key)))
        applier.apply(RemoteCollection.MEAL_PLAN, listOf(RemoteChange.Removed(m.id)))
        applier.apply(RemoteCollection.STAPLES, listOf(RemoteChange.Removed(s.key)))
        assertTrue(db.departmentDao().getAll().isEmpty())
        assertTrue(db.departmentAssignmentDao().getAll().isEmpty())
        assertTrue(db.mealPlanDao().getBetween(m.date, m.date).isEmpty())
        assertTrue(db.stapleDao().getAll().isEmpty())
    }

    @Test fun `SyncApplier ignoriert kaputte Dokumente der neuen Sammlungen`() = runBlocking {
        applier.apply(RemoteCollection.DEPARTMENTS, listOf(RemoteChange.Upsert("d", mapOf("position" to 1L))))
        applier.apply(RemoteCollection.DEPARTMENT_ASSIGNMENTS, listOf(RemoteChange.Upsert("kaputt", mapOf("departmentId" to "x"))))
        applier.apply(RemoteCollection.MEAL_PLAN, listOf(RemoteChange.Upsert("m", mapOf("date" to "x", "recipeId" to "r"))))
        applier.apply(RemoteCollection.STAPLES, listOf(RemoteChange.Upsert("s", mapOf("label" to ""))))
        assertTrue(db.departmentDao().getAll().isEmpty())
        assertTrue(db.departmentAssignmentDao().getAll().isEmpty())
        assertTrue(db.stapleDao().getAll().isEmpty())
        assertTrue(db.mealPlanDao().getAll().isEmpty())
    }

    @Test fun `neue Sammlungen sind Datensammlungen und im Backup-Umfang`() {
        listOf(
            RemoteCollection.DEPARTMENTS, RemoteCollection.DEPARTMENT_ASSIGNMENTS,
            RemoteCollection.MEAL_PLAN, RemoteCollection.STAPLES,
        ).forEach {
            assertTrue("$it in DATA", it in RemoteCollection.DATA)
            assertTrue("$it in BULK", it in RemoteCollection.BULK)
        }
        assertEquals("departments", RemoteCollection.DEPARTMENTS.path)
        assertEquals("departmentAssignments", RemoteCollection.DEPARTMENT_ASSIGNMENTS.path)
        assertEquals("mealPlan", RemoteCollection.MEAL_PLAN.path)
        assertEquals("staples", RemoteCollection.STAPLES.path)
    }

    // ---------------------------------------------------------------- Wochenplan

    @Test fun `Wochenplan hinzufuegen, verschieben, entfernen und wiederherstellen`() = runBlocking {
        val mon = LocalDate.of(2026, 11, 9)
        val e = plan.add(mon, "builtin:Suppe", 4)
        assertEquals(listOf(e), plan.observeBetween(mon, mon.plusDays(6)).first())
        assertEquals(4L, remote.doc(RemoteCollection.MEAL_PLAN, e.id)!!["persons"])
        assertEquals("2026-11-09", remote.doc(RemoteCollection.MEAL_PLAN, e.id)!!["date"])

        plan.move(e.id, mon.plusDays(2))
        assertEquals(mon.plusDays(2), db.mealPlanDao().getById(e.id)!!.date)
        assertEquals("2026-11-11", remote.doc(RemoteCollection.MEAL_PLAN, e.id)!!["date"])

        plan.setPersons(e.id, 0)
        assertEquals(1, db.mealPlanDao().getById(e.id)!!.persons) // mindestens 1

        val removed = plan.remove(e.id)!!
        assertNull(db.mealPlanDao().getById(e.id))
        assertNull(remote.doc(RemoteCollection.MEAL_PLAN, e.id))
        plan.restore(removed)
        assertEquals(removed, db.mealPlanDao().getById(e.id))
        assertNotNull(remote.doc(RemoteCollection.MEAL_PLAN, e.id))
        assertNull(plan.remove("gibt-es-nicht"))
    }

    @Test fun `Wochenplan liefert nur den Zeitraum, auch fuer geloeschte Rezepte`() = runBlocking {
        val mon = LocalDate.of(2026, 11, 9)
        plan.add(mon.minusDays(1), "r1", 2)
        plan.add(mon, "geloeschtes-rezept", 2)
        plan.add(mon.plusDays(6), "r2", 2)
        plan.add(mon.plusDays(7), "r3", 2)
        val week = plan.getBetween(mon, mon.plusDays(6))
        assertEquals(listOf("geloeschtes-rezept", "r2"), week.map { it.recipeId })
    }

    @Test fun `Immer zuhause hinzufuegen und entfernen`() = runBlocking {
        staples.add("group:spices", "  Salz, Pfeffer & Gewürze ")
        staples.add("xyzzy", "Xyzzy")
        staples.add("a/b", "ungültig")
        staples.add("leer", "   ")
        assertEquals(listOf("group:spices", "xyzzy"), staples.getAll().map { it.key }.sorted())
        assertEquals("Salz, Pfeffer & Gewürze", staples.observeAll().first().first { it.key == "group:spices" }.label)
        assertEquals(setOf("group:spices", "xyzzy"), ids(RemoteCollection.STAPLES))

        staples.remove("xyzzy")
        assertEquals(listOf("group:spices"), staples.getAll().map { it.key })
        assertEquals(setOf("group:spices"), ids(RemoteCollection.STAPLES))
    }
}
