package com.breakyuna.esjzone.database

import com.breakyuna.esjzone.database.entity.BookshelfEntry
import com.breakyuna.esjzone.database.entity.LocalReadingActivity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** Pure ordering rules for the local-first bookshelf. */
object BookshelfSort {

    enum class Order {
        RECENT_READ,
        RECENT_ADDED,
        RECENT_UPDATED
    }

    /**
     * Recently read books come first. Books without a local reading record
     * follow by local insertion time. The maps avoid an O(n*m) pairwise scan.
     */
    fun sort(
        entries: List<BookshelfEntry>,
        activities: List<LocalReadingActivity>,
        keyForUrl: (String) -> String,
        order: Order = Order.RECENT_READ
    ): List<BookshelfEntry> {
        val latestByNovelId = HashMap<String, Long>()
        val latestByBookKey = HashMap<String, Long>()

        activities.forEach { activity ->
            if (activity.novelId.isNotBlank()) {
                latestByNovelId[activity.novelId] = maxOf(
                    latestByNovelId[activity.novelId] ?: Long.MIN_VALUE,
                    activity.lastReadAt
                )
            }
            val bookKey = activity.novelUrl.takeIf { it.isNotBlank() }?.let(keyForUrl).orEmpty()
            if (bookKey.isNotBlank()) {
                latestByBookKey[bookKey] = maxOf(
                    latestByBookKey[bookKey] ?: Long.MIN_VALUE,
                    activity.lastReadAt
                )
            }
        }

        // Resolve each row once before sorting. URL normalization therefore
        // remains O(n), rather than being repeated inside O(n log n) compares.
        return entries.map { entry ->
            RankedEntry(
                entry = entry,
                readAt = readingAt(entry, latestByNovelId, latestByBookKey, keyForUrl)
            )
        }.sortedWith(
            Comparator { left, right ->
                val comparison = when (order) {
                    Order.RECENT_READ -> compareNullableDescending(left.readAt, right.readAt)
                        .takeIf { it != 0 }
                        ?: right.entry.addedAt.compareTo(left.entry.addedAt)
                    Order.RECENT_ADDED -> right.entry.addedAt.compareTo(left.entry.addedAt)
                    Order.RECENT_UPDATED -> compareNullableDescending(
                        updatedAt(left.entry), updatedAt(right.entry)
                    ).takeIf { it != 0 }
                        ?: right.entry.addedAt.compareTo(left.entry.addedAt)
                }
                comparison.takeIf { it != 0 } ?: left.entry.bookKey.compareTo(right.entry.bookKey)
            }
        ).map { it.entry }
    }

    private data class RankedEntry(
        val entry: BookshelfEntry,
        val readAt: Long?
    )

    private fun compareNullableDescending(left: Long?, right: Long?): Int = when {
        left != null && right == null -> -1
        left == null && right != null -> 1
        left != null && right != null -> right.compareTo(left)
        else -> 0
    }

    /**
     * The site exposes update dates as text. Parse the observed date forms when
     * possible, while retaining addedAt as a stable fallback for old rows or
     * pages whose date text is unavailable.
     */
    private fun updatedAt(entry: BookshelfEntry): Long? = entry.remoteUpdatedAt
        .trim()
        .takeIf(String::isNotBlank)
        ?.let(::parseDate)

    private fun parseDate(value: String): Long? {
        val normalized = value
            .replace('年', '-')
            .replace('月', '-')
            .replace("日", "")
            .trim()
        val zone = ZoneId.systemDefault()
        DATE_TIME_FORMATTERS.forEach { formatter ->
            try {
                return LocalDateTime.parse(normalized, formatter).atZone(zone).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
                // Try the next observed format.
            }
        }
        DATE_FORMATTERS.forEach { formatter ->
            try {
                return LocalDate.parse(normalized, formatter).atStartOfDay(zone).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
                // Try the next observed format.
            }
        }
        return null
    }

    private val DATE_TIME_FORMATTERS = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT),
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss", Locale.ROOT),
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm", Locale.ROOT)
    )

    private val DATE_FORMATTERS = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT),
        DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.ROOT)
    )

    private fun readingAt(
        entry: BookshelfEntry,
        latestByNovelId: Map<String, Long>,
        latestByBookKey: Map<String, Long>,
        keyForUrl: (String) -> String
    ): Long? = latestByNovelId[entry.novelId]
        ?: latestByBookKey[entry.bookKey]
        ?: entry.url.takeIf { it.isNotBlank() }?.let(keyForUrl)?.let(latestByBookKey::get)
}
