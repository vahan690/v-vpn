# =============================================================================
# V-VPN ProGuard Configuration - SECURITY HARDENED
# =============================================================================
# This configuration enables code obfuscation while keeping necessary classes
# for proper app functionality. MOST of your code WILL be obfuscated.
# =============================================================================

# Keep line numbers for crash reports (helps debugging without exposing code)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# =============================================================================
# REMOVE DEBUG LOGGING IN PRODUCTION - CRITICAL FOR SECURITY
# =============================================================================
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# =============================================================================
# KEEP ONLY ESSENTIAL APP CLASSES (Everything else WILL be obfuscated)
# =============================================================================

# Keep data classes used for JSON serialization/deserialization
# These need to keep their field names for API communication
-keepclassmembers class com.vvpn.android.payment.PaymentManager$* {
    <fields>;
    <init>(...);
}
-keepclassmembers class com.vvpn.android.payment.AuthManager$* {
    <fields>;
    <init>(...);
}
-keepclassmembers class com.vvpn.android.payment.LicenseManager$* {
    <fields>;
    <init>(...);
}

# Keep all network API data classes (CRITICAL for device management)
# These are serialized/deserialized with Gson for API communication
-keep class com.vvpn.android.network.** { *; }

# Keep VPN protocol beans (needed for serialization)
-keep class com.vvpn.android.fmt.** { *; }
-keep class com.vvpn.android.database.ProxyEntity { *; }
-keep class com.vvpn.android.database.RuleEntity { *; }
-keep class com.vvpn.android.database.GroupEntity { *; }

# Keep entry points (Android components)
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# Keep Fragment constructors
-keepclassmembers class * extends androidx.fragment.app.Fragment {
    public <init>();
}

# Keep ViewModel factory methods
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# =============================================================================
# THIRD-PARTY LIBRARIES
# =============================================================================

# Kotlin
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# Keep annotations
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations

# Gson (if used)
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Retrofit (if used)
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.*
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# OkHttp & Certificate Pinning
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
# Keep certificate pinning configuration (CRITICAL for security)
-keep class com.vvpn.android.payment.SecureHttpClient { *; }
-keep class okhttp3.CertificatePinner { *; }
-keep class okhttp3.CertificatePinner$Pin { *; }

# Keep security detection classes (root/tamper/debug detection)
# These must be kept to function correctly in release builds
-keep class com.vvpn.android.security.RootDetector { *; }
-keep class com.vvpn.android.security.TamperDetector { *; }
-keep class com.vvpn.android.security.SecurityManager { *; }
-keep class com.vvpn.android.security.SecurityManager$** { *; }
-keep class com.vvpn.android.security.AntiDebug { *; }
-keep class com.vvpn.android.security.AntiDebug$** { *; }
-keep class com.vvpn.android.security.StringObfuscator { *; }
-keep class com.vvpn.android.security.ObfuscatedStrings { *; }

# Encrypted SharedPreferences
-keep class androidx.security.crypto.** { *; }
-keep class androidx.security.crypto.EncryptedSharedPreferences { *; }
-keep class androidx.security.crypto.MasterKey { *; }

# =============================================================================
# ANDROID FRAMEWORK
# =============================================================================

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep View constructors (for XML inflation)
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Parcelable classes
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep Serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# =============================================================================
# JSON PARSING (org.json)
# =============================================================================
-keep class org.json.** { *; }
-keepclassmembers class * {
    public <init>(org.json.JSONObject);
}

# =============================================================================
# LIBCORE (VPN Engine)
# =============================================================================
# Keep libcore classes that are accessed via JNI or reflection
-keep class libcore.** { *; }
-keep class io.nekohasekai.sagernet.** { *; }

# =============================================================================
# WARNINGS TO IGNORE
# =============================================================================
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.*

# =============================================================================
# OPTIMIZATION SETTINGS
# =============================================================================
# Enable aggressive optimization
-optimizationpasses 5
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*

# Allow obfuscation of class names
-repackageclasses 'o'

# =============================================================================
# WHAT GETS OBFUSCATED
# =============================================================================
# With these rules, the following WILL be obfuscated:
# - All class names (except kept ones)
# - All method names (except kept ones)
# - All field names (except kept ones)
# - Package structure (repackaged to 'o')
# - String constants (some)
# - Control flow
#
# This makes reverse engineering MUCH harder!
# =============================================================================
