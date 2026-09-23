package com.breakyuna.esjzone

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface StartupState {
    data object Starting : StartupState
    data object Ready : StartupState
    data class Failed(val reason: String) : StartupState
}

/** Coordinates startup work without depending on Android lifecycle or UI frame scheduling. */
internal class StartupCoordinator(
    private val mapFailure: (Exception) -> String,
    private val onFailure: (Exception) -> Unit,
) {
    private val initMutex = Mutex()
    private val mutableState = MutableStateFlow<StartupState>(StartupState.Starting)

    val state: StateFlow<StartupState> = mutableState.asStateFlow()

    fun retry() {
        mutableState.value = StartupState.Starting
    }

    suspend fun initializeOnce(initialize: suspend () -> Unit) {
        initMutex.withLock {
            if (mutableState.value is StartupState.Ready) return@withLock

            try {
                initialize()
                mutableState.value = StartupState.Ready
            } catch (error: Exception) {
                onFailure(error)
                mutableState.value = StartupState.Failed(mapFailure(error))
            }
        }
    }
}
