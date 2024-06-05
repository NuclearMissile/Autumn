package org.example.autumn.servlet

import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.example.autumn.utils.JsonUtils.toJson

data class ResponseEntity(
    val body: Any?, val contentType: String, val status: Int = 200,
    val headers: List<Pair<String, String>>? = null, val cookies: List<Cookie>? = null,
)

fun HttpServletResponse.setUp(respEntity: ResponseEntity) {
    respEntity.headers?.forEach { (k, v) -> addHeader(k, v) }
    respEntity.cookies?.forEach { addCookie(it) }
    status = respEntity.status
    contentType = respEntity.contentType
    when (respEntity.body) {
        is String -> writer.apply { write(respEntity.body) }.flush()
        is ByteArray -> outputStream.apply { write(respEntity.body) }.flush()
        else -> writer.apply { write(respEntity.body.toJson()) }.flush()
    }
}

data class RequestEntity(
    val method: String,
    val url: String,
    val body: String,
    val headers: Map<String, List<String>>,
    val reqParams: Map<String, List<String>>,
    val cookies: List<Cookie>,
)