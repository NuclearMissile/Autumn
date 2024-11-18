package org.example.autumn.servlet

import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.example.autumn.DEFAULT_ERROR_RESP_BODY
import org.example.autumn.annotation.*
import org.example.autumn.context.ApplicationContextHolder
import org.example.autumn.exception.NotFoundException
import org.example.autumn.exception.ResponseErrorException
import org.example.autumn.exception.ServerErrorException
import org.example.autumn.utils.ClassUtils.findClosestMatchingType
import org.example.autumn.utils.JsonUtils.writeJson
import org.slf4j.LoggerFactory
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType

abstract class ExceptionMapper<T : Exception> {
    abstract fun map(e: T, req: HttpServletRequest, resp: HttpServletResponse)
}

class DispatcherServlet : HttpServlet() {
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
            val respBody = if (e is ResponseErrorException) e.responseBody else DEFAULT_ERROR_RESP_BODY[500]
            val statusCode = if (e is ResponseErrorException) e.statusCode else 500
            logger.info("no match exception mapper found, using default one.")
            logger.warn("process request failed for $url, status: $statusCode", e)
            resp.set(ResponseEntity(respBody, statusCode, "text/html"))
        }
    }
    private val resourcePath = context.config.getRequiredString("autumn.web.static-path").removeSuffix("/") + "/"
    private val faviconPath = context.config.getRequiredString("autumn.web.favicon-path")
    private val getDispatchers = mutableListOf<Dispatcher>()
    private val postDispatchers = mutableListOf<Dispatcher>()

    // controller name: (exception class: dispatcher)
    private val exceptionHandlerMap = mutableMapOf<String, MutableMap<Class<Exception>, Dispatcher>>()

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

        clazz.declaredMethods.sortedBy { it.name }.forEach { m ->
            val annos = listOf(
                Get::class.java,
                Post::class.java,
                ExceptionHandler::class.java
            ).mapNotNull { m.getAnnotation(it) }
            if (annos.isEmpty()) return@forEach
            if (annos.size > 1) throw IllegalArgumentException("Ambiguous annotation for $controllerBeanName: $m")

            val anno = annos.first()
            configMethod(m)
            if (anno is Get) {
                val urlPattern = "/" + (prefix + anno.value).trim('/')
                getDispatchers +=
                    Dispatcher(urlPattern, instance, m, controllerBeanName, anno.produce, isRest)
            }
            if (anno is Post) {
                val urlPattern = "/" + (prefix + anno.value).trim('/')
                postDispatchers +=
                    Dispatcher(urlPattern, instance, m, controllerBeanName, anno.produce, isRest)
            }
            if (anno is ExceptionHandler) {
                val urlPattern = "/" + prefix.trim('/')
                val exceptionClass = anno.value.java as Class<Exception>
                val exceptionHandlers = exceptionHandlerMap[controllerBeanName]!!
                if (exceptionHandlers.containsKey(exceptionClass))
                    throw IllegalArgumentException("Ambiguous exception handler for $controllerBeanName:$exceptionClass")
                exceptionHandlers[exceptionClass] =
                    Dispatcher(urlPattern, instance, m, controllerBeanName, anno.produce, isRest)
            }
        }

        if (clazz.superclass != null) {
            addMethods(controllerBeanName, prefix, instance, clazz.superclass, isRest)
        }
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
            serveException(e, req, resp, dispatcher?.isRest == true)
        }
    }

    private fun serveRest(url: String, dispatcher: Dispatcher, req: HttpServletRequest, resp: HttpServletResponse) {
        val (ret, _dispatcher) = runDispatcher(url, dispatcher, req, resp)
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
        val (ret, _dispatcher) = runDispatcher(url, dispatcher, req, resp)
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

    private fun runDispatcher(
        url: String, dispatcher: Dispatcher, req: HttpServletRequest, resp: HttpServletResponse,
    ): Pair<Any?, Dispatcher> {
        fun findExceptionHandler(controllerName: String, exceptionClass: Class<Exception>): Dispatcher? {
            val exceptionHandlers = exceptionHandlerMap[controllerName]!!
            val matchedExceptionClass = findClosestMatchingType(exceptionClass, exceptionHandlers.keys) ?: return null
            return exceptionHandlers[matchedExceptionClass]
        }

        var _dispatcher = dispatcher
        if (_dispatcher.produce.isNotEmpty()) resp.contentType = _dispatcher.produce
        val ret = try {
            _dispatcher.process(url, req, resp)
        } catch (e: Exception) {
            val exceptionHandler = findExceptionHandler(dispatcher.controllerBeanName, e.javaClass) ?: throw e
            _dispatcher = exceptionHandler
            if (exceptionHandler.produce.isNotEmpty()) resp.contentType = _dispatcher.produce
            _dispatcher.process(url, req, resp, e)
        }
        return (ret to _dispatcher)
    }

    private fun serveException(e: Exception, req: HttpServletRequest, resp: HttpServletResponse, isRest: Boolean) {
        logger.warn("Unhandled exception thrown in DispatcherServlet.", e)
        if (isRest) {
            val mapper = run {
                val target = findClosestMatchingType(e.javaClass, exceptionMappers.keys)
                if (target != null) exceptionMappers[target]!! else defaultExceptionMapper
            }
            mapper.map(e, req, resp)
        } else {
            val statusCode = if (e is ResponseErrorException) e.statusCode else 500
            viewResolver.renderError(statusCode, emptyMap(), req, resp)
        }
    }
}
