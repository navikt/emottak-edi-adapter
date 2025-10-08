package no.nav.emottak.edi.adapter.model

import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val messageType: String,
    val recipient: String,
    val businessDocument: String,
    val contentType: String = "application/xml",
    val contentTransferEncoding: String = "base64"
)
