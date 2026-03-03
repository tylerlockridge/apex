package com.healthplatform.sync.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class BiometricLockManager(private val context: Context) {

    fun isEnabled(): Boolean = SecurePrefs.getBiometricEnabled(context)

    /** Returns true if strong biometrics are enrolled and ready to use. */
    fun canAuthenticate(): Boolean {
        val result = BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Enable or disable biometric lock.
     *
     * Returns false (and does NOT persist the change) when trying to enable
     * biometrics while none are enrolled — the caller should surface an error
     * instead of silently locking the user out.
     */
    fun setEnabled(enabled: Boolean): Boolean {
        if (enabled && !canAuthenticate()) return false
        SecurePrefs.setBiometricEnabled(context, enabled)
        return true
    }

    fun authenticate(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(context)

        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errString.toString())
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // Called when a presented biometric does not match — do not close prompt
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Apex Health")
            .setSubtitle("Authenticate to access your health data")
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}
