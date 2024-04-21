package org.example.autumn.server.component

import jakarta.servlet.Servlet
import jakarta.servlet.ServletConfig
import jakarta.servlet.ServletContext
import jakarta.servlet.ServletRegistration
import java.util.*

class ServletRegistrationImpl(
    val servletContext: ServletContext, val servletName: String, val servlet: Servlet
) : ServletRegistration {
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
}