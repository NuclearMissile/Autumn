package io.nuclearmissile.autumn.aop.metric

import io.nuclearmissile.autumn.context.AnnotationApplicationContext
import io.nuclearmissile.autumn.utils.ConfigProperties
import java.util.*
import kotlin.test.*

class MetricProxyTest {
    @Test
    fun testMetricProxy() {
        AnnotationApplicationContext(MetricConfiguration::class.java, ConfigProperties(emptyMap())).use { ctx ->
            val worker = ctx.getUniqueBean(HashWorker::class.java)
            // proxy class, not origin class:
            assertNotSame(HashWorker::class.java, worker.javaClass)

            val md5 = "0x098f6bcd4621d373cade4e832627b4f6"
            val sha1 = "0xa94a8fe5ccb19ba61c4c0873d391e987982fbbd3"
            val sha256 = "0x9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"

            assertEquals(md5, worker.md5("test"))
            assertEquals(sha1, worker.sha1("test"))
            assertEquals(sha1, worker.sha1_f("test"))
            assertEquals(sha256, worker.sha256("test"))

            // get metric time:
            val metrics = ctx.getUniqueBean(MetricInvocation::class.java)
            assertNotNull(metrics.lastProcessedTime["MD5"])
            assertNotNull(metrics.lastProcessedTime["SHA-256"])
            assertNotNull(metrics.lastProcessedTime["SHA-1"])
            // cannot metric sha1() because it is a final method:
            assertNull(metrics.lastProcessedTime["SHA-1f"])
        }
    }
}