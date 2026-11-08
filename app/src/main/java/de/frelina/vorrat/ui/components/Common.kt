package de.frelina.vorrat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import de.frelina.vorrat.R
import de.frelina.vorrat.data.image.ImageStore
import de.frelina.vorrat.domain.ExpiryStatus
import de.frelina.vorrat.domain.ValidationError
import java.io.File

/** Produktbild aus dem App-internen Speicher oder ein Platzhalter-Icon. */
@Composable
fun ProductImage(imagePath: String?, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Fehlt die Datei noch (Sync lädt sie im Hintergrund), nach jedem abgelegten Bild erneut prüfen.
    val imagesVersion by ImageStore.changes.collectAsState()
    val file = remember(imagePath, imagesVersion) {
        imagePath?.let { File(File(context.filesDir, ImageStore.DIR_NAME), it) }?.takeIf { it.exists() }
    }
    val shape = RoundedCornerShape(12.dp)
    if (file != null) {
        AsyncImage(
            model = file,
            contentDescription = stringResource(R.string.product_image),
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(shape),
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Inventory2,
                contentDescription = stringResource(R.string.no_image),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Farbrollen für Ablauf-Hervorhebung (Dynamic-Color-tauglich, keine festen Hex-Werte). */
data class ExpiryColors(val container: Color, val content: Color)

@Composable
@ReadOnlyComposable
fun expiryColors(status: ExpiryStatus): ExpiryColors? {
    val c = MaterialTheme.colorScheme
    return when (status) {
        ExpiryStatus.EXPIRED -> ExpiryColors(c.errorContainer, c.onErrorContainer)
        ExpiryStatus.SOON -> ExpiryColors(c.tertiaryContainer, c.onTertiaryContainer)
        else -> null
    }
}

@Composable
@ReadOnlyComposable
fun validationMessage(error: ValidationError): String = stringResource(
    when (error) {
        ValidationError.REQUIRED -> R.string.error_required
        ValidationError.QUANTITY_MIN_ONE -> R.string.error_quantity_min
        ValidationError.WHOLE_NUMBER -> R.string.error_whole_number
        ValidationError.INVALID_DATE -> R.string.error_invalid_date
        ValidationError.POSITIVE_NUMBER -> R.string.error_positive_number
    },
)

@Composable
fun ErrorSupportingText(error: ValidationError?) {
    if (error != null) Text(validationMessage(error), color = MaterialTheme.colorScheme.error)
}
