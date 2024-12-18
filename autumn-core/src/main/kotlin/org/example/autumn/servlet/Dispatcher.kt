package org.example.autumn.servlet

import jakarta.servlet.ServletContext
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpSession
import org.example.autumn.DUMMY_VALUE
import org.example.autumn.annotation.*
import org.example.autumn.exception.RequestErrorException
import org.example.autumn.exception.ServerErrorException
import org.example.autumn.utils.ClassUtils.extractTarget
import org.example.autumn.utils.ClassUtils.toPrimitive
import org.example.autumn.utils.JsonUtils.readJson
import org.slf4j.LoggerFactory
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Parameter

class Dispatcher(
    urlPattern: String,
    private val controller: Any,
    private val handlerMethod: Method,
    val controllerBeanName: String,
    val produce: String,
    val isRest: Boolean,
) : Comparable<Dispatcher> {
    companion object {
        private val PATH_VARIABLE_REGEX = "\\{([a-zA-Z][a-zA-Z0-9]*)}".toRegex()

        fun compilePath(path: String): Regex {
            val regPath = path.replace(PATH_VARIABLE_REGEX, "(?<$1>[^/]*)")
            if (regPath.find { it == '{' || it == '}' } != null) {
                throw ServletException("Invalid path: $path")
            }
            return Regex("^$regPath\$")
        }
    }

    val isResponseBody = handlerMethod.getAnnotation(ResponseBody::class.java) != null
    val isVoid = handlerMethod.returnType == Void.TYPE

    private val logger = LoggerFactory.getLogger(javaClass)
    private val methodParams = mutableListOf<Param>()
    private val name = controllerBeanName + "." + handlerMethod.name
    private val urlPatternRegex = compilePath(urlPattern)

    init {
        val params = handlerMethod.parameters
        val paramsAnnos = handlerMethod.parameterAnnotations
        for (i in params.indices) {
            methodParams += Param(handlerMethod, params[i], paramsAnnos[i].toList())
        }
        logger.atDebug().log(
            "mapping {} to handler {}.{}, parameters: {}",
            urlPatternRegex.pattern, controller.javaClass.simpleName, handlerMethod.name, methodParams
        )
    }

    fun match(url: String): Boolean {
        return urlPatternRegex.matches(url)
    }

    fun process(url: String, req: HttpServletRequest, resp: HttpServletResponse, exception: Exception? = null): Any? {
        val args = methodParams.map { param ->
            when (param.paramAnno) {
                is PathVariable -> try {
                    urlPatternRegex.matchEntire(url)!!.groups[param.name!!]!!.value.toPrimitive(param.paramType)
                        ?: throw ServerErrorException("Could not determine argument type: ${param.paramType}")
                } catch (_: Exception) {
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
                    exception ?: throw ServerErrorException(
                        "Exception parameter is only supported in exception handler."
                    )
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
                    "(Duplicated annotation?) @PathVariable, @RequestParam, @RequestBody and @Header cannot be combined: $method"
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
                        throw ServerErrorException(
                            "Unsupported argument type: $paramType, at method: $method, @Header parameter must be String? type."
                        )
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