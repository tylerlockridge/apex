package com.healthplatform.sync.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.healthplatform.sync.ui.theme.*
import org.json.JSONObject
import java.util.concurrent.Executors

// ---------------------------------------------------------------------------
// QR onboarding screen
//
// Decodes a JSON QR code in the format:
//   {"serverUrl": "https://...", "apiKey": "...", "deviceSecret": "..."}
//
// Invokes [onConfigured] on successful scan. Caller is responsible for
// writing the values to storage and navigating back.
// ---------------------------------------------------------------------------

@Composable
fun QrScanScreen(
    onConfigured: (serverUrl: String, apiKey: String, deviceSecret: String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    var scanError by remember { mutableStateOf<String?>(null) }
    // Guard prevents the callback firing multiple times before the screen is popped.
    var consumed by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            hasPermission -> {
                CameraPreview(
                    onBarcodeDetected = { barcode ->
                        if (consumed) return@CameraPreview
                        val raw = barcode.rawValue ?: run {
                            scanError = "QR code is empty"
                            return@CameraPreview
                        }
                        try {
                            val json = JSONObject(raw)
                            val serverUrl = json.getString("serverUrl")
                            val apiKey = json.getString("apiKey")
                            val deviceSecret = json.getString("deviceSecret")
                            consumed = true
                            onConfigured(serverUrl, apiKey, deviceSecret)
                        } catch (_: Exception) {
                            scanError = "Invalid QR format — expected {serverUrl, apiKey, deviceSecret}"
                        }
                    }
                )

                // Scan frame overlay
                ScanFrameOverlay(
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            else -> {
                // Camera permission denied
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.QrCodeScanner,
                        contentDescription = null,
                        tint = ApexPrimary,
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        text = "Camera permission is required to scan QR codes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ApexPrimary,
                            contentColor = ApexOnPrimary
                        )
                    ) {
                        Text("Grant Permission")
                    }
                }
            }
        }

        // Back button (top-left, always visible)
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
            )
        }

        // Instruction text (above the scan frame when camera is active)
        if (hasPermission) {
            Text(
                text = "Point your camera at the\nApex setup QR code",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 72.dp, start = 32.dp, end = 32.dp)
            )
        }

        // Error message (bottom of screen)
        val error = scanError
        if (error != null) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                containerColor = ApexStatusRed,
                contentColor = Color.White,
                action = {
                    TextButton(onClick = { scanError = null }) {
                        Text("Dismiss", color = Color.White)
                    }
                }
            ) {
                Text(error, fontSize = 13.sp)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Camera preview with ML Kit barcode analysis
// ---------------------------------------------------------------------------

@Composable
private fun CameraPreview(onBarcodeDetected: (Barcode) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(cameraExecutor) {
        onDispose { cameraExecutor.shutdown() }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val scanner = BarcodeScanning.getClient()
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            processImageProxy(scanner, imageProxy, onBarcodeDetected)
                        }
                    }
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    android.util.Log.e("QrScanScreen", "Camera binding failed", e)
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

@OptIn(ExperimentalGetImage::class)
private fun processImageProxy(
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    imageProxy: ImageProxy,
    onDetected: (Barcode) -> Unit
) {
    val mediaImage = imageProxy.image ?: run { imageProxy.close(); return }
    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    scanner.process(image)
        .addOnSuccessListener { barcodes -> barcodes.firstOrNull()?.let(onDetected) }
        .addOnCompleteListener { imageProxy.close() }
}

// ---------------------------------------------------------------------------
// Scan frame — four corner brackets drawn with Canvas
// ---------------------------------------------------------------------------

@Composable
private fun ScanFrameOverlay(modifier: Modifier = Modifier) {
    val color = ApexPrimary
    Canvas(modifier = modifier.size(240.dp)) {
        val arm = 48.dp.toPx()       // length of each bracket arm
        val stroke = 5.dp.toPx()     // line thickness
        val cap = StrokeCap.Square

        // top-left
        drawLine(color, androidx.compose.ui.geometry.Offset(0f, arm), androidx.compose.ui.geometry.Offset(0f, 0f), stroke, cap)
        drawLine(color, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(arm, 0f), stroke, cap)
        // top-right
        drawLine(color, androidx.compose.ui.geometry.Offset(size.width, arm), androidx.compose.ui.geometry.Offset(size.width, 0f), stroke, cap)
        drawLine(color, androidx.compose.ui.geometry.Offset(size.width, 0f), androidx.compose.ui.geometry.Offset(size.width - arm, 0f), stroke, cap)
        // bottom-left
        drawLine(color, androidx.compose.ui.geometry.Offset(0f, size.height - arm), androidx.compose.ui.geometry.Offset(0f, size.height), stroke, cap)
        drawLine(color, androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(arm, size.height), stroke, cap)
        // bottom-right
        drawLine(color, androidx.compose.ui.geometry.Offset(size.width, size.height - arm), androidx.compose.ui.geometry.Offset(size.width, size.height), stroke, cap)
        drawLine(color, androidx.compose.ui.geometry.Offset(size.width, size.height), androidx.compose.ui.geometry.Offset(size.width - arm, size.height), stroke, cap)
    }
}
