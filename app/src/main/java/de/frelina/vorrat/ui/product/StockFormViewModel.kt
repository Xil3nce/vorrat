package de.frelina.vorrat.ui.product

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.frelina.vorrat.R
import de.frelina.vorrat.data.db.LocationEntity
import de.frelina.vorrat.data.db.ProductEntity
import de.frelina.vorrat.data.db.ProductSource
import de.frelina.vorrat.data.db.StockEntryEntity
import de.frelina.vorrat.data.db.QuantityTypeEntity
import de.frelina.vorrat.data.db.UnitEntity
import de.frelina.vorrat.data.image.ImageStore
import de.frelina.vorrat.data.off.LookupErrorKind
import de.frelina.vorrat.data.repo.CatalogRepository
import de.frelina.vorrat.data.repo.ProductRepository
import de.frelina.vorrat.data.repo.SaveResult
import de.frelina.vorrat.data.repo.ScanRepository
import de.frelina.vorrat.data.repo.ShoppingRepository
import de.frelina.vorrat.data.repo.StockRepository
import de.frelina.vorrat.ui.components.UiMessage
import de.frelina.vorrat.ui.components.UiMessages
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class FormMode {
    /** Neues Produkt + erster Bestandseintrag (manuell oder mit OFF-Vorbelegung). */
    NEW_PRODUCT,

    /** Neuer Bestandseintrag für ein bekanntes Produkt. */
    EXISTING_PRODUCT,

    /** Bestehenden Bestandseintrag bearbeiten. */
    EDIT_ENTRY,

    /** Nur Produktdaten bearbeiten. */
    EDIT_PRODUCT,
}

data class StockFormState(
    val mode: FormMode = FormMode.NEW_PRODUCT,
    val loading: Boolean = true,
    val product: ProductForm = ProductForm(),
    /** Bekanntes Produkt (für EXISTING_PRODUCT/EDIT_ENTRY als Kopfzeile). */
    val existingProduct: ProductEntity? = null,
    val entry: EntryForm = EntryForm(),
    val prefilledFromOff: Boolean = false,
    val packageUnparsed: Boolean = false,
    val lookupError: LookupErrorKind? = null,
    val lookupNotFound: Boolean = false,
    val saving: Boolean = false,
    val imageError: Boolean = false,
    /** Gleicher Eintrag existiert schon → Nutzer fragen, ob zusammengeführt werden soll. */
    val mergeOffer: MergeOffer? = null,
    /** Selbst Eingefrorenes erfassen: vereinfachter Produktteil (nur Name, Bild). */
    val frozenMode: Boolean = false,
)

/** [entry] (validiert, noch nicht gespeichert) hat dieselben Daten wie der bestehende Eintrag [target]. */
data class MergeOffer(val entry: StockEntryEntity, val target: StockEntryEntity)

/**
 * Gemeinsames ViewModel für "Zum Bestand hinzufügen", "Produkt manuell anlegen",
 * "Eintrag bearbeiten" und "Produkt bearbeiten" (unterschieden über die Routen-Argumente).
 */
@HiltViewModel
class StockFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val stockRepository: StockRepository,
    private val productRepository: ProductRepository,
    private val catalogRepository: CatalogRepository,
    private val scanRepository: ScanRepository,
    private val shoppingRepository: ShoppingRepository,
    private val imageStore: ImageStore,
    private val messages: UiMessages,
    private val purchaseDrafts: de.frelina.vorrat.data.purchase.PurchaseDraftStore,
) : ViewModel() {

    // Die Routen haben unterschiedliche Argumentnamen → Modus eindeutig bestimmbar.
    private val entryId: String? = savedStateHandle.get<String>("editEntryId")
    private val editProductId: String? = savedStateHandle.get<String>("editProductId")
    private val addProductId: String? = savedStateHandle.get<String>("productId")?.takeIf { it.isNotBlank() }
    private val barcodeArg: String? = savedStateHandle.get<String>("barcode")?.takeIf { it.isNotBlank() }
    private val copyFromEntryId: String? = savedStateHandle.get<String>("copyFromEntryId")
    private val frozenArg: Boolean = savedStateHandle.get<Boolean>("frozen") == true
    private val nfcTagArg: String? = savedStateHandle.get<String>("nfcTagId")?.takeIf { it.isNotBlank() }

    /** Artikel aus dem Einkauf ("+ Bestand"): Speichern schreibt in den Einkauf, nicht in den Bestand. */
    private val draftId: String? = savedStateHandle.get<String>("draftId")?.takeIf { it.isNotBlank() }
    val draftMode: Boolean = draftId != null

    /**
     * ID des neuen Eintrags – steht schon vor dem Speichern fest, damit ein NFC-Chip bereits im Formular
     * beschrieben werden kann. Beim Bearbeiten die ID des bestehenden Eintrags.
     */
    val nfcEntryId: String = entryId ?: draftId ?: savedStateHandle.get<String>(KEY_NEW_ENTRY_ID)
        ?: de.frelina.vorrat.data.db.newId().also { savedStateHandle[KEY_NEW_ENTRY_ID] = it }

    /** Kauf von der Einkaufsliste: dieser Eintrag wird nach dem Speichern entfernt. */
    private val shoppingItemId: String? = savedStateHandle.get<String>("shoppingItemId")?.takeIf { it.isNotBlank() }

    /** Duplizieren: Formular mit den Werten eines bestehenden Eintrags; gespeichert wird ein neuer Eintrag. */
    val duplicating: Boolean = copyFromEntryId != null

    /** Neues Produkt aus einem Scan (Barcode als Routen-Argument) – steuert nur den Titel, nicht die Eingabe. */
    val fromScan: Boolean = barcodeArg != null

    private val _state = MutableStateFlow(StockFormState())
    val state: StateFlow<StockFormState> = _state.asStateFlow()

    val locations: StateFlow<List<LocationEntity>> =
        catalogRepository.observeLocations().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val units: StateFlow<List<UnitEntity>> =
        catalogRepository.observeUnits().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val quantityTypes: StateFlow<List<QuantityTypeEntity>> =
        catalogRepository.observeQuantityTypes().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val categories: StateFlow<List<String>> =
        productRepository.observeCategories().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Namen bisheriger Produkte ohne Barcode (Vorschläge beim Einfrieren, z. B. "Bolognese"). */
    val manualProductNames: StateFlow<List<String>> = productRepository.observeAll()
        .map { list -> list.filter { it.barcode == null }.map { it.name }.distinct() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _saved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saved: SharedFlow<Unit> = _saved

    /** Bild stammt aus dem OFF-Cache und muss beim Speichern kopiert werden. */
    private var imageFromCache = false

    /** Vom Nutzer neu aufgenommenes, noch nicht gespeichertes Bild. */
    private var pendingImage: String? = null

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val types = catalogRepository.observeQuantityTypes().first()
        val locs = catalogRepository.observeLocations().first()
        // Kauf von der Einkaufsliste: Anzahl des Listeneintrags übernehmen (auch nach dem Scan).
        val shoppingItem = shoppingItemId?.let { shoppingRepository.getById(it) }
        val defaultEntry = EntryForm(
            quantityTypeId = types.firstOrNull()?.id,
            locationId = locs.singleOrNull()?.id,
            quantity = shoppingItem?.quantity?.toString() ?: "1",
        )
        when {
            draftId != null -> loadDraft(draftId, defaultEntry)
            entryId != null -> {
                val entry = stockRepository.getEntry(entryId)
                val product = entry?.let { productRepository.getById(it.productId) }
                _state.value = StockFormState(
                    mode = FormMode.EDIT_ENTRY,
                    loading = false,
                    existingProduct = product,
                    entry = entry?.let(EntryForm::from) ?: defaultEntry,
                )
            }
            editProductId != null -> {
                val product = productRepository.getById(editProductId)
                _state.value = StockFormState(
                    mode = FormMode.EDIT_PRODUCT,
                    loading = false,
                    existingProduct = product,
                    product = product?.let(ProductForm::from) ?: ProductForm(),
                )
            }
            copyFromEntryId != null -> {
                val source = stockRepository.getEntry(copyFromEntryId)
                if (source == null) {
                    _state.value = StockFormState(FormMode.NEW_PRODUCT, loading = false, entry = defaultEntry)
                } else {
                    loadExisting(source.productId, EntryForm.from(source))
                }
            }
            addProductId != null -> loadExisting(addProductId, defaultEntry)
            barcodeArg != null -> loadForBarcode(barcodeArg, defaultEntry)
            frozenArg -> {
                val portionType = catalogRepository.ensureQuantityType(
                    context.getString(R.string.frozen_portion_symbol),
                    context.getString(R.string.frozen_portion_name),
                )
                _state.value = StockFormState(
                    FormMode.NEW_PRODUCT,
                    loading = false,
                    frozenMode = true,
                    entry = defaultEntry.copy(
                        quantityTypeId = portionType ?: defaultEntry.quantityTypeId,
                        frozen = true,
                        frozenAt = de.frelina.vorrat.domain.Validation.formatDate(java.time.LocalDate.now()),
                        nfcTagId = nfcTagArg,
                    ),
                )
            }
            else -> {
                // Freitext von der Einkaufsliste ohne Barcode (Button im Kauf-Scanner): nur Name und Anzahl übernehmen.
                _state.value = StockFormState(
                    FormMode.NEW_PRODUCT,
                    loading = false,
                    entry = defaultEntry,
                    product = ProductForm(name = shoppingItem?.name.orEmpty()),
                )
            }
        }
    }

    private suspend fun loadDraft(id: String, defaultEntry: EntryForm) {
        val draft = purchaseDrafts.get(id)
        if (draft == null) {
            _state.value = StockFormState(FormMode.NEW_PRODUCT, loading = false, entry = defaultEntry)
            _saved.tryEmit(Unit) // schon übernommen oder entfernt
            return
        }
        val product = draft.productId?.let { productRepository.getById(it) }
        _state.value = if (product != null) {
            StockFormState(FormMode.EXISTING_PRODUCT, loading = false, existingProduct = product, entry = draft.toEntryForm())
        } else {
            imageFromCache = draft.imageFromCache
            StockFormState(
                FormMode.NEW_PRODUCT,
                loading = false,
                product = draft.toProductForm(),
                entry = draft.toEntryForm(),
                prefilledFromOff = draft.fromOff,
            )
        }
    }

    /** Einkauf: Eingaben prüfen und in den Artikel übernehmen (noch nicht in den Bestand). */
    private suspend fun saveDraft(id: String, s: StockFormState) {
        val draft = purchaseDrafts.get(id)
        if (draft == null) {
            _saved.tryEmit(Unit)
            return
        }
        val newProduct = s.mode == FormMode.NEW_PRODUCT
        val (productForm, fields) = if (newProduct) s.product.validate() else s.product to null
        val (entryForm, entry) = s.entry.validate(entryId = id)
        _state.update { it.copy(product = productForm, entry = entryForm) }
        if ((newProduct && fields == null) || entry == null) return
        val updated = draft.withForms(product = if (newProduct) s.product else null, entry = s.entry)
        // Ein neu aufgenommenes Bild gehört jetzt dem Artikel (nicht mehr aus dem OFF-Cache).
        purchaseDrafts.update(if (newProduct) updated.copy(imageFromCache = imageFromCache) else updated)
        pendingImage = null
        _saved.tryEmit(Unit)
    }

    private suspend fun loadExisting(productId: String, entry: EntryForm) {
        val product = productRepository.getById(productId)
        _state.value = StockFormState(FormMode.EXISTING_PRODUCT, loading = false, existingProduct = product, entry = entry)
    }

    /** Lokal unbekanntes Produkt: OFF-Daten (Cache, sonst Abfrage) vorbelegen. */
    private suspend fun loadForBarcode(barcode: String, entry: EntryForm) {
        productRepository.getByBarcode(barcode)?.let { return loadExisting(it.id, entry) }
        _state.value = StockFormState(FormMode.NEW_PRODUCT, loading = true, entry = entry, product = ProductForm(barcode = barcode))

        var prefill = scanRepository.cachedPrefill(barcode)
        var error: LookupErrorKind? = null
        var notFound = false
        if (prefill == null) {
            val outcome = scanRepository.resolve(barcode)
            prefill = outcome.remote
            error = outcome.lookupError
            notFound = outcome.remoteNotFound
        }
        val unitId = prefill?.unitSymbol?.let { symbol ->
            catalogRepository.observeUnits().first().firstOrNull { it.symbol.equals(symbol, ignoreCase = true) }?.id
        }
        imageFromCache = prefill?.imagePath != null
        _state.value = StockFormState(
            mode = FormMode.NEW_PRODUCT,
            loading = false,
            entry = entry,
            product = ProductForm(
                name = prefill?.name.orEmpty(),
                brand = prefill?.brand.orEmpty(),
                category = prefill?.category.orEmpty(),
                packageSize = if (unitId != null) prefill?.packageSize?.toPlainString().orEmpty() else "",
                unitId = unitId,
                barcode = barcode,
                imagePath = prefill?.imagePath,
            ),
            prefilledFromOff = prefill != null,
            packageUnparsed = prefill != null && unitId == null,
            lookupError = error,
            lookupNotFound = notFound,
        )
    }

    // ---------------------------------------------------------------- Eingaben

    fun updateProduct(transform: (ProductForm) -> ProductForm) = _state.update { it.copy(product = transform(it.product)) }

    fun updateEntry(transform: (EntryForm) -> EntryForm) = _state.update { it.copy(entry = transform(it.entry)) }

    /** "Eingefroren" an/aus; beim Einschalten ist das Einfrierdatum heute. */
    fun setFrozen(on: Boolean) = updateEntry {
        val date = it.frozenAt.ifBlank { de.frelina.vorrat.domain.Validation.formatDate(java.time.LocalDate.now()) }
        it.copy(frozen = on, frozenAt = if (on) date else it.frozenAt, frozenAtError = null)
    }

    /** NFC-Chip wurde mit der URI dieses Eintrags beschrieben. */
    fun onNfcWritten(tagId: String) {
        updateEntry { it.copy(nfcTagId = tagId) }
        messages.show(UiMessage(R.string.nfc_write_done))
    }

    fun clearNfcTag() = updateEntry { it.copy(nfcTagId = null) }

    fun onImagePicked(uri: Uri) = viewModelScope.launch {
        try {
            val name = imageStore.importFromUri(context, uri)
            discardPendingImage()
            pendingImage = name
            imageFromCache = false
            updateProduct { it.copy(imagePath = name) }
            _state.update { it.copy(imageError = false) }
        } catch (_: Exception) {
            _state.update { it.copy(imageError = true) }
        }
    }

    fun removeImage() = viewModelScope.launch {
        discardPendingImage()
        imageFromCache = false
        updateProduct { it.copy(imagePath = null) }
    }

    private suspend fun discardPendingImage() {
        pendingImage?.let { imageStore.delete(it) }
        pendingImage = null
    }

    /** Ort direkt beim Erfassen anlegen; liefert ein Ergebnis für Fehlermeldungen im Dialog. */
    suspend fun createLocation(name: String): SaveResult {
        val result = catalogRepository.addLocation(name)
        if (result is SaveResult.Saved) updateEntry { it.copy(locationId = result.id) }
        return result
    }

    // ---------------------------------------------------------------- Speichern

    fun save() = viewModelScope.launch {
        val s = _state.value
        if (s.saving) return@launch
        _state.update { it.copy(saving = true) }
        try {
            if (draftId != null) {
                saveDraft(draftId, s)
                return@launch
            }
            when (s.mode) {
                FormMode.NEW_PRODUCT -> saveNewProduct(s)
                FormMode.EXISTING_PRODUCT -> saveExistingProductEntry(s)
                FormMode.EDIT_ENTRY -> saveEditEntry(s)
                FormMode.EDIT_PRODUCT -> saveEditProduct(s)
            }
        } finally {
            _state.update { it.copy(saving = false) }
        }
    }

    private suspend fun saveNewProduct(s: StockFormState) {
        val (productForm, fields) = s.product.validate()
        val (entryForm, entry) = s.entry.validate(entryId = nfcEntryId)
        _state.update { it.copy(product = productForm, entry = entryForm) }
        if (fields == null || entry == null) return
        // Eingefrorenes ohne Barcode: gleichnamiges Produkt wiederverwenden (keine Dubletten wie "Bolognese" ×5).
        if (s.frozenMode && fields.barcode == null) {
            productRepository.findWithoutBarcodeByName(fields.name)?.let { existing ->
                val forExisting = entry.copy(productId = existing.id)
                if (offerMerge(forExisting)) return
                stockRepository.addEntry(forExisting)
                pendingImage?.let { imageStore.delete(it) }
                pendingImage = null
                finish(purchasedProductId = existing.id)
                return
            }
        }
        if (fields.barcode != null && productRepository.getByBarcode(fields.barcode) != null) {
            _state.update { it.copy(product = it.product.copy(barcodeDuplicate = true)) }
            return
        }
        val image = if (imageFromCache) s.product.imagePath?.let { imageStore.copy(it) } else s.product.imagePath
        val source = if (s.prefilledFromOff) ProductSource.OPEN_FOOD_FACTS else ProductSource.MANUAL
        val product = fields.toNewEntity(image, source, System.currentTimeMillis())
        stockRepository.addProductWithEntry(product, entry)
        pendingImage = null
        finish(purchasedProductId = product.id)
    }

    private suspend fun saveExistingProductEntry(s: StockFormState) {
        val product = s.existingProduct ?: return
        val (entryForm, entry) = s.entry.validate(entryId = nfcEntryId, productId = product.id)
        _state.update { it.copy(entry = entryForm) }
        if (entry == null) return
        if (offerMerge(entry)) return
        stockRepository.addEntry(entry)
        finish(purchasedProductId = product.id)
    }

    private suspend fun saveEditEntry(s: StockFormState) {
        val id = entryId ?: return
        val original = stockRepository.getEntry(id) ?: return
        val (entryForm, entry) = s.entry.validate(entryId = id, productId = original.productId)
        _state.update { it.copy(entry = entryForm) }
        if (entry == null) return
        if (offerMerge(entry)) return
        stockRepository.updateEntry(entry)
        finish()
    }

    /** Gibt es schon einen Eintrag mit denselben Daten, wird nachgefragt (true = Speichern wartet auf Antwort). */
    private suspend fun offerMerge(entry: StockEntryEntity): Boolean {
        val twin = stockRepository.findTwin(entry) ?: return false
        _state.update { it.copy(mergeOffer = MergeOffer(entry, twin)) }
        return true
    }

    /** Antwort "Zusammenführen": Anzahl auf den bestehenden Eintrag addieren. */
    fun confirmMerge() = resolveMerge { offer ->
        if (!stockRepository.mergeInto(offer.target.id, offer.entry)) persist(offer.entry)
    }

    /** Antwort "Getrennt speichern": wie ohne Nachfrage speichern. */
    fun keepSeparate() = resolveMerge { offer -> persist(offer.entry) }

    /** Dialog abgebrochen: nichts speichern, Formular bleibt offen. */
    fun dismissMerge() = _state.update { it.copy(mergeOffer = null) }

    private fun resolveMerge(action: suspend (MergeOffer) -> Unit) = viewModelScope.launch {
        val offer = _state.value.mergeOffer ?: return@launch
        if (_state.value.saving) return@launch
        _state.update { it.copy(mergeOffer = null, saving = true) }
        try {
            action(offer)
            finish(purchasedProductId = offer.entry.productId.takeIf { _state.value.mode != FormMode.EDIT_ENTRY })
        } finally {
            _state.update { it.copy(saving = false) }
        }
    }

    private suspend fun persist(entry: StockEntryEntity) {
        if (_state.value.mode == FormMode.EDIT_ENTRY) stockRepository.updateEntry(entry) else stockRepository.addEntry(entry)
    }

    private suspend fun saveEditProduct(s: StockFormState) {
        val product = s.existingProduct ?: return
        val (productForm, fields) = s.product.validate()
        _state.update { it.copy(product = productForm) }
        if (fields == null) return
        if (fields.barcode != null) {
            val other = productRepository.getByBarcode(fields.barcode)
            if (other != null && other.id != product.id) {
                _state.update { it.copy(product = it.product.copy(barcodeDuplicate = true)) }
                return
            }
        }
        productRepository.update(fields.applyTo(product, s.product.imagePath))
        pendingImage = null
        finish()
    }

    /**
     * Abschluss. Wurde ein Produkt zum Bestand hinzugefügt ([purchasedProductId]), gilt es als gekauft:
     * passende Einträge der Einkaufsliste (und der Eintrag, von dem aus erfasst wurde) werden entfernt.
     */
    private suspend fun finish(purchasedProductId: String? = null) {
        val removed = buildList {
            shoppingItemId?.let { id -> shoppingRepository.remove(id)?.let(::add) }
            purchasedProductId?.let { addAll(shoppingRepository.removeForProduct(it)) }
        }
        messages.show(UiMessage(if (removed.isEmpty()) R.string.saved else R.string.shopping_removed_after_purchase))
        _saved.tryEmit(Unit)
    }

    private companion object {
        const val KEY_NEW_ENTRY_ID = "new_entry_id"
    }

    override fun onCleared() {
        // Nicht gespeicherte, neu aufgenommene Bilder verwerfen.
        pendingImage?.let { imageStore.file(it).delete() }
    }
}
