package de.frelina.vorrat.ui.settings

import androidx.compose.material.icons.outlined.Place
import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.RemoveShoppingCart
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.frelina.vorrat.R
import de.frelina.vorrat.domain.InviteCode
import de.frelina.vorrat.ui.update.UpdateViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private fun notificationsAllowed(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
        context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenLocations: () -> Unit,
    onOpenUnits: () -> Unit,
    onOpenQuantityTypes: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenRecipes: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    updates: UpdateViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val reminder by viewModel.reminder.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val pendingImport by viewModel.pendingImport.collectAsStateWithLifecycle()
    val household by viewModel.household.collectAsStateWithLifecycle()
    val memberCount by viewModel.memberCount.collectAsStateWithLifecycle()
    val householdSettings by viewModel.householdSettings.collectAsStateWithLifecycle()
    var nameDialog by remember { mutableStateOf(false) }

    var allowed by remember { mutableStateOf(notificationsAllowed(context)) }
    var requestedOnce by remember { mutableStateOf(false) }
    var rationale by remember { mutableStateOf(false) }
    var timeDialog by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { allowed = notificationsAllowed(context) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        requestedOnce = true
        allowed = granted && notificationsAllowed(context)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(viewModel::export)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::startImport)
    }
    val openNotificationSettings = {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        )
    }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

            // ---------------- Haushalt
            household?.let { h ->
                val formatted = InviteCode.format(h.code)
                SectionHeader(R.string.settings_household)
                ListItem(
                    leadingContent = { Icon(Icons.Outlined.Groups, contentDescription = null) },
                    headlineContent = {
                        Text(formatted, style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace))
                    },
                    overlineContent = { Text(stringResource(R.string.settings_household_code)) },
                    supportingContent = {
                        Text(
                            memberCount?.let { pluralStringResource(R.plurals.settings_household_members, it, it) }
                                ?: stringResource(R.string.settings_household_members_unknown),
                        )
                    },
                    trailingContent = {
                        val shareText = stringResource(R.string.settings_household_share_text, formatted)
                        val chooserTitle = stringResource(R.string.settings_household_share)
                        IconButton(onClick = {
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText)
                            context.startActivity(Intent.createChooser(send, chooserTitle))
                        }) {
                            Icon(Icons.Outlined.Share, contentDescription = chooserTitle)
                        }
                    },
                )
                Text(
                    stringResource(R.string.settings_household_hint),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                ListItem(
                    leadingContent = { Icon(Icons.Outlined.Badge, contentDescription = null) },
                    headlineContent = { Text(stringResource(R.string.settings_my_name)) },
                    supportingContent = {
                        Text(householdSettings.myName.ifBlank { stringResource(R.string.settings_my_name_empty) })
                    },
                    modifier = Modifier.clickable { nameDialog = true },
                )
                ListItem(
                    leadingContent = { Icon(Icons.Outlined.RemoveShoppingCart, contentDescription = null) },
                    headlineContent = { Text(stringResource(R.string.settings_notify_empty)) },
                    supportingContent = { Text(stringResource(R.string.settings_notify_empty_summary)) },
                    trailingContent = {
                        Switch(checked = householdSettings.notifyWhenEmpty, onCheckedChange = viewModel::setNotifyWhenEmpty)
                    },
                )
                ListItem(
                    leadingContent = { Icon(Icons.Outlined.NotificationsActive, contentDescription = null) },
                    headlineContent = { Text(stringResource(R.string.settings_receive_notices)) },
                    supportingContent = { Text(stringResource(R.string.settings_receive_notices_summary)) },
                    trailingContent = {
                        Switch(checked = householdSettings.receiveNotices, onCheckedChange = viewModel::setReceiveNotices)
                    },
                )
                NavItem(Icons.Outlined.History, R.string.history_title, R.string.settings_history_summary, onOpenHistory)
                HorizontalDivider()
            }
            if (nameDialog) {
                var name by rememberSaveable { mutableStateOf(householdSettings.myName) }
                AlertDialog(
                    onDismissRequest = { nameDialog = false },
                    title = { Text(stringResource(R.string.settings_my_name)) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.settings_my_name_hint), style = MaterialTheme.typography.bodyMedium)
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it.take(40) },
                                singleLine = true,
                                label = { Text(stringResource(R.string.settings_my_name)) },
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { viewModel.setMyName(name); nameDialog = false }) {
                            Text(stringResource(R.string.action_save))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { nameDialog = false }) { Text(stringResource(R.string.action_cancel)) }
                    },
                )
            }

            // ---------------- Rezepte
            NavItem(Icons.Outlined.Restaurant, R.string.recipes_title, R.string.settings_recipes_summary, onOpenRecipes)
            HorizontalDivider()

            // ---------------- Stammdaten
            SectionHeader(R.string.settings_catalogs)
            NavItem(Icons.Outlined.Place, R.string.settings_locations, R.string.settings_locations_summary, onOpenLocations)
            NavItem(Icons.Outlined.Straighten, R.string.settings_units, R.string.settings_units_summary, onOpenUnits)
            NavItem(Icons.Outlined.Category, R.string.settings_quantity_types, R.string.settings_quantity_types_summary, onOpenQuantityTypes)
            HorizontalDivider()

            // ---------------- Erinnerungen
            SectionHeader(R.string.settings_reminders)
            ListItem(
                leadingContent = { Icon(Icons.Outlined.NotificationsActive, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.settings_reminders_enabled)) },
                trailingContent = {
                    Switch(
                        checked = reminder.enabled,
                        onCheckedChange = { enabled ->
                            viewModel.setEnabled(enabled)
                            if (enabled && !allowed) rationale = true
                        },
                    )
                },
            )
            if (reminder.enabled) {
                if (!allowed) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.WarningAmber, contentDescription = null)
                                Text(stringResource(R.string.settings_notifications_denied), Modifier.padding(start = 8.dp))
                            }
                            OutlinedButton(onClick = { rationale = true }) {
                                Text(stringResource(R.string.settings_notifications_request))
                            }
                            if (requestedOnce) {
                                OutlinedButton(onClick = openNotificationSettings) {
                                    Text(stringResource(R.string.settings_notifications_open_settings))
                                }
                            }
                        }
                    }
                }
                ListItem(
                    modifier = Modifier,
                    leadingContent = { Icon(Icons.Outlined.Schedule, contentDescription = null) },
                    headlineContent = { Text(stringResource(R.string.settings_reminders_time)) },
                    supportingContent = {
                        Text(
                            LocalDate.now().atTime(reminder.hour, reminder.minute)
                                .format(DateTimeFormatter.ofPattern("HH:mm")),
                        )
                    },
                    trailingContent = {
                        TextButton(onClick = { timeDialog = true }) { Text(stringResource(R.string.action_edit)) }
                    },
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_reminders_lead)) },
                    supportingContent = {
                        Text(pluralStringResource(R.plurals.settings_reminders_lead_days, reminder.leadDays, reminder.leadDays))
                    },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { viewModel.setLeadDays(reminder.leadDays - 1) }, enabled = reminder.leadDays > 0) {
                                Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.action_decrease))
                            }
                            IconButton(onClick = { viewModel.setLeadDays(reminder.leadDays + 1) }, enabled = reminder.leadDays < 60) {
                                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.action_increase))
                            }
                        }
                    },
                )
                Text(
                    stringResource(R.string.settings_reminders_inexact),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            HorizontalDivider()

            // ---------------- Backup
            SectionHeader(R.string.settings_backup)
            NavItem(Icons.Outlined.Upload, R.string.settings_backup_export, R.string.settings_backup_export_summary) {
                if (!busy) exportLauncher.launch("vorrat-backup-${LocalDate.now()}.zip")
            }
            NavItem(Icons.Outlined.Download, R.string.settings_backup_import, R.string.settings_backup_import_summary) {
                if (!busy) importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed"))
            }
            HorizontalDivider()

            // ---------------- App-Update
            SectionHeader(R.string.settings_update)
            val update by updates.state.collectAsStateWithLifecycle()
            ListItem(
                modifier = Modifier.fillMaxWidth().clickable(enabled = !update.checking) {
                    if (update.available != null) updates.showPrompt() else updates.checkNow()
                },
                leadingContent = { Icon(Icons.Outlined.SystemUpdate, contentDescription = null) },
                headlineContent = {
                    Text(
                        update.available?.let { stringResource(R.string.settings_update_available, it.version) }
                            ?: stringResource(if (update.checking) R.string.settings_update_checking else R.string.settings_update_check),
                    )
                },
                supportingContent = {
                    val error = update.error
                    when {
                        error != null && !update.prompt -> Text(stringResource(error), color = MaterialTheme.colorScheme.error)
                        update.checked && update.available == null -> Text(stringResource(R.string.settings_update_current, update.current))
                        else -> Text(stringResource(R.string.settings_update_installed, update.current))
                    }
                },
            )
            HorizontalDivider()

            // ---------------- Über
            NavItem(Icons.Outlined.Info, R.string.settings_about, R.string.settings_about_summary, onOpenAbout)
        }
    }

    if (rationale) {
        AlertDialog(
            onDismissRequest = { rationale = false },
            title = { Text(stringResource(R.string.settings_notifications_rationale_title)) },
            text = { Text(stringResource(R.string.settings_notifications_rationale_text)) },
            confirmButton = {
                TextButton(onClick = {
                    rationale = false
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text(stringResource(R.string.settings_notifications_request)) }
            },
            dismissButton = { TextButton(onClick = { rationale = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (timeDialog) {
        val state = rememberTimePickerState(initialHour = reminder.hour, initialMinute = reminder.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { timeDialog = false },
            title = { Text(stringResource(R.string.settings_reminders_time)) },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setTime(state.hour, state.minute)
                    timeDialog = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { timeDialog = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    pendingImport?.let { parsed ->
        AlertDialog(
            onDismissRequest = viewModel::cancelImport,
            title = { Text(stringResource(R.string.settings_backup_import_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.settings_backup_import_confirm_text,
                        parsed.productCount,
                        parsed.entryCount,
                        parsed.locationCount,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmImport) { Text(stringResource(R.string.settings_backup_import_replace)) }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelImport) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun SectionHeader(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun NavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: Int,
    summary: Int,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(summary)) },
        trailingContent = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, Modifier.size(24.dp)) },
    )
}
