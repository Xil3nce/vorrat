package de.frelina.vorrat.domain

import de.frelina.vorrat.data.db.DepartmentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DepartmentsTest {

    private val defaults = DepartmentDefaults.entities()
    private val ids = defaults.map { it.id }.toSet()

    private fun resolve(
        name: String,
        productId: String? = null,
        category: String? = null,
        manualGroup: String? = null,
        assignments: Map<String, String> = emptyMap(),
        departmentIds: Set<String> = ids,
    ) = DepartmentResolver.resolve(productId, name, category, manualGroup, assignments, departmentIds)

    // ------------------------------------------------------------------ Standard-Abteilungen

    @Test fun `13 Standard-Abteilungen mit festen IDs und lueckenloser Reihenfolge`() {
        assertEquals(13, defaults.size)
        assertEquals(defaults.indices.toList(), defaults.map { it.position })
        assertEquals(DepartmentDefaults.OTHER, defaults.last().id)
        assertEquals(defaults.size, ids.size)
        assertTrue(defaults.all { it.id.startsWith("dept_") })
    }

    @Test fun `jede Zutatengruppe hat eine Standard-Abteilung`() {
        val missing = IngredientGroups.all.filter { DepartmentDefaults.forGroup(it.id) == null }.map { it.id }
        assertTrue("Gruppen ohne Abteilung: $missing", missing.isEmpty())
        IngredientGroups.all.forEach { assertTrue(DepartmentDefaults.forGroup(it.id) in ids) }
    }

    // ------------------------------------------------------------------ DepartmentResolver

    @Test fun `Standard ueber die Zutatengruppe`() {
        assertEquals(DepartmentDefaults.PRODUCE, resolve("Tomaten"))
        assertEquals(DepartmentDefaults.DAIRY, resolve("Vollmilch"))
        assertEquals(DepartmentDefaults.PASTA, resolve("Spaghetti"))
    }

    @Test fun `unbekannter Artikel landet in Sonstiges`() {
        assertEquals(DepartmentDefaults.OTHER, resolve("Xyzzy Plonk"))
    }

    @Test fun `manuelle Gruppe des Produkts hat Vorrang vor dem Namen`() {
        assertEquals(DepartmentDefaults.CHEESE, resolve("Tomaten", manualGroup = "cheese"))
    }

    @Test fun `Zuordnung per Produkt schlaegt Zuordnung per Name und Standard`() {
        val assignments = mapOf(
            DepartmentResolver.productKey("p1") to DepartmentDefaults.FROZEN,
            DepartmentResolver.nameKey("Tomaten")!! to DepartmentDefaults.CANNED,
        )
        assertEquals(DepartmentDefaults.FROZEN, resolve("Tomaten", productId = "p1", assignments = assignments))
        assertEquals(DepartmentDefaults.CANNED, resolve("Tomaten", productId = "p2", assignments = assignments))
        assertEquals(DepartmentDefaults.CANNED, resolve("tomaten", assignments = assignments))
        assertEquals(DepartmentDefaults.PRODUCE, resolve("Gurke", assignments = assignments))
    }

    @Test fun `geloeschte Abteilung faellt auf Standard und dann auf Sonstiges zurueck`() {
        val gone = "dept_custom"
        val assignments = mapOf(DepartmentResolver.productKey("p1") to gone)
        // Zuordnung zeigt ins Leere → Standard der Gruppe
        assertEquals(DepartmentDefaults.PRODUCE, resolve("Tomaten", productId = "p1", assignments = assignments))
        // Ist auch die Standard-Abteilung weg → Sonstiges
        assertEquals(
            DepartmentDefaults.OTHER,
            resolve("Tomaten", productId = "p1", assignments = assignments, departmentIds = ids - DepartmentDefaults.PRODUCE),
        )
    }

    @Test fun `Zuordnungsschluessel`() {
        assertEquals("product:p1", DepartmentResolver.assignmentKey("p1", "Milch"))
        assertEquals("name:milch", DepartmentResolver.assignmentKey(null, "  Milch "))
        assertNull(DepartmentResolver.assignmentKey(null, "   "))
    }

    // ------------------------------------------------------------------ ShoppingBoard

    private fun input(key: String, name: String) =
        BoardInput(key = key, name = name, productId = null, category = null, manualGroup = null, payload = key)

    @Test fun `Liste gruppiert in Abteilungsreihenfolge, leere Abteilungen entfallen`() {
        val rows = ShoppingBoard.build(defaults, emptyMap(), listOf(input("a", "Vollmilch"), input("b", "Tomaten"), input("c", "Äpfel")))
        assertEquals(
            listOf("d-dept_produce", "b", "c", "d-dept_dairy", "a"),
            rows.map { it.key },
        )
        assertEquals(listOf(true, false, false, true, false), rows.map { it.isHeader })
        assertEquals(listOf(DepartmentDefaults.PRODUCE, DepartmentDefaults.DAIRY), ShoppingBoard.departmentOrder(rows))
    }

    @Test fun `geaenderte Reihenfolge der Abteilungen wirkt auf die Liste`() {
        val reordered = defaults.reversed().mapIndexed { i, d -> d.copy(position = i) }
        val rows = ShoppingBoard.build(reordered, emptyMap(), listOf(input("a", "Vollmilch"), input("b", "Tomaten")))
        assertEquals(listOf(DepartmentDefaults.DAIRY, DepartmentDefaults.PRODUCE), ShoppingBoard.departmentOrder(rows))
    }

    @Test fun `fehlendes Sonstiges steht am Ende`() {
        val without = defaults.filter { it.id != DepartmentDefaults.OTHER }
        val rows = ShoppingBoard.build(without, emptyMap(), listOf(input("a", "Xyzzy"), input("b", "Tomaten")))
        assertEquals(listOf(DepartmentDefaults.PRODUCE, DepartmentDefaults.OTHER), ShoppingBoard.departmentOrder(rows))
    }

    private fun sample(): List<BoardRow<String>> = listOf(
        BoardRow("d-A", "A", null),
        BoardRow("a1", "A", "a1"),
        BoardRow("a2", "A", "a2"),
        BoardRow("d-B", "B", null),
        BoardRow("b1", "B", "b1"),
        BoardRow("d-C", "C", null),
        BoardRow("c1", "C", "c1"),
        BoardRow("c2", "C", "c2"),
    )

    @Test fun `Eintrag in andere Abteilung ziehen aendert die Abteilung`() {
        val moved = ShoppingBoard.moveEntry(sample(), from = 1, to = 4)!!
        assertEquals(listOf("d-A", "a2", "d-B", "b1", "a1", "d-C", "c1", "c2"), moved.map { it.key })
        assertEquals("B", moved.first { it.key == "a1" }.departmentId)
    }

    @Test fun `Eintrag nie oberhalb der ersten Ueberschrift und Ueberschriften nicht per moveEntry`() {
        assertNull(ShoppingBoard.moveEntry(sample(), from = 1, to = 0))
        assertNull(ShoppingBoard.moveEntry(sample(), from = 0, to = 3))
        assertNull(ShoppingBoard.moveEntry(sample(), from = 2, to = 2))
    }

    @Test fun `Abteilung samt Inhalt nach unten und nach oben verschieben`() {
        val down = ShoppingBoard.moveDepartment(sample(), from = 0, to = 3)!!
        assertEquals(listOf("d-B", "b1", "d-A", "a1", "a2", "d-C", "c1", "c2"), down.map { it.key })
        val up = ShoppingBoard.moveDepartment(sample(), from = 5, to = 0)!!
        assertEquals(listOf("d-C", "c1", "c2", "d-A", "a1", "a2", "d-B", "b1"), up.map { it.key })
        assertNull(ShoppingBoard.moveDepartment(sample(), from = 0, to = 1)) // Ziel ist ein Eintrag
    }

    @Test fun `mergeOrder behaelt die Plaetze ausgeblendeter Abteilungen`() {
        val all = listOf("A", "B", "C", "D")
        // B und D leer/ausgeblendet; sichtbar sind A und C – vertauscht
        assertEquals(listOf("C", "B", "A", "D"), ShoppingBoard.mergeOrder(all, listOf("C", "A")))
        // Unbekannte sichtbare Abteilung kommt ans Ende
        assertEquals(listOf("A", "B", "C", "D", "X"), ShoppingBoard.mergeOrder(all, listOf("A", "X")).let { it })
    }

    @Test fun `departmentAt liefert die Ueberschrift darueber`() {
        val rows = sample()
        assertEquals("A", ShoppingBoard.departmentAt(rows, 2))
        assertEquals("B", ShoppingBoard.departmentAt(rows, 3))
        assertEquals("C", ShoppingBoard.departmentAt(rows, 7))
    }

    @Test fun `DepartmentEntity Standardnamen sind nicht leer`() {
        assertTrue(defaults.all { (it as DepartmentEntity).name.isNotBlank() })
    }
}
