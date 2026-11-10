package de.frelina.vorrat.data.repo

import de.frelina.vorrat.data.db.MealPlanDao
import de.frelina.vorrat.data.db.MealPlanEntity
import de.frelina.vorrat.data.db.StapleDao
import de.frelina.vorrat.data.db.StapleEntity
import de.frelina.vorrat.data.sync.RemoteCollection
import de.frelina.vorrat.data.sync.RemoteMapper
import de.frelina.vorrat.data.sync.RemoteWriter
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Wochenplan: welches Rezept wann für wie viele Personen gekocht wird (im Haushalt geteilt). */
@Singleton
class MealPlanRepository @Inject constructor(
    private val dao: MealPlanDao,
    private val remote: RemoteWriter,
) {
    /** Einträge von [from] bis [to] (beide inklusive), nach Tag und Anlegezeit sortiert. */
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<MealPlanEntity>> = dao.observeBetween(from, to)

    suspend fun getBetween(from: LocalDate, to: LocalDate): List<MealPlanEntity> = dao.getBetween(from, to)

    suspend fun add(date: LocalDate, recipeId: String, persons: Int): MealPlanEntity {
        val entry = MealPlanEntity(
            date = date,
            recipeId = recipeId,
            persons = persons.coerceIn(1, RemoteMapper.MAX_PLAN_PERSONS),
            createdAt = System.currentTimeMillis(),
        )
        save(entry)
        return entry
    }

    suspend fun move(id: String, date: LocalDate) {
        val entry = dao.getById(id) ?: return
        if (entry.date != date) save(entry.copy(date = date))
    }

    suspend fun setPersons(id: String, persons: Int) {
        val entry = dao.getById(id) ?: return
        save(entry.copy(persons = persons.coerceIn(1, RemoteMapper.MAX_PLAN_PERSONS)))
    }

    /** Entfernt den Eintrag; liefert ihn für "Rückgängig". */
    suspend fun remove(id: String): MealPlanEntity? {
        val entry = dao.getById(id) ?: return null
        dao.deleteById(id)
        remote.delete(RemoteCollection.MEAL_PLAN, id)
        return entry
    }

    suspend fun restore(entry: MealPlanEntity) = save(entry)

    private suspend fun save(entry: MealPlanEntity) {
        dao.upsert(entry)
        remote.putMealPlan(entry)
    }
}

/** "Immer zuhause": Zutaten, die nicht im Bestand geführt werden, aber immer da sind (im Haushalt geteilt). */
@Singleton
class StapleRepository @Inject constructor(
    private val dao: StapleDao,
    private val remote: RemoteWriter,
) {
    fun observeAll(): Flow<List<StapleEntity>> = dao.observeAll()

    suspend fun getAll(): List<StapleEntity> = dao.getAll()

    /** Fügt hinzu (oder aktualisiert die Bezeichnung); [key] siehe MealPlanShopping.stapleKey. */
    suspend fun add(key: String, label: String) {
        if (!RemoteMapper.isValidStapleKey(key)) return
        val text = label.trim().take(RemoteMapper.MAX_STAPLE_LABEL).takeIf(String::isNotEmpty) ?: return
        val entity = StapleEntity(key, text, System.currentTimeMillis())
        dao.upsert(entity)
        remote.putStaple(entity)
    }

    suspend fun remove(key: String) {
        dao.deleteByKey(key)
        remote.delete(RemoteCollection.STAPLES, key)
    }
}
