package de.frelina.vorrat

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import de.frelina.vorrat.ui.components.UiMessages
import de.frelina.vorrat.ui.household.HouseholdViewModel
import de.frelina.vorrat.ui.household.OnboardingScreen
import de.frelina.vorrat.ui.household.RootState
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

    /** Tippen auf einen Hinweis aus dem Haushalt → Verlauf. */
    private val openHistory = Channel<Unit>(Channel.CONFLATED)

    /** Schnellkachel "Scannen" → Scanner. */
    private val openScan = Channel<Unit>(Channel.CONFLATED)

    /** NFC-Chip (vorrat://entry/…) gescannt, während die App nicht im Lese-Dialog war. */
    private val nfcTags = Channel<de.frelina.vorrat.nfc.NfcTagRead>(Channel.CONFLATED)

    /** Per "Teilen" empfangener Text (z. B. Chefkoch-Link) → Rezept-Import. */
    private val sharedRecipes = Channel<String>(Channel.CONFLATED)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Immer dunkles Design → helle Symbole in Status- und Navigationsleiste.
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            VorratTheme {
                val household: HouseholdViewModel = hiltViewModel()
                val root by household.root.collectAsStateWithLifecycle()
                when (root) {
                    RootState.LOADING -> Unit
                    RootState.NEEDS_HOUSEHOLD -> OnboardingScreen(household)
                    RootState.READY -> VorratRoot(
                        messages = messages,
                        openSoonFilter = openSoonFilter.receiveAsFlow(),
                        openHistory = openHistory.receiveAsFlow(),
                        openScan = openScan.receiveAsFlow(),
                        nfcTags = nfcTags.receiveAsFlow(),
                        sharedRecipes = sharedRecipes.receiveAsFlow(),
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.getStringExtra(EXTRA_OPEN_FILTER)) {
            FILTER_SOON -> {
                openSoonFilter.trySend(Unit)
                intent.removeExtra(EXTRA_OPEN_FILTER)
            }
            OPEN_HISTORY -> {
                openHistory.trySend(Unit)
                intent.removeExtra(EXTRA_OPEN_FILTER)
            }
            OPEN_SCAN -> {
                openScan.trySend(Unit)
                intent.removeExtra(EXTRA_OPEN_FILTER)
            }
        }
        de.frelina.vorrat.nfc.NfcTags.fromIntent(intent)?.let { nfcTags.trySend(it) }
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let { sharedRecipes.trySend(it.take(2000)) }
            intent.removeExtra(Intent.EXTRA_TEXT)
        }
    }

    companion object {
        const val EXTRA_OPEN_FILTER = "open_filter"
        const val FILTER_SOON = "soon"
        const val OPEN_HISTORY = "history"
        const val OPEN_SCAN = "scan"
        const val ACTION_OPEN_SCAN = "de.frelina.vorrat.action.OPEN_SCAN"
    }
}
