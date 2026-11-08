package de.frelina.vorrat.data.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import androidx.annotation.StringRes
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import de.frelina.vorrat.BuildConfig
import de.frelina.vorrat.R
import de.frelina.vorrat.di.ApplicationScope
import de.frelina.vorrat.domain.AppRelease
import de.frelina.vorrat.domain.AppUpdate
import de.frelina.vorrat.domain.GitHubRelease
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class UpdateState(
    val current: String = BuildConfig.VERSION_NAME,
    /** Neueres Release als die installierte Version, sonst null. */
    val available: AppRelease? = null,
    val checking: Boolean = false,
    /** Wurde in diesem Prozess schon erfolgreich nachgesehen? (für "Aktuell"-Anzeige) */
    val checked: Boolean = false,
    /** Update-Dialog sichtbar. */
    val prompt: Boolean = false,
    /** Download läuft: Anteil 0..1, oder -1 wenn die Größe unbekannt ist. */
    val progress: Float? = null,
    /** Systemdialog zur Installation ist offen bzw. Installation läuft. */
    val installing: Boolean = false,
    /** Installation aus unbekannten Quellen ist für Vorrat noch nicht erlaubt. */
    val needsPermission: Boolean = false,
    @StringRes val error: Int? = null,
)

/**
 * Selbst-Update über GitHub-Releases (wie bei Zahnwerk): fragt beim App-Start (höchstens einmal pro Stunde)
 * `releases/latest` von [BuildConfig.UPDATE_REPO] ab, lädt auf Wunsch die APK in den Cache und übergibt
 * sie dem System-Installer. Android installiert nur, wenn Paketname und Signatur zur installierten App
 * passen – eine fremde APK wird also abgelehnt.
 */
@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    client: OkHttpClient,
    private val json: Json,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val apiClient = client
    private val downloadClient = client.newBuilder()
        .callTimeout(0, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow(UpdateState())
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var job: Job? = null

    /** Beim App-Start: still nachsehen (gedrosselt); Dialog nur für noch nicht weggeklickte Versionen. */
    fun checkAutomatically() {
        val last = prefs.getLong(KEY_LAST_CHECK, 0)
        if (System.currentTimeMillis() - last >= CHECK_EVERY_MS) check(userAction = false)
    }

    /** "Nach Updates suchen" in den Einstellungen: immer abfragen, gefundenes Update immer anbieten. */
    fun checkNow() = check(userAction = true)

    private fun check(userAction: Boolean) {
        if (_state.value.checking || job?.isActive == true) return
        job = scope.launch {
            _state.update { it.copy(checking = true, error = null) }
            try {
                val release = fetchLatest()
                prefs.edit { putLong(KEY_LAST_CHECK, System.currentTimeMillis()) }
                val newer = release?.takeIf { AppUpdate.isNewer(it.version, BuildConfig.VERSION_NAME) }
                val dismissed = prefs.getString(KEY_DISMISSED, null)
                _state.update {
                    it.copy(
                        checking = false,
                        checked = true,
                        available = newer,
                        prompt = newer != null && (userAction || newer.version != dismissed),
                    )
                }
            } catch (e: IOException) {
                Log.w(TAG, "Update-Prüfung fehlgeschlagen", e)
                _state.update { it.copy(checking = false, error = if (userAction) R.string.update_error_check else null) }
            } catch (e: RuntimeException) {
                Log.w(TAG, "Update-Antwort unbrauchbar", e)
                _state.update { it.copy(checking = false, error = if (userAction) R.string.update_error_check else null) }
            }
        }
    }

    private suspend fun fetchLatest(): AppRelease? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        apiClient.newCall(request).execute().use { response ->
            when {
                response.code == 404 -> null // noch kein Release veröffentlicht
                !response.isSuccessful -> throw IOException("GitHub ${response.code}")
                else -> AppUpdate.pick(json.decodeFromString<GitHubRelease>(response.body.string()))
            }
        }
    }

    fun showPrompt() = _state.update { if (it.available != null) it.copy(prompt = true, error = null) else it }

    /** "Später": Dialog für diese Version nicht mehr automatisch zeigen (Einstellungen zeigen sie weiter). */
    fun dismiss() {
        _state.value.available?.let { prefs.edit { putString(KEY_DISMISSED, it.version) } }
        _state.update { it.copy(prompt = false, error = null, needsPermission = false) }
    }

    /** Herunterladen und Installation starten. Ohne Erlaubnis für unbekannte Quellen erst dorthin leiten. */
    fun install() {
        val release = _state.value.available ?: return
        if (job?.isActive == true) return
        if (!context.packageManager.canRequestPackageInstalls()) {
            _state.update { it.copy(needsPermission = true, error = null) }
            return
        }
        job = scope.launch {
            _state.update { it.copy(needsPermission = false, error = null, progress = if (release.apkSize > 0) 0f else -1f) }
            val apk = try {
                download(release)
            } catch (e: IOException) {
                Log.w(TAG, "Update-Download fehlgeschlagen", e)
                _state.update { it.copy(progress = null, installing = false, error = R.string.update_error_download) }
                return@launch
            }
            _state.update { it.copy(progress = null, installing = true) }
            try {
                withContext(Dispatchers.IO) { commitSession(apk) }
            } catch (e: IOException) {
                Log.w(TAG, "Installation konnte nicht gestartet werden", e)
                _state.update { it.copy(installing = false, error = R.string.update_error_install) }
            }
        }
    }

    fun permissionChecked() {
        if (_state.value.needsPermission && context.packageManager.canRequestPackageInstalls()) {
            _state.update { it.copy(needsPermission = false) }
            install()
        }
    }

    /**
     * Lädt die APK. Zuerst über die normale Download-Adresse; antwortet github.com dort mit einem Fehler
     * (z. B. 504 bei Störungen), wird die API-Adresse des Assets versucht.
     */
    private suspend fun download(release: AppRelease): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "vorrat-${release.version}.apk")
        val requests = listOfNotNull(
            Request.Builder().url(release.apkUrl).build(),
            release.apiUrl?.let { Request.Builder().url(it).header("Accept", "application/octet-stream").build() },
        )
        var lastError: IOException? = null
        for (request in requests) {
            try {
                downloadTo(request, release, target)
                return@withContext target
            } catch (e: IOException) {
                Log.w(TAG, "Download über ${request.url.host} fehlgeschlagen", e)
                target.delete()
                lastError = e
            }
        }
        throw lastError ?: IOException("Keine Download-Adresse")
    }

    private fun downloadTo(request: Request, release: AppRelease, target: File) {
        downloadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Download ${response.code}")
            val body = response.body
            val total = release.apkSize.takeIf { it > 0 } ?: body.contentLength()
            var done = 0L
            var lastReported = -1
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) {
                            val percent = (done * 100 / total).toInt()
                            if (percent != lastReported) {
                                lastReported = percent
                                _state.update { it.copy(progress = (done.toFloat() / total).coerceIn(0f, 1f)) }
                            }
                        }
                    }
                }
            }
            if (release.apkSize > 0 && done != release.apkSize) {
                target.delete()
                throw IOException("Unvollständig: $done von ${release.apkSize} Bytes")
            }
        }
    }

    private fun commitSession(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            // Ab der zweiten Selbst-Installation darf Android die Rückfrage weglassen; beim ersten Mal fragt es.
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite("vorrat.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val callback = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    Intent(context, InstallResultReceiver::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(callback.intentSender)
            }
        } catch (e: Exception) {
            installer.abandonSession(sessionId)
            throw IOException("Installation konnte nicht gestartet werden", e)
        }
    }

    /** Aufruf aus [InstallResultReceiver]. Bei Erfolg wird der Prozess ohnehin durch die neue Version ersetzt. */
    internal fun onInstallResult(status: Int, message: String?) {
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> Unit
            PackageInstaller.STATUS_SUCCESS -> _state.update { it.copy(installing = false, prompt = false) }
            PackageInstaller.STATUS_FAILURE_ABORTED -> _state.update { it.copy(installing = false) }
            else -> {
                Log.w(TAG, "Installation fehlgeschlagen: $status $message")
                val error = if (status == PackageInstaller.STATUS_FAILURE_CONFLICT || status == PackageInstaller.STATUS_FAILURE_INCOMPATIBLE) {
                    R.string.update_error_signature
                } else {
                    R.string.update_error_install
                }
                _state.update { it.copy(installing = false, prompt = true, error = error) }
            }
        }
    }

    private companion object {
        const val TAG = "UpdateManager"
        const val KEY_LAST_CHECK = "lastCheck"
        const val KEY_DISMISSED = "dismissedVersion"
        const val CHECK_EVERY_MS = 60 * 60 * 1000L
    }
}
