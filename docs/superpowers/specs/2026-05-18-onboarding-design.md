# IronLog Onboarding — Design Spec
**Date:** 2026-05-18  
**Status:** Approved

---

## Overview

An 8-screen cinematic onboarding flow with Solo Leveling "SYSTEM AWAKENING" aesthetic. Every screen is dark (near-black background), electric-blue (`#4FC3F7`) or gold (`#FFD700`) accent glows, particle shimmer effects, and rank-badge motifs. The user feels like they are entering a game, not filling a form.

**All text labels, copy, color tokens, and screen ordering are defined in a single `OnboardingConfig.kt` data file** — no hardcoded strings or values scattered across composables. Any screen can be reordered, retitled, or reskinned by editing that one file.

---

## Architecture

### Files to create / modify

| File | Role |
|------|------|
| `ui/screens/OnboardingScreen.kt` | Root composable — `HorizontalPager`, progress dots, nav |
| `ui/screens/onboarding/OnboardingConfig.kt` | All static copy, color tokens, step definitions |
| `ui/screens/onboarding/OnboardingViewModel.kt` | Holds transient state; performs atomic save on completion |
| `ui/screens/onboarding/steps/Step1Awakening.kt` | Cinematic splash |
| `ui/screens/onboarding/steps/Step2Registration.kt` | Hunter name input |
| `ui/screens/onboarding/steps/Step3Classification.kt` | Rank / training level |
| `ui/screens/onboarding/steps/Step4Quota.kt` | Weekly days + unit toggle |
| `ui/screens/onboarding/steps/Step5GoalMode.kt` | Goal mode 2×2 grid |
| `ui/screens/onboarding/steps/Step6AiAbilities.kt` | AI tier selector + Cloud API key |
| `ui/screens/onboarding/steps/Step7Permissions.kt` | Camera / Health Connect / Notifications |
| `ui/screens/onboarding/steps/Step8Arise.kt` | Cinematic finale + ARISE CTA |
| `navigation/AppNavigator.kt` | Already routes to "Onboarding"; minor update to pass VM |
| `domain/badges/BadgeDefinitions.kt` | Badge catalog used by gamification (16 badges) |
| `res/values/strings_onboarding.xml` | String resources for all onboarding copy |

### State management

`OnboardingViewModel` holds:
```kotlin
data class OnboardingDraft(
    val userName: String = "",
    val progressionStyle: ProgressionStyle = ProgressionStyle.LINEAR,
    val goalMode: GoalMode = GoalMode.STRENGTH,
    val weeklyGoalDays: Int = 3,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val cloudAiApiKey: String = "",
    val cloudAiModelName: String = "gemini-2.0-flash",
    val cloudAiProviderPreset: CloudAiProviderPreset = CloudAiProviderPreset.GEMINI,
    val intelligenceMode: IntelligenceMode = IntelligenceMode.LOCAL,
    val cameraGranted: Boolean = false,
    val healthConnectGranted: Boolean = false,
    val notificationsGranted: Boolean = false,
)
```

On Screen 8 "ARISE" tap: `SettingsRepository.saveOnboardingData(draft)` called atomically, then `onboardingComplete = true`, navigate to Tabs.

### Navigation

- Forward-only: swipe disabled on `HorizontalPager`, all progress via explicit CTA buttons
- Screen 1 auto-advances after 2.5 s via `LaunchedEffect`
- Progress dots shown on screens 2–8 (not on cinematic screens 1 and 8)
- Back navigation: system back moves to previous page (handled by `HorizontalPager` `userScrollEnabled = false`, manual back via `PagerState.scrollToPage`)

---

## Screen-by-Screen Specification

### Screen 1 — SYSTEM AWAKENING

**Purpose:** Cinematic tone-setter. No user input.

**Layout:**
- Full-screen black (`Color(0xFF050508)`)
- Particle field: 40–60 small white/blue dots drifting upward via `rememberInfiniteTransition`
- IronLog logo: fades in from alpha 0 → 1 over 1.2 s, accompanied by shimmer sweep (horizontal gradient mask animated left-to-right)
- Typewriter text below logo:
  - Line 1: `"THE SYSTEM HAS DETECTED A NEW HUNTER"` (letter-by-letter, 40ms/char)
  - Line 2: `"INITIALIZING REGISTRATION PROTOCOL..."` (starts after Line 1 finishes)
- Auto-advance after 2.5 s total

**Colors:** Background `#050508`, text `#4FC3F7` (electric blue), logo glow `#4FC3F7` at 60% alpha

---

### Screen 2 — HUNTER REGISTRATION

**Purpose:** Collect `userName`.

**Layout:**
- Header: `"HUNTER DESIGNATION"` (all-caps, letter-spacing 4sp, `#4FC3F7`)
- Subtext: `"Enter your designation, Hunter"` (muted gray)
- Single `OutlinedTextField` centered vertically:
  - Focused border: animated glow `#4FC3F7`
  - Unfocused border: `#4FC3F7` at 30% alpha
  - Cursor: electric blue
  - `keyboardOptions = KeyboardOptions(capitalization = Words, imeAction = Done)`
- CTA button `"CONFIRM IDENTITY →"`: disabled (50% alpha) until `userName.isNotBlank()`
- Keyboard opens automatically via `LaunchedEffect(Unit) { focusRequester.requestFocus() }`

**Validation:** 1–30 characters. Trims whitespace before save.

---

### Screen 3 — TRAINING CLASSIFICATION

**Purpose:** Set `progressionStyle` and seed `goalMode`.

**Layout:**
- Header: `"ASSESS YOUR CURRENT RANK"`
- Subtext: `"The System will calibrate your training path"`
- Three cards displayed horizontally (full-width, scrollable if screen narrow):

| Card | Rank Badge | Label | Description | progressionStyle |
|------|-----------|-------|-------------|-----------------|
| Left | E (gray) | `NOVICE` | `"Starting my journey"` | `LINEAR` |
| Center | C (silver) | `INTERMEDIATE` | `"Training 6+ months"` | `DOUBLE_PROGRESSION` |
| Right | A (gold) | `ADVANCED` | `"2+ years, serious lifter"` | `UNDULATING` |

- Selected card: `#4FC3F7` border (2dp glow), rank badge brightens to full opacity
- Unselected: border `#4FC3F7` at 20% alpha, badge at 60% alpha
- Auto-advance on card tap (no separate CTA button needed)
- Seeds `goalMode` default: NOVICE → STRENGTH, INTERMEDIATE → HYPERTROPHY, ADVANCED → PERFORMANCE

**Rank badge component:** Reusable `RankBadge(rank: String, color: Color, size: Dp)` composable showing letter in metallic ring.

---

### Screen 4 — WEEKLY MISSION QUOTA

**Purpose:** Set `weeklyGoalDays` and `weightUnit`.

**Layout:**
- Header: `"SET YOUR WEEKLY OBJECTIVE"`
- Day picker: 7 dots in a row, labeled `M T W T F S S`
  - Dot size: 40dp
  - Selected: filled `#4FC3F7`, white letter, drop shadow glow
  - Unselected: outlined `#4FC3F7` at 30% alpha, muted letter
  - Tap toggles. Rule: minimum 1 day must always be selected (if user deselects last active dot, snap back)
  - Days selected need not be consecutive — user picks any combination
  - `weeklyGoalDays = selectedDots.size`
- Live counter below dots: `"{N} SESSIONS PER WEEK"` (large, gold)
- **Unit toggle** (secondary, below counter):
  - Pill toggle: `[ KG ] [ LBS ]`
  - Selected side: `#4FC3F7` background, white text
  - Unselected: transparent background, muted text
- CTA: `"SET OBJECTIVE →"`

---

### Screen 5 — GOAL MODE

**Purpose:** Confirm / override `goalMode`.

**Layout:**
- Header: `"CHOOSE YOUR COMBAT STYLE"`
- 2×2 grid of large tappable cards (each ~160×180dp):

| Position | Icon | Label | goalMode |
|----------|------|-------|----------|
| Top-left | Flexed arm (💪) | `STRENGTH` | `STRENGTH` |
| Top-right | Trophy (🏆) | `HYPERTROPHY` | `HYPERTROPHY` |
| Bottom-left | Lightning (⚡) | `PERFORMANCE` | `PERFORMANCE` |
| Bottom-right | Runner (🏃) | `ENDURANCE` | `ENDURANCE` |

- Icons: use vector drawables from `res/drawable/` (no emoji in production code)
- Selected card: gold border glow, background tint `#FFD700` at 8% alpha
- Subtext per card (1 line): Strength → `"Max weight, low reps"` / Hypertrophy → `"Size & muscle growth"` / Performance → `"Speed & power"` / Endurance → `"High volume, stamina"`
- Pre-selected based on Screen 3 seed
- CTA: `"LOCK IN →"`

---

### Screen 6 — UNLOCK AI ABILITIES

**Purpose:** Set `intelligenceMode`, optionally add cloud API key.

**Layout:**
- Header: `"ENHANCE YOUR HUNTER ABILITIES"`
- Subtext: `"Unlock AI-powered coaching and adaptive recommendations"`

**Tier A — On-Device (always active, shown as already-unlocked):**
- Small card, gray border, lock-open icon
- `"ON-DEVICE INTELLIGENCE — ACTIVE"`
- Subtext: `"Recovery scoring, workout suggestion, effort tracking"`
- No interaction needed

**Tier B — Cloud AI (optional expand):**
- Large card with lock icon and `"CLOUD ABILITIES"` header
- Default collapsed state shows: lock icon + `"TAP TO UNLOCK CLOUD AI COACHING"`
- Tap expands card with `AnimatedContent`:
  - Provider row: `"API PROVIDER"` label + dropdown chip `[GOOGLE GEMINI ▼]`
    - Options: `Gemini`, `OpenAI`, `Custom`
  - API key field: `OutlinedTextField` with `visualTransformation = PasswordVisualTransformation()` + eye toggle
    - Placeholder: `"Paste your API key here"`
    - Helper text: `"🔒 Stored locally in encrypted SharedPreferences, never uploaded"`
  - Recommended badge for Gemini (shown when Gemini selected):
    ```
    ✦ GEMINI 2.0 FLASH  —  RECOMMENDED (FREE)
      1,500 requests / day  ·  15 req/min  ·  1M token context
      → Get free key at aistudio.google.com
    ```
  - Model selector (dropdown chip):
    - Gemini: `2.0 Flash (free, 1500/day)`, `2.5 Flash (250/day)`, `2.5 Pro (100/day)`
    - OpenAI: `gpt-4o-mini`, `gpt-4o`
    - Custom: freeform model name field
  - Validation: Gemini keys start with `AIza` (show inline warning if format wrong)
  - When valid key entered → card border turns `#4FC3F7`, shows `"CLOUD AI READY ✓"`

- `intelligenceMode`:
  - No key entered → `LOCAL`
  - Valid key entered → `AUTO` (device + cloud blend)

- CTA at bottom: `"ACTIVATE →"` (or `"SKIP FOR NOW"` secondary link)

**Key storage:** `EncryptedSharedPreferences` (already in `build.gradle.kts` via `security-crypto`). Field: `"cloud_ai_api_key"`.

---

### Screen 7 — GRANT PERMISSIONS

**Purpose:** Request CAMERA, Health Connect, POST_NOTIFICATIONS.

**Layout:**
- Header: `"UNLOCK HUNTER ABILITIES"`
- Subtext: `"Each permission activates a System feature. All optional."`
- Three permission cards stacked vertically:

| Icon | Title | Description | Permission |
|------|-------|-------------|------------|
| Camera icon | `SCANNER — QR SHARING` | `"Scan & share workout plans as QR codes"` | `CAMERA` |
| Heart icon | `VITALS — HEALTH CONNECT` | `"Auto-import sleep, HRV, heart rate for recovery scores"` | Health Connect bundle |
| Bell icon | `ALERTS — NOTIFICATIONS` | `"Rest timer, streak reminders, daily check-ins"` | `POST_NOTIFICATIONS` |

**Per card UI:**
- Right side: `[GRANT]` button chip (outlined, `#4FC3F7`)
- On grant → chip changes to `[GRANTED ✓]` (filled green-teal, no longer tappable)
- On denial → chip shows `[DENIED]` (muted red outline) with note `"Grant later in Settings"`
- Cards use `rememberLauncherForActivityResult`:
  - Camera: `ActivityResultContracts.RequestPermission(Manifest.permission.CAMERA)`
  - Notifications: `ActivityResultContracts.RequestPermission(Manifest.permission.POST_NOTIFICATIONS)` (Android 13+ guard: `if (Build.VERSION.SDK_INT >= 33)`)
  - Health Connect: `PermissionController.createRequestPermissionResultContract()` with set of HC permissions (sleep, resting HR, HRV, exercise session write)

**Health Connect permissions set:**
```kotlin
setOf(
    HealthPermission.getReadPermission(SleepSessionRecord::class),
    HealthPermission.getReadPermission(RestingHeartRateRecord::class),
    HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
    HealthPermission.getWritePermission(ExerciseSessionRecord::class),
)
```

- CTA: `"CONTINUE →"` (always active — all permissions optional)

---

### Screen 8 — ARISE

**Purpose:** Cinematic finale, final save, navigate to app.

**Layout:**
- Full-screen black
- Particle burst animation from screen center (200 particles, outward explosion over 1.5 s, then fade)
- Rank E badge (`RankBadge("E", Color(0xFF4FC3F7), 120.dp)`) materializes at center with pulsing glow ring
- Text animates in below badge:
  - `"HUNTER [userName]"` (gold, large)
  - `"REGISTRATION COMPLETE"` (electric blue, medium, letter-spaced)
  - `"THE SYSTEM ACKNOWLEDGES YOUR EXISTENCE."` (muted, small)
  - `"YOUR JOURNEY BEGINS NOW."` (muted, small)
- CTA: `[          ARISE          ]` — wide glowing button, electric blue gradient, shadow glow
  - On tap:
    1. `OnboardingViewModel.completeOnboarding()` → saves all draft fields atomically
    2. `onboardingComplete = true` in `AppDataState`
    3. Navigate to `"Tabs"`, `popUpTo("Onboarding") { inclusive = true }`
    4. Post-navigation: show widget add bottom sheet (see below)

**Widget prompt (bottom sheet, shown after navigation):**
- Triggered by `LaunchedEffect` in `HomeScreen` when `justCompletedOnboarding = true`
- Title: `"Add IronLog to Your Home Screen"`
- Body: `"Quick-start workouts and check your streak without opening the app."`
- Button: `"ADD WIDGET"` → deep link to widget picker intent
- Dismiss: `"Maybe Later"` link

---

## OnboardingConfig.kt — Single Source of Truth

All copy, colors, step ordering, and badge definitions must live in this file:

```kotlin
object OnboardingConfig {
    val steps: List<OnboardingStep> = listOf(
        OnboardingStep.Awakening,
        OnboardingStep.Registration,
        OnboardingStep.Classification,
        OnboardingStep.Quota,
        OnboardingStep.GoalMode,
        OnboardingStep.AiAbilities,
        OnboardingStep.Permissions,
        OnboardingStep.Arise,
    )

    val accentBlue = Color(0xFF4FC3F7)
    val accentGold = Color(0xFFFFD700)
    val backgroundDark = Color(0xFF050508)
    val surfaceDark = Color(0xFF0D0D14)
    val cardBorder = Color(0xFF1A1A2E)

    // AI model options
    val geminiModels = listOf(
        AiModelOption("gemini-2.0-flash", "Gemini 2.0 Flash", "Free · 1,500 req/day · 1M ctx"),
        AiModelOption("gemini-2.5-flash", "Gemini 2.5 Flash", "Free · 250 req/day · 250K ctx"),
        AiModelOption("gemini-2.5-pro", "Gemini 2.5 Pro", "Free · 100 req/day · 1M ctx"),
    )

    val geminiKeyPrefix = "AIza"
    val aistudioUrl = "https://aistudio.google.com/app/apikey"
}
```

---

## Badge Definitions

All 16 badges from the approved badge sheet are defined in `BadgeDefinitions.kt`:

```kotlin
enum class BadgeTier { BRONZE, SILVER, GOLD, BLUE }

data class BadgeDefinition(
    val id: String,
    val title: String,
    val description: String,
    val tier: BadgeTier,
    val iconResId: Int,        // vector drawable resource
    val unlockCondition: (AppStats) -> Boolean,
)
```

| id | Title | Tier | Icon | Unlock Condition |
|----|-------|------|------|-----------------|
| `first_workout` | Iron Initiate | BRONZE | dumbbell | 1 workout logged |
| `streak_3` | Spark | BRONZE | flame | 3-day workout streak |
| `workouts_10` | Charged | SILVER | lightning | 10 workouts total |
| `consistency_4w` | Clockwork | SILVER | calendar | Worked out 4 weeks in a row |
| `first_pr` | Muscle Memory | SILVER | flexed arm | First personal record logged |
| `workouts_50` | Champion | GOLD | trophy | 50 workouts total |
| `streak_30` | Ironclad | GOLD | shield | 30-day streak |
| `workouts_100` | Sovereign | GOLD | crown | 100 workouts total |
| `member_365` | Eternal | BLUE | infinity | 365 days since first workout |
| `all_goal_modes` | Multiclass | BLUE | 3 stars | Used all 4 goal modes |
| `ai_activated` | Augmented | SILVER | atom | Cloud AI key entered |
| `volume_milestone` | Summit | GOLD | mountain | Lifted 100,000 kg total volume |
| `first_rest_timer` | Patience | BRONZE | hourglass | Used rest timer first time |
| `first_plan` | Architect | BRONZE | twin dumbbells | Created first training plan |
| `progressive_streak` | Growth Curve | SILVER | chart | 4 consecutive workouts with progression |
| `s_rank` | Diamond | BLUE | diamond | S-Rank achieved (defined by XP threshold in gamification plan) |

---

## Settings Fields Written

| Field | Type | Screen |
|-------|------|--------|
| `userName` | String | 2 |
| `progressionStyle` | Enum | 3 |
| `goalMode` | Enum | 5 |
| `weeklyGoalDays` | Int | 4 |
| `weightUnit` | Enum | 4 |
| `cloudAiApiKey` | String (encrypted) | 6 |
| `cloudAiModelName` | String | 6 |
| `cloudAiProviderPreset` | Enum | 6 |
| `intelligenceMode` | Enum | 6 (AUTO if key set, LOCAL otherwise) |
| `onboardingComplete` | Boolean | 8 |

---

## Animations & Effects Summary

| Effect | Implementation |
|--------|----------------|
| Particle drift (screens 1 & 8) | Custom `Canvas` composable, `rememberInfiniteTransition` |
| Particle burst (screen 8) | `LaunchedEffect` driven `mutableStateListOf<ParticleState>` |
| Logo shimmer sweep | Animated gradient mask, `Brush.linearGradient` with animated offset |
| Typewriter text | `LaunchedEffect` with `delay(40)` per character, `StringBuilder` |
| Card glow border | `Modifier.border(...)` with `animateColorAsState` |
| Pulsing glow ring | `rememberInfiniteTransition`, `animateFloat` 0.4f→1.0f, 900ms Reverse |
| Screen transition | `HorizontalPager` default slide |
| Permission chip state | `animateColorAsState(tween(300))` |

---

## Testing Requirements

- Unit: `OnboardingViewModelTest` — all state transitions, `completeOnboarding()` saves all fields
- Unit: `OnboardingConfigTest` — steps list non-empty, all required fields present
- Integration: `OnboardingFlowTest` — full screen-to-screen navigation, final state verification
- Permission handling: mocked `ActivityResultLauncher` for all three permission types
