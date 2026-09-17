package com.breakyuna.esjzone.novellibrary.data

import com.breakyuna.esjzone.novellibrary.novel.CoveredNovel
import com.google.gson.annotations.SerializedName
import java.io.Serializable

class HomeData(
    val recentlyUpdateTranslated: List<CoveredNovel>,
    val recentlyUpdateOriginal: List<CoveredNovel>,
    val recentlyUpdateTranslatedR18: List<CoveredNovel>,
    val recentlyUpdateOriginalR18: List<CoveredNovel>,
    val recommendation: List<CoveredNovel>,
    /** The site-provided days for the current Monday-to-today update window. */
    val weeklyUpdates: List<WeeklyUpdateDay> = emptyList(),
    /** The first ten entries from the site's server-rendered previous-week ranking. */
    val weeklyPopular: List<WeeklyPopularNovel> = emptyList()
)

data class WeeklyPopularNovel(
    @SerializedName("rank")
    val rank: Int = 0,
    @SerializedName("weeklyViews")
    val weeklyViews: Int = 0,
    @SerializedName("descriptionPreview")
    val descriptionPreview: String = "",
    @SerializedName("type")
    val type: String = "",
    @SerializedName("coverUrl")
    override val coverUrl: String = "",
    @SerializedName("name")
    override val name: String = "",
    @SerializedName("url")
    override val url: String = "",
    @SerializedName("views")
    override val views: Int = 0,
    @SerializedName("likes")
    override val likes: Int = 0,
    @SerializedName("isAdult")
    override val isAdult: Boolean = false,
    @SerializedName("author")
    override val author: String? = null
) : CoveredNovel, Serializable
