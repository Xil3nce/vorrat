package de.frelina.vorrat.data.repo

import android.database.sqlite.SQLiteConstraintException
import de.frelina.vorrat.data.db.LocationDao
import de.frelina.vorrat.data.db.LocationEntity
import de.frelina.vorrat.data.db.QuantityTypeDao
import de.frelina.vorrat.data.db.QuantityTypeEntity
import de.frelina.vorrat.data.db.UnitDao
import de.frelina.vorrat.data.db.UnitEntity
import de.frelina.vorrat.data.sync.RemoteCollection
import de.frelina.vorrat.data.sync.RemoteWriter
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SaveResult {
    data class Saved(val id: String) : SaveResult
    data object Blank : SaveResult
    data object Duplicate : SaveResult
}

sealed interface DeleteResult {
    data object Deleted : DeleteResult

    /** Löschschutz: wird noch von [usages] Einträgen verwendet. */
    data class InUse(val usages: Int) : DeleteResult
}

/** Orte, Packungseinheiten und Menge-Arten (Stammdaten mit Löschschutz). */
@Singleton
class CatalogRepository @Inject constructor(
    private val locationDao: LocationDao,
    private val unitDao: UnitDao,
    private val quantityTypeDao: QuantityTypeDao,
    private val remote: RemoteWriter,
) {
    // ---------- Orte ----------
    fun observeLocations(): Flow<List<LocationEntity>> = locationDao.observeAll()

    suspend fun addLocation(name: String, levels: Int? = null, shelves: Int? = null): SaveResult {
        val n = name.trim()
        if (n.isEmpty()) return SaveResult.Blank
        if (locationDao.findByName(n) != null) return SaveResult.Duplicate
        val location = LocationEntity(name = n, levels = levels.slotCount(), shelves = shelves.slotCount())
        return guardUnique {
            locationDao.insert(location)
            SaveResult.Saved(location.id)
        }.also { if (it is SaveResult.Saved) remote.putLocation(location) }
    }

    suspend fun renameLocation(location: LocationEntity, newName: String): SaveResult =
        updateLocation(location, newName, location.levels, location.shelves)

    /** Name sowie Anzahl Ebenen/Fächer ändern (null bzw. ≤ 0 = nicht festgelegt). */
    suspend fun updateLocation(location: LocationEntity, newName: String, levels: Int?, shelves: Int?): SaveResult {
        val n = newName.trim()
        if (n.isEmpty()) return SaveResult.Blank
        val existing = locationDao.findByName(n)
        if (existing != null && existing.id != location.id) return SaveResult.Duplicate
        val renamed = location.copy(name = n, levels = levels.slotCount(), shelves = shelves.slotCount())
        return guardUnique {
            locationDao.update(renamed)
            SaveResult.Saved(location.id)
        }.also { if (it is SaveResult.Saved) remote.putLocation(renamed) }
    }

    suspend fun deleteLocation(location: LocationEntity): DeleteResult {
        val usages = locationDao.countStockEntries(location.id)
        if (usages > 0) return DeleteResult.InUse(usages)
        locationDao.delete(location)
        remote.delete(RemoteCollection.LOCATIONS, location.id)
        return DeleteResult.Deleted
    }

    // ---------- Packungseinheiten ----------
    fun observeUnits(): Flow<List<UnitEntity>> = unitDao.observeAll()

    suspend fun saveUnit(id: String?, symbol: String, name: String): SaveResult {
        val s = symbol.trim()
        val n = name.trim()
        if (s.isEmpty() || n.isEmpty()) return SaveResult.Blank
        val existing = unitDao.findBySymbol(s)
        if (existing != null && existing.id != id) return SaveResult.Duplicate
        val unit = if (id == null) UnitEntity(symbol = s, name = n) else UnitEntity(id = id, symbol = s, name = n)
        return guardUnique {
            if (id == null) unitDao.insert(unit) else unitDao.update(unit)
            SaveResult.Saved(unit.id)
        }.also { if (it is SaveResult.Saved) remote.putUnit(unit) }
    }

    suspend fun deleteUnit(unit: UnitEntity): DeleteResult {
        val usages = unitDao.countProducts(unit.id)
        if (usages > 0) return DeleteResult.InUse(usages)
        unitDao.delete(unit)
        remote.delete(RemoteCollection.UNITS, unit.id)
        return DeleteResult.Deleted
    }

    // ---------- Menge-Arten ----------
    fun observeQuantityTypes(): Flow<List<QuantityTypeEntity>> = quantityTypeDao.observeAll()

    suspend fun saveQuantityType(id: String?, symbol: String, name: String): SaveResult {
        val s = symbol.trim()
        val n = name.trim()
        if (s.isEmpty() || n.isEmpty()) return SaveResult.Blank
        val existing = quantityTypeDao.findBySymbol(s)
        if (existing != null && existing.id != id) return SaveResult.Duplicate
        val type = if (id == null) {
            QuantityTypeEntity(symbol = s, name = n)
        } else {
            QuantityTypeEntity(id = id, symbol = s, name = n)
        }
        return guardUnique {
            if (id == null) quantityTypeDao.insert(type) else quantityTypeDao.update(type)
            SaveResult.Saved(type.id)
        }.also { if (it is SaveResult.Saved) remote.putQuantityType(type) }
    }

    suspend fun deleteQuantityType(type: QuantityTypeEntity): DeleteResult {
        val usages = quantityTypeDao.countStockEntries(type.id)
        if (usages > 0) return DeleteResult.InUse(usages)
        quantityTypeDao.delete(type)
        remote.delete(RemoteCollection.QUANTITY_TYPES, type.id)
        return DeleteResult.Deleted
    }

    private inline fun guardUnique(block: () -> SaveResult): SaveResult = try {
        block()
    } catch (_: SQLiteConstraintException) {
        SaveResult.Duplicate
    }
}

private fun Int?.slotCount(): Int? = this?.takeIf { it > 0 }?.coerceAtMost(LocationEntity.MAX_SLOTS)
