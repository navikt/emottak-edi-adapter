package no.nav.emottak.edi.adapter.plugin

import arrow.core.raise.recover
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.contentType
import io.ktor.http.parametersOf
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlin.math.log
import no.nav.emottak.MessageError
import no.nav.emottak.edi.adapter.model.AppRec
import no.nav.emottak.edi.adapter.model.Message
import no.nav.emottak.herId
import no.nav.emottak.messageId
import no.nav.emottak.messageIds
import no.nav.emottak.senderHerId
import no.nav.emottak.toContent

private const val RECEIVER_HER_IDS = "ReceiverHerIds"

fun Application.configureRoutes(
    ediClient: HttpClient,
    registry: PrometheusMeterRegistry
) {
    routing {
        internalRoutes(registry)
        // authenticate(config.azureAuth.azureAdAuth.value) {
        externalRoutes(ediClient)
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

fun Route.externalRoutes(ediClient: HttpClient) {
    route("/api/v1") {
        get("/messages") {
            no.nav.emottak.log.info("EDI2 test: Request received for GET /messages")
            recover({
                val messageIds = messageIds(call)
                val params = parametersOf(RECEIVER_HER_IDS to messageIds)
                val response = ediClient.get("Messages") { url { parameters.appendAll(params) } }
                call.respond(response.bodyAsText())
            }) { e: MessageError -> call.respond(e.toContent()) }
        }
        get("/messages/{messageId}") {
            no.nav.emottak.log.info("EDI2 test: Request received for GET /messages/{messageId}")
            recover({
                val messageId = messageId(call)
                val response = ediClient.get("Messages/$messageId")
                call.respond(response.bodyAsText())
            }) { e: MessageError -> call.respond(e.toContent()) }
        }

        get("/messages/{messageId}/document") {
            no.nav.emottak.log.info("EDI2 test: Request received for GET /messages/{messageId}/document")
            recover({
                val messageId = messageId(call)
                val response = ediClient.get("Messages/$messageId/business-document")
                call.respond(response.bodyAsText())
            }) { e: MessageError -> call.respond(e.toContent()) }
        }

        get("/messages/{messageId}/status") {
            no.nav.emottak.log.info("EDI2 test: Request received for GET /messages/{messageId}/status")
            recover({
                val messageId = messageId(call)
                val response = ediClient.get("Messages/$messageId/status")
                call.respond(response.bodyAsText())
            }) { e: MessageError -> call.respond(e.toContent()) }
        }
        get("/messages/{messageId}/apprec") {
            no.nav.emottak.log.info("EDI2 test: Request received for GET /messages/{messageId}/apprec")
            recover({
                val messageId = messageId(call)
                val response = ediClient.get("Messages/$messageId/apprec")
                call.respond(response.bodyAsText())
            }) { e: MessageError -> call.respond(e.toContent()) }
        }
        post("/messages") {
            no.nav.emottak.log.info("EDI2 test: Request received for POST /messages")
            val message = call.receive<Message>()
            val response = ediClient.post("Messages") {
                contentType(Json)
                setBody(message)
            }
            call.respond(response.bodyAsText())
        }

        post("/messages/{messageId}/apprec/{apprecSenderHerId}") {
            no.nav.emottak.log.info("EDI2 test: Request received for POST /messages/{messageId}/apprec/{apprecSenderHerId}")
            recover({
                val messageId = messageId(call)
                val senderHerId = senderHerId(call)
                val appRec = call.receive<AppRec>()

                val response = ediClient.post("Messages/$messageId/apprec/$senderHerId") {
                    contentType(Json)
                    setBody(appRec)
                }
                call.respond(response.bodyAsText())
            }) { e: MessageError -> call.respond(e.toContent()) }
        }

        put("/messages/{messageId}/read/{herId}") {
            no.nav.emottak.log.info("EDI2 test: Request received for PUT /messages/{messageId}/read/{herId}")
            recover({
                val messageId = messageId(call)
                val herId = herId(call)
                val response = ediClient.put("/messages/$messageId/read/$herId")
                call.respond(response.status.value)
            }) { e: MessageError -> call.respond(e.toContent()) }
        }
    }
}
