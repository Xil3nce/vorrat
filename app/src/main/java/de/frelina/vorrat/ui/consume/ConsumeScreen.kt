package de.frelina.vorrat.ui.consume

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.frelina.vorrat.R
import de.frelina.vorrat.domain.ConsumeListLogic
import de.frelina.vorrat.ui.components.GlassCard
import de.frelina.vorrat.ui.components.GlassIconButton
import de.frelina.vorrat.ui.components.OpenedBadge
import de.frelina.vorrat.ui.components.ProductImage
import de.frelina.vorrat.ui.purchase.SwipeToDelete
import de.frelina.vorrat.ui.scan.ConsumeDialog
import de.frelina.vorrat.ui.theme.VorratTheme

/** Entnehmen: alle im Modus "Entnehmen" gescannten Artikel prüfen und gesammelt ausbuchen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsumeScreen(
    onBack: () -> Unit,
    viewModel: ConsumeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val leadDays by viewModel.leadDays.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<ConsumeRow?>(null) }
    LaunchedEffect(Unit) { viewModel.done.collect { onBack() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.consume_title), fontWeight = FontWeight.SemiBold) },
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
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                    Button(
                        onClick = viewModel::commit,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        if (busy) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            Text(pluralStringResource(R.plurals.consume_list_commit, state.rows.size, state.rows.size))
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
                Icon(Icons.Outlined.RemoveCircleOutline, contentDescription = null, tint = VorratTheme.colors.textTertiary, modifier = Modifier.size(48.dp))
                Text(stringResource(R.string.consume_list_empty), textAlign = TextAlign.Center, color = VorratTheme.colors.textSecondary)
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        stringResource(R.string.consume_list_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = VorratTheme.colors.textTertiary,
                    )
                }
                items(state.rows, key = { it.draft.entryId }) { row ->
                    SwipeToDelete(onDelete = { viewModel.remove(row.draft.entryId) }, modifier = Modifier.animateItem()) {
                        ConsumeRowCard(
                            row = row,
                            onClick = { if (row.item != null) editing = row },
                            onAdjust = { viewModel.adjust(row, it) },
                        )
                    }
                }
            }
        }
    }

    editing?.let { row ->
        // Zeile (oder ihr Eintrag) kann inzwischen verschwunden sein.
        val current = state.rows.firstOrNull { it.draft.entryId == row.draft.entryId }
        if (current == null || current.options.isEmpty()) {
            editing = null
        } else {
            ConsumeDialog(
                entries = current.options,
                leadDays = leadDays,
                onDismiss = { editing = null },
                onConsume = { entryId, amount -> editing = null; viewModel.change(current, entryId, amount) },
                initialEntryId = current.draft.entryId,
                initialAmount = current.draft.quantity,
                confirmLabel = R.string.consume_apply,
            )
        }
    }
}

/** Produktbild, Name, "Ort · MHD tt.mm." und rechts der −/+ Mengenregler. */
@Composable
private fun ConsumeRowCard(row: ConsumeRow, onClick: () -> Unit, onAdjust: (Int) -> Unit) {
    val item = row.item
    val colors = VorratTheme.colors
    GlassCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (item != null) {
                ProductImage(item.imagePath, size = 52.dp, name = item.productName)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        item.productName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val place = item.locationName ?: stringResource(R.string.location_none)
                    val expiry = item.effectiveExpiry
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (expiry == null) place else stringResource(R.string.consume_list_sub, place, ConsumeListLogic.shortDate(expiry)),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        item.openedAt?.let { opened -> OpenedBadge(opened, Modifier.padding(start = 8.dp)) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(
                        onClick = { onAdjust(-1) },
                        enabled = row.draft.quantity > 1,
                        modifier = Modifier.size(36.dp),
                    ) { Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.action_decrease)) }
                    Text(
                        row.draft.quantity.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(min = 32.dp),
                    )
                    FilledTonalIconButton(
                        onClick = { onAdjust(1) },
                        enabled = row.draft.quantity < item.quantity,
                        modifier = Modifier.size(36.dp),
                    ) { Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.action_increase)) }
                }
            } else {
                Text(
                    stringResource(R.string.consume_list_missing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.warning,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
