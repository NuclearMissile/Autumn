package io.nuclearmissile.autumn.servlet

import io.nuclearmissile.autumn.utils.JsonUtils.toJson
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletResponse

data class ResponseEntity(
    val body: Any?, val status: Int = 200, val contentType: String = "",
    val headers: List<Pair<String, String>>? = null, val cookies: List<Cookie>? = null,
)

fun HttpServletResponse.set(entity: ResponseEntity) {
    if (isCommitted) throw IllegalStateException("HttpServletResponse $this is already committed")
    entity.headers?.forEach { (k, v) -> addHeader(k, v) }
    entity.cookies?.forEach { addCookie(it) }
    status = entity.status
    if (entity.contentType.isNotEmpty()) contentType = entity.contentType
    when (entity.body) {
        is String -> writer.apply { write(entity.body) }.flush()
        is ByteArray -> outputStream.apply { write(entity.body) }.flush()
        else -> writer.apply { write(entity.body.toJson()) }.flush()
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