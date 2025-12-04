package no.nav.emottak.ediadapter.server.plugin

import arrow.core.raise.recover
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpHeaders.Location
import io.ktor.http.HttpStatusCode
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
import no.nav.emottak.ediadapter.model.PostAppRecRequest
import no.nav.emottak.ediadapter.model.PostMessageRequest
import no.nav.emottak.ediadapter.server.MessageError
import no.nav.emottak.ediadapter.server.config
import no.nav.emottak.ediadapter.server.herId
import no.nav.emottak.ediadapter.server.messageId
import no.nav.emottak.ediadapter.server.receiverHerIds
import no.nav.emottak.ediadapter.server.senderHerId
import no.nav.emottak.ediadapter.server.toContent
import kotlinx.serialization.json.Json as JsonUtil

private const val RECEIVER_HER_IDS = "ReceiverHerIds"
const val UUID_FIELD_NAME = "uuid"
const val LOCATION_FIELD_NAME = "locationUrl"

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
                val receiverHerIds = receiverHerIds(call)
                val params = parametersOf(RECEIVER_HER_IDS to receiverHerIds)
                val response = ediClient.get("Messages") { url { parameters.appendAll(params) } }
                call.respondText(
                    text = response.bodyAsText(),
                    contentType = Json,
                    status = response.status
                )
            }) { e: MessageError -> call.respond(e.toContent()) }
        }
        get("/messages/{messageId}") {
            recover({
                val messageId = messageId(call)
                val response = ediClient.get("Messages/$messageId")
                call.respondText(
                    text = response.bodyAsText(),
                    contentType = Json,
                    status = response.status
                )
            }) { e: MessageError -> call.respond(e.toContent()) }
        }

        get("/messages/{messageId}/document") {
            recover({
                val messageId = messageId(call)
                val response = ediClient.get("Messages/$messageId/business-document")
                call.respondText(
                    text = response.bodyAsText(),
                    contentType = Json,
                    status = response.status
                )
            }) { e: MessageError -> call.respond(e.toContent()) }
        }

        get("/messages/{messageId}/status") {
            recover({
                val messageId = messageId(call)
                val response = ediClient.get("Messages/$messageId/status")
                call.respondText(
                    text = response.bodyAsText(),
                    contentType = Json,
                    status = response.status
                )
            }) { e: MessageError -> call.respond(e.toContent()) }
        }
        get("/messages/{messageId}/apprec") {
            recover({
                val messageId = messageId(call)
                val response = ediClient.get("Messages/$messageId/apprec")
                call.respondText(
                    text = response.bodyAsText(),
                    contentType = Json,
                    status = response.status
                )
            }) { e: MessageError -> call.respond(e.toContent()) }
        }
        post("/messages") {
            val message = call.receive<PostMessageRequest>()
            val response = ediClient.post("Messages") {
                contentType(Json)
                setBody(message)
            }
            call.respondText(
                text = response.ifCreatedAppendLocation(),
                contentType = Json,
                status = response.status
            )
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
                call.respondText(
                    text = response.ifCreatedAppendLocation(),
                    contentType = Json,
                    status = response.status
                )
            }) { e: MessageError -> call.respond(e.toContent()) }
        }

        put("/messages/{messageId}/read/{herId}") {
            recover({
                val messageId = messageId(call)
                val herId = herId(call)
                val response = ediClient.put("/messages/$messageId/read/$herId")
                call.respondText(
                    text = response.bodyAsText(),
                    contentType = Json,
                    status = response.status
                )
            }) { e: MessageError -> call.respond(e.toContent()) }
        }
    }
}

suspend fun HttpResponse.ifCreatedAppendLocation(): String {
    if (this.status == HttpStatusCode.Created) {
        val jsonMap = mapOf(
            UUID_FIELD_NAME to this.bodyAsText(),
            LOCATION_FIELD_NAME to this.headers[Location]
        )
        return JsonUtil.encodeToString(jsonMap)
    }

    return this.bodyAsText()
}
