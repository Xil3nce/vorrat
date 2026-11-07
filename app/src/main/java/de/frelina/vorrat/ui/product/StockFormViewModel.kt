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
import de.frelina.vorrat.data.db.QuantityTypeEntity
import de.frelina.vorrat.data.db.UnitEntity
import de.frelina.vorrat.data.image.ImageStore
import de.frelina.vorrat.data.off.LookupErrorKind
import de.frelina.vorrat.data.repo.CatalogRepository
import de.frelina.vorrat.data.repo.ProductRepository
import de.frelina.vorrat.data.repo.SaveResult
import de.frelina.vorrat.data.repo.ScanRepository
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
)

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
    private val imageStore: ImageStore,
    private val messages: UiMessages,
) : ViewModel() {

    // Die Routen haben unterschiedliche Argumentnamen → Modus eindeutig bestimmbar.
    private val entryId: String? = savedStateHandle.get<String>("editEntryId")
    private val editProductId: String? = savedStateHandle.get<String>("editProductId")
    private val addProductId: String? = savedStateHandle.get<String>("productId")?.takeIf { it.isNotBlank() }
    private val barcodeArg: String? = savedStateHandle.get<String>("barcode")?.takeIf { it.isNotBlank() }

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
        val defaultEntry = EntryForm(
            quantityTypeId = types.firstOrNull()?.id,
            locationId = locs.singleOrNull()?.id,
        )
        when {
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
            addProductId != null -> loadExisting(addProductId, defaultEntry)
            barcodeArg != null -> loadForBarcode(barcodeArg, defaultEntry)
            else -> _state.value = StockFormState(FormMode.NEW_PRODUCT, loading = false, entry = defaultEntry)
        }
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
        if (result is SaveResult.Saved) updateEntry { it.copy(locationId = result.id, locationMissing = false) }
        return result
    }

    // ---------------------------------------------------------------- Speichern

    fun save() = viewModelScope.launch {
        val s = _state.value
        if (s.saving) return@launch
        _state.update { it.copy(saving = true) }
        try {
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
        val (entryForm, entry) = s.entry.validate()
        _state.update { it.copy(product = productForm, entry = entryForm) }
        if (fields == null || entry == null) return
        if (fields.barcode != null && productRepository.getByBarcode(fields.barcode) != null) {
            _state.update { it.copy(product = it.product.copy(barcodeDuplicate = true)) }
            return
        }
        val image = if (imageFromCache) s.product.imagePath?.let { imageStore.copy(it) } else s.product.imagePath
        val source = if (s.prefilledFromOff) ProductSource.OPEN_FOOD_FACTS else ProductSource.MANUAL
        stockRepository.addProductWithEntry(fields.toNewEntity(image, source, System.currentTimeMillis()), entry)
        pendingImage = null
        finish()
    }

    private suspend fun saveExistingProductEntry(s: StockFormState) {
        val product = s.existingProduct ?: return
        val (entryForm, entry) = s.entry.validate(productId = product.id)
        _state.update { it.copy(entry = entryForm) }
        if (entry == null) return
        stockRepository.addEntry(entry)
        finish()
    }

    private suspend fun saveEditEntry(s: StockFormState) {
        val id = entryId ?: return
        val original = stockRepository.getEntry(id) ?: return
        val (entryForm, entry) = s.entry.validate(entryId = id, productId = original.productId)
        _state.update { it.copy(entry = entryForm) }
        if (entry == null) return
        stockRepository.updateEntry(entry)
        finish()
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

    private fun finish() {
        messages.show(UiMessage(R.string.saved))
        _saved.tryEmit(Unit)
    }

    override fun onCleared() {
        // Nicht gespeicherte, neu aufgenommene Bilder verwerfen.
        pendingImage?.let { imageStore.file(it).delete() }
    }
}
