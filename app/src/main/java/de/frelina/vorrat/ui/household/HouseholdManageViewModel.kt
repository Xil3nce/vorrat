package de.frelina.vorrat.ui.household

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.frelina.vorrat.R
import de.frelina.vorrat.data.household.Household
import de.frelina.vorrat.data.household.HouseholdRepository
import de.frelina.vorrat.data.household.HouseholdResult
import de.frelina.vorrat.data.settings.SettingsRepository
import de.frelina.vorrat.domain.HouseholdMember
import de.frelina.vorrat.ui.components.MessageTone
import de.frelina.vorrat.ui.components.UiMessage
import de.frelina.vorrat.ui.components.UiMessages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HouseholdManageState(
    val household: Household? = null,
    /** null = wird noch geladen. */
    val members: List<HouseholdMember>? = null,
)

/** Haushalt verwalten: Einladungscode erneuern, Mitglieder entfernen, Haushalt verlassen. */
@HiltViewModel
class HouseholdManageViewModel @Inject constructor(
    private val repository: HouseholdRepository,
    settings: SettingsRepository,
    private val messages: UiMessages,
) : ViewModel() {

    val state: StateFlow<HouseholdManageState> = combine(
        repository.household,
        repository.members,
        settings.householdSettings,
    ) { household, members, own ->
        val myName = own.myName.trim()
        HouseholdManageState(
            household = household,
            // Für dieses Handy gilt der Name aus den Einstellungen, auch wenn er noch nicht im Verlauf steht.
            members = members?.map { if (it.isMe && myName.isNotEmpty()) it.copy(name = myName) else it },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HouseholdManageState())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun renewCode() = run(R.string.household_code_renewed) { repository.renewCode() }

    fun removeMember(uid: String) = run(R.string.household_member_removed) { repository.removeMember(uid) }

    /** Nach Erfolg zeigt die App von selbst wieder "Haushalt anlegen/beitreten". */
    fun leave() = run(success = null) { repository.leave() }

    private fun run(success: Int?, action: suspend () -> HouseholdResult) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                if (action() == HouseholdResult.Success) {
                    success?.let { messages.show(UiMessage(it)) }
                } else {
                    messages.show(UiMessage(R.string.household_action_failed, tone = MessageTone.ERROR))
                }
            } finally {
                _busy.value = false
            }
        }
    }
}
