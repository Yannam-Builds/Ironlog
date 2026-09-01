import java.util.Properties
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose") // Kotlin 2.x Compose compiler plugin
}
apply(plugin = "io.objectbox")

// Pin Kotlin/serialization artifacts to 2.1.0 (Kotlin 2.x baseline).
configurations.all {
    resolutionStrategy.force(
        "org.jetbrains.kotlin:kotlin-stdlib:2.1.0",
        "org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.1.0",
        "org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.1.0",
        "org.jetbrains.kotlin:kotlin-stdlib-common:2.1.0",
        "org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.3",
        "org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3"
    )
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    namespace = "com.ironlog.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ironlogpro.app"
        minSdk = 26
        targetSdk = 36
        versionCode = providers.gradleProperty("IRONLOG_VERSION_CODE").orNull?.toIntOrNull()
            ?: localProps.getProperty("version.code", "9").toInt()
        versionName = providers.gradleProperty("IRONLOG_VERSION_NAME").orNull
            ?: localProps.getProperty("version.name", "0.1.0-pre-alpha.8")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures { compose = true; buildConfig = true }
    // composeOptions.kotlinCompilerExtensionVersion is no longer needed with the
    // org.jetbrains.kotlin.plugin.compose plugin (Kotlin 2.x). The compiler is
    // bundled with the plugin and automatically matches the Kotlin version.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    signingConfigs {
        create("release") {
            storeFile = file("ironlog-release.jks")
            storePassword = localProps.getProperty("signing.storePassword", "")
            keyAlias = "Ironlog"
            keyPassword = localProps.getProperty("signing.keyPassword", "")
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-qa"
            resValue("string", "app_name", "IronLog QA")
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    sourceSets.getByName("debug").assets.srcDir(layout.buildDirectory.dir("generated/ironlogQaAssets"))

}

val generateIronLogQaFixture by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/ironlogQaAssets")
    outputs.dir(outputDir)
    doLast {
        val destination = outputDir.get().file("ironlog_qa_fixture.json").asFile
        destination.delete()
        val configured = localProps.getProperty("ironlog.debugFixturePath")?.trim().orEmpty()
        if (configured.isBlank()) return@doLast
        val source = file(configured)
        require(source.isFile) { "Configured IronLog QA fixture does not exist: $configured" }

        @Suppress("UNCHECKED_CAST")
        val root = JsonSlurper().parse(source) as MutableMap<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val data = root["data"] as? MutableMap<String, Any?>
            ?: error("Configured IronLog QA fixture has no data object")
        fun rows(key: String): List<MutableMap<String, Any?>> =
            (data[key] as? List<*>)?.mapNotNull { it as? MutableMap<String, Any?> } ?: emptyList()
        fun id(row: Map<String, Any?>, key: String = "id") = row[key]?.toString().orEmpty()

        val exercises = rows("exercises")
        val exerciseIds = exercises.map { id(it) }.filter(String::isNotBlank).toSet()
        val plans = rows("plans")
        val planIds = plans.map { id(it) }.toSet()
        val planDays = rows("plan_days").filter { id(it, "plan_id") in planIds }
        val dayIds = planDays.map { id(it) }.toSet()
        val planExercises = rows("plan_exercises").filter {
            id(it, "plan_day_id") in dayIds && id(it, "exercise_id") in exerciseIds
        }
        val workouts = rows("workouts").filter { it["status"]?.toString() == "completed" }
        val workoutIds = workouts.map { id(it) }.toSet()
        val workoutExercises = rows("workout_exercises").filter {
            id(it, "workout_id") in workoutIds && id(it, "exercise_id") in exerciseIds
        }
        val workoutExerciseIds = workoutExercises.map { id(it) }.toSet()
        val workoutSets = rows("workout_sets").filter { id(it, "workout_exercise_id") in workoutExerciseIds }

        val now = System.currentTimeMillis()
        val safeSettings = listOf(
            mutableMapOf<String, Any?>("id" to "exercise_seed_complete", "key" to "exercise_seed_complete", "value" to "true", "value_type" to "boolean", "updated_at" to now),
            mutableMapOf<String, Any?>("id" to "onboarding_complete", "key" to "onboarding_complete", "value" to "true", "value_type" to "boolean", "updated_at" to now),
            mutableMapOf<String, Any?>(
                "id" to "ironlog_settings", "key" to "ironlog_settings", "value_type" to "json", "updated_at" to now,
                "value" to JsonOutput.toJson(mapOf(
                    "theme" to "dark", "weightUnit" to "kg", "hapticFeedback" to true,
                    "effortTracking" to "off", "defaultRestSeconds" to 90,
                    "defaultRestHeavySeconds" to 180, "barWeightKg" to 20,
                    "weeklyGoalDays" to 4, "goalMode" to "hypertrophy",
                    "progressionStyle" to "balanced", "userName" to "QA Athlete",
                    "performanceMode" to "balanced", "intelligenceMode" to "built_in",
                )),
            ),
        )
        val safeCalibration = listOf(mutableMapOf<String, Any?>(
            "offline_user_id" to "local", "training_age_months" to 6,
            "historical_training_days_per_week" to 3, "first_verified_session_at" to 0,
            "weight_unit" to "kg", "bodyweight_kg" to 70, "goal_mode" to "hypertrophy",
            "weekly_goal_days" to 4, "imported_history" to false, "confidence" to 0.5,
            "updated_at" to now, "has_past_training" to true, "has_gym_access" to true,
            "baseline_pushups" to 0, "baseline_pullups" to 0, "baseline_bench_kg" to 0,
            "baseline_lat_pulldown_kg" to 0, "baseline_mile_run_seconds" to 0,
        ))

        data["exercises"] = exercises
        data["exercise_muscles"] = rows("exercise_muscles").filter { id(it, "exercise_id") in exerciseIds }
        data["plans"] = plans
        data["plan_days"] = planDays
        data["plan_exercises"] = planExercises
        data["workouts"] = workouts
        data["workout_exercises"] = workoutExercises
        data["workout_sets"] = workoutSets
        data["body_measurements"] = emptyList<Any>()
        data["progress_photos"] = emptyList<Any>()
        data["app_settings"] = safeSettings
        data["athlete_calibrations"] = safeCalibration
        data["gamification_profiles"] = emptyList<Any>()
        data["iron_ledger_events"] = emptyList<Any>()
        destination.parentFile.mkdirs()
        destination.writeText(JsonOutput.toJson(root), Charsets.UTF_8)
    }
}

tasks.matching { it.name == "mergeDebugAssets" }.configureEach { dependsOn(generateIronLogQaFixture) }
tasks.matching {
    it.name == "lintAnalyzeDebug" ||
        it.name == "generateDebugLintReportModel" ||
        it.name == "generateDebugAndroidTestLintModel" ||
        it.name == "generateDebugUnitTestLintModel"
}.configureEach { dependsOn(generateIronLogQaFixture) }

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("androidx.lifecycle:lifecycle-process:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.navigation:navigation-compose:2.8.9")
    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.google.code.gson:gson:2.11.0")
    // Ktor Client — coroutine-native HTTP, replaces raw OkHttp in CloudAiEngine
    val ktorVersion = "3.1.3"
    implementation("io.ktor:ktor-client-android:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.objectbox:objectbox-kotlin:5.4.2")
    kapt("io.objectbox:objectbox-processor:5.4.2")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Haze — hardware blur for tab bar frosted glass
    // 1.6.8 fixes the startup pre-draw invalidation loop (upstream #725).
    implementation("dev.chrisbanes.haze:haze:1.6.8")

    // V1 premium UI
    implementation("com.airbnb.android:lottie-compose:6.7.1")
    implementation("sh.calvin.reorderable:reorderable:3.1.0")
    implementation("com.valentinilk.shimmer:compose-shimmer:1.3.1")
    implementation("io.github.vinceglb:confettikit-android:0.4.0")

    // Gemini Nano on-device inference via Android AICore
    implementation("com.google.ai.edge.aicore:aicore:0.0.1-exp02")

    // API key encryption — EncryptedSharedPreferences backed by Android Keystore
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Health Connect (replaces deprecated Google Fit)
    implementation("androidx.health.connect:connect-client:1.1.0-alpha07")

    // Jetpack Glance — Compose-based app widgets
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")

    // Vico — Compose chart library (replaces hand-drawn Canvas charts)
    implementation("com.patrykandpatrick.vico:compose-m3:2.1.2")

    // Accompanist Permissions — Compose-native permission state (replaces ActivityResultContracts boilerplate)
    implementation("com.google.accompanist:accompanist-permissions:0.37.2")

    // Coil 3 — async image loading, memory-safe (replaces BitmapFactory boilerplate)
    implementation("io.coil-kt.coil3:coil-compose:3.2.0")
    implementation("io.coil-kt.coil3:coil-android:3.2.0") // required for content:// Uri decoding on Android

    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
    implementation("com.jakewharton.timber:timber:5.0.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.06.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Later / optional — uncomment when needed
    // implementation("app.rive:rive-android:9.7.2")
}
