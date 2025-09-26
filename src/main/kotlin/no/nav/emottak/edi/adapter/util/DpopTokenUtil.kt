package no.nav.emottak.edi.adapter.util

import com.nimbusds.oauth2.sdk.token.AccessTokenType.DPOP
import com.nimbusds.openid.connect.sdk.Nonce
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType.Application.FormUrlEncoded
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.formUrlEncode
import no.nav.emottak.edi.adapter.config.AzureAuth
import no.nav.emottak.edi.adapter.model.DpopTokens
import no.nav.emottak.edi.adapter.model.TokenInfo
import no.nav.emottak.edi.adapter.model.toDpopTokens

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
