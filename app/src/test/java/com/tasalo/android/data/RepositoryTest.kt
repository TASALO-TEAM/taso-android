package com.tasalo.android.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.tasalo.android.data.local.CacheStore
import com.tasalo.android.data.parse.Parsers
import com.tasalo.android.data.remote.TasaloApi
import com.tasalo.android.domain.Source
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Retrofit

/** Tests del repositorio con MockWebServer: sin red real (plan §9). */
class RepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var scope: CoroutineScope
    private lateinit var cache: CacheStore

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResource(name)!!.readText()

    private fun yearJson(quote: String) =
        """{"ok":true,"progress":{"year":2026,"percent":74.4,"days_left":93},"quote":{"ok":true,"quote":"$quote"}}"""

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        cache = CacheStore(
            PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "cache.preferences_pb") },
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
        scope.cancel()
    }

    private fun repo(client: OkHttpClient = OkHttpClient()): TasaloRepository {
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .build()
            .create(TasaloApi::class.java)
        return TasaloRepository(
            cache = cache,
            apiFor = { api },
            baseUrl = { server.url("/").toString() },
            now = { Instant.parse("2026-09-29T20:00:00Z") },
            zone = { ZoneOffset.UTC },
            retryDelayMs = 0,
        )
    }

    private fun ok(body: String) = MockResponse().setResponseCode(200).setBody(body)
    private fun code(status: Int) = MockResponse().setResponseCode(status).setBody("{}")

    private fun route(handler: (String) -> MockResponse) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = handler(request.path.orEmpty())
        }
    }

    @Test
    fun everything_ok_saves_rates_fuel_and_year() = runBlocking {
        route {
            when (it) {
                "/api/v1/tasas/latest" -> ok(fixture("latest.json"))
                "/api/v1/tasas/fuel" -> ok(fixture("fuel.json"))
                "/api/v1/year/state" -> ok(fixture("year_state.json"))
                else -> code(404)
            }
        }
        val result = repo().refreshAll()
        assertTrue(result.allOk)
        val snap = repo().snapshot()
        assertNotNull(snap.rates)
        assertEquals(750.0, snap.rates!!.bySource.getValue(Source.ELTOQUE).first { it.currency == "USD" }.rate, 0.0)
        assertEquals(4, snap.fuel!!.items.size)
        assertEquals(93, snap.year.daysLeft)
    }

    @Test
    fun a_failing_fuel_endpoint_does_not_prevent_saving_rates_and_year() = runBlocking {
        route {
            when (it) {
                "/api/v1/tasas/latest" -> ok(fixture("latest.json"))
                "/api/v1/tasas/fuel" -> code(500)
                "/api/v1/year/state" -> ok(fixture("year_state.json"))
                else -> code(404)
            }
        }
        val result = repo().refreshAll()
        assertTrue(result.rates)
        assertTrue(result.year)
        assertFalse(result.fuel)
        val snap = repo().snapshot()
        assertNotNull(snap.rates)
        assertNull(snap.fuel)
    }

    @Test
    fun latest_failure_falls_back_to_the_per_source_endpoints() = runBlocking {
        route {
            when (it) {
                "/api/v1/tasas/latest" -> code(500)
                "/api/v1/tasas/eltoque" -> ok(fixture("eltoque.json"))
                else -> code(404)
            }
        }
        val result = repo().refreshAll()
        assertTrue(result.rates)
        val rates = repo().snapshot().rates!!
        assertEquals(6, rates.bySource.getValue(Source.ELTOQUE).size)
        assertTrue(rates.bySource.getValue(Source.BCC).isEmpty())
    }

    @Test
    fun corrupt_json_is_not_saved_and_the_previous_cache_survives() = runBlocking {
        route { if (it == "/api/v1/tasas/latest") ok(fixture("latest.json")) else code(404) }
        assertTrue(repo().refreshAll().rates)

        route { if (it == "/api/v1/tasas/latest") ok("{esto no es json") else code(404) }
        val second = repo().refreshAll()
        assertFalse(second.anyOk)
        assertNotNull(repo().snapshot().rates)
    }

    @Test
    fun a_timeout_only_fails_that_resource() = runBlocking {
        route {
            when (it) {
                "/api/v1/tasas/latest" -> ok(fixture("latest.json"))
                "/api/v1/tasas/fuel" -> MockResponse().apply { socketPolicy = SocketPolicy.NO_RESPONSE }
                "/api/v1/year/state" -> ok(fixture("year_state.json"))
                else -> code(404)
            }
        }
        val client = OkHttpClient.Builder().readTimeout(300, TimeUnit.MILLISECONDS).build()
        val result = repo(client).refreshAll()
        assertTrue(result.rates)
        assertTrue(result.year)
        assertFalse(result.fuel)
    }

    @Test
    fun the_quote_of_the_day_is_kept_even_if_the_server_returns_another() = runBlocking {
        route { if (it == "/api/v1/year/state") ok(yearJson("Primera")) else code(404) }
        repo().refreshAll()
        route { if (it == "/api/v1/year/state") ok(yearJson("Segunda")) else code(404) }
        repo().refreshAll()
        assertEquals("Primera", repo().snapshot().year.quote)
    }

    @Test
    fun client_errors_are_not_retried() = runBlocking {
        route { code(404) }
        repo().refreshAll()
        // latest + 4 fallbacks (con QvaPay) + fuel + year + mensajes = 8 llamadas, una sola vez cada una.
        assertEquals(8, server.requestCount)
    }

    @Test
    fun messages_are_saved_in_the_cache() = runBlocking {
        route {
            when (it) {
                "/api/v1/app/messages?limit=20" -> ok(fixture("app_messages.json"))
                else -> code(404)
            }
        }
        val result = repo().refreshAll()
        assertTrue(result.messages)
        val messages = Parsers.messages(cache.current().messagesJson!!)!!
        assertEquals(listOf(3L, 2L), messages.map { it.id })
    }

    @Test
    fun a_missing_messages_endpoint_does_not_break_or_flag_the_refresh() = runBlocking {
        // API antigua sin /app/messages: 404 en mensajes, todo lo demás bien.
        route {
            when (it) {
                "/api/v1/tasas/latest" -> ok(fixture("latest.json"))
                "/api/v1/tasas/fuel" -> ok(fixture("fuel.json"))
                "/api/v1/year/state" -> ok(fixture("year_state.json"))
                else -> code(404)
            }
        }
        val result = repo().refreshAll()
        assertFalse(result.messages)
        assertTrue(result.allOk) // los mensajes son opcionales: no cuentan para allOk
        assertNull(cache.current().messagesJson)
    }
}
