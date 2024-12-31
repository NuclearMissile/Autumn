package io.nuclearmissile.autumn.servlet

import jakarta.servlet.Filter

abstract class FilterRegistration {
    abstract val name: String
    abstract val urlPatterns: List<String>
    abstract val filter: Filter
}