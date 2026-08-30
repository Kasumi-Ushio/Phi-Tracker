package org.kasumi321.ushio.phitracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal class ViewModelTestLifecycle(
    private val cleanupTimeout: Duration = 5.seconds
) {
    private val scopeJobs = mutableListOf<Job>()

    fun <T : ViewModel> track(viewModel: T): T = viewModel.also {
        scopeJobs += it.viewModelScope.coroutineContext.job
    }

    suspend fun cancelAndJoin() {
        scopeJobs.forEach { it.cancel() }
        scopeJobs.forEach { it.join() }
        scopeJobs.clear()
    }

    fun tearDown(dispatcher: TestDispatcher) {
        try {
            runTest(dispatcher) {
                try {
                    withContext(Dispatchers.Default) {
                        withTimeout(cleanupTimeout) {
                            cancelAndJoin()
                        }
                    }
                } catch (_: TimeoutCancellationException) {
                    throw ViewModelScopeCleanupTimeoutException(cleanupTimeout)
                }
            }
        } finally {
            Dispatchers.resetMain()
        }
    }
}

internal class ViewModelScopeCleanupTimeoutException(timeout: Duration) : AssertionError(
    "Tracked ViewModel scope cleanup did not complete within $timeout"
)
