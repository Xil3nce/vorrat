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

    @Test fun `Alte Eintraege ohne Packungsgroesse bleiben gueltig`() {
        val read = RemoteMapper.shopping("s2", mapOf("name" to "Brot", "quantity" to 1L, "packageSize" to "-3"))!!
        assertNull(read.packageSize)
        assertNull(read.unitSymbol)
    }
}
