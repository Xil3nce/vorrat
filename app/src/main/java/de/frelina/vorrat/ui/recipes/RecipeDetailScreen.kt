package de.frelina.vorrat.ui.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.frelina.vorrat.R
import de.frelina.vorrat.domain.HaveIngredient
import de.frelina.vorrat.domain.RecipeIngredient
import de.frelina.vorrat.domain.RecipeMatch
import de.frelina.vorrat.domain.Validation
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    /** Eingebautes Rezept als eigenes kopieren: öffnet den Editor vorbelegt. */
    onCopy: (String) -> Unit,
    viewModel: RecipeDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val match = state.match
    var cookDialog by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(match?.recipe?.title ?: stringResource(R.string.recipes_title), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (match != null) {
                        if (match.recipe.builtin) {
                            IconButton(onClick = { onCopy(match.recipe.id) }) {
                                Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.recipe_copy))
                            }
                        } else {
                            IconButton(onClick = { onEdit(match.recipe.id) }) {
                                Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.action_edit))
                            }
                            IconButton(onClick = { deleteDialog = true }) {
                                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_delete))
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            match == null -> Box(Modifier.padding(padding).fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.recipe_not_found), textAlign = TextAlign.Center)
            }
            else -> Column(
                Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(match.recipe.title, style = MaterialTheme.typography.headlineSmall)
                val meta = recipeMeta(match.recipe)
                if (meta.isNotEmpty()) {
                    Text(meta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SectionTitle(R.string.recipe_ingredients)
                IngredientList(match, state.today)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = viewModel::addMissingToShopping,
                        enabled = match.missing.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.AddShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.recipe_missing_to_shopping), modifier = Modifier.padding(start = 8.dp))
                    }
                    Button(
                        onClick = { cookDialog = true },
                        enabled = match.have.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.Restaurant, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.recipe_cooked), modifier = Modifier.padding(start = 8.dp))
                    }
                }
                if (match.recipe.steps.isNotEmpty()) {
                    HorizontalDivider()
                    SectionTitle(R.string.recipe_steps)
                    match.recipe.steps.forEachIndexed { i, step ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${i + 1}.", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            Text(step, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }

    if (cookDialog && match != null) {
        CookedDialog(
            have = match.have.distinctBy { it.entryId },
            onDismiss = { cookDialog = false },
            onConfirm = { amounts ->
                cookDialog = false
                viewModel.cooked(amounts)
            },
        )
    }
    if (deleteDialog) {
        AlertDialog(
            onDismissRequest = { deleteDialog = false },
            title = { Text(stringResource(R.string.recipe_delete_title)) },
            text = { Text(stringResource(R.string.recipe_delete_text, match?.recipe?.title.orEmpty())) },
            confirmButton = {
                TextButton(onClick = {
                    deleteDialog = false
                    viewModel.delete(onBack)
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun IngredientList(match: RecipeMatch, today: LocalDate) {
    val haveBy = match.have.associateBy { it.ingredient }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        match.recipe.ingredients.forEach { ingredient ->
            val have = haveBy[ingredient]
            val missing = ingredient in match.missing
            IngredientRow(ingredient, have, missing, today)
        }
    }
}

@Composable
private fun IngredientRow(ingredient: RecipeIngredient, have: HaveIngredient?, missing: Boolean, today: LocalDate) {
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        when {
            have != null -> Icon(
                Icons.Outlined.Check,
                contentDescription = stringResource(R.string.recipe_status_have),
                tint = colors.primary,
                modifier = Modifier.size(20.dp),
            )
            missing -> Icon(
                Icons.Outlined.Close,
                contentDescription = stringResource(R.string.recipe_status_missing),
                tint = colors.error,
                modifier = Modifier.size(20.dp),
            )
            else -> Box(Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            val optional = if (ingredient.optional) " (" + stringResource(R.string.optional) + ")" else ""
            val amount = ingredient.amount?.takeIf(String::isNotBlank)?.let { "$it " }.orEmpty()
            Text(amount + ingredient.name + optional, style = MaterialTheme.typography.bodyLarge)
            if (have != null) {
                val date = have.expiry?.let { Validation.formatDate(it) }
                val phrase = expiryPhrase(have.expiry, today, onlySoon = true)
                val detail = listOfNotNull(
                    have.productName,
                    date?.let { stringResource(R.string.recipe_expiry_date, it) },
                    phrase,
                ).joinToString(" · ")
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (phrase != null) colors.tertiary else colors.onSurfaceVariant,
                )
            } else if (missing) {
                Text(
                    stringResource(R.string.recipe_status_missing),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.error,
                )
            }
        }
    }
}

/** "Gekocht": vorhandene Produkte ankreuzen und Anzahl wählen (Standard 1); Entnahme vom frühesten Eintrag. */
@Composable
private fun CookedDialog(
    have: List<HaveIngredient>,
    onDismiss: () -> Unit,
    onConfirm: (Map<String, Int>) -> Unit,
) {
    // entryId → Anzahl; enthalten = angekreuzt
    val selected = remember { mutableStateMapOf<String, Int>().apply { have.forEach { put(it.entryId, 1) } } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.recipe_cooked_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.recipe_cooked_text), style = MaterialTheme.typography.bodyMedium)
                have.forEach { h ->
                    val count = selected[h.entryId]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = count != null,
                            onCheckedChange = { on -> if (on) selected[h.entryId] = 1 else selected.remove(h.entryId) },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(h.productName, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(R.string.recipe_cooked_stock, h.quantity),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (count != null) {
                            IconButton(
                                onClick = { selected[h.entryId] = count - 1 },
                                enabled = count > 1,
                            ) { Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.action_decrease)) }
                            Text("$count", style = MaterialTheme.typography.titleMedium)
                            IconButton(
                                onClick = { selected[h.entryId] = count + 1 },
                                enabled = count < h.quantity,
                            ) { Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.action_increase)) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected.toMap()) }, enabled = selected.isNotEmpty()) {
                Text(stringResource(R.string.recipe_cooked_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
