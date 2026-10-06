package com.lemon.yingshi.tv.util

import java.net.Inet4Address
import java.net.NetworkInterface

object LanAddresses {
    fun ipv4(): List<String> {
        val found = mutableListOf<String>()
        val interfaces = runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        }.getOrDefault(emptyList())
        for (nic in interfaces) {
            val usable = runCatching { nic.isUp && !nic.isLoopback }.getOrDefault(false)
            if (!usable) continue
            for (address in nic.inetAddresses.toList()) {
                if (address is Inet4Address &&
                    !address.isLoopbackAddress &&
                    !address.isLinkLocalAddress
                ) {
                    found += address.hostAddress ?: continue
                }
            }
        }
        return found.distinct().sortedBy { rank(it) }
    }

    fun listenUrls(port: Int): List<String> =
        ipv4().map { "http://$it:$port/" }

    private fun rank(ip: String): Int = when {
        ip.startsWith("192.168.") -> 0
        ip.startsWith("10.") -> 1
        ip.startsWith("172.") -> 2
        else -> 3
    }
}
