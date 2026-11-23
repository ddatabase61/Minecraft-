package com.example.update

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.Keep
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.example.data.firebase.isFirebaseConfigured
import com.example.data.firebase.resolveFirestoreDatabaseId
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Locale

private const val UPDATE_TAG = "AppUpdateManager"
private const val CONFIG_COLLECTION = "app_config"
private const val CONFIG_DOCUMENT = "update"
private const val UPDATES_SUBDIR = "updates"
private const val APK_FILE_NAME = "blockforge_update.apk"
private const val MAX_REDIRECTS = 5
private const val CONNECT_TIMEOUT_MS = 15_000
private const val READ_TIMEOUT_MS = 30_000
private const val BUFFER_SIZE_BYTES = 16 * 1024

/**
 * Firestore document schema at `/app_config/update`.
 */
@Keep
@IgnoreExtraProperties
data class AppUpdateConfig(
    val latestVersionCode: Long = 0L,
    val latestVersionName: String = "",
    val apkUrl: String = "",
    val forceUpdate: Boolean = false,
    val releaseNotes: String = ""
)

/**
 * Lifecycle UI state for the automatic APK updater.
 */
sealed interface UpdateUiState {
    data object Idle : UpdateUiState

    data class UpdateAvailable(
        val config: AppUpdateConfig
    ) : UpdateUiState

    data class Downloading(
        val config: AppUpdateConfig,
        val progressPercent: Int, // -1 when server omits Content-Length
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : UpdateUiState

    data class NeedsInstallPermission(
        val config: AppUpdateConfig,
        val apkFile: File
    ) : UpdateUiState

    data class ReadyToInstall(
        val config: AppUpdateConfig,
        val apkFile: File
    ) : UpdateUiState

    data class Error(
        val config: AppUpdateConfig,
        val message: String
    ) : UpdateUiState
}

/**
 * Pure validation & helper functions (unit-tested independently).
 */
object AppUpdateValidator {
    fun isValidApkDownloadUrl(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.length < 12 || trimmed.length > 1024) return false
        if (trimmed.any { it.isWhitespace() || it.isISOControl() }) return false
        return try {
            val uri = URI(trimmed)
            uri.scheme.equals("https", ignoreCase = true) &&
                !uri.host.isNullOrBlank()
        } catch (_: Exception) {
            false
        }
    }

    fun isUpdateAvailable(currentVersionCode: Int, config: AppUpdateConfig): Boolean {
        return config.latestVersionCode > currentVersionCode.toLong() &&
            config.latestVersionName.isNotBlank() &&
            isValidApkDownloadUrl(config.apkUrl)
    }

    fun computeDownloadPercent(downloadedBytes: Long, totalBytes: Long): Int {
        if (totalBytes <= 0L) return -1
        return ((downloadedBytes.coerceAtLeast(0L) * 100L) / totalBytes)
            .toInt()
            .coerceIn(0, 100)
    }

    fun formatBytesReadable(bytes: Long): String {
        if (bytes <= 0L) return "0 KB"
        val kb = bytes / 1024.0
        if (kb < 1024.0) {
            return String.format(Locale.US, "%.0f KB", kb)
        }
        val mb = kb / 1024.0
        return String.format(Locale.US, "%.1f MB", mb)
    }
}

/**
 * Coordinates Firestore update checks, direct GitHub Releases APK downloads,
 * Android 8+ Unknown Sources permission checks, and FileProvider installation.
 */
class AppUpdateController {
    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    @Volatile
    private var hasCheckedThisSession = false

    /**
     * Checks `/app_config/update` on app startup when online.
     * Silently skips when offline, unconfigured, or unreachable so the app never crashes.
     */
    suspend fun checkForUpdate(
        context: Context,
        currentVersionCode: Int = BuildConfig.VERSION_CODE
    ) {
        if (hasCheckedThisSession) return
        hasCheckedThisSession = true

        val appContext = context.applicationContext
        if (!isNetworkAvailable(appContext)) {
            Log.d(UPDATE_TAG, "Offline: skipping update check silently.")
            return
        }
        if (!isFirebaseConfigured(appContext)) {
            Log.d(UPDATE_TAG, "Firebase not configured: skipping update check silently.")
            return
        }

        try {
            val dbId = resolveFirestoreDatabaseId(appContext)
            val firestore = if (dbId != null) {
                FirebaseFirestore.getInstance(dbId)
            } else {
                FirebaseFirestore.getInstance()
            }

            val snapshot = firestore
                .collection(CONFIG_COLLECTION)
                .document(CONFIG_DOCUMENT)
                .get(Source.SERVER)
                .await()

            if (!snapshot.exists()) {
                Log.d(UPDATE_TAG, "No /app_config/update document found; skipping.")
                return
            }

            val rawVersionCode = (snapshot.get("latestVersionCode") as? Number)?.toLong() ?: 0L
            val rawVersionName = snapshot.getString("latestVersionName")?.trim().orEmpty()
            val rawApkUrl = snapshot.getString("apkUrl")?.trim().orEmpty()
            val rawForceUpdate = snapshot.getBoolean("forceUpdate") ?: false
            val rawReleaseNotes = snapshot.getString("releaseNotes")?.trim().orEmpty()

            val config = AppUpdateConfig(
                latestVersionCode = rawVersionCode,
                latestVersionName = rawVersionName,
                apkUrl = rawApkUrl,
                forceUpdate = rawForceUpdate,
                releaseNotes = rawReleaseNotes
            )

            if (AppUpdateValidator.isUpdateAvailable(currentVersionCode, config)) {
                Log.i(
                    UPDATE_TAG,
                    "Update available: v${config.latestVersionName} (${config.latestVersionCode}) > current ($currentVersionCode), force=${config.forceUpdate}"
                )
                _uiState.value = UpdateUiState.UpdateAvailable(config)
            } else {
                Log.d(
                    UPDATE_TAG,
                    "App is up to date (current=$currentVersionCode, latest=${config.latestVersionCode})."
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Silently ignore network/server errors so the game never crashes on launch.
            Log.w(UPDATE_TAG, "Update check skipped due to error: ${e.message}")
        }
    }

    /**
     * Downloads the APK from GitHub Releases into the app's private cache/updates folder
     * while emitting live progress percentages, then launches the package installer.
     */
    suspend fun startDownload(context: Context, config: AppUpdateConfig) {
        val appContext = context.applicationContext
        if (!AppUpdateValidator.isValidApkDownloadUrl(config.apkUrl)) {
            _uiState.value = UpdateUiState.Error(
                config = config,
                message = "अपडेट डाउनलोड लिंक अमान्य या असुरक्षित (HTTPS नहीं) है।"
            )
            return
        }
        if (!isNetworkAvailable(appContext)) {
            _uiState.value = UpdateUiState.Error(
                config = config,
                message = "इंटरनेट कनेक्शन उपलब्ध नहीं है। कृपया नेटवर्क जाँचें और दोबारा कोशिश करें।"
            )
            return
        }

        _uiState.value = UpdateUiState.Downloading(
            config = config,
            progressPercent = 0,
            downloadedBytes = 0L,
            totalBytes = -1L
        )

        val result = withContext(Dispatchers.IO) {
            downloadApkToCache(appContext, config) { downloaded, total ->
                val percent = AppUpdateValidator.computeDownloadPercent(downloaded, total)
                _uiState.value = UpdateUiState.Downloading(
                    config = config,
                    progressPercent = percent,
                    downloadedBytes = downloaded,
                    totalBytes = total
                )
            }
        }

        result.fold(
            onSuccess = { apkFile ->
                proceedToInstall(context, config, apkFile)
            },
            onFailure = { error ->
                if (error is CancellationException) throw error
                Log.e(UPDATE_TAG, "APK download failed", error)
                _uiState.value = UpdateUiState.Error(
                    config = config,
                    message = error.message
                        ?: "APK डाउनलोड करने में समस्या आई। कृपया दोबारा कोशिश करें।"
                )
            }
        )
    }

    /**
     * Checks Android 8+ Unknown Sources permission and launches the system APK installer.
     */
    fun proceedToInstall(context: Context, config: AppUpdateConfig, apkFile: File) {
        if (!apkFile.exists() || apkFile.length() == 0L) {
            _uiState.value = UpdateUiState.Error(
                config = config,
                message = "डाउनलोड की गई APK फ़ाइल नहीं मिली। कृपया दोबारा डाउनलोड करें।"
            )
            return
        }

        if (!canInstallPackages(context)) {
            _uiState.value = UpdateUiState.NeedsInstallPermission(config, apkFile)
            return
        }

        _uiState.value = UpdateUiState.ReadyToInstall(config, apkFile)
        launchInstaller(context, config, apkFile)
    }

    /**
     * Re-triggers installation automatically when the user returns from Android Settings
     * after enabling "Install unknown apps".
     */
    fun onReturnedFromInstallSettings(context: Context) {
        val current = _uiState.value
        if (current is UpdateUiState.NeedsInstallPermission && canInstallPackages(context)) {
            _uiState.value = UpdateUiState.ReadyToInstall(current.config, current.apkFile)
            launchInstaller(context, current.config, current.apkFile)
        }
    }

    fun launchInstaller(context: Context, config: AppUpdateConfig, apkFile: File) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(context, authority, apkFile)
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(UPDATE_TAG, "Failed to launch package installer", e)
            _uiState.value = UpdateUiState.Error(
                config = config,
                message = "पैकेज इंस्टॉलर खोलने में समस्या आई: ${e.localizedMessage ?: "अज्ञात त्रुटि"}"
            )
        }
    }

    fun openUnknownSourcesSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(UPDATE_TAG, "Failed to open unknown app sources settings", e)
            }
        }
    }

    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Dismisses the dialog ONLY when `forceUpdate` is false.
     */
    fun dismissIfAllowed() {
        val config = when (val state = _uiState.value) {
            is UpdateUiState.UpdateAvailable -> state.config
            is UpdateUiState.NeedsInstallPermission -> state.config
            is UpdateUiState.ReadyToInstall -> state.config
            is UpdateUiState.Error -> state.config
            is UpdateUiState.Downloading -> return // Do not dismiss mid-download
            UpdateUiState.Idle -> return
        }
        if (!config.forceUpdate) {
            _uiState.value = UpdateUiState.Idle
        }
    }

    /**
     * Exposed internally for unit testing state transitions.
     */
    internal fun setStateForTest(state: UpdateUiState) {
        _uiState.value = state
    }

    private fun downloadApkToCache(
        context: Context,
        config: AppUpdateConfig,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> {
        val updatesDir = File(context.cacheDir, UPDATES_SUBDIR).apply { mkdirs() }
        val tempFile = File(updatesDir, "$APK_FILE_NAME.tmp")
        val finalFile = File(updatesDir, APK_FILE_NAME)

        // Clean stale downloads before starting
        if (tempFile.exists()) tempFile.delete()
        if (finalFile.exists()) finalFile.delete()

        var connection: HttpURLConnection? = null
        return try {
            connection = openValidatedHttpsConnection(config.apkUrl)
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("सर्वर से APK डाउनलोड नहीं हो सका (HTTP $responseCode)। कृपया लिंक जाँचें।")
            }

            val totalBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connection.contentLengthLong
            } else {
                connection.contentLength.toLong()
            }

            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE_BYTES)
                    var downloaded = 0L
                    var lastReportTime = 0L
                    var bytesRead = input.read(buffer)
                    while (bytesRead >= 0) {
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        val now = System.currentTimeMillis()
                        if (now - lastReportTime >= 120L || (totalBytes > 0 && downloaded >= totalBytes)) {
                            lastReportTime = now
                            onProgress(downloaded, totalBytes)
                        }
                        bytesRead = input.read(buffer)
                    }
                    output.flush()
                    onProgress(downloaded, totalBytes)
                }
            }

            if (!isValidApkZipHeader(tempFile)) {
                tempFile.delete()
                throw IOException("डाउनलोड की गई फ़ाइल सही APK नहीं है। कृपया GitHub Release का सीधा .apk लिंक जाँचें।")
            }

            if (!tempFile.renameTo(finalFile)) {
                tempFile.copyTo(finalFile, overwrite = true)
                tempFile.delete()
            }
            Result.success(finalFile)
        } catch (e: CancellationException) {
            tempFile.delete()
            throw e
        } catch (e: Exception) {
            tempFile.delete()
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Follows up to [MAX_REDIRECTS] HTTP 3xx redirects (required for GitHub Releases ->
     * objects.githubusercontent.com) while enforcing HTTPS at every hop.
     */
    private fun openValidatedHttpsConnection(initialUrl: String): HttpURLConnection {
        var currentUrl = initialUrl.trim()
        repeat(MAX_REDIRECTS + 1) { hop ->
            if (!AppUpdateValidator.isValidApkDownloadUrl(currentUrl)) {
                throw IOException("असुरक्षित रीडायरेक्ट लिंक ब्लॉक कर दिया गया (केवल HTTPS अनुमत है)।")
            }
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.android.package-archive, application/octet-stream, */*")
                setRequestProperty("User-Agent", "BlockForge-Android-Updater/${BuildConfig.VERSION_NAME}")
            }
            conn.connect()
            val code = conn.responseCode
            val isRedirect = code == HttpURLConnection.HTTP_MOVED_PERM ||
                code == HttpURLConnection.HTTP_MOVED_TEMP ||
                code == HttpURLConnection.HTTP_SEE_OTHER ||
                code == 307 ||
                code == 308
            if (!isRedirect) {
                return conn
            }
            val location = conn.getHeaderField("Location")
            conn.disconnect()
            if (location.isNullOrBlank()) {
                throw IOException("डाउनलोड रीडायरेक्ट अधूरा है (HTTP $code)।")
            }
            if (hop == MAX_REDIRECTS) {
                throw IOException("बहुत अधिक रीडायरेक्ट हुए। कृपया GitHub APK लिंक जाँचें।")
            }
            currentUrl = URL(URL(currentUrl), location).toString()
        }
        throw IOException("डाउनलोड कनेक्शन स्थापित नहीं हो सका।")
    }

    /**
     * Verifies the downloaded file starts with the ZIP/APK signature ('P', 'K').
     */
    private fun isValidApkZipHeader(file: File): Boolean {
        if (!file.exists() || file.length() < 4L) return false
        return try {
            FileInputStream(file).use { stream ->
                val b0 = stream.read()
                val b1 = stream.read()
                b0 == 0x50 && b1 == 0x4B // 'P' 'K'
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val activeNetwork = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (_: Exception) {
            false
        }
    }
}
