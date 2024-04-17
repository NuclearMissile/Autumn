package org.example.autumn.jdbc

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.example.autumn.annotation.Autowired
import org.example.autumn.annotation.Bean
import org.example.autumn.annotation.Configuration
import org.example.autumn.annotation.Value
import org.example.autumn.exception.DataAccessException
import org.example.autumn.orm.DbTemplate
import java.sql.*
import javax.sql.DataSource

@Configuration
class JdbcConfiguration {
    @Bean(destroyMethod = "close")
    fun dataSource(
        @Value("\${autumn.datasource.url}") url: String,
        @Value("\${autumn.datasource.username}") username: String,
        @Value("\${autumn.datasource.password}") password: String,
        @Value("\${autumn.datasource.driver-class-name:}") driver: String,
        @Value("\${autumn.datasource.maximum-pool-size:20}") maximumPoolSize: Int,
        @Value("\${autumn.datasource.minimum-pool-size:1}") minimumPoolSize: Int,
        @Value("\${autumn.datasource.connection-timeout:30000}") connTimeout: Int,
    ): DataSource {
        return HikariDataSource(HikariConfig().also { config ->
            config.isAutoCommit = false
            config.jdbcUrl = url
            config.username = username
            config.password = password
            config.driverClassName = driver
            config.maximumPoolSize = maximumPoolSize
            config.minimumIdle = minimumPoolSize
            config.connectionTimeout = connTimeout.toLong()
        })
    }

    @Bean
    fun jdbcTemplate(@Autowired dataSource: DataSource): JdbcTemplate {
        return JdbcTemplate(dataSource)
    }

    @Bean
    fun dbTemplate(
        @Autowired jdbcTemplate: JdbcTemplate,
        @Value("\${autumn.db-template.entity-package-path:}") entityPackagePath: String,
    ): DbTemplate {
        return DbTemplate(jdbcTemplate, entityPackagePath)
    }

    @Bean
    fun transactionalBeanPostProcessor(): TransactionalBeanPostProcessor {
        return TransactionalBeanPostProcessor()
    }

    @Bean
    fun transactionManager(@Autowired dataSource: DataSource): TransactionManager {
        return DataSourceTransactionManager(dataSource)
    }
}

class JdbcTemplate(private val dataSource: DataSource) {
    fun <T> query(sql: String, rse: ResultSetExtractor<T>, vararg args: Any?): T? {
        return execute(preparedStatementCreator(sql, *args)) { ps ->
            ps.executeQuery().use { rs -> rse.extractData(rs) }
        }
    }

    fun update(sql: String, vararg args: Any?): Int {
        return execute(preparedStatementCreator(sql, *args), PreparedStatement::executeUpdate)!!
    }

    fun batchInsert(sql: String, count: Int, vararg args: Any?): List<Number> {
        return execute({ conn ->
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
                        add(keys.getObject(1) as Number)
                    }
                }
            }
        }!!
    }

    fun updateWithGeneratedKey(sql: String, vararg args: Any?): Number {
        return execute(preparedStatementCreator(sql, *args)) { ps: PreparedStatement ->
            val n = ps.executeUpdate()
            if (n == 0) {
                throw DataAccessException("0 rows inserted.")
            }
            if (n > 1) {
                throw DataAccessException("Multiple rows inserted.")
            }
            ps.generatedKeys.use { keys ->
                while (keys.next()) {
                    return@execute keys.getObject(1) as Number
                }
            }
            throw DataAccessException("Should not reach here.")
        }!!
    }

    fun <T> queryRequiredObject(sql: String, clazz: Class<T>, vararg args: Any?): T {
        return when {
            clazz == String::class.java -> queryRequiredObject(sql, StringRowMapper.instance, *args) as T
            clazz == Boolean::class.java || clazz == Boolean::class.javaPrimitiveType ->
                queryRequiredObject(sql, BooleanRowMapper.instance, *args) as T

            Number::class.java.isAssignableFrom(clazz) || clazz.isPrimitive ->
                queryRequiredObject(sql, NumberRowMapper.instance, *args) as T

            else -> queryRequiredObject(sql, BeanRowMapper(clazz), *args)
        }
    }

    fun <T> queryRequiredObject(sql: String, rowMapper: ResultSetExtractor<T>, vararg args: Any?): T {
        return execute(preparedStatementCreator(sql, *args)) { ps ->
            var ret: T? = null
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    if (ret == null) {
                        ret = rowMapper.extractData(rs)
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

    fun <T> queryList(sql: String, clazz: Class<T>, vararg args: Any?): List<T> {
        return queryList(sql, BeanRowMapper(clazz), *args)
    }

    fun <T> queryList(sql: String, rowMapper: ResultSetExtractor<T>, vararg args: Any?): List<T> {
        return execute(preparedStatementCreator(sql, *args)) { ps ->
            buildList {
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        add(rowMapper.extractData(rs)!!)
                    }
                }
            }
        }!!
    }

    fun <T> execute(psc: PreparedStatementCreator, callback: PreparedStatementCallback<T>): T? {
        return executeWithTx { conn ->
            psc.createPreparedStatement(conn).use { ps -> callback.doInPreparedStatement(ps) }
        }
    }

    fun <T> executeWithTx(callback: ConnectionCallback<T>): T? {
        val txConn = DataSourceTransactionManager.transactionConn
        return try {
            if (txConn != null)
                callback.doInConnection(txConn)
            else dataSource.connection.use { newConn ->
                val autoCommit = newConn.autoCommit
                if (!autoCommit) newConn.autoCommit = true
                val result = callback.doInConnection(newConn)
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

fun interface ConnectionCallback<T> {
    fun doInConnection(conn: Connection): T?
}

fun interface PreparedStatementCallback<T> {
    fun doInPreparedStatement(ps: PreparedStatement): T?
}

fun interface PreparedStatementCreator {
    fun createPreparedStatement(con: Connection): PreparedStatement
}

fun interface ResultSetExtractor<T> {
    fun extractData(rs: ResultSet): T?
}