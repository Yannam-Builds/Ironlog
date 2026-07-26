# IronLog ProGuard / R8 rules

# ── ObjectBox ────────────────────────────────────────────────────────────────
-keep class io.objectbox.** { *; }
-dontwarn io.objectbox.**
# Keep all entity classes and their fields (ObjectBox uses reflection for cursor generation)
-keep @io.objectbox.annotation.Entity class * { *; }
-keepclassmembers @io.objectbox.annotation.Entity class * { *; }

# ── Kotlin serialization ─────────────────────────────────────────────────────
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers @kotlinx.serialization.Serializable class * { *; }
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.** { *; }

# ── Kotlin ───────────────────────────────────────────────────────────────────
-keep class kotlin.Metadata { *; }
-keepattributes Signature, RuntimeVisibleAnnotations, AnnotationDefault

# ── Ktor ─────────────────────────────────────────────────────────────────────
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-keep class kotlinx.coroutines.** { *; }

# ── Security crypto (EncryptedSharedPreferences) ─────────────────────────────
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# ── Gson ─────────────────────────────────────────────────────────────────────
-keep class com.google.gson.** { *; }
-keepattributes *Annotation*
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ── ML Kit Barcode / CameraX ─────────────────────────────────────────────────
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-keep class androidx.camera.** { *; }

# ── Glance widgets ───────────────────────────────────────────────────────────
-keep class androidx.glance.** { *; }
-dontwarn androidx.glance.**

# ── Compose ──────────────────────────────────────────────────────────────────
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
-keepclassmembers class * extends androidx.lifecycle.ViewModel { <init>(...); }

# ── Lottie ───────────────────────────────────────────────────────────────────
-keep class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**

# ── Coil ─────────────────────────────────────────────────────────────────────
-keep class coil3.** { *; }
-dontwarn coil3.**

# ── Vico charts ──────────────────────────────────────────────────────────────
-keep class com.patrykandpatrick.vico.** { *; }
-dontwarn com.patrykandpatrick.vico.**

# ── Timber ───────────────────────────────────────────────────────────────────
-keep class timber.log.** { *; }

# ── Health Connect ───────────────────────────────────────────────────────────
-keep class androidx.health.connect.** { *; }
-dontwarn androidx.health.connect.**

# ── Haze blur ────────────────────────────────────────────────────────────────
-keep class dev.chrisbanes.haze.** { *; }
-dontwarn dev.chrisbanes.haze.**

# ── Gemini Nano AICore ───────────────────────────────────────────────────────
-keep class com.google.ai.edge.** { *; }
-dontwarn com.google.ai.edge.**

# ── WorkManager ──────────────────────────────────────────────────────────────
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }
-keepclassmembers class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ── QR codec ─────────────────────────────────────────────────────────────────
-keep class io.github.g0dkar.** { *; }
-dontwarn io.github.g0dkar.**

# ── App data models (backup/restore JSON mapping via Gson/kotlinx) ────────────
-keep class com.ironlog.app.data.model.** { *; }
-keep class com.ironlog.app.ui.model.** { *; }
-keep class com.ironlog.app.domain.gamification.** { *; }
-keep class com.ironlog.app.domain.badges.** { *; }

# ── Keep enums ───────────────────────────────────────────────────────────────
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── Suppress common harmless warnings ────────────────────────────────────────
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn sun.misc.**
-dontwarn javax.annotation.**
