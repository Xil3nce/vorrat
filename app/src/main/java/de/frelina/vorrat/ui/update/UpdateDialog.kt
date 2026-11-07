package de.frelina.vorrat.ui.update

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.frelina.vorrat.R

/** Dialog "Update verfügbar" mit Download-Fortschritt; liegt app-weit über der Navigation. */
@Composable
fun UpdateDialog(viewModel: UpdateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Zurück aus "Unbekannte Apps installieren": Erlaubnis prüfen und ggf. direkt weitermachen.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.permissionChecked() }

    val release = state.available ?: return
    if (!state.prompt) return
    val busy = state.progress != null || state.installing

    AlertDialog(
        onDismissRequest = { if (!busy) viewModel.dismiss() },
        title = { Text(stringResource(R.string.update_title, release.version)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.update_installed, state.current))
                if (release.notes.isNotBlank()) {
                    Text(
                        release.notes,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState()),
                    )
                }
                when {
                    state.needsPermission -> Text(stringResource(R.string.update_permission_text))
                    state.progress != null -> {
                        Text(stringResource(R.string.update_downloading))
                        val p = state.progress!!
                        if (p >= 0f) LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                        else LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    state.installing -> Text(stringResource(R.string.update_installing))
                    else -> Text(stringResource(R.string.update_data_kept), style = MaterialTheme.typography.bodySmall)
                }
                state.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            when {
                state.needsPermission -> TextButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri()),
                    )
                }) { Text(stringResource(R.string.update_permission_open)) }
                !busy -> TextButton(onClick = viewModel::install) { Text(stringResource(R.string.update_install)) }
            }
        },
        dismissButton = {
            if (!busy) TextButton(onClick = viewModel::dismiss) { Text(stringResource(R.string.update_later)) }
        },
    )
}
