package com.warrantyvault.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.warrantyvault.ui.theme.ThemePreference
import com.warrantyvault.ui.theme.WarrantyVaultTheme
import java.io.File

/**
 * Real CameraX document capture: live preview -> capture -> shot preview -> retake/accept.
 * Returns the captured JPEG path via the activity result.
 *
 * Permission authority: exactly one launcher, requested from onCreate via state. Denial shows
 * guidance (including an "open settings" path when permanently denied) instead of a dead screen.
 */
class CameraCaptureActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OUTPUT_PATH = "extra_output_path"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val outputDir = cacheDir.resolve("captures").apply { mkdirs() }
        val outputFile = File.createTempFile("capture_", ".jpg", outputDir)

        setContent {
            // The capture screen is themed too, so the preview chrome matches the app's palette.
            val themeMode by ThemePreference.mode.collectAsState()
            WarrantyVaultTheme(mode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    CameraCaptureContent(
                        outputFile = outputFile,
                        onAccepted = { path ->
                            setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_OUTPUT_PATH, path))
                            finish()
                        },
                        onCancelled = {
                            setResult(Activity.RESULT_CANCELED)
                            finish()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraCaptureContent(
    outputFile: File,
    onAccepted: (String) -> Unit,
    onCancelled: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember { ImageCapture.Builder().build() }

    var capturedPath by remember { mutableStateOf<String?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    // Single permission authority for this activity.
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permanentlyDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        // If the system stops showing the rationale, the user chose "don't ask again";
        // guide them to app settings instead of looping on a dead dialog.
        permanentlyDenied = !granted && !shouldShowRationale(context)
    }

    // Request exactly once per entry into the activity.
    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when {
            capturedPath != null -> {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(File(capturedPath!!)).build(),
                    contentDescription = "Captured document",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(48.dp)
                ) {
                    IconButton(
                        onClick = {
                            File(capturedPath!!).delete()
                            capturedPath = null
                        },
                        modifier = Modifier.size(56.dp).background(Color.White.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(Icons.Default.Replay, contentDescription = "Retake", tint = Color.White)
                    }
                    IconButton(
                        onClick = { onAccepted(capturedPath!!) },
                        modifier = Modifier.size(56.dp).background(MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Use photo", tint = Color.White)
                    }
                }
            }
            hasPermission -> {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val providerFuture = ProcessCameraProvider.getInstance(ctx)
                        providerFuture.addListener({
                            try {
                                val provider = providerFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }
                                provider.unbindAll()
                                provider.bindToLifecycle(
                                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
                                )
                            } catch (e: Exception) {
                                cameraError = "Camera unavailable: ${e.message}"
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Position the document flat and fill the frame",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(48.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onCancelled, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Cancel", tint = Color.White)
                        }
                        IconButton(
                            onClick = {
                                val options = ImageCapture.OutputFileOptions.Builder(outputFile).build()
                                imageCapture.takePicture(
                                    options,
                                    ContextCompat.getMainExecutor(context),
                                    object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(res: ImageCapture.OutputFileResults) {
                                            capturedPath = outputFile.path
                                        }
                                        override fun onError(e: ImageCaptureException) {
                                            cameraError = "Capture failed: ${e.message}"
                                        }
                                    }
                                )
                            },
                            modifier = Modifier
                                .size(84.dp)
                                .background(Color.White, CircleShape)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Capture", tint = Color.Black)
                        }
                        Box(modifier = Modifier.size(56.dp)) // balances the row
                    }
                    cameraError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            else -> {
                // Permission missing: graceful guidance, never a dead screen.
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Camera access is needed to photograph your document.",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "You can also cancel and choose an existing image instead.",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (permanentlyDenied) {
                        Button(onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    .setData(Uri.parse("package:${context.packageName}"))
                            )
                        }) { Text("Open Settings") }
                    }
                    TextButton(onClick = onCancelled) { Text("Cancel") }
                }
            }
        }
    }
}

private fun shouldShowRationale(context: android.content.Context): Boolean =
    (context as? Activity)?.let {
        androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
    } ?: false
