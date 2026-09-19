package com.breakyuna.esjzone.ui.tab

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.novellibrary.data.WeeklyUpdateDay
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovel
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.discovery.DiscoveryEmptyState
import java.time.DayOfWeek

internal const val WEEKLY_UPDATE_MAX_ITEMS = 24

internal fun LazyListScope.weeklyUpdatesCollection(
    days: List<WeeklyUpdateDay>,
    selectedIndex: Int,
    novelsByDay: List<List<CoveredNovel>>,
    showDivider: Boolean,
    onSelect: (Int) -> Unit,
    onNovelClick: (CoveredNovel) -> Unit
) {
    if (days.isEmpty()) return
    if (showDivider) {
        item(key = "home-weekly-divider", contentType = "home-section-divider") {
            HomeSectionDividerItem()
        }
    }
    item(key = "home-weekly-header", contentType = "home-weekly-header") {
        WeeklyUpdatesHeader(days, selectedIndex, onSelect)
    }

    val novels = novelsByDay.getOrNull(selectedIndex).orEmpty()
    if (novels.isEmpty()) {
        item(key = "home-weekly-empty-$selectedIndex", contentType = "empty") {
            DiscoveryEmptyState(
                title = stringResource(R.string.home_collection_empty_title),
                message = stringResource(R.string.home_weekly_update_empty)
            )
        }
    } else {
        val rows = novels.chunked(HOME_GRID_COLUMNS)
        items(
            count = rows.size,
            key = { rowIndex -> "home-weekly-$selectedIndex-row:${novelKey(rows[rowIndex].first())}" },
            contentType = { "home-grid-row" }
        ) { rowIndex ->
            val row = rows[rowIndex]
            NovelGridRow(
                row = row,
                showLatestTitle = true,
                onNovelClick = onNovelClick,
                modifier = Modifier
                    .padding(
                        top = if (rowIndex == 0) AppSpacing.sm else AppSpacing.zero,
                        bottom = if (rowIndex < rows.size - 1) AppSpacing.lg else AppSpacing.zero
                    )
                    .semantics { contentDescription = "Weekly update row $rowIndex" }
            )
        }
    }
}

@Composable
private fun WeeklyUpdatesHeader(
    days: List<WeeklyUpdateDay>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weeklyDaySwipe(days, selectedIndex, onSelect),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.home_weekly_updates), style = MaterialTheme.typography.titleMedium)
        }
        TabRow(selectedTabIndex = selectedIndex) {
            days.forEachIndexed { index, day ->
                Tab(
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                    text = { Text(weeklyDayLabel(day.date.dayOfWeek), maxLines = 1, overflow = TextOverflow.Clip) }
                )
            }
        }
    }
}

@Composable
private fun Modifier.weeklyDaySwipe(
    days: List<WeeklyUpdateDay>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
): Modifier {
    val swipeThreshold = with(LocalDensity.current) { 48.dp.toPx() }
    return pointerInput(days, selectedIndex, swipeThreshold) {
        var dragDistance = 0f
        detectHorizontalDragGestures(
            onHorizontalDrag = { change, dragAmount ->
                change.consume()
                dragDistance += dragAmount
            },
            onDragCancel = { dragDistance = 0f },
            onDragEnd = {
                val target = when {
                    dragDistance <= -swipeThreshold -> (selectedIndex + 1).coerceAtMost(days.lastIndex)
                    dragDistance >= swipeThreshold -> (selectedIndex - 1).coerceAtLeast(0)
                    else -> selectedIndex
                }
                if (target != selectedIndex) onSelect(target)
                dragDistance = 0f
            }
        )
    }
}

@Composable
private fun weeklyDayLabel(day: DayOfWeek): String = stringResource(
    when (day) {
        DayOfWeek.MONDAY -> R.string.home_weekday_monday
        DayOfWeek.TUESDAY -> R.string.home_weekday_tuesday
        DayOfWeek.WEDNESDAY -> R.string.home_weekday_wednesday
        DayOfWeek.THURSDAY -> R.string.home_weekday_thursday
        DayOfWeek.FRIDAY -> R.string.home_weekday_friday
        DayOfWeek.SATURDAY -> R.string.home_weekday_saturday
        DayOfWeek.SUNDAY -> R.string.home_weekday_sunday
    }
)
