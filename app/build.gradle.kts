import java.util.Properties

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
        versionCode = localProps.getProperty("version.code", "1").toInt()
        versionName = localProps.getProperty("version.name", "1.0.0")
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

}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
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
    implementation("dev.chrisbanes.haze:haze:1.6.7")

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

    // Later / optional — uncomment when needed
    // implementation("app.rive:rive-android:9.7.2")
}
