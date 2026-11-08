package de.frelina.vorrat.nfc

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.util.Log
import de.frelina.vorrat.domain.NfcLink

/** Gelesener Chip: Kennung (Hex) und – falls von Vorrat beschrieben – die Eintrags-ID aus der URI. */
data class NfcTagRead(val tagId: String, val entryId: String?)

enum class NfcWriteResult { OK, READ_ONLY, TOO_SMALL, UNSUPPORTED, FAILED }

/** Lesen und Beschreiben von NFC-Chips (NDEF). Aufrufe mit Tag-Zugriff blockieren – nicht im UI-Thread. */
object NfcTags {
    private const val TAG = "NfcTags"

    /** Chip aus einem NFC-Intent (App über die URI geöffnet) oder null. */
    fun fromIntent(intent: Intent?): NfcTagRead? {
        if (intent == null) return null
        val action = intent.action
        if (action != NfcAdapter.ACTION_NDEF_DISCOVERED && action != NfcAdapter.ACTION_TAG_DISCOVERED &&
            action != NfcAdapter.ACTION_TECH_DISCOVERED
        ) {
            return null
        }
        val tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        val messages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, NdefMessage::class.java)
        val uriEntry = messages?.firstNotNullOfOrNull { entryIdOf(it) }
            ?: NfcLink.entryIdFrom(intent.data?.toString())
        // Intent nur einmal auswerten (z. B. nicht erneut nach Drehen des Bildschirms).
        intent.action = null
        val tagId = NfcLink.tagIdHex(tag?.id) ?: return uriEntry?.let { NfcTagRead("", it) }
        return NfcTagRead(tagId, uriEntry)
    }

    fun read(tag: Tag): NfcTagRead {
        val tagId = NfcLink.tagIdHex(tag.id).orEmpty()
        val message = Ndef.get(tag)?.let { ndef ->
            ndef.cachedNdefMessage ?: runCatching {
                ndef.connect()
                ndef.use { it.ndefMessage }
            }.getOrNull()
        }
        return NfcTagRead(tagId, message?.let(::entryIdOf))
    }

    /** Schreibt die URI des Eintrags (plus App-Verweis, damit Antippen Vorrat öffnet). */
    fun write(tag: Tag, entryId: String, packageName: String): NfcWriteResult {
        val message = NdefMessage(
            arrayOf(
                NdefRecord.createUri(NfcLink.uriFor(entryId)),
                NdefRecord.createApplicationRecord(packageName),
            ),
        )
        return try {
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.connect()
                ndef.use {
                    when {
                        !it.isWritable -> NfcWriteResult.READ_ONLY
                        it.maxSize < message.byteArrayLength -> NfcWriteResult.TOO_SMALL
                        else -> {
                            it.writeNdefMessage(message)
                            NfcWriteResult.OK
                        }
                    }
                }
            } else {
                val formatable = NdefFormatable.get(tag) ?: return NfcWriteResult.UNSUPPORTED
                formatable.connect()
                formatable.use { it.format(message) }
                NfcWriteResult.OK
            }
        } catch (e: Exception) {
            Log.w(TAG, "Chip nicht beschrieben", e)
            NfcWriteResult.FAILED
        }
    }

    private fun entryIdOf(message: NdefMessage): String? =
        message.records.firstNotNullOfOrNull { record -> NfcLink.entryIdFrom(record.toUri()?.toString()) }
}
