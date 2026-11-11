package de.frelina.vorrat.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.frelina.vorrat.ui.components.rememberListSwipeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Wischen zum Löschen und "Rückgängig": Der Eintrag kommt mit derselben ID (= Listen-Schlüssel) zurück und muss
 * wieder sichtbar sein, nicht im weggewischten Zustand hängen bleiben.
 */
@RunWith(AndroidJUnit4::class)
class SwipeUndoTest {

    @get:Rule
    val rule = createComposeRule()

    private val states = mutableMapOf<String, SwipeToDismissBoxState>()

    @Composable
    private fun SwipeList(items: List<String>, onDelete: (String) -> Unit, state: @Composable () -> SwipeToDismissBoxState) {
        LazyColumn {
            items(items, key = { it }) { id ->
                val swipe = state()
                states[id] = swipe
                SwipeToDismissBox(
                    state = swipe,
                    backgroundContent = {},
                    onDismiss = { if (it == SwipeToDismissBoxValue.EndToStart) onDelete(id) },
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                ) { Text(id) }
            }
        }
    }

    private fun deleteAndUndo(state: @Composable () -> SwipeToDismissBoxState) {
        val items = mutableStateListOf("Milch", "Reis")
        lateinit var scope: CoroutineScope
        rule.setContent {
            scope = rememberCoroutineScope()
            SwipeList(items, onDelete = { items.remove(it) }, state = state)
        }

        // Wie nach dem Wischen: Zustand "weggewischt", dann entfernt die App den Eintrag.
        val swiped = states.getValue("Milch")
        rule.runOnIdle { scope.launch { swiped.snapTo(SwipeToDismissBoxValue.EndToStart) } }
        rule.waitForIdle()
        assertEquals(SwipeToDismissBoxValue.EndToStart, swiped.currentValue)
        rule.runOnIdle { items.remove("Milch") }
        rule.waitForIdle()
        assertEquals(listOf("Reis"), items.toList())

        rule.runOnIdle { items.add(0, "Milch") } // Rückgängig: gleiche ID
        rule.waitForIdle()
    }

    @Test
    fun restoredItemIsVisibleAgain() {
        deleteAndUndo { rememberListSwipeState() }
        assertEquals(SwipeToDismissBoxValue.Settled, states.getValue("Milch").currentValue)
        rule.onNodeWithText("Milch").assertIsDisplayed()
    }

    /** Belegt die Ursache: Mit dem reinen Material-Zustand bleibt der zurückgeholte Eintrag weggewischt. */
    @Test
    fun plainMaterialStateStaysDismissed() {
        deleteAndUndo { rememberSwipeToDismissBoxState(positionalThreshold = { it * 0.4f }) }
        assertEquals(SwipeToDismissBoxValue.EndToStart, states.getValue("Milch").currentValue)
    }
}
