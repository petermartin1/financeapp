package com.financeapp.data.ofx

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import okhttp3.CertificatePinner
import java.util.concurrent.TimeUnit

actual fun createSecureHttpClient(certificatePins: Map<String, List<String>>): HttpClient {
    return HttpClient(OkHttp) {
        engine {
            config {
                if (certificatePins.isNotEmpty()) {
                    val pinnerBuilder = CertificatePinner.Builder()

                    for ((hostname, pins) in certificatePins) {
                        for (pin in pins) {
                            // Pins should be in format "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
                            pinnerBuilder.add(hostname, pin)
                        }
                    }

                    certificatePinner(pinnerBuilder.build())
                }

                // Additional security settings
                followRedirects(false) // Don't auto-follow redirects to different hosts
                followSslRedirects(false)

                // Network timeouts for security
                connectTimeout(10, TimeUnit.SECONDS)      // 10s to establish connection
                readTimeout(30, TimeUnit.SECONDS)         // 30s to read response
                writeTimeout(30, TimeUnit.SECONDS)        // 30s to write request
                callTimeout(60, TimeUnit.SECONDS)         // 60s total request time
            }
        }

        // Ktor-level request timeout
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000  // 60 seconds
            connectTimeoutMillis = 10_000  // 10 seconds
            socketTimeoutMillis = 30_000   // 30 seconds
        }
    }
}
