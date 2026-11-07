package de.frelina.vorrat.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.frelina.vorrat.data.db.LocationEntity
import de.frelina.vorrat.data.db.ProductEntity
import de.frelina.vorrat.data.db.ProductSource
import de.frelina.vorrat.data.db.StockEntryEntity
import de.frelina.vorrat.data.db.VorratDatabase
import de.frelina.vorrat.data.household.Household
import de.frelina.vorrat.data.household.HouseholdStore
import de.frelina.vorrat.data.image.ImageStore
import de.frelina.vorrat.data.sync.RemoteWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import java.io.File
import java.time.LocalDate
import java.util.UUID

/**
 * In-Memory-Room (Robolectric) inkl. Startwerten für Einheiten und Menge-Arten,
 * dazu ein [FakeRemoteStore] statt Firestore und ein Haushalt im DataStore.
 */
abstract class DbTestBase {
    protected lateinit var context: Context
    protected lateinit var db: VorratDatabase
    protected lateinit var remote: FakeRemoteStore
    protected lateinit var householdStore: HouseholdStore
    protected lateinit var images: ImageStore
    protected lateinit var writer: RemoteWriter
    private lateinit var storeScope: CoroutineScope

    /** Ohne Haushalt (vor dem Onboarding) schreibt der RemoteWriter nichts. */
    protected open val withHousehold: Boolean = true

    @Before
    fun createDb() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, VorratDatabase::class.java)
            .addCallback(VorratDatabase.SEED_CALLBACK)
            .build()
        remote = FakeRemoteStore()
        storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val prefs = File(context.cacheDir, "prefs-${UUID.randomUUID()}.preferences_pb")
        householdStore = HouseholdStore(PreferenceDataStoreFactory.create(scope = storeScope) { prefs })
        if (withHousehold) runBlocking { householdStore.set(Household(HOUSEHOLD_ID, "ABCDE23456", "uid-test")) }
        images = ImageStore(File(context.cacheDir, "images-${UUID.randomUUID()}"))
        writer = RemoteWriter(remote, householdStore, images)
    }

    @After
    fun closeDb() {
        db.close()
        storeScope.cancel()
        images.directory.deleteRecursively()
    }

    protected suspend fun location(name: String = "Keller"): String =
        LocationEntity(name = name).also { db.locationDao().insert(it) }.id

    protected suspend fun product(
        name: String = "Nutella",
        barcode: String? = "3017624010701",
        category: String? = "en:hazelnut-spreads",
        imagePath: String? = null,
    ): String = ProductEntity(
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
    ).also { db.productDao().insert(it) }.id

    protected suspend fun entry(
        productId: String,
        locationId: String,
        quantity: Int = 2,
        expiry: LocalDate? = LocalDate.of(2026, 12, 24),
    ): String = StockEntryEntity(
        productId = productId,
        locationId = locationId,
        level = 1,
        shelf = 3,
        expiryDate = expiry,
        quantity = quantity,
        quantityTypeId = db.quantityTypeDao().findBySymbol("Stck.")!!.id,
        note = "Notiz",
    ).also { db.stockEntryDao().insert(it) }.id

    companion object {
        const val HOUSEHOLD_ID = "hh-test"
    }
}
