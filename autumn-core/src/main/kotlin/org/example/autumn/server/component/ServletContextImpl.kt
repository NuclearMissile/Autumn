package org.example.autumn.server.component

import jakarta.servlet.*
import jakarta.servlet.descriptor.JspConfigDescriptor
import java.io.InputStream
import java.net.URL
import java.util.*

class ServletContextImpl:ServletContext {
    override fun getContextPath(): String {
        TODO("Not yet implemented")
    }

    override fun getContext(uripath: String): ServletContext {
        TODO("Not yet implemented")
    }

    override fun getMajorVersion(): Int {
        TODO("Not yet implemented")
    }

    override fun getMinorVersion(): Int {
        TODO("Not yet implemented")
    }

    override fun getEffectiveMajorVersion(): Int {
        TODO("Not yet implemented")
    }

    override fun getEffectiveMinorVersion(): Int {
        TODO("Not yet implemented")
    }

    override fun getMimeType(file: String): String {
        TODO("Not yet implemented")
    }

    override fun getResourcePaths(path: String): MutableSet<String> {
        TODO("Not yet implemented")
    }

    override fun getResource(path: String): URL {
        TODO("Not yet implemented")
    }

    override fun getResourceAsStream(path: String): InputStream {
        TODO("Not yet implemented")
    }

    override fun getRequestDispatcher(path: String): RequestDispatcher {
        TODO("Not yet implemented")
    }

    override fun getNamedDispatcher(name: String): RequestDispatcher {
        TODO("Not yet implemented")
    }

    override fun log(msg: String) {
        TODO("Not yet implemented")
    }

    override fun log(message: String, throwable: Throwable) {
        TODO("Not yet implemented")
    }

    override fun getRealPath(path: String): String {
        TODO("Not yet implemented")
    }

    override fun getServerInfo(): String {
        TODO("Not yet implemented")
    }

    override fun getInitParameter(name: String): String {
        TODO("Not yet implemented")
    }

    override fun getInitParameterNames(): Enumeration<String> {
        TODO("Not yet implemented")
    }

    override fun setInitParameter(name: String, value: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun getAttribute(name: String): Any {
        TODO("Not yet implemented")
    }

    override fun getAttributeNames(): Enumeration<String> {
        TODO("Not yet implemented")
    }

    override fun setAttribute(name: String, `object`: Any) {
        TODO("Not yet implemented")
    }

    override fun removeAttribute(name: String) {
        TODO("Not yet implemented")
    }

    override fun getServletContextName(): String {
        TODO("Not yet implemented")
    }

    override fun addServlet(servletName: String, className: String): ServletRegistration.Dynamic {
        TODO("Not yet implemented")
    }

    override fun addServlet(servletName: String, servlet: Servlet): ServletRegistration.Dynamic {
        TODO("Not yet implemented")
    }

    override fun addServlet(servletName: String, servletClass: Class<out Servlet>): ServletRegistration.Dynamic {
        TODO("Not yet implemented")
    }

    override fun addJspFile(servletName: String, jspFile: String): ServletRegistration.Dynamic {
        TODO("Not yet implemented")
    }

    override fun <T : Servlet> createServlet(clazz: Class<T>): T {
        TODO("Not yet implemented")
    }

    override fun getServletRegistration(servletName: String): ServletRegistration {
        TODO("Not yet implemented")
    }

    override fun getServletRegistrations(): MutableMap<String, out ServletRegistration> {
        TODO("Not yet implemented")
    }

    override fun addFilter(filterName: String, className: String): FilterRegistration.Dynamic {
        TODO("Not yet implemented")
    }

    override fun addFilter(filterName: String, filter: Filter): FilterRegistration.Dynamic {
        TODO("Not yet implemented")
    }

    override fun addFilter(filterName: String, filterClass: Class<out Filter>): FilterRegistration.Dynamic {
        TODO("Not yet implemented")
    }

    override fun <T : Filter> createFilter(clazz: Class<T>): T {
        TODO("Not yet implemented")
    }

    override fun getFilterRegistration(filterName: String): FilterRegistration {
        TODO("Not yet implemented")
    }

    override fun getFilterRegistrations(): MutableMap<String, out FilterRegistration> {
        TODO("Not yet implemented")
    }

    override fun getSessionCookieConfig(): SessionCookieConfig {
        TODO("Not yet implemented")
    }

    override fun setSessionTrackingModes(sessionTrackingModes: MutableSet<SessionTrackingMode>) {
        TODO("Not yet implemented")
    }

    override fun getDefaultSessionTrackingModes(): MutableSet<SessionTrackingMode> {
        TODO("Not yet implemented")
    }

    override fun getEffectiveSessionTrackingModes(): MutableSet<SessionTrackingMode> {
        TODO("Not yet implemented")
    }

    override fun addListener(className: String) {
        TODO("Not yet implemented")
    }

    override fun <T : EventListener> addListener(t: T) {
        TODO("Not yet implemented")
    }

    override fun addListener(listenerClass: Class<out EventListener>) {
        TODO("Not yet implemented")
    }

    override fun <T : EventListener> createListener(clazz: Class<T>): T {
        TODO("Not yet implemented")
    }

    override fun getJspConfigDescriptor(): JspConfigDescriptor {
        TODO("Not yet implemented")
    }

    override fun getClassLoader(): ClassLoader {
        TODO("Not yet implemented")
    }

    override fun declareRoles(vararg roleNames: String) {
        TODO("Not yet implemented")
    }

    override fun getVirtualServerName(): String {
        TODO("Not yet implemented")
    }

    override fun getSessionTimeout(): Int {
        TODO("Not yet implemented")
    }

    override fun setSessionTimeout(sessionTimeout: Int) {
        TODO("Not yet implemented")
    }

    override fun getRequestCharacterEncoding(): String {
        TODO("Not yet implemented")
    }

    override fun setRequestCharacterEncoding(encoding: String) {
        TODO("Not yet implemented")
    }

    override fun getResponseCharacterEncoding(): String {
        TODO("Not yet implemented")
    }

    override fun setResponseCharacterEncoding(encoding: String) {
        TODO("Not yet implemented")
    }
}