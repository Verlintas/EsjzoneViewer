package com.breakyuna.esjzone.ui.page

import androidx.lifecycle.viewModelScope
import com.breakyuna.esjzone.app.PresentationAccess

import com.breakyuna.esjzone.ui.navigation.AppStateViewModel
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.network.features.HistoryDataCache
import com.breakyuna.esjzone.network.features.getHistories
import com.breakyuna.esjzone.network.loadFailureKind
import com.breakyuna.esjzone.novellibrary.novel.HistoryNovel
import com.breakyuna.esjzone.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Cloud history loader isolated from the tab and its paging presentation. */
class HistoryPageModel(
    private val authorization: Authorization
) : AppStateViewModel<HistoryPageModel.State>(
    HistoryDataCache.readSnapshot()?.let { State.Result(it, isSyncSuccess = false) } ?: State.Loading
) {

    private var loadJob: Job? = null
    private var loadStarted = false

    sealed class State {
        data object Loading : State()
        data class Error(val failure: LoadFailureKind) : State()
        data class Result(
            val historyNovels: List<HistoryNovel>,
            val isSyncing: Boolean = false,
            val isSyncSuccess: Boolean = false,
            val lastSyncFailure: LoadFailureKind? = null
        ) : State()
    }

    fun getNovels(forceRefresh: Boolean = false) {
        if (loadStarted) return
        loadStarted = true
        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            val visibleData = mutableState.value as? State.Result
            mutableState.value = visibleData?.copy(isSyncing = true, lastSyncFailure = null) ?: State.Loading
            try {
                val histories = PresentationAccess.client.getHistories(
                    authorization,
                    forceRefresh = forceRefresh
                )
                ensureActive()
                HistoryDataCache.writeSnapshot(histories)
                mutableState.value = State.Result(
                    historyNovels = histories,
                    isSyncing = false,
                    isSyncSuccess = true,
                    lastSyncFailure = null
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (visibleData == null) {
                    mutableState.value = State.Error(e.loadFailureKind())
                } else {
                    mutableState.value = visibleData.copy(
                        isSyncing = false,
                        isSyncSuccess = false,
                        lastSyncFailure = e.loadFailureKind()
                    )
                }
                loadStarted = false
                AppLogger.e("HistoryPageModel", "Failed to load cloud histories", e)
            }
        }
    }

    fun reload() {
        loadStarted = false
        getNovels(forceRefresh = true)
    }

}
