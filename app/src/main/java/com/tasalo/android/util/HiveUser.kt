package com.tasalo.android.util

/**
 * Nombres de usuario de Hive: 3-16 caracteres, minúsculas, números, guiones y puntos; empieza por letra, termina
 * en letra o número, sin guiones seguidos, y cada parte separada por punto tiene al menos 3 caracteres.
 */
object HiveUser {
    private val fromLink = Regex("""@([A-Za-z0-9.-]+)""")
    private val valid = Regex("""^[a-z][a-z0-9-]*[a-z0-9](\.[a-z][a-z0-9-]*[a-z0-9])*$""")

    /**
     * Acepta `usuario`, `@Usuario` o un enlace con `@usuario` (Ecency, PeakD, Hive.blog...).
     * Devuelve el nombre en minúsculas, o null si no es un usuario de Hive válido.
     */
    fun normalize(input: String): String? {
        val text = input.trim()
        val candidate = (if ('/' in text) fromLink.find(text)?.groupValues?.get(1)?.trimEnd('.') else text.removePrefix("@"))
            ?.lowercase()
            ?: return null
        if (candidate.length !in 3..16) return null
        if ("--" in candidate || !valid.matches(candidate)) return null
        if (candidate.split('.').any { it.length < 3 }) return null
        return candidate
    }
}
