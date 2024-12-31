package io.nuclearmissile.autumn.server.component

import io.nuclearmissile.autumn.server.component.servlet.DefaultServlet
import io.nuclearmissile.autumn.server.component.support.FilterMapping
import io.nuclearmissile.autumn.server.component.support.ServletMapping
import io.nuclearmissile.autumn.utils.ClassUtils.createInstance
import io.nuclearmissile.autumn.utils.HttpUtils.escapeHtml
import io.nuclearmissile.autumn.utils.IProperties
import io.nuclearmissile.autumn.utils.getRequired
import jakarta.servlet.*
import jakarta.servlet.annotation.WebFilter
import jakarta.servlet.annotation.WebListener
import jakarta.servlet.annotation.WebServlet
import jakarta.servlet.descriptor.JspConfigDescriptor
import jakarta.servlet.http.*
import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.io.InputStream
import java.net.MalformedURLException
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class ServletContextImpl(
    private val classLoader: ClassLoader, private val config: IProperties, webRoot: String,
) : ServletContext, AutoCloseable {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val webRoot = Paths.get(webRoot).normalize().toAbsolutePath()
    private val attributes = ConcurrentHashMap<String, Any>()
    private val sessionCookieConfig = SessionCookieConfigImpl(config)
    private val servletRegistrations = mutableMapOf<String, ServletRegistrationImpl>()
    private val filterRegistrations = mutableMapOf<String, FilterRegistrationImpl>()
    private val servletMappings = mutableListOf<ServletMapping>()
    private val filterMappings = mutableListOf<FilterMapping>()

    private val servletContextListeners = mutableListOf<ServletContextListener>()
    private val servletContextAttributeListeners = mutableListOf<ServletContextAttributeListener>()
    private val servletRequestListeners = mutableListOf<ServletRequestListener>()
    private val servletRequestAttributeListeners = mutableListOf<ServletRequestAttributeListener>()
    private val httpSessionAttributeListeners = mutableListOf<HttpSessionAttributeListener>()
    private val httpSessionListeners = mutableListOf<HttpSessionListener>()

    private var initialized = false

    internal val sessionManager = SessionManager(this, config.getRequired("server.web-app.session-timeout"))

    companion object {
        private fun Class<out Servlet>.getServletName(): String {
            val w = getAnnotation(WebServlet::class.java)
            return if (w != null && w.name.isNotEmpty())
                w.name else name.replaceFirstChar { it.lowercase() }
        }

        private fun Class<out Filter>.getFilterName(): String {
            val w = getAnnotation(WebFilter::class.java)
            return if (w != null && w.filterName.isNotEmpty())
                w.filterName else name.replaceFirstChar { it.lowercase() }
        }

        private fun Class<out Servlet>.getServletInitParams(): Map<String, String> {
            val w = getAnnotation(WebServlet::class.java) ?: return emptyMap()
            return w.initParams.associate { it.name to it.value }
        }

        private fun Class<out Filter>.getFilterInitParams(): Map<String, String> {
            val w = getAnnotation(WebFilter::class.java) ?: return emptyMap()
            return w.initParams.associate { it.name to it.value }
        }

        private fun Class<out Servlet>.getServletUrlPatterns(): Array<String> {
            val w = getAnnotation(WebServlet::class.java) ?: return emptyArray()
            return setOf(*(w.value + w.urlPatterns)).toTypedArray()
        }

        private fun Class<out Filter>.getFilterUrlPatterns(): Array<String> {
            val w = getAnnotation(WebFilter::class.java) ?: return emptyArray()
            return setOf(*(w.value + w.urlPatterns)).toTypedArray()
        }

        private fun Class<out Filter>.getFilterDispatcherTypes(): EnumSet<DispatcherType> {
            val w = getAnnotation(WebFilter::class.java) ?: return EnumSet.of(DispatcherType.REQUEST)
            return EnumSet.copyOf(listOf(*w.dispatcherTypes))
        }
    }

    fun init(scannedClasses: List<Class<*>>) {
        require(!initialized) {
            throw IllegalStateException("Cannot double initializing.")
        }

        // register @WebListener, @WebServlet, @WebFilter
        scannedClasses.forEach {
            if (it.isAnnotationPresent(WebListener::class.java)) {
                logger.atDebug().log("register @WebListener: {}", it.name)
                addListener(it as Class<out EventListener>)
            }
            if (it.isAnnotationPresent(WebServlet::class.java)) {
                logger.atDebug().log("register @WebServlet: {}", it.name)
                val clazz = it as Class<out Servlet>
                val registration = addServlet(clazz.getServletName(), clazz)
                registration.addMapping(*clazz.getServletUrlPatterns())
                registration.initParameters = clazz.getServletInitParams()
            }
            if (it.isAnnotationPresent(WebFilter::class.java)) {
                logger.atDebug().log("register @WebFilter: {}", it.name)
                val clazz = it as Class<out Filter>
                val registration = addFilter(clazz.getFilterName(), clazz)
                registration.addMappingForUrlPatterns(
                    clazz.getFilterDispatcherTypes(), true, *clazz.getFilterUrlPatterns()
                )
                registration.initParameters = clazz.getFilterInitParams()
            }
        }

        // notify event
        invokeServletContextInitialized(ServletContextEvent(this))

        // add servlet mapping and init servlets
        servletRegistrations.forEach { (name, servletReg) ->
            try {
                servletReg.servlet.init(servletReg.getServletConfig())
                for (urlPattern in servletReg.mappings) {
                    servletMappings.add(ServletMapping(servletReg.servlet, urlPattern))
                    logger.info("add servlet mapping: {} for {}", servletReg.className, urlPattern)
                }
                servletReg.initialized = true
            } catch (e: ServletException) {
                logger.error("init servlet failed: $name: ${servletReg.servlet.javaClass.name}", e)
            }
        }

        // add DefaultServlet if no servlet added
        if (servletMappings.isEmpty() && config.getRequired("server.web-app.enable-default-servlet")) {
            logger.info("no servlet found, register default servlet {} for /*", DefaultServlet::class.java.name)
            val defaultServlet = DefaultServlet()
            try {
                defaultServlet.init(object : ServletConfig {
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
                servletMappings.add(ServletMapping(defaultServlet, "/*"))
            } catch (e: ServletException) {
                logger.error("init default servlet failed.", e)
            }
        }

        // init filters:
        filterRegistrations.forEach { (name, filterReg) ->
            try {
                filterReg.filter.init(filterReg.getFilterConfig())
                for (urlPattern in filterReg.urlPatternMappings) {
                    filterMappings.add(FilterMapping(filterReg.filter, urlPattern))
                }
                filterReg.initialized = true
            } catch (e: ServletException) {
                logger.error("init filter failed: " + name + " / " + filterReg.filter.javaClass.name, e)
            }
        }

        initialized = true
    }

    fun process(req: HttpServletRequest, resp: HttpServletResponse) {
        val path = req.requestURI
        // search servlet:
        val servlet = servletMappings.filter { it.match(path) }.let {
            if (it.isEmpty()) {
                resp.sendError(404, "<h1>404 Not Found</h1><p>No servlet found for URL: ${path.escapeHtml()}</p>")
                return
            }
            if (it.size > 1 && it.any { mapping -> it[0] !== mapping }) {
                resp.sendError(
                    500,
                    "<h1>500 Internal Error</h1><p>Multiple servlets found for URL: ${path.escapeHtml()}</p>"
                )
                return
            }
            it[0].servlet
        }

        // search filter:
        val filters = filterMappings.filter { it.match(path) }.map { it.filter }
        logger.atDebug().log(
            "process {} by filters {}, servlet {}", path, filters.toTypedArray().contentToString(), servlet
        )
        val chain = FilterChainImpl(filters, servlet)
        try {
            invokeServletRequestInitialized(ServletRequestEvent(this, req))
            chain.doFilter(req, resp)
        } finally {
            invokeServletRequestDestroyed(ServletRequestEvent(this, req))
        }
    }

    fun invokeServletContextInitialized(event: ServletContextEvent) {
        servletContextListeners.forEach { it.contextInitialized(event) }
    }

    fun invokeServletContextDestroyed(event: ServletContextEvent) {
        servletContextListeners.forEach { it.contextDestroyed(event) }
    }

    fun invokeServletContextAttributeAdded(event: ServletContextAttributeEvent) {
        servletContextAttributeListeners.forEach { it.attributeAdded(event) }
    }

    fun invokeServletContextAttributeRemoved(event: ServletContextAttributeEvent) {
        servletContextAttributeListeners.forEach { it.attributeRemoved(event) }
    }

    fun invokeServletContextAttributeReplaced(event: ServletContextAttributeEvent) {
        servletContextAttributeListeners.forEach { it.attributeReplaced(event) }
    }

    fun invokeServletRequestInitialized(event: ServletRequestEvent) {
        servletRequestListeners.forEach { it.requestInitialized(event) }
    }

    fun invokeServletRequestDestroyed(event: ServletRequestEvent) {
        servletRequestListeners.forEach { it.requestDestroyed(event) }
    }

    fun invokeServletRequestAttributeAdded(event: ServletRequestAttributeEvent) {
        servletRequestAttributeListeners.forEach { it.attributeAdded(event) }
    }

    fun invokeServletRequestAttributeRemoved(event: ServletRequestAttributeEvent) {
        servletRequestAttributeListeners.forEach { it.attributeRemoved(event) }
    }

    fun invokeServletRequestAttributeReplaced(event: ServletRequestAttributeEvent) {
        servletRequestAttributeListeners.forEach { it.attributeReplaced(event) }
    }

    fun invokeHttpSessionAttributeAdded(event: HttpSessionBindingEvent) {
        httpSessionAttributeListeners.forEach { it.attributeAdded(event) }
    }

    fun invokeHttpSessionAttributeRemoved(event: HttpSessionBindingEvent) {
        httpSessionAttributeListeners.forEach { it.attributeRemoved(event) }
    }

    fun invokeHttpSessionAttributeReplaced(event: HttpSessionBindingEvent) {
        httpSessionAttributeListeners.forEach { it.attributeReplaced(event) }
    }

    fun invokeHttpSessionCreated(event: HttpSessionEvent) {
        httpSessionListeners.forEach { it.sessionCreated(event) }
    }

    fun invokeHttpSessionDestroyed(event: HttpSessionEvent) {
        httpSessionListeners.forEach { it.sessionDestroyed(event) }
    }

    override fun close() {
        // destroy filter and servlet:
        filterMappings.forEach { mapping ->
            try {
                mapping.filter.destroy()
            } catch (e: Exception) {
                logger.error("destroy filter: ${mapping.filter} failed.", e)
            }
        }
        servletMappings.forEach { mapping ->
            try {
                mapping.servlet.destroy()
            } catch (e: Exception) {
                logger.error("destroy servlet: ${mapping.servlet} failed.", e)
            }
        }

        // notify event
        invokeServletContextDestroyed(ServletContextEvent(this))
    }

    override fun getContextPath(): String {
        return ""
    }

    override fun getContext(uripath: String): ServletContext? {
        return if (uripath == "/") this else null
    }

    override fun getMajorVersion(): Int {
        return 6
    }

    override fun getMinorVersion(): Int {
        return 0
    }

    override fun getEffectiveMajorVersion(): Int {
        return 6
    }

    override fun getEffectiveMinorVersion(): Int {
        return 0
    }

    override fun getMimeType(file: String): String? {
        val n = file.lastIndexOf(".")
        return if (n < 0)
            null else config.getString("server.mime-types${file.substring(n).lowercase()}")
    }

    override fun getResourcePaths(path: String): MutableSet<String>? {
        val loc = webRoot.resolve(path.removePrefix("/")).normalize()
        if (loc.startsWith(webRoot) && Files.isDirectory(loc)) {
            try {
                return Files.list(loc).map { it.fileName.toString() }.toList().toMutableSet()
            } catch (_: Exception) {
                logger.warn("list files failed for path: {}", path)
            }
        }
        return null
    }

    override fun getResource(path: String): URL? {
        val loc = webRoot.resolve(path.removePrefix("/")).normalize()
        if (loc.startsWith(webRoot)) {
            return if (Files.isRegularFile(loc)) URI.create("file://$loc").toURL() else null
        }
        throw MalformedURLException("Path not found: $path")
    }

    override fun getResourceAsStream(path: String): InputStream? {
        val loc = webRoot.resolve(path.removePrefix("/")).normalize()
        return if (loc.startsWith(webRoot) && Files.isReadable(loc))
            BufferedInputStream(FileInputStream(loc.toFile())) else null
    }

    override fun getRequestDispatcher(path: String): RequestDispatcher? {
        // not support
        return null
    }

    override fun getNamedDispatcher(name: String): RequestDispatcher? {
        // not support
        return null
    }

    override fun log(msg: String) {
        logger.info(msg)
    }

    override fun log(message: String, throwable: Throwable) {
        logger.error(message, throwable)
    }

    override fun getRealPath(path: String): String? {
        val loc = webRoot.resolve(path.removePrefix("/")).normalize()
        return if (loc.startsWith(webRoot)) loc.toString() else null
    }

    override fun getServerInfo(): String {
        return config.getRequiredString("server.name")
    }

    override fun getInitParameter(name: String): String? {
        // not support
        return null
    }

    override fun getInitParameterNames(): Enumeration<String> {
        // not support
        return Collections.emptyEnumeration()
    }

    override fun setInitParameter(name: String, value: String): Boolean {
        // not support
        throw UnsupportedOperationException("setInitParameter")
    }

    override fun getAttribute(name: String): Any? {
        return attributes[name]
    }

    override fun getAttributeNames(): Enumeration<String> {
        return attributes.keys()
    }

    override fun setAttribute(name: String, value: Any?) {
        val oldValue = attributes[name]
        if (value == null) {
            if (oldValue != null) {
                attributes.remove(name)
                invokeServletContextAttributeRemoved(ServletContextAttributeEvent(this, name, oldValue))
            }
        } else {
            attributes[name] = value
            if (oldValue == null)
                invokeServletContextAttributeAdded(ServletContextAttributeEvent(this, name, value))
            else
                invokeServletContextAttributeReplaced(ServletContextAttributeEvent(this, name, oldValue))
        }
    }

    override fun removeAttribute(name: String) {
        val oldValue = attributes[name]
        if (oldValue != null) {
            attributes.remove(name)
            invokeServletContextAttributeRemoved(ServletContextAttributeEvent(this, name, oldValue))
        }
    }

    override fun getServletContextName(): String {
        return config.getRequiredString("server.web-app.name")
    }

    override fun addServlet(servletName: String, className: String): ServletRegistration.Dynamic {
        require(!initialized) {
            throw IllegalStateException("addServlet after initialization.")
        }
        return addServlet(servletName, createInstance<Servlet>(className))
    }

    override fun addServlet(servletName: String, servlet: Servlet): ServletRegistration.Dynamic {
        require(!initialized) {
            throw IllegalStateException("addServlet after initialization.")
        }
        val servletReg = ServletRegistrationImpl(this, servletName, servlet)
        servletRegistrations[servletName] = servletReg
        return servletReg
    }

    override fun addServlet(servletName: String, servletClass: Class<out Servlet>): ServletRegistration.Dynamic {
        require(!initialized) {
            throw IllegalStateException("addServlet after initialization.")
        }
        return addServlet(servletName, createInstance(servletClass))
    }

    override fun addJspFile(servletName: String, jspFile: String): ServletRegistration.Dynamic {
        throw UnsupportedOperationException("addJspFile")
    }

    override fun <T : Servlet> createServlet(clazz: Class<T>): T {
        require(!initialized) {
            throw IllegalStateException("createServlet after initialization.")
        }
        return createInstance(clazz)
    }

    override fun getServletRegistration(servletName: String): ServletRegistration? {
        return servletRegistrations[servletName]
    }

    override fun getServletRegistrations(): MutableMap<String, out ServletRegistration> {
        return servletRegistrations.toMutableMap()
    }

    override fun addFilter(filterName: String, className: String): FilterRegistration.Dynamic {
        require(!initialized) {
            throw IllegalStateException("addFilter after initialization.")
        }
        return addFilter(filterName, createInstance<Filter>(className))
    }

    override fun addFilter(filterName: String, filter: Filter): FilterRegistration.Dynamic {
        require(!initialized) {
            throw IllegalStateException("addFilter after initialization.")
        }
        val filterReg = FilterRegistrationImpl(this, filterName, filter)
        filterRegistrations[filterName] = filterReg
        return filterReg
    }

    override fun addFilter(filterName: String, filterClass: Class<out Filter>): FilterRegistration.Dynamic {
        require(!initialized) {
            throw IllegalStateException("addFilter after initialization.")
        }
        return addFilter(filterName, createInstance(filterClass))
    }

    override fun <T : Filter> createFilter(clazz: Class<T>): T {
        require(!initialized) {
            throw IllegalStateException("createFilter after initialization.")
        }
        return createInstance(clazz)
    }

    override fun getFilterRegistration(filterName: String): FilterRegistration? {
        return filterRegistrations[filterName]
    }

    override fun getFilterRegistrations(): MutableMap<String, out FilterRegistration> {
        return filterRegistrations.toMutableMap()
    }

    override fun getSessionCookieConfig(): SessionCookieConfig {
        return sessionCookieConfig
    }

    override fun setSessionTrackingModes(sessionTrackingModes: MutableSet<SessionTrackingMode>) {
        throw UnsupportedOperationException("setSessionTrackingModes")
    }

    override fun getDefaultSessionTrackingModes(): MutableSet<SessionTrackingMode> {
        // only support tracking by cookie:
        return mutableSetOf(SessionTrackingMode.COOKIE)
    }

    override fun getEffectiveSessionTrackingModes(): MutableSet<SessionTrackingMode> {
        return defaultSessionTrackingModes
    }

    override fun addListener(className: String) {
        require(!initialized) {
            throw IllegalStateException("addListener after initialization.")
        }
        addListener(createInstance<EventListener>(className))
    }

    override fun <T : EventListener> addListener(t: T) {
        require(!initialized) {
            throw IllegalStateException("addListener after initialization.")
        }
        when (t) {
            is ServletContextListener -> servletContextListeners += t
            is ServletContextAttributeListener -> servletContextAttributeListeners += t
            is ServletRequestListener -> servletRequestListeners += t
            is ServletRequestAttributeListener -> servletRequestAttributeListeners += t
            is HttpSessionAttributeListener -> httpSessionAttributeListeners += t
            is HttpSessionListener -> httpSessionListeners += t
            else -> throw IllegalArgumentException("unsupported listener type: ${t.javaClass.name}")
        }
    }

    override fun addListener(listenerClass: Class<out EventListener>) {
        require(!initialized) {
            throw IllegalStateException("addListener after initialization.")
        }
        addListener(createInstance(listenerClass))
    }

    override fun <T : EventListener> createListener(clazz: Class<T>): T {
        require(!initialized) {
            throw IllegalStateException("createListener after initialization.")
        }
        return createInstance(clazz)
    }

    override fun getJspConfigDescriptor(): JspConfigDescriptor? {
        // not support
        return null
    }

    override fun getClassLoader(): ClassLoader {
        return classLoader
    }

    override fun declareRoles(vararg roleNames: String) {
        throw UnsupportedOperationException("declareRoles")
    }

    override fun getVirtualServerName(): String {
        return config.getRequiredString("server.web-app.virtual-server-name")
    }

    override fun getSessionTimeout(): Int {
        return config.getRequired("server.web-app.session-timeout")
    }

    override fun setSessionTimeout(sessionTimeout: Int) {
        require(!initialized) {
            throw IllegalStateException("setSessionTimeout after initialization.")
        }
        config.set("server.web-app.session-timeout", sessionTimeout.toString())
    }

    override fun getRequestCharacterEncoding(): String {
        return config.getRequiredString("server.request-encoding")
    }

    override fun setRequestCharacterEncoding(encoding: String) {
        require(!initialized) {
            throw IllegalStateException("setRequestCharacterEncoding after initialization.")
        }
        config.set("server.request-encoding", encoding)
    }

    override fun getResponseCharacterEncoding(): String {
        return config.getRequiredString("server.response-encoding")
    }

    override fun setResponseCharacterEncoding(encoding: String) {
        require(!initialized) {
            throw IllegalStateException("setResponseCharacterEncoding after initialization.")
        }
        config.set("server.response-encoding", encoding)
    }
}