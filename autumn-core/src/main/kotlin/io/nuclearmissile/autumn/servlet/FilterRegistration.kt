package io.nuclearmissile.autumn.servlet

import jakarta.servlet.Filter

abstract class FilterRegistration {
    abstract val urlPatterns: List<String>
    abstract val filter: Filter
}