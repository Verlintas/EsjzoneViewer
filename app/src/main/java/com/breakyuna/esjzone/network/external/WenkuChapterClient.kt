package com.breakyuna.esjzone.network.external

import android.content.Context
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.PageCache
import com.breakyuna.esjzone.network.PageCacheTtl
import com.breakyuna.esjzone.network.PageKind
import com.breakyuna.esjzone.network.PageResponsePolicy
import com.breakyuna.esjzone.network.NetworkHttpException
import com.breakyuna.esjzone.network.pageRequestCancellation
import com.breakyuna.esjzone.network.readBytesBounded
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.ChapterSource
import com.breakyuna.esjzone.novellibrary.novel.DetailedChapter
import com.breakyuna.esjzone.novellibrary.novel.resolveChapterSource
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request

internal class WenkuChapterClient(context: Context, userAgent: String) {
    private val jar = WenkuCookieJar(context)
    private val shield = WenkuShield(context, jar, userAgent)
    private val client = OkHttpClient.Builder()
        .cookieJar(jar)
        .dns(Dns { host ->
            if (host != "www.wenku8.net") throw java.net.UnknownHostException("External host is not allowed")
            val addresses = Dns.SYSTEM.lookup(host)
            addresses.filter(::isPublicAddress).takeIf { it.isNotEmpty() }
                ?: throw java.net.UnknownHostException("External host has no public address")
        })
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()
    private val userAgentHeader = userAgent

    fun importBrowserCookies(raw: String?) = jar.importBrowserCookies(raw)

    fun userAgent(): String = userAgentHeader

    fun imageClient(): OkHttpClient = client.newBuilder()
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(2, TimeUnit.MINUTES)
        .build()

    fun load(chapter: Chapter, url: String, forceRefresh: Boolean, allowAutoSolve: Boolean,
             onSecurityCheck: (() -> Unit)? = null): DetailedChapter {
        if (resolveChapterSource(url) != ChapterSource.WENKU8) throw UnsupportedExternalChapterException()
        val key = "wenku8|${EsjzoneUrls.canonicalPageKey(url)}"
        if (!forceRefresh) PageCache.read(key, PageCacheTtl.CHAPTER)?.let { cached ->
            runCatching { validateAndParse(cached, chapter, url) }.getOrNull()?.let { return it }
            PageCache.remove(key)
        }
        var result = request(url)
        if (result.challenge) {
            if (!allowAutoSolve) throw CloudflareChallengeRequiredException()
            onSecurityCheck?.invoke()
            if (!shield.solve(url)) throw CloudflareChallengeRequiredException()
            result = request(url)
            if (result.challenge) throw CloudflareClearanceRejectedException()
        }
        if (result.status !in 200..299) throw NetworkHttpException("https://www.wenku8.net/", result.status)
        val detail = validateAndParse(result.html, chapter, url)
        val cachedDocument = org.jsoup.Jsoup.parse("<html><body><div id=title></div><div id=content></div></body></html>", url)
        cachedDocument.selectFirst("#title")?.text(detail.name)
        cachedDocument.selectFirst("#content")?.html(detail.contentHtml.orEmpty())
        PageCache.write(key, cachedDocument.outerHtml())
        return detail
    }

    private fun validateAndParse(html: String, chapter: Chapter, url: String): DetailedChapter {
        if (!PageResponsePolicy.validate(200, html, url, kind = PageKind.EXTERNAL_CHAPTER).trusted) {
            throw ExternalChapterParseException()
        }
        return ExternalChapterHtml.parse(html, chapter, url)
    }

    private fun request(url: String): ResponseData {
        val request = Request.Builder().url(url).header("User-Agent", userAgentHeader)
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .header("Accept", "text/html,application/xhtml+xml")
            .get().build()
        val call = client.newCall(request)
        val cancellation = pageRequestCancellation.get()
        cancellation?.attach(call)
        try {
            return call.execute().use { response ->
                if (resolveChapterSource(response.request.url.toString()) != ChapterSource.WENKU8) {
                    throw UnsupportedExternalChapterException()
                }
                val contentType = response.header("Content-Type")
                if (response.isSuccessful && contentType != null &&
                    !contentType.contains("text/html", ignoreCase = true) &&
                    !contentType.contains("application/xhtml+xml", ignoreCase = true)) {
                    throw ExternalChapterParseException()
                }
                val bytes = response.body?.readBytesBounded() ?: ByteArray(0)
                val html = ExternalChapterHtml.decode(bytes, contentType)
                ResponseData(
                    response.code, html,
                    CloudflareChallenge.isChallenge(response.code, response.header("cf-mitigated"),
                        response.header("Server"), html)
                )
            }
        } finally {
            cancellation?.detachCall()
        }
    }

    private data class ResponseData(val status: Int, val html: String, val challenge: Boolean)

    private fun isPublicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress) return false
        val bytes = address.address
        if (bytes.size == 16 && (bytes[0].toInt() and 0xfe) == 0xfc) return false
        return true
    }
}
