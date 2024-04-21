package org.example.autumn.server.component

import jakarta.servlet.*
import java.util.*

class ServletRegistrationImpl(
    private val servletContext: ServletContext, private val servletName: String, val servlet: Servlet
) : ServletRegistration.Dynamic {
    private val urlPatterns = mutableListOf<String>()
    private val initParams = mutableMapOf<String, String>()

    var initialized: Boolean = false

    fun getServletConfig(): ServletConfig {
        return object : ServletConfig {
            override fun getServletName(): String {
                return this@ServletRegistrationImpl.servletName
            }

            override fun getServletContext(): ServletContext {
                return this@ServletRegistrationImpl.servletContext
            }

            override fun getInitParameter(name: String): String? {
                return this@ServletRegistrationImpl.initParameters[name]
            }

            override fun getInitParameterNames(): Enumeration<String> {
                return Collections.enumeration(this@ServletRegistrationImpl.initParameters.keys)
            }
        }
    }

    override fun getName(): String {
        return servletName
    }

    override fun getClassName(): String {
        return servlet.javaClass.name
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

    override fun addMapping(vararg urlPatterns: String): MutableSet<String> {
        require(!initialized) {
            throw IllegalStateException("addMapping after initialization.")
        }
        require(urlPatterns.isNotEmpty()) { "urlPatterns is empty." }
        this.urlPatterns.addAll(urlPatterns)
        return mutableSetOf()
    }

    override fun getMappings(): MutableCollection<String> {
        return urlPatterns
    }

    override fun getRunAsRole(): String? {
        return null
    }

    override fun setAsyncSupported(isAsyncSupported: Boolean) {
        throw UnsupportedOperationException("isAsyncSupported")
    }

    override fun setLoadOnStartup(loadOnStartup: Int) {
        require(!initialized) {
            throw IllegalStateException("setLoadOnStartup after initialization.")
        }
    }

    override fun setServletSecurity(constraint: ServletSecurityElement?): MutableSet<String> {
        throw UnsupportedOperationException("setServletSecurity")
    }

    override fun setMultipartConfig(multipartConfig: MultipartConfigElement?) {
        throw UnsupportedOperationException("setMultipartConfig")
    }

    override fun setRunAsRole(roleName: String?) {
        throw UnsupportedOperationException("setRunAsRole")
    }
}