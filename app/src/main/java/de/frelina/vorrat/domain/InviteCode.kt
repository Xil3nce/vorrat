package de.frelina.vorrat.domain

import java.security.SecureRandom
import java.util.Locale

/**
 * Einladungscode für den gemeinsamen Haushalt: 10 Zeichen aus einem Alphabet ohne leicht verwechselbare
 * Zeichen (0/O, 1/I/L). Anzeige als `XXXXX-XXXXX`; die Eingabe ist tolerant (Groß-/Kleinschreibung,
 * Bindestriche und Leerzeichen sind egal). 31^10 ≈ 8·10^14 Möglichkeiten.
 */
object InviteCode {
    const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
    const val LENGTH = 10

    private val random = SecureRandom()

    fun generate(rnd: java.util.Random = random): String =
        buildString(LENGTH) { repeat(LENGTH) { append(ALPHABET[rnd.nextInt(ALPHABET.length)]) } }

    /** Normalisierte Form (10 Zeichen, Großbuchstaben) oder null, wenn die Eingabe kein gültiger Code ist. */
    fun normalize(input: String): String? {
        val cleaned = input.filterNot { it.isWhitespace() || it == '-' || it == '–' }.uppercase(Locale.ROOT)
        return cleaned.takeIf { it.length == LENGTH && it.all { c -> c in ALPHABET } }
    }

    /** Anzeigeform `XXXXX-XXXXX` (ungültige Eingaben unverändert). */
    fun format(code: String): String {
        val n = normalize(code) ?: return code
        return n.substring(0, LENGTH / 2) + "-" + n.substring(LENGTH / 2)
    }
}
