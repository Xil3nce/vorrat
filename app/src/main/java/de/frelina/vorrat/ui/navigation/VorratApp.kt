package de.frelina.vorrat.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import de.frelina.vorrat.ui.components.MessagePill
import de.frelina.vorrat.ui.components.ToneSnackbarVisuals
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import de.frelina.vorrat.ui.theme.VorratTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import de.frelina.vorrat.R
import de.frelina.vorrat.ui.components.UiMessages
import de.frelina.vorrat.ui.locations.LocationsScreen
import de.frelina.vorrat.ui.mealplan.MealPlanScreen
import de.frelina.vorrat.ui.mealplan.PlanShoppingScreen
import de.frelina.vorrat.ui.product.StockFormScreen
import de.frelina.vorrat.ui.recipes.EditRecipeScreen
import de.frelina.vorrat.ui.recipes.RecipeDetailScreen
import de.frelina.vorrat.ui.recipes.RecipesScreen
import de.frelina.vorrat.ui.scan.ScanScreen
import de.frelina.vorrat.ui.settings.AboutScreen
import de.frelina.vorrat.ui.settings.CatalogScreen
import de.frelina.vorrat.ui.settings.SettingsScreen
import de.frelina.vorrat.ui.shopping.DepartmentsScreen
import de.frelina.vorrat.ui.shopping.ShoppingScreen
import de.frelina.vorrat.ui.stock.EntryDetailScreen
import de.frelina.vorrat.ui.stock.StockScreen
import de.frelina.vorrat.ui.update.UpdateDialog
import de.frelina.vorrat.ui.update.UpdateViewModel
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

private data class TopLevel(val route: Any, val routeClass: KClass<*>, val label: Int, val icon: ImageVector)

private val TOP_LEVEL = listOf(
    TopLevel(StockRoute(), StockRoute::class, R.string.nav_stock, Icons.Outlined.Inventory2),
    TopLevel(OverviewRoute, OverviewRoute::class, R.string.nav_overview, Icons.Outlined.Dashboard),
    TopLevel(ScanRoute, ScanRoute::class, R.string.nav_scan, Icons.Outlined.QrCodeScanner),
    TopLevel(ShoppingRoute, ShoppingRoute::class, R.string.nav_shopping, Icons.Outlined.ShoppingCart),
    TopLevel(SettingsRoute, SettingsRoute::class, R.string.nav_settings, Icons.Outlined.Settings),
)

/**
 * App-Gerüst: untere Navigationsleiste mit fünf Bereichen, NavHost, globale Snackbar.
 * [openSoonFilter] liefert Ereignisse, wenn die App über die Erinnerung geöffnet wird.
 */
@Composable
fun VorratRoot(
    messages: UiMessages,
    openSoonFilter: Flow<Unit>,
    openHistory: Flow<Unit>,
    nfcTags: Flow<de.frelina.vorrat.nfc.NfcTagRead>,
    sharedRecipes: Flow<String>,
    updates: UpdateViewModel = hiltViewModel(),
    nfc: NfcViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    var nfcReadDialog by remember { mutableStateOf(false) }
    var unknownChip by remember { mutableStateOf<NfcTarget.Unknown?>(null) }
    val scope = rememberCoroutineScope()
    val onChip: (de.frelina.vorrat.nfc.NfcTagRead) -> Unit = { read ->
        scope.launch {
            when (val target = nfc.resolve(read)) {
                is NfcTarget.Entry -> navController.navigate(EntryDetailRoute(target.entryId)) { launchSingleTop = true }
                is NfcTarget.Unknown -> unknownChip = target
            }
        }
    }
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val showBottomBar = TOP_LEVEL.any { t -> destination?.hierarchy?.any { it.hasRoute(t.routeClass) } == true }
    LaunchedEffect(Unit) {
        messages.messages.collect { msg ->
            val text = if (msg.pluralCount != null) {
                resources.getQuantityString(msg.text, msg.pluralCount, msg.pluralCount, *msg.args.toTypedArray())
            } else {
                resources.getString(msg.text, *msg.args.toTypedArray())
            }
            val result = snackbar.showSnackbar(
                ToneSnackbarVisuals(
                    message = text,
                    actionLabel = msg.actionLabel?.let(resources::getString),
                    duration = if (msg.onAction != null) SnackbarDuration.Long else SnackbarDuration.Short,
                    tone = msg.tone,
                ),
            )
            if (result == SnackbarResult.ActionPerformed) msg.onAction?.invoke()
        }
    }
    LaunchedEffect(Unit) { updates.checkAutomatically() }
    UpdateDialog(updates)
    LaunchedEffect(Unit) {
        openSoonFilter.collect {
            navController.navigate(StockRoute(filter = de.frelina.vorrat.MainActivity.FILTER_SOON)) {
                popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(Unit) {
        openHistory.collect { navController.navigate(HistoryRoute) { launchSingleTop = true } }
    }
    LaunchedEffect(Unit) { nfcTags.collect { onChip(it) } }
    LaunchedEffect(Unit) {
        sharedRecipes.collect { text ->
            if (de.frelina.vorrat.domain.ChefkochLink.recipeId(text) != null) {
                navController.navigate(EditRecipeRoute(importText = text))
            } else {
                messages.show(de.frelina.vorrat.ui.components.UiMessage(de.frelina.vorrat.R.string.recipe_import_invalid_link, tone = de.frelina.vorrat.ui.components.MessageTone.ERROR))
            }
        }
    }
    if (nfcReadDialog) {
        de.frelina.vorrat.nfc.NfcDialog(
            mode = de.frelina.vorrat.nfc.NfcMode.Read,
            onDismiss = { nfcReadDialog = false },
            onRead = { nfcReadDialog = false; onChip(it) },
        )
    }
    unknownChip?.let { chip ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { unknownChip = null },
            title = { Text(stringResource(R.string.nfc_unknown_title)) },
            text = { Text(stringResource(R.string.nfc_unknown_text)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    unknownChip = null
                    navController.navigate(AddStockRoute(frozen = true, nfcTagId = chip.tagId))
                }) { Text(stringResource(R.string.frozen_add)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { unknownChip = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            if (showBottomBar) {
                FloatingNavBar(
                    isSelected = { item -> destination?.hierarchy?.any { it.hasRoute(item.routeClass) } == true },
                    onSelect = { item -> navController.navigateTopLevel(item.route) },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = StockRoute(),
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable<StockRoute> {
                StockScreen(
                    onOpenEntry = { navController.navigate(EntryDetailRoute(it)) },
                    onAddManual = { navController.navigate(AddStockRoute()) },
                    onDuplicateEntry = { navController.navigate(DuplicateEntryRoute(it)) },
                    onAddFrozen = { navController.navigate(AddStockRoute(frozen = true)) },
                    onReadNfc = { nfcReadDialog = true },
                    onOpenHistory = { navController.navigate(HistoryRoute) },
                    onOpenRecipes = { navController.navigate(RecipesRoute) },
                )
            }
            composable<OverviewRoute> {
                LocationsScreen(onBack = null, onOpenLocation = { navController.openStockAt(it) })
            }
            composable<ScanRoute> {
                ScanScreen(
                    onOpenEntry = { navController.navigate(EntryDetailRoute(it)) },
                    onAddNewProduct = { navController.navigate(AddStockRoute(barcode = it)) },
                    onAddEntryForProduct = { navController.navigate(AddStockRoute(productId = it)) },
                    onCreateManual = { navController.navigate(AddStockRoute()) },
                    onReadNfc = { nfcReadDialog = true },
                    onOpenPurchase = { navController.navigate(PurchaseRoute) },
                )
            }
            composable<PurchaseRoute> {
                de.frelina.vorrat.ui.purchase.PurchaseScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDraft = { navController.navigate(AddStockRoute(draftId = it)) },
                )
            }
            composable<ShoppingRoute> {
                ShoppingScreen(
                    onBought = { productId, itemId ->
                        navController.navigate(AddStockRoute(productId = productId, shoppingItemId = itemId))
                    },
                    onOpenDepartments = { navController.navigate(DepartmentsRoute) },
                    onOpenMealPlan = { navController.navigate(MealPlanRoute) },
                )
            }
            composable<LocationsRoute> {
                LocationsScreen(onBack = { navController.popBackStack() }, onOpenLocation = { navController.openStockAt(it) })
            }
            composable<SettingsRoute> {
                SettingsScreen(
                    onOpenLocations = { navController.navigate(LocationsRoute) },
                    onOpenUnits = { navController.navigate(CatalogRoute(CatalogRoute.UNITS)) },
                    onOpenQuantityTypes = { navController.navigate(CatalogRoute(CatalogRoute.QUANTITY_TYPES)) },
                    onOpenAbout = { navController.navigate(AboutRoute) },
                    onOpenHistory = { navController.navigate(HistoryRoute) },
                    onOpenRecipes = { navController.navigate(RecipesRoute) },
                    onOpenMealPlan = { navController.navigate(MealPlanRoute) },
                    onOpenDepartments = { navController.navigate(DepartmentsRoute) },
                )
            }
            composable<EntryDetailRoute> {
                EntryDetailScreen(
                    onBack = { navController.popBackStack() },
                    onReplace = { id ->
                        navController.navigate(EntryDetailRoute(id)) {
                            popUpTo<EntryDetailRoute> { inclusive = true }
                        }
                    },
                    onEditEntry = { navController.navigate(EditEntryRoute(it)) },
                    onEditProduct = { navController.navigate(EditProductRoute(it)) },
                )
            }
            composable<AddStockRoute> { StockFormScreen(onDone = { navController.popBackStack() }) }
            composable<EditEntryRoute> { StockFormScreen(onDone = { navController.popBackStack() }) }
            composable<DuplicateEntryRoute> { StockFormScreen(onDone = { navController.popBackStack() }) }
            composable<EditProductRoute> { StockFormScreen(onDone = { navController.popBackStack() }) }
            composable<CatalogRoute> { CatalogScreen(onBack = { navController.popBackStack() }) }
            composable<AboutRoute> { AboutScreen(onBack = { navController.popBackStack() }) }
            composable<RecipesRoute> {
                RecipesScreen(
                    onBack = { navController.popBackStack() },
                    onOpenRecipe = { navController.navigate(RecipeDetailRoute(it)) },
                    onAddRecipe = { navController.navigate(EditRecipeRoute()) },
                    onOpenMealPlan = { navController.navigate(MealPlanRoute) },
                )
            }
            composable<RecipeDetailRoute> {
                RecipeDetailScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(EditRecipeRoute(it)) },
                    onCopy = { navController.navigate(EditRecipeRoute(it)) },
                )
            }
            composable<DepartmentsRoute> { DepartmentsScreen(onBack = { navController.popBackStack() }) }
            composable<MealPlanRoute> {
                MealPlanScreen(
                    onBack = { navController.popBackStack() },
                    onOpenRecipe = { id, persons -> navController.navigate(RecipeDetailRoute(id, persons)) },
                    onOpenShopping = { from, to -> navController.navigate(PlanShoppingRoute(from.toString(), to.toString())) },
                )
            }
            composable<PlanShoppingRoute> { PlanShoppingScreen(onBack = { navController.popBackStack() }) }
            composable<EditRecipeRoute> { EditRecipeScreen(onDone = { navController.popBackStack() }) }
            composable<HistoryRoute> {
                de.frelina.vorrat.ui.history.HistoryScreen(onBack = { navController.popBackStack() })
            }
        }
    }
    // Meldungen als Glas-Pille oben, knapp unter der Kopfzeile (verdecken weder FABs noch die Navigationsleiste).
    SnackbarHost(
        snackbar,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .statusBarsPadding()
            .padding(top = 60.dp, start = 12.dp, end = 12.dp),
    ) { data -> MessagePill(data) }
    }
}

/**
 * Schwebende Leiste (Abstand 12 dp seitlich, 4 dp unten); der Scan-Punkt in der Mitte sitzt auf derselben
 * Zeilenhöhe wie die anderen, als Kreis in Akzentfarbe hervorgehoben.
 */
@Composable
private fun FloatingNavBar(isSelected: (TopLevel) -> Boolean, onSelect: (TopLevel) -> Unit) {
    val colors = VorratTheme.colors
    val scan = TOP_LEVEL.first { it.routeClass == ScanRoute::class }
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, bottom = 4.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(36.dp))
                .background(colors.bar)
                .border(1.dp, colors.glassBorder, RoundedCornerShape(36.dp))
                .selectableGroup()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TOP_LEVEL.forEach { item ->
                val selected = isSelected(item)
                val tint = if (selected) colors.accent else colors.textSecondary
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(24.dp))
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(item) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (item === scan) {
                        Box(
                            Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(if (selected) colors.accent else colors.accent.copy(alpha = 0.16f))
                                .border(1.5.dp, colors.accent.copy(alpha = 0.7f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                item.icon,
                                contentDescription = null,
                                tint = if (selected) colors.onAccent else colors.accent,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    } else {
                        Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                    }
                    Text(
                        stringResource(item.label),
                        color = tint,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                    )
                }
            }
        }
    }
}

/** Bestand nach einem Ort gefiltert öffnen (neuer Bestand-Eintrag, damit der Filter aus der Route greift). */
private fun NavHostController.openStockAt(locationId: String) {
    navigate(StockRoute(locationId = locationId)) {
        popUpTo(graph.findStartDestination().id) { inclusive = true }
        launchSingleTop = true
    }
}

private fun NavHostController.navigateTopLevel(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
