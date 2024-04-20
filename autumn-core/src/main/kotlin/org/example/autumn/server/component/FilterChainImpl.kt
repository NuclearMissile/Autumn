package org.example.autumn.server.component

import jakarta.servlet.*

class FilterChainImpl(
    private val filters: List<Filter>, private val servlet: Servlet
) : FilterChain {
    private var index: Int = 0

    override fun doFilter(request: ServletRequest, response: ServletResponse) {
        if (index < filters.size) {
            filters[index].doFilter(request, response, this)
            index++
        } else {
            servlet.service(request, response)
        }
    }
}