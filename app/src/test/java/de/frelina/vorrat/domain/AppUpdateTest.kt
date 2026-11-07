package de.frelina.vorrat.domain

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

    @Test fun `Versionen werden gelesen`() {
        assertEquals(listOf(1, 2, 3), AppUpdate.parseVersion("v1.2.3"))
        assertEquals(listOf(1, 2, 0), AppUpdate.parseVersion("1.2"))
        assertEquals(listOf(2, 0, 0), AppUpdate.parseVersion(" v2 "))
        assertNull(AppUpdate.parseVersion("1.2.3-beta"))
        assertNull(AppUpdate.parseVersion(null))
    }

    @Test fun `neuer wird zahlenweise verglichen`() {
        assertTrue(AppUpdate.isNewer("1.10.0", "1.9.9"))
        assertTrue(AppUpdate.isNewer("v2.0", "1.99.99"))
        assertFalse(AppUpdate.isNewer("1.0.0", "1.0.0"))
        assertFalse(AppUpdate.isNewer("1.0.0", "1.0.1"))
        assertFalse(AppUpdate.isNewer("kaputt", "1.0.0"))
    }

    @Test fun `GitHub-Antwort mit APK wird zum Update`() {
        val release = json.decodeFromString<GitHubRelease>(
            """
            {"tag_name":"v1.1.0","html_url":"https://github.com/x/y/releases/tag/v1.1.0","body":" Neu: Updates \n",
             "draft":false,"prerelease":false,"id":5,
             "assets":[{"name":"notes.txt","size":3,"browser_download_url":"https://example.org/notes.txt"},
                       {"name":"vorrat-1.1.0.apk","size":1234,"browser_download_url":"https://example.org/vorrat-1.1.0.apk"}]}
            """.trimIndent(),
        )
        assertEquals(AppRelease("1.1.0", "https://example.org/vorrat-1.1.0.apk", 1234, "Neu: Updates"), AppUpdate.pick(release))
    }

    @Test fun `Entwurf, Vorabversion, fehlende APK und http zaehlen nicht`() {
        val apk = listOf(GitHubAsset("a.apk", 1, "https://example.org/a.apk"))
        assertNull(AppUpdate.pick(GitHubRelease("v1.1.0", draft = true, assets = apk)))
        assertNull(AppUpdate.pick(GitHubRelease("v1.1.0", prerelease = true, assets = apk)))
        assertNull(AppUpdate.pick(GitHubRelease("v1.1.0")))
        assertNull(AppUpdate.pick(GitHubRelease("v1.1.0", assets = listOf(GitHubAsset("a.apk", 1, "http://example.org/a.apk")))))
        assertNull(AppUpdate.pick(GitHubRelease("latest", assets = apk)))
        assertNull(AppUpdate.pick(null))
    }
}
