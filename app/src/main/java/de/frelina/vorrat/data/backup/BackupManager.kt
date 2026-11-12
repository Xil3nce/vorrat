package de.frelina.vorrat.data.backup

import androidx.room.withTransaction
import de.frelina.vorrat.data.db.DepartmentAssignmentEntity
import de.frelina.vorrat.data.db.DepartmentEntity
import de.frelina.vorrat.data.db.LocationEntity
import de.frelina.vorrat.data.db.MealPlanEntity
import de.frelina.vorrat.data.db.ProductEntity
import de.frelina.vorrat.data.db.ProductSource
import de.frelina.vorrat.data.db.QuantityTypeEntity
import de.frelina.vorrat.data.db.RecipeEntity
import de.frelina.vorrat.data.db.ShoppingItemEntity
import de.frelina.vorrat.data.db.StapleEntity
import de.frelina.vorrat.data.db.StockEntryEntity
import de.frelina.vorrat.data.db.UnitEntity
import de.frelina.vorrat.data.db.VorratDatabase
import de.frelina.vorrat.data.image.ImageStore
import de.frelina.vorrat.data.sync.RemoteMapper
import de.frelina.vorrat.data.sync.RemoteWriter
import de.frelina.vorrat.domain.DepartmentDefaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** Gelesenes und geprüftes Backup; Bilder liegen bereits im Staging-Verzeichnis. */
class ParsedBackup(val data: BackupData, val stagingDir: File) {
    val productCount get() = data.products.size
    val entryCount get() = data.stockEntries.size
    val locationCount get() = data.locations.size
}

@Singleton
class BackupManager @Inject constructor(
    private val db: VorratDatabase,
    private val imageStore: ImageStore,
    @Named("appVersion") private val appVersion: String,
    @Named("cacheDir") private val cacheDir: File,
    private val remote: RemoteWriter,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        // Backups mit schemaVersion 1 haben Zahlen-IDs (ohne Anführungszeichen) → als Text lesen.
        isLenient = true
    }

    // ------------------------------------------------------------------ Export

    suspend fun export(output: OutputStream) = withContext(Dispatchers.IO) {
        val data = db.withTransaction { snapshot() }
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(JSON_NAME))
            zip.write(json.encodeToString(BackupData.serializer(), data).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            (data.products.mapNotNull { it.imagePath } + data.recipes.mapNotNull { it.imagePath }).distinct().forEach { name ->
                val file = imageStore.fileOrNull(name) ?: return@forEach
                zip.putNextEntry(ZipEntry("$IMAGES_PREFIX$name"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    private suspend fun snapshot(): BackupData = BackupData(
        formatVersion = FORMAT_VERSION,
        schemaVersion = VorratDatabase.VERSION,
        exportedAt = System.currentTimeMillis(),
        appVersion = appVersion,
        locations = db.locationDao().getAll().map { LocationDto(it.id, it.name, it.levels, it.shelves) },
        units = db.unitDao().getAll().map { UnitDto(it.id, it.symbol, it.name) },
        quantityTypes = db.quantityTypeDao().getAll().map { QuantityTypeDto(it.id, it.symbol, it.name) },
        products = db.productDao().getAll().map {
            ProductDto(
                id = it.id,
                barcode = it.barcode,
                name = it.name,
                brand = it.brand,
                category = it.category,
                packageSize = it.packageSize?.toPlainString(),
                unitId = it.unitId,
                imagePath = it.imagePath?.takeIf { name -> imageStore.fileOrNull(name) != null },
                source = it.source.name,
                createdAt = it.createdAt,
                updatedAt = it.updatedAt,
                minStock = it.minStock,
                openedDays = it.openedDays,
                ingredientGroup = it.ingredientGroup,
            )
        },
        stockEntries = db.stockEntryDao().getAll().map {
            StockEntryDto(
                id = it.id,
                productId = it.productId,
                locationId = it.locationId,
                level = it.level,
                shelf = it.shelf,
                expiryDate = it.expiryDate?.toString(),
                quantity = it.quantity,
                quantityTypeId = it.quantityTypeId,
                note = it.note,
                openedAt = it.openedAt?.toString(),
                openedDays = it.openedDays,
                frozenAt = it.frozenAt?.toString(),
                nfcTagId = it.nfcTagId,
            )
        },
        shoppingItems = db.shoppingDao().getAll().map {
            ShoppingItemDto(
                it.id, it.productId, it.name, it.quantity, it.note, it.createdAt,
                it.packageSize?.toPlainString(), it.unitSymbol, it.recipes,
            )
        },
        recipes = db.recipeDao().getAll().map {
            RecipeDto(
                it.id, it.title, it.minutes, it.servings, it.ingredients, it.steps, it.vegetarian, it.createdAt, it.updatedAt,
                imagePath = it.imagePath?.takeIf { name -> imageStore.fileOrNull(name) != null },
            )
        },
        departments = db.departmentDao().getAll().map { DepartmentDto(it.id, it.name, it.position) },
        departmentAssignments = db.departmentAssignmentDao().getAll()
            .map { DepartmentAssignmentDto(it.key, it.departmentId, it.updatedAt) },
        mealPlan = db.mealPlanDao().getAll().map { MealPlanDto(it.id, it.date.toString(), it.recipeId, it.persons, it.createdAt) },
        staples = db.stapleDao().getAll().map { StapleDto(it.key, it.label, it.createdAt) },
    )

    // ------------------------------------------------------------------ Import

    /**
     * Schritt 1: ZIP lesen und vollständig validieren. Es wird noch nichts an den bestehenden
     * Daten verändert; Bilder landen in einem Staging-Verzeichnis.
     */
    suspend fun read(input: InputStream): ParsedBackup = withContext(Dispatchers.IO) {
        val staging = File(cacheDir, "import-${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            var data: BackupData? = null
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    when {
                        entry.isDirectory -> Unit
                        name == JSON_NAME -> {
                            val text = zip.readBytes().toString(Charsets.UTF_8)
                            data = json.decodeFromString(BackupData.serializer(), text)
                        }
                        name.startsWith(IMAGES_PREFIX) -> {
                            val fileName = name.removePrefix(IMAGES_PREFIX)
                            // Schutz vor "Zip Slip": nur einfache Dateinamen zulassen.
                            if (SAFE_NAME.matches(fileName)) {
                                File(staging, fileName).outputStream().use { zip.copyTo(it) }
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
            val parsed = data ?: throw BackupException(BackupException.Reason.NOT_A_BACKUP)
            validate(parsed)
            ParsedBackup(parsed, staging)
        } catch (e: BackupException) {
            staging.deleteRecursively()
            throw e
        } catch (e: SerializationException) {
            staging.deleteRecursively()
            throw BackupException(BackupException.Reason.INVALID_DATA, e.message)
        } catch (e: IllegalArgumentException) {
            staging.deleteRecursively()
            throw BackupException(BackupException.Reason.INVALID_DATA, e.message)
        } catch (e: ZipException) {
            staging.deleteRecursively()
            throw BackupException(BackupException.Reason.NOT_A_BACKUP, e.message)
        } catch (e: IOException) {
            staging.deleteRecursively()
            throw BackupException(BackupException.Reason.UNREADABLE, e.message)
        }
    }

    /**
     * Schritt 2 (nach Bestätigung): bestehende Daten in EINER Transaktion ersetzen.
     * Schlägt etwas fehl, rollt Room die Transaktion zurück – die bisherigen Daten bleiben.
     * Bilder werden erst nach erfolgreichem Commit übernommen. Anschließend werden die Haushaltsdaten in
     * Firestore ersetzt (betrifft beide Handys).
     */
    suspend fun replaceAll(parsed: ParsedBackup) = withContext(Dispatchers.IO) {
        val d = parsed.data
        try {
            val previousImages = db.productDao().getAllImagePaths().toSet() + db.recipeDao().getAllImagePaths()
            db.withTransaction {
                db.shoppingDao().deleteAll()
                db.recipeDao().deleteAll()
                db.departmentAssignmentDao().deleteAll()
                db.departmentDao().deleteAll()
                db.mealPlanDao().deleteAll()
                db.stapleDao().deleteAll()
                db.stockEntryDao().deleteAll()
                db.productDao().deleteAll()
                db.locationDao().deleteAll()
                db.unitDao().deleteAll()
                db.quantityTypeDao().deleteAll()

                db.locationDao().insertAll(d.locations.map { LocationEntity(
                            id = it.id,
                            name = it.name.trim(),
                            levels = it.levels?.takeIf { n -> n in 1..LocationEntity.MAX_SLOTS },
                            shelves = it.shelves?.takeIf { n -> n in 1..LocationEntity.MAX_SLOTS },
                        ) })
                db.unitDao().insertAll(d.units.map { UnitEntity(it.id, it.symbol.trim(), it.name.trim()) })
                db.quantityTypeDao().insertAll(
                    d.quantityTypes.map { QuantityTypeEntity(it.id, it.symbol.trim(), it.name.trim()) },
                )
                db.productDao().insertAll(
                    d.products.map {
                        ProductEntity(
                            id = it.id,
                            barcode = it.barcode?.trim()?.takeIf(String::isNotEmpty),
                            name = it.name.trim(),
                            brand = it.brand,
                            category = it.category,
                            packageSize = it.packageSize?.toBigDecimal(),
                            unitId = it.unitId,
                            imagePath = it.imagePath?.takeIf { n -> File(parsed.stagingDir, n).exists() },
                            source = ProductSource.valueOf(it.source),
                            createdAt = it.createdAt,
                            updatedAt = it.updatedAt,
                            minStock = it.minStock?.takeIf { n -> n > 0 },
                            openedDays = it.openedDays?.takeIf { n -> n >= 0 },
                            ingredientGroup = it.ingredientGroup?.takeIf { g -> g.matches(GROUP_ID) },
                        )
                    },
                )
                db.stockEntryDao().insertAll(
                    d.stockEntries.map {
                        StockEntryEntity(
                            id = it.id,
                            productId = it.productId,
                            locationId = it.locationId,
                            level = it.level,
                            shelf = it.shelf,
                            expiryDate = it.expiryDate?.let(LocalDate::parse),
                            quantity = it.quantity,
                            quantityTypeId = it.quantityTypeId,
                            note = it.note,
                            openedAt = it.openedAt?.let(LocalDate::parse),
                            openedDays = it.openedDays?.takeIf { n -> n >= 0 },
                            frozenAt = it.frozenAt?.let(LocalDate::parse),
                            nfcTagId = it.nfcTagId?.takeIf(String::isNotBlank),
                        )
                    },
                )
                db.shoppingDao().insertAll(
                    d.shoppingItems.filter { it.name.isNotBlank() }.map {
                        ShoppingItemEntity(
                            id = it.id,
                            productId = it.productId?.takeIf { id -> id in productIdsOf(d) },
                            name = it.name.trim(),
                            quantity = it.quantity.coerceAtLeast(1),
                            note = it.note,
                            createdAt = it.createdAt,
                            packageSize = it.packageSize?.toBigDecimalOrNull()?.takeIf { s -> s.signum() > 0 },
                            unitSymbol = it.unitSymbol?.trim()?.takeIf(String::isNotEmpty),
                            recipes = it.recipes?.take(RemoteMapper.MAX_PLAN_NOTE)?.takeIf(String::isNotBlank),
                        )
                    },
                )
                db.recipeDao().insertAll(
                    d.recipes.map {
                        RecipeEntity(
                            id = it.id,
                            title = it.title.trim().take(RemoteMapper.MAX_TITLE),
                            minutes = it.minutes?.takeIf { n -> n in 1..RemoteMapper.MAX_MINUTES },
                            servings = it.servings?.takeIf { n -> n in 1..RemoteMapper.MAX_SERVINGS },
                            ingredients = it.ingredients,
                            steps = it.steps.take(RemoteMapper.MAX_STEPS),
                            vegetarian = it.vegetarian,
                            createdAt = it.createdAt,
                            updatedAt = it.updatedAt,
                            imagePath = it.imagePath?.takeIf { n -> File(parsed.stagingDir, n).exists() },
                        )
                    },
                )
                // Backups von vor Schema 8 kennen keine Abteilungen → Standard-Abteilungen anlegen.
                db.departmentDao().insertAll(
                    if (d.departments.isEmpty()) {
                        DepartmentDefaults.entities()
                    } else {
                        d.departments.map {
                            DepartmentEntity(it.id, it.name.trim().take(RemoteMapper.MAX_DEPARTMENT_NAME), it.position)
                        }
                    },
                )
                db.departmentAssignmentDao().insertAll(
                    d.departmentAssignments.map { DepartmentAssignmentEntity(it.key, it.departmentId, it.updatedAt) },
                )
                db.mealPlanDao().insertAll(
                    d.mealPlan.map {
                        MealPlanEntity(
                            id = it.id,
                            date = LocalDate.parse(it.date),
                            recipeId = it.recipeId,
                            persons = it.persons.coerceIn(1, RemoteMapper.MAX_PLAN_PERSONS),
                            createdAt = it.createdAt,
                        )
                    },
                )
                db.stapleDao().insertAll(
                    d.staples.map { StapleEntity(it.key, it.label.trim().take(RemoteMapper.MAX_STAPLE_LABEL), it.createdAt) },
                )
            }
            // Commit erfolgreich → Bilder übernehmen, nicht mehr benötigte entfernen.
            parsed.stagingDir.listFiles()?.forEach { f ->
                f.copyTo(imageStore.file(f.name), overwrite = true)
            }
            val referenced = db.productDao().getAllImagePaths().toSet() +
                db.lookupCacheDao().getAllImagePaths().toSet() + db.recipeDao().getAllImagePaths().toSet()
            imageStore.deleteUnreferenced(referenced)
            remote.replaceAll(db, previousImages)
        } finally {
            parsed.stagingDir.deleteRecursively()
        }
    }

    fun discard(parsed: ParsedBackup) {
        parsed.stagingDir.deleteRecursively()
    }

    // ------------------------------------------------------------------ Validierung

    internal fun validate(d: BackupData) {
        fun invalid(detail: String): Nothing = throw BackupException(BackupException.Reason.INVALID_DATA, detail)

        if (d.formatVersion != FORMAT_VERSION) {
            throw BackupException(BackupException.Reason.UNSUPPORTED_FORMAT, "formatVersion=${d.formatVersion}")
        }
        if (d.schemaVersion > VorratDatabase.VERSION) {
            throw BackupException(BackupException.Reason.NEWER_SCHEMA, "schemaVersion=${d.schemaVersion}")
        }
        if (d.schemaVersion < 1) invalid("schemaVersion")

        val locationIds = d.locations.map { it.id }.toSet()
        val unitIds = d.units.map { it.id }.toSet()
        val typeIds = d.quantityTypes.map { it.id }.toSet()
        val productIds = d.products.map { it.id }.toSet()

        if (locationIds.size != d.locations.size) invalid("doppelte Orts-IDs")
        if (unitIds.size != d.units.size) invalid("doppelte Einheiten-IDs")
        if (typeIds.size != d.quantityTypes.size) invalid("doppelte Menge-Art-IDs")
        if (productIds.size != d.products.size) invalid("doppelte Produkt-IDs")
        if (d.stockEntries.map { it.id }.toSet().size != d.stockEntries.size) invalid("doppelte Eintrags-IDs")
        if (d.shoppingItems.map { it.id }.toSet().size != d.shoppingItems.size) invalid("doppelte Einkaufslisten-IDs")
        if (d.recipes.map { it.id }.toSet().size != d.recipes.size) invalid("doppelte Rezept-IDs")
        d.recipes.forEach { r ->
            if (r.title.isBlank()) invalid("Rezept ${r.id} ohne Titel")
            if (r.ingredients.length > RemoteMapper.MAX_INGREDIENTS_JSON) invalid("Rezept ${r.id}: Zutaten zu lang")
        }

        if (d.departments.map { it.id }.toSet().size != d.departments.size) invalid("doppelte Abteilungs-IDs")
        if (d.departments.any { it.name.isBlank() }) invalid("Abteilung ohne Namen")
        if (d.departmentAssignments.map { it.key }.toSet().size != d.departmentAssignments.size) {
            invalid("doppelte Zuordnungen")
        }
        d.departmentAssignments.forEach { a ->
            if (!RemoteMapper.isValidAssignmentKey(a.key)) invalid("Zuordnung ${a.key}: Schlüssel")
        }
        if (d.mealPlan.map { it.id }.toSet().size != d.mealPlan.size) invalid("doppelte Wochenplan-IDs")
        d.mealPlan.forEach { m ->
            if (m.recipeId.isBlank()) invalid("Wochenplan ${m.id}: Rezept")
            try {
                LocalDate.parse(m.date)
            } catch (_: DateTimeParseException) {
                invalid("Wochenplan ${m.id}: Datum")
            }
        }
        if (d.staples.map { it.key }.toSet().size != d.staples.size) invalid("doppelte Einträge bei Immer zuhause")
        d.staples.forEach { s ->
            if (!RemoteMapper.isValidStapleKey(s.key) || s.label.isBlank()) invalid("Immer zuhause ${s.key}")
        }

        if (d.locations.any { it.name.isBlank() }) invalid("Ort ohne Namen")
        if (d.locations.map { it.name.trim().lowercase() }.toSet().size != d.locations.size) invalid("doppelte Ortsnamen")
        if (d.units.any { it.symbol.isBlank() || it.name.isBlank() }) invalid("Einheit unvollständig")
        if (d.units.map { it.symbol.trim().lowercase() }.toSet().size != d.units.size) invalid("doppelte Einheiten")
        if (d.quantityTypes.any { it.symbol.isBlank() || it.name.isBlank() }) invalid("Menge-Art unvollständig")
        if (d.quantityTypes.map { it.symbol.trim().lowercase() }.toSet().size != d.quantityTypes.size) {
            invalid("doppelte Menge-Arten")
        }

        val barcodes = d.products.mapNotNull { it.barcode?.trim()?.takeIf(String::isNotEmpty) }
        if (barcodes.toSet().size != barcodes.size) invalid("doppelte Barcodes")
        d.products.forEach { p ->
            if (p.name.isBlank()) invalid("Produkt ${p.id} ohne Namen")
            if (p.unitId != null && p.unitId !in unitIds) invalid("Produkt ${p.id}: unbekannte Einheit")
            if (ProductSource.entries.none { it.name == p.source }) invalid("Produkt ${p.id}: Quelle")
            p.packageSize?.let { size ->
                val n = size.toBigDecimalOrNull()
                if (n == null || n.signum() <= 0) invalid("Produkt ${p.id}: Packungsgröße")
            }
        }
        d.stockEntries.forEach { e ->
            if (e.productId !in productIds) invalid("Eintrag ${e.id}: unbekanntes Produkt")
            if (e.locationId != null && e.locationId !in locationIds) invalid("Eintrag ${e.id}: unbekannter Ort")
            if (e.quantityTypeId !in typeIds) invalid("Eintrag ${e.id}: unbekannte Menge-Art")
            if (e.quantity < 1) invalid("Eintrag ${e.id}: Anzahl < 1")
            if ((e.level ?: 0) < 0 || (e.shelf ?: 0) < 0) invalid("Eintrag ${e.id}: Ebene/Fach")
            listOfNotNull(e.expiryDate, e.openedAt, e.frozenAt).forEach {
                try {
                    LocalDate.parse(it)
                } catch (_: DateTimeParseException) {
                    invalid("Eintrag ${e.id}: Datum")
                }
            }
        }
        // Fehlende Bilder sind kein Abbruchgrund – das Produkt wird dann ohne Bild importiert.
    }

    companion object {
        const val FORMAT_VERSION = 1
        const val JSON_NAME = "backup.json"
        const val IMAGES_PREFIX = "images/"
        private val SAFE_NAME = Regex("^[A-Za-z0-9._-]{1,128}$")
        private val GROUP_ID = Regex("^[a-z][a-z0-9_]{0,39}$")
    }

    private fun productIdsOf(d: BackupData): Set<String> = d.products.map { it.id }.toSet()
}
