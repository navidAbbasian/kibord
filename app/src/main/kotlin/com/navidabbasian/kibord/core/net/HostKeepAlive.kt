package com.navidabbasian.kibord.core.net

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager

/**
 * در حین میزبانیِ بازی شبکه‌ای، وای‌فای و CPU را بیدار نگه می‌دارد تا اگر میزبان
 * گوشی را قفل کند (صفحه خاموش شود) اتصالِ بازیکنان قطع نشود.
 *
 * با شروع میزبانی [acquire] و با پایان/خروج [release] صدا زده می‌شود.
 * نیازمند مجوز WAKE_LOCK در مانیفست است.
 */
class HostKeepAlive(context: Context) {

    private val appContext = context.applicationContext
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    fun acquire() {
        if (wifiLock != null || wakeLock != null) return
        try {
            val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiLock = wifi?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "kibord:host")?.apply {
                setReferenceCounted(false)
                acquire()
            }
            val power = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            @Suppress("WakelockTimeout") // تا پایانِ میزبانی نگه داشته می‌شود و در release آزاد می‌گردد
            wakeLock = power?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "kibord:host")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (_: Exception) {
        }
    }

    fun release() {
        try {
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (_: Exception) {
        }
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {
        }
        wifiLock = null
        wakeLock = null
    }
}
