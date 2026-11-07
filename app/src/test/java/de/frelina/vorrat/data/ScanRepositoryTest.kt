package de.frelina.vorrat.data

import de.frelina.vorrat.data.image.ImageStore
import de.frelina.vorrat.data.off.LookedUpProduct
import de.frelina.vorrat.data.off.LookupErrorKind
import de.frelina.vorrat.data.off.LookupResult
import de.frelina.vorrat.data.off.ProductLookupSource
import de.frelina.vorrat.data.repo.ScanRepository
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
        override suspend fun downloadImage(url: String): ByteArray = byteArrayOf(1, 2, 3)
    }

    private lateinit var source: FakeSource
    private lateinit var repo: ScanRepository

    @Before
    fun setUpRepo() {
        source = FakeSource(LookupResult.Failed(LookupErrorKind.NO_NETWORK))
        val images = ImageStore(File(context.cacheDir, "scan-images").apply { deleteRecursively() })
        repo = ScanRepository(db.productDao(), db.stockEntryDao(), db.lookupCacheDao(), source, images)
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
}
