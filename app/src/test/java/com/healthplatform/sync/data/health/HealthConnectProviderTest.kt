package com.healthplatform.sync.data.health

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.healthplatform.sync.data.HealthConnectReader
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

/**
 * Verifies that [HealthConnectProvider] delegates every method to [HealthConnectReader].
 * This is the Package 0B adapter test per ADR-005.
 */
@RunWith(RobolectricTestRunner::class)
class HealthConnectProviderTest {

    private lateinit var mockReader: HealthConnectReader
    private lateinit var provider: HealthConnectProvider

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        mockReader = mockk()
        provider = HealthConnectProvider(context, reader = mockReader)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private val since = Instant.parse("2026-03-01T00:00:00Z")

    // -- Full-window reads ------------------------------------------------

    @Test
    fun `readBloodPressure delegates to reader`() = runTest {
        val expected = listOf(BloodPressureData(120, 80, "2026-03-01T08:00:00Z"))
        coEvery { mockReader.readBloodPressure(since) } returns expected

        val result = provider.readBloodPressure(since)

        assertEquals(expected, result)
        coVerify(exactly = 1) { mockReader.readBloodPressure(since) }
    }

    @Test
    fun `readSleep delegates to reader`() = runTest {
        val expected = listOf(SleepData("2026-03-01T23:00:00Z", "2026-03-02T07:00:00Z", 480))
        coEvery { mockReader.readSleep(since) } returns expected

        val result = provider.readSleep(since)

        assertEquals(expected, result)
        coVerify(exactly = 1) { mockReader.readSleep(since) }
    }

    @Test
    fun `readWeight delegates to reader`() = runTest {
        val expected = listOf(BodyMeasurementData("2026-03-01T07:00:00Z", weightKg = 80.0))
        coEvery { mockReader.readWeight(since) } returns expected

        val result = provider.readWeight(since)

        assertEquals(expected, result)
        coVerify(exactly = 1) { mockReader.readWeight(since) }
    }

    @Test
    fun `readHeartRateVariability delegates to reader`() = runTest {
        val expected = listOf(HrvData("2026-03-01T06:00:00Z", 55.0))
        coEvery { mockReader.readHeartRateVariability(since) } returns expected

        val result = provider.readHeartRateVariability(since)

        assertEquals(expected, result)
        coVerify(exactly = 1) { mockReader.readHeartRateVariability(since) }
    }

    // -- Change-token reads -----------------------------------------------

    @Test
    fun `readBloodPressureChanges delegates to reader`() = runTest {
        val expected = listOf(BloodPressureData(130, 85, "2026-03-02T08:00:00Z")) to "new-token"
        coEvery { mockReader.readBloodPressureChanges("old-token") } returns expected

        val result = provider.readBloodPressureChanges("old-token")

        assertEquals(expected, result)
        coVerify(exactly = 1) { mockReader.readBloodPressureChanges("old-token") }
    }

    @Test
    fun `readSleepChanges delegates to reader`() = runTest {
        val expected = emptyList<SleepData>() to "new-token"
        coEvery { mockReader.readSleepChanges("old-token") } returns expected

        val result = provider.readSleepChanges("old-token")

        assertEquals(expected, result)
    }

    @Test
    fun `readHrvChanges delegates to reader`() = runTest {
        val expected = listOf(HrvData("2026-03-01T06:00:00Z", 48.0)) to "new-token"
        coEvery { mockReader.readHrvChanges("old-token") } returns expected

        val result = provider.readHrvChanges("old-token")

        assertEquals(expected, result)
    }

    // -- Change-token acquisition -----------------------------------------

    @Test
    fun `getBpChangesToken delegates to reader`() = runTest {
        coEvery { mockReader.getBpChangesToken() } returns "bp-token-123"

        assertEquals("bp-token-123", provider.getBpChangesToken())
    }

    @Test
    fun `getSleepChangesToken delegates to reader`() = runTest {
        coEvery { mockReader.getSleepChangesToken() } returns "sleep-token-456"

        assertEquals("sleep-token-456", provider.getSleepChangesToken())
    }

    @Test
    fun `getHrvChangesToken delegates to reader`() = runTest {
        coEvery { mockReader.getHrvChangesToken() } returns "hrv-token-789"

        assertEquals("hrv-token-789", provider.getHrvChangesToken())
    }

    // -- Permissions ------------------------------------------------------

    @Test
    fun `checkPermissions delegates to reader`() = runTest {
        val expected = setOf("perm1", "perm2")
        coEvery { mockReader.checkPermissions() } returns expected

        assertEquals(expected, provider.checkPermissions())
    }

    @Test
    fun `hasAllPermissions delegates to reader`() = runTest {
        coEvery { mockReader.hasAllPermissions() } returns true
        assertTrue(provider.hasAllPermissions())

        coEvery { mockReader.hasAllPermissions() } returns false
        assertFalse(provider.hasAllPermissions())
    }

    @Test
    fun `requiredPermissions delegates to reader`() {
        val expected = setOf("req1", "req2")
        every { mockReader.requiredPermissions } returns expected

        assertEquals(expected, provider.requiredPermissions)
    }

    // -- Availability -----------------------------------------------------
    // isAvailable() delegates to HealthConnectReader.isAvailable(context),
    // a companion object function wrapping HealthConnectClient.getSdkStatus().
    // Mocking static/companion methods reliably in Robolectric + MockK is
    // fragile (getSdkStatus signature overloads cause matcher mismatches).
    // The delegation is a one-liner; the other 13 tests confirm the adapter
    // pattern works. Availability is covered by integration/device testing.
}
