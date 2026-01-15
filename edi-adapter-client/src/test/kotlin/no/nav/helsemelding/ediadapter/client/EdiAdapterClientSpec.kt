package no.nav.helsemelding.ediadapter.client

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.equality.shouldBeEqualUsingFields
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
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
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import no.nav.helsemelding.ediadapter.model.AppRecStatus
import no.nav.helsemelding.ediadapter.model.ApprecInfo
import no.nav.helsemelding.ediadapter.model.DeliveryState
import no.nav.helsemelding.ediadapter.model.ErrorMessage
import no.nav.helsemelding.ediadapter.model.GetBusinessDocumentResponse
import no.nav.helsemelding.ediadapter.model.GetMessagesRequest
import no.nav.helsemelding.ediadapter.model.Message
import no.nav.helsemelding.ediadapter.model.Metadata
import no.nav.helsemelding.ediadapter.model.PostAppRecRequest
import no.nav.helsemelding.ediadapter.model.PostMessageRequest
import no.nav.helsemelding.ediadapter.model.StatusInfo
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.text.Charsets.UTF_8
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.Uuid
import kotlinx.serialization.json.Json as JsonUtil

@OptIn(ExperimentalTime::class)
class EdiAdapterClientSpec : StringSpec(
    {

        val errorMessage500 = ErrorMessage(
            error = "Internal Server Error",
            errorCode = 1000,
            validationErrors = listOf("Example error"),
            stackTrace = "[StackTrace]",
            requestId = Uuid.random().toString()
        )

        val errorMessage404 = ErrorMessage(
            error = "Not Found",
            errorCode = 1000,
            validationErrors = listOf("Example error"),
            stackTrace = "[StackTrace]",
            requestId = Uuid.random().toString()
        )

        "getApprecInfo returns apprecInfos and no messageError if response is 200" {
            val apprecInfosStub = listOf(
                ApprecInfo(
                    receiverHerId = 1,
                    appRecStatus = AppRecStatus.OK,
                    appRecErrorList = emptyList()
                )
            )
            val uuid = Uuid.random()
            val ediClient = ediAdapterClient {
                fakeScopedAuthHttpClient { request ->
                    request.method shouldBe HttpMethod.Get
                    request.url.fullPath shouldBeEqual "/api/v1/messages/$uuid/apprec"

                    respond(
                        content = JsonUtil.encodeToString(apprecInfosStub),
                        headers = headersOf(ContentType, Json.toString()),
                        status = HttpStatusCode.OK
                    )
                }
            }

            val apprecInfos = ediClient.getApprecInfo(uuid).shouldBeRight()
            apprecInfos shouldContainExactly apprecInfosStub
        }

        "getApprecInfo returns messageError and no apprecInfos if response is 404" {
            val uuid = Uuid.random()
            val ediClient = ediAdapterClient {
                fakeScopedAuthHttpClient { request ->
                    request.method shouldBe HttpMethod.Get
                    request.url.fullPath shouldBeEqual "/api/v1/messages/$uuid/apprec"

                    respond(
                        content = JsonUtil.encodeToString(errorMessage404),
                        headers = headersOf(ContentType, Json.toString()),
                        status = HttpStatusCode.NotFound
                    )
                }
            }

            val errorMessage = ediClient.getApprecInfo(uuid).shouldBeLeft()
            errorMessage shouldBeEqualUsingFields errorMessage404
        }

        "getMessages returns messages and no messageError if response is 200" {
            val messagesStub = listOf(Message(receiverHerId = 1))
            val ediClient = ediAdapterClient {
                fakeScopedAuthHttpClient { request ->
                    request.method shouldBe HttpMethod.Get
                    request.url.fullPath shouldStartWith "/api/v1/messages?receiverHerIds=1"

                    respond(
                        content = JsonUtil.encodeToString(messagesStub),
                        headers = headersOf(ContentType, Json.toString()),
                        status = HttpStatusCode.OK
                    )
                }
            }

            val getMessagesRequest = GetMessagesRequest(receiverHerIds = listOf(1))
            val messages = ediClient.getMessages(getMessagesRequest).shouldBeRight()
            messages shouldContainExactly messagesStub
        }

        "getMessages returns messageError and no messages if response is 500" {
            val ediClient = ediAdapterClient {
                fakeScopedAuthHttpClient { request ->
                    request.method shouldBe HttpMethod.Get
                    request.url.fullPath shouldStartWith "/api/v1/messages?receiverHerIds=1"

                    respond(
                        content = JsonUtil.encodeToString(errorMessage500),
                        headers = headersOf(ContentType, Json.toString()),
                        status = HttpStatusCode.InternalServerError
                    )
                }
            }

            val getMessagesRequest = GetMessagesRequest(receiverHerIds = listOf(1))
            val errorMessage = ediClient.getMessages(getMessagesRequest).shouldBeLeft()
            errorMessage shouldBeEqualUsingFields errorMessage500
        }

        "postMessage returns metadata and no messageError if response is 201" {
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
            val metadata = ediClient.postMessage(request).shouldBeRight()
            metadata shouldBeEqualUsingFields metadataStub
        }

        "postMessage returns messageError and no metadata if response is 500" {
            val ediClient = ediAdapterClient {
                fakeScopedAuthHttpClient { request ->
                    request.method shouldBe HttpMethod.Post
                    request.url.fullPath shouldBe "/api/v1/messages"

                    respond(
                        content = JsonUtil.encodeToString(errorMessage500),
                        headers = headersOf(ContentType, Json.toString()),
                        status = HttpStatusCode.InternalServerError
                    )
                }
            }

            val postMessageRequest = PostMessageRequest(
                businessDocument = base64EncodedDocument(),
                contentType = "application/xml",
                contentTransferEncoding = "base64"
            )
            val errorMessage = ediClient.postMessage(postMessageRequest).shouldBeLeft()
            errorMessage shouldBeEqualUsingFields errorMessage500
        }

        "getMessage returns message and no messageError if response is 200" {
            val uuid = Uuid.random()
            val messageStub = Message(
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
                        content = JsonUtil.encodeToString(messageStub),
                        headers = headersOf(ContentType, Json.toString()),
                        status = HttpStatusCode.OK
                    )
                }
            }

            val message = ediClient.getMessage(uuid).shouldBeRight()
            message shouldBeEqualUsingFields messageStub
        }

        "getMessage returns messageError and no message if response is 404" {
            val uuid = Uuid.random()
            val ediClient = ediAdapterClient {
                fakeScopedAuthHttpClient { request ->
                    request.method shouldBe HttpMethod.Get
                    request.url.fullPath shouldBe "/api/v1/messages/$uuid"

                    respond(
                        content = JsonUtil.encodeToString(errorMessage404),
                        headers = headersOf(ContentType, Json.toString()),
                        status = HttpStatusCode.NotFound
                    )
                }
            }

            val errorMessage = ediClient.getMessage(uuid).shouldBeLeft()
            errorMessage shouldBeEqualUsingFields errorMessage404
        }

        "getBusinessDocument returns business document and no messageError if response is 200" {
            val businessDocumentResponseStub = GetBusinessDocumentResponse(
                businessDocument = base64EncodedDocument(),
                contentType = "application/xml",
                contentTransferEncoding = "base64"
            )
            val uuid = Uuid.random()
            val ediClient = ediAdapterClient {
                fakeScopedAuthHttpClient { request ->
                    request.method shouldBe HttpMethod.Get
                    request.url.fullPath shouldBeEqual "/api/v1/messages/$uuid/document"

                    respond(
                        content = JsonUtil.encodeToString(businessDocumentResponseStub),
                        headers = headersOf(ContentType, Json.toString()),
                        status = HttpStatusCode.OK
                    )
                }
            }

            val businessDocumentResponse = ediClient.getBusinessDocument(uuid).shouldBeRight()
            businessDocumentResponse shouldBeEqualUsingFields businessDocumentResponseStub
        }

        "getBusinessDocument returns messageError and no business document if response is 404" {
            val uuid = Uuid.random()
            val ediClient = ediAdapterClient {
                fakeScopedAuthHttpClient { request ->
                    request.method shouldBe HttpMethod.Get
                    request.url.fullPath shouldBeEqual "/api/v1/messages/$uuid/document"

                    respond(
                        content = JsonUtil.encodeToString(errorMessage404),
                        headers = headersOf(ContentType, Json.toString()),
                        status = HttpStatusCode.NotFound
                    )
                }
            }

            val errorMessage = ediClient.getBusinessDocument(uuid).shouldBeLeft()
            errorMessage shouldBeEqualUsingFields errorMessage404
        }

        "getMessageStatus returns statusInfos and no messageError if response is 200" {
            val statusInfosStub = listOf(
                StatusInfo(
                    receiverHerId = 1,
                    transportDeliveryState = DeliveryState.ACKNOWLEDGED,
                    sent = true
                )
            )
            val uuid = Uuid.random()
            val ediClient = ediAdapterClient {
                fakeScopedAuthHttpClient { request ->
                    request.method shouldBe HttpMethod.Get
                    request.url.fullPath shouldBeEqual "/api/v1/messages/$uuid/status"

                    respond(
                        content = JsonUtil.encodeToString(statusInfosStub),
                        headers = headersOf(ContentType, Json.toString()),
                        status = HttpStatusCode.OK
                    )
                }
            }

            val statusInfos = ediClient.getMessageStatus(uuid).shouldBeRight()
            statusInfos shouldContainExactly statusInfosStub
        }

        "getMessageStatus returns messageError and no statusInfos if response is 404" {
            val uuid = Uuid.random()
            val ediClient = ediAdapterClient {
                fakeScopedAuthHttpClient { request ->
                    request.method shouldBe HttpMethod.Get
                    request.url.fullPath shouldBeEqual "/api/v1/messages/$uuid/status"

                    respond(
                        content = JsonUtil.encodeToString(errorMessage404),
                        headers = headersOf(ContentType, Json.toString()),
                        status = HttpStatusCode.NotFound
                    )
                }
            }

            val errorMessage = ediClient.getMessageStatus(uuid).shouldBeLeft()
            errorMessage shouldBeEqualUsingFields errorMessage404
        }

        "postApprec returns metadata and no messageError if response is 201" {
            val metadataStub = Metadata(
                id = Uuid.random(),
                location = "https://example.com/messages/1"
            )
            val uuid = Uuid.random()
            val apprecSenderHerId = 100
            val ediClient = ediAdapterClient {
                fakeScopedAuthHttpClient { request ->
                    request.method shouldBe HttpMethod.Post
                    request.url.fullPath shouldBe "/api/v1/messages/$uuid/apprec/$apprecSenderHerId"

                    respond(
                        content = JsonUtil.encodeToString(metadataStub),
                        headers = headersOf(ContentType, Json.toString()),
                        status = HttpStatusCode.Created
                    )
                }
            }

            val apprecRequest = PostAppRecRequest(
                appRecStatus = AppRecStatus.OK
            )
            val metadata = ediClient.postApprec(uuid, apprecSenderHerId, apprecRequest).shouldBeRight()
            metadata shouldBeEqualUsingFields metadataStub
        }

        "postApprec returns messageError and no metadata if response is 500" {
            val uuid = Uuid.random()
            val apprecSenderHerId = 100
            val ediClient = ediAdapterClient {
                fakeScopedAuthHttpClient { request ->
                    request.method shouldBe HttpMethod.Post
                    request.url.fullPath shouldBe "/api/v1/messages/$uuid/apprec/$apprecSenderHerId"

                    respond(
                        content = JsonUtil.encodeToString(errorMessage500),
                        headers = headersOf(ContentType, Json.toString()),
                        status = HttpStatusCode.InternalServerError
                    )
                }
            }

            val apprecRequest = PostAppRecRequest(
                appRecStatus = AppRecStatus.OK
            )
            val errorMessage = ediClient.postApprec(uuid, apprecSenderHerId, apprecRequest).shouldBeLeft()
            errorMessage shouldBeEqualUsingFields errorMessage500
        }

        "markMessageAsRead returns true and no messageError if response is 204" {
            val uuid = Uuid.random()
            val herId = 1
            val ediClient = ediAdapterClient {
                fakeScopedAuthHttpClient { request ->
                    request.method shouldBe HttpMethod.Put
                    request.url.fullPath shouldBeEqual "/api/v1/messages/$uuid/read/$herId"

                    respond(
                        content = JsonUtil.encodeToString(true),
                        headers = headersOf(ContentType, Json.toString()),
                        status = HttpStatusCode.NoContent
                    )
                }
            }

            val isMessagesMarkedAsRead = ediClient.markMessageAsRead(uuid, herId).shouldBeRight()
            isMessagesMarkedAsRead.shouldBeTrue()
        }

        "markMessageAsRead returns messageError and no boolean value if response is 404" {
            val uuid = Uuid.random()
            val herId = 1
            val ediClient = ediAdapterClient {
                fakeScopedAuthHttpClient { request ->
                    request.method shouldBe HttpMethod.Put
                    request.url.fullPath shouldBeEqual "/api/v1/messages/$uuid/read/$herId"

                    respond(
                        content = JsonUtil.encodeToString(errorMessage404),
                        headers = headersOf(ContentType, Json.toString()),
                        status = HttpStatusCode.NotFound
                    )
                }
            }

            val errorMessage = ediClient.markMessageAsRead(uuid, herId).shouldBeLeft()
            errorMessage shouldBeEqualUsingFields errorMessage404
        }

        "throws cancellationException if attempting to use ediClientAdapter after calling close()" {
            val ediClient = ediAdapterClient {
                fakeScopedAuthHttpClient { request ->
                    respond(
                        content = JsonUtil.encodeToString(errorMessage404),
                        headers = headersOf(ContentType, Json.toString()),
                        status = HttpStatusCode.NotFound
                    )
                }
            }

            ediClient.close()

            shouldThrow<CancellationException> {
                ediClient.getMessage(Uuid.random())
            }
        }
    }
)

private fun ediAdapterClient(httpClient: () -> HttpClient) = HttpEdiAdapterClient(
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

@OptIn(ExperimentalEncodingApi::class)
fun base64EncodedDocument(): String =
    Base64.encode(
        """"<MsgHead><Body>hello world</Body></MsgHead>""""
            .trimIndent()
            .toByteArray(UTF_8)
    )
