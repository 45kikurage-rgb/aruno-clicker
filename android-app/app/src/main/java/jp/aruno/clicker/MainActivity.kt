package jp.aruno.clicker

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import jp.aruno.clicker.ui.ArunoClickerApp
import jp.aruno.clicker.ui.ArunoClickerTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private var permissionRefresh by mutableStateOf(0)

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { permissionRefresh++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_ArunoClicker)
        super.onCreate(savedInstanceState)
        setContent {
            ArunoClickerTheme {
                ArunoClickerApp(
                    viewModel = viewModel,
                    isVerS = BuildConfig.IS_VER_S,
                    useAccessibilityOverlay = BuildConfig.USE_ACCESSIBILITY_OVERLAY,
                    permissionRefresh = permissionRefresh,
                    openAccessibilitySettings = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    requestOverlayPermission = {
                        startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName"),
                            ),
                        )
                    },
                    requestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    startFirstAutomation = { ControlContract.startFirst(this) },
                    startRepeatAutomation = { ControlContract.startRepeat(this) },
                    startAutomaticAutomation = { ControlContract.startAutomatic(this) },
                    stopAutomation = { ControlContract.stop(this) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshSettings()
        permissionRefresh++
    }
}
