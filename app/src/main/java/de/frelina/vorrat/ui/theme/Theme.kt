package de.frelina.vorrat.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Feste dunkle Palette ("Glas"-Look). Einzige Stelle mit Hex-Werten – Screens nutzen
 * Material-Farbrollen oder [VorratTheme.colors].
 */
private object Palette {
    val Ink = Color(0xFF0B1B2E)
    val Mid = Color(0xFF0D2536)
    val Teal = Color(0xFF0A2E33)
    val Glow = Color(0xFF14445A)
    val Text = Color(0xFFF2F6F8)
    val TextSecondary = Color(0xFFA9B8C3)
    val TextTertiary = Color(0xFFC9D5DD)
    val Accent = Color(0xFF8BE38A)
    val Warning = Color(0xFFF5B94C)
    val Danger = Color(0xFFFF8A8A)
    val Info = Color(0xFF7FC4FF)
    val Dialog = Color(0xFF17304A)
    val Menu = Color(0xFF132A3C)
    val Sheet = Color(0xFF10263A)
}

/** Zusatzfarben, die Material 3 nicht als Rolle kennt. */
@Immutable
data class VorratColors(
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val onAccent: Color,
    val warning: Color,
    val danger: Color,
    val info: Color,
    /** Fläche der Glas-Karten. */
    val glass: Color,
    /** Etwas kräftigere Glasfläche (Eingabefelder, Kopf-Buttons). */
    val glassStrong: Color,
    val glassBorder: Color,
    /** Hintergrund-Spur von Ringen und Balken. */
    val track: Color,
    /** Schwebende Navigationsleiste. */
    val bar: Color,
    /** Meldungen (Glas-Pille): fast deckend, damit Text darunter nicht durchscheint. */
    val toast: Color,
    /** Pastellflächen für Produkt-Kacheln und Orte (ohne Bild). */
    val pastels: List<Color>,
)

private val Colors = VorratColors(
    textSecondary = Palette.TextSecondary,
    textTertiary = Palette.TextTertiary,
    accent = Palette.Accent,
    onAccent = Palette.Ink,
    warning = Palette.Warning,
    danger = Palette.Danger,
    info = Palette.Info,
    glass = Color.White.copy(alpha = 0.06f),
    glassStrong = Color.White.copy(alpha = 0.09f),
    glassBorder = Color.White.copy(alpha = 0.12f),
    track = Color.White.copy(alpha = 0.12f),
    bar = Color(0xE6102233),
    toast = Color(0xF51C3446),
    pastels = listOf(
        Color(0xFFF6E2B3), Color(0xFFCDE7FF), Color(0xFFC8B6FF), Color(0xFF9EE6E0),
        Color(0xFFFFC2B8), Color(0xFFD9F2B4), Color(0xFFF7C8E0),
    ),
)

private val LocalVorratColors = staticCompositionLocalOf { Colors }

private val Scheme = darkColorScheme(
    primary = Palette.Accent,
    onPrimary = Palette.Ink,
    primaryContainer = Palette.Accent,
    onPrimaryContainer = Palette.Ink,
    inversePrimary = Color(0xFF2E7D4F),
    secondary = Palette.TextTertiary,
    onSecondary = Palette.Ink,
    // Aktive Filter-Pillen: weiß gefüllt, dunkle Schrift.
    secondaryContainer = Palette.Text,
    onSecondaryContainer = Palette.Ink,
    tertiary = Palette.Warning,
    onTertiary = Palette.Ink,
    tertiaryContainer = Color(0xFF4A3A1E),
    onTertiaryContainer = Color(0xFFFFD58A),
    error = Palette.Danger,
    onError = Palette.Ink,
    errorContainer = Color(0xFF4D2329),
    onErrorContainer = Color(0xFFFFC2C2),
    // Hintergrund kommt aus dem Verlauf in [VorratTheme]; Flächen sind darum durchsichtig.
    background = Color.Transparent,
    onBackground = Palette.Text,
    surface = Color.Transparent,
    onSurface = Palette.Text,
    surfaceVariant = Color.White.copy(alpha = 0.08f),
    onSurfaceVariant = Palette.TextSecondary,
    surfaceTint = Color.Transparent,
    inverseSurface = Palette.Text,
    inverseOnSurface = Palette.Ink,
    outline = Color.White.copy(alpha = 0.22f),
    outlineVariant = Color.White.copy(alpha = 0.12f),
    scrim = Color.Black,
    // Dialoge, Menüs und Sheets liegen über dem Inhalt und brauchen deckende Flächen.
    surfaceBright = Palette.Dialog,
    surfaceDim = Palette.Ink,
    surfaceContainerLowest = Palette.Ink,
    surfaceContainerLow = Palette.Sheet,
    surfaceContainer = Palette.Menu,
    surfaceContainerHigh = Palette.Dialog,
    surfaceContainerHighest = Color.White.copy(alpha = 0.06f),
)

private val VorratShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    // Chips als Pillen.
    small = RoundedCornerShape(percent = 50),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Nur dunkel: feste Palette, Glas-Flächen über einem Verlauf von Nachtblau nach Petrol. */
@Composable
fun VorratTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalVorratColors provides Colors) {
        MaterialTheme(colorScheme = Scheme, shapes = VorratShapes) {
            Box(Modifier.fillMaxSize().vorratBackground()) { content() }
        }
    }
}

object VorratTheme {
    val colors: VorratColors
        @Composable @ReadOnlyComposable get() = LocalVorratColors.current
}

/** Verlauf (170°) mit Schimmer oben rechts. */
fun Modifier.vorratBackground(): Modifier = background(Palette.Ink).drawBehind {
    drawRect(
        Brush.linearGradient(
            0f to Palette.Ink, 0.6f to Palette.Mid, 1f to Palette.Teal,
            start = Offset(size.width * 0.4f, 0f),
            end = Offset(size.width * 0.6f, size.height),
        ),
    )
    drawRect(
        Brush.radialGradient(
            0f to Palette.Glow, 1f to Palette.Glow.copy(alpha = 0f),
            center = Offset(size.width * 0.85f, 0f),
            radius = size.width * 1.1f,
        ),
    )
}
