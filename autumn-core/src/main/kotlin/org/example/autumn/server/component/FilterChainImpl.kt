package org.example.autumn.server.component

import jakarta.servlet.*

class FilterChainImpl(
    private val filters: List<Filter>, private val servlet: Servlet
) : FilterChain {
    private var index: Int = 0

    override fun doFilter(request: ServletRequest, response: ServletResponse) {
        if (index < filters.size) {
            val current = index
            index++
            filters[current].doFilter(request, response, this)
        } else {
            servlet.service(request, response)
        }
    }
}