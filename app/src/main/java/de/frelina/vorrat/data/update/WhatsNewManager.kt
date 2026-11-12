package de.frelina.vorrat.data.update

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import de.frelina.vorrat.BuildConfig
import de.frelina.vorrat.di.ApplicationScope
import de.frelina.vorrat.domain.AppUpdate
import de.frelina.vorrat.domain.GitHubRelease
import de.frelina.vorrat.domain.ReleaseNotes
import de.frelina.vorrat.domain.VersionNotes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Was ist neu": Nach einem Update (installierte Version neuer als die zuletzt gesehene) holt die App einmalig die
 * Release-Notizen der übersprungenen Versionen von GitHub und zeigt sie. Ohne Netz wird es beim nächsten Start
 * erneut versucht; nach einer Neuinstallation gibt es nichts zu zeigen.
 */
@Singleton
class WhatsNewManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    private val json: Json,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)

    private val _notes = MutableStateFlow<List<VersionNotes>>(emptyList())

    /** Anzuzeigende Notizen (neueste zuerst); leer = kein Dialog. */
    val notes: StateFlow<List<VersionNotes>> = _notes.asStateFlow()

    private var started = false

    /** Beim App-Start: prüfen, ob seit dem letzten Start ein Update installiert wurde. */
    fun checkAfterUpdate() {
        if (started) return
        started = true
        val current = BuildConfig.VERSION_NAME
        val lastSeen = prefs.getString(KEY_LAST_SEEN, null)
        when {
            lastSeen == current -> return
            lastSeen == null && isFreshInstall() -> {
                markSeen()
                return
            }
            lastSeen != null && !AppUpdate.isNewer(current, lastSeen) -> {
                // Ältere Version installiert (z. B. Test-Build) – nichts zeigen.
                markSeen()
                return
            }
        }
        scope.launch {
            try {
                val notes = ReleaseNotes.between(fetchReleases(), lastSeen, current)
                if (notes.isEmpty()) markSeen() else _notes.value = notes
            } catch (e: IOException) {
                Log.i(TAG, "Release-Notizen nicht erreichbar, nächster Versuch beim nächsten Start", e)
            } catch (e: RuntimeException) {
                Log.w(TAG, "Release-Notizen unbrauchbar", e)
                markSeen()
            }
        }
    }

    /** Dialog geschlossen: diese Version gilt als gesehen. */
    fun dismiss() {
        markSeen()
        _notes.value = emptyList()
    }

    private fun markSeen() = prefs.edit { putString(KEY_LAST_SEEN, BuildConfig.VERSION_NAME) }

    /** Erste Installation (nie aktualisiert) → keine "Was ist neu"-Übersicht. */
    private fun isFreshInstall(): Boolean = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.firstInstallTime == info.lastUpdateTime
    }.getOrDefault(false)

    private suspend fun fetchReleases(): List<GitHubRelease> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases?per_page=20")
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("GitHub ${response.code}")
            json.decodeFromString<List<GitHubRelease>>(response.body.string())
        }
    }

    private companion object {
        const val TAG = "WhatsNewManager"
        const val KEY_LAST_SEEN = "lastSeenVersion"
    }
}
