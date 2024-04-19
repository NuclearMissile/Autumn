package org.example.autumn.server.component

import jakarta.servlet.DispatcherType
import jakarta.servlet.FilterRegistration
import java.util.*

class FilterRegistrationImpl : FilterRegistration {
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