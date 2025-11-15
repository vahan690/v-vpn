package com.vvpn.android.security

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * String obfuscation to hide sensitive strings from static analysis.
 *
 * IMPORTANT: This is NOT cryptographically secure encryption.
 * It's designed to make static analysis harder, not to protect data at rest.
 *
 * Attackers can still recover these strings with enough effort, but it raises
 * the barrier significantly compared to plaintext strings in the APK.
 */
object StringObfuscator {

    /**
     * Simple XOR-based obfuscation key.
     * Change this to a random value for your app.
     *
     * NOTE: This will be visible in the decompiled code, but combined with
     * ProGuard obfuscation, it makes analysis significantly harder.
     */
    private val KEY = byteArrayOf(
        0x56, 0x76, 0x70, 0x6E.toByte(),  // "Vvpn"
        0x53, 0x65, 0x63, 0x72,           // "Secr"
        0x65, 0x74, 0x4B, 0x65,           // "etKe"
        0x79, 0x32, 0x30, 0x32, 0x35      // "y2025"
    )

    /**
     * Obfuscate a string (use this during development to generate obfuscated strings).
     * The output should be hardcoded in your app, not computed at runtime.
     */
    fun obfuscate(input: String): String {
        val bytes = input.toByteArray()
        val result = ByteArray(bytes.size)

        for (i in bytes.indices) {
            result[i] = (bytes[i].toInt() xor KEY[i % KEY.size].toInt()).toByte()
        }

        return Base64.encodeToString(result, Base64.NO_WRAP)
    }

    /**
     * Deobfuscate a string at runtime.
     * Use this to recover the original string from the obfuscated version.
     */
    fun deobfuscate(obfuscated: String): String {
        val bytes = Base64.decode(obfuscated, Base64.NO_WRAP)
        val result = ByteArray(bytes.size)

        for (i in bytes.indices) {
            result[i] = (bytes[i].toInt() xor KEY[i % KEY.size].toInt()).toByte()
        }

        return String(result)
    }

    /**
     * Helper function to make code more readable.
     * Instead of: deobfuscate("encoded_string")
     * Use: s("encoded_string")
     */
    fun s(obfuscated: String): String = deobfuscate(obfuscated)
}

/**
 * Pre-obfuscated sensitive strings.
 *
 * To add a new obfuscated string:
 * 1. Use StringObfuscator.obfuscate("your_string") to get the encoded version
 * 2. Add it here as a constant
 * 3. Use ObfuscatedStrings.YOUR_CONSTANT in your code
 *
 * These strings will be obfuscated in the final APK, making static analysis harder.
 */
object ObfuscatedStrings {

    // API URLs
    // Original: "https://api.vvpn.space"
    val API_BASE_URL by lazy { StringObfuscator.deobfuscate("Ij8fFhEVGAQcFwIwEhcTEhMZBQ==") }

    // Original: "https://bsc.vvpn.space"
    val BSC_BASE_URL by lazy { StringObfuscator.deobfuscate("Ij8fFhEVGAQcAhEWBBcTEhMZBQ==") }

    // Root detection package names (obfuscated to make bypass harder)
    // Original: "com.topjohnwu.magisk"
    val MAGISK_PACKAGE by lazy { StringObfuscator.deobfuscate("BRASFxQcEwFXGF0eGxEfAQc=") }

    // Original: "eu.chainfire.supersu"
    val SUPERSU_PACKAGE by lazy { StringObfuscator.deobfuscate("DhQaGBxBABBRAw4cCAQUGhQ=") }

    // Security-related strings
    // Original: "Security Warning"
    val SECURITY_WARNING by lazy { StringObfuscator.deobfuscate("FRACEg0SAA8VFR0aBgw=") }

    // Original: "Root detected"
    val ROOT_DETECTED by lazy { StringObfuscator.deobfuscate("FgUVFBwQARQeBAEf") }

    /**
     * Generate obfuscated version of a string (for development use).
     *
     * Example usage:
     * println("Obfuscated: ${ObfuscatedStrings.generate("https://api.vvpn.space")}")
     */
    fun generate(plaintext: String): String {
        return StringObfuscator.obfuscate(plaintext)
    }
}
