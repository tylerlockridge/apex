package com.healthplatform.sync.ui

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.healthplatform.sync.SyncPrefsKeys
import com.healthplatform.sync.data.HealthConnectReader
import com.healthplatform.sync.security.SecurePrefs
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var app: Application
    private lateinit var mockServer: MockWebServer

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        app = ApplicationProvider.getApplicationContext()

        mockkObject(SecurePrefs)
        every { SecurePrefs.getApiKey(any()) } returns "test-key"

        mockkObject(HealthConnectReader.Companion)
        every { HealthConnectReader.isAvailable(any()) } returns false

        mockServer = MockWebServer()
        mockServer.start()

        val serverUrl = mockServer.url("/").toString().removeSuffix("/")
        app.getSharedPreferences(SyncPrefsKeys.FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(SyncPrefsKeys.SERVER_URL, serverUrl)
            .commit()
    }

    @After
    fun tearDown() {
        mockServer.shutdown()
        app.getSharedPreferences(SyncPrefsKeys.FILE_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        Dispatchers.resetMain()
        unmockkAll()
    }

    /**
     * Drains both the IO dispatcher (real threads hitting MockWebServer) and the
     * test dispatcher. The IO work completes quickly against localhost but we need
     * to give the continuation time to post back to the test scheduler.
     */
    private fun drainAll() {
        // checkAll() runs HC check → server check → version check sequentially.
        // Each check uses withContext(Dispatchers.IO) so we need multiple drain passes.
        repeat(3) {
            Thread.sleep(500)
            testDispatcher.scheduler.advanceUntilIdle()
        }
    }

    @Test
    fun `server 200 sets connected true`() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"version":"1.0.0"}"""))

        val vm = SettingsViewModel(app)
        drainAll()

        assertEquals(true, vm.serverState.value.serverStatus)
    }

    @Test
    fun `server error sets connected false`() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(500))
        mockServer.enqueue(MockResponse().setResponseCode(500))

        val vm = SettingsViewModel(app)
        drainAll()

        assertEquals(false, vm.serverState.value.serverStatus)
    }

    @Test
    fun `version match sets serverVersionOk true`() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"version":"1.0.0"}"""))

        val vm = SettingsViewModel(app)
        drainAll()

        assertEquals(true, vm.serverState.value.serverVersionOk)
    }

    @Test
    fun `older server sets serverVersionOk false`() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"version":"0.9.0"}"""))

        val vm = SettingsViewModel(app)
        drainAll()

        assertEquals(false, vm.serverState.value.serverVersionOk)
    }

    @Test
    fun `newer server sets serverVersionOk true`() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"version":"2.1.0"}"""))

        val vm = SettingsViewModel(app)
        drainAll()

        assertEquals(true, vm.serverState.value.serverVersionOk)
    }
}
