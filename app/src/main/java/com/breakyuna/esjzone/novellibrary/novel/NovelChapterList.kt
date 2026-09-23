package com.breakyuna.esjzone.novellibrary.novel

import androidx.compose.runtime.Immutable
import com.breakyuna.esjzone.novellibrary.component.ChapterItem
import com.breakyuna.esjzone.novellibrary.component.ChapterListItem
import com.breakyuna.esjzone.novellibrary.component.Item
import com.breakyuna.esjzone.novellibrary.component.analyseItems
import org.jsoup.nodes.Element
import java.io.Serializable

@Immutable
data class NovelChapterList(
    val items: List<Item>
) : Serializable {

    /** Chapters in the order shown by the detail page's canonical table of contents. */
    val orderedChapters: List<Chapter> = items.flatMap { item ->
        when (item) {
            is ChapterItem -> listOf(item.chapter)
            is ChapterListItem -> item.chapters
            else -> emptyList()
        }
    }

    val hasHistory: Boolean
    val toRead: Chapter?

    init {
        var hasHistory = false
        var toRead: Chapter? = null
        for (item in items) {
            if (item is ChapterItem) {
                if (toRead == null && !item.chapter.isExternal)
                    toRead = item.chapter
                if (item.chapter.isHistory && !item.chapter.isExternal) {
                    if (!item.chapter.isExternal && toRead?.isHistory != true)
                        toRead = item.chapter
                    hasHistory = true
                    break
                }
            } else if (item is ChapterListItem) {
                for (chapter in item.chapters) {
                    if (toRead == null && !chapter.isExternal)
                        toRead = chapter
                    if (chapter.isHistory && !chapter.isExternal) {
                        if (!chapter.isExternal && toRead?.isHistory != true)
                            toRead = chapter
                        hasHistory = true
                        break
                    }
                }
            }
        }
        this.hasHistory = hasHistory
        this.toRead = toRead
    }

}

fun analyseChapterList(element: Element): NovelChapterList {
    return NovelChapterList(
        analyseItems(element)
    )
}
