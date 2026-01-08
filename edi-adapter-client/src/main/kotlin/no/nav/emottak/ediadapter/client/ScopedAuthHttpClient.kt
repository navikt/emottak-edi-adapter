package no.nav.helsemelding.ediadapter.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.HttpResponse
import io.ktor.http.parameters
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import no.nav.helsemelding.ediadapter.client.Config.AzureAuth

fun scopedAuthHttpClient(scope: String): () -> HttpClient = { httpClient(httpTokenClient(), scope) }

private fun httpTokenClient(): HttpClient =
    HttpClient(CIO) {
        install(HttpTimeout) {
            connectTimeoutMillis = config().httpTokenClient.connectionTimeout.toMillis()
        }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

private fun httpClient(
    tokenClient: HttpClient,
    scope: String
): HttpClient =
    HttpClient(CIO) {
        install(HttpTimeout) {
            connectTimeoutMillis = config().httpClient.connectionTimeout.toMillis()
        }
        install(ContentNegotiation) { json() }
        install(Auth) {
            bearer {
                refreshTokens {
                    val tokenInfo: TokenInfo = submitTokenForm(tokenClient, config().azureAuth, scope).body()
                    BearerTokens(tokenInfo.accessToken, null)
                }
                sendWithoutRequest { true }
            }
        }
    }

private suspend fun submitTokenForm(
    tokenClient: HttpClient,
    azureAuth: AzureAuth,
    scope: String
): HttpResponse =
    tokenClient.submitForm(
        url = azureAuth.azureTokenEndpoint.value,
        formParameters = parameters {
            append("client_id", azureAuth.azureAppClientId.value)
            append("client_secret", azureAuth.azureAppClientSecret.value)
            append("grant_type", azureAuth.azureGrantType.value)
            append("scope", scope)
        }
    )
