package de.frelina.vorrat.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Ausschnitt aus GET /repos/{owner}/{repo}/releases/latest (GitHub REST API). */
@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
data class GitHubAsset(
    val name: String? = null,
    val size: Long = 0,
    @SerialName("browser_download_url") val downloadUrl: String? = null,
)

/** Installierbares Update: Version ohne "v", direkter APK-Link, erwartete Größe (0 = unbekannt). */
data class AppRelease(val version: String, val apkUrl: String, val apkSize: Long, val notes: String)

object AppUpdate {

    /** "v1.2.3" oder "1.2" → [1, 2, 3] bzw. [1, 2, 0]; null bei allem anderen. */
    fun parseVersion(text: String?): List<Int>? {
        val match = Regex("""^v?(\d+)(?:\.(\d+))?(?:\.(\d+))?$""").matchEntire(text?.trim().orEmpty()) ?: return null
        return (1..3).map { match.groupValues[it].ifEmpty { "0" }.toInt() }
    }

    fun isNewer(latest: String, current: String): Boolean {
        val a = parseVersion(latest) ?: return false
        val b = parseVersion(current) ?: return false
        for (i in 0..2) if (a[i] != b[i]) return a[i] > b[i]
        return false
    }

    /** Entwürfe, Vorabversionen und Releases ohne APK zählen nicht. */
    fun pick(release: GitHubRelease?): AppRelease? {
        if (release == null || release.draft || release.prerelease || parseVersion(release.tagName) == null) return null
        val apk = release.assets.firstOrNull {
            it.name?.endsWith(".apk", ignoreCase = true) == true && it.downloadUrl?.startsWith("https://") == true
        } ?: return null
        return AppRelease(
            version = release.tagName!!.trim().removePrefix("v"),
            apkUrl = apk.downloadUrl!!,
            apkSize = apk.size,
            notes = release.body.orEmpty().trim(),
        )
    }
}
