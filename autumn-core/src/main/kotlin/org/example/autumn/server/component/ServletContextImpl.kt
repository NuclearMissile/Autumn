package org.example.autumn.server.component

import jakarta.servlet.*
import jakarta.servlet.annotation.WebFilter
import jakarta.servlet.annotation.WebListener
import jakarta.servlet.annotation.WebServlet
import jakarta.servlet.descriptor.JspConfigDescriptor
import jakarta.servlet.http.*
import org.example.autumn.resolver.PropertyResolver
import org.example.autumn.server.component.servlet.DefaultServlet
import org.example.autumn.server.component.support.FilterMapping
import org.example.autumn.server.component.support.ServletMapping
import org.example.autumn.utils.ClassUtils.createInstance
import org.example.autumn.utils.HttpUtils.escapeHtml
import org.example.autumn.utils.J2EEAnnoUtils.getFilterDispatcherTypes
import org.example.autumn.utils.J2EEAnnoUtils.getFilterInitParams
import org.example.autumn.utils.J2EEAnnoUtils.getFilterName
import org.example.autumn.utils.J2EEAnnoUtils.getFilterUrlPatterns
import org.example.autumn.utils.J2EEAnnoUtils.getServletInitParams
import org.example.autumn.utils.J2EEAnnoUtils.getServletName
import org.example.autumn.utils.J2EEAnnoUtils.getServletUrlPatterns
import org.slf4j.Logger
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
    private val classLoader: ClassLoader, private val config: PropertyResolver, webRoot: String
) : ServletContext, AutoCloseable {
    private val logger: Logger = LoggerFactory.getLogger(javaClass)
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
    private var defaultServlet: Servlet? = null

    val sessionManager = SessionManager(
        this, config.getRequiredProperty("server.web-app.session-timeout", Int::class.java)
    )

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
                val registration = addServlet(getServletName(clazz), clazz)
                registration.addMapping(*getServletUrlPatterns(clazz))
                registration.setInitParameters(getServletInitParams(clazz))
            }
            if (it.isAnnotationPresent(WebFilter::class.java)) {
                logger.atDebug().log("register @WebFilter: {}", it.name)
                val clazz = it as Class<out Filter>
                val registration = addFilter(getFilterName(clazz), clazz)
                registration.addMappingForUrlPatterns(
                    getFilterDispatcherTypes(clazz), true, *getFilterUrlPatterns(clazz)
                )
                registration.setInitParameters(getFilterInitParams(clazz))
            }
        }

        // notify event
        invokeServletContextInitialized(ServletContextEvent(this))

        // init servlets while find default servlet:
        var defaultServlet: Servlet? = null
        servletRegistrations.forEach { (name, servletReg) ->
            try {
                servletReg.servlet.init(servletReg.getServletConfig())
                for (urlPattern in servletReg.mappings) {
                    servletMappings.add(ServletMapping(servletReg.servlet, urlPattern))
                    if (urlPattern == "/") {
                        if (defaultServlet == null) {
                            defaultServlet = servletReg.servlet
                            logger.info("set default servlet: {}", servletReg.className)
                        } else {
                            logger.warn(
                                "found duplicate default servlet: {} and {}", defaultServlet, servletReg.className)
                        }
                    }
                }
                servletReg.initialized = true
            } catch (e: ServletException) {
                logger.error("init servlet failed: $name: ${servletReg.servlet.javaClass.name}", e)
            }
        }
        if (defaultServlet == null &&
            config.getRequiredProperty("server.web-app.default-servlet", Boolean::class.java)
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
                for (urlPattern in filterReg.urlPatternMappings) {
                    filterMappings.add(FilterMapping(filterReg.filter, name, urlPattern))
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
        val servlet = if ("/" != path)
            servletMappings.firstOrNull { it.matches(path) }?.servlet ?: defaultServlet else defaultServlet
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
            invokeServletRequestInitialized(ServletRequestEvent(this, req))
            chain.doFilter(req, resp)
        } catch (e: Throwable) {
            logger.error(e.message, e)
            resp.sendError(400)
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

    override fun getMimeType(file: String): String {
        val default = config.getRequiredProperty("server.mime-default")
        val n = file.lastIndexOf(".")
        return if (n < 0)
            default else config.getProperty("server.mime-types${file.substring(n).lowercase()}", default)
    }

    override fun getResourcePaths(path: String): MutableSet<String>? {
        val loc = webRoot.resolve(path.removePrefix("/")).normalize()
        if (loc.startsWith(webRoot) && Files.isDirectory(loc)) {
            try {
                return Files.list(loc).map { it.fileName.toString() }.toList().toMutableSet()
            } catch (e: Exception) {
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
        return config.getRequiredProperty("server.name")
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
        return config.getRequiredProperty("server.web-app.name")
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
        return config.getRequiredProperty("server.web-app.virtual-server-name")
    }

    override fun getSessionTimeout(): Int {
        return config.getRequiredProperty("server.web-app.session-timeout", Int::class.java)
    }

    override fun setSessionTimeout(sessionTimeout: Int) {
        require(!initialized) {
            throw IllegalStateException("setSessionTimeout after initialization.")
        }
        config.setProperty("server.web-app.session-timeout", sessionTimeout.toString())
    }

    override fun getRequestCharacterEncoding(): String {
        return config.getRequiredProperty("server.request-encoding")
    }

    override fun setRequestCharacterEncoding(encoding: String) {
        require(!initialized) {
            throw IllegalStateException("setRequestCharacterEncoding after initialization.")
        }
        config.setProperty("server.request-encoding", encoding)
    }

    override fun getResponseCharacterEncoding(): String {
        return config.getRequiredProperty("server.response-encoding")
    }

    override fun setResponseCharacterEncoding(encoding: String) {
        require(!initialized) {
            throw IllegalStateException("setResponseCharacterEncoding after initialization.")
        }
        config.setProperty("server.response-encoding", encoding)
    }
}