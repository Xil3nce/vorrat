package de.frelina.vorrat.ui.mealplan

import androidx.compose.foundation.BorderStroke
import de.frelina.vorrat.ui.components.SectionLabel
import de.frelina.vorrat.ui.components.glassBorder
import de.frelina.vorrat.ui.theme.VorratTheme
import androidx.compose.ui.text.font.FontWeight
import de.frelina.vorrat.ui.components.GlassIconButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import java.time.format.TextStyle
import kotlinx.coroutines.launch
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import de.frelina.vorrat.ui.components.GlassCard
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    var selected by rememberSaveable { mutableStateOf<String?>(null) }

    val hasEntries = state.days.any { it.entries.isNotEmpty() }
    val hasFromToday = PlanWeek.hasFromTodayChoice(state.weekStart, state.today)

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Hervorgehobener Tag: gewählter Tag dieser Woche, sonst heute (falls in der Woche), sonst Montag.
    val weekDays = PlanWeek.days(state.weekStart)
    val selectedDay = selected?.let(LocalDate::parse)?.takeIf { it in weekDays }
        ?: state.today.takeIf { it in weekDays }
        ?: state.weekStart

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.mealplan_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    GlassIconButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back), onBack, Modifier.padding(start = 8.dp))
                },
                actions = {
                    Box(Modifier.padding(end = 8.dp)) {
                        GlassIconButton(Icons.Outlined.MoreVert, stringResource(R.string.shopping_menu_more), { menu = true })
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.mealplan_menu_staples)) },
                                onClick = { menu = false; staplesDialog = true },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (hasEntries) {
                Button(
                    onClick = {
                        if (hasFromToday) {
                            rangeDialog = true
                        } else {
                            val (from, to) = PlanWeek.shoppingRange(state.weekStart, state.today, fromToday = false)
                            onOpenShopping(from, to)
                        }
                    },
                    shape = RoundedCornerShape(28.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp, top = 8.dp)
                        .height(56.dp),
                ) {
                    Icon(Icons.Outlined.ShoppingCart, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(
                        stringResource(R.string.mealplan_shopping),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
            }
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                Modifier.padding(padding).fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "week") {
                    WeekHeader(
                        days = state.days,
                        today = state.today,
                        selected = selectedDay,
                        isThisWeek = state.weekStart == PlanWeek.monday(state.today),
                        onSelect = { day ->
                            selected = day.toString()
                            val index = state.days.indexOfFirst { it.date == day }
                            if (index >= 0) scope.launch { listState.animateScrollToItem(index + 1) }
                        },
                        onPrevious = { selected = null; viewModel.previousWeek() },
                        onNext = { selected = null; viewModel.nextWeek() },
                        onThisWeek = { selected = null; viewModel.thisWeek() },
                    )
                }
                items(state.days, key = { it.date.toString() }) { day ->
                    DayRow(
                        day = day,
                        highlighted = day.date == selectedDay,
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

/** "Mo", "Di", … (erste zwei Buchstaben des Wochentags, ohne Punkt). */
private fun weekdayShort(day: LocalDate): String =
    day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.GERMANY).take(2)

/** Monat der Woche ("November", über den Monatswechsel "November / Dezember"); fremdes Jahr mit Jahreszahl. */
private fun monthTitle(weekStart: LocalDate, today: LocalDate): String {
    val end = weekStart.plusDays(6)
    fun name(d: LocalDate) = d.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.GERMANY)
    val months = if (weekStart.month == end.month) name(weekStart) else "${name(weekStart)} / ${name(end)}"
    return if (end.year != today.year) "$months ${end.year}" else months
}

/** Kopfkarte: Monat, Wochenwechsel und die sieben Tage als Leiste (Punkt = etwas geplant). */
@Composable
private fun WeekHeader(
    days: List<PlanDayUi>,
    today: LocalDate,
    selected: LocalDate,
    isThisWeek: Boolean,
    onSelect: (LocalDate) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onThisWeek: () -> Unit,
) {
    val weekStart = days.firstOrNull()?.date ?: PlanWeek.monday(today)
    GlassCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    monthTitle(weekStart, today),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                if (!isThisWeek) {
                    TextButton(onClick = onThisWeek) { Text(stringResource(R.string.mealplan_this_week)) }
                }
                GlassIconButton(
                    Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                    stringResource(R.string.mealplan_week_prev),
                    onPrevious,
                    Modifier.size(40.dp),
                )
                GlassIconButton(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    stringResource(R.string.mealplan_week_next),
                    onNext,
                    Modifier.padding(start = 8.dp).size(40.dp),
                )
            }
            Row(Modifier.padding(top = 14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                days.forEach { day ->
                    DayPill(
                        date = day.date,
                        planned = day.entries.isNotEmpty(),
                        selected = day.date == selected,
                        onClick = { onSelect(day.date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayPill(date: LocalDate, planned: Boolean, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = VorratTheme.colors
    val content = if (selected) colors.onAccent else MaterialTheme.colorScheme.onSurface
    val shape = RoundedCornerShape(20.dp)
    val label = stringResource(R.string.mealplan_select_day, date.format(DAY_FORMAT))
    Column(
        modifier
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.onSurface else colors.glass)
            .border(1.dp, if (selected) Color.Transparent else colors.glassBorder, shape)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            weekdayShort(date),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) content else colors.textSecondary,
        )
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = content,
        )
        Box(
            Modifier
                .padding(top = 4.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(
                    when {
                        !planned -> Color.Transparent
                        selected -> colors.onAccent
                        else -> colors.accent
                    },
                ),
        )
    }
}

/** Tageszeile: Wochentag + Datum links, geplante Rezepte mit Vorratsstand, Plus zum Hinzufügen. */
@Composable
private fun DayRow(
    day: PlanDayUi,
    highlighted: Boolean,
    onAdd: () -> Unit,
    onOpen: (PlanEntryUi) -> Unit,
    onMove: (PlanEntryUi) -> Unit,
    onRemove: (PlanEntryUi) -> Unit,
) {
    val label = day.date.format(DAY_FORMAT)
    GlassCard(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = if (highlighted) BorderStroke(1.dp, VorratTheme.colors.accent.copy(alpha = 0.6f)) else glassBorder(),
    ) {
        Row(
            Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier.width(48.dp).semantics(mergeDescendants = true) { heading() },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(weekdayShort(day.date), style = MaterialTheme.typography.labelMedium, color = VorratTheme.colors.textSecondary)
                Text(day.date.dayOfMonth.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f).padding(start = 8.dp, end = 8.dp)) {
                if (day.entries.isEmpty()) {
                    Text(
                        stringResource(R.string.mealplan_day_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = VorratTheme.colors.textSecondary,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                }
                day.entries.forEach { entry ->
                    EntryLine(entry, onOpen = { onOpen(entry) }, onMove = { onMove(entry) }, onRemove = { onRemove(entry) })
                }
            }
            IconButton(
                onClick = onAdd,
                modifier = Modifier.size(40.dp).border(1.dp, VorratTheme.colors.glassBorder, CircleShape),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.mealplan_add_for_day, label))
            }
        }
    }
}

/** Ein geplantes Rezept: Titel und "Alles vorrätig" bzw. "Fehlt: …". Lange drücken öffnet Verschieben/Entfernen. */
@Composable
private fun EntryLine(entry: PlanEntryUi, onOpen: () -> Unit, onMove: () -> Unit, onRemove: () -> Unit) {
    val title = entry.recipe?.title ?: stringResource(R.string.mealplan_recipe_missing)
    var menu by remember { mutableStateOf(false) }
    val removeLabel = stringResource(R.string.mealplan_remove)
    val moveLabel = stringResource(R.string.mealplan_move)
    val optionsLabel = stringResource(R.string.mealplan_entry_options, title)
    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .combinedClickable(
                    onClick = { if (entry.recipe != null) onOpen() else menu = true },
                    onLongClick = { menu = true },
                    onLongClickLabel = optionsLabel,
                )
                .semantics {
                    customActions = listOfNotNull(
                        CustomAccessibilityAction(moveLabel) { onMove(); true }.takeIf { entry.recipe != null },
                        CustomAccessibilityAction(removeLabel) { onRemove(); true },
                    )
                }
                .padding(vertical = 4.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (entry.recipe == null) VorratTheme.colors.danger else MaterialTheme.colorScheme.onSurface,
            )
            if (entry.recipe != null) {
                val persons = pluralStringResource(R.plurals.mealplan_for_persons, entry.entry.persons, entry.entry.persons)
                if (entry.missing.isEmpty()) {
                    Text(
                        "${stringResource(R.string.mealplan_all_in_stock)} · $persons",
                        style = MaterialTheme.typography.bodySmall,
                        color = VorratTheme.colors.textSecondary,
                    )
                } else {
                    Text(
                        stringResource(R.string.mealplan_missing, entry.missing.joinToString(", ")),
                        style = MaterialTheme.typography.bodySmall,
                        color = VorratTheme.colors.warning,
                    )
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (entry.recipe != null) {
                DropdownMenuItem(text = { Text(moveLabel) }, onClick = { menu = false; onMove() })
            }
            DropdownMenuItem(text = { Text(removeLabel) }, onClick = { menu = false; onRemove() })
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
