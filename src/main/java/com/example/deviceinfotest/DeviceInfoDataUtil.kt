package com.example.deviceinfotest

import android.Manifest
import android.annotation.SuppressLint
import kotlin.math.sqrt
import android.app.Activity
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.StatFs
import android.provider.Settings
import android.util.DisplayMetrics
import android.webkit.WebSettings
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.File
import java.util.*

object DeviceInfoDataUtil {
    @SuppressLint("HardwareIds")
    fun collect(activity: Activity): DeviceInfoModel {
        val metrics: DisplayMetrics = activity.resources.displayMetrics
        val screenResolution = "${metrics.widthPixels}x${metrics.heightPixels}"
        val densityDpi = metrics.densityDpi
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        val board = Build.BOARD
        val brand = Build.BRAND
        val osVersion = Build.VERSION.RELEASE ?: "unknown"
        val runningApiLevel = Build.VERSION.SDK_INT
        val targetSdkVersion = activity.applicationInfo.targetSdkVersion

        @SuppressLint("HardwareIds")
        val androidId =
            Settings.Secure.getString(activity.contentResolver, Settings.Secure.ANDROID_ID)
        val timeZone = TimeZone.getDefault().id
        val deviceTimezone = TimeZone.getDefault().rawOffset / 60000
        val packageName = activity.packageName
        val appVersion = try {
            activity.packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
        val isRooted = isDeviceRooted()
        val isEmulator = isProbablyEmulator()
        val userAgent = getWebViewUserAgent(activity)

        // Battery status
        val batteryIntent = activity.registerReceiver(
            null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val batteryPct = if (level != null && scale != null && level >= 0 && scale > 0) {
            (level * 100 / scale)
        } else null

        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_FULL -> true
            BatteryManager.BATTERY_STATUS_DISCHARGING, BatteryManager.BATTERY_STATUS_NOT_CHARGING, BatteryManager.BATTERY_STATUS_UNKNOWN -> false
            else -> null
        }

        val deviceLanguage = Locale.getDefault().toString()
        val session = UUID.randomUUID().toString().replace("-", "").take(20)
        val time = System.currentTimeMillis()
        val browserName = getWebViewUserAgentName(activity)
        val browserVersion = getWebViewUserAgentVersion(activity)
        val deviceType = getDeviceType(activity)
        val storage = getTotalInternalStorage(activity) // Handles permission internally!

        return DeviceInfoModel(
            time = time,
            storage = storage,  // This will be 0 or "unknown" if no permission
            model = model,
            board = board,
            brand = brand,
            session = session,
            timeZone = timeZone,
            userAgent = userAgent,
            densityDpi = densityDpi,
            batteryPct = batteryPct,
            isRooted = isRooted,
            screenResolution = screenResolution,
            runningApiLevel = runningApiLevel,
            manufacturer = manufacturer,
            androidId = androidId,
            appVersion = appVersion,
            isEmulator = isEmulator,
            isCharging = isCharging,
            targetSdkVersion = targetSdkVersion,
            deviceTimezone = deviceTimezone,
            deviceLanguage = deviceLanguage,
            browserName = browserName,
            browserVersion = browserVersion,
            osVersion = osVersion,
            deviceType = deviceType,

            packageName = "",
            batteryLevel = 0,
            externalStorageAvailable = true,
            externalStorageBytes = 0L,
            fineLocationAllowed = true,
            coarseLocationAllowed = true,
        )
    }

    private fun getWebViewUserAgent(context: Activity): String =
        try {
            WebSettings.getDefaultUserAgent(context)
        } catch (e: Exception) {
            "unknown"
        }

    private fun getWebViewUserAgentName(context: Activity): String {
        val ua = getWebViewUserAgent(context).lowercase(Locale.US)
        return when {
            ua.contains("chrome") -> "chrome"
            ua.contains("samsungbrowser") -> "samsung"
            ua.contains("firefox") -> "firefox"
            ua.contains("opera") -> "opera"
            else -> "android-webview"
        }
    }

    private fun getWebViewUserAgentVersion(context: Activity): String {
        val ua = getWebViewUserAgent(context)
        val regex = Regex("([A-Za-z]+)/([\\d.]+)")
        return regex.find(ua)?.groups?.get(2)?.value ?: "unknown"
    }

    private fun getDeviceType(context: Activity): String {
        val metrics: DisplayMetrics = context.resources.displayMetrics
        val yInches: Float = metrics.heightPixels / metrics.ydpi
        val xInches: Float = metrics.widthPixels / metrics.xdpi
        val diagonalInches = sqrt((xInches * xInches + yInches * yInches).toDouble())
        return if (diagonalInches >= 7.0) "tablet" else "mobile"
    }


    private fun getTotalInternalStorage(context: Activity): Long {
        return try {
            val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            else true
            if (hasPermission) {
                val stat = StatFs(context.filesDir.path)
                stat.blockCountLong * stat.blockSizeLong
            } else 0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun isDeviceRooted(): Boolean {
        val buildTags = Build.TAGS
        if (buildTags != null && buildTags.contains("test-keys")) return true
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su", "/system/bin/su",
            "/system/xbin/su", "/data/local/xbin/su",
            "/data/local/bin/su", "/system/sd/xbin/su",
            "/system/bin/failsafe/su", "/data/local/su"
        )
        for (path in paths) {
            if (File(path).exists()) return true
        }
        return false
    }

    private fun isProbablyEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.lowercase(Locale.US).contains("droid4x")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86"))
                || Build.BOARD == "QC_Reference_Phone"
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.HOST.startsWith("Build") // MSFT emulator
                || Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")
    }
}

// Extension for serialization
fun DeviceInfoModel.toJson(): String = JSONObject().apply {
    put("time", time)
    put("model", model)
    put("board", board)
    put("brand", brand)
    put("session", session)
    put("storage", storage)
    put("timeZone", timeZone)
    put("isRooted", isRooted)
    put("androidId", androidId)
    put("userAgent", userAgent)
    put("appVersion", appVersion)
    put("osVersion", osVersion)
    put("densityDpi", densityDpi)
    put("batteryPct", batteryPct)
    put("isEmulator", isEmulator)
    put("deviceType", deviceType)
    put("isCharging", isCharging)
    put("packageName", packageName)
    put("browserName", browserName)
    put("manufacturer", manufacturer)
    put("batteryLevel", batteryLevel)
    put("deviceTimezone", deviceTimezone)
    put("deviceLanguage", deviceLanguage)
    put("browserVersion", browserVersion)
    put("runningApiLevel", runningApiLevel)
    put("screenResolution", screenResolution)
    put("targetSdkVersion", targetSdkVersion)
}.toString()
