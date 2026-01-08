package no.nav.helsemelding.ediadapter.model

import kotlinx.serialization.Serializable

@Serializable
enum class DeliveryState(val value: String, val description: String) {
    UNCONFIRMED("Unconfirmed", "Transport is not confirmed"),
    ACKNOWLEDGED("Acknowledged", "Transport is confirmed. Equivalent to 'Acknowledgement' without severe 'eb:ErrorList/eb:HighestSeverity'"),
    REJECTED("Rejected", "Transport is rejected. Equivalent to 'MessageError' with 'eb:ErrorList/eb:HighestSeverity'=\"Error\""),
    UNKNOWN("Unknown", "The value is not supported");

    companion object {
        fun fromValue(value: String?): DeliveryState =
            entries.find { it.value.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}
