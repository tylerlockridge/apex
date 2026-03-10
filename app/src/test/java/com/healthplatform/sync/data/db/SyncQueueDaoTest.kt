package com.healthplatform.sync.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncQueueDaoTest {

    private lateinit var db: ApexDatabase
    private lateinit var dao: SyncQueueDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ApexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.syncQueueDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `insertAll adds records`() = runTest {
        val records = listOf(
            SyncQueueEntity(dataType = "blood_pressure", measuredAt = "2026-03-01T08:00:00Z", payload = """{"systolic":120}"""),
            SyncQueueEntity(dataType = "sleep", measuredAt = "2026-03-01T23:00:00Z", payload = """{"durationMinutes":480}"""),
        )
        dao.insertAll(records)

        assertEquals(2, dao.pendingCount())
    }

    @Test
    fun `duplicate dataType+measuredAt ignored`() = runTest {
        val record = SyncQueueEntity(dataType = "blood_pressure", measuredAt = "2026-03-01T08:00:00Z", payload = """{"systolic":120}""")
        dao.insertAll(listOf(record))
        dao.insertAll(listOf(record.copy(payload = """{"systolic":999}""")))

        val pending = dao.getPending("blood_pressure")
        assertEquals(1, pending.size)
        // Original payload preserved (IGNORE means second insert is dropped)
        assertEquals("""{"systolic":120}""", pending[0].payload)
    }

    @Test
    fun `getPending returns sorted ASC by measuredAt`() = runTest {
        val records = listOf(
            SyncQueueEntity(dataType = "blood_pressure", measuredAt = "2026-03-03T08:00:00Z", payload = "{}"),
            SyncQueueEntity(dataType = "blood_pressure", measuredAt = "2026-03-01T08:00:00Z", payload = "{}"),
            SyncQueueEntity(dataType = "blood_pressure", measuredAt = "2026-03-02T08:00:00Z", payload = "{}"),
        )
        dao.insertAll(records)

        val pending = dao.getPending("blood_pressure")
        assertEquals(3, pending.size)
        assertEquals("2026-03-01T08:00:00Z", pending[0].measuredAt)
        assertEquals("2026-03-02T08:00:00Z", pending[1].measuredAt)
        assertEquals("2026-03-03T08:00:00Z", pending[2].measuredAt)
    }

    @Test
    fun `delete removes only specified records`() = runTest {
        val records = listOf(
            SyncQueueEntity(dataType = "blood_pressure", measuredAt = "2026-03-01T08:00:00Z", payload = "{}"),
            SyncQueueEntity(dataType = "blood_pressure", measuredAt = "2026-03-02T08:00:00Z", payload = "{}"),
            SyncQueueEntity(dataType = "sleep", measuredAt = "2026-03-01T23:00:00Z", payload = "{}"),
        )
        dao.insertAll(records)

        val all = dao.getPending("blood_pressure")
        assertEquals(2, all.size)

        // Delete only the first BP record
        dao.delete(listOf(all[0]))

        val remaining = dao.getPending("blood_pressure")
        assertEquals(1, remaining.size)
        assertEquals("2026-03-02T08:00:00Z", remaining[0].measuredAt)

        // Sleep record untouched
        assertEquals(1, dao.getPending("sleep").size)
    }

    @Test
    fun `pendingCount returns total across all types`() = runTest {
        val records = listOf(
            SyncQueueEntity(dataType = "blood_pressure", measuredAt = "2026-03-01T08:00:00Z", payload = "{}"),
            SyncQueueEntity(dataType = "sleep", measuredAt = "2026-03-01T23:00:00Z", payload = "{}"),
            SyncQueueEntity(dataType = "hrv", measuredAt = "2026-03-01T06:00:00Z", payload = "{}"),
        )
        dao.insertAll(records)

        assertEquals(3, dao.pendingCount())
    }
}
