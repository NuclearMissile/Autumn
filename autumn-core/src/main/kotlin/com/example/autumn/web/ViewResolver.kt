package com.example.autumn.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

interface ViewResolver {
    fun init()
    fun render(viewName: String, model: Map<String, Any>?, req: HttpServletRequest, resp: HttpServletResponse)
}
