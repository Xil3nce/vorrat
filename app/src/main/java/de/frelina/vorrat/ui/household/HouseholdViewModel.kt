package de.frelina.vorrat.ui.household

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.frelina.vorrat.R
import de.frelina.vorrat.data.household.HouseholdRepository
import de.frelina.vorrat.data.household.HouseholdResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RootState { LOADING, NEEDS_HOUSEHOLD, READY }

data class OnboardingState(
    val busy: Boolean = false,
    @param:StringRes val error: Int? = null,
)

/** Entscheidet, ob das Onboarding (Haushalt anlegen/beitreten) oder die App gezeigt wird. */
@HiltViewModel
class HouseholdViewModel @Inject constructor(
    private val repository: HouseholdRepository,
) : ViewModel() {

    val root: StateFlow<RootState> = repository.household
        .map { if (it == null) RootState.NEEDS_HOUSEHOLD else RootState.READY }
        .stateIn(viewModelScope, SharingStarted.Eagerly, RootState.LOADING)

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    fun create() = run { repository.create() }

    fun join(code: String) = run { repository.join(code) }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun run(action: suspend () -> HouseholdResult) {
        if (_state.value.busy) return
        _state.value = OnboardingState(busy = true)
        viewModelScope.launch {
            val error = when (action()) {
                HouseholdResult.Success -> null
                HouseholdResult.InvalidCode -> R.string.onboarding_error_invalid
                HouseholdResult.UnknownCode -> R.string.onboarding_error_unknown
                HouseholdResult.Failed -> R.string.onboarding_error_failed
            }
            _state.value = OnboardingState(busy = false, error = error)
        }
    }
}
