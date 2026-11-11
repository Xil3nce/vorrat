package de.frelina.vorrat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.frelina.vorrat.ui.theme.VorratTheme

/** Snackbar-Inhalt samt [tone], damit die Pille Farbe und Symbol wählen kann. */
data class ToneSnackbarVisuals(
    override val message: String,
    override val actionLabel: String?,
    override val duration: SnackbarDuration,
    val tone: MessageTone,
    override val withDismissAction: Boolean = false,
) : SnackbarVisuals

/**
 * Meldung als Glas-Pille (oben unter der Kopfzeile): Glasfläche, Statuskreis (grün ✓, blau i, rot ×),
 * Text und optional eine Aktion wie "Rückgängig" in Akzentgrün.
 */
@Composable
fun MessagePill(data: SnackbarData, modifier: Modifier = Modifier) {
    val colors = VorratTheme.colors
    val tone = (data.visuals as? ToneSnackbarVisuals)?.tone ?: MessageTone.SUCCESS
    val (statusColor, icon) = when (tone) {
        MessageTone.SUCCESS -> colors.accent to Icons.Outlined.Check
        MessageTone.INFO -> colors.info to Icons.Outlined.Info
        MessageTone.ERROR -> colors.danger to Icons.Outlined.Close
    }
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(colors.toast)
            .border(1.dp, colors.glassBorder, shape)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(start = 10.dp, end = if (data.visuals.actionLabel != null) 4.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(statusColor.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = statusColor, modifier = Modifier.size(16.dp))
        }
        Text(
            data.visuals.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 10.dp),
        )
        data.visuals.actionLabel?.let { label ->
            TextButton(onClick = data::performAction) {
                Text(label, color = colors.accent, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
