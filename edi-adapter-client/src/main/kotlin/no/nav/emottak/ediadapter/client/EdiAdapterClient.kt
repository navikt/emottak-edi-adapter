package no.nav.emottak.ediadapter.client

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
import no.nav.emottak.ediadapter.model.ApprecInfo
import no.nav.emottak.ediadapter.model.ErrorMessage
import no.nav.emottak.ediadapter.model.GetBusinessDocumentResponse
import no.nav.emottak.ediadapter.model.GetMessagesRequest
import no.nav.emottak.ediadapter.model.Message
import no.nav.emottak.ediadapter.model.Metadata
import no.nav.emottak.ediadapter.model.PostAppRecRequest
import no.nav.emottak.ediadapter.model.PostMessageRequest
import no.nav.emottak.ediadapter.model.StatusInfo
import kotlin.uuid.Uuid

private val log = KotlinLogging.logger {}

class EdiAdapterClient(
    clientProvider: () -> HttpClient,
    private val ediAdapterUrl: String = config().ediAdapterServer.url.toString()
) {
    private var httpClient = clientProvider.invoke()

    suspend fun getApprecInfo(id: Uuid): Pair<List<ApprecInfo>?, ErrorMessage?> {
        val url = "$ediAdapterUrl/api/v1/messages/$id/apprec"
        val response = httpClient.get(url) {
            contentType(ContentType.Application.Json)
        }.withLogging()

        return handleResponse(response)
    }

    suspend fun getMessages(getMessagesRequest: GetMessagesRequest): Pair<List<Message>?, ErrorMessage?> {
        val url = "$ediAdapterUrl/api/v1/messages?${getMessagesRequest.toUrlParams()}"
        val response = httpClient.get(url) {
            contentType(ContentType.Application.Json)
        }.withLogging()

        return handleResponse(response)
    }

    suspend fun postMessage(postMessagesRequest: PostMessageRequest): Pair<Metadata?, ErrorMessage?> {
        val url = "$ediAdapterUrl/api/v1/messages"
        val response = httpClient.post(url) {
            contentType(ContentType.Application.Json)
            setBody(postMessagesRequest)
        }.withLogging()

        return handleResponse(response)
    }

    suspend fun getMessage(id: Uuid): Pair<Message?, ErrorMessage?> {
        val url = "$ediAdapterUrl/api/v1/messages/$id"
        val response = httpClient.get(url) {
            contentType(ContentType.Application.Json)
        }.withLogging()

        return handleResponse(response)
    }

    suspend fun getBusinessDocument(id: Uuid): Pair<GetBusinessDocumentResponse?, ErrorMessage?> {
        val url = "$ediAdapterUrl/api/v1/messages/$id/document"
        val response = httpClient.get(url) {
            contentType(ContentType.Application.Json)
        }.withLogging()

        return handleResponse(response)
    }

    suspend fun getMessageStatus(id: Uuid): Pair<List<StatusInfo>?, ErrorMessage?> {
        val url = "$ediAdapterUrl/api/v1/messages/$id/status"
        val response = httpClient.get(url) {
            contentType(ContentType.Application.Json)
        }.withLogging()

        return handleResponse(response)
    }

    suspend fun postApprec(
        id: Uuid,
        apprecSenderHerId: Int,
        postAppRecRequest: PostAppRecRequest
    ): Pair<Metadata?, ErrorMessage?> {
        val url = "$ediAdapterUrl/api/v1/messages/$id/apprec/$apprecSenderHerId"
        val response = httpClient.post(url) {
            contentType(ContentType.Application.Json)
            setBody(postAppRecRequest)
        }.withLogging()

        return handleResponse(response)
    }

    suspend fun markMessageAsRead(id: Uuid, herId: Int): Pair<Boolean?, ErrorMessage?> {
        val url = "$ediAdapterUrl/api/v1/messages/$id/read/$herId"
        val response = httpClient.put(url) {
            contentType(ContentType.Application.Json)
        }.withLogging()

        return if (response.status == HttpStatusCode.NoContent) {
            Pair(true, null)
        } else {
            Pair(null, response.body())
        }
    }

    fun close() = httpClient.close()

    private suspend inline fun <reified T> handleResponse(httpResponse: HttpResponse): Pair<T?, ErrorMessage?> {
        return if (httpResponse.status == HttpStatusCode.OK || httpResponse.status == HttpStatusCode.Created) {
            Pair(httpResponse.body(), null)
        } else {
            Pair(null, httpResponse.body())
        }
    }
}

suspend fun HttpResponse.withLogging(): HttpResponse {
    val body = this.bodyAsText()
    log.debug { "Response from ${request.method} ${request.url} is $status: $body" }
    return this
}
