package com.example.deviceinfotest

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Base64
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.InetAddress
import java.security.MessageDigest
import java.util.Locale
import java.util.TimeZone

object DeviceInfoCollector {

    /**
     * One-call API for collecting info *and* handling any permissions.
     * @param activity Activity context
     * @param permissions Permissions needed (array)
     * @param onSuccess Called with Base64 info string if permissions granted
     * @param onPermissionDenied Called with denied & permanentlyDenied lists if permissions missing
     */
    fun collectWithPermissions(
        activity: Activity,
        permissions: Array<String>,
        onSuccess: (String) -> Unit,
        onPermissionDenied: (denied: List<String>, permanentlyDenied: List<String>) -> Unit
    ) {
        CoroutineScope(Dispatchers.Main).launch {
            val (allGranted, denied, _) = PermissionHelper.checkPermissions(activity, permissions)
            when {
                allGranted -> {
                    val result = withContext(Dispatchers.IO) { collectInfoBase64(activity) }
                    Logger.log("DeviceInfoLib: All permissions granted... $result")
                    onSuccess(result)
                }
                denied.isNotEmpty() -> {
                    // Always ask permissions FIRST if denied, regardless of permanentlyDenied status.
                    PermissionHelper.requestPermissions(activity, permissions, object : PermissionHelper.PermissionResultListener {
                        override fun onResult(granted: Boolean, denied: List<String>, permanentlyDenied: List<String>) {
                            if (granted) {
                                CoroutineScope(Dispatchers.Main).launch {
                                    val result = withContext(Dispatchers.IO) { collectInfoBase64(activity) }
                                    Logger.log("DeviceInfoLib: All permissions granted... $result")
                                    onSuccess(result)
                                }
                            } else if (permanentlyDenied.isNotEmpty()) {
                                showSettingsPopup(activity, permanentlyDenied) {
                                    onPermissionDenied(denied, permanentlyDenied)
                                }
                            } else {
                                onPermissionDenied(denied, permanentlyDenied)
                            }
                        }
                    })
                }
                else -> {
                    // This else may never be hit; can be omitted.
                }
            }
        }
    }

    // Standard info collection code
    private fun collectInfoBase64(activity: Activity): String {
        return try {
            val info = DeviceInfoDataUtil.collect(activity)
            val json = info.toJson()
            Logger.log("Collected data: $json")
            Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        } catch (e: Exception) {
            Logger.log("Error: ${e.message}")
            ""
        }
    }

    // Show a custom dialog to guide user to settings
    private fun showSettingsPopup(activity: Activity, permissions: List<String>, onClosed: () -> Unit) {
        val permissionsString = PermissionHelper.getPermissionsString(permissions)
        val message = "Some permissions required for full functionality are permanently denied. Please open App Settings and allow them. $permissionsString"
        AlertDialog.Builder(activity)
            .setTitle("Permission Needed")
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Open Settings") { _, _ ->
                PermissionHelper.openAppSettings(activity)
                onClosed()
            }
            .setNegativeButton("Cancel") { _, _ ->
                onClosed()
            }
            .show()
    }
}
