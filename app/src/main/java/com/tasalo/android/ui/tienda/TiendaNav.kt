package com.tasalo.android.ui.tienda

import java.net.URI

/**
 * Reglas de navegación de la pestaña Tienda (taso-inv dentro de un WebView). Puras, sin Android, para poder probarlas.
 *
 * Dentro de la app solo se navega por el dominio de taso-inv y por la pantalla de inicio de sesión de Telegram
 * (el login redirige a oauth.telegram.org y vuelve al dominio con una cookie: si saliera al navegador, la cookie quedaría
 * en otro sitio y el acceso fallaría). Cualquier otro enlace sale al sistema.
 */
object TiendaNav {
    const val INV_HOST = "camiersus.duckdns.org"
    const val INV_URL = "https://$INV_HOST/"

    private val TELEGRAM_LOGIN_HOSTS = setOf("oauth.telegram.org")
    private val OUTSIDE_SCHEMES = setOf("https", "http", "tg", "mailto", "tel")

    fun host(url: String?): String? {
        if (url == null) return null
        return runCatching { URI(url).host?.lowercase() }.getOrNull()
    }

    private fun isHttps(url: String?): Boolean = url?.startsWith("https://", ignoreCase = true) == true

    /** ¿Es una página de taso-inv? Se usa para dejar que solo ella use el puente nativo. */
    fun isInv(url: String?): Boolean = isHttps(url) && host(url) == INV_HOST

    /** ¿Se carga dentro del WebView (true) o se entrega al sistema (false)? */
    fun staysInside(url: String?): Boolean = isInv(url) || (isHttps(url) && host(url) in TELEGRAM_LOGIN_HOSTS)

    /** Esquemas que el sistema puede abrir sin riesgo (Telegram, correo, teléfono, web). El resto se ignora. */
    fun canOpenOutside(url: String?): Boolean {
        if (url == null) return false
        val scheme = runCatching { URI(url).scheme?.lowercase() }.getOrNull() ?: return false
        return scheme in OUTSIDE_SCHEMES
    }
}
