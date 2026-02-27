# Health Connect
-keep class androidx.health.connect.client.** { *; }

# Retrofit
-keepattributes Signature
-keepattributes Annotation
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
