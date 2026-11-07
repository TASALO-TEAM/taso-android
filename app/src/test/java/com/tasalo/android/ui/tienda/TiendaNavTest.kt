package com.tasalo.android.ui.tienda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Las reglas de TiendaNav deciden qué páginas pueden usar el puente nativo y cuáles salen al sistema. */
class TiendaNavTest {

    @Test
    fun soloElDominioDeTasoInvEsInvYSoloPorHttps() {
        assertTrue(TiendaNav.isInv("https://camiersus.duckdns.org/"))
        assertTrue(TiendaNav.isInv("https://camiersus.duckdns.org/#/catalogo"))
        assertTrue(TiendaNav.isInv("HTTPS://Camiersus.DuckDNS.org/pedidos"))
        assertFalse(TiendaNav.isInv("http://camiersus.duckdns.org/"))
        assertFalse(TiendaNav.isInv("https://camiersus.duckdns.org.evil.com/"))
        assertFalse(TiendaNav.isInv("https://camiersus.duckdns.org@evil.com/"))
        assertFalse(TiendaNav.isInv("https://evil.com/?u=camiersus.duckdns.org"))
        assertFalse(TiendaNav.isInv(null))
    }

    @Test
    fun dentroSoloTasoInvYElLoginDeTelegram() {
        assertTrue(TiendaNav.staysInside("https://camiersus.duckdns.org/api/auth/telegram"))
        assertTrue(TiendaNav.staysInside("https://oauth.telegram.org/auth?bot_id=1"))
        assertFalse(TiendaNav.staysInside("http://oauth.telegram.org/auth"))
        assertFalse(TiendaNav.staysInside("https://telegram.org/"))
        assertFalse(TiendaNav.staysInside("https://example.com/"))
    }

    @Test
    fun alSistemaSoloEsquemasSeguros() {
        assertTrue(TiendaNav.canOpenOutside("https://example.com/"))
        assertTrue(TiendaNav.canOpenOutside("tg://resolve?domain=camiersusbot"))
        assertTrue(TiendaNav.canOpenOutside("mailto:alguien@example.com"))
        assertTrue(TiendaNav.canOpenOutside("tel:+5355555555"))
        assertFalse(TiendaNav.canOpenOutside("javascript:alert(1)"))
        assertFalse(TiendaNav.canOpenOutside("intent://scan/#Intent;scheme=zxing;end"))
        assertFalse(TiendaNav.canOpenOutside("file:///sdcard/x"))
        assertFalse(TiendaNav.canOpenOutside(null))
    }

    private val codigo = "Ab1-_".repeat(9).take(43)

    @Test
    fun alNavegadorSoloVaLaPaginaDeGoogleDeTasoInv() {
        assertTrue(TiendaNav.isGoogleBrowserUrl("https://camiersus.duckdns.org/google-app.html?c=$codigo"))
        assertTrue(TiendaNav.isGoogleBrowserUrl("https://camiersus.duckdns.org:443/google-app.html?c=x"))
        assertFalse(TiendaNav.isGoogleBrowserUrl("http://camiersus.duckdns.org/google-app.html?c=x"))
        assertFalse(TiendaNav.isGoogleBrowserUrl("https://camiersus.duckdns.org/otra.html?c=x"))
        assertFalse(TiendaNav.isGoogleBrowserUrl("https://camiersus.duckdns.org/google-app.html/../x"))
        assertFalse(TiendaNav.isGoogleBrowserUrl("https://camiersus.duckdns.org:8443/google-app.html?c=x"))
        assertFalse(TiendaNav.isGoogleBrowserUrl("https://camiersus.duckdns.org.evil.com/google-app.html?c=x"))
        assertFalse(TiendaNav.isGoogleBrowserUrl("https://camiersus.duckdns.org@evil.com/google-app.html?c=x"))
        assertFalse(TiendaNav.isGoogleBrowserUrl("https://user:pass@camiersus.duckdns.org/google-app.html"))
        assertFalse(TiendaNav.isGoogleBrowserUrl("https://evil.com/google-app.html?u=camiersus.duckdns.org"))
        assertFalse(TiendaNav.isGoogleBrowserUrl("tasalo://login?codigo=$codigo"))
        assertFalse(TiendaNav.isGoogleBrowserUrl(null))
    }

    @Test
    fun elCodigoDelEnlaceProfundoSeValidaAntesDeUsarse() {
        assertEquals(codigo, TiendaNav.googleCodeFromLink("tasalo://login?codigo=$codigo"))
        assertEquals(codigo, TiendaNav.googleCodeFromLink("TASALO://Login?x=1&codigo=$codigo"))
        assertNull(TiendaNav.googleCodeFromLink("tasalo://login"))
        assertNull(TiendaNav.googleCodeFromLink("tasalo://login?codigo="))
        assertNull(TiendaNav.googleCodeFromLink("tasalo://login?codigo=corto"))
        assertNull(TiendaNav.googleCodeFromLink("tasalo://otro?codigo=$codigo"))
        assertNull(TiendaNav.googleCodeFromLink("https://camiersus.duckdns.org/?codigo=$codigo"))
        assertNull(TiendaNav.googleCodeFromLink(null))
        // Nada que pueda salirse de las comillas de JavaScript: el código no se acepta si trae otra cosa que base64url.
        assertNull(TiendaNav.googleCodeFromLink("tasalo://login?codigo=$codigo');alert(1);('"))
        assertNull(TiendaNav.googleCodeFromLink("tasalo://login?codigo=${codigo}%27%29"))
        assertTrue(TiendaNav.isGoogleCode(codigo))
        assertFalse(TiendaNav.isGoogleCode("abc def ghijklmnopqrstuvwxyz0123"))
        assertFalse(TiendaNav.isGoogleCode(null))
    }
}
