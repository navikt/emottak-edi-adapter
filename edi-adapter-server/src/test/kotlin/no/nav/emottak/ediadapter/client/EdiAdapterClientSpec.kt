package no.nav.emottak.ediadapter.client

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.equality.shouldBeEqualUsingFields
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpHeaders.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.fullPath
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import no.nav.emottak.ediadapter.model.ErrorMessage
import no.nav.emottak.ediadapter.model.Message
import no.nav.emottak.ediadapter.model.Metadata
import no.nav.emottak.ediadapter.model.PostMessageRequest
import no.nav.emottak.ediadapter.server.plugin.base64EncodedDocument
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.Uuid
import kotlinx.serialization.json.Json as JsonUtil

@OptIn(ExperimentalTime::class)
class EdiAdapterClientSpec : StringSpec(
    {

        "getMessage returns message and no messageError if ediAdapterClient returns 200" {
            val uuid = Uuid.random()
            val existingMessage = Message(
                id = uuid,
                contentType = "application/xml",
                receiverHerId = 42,
                senderHerId = 1111,
                businessDocumentId = Uuid.random().toString(),
                businessDocumentGenDate = Clock.System.now(),
                isAppRec = false,
                sourceSystem = "Source system"
            )
            val ediClient = ediAdapterClient {
                fakeScopedAuthHttpClient { request ->
                    request.method shouldBe HttpMethod.Get
                    request.url.fullPath shouldBe "/api/v1/messages/$uuid"

                    respond(
                        content = JsonUtil.encodeToString(existingMessage),
                        headers = headersOf(ContentType, Json.toString()),
                        status = HttpStatusCode.OK
                    )
                }
            }

            val (message, errorMessage) = ediClient.getMessage(uuid)

            message.shouldNotBeNull()
            existingMessage shouldBeEqualUsingFields message
            errorMessage.shouldBeNull()
        }

        "getMessage returns messageError and no message if ediAdapterClient returns 404" {
            val uuid = Uuid.random()
            val errorMessageStub = ErrorMessage(
                error = "Not Found",
                errorCode = 1000,
                validationErrors = listOf("Example error"),
                stackTrace = "[StackTrace]",
                requestId = Uuid.random().toString()
            )
            val ediClient = ediAdapterClient {
                fakeScopedAuthHttpClient { request ->
                    request.method shouldBe HttpMethod.Get
                    request.url.fullPath shouldBe "/api/v1/messages/$uuid"

                    respond(
                        content = JsonUtil.encodeToString(errorMessageStub),
                        headers = headersOf(ContentType, Json.toString()),
                        status = HttpStatusCode.NotFound
                    )
                }
            }

            val (message, errorMessage) = ediClient.getMessage(uuid)

            message.shouldBeNull()
            errorMessage.shouldNotBeNull()
            errorMessage shouldBeEqualUsingFields errorMessageStub
        }

        "postMessage returns metadata and no messageError if ediAdapterClient returns 201" {
            val metadataStub = Metadata(
                id = Uuid.random(),
                location = "https://example.com/messages/1"
            )
            val ediClient = ediAdapterClient {
                fakeScopedAuthHttpClient { request ->
                    request.method shouldBe HttpMethod.Post
                    request.url.fullPath shouldBe "/api/v1/messages"

                    respond(
                        content = JsonUtil.encodeToString(metadataStub),
                        headers = headersOf(ContentType, Json.toString()),
                        status = HttpStatusCode.Created
                    )
                }
            }

            val request = PostMessageRequest(
                businessDocument = base64EncodedDocument(),
                contentType = "application/xml",
                contentTransferEncoding = "base64"
            )
            val (metadata, errorMessage) = ediClient.postMessage(request)

            metadata.shouldNotBeNull()
            metadata shouldBeEqualUsingFields metadataStub
            errorMessage.shouldBeNull()
        }

        "postMessage returns messageError and no metadata if ediAdapterClient returns 400" {
            val errorMessageStub = ErrorMessage(
                error = "Model validation error occurred",
                errorCode = 1000,
                validationErrors = listOf("Example error"),
                stackTrace = "[StackTrace]",
                requestId = Uuid.random().toString()
            )
            val ediClient = ediAdapterClient {
                fakeScopedAuthHttpClient { request ->
                    request.method shouldBe HttpMethod.Post
                    request.url.fullPath shouldBe "/api/v1/messages"

                    respond(
                        content = JsonUtil.encodeToString(errorMessageStub),
                        headers = headersOf(ContentType, Json.toString()),
                        status = HttpStatusCode.BadRequest
                    )
                }
            }

            val badRequest = PostMessageRequest(
                businessDocument = base64EncodedDocument(),
                contentType = "",
                contentTransferEncoding = ""
            )
            val (metadata, errorMessage) = ediClient.postMessage(badRequest)

            metadata.shouldBeNull()
            errorMessage.shouldNotBeNull()
            errorMessage shouldBeEqualUsingFields errorMessageStub
        }
    }
)

private fun ediAdapterClient(httpClient: () -> HttpClient) = EdiAdapterClient(
    ediAdapterUrl = "http://localhost",
    clientProvider = httpClient
)

private fun fakeScopedAuthHttpClient(
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
) = HttpClient(MockEngine) {
    engine { addHandler(handler) }
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
        )
    }
}
