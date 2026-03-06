package com.healthplatform.sync.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.SleepSessionRecord
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
import kotlin.reflect.KClass
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        val context = ApplicationProvider.getApplicationContext<Context>()
        reader = HealthConnectReader(context, client = mockHcClient)
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
        stubReadRecords(WeightRecord::class,
            mockResponse(listOf(makeWeightRecord(80.0, "2026-03-01T07:00:00Z")), "p2"),
            mockResponse(listOf(makeWeightRecord(80.5, "2026-03-02T07:00:00Z")), "p3"),
            mockResponse(listOf(makeWeightRecord(81.0, "2026-03-03T07:00:00Z")), null),
        )
        stubReadRecords(BodyFatRecord::class, mockResponse(emptyList<BodyFatRecord>(), null))
        stubReadRecords(LeanBodyMassRecord::class, mockResponse(emptyList<LeanBodyMassRecord>(), null))

        val result = reader.readWeight(Instant.parse("2026-03-01T00:00:00Z"))

        assertEquals(3, result.size)
        assertEquals(80.0, result[0].weightKg!!, 0.01)
        assertEquals(80.5, result[1].weightKg!!, 0.01)
        assertEquals(81.0, result[2].weightKg!!, 0.01)
    }

    // -------------------------------------------------------------------------
    // US-002: Oura Ring filtering (readSleep)
    // -------------------------------------------------------------------------

    @Test
    fun `readSleep mixed Oura and Pixel filters to Oura only`() = runTest {
        val ouraRecord = makeSleepRecord("2026-03-01T23:00:00Z", "2026-03-02T07:00:00Z", "com.ouraring.oura")
        val pixelRecord = makeSleepRecord("2026-03-01T22:00:00Z", "2026-03-02T06:00:00Z", "com.google.android.apps.fitness")

        coEvery { mockHcClient.readRecords(any<ReadRecordsRequest<SleepSessionRecord>>()) } returns
            mockResponse(listOf(ouraRecord, pixelRecord), null)

        val result = reader.readSleep(Instant.parse("2026-03-01T00:00:00Z"))

        assertEquals(1, result.size)
        assertEquals("2026-03-01T23:00:00Z", result[0].sleepStart)
    }

    @Test
    fun `readSleep no Oura returns all records`() = runTest {
        val pixelRecord = makeSleepRecord("2026-03-01T22:00:00Z", "2026-03-02T06:00:00Z", "com.google.android.apps.fitness")

        coEvery { mockHcClient.readRecords(any<ReadRecordsRequest<SleepSessionRecord>>()) } returns
            mockResponse(listOf(pixelRecord), null)

        val result = reader.readSleep(Instant.parse("2026-03-01T00:00:00Z"))

        assertEquals(1, result.size)
    }

    // -------------------------------------------------------------------------
    // US-002: Oura Ring filtering (readHeartRateVariability)
    // -------------------------------------------------------------------------

    @Test
    fun `readHRV prefers Oura source`() = runTest {
        val ouraHrv = makeHrvRecord(55.3, "2026-03-01T06:00:00Z", "com.ouraring.oura")
        val pixelHrv = makeHrvRecord(48.0, "2026-03-01T06:00:00Z", "com.google.android.apps.fitness")

        coEvery { mockHcClient.readRecords(any<ReadRecordsRequest<HeartRateVariabilityRmssdRecord>>()) } returns
            mockResponse(listOf(ouraHrv, pixelHrv), null)

        val result = reader.readHeartRateVariability(Instant.parse("2026-03-01T00:00:00Z"))

        assertEquals(1, result.size)
        assertEquals(55.3, result[0].hrvMs, 0.01)
    }

    @Test
    fun `readHRV no Oura returns all`() = runTest {
        val pixelHrv = makeHrvRecord(48.0, "2026-03-01T06:00:00Z", "com.google.android.apps.fitness")

        coEvery { mockHcClient.readRecords(any<ReadRecordsRequest<HeartRateVariabilityRmssdRecord>>()) } returns
            mockResponse(listOf(pixelHrv), null)

        val result = reader.readHeartRateVariability(Instant.parse("2026-03-01T00:00:00Z"))

        assertEquals(1, result.size)
        assertEquals(48.0, result[0].hrvMs, 0.01)
    }

    // -------------------------------------------------------------------------
    // US-003: Body match window boundary tests
    // -------------------------------------------------------------------------

    @Test
    fun `readWeight body match at 0s offset matches`() = runTest {
        val weightTime = "2026-03-01T07:00:00Z"
        stubReadRecords(WeightRecord::class, mockResponse(listOf(makeWeightRecord(80.0, weightTime)), null))
        stubReadRecords(BodyFatRecord::class, mockResponse(listOf(makeBodyFatRecord(20.0, weightTime)), null))
        stubReadRecords(LeanBodyMassRecord::class, mockResponse(emptyList<LeanBodyMassRecord>(), null))

        val result = reader.readWeight(Instant.parse("2026-03-01T00:00:00Z"))

        assertEquals(1, result.size)
        assertEquals(20.0, result[0].bodyFatPercent!!, 0.01)
    }

    @Test
    fun `readWeight body match at 3600s offset matches`() = runTest {
        stubReadRecords(WeightRecord::class, mockResponse(listOf(makeWeightRecord(80.0, "2026-03-01T07:00:00Z")), null))
        stubReadRecords(BodyFatRecord::class, mockResponse(listOf(makeBodyFatRecord(18.5, "2026-03-01T08:00:00Z")), null))
        stubReadRecords(LeanBodyMassRecord::class, mockResponse(emptyList<LeanBodyMassRecord>(), null))

        val result = reader.readWeight(Instant.parse("2026-03-01T00:00:00Z"))

        assertEquals(1, result.size)
        assertEquals(18.5, result[0].bodyFatPercent!!, 0.01)
    }

    @Test
    fun `readWeight body match at 3601s offset NOT matched`() = runTest {
        stubReadRecords(WeightRecord::class, mockResponse(listOf(makeWeightRecord(80.0, "2026-03-01T07:00:00Z")), null))
        stubReadRecords(BodyFatRecord::class, mockResponse(listOf(makeBodyFatRecord(18.5, "2026-03-01T08:00:01Z")), null))
        stubReadRecords(LeanBodyMassRecord::class, mockResponse(emptyList<LeanBodyMassRecord>(), null))

        val result = reader.readWeight(Instant.parse("2026-03-01T00:00:00Z"))

        assertEquals(1, result.size)
        assertNull(result[0].bodyFatPercent)
    }

    @Test
    fun `readWeight no body comp returns null fields`() = runTest {
        stubReadRecords(WeightRecord::class, mockResponse(listOf(makeWeightRecord(80.0, "2026-03-01T07:00:00Z")), null))
        stubReadRecords(BodyFatRecord::class, mockResponse(emptyList<BodyFatRecord>(), null))
        stubReadRecords(LeanBodyMassRecord::class, mockResponse(emptyList<LeanBodyMassRecord>(), null))

        val result = reader.readWeight(Instant.parse("2026-03-01T00:00:00Z"))

        assertEquals(1, result.size)
        assertNull(result[0].bodyFatPercent)
        assertNull(result[0].muscleMassKg)
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

    private fun makeSleepRecord(startTime: String, endTime: String, packageName: String): SleepSessionRecord {
        val metadata = mockk<Metadata>()
        val device = mockk<Device>()
        every { device.manufacturer } returns "TestDevice"
        every { metadata.device } returns device
        val dataOrigin = mockk<DataOrigin>()
        every { dataOrigin.packageName } returns packageName
        every { metadata.dataOrigin } returns dataOrigin

        val record = mockk<SleepSessionRecord>()
        every { record.startTime } returns Instant.parse(startTime)
        every { record.endTime } returns Instant.parse(endTime)
        every { record.metadata } returns metadata
        every { record.stages } returns emptyList()
        return record
    }

    private fun makeHrvRecord(hrvMs: Double, time: String, packageName: String): HeartRateVariabilityRmssdRecord {
        val metadata = mockk<Metadata>()
        val device = mockk<Device>()
        every { device.manufacturer } returns "TestDevice"
        every { metadata.device } returns device
        val dataOrigin = mockk<DataOrigin>()
        every { dataOrigin.packageName } returns packageName
        every { metadata.dataOrigin } returns dataOrigin

        val record = mockk<HeartRateVariabilityRmssdRecord>()
        every { record.heartRateVariabilityMillis } returns hrvMs
        every { record.time } returns Instant.parse(time)
        every { record.metadata } returns metadata
        return record
    }

    private fun makeBodyFatRecord(percent: Double, time: String): BodyFatRecord {
        val percentage = mockk<androidx.health.connect.client.units.Percentage>()
        every { percentage.value } returns percent

        val metadata = mockk<Metadata>()
        val device = mockk<Device>()
        every { device.manufacturer } returns "TestDevice"
        every { metadata.device } returns device
        val dataOrigin = mockk<DataOrigin>()
        every { dataOrigin.packageName } returns "com.test"
        every { metadata.dataOrigin } returns dataOrigin

        val record = mockk<BodyFatRecord>()
        every { record.percentage } returns percentage
        every { record.time } returns Instant.parse(time)
        every { record.metadata } returns metadata
        return record
    }

    /**
     * Stubs readRecords calls by matching on recordType to avoid type erasure issues.
     * Single response variant.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T : androidx.health.connect.client.records.Record> stubReadRecords(
        type: KClass<T>,
        vararg responses: ReadRecordsResponse<T>,
    ) {
        if (responses.size == 1) {
            coEvery {
                mockHcClient.readRecords(match<ReadRecordsRequest<T>> { it.recordType == type })
            } returns responses[0]
        } else {
            coEvery {
                mockHcClient.readRecords(match<ReadRecordsRequest<T>> { it.recordType == type })
            } returnsMany responses.toList()
        }
    }

    @Suppress("UNCHECKED_CAST")
    internal fun <T : androidx.health.connect.client.records.Record> mockResponse(records: List<T>, pageToken: String?): ReadRecordsResponse<T> {
        val response = mockk<ReadRecordsResponse<T>>()
        every { response.records } returns records
        every { response.pageToken } returns pageToken
        return response
    }
}
