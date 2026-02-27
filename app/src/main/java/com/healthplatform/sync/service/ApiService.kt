package com.healthplatform.sync.service

import com.healthplatform.sync.data.BloodPressureData
import com.healthplatform.sync.data.BodyMeasurementData
import com.healthplatform.sync.data.SleepData
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

interface HealthPlatformApi {
    @POST("api/sync/health-connect")
    suspend fun syncData(@Body request: SyncRequest): Response<SyncResponse>
}

data class SyncRequest(
    val device_secret: String,
    val data_type: String,
    val records: List<Any>
)

data class SyncResponse(
    val success: Boolean,
    val synced: Int,
    val sync_id: String?
)

class ApiService(baseUrl: String, private val deviceSecret: String, private val apiKey: String) {

    private val api: HealthPlatformApi

    init {
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $apiKey")
                    .build()
                chain.proceed(request)
            })
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
                Result.success(response.body()!!)
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
                Result.success(response.body()!!)
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
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Sync failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
