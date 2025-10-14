package no.nav.emottak.edi.adapter.util

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.extensions.system.withEnvironment
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders.ContentType
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import no.nav.emottak.config
import no.nav.emottak.generateRsaJwk

class DpopTokenUtilSpec : StringSpec(
    {
        val rsaJwk = generateRsaJwk()
        val rsaJwkJson = rsaJwk.toString()

        "should return tokens immediately if first call is 200" {
            withEnvironment("emottak-nhn-edi", rsaJwkJson) {
                val config = config().azureAuth

                val engine = MockEngine { _ ->
                    respond(
                        content = """{
                    "access_token":"token-abc",
                    "expires_in":3600,
                    "token_type":"DPoP"
                }""",
                        status = OK,
                        headers = headersOf(ContentType, "application/json")
                    )
                }

                val client = HttpClient(engine) {
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true })
                    }
                }

                val tokens = obtainDpopTokens(config, client)

                tokens.accessToken.value shouldBe "token-abc"
                tokens.accessToken.type.value shouldBe "DPoP"
            }
        }

        "should retry with nonce if first call returns 400" {
            withEnvironment("emottak-nhn-edi", rsaJwkJson) {
                val config = config().azureAuth
                var callCount = 0
                val engine = MockEngine { _ ->
                    callCount++
                    if (callCount == 1) {
                        respond(
                            content = """{"error":"invalid_request"}""",
                            status = BadRequest,
                            headers = headersOf("DPoP-Nonce", "nonce-xyz")
                        )
                    } else {
                        respond(
                            content = """{
                        "access_token":"token-retried",
                        "expires_in":3600,
                        "token_type":"DPoP"
                    }""",
                            status = OK,
                            headers = headersOf(ContentType, "application/json")
                        )
                    }
                }

                val client = HttpClient(engine) {
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true })
                    }
                }
                val tokens = obtainDpopTokens(config, client)

                tokens.accessToken.value shouldBe "token-retried"
            }
        }

        "should throw if final response is not 200" {
            withEnvironment("emottak-nhn-edi", rsaJwkJson) {
                val config = config().azureAuth

                val engine = MockEngine { _ ->
                    respond(
                        content = """{"error":"unauthorized"}""",
                        status = Unauthorized
                    )
                }

                val client = HttpClient(engine)
                shouldThrow<IllegalStateException> {
                    runBlocking { obtainDpopTokens(config, client) }
                }
                    .message shouldBe "Failed to obtain DPoP token: 401 Unauthorized - {\"error\":\"unauthorized\"}"
            }
        }
    }
)
