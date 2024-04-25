package org.example.autumn.server.component.servlet

import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

class DefaultServlet : HttpServlet() {
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        resp.writer.apply {
            write("<h1>Default Servlet.</h1>")
            flush()
        }
    }
}