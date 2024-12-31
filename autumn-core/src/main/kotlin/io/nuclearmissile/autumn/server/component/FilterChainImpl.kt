package io.nuclearmissile.autumn.server.component

import jakarta.servlet.*

class FilterChainImpl(
    filters: List<Filter>, private val servlet: Servlet,
) : FilterChain {
    private val iter = filters.iterator()

    override fun doFilter(request: ServletRequest, response: ServletResponse) {
        if (iter.hasNext()) {
            iter.next().doFilter(request, response, this)
        } else {
            servlet.service(request, response)
        }
    }
}