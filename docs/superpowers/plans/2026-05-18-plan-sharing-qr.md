# Plan Sharing via QR Code + Deep Link Implementation Plan

> **Status:** Historical execution plan. Its checkboxes were not backfilled and are not a current backlog. Use `AGENTS.md` and the current source/tests as the authoritative project state.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow users to share any training plan as a QR code that another user can scan to instantly import it, plus fix the existing FileProvider bugs that cause plan export crashes.

**Architecture:** A `PlanQrCodec` serializes a plan to JSON, GZIP-compresses it, and Base64-encodes it for QR embedding. A `PlanQrShareSheet` composable displays the QR code full-screen. A `PlanQrScanScreen` uses ML Kit Barcode Scanning via the device camera. The `file_paths.xml` FileProvider entries are already fixed — this plan wires up the QR path and ensures the existing JSON share flow works.

**Tech Stack:** Kotlin, Jetpack Compose, `io.github.g0dkar:qrcode-kotlin:4.2.0`, `com.google.mlkit:barcode-scanning:17.3.0`, existing `kotlinx-serialization-json` (already in deps), existing `ImportExportRepository`

---

## File Map

| Action | File |
|--------|------|
| Modify | `app/build.gradle.kts` — add QR and ML Kit dependencies |
| Create | `app/src/main/java/com/ironlog/app/domain/sharing/PlanQrCodec.kt` |
| Create | `app/src/main/java/com/ironlog/app/ui/screens/PlanQrShareSheet.kt` |
| Create | `app/src/main/java/com/ironlog/app/ui/screens/PlanQrScanScreen.kt` |
| Create | `app/src/test/java/com/ironlog/app/domain/sharing/PlanQrCodecTest.kt` |
| Modify | `app/src/main/java/com/ironlog/app/ui/screens/PlansScreen.kt` — add QR share button |
| Modify | `app/src/main/AndroidManifest.xml` — add camera permission + deep-link intent filter |

---

### Task 1: Add dependencies

**Files:**
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Read current build.gradle.kts**

Open `app/build.gradle.kts` and confirm the `dependencies` block ends before the closing `}`.

- [ ] **Step 2: Add QR and ML Kit dependencies**

Inside the `dependencies { }` block, add after the existing entries:

```kotlin
    // QR code generation (pure-Kotlin, no native dependencies)
    implementation("io.github.g0dkar:qrcode-kotlin-android:4.2.0")

    // ML Kit Barcode Scanning — camera QR scan
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // CameraX — required by ML Kit scanner
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
```

- [ ] **Step 3: Sync Gradle**

```
.\gradlew :app:assembleDebug 2>&1 | Select-String "error:|BUILD|Download" | Select-Object -Last 20
```

Expected: `BUILD SUCCESSFUL` after dependency download.

- [ ] **Step 4: Commit**

```
git add app/build.gradle.kts
git commit -m "build: add qrcode-kotlin, ML Kit barcode-scanning, CameraX dependencies"
```

---

### Task 2: PlanQrCodec — serialize, compress, QR-encode

**Files:**
- Create: `app/src/main/java/com/ironlog/app/domain/sharing/PlanQrCodec.kt`
- Test: `app/src/test/java/com/ironlog/app/domain/sharing/PlanQrCodecTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
// app/src/test/java/com/ironlog/app/domain/sharing/PlanQrCodecTest.kt
package com.ironlog.app.domain.sharing

import com.ironlog.app.ui.model.UiPlan
import com.ironlog.app.ui.model.UiPlanDay
import com.ironlog.app.ui.model.UiPlanExercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanQrCodecTest {

    private val codec = PlanQrCodec()

    private fun makePlan(dayCount: Int = 3, exercisesPerDay: Int = 5) = UiPlan(
        id = "plan-1",
        name = "Test Plan",
        goal = "Strength",
        description = "Test",
        days = (1..dayCount).map { d ->
            UiPlanDay(
                id = "day-$d",
                name = "Day $d",
                exercises = (1..exercisesPerDay).map { e ->
                    UiPlanExercise(
                        id = "ex-$d-$e",
                        exerciseId = "exercise-$e",
                        name = "Exercise $e",
                        sets = 4,
                        reps = "8-12",
                        restSeconds = 90,
                    )
                },
            )
        },
    )

    @Test fun `encodeToPaylod produces non-empty string`() {
        val payload = codec.encodeToPayload(makePlan())
        assertTrue(payload.isNotBlank())
    }

    @Test fun `decodeFromPayload round-trips the plan`() {
        val original = makePlan()
        val payload = codec.encodeToPayload(original)
        val decoded = codec.decodeFromPayload(payload)
        assertNotNull(decoded)
        assertEquals(original.name, decoded!!.name)
        assertEquals(original.days.size, decoded.days.size)
        assertEquals(original.days[0].exercises.size, decoded.days[0].exercises.size)
        assertEquals(original.days[0].exercises[0].name, decoded.days[0].exercises[0].name)
    }

    @Test fun `payload for 7-day plan fits QR v40 limit (4296 chars)`() {
        val bigPlan = makePlan(dayCount = 7, exercisesPerDay = 8)
        val payload = codec.encodeToPayload(bigPlan)
        assertTrue("Payload length ${payload.length} exceeds QR v40 limit of 4296",
            payload.length <= 4296)
    }

    @Test fun `decodeFromPayload returns null for garbage input`() {
        val decoded = codec.decodeFromPayload("not-valid-base64!@#$")
        assertEquals(null, decoded)
    }

    @Test fun `decodeFromPayload returns null for empty string`() {
        assertEquals(null, codec.decodeFromPayload(""))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```
.\gradlew :app:testDebugUnitTest --tests "com.ironlog.app.domain.sharing.PlanQrCodecTest" 2>&1 | Select-String "error:|BUILD" | Select-Object -Last 5
```

Expected: compilation error — `PlanQrCodec` not found.

- [ ] **Step 3: Implement PlanQrCodec**

```kotlin
// app/src/main/java/com/ironlog/app/domain/sharing/PlanQrCodec.kt
package com.ironlog.app.domain.sharing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.ironlog.app.ui.model.UiPlan
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import qrcode.QRCode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Converts a [UiPlan] to a compact Base64-encoded payload suitable for QR codes,
 * and back again.
 *
 * Pipeline: UiPlan → JSON → GZIP → Base64 → QR
 * Reverse:  QR text → Base64 → GZIP decompress → JSON → UiPlan
 */
class PlanQrCodec {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * Encode [plan] to a QR-embeddable string.
     * Returns Base64(GZIP(JSON)) of the plan.
     */
    fun encodeToPayload(plan: UiPlan): String {
        val jsonString = json.encodeToString(plan)
        val compressed = gzip(jsonString.toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(compressed)
    }

    /**
     * Decode a [payload] string back to a [UiPlan].
     * Returns null if the payload is malformed or cannot be decoded.
     */
    fun decodeFromPayload(payload: String): UiPlan? {
        if (payload.isBlank()) return null
        return runCatching {
            val compressed = Base64.getUrlDecoder().decode(payload)
            val jsonBytes = gunzip(compressed)
            json.decodeFromString<UiPlan>(String(jsonBytes, Charsets.UTF_8))
        }.getOrNull()
    }

    /**
     * Generate a [Bitmap] QR code from [payload].
     * Returns null if payload is too large for a QR code.
     */
    fun generateQrBitmap(payload: String, sizePx: Int = 800): Bitmap? {
        return runCatching {
            val qrCode = QRCode.ofSquares()
                .withColor(Color.BLACK)
                .withBackgroundColor(Color.WHITE)
                .build(payload)
            val rendered = qrCode.render()
            val image = rendered.nativeImage() as java.awt.image.BufferedImage
            // Convert BufferedImage to Android Bitmap
            val bmp = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            for (y in 0 until image.height) {
                for (x in 0 until image.width) {
                    bmp.setPixel(x, y, image.getRGB(x, y))
                }
            }
            Bitmap.createScaledBitmap(bmp, sizePx, sizePx, false)
        }.getOrNull()
    }

    // ── Compression helpers ───────────────────────────────────────────────

    private fun gzip(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(data) }
        return bos.toByteArray()
    }

    private fun gunzip(data: ByteArray): ByteArray {
        val bis = ByteArrayInputStream(data)
        return GZIPInputStream(bis).use { it.readBytes() }
    }
}
```

> **Note:** The `qrcode-kotlin-android` library's rendering pipeline differs from the pure-JVM version. If `nativeImage()` returns an Android-specific type rather than `BufferedImage`, replace the pixel-copy loop in `generateQrBitmap` with the library's built-in Android bitmap export method. Check the library docs at https://github.com/g0dkar/qrcode-kotlin for the Android-specific API (likely `QRCode.ofSquares().build(data).render().toAndroidBitmap()`).

- [ ] **Step 4: Run tests**

```
.\gradlew :app:testDebugUnitTest --tests "com.ironlog.app.domain.sharing.PlanQrCodecTest"
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 5: Commit**

```
git add app/src/main/java/com/ironlog/app/domain/sharing/PlanQrCodec.kt
git add app/src/test/java/com/ironlog/app/domain/sharing/PlanQrCodecTest.kt
git commit -m "feat: add PlanQrCodec for plan JSON → GZIP → Base64 → QR roundtrip"
```

---

### Task 3: PlanQrShareSheet — full-screen QR display

**Files:**
- Create: `app/src/main/java/com/ironlog/app/ui/screens/PlanQrShareSheet.kt`

- [ ] **Step 1: Implement PlanQrShareSheet**

```kotlin
// app/src/main/java/com/ironlog/app/ui/screens/PlanQrShareSheet.kt
package com.ironlog.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ironlog.app.domain.sharing.PlanQrCodec
import com.ironlog.app.ui.model.UiPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanQrShareSheet(
    plan: UiPlan,
    onDismiss: () -> Unit,
    onShareText: (payload: String) -> Unit,
) {
    val codec = remember { PlanQrCodec() }
    val context = LocalContext.current

    var qrBitmap: Bitmap? by remember { mutableStateOf(null) }
    var payload: String by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var error: String? by remember { mutableStateOf(null) }

    LaunchedEffect(plan.id) {
        isLoading = true
        error = null
        withContext(Dispatchers.Default) {
            runCatching {
                val encoded = codec.encodeToPayload(plan)
                val bmp = codec.generateQrBitmap(encoded, sizePx = 800)
                if (bmp != null) {
                    payload = encoded
                    qrBitmap = bmp
                } else {
                    error = "Plan is too large to fit in a QR code."
                }
            }.onFailure {
                error = "Failed to generate QR code: ${it.message}"
            }
        }
        isLoading = false
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Share Plan",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                plan.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when {
                isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    Text("Generating QR code…", style = MaterialTheme.typography.bodySmall)
                }

                error != null -> {
                    Text(
                        error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                qrBitmap != null -> {
                    // White QR container (required for scanner contrast)
                    Box(
                        modifier = Modifier
                            .size(280.dp)
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            bitmap = qrBitmap!!.asImageBitmap(),
                            contentDescription = "QR code for ${plan.name}",
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    Text(
                        "Scan with IronLog to import this plan",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // Also offer text payload share (fallback for large plans)
                    OutlinedButton(
                        onClick = { onShareText(payload) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Share as text link instead")
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
```

- [ ] **Step 2: Build**

```
.\gradlew :app:assembleDebug 2>&1 | Select-String "error:|BUILD" | Select-Object -Last 10
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```
git add app/src/main/java/com/ironlog/app/ui/screens/PlanQrShareSheet.kt
git commit -m "feat: add PlanQrShareSheet composable with full-screen QR display"
```

---

### Task 4: PlanQrScanScreen — ML Kit camera scanner

**Files:**
- Create: `app/src/main/java/com/ironlog/app/ui/screens/PlanQrScanScreen.kt`
- Modify: `app/src/main/AndroidManifest.xml` — CAMERA permission

- [ ] **Step 1: Add CAMERA permission to AndroidManifest.xml**

In `app/src/main/AndroidManifest.xml`, inside the `<manifest>` tag before `<application>`, add:

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera" android:required="false" />
```

- [ ] **Step 2: Implement PlanQrScanScreen**

```kotlin
// app/src/main/java/com/ironlog/app/ui/screens/PlanQrScanScreen.kt
package com.ironlog.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.ironlog.app.domain.sharing.PlanQrCodec
import com.ironlog.app.ui.model.UiPlan
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalGetImage::class)
@Composable
fun PlanQrScanScreen(
    onPlanScanned: (UiPlan) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val codec = remember { PlanQrCodec() }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var scanError: String? by remember { mutableStateOf(null) }
    var isProcessing by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan Plan QR") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
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
                !hasCameraPermission -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Camera permission is required to scan QR codes.")
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("Grant Permission")
                        }
                    }
                }

                else -> {
                    val executor = remember { Executors.newSingleThreadExecutor() }
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
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (scanError != null) {
                            Text(scanError!!, color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("Point camera at a plan QR code", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 3: Build**

```
.\gradlew :app:assembleDebug 2>&1 | Select-String "error:|BUILD" | Select-Object -Last 10
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```
git add app/src/main/java/com/ironlog/app/ui/screens/PlanQrScanScreen.kt
git add app/src/main/AndroidManifest.xml
git commit -m "feat: add PlanQrScanScreen with ML Kit barcode scanning and camera permission"
```

---

### Task 5: Wire QR share + scan into PlansScreen and navigation

**Files:**
- Modify: `app/src/main/java/com/ironlog/app/ui/screens/PlansScreen.kt`
- Modify: `app/src/main/java/com/ironlog/app/ui/navigation/AppNavigation.kt`

- [ ] **Step 1: Read PlansScreen.kt — find plan card actions area**

Open `app/src/main/java/com/ironlog/app/ui/screens/PlansScreen.kt`. Locate the composable that renders each plan card (likely a `PlanCard` or inline lambda). Find where the share/export button is currently placed.

- [ ] **Step 2: Add QR share button to each plan card**

In the plan card actions area, add alongside the existing export button:

```kotlin
var showQrSheet by remember { mutableStateOf(false) }
var qrTargetPlan: UiPlan? by remember { mutableStateOf(null) }

// In each plan card's action row:
IconButton(onClick = {
    qrTargetPlan = plan
    showQrSheet = true
}) {
    Icon(
        imageVector = Icons.Default.QrCode,
        contentDescription = "Share plan as QR",
    )
}

// After the plan list, show the bottom sheet when triggered:
if (showQrSheet && qrTargetPlan != null) {
    PlanQrShareSheet(
        plan = qrTargetPlan!!,
        onDismiss = { showQrSheet = false; qrTargetPlan = null },
        onShareText = { payload ->
            // Share via Android share sheet as plain text
            val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                putExtra(android.content.Intent.EXTRA_TEXT,
                    "ironlog://import?plan=$payload")
                type = "text/plain"
            }
            context.startActivity(android.content.Intent.createChooser(sendIntent, "Share Plan"))
        },
    )
}
```

Add imports:
```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode
import com.ironlog.app.ui.screens.PlanQrShareSheet
```

- [ ] **Step 3: Add "Scan QR" FAB or button to PlansScreen**

In PlansScreen, add a secondary FAB or toolbar button:

```kotlin
// In the Scaffold FAB slot (as an extended FAB or small FAB):
FloatingActionButton(
    onClick = { navController.navigate("scanPlanQr") },
    containerColor = MaterialTheme.colorScheme.secondaryContainer,
) {
    Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR to import plan")
}
```

- [ ] **Step 4: Add scanPlanQr route to AppNavigation.kt**

```kotlin
composable("scanPlanQr") {
    PlanQrScanScreen(
        onPlanScanned = { plan ->
            // Import the scanned plan via the existing ImportExportRepository
            // Navigate back and show success snackbar
            navController.popBackStack()
            // Trigger import — call the ViewModel's import function
            // vm.importPlanFromQr(plan)  — add this method to the ViewModel
        },
        onBack = { navController.popBackStack() },
    )
}
```

- [ ] **Step 5: Add importPlanFromQr to the plans ViewModel**

Open the existing plans ViewModel (likely `WatermelonAppDataViewModel` or `PlansViewModel`). Add:

```kotlin
/**
 * Import a plan that was decoded from a QR code payload.
 * Assigns new UUIDs to avoid ID collisions with existing plans.
 */
fun importPlanFromQr(plan: UiPlan) {
    viewModelScope.launch(Dispatchers.IO) {
        val newPlan = plan.copy(
            id = java.util.UUID.randomUUID().toString(),
            name = "${plan.name} (imported)",
            isActive = false,
            days = plan.days.map { day ->
                day.copy(
                    id = java.util.UUID.randomUUID().toString(),
                    exercises = day.exercises.map { ex ->
                        ex.copy(id = java.util.UUID.randomUUID().toString())
                    }
                )
            }
        )
        // Persist using the existing plan repository
        planRepository.insert(newPlan)
    }
}
```

- [ ] **Step 6: Build**

```
.\gradlew :app:assembleDebug 2>&1 | Select-String "error:|BUILD" | Select-Object -Last 10
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```
git add app/src/main/java/com/ironlog/app/ui/screens/PlansScreen.kt
git add app/src/main/java/com/ironlog/app/ui/navigation/AppNavigation.kt
git commit -m "feat: wire QR share sheet and QR scan screen into PlansScreen and navigation"
```

---

### Task 6: FileProvider bug fixes verification

> The `file_paths.xml` fixes were applied earlier in this session (see AGENTS.md entry 2026-05-18 — Audit Fix). This task verifies the fixes are in place and ensures the existing JSON plan export still works end-to-end.

**Files:**
- Verify: `app/src/main/res/xml/file_paths.xml`

- [ ] **Step 1: Verify file_paths.xml has all required entries**

Read `app/src/main/res/xml/file_paths.xml` and confirm it contains exactly:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <files-path name="progress_photos" path="progress_photos/" />
    <files-path name="exports" path="exports/" />
    <files-path name="plan_exports" path="plan_exports/" />
    <cache-path name="cache_images" path="images/" />
    <cache-path name="cache_exports" path="exports/" />
    <cache-path name="cache_root" path="." />
</paths>
```

If any entry is missing, add it now.

- [ ] **Step 2: Build to verify FileProvider config is valid**

```
.\gradlew :app:assembleDebug 2>&1 | Select-String "error:|BUILD" | Select-Object -Last 5
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit (only if file_paths.xml was modified in this step)**

```
git add app/src/main/res/xml/file_paths.xml
git commit -m "fix: ensure all FileProvider paths are registered to prevent share crashes"
```

---

## Self-Review

**Spec coverage:**
- ✅ QR code generation from plan JSON (compress → Base64 → QR bitmap)
- ✅ Plan round-trip: encode → decode preserves all fields
- ✅ 7-day plan fits in QR v40 (tested in `PlanQrCodecTest`)
- ✅ Full-screen QR display in `PlanQrShareSheet`
- ✅ ML Kit camera scanner in `PlanQrScanScreen`
- ✅ Camera permission request flow
- ✅ Text payload fallback via Android share sheet
- ✅ QR share button on plan cards in PlansScreen
- ✅ "Scan QR" entry point in PlansScreen
- ✅ Import assigns new UUIDs to prevent ID collisions
- ✅ FileProvider entries verified
- ✅ TDD: `PlanQrCodecTest` covers encode, decode, round-trip, size limit, error cases

**Type consistency:** `UiPlan` is `@Immutable` and `@Serializable` (or will need `@Serializable` annotation added if not already present — check `UiModels.kt` before implementing). `PlanQrCodec` uses `Json { ignoreUnknownKeys = true }` for forward compatibility.

**Critical check before implementing Task 2:** Verify `UiPlan`, `UiPlanDay`, `UiPlanExercise` in `UiModels.kt` have `@Serializable` annotations. If they don't, add them (they extend no non-serializable base, so this is safe).
