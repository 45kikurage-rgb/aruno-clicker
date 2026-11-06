package jp.aruno.clicker.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.aruno.clicker.automation.AccessibilityHealthSnapshot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TikerUpdateButtonTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun updateIsVisibleAndOpensTheOfficialPageWhileIdle() {
        val running = mutableStateOf(false)
        val openedUris = mutableListOf<String>()
        val uriHandler = object : UriHandler {
            override fun openUri(uri: String) {
                openedUris.add(uri)
            }
        }
        compose.setContent {
            CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                ArunoClickerTheme {
                    UrlRunnerHomeScreen(
                        isAdmin = false,
                        isRunning = running.value,
                        savedConfig = null,
                        statusMessage = "",
                        syncBusy = false,
                        syncMessage = "",
                        accessibilityGranted = false,
                        accessibilityHealth = AccessibilityHealthSnapshot(false, false, "", emptyList()),
                        openAccessibilitySettings = {},
                        onStartVideo = {},
                        onStartFromSixthWithSplash = {},
                        onStartFromSixthWithoutSplash = {},
                        onStop = {},
                        onDownload = {},
                        onOpenSettings = {},
                        onOpenLegacySettings = {},
                        onTestGiftHome = {},
                    )
                }
            }
        }

        val update = compose.onNodeWithText("アプリ更新")
        update.assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(listOf("https://download.aruno-id.com/tiker"), openedUris)
            running.value = true
        }
        update.assertIsDisplayed().assertIsNotEnabled()
    }
}
