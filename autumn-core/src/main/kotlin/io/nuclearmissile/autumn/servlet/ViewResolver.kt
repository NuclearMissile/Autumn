package io.nuclearmissile.autumn.servlet

import freemarker.cache.TemplateLoader
import freemarker.core.HTMLOutputFormat
import freemarker.template.Configuration
import freemarker.template.DefaultObjectWrapper
import freemarker.template.TemplateException
import freemarker.template.TemplateExceptionHandler
import freemarker.template.TemplateNotFoundException
import io.nuclearmissile.autumn.exception.ServerErrorException
import io.nuclearmissile.autumn.utils.HttpUtils.getDefaultErrorResponse
import jakarta.servlet.ServletContext
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import java.io.*

interface ViewResolver {
    fun init()
    fun render(
        viewName: String, model: Map<String, Any>?, statusCode: Int, req: HttpServletRequest, resp: HttpServletResponse,
    )

    fun renderError(
        statusCode: Int, model: Map<String, Any>?, req: HttpServletRequest, resp: HttpServletResponse,
    )
}

class FreeMarkerViewResolver(
    private val servletContext: ServletContext,
    private val templatePath: String,
    private val errorTemplatePath: String,
    private val templateEncoding: String,
) : ViewResolver {
    private val logger = LoggerFactory.getLogger(javaClass)
    private lateinit var freeMarkerConfig: Configuration
    private lateinit var freeMarkerErrorConfig: Configuration

    override fun init() {
        fun createConfig(templatePath: String) = Configuration(Configuration.VERSION_2_3_34).apply {
            outputFormat = HTMLOutputFormat.INSTANCE
            defaultEncoding = templateEncoding
            templateLoader = ServletTemplateLoader(servletContext, templatePath)
            templateExceptionHandler = TemplateExceptionHandler.HTML_DEBUG_HANDLER
            autoEscapingPolicy = Configuration.ENABLE_IF_SUPPORTED_AUTO_ESCAPING_POLICY
            localizedLookup = false
            objectWrapper = DefaultObjectWrapper(Configuration.VERSION_2_3_34).apply { isExposeFields = true }
        }

        logger.info(
            "init {}, set template path: {}, error template path: {}",
            javaClass.simpleName, templatePath, errorTemplatePath
        )
        freeMarkerConfig = createConfig(templatePath)
        freeMarkerErrorConfig = createConfig(errorTemplatePath)
    }

    override fun render(
        viewName: String, model: Map<String, Any>?, statusCode: Int, req: HttpServletRequest, resp: HttpServletResponse,
    ) {
        resp.status = statusCode
        resp.contentType = "text/html"

        val template = try {
            freeMarkerConfig.getTemplate(viewName)
        } catch (_: Exception) {
            throw ServerErrorException("Template '$viewName' not found.")
        }

        resp.writer.apply {
            try {
                template.process(model, this)
            } catch (e: TemplateException) {
                throw ServerErrorException("Exception thrown while rendering template: $viewName", e)
            }
        }.flush()
    }

    override fun renderError(
        statusCode: Int, model: Map<String, Any>?, req: HttpServletRequest, resp: HttpServletResponse,
    ) {
        resp.status = statusCode
        resp.contentType = "text/html"

        val template = try {
            freeMarkerErrorConfig.getTemplate("$statusCode.html")
        } catch (_: Exception) {
            resp.sendError(statusCode, getDefaultErrorResponse(statusCode))
            return
        }

        resp.writer.apply {
            try {
                template.process(model, this)
            } catch (_: Exception) {
                logger.warn("Exception thrown while rendering template: $statusCode.html")
                resp.sendError(500, getDefaultErrorResponse(500))
            }
        }.flush()
    }
}

class ServletTemplateLoader(private val servletContext: ServletContext, subDirPath: String) : TemplateLoader {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val subDirPath = "/${subDirPath.replace("\\", "/").trim('/')}/"

    override fun findTemplateSource(name: String): Any? {
        val realPath = servletContext.getRealPath(subDirPath + name)
        logger.atDebug().log("try to load template {}, real path: {}", name, realPath)
        if (realPath != null) {
            val file = File(realPath)
            if (file.canRead() && file.isFile) {
                return file
            }
        }
        return null
    }

    override fun getLastModified(templateSource: Any?): Long {
        return if (templateSource is File) templateSource.lastModified() else 0
    }

    override fun getReader(templateSource: Any?, encoding: String): Reader {
        if (templateSource is File) {
            return InputStreamReader(FileInputStream(templateSource), encoding)
        }
        throw IOException("File not found.")
    }

    override fun closeTemplateSource(templateSource: Any?) {}
}
