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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertContains
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ViewModelTestLifecycleTest {
    @Test
    fun tearDownFailsWithinBoundWhenTrackedScopeCannotFinishCleanup() {
        val dispatcher = StandardTestDispatcher()
        val lifecycle = ViewModelTestLifecycle(cleanupTimeout = 100.milliseconds)
        val defaultStarted = CompletableDeferred<Unit>()
        val allowCleanupToFinish = CompletableDeferred<Unit>()
        Dispatchers.setMain(dispatcher)
        val viewModel = lifecycle.track(object : ViewModel() {
            fun startBlockedCleanup() {
                viewModelScope.launch {
                    withContext(Dispatchers.Default) {
                        defaultStarted.complete(Unit)
                        try {
                            awaitCancellation()
                        } finally {
                            withContext(NonCancellable) {
                                allowCleanupToFinish.await()
                            }
                        }
                    }
                }
            }
        })

        runTest(dispatcher) {
            viewModel.startBlockedCleanup()
            runCurrent()
            defaultStarted.await()
        }

        val failure = assertFailsWith<ViewModelScopeCleanupTimeoutException> {
            lifecycle.tearDown(dispatcher)
        }
        assertContains(failure.message.orEmpty(), "Tracked ViewModel scope cleanup")

        Dispatchers.setMain(dispatcher)
        allowCleanupToFinish.complete(Unit)
        runTest(dispatcher) {
            lifecycle.cancelAndJoin()
        }
        Dispatchers.resetMain()
    }

    @Test
    fun runTestSurfacesFailureFromTrackedViewModelChild() {
        val dispatcher = StandardTestDispatcher()
        val lifecycle = ViewModelTestLifecycle()
        val expectedFailure = IllegalStateException("tracked ViewModel child failed")
        Dispatchers.setMain(dispatcher)
        val viewModel = lifecycle.track(object : ViewModel() {
            fun failFromChild() {
                viewModelScope.launch {
                    throw expectedFailure
                }
            }
        })

        try {
            val actualFailure = assertFailsWith<IllegalStateException> {
                runTest(dispatcher) {
                    viewModel.failFromChild()
                    runCurrent()
                }
            }

            assertSame(expectedFailure, actualFailure)
        } finally {
            lifecycle.tearDown(dispatcher)
        }
    }

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
