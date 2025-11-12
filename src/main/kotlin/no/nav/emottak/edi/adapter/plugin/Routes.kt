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
import io.ktor.server.auth.authenticate
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
import no.nav.emottak.MessageError
import no.nav.emottak.config
import no.nav.emottak.edi.adapter.model.AppRec
import no.nav.emottak.edi.adapter.model.Message
import no.nav.emottak.herId
import no.nav.emottak.messageId
import no.nav.emottak.messageIds
import no.nav.emottak.senderHerId
import no.nav.emottak.toContent
import no.nav.emottak.utils.edi2.models.PostAppRecRequest

private const val RECEIVER_HER_IDS = "ReceiverHerIds"

fun Application.configureRoutes(
    ediClient: HttpClient,
    registry: PrometheusMeterRegistry
) {
    routing {
        internalRoutes(registry)
        authenticate(config().azureAuth.issuer.value) {
            externalRoutes(ediClient)
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
        get("/messages") {
            recover({
                val messageIds = messageIds(call)
                val params = parametersOf(RECEIVER_HER_IDS to messageIds)
                val response = ediClient.get("Messages") { url { parameters.appendAll(params) } }
                call.respond(response.status, response.bodyAsText())
            }) { e: MessageError -> call.respond(e.toContent()) }
        }
        get("/messages/{messageId}") {
            recover({
                val messageId = messageId(call)
                val response = ediClient.get("Messages/$messageId")
                call.respond(response.status, response.bodyAsText())
            }) { e: MessageError -> call.respond(e.toContent()) }
        }

        get("/messages/{messageId}/document") {
            recover({
                val messageId = messageId(call)
                val response = ediClient.get("Messages/$messageId/business-document")
                call.respond(response.status, response.bodyAsText())
            }) { e: MessageError -> call.respond(e.toContent()) }
        }

        get("/messages/{messageId}/status") {
            recover({
                val messageId = messageId(call)
                val response = ediClient.get("Messages/$messageId/status")
                call.respond(response.status, response.bodyAsText())
            }) { e: MessageError -> call.respond(e.toContent()) }
        }
        get("/messages/{messageId}/apprec") {
            recover({
                val messageId = messageId(call)
                val response = ediClient.get("Messages/$messageId/apprec")
                call.respond(response.status, response.bodyAsText())
            }) { e: MessageError -> call.respond(e.toContent()) }
        }
        post("/messages") {
            val message = call.receive<Message>()
            val response = ediClient.post("Messages") {
                contentType(Json)
                setBody(message)
            }
            call.respond(response.status, response.bodyAsText())
        }

        post("/messages/{messageId}/apprec/{apprecSenderHerId}") {
            recover({
                val messageId = messageId(call)
                val senderHerId = senderHerId(call)
                val appRec = call.receive<PostAppRecRequest>()

                val response = ediClient.post("Messages/$messageId/apprec/$senderHerId") {
                    contentType(Json)
                    setBody(appRec)
                }
                call.respond(response.status, response.bodyAsText())
            }) { e: MessageError -> call.respond(e.toContent()) }
        }

        put("/messages/{messageId}/read/{herId}") {
            recover({
                val messageId = messageId(call)
                val herId = herId(call)
                val response = ediClient.put("/messages/$messageId/read/$herId")
                call.respond(response.status, response.status.value)
            }) { e: MessageError -> call.respond(e.toContent()) }
        }
    }
}
