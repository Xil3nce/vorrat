package de.frelina.vorrat.nfc

import android.content.Intent
import android.nfc.NfcAdapter
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.os.HandlerCompat
import android.os.Looper
import de.frelina.vorrat.R
import de.frelina.vorrat.domain.NfcLink

/** Was der Dialog mit dem nächsten Chip tun soll. */
sealed interface NfcMode {
    data object Read : NfcMode

    /** Chip mit der URI des Eintrags [entryId] beschreiben. */
    data class Write(val entryId: String) : NfcMode
}

/**
 * "Chip ans Handy halten": aktiviert den NFC-Lesemodus, solange der Dialog offen ist.
 * Lesen → [onRead]; Beschreiben → [onWritten] mit der Chip-Kennung. Fehler werden im Dialog angezeigt
 * (der Dialog bleibt offen, ein neuer Versuch ist sofort möglich).
 */
@Composable
fun NfcDialog(
    mode: NfcMode,
    onDismiss: () -> Unit,
    onRead: (NfcTagRead) -> Unit = {},
    onWritten: (tagId: String) -> Unit = {},
) {
    val activity = LocalActivity.current
    val adapter = remember(activity) { activity?.let { NfcAdapter.getDefaultAdapter(it) } }
    var enabled by remember { mutableStateOf(adapter?.isEnabled == true) }
    var error by remember { mutableIntStateOf(0) }
    val currentOnRead by rememberUpdatedState(onRead)
    val currentOnWritten by rememberUpdatedState(onWritten)
    val currentMode by rememberUpdatedState(mode)

    DisposableEffect(activity, adapter, enabled) {
        if (activity == null || adapter == null || !enabled) return@DisposableEffect onDispose { }
        val main = HandlerCompat.createAsync(Looper.getMainLooper())
        val callback = NfcAdapter.ReaderCallback { tag ->
            // Läuft in einem Binder-Thread → Tag-Zugriff hier, Ergebnis im UI-Thread melden.
            when (val m = currentMode) {
                NfcMode.Read -> {
                    val read = NfcTags.read(tag)
                    main.post {
                        if (read.tagId.isEmpty() && read.entryId == null) error = R.string.nfc_error_read
                        else currentOnRead(read)
                    }
                }
                is NfcMode.Write -> {
                    val result = NfcTags.write(tag, m.entryId, activity.packageName)
                    val tagId = NfcLink.tagIdHex(tag.id)
                    main.post {
                        when {
                            result == NfcWriteResult.OK && tagId != null -> currentOnWritten(tagId)
                            result == NfcWriteResult.READ_ONLY -> error = R.string.nfc_error_read_only
                            result == NfcWriteResult.TOO_SMALL -> error = R.string.nfc_error_too_small
                            result == NfcWriteResult.UNSUPPORTED -> error = R.string.nfc_error_unsupported
                            else -> error = R.string.nfc_error_write
                        }
                    }
                }
            }
        }
        val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V
        adapter.enableReaderMode(activity, callback, flags, null)
        onDispose { runCatching { adapter.disableReaderMode(activity) } }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Nfc, contentDescription = null, modifier = Modifier.size(40.dp)) },
        title = {
            Text(stringResource(if (mode is NfcMode.Write) R.string.nfc_write_title else R.string.nfc_read_title))
        },
        text = {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val hint = when {
                    adapter == null -> R.string.nfc_not_available
                    !enabled -> R.string.nfc_disabled
                    mode is NfcMode.Write -> R.string.nfc_write_hint
                    else -> R.string.nfc_read_hint
                }
                Text(stringResource(hint), textAlign = TextAlign.Center)
                if (error != 0) {
                    Text(stringResource(error), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                }
            }
        },
        confirmButton = {
            if (adapter != null && !enabled && activity != null) {
                TextButton(onClick = {
                    activity.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                }) { Text(stringResource(R.string.nfc_open_settings)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )

    // Nach Rückkehr aus den NFC-Einstellungen neu prüfen.
    androidx.lifecycle.compose.LifecycleResumeEffect(adapter) {
        enabled = adapter?.isEnabled == true
        onPauseOrDispose { }
    }
}
