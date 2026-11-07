package de.frelina.vorrat.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class InviteCodeTest {

    @Test fun `Alphabet ohne verwechselbare Zeichen`() {
        listOf('0', 'O', '1', 'I', 'L').forEach { assertFalse("$it im Alphabet", it in InviteCode.ALPHABET) }
        assertEquals(31, InviteCode.ALPHABET.toSet().size)
    }

    @Test fun `erzeugte Codes haben 10 Zeichen aus dem Alphabet und sind gueltig`() {
        val rnd = Random(42)
        val codes = List(500) { InviteCode.generate(rnd) }
        codes.forEach { code ->
            assertEquals(10, code.length)
            assertTrue(code.all { it in InviteCode.ALPHABET })
            assertEquals(code, InviteCode.normalize(code))
        }
        assertTrue("Codes sollten sich unterscheiden", codes.toSet().size > 490)
    }

    @Test fun `Anzeige als XXXXX-XXXXX`() {
        assertEquals("ABCDE-23456", InviteCode.format("ABCDE23456"))
        assertEquals("ABCDE-23456", InviteCode.format("abcde 23456"))
    }

    @Test fun `Eingabe tolerant gegenueber Gross-Klein, Bindestrich und Leerzeichen`() {
        assertEquals("ABCDE23456", InviteCode.normalize("abcde-23456"))
        assertEquals("ABCDE23456", InviteCode.normalize("  AbCdE 234 56 "))
        assertEquals("ABCDE23456", InviteCode.normalize("ABCDE–23456"))
    }

    @Test fun `ungueltige Eingaben`() {
        assertNull(InviteCode.normalize(""))
        assertNull(InviteCode.normalize("ABCDE-2345")) // zu kurz
        assertNull(InviteCode.normalize("ABCDE-234567")) // zu lang
        assertNull(InviteCode.normalize("ABCDE-2345O")) // O nicht im Alphabet
        assertNull(InviteCode.normalize("ABCDE-23451")) // 1 nicht im Alphabet
        assertNull(InviteCode.normalize("ABCDE_23456"))
    }
}
