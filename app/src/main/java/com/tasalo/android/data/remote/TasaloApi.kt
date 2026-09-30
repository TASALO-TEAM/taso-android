package com.tasalo.android.data.remote

import okhttp3.ResponseBody
import retrofit2.http.GET

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

    // Fallback por fuente, solo si /latest falla.
    @GET("api/v1/tasas/eltoque")
    suspend fun eltoque(): ResponseBody

    @GET("api/v1/tasas/bcc")
    suspend fun bcc(): ResponseBody

    @GET("api/v1/tasas/cadeca")
    suspend fun cadeca(): ResponseBody
}
