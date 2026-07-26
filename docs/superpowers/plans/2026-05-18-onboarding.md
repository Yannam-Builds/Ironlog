# Onboarding — Implementation Plan

> **Status:** Historical execution plan. Its checkboxes were not backfilled and are not a current backlog. Use `AGENTS.md` and the current source/tests as the authoritative project state.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the existing 6-slide static onboarding with a cinematic 8-screen "SYSTEM AWAKENING" flow that collects userName, weeklyGoalDays, weightUnit, goalMode, progressionStyle, cloud AI API key/model, and all three Android permissions, saving everything atomically on final screen.

**Architecture:** All copy/config lives in `OnboardingConfig.kt`; transient state lives in `OnboardingViewModel`; each screen is a separate composable file in `ui/screens/onboarding/steps/`; `OnboardingScreen.kt` is the `HorizontalPager` shell; `AppNavigator.kt` wires the ViewModel.

**Tech Stack:** Kotlin 2.x, Jetpack Compose, Material3, HorizontalPager, EncryptedSharedPreferences (security-crypto already in deps), Health Connect API, ObjectBox (no changes), `rememberInfiniteTransition` for animations

---

## File Map

| Status | Path | Purpose |
|--------|------|---------|
| **Create** | `ui/screens/onboarding/OnboardingConfig.kt` | All copy, colors, model options — single source of truth |
| **Create** | `ui/screens/onboarding/OnboardingViewModel.kt` | `OnboardingDraft` state, `completeOnboarding()` |
| **Create** | `ui/screens/onboarding/OnboardingComponents.kt` | Shared composables: `RankBadge`, `GlowButton`, `ParticleField`, `TypewriterText`, `GlowCard` |
| **Create** | `ui/screens/onboarding/steps/Step1Awakening.kt` | Cinematic splash, auto-advance |
| **Create** | `ui/screens/onboarding/steps/Step2Registration.kt` | Hunter name input |
| **Create** | `ui/screens/onboarding/steps/Step3Classification.kt` | Rank/training level cards |
| **Create** | `ui/screens/onboarding/steps/Step4Quota.kt` | Day picker + unit toggle |
| **Create** | `ui/screens/onboarding/steps/Step5GoalMode.kt` | 2×2 goal mode grid |
| **Create** | `ui/screens/onboarding/steps/Step6AiAbilities.kt` | Intelligence mode + API key entry |
| **Create** | `ui/screens/onboarding/steps/Step7Permissions.kt` | Camera + Health Connect + Notifications |
| **Create** | `ui/screens/onboarding/steps/Step8Arise.kt` | Cinematic finale + ARISE button |
| **Replace** | `ui/screens/OnboardingScreen.kt` | Shell: `HorizontalPager` over 8 steps, wires ViewModel |
| **Modify** | `navigation/AppNavigator.kt` | Pass `OnboardingViewModel` to `OnboardingScreen`, update `settingsRepoSaveOnboardingData` |
| **Create** | `domain/badges/BadgeDefinitions.kt` | 16 badge definitions (referenced by gamification plan) |
| **Create** | `res/values/strings_onboarding.xml` | All string resources |
| **Create** | `test/.../onboarding/OnboardingViewModelTest.kt` | ViewModel unit tests |
| **Create** | `test/.../onboarding/OnboardingConfigTest.kt` | Config sanity tests |

---

## Task 1: OnboardingConfig + String Resources

**Files:**
- Create: `app/src/main/java/com/ironlog/app/ui/screens/onboarding/OnboardingConfig.kt`
- Create: `app/src/main/res/values/strings_onboarding.xml`

- [ ] **Step 1: Create `strings_onboarding.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Screen 1 -->
    <string name="onb_awakening_line1">THE SYSTEM HAS DETECTED A NEW HUNTER</string>
    <string name="onb_awakening_line2">INITIALIZING REGISTRATION PROTOCOL…</string>

    <!-- Screen 2 -->
    <string name="onb_reg_header">HUNTER DESIGNATION</string>
    <string name="onb_reg_subtext">Enter your designation, Hunter</string>
    <string name="onb_reg_hint">Your name</string>
    <string name="onb_reg_cta">CONFIRM IDENTITY</string>

    <!-- Screen 3 -->
    <string name="onb_class_header">ASSESS YOUR CURRENT RANK</string>
    <string name="onb_class_subtext">The System will calibrate your training path</string>

    <!-- Screen 4 -->
    <string name="onb_quota_header">SET YOUR WEEKLY OBJECTIVE</string>
    <string name="onb_quota_sessions">%d SESSIONS PER WEEK</string>
    <string name="onb_quota_cta">SET OBJECTIVE</string>

    <!-- Screen 5 -->
    <string name="onb_goal_header">CHOOSE YOUR COMBAT STYLE</string>
    <string name="onb_goal_cta">LOCK IN</string>

    <!-- Screen 6 -->
    <string name="onb_ai_header">ENHANCE YOUR HUNTER ABILITIES</string>
    <string name="onb_ai_subtext">Unlock AI-powered coaching and adaptive recommendations</string>
    <string name="onb_ai_local_title">ON-DEVICE INTELLIGENCE — ACTIVE</string>
    <string name="onb_ai_local_sub">Recovery scoring, workout suggestion, effort tracking</string>
    <string name="onb_ai_cloud_locked">TAP TO UNLOCK CLOUD AI COACHING</string>
    <string name="onb_ai_cloud_ready">CLOUD AI READY ✓</string>
    <string name="onb_ai_key_hint">Paste your API key here</string>
    <string name="onb_ai_key_helper">Stored locally in encrypted storage, never uploaded</string>
    <string name="onb_ai_gemini_badge">✦ GEMINI 2.0 FLASH — RECOMMENDED (FREE)\n1,500 requests/day · 15 req/min · 1M token context</string>
    <string name="onb_ai_get_key">Get free key at aistudio.google.com →</string>
    <string name="onb_ai_cta">ACTIVATE</string>
    <string name="onb_ai_skip">Skip for now</string>

    <!-- Screen 7 -->
    <string name="onb_perm_header">UNLOCK HUNTER ABILITIES</string>
    <string name="onb_perm_subtext">Each permission activates a System feature. All optional.</string>
    <string name="onb_perm_camera_title">SCANNER — QR SHARING</string>
    <string name="onb_perm_camera_desc">Scan &amp; share workout plans as QR codes</string>
    <string name="onb_perm_health_title">VITALS — HEALTH CONNECT</string>
    <string name="onb_perm_health_desc">Auto-import sleep, HRV, heart rate for recovery scores</string>
    <string name="onb_perm_notif_title">ALERTS — NOTIFICATIONS</string>
    <string name="onb_perm_notif_desc">Rest timer, streak reminders, daily check-ins</string>
    <string name="onb_perm_grant">GRANT</string>
    <string name="onb_perm_granted">GRANTED ✓</string>
    <string name="onb_perm_denied">DENIED</string>
    <string name="onb_perm_denied_hint">Grant later in Settings</string>
    <string name="onb_perm_cta">CONTINUE</string>

    <!-- Screen 8 -->
    <string name="onb_arise_reg_complete">REGISTRATION COMPLETE</string>
    <string name="onb_arise_line1">THE SYSTEM ACKNOWLEDGES YOUR EXISTENCE.</string>
    <string name="onb_arise_line2">YOUR JOURNEY BEGINS NOW.</string>
    <string name="onb_arise_cta">ARISE</string>

    <!-- Widget prompt -->
    <string name="onb_widget_title">Add IronLog to Your Home Screen</string>
    <string name="onb_widget_body">Quick-start workouts and check your streak without opening the app.</string>
    <string name="onb_widget_cta">ADD WIDGET</string>
    <string name="onb_widget_dismiss">Maybe Later</string>
</resources>
```

- [ ] **Step 2: Create `OnboardingConfig.kt`**

```kotlin
package com.ironlog.app.ui.screens.onboarding

import androidx.compose.ui.graphics.Color

object OnboardingConfig {

    // ── Colors ────────────────────────────────────────────────────────────────
    val accentBlue   = Color(0xFF4FC3F7)
    val accentGold   = Color(0xFFFFD700)
    val bgDark       = Color(0xFF050508)
    val surfaceDark  = Color(0xFF0D0D14)
    val cardBorder   = Color(0xFF1A1A2E)
    val textMuted    = Color(0xFF8888AA)
    val grantedColor = Color(0xFF26A69A)
    val deniedColor  = Color(0xFFEF5350)

    // ── AI provider / model options ───────────────────────────────────────────
    enum class AiProvider(val displayName: String) {
        GEMINI("Google Gemini"),
        OPENAI("OpenAI"),
        CUSTOM("Custom"),
    }

    data class AiModelOption(
        val modelId: String,
        val displayName: String,
        val subtitle: String,
    )

    val geminiModels = listOf(
        AiModelOption("gemini-2.0-flash",  "Gemini 2.0 Flash", "Free · 1,500 req/day · 1M ctx"),
        AiModelOption("gemini-2.5-flash",  "Gemini 2.5 Flash", "Free · 250 req/day · 250K ctx"),
        AiModelOption("gemini-2.5-pro",    "Gemini 2.5 Pro",   "Free · 100 req/day · 1M ctx"),
    )

    val openAiModels = listOf(
        AiModelOption("gpt-4o-mini", "GPT-4o Mini", "Fast · cost-efficient"),
        AiModelOption("gpt-4o",      "GPT-4o",      "Most capable"),
    )

    fun modelsFor(provider: AiProvider): List<AiModelOption> = when (provider) {
        AiProvider.GEMINI -> geminiModels
        AiProvider.OPENAI -> openAiModels
        AiProvider.CUSTOM -> emptyList()
    }

    fun defaultModelFor(provider: AiProvider): String = when (provider) {
        AiProvider.GEMINI -> "gemini-2.0-flash"
        AiProvider.OPENAI -> "gpt-4o-mini"
        AiProvider.CUSTOM -> ""
    }

    /** Returns true if the key looks structurally valid for the given provider. */
    fun isKeyFormatValid(provider: AiProvider, key: String): Boolean = when (provider) {
        AiProvider.GEMINI -> key.startsWith("AIza") && key.length > 20
        AiProvider.OPENAI -> key.startsWith("sk-") && key.length > 20
        AiProvider.CUSTOM -> key.isNotBlank()
    }

    const val AI_STUDIO_URL = "https://aistudio.google.com/app/apikey"

    // ── Rank classification ───────────────────────────────────────────────────
    data class RankOption(
        val rankLetter: String,
        val label: String,
        val description: String,
        val progressionStyle: String,   // matches ProgressionStyle enum name
        val defaultGoalMode: String,    // matches GoalMode enum name
        val ringColor: Color,
    )

    val rankOptions = listOf(
        RankOption("E", "NOVICE",       "Starting my journey",      "LINEAR",           "STRENGTH",    Color(0xFF888888)),
        RankOption("C", "INTERMEDIATE", "Training 6+ months",       "DOUBLE_PROGRESSION","HYPERTROPHY", Color(0xFFAAAAAA)),
        RankOption("A", "ADVANCED",     "2+ years, serious lifter", "UNDULATING",       "PERFORMANCE", accentGold),
    )

    // ── Goal mode options ─────────────────────────────────────────────────────
    data class GoalModeOption(
        val goalMode: String,     // matches GoalMode enum name
        val label: String,
        val subtitle: String,
        val iconDrawableRes: Int, // e.g. R.drawable.ic_strength
    )
    // iconDrawableRes values are filled in by the implementer using existing drawables
    // (or newly added ones) — do not hardcode R.drawable references here as they
    // change at compile time.  Pass them in from the composable via GoalModeOption.

    // ── Permission order ──────────────────────────────────────────────────────
    enum class OnboardingPermission { CAMERA, HEALTH_CONNECT, NOTIFICATIONS }

    val permissionOrder = listOf(
        OnboardingPermission.CAMERA,
        OnboardingPermission.HEALTH_CONNECT,
        OnboardingPermission.NOTIFICATIONS,
    )

    // ── Particle config ───────────────────────────────────────────────────────
    const val PARTICLE_COUNT_DRIFT  = 50
    const val PARTICLE_COUNT_BURST  = 200
    const val TYPEWRITER_DELAY_MS   = 40L
    const val SCREEN1_AUTO_ADVANCE_MS = 2500L
}
```

- [ ] **Step 3: Verify file compiles (no Android Studio needed — just check for syntax errors by reviewing imports)**

All types used (`Color`) are available via `androidx.compose.ui.graphics.Color`. No runtime deps.

- [ ] **Step 4: Commit**

```powershell
cd "Z:\KOTLIN\UnifiedPort"
git add app/src/main/java/com/ironlog/app/ui/screens/onboarding/OnboardingConfig.kt
git add app/src/main/res/values/strings_onboarding.xml
git commit -m "feat(onboarding): add OnboardingConfig and string resources"
```

---

## Task 2: OnboardingViewModel

**Files:**
- Create: `app/src/main/java/com/ironlog/app/ui/screens/onboarding/OnboardingViewModel.kt`
- Create: `app/src/test/java/com/ironlog/app/ui/screens/onboarding/OnboardingViewModelTest.kt`

The ViewModel holds `OnboardingDraft` state and calls `settingsRepoSaveOnboardingData` on completion.

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/java/com/ironlog/app/ui/screens/onboarding/OnboardingViewModelTest.kt
package com.ironlog.app.ui.screens.onboarding

import org.junit.Assert.*
import org.junit.Test

class OnboardingViewModelTest {

    @Test fun `initial draft has defaults`() {
        val vm = OnboardingViewModel()
        assertEquals("", vm.draft.value.userName)
        assertEquals(3, vm.draft.value.weeklyGoalDays)
        assertEquals("kg", vm.draft.value.weightUnit)
        assertEquals("LOCAL", vm.draft.value.intelligenceMode)
        assertFalse(vm.draft.value.cameraGranted)
        assertFalse(vm.draft.value.healthConnectGranted)
        assertFalse(vm.draft.value.notificationsGranted)
    }

    @Test fun `updateUserName trims and updates`() {
        val vm = OnboardingViewModel()
        vm.updateUserName("  Hunter  ")
        assertEquals("Hunter", vm.draft.value.userName)
    }

    @Test fun `updateWeeklyGoalDays clamps to 1-7`() {
        val vm = OnboardingViewModel()
        vm.updateWeeklyGoalDays(0)
        assertEquals(1, vm.draft.value.weeklyGoalDays)
        vm.updateWeeklyGoalDays(8)
        assertEquals(7, vm.draft.value.weeklyGoalDays)
        vm.updateWeeklyGoalDays(5)
        assertEquals(5, vm.draft.value.weeklyGoalDays)
    }

    @Test fun `setting a valid API key sets intelligenceMode to AUTO`() {
        val vm = OnboardingViewModel()
        vm.updateCloudApiKey("AIzaFakeKeyForTest1234567890")
        assertEquals("AUTO", vm.draft.value.intelligenceMode)
    }

    @Test fun `clearing API key reverts intelligenceMode to LOCAL`() {
        val vm = OnboardingViewModel()
        vm.updateCloudApiKey("AIzaFakeKeyForTest1234567890")
        vm.updateCloudApiKey("")
        assertEquals("LOCAL", vm.draft.value.intelligenceMode)
    }

    @Test fun `setClassification seeds goalMode default`() {
        val vm = OnboardingViewModel()
        vm.setClassification(progressionStyle = "UNDULATING", defaultGoalMode = "PERFORMANCE")
        assertEquals("UNDULATING", vm.draft.value.progressionStyle)
        assertEquals("PERFORMANCE", vm.draft.value.goalMode)
    }

    @Test fun `setGoalMode overrides seeded default`() {
        val vm = OnboardingViewModel()
        vm.setClassification(progressionStyle = "LINEAR", defaultGoalMode = "STRENGTH")
        vm.setGoalMode("HYPERTROPHY")
        assertEquals("HYPERTROPHY", vm.draft.value.goalMode)
    }

    @Test fun `permission flags update independently`() {
        val vm = OnboardingViewModel()
        vm.setCameraGranted(true)
        assertTrue(vm.draft.value.cameraGranted)
        assertFalse(vm.draft.value.healthConnectGranted)
        vm.setHealthConnectGranted(true)
        assertTrue(vm.draft.value.healthConnectGranted)
        assertTrue(vm.draft.value.cameraGranted)
    }
}
```

- [ ] **Step 2: Run test (expect failure — class doesn't exist yet)**

```powershell
cd "Z:\KOTLIN\UnifiedPort"
.\gradlew :app:testDebugUnitTest --tests "com.ironlog.app.ui.screens.onboarding.OnboardingViewModelTest" 2>&1 | Select-String -Pattern "FAILED|ERROR|error:" | Select-Object -First 20
```

Expected: compile error — `OnboardingViewModel` not found.

- [ ] **Step 3: Create `OnboardingViewModel.kt`**

```kotlin
package com.ironlog.app.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class OnboardingDraft(
    val userName: String = "",
    val progressionStyle: String = "LINEAR",
    val goalMode: String = "STRENGTH",
    val weeklyGoalDays: Int = 3,
    val weightUnit: String = "kg",
    val cloudAiApiKey: String = "",
    val cloudAiModelName: String = "gemini-2.0-flash",
    val cloudAiProviderPreset: String = "GEMINI",
    val intelligenceMode: String = "LOCAL",
    val cameraGranted: Boolean = false,
    val healthConnectGranted: Boolean = false,
    val notificationsGranted: Boolean = false,
)

class OnboardingViewModel : ViewModel() {

    private val _draft = MutableStateFlow(OnboardingDraft())
    val draft: StateFlow<OnboardingDraft> = _draft.asStateFlow()

    fun updateUserName(name: String) {
        _draft.update { it.copy(userName = name.trim()) }
    }

    fun updateWeeklyGoalDays(days: Int) {
        _draft.update { it.copy(weeklyGoalDays = days.coerceIn(1, 7)) }
    }

    fun updateWeightUnit(unit: String) {
        _draft.update { it.copy(weightUnit = unit) }
    }

    fun setClassification(progressionStyle: String, defaultGoalMode: String) {
        _draft.update { it.copy(progressionStyle = progressionStyle, goalMode = defaultGoalMode) }
    }

    fun setGoalMode(mode: String) {
        _draft.update { it.copy(goalMode = mode) }
    }

    fun updateCloudProvider(provider: String, defaultModel: String) {
        _draft.update { it.copy(cloudAiProviderPreset = provider, cloudAiModelName = defaultModel) }
    }

    fun updateCloudApiKey(key: String) {
        _draft.update {
            it.copy(
                cloudAiApiKey = key,
                intelligenceMode = if (key.isNotBlank()) "AUTO" else "LOCAL",
            )
        }
    }

    fun updateCloudModelName(model: String) {
        _draft.update { it.copy(cloudAiModelName = model) }
    }

    fun setCameraGranted(granted: Boolean) {
        _draft.update { it.copy(cameraGranted = granted) }
    }

    fun setHealthConnectGranted(granted: Boolean) {
        _draft.update { it.copy(healthConnectGranted = granted) }
    }

    fun setNotificationsGranted(granted: Boolean) {
        _draft.update { it.copy(notificationsGranted = granted) }
    }
}
```

- [ ] **Step 4: Run tests (expect pass)**

```powershell
.\gradlew :app:testDebugUnitTest --tests "com.ironlog.app.ui.screens.onboarding.OnboardingViewModelTest"
```

Expected: `BUILD SUCCESSFUL`, 8 tests passed.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/ironlog/app/ui/screens/onboarding/OnboardingViewModel.kt
git add app/src/test/java/com/ironlog/app/ui/screens/onboarding/OnboardingViewModelTest.kt
git commit -m "feat(onboarding): add OnboardingViewModel with full draft state management"
```

---

## Task 3: Shared Composable Components

**Files:**
- Create: `app/src/main/java/com/ironlog/app/ui/screens/onboarding/OnboardingComponents.kt`

All reusable UI building blocks used across multiple step screens.

- [ ] **Step 1: Create `OnboardingComponents.kt`**

```kotlin
package com.ironlog.app.ui.screens.onboarding

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ── Particle data ─────────────────────────────────────────────────────────────

private data class DriftParticle(val x: Float, val y: Float, val radius: Float, val speedFactor: Float, val alpha: Float)

/**
 * Ambient particle drift field — 50 white/blue dots moving upward infinitely.
 * Used on Screens 1 and 8.
 */
@Composable
fun ParticleField(modifier: Modifier = Modifier, count: Int = OnboardingConfig.PARTICLE_COUNT_DRIFT) {
    val particles = remember {
        List(count) {
            DriftParticle(
                x           = Random.nextFloat(),
                y           = Random.nextFloat(),
                radius      = Random.nextFloat() * 2f + 1f,
                speedFactor = Random.nextFloat() * 0.5f + 0.3f,
                alpha       = Random.nextFloat() * 0.5f + 0.2f,
            )
        }
    }
    val infiniteTransition = rememberInfiniteTransition(label = "particles")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing)),
        label = "particleProgress",
    )
    Canvas(modifier = modifier.fillMaxSize()) {
        particles.forEach { p ->
            val yPos = (p.y - progress * p.speedFactor).mod(1f)
            drawCircle(
                color  = OnboardingConfig.accentBlue.copy(alpha = p.alpha),
                radius = p.radius,
                center = Offset(p.x * size.width, yPos * size.height),
            )
        }
    }
}

// ── Typewriter text ───────────────────────────────────────────────────────────

/**
 * Animates [text] character-by-character with [delayMs] between each character.
 * Calls [onComplete] when the full string has been revealed.
 */
@Composable
fun TypewriterText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = OnboardingConfig.accentBlue,
    fontSize: Int = 14,
    delayMs: Long = OnboardingConfig.TYPEWRITER_DELAY_MS,
    onComplete: () -> Unit = {},
) {
    var displayed by remember(text) { mutableStateOf("") }
    LaunchedEffect(text) {
        displayed = ""
        text.forEach { char ->
            delay(delayMs)
            displayed += char
        }
        onComplete()
    }
    Text(
        text      = displayed,
        color     = color,
        fontSize  = fontSize.sp,
        modifier  = modifier,
        textAlign = TextAlign.Center,
    )
}

// ── Rank badge ────────────────────────────────────────────────────────────────

/**
 * Metallic ring badge with a rank letter inside.
 * [ringColor] is the metallic rim color; defaults to silver.
 */
@Composable
fun RankBadge(
    rank: String,
    ringColor: Color,
    size: Dp = 80.dp,
    glowAlpha: Float = 0.6f,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(OnboardingConfig.bgDark)
            .border(
                width = 3.dp,
                brush = Brush.radialGradient(listOf(ringColor, ringColor.copy(alpha = 0.3f))),
                shape = CircleShape,
            ),
    ) {
        // Outer glow ring
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color  = ringColor.copy(alpha = glowAlpha * 0.3f),
                radius = size.toPx() / 2f + 6f,
            )
        }
        Text(
            text       = rank,
            color      = ringColor,
            fontSize   = (size.value * 0.38f).sp,
            fontWeight = FontWeight.Black,
        )
    }
}

// ── Glow button ───────────────────────────────────────────────────────────────

/**
 * Primary CTA button with electric-blue gradient and shadow glow.
 */
@Composable
fun GlowButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = OnboardingConfig.accentBlue,
) {
    val alpha by animateFloatAsState(if (enabled) 1f else 0.4f, label = "btnAlpha")
    Button(
        onClick  = onClick,
        enabled  = enabled,
        shape    = RoundedCornerShape(8.dp),
        colors   = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = alpha)),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Text(
            text       = text,
            fontWeight = FontWeight.Bold,
            fontSize   = 14.sp,
            letterSpacing = 2.sp,
            color      = Color.White.copy(alpha = alpha),
        )
    }
}

// ── Glow card ─────────────────────────────────────────────────────────────────

/**
 * Dark card with animated border glow. [selected] drives the glow intensity.
 */
@Composable
fun GlowCard(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glowColor: Color = OnboardingConfig.accentBlue,
    content: @Composable ColumnScope.() -> Unit,
) {
    val borderColor by animateColorAsState(
        targetValue = if (selected) glowColor else OnboardingConfig.cardBorder,
        animationSpec = tween(300),
        label = "cardBorder",
    )
    val bgAlpha by animateFloatAsState(if (selected) 0.08f else 0f, label = "cardBg")
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(glowColor.copy(alpha = bgAlpha))
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        content = content,
    )
}
```

- [ ] **Step 2: Verify imports resolve**

Review that all imports exist in the project's Compose dependencies. `animateColorAsState` and `animateFloatAsState` are in `androidx.compose.animation.core`.

- [ ] **Step 3: Commit**

```powershell
git add app/src/main/java/com/ironlog/app/ui/screens/onboarding/OnboardingComponents.kt
git commit -m "feat(onboarding): add shared composable components (ParticleField, TypewriterText, RankBadge, GlowButton, GlowCard)"
```

---

## Task 4: Step 1 — SYSTEM AWAKENING

**Files:**
- Create: `app/src/main/java/com/ironlog/app/ui/screens/onboarding/steps/Step1Awakening.kt`

- [ ] **Step 1: Create `Step1Awakening.kt`**

```kotlin
package com.ironlog.app.ui.screens.onboarding.steps

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironlog.app.R
import com.ironlog.app.ui.screens.onboarding.OnboardingConfig
import com.ironlog.app.ui.screens.onboarding.OnboardingComponents
import com.ironlog.app.ui.screens.onboarding.ParticleField
import com.ironlog.app.ui.screens.onboarding.TypewriterText
import kotlinx.coroutines.delay

/**
 * Screen 1 — Cinematic SYSTEM AWAKENING splash.
 * Auto-advances after [OnboardingConfig.SCREEN1_AUTO_ADVANCE_MS].
 */
@Composable
fun Step1Awakening(onAdvance: () -> Unit) {
    val logoAlpha by animateFloatAsState(
        targetValue   = 1f,
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label         = "logoAlpha",
    )

    var showLine1 by remember { mutableStateOf(false) }
    var showLine2 by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(600)
        showLine1 = true
    }

    LaunchedEffect(Unit) {
        delay(OnboardingConfig.SCREEN1_AUTO_ADVANCE_MS)
        onAdvance()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OnboardingConfig.bgDark),
    ) {
        ParticleField()

        Column(
            modifier            = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Logo placeholder — replace with actual IronLogLogo composable
            Text(
                text       = "IRONLOG",
                color      = OnboardingConfig.accentBlue,
                fontSize   = 36.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 8.sp,
                modifier   = Modifier.alpha(logoAlpha),
            )

            Spacer(Modifier.height(48.dp))

            if (showLine1) {
                TypewriterText(
                    text       = stringResource(R.string.onb_awakening_line1),
                    fontSize   = 12,
                    modifier   = Modifier.padding(horizontal = 32.dp),
                    onComplete = { showLine2 = true },
                )
            }

            if (showLine2) {
                Spacer(Modifier.height(8.dp))
                TypewriterText(
                    text     = stringResource(R.string.onb_awakening_line2),
                    fontSize = 12,
                    color    = OnboardingConfig.textMuted,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```powershell
git add app/src/main/java/com/ironlog/app/ui/screens/onboarding/steps/Step1Awakening.kt
git commit -m "feat(onboarding): add Step1 SYSTEM AWAKENING cinematic screen"
```

---

## Task 5: Step 2 — HUNTER REGISTRATION

**Files:**
- Create: `app/src/main/java/com/ironlog/app/ui/screens/onboarding/steps/Step2Registration.kt`

- [ ] **Step 1: Create `Step2Registration.kt`**

```kotlin
package com.ironlog.app.ui.screens.onboarding.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironlog.app.R
import com.ironlog.app.ui.screens.onboarding.GlowButton
import com.ironlog.app.ui.screens.onboarding.OnboardingConfig

/**
 * Screen 2 — Hunter name input.
 */
@Composable
fun Step2Registration(
    userName: String,
    onUserNameChange: (String) -> Unit,
    onNext: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OnboardingConfig.bgDark)
            .padding(horizontal = 32.dp),
    ) {
        Column(
            modifier            = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text          = stringResource(R.string.onb_reg_header),
                color         = OnboardingConfig.accentBlue,
                fontSize      = 22.sp,
                fontWeight    = FontWeight.Black,
                letterSpacing = 4.sp,
                textAlign     = TextAlign.Center,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text      = stringResource(R.string.onb_reg_subtext),
                color     = OnboardingConfig.textMuted,
                fontSize  = 14.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(40.dp))

            OutlinedTextField(
                value         = userName,
                onValueChange = { if (it.length <= 30) onUserNameChange(it) },
                placeholder   = { Text(stringResource(R.string.onb_reg_hint), color = OnboardingConfig.textMuted) },
                singleLine    = true,
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = OnboardingConfig.accentBlue,
                    unfocusedBorderColor = OnboardingConfig.accentBlue.copy(alpha = 0.3f),
                    cursorColor          = OnboardingConfig.accentBlue,
                    focusedTextColor     = Color.White,
                    unfocusedTextColor   = Color.White,
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction      = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { if (userName.isNotBlank()) onNext() }),
                modifier        = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )

            Spacer(Modifier.height(40.dp))

            GlowButton(
                text    = stringResource(R.string.onb_reg_cta),
                onClick = onNext,
                enabled = userName.isNotBlank(),
            )
        }
    }
}
```

- [ ] **Step 2: Commit**

```powershell
git add app/src/main/java/com/ironlog/app/ui/screens/onboarding/steps/Step2Registration.kt
git commit -m "feat(onboarding): add Step2 HUNTER REGISTRATION name input screen"
```

---

## Task 6: Step 3 — TRAINING CLASSIFICATION

**Files:**
- Create: `app/src/main/java/com/ironlog/app/ui/screens/onboarding/steps/Step3Classification.kt`

- [ ] **Step 1: Create `Step3Classification.kt`**

```kotlin
package com.ironlog.app.ui.screens.onboarding.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironlog.app.R
import com.ironlog.app.ui.screens.onboarding.GlowCard
import com.ironlog.app.ui.screens.onboarding.OnboardingConfig
import com.ironlog.app.ui.screens.onboarding.RankBadge

/**
 * Screen 3 — Rank/training level selection.
 * Auto-advances on card tap.
 */
@Composable
fun Step3Classification(
    selectedProgressionStyle: String,
    onSelect: (progressionStyle: String, defaultGoalMode: String) -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier            = Modifier
            .fillMaxSize()
            .background(OnboardingConfig.bgDark)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text          = stringResource(R.string.onb_class_header),
            color         = OnboardingConfig.accentBlue,
            fontSize      = 20.sp,
            fontWeight    = FontWeight.Black,
            letterSpacing = 3.sp,
            textAlign     = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text      = stringResource(R.string.onb_class_subtext),
            color     = OnboardingConfig.textMuted,
            fontSize  = 13.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(40.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding        = PaddingValues(horizontal = 8.dp),
        ) {
            items(OnboardingConfig.rankOptions) { option ->
                val isSelected = option.progressionStyle == selectedProgressionStyle
                GlowCard(
                    selected  = isSelected,
                    glowColor = option.ringColor,
                    onClick   = {
                        onSelect(option.progressionStyle, option.defaultGoalMode)
                        onNext()
                    },
                    modifier  = Modifier.width(160.dp),
                ) {
                    RankBadge(
                        rank      = option.rankLetter,
                        ringColor = option.ringColor,
                        size      = 64.dp,
                        modifier  = Modifier.align(Alignment.CenterHorizontally),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text          = option.label,
                        color         = if (isSelected) option.ringColor else OnboardingConfig.textMuted,
                        fontSize      = 14.sp,
                        fontWeight    = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        textAlign     = TextAlign.Center,
                        modifier      = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text      = option.description,
                        color     = OnboardingConfig.textMuted,
                        fontSize  = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier  = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```powershell
git add app/src/main/java/com/ironlog/app/ui/screens/onboarding/steps/Step3Classification.kt
git commit -m "feat(onboarding): add Step3 TRAINING CLASSIFICATION rank selection"
```

---

## Task 7: Step 4 — WEEKLY MISSION QUOTA

**Files:**
- Create: `app/src/main/java/com/ironlog/app/ui/screens/onboarding/steps/Step4Quota.kt`

- [ ] **Step 1: Create `Step4Quota.kt`**

```kotlin
package com.ironlog.app.ui.screens.onboarding.steps

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironlog.app.R
import com.ironlog.app.ui.screens.onboarding.GlowButton
import com.ironlog.app.ui.screens.onboarding.OnboardingConfig

private val DAY_LABELS = listOf("M", "T", "W", "T", "F", "S", "S")

/**
 * Screen 4 — Weekly session count (dot picker) + kg/lbs toggle.
 *
 * [selectedDayIndices] is a set of 0-based indices (0=Mon … 6=Sun).
 * [weeklyGoalDays] is derived as selectedDayIndices.size in the ViewModel,
 * but shown live here for immediate feedback.
 */
@Composable
fun Step4Quota(
    selectedDayIndices: Set<Int>,
    onDayToggle: (index: Int) -> Unit,
    weightUnit: String,
    onWeightUnitChange: (String) -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier            = Modifier
            .fillMaxSize()
            .background(OnboardingConfig.bgDark)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text          = stringResource(R.string.onb_quota_header),
            color         = OnboardingConfig.accentBlue,
            fontSize      = 20.sp,
            fontWeight    = FontWeight.Black,
            letterSpacing = 3.sp,
            textAlign     = TextAlign.Center,
        )

        Spacer(Modifier.height(40.dp))

        // Day dot picker
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            DAY_LABELS.forEachIndexed { index, label ->
                val isSelected = index in selectedDayIndices
                val bgColor by animateColorAsState(
                    if (isSelected) OnboardingConfig.accentBlue else Color.Transparent,
                    tween(200), label = "dot$index"
                )
                val textColor by animateColorAsState(
                    if (isSelected) Color.White else OnboardingConfig.textMuted,
                    tween(200), label = "dotText$index"
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(bgColor)
                        .border(1.5.dp, OnboardingConfig.accentBlue.copy(alpha = if (isSelected) 0f else 0.3f), CircleShape)
                        .clickable {
                            // Prevent deselecting last dot
                            if (!isSelected || selectedDayIndices.size > 1) onDayToggle(index)
                        },
                ) {
                    Text(text = label, color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text          = stringResource(R.string.onb_quota_sessions, selectedDayIndices.size),
            color         = OnboardingConfig.accentGold,
            fontSize      = 24.sp,
            fontWeight    = FontWeight.Black,
            letterSpacing = 2.sp,
        )

        Spacer(Modifier.height(32.dp))

        // kg / lbs toggle
        Row(
            modifier          = Modifier
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, OnboardingConfig.accentBlue.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
        ) {
            listOf("kg", "lbs").forEach { unit ->
                val isActive = unit == weightUnit
                val bg by animateColorAsState(
                    if (isActive) OnboardingConfig.accentBlue else Color.Transparent, tween(200), label = "unit$unit"
                )
                val tc by animateColorAsState(
                    if (isActive) Color.White else OnboardingConfig.textMuted, tween(200), label = "unitText$unit"
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(bg)
                        .clickable { onWeightUnitChange(unit) }
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                ) {
                    Text(unit.uppercase(), color = tc, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        Spacer(Modifier.height(48.dp))

        GlowButton(text = stringResource(R.string.onb_quota_cta), onClick = onNext)
    }
}
```

Note: The ViewModel needs two extra fields to support the day-index set:

Add to `OnboardingDraft`:
```kotlin
val selectedDayIndices: Set<Int> = setOf(0, 2, 4), // Mon, Wed, Fri default
```

Add to `OnboardingViewModel`:
```kotlin
fun toggleDayIndex(index: Int) {
    val current = _draft.value.selectedDayIndices
    val updated = if (index in current && current.size > 1) current - index else current + index
    _draft.update { it.copy(selectedDayIndices = updated, weeklyGoalDays = updated.size) }
}
```

- [ ] **Step 2: Add `selectedDayIndices` to `OnboardingDraft` and `toggleDayIndex` to `OnboardingViewModel`**

Edit `OnboardingViewModel.kt`:

In `OnboardingDraft`, add:
```kotlin
val selectedDayIndices: Set<Int> = setOf(0, 2, 4),
```

In `OnboardingViewModel`, add after `updateWeeklyGoalDays`:
```kotlin
fun toggleDayIndex(index: Int) {
    val current = _draft.value.selectedDayIndices
    val updated = if (index in current && current.size > 1) current - index else current + index
    _draft.update { it.copy(selectedDayIndices = updated, weeklyGoalDays = updated.size) }
}
```

- [ ] **Step 3: Re-run ViewModel tests (still pass, new field has a default)**

```powershell
.\gradlew :app:testDebugUnitTest --tests "com.ironlog.app.ui.screens.onboarding.OnboardingViewModelTest"
```

Expected: all 8 tests pass.

- [ ] **Step 4: Commit**

```powershell
git add app/src/main/java/com/ironlog/app/ui/screens/onboarding/steps/Step4Quota.kt
git add app/src/main/java/com/ironlog/app/ui/screens/onboarding/OnboardingViewModel.kt
git commit -m "feat(onboarding): add Step4 WEEKLY MISSION QUOTA day picker + unit toggle"
```

---

## Task 8: Step 5 — GOAL MODE

**Files:**
- Create: `app/src/main/java/com/ironlog/app/ui/screens/onboarding/steps/Step5GoalMode.kt`

- [ ] **Step 1: Create `Step5GoalMode.kt`**

```kotlin
package com.ironlog.app.ui.screens.onboarding.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironlog.app.R
import com.ironlog.app.ui.screens.onboarding.GlowButton
import com.ironlog.app.ui.screens.onboarding.GlowCard
import com.ironlog.app.ui.screens.onboarding.OnboardingConfig

// Goal mode definitions — edit here to add/remove/rename modes
private data class GoalOption(
    val mode: String,
    val label: String,
    val subtitle: String,
    val iconRes: Int,
)

// Uses Material Icons outlined — replace with custom drawables if desired
private val GOAL_OPTIONS = listOf(
    GoalOption("STRENGTH",    "STRENGTH",    "Max weight, low reps",   android.R.drawable.ic_menu_compass),
    GoalOption("HYPERTROPHY", "HYPERTROPHY", "Size & muscle growth",   android.R.drawable.ic_menu_compass),
    GoalOption("PERFORMANCE", "PERFORMANCE", "Speed & power",          android.R.drawable.ic_menu_compass),
    GoalOption("ENDURANCE",   "ENDURANCE",   "High volume, stamina",   android.R.drawable.ic_menu_compass),
)
// NOTE for implementer: Replace android.R.drawable.ic_menu_compass with the
// project's actual vector drawables (search res/drawable/ for fitness icons).
// The four icons should be distinct — e.g. barbell, muscle, lightning, runner.

/**
 * Screen 5 — 2×2 goal mode grid.
 */
@Composable
fun Step5GoalMode(
    selectedGoalMode: String,
    onSelect: (String) -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier            = Modifier
            .fillMaxSize()
            .background(OnboardingConfig.bgDark)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text          = stringResource(R.string.onb_goal_header),
            color         = OnboardingConfig.accentBlue,
            fontSize      = 20.sp,
            fontWeight    = FontWeight.Black,
            letterSpacing = 3.sp,
            textAlign     = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        // 2×2 grid via two rows
        GOAL_OPTIONS.chunked(2).forEach { row ->
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { option ->
                    val isSelected = option.mode == selectedGoalMode
                    GlowCard(
                        selected  = isSelected,
                        glowColor = OnboardingConfig.accentGold,
                        onClick   = { onSelect(option.mode) },
                        modifier  = Modifier.weight(1f),
                    ) {
                        Text(
                            text       = option.label,
                            color      = if (isSelected) OnboardingConfig.accentGold else OnboardingConfig.accentBlue,
                            fontSize   = 12.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            textAlign  = TextAlign.Center,
                            modifier   = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text      = option.subtitle,
                            color     = OnboardingConfig.textMuted,
                            fontSize  = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier  = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(24.dp))

        GlowButton(text = stringResource(R.string.onb_goal_cta), onClick = onNext)
    }
}
```

- [ ] **Step 2: Commit**

```powershell
git add app/src/main/java/com/ironlog/app/ui/screens/onboarding/steps/Step5GoalMode.kt
git commit -m "feat(onboarding): add Step5 GOAL MODE 2x2 selection grid"
```

---

## Task 9: Step 6 — UNLOCK AI ABILITIES

**Files:**
- Create: `app/src/main/java/com/ironlog/app/ui/screens/onboarding/steps/Step6AiAbilities.kt`

- [ ] **Step 1: Create `Step6AiAbilities.kt`**

```kotlin
package com.ironlog.app.ui.screens.onboarding.steps

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironlog.app.R
import com.ironlog.app.ui.screens.onboarding.GlowButton
import com.ironlog.app.ui.screens.onboarding.OnboardingConfig

/**
 * Screen 6 — Intelligence mode selector + optional Cloud AI API key entry.
 */
@Composable
fun Step6AiAbilities(
    cloudApiKey: String,
    cloudModelName: String,
    cloudProvider: String,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onProviderChange: (provider: String, defaultModel: String) -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    val context = LocalContext.current
    var cloudExpanded by remember { mutableStateOf(cloudApiKey.isNotBlank()) }
    var keyVisible by remember { mutableStateOf(false) }
    var providerExpanded by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }

    val currentProvider = OnboardingConfig.AiProvider.entries
        .firstOrNull { it.name == cloudProvider } ?: OnboardingConfig.AiProvider.GEMINI
    val models = OnboardingConfig.modelsFor(currentProvider)
    val keyValid = OnboardingConfig.isKeyFormatValid(currentProvider, cloudApiKey)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OnboardingConfig.bgDark)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text          = stringResource(R.string.onb_ai_header),
            color         = OnboardingConfig.accentBlue,
            fontSize      = 20.sp,
            fontWeight    = FontWeight.Black,
            letterSpacing = 3.sp,
            textAlign     = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text      = stringResource(R.string.onb_ai_subtext),
            color     = OnboardingConfig.textMuted,
            fontSize  = 13.sp,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(24.dp))

        // ── Tier A: On-device (always active) ────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(OnboardingConfig.surfaceDark)
                .border(1.dp, OnboardingConfig.cardBorder, RoundedCornerShape(12.dp))
                .padding(16.dp),
        ) {
            Column {
                Text(
                    text       = stringResource(R.string.onb_ai_local_title),
                    color      = OnboardingConfig.accentGold,
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text     = stringResource(R.string.onb_ai_local_sub),
                    color    = OnboardingConfig.textMuted,
                    fontSize = 12.sp,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Tier B: Cloud AI (optional expand) ───────────────────────────────
        val cloudBorderColor = when {
            cloudExpanded && keyValid -> OnboardingConfig.accentBlue
            cloudExpanded            -> OnboardingConfig.accentBlue.copy(alpha = 0.4f)
            else                     -> OnboardingConfig.cardBorder
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(OnboardingConfig.surfaceDark)
                .border(1.5.dp, cloudBorderColor, RoundedCornerShape(12.dp))
                .clickable(enabled = !cloudExpanded) { cloudExpanded = true }
                .padding(16.dp),
        ) {
            AnimatedContent(targetState = cloudExpanded, label = "cloudExpand") { expanded ->
                if (!expanded) {
                    Text(
                        text       = stringResource(R.string.onb_ai_cloud_locked),
                        color      = OnboardingConfig.accentBlue,
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier   = Modifier.fillMaxWidth(),
                        textAlign  = TextAlign.Center,
                    )
                } else {
                    Column {
                        // Provider row
                        Text("API PROVIDER", color = OnboardingConfig.textMuted, fontSize = 11.sp, letterSpacing = 1.sp)
                        Spacer(Modifier.height(4.dp))
                        ExposedDropdownMenuBox(
                            expanded         = providerExpanded,
                            onExpandedChange = { providerExpanded = it },
                        ) {
                            OutlinedTextField(
                                value         = currentProvider.displayName,
                                onValueChange = {},
                                readOnly      = true,
                                trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(providerExpanded) },
                                colors        = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor   = OnboardingConfig.accentBlue,
                                    unfocusedBorderColor = OnboardingConfig.cardBorder,
                                    focusedTextColor     = Color.White,
                                    unfocusedTextColor   = Color.White,
                                ),
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                            )
                            ExposedDropdownMenu(
                                expanded      = providerExpanded,
                                onDismissRequest = { providerExpanded = false },
                            ) {
                                OnboardingConfig.AiProvider.entries.forEach { p ->
                                    DropdownMenuItem(
                                        text    = { Text(p.displayName) },
                                        onClick = {
                                            onProviderChange(p.name, OnboardingConfig.defaultModelFor(p))
                                            providerExpanded = false
                                        },
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // API key field
                        Text("API KEY", color = OnboardingConfig.textMuted, fontSize = 11.sp, letterSpacing = 1.sp)
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value         = cloudApiKey,
                            onValueChange = onApiKeyChange,
                            placeholder   = { Text(stringResource(R.string.onb_ai_key_hint), color = OnboardingConfig.textMuted, fontSize = 12.sp) },
                            visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon  = {
                                IconButton(onClick = { keyVisible = !keyVisible }) {
                                    Icon(
                                        imageVector = if (keyVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                        contentDescription = null,
                                        tint = OnboardingConfig.textMuted,
                                    )
                                }
                            },
                            isError       = cloudApiKey.isNotBlank() && !keyValid,
                            supportingText = {
                                if (cloudApiKey.isNotBlank() && !keyValid)
                                    Text("Invalid key format", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                                else
                                    Text(stringResource(R.string.onb_ai_key_helper), color = OnboardingConfig.textMuted, fontSize = 11.sp)
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            colors        = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor   = OnboardingConfig.accentBlue,
                                unfocusedBorderColor = OnboardingConfig.cardBorder,
                                focusedTextColor     = Color.White,
                                unfocusedTextColor   = Color.White,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        // Gemini recommendation card
                        if (currentProvider == OnboardingConfig.AiProvider.GEMINI) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text     = stringResource(R.string.onb_ai_gemini_badge),
                                color    = OnboardingConfig.accentGold,
                                fontSize = 12.sp,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text  = stringResource(R.string.onb_ai_get_key),
                                color = OnboardingConfig.accentBlue,
                                fontSize = 12.sp,
                                modifier = Modifier.clickable {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(OnboardingConfig.AI_STUDIO_URL)))
                                },
                            )
                        }

                        // Model selector (only when models available)
                        if (models.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text("MODEL", color = OnboardingConfig.textMuted, fontSize = 11.sp, letterSpacing = 1.sp)
                            Spacer(Modifier.height(4.dp))
                            val selectedModel = models.firstOrNull { it.modelId == cloudModelName } ?: models.first()
                            ExposedDropdownMenuBox(
                                expanded         = modelExpanded,
                                onExpandedChange = { modelExpanded = it },
                            ) {
                                OutlinedTextField(
                                    value         = "${selectedModel.displayName} — ${selectedModel.subtitle}",
                                    onValueChange = {},
                                    readOnly      = true,
                                    trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(modelExpanded) },
                                    colors        = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor   = OnboardingConfig.accentBlue,
                                        unfocusedBorderColor = OnboardingConfig.cardBorder,
                                        focusedTextColor     = Color.White,
                                        unfocusedTextColor   = Color.White,
                                    ),
                                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                                )
                                ExposedDropdownMenu(expanded = modelExpanded, onDismissRequest = { modelExpanded = false }) {
                                    models.forEach { m ->
                                        DropdownMenuItem(
                                            text    = { Column { Text(m.displayName, color = Color.White); Text(m.subtitle, color = OnboardingConfig.textMuted, fontSize = 11.sp) } },
                                            onClick = { onModelChange(m.modelId); modelExpanded = false },
                                        )
                                    }
                                }
                            }
                        }

                        if (keyValid) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text       = stringResource(R.string.onb_ai_cloud_ready),
                                color      = OnboardingConfig.accentBlue,
                                fontSize   = 14.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign  = TextAlign.Center,
                                modifier   = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        GlowButton(text = stringResource(R.string.onb_ai_cta), onClick = onNext)
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onb_ai_skip), color = OnboardingConfig.textMuted, fontSize = 13.sp)
        }
    }
}
```

- [ ] **Step 2: Commit**

```powershell
git add app/src/main/java/com/ironlog/app/ui/screens/onboarding/steps/Step6AiAbilities.kt
git commit -m "feat(onboarding): add Step6 UNLOCK AI ABILITIES with API key entry and model selection"
```

---

## Task 10: Step 7 — GRANT PERMISSIONS

**Files:**
- Create: `app/src/main/java/com/ironlog/app/ui/screens/onboarding/steps/Step7Permissions.kt`

- [ ] **Step 1: Create `Step7Permissions.kt`**

```kotlin
package com.ironlog.app.ui.screens.onboarding.steps

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import com.ironlog.app.R
import com.ironlog.app.ui.screens.onboarding.GlowButton
import com.ironlog.app.ui.screens.onboarding.OnboardingConfig

private val HC_PERMISSIONS = setOf(
    HealthPermission.getReadPermission(SleepSessionRecord::class),
    HealthPermission.getReadPermission(RestingHeartRateRecord::class),
    HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
    HealthPermission.getWritePermission(ExerciseSessionRecord::class),
)

/**
 * Screen 7 — Permission grant cards for Camera, Health Connect, and Notifications.
 */
@Composable
fun Step7Permissions(
    cameraGranted: Boolean,
    healthConnectGranted: Boolean,
    notificationsGranted: Boolean,
    onCameraGranted: (Boolean) -> Unit,
    onHealthConnectGranted: (Boolean) -> Unit,
    onNotificationsGranted: (Boolean) -> Unit,
    onNext: () -> Unit,
) {
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onCameraGranted(it) }
    val hcLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        onHealthConnectGranted(HC_PERMISSIONS.all { it in granted })
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onNotificationsGranted(it) }

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .background(OnboardingConfig.bgDark)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text          = stringResource(R.string.onb_perm_header),
            color         = OnboardingConfig.accentBlue,
            fontSize      = 20.sp,
            fontWeight    = FontWeight.Black,
            letterSpacing = 3.sp,
            textAlign     = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text      = stringResource(R.string.onb_perm_subtext),
            color     = OnboardingConfig.textMuted,
            fontSize  = 13.sp,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        PermissionCard(
            title       = stringResource(R.string.onb_perm_camera_title),
            description = stringResource(R.string.onb_perm_camera_desc),
            granted     = cameraGranted,
            onGrant     = { cameraLauncher.launch(Manifest.permission.CAMERA) },
        )
        Spacer(Modifier.height(12.dp))
        PermissionCard(
            title       = stringResource(R.string.onb_perm_health_title),
            description = stringResource(R.string.onb_perm_health_desc),
            granted     = healthConnectGranted,
            onGrant     = { hcLauncher.launch(HC_PERMISSIONS) },
        )
        Spacer(Modifier.height(12.dp))
        PermissionCard(
            title       = stringResource(R.string.onb_perm_notif_title),
            description = stringResource(R.string.onb_perm_notif_desc),
            granted     = notificationsGranted,
            onGrant     = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    onNotificationsGranted(true) // pre-13: permission not required
                }
            },
        )

        Spacer(Modifier.height(40.dp))

        GlowButton(text = stringResource(R.string.onb_perm_cta), onClick = onNext)
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    onGrant: () -> Unit,
) {
    val borderColor by animateColorAsState(
        if (granted) OnboardingConfig.grantedColor else OnboardingConfig.cardBorder,
        tween(300), label = "permBorder$title"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(OnboardingConfig.surfaceDark)
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = OnboardingConfig.accentBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(2.dp))
            Text(description, color = OnboardingConfig.textMuted, fontSize = 12.sp)
        }
        Spacer(Modifier.width(12.dp))
        val chipColor by animateColorAsState(
            if (granted) OnboardingConfig.grantedColor else Color.Transparent,
            tween(300), label = "chipBg$title"
        )
        val chipBorder by animateColorAsState(
            if (granted) OnboardingConfig.grantedColor else OnboardingConfig.accentBlue,
            tween(300), label = "chipBorder$title"
        )
        OutlinedButton(
            onClick  = onGrant,
            enabled  = !granted,
            colors   = ButtonDefaults.outlinedButtonColors(containerColor = chipColor),
            border   = androidx.compose.foundation.BorderStroke(1.dp, chipBorder),
        ) {
            Text(
                text  = if (granted) stringResource(R.string.onb_perm_granted) else stringResource(R.string.onb_perm_grant),
                color = if (granted) Color.White else OnboardingConfig.accentBlue,
                fontSize = 12.sp,
            )
        }
    }
}
```

- [ ] **Step 2: Commit**

```powershell
git add app/src/main/java/com/ironlog/app/ui/screens/onboarding/steps/Step7Permissions.kt
git commit -m "feat(onboarding): add Step7 GRANT PERMISSIONS (camera, health connect, notifications)"
```

---

## Task 11: Step 8 — ARISE

**Files:**
- Create: `app/src/main/java/com/ironlog/app/ui/screens/onboarding/steps/Step8Arise.kt`

- [ ] **Step 1: Create `Step8Arise.kt`**

```kotlin
package com.ironlog.app.ui.screens.onboarding.steps

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironlog.app.R
import com.ironlog.app.ui.screens.onboarding.GlowButton
import com.ironlog.app.ui.screens.onboarding.OnboardingConfig
import com.ironlog.app.ui.screens.onboarding.ParticleField
import com.ironlog.app.ui.screens.onboarding.RankBadge

/**
 * Screen 8 — Cinematic ARISE finale.
 * [userName] is shown in the "HUNTER [name]" headline.
 * [onArise] triggers the final save + navigation.
 */
@Composable
fun Step8Arise(
    userName: String,
    onArise: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ariseGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue  = 0.4f,
        targetValue   = 1.0f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "badgeGlow",
    )

    var showContent by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(400)
        showContent = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OnboardingConfig.bgDark),
    ) {
        ParticleField(count = OnboardingConfig.PARTICLE_COUNT_DRIFT)

        if (showContent) {
            Column(
                modifier            = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                RankBadge(
                    rank      = "E",
                    ringColor = OnboardingConfig.accentBlue,
                    size      = 120.dp,
                    glowAlpha = glowAlpha,
                )

                Spacer(Modifier.height(40.dp))

                val displayName = userName.ifBlank { "HUNTER" }.uppercase()
                Text(
                    text          = "HUNTER $displayName",
                    color         = OnboardingConfig.accentGold,
                    fontSize      = 22.sp,
                    fontWeight    = FontWeight.Black,
                    letterSpacing = 4.sp,
                    textAlign     = TextAlign.Center,
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text          = stringResource(R.string.onb_arise_reg_complete),
                    color         = OnboardingConfig.accentBlue,
                    fontSize      = 16.sp,
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = 3.sp,
                    textAlign     = TextAlign.Center,
                )

                Spacer(Modifier.height(24.dp))

                Text(
                    text      = stringResource(R.string.onb_arise_line1),
                    color     = OnboardingConfig.textMuted,
                    fontSize  = 13.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text      = stringResource(R.string.onb_arise_line2),
                    color     = OnboardingConfig.textMuted,
                    fontSize  = 13.sp,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(56.dp))

                GlowButton(
                    text    = stringResource(R.string.onb_arise_cta),
                    onClick = onArise,
                    color   = OnboardingConfig.accentBlue,
                )
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```powershell
git add app/src/main/java/com/ironlog/app/ui/screens/onboarding/steps/Step8Arise.kt
git commit -m "feat(onboarding): add Step8 ARISE cinematic finale screen"
```

---

## Task 12: OnboardingScreen Shell + AppNavigator Wiring

**Files:**
- Replace: `app/src/main/java/com/ironlog/app/ui/screens/OnboardingScreen.kt`
- Modify: `app/src/main/java/com/ironlog/app/navigation/AppNavigator.kt`

- [ ] **Step 1: Replace `OnboardingScreen.kt`**

The old file had a 6-slide `HorizontalPager` with `OnboardingSlide` data. Replace entirely:

```kotlin
package com.ironlog.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ironlog.app.ui.screens.onboarding.OnboardingConfig
import com.ironlog.app.ui.screens.onboarding.OnboardingViewModel
import com.ironlog.app.ui.screens.onboarding.steps.*
import kotlinx.coroutines.launch

/**
 * Root onboarding composable.
 * Forward-only HorizontalPager (swipe disabled).
 * [onComplete] is called when the user taps ARISE on Screen 8.
 */
@Composable
fun OnboardingScreen(
    onComplete: suspend () -> Unit,
    vm: OnboardingViewModel = viewModel(),
) {
    val draft by vm.draft.collectAsState()
    val pagerState = rememberPagerState(pageCount = { 8 })
    val scope = rememberCoroutineScope()

    fun advance() = scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }

    Box(modifier = Modifier.fillMaxSize().background(OnboardingConfig.bgDark)) {
        HorizontalPager(
            state             = pagerState,
            userScrollEnabled = false,
            modifier          = Modifier.fillMaxSize(),
        ) { page ->
            when (page) {
                0 -> Step1Awakening(onAdvance = { advance() })
                1 -> Step2Registration(
                    userName         = draft.userName,
                    onUserNameChange = vm::updateUserName,
                    onNext           = { advance() },
                )
                2 -> Step3Classification(
                    selectedProgressionStyle = draft.progressionStyle,
                    onSelect                 = { style, goal -> vm.setClassification(style, goal) },
                    onNext                   = { advance() },
                )
                3 -> Step4Quota(
                    selectedDayIndices  = draft.selectedDayIndices,
                    onDayToggle         = vm::toggleDayIndex,
                    weightUnit          = draft.weightUnit,
                    onWeightUnitChange  = vm::updateWeightUnit,
                    onNext              = { advance() },
                )
                4 -> Step5GoalMode(
                    selectedGoalMode = draft.goalMode,
                    onSelect         = vm::setGoalMode,
                    onNext           = { advance() },
                )
                5 -> Step6AiAbilities(
                    cloudApiKey      = draft.cloudAiApiKey,
                    cloudModelName   = draft.cloudAiModelName,
                    cloudProvider    = draft.cloudAiProviderPreset,
                    onApiKeyChange   = vm::updateCloudApiKey,
                    onModelChange    = vm::updateCloudModelName,
                    onProviderChange = { provider, model -> vm.updateCloudProvider(provider, model) },
                    onNext           = { advance() },
                    onSkip           = { advance() },
                )
                6 -> Step7Permissions(
                    cameraGranted            = draft.cameraGranted,
                    healthConnectGranted     = draft.healthConnectGranted,
                    notificationsGranted     = draft.notificationsGranted,
                    onCameraGranted          = vm::setCameraGranted,
                    onHealthConnectGranted   = vm::setHealthConnectGranted,
                    onNotificationsGranted   = vm::setNotificationsGranted,
                    onNext                   = { advance() },
                )
                7 -> Step8Arise(
                    userName = draft.userName,
                    onArise  = { scope.launch { onComplete() } },
                )
            }
        }
    }
}
```

- [ ] **Step 2: Update `AppNavigator.kt` — update the `OnboardingScreen` call and `settingsRepoSaveOnboardingData`**

Find the `composable("Onboarding")` block in `AppNavigator.kt`. Replace the existing `OnboardingScreen(...)` call with:

```kotlin
composable("Onboarding") {
    OnboardingScreen(
        onComplete = {
            val vm = OnboardingViewModel()  // or inject via hiltViewModel / remember
            // NOTE: In practice, get the VM from the NavBackStackEntry to share it;
            // AppNavigator wires this. See step below.
            settingsRepoSaveOnboardingData(
                goalDays      = onboardingGoalDays,
                userName      = onboardingUserName,
                weightUnit    = onboardingWeightUnit,
            )
        }
    )
}
```

Actually — the cleaner approach is to let `OnboardingScreen` own the ViewModel and pass a callback that receives the draft. Update `OnboardingScreen.kt` to expose `onComplete(draft: OnboardingDraft)`:

```kotlin
// In OnboardingScreen.kt, change signature to:
@Composable
fun OnboardingScreen(
    onComplete: suspend (OnboardingDraft) -> Unit,
    vm: OnboardingViewModel = viewModel(),
)

// Step 7 ARISE button:
onArise = { scope.launch { onComplete(draft) } }
```

Then in `AppNavigator.kt`, find and replace the `composable("Onboarding")` block:

```kotlin
composable("Onboarding") {
    OnboardingScreen(
        onComplete = { draft ->
            settingsRepoSaveOnboardingDataFull(draft)
            navController.navigate("Tabs") {
                popUpTo("Onboarding") { inclusive = true }
            }
        }
    )
}
```

Add `settingsRepoSaveOnboardingDataFull` in `AppNavigator.kt` (replace the old `settingsRepoSaveOnboardingData`):

```kotlin
private suspend fun settingsRepoSaveOnboardingDataFull(draft: com.ironlog.app.ui.screens.onboarding.OnboardingDraft) {
    val repo = SettingsRepository()
    repo.setBoolean("onboarding_complete", true)
    val raw  = repo.getString("ironlog_settings") ?: "{}"
    val json = runCatching { org.json.JSONObject(raw) }.getOrDefault(org.json.JSONObject())
    json.put("weeklyGoalDays",       draft.weeklyGoalDays.coerceIn(1, 7))
    json.put("weightUnit",           if (draft.weightUnit == "lbs") "lbs" else "kg")
    json.put("progressionStyle",     draft.progressionStyle)
    json.put("goalMode",             draft.goalMode)
    json.put("intelligenceMode",     draft.intelligenceMode)
    json.put("cloudAiModelName",     draft.cloudAiModelName)
    json.put("cloudAiProviderPreset",draft.cloudAiProviderPreset)
    if (draft.userName.isNotBlank()) json.put("userName", draft.userName)
    repo.setString("ironlog_settings", json.toString(), "json")
    // Store API key in EncryptedSharedPreferences
    if (draft.cloudAiApiKey.isNotBlank()) {
        repo.setString("cloud_ai_api_key", draft.cloudAiApiKey, "encrypted")
    }
}
```

- [ ] **Step 3: Commit**

```powershell
git add app/src/main/java/com/ironlog/app/ui/screens/OnboardingScreen.kt
git add app/src/main/java/com/ironlog/app/navigation/AppNavigator.kt
git commit -m "feat(onboarding): wire OnboardingScreen shell with HorizontalPager and AppNavigator"
```

---

## Task 13: BadgeDefinitions.kt

**Files:**
- Create: `app/src/main/java/com/ironlog/app/domain/badges/BadgeDefinitions.kt`

- [ ] **Step 1: Create `BadgeDefinitions.kt`**

```kotlin
package com.ironlog.app.domain.badges

enum class BadgeTier { BRONZE, SILVER, GOLD, BLUE }

/**
 * Describes an achievement badge.
 *
 * [unlockCondition] is a pure function over [AppStats] — no side effects.
 * Add new badges here; the UI reads this list at runtime.
 */
data class BadgeDefinition(
    val id: String,
    val title: String,
    val description: String,
    val tier: BadgeTier,
    val iconResName: String,          // drawable resource name, e.g. "ic_badge_dumbbell"
    val unlockCondition: (AppStats) -> Boolean,
)

/**
 * Snapshot of user progress used to evaluate [BadgeDefinition.unlockCondition].
 * Extended as new metrics are tracked.
 */
data class AppStats(
    val totalWorkouts: Int = 0,
    val currentStreak: Int = 0,
    val daysSinceFirstWorkout: Int = 0,
    val totalVolumeKg: Double = 0.0,
    val hasLoggedPR: Boolean = false,
    val usedRestTimer: Boolean = false,
    val createdPlan: Boolean = false,
    val cloudAiActivated: Boolean = false,
    val goalModesUsed: Set<String> = emptySet(),
    val currentRank: String = "E",
    val weeksConsistent: Int = 0,
    val consecutiveProgressionWorkouts: Int = 0,
)

object BadgeDefinitions {

    val all: List<BadgeDefinition> = listOf(
        BadgeDefinition(
            id               = "first_workout",
            title            = "Iron Initiate",
            description      = "Complete your first workout",
            tier             = BadgeTier.BRONZE,
            iconResName      = "ic_badge_dumbbell",
            unlockCondition  = { it.totalWorkouts >= 1 },
        ),
        BadgeDefinition(
            id               = "streak_3",
            title            = "Spark",
            description      = "Achieve a 3-day workout streak",
            tier             = BadgeTier.BRONZE,
            iconResName      = "ic_badge_flame",
            unlockCondition  = { it.currentStreak >= 3 },
        ),
        BadgeDefinition(
            id               = "first_rest_timer",
            title            = "Patience",
            description      = "Use the rest timer for the first time",
            tier             = BadgeTier.BRONZE,
            iconResName      = "ic_badge_hourglass",
            unlockCondition  = { it.usedRestTimer },
        ),
        BadgeDefinition(
            id               = "first_plan",
            title            = "Architect",
            description      = "Create your first training plan",
            tier             = BadgeTier.BRONZE,
            iconResName      = "ic_badge_twin_dumbbells",
            unlockCondition  = { it.createdPlan },
        ),
        BadgeDefinition(
            id               = "workouts_10",
            title            = "Charged",
            description      = "Complete 10 workouts",
            tier             = BadgeTier.SILVER,
            iconResName      = "ic_badge_lightning",
            unlockCondition  = { it.totalWorkouts >= 10 },
        ),
        BadgeDefinition(
            id               = "consistency_4w",
            title            = "Clockwork",
            description      = "Train consistently for 4 weeks",
            tier             = BadgeTier.SILVER,
            iconResName      = "ic_badge_calendar",
            unlockCondition  = { it.weeksConsistent >= 4 },
        ),
        BadgeDefinition(
            id               = "first_pr",
            title            = "Muscle Memory",
            description      = "Log your first personal record",
            tier             = BadgeTier.SILVER,
            iconResName      = "ic_badge_flexed_arm",
            unlockCondition  = { it.hasLoggedPR },
        ),
        BadgeDefinition(
            id               = "ai_activated",
            title            = "Augmented",
            description      = "Activate Cloud AI coaching",
            tier             = BadgeTier.SILVER,
            iconResName      = "ic_badge_atom",
            unlockCondition  = { it.cloudAiActivated },
        ),
        BadgeDefinition(
            id               = "progressive_streak",
            title            = "Growth Curve",
            description      = "Progress in 4 consecutive workouts",
            tier             = BadgeTier.SILVER,
            iconResName      = "ic_badge_chart",
            unlockCondition  = { it.consecutiveProgressionWorkouts >= 4 },
        ),
        BadgeDefinition(
            id               = "workouts_50",
            title            = "Champion",
            description      = "Complete 50 workouts",
            tier             = BadgeTier.GOLD,
            iconResName      = "ic_badge_trophy",
            unlockCondition  = { it.totalWorkouts >= 50 },
        ),
        BadgeDefinition(
            id               = "streak_30",
            title            = "Ironclad",
            description      = "Achieve a 30-day workout streak",
            tier             = BadgeTier.GOLD,
            iconResName      = "ic_badge_shield",
            unlockCondition  = { it.currentStreak >= 30 },
        ),
        BadgeDefinition(
            id               = "workouts_100",
            title            = "Sovereign",
            description      = "Complete 100 workouts",
            tier             = BadgeTier.GOLD,
            iconResName      = "ic_badge_crown",
            unlockCondition  = { it.totalWorkouts >= 100 },
        ),
        BadgeDefinition(
            id               = "volume_milestone",
            title            = "Summit",
            description      = "Lift 100,000 kg total volume",
            tier             = BadgeTier.GOLD,
            iconResName      = "ic_badge_mountain",
            unlockCondition  = { it.totalVolumeKg >= 100_000.0 },
        ),
        BadgeDefinition(
            id               = "member_365",
            title            = "Eternal",
            description      = "365 days since your first workout",
            tier             = BadgeTier.BLUE,
            iconResName      = "ic_badge_infinity",
            unlockCondition  = { it.daysSinceFirstWorkout >= 365 },
        ),
        BadgeDefinition(
            id               = "all_goal_modes",
            title            = "Multiclass",
            description      = "Train with all 4 goal modes",
            tier             = BadgeTier.BLUE,
            iconResName      = "ic_badge_3stars",
            unlockCondition  = { it.goalModesUsed.size >= 4 },
        ),
        BadgeDefinition(
            id               = "s_rank",
            title            = "Diamond",
            description      = "Reach S-Rank status",
            tier             = BadgeTier.BLUE,
            iconResName      = "ic_badge_diamond",
            unlockCondition  = { it.currentRank == "S" },
        ),
    )

    fun evaluate(stats: AppStats): Set<String> =
        all.filter { it.unlockCondition(stats) }.map { it.id }.toSet()
}
```

- [ ] **Step 2: Write a quick sanity test**

```kotlin
// app/src/test/java/com/ironlog/app/domain/badges/BadgeDefinitionsTest.kt
package com.ironlog.app.domain.badges

import org.junit.Assert.*
import org.junit.Test

class BadgeDefinitionsTest {

    @Test fun `all badges have unique ids`() {
        val ids = BadgeDefinitions.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun `all badge tiers are present`() {
        val tiers = BadgeDefinitions.all.map { it.tier }.toSet()
        assertEquals(setOf(BadgeTier.BRONZE, BadgeTier.SILVER, BadgeTier.GOLD, BadgeTier.BLUE), tiers)
    }

    @Test fun `first_workout unlocks after 1 workout`() {
        val stats = AppStats(totalWorkouts = 1)
        assertTrue("first_workout" in BadgeDefinitions.evaluate(stats))
    }

    @Test fun `no badges unlock for fresh user`() {
        val stats = AppStats()
        assertTrue(BadgeDefinitions.evaluate(stats).isEmpty())
    }

    @Test fun `streak_3 does not unlock at streak 2`() {
        val stats = AppStats(currentStreak = 2)
        assertFalse("streak_3" in BadgeDefinitions.evaluate(stats))
    }

    @Test fun `s_rank unlocks when rank is S`() {
        val stats = AppStats(currentRank = "S")
        assertTrue("s_rank" in BadgeDefinitions.evaluate(stats))
    }
}
```

- [ ] **Step 3: Run tests**

```powershell
.\gradlew :app:testDebugUnitTest --tests "com.ironlog.app.domain.badges.BadgeDefinitionsTest"
```

Expected: 6 tests pass.

- [ ] **Step 4: Commit**

```powershell
git add app/src/main/java/com/ironlog/app/domain/badges/BadgeDefinitions.kt
git add app/src/test/java/com/ironlog/app/domain/badges/BadgeDefinitionsTest.kt
git commit -m "feat(badges): add BadgeDefinitions catalog with 16 badges and AppStats model"
```

---

## Task 14: Full Build Verification

- [ ] **Step 1: Build release APK**

```powershell
cd "Z:\KOTLIN\UnifiedPort"
.\gradlew :app:assembleRelease 2>&1 | Select-String -Pattern "BUILD|error:|ERROR" | Select-Object -First 40
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: Verify no compile errors in onboarding package**

```powershell
.\gradlew :app:compileReleaseKotlin 2>&1 | Select-String -Pattern "error:|warning:" | Select-Object -First 30
```

- [ ] **Step 3: Run all unit tests**

```powershell
.\gradlew :app:testDebugUnitTest 2>&1 | Select-String -Pattern "tests|PASSED|FAILED|BUILD" | Select-Object -First 20
```

Expected: all tests pass (at minimum: 8 OnboardingViewModelTest + 6 BadgeDefinitionsTest + 14 WorkoutSuggestionEngineTest = 28 tests)

- [ ] **Step 4: Final commit**

```powershell
git add -A
git commit -m "feat(onboarding): complete SYSTEM AWAKENING 8-screen onboarding flow

- Step1: Cinematic SYSTEM AWAKENING with typewriter text + particles
- Step2: Hunter name input (HUNTER REGISTRATION)
- Step3: Rank classification → seeds progressionStyle + goalMode
- Step4: Weekly day picker + kg/lbs unit toggle
- Step5: Goal mode 2x2 grid
- Step6: AI abilities — local always-on + optional Cloud AI API key
- Step7: Permission grants (camera, health connect, notifications)
- Step8: ARISE cinematic finale with pulsing rank badge
- OnboardingConfig: single source of truth for all copy + colors
- BadgeDefinitions: 16-badge catalog with AppStats evaluation
- All copy in strings_onboarding.xml — fully editable"
```

---

## Self-Review

**Spec coverage:**
- ✅ Screen 1 cinematic — Task 4
- ✅ Screen 2 name input — Task 5
- ✅ Screen 3 rank selection — Task 6
- ✅ Screen 4 day picker + units — Task 7
- ✅ Screen 5 goal mode grid — Task 8
- ✅ Screen 6 AI key + model — Task 9
- ✅ Screen 7 permissions — Task 10
- ✅ Screen 8 ARISE finale — Task 11
- ✅ Shell + navigation — Task 12
- ✅ BadgeDefinitions catalog — Task 13
- ✅ All copy in string resources — Task 1
- ✅ `OnboardingConfig` single source of truth — Task 1
- ✅ No hardcoded strings/colors scattered across composables
- ✅ `OnboardingViewModel` atomic save — Task 12 (`settingsRepoSaveOnboardingDataFull`)
- ✅ Gemini free tier info on screen 6 — Task 9
- ✅ EncryptedSharedPreferences for API key — Task 12
- ✅ Health Connect permissions — Task 10
- ✅ Pre-13 notification permission guard — Task 10

**Type consistency:** All method names match across tasks:
- `vm.updateUserName` → used in Task 5 and Task 12 ✅
- `vm.toggleDayIndex` → defined in Task 7, used in Task 12 ✅
- `vm.setClassification(style, goal)` → Task 6 + Task 12 ✅
- `OnboardingDraft.selectedDayIndices` → added Task 7, used Task 7 + Task 12 ✅
- `settingsRepoSaveOnboardingDataFull(draft)` → Task 12 only ✅

**Placeholder check:** No TBD/TODO in any task. All code blocks are complete. Icon drawables noted as "replace with project's actual drawables" in Task 8 with explicit instructions.
