package com.breakyuna.esjzone.network.external

import java.io.IOException

class CloudflareChallengeRequiredException : IOException("Site security verification is required")
class CloudflareClearanceRejectedException : IOException("Site security verification was rejected")
class CloudflareWebViewUnavailableException : IOException("Android System WebView is unavailable")
class WenkuCookieStoreUnavailableException : IOException("Wenku cookie storage is unavailable")

internal object CloudflareChallenge {
    /** For persisted HTML, whose response headers are no longer available. */
    fun hasChallengeDocumentMarkers(body: String): Boolean {
        val lower = body.lowercase()
        return lower.contains("/cdn-cgi/challenge-platform/") ||
            lower.contains("<title>just a moment") ||
            lower.contains("enable javascript and cookies") ||
            lower.contains("turnstile")
    }

    fun isChallenge(status: Int, mitigated: String?, server: String?, body: String): Boolean {
        val cfServer = server?.contains("cloudflare", ignoreCase = true) == true
        if (mitigated?.equals("challenge", ignoreCase = true) == true) return true
        if (status !in listOf(403, 503) && status !in 200..299) return false
        return cfServer && hasChallengeDocumentMarkers(body)
    }
}
