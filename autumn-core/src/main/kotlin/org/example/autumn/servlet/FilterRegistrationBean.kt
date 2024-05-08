package org.example.autumn.servlet

import jakarta.servlet.Filter

abstract class FilterRegistrationBean {
    abstract val urlPatterns: List<String>
    abstract val filter: Filter
    val name = javaClass.simpleName.replaceFirstChar { it.lowercase() }
}