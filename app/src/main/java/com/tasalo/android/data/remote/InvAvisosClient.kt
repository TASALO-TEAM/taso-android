package com.tasalo.android.data.remote

import android.webkit.CookieManager
import com.tasalo.android.diag.DiagnosticLog
import com.tasalo.android.domain.OrderAviso
import com.tasalo.android.domain.OrderNotices
import com.tasalo.android.ui.tienda.TiendaNav
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Lee `GET /api/avisos` de taso-inv con la sesion que la pestana Tienda ya guardo en el WebView (cookie `taso_sesion`).
 * Sin sesion, sin red o con una respuesta rara devuelve null y no pasa nada: el aviso es opcional. No marca nada como
 * leido (eso lo hace la campana de la web al abrir la Tienda), asi que no hay POST ni cabecera `Origin`.
 */
class InvAvisosClient(client: OkHttpClient) {
    // Sin redirecciones: la cookie de sesion nunca debe viajar a otro sitio.
    private val http = client.newBuilder().followRedirects(false).followSslRedirects(false).build()

    suspend fun fetch(): List<OrderAviso>? = withContext(Dispatchers.IO) {
        val cookie = runCatching { CookieManager.getInstance().getCookie(TiendaNav.INV_URL) }.getOrNull()
        if (cookie == null || !cookie.contains("taso_sesion=")) return@withContext null
        try {
            val request = Request.Builder()
                .url(TiendaNav.INV_URL + "api/avisos")
                .header("Cookie", cookie)
                .header("User-Agent", "TASALO-Android")
                .build()
            val text = http.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()
                } else {
                    DiagnosticLog.i("Pedidos", "avisos de la tienda: respuesta ${response.code}")
                    null
                }
            }
            text?.let { OrderNotices.parse(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DiagnosticLog.w("Pedidos", "no se pudieron consultar los avisos de la tienda", e)
            null
        }
    }
}
