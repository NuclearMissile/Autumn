package org.example.autumn.db

import org.example.autumn.exception.DataAccessException
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.SQLException
import java.sql.Statement
import javax.sql.DataSource

class JdbcTemplate(private val dataSource: DataSource) {
    inline fun <reified T> query(sql: String, vararg args: Any?): T? {
        return query(T::class.java.getRowExtractor(), sql, *args)
    }

    fun <T> query(rse: ResultSetExtractor<T>, sql: String, vararg args: Any?): T? {
        return execute(preparedStatementCreator(sql, *args)) { ps ->
            ps.executeQuery().use { rs -> rse.extract(rs) }
        }
    }

    fun update(sql: String, vararg args: Any?): Int {
        return execute(preparedStatementCreator(sql, *args), PreparedStatement::executeUpdate)!!
    }

    fun batchInsert(sql: String, count: Int, vararg args: Any?): List<Long> {
        return execute({ conn ->
            @Suppress("SqlSourceToSinkFlow")
            conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).apply {
                val batchCount = args.size / count
                for (i in args.indices) {
                    val refIndex = i % batchCount
                    if (refIndex == 0 && i != 0) {
                        addBatch()
                    }
                    setObject(refIndex + 1, args[i])
                }
                addBatch()
            }
        }) { ps ->
            ps.executeBatch()
            buildList {
                ps.generatedKeys.use { keys ->
                    while (keys.next()) {
                        add(keys.getLong(1))
                    }
                }
            }
        }!!
    }

    fun insert(sql: String, vararg args: Any?): Long {
        return execute(preparedStatementCreator(sql, *args)) { ps ->
            val n = ps.executeUpdate()
            if (n == 0) {
                throw DataAccessException("0 rows inserted.")
            }
            if (n > 1) {
                throw DataAccessException("Multiple rows inserted.")
            }
            ps.generatedKeys.use { keys ->
                while (keys.next()) {
                    return@execute keys.getLong(1)
                }
            }
            throw DataAccessException("Should not reach here.")
        }!!
    }

    inline fun <reified T> queryRequired(sql: String, vararg args: Any?): T {
        return queryRequired(T::class.java.getRowExtractor(), sql, *args)
    }

    fun <T> queryRequired(rse: ResultSetExtractor<T>, sql: String, vararg args: Any?): T {
        return execute(preparedStatementCreator(sql, *args)) { ps ->
            var ret: T? = null
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    if (ret == null) {
                        ret = rse.extract(rs)
                    } else {
                        throw DataAccessException("Multiple rows found.")
                    }
                }
            }
            if (ret == null) {
                throw DataAccessException("Empty result set.")
            }
            return@execute ret
        }!!
    }

    inline fun <reified T> queryList(sql: String, vararg args: Any?): List<T> {
        return queryList(T::class.java.getRowExtractor(), sql, *args)
    }

    fun <T> queryList(rse: ResultSetExtractor<T>, sql: String, vararg args: Any?): List<T> {
        return execute(preparedStatementCreator(sql, *args)) { ps ->
            buildList {
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        add(rse.extract(rs)!!)
                    }
                }
            }
        }!!
    }

    private fun <T> execute(psc: PreparedStatementCreator, callback: PreparedStatementCallback<T>): T? {
        val txConn = DataSourceTransactionManager.connection
        return try {
            if (txConn != null)
                psc.createPreparedStatement(txConn).use { ps -> callback.doWithPreparedStatement(ps) }
            else dataSource.connection.use { newConn ->
                val autoCommit = newConn.autoCommit
                if (!autoCommit) newConn.autoCommit = true
                val result = psc.createPreparedStatement(newConn).use { ps -> callback.doWithPreparedStatement(ps) }
                if (!autoCommit) newConn.autoCommit = false
                result
            }
        } catch (e: SQLException) {
            throw DataAccessException("Exception thrown while execute sql.", e)
        }
    }

    private fun preparedStatementCreator(sql: String, vararg args: Any?): PreparedStatementCreator {
        return PreparedStatementCreator { conn ->
            conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).apply {
                for (i in args.indices)
                    setObject(i + 1, args[i])
            }
        }
    }
}

fun interface PreparedStatementCallback<T> {
    fun doWithPreparedStatement(ps: PreparedStatement): T?
}

fun interface PreparedStatementCreator {
    fun createPreparedStatement(con: Connection): PreparedStatement
}
