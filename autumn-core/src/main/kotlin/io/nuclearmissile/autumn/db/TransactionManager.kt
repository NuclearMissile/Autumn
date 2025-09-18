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
        private val holder = ScopedValue.newInstance<TransactionStatus>()
        private val logger = LoggerFactory.getLogger(DataSourceTransactionManager::class.java)

        val connection: Connection?
            get() = if (holder.isBound) holder.get().connection else null
    }

    override fun invoke(caller: Any, method: Method, chain: InvocationChain, args: Array<Any?>?): Any? {
        // join current tx
        val txAnno = method.declaringClass.getAnnotation(Transactional::class.java)
        if (holder.isBound || txAnno == null || !method.isAnnotationPresent(Transactional::class.java))
            return chain.invokeChain(caller, method, args)

        dataSource.connection.use { conn ->
            val autoCommit = conn.autoCommit
            if (autoCommit) {
                conn.autoCommit = false
            }
            try {
                return ScopedValue.where(holder, TransactionStatus(conn)).call<Any?, Throwable> {
                    val ret = chain.invokeChain(caller, method, args)
                    conn.commit()
                    ret
                }
            } catch (e: Throwable) {
                logger.warn("rollback transaction for the following exception:", e)
                try {
                    conn.rollback()
                } catch (rollbackEx: Exception) {
                    logger.warn("exception thrown while rollback transaction, ignored:", rollbackEx)
                    e.addSuppressed(rollbackEx)
                }
                throw e
            } finally {
                if (autoCommit) {
                    conn.autoCommit = true
                }
            }
        }
    }
}