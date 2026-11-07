package de.frelina.vorrat.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.work.WorkManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.frelina.vorrat.BuildConfig
import de.frelina.vorrat.data.db.LocationDao
import de.frelina.vorrat.data.db.LookupCacheDao
import de.frelina.vorrat.data.db.ProductDao
import de.frelina.vorrat.data.db.QuantityTypeDao
import de.frelina.vorrat.data.db.StockEntryDao
import de.frelina.vorrat.data.db.UnitDao
import de.frelina.vorrat.data.db.VorratDatabase
import de.frelina.vorrat.data.off.OffApi
import de.frelina.vorrat.data.off.OpenFoodFactsSource
import de.frelina.vorrat.data.off.ProductLookupSource
import de.frelina.vorrat.data.sync.FirestoreRemoteStore
import de.frelina.vorrat.data.sync.RemoteStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Qualifier
import javax.inject.Singleton

object OffConfig {
    /**
     * Pflicht laut OFF-API-Doku: eigener User-Agent "AppName/Version (Kontakt)".
     * Die Kontaktadresse steht in BuildConfig.OFF_CONTACT (app/build.gradle.kts).
     */
    val USER_AGENT = "Vorrat/${BuildConfig.VERSION_NAME} (${BuildConfig.OFF_CONTACT})"
}

/** App-weiter CoroutineScope (lebt so lange wie der Prozess), z. B. für Sync-Listener. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): VorratDatabase =
        Room.databaseBuilder(context, VorratDatabase::class.java, VorratDatabase.NAME)
            .addMigrations(*VorratDatabase.MIGRATIONS)
            .addCallback(VorratDatabase.SEED_CALLBACK)
            .build()

    @Provides fun locationDao(db: VorratDatabase): LocationDao = db.locationDao()
    @Provides fun unitDao(db: VorratDatabase): UnitDao = db.unitDao()
    @Provides fun quantityTypeDao(db: VorratDatabase): QuantityTypeDao = db.quantityTypeDao()
    @Provides fun productDao(db: VorratDatabase): ProductDao = db.productDao()
    @Provides fun stockEntryDao(db: VorratDatabase): StockEntryDao = db.stockEntryDao()
    @Provides fun lookupCacheDao(db: VorratDatabase): LookupCacheDao = db.lookupCacheDao()

    @Provides
    @Singleton
    fun dataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }

    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun okHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", OffConfig.USER_AGENT).build())
        }
        .build()

    @Provides
    @Singleton
    fun offApi(client: OkHttpClient, json: Json): OffApi = Retrofit.Builder()
        .baseUrl(OffApi.BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(OffApi::class.java)

    @Provides
    @Singleton
    fun workManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)

    @Provides
    @Named("appVersion")
    fun appVersion(): String = BuildConfig.VERSION_NAME

    @Provides
    @Named("cacheDir")
    fun cacheDir(@ApplicationContext context: Context): File = context.cacheDir
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds
    abstract fun lookupSource(impl: OpenFoodFactsSource): ProductLookupSource

    @Binds
    abstract fun remoteStore(impl: FirestoreRemoteStore): RemoteStore
}
