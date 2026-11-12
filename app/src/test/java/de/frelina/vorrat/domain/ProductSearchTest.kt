package de.frelina.vorrat.domain

import de.frelina.vorrat.data.db.ProductEntity
import de.frelina.vorrat.data.db.ProductSource
import de.frelina.vorrat.data.off.OffSearchResponse
import de.frelina.vorrat.data.off.OpenFoodFactsSource
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductSearchTest {

    private fun p(name: String, brand: String? = null, category: String? = null) = ProductEntity(
        barcode = null, name = name, brand = brand, category = category, packageSize = null, unitId = null,
        imagePath = null, source = ProductSource.MANUAL, createdAt = 0, updatedAt = 0,
    )

    private val products = listOf(
        p("Vollmilch", "Weihenstephan", "en:milks"),
        p("Milchreis", "Müller", "en:rice-puddings"),
        p("Haferdrink", "Oatly", "en:plant-milks"),
        p("Nutella", "Ferrero", "en:hazelnut-spreads"),
        p("Spaghetti", "Barilla", "en:pastas"),
    )

    // ---------------------------------------------------------------- Eigene Produkte

    @Test fun `Name beginnt mit Eingabe steht vorn`() {
        val names = ProductSuggest.suggest("milch", products).map { it.name }
        assertEquals("Milchreis", names.first())
        assertTrue("Vollmilch" in names)
    }

    @Test fun `Suche ueber Marke und Kategorie`() {
        assertEquals(listOf("Spaghetti"), ProductSuggest.suggest("barilla", products).map { it.name })
        assertEquals(listOf("Spaghetti"), ProductSuggest.suggest("pastas", products).map { it.name })
        assertEquals(listOf("Nutella"), ProductSuggest.suggest("hazelnut", products).map { it.name })
    }

    @Test fun `alle Woerter muessen passen, Gross-Kleinschreibung egal`() {
        assertEquals(listOf("Milchreis"), ProductSuggest.suggest("MÜLLER milch", products).map { it.name })
        assertTrue(ProductSuggest.suggest("müller nutella", products).isEmpty())
    }

    @Test fun `zu kurze Eingabe liefert nichts`() {
        assertTrue(ProductSuggest.suggest("m", products).isEmpty())
        assertTrue(ProductSuggest.suggest("  ", products).isEmpty())
    }

    @Test fun `Limit und keine Duplikate`() {
        val many = (1..10).map { p("Milch $it") } + p("Milch 1")
        val result = ProductSuggest.suggest("milch", many, limit = 20)
        assertEquals(10, result.size)
        assertEquals(3, ProductSuggest.suggest("milch", many, limit = 3).size)
    }

    // ---------------------------------------------------------------- Open Food Facts

    @Test fun `Suchbegriff wird bereinigt und auf Deutschland eingeschraenkt`() {
        assertEquals("nutella countries_tags:\"en:germany\"", OpenFoodFactsSource.searchQuery("  nutella "))
        assertEquals("Coca Cola Zero countries_tags:\"en:germany\"", OpenFoodFactsSource.searchQuery("Coca-Cola \"Zero\":"))
        assertNull(OpenFoodFactsSource.searchQuery("ab"))
        assertNull(OpenFoodFactsSource.searchQuery("**:"))
    }

    @Test fun `Treffer werden wie beim Barcode aufbereitet`() {
        val json = Json { ignoreUnknownKeys = true }
        val body = json.decodeFromString(
            OffSearchResponse.serializer(),
            """
            {"hits":[
              {"code":"3017620425035","brands":["Ferrero"],"quantity":"1 kg","product_name":"Nutella",
               "product_name_de":"Nutella","categories_tags":["en:spreads","en:hazelnut-spreads"],
               "image_front_small_url":"https://images.openfoodfacts.org/x.jpg"},
              {"code":"4000000000001","brands":"Kölln, Peter Kölln","product_name":"Haferflocken",
               "product_quantity":500,"product_quantity_unit":"g"},
              {"code":"4000000000002","product_name":""},
              {"product_name":"Ohne Barcode"},
              {"code":"3017620425035","product_name":"Doppelt"}
            ],"count":5,"page":1}
            """.trimIndent(),
        )
        val hits = OpenFoodFactsSource.mapHits(body)
        assertEquals(listOf("Nutella", "Haferflocken"), hits.map { it.name })
        val nutella = hits[0]
        assertEquals("Ferrero", nutella.brand)
        assertEquals("en:hazelnut-spreads", nutella.category)
        assertEquals(ParsedQuantity("1".toBigDecimal(), "kg"), ParsedQuantity(nutella.packageSize!!, nutella.unitSymbol!!))
        assertEquals("https://images.openfoodfacts.org/x.jpg", nutella.imageUrl)
        val hafer = hits[1]
        assertEquals("Kölln", hafer.brand)
        assertEquals("500".toBigDecimal(), hafer.packageSize)
        assertEquals("g", hafer.unitSymbol)
        assertNull(hafer.imageUrl)
    }
}
