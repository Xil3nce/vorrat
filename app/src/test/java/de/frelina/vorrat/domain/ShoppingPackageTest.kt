package de.frelina.vorrat.domain

import de.frelina.vorrat.data.db.ShoppingItemEntity
import de.frelina.vorrat.data.sync.RemoteMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class ShoppingPackageTest {

    @Test fun `Packungsgroesse wird deutsch und ohne unnoetige Nullen angezeigt`() {
        assertEquals("500 g", ShoppingLogic.packageLabel(BigDecimal("500.00"), "g"))
        assertEquals("1,5 l", ShoppingLogic.packageLabel(BigDecimal("1.5"), " l "))
        assertEquals("10", ShoppingLogic.packageLabel(BigDecimal("10"), null))
        assertEquals("1000 g", ShoppingLogic.packageLabel(BigDecimal("1E+3"), "g"))
        assertNull(ShoppingLogic.packageLabel(null, "g"))
        assertNull(ShoppingLogic.packageLabel(BigDecimal.ZERO, "g"))
    }

    @Test fun `Packungsgroesse uebersteht den Weg ueber Firestore`() {
        val item = ShoppingItemEntity(
            id = "s1", productId = null, name = "Mehl", quantity = 2, note = null, createdAt = 5,
            packageSize = BigDecimal("1.5"), unitSymbol = "kg",
        )
        assertEquals(item, RemoteMapper.shopping("s1", RemoteMapper.toMap(item)))
    }

    @Test fun `Rezepte uebersteht den Weg ueber Firestore`() {
        val item = ShoppingItemEntity(
            id = "s3", productId = null, name = "Hack", quantity = 1, note = "Bio", createdAt = 5,
            recipes = "Suppe\nGulasch, scharf",
        )
        assertEquals(item, RemoteMapper.shopping("s3", RemoteMapper.toMap(item)))
        assertNull(RemoteMapper.shopping("s4", mapOf("name" to "Brot", "recipes" to "  "))!!.recipes)
    }

    @Test fun `Menge wird nur bei Zahl plus Masse oder Volumen zerlegt`() {
        fun parsed(text: String?) = ShoppingAmount.parse(text)?.let { it.size.toPlainString() to it.unitSymbol }
        assertEquals("700" to "g", parsed("700g"))
        assertEquals("700" to "g", parsed("700 g"))
        assertEquals("1.5" to "kg", parsed("1,5 kg"))
        assertEquals("500" to "ml", parsed("500ml"))
        assertEquals("1" to "l", parsed("1 l"))
        assertEquals("1" to "l", parsed(" 1 L "))
        assertNull(parsed("3 Stück"))
        assertNull(parsed("1 EL"))
        assertNull(parsed("2 Dosen + 1 Prise"))
        assertNull(parsed("etwas"))
        assertNull(parsed("0 g"))
        assertNull(parsed("700"))
        assertNull(parsed(""))
        assertNull(parsed(null))
    }

    @Test fun `Rezepttitel werden getrennt, bereinigt und begrenzt`() {
        assertEquals("A\nB, C", ShoppingRecipes.join(listOf(" A ", "", "B, C", "A"), 300))
        assertNull(ShoppingRecipes.join(emptyList(), 300))
        assertEquals("AAAA", ShoppingRecipes.join(listOf("AAAA", "BBBBBB"), 8))
        assertEquals(listOf("A", "B, C"), ShoppingRecipes.split("A\nB, C\n"))
        assertEquals("A, B", ShoppingRecipes.label("A\nB"))
        assertNull(ShoppingRecipes.label(null))
    }

    @Test fun `Alte Eintraege ohne Packungsgroesse bleiben gueltig`() {
        val read = RemoteMapper.shopping("s2", mapOf("name" to "Brot", "quantity" to 1L, "packageSize" to "-3"))!!
        assertNull(read.packageSize)
        assertNull(read.unitSymbol)
    }
}
