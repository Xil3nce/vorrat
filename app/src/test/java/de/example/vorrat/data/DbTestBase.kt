package de.example.vorrat.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.example.vorrat.data.db.LocationEntity
import de.example.vorrat.data.db.ProductEntity
import de.example.vorrat.data.db.ProductSource
import de.example.vorrat.data.db.StockEntryEntity
import de.example.vorrat.data.db.VorratDatabase
import org.junit.After
import org.junit.Before
import java.time.LocalDate

/** In-Memory-Room (Robolectric) inkl. Startwerten für Einheiten und Menge-Arten. */
abstract class DbTestBase {
    protected lateinit var context: Context
    protected lateinit var db: VorratDatabase

    @Before
    fun createDb() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, VorratDatabase::class.java)
            .addCallback(VorratDatabase.SEED_CALLBACK)
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    protected suspend fun location(name: String = "Keller"): Long = db.locationDao().insert(LocationEntity(name = name))

    protected suspend fun product(
        name: String = "Nutella",
        barcode: String? = "3017624010701",
        category: String? = "en:hazelnut-spreads",
        imagePath: String? = null,
    ): Long = db.productDao().insert(
        ProductEntity(
            barcode = barcode,
            name = name,
            brand = "Ferrero",
            category = category,
            packageSize = "400".toBigDecimal(),
            unitId = db.unitDao().findBySymbol("g")?.id,
            imagePath = imagePath,
            source = ProductSource.OPEN_FOOD_FACTS,
            createdAt = 1,
            updatedAt = 2,
        ),
    )

    protected suspend fun entry(
        productId: Long,
        locationId: Long,
        quantity: Int = 2,
        expiry: LocalDate? = LocalDate.of(2026, 12, 24),
    ): Long = db.stockEntryDao().insert(
        StockEntryEntity(
            productId = productId,
            locationId = locationId,
            level = 1,
            shelf = 3,
            expiryDate = expiry,
            quantity = quantity,
            quantityTypeId = db.quantityTypeDao().findBySymbol("Stck.")!!.id,
            note = "Notiz",
        ),
    )
}
