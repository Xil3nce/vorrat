package de.frelina.vorrat.ui.recipes

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import de.frelina.vorrat.data.image.ImageStore
import de.frelina.vorrat.ui.components.ProductImage
import java.io.File
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import de.frelina.vorrat.R
import de.frelina.vorrat.domain.Recipe
import de.frelina.vorrat.domain.RecipeMatcher
import java.time.LocalDate

/**
 * "läuft morgen ab" u. Ä.; null ohne Ablaufdatum bzw. – mit [onlySoon] – wenn es nicht innerhalb von
 * [RecipeMatcher.RESCUE_DAYS] Tagen abläuft.
 */
@Composable
fun expiryPhrase(expiry: LocalDate?, today: LocalDate, onlySoon: Boolean = false): String? {
    if (expiry == null) return null
    val days = RecipeMatcher.daysUntil(expiry, today)
    if (onlySoon && days > RecipeMatcher.RESCUE_DAYS) return null
    return when {
        days < 0 -> stringResource(R.string.recipe_expired)
        days == 0L -> stringResource(R.string.recipe_expires_today)
        days == 1L -> stringResource(R.string.recipe_expires_tomorrow)
        else -> pluralStringResource(R.plurals.recipe_expires_in_days, days.toInt(), days.toInt())
    }
}

/** "30 Min. · 2 Portionen · vegetarisch". */
@Composable
fun recipeMeta(recipe: Recipe): String {
    val parts = buildList {
        recipe.minutes?.let { add(stringResource(R.string.recipe_minutes, it)) }
        recipe.servings?.let { add(pluralStringResource(R.plurals.recipe_servings, it, it)) }
        if (recipe.vegetarian) add(stringResource(R.string.recipe_vegetarian))
        if (!recipe.builtin) add(stringResource(R.string.recipe_own))
    }
    return parts.joinToString(" · ")
}

/** Großes Rezeptfoto (Detailansicht); nichts, solange die Datei fehlt (z. B. noch nicht synchronisiert). */
@Composable
fun RecipePhoto(imagePath: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val imagesVersion by ImageStore.changes.collectAsState()
    val file = remember(imagePath, imagesVersion) {
        imagePath?.let { File(File(context.filesDir, ImageStore.DIR_NAME), it) }?.takeIf { it.exists() }
    } ?: return
    AsyncImage(
        model = file,
        contentDescription = stringResource(R.string.recipe_photo),
        contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(20.dp)),
    )
}

/** Foto im Rezept-Editor: aufnehmen, aus der Galerie wählen oder entfernen. */
@Composable
fun RecipePhotoPicker(
    imagePath: String?,
    title: String,
    imageError: Boolean,
    onImagePicked: (Uri) -> Unit,
    onRemoveImage: () -> Unit,
) {
    val context = LocalContext.current
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.let(onImagePicked)
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(onImagePicked)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ProductImage(imagePath, size = 96.dp, name = title.ifBlank { null })
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = {
                val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                cameraUri = uri
                takePicture.launch(uri)
            }) {
                Icon(Icons.Outlined.PhotoCamera, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.image_take_photo))
            }
            OutlinedButton(onClick = {
                pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) {
                Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.image_pick_gallery))
            }
            if (imagePath != null) {
                TextButton(onClick = onRemoveImage) {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.image_remove))
                }
            }
        }
    }
    if (imageError) Text(stringResource(R.string.image_error), color = MaterialTheme.colorScheme.error)
}
