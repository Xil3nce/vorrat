package de.frelina.vorrat.domain

import de.frelina.vorrat.data.off.LookupResult
import de.frelina.vorrat.data.off.OffProductResponse
import de.frelina.vorrat.data.off.OpenFoodFactsSource
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuantityParserTest {

    private fun p(q: String?, pq: String? = null, pu: String? = null) = QuantityParser.parse(q, pq, pu)

    @Test fun `einfache Angaben`() {
        assertEquals(ParsedQuantity("500".toBigDecimal(), "g"), p("500 g"))
        assertEquals(ParsedQuantity("500".toBigDecimal(), "g"), p("500g"))
        assertEquals(ParsedQuantity("1".toBigDecimal(), "kg"), p("1 kg"))
        assertEquals(ParsedQuantity("1.5".toBigDecimal(), "l"), p("1,5 l"))
        assertEquals(ParsedQuantity("1.5".toBigDecimal(), "l"), p("1.5 L"))
        assertEquals(ParsedQuantity("250".toBigDecimal(), "ml"), p("250 ml ℮"))
        assertEquals(ParsedQuantity("400".toBigDecimal(), "g"), p("400.0 g"))
    }

    @Test fun `Zentiliter werden in ml umgerechnet`() =
        assertEquals(ParsedQuantity("750".toBigDecimal(), "ml"), p("75 cl"))

    @Test fun `Mehrfachpackung nutzt normalisierte Gesamtmenge`() =
        assertEquals(ParsedQuantity("450".toBigDecimal(), "g"), p("3 x 150 g", "450", "g"))

    @Test fun `nicht eindeutig parsebar ergibt null`() {
        assertNull(p("6 Eier"))
        assertNull(p("3 x 150 g"))
        assertNull(p("2 Stück", "2", "Stück"))
        assertNull(p(null))
        assertNull(p(""))
        assertNull(p("0 g"))
        assertNull(p("ca. 500 g"))
    }

    @Test fun `Freitext hat Vorrang vor normalisierter Menge`() =
        assertEquals(ParsedQuantity("1".toBigDecimal(), "kg"), p("1 kg", "1000", "g"))

    @Test fun `OFF-Antwort wird korrekt gemappt (Beispiel aus der API, product_quantity als Zahl)`() {
        val json = """
            {"code":"3017624010701","product":{"brands":"Ferrero, Nutella",
             "categories_tags":["en:breakfasts","en:spreads","en:sweet-spreads","fr:pates-a-tartiner",
               "en:hazelnut-spreads","en:chocolate-spreads","en:cocoa-and-hazelnuts-spreads","de:Other"],
             "image_front_url":"https://images.openfoodfacts.org/x.jpg","product_name":"Nutella",
             "product_name_de":"Nutella DE","product_quantity":400,"product_quantity_unit":"g","quantity":"400.0 g",
             "unknown_field": 1},
             "status":1,"status_verbose":"product found"}
        """.trimIndent()
        val body = Json { ignoreUnknownKeys = true }.decodeFromString(OffProductResponse.serializer(), json)
        val result = OpenFoodFactsSource.map("3017624010701", body)
        assertTrue(result is LookupResult.Found)
        val p = (result as LookupResult.Found).product
        assertEquals("Nutella DE", p.name)
        assertEquals("Ferrero", p.brand)
        assertEquals("en:cocoa-and-hazelnuts-spreads", p.category)
        assertEquals("400".toBigDecimal(), p.packageSize)
        assertEquals("g", p.unitSymbol)
        assertEquals("https://images.openfoodfacts.org/x.jpg", p.imageUrl)
    }

    @Test fun `OFF status 0 bedeutet nicht gefunden`() {
        val body = Json.decodeFromString(OffProductResponse.serializer(), """{"code":"1","status":0,"status_verbose":"product not found"}""")
        assertEquals(LookupResult.NotFound, OpenFoodFactsSource.map("1", body))
    }

    @Test fun `spezifischste Kategorie`() {
        assertEquals("en:b", CategoryResolver.mostSpecific(listOf("en:a", "en:b", "de:Freitext")))
        assertEquals("nl:koeken", CategoryResolver.mostSpecific(listOf("nl:chocolade", "nl:koeken")))
        assertNull(CategoryResolver.mostSpecific(emptyList()))
        assertNull(CategoryResolver.mostSpecific(null))
        assertEquals("hazelnut spreads", CategoryResolver.displayName("en:hazelnut-spreads"))
        assertEquals("Nuss-Nougat-Creme", CategoryResolver.displayName("Nuss-Nougat-Creme"))
    }
}
