package com.vvpn.android.security

import android.content.Context
import android.os.Debug
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

/**
 * Advanced anti-debugging techniques.
 *
 * These checks detect various debugging and analysis tools used by crackers.
 * No single check is foolproof, but combining multiple checks makes bypassing harder.
 */
object AntiDebug {

    /**
     * Check if a debugger is currently attached.
     * This is the most basic check.
     */
    fun isDebuggerConnected(): Boolean {
        return Debug.isDebuggerConnected()
    }

    /**
     * Check if the app is waiting for a debugger.
     * This happens when the app is launched with "Wait for Debugger" option.
     */
    fun isWaitingForDebugger(): Boolean {
        return Debug.waitingForDebugger()
    }

    /**
     * Check for debugger by reading TracerPid from /proc/self/status.
     *
     * When a debugger is attached, TracerPid will be non-zero.
     * This is harder to bypass than Debug.isDebuggerConnected().
     */
    fun checkTracerPid(): Boolean {
        return try {
            val statusFile = File("/proc/self/status")
            BufferedReader(FileReader(statusFile)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line!!.startsWith("TracerPid:")) {
                        val pid = line!!.substring(10).trim().toIntOrNull() ?: 0
                        return pid != 0
                    }
                }
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Timing-based debugger detection.
     *
     * When a debugger is attached, code execution slows down significantly.
     * We measure how long a simple operation takes - if it's too slow, debugger is likely present.
     */
    fun detectTimingAnomaly(): Boolean {
        val start = System.nanoTime()

        // Simple operation that should be very fast
        var sum = 0
        for (i in 0..999) {
            sum += i
        }

        val duration = System.nanoTime() - start

        // If this takes more than 1ms, something is slowing us down (likely debugger)
        // Normal execution: < 100 microseconds
        // Debugger attached: > 1ms (10x+ slower)
        return duration > 1_000_000 // 1 millisecond in nanoseconds
    }

    /**
     * Check for common debugger ports on localhost.
     *
     * Popular debuggers (JDWP, Frida, etc.) listen on specific ports.
     * If these ports are open, a debugger is likely running.
     */
    fun checkDebuggerPorts(): Boolean {
        val suspiciousPorts = arrayOf(
            5005,   // JDWP default
            8000,   // Common JDWP alternative
            8700,   // Android Studio debugger
            27042,  // Frida default
            27043   // Frida alternative
        )

        return suspiciousPorts.any { port ->
            try {
                val socket = java.net.Socket()
                socket.connect(java.net.InetSocketAddress("127.0.0.1", port), 200)
                socket.close()
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Check for Android Debug Bridge (ADB) connection.
     *
     * ADB is used for debugging and often indicates development/analysis environment.
     */
    fun isAdbEnabled(context: Context): Boolean {
        return try {
            android.provider.Settings.Global.getInt(
                context.contentResolver,
                android.provider.Settings.Global.ADB_ENABLED,
                0
            ) == 1
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check for USB debugging enabled.
     *
     * Similar to ADB check, indicates development mode.
     */
    fun isUsbDebuggingEnabled(context: Context): Boolean {
        return try {
            android.provider.Settings.Global.getInt(
                context.contentResolver,
                android.provider.Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                0
            ) == 1
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check for thread count anomaly.
     *
     * Debuggers often create additional threads.
     * If thread count is abnormally high, debugger might be present.
     */
    fun detectThreadCountAnomaly(): Boolean {
        val threadCount = Thread.activeCount()

        // Normal Android app: 15-30 threads
        // With debugger: 40+ threads
        return threadCount > 40
    }

    /**
     * Check for specific debugger-related system properties.
     */
    fun checkDebugProperties(): Boolean {
        val suspiciousProps = arrayOf(
            "ro.debuggable",
            "ro.secure",
            "service.adb.root"
        )

        return try {
            suspiciousProps.any { prop ->
                val value = System.getProperty(prop)
                value != null && (value == "1" || value.equals("true", ignoreCase = true))
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Comprehensive anti-debugging check.
     * Returns list of detected debugging indicators.
     */
    fun performAntiDebugChecks(context: Context): AntiDebugResult {
        val indicators = mutableListOf<String>()

        if (isDebuggerConnected()) indicators.add("Debugger connected (basic check)")
        if (isWaitingForDebugger()) indicators.add("Waiting for debugger")
        if (checkTracerPid()) indicators.add("TracerPid detected")
        if (detectTimingAnomaly()) indicators.add("Timing anomaly detected")
        if (checkDebuggerPorts()) indicators.add("Debugger ports open")
        if (isAdbEnabled(context)) indicators.add("ADB enabled")
        if (isUsbDebuggingEnabled(context)) indicators.add("USB debugging enabled")
        if (detectThreadCountAnomaly()) indicators.add("Abnormal thread count")
        if (checkDebugProperties()) indicators.add("Debug properties detected")

        return AntiDebugResult(
            isDebugging = indicators.isNotEmpty(),
            indicators = indicators
        )
    }

    /**
     * Perform anti-debugging check and exit app if debugging detected.
     *
     * WARNING: This is aggressive and will close the app if ANY debugging is detected.
     * Use carefully - may affect legitimate users with developer options enabled.
     */
    fun enforceAntiDebug(context: Context) {
        val result = performAntiDebugChecks(context)

        if (result.isDebugging) {
            android.util.Log.w("AntiDebug", "Debugging detected: ${result.indicators.joinToString(", ")}")

            // Exit app immediately
            // Note: This can be bypassed, but it raises the bar
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(1)
        }
    }

    data class AntiDebugResult(
        val isDebugging: Boolean,
        val indicators: List<String>
    )
}
