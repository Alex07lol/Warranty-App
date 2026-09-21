package com.warrantyvault.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.warrantyvault.ui.theme.WarrantyVaultTheme
import java.io.File

/**
 * Real CameraX document capture: live preview -> capture -> shot preview -> retake/accept.
 * Returns the captured JPEG path via the activity result.
 */
class CameraCaptureActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OUTPUT_PATH = "extra_output_path"
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val outputDir = cacheDir.resolve("captures").apply { mkdirs() }
        val outputFile = File.createTempFile("capture_", ".jpg", outputDir)

        setContent {
            WarrantyVaultTheme {
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

    override fun onStart() {
        super.onStart()
        permissionLauncher.launch(android.Manifest.permission.CAMERA)
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
    var hasPermission by remember { mutableStateOf(false) }

    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) { requestPermission.launch(android.Manifest.permission.CAMERA) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (capturedPath == null) {
            if (hasPermission) {
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
            }

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
                            if (!hasPermission) return@IconButton
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
        } else {
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
    }
}
