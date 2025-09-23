package no.nav.emottak

import arrow.fx.coroutines.ExitCase
import arrow.fx.coroutines.ResourceScope
import arrow.fx.coroutines.await.awaitAll
import com.nimbusds.oauth2.sdk.token.AccessTokenType.DPOP
import com.nimbusds.openid.connect.sdk.Nonce
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType.Application.FormUrlEncoded
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.formUrlEncode
import io.ktor.serialization.kotlinx.json.json
import io.micrometer.prometheus.PrometheusConfig.DEFAULT
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.serialization.json.Json
import no.nav.emottak.edi.adapter.config.AzureAuth
import no.nav.emottak.edi.adapter.config.Config
import no.nav.emottak.edi.adapter.model.DpopTokens
import no.nav.emottak.edi.adapter.model.TokenInfo
import no.nav.emottak.edi.adapter.model.toDpopTokens
import no.nav.emottak.edi.adapter.plugin.DpopAuth
import no.nav.emottak.edi.adapter.util.clientAssertion
import no.nav.emottak.edi.adapter.util.dpopProofWithNonce
import no.nav.emottak.edi.adapter.util.dpopProofWithoutNonce

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

suspend fun obtainDpopTokens(
    config: AzureAuth,
    httpTokenClient: HttpClient
): DpopTokens {
    val clientAssertion = clientAssertion(config)

    val proofWithoutNonce = dpopProofWithoutNonce()
    val tokenResponseWithoutNonce = tokenRequest(
        config,
        httpTokenClient,
        clientAssertion,
        proofWithoutNonce
    )

    val response = when (tokenResponseWithoutNonce.status.value) {
        400 -> {
            val nonceHeader = tokenResponseWithoutNonce.headers["DPoP-Nonce"]
                ?: error("DPoP-Nonce header missing")

            val proofWithNonce = dpopProofWithNonce(Nonce(nonceHeader))
            tokenRequest(config, httpTokenClient, clientAssertion, proofWithNonce)
        }

        else -> tokenResponseWithoutNonce
    }

    if (response.status.value != 200) {
        error("Failed to obtain DPoP token: ${response.status} - ${response.bodyAsText()}")
    }

    val tokenInfo: TokenInfo = response.body()
    return tokenInfo.toDpopTokens()
}

private suspend fun tokenRequest(
    config: AzureAuth,
    httpClient: HttpClient,
    clientAssertion: String,
    dpopJwt: String
): HttpResponse =
    httpClient.post(config.tokenEndpoint.toString()) {
        header(DPOP.value, dpopJwt)
        contentType(FormUrlEncoded)
        setBody(
            Parameters.build {
                append("client_id", config.clientId.value)
                append("grant_type", config.grantType.value)
                append("scope", config.scope.value)
                append("client_assertion", clientAssertion)
                append("client_assertion_type", config.clientAssertionType.value)
            }
                .formUrlEncode()
        )
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
