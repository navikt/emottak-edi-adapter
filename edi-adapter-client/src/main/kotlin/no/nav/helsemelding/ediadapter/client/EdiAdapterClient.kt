package no.nav.helsemelding.ediadapter.client

import arrow.core.Either
import arrow.core.Either.Left
import arrow.core.Either.Right
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import no.nav.helsemelding.ediadapter.model.ApprecInfo
import no.nav.helsemelding.ediadapter.model.ErrorMessage
import no.nav.helsemelding.ediadapter.model.GetBusinessDocumentResponse
import no.nav.helsemelding.ediadapter.model.GetMessagesRequest
import no.nav.helsemelding.ediadapter.model.Message
import no.nav.helsemelding.ediadapter.model.Metadata
import no.nav.helsemelding.ediadapter.model.PostAppRecRequest
import no.nav.helsemelding.ediadapter.model.PostMessageRequest
import no.nav.helsemelding.ediadapter.model.StatusInfo
import kotlin.uuid.Uuid

private val log = KotlinLogging.logger {}

interface EdiAdapterClient {
    suspend fun getApprecInfo(id: Uuid): Either<ErrorMessage, List<ApprecInfo>>

    suspend fun getMessages(getMessagesRequest: GetMessagesRequest): Either<ErrorMessage, List<Message>>

    suspend fun postMessage(postMessagesRequest: PostMessageRequest): Either<ErrorMessage, Metadata>

    suspend fun getMessage(id: Uuid): Either<ErrorMessage, Message>

    suspend fun getBusinessDocument(id: Uuid): Either<ErrorMessage, GetBusinessDocumentResponse>

    suspend fun getMessageStatus(id: Uuid): Either<ErrorMessage, List<StatusInfo>>

    suspend fun postApprec(
        id: Uuid,
        apprecSenderHerId: Int,
        postAppRecRequest: PostAppRecRequest
    ): Either<ErrorMessage, Metadata>

    suspend fun markMessageAsRead(id: Uuid, herId: Int): Either<ErrorMessage, Boolean>

    fun close()
}

class HttpEdiAdapterClient(
    clientProvider: () -> HttpClient,
    private val ediAdapterUrl: String = config().ediAdapterServer.url.toString()
) : EdiAdapterClient {
    private var httpClient = clientProvider.invoke()

    override suspend fun getApprecInfo(id: Uuid): Either<ErrorMessage, List<ApprecInfo>> {
        val url = "$ediAdapterUrl/api/v1/messages/$id/apprec"
        val response = httpClient.get(url) {
            contentType(ContentType.Application.Json)
        }.withLogging()

        return handleResponse(response)
    }

    override suspend fun getMessages(getMessagesRequest: GetMessagesRequest): Either<ErrorMessage, List<Message>> {
        val url = "$ediAdapterUrl/api/v1/messages?${getMessagesRequest.toUrlParams()}"
        val response = httpClient.get(url) {
            contentType(ContentType.Application.Json)
        }.withLogging()

        return handleResponse(response)
    }

    override suspend fun postMessage(postMessagesRequest: PostMessageRequest): Either<ErrorMessage, Metadata> {
        val url = "$ediAdapterUrl/api/v1/messages"
        val response = httpClient.post(url) {
            contentType(ContentType.Application.Json)
            setBody(postMessagesRequest)
        }.withLogging()

        return handleResponse(response)
    }

    override suspend fun getMessage(id: Uuid): Either<ErrorMessage, Message> {
        val url = "$ediAdapterUrl/api/v1/messages/$id"
        val response = httpClient.get(url) {
            contentType(ContentType.Application.Json)
        }.withLogging()

        return handleResponse(response)
    }

    override suspend fun getBusinessDocument(id: Uuid): Either<ErrorMessage, GetBusinessDocumentResponse> {
        val url = "$ediAdapterUrl/api/v1/messages/$id/document"
        val response = httpClient.get(url) {
            contentType(ContentType.Application.Json)
        }.withLogging()

        return handleResponse(response)
    }

    override suspend fun getMessageStatus(id: Uuid): Either<ErrorMessage, List<StatusInfo>> {
        val url = "$ediAdapterUrl/api/v1/messages/$id/status"
        val response = httpClient.get(url) {
            contentType(ContentType.Application.Json)
        }.withLogging()

        return handleResponse(response)
    }

    override suspend fun postApprec(
        id: Uuid,
        apprecSenderHerId: Int,
        postAppRecRequest: PostAppRecRequest
    ): Either<ErrorMessage, Metadata> {
        val url = "$ediAdapterUrl/api/v1/messages/$id/apprec/$apprecSenderHerId"
        val response = httpClient.post(url) {
            contentType(ContentType.Application.Json)
            setBody(postAppRecRequest)
        }.withLogging()

        return handleResponse(response)
    }

    override suspend fun markMessageAsRead(id: Uuid, herId: Int): Either<ErrorMessage, Boolean> {
        val url = "$ediAdapterUrl/api/v1/messages/$id/read/$herId"
        val response = httpClient.put(url) {
            contentType(ContentType.Application.Json)
        }.withLogging()

        return if (response.status == HttpStatusCode.NoContent) {
            Right(true)
        } else {
            Left(response.body())
        }
    }

    override fun close() = httpClient.close()

    private suspend inline fun <reified T> handleResponse(httpResponse: HttpResponse): Either<ErrorMessage, T> {
        return if (httpResponse.status == HttpStatusCode.OK || httpResponse.status == HttpStatusCode.Created) {
            Right(httpResponse.body())
        } else {
            Left(httpResponse.body())
        }
    }
}

suspend fun HttpResponse.withLogging(): HttpResponse {
    val body = this.bodyAsText()
    log.debug { "Response from ${request.method} ${request.url} is $status: $body" }
    return this
}
