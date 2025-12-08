package no.nav.emottak.ediadapter.client

import java.time.Duration

internal data class Config(
    val auth: AzureAuth,
    val httpClient: HttpClient,
    val httpTokenClient: HttpClient
) {
    data class AzureAuth(
        val azureGrantType: AzureGrantType,
        val azureTokenEndpoint: AzureTokenEndpoint,
        val azureAppClientId: AzureApplicationId,
        val azureAppClientSecret: AzureApplicationSecret
    ) {
        @JvmInline
        value class AzureGrantType(val value: String)

        @JvmInline
        value class AzureTokenEndpoint(val value: String)

        @JvmInline
        value class AzureApplicationId(val value: String)

        @JvmInline
        value class AzureApplicationSecret(val value: String)
    }

    data class HttpClient(
        val connectionTimeout: Duration
    )
}
