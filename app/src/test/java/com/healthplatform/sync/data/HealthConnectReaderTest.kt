package com.healthplatform.sync.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.response.ReadRecordsResponse
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class HealthConnectReaderTest {

    private lateinit var mockHcClient: HealthConnectClient
    private lateinit var reader: HealthConnectReader

    @Before
    fun setUp() {
        mockHcClient = mockk()
        mockkStatic(HealthConnectClient.Companion::class)
        every { HealthConnectClient.getOrCreate(any()) } returns mockHcClient

        val context = ApplicationProvider.getApplicationContext<Context>()
        reader = HealthConnectReader(context)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // -------------------------------------------------------------------------
    // readBloodPressure pagination
    // -------------------------------------------------------------------------

    @Test
    fun `readBloodPressure 2 pages concatenated`() = runTest {
        val page1Records = listOf(makeBpRecord(120, 80, "2026-03-01T08:00:00Z"))
        val page2Records = listOf(makeBpRecord(130, 85, "2026-03-02T08:00:00Z"))

        coEvery { mockHcClient.readRecords(any<ReadRecordsRequest<BloodPressureRecord>>()) } returnsMany listOf(
            mockResponse(page1Records, "page2"),
            mockResponse(page2Records, null),
        )

        val result = reader.readBloodPressure(Instant.parse("2026-03-01T00:00:00Z"))

        assertEquals(2, result.size)
        assertEquals(120, result[0].systolic)
        assertEquals(130, result[1].systolic)
    }

    @Test
    fun `readBloodPressure empty first page returns empty`() = runTest {
        coEvery { mockHcClient.readRecords(any<ReadRecordsRequest<BloodPressureRecord>>()) } returns
            mockResponse(emptyList<BloodPressureRecord>(), null)

        val result = reader.readBloodPressure(Instant.parse("2026-03-01T00:00:00Z"))

        assertTrue(result.isEmpty())
    }

    @Test
    fun `readBloodPressure null pageToken single page`() = runTest {
        val records = listOf(
            makeBpRecord(118, 76, "2026-03-01T08:00:00Z"),
            makeBpRecord(122, 80, "2026-03-01T12:00:00Z"),
        )
        coEvery { mockHcClient.readRecords(any<ReadRecordsRequest<BloodPressureRecord>>()) } returns
            mockResponse(records, null)

        val result = reader.readBloodPressure(Instant.parse("2026-03-01T00:00:00Z"))

        assertEquals(2, result.size)
        assertEquals(118, result[0].systolic)
        assertEquals(76, result[0].diastolic)
        assertEquals(122, result[1].systolic)
    }

    // -------------------------------------------------------------------------
    // readWeight pagination (3 pages)
    // -------------------------------------------------------------------------

    @Test
    fun `readWeight 3 pages concatenated`() = runTest {
        coEvery { mockHcClient.readRecords(any<ReadRecordsRequest<WeightRecord>>()) } returnsMany listOf(
            mockResponse(listOf(makeWeightRecord(80.0, "2026-03-01T07:00:00Z")), "p2"),
            mockResponse(listOf(makeWeightRecord(80.5, "2026-03-02T07:00:00Z")), "p3"),
            mockResponse(listOf(makeWeightRecord(81.0, "2026-03-03T07:00:00Z")), null),
        )
        coEvery { mockHcClient.readRecords(any<ReadRecordsRequest<BodyFatRecord>>()) } returns
            mockResponse(emptyList<BodyFatRecord>(), null)
        coEvery { mockHcClient.readRecords(any<ReadRecordsRequest<LeanBodyMassRecord>>()) } returns
            mockResponse(emptyList<LeanBodyMassRecord>(), null)

        val result = reader.readWeight(Instant.parse("2026-03-01T00:00:00Z"))

        assertEquals(3, result.size)
        assertEquals(80.0, result[0].weightKg!!, 0.01)
        assertEquals(80.5, result[1].weightKg!!, 0.01)
        assertEquals(81.0, result[2].weightKg!!, 0.01)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    internal fun makeBpRecord(systolic: Int, diastolic: Int, time: String, packageName: String = "com.test"): BloodPressureRecord {
        val systolicPressure = mockk<androidx.health.connect.client.units.Pressure>()
        every { systolicPressure.inMillimetersOfMercury } returns systolic.toDouble()
        val diastolicPressure = mockk<androidx.health.connect.client.units.Pressure>()
        every { diastolicPressure.inMillimetersOfMercury } returns diastolic.toDouble()

        val metadata = mockk<Metadata>()
        val device = mockk<Device>()
        every { device.manufacturer } returns "TestDevice"
        every { metadata.device } returns device
        val dataOrigin = mockk<DataOrigin>()
        every { dataOrigin.packageName } returns packageName
        every { metadata.dataOrigin } returns dataOrigin

        val record = mockk<BloodPressureRecord>()
        every { record.systolic } returns systolicPressure
        every { record.diastolic } returns diastolicPressure
        every { record.time } returns Instant.parse(time)
        every { record.metadata } returns metadata
        return record
    }

    internal fun makeWeightRecord(kg: Double, time: String): WeightRecord {
        val mass = mockk<androidx.health.connect.client.units.Mass>()
        every { mass.inKilograms } returns kg

        val metadata = mockk<Metadata>()
        val device = mockk<Device>()
        every { device.manufacturer } returns "TestDevice"
        every { metadata.device } returns device
        val dataOrigin = mockk<DataOrigin>()
        every { dataOrigin.packageName } returns "com.test"
        every { metadata.dataOrigin } returns dataOrigin

        val record = mockk<WeightRecord>()
        every { record.weight } returns mass
        every { record.time } returns Instant.parse(time)
        every { record.metadata } returns metadata
        return record
    }

    @Suppress("UNCHECKED_CAST")
    internal fun <T : Any> mockResponse(records: List<T>, pageToken: String?): ReadRecordsResponse<T> {
        val response = mockk<ReadRecordsResponse<T>>()
        every { response.records } returns records
        every { response.pageToken } returns pageToken
        return response
    }
}
