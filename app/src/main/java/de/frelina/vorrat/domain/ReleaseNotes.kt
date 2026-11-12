package de.frelina.vorrat.domain

/** Notizen zu einer Version (aus dem GitHub-Release), für "Was ist neu". */
data class VersionNotes(val version: String, val items: List<NoteLine>)

/** Eine Zeile der Notizen: Aufzählungspunkt oder freier Absatz. */
data class NoteLine(val text: String, val bullet: Boolean)

object ReleaseNotes {

    /**
     * Notizen aller Releases, die seit [lastSeen] (exklusiv) bis [current] (inklusive) erschienen sind, neueste zuerst.
     * Ohne [lastSeen] nur die Notizen von [current]. Entwürfe, Vorabversionen und Releases ohne Text fallen weg.
     */
    fun between(releases: List<GitHubRelease>, lastSeen: String?, current: String, max: Int = 5): List<VersionNotes> =
        releases.asSequence()
            .filter { !it.draft && !it.prerelease }
            .mapNotNull { r ->
                val version = r.tagName?.trim()?.removePrefix("v") ?: return@mapNotNull null
                if (AppUpdate.parseVersion(version) == null) return@mapNotNull null
                val inRange = if (lastSeen == null) {
                    AppUpdate.parseVersion(version) == AppUpdate.parseVersion(current)
                } else {
                    AppUpdate.isNewer(version, lastSeen) && !AppUpdate.isNewer(version, current)
                }
                if (!inRange) return@mapNotNull null
                val items = parse(r.body)
                if (items.isEmpty()) null else VersionNotes(version, items)
            }
            .distinctBy { it.version }
            .sortedWith { a, b -> if (AppUpdate.isNewer(a.version, b.version)) -1 else if (AppUpdate.isNewer(b.version, a.version)) 1 else 0 }
            .take(max)
            .toList()

    /**
     * Release-Text → Zeilen. "- ", "* ", "• " und "1. " werden Aufzählungspunkte; Markdown-Überschriften,
     * Fettdruck und Code-Markierungen werden entfernt. Mehrere Sätze in einem Absatz ohne Aufzählung bleiben ein Absatz.
     */
    fun parse(body: String?): List<NoteLine> =
        body.orEmpty()
            .replace("\r\n", "\n")
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.matches(Regex("^[-*_]{3,}$")) }
            .map { line ->
                val bullet = BULLET.find(line)
                val text = (if (bullet != null) line.substring(bullet.range.last + 1) else line)
                    .removePrefix("#").trimStart('#').trim()
                    .replace("**", "")
                    .replace("`", "")
                    .trim()
                NoteLine(text, bullet = bullet != null)
            }
            .filter { it.text.isNotEmpty() }

    private val BULLET = Regex("""^(?:[-*•]|\d+[.)])\s+""")
}
