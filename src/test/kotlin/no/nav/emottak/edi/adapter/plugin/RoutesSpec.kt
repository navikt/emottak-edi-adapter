package no.nav.emottak.edi.adapter.plugin

import com.nimbusds.jwt.SignedJWT
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.NoContent
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.http.fullPath
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.TestApplicationBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import no.nav.emottak.config
import no.nav.emottak.edi.adapter.auth.AuthConfig
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.token.support.v3.tokenValidationSupport
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.text.Charsets.UTF_8
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation

class RoutesSpec : StringSpec(
    {
        lateinit var mockOAuth2Server: MockOAuth2Server

        val getToken: (String) -> SignedJWT = { audience: String ->
            mockOAuth2Server.issueToken(
                issuerId = config().azureAuth.issuer.value,
                audience = audience,
                subject = "testUser"
            )
        }

        val invalidAudience = "api://dev-fss.team-emottak.some-other-service/.default"

        beforeSpec {
            mockOAuth2Server = MockOAuth2Server().also { it.start(port = 3344) }
        }

        "GET /messages returns EDI response" {
            val ediClient = fakeEdiClient {
                it.url.fullPath shouldBe "/Messages?ReceiverHerIds=1"
                respond("""[{"id":"1"}]""")
            }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/messages?id=1")

                response.status shouldBe OK
                response.bodyAsText() shouldBe """[{"id":"1"}]"""
            }
        }

        "GET /messages with blank id returns 400" {
            val ediClient = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/messages?id=")
                response.status shouldBe BadRequest
                response.bodyAsText() shouldContain "Message ids"
            }
        }

        "GET /messages without id returns 400" {
            val ediClient = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/messages")

                response.status shouldBe BadRequest
                response.bodyAsText() shouldContain "Message ids"
            }
        }

        "GET /messages/{id} returns EDI response" {
            val ediClient = fakeEdiClient { request ->
                request.url.fullPath shouldBe "/Messages/42"
                respond("OK")
            }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/messages/42")

                response.status shouldBe OK
                response.bodyAsText() shouldBe "OK"
            }
        }

        "GET /messages/{id} with blank id returns 400" {
            val ediClient = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/messages/%20")

                response.status shouldBe BadRequest
                response.bodyAsText() shouldContain "Message id"
            }
        }

        "GET /messages/{id} missing id returns 404" {
            val ediClient = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/messages/")

                response.status shouldBe NotFound
            }
        }

        "GET /messages/{id}/document returns EDI response" {
            val ediClient = fakeEdiClient { request ->
                request.url.fullPath shouldBe "/Messages/99/business-document"
                respond("<xml>doc</xml>")
            }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/messages/99/document")

                response.status shouldBe OK
                response.bodyAsText() shouldBe "<xml>doc</xml>"
            }
        }

        "GET /messages/{id}/status returns EDI response" {
            val ediClient = fakeEdiClient { request ->
                request.url.fullPath shouldBe "/Messages/55/status"
                respond("""{"status":"READ"}""")
            }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/messages/55/status")

                response.status shouldBe OK
                response.bodyAsText() shouldBe """{"status":"READ"}"""
            }
        }

        "GET /messages/{id}/apprec returns EDI response" {
            val ediClient = fakeEdiClient { request ->
                request.url.fullPath shouldBe "/Messages/10/apprec"
                respond("""{"apprec":"OK"}""")
            }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/messages/10/apprec")

                response.status shouldBe OK
                response.bodyAsText() shouldBe """{"apprec":"OK"}"""
            }
        }

        "POST /messages forwards body to EDI" {
            val ediClient = fakeEdiClient { request ->
                request.url.fullPath shouldBe "/Messages"
                (request.body as TextContent).text shouldContain "HealthInformation"
                respond("""{"result":"created"}""")
            }

            testApplication {
                installExternalRoutes(ediClient)

                val message =
                    """
                {
                  "messageType":"HealthInformation",
                  "recipient":"mottak-qass@test-es.nav.no",
                  "businessDocument": ${base64EncodedDocument()}
                }
                """

                val response = client.post("/api/v1/messages") {
                    contentType(Json)
                    setBody(message)
                }

                response.status shouldBe OK
                response.bodyAsText() shouldBe """{"result":"created"}"""
            }
        }

        "POST /messages with empty body returns 400 or 415" {
            val ediClient = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.post("/api/v1/messages") {
                    contentType(Json)
                    setBody("")
                }
                response.status.value shouldBeIn listOf(400, 415)
            }
        }

        "POST /messages/{id}/apprec/{sender} forwards body" {
            val ediClient = fakeEdiClient { request ->
                request.url.fullPath shouldBe "/Messages/77/apprec/8142"
                (request.body as TextContent).text shouldContain "1"
                respond("""{"status":"ok"}""")
            }

            testApplication {
                installExternalRoutes(ediClient)

                val apprecBody = """{ "appRecStatus":"1", "appRecErrorList":[] }"""

                val response = client.post("/api/v1/messages/77/apprec/8142") {
                    contentType(Json)
                    setBody(apprecBody)
                }

                response.status shouldBe OK
                response.bodyAsText() shouldBe """{"status":"ok"}"""
            }
        }

        "POST /messages/{id}/apprec/{sender} with blank sender returns 400" {
            val ediClient = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.post("/api/v1/messages/77/apprec/%20") {
                    contentType(Json)
                    setBody("""{ "appRecStatus":"1", "appRecErrorList":[] }""")
                }

                response.status shouldBe BadRequest
                response.bodyAsText() shouldContain "Sender"
            }
        }

        "POST /messages/{id}/apprec missing sender returns 404" {
            val ediClient = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.post("/api/v1/messages/77/apprec/") {
                    contentType(Json)
                    setBody("""{"status":"1"}""")
                }
                response.status shouldBe NotFound
            }
        }

        "PUT /messages/{id}/read/{herId} marks message as read" {
            val ediClient = fakeEdiClient { request ->
                request.url.fullPath shouldBe "/messages/5/read/111"
                respond("", status = NoContent)
            }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.put("/api/v1/messages/5/read/111")

                response.status shouldBe NoContent
            }
        }

        "PUT /messages/{id}/read/{herId} with blank herId returns 400" {
            val ediClient = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.put("/api/v1/messages/5/read/%20")

                response.status shouldBe BadRequest
                response.bodyAsText() shouldContain "Her id"
            }
        }

        "PUT /messages/{id}/read missing herId returns 404" {
            val ediClient = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.put("/api/v1/messages/5/read/")

                response.status shouldBe NotFound
            }
        }

        "GET /messages returns EDI response with authentication" {
            val ediClient = fakeEdiClient {
                it.url.fullPath shouldBe "/Messages?ReceiverHerIds=1"
                respond("""[{"id":"1"}]""")
            }

            testApplication {
                installExternalRoutes(ediClient, useAuthentication = true)

                val response = client.getWithAuth("/api/v1/messages?id=1", getToken)

                response.status shouldBe OK
                response.bodyAsText() shouldBe """[{"id":"1"}]"""
            }
        }

        "GET /messages returns Unauthorised if access token is missing" {
            val ediClient = fakeEdiClient {
                it.url.fullPath shouldBe "/Messages?ReceiverHerIds=1"
                respond("""[{"id":"1"}]""")
            }

            testApplication {
                installExternalRoutes(ediClient, useAuthentication = true)

                val response = client.get("/api/v1/messages?id=1")

                response.status shouldBe Unauthorized
            }
        }

        "GET /messages returns Unauthorised if access token is invalid" {
            val ediClient = fakeEdiClient {
                it.url.fullPath shouldBe "/Messages?ReceiverHerIds=1"
                respond("""[{"id":"1"}]""")
            }

            testApplication {
                installExternalRoutes(ediClient, useAuthentication = true)

                val response = client.getWithAuth("/api/v1/messages?id=1", getToken, invalidAudience)

                response.status shouldBe Unauthorized
            }
        }
    }
)

private fun TestApplicationBuilder.installExternalRoutes(ediClient: HttpClient, useAuthentication: Boolean = false) {
    install(ContentNegotiation) { json() }
    if (useAuthentication) {
        install(Authentication) {
            tokenValidationSupport(config().azureAuth.issuer.value, AuthConfig.getTokenSupportConfig())
        }
    }
    routing {
        if (useAuthentication) {
            authenticate(config().azureAuth.issuer.value) {
                externalRoutes(ediClient)
            }
        } else {
            externalRoutes(ediClient)
        }
    }
}

private fun fakeEdiClient(
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
) = HttpClient(MockEngine) {
    engine {
        addHandler(handler)
    }
    install(ClientContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
        )
    }
}

@OptIn(ExperimentalEncodingApi::class)
private fun base64EncodedDocument(): String =
    Base64.encode(
        """"<MsgHead><Body>hello world</Body></MsgHead>""""
            .trimIndent()
            .toByteArray(UTF_8)
    )

suspend fun HttpClient.getWithAuth(
    url: String,
    getToken: (String) -> SignedJWT,
    audience: String = AuthConfig.getScope()
): io.ktor.client.statement.HttpResponse {
    return this.get(url) {
        header(
            "Authorization",
            "Bearer ${getToken(audience).serialize()}"
        )
    }
}
