package de.frelina.vorrat.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
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

    /** Synchronisiertes Bild unter seinem festen Namen ablegen (Name muss ein einfacher Dateiname sein). */
    suspend fun saveAs(name: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        require(SAFE_NAME.matches(name)) { "ungültiger Dateiname" }
        val tmp = file("$name.part")
        FileOutputStream(tmp).use { it.write(bytes) }
        if (!tmp.renameTo(file(name))) {
            tmp.delete()
            throw java.io.IOException("Umbenennen fehlgeschlagen")
        }
    }

    /**
     * JPEG für den Sync: längste Kante höchstens [SYNC_MAX_EDGE] px, Qualität schrittweise reduziert, bis
     * höchstens [SYNC_TARGET_BYTES] erreicht sind (Firestore-Dokumente dürfen max. 1 MiB groß sein,
     * Base64 vergrößert um 4/3). null, wenn die Datei fehlt oder nicht lesbar ist.
     */
    suspend fun encodeForSync(name: String): ByteArray? = withContext(Dispatchers.IO) {
        val f = fileOrNull(name) ?: return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SYNC_MAX_EDGE) sample *= 2
        val decoded = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return@withContext null
        val longest = maxOf(decoded.width, decoded.height)
        val bitmap = if (longest > SYNC_MAX_EDGE) {
            val scale = SYNC_MAX_EDGE.toFloat() / longest
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1),
                true,
            ).also { if (it !== decoded) decoded.recycle() }
        } else {
            decoded
        }
        var quality = 85
        var bytes: ByteArray
        do {
            bytes = ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                out.toByteArray()
            }
            quality -= 10
        } while (bytes.size > SYNC_TARGET_BYTES && quality >= 25)
        bitmap.recycle()
        bytes.takeIf { it.size <= SYNC_HARD_LIMIT_BYTES }
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
        const val SYNC_MAX_EDGE = 1024
        const val SYNC_TARGET_BYTES = 200 * 1024

        /** Base64 (×4/3) muss sicher unter 1 MiB pro Dokument bleiben. */
        const val SYNC_HARD_LIMIT_BYTES = 700 * 1024
        private val SAFE_NAME = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")
    }
}
