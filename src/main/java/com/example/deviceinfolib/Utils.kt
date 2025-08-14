package com.example.deviceinfolib

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.DisplayMetrics
import android.view.Surface
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal object GuidProvider {
    private const val PREF = "device_info_lib_prefs"
    private const val K_APP_GUID = "app_guid"
    private const val K_TRUE_DEVICE_ID = "true_device_id"
    private const val APP_INSTANCE_ID_KEY = "app_instance_id"

    fun getOrCreateAppGuid(ctx: Context): String =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).let { sp ->
            sp.getString(K_APP_GUID, null) ?: UUID.randomUUID().toString().also {
                sp.edit { putString(K_APP_GUID, it) }
            }
        }

    fun getOrCreateTrueDeviceId(ctx: Context): String =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).let { sp ->
            sp.getString(K_TRUE_DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
                sp.edit { putString(K_TRUE_DEVICE_ID, it) }
            }
        }

    // Combines ANDROID_ID, app UUID, and basic build info, then hashes it.
    fun getHashedUniqueId(context: Context): String {
        val androidId = getAndroidId(context)
        val appInstanceId = getOrCreateAppInstanceId(context)
        val buildInfo = "${Build.MANUFACTURER}:${Build.MODEL}"
        val raw = "$androidId:$appInstanceId:$buildInfo"
        return sha256(raw)
    }

    // Get ANDROID_ID
    @SuppressLint("HardwareIds")
    private fun getAndroidId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown"
    }

    // Generates (or retrieves) a stable app instance UUID.
    private fun getOrCreateAppInstanceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        var id = prefs.getString(APP_INSTANCE_ID_KEY, null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            prefs.edit { putString(APP_INSTANCE_ID_KEY, id) }
        }
        return id
    }

    // SHA-256 hash function (hex output)
    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

internal object CellularIdUtil {

    @SuppressLint("MissingPermission", "HardwareIds")
    fun getHashedDeviceCellularId(ctx: Context): String? {
        // Must have READ_PHONE_STATE just to attempt.
        if (!PermissionHelper.has(ctx, Manifest.permission.READ_PHONE_STATE)) return null

        val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return null

        // From Android 10+ (API 29), hardware IDs require extra privileges.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val hasCarrierPrivileges = runCatching { tm.hasCarrierPrivileges() }.getOrDefault(false)
            val isDefaultDialer = isDefaultDialer(ctx)
            val isDefaultSms = isDefaultSms(ctx)
            if (!(hasCarrierPrivileges || isDefaultDialer || isDefaultSms)) {
                return null
            }
        }

        val ids = mutableListOf<String>()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val phoneCount = runCatching {
                    @Suppress("DEPRECATION")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                        tm.activeModemCount
                    else tm.phoneCount
                }.getOrDefault(1)
                for (slot in 0 until phoneCount) {
                    val imei = runCatching { tm.getImei(slot) }.getOrNull()
                    val meid = runCatching {
                        @Suppress("DEPRECATION")
                        tm.getMeid(slot)
                    }.getOrNull()
                    if (!imei.isNullOrBlank()) ids += imei
                    if (!meid.isNullOrBlank()) ids += meid
                }
            } else {
                @Suppress("DEPRECATION")
                val legacy = tm.deviceId
                if (!legacy.isNullOrBlank()) ids += legacy
            }
        } catch (_: SecurityException) {
            return null
        } catch (_: Throwable) {
            // ignore and fall through
        }

        if (ids.isEmpty()) return null
        return sha256(ids.joinToString("|"))
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun isDefaultDialer(ctx: Context): Boolean {
        val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager ?: return false
        return tm.defaultDialerPackage?.equals(ctx.packageName, ignoreCase = true) == true
    }

    private fun isDefaultSms(ctx: Context): Boolean {
        val pkg = Telephony.Sms.getDefaultSmsPackage(ctx)
        return pkg?.equals(ctx.packageName, ignoreCase = true) == true
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }
}

internal object EmulatorUtil {
    fun isProbablyEmulator(): Boolean {
        val fp = Build.FINGERPRINT?.lowercase(Locale.US) ?: ""
        return fp.contains("generic") ||
                Build.MODEL?.contains("Emulator", true) == true ||
                Build.MANUFACTURER?.contains("Genymotion", true) == true ||
                Build.BRAND?.startsWith("generic", true) == true
    }
}

internal object RootUtil {
    fun isRooted(): Boolean {
        val tags = Build.TAGS
        if (tags?.contains("test-keys") == true) return true
        val paths = arrayOf(
            "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su", "/system/xbin/su",
            "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su",
            "/system/bin/failsafe/su", "/data/local/su"
        )
        return paths.any { File(it).exists() }
    }
}

internal object DisplayUtil {
    fun screenPx(ctx: Context): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val wm = ctx.getSystemService(WindowManager::class.java)
            val b = wm.currentWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val dm: DisplayMetrics = ctx.resources.displayMetrics
            dm.widthPixels to dm.heightPixels
        }
    }
}

internal object ScreenUtil {
    fun brightnessPercent(ctx: Context): Int {
        return runCatching {
            val c = Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            (c * 100f / 255f).toInt()
        }.getOrDefault(0)
    }
}

internal object NetworkUtil {
    fun activeTransport(ctx: Context): String {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val net = cm.activeNetwork ?: return "NONE"
            val cap = cm.getNetworkCapabilities(net) ?: return "NONE"
            when {
                cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
                cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
                cap.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
                else -> "NONE"
            }
        } else {
            @Suppress("DEPRECATION")
            when (cm.activeNetworkInfo?.type) {
                ConnectivityManager.TYPE_WIFI -> "WIFI"
                ConnectivityManager.TYPE_MOBILE -> "CELLULAR"
                ConnectivityManager.TYPE_ETHERNET -> "ETHERNET"
                else -> "NONE"
            }
        }
    }

    fun isVpnActive(ctx: Context): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val net = cm.activeNetwork ?: return false
            val cap = cm.getNetworkCapabilities(net) ?: return false
            cap.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.type == ConnectivityManager.TYPE_VPN
        }
    }
}

internal object WifiUtil {
    fun ssid(ctx: Context): String? = runCatching {
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        @Suppress("DEPRECATION")
        val ssidRaw = wm.connectionInfo?.ssid
        ssidRaw?.trim('"')?.takeIf { it != "<unknown ssid>" }
    }.getOrNull()
}

internal object KeyguardUtil {
    fun isSecure(ctx: Context): Boolean {
        val km = ctx.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        return km.isKeyguardSecure
    }
}

internal object NfcUtil {
    fun state(ctx: Context): Pair<Boolean, Boolean> {
        val nfc = android.nfc.NfcAdapter.getDefaultAdapter(ctx) ?: return false to false
        return true to nfc.isEnabled
    }
}

internal object PowerUtil {
    fun powerSource(batteryIntent: Intent?): String {
        val plug = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        return when (plug) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "WIRELESS"
            else -> "BATTERY"
        }
    }
}

internal object OrientationUtil {
    fun describe(activity: Activity): String? = when (getScreenRotation(activity)) {
        Surface.ROTATION_0 -> "Portrait Up"
        Surface.ROTATION_90 -> "Landscape Left"
        Surface.ROTATION_180 -> "Portrait Down"
        Surface.ROTATION_270 -> "Landscape Right"
        else -> null
    }

    fun getScreenRotation(activity: Activity): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            (activity.getSystemService(Activity.WINDOW_SERVICE) as WindowManager)
                .defaultDisplay.rotation
        }
}

internal object ProxyUtil {
    fun isUsingProxy(): Boolean {
        val host = System.getProperty("http.proxyHost") ?: return false
        val port = System.getProperty("http.proxyPort") ?: "0"
        return host.isNotBlank() && port.toIntOrNull()?.let { it > 0 } == true
    }
}

internal object CpuUtil {
    fun maxFreqMHz(): Float {
        return try {
            // Best-effort parse of CPU0 max freq if available
            val f = File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq")
            if (f.exists()) {
                val khz = f.readText().trim().toFloatOrNull() ?: return -1f
                khz / 1000f
            } else -1f
        } catch (_: Exception) {
            -1f
        }
    }
}

internal object SessionIdProvider {
    private var cached: String? = null
    fun currentSessionId(): String = cached ?: UUID.randomUUID().toString().also { cached = it }
}

internal object SystemPropertiesUtil {
    @SuppressLint("PrivateApi")
    fun firstApiLevel(): Int? = try {
        val c = Class.forName("android.os.SystemProperties")
        val m = c.getMethod("getInt", String::class.java, Int::class.javaPrimitiveType)
        m.invoke(null, "ro.product.first_api_level", 0) as Int
    } catch (_: Throwable) {
        null
    }
}

internal object BiometricUtil {
    fun status(ctx: Context): String {
        return try {
            val bm = androidx.biometric.BiometricManager.from(ctx)
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                bm.canAuthenticate(
                    androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or
                            androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
            } else {
                @Suppress("DEPRECATION")
                bm.canAuthenticate()
            }
            when (result) {
                androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS -> "ENROLLED"
                androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "NOT_ENROLLED"
                androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "UNSUPPORTED"
                androidx.biometric.BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "UNAVAILABLE"
                else -> "UNKNOWN"
            }
        } catch (_: Throwable) {
            "UNKNOWN"
        }
    }
}

internal data class IpInfo(
    val ip: String?, val countryCode: String?, val isp: String?, val region: String?,
    val dnsIp: String?, val dnsCountry: String?, val dnsIsp: String?
)

internal object NetInfoResolver {
    var externalResolver: ((Context, String?) -> IpInfo?)? = null

    fun resolveBestEffort(ctx: Context): IpInfo? {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Active IP (best effort)
        val ip = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val net = cm.activeNetwork ?: return null
                val lp = cm.getLinkProperties(net)
                val first =
                    lp?.linkAddresses?.firstOrNull { it.address.hostAddress?.isNotBlank() == true }
                first?.address?.hostAddress
            } else {
                @Suppress("DEPRECATION")
                cm.activeNetworkInfo?.let { _ ->
                    // Fallback: grab Wi-Fi IP if possible
                    val wm =
                        ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    val ipInt = wm.connectionInfo?.ipAddress ?: 0
                    if (ipInt != 0) java.net.InetAddress.getByAddress(
                        byteArrayOf(
                            (ipInt and 0xff).toByte(),
                            (ipInt shr 8 and 0xff).toByte(),
                            (ipInt shr 16 and 0xff).toByte(),
                            (ipInt shr 24 and 0xff).toByte()
                        )
                    ).hostAddress else null
                }
            }
        } catch (_: Throwable) {
            null
        }

        // DNS servers
        val dnsIp = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val net = cm.activeNetwork
                val lp = net?.let { cm.getLinkProperties(it) }
                lp?.dnsServers?.firstOrNull()?.hostAddress
            } else null
        } catch (_: Throwable) {
            null
        }

        // Allow external enrichment (HTTP -> ISP, country, region)
        val enriched = try {
            externalResolver?.invoke(ctx, ip)
        } catch (_: Throwable) {
            null
        }

        return IpInfo(
            ip = ip,
            countryCode = enriched?.countryCode, // null if no resolver
            isp = enriched?.isp,
            region = enriched?.region,
            dnsIp = dnsIp,
            dnsCountry = enriched?.dnsCountry,
            dnsIsp = enriched?.dnsIsp
        )
    }
}

internal object CallUtil {
    fun isOnCall(ctx: Context): Boolean {
        val tm =
            ctx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return false
        // Some devices require READ_PHONE_STATE for accurate call state.
        val hasPerm = PermissionHelper.has(ctx, Manifest.permission.READ_PHONE_STATE)
        if (!hasPerm) return false

        return try {
            val isInCall = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                tm.callStateForSubscription
            } else {
                @Suppress("DEPRECATION")
                tm.callState
            }

            when (isInCall) {
                TelephonyManager.CALL_STATE_RINGING,
                TelephonyManager.CALL_STATE_OFFHOOK -> true

                else -> false
            }
        } catch (_: Throwable) {
            false
        }
    }
}

internal object LocationUtil {

    /**
     * Returns a best-effort DeviceLocation.
     * - If location permission is missing -> status = NO_PERMISSION.
     * - If no provider or no last known fix -> status = UNAVAILABLE.
     * - On success -> fills lat/lng/accuracy, reverse-geo fields when available.
     */
    @SuppressLint("MissingPermission")
    fun lastKnown(ctx: Context): DeviceLocation {
        val hasFine = PermissionHelper.has(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
        val hasCoarse = PermissionHelper.has(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (!hasFine && !hasCoarse) {
            return DeviceLocation(
                accuracy = 0,
                is_simulated = false,
                latitude = null,
                longitude = null,
                status = "NO_PERMISSION",
                zip = null, city = null, region = null, country_code = null
            )
        }

        // 1) Try Fused (no hard dependency at compile time)
        val fusedLoc = getFusedLastLocation(ctx)
        val best = fusedLoc ?: getLocationManagerLastLocation(ctx)

        if (best == null) {
            return DeviceLocation(
                accuracy = 0,
                is_simulated = false,
                latitude = null,
                longitude = null,
                status = "UNAVAILABLE",
                zip = null, city = null, region = null, country_code = null
            )
        }

        val isMock = isLocationMock(best)
        val address = reverseGeocode(ctx, best.latitude, best.longitude)

        return DeviceLocation(
            accuracy = best.accuracy.toInt(),
            is_simulated = isMock,
            latitude = best.latitude,
            longitude = best.longitude,
            status = "SUCCESS",
            zip = address?.postalCode,
            city = address?.locality ?: address?.subAdminArea,
            region = address?.adminArea,
            country_code = address?.countryCode
        )
    }

    // ------------------------------------------------------------
    // Providers
    // ------------------------------------------------------------

    /**
     * Best-effort FusedLocationProvider via reflection.
     * Returns null if Play Services is missing / Google Play not available / any error.
     *
     * This avoids adding a hard dependency on play-services-location in your library.
     * If you prefer a direct dependency, I can provide a non-reflective variant instead.
     */
    @Suppress("UNCHECKED_CAST", "NewApi")
    private fun getFusedLastLocation(ctx: Context): Location? {
        return try {
            val clazz = Class.forName("com.google.android.gms.location.LocationServices")
            val clientMethod =
                clazz.getMethod("getFusedLocationProviderClient", Context::class.java)
            val client = clientMethod.invoke(null, ctx) // FusedLocationProviderClient

            val getLastLocation = client.javaClass.getMethod("getLastLocation")
            val task = getLastLocation.invoke(client) // com.google.android.gms.tasks.Task<Location>

            // Task<Location> is async. We’ll block shortly using Tasks.await() via reflection.
            val tasksClass = Class.forName("com.google.android.gms.tasks.Tasks")
            val await =
                tasksClass.getMethod("await", Class.forName("com.google.android.gms.tasks.Task"))
            val result = await.invoke(null, task) // returns Location or null
            result as? Location
        } catch (_: Throwable) {
            null
        }
    }

    @SuppressLint("MissingPermission")
    private fun getLocationManagerLastLocation(ctx: Context): Location? {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = try {
            lm.getProviders(true)
        } catch (_: Throwable) {
            emptyList()
        }
        var best: Location? = null
        for (p in providers) {
            val loc = try {
                lm.getLastKnownLocation(p)
            } catch (_: SecurityException) {
                null
            } catch (_: Throwable) {
                null
            }
            if (loc != null && (best == null || loc.time > best.time)) best = loc
        }
        return best
    }

    // ------------------------------------------------------------
    // Mock detection (API 31+ isMock, older isFromMockProvider)
    // ------------------------------------------------------------

    @Suppress("DEPRECATION")
    private fun isLocationMock(location: Location?): Boolean {
        if (location == null) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            location.isMock
        } else {
            location.isFromMockProvider
        }
    }

    // ------------------------------------------------------------
    // Reverse geocoding (API 33+ async listener, pre-33 sync)
    // ------------------------------------------------------------

    private fun reverseGeocode(ctx: Context, lat: Double, lng: Double): Address? {
        return try {
            val geocoder = Geocoder(ctx, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // async → wait up to 2s
                var out: Address? = null
                val latch = CountDownLatch(1)
                geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        out = addresses.firstOrNull()
                        latch.countDown()
                    }

                    override fun onError(errorMessage: String?) {
                        latch.countDown()
                    }
                })
                latch.await(2, TimeUnit.SECONDS)
                out
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(lat, lng, 1)?.firstOrNull()
            }
        } catch (_: Throwable) {
            null
        }
    }
}

//internal object LocationUtil {
//    @SuppressLint("MissingPermission")
//    fun lastKnown(ctx: Context): DeviceLocation {
//        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
//            ?: return empty(success = true)
//
//        val hasFine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
//        val hasCoarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
//        if (!hasFine && !hasCoarse) return empty(success = false) // no permission
//
//        val providers = lm.getProviders(true)
//        var best: android.location.Location? = null
//        for (p in providers) {
//            val loc = try { lm.getLastKnownLocation(p) } catch (_: SecurityException) { null } catch (_: Throwable) { null }
//            if (loc != null && (best == null || loc.time > best.time)) best = loc
//        }
//
//        if (best == null) return empty(success = false)
//
//        val isMock = isLocationMock(best)
//
//        // Reverse region/zip (best-effort; may be slow / require network)
//        var zip: String? = null
//        var city: String? = null
//        var region: String? = null
//        var country: String? = null
//        try {
//            val geocoder = android.location.Geocoder(ctx, Locale.getDefault())
//            val res = geocoder.getFromLocation(best.latitude, best.longitude, 1)
//            if (!res.isNullOrEmpty()) {
//                val a = res[0]
//                zip = a.postalCode
//                city = a.locality ?: a.subAdminArea
//                region = a.adminArea
//                country = a.countryCode
//            }
//        } catch (_: Throwable) { /* ignore */ }
//
//        return DeviceLocation(
//            accuracy = best.accuracy.toInt(),
//            is_simulated = isMock,
//            latitude = best.latitude,
//            longitude = best.longitude,
//            status = "SUCCESS",
//            zip = zip,
//            city = city,
//            region = region,
//            country_code = country
//        )
//    }
//
//    @Suppress("DEPRECATION")
//    private fun isLocationMock(location: android.location.Location?): Boolean {
//        if (location == null) return false
//
//        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//            location.isMock // API 31+
//        } else {
//            location.isFromMockProvider // Deprecated but still works pre-S
//        }
//    }
//
//    private fun empty(success: Boolean) = DeviceLocation(
//        accuracy = 0,
//        is_simulated = false,
//        latitude = null,
//        longitude = null,
//        status = if (success) "SUCCESS" else "NO_PERMISSION",
//        zip = null, city = null, region = null, country_code = null
//    )
//}

internal object CloneUtil {
    @SuppressLint("PrivateApi")
    fun isCloned(ctx: Context): Boolean {
        // 1) User/profile check: cloned / work-profile apps often run under userId != 0
        val userId = try {
            val m = android.os.UserHandle::class.java.getDeclaredMethod("myUserId")
            (m.invoke(null) as? Int) ?: 0
        } catch (_: Throwable) {
            0
        }

        if (userId > 0) return true

        // 2) Data dir path hint: many vendors put clones in user/999 or similar
        val dataDir = try {
            ctx.applicationInfo.dataDir ?: ""
        } catch (_: Throwable) {
            ""
        }
        if (dataDir.contains("/user/999") || dataDir.contains("/user_de/999")) return true

        // 3) Package/code path hints
        val codePath = ctx.packageCodePath
        if (codePath.contains("/999/")) return true

        // 4) Known cloner packages present on device
        val cloneManagers = listOf(
            "com.parallel.space", "com.parallel.space.lite", "com.lbe.parallel.intl",
            "com.dualspace.multid.accounts", "com.oem.cloneapp", "com.oplus.clonephone",
            "com.huawei.android.clone", "com.miui.securitycore"
        )
        val pm = ctx.packageManager
        for (pkg in cloneManagers) {
            if (isPackageInstalled(pm, pkg)) return true
        }

        // 5) Process name suffix (very heuristic)
        val procName = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Application.getProcessName()
            else ActivityManager::class.java
                .getMethod("getMyMemoryState", ActivityManager.RunningAppProcessInfo::class.java)
                .let { android.os.Process.myPid().toString() } // fallback (no reliable name pre-P)
        } catch (_: Throwable) {
            null
        }
        return procName?.contains(":clone", ignoreCase = true) == true
    }

    private fun isPackageInstalled(pm: PackageManager, pkg: String): Boolean =
        try {
            pm.getPackageInfo(pkg, 0); true
        } catch (_: Exception) {
            false
        }
}