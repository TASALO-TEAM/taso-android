package com.tasalo.android

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.tasalo.android.data.TasaloRepository
import com.tasalo.android.data.local.CacheStore
import com.tasalo.android.data.local.SettingsStore
import com.tasalo.android.data.remote.TasaloApi
import com.tasalo.android.diag.DiagnosticLog
import com.tasalo.android.update.UpdateChecker
import com.tasalo.android.work.RefreshScheduler
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import retrofit2.Retrofit

private val Context.cacheDataStore by preferencesDataStore(name = "cache")
private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** DI manual (plan §1): sin Hilt/Koin. Un solo contenedor para toda la app. */
class AppContainer(context: Context) {
    val cacheStore = CacheStore(context.cacheDataStore)
    val settingsStore = SettingsStore(context.settingsDataStore)

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    val updateChecker = UpdateChecker(http)

    private val apis = ConcurrentHashMap<String, TasaloApi>()

    private fun api(baseUrl: String): TasaloApi =
        apis.getOrPut(baseUrl) {
            Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(http)
                .build()
                .create(TasaloApi::class.java)
        }

    val repository = TasaloRepository(
        cache = cacheStore,
        apiFor = ::api,
        baseUrl = { settingsStore.current().baseUrl },
    )
}

class TasaloApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Primero el registro y el capturador de fallos, para que cualquier error posterior quede anotado.
        DiagnosticLog.init(this)
        DiagnosticLog.installCrashHandler()
        container = AppContainer(this)
        DiagnosticLog.i("App", "inicio")
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            RefreshScheduler.schedulePeriodic(this@TasaloApp, container.settingsStore.current().refreshMinutes)
        }
    }
}

val Context.container: AppContainer
    get() = (applicationContext as TasaloApp).container
