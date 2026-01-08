package no.nav.helsemelding.ediadapter.model

import kotlinx.serialization.Serializable

@Serializable
data class GetBusinessDocumentResponse(
    val businessDocument: String,
    val contentType: String,
    val contentTransferEncoding: String
)
