package org.example.autumn.servlet

import jakarta.servlet.ServletContext
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpSession
import org.example.autumn.annotation.*
import org.example.autumn.context.ApplicationContextHolder
import org.example.autumn.context.ConfigurableApplicationContext
import org.example.autumn.exception.NotFoundException
import org.example.autumn.exception.RequestErrorException
import org.example.autumn.exception.ResponseErrorException
import org.example.autumn.exception.ServerErrorException
import org.example.autumn.utils.ClassUtils.findAnnotation
import org.example.autumn.utils.JsonUtils.readJson
import org.example.autumn.utils.JsonUtils.writeJson
import org.slf4j.LoggerFactory
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Parameter
import java.util.regex.Pattern

class DispatcherServlet : HttpServlet() {
    companion object {
        fun compilePath(path: String): Pattern {
            val regPath = path.replace("\\{([a-zA-Z][a-zA-Z0-9]*)\\}".toRegex(), "(?<$1>[^/]*)")
            if (regPath.find { it == '{' || it == '}' } != null) {
                throw ServletException("Invalid path: $path")
            }
            return Pattern.compile("^$regPath$")
        }
    }

    private val logger = LoggerFactory.getLogger(javaClass)
    private val applicationContext =
        ApplicationContextHolder.requiredApplicationContext as ConfigurableApplicationContext
    private val propertyResolver = applicationContext.getPropertyResolver()
    private val viewResolver = applicationContext.getBean(ViewResolver::class.java)
    private val resourcePath = propertyResolver
        .getProperty("\${autumn.web.static-path:/static/}")!!.removeSuffix("/") + "/"
    private val faviconPath = propertyResolver.getRequiredProperty("\${autumn.web.favicon-path:/favicon.ico}")
    private val getDispatchers = mutableListOf<Dispatcher>()
    private val postDispatchers = mutableListOf<Dispatcher>()

    override fun init() {
        logger.info("init {}.", javaClass.name)
        // scan @Controller and @RestController:
        for (info in applicationContext.findBeanMetaInfos(Any::class.java)) {
            val beanClass = info.beanClass
            val bean = info.getRequiredInstance()
            val controllerAnno = beanClass.getAnnotation(Controller::class.java)
            val restControllerAnno = beanClass.getAnnotation(RestController::class.java)
            if (controllerAnno != null && restControllerAnno != null) {
                throw ServletException("Found both @Controller and @RestController on class: ${beanClass.name}")
            }
            if (controllerAnno != null) {
                addController(info.beanName, controllerAnno.prefix, bean, false)
            }
            if (restControllerAnno != null) {
                addController(info.beanName, restControllerAnno.prefix, bean, true)
            }
        }
    }

    override fun destroy() {
        applicationContext.close()
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
                serveError(url, NotFoundException("Resource not found: $url"), req, resp, false)
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
                dispatcher == null -> serveError(url, NotFoundException("Not found: $url"), req, resp, false)
                dispatcher.isRest -> serveRest(url, dispatcher, req, resp)
                else -> serveNormal(url, dispatcher, req, resp)
            }
        } catch (e: ResponseErrorException) {
            serveError(url, e, req, resp, dispatcher!!.isRest)
        }
    }

    private fun serveRest(url: String, dispatcher: Dispatcher, req: HttpServletRequest, resp: HttpServletResponse) {
        val ret = dispatcher.process(url, req, resp)
        if (!resp.isCommitted) resp.contentType = "application/json"
        if (dispatcher.isResponseBody) {
            when (ret) {
                is String -> resp.writer.also { it.write(ret) }.flush()
                is ByteArray -> resp.outputStream.also { it.write(ret) }.flush()
                else -> throw ServerErrorException("Unable to process REST @ResponseBody result when handle url: $url")
            }
        } else if (!dispatcher.isVoid) {
            resp.writer.writeJson(ret).flush()
        }
    }

    private fun serveNormal(url: String, dispatcher: Dispatcher, req: HttpServletRequest, resp: HttpServletResponse) {
        val ret = dispatcher.process(url, req, resp)
        if (!resp.isCommitted) resp.contentType = "text/html"
        when (ret) {
            is String -> {
                if (dispatcher.isResponseBody) {
                    resp.writer.also { it.write(ret) }.flush()
                } else if (ret.startsWith("redirect:")) {
                    resp.sendRedirect(req.contextPath + ret.substring(9))
                } else {
                    throw ServerErrorException("Unable to process String result when handle url: $url")
                }
            }

            is ByteArray -> {
                if (dispatcher.isResponseBody) {
                    resp.outputStream.also { it.write(ret) }.flush()
                } else {
                    throw ServerErrorException("Unable to process ByteArray result when handle url: $url")
                }
            }

            is ModelAndView -> {
                val viewName = ret.viewName
                if (ret.status >= 400) {
                    viewResolver.renderError(ret.getModel(), ret.status, req, resp)
                } else if (viewName.startsWith("redirect:")) {
                    resp.sendRedirect(req.contextPath + viewName.substring(9))
                } else {
                    viewResolver.render(viewName, ret.getModel(), ret.status, req, resp)
                }
            }

            (ret != null && !dispatcher.isVoid) -> {
                throw ServerErrorException("Unable to process ${ret.javaClass.name} result when handle url: $url")
            }
        }
    }

    private fun serveError(
        url: String, e: ResponseErrorException, req: HttpServletRequest, resp: HttpServletResponse, isRest: Boolean
    ) {
        logger.warn("process request failed with status: ${e.statusCode}, $url", e)
        resp.reset()
        resp.status = e.statusCode
        when {
            isRest -> {
                resp.contentType = "application/json"
                resp.writer.write(e.responseBody ?: "")
                resp.writer.flush()
            }

            e.responseBody != null -> {
                resp.contentType = "text/plain"
                resp.writer.write(e.responseBody)
                resp.writer.flush()
            }

            else -> {
                viewResolver.renderError(null, e.statusCode, req, resp)
            }
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
            val getAnno = m.getAnnotation(Get::class.java)
            val postAnno = m.getAnnotation(Post::class.java)
            if (getAnno != null) {
                configMethod(m)
                getDispatchers += Dispatcher(instance, m, "/" + (prefix + getAnno.value).trim('/'), isRest)
            }
            if (postAnno != null) {
                configMethod(m)
                postDispatchers += Dispatcher(instance, m, "/" + (prefix + postAnno.value).trim('/'), isRest)
            }
        }

        if (clazz.superclass != null) {
            addMethods(name, prefix, instance, clazz.superclass, isRest)
        }
    }

    private inner class Dispatcher(
        private val controller: Any,
        private val handlerMethod: Method,
        urlPattern: String,
        val isRest: Boolean
    ) {
        val isResponseBody = handlerMethod.getAnnotation(ResponseBody::class.java) != null
        val isVoid = handlerMethod.returnType == Void.TYPE

        private val logger = LoggerFactory.getLogger(javaClass)
        private val urlPattern = compilePath(urlPattern)
        private val methodParams = mutableListOf<Param>()

        init {
            val params = handlerMethod.parameters
            val paramsAnnos = handlerMethod.parameterAnnotations
            for (i in params.indices) {
                methodParams += Param(handlerMethod, params[i], paramsAnnos[i].toList())
            }
            logger.atDebug().log(
                "mapping {} to handler {}.{}", urlPattern, controller.javaClass.simpleName, handlerMethod.name
            )
            if (logger.isDebugEnabled) {
                for (p in methodParams) {
                    logger.debug("parameter: {}", p)
                }
            }
        }

        fun match(url: String): Boolean {
            return urlPattern.matcher(url).matches()
        }

        fun process(url: String, req: HttpServletRequest, resp: HttpServletResponse): Any? {
            val args = methodParams.map { param ->
                when (param.paramAnno) {
                    is PathVariable -> try {
                        urlPattern.matcher(url).let {
                            it.matches()
                            convertToType(param.paramType, it.group(param.name))
                        }
                    } catch (e: IllegalArgumentException) {
                        throw RequestErrorException("Path variable '${param.name}' is required.")
                    }

                    is RequestBody -> req.reader.readJson(param.paramType)

                    is RequestParam -> {
                        val value = req.getParameter(param.name) ?: param.defaultValue!!
                        if (value == DUMMY_VALUE) {
                            if (param.required!!)
                                throw RequestErrorException("Request parameter '${param.name!!}' is required.")
                            else
                                return@map null
                        }
                        convertToType(param.paramType, value)
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

                    else -> when (param.paramType) {
                        HttpServletRequest::class.java -> req
                        HttpServletResponse::class.java -> resp
                        HttpSession::class.java -> req.session
                        ServletContext::class.java -> req.servletContext
                        else -> throw ServerErrorException("Could not determine argument type: ${param.paramType}")
                    }
                }
            }
            return try {
                handlerMethod.invoke(controller, *args.toTypedArray())
            } catch (e: InvocationTargetException) {
                throw e.targetException
            }
        }

        private fun convertToType(classType: Class<*>, s: String): Any {
            return when (classType) {
                String::class.java -> s
                Boolean::class.javaPrimitiveType, Boolean::class.java -> s.toBoolean()
                Int::class.javaPrimitiveType, Int::class.java -> s.toInt()
                Long::class.javaPrimitiveType, Long::class.java -> s.toLong()
                Byte::class.javaPrimitiveType, Byte::class.java -> s.toByte()
                Short::class.javaPrimitiveType, Short::class.java -> s.toShort()
                Float::class.javaPrimitiveType, Float::class.java -> s.toFloat()
                Double::class.javaPrimitiveType, Double::class.java -> s.toDouble()
                else -> throw ServerErrorException("Could not determine argument type: $classType")
            }
        }
    }

    class Param(method: Method, param: Parameter, annos: List<Annotation>) {
        var name: String? = null
        val paramAnno: Annotation?
        var defaultValue: String? = null
        var required: Boolean? = null
        val paramType: Class<*> = param.type

        init {
            val anno = listOf(
                PathVariable::class.java, RequestParam::class.java, RequestBody::class.java, Header::class.java
            ).mapNotNull { findAnnotation(annos, it) }

            // should only have 1 annotation:
            if (anno.count() > 1) {
                throw ServletException(
                    "Annotation @PathVariable, @RequestParam, @RequestBody, @Headers and @Header cannot be combined at method: $method"
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
                    if (!paramType.isAssignableFrom(String::class.java))
                        throw ServerErrorException("Unsupported argument type: $paramType, at method: $method, @Header parameter must be String? type.")
                    name = paramAnno.value.ifEmpty { param.name }
                    required = paramAnno.required
                    defaultValue = paramAnno.defaultValue
                }

                else -> {
                    if (paramType != HttpServletRequest::class.java &&
                        paramType != HttpServletResponse::class.java &&
                        paramType != HttpSession::class.java &&
                        paramType != ServletContext::class.java
                    ) {
                        throw ServerErrorException(
                            "(Missing annotation?) Unsupported argument type: $paramType at method: $method"
                        )
                    }
                }
            }
        }

        override fun toString(): String {
            return "Param(name=$name, paramAnno=$paramAnno, paramClassType=$paramType)"
        }
    }
}