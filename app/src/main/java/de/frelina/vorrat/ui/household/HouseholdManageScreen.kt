package de.frelina.vorrat.ui.household

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.frelina.vorrat.R
import de.frelina.vorrat.domain.HouseholdMember
import de.frelina.vorrat.domain.InviteCode
import de.frelina.vorrat.domain.Validation
import de.frelina.vorrat.ui.components.GlassCard
import de.frelina.vorrat.ui.components.GlassIconButton
import de.frelina.vorrat.ui.components.SectionLabel
import de.frelina.vorrat.ui.components.initialsOf
import de.frelina.vorrat.ui.components.pastelFor
import de.frelina.vorrat.ui.theme.VorratTheme
import java.time.Instant
import java.time.ZoneId

/** Haushalt verwalten: Einladungscode (teilen, erneuern), Mitglieder (entfernen), Haushalt verlassen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HouseholdManageScreen(onBack: () -> Unit, viewModel: HouseholdManageViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var confirmRenew by rememberSaveable { mutableStateOf(false) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    var removeUid by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val unknownName = stringResource(R.string.household_member_unknown)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.household_manage_title), fontWeight = FontWeight.SemiBold) },
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
    ) { padding ->
        val household = state.household ?: return@Scaffold
        val members = state.members
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

            // ---------------- Einladungscode
            SectionLabel(stringResource(R.string.settings_household_code), Modifier.padding(start = 4.dp, top = 8.dp))
            GlassCard(Modifier.fillMaxWidth()) {
                val formatted = InviteCode.format(household.code)
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatted,
                        style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.weight(1f),
                    )
                    val shareText = stringResource(R.string.settings_household_share_text, formatted)
                    val chooserTitle = stringResource(R.string.settings_household_share)
                    IconButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText)
                        context.startActivity(Intent.createChooser(send, chooserTitle))
                    }) {
                        Icon(Icons.Outlined.Share, contentDescription = chooserTitle)
                    }
                }
                Text(
                    stringResource(R.string.household_code_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = VorratTheme.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                TextButton(onClick = { confirmRenew = true }, enabled = !busy, modifier = Modifier.padding(horizontal = 4.dp)) {
                    Icon(Icons.Outlined.Autorenew, contentDescription = null, Modifier.padding(end = 8.dp).size(18.dp))
                    Text(stringResource(R.string.household_renew_code))
                }
            }

            // ---------------- Mitglieder
            SectionLabel(stringResource(R.string.household_members), Modifier.padding(start = 4.dp, top = 12.dp))
            GlassCard(Modifier.fillMaxWidth()) {
                if (members == null) {
                    Text(
                        stringResource(R.string.household_members_loading),
                        color = VorratTheme.colors.textSecondary,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    members.forEachIndexed { index, member ->
                        if (index > 0) HorizontalDivider(color = VorratTheme.colors.glassBorder)
                        MemberRow(member, enabled = !busy, onRemove = { removeUid = member.uid })
                    }
                }
            }
            Text(
                stringResource(R.string.household_members_hint),
                style = MaterialTheme.typography.bodySmall,
                color = VorratTheme.colors.textSecondary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            // ---------------- Verlassen
            OutlinedButton(
                onClick = { confirmLeave = true },
                enabled = !busy,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp),
            ) {
                Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, Modifier.padding(end = 8.dp).size(18.dp))
                Text(stringResource(R.string.household_leave))
            }
        }

        if (confirmRenew) {
            ConfirmDialog(
                title = R.string.household_renew_code_title,
                text = stringResource(R.string.household_renew_code_text),
                confirm = R.string.household_renew_code,
                onConfirm = { confirmRenew = false; viewModel.renewCode() },
                onDismiss = { confirmRenew = false },
            )
        }
        removeUid?.let { uid ->
            val name = members?.firstOrNull { it.uid == uid }?.name ?: unknownName
            ConfirmDialog(
                title = R.string.household_remove_title,
                text = stringResource(R.string.household_remove_text, name),
                confirm = R.string.household_remove_confirm,
                onConfirm = { removeUid = null; viewModel.removeMember(uid) },
                onDismiss = { removeUid = null },
            )
        }
        if (confirmLeave) {
            val alone = members != null && members.size <= 1
            ConfirmDialog(
                title = R.string.household_leave_title,
                text = stringResource(if (alone) R.string.household_leave_text_alone else R.string.household_leave_text),
                confirm = R.string.household_leave_confirm,
                onConfirm = { confirmLeave = false; viewModel.leave() },
                onDismiss = { confirmLeave = false },
            )
        }
    }
}

@Composable
private fun MemberRow(member: HouseholdMember, enabled: Boolean, onRemove: () -> Unit) {
    val name = member.name ?: stringResource(R.string.household_member_unknown)
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(pastelFor(member.uid)), contentAlignment = Alignment.Center) {
            Text(
                initialsOf(member.name ?: "?"),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = VorratTheme.colors.onAccent,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                if (member.isMe) stringResource(R.string.household_member_me, name) else name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val details = listOfNotNull(
                member.joinedAt?.let { stringResource(R.string.household_member_since, formatDay(it)) },
                member.lastActive?.let { stringResource(R.string.household_member_active, formatDay(it)) }
                    ?: stringResource(R.string.household_member_inactive).takeIf { !member.isMe },
            )
            if (details.isNotEmpty()) {
                Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = VorratTheme.colors.textSecondary)
            }
        }
        if (!member.isMe) {
            IconButton(onClick = onRemove, enabled = enabled) {
                Icon(
                    Icons.Outlined.PersonRemove,
                    contentDescription = stringResource(R.string.household_remove_member, name),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ConfirmDialog(title: Int, text: String, confirm: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private fun formatDay(millis: Long): String =
    Validation.formatDate(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate())
