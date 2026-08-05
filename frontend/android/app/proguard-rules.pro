# Flutter Wrapper
-keep class io.flutter.app.** { *; }
-keep class io.flutter.plugin.**  { *; }
-keep class io.flutter.util.**  { *; }
-keep class io.flutter.view.**  { *; }
-keep class io.flutter.**  { *; }
-keep class io.flutter.plugins.**  { *; }
-dontwarn io.flutter.embedding.**

# Firebase/Google Play Services
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}

# UPI Tracker App Classes
-keep class com.inspiranet.upitracker.** { *; }

# JSON parsing (used in UpiNotificationService)
-keep class org.json.** { *; }

# Networking & Storage
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-keep class com.jakewharton.retrofit2.adapter.kotlin.coroutines.** { *; }
-keep class com.itkacher.okhttpprofiler.** { *; }
-keep class java.nio.file.** { *; }
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
