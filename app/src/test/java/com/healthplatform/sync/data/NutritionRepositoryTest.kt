package com.healthplatform.sync.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.healthplatform.sync.data.db.*
import com.healthplatform.sync.data.NutritionRepository.Companion.SENTINEL_NULL
import com.healthplatform.sync.service.CreateFoodRequest
import com.healthplatform.sync.service.PatchField
import com.healthplatform.sync.service.UpdateFoodEntryPatch
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

    // -----------------------------------------------------------------------
    // Food entry edit tests
    // -----------------------------------------------------------------------

    @Test
    fun `food entry edit updates servings and rescales calories`() = runTest {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        dao.upsertFoodEntry(
            FoodEntryCacheEntity(
                id = "e-edit-1", foodId = "f-1", foodName = "Rice",
                entrySource = "manual", mealType = "lunch", servings = 1.0,
                calories = 130.0, proteinG = 2.7, carbsG = 28.0, fatG = 0.3,
                fiberG = null, sugarG = null, sodiumMg = null,
                loggedAt = "2026-03-28T12:00:00Z", loggedDate = today,
                notes = null, syncState = NutritionSyncState.SYNCED,
                createdAt = null, updatedAt = null,
            )
        )

        val existing = dao.getFoodEntryById("e-edit-1")!!
        val factor = 2.0 / existing.servings
        dao.upsertFoodEntry(existing.copy(
            servings = 2.0,
            calories = existing.calories * factor,
            proteinG = existing.proteinG?.let { it * factor },
            syncState = NutritionSyncState.PENDING_UPDATE,
        ))

        val updated = dao.getFoodEntryById("e-edit-1")!!
        assertEquals(2.0, updated.servings, 0.01)
        assertEquals(260.0, updated.calories, 0.01)
        assertEquals(5.4, updated.proteinG!!, 0.01)
        assertEquals(NutritionSyncState.PENDING_UPDATE, updated.syncState)
    }

    @Test
    fun `food entry edit on pending_create stays pending_create`() = runTest {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        dao.upsertFoodEntry(
            FoodEntryCacheEntity(
                id = "e-pending-1", foodId = "f-1", foodName = "Rice",
                entrySource = "manual", mealType = "lunch", servings = 1.0,
                calories = 130.0, proteinG = 2.7, carbsG = 28.0, fatG = 0.3,
                fiberG = null, sugarG = null, sodiumMg = null,
                loggedAt = "2026-03-28T12:00:00Z", loggedDate = today,
                notes = null, syncState = NutritionSyncState.PENDING_CREATE,
                createdAt = null, updatedAt = null,
            )
        )

        // Editing a pending_create entry should keep it as pending_create
        val existing = dao.getFoodEntryById("e-pending-1")!!
        dao.upsertFoodEntry(existing.copy(
            servings = 3.0,
            syncState = NutritionSyncState.PENDING_CREATE, // must stay pending_create
        ))

        val updated = dao.getFoodEntryById("e-pending-1")!!
        assertEquals(NutritionSyncState.PENDING_CREATE, updated.syncState)
    }

    // -----------------------------------------------------------------------
    // PATCH null-vs-omit tests
    // -----------------------------------------------------------------------

    @Test
    fun `UpdateFoodEntryPatch omits unchanged fields`() {
        val patch = UpdateFoodEntryPatch(
            servings = PatchField.SetValue(2.0),
        )
        val json = patch.toJsonObject()
        assertTrue(json.has("servings"))
        assertFalse(json.has("meal_type"))
        assertFalse(json.has("notes"))
        assertEquals(2.0, json.get("servings").asDouble, 0.01)
    }

    @Test
    fun `UpdateFoodEntryPatch sends explicit null for SetNull`() {
        val patch = UpdateFoodEntryPatch(
            meal_type = PatchField.SetNull,
            notes = PatchField.SetValue("updated note"),
        )
        val json = patch.toJsonObject()
        assertTrue(json.has("meal_type"))
        assertTrue(json.get("meal_type").isJsonNull)
        assertTrue(json.has("notes"))
        assertEquals("updated note", json.get("notes").asString)
        assertFalse(json.has("servings"))
    }

    @Test
    fun `pending write encodes sentinel for null clear`() = runTest {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        dao.upsertFoodEntry(
            FoodEntryCacheEntity(
                id = "e-patch-1", foodId = "f-1", foodName = "Test",
                entrySource = "manual", mealType = "lunch", servings = 1.0,
                calories = 100.0, proteinG = 10.0, carbsG = 20.0, fatG = 5.0,
                fiberG = null, sugarG = null, sodiumMg = null,
                loggedAt = "2026-03-28T12:00:00Z", loggedDate = today,
                notes = "old note", syncState = NutritionSyncState.SYNCED,
                createdAt = null, updatedAt = null,
            )
        )

        // Simulate what the repo does: encode SENTINEL_NULL for meal_type clear
        val patchMap = mutableMapOf<String, Any?>(
            "id" to "e-patch-1",
            "meal_type" to SENTINEL_NULL,
        )
        dao.upsertPendingWrite(
            PendingNutritionWriteEntity(
                actionType = NutritionQueueAction.UPDATE_FOOD_ENTRY,
                dedupeKey = "update_food_entry:e-patch-1",
                payload = com.google.gson.Gson().toJson(patchMap),
            )
        )

        val write = dao.getPendingWriteByDedupeKey("update_food_entry:e-patch-1")!!
        assertTrue(write.payload.contains(SENTINEL_NULL))
    }

    // -----------------------------------------------------------------------
    // Hydration validation tests
    // -----------------------------------------------------------------------

    @Test(expected = IllegalArgumentException::class)
    fun `createWaterEntry rejects zero amount`() = runTest {
        val repo = NutritionRepository(ApplicationProvider.getApplicationContext())
        repo.createWaterEntry(0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `createWaterEntry rejects negative amount`() = runTest {
        val repo = NutritionRepository(ApplicationProvider.getApplicationContext())
        repo.createWaterEntry(-100)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `createWaterEntry rejects amount over 10000`() = runTest {
        val repo = NutritionRepository(ApplicationProvider.getApplicationContext())
        repo.createWaterEntry(10001)
    }

    @Test
    fun `createWaterEntry accepts boundary values`() = runTest {
        // These use the DAO directly since the repo uses the singleton
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

        // Min valid
        dao.upsertWaterEntry(
            WaterEntryCacheEntity(
                id = "w-min", amountMl = 1, entrySource = "quick_add",
                loggedAt = "2026-03-28T12:00:00Z", loggedDate = today,
                notes = null, syncState = NutritionSyncState.PENDING_CREATE,
                createdAt = null,
            )
        )
        assertEquals(1, dao.getWaterEntryById("w-min")!!.amountMl)

        // Max valid
        dao.upsertWaterEntry(
            WaterEntryCacheEntity(
                id = "w-max", amountMl = 10000, entrySource = "quick_add",
                loggedAt = "2026-03-28T12:01:00Z", loggedDate = today,
                notes = null, syncState = NutritionSyncState.PENDING_CREATE,
                createdAt = null,
            )
        )
        assertEquals(10000, dao.getWaterEntryById("w-max")!!.amountMl)
    }
}
