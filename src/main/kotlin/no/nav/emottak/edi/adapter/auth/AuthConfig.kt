package no.nav.emottak.edi.adapter.auth

import no.nav.emottak.config
import no.nav.emottak.utils.environment.getEnvVar
import no.nav.security.token.support.v3.IssuerConfig
import no.nav.security.token.support.v3.TokenSupportConfig
import org.slf4j.LoggerFactory

class AuthConfig {
    companion object {
        private val logger = LoggerFactory.getLogger(AuthConfig::class.java)

        fun getTokenSupportConfig(): TokenSupportConfig = TokenSupportConfig(
            IssuerConfig(
                name = config().azureAuth.issuer.value,
                discoveryUrl = getAzureWellKnownUrl(),
                acceptedAudience = getAcceptedAudience()
            )
        )

        fun getScope(): String {
            val scope = getEnvVar(
                config().azureAuth.appScope.value,
                "api://${getEnvVar("NAIS_CLUSTER_NAME", "dev-gcp")}.team-emottak.${config().azureAuth.appName.value}/.default"
            )
            logger.info("EDI2 test: getScope() returns: $scope")
            return scope
        }

        fun getAzureWellKnownUrl(): String {
            val url = getEnvVar(
                "AZURE_APP_WELL_KNOWN_URL",
                "http://localhost:3344/${getEnvVar("AZURE_APP_TENANT_ID", config().azureAuth.issuer.value)}/.well-known/openid-configuration"
            )
            logger.info("EDI2 test: getAzureWellKnownUrl() returns: $url")
            return url
        }

        private fun getAcceptedAudience(): List<String> {
            val audience = listOf(getEnvVar("AZURE_APP_CLIENT_ID", getScope()))
            logger.info("EDI2 test: getAcceptedAudience() returns: $audience")
            return audience
        }
    }
}
