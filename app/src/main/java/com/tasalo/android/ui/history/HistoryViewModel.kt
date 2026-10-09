package com.tasalo.android.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tasalo.android.container
import com.tasalo.android.domain.PricePoint
import com.tasalo.android.domain.Source
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado del detalle: `loading` solo cuando todavía no hay nada que mostrar; `error` = no se pudo actualizar. */
data class DetailState(
    val loading: Boolean = true,
    val points: List<PricePoint> = emptyList(),
    val error: Boolean = false,
)

class HistoryViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = app.container.historyRepository

    private val _summary = MutableStateFlow<Map<String, List<PricePoint>>>(emptyMap())

    /** Últimos 30 días por tarjeta (clave `FUENTE:MONEDA`) para el fondo; vacío si la API aún no lo ofrece. */
    val summary: StateFlow<Map<String, List<PricePoint>>> = _summary

    private val _detail = MutableStateFlow(DetailState())
    val detail: StateFlow<DetailState> = _detail

    private var detailJob: Job? = null
    private var detailKey: String? = null

    /** Una sola llamada liviana; se repite como mucho cada 30 min (el caché lo decide) o con `force`. */
    fun loadSummary(force: Boolean = false) {
        viewModelScope.launch {
            val result = repository.summary(force)
            if (result.isNotEmpty()) _summary.value = result
        }
    }

    fun openDetail(source: Source, currency: String, force: Boolean = false) {
        val key = "${source.name}:$currency"
        if (!force && key == detailKey && detailJob?.isActive == true) return
        if (key != detailKey) _detail.value = DetailState()
        detailKey = key
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            _detail.update { it.copy(loading = it.points.isEmpty(), error = false) }
            val result = repository.series(source, currency, force)
            _detail.value = DetailState(loading = false, points = result.points, error = result.error)
        }
    }

    fun closeDetail() {
        detailJob?.cancel()
        detailKey = null
        _detail.value = DetailState()
    }
}
