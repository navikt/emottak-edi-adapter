package no.nav.emottak.edi.adapter.auth

import no.nav.emottak.config
import no.nav.emottak.utils.environment.getEnvVar
import no.nav.security.token.support.v3.IssuerConfig
import no.nav.security.token.support.v3.TokenSupportConfig

class AuthConfig {
    companion object {
        fun getTokenSupportConfig(): TokenSupportConfig = TokenSupportConfig(
            IssuerConfig(
                name = config().azureAuth.issuer.value,
                discoveryUrl = getAzureWellKnownUrl(),
                acceptedAudience = getAcceptedAudience()
            )
        )

        fun getScope(): String = getEnvVar(
            config().azureAuth.appScope.value,
            "api://${getEnvVar("NAIS_CLUSTER_NAME", "dev-gcp")}.team-emottak.${config().azureAuth.appName.value}/.default"
        )

        fun getAzureWellKnownUrl(): String = getEnvVar(
            "AZURE_APP_WELL_KNOWN_URL",
            "http://localhost:3344/${getEnvVar("AZURE_APP_TENANT_ID", config().azureAuth.issuer.value)}/.well-known/openid-configuration"
        )

        private fun getAcceptedAudience(): List<String> = listOf(getEnvVar("AZURE_APP_CLIENT_ID", getScope()))
    }
}
