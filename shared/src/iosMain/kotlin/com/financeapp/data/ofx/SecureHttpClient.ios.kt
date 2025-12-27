package com.financeapp.data.ofx

import io.ktor.client.*
import io.ktor.client.engine.darwin.*

actual fun createSecureHttpClient(certificatePins: Map<String, List<String>>): HttpClient {
    return HttpClient(Darwin) {
        engine {
            configureRequest {
                setAllowsCellularAccess(true)
            }

            // Note: Full certificate pinning on iOS requires implementing
            // NSURLSessionDelegate.urlSession(_:didReceive:completionHandler:)
            // This is a simplified implementation that relies on system trust
            // For production, consider using TrustKit or similar library

            if (certificatePins.isNotEmpty()) {
                handleChallenge { session, task, challenge, completionHandler ->
                    // For now, use default handling
                    // TODO: Implement proper certificate pinning validation
                    completionHandler(
                        platform.Foundation.NSURLSessionAuthChallengeDisposition.NSURLSessionAuthChallengePerformDefaultHandling,
                        null
                    )
                }
            }
        }
    }
}
