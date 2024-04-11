package org.example.autumn.servlet

import org.example.autumn.annotation.*
import org.example.autumn.context.ApplicationContext
import org.example.autumn.context.ConfigurableApplicationContext
import org.example.autumn.exception.AutumnException
import org.example.autumn.exception.ErrorResponseException
import org.example.autumn.exception.RequestErrorException
import org.example.autumn.exception.ServerErrorException
import org.example.autumn.resolver.PropertyResolver
import org.example.autumn.utils.ClassUtils.findAnnotation
import org.example.autumn.utils.JsonUtils.readJson
import org.example.autumn.utils.JsonUtils.writeJson
import org.example.autumn.utils.ServletUtils.DUMMY_VALUE
import org.example.autumn.utils.ServletUtils.compilePath
import jakarta.servlet.ServletContext
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpSession
import org.slf4j.LoggerFactory
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Parameter

class DispatcherServlet(
    private val applicationContext: ApplicationContext,
    propertyResolver: PropertyResolver,
) : HttpServlet() {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val viewResolver = applicationContext.getBean(ViewResolver::class.java)
    private val resourcePath = propertyResolver
        .getProperty("\${autumn.web.static-path:/static/}")!!.removeSuffix("/") + "/"
    private val faviconPath = propertyResolver.getRequiredProperty("\${autumn.web.favicon-path:/favicon.ico}")
    private val getDispatchers = mutableListOf<Dispatcher>()
    private val postDispatchers = mutableListOf<Dispatcher>()

    override fun init() {
        logger.info("init {}.", javaClass.name)
        // scan @Controller and @RestController:
        for (info in (applicationContext as ConfigurableApplicationContext).findBeanMetaInfos(Any::class.java)) {
            val beanClass = info.beanClass
            val bean = info.getRequiredInstance()
            val controllerAnno = beanClass.getAnnotation(Controller::class.java)
            val restControllerAnno = beanClass.getAnnotation(RestController::class.java)
            if (controllerAnno != null && restControllerAnno != null) {
                throw ServletException("Found both @Controller and @RestController on class: ${beanClass.name}")
            }
            if (controllerAnno != null) {
                addController(info.beanName, bean, false)
            }
            if (restControllerAnno != null) {
                addController(info.beanName, bean, true)
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

    private fun serve(req: HttpServletRequest, resp: HttpServletResponse, dispatchers: List<Dispatcher>) {
        val url = req.requestURI.removePrefix(req.contextPath)
        try {
            for (dispatcher in dispatchers) {
                val result = dispatcher.process(url, req, resp)
                if (!result.isProcessed) continue

                val ret = result.ret
                if (dispatcher.isRest) {
                    if (!resp.isCommitted) resp.contentType = "application/json"
                    if (dispatcher.isResponseBody) {
                        when (ret) {
                            is String -> resp.writer.also { it.write(ret) }.flush()
                            is ByteArray -> resp.outputStream.also { it.write(ret) }.flush()
                            else -> throw ServletException("Unable to process REST @ResponseBody result when handle url: $url")
                        }
                    } else if (!dispatcher.isVoid) {
                        resp.writer.writeJson(ret).flush()
                    }
                } else {
                    if (!resp.isCommitted) resp.contentType = "text/html"
                    when (ret) {
                        is String -> {
                            if (dispatcher.isResponseBody) {
                                resp.writer.also { it.write(ret) }.flush()
                            } else if (ret.startsWith("redirect:")) {
                                resp.sendRedirect(ret.substring(9))
                            } else {
                                throw ServletException("Unable to process String result when handle url: $url")
                            }
                        }

                        is ByteArray -> {
                            if (dispatcher.isResponseBody) {
                                resp.outputStream.also { it.write(ret) }.flush()
                            } else {
                                throw ServletException("Unable to process ByteArray result when handle url: $url")
                            }
                        }

                        is ModelAndView -> {
                            val viewName = ret.viewName
                            if (ret.status >= 400) {
                                resp.sendError(ret.status)
                            } else if (viewName.startsWith("redirect:")) {
                                resp.sendRedirect(viewName.substring(9))
                            } else {
                                viewResolver.render(viewName, ret.getModel(), req, resp)
                            }
                        }

                        (ret != null && !dispatcher.isVoid) -> {
                            throw ServletException("Unable to process ${ret.javaClass.name} result when handle url: $url")
                        }
                    }
                }
                return
            }
            resp.sendError(404)
        } catch (e: ErrorResponseException) {
            logger.warn("process request failed with status: ${e.statusCode}, $url", e)
            if (!resp.isCommitted) {
                resp.resetBuffer()
                resp.sendError(e.statusCode)
            }
        } catch (e: Exception) {
            logger.warn("process request failed: $url with unknown exception.", e)
            throw AutumnException("process request failed: $url with unknown exception.", e)
        }
    }

    private fun resource(req: HttpServletRequest, resp: HttpServletResponse) {
        val url = req.requestURI.removePrefix(req.contextPath)
        val ctx = req.servletContext
        ctx.getResourceAsStream(url).use { input ->
            if (input == null) {
                resp.sendError(404, "Not Found.")
            } else {
                val filePath = url.removeSuffix("/")
                resp.contentType = ctx.getMimeType(filePath) ?: "application/octet-stream"
                val output = resp.outputStream
                input.transferTo(output)
                output.flush()
            }
        }
    }

    private fun addController(name: String, instance: Any, isRest: Boolean) {
        logger.info("add {} controller '{}': {}", if (isRest) "REST" else "MVC", name, instance.javaClass.name)
        addMethods(name, instance, instance.javaClass, isRest)
    }

    private fun addMethods(name: String, instance: Any, clazz: Class<*>, isRest: Boolean) {
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
                getDispatchers += Dispatcher(instance, m, getAnno.value, isRest)
            }
            if (postAnno != null) {
                configMethod(m)
                postDispatchers += Dispatcher(instance, m, postAnno.value, isRest)
            }
        }

        if (clazz.superclass != null) {
            addMethods(name, instance, clazz.superclass, isRest)
        }
    }

    inner class Dispatcher(
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

        fun process(url: String, req: HttpServletRequest, resp: HttpServletResponse): Result {
            val matcher = urlPattern.matcher(url)
            if (!matcher.matches())
                return Result(false, null)

            val args = methodParams.map { param ->
                when (param.paramAnno) {
                    is PathVariable -> try {
                        convertToType(param.paramType, matcher.group(param.name))
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
            val ret = try {
                handlerMethod.invoke(controller, *args.toTypedArray())
            } catch (e: InvocationTargetException) {
                throw ServletException(e.targetException)
            } catch (e: Exception) {
                throw ServletException(e)
            }
            return Result(true, ret)
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

    data class Result(val isProcessed: Boolean, val ret: Any?)
}