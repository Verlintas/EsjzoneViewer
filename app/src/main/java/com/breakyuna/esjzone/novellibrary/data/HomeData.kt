package com.breakyuna.esjzone.novellibrary.data

import com.breakyuna.esjzone.novellibrary.novel.CoveredNovel
import java.io.Serializable

class HomeData(
    val recentlyUpdateTranslated: List<CoveredNovel>,
    val recentlyUpdateOriginal: List<CoveredNovel>,
    val recentlyUpdateTranslatedR18: List<CoveredNovel>,
    val recentlyUpdateOriginalR18: List<CoveredNovel>,
    val recommendation: List<CoveredNovel>,
    /** The site-provided days for the current Monday-to-today update window. */
    val weeklyUpdates: List<WeeklyUpdateDay> = emptyList(),
    /** The first five entries from the site's server-rendered previous-week ranking. */
    val weeklyPopular: List<WeeklyPopularNovel> = emptyList()
)

data class WeeklyPopularNovel(
    val rank: Int,
    val weeklyViews: Int,
    val descriptionPreview: String,
    val type: String,
    override val coverUrl: String,
    override val name: String,
    override val url: String,
    override val views: Int,
    override val likes: Int,
    override val isAdult: Boolean,
    override val author: String?
) : CoveredNovel, Serializable
