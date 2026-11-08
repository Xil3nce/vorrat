package de.frelina.vorrat.domain

/**
 * Inhalt der NFC-Chips an selbst Eingefrorenem: eine URI `vorrat://entry/{eintragId}`.
 * Zusätzlich merkt sich der Eintrag die Kennung (UID) des Chips – sie ist maßgeblich, die URI dient dem
 * Öffnen der App beim Antippen und als Rückfall für Chips, deren Kennung (noch) nicht bekannt ist.
 */
object NfcLink {
    const val SCHEME = "vorrat"
    const val HOST = "entry"

    fun uriFor(entryId: String): String = "$SCHEME://$HOST/$entryId"

    /** Eintrags-ID aus einer Chip-URI oder null, wenn die URI nicht von Vorrat stammt. */
    fun entryIdFrom(uri: String?): String? {
        val prefix = "$SCHEME://$HOST/"
        if (uri == null || !uri.startsWith(prefix, ignoreCase = true)) return null
        return uri.substring(prefix.length).trim('/').takeIf { ID.matches(it) }
    }

    /** Chip-Kennung als Hex-Text (Großbuchstaben, ohne Trennzeichen). */
    fun tagIdHex(bytes: ByteArray?): String? =
        bytes?.takeIf { it.isNotEmpty() }?.joinToString("") { "%02X".format(it) }

    /** Ergebnis einer Chip-Zuordnung: Eintrag laut Kennung, sonst laut URI (falls vorhanden). */
    fun resolve(entryIdsByTag: List<String>, uriEntryId: String?, existingIds: Set<String>): String? =
        entryIdsByTag.firstOrNull() ?: uriEntryId?.takeIf { it in existingIds }

    private val ID = Regex("^[A-Za-z0-9-]{1,64}$")
}
