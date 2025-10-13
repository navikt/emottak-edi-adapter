package no.nav.emottak.edi.adapter.plugin

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.serialization.json.Json
import no.nav.emottak.utils.edi2.models.AppRecError
import no.nav.emottak.utils.edi2.models.AppRecStatus
import no.nav.emottak.utils.edi2.models.ApprecInfo
import no.nav.emottak.utils.edi2.models.DeliveryState
import no.nav.emottak.utils.edi2.models.GetBusinessDocumentResponse
import no.nav.emottak.utils.edi2.models.GetMessagesRequest
import no.nav.emottak.utils.edi2.models.Message
import no.nav.emottak.utils.edi2.models.OrderBy
import no.nav.emottak.utils.edi2.models.PostAppRecRequest
import no.nav.emottak.utils.edi2.models.PostMessageRequest
import no.nav.emottak.utils.edi2.models.StatusInfo
import java.time.Instant
import kotlin.uuid.Uuid

fun Application.configureRoutes(
    registry: PrometheusMeterRegistry
) {
    routing {
        internalRoutes(registry)
        // authenticate(config.azureAuth.azureAdAuth.value) {
        externalRoutes()
        // }
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

fun Route.externalRoutes() {
    route("/api/v1") {
        get("/edi/adapter") {
            call.respondText("Pong from emottak-edi-adapter")
        }

        get("/messages/{id}/apprec") {
            val id = call.parameters["id"]?.let { Uuid.parse(it) }

            // Business logic here

            val dummyResponse = listOf(
                ApprecInfo(
                    receiverHerId = 123456,
                    appRecStatus = AppRecStatus.REJECTED,
                    appRecErrorList = listOf(
                        AppRecError(
                            errorCode = "123",
                            details = "Some details",
                            description = "Some description",
                            oid = "abc123"
                        )
                    )
                )
            )

            call.respond(HttpStatusCode.OK, dummyResponse)
        }

        get("/messages") {
            val params = call.request.queryParameters

            val getMessagesRequest = GetMessagesRequest(
                receiverHerIds = params.getAll("ReceiverHerIds")?.mapNotNull { it.toIntOrNull() } ?: emptyList(),
                senderHerId = params["SenderHerId"]?.toIntOrNull(),
                businessDocumentId = params["BusinessDocumentId"],
                includeMetadata = params["IncludeMetadata"]?.toBooleanStrictOrNull() ?: false,
                messagesToFetch = params["MessagesToFetch"]?.toIntOrNull() ?: 10,
                orderBy = OrderBy.fromValue(params["OrderBy"])
            )

            // Business logic here

            val dummyResponse = listOf(
                Message(
                    id = Uuid.random(),
                    contentType = "application/xml",
                    receiverHerId = 123456,
                    senderHerId = 654321,
                    businessDocumentId = "BD123",
                    businessDocumentGenDate = Instant.now(),
                    isAppRec = false
                )
            )

            call.respond(HttpStatusCode.OK, dummyResponse)
        }

        post("/messages") {
            val postMessageRequestJson = call.receiveText()
            val postMessageRequest = Json.decodeFromString<PostMessageRequest>(postMessageRequestJson)

            // Business logic here

            call.respond(HttpStatusCode.Created, Uuid.random().toString())
        }

        get("/messages/{id}") {
            val id = call.parameters["id"]?.let { Uuid.parse(it) }

            // Business logic here

            val dummyResponse = Message(
                id = Uuid.random(),
                contentType = "application/xml",
                receiverHerId = 123456,
                senderHerId = 654321,
                businessDocumentId = "BD123",
                businessDocumentGenDate = Instant.now(),
                isAppRec = false
            )

            call.respond(HttpStatusCode.OK, dummyResponse)
        }

        get("/messages/{id}/document") {
            val id = call.parameters["id"]?.let { Uuid.parse(it) }

            // Business logic here

            val dummyResponse = GetBusinessDocumentResponse(
                businessDocument = "<xml>...</xml>",
                contentType = "application/xml",
                contentTransferEncoding = "base64"
            )

            call.respond(HttpStatusCode.OK, dummyResponse)
        }

        get("/messages/{id}/status") {
            val id = call.parameters["id"]?.let { Uuid.parse(it) }

            // Business logic here

            val dummyResponse = listOf(
                StatusInfo(
                    receiverHerId = 123456,
                    transportDeliveryState = DeliveryState.ACKNOWLEDGED,
                    appRecStatus = AppRecStatus.OK
                )
            )

            call.respond(HttpStatusCode.OK, dummyResponse)
        }

        post("/messages/{id}/apprec/{apprecSenderHerId}") {
            val id = call.parameters["id"]?.let { Uuid.parse(it) }
            val apprecSenderHerId = call.parameters["apprecSenderHerId"]?.toIntOrNull()

            val postAppRecRequestJson = call.receiveText()
            val postAppRecRequest = Json.decodeFromString<PostAppRecRequest>(postAppRecRequestJson)

            // Business logic here

            call.respond(HttpStatusCode.Created, Uuid.random().toString())
        }

        put("/messages/{id}/read/{herId}") {
            val id = call.parameters["id"]?.let { Uuid.parse(it) }
            val herId = call.parameters["herId"]?.toIntOrNull()

            // Business logic here

            call.respond(HttpStatusCode.NoContent)
        }
    }
}
