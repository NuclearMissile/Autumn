package com.example.autumn.web

import com.example.autumn.context.ApplicationContext
import com.example.autumn.resolver.PropertyResolver
import jakarta.servlet.http.HttpServlet
import org.slf4j.LoggerFactory

class DispatcherServlet(
    private val applicationContext: ApplicationContext,
    private val propertyResolver: PropertyResolver,
) : HttpServlet() {
    private val logger = LoggerFactory.getLogger(javaClass)
}