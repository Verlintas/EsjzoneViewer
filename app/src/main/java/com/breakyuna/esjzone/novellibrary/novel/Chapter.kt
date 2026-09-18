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
