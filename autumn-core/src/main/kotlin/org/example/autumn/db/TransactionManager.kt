package org.example.autumn.db

import org.example.autumn.annotation.Transactional
import org.example.autumn.aop.AnnotationProxyBeanPostProcessor
import org.example.autumn.utils.ClassUtils.extractTarget
import org.slf4j.LoggerFactory
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.sql.Connection
import javax.sql.DataSource

interface TransactionManager

class TransactionStatus(val conn: Connection)

class TransactionalBeanPostProcessor : AnnotationProxyBeanPostProcessor<Transactional>()

class DataSourceTransactionManager(private val dataSource: DataSource) : TransactionManager, InvocationHandler {
    companion object {
        private val transactionStatusHolder = ThreadLocal<TransactionStatus>()
        val connection: Connection?
            get() = transactionStatusHolder.get()?.conn
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun invoke(proxy: Any, method: Method, args: Array<Any?>?): Any? {
        val txs = transactionStatusHolder.get()
        // join current tx
        if (txs != null)
            return method.invoke(proxy, *(args ?: emptyArray()))

        dataSource.connection.use { conn ->
            val autoCommit = conn.autoCommit
            if (autoCommit) {
                conn.autoCommit = false
            }
            try {
                transactionStatusHolder.set(TransactionStatus(conn))
                val ret = method.invoke(proxy, *(args ?: emptyArray()))
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
                transactionStatusHolder.remove()
                if (autoCommit) {
                    conn.autoCommit = true
                }
            }
        }
    }
}

