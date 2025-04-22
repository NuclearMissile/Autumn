package io.nuclearmissile.autumn.servlet

import io.nuclearmissile.autumn.annotation.*
import io.nuclearmissile.autumn.context.ApplicationContextHolder
import io.nuclearmissile.autumn.exception.NotFoundException
import io.nuclearmissile.autumn.exception.RequestErrorException
import io.nuclearmissile.autumn.exception.ResponseErrorException
import io.nuclearmissile.autumn.exception.ServerErrorException
import io.nuclearmissile.autumn.utils.ClassUtils.findClosestMatchingType
import io.nuclearmissile.autumn.utils.HttpUtils.getDefaultErrorResponse
import io.nuclearmissile.autumn.utils.IOUtils.toUnixString
import io.nuclearmissile.autumn.utils.JsonUtils.writeJson
import io.nuclearmissile.autumn.utils.getRequired
import io.routekit.Router
import io.routekit.RouterSetup
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.nio.file.Paths

abstract class ExceptionMapper<T : Exception> {
    abstract fun map(e: T, req: HttpServletRequest, resp: HttpServletResponse)
}

class DispatcherServlet : HttpServlet() {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val context = ApplicationContextHolder.required
    private val friendlyErrorPageRendering =
        context.config.getRequired<Boolean>("server.web-app.friendly-error-page-rendering")
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
    private val resourcePath = context.config.getRequiredString("autumn.web.static-path").removeSuffix("/") + "/"
    private val faviconPath = context.config.getRequiredString("autumn.web.favicon-path")
    private val routerSetupMap = mapOf(
        "GET" to RouterSetup<IHttpRequestHandler>(), "POST" to RouterSetup<IHttpRequestHandler>()
    )
    private lateinit var routerMap: Map<String, Router<IHttpRequestHandler>>

    // controller name: (exception class: handler)
    private val exceptionHandlerMap = mutableMapOf<String, MutableMap<Class<Exception>, IHttpRequestHandler>>()

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

        routerMap = routerSetupMap.mapValues { it.value.build() }
    }

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val url = Paths.get(req.requestURI.removePrefix(req.contextPath)).normalize().toUnixString()
        if (url == faviconPath || url.startsWith(resourcePath))
            resource(url, req, resp)
        else
            serve(url, req, resp)
    }

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        val url = Paths.get(req.requestURI.removePrefix(req.contextPath)).normalize().toUnixString()
        serve(url, req, resp)
    }

    private fun addController(controllerBeanName: String, prefix: String, instance: Any, isRest: Boolean) {
        logger.info(
            "add {} controller '{}': {}", if (isRest) "REST" else "MVC", controllerBeanName, instance.javaClass.name
        )
        exceptionHandlerMap[controllerBeanName] = mutableMapOf()
        addMethods(controllerBeanName, prefix, instance, instance.javaClass, isRest)
    }

    private fun addMethods(
        controllerBeanName: String, prefix: String, instance: Any, clazz: Class<*>, isRest: Boolean,
    ) {
        fun configMethod(m: Method) {
            if (Modifier.isStatic(m.modifiers))
                throw ServletException("Cannot map url to a static method: $m.")
            m.isAccessible = true
        }

        clazz.declaredMethods.forEach { m ->
            val annos = listOf(
                Get::class.java,
                Post::class.java,
                ExceptionHandler::class.java
            ).mapNotNull { m.getAnnotation(it) }
            if (annos.isEmpty()) return@forEach
            if (annos.size > 1) throw IllegalArgumentException("Ambiguous annotation for $controllerBeanName: $m")

            val anno = annos.first()
            configMethod(m)
            when (anno) {
                is Get -> {
                    val urlPattern = Paths.get(prefix + anno.value).normalize().toUnixString()
                    routerSetupMap["GET"]!!.add(
                        urlPattern, HttpRequestHandler(instance, m, controllerBeanName, anno.produce, isRest)
                    )
                }

                is Post -> {
                    val urlPattern = Paths.get(prefix + anno.value).normalize().toUnixString()
                    routerSetupMap["POST"]!!.add(
                        urlPattern, HttpRequestHandler(instance, m, controllerBeanName, anno.produce, isRest)
                    )
                }

                is ExceptionHandler -> {
                    val exceptionClass = anno.value.java as Class<Exception>
                    val exceptionHandlers = exceptionHandlerMap[controllerBeanName]!!
                    if (exceptionHandlers.containsKey(exceptionClass))
                        throw IllegalArgumentException("Ambiguous exception handler for $controllerBeanName:$exceptionClass")
                    exceptionHandlers[exceptionClass] =
                        HttpRequestHandler(instance, m, controllerBeanName, anno.produce, isRest)
                }
            }
        }

        if (clazz.superclass != null) {
            addMethods(controllerBeanName, prefix, instance, clazz.superclass, isRest)
        }
    }

    private fun resource(url: String, req: HttpServletRequest, resp: HttpServletResponse) {
        val ctx = req.servletContext
        ctx.getResourceAsStream(url).use { input ->
            if (input == null) {
                logger.warn("resource not found: {}", url)
                resp.sendError(404, getDefaultErrorResponse(404))
            } else {
                val filePath = url.removeSuffix("/")
                resp.contentType = ctx.getMimeType(filePath) ?: "application/octet-stream"
                val output = resp.outputStream
                input.transferTo(output)
                output.flush()
            }
        }
    }

    private fun serve(url: String, req: HttpServletRequest, resp: HttpServletResponse) {
        try {
            val router = routerMap[req.method] ?: throw RequestErrorException("unsupported method: ${req.method}")
            val result = router.routeOrNull(url) ?: throw NotFoundException("resource not found for: $url")
            val handler = result.handler
            val params = result.variables().mapValues { it.value.toString() }
            if (handler.isRest)
                serveRest(url, params, handler, req, resp)
            else
                serveMvc(url, params, handler, req, resp)
        } catch (e: Exception) {
            serveException(e, req, resp)
        }
    }

    private fun serveRest(
        url: String,
        params: Map<String, String>,
        handler: IHttpRequestHandler,
        req: HttpServletRequest,
        resp: HttpServletResponse,
    ) {
        val (ret, _handler) = runHandler(handler, params, req, resp)
        if (resp.isCommitted) return
        when {
            _handler.isResponseBody -> {
                when (ret) {
                    is String -> resp.writer.apply { write(ret) }.flush()
                    is ByteArray -> resp.outputStream.apply { write(ret) }.flush()
                    else -> throw ServerErrorException("Unable to process REST @ResponseBody result when handle url: $url")
                }
            }

            ret is ResponseEntity -> resp.set(ret)

            !_handler.isVoid -> {
                resp.writer.writeJson(ret).flush()
            }
        }
    }

    private fun serveMvc(
        url: String,
        params: Map<String, String>,
        handler: IHttpRequestHandler,
        req: HttpServletRequest,
        resp: HttpServletResponse,
    ) {
        val (ret, _handler) = runHandler(handler, params, req, resp)
        if (resp.isCommitted) return
        when (ret) {
            is ResponseEntity -> resp.set(ret)

            is String -> {
                if (_handler.isResponseBody) {
                    resp.writer.apply { write(ret) }.flush()
                } else if (ret.startsWith("redirect:")) {
                    resp.sendRedirect(req.contextPath + ret.substring(9))
                } else {
                    throw ServerErrorException("Unable to process String result when handle url: $url")
                }
            }

            is ByteArray -> {
                if (_handler.isResponseBody) {
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

            (ret != null && !_handler.isVoid) -> {
                throw ServerErrorException("Unable to process ${ret.javaClass.name} result when handle url: $url")
            }
        }
    }

    private fun runHandler(
        handler: IHttpRequestHandler, params: Map<String, String>, req: HttpServletRequest, resp: HttpServletResponse,
    ): Pair<Any?, IHttpRequestHandler> {
        fun findExceptionHandler(controllerName: String, exceptionClass: Class<Exception>): IHttpRequestHandler? {
            val exceptionHandlers = exceptionHandlerMap[controllerName]!!
            val matchedExceptionClass = findClosestMatchingType(exceptionClass, exceptionHandlers.keys) ?: return null
            return exceptionHandlers[matchedExceptionClass]
        }

        var _handler = handler
        if (_handler.produce.isNotEmpty()) resp.contentType = _handler.produce
        val ret = try {
            _handler.process(req, resp, params, null)
        } catch (e: Exception) {
            val exceptionHandler = findExceptionHandler(handler.controllerBeanName, e.javaClass) ?: throw e
            _handler = exceptionHandler
            if (exceptionHandler.produce.isNotEmpty()) resp.contentType = _handler.produce
            _handler.process(req, resp, emptyMap(), e)
        }
        return (ret to _handler)
    }

    private fun serveException(e: Exception, req: HttpServletRequest, resp: HttpServletResponse) {
        val mapper = run {
            val target = findClosestMatchingType(e.javaClass, exceptionMappers.keys)
            if (target != null) exceptionMappers[target] else null
        }
        if (mapper != null) {
            mapper.map(e, req, resp)
        } else if (friendlyErrorPageRendering) {
            logger.info("friendly error page rendered for", e)
            val statusCode = if (e is ResponseErrorException) e.statusCode else 500
            viewResolver.renderError(statusCode, emptyMap(), req, resp)
        } else if (e is ResponseErrorException) {
            resp.sendError(e.statusCode, getDefaultErrorResponse(e.statusCode))
        } else {
            throw e
        }
    }
}
