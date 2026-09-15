@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.breakyuna.esjzone.ui.tab

import androidx.lifecycle.viewModelScope

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.network.features.HomeDataCache
import com.breakyuna.esjzone.network.features.getHomeData
import com.breakyuna.esjzone.network.features.novels
import com.breakyuna.esjzone.network.loadFailureKind
import com.breakyuna.esjzone.network.PageableRequester
import com.breakyuna.esjzone.novellibrary.data.HomeData
import com.breakyuna.esjzone.novellibrary.data.WeeklyUpdateDay
import com.breakyuna.esjzone.novellibrary.data.WeeklyPopularNovel
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import kotlin.random.Random

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
        val randomState by model.randomRecommendations.collectAsState()
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
        val randomRecommendationsTitle = stringResource(R.string.home_random_recommendations)
        val changeBatchLabel = stringResource(R.string.home_random_change_batch)

        val navPadding = LocalFloatingNavPadding.current
        val layoutDirection = LocalLayoutDirection.current
        val searchActionLabel = stringResource(R.string.search_action)
        val listState = rememberLazyListState()
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
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp + navPadding.calculateStartPadding(layoutDirection),
                    end = 16.dp,
                    top = AppSpacing.sm,
                    bottom = AppSpacing.sm + navPadding.calculateBottomPadding()
                ),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
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
                        weeklyPopularCarousel(
                            novels = snapshot.homeData.weeklyPopular.filter { adult || !it.isAdult },
                            navigator = navigator
                        )
                        item(key = "home-actions", contentType = "home-actions") {
                            HomeActions(
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
                        randomRecommendationsCollection(
                            state = randomState,
                            title = randomRecommendationsTitle,
                            changeBatchLabel = changeBatchLabel,
                            onChangeBatch = { model.replaceRandomRecommendations(adult) },
                            onRetry = { model.loadMoreRandomRecommendations(adult) },
                            navigator = navigator
                        )
                    }
                }
            }
            }
        }

        LaunchedEffect(Unit) { model.getHomeData() }
        LaunchedEffect(adult) { model.onRandomAdultModeChanged(adult) }
        LaunchedEffect(listState, randomState.hasMore) {
            snapshotFlow {
                val layout = listState.layoutInfo
                val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
                layout.totalItemsCount > 0 && lastVisible >= layout.totalItemsCount - 3
            }
                .distinctUntilChanged()
                .filter { it }
                .collect { model.loadMoreRandomRecommendations(adult) }
        }
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
private const val RANDOM_RECOMMENDATION_BATCH_SIZE = 32
private const val RANDOM_RECOMMENDATION_MAX_PAGES_PER_BATCH = 3

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

private fun LazyListScope.randomRecommendationsCollection(
    state: HomeTabModel.RandomRecommendationsState,
    title: String,
    changeBatchLabel: String,
    onChangeBatch: () -> Unit,
    onRetry: () -> Unit,
    navigator: AppNavigator?
) {
    homeSectionDivider(key = "home-random-divider")
    item(key = "home-random-header", contentType = "home-random-header") {
        Row(
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                enabled = !state.isLoading,
                onClick = onChangeBatch
            ) {
                Text(changeBatchLabel)
            }
        }
    }

    val rows = state.items.chunked(HOME_GRID_COLUMNS)
    items(
        items = rows,
        key = { row -> "home-random-row:${novelKey(row.first())}" },
        contentType = { "home-random-row" }
    ) { row ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = AppSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md / 2)
        ) {
            row.forEach { novel ->
                HomeGridNovelTile(
                    novel = novel,
                    showLatestTitle = false,
                    onClick = { navigator?.pushIfNotCurrent(NovelPage(novel)) },
                    modifier = Modifier.weight(1f)
                )
            }
            repeat(HOME_GRID_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }

    if (state.isLoading) {
        item(key = "home-random-loading", contentType = "loading") {
            Box(
                modifier = Modifier.fillMaxWidth().padding(AppSpacing.lg),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    } else if (state.failure != null) {
        item(key = "home-random-error", contentType = "error") {
            DiscoveryErrorState(
                message = stringResource(failureMessage(state.failure)),
                onRetry = onRetry
            )
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
        modifier = Modifier.fillMaxWidth()
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

private fun LazyListScope.weeklyPopularCarousel(
    novels: List<WeeklyPopularNovel>,
    navigator: AppNavigator?
) {
    val visible = novels.take(5)
    if (visible.isEmpty()) return

    item(key = "home-weekly-popular", contentType = "home-weekly-popular") {
        val pagerState = rememberPagerState(pageCount = { visible.size })
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            HorizontalPager(
                state = pagerState,
                pageSpacing = AppSpacing.sm,
                modifier = Modifier.fillMaxWidth()
            ) { page ->
                WeeklyPopularCard(
                    novel = visible[page],
                    onClick = { navigator?.pushIfNotCurrent(NovelPage(visible[page])) }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                visible.indices.forEach { index ->
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .width(if (index == pagerState.currentPage) 28.dp else 7.dp)
                            .height(7.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == pagerState.currentPage) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun WeeklyPopularCard(
    novel: WeeklyPopularNovel,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(224.dp),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                            MaterialTheme.colorScheme.surfaceContainer,
                            MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    )
                )
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Box(modifier = Modifier.width(116.dp).fillMaxSize()) {
                AppNovelCover(
                    coverUrl = novel.coverUrl,
                    title = novel.name,
                    modifier = Modifier.fillMaxSize(),
                    isAdult = novel.isAdult
                )
                Text(
                    text = stringResource(R.string.home_weekly_popular_badge, novel.rank),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onError,
                    modifier = Modifier
                        .padding(10.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.94f))
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text(
                    text = novel.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = novel.descriptionPreview.ifBlank { stringResource(R.string.home_weekly_popular_no_description) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.weight(1f))
                novel.author?.takeIf(String::isNotBlank)?.let {
                    Text(
                        text = stringResource(R.string.home_weekly_popular_author, it),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                novel.type.takeIf(String::isNotBlank)?.let {
                    Text(
                        text = stringResource(R.string.home_weekly_popular_type, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Text(
                    text = stringResource(
                        R.string.home_weekly_popular_heat,
                        formatWeeklyHeat(novel.weeklyViews)
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatWeeklyHeat(value: Int): String = when {
    value >= 10_000 -> "%.1f万".format(value / 10_000f)
    else -> value.toString()
}

@Composable
private fun HomeActions(
    onForum: () -> Unit,
    onGuestbook: () -> Unit,
    onWaterCooler: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        HomeShortcut(stringResource(R.string.forum), Icons.Filled.Forum, Modifier.weight(1f), onForum)
        HomeShortcut(stringResource(R.string.guestbook), Icons.Filled.Forum, Modifier.weight(1f), onGuestbook)
        HomeShortcut(stringResource(R.string.home_water_cooler), null, Modifier.weight(1f), onWaterCooler)
    }
}

@Composable
private fun HomeShortcut(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(64.dp).semantics { contentDescription = label },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            else Text("水", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(AppSpacing.sm))
            Text(label, style = MaterialTheme.typography.titleSmall)
        }
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
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp),
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
            modifier = Modifier.padding(top = AppSpacing.md, bottom = AppSpacing.zero),
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

private fun novelKey(novel: CoveredNovel): String =
    novel.url.trim().ifBlank { novel.name.trim() }

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
    private var randomLoadJob: Job? = null
    private var randomRequester: PageableRequester<CoveredNovel>? = null
    private var randomAdultMode: Boolean? = null
    private val randomVisitedPages = mutableSetOf<Int>()
    private val randomSeenNovelKeys = mutableSetOf<String>()
    private val _randomRecommendations = MutableStateFlow(RandomRecommendationsState())
    val randomRecommendations = _randomRecommendations.asStateFlow()

    sealed class State {
        data object Loading : State()
        data class Error(val failure: LoadFailureKind) : State()
        data class Result(
            val homeData: HomeData,
            val isSyncing: Boolean = false
        ) : State()
    }

    data class RandomRecommendationsState(
        val items: List<CoveredNovel> = emptyList(),
        val isLoading: Boolean = false,
        val failure: LoadFailureKind? = null,
        val hasMore: Boolean = true
    )

    fun onRandomAdultModeChanged(adult: Boolean) {
        val previousMode = randomAdultMode
        randomAdultMode = adult
        if (previousMode != null && previousMode != adult) {
            replaceRandomRecommendations(adult)
        }
    }

    fun replaceRandomRecommendations(adult: Boolean) {
        randomAdultMode = adult
        loadRandomRecommendations(adult = adult, replace = true)
    }

    fun loadMoreRandomRecommendations(adult: Boolean) {
        loadRandomRecommendations(adult = adult, replace = false)
    }

    private fun loadRandomRecommendations(adult: Boolean, replace: Boolean) {
        val activeJob = randomLoadJob
        if (!replace && activeJob?.isActive == true) return
        if (!replace && !_randomRecommendations.value.hasMore) return

        randomLoadJob = viewModelScope.launch(Dispatchers.IO) {
            if (replace) activeJob?.cancelAndJoin()
            val previous = _randomRecommendations.value
            _randomRecommendations.value = previous.copy(isLoading = true, failure = null)
            try {
                val requester = randomRequester ?: PresentationAccess.client
                    .novels(authorization, novelType = 0, sortType = 1)
                    .first
                    .also { randomRequester = it }
                if (replace && randomVisitedPages.size >= requester.pages()) {
                    randomVisitedPages.clear()
                    randomSeenNovelKeys.clear()
                }
                val collected = ArrayList<CoveredNovel>(RANDOM_RECOMMENDATION_BATCH_SIZE)
                var requestedPages = 0

                while (
                    collected.size < RANDOM_RECOMMENDATION_BATCH_SIZE &&
                    requestedPages < RANDOM_RECOMMENDATION_MAX_PAGES_PER_BATCH &&
                    randomVisitedPages.size < requester.pages()
                ) {
                    ensureActive()
                    val page = chooseUnvisitedRandomPage(requester.pages()) ?: break
                    randomVisitedPages += page
                    requestedPages += 1
                    requester.more(page)
                        .filter { adult || !it.isAdult }
                        .shuffled()
                        .forEach { novel ->
                            if (
                                collected.size < RANDOM_RECOMMENDATION_BATCH_SIZE &&
                                randomSeenNovelKeys.add(novelKey(novel))
                            ) {
                                collected += novel
                            }
                        }
                }

                ensureActive()
                val items = if (replace) collected else previous.items + collected
                _randomRecommendations.value = RandomRecommendationsState(
                    items = items,
                    hasMore = randomVisitedPages.size < requester.pages()
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _randomRecommendations.value = previous.copy(
                    isLoading = false,
                    failure = error.loadFailureKind()
                )
                com.breakyuna.esjzone.util.AppLogger.e(
                    "HomeTabModel",
                    "Failed to load random recommendations",
                    error
                )
            }
        }
    }

    private fun chooseUnvisitedRandomPage(pageCount: Int): Int? {
        if (pageCount <= 0 || randomVisitedPages.size >= pageCount) return null
        repeat(12) {
            val candidate = Random.nextInt(1, pageCount + 1)
            if (candidate !in randomVisitedPages) return candidate
        }
        return (1..pageCount).firstOrNull { it !in randomVisitedPages }
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
