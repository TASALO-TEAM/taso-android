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

    /** Página de taso-inv que hace el login con Google en el navegador del sistema (taso-inv: web/google-app.html). */
    const val GOOGLE_APP_PATH = "/google-app.html"

    // El servidor genera el código con token_urlsafe(32): 43 caracteres base64url. Se acepta un margen, nada más.
    private val GOOGLE_CODE = Regex("[A-Za-z0-9_-]{20,128}")

    /**
     * ¿Es la página de Google de taso-inv? (https, nuestro dominio, sin puerto raro ni credenciales, ruta exacta.)
     * Es lo único que el puente `googleLogin` abre en el navegador: la web no puede mandar la app a cualquier sitio.
     */
    fun isGoogleBrowserUrl(url: String?): Boolean {
        if (url == null || !isInv(url)) return false
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return (uri.port == -1 || uri.port == 443) && uri.userInfo == null && uri.path == GOOGLE_APP_PATH
    }

    /** ¿Tiene la forma de un código de un solo uso de taso-inv? Se comprueba antes de meterlo en JavaScript. */
    fun isGoogleCode(code: String?): Boolean = code != null && GOOGLE_CODE.matches(code)

    /** Código del enlace `tasalo://login?codigo=...` con el que el navegador devuelve el login con Google; null si no es ese enlace. */
    fun googleCodeFromLink(link: String?): String? {
        if (link == null) return null
        val uri = runCatching { URI(link) }.getOrNull() ?: return null
        if (!uri.scheme.equals("tasalo", ignoreCase = true) || !uri.host.equals("login", ignoreCase = true)) return null
        val code = uri.rawQuery?.split('&')?.firstOrNull { it.startsWith("codigo=") }?.substringAfter('=')
        return code?.takeIf { isGoogleCode(it) }
    }
}
