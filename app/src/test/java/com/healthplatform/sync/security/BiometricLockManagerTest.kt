package com.healthplatform.sync.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BiometricLockManagerTest {

    private lateinit var context: Context
    private lateinit var manager: BiometricLockManager
    private lateinit var mockBiometricManager: BiometricManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockkObject(SecurePrefs)
        mockBiometricManager = io.mockk.mockk()
        mockkStatic(BiometricManager::class)
        every { BiometricManager.from(any()) } returns mockBiometricManager
        manager = BiometricLockManager(context)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `isEnabled mirrors SecurePrefs biometricEnabled true`() {
        every { SecurePrefs.getBiometricEnabled(any()) } returns true
        assertTrue(manager.isEnabled())
    }

    @Test
    fun `isEnabled mirrors SecurePrefs biometricEnabled false`() {
        every { SecurePrefs.getBiometricEnabled(any()) } returns false
        assertFalse(manager.isEnabled())
    }

    @Test
    fun `canAuthenticate returns true when BIOMETRIC_SUCCESS`() {
        every {
            mockBiometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        } returns BiometricManager.BIOMETRIC_SUCCESS

        assertTrue(manager.canAuthenticate())
    }

    @Test
    fun `canAuthenticate returns false when no biometrics enrolled`() {
        every {
            mockBiometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        } returns BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED

        assertFalse(manager.canAuthenticate())
    }

    @Test
    fun `setEnabled guard rejects enable when no biometrics available`() {
        every {
            mockBiometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        } returns BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE

        val result = manager.setEnabled(true)

        assertFalse(result)
        verify(exactly = 0) { SecurePrefs.setBiometricEnabled(any(), any()) }
    }

    @Test
    fun `setEnabled persists true when biometrics available`() {
        every {
            mockBiometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        } returns BiometricManager.BIOMETRIC_SUCCESS
        every { SecurePrefs.setBiometricEnabled(any(), any()) } returns Unit

        val result = manager.setEnabled(true)

        assertTrue(result)
        verify { SecurePrefs.setBiometricEnabled(context, true) }
    }
}
