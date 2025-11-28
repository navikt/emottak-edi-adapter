package no.nav.emottak.ediadapter.model

import kotlinx.serialization.Serializable
import no.nav.emottak.ediadapter.model.serialization.InstantSerializer
import no.nav.emottak.ediadapter.model.serialization.UuidSerializer
import java.time.Instant
import kotlin.uuid.Uuid

@Serializable
@kotlin.uuid.ExperimentalUuidApi
data class Message(
    @Serializable(with = UuidSerializer::class)
    val id: Uuid? = null,
    val contentType: String? = null,
    val receiverHerId: Int? = null,
    val senderHerId: Int? = null,
    val businessDocumentId: String? = null,
    @Serializable(with = InstantSerializer::class)
    val businessDocumentGenDate: Instant? = null,
    val isAppRec: Boolean? = null
)
