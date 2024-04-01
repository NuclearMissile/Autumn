package com.example.autumn.jdbc

import com.example.autumn.annotation.Transactional
import com.example.autumn.annotation.WithTransaction
import com.example.autumn.aop.AnnotationProxyBeanPostProcessor
import com.example.autumn.exception.TransactionException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.sql.Connection
import java.sql.SQLException
import javax.sql.DataSource

interface TransactionManager

class TransactionStatus(val conn: Connection)

class TransactionalBeanPostProcessor : AnnotationProxyBeanPostProcessor<Transactional>()

class DataSourceTransactionManager(private val dataSource: DataSource) : TransactionManager, InvocationHandler {
    companion object {
        private val transactionStatus = ThreadLocal<TransactionStatus?>()
        val transactionConn: Connection?
            get() = transactionStatus.get()?.conn
    }

    val logger: Logger = LoggerFactory.getLogger(javaClass)

    override fun invoke(proxy: Any, method: Method, args: Array<Any>?): Any? {
        val txs = transactionStatus.get()
        // join current tx
        if (txs != null || !method.isAnnotationPresent(WithTransaction::class.java))
            return method.invoke(proxy, *(args ?: emptyArray()))

        dataSource.connection.use { conn ->
            val autoCommit = conn.autoCommit
            if (autoCommit) {
                conn.autoCommit = false
            }
            try {
                transactionStatus.set(TransactionStatus(conn))
                val ret = method.invoke(proxy, *(args ?: emptyArray()))
                conn.commit()
                return ret
            } catch (e: InvocationTargetException) {
                val cause = e.cause?.cause
                logger.warn(
                    "will rollback transaction for caused exception: {}", cause?.toString() ?: "null"
                )
                val txe = TransactionException("Exception thrown in tx context.", cause)
                try {
                    conn.rollback()
                } catch (sqle: SQLException) {
                    txe.addSuppressed(sqle)
                }
                throw txe
            } finally {
                transactionStatus.remove()
                if (autoCommit) {
                    conn.autoCommit = true
                }
            }
        }
    }
}

