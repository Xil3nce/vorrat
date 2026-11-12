package de.frelina.vorrat.ui.trash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.frelina.vorrat.R
import de.frelina.vorrat.data.trash.TrashItem
import de.frelina.vorrat.data.trash.TrashKind
import de.frelina.vorrat.domain.TrashLogic
import de.frelina.vorrat.ui.components.GlassCard
import de.frelina.vorrat.ui.components.GlassIconButton
import de.frelina.vorrat.ui.components.SectionLabel
import de.frelina.vorrat.ui.components.initialsOf
import de.frelina.vorrat.ui.components.pastelFor
import de.frelina.vorrat.ui.theme.VorratTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Papierkorb: Gelöschtes 30 Tage lang wiederherstellen oder endgültig löschen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(onBack: () -> Unit, viewModel: TrashViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<TrashItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trash_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    GlassIconButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back), onBack, Modifier.padding(start = 8.dp))
                },
                actions = {
                    if (state.items.isNotEmpty()) {
                        GlassIconButton(
                            Icons.Outlined.DeleteSweep,
                            stringResource(R.string.trash_clear),
                            { confirmClear = true },
                            Modifier.padding(end = 8.dp),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (!state.loading && state.items.isEmpty()) {
            EmptyTrash(Modifier.padding(padding))
            return@Scaffold
        }
        val entries = state.items.filter { it.kind == TrashKind.ENTRY }
        val recipes = state.items.filter { it.kind == TrashKind.RECIPE }
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.trash_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = VorratTheme.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            if (entries.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.trash_section_entries), Modifier.padding(start = 4.dp, top = 8.dp)) }
                items(entries, key = { it.key }) { item ->
                    TrashRow(item, state.now, onRestore = { viewModel.restore(item) }, onDelete = { confirmDelete = item })
                }
            }
            if (recipes.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.trash_section_recipes), Modifier.padding(start = 4.dp, top = 8.dp)) }
                items(recipes, key = { it.key }) { item ->
                    TrashRow(item, state.now, onRestore = { viewModel.restore(item) }, onDelete = { confirmDelete = item })
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.trash_clear_title)) },
            text = { Text(pluralStringResource(R.plurals.trash_clear_text, state.items.size, state.items.size)) },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; viewModel.clear() }) {
                    Text(stringResource(R.string.trash_clear), color = VorratTheme.colors.danger)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    confirmDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.trash_delete_title)) },
            text = { Text(stringResource(R.string.trash_delete_text, item.title)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = null; viewModel.deleteForever(item) }) {
                    Text(stringResource(R.string.trash_delete_forever), color = VorratTheme.colors.danger)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

private val DATE = DateTimeFormatter.ofPattern("dd.MM.")

@Composable
private fun TrashRow(item: TrashItem, now: Long, onRestore: () -> Unit, onDelete: () -> Unit) {
    val colors = VorratTheme.colors
    val daysLeft = TrashLogic.daysLeft(item.deletedAt, now)
    val deletedOn = Instant.ofEpochMilli(item.deletedAt).atZone(ZoneId.systemDefault()).toLocalDate().format(DATE)
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val title = item.title.ifBlank { stringResource(R.string.trash_untitled) }
                if (item.kind == TrashKind.RECIPE) {
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(colors.glassStrong),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Outlined.Restaurant, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp)) }
                } else {
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(pastelFor(title)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            initialsOf(title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.onAccent,
                        )
                    }
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    item.detail?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.trash_deleted_on, deletedOn) + " · " +
                            pluralStringResource(R.plurals.trash_days_left, daysLeft, daysLeft),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (daysLeft <= 3) colors.warning else colors.textTertiary,
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.DeleteForever, contentDescription = stringResource(R.string.trash_delete_forever), tint = colors.danger)
                }
                FilledTonalButton(
                    onClick = onRestore,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = colors.accent.copy(alpha = 0.16f),
                        contentColor = colors.accent,
                    ),
                ) {
                    Icon(Icons.Outlined.RestoreFromTrash, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.trash_restore), Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun EmptyTrash(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(VorratTheme.colors.glassStrong),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = VorratTheme.colors.textTertiary, modifier = Modifier.size(32.dp))
        }
        Text(
            stringResource(R.string.trash_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            stringResource(R.string.trash_empty_text),
            style = MaterialTheme.typography.bodyMedium,
            color = VorratTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
