package com.breakyuna.esjzone.ui.tab

import androidx.lifecycle.viewModelScope
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.network.PageableRequester
import com.breakyuna.esjzone.network.features.HomeDataCache
import com.breakyuna.esjzone.network.features.getHomeData
import com.breakyuna.esjzone.network.features.novels
import com.breakyuna.esjzone.network.loadFailureKind
import com.breakyuna.esjzone.novellibrary.data.HomeData
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovel
import com.breakyuna.esjzone.ui.navigation.AppStateViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

internal const val RANDOM_RECOMMENDATION_BATCH_SIZE = 24
internal const val RANDOM_RECOMMENDATION_MAX_PAGES_PER_BATCH = 3

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
        val hasMore: Boolean = true,
        val isActivated: Boolean = false
    )

    fun onRandomAdultModeChanged(adult: Boolean) {
        val previousMode = randomAdultMode
        randomAdultMode = adult
        if (previousMode != null && previousMode != adult && _randomRecommendations.value.isActivated) {
            replaceRandomRecommendations(adult)
        }
    }

    fun activateRandomRecommendations(adult: Boolean) {
        if (_randomRecommendations.value.isActivated && _randomRecommendations.value.items.isNotEmpty()) return
        randomAdultMode = adult
        loadRandomRecommendations(adult = adult, replace = true, activate = true)
    }

    fun replaceRandomRecommendations(adult: Boolean) {
        randomAdultMode = adult
        loadRandomRecommendations(adult = adult, replace = true, activate = true)
    }

    fun retryRandomRecommendations(adult: Boolean) {
        randomAdultMode = adult
        loadRandomRecommendations(adult = adult, replace = false, activate = true)
    }

    private fun loadRandomRecommendations(adult: Boolean, replace: Boolean, activate: Boolean) {
        val activeJob = randomLoadJob
        if (!replace && activeJob?.isActive == true) return

        randomLoadJob = viewModelScope.launch(Dispatchers.IO) {
            if (replace) activeJob?.cancelAndJoin()
            val previous = _randomRecommendations.value
            _randomRecommendations.value = previous.copy(
                isLoading = true,
                failure = null,
                isActivated = activate || previous.isActivated
            )
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
                    isLoading = false,
                    failure = null,
                    hasMore = randomVisitedPages.size < requester.pages(),
                    isActivated = true
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _randomRecommendations.value = previous.copy(
                    isLoading = false,
                    failure = error.loadFailureKind(),
                    isActivated = activate || previous.isActivated
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
