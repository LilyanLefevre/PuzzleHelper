package com.lilyan_lefevre.puzzleit.feature.account.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Whether the app agrees to send a password to [address]: https anywhere, but plain http only on a home network (private ranges, localhost,
 * *.local, Tailscale), so a password never crosses the internet in clear.
 */
fun isAcceptableServer(address: String): Boolean {
    val url = address.trim().toHttpUrlOrNull() ?: return false
    if (url.isHttps) return true
    val host = url.host
    if (host == "localhost" || host.endsWith(".local")) return true
    val parts = host.split('.')
    val n = parts.mapNotNull { it.toIntOrNull() }
    if (parts.size != 4 || n.size != 4) return false
    return n[0] == 10 || n[0] == 127 || (n[0] == 192 && n[1] == 168) || (n[0] == 172 && n[1] in 16..31) || (n[0] == 100 && n[1] in 64..127)
}
