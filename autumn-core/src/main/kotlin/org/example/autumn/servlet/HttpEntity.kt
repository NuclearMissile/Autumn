package org.example.autumn.servlet

data class ResponseEntity(
    val body: Any?, var status: Int = 200, val contentType: String = "text/plain",
)

data class RequestEntity(
    val method: String,
    val url: String,
    val body: String,
    val headers: Map<String, List<String>>,
    val reqParams: Map<String, List<String>>,
)