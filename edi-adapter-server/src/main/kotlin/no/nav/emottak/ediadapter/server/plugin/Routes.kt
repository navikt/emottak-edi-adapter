package no.nav.emottak.ediadapter.server.plugin

import arrow.core.raise.Raise
import arrow.core.raise.recover
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.openApi
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.put
import io.github.smiley4.ktorswaggerui.swaggerUI
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpHeaders.Location
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NoContent
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.Parameters
import io.ktor.http.ParametersBuilder
import io.ktor.http.contentType
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.emottak.ediadapter.model.AppRecError
import no.nav.emottak.ediadapter.model.AppRecStatus
import no.nav.emottak.ediadapter.model.AppRecStatus.OK_ERROR_IN_MESSAGE_PART
import no.nav.emottak.ediadapter.model.ApprecInfo
import no.nav.emottak.ediadapter.model.DeliveryState.ACKNOWLEDGED
import no.nav.emottak.ediadapter.model.EbXmlInfo
import no.nav.emottak.ediadapter.model.GetBusinessDocumentResponse
import no.nav.emottak.ediadapter.model.Message
import no.nav.emottak.ediadapter.model.Metadata
import no.nav.emottak.ediadapter.model.OrderBy
import no.nav.emottak.ediadapter.model.PostAppRecRequest
import no.nav.emottak.ediadapter.model.PostMessageRequest
import no.nav.emottak.ediadapter.model.StatusInfo
import no.nav.emottak.ediadapter.server.MessageError
import no.nav.emottak.ediadapter.server.ValidationError
import no.nav.emottak.ediadapter.server.apprecSenderHerId
import no.nav.emottak.ediadapter.server.businessDocumentId
import no.nav.emottak.ediadapter.server.config
import no.nav.emottak.ediadapter.server.herId
import no.nav.emottak.ediadapter.server.includeMetadata
import no.nav.emottak.ediadapter.server.messageId
import no.nav.emottak.ediadapter.server.messagesToFetch
import no.nav.emottak.ediadapter.server.orderBy
import no.nav.emottak.ediadapter.server.receiverHerIds
import no.nav.emottak.ediadapter.server.senderHerId
import no.nav.emottak.ediadapter.server.toContent
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.serialization.json.Json as JsonUtil

private val log = KotlinLogging.logger { }

private const val RECEIVER_HER_IDS = "ReceiverHerIds"
private const val SENDER_HER_ID = "SenderHerId"
private const val BUSINESS_DOCUMENT_ID = "BusinessDocumentId"
private const val INCLUDE_METADATA = "IncludeMetadata"
private const val MESSAGES_TO_FETCH = "MessagesToFetch"
private const val ORDER_BY = "OrderBy"

fun Application.configureRoutes(
    ediClient: HttpClient,
    registry: PrometheusMeterRegistry
) {
    routing {
        swaggerRoutes()
        internalRoutes(registry)

        authenticate(config().azureAuth.issuer.value) {
            externalRoutes(ediClient)
        }
    }
}

fun Route.swaggerRoutes() {
    route("api.json") {
        openApi()
    }
    route("swagger") {
        swaggerUI("/api.json") {
        }
    }
}

fun Route.internalRoutes(registry: PrometheusMeterRegistry) {
    get("/prometheus") {
        call.respond(registry.scrape())
    }
    route("/internal") {
        get("/health/liveness") {
            call.respondText("I'm alive! :)")
        }
        get("/health/readiness") {
            call.respondText("I'm ready! :)")
        }
    }
}

fun Route.externalRoutes(ediClient: HttpClient) {
    route("/api/v1") {
        get("/messages", {
            summary = "Get a list of unread messages"
            description = "Get a list of unread messages using the given query parameters"

            request {
                queryParameter<List<Int>>("receiverHerIds") {
                    description = "List of receiver HER IDs"
                    required = true

                    example("Multiple receivers") {
                        summary = "Multiple receiver HER IDs"
                        description = "At least one receiver HER ID is required"
                        value = listOf(8142520, 8142521)
                    }
                }

                queryParameter<Int>("senderHerId") {
                    description = "Sender HER ID"
                    required = false

                    example("Sender HER ID") {
                        summary = "Sender filter"
                        description = "Filter messages by sender HER ID"
                        value = 8142519
                    }
                }

                queryParameter<String>("businessDocumentId") {
                    description = "Business document UUID"
                    required = false

                    example("Business document ID") {
                        summary = "Document filter"
                        description = "Filter messages by business document ID"
                        value = "cc169595-bbf0-11dd-9ca9-117f241b4a68"
                    }
                }

                queryParameter<Boolean>("includeMetadata") {
                    description = "Whether to include message metadata (default: false)"
                    required = false

                    example("Default") {
                        summary = "Exclude metadata"
                        description = "Metadata is excluded by default"
                        value = false
                    }

                    example("Include metadata") {
                        summary = "Include metadata"
                        description = "Returns extended message fields"
                        value = true
                    }
                }

                queryParameter<Int>("messagesToFetch") {
                    description = "Number of messages to fetch (1–100, default: 10)"
                    required = false

                    example("Default") {
                        summary = "Default value"
                        description = "Fetch default number of messages"
                        value = 10
                    }

                    example("Maximum") {
                        summary = "Maximum value"
                        description = "Fetch the maximum allowed number of messages"
                        value = 100
                    }
                }

                queryParameter<OrderBy>("orderBy") {
                    description = "Message ordering (default: ASC)"
                    required = false

                    example("Ascending") {
                        summary = "Ascending order"
                        description = "Oldest messages first"
                        value = OrderBy.ASC
                    }

                    example("Descending") {
                        summary = "Descending order"
                        description = "Newest messages first"
                        value = OrderBy.DESC
                    }
                }
            }

            response {
                OK to {
                    description = """
            Messages retrieved successfully.
            Response fields depend on `includeMetadata`.
                    """.trimIndent()

                    body<List<Message>> {
                        example("Without metadata") {
                            summary = "Messages without metadata"
                            value = listOf(
                                Message(
                                    id = Uuid.parse("733be787-0ad0-475a-98b7-00512caa9ccb"),
                                    receiverHerId = 8142520
                                ),
                                Message(
                                    id = Uuid.parse("68e60a2b-5990-408c-b99b-089d8657d6ed"),
                                    receiverHerId = 8142520
                                )
                            )
                        }

                        example("With metadata") {
                            summary = "Messages with metadata"
                            value = listOf(
                                Message(
                                    id = Uuid.parse("733be787-0ad0-475a-98b7-00512caa9ccb"),
                                    contentType = "application/xml",
                                    receiverHerId = 8142520,
                                    senderHerId = 8142519,
                                    businessDocumentId = "cc169595-bbf0-11dd-9ca9-117f241b4a68",
                                    businessDocumentGenDate = Instant.parse("2008-11-26T19:31:17.281Z"),
                                    isAppRec = false,
                                    sourceSystem = "eMottak EDI 2.0 edi-adapter, v1.0"
                                ),
                                Message(
                                    id = Uuid.parse("68e60a2b-5990-408c-b99b-089d8657d6ed"),
                                    contentType = "application/xml",
                                    receiverHerId = 8142520,
                                    senderHerId = 8142519,
                                    businessDocumentId = "cc169595-bbf0-11dd-9ca9-117f241b4a68",
                                    businessDocumentGenDate = Instant.parse("2008-11-26T19:31:17.281Z"),
                                    isAppRec = false,
                                    sourceSystem = "eMottak EDI 2.0 edi-adapter, v1.0"
                                )
                            )
                        }
                    }
                }

                BadRequest to {
                    description = "Bad request. Required query parameter `receiverHerIds` is missing."

                    body<String> {
                        example("Missing receiverHerIds") {
                            summary = "receiverHerIds missing"
                            description = "The mandatory query parameter `receiverHerIds` was not provided."
                            value = "Receiver her ids are missing"
                        }
                    }
                }

                InternalServerError to {
                    description = "Unexpected server error"
                }
            }
        }) {
            recover(
                {
                    val params = messageQueryParams(call)
                    val response = ediClient.get("Messages") { url { parameters.appendAll(params) } }
                    call.respondText(
                        text = response.bodyAsText(),
                        contentType = Json,
                        status = response.status
                    )
                },
                { e: MessageError -> call.respond(e.toContent()) }
            ) { t: Throwable -> call.respondInternalError(t) }
        }

        get("/messages/{messageId}", {
            summary = "Get message by id"
            description = "Returns a single message. Metadata is always included."

            request {
                pathParameter<String>("messageId") {
                    description = "Message identifier"
                    required = true

                    example("Message ID") {
                        value = "733be787-0ad0-475a-98b7-00512caa9ccb"
                    }
                }
            }

            response {
                OK to {
                    description = "Message found"

                    body<Message> {
                        example("Message") {
                            value = Message(
                                id = Uuid.parse("733be787-0ad0-475a-98b7-00512caa9ccb"),
                                contentType = "application/xml",
                                receiverHerId = 8142520,
                                senderHerId = 8142519,
                                businessDocumentId = "cc169595-bbf0-11dd-9ca9-117f241b4a68",
                                businessDocumentGenDate = Instant.parse("2008-11-26T19:31:17.281Z"),
                                isAppRec = false,
                                sourceSystem = "eMottak EDI 2.0 edi-adapter, v1.0"
                            )
                        }
                    }
                }

                NotFound to {
                    description = "Message not found"

                    body<String> {
                        example("Not found") {
                            value = "Not found"
                        }
                    }
                }

                InternalServerError to {
                    description = "Internal server error"
                }
            }
        }) {
            recover(
                {
                    val messageId = messageId(call)
                    val response = ediClient.get("Messages/$messageId")
                    call.respondText(
                        text = response.bodyAsText(),
                        contentType = Json,
                        status = response.status
                    )
                },
                { e: MessageError -> call.respond(e.toContent()) }
            ) { t: Throwable -> call.respondInternalError(t) }
        }

        get("/messages/{messageId}/document", {
            summary = "Get business document for message"
            description = "Returns the business document associated with a message."

            request {
                pathParameter<String>("messageId") {
                    description = "Message identifier"
                    required = true

                    example("Message ID") {
                        value = "733be787-0ad0-475a-98b7-00512caa9ccb"
                    }
                }
            }

            response {
                OK to {
                    description = "Business document retrieved successfully"

                    body<GetBusinessDocumentResponse> {
                        example("Business document") {
                            value = GetBusinessDocumentResponse(
                                businessDocument = "PHhtbD48RG9jdW1lbnQ+Li4uPC9Eb2N1bWVudD4=",
                                contentType = "application/xml",
                                contentTransferEncoding = "base64"
                            )
                        }
                    }
                }

                NotFound to {
                    description = "Message or business document not found"

                    body<String> {
                        example("Not found") {
                            value = "Business document for message not found"
                        }
                    }
                }

                InternalServerError to {
                    description = "Internal server error"
                }
            }
        }) {
            recover(
                {
                    val messageId = messageId(call)
                    val response = ediClient.get("Messages/$messageId/business-document")
                    call.respondText(
                        text = response.bodyAsText(),
                        contentType = Json,
                        status = response.status
                    )
                },
                { e: MessageError -> call.respond(e.toContent()) }
            ) { t: Throwable -> call.respondInternalError(t) }
        }

        get("/messages/{messageId}/status", {
            summary = "Get message delivery status"
            description = "Returns transport and application receipt status for a message."

            request {
                pathParameter<String>("messageId") {
                    description = "Message identifier"
                    required = true

                    example("Message ID") {
                        value = "733be787-0ad0-475a-98b7-00512caa9ccb"
                    }
                }
            }

            response {
                OK to {
                    description = "Message status retrieved successfully"

                    body<StatusInfo> {
                        example("Message status") {
                            value = StatusInfo(
                                receiverHerId = 8142520,
                                transportDeliveryState = ACKNOWLEDGED,
                                sent = true,
                                appRecStatus = AppRecStatus.OK
                            )
                        }
                    }
                }

                NotFound to {
                    description = "Message not found"

                    body<String> {
                        example("Not found") {
                            value = "Message not found"
                        }
                    }
                }

                InternalServerError to {
                    description = "Internal server error"
                }
            }
        }) {
            recover(
                {
                    val messageId = messageId(call)
                    val response = ediClient.get("Messages/$messageId/status")
                    call.respondText(
                        text = response.bodyAsText(),
                        contentType = Json,
                        status = response.status
                    )
                },
                { e: MessageError -> call.respond(e.toContent()) }
            ) { t: Throwable -> call.respondInternalError(t) }
        }

        get("/messages/{messageId}/apprec", {
            summary = "Get application receipt (AppRec) information"
            description =
                "Returns application receipt status and any associated application receipt errors for a message."

            request {
                pathParameter<String>("messageId") {
                    description = "Message identifier"
                    required = true

                    example("Message ID") {
                        value = "733be787-0ad0-475a-98b7-00512caa9ccb"
                    }
                }
            }

            response {
                OK to {
                    description = "Application receipt information retrieved successfully"

                    body<ApprecInfo> {
                        example("AppRec information") {
                            value = ApprecInfo(
                                receiverHerId = 8142520,
                                appRecStatus = OK_ERROR_IN_MESSAGE_PART,
                                appRecErrorList = listOf(
                                    AppRecError(
                                        errorCode = "E123",
                                        description = "Invalid document structure"
                                    )
                                )
                            )
                        }
                    }
                }

                NotFound to {
                    description = "Message or application receipt not found"

                    body<String> {
                        example("Not found") {
                            value = "Application receipt for message not found"
                        }
                    }
                }

                InternalServerError to {
                    description = "Internal server error"

                    body<String> {
                        example("Internal error") {
                            value = "Internal server error"
                        }
                    }
                }
            }
        }) {
            recover(
                {
                    val messageId = messageId(call)
                    val response = ediClient.get("Messages/$messageId/apprec")
                    call.respondText(
                        text = response.bodyAsText(),
                        contentType = Json,
                        status = response.status
                    )
                },
                { e: MessageError -> call.respond(e.toContent()) }
            ) { t: Throwable -> call.respondInternalError(t) }
        }

        post("/messages", {
            summary = "Post a new message"
            description = "Submits a new message with a business document to one or more receivers."

            request {
                body<PostMessageRequest> {
                    required = true

                    example("Post message with ebXML overrides") {
                        value = PostMessageRequest(
                            businessDocument = "PHhtbD48RG9jdW1lbnQ+Li4uPC9Eb2N1bWVudD4=",
                            contentType = "application/xml",
                            contentTransferEncoding = "base64",
                            ebXmlOverrides = EbXmlInfo(
                                cpaId = "string",
                                conversationId = "string",
                                service = "string",
                                serviceType = "string",
                                action = "string",
                                useSenderLevel1HerId = true,
                                receiverRole = "string",
                                applicationName = "EPJ Front",
                                applicationVersion = "18.0.8",
                                middlewareName = "string",
                                middlewareVersion = "string",
                                compressPayload = true
                            ),
                            receiverHerIdsSubset = listOf(0)
                        )
                    }
                }
            }

            response {
                Created to {
                    description = "Message created successfully"

                    body<Metadata> {
                        example("Message metadata") {
                            value = Metadata(
                                id = Uuid.parse("733be787-0ad0-475a-98b7-00512caa9ccb"),
                                location = "https://example.com/messages/733be787-0ad0-475a-98b7-00512caa9ccb"
                            )
                        }
                    }
                }

                BadRequest to {
                    description = "Invalid request payload"

                    body<String> {
                        example("Bad request") {
                            value = "Invalid message payload"
                        }
                    }
                }

                InternalServerError to {
                    description = "Internal server error"

                    body<String> {
                        example("Internal error") {
                            value = "Internal server error"
                        }
                    }
                }
            }
        }) {
            val message = call.receive<PostMessageRequest>()
            recover(
                {
                    val response = ediClient.post("Messages") {
                        contentType(Json)
                        setBody(message)
                    }
                    call.respondText(
                        text = response.toMetadata(),
                        contentType = Json,
                        status = response.status
                    )
                },
                { e: MessageError -> call.respond(e.toContent()) }
            ) { t: Throwable -> call.respondInternalError(t) }
        }

        post("/messages/{messageId}/apprec/{apprecSenderHerId}", {
            summary = "Post application receipt (AppRec)"
            description = "Submits an application receipt for a specific message and sender."

            request {
                pathParameter<String>("messageId") {
                    description = "Message identifier"
                    required = true

                    example("Message ID") {
                        value = "733be787-0ad0-475a-98b7-00512caa9ccb"
                    }
                }

                pathParameter<Int>("apprecSenderHerId") {
                    description = "HER ID of the AppRec sender"
                    required = true

                    example("AppRec sender HER ID") {
                        value = 8142519
                    }
                }

                body<PostAppRecRequest> {
                    required = true

                    example("Post AppRec") {
                        value = PostAppRecRequest(
                            appRecStatus = OK_ERROR_IN_MESSAGE_PART,
                            appRecErrorList = listOf(
                                AppRecError(
                                    errorCode = "E123",
                                    description = "Invalid document structure"
                                )
                            ),
                            ebXmlOverrides = EbXmlInfo(
                                cpaId = "string",
                                conversationId = "string",
                                service = "string",
                                serviceType = "string",
                                action = "string",
                                useSenderLevel1HerId = true,
                                receiverRole = "string",
                                applicationName = "EPJ Front",
                                applicationVersion = "18.0.8",
                                middlewareName = "string",
                                middlewareVersion = "string",
                                compressPayload = true
                            )
                        )
                    }
                }
            }

            response {
                Created to {
                    description = "Application receipt created successfully"

                    body<Metadata> {
                        example("AppRec metadata") {
                            value = Metadata(
                                id = Uuid.parse("733be787-0ad0-475a-98b7-00512caa9ccb"),
                                location = "https://example.com/messages/733be787-0ad0-475a-98b7-00512caa9ccb/apprec/8142519"
                            )
                        }
                    }
                }

                BadRequest to {
                    description = "Invalid request"

                    body<String> {
                        example("Bad request") {
                            value = "Invalid AppRec request"
                        }
                    }
                }

                NotFound to {
                    description = "Message not found"

                    body<String> {
                        example("Not found") {
                            value = "Message not found"
                        }
                    }
                }

                InternalServerError to {
                    description = "Internal server error"

                    body<String> {
                        example("Internal error") {
                            value = "Internal server error"
                        }
                    }
                }
            }
        }) {
            val appRec = call.receive<PostAppRecRequest>()
            recover(
                {
                    val messageId = messageId(call)
                    val senderHerId = apprecSenderHerId(call)

                    val response = ediClient.post("Messages/$messageId/apprec/$senderHerId") {
                        contentType(Json)
                        setBody(appRec)
                    }
                    call.respondText(
                        text = response.toMetadata(),
                        contentType = Json,
                        status = response.status
                    )
                },
                { e: MessageError -> call.respond(e.toContent()) }
            ) { t: Throwable -> call.respondInternalError(t) }
        }

        put("/messages/{messageId}/read/{herId}", {
            summary = "Mark message as read"
            description = "Marks a message as read for the given HER ID."

            request {
                pathParameter<String>("messageId") {
                    description = "Message identifier"
                    required = true

                    example("Message ID") {
                        value = "733be787-0ad0-475a-98b7-00512caa9ccb"
                    }
                }

                pathParameter<Int>("herId") {
                    description = "HER ID for which the message is marked as read"
                    required = true

                    example("HER ID") {
                        value = 8142520
                    }
                }
            }

            response {
                NoContent to {
                    description = "Message marked as read successfully"
                }

                BadRequest to {
                    description = "Invalid messageId or herId"

                    body<String> {
                        example("Bad request") {
                            value = "Invalid messageId or herId"
                        }
                    }
                }

                NotFound to {
                    description = "Message not found"

                    body<String> {
                        example("Not found") {
                            value = "Message not found"
                        }
                    }
                }

                InternalServerError to {
                    description = "Internal server error"

                    body<String> {
                        example("Internal error") {
                            value = "Internal server error"
                        }
                    }
                }
            }
        }) {
            recover(
                {
                    val messageId = messageId(call)
                    val herId = herId(call)
                    val response = ediClient.put("Messages/$messageId/read/$herId")
                    call.respondText(
                        text = response.bodyAsText(),
                        contentType = Json,
                        status = response.status
                    )
                },
                { e: MessageError -> call.respond(e.toContent()) }
            ) { t: Throwable -> call.respondInternalError(t) }
        }
    }
}

private suspend fun HttpResponse.toMetadata(): String {
    val body = bodyAsText()
    val location = headers[Location] ?: return body

    val id = JsonUtil.decodeFromString<Uuid>(body)

    val metadata = Metadata(
        id = id,
        location = location
    )

    return JsonUtil.encodeToString(metadata)
}

private fun Raise<ValidationError>.messageQueryParams(
    call: ApplicationCall
): Parameters {
    val receiverHerIds = receiverHerIds(call)
    val senderHerId = senderHerId(call)
    val businessDocumentId = businessDocumentId(call)
    val includeMetadata = includeMetadata(call)
    val messagesToFetch = messagesToFetch(call)
    val orderBy = orderBy(call)

    return Parameters.build {
        appendAll(RECEIVER_HER_IDS, receiverHerIds)
        appendIfPresent(SENDER_HER_ID, senderHerId)
        appendIfPresent(BUSINESS_DOCUMENT_ID, businessDocumentId)
        appendIfPresent(INCLUDE_METADATA, includeMetadata)
        appendIfPresent(MESSAGES_TO_FETCH, messagesToFetch)
        appendIfPresent(ORDER_BY, orderBy)
    }
}

private fun ParametersBuilder.appendIfPresent(name: String, value: Any?) =
    value?.let { append(name, it.toString()) }

private suspend fun ApplicationCall.respondInternalError(t: Throwable) {
    log.error(t) { "Unexpected error while processing request" }
    respond(InternalServerError)
}
