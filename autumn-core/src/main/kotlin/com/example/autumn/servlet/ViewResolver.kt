package com.example.autumn.servlet

import com.example.autumn.exception.ServerErrorException
import freemarker.cache.TemplateLoader
import freemarker.core.HTMLOutputFormat
import freemarker.template.Configuration
import freemarker.template.DefaultObjectWrapper
import freemarker.template.TemplateException
import freemarker.template.TemplateExceptionHandler
import jakarta.servlet.ServletContext
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import java.io.*

interface ViewResolver {
    fun init()
    fun render(viewName: String, model: Map<String, Any?>?, req: HttpServletRequest, resp: HttpServletResponse)
}

class FreeMarkerViewResolver(
    private val servletContext: ServletContext,
    private val templatePath: String,
    private val templateEncoding: String,
) : ViewResolver {
    private val logger = LoggerFactory.getLogger(javaClass)
    private lateinit var freeMarkerConfig: Configuration
    override fun init() {
        logger.info("init {}, set template path: {}", javaClass.simpleName, templatePath)
        freeMarkerConfig = Configuration(Configuration.VERSION_2_3_32).also { cfg ->
            cfg.outputFormat = HTMLOutputFormat.INSTANCE
            cfg.defaultEncoding = templateEncoding
            cfg.templateLoader = ServletTemplateLoader(servletContext, templatePath)
            cfg.templateExceptionHandler = TemplateExceptionHandler.HTML_DEBUG_HANDLER
            cfg.autoEscapingPolicy = Configuration.ENABLE_IF_SUPPORTED_AUTO_ESCAPING_POLICY
            cfg.localizedLookup = false
            cfg.objectWrapper = DefaultObjectWrapper(Configuration.VERSION_2_3_32).also { it.isExposeFields = true }
        }
    }

    override fun render(
        viewName: String,
        model: Map<String, Any?>?,
        req: HttpServletRequest,
        resp: HttpServletResponse
    ) {
        val template = try {
            freeMarkerConfig.getTemplate(viewName)
        } catch (e: Exception) {
            throw ServerErrorException("Exception thrown while getting template.", e)
        }
        val pw = resp.writer
        try {
            template.process(model, pw)
        } catch (e: TemplateException) {
            throw ServerErrorException("Exception thrown while rendering template.", e)
        }
        pw.flush()
    }
}

class ServletTemplateLoader(private val servletContext: ServletContext, subDirPath: String) : TemplateLoader {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val subDirPath = "/${subDirPath.replace("\\", "/").trim('/')}/"

    override fun findTemplateSource(name: String): Any? {
        val realPath = servletContext.getRealPath(subDirPath + name)
        logger.atDebug().log("load template {}: real path: {}", name, realPath)
        if (realPath != null) {
            val file = File(realPath)
            if (file.canRead() && file.isFile) {
                return file
            }
        }
        return null
    }

    override fun getLastModified(templateSource: Any?): Long {
        return if (templateSource is File)
            templateSource.lastModified()
        else 0
    }

    override fun getReader(templateSource: Any?, encoding: String): Reader {
        if (templateSource is File) {
            return InputStreamReader(FileInputStream(templateSource), encoding)
        }
        throw IOException("File not found.")
    }

    override fun closeTemplateSource(templateSource: Any?) {}
}
