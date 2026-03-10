package com.healthplatform.sync.service

import com.google.gson.Gson
import com.healthplatform.sync.data.BloodPressureData
import com.healthplatform.sync.data.BodyMeasurementData
import com.healthplatform.sync.data.HrvData
import com.healthplatform.sync.data.SleepData
import okhttp3.CertificatePinner
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

interface HealthPlatformApi {
    @POST("api/sync/health-connect")
    suspend fun syncData(@Body request: SyncRequest<@JvmSuppressWildcards Any>): Response<SyncResponse>
}

/**
 * Wire format for a Health Connect sync request.
 *
 * [T] is covariant so that SyncRequest<BloodPressureData> can be passed wherever
 * SyncRequest<Any> is expected without an unchecked cast. Gson serialises using the
 * actual runtime type, so type erasure does not affect the on-wire payload.
 *
 * device_secret is intentionally absent — the HMAC signature in X-Signature proves
 * possession of the shared secret without transmitting it in the body.
 */
data class SyncRequest<out T>(
    val data_type: String,
    val records: List<T>
)

/** Wire format for HRV records — maps from [HrvData] to the server's expected field names. */
data class HrvSyncRecord(
    val measured_at: String,
    val hrv_ms: Double,
    val resting_hr: Int? = null,
    val reading_type: String? = "sleep",
    val device_name: String? = null
)

data class SyncResponse(
    val success: Boolean,
    val synced: Int,
    val sync_id: String?
)

/**
 * Retrofit-based API client for the Health Platform server.
 *
 * The underlying [OkHttpClient] is created once (singleton via [get]) so the
 * thread pool and connection pool are shared across all WorkManager runs. Only
 * the API key is refreshed on each [get] call to reflect the latest stored value.
 */
class ApiService private constructor(
    baseUrl: String,
    deviceSecret: String,
    initialApiKey: String,
) {
    /** Updated on each [get] call — read by the auth interceptor at request time. */
    @Volatile private var _apiKey: String = initialApiKey

    private val api: HealthPlatformApi

    init {
        // Pin ISRG Root X1 and Root X2 — the two Let's Encrypt root CAs.
        // Pinning roots (not intermediates) gives multi-year stability without rotation risk.
        val certificatePinner = CertificatePinner.Builder()
            .add("tyler-health.duckdns.org", "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=") // ISRG Root X1
            .add("tyler-health.duckdns.org", "sha256/diGVwiVYbubAI3RW4hB9xU8e/CH2GnkuvXFu2z8LMAs=") // ISRG Root X2
            .build()

        val client = OkHttpClient.Builder()
            // 1. Auth header — reads _apiKey at request time so key refreshes are picked up
            //    without rebuilding the client.
            .addInterceptor(Interceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer ${this._apiKey}")
                        .build()
                )
            })
            // 2. HMAC-SHA256 signing with replay protection.
            //    X-Timestamp: Unix seconds (server rejects if > 5 min old).
            //    X-Signature: sha256=HMAC(secret, "${timestamp}\n${body}").
            //    Including the timestamp in the HMAC input binds the signature to
            //    this specific request moment; a replayed body+sig with a new
            //    timestamp would fail to verify.
            .addInterceptor(Interceptor { chain ->
                val original = chain.request()
                val body = original.body
                val signed = if (body != null) {
                    val buffer = Buffer()
                    body.writeTo(buffer)
                    val bodyBytes = buffer.readByteArray()
                    val timestamp = (System.currentTimeMillis() / 1000L).toString()

                    val mac = Mac.getInstance("HmacSHA256")
                    mac.init(SecretKeySpec(deviceSecret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
                    mac.update("$timestamp\n".toByteArray(Charsets.UTF_8))
                    mac.update(bodyBytes)
                    val hex = mac.doFinal().joinToString("") { "%02x".format(it) }

                    original.newBuilder()
                        .addHeader("X-Signature", "sha256=$hex")
                        .addHeader("X-Timestamp", timestamp)
                        .method(original.method, bodyBytes.toRequestBody(body.contentType()))
                        .build()
                } else {
                    original
                }
                chain.proceed(signed)
            })
            .certificatePinner(certificatePinner)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        api = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(HealthPlatformApi::class.java)
    }

    // -------------------------------------------------------------------------
    // Public sync methods
    // -------------------------------------------------------------------------

    suspend fun syncBloodPressure(records: List<BloodPressureData>): Result<SyncResponse> =
        sync(SyncRequest(data_type = "blood_pressure", records = records))

    suspend fun syncSleep(records: List<SleepData>): Result<SyncResponse> =
        sync(SyncRequest(data_type = "sleep", records = records))

    suspend fun syncBodyMeasurements(records: List<BodyMeasurementData>): Result<SyncResponse> =
        sync(SyncRequest(data_type = "body_measurements", records = records))

    suspend fun syncHrv(records: List<HrvData>): Result<SyncResponse> =
        sync(SyncRequest(
            data_type = "hrv",
            records = records.map { hrv ->
                HrvSyncRecord(
                    measured_at = hrv.measuredAt,
                    hrv_ms = hrv.hrvMs,
                    device_name = hrv.deviceName
                )
            }
        ))

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private suspend fun <T : Any> sync(request: SyncRequest<T>): Result<SyncResponse> = try {
        val response = api.syncData(request)
        if (response.isSuccessful) {
            val body = response.body() ?: throw Exception("Empty sync response body")
            // H-2: server can return 200 with success=false (e.g. partial rejection).
            // Treat as transient so WorkManager retries rather than silently dropping records.
            if (!body.success) {
                Result.failure(Exception("Server rejected sync (success=false, synced=${body.synced})"))
            } else {
                Result.success(body)
            }
        } else {
            // 4xx (except 429) are permanent — bad auth, malformed payload, server rejected.
            // 5xx and 429 are transient — server error or rate limit, safe to retry.
            val e = when (response.code()) {
                400, 401, 403, 404, 422 -> PermanentSyncFailure("Sync rejected: HTTP ${response.code()}")
                else -> Exception("Sync failed: ${response.code()}")
            }
            Result.failure(e)
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    // -------------------------------------------------------------------------
    // Singleton factory
    // -------------------------------------------------------------------------

    companion object {
        /**
         * Shared Gson instance — thread-safe and expensive to construct.
         * Used by both [ApiService] and [SyncWorker] serialisation.
         */
        val gson = Gson()

        @Volatile private var instance: ApiService? = null
        @Volatile private var currentBaseUrl: String? = null
        @Volatile private var currentSecret: String? = null

        /**
         * Returns the singleton [ApiService], creating it on first call.
         *
         * The underlying [OkHttpClient] (with cert pinning, HMAC interceptor, and
         * connection pool) is created once and reused. The [apiKey] is refreshed on
         * every call so WorkManager runs always use the current key.
         *
         * If [baseUrl] or [deviceSecret] changed (e.g. after a QR code scan), the
         * instance is rebuilt so the new URL and HMAC secret take effect immediately.
         */
        fun get(baseUrl: String, deviceSecret: String, apiKey: String): ApiService =
            synchronized(this) {
                if (instance == null || currentBaseUrl != baseUrl || currentSecret != deviceSecret) {
                    instance = ApiService(baseUrl, deviceSecret, apiKey)
                    currentBaseUrl = baseUrl
                    currentSecret = deviceSecret
                }
                instance!!.also { it._apiKey = apiKey }
            }

        /** Creates a fresh (non-singleton) instance for unit testing only. */
        internal fun createForTest(baseUrl: String, deviceSecret: String, apiKey: String) =
            ApiService(baseUrl, deviceSecret, apiKey)
    }
}

/**
 * Thrown when the server returns a non-retryable HTTP error (400/401/403/404/422).
 * [SyncWorker] maps this to [Result.failure] so WorkManager stops retrying.
 */
class PermanentSyncFailure(message: String) : Exception(message)
