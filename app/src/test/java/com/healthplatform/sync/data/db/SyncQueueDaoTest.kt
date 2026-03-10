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

    private fun entity(dataType: String, measuredAt: String, payload: String) =
        SyncQueueEntity(
            dataType = dataType,
            measuredAt = measuredAt,
            payload = payload,
            recordHash = computeRecordHash(dataType, payload)
        )

    @Test
    fun `insertAll adds records`() = runTest {
        val records = listOf(
            entity("blood_pressure", "2026-03-01T08:00:00Z", """{"systolic":120}"""),
            entity("sleep", "2026-03-01T23:00:00Z", """{"durationMinutes":480}"""),
        )
        dao.insertAll(records)

        assertEquals(2, dao.pendingCount())
    }

    @Test
    fun `duplicate payload hash ignored`() = runTest {
        val record = entity("blood_pressure", "2026-03-01T08:00:00Z", """{"systolic":120}""")
        dao.insertAll(listOf(record))
        // Same payload = same hash — second insert is silently ignored
        dao.insertAll(listOf(record))

        val pending = dao.getPending("blood_pressure")
        assertEquals(1, pending.size)
        assertEquals("""{"systolic":120}""", pending[0].payload)
    }

    @Test
    fun `distinct payloads at same timestamp both inserted`() = runTest {
        // Two records with same timestamp but different content (different devices) must both be kept
        val r1 = entity("blood_pressure", "2026-03-01T08:00:00Z", """{"systolic":120,"device":"Pixel"}""")
        val r2 = entity("blood_pressure", "2026-03-01T08:00:00Z", """{"systolic":122,"device":"Omron"}""")
        dao.insertAll(listOf(r1, r2))

        assertEquals(2, dao.getPending("blood_pressure").size)
    }

    @Test
    fun `getPending returns sorted ASC by measuredAt`() = runTest {
        val records = listOf(
            entity("blood_pressure", "2026-03-03T08:00:00Z", """{"systolic":122}"""),
            entity("blood_pressure", "2026-03-01T08:00:00Z", """{"systolic":120}"""),
            entity("blood_pressure", "2026-03-02T08:00:00Z", """{"systolic":121}"""),
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
            entity("blood_pressure", "2026-03-01T08:00:00Z", """{"systolic":120}"""),
            entity("blood_pressure", "2026-03-02T08:00:00Z", """{"systolic":121}"""),
            entity("sleep", "2026-03-01T23:00:00Z", """{"duration":480}"""),
        )
        dao.insertAll(records)

        val all = dao.getPending("blood_pressure")
        assertEquals(2, all.size)

        dao.delete(listOf(all[0]))

        val remaining = dao.getPending("blood_pressure")
        assertEquals(1, remaining.size)
        assertEquals("2026-03-02T08:00:00Z", remaining[0].measuredAt)
        assertEquals(1, dao.getPending("sleep").size)
    }

    @Test
    fun `pendingCount returns total across all types`() = runTest {
        val records = listOf(
            entity("blood_pressure", "2026-03-01T08:00:00Z", """{"systolic":120}"""),
            entity("sleep", "2026-03-01T23:00:00Z", """{"duration":480}"""),
            entity("hrv", "2026-03-01T06:00:00Z", """{"hrv_ms":45.0}"""),
        )
        dao.insertAll(records)

        assertEquals(3, dao.pendingCount())
    }
}
