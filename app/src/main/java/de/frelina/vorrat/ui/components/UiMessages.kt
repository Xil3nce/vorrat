package de.frelina.vorrat.ui.components

import androidx.annotation.StringRes
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Art der Meldung: bestimmt Farbe und Symbol der Glas-Pille. */
enum class MessageTone { SUCCESS, INFO, ERROR }

/**
 * Snackbar-Nachricht; Texte als Ressourcen (keine Hardcodes). Mit [pluralCount] ist [text] eine Plurals-Ressource
 * (die Anzahl steht dann auch als erstes Argument zur Verfügung: `%1$d`).
 */
data class UiMessage(
    val text: Int,
    val args: List<Any> = emptyList(),
    @StringRes val actionLabel: Int? = null,
    val onAction: (suspend () -> Unit)? = null,
    val pluralCount: Int? = null,
    val tone: MessageTone = MessageTone.SUCCESS,
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
