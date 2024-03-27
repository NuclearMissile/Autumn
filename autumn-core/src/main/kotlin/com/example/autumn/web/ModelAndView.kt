package com.example.autumn.web

import jakarta.servlet.http.HttpServletResponse

class ModelAndView(
    val viewName: String,
    val status: Int = HttpServletResponse.SC_OK,
    val model: MutableMap<String, Any> = mutableMapOf(),
) {
    fun addModel(map: Map<String, Any>) {
        model += map
    }

    fun addModel(key: String, value: Any) {
        model[key] = value
    }
}