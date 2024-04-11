package org.example.autumn.aop.perf_metric

import org.example.autumn.annotation.Component
import org.example.autumn.annotation.ComponentScan
import org.example.autumn.annotation.Configuration
import org.example.autumn.aop.AnnotationProxyBeanPostProcessor
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.annotation.Inherited
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
)
@Retention(AnnotationRetention.RUNTIME)
@Inherited
@MustBeDocumented
annotation class Metric(val value: String)

@Configuration
@ComponentScan
class MetricApplication

@Component
class MetricProxyBeanPostProcessor : AnnotationProxyBeanPostProcessor<Metric>()

@Component
class MetricInvocationHandler : InvocationHandler {
    val logger: Logger = LoggerFactory.getLogger(javaClass)
    val lastProcessedTime = mutableMapOf<String, Long>()
    override fun invoke(proxy: Any, method: Method, args: Array<Any>?): Any? {
        val metric = method.getAnnotation(Metric::class.java)
            ?: // do not do performance test:
            return method.invoke(proxy, *(args ?: emptyArray()))
        val name: String = metric.value
        val start = System.currentTimeMillis()
        return try {
            method.invoke(proxy, *(args ?: emptyArray()))
        } finally {
            val end = System.currentTimeMillis()
            val execTime = end - start
            logger.info("log metric time: {} = {}", name, execTime)
            lastProcessedTime[name] = execTime
        }
    }
}

@Metric("metricInvocationHandler")
abstract class BaseWorker {
    @Metric("MD5")
    open fun md5(input: String): String {
        return hash("MD5", input)
    }

    @OptIn(ExperimentalStdlibApi::class)
    protected fun hash(name: String, input: String): String {
        val md = MessageDigest.getInstance(name)
        for (i in 0 until 100_0000) {
            md.reset()
            md.update(input.toByteArray(StandardCharsets.UTF_8))
        }
        return "0x" + md.digest().toHexString()
    }
}

@Component
class HashWorker : BaseWorker() {
    @Metric("SHA-1f")
    final fun sha1_f(input: String): String {
        return hash("SHA-1", input)
    }

    @Metric("SHA-1")
    fun sha1(input: String): String {
        return hash("SHA-1", input)
    }

    @Metric("SHA-256")
    fun sha256(input: String): String {
        return hash("SHA-256", input)
    }
}
