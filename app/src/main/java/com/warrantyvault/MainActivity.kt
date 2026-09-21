package com.warrantyvault

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.warrantyvault.notification.NotificationPermissionHelper
import com.warrantyvault.ui.theme.WarrantyVaultTheme
import com.warrantyvault.ui.screens.MainAppScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WarrantyVaultTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // POST_NOTIFICATIONS is a UI-level concern: an Application context cannot
                    // show the dialog. Request once from the foreground activity, and never
                    // re-prompt after a decision (granted or denied) this install.
                    if (Build.VERSION.SDK_INT >= 33) {
                        var alreadyAsked by remember { mutableStateOf(false) }
                        val launcher = rememberLauncherForActivityResult(
                            ActivityResultContracts.RequestPermission()
                        ) { /* result tracked by the system; worker degrades gracefully */ }

                        LaunchedEffect(Unit) {
                            val granted = ContextCompat.checkSelfPermission(
                                this@MainActivity, NotificationPermissionHelper.PERMISSION
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            if (!granted && !alreadyAsked) {
                                alreadyAsked = true
                                launcher.launch(NotificationPermissionHelper.PERMISSION)
                            }
                        }
                    }
                    MainAppScreen()
                }
            }
        }
    }
}
