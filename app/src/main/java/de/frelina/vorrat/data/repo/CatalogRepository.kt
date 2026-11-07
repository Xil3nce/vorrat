package de.frelina.vorrat.data.repo

import android.database.sqlite.SQLiteConstraintException
import de.frelina.vorrat.data.db.LocationDao
import de.frelina.vorrat.data.db.LocationEntity
import de.frelina.vorrat.data.db.QuantityTypeDao
import de.frelina.vorrat.data.db.QuantityTypeEntity
import de.frelina.vorrat.data.db.UnitDao
import de.frelina.vorrat.data.db.UnitEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SaveResult {
    data class Saved(val id: Long) : SaveResult
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
) {
    // ---------- Orte ----------
    fun observeLocations(): Flow<List<LocationEntity>> = locationDao.observeAll()

    suspend fun addLocation(name: String): SaveResult {
        val n = name.trim()
        if (n.isEmpty()) return SaveResult.Blank
        if (locationDao.findByName(n) != null) return SaveResult.Duplicate
        return guardUnique { SaveResult.Saved(locationDao.insert(LocationEntity(name = n))) }
    }

    suspend fun renameLocation(location: LocationEntity, newName: String): SaveResult {
        val n = newName.trim()
        if (n.isEmpty()) return SaveResult.Blank
        val existing = locationDao.findByName(n)
        if (existing != null && existing.id != location.id) return SaveResult.Duplicate
        return guardUnique {
            locationDao.update(location.copy(name = n))
            SaveResult.Saved(location.id)
        }
    }

    suspend fun deleteLocation(location: LocationEntity): DeleteResult {
        val usages = locationDao.countStockEntries(location.id)
        if (usages > 0) return DeleteResult.InUse(usages)
        locationDao.delete(location)
        return DeleteResult.Deleted
    }

    // ---------- Packungseinheiten ----------
    fun observeUnits(): Flow<List<UnitEntity>> = unitDao.observeAll()

    suspend fun saveUnit(id: Long?, symbol: String, name: String): SaveResult {
        val s = symbol.trim()
        val n = name.trim()
        if (s.isEmpty() || n.isEmpty()) return SaveResult.Blank
        val existing = unitDao.findBySymbol(s)
        if (existing != null && existing.id != id) return SaveResult.Duplicate
        return guardUnique {
            if (id == null) {
                SaveResult.Saved(unitDao.insert(UnitEntity(symbol = s, name = n)))
            } else {
                unitDao.update(UnitEntity(id = id, symbol = s, name = n))
                SaveResult.Saved(id)
            }
        }
    }

    suspend fun deleteUnit(unit: UnitEntity): DeleteResult {
        val usages = unitDao.countProducts(unit.id)
        if (usages > 0) return DeleteResult.InUse(usages)
        unitDao.delete(unit)
        return DeleteResult.Deleted
    }

    // ---------- Menge-Arten ----------
    fun observeQuantityTypes(): Flow<List<QuantityTypeEntity>> = quantityTypeDao.observeAll()

    suspend fun saveQuantityType(id: Long?, symbol: String, name: String): SaveResult {
        val s = symbol.trim()
        val n = name.trim()
        if (s.isEmpty() || n.isEmpty()) return SaveResult.Blank
        val existing = quantityTypeDao.findBySymbol(s)
        if (existing != null && existing.id != id) return SaveResult.Duplicate
        return guardUnique {
            if (id == null) {
                SaveResult.Saved(quantityTypeDao.insert(QuantityTypeEntity(symbol = s, name = n)))
            } else {
                quantityTypeDao.update(QuantityTypeEntity(id = id, symbol = s, name = n))
                SaveResult.Saved(id)
            }
        }
    }

    suspend fun deleteQuantityType(type: QuantityTypeEntity): DeleteResult {
        val usages = quantityTypeDao.countStockEntries(type.id)
        if (usages > 0) return DeleteResult.InUse(usages)
        quantityTypeDao.delete(type)
        return DeleteResult.Deleted
    }

    private inline fun guardUnique(block: () -> SaveResult): SaveResult = try {
        block()
    } catch (_: SQLiteConstraintException) {
        SaveResult.Duplicate
    }
}
