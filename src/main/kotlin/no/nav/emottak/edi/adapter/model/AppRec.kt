package no.nav.emottak.edi.adapter.model

import kotlinx.serialization.Serializable

@Serializable
data class AppRec(
    val appRecStatus: String = "1",
    val appRecErrorList: List<AppRecError> = emptyList()
)
