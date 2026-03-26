package com.healthplatform.sync.service

import com.google.gson.Gson
import com.healthplatform.sync.Config
import com.healthplatform.sync.data.health.BloodPressureData
import com.healthplatform.sync.data.health.BodyMeasurementData
import com.healthplatform.sync.data.health.HrvData
import com.healthplatform.sync.data.health.SleepData
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

/** Wire format for blood pressure records — maps from [BloodPressureData] to server field names. */
data class BpSyncRecord(
    val systolic: Int,
    val diastolic: Int,
    val measured_at: String,
    val pulse: Int? = null,
    val context: String? = null,
    val device_name: String? = null
)

/** Wire format for sleep records — maps from [SleepData] to server field names. */
data class SleepSyncRecord(
    val sleep_start: String,
    val sleep_end: String,
    val duration_minutes: Int? = null,
    val deep_sleep_minutes: Int? = null,
    val rem_sleep_minutes: Int? = null,
    val light_sleep_minutes: Int? = null,
    val sleep_score: Int? = null,
    val device_name: String? = null
)

/** Wire format for body records — maps from [BodyMeasurementData] to server field names. */
data class BodySyncRecord(
    val measured_at: String,
    val weight_kg: Double,
    val body_fat_percent: Double? = null,
    val muscle_mass_kg: Double? = null,
    val device_name: String? = null
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
        // GPT 5.4 A-07: Derive pinned host from runtime baseUrl so QR-configured
        // custom servers are pinned correctly (previously hardcoded tyler-health.duckdns.org).
        val host = Config.extractHost(baseUrl)

        val certificatePinner = CertificatePinner.Builder()
            .add(host, Config.PIN_LETS_ENCRYPT_E8)
            .add(host, Config.PIN_ISRG_ROOT_X1)
            .add(host, Config.PIN_ISRG_ROOT_X2)
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
        sync(SyncRequest(
            data_type = "blood_pressure",
            records = records.map { bp ->
                BpSyncRecord(
                    systolic = bp.systolic,
                    diastolic = bp.diastolic,
                    measured_at = bp.measuredAt,
                    pulse = bp.pulse,
                    context = bp.context,
                    device_name = bp.deviceName
                )
            }
        ))

    suspend fun syncSleep(records: List<SleepData>): Result<SyncResponse> =
        sync(SyncRequest(
            data_type = "sleep",
            records = records.map { sleep ->
                SleepSyncRecord(
                    sleep_start = sleep.sleepStart,
                    sleep_end = sleep.sleepEnd,
                    duration_minutes = sleep.durationMinutes,
                    deep_sleep_minutes = sleep.deepSleepMinutes,
                    rem_sleep_minutes = sleep.remSleepMinutes,
                    light_sleep_minutes = sleep.lightSleepMinutes,
                    sleep_score = sleep.sleepScore,
                    device_name = sleep.deviceName
                )
            }
        ))

    suspend fun syncBodyMeasurements(records: List<BodyMeasurementData>): Result<SyncResponse> {
        val bodyRecords = records.mapNotNull { body ->
            body.weightKg?.let { weightKg ->
                BodySyncRecord(
                    measured_at = body.measuredAt,
                    weight_kg = weightKg,
                    body_fat_percent = body.bodyFatPercent,
                    muscle_mass_kg = body.muscleMassKg,
                    device_name = body.deviceName
                )
            }
        }

        return if (bodyRecords.isEmpty()) {
            Result.success(SyncResponse(success = true, synced = 0, sync_id = null))
        } else {
            sync(SyncRequest(data_type = "body_measurements", records = bodyRecords))
        }
    }

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
