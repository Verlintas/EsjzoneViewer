package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.HtmlSelector
import com.breakyuna.esjzone.network.JsoupHtmlSelector
import com.breakyuna.esjzone.network.PageCacheTtl
import com.breakyuna.esjzone.network.PageKind
import com.breakyuna.esjzone.offline.NovelDownloadStore
import com.breakyuna.esjzone.novellibrary.component.analyseComponents
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.DetailedChapter
import com.breakyuna.esjzone.util.AppLogger
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

private val chapterSelector: HtmlSelector = JsoupHtmlSelector

fun EsjzoneClient.getChapterDetail(
    authorization: Authorization,
    chapter: Chapter,
    preferDownloaded: Boolean = true,
    forceRefresh: Boolean = false,
    baseUrl: String? = null
): DetailedChapter {
    val targetUrl = baseUrl?.let { EsjzoneUrls.resolve(chapter.url, it) }
        ?: EsjzoneUrls.resolve(chapter.url)

    if (preferDownloaded) {
        NovelDownloadStore.readChapter(targetUrl)?.let { downloaded ->
            AppLogger.i("GetChapterDetail", "Using downloaded chapter: $targetUrl")
            return downloaded
        }
    }

    AppLogger.i("GetChapterDetail", "Fetching chapter: ${chapter.name} at $targetUrl")
    val responseBody = try {
        getPage(
            authorization,
            targetUrl,
            PageCacheTtl.CHAPTER,
            forceRefresh,
            PageKind.CHAPTER
        )
    } catch (error: Exception) {
        NovelDownloadStore.readChapter(targetUrl)?.let { downloaded ->
            AppLogger.w(
                "GetChapterDetail",
                "Network unavailable; falling back to downloaded chapter: $targetUrl",
                error
            )
            return downloaded
        }
        throw error
    }

    val document = Jsoup.parse(responseBody, targetUrl)

    val contentElement = chapterSelector.first(document, ".forum-content.mt-3, .forum-content")
    val components = if (contentElement != null) {
        analyseComponents(contentElement)
    } else {
        AppLogger.w("GetChapterDetail", "No content element found in chapter page: $targetUrl")
        listOf()
    }

    val previousChapter = chapterSelector.first(
        document,
        "a.btn-prev, a[rel='prev'], a[data-direction='previous']"
    )
    val nextChapter = chapterSelector.first(
        document,
        "a.btn-next, a[rel='next'], a[data-direction='next']"
    )

    val previous = parseChapterNav(previousChapter, targetUrl)
    val next = parseChapterNav(nextChapter, targetUrl)

    return DetailedChapter(
        chapter.name,
        components,
        previous,
        next,
        contentElement?.html(),
        targetUrl
    )
}

private fun parseChapterNav(element: Element?, targetUrl: String): Chapter? {
    if (element == null || element.hasClass("disabled")) return null
    val rawHref = element.attr("href").trim()
    if (rawHref.isBlank() || rawHref == "#" || rawHref.startsWith("javascript:", ignoreCase = true)) {
        return null
    }
    val resolvedUrl = EsjzoneUrls.resolve(rawHref, targetUrl).takeIf { it.isNotBlank() } ?: return null
    val title = element.attr("data-title").ifBlank { element.text() }.trim()
    return Chapter(title, resolvedUrl, false)
}
