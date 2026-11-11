package de.frelina.vorrat.ui.recipes

import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import de.frelina.vorrat.ui.components.GlassIconButton
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
import de.frelina.vorrat.ui.components.ProgressRing
import de.frelina.vorrat.ui.components.SectionLabel
import de.frelina.vorrat.ui.theme.VorratTheme
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
 * eine Zutatengruppe, darunter Suche und Filter: "Alle" (Standard) sowie unabhängig kombinierbar Eigene,
 * Vegetarisch und "Kochbar mit Vorrat".
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
                title = { Text(stringResource(R.string.recipes_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    GlassIconButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back), onBack, Modifier.padding(start = 8.dp))
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
                        SectionLabel(stringResource(R.string.recipes_expiring_title))
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
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = VorratTheme.colors.glass,
                        focusedContainerColor = VorratTheme.colors.glassStrong,
                        unfocusedBorderColor = VorratTheme.colors.glassBorder,
                    ),
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
                    FilterChip(
                        selected = !state.onlyOwn && !state.onlyVegetarian && !state.onlyPantry,
                        onClick = viewModel::clearFilters,
                        label = { Text(stringResource(R.string.recipes_filter_all)) },
                    )
                    FilterChip(
                        selected = state.onlyOwn,
                        onClick = viewModel::toggleOwn,
                        label = { Text(stringResource(R.string.recipes_filter_own)) },
                    )
                    FilterChip(
                        selected = state.onlyVegetarian,
                        onClick = viewModel::toggleVegetarian,
                        label = { Text(stringResource(R.string.recipes_filter_vegetarian)) },
                    )
                    FilterChip(
                        selected = state.onlyPantry,
                        onClick = viewModel::togglePantry,
                        label = { Text(stringResource(R.string.recipes_filter_pantry)) },
                    )
                }
            }
            if (state.matches.isEmpty()) {
                item(key = "empty") {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(if (state.onlyPantry && state.query.isBlank()) R.string.recipes_empty else R.string.recipes_empty_all),
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
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(recipe.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            val meta = recipeMeta(recipe)
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.bodySmall, color = VorratTheme.colors.textSecondary)
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
                    color = if (rescue) VorratTheme.colors.warning else MaterialTheme.colorScheme.onSurface,
                )
            }
            if (have.size > MAX_HAVE_LINES) {
                Text(
                    pluralStringResource(R.plurals.recipe_have_more, have.size - MAX_HAVE_LINES, have.size - MAX_HAVE_LINES),
                    style = MaterialTheme.typography.bodySmall,
                    color = VorratTheme.colors.textSecondary,
                )
            }
            if (match.missing.isNotEmpty()) {
                Text(
                    stringResource(R.string.recipe_missing_line, match.missing.joinToString { it.name }),
                    style = MaterialTheme.typography.bodyMedium,
                    color = VorratTheme.colors.warning,
                )
            }
        }
        val total = match.have.size + match.missing.size
        if (total > 0) {
            ProgressRing(
                progress = match.have.size.toFloat() / total,
                size = 44.dp,
                color = if (match.missing.isEmpty()) VorratTheme.colors.accent else VorratTheme.colors.warning,
            ) {
                Text("${match.have.size}/$total", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            }
        }
        }
    }
}

private const val MAX_HAVE_LINES = 4

