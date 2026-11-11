package de.frelina.vorrat.ui.recipes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import de.frelina.vorrat.ui.components.GlassCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.frelina.vorrat.R
import de.frelina.vorrat.domain.RecipeMatch
import java.time.LocalDate

/**
 * "Was koche ich heute?": Rezeptvorschläge passend zu den bald ablaufenden Produkten. Chips oben filtern auf
 * eine Zutatengruppe, darunter Suche und Filter; ein Umschalter zeigt alle Rezepte (auch ohne Treffer).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipesScreen(
    onBack: () -> Unit,
    onOpenRecipe: (String) -> Unit,
    onAddRecipe: () -> Unit,
    onOpenMealPlan: () -> Unit,
    viewModel: RecipesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recipes_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = onOpenMealPlan) {
                        Icon(Icons.Outlined.CalendarMonth, contentDescription = stringResource(R.string.mealplan_title))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddRecipe,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.recipe_add)) },
            )
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "expiring") {
                if (state.expiring.isEmpty()) {
                    Text(
                        stringResource(R.string.recipes_no_expiring),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.recipes_expiring_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            state.expiring.forEach { group ->
                                FilterChip(
                                    selected = state.selectedGroup == group.groupId,
                                    onClick = { viewModel.toggleGroup(group.groupId) },
                                    label = {
                                        val phrase = expiryPhrase(group.expiry, state.today)
                                        Text(group.productName + if (phrase != null) " · $phrase" else "", maxLines = 1)
                                    },
                                )
                            }
                        }
                    }
                }
            }
            item(key = "search") {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    singleLine = true,
                    label = { Text(stringResource(R.string.recipes_search)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setQuery("") }) {
                                Icon(Icons.Outlined.Clear, contentDescription = stringResource(R.string.action_clear))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item(key = "filters") {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        RecipeFilter.ALL to R.string.recipes_filter_all,
                        RecipeFilter.OWN to R.string.recipes_filter_own,
                        RecipeFilter.VEGETARIAN to R.string.recipes_filter_vegetarian,
                    ).forEach { (filter, label) ->
                        FilterChip(
                            selected = state.filter == filter,
                            onClick = { viewModel.setFilter(filter) },
                            label = { Text(stringResource(label)) },
                        )
                    }
                    FilterChip(
                        selected = state.showAll,
                        onClick = { viewModel.setShowAll(!state.showAll) },
                        label = { Text(stringResource(R.string.recipes_show_all)) },
                    )
                }
            }
            if (state.matches.isEmpty()) {
                item(key = "empty") {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(if (state.showAll || state.query.isNotBlank()) R.string.recipes_empty_all else R.string.recipes_empty),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
            items(state.matches, key = { it.recipe.id }) { match ->
                RecipeCard(match, state.today, onClick = { onOpenRecipe(match.recipe.id) }, modifier = Modifier.animateItem())
            }
        }
    }
}

@Composable
private fun RecipeCard(match: RecipeMatch, today: LocalDate, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val recipe = match.recipe
    GlassCard(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(recipe.title, style = MaterialTheme.typography.titleMedium)
            val meta = recipeMeta(recipe)
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Zutaten, die bald ablaufen, zuerst.
            val have = match.have.sortedWith(compareBy({ it.expiry == null }, { it.expiry }))
            have.take(MAX_HAVE_LINES).forEach { h ->
                val phrase = h.expiry?.let { expiryPhrase(it, today, onlySoon = true) }
                val rescue = phrase != null
                Text(
                    stringResource(R.string.recipe_have_line, h.ingredient.name) +
                        if (phrase != null) " ($phrase)" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (rescue) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                )
            }
            if (have.size > MAX_HAVE_LINES) {
                Text(
                    pluralStringResource(R.plurals.recipe_have_more, have.size - MAX_HAVE_LINES, have.size - MAX_HAVE_LINES),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (match.missing.isNotEmpty()) {
                Text(
                    stringResource(R.string.recipe_missing_line, match.missing.joinToString { it.name }),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private const val MAX_HAVE_LINES = 4

