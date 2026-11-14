package de.frelina.vorrat.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.atomic.AtomicReference

/**
 * NFC-Lesemodus für die ganze App, solange `MainActivity` im Vordergrund ist.
 *
 * Ist er nur während des NFC-Dialogs aktiv, übernimmt Android den Chip, sobald der Dialog schließt und der Chip
 * noch am Handy liegt (Systemfenster "Tag erkannt" überlagert die App). Deshalb bleibt er durchgehend an:
 * Ein offener [NfcDialog] bekommt den Chip über [attach]; sonst geht er an den Fallback der Activity
 * (Vorrat-Chip → Eintrag öffnen, fremde Chips werden ignoriert).
 */
object NfcReader {
    private const val TAG = "NfcReader"

    private val handler = AtomicReference<((Tag) -> Unit)?>(null)

    /** true, solange ein NFC-Dialog auf einen Chip wartet (Scanner pausiert dann die Kamera). */
    var dialogOpen by mutableStateOf(false)
        private set

    fun attach(onTag: (Tag) -> Unit) {
        handler.set(onTag)
        dialogOpen = true
    }

    fun detach(onTag: (Tag) -> Unit) {
        if (handler.compareAndSet(onTag, null)) dialogOpen = false
    }

    /** In `onResume` aufrufen. [fallback] läuft (wie die Dialog-Handler) in einem Binder-Thread. */
    fun enable(activity: Activity, fallback: (Tag) -> Unit) {
        val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return
        val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V
        runCatching {
            adapter.enableReaderMode(activity, { tag -> (handler.get() ?: fallback)(tag) }, flags, null)
        }.onFailure { Log.w(TAG, "Lesemodus nicht aktiviert", it) }
    }

    /** In `onPause` aufrufen. */
    fun disable(activity: Activity) {
        val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return
        runCatching { adapter.disableReaderMode(activity) }
    }
}
