package io.nuclearmissile.autumn.server.component.servlet

import io.nuclearmissile.autumn.utils.DateUtils.formatDateTimeGMT
import io.nuclearmissile.autumn.utils.HttpUtils.escapeHtml
import io.nuclearmissile.autumn.utils.HttpUtils.getDefaultErrorResponse
import io.nuclearmissile.autumn.utils.IOUtils
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class DefaultServlet : HttpServlet() {
    private val logger = LoggerFactory.getLogger(DefaultServlet::class.java)
    private lateinit var indexTemplate: String

    companion object {
        private fun formatTr(file: Path, size: Long, name: String): String {
            return "<tr>" +
                    "<td><a href=\"$name\">${name.escapeHtml()}</a></td>" +
                    "<td>${formatSize(size)}</td>" +
                    "<td>${formatDateTimeGMT(Files.getLastModifiedTime(file).toMillis())}</td>"
        }

        private fun formatSize(size: Long): String {
            return when {
                size >= 0 -> {
                    if (size > 1024 * 1024 * 1024) "%.3f GB".format(size / (1024 * 1024 * 1024.0))
                    if (size > 1024 * 1024) "%.3f MB".format(size / (1024 * 1024.0))
                    if (size > 1024) "%.3f KB".format(size / 1024.0)
                    "$size B"
                }

                else -> "N/A"
            }
        }
    }

    override fun init() {
        indexTemplate = IOUtils.readStringFromClassPath("/default-servlet/index.html")
    }

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val uri = req.requestURI
        if (!uri.startsWith("/") || uri.indexOf("/../") > 0 || uri.startsWith("/WEB-INF") || uri.startsWith("/META-INF")) {
            // insecure uri:
            logger.debug("prevent access insecure uri: {}", uri)
            resp.sendError(403, getDefaultErrorResponse(403))
            return
        }
        val path = Paths.get(req.servletContext.getRealPath(uri))
        logger.debug("try access path: {}", path)
        when {
            uri.endsWith("/") && Files.isDirectory(path) -> {
                val sb = StringBuilder(4096)
                if (uri != "/") {
                    sb.append(formatTr(path.parent, -1, ".."))
                }

                for (file in Files.list(path).toList().sortedBy { it.toString() }) {
                    var name = file.fileName.toString()
                    var size = -1L
                    if (Files.isDirectory(file)) {
                        name = "$name/"
                    } else if (Files.isRegularFile(file)) {
                        size = Files.size(file)
                    }
                    sb.append(formatTr(file, size, name))
                }
                val trs = sb.append("</tr>").toString()
                val html = indexTemplate.replace("\${URI}", uri.escapeHtml()) //
                    .replace("\${SERVER_INFO}", servletContext.serverInfo) //
                    .replace("\${TRS}", trs)
                resp.writer.apply { write(html) }.flush()
            }

            Files.isReadable(path) -> {
                logger.atDebug().log("read file: {}", path)
                resp.contentType = servletContext.getMimeType(uri) ?: "application/octet-stream"
                path.toFile().inputStream().use {
                    it.transferTo(resp.outputStream)
                }
                resp.outputStream.flush()
            }

            else -> {
                resp.sendError(404, getDefaultErrorResponse(404))
            }
        }
    }
}