package org.example.autumn.server.component

import jakarta.servlet.*
import jakarta.servlet.annotation.WebFilter
import jakarta.servlet.annotation.WebListener
import jakarta.servlet.annotation.WebServlet
import jakarta.servlet.descriptor.JspConfigDescriptor
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpSessionAttributeListener
import jakarta.servlet.http.HttpSessionListener
import org.example.autumn.resolver.PropertyResolver
import org.example.autumn.server.component.mapping.FilterMapping
import org.example.autumn.server.component.mapping.ServletMapping
import org.example.autumn.server.component.servlet.DefaultServlet
import org.example.autumn.utils.J2EEAnnoUtils.getFilterDispatcherTypes
import org.example.autumn.utils.J2EEAnnoUtils.getFilterInitParams
import org.example.autumn.utils.J2EEAnnoUtils.getFilterName
import org.example.autumn.utils.J2EEAnnoUtils.getFilterUrlPatterns
import org.example.autumn.utils.J2EEAnnoUtils.getServletInitParams
import org.example.autumn.utils.J2EEAnnoUtils.getServletName
import org.example.autumn.utils.J2EEAnnoUtils.getServletUrlPatterns
import org.example.autumn.utils.escapeHtml
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.net.URL
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class ServletContextImpl(
    private val classLoader: ClassLoader, private val config: PropertyResolver, webRoot: String
) : ServletContext, AutoCloseable {
    private val logger: Logger = LoggerFactory.getLogger(javaClass)
    private val webRoot = Paths.get(webRoot).normalize().toAbsolutePath()
    private val sessionManager = SessionManager(
        this, config.getRequiredProperty("server.web-app.session-timeout", Int::class.java)
    )
    private val attributes = ConcurrentHashMap<String, Any>()
    private val sessionCookieConfig = SessionCookieConfigImpl(config)
    private val servletRegistrations = mutableMapOf<String, ServletRegistrationImpl>()
    private val filterRegistrations = mutableMapOf<String, FilterRegistrationImpl>()
    private val servlets = mutableMapOf<String, Servlet>()
    private val filters = mutableMapOf<String, Filter>()
    private val servletMappings = mutableListOf<ServletMapping>()
    private val filterMappings = mutableListOf<FilterMapping>()

    private val servletContextListeners: List<ServletContextListener> = emptyList()
    private val servletContextAttributeListeners: List<ServletContextAttributeListener> = emptyList()
    private val servletRequestListeners: List<ServletRequestListener> = emptyList()
    private val servletRequestAttributeListeners: List<ServletRequestAttributeListener> = emptyList()
    private val httpSessionAttributeListeners: List<HttpSessionAttributeListener> = emptyList()
    private val httpSessionListeners: List<HttpSessionListener> = emptyList()

    private var initialized = false
    private var defaultServlet: Servlet? = null

    fun init(scannedClasses: List<Class<*>>) {
        require(!initialized) {
            throw IllegalStateException("Cannot double initializing.")
        }

        // register @WebListener, @WebServlet, @WebFilter
        scannedClasses.forEach {
            if (it.isAnnotationPresent(WebListener::class.java)) {
                logger.info("register @WebListener: {}", it.name)
                addListener(it as Class<out EventListener>)
            }
            if (it.isAnnotationPresent(WebServlet::class.java)) {
                logger.info("register @WebServlet: {}", it.name)
                val clazz = it as Class<out Servlet>
                val registration = addServlet(getServletName(clazz), clazz)
                registration.addMapping(*getServletUrlPatterns(clazz))
                registration.setInitParameters(getServletInitParams(clazz))
            }
            if (it.isAnnotationPresent(WebFilter::class.java)) {
                logger.info("register @WebFilter: {}", it.name)
                val clazz = it as Class<out Filter>
                val registration = addFilter(getFilterName(clazz), clazz)
                registration.addMappingForUrlPatterns(
                    getFilterDispatcherTypes(clazz), true, *getFilterUrlPatterns(clazz)
                )
                registration.setInitParameters(getFilterInitParams(clazz))
            }
        }

        // init servlets while find default servlet:
        var defaultServlet: Servlet? = null
        servletRegistrations.forEach { (name, servletReg) ->
            try {
                servletReg.servlet.init(servletReg.getServletConfig())
                servlets[name] = servletReg.servlet
                for (urlPattern in servletReg.mappings) {
                    servletMappings.add(ServletMapping(servletReg.servlet, urlPattern))
                    if (urlPattern == "/") {
                        if (defaultServlet == null) {
                            defaultServlet = servletReg.servlet
                            logger.info("set default servlet: {}", servletReg.className)
                        } else {
                            logger.warn("found duplicate default servlet: {}", servletReg.className)
                        }
                    }
                }
                servletReg.initialized = true
            } catch (e: ServletException) {
                logger.error("init servlet failed: $name: ${servletReg.servlet.javaClass.name}", e)
            }
        }
        if (defaultServlet == null && config.getRequiredProperty("server.web-app.file-listing", Boolean::class.java)
        ) {
            logger.info("no default servlet. auto register {}...", DefaultServlet::class.java.name)
            defaultServlet = DefaultServlet()
            try {
                defaultServlet!!.init(object : ServletConfig {
                    override fun getServletName(): String {
                        return "DefaultServlet"
                    }

                    override fun getServletContext(): ServletContext {
                        return this@ServletContextImpl
                    }

                    override fun getInitParameter(name: String): String? {
                        return null
                    }

                    override fun getInitParameterNames(): Enumeration<String> {
                        return Collections.emptyEnumeration()
                    }
                })
                servletMappings.add(ServletMapping(defaultServlet!!, "/"))
            } catch (e: ServletException) {
                logger.error("init default servlet failed.", e)
            }
        }
        this.defaultServlet = defaultServlet

        // init filters:
        filterRegistrations.forEach { (name, filterReg) ->
            try {
                filterReg.filter.init(filterReg.getFilterConfig())
                filters[name] = filterReg.filter
                for (urlPattern in filterReg.urlPatternMappings) {
                    filterMappings.add(FilterMapping(filterReg.filter, name, urlPattern))
                }
                filterReg.initialized = true
            } catch (e: ServletException) {
                logger.error("init filter failed: " + name + " / " + filterReg.filter.javaClass.name, e)
            }
        }

        // sort servlet mapping:
        servletMappings.sort()
        // sort by filter name then by itself
        filterMappings.sortBy { it.filterName }
        filterMappings.sort()

        // notify event
        servletContextListeners.forEach { it.contextInitialized(ServletContextEvent(this)) }
        initialized = true
    }

    fun process(req: HttpServletRequest, resp: HttpServletResponse) {
        val path = req.requestURI

        // search servlet:
        val servlet = if ("/" != path)
            servletMappings.firstOrNull { it.matches(path) }?.servlet else defaultServlet
        // 404 Not Found:
        if (servlet == null) {
            resp.writer.apply {
                write("<h1>404 Not Found</h1><p>No mapping for URL: ${path.escapeHtml()}</p>")
                flush()
            }
            return
        }

        // search filter:
        val filters = filterMappings.filter { it.matches(path) }.map { it.filter }
        logger.atDebug().log(
            "process {} by filters {}, servlet {}", path, filters.toTypedArray().contentToString(), servlet
        )
        val chain = FilterChainImpl(filters, servlet)
        try {
            servletRequestListeners.forEach { it.requestInitialized(ServletRequestEvent(this, req)) }
            chain.doFilter(req, resp)
        } catch (e:Exception){
            logger.error(e.message, e)
            throw e
        } finally {
            servletRequestListeners.forEach { it.requestDestroyed(ServletRequestEvent(this, req)) }
        }
    }

    override fun close() {
        // destroy filter and servlet:
        filterMappings.forEach { mapping: FilterMapping ->
            try {
                mapping.filter.destroy()
            } catch (e: Exception) {
                logger.error("destroy filter: ${mapping.filter} failed.", e)
            }
        }
        servletMappings.forEach { mapping: ServletMapping ->
            try {
                mapping.servlet.destroy()
            } catch (e: Exception) {
                logger.error("destroy servlet: ${mapping.servlet} failed.", e)
            }
        }

        // notify event
        servletContextListeners.forEach { it.contextDestroyed(ServletContextEvent(this)) }
    }

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