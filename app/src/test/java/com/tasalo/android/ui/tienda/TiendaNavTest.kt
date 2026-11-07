package com.tasalo.android.ui.tienda

import org.junit.Assert.assertFalse
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
}
