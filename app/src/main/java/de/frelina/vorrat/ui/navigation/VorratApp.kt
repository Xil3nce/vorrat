package de.frelina.vorrat.ui.navigation

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import de.frelina.vorrat.ui.product.StockFormScreen
import de.frelina.vorrat.ui.scan.ScanScreen
import de.frelina.vorrat.ui.search.SearchScreen
import de.frelina.vorrat.ui.settings.AboutScreen
import de.frelina.vorrat.ui.settings.CatalogScreen
import de.frelina.vorrat.ui.settings.SettingsScreen
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
    TopLevel(SearchRoute, SearchRoute::class, R.string.nav_search, Icons.Outlined.Search),
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
    // Screens mit FAB bzw. unterer Bedienleiste: Snackbar darüber anzeigen, damit sie nichts verdeckt.
    val liftSnackbar = listOf(StockRoute::class, LocationsRoute::class, ScanRoute::class, ShoppingRoute::class)
        .any { r -> destination?.hierarchy?.any { it.hasRoute(r) } == true }

    LaunchedEffect(Unit) {
        messages.messages.collect { msg ->
            val text = resources.getString(msg.text, *msg.args.toTypedArray())
            val result = snackbar.showSnackbar(
                message = text,
                actionLabel = msg.actionLabel?.let(resources::getString),
                duration = if (msg.onAction != null) SnackbarDuration.Long else SnackbarDuration.Short,
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = if (liftSnackbar) 72.dp else 0.dp)) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TOP_LEVEL.forEach { item ->
                        val selected = destination?.hierarchy?.any { it.hasRoute(item.routeClass) } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateTopLevel(item.route) },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(stringResource(item.label), maxLines = 1, softWrap = false, overflow = TextOverflow.Visible) },
                        )
                    }
                }
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
                )
            }
            composable<SearchRoute> {
                SearchScreen(onOpenEntry = { navController.navigate(EntryDetailRoute(it)) })
            }
            composable<ScanRoute> {
                ScanScreen(
                    onOpenEntry = { navController.navigate(EntryDetailRoute(it)) },
                    onAddNewProduct = { navController.navigate(AddStockRoute(barcode = it)) },
                    onAddEntryForProduct = { navController.navigate(AddStockRoute(productId = it)) },
                    onCreateManual = { navController.navigate(AddStockRoute()) },
                )
            }
            composable<ShoppingRoute> {
                ShoppingScreen(
                    onBought = { productId, itemId ->
                        navController.navigate(AddStockRoute(productId = productId, shoppingItemId = itemId))
                    },
                )
            }
            composable<LocationsRoute> { LocationsScreen(onBack = { navController.popBackStack() }) }
            composable<SettingsRoute> {
                SettingsScreen(
                    onOpenLocations = { navController.navigate(LocationsRoute) },
                    onOpenUnits = { navController.navigate(CatalogRoute(CatalogRoute.UNITS)) },
                    onOpenQuantityTypes = { navController.navigate(CatalogRoute(CatalogRoute.QUANTITY_TYPES)) },
                    onOpenAbout = { navController.navigate(AboutRoute) },
                    onOpenHistory = { navController.navigate(HistoryRoute) },
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
            composable<HistoryRoute> {
                de.frelina.vorrat.ui.history.HistoryScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

private fun NavHostController.navigateTopLevel(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
