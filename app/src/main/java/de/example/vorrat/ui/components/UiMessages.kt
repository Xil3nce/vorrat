package de.example.vorrat.ui.components

import androidx.annotation.StringRes
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Snackbar-Nachricht; Texte als String-Ressourcen (keine Hardcodes). */
data class UiMessage(
    @StringRes val text: Int,
    val args: List<Any> = emptyList(),
    @StringRes val actionLabel: Int? = null,
    val onAction: (suspend () -> Unit)? = null,
)

/**
 * App-weite Snackbar-Nachrichten (z. B. "Rückgängig" nach dem Entfernen eines Eintrags,
 * auch wenn der Detail-Screen bereits geschlossen ist).
 */
@Singleton
class UiMessages @Inject constructor() {
    private val channel = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = channel.receiveAsFlow()

    fun show(message: UiMessage) {
        channel.trySend(message)
    }
}
