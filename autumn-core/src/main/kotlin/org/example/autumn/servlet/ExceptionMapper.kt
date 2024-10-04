package org.example.autumn.servlet

fun interface ExceptionMapper<T : Exception> {
    fun map(url: String, e: T): ResponseEntity
}