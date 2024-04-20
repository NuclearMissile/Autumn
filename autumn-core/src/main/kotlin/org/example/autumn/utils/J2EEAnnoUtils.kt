package org.example.autumn.utils

import jakarta.servlet.DispatcherType
import jakarta.servlet.Filter
import jakarta.servlet.Servlet
import jakarta.servlet.annotation.WebFilter
import jakarta.servlet.annotation.WebServlet
import java.util.*

object J2EEAnnoUtils {
    fun getServletName(clazz: Class<out Servlet>): String {
        val w = clazz.getAnnotation(WebServlet::class.java)
        return if (w != null && w.name.isNotEmpty())
            w.name else clazz.name.replaceFirstChar { it.lowercase() }
    }

    fun getFilterName(clazz: Class<out Filter>): String {
        val w = clazz.getAnnotation(WebFilter::class.java)
        return if (w != null && w.filterName.isNotEmpty())
            w.filterName else clazz.name.replaceFirstChar { it.lowercase() }
    }

    fun getServletInitParams(clazz: Class<out Servlet>): Map<String, String> {
        val w = clazz.getAnnotation(WebServlet::class.java) ?: return emptyMap()
        return w.initParams.associate { it.name to it.value }
    }

    fun getFilterInitParams(clazz: Class<out Filter>): Map<String, String> {
        val w = clazz.getAnnotation(WebFilter::class.java) ?: return emptyMap()
        return w.initParams.associate { it.name to it.value }
    }

    fun getServletUrlPatterns(clazz: Class<out Servlet>): Array<String> {
        val w = clazz.getAnnotation(WebServlet::class.java) ?: return emptyArray()
        return setOf(*(w.value + w.urlPatterns)).toTypedArray()
    }

    fun getFilterUrlPatterns(clazz: Class<out Filter>): Array<String> {
        val w = clazz.getAnnotation(WebFilter::class.java) ?: return emptyArray()
        return setOf(*(w.value + w.urlPatterns)).toTypedArray()
    }

    fun getFilterDispatcherTypes(clazz: Class<out Filter>): EnumSet<DispatcherType> {
        val w = clazz.getAnnotation(WebFilter::class.java) ?: return EnumSet.of(DispatcherType.REQUEST)
        return EnumSet.copyOf(listOf(*w.dispatcherTypes))
    }
}