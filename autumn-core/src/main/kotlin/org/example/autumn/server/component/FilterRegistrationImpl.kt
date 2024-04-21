package org.example.autumn.server.component

import jakarta.servlet.*
import java.util.*

class FilterRegistrationImpl(
    private val servletContext: ServletContext, val filterName: String, val filter: Filter
) : FilterRegistration {
    var initialized: Boolean = false

    fun getFilterConfig(): FilterConfig {
        return object : FilterConfig {
            override fun getFilterName(): String {
                return this@FilterRegistrationImpl.filterName
            }

            override fun getServletContext(): ServletContext {
                return this@FilterRegistrationImpl.servletContext
            }

            override fun getInitParameter(name: String): String? {
                return this@FilterRegistrationImpl.initParameters[name]
            }

            override fun getInitParameterNames(): Enumeration<String> {
                return Collections.enumeration(this@FilterRegistrationImpl.initParameters.keys)
            }
        }
    }

    override fun getName(): String {
        TODO("Not yet implemented")
    }

    override fun getClassName(): String {
        TODO("Not yet implemented")
    }

    override fun setInitParameter(name: String, value: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun getInitParameter(name: String): String {
        TODO("Not yet implemented")
    }

    override fun setInitParameters(initParameters: MutableMap<String, String>): MutableSet<String> {
        TODO("Not yet implemented")
    }

    override fun getInitParameters(): MutableMap<String, String> {
        TODO("Not yet implemented")
    }

    override fun addMappingForServletNames(
        dispatcherTypes: EnumSet<DispatcherType>, isMatchAfter: Boolean, vararg servletNames: String
    ) {
        TODO("Not yet implemented")
    }

    override fun getServletNameMappings(): MutableCollection<String> {
        TODO("Not yet implemented")
    }

    override fun addMappingForUrlPatterns(
        dispatcherTypes: EnumSet<DispatcherType>,
        isMatchAfter: Boolean,
        vararg urlPatterns: String
    ) {
        TODO("Not yet implemented")
    }

    override fun getUrlPatternMappings(): MutableCollection<String> {
        TODO("Not yet implemented")
    }
}