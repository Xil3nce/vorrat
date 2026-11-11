package de.frelina.vorrat.ui.recipes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.frelina.vorrat.R
import de.frelina.vorrat.ui.shopping.ShoppingCard

/** Zutat, wie sie auf der Einkaufsliste landen würde: Name und Notiz (Menge · Rezept). */
data class MissingLine(val name: String, val note: String?)

/**
 * Auswahl "Fehlendes auf die Einkaufsliste": die fehlenden Zutaten als Karten wie auf der Einkaufsliste.
 * Nach links wischen nimmt eine Zutat heraus; "Zutaten übernehmen" bleibt unten immer sichtbar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissingToShoppingDialog(
    lines: List<MissingLine>,
    onDismiss: () -> Unit,
    onConfirm: (List<MissingLine>) -> Unit,
) {
    // Namen der herausgenommenen Zutaten; bleibt beim Drehen erhalten
    var removed by rememberSaveable { mutableStateOf(listOf<String>()) }
    val visible = lines.filter { it.name !in removed }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(R.string.recipe_missing_to_shopping), maxLines = 1) },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_cancel))
                            }
                        },
                        actions = {
                            if (removed.isNotEmpty()) {
                                TextButton(onClick = { removed = emptyList() }) {
                                    Text(stringResource(R.string.recipe_missing_restore))
                                }
                            }
                        },
                    )
                },
                bottomBar = {
                    Surface(tonalElevation = 3.dp) {
                        Button(
                            onClick = { onConfirm(visible) },
                            enabled = visible.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                        ) {
                            Icon(Icons.Outlined.AddShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.recipe_missing_apply), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                },
            ) { padding ->
                if (visible.isEmpty()) {
                    Box(Modifier.padding(padding).fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.recipe_missing_empty), textAlign = TextAlign.Center)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        item {
                            Text(
                                stringResource(R.string.recipe_missing_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        items(visible, key = { it.name }) { line ->
                            SwipeToRemove(onRemove = { removed = removed + line.name }, modifier = Modifier.animateItem()) {
                                ShoppingCard(
                                    imagePath = null,
                                    name = line.name,
                                    brand = null,
                                    detail = {
                                        line.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                    },
                                    onClick = {},
                                ) {
                                    Text(
                                        stringResource(R.string.shopping_quantity, 1),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(end = 12.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToRemove(onRemove: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(positionalThreshold = { total -> total * 0.4f })
    val label = stringResource(R.string.recipe_missing_remove)
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .semantics { customActions = listOf(CustomAccessibilityAction(label) { onRemove(); true }) },
        backgroundContent = {
            val colors = MaterialTheme.colorScheme
            val active = state.dismissDirection == SwipeToDismissBoxValue.EndToStart
            Row(
                Modifier.fillMaxSize().background(if (active) colors.errorContainer else Color.Transparent)
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                if (active) {
                    Text(label, color = colors.onErrorContainer, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.width(12.dp))
                    Icon(Icons.Outlined.Delete, contentDescription = null, tint = colors.onErrorContainer)
                }
            }
        },
        onDismiss = { direction -> if (direction == SwipeToDismissBoxValue.EndToStart) onRemove() },
    ) { content() }
}
