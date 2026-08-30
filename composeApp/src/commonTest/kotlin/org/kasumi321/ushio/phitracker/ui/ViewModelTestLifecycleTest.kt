package org.kasumi321.ushio.phitracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ViewModelTestLifecycleTest {
    @Test
    fun tearDownWaitsForDefaultChildBeforeResettingMain() {
        val dispatcher = StandardTestDispatcher()
        val lifecycle = ViewModelTestLifecycle()
        val defaultStarted = CompletableDeferred<Unit>()
        val cancellationObserved = CompletableDeferred<Unit>()
        val allowCleanupToFinish = CompletableDeferred<Unit>()
        val defaultFinished = CompletableDeferred<Unit>()
        Dispatchers.setMain(dispatcher)
        val viewModel = lifecycle.track(object : ViewModel() {
            fun startDefaultWork() {
                viewModelScope.launch {
                    withContext(Dispatchers.Default) {
                        defaultStarted.complete(Unit)
                        try {
                            awaitCancellation()
                        } finally {
                            withContext(NonCancellable) {
                                cancellationObserved.complete(Unit)
                                allowCleanupToFinish.await()
                                defaultFinished.complete(Unit)
                            }
                        }
                    }
                }
            }
        })

        runTest(dispatcher) {
            viewModel.startDefaultWork()
            runCurrent()
            defaultStarted.await()
        }

        runTest(dispatcher) {
            val teardown = backgroundScope.launch {
                lifecycle.cancelAndJoin()
            }
            runCurrent()
            cancellationObserved.await()
            assertFalse(teardown.isCompleted)
            allowCleanupToFinish.complete(Unit)
            teardown.join()
        }
        Dispatchers.resetMain()

        assertTrue(defaultFinished.isCompleted)
    }
}
