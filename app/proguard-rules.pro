# ==============================================================================
# AI Interview Assistant - Production ProGuard / R8 Rules
# Target Platform: Android 14 to Android 17 (API 34-37)
# R8 Mode: Full Mode Compatible
# ==============================================================================

# ------------------------------------------------------------------------------
# 1. Native JNI Bindings: LiteRT-LM & Sherpa-ONNX
# ------------------------------------------------------------------------------
-keep class com.google.ai.edge.litertlm.** { *; }
-keepclasseswithmembernames class com.google.ai.edge.litertlm.** {
    native <methods>;
}

-keep class com.k2fsa.sherpa.onnx.** { *; }
-keepclassmembers class com.k2fsa.sherpa.onnx.** {
    *;
}
-keepclasseswithmembernames class com.k2fsa.sherpa.onnx.** {
    native <methods>;
}

# Preserve global JNI native method signatures
-keepclasseswithmembernames class * {
    native <methods>;
}

# ------------------------------------------------------------------------------
# 2. Missing Consumer Rules Suppression (-dontwarn)
# ------------------------------------------------------------------------------
# LiteRT-LM and Sherpa-ONNX AARs lack embedded proguard.txt consumer rules.
# Suppress unresolved references to optional/desktop libraries and annotations:
-dontwarn com.google.ai.edge.litertlm.**
-dontwarn com.k2fsa.sherpa.onnx.**
-dontwarn org.jetbrains.annotations.**

# ------------------------------------------------------------------------------
# 3. Domain Models, Enums & JSON Serialization (CRITICAL ENUM RETENTION)
# ------------------------------------------------------------------------------
# Must use '-keep enum' AND '-keepclassmembers enum' so both enum class names
# and values/methods are preserved for InterviewCategory.valueOf() in SessionHistoryRepository.
-keep enum com.local.interviewassistant.** {
    *;
}
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    *;
}

# Preserve all core data models and StateFlow structures
-keep class com.local.interviewassistant.core.model.** {
    *;
}

# Preserve Session History and Telemetry models
-keep class com.local.interviewassistant.data.history.** {
    *;
}

# Preserve Hardware Profiles and Delegate types
-keep class com.local.interviewassistant.data.hardware.** {
    *;
}

# Preserve Model Provisioning models and descriptors
-keep class com.local.interviewassistant.data.provisioning.** {
    *;
}

# Preserve Reflection & Signature Metadata
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# ------------------------------------------------------------------------------
# 4. Kotlin Coroutines & Flow Internals
# ------------------------------------------------------------------------------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler { *; }
-keepclassmembernames class kotlinx.coroutines.** {
    volatile <fields>;
}

# ------------------------------------------------------------------------------
# 5. AndroidX Architecture, WorkManager & Jetpack Compose
# ------------------------------------------------------------------------------
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class androidx.lifecycle.LifecycleService { *; }
-keep class androidx.compose.runtime.** { *; }

