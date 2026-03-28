package com.healthplatform.sync.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HydrationViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var app: Application

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        app = ApplicationProvider.getApplicationContext()
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(app, config)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has zero total`() {
        val vm = HydrationViewModel(app)
        Thread.sleep(1000)
        val state = vm.state.value
        assertTrue(state.entries.isEmpty())
        assertEquals(0, state.totalMl)
    }

    @Test
    fun `quick add creates entry`() {
        val vm = HydrationViewModel(app)
        Thread.sleep(1000)
        vm.quickAdd(250)
        // Wait for IO dispatched work
        Thread.sleep(2000)
        val state = vm.state.value
        // The entry is created in local cache even though server sync fails in test
        // However, the loadAll() refresh may fail because server is unreachable,
        // and the ViewModel re-reads local state which should include the entry.
        assertTrue("Expected totalMl >= 250, got ${state.totalMl}", state.totalMl >= 250)
    }
}
