package com.breakyuna.esjzone.database

import com.breakyuna.esjzone.EsjzoneApplication
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.database.entity.BookshelfEntry
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.util.AppLogger
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Durable local copies of bookshelf covers.
 *
 * Coil remains the common image cache for the rest of the app, while every
 * visible bookshelf item gets a stable app-files copy.  The file name includes
 * the source URL fingerprint, so a changed remote cover naturally gets a new
 * local file instead of serving stale artwork.
 */
object BookshelfCoverStore {
    private const val DIRECTORY_NAME = "bookshelf_covers"
    private const val BATCH_SIZE = 36
    private const val BATCH_COOLDOWN_MILLIS = 3_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeKeys = ConcurrentHashMap.newKeySet<String>()
    private val downloadSemaphore = Semaphore(2)

    private val directory: File
        get() = File(EsjzoneApplication.instance.filesDir, DIRECTORY_NAME).apply { mkdirs() }

    private fun fileName(entry: BookshelfEntry): String {
        val source = EsjzoneUrls.coverOrEmpty(entry.coverUrl)
        val identity = "${entry.bookKey}|$source"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "cover_${digest}.img"
    }

    private fun fileFor(entry: BookshelfEntry): File = File(directory, fileName(entry))

    /** Returns a durable local URI when present, otherwise the original URL. */
    fun localOrRemote(entry: BookshelfEntry): String {
        val local = fileFor(entry)
        return if (local.isFile && local.length() > 0L) local.toURI().toString() else entry.coverUrl
    }

    /** Schedules a throttled, best-effort persistence pass without blocking shelf UI. */
    fun schedulePersist(entries: List<BookshelfEntry>) {
        val pending = entries.filter { entry ->
            entry.visible && EsjzoneUrls.coverOrEmpty(entry.coverUrl).isNotBlank() &&
                !fileFor(entry).isFile && activeKeys.add(fileName(entry))
        }
        if (pending.isEmpty()) return
        scope.launch {
            try {
                pending.chunked(BATCH_SIZE).forEachIndexed { index, batch ->
                    coroutineScope {
                        batch.map { entry ->
                            async {
                                downloadSemaphore.withPermit { persist(entry) }
                            }
                        }.awaitAll()
                    }
                    if (index < pending.lastIndex / BATCH_SIZE) delay(BATCH_COOLDOWN_MILLIS)
                }
            } finally {
                pending.forEach { activeKeys.remove(fileName(it)) }
            }
        }
    }

    private suspend fun persist(entry: BookshelfEntry) = withContext(Dispatchers.IO) {
        val target = fileFor(entry)
        if (target.isFile && target.length() > 0L) return@withContext
        val source = EsjzoneUrls.coverOrEmpty(entry.coverUrl)
        if (source.isBlank()) return@withContext

        // First use an already-downloaded Coil source.  This avoids duplicate
        // transfers for the covers visible while the shelf is opened.
        val cached = BookmarkCoverStore.findCoilCachedCover(source)
        if (cached != null && copyAtomically(cached, target)) return@withContext

        // A cold shelf still needs to become fully offline-capable.  Loading via
        // the app-scoped Coil loader keeps this request in the same cache and
        // networking pipeline as normal image rendering.
        try {
            val request = ImageRequest.Builder(EsjzoneApplication.instance)
                .data(source)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .networkCachePolicy(CachePolicy.ENABLED)
                .build()
            PresentationAccess.imageLoader.execute(request)
            BookmarkCoverStore.findCoilCachedCover(source)?.let { copyAtomically(it, target) }
        } catch (error: Exception) {
            AppLogger.w("BookshelfCoverStore", "Unable to persist cover for ${entry.bookKey}", error)
        }
    }

    private fun copyAtomically(source: File, target: File): Boolean {
        if (!source.isFile || source.length() <= 0L) return false
        return runCatching {
            val temp = File(directory, "${target.name}.tmp")
            source.copyTo(temp, overwrite = true)
            if (temp.renameTo(target)) true else {
                temp.delete()
                false
            }
        }.getOrElse { error ->
            AppLogger.w("BookshelfCoverStore", "Unable to copy ${target.name}", error)
            false
        }
    }
}
