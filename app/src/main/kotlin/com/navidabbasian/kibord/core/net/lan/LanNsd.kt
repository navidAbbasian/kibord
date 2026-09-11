package com.navidabbasian.kibord.core.net.lan

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import java.net.Inet4Address
import java.net.NetworkInterface

/** یک بازیِ پیداشده در شبکه‌ی محلی */
data class LanDiscoveredGame(
    val hostName: String,
    val address: String,
    val port: Int,
)

/**
 * ثبت و کشف بازی روی شبکه‌ی محلی، مشترک بین بازی‌ها — هر بازی با پیشوند
 * خودش ([namePrefix]) سرویس را ثبت می‌کند تا مهمان فقط میزبان‌های همان
 * بازی را ببیند. همه‌ی callback ها روی نخ اصلی می‌آیند.
 */
class LanNsd(context: Context, private val namePrefix: String) {

    private val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val main = Handler(Looper.getMainLooper())

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    // ---- سمت میزبان ----

    fun register(hostPlayerName: String, port: Int) {
        unregister()
        val info = NsdServiceInfo().apply {
            serviceName = "$namePrefix$hostPlayerName"
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {}
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }
        registrationListener = listener
        try {
            nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (_: Exception) {
        }
    }

    fun unregister() {
        registrationListener?.let {
            try {
                nsd.unregisterService(it)
            } catch (_: Exception) {
            }
        }
        registrationListener = null
    }

    // ---- سمت مهمان ----

    fun discover(
        onFound: (LanDiscoveredGame) -> Unit,
        onLost: (hostName: String) -> Unit,
    ) {
        stopDiscovery()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onDiscoveryStopped(serviceType: String) {}

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!serviceInfo.serviceName.startsWith(namePrefix)) return
                try {
                    @Suppress("DEPRECATION")
                    nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {}
                        override fun onServiceResolved(info: NsdServiceInfo) {
                            val host = info.host as? Inet4Address ?: (info.host ?: return)
                            val game = LanDiscoveredGame(
                                hostName = info.serviceName.removePrefix(namePrefix),
                                address = host.hostAddress ?: return,
                                port = info.port,
                            )
                            main.post { onFound(game) }
                        }
                    })
                } catch (_: Exception) {
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                if (!serviceInfo.serviceName.startsWith(namePrefix)) return
                main.post { onLost(serviceInfo.serviceName.removePrefix(namePrefix)) }
            }
        }
        discoveryListener = listener
        try {
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (_: Exception) {
        }
    }

    fun stopDiscovery() {
        discoveryListener?.let {
            try {
                nsd.stopServiceDiscovery(it)
            } catch (_: Exception) {
            }
        }
        discoveryListener = null
    }

    fun release() {
        unregister()
        stopDiscovery()
    }

    companion object {
        const val SERVICE_TYPE = "_kibord._tcp."

        /** آدرس محلی این گوشی در شبکه — برای نمایش در لابی و اتصال دستی */
        fun localIpAddress(): String? = try {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }
                ?.hostAddress
        } catch (_: Exception) {
            null
        }
    }
}
