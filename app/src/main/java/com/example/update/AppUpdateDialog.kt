package com.example.update

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import kotlinx.coroutines.launch

/**
 * Self-contained root host composable that automatically checks Firestore `/app_config/update`
 * once per app launch and renders the update dialog whenever a newer APK release is available.
 */
@Composable
fun AppUpdateHost(
    controller: AppUpdateController = remember { AppUpdateController() }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by controller.uiState.collectAsStateWithLifecycle()

    // Automatically check for updates on every app launch
    LaunchedEffect(controller) {
        controller.checkForUpdate(context)
    }

    // Automatically resume APK install when returning from Android "Unknown app sources" Settings
    LifecycleResumeEffect(controller, context) {
        controller.onReturnedFromInstallSettings(context)
        onPauseOrDispose { }
    }

    if (uiState != UpdateUiState.Idle) {
        AppUpdateDialog(
            state = uiState,
            onStartDownload = { config ->
                scope.launch {
                    controller.startDownload(context, config)
                }
            },
            onInstallNow = { config, apkFile ->
                controller.proceedToInstall(context, config, apkFile)
            },
            onOpenSettings = {
                controller.openUnknownSourcesSettings(context)
            },
            onDismiss = {
                controller.dismissIfAllowed()
            }
        )
    }
}

@Composable
fun AppUpdateDialog(
    state: UpdateUiState,
    onStartDownload: (AppUpdateConfig) -> Unit,
    onInstallNow: (AppUpdateConfig, java.io.File) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val config = when (state) {
        is UpdateUiState.UpdateAvailable -> state.config
        is UpdateUiState.Downloading -> state.config
        is UpdateUiState.NeedsInstallPermission -> state.config
        is UpdateUiState.ReadyToInstall -> state.config
        is UpdateUiState.Error -> state.config
        UpdateUiState.Idle -> return
    }

    val isDownloading = state is UpdateUiState.Downloading
    val canDismiss = !config.forceUpdate && !isDownloading

    Dialog(
        onDismissRequest = {
            if (canDismiss) onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = canDismiss,
            dismissOnClickOutside = canDismiss,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 460.dp)
                .testTag("app_update_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF0E1F18),
            border = BorderStroke(
                width = 1.5.dp,
                color = if (config.forceUpdate) Color(0xFFFFB74D) else Color(0xFF39D98A)
            ),
            shadowElevation = 18.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF132D22),
                                Color(0xFF0B1913)
                            )
                        )
                    )
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (state is UpdateUiState.Error) Color(0x33FF5252)
                                else Color(0x2B39D98A)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        val headerIcon = when (state) {
                            is UpdateUiState.Error -> Icons.Rounded.ErrorOutline
                            is UpdateUiState.NeedsInstallPermission -> Icons.Rounded.Security
                            is UpdateUiState.ReadyToInstall -> Icons.Rounded.InstallMobile
                            else -> Icons.Rounded.SystemUpdate
                        }
                        Icon(
                            imageVector = headerIcon,
                            contentDescription = stringResource(R.string.update_cd_icon),
                            tint = if (state is UpdateUiState.Error) Color(0xFFFF6E6E) else Color(0xFF4CF09C),
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (state) {
                                is UpdateUiState.Downloading -> stringResource(R.string.update_downloading_title)
                                is UpdateUiState.NeedsInstallPermission -> stringResource(R.string.update_permission_title)
                                is UpdateUiState.ReadyToInstall -> stringResource(R.string.update_ready_title)
                                is UpdateUiState.Error -> stringResource(R.string.update_error_title)
                                else -> stringResource(R.string.update_title)
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF0FFF7)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0x2E39D98A),
                                border = BorderStroke(1.dp, Color(0x6639D98A))
                            ) {
                                Text(
                                    text = "v${config.latestVersionName}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF69F5AE),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }

                            if (config.forceUpdate) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0x33FF9800),
                                    border = BorderStroke(1.dp, Color(0xAAFFB74D))
                                ) {
                                    Text(
                                        text = stringResource(R.string.update_force_badge),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFFD180),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Release Notes Box (shown in UpdateAvailable, Downloading, ReadyToInstall)
                if (state is UpdateUiState.UpdateAvailable || state is UpdateUiState.ReadyToInstall) {
                    val notes = config.releaseNotes.ifBlank {
                        stringResource(R.string.update_default_notes)
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0x8807130E),
                        border = BorderStroke(1.dp, Color(0x3339D98A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.update_whats_new),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF8BE8B7)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 130.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = notes,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFFD8EFE2)
                                )
                            }
                        }
                    }
                }

                // State-specific content & action buttons
                when (state) {
                    is UpdateUiState.UpdateAvailable -> {
                        UpdateActionButtons(
                            primaryLabel = stringResource(R.string.update_action_now),
                            primaryTestTag = "update_now_button",
                            primaryIcon = Icons.Rounded.Download,
                            onPrimaryClick = { onStartDownload(config) },
                            showLaterButton = !config.forceUpdate,
                            onLaterClick = onDismiss
                        )
                    }

                    is UpdateUiState.Downloading -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (state.progressPercent >= 0) {
                                        "${state.progressPercent}%"
                                    } else {
                                        "डाउनलोड हो रहा है…"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF53FF9E),
                                    modifier = Modifier.testTag("update_progress_percent")
                                )
                                Text(
                                    text = if (state.totalBytes > 0L) {
                                        "${AppUpdateValidator.formatBytesReadable(state.downloadedBytes)} / ${AppUpdateValidator.formatBytesReadable(state.totalBytes)}"
                                    } else {
                                        AppUpdateValidator.formatBytesReadable(state.downloadedBytes)
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFFAAD4BE)
                                )
                            }

                            if (state.progressPercent >= 0) {
                                LinearProgressIndicator(
                                    progress = { state.progressPercent / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .testTag("update_progress_bar"),
                                    color = Color(0xFF39D98A),
                                    trackColor = Color(0x3339D98A)
                                )
                            } else {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .testTag("update_progress_bar"),
                                    color = Color(0xFF39D98A),
                                    trackColor = Color(0x3339D98A)
                                )
                            }

                            Text(
                                text = stringResource(R.string.update_downloading_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF9AC2AE)
                            )
                        }
                    }

                    is UpdateUiState.NeedsInstallPermission -> {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0x22FFB74D),
                            border = BorderStroke(1.dp, Color(0x77FFB74D)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.update_permission_message),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFFFE7C2),
                                modifier = Modifier.padding(12.dp)
                            )
                        }

                        UpdateActionButtons(
                            primaryLabel = stringResource(R.string.update_action_open_settings),
                            primaryTestTag = "open_install_settings_button",
                            primaryIcon = Icons.Rounded.Security,
                            onPrimaryClick = onOpenSettings,
                            showLaterButton = !config.forceUpdate,
                            onLaterClick = onDismiss
                        )
                    }

                    is UpdateUiState.ReadyToInstall -> {
                        Text(
                            text = stringResource(R.string.update_ready_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFA9EBC8)
                        )

                        UpdateActionButtons(
                            primaryLabel = stringResource(R.string.update_action_install_now),
                            primaryTestTag = "update_install_now_button",
                            primaryIcon = Icons.Rounded.InstallMobile,
                            onPrimaryClick = { onInstallNow(config, state.apkFile) },
                            showLaterButton = !config.forceUpdate,
                            onLaterClick = onDismiss
                        )
                    }

                    is UpdateUiState.Error -> {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0x2BFF5252),
                            border = BorderStroke(1.dp, Color(0x88FF6E6E)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFFFD6D6),
                                modifier = Modifier
                                    .padding(12.dp)
                                    .testTag("update_error_message")
                            )
                        }

                        UpdateActionButtons(
                            primaryLabel = stringResource(R.string.update_action_retry),
                            primaryTestTag = "update_retry_button",
                            primaryIcon = Icons.Rounded.Refresh,
                            onPrimaryClick = { onStartDownload(config) },
                            showLaterButton = !config.forceUpdate,
                            onLaterClick = onDismiss
                        )
                    }

                    UpdateUiState.Idle -> Unit
                }
            }
        }
    }
}

@Composable
private fun UpdateActionButtons(
    primaryLabel: String,
    primaryTestTag: String,
    primaryIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onPrimaryClick: () -> Unit,
    showLaterButton: Boolean,
    onLaterClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showLaterButton) {
            OutlinedButton(
                onClick = onLaterClick,
                modifier = Modifier
                    .weight(0.42f)
                    .height(48.dp)
                    .testTag("update_later_button"),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0x668AB8A0)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFC9E5D6)
                )
            ) {
                Text(
                    text = stringResource(R.string.update_action_later),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }

        Button(
            onClick = onPrimaryClick,
            modifier = Modifier
                .weight(if (showLaterButton) 0.58f else 1f)
                .height(48.dp)
                .testTag(primaryTestTag),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2ED47E),
                contentColor = Color(0xFF062013)
            )
        ) {
            Icon(
                imageVector = primaryIcon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = primaryLabel,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}
