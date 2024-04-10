package com.example.autumn.jdbc

import com.example.autumn.annotation.Autowired
import com.example.autumn.annotation.Bean
import com.example.autumn.annotation.Configuration
import com.example.autumn.annotation.Value
import com.example.autumn.exception.DataAccessException
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
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
        @Value("\${autumn.datasource.connection-timeout:30000}") connTimeout: Int
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

    fun updateWithGeneratedKey(sql: String, vararg args: Any?): Number {
        return execute( // PreparedStatementCreator
            PreparedStatementCreator { con: Connection ->
                @Suppress("SqlSourceToSinkFlow")
                val ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)
                for (i in args.indices)
                    ps.setObject(i + 1, args[i])
                return@PreparedStatementCreator ps
            },  // PreparedStatementCallback
            PreparedStatementCallback { ps: PreparedStatement ->
                val n = ps.executeUpdate()
                if (n == 0) {
                    throw DataAccessException("0 rows inserted.")
                }
                if (n > 1) {
                    throw DataAccessException("Multiple rows inserted.")
                }
                ps.generatedKeys.use { keys ->
                    while (keys.next()) {
                        return@PreparedStatementCallback keys.getObject(1) as Number
                    }
                }
                throw DataAccessException("Should not reach here.")
            }
        )!!
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
        return execute(preparedStatementCreator(sql, *args), PreparedStatementCallback { ps ->
            var t: T? = null
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    if (t == null) {
                        t = rowMapper.extractData(rs)
                    } else {
                        throw DataAccessException("Multiple rows found.")
                    }
                }
            }
            if (t == null) {
                throw DataAccessException("Empty result set.")
            }
            return@PreparedStatementCallback t!!
        })!!
    }

    fun <T> queryList(sql: String, clazz: Class<T>, vararg args: Any?): List<T> {
        return queryList(sql, BeanRowMapper(clazz), *args)
    }

    fun <T> queryList(sql: String, rowMapper: ResultSetExtractor<T>, vararg args: Any?): List<T> {
        return execute(preparedStatementCreator(sql, *args), PreparedStatementCallback { ps ->
            val list = mutableListOf<T>()
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    list.add(rowMapper.extractData(rs)!!)
                }
            }
            return@PreparedStatementCallback list
        })!!
    }

    fun <T> execute(psc: PreparedStatementCreator, callback: PreparedStatementCallback<T>): T? {
        return execute { conn ->
            psc.createPreparedStatement(conn).use { ps -> callback.doInPreparedStatement(ps) }
        }
    }

    fun <T> execute(callback: ConnectionCallback<T>): T? {
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
            val ps = conn.prepareStatement(sql)
            for (i in args.indices)
                ps.setObject(i + 1, args[i])
            ps
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