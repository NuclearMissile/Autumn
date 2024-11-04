package org.example.autumn.servlet

import jakarta.servlet.ServletContext
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpSession
import org.example.autumn.DEFAULT_ERROR_MSG
import org.example.autumn.DUMMY_VALUE
import org.example.autumn.annotation.*
import org.example.autumn.context.ApplicationContextHolder
import org.example.autumn.exception.NotFoundException
import org.example.autumn.exception.RequestErrorException
import org.example.autumn.exception.ResponseErrorException
import org.example.autumn.exception.ServerErrorException
import org.example.autumn.utils.ClassUtils.extractTarget
import org.example.autumn.utils.ClassUtils.findClosestMatchingType
import org.example.autumn.utils.ClassUtils.toPrimitive
import org.example.autumn.utils.JsonUtils.readJson
import org.example.autumn.utils.JsonUtils.writeJson
import org.slf4j.LoggerFactory
import java.lang.reflect.*

class DispatcherServlet : HttpServlet() {
    companion object {
        fun compilePath(path: String): Regex {
            val regPath = path.replace("\\{([a-zA-Z][a-zA-Z0-9]*)}".toRegex(), "(?<$1>[^/]*)")
            if (regPath.find { it == '{' || it == '}' } != null) {
                throw ServletException("Invalid path: $path")
            }
            return Regex("^$regPath\$")
        }
    }

    private val logger = LoggerFactory.getLogger(javaClass)
    private val context = ApplicationContextHolder.required
    private val viewResolver = context.getUniqueBean(ViewResolver::class.java)
    private val exceptionMappers = run {
        val infoToTarget = context.getBeanInfos(ExceptionMapper::class.java).associateWith {
            (it.requiredInstance.javaClass.genericSuperclass as ParameterizedType)
                .actualTypeArguments.single() as Class<Exception>
        }
        infoToTarget.values.toSet().associateWith { target ->
            infoToTarget.filter { it.value == target }.map { it.key }.minOf { it }
                .requiredInstance as ExceptionMapper<Exception>
        }
    }
    private val defaultExceptionMapper = object : ExceptionMapper<Exception>() {
        override fun map(e: Exception, req: HttpServletRequest, resp: HttpServletResponse) {
            val url = req.requestURI.removePrefix(req.contextPath)
            logger.info("no match exception mapper found, using default one.")
            logger.warn("process request failed for $url, status: 500", e)
            resp.set(ResponseEntity(DEFAULT_ERROR_MSG[500], 500, "text/html"))
        }
    }
    private val resourcePath = context.config.getRequiredString("autumn.web.static-path").removeSuffix("/") + "/"
    private val faviconPath = context.config.getRequiredString("autumn.web.favicon-path")
    private val getDispatchers = mutableListOf<Dispatcher>()
    private val postDispatchers = mutableListOf<Dispatcher>()
    private val exceptionHandlers = mutableMapOf<String, Dispatcher>()

    override fun init() {
        logger.info("init {}.", javaClass.name)

        // scan @Controller and @RestController:
        for (info in context.getBeanInfos(Any::class.java)) {
            val beanClass = info.beanClass
            val controllerAnno = beanClass.getAnnotation(Controller::class.java)
            val restControllerAnno = beanClass.getAnnotation(RestController::class.java)

            if (controllerAnno == null && restControllerAnno == null)
                continue
            if (controllerAnno != null && restControllerAnno != null)
                throw ServletException("Found both @Controller and @RestController on class: ${beanClass.name}")

            val bean = info.requiredInstance
            if (controllerAnno != null)
                addController(info.beanName, controllerAnno.prefix, bean, false)
            if (restControllerAnno != null)
                addController(info.beanName, restControllerAnno.prefix, bean, true)
        }

        getDispatchers.sort()
        postDispatchers.sort()
    }

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val url = req.requestURI.removePrefix(req.contextPath)
        if (url == faviconPath || url.startsWith(resourcePath))
            resource(req, resp)
        else
            serve(req, resp, getDispatchers)
    }

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        serve(req, resp, postDispatchers)
    }

    private fun resource(req: HttpServletRequest, resp: HttpServletResponse) {
        val url = req.requestURI.removePrefix(req.contextPath)
        val ctx = req.servletContext
        ctx.getResourceAsStream(url).use { input ->
            if (input == null) {
                serveException(NotFoundException("Not found"), req, resp, false)
            } else {
                val filePath = url.removeSuffix("/")
                resp.contentType = ctx.getMimeType(filePath) ?: "application/octet-stream"
                val output = resp.outputStream
                input.transferTo(output)
                output.flush()
            }
        }
    }

    private fun serve(req: HttpServletRequest, resp: HttpServletResponse, dispatchers: List<Dispatcher>) {
        val url = req.requestURI.removePrefix(req.contextPath)
        val dispatcher = dispatchers.firstOrNull { it.match(url) }
        try {
            when {
                dispatcher == null -> throw NotFoundException("Not found")
                dispatcher.isRest -> serveRest(url, dispatcher, req, resp)
                else -> serveMvc(url, dispatcher, req, resp)
            }
        } catch (e: Exception) {
            serveException(e, req, resp, dispatcher?.isRest ?: false)
        }
    }

    private fun serveRest(url: String, dispatcher: Dispatcher, req: HttpServletRequest, resp: HttpServletResponse) {
        var _dispatcher = dispatcher
        if (_dispatcher.produce.isNotEmpty()) resp.contentType = _dispatcher.produce
        val ret = try {
            _dispatcher.process(url, req, resp)
        } catch (e: Exception) {
            val exceptionHandlerKey = "${_dispatcher.controller.javaClass.name}:${e.javaClass.simpleName}"
            val exceptionHandler = exceptionHandlers[exceptionHandlerKey] ?: throw e
            _dispatcher = exceptionHandler
            if (exceptionHandler.produce.isNotEmpty()) resp.contentType = _dispatcher.produce
            _dispatcher.process(url, req, resp, e)
        }
        if (resp.isCommitted) return
        when {
            _dispatcher.isResponseBody -> {
                when (ret) {
                    is String -> resp.writer.apply { write(ret) }.flush()
                    is ByteArray -> resp.outputStream.apply { write(ret) }.flush()
                    else -> throw ServerErrorException("Unable to process REST @ResponseBody result when handle url: $url")
                }
            }

            ret is ResponseEntity -> resp.set(ret)

            !_dispatcher.isVoid -> {
                resp.writer.writeJson(ret).flush()
            }
        }
    }

    private fun serveMvc(url: String, dispatcher: Dispatcher, req: HttpServletRequest, resp: HttpServletResponse) {
        var _dispatcher = dispatcher
        if (_dispatcher.produce.isNotEmpty()) resp.contentType = _dispatcher.produce
        val ret = try {
            _dispatcher.process(url, req, resp)
        } catch (e: Exception) {
            val exceptionHandlerKey = "${_dispatcher.controller.javaClass.name}:${e.javaClass.simpleName}"
            val exceptionHandler = exceptionHandlers[exceptionHandlerKey] ?: throw e
            _dispatcher = exceptionHandler
            if (exceptionHandler.produce.isNotEmpty()) resp.contentType = _dispatcher.produce
            _dispatcher.process(url, req, resp, e)
        }
        if (resp.isCommitted) return
        when (ret) {
            is ResponseEntity -> resp.set(ret)

            is String -> {
                if (_dispatcher.isResponseBody) {
                    resp.writer.apply { write(ret) }.flush()
                } else if (ret.startsWith("redirect:")) {
                    resp.sendRedirect(req.contextPath + ret.substring(9))
                } else {
                    throw ServerErrorException("Unable to process String result when handle url: $url")
                }
            }

            is ByteArray -> {
                if (_dispatcher.isResponseBody) {
                    resp.outputStream.also { it.write(ret) }.flush()
                } else {
                    throw ServerErrorException("Unable to process ByteArray result when handle url: $url")
                }
            }

            is ModelAndView -> {
                val viewName = ret.viewName
                if (viewName == null) {
                    if (ret.status >= 400) {
                        viewResolver.renderError(ret.status, ret.getModel(), req, resp)
                    } else {
                        throw ServerErrorException("Got null viewName when handle url: $url")
                    }
                } else if (viewName.startsWith("redirect:")) {
                    resp.sendRedirect(req.contextPath + viewName.substring(9))
                } else {
                    viewResolver.render(viewName, ret.getModel(), ret.status, req, resp)
                }
            }

            (ret != null && !_dispatcher.isVoid) -> {
                throw ServerErrorException("Unable to process ${ret.javaClass.name} result when handle url: $url")
            }
        }
    }

    private fun serveException(e: Exception, req: HttpServletRequest, resp: HttpServletResponse, isRest: Boolean) {
        if (!isRest && e is ResponseErrorException && e.responseBody == null) {
            viewResolver.renderError(e.statusCode, emptyMap(), req, resp)
        } else {
            val mapper = run {
                val target = findClosestMatchingType(e.javaClass, exceptionMappers.keys)
                if (target != null) exceptionMappers[target]!! else defaultExceptionMapper
            }
            mapper.map(e, req, resp)
        }
    }

    private fun addController(name: String, prefix: String, instance: Any, isRest: Boolean) {
        logger.info("add {} controller '{}': {}", if (isRest) "REST" else "MVC", name, instance.javaClass.name)
        addMethods(name, prefix, instance, instance.javaClass, isRest)
    }

    private fun addMethods(name: String, prefix: String, instance: Any, clazz: Class<*>, isRest: Boolean) {
        fun configMethod(m: Method) {
            if (Modifier.isStatic(m.modifiers))
                throw ServletException("Cannot map url to a static method: $m.")
            m.isAccessible = true
        }

        clazz.declaredMethods.sortedBy { it.name }.forEach { m ->
            val annos = listOf(
                Get::class.java,
                Post::class.java,
                ExceptionHandler::class.java
            ).mapNotNull { m.getAnnotation(it) }
            if (annos.isEmpty()) return@forEach
            if (annos.size > 1) throw IllegalArgumentException("Ambiguous annotation for $name: $m")

            val anno = annos.first()
            if (anno is Get) {
                val urlPattern = "/" + (prefix + anno.value).trim('/')
                configMethod(m)
                getDispatchers += Dispatcher(instance, m, compilePath(urlPattern), anno.produce, isRest)
            }
            if (anno is Post) {
                val urlPattern = "/" + (prefix + anno.value).trim('/')
                configMethod(m)
                postDispatchers += Dispatcher(instance, m, compilePath(urlPattern), anno.produce, isRest)
            }
            if (anno is ExceptionHandler) {
                configMethod(m)
                val exceptionHandlerKey = "${instance.javaClass.name}:${anno.value.simpleName}"
                if (exceptionHandlers.containsKey(exceptionHandlerKey))
                    throw IllegalArgumentException("Ambiguous exception handler for $exceptionHandlerKey")
                val urlPattern = "/" + prefix.trim('/')
                exceptionHandlers[exceptionHandlerKey] =
                    Dispatcher(instance, m, compilePath(urlPattern), anno.produce, isRest)
            }
        }

        if (clazz.superclass != null) {
            addMethods(name, prefix, instance, clazz.superclass, isRest)
        }
    }
}

class Dispatcher(
    val controller: Any,
    private val handlerMethod: Method,
    private val urlPattern: Regex,
    val produce: String,
    val isRest: Boolean,
) : Comparable<Dispatcher> {
    val isResponseBody = handlerMethod.getAnnotation(ResponseBody::class.java) != null
    val isVoid = handlerMethod.returnType == Void.TYPE

    private val logger = LoggerFactory.getLogger(javaClass)
    private val methodParams = mutableListOf<Param>()
    private val name = controller.javaClass.name + "." + handlerMethod.name

    init {
        val params = handlerMethod.parameters
        val paramsAnnos = handlerMethod.parameterAnnotations
        for (i in params.indices) {
            methodParams += Param(handlerMethod, params[i], paramsAnnos[i].toList())
        }
        logger.atDebug().log(
            "mapping {} to handler {}.{}, parameters: {}",
            urlPattern.pattern, controller.javaClass.simpleName, handlerMethod.name, methodParams
        )
    }

    fun match(url: String): Boolean {
        return urlPattern.matches(url)
    }

    fun process(url: String, req: HttpServletRequest, resp: HttpServletResponse, exception: Exception? = null): Any? {
        val args = methodParams.map { param ->
            when (param.paramAnno) {
                is PathVariable -> try {
                    urlPattern.matchEntire(url)!!.groups[param.name!!]!!.value.toPrimitive(param.paramType)
                        ?: throw ServerErrorException("Could not determine argument type: ${param.paramType}")
                } catch (e: Exception) {
                    throw RequestErrorException("Path variable '${param.name}' is required.")
                }

                is RequestBody -> if (String::class.java.isAssignableFrom(param.paramType))
                    req.reader.use { it.readText() } else req.reader.use { it.readJson(param.paramType) }

                is RequestParam -> {
                    val value = req.getParameter(param.name) ?: param.defaultValue!!
                    if (value == DUMMY_VALUE) {
                        if (param.required!!)
                            throw RequestErrorException("Request parameter '${param.name!!}' is required.")
                        else
                            return@map null
                    }
                    value.toPrimitive(param.paramType)
                        ?: throw ServerErrorException("Could not determine argument type: ${param.paramType}")
                }

                is Header -> {
                    val value = req.getHeader(param.name) ?: param.defaultValue!!
                    if (value == DUMMY_VALUE) {
                        if (param.required!!)
                            throw RequestErrorException("Header '${param.name!!}' is required.")
                        else
                            return@map null
                    }
                    value
                }

                else -> if (param.paramType == HttpServletRequest::class.java) req
                else if (param.paramType == HttpServletResponse::class.java) resp
                else if (param.paramType == HttpSession::class.java) req.session
                else if (param.paramType == ServletContext::class.java) req.servletContext
                else if (Exception::class.java.isAssignableFrom(param.paramType))
                    exception ?: ServerErrorException("Could not determine argument type: ${param.paramType}")
                else if (param.paramType == RequestEntity::class.java) RequestEntity(
                    req.method, req.requestURI, req.reader.use { it.readText() },
                    req.headerNames.asSequence().associateWith { req.getHeaders(it).toList() },
                    req.parameterNames.asSequence().associateWith { req.getParameterValues(it).toList() },
                    req.cookies?.toList() ?: emptyList()
                )
                else throw ServerErrorException("Could not determine argument type: ${param.paramType}")
            }
        }

        return try {
            handlerMethod.invoke(controller, *args.toTypedArray())
        } catch (e: InvocationTargetException) {
            throw e.extractTarget()
        }
    }

    override fun compareTo(other: Dispatcher): Int {
        val ret = name.compareTo(other.name)
        return if (ret == 0) methodParams.count().compareTo(other.methodParams.count()) else ret
    }

    private class Param(method: Method, param: Parameter, annos: List<Annotation>) {
        var name: String? = null
        val paramAnno: Annotation?
        var defaultValue: String? = null
        var required: Boolean? = null
        val paramType: Class<*> = param.type

        init {
            val anno = listOf(
                PathVariable::class.java, RequestParam::class.java, RequestBody::class.java, Header::class.java
            ).mapNotNull { annos.firstOrNull { a -> it.isInstance(a) } }

            // should only have 1 annotation:
            if (anno.count() > 1) {
                throw ServletException(
                    "(Duplicated annotation?) @PathVariable, @RequestParam, @RequestBody, @Headers and @Header cannot be combined: $method"
                )
            }

            paramAnno = anno.singleOrNull()
            when (paramAnno) {
                is PathVariable -> {
                    name = paramAnno.value.ifEmpty { param.name }
                }

                is RequestParam -> {
                    name = paramAnno.value.ifEmpty { param.name }
                    required = paramAnno.required
                    defaultValue = paramAnno.defaultValue
                }

                is RequestBody -> {}

                is Header -> {
                    if (!String::class.java.isAssignableFrom(paramType))
                        throw ServerErrorException("Unsupported argument type: $paramType, at method: $method, @Header parameter must be String? type.")
                    name = paramAnno.value.ifEmpty { param.name }
                    required = paramAnno.required
                    defaultValue = paramAnno.defaultValue
                }

                else -> {
                    if (paramType != HttpServletRequest::class.java &&
                        paramType != HttpServletResponse::class.java &&
                        paramType != HttpSession::class.java &&
                        paramType != ServletContext::class.java &&
                        paramType != RequestEntity::class.java &&
                        !Exception::class.java.isAssignableFrom(paramType)
                    ) {
                        throw ServerErrorException("Unsupported argument type: $paramType at method: $method")
                    }
                }
            }
        }

        override fun toString(): String {
            return "Param(name=$name, paramAnno=$paramAnno, paramClassType=$paramType)"
        }
    }
}