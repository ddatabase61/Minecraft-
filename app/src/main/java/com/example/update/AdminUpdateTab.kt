package com.example.update

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.data.firebase.isFirebaseConfigured
import com.example.data.firebase.resolveFirestoreDatabaseId
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val ADMIN_UPDATE_EMAIL = "ddatabase61@gmail.com"

/**
 * Admin Panel tab allowing the verified admin (`ddatabase61@gmail.com`) to publish
 * a new APK release directly to Firestore `/app_config/update` so all older app versions
 * automatically receive the update dialog on launch.
 */
@Composable
fun AdminAppUpdateTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var versionCodeText by remember { mutableStateOf((BuildConfig.VERSION_CODE + 1).toString()) }
    var versionNameText by remember { mutableStateOf("1.${BuildConfig.VERSION_CODE + 1}") }
    var apkUrlText by remember { mutableStateOf("") }
    var forceUpdate by remember { mutableStateOf(false) }
    var releaseNotesText by remember {
        mutableStateOf("नया अपडेट: बेहतर मैप, स्मूथ गेमप्ले और नए फीचर्स!")
    }
    var isSaving by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isErrorStatus by remember { mutableStateOf(false) }

    // Pre-fill from live Firestore /app_config/update if it already exists
    LaunchedEffect(Unit) {
        fetchExistingUpdateConfig(context)?.let { existing ->
            if (existing.latestVersionCode > 0L) {
                versionCodeText = existing.latestVersionCode.toString()
            }
            if (existing.latestVersionName.isNotBlank()) {
                versionNameText = existing.latestVersionName
            }
            if (existing.apkUrl.isNotBlank()) {
                apkUrlText = existing.apkUrl
            }
            forceUpdate = existing.forceUpdate
            if (existing.releaseNotes.isNotBlank()) {
                releaseNotesText = existing.releaseNotes
            }
        }
    }

    val signedInEmail = remember(context) {
        if (isFirebaseConfigured(context)) Firebase.auth.currentUser?.email else null
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, Color(0x5539D98A)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SystemUpdate,
                    contentDescription = "Auto Update Publisher",
                    tint = Color(0xFF4CF09C)
                )
                Column {
                    Text(
                        text = "Auto-Update Publisher (पुराने वर्ज़न में अपडेट भेजें)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "वर्तमान इंस्टॉल वर्ज़न: v${BuildConfig.VERSION_NAME} (versionCode = ${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (signedInEmail != ADMIN_UPDATE_EMAIL) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0x22FFB74D),
                    border = BorderStroke(1.dp, Color(0x77FFB74D)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Firestore में अपडेट पब्लिश करने के लिए कृपया $ADMIN_UPDATE_EMAIL से Google Sign-In करें। (अभी: ${signedInEmail ?: "Guest Mode"})",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFFE0B2),
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = versionCodeText,
                    onValueChange = { versionCodeText = it.filter { c -> c.isDigit() }.take(9) },
                    label = { Text("latestVersionCode") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("admin_update_version_code")
                )
                OutlinedTextField(
                    value = versionNameText,
                    onValueChange = { if (it.length <= 40) versionNameText = it },
                    label = { Text("latestVersionName") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("admin_update_version_name")
                )
            }

            OutlinedTextField(
                value = apkUrlText,
                onValueChange = { if (it.length <= 1024) apkUrlText = it.trim() },
                label = { Text("GitHub Releases Direct APK Link (HTTPS)") },
                placeholder = { Text("https://github.com/USER/REPO/releases/latest/download/BlockForge.apk") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_update_apk_url")
            )

            OutlinedTextField(
                value = releaseNotesText,
                onValueChange = { if (it.length <= 4000) releaseNotesText = it },
                label = { Text("Release Notes (क्या नया है)") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_update_release_notes")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Force Update (अनिवार्य अपडेट)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "चालू होने पर यूज़र 'बाद में' नहीं दबा सकेगा।",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = forceUpdate,
                    onCheckedChange = { forceUpdate = it },
                    modifier = Modifier.testTag("admin_update_force_switch")
                )
            }

            if (statusMessage != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isErrorStatus) MaterialTheme.colorScheme.errorContainer else Color(0x2B39D98A),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = statusMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isErrorStatus) MaterialTheme.colorScheme.onErrorContainer else Color(0xFF7CF7B8),
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Button(
                onClick = {
                    val code = versionCodeText.toLongOrNull() ?: 0L
                    val name = versionNameText.trim()
                    val url = apkUrlText.trim()
                    val notes = releaseNotesText.trim()

                    when {
                        code < 1L -> {
                            isErrorStatus = true
                            statusMessage = "कृपया सही latestVersionCode (1 या उससे बड़ा नंबर) डालें।"
                            return@Button
                        }
                        name.isBlank() -> {
                            isErrorStatus = true
                            statusMessage = "कृपया latestVersionName डालें (जैसे 1.2)।"
                            return@Button
                        }
                        !AppUpdateValidator.isValidApkDownloadUrl(url) -> {
                            isErrorStatus = true
                            statusMessage = "कृपया मान्य https:// GitHub Releases APK लिंक डालें।"
                            return@Button
                        }
                    }

                    isSaving = true
                    statusMessage = null
                    scope.launch {
                        val result = publishUpdateConfigToFirestore(
                            context = context,
                            config = AppUpdateConfig(
                                latestVersionCode = code,
                                latestVersionName = name,
                                apkUrl = url,
                                forceUpdate = forceUpdate,
                                releaseNotes = notes
                            )
                        )
                        isSaving = false
                        result.fold(
                            onSuccess = {
                                isErrorStatus = false
                                statusMessage = "✓ अपडेट v$name (Code $code) लाइव पब्लिश हो गया! अब पुराने वर्ज़न वाले सभी यूज़र्स को ऑटोमैटिक अपडेट दिखेगा।"
                            },
                            onFailure = { err ->
                                isErrorStatus = true
                                statusMessage = err.localizedMessage
                                    ?: "अपडेट पब्लिश नहीं हो सका। कृपया $ADMIN_UPDATE_EMAIL से Google Sign-In जाँचें।"
                            }
                        )
                    }
                },
                enabled = !isSaving,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2ED47E),
                    contentColor = Color(0xFF062013)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_admin_push_apk_update")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF062013)
                    )
                } else {
                    Icon(Icons.Rounded.CloudUpload, contentDescription = null)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isSaving) "पब्लिश हो रहा है…" else "सभी पुराने वर्ज़न में अपडेट भेजें",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private suspend fun fetchExistingUpdateConfig(context: Context): AppUpdateConfig? {
    val appContext = context.applicationContext
    if (!isFirebaseConfigured(appContext)) return null
    return try {
        val dbId = resolveFirestoreDatabaseId(appContext)
        val db = if (dbId != null) FirebaseFirestore.getInstance(dbId) else FirebaseFirestore.getInstance()
        val snap = db.collection("app_config").document("update").get().await()
        if (!snap.exists()) return null
        AppUpdateConfig(
            latestVersionCode = (snap.get("latestVersionCode") as? Number)?.toLong() ?: 0L,
            latestVersionName = snap.getString("latestVersionName").orEmpty(),
            apkUrl = snap.getString("apkUrl").orEmpty(),
            forceUpdate = snap.getBoolean("forceUpdate") ?: false,
            releaseNotes = snap.getString("releaseNotes").orEmpty()
        )
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}

private suspend fun publishUpdateConfigToFirestore(
    context: Context,
    config: AppUpdateConfig
): Result<Unit> {
    val appContext = context.applicationContext
    if (!isFirebaseConfigured(appContext)) {
        return Result.failure(IllegalStateException("इस बिल्ड में Firebase कॉन्फ़िगर नहीं है।"))
    }
    return try {
        val user = Firebase.auth.currentUser
            ?: return Result.failure(IllegalStateException("पहले $ADMIN_UPDATE_EMAIL से Google Sign-In करें।"))
        if (user.email != ADMIN_UPDATE_EMAIL) {
            return Result.failure(IllegalStateException("केवल एडमिन ($ADMIN_UPDATE_EMAIL) ही अपडेट पब्लिश कर सकते हैं।"))
        }
        val dbId = resolveFirestoreDatabaseId(appContext)
        val db = if (dbId != null) FirebaseFirestore.getInstance(dbId) else FirebaseFirestore.getInstance()
        val payload = mapOf(
            "latestVersionCode" to config.latestVersionCode,
            "latestVersionName" to config.latestVersionName,
            "apkUrl" to config.apkUrl,
            "forceUpdate" to config.forceUpdate,
            "releaseNotes" to config.releaseNotes
        )
        db.collection("app_config").document("update").set(payload).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
