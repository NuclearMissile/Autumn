package io.nuclearmissile.autumn.server.component.support

import io.nuclearmissile.autumn.server.connector.HttpExchangeRequest
import io.nuclearmissile.autumn.utils.HttpUtils
import java.nio.charset.Charset
import java.util.*

class HttpReqParams(
    private val exchangeReq: HttpExchangeRequest,
    private var charset: Charset,
) {
    private var params = initParams()

    fun setCharset(charset: Charset) {
        this.charset = charset
        params = initParams()
    }

    fun getParameter(name: String): String? {
        val values = getParameterValues(name) ?: return null
        return values.first()
    }

    fun getParameterNames(): Enumeration<String> {
        return Collections.enumeration(getParameterMap().keys)
    }

    fun getParameterValues(name: String): Array<String>? {
        return getParameterMap()[name]
    }

    fun getParameterMap(): Map<String, Array<String>> {
        return params
    }

    private fun initParams(): Map<String, Array<String>> {
        var ret = mutableMapOf<String, MutableList<String>>()
        val query = exchangeReq.getRequestURI().rawQuery
        if (query != null) {
            ret = HttpUtils.parseQuery(query, charset).toMutableMap()
        }
        if ("POST" == exchangeReq.getRequestMethod()) {
            val value = exchangeReq.getRequestHeaders()["Content-Type"]?.firstOrNull()
            if (value?.startsWith("application/x-www-form-urlencoded") == true) {
                val requestBody = exchangeReq.getRequestBody().toString(charset)
                val postParams = HttpUtils.parseQuery(requestBody, charset)
                // merge:
                postParams.forEach { (key, postValues) ->
                    ret[key]?.addAll(postValues) ?: ret.put(key, postValues)
                }
            }
        }
        return ret.entries.associate { it.key to it.value.toTypedArray() }
    }
}