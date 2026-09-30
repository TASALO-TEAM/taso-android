package com.tasalo.android.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object UrlValidator {
    /**
     * Valida la URL base configurable (plan §7): solo `https`, host no vacío.
     * Devuelve la URL normalizada con `/` final, o null si no es válida.
     */
    fun normalize(input: String): String? {
        val trimmed = input.trim()
        if (!trimmed.startsWith("https://", ignoreCase = true)) return null
        val url = trimmed.toHttpUrlOrNull() ?: return null
        if (url.scheme != "https" || url.host.isBlank()) return null
        val text = url.toString()
        return if (text.endsWith("/")) text else "$text/"
    }
}

object NetworkMonitor {
    /** true si hay una red activa con salida a Internet (no garantiza que la API responda). */
    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
