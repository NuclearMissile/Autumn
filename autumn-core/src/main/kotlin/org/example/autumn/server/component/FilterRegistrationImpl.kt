package org.example.autumn.server.component

import jakarta.servlet.*
import java.util.*

class FilterRegistrationImpl(
    private val servletContext: ServletContext, private val filterName: String, val filter: Filter,
) : FilterRegistration.Dynamic {
    private val urlPatterns = mutableListOf<String>()
    private val initParams = mutableMapOf<String, String>()

    var initialized = false

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
        return filterName
    }

    override fun getClassName(): String {
        return filter.javaClass.name
    }

    override fun setInitParameter(name: String, value: String): Boolean {
        require(!initialized) {
            throw IllegalStateException("setInitParameter after initialization.")
        }
        require(name.isNotEmpty()) { "name is empty." }
        require(value.isNotEmpty()) { "value is empty." }
        if (initParams.contains(name)) return false
        initParams[name] = value
        return true
    }

    override fun getInitParameter(name: String): String? {
        return initParams[name]
    }

    override fun setInitParameters(initParameters: MutableMap<String, String>): MutableSet<String> {
        require(!initialized) {
            throw IllegalStateException("setInitParameters after initialization.")
        }
        val conflicts = mutableSetOf<String>()
        if (initParameters.isEmpty()) return mutableSetOf()
        initParams.forEach { (k, v) ->
            if (initParams.contains(k)) conflicts.add(v) else initParams[k] = v
        }
        return conflicts
    }

    override fun getInitParameters(): MutableMap<String, String> {
        return initParams
    }

    override fun addMappingForServletNames(
        dispatcherTypes: EnumSet<DispatcherType>, isMatchAfter: Boolean, vararg servletNames: String,
    ) {
        throw UnsupportedOperationException("addMappingForServletNames")
    }

    override fun getServletNameMappings(): Collection<String> {
        return emptyList()
    }

    override fun addMappingForUrlPatterns(
        dispatcherTypes: EnumSet<DispatcherType>, isMatchAfter: Boolean, vararg urlPatterns: String,
    ) {
        require(!initialized) {
            throw IllegalStateException("addMappingForUrlPatterns after initialization.")
        }
        require(dispatcherTypes.contains(DispatcherType.REQUEST) && dispatcherTypes.size == 1) {
            "Only support DispatcherType.REQUEST."
        }
        require(urlPatterns.isNotEmpty()) { "Missing urlPatterns." }
        this.urlPatterns.addAll(urlPatterns)
    }

    override fun getUrlPatternMappings(): Collection<String> {
        return urlPatterns
    }

    override fun setAsyncSupported(isAsyncSupported: Boolean) {
        throw UnsupportedOperationException("setAsyncSupported")
    }
}