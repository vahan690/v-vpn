package com.vvpn.android.security

import android.content.Context
import android.util.Log

/**
 * Central security manager for V-VPN.
 *
 * Coordinates all security checks: root detection, tamper detection, and runtime security.
 */
object SecurityManager {

    private const val TAG = "SecurityManager"

    /**
     * Security check policy.
     * Determines what to do when security threats are detected.
     */
    enum class Policy {
        /**
         * Log security issues but allow app to continue.
         * Use for development/testing.
         */
        PERMISSIVE,

        /**
         * Warn user about security issues but allow app to continue.
         * Use for soft enforcement.
         */
        WARNING,

        /**
         * Block app from running if security threats detected.
         * Use for production with strict security requirements.
         */
        STRICT
    }

    /**
     * Current security policy.
     * Change to STRICT for production release.
     */
    private var currentPolicy = Policy.WARNING

    /**
     * Set security policy.
     */
    fun setPolicy(policy: Policy) {
        currentPolicy = policy
        Log.i(TAG, "Security policy set to: $policy")
    }

    /**
     * Perform comprehensive security check.
     *
     * @return true if app should be allowed to run, false if blocked
     */
    fun performSecurityCheck(context: Context): SecurityStatus {
        Log.d(TAG, "=== Performing Security Check ===")

        val rootDetection = RootDetector.getRootDetectionDetails()
        val tamperCheck = TamperDetector.performSecurityChecks(context)
        val antiDebug = AntiDebug.performAntiDebugChecks(context)

        val threats = mutableListOf<SecurityThreat>()

        // Check root
        if (rootDetection.isRooted) {
            threats.add(
                SecurityThreat(
                    type = ThreatType.ROOT_DETECTED,
                    severity = ThreatSeverity.HIGH,
                    description = "Device is rooted",
                    details = rootDetection.detectedMethods.joinToString(", ")
                )
            )
        }

        // Check tamper
        if (!tamperCheck.isSecure) {
            tamperCheck.issues.forEach { issue: String ->
                val severity = when {
                    issue.contains("tampered") -> ThreatSeverity.CRITICAL
                    issue.contains("Frida") || issue.contains("Xposed") -> ThreatSeverity.HIGH
                    issue.contains("debugger") -> ThreatSeverity.MEDIUM
                    else -> ThreatSeverity.LOW
                }

                threats.add(
                    SecurityThreat(
                        type = ThreatType.TAMPER_DETECTED,
                        severity = severity,
                        description = issue,
                        details = ""
                    )
                )
            }
        }

        // Check debugging
        if (antiDebug.isDebugging) {
            threats.add(
                SecurityThreat(
                    type = ThreatType.DEBUGGER_ATTACHED,
                    severity = ThreatSeverity.HIGH,
                    description = "Debugging detected",
                    details = antiDebug.indicators.joinToString(", ")
                )
            )
        }

        // Log all threats
        if (threats.isNotEmpty()) {
            Log.w(TAG, "Security threats detected:")
            threats.forEach { threat ->
                Log.w(TAG, "  - [${threat.severity}] ${threat.type}: ${threat.description}")
                if (threat.details.isNotEmpty()) {
                    Log.w(TAG, "    Details: ${threat.details}")
                }
            }
        } else {
            Log.i(TAG, "No security threats detected")
        }

        // Determine if app should be allowed to run
        val shouldBlock = when (currentPolicy) {
            Policy.PERMISSIVE -> false
            Policy.WARNING -> false
            Policy.STRICT -> threats.any { it.severity >= ThreatSeverity.HIGH }
        }

        Log.i(TAG, "Policy: $currentPolicy, Should block: $shouldBlock")

        return SecurityStatus(
            isSecure = threats.isEmpty(),
            shouldBlock = shouldBlock,
            threats = threats,
            policy = currentPolicy
        )
    }

    /**
     * Check if root detection should block the app.
     */
    fun shouldBlockRoot(): Boolean {
        return currentPolicy == Policy.STRICT
    }

    /**
     * Check if tamper detection should block the app.
     */
    fun shouldBlockTamper(): Boolean {
        return currentPolicy == Policy.STRICT
    }

    /**
     * Comprehensive security status result.
     */
    data class SecurityStatus(
        val isSecure: Boolean,
        val shouldBlock: Boolean,
        val threats: List<SecurityThreat>,
        val policy: Policy
    ) {
        fun hasHighSeverityThreats(): Boolean {
            return threats.any { it.severity >= ThreatSeverity.HIGH }
        }

        fun getThreatSummary(): String {
            if (threats.isEmpty()) return "No threats detected"
            return threats.joinToString("\n") { "• ${it.description}" }
        }
    }

    data class SecurityThreat(
        val type: ThreatType,
        val severity: ThreatSeverity,
        val description: String,
        val details: String
    )

    enum class ThreatType {
        ROOT_DETECTED,
        TAMPER_DETECTED,
        DEBUGGER_ATTACHED,
        EMULATOR_DETECTED,
        HOOKING_FRAMEWORK
    }

    enum class ThreatSeverity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
}
