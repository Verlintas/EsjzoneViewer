package com.breakyuna.esjzone.network.external

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock

/** One hidden browser per host. Call only from a cancellable IO request. */
internal class WenkuShield(private val context: Context, private val jar: WenkuCookieJar, private val userAgent: String) {
    private val main = Handler(Looper.getMainLooper())
    private val hostLock = ReentrantLock(true)
    @Volatile private var finishedAt = 0L
    @Volatile private var lastResult = false

    fun solve(url: String): Boolean {
        val joinedAt = System.currentTimeMillis()
        val observedClearance = jar.clearance()
        try {
            if (!hostLock.tryLock(30, TimeUnit.SECONDS)) throw CloudflareChallengeRequiredException()
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Security verification cancelled", error)
        }
        try {
            if (finishedAt >= joinedAt) return lastResult
            if (!lastResult && finishedAt > 0L &&
                System.currentTimeMillis() - finishedAt < 60_000L) return false
            // Another waiting request may already have received the same clearance.
            val before = jar.clearance()
            if (before != null && before != observedClearance) return true
            val latch = CountDownLatch(1)
            val active = AtomicBoolean(true)
            var browser: WebView? = null
            var changed = false
            var unavailable = false
            main.post {
                try {
                    val view = WebView(context.applicationContext)
                    browser = view
                    view.settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        userAgentString = userAgent
                        allowFileAccess = false
                        allowContentAccess = false
                        allowFileAccessFromFileURLs = false
                        allowUniversalAccessFromFileURLs = false
                    }
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(view, false)
                    view.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            if (!request.isForMainFrame) return false
                            return request.url.scheme != "https" || request.url.host != "www.wenku8.net"
                        }

                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                            val host = request.url.host.orEmpty()
                            if (request.url.scheme == "https" && host in setOf(
                                    "www.wenku8.net", "challenges.cloudflare.com", "www.cloudflare.com")) return null
                            return WebResourceResponse("text/plain", "utf-8", java.io.ByteArrayInputStream(ByteArray(0)))
                        }

                        override fun onPageFinished(view: WebView, finishedUrl: String) {
                            if (!active.get()) return
                            view.evaluateJavascript(
                                "Boolean(document.querySelector('.cf-turnstile, iframe[src*=turnstile], input[name=cf-turnstile-response]'))"
                            ) { result -> if (result == "true" && active.get()) latch.countDown() }
                            val cookie = CookieManager.getInstance().getCookie(url)
                            if (cookie != null && cookie.split(';').any { it.trim().startsWith("cf_clearance=") &&
                                    it.substringAfter('=').isNotBlank() && it.substringAfter('=') != before }) {
                                CookieManager.getInstance().flush()
                                jar.importBrowserCookies(cookie)
                                changed = true
                                latch.countDown()
                            }
                        }
                    }
                    view.loadUrl(url, mapOf("Accept-Language" to "zh-CN,zh;q=0.9"))
                } catch (_: Exception) {
                    unavailable = true
                    latch.countDown()
                }
            }
            try {
                latch.await(30, TimeUnit.SECONDS)
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Security verification cancelled", error)
            } finally {
                active.set(false)
                main.post { browser?.stopLoading(); browser?.destroy() }
            }
            if (unavailable) throw CloudflareWebViewUnavailableException()
            lastResult = changed
            finishedAt = System.currentTimeMillis()
            return changed
        } finally {
            hostLock.unlock()
        }
    }
}
