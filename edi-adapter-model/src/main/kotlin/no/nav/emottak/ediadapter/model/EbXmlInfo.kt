package no.nav.emottak.ediadapter.model

import kotlinx.serialization.Serializable

@Serializable
data class EbXmlInfo(
    val cpaId: String? = null,
    val conversationId: String? = null,
    val service: String? = null,
    val serviceType: String? = null,
    val action: String? = null,
    val role: String? = null,
    val useSenderLevel1HerId: Boolean? = null,
    val receiverRole: String? = null,
    val applicationName: String? = null,
    val applicationVersion: String? = null,
    val middlewareName: String? = null,
    val middlewareVersion: String? = null,
    val compressPayload: Boolean? = null
)
