package com.healthplatform.sync.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import com.healthplatform.sync.data.db.ApexDatabase
import com.healthplatform.sync.data.db.FoodEntryCacheEntity
import com.healthplatform.sync.data.db.NutritionSyncState
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
        ApexDatabase.resetForTesting()
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(app, config)
    }

    @After
    fun tearDown() {
        ApexDatabase.resetForTesting()
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

    @Test
    fun `start and cancel edit entry`() {
        val vm = NutritionViewModel(app)
        Thread.sleep(500)
        assertNull(vm.state.value.editingEntry)

        val mockEntry = FoodEntryCacheEntity(
            id = "test-entry", foodId = "f-1", foodName = "Test Food",
            entrySource = "manual", mealType = "lunch", servings = 1.0,
            calories = 200.0, proteinG = 20.0, carbsG = 30.0, fatG = 10.0,
            fiberG = null, sugarG = null, sodiumMg = null,
            loggedAt = "2026-03-28T12:00:00Z", loggedDate = "2026-03-28",
            notes = null, syncState = NutritionSyncState.SYNCED,
            createdAt = null, updatedAt = null,
        )

        vm.startEditEntry(mockEntry)
        assertNotNull(vm.state.value.editingEntry)
        assertEquals("Test Food", vm.state.value.editingEntry?.foodName)

        vm.cancelEditEntry()
        assertNull(vm.state.value.editingEntry)
    }
}
