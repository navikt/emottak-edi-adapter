package no.nav.emottak.ediadapterserver

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import io.ktor.server.application.ApplicationCall

private const val ID = "id"
private const val MESSAGE_ID = "messageId"
private const val HER_ID = "herId"
private const val APP_REC_SENDER_HER_ID = "apprecSenderHerId"

fun Raise<ValidationError>.messageId(call: ApplicationCall): String =
    call.parameters[MESSAGE_ID]!!.also {
        ensure(it.isNotBlank()) { MessageIdEmpty }
    }

fun Raise<ValidationError>.herId(call: ApplicationCall): String =
    call.parameters[HER_ID]!!.also {
        ensure(it.isNotBlank()) { HerIdEmpty }
    }

fun Raise<ValidationError>.senderHerId(call: ApplicationCall): String =
    call.parameters[APP_REC_SENDER_HER_ID]!!.also {
        ensure(it.isNotBlank()) { SenderHerIdEmpty }
    }

fun Raise<ValidationError>.messageIds(call: ApplicationCall): List<String> =
    ensureNotNull(call.request.queryParameters.getAll(ID)) { MessageIdsMissing }
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .also { ensure(it.isNotEmpty()) { MessageIdsEmpty } }
