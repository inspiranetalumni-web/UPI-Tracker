package com.inspiranet.upitracker

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val CHANNEL = "upi_tracker/notifications"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        val channel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
        UpiNotificationService.methodChannel = channel

        channel.setMethodCallHandler { call, result ->
            when (call.method) {
                "isPermissionGranted" -> {
                    val enabled = try {
                        androidx.core.app.NotificationManagerCompat
                            .getEnabledListenerPackages(applicationContext)
                            .contains(packageName)
                    } catch (e: Exception) {
                        android.provider.Settings.Secure.getString(
                            contentResolver,
                            "enabled_notification_listeners"
                        )?.contains(packageName) == true
                    }
                    result.success(enabled)
                }
                "openNotificationSettings" -> {
                    startActivity(
                        android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                    )
                    result.success(null)
                }
                "requestBatteryExemption" -> {
                    try {
                        val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        intent.data = android.net.Uri.parse("package:$packageName")
                        startActivity(intent)
                        result.success(true)
                    } catch (e: Exception) {
                        result.success(false)
                    }
                }
                "flushOfflineQueue" -> {
                    UpiNotificationService.flushOfflineQueue(applicationContext)
                    result.success(true)
                }
                else -> result.notImplemented()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Release channel reference so it can be recreated on next launch (#9)
        UpiNotificationService.methodChannel = null
    }
}
