package com.vvpn.android.payment

import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Provides a secure OkHttpClient with certificate pinning to prevent MITM attacks.
 *
 * Certificate pins are SHA-256 hashes of the public key of the SSL certificate.
 * If the certificate changes, these pins MUST be updated or the app will fail to connect.
 */
object SecureHttpClient {

    /**
     * Certificate pins for V-VPN API servers.
     *
     * IMPORTANT: When SSL certificates are renewed, update these pins:
     * 1. Get new pin: echo | openssl s_client -servername api.vvpn.space -connect api.vvpn.space:443 2>/dev/null | openssl x509 -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | base64
     * 2. Add new pin as backup pin BEFORE certificate renewal
     * 3. After certificate is renewed and stable, remove old pin
     *
     * Pin format: sha256/<base64-encoded-hash>
     */
    private val certificatePinner = CertificatePinner.Builder()
        // Primary certificate pin for api.vvpn.space
        .add("api.vvpn.space", "sha256/WsauAvtpqgBjig/NhGyq5M1Qy0rruP1ebXu8ZZsxunM=")
        // Primary certificate pin for bsc.vvpn.space (same as api)
        .add("bsc.vvpn.space", "sha256/WsauAvtpqgBjig/NhGyq5M1Qy0rruP1ebXu8ZZsxunM=")
        // Backup pin for Google Trust Services WE1 (intermediate CA)
        // This provides resilience during certificate rotation
        .add("api.vvpn.space", "sha256/f8NnEFzxsikbfSZmUzDMhQnlMqVeQkSQ5SXSjytHE2Y=")
        .add("bsc.vvpn.space", "sha256/f8NnEFzxsikbfSZmUzDMhQnlMqVeQkSQ5SXSjytHE2Y=")
        .build()

    /**
     * Singleton OkHttpClient instance with security hardening:
     * - Certificate pinning to prevent MITM attacks
     * - 30-second connection timeout
     * - 30-second read timeout
     * - 30-second write timeout
     */
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .certificatePinner(certificatePinner)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            // Uncomment below for debugging certificate issues (DEVELOPMENT ONLY)
            // .addInterceptor { chain ->
            //     val request = chain.request()
            //     android.util.Log.d("SecureHttpClient", "Request: ${request.url}")
            //     chain.proceed(request)
            // }
            .build()
    }

    /**
     * Creates a client with certificate pinning disabled.
     *
     * WARNING: Only use this for testing/debugging. Never use in production.
     * Certificate pinning is a critical security feature.
     */
    @Deprecated("Only for testing. Use client property instead.")
    val insecureClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
