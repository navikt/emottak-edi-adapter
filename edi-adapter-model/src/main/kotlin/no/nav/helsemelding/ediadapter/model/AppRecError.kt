package no.nav.helsemelding.ediadapter.model

import kotlinx.serialization.Serializable

@Serializable
data class AppRecError(
    val errorCode: String? = null,
    val details: String? = null,
    val description: String? = null,
    val oid: String? = null
)
