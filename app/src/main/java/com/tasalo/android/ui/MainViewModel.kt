package com.tasalo.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tasalo.android.container
import com.tasalo.android.data.Snapshots
import com.tasalo.android.diag.DiagnosticLog
import com.tasalo.android.diag.ReportBuilder
import com.tasalo.android.domain.AppSettings
import com.tasalo.android.domain.FuelSnapshot
import com.tasalo.android.domain.RatesSnapshot
import com.tasalo.android.domain.Source
import com.tasalo.android.domain.ThemeMode
import com.tasalo.android.domain.YearState
import com.tasalo.android.update.UpdateInfo
import com.tasalo.android.update.UpdateResult
import com.tasalo.android.util.NetworkMonitor
import com.tasalo.android.widget.WidgetUpdater
import com.tasalo.android.work.RefreshScheduler
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val currentVersion: String = "",
    val update: UpdateInfo? = null,
    val showUpdate: Boolean = false,
    val checkingUpdate: Boolean = false,
    val updateMessage: String? = null,
    val showCrash: Boolean = false,
)

/** Estado de actualizaciones y reportes, separado para no pasar de 5 flujos en un solo `combine`. */
private data class Extras(
    val update: UpdateInfo? = null,
    val showUpdate: Boolean = false,
    val checking: Boolean = false,
    val message: String? = null,
    val showCrash: Boolean = false,
)

class MainViewModel(private val app: Application) : AndroidViewModel(app) {
    private val container = app.container
    private val repository = container.repository
    private val settingsStore = container.settingsStore
    private val updateChecker = container.updateChecker

    /** Sin el sufijo "-debug", para comparar con la versión publicada. */
    private val currentVersion: String = ReportBuilder.versionName(app).substringBefore('-')

    private val refreshing = MutableStateFlow(false)
    private val errors = MutableStateFlow(RefreshError.NONE to RefreshError.NONE)
    private val extras = MutableStateFlow(Extras())

    /** Reloj para que "hace X min" y los avisos de datos viejos se actualicen solos. */
    private val ticker = flow {
        while (true) {
            emit(Instant.now())
            delay(30_000)
        }
    }

    private val base: StateFlow<UiState> = combine(
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
            currentVersion = currentVersion,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState(currentVersion = currentVersion))

    val state: StateFlow<UiState> = combine(base, extras) { b, x ->
        b.copy(
            update = x.update,
            showUpdate = x.showUpdate,
            checkingUpdate = x.checking,
            updateMessage = x.message,
            showCrash = x.showCrash,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState(currentVersion = currentVersion))

    init {
        checkPendingCrash()
        checkForUpdates(manual = false)
    }

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

    // ---------- Actualizaciones ----------

    /** Automática: como máximo cada 6 h y respetando "omitir esta versión". Manual: siempre. */
    fun checkForUpdates(manual: Boolean) {
        viewModelScope.launch {
            if (extras.value.checking) return@launch
            val settings = settingsStore.current()
            val nowMs = System.currentTimeMillis()
            if (!manual && nowMs - settings.lastUpdateCheck < SIX_HOURS_MS) return@launch

            extras.update { it.copy(checking = true, message = null) }
            val result = updateChecker.check(currentVersion)
            settingsStore.setLastUpdateCheck(nowMs)
            extras.update { x ->
                when (result) {
                    is UpdateResult.Available -> {
                        val skipped = !manual && result.info.version == settings.skippedVersion
                        x.copy(
                            checking = false,
                            update = result.info,
                            showUpdate = !skipped,
                            message = if (manual) "Hay una nueva versión: ${result.info.version}" else null,
                        )
                    }
                    UpdateResult.UpToDate -> x.copy(
                        checking = false,
                        update = null,
                        message = if (manual) "Estás al día (versión $currentVersion)" else null,
                    )
                    is UpdateResult.Failed -> x.copy(
                        checking = false,
                        message = if (manual) "No se pudo comprobar. Revisa tu conexión." else null,
                    )
                }
            }
        }
    }

    fun dismissUpdate() = extras.update { it.copy(showUpdate = false) }

    fun showUpdateDialog() = extras.update { if (it.update != null) it.copy(showUpdate = true) else it }

    fun skipUpdate() {
        val version = extras.value.update?.version ?: return
        viewModelScope.launch { settingsStore.setSkippedVersion(version) }
        extras.update { it.copy(showUpdate = false) }
    }

    // ---------- Reportes de fallos ----------

    private fun checkPendingCrash() {
        viewModelScope.launch {
            val prompt = settingsStore.current().crashPrompt
            val pending = withContext(Dispatchers.IO) { DiagnosticLog.hasPendingCrashes() }
            if (prompt && pending) extras.update { it.copy(showCrash = true) }
        }
    }

    /** Se llama tras abrir el correo o al descartar: evita volver a preguntar por el mismo fallo. */
    fun onCrashHandled() {
        viewModelScope.launch(Dispatchers.IO) { DiagnosticLog.clearCrashes() }
        extras.update { it.copy(showCrash = false) }
    }

    fun setRefreshMinutes(minutes: Int) {
        viewModelScope.launch {
            settingsStore.setRefreshMinutes(minutes)
            RefreshScheduler.schedulePeriodic(app, minutes)
        }
    }

    fun setCrashPrompt(value: Boolean) {
        viewModelScope.launch { settingsStore.setCrashPrompt(value) }
    }

    // ---------- Ajustes ----------

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

    private companion object {
        const val SIX_HOURS_MS = 6 * 60 * 60 * 1000L
    }
}
