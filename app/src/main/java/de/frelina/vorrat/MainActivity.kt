package de.frelina.vorrat

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import de.frelina.vorrat.ui.components.UiMessages
import de.frelina.vorrat.ui.navigation.VorratRoot
import de.frelina.vorrat.ui.theme.VorratTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var messages: UiMessages

    /** Tippen auf die Erinnerung → Bestand mit Filter "läuft bald ab". */
    private val openSoonFilter = Channel<Unit>(Channel.CONFLATED)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            VorratTheme {
                VorratRoot(messages = messages, openSoonFilter = openSoonFilter.receiveAsFlow())
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getStringExtra(EXTRA_OPEN_FILTER) == FILTER_SOON) {
            openSoonFilter.trySend(Unit)
            intent.removeExtra(EXTRA_OPEN_FILTER)
        }
    }

    companion object {
        const val EXTRA_OPEN_FILTER = "open_filter"
        const val FILTER_SOON = "soon"
    }
}
