package com.github.bhlangonijr.flubber.action

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager


/**
 * Fetch content from HTTP endpoint
 */
class RestAction : Action {

    companion object {
        private val objectMapper = ObjectMapper().registerModule(KotlinModule())
        private val sslContext = customSslContext()

        private fun customSslContext(): SSLContext {
            val sslContext: SSLContext = SSLContext.getInstance("SSL")
            sslContext.init(null, arrayOf<TrustManager>(CustomTrustManager()), SecureRandom())
            return sslContext
        }
    }

    override fun execute(context: JsonNode, args: Map<String, Any?>): Any {

        val url = args["url"]?.toString()
        val method = args["method"]?.toString() ?: "get"
        val headers = args["headers"]?.toString()
            ?.let { jsonToMap(it) }
        val body = args["body"]?.toString()

        if (url == null) {
            throw IllegalArgumentException("Field url must be informed")
        }
        val response = exchange(url, method, body, headers)
        return mapOf(
            Pair("status", response.statusCode()),
            Pair("body", response.body() ?: ""),
            Pair("headers", response.headers().map().entries.associateBy({ it.key }, { it.value }))
        )
    }

    private fun exchange(
        url: String,
        method: String,
        body: String? = null,
        headers: Map<String, String>? = null,
        contentType: String? = "application/json"
    ): HttpResponse<String> {

        val client: HttpClient = HttpClient.newBuilder().sslContext(sslContext).build()
        val requestBuilder = HttpRequest.newBuilder(URI(url))
            .header("Accept", contentType)
        headers?.forEach { requestBuilder.setHeader(it.key, it.value) }
        when (method) {
            "post" -> requestBuilder.POST(HttpRequest.BodyPublishers.ofString(body))
            "put" -> requestBuilder.method("PUT", HttpRequest.BodyPublishers.ofString(body))
            "patch" -> requestBuilder.method("PATCH", HttpRequest.BodyPublishers.ofString(body))
            "delete" -> requestBuilder.DELETE()
            else -> requestBuilder.GET()
        }
        val request = requestBuilder.build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun jsonToMap(json: String) =
        objectMapper.readTree(json).fields().asSequence().associateBy({ it.key }, { it.value.asText() })

}

class CustomTrustManager : X509TrustManager {

    override fun checkClientTrusted(
        certs: Array<X509Certificate>,
        authType: String
    ) {

    }

    override fun checkServerTrusted(
        certs: Array<X509Certificate>,
        authType: String
    ) {

    }

    override fun getAcceptedIssuers(): Array<X509Certificate>? {

        return null
    }
}
