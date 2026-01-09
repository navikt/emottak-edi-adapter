package no.nav.helsemelding.ediadapter.model

import kotlinx.serialization.Serializable

@Serializable
data class StatusInfo(
    val receiverHerId: Int,
    val transportDeliveryState: DeliveryState,
    val sent: Boolean,
    val appRecStatus: AppRecStatus? = null
)
