package no.nav.emottak.ediadapter.server

import io.ktor.http.ContentType.Text.Plain
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.content.TextContent

sealed interface MessageError
sealed interface ValidationError : MessageError

data object MessageIdEmpty : ValidationError
data object MessageIdsMissing : ValidationError
data object MessageIdsEmpty : ValidationError
data object HerIdEmpty : ValidationError
data object SenderHerIdMissing : ValidationError
data object SenderHerIdEmpty : ValidationError

fun MessageError.toContent(): TextContent =
    when (this) {
        is MessageIdEmpty ->
            TextContent("Message id is empty")

        is MessageIdsMissing ->
            TextContent("Message ids are missing")

        is MessageIdsEmpty ->
            TextContent("Message ids are empty")

        is HerIdEmpty ->
            TextContent("Her id is empty")

        is SenderHerIdMissing ->
            TextContent("Sender her id is missing")

        is SenderHerIdEmpty ->
            TextContent("Sender her id is empty")
    }

private fun TextContent(
    content: String,
    statusCode: HttpStatusCode = BadRequest
): TextContent = TextContent(content, Plain, statusCode)
