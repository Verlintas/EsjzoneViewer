package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.features.selectWeeklyPopularSeeds
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Test

class WeeklyPopularParserTest {
    @Test
    fun `keeps only the first ten ranked novels`() {
        val items = (1..12).joinToString("") { index ->
            "<li><a href='/detail/$index.html'>Novel $index</a><span>${index * 1000}</span></li>"
        }
        val document = Jsoup.parse("<section class='widget-categories-hot'><ul>$items</ul></section>")

        val result = selectWeeklyPopularSeeds(document)

        assertEquals(10, result.size)
        assertEquals(1, result.first().rank)
        assertEquals("/detail/1.html", result.first().url)
        assertEquals(10_000, result.last().weeklyViews)
    }
}
