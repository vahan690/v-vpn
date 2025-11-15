package com.vvpn.android.security

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.security.MessageDigest

/**
 * Detects app tampering and repackaging.
 *
 * This verifies the app's signing certificate matches the official release certificate.
 * Repackaged/modified APKs will have different signatures.
 */
object TamperDetector {

    /**
     * YOUR OFFICIAL RELEASE CERTIFICATE SHA-256 HASH
     *
     * IMPORTANT: Replace this with your actual release certificate hash.
     *
     * To get your release certificate SHA-256:
     * keytool -list -v -keystore /path/to/your/keystore.jks -alias your_alias | grep SHA256
     *
     * Or from APK:
     * unzip -p your-app.apk META-INF/CERT.RSA | keytool -printcert | grep SHA256
     */
    private const val OFFICIAL_SIGNATURE_SHA256 = "16970DDBE58E490FD694BA9B61E29BE60B7CAE9E31E4D25E54E5D3EA38D79D9E"

    /**
     * Check if the app has been tampered with (repackaged/modified).
     *
     * @return true if app is tampered, false if legitimate
     */
    fun isAppTampered(context: Context): Boolean {
        // Skip check if signature not configured (for development)
        if (OFFICIAL_SIGNATURE_SHA256 == "YOUR_SIGNATURE_SHA256_HERE") {
            android.util.Log.w("TamperDetector", "Official signature not configured - skipping tamper check")
            return false
        }

        val currentSignature = getAppSignatureSHA256(context)
        return currentSignature != OFFICIAL_SIGNATURE_SHA256
    }

    /**
     * Get the SHA-256 hash of the app's signing certificate.
     */
    fun getAppSignatureSHA256(context: Context): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )
            }

            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }

            if (signatures.isNullOrEmpty()) {
                android.util.Log.e("TamperDetector", "No signatures found")
                return ""
            }

            val signature = signatures[0]
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(signature.toByteArray())

            // Convert to hex string
            digest.joinToString("") { "%02X".format(it) }
        } catch (e: Exception) {
            android.util.Log.e("TamperDetector", "Error getting signature", e)
            ""
        }
    }

    /**
     * Check if app is running in an emulator.
     * Emulators are often used for analyzing/cracking apps.
     */
    fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")
                || "google_sdk" == Build.PRODUCT
                || Build.PRODUCT.contains("sdk")
                || Build.PRODUCT.contains("vbox")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu"))
    }

    /**
     * Check if debugger is attached.
     * Debuggers are used to analyze app behavior and bypass security checks.
     */
    fun isDebuggerAttached(): Boolean {
        return android.os.Debug.isDebuggerConnected() || android.os.Debug.waitingForDebugger()
    }

    /**
     * Check if app is debuggable (has android:debuggable="true" in manifest).
     * Release builds should NEVER be debuggable.
     */
    fun isAppDebuggable(context: Context): Boolean {
        return try {
            val flags = context.applicationInfo.flags
            (flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check for Xposed Framework.
     * Xposed is used to hook and modify app behavior at runtime.
     */
    fun isXposedInstalled(): Boolean {
        return try {
            // Check for Xposed classes in the stack trace
            val stackTrace = Throwable().stackTrace
            stackTrace.any { element ->
                element.className.contains("de.robv.android.xposed")
            }
        } catch (e: Exception) {
            false
        } || checkXposedFiles()
    }

    /**
     * Check for Xposed-related files.
     */
    private fun checkXposedFiles(): Boolean {
        val xposedPaths = arrayOf(
            "/system/lib/libxposed_art.so",
            "/system/lib64/libxposed_art.so",
            "/system/framework/XposedBridge.jar"
        )

        return xposedPaths.any { java.io.File(it).exists() }
    }

    /**
     * Check for Frida framework.
     * Frida is a dynamic instrumentation toolkit used for reverse engineering.
     */
    fun isFridaRunning(): Boolean {
        return try {
            // Check for Frida's default port
            val fridaPorts = arrayOf(27042, 27043)
            fridaPorts.any { port ->
                try {
                    val socket = java.net.Socket()
                    socket.connect(java.net.InetSocketAddress("127.0.0.1", port), 500)
                    socket.close()
                    true
                } catch (e: Exception) {
                    false
                }
            }
        } catch (e: Exception) {
            false
        } || checkFridaFiles()
    }

    /**
     * Check for Frida-related files.
     */
    private fun checkFridaFiles(): Boolean {
        val fridaFiles = arrayOf(
            "/data/local/tmp/frida-server",
            "/data/local/tmp/re.frida.server"
        )

        return fridaFiles.any { java.io.File(it).exists() }
    }

    /**
     * Get comprehensive security check results.
     */
    fun performSecurityChecks(context: Context): SecurityCheckResult {
        val issues = mutableListOf<String>()

        if (isAppTampered(context)) issues.add("App signature tampered")
        if (isEmulator()) issues.add("Running on emulator")
        if (isDebuggerAttached()) issues.add("Debugger attached")
        if (isAppDebuggable(context)) issues.add("App is debuggable")
        if (isXposedInstalled()) issues.add("Xposed Framework detected")
        if (isFridaRunning()) issues.add("Frida Framework detected")

        return SecurityCheckResult(
            isSecure = issues.isEmpty(),
            issues = issues,
            signatureSHA256 = getAppSignatureSHA256(context)
        )
    }

    data class SecurityCheckResult(
        val isSecure: Boolean,
        val issues: List<String>,
        val signatureSHA256: String
    )
}
