package de.frelina.vorrat.ui.mealplan

import androidx.compose.foundation.BorderStroke
import de.frelina.vorrat.ui.components.SectionLabel
import de.frelina.vorrat.ui.components.glassBorder
import de.frelina.vorrat.ui.theme.VorratTheme
import androidx.compose.ui.text.font.FontWeight
import de.frelina.vorrat.ui.components.GlassIconButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import de.frelina.vorrat.ui.components.GlassCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.frelina.vorrat.R
import de.frelina.vorrat.domain.PlanWeek
import de.frelina.vorrat.domain.Recipe
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY_FORMAT = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMANY)
private val SHORT_FORMAT = DateTimeFormatter.ofPattern("d.M.", Locale.GERMANY)
private val CHIP_FORMAT = DateTimeFormatter.ofPattern("EEE d.M.", Locale.GERMANY)
private const val DEFAULT_PERSONS = 2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealPlanScreen(
    onBack: () -> Unit,
    /** Rezept mit der geplanten Personenzahl öffnen. */
    onOpenRecipe: (String, Int) -> Unit,
    /** Zeitraum (von, bis; beide inklusive) für die Zutatenprüfung. */
    onOpenShopping: (LocalDate, LocalDate) -> Unit,
    viewModel: MealPlanViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var pickDay by rememberSaveable { mutableStateOf<String?>(null) }
    var moveEntry by remember { mutableStateOf<PlanEntryUi?>(null) }
    var rangeDialog by remember { mutableStateOf(false) }
    var staplesDialog by remember { mutableStateOf(false) }

    val hasEntries = state.days.any { it.entries.isNotEmpty() }
    val hasFromToday = PlanWeek.hasFromTodayChoice(state.weekStart, state.today)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mealplan_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    GlassIconButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back), onBack, Modifier.padding(start = 8.dp))
                },
                actions = {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.shopping_menu_more))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.mealplan_menu_staples)) },
                            onClick = { menu = false; staplesDialog = true },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (hasEntries) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (hasFromToday) {
                            rangeDialog = true
                        } else {
                            val (from, to) = PlanWeek.shoppingRange(state.weekStart, state.today, fromToday = false)
                            onOpenShopping(from, to)
                        }
                    },
                    icon = { Icon(Icons.Outlined.AddShoppingCart, contentDescription = null) },
                    text = { Text(stringResource(R.string.mealplan_shopping)) },
                )
            }
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                Modifier.padding(padding).fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "week") {
                    WeekNavigation(
                        weekStart = state.weekStart,
                        isThisWeek = state.weekStart == PlanWeek.monday(state.today),
                        onPrevious = viewModel::previousWeek,
                        onNext = viewModel::nextWeek,
                        onThisWeek = viewModel::thisWeek,
                    )
                }
                items(state.days, key = { it.date.toString() }) { day ->
                    DayCard(
                        day = day,
                        isToday = day.date == state.today,
                        onAdd = { pickDay = day.date.toString() },
                        onOpen = { entry -> entry.recipe?.let { onOpenRecipe(it.id, entry.entry.persons) } },
                        onMove = { moveEntry = it },
                        onRemove = viewModel::remove,
                    )
                }
            }
        }
    }

    pickDay?.let { dayText ->
        val day = LocalDate.parse(dayText)
        RecipePickerSheet(
            day = day,
            recipes = state.recipes,
            suggestions = state.suggestions,
            onDismiss = { pickDay = null },
            onPick = { recipe ->
                pickDay = null
                viewModel.add(day, recipe.id, recipe.servings ?: DEFAULT_PERSONS)
            },
        )
    }
    moveEntry?.let { entry ->
        MoveDialog(
            today = state.today,
            weekStart = state.weekStart,
            current = entry.entry.date,
            onDismiss = { moveEntry = null },
            onPick = { date ->
                moveEntry = null
                viewModel.move(entry.entry.id, date)
            },
        )
    }
    if (rangeDialog) {
        AlertDialog(
            onDismissRequest = { rangeDialog = false },
            title = { Text(stringResource(R.string.mealplan_range_title)) },
            text = {
                Column {
                    TextButton(onClick = {
                        rangeDialog = false
                        val (from, to) = PlanWeek.shoppingRange(state.weekStart, state.today, fromToday = false)
                        onOpenShopping(from, to)
                    }) { Text(stringResource(R.string.mealplan_range_week)) }
                    TextButton(onClick = {
                        rangeDialog = false
                        val (from, to) = PlanWeek.shoppingRange(state.weekStart, state.today, fromToday = true)
                        onOpenShopping(from, to)
                    }) { Text(stringResource(R.string.mealplan_range_from_today)) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { rangeDialog = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (staplesDialog) {
        AlertDialog(
            onDismissRequest = { staplesDialog = false },
            title = { Text(stringResource(R.string.mealplan_staples_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.mealplan_staples_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.staples.isEmpty()) {
                        Text(stringResource(R.string.mealplan_staples_empty))
                    } else {
                        LazyColumn {
                            items(state.staples, key = { it.key }) { staple ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(staple.label, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { viewModel.removeStaple(staple.key) }) {
                                        Icon(
                                            Icons.Outlined.Close,
                                            contentDescription = stringResource(R.string.mealplan_staples_remove, staple.label),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { staplesDialog = false }) { Text(stringResource(R.string.action_close)) } },
        )
    }
}

@Composable
private fun WeekNavigation(
    weekStart: LocalDate,
    isThisWeek: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onThisWeek: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = stringResource(R.string.mealplan_week_prev))
            }
            Text(
                stringResource(
                    R.string.mealplan_week_range,
                    weekStart.format(SHORT_FORMAT),
                    weekStart.plusDays(6).format(SHORT_FORMAT),
                ),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f, fill = false),
            )
            IconButton(onClick = onNext) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = stringResource(R.string.mealplan_week_next))
            }
        }
        if (!isThisWeek) TextButton(onClick = onThisWeek) { Text(stringResource(R.string.mealplan_this_week)) }
    }
}

@Composable
private fun DayCard(
    day: PlanDayUi,
    isToday: Boolean,
    onAdd: () -> Unit,
    onOpen: (PlanEntryUi) -> Unit,
    onMove: (PlanEntryUi) -> Unit,
    onRemove: (PlanEntryUi) -> Unit,
) {
    val label = day.date.format(DAY_FORMAT)
    GlassCard(
        border = if (isToday) BorderStroke(1.dp, VorratTheme.colors.accent.copy(alpha = 0.6f)) else glassBorder(),
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isToday) "$label · ${stringResource(R.string.mealplan_today)}" else label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isToday) VorratTheme.colors.accent else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                IconButton(onClick = onAdd) {
                    Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.mealplan_add_for_day, label))
                }
            }
            if (day.entries.isEmpty()) {
                Text(
                    stringResource(R.string.mealplan_day_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = VorratTheme.colors.textSecondary,
                    modifier = Modifier.padding(start = 16.dp, bottom = 12.dp),
                )
            }
            day.entries.forEachIndexed { index, entry ->
                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = VorratTheme.colors.glassBorder)
                EntryRow(entry, onOpen = { onOpen(entry) }, onMove = { onMove(entry) }, onRemove = { onRemove(entry) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryRow(entry: PlanEntryUi, onOpen: () -> Unit, onMove: () -> Unit, onRemove: () -> Unit) {
    val title = entry.recipe?.title ?: stringResource(R.string.mealplan_recipe_missing)
    var menu by remember { mutableStateOf(false) }
    val removeLabel = stringResource(R.string.mealplan_remove)
    val moveLabel = stringResource(R.string.mealplan_move)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) onRemove()
            false
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = null,
                    tint = VorratTheme.colors.danger,
                    modifier = Modifier.padding(end = 20.dp),
                )
            }
        },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = entry.recipe != null, onClick = onOpen)
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction(moveLabel) { onMove(); true },
                        CustomAccessibilityAction(removeLabel) { onRemove(); true },
                    )
                }
                .padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (entry.recipe == null) VorratTheme.colors.danger else MaterialTheme.colorScheme.onSurface,
                )
                if (entry.recipe != null) {
                    Text(
                        pluralStringResource(R.plurals.mealplan_for_persons, entry.entry.persons, entry.entry.persons),
                        style = MaterialTheme.typography.bodySmall,
                        color = VorratTheme.colors.textSecondary,
                    )
                }
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.mealplan_entry_options, title))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (entry.recipe != null) {
                        DropdownMenuItem(text = { Text(moveLabel) }, onClick = { menu = false; onMove() })
                    }
                    DropdownMenuItem(text = { Text(removeLabel) }, onClick = { menu = false; onRemove() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipePickerSheet(
    day: LocalDate,
    recipes: List<Recipe>,
    suggestions: List<Recipe>,
    onDismiss: () -> Unit,
    onPick: (Recipe) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = recipes.filter { it.title.contains(query.trim(), ignoreCase = true) }
    val showSuggestions = query.isBlank() && suggestions.isNotEmpty()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(
                stringResource(R.string.mealplan_pick_title, day.format(DAY_FORMAT)),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.mealplan_pick_search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            LazyColumn(Modifier.fillMaxWidth()) {
                if (showSuggestions) {
                    item(key = "h-suggestions") { PickerHeader(R.string.mealplan_pick_suggestions) }
                    items(suggestions, key = { "s-" + it.id }) { PickerRow(it, onPick) }
                    item(key = "h-all") { PickerHeader(R.string.mealplan_pick_all) }
                }
                if (filtered.isEmpty()) {
                    item(key = "empty") { Text(stringResource(R.string.mealplan_pick_empty), modifier = Modifier.padding(vertical = 16.dp)) }
                }
                items(filtered, key = { "a-" + it.id }) { PickerRow(it, onPick) }
            }
        }
    }
}

@Composable
private fun PickerHeader(text: Int) {
    SectionLabel(
        stringResource(text),
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp).semantics { heading() },
    )
}

@Composable
private fun PickerRow(recipe: Recipe, onPick: (Recipe) -> Unit) {
    Text(
        recipe.title,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth().clickable { onPick(recipe) }.padding(vertical = 14.dp),
    )
}

/** Eintrag auf einen anderen Tag der Auswahl (heute, die nächsten Tage und die angezeigte Woche) verschieben. */
@Composable
private fun MoveDialog(
    today: LocalDate,
    weekStart: LocalDate,
    current: LocalDate,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit,
) {
    val days = (PlanWeek.days(weekStart) + PlanWeek.offerDays(today)).distinct().sorted().filter { it != current }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mealplan_move_title)) },
        text = {
            LazyColumn {
                items(days, key = { it.toString() }) { day ->
                    Text(
                        if (day == today) {
                            "${day.format(CHIP_FORMAT)} · ${stringResource(R.string.mealplan_today)}"
                        } else {
                            day.format(CHIP_FORMAT)
                        },
                        modifier = Modifier.fillMaxWidth().clickable { onPick(day) }.padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
