package de.frelina.vorrat.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.frelina.vorrat.R
import de.frelina.vorrat.data.db.StockItem
import de.frelina.vorrat.domain.ExpiryLogic
import de.frelina.vorrat.domain.ExpiryStatus
import de.frelina.vorrat.domain.Validation
import java.time.LocalDate

/** "Keller · Ebene 2 · Fach 3" */
@Composable
fun locationText(item: StockItem): String = buildList {
    add(item.locationName ?: stringResource(R.string.location_none))
    item.level?.let { add(stringResource(R.string.level_label, it)) }
    item.shelf?.let { add(stringResource(R.string.shelf_label, it)) }
}.joinToString(" · ")

@Composable
fun expiryText(item: StockItem, today: LocalDate, leadDays: Int): String {
    val date = item.effectiveExpiry ?: return stringResource(R.string.expiry_none)
    val opened = item.openedAt != null && date != item.expiryDate
    val base = if (opened) {
        stringResource(R.string.expiry_opened_label, Validation.formatDate(date))
    } else {
        stringResource(R.string.expiry_label, Validation.formatDate(date))
    }
    val suffix = when (ExpiryLogic.status(date, today, leadDays)) {
        ExpiryStatus.EXPIRED -> stringResource(R.string.expiry_expired)
        ExpiryStatus.SOON ->
            if (ExpiryLogic.isExpiringToday(date, today)) stringResource(R.string.expiry_today)
            else stringResource(R.string.expiry_soon)
        else -> null
    }
    return if (suffix != null) "$base – $suffix" else base
}

@Composable
fun StockItemCard(
    item: StockItem,
    today: LocalDate,
    leadDays: Int,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val status = ExpiryLogic.status(item.effectiveExpiry, today, leadDays)
    val colors = expiryColors(status)
    val cardColors = if (colors != null) {
        CardDefaults.cardColors(containerColor = colors.container, contentColor = colors.content)
    } else {
        CardDefaults.cardColors()
    }
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProductImage(item.imagePath, size = 56.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    item.productName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                item.brand?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(locationText(item), style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (status == ExpiryStatus.EXPIRED || status == ExpiryStatus.SOON) {
                        Icon(
                            if (status == ExpiryStatus.EXPIRED) Icons.Outlined.ErrorOutline else Icons.Outlined.Schedule,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    Text(expiryText(item, today, leadDays), style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(
                stringResource(R.string.quantity_label, item.quantity, item.quantityTypeSymbol),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), colors = cardColors) { content() }
    } else {
        Card(modifier = modifier.fillMaxWidth(), colors = cardColors) { content() }
    }
}
