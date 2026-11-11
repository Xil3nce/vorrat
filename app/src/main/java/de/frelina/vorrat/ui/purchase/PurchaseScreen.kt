package de.frelina.vorrat.ui.purchase

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.frelina.vorrat.R
import de.frelina.vorrat.ui.components.GlassIconButton
import de.frelina.vorrat.ui.components.StockItemCard
import de.frelina.vorrat.ui.components.rememberListSwipeState
import de.frelina.vorrat.ui.theme.VorratTheme
import java.time.LocalDate

/** Einkauf: alle per "+ Bestand" gescannten Artikel prüfen, ergänzen und gesammelt in den Bestand übernehmen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseScreen(
    onBack: () -> Unit,
    onOpenDraft: (String) -> Unit,
    viewModel: PurchaseViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val leadDays by viewModel.leadDays.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.done.collect { onBack() } }
    val today = LocalDate.now()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.purchase_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    GlassIconButton(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        stringResource(R.string.action_back),
                        onBack,
                        Modifier.padding(start = 8.dp),
                    )
                },
            )
        },
        bottomBar = {
            if (state.rows.isNotEmpty()) {
                Column(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.incomplete > 0) {
                        Text(
                            pluralStringResource(R.plurals.purchase_incomplete, state.incomplete, state.incomplete),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Button(
                        onClick = viewModel::commit,
                        enabled = !busy && state.incomplete == 0,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        if (busy) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.purchase_commit_count, state.rows.size))
                        }
                    }
                }
            }
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.rows.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Outlined.ShoppingBag, contentDescription = null, tint = VorratTheme.colors.textTertiary, modifier = Modifier.size(48.dp))
                Text(stringResource(R.string.purchase_empty), textAlign = TextAlign.Center, color = VorratTheme.colors.textSecondary)
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        stringResource(R.string.purchase_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = VorratTheme.colors.textTertiary,
                    )
                }
                items(state.rows, key = { it.draft.id }) { row ->
                    val unknown = stringResource(R.string.scan_unknown_product, row.draft.barcode.orEmpty())
                    val item = if (row.item.productName.isBlank()) row.item.copy(productName = unknown) else row.item
                    SwipeToDelete(onDelete = { viewModel.remove(row.draft.id) }, modifier = Modifier.animateItem()) {
                        Column {
                            StockItemCard(item, today, leadDays, onClick = { onOpenDraft(row.draft.id) })
                            if (!row.complete) {
                                Row(
                                    Modifier.padding(start = 14.dp, top = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Outlined.ErrorOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(end = 6.dp).size(16.dp),
                                    )
                                    Text(
                                        stringResource(R.string.purchase_item_incomplete),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
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

/** Nach links wischen entfernt den Artikel (mit Rückgängig); auch als Bedienungshilfen-Aktion. */
@Composable
private fun SwipeToDelete(onDelete: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val swipeState = rememberListSwipeState()
    val label = stringResource(R.string.stock_swipe_delete)
    SwipeToDismissBox(
        state = swipeState,
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .semantics { customActions = listOf(CustomAccessibilityAction(label) { onDelete(); true }) },
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val active = swipeState.dismissDirection == SwipeToDismissBoxValue.EndToStart
            Row(
                Modifier
                    .fillMaxSize()
                    .background(if (active) MaterialTheme.colorScheme.errorContainer else Color.Transparent)
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (active) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        },
        onDismiss = { if (it == SwipeToDismissBoxValue.EndToStart) onDelete() },
    ) { content() }
}
