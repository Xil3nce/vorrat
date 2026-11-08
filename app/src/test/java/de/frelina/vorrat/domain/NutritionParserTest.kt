package de.frelina.vorrat.domain

import de.frelina.vorrat.data.off.LookupResult
import de.frelina.vorrat.data.off.OffProductResponse
import de.frelina.vorrat.data.off.OpenFoodFactsSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionParserTest {

    private val json = Json { ignoreUnknownKeys = true }
    private fun obj(text: String) = json.decodeFromString(JsonObject.serializer(), text)

    @Test fun `Werte je 100 g werden gelesen, Text-Zahlen und Komma inklusive`() {
        val n = NutritionParser.fromNutriments(
            obj(
                """{"energy-kj_100g":2252,"energy-kcal_100g":539,"fat_100g":30.9,"saturated-fat_100g":"10,6",
                   "carbohydrates_100g":57.5,"sugars_100g":56.3,"fiber_100g":"","proteins_100g":6.3,"salt_100g":0.107,
                   "fat_serving":4.6,"nova-group":4}""",
            ),
            "g",
        )!!
        assertEquals(2252.0, n.energyKj!!, 0.0)
        assertEquals(539.0, n.energyKcal!!, 0.0)
        assertEquals(10.6, n.saturatedFat!!, 0.0)
        assertNull(n.fiber)
        assertEquals(0.107, n.salt!!, 0.0)
        assertFalse(n.perMl)
    }

    @Test fun `fehlende kcal werden aus kJ berechnet und umgekehrt`() {
        val a = NutritionParser.fromNutriments(obj("""{"energy_100g":418.4}"""), null)!!
        assertEquals(100.0, a.energyKcal!!, 0.001)
        val b = NutritionParser.fromNutriments(obj("""{"energy-kcal_100g":100}"""), null)!!
        assertEquals(418.4, b.energyKj!!, 0.001)
    }

    @Test fun `Getraenke gelten je 100 ml`() {
        assertTrue(NutritionParser.fromNutriments(obj("""{"sugars_100g":10.6}"""), "ml")!!.perMl)
        assertTrue(NutritionParser.fromNutriments(obj("""{"sugars_100g":10.6}"""), "l")!!.perMl)
    }

    @Test fun `ohne verwertbare Werte gibt es keine Naehrwerte`() {
        assertNull(NutritionParser.fromNutriments(null, "g"))
        assertNull(NutritionParser.fromNutriments(obj("""{}"""), "g"))
        assertNull(NutritionParser.fromNutriments(obj("""{"fat_serving":3,"fat_100g":-1,"salt_100g":"x"}"""), "g"))
    }

    @Test fun `JSON-Speicherung ist verlustfrei, Kaputtes ergibt null`() {
        val n = Nutrition(energyKj = 100.0, energyKcal = 23.9, fat = 1.5, perMl = true)
        assertEquals(n, NutritionParser.decode(NutritionParser.encode(n)))
        assertNull(NutritionParser.encode(null))
        assertNull(NutritionParser.decode(null))
        assertNull(NutritionParser.decode("kein json"))
    }

    @Test fun `deutsches Zahlenformat`() {
        assertEquals("0,11", NutritionParser.formatAmount(0.107))
        assertEquals("30,9", NutritionParser.formatAmount(30.9))
        assertEquals("6", NutritionParser.formatAmount(6.0))
        assertEquals("2.252", NutritionParser.formatEnergy(2252.4))
        assertEquals("539", NutritionParser.formatEnergy(538.6))
    }

    @Test fun `OFF-Antwort mit nutriments wird mitgemappt`() {
        val body = json.decodeFromString(
            OffProductResponse.serializer(),
            """{"code":"5449000000996","status":1,"product":{"product_name":"Coca-Cola","quantity":"1 l",
               "nutriments":{"energy-kcal_100g":42,"sugars_100g":10.6,"salt_100g":0}}}""",
        )
        val found = OpenFoodFactsSource.map("5449000000996", body) as LookupResult.Found
        val n = found.product.nutrition!!
        assertEquals(42.0, n.energyKcal!!, 0.0)
        assertEquals(0.0, n.salt!!, 0.0)
        assertTrue(n.perMl)
    }

    @Test fun `OFF-Antwort ohne nutriments hat keine Naehrwerte`() {
        val body = json.decodeFromString(
            OffProductResponse.serializer(),
            """{"code":"1","status":1,"product":{"product_name":"X"}}""",
        )
        assertNull((OpenFoodFactsSource.map("1", body) as LookupResult.Found).product.nutrition)
    }
}
