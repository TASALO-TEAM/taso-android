package com.tasalo.android.domain

/** Resultado de agrupar: secciones de mercado con título y, aparte, las de referencia (valor fijo, sin ofertas). */
data class RateSection(val group: String, val title: String, val rates: List<Rate>)

data class GroupedRates(val sections: List<RateSection>, val reference: List<Rate>)

object RateGroups {
    /** Orden de los grupos del catálogo de la API. */
    val ORDER = listOf("main", "crypto", "other")

    private val TITLES = mapOf("main" to "Principales", "crypto" to "Cripto", "other" to "Otras monedas")

    /**
     * Agrupa según el catálogo de la API. Devuelve null si ninguna tasa trae `group` (QvaPay, BCC o API vieja):
     * la pantalla entonces queda como antes. Conserva el orden de entrada dentro de cada sección.
     */
    fun group(rates: List<Rate>): GroupedRates? {
        if (rates.none { it.group != null }) return null
        val market = rates.filter { !it.isReference }
        val sections = ORDER.mapNotNull { g ->
            val inGroup = market.filter { (it.group ?: "other") == g }
            if (inGroup.isEmpty()) null else RateSection(g, TITLES.getValue(g), inGroup)
        }
        return GroupedRates(sections, rates.filter { it.isReference })
    }
}
