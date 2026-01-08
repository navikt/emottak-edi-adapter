package no.nav.helsemelding.ediadapter.model

import kotlinx.serialization.Serializable

@Serializable
data class PostAppRecRequest(
    val appRecStatus: AppRecStatus,
    val appRecErrorList: List<AppRecError>? = null,
    val ebXmlOverrides: EbXmlInfo? = null
)
