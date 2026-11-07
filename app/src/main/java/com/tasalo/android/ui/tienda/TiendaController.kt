package com.tasalo.android.ui.tienda

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tasalo.android.diag.DiagnosticLog

/**
 * Dueño del WebView de la pestaña Tienda (taso-inv: tienda para clientes, herramienta para el personal).
 *
 * Vive a nivel de la actividad, no de la página del paginador: así la sesión, el formulario a medias y el desplazamiento
 * se conservan al cambiar de pestaña. La sesión es la cookie HttpOnly de taso-inv, que el WebView guarda solo.
 * [initialUrl] / [onUrlChanged] solo sirven para volver a la misma pantalla si la actividad se recrea (p. ej. al rotar).
 */
class TiendaController(
    private val activity: Activity,
    private val userAgentSuffix: String,
    initialUrl: String?,
    private val onUrlChanged: (String) -> Unit,
) {
    var progress by mutableIntStateOf(0)
        private set
    var failed by mutableStateOf(false)
        private set
    var canGoBack by mutableStateOf(false)
        private set

    /** Cambia cuando el WebView se recrea (murió su proceso de render): la pantalla lo vuelve a montar. */
    var generation by mutableIntStateOf(0)
        private set

    /** Lo pone la pantalla: lanza el selector de archivos del sistema. Devuelve false si no pudo abrirlo. */
    var chooseFile: ((Intent) -> Boolean)? = null

    private var webView: WebView? = null
    private var pendingFile: ValueCallback<Array<Uri>>? = null
    private var backgroundArgb = 0
    private var started = false
    private var loadedOnce = false
    private var goneCount = 0
    private var lastUrl: String = initialUrl?.takeIf { TiendaNav.isInv(it) } ?: TiendaNav.INV_URL

    // Con poca memoria y la pestaña fuera de pantalla se suelta el WebView (la siguiente vez recarga desde la caché HTTP):
    // en equipos de gama baja el sistema, si no, mata el render o la app entera y la tienda vuelve en blanco.
    private val trimCallbacks = object : ComponentCallbacks2 {
        override fun onTrimMemory(level: Int) {
            val view = webView ?: return
            if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND && view.parent == null) drop()
        }

        override fun onConfigurationChanged(newConfig: Configuration) {}

        override fun onLowMemory() {}
    }

    init {
        activity.registerComponentCallbacks(trimCallbacks)
    }

    /** El WebView, creado la primera vez que se pide. Se suelta de su padre anterior para poder montarlo en otro. */
    fun obtain(backgroundColor: Int): WebView {
        backgroundArgb = backgroundColor
        val view = webView ?: create().also { webView = it }
        (view.parent as? ViewGroup)?.removeView(view)
        view.setBackgroundColor(backgroundColor)
        return view
    }

    fun setBackground(color: Int) {
        backgroundArgb = color
        webView?.setBackgroundColor(color)
    }

    /** Carga la tienda la primera vez que la pestaña se ve (no antes: no se gastan datos sin que se abra). */
    fun ensureLoaded() {
        if (started) return
        started = true
        webView?.loadUrl(lastUrl)
    }

    fun retry() {
        failed = false
        goneCount = 0
        val view = webView
        if (view == null) {
            generation++
            return
        }
        view.loadUrl(view.url?.takeIf { TiendaNav.isInv(it) } ?: lastUrl)
    }

    fun goBack() {
        webView?.takeIf { it.canGoBack() }?.goBack()
    }

    fun onPause() {
        webView?.onPause()
        CookieManager.getInstance().flush()
    }

    fun onResume() {
        webView?.onResume()
    }

    fun destroy() {
        activity.unregisterComponentCallbacks(trimCallbacks)
        pendingFile?.onReceiveValue(null)
        pendingFile = null
        webView?.let { discard(it) }
        webView = null
    }

    /**
     * Al volver a la pestaña: si el WebView ya cargó antes pero no responde o quedó sin contenido (el sistema le quitó
     * el render y el WebView no avisó), se recrea y recarga la última pantalla en vez de dejar una pantalla en blanco.
     */
    fun checkAlive() {
        val view = webView ?: return
        if (!loadedOnce || failed || progress in 1..99) return
        var answered = false
        view.evaluateJavascript("document.body ? document.body.childElementCount : -1") { result ->
            answered = true
            if (webView === view && (result == "0" || result == "-1" || result == "null")) recover()
        }
        view.postDelayed({ if (!answered && webView === view && view.isAttachedToWindow) recover() }, 2500)
    }

    private fun recover() {
        DiagnosticLog.w("Tienda", "el WebView no respondia o estaba en blanco al volver: se recrea")
        drop()
        generation++
    }

    /** Suelta el WebView actual; el siguiente se crea al pedirlo y recarga la última pantalla (la sesión es una cookie). */
    private fun drop() {
        webView?.let { discard(it) }
        webView = null
        loadedOnce = false
        canGoBack = false
        progress = 0
    }

    /** Respuesta del selector de archivos (null = cancelado). */
    fun finishFileChooser(uris: Array<Uri>?) {
        pendingFile?.onReceiveValue(uris)
        pendingFile = null
    }

    /** Hoja de impresión del sistema (incluye «Guardar como PDF»); solo la pide la propia web de taso-inv por el puente. */
    fun printCurrentPage() {
        val view = webView ?: return
        view.post {
            if (!TiendaNav.isInv(view.url)) return@post
            runCatching {
                val manager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val name = view.title?.takeIf { it.isNotBlank() } ?: "TASALO"
                manager.print(name, view.createPrintDocumentAdapter(name), PrintAttributes.Builder().build())
            }.onFailure { DiagnosticLog.w("Tienda", "no se pudo abrir la impresión", it) }
        }
    }

    private fun discard(view: WebView) {
        (view.parent as? ViewGroup)?.removeView(view)
        view.stopLoading()
        view.webViewClient = WebViewClient()
        view.webChromeClient = null
        view.destroy()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun create(): WebView = WebView(activity).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        setBackgroundColor(backgroundArgb)
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = android.view.View.OVER_SCROLL_NEVER
        with(settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            // Sin archivos locales ni contenido mezclado: solo se carga https de taso-inv.
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            // La web de taso-inv reconoce la app por este sufijo (lib/app.js): sin Google y con puente de impresión.
            userAgentString = "$userAgentString TasaloAndroid/$userAgentSuffix"
        }
        CookieManager.getInstance().setAcceptCookie(true)
        webViewClient = client()
        webChromeClient = chromeClient()
        addJavascriptInterface(TiendaBridge(this@TiendaController), "TasaloAndroid")
        if (started) loadUrl(lastUrl)
    }

    private fun client() = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            if (TiendaNav.staysInside(url)) return false
            openOutside(url)
            return true
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
            failed = false
        }

        override fun onPageFinished(view: WebView, url: String?) {
            canGoBack = view.canGoBack()
            loadedOnce = true
            goneCount = 0
            CookieManager.getInstance().flush()
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            canGoBack = view.canGoBack()
            if (url != null && TiendaNav.isInv(url)) {
                lastUrl = url
                onUrlChanged(url)
            }
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) {
                DiagnosticLog.w("Tienda", "error al cargar la tienda (${error.errorCode}): ${error.description}")
                failed = true
            }
        }

        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
            // 502/503/504: el servidor (o el túnel hasta él) no responde. Un 4xx de una ruta de la web lo maneja la propia web.
            if (request.isForMainFrame && errorResponse.statusCode >= 500) failed = true
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            DiagnosticLog.w("Tienda", "el proceso de render del WebView terminó (fallo=${detail.didCrash()})")
            drop()
            // El sistema mata el render con poca memoria: se recrea sin aviso de error. Si cae 3 veces seguidas sin
            // llegar a cargar, se muestra el error con Reintentar en vez de entrar en bucle.
            if (++goneCount > 3) failed = true else generation++
            return true
        }
    }

    private fun chromeClient() = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            progress = newProgress
        }

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams,
        ): Boolean {
            pendingFile?.onReceiveValue(null)
            pendingFile = filePathCallback
            val opened = runCatching { chooseFile?.invoke(fileChooserParams.createIntent()) == true }.getOrDefault(false)
            if (!opened) {
                pendingFile = null
                filePathCallback.onReceiveValue(null)
            }
            return true
        }
    }

    private fun openOutside(url: String) {
        if (!TiendaNav.canOpenOutside(url)) return
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            DiagnosticLog.w("Tienda", "no hay app para abrir $url", e)
        }
    }
}

/**
 * Puente `window.TasaloAndroid` para la web de taso-inv. Solo hace lo que la web no puede en un WebView y solo si la
 * página que lo llama es de taso-inv. Hoy: imprimir. (Google nativo llegará aquí, v0.17.0.)
 */
class TiendaBridge(private val owner: TiendaController) {
    @JavascriptInterface
    fun print() {
        owner.printCurrentPage()
    }
}
