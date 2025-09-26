package no.nav.emottak

import arrow.fx.coroutines.ExitCase
import arrow.fx.coroutines.ResourceScope
import arrow.fx.coroutines.await.awaitAll
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.serialization.kotlinx.json.json
import io.micrometer.prometheus.PrometheusConfig.DEFAULT
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.serialization.json.Json
import no.nav.emottak.edi.adapter.config.Config
import no.nav.emottak.edi.adapter.plugin.DpopAuth
import no.nav.emottak.edi.adapter.util.obtainDpopTokens

data class Dependencies(
    val httpClient: HttpClient,
    val meterRegistry: PrometheusMeterRegistry
)

internal suspend fun ResourceScope.metricsRegistry(): PrometheusMeterRegistry =
    install({ PrometheusMeterRegistry(DEFAULT) }) { p, _: ExitCase ->
        p.close().also { log.info("Closed prometheus registry") }
    }

internal suspend fun ResourceScope.httpClientEngine(): HttpClientEngine =
    install({ CIO.create() }) { e, _: ExitCase -> e.close().also { log.info("Closed http client engine") } }

internal suspend fun ResourceScope.httpTokenClientEngine(): HttpClientEngine =
    install({ CIO.create() }) { e, _: ExitCase -> e.close().also { log.info("Closed http token client engine") } }

private fun httpTokenClient(config: Config, clientEngine: HttpClientEngine): HttpClient =
    HttpClient(clientEngine) {
        install(HttpTimeout) {
            connectTimeoutMillis = config.httpTokenClient.connectionTimeout.value
        }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

private fun httpClient(
    config: Config,
    clientEngine: HttpClientEngine,
    httpTokenClient: HttpClient
): HttpClient = HttpClient(clientEngine) {
    install(HttpTimeout) {
        connectTimeoutMillis = config.httpClient.connectionTimeout.value
    }
    install(ContentNegotiation) { json() }
    install(DpopAuth) {
        azureAuth = config.azureAuth
        loadTokens = { obtainDpopTokens(config.azureAuth, httpTokenClient) }
    }
    defaultRequest { url(config.nhn.baseUrl.toString()) }
}

suspend fun ResourceScope.dependencies(): Dependencies = awaitAll {
    val config = config()

    val metricsRegistry = async { metricsRegistry() }
    val httpTokenClientEngine = async { httpTokenClientEngine() }
    val httpTokenClient = async { httpTokenClient(config, httpTokenClientEngine.await()) }.await()
    val httpClientEngine = async { httpClientEngine() }.await()
    val httpClient = async { httpClient(config, httpClientEngine, httpTokenClient) }

    Dependencies(
        httpClient.await(),
        metricsRegistry.await()
    )
}
