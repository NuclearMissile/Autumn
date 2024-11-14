package org.example.autumn.server.connector

import com.sun.net.httpserver.Headers
import com.sun.net.httpserver.HttpExchange
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.URI

interface HttpExchangeRequest {
    fun getRequestMethod(): String
    fun getRequestURI(): URI
    fun getRequestHeaders(): Headers
    fun getRemoteAddress(): InetSocketAddress
    fun getLocalAddress(): InetSocketAddress
    fun getRequestBody(): ByteArray
}

interface HttpExchangeResponse {
    fun getResponseHeaders(): Headers
    fun sendResponseHeaders(rCode: Int, responseLength: Long)
    fun getResponseBody(): OutputStream
}

class HttpExchangeAdapter(private val exchange: HttpExchange) : HttpExchangeRequest, HttpExchangeResponse {
    private var requestBytes: ByteArray? = null

    override fun getRequestMethod(): String {
        return exchange.requestMethod
    }

    override fun getRequestURI(): URI {
        return exchange.requestURI
    }

    override fun getRequestHeaders(): Headers {
        return exchange.requestHeaders
    }

    override fun getRemoteAddress(): InetSocketAddress {
        return exchange.remoteAddress
    }

    override fun getLocalAddress(): InetSocketAddress {
        return exchange.localAddress
    }

    override fun getRequestBody(): ByteArray {
        if (requestBytes == null) {
            exchange.requestBody.use { input ->
                requestBytes = input.readAllBytes()
            }
        }
        return requestBytes!!
    }

    override fun getResponseHeaders(): Headers {
        return exchange.responseHeaders
    }

    override fun sendResponseHeaders(rCode: Int, responseLength: Long) {
        exchange.sendResponseHeaders(rCode, responseLength)
    }

    override fun getResponseBody(): OutputStream {
        return exchange.responseBody
    }
}
