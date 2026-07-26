package com.ironlog.app.ui.screens.plans

import android.Manifest
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.ironlog.app.domain.sharing.PlanQrCodec
import com.ironlog.app.ui.model.UiPlan
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalGetImage::class, ExperimentalPermissionsApi::class)
@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
fun PlanQrScanScreen(
    onPlanScanned: (UiPlan) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val codec = remember { PlanQrCodec() }

    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    var scanError: String? by remember { mutableStateOf(null) }

    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) cameraPermission.launchPermissionRequest()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan Plan QR") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when {
                !cameraPermission.status.isGranted -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (cameraPermission.status.shouldShowRationale) {
                            // User denied once but system dialog can still appear
                            Text("Camera access is needed to scan QR codes.")
                            Button(onClick = { cameraPermission.launchPermissionRequest() }) {
                                Text("Grant Permission")
                            }
                        } else {
                            // First-time (LaunchedEffect already fired) or permanently denied
                            Text("Camera permission is required. Please enable it in Settings.")
                            Button(onClick = {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            }) {
                                Text("Open Settings")
                            }
                        }
                    }
                }

                else -> {
                    val executor = remember { Executors.newSingleThreadExecutor() }
                    DisposableEffect(Unit) { onDispose { executor.shutdownNow() } }
                    var scanned by remember { mutableStateOf(false) }

                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx)
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                            cameraProviderFuture.addListener({
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }
                                val barcodeScanner = BarcodeScanning.getClient()
                                val analysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                    .also { imageAnalysis ->
                                        imageAnalysis.setAnalyzer(executor) { imageProxy ->
                                            if (scanned) {
                                                imageProxy.close()
                                                return@setAnalyzer
                                            }
                                            val mediaImage = imageProxy.image
                                            if (mediaImage != null) {
                                                val img = InputImage.fromMediaImage(
                                                    mediaImage, imageProxy.imageInfo.rotationDegrees
                                                )
                                                barcodeScanner.process(img)
                                                    .addOnSuccessListener { barcodes ->
                                                        barcodes.firstOrNull { it.format == Barcode.FORMAT_QR_CODE }
                                                            ?.rawValue
                                                            ?.let { raw ->
                                                                val plan = codec.decodeFromPayload(raw)
                                                                if (plan != null && !scanned) {
                                                                    scanned = true
                                                                    onPlanScanned(plan)
                                                                } else if (plan == null) {
                                                                    scanError = "QR code is not an IronLog plan."
                                                                }
                                                            }
                                                    }
                                                    .addOnCompleteListener { imageProxy.close() }
                                            } else {
                                                imageProxy.close()
                                            }
                                        }
                                    }

                                runCatching {
                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        analysis,
                                    )
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                            previewView
                        },
                        modifier = Modifier.fillMaxSize(),
                    )

                    // Scan frame overlay
                    Box(
                        modifier = Modifier
                            .size(250.dp)
                            .border(3.dp, Color.White, RoundedCornerShape(12.dp))
                    )

                    // Status text overlay at bottom
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.50f))
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (scanError != null) {
                            Text(scanError!!, color = MaterialTheme.colorScheme.error, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        } else {
                            Text("Point camera at a plan QR code", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

