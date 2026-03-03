package com.healthplatform.sync.service

import com.healthplatform.sync.BuildConfig
import com.healthplatform.sync.Config
import okhttp3.CertificatePinner
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

// ---------------------------------------------------------------------------
// Response data models
// ---------------------------------------------------------------------------

data class BpReadingResponse(
    val id: String?,
    val systolic: Int,
    val diastolic: Int,
    val pulse: Int?,
    val measured_at: String,
    val context: String?,
    val source: String?
)

data class SleepSessionResponse(
    val id: String?,
    val sleep_start: String,
    val sleep_end: String?,
    val duration_minutes: Int,
    val deep_sleep_minutes: Int?,
    val rem_sleep_minutes: Int?,
    val light_sleep_minutes: Int?,
    val sleep_score: Int?,
    val source: String?
)

data class BodyMeasurementResponse(
    val id: String?,
    val measured_at: String,
    val weight_kg: Double?,
    val body_fat_percent: Double?,
    val muscle_mass_kg: Double?,
    val source: String?
)

data class WorkoutResponse(
    val id: String,
    val hevy_id: String?,
    val started_at: String,
    val ended_at: String?,
    val duration_minutes: Int?,
    val title: String,
    val total_volume_kg: Double?,
    val total_sets: Int?,
    val total_reps: Int?
)

data class WorkoutStatsSummaryResponse(
    val total_workouts: Int,
    val avg_duration: Int?,
    val total_volume_kg: Double?,
    val avg_sets_per_workout: Int?
)

data class HevySyncResult(
    val success: Boolean,
    val synced: Int,
    val skipped: Int,
    val total_fetched: Int,
    val sync_id: String?
)

data class HrvReadingResponse(
    val id: String?,
    val measured_at: String,
    val hrv_ms: Double,
    val resting_hr: Int?,
    val reading_type: String?,
    val source: String?,
    val device_name: String?
)

// ---------------------------------------------------------------------------
// Retrofit interface
// ---------------------------------------------------------------------------

interface ServerReadApi {
    @GET("api/bp")
    suspend fun getBloodPressure(
        @Query("days") days: Int = 30
    ): List<BpReadingResponse>

    @GET("api/sleep")
    suspend fun getSleep(
        @Query("days") days: Int = 30
    ): List<SleepSessionResponse>

    @GET("api/body")
    suspend fun getBodyMeasurements(
        @Query("days") days: Int = 30
    ): List<BodyMeasurementResponse>

    @GET("api/workouts")
    suspend fun getWorkouts(
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): List<WorkoutResponse>

    @GET("api/workouts/stats/summary")
    suspend fun getWorkoutStatsSummary(
        @Query("days") days: Int = 30
    ): WorkoutStatsSummaryResponse

    @GET("api/hrv/recent")
    suspend fun getHrv(
        @Query("days") days: Int = 30
    ): List<HrvReadingResponse>

    @POST("api/sync/hevy/workouts")
    suspend fun triggerHevySync(@Body body: RequestBody): HevySyncResult
}

// ---------------------------------------------------------------------------
// Client
// ---------------------------------------------------------------------------

class ServerApiClient(private val apiKey: String) {

    private val api: ServerReadApi

    init {
        val certificatePinner = CertificatePinner.Builder()
            .add("tyler-health.duckdns.org", "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=") // ISRG Root X1
            .add("tyler-health.duckdns.org", "sha256/diGVwiVYbubAI3RW4hB9xU8e/CH2GnkuvXFu2z8LMAs=") // ISRG Root X2
            .build()

        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $apiKey")
                    .build()
                chain.proceed(request)
            })
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    })
                }
            }
            .certificatePinner(certificatePinner)
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("${Config.SERVER_URL}/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        api = retrofit.create(ServerReadApi::class.java)
    }

    suspend fun getBloodPressure(days: Int = 30): Result<List<BpReadingResponse>> {
        return try {
            Result.success(api.getBloodPressure(days))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSleep(days: Int = 30): Result<List<SleepSessionResponse>> {
        return try {
            Result.success(api.getSleep(days))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getBodyMeasurements(days: Int = 30): Result<List<BodyMeasurementResponse>> {
        return try {
            Result.success(api.getBodyMeasurements(days))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getWorkouts(limit: Int = 20, offset: Int = 0): Result<List<WorkoutResponse>> {
        return try {
            Result.success(api.getWorkouts(limit, offset))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getWorkoutStatsSummary(days: Int = 30): Result<WorkoutStatsSummaryResponse> {
        return try {
            Result.success(api.getWorkoutStatsSummary(days))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getHrv(days: Int = 30): Result<List<HrvReadingResponse>> {
        return try {
            Result.success(api.getHrv(days))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun triggerHevySync(): Result<HevySyncResult> {
        return try {
            val body = "{}".toRequestBody("application/json".toMediaType())
            Result.success(api.triggerHevySync(body))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
