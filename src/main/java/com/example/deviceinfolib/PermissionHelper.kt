package com.example.deviceinfolib

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object PermissionHelper {
    const val PERMISSION_REQUEST_CODE = 2025
    val PERMISSIONS = mapOf("android.permission.ACCESS_FINE_LOCATION" to "Location")

    interface PermissionResultListener {
        fun onResult(granted: Boolean, denied: List<String>, permanentlyDenied: List<String>)
    }

    private var listener: PermissionResultListener? = null
    private var requestedPermissions: Array<String> = arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)

    // Call to request any set of permissions
    fun requestPermissions(
        activity: Activity,
        permissions: Array<String>?,
        cb: PermissionResultListener
    ) {
        listener = cb
        ActivityCompat.requestPermissions(activity, permissions ?: requestedPermissions, PERMISSION_REQUEST_CODE)
    }

    // Checks the status of each permission in the array
    fun checkPermissions(
        context: Activity,
        permissions: Array<String>
    ): Triple<Boolean, List<String>, List<String>> {
        val denied = mutableListOf<String>()
        val permanentlyDenied = mutableListOf<String>()
        var allGranted = true
        for (permission in permissions) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                permission
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                allGranted = false
                denied.add(permission)
                // Check if permanently denied
                if (!ActivityCompat.shouldShowRequestPermissionRationale(context, permission)) {
                    permanentlyDenied.add(permission)
                }
            }
        }
        return Triple(allGranted, denied, permanentlyDenied)
    }

    // Call from onRequestPermissionsResult
    fun handlePermissionResult(
        activity: Activity,
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val denied = mutableListOf<String>()
            val permanentlyDenied = mutableListOf<String>()
            for (i in permissions.indices) {
                val permission = permissions[i]
                val granted = grantResults[i] == PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    denied.add(permission)
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(
                            activity,
                            permission
                        )
                    ) {
                        permanentlyDenied.add(permission)
                    }
                }
            }
            listener?.onResult(denied.isEmpty(), denied, permanentlyDenied)
            listener = null
        }
    }

    // Utility: Launch app settings
    fun openAppSettings(activity: Activity) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.fromParts("package", activity.packageName, null)
        activity.startActivity(intent)
    }

    fun getPermissionsString(permissions: List<String>): String {
        val permissionsString =
            StringBuilder().append(
                permissions.joinToString(
                    "\n- ",
                    prefix = "\n- ",
                    postfix = "\n"
                ) { PERMISSIONS[it].toString() })
        return permissionsString.toString()
    }

    fun has(ctx: Context, permission: String) =
        ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED
}
