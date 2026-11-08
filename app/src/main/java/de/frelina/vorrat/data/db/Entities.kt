package de.frelina.vorrat.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/** Neue, geräteübergreifend eindeutige ID (Sync: beide Handys legen unabhängig Datensätze an). */
fun newId(): String = UUID.randomUUID().toString()

/**
 * Lagerort, z. B. "Keller" oder "Kühlschrank". Name eindeutig (ohne Groß-/Kleinschreibung) –
 * geprüft im CatalogRepository. Seit Schema 2 kein UNIQUE-Index mehr: Room spiegelt den
 * Firestore-Stand, und zwei Handys können offline denselben Namen anlegen.
 */
@Entity(
    tableName = "locations",
    indices = [Index(value = ["name"])],
)
data class LocationEntity(
    @PrimaryKey val id: String = newId(),
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    /** Anzahl Ebenen (optional); Bestandseinträge wählen dann Ebene 1 … [levels] per Auswahlliste. */
    val levels: Int? = null,
    /** Anzahl Fächer (optional); Auswahl Fach 1 … [shelves]. */
    val shelves: Int? = null,
) {
    companion object {
        /** Obergrenze für Ebenen/Fächer je Ort (Auswahlliste bleibt handhabbar). */
        const val MAX_SLOTS = 50
    }
}

/** Packungseinheit (g, kg, ml, l, …). */
@Entity(
    tableName = "units",
    indices = [Index(value = ["symbol"])],
)
data class UnitEntity(
    @PrimaryKey val id: String = newId(),
    @ColumnInfo(collate = ColumnInfo.NOCASE) val symbol: String,
    val name: String,
)

/** Menge-Art (Stck., Pck., …). */
@Entity(
    tableName = "quantity_types",
    indices = [Index(value = ["symbol"])],
)
data class QuantityTypeEntity(
    @PrimaryKey val id: String = newId(),
    @ColumnInfo(collate = ColumnInfo.NOCASE) val symbol: String,
    val name: String,
)

enum class ProductSource { OPEN_FOOD_FACTS, MANUAL }

@Entity(
    tableName = "products",
    foreignKeys = [
        ForeignKey(
            entity = UnitEntity::class,
            parentColumns = ["id"],
            childColumns = ["unitId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        // Eindeutigkeit (falls gesetzt) prüft das Formular; kein UNIQUE-Index (siehe LocationEntity).
        Index(value = ["barcode"]),
        Index(value = ["unitId"]),
        Index(value = ["category"]),
    ],
)
data class ProductEntity(
    @PrimaryKey val id: String = newId(),
    val barcode: String?,
    val name: String,
    val brand: String?,
    val category: String?,
    val packageSize: BigDecimal?,
    val unitId: String?,
    /** Dateiname relativ zum App-internen Bildordner (siehe ImageStore). */
    val imagePath: String?,
    val source: ProductSource,
    val createdAt: Long,
    val updatedAt: Long,
    /** Mindestbestand (Summe aller Einträge); darunter steht das Produkt auf der Einkaufsliste. Seit Schema 5. */
    val minStock: Int? = null,
    /** Zuletzt verwendete Haltbarkeit nach dem Öffnen in Tagen (Vorschlag beim nächsten Öffnen). Seit Schema 5. */
    val openedDays: Int? = null,
)

@Entity(
    tableName = "stock_entries",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["locationId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = QuantityTypeEntity::class,
            parentColumns = ["id"],
            childColumns = ["quantityTypeId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["productId"]),
        Index(value = ["locationId"]),
        Index(value = ["quantityTypeId"]),
        Index(value = ["expiryDate"]),
        Index(value = ["nfcTagId"]),
    ],
)
data class StockEntryEntity(
    @PrimaryKey val id: String = newId(),
    val productId: String,
    /** Ort; null = noch keinem Ort zugeordnet (z. B. im Laden erfasst). Seit Schema 5 optional. */
    val locationId: String?,
    /** Ebene (optional). */
    val level: Int?,
    /** Fach (optional). */
    val shelf: Int?,
    val expiryDate: LocalDate?,
    /** Immer > 0; bei 0 wird der Eintrag gelöscht (siehe StockRepository). */
    val quantity: Int,
    val quantityTypeId: String,
    val note: String?,
    /** Geöffnet am (nur für geöffnete Packungen). Seit Schema 5. */
    val openedAt: LocalDate? = null,
    /** Haltbarkeit nach dem Öffnen in Tagen (zusammen mit [openedAt]). */
    val openedDays: Int? = null,
    /** Selbst eingefroren am (z. B. Bolognese-Portionen); null = gekaufte Ware. Seit Schema 6. */
    val frozenAt: LocalDate? = null,
    /** Kennung (UID, Hex) des verknüpften NFC-Chips; ein Chip gehört höchstens zu einem Eintrag. Seit Schema 6. */
    val nfcTagId: String? = null,
)

enum class HistoryAction {
    /** Hinzugefügt (neuer Eintrag oder Anzahl erhöht). */
    ADDED,

    /** Entnommen (Anzahl verringert, bei 0 Eintrag entfernt). */
    REMOVED,

    /** Eintrag gelöscht (Korrektur, nicht verbraucht). */
    DELETED,

    /** Packung geöffnet. */
    OPENED,

    /** Nachricht an den Haushalt, z. B. "Milch ist alle". */
    MESSAGE,
}

/**
 * Verlauf im Haushalt: wer hat wann was entnommen oder hinzugefügt (synchronisiert, ohne Fremdschlüssel –
 * Produkte/Einträge können längst gelöscht sein; Name und Menge-Art werden deshalb mitgespeichert).
 * Seit Schema 6.
 */
@Entity(
    tableName = "history_events",
    indices = [Index(value = ["createdAt"])],
)
data class HistoryEventEntity(
    @PrimaryKey val id: String = newId(),
    val createdAt: Long,
    val action: HistoryAction,
    /** UID der Mitgliedschaft (Firebase) des Geräts, das den Eintrag erzeugt hat; null ohne Haushalt. */
    val authorUid: String?,
    /** Angezeigter Name der Person (Einstellungen → Dein Name), optional. */
    val authorName: String?,
    val productId: String?,
    val productName: String,
    /** Geänderte Anzahl (immer positiv), bei MESSAGE null. */
    val quantity: Int?,
    val quantityTypeSymbol: String?,
    val entryId: String?,
    /** Nach dieser Änderung ist vom Produkt nichts mehr da ("ist alle"). */
    val outOfStock: Boolean = false,
    /** Freitext (MESSAGE). */
    val message: String? = null,
    /** Andere Person per Benachrichtigung informieren. */
    val notify: Boolean = false,
)

/**
 * Einkaufsliste (gemeinsam im Haushalt). Einträge unter Mindestbestand werden nicht gespeichert,
 * sondern aus den Produkten berechnet (siehe ShoppingLogic); hier stehen nur manuell hinzugefügte.
 */
@Entity(tableName = "shopping_items")
data class ShoppingItemEntity(
    @PrimaryKey val id: String = newId(),
    /** Verknüpftes Produkt (optional, ohne Fremdschlüssel: Produkt kann auf dem anderen Handy fehlen). */
    val productId: String?,
    val name: String,
    val quantity: Int,
    val note: String?,
    val createdAt: Long,
)

/**
 * Lokaler Cache für Barcode-Abfragen bei Open Food Facts (auch "nicht gefunden"),
 * damit dieselbe Abfrage nicht wiederholt werden muss.
 */
@Entity(tableName = "lookup_cache")
data class LookupCacheEntity(
    @PrimaryKey val barcode: String,
    val found: Boolean,
    val name: String?,
    val brand: String?,
    val category: String?,
    val packageSize: BigDecimal?,
    val unitSymbol: String?,
    /** Lokal heruntergeladenes Bild (Dateiname im Bildordner). */
    val imagePath: String?,
    val fetchedAt: Long,
    /** Nährwerte als JSON (siehe NutritionParser), null wenn keine vorhanden. Seit Schema 4. */
    val nutrition: String? = null,
    /**
     * true, sobald für diesen Eintrag Nährwerte abgefragt wurden (auch wenn OFF keine hat).
     * Einträge von vor Schema 4 haben false → die Detailansicht fragt einmal nach.
     */
    @ColumnInfo(defaultValue = "0") val nutritionLoaded: Boolean = false,
    /** Haltbarkeit nach dem Öffnen laut OFF-Lagerhinweis (Tage), falls erkennbar. Seit Schema 5. */
    val openedDays: Int? = null,
)
