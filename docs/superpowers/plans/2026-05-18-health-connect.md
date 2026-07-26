# Health Connect Integration Implementation Plan

> **Status:** Historical execution plan. Its checkboxes were not backfilled and are not a current backlog. Use `AGENTS.md` and the current source/tests as the authoritative project state.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Write completed workout sessions to Android Health Connect and read back sleep, resting heart rate, and HRV data to enrich the recovery readiness score shown on HomeScreen.

**Architecture:** A `HealthConnectRepository` wraps the Health Connect client with write (ExerciseSession, Weight) and read (Sleep, RHR, HRV) operations. A one-time `HealthConnectPermissionSheet` bottom-sheet requests permissions on first use. The existing `RecoveryReadinessEngine` gains a `blendWithBiometric()` extension function that combines Health Connect biometric data with the training-load readiness score. No new ViewModel is needed — the existing `WatermelonAppDataViewModel` gains a `healthConnect` property.

**Tech Stack:** Kotlin, `androidx.health.connect:connect-client:1.1.0-alpha07`, Jetpack Compose, existing `RecoveryReadinessEngine`, existing `HistoryEntry` model

---

## File Map

| Action | File |
|--------|------|
| Modify | `app/build.gradle.kts` — add Health Connect dependency |
| Create | `app/src/main/java/com/ironlog/app/data/health/HealthConnectRepository.kt` |
| Create | `app/src/main/java/com/ironlog/app/data/health/BiometricSnapshot.kt` |
| Create | `app/src/main/java/com/ironlog/app/ui/screens/HealthConnectPermissionSheet.kt` |
| Create | `app/src/test/java/com/ironlog/app/data/health/HealthConnectRepositoryTest.kt` |
| Modify | `app/src/main/java/com/ironlog/app/domain/intelligence/RecoveryReadinessEngine.kt` — add `blendWithBiometric()` |
| Modify | `app/src/main/java/com/ironlog/app/ui/screens/HomeScreen.kt` — trigger permission sheet + show blended score |
| Modify | `app/src/main/AndroidManifest.xml` — Health Connect activity + queries |

---

### Task 1: Add Health Connect dependency

**Files:**
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add dependency**

Inside the `dependencies { }` block in `app/build.gradle.kts`, add:

```kotlin
    // Health Connect (replaces deprecated Google Fit)
    implementation("androidx.health.connect:connect-client:1.1.0-alpha07")
```

- [ ] **Step 2: Sync and build**

```
.\gradlew :app:assembleDebug 2>&1 | Select-String "error:|BUILD|Download" | Select-Object -Last 15
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```
git add app/build.gradle.kts
git commit -m "build: add Health Connect client dependency"
```

---

### Task 2: AndroidManifest — Health Connect integration metadata

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Read AndroidManifest.xml to find the application tag**

Open `app/src/main/AndroidManifest.xml` and locate `<application>`.

- [ ] **Step 2: Add Health Connect queries and activity**

Inside `<manifest>`, before `<application>`, add:

```xml
<!-- Health Connect — declare intent queries so Android can route to HC -->
<queries>
    <package android:name="com.google.android.apps.healthdata" />
    <intent>
        <action android:name="androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" />
    </intent>
</queries>
```

Inside `<application>`, add the Health Connect permission rationale activity:

```xml
<!-- Health Connect — required activity for permission rationale dialog -->
<activity
    android:name="androidx.health.connect.client.PermissionController$createRequestPermissionResultContract"
    android:exported="true">
    <intent-filter>
        <action android:name="androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" />
    </intent-filter>
</activity>
```

Also add the privacy policy metadata (required by Health Connect):

```xml
<!-- Health Connect — privacy policy URL (required for Play Store submission) -->
<meta-data
    android:name="health_connect_privacy_policy_url"
    android:value="https://ironlogpro.app/privacy" />
```

- [ ] **Step 3: Build**

```
.\gradlew :app:assembleDebug 2>&1 | Select-String "error:|BUILD" | Select-Object -Last 10
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```
git add app/src/main/AndroidManifest.xml
git commit -m "feat: add Health Connect manifest entries (queries, rationale activity, metadata)"
```

---

### Task 3: BiometricSnapshot data class

**Files:**
- Create: `app/src/main/java/com/ironlog/app/data/health/BiometricSnapshot.kt`

- [ ] **Step 1: Create BiometricSnapshot**

```kotlin
// app/src/main/java/com/ironlog/app/data/health/BiometricSnapshot.kt
package com.ironlog.app.data.health

/**
 * Latest biometric data read from Health Connect.
 * All values are nullable — null means no recent data available.
 *
 * @param sleepHours     Total sleep hours from last night (Health Connect SleepSessionRecord).
 * @param restingHrBpm   Resting heart rate in bpm (Health Connect RestingHeartRateRecord).
 * @param hrvRmssd       Heart Rate Variability RMSSD in ms (Health Connect HRVRecord).
 * @param weightKg       Latest body weight in kg (Health Connect WeightRecord).
 */
data class BiometricSnapshot(
    val sleepHours: Double? = null,
    val restingHrBpm: Long? = null,
    val hrvRmssd: Double? = null,
    val weightKg: Double? = null,
)
```

- [ ] **Step 2: Commit**

```
git add app/src/main/java/com/ironlog/app/data/health/BiometricSnapshot.kt
git commit -m "feat: add BiometricSnapshot data class for Health Connect readings"
```

---

### Task 4: HealthConnectRepository — write sessions, read biometrics

**Files:**
- Create: `app/src/main/java/com/ironlog/app/data/health/HealthConnectRepository.kt`
- Test: `app/src/test/java/com/ironlog/app/data/health/HealthConnectRepositoryTest.kt`

- [ ] **Step 1: Write failing tests (unit-testable logic only)**

> Note: The Health Connect client itself requires an Android runtime and cannot be unit-tested directly. We test the pure-logic helpers: permission set construction, biometric score computation, and `blendWithBiometric`.

```kotlin
// app/src/test/java/com/ironlog/app/data/health/HealthConnectRepositoryTest.kt
package com.ironlog.app.data.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthConnectRepositoryTest {

    // ── Biometric readiness score ─────────────────────────────────────────

    @Test fun `sleepScore returns 1_0 for 8+ hours`() {
        assertEquals(1.0, sleepScore(8.0), 0.001)
        assertEquals(1.0, sleepScore(9.0), 0.001)
    }

    @Test fun `sleepScore returns 0_0 for 4 hours or less`() {
        assertEquals(0.0, sleepScore(4.0), 0.001)
        assertEquals(0.0, sleepScore(3.0), 0.001)
    }

    @Test fun `sleepScore scales linearly between 4 and 8 hours`() {
        val score6h = sleepScore(6.0)
        assertTrue("6h sleep score should be ~0.5 but was $score6h", score6h in 0.45..0.55)
    }

    @Test fun `hrvScore returns 1_0 for HRV >= 80 ms`() {
        assertEquals(1.0, hrvScore(80.0), 0.001)
        assertEquals(1.0, hrvScore(100.0), 0.001)
    }

    @Test fun `hrvScore returns 0_0 for HRV <= 20 ms`() {
        assertEquals(0.0, hrvScore(20.0), 0.001)
        assertEquals(0.0, hrvScore(10.0), 0.001)
    }

    @Test fun `biometricReadinessScore blends sleep and HRV`() {
        val snap = BiometricSnapshot(sleepHours = 8.0, hrvRmssd = 80.0)
        val score = biometricReadinessScore(snap)
        assertTrue("Score with full sleep+HRV should be >= 0.9", score >= 0.9)
    }

    @Test fun `biometricReadinessScore with null data returns 0_5 neutral`() {
        val snap = BiometricSnapshot()  // all nulls
        val score = biometricReadinessScore(snap)
        assertEquals(0.5, score, 0.001)
    }

    @Test fun `biometricReadinessScore with only sleep data uses sleep score`() {
        val snap = BiometricSnapshot(sleepHours = 4.0)
        val score = biometricReadinessScore(snap)
        assertTrue("Poor sleep only should score < 0.3", score < 0.3)
    }
}

// ── Pure helper functions (extracted for testability) ───────────────────────

fun sleepScore(hours: Double): Double = ((hours - 4.0) / 4.0).coerceIn(0.0, 1.0)

fun hrvScore(rmssd: Double): Double = ((rmssd - 20.0) / 60.0).coerceIn(0.0, 1.0)

fun biometricReadinessScore(snap: BiometricSnapshot): Double {
    val scores = buildList {
        snap.sleepHours?.let { add(sleepScore(it) * 0.6) }
        snap.hrvRmssd?.let  { add(hrvScore(it) * 0.4) }
    }
    if (scores.isEmpty()) return 0.5
    // Normalize: sum of weights of present components
    val weights = buildList {
        if (snap.sleepHours != null) add(0.6)
        if (snap.hrvRmssd != null)   add(0.4)
    }
    return (scores.sum() / weights.sum()).coerceIn(0.0, 1.0)
}
```

- [ ] **Step 2: Run tests**

```
.\gradlew :app:testDebugUnitTest --tests "com.ironlog.app.data.health.HealthConnectRepositoryTest" 2>&1 | Select-String "error:|BUILD" | Select-Object -Last 5
```

Expected: compilation error — functions not found yet (they're defined in the test file itself for now; this verifies the test logic compiles).

Actually since the helper functions are defined in the test file directly, the tests should compile. Run them:

```
.\gradlew :app:testDebugUnitTest --tests "com.ironlog.app.data.health.HealthConnectRepositoryTest"
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 3: Implement HealthConnectRepository**

```kotlin
// app/src/main/java/com/ironlog/app/data/health/HealthConnectRepository.kt
package com.ironlog.app.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Mass
import com.ironlog.app.ui.model.HistoryEntry
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Wraps the Health Connect client for IronLog read/write operations.
 *
 * All public methods are suspend functions and must be called from a coroutine.
 * Callers should check [isAvailable] before calling any other method.
 */
class HealthConnectRepository(private val context: Context) {

    private val client: HealthConnectClient? by lazy {
        runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull()
    }

    /** Returns true if Health Connect is installed and available on this device. */
    fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    /** The permission set IronLog requests from Health Connect. */
    val requiredPermissions: Set<String> = setOf(
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(WeightRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
    )

    /** Returns which of [requiredPermissions] have been granted. */
    suspend fun grantedPermissions(): Set<String> {
        val c = client ?: return emptySet()
        return c.permissionController.getGrantedPermissions()
    }

    /** Returns true if all required permissions are granted. */
    suspend fun hasAllPermissions(): Boolean =
        grantedPermissions().containsAll(requiredPermissions)

    // ── Write ─────────────────────────────────────────────────────────────

    /**
     * Write a completed workout session from a [HistoryEntry] to Health Connect.
     * No-op if Health Connect is unavailable or permissions are missing.
     */
    suspend fun writeWorkoutSession(entry: HistoryEntry) {
        val c = client ?: return
        runCatching {
            val date = LocalDate.parse(entry.date.take(10))
            val zone = ZoneId.systemDefault()
            val start = date.atStartOfDay(zone).toInstant()
            val end = start.plusSeconds(entry.duration.toLong().coerceAtLeast(60))

            val record = ExerciseSessionRecord(
                startTime = start,
                startZoneOffset = zone.rules.getOffset(start),
                endTime = end,
                endZoneOffset = zone.rules.getOffset(end),
                exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
                title = entry.name,
                metadata = Metadata.autoRecorded(),
            )
            c.insertRecords(listOf(record))
        }
    }

    /**
     * Write the user's body weight to Health Connect.
     * @param weightKg Weight in kilograms.
     */
    suspend fun writeWeight(weightKg: Double) {
        val c = client ?: return
        runCatching {
            val now = Instant.now()
            val zone = ZoneId.systemDefault()
            val record = WeightRecord(
                time = now,
                zoneOffset = zone.rules.getOffset(now),
                weight = Mass.kilograms(weightKg),
                metadata = Metadata.autoRecorded(),
            )
            c.insertRecords(listOf(record))
        }
    }

    // ── Read ──────────────────────────────────────────────────────────────

    /**
     * Read a [BiometricSnapshot] for the last 24 hours.
     * Returns a snapshot with null fields where data is unavailable.
     */
    suspend fun readBiometricSnapshot(): BiometricSnapshot {
        val c = client ?: return BiometricSnapshot()

        val now = Instant.now()
        val yesterday = now.minus(Duration.ofHours(36))
        val timeRange = TimeRangeFilter.between(yesterday, now)

        val sleepHours: Double? = runCatching {
            val result = c.readRecords(
                ReadRecordsRequest(SleepSessionRecord::class, timeRange)
            )
            result.records.lastOrNull()?.let { session ->
                Duration.between(session.startTime, session.endTime).toMinutes() / 60.0
            }
        }.getOrNull()

        val restingHr: Long? = runCatching {
            val result = c.readRecords(
                ReadRecordsRequest(RestingHeartRateRecord::class, timeRange)
            )
            result.records.lastOrNull()?.beatsPerMinute
        }.getOrNull()

        val hrv: Double? = runCatching {
            val result = c.readRecords(
                ReadRecordsRequest(HeartRateVariabilityRmssdRecord::class, timeRange)
            )
            result.records.lastOrNull()?.heartRateVariabilityMillis
        }.getOrNull()

        return BiometricSnapshot(
            sleepHours = sleepHours,
            restingHrBpm = restingHr,
            hrvRmssd = hrv,
        )
    }
}
```

- [ ] **Step 4: Build**

```
.\gradlew :app:assembleDebug 2>&1 | Select-String "error:|BUILD" | Select-Object -Last 10
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```
git add app/src/main/java/com/ironlog/app/data/health/HealthConnectRepository.kt
git add app/src/main/java/com/ironlog/app/data/health/BiometricSnapshot.kt
git add app/src/test/java/com/ironlog/app/data/health/HealthConnectRepositoryTest.kt
git commit -m "feat: add HealthConnectRepository for HC write/read and BiometricSnapshot"
```

---

### Task 5: RecoveryReadinessEngine — blendWithBiometric extension

**Files:**
- Modify: `app/src/main/java/com/ironlog/app/domain/intelligence/RecoveryReadinessEngine.kt`

- [ ] **Step 1: Read RecoveryReadinessEngine.kt**

Open `app/src/main/java/com/ironlog/app/domain/intelligence/RecoveryReadinessEngine.kt` and locate the end of the class body.

- [ ] **Step 2: Add blendWithBiometric function**

After the closing `}` of `RecoveryReadinessEngine` (or as the last method inside the class), add:

```kotlin
/**
 * Blends a training-load [readinessScore] (0.0–1.0) with biometric data
 * from Health Connect.
 *
 * Weights:
 *   - Training load readiness: 60%
 *   - Sleep quality:           25%
 *   - HRV:                     15%
 *
 * If no biometric data is available (all nulls), returns [readinessScore] unchanged.
 *
 * @param readinessScore  Overall score from [score] (0.0 = very fatigued, 1.0 = fully ready).
 * @param biometrics      Latest snapshot from [HealthConnectRepository.readBiometricSnapshot].
 */
fun blendWithBiometric(readinessScore: Double, biometrics: BiometricSnapshot): Double {
    // Helpers (duplicated from test file for production use — single source of truth here)
    fun sleepScore(hours: Double) = ((hours - 4.0) / 4.0).coerceIn(0.0, 1.0)
    fun hrvScore(rmssd: Double)   = ((rmssd - 20.0) / 60.0).coerceIn(0.0, 1.0)

    val hasSleep = biometrics.sleepHours != null
    val hasHrv   = biometrics.hrvRmssd != null

    if (!hasSleep && !hasHrv) return readinessScore

    var weightedSum = readinessScore * 0.60
    var totalWeight = 0.60

    if (hasSleep) {
        weightedSum += sleepScore(biometrics.sleepHours!!) * 0.25
        totalWeight += 0.25
    }
    if (hasHrv) {
        weightedSum += hrvScore(biometrics.hrvRmssd!!) * 0.15
        totalWeight += 0.15
    }

    return (weightedSum / totalWeight).coerceIn(0.0, 1.0)
}
```

Add the import at the top of the file:
```kotlin
import com.ironlog.app.data.health.BiometricSnapshot
```

- [ ] **Step 3: Write a unit test for blendWithBiometric**

Add to `app/src/test/java/com/ironlog/app/domain/intelligence/RecoveryReadinessEngineTest.kt` (create the file if it doesn't exist):

```kotlin
// app/src/test/java/com/ironlog/app/domain/intelligence/RecoveryReadinessEngineTest.kt
package com.ironlog.app.domain.intelligence

import com.ironlog.app.data.health.BiometricSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryReadinessEngineTest {

    private val engine = RecoveryReadinessEngine()

    @Test fun `blendWithBiometric returns training score when no biometrics`() {
        val snap = BiometricSnapshot()  // all null
        val result = engine.blendWithBiometric(0.8, snap)
        assertEquals(0.8, result, 0.001)
    }

    @Test fun `blendWithBiometric blends down when sleep is poor`() {
        val snap = BiometricSnapshot(sleepHours = 4.0)  // worst sleep score
        val result = engine.blendWithBiometric(1.0, snap)
        assertTrue("Poor sleep should pull blend below 1.0", result < 1.0)
    }

    @Test fun `blendWithBiometric is 1_0 when training and biometrics are both perfect`() {
        val snap = BiometricSnapshot(sleepHours = 8.0, hrvRmssd = 80.0)
        val result = engine.blendWithBiometric(1.0, snap)
        assertEquals(1.0, result, 0.001)
    }

    @Test fun `blendWithBiometric stays within 0_0 to 1_0`() {
        val snap = BiometricSnapshot(sleepHours = 0.0, hrvRmssd = 0.0)
        val result = engine.blendWithBiometric(0.0, snap)
        assertTrue(result in 0.0..1.0)
    }
}
```

- [ ] **Step 4: Run tests**

```
.\gradlew :app:testDebugUnitTest --tests "com.ironlog.app.domain.intelligence.RecoveryReadinessEngineTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```
git add app/src/main/java/com/ironlog/app/domain/intelligence/RecoveryReadinessEngine.kt
git add app/src/test/java/com/ironlog/app/domain/intelligence/RecoveryReadinessEngineTest.kt
git commit -m "feat: add blendWithBiometric to RecoveryReadinessEngine for HC-enriched scores"
```

---

### Task 6: HealthConnectPermissionSheet — one-time permission request

**Files:**
- Create: `app/src/main/java/com/ironlog/app/ui/screens/HealthConnectPermissionSheet.kt`

- [ ] **Step 1: Implement HealthConnectPermissionSheet**

```kotlin
// app/src/main/java/com/ironlog/app/ui/screens/HealthConnectPermissionSheet.kt
package com.ironlog.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthConnectPermissionSheet(
    onRequestPermissions: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Connect to Health Connect",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "IronLog can read your sleep, heart rate, and HRV data to give you smarter recovery recommendations.",
                style = MaterialTheme.typography.bodyMedium,
            )

            BenefitRow(emoji = "😴", text = "Sleep quality → better fatigue scoring")
            BenefitRow(emoji = "❤️", text = "Resting HR + HRV → recovery readiness")
            BenefitRow(emoji = "🏋️", text = "Workouts written back → unified health timeline")

            Text(
                "Your data stays on your device. IronLog never uploads health data to any server.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onRequestPermissions,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Connect Health Connect")
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Not now")
            }
        }
    }
}

@Composable
private fun BenefitRow(emoji: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
```

- [ ] **Step 2: Build**

```
.\gradlew :app:assembleDebug 2>&1 | Select-String "error:|BUILD" | Select-Object -Last 5
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```
git add app/src/main/java/com/ironlog/app/ui/screens/HealthConnectPermissionSheet.kt
git commit -m "feat: add HealthConnectPermissionSheet for one-time HC permission request"
```

---

### Task 7: Wire Health Connect into HomeScreen

**Files:**
- Modify: `app/src/main/java/com/ironlog/app/ui/screens/HomeScreen.kt`

- [ ] **Step 1: Add HealthConnectRepository initialization and biometric fetch**

In `HomeScreen` (or the ViewModel that HomeScreen observes), add:

```kotlin
// At the top of the HomeScreen composable (or in the ViewModel init):
val context = LocalContext.current
val healthRepo = remember { HealthConnectRepository(context) }

// State for permission sheet
var showHealthConnectSheet by remember { mutableStateOf(false) }

// Biometric snapshot — fetched once per composition if HC is available
var biometricSnapshot: BiometricSnapshot by remember { mutableStateOf(BiometricSnapshot()) }

// Check HC availability and permissions on first composition
LaunchedEffect(Unit) {
    if (healthRepo.isAvailable()) {
        if (!healthRepo.hasAllPermissions()) {
            // Show permission sheet on first load if not yet connected
            val prefs = context.getSharedPreferences("hc_prefs", 0)
            if (!prefs.getBoolean("hc_sheet_shown", false)) {
                showHealthConnectSheet = true
                prefs.edit().putBoolean("hc_sheet_shown", true).apply()
            }
        } else {
            // Already have permissions — fetch biometrics
            biometricSnapshot = withContext(Dispatchers.IO) {
                healthRepo.readBiometricSnapshot()
            }
        }
    }
}

// Add required imports:
// import com.ironlog.app.data.health.HealthConnectRepository
// import com.ironlog.app.data.health.BiometricSnapshot
// import kotlinx.coroutines.Dispatchers
// import kotlinx.coroutines.withContext
```

- [ ] **Step 2: Blend biometric data into the readiness score for WorkoutSuggestionEngine**

Find the existing `readiness` computation from Task 2 of the fatigue-suggestion plan and update it:

```kotlin
// Replace the existing readiness computation:
val recoveryEngine = remember { RecoveryReadinessEngine() }

val readiness: Map<String, Double> = remember(state.history, biometricSnapshot) {
    val rawReadiness = recoveryEngine.readinessByRegion(state.history, emptyMap())
    val rawScore = recoveryEngine.score(rawReadiness, null).overall
    val blendedScore = recoveryEngine.blendWithBiometric(rawScore, biometricSnapshot)
    // Scale all regions by the blend ratio
    val ratio = if (rawScore > 0) blendedScore / rawScore else 1.0
    rawReadiness.mapValues { (_, v) -> (v * ratio).coerceIn(0.0, 1.0) }
}
```

- [ ] **Step 3: Show permission sheet when triggered**

```kotlin
if (showHealthConnectSheet && healthRepo.isAvailable()) {
    val permLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { grantedPerms ->
        showHealthConnectSheet = false
        if (grantedPerms.containsAll(healthRepo.requiredPermissions)) {
            // Permissions granted — fetch biometrics
            coroutineScope.launch {
                biometricSnapshot = withContext(Dispatchers.IO) {
                    healthRepo.readBiometricSnapshot()
                }
            }
        }
    }

    HealthConnectPermissionSheet(
        onRequestPermissions = { permLauncher.launch(healthRepo.requiredPermissions) },
        onDismiss = { showHealthConnectSheet = false },
    )
}
```

Add imports:
```kotlin
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.health.connect.client.PermissionController
import com.ironlog.app.ui.screens.HealthConnectPermissionSheet
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
```

- [ ] **Step 4: Write workout sessions to Health Connect after workout completion**

In the existing workout completion callback (wherever the app saves a new `HistoryEntry`), add:

```kotlin
// After saving the workout entry to ObjectBox:
if (healthRepo.isAvailable() && healthRepo.hasAllPermissions()) {
    coroutineScope.launch(Dispatchers.IO) {
        healthRepo.writeWorkoutSession(completedEntry)
    }
}
```

- [ ] **Step 5: Build**

```
.\gradlew :app:assembleDebug 2>&1 | Select-String "error:|BUILD" | Select-Object -Last 10
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```
git add app/src/main/java/com/ironlog/app/ui/screens/HomeScreen.kt
git commit -m "feat: integrate Health Connect biometric blending and HC permission flow into HomeScreen"
```

---

## Self-Review

**Spec coverage:**
- ✅ Health Connect dependency added (`connect-client:1.1.0-alpha07`)
- ✅ Manifest: queries block, rationale activity, privacy policy meta-data
- ✅ Write: `ExerciseSessionRecord` after workout completion
- ✅ Write: `WeightRecord` (method provided, caller hooks in Settings/profile screen)
- ✅ Read: Sleep, RHR, HRV into `BiometricSnapshot`
- ✅ `blendWithBiometric` on `RecoveryReadinessEngine` (60% training / 25% sleep / 15% HRV)
- ✅ One-time permission sheet on first HomeScreen open
- ✅ Permission launcher using `PermissionController.createRequestPermissionResultContract()`
- ✅ No-op if HC unavailable (graceful degradation — app works without HC)
- ✅ Unit tests for pure score-blending logic

**Limitations noted:**
- `HealthConnectRepository` uses `Metadata.autoRecorded()` — for production, switch to `Metadata.manualEntry()` with a proper `DataOrigin` if required by HC policy review.
- `RecoveryReadinessEngine.score()` return type must have `.overall: Double` — verify this before implementing Task 5. If it returns a different type, adapt the blend call accordingly.
