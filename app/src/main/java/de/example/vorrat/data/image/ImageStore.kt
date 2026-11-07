package de.example.vorrat.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Produktbilder im App-internen Speicher (filesDir/images). In der Datenbank wird nur der
 * Dateiname gespeichert – dadurch bleiben Bilder offline verfügbar und Backups portabel.
 */
@Singleton
class ImageStore(private val dir: File) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(File(context.filesDir, DIR_NAME))

    init {
        dir.mkdirs()
    }

    val directory: File get() = dir

    fun file(name: String): File = File(dir, name)

    fun fileOrNull(name: String?): File? = name?.let { file(it) }?.takeIf { it.exists() }

    suspend fun saveBytes(bytes: ByteArray, extension: String = "jpg"): String = withContext(Dispatchers.IO) {
        val name = newName(extension)
        FileOutputStream(file(name)).use { it.write(bytes) }
        name
    }

    suspend fun copy(name: String): String? = withContext(Dispatchers.IO) {
        val source = fileOrNull(name) ?: return@withContext null
        val target = newName(source.extension.ifEmpty { "jpg" })
        source.copyTo(file(target))
        target
    }

    /** Bild von Kamera/Galerie verkleinert (max. [MAX_EDGE] px) als JPEG übernehmen. */
    suspend fun importFromUri(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            val longest = maxOf(w, h)
            if (longest > MAX_EDGE) {
                val scale = MAX_EDGE.toFloat() / longest
                decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val name = newName("jpg")
        FileOutputStream(file(name)).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        bitmap.recycle()
        name
    }

    suspend fun delete(name: String?) = withContext(Dispatchers.IO) {
        if (name != null) file(name).delete()
    }

    /** Löscht alle Bilddateien, die nicht mehr referenziert sind. */
    suspend fun deleteUnreferenced(referenced: Set<String>) = withContext(Dispatchers.IO) {
        dir.listFiles()?.forEach { f -> if (f.isFile && f.name !in referenced) f.delete() }
    }

    private fun newName(extension: String) = "${UUID.randomUUID()}.$extension"

    companion object {
        const val DIR_NAME = "images"
        private const val MAX_EDGE = 1280
    }
}
