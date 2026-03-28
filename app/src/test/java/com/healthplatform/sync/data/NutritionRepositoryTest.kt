package com.healthplatform.sync.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.healthplatform.sync.data.db.*
import com.healthplatform.sync.service.CreateFoodRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NutritionRepositoryTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var db: ApexDatabase
    private lateinit var dao: NutritionCacheDao

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ApexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.nutritionCacheDao()
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `searchFoods returns matching results`() = runTest {
        dao.upsertFood(
            FoodCacheEntity(
                id = "f-1", name = "Banana", brand = null, barcode = null,
                servingSizeG = 118.0, calories = 105.0, proteinG = 1.3,
                carbsG = 27.0, fatG = 0.4, fiberG = 3.1, sugarG = 14.0,
                sodiumMg = 1.0, dataSource = "custom", qualityFlag = "user_created",
                isCustom = true, syncState = NutritionSyncState.SYNCED,
                lastUsedAt = System.currentTimeMillis(), createdAt = null, updatedAt = null,
            )
        )
        dao.upsertFood(
            FoodCacheEntity(
                id = "f-2", name = "Apple", brand = null, barcode = null,
                servingSizeG = 182.0, calories = 95.0, proteinG = 0.5,
                carbsG = 25.0, fatG = 0.3, fiberG = 4.4, sugarG = 19.0,
                sodiumMg = 2.0, dataSource = "custom", qualityFlag = "user_created",
                isCustom = true, syncState = NutritionSyncState.SYNCED,
                lastUsedAt = System.currentTimeMillis(), createdAt = null, updatedAt = null,
            )
        )

        val results = dao.searchFoods("ban", 30)
        assertEquals(1, results.size)
        assertEquals("Banana", results[0].name)

        val all = dao.recentFoods(30)
        assertEquals(2, all.size)
    }

    @Test
    fun `food entry CRUD works`() = runTest {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val entry = FoodEntryCacheEntity(
            id = "e-1", foodId = "f-1", foodName = "Rice",
            entrySource = "manual", mealType = "lunch", servings = 2.0,
            calories = 260.0, proteinG = 5.4, carbsG = 56.0, fatG = 0.6,
            fiberG = null, sugarG = null, sodiumMg = null,
            loggedAt = "2026-03-28T12:00:00Z", loggedDate = today,
            notes = null, syncState = NutritionSyncState.PENDING_CREATE,
            createdAt = null, updatedAt = null,
        )
        dao.upsertFoodEntry(entry)

        val entries = dao.getFoodEntriesForDate(today)
        assertEquals(1, entries.size)
        assertEquals("Rice", entries[0].foodName)
        assertEquals(260.0, entries[0].calories, 0.01)

        dao.deleteFoodEntryById("e-1")
        val afterDelete = dao.getFoodEntriesForDate(today)
        assertTrue(afterDelete.isEmpty())
    }

    @Test
    fun `water entry CRUD works`() = runTest {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val entry = WaterEntryCacheEntity(
            id = "w-1", amountMl = 500, entrySource = "quick_add",
            loggedAt = "2026-03-28T12:00:00Z", loggedDate = today,
            notes = null, syncState = NutritionSyncState.PENDING_CREATE,
            createdAt = null,
        )
        dao.upsertWaterEntry(entry)

        val entries = dao.getWaterEntriesForDate(today)
        assertEquals(1, entries.size)
        assertEquals(500, entries[0].amountMl)

        dao.deleteWaterEntryById("w-1")
        assertTrue(dao.getWaterEntriesForDate(today).isEmpty())
    }

    @Test
    fun `nutrition target lookup works by effective date`() = runTest {
        dao.upsertNutritionTarget(
            NutritionTargetCacheEntity(
                effectiveDate = "2026-03-01", serverId = null,
                calories = 2000, proteinG = 150, carbsG = 250, fatG = 70,
                method = "manual", syncState = NutritionSyncState.SYNCED,
                createdAt = null, updatedAt = null,
            )
        )

        val target = dao.getActiveNutritionTarget("2026-03-28")
        assertNotNull(target)
        assertEquals(2000, target!!.calories)

        val noTarget = dao.getActiveNutritionTarget("2026-02-28")
        assertNull(noTarget)
    }

    @Test
    fun `hydration target lookup works`() = runTest {
        dao.upsertHydrationTarget(
            HydrationTargetCacheEntity(
                effectiveDate = "2026-03-01", serverId = null,
                targetMl = 3000, method = "manual",
                syncState = NutritionSyncState.SYNCED,
                createdAt = null, updatedAt = null,
            )
        )
        val target = dao.getActiveHydrationTarget("2026-03-28")
        assertNotNull(target)
        assertEquals(3000, target!!.targetMl)
    }

    @Test
    fun `pending write queue works`() = runTest {
        assertEquals(0, dao.pendingWriteCount())
        dao.upsertPendingWrite(
            PendingNutritionWriteEntity(
                actionType = NutritionQueueAction.CREATE_FOOD,
                dedupeKey = "create_food:test-1",
                payload = "{}",
            )
        )
        assertEquals(1, dao.pendingWriteCount())

        val writes = dao.getPendingWrites()
        assertEquals(NutritionQueueAction.CREATE_FOOD, writes[0].actionType)

        dao.deletePendingWrite(writes[0].id)
        assertEquals(0, dao.pendingWriteCount())
    }

    @Test
    fun `dedupe key prevents duplicate writes`() = runTest {
        dao.upsertPendingWrite(
            PendingNutritionWriteEntity(
                actionType = NutritionQueueAction.CREATE_FOOD,
                dedupeKey = "create_food:test-1",
                payload = "{\"v\":1}",
            )
        )
        dao.upsertPendingWrite(
            PendingNutritionWriteEntity(
                actionType = NutritionQueueAction.CREATE_FOOD,
                dedupeKey = "create_food:test-1",
                payload = "{\"v\":2}",
            )
        )
        assertEquals(1, dao.pendingWriteCount())
        val latest = dao.getPendingWriteByDedupeKey("create_food:test-1")
        assertTrue(latest!!.payload.contains("v\":2"))
    }
}
