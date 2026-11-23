package com.example

import com.example.update.AppUpdateConfig
import com.example.update.AppUpdateController
import com.example.update.AppUpdateValidator
import com.example.update.UpdateUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {

    private val validConfig = AppUpdateConfig(
        latestVersionCode = 2L,
        latestVersionName = "1.1.0",
        apkUrl = "https://github.com/myuser/blockforge/releases/download/v1.1.0/BlockForge-v1.1.0.apk",
        forceUpdate = false,
        releaseNotes = "नया अपडेट!"
    )

    @Test
    fun isUpdateAvailable_trueWhenLatestVersionCodeHigherAndHttpsUrl() {
        assertTrue(AppUpdateValidator.isUpdateAvailable(currentVersionCode = 1, config = validConfig))
    }

    @Test
    fun isUpdateAvailable_falseWhenCurrentVersionEqualOrHigher() {
        assertFalse(AppUpdateValidator.isUpdateAvailable(currentVersionCode = 2, config = validConfig))
        assertFalse(AppUpdateValidator.isUpdateAvailable(currentVersionCode = 3, config = validConfig))
    }

    @Test
    fun isValidApkDownloadUrl_blocksNonHttpsAndMalformedUrls() {
        assertTrue(AppUpdateValidator.isValidApkDownloadUrl(validConfig.apkUrl))
        assertFalse(AppUpdateValidator.isValidApkDownloadUrl("http://github.com/user/repo/releases/download/v1/app.apk"))
        assertFalse(AppUpdateValidator.isValidApkDownloadUrl("file:///sdcard/app.apk"))
        assertFalse(AppUpdateValidator.isValidApkDownloadUrl("javascript:alert(1)"))
        assertFalse(AppUpdateValidator.isValidApkDownloadUrl(""))
        assertFalse(AppUpdateValidator.isValidApkDownloadUrl("https://github.com/with space/app.apk"))
    }

    @Test
    fun computeDownloadPercent_handlesKnownAndUnknownTotalBytes() {
        assertEquals(0, AppUpdateValidator.computeDownloadPercent(0L, 1000L))
        assertEquals(50, AppUpdateValidator.computeDownloadPercent(500L, 1000L))
        assertEquals(100, AppUpdateValidator.computeDownloadPercent(1200L, 1000L))
        assertEquals(-1, AppUpdateValidator.computeDownloadPercent(500L, -1L))
        assertEquals(-1, AppUpdateValidator.computeDownloadPercent(500L, 0L))
    }

    @Test
    fun dismissIfAllowed_blocksDismissWhenForceUpdateTrue_allowsWhenFalse() {
        val controller = AppUpdateController()

        val forcedConfig = validConfig.copy(forceUpdate = true)
        controller.setStateForTest(UpdateUiState.UpdateAvailable(forcedConfig))
        controller.dismissIfAllowed()
        assertTrue(
            "Force update dialog must not dismiss",
            controller.uiState.value is UpdateUiState.UpdateAvailable
        )

        val optionalConfig = validConfig.copy(forceUpdate = false)
        controller.setStateForTest(UpdateUiState.UpdateAvailable(optionalConfig))
        controller.dismissIfAllowed()
        assertEquals(UpdateUiState.Idle, controller.uiState.value)
    }
}
