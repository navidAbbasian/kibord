package com.navidabbasian.kibord.games.nofoozi.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import java.net.Inet4Address
import java.net.NetworkInterface

/** یک بازیِ نفوذی پیداشده در شبکه */
data class NfDiscoveredGame(
    val hostName: String,
    val address: String,
    val port: Int,
)

/**
 * ثبت و کشف بازی نفوذی روی شبکه‌ی محلی — میزبان سرویس را ثبت می‌کند و
 * مهمان‌ها خودکار پیدایش می‌کنند. همه‌ی callback ها روی نخ اصلی می‌آیند.
 */
class NfNsd(context: Context) {

    private val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val main = Handler(Looper.getMainLooper())

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    // ---- سمت میزبان ----

    fun register(hostPlayerName: String, port: Int) {
        unregister()
        val info = NsdServiceInfo().apply {
            serviceName = "$SERVICE_NAME_PREFIX$hostPlayerName"
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
        onFound: (NfDiscoveredGame) -> Unit,
        onLost: (serviceName: String) -> Unit,
    ) {
        stopDiscovery()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onDiscoveryStopped(serviceType: String) {}

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!serviceInfo.serviceName.startsWith(SERVICE_NAME_PREFIX)) return
                try {
                    @Suppress("DEPRECATION")
                    nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {}
                        override fun onServiceResolved(info: NsdServiceInfo) {
                            val host = info.host as? Inet4Address ?: (info.host ?: return)
                            val game = NfDiscoveredGame(
                                hostName = info.serviceName.removePrefix(SERVICE_NAME_PREFIX),
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
                main.post { onLost(serviceInfo.serviceName.removePrefix(SERVICE_NAME_PREFIX)) }
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
        const val SERVICE_NAME_PREFIX = "nofoozi-"

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
