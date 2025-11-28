package no.nav.emottak.ediadapter.server.config

import io.ktor.client.plugins.logging.LogLevel
import no.nav.emottak.utils.config.Server
import java.net.URI

data class Config(
    val nhn: Nhn,
    val azureAuth: AzureAuth,
    val server: Server,
    val httpClient: HttpClient,
    val httpTokenClient: HttpTokenClient
)

data class Nhn(
    val baseUrl: URI,
    val keyPairPath: KeyPairPath
) {
    @JvmInline
    value class KeyPairPath(val value: String)
}

data class AzureAuth(
    val keyId: KeyId,
    val clientId: ClientId,
    val audience: Audience,
    val tokenEndpoint: URI,
    val scope: Scope,
    val grantType: GrantType,
    val clientAssertionType: ClientAssertionType
) {
    @JvmInline
    value class KeyId(val value: String)

    @JvmInline
    value class ClientId(val value: String)

    @JvmInline
    value class Audience(val value: String)

    @JvmInline
    value class Scope(val value: String)

    @JvmInline
    value class GrantType(val value: String)

    @JvmInline
    value class ClientAssertionType(val value: String)
}

@JvmInline
value class Timeout(val value: Long)

data class HttpClient(
    val connectionTimeout: Timeout,
    val apiVersionHeader: ApiVersionHeader,
    val sourceSystemHeader: SourceSystemHeader,
    val acceptTypeHeader: AcceptTypeHeader,
    val logLevel: LogLevel
) {
    @JvmInline
    value class ApiVersionHeader(val value: String)

    @JvmInline
    value class SourceSystemHeader(val value: String)

    @JvmInline
    value class AcceptTypeHeader(val value: String)
}

data class HttpTokenClient(val connectionTimeout: Timeout)
