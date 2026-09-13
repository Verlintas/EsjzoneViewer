package com.breakyuna.esjzone

import com.breakyuna.esjzone.database.BookmarkCoverStore
import org.junit.Assert.assertEquals
import org.junit.Test

class BookmarkCoverStoreTest {

    @Test
    fun cleanNovelId_handlesNumericId() {
        assertEquals("1642059588", BookmarkCoverStore.cleanNovelId("1642059588"))
    }

    @Test
    fun cleanNovelId_handlesDetailPath() {
        assertEquals("1642059588", BookmarkCoverStore.cleanNovelId("/detail/1642059588.html"))
    }

    @Test
    fun cleanNovelId_extractsFromChapterUrlWhenIdIsBlank() {
        assertEquals(
            "1642059588",
            BookmarkCoverStore.cleanNovelId("", "https://www.esjzone.me/forum/1642059588/12345.html")
        )
    }

    @Test
    fun cleanNovelId_sameNovelProducesIdenticalKeyForMultipleChapters() {
        val chapter1 = BookmarkCoverStore.cleanNovelId("", "https://www.esjzone.me/forum/1642059588/1.html")
        val chapter5 = BookmarkCoverStore.cleanNovelId("1642059588", "https://www.esjzone.me/forum/1642059588/5.html")
        val chapterDetail = BookmarkCoverStore.cleanNovelId("/detail/1642059588.html", "")

        assertEquals(chapter1, chapter5)
        assertEquals(chapter1, chapterDetail)
    }
}
