package com.ibero.arenapokemon

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import java.net.Inet4Address
import java.util.concurrent.atomic.AtomicBoolean

// Busca en la red local el servidor que se anuncia por Bonjour (mDNS) como _arenapokemon._tcp,
// para no tener que escribir su IP. `alEncontrar` recibe "ip:puerto" en el hilo principal.
class BuscadorServidor(contexto: Context, private val alEncontrar: (String) -> Unit) {
    private val nsd = contexto.getSystemService(NsdManager::class.java)
    private val principal = Handler(Looper.getMainLooper())
    private var buscando = false
    // NsdManager resuelve un servicio a la vez.
    private val resolviendo = AtomicBoolean(false)

    private val oyente = object : NsdManager.DiscoveryListener {
        override fun onServiceFound(servicio: NsdServiceInfo) = resolver(servicio)
        override fun onDiscoveryStarted(tipo: String) {}
        override fun onDiscoveryStopped(tipo: String) {}
        override fun onServiceLost(servicio: NsdServiceInfo) {}
        override fun onStartDiscoveryFailed(tipo: String, codigo: Int) {}
        override fun onStopDiscoveryFailed(tipo: String, codigo: Int) {}
    }

    fun iniciar() {
        if (buscando) return
        buscando = true
        nsd.discoverServices(TIPO, NsdManager.PROTOCOL_DNS_SD, oyente)
    }

    fun detener() {
        if (!buscando) return
        buscando = false
        runCatching { nsd.stopServiceDiscovery(oyente) }
    }

    // resolveService y host son la API disponible desde Android 7 (minSdk 24).
    @Suppress("DEPRECATION")
    private fun resolver(servicio: NsdServiceInfo) {
        if (!resolviendo.compareAndSet(false, true)) return
        nsd.resolveService(servicio, object : NsdManager.ResolveListener {
            override fun onServiceResolved(info: NsdServiceInfo) {
                val ip = (info.host as? Inet4Address)?.hostAddress
                if (ip != null) {
                    principal.post { alEncontrar("$ip:${info.port}") }
                }
                resolviendo.set(false)
            }

            override fun onResolveFailed(info: NsdServiceInfo, codigo: Int) {
                resolviendo.set(false)
            }
        })
    }

    private companion object {
        const val TIPO = "_arenapokemon._tcp"
    }
}
