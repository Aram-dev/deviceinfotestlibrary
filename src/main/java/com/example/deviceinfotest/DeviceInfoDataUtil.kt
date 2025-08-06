package com.example.deviceinfotest

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.DisplayMetrics
import android.view.WindowManager
import android.webkit.WebSettings
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.File
import java.net.InetAddress
import java.security.MessageDigest
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.math.sqrt

object DeviceInfoDataUtil {


    fun collect(context: Context): JSONObject {
        val json = JSONObject()

        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val pm = context.packageManager
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val screenWidth: Int
        val screenHeight: Int

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            screenWidth = bounds.width()
            screenHeight = bounds.height()
        } else {
            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(displayMetrics)
            screenWidth = displayMetrics.widthPixels
            screenHeight = displayMetrics.heightPixels
        }

        val displayMetrics = context.resources.displayMetrics
        val screenScale = displayMetrics.density
        val densityDpi = displayMetrics.densityDpi

        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensors = sm.getSensorList(Sensor.TYPE_ALL)

        val batteryStatus = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        val batteryLevel = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val batteryScale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct =
            if (batteryLevel >= 0 && batteryScale > 0) (batteryLevel * 100) / batteryScale else -1

        val deviceInfo = JSONObject()

        // Android ID
        @SuppressLint("HardwareIds")
        deviceInfo.put(
            "android_id",
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        )

        // Android Version
        deviceInfo.put("android_version", "${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")

        // Audio
        val isMuted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.isStreamMute(AudioManager.STREAM_MUSIC)
        } else {
            am.getStreamVolume(AudioManager.STREAM_MUSIC) == 0
        }
        deviceInfo.put("audio_mute_status", isMuted)
        deviceInfo.put("audio_volume_current", am.getStreamVolume(AudioManager.STREAM_MUSIC))

        // Battery
        deviceInfo.put(
            "battery_charging",
            batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) != 0
        )
        deviceInfo.put(
            "battery_health",
            batteryStatus?.getIntExtra(
                BatteryManager.EXTRA_HEALTH,
                BatteryManager.BATTERY_HEALTH_UNKNOWN
            )
        )
        deviceInfo.put("battery_level", batteryPct)
        deviceInfo.put(
            "battery_temperature",
            batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)?.toFloat()?.div(10)
        )
        deviceInfo.put(
            "battery_voltage",
            batteryStatus?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        )

        // Build Info
        deviceInfo.put("build_device", Build.DEVICE)
        deviceInfo.put("build_id", Build.ID)
        deviceInfo.put("build_manufacturer", Build.MANUFACTURER)
        deviceInfo.put("build_model", Build.MODEL)
        deviceInfo.put("build_number", Build.DISPLAY)
        deviceInfo.put("build_time", Build.TIME)

        // CPU Info
        deviceInfo.put("cpu_type", Build.SUPPORTED_ABIS.joinToString())
        deviceInfo.put("cpu_count", Runtime.getRuntime().availableProcessors())
        deviceInfo.put("cpu_hash", hashText(Build.SUPPORTED_ABIS.joinToString()))

        // Kernel
        deviceInfo.put("kernel_arch", System.getProperty("os.arch"))
        deviceInfo.put("kernel_name", System.getProperty("os.name"))
        deviceInfo.put("kernel_version", System.getProperty("os.version"))

        // Storage
        val stat = StatFs(Environment.getDataDirectory().path)
        deviceInfo.put("total_storage", stat.blockCountLong * stat.blockSizeLong)
        deviceInfo.put("free_storage", stat.availableBlocksLong * stat.blockSizeLong)

        // Memory
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        deviceInfo.put("physical_memory", memoryInfo.totalMem)

        // Screen
        deviceInfo.put("screen_width", screenWidth)
        deviceInfo.put("screen_height", screenHeight)
        deviceInfo.put("screen_scale", screenScale)

        // Sensors
        deviceInfo.put("sensor_hash", hashText(sensors.joinToString { it.name }))
        deviceInfo.put("has_proximity_sensor", sensors.any { it.type == Sensor.TYPE_PROXIMITY })

        // Network Info (partial)
        val networkConfig =
            if (isWifiConnected(context)) "WIFI"
            else if (isMobileDataConnected(context)) "MOBILE"
            else if (isVpnActive(context)) "VPN"
            else "UNKNOWN"
        deviceInfo.put("network_config", networkConfig)

        // Sim Info
        deviceInfo.put("carrier_country", tm.networkCountryIso)
        deviceInfo.put("carrier_name", tm.networkOperatorName)

        // Others (placeholders for now)
        deviceInfo.put("device_ip_address", InetAddress.getLocalHost().hostAddress)
        deviceInfo.put("device_name", Build.MANUFACTURER + " " + Build.MODEL)
        deviceInfo.put("timezone_identifier", TimeZone.getDefault().id)
        deviceInfo.put("region_language", Locale.getDefault().language)
        deviceInfo.put("region_country", Locale.getDefault().country)
        deviceInfo.put("region_timezone", TimeZone.getDefault().rawOffset / 3600000)
        deviceInfo.put(
            "usb_debugging_state",
            if (Settings.Secure.getInt(
                    context.contentResolver,
                    Settings.Secure.ADB_ENABLED,
                    0
                ) == 1
            ) "USB_DEBUGGING_ENABLED" else "DISABLED"
        )

        json.put("device_details", deviceInfo)
        return json
    }

    private fun hashText(text: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    fun isWifiConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork
            val capabilities = cm.getNetworkCapabilities(network)
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.type == ConnectivityManager.TYPE_WIFI
        }
    }

    fun isMobileDataConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork
            val capabilities = cm.getNetworkCapabilities(network)
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.type == ConnectivityManager.TYPE_MOBILE
        }
    }

    fun isVpnActive(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork
            val capabilities = cm.getNetworkCapabilities(network)
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.type == ConnectivityManager.TYPE_VPN
        }
    }

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
