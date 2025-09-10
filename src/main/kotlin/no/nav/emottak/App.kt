package no.nav.emottak

import arrow.continuations.SuspendApp
import arrow.continuations.ktor.server
import arrow.core.raise.result
import arrow.fx.coroutines.resourceScope
import io.ktor.server.application.Application
import io.ktor.server.netty.Netty
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import no.nav.emottak.edi.adapter.plugin.configureCallLogging
import no.nav.emottak.edi.adapter.plugin.configureContentNegotiation
import no.nav.emottak.edi.adapter.plugin.configureMetrics
import no.nav.emottak.edi.adapter.plugin.configureRoutes
import org.slf4j.LoggerFactory

internal val log = LoggerFactory.getLogger("no.nav.emottak.edi.adapter")

fun main() = SuspendApp {
    result {
        resourceScope {
            val deps = initDependencies()
            server(
                Netty,
                port = config().server.port.value,
                preWait = config().server.preWait,
                module = ediAdapterModule(deps.meterRegistry)
            )

            awaitCancellation()
        }
    }
        .onFailure { error -> if (error !is CancellationException) logError(error) }
}

internal fun ediAdapterModule(
    meterRegistry: PrometheusMeterRegistry
): Application.() -> Unit {
    return {
        configureMetrics(meterRegistry)
        configureContentNegotiation()
        // configureAuthentication()
        configureRoutes(meterRegistry)
        configureCallLogging()
    }
}

private fun logError(t: Throwable) = log.error("Shutdown edi-adapter due to: ${t.stackTraceToString()}")
