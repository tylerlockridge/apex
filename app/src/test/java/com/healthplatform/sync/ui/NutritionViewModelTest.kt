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
class NutritionViewModelTest {

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
    fun `initial state has empty entries`() {
        val vm = NutritionViewModel(app)
        Thread.sleep(1000)
        val state = vm.state.value
        assertTrue(state.entries.isEmpty())
        assertEquals(0.0, state.totalCalories, 0.01)
    }

    @Test
    fun `show and hide add entry sheet`() {
        val vm = NutritionViewModel(app)
        Thread.sleep(500)
        assertFalse(vm.state.value.showAddEntrySheet)
        vm.showAddEntry()
        Thread.sleep(500)
        assertTrue(vm.state.value.showAddEntrySheet)
        vm.hideAddEntry()
        assertFalse(vm.state.value.showAddEntrySheet)
    }

    @Test
    fun `show and hide create food dialog`() {
        val vm = NutritionViewModel(app)
        Thread.sleep(500)
        assertFalse(vm.state.value.showCreateFoodDialog)
        vm.showCreateFoodDialog()
        assertTrue(vm.state.value.showCreateFoodDialog)
        vm.hideCreateFoodDialog()
        assertFalse(vm.state.value.showCreateFoodDialog)
    }
}
