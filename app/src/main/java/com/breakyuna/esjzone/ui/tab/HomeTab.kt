@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.breakyuna.esjzone.ui.tab

import androidx.lifecycle.viewModelScope

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.network.features.HomeDataCache
import com.breakyuna.esjzone.network.features.getHomeData
import com.breakyuna.esjzone.network.loadFailureKind
import com.breakyuna.esjzone.novellibrary.data.HomeData
import com.breakyuna.esjzone.novellibrary.data.WeeklyUpdateDay
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovel
import com.breakyuna.esjzone.ui.component.AppNovelCover
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.discovery.DiscoveryEmptyState
import com.breakyuna.esjzone.ui.discovery.DiscoveryErrorState
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.platform.LocalLayoutDirection
import com.breakyuna.esjzone.ui.discovery.DiscoveryOfflineBanner
import com.breakyuna.esjzone.ui.discovery.DiscoveryScaffold
import com.breakyuna.esjzone.ui.navigation.AppNavigator
import com.breakyuna.esjzone.ui.navigation.AppStateViewModel
import com.breakyuna.esjzone.ui.navigation.AppTab
import com.breakyuna.esjzone.ui.navigation.AppTabOptions
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.navigation.LocalFloatingNavPadding
import com.breakyuna.esjzone.ui.navigation.rememberAppViewModel
import com.breakyuna.esjzone.ui.page.ForumPage
import com.breakyuna.esjzone.ui.page.ForumPostPage
import com.breakyuna.esjzone.ui.page.GuestbookPage
import com.breakyuna.esjzone.ui.page.NovelListPage
import com.breakyuna.esjzone.ui.page.NovelPage
import com.breakyuna.esjzone.novellibrary.community.ForumTopic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.time.DayOfWeek

object HomeTab : AppTab {

    private fun readResolve(): Any = HomeTab

    override val options: AppTabOptions
        @Composable
        get() = AppTabOptions(
            index = 0,
            title = stringResource(R.string.screen_main_tab_home),
            icon = androidx.compose.ui.graphics.vector.rememberVectorPainter(image = Icons.Filled.Home)
        )

    @Composable
    @OptIn(ExperimentalMaterial3Api::class)
    override fun Content() {
        val navigator = LocalBaseNavigator.current
        val authorization = LocalAuthorization.current
        val model = rememberAppViewModel { HomeTabModel(authorization) }
        val state by model.state.collectAsState()
        val adult by PresentationAccess.settings.adult
        val editorPicksTitle = stringResource(R.string.home_editor_picks)
        val translatedTitle = stringResource(R.string.tab_home_recentlyupdate_tranlated)
        val originalTitle = stringResource(R.string.tab_home_recentlyupdate_original)
        val translatedAdultTitle = stringResource(R.string.tab_home_recentlyupdate_tranlated_r18)
        val originalAdultTitle = stringResource(R.string.tab_home_recentlyupdate_original_r18)
        val browseMoreLabel = stringResource(R.string.home_browse_more)
        val emptyCollectionTitle = stringResource(R.string.home_collection_empty_title)
        val emptyCollectionMessage = stringResource(R.string.home_collection_empty_message)
        val waterCoolerTitle = stringResource(R.string.home_water_cooler)

        val navPadding = LocalFloatingNavPadding.current
        val layoutDirection = LocalLayoutDirection.current
        val searchActionLabel = stringResource(R.string.search_action)
        val weeklyDays = (state as? HomeTabModel.State.Result)?.homeData?.weeklyUpdates
            .orEmpty().sortedByDescending { it.date }
        val weeklyDayKeys = weeklyDays.map { it.date.toString() }
        var selectedWeeklyDate by rememberSaveable(weeklyDayKeys) {
            mutableStateOf(weeklyDayKeys.firstOrNull().orEmpty())
        }
        val weeklyIndex = weeklyDays.indexOfFirst { it.date.toString() == selectedWeeklyDate }
            .takeIf { it >= 0 } ?: 0
        val weeklyNovelsByDay = remember(weeklyDays, adult) {
            weeklyDays.map { day ->
                day.novels.asSequence()
                    .filter { adult || !it.isAdult }
                    .distinctBy { it.url.trim().ifBlank { it.name.trim() } }
                    .take(WEEKLY_UPDATE_MAX_ITEMS)
                    .toList()
            }
        }

        DiscoveryScaffold(
            title = stringResource(R.string.home_discover),
            actions = {
                HomeAction(
                    label = searchActionLabel,
                    icon = Icons.Filled.Search,
                    onClick = { navigator?.pushIfNotCurrent(SearchTab) }
                )
            }
        ) { padding ->
            PullToRefreshBox(
                isRefreshing = (state as? HomeTabModel.State.Result)?.isSyncing == true,
                onRefresh = model::reload,
                modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())
            ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp + navPadding.calculateStartPadding(layoutDirection),
                    end = 16.dp,
                    top = AppSpacing.sm,
                    bottom = AppSpacing.sm + navPadding.calculateBottomPadding()
                ),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                item(key = "home-actions", contentType = "home-actions") {
                    HomeActions(
                        onCategories = { navigator?.pushIfNotCurrent(CategoryBrowserPage()) },
                        onForum = { navigator?.pushIfNotCurrent(ForumPage) },
                        onGuestbook = { navigator?.pushIfNotCurrent(GuestbookPage) },
                        onWaterCooler = {
                            navigator?.pushIfNotCurrent(
                                ForumPostPage(
                                    ForumTopic(
                                        boardId = "1585405223",
                                        id = "103280",
                                        title = waterCoolerTitle,
                                        author = null,
                                        createdAt = null,
                                        replyCount = null,
                                        viewCount = null,
                                        lastReplyAt = null,
                                        url = WATER_COOLER_URL
                                    )
                                )
                            )
                        }
                    )
                }
                when (val snapshot = state) {
                    HomeTabModel.State.Loading -> item(key = "home-loading", contentType = "loading") {
                        HomeInitialLoadingState()
                    }
                    is HomeTabModel.State.Error -> item(key = "home-error", contentType = "error") {
                        Column {
                            if (snapshot.failure == LoadFailureKind.NETWORK) {
                                DiscoveryOfflineBanner(modifier = Modifier.padding(bottom = 8.dp))
                            }
                            DiscoveryErrorState(
                                message = stringResource(failureMessage(snapshot.failure)),
                                onRetry = model::reload
                            )
                        }
                    }
                    is HomeTabModel.State.Result -> {
                        homeCollection(
                            title = editorPicksTitle,
                            novels = snapshot.homeData.recommendation,
                            showDivider = true,
                            onMore = null,
                            adult = adult,
                            navigator = navigator,
                            browseMoreLabel = browseMoreLabel,
                            emptyTitle = emptyCollectionTitle,
                            emptyMessage = emptyCollectionMessage
                        )
                        homeCollection(
                            title = translatedTitle,
                            novels = snapshot.homeData.recentlyUpdateTranslated,
                            showDivider = true,
                            onMore = { navigator?.pushIfNotCurrent(NovelListPage(1, 1, false)) },
                            adult = adult,
                            navigator = navigator,
                            browseMoreLabel = browseMoreLabel,
                            emptyTitle = emptyCollectionTitle,
                            emptyMessage = emptyCollectionMessage
                        )
                        homeCollection(
                            title = originalTitle,
                            novels = snapshot.homeData.recentlyUpdateOriginal,
                            showDivider = true,
                            onMore = { navigator?.pushIfNotCurrent(NovelListPage(2, 1, false)) },
                            adult = adult,
                            navigator = navigator,
                            browseMoreLabel = browseMoreLabel,
                            emptyTitle = emptyCollectionTitle,
                            emptyMessage = emptyCollectionMessage
                        )
                        if (adult) {
                            homeCollection(
                                title = translatedAdultTitle,
                                novels = snapshot.homeData.recentlyUpdateTranslatedR18,
                                showDivider = true,
                                onMore = { navigator?.pushIfNotCurrent(NovelListPage(1, 1, true)) },
                                adult = true,
                                navigator = navigator,
                                browseMoreLabel = browseMoreLabel,
                                emptyTitle = emptyCollectionTitle,
                                emptyMessage = emptyCollectionMessage
                            )
                            homeCollection(
                                title = originalAdultTitle,
                                novels = snapshot.homeData.recentlyUpdateOriginalR18,
                                showDivider = true,
                                onMore = { navigator?.pushIfNotCurrent(NovelListPage(2, 1, true)) },
                                adult = true,
                                navigator = navigator,
                                browseMoreLabel = browseMoreLabel,
                                emptyTitle = emptyCollectionTitle,
                                emptyMessage = emptyCollectionMessage
                            )
                        }
                        weeklyUpdatesCollection(
                            days = weeklyDays,
                            selectedIndex = weeklyIndex,
                            novelsByDay = weeklyNovelsByDay,
                            showDivider = true,
                            onSelect = { selectedWeeklyDate = weeklyDays[it].date.toString() },
                            navigator = navigator
                        )
                    }
                }
            }
            }
        }

        LaunchedEffect(Unit) { model.getHomeData() }
    }
}

@Composable
private fun HomeInitialLoadingState() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(AppSpacing.xxxl),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

private const val HOME_GRID_COLUMNS = 4
private const val WEEKLY_UPDATE_MAX_ITEMS = 24
private const val WEEKLY_UPDATE_TRANSITION_DURATION = 280

private const val WATER_COOLER_URL =
    "https://www.esjzone.cc/forum/1585405223/103280.html"

private fun LazyListScope.weeklyUpdatesCollection(
    days: List<WeeklyUpdateDay>,
    selectedIndex: Int,
    novelsByDay: List<List<CoveredNovel>>,
    showDivider: Boolean,
    onSelect: (Int) -> Unit,
    navigator: AppNavigator?
) {
    if (days.isEmpty()) return
    if (showDivider) {
        homeSectionDivider(key = "home-weekly-divider")
    }
    item(key = "home-weekly-header", contentType = "home-weekly-header") {
        WeeklyUpdatesHeader(days, selectedIndex, onSelect)
    }
    item(key = "home-weekly-content", contentType = "home-weekly-content") {
        AnimatedContent(
            targetState = selectedIndex,
            transitionSpec = {
                if (targetState > initialState) {
                    slideInHorizontally(animationSpec = tween(WEEKLY_UPDATE_TRANSITION_DURATION)) { fullWidth -> fullWidth } togetherWith
                        slideOutHorizontally(animationSpec = tween(WEEKLY_UPDATE_TRANSITION_DURATION)) { fullWidth -> -fullWidth }
                } else {
                    slideInHorizontally(animationSpec = tween(WEEKLY_UPDATE_TRANSITION_DURATION)) { fullWidth -> -fullWidth } togetherWith
                        slideOutHorizontally(animationSpec = tween(WEEKLY_UPDATE_TRANSITION_DURATION)) { fullWidth -> fullWidth }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AppSpacing.sm)
                .weeklyDaySwipe(days, selectedIndex, onSelect),
            label = "weekly-update-date-transition"
        ) { index ->
            val novels = novelsByDay.getOrNull(index).orEmpty()
            if (novels.isEmpty()) {
                DiscoveryEmptyState(
                    title = stringResource(R.string.home_collection_empty_title),
                    message = stringResource(R.string.home_weekly_update_empty)
                )
            } else {
                HomeNovelGrid(
                    novels = novels,
                    showLatestTitle = true,
                    onNovelClick = { novel -> navigator?.pushIfNotCurrent(NovelPage(novel)) }
                )
            }
        }
    }
}

@Composable
private fun HomeNovelGrid(
    novels: List<CoveredNovel>,
    showLatestTitle: Boolean = false,
    onNovelClick: (CoveredNovel) -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = remember(novels) { novels.chunked(HOME_GRID_COLUMNS) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.md / 2)
            ) {
                row.forEach { novel ->
                    HomeGridNovelTile(
                        novel = novel,
                        showLatestTitle = showLatestTitle,
                        onClick = { onNovelClick(novel) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(HOME_GRID_COLUMNS - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
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
        modifier = Modifier.fillMaxWidth().padding(top = AppSpacing.md)
            .weeklyDaySwipe(days, selectedIndex, onSelect),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        Text(stringResource(R.string.home_weekly_updates), style = MaterialTheme.typography.titleMedium)
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
private fun HomeGridNovelTile(
    novel: CoveredNovel,
    showLatestTitle: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
    ) {
        AppNovelCover(
            coverUrl = novel.coverUrl,
            title = novel.name,
            isAdult = novel.isAdult,
            modifier = Modifier.fillMaxWidth().aspectRatio(0.7f)
        )
        Text(
            text = novel.name,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (showLatestTitle) {
            Text(
                text = novel.latestTitle?.trim().takeUnless { it.isNullOrBlank() }
                    ?: stringResource(R.string.home_weekly_update_no_chapter),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
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

@Composable
private fun HomeActions(
    onCategories: () -> Unit,
    onForum: () -> Unit,
    onGuestbook: () -> Unit,
    onWaterCooler: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        HomeAction(stringResource(R.string.categories), Icons.Filled.Category, onCategories)
        HomeAction(stringResource(R.string.forum), Icons.Filled.Forum, onForum)
        HomeAction(stringResource(R.string.guestbook), Icons.Filled.Forum, onGuestbook)
        HomeWaterCoolerAction(
            label = stringResource(R.string.home_water_cooler),
            onClick = onWaterCooler
        )
    }
}

@Composable
private fun HomeAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = label }) {
        Icon(icon, contentDescription = null)
    }
}

@Composable
private fun HomeWaterCoolerAction(
    label: String,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = label }) {
        Text(
            text = "水",
            style = MaterialTheme.typography.titleMedium
        )
    }
}

private const val HOME_COLLECTION_MAX_ITEMS = 16

private fun LazyListScope.homeCollection(
    title: String,
    novels: List<CoveredNovel>,
    showDivider: Boolean,
    onMore: (() -> Unit)?,
    adult: Boolean,
    navigator: AppNavigator?,
    browseMoreLabel: String,
    emptyTitle: String,
    emptyMessage: String
) {
    val visible = novels
        .asSequence()
        .filter { adult || !it.isAdult }
        .distinctBy { it.url.trim().ifBlank { it.name.trim() } }
        .take(HOME_COLLECTION_MAX_ITEMS)
        .toList()

    if (showDivider) {
        homeSectionDivider(key = "home-divider-$title")
    }
    item(key = "home-section-$title", contentType = "home-section") {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(
                text = homeSectionTitleText(title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            if (onMore != null) {
                TextButton(onClick = onMore) { Text(browseMoreLabel) }
            }
        }
    }
    if (visible.isEmpty()) {
        item(key = "home-empty-$title", contentType = "empty") {
            DiscoveryEmptyState(
                title = emptyTitle,
                message = emptyMessage
            )
        }
    } else {
        item(key = "home-grid-$title", contentType = "home-grid") {
            HomeNovelGrid(
                novels = visible,
                onNovelClick = { novel -> navigator?.pushIfNotCurrent(NovelPage(novel)) },
                modifier = Modifier.semantics { contentDescription = title }
            )
        }
    }
}

private fun LazyListScope.homeSectionDivider(key: String) {
    item(key = key, contentType = "home-section-divider") {
        HorizontalDivider(
            modifier = Modifier.padding(top = AppSpacing.md, bottom = AppSpacing.sm),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
        )
    }
}

/** Gives optional parenthetical section descriptors a subordinate visual weight. */
@Composable
private fun homeSectionTitleText(title: String): androidx.compose.ui.text.AnnotatedString {
    val descriptorFontSize = MaterialTheme.typography.labelLarge.fontSize
    return buildAnnotatedString {
        var cursor = 0
        HOME_SECTION_TITLE_PARENTHESIS.findAll(title).forEach { match ->
            append(title.substring(cursor, match.range.first))
            withStyle(SpanStyle(fontSize = descriptorFontSize)) {
                append(match.value)
            }
            cursor = match.range.last + 1
        }
        append(title.substring(cursor))
    }
}

private val HOME_SECTION_TITLE_PARENTHESIS = Regex("[（(][^（）()]*[）)]")

private fun failureMessage(failure: LoadFailureKind): Int = when (failure) {
    LoadFailureKind.NETWORK -> R.string.load_network_error
    LoadFailureKind.CLIENT -> R.string.load_client_error
}

class HomeTabModel(
    private val authorization: Authorization
) : AppStateViewModel<HomeTabModel.State>(
    HomeDataCache.readSnapshot()?.let { State.Result(it) } ?: State.Loading
) {

    private var loadStarted = false

    sealed class State {
        data object Loading : State()
        data class Error(val failure: LoadFailureKind) : State()
        data class Result(
            val homeData: HomeData,
            val isSyncing: Boolean = false
        ) : State()
    }

    fun getHomeData(forceRefresh: Boolean = false) {
        if (loadStarted) return
        loadStarted = true
        viewModelScope.launch(Dispatchers.IO) {
            val visibleData = mutableState.value as? State.Result
            mutableState.value = visibleData?.copy(isSyncing = true) ?: State.Loading
            try {
                val data = PresentationAccess.client.getHomeData(authorization, forceRefresh = forceRefresh)
                ensureActive()
                HomeDataCache.writeSnapshot(data)
                mutableState.value = State.Result(data)
            } catch (e: CancellationException) {
                loadStarted = false
                throw e
            } catch (e: Exception) {
                if (visibleData == null) {
                    mutableState.value = State.Error(e.loadFailureKind())
                } else {
                    mutableState.value = visibleData.copy(isSyncing = false)
                }
                loadStarted = false
                com.breakyuna.esjzone.util.AppLogger.e("HomeTabModel", "Failed to load home data", e)
            }
        }
    }

    fun reload() {
        loadStarted = false
        getHomeData(forceRefresh = true)
    }
}
