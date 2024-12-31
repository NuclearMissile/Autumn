package io.nuclearmissile.autumn.db

import io.nuclearmissile.autumn.annotation.Transactional
import io.nuclearmissile.autumn.aop.AnnotationProxyBeanPostProcessor
import io.nuclearmissile.autumn.aop.Invocation
import io.nuclearmissile.autumn.aop.InvocationChain
import org.slf4j.LoggerFactory
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
            return chain.invokeChain(caller, method, args)

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
            } catch (e: Exception) {
                logger.warn("rollback transaction for the following exception:", e)
                try {
                    conn.rollback()
                } catch (e: Exception) {
                    logger.warn("exception thrown while rollback transaction, ignored:", e)
                    e.addSuppressed(e)
                }
                throw e
            } finally {
                holder.remove()
                if (autoCommit) {
                    conn.autoCommit = true
                }
            }
        }
    }
}

