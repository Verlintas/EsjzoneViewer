package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.PageKind
import com.breakyuna.esjzone.network.PageResponsePolicy
import com.breakyuna.esjzone.network.external.CloudflareChallenge
import com.breakyuna.esjzone.network.external.ExternalChapterHtml
import com.breakyuna.esjzone.network.external.ExternalChapterParseException
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.ChapterSource
import com.breakyuna.esjzone.novellibrary.novel.resolveChapterSource
import com.breakyuna.esjzone.novellibrary.novel.NovelChapterList
import com.breakyuna.esjzone.novellibrary.component.ChapterItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class WenkuChapterTest {
    private val url = "https://www.wenku8.net/novel/2/2552/96772.htm"

    @Test fun sourceAndIdentity() {
        assertEquals(ChapterSource.ESJ_ZONE, resolveChapterSource("/forum/1552200797/123.html"))
        assertEquals(ChapterSource.WENKU8, resolveChapterSource(url))
        assertEquals(ChapterSource.WENKU8,
            resolveChapterSource("http://www.wenku8.net/novel/2/2552/96772.htm"))
        assertEquals(ChapterSource.WENKU8, resolveChapterSource("96773.htm", url))
        assertEquals(ChapterSource.UNSUPPORTED_EXTERNAL,
            resolveChapterSource("https://wenku8.net.example.com/novel/2/2552/96772.htm"))
        assertEquals(ChapterSource.UNSUPPORTED_EXTERNAL, resolveChapterSource("javascript:alert(1)"))
        assertEquals(ChapterSource.UNSUPPORTED_EXTERNAL, resolveChapterSource("https://example.org/chapter"))
        assertTrue(EsjzoneUrls.canonicalPageKey(url).startsWith("https://www.wenku8.net/"))
        assertFalse(EsjzoneUrls.canonicalPageKey(url).contains("__cf_chl_"))
    }

    @Test fun continueReadingKeepsFirstSupportedChapterAndHistory() {
        val first = Chapter("first", url, false)
        val later = Chapter("later", "/forum/1552200797/123.html", false)
        val unsupported = Chapter("other", "https://example.org/chapter", false)
        assertEquals(first, NovelChapterList(listOf(ChapterItem(first), ChapterItem(later))).toRead)
        assertEquals(later.copy(isHistory = true), NovelChapterList(listOf(
            ChapterItem(first), ChapterItem(later.copy(isHistory = true)))).toRead)
        assertEquals(first, NovelChapterList(listOf(ChapterItem(unsupported), ChapterItem(first))).toRead)
        assertEquals(listOf(first, later), NovelChapterList(listOf(
            ChapterItem(first), ChapterItem(later))).orderedChapters)
    }

    @Test fun gbkAndDeclaredCharsets() {
        val chinese = "<html><head><meta charset=GBK></head><body><div id=content>中文正文测试</div></body></html>"
        assertEquals(chinese, ExternalChapterHtml.decode(chinese.toByteArray(charset("GBK")), null))
        val utf8 = "<html><head><meta charset=GBK></head><body>正文</body></html>"
        assertEquals(utf8, ExternalChapterHtml.decode(utf8.toByteArray(), "text/html; charset=UTF-8"))
        assertEquals(chinese, ExternalChapterHtml.decode(chinese.toByteArray(charset("GBK")),
            "text/html; charset=unknown-charset"))
        val equiv = chinese.replace("<meta charset=GBK>",
            "<meta http-equiv='Content-Type' content='text/html; charset=GBK'>")
        assertEquals(equiv, ExternalChapterHtml.decode(equiv.toByteArray(charset("GBK")), null))
    }

    @Test fun parserCleansBodyAndResolvesLinks() {
        val body = "<html><body><div id=title>第一章</div><div id=content>" +
            "<p>这是第一段正文，长度足以成为有效章节。</p><script>tracking()</script>" +
            "<a href=96773.htm>下一页</a><img src=96772.jpg></div></body></html>"
        val detail = ExternalChapterHtml.parse(body, Chapter("fallback", url, false), url)
        assertEquals("第一章", detail.name)
        assertTrue(detail.contentHtml!!.contains("正文"))
        assertFalse(detail.contentHtml!!.contains("tracking"))
        assertFalse(detail.contentHtml!!.contains("下一页"))
        assertTrue(detail.contentHtml!!.contains("https://www.wenku8.net/novel/2/2552/96772.jpg"))
        assertEquals("https://www.wenku8.net/novel/2/2552/96773.htm", detail.next?.url)
        assertThrows(ExternalChapterParseException::class.java) {
            ExternalChapterHtml.parse("<html><body>missing</body></html>", Chapter("x", url, false), url)
        }
    }

    @Test fun imageOnlyChapterPassesCachePolicyAndParser() {
        val html = "<html><body><div id=title>彩页</div><div id=content>" +
            "<img src='96772.jpg'></div></body></html>"
        assertTrue(PageResponsePolicy.validate(200, html, url, kind = PageKind.EXTERNAL_CHAPTER).trusted)
        val detail = ExternalChapterHtml.parse(html, Chapter("彩页", url, false), url)
        assertEquals("彩页", detail.name)
        assertTrue(detail.contentHtml!!.contains("https://www.wenku8.net/novel/2/2552/96772.jpg"))
    }

    @Test fun footerNavigationWinsOverHeaderLinks() {
        val html = "<html><body><a href='96773.htm'>下一页</a>" +
            "<div id=content>正文内容足够长，用于验证目录缺失时页尾链接作为降级导航。</div>" +
            "<a href='100485.htm'>下一页</a></body></html>"
        val detail = ExternalChapterHtml.parse(html, Chapter("章节", url, false), url)
        assertEquals("https://www.wenku8.net/novel/2/2552/100485.htm", detail.next?.url)
    }

    @Test fun challengeIsNotOrdinaryHttpFailureAndCannotCache() {
        val challenge = "<html><body><title>Just a moment...</title>" +
            "<script src='/cdn-cgi/challenge-platform/start.js'></script></body></html>"
        assertTrue(CloudflareChallenge.isChallenge(403, "challenge", "cloudflare", challenge))
        assertTrue(CloudflareChallenge.isChallenge(403, null, "cloudflare", challenge))
        assertTrue(CloudflareChallenge.isChallenge(200, null, "cloudflare", challenge))
        assertFalse(CloudflareChallenge.isChallenge(403, null, null, "Forbidden"))
        assertFalse(CloudflareChallenge.isChallenge(429, null, "cloudflare", "rate limited"))
        assertFalse(CloudflareChallenge.isChallenge(500, null, "cloudflare", "server failure"))
        assertFalse(PageResponsePolicy.validate(200, challenge, url, kind = PageKind.EXTERNAL_CHAPTER).trusted)
        assertTrue(CloudflareChallenge.hasChallengeDocumentMarkers(challenge))
    }
}
