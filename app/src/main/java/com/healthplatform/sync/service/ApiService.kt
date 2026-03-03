package com.healthplatform.sync.service

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
    suspend fun syncData(@Body request: SyncRequest): Response<SyncResponse>
}

data class SyncRequest(
    val device_secret: String,
    val data_type: String,
    val records: List<Any>
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

class ApiService(baseUrl: String, private val deviceSecret: String, private val apiKey: String) {

    private val api: HealthPlatformApi

    init {
        // Pin ISRG Root X1 and Root X2 — the two Let's Encrypt root CAs.
        // Pinning roots (not intermediates) gives multi-year stability without rotation risk.
        // To verify: openssl s_client -connect tyler-health.duckdns.org:443 | openssl x509 -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | base64
        val certificatePinner = CertificatePinner.Builder()
            .add("tyler-health.duckdns.org", "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=") // ISRG Root X1
            .add("tyler-health.duckdns.org", "sha256/diGVwiVYbubAI3RW4hB9xU8e/CH2GnkuvXFu2z8LMAs=") // ISRG Root X2
            .build()

        val client = OkHttpClient.Builder()
            // 1. Auth header
            .addInterceptor(Interceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $apiKey")
                    .build()
                chain.proceed(request)
            })
            // 2. HMAC-SHA256 request signing
            //    Header: X-Signature: sha256=<lowercase hex>
            //    Key: device_secret (shared secret injected at build time)
            //    Covers: the serialised JSON request body
            .addInterceptor(Interceptor { chain ->
                val original = chain.request()
                val body = original.body
                val signed = if (body != null) {
                    val buffer = Buffer()
                    body.writeTo(buffer)
                    val bodyBytes = buffer.readByteArray()

                    val mac = Mac.getInstance("HmacSHA256")
                    mac.init(SecretKeySpec(deviceSecret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
                    val hex = mac.doFinal(bodyBytes).joinToString("") { "%02x".format(it) }

                    original.newBuilder()
                        .addHeader("X-Signature", "sha256=$hex")
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

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        api = retrofit.create(HealthPlatformApi::class.java)
    }

    suspend fun syncBloodPressure(records: List<BloodPressureData>): Result<SyncResponse> {
        return try {
            val request = SyncRequest(
                device_secret = deviceSecret,
                data_type = "blood_pressure",
                records = records
            )
            val response = api.syncData(request)
            if (response.isSuccessful) {
                Result.success(response.body() ?: throw Exception("Empty sync response body"))
            } else {
                Result.failure(Exception("Sync failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncSleep(records: List<SleepData>): Result<SyncResponse> {
        return try {
            val request = SyncRequest(
                device_secret = deviceSecret,
                data_type = "sleep",
                records = records
            )
            val response = api.syncData(request)
            if (response.isSuccessful) {
                Result.success(response.body() ?: throw Exception("Empty sync response body"))
            } else {
                Result.failure(Exception("Sync failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncBodyMeasurements(records: List<BodyMeasurementData>): Result<SyncResponse> {
        return try {
            val request = SyncRequest(
                device_secret = deviceSecret,
                data_type = "body_measurements",
                records = records
            )
            val response = api.syncData(request)
            if (response.isSuccessful) {
                Result.success(response.body() ?: throw Exception("Empty sync response body"))
            } else {
                Result.failure(Exception("Sync failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncHrv(records: List<HrvData>): Result<SyncResponse> {
        return try {
            val syncRecords = records.map { hrv ->
                HrvSyncRecord(
                    measured_at = hrv.measuredAt,
                    hrv_ms = hrv.hrvMs,
                    device_name = hrv.deviceName
                )
            }
            val request = SyncRequest(
                device_secret = deviceSecret,
                data_type = "hrv",
                records = syncRecords
            )
            val response = api.syncData(request)
            if (response.isSuccessful) {
                Result.success(response.body() ?: throw Exception("Empty sync response body"))
            } else {
                Result.failure(Exception("Sync failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
