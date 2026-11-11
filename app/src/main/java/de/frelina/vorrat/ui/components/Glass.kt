package de.frelina.vorrat.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.frelina.vorrat.domain.ExpiryStatus
import de.frelina.vorrat.ui.theme.VorratTheme

/** Rand der Glas-Flächen. */
@Composable
fun glassBorder(): BorderStroke = BorderStroke(1.dp, VorratTheme.colors.glassBorder)

/**
 * Glas-Karte: weiße 6-%-Fläche, feiner heller Rand. Ersetzt `Card` überall in der App;
 * eigene [colors] (z. B. Ablauf-Hervorhebung) bleiben möglich.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.medium,
    colors: CardColors = CardDefaults.cardColors(),
    border: BorderStroke? = glassBorder(),
    content: @Composable ColumnScope.() -> Unit,
) {
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = shape, colors = colors, border = border, content = content)
    } else {
        Card(modifier = modifier, shape = shape, colors = colors, border = border, content = content)
    }
}

/** Runder Glas-Knopf (Kopfzeile: Zurück, Verlauf, Rezepte …). */
@Composable
fun GlassIconButton(icon: ImageVector, contentDescription: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(44.dp)
            .border(1.dp, VorratTheme.colors.glassBorder, CircleShape),
        colors = IconButtonDefaults.iconButtonColors(containerColor = VorratTheme.colors.glass),
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(20.dp))
    }
}

/** Fortschrittsring; [progress] 0..1, optional mit Text in der Mitte. */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    stroke: Dp = 4.dp,
    color: Color = VorratTheme.colors.accent,
    center: (@Composable () -> Unit)? = null,
) {
    val track = VorratTheme.colors.track
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = stroke.toPx()
            val inset = w / 2
            val arcSize = Size(this.size.width - w, this.size.height - w)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w))
            val sweep = 360f * progress.coerceIn(0f, 1f)
            if (sweep > 0f) {
                drawArc(color, -90f, sweep, false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
            }
        }
        center?.invoke()
    }
}

/** Mehrfarbiger Übersichtsring: Anteile in Reihenfolge, Rest = Spur. */
@Composable
fun SegmentRing(
    segments: List<Pair<Float, Color>>,
    modifier: Modifier = Modifier,
    size: Dp = 112.dp,
    stroke: Dp = 12.dp,
    center: (@Composable () -> Unit)? = null,
) {
    val track = VorratTheme.colors.track
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = stroke.toPx()
            val inset = w / 2
            val arcSize = Size(this.size.width - w, this.size.height - w)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w))
            var start = -90f
            segments.forEach { (fraction, color) ->
                val sweep = 360f * fraction.coerceIn(0f, 1f)
                if (sweep > 0.5f) {
                    // Kleine Lücke zwischen den Segmenten.
                    drawArc(color, start + 1.5f, (sweep - 3f).coerceAtLeast(0.5f), false, Offset(inset, inset), arcSize,
                        style = Stroke(w, cap = StrokeCap.Round))
                }
                start += sweep
            }
        }
        center?.invoke()
    }
}

/** Farbe zum Ablaufstatus (Punkt, Balken, Text). */
@Composable
fun statusColor(status: ExpiryStatus): Color = when (status) {
    ExpiryStatus.EXPIRED -> VorratTheme.colors.danger
    ExpiryStatus.SOON -> VorratTheme.colors.warning
    else -> VorratTheme.colors.accent
}

/** Dünner Balken unter Bestandseinträgen; [fraction] = verbleibender Anteil der Haltbarkeit. */
@Composable
fun ExpiryBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(VorratTheme.colors.track),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0.04f, 1f))
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
    }
}

/** Kleiner weißer Kreis mit Anzahl ("4×") am Zeilenende. */
@Composable
fun CountCircle(text: String, modifier: Modifier = Modifier, size: Dp = 34.dp) {
    Box(
        modifier
            .sizeIn(minWidth = size, minHeight = size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.inverseSurface)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            maxLines = 1,
        )
    }
}

/** Plakette rechts an Zeilen (Menge/Packung, "fehlt 2"). */
@Composable
fun AmountBadge(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
    }
}

/** Abschnitts-Überschrift in Großbuchstaben. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = VorratTheme.colors.textSecondary,
    )
}

/** Stabile Pastellfarbe für einen Namen (Kacheln ohne Bild, Orte). */
@Composable
fun pastelFor(key: String): Color {
    val pastels = VorratTheme.colors.pastels
    return pastels[Math.floorMod(key.hashCode(), pastels.size)]
}

/** Kürzel aus den ersten Buchstaben ("Vollmilch 1,5 %" → "VM"). */
fun initialsOf(name: String): String {
    val words = name.split(' ', '-', '_').filter { w -> w.firstOrNull()?.isLetter() == true }
    return when {
        words.size >= 2 -> "${words[0].first()}${words[1].first()}"
        words.size == 1 -> words[0].take(2)
        else -> name.take(2)
    }.uppercase()
}
