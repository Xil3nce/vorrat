package de.frelina.vorrat.data.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import de.frelina.vorrat.domain.OcrLine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Liest den Text eines Fotos (ML Kit Texterkennung, nur auf dem Gerät – das Bild verlässt das Handy nicht).
 * Auswertung des Textes: [de.frelina.vorrat.domain.RecipeTextParser].
 */
@Singleton
class RecipeTextRecognizer @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    /** Zeilen in Leserichtung mit Lage im Bild; leer ohne erkennbaren Text, null wenn das Bild nicht lesbar ist. */
    suspend fun read(uri: Uri): List<OcrLine>? = withContext(Dispatchers.Default) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            // fromFilePath berücksichtigt die Drehung aus den Bilddaten (EXIF).
            val result = recognizer.process(InputImage.fromFilePath(context, uri)).await()
            result.textBlocks.flatMapIndexed { block, textBlock ->
                textBlock.lines.map { line ->
                    val box = line.boundingBox
                    OcrLine(line.text, box?.left ?: 0, box?.top ?: 0, box?.right ?: 0, box?.bottom ?: 0, block)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } finally {
            recognizer.close()
        }
    }

    /** Temporäres Kamerafoto nach dem Lesen wieder löschen. */
    suspend fun discard(uri: Uri) = withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.delete(uri, null, null) }
        Unit
    }
}
