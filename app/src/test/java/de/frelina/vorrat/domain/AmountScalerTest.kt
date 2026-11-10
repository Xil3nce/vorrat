package de.frelina.vorrat.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmountScalerTest {

    private fun s(amount: String?, factor: Double) = AmountScaler.scale(amount, factor)

    @Test
    fun `Faktor aus Personenzahl`() {
        assertEquals(1.5, AmountScaler.factor(4, 6), 1e-9)
        assertEquals(0.5, AmountScaler.factor(2, 1), 1e-9)
        assertEquals(1.0, AmountScaler.factor(null, 3), 1e-9)
    }

    @Test
    fun `Faktor 1 und Angaben ohne Zahl bleiben unverändert`() {
        assertEquals("400 g", s("400 g", 1.0))
        assertEquals("zum Servieren", s("zum Servieren", 2.0))
        assertEquals("etwas", s("etwas", 0.5))
        assertNull(s(null, 2.0))
        assertEquals("", s("", 2.0))
    }

    @Test
    fun `Gramm und Milliliter werden sinnvoll gerundet`() {
        assertEquals("600 g", s("400 g", 1.5))
        assertEquals("130 g", s("200 g", 2.0 / 3))
        assertEquals("65 ml", s("100 ml", 2.0 / 3))
        assertEquals("13 g", s("20 g", 2.0 / 3))
        assertEquals("1 g", s("1 g", 0.25))
    }

    @Test
    fun `Kilogramm und Liter mit einer Nachkommastelle`() {
        assertEquals("1,5 kg", s("1 kg", 1.5))
        assertEquals("0,8 l", s("1,5 l", 0.5))
        assertEquals("2 l", s("1 l", 2.0))
    }

    @Test
    fun `Stückzahlen auf halbe und viertel runden`() {
        assertEquals("2", s("1", 2.0))
        assertEquals("1½", s("1", 1.5))
        assertEquals("½", s("1", 0.5))
        assertEquals("¼", s("1", 0.25))
        assertEquals("¼", s("1", 0.1))
        assertEquals("3 EL", s("2 EL", 1.5))
        assertEquals("1½ TL", s("1 TL", 1.5))
    }

    @Test
    fun `Brüche, Dezimalzahlen und Spannen werden erkannt`() {
        assertEquals("1 Dose", s("½ Dose", 2.0))
        assertEquals("1 Dose", s("1/2 Dose", 2.0))
        assertEquals("3 EL", s("1 ½ EL", 2.0))
        assertEquals("3 EL", s("1,5 EL", 2.0))
        assertEquals("4–6 EL", s("2-3 EL", 2.0))
        assertEquals("1–1½", s("2–3", 0.5))
    }

    @Test
    fun `Einheit wechselt zwischen Singular und Plural`() {
        assertEquals("2 Zehen", s("1 Zehe", 2.0))
        assertEquals("1 Zehe", s("2 Zehen", 0.5))
        assertEquals("½ Dose", s("1 Dose", 0.5))
        assertEquals("3 Dosen", s("2 Dosen", 1.5))
        assertEquals("2 kleine Dosen", s("1 kleine Dose", 2.0))
        assertEquals("2 Köpfe", s("1 Kopf", 2.0))
        assertEquals("2 zum Bestreichen", s("1 zum Bestreichen", 2.0))
    }
}
