package com.breakyuna.esjzone.ui.page

/** Decides when a browser challenge result is ready to hand back to the reader. */
internal class WenkuVerificationPolicy(private val acceptChapterContent: Boolean) {
    enum class Decision { WAIT, USE_CHAPTER_CONTENT, USE_CLEARANCE }

    private var clearanceObservedAtMillis: Long? = null

    fun next(
        hasReadableChapter: Boolean,
        hasClearance: Boolean,
        nowElapsedMillis: Long
    ): Decision {
        if (acceptChapterContent && hasReadableChapter) return Decision.USE_CHAPTER_CONTENT
        if (hasClearance) {
            if (!acceptChapterContent) return Decision.USE_CLEARANCE
            val firstSeen = clearanceObservedAtMillis
                ?: nowElapsedMillis.also { clearanceObservedAtMillis = it }
            if (nowElapsedMillis - firstSeen >= CLEARANCE_CONTENT_WAIT_MILLIS) {
                return Decision.USE_CLEARANCE
            }
        } else {
            clearanceObservedAtMillis = null
        }
        return Decision.WAIT
    }

    fun shouldContinue(nowElapsedMillis: Long, checkStartedAtMillis: Long): Boolean =
        nowElapsedMillis - checkStartedAtMillis < MAX_CHECK_DURATION_MILLIS ||
            clearanceObservedAtMillis?.let {
                nowElapsedMillis - it < CLEARANCE_CONTENT_WAIT_MILLIS
            } == true

    companion object {
        const val MAX_CHECK_DURATION_MILLIS = 60_000L
        const val CLEARANCE_CONTENT_WAIT_MILLIS = 20_000L
    }
}
