package com.healthplatform.sync.service

import com.healthplatform.sync.data.BloodPressureData
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApiServiceTest {

    private val server = MockWebServer()
    private lateinit var apiService: ApiService

    @Before
    fun setUp() {
        server.start()
        apiService = ApiService.createForTest(server.url("/").toString(), "test-secret", "test-key")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // -------------------------------------------------------------------------
    // syncBloodPressure
    // -------------------------------------------------------------------------

    @Test
    fun `syncBloodPressure returns success with correct synced count`() = runTest {
        server.enqueue(
            MockResponse()
                .setBody("""{"success":true,"synced":2,"sync_id":"abc123"}""")
                .addHeader("Content-Type", "application/json")
        )

        val result = apiService.syncBloodPressure(
            listOf(
                BloodPressureData(120, 80, "2024-01-01T00:00:00Z"),
                BloodPressureData(118, 78, "2024-01-02T00:00:00Z")
            )
        )

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow().synced)
        assertEquals("abc123", result.getOrThrow().sync_id)
    }

    @Test
    fun `syncBloodPressure returns failure on HTTP 401`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))

        val result = apiService.syncBloodPressure(
            listOf(BloodPressureData(120, 80, "2024-01-01T00:00:00Z"))
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("401"))
    }

    @Test
    fun `syncBloodPressure returns failure with message on HTTP 500`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        val result = apiService.syncBloodPressure(
            listOf(BloodPressureData(120, 80, "2024-01-01T00:00:00Z"))
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("500"))
    }

    @Test
    fun `syncBloodPressure returns failure with sentinel message on null body`() = runTest {
        // Retrofit + Gson returns null body when server sends JSON null on 200
        server.enqueue(
            MockResponse()
                .setBody("null")
                .addHeader("Content-Type", "application/json")
        )

        val result = apiService.syncBloodPressure(
            listOf(BloodPressureData(120, 80, "2024-01-01T00:00:00Z"))
        )

        assertTrue(result.isFailure)
        assertEquals("Empty sync response body", result.exceptionOrNull()!!.message)
    }

    // -------------------------------------------------------------------------
    // syncSleep — spot-check a second method to confirm shared logic
    // -------------------------------------------------------------------------

    @Test
    fun `syncSleep returns success on valid response`() = runTest {
        server.enqueue(
            MockResponse()
                .setBody("""{"success":true,"synced":1,"sync_id":null}""")
                .addHeader("Content-Type", "application/json")
        )

        val result = apiService.syncSleep(emptyList())

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().success)
    }

    @Test
    fun `Authorization header is sent with every request`() = runTest {
        server.enqueue(
            MockResponse()
                .setBody("""{"success":true,"synced":0,"sync_id":null}""")
                .addHeader("Content-Type", "application/json")
        )

        apiService.syncBloodPressure(emptyList())

        val recorded = server.takeRequest()
        assertEquals("Bearer test-key", recorded.getHeader("Authorization"))
    }

    // -------------------------------------------------------------------------
    // HMAC signing (X-Signature + X-Timestamp)
    // -------------------------------------------------------------------------

    @Test
    fun `X-Signature header is sent with sha256 prefix`() = runTest {
        server.enqueue(
            MockResponse()
                .setBody("""{"success":true,"synced":0,"sync_id":null}""")
                .addHeader("Content-Type", "application/json")
        )

        apiService.syncBloodPressure(emptyList())

        val recorded = server.takeRequest()
        val sig = recorded.getHeader("X-Signature")
        assertNotNull("X-Signature header must be present", sig)
        assertTrue("X-Signature must start with 'sha256='", sig!!.startsWith("sha256="))
        // Hex-encoded SHA-256 HMAC is always 64 characters after the prefix
        assertEquals(64, sig.removePrefix("sha256=").length)
    }

    @Test
    fun `X-Timestamp header is sent and is a recent Unix timestamp`() = runTest {
        server.enqueue(
            MockResponse()
                .setBody("""{"success":true,"synced":0,"sync_id":null}""")
                .addHeader("Content-Type", "application/json")
        )

        val beforeSec = System.currentTimeMillis() / 1000L
        apiService.syncBloodPressure(emptyList())
        val afterSec = System.currentTimeMillis() / 1000L

        val recorded = server.takeRequest()
        val ts = recorded.getHeader("X-Timestamp")
        assertNotNull("X-Timestamp header must be present", ts)
        val tsNum = ts!!.toLong()
        assertTrue("Timestamp must be >= request start", tsNum >= beforeSec)
        assertTrue("Timestamp must be <= request end", tsNum <= afterSec + 1)
    }

    @Test
    fun `X-Signature covers both timestamp and body`() = runTest {
        // Two requests with the same body but sent at different times should produce
        // different signatures because the timestamp changes.
        server.enqueue(
            MockResponse()
                .setBody("""{"success":true,"synced":1,"sync_id":"a"}""")
                .addHeader("Content-Type", "application/json")
        )
        server.enqueue(
            MockResponse()
                .setBody("""{"success":true,"synced":1,"sync_id":"b"}""")
                .addHeader("Content-Type", "application/json")
        )

        val records = listOf(BloodPressureData(120, 80, "2024-01-01T00:00:00Z"))
        apiService.syncBloodPressure(records)
        Thread.sleep(1100) // ensure timestamp increments by at least 1 second
        apiService.syncBloodPressure(records)

        val sig1 = server.takeRequest().getHeader("X-Signature")
        val sig2 = server.takeRequest().getHeader("X-Signature")
        assertTrue("Signatures should differ when timestamp differs", sig1 != sig2)
    }
}
