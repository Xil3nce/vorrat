package de.frelina.vorrat.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import de.frelina.vorrat.BuildConfig
import de.frelina.vorrat.R
import de.frelina.vorrat.data.update.UpdateManager
import de.frelina.vorrat.data.update.WhatsNewManager
import de.frelina.vorrat.domain.NoteLine
import de.frelina.vorrat.domain.VersionNotes
import de.frelina.vorrat.ui.components.AmountBadge
import de.frelina.vorrat.ui.components.SectionLabel
import de.frelina.vorrat.ui.theme.VorratTheme
import javax.inject.Inject

@HiltViewModel
class WhatsNewViewModel @Inject constructor(
    private val manager: WhatsNewManager,
    updates: UpdateManager,
) : ViewModel() {
    val notes = manager.notes
    val updateState = updates.state

    init {
        manager.checkAfterUpdate()
    }

    fun dismiss() = manager.dismiss()
}

/**
 * "Neu in Vorrat": einmal nach einem Update die Release-Notizen der neuen Version(en) zeigen.
 * Glas-Karte über dem Inhalt; wartet, solange der Update-Dialog offen ist.
 */
@Composable
fun WhatsNewDialog(viewModel: WhatsNewViewModel = hiltViewModel()) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val update by viewModel.updateState.collectAsStateWithLifecycle()
    if (notes.isEmpty() || update.prompt) return
    WhatsNewContent(notes, onDismiss = viewModel::dismiss)
}

@Composable
fun WhatsNewContent(notes: List<VersionNotes>, onDismiss: () -> Unit) {
    val colors = VorratTheme.colors
    val shape = RoundedCornerShape(28.dp)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Dialog-Fenster erben die Inhaltsfarbe nicht → helle Schrift ausdrücklich setzen.
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, colors.glassBorder, shape)
                .padding(24.dp),
        ) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(colors.accent.copy(alpha = 0.16f))
                    .border(1.5.dp, colors.accent.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = colors.accent, modifier = Modifier.size(26.dp))
            }
            SectionLabel(stringResource(R.string.whatsnew_overline), Modifier.padding(top = 16.dp))
            Text(
                stringResource(R.string.whatsnew_title, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 2.dp),
            )
            Column(
                Modifier
                    .padding(top = 16.dp)
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                val showVersions = notes.size > 1
                notes.forEach { version ->
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (showVersions) {
                            AmountBadge(stringResource(R.string.whatsnew_version, version.version), color = colors.accent)
                        }
                        version.items.forEach { NoteRow(it) }
                    }
                }
            }
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
            ) {
                Text(stringResource(R.string.whatsnew_ok), fontWeight = FontWeight.SemiBold)
            }
        }
        }
    }
}

@Composable
private fun NoteRow(line: NoteLine) {
    val colors = VorratTheme.colors
    if (!line.bullet) {
        Text(line.text, style = MaterialTheme.typography.bodyMedium, color = colors.textTertiary)
        return
    }
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 7.dp)
                .size(7.dp)
                .clip(CircleShape)
                .background(colors.accent),
        )
        Text(
            line.text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
