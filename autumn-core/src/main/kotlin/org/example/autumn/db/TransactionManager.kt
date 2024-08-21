package org.example.autumn.db

import org.example.autumn.annotation.Transactional
import org.example.autumn.aop.AnnotationProxyBeanPostProcessor
import org.example.autumn.aop.Invocation
import org.example.autumn.aop.InvocationChain
import org.example.autumn.utils.ClassUtils.extractTarget
import org.slf4j.LoggerFactory
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.sql.Connection
import javax.sql.DataSource

interface TransactionManager

class TransactionStatus(val connection: Connection)

class TransactionalBeanPostProcessor : AnnotationProxyBeanPostProcessor<Transactional>()

class DataSourceTransactionManager(private val dataSource: DataSource) : TransactionManager, Invocation {
    companion object {
        private val holder = ThreadLocal<TransactionStatus>()
        val connection: Connection?
            get() = holder.get()?.connection
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun invoke(caller: Any, method: Method, chain: InvocationChain, args: Array<Any?>?): Any? {
        // join current tx
        val txAnno = method.declaringClass.getAnnotation(Transactional::class.java)
        if (holder.get() != null || txAnno == null || !method.isAnnotationPresent(Transactional::class.java))
            return try {
                chain.invokeChain(caller, method, args)
            } catch (e: InvocationTargetException) {
                throw e.extractTarget()
            }

        dataSource.connection.use { conn ->
            val autoCommit = conn.autoCommit
            if (autoCommit) {
                conn.autoCommit = false
            }
            try {
                holder.set(TransactionStatus(conn))
                val ret = chain.invokeChain(caller, method, args)
                conn.commit()
                return ret
            } catch (e: InvocationTargetException) {
                val target = e.extractTarget()
                logger.warn("rollback transaction for the following exception:", target)
                try {
                    conn.rollback()
                } catch (e: Exception) {
                    target.addSuppressed(e)
                }
                throw target
            } finally {
                holder.remove()
                if (autoCommit) {
                    conn.autoCommit = true
                }
            }
        }
    }
}

