package de.frelina.vorrat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.frelina.vorrat.R
import de.frelina.vorrat.ui.theme.VorratTheme
import de.frelina.vorrat.data.db.StockItem
import de.frelina.vorrat.domain.ExpiryLogic
import de.frelina.vorrat.domain.ExpiryStatus
import de.frelina.vorrat.domain.Validation
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** "Keller · Ebene 2 · Fach 3" */
@Composable
fun locationText(item: StockItem): String = buildList {
    add(item.locationName ?: stringResource(R.string.location_none))
    item.level?.let { add(stringResource(R.string.level_label, it)) }
    item.shelf?.let { add(stringResource(R.string.shelf_label, it)) }
}.joinToString(" · ")

/**
 * Ablauftext, z. B. "Ablauf: 01.09.2026 – abgelaufen". Mit [mentionOpened] = false (Karte zeigt "Geöffnet" als
 * eigene Plakette) heißt es bei geöffneten Packungen "Verbrauchen bis …" statt "Geöffnet – verbrauchen bis …".
 */
@Composable
fun expiryText(item: StockItem, today: LocalDate, leadDays: Int, mentionOpened: Boolean = true): String {
    val date = item.effectiveExpiry ?: return stringResource(R.string.expiry_none)
    val opened = item.openedAt != null && date != item.expiryDate
    val base = if (opened && !mentionOpened) {
        stringResource(R.string.opened_use_by, Validation.formatDate(date))
    } else if (opened) {
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

/** Anteil für den Ablaufbalken: abgelaufen = voll (rot), sonst verbleibende Tage bezogen auf zwei Monate. */
fun expiryFraction(item: StockItem, today: LocalDate): Float? {
    val date = item.effectiveExpiry ?: return null
    val days = java.time.temporal.ChronoUnit.DAYS.between(today, date)
    return if (days < 0) 1f else (days / 60f).coerceIn(0.04f, 1f)
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
    val color = statusColor(status)
    GlassCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ProductImage(item.imagePath, size = 52.dp, name = item.productName)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        item.productName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val sub = listOfNotNull(item.brand?.takeIf { it.isNotBlank() }, locationText(item)).joinToString(" · ")
                    Text(
                        sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = VorratTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    item.frozenAt?.let { frozen ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.AcUnit,
                                contentDescription = null,
                                tint = VorratTheme.colors.info,
                                modifier = Modifier.padding(end = 4.dp).size(14.dp),
                            )
                            Text(
                                stringResource(R.string.frozen_at_label, Validation.formatDate(frozen)),
                                style = MaterialTheme.typography.bodySmall,
                                color = VorratTheme.colors.textSecondary,
                            )
                            if (item.nfcTagId != null) {
                                Icon(
                                    Icons.Outlined.Nfc,
                                    contentDescription = stringResource(R.string.nfc_linked),
                                    tint = VorratTheme.colors.textSecondary,
                                    modifier = Modifier.padding(start = 6.dp).size(14.dp),
                                )
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (status == ExpiryStatus.EXPIRED || status == ExpiryStatus.SOON) {
                            Icon(
                                if (status == ExpiryStatus.EXPIRED) Icons.Outlined.ErrorOutline else Icons.Outlined.Schedule,
                                contentDescription = null,
                                tint = color,
                                modifier = Modifier.padding(end = 4.dp).size(14.dp),
                            )
                        }
                        Text(
                            expiryText(item, today, leadDays, mentionOpened = false),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (status == ExpiryStatus.EXPIRED || status == ExpiryStatus.SOON) color
                            else VorratTheme.colors.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        item.openedAt?.let { opened -> OpenedBadge(opened, Modifier.padding(start = 8.dp)) }
                    }
                }
                CountCircle(stringResource(R.string.quantity_label, item.quantity))
            }
            expiryFraction(item, today)?.let { fraction ->
                ExpiryBar(fraction, color, Modifier.padding(top = 10.dp))
            }
        }
    }
}

/** Kleine Plakette "Geöffnet 10.11." neben dem Ablaufdatum. */
@Composable
fun OpenedBadge(openedAt: LocalDate, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.tertiary
    val label = stringResource(R.string.opened_badge, openedAt.format(SHORT_DATE))
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = color,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

private val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.")
