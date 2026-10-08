package com.tasalo.android.data.remote

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Solo GET, sin autenticación (plan §2). Devuelve el cuerpo crudo: el parseo tolerante
 * vive en `Parsers`, así un campo inesperado nunca rompe la deserialización.
 * Quien llama debe consumir el cuerpo (`.string()`).
 */
interface TasaloApi {
    @GET("api/v1/tasas/latest")
    suspend fun latest(): ResponseBody

    @GET("api/v1/year/state")
    suspend fun yearState(): ResponseBody

    @GET("api/v1/tasas/fuel")
    suspend fun fuel(): ResponseBody

    /** Mensajes del equipo (Alertas). Endpoint nuevo: con una API antigua da 404 y se ignora. */
    @GET("api/v1/app/messages")
    suspend fun appMessages(@Query("limit") limit: Int): ResponseBody

    // Fallback por fuente, solo si /latest falla.
    @GET("api/v1/tasas/eltoque")
    suspend fun eltoque(): ResponseBody

    @GET("api/v1/tasas/bcc")
    suspend fun bcc(): ResponseBody

    @GET("api/v1/tasas/cadeca")
    suspend fun cadeca(): ResponseBody

    /** Fuente nueva: con una API antigua da 404 y simplemente no hay datos de QvaPay. */
    @GET("api/v1/tasas/qvapay")
    suspend fun qvapay(): ResponseBody
}
