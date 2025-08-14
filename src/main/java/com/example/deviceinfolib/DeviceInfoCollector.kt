package com.example.deviceinfolib

import android.Manifest
import android.app.Activity
import android.util.Base64
import androidx.annotation.MainThread
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object DeviceInfoCollector {

    /**
     * Collects a Base64(JSON) payload in one shot.
     * - If everything needed is already granted: collects immediately.
     * - Otherwise: requests all missing permissions once, then collects and returns.
     *
     * Logging is enabled automatically when the host app is debuggable.
     *
     * @param activity            Activity context (must be foreground; main thread)
     * @param enableLocationInfo  If true, requests ACCESS_FINE_LOCATION to enrich location & Wi-Fi SSID
     * @param enableNetworkStateInfo If true, requests READ_PHONE_STATE to enrich telephony/call state
     * @param enableManualActions If true, shows a “Go to Settings” dialog when permissions are permanently denied
     * @param onSuccess           Called exactly once with the Base64 payload
     * @param onPermissionDenied  Optional: called with lists of denied & permanently denied permissions
     */
    @MainThread
    fun collect(
        activity: Activity,
        enableLocationInfo: Boolean = false,
        enableNetworkStateInfo: Boolean = false,
        enableManualActions: Boolean = false,
        onSuccess: (String) -> Unit,
        onPermissionDenied: ((denied: List<String>, permanentlyDenied: List<String>) -> Unit)? = null
    ) {
        val isHostAppDebuggable =
            (activity.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        Logger.enabled = isHostAppDebuggable

        val scope = CoroutineScope(Dispatchers.Main)
        val requiredPermissions = mutableListOf<String>().apply {
            if (enableLocationInfo) add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (enableNetworkStateInfo) add(Manifest.permission.READ_PHONE_STATE)
        }
        scope.launch {
            // 1) Figure out which permissions are actually missing now
            val (allGrantedNow, deniedNow, permanentlyDeniedNow) =
                PermissionHelper.checkPermissions(activity, requiredPermissions.toTypedArray())

            val missing = if (allGrantedNow) emptyList()
            else (deniedNow + permanentlyDeniedNow).distinct()

            // 2) Nothing to request? Collect baseline immediately!!!
            if (missing.isEmpty()) {
                // All already granted; baseline might already include everything.
                val baseline = withContext(Dispatchers.IO) { collectInfoBase64(activity) }
                Logger.log("DeviceInfoLib: baseline payload ready.")
                onSuccess(baseline)
                return@launch
            }

            // 3) Request all missing permissions ONCE
            PermissionHelper.requestPermissions(
                activity,
                missing.toTypedArray(),
                object : PermissionHelper.PermissionResultListener {
                    override fun onResult(
                        granted: Boolean,                     // true when ALL requested were granted
                        denied: List<String>,                 // denied after request
                        permanentlyDenied: List<String>       // permanently denied after request
                    ) {
                        // Did we get at least one new permission?
                        // requested - (stillDenied + stillPermanentlyDenied) > 0  => some granted
                        // val grantedCount = missing.size - (denied.size + permanentlyDenied.size)
                        // val anyNewGranted = granted || (grantedCount > 0)

                        // if (anyNewGranted) {
                        // 4) Re-collect once with whatever was granted
                        scope.launch {
                            val enriched =
                                withContext(Dispatchers.IO) { collectInfoBase64(activity) }
                            Logger.log("DeviceInfoLib: enriched payload ready (batch request).")
                            onSuccess(enriched)
                        }
                        // }

                        // 5) Report denials (optional)
                        if (denied.isNotEmpty() || permanentlyDenied.isNotEmpty()) {
                            onPermissionDenied?.invoke(denied, permanentlyDenied)
                        }

                        // 6) Show popup to inform about permanently denied permissions with the
                        //    ability to redirect to the settings to allow those manually. (optional)
                        if (permanentlyDenied.isNotEmpty() && enableManualActions) {
                            showSettingsPopup(activity, permanentlyDenied) {
                                onPermissionDenied?.invoke(denied, permanentlyDenied)
                            }
                        }
                    }
                }
            )
        }
    }

    private fun collectInfoBase64(activity: Activity): String {
        return try {
            val details = DeviceInfoDataUtil.collectDeviceDetails(activity)
            val json = details.toJsonString() // matches your schema
            Logger.log("DeviceInfoLib: collect success: $json")
            Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        } catch (t: Throwable) {
            Logger.log("DeviceInfoLib: collect failed: ${t.message}")
            ""
        }
    }

    // Show a custom dialog to guide user to settings
    private fun showSettingsPopup(
        activity: Activity,
        permissions: List<String>,
        onClosed: () -> Unit
    ) {
        val permissionsString = PermissionHelper.getPermissionsString(permissions)
        val message = "Some permissions required for full functionality are permanently denied. " +
                "Please open App Settings and allow them. $permissionsString"
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
