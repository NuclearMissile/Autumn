package org.example.autumn.servlet

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

abstract class ExceptionMapper<T : Exception> {
    abstract fun map(e: T, req: HttpServletRequest, resp: HttpServletResponse)
}