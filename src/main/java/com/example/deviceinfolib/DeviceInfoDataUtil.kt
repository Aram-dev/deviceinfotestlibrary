package com.example.deviceinfolib

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
import android.os.BatteryManager
import android.os.Build
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import java.security.MessageDigest
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

@SuppressLint("HardwareIds")
internal object DeviceInfoDataUtil {

    @SuppressLint("DefaultLocale")
    fun collectDeviceDetails(activity: Activity): DeviceDetails {
        val ctx = activity.applicationContext

        // --- Always-available or safe fields ---
        val androidId = runCatching {
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()

        val appInstanceId =
            GuidProvider.getHashedUniqueId(ctx) // stored in SharedPreferences/SecureStorage

        val androidVersion = "${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE ?: "unknown"})"

        val appGuid =
            GuidProvider.getOrCreateAppGuid(ctx) // stored in SharedPreferences/SecureStorage

        val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val audioMuted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audio.isStreamMute(AudioManager.STREAM_MUSIC)
        } else {
            audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 0
        }
        val audioVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)

        // Battery
        val battery = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val batteryLevel = safeBatteryPercent(battery)
        val batteryCharging = safeBatteryCharging(battery)
        val batteryHealth = safeBatteryHealth(battery)
        val batteryTemp = (battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val batteryVolt = battery?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0

        // Biometric enrolled?
        val biometricStatus = BiometricUtil.status(ctx)

        // Bootloader/dev options (best-effort; dev options is readable, ADB not for normal apps)
        val bootloaderState =
            if (Build.TAGS?.contains("test-keys") == true) "BOOTLOADER_STATE_UNLOCKED" else "BOOTLOADER_STATE_LOCKED"
        val enabled = Settings.Global.getInt(
            ctx.contentResolver,
            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
            0
        ) == 1
        val devOptionsEnabled = if (enabled) "DEV_OPTIONS_ENABLED" else "DEV_OPTIONS_DISABLED"

        // Build
        val buildTime = Build.TIME
        val buildDevice = Build.DEVICE
        val buildId = Build.ID
        val buildManufacturer = Build.MANUFACTURER
        val buildModel = Build.MODEL
        val buildNumber = Build.DISPLAY

        // Telephony (permission-gated for ids; network/country/operator name mostly safe)
        val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val carrierCountry = safeUpper(tm.networkCountryIso)
        val carrierName = safeString(tm.networkOperatorName)
        val deviceCellularId: String? = CellularIdUtil.getHashedDeviceCellularId(ctx)

        // CPU
        val cpuType =
            Build.SUPPORTED_ABIS.firstOrNull() ?: System.getProperty("os.arch") ?: "unknown"
        val cpuCount = Runtime.getRuntime().availableProcessors()
        val cpuSpeedMhz = CpuUtil.maxFreqMHz() // returns -1.0f if unavailable
        val cpuHash = sha256(Build.SUPPORTED_ABIS.joinToString(","))

        // Device hash (stable-ish, non-PII combo)
        val deviceHash = sha256(
            listOfNotNull(
                buildManufacturer, buildModel, buildDevice, androidId
            ).joinToString("|")
        )

        // Name
        val deviceName = "$buildManufacturer $buildModel".trim()

        // Orientation (best-effort, based on current rotation—can be null if not a resumed activity)
        val deviceOrientation = OrientationUtil.describe(activity)

        // DNS/IP/ISP/country: requires network calls or OS APIs not available to normal apps.
        // Provide hook for caller to inject a resolver; default nulls.
        // Usage to enrich (optional)
        // NetInfoResolver.externalResolver = { ctx, publicIp ->
        //     // Do your HTTP call here (blocking or wrap sync call); return IpInfo with ISP/country.
        //     null // keep baseline if you don't implement
        // }
        val ipInfo = NetInfoResolver.resolveBestEffort(ctx)
        val deviceIpAddress = ipInfo?.ip
        val deviceIpCountry = ipInfo?.countryCode
        val deviceIpIsp = ipInfo?.isp
        val deviceIpRegion = ipInfo?.region

        val dnsIp = ipInfo?.dnsIp
        val dnsCountry = ipInfo?.dnsCountry
        val dnsIsp = ipInfo?.dnsIsp

        // Storage
        val (totalStorage, freeStorage) = storageBytes(ctx)

        // Memory
        val physicalMemory = (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .run { ActivityManager.MemoryInfo().also { getMemoryInfo(it) }.totalMem }

        // Screen metrics (non-deprecated, API aware)
        val (width, height) = DisplayUtil.screenPx(ctx)
        val screenScale = ctx.resources.displayMetrics.density
        val screenBrightness = ScreenUtil.brightnessPercent(ctx) // 0..100

        // Sensors
        val sensorHash = runCatching {
            val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val sensors = sm.getSensorList(Sensor.TYPE_ALL)
                .joinToString("|") { "${it.type}:${it.name}:${it.vendor}" }
            sha256(sensors)
        }.getOrDefault("")

        val hasProximity = runCatching {
            val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            sm.getDefaultSensor(Sensor.TYPE_PROXIMITY) != null
        }.getOrDefault(false)

        // Network (modern API)
        val networkConfig = NetworkUtil.activeTransport(ctx) // WIFI / CELLULAR / NONE
        val vpnState = if (NetworkUtil.isVpnActive(ctx)) "CONNECTED" else "NOT_CONNECTED"

        // WiFi info (SSID: needs ACCESS_FINE_LOCATION + ACCESS_WIFI_STATE on modern Android)
        val wifiSsid = if (has(ctx, Manifest.permission.ACCESS_FINE_LOCATION) &&
            has(ctx, Manifest.permission.ACCESS_WIFI_STATE)
        ) WifiUtil.ssid(ctx) else null

        val wifiMacAddress: String? = null // not available to normal apps (randomized)

        // GSF ID (not available without Google Services APIs; keep null)
        val gsfId: String? = null

        val usbCableState =
            "UNKNOWN" // needs BroadcastReceiver and/or USB manager hooks; leave unknown
        val usbDebuggingState = "UNKNOWN" // ADB_ENABLED is inaccessible for normal apps

        // NFC
        val (nfcAvailable, nfcEnabled) = NfcUtil.state(ctx)

        // Keyguard
        val isKeyguardSecure = KeyguardUtil.isSecure(ctx)

        // On call?
        val isOnCall =
            CallUtil.isOnCall(ctx) // requires READ_PHONE_STATE on older APIs; otherwise false best-effort

        // Remote control?
        val isRemoteControlConnected = false // custom business logic / integrations

        // Mirroring?
        val isScreenMirrored = false // no public reliable API; keep false

        // Pasteboard hash (clipboard contents hash) – access may be restricted; return stable empty hash
        val pasteboardHash = sha256("")

        // System info
        val kernelArch = System.getProperty("os.arch") ?: "unknown"
        val kernelName = System.getProperty("os.name") ?: "Linux"
        val kernelVersion = System.getProperty("os.version") ?: "unknown"

        val systemUptimeSec = (SystemClock.elapsedRealtime() / 1000L).toInt()
        val lastBootTimeSec = (System.currentTimeMillis() / 1000L) - systemUptimeSec

        val tzId = TimeZone.getDefault().id
        val regionLang = Locale.getDefault().language
        val regionCountry = Locale.getDefault().country
        val regionTzOffset = TimeZone.getDefault().rawOffset // ms offset
        val regionTimezone = String.format(
            "%+03d:%02d",
            regionTzOffset / 3600000,
            (abs(regionTzOffset / 60000) % 60)
        )

        val timezoneIdentifier = if (tzId == "GMT") "GMT" else tzId

        // Power source
        val powerSource = PowerUtil.powerSource(battery)

        // Security / integrity (best-effort)
        val isRooted = RootUtil.isRooted()
        val isEmulator = EmulatorUtil.isProbablyEmulator()
        val systemIntegrity = if (isEmulator || isRooted) "COMPROMISED" else "OK"

        val trueDeviceId =
            GuidProvider.getOrCreateTrueDeviceId(ctx) // long-lived UUID (not hardware id)

        // NFC / Clone / Suspicious flags – sample
        val isAppCloned = CloneUtil.isCloned(ctx)
        val suspiciousFlags = mutableListOf<String>().apply {
            if (isEmulator) add("EMULATOR")
            if (isRooted) add("ROOTED")
        }

        // Orientation, source, session
        val sessionId = SessionIdProvider.currentSessionId()
        val source = "android-${Build.VERSION.SDK_INT}.${Build.VERSION.RELEASE ?: "?"}"

        // First API level device ever launched with (SDK_INT for current; first api (ro.product.first_api_level) if available)
        val firstApiLevel = SystemPropertiesUtil.firstApiLevel() ?: Build.VERSION.SDK_INT

        // Region/timezone high-level
        val type = "android"

        // Location (permission-gated)
        val location = LocationUtil.lastKnown(ctx)

        return DeviceDetails(
            android_id = androidId,
            app_instance_id = appInstanceId,
            android_version = androidVersion,
            app_guid = appGuid,
            audio_mute_status = audioMuted,
            audio_volume_current = audioVolume,
            battery_charging = batteryCharging,
            battery_health = batteryHealth,
            battery_level = batteryLevel,
            battery_temperature = batteryTemp.toDouble(),
            battery_voltage = batteryVolt,
            biometric_status = biometricStatus,
            bootloader_state = bootloaderState,
            build_device = buildDevice,
            build_id = buildId,
            build_manufacturer = buildManufacturer,
            build_model = buildModel,
            build_number = buildNumber,
            build_time = buildTime / 1000, // seconds
            carrier_country = carrierCountry,
            carrier_name = carrierName,
            cpu_count = cpuCount,
            cpu_hash = cpuHash,
            cpu_speed = cpuSpeedMhz,
            cpu_type = cpuType,
            developer_options_state = devOptionsEnabled,
            device_cellular_id = deviceCellularId,
            device_hash = deviceHash,
            device_ip_address = deviceIpAddress,
            device_ip_country = deviceIpCountry,
            device_ip_isp = deviceIpIsp,
            device_ip_region = deviceIpRegion,
            device_name = deviceName,
            device_orientation = deviceOrientation,
            dns_ip_country = dnsCountry,
            dns_ip_isp = dnsIsp,
            dns_ip = dnsIp,
            free_storage = freeStorage,
            gsf_id = gsfId,
            has_proximity_sensor = hasProximity,
            interfering_apps = emptyList(),
            is_click_automator_installed = false,
            is_emulator = isEmulator,
            is_keyguard_secure = isKeyguardSecure,
            is_nfc_available = nfcAvailable,
            is_nfc_enabled = nfcEnabled,
            is_on_call = isOnCall,
            is_remote_control_connected = isRemoteControlConnected,
            is_rooted = isRooted,
            is_screen_being_mirrored = isScreenMirrored,
            kernel_arch = kernelArch,
            kernel_name = kernelName,
            kernel_version = kernelVersion,
            last_boot_time = lastBootTimeSec,
            network_config = networkConfig,
            pasteboard_hash = pasteboardHash,
            physical_memory = physicalMemory,
            region_country = regionCountry,
            region_language = regionLang,
            region_timezone = regionTimezone,
            remote_control_provider = null,
            screen_brightness = screenBrightness,
            screen_height = height,
            screen_scale = screenScale.toInt(), // your sample shows 2 (int), so cast density
            screen_width = width,
            sensor_hash = sensorHash,
            session_id = sessionId,
            source = source,
            system_uptime = systemUptimeSec,
            timezone_identifier = timezoneIdentifier,
            total_storage = totalStorage,
            type = type,
            usb_cable_state = usbCableState,
            usb_debugging_state = usbDebuggingState,
            wifi_mac_address = wifiMacAddress,
            wifi_ssid = wifiSsid,
            first_api_level = firstApiLevel,
            power_source = powerSource,
            proxy_address = null,
            proxy_state = if (ProxyUtil.isUsingProxy()) "CONNECTED" else "NOT_CONNECTED",
            vpn_state = vpnState,
            suspicious_flags = suspiciousFlags,
            true_device_id = trueDeviceId,
            system_integrity = systemIntegrity,
            is_app_cloned = isAppCloned,
            device_location = location
        )
    }

    // ---------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------

    private fun has(ctx: Context, perm: String) =
        ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED

    private fun safeString(s: String?) = s?.takeIf { it.isNotBlank() }

    private fun safeUpper(iso: String?) = iso?.takeIf { it.isNotBlank() }?.uppercase()

    private fun sha256(text: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun storageBytes(ctx: Context): Pair<Long, Long> {
        return try {
            val stat = StatFs(ctx.filesDir.path)
            val total = stat.blockCountLong * stat.blockSizeLong
            val free = stat.availableBlocksLong * stat.blockSizeLong
            total to free
        } catch (_: Exception) {
            0L to 0L
        }
    }

    private fun safeBatteryPercent(intent: Intent?): Int? {
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100 / scale) else null
    }

    private fun safeBatteryCharging(intent: Intent?): Boolean {
        val st = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun safeBatteryHealth(intent: Intent?): String {
        return when (intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1) {
            BatteryManager.BATTERY_HEALTH_COLD -> "COLD"
            BatteryManager.BATTERY_HEALTH_DEAD -> "DEAD"
            BatteryManager.BATTERY_HEALTH_GOOD -> "GOOD"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "OVER_VOLTAGE"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "OVERHEAT"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "UNSPECIFIED_FAILURE"
            else -> "UNKNOWN"
        }
    }
}