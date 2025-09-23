package no.nav.emottak.edi.adapter.plugin

import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.HttpHeaders.Authorization
import no.nav.emottak.edi.adapter.config.AzureAuth
import no.nav.emottak.edi.adapter.model.DpopTokens
import no.nav.emottak.edi.adapter.util.dpopProofWithTokenInfo
import java.net.URI

val DpopAuth = createClientPlugin("DpopAuth", ::DpopAuthConfig) {
    val config = pluginConfig

    onRequest { request, _ ->
        if (config.tokens == null || config.tokens!!.isExpired()) {
            config.tokens = config.loadTokens?.invoke()
        }

        val dpopTokens = config.tokens!!

        val proof = dpopProofWithTokenInfo(
            URI(request.url.buildString()),
            request.method,
            dpopTokens.accessToken
        )

        val accessToken = dpopTokens.accessToken
        request.headers.append(Authorization, accessToken.toAuthorizationHeader())
        request.headers.append(accessToken.type.value, proof)
    }
}

class DpopAuthConfig {
    var azureAuth: AzureAuth? = null
    var loadTokens: (suspend () -> DpopTokens)? = null
    var tokens: DpopTokens? = null
}
