package com.tasalo.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tasalo.android.container
import com.tasalo.android.data.Snapshots
import com.tasalo.android.domain.AppSettings
import com.tasalo.android.domain.FuelSnapshot
import com.tasalo.android.domain.RatesSnapshot
import com.tasalo.android.domain.Source
import com.tasalo.android.domain.ThemeMode
import com.tasalo.android.domain.YearState
import com.tasalo.android.util.NetworkMonitor
import com.tasalo.android.widget.WidgetUpdater
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class RefreshError { NONE, OFFLINE, API }

data class UiState(
    val loaded: Boolean = false,
    val rates: RatesSnapshot? = null,
    val fuel: FuelSnapshot? = null,
    val year: YearState? = null,
    val settings: AppSettings = AppSettings(),
    val now: Instant = Instant.now(),
    val refreshing: Boolean = false,
    val ratesError: RefreshError = RefreshError.NONE,
    val fuelError: RefreshError = RefreshError.NONE,
)

class MainViewModel(private val app: Application) : AndroidViewModel(app) {
    private val container = app.container
    private val repository = container.repository
    private val settingsStore = container.settingsStore

    private val refreshing = MutableStateFlow(false)
    private val errors = MutableStateFlow(RefreshError.NONE to RefreshError.NONE)

    /** Reloj para que "hace X min" y los avisos de datos viejos se actualicen solos. */
    private val ticker = flow {
        while (true) {
            emit(Instant.now())
            delay(30_000)
        }
    }

    val state: StateFlow<UiState> = combine(
        repository.raw,
        settingsStore.settings,
        ticker,
        refreshing,
        errors,
    ) { raw, settings, now, isRefreshing, err ->
        val snap = Snapshots.from(raw, now)
        UiState(
            loaded = true,
            rates = snap.rates,
            fuel = snap.fuel,
            year = snap.year,
            settings = settings,
            now = now,
            refreshing = isRefreshing,
            ratesError = err.first,
            fuelError = err.second,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    /** Pull-to-refresh y botón: siempre permitido (salvo que ya haya uno en curso). */
    fun refresh() {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            try {
                val result = repository.refreshAll()
                val failure = if (NetworkMonitor.isOnline(app)) RefreshError.API else RefreshError.OFFLINE
                errors.value = (if (result.rates && result.year) RefreshError.NONE else failure) to
                    (if (result.fuel) RefreshError.NONE else failure)
                WidgetUpdater.updateAll(app)
            } finally {
                refreshing.value = false
            }
        }
    }

    /** Al abrir la app o volver a RESUMED: solo si el caché tiene más de 5 min (plan §3.2). */
    fun refreshIfStale() {
        viewModelScope.launch {
            val fetchedAt = repository.snapshot().rates?.fetchedAt
            if (fetchedAt == null || Duration.between(fetchedAt, Instant.now()).toMinutes() >= 5) refresh()
        }
    }

    fun selectSource(source: Source) {
        viewModelScope.launch { settingsStore.setLastSource(source) }
    }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { settingsStore.setTheme(mode); WidgetUpdater.updateAll(app) }
    }

    fun setDefaultSource(source: Source) {
        viewModelScope.launch { settingsStore.setDefaultSource(source) }
    }

    fun setInvertColors(value: Boolean) {
        viewModelScope.launch { settingsStore.setInvertColors(value); WidgetUpdater.updateAll(app) }
    }

    fun setCurrencyVisible(source: Source, currency: String, visible: Boolean) {
        viewModelScope.launch {
            settingsStore.setCurrencyVisible(source, currency, visible)
            WidgetUpdater.updateAll(app)
        }
    }

    /** Devuelve true si la URL era válida (https) y se guardó; refresca contra la nueva URL. */
    suspend fun saveBaseUrl(input: String): Boolean {
        val ok = settingsStore.setBaseUrl(input)
        if (ok) refresh()
        return ok
    }

    fun resetBaseUrl() {
        viewModelScope.launch { settingsStore.resetBaseUrl(); refresh() }
    }
}
