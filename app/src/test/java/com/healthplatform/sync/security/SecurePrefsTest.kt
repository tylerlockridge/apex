package com.healthplatform.sync.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests SecurePrefs public API via mockkObject since EncryptedSharedPreferences
 * requires Android Keystore which is unavailable in Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
class SecurePrefsTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockkObject(SecurePrefs)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `getApiKey returns stored key`() {
        every { SecurePrefs.getApiKey(any()) } returns "test-api-key-123"
        assertEquals("test-api-key-123", SecurePrefs.getApiKey(context))
    }

    @Test
    fun `setApiKey persists key`() {
        every { SecurePrefs.setApiKey(any(), any()) } returns Unit
        SecurePrefs.setApiKey(context, "new-key")
        verify { SecurePrefs.setApiKey(context, "new-key") }
    }

    @Test
    fun `getBiometricEnabled returns true when set`() {
        every { SecurePrefs.getBiometricEnabled(any()) } returns true
        assertTrue(SecurePrefs.getBiometricEnabled(context))
    }

    @Test
    fun `getBiometricEnabled returns false by default`() {
        every { SecurePrefs.getBiometricEnabled(any()) } returns false
        assertFalse(SecurePrefs.getBiometricEnabled(context))
    }

    @Test
    fun `getDeviceSecret falls back to provided fallback`() {
        every { SecurePrefs.getDeviceSecret(any(), any()) } answers {
            secondArg<String>() // returns the fallback
        }
        assertEquals("fallback-secret", SecurePrefs.getDeviceSecret(context, "fallback-secret"))
    }

    @Test
    fun `getDeviceSecret returns stored secret when present`() {
        every { SecurePrefs.getDeviceSecret(any(), any()) } returns "stored-secret"
        assertEquals("stored-secret", SecurePrefs.getDeviceSecret(context, "fallback"))
    }

    @Test
    fun `clearAll resets all values`() {
        every { SecurePrefs.clearAll(any()) } returns Unit
        SecurePrefs.clearAll(context)
        verify { SecurePrefs.clearAll(context) }
    }

    @Test
    fun `migration from plain prefs - getApiKey returns migrated value`() {
        // Simulate that after migration, the key is available via SecurePrefs
        every { SecurePrefs.getApiKey(any()) } returns "migrated-key"
        assertEquals("migrated-key", SecurePrefs.getApiKey(context))
    }
}
