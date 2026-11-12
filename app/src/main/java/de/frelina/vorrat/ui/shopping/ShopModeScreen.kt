package de.frelina.vorrat.ui.shopping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.frelina.vorrat.R
import de.frelina.vorrat.domain.BoardRow
import de.frelina.vorrat.domain.DepartmentDefaults
import de.frelina.vorrat.domain.ShoppingLogic
import de.frelina.vorrat.ui.components.AmountBadge
import de.frelina.vorrat.ui.components.GlassCard
import de.frelina.vorrat.ui.components.GlassIconButton
import de.frelina.vorrat.ui.components.ProductImage
import de.frelina.vorrat.ui.components.ProgressRing
import de.frelina.vorrat.ui.components.SectionLabel
import de.frelina.vorrat.ui.theme.VorratTheme

/**
 * Einkaufsmodus für den Laden: große Zeilen zum Abhaken in der Reihenfolge der Abteilungen, der Bildschirm bleibt
 * an. Abgehaktes wandert nach unten ("Im Wagen") und liegt als Artikel im Einkauf ("+ Bestand").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopModeScreen(
    onBack: () -> Unit,
    onOpenPurchase: () -> Unit,
    viewModel: ShopModeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Im Laden soll das Handy nicht ständig entsperrt werden müssen.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.shop_mode_title), fontWeight = FontWeight.SemiBold) },
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
            if (state.cartCount > 0) {
                Button(
                    onClick = onOpenPurchase,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(56.dp),
                ) {
                    Text(stringResource(R.string.shop_mode_open_purchase, state.cartCount))
                }
            }
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.total == 0 -> Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.shop_mode_empty),
                    textAlign = TextAlign.Center,
                    color = VorratTheme.colors.textSecondary,
                )
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "progress") { ShopProgress(done = state.checked.size, total = state.total) }
                items(state.open, key = { it.key }) { row ->
                    val payload = row.payload
                    if (payload == null) {
                        SectionLabel(
                            state.departmentNames[row.departmentId] ?: DepartmentDefaults.nameOf(row.departmentId).orEmpty(),
                            Modifier.padding(top = 8.dp, start = 4.dp).animateItem(),
                        )
                    } else {
                        ShopRow(payload, state.units, checked = false, onToggle = { viewModel.check(row) }, Modifier.animateItem())
                    }
                }
                if (state.checked.isNotEmpty()) {
                    item(key = "h-cart") {
                        SectionLabel(
                            stringResource(R.string.shop_mode_in_cart),
                            Modifier.padding(top = 8.dp, start = 4.dp).animateItem(),
                        )
                    }
                    items(state.checked, key = { it.key }) { row ->
                        val payload = row.payload ?: return@items
                        ShopRow(payload, state.units, checked = true, onToggle = { viewModel.uncheck(row) }, Modifier.animateItem())
                    }
                }
            }
        }
    }
}

/** Fortschritt oben: Ring und "3 von 12 im Wagen". */
@Composable
private fun ShopProgress(done: Int, total: Int) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ProgressRing(progress = if (total > 0) done.toFloat() / total else 0f)
            Column(Modifier.weight(1f)) {
                Text(
                    if (done >= total) stringResource(R.string.shop_mode_all_done)
                    else stringResource(R.string.shop_mode_progress, done, total),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.shop_mode_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = VorratTheme.colors.textSecondary,
                )
            }
        }
    }
}

/** Große Zeile zum Abhaken: Haken, Bild, Name, Menge. Die ganze Zeile ist die Schaltfläche. */
@Composable
private fun ShopRow(
    payload: ShoppingPayload,
    units: Map<String, String>,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val product = when (payload) {
        is ShoppingPayload.Short -> payload.shortage.product
        is ShoppingPayload.Item -> payload.entry.product
    }
    val amount = when (payload) {
        is ShoppingPayload.Short -> {
            val pack = ShoppingLogic.packageLabel(product?.packageSize, product?.unitId?.let(units::get))
            val missing = payload.shortage.missing
            if (pack != null) stringResource(R.string.shopping_quantity_package, missing, pack)
            else stringResource(R.string.shopping_quantity, missing)
        }
        is ShoppingPayload.Item -> shoppingAmountText(payload.entry.item)
    }
    val note = (payload as? ShoppingPayload.Item)?.entry?.item?.note?.takeIf { it.isNotBlank() }
    val stateText = stringResource(if (checked) R.string.shop_mode_in_cart else R.string.shop_mode_state_open)
    GlassCard(
        onClick = onToggle,
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (checked) 0.55f else 1f)
            .semantics {
                role = Role.Checkbox
                stateDescription = stateText
            },
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 84.dp).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                if (checked) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (checked) VorratTheme.colors.accent else VorratTheme.colors.textSecondary,
                modifier = Modifier.size(32.dp),
            )
            ProductImage(product?.imagePath, size = 52.dp, name = payload.name)
            Column(Modifier.weight(1f)) {
                Text(
                    payload.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (checked) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                listOfNotNull(product?.brand?.takeIf { it.isNotBlank() }, note).joinToString(" · ").takeIf { it.isNotEmpty() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = VorratTheme.colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            AmountBadge(amount)
        }
    }
}
