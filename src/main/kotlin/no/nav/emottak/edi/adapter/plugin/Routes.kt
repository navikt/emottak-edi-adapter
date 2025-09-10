package no.nav.emottak.edi.adapter.plugin

import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.micrometer.prometheus.PrometheusMeterRegistry

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
    route("/api") {
        get("/edi/adapter") {
            call.respondText("Pong from emottak-edi-adapter")
        }
    }
}
