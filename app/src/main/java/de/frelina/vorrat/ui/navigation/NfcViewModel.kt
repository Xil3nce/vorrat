package de.frelina.vorrat.ui.navigation

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import de.frelina.vorrat.data.repo.StockRepository
import de.frelina.vorrat.domain.NfcLink
import de.frelina.vorrat.nfc.NfcTagRead
import javax.inject.Inject

/** Wohin ein gelesener Chip führt. */
sealed interface NfcTarget {
    data class Entry(val entryId: String) : NfcTarget

    /** Chip gehört zu keinem Eintrag (neu oder Portion schon aufgebraucht) → neu einfrieren anbieten. */
    data class Unknown(val tagId: String?) : NfcTarget
}

@HiltViewModel
class NfcViewModel @Inject constructor(
    private val stockRepository: StockRepository,
) : ViewModel() {

    suspend fun resolve(read: NfcTagRead): NfcTarget {
        val byTag = read.tagId.takeIf { it.isNotBlank() }
            ?.let { stockRepository.entriesForNfcTag(it).map { e -> e.id } }
            .orEmpty()
        val uriExists = read.entryId?.let { id -> stockRepository.getEntry(id) != null } == true
        val id = NfcLink.resolve(byTag, read.entryId, if (uriExists) setOfNotNull(read.entryId) else emptySet())
        return if (id != null) NfcTarget.Entry(id) else NfcTarget.Unknown(read.tagId.takeIf { it.isNotBlank() })
    }
}
