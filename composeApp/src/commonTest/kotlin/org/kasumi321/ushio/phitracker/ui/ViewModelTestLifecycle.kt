package org.kasumi321.ushio.phitracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal class ViewModelTestLifecycle {
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
                cancelAndJoin()
            }
        } finally {
            Dispatchers.resetMain()
        }
    }
}
