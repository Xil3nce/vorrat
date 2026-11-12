package de.frelina.vorrat.data

import de.frelina.vorrat.data.image.ImageStore
import de.frelina.vorrat.data.off.LookedUpProduct
import de.frelina.vorrat.data.off.LookupErrorKind
import de.frelina.vorrat.data.off.LookupResult
import de.frelina.vorrat.data.off.ProductLookupSource
import de.frelina.vorrat.data.off.SearchResult
import de.frelina.vorrat.data.repo.ScanRepository
import de.frelina.vorrat.data.db.LookupCacheEntity
import de.frelina.vorrat.domain.Nutrition
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ScanRepositoryTest : DbTestBase() {

    private class FakeSource(var result: LookupResult) : ProductLookupSource {
        var calls = 0
        override suspend fun lookup(barcode: String): LookupResult {
            calls++
            return result
        }
        override suspend fun search(query: String): SearchResult = SearchResult.Hits(emptyList())
        override suspend fun downloadImage(url: String): ByteArray = byteArrayOf(1, 2, 3)
    }

    private lateinit var source: FakeSource
    private lateinit var repo: ScanRepository

    @Before
    fun setUpRepo() {
        source = FakeSource(LookupResult.Failed(LookupErrorKind.NO_NETWORK))
        val scanImages = ImageStore(File(context.cacheDir, "scan-images").apply { deleteRecursively() })
        repo = ScanRepository(db.productDao(), db.stockEntryDao(), db.lookupCacheDao(), source, scanImages)
    }

    @Test fun `lokal bekannter Barcode wird offline aufgeloest (exakter Treffer)`() = runBlocking {
        val loc = location()
        entry(product(barcode = "111"), loc)
        val outcome = repo.resolve("111")
        assertEquals(1, outcome.exactMatches.size)
        assertTrue(outcome.alternatives.isEmpty())
        assertEquals(0, source.calls)
    }

    @Test fun `bekanntes Produkt ohne Bestand zeigt Alternativen gleicher Kategorie`() = runBlocking {
        val loc = location()
        product(name = "Nutella", barcode = "111", category = "en:hazelnut-spreads") // kein Bestand mehr
        entry(product(name = "Nusspli", barcode = "222", category = "en:hazelnut-spreads"), loc)
        entry(product(name = "Honig", barcode = "333", category = "en:honeys"), loc)
        val outcome = repo.resolve("111")
        assertTrue(outcome.exactMatches.isEmpty())
        assertEquals(listOf("Nusspli"), outcome.alternatives.map { it.productName })
        assertEquals(0, source.calls)
    }

    @Test fun `unbekannter Barcode offline – keine Alternativen, Fehler gemeldet`() = runBlocking {
        entry(product(barcode = "222", category = "en:hazelnut-spreads"), location())
        val outcome = repo.resolve("999")
        assertEquals(LookupErrorKind.NO_NETWORK, outcome.lookupError)
        assertTrue(outcome.alternatives.isEmpty())
        assertNull(outcome.remote)
    }

    @Test fun `unbekannter Barcode online – OFF-Kategorie liefert Alternativen und wird gecacht`() = runBlocking {
        entry(product(name = "Nusspli", barcode = "222", category = "en:hazelnut-spreads"), location())
        source.result = LookupResult.Found(
            LookedUpProduct("999", "Nutella", "Ferrero", "en:hazelnut-spreads", null, null, "https://x/img.jpg"),
        )
        val first = repo.resolve("999")
        assertEquals("Nutella", first.remote?.name)
        assertNotNull(first.remote?.imagePath) // Bild lokal abgelegt
        assertEquals(listOf("Nusspli"), first.alternatives.map { it.productName })

        // Zweiter Scan: aus dem Cache, keine weitere Abfrage
        source.result = LookupResult.Failed(LookupErrorKind.NO_NETWORK)
        val second = repo.resolve("999")
        assertEquals("Nutella", second.remote?.name)
        assertEquals(1, source.calls)
    }

    @Test fun `nicht gefunden wird gemeldet und gecacht`() = runBlocking {
        source.result = LookupResult.NotFound
        assertTrue(repo.resolve("555").remoteNotFound)
        assertTrue(repo.resolve("555").remoteNotFound)
        assertEquals(1, source.calls)
    }

    private val nutella = Nutrition(energyKj = 2252.0, energyKcal = 539.0, fat = 30.9, sugars = 56.3)

    private fun found(code: String, nutrition: Nutrition?) = LookupResult.Found(
        LookedUpProduct(code, "Nutella", "Ferrero", "en:hazelnut-spreads", null, null, null, nutrition),
    )

    @Test fun `Scan speichert Naehrwerte im Cache, Detailansicht fragt nicht erneut`() = runBlocking {
        source.result = found("999", nutella)
        repo.resolve("999")
        assertEquals(nutella, repo.nutritionFor("999"))
        assertEquals(1, source.calls)
    }

    @Test fun `alter Cache-Eintrag ohne Naehrwerte wird einmal nachgefragt, Bild bleibt erhalten`() = runBlocking {
        db.lookupCacheDao().upsert(
            LookupCacheEntity("999", true, "Nutella", null, null, null, null, "bild.jpg", 1L),
        )
        source.result = found("999", nutella)
        assertEquals(nutella, repo.nutritionFor("999"))
        assertEquals(nutella, repo.nutritionFor("999"))
        assertEquals(1, source.calls)
        assertEquals("bild.jpg", db.lookupCacheDao().get("999")!!.imagePath)
    }

    @Test fun `lokales Produkt ohne Cache – Naehrwerte werden geholt und gemerkt, auch wenn OFF keine hat`() = runBlocking {
        source.result = found("777", null)
        assertNull(repo.nutritionFor("777"))
        assertNull(repo.nutritionFor("777"))
        assertEquals(1, source.calls)
        assertTrue(db.lookupCacheDao().get("777")!!.found)
    }

    @Test fun `offline – keine Naehrwerte, beim naechsten Mal neuer Versuch`() = runBlocking {
        assertNull(repo.nutritionFor("888"))
        assertNull(db.lookupCacheDao().get("888"))
        source.result = found("888", nutella)
        assertEquals(nutella, repo.nutritionFor("888"))
        assertEquals(2, source.calls)
    }

    @Test fun `nicht bei OFF – gemerkt fuer 24 h`() = runBlocking {
        source.result = LookupResult.NotFound
        assertNull(repo.nutritionFor("555", now = 1_000L))
        assertNull(repo.nutritionFor("555", now = 2_000L))
        assertEquals(1, source.calls)
        source.result = found("555", nutella)
        assertEquals(nutella, repo.nutritionFor("555", now = 1_000L + ScanRepository.NOT_FOUND_TTL_MS + 1))
        assertEquals(2, source.calls)
    }
}
