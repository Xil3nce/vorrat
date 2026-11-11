package de.frelina.vorrat.ui.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import de.frelina.vorrat.ui.components.GlassCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.frelina.vorrat.R
import de.frelina.vorrat.ui.components.ErrorSupportingText
import de.frelina.vorrat.ui.components.IngredientGroupField
import de.frelina.vorrat.ui.components.NumberField

/** Editor für eigene Rezepte (neu, bearbeiten oder Kopie eines eingebauten Rezepts). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRecipeScreen(
    onDone: () -> Unit,
    viewModel: EditRecipeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onDone() }
    var importDialog by rememberSaveable { mutableStateOf(false) }
    if (importDialog) {
        ChefkochImportDialog(
            onDismiss = { importDialog = false },
            onImport = { importDialog = false; viewModel.importFrom(it) },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (state.editingExisting) R.string.recipe_edit_title else R.string.recipe_new_title))
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::save, enabled = !state.loading) {
                        Text(stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!state.editingExisting) {
                OutlinedButton(onClick = { importDialog = true }, enabled = !state.importing) {
                    Icon(Icons.Outlined.Download, contentDescription = null)
                    Text(stringResource(R.string.recipe_import_action), modifier = Modifier.padding(start = 8.dp))
                }
                if (state.importing) {
                    Text(stringResource(R.string.recipe_import_loading), style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::setTitle,
                label = { Text(stringResource(R.string.recipe_field_title)) },
                singleLine = true,
                isError = state.titleError != null,
                supportingText = state.titleError?.let { { ErrorSupportingText(it) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(
                    value = state.minutes,
                    onValueChange = viewModel::setMinutes,
                    label = stringResource(R.string.recipe_field_minutes),
                    error = state.minutesError,
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    value = state.servings,
                    onValueChange = viewModel::setServings,
                    label = stringResource(R.string.recipe_field_servings),
                    error = state.servingsError,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.recipe_field_vegetarian), modifier = Modifier.weight(1f))
                Switch(checked = state.vegetarian, onCheckedChange = viewModel::setVegetarian)
            }

            Text(
                stringResource(R.string.recipe_ingredients),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            state.rows.forEach { row ->
                androidx.compose.runtime.key(row.key) {
                    GlassCard(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                                OutlinedTextField(
                                    value = row.name,
                                    onValueChange = { viewModel.setName(row.key, it) },
                                    label = { Text(stringResource(R.string.recipe_field_ingredient)) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                    modifier = Modifier.weight(1.4f),
                                )
                                OutlinedTextField(
                                    value = row.amount,
                                    onValueChange = { viewModel.setAmount(row.key, it) },
                                    label = { Text(stringResource(R.string.recipe_field_amount)) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            IngredientGroupField(
                                label = stringResource(R.string.recipe_field_group),
                                selected = row.group,
                                nullOptionLabel = stringResource(R.string.recipe_group_none),
                                onSelect = { viewModel.setGroup(row.key, it) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = row.optional, onCheckedChange = { viewModel.setOptional(row.key, it) })
                                Text(stringResource(R.string.recipe_field_optional), modifier = Modifier.weight(1f))
                                IconButton(onClick = { viewModel.removeRow(row.key) }) {
                                    Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.recipe_remove_ingredient))
                                }
                            }
                        }
                    }
                }
            }
            OutlinedButton(onClick = viewModel::addRow) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text(stringResource(R.string.recipe_add_ingredient), modifier = Modifier.padding(start = 8.dp))
            }

            OutlinedTextField(
                value = state.steps,
                onValueChange = viewModel::setSteps,
                label = { Text(stringResource(R.string.recipe_field_steps)) },
                supportingText = { Text(stringResource(R.string.recipe_field_steps_hint)) },
                minLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Dialog zum Einfügen eines Chefkoch-Links. */
@Composable
private fun ChefkochImportDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    var link by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.recipe_import_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.recipe_import_text))
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it.take(2000) },
                    label = { Text(stringResource(R.string.recipe_import_link)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onImport(link) }, enabled = link.isNotBlank()) {
                Text(stringResource(R.string.recipe_import_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
