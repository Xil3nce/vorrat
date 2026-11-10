package de.frelina.vorrat.data.repo

import de.frelina.vorrat.data.db.DepartmentAssignmentDao
import de.frelina.vorrat.data.db.DepartmentAssignmentEntity
import de.frelina.vorrat.data.db.DepartmentDao
import de.frelina.vorrat.data.db.DepartmentEntity
import de.frelina.vorrat.data.sync.RemoteCollection
import de.frelina.vorrat.data.sync.RemoteMapper
import de.frelina.vorrat.data.sync.RemoteWriter
import de.frelina.vorrat.domain.DepartmentDefaults
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class DepartmentNameResult { OK, INVALID, DUPLICATE }

/**
 * Abteilungen der Einkaufsliste und die gemerkten Zuordnungen "Artikel → Abteilung" (beides im Haushalt geteilt).
 */
@Singleton
class DepartmentRepository @Inject constructor(
    private val departmentDao: DepartmentDao,
    private val assignmentDao: DepartmentAssignmentDao,
    private val remote: RemoteWriter,
) {
    fun observeDepartments(): Flow<List<DepartmentEntity>> = departmentDao.observeAll()

    /** Schlüssel ("product:…"/"name:…") → Abteilungs-ID. */
    fun observeAssignments(): Flow<Map<String, String>> =
        assignmentDao.observeAll().map { list -> list.associate { it.key to it.departmentId } }

    suspend fun getAll(): List<DepartmentEntity> = departmentDao.getAll()

    /**
     * Legt die Standard-Abteilungen an, wenn es lokal noch keine gibt (feste IDs → idempotent: beide Handys
     * schreiben dieselben Dokumente). Liefert true, wenn etwas angelegt wurde.
     */
    suspend fun ensureDefaults(): Boolean {
        if (departmentDao.count() > 0) return false
        val defaults = DepartmentDefaults.entities()
        departmentDao.upsertAll(defaults)
        defaults.forEach { remote.putDepartment(it) }
        return true
    }

    /** Artikel einer Abteilung zuordnen (gilt künftig für diesen Artikel). */
    suspend fun assign(key: String, departmentId: String) {
        if (!RemoteMapper.isValidAssignmentKey(key)) return
        val entity = DepartmentAssignmentEntity(key, departmentId, System.currentTimeMillis())
        assignmentDao.upsert(entity)
        remote.putDepartmentAssignment(entity)
    }

    /** Speichert die Reihenfolge ([orderedIds]); nicht genannte Abteilungen folgen in bisheriger Reihenfolge. */
    suspend fun saveOrder(orderedIds: List<String>) {
        val current = departmentDao.getAll()
        val byId = current.associateBy { it.id }
        val ordered = orderedIds.distinct().mapNotNull(byId::get)
        val rest = current.filter { it.id !in orderedIds.toSet() }
        (ordered + rest).forEachIndexed { index, d ->
            if (d.position != index) save(d.copy(position = index))
        }
    }

    suspend fun add(name: String): DepartmentNameResult {
        val result = check(name, ignoreId = null)
        if (result != DepartmentNameResult.OK) return result
        val position = (departmentDao.getAll().maxOfOrNull { it.position } ?: -1) + 1
        save(DepartmentEntity(name = name.trim(), position = position))
        return DepartmentNameResult.OK
    }

    suspend fun rename(id: String, name: String): DepartmentNameResult {
        val result = check(name, ignoreId = id)
        if (result != DepartmentNameResult.OK) return result
        val department = departmentDao.getById(id) ?: return DepartmentNameResult.INVALID
        if (department.name != name.trim()) save(department.copy(name = name.trim()))
        return DepartmentNameResult.OK
    }

    /** Löscht eine Abteilung samt ihrer Zuordnungen (die Artikel fallen auf ihre Standard-Abteilung zurück). "Sonstiges" bleibt. */
    suspend fun delete(id: String): Boolean {
        if (id == DepartmentDefaults.OTHER) return false
        departmentDao.getById(id) ?: return false
        assignmentDao.getForDepartment(id).forEach { a ->
            assignmentDao.deleteByKey(a.key)
            remote.delete(RemoteCollection.DEPARTMENT_ASSIGNMENTS, a.key)
        }
        departmentDao.deleteById(id)
        remote.delete(RemoteCollection.DEPARTMENTS, id)
        return true
    }

    /**
     * Reihenfolge und Namen der Standard-Abteilungen wiederherstellen (auch gelöschte kommen zurück);
     * eigene Abteilungen folgen danach in ihrer bisherigen Reihenfolge.
     */
    suspend fun resetToDefaults() {
        val defaults = DepartmentDefaults.entities()
        val custom = departmentDao.getAll().filter { !DepartmentDefaults.isDefault(it.id) }
        val existing = departmentDao.getAll().associateBy { it.id }
        val target = defaults + custom.mapIndexed { i, d -> d.copy(position = defaults.size + i) }
        target.filter { existing[it.id] != it }.forEach { save(it) }
    }

    private suspend fun check(name: String, ignoreId: String?): DepartmentNameResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > RemoteMapper.MAX_DEPARTMENT_NAME) return DepartmentNameResult.INVALID
        val taken = departmentDao.getAll().any { it.id != ignoreId && it.name.equals(trimmed, ignoreCase = true) }
        return if (taken) DepartmentNameResult.DUPLICATE else DepartmentNameResult.OK
    }

    private suspend fun save(department: DepartmentEntity) {
        departmentDao.upsert(department)
        remote.putDepartment(department)
    }
}
