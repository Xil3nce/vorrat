package de.frelina.vorrat.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import de.frelina.vorrat.ui.components.GlassCard
import de.frelina.vorrat.ui.components.SectionLabel
import de.frelina.vorrat.ui.components.initialsOf
import de.frelina.vorrat.ui.components.pastelFor
import de.frelina.vorrat.ui.theme.VorratTheme
import androidx.compose.ui.text.font.FontWeight
import de.frelina.vorrat.ui.components.GlassIconButton
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.frelina.vorrat.R
import de.frelina.vorrat.data.db.HistoryAction
import de.frelina.vorrat.data.db.HistoryEventEntity
import de.frelina.vorrat.domain.HistoryLogic
import de.frelina.vorrat.domain.Validation
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var messageDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    GlassIconButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back), onBack, Modifier.padding(start = 8.dp))
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { messageDialog = true },
                icon = { Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null) },
                text = { Text(stringResource(R.string.history_send_message)) },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    HistoryFilter.ALL to R.string.history_filter_all,
                    HistoryFilter.ADDED to R.string.history_filter_added,
                    HistoryFilter.REMOVED to R.string.history_filter_removed,
                    HistoryFilter.MESSAGES to R.string.history_filter_messages,
                ).forEach { (f, label) ->
                    FilterChip(
                        selected = state.filter == f,
                        onClick = { viewModel.setFilter(f) },
                        label = { Text(stringResource(label)) },
                    )
                }
            }
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.events.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.history_empty),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                else -> {
                    val days = remember(state.events) { HistoryLogic.groupByDay(state.events) }
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        days.forEach { (day, events) ->
                            item(key = "day-$day") { DayHeader(day) }
                            items(events, key = { it.id }) { e ->
                                HistoryRow(e, state.myUid, onNotify = { viewModel.notifyOther(e) })
                            }
                        }
                    }
                }
            }
        }
    }

    if (messageDialog) {
        MessageDialog(
            suggestions = state.outOfStockNames,
            onDismiss = { messageDialog = false },
            onSend = { viewModel.send(it); messageDialog = false },
        )
    }
}

@Composable
private fun DayHeader(day: LocalDate) {
    val today = LocalDate.now()
    val label = when (day) {
        today -> stringResource(R.string.history_today)
        today.minusDays(1) -> stringResource(R.string.history_yesterday)
        else -> Validation.formatDate(day)
    }
    SectionLabel(label, Modifier.padding(start = 4.dp, end = 16.dp, top = 12.dp, bottom = 2.dp))
}

@Composable
private fun HistoryRow(e: HistoryEventEntity, myUid: String?, onNotify: () -> Unit) {
    val who = when {
        e.authorUid == myUid -> stringResource(R.string.history_you)
        else -> e.authorName ?: stringResource(R.string.history_someone)
    }
    val time = Instant.ofEpochMilli(e.createdAt).atZone(ZoneId.systemDefault()).format(TIME_FORMAT)
    val amount = e.quantity?.let { q -> listOfNotNull(q.toString(), e.quantityTypeSymbol).joinToString(" ") }.orEmpty()
    val headline = when (e.action) {
        HistoryAction.ADDED -> stringResource(R.string.history_added, who, amount, e.productName)
        HistoryAction.REMOVED -> stringResource(R.string.history_removed, who, amount, e.productName)
        HistoryAction.DELETED -> stringResource(R.string.history_deleted, who, e.productName)
        HistoryAction.OPENED -> stringResource(R.string.history_opened, who, e.productName)
        HistoryAction.MESSAGE -> stringResource(R.string.history_message, who, e.message.orEmpty())
    }
    val dotColor = when (e.action) {
        HistoryAction.ADDED -> VorratTheme.colors.accent
        HistoryAction.REMOVED -> VorratTheme.colors.warning
        HistoryAction.DELETED -> VorratTheme.colors.danger
        HistoryAction.OPENED -> VorratTheme.colors.info
        HistoryAction.MESSAGE -> VorratTheme.colors.info
    }
    val avatarColor = pastelFor(who)
    GlassCard(Modifier.fillMaxWidth()) {
    ListItem(
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            supportingColor = VorratTheme.colors.textSecondary,
        ),
        leadingContent = {
            Box(Modifier.size(44.dp)) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(avatarColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        initialsOf(who),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = VorratTheme.colors.onAccent,
                    )
                }
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
            }
        },
        headlineContent = { Text(headline) },
        supportingContent = {
            val parts = buildList {
                add(time)
                if (e.outOfStock) add(stringResource(R.string.history_out_of_stock, e.productName))
                if (e.notify && e.action != HistoryAction.MESSAGE) add(stringResource(R.string.history_notified))
            }
            Text(parts.joinToString(" · "))
        },
        trailingContent = if (e.outOfStock && !e.notify && e.authorUid == myUid) {
            {
                TextButton(onClick = onNotify) {
                    Icon(Icons.Outlined.NotificationsActive, contentDescription = null)
                    Text(stringResource(R.string.history_notify_action), modifier = Modifier.padding(start = 4.dp))
                }
            }
        } else {
            null
        },
    )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun MessageDialog(suggestions: List<String>, onDismiss: () -> Unit, onSend: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.history_send_message)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.history_message_hint), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(200) },
                    label = { Text(stringResource(R.string.history_message_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (suggestions.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestions.forEach { name ->
                            val suggestion = stringResource(R.string.history_is_empty_message, name)
                            AssistChip(onClick = { text = suggestion }, label = { Text(suggestion) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSend(text) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.history_send))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
