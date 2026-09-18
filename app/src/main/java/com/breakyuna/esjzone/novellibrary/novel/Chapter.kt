package com.breakyuna.esjzone.novellibrary.novel

import androidx.compose.runtime.Immutable
import com.breakyuna.esjzone.novellibrary.component.Component
import java.io.Serializable

private val URL_REGEX = "(?:https?://[^/]+)?/forum/([0-9]+)/[0-9]+\\.html".toRegex()

@Immutable
data class Chapter(
    val name: String,
    val url: String,
    val isHistory: Boolean
) : Serializable {

    val isExternal: Boolean
        get() {
            val trimmed = url.trim()
            if (trimmed.isBlank()) return true
            if (trimmed.startsWith("/")) {
                return !trimmed.contains("/forum/")
            }
            val isHttp = trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true)
            if (!isHttp) return true
            val isEsj = trimmed.contains("esjzone", ignoreCase = true)
            val isForum = trimmed.contains("/forum/", ignoreCase = true)
            return !(isEsj && isForum)
        }

    fun novelId(): String {
        return URL_REGEX.find(this.url)?.groupValues?.getOrNull(1).orEmpty()
    }
}

@Immutable
data class DetailedChapter(
    val name: String,
    val content: List<Component>,
    val previous: Chapter?,
    val next: Chapter?,
    val contentHtml: String? = null,
    val sourceUrl: String? = null
) : Serializable
