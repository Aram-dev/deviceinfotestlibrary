package com.example.deviceinfolib

import org.json.JSONArray
import org.json.JSONObject

data class DeviceLocation(
    val accuracy: Int? = null,
    val is_simulated: Boolean? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val status: String? = null,
    val zip: String? = null,
    val city: String? = null,
    val region: String? = null,
    val country_code: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("accuracy", accuracy)
        put("is_simulated", is_simulated)
        put("latitude", latitude)
        put("longitude", longitude)
        put("status", status)
        put("zip", zip)
        put("city", city)
        put("region", region)
        put("country_code", country_code)
    }
}

data class DeviceDetails(
    val android_id: String?,
    val app_instance_id: String,
    val android_version: String,
    val app_guid: String,
    val audio_mute_status: Boolean,
    val audio_volume_current: Int,
    val battery_charging: Boolean,
    val battery_health: String,
    val battery_level: Int?, // nullable in our collector if not derivable
    val battery_temperature: Double,
    val battery_voltage: Int,
    val biometric_status: String,
    val bootloader_state: String,
    val build_device: String,
    val build_id: String,
    val build_manufacturer: String,
    val build_model: String,
    val build_number: String,
    val build_time: Long, // seconds
    val carrier_country: String?,
    val carrier_name: String?,
    val cpu_count: Int,
    val cpu_hash: String,
    val cpu_speed: Float, // -1.0f if unknown
    val cpu_type: String,
    val developer_options_state: String,
    val device_cellular_id: String?,
    val device_hash: String,
    val device_ip_address: String?,
    val device_ip_country: String?,
    val device_ip_isp: String?,
    val device_ip_region: String?,
    val device_name: String,
    val device_orientation: String?,
    val dns_ip_country: String?,
    val dns_ip_isp: String?,
    val dns_ip: String?,
    val free_storage: Long,
    val gsf_id: String?,
    val has_proximity_sensor: Boolean,
    val interfering_apps: List<String>,
    val is_click_automator_installed: Boolean,
    val is_emulator: Boolean,
    val is_keyguard_secure: Boolean,
    val is_nfc_available: Boolean,
    val is_nfc_enabled: Boolean,
    val is_on_call: Boolean,
    val is_remote_control_connected: Boolean,
    val is_rooted: Boolean,
    val is_screen_being_mirrored: Boolean,
    val kernel_arch: String,
    val kernel_name: String,
    val kernel_version: String,
    val last_boot_time: Long,
    val network_config: String, // WIFI / CELLULAR / NONE
    val pasteboard_hash: String,
    val physical_memory: Long,
    val region_country: String?,
    val region_language: String?,
    val region_timezone: String,
    val remote_control_provider: String?,
    val screen_brightness: Int, // 0..100
    val screen_height: Int,
    val screen_scale: Int,
    val screen_width: Int,
    val sensor_hash: String,
    val session_id: String,
    val source: String,
    val system_uptime: Int,
    val timezone_identifier: String,
    val total_storage: Long,
    val type: String,
    val usb_cable_state: String,
    val usb_debugging_state: String, // UNKNOWN for normal apps
    val wifi_mac_address: String?,
    val wifi_ssid: String?,
    val first_api_level: Int,
    val power_source: String, // AC / USB / WIRELESS / BATTERY
    val proxy_address: String?,
    val proxy_state: String, // CONNECTED / NOT_CONNECTED
    val vpn_state: String,
    val suspicious_flags: List<String>,
    val true_device_id: String,
    val system_integrity: String, // COMPROMISED / OK
    val is_app_cloned: Boolean,
    val device_location: DeviceLocation?
) {
    fun toJson(): JSONObject = JSONObject().apply {
        fun putSafe(key: String, value: Any?) = put(key, value)

        putSafe("android_id", android_id)
        putSafe("app_instance_id", app_instance_id)
        putSafe("android_version", android_version)
        putSafe("app_guid", app_guid)
        putSafe("audio_mute_status", audio_mute_status)
        putSafe("audio_volume_current", audio_volume_current)
        putSafe("battery_charging", battery_charging)
        putSafe("battery_health", battery_health)
        putSafe("battery_level", battery_level)
        putSafe("battery_temperature", battery_temperature)
        putSafe("battery_voltage", battery_voltage)
        putSafe("biometric_status", biometric_status)
        putSafe("bootloader_state", bootloader_state)
        putSafe("build_device", build_device)
        putSafe("build_id", build_id)
        putSafe("build_manufacturer", build_manufacturer)
        putSafe("build_model", build_model)
        putSafe("build_number", build_number)
        putSafe("build_time", build_time)
        putSafe("carrier_country", carrier_country)
        putSafe("carrier_name", carrier_name)
        putSafe("cpu_count", cpu_count)
        putSafe("cpu_hash", cpu_hash)
        putSafe("cpu_speed", cpu_speed)
        putSafe("cpu_type", cpu_type)
        putSafe("developer_options_state", developer_options_state)
        putSafe("device_cellular_id", device_cellular_id)
        putSafe("device_hash", device_hash)
        putSafe("device_ip_address", device_ip_address)
        putSafe("device_ip_country", device_ip_country)
        putSafe("device_ip_isp", device_ip_isp)
        putSafe("device_ip_region", device_ip_region)
        putSafe("device_name", device_name)
        putSafe("device_orientation", device_orientation)
        putSafe("dns_ip_country", dns_ip_country)
        putSafe("dns_ip_isp", dns_ip_isp)
        putSafe("dns_ip", dns_ip)
        putSafe("free_storage", free_storage)
        putSafe("gsf_id", gsf_id)
        putSafe("has_proximity_sensor", has_proximity_sensor)
        putSafe("interfering_apps", JSONArray(interfering_apps))
        putSafe("is_click_automator_installed", is_click_automator_installed)
        putSafe("is_emulator", is_emulator)
        putSafe("is_keyguard_secure", is_keyguard_secure)
        putSafe("is_nfc_available", is_nfc_available)
        putSafe("is_nfc_enabled", is_nfc_enabled)
        putSafe("is_on_call", is_on_call)
        putSafe("is_remote_control_connected", is_remote_control_connected)
        putSafe("is_rooted", is_rooted)
        putSafe("is_screen_being_mirrored", is_screen_being_mirrored)
        putSafe("kernel_arch", kernel_arch)
        putSafe("kernel_name", kernel_name)
        putSafe("kernel_version", kernel_version)
        putSafe("last_boot_time", last_boot_time)
        putSafe("network_config", network_config)
        putSafe("pasteboard_hash", pasteboard_hash)
        putSafe("physical_memory", physical_memory)
        putSafe("region_country", region_country)
        putSafe("region_language", region_language)
        putSafe("region_timezone", region_timezone)
        putSafe("remote_control_provider", remote_control_provider)
        putSafe("screen_brightness", screen_brightness)
        putSafe("screen_height", screen_height)
        putSafe("screen_scale", screen_scale)
        putSafe("screen_width", screen_width)
        putSafe("sensor_hash", sensor_hash)
        putSafe("session_id", session_id)
        putSafe("source", source)
        putSafe("system_uptime", system_uptime)
        putSafe("timezone_identifier", timezone_identifier)
        putSafe("total_storage", total_storage)
        putSafe("type", type)
        putSafe("usb_cable_state", usb_cable_state)
        putSafe("usb_debugging_state", usb_debugging_state)
        putSafe("wifi_mac_address", wifi_mac_address)
        putSafe("wifi_ssid", wifi_ssid)
        putSafe("first_api_level", first_api_level)
        putSafe("power_source", power_source)
        putSafe("proxy_address", proxy_address)
        putSafe("proxy_state", proxy_state)
        putSafe("vpn_state", vpn_state)
        putSafe("suspicious_flags", JSONArray(suspicious_flags))
        putSafe("true_device_id", true_device_id)
        putSafe("system_integrity", system_integrity)
        putSafe("is_app_cloned", is_app_cloned)
        putSafe("device_location", device_location?.toJson())
    }

    fun toJsonString(): String = toJson().let { JSONObject().put("device_details", it).toString() }
}
