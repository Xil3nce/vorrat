package de.frelina.vorrat.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeTextImportTest {

    @Test
    fun `gedrucktes Rezept mit Überschriften`() {
        val r = RecipeTextParser.parse(
            """
            PFANNKUCHEN
            Zubereitungszeit: 20 Min.
            Zutaten für 4 Personen
            250 g Mehl
            1/2 l Milch
            3 Eier
            1 Prise Salz
            2 EL Öl zum Braten

            Zubereitung
            1. Mehl, Milch, Eier und Salz zu einem glatten
            Teig verrühren.
            2. Den Teig 10 Minuten quellen lassen.
            3. Öl in einer Pfanne erhitzen und nacheinander dünne Pfann-
            kuchen backen.
            """.trimIndent(),
        )
        assertEquals("", r.id)
        assertEquals("Pfannkuchen", r.title)
        assertEquals(20, r.minutes)
        assertEquals(4, r.servings)
        assertEquals(
            listOf("250 g" to "Mehl", "½ l" to "Milch", "3" to "Eier", "1 Prise" to "Salz", "2 EL" to "Öl zum Braten"),
            r.ingredients.map { it.amount to it.name },
        )
        assertEquals(
            listOf(
                "Mehl, Milch, Eier und Salz zu einem glatten Teig verrühren.",
                "Den Teig 10 Minuten quellen lassen.",
                "Öl in einer Pfanne erhitzen und nacheinander dünne Pfannkuchen backen.",
            ),
            r.steps,
        )
        assertFalse(r.vegetarian)
    }

    @Test
    fun `Zutaten bekommen automatisch eine Gruppe und Mengen passen zum AmountScaler`() {
        val r = RecipeTextParser.parse("Reispfanne\nZutaten\n250 g Reis\n1 1/2 TL Salz\nZubereitung\nReis kochen.")
        assertEquals(IngredientMatcher.matchName("Reis"), r.ingredients[0].group)
        assertEquals("1 ½ TL", r.ingredients[1].amount)
        assertEquals("500 g", AmountScaler.scale(r.ingredients[0].amount, 2.0))
        assertEquals("3 TL", AmountScaler.scale(r.ingredients[1].amount, 2.0))
    }

    @Test
    fun `handgeschriebenes Rezept ohne Überschriften`() {
        val r = RecipeTextParser.parse(
            """
            Omas Apfelkuchen
            - 200g Butter
            - 3 Eier
            - etwas Zimt
            - eine Prise Salz
            - 1 kg Äpfel
            Butter und Eier schaumig rühren, dann das Mehl unterheben.
            Äpfel schälen und auf dem Teig verteilen.
            Bei 180 Grad 45 Minuten backen.
            """.trimIndent(),
        )
        assertEquals("Omas Apfelkuchen", r.title)
        assertNull(r.minutes)
        assertNull(r.servings)
        assertEquals(
            listOf("200 g" to "Butter", "3" to "Eier", "etwas" to "Zimt", "1 Prise" to "Salz", "1 kg" to "Äpfel"),
            r.ingredients.map { it.amount to it.name },
        )
        assertEquals(3, r.steps.size)
        assertEquals("Bei 180 Grad 45 Minuten backen.", r.steps[2])
    }

    @Test
    fun `Mengen und Einheiten`() {
        fun one(line: String) = RecipeTextParser.parse("Titel\nZutaten\n$line").ingredients.single().let { it.amount to it.name }
        assertEquals("2-3 EL" to "Olivenöl", one("2-3 EL Olivenöl"))
        assertEquals("1,5 kg" to "Kartoffeln", one("1,5 kg Kartoffeln"))
        assertEquals("1 kl. Dose" to "Mais", one("1 kl. Dose Mais"))
        assertEquals("1 Pck." to "Vanillezucker", one("1 Pck. Vanillezucker"))
        assertEquals("1" to "kleine Zwiebel", one("1 kleine Zwiebel"))
        assertEquals("2" to "große Eier", one("2 große Eier"))
        assertEquals("6" to "Blätterteigplatten", one("6 Blätterteigplatten"))
        assertEquals("1" to "Gurke", one("1 Gurke"))
        assertEquals("2" to "Limetten", one("2 Limetten"))
        assertEquals("½" to "Zitrone", one("½ Zitrone"))
        assertEquals("2" to "Eier", one("zwei Eier"))
        assertEquals(null to "Salz und Pfeffer", one("Salz und Pfeffer"))
        assertEquals("ein paar" to "Basilikumblätter", one("• ein paar Basilikumblätter"))
    }

    @Test
    fun `Zeit und Portionen`() {
        fun parse(meta: String) = RecipeTextParser.parse("Suppe\n$meta\nZutaten\n1 l Brühe")
        assertEquals(80, parse("Gesamtzeit: 1 Std. 20 Min.\nArbeitszeit: 15 Min.").minutes)
        assertEquals(45, parse("Arbeitszeit ca. 15 Min.\nKochzeit ca. 30 Min.").minutes)
        assertEquals(90, parse("Backzeit: 1,5 Stunden").minutes)
        assertEquals(30, parse("4 Portionen · 30 Min.").minutes)
        assertEquals(4, parse("4 Portionen · 30 Min.").servings)
        assertEquals(6, parse("(für 6 Personen)").servings)
        assertEquals(2, parse("Portionen: 2").servings)
        // Angaben-Zeilen sind weder Titel noch Zutat
        val r = parse("Zubereitungszeit: 25 Minuten\nfür 2 Personen")
        assertEquals("Suppe", r.title)
        assertEquals(listOf("Brühe"), r.ingredients.map { it.name })
    }

    @Test
    fun `Portionsangabe im Titel bleibt im Titel`() {
        val r = RecipeTextParser.parse("Lasagne für 4 Personen\nZutaten\n500 g Hackfleisch")
        assertEquals("Lasagne für 4 Personen", r.title)
        assertEquals(4, r.servings)
    }

    @Test
    fun `Zwischenüberschriften der Zutaten werden übersprungen`() {
        val r = RecipeTextParser.parse(
            "Kuchen\nZutaten:\nFür den Teig:\n200 g Mehl\nFür den Belag:\n500 g Quark\nZubereitung:\nAlles verrühren.",
        )
        assertEquals(listOf("Mehl", "Quark"), r.ingredients.map { it.name })
        assertEquals(listOf("Alles verrühren."), r.steps)
    }

    @Test
    fun `Mengen und Zutaten in getrennten Zeilen werden verbunden`() {
        // abwechselnd
        val a = RecipeTextParser.parse("Brot\nZutaten\n500 g\nMehl\n1 Würfel\nHefe\nZubereitung\nKneten.")
        assertEquals(listOf("500 g" to "Mehl", "1 Würfel" to "Hefe"), a.ingredients.map { it.amount to it.name })
        // spaltenweise gelesen: erst alle Mengen, dann alle Zutaten
        val b = RecipeTextParser.parse("Brot\nZutaten\n500 g\n1 Würfel\n2 TL\nMehl\nHefe\nSalz\nWasser\nZubereitung\nKneten.")
        assertEquals(
            listOf("500 g" to "Mehl", "1 Würfel" to "Hefe", "2 TL" to "Salz", null to "Wasser"),
            b.ingredients.map { it.amount to it.name },
        )
    }

    @Test
    fun `Schritte ohne Nummern - Absätze und Satzenden`() {
        // Absätze (Leerzeile) nach einem Satzende trennen Schritte; Zeilenumbrüche im Absatz nicht.
        val a = RecipeTextParser.parse(
            "Soße\nZutaten\n1 Zwiebel\nZubereitung\nDie Zwiebel fein würfeln. Dann in\nButter anschwitzen.\n\nMit Brühe ablöschen und\n\neinkochen lassen.",
        )
        assertEquals(
            listOf("Die Zwiebel fein würfeln. Dann in Butter anschwitzen.", "Mit Brühe ablöschen und einkochen lassen."),
            a.steps,
        )
        // Ein einziger langer Absatz wird an Zeilen geteilt, die mit einem Satz enden.
        val b = RecipeTextParser.parse(
            "Soße\nZutaten\n1 Zwiebel\nZubereitung\nDie Zwiebel fein\nwürfeln.\nIn Butter\nanschwitzen und\nablöschen.",
        )
        assertEquals(listOf("Die Zwiebel fein würfeln.", "In Butter anschwitzen und ablöschen."), b.steps)
    }

    @Test
    fun `vegetarisch wird erkannt und leerer Text ergibt ein leeres Rezept`() {
        assertTrue(RecipeTextParser.parse("Gemüsecurry (vegan)\nZutaten\n1 Dose Kokosmilch").vegetarian)
        val empty = RecipeTextParser.parse("  \n")
        assertEquals("", empty.title)
        assertTrue(empty.ingredients.isEmpty())
        assertTrue(empty.steps.isEmpty())
    }

    @Test
    fun `Fließtext ganz ohne Zutatenliste wird zur Zubereitung`() {
        val r = RecipeTextParser.parse("Rührei\nEier in einer Schüssel verquirlen und mit Salz würzen.\nIn Butter stocken lassen.")
        assertEquals("Rührei", r.title)
        assertTrue(r.ingredients.isEmpty())
        assertEquals(2, r.steps.size)
    }

    @Test
    fun `nur Menge`() {
        assertTrue(RecipeTextParser.isAmountOnly("250 g"))
        assertTrue(RecipeTextParser.isAmountOnly("2"))
        assertTrue(RecipeTextParser.isAmountOnly("1 EL"))
        assertTrue(RecipeTextParser.isAmountOnly("½ l"))
        assertFalse(RecipeTextParser.isAmountOnly("250 g Mehl"))
        assertFalse(RecipeTextParser.isAmountOnly("Mehl"))
        assertFalse(RecipeTextParser.isAmountOnly("30 Min."))
    }

    @Test
    fun `Layout - Tabelle wird zeilenweise verbunden und Absätze getrennt`() {
        val lines = listOf(
            OcrLine("Waffeln", 100, 20, 400, 60, block = 0),
            OcrLine("Zutaten", 100, 100, 300, 130, block = 1),
            // Mengenspalte (Block 2) und Namensspalte (Block 3)
            OcrLine("250 g", 100, 150, 180, 180, block = 2),
            OcrLine("3", 100, 190, 120, 220, block = 2),
            OcrLine("Mehl", 220, 152, 320, 182, block = 3),
            OcrLine("Eier", 220, 191, 300, 221, block = 3),
            OcrLine("Zubereitung", 100, 300, 350, 330, block = 4),
            OcrLine("Alles verrühren.", 100, 340, 500, 370, block = 5),
        )
        val text = RecipeTextLayout.toText(lines)
        assertEquals("Waffeln\n\nZutaten\n\n250 g Mehl\n3 Eier\n\nZubereitung\n\nAlles verrühren.", text)
        val r = RecipeTextParser.parse(text)
        assertEquals("Waffeln", r.title)
        assertEquals(listOf("250 g" to "Mehl", "3" to "Eier"), r.ingredients.map { it.amount to it.name })
        assertEquals(listOf("Alles verrühren."), r.steps)
    }

    @Test
    fun `Layout - Menge ohne Nachbarn bleibt allein`() {
        val lines = listOf(
            OcrLine("2 EL", 100, 100, 160, 130),
            OcrLine("Zucker", 100, 140, 220, 170), // darunter, nicht daneben
        )
        assertEquals("2 EL\nZucker", RecipeTextLayout.toText(lines))
    }
}
