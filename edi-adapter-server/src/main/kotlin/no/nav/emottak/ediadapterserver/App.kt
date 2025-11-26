package no.nav.emottak.ediadapterserver

import arrow.continuations.SuspendApp
import arrow.continuations.ktor.server
import arrow.core.raise.result
import arrow.fx.coroutines.resourceScope
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.server.application.Application
import io.ktor.server.netty.Netty
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import no.nav.emottak.ediadapterserver.plugin.configureCallLogging
import no.nav.emottak.ediadapterserver.plugin.configureContentNegotiation
import no.nav.emottak.ediadapterserver.plugin.configureMetrics
import no.nav.emottak.ediadapterserver.plugin.configureRoutes

private val log = KotlinLogging.logger {}

fun main() = SuspendApp {
    result {
        resourceScope {
            val deps = dependencies()

            server(
                Netty,
                port = config().server.port.value,
                preWait = config().server.preWait,
                module = ediAdapterModule(deps.httpClient, deps.meterRegistry)
            )

            awaitCancellation()
        }
    }
        .onFailure { error -> if (error !is CancellationException) logError(error) }
}

internal fun ediAdapterModule(
    ediClient: HttpClient,
    meterRegistry: PrometheusMeterRegistry
): Application.() -> Unit {
    return {
        configureMetrics(meterRegistry)
        configureContentNegotiation()
        configureRoutes(ediClient, meterRegistry)
        configureCallLogging()
    }
}

private fun logError(t: Throwable) = log.error { "Shutdown edi-adapter due to: ${t.stackTraceToString()}" }
