package com.vvpn.android.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File

/**
 * Detects rooted devices and security threats.
 *
 * IMPORTANT: This is NOT foolproof - determined attackers can bypass these checks.
 * However, it raises the bar significantly and deters casual crackers.
 */
object RootDetector {

    /**
     * Comprehensive root detection check.
     * Combines multiple detection methods for better accuracy.
     */
    fun isDeviceRooted(): Boolean {
        return checkRootBinaries() ||
               checkSuperuserApk() ||
               checkRootCloakingApps() ||
               checkDangerousProps() ||
               checkRWPaths() ||
               checkSuExists() ||
               checkMagiskHide()
    }

    /**
     * Check for common root binary files.
     * These are installed by most rooting methods (SuperSU, Magisk, etc.)
     */
    private fun checkRootBinaries(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su",
            "/su/bin",
            "/system/xbin/daemonsu"
        )

        return paths.any { File(it).exists() }
    }

    /**
     * Check for SuperUser/root management apps.
     */
    private fun checkSuperuserApk(): Boolean {
        val packages = arrayOf(
            "com.noshufou.android.su",
            "com.noshufou.android.su.elite",
            "eu.chainfire.supersu",
            "com.koushikdutta.superuser",
            "com.thirdparty.superuser",
            "com.yellowes.su",
            "com.topjohnwu.magisk"
        )

        return packages.any { checkPackageExists(it) }
    }

    /**
     * Check for root cloaking and hooking frameworks.
     * These are used to hide root from detection.
     */
    private fun checkRootCloakingApps(): Boolean {
        val packages = arrayOf(
            "com.devadvance.rootcloak",
            "com.devadvance.rootcloakplus",
            "de.robv.android.xposed.installer",
            "com.saurik.substrate",
            "com.zachspong.temprootremovejb",
            "com.amphoras.hidemyroot",
            "com.amphoras.hidemyrootadfree",
            "com.formyhm.hiderootPremium",
            "com.formyhm.hideroot",
            "me.phh.superuser",
            "eu.chainfire.supersu.pro",
            "com.kingouser.com"
        )

        return packages.any { checkPackageExists(it) }
    }

    /**
     * Check for test-keys in build tags.
     * Official releases are signed with release-keys.
     */
    private fun checkDangerousProps(): Boolean {
        val buildTags = Build.TAGS
        return buildTags != null && buildTags.contains("test-keys")
    }

    /**
     * Check if critical system paths are writable.
     * On rooted devices, /system is often remounted as read-write.
     */
    private fun checkRWPaths(): Boolean {
        val paths = arrayOf("/system", "/system/bin", "/system/sbin", "/system/xbin", "/vendor/bin", "/sbin", "/etc")

        return paths.any { path ->
            val file = File(path)
            file.exists() && file.canWrite()
        }
    }

    /**
     * Try to execute 'su' command.
     * If successful, device is rooted.
     */
    private fun checkSuExists(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("/system/xbin/which", "su"))
            val input = process.inputStream.bufferedReader()
            val output = input.readText()
            input.close()
            output.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check for Magisk Hide - advanced root hiding.
     * Magisk is the most popular modern root solution.
     */
    private fun checkMagiskHide(): Boolean {
        val paths = arrayOf(
            "/sbin/.magisk",
            "/sbin/.core",
            "/cache/.disable_magisk",
            "/dev/.magisk.unblock",
            "/cache/magisk.log",
            "/data/adb/magisk",
            "/data/adb/magisk.img",
            "/data/adb/magisk.db"
        )

        return paths.any { File(it).exists() }
    }

    /**
     * Check if a package is installed on the device.
     * Note: This is a simple check and may not work in all contexts.
     */
    fun isPackageInstalled(packageName: String, context: android.content.Context?): Boolean {
        if (context == null) return false
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check for packages without context (uses reflection).
     */
    private fun checkPackageExists(packageName: String): Boolean {
        return java.io.File("/data/data/$packageName").exists()
    }

    /**
     * Get detailed root detection result with reasons.
     * Useful for logging and debugging.
     */
    fun getRootDetectionDetails(): RootDetectionResult {
        val checks = mutableListOf<String>()

        if (checkRootBinaries()) checks.add("Root binaries found")
        if (checkSuperuserApk()) checks.add("Root management apps installed")
        if (checkRootCloakingApps()) checks.add("Root cloaking/hooking frameworks detected")
        if (checkDangerousProps()) checks.add("Test-keys build detected")
        if (checkRWPaths()) checks.add("System paths are writable")
        if (checkSuExists()) checks.add("'su' binary executable")
        if (checkMagiskHide()) checks.add("Magisk detected")

        return RootDetectionResult(
            isRooted = checks.isNotEmpty(),
            detectedMethods = checks
        )
    }

    data class RootDetectionResult(
        val isRooted: Boolean,
        val detectedMethods: List<String>
    )
}
