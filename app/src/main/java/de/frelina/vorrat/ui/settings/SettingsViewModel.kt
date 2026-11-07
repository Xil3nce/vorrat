package de.frelina.vorrat.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.frelina.vorrat.R
import de.frelina.vorrat.data.backup.BackupException
import de.frelina.vorrat.data.backup.BackupManager
import de.frelina.vorrat.data.backup.ParsedBackup
import de.frelina.vorrat.data.household.Household
import de.frelina.vorrat.data.household.HouseholdRepository
import de.frelina.vorrat.data.settings.ReminderSettings
import de.frelina.vorrat.data.settings.SettingsRepository
import de.frelina.vorrat.reminder.ReminderScheduler
import de.frelina.vorrat.ui.components.UiMessage
import de.frelina.vorrat.ui.components.UiMessages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val scheduler: ReminderScheduler,
    private val backupManager: BackupManager,
    private val messages: UiMessages,
    householdRepository: HouseholdRepository,
) : ViewModel() {

    val household: StateFlow<Household?> =
        householdRepository.household.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Mitgliederzahl (null, solange unbekannt, z. B. offline beim ersten Laden). */
    val memberCount: StateFlow<Int?> =
        householdRepository.memberCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val reminder: StateFlow<ReminderSettings> =
        settings.reminderSettings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReminderSettings())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _pendingImport = MutableStateFlow<ParsedBackup?>(null)
    val pendingImport: StateFlow<ParsedBackup?> = _pendingImport.asStateFlow()

    // ------------------------------------------------ Erinnerungen

    fun setEnabled(enabled: Boolean) = viewModelScope.launch {
        settings.setEnabled(enabled)
        scheduler.reschedule()
    }

    fun setTime(hour: Int, minute: Int) = viewModelScope.launch {
        settings.setTime(hour, minute)
        scheduler.reschedule()
    }

    fun setLeadDays(days: Int) = viewModelScope.launch { settings.setLeadDays(days) }

    // ------------------------------------------------ Backup

    fun export(uri: Uri) = viewModelScope.launch {
        _busy.value = true
        try {
            val stream = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("kein Ausgabestrom")
            stream.use { backupManager.export(it) }
            messages.show(UiMessage(R.string.settings_backup_export_done))
        } catch (_: Exception) {
            messages.show(UiMessage(R.string.settings_backup_export_failed))
        } finally {
            _busy.value = false
        }
    }

    /** Liest und validiert; danach fragt der Screen, ob ersetzt werden soll. */
    fun startImport(uri: Uri) = viewModelScope.launch {
        _busy.value = true
        try {
            val stream = context.contentResolver.openInputStream(uri) ?: throw IOException("kein Eingabestrom")
            _pendingImport.value = stream.use { backupManager.read(it) }
        } catch (e: BackupException) {
            messages.show(errorMessage(e))
        } catch (_: Exception) {
            messages.show(UiMessage(R.string.settings_backup_error_unreadable))
        } finally {
            _busy.value = false
        }
    }

    fun confirmImport() = viewModelScope.launch {
        val parsed = _pendingImport.value ?: return@launch
        _pendingImport.value = null
        _busy.value = true
        try {
            backupManager.replaceAll(parsed)
            scheduler.reschedule()
            messages.show(UiMessage(R.string.settings_backup_import_done))
        } catch (_: Exception) {
            messages.show(UiMessage(R.string.settings_backup_import_failed))
        } finally {
            _busy.value = false
        }
    }

    fun cancelImport() {
        _pendingImport.value?.let(backupManager::discard)
        _pendingImport.value = null
    }

    private fun errorMessage(e: BackupException): UiMessage = when (e.reason) {
        BackupException.Reason.NOT_A_BACKUP -> UiMessage(R.string.settings_backup_error_not_backup)
        BackupException.Reason.UNREADABLE -> UiMessage(R.string.settings_backup_error_unreadable)
        BackupException.Reason.NEWER_SCHEMA -> UiMessage(R.string.settings_backup_error_newer)
        BackupException.Reason.UNSUPPORTED_FORMAT -> UiMessage(R.string.settings_backup_error_format)
        BackupException.Reason.INVALID_DATA -> UiMessage(R.string.settings_backup_error_invalid, listOf(e.detail.orEmpty()))
    }

    override fun onCleared() {
        _pendingImport.value?.let(backupManager::discard)
    }
}
