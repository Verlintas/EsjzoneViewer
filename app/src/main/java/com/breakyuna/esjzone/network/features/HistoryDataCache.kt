package com.breakyuna.esjzone.network.features

import android.content.Context
import com.breakyuna.esjzone.EsjzoneApplication
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.HistoryNovel
import com.breakyuna.esjzone.util.AppLogger
import com.google.gson.Gson
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Persistent cloud-history snapshot used to keep the latest known list visible
 * while an account refresh is running.
 */
object HistoryDataCache {

    private const val FILE_NAME = "history_data_snapshot.json"
    private val gson = Gson()
    private val ioLock = Any()

    @Volatile
    private var memoryCache: List<HistoryNovel>? = null

    @Volatile
    private var storageDir: File? = null

    fun initialize(context: Context) {
        val dir = context.applicationContext.filesDir
        storageDir = dir
        if (memoryCache == null) {
            synchronized(ioLock) {
                if (memoryCache == null) {
                    memoryCache = loadFromDisk(dir)
                }
            }
        }
    }

    fun readSnapshot(): List<HistoryNovel>? {
        memoryCache?.let { return it }
        val dir = storageDir ?: runCatching {
            EsjzoneApplication.instance.filesDir
        }.getOrNull() ?: return null

        return synchronized(ioLock) {
            memoryCache ?: loadFromDisk(dir)?.also { memoryCache = it }
        }
    }

    fun writeSnapshot(histories: List<HistoryNovel>) {
        memoryCache = histories
        val dir = storageDir ?: runCatching {
            EsjzoneApplication.instance.filesDir
        }.getOrNull() ?: return

        synchronized(ioLock) {
            try {
                val json = gson.toJson(HistorySnapshot(histories.map(::toSnapshot)))
                val targetFile = File(dir, FILE_NAME)
                val tempFile = File(dir, "$FILE_NAME.tmp")
                tempFile.writeText(json, StandardCharsets.UTF_8)
                try {
                    Files.move(
                        tempFile.toPath(),
                        targetFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                    )
                } catch (_: Exception) {
                    Files.move(
                        tempFile.toPath(),
                        targetFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                    )
                }
            } catch (e: Exception) {
                AppLogger.w("HistoryDataCache", "Failed to write history snapshot", e)
            }
        }
    }

    private fun loadFromDisk(dir: File): List<HistoryNovel>? {
        val file = File(dir, FILE_NAME)
        if (!file.isFile) return null
        return try {
            val snapshot = gson.fromJson(file.readText(StandardCharsets.UTF_8), HistorySnapshot::class.java)
            snapshot?.histories?.map { it.toHistoryNovel() }
        } catch (e: Exception) {
            AppLogger.w("HistoryDataCache", "Failed to read history snapshot", e)
            null
        }
    }

    private data class HistorySnapshot(
        val histories: List<HistoryNovelSnapshot> = emptyList()
    )

    private data class HistoryNovelSnapshot(
        val name: String,
        val url: String,
        val vid: String,
        val chapterName: String,
        val chapterUrl: String,
        val chapterIsHistory: Boolean
    ) {
        fun toHistoryNovel(): HistoryNovel = HistoryNovel(
            name = name,
            url = url,
            vid = vid,
            chapter = Chapter(chapterName, chapterUrl, chapterIsHistory)
        )
    }

    private fun toSnapshot(history: HistoryNovel): HistoryNovelSnapshot = HistoryNovelSnapshot(
        name = history.name,
        url = history.url,
        vid = history.vid,
        chapterName = history.chapter.name,
        chapterUrl = history.chapter.url,
        chapterIsHistory = history.chapter.isHistory
    )
}
