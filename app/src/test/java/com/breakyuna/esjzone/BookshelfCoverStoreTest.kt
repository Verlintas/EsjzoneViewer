package com.breakyuna.esjzone

import com.breakyuna.esjzone.database.BookshelfCoverStore
import com.breakyuna.esjzone.database.entity.BookshelfEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookshelfCoverStoreTest {

    @Test
    fun canonicalSource_normalizesEsjDomains() {
        val ccUrl = "https://www.esjzone.cc/assets/cover/123.jpg"
        val oneUrl = "https://www.esjzone.one/assets/cover/123.jpg"
        assertEquals("esj:/assets/cover/123.jpg", BookshelfCoverStore.canonicalSource(ccUrl))
        assertEquals("esj:/assets/cover/123.jpg", BookshelfCoverStore.canonicalSource(oneUrl))
    }

    @Test
    fun canonicalSource_preservesNonEsjDomains() {
        val externalUrl = "https://images.example.com/cover/novel.png"
        assertEquals("https://images.example.com/cover/novel.png", BookshelfCoverStore.canonicalSource(externalUrl))
    }

    @Test
    fun canonicalSource_handlesBlank() {
        assertEquals("", BookshelfCoverStore.canonicalSource(""))
        assertEquals("", BookshelfCoverStore.canonicalSource("   "))
    }

    @Test
    fun fileName_producesIdenticalFilenameAcrossDomains() {
        val entryCc = BookshelfEntry(
            scope = "domain:www.esjzone.cc",
            bookKey = "detail/123.html",
            url = "https://www.esjzone.cc/detail/123.html",
            title = "Test Novel",
            coverUrl = "https://www.esjzone.cc/assets/cover/123.jpg"
        )
        val entryOne = BookshelfEntry(
            scope = "domain:www.esjzone.one",
            bookKey = "detail/123.html",
            url = "https://www.esjzone.one/detail/123.html",
            title = "Test Novel",
            coverUrl = "https://www.esjzone.one/assets/cover/123.jpg"
        )
        assertEquals(BookshelfCoverStore.fileName(entryCc), BookshelfCoverStore.fileName(entryOne))
    }

    @Test
    fun fileName_changesWhenRemoteCoverPathChanges() {
        val entry1 = BookshelfEntry(
            scope = "domain:www.esjzone.one",
            bookKey = "detail/123.html",
            url = "https://www.esjzone.one/detail/123.html",
            title = "Test Novel",
            coverUrl = "https://www.esjzone.one/assets/cover/123.jpg"
        )
        val entry2 = BookshelfEntry(
            scope = "domain:www.esjzone.one",
            bookKey = "detail/123.html",
            url = "https://www.esjzone.one/detail/123.html",
            title = "Test Novel",
            coverUrl = "https://www.esjzone.one/assets/cover/456.jpg"
        )
        assertNotEquals(BookshelfCoverStore.fileName(entry1), BookshelfCoverStore.fileName(entry2))
    }

    @Test
    fun legacyFileNames_includesCandidateDomains() {
        val entryOne = BookshelfEntry(
            scope = "domain:www.esjzone.one",
            bookKey = "detail/123.html",
            url = "https://www.esjzone.one/detail/123.html",
            title = "Test Novel",
            coverUrl = "https://www.esjzone.one/assets/cover/123.jpg"
        )
        val legacyNames = BookshelfCoverStore.legacyFileNames(entryOne)
        assertTrue(legacyNames.isNotEmpty())
        val entryCc = BookshelfEntry(
            scope = "domain:www.esjzone.cc",
            bookKey = "detail/123.html",
            url = "https://www.esjzone.cc/detail/123.html",
            title = "Test Novel",
            coverUrl = "https://www.esjzone.cc/assets/cover/123.jpg"
        )
        val legacyNamesFromCc = BookshelfCoverStore.legacyFileNames(entryCc)
        assertTrue(legacyNames.any { it in legacyNamesFromCc })
    }
}
