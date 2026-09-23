package com.breakyuna.esjzone.network.external

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.breakyuna.esjzone.novellibrary.novel.ChapterSource
import com.breakyuna.esjzone.novellibrary.novel.resolveChapterSource
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import org.json.JSONObject

internal class WenkuBrowserSessionClosedException : IOException("Browser session closed")

/** A single Chromium transport for supported wenku8 chapters. All WebView access stays on main. */
internal class WenkuWebViewSession(context: Context, private val userAgent: String) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val lock = ReentrantLock(true)
    private var view: WebView? = null
    private val generation = AtomicLong()
    @Volatile private var ready = false
    @Volatile private var lastUse = 0L
    private var cleanup: Runnable? = null

    fun isReady(): Boolean = ready && System.currentTimeMillis() - lastUse < IDLE_MS

    fun cookies(url: String): String? {
        if (resolveChapterSource(url) != ChapterSource.WENKU8) return null
        return onMain { android.webkit.CookieManager.getInstance().getCookie(url) }
    }

    fun close() {
        generation.incrementAndGet()
        ready = false
        main.post {
            cleanup?.let(main::removeCallbacks)
            cleanup = null
            view?.stopLoading()
            view?.destroy()
            view = null
        }
    }

    fun fetch(url: String): String {
        if (resolveChapterSource(url) != ChapterSource.WENKU8) throw UnsupportedExternalChapterException()
        try {
            lock.lockInterruptibly()
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Browser request cancelled", error)
        }
        try {
            if (Looper.myLooper() == Looper.getMainLooper()) throw IOException("Browser request on main thread")
            val token = generation.incrementAndGet()
            val browser = onMain {
                cleanup?.let(main::removeCallbacks)
                cleanup = null
                view ?: WebView(appContext).also { created ->
                    created.settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        userAgentString = userAgent
                        allowFileAccess = false
                        allowContentAccess = false
                        allowFileAccessFromFileURLs = false
                        allowUniversalAccessFromFileURLs = false
                    }
                    android.webkit.CookieManager.getInstance().setAcceptCookie(true)
                    android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(created, false)
                    view = created
                }
            }
            val deadline = System.currentTimeMillis() + REQUEST_MS
            var html: String? = null
            if (ready) {
                try {
                    html = fetchInPage(browser, url, token, deadline)
                } catch (error: WenkuBrowserSessionClosedException) {
                    throw error
                } catch (_: IOException) {
                    // A browser fetch can be challenged even with a healthy page session.
                }
                if (html != null && CloudflareChallenge.hasChallengeDocumentMarkers(html)) html = null
            }
            if (html == null) html = navigate(browser, url, token, deadline)
            ready = true
            lastUse = System.currentTimeMillis()
            return html
        } catch (error: IOException) {
            ready = false
            throw error
        } catch (error: InterruptedException) {
            ready = false
            Thread.currentThread().interrupt()
            throw IOException("Browser request cancelled", error)
        } finally {
            main.post { view?.stopLoading() }
            val token = generation.get()
            main.post {
                cleanup?.let(main::removeCallbacks)
                cleanup = Runnable {
                    if (generation.get() == token && System.currentTimeMillis() - lastUse >= IDLE_MS) {
                        view?.stopLoading()
                        view?.destroy()
                        view = null
                        ready = false
                    }
                }.also { main.postDelayed(it, IDLE_MS) }
            }
            lock.unlock()
        }
    }

    private fun navigate(browser: WebView, url: String, token: Long, deadline: Long): String {
        onMain {
            browser.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    request.isForMainFrame && resolveChapterSource(request.url.toString()) != ChapterSource.WENKU8

                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    val uri = request.url
                    if (uri.scheme == "https" && uri.host in ALLOWED_RESOURCE_HOSTS) return null
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
            }
            browser.loadUrl(url, mapOf("Accept-Language" to "zh-CN,zh;q=0.9"))
        }
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(POLL_MS)
            if (token != generation.get()) throw WenkuBrowserSessionClosedException()
            val state = evaluate(browser, """(function(){let expected=new URL(${JSONObject.quote(url)});if(location.protocol!=='https:'||location.hostname!=='www.wenku8.net'||location.pathname.toLowerCase()!==expected.pathname.toLowerCase())return 0;let c=document.querySelector('#content');return c&&(c.textContent.trim().length>=20||c.querySelector('img'))?1:0})()""", deadline)
            if (state == "1") {
                val html = capture(browser, "document.documentElement.outerHTML", deadline)
                if (!CloudflareChallenge.hasChallengeDocumentMarkers(html)) return html
            }
        }
        throw CloudflareChallengeRequiredException()
    }

    private fun fetchInPage(browser: WebView, url: String, token: Long, deadline: Long): String {
        val script = """(function(){window.__esjWenkuResult=null;fetch(${JSONObject.quote(url)},{credentials:'include'}).then(async r=>{if(!r.ok)throw Error('HTTP '+r.status);let b=await r.arrayBuffer();let p=new TextDecoder('iso-8859-1').decode(b.slice(0,4096));let c=(r.headers.get('content-type')||'').match(/charset\s*=\s*['"]?([\w+.-]+)/i)?.[1]||p.match(/<meta[^>]+charset\s*=\s*['"]?([\w+.-]+)/i)?.[1]||'gbk';try{return new TextDecoder(c).decode(b)}catch(_){return new TextDecoder('gbk').decode(b)}}).then(t=>window.__esjWenkuResult=t).catch(()=>window.__esjWenkuResult=false);return true})()"""
        evaluate(browser, script, deadline)
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(POLL_MS)
            if (token != generation.get()) throw WenkuBrowserSessionClosedException()
            val state = evaluate(browser, "window.__esjWenkuResult===false?-1:typeof window.__esjWenkuResult==='string'?1:0", deadline)
            if (state == "-1") throw IOException("Browser fetch failed")
            if (state == "1") return capture(browser, "window.__esjWenkuResult", deadline)
        }
        throw IOException("Browser fetch timed out")
    }

    private fun capture(browser: WebView, expression: String, deadline: Long): String {
        try {
            val length = evaluate(browser, "(window.__esjWenkuCapture=$expression).length", deadline).toIntOrNull()
                ?: throw ExternalChapterParseException()
            if (length > MAX_BROWSER_CHAPTER_HTML_BYTES) throw ExternalChapterParseException()
            val builder = StringBuilder()
            var offset = 0
            while (offset < length) {
                // JS slices by UTF-16 units; the final UTF-8 byte count is checked below.
                val chunk = evaluate(browser, "window.__esjWenkuCapture.slice($offset,${offset + CHUNK_SIZE})", deadline)
                builder.append(chunk)
                offset += CHUNK_SIZE
                if (builder.length > MAX_BROWSER_CHAPTER_HTML_BYTES) throw ExternalChapterParseException()
            }
            return builder.toString().also {
                if (it.toByteArray(Charsets.UTF_8).size > MAX_BROWSER_CHAPTER_HTML_BYTES) throw ExternalChapterParseException()
            }
        } finally {
            main.post {
                runCatching {
                    browser.evaluateJavascript("window.__esjWenkuCapture=null;window.__esjWenkuResult=null", null)
                }
            }
        }
    }

    private fun evaluate(browser: WebView, script: String, deadline: Long): String {
        val latch = CountDownLatch(1)
        var result: String? = null
        var failure: Throwable? = null
        main.post {
            try {
                browser.evaluateJavascript(script) { value -> result = value; latch.countDown() }
            } catch (error: Throwable) {
                failure = error
                latch.countDown()
            }
        }
        val remaining = deadline - System.currentTimeMillis()
        if (remaining <= 0 || !latch.await(remaining, TimeUnit.MILLISECONDS)) throw IOException("Browser request timed out")
        failure?.let { throw IOException("Browser script failed", it) }
        val raw = result ?: throw IOException("Browser returned no result")
        return if (raw.startsWith('"')) try {
            org.json.JSONArray("[$raw]").getString(0)
        } catch (error: org.json.JSONException) {
            throw IOException("Browser result could not be decoded", error)
        } else raw
    }

    private fun <T> onMain(block: () -> T): T {
        val latch = CountDownLatch(1)
        var result: T? = null
        var failure: Throwable? = null
        main.post { try { result = block() } catch (error: Throwable) { failure = error } finally { latch.countDown() } }
        if (!latch.await(5, TimeUnit.SECONDS)) throw CloudflareWebViewUnavailableException()
        failure?.let { throw CloudflareWebViewUnavailableException(it) }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private companion object {
        const val REQUEST_MS = 35_000L
        const val IDLE_MS = 60_000L
        const val POLL_MS = 500L
        const val CHUNK_SIZE = 16_384
        val ALLOWED_RESOURCE_HOSTS = setOf("www.wenku8.net", "challenges.cloudflare.com", "www.cloudflare.com")
    }
}
