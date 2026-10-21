package com.vm.vector.ui.viewmodel

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vm.core.models.MotionScoring
import com.vm.core.models.SupermarketMath
import com.vm.core.models.VectorItem
import com.vm.core.models.VectorList
import com.vm.vector.VectorApplication
import com.vm.vector.WearSupermarketUpdate
import com.vm.vector.data.BrainRepository
import com.vm.vector.data.PreferenceManager
import com.vm.vector.data.SaveFileResult
import com.vm.vector.data.SupermarketRepository
import com.vm.vector.data.sync.BrainStateSync
import com.vm.vector.data.sync.BrainSyncOutcome
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

enum class SupermarketFilter { ALL, TO_BUY, STOCKED }

data class SupermarketUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isSaving: Boolean = false,
    /** Autosaved edits the user hasn't confirmed with Save yet (they still sync). */
    val hasUnsavedChanges: Boolean = false,
    val list: VectorList? = null,
    val filter: SupermarketFilter = SupermarketFilter.ALL,
    val slidersLocked: Boolean = true,
    val snackbarMessage: String? = null,
    val pendingConsentIntent: Intent? = null,
    val configError: Boolean = false,
    /** Points a confirmed trip would earn; non-null while the "Did you go?" modal is open. */
    val tripPromptPoints: Int? = null,
)

class SupermarketViewModel(
    private val repository: SupermarketRepository,
    wearUpdates: Flow<WearSupermarketUpdate>? = null,
    private val brainRepository: BrainRepository? = null,
    private val preferences: PreferenceManager? = null,
    private val stateSync: BrainStateSync? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SupermarketUiState())
    val uiState: StateFlow<SupermarketUiState> = _uiState.asStateFlow()

    private var autosaveJob: Job? = null
    private var retryAfterConsent: (() -> Unit)? = null

    init {
        wearUpdates?.let { updates ->
            viewModelScope.launch {
                updates.collect { applyWearUpdate(it) }
            }
        }
        viewModelScope.launch {
            SupermarketRepository.changes.collect { reloadAfterSync() }
        }
    }

    /** The phone already saved the watch change to the file, so only the in-memory list is patched here. */
    private fun applyWearUpdate(update: WearSupermarketUpdate) {
        val list = _uiState.value.list ?: return
        val index = if (update.index in list.items.indices && list.items[update.index].title == update.title) {
            update.index
        } else {
            list.items.indexOfFirst { it.title == update.title }
        }
        if (index < 0) return
        _uiState.value = _uiState.value.copy(
            list = list.copy(
                items = list.items.mapIndexed { i, item ->
                    if (i == index) item.copy(isChecked = update.isChecked) else item
                }
            )
        )
        // The watch save uploads the whole phone copy, which may have synced pending edits too.
        viewModelScope.launch {
            if (autosaveJob?.isActive != true) {
                _uiState.value = _uiState.value.copy(hasUnsavedChanges = repository.hasUnsyncedChanges())
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val loaded = repository.loadLocal()
            val unsynced = repository.hasUnsyncedChanges()
            if (loaded != null) {
                _uiState.value = _uiState.value.copy(isLoading = false, list = loaded, hasUnsavedChanges = unsynced)
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    snackbarMessage = "No supermarket list on this phone — swipe down to load it from Drive"
                )
            }
        }
    }

    /** Writes pending edits to the phone, then runs a Brain sync so other devices' changes come in. */
    fun refresh() {
        viewModelScope.launch {
            if (autosaveJob?.isActive == true) {
                autosaveJob?.cancel()
                _uiState.value.list?.let { repository.saveLocal(it) }
            }
            autosaveJob = null
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            val outcome = stateSync?.sync()
            val message = when (outcome) {
                null, BrainSyncOutcome.UpToDate -> "Up to date"
                is BrainSyncOutcome.Pulled -> "Updated from your other devices"
                is BrainSyncOutcome.Pushed -> "Synced to Drive"
                is BrainSyncOutcome.NeedsDecision -> "Changed on two devices — open Brain to merge"
                BrainSyncOutcome.NotConfigured -> "Connect Google Drive in Settings to sync"
                is BrainSyncOutcome.Failed -> outcome.message
            }
            val consent = (outcome as? BrainSyncOutcome.Failed)?.consentIntent
            if (consent != null) retryAfterConsent = ::refresh
            _uiState.value = _uiState.value.copy(
                isRefreshing = false,
                list = repository.loadLocal() ?: _uiState.value.list,
                hasUnsavedChanges = repository.hasUnsyncedChanges(),
                pendingConsentIntent = consent,
                snackbarMessage = if (consent == null) message else null,
            )
        }
    }

    private suspend fun reloadAfterSync() {
        if (autosaveJob?.isActive == true) return
        val loaded = repository.loadLocal() ?: return
        _uiState.value = _uiState.value.copy(list = loaded, hasUnsavedChanges = repository.hasUnsyncedChanges())
    }

    fun clearPendingConsent() {
        _uiState.value = _uiState.value.copy(pendingConsentIntent = null)
    }

    /** Re-runs whichever Drive action (save or refresh) asked for consent. */
    fun onConsentGranted() {
        val retry = retryAfterConsent ?: return
        retryAfterConsent = null
        retry()
    }

    fun setFilter(filter: SupermarketFilter) {
        _uiState.value = _uiState.value.copy(filter = filter)
    }

    fun toggleSlidersLocked() {
        _uiState.value = _uiState.value.copy(slidersLocked = !_uiState.value.slidersLocked)
    }

    fun updateRemaining(itemId: String, remaining: Double) {
        if (_uiState.value.slidersLocked) return
        updateItem(itemId) { it.copy(remaining = remaining) }
    }

    /**
     * [orderedIds] may be a filtered subset: those items are rearranged among the slots they already
     * occupy in the full list, then every item gets a global `order`. The JSON array itself is left
     * untouched because the watch addresses items by array index.
     */
    fun reorderItems(orderedIds: List<String>) {
        val list = _uiState.value.list ?: return
        val full = SupermarketMath.sortedByOrder(list)
        val idSet = orderedIds.toSet()
        val reordered = orderedIds.iterator()
        val merged = full.map { item -> if (item.id in idSet && reordered.hasNext()) reordered.next() else item.id }
        val position = merged.withIndex().associate { it.value to it.index }
        setList(list.copy(items = list.items.map { it.copy(order = position[it.id]) }))
    }

    /** Blank [aisle] is stored as null; missing [remaining] defaults to a full [total]. */
    fun addItem(title: String, aisle: String?, total: Double?, remaining: Double?) {
        val list = _uiState.value.list ?: return
        val order = (list.items.mapNotNull { it.order }.maxOrNull() ?: -1) + 1
        val (qty, left) = normalizeStock(total, remaining)
        val item = VectorItem(
            id = "item_${UUID.randomUUID()}",
            title = title.trim(),
            quantity = qty,
            remaining = left,
            category = aisle?.trim()?.takeIf { it.isNotEmpty() },
            order = order,
        )
        setList(list.copy(items = list.items + item))
    }

    fun editItem(itemId: String, title: String, aisle: String?, total: Double?, remaining: Double?) {
        val (qty, left) = normalizeStock(total, remaining)
        updateItem(itemId) {
            it.copy(
                title = title.trim(),
                category = aisle?.trim()?.takeIf { a -> a.isNotEmpty() },
                quantity = qty,
                remaining = left,
            )
        }
    }

    private fun normalizeStock(total: Double?, remaining: Double?): Pair<Double, Double> {
        val qty = total?.takeIf { it > 0.0 } ?: 1.0
        val left = (remaining ?: qty).coerceIn(0.0, qty)
        return qty to left
    }

    fun deleteItem(itemId: String) {
        val list = _uiState.value.list ?: return
        setList(list.copy(items = list.items.filter { it.id != itemId }))
    }

    fun filteredItems(): List<VectorItem> {
        val list = _uiState.value.list ?: return emptyList()
        val sorted = SupermarketMath.sortedByOrder(list)
        return when (_uiState.value.filter) {
            SupermarketFilter.ALL -> sorted
            SupermarketFilter.TO_BUY -> sorted.filter { SupermarketMath.needsBuy(it) }
            SupermarketFilter.STOCKED -> sorted.filterNot { SupermarketMath.needsBuy(it) }
        }
    }

    fun filterCounts(): Map<SupermarketFilter, Int> {
        val list = _uiState.value.list ?: return emptyMap()
        val toBuy = list.items.count { SupermarketMath.needsBuy(it) }
        return mapOf(
            SupermarketFilter.ALL to list.items.size,
            SupermarketFilter.TO_BUY to toBuy,
            SupermarketFilter.STOCKED to list.items.size - toBuy,
        )
    }

    fun clearSnackbar() {
        _uiState.value = _uiState.value.copy(snackbarMessage = null)
    }

    private fun updateItem(itemId: String, transform: (VectorItem) -> VectorItem) {
        val list = _uiState.value.list ?: return
        setList(list.copy(items = list.items.map { if (it.id == itemId) transform(it) else it }))
    }

    private fun setList(list: VectorList) {
        _uiState.value = _uiState.value.copy(list = list, hasUnsavedChanges = true)
        scheduleAutosave()
    }

    /** Debounced write of the phone copy; [save] additionally moves the restock baseline. */
    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(800)
            _uiState.value.list?.let { repository.saveLocal(it) }
        }
    }

    /**
     * Confirms the current list and queues a sync; enabled in the UI only while there are unsaved changes.
     * A restock (some stock went up since the last save and nothing is at zero) during an open
     * cycle asks first whether this was a supermarket trip.
     */
    fun save() {
        autosaveJob?.cancel()
        autosaveJob = null
        viewModelScope.launch {
            val points = tripPointsIfRestocked()
            if (points != null) {
                _uiState.value = _uiState.value.copy(tripPromptPoints = points)
            } else {
                saveNow()
            }
        }
    }

    /** Answer to the trip modal; the list is saved either way. */
    fun confirmTrip(wentShopping: Boolean) {
        _uiState.value = _uiState.value.copy(tripPromptPoints = null)
        viewModelScope.launch {
            val brain = brainRepository
            val prefs = preferences
            val earned = if (wentShopping && brain != null && prefs != null) {
                brain.recordSupermarketTrip(prefs.motionSettings.first())
            } else {
                null
            }
            saveNow(prefix = earned?.let { "Trip logged · +$it pts · " }.orEmpty())
        }
    }

    private suspend fun tripPointsIfRestocked(): Int? {
        val brain = brainRepository ?: return null
        val prefs = preferences ?: return null
        val list = _uiState.value.list ?: return null
        if (list.items.isEmpty() || list.items.any { it.remaining == 0.0 }) return null
        val baseline = repository.loadBaseline()
        val restocked = list.items.any { item ->
            val before = baseline[SupermarketRepository.baselineKey(item.title)] ?: return@any false
            (item.remaining ?: item.quantity ?: 1.0) > before
        }
        if (!restocked) return null
        val cycle = brain.supermarketCycle(prefs.motionSettings.first())
        if (!cycle.open) return null
        return MotionScoring.supermarketPoints(cycle.dueDate, LocalDate.now())
    }

    private suspend fun saveNow(prefix: String = "") {
        val list = _uiState.value.list ?: return
        _uiState.value = _uiState.value.copy(isSaving = true)
        when (val result = repository.save(list)) {
            is SaveFileResult.Success -> {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    hasUnsavedChanges = _uiState.value.list != list,
                    snackbarMessage = "${prefix}Saved · syncing",
                )
            }
            is SaveFileResult.NeedsRemoteConsent -> {
                retryAfterConsent = { viewModelScope.launch { saveNow() } }
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    pendingConsentIntent = result.intent
                )
            }
            is SaveFileResult.SavedLocallyOnly -> {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    snackbarMessage = "${prefix}Saved on this phone only — Drive upload failed"
                )
            }
        }
    }
}

class SupermarketViewModelFactory(
    private val context: android.content.Context,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val app = context.applicationContext as? VectorApplication
        return SupermarketViewModel(
            SupermarketRepository.create(context),
            app?.wearSupermarketUpdated,
            app?.brainRepository,
            PreferenceManager(context.applicationContext),
            app?.let { BrainStateSync(it, it.database) },
        ) as T
    }
}
