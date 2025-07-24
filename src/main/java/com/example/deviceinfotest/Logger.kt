package com.example.deviceinfotest

import android.util.Log

object Logger {
    var enabled = false
    fun log(message: String) {
        if (enabled) Log.d("DeviceInfo", message)
    }
}