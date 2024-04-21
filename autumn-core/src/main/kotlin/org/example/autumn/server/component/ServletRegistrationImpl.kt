package org.example.autumn.server.component

import jakarta.servlet.*
import java.util.*

class ServletRegistrationImpl(
    val servletContext: ServletContext, val servletName: String, val servlet: Servlet
) : ServletRegistration.Dynamic {
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
       TODO()
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

    override fun addMapping(vararg urlPatterns: String): MutableSet<String> {
        TODO("Not yet implemented")
    }

    override fun getMappings(): MutableCollection<String> {
        TODO("Not yet implemented")
    }

    override fun getRunAsRole(): String {
        TODO("Not yet implemented")
    }

    override fun setAsyncSupported(isAsyncSupported: Boolean) {
        TODO("Not yet implemented")
    }

    override fun setLoadOnStartup(loadOnStartup: Int) {
        TODO("Not yet implemented")
    }

    override fun setServletSecurity(constraint: ServletSecurityElement?): MutableSet<String> {
        TODO("Not yet implemented")
    }

    override fun setMultipartConfig(multipartConfig: MultipartConfigElement?) {
        TODO("Not yet implemented")
    }

    override fun setRunAsRole(roleName: String?) {
        TODO("Not yet implemented")
    }
}