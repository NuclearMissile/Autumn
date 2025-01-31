package io.nuclearmissile.autumn.aop.metric

import io.nuclearmissile.autumn.annotation.Component
import io.nuclearmissile.autumn.aop.AnnotationProxyBeanPostProcessor
import io.nuclearmissile.autumn.aop.Invocation
import io.nuclearmissile.autumn.aop.InvocationChain
import org.slf4j.LoggerFactory
import java.lang.annotation.Inherited
import java.lang.reflect.Method
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Inherited
@MustBeDocumented
annotation class Metric(val value: Array<String>)

class MetricConfiguration

@Component
class MetricProxyBeanPostProcessor : AnnotationProxyBeanPostProcessor<Metric>()

@Component
class MetricInvocation : Invocation {
    private val logger = LoggerFactory.getLogger(javaClass)
    val lastProcessedTime = mutableMapOf<String, Long>()

    override fun invoke(caller: Any, method: Method, chain: InvocationChain, args: Array<Any?>?): Any? {
        val metric = method.getAnnotation(Metric::class.java)
            ?: // do not do performance test:
            return chain.invokeChain(caller, method, args)
        val name: String = metric.value.first()
        val start = System.currentTimeMillis()
        return try {
            chain.invokeChain(caller, method, args)
        } finally {
            val end = System.currentTimeMillis()
            val execTime = end - start
            logger.info("log metric time: {} = {}", name, execTime)
            lastProcessedTime[name] = execTime
        }
    }
}

@Metric(["metricInvocation"])
abstract class BaseWorker {
    @Metric(["MD5"])
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
    @Metric(["SHA-1f"])
    final fun sha1_f(input: String): String {
        return hash("SHA-1", input)
    }

    @Metric(["SHA-1"])
    fun sha1(input: String): String {
        return hash("SHA-1", input)
    }

    @Metric(["SHA-256"])
    fun sha256(input: String): String {
        return hash("SHA-256", input)
    }
}
