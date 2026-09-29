package io.github.sbshrey.tambola.game.updates

import android.app.Application
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.sbshrey.tambola.game.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

internal enum class UpdateStage { IDLE, CHECKING, AVAILABLE, DOWNLOADING, READY, CURRENT, ERROR, PERMISSION }
internal data class UpdateState(val stage: UpdateStage = UpdateStage.IDLE, val release: UpdateRelease? = null,
    val percent: Int = 0, val visible: Boolean = false)

/** Optional downloads never share credentials or storage with the game session. */
internal class UpdateViewModel(app: Application) : AndroidViewModel(app) {
    private val mutable = MutableStateFlow(UpdateState())
    val state = mutable.asStateFlow()
    val enabled = BuildConfig.BUILD_TYPE == "publicBeta"
    private var job: Job? = null
    private var checked = false
    private var eligible = false
    private val directory = File(app.cacheDir, "updates")
    private val apk = File(directory, "update.apk")
    private val partial = File(directory, "download.part")

    fun setEligible(value: Boolean) { eligible = value; if (value && !checked) check(false) }
    fun check(manual: Boolean = true) {
        if (!enabled || !eligible) return
        if (job?.isActive == true) { if (manual) mutable.update { it.copy(visible = true) }; return }
        if (manual && mutable.value.stage in setOf(UpdateStage.READY, UpdateStage.PERMISSION, UpdateStage.AVAILABLE)) {
            mutable.update { it.copy(visible = true) }; return
        }
        checked = true
        mutable.value = UpdateState(stage = UpdateStage.CHECKING, visible = manual)
        job = viewModelScope.launch {
            try {
                val release = withContext(Dispatchers.IO) {
                    val connection = connection(UpdateFeed.URL)
                    try {
                        require(connection.responseCode == 200)
                        val data = connection.inputStream.use { input ->
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                ensureActive(); val n = input.read(buffer); if (n < 0) break
                                require(output.size() + n <= 2 * 1024 * 1024); output.write(buffer, 0, n)
                            }
                            output.toByteArray()
                        }
                        require(data.size <= 2 * 1024 * 1024)
                        UpdateFeed.latest(data.toString(Charsets.UTF_8), BuildConfig.VERSION_CODE)
                    } finally { connection.disconnect() }
                }
                mutable.value = UpdateState(if (release == null) UpdateStage.CURRENT else UpdateStage.AVAILABLE,
                    release, visible = manual || release != null)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.value = UpdateState(UpdateStage.ERROR, visible = manual) }
        }
    }
    fun dismiss() { mutable.update { it.copy(visible = false) } }
    fun cancel() {
        job?.cancel()
        mutable.update { it.copy(stage = if (it.release == null) UpdateStage.IDLE else UpdateStage.AVAILABLE, visible = false, percent = 0) }
    }
    fun download() {
        val release = mutable.value.release ?: return
        if (!eligible || job?.isActive == true) return
        mutable.update { it.copy(stage = UpdateStage.DOWNLOADING, percent = 0) }
        job = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    directory.mkdirs(); partial.delete(); apk.delete()
                    var connection = connection(release.url)
                    try {
                        var redirects = 0
                        while (connection.responseCode in setOf(301, 302, 303, 307, 308)) {
                            require(++redirects <= 5)
                            val target = URL(connection.url, requireNotNull(connection.getHeaderField("Location")))
                            connection.disconnect()
                            connection = connection(target.toString())
                        }
                        require(connection.responseCode == 200)
                        val digest = MessageDigest.getInstance("SHA-256")
                        var count = 0L
                        val started = android.os.SystemClock.elapsedRealtime()
                        connection.inputStream.use { input -> partial.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                ensureActive()
                                require(android.os.SystemClock.elapsedRealtime() - started < 300_000)
                                val read = input.read(buffer)
                                if (read < 0) break
                                count += read
                                require(count <= release.bytes)
                                output.write(buffer, 0, read); digest.update(buffer, 0, read)
                                mutable.update { it.copy(percent = (count * 100 / release.bytes).toInt()) }
                            }
                        } }
                        require(count == release.bytes && digest.digest().hex() == release.sha256)
                        verifyApk(partial, release)
                        check(partial.renameTo(apk))
                    } finally { connection.disconnect(); partial.delete() }
                }
                mutable.update { it.copy(stage = UpdateStage.READY) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.update { it.copy(stage = UpdateStage.ERROR) } }
        }
    }
    /** Re-check immediately before handing a private content URI to Android. */
    fun install(activity: android.app.Activity) {
        val release = mutable.value.release ?: return
        if (!eligible || job?.isActive == true) return
        job = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    require(apk.length() == release.bytes)
                    val digest = MessageDigest.getInstance("SHA-256")
                    apk.inputStream().use { input -> val buffer = ByteArray(65536); while (true) {
                        ensureActive(); val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n)
                    } }
                    require(digest.digest().hex() == release.sha256); verifyApk(apk, release)
                }
                if (!eligible) return@launch
                if (!activity.packageManager.canRequestPackageInstalls()) {
                    mutable.update { it.copy(stage = UpdateStage.PERMISSION) }
                    activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.packageName)))
                } else {
                    val uri = FileProvider.getUriForFile(activity, activity.packageName + ".updates", apk)
                    activity.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    mutable.update { it.copy(stage = UpdateStage.READY) }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.update { it.copy(stage = UpdateStage.ERROR) } }
        }
    }
    @Suppress("DEPRECATION")
    private fun verifyApk(file: File, release: UpdateRelease) {
        val pm = getApplication<Application>().packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val candidate = requireNotNull(pm.getPackageArchiveInfo(file.path, flags))
        val current = pm.getPackageInfo(BuildConfig.APPLICATION_ID, flags)
        val version = if (Build.VERSION.SDK_INT >= 28) candidate.longVersionCode else candidate.versionCode.toLong()
        fun signers(info: PackageInfo): Set<String> = (if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures)
            .orEmpty().map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).hex() }.toSet()
        val trusted = signers(current)
        validateUpdateIdentity(candidate.packageName, version, requireNotNull(candidate.applicationInfo).minSdkVersion,
            signers(candidate), BuildConfig.APPLICATION_ID, BuildConfig.VERSION_CODE, Build.VERSION.SDK_INT, trusted, release.version)
    }
    private fun connection(value: String): HttpsURLConnection {
        val url = URL(value)
        require(url.protocol == "https" && url.userInfo == null && url.port in setOf(-1, 443))
        require(url.host in setOf("api.github.com", "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com"))
        return (url.openConnection() as HttpsURLConnection).apply {
            instanceFollowRedirects = false; connectTimeout = 15000; readTimeout = 20000
            setRequestProperty("User-Agent", "Tambola-Beta-Updater")
            setRequestProperty("Accept", if (url.host == "api.github.com") "application/vnd.github+json" else "application/octet-stream")
        }
    }
}
private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
