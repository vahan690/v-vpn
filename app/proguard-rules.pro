# Keep line numbers for debugging
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep your app classes (updated package name)
-keep class com.vvpn.android.** { *; }

# Keep payment, auth, and license classes specifically
-keep class com.vvpn.android.payment.** { *; }
-keep class com.vvpn.android.ui.** { *; }

# Keep all data classes and their fields for JSON serialization
-keepclassmembers class com.vvpn.android.payment.** {
    <fields>;
    <init>(...);
}

# Keep Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# Keep Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Keep Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# Keep encrypted shared preferences
-keep class androidx.security.crypto.** { *; }
-keep class androidx.security.crypto.EncryptedSharedPreferences { *; }
-keep class androidx.security.crypto.MasterKey { *; }

# Keep Android system classes needed for device ID
-keep class android.provider.Settings { *; }
-keep class android.provider.Settings$Secure { *; }
-keep class android.content.ContentResolver { *; }

# Keep JSON parsing classes
-keep class org.json.** { *; }
-keepclassmembers class * {
    public <init>(org.json.JSONObject);
}

# Keep all Kotlin data classes used in network responses
-keep @kotlin.Metadata class com.vvpn.android.payment.** { *; }
-keepclassmembers class com.vvpn.android.payment.** {
    public <init>(...);
    public *** component*();
    public *** copy(...);
}

# Keep Thread classes for network operations
-keep class java.lang.Thread { *; }
-keep class java.lang.Runnable { *; }
-keep class java.net.** { *; }
-keep class javax.net.ssl.** { *; }
-keep class java.net.HttpURLConnection { *; }
-keep class javax.net.ssl.HttpsURLConnection { *; }

# Keep URL and URLConnection
-keep class java.net.URL { *; }
-keep class java.net.URLConnection { *; }

# Keep BufferedReader and InputStreamReader for network responses
-keep class java.io.BufferedReader { *; }
-keep class java.io.InputStreamReader { *; }
-keep class java.io.InputStream { *; }
-keep class java.io.OutputStream { *; }
-keep class java.io.OutputStreamWriter { *; }

# Keep all logging for debugging (temporary - remove for production)
# -assumenosideeffects class android.util.Log {
#     public static *** d(...);
#     public static *** v(...);
# }
