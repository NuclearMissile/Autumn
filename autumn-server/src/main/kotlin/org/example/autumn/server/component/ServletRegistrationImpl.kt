package org.example.autumn.server.component

import jakarta.servlet.ServletRegistration

class ServletRegistrationImpl : ServletRegistration {
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