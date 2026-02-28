# Health Connect
-keep class androidx.health.connect.client.** { *; }

# Retrofit
-keepattributes Signature
-keepattributes Annotation
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Gson — keep data classes used for JSON serialization/deserialization
-keepattributes EnclosingMethod
-keep class com.healthplatform.sync.service.** { *; }
-keep class com.healthplatform.sync.data.** { *; }

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
