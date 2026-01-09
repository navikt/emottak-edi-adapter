package no.nav.helsemelding.ediadapter.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = AppRecStatusSerializer::class)
enum class AppRecStatus(val value: String, val description: String) {
    OK("Ok", "Ok"),
    REJECTED("Rejected", "Avvist"),
    OK_ERROR_IN_MESSAGE_PART("OkErrorInMessagePart", "Ok, feil i delmelding"),
    UNKNOWN("Unknown", "The value is not supported");

    companion object {
        fun fromValue(value: String?): AppRecStatus =
            entries.find { it.value.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

object AppRecStatusSerializer : KSerializer<AppRecStatus> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("AppRecStatus", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: AppRecStatus) {
        val code = when (value) {
            AppRecStatus.OK -> "1"
            AppRecStatus.REJECTED -> "2"
            AppRecStatus.OK_ERROR_IN_MESSAGE_PART -> "3"
            AppRecStatus.UNKNOWN -> "4"
        }
        encoder.encodeString(code)
    }

    override fun deserialize(decoder: Decoder): AppRecStatus {
        return when (decoder.decodeString()) {
            "1" -> AppRecStatus.OK
            AppRecStatus.OK.value -> AppRecStatus.OK
            "2" -> AppRecStatus.REJECTED
            AppRecStatus.REJECTED.value -> AppRecStatus.REJECTED
            "3" -> AppRecStatus.OK_ERROR_IN_MESSAGE_PART
            AppRecStatus.OK_ERROR_IN_MESSAGE_PART.value -> AppRecStatus.OK_ERROR_IN_MESSAGE_PART
            "4" -> AppRecStatus.UNKNOWN
            AppRecStatus.UNKNOWN.value -> AppRecStatus.UNKNOWN
            else -> AppRecStatus.UNKNOWN
        }
    }
}
