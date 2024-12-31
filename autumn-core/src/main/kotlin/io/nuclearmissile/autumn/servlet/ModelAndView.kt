package io.nuclearmissile.autumn.servlet

import jakarta.servlet.http.HttpServletResponse

class ModelAndView(
    val viewName: String?, initModel: Map<String, Any> = mutableMapOf(), val status: Int = HttpServletResponse.SC_OK,
) {
    private val model = initModel.toMutableMap()

    fun getModel(): Map<String, Any> = model

    fun add(map: Map<String, Any>): ModelAndView {
        model += map
        return this
    }

    fun add(key: String, value: Any): ModelAndView {
        model[key] = value
        return this
    }
}