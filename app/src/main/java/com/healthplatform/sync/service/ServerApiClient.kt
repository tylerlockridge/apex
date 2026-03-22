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
import okio.Buffer
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
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

data class ServerVersionResponse(val version: String)

// ---------------------------------------------------------------------------
// Phase 3: Progression + Generation models
// ---------------------------------------------------------------------------

data class LandmarkStatusResponse(
    val sets: Int,
    val mevLow: Int,
    val mavLow: Int,
    val mavHigh: Int,
    val mrvHigh: Int,
    val status: String,
    val approachingMrv: Boolean
)

data class ExerciseSignalResponse(
    val exerciseTemplateId: String,
    val exerciseName: String,
    val lastWeightKg: Double,
    val consecutiveTargetHits: Int,
    val suggestion: String
)

data class ProgressionSummaryResponse(
    val snapshotDate: String,
    val periodDays: Int,
    val trainingLoadScore: Int,
    val historyFresh: Boolean,
    val volumeByMuscle: Map<String, Int>,
    val landmarkStatus: Map<String, LandmarkStatusResponse>,
    val exerciseSignals: List<ExerciseSignalResponse>
)

data class ReadinessPayloadRequest(
    val aggregateScore: Int?,
    val label: String?,
    val computedAt: String? = null,
    val inputs: List<ReadinessInputRequest>? = null
)

data class ReadinessInputRequest(
    val id: String,
    val status: String,
    val score: Int? = null,
    val effectiveWeight: Double,
    val lastUpdatedAt: String? = null,
    val reason: String? = null
)

data class GenerateRoutineRequest(
    val sessionType: String,
    val targetMuscleGroups: List<String>,
    val durationMinutes: Int = 60,
    val readiness: ReadinessPayloadRequest? = null
)

data class GeneratedRoutineReadinessResponse(
    val aggregateScore: Double?,
    val label: String?
)

data class GeneratedRoutineProgressionResponse(
    val trainingLoadScore: Int,
    val historyFresh: Boolean
)

data class GeneratedRoutineExerciseResponse(
    val id: String,
    val ordering: Int,
    val exerciseTemplateId: String,
    val exerciseName: String,
    val targetSets: Int,
    val targetReps: Int?,
    val targetWeightKg: Double?,
    val targetRpe: Double?,
    val resolvedPrimary: String,
    val resolvedSecondaries: List<String>,
    val reasoning: String,
    val flags: List<String>
)

data class GeneratedRoutineResponse(
    val id: String,
    val title: String,
    val status: String,
    val reasoningSummary: String?,
    val readiness: GeneratedRoutineReadinessResponse?,
    val progression: GeneratedRoutineProgressionResponse?,
    val warnings: List<String>,
    val exercises: List<GeneratedRoutineExerciseResponse>
)

data class RoutineDecisionRequest(val decision: String)

data class RoutineDecisionResponse(
    val id: String,
    val status: String,
    val decidedAt: String?
)

data class AppErrorResponse(
    val error: String,
    val code: String?
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

    @GET("api/version")
    suspend fun getServerVersion(): ServerVersionResponse

    @GET("api/workouts/progression/summary")
    suspend fun getProgressionSummary(
        @Query("days") days: Int = 7
    ): ProgressionSummaryResponse

    @POST("api/generated-routines")
    suspend fun generateRoutine(@Body body: GenerateRoutineRequest): GeneratedRoutineResponse

    @GET("api/generated-routines/{id}")
    suspend fun getGeneratedRoutine(@Path("id") id: String): GeneratedRoutineResponse

    @POST("api/generated-routines/{id}/decision")
    suspend fun decideGeneratedRoutine(
        @Path("id") id: String,
        @Body body: RoutineDecisionRequest
    ): RoutineDecisionResponse
}

// ---------------------------------------------------------------------------
// Client
// ---------------------------------------------------------------------------

class ServerApiClient(
    private val apiKey: String,
    baseUrl: String = Config.SERVER_URL_DEFAULT,
    private val deviceSecret: String = "",
) {

    private val api: ServerReadApi

    init {
        val effectiveBase = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        // S-1: Auth interceptor adds Bearer token + HMAC signing (same as ApiService)
        // so read endpoints have the same authentication strength as write endpoints.
        val client = sharedClient(baseUrl).newBuilder()
            .addInterceptor(Interceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $apiKey")
                    .build()
                chain.proceed(request)
            })
            .addInterceptor(Interceptor { chain ->
                val original = chain.request()
                val body = original.body
                val timestamp = (System.currentTimeMillis() / 1000L).toString()
                val signed = if (body != null && deviceSecret.isNotEmpty()) {
                    val buffer = Buffer()
                    body.writeTo(buffer)
                    val bodyBytes = buffer.readByteArray()
                    val mac = javax.crypto.Mac.getInstance("HmacSHA256")
                    mac.init(javax.crypto.spec.SecretKeySpec(deviceSecret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
                    mac.update("$timestamp\n".toByteArray(Charsets.UTF_8))
                    mac.update(bodyBytes)
                    val hex = mac.doFinal().joinToString("") { "%02x".format(it) }
                    original.newBuilder()
                        .addHeader("X-Signature", "sha256=$hex")
                        .addHeader("X-Timestamp", timestamp)
                        .method(original.method, bodyBytes.toRequestBody(body.contentType()))
                        .build()
                } else if (deviceSecret.isNotEmpty()) {
                    // GET requests: sign empty body
                    val mac = javax.crypto.Mac.getInstance("HmacSHA256")
                    mac.init(javax.crypto.spec.SecretKeySpec(deviceSecret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
                    mac.update("$timestamp\n".toByteArray(Charsets.UTF_8))
                    val hex = mac.doFinal().joinToString("") { "%02x".format(it) }
                    original.newBuilder()
                        .addHeader("X-Signature", "sha256=$hex")
                        .addHeader("X-Timestamp", timestamp)
                        .build()
                } else {
                    original
                }
                chain.proceed(signed)
            })
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    })
                }
            }
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(effectiveBase)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        api = retrofit.create(ServerReadApi::class.java)
    }

    companion object {
        @Volatile private var cachedClient: OkHttpClient? = null
        @Volatile private var cachedHost: String? = null

        /**
         * Returns a shared [OkHttpClient] with cert pinning, connection pool, and
         * timeouts. The client is rebuilt only when the target host changes (e.g.
         * after QR onboarding to a different server). Per-instance auth interceptors
         * are added via [OkHttpClient.newBuilder] which shares the underlying pool.
         */
        private fun sharedClient(baseUrl: String): OkHttpClient {
            val host = Config.extractHost(baseUrl)
            val existing = cachedClient
            if (existing != null && cachedHost == host) return existing
            return synchronized(this) {
                val current = cachedClient
                if (current != null && cachedHost == host) return current
                val certificatePinner = CertificatePinner.Builder()
                    .add(host, Config.PIN_LETS_ENCRYPT_E8)
                    .add(host, Config.PIN_ISRG_ROOT_X1)
                    .add(host, Config.PIN_ISRG_ROOT_X2)
                    .build()
                OkHttpClient.Builder()
                    .certificatePinner(certificatePinner)
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .build().also {
                        cachedClient = it
                        cachedHost = host
                    }
            }
        }
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

    /** Returns the server version string (e.g. "1.2.3"), or failure if endpoint doesn't exist. */
    suspend fun getServerVersion(): Result<String> {
        return try {
            Result.success(api.getServerVersion().version)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getProgressionSummary(days: Int = 7): Result<ProgressionSummaryResponse> {
        return try {
            Result.success(api.getProgressionSummary(days))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun generateRoutine(request: GenerateRoutineRequest): Result<GeneratedRoutineResponse> {
        return try {
            Result.success(api.generateRoutine(request))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getGeneratedRoutine(id: String): Result<GeneratedRoutineResponse> {
        return try {
            Result.success(api.getGeneratedRoutine(id))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun decideGeneratedRoutine(id: String, decision: String): Result<RoutineDecisionResponse> {
        return try {
            Result.success(api.decideGeneratedRoutine(id, RoutineDecisionRequest(decision)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
