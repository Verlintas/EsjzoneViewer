package com.breakyuna.esjzone.novellibrary.data

import androidx.compose.runtime.Immutable
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovel
import java.time.LocalDate

/** One date tab exposed by ESJ's `/update/` page. */
@Immutable
data class WeeklyUpdateDay(
    val date: LocalDate,
    val novels: List<CoveredNovel>
)
