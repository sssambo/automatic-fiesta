package com.multispace.core.util

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.net.wifi.WifiManager
import android.util.Log

/**
 * Retrieves real device identifiers from Android system
 * Replaces fake UUID-based ID generation
 * - Android ID: System device identifier
 * - IMEI: International Mobile Equipment Identity (SIM card)
 * - MAC Address: WiFi hardware address
 * - Serial: Device serial number
 * - Build Fingerprint: Device model/version fingerprint
 */
class DeviceIdentifierHelper(private val context: Context) {
    private val TAG = "DeviceIdentifierHelper"
    
    /**
     * Get real Android ID from Settings.Secure
     * Format: 16-character hex string (unique per device, per Android ID)
     */
    fun getAndroidId(): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
    }
    
    /**
     * Get real IMEI from TelephonyManager
     * Format: 15-digit number
     * Requires: READ_PHONE_STATE permission
     */
    fun getImei(): String {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val imeis = mutableListOf<String>()
                repeat(telephonyManager.phoneCount) { slotId ->
                    try {
                        val imei = telephonyManager.getImei(slotId)
                        if (imei != null && imei.isNotEmpty()) {
                            imeis.add(imei)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to get IMEI for slot $slotId", e)
                    }
                }
                imeis.firstOrNull() ?: ""
            } else {
                @Suppress("DEPRECATION")
                telephonyManager.deviceId ?: ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting IMEI", e)
            ""
        }
    }
    
    /**
     * Get real MAC address from WifiManager
     * Format: XX:XX:XX:XX:XX:XX
     * Note: On Android 6+, requires proper permissions
     */
    fun getMacAddress(): String {
        return try {
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val connectionInfo = wifiManager.connectionInfo
            connectionInfo?.macAddress ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Error getting MAC address", e)
            ""
        }
    }
    
    /**
     * Get real device serial number
     * Format: Manufacturer-specific (usually alphanumeric, 8-20 chars)
     */
    fun getSerialNumber(): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Build.getSerial()
            } else {
                @Suppress("DEPRECATION")
                Build.SERIAL
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting serial number", e)
            ""
        }
    }
    
    /**
     * Get real device build fingerprint
     * Format: brand/product/device:version/build_id
     * Example: google/Pixel7/Pixel7:14/TP1A.220624.014
     */
    fun getBuildFingerprint(): String {
        return Build.FINGERPRINT
    }
    
    /**
     * Get device model info for anti-detection
     */
    fun getDeviceModel(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL}"
    }
}
