package org.example.autumn.servlet

import jakarta.servlet.Filter

abstract class FilterRegistrationBean {
    abstract val urlPatterns: List<String>
    abstract val filter: Filter
    val name: String
        /**
         * Get name by class name. Example:
         *
         * ApiFilterRegistrationBean -> apiFilter
         *
         * ApiFilterRegistration -> apiFilter
         *
         * ApiFilterReg -> apiFilterReg
         */
        get() {
            val ret = javaClass.simpleName.replaceFirstChar { it.lowercase() }
            return when {
                ret.endsWith("FilterRegistrationBean") && ret.length > "FilterRegistrationBean".length -> {
                    ret.substring(0, ret.length - "FilterRegistrationBean".length)
                }

                ret.endsWith("FilterRegistration") && ret.length > "FilterRegistration".length -> {
                    ret.substring(0, ret.length - "FilterRegistration".length)
                }

                else -> ret
            }
        }
}